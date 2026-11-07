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
import com.nvidia.rtxremix.minecraft.LevelExtractorAccess;
import com.nvidia.rtxremix.minecraft.RemixRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

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
@Mixin(LevelRenderer.class)
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
    private SectionRenderDispatcher sectionRenderDispatcher;

    @Shadow
    @Nullable
    private ViewArea viewArea;

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Shadow
    @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    private ProfileScope rtxremix$profilerPhaseScope;
    private String rtxremix$profilerPhaseName;
    private int rtxremix$renderLayerCalls;
    private int rtxremix$renderLayerSkips;
    private int rtxremix$phaseRenderLayerCallsStart;
    private int rtxremix$phaseRenderLayerSkipsStart;
    private int rtxremix$blockEntityChunkScanCount = -1;
    private int rtxremix$blockEntityChunkCount = -1;
    private SectionRenderDispatcher.RenderSection[] rtxremix$blockEntityChunks =
            new SectionRenderDispatcher.RenderSection[rtxremix$BLOCK_ENTITY_CHUNK_LIST_INITIAL_CAPACITY];
    private int rtxremix$blockEntityChunkBufferUsed;
    private final BuiltChunkArrayIterator rtxremix$blockEntityChunkIterator = new BuiltChunkArrayIterator();
    @Shadow
    private net.minecraft.client.renderer.SkyRenderer skyRenderer;

    @Override
    public int rtxremix$getCloudTicks() {
        // 26.2: vanilla clouds run on the extracted game time instead of a renderer tick counter.
        return (int) levelRenderState.gameTime;
    }

    @Override
    public float[] rtxremix$copyStarVertices() {
        return ((WorldRendererStarAccess) skyRenderer).rtxremix$copyStarVertices();
    }

    // 26.2: setBlocksDirty and setSectionDirtyWithNeighbors moved to LevelExtractor; the water
    // occlusion margin and the chunk-load re-flag filter live in LevelExtractorMixin.

    @Override
    public void rtxremix$onNeighbourColumnInvalidated(int cx, int cz) {
        if (viewArea != null) {
            ((BuiltChunkStorageSectionAccess) viewArea).rtxremix$clearNeighbourSnapshotBits(cx, cz);
        }
    }

    @Override
    public LongSet rtxremix$builtSectionPositions() {
        LongOpenHashSet built = new LongOpenHashSet();
        if (viewArea == null) {
            return built;
        }
        for (SectionRenderDispatcher.RenderSection builtChunk : rtxremix$residentSections()) {
            if (builtChunk.getSectionMesh() == CompiledSectionMesh.UNCOMPILED) {
                continue;
            }
            BlockPos origin = builtChunk.getRenderOrigin();
            built.add(SectionPos.asLong(
                    SectionPos.blockToSectionCoord(origin.getX()),
                    SectionPos.blockToSectionCoord(origin.getY()),
                    SectionPos.blockToSectionCoord(origin.getZ())));
        }
        return built;
    }

    @Override
    public LongSet rtxremix$queueSectionsForRemix(LongSet sections) {
        ClientLevel level = Minecraft.getInstance().level;
        if (viewArea == null || sectionRenderDispatcher == null || level == null || sections.isEmpty()) {
            return LongSets.emptySet();
        }

        // Same gate as vanilla updateChunks: a section built before its light
        // is ready would bake bad light, and cancelRebuild below means nothing
        // re-flags it afterwards.
        LevelLightEngine lightingProvider = level.getLightEngine();

        ArrayList<SectionRenderDispatcher.RenderSection> loadedSections = new ArrayList<>();
        for (SectionRenderDispatcher.RenderSection builtChunk : rtxremix$residentSections()) {
            BlockPos origin = builtChunk.getRenderOrigin();
            int cx = SectionPos.blockToSectionCoord(origin.getX());
            int sy = SectionPos.blockToSectionCoord(origin.getY());
            int cz = SectionPos.blockToSectionCoord(origin.getZ());
            if (!sections.contains(SectionPos.asLong(cx, sy, cz))) {
                continue;
            }
            LevelChunk worldChunk = level.getChunkSource().getChunk(cx, cz, false);
            if (worldChunk != null
                    && !worldChunk.getSection(worldChunk.getSectionIndexFromSectionY(sy)).hasOnlyAir()
                    && lightingProvider.lightOnInColumn(SectionPos.asLong(cx, sy, cz))) {
                loadedSections.add(builtChunk);
            }
        }

        // ChunkBuilder's normal task queue is FIFO. Queue nearby sections first
        // so visible holes close promptly while the Remix scene repopulates.
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        double cameraX = camera.position().x;
        double cameraY = camera.position().y;
        double cameraZ = camera.position().z;
        loadedSections.sort(Comparator.comparingDouble(builtChunk -> {
            BlockPos origin = builtChunk.getRenderOrigin();
            double dx = origin.getX() + 8.0 - cameraX;
            double dy = origin.getY() + 8.0 - cameraY;
            double dz = origin.getZ() + 8.0 - cameraZ;
            return dx * dx + dy * dy + dz * dz;
        }));

        RenderRegionCache regionBuilder = new RenderRegionCache();
        LongOpenHashSet queued = new LongOpenHashSet(loadedSections.size());
        LevelExtractorAccess extractor = (LevelExtractorAccess) Minecraft.getInstance().levelExtractor;
        try {
            for (SectionRenderDispatcher.RenderSection builtChunk : loadedSections) {
                builtChunk.compileAsync(regionBuilder.createRegion(level, builtChunk.getSectionNode()));
                // Prevent the next extraction from queueing this same slot a second
                // time when it next walks vanilla's visible section list.
                extractor.rtxremix$setSectionNotDirty(builtChunk.getSectionNode());
                BlockPos origin = builtChunk.getRenderOrigin();
                queued.add(SectionPos.asLong(
                        SectionPos.blockToSectionCoord(origin.getX()),
                        SectionPos.blockToSectionCoord(origin.getY()),
                        SectionPos.blockToSectionCoord(origin.getZ())));
            }
        } finally {
            ((RegionBuilderRelease) regionBuilder).rtxremix$releaseSnapshots();
        }
        return queued;
    }

    @Override
    public void rtxremix$scheduleResidentSectionRebuild(int cx, int sy, int cz) {
        ((BuiltChunkStorageSectionAccess) viewArea).rtxremix$scheduleRebuildIfResident(cx, sy, cz);
    }

    /**
     * Every section slot the view area holds. 26.2 keeps them in a private rotating storage,
     * so the slots are read back through the public per-position lookup.
     */
    private ArrayList<SectionRenderDispatcher.RenderSection> rtxremix$residentSections() {
        ArrayList<SectionRenderDispatcher.RenderSection> resident = new ArrayList<>();
        ViewArea area = viewArea;
        if (area == null) {
            return resident;
        }
        SectionPos center = area.getCameraSectionPos();
        int radius = area.getViewDistance();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int sx = center.x() - radius; sx <= center.x() + radius; sx++) {
            for (int sz = center.z() - radius; sz <= center.z() + radius; sz++) {
                for (int sy = area.minSectionY(); sy <= area.maxSectionY(); sy++) {
                    pos.set(SectionPos.sectionToBlockCoord(sx), SectionPos.sectionToBlockCoord(sy),
                            SectionPos.sectionToBlockCoord(sz));
                    SectionRenderDispatcher.RenderSection section = area.getRenderSectionAt(pos);
                    if (section != null) {
                        resident.add(section);
                    }
                }
            }
        }
        return resident;
    }

    // TODO(1.21.11): WorldRenderer.render no longer calls renderEntity(...); entities are
    // extracted into render states and submitted through OrderedRenderCommandQueue. In-place
    // entity capture (with its outline-only vanilla fallback) is disabled here.

    // TODO(26.2): the block-breaking crack is now submitted as submitBreakingBlockModel from
    // LevelRenderer.submitBlockDestroyAnimation over extracted BlockBreakingRenderStates; the
    // in-place Remix crack capture (RemixRenderer.renderBlockDamageInPlace) is not hooked.

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
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"
            ))
    private void rtxremix$mirrorVanillaProfilerPhase(
            ProfilerFiller profiler,
            String name,
            Operation<Void> original) {
        original.call(profiler, name);
        rtxremix$transitionProfilerPhase(name);
    }

    // TODO(1.21.11): WorldRenderer.renderLayer was replaced by renderBlockLayers, which returns
    // a SectionRenderState drawn later by the frame graph; vanilla terrain layers are no longer
    // skipped while Remix renders the world.

    // 26.2: particles are submitted as features from submitFeatures, not drawn in their own pass.
    @WrapOperation(
            method = "submitFeatures",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;submit(Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
            ))
    private void rtxremix$skipVanillaParticleDraw(
            ParticlesRenderState particles,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            Operation<Void> original) {
        if (!RtxRemixMod.isRemixWorldRendering()) {
            original.call(particles, collector, camera);
        }
    }

    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
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

    // TODO(26.2): block entities are walked in LevelExtractor.extractVisibleBlockEntities; the
    // narrowing to sections that hold block entities is not ported.

    // 26.2: the section occlusion update at the end of LevelRenderer.render stands in for setupTerrain.
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"
            ))
    private void rtxremix$setupTerrainHead(CallbackInfo ci) {
        rtxremix$openJfrOnly(
                rtxremix$setupTerrainScope,
                "WorldRenderer.setupTerrain",
                "Java.WorldRenderer.setupTerrain.ms");
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V",
                    shift = At.Shift.AFTER
            ))
    private void rtxremix$setupTerrainReturn(CallbackInfo ci) {
        rtxremix$close(rtxremix$setupTerrainScope);
    }

    @Inject(method = "compileSections", at = @At("HEAD"))
    private void rtxremix$updateChunksHead(CameraRenderState camera, CallbackInfo ci) {
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

    /**
     * Drops the builder's references so the snapshots it cached can be recycled once their
     * rebuild tasks have run. See {@link ChunkRendererRegionBuilderMixin}.
     */
    @Inject(method = "compileSections", at = @At("RETURN"))
    private void rtxremix$updateChunksReturn(CameraRenderState camera, CallbackInfo ci) {
        // 26.2: LevelExtractor.extract builds the frame's region cache; LevelExtractorMixin holds it.
        RenderRegionCache builder =
                ((LevelExtractorAccess) Minecraft.getInstance().levelExtractor).rtxremix$takeFrameRegionCache();
        if (builder != null) {
            ((RegionBuilderRelease) builder).rtxremix$releaseSnapshots();
        }
        rtxremix$close(rtxremix$updateChunksScope);
    }

    @Inject(
            method = "compileSections",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;compileSync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;)V"
            ),
            require = 0)
    private void rtxremix$beforeSyncRebuild(CameraRenderState camera, CallbackInfo ci) {
        rtxremix$open(
                rtxremix$syncRebuildScope,
                "WorldRenderer.updateChunks.build_near_sync",
                -1,
                rtxremix$chunksToBatch());
    }

    @Inject(
            method = "compileSections",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;compileSync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0)
    private void rtxremix$afterSyncRebuild(CameraRenderState camera, CallbackInfo ci) {
        rtxremix$close(rtxremix$syncRebuildScope);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V"
            ),
            require = 0)
    private void rtxremix$beforeChunkUpload(CallbackInfo ci) {
        rtxremix$open(
                rtxremix$uploadScope,
                "WorldRenderer.updateChunks.upload",
                rtxremix$chunksToUpload(),
                -1);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V",
                    shift = At.Shift.AFTER
            ),
            require = 0)
    private void rtxremix$afterChunkUpload(CallbackInfo ci) {
        rtxremix$close(rtxremix$uploadScope);
    }

    private int rtxremix$chunksToUpload() {
        // TODO(26.2): SectionRenderDispatcher no longer reports a pending-upload count.
        return -1;
    }

    private int rtxremix$chunksToBatch() {
        return sectionRenderDispatcher == null ? -1 : sectionRenderDispatcher.getCompileQueueSize();
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
        if (visibleSections != null) {
            int visibleChunks = visibleSections.size();
            scope.visibleChunkCount(visibleChunks);
            scope.captureCount(visibleChunks);
        }
        // TODO(1.21.11): regularEntityCount, blockEntityCount and noCullingBlockEntities were
        // removed from WorldRenderer; those counters report -1.
        scope.entityCount(-1);
        scope.blockEntityCount(-1);
        scope.blockCount(-1);
        scope.noCullBlockEntityCount(-1);
        ClientLevel level = Minecraft.getInstance().level;
        scope.blockBreakingCount(level == null ? -1 : level.destructionProgress().size());
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
