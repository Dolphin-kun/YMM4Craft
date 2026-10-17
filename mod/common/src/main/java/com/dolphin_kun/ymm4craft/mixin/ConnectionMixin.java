package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayRecorder;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "configurePacketHandler", at = @At("TAIL"))
    private void ymm4craft$attachRecorder(ChannelPipeline pipeline, CallbackInfo ci) {
        Connection self = (Connection) (Object) this;
        if (self.getReceiving() != PacketFlow.CLIENTBOUND) return;
        ReplayRecorder.onConnectionCreated(self, pipeline);
    }
}
