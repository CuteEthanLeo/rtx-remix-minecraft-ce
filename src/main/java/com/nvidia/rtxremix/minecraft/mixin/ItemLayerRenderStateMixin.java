/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.entity.EntityRenderContext;
import com.nvidia.rtxremix.minecraft.entity.HeldItemEffectsRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Adds the lit element of a hand-held item. */
@Mixin(ItemRenderState.LayerRenderState.class)
public class ItemLayerRenderStateMixin {
    @Shadow
    Transformation transform;

    /**
     * Adds the item's lit element right after its baked quads, while the matrix
     * stack still holds the item's block-space pose. Running here rather than in
     * a hand-specific renderer covers every hand-held render with one hook.
     *
     * <p>Each baked layer of an item render state is drawn through this call, with the
     * layer's display transform already on the stack; since 1.21.5 the layer, not a baked
     * model, owns that transform. The stack itself comes from the capture context, where
     * {@code ItemRenderStateMixin} put it; the first baked layer takes it, so an item made
     * of several layers still gets one lit element. Wrapped rather than injected after,
     * because Fabric's renderer redirects the same call.
     */
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/ItemRenderer;renderItem(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V"
        )
    )
    private void rtxremix$renderHeldFlame(ItemDisplayContext mode,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                          int light, int overlay, int[] tints, List<BakedQuad> quads,
                                          RenderLayer renderLayer, ItemRenderState.Glint glint,
                                          Operation<Void> original) {
        original.call(mode, matrices, vertexConsumers, light, overlay, tints, quads, renderLayer, glint);
        ItemStack stack = EntityRenderContext.get().takeRenderedItem();
        if (stack != null) {
            HeldItemEffectsRenderer.render(stack, mode, matrices, vertexConsumers, transform.scale().x());
        }
    }
}
