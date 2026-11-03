/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import java.util.Random;

/**
 * Raindrop ripples on rain-exposed ground.
 *
 * <p>Capture lays a decal over every upward, full-width, rain-exposed block top,
 * with UVs in world space over {@link #TILE_BLOCKS}. Its material is an animated
 * sprite sheet: a normal map of expanding drop rings and an albedo whose alpha
 * marks the rings and a thin water film, so the ground gains glossy rings that
 * catch the sky the way shader-pack puddles do. The decals sit on their own
 * terrain pages, shown only while it rains where the camera is, so ripples
 * appear and stop with the rain without recapturing terrain.
 *
 * <p>Every drop starts at a random phase of a one-second loop, and the field
 * wraps across the tile, so the animation loops and tiles without seams.
 */
public final class RainRipples {

    public static final String BUCKET = "rain_ripples";
    public static final float TILE_BLOCKS = 2.0f;
    public static final float LIFT = 0.003f;
    public static final int FRAME_SIZE = 256;
    public static final int SHEET_SIDE = 4;
    public static final int FRAMES = SHEET_SIDE * SHEET_SIDE;
    public static final int FRAMES_PER_SECOND = 16;
    /** Rain strength above which the ripples show. */
    public static final float SHOW_ABOVE = 0.2f;

    private static final int DROPS = 42;
    /** Lifetime of one ring, as a share of the loop. */
    private static final double LIFETIME = 0.55;
    /** Ring radius at the end of its life, as a share of the tile. */
    private static final double MAX_RADIUS = 0.11;
    private static final double RING_WIDTH = 0.012;
    private static final double WAVELENGTH = 0.03;
    /** Alpha of the water film between rings. */
    private static final double FILM_ALPHA = 0.16;
    private static final double RING_ALPHA = 0.7;
    private static final double NORMAL_STRENGTH = 0.9;

    private static volatile boolean enabled = true;

    private RainRipples() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    /** Whether the ripple decals should show for this rain strength. */
    public static boolean visible(float rain) {
        return enabled && rain > SHOW_ABOVE;
    }

    public static int sheetSize() {
        return FRAME_SIZE * SHEET_SIDE;
    }

    /** The two sprite sheets, RGBA8: albedo with ring and film alpha, and a tangent-space normal map. */
    public record Sheets(byte[] albedo, byte[] normal) {}

    public static Sheets generate(long seed) {
        Random random = new Random(seed);
        double[][] drops = new double[DROPS][];
        for (int i = 0; i < DROPS; i++) {
            drops[i] = new double[]{random.nextDouble(), random.nextDouble(), random.nextDouble(),
                    0.6 + 0.4 * random.nextDouble()};
        }
        int side = sheetSize();
        byte[] albedo = new byte[side * side * 4];
        byte[] normal = new byte[side * side * 4];
        double[] height = new double[FRAME_SIZE * FRAME_SIZE];
        double[] ring = new double[FRAME_SIZE * FRAME_SIZE];
        for (int frame = 0; frame < FRAMES; frame++) {
            fillFrame(drops, frame / (double) FRAMES, height, ring);
            int originX = (frame % SHEET_SIDE) * FRAME_SIZE;
            int originY = (frame / SHEET_SIDE) * FRAME_SIZE;
            for (int y = 0; y < FRAME_SIZE; y++) {
                for (int x = 0; x < FRAME_SIZE; x++) {
                    double dx = height[y * FRAME_SIZE + (x + 1) % FRAME_SIZE]
                            - height[y * FRAME_SIZE + (x + FRAME_SIZE - 1) % FRAME_SIZE];
                    double dy = height[((y + 1) % FRAME_SIZE) * FRAME_SIZE + x]
                            - height[((y + FRAME_SIZE - 1) % FRAME_SIZE) * FRAME_SIZE + x];
                    double nx = -dx * NORMAL_STRENGTH * FRAME_SIZE * 0.5;
                    double ny = -dy * NORMAL_STRENGTH * FRAME_SIZE * 0.5;
                    double length = Math.sqrt(nx * nx + ny * ny + 1.0);
                    int index = ((originY + y) * side + originX + x) * 4;
                    normal[index] = encode(nx / length);
                    normal[index + 1] = encode(ny / length);
                    normal[index + 2] = encode(1.0 / length);
                    normal[index + 3] = (byte) 255;

                    double alpha = Math.min(1.0, FILM_ALPHA + RING_ALPHA * ring[y * FRAME_SIZE + x]);
                    albedo[index] = (byte) 52;
                    albedo[index + 1] = (byte) 56;
                    albedo[index + 2] = (byte) 62;
                    albedo[index + 3] = (byte) Math.round(alpha * 255.0);
                }
            }
        }
        return new Sheets(albedo, normal);
    }

    /** Height and ring-mask fields of one frame at loop time {@code t}, in tile units. */
    private static void fillFrame(double[][] drops, double t, double[] height, double[] ring) {
        java.util.Arrays.fill(height, 0.0);
        java.util.Arrays.fill(ring, 0.0);
        int reach = (int) Math.ceil((MAX_RADIUS + 3 * RING_WIDTH) * FRAME_SIZE);
        for (double[] drop : drops) {
            double age = (t - drop[2] + 1.0) % 1.0;
            if (age >= LIFETIME) continue;
            double life = age / LIFETIME;
            double radius = MAX_RADIUS * Math.sqrt(life);
            double amplitude = drop[3] * (1.0 - life) * (1.0 - life);
            int cx = (int) Math.round(drop[0] * FRAME_SIZE);
            int cy = (int) Math.round(drop[1] * FRAME_SIZE);
            for (int oy = -reach; oy <= reach; oy++) {
                for (int ox = -reach; ox <= reach; ox++) {
                    double distance = Math.sqrt(ox * ox + oy * oy) / FRAME_SIZE;
                    double offset = distance - radius;
                    double envelope = Math.exp(-(offset * offset) / (RING_WIDTH * RING_WIDTH));
                    if (envelope < 1.0e-3) continue;
                    int px = Math.floorMod(cx + ox, FRAME_SIZE);
                    int py = Math.floorMod(cy + oy, FRAME_SIZE);
                    int index = py * FRAME_SIZE + px;
                    height[index] += amplitude * envelope
                            * Math.cos(2.0 * Math.PI * offset / WAVELENGTH) * 0.004;
                    ring[index] = Math.min(1.0, ring[index] + amplitude * envelope);
                }
            }
        }
    }

    private static byte encode(double component) {
        return (byte) Math.round((component * 0.5 + 0.5) * 255.0);
    }
}
