/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudTintTest {

    @AfterEach
    void restoreDefaults() {
        CommunityLook.setOvercastWeather(CommunityLook.DEFAULT_OVERCAST_WEATHER);
    }

    @Test
    void aClearDayCloudStaysWhiteWithTheUpstreamScattering() {
        int packed = CloudTint.pack(1.0, 1.0, 1.0, 0.0f);
        assertEquals(0x00ff_ffff, packed & 0x00ff_ffff);
        assertEquals(CloudTint.UPSTREAM_SSS_TRANSMITTANCE, CloudTint.sssTransmittance(packed), 1.0e-6f);
    }

    @Test
    void aNightCloudIsDarkAndScattersLess() {
        // Vanilla's clear-night cloud colour.
        int packed = CloudTint.pack(0.1, 0.1, 0.15, 0.0f);
        assertTrue(red(packed) < 70, "night albedo is dark, was " + red(packed));
        assertTrue(blue(packed) > red(packed), "night keeps vanilla's blue lean");
        assertTrue(CloudTint.sssTransmittance(packed) < 0.96f);
    }

    @Test
    void aRainCloudIsANeutralToCoolGreyDarkerThanVanillaKeepsIt() {
        // Vanilla's daytime full-rain cloud colour is a flat grey near 0.62.
        int packed = CloudTint.pack(0.62, 0.62, 0.62, 1.0f);
        assertTrue(red(packed) < 130, "storm albedo is darker than half, was " + red(packed));
        assertTrue(blue(packed) >= green(packed) && green(packed) >= red(packed), "no warm cast");
    }

    @Test
    void anUntintedUpstreamColourDecodesToTheUpstreamTransmittance() {
        assertEquals(CloudTint.UPSTREAM_SSS_TRANSMITTANCE, CloudTint.sssTransmittance(0x00cc_cccc), 0.0f);
    }

    @Test
    void theOvercastSkyDimsTheDomeAndAddsDaytimeAmbientOnlyInWeather() {
        assertEquals(OvercastSky.DOME_SCALE, OvercastSky.domeScale(), 0.0f);
        assertEquals(0.0f, OvercastSky.extraAmbient(0.0f, 1.0f), 0.0f);
        assertEquals(0.0f, OvercastSky.extraAmbient(1.0f, 0.0f), 0.0f);
        assertEquals(OvercastSky.DAY_AMBIENT_AT_FULL_WEATHER, OvercastSky.extraAmbient(1.0f, 1.0f), 1.0e-6f);

        CommunityLook.setOvercastWeather(false);
        assertEquals(1.0f, OvercastSky.domeScale(), 0.0f);
        assertEquals(0.0f, OvercastSky.extraAmbient(1.0f, 1.0f), 0.0f);
    }

    private static int red(int packed) { return (packed >>> 16) & 0xff; }
    private static int green(int packed) { return (packed >>> 8) & 0xff; }
    private static int blue(int packed) { return packed & 0xff; }
}
