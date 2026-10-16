using YukkuriMovieMaker.Plugin;

namespace YMM4Craft.Settings;

internal sealed class YMM4CraftSettings : SettingsBase<YMM4CraftSettings>
{
    public override SettingsCategory Category => SettingsCategory.Shape;
    public override string Name => "YMM4Craft";
    public override bool HasSettingView => true;
    public override object? SettingView => new YMM4CraftSettingsView();

    public bool AutoExportAudio { get => autoExportAudio; set => Set(ref autoExportAudio, value); }
    bool autoExportAudio;

    public string AudioFolder { get => audioFolder; set => Set(ref audioFolder, value); }
    string audioFolder = "";

    public override void Initialize()
    {
    }
}
