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
    public static final int BAND_WIDTH = 1024;
    public static final int BAND_HEIGHT = 128;

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
     * {@code u = 0.5}, and a dark dust lane runs along its middle.
     */
    public static byte[] milkyWay(long seed) {
        byte[] pixels = new byte[BAND_WIDTH * BAND_HEIGHT * 4];
        for (int y = 0; y < BAND_HEIGHT; y++) {
            float v = (y + 0.5f) / BAND_HEIGHT * 2.0f - 1.0f;
            for (int x = 0; x < BAND_WIDTH; x++) {
                float u = (x + 0.5f) / BAND_WIDTH;
                float toCenter = Math.abs(u - 0.5f) * 2.0f;
                float bulge = (float) Math.exp(-toCenter * toCenter * 6.0);
                float width = 0.38f + 0.22f * bulge;
                float profile = (float) Math.exp(-(v * v) / (width * width));

                float clouds = fbm(seed, u * BAND_PERIOD, v * 6.0f + 50.0f, 5);
                float lane = fbm(seed ^ 0x5DEECE66DL, u * BAND_PERIOD, 7.0f, 3);
                float laneCenter = 0.08f * (lane - 0.5f) * 4.0f;
                float laneWidth = 0.10f + 0.06f * fbm(seed + 17, u * BAND_PERIOD, 3.0f, 3);
                float laneDepth = 0.75f * bulge + 0.35f;
                float dust = 1.0f - laneDepth
                        * (float) Math.exp(-((v - laneCenter) * (v - laneCenter)) / (laneWidth * laneWidth));

                float intensity = profile * (0.35f + 0.65f * bulge)
                        * (0.45f + 0.9f * clouds) * Math.max(0.0f, dust);
                float edge = Math.min(1.0f, Math.abs(v) / width);
                float r = intensity * (1.00f - 0.18f * edge);
                float g = intensity * (0.93f - 0.05f * edge);
                float b = intensity * (0.82f + 0.15f * edge);
                put(pixels, (y * BAND_WIDTH + x) * 4, r, g, b, intensity);
            }
        }
        return pixels;
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
