package com.dolphin_kun.ymm4craft.feature.replay;

import net.minecraft.network.protocol.Packet;

public record ReplayEntry(int timeMs, byte kind, byte[] data, Packet<?> packet) {
    private static final byte[] NONE = new byte[0];

    public ReplayEntry(int timeMs, byte kind, byte[] data) {
        this(timeMs, kind, data, null);
    }

    static ReplayEntry of(int timeMs, Packet<?> packet) {
        return new ReplayEntry(timeMs, ReplayFiles.DECODED, NONE, packet);
    }
}
