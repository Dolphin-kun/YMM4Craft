package com.dolphin_kun.ymm4craft.core;

import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

public final class NoChunkFade {
    public static final OptionInstance<Double> OPTION = new OptionInstance<>(
            "options.chunkFade",
            OptionInstance.noTooltip(),
            (caption, value) -> Component.translatable("options.chunkFade.none"),
            new OptionInstance.IntRange(0, 0).xmap(value -> 0.0, value -> 0, true),
            Codec.doubleRange(0.0, 0.0),
            0.0,
            value -> {
            });

    private NoChunkFade() {
    }
}
