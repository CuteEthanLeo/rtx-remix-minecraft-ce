/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nvidia.rtxremix.minecraft.gl.DynamicBufferUpload;
import com.nvidia.rtxremix.minecraft.gl.DynamicVertexBuffer;
import net.minecraft.client.gl.VertexBuffer;
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

/**
 * Routes the {@code glBufferData} re-specification of dynamic buffers through
 * {@link DynamicBufferUpload}. Every other upload keeps the original call.
 *
 * <p>This version passes the STATIC hint for every buffer, so a dynamic buffer is one
 * {@link DynamicVertexBuffer} marked, and its uploads carry the DYNAMIC hint in its place.
 */
@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin implements DynamicVertexBuffer {
    private static final String rtxremix$GL_BUFFER_DATA =
        "Lcom/mojang/blaze3d/systems/RenderSystem;glBufferData(ILjava/nio/ByteBuffer;I)V";

    private final DynamicBufferUpload.Storage rtxremix$vertexStorage = new DynamicBufferUpload.Storage();
    private final DynamicBufferUpload.Storage rtxremix$indexStorage = new DynamicBufferUpload.Storage();
    private boolean rtxremix$dynamic;

    @Override
    public void rtxremix$markDynamic() {
        rtxremix$dynamic = true;
    }

    @Redirect(method = "uploadVertexBuffer", at = @At(value = "INVOKE", target = rtxremix$GL_BUFFER_DATA))
    private void rtxremix$uploadVertexData(int target, ByteBuffer data, int usage) {
        rtxremix$upload(target, data, usage, rtxremix$vertexStorage);
    }

    @Redirect(method = "uploadIndexBuffer", at = @At(value = "INVOKE", target = rtxremix$GL_BUFFER_DATA))
    private void rtxremix$uploadIndexData(int target, ByteBuffer data, int usage) {
        rtxremix$upload(target, data, usage, rtxremix$indexStorage);
    }

    private void rtxremix$upload(int target, ByteBuffer data, int usage, DynamicBufferUpload.Storage storage) {
        int effectiveUsage = rtxremix$dynamic ? GL15.GL_DYNAMIC_DRAW : usage;
        if (DynamicBufferUpload.handles(effectiveUsage)) {
            DynamicBufferUpload.upload(target, data, effectiveUsage, storage);
        } else {
            RenderSystem.glBufferData(target, data, usage);
        }
    }
}
