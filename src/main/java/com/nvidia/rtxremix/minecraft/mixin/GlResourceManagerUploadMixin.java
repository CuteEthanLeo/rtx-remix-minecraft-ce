/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.textures.GpuTexture;
import com.nvidia.rtxremix.minecraft.texture.GlyphPageMirror;
import net.minecraft.client.gl.GlResourceManager;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

/**
 * Copies a glyph upload into its page mirror where the pixels meet GL.
 *
 * <p>Since 1.21.5 every texture write goes through the device's command encoder, and a
 * glyph reaches it through one of two overloads. Bitmap-font glyphs upload a window of the
 * font's sheet, TrueType and built-in glyphs a whole small {@link NativeImage}; Unihex
 * glyphs are rasterised into an {@code IntBuffer} with rows packed at the glyph's width.
 * Both overloads' arguments are exactly the GL sub-image call: destination slot, size and,
 * for an image, the source window. Only an upload inside a glyph upload scope is mirrored,
 * so every other texture upload in the game costs one static read.
 */
@Mixin(GlResourceManager.class)
public abstract class GlResourceManagerUploadMixin {

    @Inject(
            method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lnet/minecraft/client/texture/NativeImage;IIIIIII)V",
            at = @At("HEAD"))
    private void rtxremix$mirrorGlyphImageUpload(GpuTexture target, NativeImage source, int mipLevel,
                                                 int intoX, int intoY, int width, int height,
                                                 int x, int y, CallbackInfo ci) {
        GlyphPageMirror page = GlyphPageMirror.uploadTarget();
        if (page == null || mipLevel != 0 || width <= 0 || height <= 0) {
            return;
        }
        int channels = source.getFormat().getChannelCount();
        int rowPitch = source.getWidth() * channels;
        long first = source.imageId() + ((long) y * source.getWidth() + x) * channels;
        // The window's rows stride the whole image; the buffer spans only the bytes the
        // window touches, from its first pixel to the end of its last row.
        int span = (height - 1) * rowPitch + width * channels;
        page.blit(intoX, intoY, width, height, channels,
                MemoryUtil.memByteBuffer(first, span), rowPitch);
    }

    @Inject(
            method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/nio/IntBuffer;Lnet/minecraft/client/texture/NativeImage$Format;IIIII)V",
            at = @At("HEAD"))
    private void rtxremix$mirrorGlyphBufferUpload(GpuTexture target, IntBuffer source, NativeImage.Format format,
                                                  int mipLevel, int intoX, int intoY, int width, int height,
                                                  CallbackInfo ci) {
        GlyphPageMirror page = GlyphPageMirror.uploadTarget();
        if (page == null || mipLevel != 0 || width <= 0 || height <= 0) {
            return;
        }
        int channels = format.getChannelCount();
        int rowPitch = width * channels;
        page.blit(intoX, intoY, width, height, channels,
                MemoryUtil.memByteBuffer(MemoryUtil.memAddress(source), height * rowPitch), rowPitch);
    }
}
