using System.Net.Sockets;
using System.Text.Json.Nodes;
using YMM4Craft.Core.Frames;
using YMM4Craft.Core.Protocol;
using YMM4Craft.Core.Session;

namespace YMM4Craft.Core;

public sealed class CraftHost : IAsyncDisposable
{
    static readonly TimeSpan HelloTimeout = TimeSpan.FromSeconds(5);

    readonly SessionRequest request;
    readonly FrameFile frameFile;
    Socket? listener;
    JsonLineConnection? connection;
    readonly CancellationTokenSource cts = new();
    Task? pingLoop;
    int disconnected;

    public ModInfo? Mod { get; private set; }

    public string? RejectionMessage => SessionRejection.ReadMessage(request);


    public event Action<string, JsonNode?>? EventReceived;

    public event Action<FrameNotice>? FrameArrived;

    public event Action<string>? Disconnected;

    public bool IsConnected => connection is { IsClosed: false };

    CraftHost(SessionRequest request, FrameFile frameFile, Socket listener)
    {
        this.request = request;
        this.frameFile = frameFile;
        this.listener = listener;
    }

    public static CraftHost Start(IReadOnlyList<string> features, long frameCapacity = FrameFile.DefaultCapacity)
    {
        Directory.CreateDirectory(SessionPaths.BaseDir);
        CleanupStaleFiles();
        var request = SessionRequest.Create(Environment.ProcessId, features, frameCapacity, DateTimeOffset.UtcNow);

        var frameFile = FrameFile.Create(request.FrameFile, frameCapacity);
        Socket? listener = null;
        try
        {
            DeleteQuietly(request.SocketPath);
            listener = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
            listener.Bind(new UnixDomainSocketEndPoint(request.SocketPath));
            listener.Listen(1);

            request.WriteTo(SessionPaths.RequestFile);
            return new CraftHost(request, frameFile, listener);
        }
        catch
        {
            listener?.Dispose();
            frameFile.Dispose();
            DeleteQuietly(request.SocketPath);
            throw;
        }
    }

    public async Task<ModInfo> WaitForModAsync(CancellationToken ct)
    {
        var l = listener ?? throw new InvalidOperationException("すでに接続済みです");
        while (true)
        {
            var socket = await l.AcceptAsync(ct).ConfigureAwait(false);
            var conn = new JsonLineConnection(socket);
            try
            {
                using var helloCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
                helloCts.CancelAfter(HelloTimeout);
                var hello = await conn.ReadMessageAsync(helloCts.Token).ConfigureAwait(false);

                var problem = ValidateHello(hello);
                if (problem is not null)
                {
                    await conn.SendAsync(new JsonObject { ["type"] = "reject", ["reason"] = problem }, ct).ConfigureAwait(false);
                    await conn.DisposeAsync().ConfigureAwait(false);
                    continue;
                }

                await conn.SendAsync(new JsonObject { ["type"] = "welcome", ["protocol"] = ProtocolInfo.Version }, ct).ConfigureAwait(false);
                Mod = new ModInfo(
                    (string?)hello!["modVersion"] ?? "?",
                    (string?)hello["gameVersion"] ?? "?",
                    hello["commands"]?.AsArray().Select(n => (string?)n ?? "").ToArray() ?? []);
            }
            catch (Exception ex) when (ex is not OperationCanceledException || !ct.IsCancellationRequested)
            {
                await conn.DisposeAsync().ConfigureAwait(false);
                continue;
            }

            l.Dispose();
            listener = null;
            DeleteQuietly(request.SocketPath);

            connection = conn;
            conn.EventReceived += (name, data) =>
            {
                if (name == "core.frame")
                {
                    if (FrameNotice.FromJson(data) is { } notice) FrameArrived?.Invoke(notice);
                    return;
                }
                EventReceived?.Invoke(name, data);
            };
            conn.Closed += OnClosed;
            conn.StartReceiving();
            pingLoop = Task.Run(() => PingLoopAsync(cts.Token), CancellationToken.None);
            return Mod!;
        }
    }

    string? ValidateHello(JsonObject? hello)
    {
        if (hello is null || (string?)hello["type"] != "hello") return "最初のメッセージが hello ではありません";
        if (!request.TokenMatches((string?)hello["token"])) return "トークンが一致しません";
        if ((int?)hello["protocol"] != ProtocolInfo.Version) return "プロトコルのバージョンが違います";
        return null;
    }

