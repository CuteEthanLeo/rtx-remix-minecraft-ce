/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrameTimeHistogramTest {

    private static final long MS = 1_000_000L;

    @Test
    void anEmptyHistogramReportsZero() {
        FrameTimeHistogram histogram = new FrameTimeHistogram();
        assertEquals(0, histogram.count());
        assertEquals(0.0, histogram.meanMillis());
        assertEquals(0.0, histogram.fps());
        assertEquals(0.0, histogram.percentileMillis(0.99));
        assertEquals(0.0, histogram.onePercentLowFps());
    }

    @Test
    void uniformFramesReportTheirExactTimeAndRate() {
        FrameTimeHistogram histogram = new FrameTimeHistogram();
        for (int i = 0; i < 100; i++) {
            histogram.add(10 * MS);
        }
        assertEquals(10.0, histogram.meanMillis(), 1e-9);
        assertEquals(100.0, histogram.fps(), 1e-9);
        assertEquals(10.0, histogram.percentileMillis(0.5), 1e-9, "capped at the largest frame");
        assertEquals(100.0, histogram.onePercentLowFps(), 1e-9);
    }

    @Test
    void percentilesResolveToTheirBucketWithinAQuarterMillisecond() {
        FrameTimeHistogram histogram = new FrameTimeHistogram();
        for (int i = 0; i < 98; i++) {
            histogram.add(10 * MS);
        }
        histogram.add(40 * MS);
        histogram.add(40 * MS);

        assertEquals(10.25, histogram.percentileMillis(0.50), 1e-9);
        assertEquals(10.25, histogram.percentileMillis(0.98), 1e-9);
        assertEquals(40.0, histogram.percentileMillis(0.99), 1e-9);
        assertEquals(25.0, histogram.onePercentLowFps(), 1e-9);
        assertEquals(10.0, histogram.minMillis(), 1e-9);
        assertEquals(40.0, histogram.maxMillis(), 1e-9);
    }

    @Test
    void framesBeyondTheRangeReportTheLargestFrame() {
        FrameTimeHistogram histogram = new FrameTimeHistogram();
        histogram.add(150 * MS);
        histogram.add(250 * MS);
        assertEquals(250.0, histogram.percentileMillis(0.5), 1e-9);
        assertEquals(200.0, histogram.meanMillis(), 1e-9);
    }

    @Test
    void nonPositiveFramesAreIgnoredAndResetClears() {
        FrameTimeHistogram histogram = new FrameTimeHistogram();
        histogram.add(0);
        histogram.add(-5);
        assertEquals(0, histogram.count());

        histogram.add(16 * MS);
        histogram.reset();
        assertEquals(0, histogram.count());
        assertEquals(0L, histogram.totalNanos());
        assertEquals(0.0, histogram.maxMillis());
    }
}
