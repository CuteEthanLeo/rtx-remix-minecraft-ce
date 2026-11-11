/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.pacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FrameGenPacingTest {

    private static final long MS = 1_000_000L;

    @AfterEach
    void restoreSetting() {
        FrameGenPacing.setEnabled(FrameGenPacing.DEFAULT_ENABLED);
    }

    /** Runs frames whose busy time alternates, as a jittery load does, and returns their periods. */
    private static long[] run(FrameGenPacing pacing, PreciseFrameWaitTest.FakeClock clock, boolean active, int frames) {
        long[] periods = new long[frames];
        long last = clock.now;
        for (int i = 0; i < frames; i++) {
            clock.now += i % 2 == 0 ? 7 * MS : 11 * MS;
            pacing.afterPresent(active);
            periods[i] = clock.now - last;
            last = clock.now;
        }
        return periods;
    }

    @Test
    void evensOutAJitteryLoadWhileFrameGenerationIsOn() {
        PreciseFrameWaitTest.FakeClock clock = new PreciseFrameWaitTest.FakeClock(50_000L);
        FrameGenPacing pacing = new FrameGenPacing(clock);
        long[] periods = run(pacing, clock, true, 600);
        long min = Long.MAX_VALUE;
        long max = 0;
        for (int i = 400; i < periods.length; i++) {
            min = Math.min(min, periods[i]);
            max = Math.max(max, periods[i]);
        }
        assertTrue(pacing.isEngaged());
        assertTrue(max - min <= 20_000L, "periods range " + min + ".." + max);
        assertTrue(min >= 11 * MS, "the steady interval covers the slow frames");
    }

    @Test
    void leavesFramesAloneWithoutFrameGeneration() {
        PreciseFrameWaitTest.FakeClock clock = new PreciseFrameWaitTest.FakeClock(0);
        FrameGenPacing pacing = new FrameGenPacing(clock);
        long[] periods = run(pacing, clock, false, 300);
        assertFalse(pacing.isEngaged());
        assertEquals(0, clock.parks + clock.spins);
        assertEquals(7 * MS, periods[298]);
        assertTrue(pacing.shouldRunVanillaLimiter(60), "vanilla keeps its limiter");
    }

    @Test
    void theSettingTurnsItOff() {
        FrameGenPacing.setEnabled(false);
        PreciseFrameWaitTest.FakeClock clock = new PreciseFrameWaitTest.FakeClock(0);
        FrameGenPacing pacing = new FrameGenPacing(clock);
        run(pacing, clock, true, 300);
        assertFalse(pacing.isEngaged());
        assertEquals(0, clock.parks + clock.spins);
    }

    @Test
    void takesTheMaxFramerateCapOverWhileEngaged() {
        PreciseFrameWaitTest.FakeClock clock = new PreciseFrameWaitTest.FakeClock(0);
        FrameGenPacing pacing = new FrameGenPacing(clock);
        long last = clock.now;
        long period = 0;
        for (int i = 0; i < 400; i++) {
            clock.now += 4 * MS;
            pacing.afterPresent(true);
            assertFalse(pacing.shouldRunVanillaLimiter(50), "vanilla's limiter is skipped while engaged");
            period = clock.now - last;
            last = clock.now;
        }
        assertTrue(Math.abs(period - 20 * MS) <= 20_000L, "a 50 fps cap is kept with the precise wait: " + period);
    }

    @Test
    void returnsToTheSteadyIntervalAsSoonAsACapLifts() {
        PreciseFrameWaitTest.FakeClock clock = new PreciseFrameWaitTest.FakeClock(0);
        FrameGenPacing pacing = new FrameGenPacing(clock);
        long last = clock.now;
        long period = 0;
        // 400 free frames, 600 under vanilla's 30 fps idle limiter with its lighter load, then
        // the player moves the mouse and vanilla stops asking for a limit at all.
        for (int i = 0; i < 1030; i++) {
            boolean idle = i >= 400 && i < 1000;
            clock.now += idle ? 8_500_000L : 9 * MS;
            pacing.afterPresent(true);
            if (idle) {
                pacing.shouldRunVanillaLimiter(30);
            }
            period = clock.now - last;
            last = clock.now;
            if (i == 999) {
                assertTrue(Math.abs(period - 1_000_000_000L / 30) <= 20_000L, "the idle cap is kept: " + period);
            }
        }
        assertTrue(period <= 10 * MS, "frames are back at their own pace 30 frames after the cap lifted: " + period);
    }
}
