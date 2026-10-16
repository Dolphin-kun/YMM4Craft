using System.ComponentModel.DataAnnotations;

namespace YMM4Craft.Models;

public enum SceneSkinModel
{
    [Display(Name = "自動")] Auto = 0,
    [Display(Name = "クラシック（太い腕）")] Wide = 1,
    [Display(Name = "スリム（細い腕）")] Slim = 2,
}
