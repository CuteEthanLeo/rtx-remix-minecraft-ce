/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.light;

import com.nvidia.rtxremix.minecraft.block.SpotLightBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Built-in block light rules. Resource packs can override these by using the same rule path.
 */
final class BlockLightDefaults {
    static final float DEFAULT_AUTHORED_INTENSITY_MULTIPLIER = 5.0f;
    static final float TORCH_AUTHORED_INTENSITY_MULTIPLIER = 7.5f;
    static final float CAMPFIRE_AUTHORED_INTENSITY_MULTIPLIER = 7.5f;
    static final float THIN_SPHERE_RADIUS_SCALE = 0.4f;
    static final float THIN_SPHERE_INTENSITY_SCALE = 3.0f;
    /** Pre-scale emitter radius of a torch's flame, shared with the light reach calibration. */
    static final float TORCH_RADIUS = 0.2f;
    private static final float AMETHYST_INTENSITY_SCALE = 2.0f;
    static final LightEntry.ColorOverride TORCH_COLOR = LightEntry.ColorOverride.of(1.0f, 0.35f, 0.1f);
    private static final LightEntry.ColorOverride CAMPFIRE_COLOR = LightEntry.ColorOverride.of(1.0f, 0.1f, 0.05f);
    private static final LightEntry.ColorOverride SOUL_COLOR = LightEntry.ColorOverride.of(0.05f, 0.2f, 1.0f);
    private static final LightEntry.ColorOverride REDSTONE_COLOR = LightEntry.ColorOverride.of(1.0f, 0.15f, 0.02f);
    private static final LightEntry.ColorOverride CONDUIT_COLOR = LightEntry.ColorOverride.of(0.45f, 0.9f, 1.0f);
    private static final LightEntry.ColorOverride AMETHYST_COLOR = LightEntry.ColorOverride.of(0.65f, 0.35f, 1.0f);
    static final LightEntry.ColorOverride LANTERN_COLOR = LightEntry.ColorOverride.of(1.0f, 0.6f, 0.3f);
    /** Faces of a full-block emitter sit this far outside the block, so its own faces never shadow them. */
    static final float FACE_LIGHT_OFFSET = 0.005f;
    static final float LANTERN_RADIUS = 0.15f;
    static final float END_ROD_RADIUS = 0.0625f;
    static final float END_ROD_LENGTH = 0.9375f;
    private static final Direction[] FACES = Direction.values();

    // Shared beacon geometry and path-specific radiance tuning live in
    // BeaconBeam; the block_display lighthouse remains independent.
    // Beam colour is per-variant and lives on the block (SpotLightBlock.beamColor).

    private BlockLightDefaults() {}

