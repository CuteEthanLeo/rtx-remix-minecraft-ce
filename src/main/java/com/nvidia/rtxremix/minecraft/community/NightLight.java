/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * Moonlight under the starry-night look: three times the upstream moonlight,
 * cooler, following the phase, and a brighter moon disc, so a moonlit
 * landscape reads blue-silver with real shadows instead of flat grey.
 *
 * <p>The moon is the night's main light: the sky it lights is about a tenth of
 * it with the physical atmosphere, as for the sun by day, instead of several
 * times it. That is why the moon is stronger than in 0.1.0-ce.3 (2x).
 */
public final class NightLight {

    static final float MOON_LUMINANCE_SCALE = 3.0f;
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

    /** Share of full-moon light at new moon; the dark of the month, not a black one. */
    static final float NEW_MOON_LIGHT = 0.3f;

    /**
     * Factor on moonlight and the moonlit sky for the phase, from vanilla's
     * moon size (1 at full moon, 0 at new moon). Real moonlight falls off far
     * faster than the lit area; this keeps nights playable while still making
     * a full moon clearly the brightest. 1 when the look is off.
     */
    public static float moonPhaseScale(float moonSize) {
        if (!CommunityLook.isStarryNight()) {
            return 1.0f;
        }
        float size = Math.max(0.0f, Math.min(1.0f, moonSize));
        return NEW_MOON_LIGHT + (1.0f - NEW_MOON_LIGHT) * size;
    }

    /** RGB tint of moonlight; the blue channel stays 1 so luminance scales stay comparable. */
    public static float[] moonTint() {
        return CommunityLook.isStarryNight() ? MOON_TINT : UPSTREAM_MOON_TINT;
    }
}
