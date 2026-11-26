package com.dolphin_kun.ymm4craft.mixin;

import net.minecraft.client.renderer.SectionBufferBuilderPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.ArrayBlockingQueue;

@Mixin(SectionBufferBuilderPool.class)
public interface SectionBufferBuilderPoolAccessor {
    @Accessor("freeBuffers")
    ArrayBlockingQueue<?> ymm4craft$getFreeBuffers();
}
