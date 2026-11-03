/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuddlesTest {

    @Test
    void puddlesAppearWithWetnessAndGrowWithIt() {
        float[] depth = Puddles.depthField(3L);
        byte[] rgba = new byte[depth.length * 4];

        Puddles.mask(depth, 0, rgba);
        assertEquals(0.0, coverage(rgba), 0.0, "dry ground holds no water");

        Puddles.mask(depth, WetSurfaces.LEVELS / 2, rgba);
        double half = coverage(rgba);
        Puddles.mask(depth, WetSurfaces.LEVELS, rgba);
        double full = coverage(rgba);
        assertTrue(half > 0.0 && half < full, "puddles spread as the rain goes on");
        assertTrue(full > 0.08 && full < 0.5, "full wetness pools part of the ground, was " + full);
    }

    @Test
    void theDepthFieldTilesInBothDirections() {
        float[] depth = Puddles.depthField(3L);
        int size = Puddles.SIZE;
        double seam = 0.0;
        for (int i = 0; i < size; i++) {
            seam = Math.max(seam, Math.abs(depth[i] - depth[(size - 1) * size + i]));
            seam = Math.max(seam, Math.abs(depth[i * size] - depth[i * size + size - 1]));
        }
        assertTrue(seam < 0.05, "opposite edges meet, worst step " + seam);
    }

    @Test
    void onlyHardGroundPools() {
        assertTrue(Puddles.pools("block/dirt_path_top"));
        assertTrue(Puddles.pools("block/stone"));
        assertFalse(Puddles.pools("block/grass_block_top"));
        assertFalse(Puddles.pools("block/sand"));
        assertFalse(Puddles.pools(null));
    }

    private static double coverage(byte[] rgba) {
        int covered = 0;
        for (int i = 3; i < rgba.length; i += 4) {
            if ((rgba[i] & 0xFF) > 128) covered++;
        }
        return covered / (rgba.length / 4.0);
    }
}
