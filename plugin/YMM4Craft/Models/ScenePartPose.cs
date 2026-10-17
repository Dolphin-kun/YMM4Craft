using YMM4Craft.Controls;
using System.ComponentModel.DataAnnotations;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Controls;
using YukkuriMovieMaker.ItemEditor.CustomVisibilityAttributes;

namespace YMM4Craft.Models;

public sealed class ScenePartPose : Animatable
{
    const string Group = "部位";

    public string Name { get => name; set => Set(ref name, value ?? ""); }
    string name = "";

    [Display(GroupName = Group, Name = "X", Description = "横の軸で回す")]
    [AnimationSlider("F1", "°", -180, 180)]
    public Animation X { get; } = new Animation(0, -Angles.Limit, Angles.Limit);

    [Display(GroupName = Group, Name = "Y", Description = "縦の軸で回す")]
    [AnimationSlider("F1", "°", -180, 180)]
    public Animation Y { get; } = new Animation(0, -Angles.Limit, Angles.Limit);

    [Display(GroupName = Group, Name = "Z", Description = "前後の軸で回す")]
    [AnimationSlider("F1", "°", -180, 180)]
    public Animation Z { get; } = new Animation(0, -Angles.Limit, Angles.Limit);

    [Display(GroupName = Group, Name = "曲げ", Description = "部位の真ん中で前後に曲げる")]
    [AnimationSlider("F1", "°", -150, 150)]
    public Animation Bend { get; } = new Animation(0, -180, 180);

    [Display(GroupName = Group, Name = "曲げ（横）", Description = "部位の真ん中で横に曲げる")]
    [AnimationSlider("F1", "°", -150, 150)]
    public Animation BendSide { get; } = new Animation(0, -180, 180);

    [Display(GroupName = Group, Name = "目標に向ける", Description = "部位の先を目標の位置に向ける。腕と脚は先を、頭は顔を向ける")]
    [ToggleSlider]
    public bool Aim { get => aim; set => Set(ref aim, value); }
    bool aim;

    [Display(GroupName = Group, Name = "目標 X", Description = "モデルの位置から見た目標の位置")]
    [AnimationSlider("F2", "", -8, 8)]
    [ShowPropertyEditorWhen(nameof(Aim), true)]
    public Animation AimX { get; } = new Animation(0, -10000, 10000);

    [Display(GroupName = Group, Name = "目標 Y", Description = "モデルの位置から見た目標の位置")]
    [AnimationSlider("F2", "", -8, 8)]
    [ShowPropertyEditorWhen(nameof(Aim), true)]
    public Animation AimY { get; } = new Animation(1.5, -10000, 10000);

    [Display(GroupName = Group, Name = "目標 Z", Description = "モデルの位置から見た目標の位置")]
    [AnimationSlider("F2", "", -8, 8)]
    [ShowPropertyEditorWhen(nameof(Aim), true)]
    public Animation AimZ { get; } = new Animation(2, -10000, 10000);

    [Newtonsoft.Json.JsonIgnore]
    public string Label => DisplayName(Name);

    public ScenePartPose() { }

    public ScenePartPose(string name) => this.name = name;

    protected override IEnumerable<IAnimatable> GetAnimatables() => [X, Y, Z, Bend, BendSide, AimX, AimY, AimZ];

    internal System.Text.Json.Nodes.JsonObject? ToJson(Func<Animation, double> valueOf, (double X, double Y, double Z) origin)
    {
        var (x, y, z, bend, side) = (valueOf(X), valueOf(Y), valueOf(Z), valueOf(Bend), valueOf(BendSide));
        if (x == 0 && y == 0 && z == 0 && bend == 0 && side == 0 && !Aim) return null;
        var o = new System.Text.Json.Nodes.JsonObject
        {
            ["name"] = Name,
            ["x"] = Angles.Wrap(x),
            ["y"] = Angles.Wrap(y),
            ["z"] = Angles.Wrap(z),
        };
        if (bend != 0) o["bend"] = Math.Clamp(bend, -180, 180);
        if (side != 0) o["bendZ"] = Math.Clamp(side, -180, 180);
        if (Aim)
        {
            o["aim"] = new System.Text.Json.Nodes.JsonObject
            {
                ["x"] = origin.X + valueOf(AimX),
                ["y"] = Math.Clamp(origin.Y + valueOf(AimY), -2048, 4096),
                ["z"] = origin.Z + valueOf(AimZ),
            };
        }
        return o;
    }

    public ScenePartPose Clone()
    {
        var copy = new ScenePartPose(Name);
        copy.X.CopyFrom(X);
        copy.Y.CopyFrom(Y);
        copy.Z.CopyFrom(Z);
        copy.Bend.CopyFrom(Bend);
        copy.BendSide.CopyFrom(BendSide);
        copy.Aim = Aim;
        copy.AimX.CopyFrom(AimX);
        copy.AimY.CopyFrom(AimY);
        copy.AimZ.CopyFrom(AimZ);
        return copy;
    }

    public static string DisplayName(string name) => name switch
    {
        "head" => "頭",
        "hat" => "帽子（頭の外側）",
        "body" => "体",
        "jacket" => "上着（体の外側）",
        "right_arm" => "右腕",
        "left_arm" => "左腕",
        "right_sleeve" => "右袖",
        "left_sleeve" => "左袖",
        "right_leg" => "右脚",
        "left_leg" => "左脚",
        "right_pants" => "右ズボン",
        "left_pants" => "左ズボン",
        "right_front_leg" => "右前脚",
        "left_front_leg" => "左前脚",
        "right_hind_leg" => "右後ろ脚",
        "left_hind_leg" => "左後ろ脚",
        "tail" => "しっぽ",
        "right_wing" => "右の翼",
        "left_wing" => "左の翼",
        "jaw" => "あご",
        "nose" => "鼻",
        "neck" => "首",
        _ => name,
    };
}
