using System.Diagnostics;
using System.Reflection;
using System.Windows;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Scene;

internal static class PreviewRefresher
{
    static readonly TimeSpan MinInterval = TimeSpan.FromMilliseconds(100);

    static readonly Stopwatch sinceLast = Stopwatch.StartNew();
    static int pending;
    static bool disabled;

    public static void Request()
    {
        if (disabled || sinceLast.Elapsed < MinInterval) return;
        if (Interlocked.Exchange(ref pending, 1) == 1) return;
        sinceLast.Restart();

        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null)
        {
            pending = 0;
            return;
        }
        dispatcher.BeginInvoke(() =>
        {
            try
            {
                MarkChanged();
            }
            catch (Exception ex)
            {
                disabled = true;
                Log.Default.Write("YMM4Craft: プレビューの描き直しに失敗したため、以後は行いません", ex);
            }
            finally
            {
                pending = 0;
            }
        });
    }

    const BindingFlags Instance = BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic;

    static FieldInfo? areaField;
    static PropertyInfo? viewModelProperty;
    static FieldInfo? playerField;
    static FieldInfo? flagField;

    static void MarkChanged()
    {
        var main = Application.Current.MainWindow?.DataContext;
        if (main is null) return;

        areaField ??= main.GetType().GetField("previewAreaViewModel", Instance);
        var area = areaField?.GetValue(main);
        if (area is null) return;
        viewModelProperty ??= area.GetType().GetProperty("ViewModel", Instance);
        var preview = viewModelProperty?.GetValue(area);
        if (preview is null) return;
        playerField ??= preview.GetType().GetField("player", Instance);
        var player = playerField?.GetValue(preview);
        if (player is null) return;

        flagField ??= FindField(player.GetType(), "isTimelineChanged")
                      ?? throw new MissingFieldException(player.GetType().FullName, "isTimelineChanged");
        flagField.SetValue(player, true);
    }

    static FieldInfo? FindField(Type? type, string name)
    {
        for (; type is not null; type = type.BaseType)
        {
            var field = type.GetField(name, Instance | BindingFlags.DeclaredOnly);
            if (field is not null && field.FieldType == typeof(bool)) return field;
        }
        return null;
    }
}
