package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayRecorder;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Shadow
    @Final
    private Deque<Runnable> lightUpdateQueue;

    @Inject(method = "update", at = @At("HEAD"))
    private void ymm4craft$replayLightAtOnce(CallbackInfo ci) {
        if (ReplayPlayback.active()) ymm4craft$drainLight();
    }

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$skipAnimateTick(int x, int y, int z, CallbackInfo ci) {
        if (ReplayPlayback.skipsParticles()) ci.cancel();
    }

    @Unique
    private void ymm4craft$drainLight() {
        for (Runnable update; (update = lightUpdateQueue.poll()) != null; ) update.run();
    }

    @Shadow
    private void playSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay, long seed) {
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"))
    private void ymm4craft$recordSelfSound(Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source,
                                          float volume, float pitch, long seed, CallbackInfo ci) {
        if (except != null && except == Minecraft.getInstance().player) {
            ReplayRecorder.onSelfPacket(new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch, seed));
        } else if (except != null && ReplayPlayback.playsSoundsOf(except)) {
            playSound(x, y, z, sound.value(), source, volume, pitch, false, seed);
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"))
    private void ymm4craft$recordSelfEntitySound(Entity except, Entity sourceEntity, Holder<SoundEvent> sound, SoundSource source,
                                                float volume, float pitch, long seed, CallbackInfo ci) {
        if (except != null && except == Minecraft.getInstance().player) {
            ReplayRecorder.onSelfPacket(new ClientboundSoundEntityPacket(sound, source, sourceEntity, volume, pitch, seed));
        } else if (except != null && ReplayPlayback.playsSoundsOf(except)) {
            Minecraft.getInstance().getSoundManager().play(new EntityBoundSoundInstance(sound.value(), source, volume, pitch, sourceEntity, seed));
        }
    }

    @Inject(method = "destroyBlockProgress", at = @At("HEAD"))
    private void ymm4craft$recordSelfBreaking(int id, BlockPos pos, int progress, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && id == player.getId()) {
            ReplayRecorder.onSelfPacket(new ClientboundBlockDestructionPacket(id, pos.immutable(), progress));
        }
    }

    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void ymm4craft$recordSelfLevelEvent(Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
        if (source != null && source == Minecraft.getInstance().player) {
            ReplayRecorder.onSelfPacket(new ClientboundLevelEventPacket(type, pos.immutable(), data, false));
        }
    }
}
