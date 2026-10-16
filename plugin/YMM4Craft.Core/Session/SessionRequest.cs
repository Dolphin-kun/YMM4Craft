using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using YMM4Craft.Core.Protocol;

namespace YMM4Craft.Core.Session;

public sealed record SessionRequest
{
    [JsonPropertyName("protocol")] public required int Protocol { get; init; }
    [JsonPropertyName("hostPid")] public required int HostPid { get; init; }
    [JsonPropertyName("createdAtUnixMs")] public required long CreatedAtUnixMs { get; init; }
    [JsonPropertyName("token")] public required string Token { get; init; }
    [JsonPropertyName("features")] public required IReadOnlyList<string> Features { get; init; }
    [JsonPropertyName("socketPath")] public required string SocketPath { get; init; }
    [JsonPropertyName("frameFile")] public required string FrameFile { get; init; }
    [JsonPropertyName("frameCapacity")] public required long FrameCapacity { get; init; }

    static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public static SessionRequest Create(int hostPid, IReadOnlyList<string> features, long frameCapacity, DateTimeOffset now) => new()
    {
        Protocol = ProtocolInfo.Version,
        HostPid = hostPid,
        CreatedAtUnixMs = now.ToUnixTimeMilliseconds(),
        Token = Base64Url(RandomNumberGenerator.GetBytes(32)),
        Features = features,
        SocketPath = SessionPaths.SocketFile(hostPid),
        FrameFile = SessionPaths.FrameFile(hostPid),
        FrameCapacity = frameCapacity,
    };

    public string ToJson() => JsonSerializer.Serialize(this, JsonOptions);

    public static SessionRequest? FromJson(string json) => JsonSerializer.Deserialize<SessionRequest>(json);

    public void WriteTo(string path)
    {
        var temp = path + ".tmp";
        File.WriteAllText(temp, ToJson());
        File.Move(temp, path, overwrite: true);
    }

    public bool TokenMatches(string? token) =>
        token is not null &&
        CryptographicOperations.FixedTimeEquals(
            System.Text.Encoding.UTF8.GetBytes(Token),
            System.Text.Encoding.UTF8.GetBytes(token));

    static string Base64Url(byte[] bytes) =>
        Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}
