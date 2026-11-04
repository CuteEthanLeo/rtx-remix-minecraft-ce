/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * Procedural RGBA8 textures for the night sky, generated once on the CPU.
 *
 * <p>The sky draws stars and the Milky Way additively with an alpha test, so a
 * texel's colour is light added to the sky and an alpha of 0 discards it.
 */
public final class NightSkyTextures {

    public static final int STAR_SIZE = 16;
    public static final int BAND_WIDTH = 2048;
    public static final int BAND_HEIGHT = 256;
    public static final int METEOR_WIDTH = 128;
    public static final int METEOR_HEIGHT = 16;

    /** Lattice cells around the band; the noise repeats exactly once per turn. */
    private static final int BAND_PERIOD = 48;

    private NightSkyTextures() {}

    /** A soft round star sprite in the given colour. */
    public static byte[] star(float[] color) {
        byte[] pixels = new byte[STAR_SIZE * STAR_SIZE * 4];
        float center = (STAR_SIZE - 1) * 0.5f;
        for (int y = 0; y < STAR_SIZE; y++) {
            for (int x = 0; x < STAR_SIZE; x++) {
                float dx = (x - center) / center;
                float dy = (y - center) / center;
                float r2 = dx * dx + dy * dy;
                float falloff = r2 >= 1.0f ? 0.0f : (float) Math.exp(-4.0 * r2) * (1.0f - r2);
                put(pixels, (y * STAR_SIZE + x) * 4,
                        color[0] * falloff, color[1] * falloff, color[2] * falloff, falloff);
            }
        }
        return pixels;
    }

    /**
     * The Milky Way band: {@code u} runs along the band and wraps, {@code v}
     * runs across it. Its brightest region, the galactic centre, sits at
     * {@code u = 0.5}.
     *
     * <p>Built the way it looks to the eye on a dark night rather than as a
     * smooth glow: clumpy star clouds from domain-warped noise, a central rift
     * and a web of thinner dark dust lanes from ridged noise, a band that
     * meanders and widens into the bulge, a warm core and bluish arms, faint
     * reddish nebulae in the brightest clouds, and a grain of unresolved stars.
     */
    public static byte[] milkyWay(long seed) {
        byte[] pixels = new byte[BAND_WIDTH * BAND_HEIGHT * 4];
        for (int y = 0; y < BAND_HEIGHT; y++) {
            float v = (y + 0.5f) / BAND_HEIGHT * 2.0f - 1.0f;
            for (int x = 0; x < BAND_WIDTH; x++) {
                float u = (x + 0.5f) / BAND_WIDTH;
                float px = u * BAND_PERIOD;
                float toCenter = Math.abs(u - 0.5f) * 2.0f;
                float bulge = (float) Math.exp(-toCenter * toCenter * 5.0);

                // The band meanders and widens toward the bulge.
                float meander = 0.16f * (fbm(seed + 3, px * 0.5f, 11.0f, 3, BAND_PERIOD / 2) - 0.5f);
                float vc = v - meander;
                float width = 0.36f + 0.26f * bulge;
                float profile = (float) Math.exp(-(vc * vc) / (width * width));
                float edgeFade = 1.0f - smoothstep(0.7f, 1.0f, Math.abs(v));

                // Star clouds: noise warped by noise, so it clumps instead of streaking.
                float warp = fbm(seed + 5, px, v * 7.0f + 20.0f, 4);
                float clouds = fbm(seed, px + 3.0f * warp, v * 7.0f + 3.0f * warp + 50.0f, 6);
                float cloudLight = (float) Math.pow(clouds, 1.6) * 1.9f;

                // The great rift along the middle, deepest in the bulge.
                float lane = fbm(seed ^ 0x5DEECE66DL, px, 7.0f, 3);
                float laneCenter = 0.32f * (lane - 0.5f) - meander * 0.5f;
                float laneWidth = 0.07f + 0.05f * fbm(seed + 17, px, 3.0f, 3);
                float rift = (0.55f + 0.35f * bulge)
                        * (float) Math.exp(-((v - laneCenter) * (v - laneCenter)) / (laneWidth * laneWidth));
                // Thin dust filaments: the ridges of a noise field.
                float ridge = 1.0f - Math.abs(2.0f * fbm(seed + 9, px * 1.5f + 2.0f * warp,
                        v * 9.0f + 7.0f, 5, BAND_PERIOD * 3 / 2) - 1.0f);
                float filaments = 0.7f * smoothstep(0.80f, 0.97f, ridge);
                float dust = Math.max(0.0f, 1.0f - rift) * (1.0f - filaments * profile);

                float glow = profile * edgeFade * (0.22f + 0.78f * bulge) * (0.30f + cloudLight) * dust;

                // Unresolved stars: a fine grain that is denser where the band is.
                float grain = hash01(seed + 31, x, y);
                float sparkle = grain > 1.0f - 0.035f * profile ? 0.5f + 0.5f * hash01(seed + 37, x, y) : 0.0f;
                sparkle *= edgeFade * (0.4f + 0.6f * dust);

                // Warm old stars in the bulge, younger blue-white stars in the arms.
                float warmth = Math.min(1.0f, bulge * 0.9f + 0.25f * cloudLight * bulge);
                float r = lerp(0.78f, 1.00f, warmth);
                float g = lerp(0.86f, 0.84f, warmth);
                float b = lerp(1.00f, 0.66f, warmth);
                // Faint emission nebulae in the brightest clouds.
                float nebula = smoothstep(0.62f, 0.8f, fbm(seed + 41, px * 2.0f, v * 12.0f, 3, BAND_PERIOD * 2))
                        * smoothstep(0.55f, 0.75f, clouds) * profile * 0.35f;

                float red = glow * r + nebula * glow * 1.4f + sparkle * 0.9f;
                float green = glow * g + sparkle * 0.92f;
                float blue = glow * b + nebula * glow * 0.5f + sparkle;
                float alpha = Math.max(Math.max(red, green), blue);
                put(pixels, (y * BAND_WIDTH + x) * 4, red, green, blue, alpha);
            }
        }
        return pixels;
    }

