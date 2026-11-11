/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPbrTest {

    private static AutoPbr.Surface surface(String name) {
        return AutoPbr.surface(new Identifier("block/" + name));
    }

    @Test
    void metalBlocksReflectAsMetalAndGemsAreGlossy() {
        assertTrue(surface("gold_block").metal());
        assertTrue(surface("iron_block").metal());
        assertTrue(surface("iron_block").roughness() <= 0.16f);
        assertTrue(surface("iron_block").normalStrength() <= 0.2f);
        assertTrue(surface("netherite_block").metal());
        assertTrue(surface("copper_block").metal());
        assertTrue(surface("gold_block").roughness() < 0.3f);

        assertFalse(surface("diamond_block").metal());
        assertTrue(surface("diamond_block").roughness() < 0.1f);
        assertTrue(surface("ice").roughness() < 0.1f);

        assertFalse(surface("oxidized_copper").metal(), "patina is not bare metal");
        assertTrue(surface("exposed_cut_copper").metal());
        assertTrue(surface("stone").roughness() > 0.7f);
        assertNull(surface("water_still"), "animated liquids are left alone");
    }

    @Test
    void theNormalTiltsAwayFromDarkerTexels() {
        int w = 4;
        int h = 1;
        byte[] albedo = new byte[w * h * 4];
        int[] lum = {0, 128, 255, 128};
        for (int x = 0; x < w; x++) {
            albedo[x * 4] = (byte) lum[x];
            albedo[x * 4 + 1] = (byte) lum[x];
            albedo[x * 4 + 2] = (byte) lum[x];
            albedo[x * 4 + 3] = (byte) 255;
        }
        byte[] normal = new byte[w * h * 4];
        AutoPbr.writeNormal(albedo, normal, w, 0, 0, w, h, 1.0f);
        // Texel 1 rises toward its brighter right neighbour, so its normal leans left (-x).
        assertTrue((normal[4] & 0xFF) < 128, "x " + (normal[4] & 0xFF));
        assertTrue((normal[6] & 0xFF) > 150, "still facing out");
        // Texel 2 is a peak: flat.
        assertEquals(128, normal[8] & 0xFF, 1);
    }

    @Test
    void materialWritesMetalnessAndDarkerTexelsAreRougher() {
        byte[] albedo = new byte[2 * 4];
        albedo[0] = albedo[1] = albedo[2] = (byte) 60;
        albedo[4] = albedo[5] = albedo[6] = (byte) 220;
        albedo[3] = albedo[7] = (byte) 255;
        byte[] roughness = new byte[2];
        byte[] metallic = new byte[2];
        AutoPbr.writeMaterial(albedo, roughness, metallic, 2, 0, 0, 2, 1, surface("iron_block"));
        assertEquals(255, metallic[0] & 0xFF);
        assertTrue((roughness[0] & 0xFF) > (roughness[1] & 0xFF));
    }
}
