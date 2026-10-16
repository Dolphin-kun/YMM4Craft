using System.Windows;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Controls;

internal sealed class CameraPadAttribute : PropertyEditorAttribute2
{
    public CameraPadAttribute()
    {
        PropertyEditorSize = PropertyEditorSize.FullWidth;
    }

    public override FrameworkElement Create() => new CameraPad();

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        ((CameraPad)control).Target = itemProperties.Length > 0 ? itemProperties[0].PropertyOwner as ICameraPadTarget : null;
    }

    public override void ClearBindings(FrameworkElement control)
    {
        ((CameraPad)control).Target = null;
    }
}
