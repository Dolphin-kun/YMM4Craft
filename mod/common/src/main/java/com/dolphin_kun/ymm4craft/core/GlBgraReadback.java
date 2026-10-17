package com.dolphin_kun.ymm4craft.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlBuffer;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTexture;

final class GlBgraReadback {
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB;
    private static final int GL_PACK_ROW_LENGTH = 0x0D02;
    private static final int GL_PACK_ALIGNMENT = 0x0D05;
    private static final int GL_BGRA = 0x80E1;
    private static final int GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367;

    private static int readFramebuffer;

    private GlBgraReadback() {
    }

    static boolean supports(GpuTexture texture, GpuBuffer buffer) {
        return texture instanceof GlTexture && buffer instanceof GlBuffer;
    }

    static void copy(GpuTexture texture, GpuBuffer buffer, int width, int height, Runnable onComplete) {
        GlTexture glTexture = (GlTexture) texture;
        GlBuffer glBuffer = (GlBuffer) buffer;
        glBuffer.checkCanBeUsed();
        if (readFramebuffer == 0) readFramebuffer = GlStateManager.glGenFramebuffers();

        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        GlStateManager._glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, glTexture.glId(), 0);
        GlStateManager._glReadBuffer(GL_COLOR_ATTACHMENT0);
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, glBuffer.handle());
        GlStateManager._pixelStore(GL_PACK_ALIGNMENT, 4);
        GlStateManager._pixelStore(GL_PACK_ROW_LENGTH, 0);
        try {
            GlStateManager._readPixels(0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0L);
        } finally {
            GlStateManager._glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
            GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        }
        RenderSystem.queueFencedTask(onComplete);
    }
}
