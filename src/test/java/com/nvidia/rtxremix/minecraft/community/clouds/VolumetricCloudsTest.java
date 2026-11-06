/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.clouds;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumetricCloudsTest {

    @AfterEach
    void reset() {
        VolumetricClouds.deactivate();
        VolumetricClouds.setQuality(VolumetricClouds.DEFAULT_QUALITY);
        VolumetricClouds.setCoverage(VolumetricClouds.DEFAULT_COVERAGE);
    }

    @Test
    void settingsClampToTheirRanges() {
        VolumetricClouds.setQuality(7);
        assertEquals(VolumetricClouds.QUALITY_ULTRA, VolumetricClouds.getQuality());
        VolumetricClouds.setCoverage(5.0f);
        assertEquals(VolumetricClouds.MAX_COVERAGE, VolumetricClouds.getCoverage());
    }

    @Test
    void qualityLevelsGrowMonotonically() {
        VolumetricClouds.Quality low = VolumetricClouds.quality(VolumetricClouds.QUALITY_LOW);
        VolumetricClouds.Quality high = VolumetricClouds.quality(VolumetricClouds.QUALITY_HIGH);
        VolumetricClouds.Quality ultra = VolumetricClouds.quality(VolumetricClouds.QUALITY_ULTRA);
        assertTrue(low.domeWidth() < high.domeWidth() && high.domeWidth() < ultra.domeWidth());
        assertTrue(low.primarySteps() < high.primarySteps() && high.primarySteps() < ultra.primarySteps());
        assertTrue(low.nearRadius() < high.nearRadius() && high.nearRadius() <= ultra.nearRadius());
        for (VolumetricClouds.Quality quality : new VolumetricClouds.Quality[]{low, high, ultra}) {
            // Tiles share their seam planes only when the mesh step divides the tile.
            assertEquals(0, VolumetricClouds.TILE_SIZE % quality.meshStep());
            assertEquals(0.0f, VolumetricClouds.NEAR_CAP_ABOVE_BASE % quality.meshStep());
        }
        // The near layer stays inside the camera's far plane (1000) with its cap.
        double reach = Math.hypot(ultra.nearRadius() + VolumetricClouds.TILE_SIZE,
                CloudField.BASE_ABOVE_VANILLA + VolumetricClouds.NEAR_CAP_ABOVE_BASE + 192.0 - 64.0);
        assertTrue(reach < 1000.0, "near reach " + reach);
        // The occupancy grid reaches past the near tiles and their keep margin.
        assertTrue(VolumetricClouds.OCCUPANCY_HALF * VolumetricClouds.TILE_SIZE
                > ultra.nearRadius() + 2 * VolumetricClouds.TILE_SIZE);
    }

    @Test
    void skyIsFullyVisibleWithoutClouds() {
        assertEquals(1.0f, VolumetricClouds.skyVisibility(0.3f, 0.8f, -0.2f));
        assertEquals(1.0f, VolumetricClouds.sunTransmittance());
        assertEquals(0.0f, VolumetricClouds.skyCover());
    }

    @Test
    void occlusionMapFollowsTheDomeDirections() {
        VolumetricClouds.publishFrame(CloudField.Params.of(260.0f, 0.45f, 0.0f, 0.0f), 0.0);
        float[] map = new float[VolumetricClouds.OCCLUSION_MAP_WIDTH * VolumetricClouds.OCCLUSION_MAP_HEIGHT];
        Arrays.fill(map, 1.0f);
        // Zenith row: hidden. Straight up maps to the first row whatever the azimuth.
        for (int x = 0; x < VolumetricClouds.OCCLUSION_MAP_WIDTH; x++) {
            map[x] = 0.0f;
        }
        VolumetricClouds.publishFarResults(0.2f, 0.9f, 0.1f, 0.8f, 0.4f, map);
        assertEquals(0.0f, VolumetricClouds.skyVisibility(0.01f, 1.0f, 0.0f), 1.0e-6f);
        assertEquals(1.0f, VolumetricClouds.skyVisibility(1.0f, 0.05f, 0.0f), 1.0e-6f);
        assertEquals(0.2f, VolumetricClouds.sunTransmittance(), 1.0e-6f);
        assertNotEquals(1.0f, VolumetricClouds.skyCover());
    }

    @Test
    void occupancyVersionChangesOnlyWhenCoverageChanges() {
        VolumetricClouds.publishOccupancy(5, -3, (x, z) -> x == 5 && z == -3);
        int version = VolumetricClouds.occupancyVersion();
        VolumetricClouds.publishOccupancy(5, -3, (x, z) -> x == 5 && z == -3);
        assertEquals(version, VolumetricClouds.occupancyVersion());
        VolumetricClouds.publishOccupancy(5, -3, (x, z) -> false);
        assertNotEquals(version, VolumetricClouds.occupancyVersion());
        int centre = VolumetricClouds.OCCUPANCY_HALF * VolumetricClouds.OCCUPANCY_SIZE + VolumetricClouds.OCCUPANCY_HALF;
        assertEquals(0, VolumetricClouds.occupancy()[centre]);
    }

    @Test
    void nearCloudsCanHideDiscsWithoutDimmingTheDistantLightsTwice() {
        VolumetricClouds.publishFrame(CloudField.Params.defaults(), 0.0);
        float[] map = new float[VolumetricClouds.OCCLUSION_MAP_WIDTH * VolumetricClouds.OCCLUSION_MAP_HEIGHT];
        VolumetricClouds.publishFarResults(1.0f, 1.0f, 0.0f, 0.05f, 0.8f, map);
        assertEquals(1.0f, VolumetricClouds.sunTransmittance());
        assertEquals(1.0f, VolumetricClouds.moonTransmittance());
        assertEquals(0.0f, VolumetricClouds.sunDiscTransmittance());
        assertEquals(0.05f, VolumetricClouds.moonDiscTransmittance());

        VolumetricClouds.clearFarResults();
        VolumetricClouds.publishFarResults(0.2f, 0.3f, 0.0f, 0.0f, 0.8f, map);
        assertEquals(0.2f, VolumetricClouds.sunTransmittance(), 1.0e-6f,
                "a new scene must not blend its light with the old scene");
        VolumetricClouds.deactivate();
        assertEquals(1.0f, VolumetricClouds.sunDiscTransmittance());
        assertEquals(1.0f, VolumetricClouds.moonDiscTransmittance());
    }

    @Test
    void aRestartKeepsTheCloudsUpUntilTheNextResultsReplaceThem() {
        VolumetricClouds.publishFrame(CloudField.Params.defaults(), 0.0);
        float[] map = new float[VolumetricClouds.OCCLUSION_MAP_WIDTH * VolumetricClouds.OCCLUSION_MAP_HEIGHT];
        VolumetricClouds.clearFarResults();
        VolumetricClouds.publishFarResults(0.3f, 0.4f, 0.1f, 0.2f, 0.8f, map);

        VolumetricClouds.restartFarResults();
        assertEquals(0.1f, VolumetricClouds.sunDiscTransmittance(), 1.0e-6f,
                "a restart must not show a cloudless sun before the new march lands");
        assertEquals(0.3f, VolumetricClouds.sunTransmittance(), 1.0e-6f);
        assertEquals(0.0f, VolumetricClouds.skyVisibility(0.0f, 1.0f, 0.0f), 1.0e-6f);

        Arrays.fill(map, 1.0f);
        VolumetricClouds.publishFarResults(0.9f, 0.9f, 0.6f, 0.6f, 0.1f, map);
        assertEquals(0.9f, VolumetricClouds.sunTransmittance(), 1.0e-6f,
                "the new scene replaces the old one outright");
        assertEquals(1.0f, VolumetricClouds.skyVisibility(0.0f, 1.0f, 0.0f), 1.0e-6f);
        VolumetricClouds.deactivate();
    }
}
