using YMM4Craft.Core;
using YukkuriMovieMaker.Plugin.Shape;
using YukkuriMovieMaker.Project;

namespace YMM4Craft.Scene;

public sealed class SceneShapePlugin : IShapePlugin
{
    static int initialized;

    public SceneShapePlugin()
    {
        if (Interlocked.Exchange(ref initialized, 1) == 0)
        {
            CraftLink.Shared.GpuShareProvider = Gpu.GpuShareHost.Shared.CreateOffer;
        }
    }

    public string Name => "YMM4Craftシーン";

    public bool IsExoShapeSupported => false;

    public bool IsExoMaskSupported => false;

    public IShapeParameter CreateShapeParameter(SharedDataStore? sharedData) => new SceneShapeParameter(sharedData);
}
