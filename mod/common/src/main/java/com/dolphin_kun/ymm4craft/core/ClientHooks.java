package com.dolphin_kun.ymm4craft.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ClientHooks {
    private static final List<Consumer<Minecraft>> endTick = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Screen>> screenInit = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Minecraft>> join = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Minecraft>> stopping = new CopyOnWriteArrayList<>();

    private ClientHooks() {
    }

    public static void onEndTick(Consumer<Minecraft> listener) {
        endTick.add(listener);
    }

    public static void onScreenInit(Consumer<Screen> listener) {
        screenInit.add(listener);
    }

    public static void onJoin(Consumer<Minecraft> listener) {
        join.add(listener);
    }

    public static void onStopping(Consumer<Minecraft> listener) {
        stopping.add(listener);
    }

    public static void fireEndTick(Minecraft mc) {
        endTick.forEach(l -> l.accept(mc));
    }

    public static void fireScreenInit(Screen screen) {
        screenInit.forEach(l -> l.accept(screen));
    }

    public static void fireJoin(Minecraft mc) {
        join.forEach(l -> l.accept(mc));
    }

    public static void fireStopping(Minecraft mc) {
        stopping.forEach(l -> l.accept(mc));
    }
}
