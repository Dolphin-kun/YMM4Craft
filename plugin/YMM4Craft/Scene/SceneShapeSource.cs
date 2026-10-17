using YMM4Craft.Controls;
using YMM4Craft.Models;
using System.Numerics;
using System.Text.Json.Nodes;
using Vortice.DCommon;
using Vortice.Direct2D1;
using Vortice.DirectWrite;
using Vortice.DXGI;
using Vortice.Mathematics;
using YMM4Craft.Core;
using YMM4Craft.Core.Frames;
using YMM4Craft.Gpu;
using YukkuriMovieMaker.Commons;
using YukkuriMovieMaker.Player.Video;

namespace YMM4Craft.Scene;

internal sealed class SceneShapeSource(IGraphicsDevicesAndContext devices, SceneShapeParameter parameter) : IShapeSource
{
    static readonly TimeSpan PausedWait = TimeSpan.FromMilliseconds(250);
    static readonly TimeSpan ExportWait = TimeSpan.FromSeconds(30);
    static readonly TimeSpan ExportSettleWait = TimeSpan.FromSeconds(20);

    readonly IGraphicsDevicesAndContext devices = devices;
    readonly SceneShapeParameter parameter = parameter;
    readonly CraftLink link = CraftLink.Shared;
    readonly DisposeCollector disposer = new();

    ID2D1CommandList? commandList;
    ID2D1Bitmap1? bitmap;
    long drawnSequence = -1;
    long drawnGpuSequence = -1;
    bool adapterNoted;
    GpuFrameReceiver? receiver;
    ID2D1Bitmap1? gpuBitmap;
    object? gpuBitmapSource;
    int screenWidth = 1920;
    int screenHeight = 1080;
    (int Width, int Height) drawnSize;
    string? drawnMessage;

    IDWriteFactory? dwrite;
    IDWriteTextFormat? textFormat;
    ID2D1SolidColorBrush? backgroundBrush;
    ID2D1SolidColorBrush? textBrush;
    ID2D1SolidColorBrush? fillBrush;
    System.Windows.Media.Color? drawnFill;
    bool overlay;
    bool drawnOverlay;
    ID2D1SolidColorBrush? overlayBrush;

    public ID2D1Image Output => commandList ?? throw new InvalidOperationException("Update を呼ぶ前に Output が使われました");

    public void Update(TimelineItemSourceDescription desc)
    {
        var frame = desc.ItemPosition.Frame;
        var length = desc.ItemDuration.Frame;
        var fps = desc.FPS;
        if (desc.Usage != TimelineSourceUsage.Exporting)
        {
            parameter.PreviewPosition = (frame, length);
            parameter.PreviewFps = fps;
            foreach (var model in parameter.Models)
            {
                model.PreviewPosition = (frame, length);
                model.PreviewFps = fps;
            }
        }
        foreach (var model in parameter.Models) model.Siblings = parameter.Models;

        var scene = new JsonObject
        {
            ["camera"] = new JsonObject
            {
                ["x"] = parameter.CameraX.GetValue(frame, length, fps),
                ["y"] = parameter.CameraY.GetValue(frame, length, fps),
                ["z"] = parameter.CameraZ.GetValue(frame, length, fps),
                ["yaw"] = Angles.Wrap(parameter.Yaw.GetValue(frame, length, fps)),
                ["pitch"] = Math.Clamp(parameter.Pitch.GetValue(frame, length, fps), -90, 90),
            },
            ["fov"] = Math.Clamp(parameter.Fov.GetValue(frame, length, fps), 1, 170),
            ["hideHud"] = parameter.HideHud,
            ["transparent"] = parameter.Background != SceneBackground.World,
            ["models"] = new JsonArray([.. parameter.Models.Select(m => m.ToJson(frame, length, fps, path => SkinFiles.IdOf(path, desc.Usage == TimelineSourceUsage.Exporting))).OfType<JsonNode>()]),
        };
        if (parameter.IsReplay)
        {
            scene["replay"] = new JsonObject
            {
                ["time"] = Math.Clamp(parameter.ReplayTimeAt(frame, length, fps), 0, 604800),
                ["camera"] = parameter.ReplayCamera == SceneReplayCamera.Player ? "pov" : "free",
                ["hand"] = parameter.ReplayShowHand,
                ["screens"] = parameter.ReplayShowScreens,
                ["cursor"] = parameter.ReplayShowCursor,
                ["chat"] = parameter.ReplayShowChat,
                ["player"] = parameter.ReplayPlayer,
                ["exact"] = desc.Usage == TimelineSourceUsage.Exporting,
            };
        }
        else
        {
            scene["time"] = (int)Math.Round(Math.Clamp(parameter.Time.GetValue(frame, length, fps), 0, 23999));
            scene["weather"] = parameter.Weather switch
            {
                SceneWeather.Rain => "rain",
                SceneWeather.Thunder => "thunder",
                _ => "clear",
            };
        }

        (screenWidth, screenHeight) = parameter.Resolution == SceneResolution.Custom
            ? (Math.Clamp(parameter.CustomWidth, 16, 3840), Math.Clamp(parameter.CustomHeight, 16, 2160))
            : (Math.Clamp(desc.ScreenSize.Width, 16, 3840), Math.Clamp(desc.ScreenSize.Height, 16, 2160));

        if (!adapterNoted)
        {
            adapterNoted = true;
            try
            {
                GpuShareHost.Shared.SetPreferredAdapter(devices.D3D.Device);
            }
            catch (Exception ex) when (ex is SharpGen.Runtime.SharpGenException or InvalidCastException)
            {
            }
        }

        link.EnsureStarted();
        link.SetProjectFps(fps);
        link.RequestWorld(parameter.World);
        link.RequestResolution(screenWidth, screenHeight);
        var revision = link.SubmitScene(scene);
        ScenePreviewUpdater.OnUpdate(desc.Usage, revision);
        if (parameter.IsReplay && desc.Usage != TimelineSourceUsage.Exporting) ReplayAudioExporter.RequestAuto(parameter);

        var notice = desc.Usage switch
        {
            TimelineSourceUsage.Exporting => link.WaitForNotice(revision, ExportSettleWait, requireSettled: true)
                ?? link.WaitForNotice(revision, ExportWait)
                ?? throw new InvalidOperationException($"Minecraftの描画を待てませんでした。{link.StatusMessage}"),
            TimelineSourceUsage.Paused => link.WaitForNotice(revision, PausedWait) ?? link.LatestNotice,
            _ => link.LatestNotice,
        };

        overlay = desc.Usage != TimelineSourceUsage.Exporting && link.IsReplayLoading;
        if (notice is not null && link.State == CraftLinkState.Ready)
        {
            var gpu = link.ActiveGpuShare;
            if (notice.GpuSlot >= 0)
            {
                if (gpu is not null && gpu.Generation == notice.GpuGeneration) DrawGpuFrame(gpu, notice);
                return;
            }
            if (link.LatestFrame is { } cpuFrame)
            {
                DrawFrame(cpuFrame);
                return;
            }
        }
        DrawMessage(link.StatusMessage is { Length: > 0 } m ? m : "Minecraftと接続していません");
    }

