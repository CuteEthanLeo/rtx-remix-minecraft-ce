/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.entity.EntityRenderContext;
import com.nvidia.rtxremix.minecraft.entity.ItemRenderStateStack;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockQuadEmitter;
import com.nvidia.rtxremix.minecraft.texture.BlockTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the capture which item a render state is drawing: the terrain layer of a block
 * item, and the stack itself for the lit element {@code ItemRendererMixin} adds.
 *
 * <p>Minecraft routes ordinary opaque items through its translucent item layer,
 * and clear glass through its cutout one, so the render layer alone cannot tell
 * a pane of stained glass from an apple, or clear glass from a torch. The
 * {@code ItemStack} can. Since 1.21.4 every item draw, in a hand, on the ground or in
 * a frame, is one {@code ItemRenderState.render} call, which is the last point before
 * the geometry reaches capture; the stack is the one {@code ItemModelManagerMixin}
 * recorded when the state was filled.
 */
@Mixin(ItemRenderState.class)
public abstract class ItemRenderStateMixin implements ItemRenderStateStack {

    private ItemStack rtxremix$stack = ItemStack.EMPTY;

    @Override
    public void rtxremix$setStack(ItemStack stack) {
        rtxremix$stack = stack;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void rtxremix$beginItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                    int light, int overlay, CallbackInfo ci) {
        EntityRenderContext context = EntityRenderContext.get();
        if (context.isRemixPass()) {
            context.setBlockItemLayer(rtxremix$blockLayer(rtxremix$stack));
            context.setRenderedItem(rtxremix$stack);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void rtxremix$endItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                  int light, int overlay, CallbackInfo ci) {
        EntityRenderContext context = EntityRenderContext.get();
        if (context.isRemixPass()) {
            context.setBlockItemLayer(EntityRenderContext.NO_BLOCK_ITEM);
            context.setRenderedItem(null);
        }
    }

    /**
     * Asks the question terrain asks of a placed block, through the same
     * classifier, so a held block is classified exactly as its placed
     * counterpart is.
     */
    private static int rtxremix$blockLayer(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                ? BlockTextureManager.layerType(
                        BlockQuadEmitter.classifyBlockLayer(blockItem.getBlock().getDefaultState()))
                : EntityRenderContext.NO_BLOCK_ITEM;
    }
}
