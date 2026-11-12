/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.visibility.VisibilityGraphBuffers;
import net.minecraft.client.render.WorldRenderer.ChunkInfo;
import net.minecraft.client.render.WorldRenderer.ChunkInfoList;
import net.minecraft.client.render.WorldRenderer.RenderableChunks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.LinkedHashSet;

/**
 * Builds vanilla's visibility state on the buffers {@link VisibilityGraphBuffers} keeps
 * across walks instead of two fresh collections sized to the whole storage. Vanilla's
 * fields and readers are unchanged; they see a {@code LinkedHashSet} and a
 * {@code ChunkInfoList}, as before.
 */
@Mixin(RenderableChunks.class)
public abstract class RenderableChunksMixin {

    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", target = "(I)Lnet/minecraft/client/render/WorldRenderer$ChunkInfoList;"))
    private ChunkInfoList rtxremix$reusedInfoList(int chunkCount) {
        return VisibilityGraphBuffers.current().infos;
    }

    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", target = "(I)Ljava/util/LinkedHashSet;"))
    private LinkedHashSet<ChunkInfo> rtxremix$reusedChunkSet(int chunkCount) {
        return VisibilityGraphBuffers.current().chunks;
    }
}
