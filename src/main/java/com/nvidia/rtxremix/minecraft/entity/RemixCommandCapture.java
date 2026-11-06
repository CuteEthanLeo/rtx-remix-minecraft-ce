/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.state.CameraRenderState;

/**
 * Bridges the 1.21.9 submit model to the capture providers.
 *
 * <p>Vanilla renderers no longer write vertices while they run: they submit model,
 * part, item, and text commands to an {@code OrderedRenderCommandQueue}, and a
 * {@link RenderDispatcher} later replays those commands into a vertex consumer
 * provider. A capture therefore renders into this private queue and then
 * {@link #flush() flushes} it through a dispatcher whose providers are the capture's,
 * inside the capture's {@code EntityRenderContext} bracket, so cuboids and quads still
 * reach {@code RemixCapturingConsumer} on the render thread.
 */
final class RemixCommandCapture implements AutoCloseable {

    private final OrderedRenderCommandQueueImpl queue = new OrderedRenderCommandQueueImpl();
    private final CapturingImmediate immediate;
    /** Crumbling overlays and outlines have no Remix consumer; they go nowhere. */
    private final CapturingImmediate discard =
            new CapturingImmediate(layer -> RemixVertexConsumerProvider.NoOpVertexConsumer.INSTANCE);
    private final OutlineVertexConsumerProvider outline = new OutlineVertexConsumerProvider();
    private final CameraRenderState cameraState = new CameraRenderState();
    private RenderDispatcher dispatcher;

    RemixCommandCapture(RemixVertexConsumerProvider provider) {
        this.immediate = new CapturingImmediate(provider);
    }

    OrderedRenderCommandQueueImpl queue() {
        return queue;
    }

    /** The adapter vanilla calls that still take an Immediate provider directly can use. */
    CapturingImmediate immediate() {
        return immediate;
    }

    /** Camera state for renderers that orient labels or billboards toward the viewer. */
    CameraRenderState cameraState() {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client == null || client.gameRenderer == null ? null : client.gameRenderer.getCamera();
        if (camera != null) {
            cameraState.pos = camera.getCameraPos();
            cameraState.blockPos = camera.getBlockPos();
            cameraState.orientation.set(camera.getRotation());
            cameraState.entityPos = camera.getFocusedEntity() == null
                    ? cameraState.pos
                    : camera.getFocusedEntity().getEntityPos();
            cameraState.initialized = true;
        }
        return cameraState;
    }

    /** Replays every queued command into the capture provider and clears the queue. */
    void flush() {
        RenderDispatcher d = dispatcher();
        if (d == null) {
            queue.clear();
            return;
        }
        d.render();
    }

    /** Drops queued commands without replaying them, after a renderer threw. */
    void discard() {
        queue.clear();
    }

    private RenderDispatcher dispatcher() {
        if (dispatcher == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return null;
            }
            dispatcher = new RenderDispatcher(queue, client.getBlockRenderManager(), immediate,
                    client.getAtlasManager(), outline, discard, client.textRenderer);
        }
        return dispatcher;
    }

    @Override
    public void close() {
        queue.clear();
        if (dispatcher != null) {
            dispatcher.close();
            dispatcher = null;
        }
        immediate.close();
        discard.close();
    }
}
