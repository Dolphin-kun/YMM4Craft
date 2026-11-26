package com.dolphin_kun.ymm4craft.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("needsFullRenderChunkUpdate")
    boolean ymm4craft$needsFullRenderChunkUpdate();

    @Accessor("lastFullRenderChunkUpdate")
    Future<?> ymm4craft$getLastFullRenderChunkUpdate();

    @Accessor("needsFrustumUpdate")
    AtomicBoolean ymm4craft$getNeedsFrustumUpdate();

    @Accessor("recentlyCompiledChunks")
    BlockingQueue<?> ymm4craft$getRecentlyCompiledChunks();

    @Accessor("renderChunksInFrustum")
    ObjectArrayList<?> ymm4craft$getRenderChunksInFrustum();
}
