/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaylightSkyTest {

    @AfterEach
    void restoreDefaults() {
        DaylightSky.setEnabled(DaylightSky.DEFAULT_ENABLED);
        DaylightSky.setSkyFillTuning(DaylightSky.DEFAULT_SKY_FILL);
        DaylightSky.setHaloBoostTuning(DaylightSky.DEFAULT_HALO_BOOST);
    }

    @Test
    void onByDefaultWithAPhysicalSkyAPhysicalHaloAndARoundSun() {
        assertTrue(DaylightSky.isPhysicalAtmosphere());
        assertTrue(DaylightSky.isRoundSun());
        assertEquals(1.0f, DaylightSky.skyFill(), 0.0f);
        assertEquals(1.0f, DaylightSky.haloBoost(), 0.0f);
        assertEquals(0.75f, DaylightSky.layerStrength(0.75f), 0.0f);
    }

    @Test
    void offRestoresTheUpstreamSky() {
        DaylightSky.setEnabled(false);
        assertFalse(DaylightSky.isPhysicalAtmosphere());
        assertFalse(DaylightSky.isRoundSun());
        assertEquals(DaylightSky.UPSTREAM_DAY_SCATTERING, DaylightSky.dayScattering(), 0.0f);
        assertEquals(DaylightSky.UPSTREAM_HALO_BOOST, DaylightSky.haloBoost(), 0.0f);
        assertEquals(0.0f, DaylightSky.layerStrength(1.0f), 0.0f);
    }

    @Test
    void haloTuningOnlyAppliesWhileTheLookIsOn() {
        DaylightSky.setHaloBoostTuning(3.0f);
        assertEquals(3.0f, DaylightSky.haloBoost(), 0.0f);
        DaylightSky.setEnabled(false);
        assertEquals(DaylightSky.UPSTREAM_HALO_BOOST, DaylightSky.haloBoost(), 0.0f);
    }

    @Test
    void skyFillTuningIsNeverNegative() {
        DaylightSky.setSkyFillTuning(-2.0f);
        assertEquals(0.0f, DaylightSky.skyFill(), 0.0f);
        DaylightSky.setSkyFillTuning(1.5f);
        assertEquals(1.5f, DaylightSky.skyFill(), 0.0f);
    }
}
