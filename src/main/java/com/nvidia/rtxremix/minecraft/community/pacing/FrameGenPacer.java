/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.pacing;

import java.util.Arrays;

/**
 * Chooses a steady real-frame interval while DLSS frame generation is on.
 *
 * <p>On GPUs without hardware present metering, the runtime's frame generation spaces each
 * generated frame and the next real frame by the previous real-frame interval: with one
 * generated frame they go on screen {@code prev/2} and {@code cur - prev/2} apart. Any jitter
 * in the real interval therefore alternates the displayed frame times, a saw-tooth whatever the
 * frame rate. Holding real frames to a fixed interval just above what the frame needs keeps the
 * two halves equal.
 *
 * <p>Each frame reports its busy time, from the end of the previous pacing wait to the end of
 * its Present. Every {@link #RECOMPUTE_FRAMES} frames the target is recomputed from the last
 * {@link #WINDOW} busy times:
 * <ul>
 * <li>It rises only when frames really miss it: more than {@link #MISS_FRACTION} of the window
 *     ran over, so it becomes the 90th percentile with {@link #RAISE_MARGIN}. A wait inside
 *     Present that stretches to whatever interval it is given, such as Reflex's, never pushes
 *     it up.</li>
 * <li>It falls slowly: after {@link #LOWER_AFTER_FRAMES} frames without a raise, if the 95th
 *     percentile with {@link #SETTLE_MARGIN} sits below {@link #LOWER_HEADROOM} of it, towards
 *     that value by at most {@link #LOWER_STEP} at a time. At that value at most 5% of frames
 *     run over, so a fall never invites the next raise.</li>
 * <li>It follows the frames alone. The player's own cap lengthens the interval a frame is held
 *     to and never enters the target, so a cap that lifts, as vanilla's idle limiter does on the
 *     next input, frees the frame rate at once instead of at the slow fall above.</li>
 * <li>Frames slower than {@link #MAX_INTERVAL_NANOS} are not paced, because frame generation
 *     stops spacing them anyway.</li>
 * </ul>
 *
 * <p>Pure logic: no clock, GL or Remix calls, and no allocation after construction.
 */
public final class FrameGenPacer {

    static final int WINDOW = 128;
    static final int RECOMPUTE_FRAMES = 15;
    static final double MISS_FRACTION = 0.05;
    static final long MISS_TOLERANCE_NANOS = 250_000L;
    static final double RAISE_MARGIN = 1.03;
    static final double SETTLE_MARGIN = 1.04;
    static final double LOWER_HEADROOM = 0.99;
    static final double LOWER_STEP = 0.95;
    /** About two seconds at the rates frame generation runs at. */
    static final int LOWER_AFTER_FRAMES = 120;
    static final long MIN_INTERVAL_NANOS = 1_000_000L;
    /** 20 real frames per second; below it frame generation is not worth pacing. */
    static final long MAX_INTERVAL_NANOS = 50_000_000L;

    private final long[] busy = new long[WINDOW];
    private final long[] sorted = new long[WINDOW];
    private int next;
    private int count;
    private int sinceRecompute;
    private int sinceRaise;
    private long target;

    private long reportFrames;
    private long reportMisses;
    private long reportPaced;

    /**
     * Records one frame and returns the interval the next frame should start at, measured from
     * the start of the frame just recorded, or 0 to start it at once.
     *
     * @param busyNanos      the frame's busy time
     * @param userCapNanos   the interval of the player's own frame cap, or 0 for none
     */
    public long onFrame(long busyNanos, long userCapNanos) {
        long floor = Math.max(MIN_INTERVAL_NANOS, userCapNanos);
        reportFrames++;
        if (target > 0 && busyNanos > Math.max(target, floor) + MISS_TOLERANCE_NANOS) {
            reportMisses++;
        }
        busy[next] = busyNanos;
        next = (next + 1) % WINDOW;
        if (count < WINDOW) {
            count++;
        }
        sinceRaise++;
        if (++sinceRecompute >= RECOMPUTE_FRAMES && count == WINDOW) {
            sinceRecompute = 0;
            recompute();
        }
        if (target == 0) {
            return userCapNanos;
        }
        long interval = Math.max(target, floor);
        if (interval > MAX_INTERVAL_NANOS) {
            return userCapNanos;
        }
        reportPaced++;
        return interval;
    }

    private void recompute() {
        System.arraycopy(busy, 0, sorted, 0, WINDOW);
        Arrays.sort(sorted);
        long p90 = sorted[(int) (WINDOW * 0.90)];
        long p95 = sorted[(int) (WINDOW * 0.95)];
        if (target == 0) {
            target = clamp((long) (p95 * SETTLE_MARGIN));
            sinceRaise = 0;
            return;
        }
        int misses = 0;
        long missAbove = target + MISS_TOLERANCE_NANOS;
        for (long value : busy) {
            if (value > missAbove) {
                misses++;
            }
        }
        if (misses > WINDOW * MISS_FRACTION) {
            long raised = clamp((long) (p90 * RAISE_MARGIN));
            if (raised > target) {
                target = raised;
                sinceRaise = 0;
            }
            return;
        }
        long settled = (long) (p95 * SETTLE_MARGIN);
        if (sinceRaise >= LOWER_AFTER_FRAMES && settled < target * LOWER_HEADROOM) {
            long stepped = (long) (target * LOWER_STEP);
            target = clamp(Math.max(settled, stepped));
            sinceRaise = 0;
        }
    }

    /** Keeps the target a real interval. The player's cap is not applied here; see the class comment. */
    private static long clamp(long interval) {
        return Math.max(MIN_INTERVAL_NANOS, interval);
    }

    /** Forgets every frame, so pacing waits for a full window again. */
    public void reset() {
        next = 0;
        count = 0;
        sinceRecompute = 0;
        sinceRaise = 0;
        target = 0;
    }

    /** The paced interval, or 0 before the first full window. */
    public long target() {
        return target;
    }

    public long reportFrames() {
        return reportFrames;
    }

    /** Frames that ran past the interval they were held to by more than the tolerance. */
    public long reportMisses() {
        return reportMisses;
    }

    /** Frames that were followed by a pacing interval. */
    public long reportPaced() {
        return reportPaced;
    }

    public void resetReport() {
        reportFrames = 0;
        reportMisses = 0;
        reportPaced = 0;
    }
}
