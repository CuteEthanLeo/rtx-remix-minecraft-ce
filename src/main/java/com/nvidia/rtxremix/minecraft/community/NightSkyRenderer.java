/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import com.nvidia.rtxremix.HardcodedVertex;
import com.nvidia.rtxremix.InstanceCategoryBit;
import com.nvidia.rtxremix.MaterialInfo;
import com.nvidia.rtxremix.MaterialInfoOpaqueEXT;
import com.nvidia.rtxremix.RemixAPI;
import com.nvidia.rtxremix.TextureFormat;
import com.nvidia.rtxremix.TexturePurpose;
import com.nvidia.rtxremix.Transform;
import com.nvidia.rtxremix.minecraft.asset.RemixAssetBase;
import com.nvidia.rtxremix.minecraft.asset.RemixMaterial;
import com.nvidia.rtxremix.minecraft.asset.RemixMaterialStore;
import com.nvidia.rtxremix.minecraft.asset.RemixMesh;
import com.nvidia.rtxremix.minecraft.asset.RemixMeshStore;
import com.nvidia.rtxremix.minecraft.asset.RemixMeshSurface;
import com.nvidia.rtxremix.minecraft.asset.RemixTexture;
import com.nvidia.rtxremix.minecraft.asset.RemixTextureStore;
import com.nvidia.rtxremix.minecraft.community.clouds.VolumetricClouds;
import com.nvidia.rtxremix.minecraft.util.DirectBufferPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws the {@link StarField} stars and the Milky Way in place of vanilla's
 * stars. The sky renderer owns when and where: it hands over the sky-local
 * transform, its additive celestial blend, and the opacities each frame.
 *
 * <p>Every star of one colour class and brightness tier shares a material, so
 * the whole field is one mesh of fifteen surfaces, and a change of opacity is
 * fifteen material updates rather than one per star.
 */
