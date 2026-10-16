using System.Text.Json.Nodes;
using System.Diagnostics;
using YMM4Craft.Core.Frames;
using YMM4Craft.Core.Protocol;

namespace YMM4Craft.Core;

public sealed class CraftLink : IAsyncDisposable
{
    public static CraftLink Shared { get; } = new();

    static readonly TimeSpan IdleTimeout = TimeSpan.FromMinutes(5);

    static readonly TimeSpan RequestLifetime = TimeSpan.FromMinutes(2.5);

    readonly Lock gate = new();
    readonly CancellationTokenSource cts = new();
    Task? loop;
    CraftHost? host;
    DateTimeOffset lastUsedAt;

    string? requestedWorld;
    (CraftHost Host, SceneCatalog Catalog)? catalog;
    readonly Dictionary<string, Task<string?>> skinUploads = [];
    CraftHost? skinHost;
    (string Name, WorldKind Kind, TaskCompletionSource<string> Done)? pendingCreate;

    bool paused;
    (int Width, int Height)? requestedResolution;
    (int Width, int Height)? sentResolution;

    long nextRevision;
    string? lastSubmittedJson;
    long lastSubmittedRevision;
    (JsonObject Scene, long Revision)? pendingScene;
    bool sending;

    readonly object frameGate = new();
    FrameNotice? latestNotice;
    CraftHost? frameHost;
    Frame? cachedFrame;
    byte[]? spareBuffer;

    const int IdleStreamFps = 2;
    int activeStreamFps = 36;
    const int EditorStreamFps = 72;
    const int MaxStreamFps = 144;
    int highFpsRequests;
    static readonly TimeSpan ActiveDuration = TimeSpan.FromSeconds(8);
    DateTimeOffset lastActivityAt = DateTimeOffset.UtcNow;
    int sentStreamFps = -1;

    public CraftLinkState State { get; private set; }
    public string StatusMessage { get; private set; } = "";

    public string? OpenedWorld { get; private set; }

    public bool IsReplayLoading { get; private set; }

    public bool IsExportingAudio { get; private set; }

    public bool IsLoading => IsReplayLoading || State == CraftLinkState.LoadingWorld;

    public event Action<double, bool>? ReplayAudioProgress;

    public Func<int, int, GpuShareOffer?>? GpuShareProvider { get; set; }

    public GpuShareOffer? ActiveGpuShare { get; private set; }

    public string? GpuShareProblem { get; private set; }

    public void DisableGpuShare(string reason)
    {
        CraftHost? h;
        lock (gate)
        {
            if (ActiveGpuShare is null && GpuShareProblem is not null) return;
            ActiveGpuShare = null;
            GpuShareProblem = reason;
            h = host;
        }
        if (h is not null) _ = h.RequestAsync("core.gpuShare", new JsonObject { ["disable"] = true });
    }

    public event Action? Changed;

    public FrameNotice? LatestNotice
    {
        get { lock (frameGate) return latestNotice; }
    }

    public FrameNotice? WaitForNotice(long revision, TimeSpan timeout, bool requireSettled = false)
    {
        NoteActivity();
        var deadline = DateTime.UtcNow + timeout;
        lock (frameGate)
        {
            while (true)
            {
                var notice = latestNotice;
                if (notice is not null && notice.Revision >= revision && (!requireSettled || notice.Settled)) return notice;
                var left = deadline - DateTime.UtcNow;
                if (left <= TimeSpan.Zero || State != CraftLinkState.Ready) return null;
                Monitor.Wait(frameGate, left);
            }
        }
    }

    public Frame? LatestFrame
    {
        get { lock (frameGate) return ReadLatestLocked(); }
    }

    Frame? ReadLatestLocked()
    {
        var notice = latestNotice;
        if (notice is null || notice.GpuSlot >= 0 || frameHost is null || cachedFrame?.Sequence == notice.Sequence) return cachedFrame;
        var frame = frameHost.ReadFrame(notice, spareBuffer);
        if (frame is null) return cachedFrame;
        spareBuffer = cachedFrame?.Pixels;
        cachedFrame = frame;
        return cachedFrame;
    }

