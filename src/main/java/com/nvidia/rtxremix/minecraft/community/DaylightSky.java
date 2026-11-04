/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * The "Natural daylight" look: a clear day lit by a warm sun and a blue sky
 * in a believable ratio, instead of a bright, flat, white one.
 *
 * <p>Upstream multiplies the daytime sky's scattering by 16, so the dome
 * lights shaded ground nearly as strongly as the sun lights open ground: sun
 * and shade differ little, and shadows read grey instead of blue. A lower
 * multiplier keeps the sky's colour but returns the sun its share; auto
 * exposure then lifts the whole frame back, so sunlit ground ends up brighter
 * and shade keeps its blue, rather than the picture getting darker. The night
 * multiplier is unchanged.
 *
 * <p>The sun's Mie halo is boosted by about ten upstream, which with the
 * vanilla-sized sun billboard spreads into a large overexposed blot. A lower
 * boost keeps a bright but tight glow, and the billboard is drawn at half the
 * vanilla size. The haze half of the look is {@code confs/community/daylight.conf},
 * whose strength {@code ConfLayerDriver} gates with {@link #isEnabled()}.
 */
public final class DaylightSky {

    public static final boolean DEFAULT_ENABLED = true;
    /** Upstream daytime sky scattering multiplier. */
    public static final float UPSTREAM_DAY_SCATTERING = 16.0f;
    public static final float DEFAULT_DAY_SCATTERING = 11.0f;
    /** Upstream Mie halo boost around the sun, as {@code sky_atmosphere.comp} had it. */
    public static final float UPSTREAM_HALO_BOOST = 10.35f;
    public static final float DEFAULT_HALO_BOOST = 4.0f;
    /** Sun billboard size relative to vanilla. */
    static final float SUN_SIZE_SCALE = 0.5f;

    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile float dayScattering = DEFAULT_DAY_SCATTERING;
    private static volatile float haloBoost = DEFAULT_HALO_BOOST;

    private DaylightSky() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    /** Live tuning for {@code /rtx ce skyfill}; the look switch still gates it. */
    public static float getDayScatteringTuning() { return dayScattering; }
    public static void setDayScatteringTuning(float value) { dayScattering = Math.max(0.0f, value); }

    /** Live tuning for {@code /rtx ce halo}; the look switch still gates it. */
    public static float getHaloBoostTuning() { return haloBoost; }
    public static void setHaloBoostTuning(float value) { haloBoost = Math.max(0.0f, value); }

    /** Daytime sky scattering multiplier for the atmosphere generator. */
    public static float dayScattering() {
        return enabled ? dayScattering : UPSTREAM_DAY_SCATTERING;
    }

    /** Mie halo boost around the sun for the atmosphere shader. */
    public static float haloBoost() {
        return enabled ? haloBoost : UPSTREAM_HALO_BOOST;
    }

    /** Sun billboard half-size, given the vanilla one. */
    public static float sunHalfSize(float vanillaHalfSize) {
        return enabled ? vanillaHalfSize * SUN_SIZE_SCALE : vanillaHalfSize;
    }

    /** Strength of the daylight conf layer, given the day profile's strength. */
    public static float layerStrength(float dayStrength) {
        return enabled ? dayStrength : 0.0f;
    }
}
