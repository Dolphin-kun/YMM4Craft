package com.dolphin_kun.ymm4craft.config;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class WideTooltips {
    public static final int WIDTH = 320;

    private static final Set<Tooltip> WIDE = Collections.newSetFromMap(new WeakHashMap<>());

    private WideTooltips() {
    }

    public static Tooltip create(Component message) {
        Tooltip tooltip = Tooltip.create(message);
        WIDE.add(tooltip);
        return tooltip;
    }

    public static boolean isWide(Tooltip tooltip) {
        return WIDE.contains(tooltip);
    }
}
