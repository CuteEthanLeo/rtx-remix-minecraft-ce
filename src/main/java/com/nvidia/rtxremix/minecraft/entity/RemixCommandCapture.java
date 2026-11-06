/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;

/**
 * Bridges the submit model to the capture providers.
 *
 * <p>Vanilla renderers no longer write vertices while they run: they submit model,
 * part, item, and text commands to a {@link SubmitNodeStorage}, and in 26.x the feature
 * renderers replay those into a staged GPU buffer rather than a buffer source. A capture
 * therefore renders into this private queue and then {@link #flush() flushes} it by
 * replaying the vertex-producing submits itself, into the capture provider, inside the
 * capture's {@code EntityRenderContext} bracket, so cuboids and quads still reach
 * {@code RemixCapturingConsumer} on the render thread.
 */
final class RemixCommandCapture implements AutoCloseable {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final FeatureRenderPhase.Output DISCARD = (submit, strictlyOrdered) -> { };

    private final SubmitNodeStorage queue = new SubmitNodeStorage();
    private final RemixBufferSource provider;
    private final CameraRenderState cameraState = new CameraRenderState();
    private final PoseStack replayStack = new PoseStack();
    private final QuadInstance quadInstance = new QuadInstance();
    private final FeatureRenderPhase.Output replay = (submit, strictlyOrdered) -> replay(submit);

    RemixCommandCapture(RemixBufferSource provider) {
        this.provider = provider;
    }

    SubmitNodeStorage queue() {
        return queue;
    }

    /** Camera state for renderers that orient labels or billboards toward the viewer. */
    CameraRenderState cameraState() {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client == null || client.gameRenderer == null ? null : client.gameRenderer.mainCamera();
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
        for (SubmitNodeCollection collection : queue.getSubmitsPerOrder().values()) {
            for (FeatureRenderPhase<?> phase : collection.allPhases()) {
                if (phase.isEmpty()) {
                    continue;
                }
                // Crumbling overlays and outlines have no Remix consumer; they go nowhere.
                boolean skip = phase == collection.breakingOverlay || phase == collection.outline;
                phase.sortInto(skip ? DISCARD : replay);
            }
        }
        queue.getSubmitsPerOrder().clear();
    }

    /** Drops queued commands without replaying them, after a renderer threw. */
    void discard() {
        for (SubmitNodeCollection collection : queue.getSubmitsPerOrder().values()) {
            for (FeatureRenderPhase<?> phase : collection.allPhases()) {
                if (!phase.isEmpty()) {
                    phase.sortInto(DISCARD);
                }
            }
        }
        queue.getSubmitsPerOrder().clear();
    }

    private void replay(SubmitNode submit) {
        if (submit instanceof ModelFeatureRenderer.Submit<?> model) {
            replayModel(model);
        } else if (submit instanceof CustomFeatureRenderer.Submit custom) {
            custom.customGeometryRenderer().render(custom.pose(), provider.getBuffer(custom.renderType()));
        } else if (submit instanceof ItemFeatureRenderer.Submit item) {
            replayItem(item);
        } else if (submit instanceof BlockModelFeatureRenderer.Submit block) {
            replayBlockModel(block);
        }
        // TODO(26.2): text, name tags, leashes, flames, moving blocks and shadows are
        // not replayed; their feature renderers no longer take a buffer source.
    }

    private <S> void replayModel(ModelFeatureRenderer.Submit<S> submit) {
        replayStack.last().set(submit.pose());
        VertexConsumer buffer = provider.getBuffer(submit.renderType());
        if (submit.sheetedDecalPose() != null) {
            buffer = new SheetedDecalTextureGenerator(buffer, submit.sheetedDecalPose(), 1.0F);
        } else if (submit.sprite() != null) {
            buffer = submit.sprite().wrap(buffer);
        }
        Model<? super S> model = submit.model();
        model.setupAnim(submit.state());
        model.renderToBuffer(replayStack, buffer, submit.lightCoords(), submit.overlayCoords(),
                submit.tintedColor());
    }

    private void replayItem(ItemFeatureRenderer.Submit submit) {
        if (submit.outlineColor() != 0) {
            return;
        }
        quadInstance.setLightCoords(submit.lightCoords());
        quadInstance.setOverlayCoords(submit.overlayCoords());
        int[] tints = submit.tintLayers();
        for (BakedQuad quad : submit.quads()) {
            BakedQuad.MaterialInfo material = quad.materialInfo();
            int tint = material.isTinted() && material.tintIndex() < tints.length
                    ? tints[material.tintIndex()] : -1;
            quadInstance.setColor(tint);
            provider.getBuffer(material.itemRenderType()).putBakedQuad(submit.pose(), quad, quadInstance);
        }
        // TODO(26.2): the enchantment glint pass is not replayed.
    }

    private void replayBlockModel(BlockModelFeatureRenderer.Submit submit) {
        VertexConsumer buffer = provider.getBuffer(submit.renderType());
        if (submit.sheetedDecalPose() != null) {
            buffer = new SheetedDecalTextureGenerator(buffer, submit.sheetedDecalPose(), 1.0F);
        }
        quadInstance.setLightCoords(submit.lightCoords());
        quadInstance.setOverlayCoords(submit.overlayCoords());
        for (BlockStateModelPart part : submit.modelParts()) {
            for (Direction direction : DIRECTIONS) {
                putBlockQuads(part, direction, submit, buffer);
            }
            putBlockQuads(part, null, submit, buffer);
        }
    }

    private void putBlockQuads(BlockStateModelPart part, Direction direction,
                               BlockModelFeatureRenderer.Submit submit, VertexConsumer buffer) {
        int[] tints = submit.tintLayers();
        for (BakedQuad quad : part.getQuads(direction)) {
            int tintIndex = quad.materialInfo().tintIndex();
            boolean useTint = tintIndex != -1 && tintIndex < tints.length;
            quadInstance.setColor(useTint ? ARGB.multiply(submit.tintColor(), tints[tintIndex]) : submit.tintColor());
            buffer.putBakedQuad(submit.pose(), quad, quadInstance);
        }
    }

    @Override
    public void close() {
        discard();
    }
}
