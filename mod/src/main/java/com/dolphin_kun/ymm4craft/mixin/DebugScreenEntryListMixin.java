package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

@Mixin(DebugScreenEntryList.class)
public abstract class DebugScreenEntryListMixin {
    @Inject(method = "isCurrentlyEnabled", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noDebugEntry(Identifier location, CallbackInfoReturnable<Boolean> cir) {
        if (Ymm4Craft.isActive()) cir.setReturnValue(false);
    }

    @Inject(method = "isOverlayVisible", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noOverlay(CallbackInfoReturnable<Boolean> cir) {
        if (Ymm4Craft.isActive()) cir.setReturnValue(false);
    }

    @Inject(method = "getCurrentlyEnabled", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noEntries(CallbackInfoReturnable<Collection<Identifier>> cir) {
        if (Ymm4Craft.isActive()) cir.setReturnValue(List.of());
    }
}
