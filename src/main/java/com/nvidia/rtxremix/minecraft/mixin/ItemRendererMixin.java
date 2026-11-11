/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.entity.EntityRenderContext;
import com.nvidia.rtxremix.minecraft.entity.HeldItemEffectsRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the lit element of a hand-held item. */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    /**
     * Adds the item's lit element right after its baked model, while the matrix
     * stack still holds the item's block-space pose. Running here rather than in
     * a hand-specific renderer covers every hand-held render with one hook.
     */
    // 1.21.4 draws each baked layer of an item render state through this static overload,
    // with the layer's pose already on the stack. The stack itself comes from the capture
    // context, where ItemRenderStateMixin put it; the first baked layer takes it, so an
    // item made of several layers still gets one lit element.
    @Inject(
        method = "renderItem(Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;[IIILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V",
            shift = At.Shift.AFTER
        )
    )
    private static void rtxremix$renderHeldFlame(ModelTransformationMode mode,
                                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, int overlay, int[] tints, BakedModel model,
                                                 RenderLayer renderLayer, ItemRenderState.Glint glint,
                                                 CallbackInfo ci) {
        ItemStack stack = EntityRenderContext.get().takeRenderedItem();
        if (stack != null) {
            HeldItemEffectsRenderer.render(stack, mode, matrices, vertexConsumers, model);
        }
    }
}
