/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.entity.EntityRenderContext;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockQuadEmitter;
import com.nvidia.rtxremix.minecraft.texture.BlockTextureManager;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Formerly recorded the terrain layer of the block item being rendered, and added the
 * lit element of a hand-held item ({@code HeldItemEffectsRenderer}) right after the
 * item's baked model.
 *
 * <p>TODO(1.21.11): {@code ItemRenderer} is now a static quad emitter fed by
 * {@code ItemRenderState} from the command queue; neither the {@code ItemStack} nor the
 * display transform is in scope there, and the stack-aware entry point
 * ({@code HeldItemRenderer.renderItem}) only submits commands that are replayed later.
 * Block-item layer classification and the held flame/smoke are disabled until they are
 * re-hooked on {@code ItemRenderState} / {@code ItemCommandRenderer}.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    /**
     * Asks the question terrain asks of a placed block, through the same
     * classifier, so a held block is classified exactly as its placed
     * counterpart is. Kept for the re-hook.
     */
    @SuppressWarnings("unused")
    private static int rtxremix$blockLayer(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                ? BlockTextureManager.layerType(
                        BlockQuadEmitter.classifyBlockLayer(blockItem.getBlock().getDefaultState()))
                : EntityRenderContext.NO_BLOCK_ITEM;
    }
}
