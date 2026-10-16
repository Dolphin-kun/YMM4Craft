package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.CommandException;
import com.dolphin_kun.ymm4craft.mixin.BackupConfirmScreenAccessor;
import com.dolphin_kun.ymm4craft.mixin.ConfirmScreenAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.FileFixerAbortedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.world.level.GameType;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class WorldCommands {
    private static final Pattern WORLD_NAME = Pattern.compile("[^/\\\\:*?\"<>|\\p{Cntrl}]{1,128}");
    private static final int READY_DELAY_TICKS = 10;
    private static final int MAX_OPEN_RETRIES = 2;

    private static String openedWorld;
    private static int readyCountdown = -1;
    private static String pendingCreatedWorld;
    private static int openRetriesLeft;
    private static Screen handledPrompt;
    private static final List<CompletableFuture<Void>> waitingForStartup = new ArrayList<>();
    private static final List<Runnable> closeHooks = new ArrayList<>();

    private WorldCommands() {
    }

    static void register(CommandRegistry registry) {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!Ymm4Craft.isActive()) return;
            advanceTempWorldPrompts(mc);
            notifyWorldReadyWhenShown(mc);
            if (!waitingForStartup.isEmpty() && isStartupFinished(mc)) {
                List<CompletableFuture<Void>> ready = new ArrayList<>(waitingForStartup);
                waitingForStartup.clear();
                ready.forEach(f -> f.complete(null));
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            if (!Ymm4Craft.isActive()) return;
            readyCountdown = READY_DELAY_TICKS;
        });

        registry.register("core.listWorlds", p -> listWorlds());
        registry.register("core.openWorld", WorldCommands::openWorld);
        registry.register("core.createWorld", WorldCommands::createWorld);
        registry.register("core.closeWorld", p -> CommandRegistry.onMainThread(() -> {
            closeWorld();
            return null;
        }));
    }

    static void onSessionEnded() {
        closeWorld();
        readyCountdown = -1;
        pendingCreatedWorld = null;
        handledPrompt = null;
        waitingForStartup.forEach(f -> f.cancel(false));
        waitingForStartup.clear();
    }

    public static String openedWorld() {
        return openedWorld;
    }

    public static void markOpened(String name) {
        openedWorld = name;
    }

    public static void addCloseHook(Runnable hook) {
        closeHooks.add(hook);
    }

    public static CompletableFuture<JsonElement> closeWorldThen(Supplier<JsonElement> open) {
        return CommandRegistry.onMainThread(() -> {
                    closeWorld();
                    return null;
                })
                .thenCompose(ignored -> CommandRegistry.onMainThread(WorldCommands::whenStartupFinished))
                .thenCompose(f -> f)
                .thenCompose(ignored -> CommandRegistry.onMainThread(open::get))
                .exceptionallyCompose(CommandRegistry::unwrapFailure);
    }

    private static void notifyWorldReadyWhenShown(Minecraft mc) {
        if (readyCountdown < 0) return;
        if (mc.level == null || mc.gui.screen() != null) return;
        if (readyCountdown-- > 0) return;
        readyCountdown = -1;
        if (pendingCreatedWorld != null) {
            String created = pendingCreatedWorld;
            pendingCreatedWorld = null;
            Ymm4Craft.LOGGER.info("ワールド「{}」を作りました。コピーを開き直します", created);
            openRetriesLeft = MAX_OPEN_RETRIES;
            copyAndOpen(created).exceptionally(t -> {
                Ymm4Craft.LOGGER.warn("作ったワールドを開けませんでした", t);
                sendWorldFailed();
                return null;
            });
            return;
        }
        HostConnection c = Ymm4Craft.connection();
        if (c == null) return;
        JsonObject data = new JsonObject();
        data.addProperty("world", openedWorld);
        c.sendEvent("core.worldReady", data);
    }

    private static void sendWorldFailed() {
        HostConnection c = Ymm4Craft.connection();
        if (c != null) c.sendEvent("core.worldFailed", null);
    }

    private static boolean isStartupFinished(Minecraft mc) {
        return mc.gui.overlay() == null;
    }

    private static void advanceTempWorldPrompts(Minecraft mc) {
        if (openedWorld == null || mc.level != null) return;
        Screen screen = mc.gui.screen();
        if (screen == null || screen == handledPrompt) return;

        if (screen instanceof BackupConfirmScreen backup) {
            handledPrompt = screen;
            Ymm4Craft.LOGGER.info("一時ワールドのため、バックアップを作らずに読み込みを続けます");
            ((BackupConfirmScreenAccessor) backup).ymm4craft$getOnProceed().proceed(false, false);
        } else if (screen instanceof ConfirmScreen confirm
                && confirm.getTitle().getContents() instanceof TranslatableContents t
                && t.getKey().equals("upgradeWorld.done")) {
            handledPrompt = screen;
            Ymm4Craft.LOGGER.info("一時ワールドのアップグレードが終わったので、そのまま参加します");
            ((ConfirmScreenAccessor) confirm).ymm4craft$getCallback().accept(true);
        } else if (screen instanceof FileFixerAbortedScreen) {
            handledPrompt = screen;
            retryOpenAfterFileFixFailure(openedWorld);
        }
    }

    private static CompletableFuture<Void> whenStartupFinished() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (isStartupFinished(Minecraft.getInstance())) future.complete(null);
        else waitingForStartup.add(future);
        return future;
    }

    private static Path savesDir() {
        return Minecraft.getInstance().getLevelSource().getBaseDir();
    }

    private static Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    private static CompletableFuture<JsonElement> listWorlds() throws CommandException {
        try {
            JsonArray worlds = new JsonArray();
            for (String name : TempWorlds.listWorlds(savesDir())) worlds.add(name);
            JsonObject o = new JsonObject();
            o.add("worlds", worlds);
            return CompletableFuture.completedFuture(o);
        } catch (IOException e) {
            throw new CommandException("ワールドの一覧を取得できませんでした: " + e.getMessage());
        }
    }

    private static CompletableFuture<JsonElement> openWorld(CommandParams p) throws CommandException {
        String world = p.getString("world", WORLD_NAME);
        try {
            if (!TempWorlds.listWorlds(savesDir()).contains(world)) {
                throw new CommandException("ワールドが見つかりません: " + world);
            }
        } catch (IOException e) {
            throw new CommandException("ワールドの一覧を取得できませんでした: " + e.getMessage());
        }

        openRetriesLeft = MAX_OPEN_RETRIES;
        return copyAndOpen(world)
                .thenApply(ignored -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("world", world);
                    return (JsonElement) o;
                })
                .exceptionallyCompose(CommandRegistry::unwrapFailure);
    }

    private static CompletableFuture<JsonElement> createWorld(CommandParams p) throws CommandException {
        String name = p.getString("name", WORLD_NAME);
        String type = p.has("type") ? p.getChoice("type", Set.of("normal", "flat", "void")) : "normal";
        Minecraft mc = Minecraft.getInstance();
        return CommandRegistry.onMainThread(() -> {
                    closeWorld();
                    return null;
                })
                .thenCompose(ignored -> CommandRegistry.onMainThread(WorldCommands::whenStartupFinished))
                .thenCompose(f -> f)
                .thenCompose(ignored -> CommandRegistry.<JsonElement>onMainThread(() -> {
                    String folder;
                    try {
                        folder = FileUtil.findAvailableName(savesDir(), name, "");
                    } catch (IOException e) {
                        throw new RuntimeException(new CommandException("ワールドを作れませんでした: " + e.getMessage()));
                    }
                    LevelSettings settings = new LevelSettings(name, GameType.SPECTATOR,
                            LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
                    Function<HolderLookup.Provider, WorldDimensions> dimensions = switch (type) {
                        case "flat" -> WorldCommands::flatDimensions;
                        case "void" -> WorldCommands::voidDimensions;
                        default -> WorldPresets::createNormalWorldDimensions;
                    };
                    pendingCreatedWorld = folder;
                    openedWorld = folder;
                    mc.createWorldOpenFlows().createFreshLevel(folder, settings, WorldOptions.defaultWithRandomSeed(), dimensions, new TitleScreen());
                    JsonObject o = new JsonObject();
                    o.addProperty("world", folder);
                    return o;
                }))
                .exceptionallyCompose(CommandRegistry::unwrapFailure);
    }

    private static WorldDimensions flatDimensions(HolderLookup.Provider provider) {
        return provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
    }

    private static WorldDimensions voidDimensions(HolderLookup.Provider provider) {
        FlatLevelGeneratorSettings settings = FlatLevelGeneratorSettings.getDefault(provider.lookupOrThrow(Registries.BIOME),
                        provider.lookupOrThrow(Registries.STRUCTURE_SET), provider.lookupOrThrow(Registries.PLACED_FEATURE))
                .withBiomeAndLayers(List.of(new FlatLayerInfo(1, Blocks.AIR)), Optional.of(HolderSet.empty()),
                        provider.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID));
        return flatDimensions(provider).replaceOverworldGenerator(provider, new FlatLevelSource(settings));
    }

    private static CompletableFuture<Void> copyAndOpen(String world) {
        Minecraft mc = Minecraft.getInstance();
        return CommandRegistry.onMainThread(() -> {
                    closeWorld();
                    return null;
                })
                .thenApplyAsync(ignored -> {
                    try {
                        TempWorlds.copyToTemp(savesDir(), gameDir(), world);
                    } catch (IOException e) {
                        throw new RuntimeException(new CommandException("ワールドのコピーに失敗しました: " + e.getMessage()));
                    }
                    return null;
                }, Util.ioPool())
                .thenCompose(ignored -> CommandRegistry.onMainThread(WorldCommands::whenStartupFinished))
                .thenCompose(f -> f)
                .thenCompose(ignored -> CommandRegistry.<Void>onMainThread(() -> {
                    LevelStorageSource tempSource = LevelStorageSource.createDefault(TempWorlds.tempRoot(gameDir()));
                    openedWorld = world;
                    new WorldOpenFlows(mc, tempSource).openWorld(world, () -> {
                        openedWorld = null;
                        sendWorldFailed();
                    });
                    return null;
                }));
    }

    private static void retryOpenAfterFileFixFailure(String world) {
        if (openRetriesLeft-- <= 0) {
            Ymm4Craft.LOGGER.warn("一時ワールドのアップグレードに失敗しました。再試行の回数を超えたのであきらめます");
            sendWorldFailed();
            return;
        }
        Ymm4Craft.LOGGER.info("一時ワールドのアップグレードに失敗したので、コピーからやり直します");
        openedWorld = null;
        Minecraft.getInstance().setScreenAndShow(new TitleScreen());
        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
                .execute(() -> copyAndOpen(world).exceptionally(t -> {
                    Ymm4Craft.LOGGER.warn("ワールドを開き直せませんでした", t);
                    sendWorldFailed();
                    return null;
                }));
    }

    private static void closeWorld() {
        Minecraft mc = Minecraft.getInstance();
        for (Runnable hook : closeHooks) {
            try {
                hook.run();
            } catch (Throwable t) {
                Ymm4Craft.LOGGER.warn("ワールドを閉じるときの後始末に失敗しました", t);
            }
        }
        if (mc.level != null) {
            mc.disconnectWithProgressScreen();
        }
        openedWorld = null;
        TempWorlds.cleanupLeftovers(gameDir());
    }
}
