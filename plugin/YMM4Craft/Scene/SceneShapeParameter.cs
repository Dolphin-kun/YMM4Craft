using System.Windows.Media;
using YMM4Craft.Models;
using System.Collections.Immutable;
using System.ComponentModel.DataAnnotations;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Controls;
using Newtonsoft.Json;
using YMM4Craft.Controls;
using YMM4Craft.Core;
using YukkuriMovieMaker.Exo;
using YukkuriMovieMaker.ItemEditor.CustomVisibilityAttributes;
using YukkuriMovieMaker.Player.Video;
using YukkuriMovieMaker.Plugin.Shape;
using YukkuriMovieMaker.Project;

namespace YMM4Craft.Scene;

internal sealed class SceneShapeParameter(SharedDataStore? sharedData) : ShapeParameterBase(sharedData), ICameraPadTarget
{
    [Display(GroupName = "ワールド", Name = "ワールド", Description = "開くワールドやリプレイ。開くのはコピーなので、元のワールドは変わらない")]
    [WorldSelector]
    public string World
    {
        get => world;
        set
        {
            Set(ref world, value ?? "");
            OnPropertyChanged(nameof(IsReplay));
            OnPropertyChanged(nameof(IsWorld));
            OnPropertyChanged(nameof(UsesSceneCamera));
            OnPropertyChanged(nameof(IsPlayerView));
        }
    }
    string world = "";

    [JsonIgnore]
    public bool IsReplay => World.StartsWith(CraftLink.ReplayPrefix, StringComparison.Ordinal);

    [JsonIgnore]
    public bool IsWorld => !IsReplay;

    [Display(GroupName = "ワールド", Name = "時刻", Description = "ゲーム内の時刻")]
    [AnimationSlider("F0", "", 0, 23999)]
    [ShowPropertyEditorWhen(nameof(IsWorld), true)]
    public Animation Time { get; } = new Animation(6000, 0, 23999);

    [Display(GroupName = "ワールド", Name = "天気")]
    [EnumComboBox]
    [ShowPropertyEditorWhen(nameof(IsWorld), true)]
    public SceneWeather Weather { get => weather; set => Set(ref weather, value); }
    SceneWeather weather = SceneWeather.Clear;

    [Display(GroupName = "ワールド", Name = "開始位置", Description = "アイテムの先頭で再生する、リプレイの時刻")]
    [TextBoxSlider("F2", "秒", 0, 300)]
    [Range(0, 604800)]
    [ShowPropertyEditorWhen(nameof(IsReplay), true)]
    public double ReplayStart { get => replayStart; set => Set(ref replayStart, Math.Clamp(value, 0, 604800)); }
    double replayStart;

    [Display(GroupName = "ワールド", Name = "速さ", Description = "リプレイを再生する速さ")]
    [AnimationSlider("F2", "倍", 0, 10)]
    [ShowPropertyEditorWhen(nameof(IsReplay), true)]
    public Animation ReplaySpeed { get; } = new Animation(1, 0, 1000);

    [Display(GroupName = "ワールド", Name = "視点", Description = "リプレイを撮るカメラ")]
    [EnumComboBox]
    [ShowPropertyEditorWhen(nameof(IsReplay), true)]
    public SceneReplayCamera ReplayCamera
    {
        get => replayCamera;
        set
        {
            Set(ref replayCamera, value);
            OnPropertyChanged(nameof(UsesSceneCamera));
            OnPropertyChanged(nameof(IsPlayerView));
        }
    }

    [JsonIgnore]
    public bool UsesSceneCamera => !IsPlayerView;

    [JsonIgnore]
    public bool IsPlayerView => IsReplay && ReplayCamera == SceneReplayCamera.Player;
    SceneReplayCamera replayCamera = SceneReplayCamera.Free;

    [Display(GroupName = "ワールド", Name = "対象", Description = "視点にするプレイヤー")]
    [ReplayPlayerSelector]
    [ShowPropertyEditorWhen(nameof(IsPlayerView), true)]
    public string ReplayPlayer { get => replayPlayer; set => Set(ref replayPlayer, value ?? ""); }
    string replayPlayer = "";

