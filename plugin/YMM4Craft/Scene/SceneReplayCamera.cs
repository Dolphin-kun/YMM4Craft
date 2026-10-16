using System.ComponentModel.DataAnnotations;

namespace YMM4Craft.Scene;

public enum SceneReplayCamera
{
    [Display(Name = "カメラの設定どおり", Description = "カメラの欄の位置と向きから撮る")] Free = 0,
    [Display(Name = "視点", Description = "プレイヤーの目の位置と向きから撮る")] Player = 1,
}
