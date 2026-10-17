using YMM4Craft.Models;
using System.IO;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using YMM4Craft.Controls;
using YMM4Craft.Core;
using YMM4Craft.Gpu;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.PoseEditor;

internal sealed class PoseEditorWindow : Window
{
    const double FallbackDegreesPerPixel = 0.5;
    const double RingGrabDistance = 8;
    static readonly Brush[] RingBrushes = [Brushes.Red, Brushes.LimeGreen, Brushes.DodgerBlue];

    readonly SceneModel model;
    readonly IPoseEditHost host;
    readonly CraftLink link = CraftLink.Shared;
    readonly Image image;
    readonly Canvas overlay;
    readonly Polyline[] rings = new Polyline[3];
    readonly Ellipse pivotMark;
    readonly TextBlock status;
    readonly ListBox parts;
    readonly TextBlock partTitle;
    readonly TextBlock[] valueTexts = new TextBlock[5];
    readonly Button resetButton;
    WriteableBitmap? bitmap;
    readonly CancellationTokenSource closing = new();
    readonly GpuFrameReader gpuReader = new();
    readonly DisposeCollector disposer = new();
    long shownSequence = -1;
    bool poseChanged;
    readonly SemaphoreSlim sendSignal = new(0);

    double viewYaw;
    double viewPitch = 10;
    double viewDistance = 3;
    bool viewChanged = true;

    string? selectedPart;
    bool refreshing;

    sealed record Gizmo(Point[][] Rings, Vector[] Drag, Point? Pivot, Point? Tip);
    Gizmo? gizmo;

    enum Drag { None, Axis, Free, Orbit }
    Drag drag;
    int dragAxis;
    int hoveredAxis = -1;
    Point lastMouse;

    public PoseEditorWindow(SceneModel model, IPoseEditHost host)
    {
        this.model = model;
        this.host = host;
        Title = $"ポーズエディタ - {model.Label}";
        Width = 1000;
        Height = 620;
        WindowStartupLocation = WindowStartupLocation.CenterOwner;
        viewYaw = KeyframeValues.Get(model.Yaw, 0) + 180;
        selectedPart = host.SelectedPart;

        image = new Image { Stretch = Stretch.Uniform, Cursor = Cursors.Cross };
        RenderOptions.SetBitmapScalingMode(image, BitmapScalingMode.HighQuality);
        overlay = new Canvas { IsHitTestVisible = false, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        overlay.SetBinding(WidthProperty, new System.Windows.Data.Binding(nameof(ActualWidth)) { Source = image });
        overlay.SetBinding(HeightProperty, new System.Windows.Data.Binding(nameof(ActualHeight)) { Source = image });
        for (var i = 0; i < 3; i++)
        {
            rings[i] = new Polyline { Stroke = RingBrushes[i], StrokeThickness = 2, Visibility = Visibility.Collapsed };
            overlay.Children.Add(rings[i]);
        }
        pivotMark = new Ellipse { Width = 8, Height = 8, Fill = Brushes.White, Stroke = Brushes.Black, Visibility = Visibility.Collapsed };
        overlay.Children.Add(pivotMark);

        var imageArea = new Grid { Background = Brushes.Black, ClipToBounds = true };
        imageArea.Children.Add(image);
        imageArea.Children.Add(overlay);
        image.MouseLeftButtonDown += OnImageMouseDown;
        image.MouseRightButtonDown += OnImageMouseDown;
        image.MouseMove += OnImageMouseMove;
        image.MouseLeftButtonUp += OnImageMouseUp;
        image.MouseRightButtonUp += OnImageMouseUp;
        image.LostMouseCapture += (_, _) => EndDrag();
        imageArea.MouseWheel += (_, e) =>
        {
            viewDistance = Math.Clamp(viewDistance * (e.Delta > 0 ? 0.9 : 1.1), 0.5, 64);
            ChangeView();
        };

        parts = new ListBox();
        parts.SelectionChanged += (_, _) =>
        {
            if (!refreshing && parts.SelectedItem is ListBoxItem { Tag: string name }) Select(name, notifyHost: true);
        };

        partTitle = new TextBlock { FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 6, 0, 2), TextTrimming = TextTrimming.CharacterEllipsis };
        var valueGrid = new Grid();
        valueGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        valueGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        string[] labels = ["X", "Y", "Z", "曲げ", "曲げ（横）"];
        for (var i = 0; i < labels.Length; i++)
        {
            valueGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var label = new TextBlock { Text = labels[i], Opacity = 0.7, Margin = new Thickness(0, 2, 12, 2) };
            valueTexts[i] = new TextBlock { TextAlignment = TextAlignment.Right, Margin = new Thickness(0, 2, 0, 2) };
            Grid.SetRow(label, i);
            Grid.SetRow(valueTexts[i], i);
            Grid.SetColumn(valueTexts[i], 1);
            valueGrid.Children.Add(label);
            valueGrid.Children.Add(valueTexts[i]);
        }

        resetButton = new Button { Content = "元に戻す", ToolTip = "選んだ部位の回転と曲げを 0 に戻す", Height = IconButtons.Size };
        resetButton.Click += (_, _) => ResetSelected();
        var front = new Button { Content = "正面から見る", Height = IconButtons.Size };
        front.Click += (_, _) =>
        {
            viewYaw = KeyframeValues.Get(model.Yaw, 0) + 180;
            viewPitch = 10;
            ChangeView();
        };
        var buttonRow = new System.Windows.Controls.Primitives.UniformGrid { Columns = 2, Margin = new Thickness(0, 6, 0, 0) };
        buttonRow.Children.Add(resetButton);
        buttonRow.Children.Add(front);

        status = new TextBlock { Opacity = 0.7, FontSize = 11, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 4, 0, 0) };
        status.SetBinding(VisibilityProperty, new System.Windows.Data.Binding(nameof(TextBlock.Text))
        {
            Source = status,
            Converter = new EmptyToCollapsedConverter(),
        });

