package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class FrameCapture {
    private static final int MAX_IN_FLIGHT = 2;

    private static final java.util.concurrent.ExecutorService COPY_THREAD =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "YMM4Craft-FrameCopy");
                t.setDaemon(true);
                return t;
            });

    private final Session session;
    private FrameFile frameFile;

    private final List<Pending> waiting = new ArrayList<>();
    private final List<Readback> reading = new ArrayList<>();
    private final ArrayDeque<Readback> freeBuffers = new ArrayDeque<>();
    private int inFlight;
    private long streamIntervalNanos;

    private static final long MIN_QUIET_NANOS = 500_000_000L;
    private long quietSince = -1;
    private int lastLoadedChunks = -1;
    private int lastVisibleSections = -1;
    private long lastRevision = -1;
    private long nextStreamAt;

    private static final class Pending {
        int framesLeft;
        final CompletableFuture<JsonElement> future;

        Pending(int framesLeft, CompletableFuture<JsonElement> future) {
            this.framesLeft = framesLeft;
            this.future = future;
        }
    }

    private static final class Readback {
        final int buffer;
        final long size;
        long fence;
        int width;
        int height;
        long revision;
        boolean settled;
        long issuedAt;
        boolean notify;
        List<CompletableFuture<JsonElement>> waiters;

        Readback(long size) {
            this.size = size;
            this.buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, size, GL15.GL_STREAM_READ);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        }

        void delete() {
            if (fence != 0) GL32.glDeleteSync(fence);
            fence = 0;
            GL15.glDeleteBuffers(buffer);
        }
    }

    public FrameCapture(Session session) {
        this.session = session;
    }

    public void close() {
        setStreamFps(0);
        for (Pending p : waiting) p.future.completeExceptionally(new IllegalStateException("YMM4との接続を終えました"));
        waiting.clear();
    }

    public CompletableFuture<JsonElement> request(int settleFrames) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        waiting.add(new Pending(settleFrames, future));
        return future;
    }

    public void setStreamFps(int fps) {
        streamIntervalNanos = fps <= 0 ? 0 : 1_000_000_000L / fps;
        nextStreamAt = 0;
    }

    public void onFrameRendered() {
        updateSettled();
        collectFinished();

        List<CompletableFuture<JsonElement>> due = null;
        Iterator<Pending> it = waiting.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.framesLeft-- > 0) continue;
            it.remove();
            if (due == null) due = new ArrayList<>();
            due.add(p.future);
        }

        boolean stream = false;
        if (streamIntervalNanos > 0) {
            long now = System.nanoTime();
            if (now >= nextStreamAt && inFlight < MAX_IN_FLIGHT) {
                stream = true;
                nextStreamAt = Math.max(nextStreamAt + streamIntervalNanos, now - streamIntervalNanos / 2);
            }
        }
        if (stream && ReplayPlayback.hidesFrames()) stream = false;
        if (due == null && !stream) return;

        try {
            startReadback(due == null ? List.of() : due, stream);
        } catch (Throwable t) {
            if (due != null) due.forEach(f -> f.completeExceptionally(t));
            if (due == null) Ymm4Craft.LOGGER.warn("フレームの読み出しに失敗しました", t);
        }
    }

    private void startReadback(List<CompletableFuture<JsonElement>> waiters, boolean notify) throws CommandException, IOException {
        if (frameFile == null) {
            frameFile = FrameFile.open(session.frameFile(), session.frameCapacity());
        }

        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        int width = target.width;
        int height = target.height;
        if (width <= 0 || height <= 0) throw new CommandException("描画結果がまだありません");
        if (!frameFile.fits(width, height)) {
            throw new CommandException("解像度が大きすぎます: " + width + "x" + height);
        }
        Readback r = takeBuffer((long) width * height * 4);
        r.width = width;
        r.height = height;
        r.revision = SceneFeature.appliedRevision();
        r.settled = isSettled();
        r.issuedAt = System.nanoTime();
        r.notify = notify;
        r.waiters = waiters;

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, r.buffer);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
        try {
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, 0L);
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        }
        r.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        inFlight++;
        reading.add(r);
    }

    private void collectFinished() {
        Iterator<Readback> it = reading.iterator();
        while (it.hasNext()) {
            Readback r = it.next();
            int status = GL32.glClientWaitSync(r.fence, 0, 0L);
            if (status != GL32.GL_ALREADY_SIGNALED && status != GL32.GL_CONDITION_SATISFIED) continue;
            it.remove();
            GL32.glDeleteSync(r.fence);
            r.fence = 0;
            long gpuDoneAt = System.nanoTime();
            ByteBuffer view;
            try {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, r.buffer);
                view = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0, r.size, GL30.GL_MAP_READ_BIT);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                if (view == null) throw new IllegalStateException("読み出し用バッファを開けませんでした");
            } catch (Throwable t) {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                r.waiters.forEach(f -> f.completeExceptionally(t));
                release(r, false);
                continue;
            }
            COPY_THREAD.execute(() -> {
                try {
                    FrameFile.Written w = frameFile.write(r.width, r.height, FrameFile.FORMAT_BGRA8_BOTTOM_UP, r.revision, r.settled, view);
                    finish(w, r.issuedAt, gpuDoneAt, r.waiters, r.notify);
                } catch (Throwable t) {
                    r.waiters.forEach(f -> f.completeExceptionally(t));
                } finally {
                    Minecraft.getInstance().execute(() -> release(r, true));
                }
            });
        }
    }

    private static void finish(FrameFile.Written w, long issuedAt, long gpuDoneAt,
                               List<CompletableFuture<JsonElement>> waiters, boolean notify) {
        long writtenAt = System.nanoTime();
        JsonObject info = new JsonObject();
        info.addProperty("sequence", w.sequence());
        info.addProperty("offset", w.offset());
        info.addProperty("width", w.width());
        info.addProperty("height", w.height());
        info.addProperty("revision", w.revision());
        info.addProperty("settled", w.settled());
        info.addProperty("gpuWaitMs", (gpuDoneAt - issuedAt) / 1e6);
        info.addProperty("writeMs", (writtenAt - gpuDoneAt) / 1e6);

        for (CompletableFuture<JsonElement> f : waiters) f.complete(info);
        HostConnection c = Ymm4Craft.connection();
        if (notify && c != null) c.sendEvent("core.frame", info);
    }

    private void release(Readback r, boolean mapped) {
        try {
            if (mapped) {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, r.buffer);
                GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            }
            freeBuffers.push(r);
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.warn("読み出し用バッファの解放に失敗しました", t);
            r.delete();
        } finally {
            inFlight--;
        }
    }

    private void updateSettled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            quietSince = -1;
            lastLoadedChunks = -1;
            return;
        }
        int loaded = mc.level.getChunkSource().getLoadedChunksCount();
        int visible = mc.levelRenderer.countRenderedSections();
        long revision = SceneFeature.appliedRevision();
        SectionRenderDispatcher dispatcher = mc.levelRenderer.getSectionRenderDispatcher();
        boolean quiet = (dispatcher == null || dispatcher.isQueueEmpty())
                && loaded == lastLoadedChunks
                && visible == lastVisibleSections
                && revision == lastRevision;
        lastLoadedChunks = loaded;
        lastVisibleSections = visible;
        lastRevision = revision;
        if (!quiet) quietSince = -1;
        else if (quietSince < 0) quietSince = System.nanoTime();
    }

    private boolean isSettled() {
        return quietSince >= 0 && System.nanoTime() - quietSince >= MIN_QUIET_NANOS;
    }

    private Readback takeBuffer(long size) {
        while (!freeBuffers.isEmpty()) {
            Readback b = freeBuffers.pop();
            if (b.size == size) return b;
            b.delete();
        }
        return new Readback(size);
    }
}
