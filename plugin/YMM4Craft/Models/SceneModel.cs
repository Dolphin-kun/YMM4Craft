using YMM4Craft.Controls;
using System.Collections.Immutable;
using System.ComponentModel.DataAnnotations;
using Newtonsoft.Json;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Controls;
using YukkuriMovieMaker.ItemEditor.CustomVisibilityAttributes;
using YukkuriMovieMaker.Settings;

namespace YMM4Craft.Models;

public sealed partial class SceneModel : Animatable
{
    const string Group = "モデル";
    internal const string LookGroup = "見た目";
    internal const string ArmorGroup = "防具";
    internal const string PoseGroup = "ポーズ";
    internal const string EffectGroup = "状態";
    internal const string ParentGroup = "親子付け";

    public string Key { get => key; set => Set(ref key, string.IsNullOrEmpty(value) ? NewKey() : value); }
    string key = NewKey();

    public SceneModelKind Kind
    {
        get => kind;
        set
        {
            Set(ref kind, value);
            NotifyVisibility();
        }
    }
    SceneModelKind kind;

    [Display(GroupName = Group, Name = "", Description = "見た目・防具・状態・親子付け・ポーズの欄を出すかどうか。隠しても値はそのまま使う")]
    [ModelGroups]
    [JsonIgnore]
    [System.Diagnostics.CodeAnalysis.SuppressMessage("Performance", "CA1822", Justification = "YMM4のプロパティ欄に出すため、インスタンスのプロパティにする")]
    public object? GroupsPlaceholder => null;

    public bool UseLook { get => useLook ?? (Scale.Values.Any(v => v.Value != 1) || Baby || Skin.Length > 0 || SkinName.Length > 0 || SkinModel != SceneSkinModel.Auto || Nbt.Length > 0); set => SetGroup(ref useLook, value); }
    bool? useLook;

    public bool UseArmor { get => useArmor ?? (Head.Length > 0 || Chest.Length > 0 || Legs.Length > 0 || Feet.Length > 0); set => SetGroup(ref useArmor, value); }
    bool? useArmor;

    public bool UseEffects { get => useEffects ?? (OnFire || Glowing || Invisible || Hurt || Dying || Swell.Values.Any(v => v.Value != 0)); set => SetGroup(ref useEffects, value); }
    bool? useEffects;

    public bool UseParent { get => useParent ?? ParentKey.Length > 0; set => SetGroup(ref useParent, value); }
    bool? useParent;

    public bool UsePose { get => usePose ?? Pose.Count > 0; set => SetGroup(ref usePose, value); }
    bool? usePose;

    void SetGroup(ref bool? field, bool value, [System.Runtime.CompilerServices.CallerMemberName] string name = "")
    {
        field = value;
        OnPropertyChanged(name);
        NotifyVisibility();
    }

    void NotifyVisibility()
    {
        foreach (var name in VisibilityProperties) OnPropertyChanged(name);
    }

    static readonly string[] VisibilityProperties =
    [
        nameof(HasBody), nameof(HasType), nameof(ShowLookBody), nameof(ShowLookMob), nameof(ShowLookPlayer), nameof(ShowSkinFile), nameof(ShowSkinName), nameof(ShowArmor),
        nameof(ShowEffects), nameof(ShowEffectsBody), nameof(ShowEffectsMob), nameof(ShowGlowColor), nameof(ShowParent), nameof(ShowPose),
    ];

    [JsonIgnore] public bool HasBody => Kind is SceneModelKind.Mob or SceneModelKind.Player;
    [JsonIgnore] public bool HasType => Kind != SceneModelKind.Player;
    [JsonIgnore] public bool ShowLookBody => UseLook && (HasBody || Kind == SceneModelKind.Block);
    [JsonIgnore] public bool ShowLookMob => UseLook && Kind == SceneModelKind.Mob;
    [JsonIgnore] public bool ShowLookPlayer => UseLook && Kind == SceneModelKind.Player;
    [JsonIgnore] public bool ShowSkinFile => ShowLookPlayer && SkinSource == SceneSkinSource.File;
    [JsonIgnore] public bool ShowSkinName => ShowLookPlayer && SkinSource == SceneSkinSource.PlayerName;
    [JsonIgnore] public bool ShowArmor => UseArmor && HasBody;
    [JsonIgnore] public bool ShowEffects => UseEffects;
    [JsonIgnore] public bool ShowEffectsBody => UseEffects && HasBody;
    [JsonIgnore] public bool ShowEffectsMob => UseEffects && Kind == SceneModelKind.Mob;
    [JsonIgnore] public bool ShowGlowColor => UseEffects && Glowing;
    [JsonIgnore] public bool ShowParent => UseParent;
    [JsonIgnore] public bool ShowPose => UsePose && HasBody;

