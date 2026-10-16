using System.IO;
using System.Windows;
using System.Windows.Controls;
using YMM4Craft.Controls;
using YMM4Craft.Core;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Models;

internal sealed class ParentSelectorControl : UserControl, IPropertyEditorControl
{
    public static readonly DependencyProperty ValueProperty = DependencyProperty.Register(
        nameof(Value), typeof(string), typeof(ParentSelectorControl),
        new FrameworkPropertyMetadata("", FrameworkPropertyMetadataOptions.BindsTwoWayByDefault, (d, _) => ((ParentSelectorControl)d).ShowValue()));

    public string Value
    {
        get => (string)GetValue(ValueProperty);
        set => SetValue(ValueProperty, value);
    }

    public SceneModel? Model { get; set; }

    public event EventHandler? BeginEdit;
    public event EventHandler? EndEdit;

    sealed record Choice(string Value, string Label)
    {
        public override string ToString() => Label;
    }

    readonly bool parts;
    readonly ComboBox box = new() { VerticalContentAlignment = VerticalAlignment.Center };
    bool showing;

    public ParentSelectorControl(bool parts)
    {
        this.parts = parts;
        Content = box;
        box.DropDownOpened += async (_, _) => await FillAsync();
        box.SelectionChanged += (_, _) =>
        {
            if (showing || box.SelectedItem is not Choice choice || choice.Value == Value) return;
            Commit(choice.Value);
        };
        Loaded += (_, _) => ShowValue();
        ComboWheel.Attach(box, StepAsync);
    }

    async Task StepAsync(int delta)
    {
        var choices = parts ? await PartChoicesAsync() : ModelChoices();
        var index = choices.FindIndex(c => c.Value == (Value ?? ""));
        var next = Math.Clamp((index < 0 ? 0 : index) + delta, 0, choices.Count - 1);
        if (next == index || choices.Count == 0) return;
        Commit(choices[next].Value);
    }

    async Task FillAsync()
    {
        var choices = parts ? await PartChoicesAsync() : ModelChoices();
        showing = true;
        box.ItemsSource = choices;
        box.SelectedItem = choices.FirstOrDefault(c => c.Value == Value);
        showing = false;
    }

    List<Choice> ModelChoices()
    {
        var list = new List<Choice> { new("", "なし") };
        if (Model is null) return list;
        var siblings = Model.Siblings;
        for (var i = 0; i < siblings.Count; i++)
        {
            if (ReferenceEquals(siblings[i], Model)) continue;
            list.Add(new Choice(siblings[i].Key, $"{i + 1}. {siblings[i].Label}"));
        }
        return list;
    }

    async Task<List<Choice>> PartChoicesAsync()
    {
        var list = new List<Choice> { new("", "デフォルト") };
        if (Model?.ParentModel is not { } parent || parent.Kind == SceneModelKind.Item) return list;
        try
        {
            var kind = parent.Kind == SceneModelKind.Player ? "player" : "mob";
            var found = await CraftLink.Shared.GetModelPartsAsync(kind, CatalogNames.ToId(parent.Type) ?? "", parent.Baby);
            list.AddRange(found.Select(p => new Choice(p.Name, $"{new string('　', p.Depth)}{ScenePartPose.DisplayName(p.Name)}")));
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
            if (Value.Length > 0) list.Add(new Choice(Value, ScenePartPose.DisplayName(Value)));
        }
        return list;
    }

    void Commit(string value)
    {
        BeginEdit?.Invoke(this, EventArgs.Empty);
        if (!parts && Model is not null) Model.ChangeParent(value);
        else Value = value;
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    void ShowValue()
    {
        showing = true;
        var label = parts
            ? (string.IsNullOrEmpty(Value) ? "デフォルト" : ScenePartPose.DisplayName(Value))
            : (string.IsNullOrEmpty(Value) ? "なし" : Model?.Siblings.FirstOrDefault(m => m.Key == Value)?.Label ?? "見つかりません");
        var current = new Choice(Value ?? "", label);
        box.ItemsSource = new[] { current };
        box.SelectedItem = current;
        showing = false;
    }
}
