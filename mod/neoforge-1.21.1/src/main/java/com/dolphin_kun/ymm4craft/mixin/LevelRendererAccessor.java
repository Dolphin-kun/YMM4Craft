package com.dolphin_kun.ymm4craft.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("sectionOcclusionGraph")
    SectionOcclusionGraph ymm4craft$getSectionOcclusionGraph();

    @Accessor("visibleSections")
    ObjectArrayList<?> ymm4craft$getVisibleSections();
}
