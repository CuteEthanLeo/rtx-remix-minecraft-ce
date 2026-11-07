/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.nvidia.rtxremix.minecraft.sky.WorldRendererStarAccess;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Copies vanilla's canonical star positions before its CPU mesh is released. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin implements WorldRendererStarAccess {
    // No initializer: buildStars runs inside the target's constructor.
    private float[] rtxremix$starVertices;

    @WrapOperation(method = "buildStars", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;buildOrThrow()Lcom/mojang/blaze3d/vertex/MeshData;"))
    private MeshData rtxremix$captureStars(BufferBuilder builder, Operation<MeshData> original) {
        MeshData mesh = original.call(builder);
        int count = mesh.drawState().vertexCount();
        int stride = mesh.drawState().format().getVertexSize();
        ByteBuffer data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        int start = data.position();
        float[] positions = new float[count * 3];
        for (int vertex = 0; vertex < count; vertex++) {
            int offset = start + vertex * stride;
            for (int axis = 0; axis < 3; axis++) {
                positions[vertex * 3 + axis] = data.getFloat(offset + axis * Float.BYTES);
            }
        }
        rtxremix$starVertices = positions;
        return mesh;
    }

    @Override
    public float[] rtxremix$copyStarVertices() {
        return rtxremix$starVertices.clone();
    }
}
