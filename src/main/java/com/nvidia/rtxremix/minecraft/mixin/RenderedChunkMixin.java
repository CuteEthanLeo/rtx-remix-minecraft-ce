/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.terrain.capture.RenderRegionStoragePool;
import com.nvidia.rtxremix.minecraft.terrain.capture.RegionSnapshotRelease;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * A section snapshot for a render region whose block-state copy draws its storage from
 * {@link RenderRegionStoragePool} and gives it back when the last region holding the snapshot
 * has been consumed. Snapshots are shared between the regions one frame's rebuild scheduling
 * builds, hence the count. The constructor sets the pool flag around its block-state copy
 * and takes the builder's reference on the new snapshot; vanilla's own snapshot code is
 * untouched.
 *
 * <p>1.21.11 snapshots one section per {@code RenderedChunk} rather than a column, and the
 * builder caches them itself, so the former {@code ClientChunkMixin} hook lives here.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionCopy")
public abstract class RenderedChunkMixin implements RegionSnapshotRelease {

    @Shadow
    @Final
    @Mutable
    private PalettedContainer<BlockState> section;

    private final AtomicInteger rtxremix$regions = new AtomicInteger();

    @WrapOperation(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;copy()Lnet/minecraft/world/level/chunk/PalettedContainer;"))
    private PalettedContainer<BlockState> rtxremix$pooledCopy(
            PalettedContainer<BlockState> container, Operation<PalettedContainer<BlockState>> original) {
        RenderRegionStoragePool.beginCopying();
        try {
            return original.call(container);
        } finally {
            RenderRegionStoragePool.endCopying();
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rtxremix$builderReference(LevelChunk chunk, int sectionIndex, CallbackInfo ci) {
        rtxremix$retain();
    }

    @Override
    public void rtxremix$retain() {
        rtxremix$regions.incrementAndGet();
    }

    @Override
    public void rtxremix$release() {
        if (rtxremix$regions.decrementAndGet() != 0 || section == null) {
            return;
        }
        if (section.data.storage() instanceof SimpleBitStorage packed) {
            RenderRegionStoragePool.release(packed.getRaw());
        }
        // Nothing may read the snapshot after its last release; a stale read sees air
        // instead of whatever the recycled arrays hold next.
        section = null;
    }
}
