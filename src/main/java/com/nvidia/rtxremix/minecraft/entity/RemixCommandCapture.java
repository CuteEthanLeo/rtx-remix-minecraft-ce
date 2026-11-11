/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Bridges the submit model to the capture providers.
 *
 * <p>Vanilla renderers no longer write vertices while they run: they submit model,
 * part, item, and text commands to a {@link SubmitNodeStorage}, and a
 * {@link FeatureRenderDispatcher} later replays those commands into a buffer source.
 * A capture therefore renders into this private queue and then {@link #flush() flushes}
 * it through a dispatcher whose buffer sources are the capture's, inside the capture's
 * {@code EntityRenderContext} bracket, so cuboids and quads still reach
 * {@code RemixCapturingConsumer} on the render thread.
 */
final class RemixCommandCapture implements AutoCloseable {

    private final SubmitNodeStorage queue = new SubmitNodeStorage();
    private final CapturingImmediate immediate;
    /** Crumbling overlays have no Remix consumer; they go nowhere, like the outlines below. */
    private final CapturingImmediate discard =
            new CapturingImmediate(layer -> RemixVertexConsumerProvider.NoOpVertexConsumer.INSTANCE);
    private final OutlineBufferSource outline = new DiscardingOutlineSource();
    private final CameraRenderState cameraState = new CameraRenderState();
    private FeatureRenderDispatcher dispatcher;

    RemixCommandCapture(RemixBufferSource provider) {
        this.immediate = new CapturingImmediate(provider);
    }

    SubmitNodeStorage queue() {
        return queue;
    }

    /** Camera state for renderers that orient labels or billboards toward the viewer. */
    CameraRenderState cameraState() {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client == null || client.gameRenderer == null ? null : client.gameRenderer.getMainCamera();
        if (camera != null) {
            cameraState.pos = camera.position();
            cameraState.blockPos = camera.blockPosition();
            cameraState.orientation.set(camera.rotation());
            cameraState.xRot = camera.xRot();
            cameraState.yRot = camera.yRot();
            cameraState.initialized = true;
        }
        return cameraState;
    }

    /** Replays every queued command into the capture provider and clears the queue. */
    void flush() {
        FeatureRenderDispatcher d = dispatcher();
        if (d == null) {
            queue.clear();
            return;
        }
        d.renderAllFeatures();
    }

    /** Drops queued commands without replaying them, after a renderer threw. */
    void discard() {
        queue.clear();
    }

    private FeatureRenderDispatcher dispatcher() {
        if (dispatcher == null) {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return null;
            }
            dispatcher = new FeatureRenderDispatcher(queue, client.getModelManager(), immediate,
                    client.getAtlasManager(), outline, discard, client.font,
                    client.gameRenderer.getGameRenderState());
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

    /**
     * Vanilla's outline source batches into its own immediate buffer and draws that into
     * the entity outline target; a capture must not raster, and nothing would end the batch.
     */
    private static final class DiscardingOutlineSource extends OutlineBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType layer) {
            return RemixVertexConsumerProvider.NoOpVertexConsumer.INSTANCE;
        }

        @Override
        public void endOutlineBatch() {
        }
    }
}
