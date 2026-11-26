package com.dolphin_kun.ymm4craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.BlockingQueue;

@Mixin(targets = "net.minecraft.client.renderer.SectionOcclusionGraph$GraphState")
public interface SectionOcclusionGraphStateAccessor {
    @Accessor("sectionsToPropagateFrom")
    BlockingQueue<?> ymm4craft$getSectionsToPropagateFrom();
}
