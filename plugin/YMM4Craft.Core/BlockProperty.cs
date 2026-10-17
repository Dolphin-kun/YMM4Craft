namespace YMM4Craft.Core;

public sealed record BlockProperty(string Name, IReadOnlyList<string> Values, string Default);
