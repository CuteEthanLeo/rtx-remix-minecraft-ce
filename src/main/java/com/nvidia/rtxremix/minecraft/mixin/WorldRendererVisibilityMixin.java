/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.visibility.VisibilityGraphBuffers;
import com.nvidia.rtxremix.minecraft.terrain.visibility.TimeSlicedWorkQueue;
import com.nvidia.rtxremix.minecraft.terrain.visibility.HierarchicalFrustumCuller;
import com.nvidia.rtxremix.minecraft.terrain.visibility.FarChunkProbe;
import com.nvidia.rtxremix.minecraft.terrain.visibility.ChunkSlotMoveLog;
import com.nvidia.rtxremix.minecraft.terrain.visibility.ChunkInfoPool;
import com.nvidia.rtxremix.minecraft.terrain.visibility.BuiltChunkFrustumIndexBuild;
import com.nvidia.rtxremix.minecraft.terrain.visibility.BuiltChunkFrustumIndex;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.google.common.collect.Queues;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.WorldRenderer.ChunkInfo;
import net.minecraft.client.render.WorldRenderer.ChunkInfoList;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Takes over vanilla's chunk visibility preparation, which this Minecraft version keeps
 * inside {@code WorldRenderer}, on three fronts: it runs the full visibility walk on a
 * dedicated low-priority executor so long view distances cannot starve terrain capture or
 * the render thread, it builds and publishes the coarse {@code BuiltChunkFrustumIndex} that
 * bulk-accepts or rejects section groups before any exact per-chunk frustum test, and it
 * time-slices vanilla's incremental propagation on the render thread through a per-frame
 * budget so a chunk-load burst cannot stall a frame.
 *
 * <p>It also labels each phase for the profiler.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererVisibilityMixin {
    @Shadow
    @Final
    private AtomicReference<WorldRenderer.RenderableChunks> renderableChunks;

    /** The frustum-filtered sections vanilla draws and schedules from, in graph order. */
    @Shadow
    @Final
    private ObjectArrayList<ChunkInfo> chunkInfos;

    /** Vanilla's neighbour order; the walk must visit directions in this order. */
    @Shadow
    @Final
    private static Direction[] DIRECTIONS;

    /** Ray-march step of vanilla's far-chunk probe, {@code ceil(sqrt(3) * 16)} blocks. */
    @Shadow
    @Final
    private static double field_34814;

    @Shadow
    @Nullable
    private BuiltChunkStorage chunks;

    @Shadow
    @Nullable
    private ClientWorld world;

    @Shadow
    @Final
    private MinecraftClient client;

    /** When vanilla re-runs the full walk after reaching a section that is not built yet. */
    @Shadow
    @Final
    private AtomicLong nextUpdateTime;

    /** Vanilla's neighbour lookup: null beyond the view distance or the world's height. */
    @Shadow
    @Nullable
    protected abstract ChunkBuilder.BuiltChunk getAdjacentChunk(
            BlockPos cameraSectionOrigin, ChunkBuilder.BuiltChunk chunk, Direction direction);

    /** Vanilla's horizontal view-distance test between the camera section and a section. */
    @Shadow
    protected abstract boolean isOutsideViewDistance(BlockPos cameraSectionOrigin, ChunkBuilder.BuiltChunk chunk);

    /**
     * {@code -Drtxremix.vanillaVisibilityWalk=true} leaves the visibility walk to vanilla with
     * RTX on. The port below must produce vanilla's visible set exactly; this is how to check
     * that on a live world, together with {@code -Drtxremix.logVisibleSet=true}, which logs
     * the size and a position hash of every walk's result whenever it changes. Run the
     * same camera with and without the switch and compare the last line each logged.
     */
    private static final boolean rtxremix$VANILLA_WALK = Boolean.getBoolean("rtxremix.vanillaVisibilityWalk");
    private static final boolean rtxremix$LOG_VISIBLE_SET = Boolean.getBoolean("rtxremix.logVisibleSet");
    private static long rtxremix$lastVisibleSetSignature;
    private static final ExecutorService rtxremix$visibilityExecutor =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "RTX Remix chunk visibility");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
                return thread;
            });

    private static final ThreadLocal<BuiltChunkFrustumIndexBuild> rtxremix$frustumIndexBuild =
            new ThreadLocal<>();

    private volatile BuiltChunkFrustumIndex rtxremix$frustumIndex;

    /**
     * Cullers taken back from retired indices, for the next build to refill. Two suffice: one
     * behind the published index, one being filled. Offered by whichever side wins the retire
     * (see {@link BuiltChunkFrustumIndex#tryRetire}), polled by the visibility thread.
     */
    private final ConcurrentLinkedQueue<HierarchicalFrustumCuller<ChunkInfo>> rtxremix$cullerPool =
            new ConcurrentLinkedQueue<>();

    /**
     * Per-frame budget for vanilla's incremental visibility propagation. After a chunk-load
     * burst the propagation can seed a walk of thousands of sections; unbudgeted, one such
     * walk cost 25 ms on the render thread. The poll floor keeps the fringe advancing even
     * when the frame is already late.
     */
    private static final long rtxremix$PROPAGATION_BUDGET_NANOS = 1_500_000L;
    private static final int rtxremix$PROPAGATION_MINIMUM_POLLS = 64;

    /**
     * Seeds and partially walked sections carried across frames. Its entries belong to the
     * visibility state that produced them, so a publish or a storage swap empties it.
     */
    private final TimeSlicedWorkQueue<Object> rtxremix$propagationQueue = new TimeSlicedWorkQueue<>(
            rtxremix$PROPAGATION_BUDGET_NANOS, rtxremix$PROPAGATION_MINIMUM_POLLS, System::nanoTime);
    /** The visibility state whose entries {@link #rtxremix$propagationQueue} holds. */
    private Object rtxremix$propagationState;

    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$frustumScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$graphScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$seedScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$walkScope = new ThreadLocal<>();

    /**
     * The storage was replaced and vanilla is about to build its initial empty state, which
     * takes the current buffers: size them for the new storage first. Vanilla has already
     * waited for any walk still running against the old storage.
     */
    @Inject(
            method = "reload()V",
            at = @At(value = "NEW", target = "(I)Lnet/minecraft/client/render/WorldRenderer$RenderableChunks;"))
    private void rtxremix$storageReplaced(CallbackInfo ci) {
        VisibilityGraphBuffers.configure(chunks.chunks.length);
        rtxremix$dropStorageState();
    }

    @Inject(method = "setWorld", at = @At("RETURN"))
    private void rtxremix$storageRemoved(ClientWorld newWorld, CallbackInfo ci) {
        if (newWorld == null) {
            VisibilityGraphBuffers.configure(0);
            rtxremix$dropStorageState();
        }
    }

    private void rtxremix$dropStorageState() {
        BuiltChunkFrustumIndex retired = rtxremix$frustumIndex;
        rtxremix$frustumIndex = null;
        if (retired != null) {
            retired.tryRetire();
        }
        // Capacities were sized for the old storage; let the next builds grow fresh.
        rtxremix$cullerPool.clear();
        rtxremix$frustumIndexBuild.remove();
        rtxremix$propagationQueue.clear();
        rtxremix$propagationState = null;
    }

    @Inject(method = "applyFrustum", at = @At("HEAD"), cancellable = true)
    private void rtxremix$frustumHead(Frustum frustum, CallbackInfo ci) {
        rtxremix$open(rtxremix$frustumScope, "ChunkVisibility.applyFrustum", chunkInfos.size());
        // Off the render thread vanilla throws; leave that to vanilla.
        if (RtxRemixMod.isRtxEnabled() && client.isOnThread() && rtxremix$applyFrustumIndex(frustum)) {
            rtxremix$captureCount(rtxremix$frustumScope, chunkInfos.size());
            rtxremix$close(rtxremix$frustumScope);
            ci.cancel();
        }
    }

    @Inject(method = "applyFrustum", at = @At("RETURN"))
    private void rtxremix$frustumReturn(Frustum frustum, CallbackInfo ci) {
        rtxremix$captureCount(rtxremix$frustumScope, chunkInfos.size());
        rtxremix$close(rtxremix$frustumScope);
    }

    @Redirect(
            method = "setupTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;getMainWorkerExecutor()Ljava/util/concurrent/ExecutorService;"
            ))
    private ExecutorService rtxremix$visibilityExecutor() {
        return RtxRemixMod.isRtxEnabled()
                ? rtxremix$visibilityExecutor
                : Util.getMainWorkerExecutor();
    }

    /**
     * A full walk is about to construct its state: hand it the other buffer set. Runs for
     * vanilla's walk too, since the reused set and list are what its state is made of.
     */
    @Inject(method = "method_38548", at = @At("HEAD"))
    private void rtxremix$beginFrustumIndex(
            Camera camera,
            Vec3d cameraPos,
            boolean culling,
            CallbackInfo ci) {
        VisibilityGraphBuffers.rotate();
        if (RtxRemixMod.isRtxEnabled()) {
            rtxremix$frustumIndexBuild.set(new BuiltChunkFrustumIndexBuild(
                    ChunkSlotMoveLog.RENDER_THREAD.sequence(), rtxremix$cullerPool.poll()));
        } else {
            rtxremix$frustumIndexBuild.remove();
            rtxremix$frustumIndex = null;
        }
    }

    // The full walk's only AtomicReference write publishes its state.
    @Redirect(
            method = "method_38548",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/atomic/AtomicReference;set(Ljava/lang/Object;)V"
            ))
    private void rtxremix$publishFrustumIndex(
            AtomicReference<Object> stateReference,
            Object state) {
        BuiltChunkFrustumIndexBuild build = rtxremix$frustumIndexBuild.get();
        rtxremix$frustumIndexBuild.remove();
        // Published even when slots were re-originned during the walk: the walk takes tens of
        // milliseconds, and discarding a build that saw a move would leave every chunk on
        // vanilla's exact per-chunk test for several frames.
        //
        // Reconciled from the move sequence the build STARTED at, never the current one:
        // anything that moved mid-walk is indexed against bounds that are already stale, so the
        // first apply must replay those moves. Starting from the current sequence would skip
        // them and silently drop the moved geometry.
        BuiltChunkFrustumIndex superseded = rtxremix$frustumIndex;
        rtxremix$frustumIndex = build != null ? build.publish(state) : null;
        stateReference.set(state);
        rtxremix$recycle(superseded);
    }

    /**
     * Returns a superseded index's culler to the pool unless the render thread is inside a use
     * of it, in which case that use retires it when it ends.
     */
    private void rtxremix$recycle(BuiltChunkFrustumIndex superseded) {
        if (superseded == null) {
            return;
        }
        HierarchicalFrustumCuller<ChunkInfo> culler = superseded.tryRetire();
        if (culler != null && rtxremix$cullerPool.size() < 2) {
            rtxremix$cullerPool.offer(culler);
        }
    }

    /** Ends a render-thread use; recycles the index if a publish superseded it meanwhile. */
    private void rtxremix$releaseIndex(BuiltChunkFrustumIndex index) {
        index.release();
        if (rtxremix$frustumIndex != index) {
            rtxremix$recycle(index);
        }
    }

    @Redirect(
            method = "collectRenderableChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/LinkedHashSet;add(Ljava/lang/Object;)Z"
            ))
    private boolean rtxremix$indexVisibleChunk(LinkedHashSet<ChunkInfo> chunks, Object chunkInfo) {
        return rtxremix$visit(chunks, (ChunkInfo) chunkInfo);
    }

    private boolean rtxremix$visit(LinkedHashSet<ChunkInfo> chunks, ChunkInfo chunkInfo) {
        if (!chunks.add(chunkInfo)) {
            return false;
        }

        BuiltChunkFrustumIndexBuild build = rtxremix$frustumIndexBuild.get();
        if (build != null) {
            build.add(chunks, chunkInfo);
            return true;
        }

        // Indexed unconditionally once the set matches: the entry is built from the chunk's
        // current bounds, so it is accurate regardless of what moved earlier. Gating this on the
        // origin epoch would silently leave the chunk out of an index that the apply path now
        // reconciles and keeps using, and an unindexed chunk is never drawn.
        BuiltChunkFrustumIndex index = rtxremix$frustumIndex;
        if (index != null && index.chunks() == chunks && index.tryAcquire()) {
            try {
                index.add(chunkInfo);
            } finally {
                rtxremix$releaseIndex(index);
            }
        }
        return true;
    }

    /**
     * Leftover propagation entries index into the state that produced them; a publish
     * invalidates them. This is the read that loads the state vanilla propagates into for
     * this frame, so tagging the leftovers here keeps them with exactly that state.
     * Re-reading the published state later could observe a newer one.
     */
    @Redirect(
            method = "setupTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/atomic/AtomicReference;get()Ljava/lang/Object;"
            ))
    private Object rtxremix$loadPropagationState(AtomicReference<?> stateReference) {
        Object state = stateReference.get();
        if (rtxremix$propagationState != state) {
            rtxremix$propagationQueue.clear();
            rtxremix$propagationState = state;
        }
        return state;
    }

    // Vanilla only propagates when sections finished building. Sections left over from a
    // budgeted slice must keep draining even on frames with none.
    @Redirect(
            method = "setupTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/BlockingQueue;isEmpty()Z",
                    ordinal = 0
            ))
    private boolean rtxremix$noPropagationWork(BlockingQueue<?> seeds) {
        if (!RtxRemixMod.isRtxEnabled()) {
            return seeds.isEmpty();
        }
        return seeds.isEmpty() && !rtxremix$propagationQueue.hasPendingWork();
    }

    // Vanilla drains the seeds into a fresh deque and walks it to completion. Handing it the
    // persistent sliced queue instead bounds the walk to this frame's budget and resumes it
    // from the same entries next frame.
    @Redirect(
            method = "setupTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Queues;newArrayDeque()Ljava/util/ArrayDeque;"
            ))
    private ArrayDeque<Object> rtxremix$propagationWorkQueue() {
        rtxremix$open(rtxremix$graphScope, "ChunkVisibility.rebuildGraph", chunkInfos.size());
        if (!RtxRemixMod.isRtxEnabled()) {
            rtxremix$propagationQueue.clear();
            return Queues.newArrayDeque();
        }
        rtxremix$propagationQueue.beginSlice();
        return rtxremix$propagationQueue;
    }

    @Inject(
            method = "setupTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;collectRenderableChunks("
                            + "Ljava/util/LinkedHashSet;"
                            + "Lnet/minecraft/client/render/WorldRenderer$ChunkInfoList;"
                            + "Lnet/minecraft/util/math/Vec3d;Ljava/util/Queue;Z)V",
                    shift = At.Shift.AFTER
            ))
    private void rtxremix$propagationReturn(
            Camera camera,
            Frustum frustum,
            boolean hasForcedFrustum,
            boolean spectator,
            CallbackInfo ci) {
        rtxremix$budgetHit(rtxremix$graphScope, rtxremix$propagationQueue.budgetHit());
        rtxremix$close(rtxremix$graphScope);
    }

    @Inject(method = "enqueueChunksInViewDistance", at = @At("HEAD"))
    private void rtxremix$seedHead(
            Camera camera,
            Queue<?> queue,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$seedScope, "ChunkVisibility.seedQueue", rtxremix$sizeOf(queue));
    }

    @Inject(method = "enqueueChunksInViewDistance", at = @At("RETURN"))
    private void rtxremix$seedReturn(
            Camera camera,
            Queue<?> queue,
            CallbackInfo ci) {
        rtxremix$close(rtxremix$seedScope);
    }

    /**
     * Vanilla's walk allocates on every step of its far-chunk probe: for each neighbour more
     * than 60 blocks from the camera it ray-marches from the neighbour's near corner back
     * toward the camera in {@link #field_34814}-block steps, creating a Vec3d for the remaining
     * offset, a Vec3d for the advanced point and a BlockPos for the lookup each time. At render
     * distance 32 that is most of the garbage the collector cleans up while the game runs, and
     * the collector's concurrent work is what stretches frames. This port keeps vanilla's
     * traversal, arithmetic and evaluation order and holds the ray in doubles and one mutable
     * position, so the visible set is identical and the walk allocates only graph nodes.
     */
    @Inject(method = "collectRenderableChunks", at = @At("HEAD"), cancellable = true)
    private void rtxremix$walkVisibleGraph(
            LinkedHashSet<ChunkInfo> chunks,
            ChunkInfoList infos,
            Vec3d cameraPos,
            Queue<ChunkInfo> queue,
            boolean culling,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$walkScope, "ChunkVisibility.walkVisibleGraph", rtxremix$sizeOf(queue));
        if (!RtxRemixMod.isRtxEnabled() || rtxremix$VANILLA_WALK) {
            return;
        }
        try {
            rtxremix$walk(chunks, infos, cameraPos, queue, culling);
        } finally {
            rtxremix$close(rtxremix$walkScope);
        }
        if (rtxremix$LOG_VISIBLE_SET) rtxremix$logVisibleSet("port", chunks);
        ci.cancel();
    }

    @Inject(method = "collectRenderableChunks", at = @At("RETURN"))
    private void rtxremix$walkReturn(
            LinkedHashSet<ChunkInfo> chunks,
            ChunkInfoList infos,
            Vec3d cameraPos,
            Queue<ChunkInfo> queue,
            boolean culling,
            CallbackInfo ci) {
        rtxremix$close(rtxremix$walkScope);
        if (rtxremix$LOG_VISIBLE_SET) rtxremix$logVisibleSet("vanilla", chunks);
    }

    /** Order-independent signature of the visible set: size and a hash of every section origin. */
    private static void rtxremix$logVisibleSet(String walk, LinkedHashSet<ChunkInfo> chunks) {
        long hash = 0;
        for (ChunkInfo info : chunks) {
            hash += (info.chunk.getOrigin().asLong() * 0x9E3779B97F4A7C15L) ^ 0x632BE59BD9B4E019L;
        }
        long signature = hash ^ ((long) chunks.size() << 48);
        if (signature != rtxremix$lastVisibleSetSignature) {
            rtxremix$lastVisibleSetSignature = signature;
            RtxRemixMod.LOGGER.info("Visible set ({} walk): {} sections, hash {}",
                    walk, chunks.size(), Long.toHexString(hash));
        }
    }

    private void rtxremix$walk(
            LinkedHashSet<ChunkInfo> chunks,
            ChunkInfoList infos,
            Vec3d cameraPos,
            Queue<ChunkInfo> queue,
            boolean culling) {
        BuiltChunkStorage storage = this.chunks;
        ClientWorld world = this.world;
        ChunkInfoPool infoPool = VisibilityGraphBuffers.poolFor(infos);
        Direction[] directions = DIRECTIONS;
        BlockPos cameraSectionOrigin = new BlockPos(
                MathHelper.floor(cameraPos.x / 16.0) * 16,
                MathHelper.floor(cameraPos.y / 16.0) * 16,
                MathHelper.floor(cameraPos.z / 16.0) * 16);
        int cameraSectionCenterX = cameraSectionOrigin.getX() + 8;
        int cameraSectionCenterY = cameraSectionOrigin.getY() + 8;
        int cameraSectionCenterZ = cameraSectionOrigin.getZ() + 8;
        // Vanilla refreshes the entity render distance at the top of every walk.
        Entity.setRenderDistanceMultiplier(
                MathHelper.clamp(client.options.getClampedViewDistance() / 8.0, 1.0, 2.5)
                        * client.options.getEntityDistanceScaling().getValue());
        // One position serves every probed step of every probe in the walk, where vanilla
        // allocated a fresh BlockPos per step. Sound while getRenderedChunk only derives
        // array indices from the position it is handed; a lookup that kept or keyed by it
        // would alias every probe to the last step.
        BlockPos.Mutable probe = new BlockPos.Mutable();
        FarChunkProbe.GraphLookup graph = (x, y, z) -> {
            ChunkBuilder.BuiltChunk passed = storage.getRenderedChunk(probe.set(x, y, z));
            return passed != null && infos.getInfo(passed) != null;
        };

        while (!queue.isEmpty()) {
            ChunkInfo chunkInfo = queue.poll();
            ChunkBuilder.BuiltChunk chunk = chunkInfo.chunk;
            rtxremix$visit(chunks, chunkInfo);
            BlockPos origin = chunk.getOrigin();
            boolean far = Math.abs(origin.getX() - cameraSectionOrigin.getX()) > 60
                    || Math.abs(origin.getY() - cameraSectionOrigin.getY()) > 60
                    || Math.abs(origin.getZ() - cameraSectionOrigin.getZ()) > 60;

            for (Direction direction : directions) {
                ChunkBuilder.BuiltChunk neighbour = getAdjacentChunk(cameraSectionOrigin, chunk, direction);
                if (neighbour == null) {
                    continue;
                }
                if (culling && chunkInfo.canCull(direction.getOpposite())) {
                    continue;
                }
                if (culling && chunkInfo.hasAnyDirection()) {
                    ChunkBuilder.ChunkData data = chunk.getData();
                    boolean visibleThrough = false;
                    for (int i = 0; i < directions.length; i++) {
                        if (chunkInfo.hasDirection(i)
                                && data.isVisibleThrough(directions[i].getOpposite(), direction)) {
                            visibleThrough = true;
                            break;
                        }
                    }
                    if (!visibleThrough) {
                        continue;
                    }
                }
                if (culling && far) {
                    BlockPos neighbourOrigin = neighbour.getOrigin();
                    boolean reaches = FarChunkProbe.reachesCamera(
                            neighbourOrigin.getX(), neighbourOrigin.getY(), neighbourOrigin.getZ(),
                            direction.getAxis(),
                            cameraSectionCenterX, cameraSectionCenterY, cameraSectionCenterZ,
                            cameraPos.x, cameraPos.y, cameraPos.z,
                            field_34814,
                            world.getTopY(), world.getBottomY(),
                            graph);
                    if (!reaches) {
                        continue;
                    }
                }

                ChunkInfo neighbourInfo = infos.getInfo(neighbour);
                if (neighbourInfo != null) {
                    neighbourInfo.addDirection(direction);
                } else if (!neighbour.shouldBuild()) {
                    // A section whose neighbours are not loaded yet joins the graph on a
                    // later full walk, which vanilla schedules here.
                    if (!isOutsideViewDistance(cameraSectionOrigin, chunk)) {
                        nextUpdateTime.set(System.currentTimeMillis() + 500L);
                    }
                } else {
                    ChunkInfo discovered = infoPool != null
                            ? infoPool.acquire(neighbour, direction, chunkInfo.propagationLevel + 1)
                            : new ChunkInfo(neighbour, direction, chunkInfo.propagationLevel + 1);
                    discovered.updateCullingState(chunkInfo.cullingState, direction);
                    queue.add(discovered);
                    infos.setInfo(neighbour, discovered);
                }
            }
        }
    }

    private static int rtxremix$sizeOf(Queue<?> values) {
        return values == null ? -1 : values.size();
    }

    private static void rtxremix$open(ThreadLocal<ArrayDeque<ProfileScope>> holder, String phaseName, int captureCount) {
        if (!ProfileScope.isDeepProfilingEnabled()) {
            return;
        }
        ArrayDeque<ProfileScope> stack = holder.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            holder.set(stack);
        }
        ProfileScope scope = ProfileScope.begin(phaseName);
        if (captureCount >= 0) {
            scope.captureCount(captureCount);
        }
        stack.push(scope);
    }

    private static void rtxremix$captureCount(ThreadLocal<ArrayDeque<ProfileScope>> holder, int captureCount) {
        if (!ProfileScope.isDeepProfilingEnabled() || captureCount < 0) {
            return;
        }
        ArrayDeque<ProfileScope> stack = holder.get();
        if (stack != null && !stack.isEmpty()) {
            stack.peek().captureCount(captureCount);
        }
    }

    private static void rtxremix$scannedChunkCount(ThreadLocal<ArrayDeque<ProfileScope>> holder, int scanned) {
        if (!ProfileScope.isDeepProfilingEnabled() || scanned < 0) {
            return;
        }
        ArrayDeque<ProfileScope> stack = holder.get();
        if (stack != null && !stack.isEmpty()) {
            stack.peek().scannedChunkCount(scanned);
        }
    }

    private static void rtxremix$budgetHit(ThreadLocal<ArrayDeque<ProfileScope>> holder, boolean budgetHit) {
        if (!ProfileScope.isDeepProfilingEnabled() || !budgetHit) {
            return;
        }
        ArrayDeque<ProfileScope> stack = holder.get();
        if (stack != null && !stack.isEmpty()) {
            stack.peek().budgetHit(true);
        }
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

    /**
     * Fills vanilla's frustum-filtered list from the coarse index, or returns false, leaving
     * the list untouched, when no index matches the published state.
     */
    private boolean rtxremix$applyFrustumIndex(Frustum frustum) {
        BuiltChunkFrustumIndex index = rtxremix$frustumIndex;
        if (index == null || index.state() != renderableChunks.get() || !index.tryAcquire()) {
            return false;
        }
        try {
            chunkInfos.clear();
            // Slots re-originned since this index was last reconciled may have left their
            // cached cells; the index re-buckets exactly those sections before it is queried.
            index.reconcile(ChunkSlotMoveLog.RENDER_THREAD);
            rtxremix$scannedChunkCount(rtxremix$frustumScope, index.size());

            FrustumAccessor accessor = (FrustumAccessor) frustum;
            FrustumIntersection intersection = accessor.rtxremix$getFrustumIntersection();
            double frustumX = accessor.rtxremix$getX();
            double frustumY = accessor.rtxremix$getY();
            double frustumZ = accessor.rtxremix$getZ();
            index.forEachVisible(
                    (minX, minY, minZ, maxX, maxY, maxZ) -> {
                        int classification = intersection.intersectAab(
                                (float) (minX - frustumX),
                                (float) (minY - frustumY),
                                (float) (minZ - frustumZ),
                                (float) (maxX - frustumX),
                                (float) (maxY - frustumY),
                                (float) (maxZ - frustumZ));
                        if (classification == FrustumIntersection.INSIDE) {
                            return HierarchicalFrustumCuller.INSIDE;
                        }
                        if (classification == FrustumIntersection.INTERSECT) {
                            return HierarchicalFrustumCuller.INTERSECT;
                        }
                        return HierarchicalFrustumCuller.OUTSIDE;
                    },
                    info -> frustum.isVisible(info.chunk.getBoundingBox()),
                    chunkInfos::add);
            return true;
        } finally {
            rtxremix$releaseIndex(index);
        }
    }
}
