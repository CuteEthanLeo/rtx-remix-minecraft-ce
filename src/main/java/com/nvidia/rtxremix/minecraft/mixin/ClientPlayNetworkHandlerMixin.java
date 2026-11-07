/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.terrain.WorldRendererTerrainAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes a loaded chunk's rebuild flags through the chunk-load re-flag filter. Since 1.21.2
 * {@code scheduleRenderChunk} flags the loaded column and its eight neighbours for every
 * section in one {@code scheduleChunkRenders} call, which cannot tell the loading column
 * from its neighbours; the filter needs exactly that. The call's range is always the
 * column (x, z) widened by one in both horizontal directions.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @WrapOperation(
            method = "scheduleRenderChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/world/ClientWorld;scheduleChunkRenders(IIIIII)V"))
    private void rtxremix$filterChunkLoadReflags(
            ClientWorld world,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            Operation<Void> original) {
        WorldRendererTerrainAccess terrain =
                (WorldRendererTerrainAccess) MinecraftClient.getInstance().worldRenderer;
        int cx = (minX + maxX) >> 1;
        int cz = (minZ + maxZ) >> 1;
        if (!terrain.rtxremix$flagLoadedColumn(cx, minY, maxY, cz)) {
            original.call(world, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