    async Task PingLoopAsync(CancellationToken ct)
    {
        using var timer = new PeriodicTimer(ProtocolInfo.PingInterval);
        try
        {
            while (await timer.WaitForNextTickAsync(ct).ConfigureAwait(false))
            {
                var conn = connection;
                if (conn is null || conn.IsClosed) return;
                if (DateTimeOffset.UtcNow - conn.LastReceivedAt > ProtocolInfo.PeerTimeout)
                {
                    await conn.DisposeAsync().ConfigureAwait(false);
                    OnClosed("Modからの応答が途絶えました");
                    return;
                }
                await conn.SendAsync(new JsonObject { ["type"] = "ping" }, ct).ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) when (ex is IOException or ObjectDisposedException) { }
    }

    void OnClosed(string reason)
    {
        if (Interlocked.Exchange(ref disconnected, 1) != 0) return;
        Disconnected?.Invoke(reason);
    }

    public async Task DisconnectAsync(string reason)
    {
        var conn = connection;
        if (conn is not null) await conn.DisposeAsync().ConfigureAwait(false);
        OnClosed(reason);
    }

    public Task<JsonNode?> RequestAsync(string command, JsonObject? parameters = null, TimeSpan? timeout = null, CancellationToken ct = default)
    {
        var conn = connection ?? throw new InvalidOperationException("Modと接続していません");
        return conn.RequestAsync(command, parameters, timeout ?? TimeSpan.FromSeconds(30), ct);
    }

    public async Task<Frame> CaptureAsync(int settleFrames = 2, CancellationToken ct = default)
    {
        var result = await RequestAsync("core.capture", new JsonObject { ["settleFrames"] = settleFrames }, TimeSpan.FromSeconds(30), ct).ConfigureAwait(false);
        var notice = FrameNotice.FromJson(result) ?? throw new InvalidDataException("capture の応答が不正です");
        return frameFile.Read(notice) ?? throw new InvalidDataException("フレームを読み出せませんでした（上書きされた可能性があります）");
    }

    public Task SetStreamFpsAsync(int fps, CancellationToken ct = default) =>
        RequestAsync("core.stream", new JsonObject { ["fps"] = fps }, ct: ct);

    public Frame? ReadFrame(FrameNotice notice) => frameFile.Read(notice);

    public Frame? ReadFrame(FrameNotice notice, byte[]? buffer) => frameFile.Read(notice, buffer);

    public async ValueTask DisposeAsync()
    {
        cts.Cancel();
        if (connection is not null) await connection.DisposeAsync().ConfigureAwait(false);
        if (pingLoop is not null)
        {
            try { await pingLoop.ConfigureAwait(false); } catch { }
        }
        listener?.Dispose();
        DeleteQuietly(request.SocketPath);
        DeleteIfOurs(SessionPaths.RequestFile);
        DeleteIfOurs(SessionPaths.AcceptedFile);
        frameFile.Dispose();
        cts.Dispose();
    }

    void DeleteIfOurs(string path)
    {
        try
        {
            if (!File.Exists(path)) return;
            var other = SessionRequest.FromJson(File.ReadAllText(path));
            if (other is not null && request.TokenMatches(other.Token)) File.Delete(path);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or System.Text.Json.JsonException) { }
    }

    static void CleanupStaleFiles()
    {
        foreach (var path in Directory.EnumerateFiles(SessionPaths.BaseDir))
        {
            var pid = SessionPaths.TryParseOwnerPid(Path.GetFileName(path));
            if (pid is null || pid == Environment.ProcessId || IsProcessAlive(pid.Value)) continue;
            DeleteQuietly(path);
        }
    }

    static bool IsProcessAlive(int pid)
    {
        try
        {
            using var p = System.Diagnostics.Process.GetProcessById(pid);
            return !p.HasExited;
        }
        catch (ArgumentException)
        {
            return false;
        }
        catch (InvalidOperationException)
        {
            return false;
        }
    }

    static void DeleteQuietly(string path)
    {
        try { File.Delete(path); } catch (IOException) { } catch (UnauthorizedAccessException) { }
    }
}
