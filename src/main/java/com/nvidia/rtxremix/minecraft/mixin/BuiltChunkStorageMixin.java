/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.BuiltChunkNeighbourhoodAccess;
import com.nvidia.rtxremix.minecraft.terrain.visibility.BuiltChunkStorageSectionAccess;
import com.nvidia.rtxremix.minecraft.terrain.NeighbourReflagFilter;
import com.nvidia.rtxremix.minecraft.terrain.SkippedSectionRebuilds;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.terrain.visibility.ChunkStorageTorus;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.client.RotatingSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ViewArea.class)
public abstract class BuiltChunkStorageMixin
        implements ChunkStorageTorus.StorageAccess, BuiltChunkStorageSectionAccess {
    // 26.2: ViewArea keeps its slots in a RotatingSectionStorage that already re-origins in
    // section space and answers only for the section a slot currently holds.
    @Shadow
    @Final
    private RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections;

    @Shadow
    public abstract int minSectionY();

    @Shadow
    public abstract int sectionCount();

    // TODO(26.2): the torus reposition (ChunkStorageTorus) is not ported; RotatingSectionStorage
    // has no slot-index access, so these StorageAccess hooks are inert.
    @Override
    public int rtxremix$getOriginX(int slotIndex) {
        return 0;
    }

    @Override
    public int rtxremix$getOriginZ(int slotIndex) {
        return 0;
    }

    @Override
    public void rtxremix$setOrigin(int slotIndex, int x, int y, int z) {
    }

    /** The slot holding the section, or null unless a slot currently holds it. */
    private SectionRenderDispatcher.RenderSection rtxremix$residentSlot(int cx, int sy, int cz) {
        SectionRenderDispatcher.RenderSection slot = sections.getValue(cx, sy, cz);
        if (slot == null) return null;
        BlockPos origin = slot.getRenderOrigin();
        boolean resident = SectionPos.blockToSectionCoord(origin.getX()) == cx
                && SectionPos.blockToSectionCoord(origin.getY()) == sy
                && SectionPos.blockToSectionCoord(origin.getZ()) == cz;
        return resident ? slot : null;
    }

    @Override
    public void rtxremix$scheduleRebuildIfResident(int cx, int sy, int cz) {
        SectionRenderDispatcher.RenderSection slot = rtxremix$residentSlot(cx, sy, cz);
        if (slot != null) {
            rtxremix$markDirty(slot);
        }
    }

    @Override
    public void rtxremix$flagLoadedSection(int cx, int sy, int cz) {
        if (!NeighbourReflagFilter.sectionInRange(sy, minSectionY(), sectionCount())) {
            return;
        }
        rtxremix$scheduleRebuildIfResident(cx, sy, cz);
        for (int k = 0; k < NeighbourReflagFilter.NEIGHBOUR_COUNT; k++) {
            SectionRenderDispatcher.RenderSection slot = rtxremix$residentSlot(
                    cx + NeighbourReflagFilter.DX[k], sy, cz + NeighbourReflagFilter.DZ[k]);
            boolean resident = slot != null;
            // TODO(26.2): dirtiness moved to LevelExtractor's private SectionUpdateTracker; a
            // resident neighbour is treated as not awaiting a build.
            boolean awaitingBuild = false;
            int snapshotMask = resident ? ((BuiltChunkNeighbourhoodAccess) slot).rtxremix$snapshotMask() : 0;
            int loaderBit = NeighbourReflagFilter.bit(NeighbourReflagFilter.mirroredIndex(k));
            switch (NeighbourReflagFilter.decideNeighbour(resident, awaitingBuild, snapshotMask, loaderBit)) {
                case FLAG -> {
                    rtxremix$markDirty(slot);
                    NeighbourReflagFilter.recordKept();
                }
                case DROP_IN_SNAPSHOT -> SkippedSectionRebuilds.recordNeighbourInSnapshot();
                case SKIP_NOT_RESIDENT, SKIP_AWAITING_BUILD -> {
                }
            }
        }
    }

    /** 26.2: RenderSection lost its dirty flag; sections are marked dirty through the LevelExtractor. */
    private static void rtxremix$markDirty(SectionRenderDispatcher.RenderSection slot) {
        long node = slot.getSectionNode();
        net.minecraft.client.Minecraft.getInstance().levelExtractor
                .setSectionDirty(SectionPos.x(node), SectionPos.y(node), SectionPos.z(node));
    }

    @Override
    public void rtxremix$clearNeighbourSnapshotBits(int cx, int cz) {
        int bottomSection = minSectionY();
        for (int k = 0; k < NeighbourReflagFilter.NEIGHBOUR_COUNT; k++) {
            int bit = NeighbourReflagFilter.bit(NeighbourReflagFilter.mirroredIndex(k));
            int neighbourX = cx + NeighbourReflagFilter.DX[k];
            int neighbourZ = cz + NeighbourReflagFilter.DZ[k];
            for (int row = 0; row < sectionCount(); row++) {
                SectionRenderDispatcher.RenderSection slot = rtxremix$residentSlot(neighbourX, bottomSection + row, neighbourZ);
                if (slot != null) {
                    ((BuiltChunkNeighbourhoodAccess) slot).rtxremix$clearSnapshotBit(bit);
                }
            }
        }
    }
}
