using Vortice.Direct3D11;
using Vortice.DXGI;
using YMM4Craft.Core.Protocol;

namespace YMM4Craft.Gpu;

public sealed class GpuFrameReceiver(ID3D11Device device, ID3D11DeviceContext context) : IDisposable
{
    const ulong KeyFree = 0;
    const ulong KeyWritten = 1;
    const int AcquireTimeoutMs = 4;
    const int WaitTimeout = 0x102;

    readonly ID3D11Device device = device;
    readonly ID3D11DeviceContext context = context;
    int generation = -1;
    ID3D11Texture2D[] shared = [];
    IDXGIKeyedMutex[] mutexes = [];

    public ID3D11Texture2D? Texture { get; private set; }

    public long Version { get; private set; }

    public bool TryCopy(GpuShareOffer offer, int slot, bool leaveUnread = false)
    {
        if (generation != offer.Generation) Open(offer);
        if (slot < 0 || slot >= shared.Length) return false;

        if (!AcquireSync(mutexes[slot], KeyWritten, AcquireTimeoutMs)) return false;
        try
        {
            context.CopyResource(Texture!, shared[slot]);
        }
        finally
        {
            mutexes[slot].ReleaseSync(leaveUnread ? KeyWritten : KeyFree);
        }
        Version++;
        return true;
    }

    void Open(GpuShareOffer offer)
    {
        Close();
        var textures = new List<ID3D11Texture2D>();
        var keyed = new List<IDXGIKeyedMutex>();
        try
        {
            foreach (var handle in offer.Handles)
            {
                var texture = device.OpenSharedResource<ID3D11Texture2D>(new IntPtr(handle));
                textures.Add(texture);
                keyed.Add(texture.QueryInterface<IDXGIKeyedMutex>());
            }
            Texture = device.CreateTexture2D(new Texture2DDescription
            {
                Width = offer.Width,
                Height = offer.Height,
                MipLevels = 1,
                ArraySize = 1,
                Format = Format.B8G8R8A8_UNorm,
                SampleDescription = new SampleDescription(1, 0),
                Usage = ResourceUsage.Default,
                BindFlags = BindFlags.ShaderResource | BindFlags.RenderTarget,
            });
        }
        catch
        {
            foreach (var m in keyed) m.Dispose();
            foreach (var t in textures) t.Dispose();
            throw;
        }
        shared = [.. textures];
        mutexes = [.. keyed];
        generation = offer.Generation;
        Version++;
    }

    static unsafe bool AcquireSync(IDXGIKeyedMutex mutex, ulong key, int milliseconds)
    {
        var self = mutex.NativePointer;
        var vtable = *(void***)self;
        var acquire = (delegate* unmanaged[Stdcall]<IntPtr, ulong, int, int>)vtable[8];
        var hr = acquire(self, key, milliseconds);
        if (hr == WaitTimeout) return false;
        if (hr < 0) throw new InvalidOperationException($"AcquireSync に失敗しました（HRESULT 0x{hr:X8}）");
        return true;
    }

    void Close()
    {
        foreach (var m in mutexes) m.Dispose();
        foreach (var t in shared) t.Dispose();
        mutexes = [];
        shared = [];
        Texture?.Dispose();
        Texture = null;
        generation = -1;
    }

    public void Dispose() => Close();
}
