package com.dolphin_kun.ymm4craft.feature.replay;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class ReplayAudioCache {
    private final BitSet covered = new BitSet();
    private final List<ReplaySoundEvent> events = new ArrayList<>();
    private final Map<TickableSoundInstance, ReplaySoundEvent> ticking = new IdentityHashMap<>();
    private final Int2ObjectOpenHashMap<Map<String, float[]>> listeners = new Int2ObjectOpenHashMap<>();
    private int tick = -1;
    private boolean collecting;

    void interrupt() {
        if (collecting && tick >= 0) covered.set(tick);
        for (ReplaySoundEvent event : ticking.values()) event.stop(tick + 1);
        ticking.clear();
        collecting = false;
        tick = -1;
    }

    void advance(int newTick, boolean collect, Entity recorded, List<? extends Player> players, Player self) {
        if (newTick != tick + 1) interrupt();
        else if (collecting) covered.set(tick);
        for (var it = ticking.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            TickableSoundInstance sound = e.getKey();
            if (!sound.isStopped()) sound.tick();
            if (sound.isStopped()) {
                e.getValue().stop(newTick);
                it.remove();
            } else {
                e.getValue().addState(sound.getX(), sound.getY(), sound.getZ(), sound.getVolume(), pitch(sound));
            }
        }
        tick = newTick;
        collecting = collect && !covered.get(newTick);
        if (!collecting) return;
        Map<String, float[]> heard = new HashMap<>();
        if (recorded != null) heard.put("", ear(recorded));
        for (Player player : players) {
            if (player != self) heard.put(player.getGameProfile().getName(), ear(player));
        }
        listeners.put(newTick, heard);
    }

    void onPlay(SoundInstance instance) {
        if (!collecting || instance.getSource() == SoundSource.MUSIC || !instance.canPlaySound()) return;
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        if (instance.resolve(manager) == null) return;
        Sound sound = instance.getSound();
        if (sound == null || sound == SoundManager.EMPTY_SOUND || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) return;
        float volume = instance.getVolume();
        ReplaySoundEvent event = new ReplaySoundEvent(sound.getPath(), tick, Math.max(volume, 1f) * sound.getAttenuationDistance(),
                instance.getAttenuation() == SoundInstance.Attenuation.LINEAR, instance.isRelative(), instance.isLooping());
        event.addState(instance.getX(), instance.getY(), instance.getZ(), volume, pitch(instance));
        events.add(event);
        if (instance instanceof TickableSoundInstance tickable) ticking.put(tickable, event);
    }

    boolean collectingAt(int t) {
        return collecting && tick == t;
    }

    int coveredCount(int from, int to) {
        return to < from ? 0 : covered.get(from, to + 1).cardinality();
    }

    int firstMissing(int from, int to) {
        int missing = covered.nextClearBit(from);
        return missing <= to ? missing : -1;
    }

    int missingRunEnd(int from, int to) {
        int next = covered.nextSetBit(from);
        return next < 0 || next > to ? to : next - 1;
    }

    List<ReplaySoundEvent> eventsBetween(int after, int last) {
        List<ReplaySoundEvent> result = new ArrayList<>();
        for (ReplaySoundEvent event : events) {
            if (event.startTick > after && event.startTick <= last) result.add(event.shift(after));
        }
        return result;
    }

    List<float[]> listenerBetween(int after, int last, String name) {
        List<float[]> result = new ArrayList<>(Math.max(0, last - after));
        for (int t = after + 1; t <= last; t++) {
            Map<String, float[]> heard = listeners.get(t);
            result.add(heard == null ? null : heard.get(name));
        }
        return result;
    }

    private static float[] ear(Entity entity) {
        Vec3 eye = entity.getEyePosition();
        return new float[]{(float) eye.x, (float) eye.y, (float) eye.z, entity.getYHeadRot(), entity.getXRot()};
    }

    private static float pitch(SoundInstance instance) {
        return Mth.clamp(instance.getPitch(), 0.5f, 2.0f);
    }
}