    void DrawGpuFrame(Core.Protocol.GpuShareOffer offer, FrameNotice notice)
    {
        if (notice.Sequence == drawnGpuSequence && drawnSize == (screenWidth, screenHeight) && drawnFill == CurrentFill && drawnOverlay == overlay && commandList is not null) return;

        receiver ??= Keep(new GpuFrameReceiver(devices.D3D.Device, devices.D3D.DeviceContext));
        try
        {
            if (!receiver.TryCopy(offer, notice.GpuSlot) && receiver.Texture is null) return;
        }
        catch (Exception ex) when (ex is SharpGen.Runtime.SharpGenException or InvalidOperationException)
        {
            link.DisableGpuShare($"YMM4側で共有テクスチャを開けませんでした: {ex.Message}");
            disposer.RemoveAndDispose(ref receiver);
            return;
        }

        var dc = devices.DeviceContext;
        var alpha = AlphaMode;
        if (gpuBitmap is null || !ReferenceEquals(gpuBitmapSource, receiver.Texture) || gpuBitmap.PixelFormat.AlphaMode != alpha)
        {
            disposer.RemoveAndDispose(ref gpuBitmap);
            using var surface = receiver.Texture!.QueryInterface<IDXGISurface>();
            gpuBitmap = Keep(dc.CreateBitmapFromDxgiSurface(surface,
                new BitmapProperties1(new PixelFormat(Format.B8G8R8A8_UNorm, alpha), 96, 96, BitmapOptions.None)));
            gpuBitmapSource = receiver.Texture;
        }

        BeginCommandList(dc);
        FillBackground(dc);
        dc.DrawBitmap(gpuBitmap, CenteredRect(screenWidth, screenHeight), 1f, BitmapInterpolationMode.Linear, null);
        DrawOverlay(dc);
        EndCommandList(dc);

        drawnGpuSequence = notice.Sequence;
        drawnSequence = -1;
        drawnSize = (screenWidth, screenHeight);
        drawnMessage = null;
    }

