using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Controls;

internal static class KeyframeValues
{
    public static int Index(Animation animation, (int Frame, int Length) position)
    {
        var count = animation.ActiveValues.Count();
        if (count <= 1) return 0;
        var (frame, length) = position;
        var frames = new List<int> { 0 };
        if (animation.KeyFrames is { } keyFrames) frames.AddRange(keyFrames.Frames);
        frames.Add(Math.Max(length - 1, 0));
        var best = 0;
        for (var i = 1; i < Math.Min(frames.Count, count); i++)
            if (Math.Abs(frames[i] - frame) < Math.Abs(frames[best] - frame)) best = i;
        return best;
    }

    public static double Get(Animation animation, int index)
    {
        var values = animation.Values;
        return values.Count == 0 ? animation.DefaultValue : values[Math.Min(index, values.Count - 1)].Value;
    }

    public static void Set(Animation animation, int index, double value)
    {
        var values = animation.Values;
        if (values.Count == 0) return;
        values[Math.Min(index, values.Count - 1)].Value = Math.Clamp(value, animation.MinValue, animation.MaxValue);
    }
}
