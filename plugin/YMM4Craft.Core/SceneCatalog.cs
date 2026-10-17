namespace YMM4Craft.Core;

public sealed record SceneCatalog(IReadOnlyList<CatalogEntry> Mobs, IReadOnlyList<CatalogEntry> Items, IReadOnlyList<CatalogEntry> Blocks);