    void DrawFrame(Frame frame)
    {
        if (frame.Sequence == drawnSequence && drawnSize == (screenWidth, screenHeight) && drawnFill == CurrentFill && drawnOverlay == overlay && commandList is not null) return;

        var format = frame.Format == FrameFormat.Bgra8BottomUp ? Format.B8G8R8A8_UNorm : Format.R8G8B8A8_UNorm;
        var dc = devices.DeviceContext;
        var alpha = AlphaMode;
        if (bitmap is null || bitmap.PixelSize.Width != frame.Width || bitmap.PixelSize.Height != frame.Height || bitmap.PixelFormat.Format != format
            || bitmap.PixelFormat.AlphaMode != alpha)
        {
            disposer.RemoveAndDispose(ref bitmap);
            bitmap = Keep(dc.CreateBitmap(new SizeI(frame.Width, frame.Height), IntPtr.Zero, 0,
                new BitmapProperties1(new PixelFormat(format, alpha), 96, 96, BitmapOptions.None)));
        }
        unsafe
        {
            fixed (byte* p = frame.Pixels)
                bitmap.CopyFromMemory((IntPtr)p, frame.Stride);
        }

        BeginCommandList(dc);
        FillBackground(dc);
        dc.Transform = Matrix3x2.CreateScale(1, -1);
        dc.DrawBitmap(bitmap, CenteredRect(screenWidth, screenHeight), 1f, BitmapInterpolationMode.Linear, null);
        dc.Transform = Matrix3x2.Identity;
        DrawOverlay(dc);
        EndCommandList(dc);

        drawnSequence = frame.Sequence;
        drawnGpuSequence = -1;
        drawnSize = (screenWidth, screenHeight);
        drawnMessage = null;
    }

    void DrawOverlay(ID2D1DeviceContext dc)
    {
        drawnOverlay = overlay;
        if (!overlay) return;
        EnsureText(dc);
        overlayBrush ??= Keep(dc.CreateSolidColorBrush(new Color4(0f, 0f, 0f, 0.55f)));
        var rect = CenteredRect(screenWidth, screenHeight);
        var box = new Vortice.RawRectF(rect.Left + 24, rect.Top + 24, rect.Left + 420, rect.Top + 88);
        dc.FillRectangle(box, overlayBrush);
        dc.DrawText(link.IsExportingAudio ? "音を書き出しています…" : "読み込み中…", textFormat!, new Vortice.RawRectF(box.Left + 20, box.Top + 12, box.Right - 12, box.Bottom - 8), textBrush!);
    }

    void EnsureText(ID2D1DeviceContext dc)
    {
        dwrite ??= Keep(DWrite.DWriteCreateFactory<IDWriteFactory>());
        textFormat ??= Keep(dwrite.CreateTextFormat("Yu Gothic UI", 32f));
        textBrush ??= Keep(dc.CreateSolidColorBrush(new Color4(1f, 1f, 1f, 1f)));
    }

    void DrawMessage(string message)
    {
        if (message == drawnMessage && drawnSize == (screenWidth, screenHeight) && commandList is not null) return;

        var dc = devices.DeviceContext;
        EnsureText(dc);
        backgroundBrush ??= Keep(dc.CreateSolidColorBrush(new Color4(0.12f, 0.14f, 0.12f, 1f)));

        var rect = CenteredRect(screenWidth, screenHeight);

        BeginCommandList(dc);
        dc.FillRectangle(rect, backgroundBrush);
        var margin = 48f;
        dc.DrawText($"YMM4Craft\n{message}", textFormat!,
            new Vortice.RawRectF(rect.Left + margin, rect.Top + margin, rect.Right - margin, rect.Bottom - margin), textBrush!);
        EndCommandList(dc);

        drawnMessage = message;
        drawnSize = (screenWidth, screenHeight);
        drawnSequence = -1;
        drawnGpuSequence = -1;
    }

    Vortice.DCommon.AlphaMode AlphaMode => parameter.Background == SceneBackground.World ? Vortice.DCommon.AlphaMode.Ignore : Vortice.DCommon.AlphaMode.Premultiplied;

    System.Windows.Media.Color? CurrentFill => parameter.Background == SceneBackground.Color ? parameter.BackgroundColor : null;

    void FillBackground(ID2D1DeviceContext dc)
    {
        drawnFill = CurrentFill;
        if (drawnFill is not { } c) return;
        disposer.RemoveAndDispose(ref fillBrush);
        fillBrush = Keep(dc.CreateSolidColorBrush(new Color4(c.R / 255f, c.G / 255f, c.B / 255f, c.A / 255f)));
        dc.FillRectangle(CenteredRect(screenWidth, screenHeight), fillBrush);
    }

    static Vortice.RawRectF CenteredRect(int width, int height) => new(-width / 2f, -height / 2f, width / 2f, height / 2f);

    void BeginCommandList(ID2D1DeviceContext dc)
    {
        disposer.RemoveAndDispose(ref commandList);
        commandList = Keep(dc.CreateCommandList());
        dc.Target = commandList;
        dc.BeginDraw();
        dc.Clear(null);
    }

    void EndCommandList(ID2D1DeviceContext dc)
    {
        dc.EndDraw();
        dc.Target = null;
        commandList!.Close();
    }

    T Keep<T>(T resource) where T : IDisposable
    {
        disposer.Collect(resource);
        return resource;
    }

    public void Dispose() => disposer.Dispose();
}
