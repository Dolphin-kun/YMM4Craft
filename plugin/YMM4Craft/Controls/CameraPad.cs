using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Resources.Icons;

namespace YMM4Craft.Controls;

internal sealed partial class CameraPad : UserControl, IPropertyEditorControl
{
    const double DegreesPerPixel = 0.3;
    const double EyeHeight = 1.62;
    const double HomeDistance = 4;
    static readonly double[] Speeds = [0.25, 0.5, 1, 2, 4, 8];
    const double HorizontalSpeed = 10.92;
    const double VerticalSpeed = 7.5;
    static readonly double HorizontalDamping = -Math.Log(0.91) * 20;
    static readonly double VerticalDamping = -Math.Log(0.6) * 20;
    const double SprintFactor = 2;
    const double StopSpeed = 0.05;
    static readonly Key[] ControlKeys = [Key.W, Key.A, Key.S, Key.D, Key.Space, Key.LeftShift, Key.RightShift, Key.LeftCtrl, Key.RightCtrl, Key.Escape];

    public event EventHandler? BeginEdit;
    public event EventHandler? EndEdit;

    public ICameraPadTarget? Target { get; set; }

    readonly Border pad;
    readonly TextBlock hint;
    readonly TextBlock position;
    readonly DispatcherTimer positionTimer;
    int speedIndex = 2;
    double velocityX, velocityY, velocityZ;
    bool active;
    bool editing;
    bool dragging;
    Point anchor;
    Point anchorOnScreen;
    bool sprinting;
    bool shownLoading;
    TimeSpan lastRender;

    public CameraPad()
    {
        hint = new TextBlock
        {
            TextWrapping = TextWrapping.Wrap,
            TextAlignment = TextAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            IsHitTestVisible = false,
        };
        pad = new Border
        {
            Height = 140,
            BorderThickness = new Thickness(1),
            Child = hint,
            Cursor = Cursors.SizeAll,
        };
        position = new TextBlock { VerticalAlignment = VerticalAlignment.Center, Opacity = 0.8, Margin = new Thickness(6, 0, 4, 0) };
        var groundButton = IconButtons.Create(GeometryKeys.ElevatorUp, "地上へ");
        groundButton.Click += async (_, _) => await MoveToGroundAsync();
        var homeButton = IconButtons.Create(GeometryKeys.AccountArrowRight, "プレイヤーの所へ");
        homeButton.Click += async (_, _) => await MoveToHomeAsync();
        var toolBar = new ToolBar();
        toolBar.Items.Add(groundButton);
        toolBar.Items.Add(homeButton);
        toolBar.Items.Add(new Separator());
        toolBar.Items.Add(position);
        var tray = new ToolBarTray { IsLocked = false };
        tray.ToolBars.Add(toolBar);
        var panel = new StackPanel();
        panel.Children.Add(tray);
        panel.Children.Add(pad);
        Content = panel;
        UpdateLook();

        positionTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(200) };
        positionTimer.Tick += (_, _) => ShowPosition();
        Loaded += (_, _) => { ShowPosition(); positionTimer.Start(); };

