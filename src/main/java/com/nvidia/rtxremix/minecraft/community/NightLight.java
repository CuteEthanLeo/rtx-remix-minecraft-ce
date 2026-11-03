/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * Moonlight under the starry-night look: about twice the upstream moonlight,
 * cooler, and a brighter moon disc, so a moonlit landscape reads blue-silver
 * with real shadows instead of flat grey.
 */
public final class NightLight {

    static final float MOON_LUMINANCE_SCALE = 2.0f;
    static final float MOON_DISC_RADIANCE = 0.6f;
    private static final float[] MOON_TINT = {0.75f, 0.85f, 1.0f};
    private static final float[] UPSTREAM_MOON_TINT = {0.85f, 0.9f, 1.0f};

    private NightLight() {}

    /** Factor on the moon's direct-light luminance. */
    public static float moonLuminanceScale() {
        return CommunityLook.isStarryNight() ? MOON_LUMINANCE_SCALE : 1.0f;
    }

    /** Emitted radiance of the moon disc, or {@code upstream} when the look is off. */
    public static float moonDiscRadiance(float upstream) {
        return CommunityLook.isStarryNight() ? MOON_DISC_RADIANCE : upstream;
    }

    /** RGB tint of moonlight; the blue channel stays 1 so luminance scales stay comparable. */
    public static float[] moonTint() {
        return CommunityLook.isStarryNight() ? MOON_TINT : UPSTREAM_MOON_TINT;
    }
}
