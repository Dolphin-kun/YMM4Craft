package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderHolder;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements ModelRenderHolder {
    @Unique
    private ModelRenderInfo ymm4craft$model;

    @Override
    public ModelRenderInfo ymm4craft$modelInfo() {
        return ymm4craft$model;
    }

    @Override
    public void ymm4craft$setModelInfo(ModelRenderInfo info) {
        ymm4craft$model = info;
    }
}
