/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.entity.EntityRenderContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the capture who holds an item drawn in an entity's hand. Since 1.21.2 the hand layer
 * renders from the entity's render state, since 1.21.4 from the item render state it
 * carries for each hand, and no longer through
 * {@code HeldItemRenderer.renderItem(LivingEntity, ...)}, so the holder is the entity the
 * dispatcher is rendering. {@code PlayerHeldItemFeatureRenderer} reaches this through its
 * super call.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void rtxremix$beginHeldItem(
            ArmedEntityRenderState state,
            ItemRenderState itemState,
            Arm arm,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        EntityRenderContext context = EntityRenderContext.get();
        if (context.isRemixPass() && context.renderedEntity() instanceof LivingEntity holder) {
            context.setHeldItemHolder(holder);
        }
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void rtxremix$endHeldItem(
            ArmedEntityRenderState state,
            ItemRenderState itemState,
            Arm arm,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        EntityRenderContext context = EntityRenderContext.get();
        if (context.isRemixPass()) {
            context.clearHeldItemHolder();
        }
    }
}
