/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarFieldTest {

    @Test
    void theFieldIsDeterministicAndOfUnitDirections() {
        List<StarField.Star> first = StarField.generate(StarField.SEED, 500);
        List<StarField.Star> second = StarField.generate(StarField.SEED, 500);
        assertEquals(first, second);
        for (StarField.Star star : first) {
            float length = (float) Math.sqrt(star.x() * star.x() + star.y() * star.y() + star.z() * star.z());
            assertEquals(1.0f, length, 1.0e-4f);
        }
    }

    @Test
    void faintStarsDominateAndCrowdTowardTheGalacticPlane() {
        List<StarField.Star> stars = StarField.generate(StarField.SEED, StarField.STAR_COUNT);
        long bright = stars.stream().filter(s -> s.tier() == 2).count();
        long dim = stars.stream().filter(s -> s.tier() == 0).count();
        assertTrue(bright < stars.size() * 0.06, "bright stars are rare");
        assertTrue(dim > stars.size() * 0.7, "most stars are dim");

        float[] n = StarField.GALACTIC_NORMAL;
        double nearPlane = stars.stream()
                .filter(s -> Math.abs(s.x() * n[0] + s.y() * n[1] + s.z() * n[2]) < Math.sin(Math.toRadians(10)))
                .count() / (double) stars.size();
        // A uniform sky puts about 17% of stars within 10 degrees of a great circle.
        assertTrue(nearPlane > 0.3, "stars concentrate along the band, was " + nearPlane);
    }

    @Test
    void aStarQuadSitsAtTheSkyRadiusFacingTheOrigin() {
        StarField.Star star = new StarField.Star(0, 1, 0, 0.2f, 2, 1);
        float[] corners = new float[12];
        StarField.quadCorners(star, corners, 0);
        for (int corner = 0; corner < 4; corner++) {
            assertEquals(StarField.RADIUS, corners[corner * 3 + 1], 1.0e-4f, "quad lies in the tangent plane");
        }
    }

    @Test
    void theMilkyWayTextureIsBrightestAtItsCentreAndWrapsAlongTheBand() {
        byte[] band = NightSkyTextures.milkyWay(StarField.SEED);
        int w = NightSkyTextures.BAND_WIDTH;
        int h = NightSkyTextures.BAND_HEIGHT;
        int centre = alpha(band, w / 2, h / 2, w) + alpha(band, w / 2 + 40, h / 2 - 20, w);
        int far = alpha(band, 10, h / 2, w) + alpha(band, 50, h / 2 - 20, w);
        assertTrue(centre > far, "galactic centre outshines the anticentre");
        assertEquals(0, alpha(band, w / 2, 0, w) / 64, "the band fades out at its edges");
        assertEquals(NightSkyTextures.fbm(1L, 0.0f, 3.0f, 4), NightSkyTextures.fbm(1L, 48.0f, 3.0f, 4), 1.0e-5f);
    }

    private static int alpha(byte[] pixels, int x, int y, int width) {
        return pixels[(y * width + x) * 4 + 3] & 0xFF;
    }
}
