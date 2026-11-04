/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.settings;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraEffectsSettingsTest {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

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
    void eachQualityStepMapsToItsSampleBudgetWithNoiseSamplingAlwaysOn() {
        Map<String, String> written = new LinkedHashMap<>();
        CameraEffectsSettings settings = new CameraEffectsSettings(written::put);

        settings.setQuality(CameraEffectsSettings.STANDARD);
        assertEquals("true", written.get(CameraEffectsSettings.ENABLE_KEY));
        assertEquals("8", written.get(CameraEffectsSettings.SAMPLE_COUNT_KEY));
        assertEquals("true", written.get(CameraEffectsSettings.NOISE_SAMPLE_KEY));
        assertEquals("false", written.get(CameraEffectsSettings.EMISSIVE_KEY));
        assertEquals("false", written.get(CameraEffectsSettings.VIGNETTE_KEY));

        settings.setQuality(CameraEffectsSettings.HIGH);
        assertEquals("16", written.get(CameraEffectsSettings.SAMPLE_COUNT_KEY));
        assertEquals("true", written.get(CameraEffectsSettings.EMISSIVE_KEY));

        settings.setQuality(CameraEffectsSettings.CINEMATIC);
        assertEquals("32", written.get(CameraEffectsSettings.SAMPLE_COUNT_KEY));

        settings.setQuality(99);
        assertEquals(CameraEffectsSettings.ULTRA, settings.getQuality());
        assertEquals("48", written.get(CameraEffectsSettings.SAMPLE_COUNT_KEY));
        assertEquals("false", written.get(CameraEffectsSettings.CHROMATIC_ABERRATION_KEY));
    }

    @Test
    void theShutterIsATimeSoTheStreakLengthDoesNotDependOnFrameRate() {
        float shutterMs = 1000.0f / 48.0f;
        float at60 = CameraEffectsSettings.exposureFraction(shutterMs, 1.0f / 60.0f);
        float at144 = CameraEffectsSettings.exposureFraction(shutterMs, 1.0f / 144.0f);
        assertEquals(1.25f, at60, 1.0e-4f);
        assertEquals(3.0f, at144, 1.0e-4f);
        // Streak length is per-frame motion times the fraction; per-frame motion scales with
        // frame time, so the product is the same at both rates.
        assertEquals(at60 / 60.0f, at144 / 144.0f, 1.0e-4f);

        assertEquals(CameraEffectsSettings.MAX_EXPOSURE_FRACTION,
                CameraEffectsSettings.exposureFraction(41.7f, 1.0f / 240.0f), 0.0f);
        assertEquals(CameraEffectsSettings.MIN_EXPOSURE_FRACTION,
                CameraEffectsSettings.exposureFraction(4.0f, 0.2f), 0.0f);
    }

    @Test
    void frameTimeIsSmoothedAndOnlyAMeaningfulChangeIsWritten() {
        List<String> fractions = new ArrayList<>();
        CameraEffectsSettings settings = new CameraEffectsSettings((key, value) -> {
            if (CameraEffectsSettings.SHUTTER_KEY.equals(key)) {
                fractions.add(value);
            }
        });
        settings.setQuality(CameraEffectsSettings.HIGH);
        settings.setShutterMs(10.0f);
        int writesAfterSetup = fractions.size();

        long now = 0L;
        for (int frame = 0; frame < 200; frame++) {
            settings.onFrame(now, 0.0f);
            now += NANOS_PER_SECOND / 120;
        }
        float settled = Float.parseFloat(fractions.get(fractions.size() - 1));
        assertEquals(10.0f / (1000.0f / 120.0f), settled, 0.03f);
        assertTrue(fractions.size() - writesAfterSetup < 40, "writes are throttled: " + fractions.size());

        int writesBeforeSteadyFrames = fractions.size();
        for (int frame = 0; frame < 100; frame++) {
            settings.onFrame(now, 0.0f);
            now += NANOS_PER_SECOND / 120;
        }
        assertEquals(writesBeforeSteadyFrames, fractions.size(), "a steady frame rate writes nothing");

        // A loading hitch is not averaged in.
        now += 2 * NANOS_PER_SECOND;
        settings.onFrame(now, 0.0f);
        assertEquals(writesBeforeSteadyFrames, fractions.size());
    }

    @Test
    void shutterAndMaxBlurReachTheRuntimeOnlyWhileBlurIsOnAndStayInRange() {
        Map<String, String> written = new LinkedHashMap<>();
        CameraEffectsSettings settings = new CameraEffectsSettings(written::put);
        settings.setShutterMs(20.0f);
        settings.onFrame(0L, 0.0f);
        assertFalse(written.containsKey(CameraEffectsSettings.SHUTTER_KEY));

        settings.setQuality(CameraEffectsSettings.HIGH);
        settings.setMaxBlur(0.5f);
        assertEquals(Float.toString(CameraEffectsSettings.MAX_MAX_BLUR),
                written.get(CameraEffectsSettings.MAX_BLUR_KEY));
        // 20 ms at the assumed 60 fps before any frame is measured.
        assertEquals(1.2f, Float.parseFloat(written.get(CameraEffectsSettings.SHUTTER_KEY)), 1.0e-4f);

        settings.setShutterMs(1000.0f);
        assertEquals(CameraEffectsSettings.MAX_SHUTTER_MS, settings.getShutterMs(), 0.0f);
    }

    @Test
    void aFixedFrameTimeIsUsedAsIsWithoutSmoothing() {
        Map<String, String> written = new LinkedHashMap<>();
        CameraEffectsSettings settings = new CameraEffectsSettings(written::put);
        settings.setQuality(CameraEffectsSettings.CINEMATIC);
        settings.setShutterMs(1000.0f / 48.0f);

        // An offline export takes seconds per frame on the wall clock but plays at 60 fps.
        long now = 0L;
        for (int frame = 0; frame < 3; frame++) {
            settings.onFrame(now, 1.0f / 60.0f);
            now += 3 * NANOS_PER_SECOND;
        }
        assertEquals(60.0f / 48.0f, Float.parseFloat(written.get(CameraEffectsSettings.SHUTTER_KEY)), 1.0e-4f);
    }
}
