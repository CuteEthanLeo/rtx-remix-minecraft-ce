/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.pacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameGenPacerTest {

    private static final long MS = 1_000_000L;

    /** Feeds {@code frames} frames of {@code busy} and returns the last interval. */
    private static long feed(FrameGenPacer pacer, int frames, long busy, long cap) {
        long interval = 0;
        for (int i = 0; i < frames; i++) {
            interval = pacer.onFrame(busy, cap);
        }
        return interval;
    }

    @Test
    void doesNotPaceBeforeAFullWindow() {
        FrameGenPacer pacer = new FrameGenPacer();
        assertEquals(0L, feed(pacer, FrameGenPacer.WINDOW - 1, 10 * MS, 0));
        assertEquals(0L, pacer.target());
    }

    @Test
    void honoursThePlayersCapBeforeAFullWindow() {
        FrameGenPacer pacer = new FrameGenPacer();
        assertEquals(8 * MS, pacer.onFrame(5 * MS, 8 * MS));
    }

    @Test
    void settlesJustAboveASteadyFrame() {
        FrameGenPacer pacer = new FrameGenPacer();
        long interval = feed(pacer, FrameGenPacer.WINDOW + FrameGenPacer.RECOMPUTE_FRAMES, 10 * MS, 0);
        assertEquals((long) (10 * MS * FrameGenPacer.SETTLE_MARGIN), interval);
    }

    @Test
    void settlesOnTheSlowFramesOfAJitteryLoad() {
        FrameGenPacer pacer = new FrameGenPacer();
        long interval = 0;
        // Alternating 8 ms and 12 ms frames: the paced interval must cover the slow ones.
        for (int i = 0; i < FrameGenPacer.WINDOW * 2; i++) {
            interval = pacer.onFrame(i % 2 == 0 ? 8 * MS : 12 * MS, 0);
        }
        assertTrue(interval >= 12 * MS, "interval " + interval);
        assertTrue(interval <= 13 * MS, "interval " + interval);
    }

    @Test
    void occasionalSpikesDoNotRaiseTheTarget() {
        FrameGenPacer pacer = new FrameGenPacer();
        feed(pacer, FrameGenPacer.WINDOW + FrameGenPacer.RECOMPUTE_FRAMES, 10 * MS, 0);
        long settled = pacer.target();
        for (int i = 0; i < FrameGenPacer.WINDOW * 4; i++) {
            // One 30 ms stall every 40 frames, 2.5% of frames.
            pacer.onFrame(i % 40 == 0 ? 30 * MS : 10 * MS, 0);
        }
        assertEquals(settled, pacer.target());
    }

    @Test
    void risesWhenFramesKeepMissing() {
        FrameGenPacer pacer = new FrameGenPacer();
        feed(pacer, FrameGenPacer.WINDOW + FrameGenPacer.RECOMPUTE_FRAMES, 10 * MS, 0);
        long interval = feed(pacer, FrameGenPacer.WINDOW, 14 * MS, 0);
        assertTrue(interval >= 14 * MS, "interval " + interval);
        assertTrue(pacer.reportMisses() > 0);
    }

    @Test
    void aWaitThatFillsTheIntervalDoesNotPushTheTargetUp() {
        FrameGenPacer pacer = new FrameGenPacer();
        feed(pacer, FrameGenPacer.WINDOW + FrameGenPacer.RECOMPUTE_FRAMES, 10 * MS, 0);
        long settled = pacer.target();
        // Like a Reflex sleep inside Present: every frame takes exactly the paced interval.
        for (int i = 0; i < FrameGenPacer.WINDOW * 10; i++) {
            pacer.onFrame(pacer.target(), 0);
        }
        assertEquals(settled, pacer.target());
    }

    @Test
    void fallsSlowlyOnceThereIsHeadroom() {
        FrameGenPacer pacer = new FrameGenPacer();
        feed(pacer, FrameGenPacer.WINDOW + FrameGenPacer.RECOMPUTE_FRAMES, 20 * MS, 0);
        long high = pacer.target();

        feed(pacer, FrameGenPacer.LOWER_AFTER_FRAMES / 2, 10 * MS, 0);
        assertEquals(high, pacer.target(), "no fall before the hold time");

        feed(pacer, FrameGenPacer.LOWER_AFTER_FRAMES, 10 * MS, 0);
        long once = pacer.target();
        assertTrue(once < high);
        assertTrue(once >= (long) (high * FrameGenPacer.LOWER_STEP), "one step at most per fall");

        feed(pacer, FrameGenPacer.LOWER_AFTER_FRAMES * 30, 10 * MS, 0);
        assertEquals((long) (10 * MS * FrameGenPacer.SETTLE_MARGIN), pacer.target());
    }

    @Test
    void neverPacesFasterThanThePlayersCap() {
        FrameGenPacer pacer = new FrameGenPacer();
        long interval = feed(pacer, FrameGenPacer.WINDOW * 2, 4 * MS, 10 * MS);
        assertEquals(10 * MS, interval);
    }

    @Test
    void aCapNeverEntersTheTarget() {
        FrameGenPacer pacer = new FrameGenPacer();
        long capped = feed(pacer, FrameGenPacer.WINDOW * 4, 9 * MS, 33 * MS);
        assertEquals(33 * MS, capped, "the cap holds the frames");
        long settled = (long) (9 * MS * FrameGenPacer.SETTLE_MARGIN);
        assertEquals(settled, pacer.target());
        assertEquals(settled, pacer.onFrame(9 * MS, 0), "the first frame without the cap is paced by the frames alone");
    }

    @Test
    void aCapThatComesAndGoesDoesNotSlowTheFramesAfterIt() {
        FrameGenPacer pacer = new FrameGenPacer();
        feed(pacer, FrameGenPacer.WINDOW + FrameGenPacer.RECOMPUTE_FRAMES, 10 * MS, 0);
        long settled = pacer.target();
        // Vanilla's idle limiter: 30 fps after a minute without input, and the lighter load
        // shortens the frames, which is what lets the target move at all.
        feed(pacer, FrameGenPacer.WINDOW * 4, 9 * MS, 33 * MS);
        long interval = pacer.onFrame(9 * MS, 0);
        assertTrue(interval <= settled, "interval " + interval + " after the cap lifted, " + settled + " before it");
    }

    @Test
    void aFrameWithinItsCapIsNotReportedAsAMiss() {
        FrameGenPacer pacer = new FrameGenPacer();
        feed(pacer, FrameGenPacer.WINDOW * 2, 9 * MS, 33 * MS);
        pacer.resetReport();
        // Slower than the target, well inside the 33 ms the cap holds it to.
        pacer.onFrame(20 * MS, 33 * MS);
        assertEquals(0L, pacer.reportMisses());
    }

    @Test
    void releasesFramesSlowerThanTheLimit() {
        FrameGenPacer pacer = new FrameGenPacer();
        long interval = feed(pacer, FrameGenPacer.WINDOW * 2, 60 * MS, 0);
        assertEquals(0L, interval, "frame generation does not space such frames, so neither do we");
    }

    @Test
    void resetForgetsTheCadence() {
        FrameGenPacer pacer = new FrameGenPacer();
        feed(pacer, FrameGenPacer.WINDOW * 2, 10 * MS, 0);
        pacer.reset();
        assertEquals(0L, pacer.target());
        assertEquals(0L, pacer.onFrame(10 * MS, 0));
    }
}
