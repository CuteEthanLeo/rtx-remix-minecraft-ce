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
import com.nvidia.rtxremix.minecraft.community.WetSurfaces;
import com.nvidia.rtxremix.minecraft.util.DirectBufferPool;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * The wet copy of the block atlas's albedo and roughness.
 *
 * <p>The copy is computed off the render thread for one wetness level at a
 * time and written into its textures in place, so the wet twin materials keep
 * their handles and terrain is never recaptured. The textures are allocated
 * the first time it rains; until then, and whenever the ground is fully dry,
 * the twins point at the dry atlas.
 */
final class WetAtlas {

    /** Creates a texture the owning manager manages and destroys. */
    interface TextureFactory {
        RemixTexture create(String name, byte[] data, int width, int height, int format);
    }

    private record Computed(int level, byte[] albedo, byte[] roughness) {}

    private final int width;
    private final int height;
    private final byte[] dryAlbedo;
    private final byte[] dryRoughness;
    private final byte[] porosity;

    private RemixTexture wetAlbedo;
    private RemixTexture wetRoughness;
    private int shownLevel;
    private CompletableFuture<Computed> computing;

    WetAtlas(int width, int height, byte[] dryAlbedo, byte[] dryRoughness, byte[] porosity) {
        this.width = width;
        this.height = height;
        this.dryAlbedo = dryAlbedo;
        this.dryRoughness = dryRoughness;
        this.porosity = porosity;
    }

    RemixTexture wetAlbedo() { return wetAlbedo; }
    RemixTexture wetRoughness() { return wetRoughness; }

    /**
     * Moves toward {@code targetLevel}. Call once per frame on the render thread.
     *
     * @return the level the wet twins should now show (0 meaning the dry atlas), or -1 when nothing changed
     */
    int update(RemixAPI api, int targetLevel, TextureFactory factory) {
        if (computing != null) {
            if (!computing.isDone()) {
                return -1;
            }
            Computed computed = computing.join();
            computing = null;
            upload(api, computed, factory);
            shownLevel = computed.level();
            return shownLevel;
        }
        if (targetLevel == shownLevel) {
            return -1;
        }
        if (targetLevel == 0) {
            shownLevel = 0;
            return 0;
        }
        int level = targetLevel;
        computing = CompletableFuture.supplyAsync(() -> {
            byte[] albedo = new byte[dryAlbedo.length];
            byte[] roughness = new byte[dryRoughness.length];
            WetSurfaces.wetAtlas(dryAlbedo, dryRoughness, porosity, level, albedo, roughness);
            return new Computed(level, albedo, roughness);
        });
        return -1;
    }

    private void upload(RemixAPI api, Computed computed, TextureFactory factory) {
        if (wetAlbedo == null || wetAlbedo.isDeleted() || wetRoughness == null || wetRoughness.isDeleted()) {
            wetAlbedo = factory.create("atlas_albedo_wet", computed.albedo(), width, height,
                    TextureFormat.RGBA8_UNORM);
            wetRoughness = factory.create("atlas_roughness_wet", computed.roughness(), width, height,
                    TextureFormat.R8_UNORM);
            return;
        }
        patch(api, wetAlbedo, computed.albedo(), width * 4, TextureFormat.RGBA8_UNORM);
        patch(api, wetRoughness, computed.roughness(), width, TextureFormat.R8_UNORM);
    }

    private void patch(RemixAPI api, RemixTexture texture, byte[] pixels, int rowPitch, int format) {
        ByteBuffer pixelData = DirectBufferPool.acquire(pixels.length);
        try {
            pixelData.put(pixels).flip();
            TexturePatch patch = new TexturePatch();
            patch.width = width;
            patch.height = height;
            patch.pixelData = pixelData;
            patch.rowPitch = rowPitch;
            patch.format = format;
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