        var sideTop = new StackPanel();
        sideTop.Children.Add(new TextBlock { Text = "部位", FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 0, 0, 4) });
        var sideBottom = new StackPanel();
        sideBottom.Children.Add(partTitle);
        sideBottom.Children.Add(valueGrid);
        sideBottom.Children.Add(buttonRow);
        sideBottom.Children.Add(status);

        var side = new DockPanel { Width = 180 };
        DockPanel.SetDock(sideTop, Dock.Top);
        DockPanel.SetDock(sideBottom, Dock.Bottom);
        side.Children.Add(sideTop);
        side.Children.Add(sideBottom);
        side.Children.Add(parts);

        var sideFrame = new Border { Child = side, Padding = new Thickness(4) };
        sideFrame.SetResourceReference(Border.BackgroundProperty, SystemColors.ControlBrushKey);
        var root = new DockPanel();
        DockPanel.SetDock(sideFrame, Dock.Right);
        root.Children.Add(sideFrame);
        root.Children.Add(imageArea);
        Content = root;

        host.SelectedPartChanged += OnHostSelectedPartChanged;
        PreviewKeyDown += OnPreviewKeyDown;
        Loaded += async (_, _) =>
        {
            RefreshPartList();
            ShowValues();
            disposer.Collect(link.RequestHighFps());
            CompositionTarget.Rendering += OnRendering;
            ChangeView();
            await SendLoopAsync(closing.Token);
        };
        Closed += async (_, _) =>
        {
            closing.Cancel();
            host.SelectedPartChanged -= OnHostSelectedPartChanged;
            PreviewKeyDown -= OnPreviewKeyDown;
            CompositionTarget.Rendering -= OnRendering;
            EndDrag();
            disposer.Collect(gpuReader);
            disposer.Dispose();
            await link.EndEditorAsync();
        };
    }

    void OnPreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (!Keyboard.Modifiers.HasFlag(ModifierKeys.Control) || e.Key is not (Key.Z or Key.Y)) return;
        if (drag != Drag.None || Application.Current?.MainWindow is not { } main || PresentationSource.FromVisual(main) is not { } source) return;
        e.Handled = true;
        var target = (IInputElement?)FocusManager.GetFocusedElement(main) ?? main;
        var preview = new KeyEventArgs(Keyboard.PrimaryDevice, source, e.Timestamp, e.Key) { RoutedEvent = Keyboard.PreviewKeyDownEvent };
        target.RaiseEvent(preview);
        if (!preview.Handled)
            target.RaiseEvent(new KeyEventArgs(Keyboard.PrimaryDevice, source, e.Timestamp, e.Key) { RoutedEvent = Keyboard.KeyDownEvent });
        Dispatcher.BeginInvoke(() =>
        {
            RefreshPartList();
            ShowValues();
            poseChanged = true;
            sendSignal.Release();
        }, System.Windows.Threading.DispatcherPriority.Background);
    }

    void OnHostSelectedPartChanged(string? name)
    {
        if (name != selectedPart) Select(name, notifyHost: false);
    }

    void ChangeView()
    {
        viewChanged = true;
        sendSignal.Release();
    }

    async Task SendLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                await sendSignal.WaitAsync(TimeSpan.FromMilliseconds(150), ct);
                if (viewChanged)
                {
                    viewChanged = false;
                    await link.EditorViewAsync(model.Key, viewYaw, viewPitch, viewDistance, ct);
                }
                if (poseChanged)
                {
                    poseChanged = false;
                    await link.SetModelPoseAsync(model.Key, CurrentPose(), ct);
                }
                await RefreshGizmoAsync(ct);
            }
            catch (OperationCanceledException)
            {
                return;
            }
            catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or Core.Protocol.CraftCommandException)
            {
                status.Text = ex.Message;
                viewChanged = true;
                try
                {
                    await Task.Delay(1000, ct);
                }
                catch (OperationCanceledException)
                {
                    return;
                }
                status.Text = "";
            }
        }
    }

    async Task RefreshGizmoAsync(CancellationToken ct)
    {
        var part = selectedPart;
        JsonObject? json = part is null ? null : await link.GetGizmoAsync(part, ct);
        if (part != selectedPart) return;
        gizmo = json is null ? null : ParseGizmo(json);
        DrawGizmo();
    }

    static Gizmo? ParseGizmo(JsonObject json)
    {
        static Point P(JsonNode? n) => new((double?)n?[0] ?? 0, (double?)n?[1] ?? 0);
        if (json["rings"] is not JsonArray ringArray || json["drag"] is not JsonArray dragArray || ringArray.Count != 3 || dragArray.Count != 3) return null;
        var ringPoints = ringArray.Select(r => r is JsonArray points ? points.Select(P).ToArray() : []).ToArray();
        var dragVectors = dragArray.Select(d => (Vector)P(d)).ToArray();
        return new Gizmo(ringPoints, dragVectors, json["pivot"] is JsonArray pivot ? P(pivot) : null, json["tip"] is JsonArray tip ? P(tip) : null);
    }

    void DrawGizmo()
    {
        var size = new Vector(image.ActualWidth, image.ActualHeight);
        for (var i = 0; i < 3; i++)
        {
            if (gizmo is null || size.X <= 0)
            {
                rings[i].Visibility = Visibility.Collapsed;
                continue;
            }
            rings[i].Points = [.. gizmo.Rings[i].Select(p => new Point(p.X * size.X, p.Y * size.Y))];
            rings[i].StrokeThickness = i == hoveredAxis || (drag == Drag.Axis && i == dragAxis) ? 4 : 2;
            rings[i].Visibility = Visibility.Visible;
        }
        pivotMark.Visibility = Visibility.Collapsed;
        if (gizmo is not null && size.X > 0 && gizmo.Rings[0].Length > 0)
        {
            var all = gizmo.Rings.SelectMany(r => r).ToList();
            Canvas.SetLeft(pivotMark, all.Average(p => p.X) * size.X - 4);
            Canvas.SetTop(pivotMark, all.Average(p => p.Y) * size.Y - 4);
            pivotMark.Visibility = Visibility.Visible;
        }
    }

    int RingAt(Point mouse)
    {
        if (gizmo is null) return -1;
        var size = new Vector(image.ActualWidth, image.ActualHeight);
        var best = -1;
        var bestDistance = RingGrabDistance;
        for (var i = 0; i < 3; i++)
        {
            var points = gizmo.Rings[i];
            for (var j = 1; j < points.Length; j++)
            {
                var a = new Point(points[j - 1].X * size.X, points[j - 1].Y * size.Y);
                var b = new Point(points[j].X * size.X, points[j].Y * size.Y);
                var d = DistanceToSegment(mouse, a, b);
                if (d < bestDistance)
                {
                    bestDistance = d;
                    best = i;
                }
            }
        }
        return best;
    }

    static double DistanceToSegment(Point p, Point a, Point b)
    {
        var ab = b - a;
        var t = ab.LengthSquared == 0 ? 0 : Math.Clamp(Vector.Multiply(p - a, ab) / ab.LengthSquared, 0, 1);
        return (p - (a + ab * t)).Length;
    }

    JsonArray CurrentPose()
    {
        var array = new JsonArray();
        var (frame, length) = model.PreviewPosition;
        double ValueOf(YukkuriMovieMaker.Commons.Animation a) => a.GetValue(frame, Math.Max(length, 1), model.PreviewFps);
        var origin = (ValueOf(model.X), ValueOf(model.Y), ValueOf(model.Z));
        foreach (var p in model.Pose)
        {
            if (p.ToJson(ValueOf, origin) is { } part) array.Add(part);
        }
        return array;
    }

    void OnRendering(object? sender, EventArgs e)
    {
        var notice = link.LatestNotice;
        if (notice is null || notice.Sequence == shownSequence) return;
        try
        {
            if (notice.GpuSlot >= 0)
            {
                if (link.ActiveGpuShare is not { } offer || offer.Generation != notice.GpuGeneration) return;
                if (!gpuReader.TryRead(offer, notice.GpuSlot, Copy)) return;
            }
            else if (link.LatestFrame is { } frame)
            {
                Show(frame);
            }
            else
            {
                return;
            }
        }
        catch (Exception ex) when (ex is InvalidOperationException or SharpGen.Runtime.SharpGenException)
        {
            status.Text = $"絵を読めませんでした: {ex.Message}";
            return;
        }
        shownSequence = notice.Sequence;
    }

    unsafe void Copy(IntPtr data, int rowPitch, int width, int height)
    {
        EnsureBitmap(width, height);
        bitmap!.Lock();
        try
        {
            var dst = (byte*)bitmap.BackBuffer;
            var src = (byte*)data;
            var rowBytes = width * 4;
            for (var y = 0; y < height; y++)
                Buffer.MemoryCopy(src + (long)y * rowPitch, dst + (long)y * bitmap.BackBufferStride, rowBytes, rowBytes);
            bitmap.AddDirtyRect(new Int32Rect(0, 0, width, height));
        }
        finally
        {
            bitmap.Unlock();
        }
    }

    void EnsureBitmap(int width, int height)
    {
        if (bitmap is not null && bitmap.PixelWidth == width && bitmap.PixelHeight == height) return;
        bitmap = new WriteableBitmap(width, height, 96, 96, PixelFormats.Bgr32, null);
        image.Source = bitmap;
    }

    void Show(Core.Frames.Frame frame)
    {
        var pixels = frame.ToBgraTopDown();
        EnsureBitmap(frame.Width, frame.Height);
        bitmap!.WritePixels(new Int32Rect(0, 0, frame.Width, frame.Height), pixels, frame.Width * 4, 0);
    }

    async void OnImageMouseDown(object sender, MouseButtonEventArgs e)
    {
        if (drag != Drag.None) return;
        lastMouse = e.GetPosition(image);
        image.CaptureMouse();
        e.Handled = true;
        if (e.ChangedButton == MouseButton.Right)
        {
            drag = Drag.Orbit;
            return;
        }

        var ring = RingAt(lastMouse);
        if (ring >= 0 && selectedPart is not null)
        {
            host.EnsurePart(selectedPart);
            drag = Drag.Axis;
            dragAxis = ring;
            host.BeginPoseEdit();
            DrawGizmo();
            return;
        }

        drag = Drag.Orbit;
        string? part = null;
        try
        {
            if (image.ActualWidth > 0 && image.ActualHeight > 0)
                part = await link.PickAsync(lastMouse.X / image.ActualWidth, lastMouse.Y / image.ActualHeight);
        }
        catch (Exception ex) when (ex is InvalidOperationException or IOException or TimeoutException or Core.Protocol.CraftCommandException)
        {
            status.Text = ex.Message;
        }
        if (part is null || drag != Drag.Orbit || Mouse.LeftButton != MouseButtonState.Pressed) return;
        host.EnsurePart(part);
        Select(part, notifyHost: true);
        drag = Drag.Free;
        host.BeginPoseEdit();
    }

    void OnImageMouseMove(object sender, MouseEventArgs e)
    {
        var p = e.GetPosition(image);
        if (drag == Drag.None)
        {
            var ring = RingAt(p);
            if (ring != hoveredAxis)
            {
                hoveredAxis = ring;
                DrawGizmo();
            }
            return;
        }
        var move = p - lastMouse;
        var previous = lastMouse;
        lastMouse = p;
        if (move.LengthSquared == 0) return;

        if (drag == Drag.Orbit)
        {
            viewYaw += move.X * 0.4;
            viewPitch = Math.Clamp(viewPitch + move.Y * 0.3, -89, 89);
            ChangeView();
            return;
        }
        if (selectedPart is null) return;

        var degrees = new double[3];
        var size = new Vector(image.ActualWidth, image.ActualHeight);
        var jacobian = gizmo?.Drag.Select(d => new Vector(d.X * size.X, d.Y * size.Y)).ToArray();
        if (drag == Drag.Axis)
        {
            degrees[dragAxis] = AroundPivot(previous, p, size, jacobian?[dragAxis]) ?? AlongAxis(move, jacobian?[dragAxis]);
        }
        else if (Keyboard.Modifiers.HasFlag(ModifierKeys.Shift))
        {
            degrees[1] = AlongAxis(move, jacobian?[1]);
        }
        else if (jacobian is not null && SolveTwoAxes(move, jacobian[0], jacobian[2]) is { } solved)
        {
            (degrees[0], degrees[2]) = solved;
        }
        else
        {
            degrees[0] = move.Y * FallbackDegreesPerPixel;
            degrees[2] = -move.X * FallbackDegreesPerPixel;
        }

        var pose = host.EnsurePart(selectedPart);
        var index = KeyframeValues.Index(pose.X, model.PreviewPosition);
        var axes = new[] { pose.X, pose.Y, pose.Z };
        for (var i = 0; i < 3; i++)
        {
            if (degrees[i] == 0) continue;
            KeyframeValues.Set(axes[i], index, KeyframeValues.Get(axes[i], index) + Math.Clamp(degrees[i], -90, 90));
        }
        ShowValues();
        poseChanged = true;
        sendSignal.Release();
    }

    double? AroundPivot(Point from, Point to, Vector size, Vector? perDegree)
    {
        if (gizmo is not { Pivot: { } pivotPoint, Tip: { } tipPoint } || perDegree is not { } d) return null;
        var pivot = new Point(pivotPoint.X * size.X, pivotPoint.Y * size.Y);
        var radius = new Point(tipPoint.X * size.X, tipPoint.Y * size.Y) - pivot;
        var turn = Vector.CrossProduct(radius, d);
        if (Math.Abs(turn) < 0.2 * radius.Length * d.Length) return null;
        var a = from - pivot;
        var b = to - pivot;
        if (a.Length < 4 || b.Length < 4) return 0;
        var radians = Math.Atan2(Vector.CrossProduct(a, b), a.X * b.X + a.Y * b.Y);
        return radians * 180 / Math.PI * Math.Sign(turn);
    }

    static double AlongAxis(Vector move, Vector? perDegree)
    {
        if (perDegree is not { } d || d.LengthSquared < 0.01) return (move.X - move.Y) * FallbackDegreesPerPixel;
        return Vector.Multiply(move, d) / d.LengthSquared;
    }

    static (double X, double Z)? SolveTwoAxes(Vector move, Vector dx, Vector dz)
    {
        var a11 = dx.LengthSquared;
        var a22 = dz.LengthSquared;
        var a12 = Vector.Multiply(dx, dz);
        var lambda = 0.05 * (a11 + a22) + 0.01;
        a11 += lambda;
        a22 += lambda;
        var det = a11 * a22 - a12 * a12;
        if (det < 1e-6 || a11 + a22 < 0.05) return null;
        var b1 = Vector.Multiply(dx, move);
        var b2 = Vector.Multiply(dz, move);
        return ((a22 * b1 - a12 * b2) / det, (a11 * b2 - a12 * b1) / det);
    }

    void OnImageMouseUp(object sender, MouseButtonEventArgs e)
    {
        EndDrag();
        e.Handled = true;
    }

    void EndDrag()
    {
        var wasRotating = drag is Drag.Axis or Drag.Free;
        drag = Drag.None;
        if (image.IsMouseCaptured) image.ReleaseMouseCapture();
        if (wasRotating)
        {
            host.EndPoseEdit();
            DrawGizmo();
        }
    }

    void Select(string? name, bool notifyHost)
    {
        if (selectedPart != name)
        {
            selectedPart = name;
            gizmo = null;
            DrawGizmo();
            sendSignal.Release();
        }
        if (notifyHost) host.SelectedPart = name;
        RefreshPartList();
        ShowValues();
    }

    void ResetSelected()
    {
        if (selectedPart is null) return;
        var pose = host.EnsurePart(selectedPart);
        host.BeginPoseEdit();
        var index = KeyframeValues.Index(pose.X, model.PreviewPosition);
        KeyframeValues.Set(pose.X, index, 0);
        KeyframeValues.Set(pose.Y, index, 0);
        KeyframeValues.Set(pose.Z, index, 0);
        KeyframeValues.Set(pose.Bend, index, 0);
        KeyframeValues.Set(pose.BendSide, index, 0);
        host.EndPoseEdit();
        ShowValues();
        poseChanged = true;
        sendSignal.Release();
    }

    void RefreshPartList()
    {
        var names = model.Pose.Select(p => p.Name).ToList();
        if (selectedPart is not null && !names.Contains(selectedPart)) names.Add(selectedPart);
        refreshing = true;
        parts.Items.Clear();
        foreach (var name in names)
        {
            var row = new ListBoxItem { Content = ScenePartPose.DisplayName(name), Tag = name, ToolTip = name };
            parts.Items.Add(row);
            if (name == selectedPart) parts.SelectedItem = row;
        }
        refreshing = false;
    }

    void ShowValues()
    {
        var pose = model.Pose.FirstOrDefault(p => p.Name == selectedPart);
        partTitle.Text = selectedPart is null ? "部位を選んでいません" : ScenePartPose.DisplayName(selectedPart);
        resetButton.IsEnabled = pose is not null;
        var index = pose is null ? 0 : KeyframeValues.Index(pose.X, model.PreviewPosition);
        YukkuriMovieMaker.Commons.Animation?[] animations = pose is null ? [null, null, null, null, null] : [pose.X, pose.Y, pose.Z, pose.Bend, pose.BendSide];
        for (var i = 0; i < valueTexts.Length; i++)
            valueTexts[i].Text = animations[i] is { } a ? $"{KeyframeValues.Get(a, index):F1}°" : "-";
    }
}
