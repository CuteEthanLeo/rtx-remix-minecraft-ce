/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.capture;

import com.nvidia.rtxremix.minecraft.light.BlockLightRuleManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.LocalRandom;

import java.util.ArrayList;
import java.util.List;

/**
 * Samples the baked model of the block currently being meshed, so a block light
 * rule can be positioned against the geometry that actually rendered.
 *
 * <p>Rules see only a {@link BlockState} and a {@link net.minecraft.util.math.BlockPos},
 * which is enough for a block whose model is fixed. It is not enough for one
 * whose blockstate picks among rotated variants: the rotation lives in the
 * blockstate JSON, not in a property, so no offset table written against the
 * unrotated model can follow it. Only the mesh emitter knows which variant a
 * position drew, so it hands that knowledge over through this.
 *
 * <p>One instance per {@link BlockQuadEmitter}, therefore per worker thread.
 * {@link #begin} resets it for the next block; everything is computed lazily and
 * memoised, so a block whose rules never ask pays only the reset.
 */
public final class ModelSampling implements BlockLightRuleManager.ModelSampler {

    /** Matches the emission loop, so a sampled variant is the drawn variant. */
    private static final Direction[] DIRECTIONS = {
        null, Direction.UP, Direction.DOWN, Direction.NORTH,
        Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /** Own random, so sampling cannot perturb the emitter's model randomisation. */
    private final LocalRandom random = new LocalRandom(0L);

    private BlockRenderManager blockRenderManager;
    private BlockState state;
    private long seed;
    private BlockView world;
    private final BlockPos.Mutable origin = new BlockPos.Mutable();
    private final BlockPos.Mutable neighbor = new BlockPos.Mutable();

    private BlockStateModel model;
    private final List<BlockModelPart> parts = new ArrayList<>();
    private boolean modelResolved;

    private float[] vertices = new float[3 * 256];
    private int vertexCount;
    private boolean verticesCollected;

    private final float[] centroid = new float[3];
    private boolean centroidComputed;

    /** Rebinds this sampler to a new block and drops everything memoised. */
    public void begin(BlockRenderManager blockRenderManager, BlockState state, long seed) {
        begin(blockRenderManager, state, seed, null, null);
    }

    /** Rebinds to a block in a world, so rules can ask which faces are exposed. */
    public void begin(BlockRenderManager blockRenderManager, BlockState state, long seed,
                      BlockView world, BlockPos pos) {
        this.world = world;
        if (pos != null) {
            this.origin.set(pos);
        }
        this.blockRenderManager = blockRenderManager;
        this.state = state;
        this.seed = seed;
        this.model = null;
        this.modelResolved = false;
        this.vertexCount = 0;
        this.verticesCollected = false;
        this.centroidComputed = false;
    }

    @Override
    public boolean isFaceExposed(Direction face) {
        if (world == null) {
            return true;
        }
        neighbor.set(origin, face);
        BlockState other = world.getBlockState(neighbor);
        return !other.isOpaqueFullCube();
    }

    @Override
    public float[] centroid() {
        if (centroidComputed) return centroid;
        centroidComputed = true;

        collect();
        if (vertexCount == 0) {
            centroid[0] = 0.5f;
            centroid[1] = 0.5f;
            centroid[2] = 0.5f;
            return centroid;
        }

        double sx = 0, sy = 0, sz = 0;
        for (int i = 0; i < vertexCount * 3; i += 3) {
            sx += vertices[i];
            sy += vertices[i + 1];
            sz += vertices[i + 2];
        }
        centroid[0] = (float) (sx / vertexCount);
        centroid[1] = (float) (sy / vertexCount);
        centroid[2] = (float) (sz / vertexCount);
        return centroid;
    }

    private void collect() {
        if (verticesCollected) return;
        verticesCollected = true;

        if (!modelResolved) {
            modelResolved = true;
            model = (blockRenderManager != null && state != null)
                    ? blockRenderManager.getModel(state)
                    : null;
        }
        if (model == null) return;

        // Seed once and let the model pick its parts exactly as the emission loop
        // does, so a multi-variant model resolves to the same variant here as on screen.
        random.setSeed(seed);
        model.addParts(random, parts);
        for (Direction dir : DIRECTIONS) {
            for (int p = 0, partCount = parts.size(); p < partCount; p++) {
                for (BakedQuad quad : parts.get(p).getQuads(dir)) {
                    int[] vd = quad.vertexData();
                    ensureCapacity(vertexCount + 4);
                    for (int v = 0; v < 4; v++) {
                        int off = v * 8;
                        int w = vertexCount * 3;
                        vertices[w] = Float.intBitsToFloat(vd[off]);
                        vertices[w + 1] = Float.intBitsToFloat(vd[off + 1]);
                        vertices[w + 2] = Float.intBitsToFloat(vd[off + 2]);
                        vertexCount++;
                    }
                }
            }
        }
        parts.clear();
    }

    private void ensureCapacity(int wantedVertices) {
        if (wantedVertices * 3 <= vertices.length) return;
        int cap = vertices.length;
        while (cap < wantedVertices * 3) cap <<= 1;
        float[] grown = new float[cap];
        System.arraycopy(vertices, 0, grown, 0, vertexCount * 3);
        vertices = grown;
    }
}
