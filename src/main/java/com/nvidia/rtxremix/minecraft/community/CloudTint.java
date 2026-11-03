/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * Cloud albedo and subsurface transmittance that follow the time of day and
 * the weather.
 *
 * <p>Vanilla's cloud colour already darkens at night and greys in rain, but the
 * upstream renderer keeps only a fifth of that change, so clouds stay nearly
 * white: at night the sky ambient above them and their highly transmissive
 * scattering make them brighter than the sky behind them, and in rain they are
 * lit mostly by light bounced off grass and dirt, which reads brown. Taking
 * most of vanilla's colour, darkening it further under rain toward a slightly
 * cool grey, and letting dim clouds scatter less fixes both.
 *
 * <p>A tint packs into one int so the renderer can compare it frame to frame
 * and update its material only on change: the low 24 bits are the albedo in
 * 8-bit RGB and the top byte is the subsurface transmittance level. A top
 * byte of 0 means "not a tint", so upstream packed colours, which never set it,
 * decode to the upstream transmittance.
 */
public final class CloudTint {

    static final double VANILLA_INFLUENCE = 0.85;
    /** Extra albedo loss at full rain, on top of vanilla's own greying. */
    static final double STORM_DARKENING = 0.35;
    /** A cool bias at full rain that offsets the warm light bounced up from the ground. */
    static final double[] STORM_BIAS = {0.93, 0.96, 1.0};

    public static final float UPSTREAM_SSS_TRANSMITTANCE = 0.999f;
    static final float MIN_SSS_TRANSMITTANCE = 0.90f;
    /** Transmittance a fully dim cloud keeps; dimness follows albedo loss. */
    static final float DIM_SSS_TRANSMITTANCE = 0.94f;

    private CloudTint() {}

    /**
     * Packs the tint for a vanilla cloud colour under the given rain strength.
     *
     * @param red vanilla cloud red, nominally 0..1
     * @param green vanilla cloud green, nominally 0..1
     * @param blue vanilla cloud blue, nominally 0..1
     * @param rain the rain gradient, 0..1
     */
    public static int pack(double red, double green, double blue, float rain) {
        double storm = clamp01(rain);
        double darken = 1.0 - STORM_DARKENING * storm;
        double r = influence(red) * darken * lerp(1.0, STORM_BIAS[0], storm);
        double g = influence(green) * darken * lerp(1.0, STORM_BIAS[1], storm);
        double b = influence(blue) * darken * lerp(1.0, STORM_BIAS[2], storm);

        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double dimness = clamp01((1.0 - luminance) / 0.75);
        double transmittance = lerp(UPSTREAM_SSS_TRANSMITTANCE, DIM_SSS_TRANSMITTANCE, dimness);

        return transmittanceLevel(transmittance) << 24
                | quantize(r) << 16
                | quantize(g) << 8
                | quantize(b);
    }

    /** The subsurface transmittance a packed colour carries, or the upstream value for an untinted one. */
    public static float sssTransmittance(int packed) {
        int level = packed >>> 24;
        if (level == 0) {
            return UPSTREAM_SSS_TRANSMITTANCE;
        }
        return MIN_SSS_TRANSMITTANCE
                + (UPSTREAM_SSS_TRANSMITTANCE - MIN_SSS_TRANSMITTANCE) * (level - 1) / 254.0f;
    }

    private static int transmittanceLevel(double transmittance) {
        double t = (transmittance - MIN_SSS_TRANSMITTANCE)
                / (UPSTREAM_SSS_TRANSMITTANCE - MIN_SSS_TRANSMITTANCE);
        return 1 + (int) Math.round(clamp01(t) * 254.0);
    }

    private static double influence(double vanilla) {
        double value = Double.isNaN(vanilla) ? 1.0 : clamp01(vanilla);
        return 1.0 - VANILLA_INFLUENCE * (1.0 - value);
    }

    private static int quantize(double value) {
        return (int) Math.round(clamp01(value) * 255.0);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
