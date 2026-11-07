/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import com.nvidia.rtxremix.RemixAPI;
import com.nvidia.rtxremix.TextureBatchEntry;
import com.nvidia.rtxremix.TextureFormat;
import com.nvidia.rtxremix.TexturePatch;
import com.nvidia.rtxremix.TexturePatchInfo;
import com.nvidia.rtxremix.minecraft.asset.RemixTexture;
import com.nvidia.rtxremix.minecraft.community.Puddles;
import com.nvidia.rtxremix.minecraft.util.DirectBufferPool;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * The puddle decal's texture, rewritten in place as the ground's wetness level
 * changes. Each level is computed off the render thread and uploaded on it.
 */
final class PuddleMask {

    private record Computed(int level, byte[] rgba) {}

    private final float[] depth;
    private final RemixTexture texture;
    private int shownLevel;
    private CompletableFuture<Computed> computing;
    private int computingLevel = -1;

    PuddleMask(float[] depth, RemixTexture texture) {
        this.depth = depth;
        this.texture = texture;
    }

    /** The level last uploaded; 0 means the mask is empty and the decals draw nothing. */
    int shownLevel() {
        return shownLevel;
    }

    /** The level being computed off the render thread, or -1 when none is. */
    int computingLevel() {
        return computingLevel;
    }

    /** Moves toward {@code targetLevel}; call once per frame on the render thread. */
    void update(RemixAPI api, int targetLevel) {
        if (computing != null) {
            if (!computing.isDone()) return;
            Computed computed = computing.join();
            computing = null;
            computingLevel = -1;
            if (!texture.isDeleted()) {
                patch(api, computed.rgba());
                shownLevel = computed.level();
            }
            return;
        }
        if (targetLevel == shownLevel) return;
        int level = targetLevel;
        computingLevel = level;
        computing = CompletableFuture.supplyAsync(() -> {
            byte[] rgba = new byte[depth.length * 4];
            Puddles.mask(depth, level, rgba);
            return new Computed(level, rgba);
        });
    }

    private void patch(RemixAPI api, byte[] pixels) {
        ByteBuffer pixelData = DirectBufferPool.acquire(pixels.length);
        try {
            pixelData.put(pixels).flip();
            TexturePatch patch = new TexturePatch();
            patch.width = Puddles.SIZE;
            patch.height = Puddles.SIZE;
            patch.pixelData = pixelData;
            patch.rowPitch = Puddles.SIZE * 4;
            patch.format = TextureFormat.RGBA8_UNORM;
            TextureBatchEntry entry = new TextureBatchEntry();
            entry.handle = texture.getHandle();
            entry.patches = new TexturePatch[]{patch};
            TexturePatchInfo info = new TexturePatchInfo();
            info.entries = new TextureBatchEntry[]{entry};
            api.updateTextureFromMemory(info);
        } finally {
            DirectBufferPool.release(pixelData);
        }
    }
}
