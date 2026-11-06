/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.clouds;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;



class CloudFieldTest {

    private static CloudNoise noise;

    @BeforeAll
    static void generate() {
        noise = CloudNoise.generate();
    }

    @Test
    void noiseIsDeterministic() {
        CloudNoise again = CloudNoise.generate();
        assertArrayEquals(noise.base(), again.base());
        assertArrayEquals(noise.detail(), again.detail());
        assertArrayEquals(noise.weather(), again.weather());
    }

    @Test
    void densityIsZeroOutsideTheCloudLayer() {
        CloudField.Params params = CloudField.Params.of(260.0f, 0.85f, 1.0f, 1.0f);
        float[] scratch = new float[4];
        for (int x = 0; x < 4096; x += 211) {
            assertEquals(0.0f, CloudField.density(noise, x, 259.0, x * 0.7, params, true, scratch));
            assertEquals(0.0f, CloudField.density(noise, x, 260.0 + CloudField.MAX_THICKNESS + 1.0,
                    x * 0.7, params, true, scratch));
        }
    }

    @Test
    void defaultCoverHidesRoughlyTwoFifthsOfAClearSky() {
        float cover = coveredFraction(CloudField.Params.of(260.0f, CloudField.DEFAULT_COVERAGE, 0.0f, 0.0f));
        assertTrue(cover > 0.25f && cover < 0.55f, "clear-sky cover " + cover);
    }

    @Test
    void clearNightsKeepOnlyScatteredClouds() {
        float day = coveredFraction(CloudField.Params.of(260.0f, CloudField.DEFAULT_COVERAGE, 0.0f, 0.0f, 0.0f));
        float night = coveredFraction(CloudField.Params.of(260.0f, CloudField.DEFAULT_COVERAGE, 0.0f, 0.0f, 1.0f));
        assertTrue(night < day * 0.6f, night + " < 0.6 * " + day);
        float rainyNight = coveredFraction(CloudField.Params.of(260.0f, CloudField.DEFAULT_COVERAGE, 1.0f, 0.0f, 1.0f));
        assertTrue(rainyNight > 0.9f, "rainy night cover " + rainyNight);
    }

    @Test
    void moreCoverRainAndThunderCoverMoreSky() {
        float few = coveredFraction(CloudField.Params.of(260.0f, VolumetricClouds.MIN_COVERAGE, 0.0f, 0.0f));
        float normal = coveredFraction(CloudField.Params.of(260.0f, CloudField.DEFAULT_COVERAGE, 0.0f, 0.0f));
        float rain = coveredFraction(CloudField.Params.of(260.0f, CloudField.DEFAULT_COVERAGE, 1.0f, 0.0f));
        assertTrue(few < normal, few + " < " + normal);
        assertTrue(normal < rain, normal + " < " + rain);
        assertTrue(rain > 0.9f, "rain cover " + rain);
    }

    @Test
    void thunderGrowsTowersRainFlattensThem() {
        assertTrue(CloudField.thickness(1.0f) >= 1400.0f);
        assertTrue(CloudField.thickness(0.5f) < 700.0f);
        assertTrue(CloudField.thickness(0.0f) <= 150.0f);
        CloudField.Params thunder = CloudField.Params.of(260.0f, CloudField.DEFAULT_COVERAGE, 1.0f, 1.0f);
        assertTrue(thunder.typeBias() > 0.8f);
    }

    @Test
    void fieldCoordinatesWrapOnTheLongestPeriod() {
        assertEquals(10.0, CloudField.wrap(CloudField.WEATHER_PERIOD + 10.0), 1.0e-9);
        assertEquals(CloudField.WEATHER_PERIOD - 10.0, CloudField.wrap(-10.0), 1.0e-9);
        assertEquals(0.0, CloudField.WEATHER_PERIOD % CloudField.BASE_PERIOD);
        assertEquals(0.0, CloudField.WEATHER_PERIOD % CloudField.DETAIL_PERIOD);
        assertEquals(0.0, CloudField.WEATHER_PERIOD % VolumetricClouds.TILE_SIZE);
        float[] a = new float[4];
        float[] b = new float[4];
        CloudField.Params params = CloudField.Params.of(260.0f, 0.7f, 0.0f, 0.0f);
        for (int i = 0; i < 50; i++) {
            double x = 37.0 * i;
            double z = 53.0 * i;
            assertEquals(
                    CloudField.density(noise, x, 400.0, z, params, true, a),
                    CloudField.density(noise, x + 3 * CloudField.WEATHER_PERIOD, 400.0,
                            z - 2 * CloudField.WEATHER_PERIOD, params, true, b),
                    1.0e-3f);
        }
    }

    @Test
    void quantizedParamsStepGently() {
        CloudField.Params a = CloudField.Params.of(260.0f, 0.45f, 0.30f, 0.0f).quantized();
        CloudField.Params b = CloudField.Params.of(260.0f, 0.45f, 0.305f, 0.0f).quantized();
        assertEquals(a, b);
        assertEquals(0.0f, a.coverageBias() * 32.0f - Math.round(a.coverageBias() * 32.0f), 1.0e-5f);
    }

    private static float coveredFraction(CloudField.Params params) {
        float[] scratch = new float[4];
        int columns = 0;
        int covered = 0;
        for (int x = 0; x < 16384; x += 257) {
            for (int z = 0; z < 16384; z += 263) {
                columns++;
                for (int y = 262; y < 260 + CloudField.MAX_THICKNESS; y += 16) {
                    if (CloudField.density(noise, x, y, z, params, true, scratch) > 0.02f) {
                        covered++;
                        break;
                    }
                }
            }
        }
        return covered / (float) columns;
    }

    @Test
    void cirrusFieldWrapsIntoItsPeriodAndDriftsFasterThanTheCumulus() {
        double x = CloudField.cirrusX(-12345.0, 1000.0);
        double z = CloudField.cirrusZ(9.0e6, 0.0);
        assertTrue(x >= 0.0 && x < CloudField.CIRRUS_PERIOD);
        assertTrue(z >= 0.0 && z < CloudField.CIRRUS_PERIOD);
        double drift = CloudField.cirrusX(1000.0, 0.0) - CloudField.cirrusX(1000.0, 100.0);
        assertEquals(100.0 * CloudField.WIND_X_PER_TICK * CloudField.CIRRUS_WIND_SCALE, drift, 1.0e-9);
    }
}
