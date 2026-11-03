/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A deterministic night sky: stars with a power-law brightness distribution,
 * a spread of colour temperatures, faint stars crowding toward the Milky Way,
 * and the band itself as a ring of quads.
 *
 * <p>Vanilla's 1500 stars are uniform, equally bright, white squares a few
 * pixels wide. Real skies are dominated by many faint stars and a few bright
 * ones, and the faint ones gather along the galactic plane. Everything here is
 * in the sky-local frame the renderer rotates with the time of day, at
 * {@link #RADIUS} from the origin, the distance vanilla's own stars use.
 */
public final class StarField {

    public static final float RADIUS = 100.0f;
    public static final long SEED = 0x5354_4152_5345_4544L;
    public static final int STAR_COUNT = 4000;

    /** Approximate sRGB colours of 3000 K, 4500 K, 6000 K, 8000 K, and 12000 K stars. */
    public static final float[][] CLASS_COLORS = {
            {1.00f, 0.71f, 0.42f},
            {1.00f, 0.84f, 0.67f},
            {1.00f, 0.96f, 0.92f},
            {0.89f, 0.91f, 1.00f},
            {0.76f, 0.82f, 1.00f},
    };
    /** How often each colour class occurs; Sun-like and warmer stars dominate a naked-eye sky. */
    private static final float[] CLASS_WEIGHTS = {0.18f, 0.27f, 0.30f, 0.17f, 0.08f};

    /** Emitted radiance of the dim, middle, and bright tiers. */
    public static final float[] TIER_RADIANCE = {0.8f, 2.0f, 6.0f};
    /** Quad half-size range of each tier, in blocks at {@link #RADIUS}. */
    private static final float[][] TIER_HALF_SIZE = {{0.07f, 0.11f}, {0.11f, 0.17f}, {0.17f, 0.28f}};
    /** Fraction of stars at or above each tier boundary: the brightest 3% and the next 17%. */
    private static final float BRIGHT_FRACTION = 0.03f;
    private static final float MIDDLE_FRACTION = 0.20f;
    /** Fraction of stars drawn near the galactic plane rather than uniformly. */
    private static final float GALACTIC_FRACTION = 0.35f;
    private static final double GALACTIC_SPREAD_RADIANS = Math.toRadians(9.0);

    /** Normal of the Milky Way's great circle in the sky-local frame. */
    public static final float[] GALACTIC_NORMAL = normalize(0.35f, 0.60f, 0.72f);
    /** Angular half-width of the band mesh. */
    public static final double BAND_HALF_WIDTH_RADIANS = Math.toRadians(14.0);
    public static final int BAND_SEGMENTS = 96;

    /** One star: a unit direction, a quad half-size, a colour class, and a brightness tier. */
    public record Star(float x, float y, float z, float halfSize, int colorClass, int tier) {}

    private StarField() {}

    public static List<Star> generate(long seed, int count) {
        Random random = new Random(seed);
        float[] u = new float[3];
        float[] w = new float[3];
        planeBasis(GALACTIC_NORMAL, u, w);
        List<Star> stars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float[] direction = random.nextFloat() < GALACTIC_FRACTION
                    ? galacticDirection(random, u, w)
                    : uniformDirection(random);
            float rank = random.nextFloat();
            int tier = rank < BRIGHT_FRACTION ? 2 : rank < MIDDLE_FRACTION ? 1 : 0;
            float[] sizes = TIER_HALF_SIZE[tier];
            float halfSize = sizes[0] + (sizes[1] - sizes[0]) * random.nextFloat();
            stars.add(new Star(direction[0], direction[1], direction[2],
                    halfSize, pickClass(random.nextFloat()), tier));
        }
        return stars;
    }

    /**
     * Writes the four corners of a star's quad, facing the origin, into
     * {@code out} as x, y, z triples starting at {@code offset}.
     */
    public static void quadCorners(Star star, float[] out, int offset) {
        float[] tangent = new float[3];
        float[] bitangent = new float[3];
        planeBasis(new float[]{star.x(), star.y(), star.z()}, tangent, bitangent);
        float cx = star.x() * RADIUS;
        float cy = star.y() * RADIUS;
        float cz = star.z() * RADIUS;
        float s = star.halfSize();
        float[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int corner = 0; corner < 4; corner++) {
            float a = signs[corner][0] * s;
            float b = signs[corner][1] * s;
            int index = offset + corner * 3;
            out[index] = cx + tangent[0] * a + bitangent[0] * b;
            out[index + 1] = cy + tangent[1] * a + bitangent[1] * b;
            out[index + 2] = cz + tangent[2] * a + bitangent[2] * b;
        }
    }

    /**
     * Point on the Milky Way band: {@code along} goes once around the circle
     * over 0..1, {@code across} spans the band from -1 to 1.
     */
    public static void bandPoint(float along, float across, float[] out) {
        float[] u = new float[3];
        float[] w = new float[3];
        planeBasis(GALACTIC_NORMAL, u, w);
        double theta = along * Math.PI * 2.0;
        double lift = across * BAND_HALF_WIDTH_RADIANS;
        double cosLift = Math.cos(lift);
        double sinLift = Math.sin(lift);
        for (int i = 0; i < 3; i++) {
            double inPlane = u[i] * Math.cos(theta) + w[i] * Math.sin(theta);
            out[i] = (float) ((inPlane * cosLift + GALACTIC_NORMAL[i] * sinLift) * RADIUS);
        }
    }

    private static float[] galacticDirection(Random random, float[] u, float[] w) {
        double theta = random.nextDouble() * Math.PI * 2.0;
        double lift = random.nextGaussian() * GALACTIC_SPREAD_RADIANS;
        float[] d = new float[3];
        for (int i = 0; i < 3; i++) {
            d[i] = (float) ((u[i] * Math.cos(theta) + w[i] * Math.sin(theta)) * Math.cos(lift)
                    + GALACTIC_NORMAL[i] * Math.sin(lift));
        }
        return normalize(d[0], d[1], d[2]);
    }

    private static float[] uniformDirection(Random random) {
        double z = random.nextDouble() * 2.0 - 1.0;
        double phi = random.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new float[]{(float) (r * Math.cos(phi)), (float) (r * Math.sin(phi)), (float) z};
    }

    private static int pickClass(float roll) {
        float cumulative = 0.0f;
        for (int i = 0; i < CLASS_WEIGHTS.length; i++) {
            cumulative += CLASS_WEIGHTS[i];
            if (roll < cumulative) return i;
        }
        return CLASS_WEIGHTS.length - 1;
    }

    /** Two unit vectors perpendicular to {@code normal} and to each other. */
    static void planeBasis(float[] normal, float[] u, float[] w) {
        float[] n = normalize(normal[0], normal[1], normal[2]);
        float[] helper = Math.abs(n[1]) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float[] a = normalize(
                helper[1] * n[2] - helper[2] * n[1],
                helper[2] * n[0] - helper[0] * n[2],
                helper[0] * n[1] - helper[1] * n[0]);
        System.arraycopy(a, 0, u, 0, 3);
        w[0] = n[1] * a[2] - n[2] * a[1];
        w[1] = n[2] * a[0] - n[0] * a[2];
        w[2] = n[0] * a[1] - n[1] * a[0];
    }

    private static float[] normalize(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / length, y / length, z / length};
    }
}