    /**
     * A meteor streak: {@code u} runs from the end of the tail (0) to the head (1), {@code v}
     * across it. A hot blue-green-white head fades into a warm, narrowing tail.
     */
    public static byte[] meteor() {
        byte[] pixels = new byte[METEOR_WIDTH * METEOR_HEIGHT * 4];
        for (int y = 0; y < METEOR_HEIGHT; y++) {
            float v = (y + 0.5f) / METEOR_HEIGHT * 2.0f - 1.0f;
            for (int x = 0; x < METEOR_WIDTH; x++) {
                float u = (x + 0.5f) / METEOR_WIDTH;
                float halfWidth = 0.12f + 0.5f * u * u;
                float across = (float) Math.exp(-(v * v) / (halfWidth * halfWidth * 0.5f));
                float tail = (float) Math.pow(u, 2.2) * across;
                float headDistance = (u - 0.94f) / 0.06f;
                float head = (float) Math.exp(-(headDistance * headDistance + v * v * 4.0f) * 1.5f);
                float hot = Math.min(1.0f, head * 1.5f + u * u * 0.4f);
                float intensity = Math.min(1.0f, tail * 0.8f + head);
                float r = intensity * lerp(1.00f, 0.85f, hot);
                float g = intensity * lerp(0.70f, 1.00f, hot);
                float b = intensity * lerp(0.45f, 0.92f, hot);
                put(pixels, (y * METEOR_WIDTH + x) * 4, r, g, b, intensity);
            }
        }
        return pixels;
    }

    private static float hash01(long seed, int x, int y) {
        return lattice(seed, x, y, Integer.MAX_VALUE);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0.0f, Math.min(1.0f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static void put(byte[] pixels, int index, float r, float g, float b, float a) {
        pixels[index] = (byte) toByte(r);
        pixels[index + 1] = (byte) toByte(g);
        pixels[index + 2] = (byte) toByte(b);
        pixels[index + 3] = (byte) toByte(a);
    }

    private static int toByte(float value) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, value)) * 255.0f);
    }

    /** Fractal value noise in 0..1 that repeats every {@link #BAND_PERIOD} cells along x. */
    static float fbm(long seed, float x, float y, int octaves) {
        return fbm(seed, x, y, octaves, BAND_PERIOD);
    }

    /** Fractal value noise in 0..1 that repeats every {@code period} cells along x. */
    static float fbm(long seed, float x, float y, int octaves, int period) {
        float sum = 0.0f;
        float amplitude = 0.5f;
        float norm = 0.0f;
        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude * valueNoise(seed + octave * 1013L, x, y, period);
            norm += amplitude;
            amplitude *= 0.5f;
            x *= 2.0f;
            y *= 2.0f;
            period *= 2;
        }
        return sum / norm;
    }

    private static float valueNoise(long seed, float x, float y, int period) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float sx = fx * fx * (3.0f - 2.0f * fx);
        float sy = fy * fy * (3.0f - 2.0f * fy);
        float a = lattice(seed, x0, y0, period);
        float b = lattice(seed, x0 + 1, y0, period);
        float c = lattice(seed, x0, y0 + 1, period);
        float d = lattice(seed, x0 + 1, y0 + 1, period);
        float top = a + (b - a) * sx;
        float bottom = c + (d - c) * sx;
        return top + (bottom - top) * sy;
    }

    private static float lattice(long seed, int x, int y, int period) {
        int wrapped = Math.floorMod(x, period);
        long h = seed ^ (wrapped * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
