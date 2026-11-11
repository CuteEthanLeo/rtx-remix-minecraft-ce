/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.entity.ItemRenderStateStack;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records which stack an item render state was filled from; see {@link ItemRenderStateStack}.
 *
 * <p>Every renderer fills a state through this overload, directly or through
 * {@code updateForLivingEntity} and {@code updateForNonLivingEntity}. The overload without
 * the hand flag is the one item models recurse through to add a nested stack's layers, a
 * bundle's selected item for one, and those layers stay attributed to the outer stack.
 */
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {

    @Inject(
            method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/LivingEntity;I)V",
            at = @At("HEAD"))
    private void rtxremix$recordStack(
            ItemRenderState renderState,
            ItemStack stack,
            ModelTransformationMode mode,
            boolean leftHand,
            World world,
            LivingEntity entity,
            int seed,
            CallbackInfo ci) {
        ((ItemRenderStateStack) renderState).rtxremix$setStack(stack);
    }
}
