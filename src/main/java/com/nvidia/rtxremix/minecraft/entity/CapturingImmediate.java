/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.LinkedHashMap;

/**
 * Vanilla code that insists on a {@link MultiBufferSource.BufferSource}, such as the
 * feature dispatcher that replays an entity's submitted commands, gets this adapter: it
 * routes every buffer request to the Remix capture provider and makes the raster flushes
 * no-ops.
 */
final class CapturingImmediate extends MultiBufferSource.BufferSource implements AutoCloseable {
    private final ByteBufferBuilder ownedAllocator;
    private final RemixBufferSource captureProvider;

    CapturingImmediate(RemixBufferSource captureProvider) {
        this(new ByteBufferBuilder(256), captureProvider);
    }

    private CapturingImmediate(ByteBufferBuilder allocator, RemixBufferSource captureProvider) {
        super(allocator, new LinkedHashMap<>());
        this.ownedAllocator = allocator;
        this.captureProvider = captureProvider;
    }

    @Override
    public VertexConsumer getBuffer(RenderType layer) {
        return captureProvider.getBuffer(layer);
    }

    @Override
    public void endLastBatch() {
    }

    @Override
    public void endBatch() {
    }

    @Override
    public void endBatch(RenderType layer) {
    }

    @Override
    public void close() {
        ownedAllocator.close();
    }
}
