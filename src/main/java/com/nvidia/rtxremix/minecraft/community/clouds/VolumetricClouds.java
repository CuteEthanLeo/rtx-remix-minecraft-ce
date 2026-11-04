/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.clouds;

import java.util.Arrays;

/**
 * Settings and per-frame state the volumetric clouds' two layers share: the near meshes
 * ({@link VolumetricCloudLayer}, driven by the cloud renderer) and the far layer composited
 * into the sky ({@link CloudSkyCompositor}, driven by the sky renderer).
 *
 * <p>The cloud renderer runs after the sky renderer in a frame, so the sky reads the state the
 * cloud renderer published one frame earlier. Settings are written from the settings screen and
 * read on the render thread, so they are volatile; the frame state is render-thread only.
 */
public final class VolumetricClouds {

    public static final boolean DEFAULT_ENABLED = true;
    public static final int QUALITY_LOW = 0;
    public static final int QUALITY_HIGH = 1;
    public static final int QUALITY_ULTRA = 2;
    public static final int DEFAULT_QUALITY = QUALITY_HIGH;
    public static final float DEFAULT_COVERAGE = CloudField.DEFAULT_COVERAGE;
    public static final float MIN_COVERAGE = 0.15f;
    public static final float MAX_COVERAGE = 0.85f;

    /** Near tiles, blocks on a side. */
    public static final int TILE_SIZE = 128;
    /** Occupancy grid half-width in tiles; the grid is centred on the camera's tile. */
    public static final int OCCUPANCY_HALF = 16;
    public static final int OCCUPANCY_SIZE = OCCUPANCY_HALF * 2;
    /** Height above the cloud base the near meshes cover; the far layer draws anything taller. */
    public static final float NEAR_CAP_ABOVE_BASE = 384.0f;
    public static final int OCCLUSION_MAP_WIDTH = 64;
    public static final int OCCLUSION_MAP_HEIGHT = 16;
    /** Share of the previous sun and moon transmittance kept per far-layer update (10 a second). */
    static final float LIGHT_KEEP = 0.85f;

    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile int quality = DEFAULT_QUALITY;
    private static volatile float coverage = DEFAULT_COVERAGE;
    /** A failure of the far layer, latched until the clouds are switched off and on again. */
    private static volatile RuntimeException failure;

    // ---- frame state (render thread) ----
    private static boolean active;
    private static CloudField.Params params = CloudField.Params.defaults();
    private static double worldTime;
    private static final byte[] occupancy = new byte[OCCUPANCY_SIZE * OCCUPANCY_SIZE];
    private static long occupancyTileX;
    private static long occupancyTileZ;
    private static int occupancyVersion;
    private static boolean occupancyEnabled;

    // ---- far-layer results (render thread) ----
    private static float sunTransmittance = 1.0f;
    private static float moonTransmittance = 1.0f;
    private static float skyCover;
    private static final float[] occlusion = new float[OCCLUSION_MAP_WIDTH * OCCLUSION_MAP_HEIGHT];
    private static boolean farResultsValid;

    static {
        Arrays.fill(occlusion, 1.0f);
    }

    private VolumetricClouds() {}

