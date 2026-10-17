using YMM4Craft.PoseEditor;
using System.Collections.Immutable;
using System.ComponentModel;
using System.IO;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using YMM4Craft.Controls;
using YMM4Craft.Core;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Controls;
using YukkuriMovieMaker.Resources.Icons;

namespace YMM4Craft.Models;

internal sealed class PartPoseListEditor : UserControl, IPropertyEditorControl, IPropertyEditorControl2, IPoseEditHost
{
    public event EventHandler? BeginEdit;
    public event EventHandler? EndEdit;
    public event Action<string?>? SelectedPartChanged;

    readonly ListBox list;
    readonly Button removeButton;
    readonly PropertiesEditor details;

    object? item;
    SceneModel? model;
    PropertyInfo? property;
    string? selectedPart;
    ScenePartPose? shown;
    PoseEditorWindow? window;
    IReadOnlyList<(string Name, int Depth)>? parts;
    string? partsFor;

    public PartPoseListEditor()
    {
        var openButton = new Button
        {
            Content = "ポーズエディタを開く",
            ToolTip = "モデルを大きく映し、部位をクリックして選び、ギズモやドラッグで回す",
        };
        openButton.Click += (_, e) =>
        {
            e.Handled = true;
            OpenWindow();
        };

        var panelList = new ListPanel();
        list = panelList.List;
        list.DisplayMemberPath = nameof(ScenePartPose.Label);
        list.SelectionChanged += (_, _) =>
        {
            if (list.SelectedItem is ScenePartPose p) SetSelected(p.Name, fromList: true);
            ShowDetails();
        };
        panelList.AddMenuButton(GeometryKeys.Plus, "追加", FillAddMenu);
        removeButton = panelList.AddButton(GeometryKeys.Minus, "削除", RemoveSelected);
        panelList.AddMenuButton(GeometryKeys.DotsVertical, "そのほかの操作", menu =>
        {
            menu.Items.Add(ListPanel.MenuItem("この部位を元に戻す", ResetSelected, list.SelectedItem is ScenePartPose));
            menu.Items.Add(ListPanel.MenuItem("すべての部位を元に戻す", ResetAll, Poses.Count > 0));
        }, bottom: true);

        details = new PropertiesEditor { IsScrollViewerEnabled = false };
        details.BeginEdit += (_, e) => BeginEdit?.Invoke(this, e);
        details.EndEdit += (_, e) => EndEdit?.Invoke(this, e);

        var panel = new StackPanel();
        panel.Children.Add(openButton);
        panel.Children.Add(panelList);
        panel.Children.Add(details);
        Content = panel;
    }

    public void SetEditorInfo(IEditorInfo? info) => details.SetEditorInfo(info);

    public void Attach(object editedItem, object owner, PropertyInfo info)
    {
        Detach();
        item = editedItem;
        model = owner as SceneModel;
        property = info;
        model?.PropertyChanged += OnModelPropertyChanged;
        Refresh();
        _ = LoadPartsAsync();
    }

    public void Detach()
    {
        window?.Close();
        window = null;
        model?.PropertyChanged -= OnModelPropertyChanged;
        item = null;
        model = null;
        property = null;
        list.ItemsSource = null;
        ClearDetails();
    }

