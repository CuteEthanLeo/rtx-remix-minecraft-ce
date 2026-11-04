/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundSunTest {

    @Test
    void theDiscDarkensTowardItsLimbAndTheGlowFadesOut() {
        float pixel = 0.05f;
        assertEquals(1.0f, RoundSun.profile(0.0f, pixel), 1.0e-6f);
        float nearLimb = RoundSun.profile(0.95f, pixel);
        assertTrue(nearLimb > 1.0f - RoundSun.LIMB_DARKENING && nearLimb < RoundSun.profile(0.5f, pixel));
        assertTrue(RoundSun.profile(1.5f, pixel) < RoundSun.GLOW_PEAK);
        assertEquals(0.0f, RoundSun.profile(1.0f / RoundSun.DISC_FRACTION, pixel), 1.0e-6f);
    }

    @Test
    void theTextureIsRoundTintedAndTransparentInItsCorners() {
        int size = RoundSun.TEXTURE_SIZE;
        ByteBuffer pixels = ByteBuffer.allocate(size * size * 4);
        RoundSun.fillTexture(pixels, 1.0f, 0.5f, 0.1f);
        assertEquals(size * size * 4, pixels.limit());

        int centre = ((size / 2) * size + size / 2) * 4;
        int red = pixels.get(centre) & 0xFF;
        int green = pixels.get(centre + 1) & 0xFF;
        int blue = pixels.get(centre + 2) & 0xFF;
        assertTrue(red > green && green > blue, "tinted orange: " + red + "," + green + "," + blue);
        assertEquals(0xFF, pixels.get(centre + 3) & 0xFF);
        assertEquals(0, pixels.get(3) & 0xFF, "corner is transparent");
    }

    @Test
    void onlyAVisibleColourChangeReuploads() {
        float[] uploaded = {1.0f, 0.9f, 0.8f};
        assertFalse(RoundSun.colorChanged(uploaded, 1.0f, 0.905f, 0.8f));
        assertTrue(RoundSun.colorChanged(uploaded, 1.0f, 0.9f, 0.78f));
    }

    @Test
    void moonlightFollowsThePhase() {
        assertEquals(1.0f, NightLight.moonPhaseScale(1.0f), 1.0e-6f);
        assertEquals(NightLight.NEW_MOON_LIGHT, NightLight.moonPhaseScale(0.0f), 1.0e-6f);
        assertTrue(NightLight.moonPhaseScale(0.5f) > NightLight.moonPhaseScale(0.25f));
    }
}
