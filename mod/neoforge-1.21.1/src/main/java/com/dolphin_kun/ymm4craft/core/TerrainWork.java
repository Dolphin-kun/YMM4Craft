package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.mixin.LevelRendererAccessor;
import com.dolphin_kun.ymm4craft.mixin.SectionOcclusionGraphAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.Future;

final class TerrainWork {
    private static int maxFreeBuffers;
    private static Method events;
    private static Method propagation;
    private static Method neighbors;

    private TerrainWork() {
    }

    static TerrainState current(Minecraft mc) {
        if (SodiumTerrain.present()) return SodiumTerrain.current();
        try {
            return vanilla(mc);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static TerrainState vanilla(Minecraft mc) throws ReflectiveOperationException {
        LevelRenderer renderer = mc.levelRenderer;
        LevelRendererAccessor access = (LevelRendererAccessor) renderer;
        int visible = access.ymm4craft$getVisibleSections().size();
        if (visible == 0) return null;

        SectionRenderDispatcher dispatcher = renderer.getSectionRenderDispatcher();
        boolean busy = false;
        if (dispatcher != null) {
            int free = dispatcher.getFreeBufferCount();
            maxFreeBuffers = Math.max(maxFreeBuffers, free);
            busy = !dispatcher.isQueueEmpty() || free < maxFreeBuffers;
        }

        SectionOcclusionGraphAccessor graph = (SectionOcclusionGraphAccessor) access.ymm4craft$getSectionOcclusionGraph();
        Future<?> fullUpdate = graph.ymm4craft$getFullUpdateTask();
        busy |= graph.ymm4craft$needsFullUpdate()
                || (fullUpdate != null && !fullUpdate.isDone())
                || graph.ymm4craft$getNeedsFrustumUpdate().get();
        Object state = graph.ymm4craft$getCurrentGraph().get();
        if (state != null) {
            if (events == null) events = accessible(state.getClass().getDeclaredMethod("events"));
            busy |= hasEvents(events.invoke(state));
        }
        busy |= hasEvents(graph.ymm4craft$getNextGraphEvents().get());

        return new TerrainState(busy, visible);
    }

    private static boolean hasEvents(Object graphEvents) throws ReflectiveOperationException {
        if (graphEvents == null) return false;
        if (propagation == null) {
            neighbors = accessible(graphEvents.getClass().getDeclaredMethod("chunksWhichReceivedNeighbors"));
            propagation = accessible(graphEvents.getClass().getDeclaredMethod("sectionsToPropagateFrom"));
        }
        return !((Collection<?>) propagation.invoke(graphEvents)).isEmpty()
                || !((Collection<?>) neighbors.invoke(graphEvents)).isEmpty();
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }
}
