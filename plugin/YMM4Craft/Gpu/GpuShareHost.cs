using Vortice.Direct3D;
using Vortice.Direct3D11;
using Vortice.DXGI;
using YMM4Craft.Core.Protocol;
using YukkuriMovieMaker.Commons;

namespace YMM4Craft.Gpu;

public sealed class GpuShareHost : IDisposable
{
    const int SlotCount = 3;

    public static GpuShareHost Shared { get; } = new();

    readonly Lock gate = new();
    DisposeCollector disposer = new();
    ID3D11Device? device;
    Vortice.Luid luid;
    Vortice.Luid? preferredLuid;
    int generation;
    TextureSet? current;
    TextureSet? previous;

    sealed record TextureSet(GpuShareOffer Offer, ID3D11Texture2D[] Textures);

    public void SetPreferredAdapter(ID3D11Device ymm4Device)
    {
        using var dxgiDevice = ymm4Device.QueryInterface<IDXGIDevice>();
        using var adapter = dxgiDevice.GetAdapter();
        var adapterLuid = adapter.Description.Luid;
        lock (gate)
        {
            if (preferredLuid is not null || device is not null) return;
            preferredLuid = adapterLuid;
        }
    }

    public GpuShareOffer? CreateOffer(int width, int height)
    {
        lock (gate)
        {
            if (current is { } c && c.Offer.Width == width && c.Offer.Height == height) return c.Offer;
            EnsureDevice();
            var textures = new ID3D11Texture2D[SlotCount];
            var handles = new long[SlotCount];
            try
            {
                for (var i = 0; i < SlotCount; i++)
                {
                    textures[i] = device!.CreateTexture2D(new Texture2DDescription
                    {
                        Width = width,
                        Height = height,
                        MipLevels = 1,
                        ArraySize = 1,
                        Format = Format.B8G8R8A8_UNorm,
                        SampleDescription = new SampleDescription(1, 0),
                        Usage = ResourceUsage.Default,
                        BindFlags = BindFlags.ShaderResource | BindFlags.RenderTarget,
                        MiscFlags = ResourceOptionFlags.SharedKeyedMutex,
                    });
                    disposer.Collect(textures[i]);
                    using var resource = textures[i].QueryInterface<IDXGIResource>();
                    handles[i] = resource.SharedHandle.ToInt64();
                }
            }
            catch
            {
                Release(textures);
                throw;
            }

            if (previous is not null) Release(previous.Textures);
            previous = current;
            var offer = new GpuShareOffer(++generation, luid.LowPart, luid.HighPart, width, height, handles);
            current = new TextureSet(offer, textures);
            return offer;
        }
    }

    void EnsureDevice()
    {
        if (device is not null) return;
        IDXGIAdapter1? adapter = null;
        if (preferredLuid is { } wanted)
        {
            using var factory = DXGI.CreateDXGIFactory1<IDXGIFactory1>();
            for (var i = 0; factory.EnumAdapters1(i, out var candidate).Success; i++)
            {
                var l = candidate.Description1.Luid;
                if (l.LowPart == wanted.LowPart && l.HighPart == wanted.HighPart)
                {
                    adapter = candidate;
                    break;
                }
                candidate.Dispose();
            }
        }
        try
        {
            var result = D3D11.D3D11CreateDevice(adapter, adapter is null ? DriverType.Hardware : DriverType.Unknown,
                DeviceCreationFlags.BgraSupport, [FeatureLevel.Level_11_0, FeatureLevel.Level_10_0], out var created);
            result.CheckError();
            device = created!;
            disposer.Collect(device);
            using var dxgiDevice = device.QueryInterface<IDXGIDevice>();
            using var actual = dxgiDevice.GetAdapter();
            luid = actual.Description.Luid;
        }
        finally
        {
            adapter?.Dispose();
        }
    }

    void Release(ID3D11Texture2D?[] textures)
    {
        for (var i = 0; i < textures.Length; i++) disposer.RemoveAndDispose(ref textures[i]);
    }

    void ResetDevice()
    {
        current = previous = null;
        device = null;
        disposer.Dispose();
        disposer = new();
    }

    public void Dispose()
    {
        lock (gate) ResetDevice();
    }
}
