namespace YMM4Craft.Core.Protocol;

public sealed record GpuShareOffer(int Generation, uint LuidLow, int LuidHigh, int Width, int Height, IReadOnlyList<long> Handles);
