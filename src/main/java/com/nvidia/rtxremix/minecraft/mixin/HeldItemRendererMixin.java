/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nvidia.rtxremix.minecraft.entity.EntityRenderContext;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the capture who is holding the item being rendered, and for the
 * first-person view which vanilla hand it is in.
 *
 * <p>Every hand-held render, first person and every entity's hands alike, passes
 * through {@code renderItem(LivingEntity, ...)}, so the holder recorded here is
 * what lets the item renderer's hook decide whether a held torch is burning.
 *
 * <p>TODO(1.21.11): these hooks now run while the hand only submits commands; the
 * capture replays them after the hooks have returned, so the holder and hand are no
 * longer in scope when the item's vertices reach the capture. Per-hand held-light
 * placement and the held-flame holder check degrade until the context is carried on
 * the submitted item command instead.
 */
@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {

    private static final String rtxremix$RENDER_HELD_ITEM =
            "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

    @Inject(method = rtxremix$RENDER_HELD_ITEM, at = @At("HEAD"))
    private void rtxremix$beginHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext mode,
                                        PoseStack matrices,
                                        SubmitNodeCollector queue, int light, CallbackInfo ci) {
        EntityRenderContext context = EntityRenderContext.get();
        if (context.isRemixPass()) context.setHeldItemHolder(entity);
    }

    @Inject(method = rtxremix$RENDER_HELD_ITEM, at = @At("RETURN"))
    private void rtxremix$endHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext mode,
                                      PoseStack matrices,
                                      SubmitNodeCollector queue, int light, CallbackInfo ci) {
        EntityRenderContext context = EntityRenderContext.get();
        if (context.isRemixPass()) context.clearHeldItemHolder();
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void rtxremix$beginFirstPersonHand(
            AbstractClientPlayer player,
            float tickDelta,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            PoseStack matrices,
            SubmitNodeCollector queue,
            int light,
            CallbackInfo ci) {
        EntityRenderContext context = EntityRenderContext.get();
        if (context.isRemixPass()) context.setActiveHand(hand);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void rtxremix$endFirstPersonHand(
            AbstractClientPlayer player,
            float tickDelta,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            PoseStack matrices,
            SubmitNodeCollector queue,
            int light,
            CallbackInfo ci) {
        EntityRenderContext context = EntityRenderContext.get();
        if (context.isRemixPass()) context.clearActiveHand();
    }
}
