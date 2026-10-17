package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.core.ClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void ymm4craft$joined(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientHooks.fireJoin(Minecraft.getInstance());
    }
}
