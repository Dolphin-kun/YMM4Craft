using System.Windows;
using System.Windows.Data;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Views.Converters;

namespace YMM4Craft.Scene;

internal sealed class WorldSelectorAttribute : PropertyEditorAttribute2
{
    public WorldSelectorAttribute()
    {
        PropertyEditorSize = PropertyEditorSize.FullWidth;
    }

    public override FrameworkElement Create() => new WorldSelectorControl();

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        control.SetBinding(WorldSelectorControl.ValueProperty, ItemPropertiesBinding.Create2(itemProperties));
        if (control is WorldSelectorControl selector) selector.Owner = itemProperties.FirstOrDefault()?.PropertyOwner;
    }

    public override void ClearBindings(FrameworkElement control)
    {
        BindingOperations.ClearBinding(control, WorldSelectorControl.ValueProperty);
        if (control is WorldSelectorControl selector) selector.Owner = null;
    }
}
