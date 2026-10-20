package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.HostConnection;
import com.dolphin_kun.ymm4craft.mixin.ChatComponentAccessor;
import com.dolphin_kun.ymm4craft.mixin.MerchantScreenAccessor;
import com.dolphin_kun.ymm4craft.mixin.MinecraftAccessor;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ReplayPlayback {
    public static final int FAST_FORWARD_PER_FRAME = 200;
    private static final int JUMP_AHEAD_TICKS = 20 * 60;
    private static final long EXPORT_IDLE_NANOS = 1_500_000_000L;
    private static final int BACKWARD_HOLD_TICKS = 40;
    private static final int BACKWARD_IGNORED_TICKS = 3;
    private static final long BACKWARD_SETTLE_NANOS = 800_000_000L;
    private static final long PROGRESS_INTERVAL_NANOS = 250_000_000L;
    private static final double COLLECT_SHARE = 0.85;
    private static final int PARTICLE_WINDOW_TICKS = 200;

    private static ReplayPlayback current;

    private final String name;
    private final ReplayInput input;
    private final boolean hasSelfSounds;
    private final List<ReplayEntry> prefix = new ArrayList<>();
    private final ReplaySnapshots snapshots = new ReplaySnapshots();
    private final ReplayHands hands = new ReplayHands();
    private ReplaySource reader;
    private ReplayEntry next;
    private EmbeddedChannel channel;
    private Connection connection;

    private int readyMs = -1;
    private int ticks;
    private int targetTicks = 1;
    private double targetTickTime;
    private final ReplayPacketRewriter rewriter = new ReplayPacketRewriter();
    private static volatile boolean pov;
    private static volatile boolean showHand = true;
    private static volatile boolean showScreens = true;
    private static volatile boolean showCursor = true;
    private static volatile boolean showChat;
    private long appliedChatRevision = -1;
    private int appliedChatOffset = Integer.MIN_VALUE;
    private static volatile String povTargetName = "";
    private Screen replayScreen;
    private long appliedScreenRevision = -1;

    private boolean jumpScheduled;
    private final ReplayAudioCache audio = new ReplayAudioCache();
    private ReplayAudioJob audioJob;
    private boolean exportMoving;
    private long lastUserSeekNanos = System.nanoTime();
    private double userSeconds;
    private double heldSeconds = -1;
    private boolean loadingSent;
    private boolean audioSent;
    private boolean particlesCleared;
    private long progressSentAt;
    private long heldAt;
    private boolean ended;
    private final List<Waiter> waiters = new ArrayList<>();

    private record Waiter(int ticks, CompletableFuture<Void> future) {
    }

    private ReplayPlayback(String name, ReplayInput input, boolean hasSelfSounds) {
        this.name = name;
        this.input = input;
        this.hasSelfSounds = hasSelfSounds;
    }

    public static boolean playsSoundsOf(Entity entity) {
        ReplayPlayback p = current;
        return p != null && !p.hasSelfSounds && Ymm4Craft.isActive() && p.isRecorded(entity);
    }

    public static boolean active() {
        return current != null && Ymm4Craft.isActive();
    }

    public static String currentName() {
        return current == null ? null : current.name;
    }

    public static void open(Path gameDir, String name) throws IOException {
        ReplayCatalog.Located located = ReplayCatalog.locate(gameDir, name);
        if (!ReplayCatalog.playable(located.info())) {
            JsonObject info = located.info();
            String version = info.has("gameVersion") ? info.get("gameVersion").getAsString() : "?";
            throw new IOException("このリプレイは別のバージョン（" + version + "）で録画されたため、再生できません");
        }
        close();
        JsonObject info = located.info();
        boolean selfSounds = info != null && info.has("selfSounds") && info.get("selfSounds").getAsBoolean();
        ReplayPlayback playback = new ReplayPlayback(name, located.input(), selfSounds);
        current = playback;
        try {
            playback.start();
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    public static void close() {
        ReplayPlayback p = current;
        if (p == null) return;
        current = null;
        p.shutdown();
        Minecraft.getInstance().gui.getChat().clearMessages(false);
        p.snapshots.clear();
        if (p.audioJob != null) p.audioJob.future().completeExceptionally(new IllegalStateException("リプレイを閉じました"));
        if (p.loadingSent) sendLoading(false, false);
        p.audioJob = null;
        p.exportMoving = false;
        List<Waiter> previous = List.copyOf(p.waiters);
        p.waiters.clear();
        previous.forEach(w -> w.future.complete(null));
    }

    public static CompletableFuture<Void> seek(double seconds, boolean exact) {
        ReplayPlayback p = current;
        if (p == null) return CompletableFuture.failedFuture(new IllegalStateException("リプレイを開いていません"));
        long now = System.nanoTime();
        p.userSeconds = seconds;
        if (p.audioJob != null && p.audioJob.exclusive()) return CompletableFuture.completedFuture(null);
        boolean moved = p.exportMoving;
        p.lastUserSeekNanos = now;
        p.exportMoving = false;
        p.heldSeconds = -1;
        int behind = p.ticks - ((int) Math.floor(Math.max(0, seconds) * 20.0) + 1);
        if (!exact && !moved && behind > 0 && behind <= BACKWARD_HOLD_TICKS && !p.jumpScheduled && p.readyMs >= 0) {
            p.hold(behind > BACKWARD_IGNORED_TICKS ? seconds : -1, now);
            return CompletableFuture.completedFuture(null);
        }
        return p.seekTo(seconds);
    }

    private void hold(double laterSeconds, long now) {
        List<Waiter> previous = List.copyOf(waiters);
        waiters.clear();
        previous.forEach(w -> w.future.complete(null));
        targetTicks = ticks;
        targetTickTime = ticks - 1;
        heldSeconds = laterSeconds;
        heldAt = now;
    }

    private void settleHeld() {
        if (heldSeconds < 0 || !waiters.isEmpty() || System.nanoTime() - heldAt < BACKWARD_SETTLE_NANOS) return;
        double seconds = heldSeconds;
        heldSeconds = -1;
        seekTo(seconds);
    }

    public static void onSoundPlayed(SoundInstance sound) {
        ReplayPlayback p = current;
        if (p != null) p.audio.onPlay(sound);
    }

    public static boolean skipsParticles() {
        ReplayPlayback p = current;
        return p != null && p.skippingParticles() && Ymm4Craft.isActive();
    }

    private boolean skippingParticles() {
        return !exportMoving && readyMs >= 0 && targetTicks - ticks > PARTICLE_WINDOW_TICKS;
    }

    public static boolean hidesFrames() {
        ReplayPlayback p = current;
        return p != null && Ymm4Craft.isActive()
                && (p.exportMoving || p.jumpScheduled || (Minecraft.getInstance().screen instanceof LevelLoadingScreen || Minecraft.getInstance().screen instanceof ReceivingLevelScreen));
    }

    public static CompletableFuture<JsonObject> exportAudio(double from, double to, double[][] camera, Path out, boolean exclusive) {
        ReplayPlayback p = current;
        if (p == null) return CompletableFuture.failedFuture(new IllegalStateException("リプレイを開いていません"));
        if (p.audioJob != null && (p.audioJob.exclusive() || !exclusive)) {
            return CompletableFuture.failedFuture(new IllegalStateException("音を書き出している途中です"));
        }
        if (p.audioJob != null) p.audioJob.future().completeExceptionally(new IllegalStateException("ボタンでの書き出しを先にしました"));
        int after = (int) Math.floor(Math.max(0, from) * 20.0) + 1;
        int last = Math.max(after, (int) Math.floor(Math.max(0, to) * 20.0) + 1);
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        p.audioJob = new ReplayAudioJob(after, last, from, camera, povTargetName, out, exclusive, future, System.nanoTime());
        return future;
    }

    private static void sendEvent(String name, JsonObject data) {
        HostConnection c = Ymm4Craft.connection();
        if (c != null) c.sendEvent(name, data);
    }

    private static void sendLoading(boolean loading, boolean audio) {
        JsonObject data = new JsonObject();
        data.addProperty("loading", loading);
        data.addProperty("audio", audio);
        sendEvent("replay.loading", data);
    }

    private static void sendProgress(double progress, boolean waiting) {
        JsonObject data = new JsonObject();
        data.addProperty("progress", Math.clamp(progress, 0.0, 1.0));
        data.addProperty("waiting", waiting);
        sendEvent("replay.audioProgress", data);
    }

    private void reportLoading(Minecraft mc) {
        boolean audioOnly = audioJob != null && audioJob.exclusive();
        boolean loading = audioOnly || jumpScheduled || (mc.screen instanceof LevelLoadingScreen || mc.screen instanceof ReceivingLevelScreen);
        if (loading == loadingSent && audioOnly == audioSent) return;
        loadingSent = loading;
        audioSent = audioOnly;
        sendLoading(loading, audioOnly);
    }

    private void reportProgress(ReplayAudioJob job, boolean waiting) {
        long now = System.nanoTime();
        if (now - progressSentAt < PROGRESS_INTERVAL_NANOS) return;
        progressSentAt = now;
        int total = Math.max(1, job.last() - job.after());
        sendProgress(COLLECT_SHARE * audio.coveredCount(job.after() + 1, job.last()) / total, waiting);
    }

    private void driveExport() {
        ReplayAudioJob job = audioJob;
        if (job == null) return;
        boolean idle = job.exclusive() || waiters.isEmpty() && System.nanoTime() - lastUserSeekNanos >= EXPORT_IDLE_NANOS;
        reportProgress(job, !idle);
        if (!idle) return;
        int missing = audio.firstMissing(job.after() + 1, job.last());
        if (missing >= 0 && audio.collectingAt(ticks) && ticks >= audio.missingRunEnd(missing, job.last())) {
            audio.interrupt();
            missing = audio.firstMissing(job.after() + 1, job.last());
        }
        if (missing < 0) {
            finishExport(job);
            return;
        }
        exportMoving = true;
        int runEnd = audio.missingRunEnd(missing, job.last());
        boolean inRun = audio.collectingAt(ticks) && ticks >= missing;
        if (!inRun && (ticks > missing - 1 || shouldJump(missing - 1))) {
            targetTicks = missing - 1;
            targetTickTime = Math.max(0, missing - 2);
            if (!jumpScheduled) scheduleJump();
            return;
        }
        targetTicks = runEnd;
        targetTickTime = runEnd - 1;
    }

    private void finishExport(ReplayAudioJob job) {
        audioJob = null;
        boolean moved = exportMoving;
        Minecraft mc = Minecraft.getInstance();
        List<ReplaySoundEvent> events = audio.eventsBetween(job.after(), job.last());
        int length = job.last() - job.after();
        ReplayListenerPath listener = job.camera() == null
                ? ReplayListenerPath.recordedPlayer(audio.listenerBetween(job.after(), job.last(), job.listenerName()))
                : ReplayListenerPath.camera(job.from(), job.camera());
        CompletableFuture.supplyAsync(() -> {
            try {
                long[] sentAt = {0};
                new ReplayAudioMixer(mc.getResourceManager()).mix(events, listener, length, job.out(), f -> {
                    long now = System.nanoTime();
                    if (now - sentAt[0] < PROGRESS_INTERVAL_NANOS) return;
                    sentAt[0] = now;
                    sendProgress(COLLECT_SHARE + (1 - COLLECT_SHARE) * f, false);
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            JsonObject result = new JsonObject();
            result.addProperty("start", (job.after() - 1) / 20.0);
            result.addProperty("seconds", length / 20.0);
            result.addProperty("sampleRate", ReplayAudioMixer.SAMPLE_RATE);
            result.addProperty("sounds", events.size());
            Ymm4Craft.LOGGER.info("リプレイの音を書き出しました（{}個の音, {}秒, {}ms）", events.size(), length / 20.0, (System.nanoTime() - job.startedNanos()) / 1_000_000);
            return result;
        }, Util.backgroundExecutor()).whenComplete((r, t) -> {
            if (t != null) job.future().completeExceptionally(t);
            else job.future().complete(r);
        });
        if (moved) seekTo(userSeconds).whenComplete((r, t) -> exportMoving = false);
    }

    private CompletableFuture<Void> seekTo(double seconds) {
        ReplayPlayback p = this;
        double t = Math.max(0, seconds) * 20.0;
        int target = (int) Math.floor(t) + 1;
        p.targetTickTime = t;
        List<Waiter> previous = List.copyOf(p.waiters);
        p.waiters.clear();
        previous.forEach(w -> w.future.complete(null));
        p.targetTicks = target;
        if (!p.jumpScheduled && p.shouldJump(target)) p.scheduleJump();
        CompletableFuture<Void> future = new CompletableFuture<>();
        p.waiters.add(new Waiter(target, future));
        p.completeWaiters();
        return future;
    }

    public static Entity recordedPlayer() {
        ReplayPlayback p = current;
        Minecraft mc = Minecraft.getInstance();
        if (p == null || mc.level == null) return null;
        return mc.level.getEntity(p.rewriter.recordedPlayerId());
    }

    public static boolean isPovTarget(Entity entity) {
        ReplayPlayback p = current;
        return p != null && entity == p.povTarget(Minecraft.getInstance());
    }

    public static String recordedPlayerName() {
        return recordedPlayer() instanceof Player player ? player.getGameProfile().getName() : "";
    }

    public static Player playerNamed(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.level == null) return null;
        for (Player player : mc.level.players()) {
            if (player != mc.player && player.getGameProfile().getName().equals(name)) return player;
        }
        return null;
    }

    public static List<String> playerNames() {
        Minecraft mc = Minecraft.getInstance();
        List<String> names = new ArrayList<>();
        if (mc.level == null) return names;
        Entity recorded = recordedPlayer();
        for (Player player : mc.level.players()) {
            if (player == mc.player || player == recorded) continue;
            names.add(player.getGameProfile().getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private Player povTarget(Minecraft mc) {
        if (mc.level == null) return null;
        String name = povTargetName;
        if (!name.isEmpty()) {
            for (Player player : mc.level.players()) {
                if (player != mc.player && player.getGameProfile().getName().equals(name)) return player;
            }
        }
        return mc.level.getEntity(rewriter.recordedPlayerId()) instanceof Player recorded ? recorded : null;
    }

    private boolean isRecorded(Entity entity) {
        return entity != null && entity.getId() == rewriter.recordedPlayerId();
    }

    public static double[] povCamera() {
        ReplayPlayback p = current;
        Entity e = p == null ? null : p.povTarget(Minecraft.getInstance());
        if (e == null) return null;
        float pt = current.currentPartial();
        Vec3 eye = e.getEyePosition(pt);
        float yaw = Mth.rotLerp(pt, e instanceof LivingEntity l ? l.yHeadRotO : e.yRotO,
                e instanceof LivingEntity l2 ? l2.yHeadRot : e.getYRot());
        float pitch = Mth.lerp(pt, e.xRotO, e.getXRot());
        return new double[]{eye.x, eye.y, eye.z, Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90, 90)};
    }

    public static Float partialTick() {
        ReplayPlayback p = current;
        return p != null && p.readyMs >= 0 && Ymm4Craft.isActive() ? p.currentPartial() : null;
    }

    private float currentPartial() {
        return (float) Mth.clamp(targetTickTime - (ticks - 1), 0.0, 1.0);
    }

    public static void setPov(boolean value) {
        pov = value;
    }

    public static void setShowHand(boolean value) {
        showHand = value;
    }

    public static void setShowScreens(boolean value) {
        showScreens = value;
    }

    public static void setShowCursor(boolean value) {
        showCursor = value;
    }

    public static void setShowChat(boolean value) {
        showChat = value;
    }

    public static boolean showsChat() {
        return current != null && showChat && Ymm4Craft.isActive();
    }

    public static boolean hidesChat() {
        return current != null && !showChat && Ymm4Craft.isActive();
    }

    public static void setPovTarget(String name) {
        povTargetName = name == null ? "" : name;
    }

    public static boolean showsCursorOn(Screen screen) {
        ReplayPlayback p = current;
        return p != null && showCursor && screen == p.replayScreen && Ymm4Craft.isActive()
                && p.rewriter.screens().cursor(p.currentPartial()) != null;
    }

    public static Player screenPlayer() {
        ReplayPlayback p = current;
        if (p == null || p.replayScreen == null || !Ymm4Craft.isActive()) return null;
        Player player = povPlayer();
        return Minecraft.getInstance().screen == p.replayScreen && p.isRecorded(player) ? player : null;
    }

    public static double[] screenCursor(int guiWidth, int guiHeight) {
        ReplayPlayback p = current;
        if (p == null || p.replayScreen == null || !Ymm4Craft.isActive()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != p.replayScreen) return null;
        ReplayScreenState screens = p.rewriter.screens();
        float[] cursor = screens.cursor(p.currentPartial());
        if (cursor == null) return new double[]{-1000, -1000};
        if (screens.cursorFromCenter()) return new double[]{guiWidth / 2.0 + cursor[0], guiHeight / 2.0 + cursor[1]};
        return new double[]{cursor[0] * guiWidth, cursor[1] * guiHeight};
    }

    public static Player povPlayer() {
        ReplayPlayback p = current;
        if (p == null || !pov || !Ymm4Craft.isActive()) return null;
        Minecraft mc = Minecraft.getInstance();
        return mc.getCameraEntity() instanceof Player player && player == p.povTarget(mc) ? player : null;
    }

    public static ReplayHands povHands() {
        ReplayPlayback p = current;
        return p != null && showHand && povPlayer() != null ? p.hands : null;
    }

    private static boolean hudRendering;

    public static void setHudRendering(boolean value) {
        hudRendering = value;
    }

    public static GameType hudGameMode() {
        return hudRendering ? povGameMode() : null;
    }

    public static GameType povGameMode() {
        ReplayPlayback p = current;
        Player player = povPlayer();
        if (player == null) return null;
        return p.isRecorded(player) ? p.rewriter.recordedGameMode() : GameType.SPECTATOR;
    }

    public static int pendingTicks() {
        ReplayPlayback p = current;
        if (p == null || p.readyMs < 0 || p.jumpScheduled || !Ymm4Craft.isActive()) return 0;
        return Math.max(0, Math.min(p.targetTicks - p.ticks, FAST_FORWARD_PER_FRAME));
    }

    public static boolean freezesEverything() {
        return current != null && Ymm4Craft.isActive();
    }

    public static void onTickStart(Minecraft mc) {
        ReplayPlayback p = current;
        if (p == null || !Ymm4Craft.isActive()) return;
        try {
            p.reportLoading(mc);
            p.tickStart(mc);
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.warn("リプレイの再生に失敗しました", t);
            close();
        }
    }

    private boolean shouldJump(int target) {
        if (target < ticks) return true;
        ReplaySnapshot snapshot = snapshots.floor(target);
        return snapshot != null && snapshot.ticks() > ticks + JUMP_AHEAD_TICKS;
    }

    private void start() throws IOException {
        reader = input.open();
        next = reader.next();
        readyMs = -1;
        ticks = 0;
        ended = false;
        prefix.clear();
        openConnection();

        boolean inPlay = false;
        while (next != null && readyMs < 0) {
            ReplayEntry e = next;
            next = reader.next();
            if (e.kind() == ReplayFiles.MARK_READY) {
                readyMs = e.timeMs();
                rewriter.markReady();
            } else {
                if (!inPlay) prefix.add(e);
                feed(e);
                if (rewriter.recordedPlayerId() != Integer.MIN_VALUE) inPlay = true;
                if (!input.marksReady() && rewriter.sawPosition()) {
                    readyMs = e.timeMs();
                    rewriter.markReady();
                }
            }
        }
        if (readyMs < 0) throw new IOException("リプレイにワールドの読み込みが終わった記録がありません（録画がすぐに止まった可能性があります）");
        channel.runPendingTasks();
        Ymm4Craft.LOGGER.info("リプレイ「{}」を開きました", name);
    }

    private void restore(ReplaySnapshot snapshot) throws IOException {
        long started = System.nanoTime();
        List<byte[]> packets = ReplayPacketCodec.decompress(snapshot.packets().join());
        openConnection();
        for (ReplayEntry e : prefix) feed(e);
        rewriter.beginRestore(snapshot.rewriter());
        for (byte[] packet : packets) feed(new ReplayEntry(0, ReplayFiles.PLAY, packet));
        rewriter.markReady();
        reader = input.openAt(snapshot.entryIndex());
        next = snapshot.next();
        ticks = snapshot.ticks();
        rewriter.chat().setTick(ticks);
        ended = snapshot.ended();
        channel.runPendingTasks();
        Ymm4Craft.LOGGER.info("リプレイを {} 秒の時点から読み直しました（{}ms）", ticks / 20.0, (System.nanoTime() - started) / 1_000_000);
    }

    private void takeSnapshot(Minecraft mc) {
        ProtocolInfo<?> info = ReplayRecorder.protocolInfoOf(channel.pipeline());
        if (info == null || info.id() != ConnectionProtocol.PLAY || mc.player == null || mc.level == null) return;
        try {
            mc.level.pollLightUpdates();
            long started = System.nanoTime();
            byte[] raw = ReplayPacketCodec.encode(ReplaySnapshotCapture.capture(mc, rewriter.stream()), info);
            snapshots.add(new ReplaySnapshot(ticks, reader.index(), next, ended, raw.length,
                    CompletableFuture.supplyAsync(() -> ReplayPacketCodec.compress(raw)), rewriter.save()));
            Ymm4Craft.LOGGER.debug("リプレイの {} 秒の時点を保存しました（{}KB, {}ms）", ticks / 20.0, raw.length / 1024, (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException e) {
            Ymm4Craft.LOGGER.warn("リプレイの途中の状態を保存できませんでした", e);
        }
    }

    private void openConnection() {
        Minecraft mc = Minecraft.getInstance();
        audio.interrupt();
        rewriter.reset();
        hands.reset();
        replayScreen = null;
        appliedScreenRevision = -1;
        appliedChatRevision = -1;

        mc.setScreen(new ReceivingLevelScreen(() -> false, ReceivingLevelScreen.Reason.OTHER));

        channel = new EmbeddedChannel();
        connection = new Connection(PacketFlow.CLIENTBOUND);
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("ymm4craft_discard", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promise.setSuccess();
            }
        });
        Connection.configureInMemoryPipeline(pipeline, PacketFlow.CLIENTBOUND);
        connection.configurePacketHandler(pipeline);
        pipeline.addBefore("inbound_config", "ymm4craft_replay_in", new ChannelInboundHandlerAdapter());
        pipeline.addBefore("packet_handler", "ymm4craft_replay_filter", new ReplayFilterHandler(rewriter, () -> connection));
        pipeline.fireChannelActive();

        connection.initiateServerboundPlayConnection("ymm4craft-replay", 0,
                new ClientHandshakePacketListenerImpl(connection, mc, null, null, false, null, status -> {
                }, null));
        ((MinecraftAccessor) mc).ymm4craft$setPendingConnection(connection);
    }

    private void shutdown() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.level != null) {
                mc.disconnect();
            } else if (connection != null) {
                connection.disconnect(Component.empty());
                ((MinecraftAccessor) mc).ymm4craft$setPendingConnection(null);
                mc.setScreen(new TitleScreen());
            }
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.warn("リプレイのワールドを閉じるときに失敗しました", t);
        }
        try {
            if (channel != null) channel.close();
        } catch (Throwable ignored) {
        }
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {
        }
        channel = null;
        connection = null;
        reader = null;
    }

    private void scheduleJump() {
        jumpScheduled = true;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (current != this) return;
            jumpScheduled = false;
            ReplaySnapshot snapshot = snapshots.floor(targetTicks);
            shutdown();
            if (snapshot != null) {
                try {
                    restore(snapshot);
                    return;
                } catch (IOException | RuntimeException e) {
                    Ymm4Craft.LOGGER.warn("リプレイを途中から読み直せなかったので、最初から読み直します", e);
                    shutdown();
                }
            }
            try {
                start();
            } catch (IOException | RuntimeException e) {
                Ymm4Craft.LOGGER.warn("リプレイを最初から読み直せませんでした", e);
                close();
            }
        });
    }

    private void tickStart(Minecraft mc) {
        if (channel != null) channel.runPendingTasks();
        if (jumpScheduled || mc.level == null || readyMs < 0) return;
        settleHeld();
        if (jumpScheduled) return;
        driveExport();
        if (jumpScheduled) return;
        if (snapshots.wants(ticks)) takeSnapshot(mc);
        var manager = mc.level.tickRateManager();
        manager.setFrozen(true);
        boolean advanced = false;
        if (ticks < targetTicks) {
            rewriter.screens().tick();
            ticks++;
            boolean skipping = skippingParticles();
            if (skipping && !particlesCleared) mc.particleEngine.clearParticles();
            particlesCleared = skipping;
            audio.advance(ticks, !skipping, mc.level.getEntity(rewriter.recordedPlayerId()), mc.level.players(), mc.player);
            advanced = true;
            rewriter.chat().setTick(ticks);
            feedUntil(readyMs + ticks * 50L);
            if (mc.level != null) mc.level.tickRateManager().setFrozenTicksToRun(1);
        } else {
            manager.setFrozenTicksToRun(0);
        }
        completeWaiters();
        applySelf(mc, advanced);
        applyChat(mc);
    }

    private void applyChat(Minecraft mc) {
        ReplayChatState chat = rewriter.chat();
        int offset = mc.gui.getGuiTicks() - ticks;
        if (chat.revision() == appliedChatRevision && offset == appliedChatOffset) return;
        appliedChatRevision = chat.revision();
        appliedChatOffset = offset;
        ChatComponentAccessor accessor = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage> all = accessor.ymm4craft$allMessages();
        all.clear();
        for (ReplayChatState.Message message : chat.messages()) {
            if (message.tick() > ticks) continue;
            all.addFirst(new GuiMessage(message.tick() + offset, message.content(), null, GuiMessageTag.system()));
        }
        accessor.ymm4craft$refreshTrimmedMessages();
    }

    private void applySelf(Minecraft mc, boolean advanced) {
        Player player = pov ? povTarget(mc) : null;
        if (player != null) {
            if (mc.getCameraEntity() != player) mc.setCameraEntity(player);
            boolean recorded = isRecorded(player);
            if (recorded) rewriter.hud().applyTo(player, mc.player);
            if (advanced) hands.tick(player);
            applyScreen(mc, showScreens && recorded ? player : null);
        } else {
            if (mc.player != null && mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
            applyScreen(mc, null);
        }
    }

    private void applyScreen(Minecraft mc, Player recorded) {
        Screen screen = mc.screen;
        if (screen != null && screen != replayScreen) return;
        ReplayScreenState state = rewriter.screens();
        ClientboundOpenScreenPacket open = recorded == null ? null : state.open();
        if (open != null) {
            if (!(screen instanceof AbstractContainerScreen<?> shown) || shown.getMenu().containerId != open.getContainerId()) {
                MenuScreens.create(open.getType(), mc, open.getContainerId(), open.getTitle());
                replayScreen = mc.screen;
                appliedScreenRevision = -1;
            }
            if (replayScreen instanceof AbstractContainerScreen<?> shown && appliedScreenRevision != state.revision()) {
                appliedScreenRevision = state.revision();
                AbstractContainerMenu menu = shown.getMenu();
                if (state.items() != null) menu.initializeContents(state.stateId(), state.items(), state.carried());
                else menu.setCarried(state.carried());
                state.data().forEach(menu::setData);
                if (menu instanceof MerchantMenu merchant && state.offers() != null) {
                    var offers = state.offers();
                    merchant.setOffers(offers.getOffers());
                    merchant.setXp(offers.getVillagerXp());
                    merchant.setMerchantLevel(offers.getVillagerLevel());
                    merchant.setShowProgressBar(offers.showProgress());
                    merchant.setCanRestock(offers.canRestock());
                }
                if (shown instanceof MerchantScreen && state.merchantSelection() >= 0) {
                    MerchantScreenAccessor accessor = (MerchantScreenAccessor) shown;
                    accessor.ymm4craft$setShopItem(state.merchantSelection());
                    accessor.ymm4craft$setScrollOff(state.merchantScroll());
                }
            }
            return;
        }
        if (recorded != null && state.inventoryOpen()) {
            if (!(screen instanceof InventoryScreen)) {
                mc.setScreen(new InventoryScreen(recorded));
                replayScreen = mc.screen;
            }
            recorded.inventoryMenu.setCarried(state.carried());
            return;
        }
        if (replayScreen != null && screen == replayScreen) {
            if (mc.player != null) mc.player.clientSideCloseContainer();
            else mc.setScreen(null);
        }
        replayScreen = null;
    }

    private void completeWaiters() {
        if (readyMs < 0 || jumpScheduled) return;
        List<Waiter> done = new ArrayList<>();
        waiters.removeIf(w -> {
            if (ticks < w.ticks && !ended) return false;
            done.add(w);
            return true;
        });
        done.forEach(w -> w.future.complete(null));
    }

    private void feedUntil(long timeMs) {
        try {
            while (next != null && next.timeMs() <= timeMs) {
                ReplayEntry e = next;
                next = reader.next();
                if (e.kind() != ReplayFiles.MARK_READY) feed(e);
            }
            if (next == null) ended = true;
        } catch (IOException e) {
            Ymm4Craft.LOGGER.warn("リプレイの読み込みに失敗しました。ここで終わりにします", e);
            next = null;
            ended = true;
        }
    }

    private ConnectionProtocol currentProtocol() {
        ProtocolInfo<?> info = channel == null ? null : ReplayRecorder.protocolInfoOf(channel.pipeline());
        return info == null ? null : info.id();
    }

    private void feed(ReplayEntry e) {
        if (channel == null) return;
        if (e.kind() == ReplayFiles.INVENTORY_SCREEN) {
            rewriter.screens().captureInventory(e.data());
            return;
        }
        if (e.kind() == ReplayFiles.MERCHANT_SELECTION) {
            rewriter.screens().captureMerchant(e.data());
            return;
        }
        if (e.kind() == ReplayFiles.CURSOR || e.kind() == ReplayFiles.CURSOR_GUI) {
            rewriter.screens().captureCursor(e.data(), e.kind() == ReplayFiles.CURSOR_GUI);
            return;
        }
        if (e.packet() != null) {
            ChannelHandlerContext filter = channel.pipeline().context("ymm4craft_replay_filter");
            if (filter == null) return;
            try {
                ((ReplayFilterHandler) filter.handler()).channelRead(filter, e.packet());
            } catch (Throwable t) {
                Ymm4Craft.LOGGER.debug("リプレイの記録を1つ読めませんでした", t);
            }
            channel.runPendingTasks();
            return;
        }
        ConnectionProtocol expected = switch (e.kind()) {
            case ReplayFiles.LOGIN -> ConnectionProtocol.LOGIN;
            case ReplayFiles.CONFIGURATION -> ConnectionProtocol.CONFIGURATION;
            case ReplayFiles.PLAY -> ConnectionProtocol.PLAY;
            default -> null;
        };
        ProtocolInfo<?> info = ReplayRecorder.protocolInfoOf(channel.pipeline());
        if (e.kind() == ReplayFiles.ANY_PHASE && info != null) expected = info.id();
        if (expected == null || info == null || info.id() != expected) {
            Ymm4Craft.LOGGER.debug("リプレイの記録を飛ばしました（{} / 今は {}）", expected, info == null ? null : info.id());
            return;
        }
        ChannelHandlerContext ctx = channel.pipeline().context("ymm4craft_replay_in");
        if (ctx == null) return;
        try {
            ctx.fireChannelRead(Unpooled.wrappedBuffer(e.data()));
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.debug("リプレイの記録を1つ読めませんでした", t);
        }
        channel.runPendingTasks();
    }
}
