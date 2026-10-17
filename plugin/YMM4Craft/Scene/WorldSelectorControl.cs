using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Threading;
using YMM4Craft.Controls;
using YMM4Craft.Core;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Resources.Icons;

namespace YMM4Craft.Scene;

internal sealed class WorldSelectorControl : UserControl, IPropertyEditorControl
{
    public static readonly DependencyProperty ValueProperty = DependencyProperty.Register(
        nameof(Value), typeof(string), typeof(WorldSelectorControl),
        new FrameworkPropertyMetadata("", FrameworkPropertyMetadataOptions.BindsTwoWayByDefault,
            (d, e) => ((WorldSelectorControl)d).OnValueChanged()));

    public string Value
    {
        get => (string)GetValue(ValueProperty);
        set => SetValue(ValueProperty, value);
    }

    public object? Owner { get; set; }

    public event EventHandler? BeginEdit;
    public event EventHandler? EndEdit;

    const int OpenMode = 0;
    const int CreateMode = 1;
    const int ReplayMode = 2;

    readonly ComboBox modeBox;
    readonly DockPanel openRow;
    readonly DockPanel createRow;
    readonly DockPanel replayRow;
    readonly ComboBox replayBox;
    readonly Button replayButton;
    readonly ComboBox nameBox;
    readonly Button openButton;
    readonly TextBox newNameBox;
    readonly Button createButton;
    readonly ComboBox kindBox;
    readonly TextBlock statusText;
    readonly Button waitButton;
    readonly Button disconnectButton;
    readonly DispatcherTimer statusTimer;
    bool busy;
    bool showing;

    const double RowHeight = IconButtons.Size;
    static readonly Thickness RowMargin = new(0);

