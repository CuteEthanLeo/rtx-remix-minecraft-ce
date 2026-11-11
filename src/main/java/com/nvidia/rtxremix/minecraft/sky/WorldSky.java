/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.sky;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

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
        EnvironmentAttributeProbe interpolator =
                Minecraft.getInstance().gameRenderer.getMainCamera().attributeProbe();
        return interpolator.getValue(attribute, tickDelta);
    }

    /** The dimension's sky type, formerly {@code DimensionEffects.SkyType}. */
    public static DimensionType.Skybox skybox(ClientLevel world) {
        return world.dimensionType().skybox();
    }

    /** Fraction of a day turned since noon, in [0, 1): 0 at noon, 0.25 at sunset, as 1.21.1. */
    public static float skyAngle(ClientLevel world, float tickDelta) {
        float degrees = visual(EnvironmentAttributes.SUN_ANGLE, tickDelta);
        float fraction = degrees / 360.0f;
        return fraction - (float) Math.floor(fraction);
    }

    public static float starBrightness(ClientLevel world, float tickDelta) {
        return visual(EnvironmentAttributes.STAR_BRIGHTNESS, tickDelta);
    }

    public static int moonPhase(ClientLevel world, float tickDelta) {
        return visual(EnvironmentAttributes.MOON_PHASE, tickDelta).index();
    }

    public static float moonSize(ClientLevel world, float tickDelta) {
        return MOON_SIZES[moonPhase(world, tickDelta) & 7];
    }

    /** Cloud base height, or NaN when the dimension draws no clouds (transparent cloud colour). */
    public static float cloudHeight(ClientLevel world, float tickDelta) {
        int color = visual(EnvironmentAttributes.CLOUD_COLOR, tickDelta);
        if (ARGB.alpha(color) == 0) {
            return Float.NaN;
        }
        return visual(EnvironmentAttributes.CLOUD_HEIGHT, tickDelta);
    }

    /** Cloud colour as linear-in-byte RGB in [0, 1], formerly {@code ClientWorld.getCloudsColor}. */
    public static Vec3 cloudColor(ClientLevel world, float tickDelta) {
        int color = visual(EnvironmentAttributes.CLOUD_COLOR, tickDelta);
        return new Vec3(
                ARGB.red(color) / 255.0,
                ARGB.green(color) / 255.0,
                ARGB.blue(color) / 255.0);
    }
}
