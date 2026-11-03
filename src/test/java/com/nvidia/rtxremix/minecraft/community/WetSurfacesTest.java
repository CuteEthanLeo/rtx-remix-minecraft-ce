/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WetSurfacesTest {

    @AfterEach
    void restoreDefaults() {
        WetSurfaces.setEnabled(true);
    }

    @Test
    void groundWetsInTwentySecondsAndDriesOverNinety() {
        WetSurfaces wet = new WetSurfaces();
        for (int i = 0; i < 10; i++) wet.advance(1.0f, 1.0f);
        assertEquals(0.5f, wet.wetness(), 1.0e-4f);
        for (int i = 0; i < 10; i++) wet.advance(1.0f, 1.0f);
        assertEquals(WetSurfaces.LEVELS, wet.advance(1.0f, 0.0f));

        for (int i = 0; i < 45; i++) wet.advance(0.0f, 1.0f);
        assertEquals(0.5f, wet.wetness(), 1.0e-4f);
    }

    @Test
    void switchingWetGroundOffDriesAtOnce() {
        WetSurfaces wet = new WetSurfaces();
        for (int i = 0; i < 20; i++) wet.advance(1.0f, 1.0f);
        WetSurfaces.setEnabled(false);
        assertEquals(0, wet.advance(1.0f, 0.016f));
        assertEquals(0.0f, wet.wetness());
    }

    @Test
    void wetKeysRoundTrip() {
        String wet = WetSurfaces.wetKey("solid_sss");
        assertTrue(WetSurfaces.isWetKey(wet));
        assertEquals("solid_sss", WetSurfaces.dryKey(wet));
        assertFalse(WetSurfaces.isWetKey("solid"));
        assertEquals("solid", WetSurfaces.dryKey("solid"));
    }

    @Test
    void porousTexelsDarkenMoreAndDenseOnesGainMoreSheen() {
        byte[] albedo = {(byte) 200, (byte) 200, (byte) 200, (byte) 255, (byte) 200, (byte) 200, (byte) 200, (byte) 255};
        byte[] roughness = {(byte) 200, (byte) 200};
        byte[] porosity = {(byte) 255, 0};
        byte[] wetAlbedo = new byte[8];
        byte[] wetRoughness = new byte[2];
        WetSurfaces.wetAtlas(albedo, roughness, porosity, WetSurfaces.LEVELS, wetAlbedo, wetRoughness);

        int porousAlbedo = wetAlbedo[0] & 0xFF;
        int denseAlbedo = wetAlbedo[4] & 0xFF;
        assertTrue(porousAlbedo < denseAlbedo, "porous darkens more");
        assertEquals(255, wetAlbedo[3] & 0xFF, "alpha is kept for cutouts");
        assertTrue((wetRoughness[1] & 0xFF) < (wetRoughness[0] & 0xFF), "dense gains more sheen");
        assertTrue((wetRoughness[1] & 0xFF) >= Math.round(0.14f * 255.0f), "never a mirror");

        WetSurfaces.wetAtlas(albedo, roughness, porosity, 0, wetAlbedo, wetRoughness);
        assertEquals(200, wetAlbedo[0] & 0xFF, "level 0 is the dry atlas");
        assertEquals(200, wetRoughness[0] & 0xFF);
    }

    @Test
    void labPbrPorosityComesFromTheLowBlueRange() {
        assertEquals(0, WetSurfaces.porosityFromSpecularBlue(0));
        assertEquals(255, WetSurfaces.porosityFromSpecularBlue(64));
        assertEquals(64, WetSurfaces.porosityFromSpecularBlue(200), "subsurface texels count as fairly dense");
    }
}
