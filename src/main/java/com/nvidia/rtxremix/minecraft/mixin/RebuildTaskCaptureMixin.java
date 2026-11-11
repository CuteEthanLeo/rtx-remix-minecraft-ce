/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.texture.BlockTextureManager;
import com.nvidia.rtxremix.minecraft.terrain.capture.SectionBlockData;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockQuadEmitter;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockDataQueue;
import com.nvidia.rtxremix.minecraft.terrain.VanillaSectionBuilds;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts chunk section builds to capture block/fluid mesh data for
 * RTX Remix <b>inline</b>, piggybacking on MC's own block loop instead
 * of running a separate pass.
 *
 * <ul>
 *   <li>{@code isOpaqueFullCube} redirect: captures opacity bitmap,
 *       face masks, and light entries for every block, and emits the
 *       block's model quads directly to the Remix interleaved buffer.</li>
 *   <li>{@code getRenderType} redirect: reports every block invisible while
 *       capturing, so vanilla skips its layer buffers, matrix stack, and
 *       {@code renderBlock} for geometry that is never drawn.</li>
 *   <li>{@code renderFluid} redirect: emits fluid quads directly,
 *       skipping GL vertex generation.</li>
 *   <li>RETURN inject: finalises the section mesh data and enqueues it
 *       for the render thread.</li>
 * </ul>
 */
@Mixin(targets = "net.minecraft.client.render.chunk.ChunkBuilder$BuiltChunk$RebuildTask")
public abstract class RebuildTaskCaptureMixin {

    private static final ThreadLocal<ProfileScope> rtxremix$tracyOuter = new ThreadLocal<>();
    private static final ThreadLocal<ProfileScope> rtxremix$tracyBlockLoop = new ThreadLocal<>();
    private static final ThreadLocal<BlockDataQueue.CaptureToken> rtxremix$blockDataCaptureToken = new ThreadLocal<>();
    /**
     * The section a capturing build began for. The slot's origin is read once, as vanilla
     * reads it: the render thread may move the slot while this worker is still building.
     */
    private static final ThreadLocal<ChunkSectionPos> rtxremix$capturedSection = new ThreadLocal<>();

    /**
     * Whether this render() is capturing for Remix, latched once at HEAD.
     *
     * <p>{@code rtxEnabled} is volatile so worker threads see a toggle promptly,
     * which means every redirect below would otherwise read it independently and
     * a toggle could land <em>between</em> two of them inside one build, routing
     * some blocks of a section into the Remix emitter and the rest into vanilla's
     * GL path. Reading one latched value keeps a build wholly in one mode;
     * {@code setRtxEnabled} forces a WorldRenderer.reload(), so sections built
     * in the losing mode are rebuilt immediately afterwards.
     *
     * <p>The latch is taken through {@link VanillaSectionBuilds}: a build that
     * latched capture off reads client light on this worker, and the client light
     * storage may publish its live map only while no such build is running.
     */
    private static final ThreadLocal<Boolean> rtxremix$captureForBuild =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Latched RTX capture state for the build in progress on this thread. */
    private static boolean rtxremix$capturing() {
        return rtxremix$captureForBuild.get();
    }

    /** The snapshot render() takes and clears; null for an empty section. */
    @Shadow
    @Nullable
    protected ChunkRendererRegion region;

    /** The enclosing {@code BuiltChunk}, whose origin names the section being built. */
    @Shadow
    @Final
    ChunkBuilder.BuiltChunk field_20839;

    // HEAD: init per-section emission state
    @Inject(method = "render", at = @At("HEAD"))
    private void rtxremix$onBuildHead(
            float cameraX,
            float cameraY,
            float cameraZ,
            BlockBufferBuilderStorage storage,
            CallbackInfoReturnable<?> cir) {

        // Latched before any early return below, and unconditionally, so a value
        // left over from this thread's previous build cannot decide this one.
        boolean capturing = VanillaSectionBuilds.beginBuild(RtxRemixMod::isRtxEnabled);
        rtxremix$captureForBuild.set(capturing);
        // An empty section has no region: vanilla's block loop does not run for it,
        // so there is nothing to capture.
        if (!capturing || region == null) return;

        ChunkSectionPos sectionPos = ChunkSectionPos.from(field_20839.getOrigin());
        rtxremix$capturedSection.set(sectionPos);
        rtxremix$blockDataCaptureToken.set(BlockDataQueue.beginCapture(sectionPos.getX(), sectionPos.getZ()));
        rtxremix$tracyOuter.set(ProfileScope.begin("SectionBuilder"));

        BlockTextureManager tex = null;
        RtxRemixMod mod = RtxRemixMod.getInstance();
        if (mod != null && mod.getRenderer() != null) {
            tex = mod.getRenderer().getTextureManager();
        }

        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        BlockQuadEmitter.threadLocal().beginSection(sectionPos, tex, blockRenderManager);
        rtxremix$tracyBlockLoop.set(ProfileScope.begin("block loop"));
    }

