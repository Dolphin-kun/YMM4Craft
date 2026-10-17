package com.dolphin_kun.ymm4craft.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("swell")
    void ymm4craft$setSwell(int value);

    @Accessor("oldSwell")
    void ymm4craft$setOldSwell(int value);

    @Accessor("maxSwell")
    int ymm4craft$maxSwell();
}
