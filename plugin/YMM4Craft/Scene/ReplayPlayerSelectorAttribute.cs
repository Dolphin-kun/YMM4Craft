using System.Windows;
using System.Windows.Data;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Views.Converters;

namespace YMM4Craft.Scene;

internal sealed class ReplayPlayerSelectorAttribute : PropertyEditorAttribute2
{
    public override FrameworkElement Create() => new ReplayPlayerSelectorControl();

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        control.SetBinding(ReplayPlayerSelectorControl.ValueProperty, ItemPropertiesBinding.Create2(itemProperties));
    }

    public override void ClearBindings(FrameworkElement control)
    {
        BindingOperations.ClearBinding(control, ReplayPlayerSelectorControl.ValueProperty);
    }
}