    // isOpaqueFullCube: capture opacity / face masks / lights / quads
    @Redirect(method = "render",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/block/BlockState;isOpaqueFullCube(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean rtxremix$captureBlock(BlockState state, BlockView world, BlockPos pos) {
        boolean opaque = state.isOpaqueFullCube(world, pos);
        BlockQuadEmitter emitter = BlockQuadEmitter.current();
        if (emitter != null && emitter.isActive()) {
            emitter.processBlock(state, world, pos, opaque);
            // Vanilla's renderBlock draws only MODEL blocks; ENTITYBLOCK_ANIMATED blocks
            // belong to their block-entity renderer.
            if (BlockQuadEmitter.getRenderTypeCached(state) == BlockRenderType.MODEL) {
                // render() only ever passes its ChunkRendererRegion here.
                emitter.emitBlock(state, pos, (BlockRenderView) world);
            }
        }
        return opaque;
    }

    // While capturing, the block's quads were already emitted above, so vanilla's raster
    // path for it (layer buffer, matrix stack, renderBlock) is dead work. Reporting the
    // block invisible makes vanilla skip all of it.
    @Redirect(method = "render",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/block/BlockState;getRenderType()Lnet/minecraft/block/BlockRenderType;"))
    private BlockRenderType rtxremix$renderTypeForVanilla(BlockState state) {
        return rtxremix$capturing()
                ? BlockRenderType.INVISIBLE
                : BlockQuadEmitter.getRenderTypeCached(state);
    }

    // renderFluid: emit fluid quads for Remix, skip GL
    @Redirect(method = "render",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderFluid(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/FluidState;)V"))
    private void rtxremix$redirectFluid(BlockRenderManager brm,
            BlockPos pos, BlockRenderView world, VertexConsumer consumer,
            BlockState blockState, FluidState fluidState) {
        if (rtxremix$capturing()) {
            BlockQuadEmitter emitter = BlockQuadEmitter.threadLocal();
            if (!emitter.isActive()) return;
            emitter.emitFluid(pos, world, fluidState);
        } else {
            brm.renderFluid(pos, world, consumer, blockState, fluidState);
        }
    }

    // RETURN: finalise mesh data and enqueue
    @Inject(method = "render", at = @At("RETURN"))
    private void rtxremix$onBuildReturn(
            float cameraX,
            float cameraY,
            float cameraZ,
            BlockBufferBuilderStorage storage,
            CallbackInfoReturnable<?> cir) {

        try {
            BlockQuadEmitter emitter = BlockQuadEmitter.threadLocal();
            if (!emitter.isActive()) return;

            ProfileScope blockScope = rtxremix$tracyBlockLoop.get();
            if (blockScope != null) blockScope.close();

            // The token and section are set at render HEAD before beginSection, so an
            // active emitter implies both exist.
            SectionBlockData blockData = emitter.finishSection(rtxremix$capturedSection.get());
            BlockDataQueue.enqueue(blockData, rtxremix$blockDataCaptureToken.get());
        } finally {
            ProfileScope outerScope = rtxremix$tracyOuter.get();
            if (outerScope != null) outerScope.close();
            rtxremix$tracyOuter.remove();
            rtxremix$tracyBlockLoop.remove();
            rtxremix$blockDataCaptureToken.remove();
            rtxremix$capturedSection.remove();
            // A build that throws skips this inject and stays counted; vanilla turns a
            // section build exception into a client crash, so the stale count cannot
            // outlive the session.
            VanillaSectionBuilds.endBuild(rtxremix$capturing());
            rtxremix$captureForBuild.remove();
        }
    }
}
