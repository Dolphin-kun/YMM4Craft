using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Data;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using YukkuriMovieMaker.Settings;

namespace YMM4Craft.Controls;

internal sealed class ListPanel : DockPanel
{
    public ListBox List { get; }

    readonly UniformGrid buttons = new() { Columns = 1 };
    readonly List<Button> bottomButtons = [];

    const double RowHeight = 25;
    const double ButtonColumnWidth = 30;

    public ListPanel()
    {
        var sizes = EffectEditorSettings.Default.VideoEditorSize;
        SetBinding(HeightProperty, new Binding(nameof(sizes.ListHeight)) { Source = sizes, Mode = BindingMode.OneWay });

        var grip = new Thumb { Height = 4, Cursor = Cursors.SizeNS, Opacity = 0 };
        grip.DragDelta += (_, e) => sizes.ListHeight += e.VerticalChange;
        SetDock(grip, Dock.Bottom);
        Children.Add(grip);

        List = new ListBox { HorizontalContentAlignment = HorizontalAlignment.Stretch };
        List.Loaded += (_, _) =>
        {
            var style = new Style(typeof(ListBoxItem), List.TryFindResource(typeof(ListBoxItem)) as Style);
            style.Setters.Add(new Setter(HeightProperty, RowHeight));
            style.Setters.Add(new Setter(Control.VerticalContentAlignmentProperty, VerticalAlignment.Center));
            List.ItemContainerStyle = style;
        };
        List.PreviewMouseWheel += OnPreviewMouseWheel;
        buttons.Width = ButtonColumnWidth;
        SetDock(buttons, Dock.Right);
        Children.Add(buttons);
        Children.Add(List);
    }

    public Button AddButton(object geometryKey, string tip, Action onClick)
    {
        var button = ColumnButton(geometryKey, tip);
        button.Click += (_, e) =>
        {
            e.Handled = true;
            onClick();
        };
        Insert(button, false);
        return button;
    }

    public Button AddMenuButton(object geometryKey, string tip, Action<ContextMenu> fill, bool bottom = false)
    {
        var button = ColumnButton(geometryKey, tip);
        var menu = new ContextMenu();
        button.Click += (_, e) =>
        {
            e.Handled = true;
            Dispatcher.BeginInvoke(() =>
            {
                menu.Items.Clear();
                fill(menu);
                menu.PlacementTarget = button;
                menu.Placement = PlacementMode.Bottom;
                menu.IsOpen = true;
            }, DispatcherPriority.Input);
        };
        Insert(button, bottom);
        return button;
    }

    void Insert(Button button, bool bottom)
    {
        if (bottom)
        {
            bottomButtons.Add(button);
            buttons.Children.Add(button);
            return;
        }
        buttons.Children.Insert(buttons.Children.Count - bottomButtons.Count, button);
    }

    public static MenuItem MenuItem(string header, Action onClick, bool enabled = true)
    {
        var item = new MenuItem { Header = header, IsEnabled = enabled };
        item.Click += (_, _) => onClick();
        return item;
    }

    static Button ColumnButton(object geometryKey, string tip)
    {
        var button = IconButtons.Create(geometryKey, tip);
        button.Width = double.NaN;
        button.Height = double.NaN;
        button.Margin = new Thickness(2, 1, 0, 1);
        return button;
    }

    void OnPreviewMouseWheel(object sender, MouseWheelEventArgs e)
    {
        var viewer = FindScrollViewer(List);
        var canScroll = viewer is not null && (e.Delta > 0 ? viewer.VerticalOffset > 0 : viewer.VerticalOffset < viewer.ScrollableHeight);
        if (canScroll) return;
        e.Handled = true;
        RaiseEvent(new MouseWheelEventArgs(e.MouseDevice, e.Timestamp, e.Delta) { RoutedEvent = MouseWheelEvent, Source = this });
    }

    static ScrollViewer? FindScrollViewer(DependencyObject parent)
    {
        for (var i = 0; i < VisualTreeHelper.GetChildrenCount(parent); i++)
        {
            var child = VisualTreeHelper.GetChild(parent, i);
            if (child is ScrollViewer viewer) return viewer;
            if (FindScrollViewer(child) is { } found) return found;
        }
        return null;
    }
}