    internal IEnumerable<(string Name, string Label)> AvailableGroups =>
        HasBody
            ? [("look", LookGroup), ("armor", ArmorGroup), ("effects", EffectGroup), ("parent", ParentGroup), ("pose", PoseGroup)]
            : Kind == SceneModelKind.Block
            ? [("look", LookGroup), ("effects", EffectGroup), ("parent", ParentGroup)]
            : [("effects", EffectGroup), ("parent", ParentGroup)];

    internal bool IsGroupEnabled(string name) => name switch
    {
        "look" => UseLook,
        "armor" => UseArmor,
        "effects" => UseEffects,
        "parent" => UseParent,
        _ => UsePose,
    };

    internal void SetGroupEnabled(string name, bool enabled)
    {
        switch (name)
        {
            case "look": UseLook = enabled; break;
            case "armor": UseArmor = enabled; break;
            case "effects": UseEffects = enabled; break;
            case "parent": UseParent = enabled; break;
            default: UsePose = enabled; break;
        }
    }

    internal void ChangeParent(string key)
    {
        var wasAttached = ParentKey.Length > 0;
        ParentKey = key;
        if (wasAttached == (key.Length > 0)) return;
        var animations = TransformAnimations;
        if (key.Length > 0)
        {
            DetachedTransform = [.. animations.Select(a => a.Values.Select(v => v.Value).ToArray())];
            foreach (var animation in animations)
                foreach (var v in animation.Values) v.Value = 0;
        }
        else if (DetachedTransform.Count == animations.Length)
        {
            for (var i = 0; i < animations.Length; i++)
            {
                var saved = DetachedTransform[i];
                var values = animations[i].Values;
                for (var j = 0; j < Math.Min(saved.Length, values.Count); j++) values[j].Value = saved[j];
            }
            DetachedTransform = [];
        }
    }

    [Display(GroupName = Group, Name = "表示")]
    [ToggleSlider]
    public bool Visible { get => visible; set => Set(ref visible, value); }
    bool visible = true;

    [Display(GroupName = Group, Name = "種類", Description = "Mob やアイテムの種類。▼で一覧から選べる")]
    [CatalogSelector(CatalogKind.ByModel)]
    [ShowPropertyEditorWhen(nameof(HasType), true)]
    public string Type
    {
        get => type;
        set
        {
            Set(ref type, value ?? "");
            OnPropertyChanged(nameof(Label));
        }
    }
    string type = "";

    [Display(GroupName = Group, Name = "X")]
    [AnimationSlider("F2", "", -16, 16)]
    public Animation X { get; } = new Animation(0, -29_999_984, 29_999_984);

    [Display(GroupName = Group, Name = "Y", Description = "足もとの高さ")]
    [AnimationSlider("F2", "", -64, 128)]
    public Animation Y { get; } = new Animation(64, -128, 512);

    [Display(GroupName = Group, Name = "Z")]
    [AnimationSlider("F2", "", -16, 16)]
    public Animation Z { get; } = new Animation(0, -29_999_984, 29_999_984);

    [Display(GroupName = Group, Name = "向き", Description = "体の向き")]
    [AnimationSlider("F1", "°", -180, 180)]
    public Animation Yaw { get; } = new Animation(0, -Angles.Limit, Angles.Limit);

    [Display(GroupName = Group, Name = "傾き（前後）", Description = "体全体を、中心を軸に前後に傾ける")]
    [AnimationSlider("F1", "°", -90, 90)]
    public Animation Tilt { get; } = new Animation(0, -Angles.Limit, Angles.Limit);

    [Display(GroupName = Group, Name = "傾き（左右）", Description = "体全体を、中心を軸に左右に傾ける")]
    [AnimationSlider("F1", "°", -90, 90)]
    public Animation Roll { get; } = new Animation(0, -Angles.Limit, Angles.Limit);

    [Display(GroupName = Group, Name = "頭（横）", Description = "体から見た頭の左右の向き")]
    [AnimationSlider("F1", "°", -90, 90)]
    [ShowPropertyEditorWhen(nameof(HasBody), true)]
    public Animation HeadYaw { get; } = new Animation(0, -Angles.Limit, Angles.Limit);

