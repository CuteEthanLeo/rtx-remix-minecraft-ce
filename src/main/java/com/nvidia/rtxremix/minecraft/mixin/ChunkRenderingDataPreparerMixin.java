/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.visibility.TimeSlicedWorkQueue;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.google.common.collect.Queues;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.TracingExecutor;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Profiling, a low-priority visibility executor, and a per-frame propagation budget around
 * vanilla's section visibility graph.
 *
 * <p>TODO(1.21.11): vanilla rewrote the walk around {@code Octree} and section-position longs.
 * The allocation-free port of the walk, its far-chunk probe, and the coarse frustum index that
 * replayed {@code ChunkSlotMoveLog} are disabled; vanilla's walk and octree frustum visit run
 * as is. {@code -Drtxremix.vanillaVisibilityWalk} and {@code -Drtxremix.logVisibleSet} have no
 * effect until the port is redone against the new walk.
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class ChunkRenderingDataPreparerMixin {

    private static final TracingExecutor rtxremix$visibilityExecutor =
            new TracingExecutor(Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "RTX Remix chunk visibility");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
                return thread;
            }));

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
    private TimeSlicedWorkQueue<Object> rtxremix$propagationQueue;
    /** Seed queue of the state whose entries {@link #rtxremix$propagationQueue} holds. */
    private BlockingQueue<?> rtxremix$propagationSeeds;

    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$storageScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$frustumScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$prepareScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$graphScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$seedScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$walkScope = new ThreadLocal<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rtxremix$initializePropagationQueue(CallbackInfo ci) {
        rtxremix$propagationQueue = new TimeSlicedWorkQueue<>(
                rtxremix$PROPAGATION_BUDGET_NANOS, rtxremix$PROPAGATION_MINIMUM_POLLS, System::nanoTime);
    }

    @Inject(method = "waitAndReset", at = @At("HEAD"))
    private void rtxremix$storageHead(ViewArea storage, CallbackInfo ci) {
        rtxremix$open(rtxremix$storageScope, "ChunkVisibility.setStorage");
    }

    @Inject(method = "waitAndReset", at = @At("RETURN"))
    private void rtxremix$storageReturn(ViewArea storage, CallbackInfo ci) {
        rtxremix$propagationQueue.clear();
        rtxremix$propagationSeeds = null;
        rtxremix$close(rtxremix$storageScope);
    }

    @Inject(method = "addSectionsInFrustum", at = @At("HEAD"))
    private void rtxremix$frustumHead(
            Frustum frustum,
            List<SectionRenderDispatcher.RenderSection> builtChunks,
            List<SectionRenderDispatcher.RenderSection> nearbyChunks,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$frustumScope, "ChunkVisibility.applyFrustum", rtxremix$sizeOf(builtChunks));
    }

    @Inject(method = "addSectionsInFrustum", at = @At("RETURN"))
    private void rtxremix$frustumReturn(
            Frustum frustum,
            List<SectionRenderDispatcher.RenderSection> builtChunks,
            List<SectionRenderDispatcher.RenderSection> nearbyChunks,
            CallbackInfo ci) {
        rtxremix$captureCount(rtxremix$frustumScope, rtxremix$sizeOf(builtChunks));
        rtxremix$close(rtxremix$frustumScope);
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void rtxremix$prepareHead(
            CameraRenderState camera,
            int fov,
            ChunkLoadingRenderState chunkLoadingRenderState,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$prepareScope, "ChunkVisibility.prepare", -1);
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void rtxremix$prepareReturn(
            CameraRenderState camera,
            int fov,
            ChunkLoadingRenderState chunkLoadingRenderState,
            CallbackInfo ci) {
        rtxremix$close(rtxremix$prepareScope);
    }

    @Redirect(
            method = "scheduleFullUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;backgroundExecutor()Lnet/minecraft/TracingExecutor;"
            ))
    private TracingExecutor rtxremix$visibilityExecutor() {
        return RtxRemixMod.isRtxEnabled()
                ? rtxremix$visibilityExecutor
                : Util.backgroundExecutor();
    }

    @Inject(method = "runPartialUpdate", at = @At("HEAD"))
    private void rtxremix$graphHead(
            CameraRenderState camera,
            LongSet loadedExpectedChunks,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$graphScope, "ChunkVisibility.rebuildGraph", -1);
    }

    @Inject(method = "runPartialUpdate", at = @At("RETURN"))
    private void rtxremix$graphReturn(
            CameraRenderState camera,
            LongSet loadedExpectedChunks,
            CallbackInfo ci) {
        rtxremix$budgetHit(rtxremix$graphScope, rtxremix$propagationQueue.budgetHit());
        rtxremix$close(rtxremix$graphScope);
    }

    // Vanilla only propagates when new seeds arrived. Sections left over from a budgeted
    // slice must keep draining even on frames with no new seeds.
    @Redirect(
            method = "runPartialUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/BlockingQueue;isEmpty()Z",
                    ordinal = 0
            ))
    private boolean rtxremix$noPropagationWork(BlockingQueue<?> seeds) {
        if (!RtxRemixMod.isRtxEnabled()) {
            return seeds.isEmpty();
        }
        rtxremix$dropForeignPropagationWork(seeds);
        return seeds.isEmpty() && !rtxremix$propagationQueue.hasPendingWork();
    }

    // Vanilla drains the seeds into a fresh deque and walks it to completion. Handing it the
    // persistent sliced queue instead bounds the walk to this frame's budget and resumes it
    // from the same entries next frame.
    @Redirect(
            method = "runPartialUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Queues;newArrayDeque()Ljava/util/ArrayDeque;"
            ))
    private ArrayDeque<Object> rtxremix$propagationWorkQueue() {
        if (!RtxRemixMod.isRtxEnabled()) {
            rtxremix$propagationQueue.clear();
            return Queues.newArrayDeque();
        }
        rtxremix$propagationQueue.beginSlice();
        return rtxremix$propagationQueue;
    }

    /**
     * Leftover entries index into the state that produced them; a publish invalidates them.
     * Each state owns its seed queue, so the queue vanilla is about to drain identifies the
     * state it loaded for this frame. Re-reading the published state here instead could
     * observe a newer one and leave this frame's leftovers tagged as that state's own.
     */
    private void rtxremix$dropForeignPropagationWork(BlockingQueue<?> seeds) {
        if (rtxremix$propagationSeeds != seeds) {
            rtxremix$propagationQueue.clear();
            rtxremix$propagationSeeds = seeds;
        }
    }

    @Inject(method = "initializeQueueForFullUpdate", at = @At("HEAD"))
    private void rtxremix$seedHead(BlockPos cameraPosition, Queue<?> queue, CallbackInfo ci) {
        rtxremix$open(rtxremix$seedScope, "ChunkVisibility.seedQueue", rtxremix$sizeOf(queue));
    }

    @Inject(method = "initializeQueueForFullUpdate", at = @At("RETURN"))
    private void rtxremix$seedReturn(BlockPos cameraPosition, Queue<?> queue, CallbackInfo ci) {
        rtxremix$close(rtxremix$seedScope);
    }

    @Inject(method = "runUpdates", at = @At("HEAD"))
    private void rtxremix$walkHead(
            SectionOcclusionGraph.GraphStorage renderableChunks,
            Vec3 cameraPos,
            Queue<?> queue,
            boolean smartCull,
            Consumer<SectionRenderDispatcher.RenderSection> onSectionAdded,
            LongOpenHashSet emptySections,
            LongOpenHashSet loadedChunks,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$walkScope, "ChunkVisibility.walkVisibleGraph", rtxremix$sizeOf(queue));
    }

    @Inject(method = "runUpdates", at = @At("RETURN"))
    private void rtxremix$walkReturn(
            SectionOcclusionGraph.GraphStorage renderableChunks,
            Vec3 cameraPos,
            Queue<?> queue,
            boolean smartCull,
            Consumer<SectionRenderDispatcher.RenderSection> onSectionAdded,
            LongOpenHashSet emptySections,
            LongOpenHashSet loadedChunks,
            CallbackInfo ci) {
        rtxremix$close(rtxremix$walkScope);
    }

    private static int rtxremix$sizeOf(List<?> values) {
        return values == null ? -1 : values.size();
    }

    private static int rtxremix$sizeOf(Queue<?> values) {
        return values == null ? -1 : values.size();
    }

    private static void rtxremix$open(ThreadLocal<ArrayDeque<ProfileScope>> holder, String phaseName) {
        rtxremix$open(holder, phaseName, -1);
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
}
