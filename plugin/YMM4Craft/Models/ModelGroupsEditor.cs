using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Models;

internal sealed class ModelGroupsEditor : UserControl, IPropertyEditorControl
{
    public event EventHandler? BeginEdit;
    public event EventHandler? EndEdit;

    readonly System.Windows.Controls.Primitives.UniformGrid panel = new() { Rows = 1 };
    SceneModel? model;
    bool refreshing;

    public SceneModel? Model
    {
        get => model;
        set
        {
            model?.PropertyChanged -= OnModelChanged;
            model = value;
            model?.PropertyChanged += OnModelChanged;
            Rebuild();
        }
    }

    public ModelGroupsEditor() => Content = panel;

    void OnModelChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(SceneModel.UseLook) or nameof(SceneModel.UseArmor) or nameof(SceneModel.UseEffects)
            or nameof(SceneModel.UseParent) or nameof(SceneModel.UsePose) or nameof(SceneModel.Kind))
            Refresh();
    }

    void Rebuild()
    {
        panel.Children.Clear();
        if (model is null) return;
        foreach (var (name, label) in model.AvailableGroups)
        {
            var button = new ToggleButton { Content = label, Tag = name, Padding = new Thickness(0, 3, 0, 3) };
            button.Click += (_, e) =>
            {
                e.Handled = true;
                if (refreshing || model is null) return;
                BeginEdit?.Invoke(this, EventArgs.Empty);
                model.SetGroupEnabled(name, button.IsChecked == true);
                EndEdit?.Invoke(this, EventArgs.Empty);
            };
            panel.Children.Add(button);
        }
        Refresh();
    }

    void Refresh()
    {
        if (model is null) return;
        refreshing = true;
        foreach (var button in panel.Children.OfType<ToggleButton>())
            button.IsChecked = model.IsGroupEnabled((string)button.Tag);
        refreshing = false;
    }
}
