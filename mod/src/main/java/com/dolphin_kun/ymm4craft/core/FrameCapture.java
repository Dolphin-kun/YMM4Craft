package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.CommandException;
import com.dolphin_kun.ymm4craft.core.gpu.GpuFrameShare;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.Minecraft;

import java.io.IOException;
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
    private final ArrayDeque<GpuBuffer> freeBuffers = new ArrayDeque<>();
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

    private GpuFrameShare gpuShare;

    public FrameCapture(Session session) {
        this.session = session;
    }

    public void close() {
        setStreamFps(0);
        setGpuShare(null);
        for (Pending p : waiting) p.future.completeExceptionally(new IllegalStateException("YMM4との接続を終えました"));
        waiting.clear();
    }

    public void setGpuShare(GpuFrameShare share) {
        if (gpuShare != null && gpuShare != share) gpuShare.close();
        gpuShare = share;
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
            if (now >= nextStreamAt && (gpuShare != null || inFlight < MAX_IN_FLIGHT)) {
                stream = true;
                nextStreamAt = Math.max(nextStreamAt + streamIntervalNanos, now - streamIntervalNanos / 2);
            }
        }
        if (stream && ReplayPlayback.hidesFrames()) stream = false;
        if (due == null && !stream) return;

        if (stream && gpuShare != null) {
            try {
                publishGpu();
            } catch (Throwable t) {
                Ymm4Craft.LOGGER.warn("GPUでの共有に失敗したので、CPUを経由する方式に戻します", t);
                setGpuShare(null);
                HostConnection c = Ymm4Craft.connection();
                if (c != null) c.sendEvent("core.gpuShareFailed", null);
            }
            if (due == null) return;
            stream = false;
        }

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

        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture texture = target.getColorTexture();
        if (texture == null) throw new CommandException("描画結果がまだありません");
        if (texture.getFormat().blockSize() != 4) {
            throw new CommandException("対応していない画素の形式です: " + texture.getFormat());
        }
        int width = texture.getWidth(0);
        int height = texture.getHeight(0);
        if (!frameFile.fits(width, height)) {
            throw new CommandException("解像度が大きすぎます: " + width + "x" + height);
        }
        long revision = SceneFeature.appliedRevision();
        boolean settled = isSettled();
        GpuBuffer buffer = takeBuffer((long) width * height * 4);

        inFlight++;
        long issuedAt = System.nanoTime();
        boolean bgra = GlBgraReadback.supports(texture, buffer);
        int format = bgra ? FrameFile.FORMAT_BGRA8_BOTTOM_UP : FrameFile.FORMAT_RGBA8_BOTTOM_UP;
        Runnable onGpuDone = () -> {
            long gpuDoneAt = System.nanoTime();
            GpuBufferSlice.MappedView view;
            try {
                view = buffer.map(true, false);
            } catch (Throwable t) {
                waiters.forEach(f -> f.completeExceptionally(t));
                release(buffer, null);
                return;
            }
            COPY_THREAD.execute(() -> {
                try {
                    FrameFile.Written w = frameFile.write(width, height, format, revision, settled, view.data());
                    finish(w, issuedAt, gpuDoneAt, waiters, notify);
                } catch (Throwable t) {
                    waiters.forEach(f -> f.completeExceptionally(t));
                } finally {
                    Minecraft.getInstance().execute(() -> release(buffer, view));
                }
            });
        };
        if (bgra) {
            GlBgraReadback.copy(texture, buffer, width, height, onGpuDone);
        } else {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, onGpuDone, 0);
        }
    }

    private long gpuSequence;

    private void publishGpu() {
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture texture = target.getColorTexture();
        if (texture == null) return;
        long issuedAt = System.nanoTime();
        GpuFrameShare.Published p = gpuShare.publish(texture);
        if (p == null) return;
        JsonObject info = new JsonObject();
        info.addProperty("sequence", ++gpuSequence);
        info.addProperty("gpuGeneration", gpuShare.generation());
        info.addProperty("gpuSlot", p.slot());
        info.addProperty("width", p.width());
        info.addProperty("height", p.height());
        info.addProperty("revision", SceneFeature.appliedRevision());
        info.addProperty("settled", isSettled());
        info.addProperty("writeMs", (System.nanoTime() - issuedAt) / 1e6);
        HostConnection c = Ymm4Craft.connection();
        if (c != null) c.sendEvent("core.frame", info);
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

    private void release(GpuBuffer buffer, GpuBufferSlice.MappedView view) {
        try {
            if (view != null) view.close();
            freeBuffers.push(buffer);
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.warn("読み出し用バッファの解放に失敗しました", t);
            buffer.close();
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
        int visible = mc.levelRenderer.visibleSections().size();
        long revision = SceneFeature.appliedRevision();
        boolean quiet = mc.levelRenderer.hasRenderedAllSections()
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
        if (quietSince < 0) return false;
        double fadeSeconds = Minecraft.getInstance().options.chunkSectionFadeInTime().get();
        long required = Math.max(MIN_QUIET_NANOS, (long) (fadeSeconds * 1e9) + 100_000_000L);
        return System.nanoTime() - quietSince >= required;
    }

    private GpuBuffer takeBuffer(long size) {
        while (!freeBuffers.isEmpty()) {
            GpuBuffer b = freeBuffers.pop();
            if (b.size() == size && !b.isClosed()) return b;
            b.close();
        }
        return RenderSystem.getDevice().createBuffer(
                () -> "YMM4Craft frame",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_HINT_CLIENT_STORAGE,
                size);
    }
}
