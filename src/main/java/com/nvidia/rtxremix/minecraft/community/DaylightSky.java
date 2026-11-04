/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * The "Natural daylight" look: a physical sky lit by the same sun, moon, and
 * atmosphere as the ground, instead of a bright, flat, white one.
 *
 * <p>Upstream lit the sky with empirical multipliers (16 by day, 70 by night)
 * and attenuated the sun with a separate airmass formula about three times
 * stronger than the sky shader's air. The sky then gave half the sun's light
 * at noon and over four times it at a low sun, so shadows read grey, golden
 * hour drowned in blue-white fill, and the sun itself was tinted orange at
 * noon. With the look on, {@code sky/AtmosphereModel} is the one atmosphere
 * (with ozone and a multiple-scattering LUT) for both, and the sky is lit by
 * the distant light's own irradiance, so their ratio is the real one at every
 * hour. Auto exposure lifts the frame back.
 *
 * <p>The sun's Mie halo is physical (no boost), and the sun is drawn as a
 * small round limb-darkened disc instead of the vanilla billboard, which at
 * an 8.5 degree half angle spread into a large overexposed blot. The haze
 * half of the look is {@code confs/community/daylight.conf}, whose strength
 * {@code ConfLayerDriver} gates with {@link #isEnabled()}.
 */
public final class DaylightSky {

    public static final boolean DEFAULT_ENABLED = true;
    /** Upstream daytime sky scattering multiplier. */
    public static final float UPSTREAM_DAY_SCATTERING = 16.0f;
    /** Factor on the physical sky's brightness; 1 is the real sun-to-sky ratio. */
    public static final float DEFAULT_SKY_FILL = 1.0f;
    /** Upstream Mie halo boost around the sun, as {@code sky_atmosphere.comp} had it. */
    public static final float UPSTREAM_HALO_BOOST = 10.35f;
    public static final float DEFAULT_HALO_BOOST = 1.0f;

    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile float skyFill = DEFAULT_SKY_FILL;
    private static volatile float haloBoost = DEFAULT_HALO_BOOST;

    private DaylightSky() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    /** Live tuning for {@code /rtx ce skyfill}; the look switch still gates it. */
    public static float getSkyFillTuning() { return skyFill; }
    public static void setSkyFillTuning(float value) { skyFill = Math.max(0.0f, value); }

    /** Live tuning for {@code /rtx ce halo}; the look switch still gates it. */
    public static float getHaloBoostTuning() { return haloBoost; }
    public static void setHaloBoostTuning(float value) { haloBoost = Math.max(0.0f, value); }

    /** Whether the sky and the sun and moon lights use the physical atmosphere. */
    public static boolean isPhysicalAtmosphere() {
        return enabled;
    }

    /** Factor on the physical sky's brightness. */
    public static float skyFill() {
        return skyFill;
    }

    /** Upstream daytime sky scattering multiplier, for the legacy atmosphere. */
    public static float dayScattering() {
        return UPSTREAM_DAY_SCATTERING;
    }

    /** Mie halo boost around the sun for the atmosphere shader. */
    public static float haloBoost() {
        return enabled ? haloBoost : UPSTREAM_HALO_BOOST;
    }

    /** Whether the sun is the round physical disc rather than the vanilla billboard. */
    public static boolean isRoundSun() {
        return enabled;
    }

    /** Strength of the daylight conf layer, given the day profile's strength. */
    public static float layerStrength(float dayStrength) {
        return enabled ? dayStrength : 0.0f;
    }
}
