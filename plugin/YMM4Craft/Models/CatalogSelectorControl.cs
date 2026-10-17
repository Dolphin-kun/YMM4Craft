using System.IO;
using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Data;
using System.Windows.Input;
using YMM4Craft.Controls;
using YMM4Craft.Core;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Models;

internal sealed class CatalogSelectorControl : UserControl, IPropertyEditorControl
{
    public static readonly DependencyProperty ValueProperty = DependencyProperty.Register(
        nameof(Value), typeof(string), typeof(CatalogSelectorControl),
        new FrameworkPropertyMetadata("", FrameworkPropertyMetadataOptions.BindsTwoWayByDefault, (d, _) => ((CatalogSelectorControl)d).ShowValue()));

    public string Value
    {
        get => (string)GetValue(ValueProperty);
        set => SetValue(ValueProperty, value);
    }

    public CatalogKind Kind { get; set; } = CatalogKind.Mob;

    public event EventHandler? BeginEdit;
    public event EventHandler? EndEdit;

    readonly ComboBox box;
    ICollectionView? view;
    bool showing;
    bool typing;

    public CatalogSelectorControl()
    {
        box = new ComboBox
        {
            IsEditable = true,
            IsTextSearchEnabled = false,
            VerticalContentAlignment = VerticalAlignment.Center,
            StaysOpenOnEdit = true,
            DisplayMemberPath = nameof(CatalogEntry.Name),
            MaxDropDownHeight = 320,
        };
        TextSearch.SetTextPath(box, nameof(CatalogEntry.Name));
        box.SetValue(VirtualizingPanel.IsVirtualizingProperty, true);
        box.ItemsPanel = new ItemsPanelTemplate(new FrameworkElementFactory(typeof(VirtualizingStackPanel)));
        Content = box;

        box.DropDownOpened += async (_, _) => await LoadAsync();
        ComboWheel.Attach(box, StepAsync, () => !typing);
        box.DropDownClosed += (_, _) =>
        {
            if (box.SelectedItem is CatalogEntry entry && box.Text == entry.Name) Commit(entry.Id);
        };
        box.AddHandler(TextBoxBase.TextChangedEvent, new TextChangedEventHandler((_, _) => OnTyped()));
        box.PreviewKeyDown += (_, e) =>
        {
            if (e.Key == Key.Enter)
            {
                CommitText();
                box.IsDropDownOpen = false;
                e.Handled = true;
            }
            else if (e.Key == Key.Escape)
            {
                ShowValue();
                box.IsDropDownOpen = false;
            }
        };
        box.LostKeyboardFocus += (_, e) =>
        {
            if (!box.IsKeyboardFocusWithin) CommitText();
        };
    }

    async Task LoadAsync()
    {
        if (view is not null) return;
        SceneCatalog catalog;
        try
        {
            catalog = await CraftLink.Shared.GetCatalogAsync();
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
            box.ToolTip = $"一覧を取得できませんでした（{ex.Message}）。minecraft:zombie のような名前を直接入れてください";
            return;
        }
        view = CollectionViewSource.GetDefaultView(CatalogNames.Entries(Kind).ToList());
        showing = true;
        var text = box.Text;
        box.ItemsSource = view;
        box.Text = text;
        showing = false;
    }

    async void OnTyped()
    {
        if (showing || !box.IsKeyboardFocusWithin) return;
        typing = true;
        await LoadAsync();
        if (view is null) return;
        var text = box.Text.Trim();
        showing = true;
        view.Filter = text.Length == 0
            ? null
            : o => o is CatalogEntry e && (e.Name.Contains(text, StringComparison.OrdinalIgnoreCase) || e.Id.Contains(text, StringComparison.OrdinalIgnoreCase));
        showing = false;
        if (!box.IsDropDownOpen) box.IsDropDownOpen = true;
        if (box.Template.FindName("PART_EditableTextBox", box) is TextBox textBox) textBox.SelectionStart = textBox.Text.Length;
    }

    void CommitText()
    {
        if (!typing) return;
        var text = box.Text.Trim();
        Commit(text.Length == 0 ? "" : CatalogNames.ToId(text) ?? Value);
    }

    async Task StepAsync(int delta)
    {
        await LoadAsync();
        if (view is null) return;
        var entries = view.SourceCollection.Cast<CatalogEntry>().ToList();
        if (entries.Count == 0) return;
        var id = CatalogNames.ToId(Value);
        var index = entries.FindIndex(e => e.Id == id);
        var next = index < 0 ? 0 : Math.Clamp(index + delta, 0, entries.Count - 1);
        if (next == index) return;
        typing = true;
        Commit(entries[next].Id);
    }

    void Commit(string id)
    {
        typing = false;
        view?.Filter = null;
        if (id != Value)
        {
            BeginEdit?.Invoke(this, EventArgs.Empty);
            Value = id;
            EndEdit?.Invoke(this, EventArgs.Empty);
        }
        ShowValue();
    }

    void ShowValue()
    {
        if (typing) return;
        showing = true;
        box.SelectedItem = null;
        box.Text = string.IsNullOrEmpty(Value) ? "" : CatalogNames.NameOf(Kind, Value);
        box.ToolTip = string.IsNullOrEmpty(Value) ? null : Value;
        showing = false;
    }
}
