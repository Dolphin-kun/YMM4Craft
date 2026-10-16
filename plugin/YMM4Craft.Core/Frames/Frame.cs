namespace YMM4Craft.Core.Frames;

public sealed record Frame(long Sequence, int Width, int Height, int Stride, FrameFormat Format, long Revision, byte[] Pixels)
{
    public bool Settled { get; init; }

    public byte[] ToBgraTopDown(bool keepAlpha = false)
    {
        var result = new byte[Width * Height * 4];
        var (r, b) = Format == FrameFormat.Rgba8BottomUp ? (0, 2) : (2, 0);
        for (var y = 0; y < Height; y++)
        {
            var src = (Height - 1 - y) * Stride;
            var dst = y * Width * 4;
            for (var x = 0; x < Width; x++)
            {
                result[dst + x * 4 + 0] = Pixels[src + x * 4 + b];
                result[dst + x * 4 + 1] = Pixels[src + x * 4 + 1];
                result[dst + x * 4 + 2] = Pixels[src + x * 4 + r];
                result[dst + x * 4 + 3] = keepAlpha ? Pixels[src + x * 4 + 3] : (byte)255;
            }
        }
        return result;
    }
}
