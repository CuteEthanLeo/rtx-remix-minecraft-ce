/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.cloud;

import com.nvidia.rtxremix.minecraft.community.clouds.CloudField;
import com.nvidia.rtxremix.minecraft.community.clouds.CloudNoise;
import com.nvidia.rtxremix.minecraft.community.clouds.VolumetricClouds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The near layer of the volumetric clouds: the cloud field around the camera meshed tile by
 * tile, so the path tracer can shadow the ground with it, form shafts in the haze under it, and
 * show it from above or beside. The far layer in the sky dome draws everything these tiles do
 * not cover, and reads which tiles they cover from {@link VolumetricClouds#publishOccupancy}.
 *
 * <p>Tiles live in the drifting field frame, so wind only moves their transforms; a tile is
 * rebuilt only when the weather parameters change. Meshing runs on one low-priority worker and
 * hands back plain vertex arrays tagged with the generation they were started under; the render
 * thread publishes at most one tile per frame and owns every Remix handle.
 *
 * @param <M> the published mesh type, a Remix mesh in game and a stand-in in tests
 */
final class VolumetricCloudTiles<M> {

    /** Density at the mesh surface; thinner wisps belong to the far layer's look only. */
    static final float ISO_DENSITY = 0.08f;
    /** Tiles are kept this far past the near radius, so a camera pacing a border does not churn them. */
    static final int KEEP_MARGIN = VolumetricClouds.TILE_SIZE + 64;
    private static final int MAX_IN_FLIGHT = 2;

    interface MeshSink<M> {
        M create(float[] vertices, int[] indices);
        void destroy(M mesh);
    }

    record TileKey(long x, long z) {}

    record GeneratedTile(long epoch, TileKey key, CloudField.Params params, float[] vertices, int[] indices,
                         long nanos, Throwable failure) {}

    static final class Tile<M> {
        final TileKey key;
        M mesh;
        CloudField.Params params;
        int vertexCount;
        int triangleCount;
        boolean pending;

        Tile(TileKey key) {
            this.key = key;
        }
    }

    private static final AtomicInteger THREADS = new AtomicInteger();
    private static ExecutorService executor;

    private final MeshSink<M> sink;
    private final Map<TileKey, Tile<M>> tiles = new HashMap<>();
    private final ConcurrentLinkedQueue<GeneratedTile> completed = new ConcurrentLinkedQueue<>();
    private final List<TileKey> wanted = new ArrayList<>();
    private volatile long epoch;
    private int inFlight;
    private int meshStep = 4;
    private int nearRadius = 448;
    private long cameraTileX;
    private long cameraTileZ;
    private long lastGenerationNanos;

    VolumetricCloudTiles(MeshSink<M> sink) {
        this.sink = sink;
    }

    /**
     * Brings the tile set up to date for a camera position in the field frame: drops tiles that
     * left the keep radius, queues missing or stale tiles nearest first, and publishes at most
     * one finished tile.
     *
     * @return the failure of a worker, if one finished with an exception
     */
    Throwable update(CloudNoise noise, double fieldX, double fieldZ, CloudField.Params params,
                     VolumetricClouds.Quality quality) {
        lastGenerationNanos = 0L;
        if (quality.meshStep() != meshStep || quality.nearRadius() != nearRadius) {
            meshStep = quality.meshStep();
            nearRadius = quality.nearRadius();
            clear();
        }
        cameraTileX = Math.floorDiv((long) Math.floor(fieldX), VolumetricClouds.TILE_SIZE);
        cameraTileZ = Math.floorDiv((long) Math.floor(fieldZ), VolumetricClouds.TILE_SIZE);

        Throwable failure = publishOne(params);
        retireDistant(fieldX, fieldZ);
        collectWanted(fieldX, fieldZ, params);
        for (TileKey key : wanted) {
            if (inFlight >= MAX_IN_FLIGHT) {
                break;
            }
            Tile<M> tile = tiles.computeIfAbsent(key, Tile::new);
            if (tile.pending) {
                continue;
            }
            tile.pending = true;
            inFlight++;
            schedule(noise, key, params, meshStep);
        }
        return failure;
    }

    private Throwable publishOne(CloudField.Params params) {
        GeneratedTile result;
        while ((result = completed.poll()) != null) {
            if (result.epoch() != epoch) {
                continue;
            }
            inFlight = Math.max(0, inFlight - 1);
            Tile<M> tile = tiles.get(result.key());
            if (tile == null) {
                continue;
            }
            tile.pending = false;
            if (result.failure() != null) {
                return result.failure();
            }
            lastGenerationNanos += result.nanos();
            if (tile.mesh != null) {
                sink.destroy(tile.mesh);
                tile.mesh = null;
            }
            tile.params = result.params();
            tile.vertexCount = result.vertices().length / 6;
            tile.triangleCount = result.indices().length / 3;
            if (result.indices().length > 0) {
                tile.mesh = sink.create(result.vertices(), result.indices());
                // One Remix mesh per frame keeps the upload off the frame time.
                return null;
            }
        }
        return null;
    }

    private void retireDistant(double fieldX, double fieldZ) {
        double keep = nearRadius + KEEP_MARGIN;
        Iterator<Tile<M>> iterator = tiles.values().iterator();
        while (iterator.hasNext()) {
            Tile<M> tile = iterator.next();
            if (tileDistance(tile.key, fieldX, fieldZ) > keep) {
                if (tile.mesh != null) {
                    sink.destroy(tile.mesh);
                }
                iterator.remove();
            }
        }
    }

    private void collectWanted(double fieldX, double fieldZ, CloudField.Params params) {
        wanted.clear();
        int reach = nearRadius / VolumetricClouds.TILE_SIZE + 1;
        for (long dz = -reach; dz <= reach; dz++) {
            for (long dx = -reach; dx <= reach; dx++) {
                TileKey key = new TileKey(cameraTileX + dx, cameraTileZ + dz);
                if (tileDistance(key, fieldX, fieldZ) > nearRadius) {
                    continue;
                }
                Tile<M> tile = tiles.get(key);
                if (tile == null || (!tile.pending && !params.equals(tile.params))) {
                    wanted.add(key);
                }
            }
        }
        wanted.sort(Comparator.comparingDouble(key -> tileDistance(key, fieldX, fieldZ)));
    }

    /** Horizontal distance from a field position to the nearest point of a tile. */
    static double tileDistance(TileKey key, double fieldX, double fieldZ) {
        double minX = key.x() * (double) VolumetricClouds.TILE_SIZE;
        double minZ = key.z() * (double) VolumetricClouds.TILE_SIZE;
        double dx = Math.max(0.0, Math.max(minX - fieldX, fieldX - (minX + VolumetricClouds.TILE_SIZE)));
        double dz = Math.max(0.0, Math.max(minZ - fieldZ, fieldZ - (minZ + VolumetricClouds.TILE_SIZE)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void schedule(CloudNoise noise, TileKey key, CloudField.Params params, int step) {
        long scheduledEpoch = epoch;
        executor().execute(() -> {
            if (scheduledEpoch != epoch) {
                completed.add(new GeneratedTile(scheduledEpoch, key, params, null, null, 0L, null));
                return;
            }
            long start = System.nanoTime();
            try {
                MarchingCubes.MeshData data = mesh(noise, key, params, step);
                completed.add(new GeneratedTile(scheduledEpoch, key, params, data.vertices(), data.indices(),
                        System.nanoTime() - start, null));
            } catch (Throwable failure) {
                completed.add(new GeneratedTile(scheduledEpoch, key, params, null, null, 0L, failure));
            }
        });
    }

    /**
     * Meshes one tile. Vertices are tile-local: x and z from the tile's corner, y from the
     * cloud base. Neighbouring tiles sample their shared faces identically, so their surfaces
     * meet without seams or walls.
     */
    static MarchingCubes.MeshData mesh(CloudNoise noise, TileKey key, CloudField.Params params, int step) {
        int size = VolumetricClouds.TILE_SIZE;
        int columns = size / step + 1;
        int layers = (int) Math.ceil(VolumetricClouds.NEAR_CAP_ABOVE_BASE / step) + 1;
        double originX = key.x() * (double) size;
        double originZ = key.z() * (double) size;
        float[] density = new float[columns * layers * columns];
        float[] scratch = new float[4];
        float[] column = new float[4];
        boolean any = false;
        for (int z = 0; z < columns; z++) {
            for (int x = 0; x < columns; x++) {
                double fx = originX + x * step;
                double fz = originZ + z * step;
                CloudField.column(noise, fx, fz, params, column);
                float coverage = column[0];
                float type = column[1];
                float thickness = column[2];
                for (int y = 0; y < layers; y++) {
                    int index = (z * layers + y) * columns + x;
                    float height = y * step;
                    float heightFraction = height / thickness;
                    float value = 0.0f;
                    if (coverage > 0.0f && heightFraction > 0.0f && heightFraction < 1.0f) {
                        value = CloudField.densityAt(noise, fx, params.base() + height, fz,
                                coverage, type, heightFraction, true, scratch);
                    }
                    density[index] = value - ISO_DENSITY;
                    if (value > ISO_DENSITY) {
                        any = true;
                    }
                }
            }
        }
        if (!any) {
            return new MarchingCubes.MeshData(new float[0], new int[0]);
        }
        // Close the surface at the cap and at the base, where the field itself is zero anyway.
        for (int z = 0; z < columns; z++) {
            for (int x = 0; x < columns; x++) {
                density[(z * layers) * columns + x] = -ISO_DENSITY;
                density[(z * layers + layers - 1) * columns + x] = -ISO_DENSITY;
            }
        }
        MarchingCubes cubes = new MarchingCubes(columns, layers, columns,
                0.0f, 0.0f, 0.0f, step, step, step, density);
        float base = params.base();
        float[] normalScratch = new float[4];
        return cubes.extract((x, y, z) -> {
            double fx = originX + x;
            double fz = originZ + z;
            return CloudField.density(noise, fx, base + y, fz, params, true, normalScratch) - ISO_DENSITY;
        });
    }

    /**
     * Whether the camera sees this tile through its mesh (or its published emptiness). A tile
     * waiting to be rebuilt for new weather keeps drawing its previous mesh and stays covered,
     * so the far layer never draws the same cloud a second time behind it.
     */
    boolean isCovered(long tileX, long tileZ) {
        Tile<M> tile = tiles.get(new TileKey(tileX, tileZ));
        return tile != null && tile.params != null;
    }

    Iterable<Tile<M>> tiles() {
        return tiles.values();
    }

    long cameraTileX() { return cameraTileX; }
    long cameraTileZ() { return cameraTileZ; }
    long lastGenerationNanos() { return lastGenerationNanos; }

    /** Drops every tile and any work in flight. */
    void clear() {
        epoch++;
        inFlight = 0;
        completed.clear();
        RuntimeException failure = null;
        for (Tile<M> tile : tiles.values()) {
            if (tile.mesh != null) {
                try {
                    sink.destroy(tile.mesh);
                } catch (RuntimeException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
        }
        tiles.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private static synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "RTX Remix volumetric clouds " + THREADS.incrementAndGet());
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
        }
        return executor;
    }
}