    [Display(GroupName = Group, Name = "頭（縦）", Description = "頭の上下の向き")]
    [AnimationSlider("F1", "°", -90, 90)]
    [ShowPropertyEditorWhen(nameof(HasBody), true)]
    public Animation HeadPitch { get; } = new Animation(0, -90, 90);

    [Display(GroupName = Group, Name = "右手", Description = "利き手に持つアイテム")]
    [CatalogSelector(CatalogKind.Item)]
    [ShowPropertyEditorWhen(nameof(HasBody), true)]
    public string MainHand { get => mainHand; set => Set(ref mainHand, value ?? ""); }
    string mainHand = "";

    [Display(GroupName = Group, Name = "左手", Description = "反対の手に持つアイテム")]
    [CatalogSelector(CatalogKind.Item)]
    [ShowPropertyEditorWhen(nameof(HasBody), true)]
    public string OffHand { get => offHand; set => Set(ref offHand, value ?? ""); }
    string offHand = "";

    [Display(GroupName = LookGroup, Name = "大きさ", Description = "モデルの大きさ")]
    [AnimationSlider("F2", "倍", 0.25, 3)]
    [ShowPropertyEditorWhen(nameof(ShowLookBody), true)]
    public Animation Scale { get; } = new Animation(1, 0.0625, 16);

