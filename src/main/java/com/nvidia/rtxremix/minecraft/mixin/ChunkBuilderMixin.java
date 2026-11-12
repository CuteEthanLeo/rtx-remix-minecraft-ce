/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.capture.SectionBuildConcurrency;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Caps the section-builder buffer pool, and with it the number of chunk
 * sections that build concurrently. Vanilla sizes the pool from the logical
 * CPU count; {@link SectionBuildConcurrency} decides how many of those to
 * allocate.
 */
@Mixin(ChunkBuilder.class)
public abstract class ChunkBuilderMixin {

    // The constructor's second Math.min bounds the CPU-derived count by the memory-derived one.
    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 1),
            index = 0)
    private static int rtxremix$capSectionBuilders(int max) {
        return SectionBuildConcurrency.cap(max);
    }
}
