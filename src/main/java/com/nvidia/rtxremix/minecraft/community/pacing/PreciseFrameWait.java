/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.pacing;

import java.util.concurrent.locks.LockSupport;

/**
 * Waits until a deadline to within a few microseconds.
 *
 * <p>A coarse sleep on Windows can overshoot by a whole timer period, up to about 16 ms when no
 * process has raised the timer resolution, and that overshoot is exactly the jitter pacing is
 * there to remove. So the wait parks only while more than the largest recent overshoot plus
 * {@link #SPIN_MARGIN_NANOS} remains and spins the rest. The overshoot estimate rises at once
 * to any overshoot it sees and decays slowly, so a coarse timer quickly turns the wait into a
 * spin and a fine one lets it park almost to the deadline.
 *
 * <p>Only a park measures the timer, and a wait shorter than the estimate never parks. So a
 * wait that only spins lets the estimate drift back towards its initial value, until a park is
 * tried again: otherwise one park that ran long because the thread was descheduled would turn
 * every later wait of that length into a spin for the rest of the session. A timer that really
 * is coarse makes that park run long again, and each time it does the drift is halved, so
 * those late wakes thin out instead of recurring at a fixed rate.
 *
 * <p>No native call, no allocation.
 */
public final class PreciseFrameWait {

    /** The clock and the two ways of waiting, so the calibration can be tested. */
    public interface Clock {
        long nanoTime();

        void park(long nanos);

        void spin();
    }

    static final long SPIN_MARGIN_NANOS = 500_000L;
    /** Assume a coarse timer until a park proves otherwise. */
    static final long INITIAL_OVERSHOOT_NANOS = 2_000_000L;
    /** Overshoots above this are a descheduled thread, not the timer, and do not count. */
    static final long MAX_COUNTED_OVERSHOOT_NANOS = 20_000_000L;
    /** The estimate loses 1/{@code DECAY} of its excess over a new, smaller overshoot per park. */
    static final int DECAY = 64;
    /** A wait that only spins loses 1/{@code RELAX} of the estimate's excess over the initial value. */
    static final int RELAX = 256;
    /** The slowest that drift gets, after parks kept finding the timer as coarse as before. */
    static final int MAX_RELAX = 1 << 16;

    private static final Clock SYSTEM = new Clock() {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public void park(long nanos) {
            LockSupport.parkNanos(nanos);
        }

        @Override
        public void spin() {
            Thread.onSpinWait();
        }
    };

    private final Clock clock;
    private long overshoot = INITIAL_OVERSHOOT_NANOS;
    private int relax = RELAX;
    /** Whether a wait that only spun has lowered the estimate since the last park. */
    private boolean relaxed;

    private long reportWaits;
    private long reportWaitNanos;
    private long reportMaxLateNanos;

    public PreciseFrameWait() {
        this(SYSTEM);
    }

    /** The real clock: {@link System#nanoTime}, {@link LockSupport#parkNanos} and {@link Thread#onSpinWait}. */
    public static Clock systemClock() {
        return SYSTEM;
    }

    PreciseFrameWait(Clock clock) {
        this.clock = clock;
    }

    /** Returns at {@code deadline} by {@link Clock#nanoTime}, or at once if it has passed. */
    public void until(long deadline) {
        long start = clock.nanoTime();
        long now = start;
        if (now >= deadline) {
            return;
        }
        boolean parked = false;
        while (deadline - now > overshoot + SPIN_MARGIN_NANOS) {
            long request = deadline - now - overshoot - SPIN_MARGIN_NANOS;
            clock.park(request);
            long woke = clock.nanoTime();
            calibrate(woke - now - request);
            now = woke;
            parked = true;
        }
        if (!parked) {
            relax();
        }
        while (now < deadline) {
            clock.spin();
            now = clock.nanoTime();
        }
        reportWaits++;
        reportWaitNanos += now - start;
        reportMaxLateNanos = Math.max(reportMaxLateNanos, now - deadline);
    }

    private void calibrate(long overshot) {
        boolean afterRelaxing = relaxed;
        relaxed = false;
        if (overshot < 0 || overshot > MAX_COUNTED_OVERSHOOT_NANOS) {
            return;
        }
        if (overshot >= overshoot) {
            overshoot = overshot;
            if (afterRelaxing) {
                // The timer is as coarse as it was before the estimate drifted: ask half as often.
                relax = Math.min(relax * 2, MAX_RELAX);
            }
        } else {
            overshoot -= (overshoot - overshot) / DECAY;
            relax = RELAX;
        }
    }

    /** Lowers the estimate after a wait that only spun, which measured nothing. */
    private void relax() {
        if (overshoot > INITIAL_OVERSHOOT_NANOS) {
            overshoot -= Math.max(1L, (overshoot - INITIAL_OVERSHOOT_NANOS) / relax);
            relaxed = true;
        }
    }

    /** The overshoot a park is currently assumed to have. */
    public long overshootEstimate() {
        return overshoot;
    }

    public long reportWaits() {
        return reportWaits;
    }

    public long reportWaitNanos() {
        return reportWaitNanos;
    }

    /** The latest any wait returned after its deadline. */
    public long reportMaxLateNanos() {
        return reportMaxLateNanos;
    }

    public void resetReport() {
        reportWaits = 0;
        reportWaitNanos = 0;
        reportMaxLateNanos = 0;
    }
}
