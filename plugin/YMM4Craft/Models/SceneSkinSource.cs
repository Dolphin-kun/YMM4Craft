using System.ComponentModel.DataAnnotations;

namespace YMM4Craft.Models;

public enum SceneSkinSource
{
    [Display(Name = "標準")] Default = 0,
    [Display(Name = "プレイヤー名")] PlayerName = 1,
    [Display(Name = "画像ファイル")] File = 2,
}
