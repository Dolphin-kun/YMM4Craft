using System.ComponentModel.DataAnnotations;

namespace YMM4Craft.Scene;

public enum SceneWeather
{
    [Display(Name = "晴れ")] Clear = 0,
    [Display(Name = "雨")] Rain = 1,
    [Display(Name = "雷雨")] Thunder = 2,
}
