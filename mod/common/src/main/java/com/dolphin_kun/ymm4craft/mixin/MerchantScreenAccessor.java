package com.dolphin_kun.ymm4craft.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
    @Accessor("shopItem")
    int ymm4craft$getShopItem();

    @Accessor("shopItem")
    void ymm4craft$setShopItem(int value);

    @Accessor("scrollOff")
    int ymm4craft$getScrollOff();

    @Accessor("scrollOff")
    void ymm4craft$setScrollOff(int value);
}
