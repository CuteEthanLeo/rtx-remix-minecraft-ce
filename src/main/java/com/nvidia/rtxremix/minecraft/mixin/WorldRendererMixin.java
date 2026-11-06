/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.weather.RemixWeatherRenderer;
import com.nvidia.rtxremix.minecraft.terrain.visibility.BuiltChunkStorageSectionAccess;
import com.nvidia.rtxremix.minecraft.terrain.visibility.BuiltChunkArrayIterator;
import com.nvidia.rtxremix.minecraft.terrain.capture.RegionBuilderRelease;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockQuadEmitter;
import com.nvidia.rtxremix.minecraft.terrain.WorldRendererTerrainAccess;
import com.nvidia.rtxremix.minecraft.terrain.NeighbourReflagFilter;
import com.nvidia.rtxremix.minecraft.terrain.ChunkBuilderBlockEntityAccess;
import com.nvidia.rtxremix.minecraft.sky.WorldRendererStarAccess;
import com.nvidia.rtxremix.minecraft.profiling.WorldRendererProfilingAccess;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import com.nvidia.rtxremix.minecraft.cloud.WorldRendererCloudAccess;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.RemixRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.world.BlockRenderView;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRenderData;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.SortedSet;

/**
 * Reroutes vanilla's world render into the Remix capture while a Remix world is
 * active.
 *
 * <ul>
 *   <li>Entities, block entities and the block-breaking crack are captured where
 *       vanilla draws them, so each model is evaluated once rather than once per
 *       renderer.</li>
 *   <li>Vanilla's terrain layers, particle draw and cloud draw are cancelled,
 *       because Remix owns that geometry.</li>
 *   <li>Weather and the star buffer are handed to the Remix passes.</li>
 *   <li>Section rebuild scheduling is exposed to the terrain pipeline: the
 *       water-occlusion margin on block-update rebuilds and the chunk-load
 *       neighbour re-flag filter.</li>
 *   <li>The block-entity walk is narrowed to chunks that hold one.</li>
 *   <li>Every vanilla render phase, {@code setupTerrain} and
 *       {@code updateChunks} are labelled for Tracy and JFR.</li>
 * </ul>
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin
        implements WorldRendererCloudAccess, WorldRendererProfilingAccess,
        WorldRendererStarAccess, WorldRendererTerrainAccess {
    private static final int rtxremix$BLOCK_ENTITY_CHUNK_LIST_INITIAL_CAPACITY = 32_768;

    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$renderScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$setupTerrainScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$updateChunksScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$uploadScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$syncRebuildScope = new ThreadLocal<>();

    @Shadow
    private ChunkBuilder chunkBuilder;

    @Shadow
    @Nullable
    private BuiltChunkStorage chunks;

    @Shadow
    @Nullable
    public ClientWorld world;

    @Shadow
    private int ticks;

    @Shadow
    @Final
    private ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions;

    private ProfileScope rtxremix$profilerPhaseScope;
    private String rtxremix$profilerPhaseName;
    private int rtxremix$renderLayerCalls;
    private int rtxremix$renderLayerSkips;
    private int rtxremix$phaseRenderLayerCallsStart;
    private int rtxremix$phaseRenderLayerSkipsStart;
    private int rtxremix$blockEntityChunkScanCount = -1;
    private int rtxremix$blockEntityChunkCount = -1;
    private ChunkBuilder.BuiltChunk[] rtxremix$blockEntityChunks =
            new ChunkBuilder.BuiltChunk[rtxremix$BLOCK_ENTITY_CHUNK_LIST_INITIAL_CAPACITY];
    private int rtxremix$blockEntityChunkBufferUsed;
    private final BuiltChunkArrayIterator rtxremix$blockEntityChunkIterator = new BuiltChunkArrayIterator();
    private float[] rtxremix$starVertices = new float[0];

    @Override
    public int rtxremix$getCloudTicks() {
        return ticks;
    }

    // TODO(1.21.11): vanilla's star buffer moved from WorldRenderer.buildStarsBuffer into
    // SkyRendering and is built straight into a GpuBuffer; the Remix star field gets no
    // vanilla star vertices until that is hooked (copyStarVertices returns an empty array).

    @Override
    public float[] rtxremix$copyStarVertices() {
        return rtxremix$starVertices.clone();
    }

    /**
     * Hidden water-top culling reads farther than vanilla's one-block rebuild
     * margin. Expand horizontal invalidation so a ceiling or fluid update
     * cannot leave a neighboring section's occlusion decision stale.
     */
    @Inject(method = "scheduleBlockRenders(IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void rtxremix$expandWaterOcclusionRebuildMargin(
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        if (!RtxRemixMod.isRtxEnabled()) {
            return;
        }

        int radius = Math.max(1, BlockQuadEmitter.getWaterTopOcclusionRadius());
        WorldRenderer renderer = (WorldRenderer) (Object) this;
        int minSectionX = ChunkSectionPos.getSectionCoord(minX - radius);
        int minSectionY = ChunkSectionPos.getSectionCoord(minY - 1);
        int minSectionZ = ChunkSectionPos.getSectionCoord(minZ - radius);
        int maxSectionX = ChunkSectionPos.getSectionCoord(maxX + radius);
        int maxSectionY = ChunkSectionPos.getSectionCoord(maxY + 1);
        int maxSectionZ = ChunkSectionPos.getSectionCoord(maxZ + radius);
        for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
            for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    renderer.scheduleChunkRender(sectionX, sectionY, sectionZ);
                }
            }
        }
        ci.cancel();
    }

    /**
     * Chunk-load and light-data re-flags enter here and nowhere else: ClientWorld's
     * scheduleBlockRenders(III) is a pure forwarder whose only callers are
     * ClientPlayNetworkHandler.scheduleRenderChunk and updateLighting. Block updates and
     * biome data use the other overloads and stay vanilla. Vanilla's 3x3x3 loop is replaced
     * wholesale because the rule must tell the loading column from its neighbours, which a
     * redirect of the inner per-section call cannot.
     */
    @Inject(method = "scheduleChunkRenders3x3x3(III)V", at = @At("HEAD"), cancellable = true)
    private void rtxremix$filterChunkLoadReflags(int cx, int sy, int cz, CallbackInfo ci) {
        if (!RtxRemixMod.isRtxEnabled() || !NeighbourReflagFilter.ENABLED || chunks == null) {
            return;
        }
        ((BuiltChunkStorageSectionAccess) chunks).rtxremix$flagLoadedSection(cx, sy, cz);
        ci.cancel();
    }

    @Override
    public void rtxremix$onNeighbourColumnInvalidated(int cx, int cz) {
        if (chunks != null) {
            ((BuiltChunkStorageSectionAccess) chunks).rtxremix$clearNeighbourSnapshotBits(cx, cz);
        }
    }

    @Override
    public LongSet rtxremix$builtSectionPositions() {
        LongOpenHashSet built = new LongOpenHashSet();
        if (chunks == null) {
            return built;
        }
        for (ChunkBuilder.BuiltChunk builtChunk : chunks.chunks) {
            if (builtChunk.getCurrentRenderData() == ChunkRenderData.HIDDEN) {
                continue;
            }
            BlockPos origin = builtChunk.getOrigin();
            built.add(ChunkSectionPos.asLong(
                    ChunkSectionPos.getSectionCoord(origin.getX()),
                    ChunkSectionPos.getSectionCoord(origin.getY()),
                    ChunkSectionPos.getSectionCoord(origin.getZ())));
        }
        return built;
    }

    @Override
    public LongSet rtxremix$queueSectionsForRemix(LongSet sections) {
        if (chunks == null || chunkBuilder == null || world == null || sections.isEmpty()) {
            return LongSets.emptySet();
        }

        // Same gate as vanilla updateChunks: a section built before its light
        // is ready would bake bad light, and cancelRebuild below means nothing
        // re-flags it afterwards.
        LightingProvider lightingProvider = world.getLightingProvider();

        ArrayList<ChunkBuilder.BuiltChunk> loadedSections = new ArrayList<>();
        for (ChunkBuilder.BuiltChunk builtChunk : chunks.chunks) {
            BlockPos origin = builtChunk.getOrigin();
            int cx = ChunkSectionPos.getSectionCoord(origin.getX());
            int sy = ChunkSectionPos.getSectionCoord(origin.getY());
            int cz = ChunkSectionPos.getSectionCoord(origin.getZ());
            if (!sections.contains(ChunkSectionPos.asLong(cx, sy, cz))) {
                continue;
            }
            WorldChunk worldChunk = world.getChunkManager().getWorldChunk(cx, cz, false);
            if (worldChunk != null
                    && !worldChunk.getSection(worldChunk.sectionCoordToIndex(sy)).isEmpty()
                    && lightingProvider.isLightingEnabled(ChunkSectionPos.asLong(cx, sy, cz))) {
                loadedSections.add(builtChunk);
            }
        }

        // ChunkBuilder's normal task queue is FIFO. Queue nearby sections first
        // so visible holes close promptly while the Remix scene repopulates.
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        double cameraX = camera.getCameraPos().x;
        double cameraY = camera.getCameraPos().y;
        double cameraZ = camera.getCameraPos().z;
        loadedSections.sort(Comparator.comparingDouble(builtChunk -> {
            BlockPos origin = builtChunk.getOrigin();
            double dx = origin.getX() + 8.0 - cameraX;
            double dy = origin.getY() + 8.0 - cameraY;
            double dz = origin.getZ() + 8.0 - cameraZ;
            return dx * dx + dy * dy + dz * dz;
        }));

        ChunkRendererRegionBuilder regionBuilder = new ChunkRendererRegionBuilder();
        LongOpenHashSet queued = new LongOpenHashSet(loadedSections.size());
        try {
            for (ChunkBuilder.BuiltChunk builtChunk : loadedSections) {
                builtChunk.scheduleRebuild(regionBuilder);
                // Prevent WorldRenderer.updateChunks from queueing this same slot a
                // second time when it next walks vanilla's visible section list.
                builtChunk.cancelRebuild();
                BlockPos origin = builtChunk.getOrigin();
                queued.add(ChunkSectionPos.asLong(
                        ChunkSectionPos.getSectionCoord(origin.getX()),
                        ChunkSectionPos.getSectionCoord(origin.getY()),
                        ChunkSectionPos.getSectionCoord(origin.getZ())));
            }
        } finally {
            ((RegionBuilderRelease) regionBuilder).rtxremix$releaseSnapshots();
        }
        return queued;
    }

    @Override
    public void rtxremix$scheduleResidentSectionRebuild(int cx, int sy, int cz) {
        ((BuiltChunkStorageSectionAccess) chunks).rtxremix$scheduleRebuildIfResident(cx, sy, cz);
    }

    // TODO(1.21.11): WorldRenderer.render no longer calls renderEntity(...); entities are
    // extracted into render states and submitted through OrderedRenderCommandQueue. In-place
    // entity capture (with its outline-only vanilla fallback) is disabled here.

    /**
     * Captures the block-breaking crack where vanilla renders it, instead of letting it
     * fall into the OpenGL frame that Remix's image replaces.
     *
     * <p>Vanilla's matrix stack and consumer are deliberately not forwarded: the stack
     * is camera-relative and the consumer writes into the GL effect buffer, while the
     * capture supplies an identity stack and its own crack projection so the geometry
     * stays block-local. When Remix declines, vanilla renders as usual.
     */
    @WrapOperation(
            method = "renderBlockDamage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderDamage(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"
            ))
    private void rtxremix$captureBlockDamageInPlace(
            BlockRenderManager blockRenderManager,
            BlockState state,
            BlockPos pos,
            BlockRenderView world,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            Operation<Void> original) {
        RtxRemixMod mod = RtxRemixMod.getInstance();
        RemixRenderer renderer = mod == null ? null : mod.getRenderer();
        boolean captured = renderer != null
                && renderer.renderBlockDamageInPlace(pos, rtxremix$blockBreakingStage(pos),
                        (captureMatrices, captureConsumer) -> original.call(
                                blockRenderManager, state, pos, world, captureMatrices, captureConsumer));
        if (!captured) {
            original.call(blockRenderManager, state, pos, world, matrices, vertexConsumer);
        }
    }

    /**
     * The highest destroy stage among the actors breaking this block, read back from the
     * progress map because the wrapped call receives only the consumer vanilla built for
     * that stage. Vanilla reaches the wrapped call only after finding this position's set
     * non-empty, on the same thread, so the entry is present here.
     */
    private int rtxremix$blockBreakingStage(BlockPos pos) {
        SortedSet<BlockBreakingInfo> progressions = blockBreakingProgressions.get(pos.asLong());
        return progressions.last().getStage();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void rtxremix$renderHead(CallbackInfo ci) {
        rtxremix$openJfrOnly(
                rtxremix$renderScope,
                "WorldRenderer.render",
                "Java.WorldRenderer.render.ms");
        rtxremix$renderLayerCalls = 0;
        rtxremix$renderLayerSkips = 0;
        rtxremix$blockEntityChunkScanCount = -1;
        rtxremix$blockEntityChunkCount = -1;
        rtxremix$transitionProfilerPhase("preamble");
        RtxRemixMod mod = RtxRemixMod.getInstance();
        if (mod != null && mod.getRenderer() != null) {
            mod.getRenderer().beginEntityFrame();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void rtxremix$renderReturn(CallbackInfo ci) {
        rtxremix$closeProfilerPhase();
        rtxremix$close(rtxremix$renderScope);
        // 1.21.11: Fabric's WorldRenderEvents.END is gone; the world-render tail lives here.
        RtxRemixMod.onWorldRenderEnd();
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V"
            ))
    private void rtxremix$mirrorVanillaProfilerPhase(
            Profiler profiler,
            String name,
            Operation<Void> original) {
        original.call(profiler, name);
        rtxremix$transitionProfilerPhase(name);
    }

    // TODO(1.21.11): WorldRenderer.renderLayer was replaced by renderBlockLayers, which returns
    // a SectionRenderState drawn later by the frame graph; vanilla terrain layers are no longer
    // skipped while Remix renders the world.

    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
    private void rtxremix$skipVanillaParticleDraw(CallbackInfo ci) {
        if (RtxRemixMod.isRemixWorldRendering()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void rtxremix$skipVanillaCloudDraw(CallbackInfo ci) {
        if (RtxRemixMod.isRemixWorldRendering()
                && RtxRemixMod.getInstance()
                        .getRenderer()
                        .getCloudRenderer()
                        .shouldSuppressVanillaClouds()) {
            ci.cancel();
        }
    }

    // TODO(1.21.11): renderWeather now hands WeatherRendering a frame-graph pass with
    // RenderPipelines; the old setShaderTexture/drawWithGlobalProgram capture points are gone, so
    // rain and snow are not captured for RemixWeatherRenderer.

    @Redirect(
            method = "render",
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "stringValue=blockentities"),
                    to = @At(value = "CONSTANT", args = "stringValue=destroyProgress")),
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;iterator()Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"
            ),
            require = 0)
    private ObjectListIterator<ChunkBuilder.BuiltChunk> rtxremix$iterateBlockEntityChunks(
            ObjectArrayList<ChunkBuilder.BuiltChunk> chunks) {
        if (!RtxRemixMod.isRemixWorldRendering()) {
            return chunks.iterator();
        }

        int visibleChunks = chunks.size();
        int blockEntityChunks = 0;
        for (int i = 0; i < visibleChunks; i++) {
            ChunkBuilder.BuiltChunk chunk = chunks.get(i);
            if (chunk instanceof ChunkBuilderBlockEntityAccess access
                    && access.rtxremix$hasBlockEntities()) {
                if (blockEntityChunks == rtxremix$blockEntityChunks.length) {
                    rtxremix$blockEntityChunks = Arrays.copyOf(
                            rtxremix$blockEntityChunks,
                            rtxremix$blockEntityChunks.length + (rtxremix$blockEntityChunks.length >> 1));
                }
                rtxremix$blockEntityChunks[blockEntityChunks++] = chunk;
            }
        }
        if (blockEntityChunks < rtxremix$blockEntityChunkBufferUsed) {
            Arrays.fill(rtxremix$blockEntityChunks, blockEntityChunks, rtxremix$blockEntityChunkBufferUsed, null);
        }
        rtxremix$blockEntityChunkBufferUsed = blockEntityChunks;
        ProfileScope.plot("Java.WorldRenderer.blockEntityChunks", blockEntityChunks);
        ProfileScope.plot("Java.WorldRenderer.blockEntityChunkScan", visibleChunks);
        rtxremix$blockEntityChunkScanCount = visibleChunks;
        rtxremix$blockEntityChunkCount = blockEntityChunks;
        return rtxremix$blockEntityChunkIterator.reset(rtxremix$blockEntityChunks, blockEntityChunks);
    }

    // 1.21.11: setupTerrain became updateCamera(Camera, Frustum, boolean).
    @Inject(method = "updateCamera", at = @At("HEAD"))
    private void rtxremix$setupTerrainHead(CallbackInfo ci) {
        rtxremix$openJfrOnly(
                rtxremix$setupTerrainScope,
                "WorldRenderer.setupTerrain",
                "Java.WorldRenderer.setupTerrain.ms");
    }

    @Inject(method = "updateCamera", at = @At("RETURN"))
    private void rtxremix$setupTerrainReturn(CallbackInfo ci) {
        rtxremix$close(rtxremix$setupTerrainScope);
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void rtxremix$updateChunksHead(Camera camera, CallbackInfo ci) {
        int installCount = rtxremix$chunksToUpload();
        int dirtyLeaves = rtxremix$chunksToBatch();
        if (installCount >= 0) {
            ProfileScope.plot("Java.Chunk.toUploadCount", installCount);
        }
        if (dirtyLeaves >= 0) {
            ProfileScope.plot("Java.Chunk.toBatchCount", dirtyLeaves);
        }
        rtxremix$open(
                rtxremix$updateChunksScope,
                "WorldRenderer.updateChunks",
                installCount,
                dirtyLeaves);
    }

    /** Vanilla's builder is a local of updateChunks; captured at construction, released at return. */
    private ChunkRendererRegionBuilder rtxremix$frameRegionBuilder;

    @WrapOperation(
            method = "updateChunks",
            at = @At(value = "NEW", target = "()Lnet/minecraft/client/render/chunk/ChunkRendererRegionBuilder;"))
    private ChunkRendererRegionBuilder rtxremix$captureFrameRegionBuilder(Operation<ChunkRendererRegionBuilder> original) {
        ChunkRendererRegionBuilder builder = original.call();
        rtxremix$frameRegionBuilder = builder;
        return builder;
    }

    /**
     * Drops the builder's references so the snapshots it cached can be recycled once their
     * rebuild tasks have run. See {@link ChunkRendererRegionBuilderMixin}.
     */
    @Inject(method = "updateChunks", at = @At("RETURN"))
    private void rtxremix$updateChunksReturn(Camera camera, CallbackInfo ci) {
        ChunkRendererRegionBuilder builder = rtxremix$frameRegionBuilder;
        rtxremix$frameRegionBuilder = null;
        if (builder != null) {
            ((RegionBuilderRelease) builder).rtxremix$releaseSnapshots();
        }
        rtxremix$close(rtxremix$updateChunksScope);
    }

    @Inject(
            method = "updateChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;rebuild(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;Lnet/minecraft/client/render/chunk/ChunkRendererRegionBuilder;)V"
            ),
            require = 0)
    private void rtxremix$beforeSyncRebuild(Camera camera, CallbackInfo ci) {
        rtxremix$open(
                rtxremix$syncRebuildScope,
                "WorldRenderer.updateChunks.build_near_sync",
                -1,
                rtxremix$chunksToBatch());
    }

    @Inject(
            method = "updateChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;rebuild(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;Lnet/minecraft/client/render/chunk/ChunkRendererRegionBuilder;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0)
    private void rtxremix$afterSyncRebuild(Camera camera, CallbackInfo ci) {
        rtxremix$close(rtxremix$syncRebuildScope);
    }

    @Inject(
            method = "updateChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;upload()V"
            ),
            require = 0)
    private void rtxremix$beforeChunkUpload(Camera camera, CallbackInfo ci) {
        rtxremix$open(
                rtxremix$uploadScope,
                "WorldRenderer.updateChunks.upload",
                rtxremix$chunksToUpload(),
                -1);
    }

    @Inject(
            method = "updateChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;upload()V",
                    shift = At.Shift.AFTER
            ),
            require = 0)
    private void rtxremix$afterChunkUpload(Camera camera, CallbackInfo ci) {
        rtxremix$close(rtxremix$uploadScope);
    }

    private int rtxremix$chunksToUpload() {
        return chunkBuilder == null ? -1 : chunkBuilder.getChunksToUpload();
    }

    private int rtxremix$chunksToBatch() {
        return chunkBuilder == null ? -1 : chunkBuilder.getScheduledTaskCount();
    }

    private void rtxremix$transitionProfilerPhase(String vanillaPhaseName) {
        if (!ProfileScope.isDeepProfilingEnabled()) {
            rtxremix$closeProfilerPhase();
            return;
        }
        rtxremix$closeProfilerPhase();
        rtxremix$profilerPhaseName = "WorldRenderer.phase." + rtxremix$sanitizePhase(vanillaPhaseName);
        rtxremix$phaseRenderLayerCallsStart = rtxremix$renderLayerCalls;
        rtxremix$phaseRenderLayerSkipsStart = rtxremix$renderLayerSkips;
        rtxremix$profilerPhaseScope = ProfileScope.begin(rtxremix$profilerPhaseName)
                .plotOnClose("Java." + rtxremix$profilerPhaseName + ".ms");
    }

    private void rtxremix$closeProfilerPhase() {
        ProfileScope scope = rtxremix$profilerPhaseScope;
        if (scope == null) {
            return;
        }
        rtxremix$profilerPhaseScope = null;
        rtxremix$annotateProfilerPhase(scope);
        scope.close();
    }

    @Override
    public void rtxremix$closeProfilerPhaseForFabricEvent() {
        rtxremix$closeProfilerPhase();
    }

    private void rtxremix$annotateProfilerPhase(ProfileScope scope) {
        if (builtChunks != null) {
            int visibleChunks = builtChunks.size();
            scope.visibleChunkCount(visibleChunks);
            scope.captureCount(visibleChunks);
        }
        // TODO(1.21.11): regularEntityCount, blockEntityCount and noCullingBlockEntities were
        // removed from WorldRenderer; those counters report -1.
        scope.entityCount(-1);
        scope.blockEntityCount(-1);
        scope.blockCount(-1);
        scope.noCullBlockEntityCount(-1);
        scope.blockBreakingCount(blockBreakingProgressions == null ? -1 : blockBreakingProgressions.size());
        scope.renderLayerCalls(rtxremix$renderLayerCalls - rtxremix$phaseRenderLayerCallsStart);
        scope.renderLayerSkips(rtxremix$renderLayerSkips - rtxremix$phaseRenderLayerSkipsStart);
        if ("WorldRenderer.phase.blockentities".equals(rtxremix$profilerPhaseName)) {
            scope.scannedChunkCount(rtxremix$blockEntityChunkScanCount);
            scope.blockEntityChunkCount(rtxremix$blockEntityChunkCount);
        }
    }

    private static String rtxremix$sanitizePhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return "unknown";
        }
        return phase.replace(' ', '_');
    }

    private static void rtxremix$openJfrOnly(
            ThreadLocal<ArrayDeque<ProfileScope>> holder,
            String phaseName,
            String plotName) {
        if (!ProfileScope.isDeepProfilingEnabled()) {
            return;
        }
        rtxremix$push(holder, ProfileScope.beginJfrOnly(phaseName).plotOnClose(plotName));
    }

    private static void rtxremix$open(
            ThreadLocal<ArrayDeque<ProfileScope>> holder,
            String phaseName,
            int installCount,
            int dirtyLeaves) {
        if (!ProfileScope.isDeepProfilingEnabled()) {
            return;
        }
        ProfileScope scope = ProfileScope.begin(phaseName);
        if (installCount >= 0) {
            scope.installCount(installCount);
        }
        if (dirtyLeaves >= 0) {
            scope.dirtyLeaves(dirtyLeaves);
        }
        rtxremix$push(holder, scope);
    }

    private static void rtxremix$push(
            ThreadLocal<ArrayDeque<ProfileScope>> holder,
            ProfileScope scope) {
        ArrayDeque<ProfileScope> stack = holder.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            holder.set(stack);
        }
        stack.push(scope);
    }

    private static void rtxremix$close(ThreadLocal<ArrayDeque<ProfileScope>> holder) {
        if (!ProfileScope.isDeepProfilingEnabled()) {
            return;
        }
        ArrayDeque<ProfileScope> stack = holder.get();
        if (stack != null && !stack.isEmpty()) {
            ProfileScope scope = stack.pop();
            if (stack.isEmpty()) {
                holder.remove();
            }
            scope.close();
        } else if (stack != null) {
            holder.remove();
        }
    }

    private static RemixWeatherRenderer rtxremix$getWeatherRenderer() {
        if (!RtxRemixMod.isRemixWorldRendering()) {
            return null;
        }
        return RtxRemixMod.getInstance().getRenderer().getWeatherRenderer();
    }
}
