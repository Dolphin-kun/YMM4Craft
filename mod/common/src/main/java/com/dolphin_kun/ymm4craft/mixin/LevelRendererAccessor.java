package com.dolphin_kun.ymm4craft.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("sectionRenderDispatcher")
    SectionRenderDispatcher ymm4craft$getSectionRenderDispatcher();
}
