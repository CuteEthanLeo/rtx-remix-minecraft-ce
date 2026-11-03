/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainRipplesTest {

    @AfterEach
    void restoreDefaults() {
        RainRipples.setEnabled(true);
    }

    @Test
    void sheetsAreDeterministicWithRingsAboveTheFilm() {
        RainRipples.Sheets first = RainRipples.generate(7L);
        RainRipples.Sheets second = RainRipples.generate(7L);
        assertArrayEquals(first.albedo(), second.albedo());
        assertArrayEquals(first.normal(), second.normal());

        int side = RainRipples.sheetSize();
        assertEquals(side * side * 4, first.albedo().length);
        int minAlpha = 255;
        int maxAlpha = 0;
        for (int i = 3; i < first.albedo().length; i += 4) {
            int alpha = first.albedo()[i] & 0xFF;
            minAlpha = Math.min(minAlpha, alpha);
            maxAlpha = Math.max(maxAlpha, alpha);
        }
        assertTrue(minAlpha > 0, "a thin film covers the whole tile");
        assertTrue(maxAlpha > minAlpha + 60, "rings stand out from the film");
    }

    @Test
    void normalsPointUpAndTilt() {
        byte[] normal = RainRipples.generate(7L).normal();
        int tilted = 0;
        for (int i = 0; i < normal.length; i += 4) {
            assertTrue((normal[i + 2] & 0xFF) > 127, "z stays positive");
            if (Math.abs((normal[i] & 0xFF) - 128) > 8) tilted++;
        }
        assertTrue(tilted > 1000, "rings bend the surface normal");
    }

    @Test
    void ripplesShowOnlyInRealRainAndWhenEnabled() {
        assertFalse(RainRipples.visible(0.0f));
        assertFalse(RainRipples.visible(RainRipples.SHOW_ABOVE));
        assertTrue(RainRipples.visible(1.0f));
        RainRipples.setEnabled(false);
        assertFalse(RainRipples.visible(1.0f));
    }
}
