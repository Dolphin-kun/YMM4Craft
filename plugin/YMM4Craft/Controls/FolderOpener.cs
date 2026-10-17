using System.Diagnostics;
using System.IO;

namespace YMM4Craft.Controls;

internal static class FolderOpener
{
    public static bool Show(string? path)
    {
        if (string.IsNullOrEmpty(path)) return false;
        if (File.Exists(path) || Directory.Exists(path))
        {
            Process.Start(new ProcessStartInfo("explorer.exe", $"/select,\"{path}\"") { UseShellExecute = true });
            return true;
        }
        return Open(Path.GetDirectoryName(path));
    }

    public static bool Open(string? folder)
    {
        if (string.IsNullOrEmpty(folder) || !Directory.Exists(folder)) return false;
        Process.Start(new ProcessStartInfo("explorer.exe", $"\"{folder}\"") { UseShellExecute = true });
        return true;
    }
}
