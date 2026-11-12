/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.WorldRendererTerrainAccess;
import com.nvidia.rtxremix.minecraft.terrain.SkippedSectionRebuilds;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * Section rebuild bookkeeping raised by the client chunk manager while RTX capture is on.
 */
@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerMixin {

    /**
     * Drops section rebuilds requested only because the client light engine changed a
     * section's light.
     *
     * <p>Vanilla re-meshes such a section so its baked lightmap follows. While RTX is enabled
     * the section builder captures every section for Remix and reports its blocks invisible
     * to vanilla, so vanilla's section buffers are empty and Remix terrain carries no baked
     * light; the rebuild would only recapture identical geometry, publish an identical page,
     * and retire the old one. The gate is the capture gate RebuildTaskCaptureMixin latches, not
     * whether Remix is drawing the world: vanilla has no terrain geometry to relight whenever
     * capture is on, including with world rendering toggled off or the renderer
     * uninitialized. Toggling RTX off reloads the world renderer, which rebuilds every
     * section with vanilla light again.
     *
     * <p>While the client light storage publishes its live map ({@link LightStorageMixin})
     * the notifications never reach this hook: that mixin cancels notifyChanges and counts
     * the pass's pending notifications itself. This hook covers the kill-switch path and the
     * passes that publish a real copy.
     */
    @Inject(method = "onLightUpdate", at = @At("HEAD"), cancellable = true)
    private void rtxremix$skipLightOnlyRebuild(LightType type, ChunkSectionPos pos, CallbackInfo ci) {
        if (RtxRemixMod.isRtxEnabled()) {
            SkippedSectionRebuilds.recordLightOnly();
            ci.cancel();
        }
    }

    /**
     * A same-position reload replaces the WorldChunk's contents in place and fires no
     * CHUNK_UNLOAD, so the neighbour slots whose region snapshot holds the old content must
     * forget the column before the reload's re-flags arrive, or they would be dropped as
     * already captured. Slot reuse by another position and unload() are covered by the Fabric
     * CHUNK_UNLOAD event.
     */
    @Inject(method = "loadChunkFromPacket", at = @At("HEAD"))
    private void rtxremix$invalidateNeighboursOnInPlaceReload(
            int x,
            int z,
            PacketByteBuf buf,
            NbtCompound nbt,
            Consumer<ChunkData.BlockEntityVisitor> consumer,
            CallbackInfoReturnable<WorldChunk> cir) {
        if (!RtxRemixMod.isRtxEnabled()) {
            return;
        }
        ClientChunkManager self = (ClientChunkManager) (Object) this;
        if (self.getChunk(x, z, ChunkStatus.FULL, false) == null) {
            return;
        }
        if (MinecraftClient.getInstance().worldRenderer instanceof WorldRendererTerrainAccess access) {
            access.rtxremix$onNeighbourColumnInvalidated(x, z);
        }
    }
}
