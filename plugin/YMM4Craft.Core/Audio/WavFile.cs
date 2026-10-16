namespace YMM4Craft.Core.Audio;

public static class WavFile
{
    public static (float[] Left, float[] Right, int SampleRate) Read(string path)
    {
        using var r = new BinaryReader(File.OpenRead(path));
        if (new string(r.ReadChars(4)) != "RIFF") throw new InvalidDataException("WAV ではありません");
        r.ReadInt32();
        if (new string(r.ReadChars(4)) != "WAVE") throw new InvalidDataException("WAV ではありません");
        int channels = 0, rate = 0, bits = 0;
        while (r.BaseStream.Position + 8 <= r.BaseStream.Length)
        {
            var id = new string(r.ReadChars(4));
            var size = r.ReadInt32();
            if (id == "fmt ")
            {
                var format = r.ReadInt16();
                channels = r.ReadInt16();
                rate = r.ReadInt32();
                r.ReadInt32();
                r.ReadInt16();
                bits = r.ReadInt16();
                if (format != 1 || bits != 16 || channels is < 1 or > 2) throw new InvalidDataException("16bit PCM のモノラルかステレオだけ読めます");
                r.BaseStream.Seek(size - 16, SeekOrigin.Current);
            }
            else if (id == "data")
            {
                if (channels == 0) throw new InvalidDataException("WAV の形式が分かりません");
                var frames = size / (2 * channels);
                var left = new float[frames];
                var right = channels == 1 ? left : new float[frames];
                for (var i = 0; i < frames; i++)
                {
                    left[i] = r.ReadInt16() / 32768f;
                    if (channels == 2) right[i] = r.ReadInt16() / 32768f;
                }
                return (left, right, rate);
            }
            else
            {
                r.BaseStream.Seek(size + (size & 1), SeekOrigin.Current);
            }
        }
        throw new InvalidDataException("WAV に音のデータがありません");
    }

    public static void Write(string path, float[] left, float[] right, int sampleRate)
    {
        using var w = new BinaryWriter(File.Create(path));
        var dataSize = left.Length * 4;
        w.Write("RIFF"u8);
        w.Write(36 + dataSize);
        w.Write("WAVEfmt "u8);
        w.Write(16);
        w.Write((short)1);
        w.Write((short)2);
        w.Write(sampleRate);
        w.Write(sampleRate * 4);
        w.Write((short)4);
        w.Write((short)16);
        w.Write("data"u8);
        w.Write(dataSize);
        for (var i = 0; i < left.Length; i++)
        {
            w.Write(ToShort(left[i]));
            w.Write(ToShort(right[i]));
        }
    }

    static short ToShort(float v) => (short)Math.Round(Math.Clamp(v, -1f, 1f) * 32767f);
}