    [Display(GroupName = LookGroup, Name = "子ども", Description = "子どもの姿にする。子どもがいる Mob だけ")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(ShowLookMob), true)]
    public bool Baby { get => baby; set => Set(ref baby, value); }
    bool baby;

    [Display(GroupName = LookGroup, Name = "スキン", Description = "スキンの指定のしかた")]
    [EnumComboBox]
    [ShowPropertyEditorWhen(nameof(ShowLookPlayer), true)]
    public SceneSkinSource SkinSource
    {
        get => skinSource ?? (Skin.Length > 0 ? SceneSkinSource.File : SkinName.Length > 0 ? SceneSkinSource.PlayerName : SceneSkinSource.Default);
        set
        {
            skinSource = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(ShowSkinFile));
            OnPropertyChanged(nameof(ShowSkinName));
        }
    }
    SceneSkinSource? skinSource;

    [Display(GroupName = LookGroup, Name = "画像ファイル", Description = "スキンの画像。64x64 の PNG")]
    [FileSelector(FileGroupType.ImageItem, CustomFilterName = "スキン（PNG）", CustomFilterValue = "*.png")]
    [ShowPropertyEditorWhen(nameof(ShowSkinFile), true)]
    public string Skin { get => skin; set => Set(ref skin, value ?? ""); }
    string skin = "";

    [Display(GroupName = LookGroup, Name = "プレイヤー名", Description = "このプレイヤーのスキンとマントを使う")]
    [TextEditor]
    [ShowPropertyEditorWhen(nameof(ShowSkinName), true)]
    public string SkinName { get => skinName; set => Set(ref skinName, (value ?? "").Trim()); }
    string skinName = "";

    [Display(GroupName = LookGroup, Name = "体型", Description = "腕の太さ")]
    [EnumComboBox]
    [ShowPropertyEditorWhen(nameof(ShowLookPlayer), true)]
    public SceneSkinModel SkinModel { get => skinModel; set => Set(ref skinModel, value); }
    SceneSkinModel skinModel = SceneSkinModel.Auto;

    [Display(GroupName = LookGroup, Name = "NBT", Description = "/summon と同じ形のデータで見た目を変える。例: ネコの柄 {variant:\"minecraft:black\"}、名前 {CustomName:\"名前\",CustomNameVisible:1b}")]
    [TextEditor(AcceptsReturn = true)]
    [ShowPropertyEditorWhen(nameof(ShowLookMob), true)]
    public string Nbt { get => nbt; set => Set(ref nbt, value ?? ""); }
    string nbt = "";

    [Display(GroupName = ArmorGroup, Name = "頭")]
    [CatalogSelector(CatalogKind.Item)]
    [ShowPropertyEditorWhen(nameof(ShowArmor), true)]
    public string Head { get => head; set => Set(ref head, value ?? ""); }
    string head = "";

    [Display(GroupName = ArmorGroup, Name = "胴")]
    [CatalogSelector(CatalogKind.Item)]
    [ShowPropertyEditorWhen(nameof(ShowArmor), true)]
    public string Chest { get => chest; set => Set(ref chest, value ?? ""); }
    string chest = "";

    [Display(GroupName = ArmorGroup, Name = "脚")]
    [CatalogSelector(CatalogKind.Item)]
    [ShowPropertyEditorWhen(nameof(ShowArmor), true)]
    public string Legs { get => legs; set => Set(ref legs, value ?? ""); }
    string legs = "";

    [Display(GroupName = ArmorGroup, Name = "足")]
    [CatalogSelector(CatalogKind.Item)]
    [ShowPropertyEditorWhen(nameof(ShowArmor), true)]
    public string Feet { get => feet; set => Set(ref feet, value ?? ""); }
    string feet = "";

    [Display(GroupName = EffectGroup, Name = "燃える", Description = "炎をまとう")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(ShowEffects), true)]
    public bool OnFire { get => onFire; set => Set(ref onFire, value); }
    bool onFire;

    [Display(GroupName = EffectGroup, Name = "光る", Description = "輪郭を光らせる")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(ShowEffects), true)]
    public bool Glowing
    {
        get => glowing;
        set
        {
            Set(ref glowing, value);
            OnPropertyChanged(nameof(ShowGlowColor));
        }
    }
    bool glowing;

    [Display(GroupName = EffectGroup, Name = "光の色", Description = "光る輪郭の色")]
    [ColorPicker]
    [ShowPropertyEditorWhen(nameof(ShowGlowColor), true)]
    public System.Windows.Media.Color GlowColor { get => glowColor; set => Set(ref glowColor, value); }
    System.Windows.Media.Color glowColor = System.Windows.Media.Colors.White;

    [Display(GroupName = EffectGroup, Name = "半透明", Description = "体を半透明にする")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(ShowEffects), true)]
    public bool Invisible { get => invisible; set => Set(ref invisible, value); }
    bool invisible;

    [Display(GroupName = EffectGroup, Name = "ダメージ", Description = "ダメージを受けたときのように赤くする")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(ShowEffectsBody), true)]
    public bool Hurt { get => hurt; set => Set(ref hurt, value); }
    bool hurt;

    [Display(GroupName = EffectGroup, Name = "倒れる", Description = "死んだときのように横に倒れる")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(ShowEffectsBody), true)]
    public bool Dying { get => dying; set => Set(ref dying, value); }
    bool dying;

    [Display(GroupName = EffectGroup, Name = "膨らみ", Description = "クリーパーが爆発する直前の膨らみと点滅")]
    [AnimationSlider("F2", "", 0, 1)]
    [ShowPropertyEditorWhen(nameof(ShowEffectsMob), true)]
    public Animation Swell { get; } = new Animation(0, 0, 1);

    [Display(GroupName = ParentGroup, Name = "親", Description = "このモデルを付ける先のモデル")]
    [ParentSelector]
    [ShowPropertyEditorWhen(nameof(ShowParent), true)]
    public string ParentKey { get => parentKey; set => Set(ref parentKey, value ?? ""); }
    string parentKey = "";

    [Display(GroupName = ParentGroup, Name = "部位", Description = "付ける先の部位")]
    [ParentPartSelector]
    [ShowPropertyEditorWhen(nameof(ShowParent), true)]
    public string ParentPart { get => parentPart; set => Set(ref parentPart, value ?? ""); }
    string parentPart = "";

    public List<double[]> DetachedTransform { get; set; } = [];

    internal Animation[] TransformAnimations => [X, Y, Z, Yaw, Tilt, Roll];

    [JsonIgnore]
    internal ImmutableList<SceneModel> Siblings { get; set; } = [];

    [JsonIgnore]
    internal SceneModel? ParentModel => ParentKey.Length == 0 ? null : Siblings.FirstOrDefault(m => m.Key == ParentKey && !ReferenceEquals(m, this));

    [Display(GroupName = PoseGroup, Name = "歩く", Description = "動いた距離に合わせて手足を振る")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(ShowPose), true)]
    public bool AutoWalk { get => autoWalk ?? false; set => Set(ref autoWalk, value); }
    bool? autoWalk;

    [Display(GroupName = PoseGroup, Name = "歩幅", Description = "手足を1回振る間に進む距離")]
    [TextBoxSlider("F2", "倍", 0.5, 2)]
    [System.ComponentModel.DataAnnotations.Range(0.1, 5)]
    [ShowPropertyEditorWhen(nameof(ShowPose), true)]
    public double Stride { get => stride; set => Set(ref stride, Math.Clamp(value, 0.1, 5)); }
    double stride = 1;

    [Display(GroupName = PoseGroup, Name = "")]
    [PartPoseList]
    [ShowPropertyEditorWhen(nameof(ShowPose), true)]
    public ImmutableList<ScenePartPose> Pose
    {
        get => pose;
        set
        {
            Set(ref pose, value ?? []);
            SubscribeChildUndoRedoable(pose);
        }
    }
    ImmutableList<ScenePartPose> pose = [];

    public SceneModel() { }

    public SceneModel(SceneModelKind kind, string type)
    {
        this.kind = kind;
        this.type = type;
        useLook = useArmor = useEffects = useParent = false;
        autoWalk = kind is SceneModelKind.Mob or SceneModelKind.Player;
        usePose = kind == SceneModelKind.Player;
        if (kind == SceneModelKind.Player)
            Pose = [.. DefaultPoseParts.Select(n => new ScenePartPose(n))];
    }

    public SceneModel Clone()
    {
        var copy = new SceneModel(Kind, Type)
        {
            UseLook = UseLook,
            UseArmor = UseArmor,
            UseEffects = UseEffects,
            UseParent = UseParent,
            UsePose = UsePose,
            Visible = Visible,
            AutoWalk = AutoWalk,
            Stride = Stride,
            MainHand = MainHand,
            OffHand = OffHand,
            Baby = Baby,
            SkinSource = SkinSource,
            Skin = Skin,
            SkinModel = SkinModel,
            Nbt = Nbt,
            OnFire = OnFire,
            Glowing = Glowing,
            GlowColor = GlowColor,
            Invisible = Invisible,
            Hurt = Hurt,
            Dying = Dying,
            DetachedTransform = [.. DetachedTransform.Select(v => (double[])v.Clone())],
            SkinName = SkinName,
            ParentKey = ParentKey,
            ParentPart = ParentPart,
            Head = Head,
            Chest = Chest,
            Legs = Legs,
            Feet = Feet,
        };
        foreach (var (to, from) in new[] { (copy.X, X), (copy.Y, Y), (copy.Z, Z), (copy.Yaw, Yaw), (copy.Tilt, Tilt), (copy.Roll, Roll), (copy.HeadYaw, HeadYaw), (copy.HeadPitch, HeadPitch), (copy.Scale, Scale), (copy.Swell, Swell) })
            to.CopyFrom(from);
        copy.Pose = [.. Pose.Select(p => p.Clone())];
        return copy;
    }

    [JsonIgnore]
    public string Label => Kind switch
    {
        SceneModelKind.Player => "プレイヤー",
        _ when Type.Length == 0 => Kind switch { SceneModelKind.Item => "アイテム（未指定）", SceneModelKind.Block => "ブロック（未指定）", _ => "Mob（未指定）" },
        _ => CatalogNames.NameOf(CatalogNames.KindOf(Kind), Type),
    };

    [JsonIgnore]
    internal (int Frame, int Length) PreviewPosition { get; set; }

    [JsonIgnore]
    internal int PreviewFps { get; set; } = 30;

    static readonly string[] DefaultPoseParts = ["right_arm", "left_arm", "right_leg", "left_leg", "body"];

    public void RefreshLabel() => OnPropertyChanged(nameof(Label));

    protected override IEnumerable<IAnimatable> GetAnimatables() => [X, Y, Z, Yaw, Tilt, Roll, HeadYaw, HeadPitch, Scale, Swell, .. Pose];

    static string NewKey() => Guid.NewGuid().ToString("N");

    (double Pos, double Speed) WalkAt(long frame, long length, int fps)
    {
        const int window = 3;
        double distance = 0, recent = 0;
        double px = X.GetValue(0, length, fps), pz = Z.GetValue(0, length, fps);
        for (long f = 1; f <= frame; f++)
        {
            double x = X.GetValue(f, length, fps), z = Z.GetValue(f, length, fps);
            var d = Math.Sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
            distance += d;
            if (f > frame - window) recent += d;
            (px, pz) = (x, z);
        }
        var perSecond = frame <= 0 ? 0 : recent / Math.Min(window, frame) * Math.Max(fps, 1);
        var speed = Math.Clamp(perSecond / 20 * 4, 0, 1);
        return (distance * 4 / Stride, speed);
    }

    internal System.Text.Json.Nodes.JsonObject? ToJson(long frame, long length, int fps, Func<string, string?> skinOf)
    {
        if (!Visible) return null;
        var parent = ParentModel;
        if (ParentKey.Length > 0 && (parent is null || !parent.Visible)) return null;
        if (Kind != SceneModelKind.Player && CatalogNames.ToId(Type) is not { } typeId) return null;
        var o = new System.Text.Json.Nodes.JsonObject
        {
            ["id"] = Key,
            ["kind"] = Kind switch { SceneModelKind.Item => "item", SceneModelKind.Player => "player", SceneModelKind.Block => "block", _ => "mob" },
            ["x"] = X.GetValue(frame, length, fps),
            ["y"] = Math.Clamp(Y.GetValue(frame, length, fps), -2048, 4096),
            ["z"] = Z.GetValue(frame, length, fps),
        };
        if (Kind != SceneModelKind.Player) o["type"] = CatalogNames.ToId(Type);
        o["yaw"] = Angles.Wrap(Yaw.GetValue(frame, length, fps));
        o["tilt"] = Angles.Wrap(Tilt.GetValue(frame, length, fps));
        o["roll"] = Angles.Wrap(Roll.GetValue(frame, length, fps));
        if (HasBody)
        {
            o["headYaw"] = Angles.Wrap(HeadYaw.GetValue(frame, length, fps));
            o["headPitch"] = Math.Clamp(HeadPitch.GetValue(frame, length, fps), -90, 90);
            if (CatalogNames.ToId(MainHand) is { } main) o["mainHand"] = main;
            if (CatalogNames.ToId(OffHand) is { } off) o["offHand"] = off;
        }

        if (Kind == SceneModelKind.Block) o["scale"] = Math.Clamp(Scale.GetValue(frame, length, fps), 0.0625, 16);
        if (HasBody)
        {
            o["scale"] = Math.Clamp(Scale.GetValue(frame, length, fps), 0.0625, 16);
            if (Kind == SceneModelKind.Mob)
            {
                if (Baby) o["baby"] = true;
                if (Nbt.Trim().Length > 0) o["nbt"] = Nbt.Trim();
            }
            if (Kind == SceneModelKind.Player)
            {
                if (SkinSource == SceneSkinSource.File && Skin.Length > 0 && skinOf(Skin) is { } skinId) o["skin"] = skinId;
                else if (SkinSource == SceneSkinSource.PlayerName && PlayerNamePattern().IsMatch(SkinName)) o["skinName"] = SkinName;
                o["skinModel"] = SkinModel switch { SceneSkinModel.Wide => "wide", SceneSkinModel.Slim => "slim", _ => "auto" };
            }
        }
        if (HasBody)
        {
            if (CatalogNames.ToId(Head) is { } h) o["head"] = h;
            if (CatalogNames.ToId(Chest) is { } c) o["chest"] = c;
            if (CatalogNames.ToId(Legs) is { } l) o["legs"] = l;
            if (CatalogNames.ToId(Feet) is { } f) o["feet"] = f;
        }
        if (HasBody && AutoWalk)
        {
            var (walkPos, walkSpeed) = WalkAt(frame, length, fps);
            o["walkPos"] = walkPos;
            o["walkSpeed"] = walkSpeed;
        }
        if (HasBody)
        {
            var parts = new System.Text.Json.Nodes.JsonArray();
            var origin = ((double)o["x"]!, (double)o["y"]!, (double)o["z"]!);
            foreach (var p in Pose)
            {
                if (p.ToJson(a => a.GetValue(frame, length, fps), origin) is { } part) parts.Add(part);
            }
            if (parts.Count > 0) o["pose"] = parts;
        }
        {
            if (OnFire) o["fire"] = true;
            if (Glowing) o["glowColor"] = (GlowColor.R << 16) | (GlowColor.G << 8) | GlowColor.B;
            if (Invisible) o["invisible"] = true;
            if (HasBody)
            {
                if (Hurt) o["hurt"] = true;
                if (Dying) o["dying"] = true;
            }
            if (Kind == SceneModelKind.Mob)
            {
                var swell = Math.Clamp(Swell.GetValue(frame, length, fps), 0, 1);
                if (swell > 0) o["swell"] = swell;
            }
        }
        if (parent is not null)
        {
            o["parent"] = parent.Key;
            if (ParentPart.Length > 0) o["parentPart"] = ParentPart;
        }
        return o;
    }

    [System.Text.RegularExpressions.GeneratedRegex("^[A-Za-z0-9_]{3,16}$")]
    private static partial System.Text.RegularExpressions.Regex PlayerNamePattern();
}
