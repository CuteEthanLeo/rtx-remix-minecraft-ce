/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * How strongly the sun lights the volumetric haze, separately from how
 * strongly it lights surfaces.
 *
 * <p>Remix lights the froxel volume with each light's radiance times its
 * {@code volumetricRadianceScale}, and shadows that in-scattering with the
 * same rays it uses for surfaces, so trees, terrain, and clouds carve shafts
 * out of a lit haze. Two things decide whether those shafts read:
 * <ul>
 *   <li>Golden hour. A low sun crosses far more air, so real haze glows much
 *       brighter toward it than its dimmed, reddened disc would suggest.
 *       Between about 2 and 20 degrees of elevation the sun's volumetric
 *       scale rises to {@link #DEFAULT_GOLDEN_PEAK} and the sky-ambient rect,
 *       which lights shadowed and sunlit haze alike, scatters less, so the
 *       shadows between shafts stay dark.</li>
 *   <li>Rain. Surfaces under an overcast sky get a fraction of a percent of
 *       the sun, but the air below the cloud deck is still lit wherever a gap
 *       lets the sun through. The volumetric scale gives the haze back a
 *       share of the clear-sky sun ({@link #DEFAULT_RAIN_SHARE}, less in
 *       thunder), and the cloud layer's own shadows turn that into slanted
 *       shafts through the gaps.</li>
 * </ul>
 */
public final class SunShafts {

    public static final boolean DEFAULT_GOLDEN_HOUR = true;
    public static final boolean DEFAULT_RAIN_SHAFTS = true;
    /** Sun volumetric scale at the golden-hour peak, around five degrees of elevation. */
    public static final float DEFAULT_GOLDEN_PEAK = 4.0f;
    /** Share of the clear-sky sun the haze keeps in full rain. */
    public static final float DEFAULT_RAIN_SHARE = 0.2f;
    /** Share of the clear-sky sun the haze keeps in a full thunderstorm. */
    static final float THUNDER_SHARE_OF_RAIN = 0.4f;
    /** Sky-ambient volumetric scale at the golden-hour peak. */
    static final float GOLDEN_AMBIENT_SCALE = 0.35f;
    /** Ceiling on the sun's volumetric scale, so the rain compensation cannot run away. */
    static final float MAX_SCALE = 150.0f;

    private static volatile boolean goldenHour = DEFAULT_GOLDEN_HOUR;
    private static volatile boolean rainShafts = DEFAULT_RAIN_SHAFTS;
    private static volatile float goldenPeak = DEFAULT_GOLDEN_PEAK;
    private static volatile float rainShare = DEFAULT_RAIN_SHARE;

    private SunShafts() {}

    public static boolean isGoldenHour() { return goldenHour; }
    public static void setGoldenHour(boolean enabled) { goldenHour = enabled; }

    public static boolean isRainShafts() { return rainShafts; }
    public static void setRainShafts(boolean enabled) { rainShafts = enabled; }

    public static float getGoldenPeak() { return goldenPeak; }
    public static void setGoldenPeak(float value) { goldenPeak = Math.max(1.0f, value); }

    public static float getRainShare() { return rainShare; }
    public static void setRainShare(float value) { rainShare = Math.max(0.0f, Math.min(1.0f, value)); }

    /**
     * Golden-hour weight: 0 below the horizon and from about 22 degrees up, 1
     * between about 3 and 8 degrees.
     *
     * @param sunHeight the y component of the unit direction toward the sun
     */
    static float goldenWeight(float sunHeight) {
        float elevation = (float) Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, sunHeight))));
        return smoothstep(-1.0f, 3.0f, elevation) * (1.0f - smoothstep(8.0f, 22.0f, elevation));
    }

    /**
     * The sun's {@code volumetricRadianceScale}.
     *
     * @param sunHeight the y component of the unit direction toward the sun
     * @param rain the rain gradient, 0..1
     * @param thunder the thunder gradient, 0..1
     * @param surfaceWeatherScale the factor weather already applies to the sun's
     *     radiance, which the rain branch divides back out of the haze
     */
    public static float sunVolumetricScale(float sunHeight, float rain, float thunder,
                                           float surfaceWeatherScale) {
        return sunVolumetricScale(sunHeight, rain, thunder, surfaceWeatherScale,
                goldenHour, rainShafts, goldenPeak, rainShare);
    }

    static float sunVolumetricScale(float sunHeight, float rain, float thunder, float surfaceWeatherScale,
                                    boolean goldenHour, boolean rainShafts, float goldenPeak, float rainShare) {
        float scale = goldenHour ? 1.0f + (goldenPeak - 1.0f) * goldenWeight(sunHeight) : 1.0f;
        float r = clamp01(rain);
        if (rainShafts && r > 0.0f && surfaceWeatherScale > 0.0f) {
            float share = lerp(1.0f, rainShare * lerp(1.0f, THUNDER_SHARE_OF_RAIN, clamp01(thunder)), r);
            scale *= Math.max(1.0f, share / surfaceWeatherScale);
        }
        return Math.min(MAX_SCALE, scale);
    }

    /**
     * The sky-ambient rect's {@code volumetricRadianceScale}: lower at a clear
     * golden hour so unshadowed sky light does not fill the dark gaps between
     * shafts, and 1 otherwise, including in rain, where that even glow is the
     * overcast haze itself.
     */
    public static float ambientVolumetricScale(float sunHeight, float weatherOpacity) {
        return ambientVolumetricScale(sunHeight, weatherOpacity, goldenHour);
    }

    static float ambientVolumetricScale(float sunHeight, float weatherOpacity, boolean goldenHour) {
        if (!goldenHour) {
            return 1.0f;
        }
        float weight = goldenWeight(sunHeight) * (1.0f - clamp01(weatherOpacity));
        return lerp(1.0f, GOLDEN_AMBIENT_SCALE, weight);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
