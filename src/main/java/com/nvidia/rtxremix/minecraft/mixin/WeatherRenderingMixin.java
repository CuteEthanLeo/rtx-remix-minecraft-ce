/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.weather.RemixWeatherRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands vanilla's rain and snow draws to {@link RemixWeatherRenderer}, which draws them over
 * the composited Remix image against the traced depth. Since 1.21.2 the precipitation draw
 * lives in {@link WeatherRendering} rather than the world renderer. Render thread only.
 */
@Mixin(WeatherRendering.class)
public abstract class WeatherRenderingMixin {
    private RemixWeatherRenderer rtxremix$weatherRenderer;
    private Identifier rtxremix$weatherTexture;

    @Inject(
            method = "renderPrecipitation(Lnet/minecraft/world/World;Lnet/minecraft/client/render/LightmapTextureManager;IFLnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"))
    private void rtxremix$beginWeatherCapture(
            World world,
            LightmapTextureManager lightmapTextureManager,
            int ticks,
            float tickDelta,
            Vec3d pos,
            CallbackInfo ci) {
        rtxremix$weatherRenderer = RtxRemixMod.isRemixWorldRendering()
                ? RtxRemixMod.getInstance().getRenderer().getWeatherRenderer()
                : null;
        rtxremix$weatherTexture = null;
        if (rtxremix$weatherRenderer != null) {
            rtxremix$weatherRenderer.beginCaptureFrame(lightmapTextureManager);
        }
    }

    @WrapOperation(
            method = "renderPrecipitation(Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/util/math/Vec3d;IFLjava/util/List;Ljava/util/List;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V"))
    private void rtxremix$captureWeatherTexture(
            int textureUnit,
            Identifier texture,
            Operation<Void> original) {
        if (rtxremix$weatherRenderer != null && textureUnit == 0) {
            rtxremix$weatherTexture = texture;
            return;
        }
        original.call(textureUnit, texture);
    }

    @WrapOperation(
            method = "renderPieces",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V"))
    private void rtxremix$captureWeatherDraw(
            BuiltBuffer buffer,
            Operation<Void> original) {
        if (rtxremix$weatherRenderer == null) {
            original.call(buffer);
            return;
        }
        rtxremix$weatherRenderer.capture(buffer, rtxremix$weatherTexture);
    }

    @Inject(
            method = "renderPrecipitation(Lnet/minecraft/world/World;Lnet/minecraft/client/render/LightmapTextureManager;IFLnet/minecraft/util/math/Vec3d;)V",
            at = @At("RETURN"))
    private void rtxremix$endWeatherCapture(
            World world,
            LightmapTextureManager lightmapTextureManager,
            int ticks,
            float tickDelta,
            Vec3d pos,
            CallbackInfo ci) {
        rtxremix$weatherRenderer = null;
        rtxremix$weatherTexture = null;
    }
}
