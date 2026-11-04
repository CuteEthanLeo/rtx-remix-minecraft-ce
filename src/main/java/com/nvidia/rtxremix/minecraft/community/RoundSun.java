/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import java.nio.ByteBuffer;

/**
 * The sun as shader packs draw it: a small round disc, darker toward its
 * limb, inside a tight glow, in place of the vanilla billboard, whose square
 * and built-in halo spread to an 8.5 degree overexposed blot.
 *
 * <p>The billboard texture holds the disc in its middle third and the glow
 * around it, fading to nothing at the edge. Its colour is the sunlight that
 * reaches the ground, so the disc turns orange and dims as it sets with the
 * same transmittance as the sun's light.
 */
public final class RoundSun {

    /** Texture size; the disc spans a third of it. */
    public static final int TEXTURE_SIZE = 128;
    /** Billboard half-size in blocks at the celestial distance of 100: about 4.5 degrees. */
    public static final float HALF_SIZE = 7.9f;
    /** Emitted radiance of the disc centre, above the daytime bloom threshold so it glows. */
    public static final float DISC_RADIANCE = 300.0f;
    /** Disc radius as a fraction of the billboard half-size: about 1.5 degrees. */
    static final float DISC_FRACTION = 1.0f / 3.0f;
    /** Linear limb-darkening coefficient of the photosphere in visible light. */
    static final float LIMB_DARKENING = 0.6f;
    /** Glow brightness just outside the limb, relative to the disc centre. */
    static final float GLOW_PEAK = 0.08f;
    /** Glow falloff per disc radius beyond the limb. */
    static final float GLOW_FALLOFF = 2.5f;
    /** Smallest change in any channel of the colour that re-uploads the texture. */
    public static final float COLOR_UPDATE_DELTA = 0.01f;

    private RoundSun() {}

    /**
     * Brightness of the billboard at a distance from its centre, relative to the disc centre.
     *
     * @param radius distance from the centre in disc radii
     * @param pixelWidth width of one texel in disc radii, for an antialiased limb
     */
    static float profile(float radius, float pixelWidth) {
        float limb = 0.0f;
        if (radius < 1.0f + pixelWidth) {
            float inside = Math.min(1.0f, radius);
            float mu = (float) Math.sqrt(Math.max(0.0f, 1.0f - inside * inside));
            float coverage = clamp01((1.0f + 0.5f * pixelWidth - radius) / pixelWidth);
            limb = (1.0f - LIMB_DARKENING * (1.0f - mu)) * coverage;
        }
        float glow = radius > 1.0f
                ? GLOW_PEAK * (float) Math.exp(-(radius - 1.0f) * GLOW_FALLOFF)
                : GLOW_PEAK;
        // Fade the glow to zero at the billboard's edge so its square never shows.
        float edge = 1.0f / DISC_FRACTION;
        float fade = clamp01((edge - radius) / (0.25f * edge));
        return Math.max(limb, glow * fade);
    }

    /**
     * Fills an RGBA8 texture of {@link #TEXTURE_SIZE} squared with the disc and glow tinted by
     * a linear colour, encoded as sRGB, leaving the buffer at position zero.
     */
    public static void fillTexture(ByteBuffer pixels, float red, float green, float blue) {
        int size = TEXTURE_SIZE;
        float half = size * 0.5f;
        float discRadiusPixels = half * DISC_FRACTION;
        float pixelWidth = 1.0f / discRadiusPixels;
        pixels.clear();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - half) / discRadiusPixels;
                float dy = (y + 0.5f - half) / discRadiusPixels;
                float value = profile((float) Math.sqrt(dx * dx + dy * dy), pixelWidth);
                byte r = encodeSrgb(value * red);
                byte g = encodeSrgb(value * green);
                byte b = encodeSrgb(value * blue);
                boolean visible = r != 0 || g != 0 || b != 0;
                pixels.put(r).put(g).put(b).put(visible ? (byte) 0xFF : 0);
            }
        }
        pixels.flip();
    }

    /** Whether a colour differs enough from the uploaded one to re-upload the texture. */
    public static boolean colorChanged(float[] uploaded, float red, float green, float blue) {
        return Math.abs(uploaded[0] - red) > COLOR_UPDATE_DELTA
                || Math.abs(uploaded[1] - green) > COLOR_UPDATE_DELTA
                || Math.abs(uploaded[2] - blue) > COLOR_UPDATE_DELTA;
    }

    static byte encodeSrgb(float linear) {
        float value = clamp01(linear);
        float encoded = value <= 0.0031308f
                ? value * 12.92f
                : 1.055f * (float) Math.pow(value, 1.0 / 2.4) - 0.055f;
        return (byte) Math.round(encoded * 255.0f);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