    public bool IsWaiting
    {
        get { lock (gate) return host is null && loop is { IsCompleted: false }; }
    }

    public bool IsPaused
    {
        get { lock (gate) return paused; }
    }

    public bool IsConnected
    {
        get { lock (gate) return host is not null; }
    }

    public void NoteActivity()
    {
        lastActivityAt = DateTimeOffset.UtcNow;
        if (sentStreamFps != activeStreamFps && Volatile.Read(ref highFpsRequests) == 0) _ = UpdateStreamFpsAsync();
    }

    public void SetProjectFps(int fps)
    {
        var wanted = Math.Clamp((int)Math.Ceiling(Math.Max(fps, 1) * 1.2), 30, MaxStreamFps);
        if (Interlocked.Exchange(ref activeStreamFps, wanted) != wanted) _ = UpdateStreamFpsAsync();
    }

    public IDisposable RequestHighFps()
    {
        Interlocked.Increment(ref highFpsRequests);
        _ = UpdateStreamFpsAsync();
        return new Release(this);
    }

    sealed class Release(CraftLink link) : IDisposable
    {
        int released;

        public void Dispose()
        {
            if (Interlocked.Exchange(ref released, 1) != 0) return;
            Interlocked.Decrement(ref link.highFpsRequests);
            link.NoteActivity();
            _ = link.UpdateStreamFpsAsync();
        }
    }

    async Task UpdateStreamFpsAsync()
    {
        CraftHost? h;
        int fps;
        lock (gate)
        {
            h = host;
            fps = highFpsRequests > 0 ? Math.Max(EditorStreamFps, activeStreamFps)
                : DateTimeOffset.UtcNow - lastActivityAt < ActiveDuration ? activeStreamFps : IdleStreamFps;
            if (h is null || fps == sentStreamFps) return;
            sentStreamFps = fps;
        }
        try
        {
            await h.SetStreamFpsAsync(fps).ConfigureAwait(false);
        }
        catch (Exception ex) when (ex is IOException or TimeoutException or InvalidOperationException or CraftCommandException)
        {
            lock (gate) sentStreamFps = -1;
        }
    }

    public void EnsureStarted()
    {
        lock (gate)
        {
            lastUsedAt = DateTimeOffset.UtcNow;
            if (paused || loop is { IsCompleted: false }) return;
            loop = Task.Run(() => RunAsync(cts.Token));
        }
    }

    public void Resume()
    {
        lock (gate) paused = false;
        EnsureStarted();
    }

    public void Disconnect()
    {
        CraftHost? h;
        lock (gate)
        {
            paused = true;
            h = host;
        }
        Changed?.Invoke();
        if (h is not null) _ = h.DisconnectAsync("YMM4で切断しました");
    }

