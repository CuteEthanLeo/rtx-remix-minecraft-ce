/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.sky.WorldRendererStarAccess;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Copies vanilla's star positions as they are tessellated. Since 1.21.2 the star buffer
 * belongs to {@link SkyRendering} rather than the world renderer, which forwards
 * {@link WorldRendererStarAccess} here. Since 1.21.4 the tessellation writes into a consumer
 * that is uploaded and discarded by its caller, so the positions are taken one vertex at a
 * time on their way in.
 */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin implements WorldRendererStarAccess {
    private static final int rtxremix$INITIAL_STAR_FLOATS = 4096;

    // No initializers: tessellateStar runs from a field initializer of the target, before
    // a mixin field initializer would.
    private float[] rtxremix$starVertices;
    private int rtxremix$starFloatCount;

    @Inject(method = "tessellateStar", at = @At("HEAD"))
    private void rtxremix$beginStarCapture(VertexConsumer vertexConsumer, CallbackInfo ci) {
        rtxremix$starVertices = new float[rtxremix$INITIAL_STAR_FLOATS];
        rtxremix$starFloatCount = 0;
    }

    @ModifyArg(
            method = "tessellateStar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumer;vertex(Lorg/joml/Vector3f;)Lnet/minecraft/client/render/VertexConsumer;"))
    private Vector3f rtxremix$captureStarVertex(Vector3f position) {
        if (rtxremix$starFloatCount + 3 > rtxremix$starVertices.length) {
            rtxremix$starVertices = Arrays.copyOf(rtxremix$starVertices, rtxremix$starVertices.length * 2);
        }
        rtxremix$starVertices[rtxremix$starFloatCount++] = position.x;
        rtxremix$starVertices[rtxremix$starFloatCount++] = position.y;
        rtxremix$starVertices[rtxremix$starFloatCount++] = position.z;
        return position;
    }

    @Inject(method = "tessellateStar", at = @At("RETURN"))
    private void rtxremix$endStarCapture(VertexConsumer vertexConsumer, CallbackInfo ci) {
        rtxremix$starVertices = Arrays.copyOf(rtxremix$starVertices, rtxremix$starFloatCount);
    }

    @Override
    public float[] rtxremix$copyStarVertices() {
        return rtxremix$starVertices == null ? new float[0] : rtxremix$starVertices.clone();
    }
}
