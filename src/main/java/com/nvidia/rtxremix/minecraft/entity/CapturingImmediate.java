/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;

import java.util.LinkedHashMap;

/**
 * Vanilla code that insists on a {@link VertexConsumerProvider.Immediate}, such as the
 * command dispatcher that replays an entity's submitted commands, gets this adapter: it
 * routes every buffer request to the Remix capture provider and makes the raster flushes
 * no-ops.
 */
final class CapturingImmediate extends VertexConsumerProvider.Immediate implements AutoCloseable {
    private final BufferAllocator ownedAllocator;
    private final VertexConsumerProvider captureProvider;

    CapturingImmediate(VertexConsumerProvider captureProvider) {
        this(new BufferAllocator(256), captureProvider);
    }

    private CapturingImmediate(BufferAllocator allocator, VertexConsumerProvider captureProvider) {
        super(allocator, new LinkedHashMap<>());
        this.ownedAllocator = allocator;
        this.captureProvider = captureProvider;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return captureProvider.getBuffer(layer);
    }

    @Override
    public void drawCurrentLayer() {
    }

    @Override
    public void draw() {
    }

    @Override
    public void draw(RenderLayer layer) {
    }

    @Override
    public void close() {
        ownedAllocator.close();
    }
}
