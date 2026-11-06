/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.gl;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the Remix output texture as a full-screen, fully opaque quad on
 * Minecraft's main framebuffer, after the world and first-person model have
 * been submitted and before the screen overlays and HUD.
 *
 * <p>Render thread only. Minecraft 1.21.5+ removed the immediate-mode
 * {@code RenderSystem}/{@code BufferRenderer} path this used to draw through,
 * so the overlay is now a direct-state-access framebuffer blit, which leaves
 * every binding vanilla's GL state cache tracks untouched.
 */
public class TextureOverlay {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TextureOverlay.class);
    
    private final Minecraft client;
    
    public TextureOverlay() {
        this.client = Minecraft.getInstance();
        LOGGER.info("TextureOverlay initialized");
    }
    
    /**
     * Renders a texture as a full-screen overlay.
     * 
     * @param textureId The GL texture ID to render as full-screen overlay
     */
    public void render(int textureId) {
        if (textureId <= 0) {
            return;
        }
        
        if (client == null || client.getWindow() == null) {
            return;
        }
        
        try {
            renderTextureOverlay(textureId);
        } catch (Exception e) {
            LOGGER.error("Error rendering texture overlay", e);
        }
    }
    
    private int sourceFbo;
    private int sourceTexture;
    private int destinationFbo;
    private int destinationTexture;

    private void renderTextureOverlay(int texId) {
        RenderTarget framebuffer = client.gameRenderer.mainRenderTarget();
        if (framebuffer == null) {
            return;
        }
        GpuTexture color = framebuffer.getColorTexture();
        if (!(color instanceof GlTexture glColor)) {
            return;
        }
        int dstTex = glColor.glId();
        if (sourceFbo == 0) {
            sourceFbo = GL45.glCreateFramebuffers();
        }
        if (sourceTexture != texId) {
            GL45.glNamedFramebufferTexture(sourceFbo, GL30.GL_COLOR_ATTACHMENT0, texId, 0);
            sourceTexture = texId;
        }
        if (destinationFbo == 0) {
            destinationFbo = GL45.glCreateFramebuffers();
        }
        if (destinationTexture != dstTex) {
            GL45.glNamedFramebufferTexture(destinationFbo, GL30.GL_COLOR_ATTACHMENT0, dstTex, 0);
            destinationTexture = dstTex;
        }
        int srcWidth = GL45.glGetTextureLevelParameteri(texId, 0, GL11.GL_TEXTURE_WIDTH);
        int srcHeight = GL45.glGetTextureLevelParameteri(texId, 0, GL11.GL_TEXTURE_HEIGHT);
        int dstWidth = framebuffer.width;
        int dstHeight = framebuffer.height;
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
            return;
        }
        // Opaque replace. Texture row 0 lands at the top of the screen, matching the
        // old top-left (0,0) UV quad: the interop blit already corrects orientation.
        GL45.glBlitNamedFramebuffer(sourceFbo, destinationFbo,
            0, 0, srcWidth, srcHeight,
            0, dstHeight, dstWidth, 0,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
    }

    /**
     * Cleans up resources.
     */
    public void shutdown() {
        if (sourceFbo != 0) {
            GL30.glDeleteFramebuffers(sourceFbo);
            sourceFbo = 0;
        }
        if (destinationFbo != 0) {
            GL30.glDeleteFramebuffers(destinationFbo);
            destinationFbo = 0;
        }
        sourceTexture = 0;
        destinationTexture = 0;
        LOGGER.info("TextureOverlay shutdown");
    }
}
