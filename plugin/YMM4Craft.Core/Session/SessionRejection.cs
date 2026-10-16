using System.Text.Json;
using System.Text.Json.Nodes;

namespace YMM4Craft.Core.Session;

public static class SessionRejection
{
    public const string FileName = "session.rejected.json";

    public static string FilePath => Path.Combine(SessionPaths.BaseDir, FileName);

    public static string? ReadMessage(SessionRequest request)
    {
        try
        {
            return File.Exists(FilePath) ? Message(File.ReadAllText(FilePath), request) : null;
        }
        catch (IOException)
        {
            return null;
        }
        catch (UnauthorizedAccessException)
        {
            return null;
        }
    }

    public static string? Message(string json, SessionRequest request)
    {
        JsonNode? node;
        try
        {
            node = JsonNode.Parse(json);
        }
        catch (JsonException)
        {
            return null;
        }
        if (node is not JsonObject o || !request.TokenMatches((string?)o["token"]) || (string?)o["reason"] != "version") return null;
        var modVersion = (string?)o["modVersion"] ?? "?";
        var modProtocol = (int?)o["modProtocol"] ?? 0;
        var newer = modProtocol > request.Protocol ? "Mod のほうが新しいので、このプラグインを更新してください" : "Mod のほうが古いので、Mod を更新してください";
        return $"Minecraft の YMM4Craft（{modVersion}）とこのプラグインの版が合いません。{newer}";
    }
}
