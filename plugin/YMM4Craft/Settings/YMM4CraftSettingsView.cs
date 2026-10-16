using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using Microsoft.Win32;

namespace YMM4Craft.Settings;

internal sealed class YMM4CraftSettingsView : UserControl
{
    public YMM4CraftSettingsView()
    {
        var settings = YMM4CraftSettings.Default;
        var panel = new StackPanel { Margin = new Thickness(8) };

        panel.Children.Add(Heading("Minecraft との接続"));
        panel.Children.Add(Note("YMM4Craft を入れた Minecraft を自分で起動し、タイトル画面の YMM4 のボタンから「YMM4と接続」を押してください。\n" +
            "その画面の「自動で接続」をオンにすると、YMM4 が待っているときにタイトル画面で自動で接続します。\n" +
            "ワールドの欄の「切断」で、Minecraft は普段のプレイに戻ります。"));

        panel.Children.Add(Heading("リプレイの音"));
        var autoAudio = new CheckBox { Content = "シーンを読み込んだときに音も書き出す", Margin = new Thickness(0, 0, 0, 6) };
        autoAudio.SetBinding(System.Windows.Controls.Primitives.ToggleButton.IsCheckedProperty,
            new Binding(nameof(YMM4CraftSettings.AutoExportAudio)) { Source = settings, Mode = BindingMode.TwoWay });
        panel.Children.Add(autoAudio);
        panel.Children.Add(Label("保存先のフォルダ"));
        var audioRow = new DockPanel { Margin = new Thickness(0, 0, 0, 6) };
        var audioBrowse = new Button { Content = "参照…", Padding = new Thickness(10, 2, 10, 2), Margin = new Thickness(6, 0, 0, 0) };
        audioBrowse.Click += (_, _) =>
        {
            var dialog = new OpenFolderDialog { Title = "リプレイの音の保存先" };
            if (dialog.ShowDialog() == true) settings.AudioFolder = dialog.FolderName;
        };
        DockPanel.SetDock(audioBrowse, Dock.Right);
        audioRow.Children.Add(audioBrowse);
        var audioFolder = Bound(nameof(YMM4CraftSettings.AudioFolder));
        audioFolder.Margin = new Thickness(0);
        audioRow.Children.Add(audioFolder);
        panel.Children.Add(audioRow);
        panel.Children.Add(Note($"空なら {Scene.ReplayAudioExporter.DefaultFolder} に保存します。書き出しは、プレビューを動かしていない間に裏で進めます。"));

        Content = new ScrollViewer
        {
            Content = panel,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
        };
    }

    static TextBlock Heading(string text) => new() { Text = text, FontWeight = FontWeights.Bold, Margin = new Thickness(0, 8, 0, 4) };

    static TextBlock Label(string text) => new() { Text = text, Margin = new Thickness(0, 4, 0, 2) };

    static TextBlock Note(string text) => new() { Text = text, TextWrapping = TextWrapping.Wrap, Opacity = 0.75, Margin = new Thickness(0, 0, 0, 6) };

    static TextBox Bound(string property)
    {
        var box = new TextBox { Margin = new Thickness(0, 0, 0, 6) };
        box.SetBinding(TextBox.TextProperty, new Binding(property)
        {
            Source = YMM4CraftSettings.Default,
            Mode = BindingMode.TwoWay,
            UpdateSourceTrigger = UpdateSourceTrigger.LostFocus,
        });
        return box;
    }
}
