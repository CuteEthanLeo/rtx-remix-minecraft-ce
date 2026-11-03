/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * Rain-wet ground: which surfaces get wet, how wet they are, and what wetness
 * does to a texel.
 *
 * <p>Capture marks upward-facing quads of blocks with open sky above them by
 * appending {@link #WET_SUFFIX} to their bucket key, so their pages use twin
 * materials that sample a wet copy of the atlas. Walls, overhangs, interiors,
 * and entities stay dry. The wet copy is rewritten as the wetness level moves,
 * so the ground darkens and gains a water sheen during rain and dries slowly
 * afterwards.
 *
 * <p>Wetness follows LabPBR porosity: porous texels such as dirt soak water up
 * and darken a lot, dense ones such as stone darken less and gain more of a
 * water film.
 */
public final class WetSurfaces {

    public static final String WET_SUFFIX = "#wet";
    /** Minimum normal Y of a quad that collects rain. */
    public static final float MIN_UP_NORMAL = 0.7f;
    /** How far above a block capture looks for something that keeps the rain off. */
    public static final int MAX_SHELTER_SCAN = 24;
    /** Quantization of the wetness the atlas is rewritten at. */
    public static final int LEVELS = 12;
    /** Porosity, 0..255, assumed for a texel without a LabPBR porosity value. */
    public static final int DEFAULT_POROSITY = 128;

    /** Seconds from dry to fully wet in steady rain. */
    static final float WETTING_SECONDS = 20.0f;
    /** Seconds from fully wet to dry after the rain stops. */
    static final float DRYING_SECONDS = 90.0f;

    private static volatile boolean enabled = true;

    private float wetness;

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static String wetKey(String bucketKey) {
        return bucketKey + WET_SUFFIX;
    }

    public static boolean isWetKey(String bucketKey) {
        return bucketKey.endsWith(WET_SUFFIX);
    }

    public static String dryKey(String bucketKey) {
        return isWetKey(bucketKey)
                ? bucketKey.substring(0, bucketKey.length() - WET_SUFFIX.length())
                : bucketKey;
    }

    /**
     * Advances the wetness toward the current rain and returns its quantized
     * level, 0 for dry up to {@link #LEVELS}.
     *
     * @param rain how hard it rains where the camera is, 0..1; 0 where it snows or never rains
     * @param seconds time since the last call
     */
    public int advance(float rain, float seconds) {
        if (!enabled) {
            wetness = 0.0f;
            return 0;
        }
        float target = Math.max(0.0f, Math.min(1.0f, rain));
        float step = Math.max(0.0f, seconds);
        if (wetness < target) {
            wetness = Math.min(target, wetness + step / WETTING_SECONDS);
        } else if (wetness > target) {
            wetness = Math.max(target, wetness - step / DRYING_SECONDS);
        }
        return level(wetness);
    }

    public float wetness() {
        return wetness;
    }

    static int level(float wetness) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, wetness)) * LEVELS);
    }

    /** Porosity, 0..255, from a LabPBR specular blue channel: 0..64 is porosity, 65 and up is subsurface. */
    public static int porosityFromSpecularBlue(int blue) {
        return blue < 65 ? Math.round(blue * 255.0f / 64.0f) : 64;
    }

    /** Fills {@code wetAlbedo} and {@code wetRoughness} from the dry atlas at one wetness level. */
    public static void wetAtlas(byte[] dryAlbedo, byte[] dryRoughness, byte[] porosity,
                                int level, byte[] wetAlbedo, byte[] wetRoughness) {
        float w = level / (float) LEVELS;
        int pixels = dryRoughness.length;
        for (int i = 0; i < pixels; i++) {
            float p = (porosity[i] & 0xFF) / 255.0f;
            float darken = 1.0f - w * (0.12f + 0.28f * p);
            int i4 = i * 4;
            wetAlbedo[i4] = (byte) Math.round((dryAlbedo[i4] & 0xFF) * darken);
            wetAlbedo[i4 + 1] = (byte) Math.round((dryAlbedo[i4 + 1] & 0xFF) * darken);
            wetAlbedo[i4 + 2] = (byte) Math.round((dryAlbedo[i4 + 2] & 0xFF) * darken);
            wetAlbedo[i4 + 3] = dryAlbedo[i4 + 3];

            float film = w * (1.0f - 0.55f * p);
            float roughness = (dryRoughness[i] & 0xFF) * (1.0f - 0.5f * film);
            wetRoughness[i] = (byte) Math.round(Math.max(Math.min(MIN_WET_ROUGHNESS, dryRoughness[i] & 0xFF),
                    roughness));
        }
    }

    /** A wet film smooths a surface, but a block face never turns into a perfect mirror. */
    private static final float MIN_WET_ROUGHNESS = 0.3f * 255.0f;
}
