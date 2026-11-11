/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.GlTexture;

/**
 * Presents a GL texture the mod does not own, such as an interop image, to vanilla's render
 * passes as a sampler source. The wrapper neither deletes the texture nor rewrites its
 * sampling parameters, which stay whatever the owner set.
 */
public final class ExternalGlTexture extends GlTexture {
    private GpuTextureView view;

    public ExternalGlTexture(String label, int glId, int width, int height) {
        super(GpuTexture.USAGE_TEXTURE_BINDING, label, TextureFormat.RGBA8, width, height, 1, 1, glId);
    }

    /** The view render passes sample through, created on first use. */
    public GpuTextureView view() {
        if (view == null) {
            view = RenderSystem.getDevice().createTextureView(this);
        }
        return view;
    }

    public boolean wraps(int glId, int width, int height) {
        return this.glId == glId && getWidth(0) == width && getHeight(0) == height;
    }

    @Override
    public void checkDirty(int target) {
    }

    // Vanilla deletes a closed texture when its last view goes; this one is not ours to delete.
    @Override
    public void incrementRefCount() {
    }

    @Override
    public void decrementRefCount() {
    }

    @Override
    public void close() {
        closed = true;
    }
}