        pad.MouseLeftButtonDown += OnPadMouseDown;
        pad.MouseMove += OnPadMouseMove;
        pad.MouseLeftButtonUp += (_, e) =>
        {
            StopDragging();
            e.Handled = true;
        };
        pad.LostMouseCapture += (_, _) =>
        {
            dragging = false;
            Mouse.OverrideCursor = null;
        };
        pad.MouseWheel += (_, e) =>
        {
            if (!active) return;
            speedIndex = Math.Clamp(speedIndex + Math.Sign(e.Delta), 0, Speeds.Length - 1);
            UpdateLook();
            e.Handled = true;
        };
        Unloaded += (_, _) =>
        {
            positionTimer.Stop();
            Deactivate();
        };
    }

    void OnPadMouseDown(object sender, MouseButtonEventArgs e)
    {
        Activate();
        dragging = true;
        anchor = e.GetPosition(pad);
        anchorOnScreen = pad.PointToScreen(anchor);
        pad.CaptureMouse();
        Mouse.OverrideCursor = Cursors.None;
        e.Handled = true;
    }

    static bool Loading => Core.CraftLink.Shared.IsLoading;

    void OnPadMouseMove(object sender, MouseEventArgs e)
    {
        if (!dragging || Target is not { } t || Loading) return;
        var p = e.GetPosition(pad);
        var dx = p.X - anchor.X;
        var dy = p.Y - anchor.Y;
        if (dx == 0 && dy == 0) return;
        SetCursorPos((int)Math.Round(anchorOnScreen.X), (int)Math.Round(anchorOnScreen.Y));

        StartEditIfNeeded();
        var index = EditIndex(t);
        SetValue(t.Yaw, index, Math.Clamp(GetValue(t.Yaw, index) + dx * DegreesPerPixel, -Angles.Limit, Angles.Limit));
        SetValue(t.Pitch, index, Math.Clamp(GetValue(t.Pitch, index) + dy * DegreesPerPixel, -90, 90));
    }

    void StopDragging()
    {
        if (!dragging) return;
        dragging = false;
        Mouse.OverrideCursor = null;
        pad.ReleaseMouseCapture();
    }

    void Activate()
    {
        if (active) return;
        active = true;
        lastRender = default;
        InputManager.Current.PreProcessInput += OnPreProcessInput;
        CompositionTarget.Rendering += OnRendering;
        if (Application.Current is { } app) app.Deactivated += OnAppDeactivated;
        UpdateLook();
    }

    void Deactivate()
    {
        if (!active) return;
        active = false;
        velocityX = velocityY = velocityZ = 0;
        sprinting = false;
        InputManager.Current.PreProcessInput -= OnPreProcessInput;
        CompositionTarget.Rendering -= OnRendering;
        if (Application.Current is { } app) app.Deactivated -= OnAppDeactivated;
        StopDragging();
        FinishEdit();
        UpdateLook();
    }

    void OnAppDeactivated(object? sender, EventArgs e) => Deactivate();

    void OnPreProcessInput(object sender, PreProcessInputEventArgs e)
    {
        var input = e.StagingItem.Input;
        if (input is KeyEventArgs key && (input.RoutedEvent == Keyboard.PreviewKeyDownEvent || input.RoutedEvent == Keyboard.PreviewKeyUpEvent))
        {
            var k = key.Key == Key.System ? key.SystemKey : key.Key;
            if (!ControlKeys.Contains(k)) return;
            if (k == Key.Escape && key.IsDown)
            {
                Dispatcher.BeginInvoke(Deactivate);
            }
            e.Cancel();
            return;
        }
        if (input is MouseButtonEventArgs mouse && input.RoutedEvent == Mouse.PreviewMouseDownEvent)
        {
            var p = mouse.GetPosition(pad);
            if (p.X < 0 || p.Y < 0 || p.X > pad.ActualWidth || p.Y > pad.ActualHeight)
                Dispatcher.BeginInvoke(Deactivate);
        }
    }

    void UpdateLook()
    {
        pad.Background = new SolidColorBrush(active ? Color.FromArgb(0x40, 0x40, 0xA0, 0xFF) : Color.FromArgb(0x20, 0x80, 0x80, 0x80));
        pad.BorderBrush = new SolidColorBrush(active ? Color.FromRgb(0x40, 0xA0, 0xFF) : Color.FromArgb(0x80, 0x80, 0x80, 0x80));
        pad.BorderThickness = new Thickness(active ? 2 : 1);
        hint.Text = shownLoading
            ? Core.CraftLink.Shared.IsExportingAudio ? "音を書き出しています。終わるまでカメラは動かせません" : "読み込み中です。終わるまでカメラは動かせません"
            : active
            ? $"操作中（速さ ×{Speeds[speedIndex]}、約 {HorizontalSpeed * Speeds[speedIndex]:0.#} ブロック/秒）\nWASD 移動 / Space 上 / Shift 下 / Ctrl ダッシュ\nドラッグ 視点 / ホイール 速さ / Esc・外をクリックでやめる"
            : "クリックしてカメラを操作\nWASD 移動 / Space 上 / Shift 下 / Ctrl ダッシュ / ドラッグで視点";
    }

    void OnRendering(object? sender, EventArgs e)
    {
        var now = ((RenderingEventArgs)e).RenderingTime;
        var dt = lastRender == default ? 0 : Math.Min((now - lastRender).TotalSeconds, 0.1);
        lastRender = now;
        if (!active || dt <= 0 || Target is not { } t) return;
        if (Loading)
        {
            velocityX = velocityY = velocityZ = 0;
            return;
        }

        double forward = 0, right = 0, up = 0;
        if (Keyboard.IsKeyDown(Key.W)) forward += 1;
        if (Keyboard.IsKeyDown(Key.S)) forward -= 1;
        if (Keyboard.IsKeyDown(Key.D)) right += 1;
        if (Keyboard.IsKeyDown(Key.A)) right -= 1;
        if (Keyboard.IsKeyDown(Key.Space)) up += 1;
        if (Keyboard.IsKeyDown(Key.LeftShift) || Keyboard.IsKeyDown(Key.RightShift)) up -= 1;

        var moving = forward != 0 || right != 0 || up != 0;
        if (!moving) sprinting = false;
        else if (Keyboard.IsKeyDown(Key.LeftCtrl) || Keyboard.IsKeyDown(Key.RightCtrl)) sprinting = true;
        var scale = Speeds[speedIndex] * (sprinting ? SprintFactor : 1);
        var index = EditIndex(t);
        var yaw = GetValue(t.Yaw, index) * Math.PI / 180;
        var (fx, fz) = (-Math.Sin(yaw), Math.Cos(yaw));
        var (rx, rz) = (-Math.Cos(yaw), -Math.Sin(yaw));

        double targetX = 0, targetZ = 0;
        var length = Math.Sqrt(forward * forward + right * right);
        if (length > 0)
        {
            var k = HorizontalSpeed * scale / length;
            targetX = (fx * forward + rx * right) * k;
            targetZ = (fz * forward + rz * right) * k;
        }
        var targetY = up * VerticalSpeed * scale;
        var h = 1 - Math.Exp(-HorizontalDamping * dt);
        var v = 1 - Math.Exp(-VerticalDamping * dt);
        velocityX += (targetX - velocityX) * h;
        velocityZ += (targetZ - velocityZ) * h;
        velocityY += (targetY - velocityY) * v;

        var stopSpeed = StopSpeed * Math.Max(scale, 1);
        if (forward == 0 && right == 0 && up == 0
            && Math.Sqrt(velocityX * velocityX + velocityZ * velocityZ) < stopSpeed && Math.Abs(velocityY) < stopSpeed)
        {
            velocityX = velocityY = velocityZ = 0;
            if (!dragging) FinishEdit();
            return;
        }

        StartEditIfNeeded();
        if (velocityX != 0) SetValue(t.CameraX, index, GetValue(t.CameraX, index) + velocityX * dt);
        if (velocityZ != 0) SetValue(t.CameraZ, index, GetValue(t.CameraZ, index) + velocityZ * dt);
        if (velocityY != 0) SetValue(t.CameraY, index, GetValue(t.CameraY, index) + velocityY * dt);
    }

    void ShowPosition()
    {
        if (Loading != shownLoading)
        {
            shownLoading = !shownLoading;
            UpdateLook();
        }
        if (Target is not { } t) return;
        var index = EditIndex(t);
        position.Text = $"X {GetValue(t.CameraX, index):0.0}  Y {GetValue(t.CameraY, index):0.0}  Z {GetValue(t.CameraZ, index):0.0}  向き {Angles.Wrap(GetValue(t.Yaw, index)):0}°";
    }

    async Task MoveToGroundAsync()
    {
        if (Target is not { } t || !Core.CraftLink.Shared.IsConnected) return;
        var index = EditIndex(t);
        try
        {
            if (await Core.CraftLink.Shared.GetGroundAsync(GetValue(t.CameraX, index), GetValue(t.CameraZ, index)) is not { } ground) return;
            Apply(() => SetValue(t.CameraY, index, ground + EyeHeight));
        }
        catch (Exception ex) when (ex is InvalidOperationException or System.IO.IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
        }
    }

    async Task MoveToHomeAsync()
    {
        if (Target is not { } t || !Core.CraftLink.Shared.IsConnected) return;
        var index = EditIndex(t);
        try
        {
            if (await Core.CraftLink.Shared.GetHomeAsync() is not { } home) return;
            var view = home.View;
            var yaw = view.Yaw * Math.PI / 180;
            Apply(() =>
            {
                SetValue(t.CameraX, index, Math.Round(view.X + Math.Sin(yaw) * HomeDistance, 2));
                SetValue(t.CameraY, index, Math.Round(view.Y + 1, 2));
                SetValue(t.CameraZ, index, Math.Round(view.Z - Math.Cos(yaw) * HomeDistance, 2));
                SetValue(t.Yaw, index, Math.Round(view.Yaw, 1));
                SetValue(t.Pitch, index, 10);
            });
        }
        catch (Exception ex) when (ex is InvalidOperationException or System.IO.IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
        }
    }

    void Apply(Action change)
    {
        BeginEdit?.Invoke(this, EventArgs.Empty);
        change();
        EndEdit?.Invoke(this, EventArgs.Empty);
        ShowPosition();
    }

    void StartEditIfNeeded()
    {
        if (editing) return;
        editing = true;
        BeginEdit?.Invoke(this, EventArgs.Empty);
    }

    void FinishEdit()
    {
        if (!editing) return;
        editing = false;
        EndEdit?.Invoke(this, EventArgs.Empty);
    }

    static int EditIndex(ICameraPadTarget t) => KeyframeValues.Index(t.CameraX, t.PreviewPosition);

    static double GetValue(Animation animation, int index) => KeyframeValues.Get(animation, index);

    static void SetValue(Animation animation, int index, double value) => KeyframeValues.Set(animation, index, value);

    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool SetCursorPos(int x, int y);
}
