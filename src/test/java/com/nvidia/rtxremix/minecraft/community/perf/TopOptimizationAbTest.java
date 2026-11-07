/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.perf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TopOptimizationAbTest {

    private static final long MS = 1_000_000L;

    /** Plays frames whose length depends on the arm until the run ends, recording each arm in order. */
    private static List<Boolean> play(TopOptimizationAb ab, long offNanos, long onNanos, long startNanos) {
        List<Boolean> arms = new ArrayList<>();
        arms.add(ab.currentArmOn());
        long now = startNanos;
        int guard = 0;
        while (ab.isRunning()) {
            long period = ab.currentArmOn() ? onNanos : offNanos;
            now += period;
            if (ab.onFrame(now, period)) {
                arms.add(ab.currentArmOn());
            }
            if (++guard > 1_000_000) {
                throw new AssertionError("the run never ended");
            }
        }
        return arms;
    }

    @Test
    void sixBlocksAlternateSoNeitherArmGetsTheWarmerHalf() {
        TopOptimizationAb ab = new TopOptimizationAb();
        assertTrue(ab.start(0));

        List<Boolean> arms = play(ab, 25 * MS, 16 * MS, 0);

        assertEquals(List.of(false, true, true, false, false, true), arms);
        assertEquals(TopOptimizationAb.Status.FINISHED, ab.status());
    }

    @Test
    void theSpeedupIsTheGeometricMeanOfThePairRatios() {
        TopOptimizationAb ab = new TopOptimizationAb();
        ab.start(5_000 * MS);
        play(ab, 25 * MS, 16 * MS, 5_000 * MS);

        TopOptimizationAb.Result result = ab.result();
        assertTrue(result.complete());
        assertEquals(40.0, result.offFps(), 1e-6);
        assertEquals(62.5, result.onFps(), 1e-6);
        assertEquals(25.0 / 16.0, result.speedup(), 1e-9);
        assertArrayEquals(new double[]{25.0 / 16.0, 25.0 / 16.0, 25.0 / 16.0}, result.pairRatios(), 1e-9);
        assertEquals(40.0, result.offOnePercentLowFps(), 1e-6);
        // Each block measures five of its six and a half seconds.
        long measuredOn = Math.round(3 * TopOptimizationAb.MEASURE_NANOS / (16.0 * MS));
        assertEquals(measuredOn, result.onFrameCount(), 3);
    }

    @Test
    void framesInsideTheSettleWindowAreDiscarded() {
        TopOptimizationAb ab = new TopOptimizationAb();
        ab.start(0);
        // A hitch right after the switch, which a real arm change can cause.
        ab.onFrame(900 * MS, 900 * MS);
        play(ab, 20 * MS, 20 * MS, 900 * MS);

        TopOptimizationAb.Result result = ab.result();
        assertEquals(50.0, result.offFps(), 1e-6);
        assertEquals(20.0, result.offOnePercentLowFps() > 0 ? 1000.0 / result.offOnePercentLowFps() : 0, 1e-6);
        assertEquals(1.0, result.speedup(), 1e-9);
    }

    @Test
    void aBlockWithTooFewFramesLeavesTheSpeedupUnknown() {
        TopOptimizationAb ab = new TopOptimizationAb();
        ab.start(0);
        play(ab, 1_000 * MS, 1_000 * MS, 0);

        TopOptimizationAb.Result result = ab.result();
        assertFalse(result.complete());
        assertTrue(Double.isNaN(result.speedup()));
        assertTrue(Double.isNaN(result.pairRatios()[0]));
    }

    @Test
    void anAbortStopsTheRunAndKeepsItsReason() {
        TopOptimizationAb ab = new TopOptimizationAb();
        ab.start(0);
        ab.onFrame(16 * MS, 16 * MS);

        ab.abort("screen");

        assertEquals(TopOptimizationAb.Status.ABORTED, ab.status());
        assertEquals("screen", ab.abortReason());
        assertFalse(ab.onFrame(10_000 * MS, 16 * MS));
        assertFalse(ab.isRunning());
    }

    @Test
    void onlyOneRunAtATimeButAFinishedRunCanRestart() {
        TopOptimizationAb ab = new TopOptimizationAb();
        assertTrue(ab.start(0));
        assertFalse(ab.start(1));

        ab.abort("world");
        assertTrue(ab.start(2));
        assertEquals(0, ab.block());
        assertFalse(ab.currentArmOn());
    }
}
