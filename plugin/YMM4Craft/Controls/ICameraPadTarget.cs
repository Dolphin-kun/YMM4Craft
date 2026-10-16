using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Controls;

internal interface ICameraPadTarget
{
    Animation CameraX { get; }
    Animation CameraY { get; }
    Animation CameraZ { get; }
    Animation Yaw { get; }
    Animation Pitch { get; }

    (int Frame, int Length) PreviewPosition { get; }
}