    public async Task<JsonNode?> StatusAsync(CancellationToken ct = default)
    {
        CraftHost h;
        lock (gate) h = host ?? throw new InvalidOperationException("Minecraftと接続していません");
        return await h.RequestAsync("core.status", ct: ct).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<string>> ListWorldsAsync(CancellationToken ct = default)
    {
        CraftHost h;
        lock (gate) h = host ?? throw new InvalidOperationException("Minecraftと接続していません");
        var result = await h.RequestAsync("core.listWorlds", ct: ct).ConfigureAwait(false);
        return result?["worlds"]?.AsArray().Select(n => (string?)n).OfType<string>().ToList() ?? [];
    }

    public async Task<SceneCatalog> GetCatalogAsync(CancellationToken ct = default)
    {
        CraftHost h;
        lock (gate)
        {
            h = host ?? throw new InvalidOperationException("Minecraftと接続していません");
            if (catalog is { } cached && ReferenceEquals(cached.Host, h)) return cached.Catalog;
        }
        var result = await h.RequestAsync("scene.catalog", ct: ct).ConfigureAwait(false);
        var made = new SceneCatalog(Entries(result?["mobs"]), Entries(result?["items"]));
        lock (gate) catalog = (h, made);
        return made;

        static IReadOnlyList<CatalogEntry> Entries(JsonNode? node) =>
            node?.AsArray()
                .Select(n => (Id: (string?)n?["id"], Name: (string?)n?["name"]))
                .Where(e => e.Id is not null)
                .Select(e => new CatalogEntry(e.Id!, e.Name ?? e.Id!))
                .ToList() ?? [];
    }

    public async Task<IReadOnlyList<(string Name, int Depth)>> GetModelPartsAsync(string kind, string type, bool baby, CancellationToken ct = default)
    {
        CraftHost h;
        lock (gate) h = host ?? throw new InvalidOperationException("Minecraftと接続していません");
        var args = new JsonObject { ["kind"] = kind, ["baby"] = baby };
        if (kind == "mob") args["type"] = type;
        var result = await h.RequestAsync("scene.modelParts", args, ct: ct).ConfigureAwait(false);
        return result?["parts"]?.AsArray()
            .Select(n => ((string?)n?["name"], (int?)n?["depth"] ?? 0))
            .Where(p => p.Item1 is not null)
            .Select(p => (p.Item1!, p.Item2))
            .ToList() ?? [];
    }

    public string? UploadSkin(byte[] png, TimeSpan wait)
    {
        var key = Convert.ToHexString(System.Security.Cryptography.SHA1.HashData(png));
        Task<string?> task;
        lock (gate)
        {
            if (host is not { } h) return null;
            if (!ReferenceEquals(skinHost, h))
            {
                skinUploads.Clear();
                skinHost = h;
            }
            if (!skinUploads.TryGetValue(key, out task!))
            {
                task = Upload(h, png);
                skinUploads[key] = task;
            }
        }
        try
        {
            return task.Wait(wait) ? task.Result : null;
        }
        catch (AggregateException)
        {
            return null;
        }

        static async Task<string?> Upload(CraftHost h, byte[] png)
        {
            try
            {
                var result = await h.RequestAsync("scene.uploadSkin", new JsonObject { ["png"] = Convert.ToBase64String(png) }).ConfigureAwait(false);
                return (string?)result?["skin"];
            }
            catch (Exception ex) when (ex is IOException or TimeoutException or Protocol.CraftCommandException or InvalidOperationException)
            {
                Trace.WriteLine($"YMM4Craft: スキンを送れませんでした: {ex.Message}");
                return null;
            }
        }
    }

    public async Task EditorViewAsync(string modelKey, double yaw, double pitch, double distance, CancellationToken ct = default)
    {
        var args = new JsonObject { ["model"] = modelKey, ["yaw"] = yaw, ["pitch"] = Math.Clamp(pitch, -90, 90), ["distance"] = Math.Clamp(distance, 0.5, 64) };
        await RequireHost().RequestAsync("scene.editor", args, ct: ct).ConfigureAwait(false);
    }

    public async Task SetModelPoseAsync(string modelKey, JsonArray pose, CancellationToken ct = default)
    {
        await RequireHost().RequestAsync("scene.modelPose", new JsonObject { ["model"] = modelKey, ["pose"] = pose }, ct: ct).ConfigureAwait(false);
    }

    public async Task EndEditorAsync()
    {
        CraftHost? h;
        lock (gate) h = host;
        if (h is null) return;
        try
        {
            await h.RequestAsync("scene.editor", []).ConfigureAwait(false);
        }
        catch (Exception ex) when (ex is IOException or TimeoutException or Protocol.CraftCommandException or InvalidOperationException)
        {
        }
    }

    public async Task<CameraView?> GetViewAsync(CancellationToken ct = default)
    {
        var view = (await RequireHost().RequestAsync("core.status", ct: ct).ConfigureAwait(false))?["view"];
        return view is null ? null : new CameraView((double?)view["x"] ?? 0, (double?)view["y"] ?? 0, (double?)view["z"] ?? 0,
            (double?)view["yaw"] ?? 0, (double?)view["pitch"] ?? 0);
    }

    public async Task<(CameraView View, bool IsRecordedPlayer)?> GetHomeAsync(CancellationToken ct = default)
    {
        var home = await RequireHost().RequestAsync("scene.home", ct: ct).ConfigureAwait(false);
        if (home is null) return null;
        return (new CameraView((double?)home["x"] ?? 0, (double?)home["y"] ?? 0, (double?)home["z"] ?? 0,
            (double?)home["yaw"] ?? 0, (double?)home["pitch"] ?? 0), (string?)home["kind"] == "recorded");
    }

    public async Task<ReplayAudio> ExportReplayAudioAsync(double from, double to, IReadOnlyList<(double Time, CameraView View)>? camera, string path, bool exclusive = false, CancellationToken ct = default)
    {
        var request = new JsonObject { ["from"] = from, ["to"] = to, ["path"] = path, ["exclusive"] = exclusive };
        if (camera is not null)
        {
            request["camera"] = new JsonArray([.. camera.Select(c => (JsonNode)new JsonObject
            {
                ["t"] = c.Time, ["x"] = c.View.X, ["y"] = c.View.Y, ["z"] = c.View.Z, ["yaw"] = c.View.Yaw, ["pitch"] = c.View.Pitch,
            })]);
        }
        var result = await RequireHost().RequestAsync("replay.exportAudio", request, TimeSpan.FromMinutes(30), ct).ConfigureAwait(false);
        return new ReplayAudio((double?)result?["start"] ?? from, (double?)result?["seconds"] ?? 0, (int?)result?["sounds"] ?? 0);
    }

    public async Task<(string Recorded, List<string> Others)> ListReplayPlayersAsync(CancellationToken ct = default)
    {
        var result = await RequireHost().RequestAsync("replay.players", ct: ct).ConfigureAwait(false);
        return ((string?)result?["recorded"] ?? "", result?["players"]?.AsArray().Select(n => (string?)n).OfType<string>().ToList() ?? []);
    }

    public async Task<double?> GetGroundAsync(double x, double z, CancellationToken ct = default)
    {
        var result = await RequireHost().RequestAsync("scene.ground", new JsonObject { ["x"] = x, ["z"] = z }, ct: ct).ConfigureAwait(false);
        return (double?)result?["y"];
    }

    public async Task<JsonObject?> GetGizmoAsync(string part, CancellationToken ct = default)
    {
        var result = await RequireHost().RequestAsync("scene.gizmo", new JsonObject { ["part"] = part }, ct: ct).ConfigureAwait(false);
        return result is JsonObject o && o.ContainsKey("pivot") ? o : null;
    }

    public async Task<string?> PickAsync(double x, double y, CancellationToken ct = default)
    {
        var result = await RequireHost().RequestAsync("scene.pick", new JsonObject { ["x"] = Math.Clamp(x, 0, 1), ["y"] = Math.Clamp(y, 0, 1) }, ct: ct).ConfigureAwait(false);
        return (string?)result?["part"];
    }

    public Task<Frame> CaptureAsync(CancellationToken ct = default) => RequireHost().CaptureAsync(settleFrames: 1, ct);

    CraftHost RequireHost()
    {
        lock (gate) return host ?? throw new InvalidOperationException("Minecraftと接続していません");
    }

    public SceneCatalog? CachedCatalog
    {
        get { lock (gate) return catalog?.Catalog; }
    }

    public Task<string> CreateWorldAsync(string name, WorldKind kind)
    {
        var done = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        TaskCompletionSource changed;
        lock (gate)
        {
            if (host is null) throw new InvalidOperationException("Minecraftと接続していません");
            pendingCreate?.Done.TrySetCanceled();
            pendingCreate = (name, kind, done);
            changed = worldChanged;
        }
        changed.TrySetResult();
        return done.Task;
    }

    public const string ReplayPrefix = "replay:";

    public void RequestReplay(string? replay) =>
        RequestWorld(string.IsNullOrWhiteSpace(replay) ? null : ReplayPrefix + replay.Trim());

    public async Task<IReadOnlyList<ReplayInfo>> ListReplaysAsync(CancellationToken ct = default)
    {
        var result = await RequireHost().RequestAsync("replay.list", ct: ct).ConfigureAwait(false);
        return result?["replays"]?.AsArray()
            .Select(n => n is null || (string?)n["name"] is not { } name ? null : new ReplayInfo(
                name,
                TimeSpan.FromMilliseconds((long?)n["durationMs"] ?? 0),
                (string?)n["world"] ?? "",
                (string?)n["player"] ?? "",
                (bool?)n["playable"] ?? true,
                (string?)n["source"] ?? ""))
            .OfType<ReplayInfo>()
            .ToList() ?? [];
    }

    public void RequestWorld(string? world)
    {
        world = string.IsNullOrWhiteSpace(world) ? null : world.Trim();
        TaskCompletionSource changed;
        lock (gate)
        {
            if (requestedWorld == world) return;
            requestedWorld = world;
            worldVersion++;
            changed = worldChanged;
        }
        changed.TrySetResult();
    }

    TaskCompletionSource worldChanged = new(TaskCreationOptions.RunContinuationsAsynchronously);
    long worldVersion;

    static readonly TimeSpan WorldInputDebounce = TimeSpan.FromMilliseconds(300);

    public void RequestResolution(int width, int height)
    {
        lock (gate) requestedResolution = (width, height);
        _ = SendResolutionIfNeededAsync();
    }

    public long SubmitScene(JsonObject scene)
    {
        var json = scene.ToJsonString();
        lock (gate)
        {
            if (json == lastSubmittedJson) return lastSubmittedRevision;
            lastSubmittedJson = json;
            lastSubmittedRevision = ++nextRevision;
            pendingScene = (scene, lastSubmittedRevision);
            if (!sending && State == CraftLinkState.Ready)
            {
                sending = true;
                _ = Task.Run(SendScenesAsync);
            }
            return lastSubmittedRevision;
        }
    }

    public Frame? WaitForFrame(long revision, TimeSpan timeout, bool requireSettled = false)
    {
        var deadline = DateTime.UtcNow + timeout;
        NoteActivity();
        lock (frameGate)
        {
            while (true)
            {
                var notice = latestNotice;
                if (notice is not null && notice.Revision >= revision && (!requireSettled || notice.Settled))
                {
                    var frame = ReadLatestLocked();
                    if (frame is not null && frame.Sequence == notice.Sequence) return frame;
                }
                var left = deadline - DateTime.UtcNow;
                if (left <= TimeSpan.Zero || State != CraftLinkState.Ready) return null;
                Monitor.Wait(frameGate, left);
            }
        }
    }

    async Task SendScenesAsync()
    {
        while (true)
        {
            (JsonObject Scene, long Revision) item;
            CraftHost? h;
            lock (gate)
            {
                if (pendingScene is null || State != CraftLinkState.Ready || host is null)
                {
                    sending = false;
                    return;
                }
                item = pendingScene.Value;
                pendingScene = null;
                h = host;
            }

            var scene = (JsonObject)item.Scene.DeepClone();
            scene["revision"] = item.Revision;
            try
            {
                await h.RequestAsync("scene.apply", scene, TimeSpan.FromSeconds(10)).ConfigureAwait(false);
            }
            catch (CraftCommandException ex)
            {
                SetStatus(State, $"シーンを反映できませんでした: {ex.Message}");
            }
            catch (Exception ex) when (ex is IOException or TimeoutException or InvalidOperationException)
            {
            }
        }
    }

    async Task SendResolutionIfNeededAsync()
    {
        CraftHost? h;
        (int Width, int Height) resolution;
        lock (gate)
        {
            if (State is not (CraftLinkState.Ready or CraftLinkState.LoadingWorld or CraftLinkState.NeedWorld) || host is null) return;
            if (requestedResolution is null || requestedResolution == sentResolution) return;
            resolution = requestedResolution.Value;
            sentResolution = resolution;
            h = host;
        }
        try
        {
            await h.RequestAsync("core.setResolution", new JsonObject { ["width"] = resolution.Width, ["height"] = resolution.Height }).ConfigureAwait(false);
        }
        catch (Exception ex) when (ex is CraftCommandException or IOException or TimeoutException or InvalidOperationException)
        {
            lock (gate) sentResolution = null;
            return;
        }
        await OfferGpuShareAsync(h, resolution.Width, resolution.Height).ConfigureAwait(false);
    }

    async Task OfferGpuShareAsync(CraftHost h, int width, int height)
    {
        if (GpuShareProvider is null || GpuShareProblem is not null) return;
        GpuShareOffer? offer;
        try
        {
            offer = GpuShareProvider(width, height);
        }
        catch (Exception ex)
        {
            GpuShareProblem = $"共有テクスチャを作れませんでした: {ex.Message}";
            return;
        }
        if (offer is null) return;

        var handles = new JsonObject { ["count"] = offer.Handles.Count };
        for (var i = 0; i < offer.Handles.Count; i++) handles[i.ToString()] = offer.Handles[i];
        try
        {
            await h.RequestAsync("core.gpuShare", new JsonObject
            {
                ["generation"] = offer.Generation,
                ["luidLow"] = offer.LuidLow,
                ["luidHigh"] = offer.LuidHigh,
                ["width"] = offer.Width,
                ["height"] = offer.Height,
                ["handles"] = handles,
            }, TimeSpan.FromSeconds(10)).ConfigureAwait(false);
            lock (gate) ActiveGpuShare = offer;
        }
        catch (CraftCommandException ex)
        {
            lock (gate)
            {
                ActiveGpuShare = null;
                GpuShareProblem = ex.Message;
            }
        }
        catch (Exception ex) when (ex is IOException or TimeoutException or InvalidOperationException)
        {
        }
    }

    async Task RunAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            lock (gate)
            {
                if (paused)
                {
                    SetStatus(CraftLinkState.Idle, "切断しました。「接続を待つ」を押すと、また Minecraft からの接続を待ちます");
                    return;
                }
                if (DateTimeOffset.UtcNow - lastUsedAt > IdleTimeout)
                {
                    SetStatus(CraftLinkState.Idle, "");
                    return;
                }
            }

            try
            {
                await RunOneSessionAsync(ct).ConfigureAwait(false);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                return;
            }
            catch (Exception ex)
            {
                SetStatus(CraftLinkState.Error, $"連携でエラーが起きました: {ex.Message}");
                try { await Task.Delay(TimeSpan.FromSeconds(5), ct).ConfigureAwait(false); } catch (OperationCanceledException) { return; }
            }
        }
    }

    async Task RunOneSessionAsync(CancellationToken ct)
    {
        await using var h = CraftHost.Start(["scene"]);
        var disconnected = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        var worldReady = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        h.Disconnected += reason => disconnected.TrySetResult(reason);
        h.EventReceived += (name, data) =>
        {
            if (name == "replay.loading")
            {
                IsReplayLoading = (bool?)data?["loading"] ?? false;
                IsExportingAudio = (bool?)data?["audio"] ?? false;
                Changed?.Invoke();
            }
            if (name == "replay.audioProgress") ReplayAudioProgress?.Invoke((double?)data?["progress"] ?? 0, (bool?)data?["waiting"] ?? false);
            if (name == "core.worldReady") worldReady.TrySetResult(true);
            if (name == "core.worldFailed") worldReady.TrySetResult(false);
            if (name == "core.gpuShareFailed")
            {
                lock (gate)
                {
                    ActiveGpuShare = null;
                    GpuShareProblem = "Minecraft側でGPUでの共有に失敗しました";
                }
            }
        };
        h.FrameArrived += notice =>
        {
            lock (frameGate)
            {
                if (frameHost == h && latestNotice is not null && latestNotice.Sequence > notice.Sequence) return;
                latestNotice = notice;
                frameHost = h;
                Monitor.PulseAll(frameGate);
            }
            Changed?.Invoke();
        };

        if (State != CraftLinkState.WaitingForMinecraft || StatusMessage.Length == 0)
            SetStatus(CraftLinkState.WaitingForMinecraft, "Minecraft のタイトル画面の YMM4 のボタンから「YMM4と接続」を押してください");
        using (var wait = CancellationTokenSource.CreateLinkedTokenSource(ct))
        {
            wait.CancelAfter(RequestLifetime);
            try
            {
                var connecting = h.WaitForModAsync(wait.Token);
                while (!connecting.IsCompleted)
                {
                    await Task.WhenAny(connecting, Task.Delay(1000, ct)).ConfigureAwait(false);
                    if (!connecting.IsCompleted && h.RejectionMessage is { } rejected && StatusMessage != rejected)
                        SetStatus(CraftLinkState.WaitingForMinecraft, rejected);
                }
                await connecting.ConfigureAwait(false);
            }
            catch (OperationCanceledException) when (!ct.IsCancellationRequested)
            {
                return;
            }
        }

        lock (gate)
        {
            host = h;
            sentResolution = null;
            ActiveGpuShare = null;
            GpuShareProblem = null;
        }
        try
        {
            SetStatus(CraftLinkState.LoadingWorld, "ワールドを開いています…");
            await SendResolutionIfNeededAsync().ConfigureAwait(false);
            lock (gate) sentStreamFps = -1;
            lastActivityAt = DateTimeOffset.UtcNow;
            await UpdateStreamFpsAsync().ConfigureAwait(false);
            using var idleTimer = new Timer(_ => _ = UpdateStreamFpsAsync(), null, TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(1));

            while (!ct.IsCancellationRequested)
            {
                Task changed;
                string? world;
                (string Name, WorldKind Kind, TaskCompletionSource<string> Done)? create;
                lock (gate)
                {
                    worldChanged = new(TaskCreationOptions.RunContinuationsAsynchronously);
                    changed = worldChanged.Task;
                    world = requestedWorld;
                    create = pendingCreate;
                    pendingCreate = null;
                }

                if (create is { } c)
                {
                    SetStatus(CraftLinkState.LoadingWorld, $"ワールド「{c.Name}」を作っています…");
                    lock (gate) OpenedWorld = null;
                    worldReady = new(TaskCreationOptions.RunContinuationsAsynchronously);
                    try
                    {
                        var created = await h.RequestAsync("core.createWorld", new JsonObject { ["name"] = c.Name, ["type"] = c.Kind switch { WorldKind.Flat => "flat", WorldKind.Empty => "void", _ => "normal" } }, ct: ct).ConfigureAwait(false);
                        var folder = (string?)created?["world"] ?? c.Name;
                        var finished = await Task.WhenAny(worldReady.Task, disconnected.Task, Task.Delay(TimeSpan.FromMinutes(5), ct)).ConfigureAwait(false);
                        if (finished == disconnected.Task)
                        {
                            c.Done.TrySetCanceled(CancellationToken.None);
                            break;
                        }
                        if (finished == worldReady.Task && worldReady.Task.Result)
                        {
                            lock (gate)
                            {
                                OpenedWorld = folder;
                                requestedWorld = folder;
                                worldVersion++;
                            }
                            c.Done.TrySetResult(folder);
                            continue;
                        }
                        c.Done.TrySetException(new CraftCommandException("core.createWorld", $"ワールド「{c.Name}」を作れませんでした"));
                        SetStatus(CraftLinkState.Error, $"ワールド「{c.Name}」を作れませんでした");
                    }
                    catch (CraftCommandException ex)
                    {
                        c.Done.TrySetException(ex);
                        SetStatus(CraftLinkState.Error, ex.Message);
                    }
                    var nextAfterCreate = await Task.WhenAny(disconnected.Task, changed, Task.Delay(Timeout.Infinite, ct)).ConfigureAwait(false);
                    if (nextAfterCreate != changed) break;
                    continue;
                }

                var target = world;
                var isReplay = target?.StartsWith(ReplayPrefix, StringComparison.Ordinal) == true;
                var worlds = isReplay
                    ? (await h.RequestAsync("replay.list", ct: ct).ConfigureAwait(false))?["replays"]?.AsArray()
                        .Select(n => (string?)n?["name"]).OfType<string>().Select(n => ReplayPrefix + n).ToList() ?? []
                    : (await h.RequestAsync("core.listWorlds", ct: ct).ConfigureAwait(false))?["worlds"]?.AsArray()
                        .Select(n => (string?)n).OfType<string>().ToList() ?? [];
                string? problem = null;
                if (target is null)
                {
                    SetStatus(CraftLinkState.NeedWorld, worlds.Count == 0
                        ? "ワールドがありません。ワールド名を入れて「新規作成」を押してください"
                        : "ワールドを選んで「開く」を押してください");
                    var nextWithoutWorld = await Task.WhenAny(disconnected.Task, changed, Task.Delay(Timeout.Infinite, ct)).ConfigureAwait(false);
                    if (nextWithoutWorld != changed) break;
                    continue;
                }
                var label = isReplay ? $"リプレイ「{target[ReplayPrefix.Length..]}」" : $"ワールド「{target}」";
                if (!worlds.Contains(target))
                    problem = isReplay
                        ? $"{label}が見つかりません（Minecraftの ymm4craft/replays にあるものを選んでください）"
                        : $"{label}が見つかりません（ある: {string.Join(", ", worlds)}）";

                if (problem is null && target != OpenedWorld)
                {
                    SetStatus(CraftLinkState.LoadingWorld, $"{label}を開いています…");
                    lock (gate) OpenedWorld = null;
                    worldReady = new(TaskCreationOptions.RunContinuationsAsynchronously);
                    try
                    {
                        if (isReplay)
                            await h.RequestAsync("replay.open", new JsonObject { ["name"] = target[ReplayPrefix.Length..] }, TimeSpan.FromMinutes(3), ct).ConfigureAwait(false);
                        else
                            await h.RequestAsync("core.openWorld", new JsonObject { ["world"] = target }, TimeSpan.FromMinutes(3), ct).ConfigureAwait(false);
                        var opened = await Task.WhenAny(worldReady.Task, disconnected.Task, Task.Delay(TimeSpan.FromMinutes(3), ct)).ConfigureAwait(false);
                        if (opened == disconnected.Task) break;
                        if (opened != worldReady.Task || !worldReady.Task.Result)
                            problem = $"{label}を開けませんでした";
                        else
                            lock (gate) OpenedWorld = target;
                    }
                    catch (CraftCommandException ex)
                    {
                        problem = ex.Message;
                    }
                }

                if (problem is not null)
                {
                    SetStatus(CraftLinkState.Error, problem);
                }
                else
                {
                    lock (gate)
                    {
                        if (lastSubmittedJson is not null)
                            pendingScene = ((JsonObject)JsonNode.Parse(lastSubmittedJson)!, lastSubmittedRevision);
                    }
                    SetStatus(CraftLinkState.Ready, $"接続中: {label}");
                    lock (gate)
                    {
                        if (pendingScene is not null && !sending)
                        {
                            sending = true;
                            _ = Task.Run(SendScenesAsync, CancellationToken.None);
                        }
                    }
                }

                var next = await Task.WhenAny(disconnected.Task, changed, Task.Delay(Timeout.Infinite, ct)).ConfigureAwait(false);
                if (next != changed) break;
                bool creating;
                lock (gate) creating = pendingCreate is not null;
                if (!creating) await WaitForWorldInputToSettleAsync(ct).ConfigureAwait(false);
            }
        }
        finally
        {
            lock (gate)
            {
                host = null;
                OpenedWorld = null;
                ActiveGpuShare = null;
            }
            IsReplayLoading = false;
            IsExportingAudio = false;
            lock (frameGate)
            {
                latestNotice = null;
                frameHost = null;
                cachedFrame = null;
                spareBuffer = null;
            }
        }
        if (disconnected.Task.IsCompleted)
            SetStatus(CraftLinkState.WaitingForMinecraft, $"Minecraftとの接続が切れました（{disconnected.Task.Result}）");
    }

    async Task WaitForWorldInputToSettleAsync(CancellationToken ct)
    {
        while (true)
        {
            long version;
            lock (gate) version = worldVersion;
            await Task.Delay(WorldInputDebounce, ct).ConfigureAwait(false);
            lock (gate)
            {
                if (version == worldVersion) return;
            }
        }
    }

    void SetStatus(CraftLinkState state, string message)
    {
        State = state;
        StatusMessage = message;
        if (state != CraftLinkState.Ready)
        {
            lock (frameGate) Monitor.PulseAll(frameGate);
        }
        Changed?.Invoke();
    }

    public async ValueTask DisposeAsync()
    {
        cts.Cancel();
        if (loop is not null)
        {
            try { await loop.ConfigureAwait(false); } catch { }
        }
        cts.Dispose();
    }
}
