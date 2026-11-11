/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import com.nvidia.rtxremix.minecraft.block.CopperBeaconBlock;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconBlockEntity;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconPose;
import com.nvidia.rtxremix.minecraft.RtxRemixCommonMod;
import net.minecraft.block.BlockState;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteHolder;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.joml.Quaternionf;

/** Renders the independently rotating yoke and lamp head above the static mount. */
public final class CopperBeaconBlockEntityRenderer
        implements BlockEntityRenderer<CopperBeaconBlockEntity, CopperBeaconBlockEntityRenderer.State> {

    /** Everything {@link #render} needs, extracted on the render thread since 1.21.9. */
    public static final class State extends BlockEntityRenderState {
        Direction up = Direction.UP;
        Direction forward = Direction.NORTH;
        float yawAngle;
        float pitchAngle;
        boolean active;
        float intensityMultiplier;
        int lensTint = -1;
    }

    public static final EntityModelLayer BODY_MODEL_LAYER = new EntityModelLayer(
            Identifier.of(RtxRemixCommonMod.MOD_ID, "copper_beacon"), "body");
    public static final EntityModelLayer LENS_MODEL_LAYER = new EntityModelLayer(
            Identifier.of(RtxRemixCommonMod.MOD_ID, "copper_beacon"), "lens");
    private static final SpriteIdentifier COPPER_TEXTURE = new SpriteIdentifier(
            SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
            Identifier.ofVanilla("block/copper_block"));
    static final Identifier LENS_SPRITE_ID =
            Identifier.of(RtxRemixCommonMod.MOD_ID, "block/copper_beacon_lens");
    static final Identifier UNLIT_LENS_SPRITE_ID =
            Identifier.of(RtxRemixCommonMod.MOD_ID, "block/copper_beacon_lens_unlit");
    private static final SpriteIdentifier LENS_TEXTURE = new SpriteIdentifier(
            SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
            LENS_SPRITE_ID);
    private static final SpriteIdentifier UNLIT_LENS_TEXTURE = new SpriteIdentifier(
            SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
            UNLIT_LENS_SPRITE_ID);

    private final ModelPart yoke;
    private final ModelPart head;
    private final ModelPart lens;
    private final SpriteHolder spriteHolder;
    private final CopperBeaconPose.Pose pose = new CopperBeaconPose.Pose();
    private final VerticalUvStripConsumer copperUvConsumer = new VerticalUvStripConsumer();

    public CopperBeaconBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        ModelPart bodyRoot = context.getLayerModelPart(BODY_MODEL_LAYER);
        yoke = bodyRoot.getChild("yoke");
        head = yoke.getChild("head");
        lens = context.getLayerModelPart(LENS_MODEL_LAYER).getChild("lens");
        spriteHolder = context.spriteHolder();
    }

    public static TexturedModelData getBodyTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        ModelPartData yokeData = root.addChild(
                "yoke",
                ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-3.0f, -6.0f, -1.0f, 3.0f, 2.0f, 3.0f)
                        .uv(0, 0).cuboid(0.0f, -6.0f, -1.0f, 3.0f, 2.0f, 3.0f)
                        .uv(0, 0).cuboid(-5.0f, -5.0f, -1.0f, 2.0f, 8.0f, 3.0f)
                        .uv(0, 0).cuboid(3.0f, -5.0f, -1.0f, 2.0f, 8.0f, 3.0f),
                ModelTransform.origin(8.0f, 10.0f, 8.0f));
        yokeData.addChild(
                "head",
                ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-4.0f, -4.0f, -3.0f, 4.0f, 8.0f, 1.0f)
                        .uv(0, 0).cuboid(0.0f, -4.0f, -3.0f, 4.0f, 8.0f, 1.0f)
                        .uv(0, 0).cuboid(-4.0f, -4.0f, 3.0f, 4.0f, 8.0f, 1.0f)
                        .uv(0, 0).cuboid(0.0f, -4.0f, 3.0f, 4.0f, 8.0f, 1.0f)
                        .uv(0, 0).cuboid(-4.0f, -3.0f, -2.0f, 1.0f, 6.0f, 5.0f)
                        .uv(0, 0).cuboid(3.0f, -3.0f, -2.0f, 1.0f, 6.0f, 5.0f)
                        .uv(0, 0).cuboid(-3.0f, -4.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .uv(0, 0).cuboid(0.0f, -4.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .uv(0, 0).cuboid(-3.0f, 3.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .uv(0, 0).cuboid(0.0f, 3.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .uv(0, 0).cuboid(-6.0f, -1.0f, -1.0f, 2.0f, 2.0f, 3.0f)
                        .uv(0, 0).cuboid(4.0f, -1.0f, -1.0f, 2.0f, 2.0f, 3.0f)
                        .uv(0, 0).cuboid(-4.0f, -4.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .uv(0, 0).cuboid(0.0f, -4.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .uv(0, 0).cuboid(-4.0f, 3.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .uv(0, 0).cuboid(0.0f, 3.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .uv(0, 0).cuboid(-4.0f, -3.0f, -4.0f, 1.0f, 6.0f, 1.0f)
                        .uv(0, 0).cuboid(3.0f, -3.0f, -4.0f, 1.0f, 6.0f, 1.0f),
                ModelTransform.NONE);
        return TexturedModelData.of(modelData, 16, 16);
    }

    public static TexturedModelData getLensTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        root.addChild(
                "lens",
                ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-3.0f, -3.0f, -4.01f, 6.0f, 6.0f, 0.0f),
                ModelTransform.origin(8.0f, 10.0f, 8.0f));
        return TexturedModelData.of(modelData, 6, 6);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(
            CopperBeaconBlockEntity beacon,
            State state,
            float tickDelta,
            Vec3d cameraPos,
            @Nullable ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        BlockEntityRenderer.super.updateRenderState(beacon, state, tickDelta, cameraPos, crumblingOverlay);
        BlockState blockState = beacon.getCachedState();
        state.up = blockState.get(CopperBeaconBlock.MOUNT);
        state.forward = blockState.get(Properties.FACING);
        double animationTick = CopperBeaconAnimationClock.sample(beacon.getWorld(), tickDelta);
        state.yawAngle = beacon.yawAngleAt(animationTick);
        state.pitchAngle = beacon.pitchAngleAt(animationTick);
        state.active = beacon.isActive();
        state.intensityMultiplier = beacon.getIntensityMultiplier();
        state.lensTint = beacon.getLensTint();
    }

    @Override
    public void render(
            State state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState) {
        int light = state.lightmapCoordinates;
        int overlay = OverlayTexture.DEFAULT_UV;

        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        CopperBeaconPose.evaluate(
                state.up,
                state.forward,
                state.yawAngle,
                state.pitchAngle,
                pose);
        matrices.multiply(pose.mountingRotation());
        matrices.translate(-0.5, -0.5, -0.5);

        // 1.21.9+: geometry is submitted as commands and replayed later, after every
        // beacon has been submitted, so the shared parts are posed inside each command
        // from values captured now rather than once here.
        float yawRadians = pose.modelYawRadians();
        float pitchRadians = pose.modelPitchRadians();
        Sprite copperSprite = spriteHolder.getSprite(COPPER_TEXTURE);
        RenderLayer copperLayer = COPPER_TEXTURE.getRenderLayer(RenderLayer::getEntitySolid);
        queue.submitCustom(matrices, copperLayer, (entry, buffer) -> {
            poseParts(yawRadians, pitchRadians);
            VertexConsumer copperVertices = copperUvConsumer.wrap(
                    copperSprite.getTextureSpecificVertexConsumer(buffer));
            yoke.render(stackAt(entry), copperVertices, light, overlay);
        });
        SpriteIdentifier lensTexture = lensTexture(state.active, state.intensityMultiplier);
        Sprite lensSprite = spriteHolder.getSprite(lensTexture);
        int lensTint = state.lensTint;
        queue.submitCustom(matrices, lensTexture.getRenderLayer(RenderLayer::getEntitySolid), (entry, buffer) -> {
            poseParts(yawRadians, pitchRadians);
            lens.render(stackAt(entry), lensSprite.getTextureSpecificVertexConsumer(buffer),
                    light, overlay, lensTint);
        });
        matrices.pop();
    }

    private void poseParts(float yawRadians, float pitchRadians) {
        yoke.yaw = yawRadians;
        yoke.pitch = 0.0f;
        head.yaw = 0.0f;
        head.pitch = pitchRadians;
        lens.yaw = yawRadians;
        lens.pitch = pitchRadians;
    }

    private static MatrixStack stackAt(MatrixStack.Entry entry) {
        MatrixStack stack = new MatrixStack();
        stack.peek().copy(entry);
        return stack;
    }

    static Quaternionf mountingRotation(Direction upDirection, Direction forwardDirection) {
        return CopperBeaconPose.mountingRotation(
                upDirection, forwardDirection, new Quaternionf());
    }

    static void applyArticulation(
            ModelPart yoke,
            ModelPart head,
            ModelPart lens,
            CopperBeaconPose.Pose pose) {
        yoke.yaw = pose.modelYawRadians();
        yoke.pitch = 0.0f;
        head.yaw = 0.0f;
        head.pitch = pose.modelPitchRadians();
        lens.yaw = pose.modelYawRadians();
        lens.pitch = pose.modelPitchRadians();
    }

    static SpriteIdentifier lensTexture(boolean active, float intensityMultiplier) {
        return active && intensityMultiplier > 0.0f
                ? LENS_TEXTURE
                : UNLIT_LENS_TEXTURE;
    }

    static float copperTextureV(float v) {
        return 0.25f + v * 0.5f;
    }

    /**
     * Keeps resource-pack details along the texture edges while excluding all
     * four corners, where stylized copper textures commonly place rivets.
     */
    private static final class VerticalUvStripConsumer implements VertexConsumer {
        private VertexConsumer delegate;

        VertexConsumer wrap(VertexConsumer delegate) {
            this.delegate = delegate;
            return this;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            delegate.color(argb);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, copperTextureV(v));
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }
    }
}
