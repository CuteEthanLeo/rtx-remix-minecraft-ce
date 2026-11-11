/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.BuiltChunkNeighbourhoodAccess;
import com.nvidia.rtxremix.minecraft.terrain.ChunkBuilderBlockEntityAccess;
import com.nvidia.rtxremix.minecraft.terrain.NeighbourReflagFilter;
import com.nvidia.rtxremix.minecraft.terrain.SectionLeftRenderSetQueue;
import com.nvidia.rtxremix.minecraft.terrain.visibility.ChunkSlotMoveLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.AbstractChunkRenderData;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRenderData;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into vanilla's BuiltChunk lifecycle.
 *
 * <ul>
 *   <li><b>setOrigin:</b> Resets per-slot block-entity and snapshot state when
 *       vanilla reuses a BuiltChunk for a new section, and logs the move so the
 *       frustum index can re-bucket exactly the slots that moved.
 *   <li><b>createRebuildTask:</b> Records which neighbour columns the region
 *       snapshot contained, so a later chunk-load re-flag from a column the
 *       capture already read can be dropped.
 *   <li><b>setCurrentRenderData:</b> Tracks whether the section contains block
 *       entities and flags empty render data as a removal candidate. The
 *       render thread validates the live world section before removing
 *       Remix terrain because vanilla can publish temporary empty data.
 * </ul>
 */
@Mixin(ChunkBuilder.BuiltChunk.class)
public abstract class BuiltChunkMixin implements ChunkBuilderBlockEntityAccess, BuiltChunkNeighbourhoodAccess {

    @Shadow
    @Final
    public int index;

    @Shadow
    @Final
    BlockPos.Mutable origin;

    @Shadow
    protected abstract boolean isChunkNonEmpty(long sectionPos);

    @Shadow
    public abstract AbstractChunkRenderData getCurrentRenderData();

    private boolean rtxremix$hasBlockEntities;

    /** Bit k set iff neighbour column (DX[k], DZ[k]) was loaded when the last region snapshot was taken. */
    private int rtxremix$snapshotMask;

    @Override
    public boolean rtxremix$hasBlockEntities() {
        return rtxremix$hasBlockEntities;
    }

    @Override
    public int rtxremix$snapshotMask() {
        return rtxremix$snapshotMask;
    }

    @Override
    public void rtxremix$clearSnapshotBit(int bit) {
        rtxremix$snapshotMask &= ~bit;
    }

    @Inject(method = "setSectionPos(J)V", at = @At("HEAD"))
    private void rtxremix$onSetOrigin(long sectionPos, CallbackInfo ci) {
        rtxremix$hasBlockEntities = false;
        rtxremix$snapshotMask = 0;
    }

    @Inject(method = "setSectionPos(J)V", at = @At("RETURN"))
    private void rtxremix$afterSetOrigin(long sectionPos, CallbackInfo ci) {
        ChunkSlotMoveLog.RENDER_THREAD.record(index);
    }

    /**
     * The probe sees no column the snapshot lacks: ChunkRendererRegionBuilder.build reads
     * ClientChunkManager.getChunk(x, z, FULL, true), which returns the shared EmptyChunk
     * precisely when the probe's create=false lookup returns null, and nothing interleaves on
     * the render thread between the two. Since 1.21.4 the probe also wants the column's light
     * enabled, so a column the snapshot holds while its light is still pending is recorded as
     * absent; that only keeps a re-flag which could have been dropped.
     */
    @Inject(
            method = "createRebuildTask(Lnet/minecraft/client/render/chunk/ChunkRendererRegionBuilder;)Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk$Task;",
            at = @At("RETURN"))
    private void rtxremix$recordSnapshotNeighbourhood(
            ChunkRendererRegionBuilder regionBuilder, CallbackInfoReturnable<?> cir) {
        // RETURN precedes the synchronous task.run in BuiltChunk.rebuild, so a first build still reads its
        // placeholder here: HIDDEN, or READY once the visibility walk has passed through the unbuilt slot.
        boolean firstBuild = !(getCurrentRenderData() instanceof ChunkRenderData);
        int mask = 0;
        for (int k = 0; k < NeighbourReflagFilter.NEIGHBOUR_COUNT; k++) {
            long neighbourSection = ChunkSectionPos.asLong(
                    ChunkSectionPos.getSectionCoord(origin.getX()) + NeighbourReflagFilter.DX[k],
                    ChunkSectionPos.getSectionCoord(origin.getY()),
                    ChunkSectionPos.getSectionCoord(origin.getZ()) + NeighbourReflagFilter.DZ[k]);
            if (isChunkNonEmpty(neighbourSection)) {
                mask |= NeighbourReflagFilter.bit(k);
            }
        }
        rtxremix$snapshotMask = mask;
        if (NeighbourReflagFilter.isIncompleteFirstBuild(firstBuild, rtxremix$squaredCameraDistance(), mask)) {
            NeighbourReflagFilter.recordIncompleteFirstBuild();
        }
    }

    private double rtxremix$squaredCameraDistance() {
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        double dx = origin.getX() + 8.0 - camera.x;
        double dy = origin.getY() + 8.0 - camera.y;
        double dz = origin.getZ() + 8.0 - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }

    // The layers are uploaded before the data is published, so hasData already tells
    // whether the build produced any geometry.
    @Inject(
            method = "setCurrentRenderData(Lnet/minecraft/client/render/chunk/AbstractChunkRenderData;)V",
            at = @At("HEAD"))
    private void rtxremix$onChunkDataSet(AbstractChunkRenderData chunkData, CallbackInfo ci) {
        rtxremix$hasBlockEntities = !chunkData.getBlockEntities().isEmpty();

        if (chunkData.hasData()) {
            return;
        }
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        if (x == -1 && y == -1 && z == -1) {
            return;
        }
        SectionLeftRenderSetQueue.enqueueEmptyCandidate(x >> 4, y >> 4, z >> 4);
    }

    @Inject(method = "clear()V", at = @At("HEAD"), require = 0)
    private void rtxremix$onClear(CallbackInfo ci) {
        rtxremix$hasBlockEntities = false;
        rtxremix$snapshotMask = 0;
    }
}
