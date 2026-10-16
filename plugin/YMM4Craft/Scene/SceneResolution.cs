using System.ComponentModel.DataAnnotations;

namespace YMM4Craft.Scene;

public enum SceneResolution
{
    [Display(Name = "プロジェクトと同じ")] Project = 0,
    [Display(Name = "カスタム")] Custom = 1,
}
