/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs vanilla's full chunk visibility walk on a dedicated low-priority executor, so long
 * view distances cannot starve terrain capture or the render thread, and labels each phase
 * for the profiler.
 *
 * <p>1.21.2 rebuilt the visibility state around an octree that vanilla fills during the walk
 * and queries per frame for the frustum. That octree does what the 1.21.1 branch's coarse
 * frustum index did, so the index is gone. The allocation-free port of the walk and the
 * per-frame budget on incremental propagation were written against 1.21.1's walk and are not
 * ported yet (TODO(1.21.2)); vanilla's walk runs unchanged.
 */
@Mixin(ChunkRenderingDataPreparer.class)
public abstract class ChunkRenderingDataPreparerMixin {
    private static final ExecutorService rtxremix$visibilityExecutor =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "RTX Remix chunk visibility");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
                return thread;
            });

    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$storageScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$frustumScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$prepareScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$graphScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$seedScope = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<ProfileScope>> rtxremix$walkScope = new ThreadLocal<>();

    @Inject(method = "setStorage", at = @At("HEAD"))
    private void rtxremix$storageHead(BuiltChunkStorage storage, CallbackInfo ci) {
        rtxremix$open(rtxremix$storageScope, "ChunkVisibility.setStorage", -1);
    }

    @Inject(method = "setStorage", at = @At("RETURN"))
    private void rtxremix$storageReturn(BuiltChunkStorage storage, CallbackInfo ci) {
        rtxremix$close(rtxremix$storageScope);
    }

    @Inject(method = "collectChunks", at = @At("HEAD"))
    private void rtxremix$frustumHead(
            Frustum frustum,
            List<ChunkBuilder.BuiltChunk> builtChunks,
            List<ChunkBuilder.BuiltChunk> nearbyChunks,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$frustumScope, "ChunkVisibility.applyFrustum", rtxremix$sizeOf(builtChunks));
    }

    @Inject(method = "collectChunks", at = @At("RETURN"))
    private void rtxremix$frustumReturn(
            Frustum frustum,
            List<ChunkBuilder.BuiltChunk> builtChunks,
            List<ChunkBuilder.BuiltChunk> nearbyChunks,
            CallbackInfo ci) {
        rtxremix$captureCount(rtxremix$frustumScope, rtxremix$sizeOf(builtChunks));
        rtxremix$close(rtxremix$frustumScope);
    }

    @Inject(method = "updateSectionOcclusionGraph", at = @At("HEAD"))
    private void rtxremix$prepareHead(
            boolean cullChunks,
            Camera camera,
            Frustum frustum,
            List<ChunkBuilder.BuiltChunk> builtChunks,
            LongOpenHashSet activeSections,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$prepareScope, "ChunkVisibility.prepare", rtxremix$sizeOf(builtChunks));
    }

    @Inject(method = "updateSectionOcclusionGraph", at = @At("RETURN"))
    private void rtxremix$prepareReturn(
            boolean cullChunks,
            Camera camera,
            Frustum frustum,
            List<ChunkBuilder.BuiltChunk> builtChunks,
            LongOpenHashSet activeSections,
            CallbackInfo ci) {
        rtxremix$close(rtxremix$prepareScope);
    }

    /** The full walk's executor: the dedicated low-priority thread while RTX is on. */
    @ModifyArg(
            method = "updateTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            ),
            index = 1)
    private Executor rtxremix$visibilityExecutor(Executor vanilla) {
        return RtxRemixMod.isRtxEnabled() ? rtxremix$visibilityExecutor : vanilla;
    }

    @Inject(method = "method_52835", at = @At("HEAD"))
    private void rtxremix$graphHead(
            boolean cullChunks,
            Frustum frustum,
            List<ChunkBuilder.BuiltChunk> builtChunks,
            Vec3d cameraPos,
            LongOpenHashSet activeSections,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$graphScope, "ChunkVisibility.rebuildGraph", rtxremix$sizeOf(builtChunks));
    }

    @Inject(method = "method_52835", at = @At("RETURN"))
    private void rtxremix$graphReturn(
            boolean cullChunks,
            Frustum frustum,
            List<ChunkBuilder.BuiltChunk> builtChunks,
            Vec3d cameraPos,
            LongOpenHashSet activeSections,
            CallbackInfo ci) {
        rtxremix$close(rtxremix$graphScope);
    }

    @Inject(method = "method_52821", at = @At("HEAD"))
    private void rtxremix$seedHead(Camera camera, Queue<?> queue, CallbackInfo ci) {
        rtxremix$open(rtxremix$seedScope, "ChunkVisibility.seedQueue", rtxremix$sizeOf(queue));
    }

    @Inject(method = "method_52821", at = @At("RETURN"))
    private void rtxremix$seedReturn(Camera camera, Queue<?> queue, CallbackInfo ci) {
        rtxremix$close(rtxremix$seedScope);
    }

    @Inject(method = "method_52825", at = @At("HEAD"))
    private void rtxremix$walkHead(
            ChunkRenderingDataPreparer.RenderableChunks renderableChunks,
            Vec3d cameraPos,
            Queue<?> queue,
            boolean cullChunks,
            java.util.function.Consumer<ChunkBuilder.BuiltChunk> chunkConsumer,
            LongOpenHashSet activeSections,
            CallbackInfo ci) {
        rtxremix$open(rtxremix$walkScope, "ChunkVisibility.walkVisibleGraph", rtxremix$sizeOf(queue));
    }

    @Inject(method = "method_52825", at = @At("RETURN"))
    private void rtxremix$walkReturn(
            ChunkRenderingDataPreparer.RenderableChunks renderableChunks,
            Vec3d cameraPos,
            Queue<?> queue,
            boolean cullChunks,
            java.util.function.Consumer<ChunkBuilder.BuiltChunk> chunkConsumer,
            LongOpenHashSet activeSections,
            CallbackInfo ci) {
        rtxremix$close(rtxremix$walkScope);
    }

    private static int rtxremix$sizeOf(List<?> values) {
        return values == null ? -1 : values.size();
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
