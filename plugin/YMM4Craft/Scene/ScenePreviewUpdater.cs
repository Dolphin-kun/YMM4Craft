using YMM4Craft.Core;
using YukkuriMovieMaker.Player.Video;

namespace YMM4Craft.Scene;

internal static class ScenePreviewUpdater
{
    static readonly TimeSpan AfterChange = TimeSpan.FromSeconds(6);

    static readonly Lock gate = new();
    static long lastRevision = -1;
    static DateTime refreshUntil;
    static bool paused;
    static CraftLinkState lastState = (CraftLinkState)(-1);
    static string lastMessage = "";

    static ScenePreviewUpdater()
    {
        CraftLink.Shared.Changed += OnLinkChanged;
    }

    public static void OnUpdate(TimelineSourceUsage usage, long revision)
    {
        lock (gate)
        {
            paused = usage == TimelineSourceUsage.Paused;
            var changed = revision != lastRevision;
            if (changed)
            {
                lastRevision = revision;
                refreshUntil = DateTime.UtcNow + AfterChange;
            }
            if (changed || !paused) CraftLink.Shared.NoteActivity();
        }
    }

    static void OnLinkChanged()
    {
        var link = CraftLink.Shared;
        bool request;
        lock (gate)
        {
            if (link.State != lastState || link.StatusMessage != lastMessage)
            {
                lastState = link.State;
                lastMessage = link.StatusMessage;
                refreshUntil = DateTime.UtcNow + AfterChange;
                request = true;
            }
            else
            {
                request = paused && DateTime.UtcNow < refreshUntil;
            }
        }
        if (request) PreviewRefresher.Request();
    }
}
