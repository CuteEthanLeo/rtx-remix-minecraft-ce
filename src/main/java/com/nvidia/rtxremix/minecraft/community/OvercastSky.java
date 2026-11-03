/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * How rain and thunder change the sky's lighting when the overcast weather
 * look is on.
 *
 * <p>The upstream overcast dome is driven by the undimmed sun, so a rainy sky
 * stays about as bright as a clear one while direct sun drops to a fraction of
 * a percent: the ground looks sunlit under a grey sky. The dome is scaled down
 * here. Upstream also leaves the daytime sky ambient off, so in daytime rain
 * nothing lights the fog and the shaded ground except the dome's escaped rays.
 * A daytime ambient that rises with the weather gives the soft, even light of
 * an overcast day and something for the rain haze to scatter.
 */
public final class OvercastSky {

    /** Overcast dome radiance relative to upstream. */
    static final float DOME_SCALE = 0.9f;
    /** Daytime sky-ambient boost at full weather, added to the player's day boost. */
    static final float DAY_AMBIENT_AT_FULL_WEATHER = 0.25f;

    private OvercastSky() {}

    /** Factor for the rain and thunder dome intensities. */
    public static float domeScale() {
        return CommunityLook.isOvercastWeather() ? DOME_SCALE : 1.0f;
    }

    /**
     * Extra sky-ambient multiplier for the current weather.
     *
     * @param weatherOpacity the overcast dome's opacity, 0 in clear weather and 1 in full rain or thunder
     * @param daylight 1 by day and 0 by night; the night ambient already covers the dark hours
     */
    public static float extraAmbient(float weatherOpacity, float daylight) {
        if (!CommunityLook.isOvercastWeather()) {
            return 0.0f;
        }
        return DAY_AMBIENT_AT_FULL_WEATHER * clamp01(weatherOpacity) * clamp01(daylight);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
