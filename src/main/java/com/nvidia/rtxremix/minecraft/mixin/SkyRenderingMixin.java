/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.sky.WorldRendererStarAccess;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Copies vanilla's star positions while their CPU buffer still exists. Since 1.21.2 the star
 * buffer belongs to {@link SkyRendering} rather than the world renderer, which forwards
 * {@link WorldRendererStarAccess} here.
 */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin implements WorldRendererStarAccess {
    // No initializer: tessellateStars runs from a field initializer of the target, before
    // a mixin field initializer would.
    private float[] rtxremix$starVertices;

    @Inject(method = "tessellateStars", at = @At("RETURN"))
    private void rtxremix$captureStarVertices(
            Tessellator tessellator,
            CallbackInfoReturnable<BuiltBuffer> cir) {
        BuiltBuffer builtBuffer = cir.getReturnValue();
        BuiltBuffer.DrawParameters parameters = builtBuffer.getDrawParameters();
        int positionOffset = parameters.format().getOffset(VertexFormatElement.POSITION);
        if (positionOffset < 0) {
            rtxremix$starVertices = new float[0];
            return;
        }

        int vertexCount = parameters.vertexCount();
        int stride = parameters.format().getVertexSizeByte();
        ByteBuffer vertices = builtBuffer.getBuffer().duplicate().order(ByteOrder.nativeOrder());
        float[] positions = new float[vertexCount * 3];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int source = vertex * stride + positionOffset;
            int destination = vertex * 3;
            positions[destination] = vertices.getFloat(source);
            positions[destination + 1] = vertices.getFloat(source + Float.BYTES);
            positions[destination + 2] = vertices.getFloat(source + 2 * Float.BYTES);
        }
        rtxremix$starVertices = positions;
    }

    @Override
    public float[] rtxremix$copyStarVertices() {
        return rtxremix$starVertices == null ? new float[0] : rtxremix$starVertices.clone();
    }
}