    public WorldSelectorControl()
    {
        modeBox = new ComboBox
        {
            ItemsSource = new[] { "既存のワールドを開く", "新しいワールドを作る", "リプレイを開く" },
            SelectedIndex = OpenMode,
            Height = RowHeight,
            Margin = RowMargin,
            VerticalContentAlignment = VerticalAlignment.Center,
            ToolTip = "ワールドの選択",
        };
        modeBox.SelectionChanged += (_, _) => UpdateMode();
        ComboWheel.Attach(modeBox, delta =>
        {
            modeBox.SelectedIndex = Math.Clamp(modeBox.SelectedIndex + delta, 0, modeBox.Items.Count - 1);
            return Task.CompletedTask;
        });

        nameBox = new ComboBox { IsEditable = true, Height = RowHeight, VerticalContentAlignment = VerticalAlignment.Center, ToolTip = "ワールド名" };
        nameBox.DropDownOpened += async (_, _) => await RefreshWorldListAsync();
        nameBox.SelectionChanged += (_, _) =>
        {
            if (!showing && nameBox.SelectedItem is string world) Commit(world);
        };
        nameBox.KeyDown += (_, e) =>
        {
            if (e.Key != System.Windows.Input.Key.Enter) return;
            e.Handled = true;
            Commit(nameBox.Text.Trim());
        };
        openButton = IconButtons.Create(GeometryKeys.FolderOpen, "ワールドのフォルダをエクスプローラーで開く");
        openButton.Click += async (_, _) => await ShowWorldFolderAsync();
        openRow = Row(nameBox, openButton);

        newNameBox = new TextBox { Height = RowHeight, VerticalContentAlignment = VerticalAlignment.Center, ToolTip = "新しく作るワールドの名前" };
        kindBox = new ComboBox
        {
            ItemsSource = new[] { "通常", "平坦", "グリーンバック" },
            SelectedIndex = 0,
            Height = RowHeight,
            VerticalContentAlignment = VerticalAlignment.Center,
            ToolTip = "作るワールドの種類",
        };
        ComboWheel.Attach(kindBox, delta =>
        {
            kindBox.SelectedIndex = Math.Clamp(kindBox.SelectedIndex + delta, 0, kindBox.Items.Count - 1);
            return Task.CompletedTask;
        });
        createButton = IconButtons.Create(GeometryKeys.Plus, "作成");
        createButton.Click += async (_, _) => await CreateAsync();
        createRow = Row(newNameBox, kindBox, createButton);
        createRow.Visibility = Visibility.Collapsed;

        replayBox = new ComboBox { Height = RowHeight, VerticalContentAlignment = VerticalAlignment.Center, DisplayMemberPath = nameof(ReplayChoice.Label), ToolTip = "リプレイ" };
        replayBox.DropDownOpened += async (_, _) => await RefreshReplayListAsync();
        replayBox.SelectionChanged += (_, _) =>
        {
            if (!showing && replayBox.SelectedItem is ReplayChoice choice) Commit(CraftLink.ReplayPrefix + choice.Name);
        };
        replayButton = IconButtons.Create(GeometryKeys.FolderOpen, "リプレイのファイルをエクスプローラーで開く");
        replayButton.Click += async (_, _) => await ShowReplayFileAsync();
        replayRow = Row(replayBox, replayButton);
        replayRow.Visibility = Visibility.Collapsed;

        statusText = new TextBlock { TextWrapping = TextWrapping.Wrap, Opacity = 0.8, VerticalAlignment = VerticalAlignment.Center };
        waitButton = IconButtons.Create(GeometryKeys.Play, "接続を待つ");
        waitButton.Click += (_, _) => CraftLink.Shared.Resume();
        disconnectButton = IconButtons.Create(GeometryKeys.LanDisconnect, "切断");
        disconnectButton.Click += (_, _) => CraftLink.Shared.Disconnect();
        var statusRow = Row(statusText, disconnectButton, waitButton);

        var panel = new StackPanel();
        panel.Children.Add(modeBox);
        panel.Children.Add(openRow);
        panel.Children.Add(createRow);
        panel.Children.Add(replayRow);
        panel.Children.Add(statusRow);
        Content = panel;

        statusTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(500) };
        statusTimer.Tick += (_, _) => UpdateStatus();
        Loaded += (_, _) => { UpdateStatus(); statusTimer.Start(); };
        Unloaded += (_, _) => statusTimer.Stop();
    }

    static DockPanel Row(FrameworkElement fill, params FrameworkElement[] right)
    {
        var row = new DockPanel { Margin = RowMargin, MinHeight = RowHeight };
        for (var i = right.Length - 1; i >= 0; i--)
        {
            right[i].Margin = new Thickness(0);
            right[i].VerticalAlignment = VerticalAlignment.Top;
            DockPanel.SetDock(right[i], Dock.Right);
            row.Children.Add(right[i]);
        }
        row.Children.Add(fill);
        return row;
    }

    void UpdateMode()
    {
        if (openRow is null || createRow is null || replayRow is null) return;
        openRow.Visibility = modeBox.SelectedIndex == OpenMode ? Visibility.Visible : Visibility.Collapsed;
        createRow.Visibility = modeBox.SelectedIndex == CreateMode ? Visibility.Visible : Visibility.Collapsed;
        replayRow.Visibility = modeBox.SelectedIndex == ReplayMode ? Visibility.Visible : Visibility.Collapsed;
    }

    void OnValueChanged()
    {
        var value = Value ?? "";
        if (value.StartsWith(CraftLink.ReplayPrefix, StringComparison.Ordinal))
        {
            var name = value[CraftLink.ReplayPrefix.Length..];
            showing = true;
            if (replayBox.SelectedItem is not ReplayChoice current || current.Name != name)
            {
                var choice = new ReplayChoice(name, name, "");
                replayBox.ItemsSource = new[] { choice };
                replayBox.SelectedItem = choice;
            }
            modeBox.SelectedIndex = ReplayMode;
            if (!nameBox.IsKeyboardFocusWithin) nameBox.Text = "";
            showing = false;
            return;
        }
        showing = true;
        if (!nameBox.IsKeyboardFocusWithin) nameBox.Text = value;
        showing = false;
        if (modeBox.SelectedIndex == ReplayMode && value.Length > 0) modeBox.SelectedIndex = OpenMode;
    }

    sealed record ReplayChoice(string Name, string Label, string Path);

    async Task RefreshReplayListAsync()
    {
        try
        {
            var selected = (replayBox.SelectedItem as ReplayChoice)?.Name;
            var choices = (await CraftLink.Shared.ListReplaysAsync())
                .Select(r => new ReplayChoice(r.Name,
                    $"{r.Name}（{(int)r.Duration.TotalMinutes}:{r.Duration.Seconds:00}{(r.World.Length > 0 ? $"・{r.World}" : "")}{(r.Source.Length > 0 ? $"・{r.Source}" : "")}{(r.Playable ? "" : "・別のバージョンのため再生できません")}）", r.Path))
                .ToList();
            showing = true;
            replayBox.ItemsSource = choices;
            replayBox.SelectedItem = choices.FirstOrDefault(c => c.Name == selected);
            showing = false;
            if (choices.Count == 0) statusText.Text = "リプレイがありません。MinecraftのYMM4Craftの画面で、リプレイの録画をオンにしてください";
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or YMM4Craft.Core.Protocol.CraftCommandException)
        {
            statusText.Text = "リプレイの一覧は、Minecraftと接続してから表示できます";
        }
    }

    void Commit(string world)
    {
        if (world.Length == 0 || world == Value) return;
        BeginEdit?.Invoke(this, EventArgs.Empty);
        Value = world;
        EndEdit?.Invoke(this, EventArgs.Empty);
        if (world.StartsWith(CraftLink.ReplayPrefix, StringComparison.Ordinal)) return;
        showing = true;
        nameBox.Text = world;
        showing = false;
    }

    async Task ShowWorldFolderAsync()
    {
        var world = nameBox.Text.Trim();
        try
        {
            if (CraftLink.Shared.SavesFolder is null) await CraftLink.Shared.ListWorldsAsync();
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or YMM4Craft.Core.Protocol.CraftCommandException)
        {
        }
        if (CraftLink.Shared.SavesFolder is not { } saves)
        {
            statusText.Text = "ワールドのフォルダは、Minecraftと接続してから開けます";
            return;
        }
        if (world.Length == 0 || !FolderOpener.Show(Path.Combine(saves, world))) FolderOpener.Open(saves);
    }

    async Task ShowReplayFileAsync()
    {
        var choice = replayBox.SelectedItem as ReplayChoice;
        var path = choice?.Path ?? "";
        try
        {
            if (path.Length == 0)
            {
                var replays = await CraftLink.Shared.ListReplaysAsync();
                path = replays.FirstOrDefault(r => r.Name == choice?.Name)?.Path ?? replays.FirstOrDefault()?.Path ?? "";
            }
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or YMM4Craft.Core.Protocol.CraftCommandException)
        {
        }
        if (!FolderOpener.Show(path)) statusText.Text = "リプレイのフォルダは、Minecraftと接続してから開けます";
    }

    async Task RefreshWorldListAsync()
    {
        try
        {
            var text = nameBox.Text;
            var worlds = await CraftLink.Shared.ListWorldsAsync();
            showing = true;
            nameBox.ItemsSource = worlds;
            nameBox.Text = text;
            showing = false;
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException)
        {
            nameBox.ItemsSource = null;
            statusText.Text = "ワールドの一覧は、Minecraftと接続してから表示できます";
        }
    }

    async Task CreateAsync()
    {
        var name = newNameBox.Text.Trim();
        if (name.Length == 0)
        {
            statusText.Text = "作るワールドの名前を入力してください";
            return;
        }
        busy = true;
        UpdateStatus();
        try
        {
            var kind = kindBox.SelectedIndex switch { 1 => WorldKind.Flat, 2 => WorldKind.Empty, _ => WorldKind.Normal };
            var folder = await CraftLink.Shared.CreateWorldAsync(name, kind);
            if (kind == WorldKind.Empty && Owner is SceneShapeParameter scene && scene.Background == SceneBackground.World)
            {
                BeginEdit?.Invoke(this, EventArgs.Empty);
                scene.Background = SceneBackground.Color;
                EndEdit?.Invoke(this, EventArgs.Empty);
            }
            Commit(folder);
            newNameBox.Text = "";
            modeBox.SelectedIndex = OpenMode;
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or TaskCanceledException
                                       or YMM4Craft.Core.Protocol.CraftCommandException)
        {
            statusText.Text = $"ワールドを作れませんでした: {ex.Message}";
        }
        finally
        {
            busy = false;
            UpdateStatus();
        }
    }

    void UpdateStatus()
    {
        var link = CraftLink.Shared;
        if (!busy) statusText.Text = link.StatusMessage is { Length: > 0 } m ? m : "Minecraftと接続していません";

        var canSelect = !busy && link.IsConnected && link.State != CraftLinkState.LoadingWorld;
        nameBox.IsEnabled = canSelect;
        openButton.IsEnabled = link.IsConnected;
        newNameBox.IsEnabled = canSelect;
        createButton.IsEnabled = canSelect;
        kindBox.IsEnabled = canSelect;
        replayBox.IsEnabled = canSelect;
        replayButton.IsEnabled = link.IsConnected;
        modeBox.IsEnabled = !busy;

        waitButton.IsEnabled = !link.IsConnected && !link.IsWaiting;
        disconnectButton.IsEnabled = link.IsConnected;
    }
}
