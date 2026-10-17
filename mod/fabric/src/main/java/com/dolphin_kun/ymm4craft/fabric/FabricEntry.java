package com.dolphin_kun.ymm4craft.fabric;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import net.fabricmc.api.ClientModInitializer;

public final class FabricEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Ymm4Craft.initialize();
    }
}