public final class NightSkyRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NightSkyRenderer.class);
    /** Milky Way radiance at full opacity, for a texel of full intensity. */
    static final float MILKY_WAY_RADIANCE = 0.45f;
    /** Share of the Milky Way a full moon washes out. */
    static final float FULL_MOON_WASHOUT = 0.6f;
    private static final float OPACITY_UPDATE_DELTA = 1.0f / 255.0f;
    /** Meteor streak radiance at full brightness, above the night bloom threshold so heads glow. */
    static final float METEOR_RADIANCE = 12.0f;

    private final List<RemixTexture> textures = new ArrayList<>();
    private final RemixMaterial[][] starMaterials =
            new RemixMaterial[StarField.CLASS_COLORS.length][StarField.TIER_RADIANCE.length];
    /**
     * Star sprite per colour class, kept here because a material lets go of its texture
     * list once Remix has created it, and an opacity update needs the sprite again.
     */
    private final RemixTexture[] starSprites = new RemixTexture[StarField.CLASS_COLORS.length];
    private RemixMaterial bandMaterial;
    private RemixTexture bandTexture;
    private RemixMesh starMesh;
    private RemixMesh bandMesh;
    private float lastStarOpacity = -1.0f;
    private float lastBandOpacity = -1.0f;
    private boolean failed;
    private final Meteors meteors = new Meteors();
    private RemixTexture meteorTexture;
    private RemixMaterial meteorMaterial;
    private RemixMesh meteorMesh;
    private float lastMeteorBrightness = -1.0f;
    private final Transform meteorTransform = new Transform();

    /**
     * Volumetric clouds hide the stars and the Milky Way behind them. The sky's additive
     * billboards cannot be depth-tested against the dome, so each star's vertex colour carries
     * how much of the sky shows through at its direction, and the meshes are rebuilt when that
     * changes; clouds drift slowly enough that every couple of seconds is plenty.
     */
    private static final long OCCLUSION_INTERVAL_NANOS = 1_000_000_000L;
    private static final float OCCLUSION_REBUILD_DELTA = 0.08f;
    private final List<List<StarField.Star>> starBuckets = new ArrayList<>();
    private final List<RemixMaterial> starBucketMaterials = new ArrayList<>();
    private final SkyVisibility starVisibility = new SkyVisibility();
    private final SkyVisibility bandVisibility = new SkyVisibility();
    private long lastOcclusionNanos = Long.MIN_VALUE;
    private boolean occluded;

    /**
     * The opacity the stars take from vanilla's star brightness. Vanilla tops
     * out at 0.5 on a clear night; the field is authored for full night at 1.
     */
    public static float starOpacity(float vanillaStarBrightness) {
        return Math.max(0.0f, Math.min(1.0f, vanillaStarBrightness * 2.0f));
    }

    /** The Milky Way fades with the moon: a full moon (size 1) washes most of it out. */
    public static float bandOpacity(float starOpacity, float moonSize) {
        float moon = Math.max(0.0f, Math.min(1.0f, moonSize));
        return starOpacity * (1.0f - FULL_MOON_WASHOUT * moon);
    }

    /**
     * Draws the night sky at the given opacities.
     *
     * @return false when the assets could not be created, so the caller can fall back to vanilla's stars
     */
    public boolean draw(RemixAPI api, Transform skyTransform, long blendPNext,
                        float starOpacity, float bandOpacity) {
        if (!ensureAssets()) {
            return false;
        }
        updateCloudOcclusion(skyTransform);
        updateOpacity(starOpacity, bandOpacity);
        if (bandOpacity > 0.0f) {
            api.drawInstance(bandMesh.getHandle(), skyTransform.matrix, InstanceCategoryBit.SKY, 1, blendPNext);
        }
        if (starOpacity > 0.0f) {
            api.drawInstance(starMesh.getHandle(), skyTransform.matrix, InstanceCategoryBit.SKY, 1, blendPNext);
        }
        return true;
    }

    /**
     * Advances the shooting stars and draws the one in flight, if any. Meteors move in real
     * time around the camera rather than with the rotating sky.
     *
     * @param visibility how visible the stars are, 0 to 1
     * @param day the world's day number, which picks the meteor-shower nights
     */
    public void drawMeteors(RemixAPI api, double cameraX, double cameraY, double cameraZ,
                            long blendPNext, float visibility, long day) {
        if (meteorMesh == null || !meteors.update(ExportClock.get().nanos(), visibility, day)) {
            return;
        }
        meteors.writeTransform(cameraX, cameraY, cameraZ, meteorTransform.matrix);
        float brightness = meteors.brightness() * VolumetricClouds.skyVisibility(
                (float) (meteorTransform.matrix[3] - cameraX),
                (float) (meteorTransform.matrix[7] - cameraY),
                (float) (meteorTransform.matrix[11] - cameraZ));
        if (Math.abs(brightness - lastMeteorBrightness) > OPACITY_UPDATE_DELTA) {
            RemixMaterialStore.get().update(meteorMaterial,
                    materialInfo(meteorTexture, METEOR_RADIANCE, Math.min(1.0f, brightness),
                            MaterialInfo.WRAP_MODE_CLAMP));
            lastMeteorBrightness = brightness;
        }
        api.drawInstance(meteorMesh.getHandle(), meteorTransform.matrix, InstanceCategoryBit.SKY, 1, blendPNext);
    }

    public void destroy() {
        destroy(meteorMesh);
        destroy(meteorMaterial);
        meteorMesh = null;
        meteorMaterial = null;
        meteorTexture = null;
        lastMeteorBrightness = -1.0f;
        destroy(starMesh);
        destroy(bandMesh);
        starMesh = null;
        bandMesh = null;
        for (RemixMaterial[] row : starMaterials) {
            for (int i = 0; i < row.length; i++) {
                destroy(row[i]);
                row[i] = null;
            }
        }
        destroy(bandMaterial);
        bandMaterial = null;
        Arrays.fill(starSprites, null);
        for (RemixTexture texture : textures) {
            destroy(texture);
        }
        textures.clear();
        bandTexture = null;
        lastStarOpacity = -1.0f;
        lastBandOpacity = -1.0f;
        starBuckets.clear();
        starBucketMaterials.clear();
        starVisibility.reset(0);
        bandVisibility.reset(0);
        lastOcclusionNanos = Long.MIN_VALUE;
        occluded = false;
    }

    /** Lets a later frame retry after the renderer's resources were rebuilt. */
    public void resetFailure() {
        failed = false;
    }

    private boolean ensureAssets() {
        if (starMesh != null && bandMesh != null && meteorMesh != null) {
            return true;
        }
        if (failed) {
            return false;
        }
        destroy();
        try {
            createStars();
            createBand();
            createMeteor();
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Failed to create the night sky; falling back to vanilla stars", e);
            destroy();
            failed = true;
            return false;
        }
    }

    private void createStars() {
        int classes = StarField.CLASS_COLORS.length;
        int tiers = StarField.TIER_RADIANCE.length;
        List<StarField.Star> stars = StarField.generate(StarField.SEED, StarField.STAR_COUNT);
        List<List<StarField.Star>> buckets = new ArrayList<>();
        for (int i = 0; i < classes * tiers; i++) {
            buckets.add(new ArrayList<>());
        }
        for (StarField.Star star : stars) {
            buckets.get(star.colorClass() * tiers + star.tier()).add(star);
        }

        starBuckets.clear();
        starBucketMaterials.clear();
        int starTotal = 0;
        for (int colorClass = 0; colorClass < classes; colorClass++) {
            RemixTexture sprite = createTexture(
                    NightSkyTextures.star(StarField.CLASS_COLORS[colorClass]),
                    NightSkyTextures.STAR_SIZE, NightSkyTextures.STAR_SIZE);
            starSprites[colorClass] = sprite;
            for (int tier = 0; tier < tiers; tier++) {
                List<StarField.Star> bucket = buckets.get(colorClass * tiers + tier);
                if (bucket.isEmpty()) continue;
                RemixMaterial material = RemixMaterialStore.get().create(new RemixMaterial(
                        materialInfo(sprite, StarField.TIER_RADIANCE[tier], 1.0f,
                                MaterialInfo.WRAP_MODE_CLAMP), List.of(sprite), null));
                starMaterials[colorClass][tier] = material;
                starBuckets.add(bucket);
                starBucketMaterials.add(material);
                starTotal += bucket.size();
            }
        }
        starVisibility.reset(starTotal);
        starMesh = buildStarMesh();
        lastStarOpacity = 1.0f;
    }

    private RemixMesh buildStarMesh() {
        List<RemixMeshSurface> surfaces = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < starBuckets.size(); i++) {
            List<StarField.Star> bucket = starBuckets.get(i);
            surfaces.add(starSurface(starBucketMaterials.get(i), bucket, starVisibility.values(), offset));
            offset += bucket.size();
        }
        return RemixMeshStore.get().create(new RemixMesh(surfaces.toArray(new RemixMeshSurface[0])));
    }

    /**
     * Rebuilds the star and Milky Way meshes with each vertex dimmed by how much of the sky the
     * volumetric clouds leave visible in its direction, when that moved enough to notice.
     */
    private void updateCloudOcclusion(Transform skyTransform) {
        boolean clouds = VolumetricClouds.isActive();
        if (!clouds && !occluded) {
            return;
        }
        long now = ExportClock.get().nanos();
        if (clouds && lastOcclusionNanos != Long.MIN_VALUE
                && now - lastOcclusionNanos < OCCLUSION_INTERVAL_NANOS && now >= lastOcclusionNanos) {
            return;
        }
        lastOcclusionNanos = now;
        float[] m = skyTransform.matrix;
        int index = 0;
        for (List<StarField.Star> bucket : starBuckets) {
            for (StarField.Star star : bucket) {
                float visibility = clouds ? visibility(m, star.x(), star.y(), star.z()) : 1.0f;
                starVisibility.values()[index++] = visibility;
            }
        }
        int segments = StarField.BAND_SEGMENTS;
        if (bandVisibility.values().length != (segments + 1) * 2) {
            bandVisibility.reset((segments + 1) * 2);
        }
        float[] point = new float[3];
        for (int i = 0; i <= segments; i++) {
            for (int side = 0; side < 2; side++) {
                StarField.bandPoint(i / (float) segments, side == 0 ? -1.0f : 1.0f, point);
                float visibility = clouds ? visibility(m, point[0], point[1], point[2]) : 1.0f;
                int v = i * 2 + side;
                bandVisibility.values()[v] = visibility;
            }
        }
        // Disabling clouds must restore even a below-threshold residual dimming.
        if (!starVisibility.changed(OCCLUSION_REBUILD_DELTA)
                && !bandVisibility.changed(OCCLUSION_REBUILD_DELTA) && (clouds || !occluded)) {
            return;
        }
        RemixMesh previousStars = starMesh;
        RemixMesh previousBand = bandMesh;
        RemixMesh nextStars = buildStarMesh();
        RemixMesh nextBand;
        try {
            nextBand = buildBandMesh();
        } catch (RuntimeException e) {
            destroy(nextStars);
            throw e;
        }
        starMesh = nextStars;
        bandMesh = nextBand;
        starVisibility.publish();
        bandVisibility.publish();
        occluded = clouds;
        destroy(previousStars);
        destroy(previousBand);
    }

    /** Sky visibility at a sky-local direction, rotated into the world by the sky transform. */
    private static float visibility(float[] m, float x, float y, float z) {
        float wx = m[0] * x + m[1] * y + m[2] * z;
        float wy = m[4] * x + m[5] * y + m[6] * z;
        float wz = m[8] * x + m[9] * y + m[10] * z;
        return VolumetricClouds.skyVisibility(wx, wy, wz);
    }

    private static void shrinkQuad(float[] corners, float scale) {
        if (scale >= 1.0f) {
            return;
        }
        for (int axis = 0; axis < 3; axis++) {
            float centre = 0.25f * (corners[axis] + corners[3 + axis] + corners[6 + axis] + corners[9 + axis]);
            for (int corner = 0; corner < 4; corner++) {
                int index = corner * 3 + axis;
                corners[index] = centre + (corners[index] - centre) * scale;
            }
        }
    }

    private static int grey(float visibility) {
        int level = Math.round(Math.max(0.0f, Math.min(1.0f, visibility)) * 255.0f);
        return 0xFF000000 | level << 16 | level << 8 | level;
    }

    private static RemixMeshSurface starSurface(RemixMaterial material, List<StarField.Star> stars,
                                                float[] visibility, int offset) {
        HardcodedVertex[] vertices = new HardcodedVertex[stars.size() * 4];
        int[] indices = new int[stars.size() * 6];
        float[] corners = new float[12];
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int s = 0; s < stars.size(); s++) {
            StarField.Star star = stars.get(s);
            StarField.quadCorners(star, corners, 0);
            // A star's light is emissive, which the vertex colour does not dim; its sprite is
            // far smaller than a pixel, so its brightness follows its area instead. Shrinking
            // the quad toward its centre by the square root of the visibility hides it behind
            // a cloud in proportion.
            shrinkQuad(corners, (float) Math.sqrt(Math.max(0.0f, Math.min(1.0f, visibility[offset + s]))));
            for (int corner = 0; corner < 4; corner++) {
                vertices[s * 4 + corner] = HardcodedVertex.create(
                        corners[corner * 3], corners[corner * 3 + 1], corners[corner * 3 + 2],
                        -star.x(), -star.y(), -star.z(),
                        uv[corner][0], uv[corner][1], grey(visibility[offset + s]));
            }
            quadIndices(indices, s * 6, s * 4);
        }
        return new RemixMeshSurface(material).setVertices(vertices).setIndices(indices);
    }

    private void createBand() {
        bandTexture = createTexture(NightSkyTextures.milkyWay(StarField.SEED),
                NightSkyTextures.BAND_WIDTH, NightSkyTextures.BAND_HEIGHT);
        bandMaterial = RemixMaterialStore.get().create(new RemixMaterial(
                materialInfo(bandTexture, MILKY_WAY_RADIANCE, 1.0f, MaterialInfo.WRAP_MODE_REPEAT),
                List.of(bandTexture), null));

        bandMesh = buildBandMesh();
        lastBandOpacity = 1.0f;
    }

    private RemixMesh buildBandMesh() {
        int segments = StarField.BAND_SEGMENTS;
        HardcodedVertex[] vertices = new HardcodedVertex[(segments + 1) * 2];
        float[] point = new float[3];
        for (int i = 0; i <= segments; i++) {
            float along = i / (float) segments;
            for (int side = 0; side < 2; side++) {
                StarField.bandPoint(along, side == 0 ? -1.0f : 1.0f, point);
                float length = (float) Math.sqrt(point[0] * point[0] + point[1] * point[1] + point[2] * point[2]);
                vertices[i * 2 + side] = HardcodedVertex.create(point[0], point[1], point[2],
                        -point[0] / length, -point[1] / length, -point[2] / length,
                        along, side, grey(bandVisibility.values().length == vertices.length
                                ? bandVisibility.values()[i * 2 + side] : 1.0f));
            }
        }
        int[] indices = new int[segments * 6];
        for (int i = 0; i < segments; i++) {
            int a = i * 2;
            indices[i * 6] = a;
            indices[i * 6 + 1] = a + 2;
            indices[i * 6 + 2] = a + 3;
            indices[i * 6 + 3] = a;
            indices[i * 6 + 4] = a + 3;
            indices[i * 6 + 5] = a + 1;
        }
        RemixMeshSurface surface = new RemixMeshSurface(bandMaterial).setVertices(vertices).setIndices(indices);
        return RemixMeshStore.get().create(new RemixMesh(new RemixMeshSurface[]{surface}));
    }

    private void createMeteor() {
        meteorTexture = createTexture(NightSkyTextures.meteor(),
                NightSkyTextures.METEOR_WIDTH, NightSkyTextures.METEOR_HEIGHT);
        meteorMaterial = RemixMaterialStore.get().create(new RemixMaterial(
                materialInfo(meteorTexture, METEOR_RADIANCE, 0.0f, MaterialInfo.WRAP_MODE_CLAMP),
                List.of(meteorTexture), null));
        HardcodedVertex[] vertices = {
                HardcodedVertex.create(0, -0.5f, 0, 0, 0, -1, 0, 0, 0xFFFFFFFF),
                HardcodedVertex.create(1, -0.5f, 0, 0, 0, -1, 1, 0, 0xFFFFFFFF),
                HardcodedVertex.create(1, 0.5f, 0, 0, 0, -1, 1, 1, 0xFFFFFFFF),
                HardcodedVertex.create(0, 0.5f, 0, 0, 0, -1, 0, 1, 0xFFFFFFFF)
        };
        RemixMeshSurface surface = new RemixMeshSurface(meteorMaterial)
                .setVertices(vertices)
                .setIndices(new int[]{0, 1, 2, 0, 2, 3});
        meteorMesh = RemixMeshStore.get().create(new RemixMesh(new RemixMeshSurface[]{surface}));
        lastMeteorBrightness = 0.0f;
    }

    private void updateOpacity(float starOpacity, float bandOpacity) {
        RemixMaterialStore materials = RemixMaterialStore.get();
        if (Math.abs(starOpacity - lastStarOpacity) > OPACITY_UPDATE_DELTA) {
            for (int colorClass = 0; colorClass < starMaterials.length; colorClass++) {
                for (int tier = 0; tier < starMaterials[colorClass].length; tier++) {
                    RemixMaterial material = starMaterials[colorClass][tier];
                    if (material == null) continue;
                    materials.update(material, materialInfo(starSprites[colorClass], StarField.TIER_RADIANCE[tier],
                            starOpacity, MaterialInfo.WRAP_MODE_CLAMP));
                }
            }
            lastStarOpacity = starOpacity;
        }
        if (Math.abs(bandOpacity - lastBandOpacity) > OPACITY_UPDATE_DELTA) {
            materials.update(bandMaterial, materialInfo(bandTexture, MILKY_WAY_RADIANCE,
                    bandOpacity, MaterialInfo.WRAP_MODE_REPEAT));
            lastBandOpacity = bandOpacity;
        }
    }

    private RemixTexture createTexture(byte[] pixels, int width, int height) {
        ByteBuffer buffer = DirectBufferPool.acquire(pixels.length);
        buffer.put(pixels).flip();
        RemixTexture texture = RemixTextureStore.get().create(new RemixTexture(
                width, height, TextureFormat.RGBA8_UNORM, TexturePurpose.COLOR, buffer));
        textures.add(texture);
        return texture;
    }

    /** The same additive emissive setup as the sun and moon, with the sky's alpha test. */
    private static MaterialInfo materialInfo(RemixTexture texture, float radiance, float opacity, byte wrapU) {
        MaterialInfoOpaqueEXT opaque = MaterialInfoOpaqueEXT.builder()
                .albedoConstant(opacity, opacity, opacity)
                .opacityConstant(opacity)
                .roughnessConstant(1.0f)
                .metallicConstant(0.0f)
                .useDrawCallAlphaState(true)
                .build();
        return MaterialInfo.builder()
                .albedoTexture(texture.getHandle())
                .emissiveTexture(texture.getHandle())
                .emissiveIntensity(radiance * opacity)
                .filterMode(1)
                .wrapMode(wrapU, MaterialInfo.WRAP_MODE_CLAMP)
                .opaqueExt(opaque)
                .build();
    }

    private static void quadIndices(int[] indices, int index, int vertex) {
        indices[index] = vertex;
        indices[index + 1] = vertex + 1;
        indices[index + 2] = vertex + 2;
        indices[index + 3] = vertex;
        indices[index + 4] = vertex + 2;
        indices[index + 5] = vertex + 3;
    }

    private static void destroy(RemixAssetBase asset) {
        if (asset != null && !asset.isDeleted()) {
            asset.destroy();
        }
    }
}
