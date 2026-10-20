package com.dolphin_kun.ymm4craft.feature.replay;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.item.ItemStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ReplayScreenState {
    private ClientboundOpenScreenPacket open;
    private ClientboundMerchantOffersPacket offers;
    private int merchantSelection = -1;
    private int merchantScroll;
    private List<ItemStack> items;
    private int stateId;
    private final Map<Integer, Integer> data = new HashMap<>();
    private ItemStack carried = ItemStack.EMPTY;
    private boolean inventoryOpen;
    private float cursorX = Float.NaN;
    private float cursorY = Float.NaN;
    private float oldCursorX = Float.NaN;
    private float oldCursorY = Float.NaN;
    private boolean cursorFromCenter;
    private long revision;

    void reset() {
        open = null;
        offers = null;
        merchantSelection = -1;
        merchantScroll = 0;
        items = null;
        data.clear();
        carried = ItemStack.EMPTY;
        inventoryOpen = false;
        cursorX = cursorY = oldCursorX = oldCursorY = Float.NaN;
        revision++;
    }

    ReplayScreenState copy() {
        ReplayScreenState c = new ReplayScreenState();
        c.open = open;
        c.offers = offers;
        c.merchantSelection = merchantSelection;
        c.merchantScroll = merchantScroll;
        c.items = items == null ? null : new ArrayList<>(items.stream().map(ItemStack::copy).toList());
        c.stateId = stateId;
        c.data.putAll(data);
        c.carried = carried.copy();
        c.inventoryOpen = inventoryOpen;
        c.cursorX = c.oldCursorX = cursorX;
        c.cursorY = c.oldCursorY = cursorY;
        c.cursorFromCenter = cursorFromCenter;
        return c;
    }

    boolean capture(Packet<?> packet) {
        switch (packet) {
            case ClientboundOpenScreenPacket o -> {
                open = o;
                items = null;
                data.clear();
                offers = null;
                merchantSelection = -1;
                merchantScroll = 0;
            }
            case ClientboundMerchantOffersPacket o -> {
                if (open == null || o.getContainerId() != open.getContainerId()) return true;
                offers = o;
            }
            case ClientboundContainerSetContentPacket content -> {
                if (open == null || content.getContainerId() != open.getContainerId()) return false;
                items = new ArrayList<>(content.getItems().stream().map(ItemStack::copy).toList());
                stateId = content.getStateId();
                carried = content.getCarriedItem().copy();
            }
            case ClientboundContainerSetSlotPacket slot when slot.getContainerId() == -1 -> carried = slot.getItem().copy();
            case ClientboundContainerSetSlotPacket slot -> {
                if (open == null || slot.getContainerId() != open.getContainerId()) return false;
                if (items != null && slot.getSlot() >= 0 && slot.getSlot() < items.size()) items.set(slot.getSlot(), slot.getItem().copy());
                stateId = slot.getStateId();
            }
            case ClientboundContainerSetDataPacket d -> {
                if (open == null || d.getContainerId() != open.getContainerId()) return true;
                data.put(d.getId(), d.getValue());
            }
            case ClientboundContainerClosePacket close -> {
                if (open != null && close.getContainerId() == open.getContainerId()) {
                    open = null;
                    items = null;
                    data.clear();
                }
            }
            default -> {
                return false;
            }
        }
        revision++;
        return true;
    }

    void captureInventory(byte[] payload) {
        inventoryOpen = payload.length > 0 && payload[0] != 0;
        revision++;
    }

    void captureMerchant(byte[] payload) {
        if (payload.length < 8) return;
        ByteBuffer buf = ByteBuffer.wrap(payload);
        merchantSelection = buf.getInt();
        merchantScroll = buf.getInt();
        revision++;
    }

    void captureCursor(byte[] payload, boolean fromCenter) {
        if (payload.length < 8) return;
        cursorFromCenter = fromCenter;
        ByteBuffer buf = ByteBuffer.wrap(payload);
        cursorX = buf.getFloat();
        cursorY = buf.getFloat();
    }

    void tick() {
        oldCursorX = cursorX;
        oldCursorY = cursorY;
    }

    float[] cursor(float partial) {
        if (Float.isNaN(cursorX)) return null;
        float x = Float.isNaN(oldCursorX) ? cursorX : oldCursorX + (cursorX - oldCursorX) * partial;
        float y = Float.isNaN(oldCursorY) ? cursorY : oldCursorY + (cursorY - oldCursorY) * partial;
        return new float[]{x, y};
    }

    ClientboundOpenScreenPacket open() {
        return open;
    }

    ClientboundMerchantOffersPacket offers() {
        return offers;
    }

    int merchantSelection() {
        return merchantSelection;
    }

    int merchantScroll() {
        return merchantScroll;
    }

    List<ItemStack> items() {
        return items;
    }

    int stateId() {
        return stateId;
    }

    Map<Integer, Integer> data() {
        return data;
    }

    ItemStack carried() {
        return carried;
    }

    boolean inventoryOpen() {
        return inventoryOpen;
    }

    boolean cursorFromCenter() {
        return cursorFromCenter;
    }

    long revision() {
        return revision;
    }
}
