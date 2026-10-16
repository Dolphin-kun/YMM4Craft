using System.Windows;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Models;

[AttributeUsage(AttributeTargets.Property)]
internal sealed class ModelGroupsAttribute : PropertyEditorAttribute2
{
    public ModelGroupsAttribute() => PropertyEditorSize = PropertyEditorSize.FullWidth;

    public override FrameworkElement Create() => new ModelGroupsEditor();

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        ((ModelGroupsEditor)control).Model = itemProperties.FirstOrDefault()?.PropertyOwner as SceneModel;
    }

    public override void ClearBindings(FrameworkElement control)
    {
        ((ModelGroupsEditor)control).Model = null;
    }
}
