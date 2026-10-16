using Vortice.Direct3D;
using Vortice.Direct3D11;
using Vortice.DXGI;
using YMM4Craft.Core.Protocol;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Gpu;

public sealed class GpuFrameReader : IDisposable
{
    DisposeCollector disposer = new();
    ID3D11Device? device;
    ID3D11DeviceContext? context;
    GpuFrameReceiver? receiver;
    ID3D11Texture2D? staging;
    (uint Low, int High) deviceLuid;

    public bool TryRead(GpuShareOffer offer, int slot, Action<IntPtr, int, int, int> write)
    {
        EnsureDevice(offer);
        if (!receiver!.TryCopy(offer, slot, leaveUnread: true) || receiver.Texture is null) return false;

        if (staging is null || staging.Description.Width != offer.Width || staging.Description.Height != offer.Height)
        {
            disposer.RemoveAndDispose(ref staging);
            staging = device!.CreateTexture2D(new Texture2DDescription
            {
                Width = offer.Width,
                Height = offer.Height,
                MipLevels = 1,
                ArraySize = 1,
                Format = Format.B8G8R8A8_UNorm,
                SampleDescription = new SampleDescription(1, 0),
                Usage = ResourceUsage.Staging,
                CPUAccessFlags = CpuAccessFlags.Read,
            });
            disposer.Collect(staging);
        }
        context!.CopyResource(staging, receiver.Texture);
        var mapped = context.Map(staging, 0, MapMode.Read);
        try
        {
            write(mapped.DataPointer, (int)mapped.RowPitch, offer.Width, offer.Height);
        }
        finally
        {
            context.Unmap(staging, 0);
        }
        return true;
    }

    void EnsureDevice(GpuShareOffer offer)
    {
        if (device is not null && deviceLuid == (offer.LuidLow, offer.LuidHigh)) return;
        Close();
        IDXGIAdapter1? adapter = null;
        using (var factory = DXGI.CreateDXGIFactory1<IDXGIFactory1>())
        {
            for (var i = 0; factory.EnumAdapters1(i, out var candidate).Success; i++)
            {
                var l = candidate.Description1.Luid;
                if (l.LowPart == offer.LuidLow && l.HighPart == offer.LuidHigh)
                {
                    adapter = candidate;
                    break;
                }
                candidate.Dispose();
            }
        }
        try
        {
            if (adapter is null) throw new InvalidOperationException("共有テクスチャのGPUが見つかりません");
            D3D11.D3D11CreateDevice(adapter, DriverType.Unknown, DeviceCreationFlags.BgraSupport,
                [FeatureLevel.Level_11_0, FeatureLevel.Level_10_0], out var created, out var createdContext).CheckError();
            device = created!;
            disposer.Collect(device);
            context = createdContext!;
            disposer.Collect(context);
            receiver = new GpuFrameReceiver(device, context);
            disposer.Collect(receiver);
            deviceLuid = (offer.LuidLow, offer.LuidHigh);
        }
        finally
        {
            adapter?.Dispose();
        }
    }

    void Close()
    {
        staging = null;
        receiver = null;
        context = null;
        device = null;
        disposer.Dispose();
        disposer = new();
    }

    public void Dispose() => Close();
}
