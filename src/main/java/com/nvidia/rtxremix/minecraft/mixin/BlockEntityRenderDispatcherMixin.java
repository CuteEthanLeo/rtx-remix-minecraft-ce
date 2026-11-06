/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nvidia.rtxremix.minecraft.RemixRenderer;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Captures block entities where vanilla renders them, instead of rendering them twice.
 *
 * <p>Vanilla evaluates the block-entity renderer here and submits its commands to the
 * world's queue. Each dispatch is offered to {@code RemixRenderer.renderBlockEntityInPlace},
 * which runs the same renderer against the Remix capture stack and a private queue and
 * submits the result; the world-queue submit never happens. Vanilla's own matrix stack and
 * queue are deliberately not forwarded: the stack is camera-relative, and the capture
 * supplies an identity one so the geometry stays block-local. When Remix declines,
 * vanilla renders as usual.
 *
 * <p>Since 1.21.9 the manager renders a {@link BlockEntityRenderState}; the block entity
 * itself is looked up from the client world at the state's position.
 */
@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @WrapMethod(
        method = "render(Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"
    )
    private <S extends BlockEntityRenderState> void rtxremix$captureBlockEntityInPlace(
            S state, MatrixStack matrices, OrderedRenderCommandQueue queue,
            CameraRenderState cameraState, Operation<Void> original) {
        RtxRemixMod mod = RtxRemixMod.getInstance();
        RemixRenderer renderer = mod == null ? null : mod.getRenderer();
        MinecraftClient client = MinecraftClient.getInstance();
        BlockEntity be = renderer == null || client.world == null
                ? null
                : client.world.getBlockEntity(state.pos);
        boolean captured = be != null
                && be.hasWorld()
                && renderer.renderBlockEntityInPlace(be,
                        (captureMatrices, captureQueue) ->
                                original.call(state, captureMatrices, captureQueue, cameraState));
        if (!captured) {
            original.call(state, matrices, queue, cameraState);
        }
    }
}
