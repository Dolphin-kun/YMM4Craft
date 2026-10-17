using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using YMM4Craft.Controls;
using YMM4Craft.Core;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Resources.Icons;

namespace YMM4Craft.Scene;

internal sealed class ReplayAudioExportControl : UserControl, IPropertyEditorControl
{
    public event EventHandler? BeginEdit { add { } remove { } }
    public event EventHandler? EndEdit { add { } remove { } }

    public SceneShapeParameter? Target
    {
        get => target;
        set
        {
            target = value;
            ShowAutoFile();
        }
    }
    SceneShapeParameter? target;

    readonly Button exportButton;
    readonly TextBlock status;
    readonly ProgressBar progress;
    readonly TextBlock progressText;
    readonly Grid progressRow;
    readonly Border fileChip;
    readonly TextBlock fileName;
    string? exported;

    public ReplayAudioExportControl()
    {
        exportButton = IconButtons.Create(GeometryKeys.FileExport, "音を WAV に書き出す\n右クリックで、リプレイ全体を書き出すこともできる");
        exportButton.Click += async (_, _) => await ExportAsync(all: false);
        var menu = new ContextMenu();
        menu.Items.Add(ListPanel.MenuItem("このアイテムの部分を書き出す", () => _ = ExportAsync(all: false)));
        menu.Items.Add(ListPanel.MenuItem("リプレイ全体を書き出す（録画した人の耳で聞く）", () => _ = ExportAsync(all: true)));
        exportButton.ContextMenu = menu;
        exportButton.ContextMenuOpening += (_, e) =>
        {
            if (!exportButton.IsEnabled) e.Handled = true;
        };
        status = new TextBlock { TextWrapping = TextWrapping.Wrap, Opacity = 0.8, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(6, 0, 0, 0) };
        var folderButton = IconButtons.Create(GeometryKeys.FolderOpen, "書き出したフォルダをエクスプローラーで開く");
        folderButton.Click += (_, _) =>
        {
            if (!FolderOpener.Show(exported) && !FolderOpener.Open(ReplayAudioExporter.Folder)) status.Text = "まだ書き出していません";
        };
        var row = new DockPanel();
        DockPanel.SetDock(exportButton, Dock.Left);
        DockPanel.SetDock(folderButton, Dock.Left);
        row.Children.Add(exportButton);
        row.Children.Add(folderButton);
        row.Children.Add(status);

        fileName = new TextBlock { TextTrimming = TextTrimming.CharacterEllipsis, VerticalAlignment = VerticalAlignment.Center };
        fileChip = new Border
        {
            Child = fileName,
            Padding = new Thickness(8, 3, 8, 3),
            Margin = new Thickness(0, 4, 0, 0),
            BorderThickness = new Thickness(1),
            BorderBrush = new SolidColorBrush(Color.FromArgb(0x80, 0x80, 0x80, 0x80)),
            Background = new SolidColorBrush(Color.FromArgb(0x20, 0x40, 0xA0, 0xFF)),
            Cursor = Cursors.Hand,
            Visibility = Visibility.Collapsed,
            ToolTip = "タイムラインへドラッグすると音声アイテムとして置ける。ダブルクリックでフォルダを開く",
        };
        fileChip.MouseMove += (_, e) =>
        {
            if (e.LeftButton != MouseButtonState.Pressed || exported is null || !File.Exists(exported)) return;
            DragDrop.DoDragDrop(fileChip, new DataObject(DataFormats.FileDrop, new[] { exported }), DragDropEffects.Copy);
        };
        fileChip.MouseLeftButtonDown += (_, e) =>
        {
            if (e.ClickCount == 2 && exported is not null && File.Exists(exported)) FolderOpener.Show(exported);
        };

        progress = new ProgressBar { Minimum = 0, Maximum = 1, Height = 16 };
        progressText = new TextBlock
        {
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            FontSize = 11,
            IsHitTestVisible = false,
        };
        progressRow = new Grid { Margin = new Thickness(0, 4, 0, 0), Visibility = Visibility.Collapsed };
        progressRow.Children.Add(progress);
        progressRow.Children.Add(progressText);

        var panel = new StackPanel();
        panel.Children.Add(row);
        panel.Children.Add(progressRow);
        panel.Children.Add(fileChip);
        Content = panel;

        Loaded += (_, _) =>
        {
            ReplayAudioExporter.Exported += OnExported;
            ReplayAudioExporter.Progress += OnProgress;
            ShowAutoFile();
        };
        Unloaded += (_, _) =>
        {
            ReplayAudioExporter.Exported -= OnExported;
            ReplayAudioExporter.Progress -= OnProgress;
        };
    }

    void OnProgress(SceneShapeParameter p, double fraction, string text)
    {
        Dispatcher.InvokeAsync(() =>
        {
            if (p != target) return;
            if (fraction < 0)
            {
                progressRow.Visibility = Visibility.Collapsed;
                return;
            }
            progressRow.Visibility = Visibility.Visible;
            progress.Value = fraction;
            progressText.Text = $"{text} {fraction * 100:0}%";
        });
    }

    void OnExported(SceneShapeParameter p, string path)
    {
        Dispatcher.InvokeAsync(() =>
        {
            if (p == target) Show(path);
        });
    }

    void ShowAutoFile()
    {
        if (target is not null && ReplayAudioExporter.AutoPath(target) is { } path && File.Exists(path)) Show(path);
    }

    void Show(string path)
    {
        exported = path;
        fileName.Text = $"♪ {Path.GetFileName(path)}";
        fileChip.Visibility = Visibility.Visible;
    }

    async Task ExportAsync(bool all)
    {
        if (target is not { } p) return;
        if (!CraftLink.Shared.IsConnected || !p.IsReplay)
        {
            status.Text = "リプレイを開いてから書き出してください";
            return;
        }
        var suggested = await ReplayAudioExporter.FileNameAsync(p, all);
        var dialog = new Microsoft.Win32.SaveFileDialog
        {
            Filter = "WAV ファイル|*.wav",
            FileName = $"{suggested}.wav",
            InitialDirectory = Directory.Exists(ReplayAudioExporter.Folder) ? ReplayAudioExporter.Folder : null,
        };
        if (dialog.ShowDialog() != true) return;

        exportButton.IsEnabled = false;
        status.Text = "書き出し中…";
        try
        {
            if (all) await ReplayAudioExporter.ExportAllAsync(p, dialog.FileName, exclusive: true);
            else await ReplayAudioExporter.ExportAsync(p, dialog.FileName, exclusive: true);
            Show(dialog.FileName);
            status.Text = "書き出しました";
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or UnauthorizedAccessException
                                       or InvalidDataException or YMM4Craft.Core.Protocol.CraftCommandException)
        {
            status.Text = $"書き出せませんでした: {ex.Message}";
        }
        finally
        {
            exportButton.IsEnabled = true;
        }
    }
}
