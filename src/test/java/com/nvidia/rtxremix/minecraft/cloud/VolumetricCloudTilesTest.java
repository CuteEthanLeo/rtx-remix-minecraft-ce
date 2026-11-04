/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.cloud;

import com.nvidia.rtxremix.minecraft.community.clouds.CloudField;
import com.nvidia.rtxremix.minecraft.community.clouds.CloudNoise;
import com.nvidia.rtxremix.minecraft.community.clouds.VolumetricClouds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumetricCloudTilesTest {

    private static CloudNoise noise;
    private static final CloudField.Params OVERCAST = CloudField.Params.of(260.0f, 0.85f, 1.0f, 0.0f).quantized();

    @BeforeAll
    static void generate() {
        noise = CloudNoise.shared().join();
    }

    @Test
    void anOvercastTileMeshesAClosedCloudDeck() {
        MarchingCubes.MeshData data = VolumetricCloudTiles.mesh(
                noise, new VolumetricCloudTiles.TileKey(3, -2), OVERCAST, 8);
        assertTrue(data.indices().length > 0);
        for (int i = 0; i < data.vertices().length; i += 6) {
            float y = data.vertices()[i + 1];
            assertTrue(y >= 0.0f && y <= VolumetricClouds.NEAR_CAP_ABOVE_BASE + 8.0f, "y " + y);
            float x = data.vertices()[i];
            assertTrue(x >= 0.0f && x <= VolumetricClouds.TILE_SIZE, "x " + x);
        }
    }

    @Test
    void neighbouringTilesMeetAtTheSameSeamVertices() {
        int step = 8;
        MarchingCubes.MeshData west = VolumetricCloudTiles.mesh(
                noise, new VolumetricCloudTiles.TileKey(0, 0), OVERCAST, step);
        MarchingCubes.MeshData east = VolumetricCloudTiles.mesh(
                noise, new VolumetricCloudTiles.TileKey(1, 0), OVERCAST, step);
        Set<String> westSeam = seam(west, VolumetricClouds.TILE_SIZE);
        Set<String> eastSeam = seam(east, 0.0f);
        assertFalse(westSeam.isEmpty());
        assertEquals(westSeam, eastSeam);
    }

    @Test
    void tilesPublishOneMeshPerUpdateAndCoverWhatTheyDraw() throws InterruptedException {
        List<float[]> created = new ArrayList<>();
        List<Object> destroyed = new ArrayList<>();
        VolumetricCloudTiles<float[]> tiles = new VolumetricCloudTiles<>(new VolumetricCloudTiles.MeshSink<>() {
            @Override
            public float[] create(float[] vertices, int[] indices) {
                created.add(vertices);
                return vertices;
            }

            @Override
            public void destroy(float[] mesh) {
                destroyed.add(mesh);
            }
        });
        VolumetricClouds.Quality quality = VolumetricClouds.quality(VolumetricClouds.QUALITY_LOW);
        assertNull(tiles.update(noise, 64.0, 64.0, OVERCAST, quality));
        assertFalse(tiles.isCovered(0, 0));
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (!tiles.isCovered(0, 0) && System.nanoTime() < deadline) {
            int before = created.size();
            assertNull(tiles.update(noise, 64.0, 64.0, OVERCAST, quality));
            assertTrue(created.size() - before <= 1);
            Thread.sleep(5);
        }
        assertTrue(tiles.isCovered(0, 0));

        // Far away, every tile is retired and its mesh destroyed.
        tiles.update(noise, 1.0e5, 1.0e5, OVERCAST, quality);
        assertFalse(tiles.isCovered(0, 0));
        assertEquals(created.size(), destroyed.size());
        tiles.clear();
    }

    @Test
    void tileDistanceIsZeroInsideTheTile() {
        VolumetricCloudTiles.TileKey key = new VolumetricCloudTiles.TileKey(2, 2);
        assertEquals(0.0, VolumetricCloudTiles.tileDistance(key, 300.0, 300.0));
        assertEquals(10.0, VolumetricCloudTiles.tileDistance(key, 246.0, 300.0), 1.0e-9);
    }

    private static Set<String> seam(MarchingCubes.MeshData data, float x) {
        Set<String> seam = new HashSet<>();
        for (int i = 0; i < data.vertices().length; i += 6) {
            if (Math.abs(data.vertices()[i] - x) < 1.0e-4f) {
                seam.add(Math.round(data.vertices()[i + 1] * 100.0f) + ":" + Math.round(data.vertices()[i + 2] * 100.0f));
            }
        }
        return seam;
    }
}
