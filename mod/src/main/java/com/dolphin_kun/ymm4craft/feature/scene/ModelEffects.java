package com.dolphin_kun.ymm4craft.feature.scene;

public record ModelEffects(boolean fire, int glowColor, boolean invisible, boolean hurt, boolean dying, float swell) {
    static final ModelEffects NONE = new ModelEffects(false, -1, false, false, false, 0);
}
