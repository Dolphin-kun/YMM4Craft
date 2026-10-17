using YMM4Craft.Core;
using YukkuriMovieMaker.Player.Video;

namespace YMM4Craft.Scene;

internal static class ScenePreviewUpdater
{
    static readonly TimeSpan AfterChange = TimeSpan.FromSeconds(6);
    static readonly TimeSpan AfterBusy = TimeSpan.FromSeconds(1);
    static readonly TimeSpan BusyLimit = TimeSpan.FromMinutes(3);

    static readonly Lock gate = new();
    static long lastRevision = -1;
    static DateTime refreshUntil;
    static DateTime changedAt;
    static bool paused;
    static CraftLinkState lastState = (CraftLinkState)(-1);
    static string lastMessage = "";
    static bool lastLoading;

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
                changedAt = DateTime.UtcNow;
                refreshUntil = changedAt + AfterChange;
            }
            if (changed || !paused) CraftLink.Shared.NoteActivity();
        }
    }

    static void OnLinkChanged()
    {
        var link = CraftLink.Shared;
        var loading = link.IsLoading;
        var settled = link.LatestNotice?.Settled ?? true;
        bool request;
        lock (gate)
        {
            var now = DateTime.UtcNow;
            if (link.State != lastState || link.StatusMessage != lastMessage || loading != lastLoading)
            {
                lastState = link.State;
                lastMessage = link.StatusMessage;
                lastLoading = loading;
                changedAt = now;
                refreshUntil = now + AfterChange;
                request = true;
            }
            else
            {
                if ((loading || !settled) && now - changedAt < BusyLimit && refreshUntil < now + AfterBusy) refreshUntil = now + AfterBusy;
                request = paused && now < refreshUntil;
            }
        }
        if (request) PreviewRefresher.Request();
    }
}
