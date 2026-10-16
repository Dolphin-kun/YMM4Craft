using System.Collections.Concurrent;
using System.Net.Sockets;
using System.Text;
using System.Text.Json.Nodes;

namespace YMM4Craft.Core.Protocol;

public sealed class JsonLineConnection(Socket socket) : IAsyncDisposable
{
    readonly Socket socket = socket;
    readonly NetworkStream stream = new(socket, ownsSocket: true);
    readonly SemaphoreSlim writeLock = new(1, 1);
    readonly ConcurrentDictionary<long, TaskCompletionSource<JsonNode?>> pending = new();
    readonly CancellationTokenSource cts = new();
    readonly byte[] readBuffer = new byte[64 * 1024];
    readonly MemoryStream lineBuffer = new();
    long nextId;
    Task? readLoop;
    int closed;

    public event Action<string, JsonNode?>? EventReceived;

    public event Action<string>? Closed;

    public DateTimeOffset LastReceivedAt { get; private set; } = DateTimeOffset.UtcNow;

    public async Task<JsonObject?> ReadMessageAsync(CancellationToken ct)
    {
        while (true)
        {
            if (TryTakeLine(out var line))
            {
                LastReceivedAt = DateTimeOffset.UtcNow;
                return JsonNode.Parse(line) as JsonObject ?? throw new InvalidDataException("メッセージがJSONオブジェクトではありません");
            }
            if (lineBuffer.Length > ProtocolInfo.MaxLineBytes)
                throw new InvalidDataException("メッセージが長すぎます");

            var n = await stream.ReadAsync(readBuffer, ct).ConfigureAwait(false);
            if (n == 0) return null;
            lineBuffer.Write(readBuffer, 0, n);
        }
    }

    bool TryTakeLine(out string line)
    {
        var data = lineBuffer.GetBuffer();
        var length = (int)lineBuffer.Length;
        var index = Array.IndexOf(data, (byte)'\n', 0, length);
        if (index < 0)
        {
            line = "";
            return false;
        }
        line = Encoding.UTF8.GetString(data, 0, index);
        var rest = data.AsSpan(index + 1, length - index - 1).ToArray();
        lineBuffer.SetLength(0);
        lineBuffer.Write(rest);
        return true;
    }

    public async Task SendAsync(JsonObject message, CancellationToken ct = default)
    {
        var bytes = Encoding.UTF8.GetBytes(message.ToJsonString() + "\n");
        await writeLock.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            await stream.WriteAsync(bytes, ct).ConfigureAwait(false);
        }
        finally
        {
            writeLock.Release();
        }
    }

    public void StartReceiving() => readLoop = Task.Run(ReceiveLoopAsync);

    async Task ReceiveLoopAsync()
    {
        try
        {
            while (!cts.IsCancellationRequested)
            {
                var message = await ReadMessageAsync(cts.Token).ConfigureAwait(false);
                if (message is null)
                {
                    Close("Modが接続を閉じました");
                    return;
                }
                Dispatch(message);
            }
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            Close($"受信エラー: {ex.Message}");
        }
        catch (OperationCanceledException)
        {
        }
    }

    void Dispatch(JsonObject message)
    {
        switch ((string?)message["type"])
        {
            case "response":
                var id = (long?)message["id"] ?? -1;
                if (!pending.TryRemove(id, out var tcs)) return;
                if ((bool?)message["ok"] == true)
                    tcs.TrySetResult(message["result"]?.DeepClone());
                else
                    tcs.TrySetException(new CraftCommandException("", (string?)message["error"] ?? "不明なエラー"));
                break;
            case "event":
                EventReceived?.Invoke((string?)message["name"] ?? "", message["data"]?.DeepClone());
                break;
        }
    }

    public async Task<JsonNode?> RequestAsync(string command, JsonObject? parameters, TimeSpan timeout, CancellationToken ct = default)
    {
        var id = Interlocked.Increment(ref nextId);
        var tcs = new TaskCompletionSource<JsonNode?>(TaskCreationOptions.RunContinuationsAsynchronously);
        pending[id] = tcs;
        try
        {
            var request = new JsonObject
            {
                ["type"] = "request",
                ["id"] = id,
                ["command"] = command,
            };
            if (parameters is not null) request["params"] = parameters;
            await SendAsync(request, ct).ConfigureAwait(false);

            try
            {
                return await tcs.Task.WaitAsync(timeout, ct).ConfigureAwait(false);
            }
            catch (CraftCommandException ex)
            {
                throw new CraftCommandException(command, ex.Message);
            }
            catch (TimeoutException)
            {
                throw new TimeoutException($"{command} の応答がありません（{timeout.TotalSeconds:0}秒）");
            }
        }
        finally
        {
            pending.TryRemove(id, out _);
        }
    }

    void Close(string reason)
    {
        if (Interlocked.Exchange(ref closed, 1) != 0) return;
        cts.Cancel();
        foreach (var (_, tcs) in pending)
            tcs.TrySetException(new IOException($"接続が切れました: {reason}"));
        pending.Clear();
        try { socket.Shutdown(SocketShutdown.Both); } catch { }
        stream.Dispose();
        Closed?.Invoke(reason);
    }

    public bool IsClosed => Volatile.Read(ref closed) != 0;

    public async ValueTask DisposeAsync()
    {
        Close("ホストが接続を閉じました");
        if (readLoop is not null)
        {
            try { await readLoop.ConfigureAwait(false); } catch { }
        }
        writeLock.Dispose();
        cts.Dispose();
    }
}
