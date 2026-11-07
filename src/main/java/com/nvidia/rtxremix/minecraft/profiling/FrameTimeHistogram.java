/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.profiling;

import java.util.Arrays;

/**
 * Wall-clock frame times in fixed 0.25 ms buckets up to 100 ms, plus one
 * overflow bucket, so percentiles and the 1% low cost no per-frame allocation
 * and no sort. Percentiles resolve to a bucket's upper edge, capped at the
 * largest frame seen, so they are exact to 0.25 ms.
 */
public final class FrameTimeHistogram {

    public static final long BUCKET_NANOS = 250_000L;
    /** Buckets below the overflow bucket: 0.25 ms each, covering 0 to 100 ms. */
    public static final int BUCKETS = 400;

    private final int[] counts = new int[BUCKETS + 1];
    private int count;
    private long totalNanos;
    private long minNanos = Long.MAX_VALUE;
    private long maxNanos;

    /** Adds one frame; a non-positive duration is ignored. */
    public void add(long nanos) {
        if (nanos <= 0) {
            return;
        }
        int bucket = nanos >= BUCKET_NANOS * BUCKETS ? BUCKETS : (int) (nanos / BUCKET_NANOS);
        counts[bucket]++;
        count++;
        totalNanos += nanos;
        minNanos = Math.min(minNanos, nanos);
        maxNanos = Math.max(maxNanos, nanos);
    }

    public int count() {
        return count;
    }

    public long totalNanos() {
        return totalNanos;
    }

    public double meanMillis() {
        return count == 0 ? 0.0 : totalNanos / (double) count / 1_000_000.0;
    }

    public double minMillis() {
        return count == 0 ? 0.0 : minNanos / 1_000_000.0;
    }

    public double maxMillis() {
        return maxNanos / 1_000_000.0;
    }

    /** Frames per second over the recorded frames: frame count over their total time. */
    public double fps() {
        return totalNanos <= 0 ? 0.0 : count * 1.0e9 / totalNanos;
    }

    /**
     * The frame time at quantile {@code q} (0 to 1, nearest rank), in
     * milliseconds: the upper edge of the bucket holding that frame, capped at
     * the largest frame recorded. Zero when nothing was recorded.
     */
    public double percentileMillis(double q) {
        if (count == 0) {
            return 0.0;
        }
        long rank = (long) Math.ceil(q * count);
        rank = Math.max(1L, Math.min(count, rank));
        long seen = 0;
        for (int bucket = 0; bucket < BUCKETS; bucket++) {
            seen += counts[bucket];
            if (seen >= rank) {
                return Math.min((bucket + 1) * BUCKET_NANOS, maxNanos) / 1_000_000.0;
            }
        }
        return maxNanos / 1_000_000.0;
    }

    /** The 1% low: the frame rate the 99th-percentile frame time corresponds to. */
    public double onePercentLowFps() {
        double p99 = percentileMillis(0.99);
        return p99 <= 0.0 ? 0.0 : 1000.0 / p99;
    }

    public void reset() {
        Arrays.fill(counts, 0);
        count = 0;
        totalNanos = 0;
        minNanos = Long.MAX_VALUE;
        maxNanos = 0;
    }
}
