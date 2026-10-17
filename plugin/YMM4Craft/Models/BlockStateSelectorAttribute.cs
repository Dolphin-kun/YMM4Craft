using System.Windows;
using System.Windows.Data;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Views.Converters;

namespace YMM4Craft.Models;

[AttributeUsage(AttributeTargets.Property)]
internal sealed class BlockStateSelectorAttribute : PropertyEditorAttribute2
{
    public override FrameworkElement Create() => new BlockStateEditor();

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        var editor = (BlockStateEditor)control;
        editor.Model = itemProperties.FirstOrDefault()?.PropertyOwner as SceneModel;
        control.SetBinding(BlockStateEditor.ValueProperty, ItemPropertiesBinding.Create2(itemProperties));
    }

    public override void ClearBindings(FrameworkElement control)
    {
        BindingOperations.ClearBinding(control, BlockStateEditor.ValueProperty);
        ((BlockStateEditor)control).Model = null;
    }
}
