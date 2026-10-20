package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TickRateManager.class)
public abstract class TickRateManagerMixin {
    @Shadow
    public abstract boolean runsNormally();

    @Inject(method = "isEntityFrozen", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$freezePlayersInReplay(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!ReplayPlayback.freezesEverything()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.tickRateManager() != (Object) this) return;
        cir.setReturnValue(!runsNormally() && entity != mc.player);
    }
}
