using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Media;

namespace YMM4Craft.Controls;

internal static class IconButtons
{
    public const double Size = 24;
    public const double IconSize = 14;

    public static Button Create(object geometryKey, string tip, double size = Size)
    {
        var button = new Button { Content = Icon(geometryKey), Width = size, Height = size, Padding = new Thickness(0), ToolTip = tip };
        var icon = (FrameworkElement)button.Content;
        button.IsEnabledChanged += (_, _) => icon.Opacity = button.IsEnabled ? 1 : 0.35;
        return button;
    }

    public static System.Windows.Shapes.Path Icon(object geometryKey)
    {
        var icon = new System.Windows.Shapes.Path { Stretch = Stretch.Uniform, Width = IconSize, Height = IconSize };
        icon.SetResourceReference(System.Windows.Shapes.Path.DataProperty, geometryKey);
        icon.SetBinding(System.Windows.Shapes.Shape.FillProperty, new Binding(nameof(Control.Foreground))
        {
            RelativeSource = new RelativeSource(RelativeSourceMode.FindAncestor) { AncestorType = typeof(Control) },
        });
        return icon;
    }
}
