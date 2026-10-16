using System.Windows;
using System.Windows.Data;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Views.Converters;

namespace YMM4Craft.Models;

[AttributeUsage(AttributeTargets.Property)]
internal sealed class CatalogSelectorAttribute(CatalogKind kind) : PropertyEditorAttribute2
{
    public override FrameworkElement Create() => new CatalogSelectorControl();

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        var selector = (CatalogSelectorControl)control;
        selector.Kind = kind == CatalogKind.ByModel
            ? itemProperties.FirstOrDefault()?.PropertyOwner is SceneModel { Kind: SceneModelKind.Item } ? CatalogKind.Item : CatalogKind.Mob
            : kind;
        control.SetBinding(CatalogSelectorControl.ValueProperty, ItemPropertiesBinding.Create2(itemProperties));
    }

    public override void ClearBindings(FrameworkElement control)
    {
        BindingOperations.ClearBinding(control, CatalogSelectorControl.ValueProperty);
    }
}
