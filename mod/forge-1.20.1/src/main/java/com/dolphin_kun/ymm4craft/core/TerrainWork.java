package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.mixin.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

import java.util.concurrent.Future;

final class TerrainWork {
    private static int maxFreeBuffers;

    private TerrainWork() {
    }

    static TerrainState current(Minecraft mc) {
        if (SodiumTerrain.present()) return SodiumTerrain.current();
        try {
            return vanilla(mc);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static TerrainState vanilla(Minecraft mc) {
        LevelRenderer renderer = mc.levelRenderer;
        LevelRendererAccessor access = (LevelRendererAccessor) renderer;
        int visible = access.ymm4craft$getRenderChunksInFrustum().size();
        if (visible == 0) return null;

        ChunkRenderDispatcher dispatcher = renderer.getChunkRenderDispatcher();
        boolean busy = false;
        if (dispatcher != null) {
            int free = dispatcher.getFreeBufferCount();
            maxFreeBuffers = Math.max(maxFreeBuffers, free);
            busy = !dispatcher.isQueueEmpty() || free < maxFreeBuffers;
        }

        Future<?> fullUpdate = access.ymm4craft$getLastFullRenderChunkUpdate();
        busy |= access.ymm4craft$needsFullRenderChunkUpdate()
                || (fullUpdate != null && !fullUpdate.isDone())
                || access.ymm4craft$getNeedsFrustumUpdate().get()
                || !access.ymm4craft$getRecentlyCompiledChunks().isEmpty();

        return new TerrainState(busy, visible);
    }
}
