/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightningFlashTest {

    @AfterEach
    void restoreDefaults() {
        LightningFlash.setEnabled(LightningFlash.DEFAULT_ENABLED);
        LightningFlash.setBoltRadiance(LightningFlash.DEFAULT_BOLT_RADIANCE);
        LightningFlash.reset();
    }

    @Test
    void aFlashJumpsToFullAndHalvesEachHalfLife() {
        assertEquals(1.0f, LightningFlash.nextLevel(0.0f, true, 0.016f), 0.0f);
        assertEquals(0.5f, LightningFlash.nextLevel(1.0f, false, LightningFlash.DECAY_HALF_LIFE_SECONDS), 1.0e-5f);
        float level = 1.0f;
        for (int frame = 0; frame < 30; frame++) {
            level = LightningFlash.nextLevel(level, false, 1.0f / 60.0f);
        }
        assertEquals(0.0f, level, 0.0f, "dark again within half a second");
    }

    @Test
    void boltLightsAreColdAndDarkBetweenFlashes() {
        LightningFlash.advance(0, 0.016f);
        assertNull(LightningFlash.boltRadianceNow());

        LightningFlash.advance(2, 0.016f);
        float[] radiance = LightningFlash.boltRadianceNow();
        assertNotNull(radiance);
        assertEquals(LightningFlash.DEFAULT_BOLT_RADIANCE, radiance[2], 1.0e-3f);
        assertTrue(radiance[0] < radiance[2], "cold: red below blue");
        assertEquals(LightningFlash.SKY_FLASH_AMBIENT, LightningFlash.skyAmbientBoost(1.0f), 1.0e-5f);
        assertEquals(0.0f, LightningFlash.skyAmbientBoost(0.0f), 0.0f);
    }

    @Test
    void switchedOffNeverFlashes() {
        LightningFlash.setEnabled(false);
        LightningFlash.advance(2, 0.016f);
        assertEquals(0.0f, LightningFlash.level(), 0.0f);
        assertNull(LightningFlash.boltRadianceNow());
    }

    @Test
    void cloudFlashUsesTheNearestStrikeAndRetainsItsPositionDuringDecay() {
        LightningFlash.advance(2, 0.016f);
        assertEquals(0, LightningFlash.cloudRadiance(2));
        LightningFlash.observeStrike(100, 100, 0, 0);
        LightningFlash.observeStrike(20, 30, 0, 0);
        LightningFlash.observeStrike(200, 200, 0, 0);
        assertEquals(20, LightningFlash.strikeX());
        assertEquals(30, LightningFlash.strikeZ());
        float peak = LightningFlash.cloudRadiance(2);
        assertEquals(LightningFlash.DEFAULT_BOLT_RADIANCE * LightningFlash.CLOUD_LIGHT_RADIANCE_SCALE, peak);
        long revision = LightningFlash.cloudRevision();
        LightningFlash.advance(0, LightningFlash.DECAY_HALF_LIFE_SECONDS);
        assertEquals(20, LightningFlash.strikeX());
        assertEquals(peak * 0.5f, LightningFlash.cloudRadiance(2), 1.0e-4f);
        assertTrue(LightningFlash.cloudRevision() > revision);
        LightningFlash.advance(0, 1);
        assertEquals(0, LightningFlash.cloudRadiance(2));
        LightningFlash.reset();
        assertEquals(0, LightningFlash.cloudRadiance(2));
    }
}
