using System.Windows;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Models;

[AttributeUsage(AttributeTargets.Property)]
internal sealed class PartPoseListAttribute : PropertyEditorAttribute2
{
    public PartPoseListAttribute() => PropertyEditorSize = PropertyEditorSize.FullWidth;

    public override FrameworkElement Create() => new PartPoseListEditor();

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        if (control is PartPoseListEditor editor && itemProperties.FirstOrDefault() is { } p) editor.Attach(p.Item, p.PropertyOwner, p.PropertyInfo);
    }

    public override void ClearBindings(FrameworkElement control)
    {
        if (control is PartPoseListEditor editor) editor.Detach();
    }
}
