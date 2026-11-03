/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.settings;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CameraEffectsSettingsTest {

    @Test
    void offStillWritesEveryPostEffectSwitchOff() {
        Map<String, String> written = new LinkedHashMap<>();
        new CameraEffectsSettings(written::put).reapply();

        assertEquals("false", written.get(CameraEffectsSettings.ENABLE_KEY));
        assertEquals("false", written.get(CameraEffectsSettings.MOTION_BLUR_KEY));
        assertEquals("false", written.get(CameraEffectsSettings.CHROMATIC_ABERRATION_KEY));
        assertEquals("false", written.get(CameraEffectsSettings.VIGNETTE_KEY));
        assertFalse(written.containsKey(CameraEffectsSettings.SAMPLE_COUNT_KEY));
    }

    @Test
    void eachQualityStepMapsToItsSampleBudgetWithTheOtherEffectsKeptOff() {
        Map<String, String> written = new LinkedHashMap<>();
        CameraEffectsSettings settings = new CameraEffectsSettings(written::put);

        settings.setQuality(CameraEffectsSettings.STANDARD);
        assertEquals("true", written.get(CameraEffectsSettings.ENABLE_KEY));
        assertEquals("8", written.get(CameraEffectsSettings.SAMPLE_COUNT_KEY));
        assertEquals("false", written.get(CameraEffectsSettings.EMISSIVE_KEY));
        assertEquals("false", written.get(CameraEffectsSettings.VIGNETTE_KEY));

        settings.setQuality(CameraEffectsSettings.HIGH);
        assertEquals("16", written.get(CameraEffectsSettings.SAMPLE_COUNT_KEY));
        assertEquals("true", written.get(CameraEffectsSettings.NOISE_SAMPLE_KEY));

        settings.setQuality(99);
        assertEquals(CameraEffectsSettings.CINEMATIC, settings.getQuality());
        assertEquals("32", written.get(CameraEffectsSettings.SAMPLE_COUNT_KEY));
        assertEquals("false", written.get(CameraEffectsSettings.CHROMATIC_ABERRATION_KEY));
    }

    @Test
    void shutterAndMaxBlurReachTheRuntimeOnlyWhileBlurIsOn() {
        Map<String, String> written = new LinkedHashMap<>();
        CameraEffectsSettings settings = new CameraEffectsSettings(written::put);
        settings.setShutter(0.5f);
        assertFalse(written.containsKey(CameraEffectsSettings.SHUTTER_KEY));

        settings.setQuality(CameraEffectsSettings.HIGH);
        settings.setMaxBlur(0.05f);
        assertEquals("0.5", written.get(CameraEffectsSettings.SHUTTER_KEY));
        assertEquals("0.05", written.get(CameraEffectsSettings.MAX_BLUR_KEY));
    }
}
