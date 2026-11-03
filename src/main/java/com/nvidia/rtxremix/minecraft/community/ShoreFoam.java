/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * Foam where still water meets the shore: a strip lying on the water surface
 * along each edge that borders a solid block, textured with a looping,
 * drifting foam pattern that thins out away from the shore.
 *
 * <p>Strips are terrain decals, so they are captured with the water and cost
 * nothing per frame. In strip UVs, {@code u} runs along the shore in world
 * blocks over {@link #TILE_BLOCKS} and {@code v} runs from 0 at the shore to 1
 * at the strip's inner edge. The texture is a sprite sheet whose frames shift
 * the pattern by a whole period over one loop, so the animation never jumps.
 */
public final class ShoreFoam {

    /** How far the strip reaches into the water, in blocks. */
    public static final float WIDTH = 0.38f;
    /** Lift above the water surface; the decal category also offsets it. */
    public static final float LIFT = 0.004f;
    public static final float TILE_BLOCKS = 4.0f;

    public static final int FRAME_WIDTH = 128;
    public static final int FRAME_HEIGHT = 32;
    public static final int SHEET_COLUMNS = 4;
    public static final int SHEET_ROWS = 2;
    public static final int FRAMES_PER_SECOND = 6;
    /** v is kept off the sheet's row edges so a frame never samples its neighbour. */
    public static final float V_MARGIN = 0.03f;

    private static final int NOISE_PERIOD = 16;

    private static volatile boolean enabled = true;

    private ShoreFoam() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static int sheetWidth() { return FRAME_WIDTH * SHEET_COLUMNS; }
    public static int sheetHeight() { return FRAME_HEIGHT * SHEET_ROWS; }

    /** The strip's v for a fraction of its width: 0 at the shore, 1 inward, inside the margins. */
    public static float v(float acrossFraction) {
        return V_MARGIN + (1.0f - 2.0f * V_MARGIN) * acrossFraction;
    }

    /** RGBA8 sprite sheet: white foam whose alpha is its coverage. */
    public static byte[] sprite(long seed) {
        int width = sheetWidth();
        int height = sheetHeight();
        int frames = SHEET_COLUMNS * SHEET_ROWS;
        byte[] pixels = new byte[width * height * 4];
        for (int frame = 0; frame < frames; frame++) {
            double phase = frame / (double) frames;
            int originX = (frame % SHEET_COLUMNS) * FRAME_WIDTH;
            int originY = (frame / SHEET_COLUMNS) * FRAME_HEIGHT;
            double lap = 0.08 * Math.sin(phase * Math.PI * 2.0);
            for (int y = 0; y < FRAME_HEIGHT; y++) {
                double across = Math.max(0.0, Math.min(1.0, (y + 0.5) / FRAME_HEIGHT + lap));
                double edge = Math.pow(1.0 - across, 1.6);
                for (int x = 0; x < FRAME_WIDTH; x++) {
                    double along = (x + 0.5) / FRAME_WIDTH * NOISE_PERIOD + phase * NOISE_PERIOD;
                    double n = NightSkyTextures.fbm(seed, (float) along, (float) (across * 5.0), 4, NOISE_PERIOD);
                    double coverage = smoothstep(0.42, 0.62, n * 0.75 + edge * 0.55) * Math.sqrt(edge);
                    int index = ((originY + y) * width + originX + x) * 4;
                    pixels[index] = (byte) 245;
                    pixels[index + 1] = (byte) 248;
                    pixels[index + 2] = (byte) 252;
                    pixels[index + 3] = (byte) Math.round(Math.max(0.0, Math.min(1.0, coverage)) * 255.0);
                }
            }
        }
        return pixels;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
