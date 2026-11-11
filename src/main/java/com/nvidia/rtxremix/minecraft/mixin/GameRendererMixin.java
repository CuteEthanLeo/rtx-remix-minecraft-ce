/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.RemixRenderer;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves the first-person arm/item draw into Remix while preserving Minecraft's
 * own HeldItemRenderer animation and selection logic, brackets the GUI tail
 * of its render method with a profiling scope, and has input capture follow
 * the Remix menus at the start of each frame.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Redirect(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"
            ))
    private void rtxremix$captureFirstPersonModel(
            HeldItemRenderer heldItemRenderer,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider.Immediate vertexConsumers,
            ClientPlayerEntity player,
            int light) {
        RemixRenderer renderer = RtxRemixMod.renderer;
        if (!RtxRemixMod.shouldCaptureFirstPersonModel()) {
            heldItemRenderer.renderItem(tickDelta, matrices, vertexConsumers, player, light);
            return;
        }

        renderer.captureFirstPersonModel(heldItemRenderer, tickDelta, matrices, player, light);
    }

    /**
     * After the first-person hand has been captured and before the screen effects, which
     * draw over the composited image. renderWorld reaches this call on every path, including
     * third person, sleeping, a hidden hand and the panorama, where renderHand draws nothing.
     */
    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderOverlays(ZF)V",
                    shift = At.Shift.BEFORE
            ))
    private void rtxremix$submitBeforeScreenOverlays(
            RenderTickCounter tickCounter,
            CallbackInfo ci) {
        RtxRemixMod.submitRemixFrameIfReady();
    }

    /**
     * The GUI tail of GameRenderer.render, from vanilla's {@code swap("gui")} to
     * the method's return: HUD, in-game screen or overlay, toasts, and the final
     * DrawContext flush. Vanilla only reaches that swap while a world is loaded,
     * so menu-only frames have no GUI zone. WorldRender.END accounts for the
     * world half of the frame.
     */
    private ProfileScope rtxremix$guiScope;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V"
            ))
    private void rtxremix$beginGuiScope(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci) {
        rtxremix$closeGuiScope();
        if (ProfileScope.isJavaScopeEnabled()) {
            rtxremix$guiScope = ProfileScope.begin("GameRenderer.render.gui")
                    .plotOnClose("Java.GameRenderer.render.gui.ms");
        }
    }

    /** Before any screen draws, so the frame's hover state already matches input capture. */
    @Inject(method = "render", at = @At("HEAD"))
    private void rtxremix$followRemixMenuVisibility(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci) {
        RtxRemixMod.followRemixMenuVisibility();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void rtxremix$endGuiScope(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci) {
        rtxremix$closeGuiScope();
    }

    private void rtxremix$closeGuiScope() {
        ProfileScope scope = rtxremix$guiScope;
        if (scope != null) {
            rtxremix$guiScope = null;
            scope.close();
        }
    }
}
