/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nvidia.rtxremix.minecraft.community.perf.PresentWaitPolicy.Mode;
import org.junit.jupiter.api.Test;

class PresentWaitPolicyTest {

    private static final int GENERATION = 7;

    /** A steady gameplay frame with the technique on. */
    private static Mode steady(PresentWaitPolicy policy) {
        return policy.decide(true, false, GENERATION, true, false, false);
    }

    private static void reachPipelining(PresentWaitPolicy policy) {
        for (int i = 0; i < PresentWaitPolicy.SETTLE_FRAMES; i++) {
            assertEquals(Mode.SETTLING, steady(policy), "frame " + i);
        }
        assertEquals(Mode.PIPELINED, steady(policy));
    }

    private static void assertSettlesBackToPipelining(PresentWaitPolicy policy) {
        reachPipelining(policy);
        assertTrue(policy.lastPresentPipelined());
    }

    @Test
    void settlesBeforeTheFirstPipelinedPresent() {
        PresentWaitPolicy policy = new PresentWaitPolicy();
        assertFalse(policy.lastPresentPipelined());

        reachPipelining(policy);
        assertEquals(Mode.PIPELINED, steady(policy));
    }

    @Test
    void everyBlockingStateBlocksAndSettlesOnceItEnds() {
        PresentWaitPolicy policy = new PresentWaitPolicy();
        reachPipelining(policy);

        assertEquals(Mode.SCREEN_OPEN, policy.decide(true, false, GENERATION, true, false, true));
        assertSettlesBackToPipelining(policy);

        assertEquals(Mode.REMIX_MENU, policy.decide(true, false, GENERATION, true, true, false));
        assertSettlesBackToPipelining(policy);

        assertEquals(Mode.NO_WORLD, policy.decide(true, false, GENERATION, false, false, false));
        assertSettlesBackToPipelining(policy);

        assertEquals(Mode.OFF, policy.decide(false, false, GENERATION, true, false, false));
        assertSettlesBackToPipelining(policy);

        assertEquals(Mode.VETOED, policy.decide(false, true, GENERATION, true, false, false));
    }

    @Test
    void aLongBlockStillSettlesOnlySettleFrames() {
        PresentWaitPolicy policy = new PresentWaitPolicy();
        reachPipelining(policy);
        for (int i = 0; i < 100; i++) {
            assertEquals(Mode.SCREEN_OPEN, policy.decide(true, false, GENERATION, true, false, true));
        }
        assertSettlesBackToPipelining(policy);
    }

    @Test
    void blockingReasonsTakeTheirPrecedence() {
        PresentWaitPolicy policy = new PresentWaitPolicy();
        assertEquals(Mode.OFF, policy.decide(false, false, GENERATION, false, true, true));
        assertEquals(Mode.NO_WORLD, policy.decide(true, false, GENERATION, false, true, true));
        assertEquals(Mode.REMIX_MENU, policy.decide(true, false, GENERATION, true, true, true));
        assertEquals(Mode.SCREEN_OPEN, policy.decide(true, false, GENERATION, true, false, true));
    }

    @Test
    void aDiscontinuityOrAStateChangeSettles() {
        PresentWaitPolicy policy = new PresentWaitPolicy();
        reachPipelining(policy);

        policy.discontinuity();
        assertSettlesBackToPipelining(policy);

        assertEquals(Mode.SETTLING, policy.decide(true, false, GENERATION + 1, true, false, false));
        for (int i = 1; i < PresentWaitPolicy.SETTLE_FRAMES; i++) {
            assertEquals(Mode.SETTLING, policy.decide(true, false, GENERATION + 1, true, false, false));
        }
        assertEquals(Mode.PIPELINED, policy.decide(true, false, GENERATION + 1, true, false, false));
    }

    @Test
    void clusteredFailuresAskForAVetoButSpreadOnesDoNot() {
        PresentWaitPolicy spread = new PresentWaitPolicy();
        for (int strike = 0; strike < 5; strike++) {
            assertFalse(spread.failure());
            for (long frame = 0; frame < PresentWaitPolicy.STRIKE_WINDOW_FRAMES; frame++) {
                steady(spread);
            }
        }

        PresentWaitPolicy clustered = new PresentWaitPolicy();
        assertFalse(clustered.failure());
        steady(clustered);
        assertFalse(clustered.failure());
        steady(clustered);
        assertTrue(clustered.failure());
    }

    @Test
    void aFailureSettles() {
        PresentWaitPolicy policy = new PresentWaitPolicy();
        reachPipelining(policy);
        policy.failure();
        assertSettlesBackToPipelining(policy);
    }

    @Test
    void countsEveryPresentByModeUntilReset() {
        PresentWaitPolicy policy = new PresentWaitPolicy();
        reachPipelining(policy);
        policy.decide(true, false, GENERATION, true, false, true);

        assertEquals(PresentWaitPolicy.SETTLE_FRAMES, policy.count(Mode.SETTLING));
        assertEquals(1, policy.count(Mode.PIPELINED));
        assertEquals(1, policy.count(Mode.SCREEN_OPEN));
        assertEquals(PresentWaitPolicy.SETTLE_FRAMES + 2, policy.totalCount());

        policy.resetCounts();
        assertEquals(0, policy.totalCount());
        assertEquals(Mode.SCREEN_OPEN, policy.lastMode(), "resetting counts keeps the state");
    }
}
