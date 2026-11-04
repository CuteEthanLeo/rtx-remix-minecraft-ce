/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.settings;

import com.nvidia.rtxremix.RemixException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VolumetricQualitySettingsTest {

    @Test
    void ultraIsTheDefaultAndWritesTheFinestGrid() {
        Map<String, String> written = new LinkedHashMap<>();
        new VolumetricQualitySettings(written::put).reapply();

        assertEquals("8", written.get(VolumetricQualitySettings.GRID_SCALE_KEY));
        assertEquals("64", written.get(VolumetricQualitySettings.DEPTH_SLICES_KEY));
        assertEquals("16", written.get(VolumetricQualitySettings.RIS_SAMPLES_KEY));
    }

    @Test
    void eachStepMapsToItsGrid() {
        Map<String, String> written = new LinkedHashMap<>();
        VolumetricQualitySettings settings = new VolumetricQualitySettings(written::put);

        settings.setQuality(VolumetricQualitySettings.STANDARD);
        assertEquals("16", written.get(VolumetricQualitySettings.GRID_SCALE_KEY));
        assertEquals("48", written.get(VolumetricQualitySettings.DEPTH_SLICES_KEY));
        assertEquals("8", written.get(VolumetricQualitySettings.RIS_SAMPLES_KEY));

        settings.setQuality(VolumetricQualitySettings.HIGH);
        assertEquals("12", written.get(VolumetricQualitySettings.GRID_SCALE_KEY));
        assertEquals("64", written.get(VolumetricQualitySettings.DEPTH_SLICES_KEY));

        settings.setQuality(99);
        assertEquals(VolumetricQualitySettings.ULTRA, settings.getQuality());
    }

    @Test
    void aRejectedOptionIsLoggedNotThrown() {
        Map<String, String> written = new LinkedHashMap<>();
        VolumetricQualitySettings settings = new VolumetricQualitySettings((key, value) -> {
            if (key.equals(VolumetricQualitySettings.RIS_SAMPLES_KEY)) {
                throw new RemixException("SetConfigVariable failed: GENERAL_FAILURE");
            }
            written.put(key, value);
        });
        settings.reapply();
        assertEquals("8", written.get(VolumetricQualitySettings.GRID_SCALE_KEY));
        assertEquals("64", written.get(VolumetricQualitySettings.DEPTH_SLICES_KEY));
    }

    @Test
    void neverWritesTheAccumulationKeysTheSettlingLayerOwns() {
        Map<String, String> written = new LinkedHashMap<>();
        new VolumetricQualitySettings(written::put).reapply();
        assertFalse(written.containsKey("rtx.volumetrics.maxAccumulationFrames"));
        assertFalse(written.containsKey("rtx.volumetrics.temporalReuseMaxSampleCount"));
    }
}
