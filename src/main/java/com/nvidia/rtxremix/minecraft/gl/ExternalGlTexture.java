/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.gl;

import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.GlTexture;

/**
 * Presents a GL texture the mod does not own, such as an interop image, to vanilla's render
 * passes as a sampler source. The wrapper neither deletes the texture nor rewrites its
 * sampling parameters, which stay whatever the owner set.
 */
public final class ExternalGlTexture extends GlTexture {
    public ExternalGlTexture(String label, int glId, int width, int height) {
        super(label, TextureFormat.RGBA8, width, height, 1, glId);
    }

    public boolean wraps(int glId, int width, int height) {
        return this.glId == glId && getWidth(0) == width && getHeight(0) == height;
    }

    @Override
    public void checkDirty() {
    }

    @Override
    public void close() {
        closed = true;
    }
}
