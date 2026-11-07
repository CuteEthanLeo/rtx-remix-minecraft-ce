/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.perf;

import com.nvidia.rtxremix.minecraft.profiling.FrameTimeHistogram;

/**
 * The in-game A/B run behind {@code /rtx topopt ab}: Top Optimization off and
 * on in alternating blocks, measured on real wall-clock frame times.
 *
 * <p>Six blocks run in the order off, on, on, off, off, on, so a laptop that
 * heats up or a scene that slowly changes biases neither arm, and each
 * adjacent off/on pair yields one speedup ratio. Each block first settles for
 * {@link #SETTLE_NANOS}, discarding frames that began inside it, then measures
 * for {@link #MEASURE_NANOS}. The result is the geometric mean of the three
 * pair ratios, with the per-arm frame rates and 1% lows.
 *
 * <p>Pure state, with time passed in. The caller applies {@link #currentArmOn}
 * whenever {@link #onFrame} reports a block change, and aborts when the
 * player opens a screen or the world changes.
 */
public final class TopOptimizationAb {

    public enum Status { IDLE, RUNNING, FINISHED, ABORTED }

    private static final boolean[] ARM_ON = {false, true, true, false, false, true};
    public static final int BLOCKS = ARM_ON.length;
    public static final long SETTLE_NANOS = 1_500_000_000L;
    public static final long MEASURE_NANOS = 5_000_000_000L;
    public static final long BLOCK_NANOS = SETTLE_NANOS + MEASURE_NANOS;
    /** Frames a block needs for its ratio to count. */
    static final int MIN_BLOCK_FRAMES = 30;

    private Status status = Status.IDLE;
    private int block;
    private long blockStartNanos;
    private final long[] blockNanos = new long[BLOCKS];
    private final int[] blockFrames = new int[BLOCKS];
    private final FrameTimeHistogram offFrames = new FrameTimeHistogram();
    private final FrameTimeHistogram onFrames = new FrameTimeHistogram();
    private String abortReason;

    /** Starts a run at {@code nowNanos}; false when one is already running. */
    public boolean start(long nowNanos) {
        if (status == Status.RUNNING) {
            return false;
        }
        status = Status.RUNNING;
        block = 0;
        blockStartNanos = nowNanos;
        java.util.Arrays.fill(blockNanos, 0L);
        java.util.Arrays.fill(blockFrames, 0);
        offFrames.reset();
        onFrames.reset();
        abortReason = null;
        return true;
    }

    public Status status() {
        return status;
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    /** The zero-based block running now. */
    public int block() {
        return block;
    }

    /** Whether Top Optimization is on in the block running now. */
    public boolean currentArmOn() {
        return ARM_ON[block];
    }

    /**
     * Feeds one frame that ended at {@code nowNanos} and took {@code periodNanos}.
     *
     * @return true when a new block began, so the caller must apply {@link #currentArmOn}
     */
    public boolean onFrame(long nowNanos, long periodNanos) {
        if (status != Status.RUNNING) {
            return false;
        }
        if (nowNanos - blockStartNanos >= BLOCK_NANOS) {
            if (block + 1 >= BLOCKS) {
                status = Status.FINISHED;
                return false;
            }
            block++;
            blockStartNanos = nowNanos;
            return true;
        }
        if (periodNanos > 0 && nowNanos - periodNanos >= blockStartNanos + SETTLE_NANOS) {
            blockNanos[block] += periodNanos;
            blockFrames[block]++;
            (ARM_ON[block] ? onFrames : offFrames).add(periodNanos);
        }
        return false;
    }

    /** Stops a running A/B; the caller restores the player's own setting. */
    public void abort(String reason) {
        if (status == Status.RUNNING) {
            status = Status.ABORTED;
            abortReason = reason;
        }
    }

    public String abortReason() {
        return abortReason;
    }

    /** The finished run's numbers; meaningful once {@link #status} is {@link Status#FINISHED}. */
    public Result result() {
        double[] ratios = new double[BLOCKS / 2];
        double logSum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = 0.0;
        boolean complete = true;
        for (int pair = 0; pair < ratios.length; pair++) {
            int first = pair * 2;
            int second = first + 1;
            int onBlock = ARM_ON[first] ? first : second;
            int offBlock = ARM_ON[first] ? second : first;
            double onFps = blockFps(onBlock);
            double offFps = blockFps(offBlock);
            if (onFps <= 0.0 || offFps <= 0.0) {
                ratios[pair] = Double.NaN;
                complete = false;
                continue;
            }
            double ratio = onFps / offFps;
            ratios[pair] = ratio;
            logSum += Math.log(ratio);
            min = Math.min(min, ratio);
            max = Math.max(max, ratio);
        }
        double geometricMean = complete ? Math.exp(logSum / ratios.length) : Double.NaN;
        return new Result(
                offFrames.fps(), onFrames.fps(),
                offFrames.onePercentLowFps(), onFrames.onePercentLowFps(),
                ratios, geometricMean,
                complete ? min : Double.NaN, complete ? max : Double.NaN,
                offFrames.count(), onFrames.count());
    }

    private double blockFps(int index) {
        if (blockFrames[index] < MIN_BLOCK_FRAMES || blockNanos[index] <= 0) {
            return 0.0;
        }
        return blockFrames[index] * 1.0e9 / blockNanos[index];
    }

    /**
     * One finished A/B run. A NaN ratio marks a pair with too few frames, and
     * a NaN {@code speedup} means at least one pair was unusable.
     */
    public record Result(
            double offFps, double onFps,
            double offOnePercentLowFps, double onOnePercentLowFps,
            double[] pairRatios, double speedup, double minPairRatio, double maxPairRatio,
            int offFrameCount, int onFrameCount) {

        public boolean complete() {
            return !Double.isNaN(speedup);
        }
    }
}
