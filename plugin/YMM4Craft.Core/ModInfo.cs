namespace YMM4Craft.Core;

public sealed record ModInfo(string ModVersion, string GameVersion, IReadOnlyList<string> Commands);
