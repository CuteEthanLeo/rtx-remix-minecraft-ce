/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.perf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SubmitOverlapGuardTest {

    private static final long HANDLE = 0x1234L;

    /** Feeds ordinary submits advancing by {@code step}; returns the last value. */
    private static long learn(SubmitOverlapGuard guard, long start, long step, int frames) {
        long value = start;
        for (int i = 0; i < frames; i++) {
            assertTrue(guard.record(false, value, HANDLE));
            value += step;
        }
        return value - step;
    }

    @Test
    void overlapWaitsForAStableStepFromOrdinarySubmits() {
        SubmitOverlapGuard guard = new SubmitOverlapGuard();
        learn(guard, 100, 1, SubmitOverlapGuard.LEARN_FRAMES);
        assertFalse(guard.ready(), "the first submit only sets the baseline");

        learn(guard, 100 + SubmitOverlapGuard.LEARN_FRAMES, 1, 1);
        assertTrue(guard.ready());
    }

    @Test
    void anIrregularStepRestartsLearning() {
        SubmitOverlapGuard guard = new SubmitOverlapGuard();
        long last = learn(guard, 10, 1, SubmitOverlapGuard.LEARN_FRAMES + 1);
        assertTrue(guard.ready());

        guard.record(false, last + 3, HANDLE);
        assertFalse(guard.ready());

        guard.record(false, last + 3, HANDLE);
        assertFalse(guard.ready(), "a submit that did not advance teaches nothing");
    }

    @Test
    void anOverlappedSubmitMustAdvanceByTheLearnedStep() {
        SubmitOverlapGuard guard = new SubmitOverlapGuard();
        long last = learn(guard, 10, 2, SubmitOverlapGuard.LEARN_FRAMES + 1);

        assertTrue(guard.record(true, last + 2, HANDLE));
        assertTrue(guard.record(true, last + 4, HANDLE));
        assertFalse(guard.record(true, last + 4, HANDLE), "stale interop info");
        assertFalse(guard.ready());
    }

    @Test
    void anOverlappedSubmitMustKeepAValidUnchangedImage() {
        SubmitOverlapGuard guard = new SubmitOverlapGuard();
        long last = learn(guard, 10, 1, SubmitOverlapGuard.LEARN_FRAMES + 1);
        assertFalse(guard.record(true, last + 1, 0L));

        guard = new SubmitOverlapGuard();
        last = learn(guard, 10, 1, SubmitOverlapGuard.LEARN_FRAMES + 1);
        assertFalse(guard.record(true, last + 1, HANDLE + 1));

        guard = new SubmitOverlapGuard();
        last = learn(guard, 10, 1, SubmitOverlapGuard.LEARN_FRAMES + 1);
        assertFalse(guard.record(true, last + 1, -1L));
    }

    @Test
    void anOverlapWithoutHistoryFailsAndResetForgets() {
        assertFalse(new SubmitOverlapGuard().record(true, 5, HANDLE));

        SubmitOverlapGuard guard = new SubmitOverlapGuard();
        learn(guard, 10, 1, SubmitOverlapGuard.LEARN_FRAMES + 1);
        guard.reset();
        assertFalse(guard.ready());
    }
}
