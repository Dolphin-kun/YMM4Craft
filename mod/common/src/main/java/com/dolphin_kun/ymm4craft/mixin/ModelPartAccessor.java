package com.dolphin_kun.ymm4craft.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("children")
    Map<String, ModelPart> ymm4craft$children();

    @Accessor("cubes")
    java.util.List<ModelPart.Cube> ymm4craft$cubes();
}
