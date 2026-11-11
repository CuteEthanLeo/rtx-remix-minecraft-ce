/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.pacing;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds real frames to a steady interval while DLSS frame generation is on; see
 * {@link FrameGenPacer} for why and how the interval is chosen.
 *
 * <p>The wait runs right after Present returns, where vanilla's own frame limiter runs, so it
 * delays the start of the next frame, input sampling included. While pacing is engaged it also
 * takes over the player's Max Framerate: vanilla's limiter is skipped
 * ({@link #shouldRunVanillaLimiter}) and its cap becomes the pacer's floor, so a capped frame
 * rate gets the same precise wait. Disengaged, everything is vanilla's again.
 *
 * <p>Engaged only while the caller reports frame generation on and the window focused, the
 * player's setting is on, and {@code -Drtxremix.frameGenPacing=false} is not set. Render thread
 * only; allocates nothing per frame.
 */
public final class FrameGenPacing {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrameGenPacing.class);

    /** The kill switch: {@code -Drtxremix.frameGenPacing=false} keeps pacing off. */
    public static final String PROPERTY = "rtxremix.frameGenPacing";
    public static final boolean DEFAULT_ENABLED = true;
    /** A wake later than this after its deadline restarts the cadence from the wake. */
    static final long LATE_WAKE_NANOS = 1_000_000L;

    private static final boolean ALLOWED = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));
    private static boolean enabled = DEFAULT_ENABLED;

    private final FrameGenPacer pacer = new FrameGenPacer();
    private final PreciseFrameWait wait;
    private final PreciseFrameWait.Clock clock;

    private boolean engaged;
    private long frameStart;
    /** The interval of the cap vanilla's limiter was asked for last frame, or 0. */
    private long userCapNanos;
    private boolean engagedLogged;

    public FrameGenPacing() {
        this(PreciseFrameWait.systemClock());
    }

    FrameGenPacing(PreciseFrameWait.Clock clock) {
        this.clock = clock;
        this.wait = new PreciseFrameWait(clock);
    }

    /** The player's setting. */
    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Whether the kill switch leaves pacing available at all. */
    public static boolean isAllowed() {
        return ALLOWED;
    }

    /**
     * Called once per frame right after Present returns. Records the frame and, while engaged,
     * waits until the next frame should start.
     *
     * @param frameGenerationActive whether frame generation is on, the window focused, and no
     *                              benchmark measures raw throughput
     */
    public void afterPresent(boolean frameGenerationActive) {
        long now = clock.nanoTime();
        boolean engage = frameGenerationActive && enabled && ALLOWED;
        if (!engage) {
            if (engaged) {
                LOGGER.info("Frame generation pacing released; Max Framerate is vanilla's again");
            }
            engaged = false;
            frameStart = now;
            userCapNanos = 0;
            return;
        }
        if (!engaged) {
            engaged = true;
            pacer.reset();
            frameStart = now;
            userCapNanos = 0;
            if (!engagedLogged) {
                engagedLogged = true;
                LOGGER.info("Frame generation pacing engaged: real frames are held to a steady interval"
                        + " so generated frames land evenly (-D{}=false or the Framerate setting turns it off)",
                        PROPERTY);
            }
            return;
        }
        long interval = pacer.onFrame(now - frameStart, userCapNanos);
        userCapNanos = 0;
        long deadline = frameStart + interval;
        if (interval <= 0 || deadline <= now) {
            frameStart = now;
            return;
        }
        wait.until(deadline);
        long woke = clock.nanoTime();
        frameStart = woke - deadline > LATE_WAKE_NANOS ? woke : deadline;
    }

    /**
     * Called where vanilla would apply its Max Framerate. Records the cap for the pacer and
     * returns whether vanilla's limiter should still run, which it does unless pacing is engaged.
     *
     * @param framerateLimit the frame rate vanilla is about to limit to
     */
    public boolean shouldRunVanillaLimiter(int framerateLimit) {
        userCapNanos = framerateLimit > 0 ? 1_000_000_000L / framerateLimit : 0L;
        return !engaged;
    }

    /** Forgets the cadence, after anything that makes the next frames unlike the last ones. */
    public void reset() {
        pacer.reset();
        frameStart = clock.nanoTime();
    }

    public boolean isEngaged() {
        return engaged;
    }

    /** One line for the periodic performance report; resets its counters. */
    public void appendReport(StringBuilder sb) {
        if (!engaged && pacer.reportFrames() == 0) {
            return;
        }
        long frames = pacer.reportFrames();
        long waits = wait.reportWaits();
        sb.append(String.format(Locale.ROOT,
                "  Frame generation pacing: %s, target %.2f ms, missed %d/%d frames, paced %d,"
                        + " mean wait %.2f ms, latest wake +%.3f ms, park overshoot %.2f ms%n",
                engaged ? "engaged" : "released",
                pacer.target() / 1.0e6,
                pacer.reportMisses(), frames, pacer.reportPaced(),
                waits == 0 ? 0.0 : wait.reportWaitNanos() / 1.0e6 / waits,
                wait.reportMaxLateNanos() / 1.0e6,
                wait.overshootEstimate() / 1.0e6));
        pacer.resetReport();
        wait.resetReport();
    }
}
