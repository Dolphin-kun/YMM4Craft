using YMM4Craft.Scene;
using System.IO;
using System.Collections.Immutable;
using System.ComponentModel;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Data;
using System.Windows.Input;
using YukkuriMovieMaker.Resources.Icons;
using YMM4Craft.Controls;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Controls;

namespace YMM4Craft.Models;

internal sealed class ModelListEditor : UserControl, IPropertyEditorControl, IPropertyEditorControl2
{
    public event EventHandler? BeginEdit;
    public event EventHandler? EndEdit;

    readonly ListPanel panelList;
    readonly ListBox list;
    readonly Button removeButton;
    readonly Button duplicateButton;
    readonly Button copyButton;
    readonly Button upButton;
    readonly Button downButton;
    readonly PropertiesEditor details;

    object? item;
    object? target;
    PropertyInfo? property;
    string? selectedKey;
    SceneModel? shown;

    public ModelListEditor()
    {
        panelList = new ListPanel();
        list = panelList.List;
        list.SelectionMode = SelectionMode.Extended;
        list.PreviewKeyDown += (_, e) =>
        {
            if (Keyboard.Modifiers != ModifierKeys.Control) return;
            if (e.Key == Key.C) CopySelected();
            else if (e.Key == Key.V) PasteModels();
            else return;
            e.Handled = true;
        };
        list.ItemTemplate = RowTemplate();
        list.AddHandler(ButtonBase.ClickEvent, new RoutedEventHandler(OnVisibleClicked));
        list.SelectionChanged += (_, _) =>
        {
            if (list.SelectedItem is SceneModel m) selectedKey = m.Key;
            ShowDetails();
        };

        panelList.AddMenuButton(GeometryKeys.Plus, "追加", menu =>
        {
            foreach (var kind in new[] { SceneModelKind.Player, SceneModelKind.Mob, SceneModelKind.Item })
                menu.Items.Add(ListPanel.MenuItem(KindName(kind), () => _ = AddAsync(kind)));
        });
        removeButton = panelList.AddButton(GeometryKeys.Minus, "削除", RemoveSelected);
        duplicateButton = panelList.AddButton(GeometryKeys.ContentDuplicate, "複製", DuplicateSelected);
        copyButton = panelList.AddButton(GeometryKeys.ContentCopy, "コピー", CopySelected);
        panelList.AddButton(GeometryKeys.ContentPaste, "貼り付け", PasteModels);
        upButton = panelList.AddButton(GeometryKeys.MenuUp, "上へ", () => MoveSelected(-1));
        downButton = panelList.AddButton(GeometryKeys.MenuDown, "下へ", () => MoveSelected(1));
        panelList.AddMenuButton(GeometryKeys.DotsVertical, "そのほかの操作", menu =>
        {
            menu.Items.Add(ListPanel.MenuItem("地面に置く", () => _ = PutOnGroundAsync(), list.SelectedItem is SceneModel));
            menu.Items.Add(ListPanel.MenuItem("すべて表示", () => SetAllVisible(true)));
            menu.Items.Add(ListPanel.MenuItem("すべて隠す", () => SetAllVisible(false)));
        }, bottom: true);

        details = new PropertiesEditor { IsScrollViewerEnabled = false };
        details.BeginEdit += (_, e) => BeginEdit?.Invoke(this, e);
        details.EndEdit += (_, e) => EndEdit?.Invoke(this, e);

        var panel = new StackPanel();
        panel.Children.Add(panelList);
        panel.Children.Add(details);
        Content = panel;
        UpdateButtons();
    }

