using System.Text.Json.Nodes;

namespace YMM4Craft.Core.Frames;

public sealed record FrameNotice(long Sequence, long Offset, int Width, int Height, long Revision)
{
    public int GpuSlot { get; init; } = -1;

    public int GpuGeneration { get; init; } = -1;

    public bool Settled { get; init; }

    public double GpuWaitMs { get; init; }

    public double WriteMs { get; init; }

    public double MapMs { get; init; }

    public static FrameNotice? FromJson(JsonNode? node)
    {
        if (node is null) return null;
        var seq = (long?)node["sequence"];
        var offset = (long?)node["offset"] ?? -1;
        var gpuSlot = (int?)node["gpuSlot"] ?? -1;
        if (seq is null || (offset < 0 && gpuSlot < 0)) return null;
        return new FrameNotice(seq.Value, offset, (int?)node["width"] ?? 0, (int?)node["height"] ?? 0, (long?)node["revision"] ?? 0)
        {
            Settled = (bool?)node["settled"] ?? false,
            GpuSlot = gpuSlot,
            GpuGeneration = (int?)node["gpuGeneration"] ?? -1,
            GpuWaitMs = (double?)node["gpuWaitMs"] ?? 0,
            WriteMs = (double?)node["writeMs"] ?? 0,
            MapMs = (double?)node["mapMs"] ?? 0,
        };
    }
}
