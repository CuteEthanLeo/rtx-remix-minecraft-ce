/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.texture.GlyphPageMirror;
import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.client.font.RenderableGlyph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives every glyph atlas page a {@link GlyphPageMirror} and brackets each glyph upload
 * so the upload mixin knows which page the pixels are for.
 *
 * <p>{@code getGlyphRenderer} is the one place a page is written: it binds the texture
 * and calls the glyph's {@code upload} with the slot it found. Every glyph implementation
 * of this version (bitmap, TrueType, legacy Unicode and built-in) reaches GL through
 * {@code NativeImage.uploadInternal}, so the scope is opened here and the pixels are read
 * where they are uploaded.
 */
@Mixin(GlyphAtlasTexture.class)
public abstract class GlyphAtlasTextureMixin implements GlyphPageMirror.Owner {

    @Shadow @Final private boolean hasColor;

    private GlyphPageMirror rtxremix$mirror;

    @Override
    public GlyphPageMirror rtxremix$glyphPageMirror() {
        // Created on first use: hasColor is assigned by the constructor, so a field
        // initialiser could not read it.
        if (rtxremix$mirror == null) {
            rtxremix$mirror = new GlyphPageMirror(!hasColor);
        }
        return rtxremix$mirror;
    }

    @WrapOperation(
        method = "getGlyphRenderer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/RenderableGlyph;upload(II)V")
    )
    private void rtxremix$mirrorGlyphUpload(RenderableGlyph glyph, int x, int y, Operation<Void> original) {
        GlyphPageMirror.beginUpload(rtxremix$glyphPageMirror());
        try {
            original.call(glyph, x, y);
        } finally {
            GlyphPageMirror.endUpload();
        }
    }
}