    static DataTemplate RowTemplate()
    {
        var row = new FrameworkElementFactory(typeof(DockPanel));
        var check = new FrameworkElementFactory(typeof(CheckBox));
        check.SetValue(VerticalAlignmentProperty, VerticalAlignment.Center);
        check.SetValue(MarginProperty, new Thickness(0, 0, 2, 0));
        check.SetValue(ToolTipProperty, "表示する");
        check.SetBinding(ToggleButton.IsCheckedProperty, new Binding(nameof(SceneModel.Visible)) { Mode = BindingMode.OneWay });
        row.AppendChild(check);
        var label = new FrameworkElementFactory(typeof(TextBlock));
        label.SetValue(VerticalAlignmentProperty, VerticalAlignment.Center);
        label.SetValue(TextBlock.TextTrimmingProperty, TextTrimming.CharacterEllipsis);
        label.SetBinding(TextBlock.TextProperty, new Binding(nameof(SceneModel.Label)));
        row.AppendChild(label);
        return new DataTemplate { VisualTree = row };
    }

    static string KindName(SceneModelKind kind) => kind switch
    {
        SceneModelKind.Item => "アイテム",
        SceneModelKind.Player => "プレイヤー",
        _ => "Mob",
    };

    public void SetEditorInfo(IEditorInfo? info)
    {
        details.SetEditorInfo(info);
    }

    public void Attach(object editedItem, object owner, PropertyInfo info)
    {
        Detach();
        item = editedItem;
        target = owner;
        property = info;
        if (owner is INotifyPropertyChanged watched) watched.PropertyChanged += OnOwnerPropertyChanged;
        Refresh();
        _ = LoadNamesAsync();
    }

    public void Detach()
    {
        if (target is INotifyPropertyChanged watched) watched.PropertyChanged -= OnOwnerPropertyChanged;
        item = null;
        target = null;
        property = null;
        list.ItemsSource = null;
        ClearDetails();
    }

    async Task LoadNamesAsync()
    {
        if (Core.CraftLink.Shared.CachedCatalog is not null || !Core.CraftLink.Shared.IsConnected) return;
        try
        {
            await Core.CraftLink.Shared.GetCatalogAsync();
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
            return;
        }
        foreach (var m in Models) m.RefreshLabel();
    }

