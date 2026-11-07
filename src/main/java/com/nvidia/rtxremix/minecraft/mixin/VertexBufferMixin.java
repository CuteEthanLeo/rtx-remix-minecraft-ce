/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.gl.DynamicBufferUpload;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.GpuBuffer;
import net.minecraft.client.gl.VertexBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps one allocation per {@code DYNAMIC} vertex buffer object; see
 * {@link DynamicBufferUpload}. Since 1.21.2 {@code uploadVertexBuffer} resizes its
 * {@link GpuBuffer} before every copy, and the resize marks the buffer uninitialized, so the
 * copy re-specifies the storage with {@code glBufferData} each time. Skipping a resize the
 * current allocation already covers leaves the buffer initialized, and the copy then
 * overwrites it with {@code glBufferSubData}. The index buffer is replaced by a fresh buffer
 * object per upload in 1.21.2, which deletes the old name instead of orphaning its storage,
 * and keeps vanilla's path.
 */
@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin {
    @Shadow
    @Final
    private GlUsage usage;

    private final DynamicBufferUpload.Storage rtxremix$vertexStorage = new DynamicBufferUpload.Storage();

    @WrapOperation(
            method = "uploadVertexBuffer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GpuBuffer;resize(I)V"))
    private void rtxremix$keepDynamicAllocation(GpuBuffer buffer, int size, Operation<Void> original) {
        if (usage == GlUsage.DYNAMIC_WRITE && !rtxremix$vertexStorage.claim(size)) {
            return;
        }
        original.call(buffer, size);
    }
}
