namespace YMM4Craft.Core.Audio;

public static class TimeStretch
{
    const double StoppedSecondsPerFrame = 1e-6;

    public static (float[] Left, float[] Right) Apply(float[] left, float[] right, int sampleRate, double sourceStart, IReadOnlyList<double> timeAtFrame, int fps)
    {
        if (timeAtFrame.Count < 2) return ([], []);
        var frames = timeAtFrame.Count - 1;
        var total = (int)Math.Round((double)frames / fps * sampleRate);
        var outLeft = new float[total];
        var outRight = new float[total];
        for (var n = 0; n < total; n++)
        {
            var frame = (double)n * fps / sampleRate;
            var f = Math.Min((int)frame, frames - 1);
            var k = frame - f;
            var a = timeAtFrame[f];
            var b = timeAtFrame[f + 1];
            if (Math.Abs(b - a) < StoppedSecondsPerFrame) continue;
            var position = (a + (b - a) * k - sourceStart) * sampleRate;
            if (position < 0 || position >= left.Length - 1) continue;
            var i = (int)position;
            var t = (float)(position - i);
            outLeft[n] = left[i] + (left[i + 1] - left[i]) * t;
            outRight[n] = right[i] + (right[i + 1] - right[i]) * t;
        }
        return (outLeft, outRight);
    }
}
