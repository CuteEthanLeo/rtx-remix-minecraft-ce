/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nvidia.rtxremix.InstanceCategoryBit;
import org.junit.jupiter.api.Test;

class ChunkManagerShownFlagsTest {

    private static final int DECAL = InstanceCategoryBit.DECAL_STATIC;
    private static final int HIDDEN = InstanceCategoryBit.HIDDEN;

    @Test
    void rainRipplesHideWhileItDoesNotRain() {
        assertEquals(DECAL | HIDDEN, ChunkManager.shownFlags(ChunkManager.CAT_RAIN, DECAL, false, true));
        assertEquals(DECAL, ChunkManager.shownFlags(ChunkManager.CAT_RAIN, DECAL, true, false));
    }

    @Test
    void puddleDecalsHideWhileTheyCannotShow() {
        assertEquals(DECAL | HIDDEN, ChunkManager.shownFlags(ChunkManager.CAT_PUDDLE, DECAL, true, false));
        assertEquals(DECAL, ChunkManager.shownFlags(ChunkManager.CAT_PUDDLE, DECAL, false, true));
    }

    @Test
    void everyOtherCategoryKeepsItsFlags() {
        for (int category = 0; category < ChunkManager.CAT_COUNT; category++) {
            if (category == ChunkManager.CAT_RAIN || category == ChunkManager.CAT_PUDDLE) {
                continue;
            }
            assertEquals(DECAL, ChunkManager.shownFlags(category, DECAL, false, false), "category " + category);
        }
    }
}
