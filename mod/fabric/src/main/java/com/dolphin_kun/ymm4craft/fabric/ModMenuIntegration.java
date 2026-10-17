package com.dolphin_kun.ymm4craft.fabric;

import com.dolphin_kun.ymm4craft.config.Ymm4CraftScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return Ymm4CraftScreen::new;
    }
}
