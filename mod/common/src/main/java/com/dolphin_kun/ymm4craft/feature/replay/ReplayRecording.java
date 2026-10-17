package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ModConfig;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import com.dolphin_kun.ymm4craft.mixin.MerchantScreenAccessor;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class ReplayRecording {
    private final Channel channel;
    private final Path folder;
    private final String name;
    private final JsonObject info;
    private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final Thread writerThread;
    private static final Object END = new Object();
    volatile boolean finished;

    private volatile long tickMs;
    private volatile long lastTickNanos;
    private volatile int lastWrittenMs;

    private boolean ready;
    private int readyMs = -1;
    private LocalPlayer spawnedFor;
    private Vec3 lastPos;
    private float lastYRot, lastXRot, lastHeadRot;
    private int lastSelectedSlot = -1;
    private int lastContainerId;
    private boolean lastInventoryOpen;
    private float lastCursorX = Float.NaN;
    private float lastCursorY = Float.NaN;
    private int slotsOfContainer = -1;
    private final List<ItemStack> lastSlots = new ArrayList<>();
    private ItemStack lastCarried = ItemStack.EMPTY;
    private long lastMerchant = Long.MIN_VALUE;
    private final Map<EquipmentSlot, ItemStack> lastEquipment = new EnumMap<>(EquipmentSlot.class);
    private long lastInfoWrite;

    private record Item(int timeMs, byte kind, byte[] data) {
    }

    private ReplayRecording(Channel channel, Path folder, String name, JsonObject info, ReplayWriter writer) {
        this.channel = channel;
        this.folder = folder;
        this.name = name;
        this.info = info;
        writerThread = new Thread(() -> writeLoop(writer), "YMM4Craft-ReplayWriter");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    static ReplayRecording start(Channel channel, boolean singleplayer) {
        Minecraft mc = Minecraft.getInstance();
        Path folder = ReplayFiles.folder(mc.gameDirectory.toPath());
        try {
            Files.createDirectories(folder);
            String base = LocalDateTime.now().format(ReplayRecorder.FILE_NAME);
            String name = base;
            for (int i = 2; Files.exists(ReplayFiles.dataFile(folder, name)); i++) name = base + "_" + i;
            JsonObject info = new JsonObject();
            info.addProperty("format", 2);
            info.addProperty("selfSounds", true);
            info.addProperty("protocol", SharedConstants.getProtocolVersion());
            info.addProperty("gameVersion", SharedConstants.getCurrentVersion().name());
            info.addProperty("startedAt", LocalDateTime.now().toString());
            info.addProperty("singleplayer", singleplayer);
            info.addProperty("player", mc.getUser().getName());
            info.addProperty("playerUuid", String.valueOf(mc.getUser().getProfileId()));
            info.addProperty("world", worldName(mc, singleplayer));
            ReplayFiles.writeInfo(folder, name, info);
            ReplayRecording r = new ReplayRecording(channel, folder, name, info, new ReplayWriter(ReplayFiles.dataFile(folder, name)));
            ReplayRecorder.active = r;
            Ymm4Craft.LOGGER.info("リプレイの録画を始めました: {}", name);
            return r;
        } catch (IOException e) {
            Ymm4Craft.LOGGER.warn("リプレイのファイルを作れませんでした", e);
            return null;
        }
    }

    private static String worldName(Minecraft mc, boolean singleplayer) {
        try {
            if (singleplayer && mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getWorldData().getLevelName();
            if (mc.getCurrentServer() != null) return mc.getCurrentServer().name;
        } catch (Throwable ignored) {
        }
        return "";
    }

    int timeMs() {
        long last = lastTickNanos;
        if (last == 0) return 0;
        long within = Math.clamp((System.nanoTime() - last) / 1_000_000L, 0, 49);
        return (int) Math.min(Integer.MAX_VALUE, tickMs + within);
    }

    void write(int timeMs, byte kind, byte[] data) {
        if (finished) return;
        int t = Math.max(timeMs, lastWrittenMs);
        lastWrittenMs = t;
        queue.add(new Item(t, kind, data));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void writeSynthetic(Packet<?> packet) {
        if (finished) return;
        ProtocolInfo<?> info = ReplayRecorder.protocolInfoOf(channel.pipeline());
        if (info == null || info.id() != ConnectionProtocol.PLAY) return;
        ByteBuf buf = Unpooled.buffer();
        try {
            ((StreamCodec) info.codec()).encode(buf, packet);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            write(timeMs(), ReplayFiles.PLAY, bytes);
        } finally {
            buf.release();
        }
    }

    void onClientTick(Minecraft mc) {
        if (finished) return;
        if (mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        if (lastTickNanos == 0) {
            lastTickNanos = now;
        } else if (!mc.isPaused()) {
            tickMs += 50;
            lastTickNanos = now;
        } else {
            lastTickNanos = now - 49_000_000L;
        }

        if (!ready && !(mc.gui.screen() instanceof LevelLoadingScreen)) {
            ready = true;
            readyMs = timeMs();
            write(readyMs, ReplayFiles.MARK_READY, new byte[0]);
            info.addProperty("readyMs", readyMs);
            if (ModConfig.get().notifyRecording) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.translatable("ymm4craft.replay.recording", name));
            }
        }
        recordSelf(mc.player);
        recordScreen(mc);

        if (ready && now - lastInfoWrite > TimeUnit.SECONDS.toNanos(10)) {
            lastInfoWrite = now;
            writeInfoAsync();
        }
    }

    private void recordScreen(Minecraft mc) {
        int id = mc.player.containerMenu.containerId;
        if (lastContainerId != 0 && id != lastContainerId) writeSynthetic(new ClientboundContainerClosePacket(lastContainerId));
        lastContainerId = id;
        Screen screen = mc.gui.screen();
        boolean inventory = screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
        if (inventory != lastInventoryOpen) {
            lastInventoryOpen = inventory;
            write(timeMs(), ReplayFiles.INVENTORY_SCREEN, new byte[]{(byte) (inventory ? 1 : 0)});
        }
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            slotsOfContainer = -1;
            return;
        }
        recordSlots(containerScreen.getMenu());
        if (screen instanceof MerchantScreen merchant) {
            MerchantScreenAccessor accessor = (MerchantScreenAccessor) merchant;
            int selection = accessor.ymm4craft$getShopItem();
            int scroll = accessor.ymm4craft$getScrollOff();
            long key = ((long) selection << 32) | (scroll & 0xFFFFFFFFL);
            if (key != lastMerchant) {
                lastMerchant = key;
                write(timeMs(), ReplayFiles.MERCHANT_SELECTION, ByteBuffer.allocate(8).putInt(selection).putInt(scroll).array());
            }
        }
        Window window = mc.getWindow();
        float x = (float) (mc.mouseHandler.getScaledXPos(window) - window.getGuiScaledWidth() / 2.0);
        float y = (float) (mc.mouseHandler.getScaledYPos(window) - window.getGuiScaledHeight() / 2.0);
        if (x == lastCursorX && y == lastCursorY) return;
        lastCursorX = x;
        lastCursorY = y;
        write(timeMs(), ReplayFiles.CURSOR_GUI, ByteBuffer.allocate(8).putFloat(x).putFloat(y).array());
    }

    private void recordSlots(AbstractContainerMenu menu) {
        boolean fresh = slotsOfContainer != menu.containerId || lastSlots.size() != menu.slots.size();
        if (fresh) {
            slotsOfContainer = menu.containerId;
            lastSlots.clear();
            for (Slot slot : menu.slots) lastSlots.add(slot.getItem().copy());
            lastCarried = menu.getCarried().copy();
            lastMerchant = Long.MIN_VALUE;
            return;
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack now = menu.slots.get(i).getItem();
            if (ItemStack.matches(now, lastSlots.get(i))) continue;
            lastSlots.set(i, now.copy());
            writeSynthetic(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), i, now.copy()));
        }
        ItemStack carried = menu.getCarried();
        if (!ItemStack.matches(carried, lastCarried)) {
            lastCarried = carried.copy();
            writeSynthetic(new ClientboundSetCursorItemPacket(carried.copy()));
        }
    }

    private void recordSelf(LocalPlayer p) {
        int id = p.getId();
        if (p != spawnedFor) {
            spawnedFor = p;
            lastEquipment.clear();
            lastSelectedSlot = -1;
            writeSynthetic(new ClientboundAddEntityPacket(id, p.getUUID(), p.getX(), p.getY(), p.getZ(),
                    p.getXRot(), p.getYRot(), EntityTypes.PLAYER, 0, Vec3.ZERO, p.getYHeadRot()));
            List<SynchedEntityData.DataValue<?>> data = p.getEntityData().getNonDefaultValues();
            if (data != null && !data.isEmpty()) writeSynthetic(new ClientboundSetEntityDataPacket(id, data));
            lastPos = p.position();
            lastYRot = p.getYRot();
            lastXRot = p.getXRot();
            lastHeadRot = p.getYHeadRot();
        } else if (!p.position().equals(lastPos) || p.getYRot() != lastYRot || p.getXRot() != lastXRot) {
            writeSynthetic(new ClientboundTeleportEntityPacket(id, PositionMoveRotation.of(p), Set.of(), p.onGround()));
            lastPos = p.position();
            lastYRot = p.getYRot();
            lastXRot = p.getXRot();
        }
        if (p.getYHeadRot() != lastHeadRot) {
            lastHeadRot = p.getYHeadRot();
            writeSynthetic(new ClientboundRotateHeadPacket(p, (byte) Mth.packDegrees(p.getYHeadRot())));
        }

        int selected = p.getInventory().getSelectedSlot();
        if (selected != lastSelectedSlot) {
            lastSelectedSlot = selected;
            writeSynthetic(new ClientboundSetHeldSlotPacket(selected));
        }

        List<Pair<EquipmentSlot, ItemStack>> changed = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack now = p.getItemBySlot(slot);
            ItemStack last = lastEquipment.getOrDefault(slot, ItemStack.EMPTY);
            if (!ItemStack.matches(now, last)) {
                changed.add(Pair.of(slot, now.copy()));
                lastEquipment.put(slot, now.copy());
            }
        }
        if (!changed.isEmpty()) writeSynthetic(new ClientboundSetEquipmentPacket(id, changed));
    }

    private void writeInfoAsync() {
        info.addProperty("durationMs", Math.max(0, lastWrittenMs - Math.max(0, readyMs)));
        JsonObject copy = info.deepCopy();
        queue.add(copy);
    }

    synchronized void finish() {
        if (finished) return;
        writeInfoAsync();
        finished = true;
        queue.add(END);
        if (ReplayRecorder.active == this) ReplayRecorder.active = null;
        Ymm4Craft.LOGGER.info("リプレイの録画を終えました: {}", name);
        if (ModConfig.get().notifyRecording && ready) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(Component.translatable("ymm4craft.replay.saved", name)));
        }
    }

    private void writeLoop(ReplayWriter writer) {
        try (writer) {
            long lastFlush = System.nanoTime();
            while (true) {
                Object o = queue.poll(1, TimeUnit.SECONDS);
                if (o == END) break;
                if (o instanceof Item item) {
                    writer.write(item.timeMs, item.kind, item.data, 0, item.data.length);
                } else if (o instanceof JsonObject json) {
                    json.add(ReplayBlockIndex.KEY, ReplayBlockIndex.toJson(writer.blocks()));
                    ReplayFiles.writeInfo(folder, name, json);
                }
                if (System.nanoTime() - lastFlush > TimeUnit.SECONDS.toNanos(3)) {
                    writer.flush();
                    lastFlush = System.nanoTime();
                }
            }
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.warn("リプレイのファイルの書き込みに失敗しました", t);
            finished = true;
            if (ReplayRecorder.active == this) ReplayRecorder.active = null;
        }
    }
}