    void OnModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == property?.Name) Refresh();
        if (e.PropertyName is nameof(SceneModel.Type) or nameof(SceneModel.Baby))
        {
            parts = null;
            _ = LoadPartsAsync();
        }
    }

    ImmutableList<ScenePartPose> Poses => model is not null && property?.GetValue(model) is ImmutableList<ScenePartPose> poses ? poses : [];

    void SetPoses(ImmutableList<ScenePartPose> poses)
    {
        if (model is null || property is null) return;
        BeginEdit?.Invoke(this, EventArgs.Empty);
        property.SetValue(model, poses);
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    void Refresh()
    {
        var poses = Poses;
        list.ItemsSource = poses;
        list.SelectedItem = poses.FirstOrDefault(p => p.Name == selectedPart) ?? poses.FirstOrDefault();
        ShowDetails();
    }

    void SetSelected(string? name, bool fromList)
    {
        if (selectedPart == name) return;
        selectedPart = name;
        if (!fromList) list.SelectedItem = Poses.FirstOrDefault(p => p.Name == name);
        SelectedPartChanged?.Invoke(name);
    }

    void ShowDetails()
    {
        removeButton.IsEnabled = list.SelectedItem is ScenePartPose;
        var pose = list.SelectedItem as ScenePartPose;
        if (ReferenceEquals(pose, shown) && details.Target == pose) return;
        shown = pose;
        details.Target = pose;
    }

    void ClearDetails()
    {
        details.Target = null;
        shown = null;
    }

    async Task LoadPartsAsync()
    {
        if (model is null || !CraftLink.Shared.IsConnected) return;
        var kind = model.Kind == SceneModelKind.Player ? "player" : "mob";
        var type = CatalogNames.ToId(model.Type) ?? "";
        var key = $"{kind}|{type}|{model.Baby}";
        if (parts is not null && partsFor == key) return;
        try
        {
            parts = await CraftLink.Shared.GetModelPartsAsync(kind, type, model.Baby);
            partsFor = key;
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
            parts = null;
        }
    }

    void FillAddMenu(ContextMenu menu)
    {
        if (parts is null)
        {
            menu.Items.Add(ListPanel.MenuItem("部位はMinecraftでワールドを開いていると選べます", () => { }, false));
            _ = LoadPartsAsync();
            return;
        }
        var added = Poses.Select(p => p.Name).ToHashSet();
        foreach (var (name, depth) in parts)
        {
            menu.Items.Add(ListPanel.MenuItem($"{new string('　', depth)}{ScenePartPose.DisplayName(name)}（{name}）",
                () => AddPart(name), !added.Contains(name)));
        }
    }

    void AddPart(string name)
    {
        selectedPart = name;
        EnsurePart(name);
        Refresh();
        SelectedPartChanged?.Invoke(name);
    }

    void RemoveSelected()
    {
        if (list.SelectedItem is not ScenePartPose pose) return;
        var poses = Poses;
        var index = poses.IndexOf(pose);
        selectedPart = poses.Count > 1 ? poses[index == poses.Count - 1 ? index - 1 : index + 1].Name : null;
        SetPoses(poses.Remove(pose));
    }

    void ResetSelected()
    {
        if (list.SelectedItem is not ScenePartPose pose) return;
        BeginEdit?.Invoke(this, EventArgs.Empty);
        Reset(pose);
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    void ResetAll()
    {
        BeginEdit?.Invoke(this, EventArgs.Empty);
        foreach (var pose in Poses) Reset(pose);
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    static void Reset(ScenePartPose pose)
    {
        foreach (var a in new[] { pose.X, pose.Y, pose.Z, pose.Bend, pose.BendSide })
            foreach (var v in a.Values) v.Value = 0;
        pose.Aim = false;
    }

    void OpenWindow()
    {
        if (model is null) return;
        if (window is not null)
        {
            window.Activate();
            return;
        }
        window = new PoseEditorWindow(model, this) { Owner = Application.Current?.MainWindow };
        window.Closed += (_, _) => window = null;
        window.Show();
    }

    public string? SelectedPart
    {
        get => selectedPart;
        set => SetSelected(value, fromList: false);
    }

    public ScenePartPose EnsurePart(string name)
    {
        if (Poses.FirstOrDefault(p => p.Name == name) is { } found) return found;
        var made = new ScenePartPose(name);
        SetPoses(Poses.Add(made));
        return made;
    }

    public void BeginPoseEdit() => BeginEdit?.Invoke(this, EventArgs.Empty);

    public void EndPoseEdit() => EndEdit?.Invoke(this, EventArgs.Empty);
}
