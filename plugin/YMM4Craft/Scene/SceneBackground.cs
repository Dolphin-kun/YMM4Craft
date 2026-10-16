using System.ComponentModel.DataAnnotations;

namespace YMM4Craft.Scene;

public enum SceneBackground
{
    [Display(Name = "ワールドのまま", Description = "空や地形などをそのまま描く")] World = 0,
    [Display(Name = "透過", Description = "モデルだけを描き、背景を透明にする")] Transparent = 1,
    [Display(Name = "単色", Description = "モデルだけを描き、背景を1色で塗る")] Color = 2,
}
