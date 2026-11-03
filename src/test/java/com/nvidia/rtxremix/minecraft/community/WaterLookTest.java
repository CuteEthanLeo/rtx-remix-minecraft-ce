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

class WaterLookTest {

    @AfterEach
    void restoreDefaults() {
        WaterLook.setRealisticWater(true);
    }

    @Test
    void vanillaBiomeColoursFindTheirOwnEntriesAndBucketKeysRoundTrip() {
        assertEquals(0, WaterLook.paletteIndex(0x3F76E4), "default water");
        assertEquals(5, WaterLook.paletteIndex(0x617B64), "swamp");
        assertEquals(1, WaterLook.paletteIndex(0x43D5EE), "warm ocean");
        for (int i = 0; i < WaterLook.PALETTE_SIZE; i++) {
            String key = WaterLook.bucketKey(i);
            assertTrue(WaterLook.isWaterBucket(key));
            assertEquals(i, WaterLook.indexOfBucketKey(key));
        }
        assertEquals("water", WaterLook.bucketKey(0), "the default bucket keeps its upstream key");
        assertFalse(WaterLook.isWaterBucket("water_sss"));
        assertFalse(WaterLook.isWaterBucket("translucent"));
        assertFalse(WaterLook.isWaterBucket("water99"));
    }

    @Test
    void realisticWaterSeesFurtherThanUpstreamAndSwampsLessThanWarmOceans() {
        WaterLook.Optics plains = WaterLook.optics(0, WaterLook.UPSTREAM);
        WaterLook.Optics swamp = WaterLook.optics(WaterLook.paletteIndex(0x617B64), WaterLook.UPSTREAM);
        WaterLook.Optics warm = WaterLook.optics(WaterLook.paletteIndex(0x43D5EE), WaterLook.UPSTREAM);
        assertTrue(plains.distance() > WaterLook.UPSTREAM.distance());
        assertTrue(swamp.distance() < warm.distance());
    }

    @Test
    void aSessionOverrideScalesEveryEntryAndTheSwitchRestoresUpstream() {
        WaterLook.Optics doubled = new WaterLook.Optics(0.6f, 0.9f, 0.89f, 2.0f);
        assertEquals(7.0f, WaterLook.optics(0, doubled).distance(), 1.0e-5f);

        WaterLook.setRealisticWater(false);
        assertEquals(WaterLook.UPSTREAM, WaterLook.optics(3, WaterLook.UPSTREAM));
    }
}
