package com.dolphin_kun.ymm4craft.feature.replay;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
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
        switch (packet) {
            case ClientboundSetHealthPacket health -> {
                food = health.getFood();
                saturation = health.getSaturation();
                hasFood = true;
            }
            case ClientboundSetExperiencePacket xp -> {
                xpProgress = xp.getExperienceProgress();
                xpTotal = xp.getTotalExperience();
                xpLevel = xp.getExperienceLevel();
            }
            case ClientboundContainerSetContentPacket content -> {
                if (content.containerId() != 0) return true;
                menuItems = new ArrayList<>(content.items().stream().map(ItemStack::copy).toList());
                stateId = content.stateId();
            }
            case ClientboundContainerSetSlotPacket slot -> {
                if (slot.getContainerId() != 0 || menuItems == null) return true;
                if (slot.getSlot() >= 0 && slot.getSlot() < menuItems.size()) menuItems.set(slot.getSlot(), slot.getItem().copy());
                stateId = slot.getStateId();
            }
            case ClientboundSetHeldSlotPacket held -> selectedSlot = held.slot();
            case ClientboundSetPlayerInventoryPacket inventory -> {
                if (menuItems == null) return true;
                int s = inventory.slot();
                int menuSlot = s >= 0 && s < 9 ? 36 + s : s >= 9 && s < 36 ? s : -1;
                if (menuSlot >= 0 && menuSlot < menuItems.size()) menuItems.set(menuSlot, inventory.contents().copy());
            }
            default -> {
                return false;
            }
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
        if (selectedSlot >= 0) player.getInventory().setSelectedSlot(selectedSlot);
        if (camera != null) camera.setExperienceValues(xpProgress, xpTotal, xpLevel);
    }
}