    [Display(GroupName = "ワールド", Name = "手を表示", Description = "手と持っているアイテムを表示する")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(IsPlayerView), true)]
    public bool ReplayShowHand { get => replayShowHand; set => Set(ref replayShowHand, value); }
    bool replayShowHand = true;

    [Display(GroupName = "ワールド", Name = "画面を表示", Description = "開いていたチェストやインベントリなどの画面を表示する")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(IsPlayerView), true)]
    public bool ReplayShowScreens { get => replayShowScreens; set => Set(ref replayShowScreens, value); }
    bool replayShowScreens = true;

    [Display(GroupName = "ワールド", Name = "カーソルを表示", Description = "画面を開いている間のマウスの位置に矢印を描く")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(IsPlayerView), true)]
    public bool ReplayShowCursor { get => replayShowCursor; set => Set(ref replayShowCursor, value); }
    bool replayShowCursor = true;

    [Display(GroupName = "ワールド", Name = "チャットを表示", Description = "録画中に届いたチャットを表示する")]
    [ToggleSlider]
    [ShowPropertyEditorWhen(nameof(IsReplay), true)]
    public bool ReplayShowChat { get => replayShowChat; set => Set(ref replayShowChat, value); }
    bool replayShowChat;

    [Display(GroupName = "ワールド", Name = "HUDを隠す")]
    [ToggleSlider]
    public bool HideHud { get => hideHud; set => Set(ref hideHud, value); }
    bool hideHud = true;

    [Display(GroupName = "ワールド", Name = "音", Description = "リプレイの音を WAV に書き出す")]
    [ReplayAudioExport]
    [ShowPropertyEditorWhen(nameof(IsReplay), true)]
    [JsonIgnore]
    [System.Diagnostics.CodeAnalysis.SuppressMessage("Performance", "CA1822", Justification = "YMM4のプロパティ欄に出すため、インスタンスのプロパティにする")]
    public object? ReplayAudioPlaceholder => null;

    [JsonIgnore]
    public int PreviewFps { get; set; }

    public double[] ReplayTimes(int length, int fps)
    {
        fps = Math.Max(fps, 1);
        var times = new double[length + 1];
        times[0] = ReplayStart;
        for (var f = 0; f < length; f++) times[f + 1] = times[f] + Math.Max(0, ReplaySpeed.GetValue(f, length, fps)) / fps;
        return times;
    }

    public double ReplayTimeAt(long frame, long length, int fps)
    {
        fps = Math.Max(fps, 1);
        if (ReplaySpeed.Values.Count <= 1 || length <= 0 || length > int.MaxValue - 1)
            return ReplayStart + frame * Math.Max(0, ReplaySpeed.GetValue(0, length, fps)) / fps;
        var key = $"{length}|{fps}|{ReplayStart}|{YukkuriMovieMaker.Json.Json.GetJsonText(ReplaySpeed)}";
        var cached = replayTimesCache;
        if (cached is null || cached.Item1 != key)
        {
            cached = Tuple.Create(key, ReplayTimes((int)length, fps));
            replayTimesCache = cached;
        }
        return cached.Item2[Math.Clamp(frame, 0, length)];
    }

    Tuple<string, double[]>? replayTimesCache;

    [Display(GroupName = "カメラ", Name = "操作", Description = "クリックしてから、WASD・Space・Shift・Ctrl とドラッグでカメラを動かす")]
    [CameraPad]
    [ShowPropertyEditorWhen(nameof(UsesSceneCamera), true)]
    [JsonIgnore]
    [System.Diagnostics.CodeAnalysis.SuppressMessage("Performance", "CA1822", Justification = "YMM4のプロパティ欄に出すため、インスタンスのプロパティにする")]
    public object? CameraPadPlaceholder => null;

    [JsonIgnore]
    public (int Frame, int Length) PreviewPosition { get; set; }

    [Display(GroupName = "カメラ", Name = "X")]
    [AnimationSlider("F2", "", -64, 64)]
    [ShowPropertyEditorWhen(nameof(UsesSceneCamera), true)]
    public Animation CameraX { get; } = new Animation(0, -29_999_984, 29_999_984);

    [Display(GroupName = "カメラ", Name = "Y", Description = "目の高さ")]
    [AnimationSlider("F2", "", -64, 128)]
    [ShowPropertyEditorWhen(nameof(UsesSceneCamera), true)]
    public Animation CameraY { get; } = new Animation(100, -128, 1024);

    [Display(GroupName = "カメラ", Name = "Z")]
    [AnimationSlider("F2", "", -64, 64)]
    [ShowPropertyEditorWhen(nameof(UsesSceneCamera), true)]
    public Animation CameraZ { get; } = new Animation(0, -29_999_984, 29_999_984);

    [Display(GroupName = "カメラ", Name = "視点（横）", Description = "左右の向き")]
    [AnimationSlider("F1", "°", -180, 180)]
    [ShowPropertyEditorWhen(nameof(UsesSceneCamera), true)]
    public Animation Yaw { get; } = new Animation(0, -Angles.Limit, Angles.Limit);

    [Display(GroupName = "カメラ", Name = "視点（縦）", Description = "上下の向き")]
    [AnimationSlider("F1", "°", -90, 90)]
    [ShowPropertyEditorWhen(nameof(UsesSceneCamera), true)]
    public Animation Pitch { get; } = new Animation(0, -90, 90);

    [Display(GroupName = "カメラ", Name = "視野角")]
    [AnimationSlider("F0", "°", 30, 110)]
    public Animation Fov { get; } = new Animation(70, 10, 150);

    [Display(GroupName = "モデル", Name = "")]
    [ModelList]
    public ImmutableList<SceneModel> Models
    {
        get => models;
        set
        {
            Set(ref models, value ?? []);
            SubscribeChildUndoRedoable(models);
        }
    }
    ImmutableList<SceneModel> models = [];

    [Display(GroupName = "出力", Name = "解像度", Description = "Minecraft で描画する解像度")]
    [EnumComboBox]
    public SceneResolution Resolution
    {
        get => resolution;
        set
        {
            Set(ref resolution, value);
            OnPropertyChanged(nameof(IsCustomResolution));
        }
    }

    [JsonIgnore]
    public bool IsCustomResolution => Resolution == SceneResolution.Custom;
    SceneResolution resolution = SceneResolution.Project;

    [Display(GroupName = "出力", Name = "幅", Description = "Minecraft で描画する幅")]
    [ShowPropertyEditorWhen(nameof(IsCustomResolution), true)]
    [TextBoxSlider("F0", "px", 0, 1920)]
    [Range(16, 3840)]
    public int CustomWidth { get => customWidth; set => Set(ref customWidth, value); }
    int customWidth = 1280;

    [Display(GroupName = "出力", Name = "高さ", Description = "Minecraft で描画する高さ")]
    [ShowPropertyEditorWhen(nameof(IsCustomResolution), true)]
    [TextBoxSlider("F0", "px", 0, 1080)]
    [Range(16, 2160)]
    public int CustomHeight { get => customHeight; set => Set(ref customHeight, value); }
    int customHeight = 720;

    [Display(GroupName = "出力", Name = "背景", Description = "空や地形などの背景の扱い")]
    [EnumComboBox]
    public SceneBackground Background
    {
        get => background;
        set
        {
            Set(ref background, value);
            OnPropertyChanged(nameof(IsColorBackground));
        }
    }
    SceneBackground background = SceneBackground.World;

    [JsonIgnore]
    public bool IsColorBackground => Background == SceneBackground.Color;

    [Display(GroupName = "出力", Name = "背景の色")]
    [ColorPicker]
    [ShowPropertyEditorWhen(nameof(IsColorBackground), true)]
    public Color BackgroundColor { get => backgroundColor; set => Set(ref backgroundColor, value); }
    Color backgroundColor = Color.FromRgb(0, 255, 0);

    public bool TransparentBackground
    {
        get => false;
        set
        {
            if (value) Background = SceneBackground.Transparent;
        }
    }

    public static bool ShouldSerializeTransparentBackground() => false;

    public SceneShapeParameter() : this(null) { }

    public override IEnumerable<string> CreateMaskExoFilter(int keyFrameIndex, ExoOutputDescription desc, ShapeMaskExoOutputDescription shapeMaskParameters) => [];

    public override IEnumerable<string> CreateShapeItemExoFilter(int keyFrameIndex, ExoOutputDescription desc) => [];

    public override IShapeSource CreateShapeSource(IGraphicsDevicesAndContext devices) => new SceneShapeSource(devices, this);

    protected override IEnumerable<IAnimatable> GetAnimatables() => [Time, ReplaySpeed, CameraX, CameraY, CameraZ, Yaw, Pitch, Fov, .. Models];

    protected override void LoadSharedData(SharedDataStore store)
    {
        store.Load<SharedData>()?.CopyTo(this);
    }

    protected override void SaveSharedData(SharedDataStore store)
    {
        store.Save(new SharedData(this));
    }

    sealed class SharedData
    {
        public string World { get; set; } = "";
        public SceneWeather Weather { get; set; }
        public SceneResolution Resolution { get; set; } = SceneResolution.Project;
        public int CustomWidth { get; set; } = 1280;
        public int CustomHeight { get; set; } = 720;
        public bool HideHud { get; set; } = true;
        public SceneBackground Background { get; set; }
        public Color BackgroundColor { get; set; } = Color.FromRgb(0, 255, 0);
        public double ReplayStart { get; set; }
        public SceneReplayCamera ReplayCamera { get; set; }
        public bool ReplayShowHand { get; set; } = true;
        public bool ReplayShowScreens { get; set; } = true;
        public bool ReplayShowCursor { get; set; } = true;
        public bool ReplayShowChat { get; set; }
        public string ReplayPlayer { get; set; } = "";
        public Animation ReplaySpeed { get; } = new Animation(1, 0, 1000);
        public ImmutableList<SceneModel> Models { get; set; } = [];
        public Animation Time { get; } = new Animation(6000, 0, 23999);
        public Animation CameraX { get; } = new Animation(0, -29_999_984, 29_999_984);
        public Animation CameraY { get; } = new Animation(100, -128, 1024);
        public Animation CameraZ { get; } = new Animation(0, -29_999_984, 29_999_984);
        public Animation Yaw { get; } = new Animation(0, -Angles.Limit, Angles.Limit);
        public Animation Pitch { get; } = new Animation(0, -90, 90);
        public Animation Fov { get; } = new Animation(70, 10, 150);

        public SharedData() { }

        public SharedData(SceneShapeParameter p)
        {
            World = p.World;
            Weather = p.Weather;
            Resolution = p.Resolution;
            CustomWidth = p.CustomWidth;
            CustomHeight = p.CustomHeight;
            HideHud = p.HideHud;
            Background = p.Background;
            BackgroundColor = p.BackgroundColor;
            ReplayStart = p.ReplayStart;
            ReplayCamera = p.ReplayCamera;
            ReplayShowHand = p.ReplayShowHand;
            ReplayShowScreens = p.ReplayShowScreens;
            ReplayShowCursor = p.ReplayShowCursor;
            ReplayShowChat = p.ReplayShowChat;
            ReplayPlayer = p.ReplayPlayer;
            ReplaySpeed.CopyFrom(p.ReplaySpeed);
            Models = p.Models;
            Time.CopyFrom(p.Time);
            CameraX.CopyFrom(p.CameraX);
            CameraY.CopyFrom(p.CameraY);
            CameraZ.CopyFrom(p.CameraZ);
            Yaw.CopyFrom(p.Yaw);
            Pitch.CopyFrom(p.Pitch);
            Fov.CopyFrom(p.Fov);
        }

        public void CopyTo(SceneShapeParameter p)
        {
            p.World = World;
            p.Weather = Weather;
            p.Resolution = Resolution;
            p.CustomWidth = CustomWidth;
            p.CustomHeight = CustomHeight;
            p.HideHud = HideHud;
            p.Background = Background;
            p.BackgroundColor = BackgroundColor;
            p.ReplayStart = ReplayStart;
            p.ReplayCamera = ReplayCamera;
            p.ReplayShowHand = ReplayShowHand;
            p.ReplayShowScreens = ReplayShowScreens;
            p.ReplayShowCursor = ReplayShowCursor;
            p.ReplayShowChat = ReplayShowChat;
            p.ReplayPlayer = ReplayPlayer;
            p.ReplaySpeed.CopyFrom(ReplaySpeed);
            p.Models = Models;
            p.Time.CopyFrom(Time);
            p.CameraX.CopyFrom(CameraX);
            p.CameraY.CopyFrom(CameraY);
            p.CameraZ.CopyFrom(CameraZ);
            p.Yaw.CopyFrom(Yaw);
            p.Pitch.CopyFrom(Pitch);
            p.Fov.CopyFrom(Fov);
        }
    }
}
