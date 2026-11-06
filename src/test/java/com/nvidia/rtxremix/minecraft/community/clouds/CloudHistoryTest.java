/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.clouds;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudHistoryTest {
    private static CloudSkyCompositor.Inputs frame(double x, double day, boolean physical, float fill) {
        return new CloudSkyCompositor.Inputs(new float[]{0, 1, 0}, new float[]{0, -1, 0},
                new float[]{1, 1, 1}, new float[]{0, 0, 0}, 0.0046f, 0.0046f,
                x, 64, 0, day, physical, fill);
    }

    @Test
    void ordinaryMotionAndTimeKeepHistoryUntilARealDiscontinuity() {
        CloudHistory history = new CloudHistory();
        var initial = frame(0, 1000, true, 1);
        assertTrue(history.needsReset(initial, 100, 400));
        history.record(initial, 100, 400);
        assertFalse(history.needsReset(frame(0.5, 1002, true, 1), 102, 400));
        assertFalse(history.isMoving(frame(0.5, 1002, true, 1)));
        assertTrue(history.isMoving(frame(1, 1002, true, 1)));
        assertFalse(history.needsReset(frame(1, 1002, true, 1), 102, 400));
        assertTrue(history.needsReset(frame(33, 1002, true, 1), 102, 400));
    }

    @Test
    void timeCommandsResetEvenWhenTotalGameTimeKeepsAdvancing() {
        CloudHistory history = new CloudHistory();
        history.record(frame(0, 6000, true, 1), 100, 400);
        assertTrue(history.needsReset(frame(0, 18000, true, 1), 102, 400));
        assertTrue(history.needsReset(frame(0, 1000, true, 1), 102, 400));
        assertTrue(history.needsReset(frame(0, 6002, true, 1), 99, 400));
        assertFalse(history.needsReset(frame(0, 6000, true, 1), 102, 400),
                "a frozen daylight cycle is a valid steady sky");
    }

    @Test
    void aFrozenDaylightCycleKeepsHistoryWhateverTheTickDelta() {
        // The sky passes getTimeOfDay() + tickDelta; with the cycle frozen only the tick delta
        // moves, so successive updates can land on an earlier fraction of the same tick.
        CloudHistory history = new CloudHistory();
        history.record(frame(0, 6000.9, true, 1), 100.9, 400);
        assertFalse(history.needsReset(frame(0, 6000.05, true, 1), 102.05, 400));
        history.record(frame(0, 6000.05, true, 1), 102.05, 400);
        assertFalse(history.needsReset(frame(0, 6000.7, true, 1), 104.7, 400));
        assertTrue(history.needsReset(frame(0, 5999.0, true, 1), 104.0, 400),
                "a whole tick back is a time command");
    }

    @Test
    void skyModeSunDirectionAndWorldInvalidationResetHistory() {
        CloudHistory history = new CloudHistory();
        var initial = frame(0, 1000, true, 1);
        history.record(initial, 100, 400);
        assertTrue(history.needsReset(frame(0, 1002, false, 1), 102, 400));
        assertTrue(history.needsReset(frame(0, 1002, true, 2), 102, 400));
        assertTrue(history.needsReset(initial, 100, 450));
        initial.sunDirection()[0] = 1;
        initial.sunDirection()[1] = 0;
        assertTrue(history.needsReset(initial, 100, 400));
        history.invalidate();
        assertTrue(history.needsReset(frame(0, 1000, true, 1), 100, 400));
    }
}
