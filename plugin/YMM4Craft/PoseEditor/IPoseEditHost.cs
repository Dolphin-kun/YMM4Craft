using YMM4Craft.Models;

namespace YMM4Craft.PoseEditor;

internal interface IPoseEditHost
{
    string? SelectedPart { get; set; }

    event Action<string?>? SelectedPartChanged;

    ScenePartPose EnsurePart(string name);

    void BeginPoseEdit();

    void EndPoseEdit();
}
