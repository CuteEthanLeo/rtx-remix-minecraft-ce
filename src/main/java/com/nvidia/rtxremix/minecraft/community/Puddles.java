/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import java.util.Random;

/**
 * Puddles that gather on rain-exposed hard ground and shrink as it dries.
 *
 * <p>Capture lays a puddle decal over every upward, full-width, rain-exposed
 * block top whose texture is hard ground, with UVs in world space over
 * {@link #TILE_BLOCKS}. The decal is a near-mirror sheet of water whose alpha
 * comes from a mask texture: a fixed, tileable noise field thresholded by the
 * current wetness, so puddles start in the field's deepest spots and spread as
 * the rain goes on. The mask is rewritten in place per wetness level, so the
 * puddles grow and dry without recapturing terrain; at level 0 it is empty.
 *
 * <p>Ground that would soak water up or never pool it stays bare: grass, sand,
 * snow, moss, leaves, and farmland.
 */
public final class Puddles {

    public static final String BUCKET = "puddles";
    public static final float TILE_BLOCKS = 16.0f;
    public static final float LIFT = 0.0015f;
    public static final int SIZE = 512;
    /** Noise level a puddle covers at full wetness; higher noise is deeper. */
    static final float FULL_WET_THRESHOLD = 0.56f;
    /** Width of a puddle's soft edge, in noise units. */
    static final float EDGE = 0.035f;
    private static final int LATTICE = 8;
    private static final String[] DRY_GROUND = {
            "grass", "sand", "snow", "moss", "leaves", "farmland", "mycelium", "podzol", "powder"};

    private static volatile boolean enabled = true;

    private Puddles() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    /** Whether a block top with this texture path can hold a puddle. */
    public static boolean pools(String texturePath) {
        if (texturePath == null) return false;
        for (String dry : DRY_GROUND) {
            if (texturePath.contains(dry)) return false;
        }
        return true;
    }

    /** The fixed depth field, 0..1 per texel, tileable in both directions. */
    public static float[] depthField(long seed) {
        Random random = new Random(seed);
        float[][] octaves = new float[4][];
        int[] periods = new int[4];
        for (int o = 0; o < octaves.length; o++) {
            periods[o] = LATTICE << o;
            octaves[o] = new float[periods[o] * periods[o]];
            for (int i = 0; i < octaves[o].length; i++) octaves[o][i] = random.nextFloat();
        }
        float[] field = new float[SIZE * SIZE];
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float sum = 0.0f;
                float amplitude = 1.0f;
                float norm = 0.0f;
                for (int o = 0; o < octaves.length; o++) {
                    int period = periods[o];
                    sum += amplitude * sample(octaves[o], period,
                            x * (float) period / SIZE, y * (float) period / SIZE);
                    norm += amplitude;
                    amplitude *= 0.45f;
                }
                float value = sum / norm;
                field[y * SIZE + x] = value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        for (int i = 0; i < field.length; i++) {
            field[i] = (field[i] - min) / (max - min);
        }
        return field;
    }

    /** The RGBA8 puddle sheet at one wetness level, 0 to {@link WetSurfaces#LEVELS}. */
    public static void mask(float[] depth, int level, byte[] rgba) {
        float wetness = level / (float) WetSurfaces.LEVELS;
        float threshold = 1.0f - (1.0f - FULL_WET_THRESHOLD) * wetness;
        for (int i = 0; i < depth.length; i++) {
            float alpha = level == 0 ? 0.0f : smoothstep(threshold - EDGE, threshold + EDGE, depth[i]);
            int index = i * 4;
            rgba[index] = (byte) 34;
            rgba[index + 1] = (byte) 38;
            rgba[index + 2] = (byte) 44;
            rgba[index + 3] = (byte) Math.round(alpha * 0.92f * 255.0f);
        }
    }

    private static float sample(float[] lattice, int period, float x, float y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float sx = fx * fx * (3 - 2 * fx);
        float sy = fy * fy * (3 - 2 * fy);
        float a = lattice[Math.floorMod(y0, period) * period + Math.floorMod(x0, period)];
        float b = lattice[Math.floorMod(y0, period) * period + Math.floorMod(x0 + 1, period)];
        float c = lattice[Math.floorMod(y0 + 1, period) * period + Math.floorMod(x0, period)];
        float d = lattice[Math.floorMod(y0 + 1, period) * period + Math.floorMod(x0 + 1, period)];
        float top = a + (b - a) * sx;
        float bottom = c + (d - c) * sx;
        return top + (bottom - top) * sy;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0.0f, Math.min(1.0f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }
}
