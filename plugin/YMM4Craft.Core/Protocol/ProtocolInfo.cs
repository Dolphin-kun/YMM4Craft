namespace YMM4Craft.Core.Protocol;

public static class ProtocolInfo
{
    public const int Version = 1;

    public static readonly TimeSpan PingInterval = TimeSpan.FromSeconds(2);

    public static readonly TimeSpan PeerTimeout = TimeSpan.FromSeconds(10);

    public const int MaxLineBytes = 1 << 20;
}
