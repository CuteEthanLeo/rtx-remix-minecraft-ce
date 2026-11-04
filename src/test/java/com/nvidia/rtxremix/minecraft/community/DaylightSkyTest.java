/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DaylightSkyTest {

    @AfterEach
    void restoreDefaults() {
        DaylightSky.setEnabled(DaylightSky.DEFAULT_ENABLED);
        DaylightSky.setDayScatteringTuning(DaylightSky.DEFAULT_DAY_SCATTERING);
        DaylightSky.setHaloBoostTuning(DaylightSky.DEFAULT_HALO_BOOST);
    }

    @Test
    void onByDefaultWithADimmerSkyFillATighterHaloAndAHalfSizeSun() {
        assertEquals(DaylightSky.DEFAULT_DAY_SCATTERING, DaylightSky.dayScattering(), 0.0f);
        assertEquals(DaylightSky.DEFAULT_HALO_BOOST, DaylightSky.haloBoost(), 0.0f);
        assertEquals(15.0f, DaylightSky.sunHalfSize(30.0f), 0.0f);
        assertEquals(0.75f, DaylightSky.layerStrength(0.75f), 0.0f);
    }

    @Test
    void offRestoresTheUpstreamSky() {
        DaylightSky.setEnabled(false);
        assertEquals(DaylightSky.UPSTREAM_DAY_SCATTERING, DaylightSky.dayScattering(), 0.0f);
        assertEquals(DaylightSky.UPSTREAM_HALO_BOOST, DaylightSky.haloBoost(), 0.0f);
        assertEquals(30.0f, DaylightSky.sunHalfSize(30.0f), 0.0f);
        assertEquals(0.0f, DaylightSky.layerStrength(1.0f), 0.0f);
    }

    @Test
    void tuningOnlyAppliesWhileTheLookIsOn() {
        DaylightSky.setDayScatteringTuning(9.0f);
        assertEquals(9.0f, DaylightSky.dayScattering(), 0.0f);
        DaylightSky.setEnabled(false);
        assertEquals(DaylightSky.UPSTREAM_DAY_SCATTERING, DaylightSky.dayScattering(), 0.0f);
    }
}
