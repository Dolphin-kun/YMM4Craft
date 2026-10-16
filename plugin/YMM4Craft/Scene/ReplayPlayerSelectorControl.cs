using System.IO;
using System.Windows;
using System.Windows.Controls;
using YMM4Craft.Core;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Scene;

internal sealed class ReplayPlayerSelectorControl : UserControl, IPropertyEditorControl
{
    const string UnknownRecorded = "録画した人";

    public static readonly DependencyProperty ValueProperty = DependencyProperty.Register(
        nameof(Value), typeof(string), typeof(ReplayPlayerSelectorControl),
        new FrameworkPropertyMetadata("", FrameworkPropertyMetadataOptions.BindsTwoWayByDefault, (d, _) => ((ReplayPlayerSelectorControl)d).ShowValue()));

    public string Value
    {
        get => (string)GetValue(ValueProperty);
        set => SetValue(ValueProperty, value);
    }

    public event EventHandler? BeginEdit;
    public event EventHandler? EndEdit;

    readonly ComboBox box;
    bool updating;
    string recordedName = UnknownRecorded;

    public ReplayPlayerSelectorControl()
    {
        box = new ComboBox
        {
            IsEditable = true,
            VerticalContentAlignment = VerticalAlignment.Center,
            ToolTip = "視点にするプレイヤー。▼で近くにいるプレイヤーを選べる",
        };
        box.DropDownOpened += async (_, _) => await RefreshAsync();
        box.SelectionChanged += (_, _) =>
        {
            if (!updating && box.SelectedItem is string name) Commit(name);
        };
        box.LostKeyboardFocus += (_, _) => Commit(box.Text);
        Content = box;
        ShowValue();
        Loaded += async (_, _) => await RefreshAsync();
    }

    void ShowValue()
    {
        updating = true;
        box.Text = string.IsNullOrEmpty(Value) ? recordedName : Value;
        updating = false;
    }

    void Commit(string text)
    {
        var name = text.Trim() == recordedName || text.Trim() == UnknownRecorded ? "" : text.Trim();
        if (name == (Value ?? "")) return;
        BeginEdit?.Invoke(this, EventArgs.Empty);
        Value = name;
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    async Task RefreshAsync()
    {
        List<string> others = [];
        try
        {
            if (CraftLink.Shared.IsConnected)
            {
                var (recorded, list) = await CraftLink.Shared.ListReplayPlayersAsync();
                if (recorded.Length > 0) recordedName = recorded;
                others = list;
            }
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or YMM4Craft.Core.Protocol.CraftCommandException)
        {
        }
        updating = true;
        box.ItemsSource = new List<string> { recordedName }.Concat(others).ToList();
        box.Text = string.IsNullOrEmpty(Value) ? recordedName : Value;
        updating = false;
    }
}
