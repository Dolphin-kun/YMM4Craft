using System.Collections.Concurrent;
using System.IO;
using YMM4Craft.Core;

namespace YMM4Craft.Models;

internal static class SkinFiles
{
    const long MaxBytes = 512 * 1024;
    static readonly TimeSpan Wait = TimeSpan.FromSeconds(3);
    static readonly ConcurrentDictionary<string, (DateTime Modified, long Length, byte[]? Data)> files = new(StringComparer.OrdinalIgnoreCase);

    public static string? IdOf(string path)
    {
        var data = Read(path);
        return data is null ? null : CraftLink.Shared.UploadSkin(data, Wait);
    }

    static byte[]? Read(string path)
    {
        try
        {
            var info = new FileInfo(path);
            if (!info.Exists || info.Length > MaxBytes) return null;
            if (files.TryGetValue(path, out var cached) && cached.Modified == info.LastWriteTimeUtc && cached.Length == info.Length)
                return cached.Data;
            var data = File.ReadAllBytes(path);
            files[path] = (info.LastWriteTimeUtc, info.Length, data);
            return data;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or ArgumentException or NotSupportedException)
        {
            return null;
        }
    }
}
