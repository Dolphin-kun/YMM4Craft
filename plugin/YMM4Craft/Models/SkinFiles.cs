using System.Collections.Concurrent;
using System.IO;
using System.Security.Cryptography;
using YMM4Craft.Core;
using YMM4Craft.Scene;

namespace YMM4Craft.Models;

internal static class SkinFiles
{
    const long MaxBytes = 512 * 1024;
    const long CheckIntervalMs = 1000;
    static readonly TimeSpan ExportWait = TimeSpan.FromSeconds(3);
    static readonly ConcurrentDictionary<string, (DateTime Modified, long Length, byte[]? Data, string? Key, long CheckedAt)> files = new(StringComparer.OrdinalIgnoreCase);

    public static string? IdOf(string path, bool waitForUpload)
    {
        if (Read(path) is not { } skin) return null;
        var task = CraftLink.Shared.UploadSkinAsync(skin.Key, skin.Data);
        if (task is null) return null;
        try
        {
            if (task.IsCompleted || (waitForUpload && task.Wait(ExportWait))) return task.IsCompletedSuccessfully ? task.Result : null;
        }
        catch (AggregateException)
        {
            return null;
        }
        task.ContinueWith(_ => PreviewRefresher.Request(), TaskScheduler.Default);
        return null;
    }

    static (byte[] Data, string Key)? Read(string path)
    {
        var now = Environment.TickCount64;
        if (files.TryGetValue(path, out var cached) && now - cached.CheckedAt < CheckIntervalMs)
            return cached.Data is null ? null : (cached.Data, cached.Key!);
        try
        {
            var info = new FileInfo(path);
            if (!info.Exists || info.Length > MaxBytes)
            {
                files[path] = (default, 0, null, null, now);
                return null;
            }
            if (cached.Data is not null && cached.Modified == info.LastWriteTimeUtc && cached.Length == info.Length)
            {
                files[path] = cached with { CheckedAt = now };
                return (cached.Data, cached.Key!);
            }
            var data = File.ReadAllBytes(path);
            var key = Convert.ToHexString(SHA1.HashData(data));
            files[path] = (info.LastWriteTimeUtc, info.Length, data, key, now);
            return (data, key);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or ArgumentException or NotSupportedException)
        {
            return null;
        }
    }
}
