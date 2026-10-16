using System.ComponentModel.DataAnnotations;

namespace YMM4Craft.Models;

public enum SceneModelKind
{
    [Display(Name = "Mob")] Mob = 0,
    [Display(Name = "アイテム")] Item = 1,
    [Display(Name = "プレイヤー")] Player = 2,
}
