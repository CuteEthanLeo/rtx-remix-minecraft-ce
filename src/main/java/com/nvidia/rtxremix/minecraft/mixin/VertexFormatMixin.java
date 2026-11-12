/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.gl.DynamicVertexBuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Marks the buffer a vertex format shares between immediate draws as dynamic.
 * {@code getBuffer} creates it once and {@code BufferRenderer} refills it for every
 * immediate draw, which is the upload pattern {@link DynamicVertexBuffer} exists for.
 */
@Mixin(VertexFormat.class)
public abstract class VertexFormatMixin {

    @Redirect(
            method = "getBuffer",
            at = @At(value = "NEW", target = "()Lnet/minecraft/client/gl/VertexBuffer;"))
    private VertexBuffer rtxremix$dynamicSharedBuffer() {
        VertexBuffer buffer = new VertexBuffer();
        ((DynamicVertexBuffer) buffer).rtxremix$markDynamic();
        return buffer;
    }
}
