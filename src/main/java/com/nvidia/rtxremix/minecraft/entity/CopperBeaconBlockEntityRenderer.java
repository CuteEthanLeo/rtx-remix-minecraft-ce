/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import com.nvidia.rtxremix.minecraft.block.CopperBeaconBlock;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconBlockEntity;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconPose;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nvidia.rtxremix.minecraft.RtxRemixCommonMod;
import org.jspecify.annotations.Nullable;
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

    public static final ModelLayerLocation BODY_MODEL_LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(RtxRemixCommonMod.MOD_ID, "copper_beacon"), "body");
    public static final ModelLayerLocation LENS_MODEL_LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(RtxRemixCommonMod.MOD_ID, "copper_beacon"), "lens");
    private static final SpriteId COPPER_TEXTURE = new SpriteId(
            TextureAtlas.LOCATION_BLOCKS,
            Identifier.withDefaultNamespace("block/copper_block"));
    static final Identifier LENS_SPRITE_ID =
            Identifier.fromNamespaceAndPath(RtxRemixCommonMod.MOD_ID, "block/copper_beacon_lens");
    static final Identifier UNLIT_LENS_SPRITE_ID =
            Identifier.fromNamespaceAndPath(RtxRemixCommonMod.MOD_ID, "block/copper_beacon_lens_unlit");
    private static final SpriteId LENS_TEXTURE = new SpriteId(
            TextureAtlas.LOCATION_BLOCKS,
            LENS_SPRITE_ID);
    private static final SpriteId UNLIT_LENS_TEXTURE = new SpriteId(
            TextureAtlas.LOCATION_BLOCKS,
            UNLIT_LENS_SPRITE_ID);

    private final ModelPart yoke;
    private final ModelPart head;
    private final ModelPart lens;
    private final SpriteGetter spriteHolder;
    private final CopperBeaconPose.Pose pose = new CopperBeaconPose.Pose();
    private final VerticalUvStripConsumer copperUvConsumer = new VerticalUvStripConsumer();

    public CopperBeaconBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart bodyRoot = context.bakeLayer(BODY_MODEL_LAYER);
        yoke = bodyRoot.getChild("yoke");
        head = yoke.getChild("head");
        lens = context.bakeLayer(LENS_MODEL_LAYER).getChild("lens");
        spriteHolder = context.sprites();
    }

    public static LayerDefinition getBodyTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();
        PartDefinition yokeData = root.addOrReplaceChild(
                "yoke",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0f, -6.0f, -1.0f, 3.0f, 2.0f, 3.0f)
                        .texOffs(0, 0).addBox(0.0f, -6.0f, -1.0f, 3.0f, 2.0f, 3.0f)
                        .texOffs(0, 0).addBox(-5.0f, -5.0f, -1.0f, 2.0f, 8.0f, 3.0f)
                        .texOffs(0, 0).addBox(3.0f, -5.0f, -1.0f, 2.0f, 8.0f, 3.0f),
                PartPose.offset(8.0f, 10.0f, 8.0f));
        yokeData.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0f, -4.0f, -3.0f, 4.0f, 8.0f, 1.0f)
                        .texOffs(0, 0).addBox(0.0f, -4.0f, -3.0f, 4.0f, 8.0f, 1.0f)
                        .texOffs(0, 0).addBox(-4.0f, -4.0f, 3.0f, 4.0f, 8.0f, 1.0f)
                        .texOffs(0, 0).addBox(0.0f, -4.0f, 3.0f, 4.0f, 8.0f, 1.0f)
                        .texOffs(0, 0).addBox(-4.0f, -3.0f, -2.0f, 1.0f, 6.0f, 5.0f)
                        .texOffs(0, 0).addBox(3.0f, -3.0f, -2.0f, 1.0f, 6.0f, 5.0f)
                        .texOffs(0, 0).addBox(-3.0f, -4.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .texOffs(0, 0).addBox(0.0f, -4.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .texOffs(0, 0).addBox(-3.0f, 3.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .texOffs(0, 0).addBox(0.0f, 3.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .texOffs(0, 0).addBox(-6.0f, -1.0f, -1.0f, 2.0f, 2.0f, 3.0f)
                        .texOffs(0, 0).addBox(4.0f, -1.0f, -1.0f, 2.0f, 2.0f, 3.0f)
                        .texOffs(0, 0).addBox(-4.0f, -4.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .texOffs(0, 0).addBox(0.0f, -4.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .texOffs(0, 0).addBox(-4.0f, 3.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .texOffs(0, 0).addBox(0.0f, 3.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .texOffs(0, 0).addBox(-4.0f, -3.0f, -4.0f, 1.0f, 6.0f, 1.0f)
                        .texOffs(0, 0).addBox(3.0f, -3.0f, -4.0f, 1.0f, 6.0f, 1.0f),
                PartPose.ZERO);
        return LayerDefinition.create(modelData, 16, 16);
    }

    public static LayerDefinition getLensTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(
                "lens",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0f, -3.0f, -4.01f, 6.0f, 6.0f, 0.0f),
                PartPose.offset(8.0f, 10.0f, 8.0f));
        return LayerDefinition.create(modelData, 6, 6);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            CopperBeaconBlockEntity beacon,
            State state,
            float tickDelta,
            Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(beacon, state, tickDelta, cameraPos, crumblingOverlay);
        BlockState blockState = beacon.getBlockState();
        state.up = blockState.getValue(CopperBeaconBlock.MOUNT);
        state.forward = blockState.getValue(BlockStateProperties.FACING);
        double animationTick = CopperBeaconAnimationClock.sample(beacon.getLevel(), tickDelta);
        state.yawAngle = beacon.yawAngleAt(animationTick);
        state.pitchAngle = beacon.pitchAngleAt(animationTick);
        state.active = beacon.isActive();
        state.intensityMultiplier = beacon.getIntensityMultiplier();
        state.lensTint = beacon.getLensTint();
    }

    @Override
    public void submit(
            State state,
            PoseStack matrices,
            SubmitNodeCollector queue,
            CameraRenderState cameraState) {
        int light = state.lightCoords;
        int overlay = OverlayTexture.NO_OVERLAY;

        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        CopperBeaconPose.evaluate(
                state.up,
                state.forward,
                state.yawAngle,
                state.pitchAngle,
                pose);
        matrices.mulPose(pose.mountingRotation());
        matrices.translate(-0.5, -0.5, -0.5);

        // 1.21.9+: geometry is submitted as commands and replayed later, after every
        // beacon has been submitted, so the shared parts are posed inside each command
        // from values captured now rather than once here.
        float yawRadians = pose.modelYawRadians();
        float pitchRadians = pose.modelPitchRadians();
        TextureAtlasSprite copperSprite = spriteHolder.get(COPPER_TEXTURE);
        RenderType copperLayer = COPPER_TEXTURE.renderType(RenderTypes::entitySolid);
        queue.submitCustomGeometry(matrices, copperLayer, (entry, buffer) -> {
            poseParts(yawRadians, pitchRadians);
            VertexConsumer copperVertices = copperUvConsumer.wrap(
                    copperSprite.wrap(buffer));
            yoke.render(stackAt(entry), copperVertices, light, overlay);
        });
        SpriteId lensTexture = lensTexture(state.active, state.intensityMultiplier);
        TextureAtlasSprite lensSprite = spriteHolder.get(lensTexture);
        int lensTint = state.lensTint;
        queue.submitCustomGeometry(matrices, lensTexture.renderType(RenderTypes::entitySolid), (entry, buffer) -> {
            poseParts(yawRadians, pitchRadians);
            lens.render(stackAt(entry), lensSprite.wrap(buffer),
                    light, overlay, lensTint);
        });
        matrices.popPose();
    }

    private void poseParts(float yawRadians, float pitchRadians) {
        yoke.yRot = yawRadians;
        yoke.xRot = 0.0f;
        head.yRot = 0.0f;
        head.xRot = pitchRadians;
        lens.yRot = yawRadians;
        lens.xRot = pitchRadians;
    }

    private static PoseStack stackAt(PoseStack.Pose entry) {
        PoseStack stack = new PoseStack();
        stack.last().set(entry);
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
        yoke.yRot = pose.modelYawRadians();
        yoke.xRot = 0.0f;
        head.yRot = 0.0f;
        head.xRot = pose.modelPitchRadians();
        lens.yRot = pose.modelYawRadians();
        lens.xRot = pose.modelPitchRadians();
    }

    static SpriteId lensTexture(boolean active, float intensityMultiplier) {
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
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            delegate.setColor(argb);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, copperTextureV(v));
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
