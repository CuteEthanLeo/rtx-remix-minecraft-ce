/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.weather.RemixWeatherRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hands vanilla's rain and snow draws to {@link RemixWeatherRenderer}, which draws them over
 * the composited Remix image against the traced depth. Since 1.21.2 the precipitation draw
 * lives in {@link WeatherRendering} rather than the world renderer. Render thread only.
 *
 * <p>Since 1.21.4 vanilla writes the pieces into the weather render layer's buffer of the
 * shared entity vertex consumers, which the world renderer flushes later. While capturing,
 * the pieces go into a buffer of the same vertex format that this mixin opens instead, so
 * the shared provider never sees a weather layer and has nothing to draw for it.
 */
@Mixin(WeatherRendering.class)
public abstract class WeatherRenderingMixin {
    private static final String rtxremix$RENDER_PRECIPITATION =
            "renderPrecipitation(Lnet/minecraft/world/World;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;IF"
            + "Lnet/minecraft/util/math/Vec3d;)V";
    private static final String rtxremix$DRAW_PRECIPITATION =
            "renderPrecipitation(Lnet/minecraft/client/render/VertexConsumerProvider;"
            + "Lnet/minecraft/util/math/Vec3d;IFLjava/util/List;Ljava/util/List;)V";

    private RemixWeatherRenderer rtxremix$weatherRenderer;
    private Identifier rtxremix$weatherTexture;

    @Inject(method = rtxremix$RENDER_PRECIPITATION, at = @At("HEAD"))
    private void rtxremix$beginWeatherCapture(
            World world,
            VertexConsumerProvider vertexConsumers,
            int ticks,
            float tickDelta,
            Vec3d pos,
            CallbackInfo ci) {
        rtxremix$weatherRenderer = RtxRemixMod.isRemixWorldRendering()
                ? RtxRemixMod.getInstance().getRenderer().getWeatherRenderer()
                : null;
        rtxremix$weatherTexture = null;
        if (rtxremix$weatherRenderer != null) {
            rtxremix$weatherRenderer.beginCaptureFrame(
                    MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager());
        }
    }

    @ModifyArg(
            method = rtxremix$DRAW_PRECIPITATION,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/RenderLayer;getWeather(Lnet/minecraft/util/Identifier;Z)Lnet/minecraft/client/render/RenderLayer;"),
            index = 0)
    private Identifier rtxremix$captureWeatherTexture(Identifier texture) {
        rtxremix$weatherTexture = texture;
        return texture;
    }

    @WrapOperation(
            method = rtxremix$DRAW_PRECIPITATION,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumerProvider;getBuffer(Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/VertexConsumer;"))
    private VertexConsumer rtxremix$openWeatherBuffer(
            VertexConsumerProvider vertexConsumers,
            RenderLayer weatherLayer,
            Operation<VertexConsumer> original) {
        if (rtxremix$weatherRenderer == null) {
            return original.call(vertexConsumers, weatherLayer);
        }
        // The weather layer's own format and draw mode; RemixWeatherRenderer draws it later.
        return Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
    }

    @Inject(method = "renderPieces", at = @At("RETURN"))
    private void rtxremix$captureWeatherDraw(
            VertexConsumer vertexConsumer,
            List<?> pieces,
            Vec3d pos,
            float intensity,
            int range,
            float gradient,
            CallbackInfo ci) {
        if (rtxremix$weatherRenderer != null) {
            // The consumer is the buffer rtxremix$openWeatherBuffer opened for this call.
            rtxremix$weatherRenderer.capture(((BufferBuilder) vertexConsumer).end(), rtxremix$weatherTexture);
        }
    }

    @Inject(method = rtxremix$RENDER_PRECIPITATION, at = @At("RETURN"))
    private void rtxremix$endWeatherCapture(
            World world,
            VertexConsumerProvider vertexConsumers,
            int ticks,
            float tickDelta,
            Vec3d pos,
            CallbackInfo ci) {
        rtxremix$weatherRenderer = null;
        rtxremix$weatherTexture = null;
    }
}
