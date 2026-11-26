package com.dolphin_kun.ymm4craft.mixin;

import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(SectionOcclusionGraph.class)
public interface SectionOcclusionGraphAccessor {
    @Accessor("needsFullUpdate")
    boolean ymm4craft$needsFullUpdate();

    @Accessor("fullUpdateTask")
    Future<?> ymm4craft$getFullUpdateTask();

    @Accessor("needsFrustumUpdate")
    AtomicBoolean ymm4craft$getNeedsFrustumUpdate();

    @Accessor("currentGraph")
    AtomicReference<?> ymm4craft$getCurrentGraph();
}
