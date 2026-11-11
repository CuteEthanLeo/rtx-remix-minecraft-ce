/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.capture.RenderRegionStoragePool;
import com.nvidia.rtxremix.minecraft.terrain.capture.RegionSectionProbe;
import com.nvidia.rtxremix.minecraft.terrain.capture.RegionSnapshotRelease;
import net.minecraft.block.BlockState;
import net.minecraft.util.collection.PackedIntegerArray;
import net.minecraft.world.chunk.PalettedContainer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A section snapshot for a render region whose block-state copy draws its storage from
 * {@link RenderRegionStoragePool} and gives it back when the last region holding the snapshot
 * has been consumed. Snapshots are shared between the regions one frame's rebuild scheduling
 * builds, hence the count. The pool flag is set around the constructor by
 * {@link ChunkRendererRegionBuilderMixin}; vanilla's own snapshot code is untouched.
 */
@Mixin(targets = "net.minecraft.client.render.chunk.RenderedChunk")
public abstract class RenderedChunkMixin implements RegionSnapshotRelease, RegionSectionProbe {

    @Shadow
    @Final
    @Nullable
    private PalettedContainer<BlockState> blockPalette;

    @Shadow
    @Final
    private boolean debugWorld;

    private final AtomicInteger rtxremix$regions = new AtomicInteger();

    // The debug world generates its blocks on read and keeps no palette.
    @Override
    public boolean rtxremix$isSectionEmpty() {
        return blockPalette == null && !debugWorld;
    }

    @Override
    public void rtxremix$retain() {
        rtxremix$regions.incrementAndGet();
    }

    @Override
    public void rtxremix$release() {
        if (rtxremix$regions.decrementAndGet() != 0 || blockPalette == null) {
            return;
        }
        if (blockPalette.data.storage() instanceof PackedIntegerArray packed) {
            RenderRegionStoragePool.release(packed.getData());
        }
        // Nothing may read the snapshot after its last release; make a stale read fail loudly
        // instead of returning whatever the recycled array holds next. The container is this
        // snapshot's own copy, so dropping its data affects no one else.
        blockPalette.data = null;
    }
}
