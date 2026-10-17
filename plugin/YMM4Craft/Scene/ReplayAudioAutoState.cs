namespace YMM4Craft.Scene;

internal sealed class ReplayAudioAutoState
{
    public string? Requested { get; set; }

    public long QuietUntil { get; set; }

    public bool Waiting { get; set; }
}
