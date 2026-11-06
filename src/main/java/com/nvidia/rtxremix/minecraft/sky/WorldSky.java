/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.sky;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeInterpolator;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.dimension.DimensionType;

/**
 * The 1.21.1 sky queries ({@code World.getSkyAngle}, {@code getStarBrightness},
 * {@code getMoonPhase}, {@code getMoonSize}, {@code ClientWorld.getCloudsColor},
 * {@code DimensionEffects}) rebuilt on 1.21.11's environment attributes, read through the
 * camera's interpolator exactly as vanilla's sky and cloud renderers read them.
 */
public final class WorldSky {
    private static final float[] MOON_SIZES = {1.0f, 0.75f, 0.5f, 0.25f, 0.0f, 0.25f, 0.5f, 0.75f};

    private WorldSky() {}

    private static <T> T visual(EnvironmentAttribute<T> attribute, float tickDelta) {
        EnvironmentAttributeInterpolator interpolator =
                MinecraftClient.getInstance().gameRenderer.getCamera().getEnvironmentAttributeInterpolator();
        return interpolator.get(attribute, tickDelta);
    }

    /** The dimension's sky type, formerly {@code DimensionEffects.SkyType}. */
    public static DimensionType.Skybox skybox(ClientWorld world) {
        return world.getDimension().skybox();
    }

    /** Fraction of a day turned since noon, in [0, 1): 0 at noon, 0.25 at sunset, as 1.21.1. */
    public static float skyAngle(ClientWorld world, float tickDelta) {
        float degrees = visual(EnvironmentAttributes.SUN_ANGLE_VISUAL, tickDelta);
        float fraction = degrees / 360.0f;
        return fraction - (float) Math.floor(fraction);
    }

    public static float starBrightness(ClientWorld world, float tickDelta) {
        return visual(EnvironmentAttributes.STAR_BRIGHTNESS_VISUAL, tickDelta);
    }

    public static int moonPhase(ClientWorld world, float tickDelta) {
        return visual(EnvironmentAttributes.MOON_PHASE_VISUAL, tickDelta).getIndex();
    }

    public static float moonSize(ClientWorld world, float tickDelta) {
        return MOON_SIZES[moonPhase(world, tickDelta) & 7];
    }

    /** Cloud base height, or NaN when the dimension draws no clouds (transparent cloud colour). */
    public static float cloudHeight(ClientWorld world, float tickDelta) {
        int color = visual(EnvironmentAttributes.CLOUD_COLOR_VISUAL, tickDelta);
        if (ColorHelper.getAlpha(color) == 0) {
            return Float.NaN;
        }
        return visual(EnvironmentAttributes.CLOUD_HEIGHT_VISUAL, tickDelta);
    }

    /** Cloud colour as linear-in-byte RGB in [0, 1], formerly {@code ClientWorld.getCloudsColor}. */
    public static Vec3d cloudColor(ClientWorld world, float tickDelta) {
        int color = visual(EnvironmentAttributes.CLOUD_COLOR_VISUAL, tickDelta);
        return new Vec3d(
                ColorHelper.getRed(color) / 255.0,
                ColorHelper.getGreen(color) / 255.0,
                ColorHelper.getBlue(color) / 255.0);
    }
}
