package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.mixin.LevelRendererAccessor;
import com.dolphin_kun.ymm4craft.mixin.SectionBufferBuilderPoolAccessor;
import com.dolphin_kun.ymm4craft.mixin.SectionOcclusionGraphAccessor;
import com.dolphin_kun.ymm4craft.mixin.SectionOcclusionGraphStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

import java.util.concurrent.Future;

final class TerrainWork {
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
        int visible = renderer.visibleSections().size();
        if (visible == 0) return null;
        SectionRenderDispatcher dispatcher = ((LevelRendererAccessor) renderer).ymm4craft$getSectionRenderDispatcher();
        boolean busy = dispatcher != null && !dispatcher.isQueueEmpty();
        busy |= ((SectionBufferBuilderPoolAccessor) mc.gameRenderer.renderBuffers().sectionBufferPool()).ymm4craft$getFreeBuffers().remainingCapacity() > 0;

        SectionOcclusionGraphAccessor graph = (SectionOcclusionGraphAccessor) renderer.sectionOcclusionGraph();
        Future<?> fullUpdate = graph.ymm4craft$getFullUpdateTask();
        busy |= graph.ymm4craft$needsFullUpdate()
                || (fullUpdate != null && !fullUpdate.isDone())
                || graph.ymm4craft$getNeedsFrustumUpdate().get();
        Object state = graph.ymm4craft$getCurrentGraph().get();
        if (state != null) busy |= !((SectionOcclusionGraphStateAccessor) state).ymm4craft$getSectionsToPropagateFrom().isEmpty();

        return new TerrainState(busy, visible);
    }
}