    // ------------------------------------------------------------------
    //  Settings
    // ------------------------------------------------------------------

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) {
        if (enabled != value) {
            failure = null;
        }
        enabled = value;
    }

    /** Whether the volumetric clouds should draw: enabled, and neither layer has failed. */
    public static boolean isUsable() {
        return enabled && failure == null;
    }

    /** Latches a failure, after which the cloud renderer falls back to the chosen classic style. */
    public static void reportFailure(RuntimeException cause) {
        failure = cause;
    }

    public static RuntimeException failure() { return failure; }

    public static int getQuality() { return quality; }
    public static void setQuality(int value) {
        quality = Math.max(QUALITY_LOW, Math.min(QUALITY_ULTRA, value));
    }

    public static float getCoverage() { return coverage; }
    public static void setCoverage(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Cloud cover must be finite");
        }
        coverage = Math.max(MIN_COVERAGE, Math.min(MAX_COVERAGE, value));
    }

    /** Per-quality budgets. */
    public record Quality(int domeWidth, int primarySteps, int lightSteps, int nearRadius,
                          int meshStep, int updateTicks) {
        public int domeHeight() { return domeWidth / 2; }
    }

    public static Quality quality(int level) {
        return switch (level) {
            // primarySteps is the march's step budget; steps grow with distance to fit it.
            case QUALITY_LOW -> new Quality(1024, 96, 4, 320, 8, 1);
            case QUALITY_ULTRA -> new Quality(3072, 256, 6, 448, 4, 2);
            default -> new Quality(2048, 160, 5, 384, 4, 2);
        };
    }

    public static Quality currentQuality() {
        return quality(quality);
    }

    // ------------------------------------------------------------------
    //  Frame state published by the cloud renderer
    // ------------------------------------------------------------------

    /** Whether the volumetric clouds drew last frame, which the sky follows. */
    public static boolean isActive() { return active; }

    public static void publishFrame(CloudField.Params frameParams, double frameWorldTime) {
        active = true;
        params = frameParams;
        worldTime = frameWorldTime;
    }

    public static void deactivate() {
        active = false;
        occupancyEnabled = false;
        clearFarResults();
    }

    public static CloudField.Params params() { return params; }
    public static double worldTime() { return worldTime; }

    /**
     * Records which near tiles are drawn as meshes, so the far layer skips them.
     *
     * @param centreTileX the tile the grid is centred on, field coordinates (unwrapped)
     * @param covered whether a relative tile (dx, dz in [-OCCUPANCY_HALF, OCCUPANCY_HALF)) is covered
     */
    public static void publishOccupancy(long centreTileX, long centreTileZ, TileCoverage covered) {
        boolean changed = centreTileX != occupancyTileX || centreTileZ != occupancyTileZ || !occupancyEnabled;
        for (int dz = -OCCUPANCY_HALF; dz < OCCUPANCY_HALF; dz++) {
            for (int dx = -OCCUPANCY_HALF; dx < OCCUPANCY_HALF; dx++) {
                byte value = covered.isCovered(centreTileX + dx, centreTileZ + dz) ? (byte) 0xff : 0;
                int index = (dz + OCCUPANCY_HALF) * OCCUPANCY_SIZE + dx + OCCUPANCY_HALF;
                if (occupancy[index] != value) {
                    occupancy[index] = value;
                    changed = true;
                }
            }
        }
        occupancyTileX = centreTileX;
        occupancyTileZ = centreTileZ;
        occupancyEnabled = true;
        if (changed) {
            occupancyVersion++;
        }
    }

    public static void clearOccupancy() {
        if (occupancyEnabled) {
            occupancyEnabled = false;
            occupancyVersion++;
        }
    }

    @FunctionalInterface
    public interface TileCoverage {
        boolean isCovered(long tileX, long tileZ);
    }

    static byte[] occupancy() { return occupancy; }
    static long occupancyTileX() { return occupancyTileX; }
    static long occupancyTileZ() { return occupancyTileZ; }
    static int occupancyVersion() { return occupancyVersion; }
    static boolean occupancyEnabled() { return occupancyEnabled; }

    // ------------------------------------------------------------------
    //  Far-layer results published by the sky renderer
    // ------------------------------------------------------------------

    static void publishFarResults(float sun, float moon, float cover, float[] map) {
        // Clouds drift slowly; smoothing keeps a light from stepping when a jittered
        // estimate lands, without lagging behind a real change by more than a second. The sun
        // and moon light the whole world, so they ease in over about a second of updates.
        float keep = farResultsValid ? 0.6f : 0.0f;
        float lightKeep = farResultsValid ? LIGHT_KEEP : 0.0f;
        sunTransmittance = mix(clamp01(sun), sunTransmittance, lightKeep);
        moonTransmittance = mix(clamp01(moon), moonTransmittance, lightKeep);
        skyCover = mix(clamp01(cover), skyCover, keep);
        for (int i = 0; i < occlusion.length; i++) {
            occlusion[i] = mix(clamp01(map[i]), occlusion[i], keep);
        }
        farResultsValid = true;
    }

    static void clearFarResults() {
        sunTransmittance = 1.0f;
        moonTransmittance = 1.0f;
        skyCover = 0.0f;
        Arrays.fill(occlusion, 1.0f);
        farResultsValid = false;
    }

    /** Far-cloud transmittance toward the sun, 1 when the volumetric clouds are off. */
    public static float sunTransmittance() {
        return active ? sunTransmittance : 1.0f;
    }

    public static float moonTransmittance() {
        return active ? moonTransmittance : 1.0f;
    }

    /** Fraction of the upper sky the clouds hide, 0 when they are off. */
    public static float skyCover() {
        return active ? skyCover : 0.0f;
    }

    /**
     * How much of the sky shows through the clouds in a world direction, 1 when they are off.
     * Directions below the horizon read the horizon row.
     */
    public static float skyVisibility(float x, float y, float z) {
        if (!active || !farResultsValid) {
            return 1.0f;
        }
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length <= 0.0f) {
            return 1.0f;
        }
        // The dome's X axis is mirrored against the world's (sky_atmosphere.comp).
        float dx = -x / length;
        float dy = y / length;
        float dz = z / length;
        double theta = Math.acos(Math.max(-1.0f, Math.min(1.0f, dy)));
        double phi = Math.atan2(dz, dx);
        double u = (phi + Math.PI * 0.5) / (2.0 * Math.PI);
        u -= Math.floor(u);
        double v = theta / Math.PI;
        int cx = Math.min(OCCLUSION_MAP_WIDTH - 1, (int) (u * OCCLUSION_MAP_WIDTH));
        int cy = Math.min(OCCLUSION_MAP_HEIGHT - 1, (int) (v * 2.0 * OCCLUSION_MAP_HEIGHT));
        return occlusion[cy * OCCLUSION_MAP_WIDTH + cx];
    }

    private static float mix(float value, float previous, float keep) {
        return value + (previous - value) * keep;
    }

    private static float clamp01(float value) {
        return Float.isNaN(value) ? 1.0f : Math.max(0.0f, Math.min(1.0f, value));
    }
}
