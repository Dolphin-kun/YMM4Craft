using System.IO;
using System.Security.Cryptography;
using System.Text;
using YMM4Craft.Controls;
using YMM4Craft.Core;
using YMM4Craft.Core.Audio;
using YMM4Craft.Settings;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Json;

namespace YMM4Craft.Scene;

internal static class ReplayAudioExporter
{
    const int MaxCameraSamples = 20000;

    static readonly Lock gate = new();
    static readonly Dictionary<SceneShapeParameter, string> requested = new(ReferenceEqualityComparer.Instance);
    static readonly Dictionary<SceneShapeParameter, long> quietUntil = new(ReferenceEqualityComparer.Instance);
    static readonly HashSet<SceneShapeParameter> waiting = new(ReferenceEqualityComparer.Instance);
    const int QuietMilliseconds = 3000;
    static bool exporting;

    public static event Action<SceneShapeParameter, string>? Exported;

    public static event Action<SceneShapeParameter, double, string>? Progress;

    const double ModShare = 0.95;

    public static string DefaultFolder => Path.Combine(AppDirectories.TemporaryDirectory, "YMM4Craft", "ReplayAudio");

    public static string Folder => string.IsNullOrWhiteSpace(YMM4CraftSettings.Default.AudioFolder) ? DefaultFolder : YMM4CraftSettings.Default.AudioFolder.Trim().Trim('"');

    public static string? AutoPath(SceneShapeParameter p)
    {
        var (_, length) = p.PreviewPosition;
        var fps = p.PreviewFps;
        if (!p.IsReplay || length <= 0 || fps <= 0) return null;
        var signature = new StringBuilder()
            .Append(p.World).Append('|').Append(p.ReplayStart).Append('|').Append(length).Append('|').Append(fps).Append('|')
            .Append(p.ReplayCamera).Append('|').Append(p.ReplayPlayer).Append('|').Append(Json.GetJsonText(p.ReplaySpeed));
        if (!p.IsPlayerView)
        {
            foreach (var a in new Animation[] { p.CameraX, p.CameraY, p.CameraZ, p.Yaw, p.Pitch }) signature.Append('|').Append(Json.GetJsonText(a));
        }
        var hash = Convert.ToHexString(SHA1.HashData(Encoding.UTF8.GetBytes(signature.ToString())))[..12].ToLowerInvariant();
        var name = Path.GetFileNameWithoutExtension(p.World[CraftLink.ReplayPrefix.Length..]);
        foreach (var c in Path.GetInvalidFileNameChars()) name = name.Replace(c, '_');
        return Path.Combine(Folder, $"{name}_{hash}.wav");
    }

    public static void RequestAuto(SceneShapeParameter p)
    {
        if (!YMM4CraftSettings.Default.AutoExportAudio || CraftLink.Shared.State != CraftLinkState.Ready) return;
        lock (gate)
        {
            quietUntil[p] = Environment.TickCount64 + QuietMilliseconds;
            if (!waiting.Add(p)) return;
        }
        _ = WaitQuietAsync(p);
    }

    static async Task WaitQuietAsync(SceneShapeParameter p)
    {
        while (true)
        {
            long left;
            lock (gate) left = quietUntil[p] - Environment.TickCount64;
            if (left <= 0) break;
            await Task.Delay(TimeSpan.FromMilliseconds(left));
        }
        var path = YMM4CraftSettings.Default.AutoExportAudio && CraftLink.Shared.State == CraftLinkState.Ready ? AutoPath(p) : null;
        var retry = false;
        lock (gate)
        {
            waiting.Remove(p);
            if (path is null || (requested.TryGetValue(p, out var last) && last == path)) return;
            if (exporting)
            {
                quietUntil[p] = Environment.TickCount64 + QuietMilliseconds;
                if (!waiting.Add(p)) return;
                retry = true;
            }
            else
            {
                requested[p] = path;
                if (File.Exists(path)) return;
                exporting = true;
            }
        }
        if (retry) await WaitQuietAsync(p);
        else await RunAutoAsync(p, path);
    }

    static async Task RunAutoAsync(SceneShapeParameter p, string path)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            await ExportAsync(p, path);
            Exported?.Invoke(p, path);
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or UnauthorizedAccessException
                                       or InvalidDataException or YMM4Craft.Core.Protocol.CraftCommandException)
        {
            lock (gate) requested.Remove(p);
        }
        finally
        {
            lock (gate) exporting = false;
        }
    }

    public static async Task ExportAsync(SceneShapeParameter p, string output, bool exclusive = false)
    {
        var (_, length) = p.PreviewPosition;
        var fps = p.PreviewFps;
        if (length <= 0 || fps <= 0) throw new InvalidOperationException("プレビューを一度表示してから書き出してください");
        var times = p.ReplayTimes(length, fps);
        if (times[^1] - times[0] < 0.05) throw new InvalidOperationException("速さが 0 なので、書き出す音がありません");
        var temp = Path.Combine(Path.GetTempPath(), $"ymm4craft-audio-{Guid.NewGuid():N}.wav");
        void OnProgress(double f, bool waiting) =>
            Progress?.Invoke(p, ModShare * f, waiting ? "プレビューを動かしている間は止まります" : f < 0.85 ? "音を集めています" : "音を重ねています");
        Progress?.Invoke(p, 0, "書き出しを始めます");
        CraftLink.Shared.ReplayAudioProgress += OnProgress;
        try
        {
            var audio = await CraftLink.Shared.ExportReplayAudioAsync(times[0], times[^1], p.IsPlayerView ? null : CameraPath(p, length, fps, times), temp, exclusive);
            CraftLink.Shared.ReplayAudioProgress -= OnProgress;
            Progress?.Invoke(p, ModShare, "速さに合わせています");
            await Task.Run(() =>
            {
                var (left, right, rate) = WavFile.Read(temp);
                var (outLeft, outRight) = TimeStretch.Apply(left, right, rate, audio.Start, times, fps);
                WavFile.Write(output, outLeft, outRight, rate);
            });
        }
        finally
        {
            CraftLink.Shared.ReplayAudioProgress -= OnProgress;
            Progress?.Invoke(p, -1, "");
            try { File.Delete(temp); } catch (IOException) { }
        }
    }

    static List<(double Time, CameraView View)> CameraPath(SceneShapeParameter p, int length, int fps, double[] times)
    {
        var step = Math.Max(1, length / MaxCameraSamples);
        var path = new List<(double, CameraView)>();
        for (var f = 0; f <= length; f += step)
        {
            path.Add((times[f], new CameraView(
                p.CameraX.GetValue(f, length, fps), p.CameraY.GetValue(f, length, fps), p.CameraZ.GetValue(f, length, fps),
                Angles.Wrap(p.Yaw.GetValue(f, length, fps)), Math.Clamp(p.Pitch.GetValue(f, length, fps), -90, 90))));
        }
        return path;
    }
}
