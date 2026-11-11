/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.sky;

import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * The sky queries the renderers share, answered from {@code ClientWorld}, its
 * {@code DimensionEffects} and its {@code DimensionType} exactly as vanilla's sky and
 * cloud renderers read them.
 */
public final class WorldSky {
    /** Vanilla draws the cloud layer this far above the dimension's cloud height. */
    private static final float CLOUD_LAYER_LIFT = 0.33f;

    private WorldSky() {}

    /** The dimension's sky type. */
    public static DimensionEffects.SkyType skybox(ClientWorld world) {
        return world.getDimensionEffects().getSkyType();
    }

    /** Fraction of a day turned since noon, in [0, 1): 0 at noon, 0.25 at sunset. */
    public static float skyAngle(ClientWorld world, float tickDelta) {
        return world.getSkyAngle(tickDelta);
    }

    public static float starBrightness(ClientWorld world, float tickDelta) {
        return world.getStarBrightness(tickDelta);
    }

    public static int moonPhase(ClientWorld world, float tickDelta) {
        return world.getMoonPhase();
    }

    public static float moonSize(ClientWorld world, float tickDelta) {
        return world.getMoonSize();
    }

    /** Height vanilla draws the cloud layer at, or NaN when the dimension draws no clouds. */
    public static float cloudHeight(ClientWorld world, float tickDelta) {
        Optional<Integer> cloudHeight = world.getDimension().cloudHeight();
        return cloudHeight.isPresent() ? cloudHeight.get() + CLOUD_LAYER_LIFT : Float.NaN;
    }

    /** Cloud colour as linear-in-byte RGB in [0, 1]. */
    public static Vec3d cloudColor(ClientWorld world, float tickDelta) {
        int color = world.getCloudsColor(tickDelta);
        return new Vec3d(
                ColorHelper.getRed(color) / 255.0,
                ColorHelper.getGreen(color) / 255.0,
                ColorHelper.getBlue(color) / 255.0);
    }
}
