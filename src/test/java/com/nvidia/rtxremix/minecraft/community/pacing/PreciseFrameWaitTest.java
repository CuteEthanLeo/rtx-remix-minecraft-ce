/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.pacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreciseFrameWaitTest {

    private static final long MS = 1_000_000L;

    /** A clock whose parks overshoot by a fixed amount and whose spins advance 10 µs. */
    static final class FakeClock implements PreciseFrameWait.Clock {
        long now;
        long parkOvershoot;
        int parks;
        int spins;

        FakeClock(long parkOvershoot) {
            this.parkOvershoot = parkOvershoot;
        }

        @Override
        public long nanoTime() {
            return now;
        }

        @Override
        public void park(long nanos) {
            parks++;
            now += nanos + parkOvershoot;
        }

        @Override
        public void spin() {
            spins++;
            now += 10_000L;
        }
    }

    @Test
    void returnsAtOnceForAPassedDeadline() {
        FakeClock clock = new FakeClock(0);
        clock.now = 10 * MS;
        new PreciseFrameWait(clock).until(5 * MS);
        assertEquals(0, clock.parks + clock.spins);
    }

    @Test
    void endsOnTheDeadlineWithAFineTimer() {
        FakeClock clock = new FakeClock(50_000L);
        PreciseFrameWait wait = new PreciseFrameWait(clock);
        for (int frame = 1; frame <= 200; frame++) {
            long deadline = clock.now + 8 * MS;
            wait.until(deadline);
            assertTrue(clock.now >= deadline);
            assertTrue(clock.now - deadline <= 10_000L, "late by " + (clock.now - deadline));
        }
        assertTrue(wait.overshootEstimate() < PreciseFrameWait.INITIAL_OVERSHOOT_NANOS,
                "a fine timer lets the estimate decay");
    }

    @Test
    void stopsParkingOnACoarseTimer() {
        // Default Windows timer resolution: a park can end 15.6 ms late.
        FakeClock clock = new FakeClock(15_600_000L);
        PreciseFrameWait wait = new PreciseFrameWait(clock);
        for (int frame = 0; frame < 50; frame++) {
            long deadline = clock.now + 12 * MS;
            wait.until(deadline);
            if (frame > 0) {
                assertTrue(clock.now - deadline <= 10_000L, "late by " + (clock.now - deadline));
            }
        }
        assertEquals(1, clock.parks, "the one park that found the timer coarse");
    }

    @Test
    void retriesACoarseTimerEverMoreRarely() {
        FakeClock clock = new FakeClock(15_600_000L);
        PreciseFrameWait wait = new PreciseFrameWait(clock);
        int late = 0;
        int lateInTheLastTwoThousand = 0;
        for (int frame = 0; frame < 5000; frame++) {
            long deadline = clock.now + 12 * MS;
            wait.until(deadline);
            if (clock.now - deadline > 10_000L) {
                late++;
                if (frame >= 3000) {
                    lateInTheLastTwoThousand++;
                }
            }
        }
        // The first park, then retries about 90, 180, 370, 730 and 1470 waits apart.
        assertTrue(late <= 8, late + " late wakes");
        assertTrue(lateInTheLastTwoThousand <= 1, lateInTheLastTwoThousand + " late wakes in the last 2000");
    }

    @Test
    void parksAgainAfterOneParkThatRanLong() {
        FakeClock clock = new FakeClock(50_000L);
        PreciseFrameWait wait = new PreciseFrameWait(clock);
        for (int frame = 0; frame < 100; frame++) {
            wait.until(clock.now + 5 * MS);
        }
        // The thread is descheduled during one park, as a busy system does now and then.
        clock.parkOvershoot = 12 * MS;
        wait.until(clock.now + 5 * MS);
        clock.parkOvershoot = 50_000L;
        assertTrue(wait.overshootEstimate() >= 12 * MS, "the estimate takes the long park at its word");

        for (int frame = 0; frame < 1000; frame++) {
            wait.until(clock.now + 5 * MS);
        }
        int parksBefore = clock.parks;
        for (int frame = 0; frame < 100; frame++) {
            long deadline = clock.now + 5 * MS;
            wait.until(deadline);
            assertTrue(clock.now - deadline <= 10_000L, "late by " + (clock.now - deadline));
        }
        assertTrue(clock.parks > parksBefore, "the wait parks again instead of spinning for the rest of the session");
        assertTrue(wait.overshootEstimate() < PreciseFrameWait.INITIAL_OVERSHOOT_NANOS,
                "and the fine timer brings the estimate back down");
    }
}
