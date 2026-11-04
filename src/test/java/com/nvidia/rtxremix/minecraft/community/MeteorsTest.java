/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeteorsTest {

    private static final long FRAME = 1_000_000_000L / 60;

    @Test
    void meteorsFallAcrossTheNightSkyAndNeverInDaylight() {
        Meteors meteors = new Meteors(new Random(7));
        long now = 0L;
        for (int frame = 0; frame < 60 * 60; frame++, now += FRAME) {
            assertFalse(meteors.update(now, 0.0f, 1), "no meteor while the stars are hidden");
        }

        int framesInFlight = 0;
        float firstHeight = Float.NaN;
        float lastHeight = Float.NaN;
        for (int frame = 0; frame < 60 * 60; frame++, now += FRAME) {
            if (!meteors.update(now, 1.0f, 1)) {
                if (framesInFlight > 0) break;
                continue;
            }
            framesInFlight++;
            float[] head = meteors.head();
            float length = (float) Math.sqrt(head[0] * head[0] + head[1] * head[1] + head[2] * head[2]);
            assertEquals(1.0f, length, 1.0e-4f, "the head stays on the sky sphere");
            if (Float.isNaN(firstHeight)) firstHeight = head[1];
            lastHeight = head[1];
            assertTrue(meteors.brightness() > 0.0f);
        }
        assertTrue(framesInFlight > 10, "a meteor flew for " + framesInFlight + " frames");
        assertTrue(lastHeight < firstHeight, "it fell: " + firstHeight + " -> " + lastHeight);
    }

    @Test
    void showerNightsHaveMoreMeteors() {
        assertTrue(count(0L) > count(1L) * 2, "day 0 is a shower night");
    }

    @Test
    void theEnvelopeFlaresAndFades() {
        assertEquals(0.0f, Meteors.envelope(0.0f), 0.0f);
        assertEquals(1.0f, Meteors.envelope(0.3f), 1.0e-6f);
        assertTrue(Meteors.envelope(0.9f) < 0.2f);
        assertEquals(0.0f, Meteors.envelope(1.0f), 0.0f);
    }

    private static int count(long day) {
        Meteors meteors = new Meteors(new Random(11));
        int started = 0;
        boolean wasActive = false;
        long now = 0L;
        for (int frame = 0; frame < 60 * 600; frame++, now += FRAME) {
            boolean active = meteors.update(now, 1.0f, day);
            if (active && !wasActive) started++;
            wasActive = active;
        }
        return started;
    }
}