    void OnOwnerPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == property?.Name) Refresh();
    }

    ImmutableList<SceneModel> Models =>
        target is not null && property?.GetValue(target) is ImmutableList<SceneModel> models ? models : [];

    void SetModels(ImmutableList<SceneModel> models, string? select)
    {
        if (target is null || property is null) return;
        selectedKey = select;
        BeginEdit?.Invoke(this, EventArgs.Empty);
        property.SetValue(target, models);
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    void Refresh()
    {
        var models = Models;
        foreach (var m in models) m.Siblings = models;
        list.ItemsSource = models;
        var selected = models.FirstOrDefault(m => m.Key == selectedKey) ?? models.FirstOrDefault();
        list.SelectedItem = selected;
        ShowDetails();
    }

    void UpdateButtons()
    {
        var index = list.SelectedIndex;
        removeButton.IsEnabled = index >= 0;
        duplicateButton.IsEnabled = index >= 0;
        copyButton.IsEnabled = index >= 0;
        upButton.IsEnabled = index > 0;
        downButton.IsEnabled = index >= 0 && index < list.Items.Count - 1;
    }

    const double GroundSnapDistance = 6;

    async Task AddAsync(SceneModelKind kind)
    {
        var model = new SceneModel(kind, kind switch
        {
            SceneModelKind.Item => "minecraft:diamond",
            SceneModelKind.Mob => "minecraft:zombie",
            _ => "",
        });
        PlaceInFrontOfCamera(model, await ViewAsync() ?? SceneCameraView());
        var feet = KeyframeValues.Get(model.Y, 0);
        if (await GroundAsync(KeyframeValues.Get(model.X, 0), KeyframeValues.Get(model.Z, 0)) is { } ground && Math.Abs(ground - feet) <= GroundSnapDistance)
            model.Y.Values[0].Value = ground;
        SetModels(Models.Add(model), model.Key);
    }

    async Task PutOnGroundAsync()
    {
        if (list.SelectedItem is not SceneModel model) return;
        var index = KeyframeValues.Index(model.Y, model.PreviewPosition);
        if (await GroundAsync(KeyframeValues.Get(model.X, index), KeyframeValues.Get(model.Z, index)) is not { } ground) return;
        BeginEdit?.Invoke(this, EventArgs.Empty);
        KeyframeValues.Set(model.Y, index, ground);
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    static async Task<double?> GroundAsync(double x, double z)
    {
        if (!Core.CraftLink.Shared.IsConnected) return null;
        try
        {
            return await Core.CraftLink.Shared.GetGroundAsync(x, z);
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
            return null;
        }
    }

    static async Task<Core.CameraView?> ViewAsync()
    {
        if (!Core.CraftLink.Shared.IsConnected) return null;
        try
        {
            return await Core.CraftLink.Shared.GetViewAsync();
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
            return null;
        }
    }

    Core.CameraView? SceneCameraView()
    {
        if (target is not SceneShapeParameter scene) return null;
        var (frame, length) = scene.PreviewPosition;
        double At(Animation a) => a.Values.Count == 1 ? a.Values[0].Value : a.GetValue(frame, Math.Max(length, 1), 60);
        return new Core.CameraView(At(scene.CameraX), At(scene.CameraY), At(scene.CameraZ), At(scene.Yaw), At(scene.Pitch));
    }

    static void PlaceInFrontOfCamera(SceneModel model, Core.CameraView? view)
    {
        if (view is null) return;
        var yaw = view.Yaw;
        var rad = yaw * Math.PI / 180;
        var x = view.X - Math.Sin(rad) * 3;
        var z = view.Z + Math.Cos(rad) * 3;
        var y = view.Y - 1.62;
        model.X.Values[0].Value = Math.Round(x, 2);
        model.Y.Values[0].Value = Math.Round(y, 2);
        model.Z.Values[0].Value = Math.Round(z, 2);
        model.Yaw.Values[0].Value = Math.Round(Angles.Wrap(yaw + 180), 1);
    }

    List<SceneModel> SelectedModels()
    {
        var models = Models;
        return [.. list.SelectedItems.OfType<SceneModel>().OrderBy(models.IndexOf)];
    }

    void RemoveSelected()
    {
        var selected = SelectedModels();
        if (selected.Count == 0) return;
        var models = Models;
        var index = models.IndexOf(selected[0]);
        var remaining = models.RemoveRange(selected);
        var next = remaining.Count > 0 ? remaining[Math.Min(index, remaining.Count - 1)].Key : null;
        SetModels(remaining, next);
    }

    void CopySelected() => ModelClipboard.Copy(SelectedModels());

    void PasteModels()
    {
        var pasted = ModelClipboard.Paste();
        if (pasted.Count == 0) return;
        var models = Models;
        var at = SelectedModels() is { Count: > 0 } selected ? models.IndexOf(selected[^1]) + 1 : models.Count;
        SetModels(models.InsertRange(at, pasted), pasted[0].Key);
    }

    void MoveSelected(int delta)
    {
        if (list.SelectedItem is not SceneModel model) return;
        var models = Models;
        var index = models.IndexOf(model);
        var to = index + delta;
        if (index < 0 || to < 0 || to >= models.Count) return;
        SetModels(models.RemoveAt(index).Insert(to, model), model.Key);
    }

    void DuplicateSelected()
    {
        var selected = SelectedModels();
        if (selected.Count == 0) return;
        var models = Models;
        var copies = selected.Select(m => m.Clone()).ToList();
        SetModels(models.InsertRange(models.IndexOf(selected[^1]) + 1, copies), copies[0].Key);
    }

    void SetAllVisible(bool visible)
    {
        BeginEdit?.Invoke(this, EventArgs.Empty);
        foreach (var m in Models) m.Visible = visible;
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    void OnVisibleClicked(object sender, RoutedEventArgs e)
    {
        if (e.OriginalSource is not CheckBox { DataContext: SceneModel model } check) return;
        BeginEdit?.Invoke(this, EventArgs.Empty);
        model.Visible = check.IsChecked == true;
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    void ShowDetails()
    {
        UpdateButtons();
        var model = list.SelectedItem as SceneModel;
        if (ReferenceEquals(model, shown) && details.Target == model) return;
        shown = model;
        details.Target = model;
    }

    void ClearDetails()
    {
        details.Target = null;
        shown = null;
    }
}
