using System.Windows.Controls;
using System.Windows.Input;

namespace YMM4Craft.Controls;

internal static class ComboWheel
{
    public static void Attach(ComboBox box, Func<int, Task> step, Func<bool>? canStep = null)
    {
        var scrolled = false;
        box.PreviewMouseWheel += async (_, e) =>
        {
            if (box.IsDropDownOpen || canStep?.Invoke() == false) return;
            var modifiers = Keyboard.Modifiers;
            if (e.RightButton != MouseButtonState.Pressed && !modifiers.HasFlag(ModifierKeys.Shift) && !modifiers.HasFlag(ModifierKeys.Control)) return;
            e.Handled = true;
            scrolled = true;
            await step(e.Delta < 0 ? 1 : -1);
        };
        box.PreviewMouseRightButtonDown += (_, _) =>
        {
            box.CaptureMouse();
            scrolled = false;
        };
        box.PreviewMouseRightButtonUp += (_, e) =>
        {
            box.ReleaseMouseCapture();
            if (scrolled) e.Handled = true;
        };
    }
}
