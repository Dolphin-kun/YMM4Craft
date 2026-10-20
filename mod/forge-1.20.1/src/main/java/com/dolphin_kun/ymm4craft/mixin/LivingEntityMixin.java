package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;swingingArm:Lnet/minecraft/world/InteractionHand;", opcode = Opcodes.PUTFIELD))
    private void ymm4craft$recordSwing(InteractionHand hand, boolean sendToSwingingEntity, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && player == Minecraft.getInstance().player) {
            ReplayRecorder.onSelfSwing(player, hand);
        }
    }
}
