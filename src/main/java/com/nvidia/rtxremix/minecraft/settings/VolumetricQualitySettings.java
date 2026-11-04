/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.settings;

import java.util.function.BiConsumer;

/**
 * Resolution of the Remix volumetric froxel grid, which decides how sharp
 * light shafts and fog edges are.
 *
 * <p>The grid is screen-space: {@code froxelGridResolutionScale} is the pixel
 * size of one froxel, and {@code froxelDepthSlices} the number of slices out
 * to the froxel distance. Remix graphics presets write both, so every apply
 * writes the whole set and a preset change is followed by {@link #reapply()}.
 * The accumulation keys stay with the conf layers (volumetrics/settling.conf
 * owns them), because a User Settings write would outrank every layer.
 */
public final class VolumetricQualitySettings {

    /** Quality steps, as the integer setting stores them. */
    public static final int STANDARD = 0;
    public static final int HIGH = 1;
    public static final int ULTRA = 2;

    public static final int DEFAULT_QUALITY = ULTRA;

    static final String GRID_SCALE_KEY = "rtx.volumetrics.froxelGridResolutionScale";
    static final String DEPTH_SLICES_KEY = "rtx.volumetrics.froxelDepthSlices";
    static final String RIS_SAMPLES_KEY = "rtx.volumetrics.initialRISSampleCount";
    static final String RIS_VISIBILITY_KEY = "rtx.volumetrics.enableVolumeRISInitialVisibility";

    private final BiConsumer<String, String> configWriter;

    private int quality = DEFAULT_QUALITY;

    public VolumetricQualitySettings(BiConsumer<String, String> configWriter) {
        this.configWriter = configWriter;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int value) {
        quality = Math.max(STANDARD, Math.min(ULTRA, value));
        apply();
    }

    /** Writes the current state again, for after a graphics preset rewrote the froxel options. */
    public void reapply() {
        apply();
    }

    private void apply() {
        configWriter.accept(GRID_SCALE_KEY, Integer.toString(gridScale(quality)));
        configWriter.accept(DEPTH_SLICES_KEY, Integer.toString(depthSlices(quality)));
        configWriter.accept(RIS_SAMPLES_KEY, Integer.toString(risSamples(quality)));
        configWriter.accept(RIS_VISIBILITY_KEY, Boolean.toString(true));
    }

    /** Pixels per froxel; Remix's default is 16. */
    static int gridScale(int quality) {
        return switch (quality) {
            case HIGH -> 12;
            case ULTRA -> 8;
            default -> 16;
        };
    }

    /** Depth slices out to the froxel distance; Remix's default is 48. */
    static int depthSlices(int quality) {
        return quality == STANDARD ? 48 : 64;
    }

    /** Light candidates per froxel; Remix's default is 8. */
    static int risSamples(int quality) {
        return quality == ULTRA ? 16 : 8;
    }
}
