using System.Windows;
using YukkuriMovieMaker.Json;

namespace YMM4Craft.Models;

internal static class ModelClipboard
{
    const string Format = "YMM4Craft.Models/1";

    public static void Copy(IReadOnlyList<SceneModel> models)
    {
        if (models.Count == 0) return;
        var text = Json.GetJsonText(new ModelClipboardData { Format = Format, Models = [.. models] });
        try
        {
            var data = new DataObject();
            data.SetData(Format, text);
            data.SetText(text);
            Clipboard.SetDataObject(data, true);
        }
        catch (System.Runtime.InteropServices.COMException)
        {
        }
    }

    public static bool HasModels()
    {
        try
        {
            return Clipboard.ContainsData(Format) || (Clipboard.ContainsText() && Clipboard.GetText().Contains(Format, StringComparison.Ordinal));
        }
        catch (System.Runtime.InteropServices.COMException)
        {
            return false;
        }
    }

    public static List<SceneModel> Paste()
    {
        string? text;
        try
        {
            text = Clipboard.ContainsData(Format) ? Clipboard.GetData(Format) as string : Clipboard.ContainsText() ? Clipboard.GetText() : null;
        }
        catch (System.Runtime.InteropServices.COMException)
        {
            return [];
        }
        if (text is null || !text.Contains(Format, StringComparison.Ordinal)) return [];
        ModelClipboardData? data;
        try
        {
            data = Json.LoadFromText<ModelClipboardData>(text);
        }
        catch (Newtonsoft.Json.JsonException)
        {
            return [];
        }
        if (data is null || data.Format != Format) return [];
        var models = data.Models.Where(m => m is not null).ToList();
        var renamed = models.ToDictionary(m => m.Key, _ => Guid.NewGuid().ToString("N"));
        foreach (var m in models)
        {
            m.Key = renamed[m.Key];
            if (renamed.TryGetValue(m.ParentKey, out var parent)) m.ParentKey = parent;
        }
        return models;
    }
}
