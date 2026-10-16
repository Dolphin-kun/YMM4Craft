using System.Windows;
using System.Windows.Data;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Views.Converters;

namespace YMM4Craft.Models;

[AttributeUsage(AttributeTargets.Property)]
internal sealed class ParentSelectorAttribute : PropertyEditorAttribute2
{
    public override FrameworkElement Create() => new ParentSelectorControl(parts: false);

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        var selector = (ParentSelectorControl)control;
        selector.Model = itemProperties.FirstOrDefault()?.PropertyOwner as SceneModel;
        control.SetBinding(ParentSelectorControl.ValueProperty, ItemPropertiesBinding.Create2(itemProperties));
    }

    public override void ClearBindings(FrameworkElement control)
    {
        BindingOperations.ClearBinding(control, ParentSelectorControl.ValueProperty);
        ((ParentSelectorControl)control).Model = null;
    }
}
