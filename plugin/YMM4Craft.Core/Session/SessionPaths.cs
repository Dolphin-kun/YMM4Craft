namespace YMM4Craft.Core.Session;

public static class SessionPaths
{
    public const string RequestFileName = "session.json";
    public const string AcceptedFileName = "session.accepted.json";

    public static string BaseDir { get; } =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "YMM4Craft");

    public static string RequestFile => Path.Combine(BaseDir, RequestFileName);
    public static string AcceptedFile => Path.Combine(BaseDir, AcceptedFileName);

    public static string SocketFile(int hostPid) => Path.Combine(BaseDir, $"h{hostPid}.sock");

    public static string FrameFile(int hostPid) => Path.Combine(BaseDir, $"f{hostPid}.bin");

    public static int? TryParseOwnerPid(string fileName)
    {
        var (prefix, extension) = fileName.Length > 0 ? (fileName[0], Path.GetExtension(fileName)) : ('\0', "");
        if (!((prefix == 'h' && extension == ".sock") || (prefix == 'f' && extension == ".bin"))) return null;
        var digits = Path.GetFileNameWithoutExtension(fileName)[1..];
        return digits.Length > 0 && digits.All(char.IsAsciiDigit) && int.TryParse(digits, out var pid) ? pid : null;
    }
}
