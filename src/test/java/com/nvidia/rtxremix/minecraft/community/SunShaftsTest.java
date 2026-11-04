/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SunShaftsTest {

    private static float height(float elevationDegrees) {
        return (float) Math.sin(Math.toRadians(elevationDegrees));
    }

    private static float clear(float elevationDegrees, boolean golden) {
        return SunShafts.sunVolumetricScale(height(elevationDegrees), 0.0f, 0.0f, 1.0f,
                golden, true, SunShafts.DEFAULT_GOLDEN_PEAK, SunShafts.DEFAULT_RAIN_SHARE);
    }

    @Test
    void goldenHourPeaksAtLowSunAndReturnsToOneByMidMorning() {
        assertEquals(SunShafts.DEFAULT_GOLDEN_PEAK, clear(5.0f, true), 1.0e-4f);
        assertEquals(1.0f, clear(30.0f, true), 1.0e-4f);
        assertEquals(1.0f, clear(60.0f, true), 1.0e-4f);
        assertEquals(1.0f, clear(-5.0f, true), 1.0e-4f);
        float rising = clear(15.0f, true);
        assertTrue(rising > 1.0f && rising < SunShafts.DEFAULT_GOLDEN_PEAK, "fades between the peak and noon");
    }

    @Test
    void goldenHourOffLeavesTheUpstreamScale() {
        assertEquals(1.0f, clear(5.0f, false), 0.0f);
    }

    @Test
    void rainGivesTheHazeBackAShareOfTheClearSun() {
        float surface = 0.0025f;
        float scale = SunShafts.sunVolumetricScale(height(45.0f), 1.0f, 0.0f, surface,
                true, true, SunShafts.DEFAULT_GOLDEN_PEAK, 0.2f);
        assertEquals(0.2f, scale * surface, 1.0e-4f);

        float thunder = SunShafts.sunVolumetricScale(height(45.0f), 1.0f, 1.0f, surface,
                true, true, SunShafts.DEFAULT_GOLDEN_PEAK, 0.2f);
        assertEquals(0.2f * SunShafts.THUNDER_SHARE_OF_RAIN, thunder * surface, 1.0e-4f);

        float off = SunShafts.sunVolumetricScale(height(45.0f), 1.0f, 0.0f, surface,
                true, false, SunShafts.DEFAULT_GOLDEN_PEAK, 0.2f);
        assertEquals(1.0f, off, 0.0f);
    }

    @Test
    void rainCompensationIsCapped() {
        float scale = SunShafts.sunVolumetricScale(height(5.0f), 1.0f, 0.0f, 1.0e-6f,
                true, true, SunShafts.DEFAULT_GOLDEN_PEAK, 1.0f);
        assertEquals(SunShafts.MAX_SCALE, scale, 0.0f);
    }

    @Test
    void skyAmbientScattersLessAtAClearGoldenHourOnly() {
        assertEquals(SunShafts.GOLDEN_AMBIENT_SCALE,
                SunShafts.ambientVolumetricScale(height(5.0f), 0.0f, true), 1.0e-4f);
        assertEquals(1.0f, SunShafts.ambientVolumetricScale(height(5.0f), 1.0f, true), 1.0e-4f);
        assertEquals(1.0f, SunShafts.ambientVolumetricScale(height(60.0f), 0.0f, true), 1.0e-4f);
        assertEquals(1.0f, SunShafts.ambientVolumetricScale(height(5.0f), 0.0f, false), 0.0f);
    }
}
