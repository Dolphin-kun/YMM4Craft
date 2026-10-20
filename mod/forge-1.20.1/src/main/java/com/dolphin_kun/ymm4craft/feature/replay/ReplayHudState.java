package com.dolphin_kun.ymm4craft.feature.replay;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class ReplayHudState {
    private int food = 20;
    private float saturation = 5;
    private boolean hasFood;
    private float xpProgress;
    private int xpTotal;
    private int xpLevel;
    private int selectedSlot = -1;
    private List<ItemStack> menuItems;
    private int stateId;
    private boolean dirty;
    private Player appliedTo;

    void reset() {
        food = 20;
        saturation = 5;
        hasFood = false;
        xpProgress = 0;
        xpTotal = 0;
        xpLevel = 0;
        selectedSlot = -1;
        menuItems = null;
        dirty = false;
        appliedTo = null;
    }

    ReplayHudState copy() {
        ReplayHudState c = new ReplayHudState();
        c.food = food;
        c.saturation = saturation;
        c.hasFood = hasFood;
        c.xpProgress = xpProgress;
        c.xpTotal = xpTotal;
        c.xpLevel = xpLevel;
        c.selectedSlot = selectedSlot;
        c.menuItems = menuItems == null ? null : new ArrayList<>(menuItems.stream().map(ItemStack::copy).toList());
        c.stateId = stateId;
        c.dirty = true;
        return c;
    }

    boolean capture(Packet<?> packet) {
        if (packet instanceof ClientboundSetHealthPacket health) {
            food = health.getFood();
            saturation = health.getSaturation();
            hasFood = true;
        } else if (packet instanceof ClientboundSetExperiencePacket xp) {
            xpProgress = xp.getExperienceProgress();
            xpTotal = xp.getTotalExperience();
            xpLevel = xp.getExperienceLevel();
        } else if (packet instanceof ClientboundContainerSetContentPacket content) {
            if (content.getContainerId() != 0) return true;
            menuItems = new ArrayList<>(content.getItems().stream().map(ItemStack::copy).toList());
            stateId = content.getStateId();
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot && slot.getContainerId() == -2) {
            if (menuItems == null) return true;
            int s = slot.getSlot();
            int menuSlot = s >= 0 && s < 9 ? 36 + s : s >= 9 && s < 36 ? s : -1;
            if (menuSlot >= 0 && menuSlot < menuItems.size()) menuItems.set(menuSlot, slot.getItem().copy());
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            if (slot.getContainerId() != 0 || menuItems == null) return true;
            if (slot.getSlot() >= 0 && slot.getSlot() < menuItems.size()) menuItems.set(slot.getSlot(), slot.getItem().copy());
            stateId = slot.getStateId();
        } else if (packet instanceof ClientboundSetCarriedItemPacket held) {
            selectedSlot = held.getSlot();
        } else {
            return false;
        }
        dirty = true;
        return true;
    }

    void applyTo(Player player, LocalPlayer camera) {
        if (appliedTo != player) {
            appliedTo = player;
            dirty = true;
        }
        if (!dirty) return;
        dirty = false;
        if (hasFood) {
            player.getFoodData().setFoodLevel(food);
            player.getFoodData().setSaturation(saturation);
        }
        if (menuItems != null) player.inventoryMenu.initializeContents(stateId, menuItems, ItemStack.EMPTY);
        if (selectedSlot >= 0) player.getInventory().selected = selectedSlot;
        if (camera != null) camera.setExperienceValues(xpProgress, xpTotal, xpLevel);
    }
}
