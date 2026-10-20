package com.dolphin_kun.ymm4craft.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Level.class)
public interface LevelInvoker {
    @Invoker("tickBlockEntities")
    void ymm4craft$tickBlockEntities();
}
