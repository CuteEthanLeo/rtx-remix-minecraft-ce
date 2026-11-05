/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkyVisibilityTest {
    @Test
    void gradualChangesAccumulateAgainstTheUploadedMesh() {
        SkyVisibility visibility = new SkyVisibility();
        visibility.reset(2);
        visibility.values()[0] = 0.96f;
        assertFalse(visibility.changed(0.08f));
        visibility.values()[0] = 0.93f;
        assertFalse(visibility.changed(0.08f));
        visibility.values()[0] = 0.89f;
        assertTrue(visibility.changed(0.08f));
        assertTrue(visibility.changed(0.08f), "an upload failure must not advance the comparison baseline");
        visibility.publish();
        assertFalse(visibility.changed(0.08f));
        visibility.values()[0] = 0.85f;
        assertFalse(visibility.changed(0.08f));
        visibility.values()[0] = 0.80f;
        assertTrue(visibility.changed(0.08f));
    }

    @Test
    void clearingAWorldRestoresEveryStarAndBandVertex() {
        SkyVisibility visibility = new SkyVisibility();
        visibility.reset(3);
        visibility.values()[2] = 0;
        visibility.publish();
        visibility.reset(3);
        assertArrayEquals(new float[]{1, 1, 1}, visibility.values());
        assertFalse(visibility.changed(0.08f));
    }
}
