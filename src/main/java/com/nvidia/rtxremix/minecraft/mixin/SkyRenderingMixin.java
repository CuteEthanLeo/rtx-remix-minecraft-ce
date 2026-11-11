/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.nvidia.rtxremix.minecraft.sky.WorldRendererStarAccess;
import net.minecraft.client.render.SkyRendering;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * Copies vanilla's star positions as they are tessellated. Since 1.21.2 the star buffer
 * belongs to {@link SkyRendering} rather than the world renderer, which forwards
 * {@link WorldRendererStarAccess} here. The tessellation writes into a builder that is
 * uploaded to a GPU buffer and discarded, so the positions are taken one vertex at a time on
 * their way in.
 */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin implements WorldRendererStarAccess {
    private static final int rtxremix$INITIAL_STAR_FLOATS = 4096;

    // No initializers: createStars runs inside the target's constructor, so the capture
    // must not depend on where a mixin field initializer lands relative to it.
    private float[] rtxremix$starVertices;
    private int rtxremix$starFloatCount;

    @Inject(method = "createStars", at = @At("HEAD"))
    private void rtxremix$beginStarCapture(CallbackInfoReturnable<GpuBuffer> cir) {
        rtxremix$starVertices = new float[rtxremix$INITIAL_STAR_FLOATS];
        rtxremix$starFloatCount = 0;
    }

    @ModifyArg(
            method = "createStars",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/BufferBuilder;vertex(Lorg/joml/Vector3f;)Lnet/minecraft/client/render/VertexConsumer;"))
    private Vector3f rtxremix$captureStarVertex(Vector3f position) {
        if (rtxremix$starFloatCount + 3 > rtxremix$starVertices.length) {
            rtxremix$starVertices = Arrays.copyOf(rtxremix$starVertices, rtxremix$starVertices.length * 2);
        }
        rtxremix$starVertices[rtxremix$starFloatCount++] = position.x;
        rtxremix$starVertices[rtxremix$starFloatCount++] = position.y;
        rtxremix$starVertices[rtxremix$starFloatCount++] = position.z;
        return position;
    }

    @Inject(method = "createStars", at = @At("RETURN"))
    private void rtxremix$endStarCapture(CallbackInfoReturnable<GpuBuffer> cir) {
        rtxremix$starVertices = Arrays.copyOf(rtxremix$starVertices, rtxremix$starFloatCount);
    }

    @Override
    public float[] rtxremix$copyStarVertices() {
        return rtxremix$starVertices == null ? new float[0] : rtxremix$starVertices.clone();
    }
}
