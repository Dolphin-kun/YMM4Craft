package com.dolphin_kun.ymm4craft.feature.replay;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

final class ReplaySoundEvent {
    final ResourceLocation path;
    final int startTick;
    final float attenuation;
    final boolean linear;
    final boolean relative;
    final boolean looping;
    private final List<float[]> states;
    private int endTick = Integer.MAX_VALUE;

    ReplaySoundEvent(ResourceLocation path, int startTick, float attenuation, boolean linear, boolean relative, boolean looping) {
        this(path, startTick, attenuation, linear, relative, looping, new ArrayList<>());
    }

    private ReplaySoundEvent(ResourceLocation path, int startTick, float attenuation, boolean linear, boolean relative, boolean looping, List<float[]> states) {
        this.path = path;
        this.startTick = startTick;
        this.attenuation = attenuation;
        this.linear = linear;
        this.relative = relative;
        this.looping = looping;
        this.states = states;
    }

    ReplaySoundEvent shift(int by) {
        ReplaySoundEvent shifted = new ReplaySoundEvent(path, startTick - by, attenuation, linear, relative, looping, List.copyOf(states));
        shifted.endTick = endTick == Integer.MAX_VALUE ? endTick : endTick - by;
        return shifted;
    }

    void addState(double x, double y, double z, float volume, float pitch) {
        states.add(new float[]{(float) x, (float) y, (float) z, volume, pitch});
    }

    void stop(int tick) {
        endTick = Math.min(endTick, tick);
    }

    float[] stateAt(int tick) {
        if (tick >= endTick || states.isEmpty()) return null;
        return states.get(Math.clamp(tick - startTick, 0, states.size() - 1));
    }
}
