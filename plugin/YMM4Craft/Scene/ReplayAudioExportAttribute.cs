using System.Windows;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Scene;

internal sealed class ReplayAudioExportAttribute : PropertyEditorAttribute2
{
    public ReplayAudioExportAttribute()
    {
        PropertyEditorSize = PropertyEditorSize.FullWidth;
    }

    public override FrameworkElement Create() => new ReplayAudioExportControl();

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        ((ReplayAudioExportControl)control).Target = itemProperties.Length > 0 ? itemProperties[0].PropertyOwner as SceneShapeParameter : null;
    }

    public override void ClearBindings(FrameworkElement control)
    {
        ((ReplayAudioExportControl)control).Target = null;
    }
}
