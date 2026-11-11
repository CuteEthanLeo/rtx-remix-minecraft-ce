/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.terrain.capture.RegionSnapshotRelease;
import com.nvidia.rtxremix.minecraft.terrain.capture.RegionBuilderRelease;
import com.nvidia.rtxremix.minecraft.terrain.capture.RenderRegionStoragePool;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.render.chunk.RenderedChunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The builder's reference on its cached section snapshots; see {@link RegionBuilderRelease}.
 * The reference is taken where a snapshot is created, with the render thread marked as
 * taking a region snapshot so the block-state copy draws pooled storage; see
 * {@link RenderedChunkMixin}.
 */
@Mixin(ChunkRendererRegionBuilder.class)
public abstract class ChunkRendererRegionBuilderMixin implements RegionBuilderRelease {

    @Shadow
    @Final
    private Long2ObjectMap<RenderedChunk> renderedChunksByPos;

    // The snapshot is created in the computeIfAbsent lambda of getRenderedChunk.
    @WrapOperation(
            method = "method_72043",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/chunk/WorldChunk;I)Lnet/minecraft/client/render/chunk/RenderedChunk;")
    )
    private static RenderedChunk rtxremix$snapshotWithPooledStorage(
            WorldChunk chunk, int sectionIndex, Operation<RenderedChunk> original) {
        RenderRegionStoragePool.beginCopying();
        RenderedChunk snapshot;
        try {
            snapshot = original.call(chunk, sectionIndex);
        } finally {
            RenderRegionStoragePool.endCopying();
        }
        ((RegionSnapshotRelease) snapshot).rtxremix$retain();
        return snapshot;
    }

    @Override
    public void rtxremix$releaseSnapshots() {
        for (RenderedChunk snapshot : renderedChunksByPos.values()) {
            ((RegionSnapshotRelease) snapshot).rtxremix$release();
        }
        renderedChunksByPos.clear();
    }
}
