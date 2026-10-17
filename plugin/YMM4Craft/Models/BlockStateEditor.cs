using System.ComponentModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using YMM4Craft.Controls;
using YMM4Craft.Core;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Models;

internal sealed class BlockStateEditor : UserControl, IPropertyEditorControl
{
    public static readonly DependencyProperty ValueProperty = DependencyProperty.Register(
        nameof(Value), typeof(string), typeof(BlockStateEditor),
        new FrameworkPropertyMetadata("", FrameworkPropertyMetadataOptions.BindsTwoWayByDefault, (d, _) => ((BlockStateEditor)d).ShowValue()));

    public string Value
    {
        get => (string)GetValue(ValueProperty);
        set => SetValue(ValueProperty, value);
    }

    public event EventHandler? BeginEdit;
    public event EventHandler? EndEdit;

    sealed record Choice(string Value, string Label)
    {
        public override string ToString() => Label;
    }

    readonly Grid grid = new();
    readonly TextBlock message = new() { TextWrapping = TextWrapping.Wrap, Opacity = 0.7, Margin = new Thickness(0, 2, 0, 2) };
    readonly List<(BlockProperty Property, ComboBox Box)> rows = [];
    IReadOnlyList<BlockProperty>? properties;
    string? loadedType;
    string? note;
    bool showing;
    SceneModel? model;

    public BlockStateEditor()
    {
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var panel = new StackPanel();
        panel.Children.Add(message);
        panel.Children.Add(grid);
        Content = panel;
        Loaded += (_, _) => _ = LoadAsync();
    }

    public SceneModel? Model
    {
        get => model;
        set
        {
            if (ReferenceEquals(model, value)) return;
            model?.PropertyChanged -= OnModelPropertyChanged;
            model = value;
            model?.PropertyChanged += OnModelPropertyChanged;
            _ = LoadAsync();
        }
    }

    void OnModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(SceneModel.Type)) _ = LoadAsync();
    }

    async Task LoadAsync()
    {
        var type = model is null ? null : CatalogNames.ToId(model.Type);
        if (type is null)
        {
            Build(null, "ブロックの種類を選ぶと、向きや形を選べます");
            return;
        }
        if (type == loadedType && properties is not null) return;
        if (!CraftLink.Shared.IsConnected)
        {
            Build(null, "状態は Minecraft でワールドを開いていると選べます");
            return;
        }
        try
        {
            var found = await CraftLink.Shared.GetBlockPropertiesAsync(type);
            if (model is null || CatalogNames.ToId(model.Type) != type) return;
            loadedType = type;
            Build(found, found.Count == 0 ? "このブロックには変えられる状態がありません" : null);
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
            Build(null, "状態は Minecraft でワールドを開いていると選べます");
        }
    }

    void Build(IReadOnlyList<BlockProperty>? found, string? text)
    {
        if (found is null) loadedType = null;
        properties = found;
        note = text;
        ShowNote();

        grid.Children.Clear();
        grid.RowDefinitions.Clear();
        rows.Clear();
        if (found is null) return;
        foreach (var property in found)
        {
            var row = grid.RowDefinitions.Count;
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var label = new TextBlock { Text = BlockStateNames.Property(property.Name), VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 1, 8, 1) };
            Grid.SetRow(label, row);
            grid.Children.Add(label);

            var choices = new List<Choice> { new("", $"デフォルト（{BlockStateNames.Value(property.Default)}）") };
            choices.AddRange(property.Values.Select(v => new Choice(v, BlockStateNames.Value(v))));
            var box = new ComboBox { ItemsSource = choices, Height = IconButtons.Size, VerticalContentAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 1, 0, 1) };
            box.SelectionChanged += (_, _) =>
            {
                if (!showing) Commit();
            };
            ComboWheel.Attach(box, delta =>
            {
                var next = Math.Clamp(box.SelectedIndex + delta, 0, choices.Count - 1);
                if (next != box.SelectedIndex) box.SelectedIndex = next;
                return Task.CompletedTask;
            });
            Grid.SetRow(box, row);
            Grid.SetColumn(box, 1);
            grid.Children.Add(box);
            rows.Add((property, box));
        }
        ShowValue();
    }

    void ShowValue()
    {
        ShowNote();
        if (properties is null) return;
        var current = BlockStateNames.Parse(Value ?? "");
        showing = true;
        foreach (var (property, box) in rows)
        {
            var value = current.TryGetValue(property.Name, out var v) && property.Values.Contains(v) ? v : "";
            box.SelectedItem = ((List<Choice>)box.ItemsSource).First(c => c.Value == value);
        }
        showing = false;
    }

    void ShowNote()
    {
        var text = properties is null && note is not null && (Value ?? "").Length > 0 ? $"{note}（今の値: {Value}）" : note;
        message.Text = text ?? "";
        message.Visibility = text is null ? Visibility.Collapsed : Visibility.Visible;
    }

    void Commit()
    {
        var pairs = rows
            .Where(r => r.Box.SelectedItem is Choice { Value.Length: > 0 })
            .Select(r => KeyValuePair.Create(r.Property.Name, ((Choice)r.Box.SelectedItem).Value));
        var text = BlockStateNames.Format(pairs);
        if (text == (Value ?? "")) return;
        BeginEdit?.Invoke(this, EventArgs.Empty);
        Value = text;
        EndEdit?.Invoke(this, EventArgs.Empty);
    }
}
