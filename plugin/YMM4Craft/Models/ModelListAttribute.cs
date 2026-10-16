using System.Windows;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Models;

[AttributeUsage(AttributeTargets.Property)]
internal sealed class ModelListAttribute : PropertyEditorAttribute2
{
    public ModelListAttribute() => PropertyEditorSize = PropertyEditorSize.FullWidth;

    public override FrameworkElement Create() => new ModelListEditor();

    public override void SetBindings(FrameworkElement control, ItemProperty[] itemProperties)
    {
        if (control is ModelListEditor editor && itemProperties.FirstOrDefault() is { } p) editor.Attach(p.Item, p.PropertyOwner, p.PropertyInfo);
    }

    public override void ClearBindings(FrameworkElement control)
    {
        if (control is ModelListEditor editor) editor.Detach();
    }
}
