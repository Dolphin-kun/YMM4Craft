using YMM4Craft.Core;

namespace YMM4Craft.Models;

internal static partial class CatalogNames
{
    public static IReadOnlyList<CatalogEntry> Entries(CatalogKind kind)
    {
        var catalog = CraftLink.Shared.CachedCatalog;
        if (catalog is null) return [];
        return kind switch { CatalogKind.Item => catalog.Items, CatalogKind.Block => catalog.Blocks, _ => catalog.Mobs };
    }

    public static CatalogKind KindOf(SceneModelKind kind) => kind switch
    {
        SceneModelKind.Item => CatalogKind.Item,
        SceneModelKind.Block => CatalogKind.Block,
        _ => CatalogKind.Mob,
    };

    public static string NameOf(CatalogKind kind, string value)
    {
        var id = ToId(value);
        return Entries(kind).FirstOrDefault(e => e.Id == id)?.Name ?? value;
    }

    public static string? ToId(string? value)
    {
        value = value?.Trim();
        if (string.IsNullOrEmpty(value)) return null;
        var catalog = CraftLink.Shared.CachedCatalog;
        if (catalog is not null)
        {
            var byName = catalog.Mobs.Concat(catalog.Items).Concat(catalog.Blocks).FirstOrDefault(e => e.Name == value || e.Id == value);
            if (byName is not null) return byName.Id;
        }
        var lower = value.ToLowerInvariant();
        if (!lower.Contains(':')) lower = "minecraft:" + lower;
        return RegistryIdPattern().IsMatch(lower) ? lower : null;
    }

    [System.Text.RegularExpressions.GeneratedRegex("^[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}$")]
    private static partial System.Text.RegularExpressions.Regex RegistryIdPattern();
}