    static Map<Identifier, BlockLightRuleManager.Rule> create() {
        LinkedHashMap<Identifier, BlockLightRuleManager.Rule> rules = new LinkedHashMap<>();
        // Built as locals, not static fields: a static field touching Blocks
        // would force the block registry to bootstrap just to load this class,
        // even for a caller that only wants a constant such as TORCH_COLOR.
        Set<Block> candles = candleBlocks();
        Set<Block> candleCakes = candleCakeBlocks();
        add(rules, "torch", exact(Blocks.TORCH),
                (id, state, pos, centroid) -> List.of(thinSphere(id, 0, state, pos, 0.5f, 0.7f, 0.5f, TORCH_RADIUS, TORCH_COLOR)),
                TORCH_AUTHORED_INTENSITY_MULTIPLIER);
        add(rules, "soul_torch", exact(Blocks.SOUL_TORCH),
                (id, state, pos, centroid) -> List.of(thinSphere(id, 0, state, pos, 0.5f, 0.7f, 0.5f, TORCH_RADIUS, SOUL_COLOR)),
                TORCH_AUTHORED_INTENSITY_MULTIPLIER);
        add(rules, "wall_torch", exact(Blocks.WALL_TORCH),
                (id, state, pos, centroid) -> List.of(wallTorch(id, state, pos, TORCH_COLOR)),
                TORCH_AUTHORED_INTENSITY_MULTIPLIER);
        add(rules, "soul_wall_torch", exact(Blocks.SOUL_WALL_TORCH),
                (id, state, pos, centroid) -> List.of(wallTorch(id, state, pos, SOUL_COLOR)),
                TORCH_AUTHORED_INTENSITY_MULTIPLIER);
        add(rules, "redstone_torch", state -> state.is(Blocks.REDSTONE_TORCH) && state.getValue(RedstoneTorchBlock.LIT),
                (id, state, pos, centroid) -> List.of(thinSphere(id, 0, state, pos, 0.5f, 0.7f, 0.5f, 0.16f, REDSTONE_COLOR)),
                TORCH_AUTHORED_INTENSITY_MULTIPLIER);
        add(rules, "redstone_wall_torch", state -> state.is(Blocks.REDSTONE_WALL_TORCH) && state.getValue(RedstoneWallTorchBlock.LIT),
                (id, state, pos, centroid) -> List.of(wallTorch(id, state, pos, REDSTONE_COLOR)),
                TORCH_AUTHORED_INTENSITY_MULTIPLIER);
        add(rules, "small_amethyst_bud", exact(Blocks.SMALL_AMETHYST_BUD),
                (id, state, pos, centroid) -> List.of(amethystCrystal(id, state, pos, 3.0f, 0.08f, 1)));
        add(rules, "medium_amethyst_bud", exact(Blocks.MEDIUM_AMETHYST_BUD),
                (id, state, pos, centroid) -> List.of(amethystCrystal(id, state, pos, 4.0f, 0.10f, 2)));
        add(rules, "large_amethyst_bud", exact(Blocks.LARGE_AMETHYST_BUD),
                (id, state, pos, centroid) -> List.of(amethystCrystal(id, state, pos, 5.0f, 0.13f, 4)));
        add(rules, "amethyst_cluster", exact(Blocks.AMETHYST_CLUSTER),
                (id, state, pos, centroid) -> List.of(amethystCrystal(id, state, pos, 7.0f, 0.18f, 5)));
        add(rules, "campfire", state -> state.is(Blocks.CAMPFIRE) && state.getValue(BlockStateProperties.LIT),
                (id, state, pos, centroid) -> List.of(sphere(id, 0, state, pos, 0.5f, 0.5f, 0.5f, 0.25f, CAMPFIRE_COLOR, null)),
                CAMPFIRE_AUTHORED_INTENSITY_MULTIPLIER);
        add(rules, "soul_campfire", state -> state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(BlockStateProperties.LIT),
                (id, state, pos, centroid) -> List.of(sphere(id, 0, state, pos, 0.5f, 0.5f, 0.5f, 0.25f, SOUL_COLOR, null)),
                CAMPFIRE_AUTHORED_INTENSITY_MULTIPLIER);
        add(rules, "conduit", exact(Blocks.CONDUIT),
                (id, state, pos, centroid) -> List.of(sphere(id, 0, state, pos, 0.5f, 0.5f, 0.5f, 0.1875f, CONDUIT_COLOR, null)));
        add(rules, "light", state -> state.is(Blocks.LIGHT) && state.getValue(BlockStateProperties.LEVEL) > 0,
                (id, state, pos, centroid) -> List.of(sphere(id, 0, state, pos, 0.5f, 0.5f, 0.5f, 0.2f, LightEntry.ColorOverride.of(1, 1, 1), null)));
        add(rules, "candles", state -> candles.contains(state.getBlock()) && state.getValue(CandleBlock.LIT),
                BlockLightDefaults::candles);
        add(rules, "candle_cakes", state -> candleCakes.contains(state.getBlock()) && state.getValue(BlockStateProperties.LIT),
                (id, state, pos, centroid) -> List.of(thinSphere(id, 0, state, pos, 0.5f, 1.0f, 0.5f, 0.08f, null)));
        // Community Edition: full-block emitters had no light of their own and lit the
        // scene only through emissive texels, which Remix does not sample as lights. Each
        // exposed face is now a rect light, calibrated so a block with all six
        // faces open emits what a torch of its light level would; the colour comes from its texture.
        Set<Block> glowingBlocks = glowingBlocks();
        add(rules, "glowing_blocks",
                state -> glowingBlocks.contains(state.getBlock()) && state.getLightEmission() > 0,
                BlockLightDefaults::glowingBlock, 1.0f);
        add(rules, "lanterns", state -> state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN),
                BlockLightDefaults::lantern, 1.0f);
        add(rules, "end_rod", exact(Blocks.END_ROD),
                (id, state, pos, centroid) -> List.of(endRod(id, state, pos)), 1.0f);
        BlockLightIntensityControls.validateBuiltInRules(rules.keySet());
        return Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }

    private static void add(Map<Identifier, BlockLightRuleManager.Rule> rules,
                            String path,
                            Predicate<BlockState> matcher,
                            BlockLightRuleManager.Resolver resolver) {
        add(rules, path, matcher, resolver, DEFAULT_AUTHORED_INTENSITY_MULTIPLIER);
    }

    private static void add(Map<Identifier, BlockLightRuleManager.Rule> rules,
                            String path,
                            Predicate<BlockState> matcher,
                            BlockLightRuleManager.Resolver resolver,
                            float authoredIntensityMultiplier) {
        Identifier id = BlockLightRuleIds.builtIn(path);
        add(rules, id, matcher, resolver, authoredIntensityMultiplier);
    }

    private static void add(Map<Identifier, BlockLightRuleManager.Rule> rules,
                            Identifier id,
                            Predicate<BlockState> matcher,
                            BlockLightRuleManager.Resolver resolver,
                            float authoredIntensityMultiplier) {
        rules.put(id, BlockLightRuleManager.rule(
                id,
                matcher,
                (ruleId, state, pos, sampler) -> resolver
                        .resolve(ruleId, state, pos, sampler)
                        .stream()
                        .map(entry -> entry.withAuthoredIntensityMultiplier(
                                authoredIntensityMultiplier))
                        .toList()));
    }

    private static Predicate<BlockState> exact(Block block) {
        return state -> state.is(block);
    }

    private static LightEntry sphere(Identifier id, int index, BlockState state, BlockPos pos,
                                     float x, float y, float z, float radius,
                                     LightEntry.ColorOverride color, Float intensity) {
        return BlockLightRuleManager.sphere(id, index, state, pos, x, y, z, radius, color, intensity);
    }

    private static LightEntry thinSphere(Identifier id, int index, BlockState state, BlockPos pos,
                                         float x, float y, float z, float radius,
                                         LightEntry.ColorOverride color) {
        float intensity = state.getLightEmission()
                * BlockLightRuleManager.RADIANCE_PER_LIGHT_LEVEL
                * THIN_SPHERE_INTENSITY_SCALE;
        return sphere(id, index, state, pos, x, y, z, radius * THIN_SPHERE_RADIUS_SCALE, color, intensity);
    }

    private static LightEntry wallTorch(Identifier id, BlockState state, BlockPos pos,
                                        LightEntry.ColorOverride color) {
        Direction facing = state.getValue(WallTorchBlock.FACING).getOpposite();
        return thinSphere(id, 0, state, pos,
                0.5f + 0.27f * facing.getStepX(),
                0.92f,
                0.5f + 0.27f * facing.getStepZ(),
                0.2f, color);
    }

    private static LightEntry endRod(Identifier id, BlockState state, BlockPos pos) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        BlockLightRuleManager.Vec3 axis = BlockLightRuleManager.Vec3.fromAxis(facing.getAxis());
        float area = 2.0f * (float) Math.PI * END_ROD_RADIUS * END_ROD_LENGTH;
        return BlockLightRuleManager.cylinder(id, 0, state, pos,
                0.5f, 0.5f, 0.5f, END_ROD_RADIUS, axis, END_ROD_LENGTH,
                LightEntry.ColorOverride.of(1.0f, 0.95f, 0.9f),
                torchMatchedIntensity(state.getLightEmission(), area));
    }

    /**
     * Radiance, for a white emitter of this surface area, whose emitted power at this
     * light level matches the torch's power scaled by level over the torch's own level.
     */
    static float torchMatchedIntensity(int lightLevel, float area) {
        float levelShare = lightLevel / (float) TORCH_LIGHT_LEVEL;
        return LightPower.torchReference() * levelShare / (area * (float) Math.PI);
    }

    /** Vanilla torch light level, the level {@link LightPower#torchReference()} is computed at. */
    static final int TORCH_LIGHT_LEVEL = 14;

    private static List<LightEntry> glowingBlock(Identifier id, BlockState state, BlockPos pos,
                                                 BlockLightRuleManager.ModelSampler sampler) {
        // A carved pumpkin lights only through its face.
        Direction only = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && state.is(Blocks.JACK_O_LANTERN)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : null;
        float intensity = torchMatchedIntensity(state.getLightEmission(), 6.0f);
        List<LightEntry> entries = new ArrayList<>(6);
        for (Direction face : FACES) {
            if ((only != null && face != only) || !sampler.isFaceExposed(face)) {
                continue;
            }
            entries.add(faceLight(id, state, pos, face, intensity));
        }
        return entries;
    }

    /** A 1x1 rect light just outside one face of the block, facing out. */
    static LightEntry faceLight(Identifier id, BlockState state, BlockPos pos, Direction face, float intensity) {
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();
        float out = 0.5f + FACE_LIGHT_OFFSET;
        // Any two axes spanning the face plane.
        Direction.Axis axis = face.getAxis();
        float ax = axis == Direction.Axis.X ? 0 : 1;
        float ay = 0;
        float az = axis == Direction.Axis.X ? 1 : 0;
        float bx = 0;
        float by = axis == Direction.Axis.Y ? 0 : 1;
        float bz = axis == Direction.Axis.Y ? 1 : 0;
        return LightEntry.rect(BlockLightRuleManager.key(pos, id, face.ordinal()),
                state.getLightEmission(), state.getBlock(),
                pos.getX() + 0.5f + nx * out, pos.getY() + 0.5f + ny * out, pos.getZ() + 0.5f + nz * out,
                ax, ay, az, 1.0f,
                bx, by, bz, 1.0f,
                nx, ny, nz,
                null, intensity, LightEntry.defaultVolumetricRadianceScale());
    }

    private static List<LightEntry> lantern(Identifier id, BlockState state, BlockPos pos,
                                            BlockLightRuleManager.ModelSampler sampler) {
        boolean hanging = state.getValue(BlockStateProperties.HANGING);
        float area = 4.0f * (float) Math.PI * LANTERN_RADIUS * LANTERN_RADIUS;
        LightEntry.ColorOverride color = state.is(Blocks.SOUL_LANTERN) ? SOUL_COLOR : LANTERN_COLOR;
        return List.of(sphere(id, 0, state, pos, 0.5f, hanging ? 0.375f : 0.3125f, 0.5f,
                LANTERN_RADIUS, color, torchMatchedIntensity(state.getLightEmission(), area)));
    }

    private static Set<Block> glowingBlocks() {
        Set<Block> blocks = new java.util.HashSet<>(List.of(
                Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT, Blocks.JACK_O_LANTERN,
                Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT,
                Blocks.REDSTONE_LAMP));
        // 26.2: the copper bulb variants live in a WeatheringCopperCollection.
        blocks.addAll(Blocks.COPPER_BULB.asList());
        return Set.copyOf(blocks);
    }

    private static LightEntry amethystCrystal(Identifier id, BlockState state, BlockPos pos,
                                              float heightPixels, float radius, int lightLevel) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        float halfHeight = heightPixels / 32.0f;
        float offsetFromCenter = 0.5f - halfHeight;
        float intensity = lightLevel
                * BlockLightRuleManager.RADIANCE_PER_LIGHT_LEVEL
                * AMETHYST_INTENSITY_SCALE;
        return sphere(id, 0, state, pos,
                0.5f - facing.getStepX() * offsetFromCenter,
                0.5f - facing.getStepY() * offsetFromCenter,
                0.5f - facing.getStepZ() * offsetFromCenter,
                radius, AMETHYST_COLOR, intensity);
    }

    private static List<LightEntry> candles(Identifier id, BlockState state, BlockPos pos,
                                            BlockLightRuleManager.ModelSampler centroid) {
        List<LightEntry> entries = new ArrayList<>(state.getValue(CandleBlock.CANDLES));
        float[][] offsets = candleOffsets(state.getValue(CandleBlock.CANDLES));
        for (int i = 0; i < offsets.length; i++) {
            float[] o = offsets[i];
            entries.add(thinSphere(id, i, state, pos, o[0], o[1], o[2], 0.06f, null));
        }
        return entries;
    }

    private static Set<Block> candleBlocks() {
        // 26.2: dyed candles live in a ColorCollection.
        Set<Block> blocks = new java.util.HashSet<>(Blocks.DYED_CANDLE.asList());
        blocks.add(Blocks.CANDLE);
        return Set.copyOf(blocks);
    }

    private static Set<Block> candleCakeBlocks() {
        Set<Block> blocks = new java.util.HashSet<>(Blocks.DYED_CANDLE_CAKE.asList());
        blocks.add(Blocks.CANDLE_CAKE);
        return Set.copyOf(blocks);
    }

    private static float[][] candleOffsets(int candles) {
        return switch (candles) {
            case 1 -> new float[][]{{0.5f, 0.5f, 0.5f}};
            case 2 -> new float[][]{{0.375f, 0.44f, 0.5f}, {0.625f, 0.5f, 0.44f}};
            case 3 -> new float[][]{{0.5f, 0.313f, 0.625f}, {0.375f, 0.44f, 0.5f}, {0.56f, 0.5f, 0.44f}};
            default -> new float[][]{{0.44f, 0.313f, 0.56f}, {0.625f, 0.44f, 0.56f}, {0.375f, 0.44f, 0.375f}, {0.56f, 0.5f, 0.375f}};
        };
    }
}
