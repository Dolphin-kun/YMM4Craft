package com.dolphin_kun.ymm4craft.core.gpu;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import org.lwjgl.opengl.WGL;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public final class GpuFrameShare implements AutoCloseable {
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_COLOR_BUFFER_BIT = 0x4000;
    private static final int GL_NEAREST = 0x2600;
    private static final int GL_LINEAR = 0x2601;
    private static final int WGL_ACCESS_WRITE_DISCARD_NV = 0x0002;

    private static final long KEY_FREE = 0;
    private static final long KEY_WRITTEN = 1;

    private final int generation;
    private final int width;
    private final int height;
    private final D3D11Device device;
    private final MemorySegment interopDevice;
    private final List<Slot> slots = new ArrayList<>();
    private final Wgl wgl;
    private int readFramebuffer;
    private int nextSlot;

    private record Slot(D3D11Device.SharedTexture shared, int glTexture, MemorySegment interopObject, int framebuffer) {
    }

    private record Wgl(MemorySegment open, MemorySegment close, MemorySegment register, MemorySegment unregister,
                       MemorySegment lock, MemorySegment unlock) {
        static Wgl load() {
            return new Wgl(find("wglDXOpenDeviceNV"), find("wglDXCloseDeviceNV"), find("wglDXRegisterObjectNV"),
                    find("wglDXUnregisterObjectNV"), find("wglDXLockObjectsNV"), find("wglDXUnlockObjectsNV"));
        }

        private static MemorySegment find(String name) {
            long address = WGL.wglGetProcAddress(null, name);
            if (address == 0) throw new IllegalStateException(name + " がありません（WGL_NV_DX_interop2 に対応していないドライバ）");
            return MemorySegment.ofAddress(address);
        }
    }

    private GpuFrameShare(int generation, int width, int height, D3D11Device device, Wgl wgl, MemorySegment interopDevice) {
        this.generation = generation;
        this.width = width;
        this.height = height;
        this.device = device;
        this.wgl = wgl;
        this.interopDevice = interopDevice;
    }

    public static GpuFrameShare open(int generation, long luidLow, int luidHigh, int width, int height, long[] handles) {
        Wgl wgl = Wgl.load();
        D3D11Device device = D3D11Device.create(luidLow, luidHigh);
        MemorySegment interop = (MemorySegment) Com.invoke(wgl.open, FunctionDescriptor.of(ADDRESS, ADDRESS), device.device);
        if (interop.address() == 0) {
            device.close();
            throw new IllegalStateException("wglDXOpenDeviceNV に失敗しました");
        }
        GpuFrameShare share = new GpuFrameShare(generation, width, height, device, wgl, interop);
        try {
            for (long handle : handles) share.addSlot(handle);
            share.readFramebuffer = GlStateManager.glGenFramebuffers();
            Ymm4Craft.LOGGER.info("GPUでの共有を始めます（{}、{}x{}、{}枚）", device.adapterName, width, height, handles.length);
            return share;
        } catch (RuntimeException e) {
            share.close();
            throw e;
        }
    }

    private void addSlot(long handle) {
        D3D11Device.SharedTexture shared = device.openShared(handle);
        int texture = GlStateManager._genTexture();
        MemorySegment object = (MemorySegment) Com.invoke(wgl.register,
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT),
                interopDevice, shared.texture(), texture, GL_TEXTURE_2D, WGL_ACCESS_WRITE_DISCARD_NV);
        if (object.address() == 0) {
            shared.close();
            throw new IllegalStateException("wglDXRegisterObjectNV に失敗しました");
        }
        int framebuffer = GlStateManager.glGenFramebuffers();
        Slot slot = new Slot(shared, texture, object, framebuffer);
        slots.add(slot);
        if (!acquireSlot(slot)) throw new IllegalStateException("共有テクスチャをロックできません");
        try {
            lock(slot);
            try {
                GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
                GlStateManager._glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
                GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
            } finally {
                unlock(slot);
            }
        } finally {
            slot.shared().release(KEY_FREE);
        }
    }

    public int generation() {
        return generation;
    }

    public record Published(int slot, int width, int height) {
    }

    public Published publish(GpuTexture source) {
        if (!(source instanceof GlTexture glSource)) throw new IllegalStateException("OpenGL以外のテクスチャです");
        for (int attempt = 0; attempt < slots.size(); attempt++) {
            int index = (nextSlot + attempt) % slots.size();
            Slot slot = slots.get(index);
            if (!acquireSlot(slot)) continue;
            try {
                lock(slot);
                try {
                    blit(glSource, source.getWidth(0), source.getHeight(0), slot);
                } finally {
                    unlock(slot);
                }
            } catch (RuntimeException e) {
                slot.shared().release(KEY_FREE);
                throw e;
            }
            slot.shared().release(KEY_WRITTEN);
            nextSlot = (index + 1) % slots.size();
            return new Published(index, width, height);
        }
        return null;
    }

    private boolean acquireSlot(Slot slot) {
        return slot.shared().acquire(KEY_FREE, 0) || slot.shared().acquire(KEY_WRITTEN, 0);
    }

    private void blit(GlTexture source, int sourceWidth, int sourceHeight, Slot slot) {
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        GlStateManager._glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, source.glId(), 0);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, slot.framebuffer());
        GlStateManager._disableScissorTest();
        boolean sameSize = sourceWidth == width && sourceHeight == height;
        GlStateManager._glBlitFrameBuffer(0, 0, sourceWidth, sourceHeight, 0, height, width, 0,
                GL_COLOR_BUFFER_BIT, sameSize ? GL_NEAREST : GL_LINEAR);
        GlStateManager._glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    }

    private void lock(Slot slot) {
        callLockOrUnlock(wgl.lock, slot, "wglDXLockObjectsNV");
    }

    private void unlock(Slot slot) {
        callLockOrUnlock(wgl.unlock, slot, "wglDXUnlockObjectsNV");
    }

    private void callLockOrUnlock(MemorySegment function, Slot slot, String name) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment objects = arena.allocate(ADDRESS);
            objects.set(ADDRESS, 0, slot.interopObject());
            int ok = (int) Com.invoke(function, FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS), interopDevice, 1, objects);
            if (ok == 0) throw new IllegalStateException(name + " に失敗しました");
        }
    }

    @Override
    public void close() {
        for (Slot slot : slots) {
            try {
                Com.invoke(wgl.unregister, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), interopDevice, slot.interopObject());
                GlStateManager._glDeleteFramebuffers(slot.framebuffer());
                GlStateManager._deleteTexture(slot.glTexture());
            } catch (RuntimeException e) {
                Ymm4Craft.LOGGER.warn("共有テクスチャの片付けに失敗しました", e);
            }
            slot.shared().close();
        }
        slots.clear();
        if (readFramebuffer != 0) GlStateManager._glDeleteFramebuffers(readFramebuffer);
        Com.invoke(wgl.close, FunctionDescriptor.of(JAVA_INT, ADDRESS), interopDevice);
        device.close();
    }
}
