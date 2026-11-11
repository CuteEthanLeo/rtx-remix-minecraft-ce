/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.model.BakedSimpleModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.model.ErrorCollectingSpriteGetter;
import net.minecraft.client.render.model.MultipartBlockStateModel;
import net.minecraft.client.render.model.ResolvableModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a multipart model's part selection under Fabric Loader, as a section build reaches it
 * through {@code BlockQuadEmitter}. Since 1.21.5 vanilla resolves the matching parts once
 * per block state and appends them to the caller's list, so the terrain capture relies on
 * vanilla's selection directly; this pins the contract it relies on: only matching parts
 * are asked, in selector order, and each sees the caller's random reseeded with one draw.
 */
@Tag("fabric")
class MultipartQuadSelectionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void matchingPartsConcatenateAndEachSeesTheReseededRandom() {
        BlockState state = Blocks.OAK_FENCE.getDefaultState();
        List<Long> draws = new ArrayList<>();
        List<BlockModelPart> first = parts(2);
        List<BlockModelPart> skipped = parts(1);
        List<BlockModelPart> second = parts(3);
        BlockStateModel model = multipart(state, List.of(
                selector(s -> true, first, draws),
                selector(s -> false, skipped, draws),
                selector(s -> true, second, draws)));

        List<BlockModelPart> result = new ArrayList<>();
        model.addParts(Random.create(42L), result);

        List<BlockModelPart> expected = new ArrayList<>(first);
        expected.addAll(second);
        assertEquals(expected, result);
        assertEquals(2, draws.size(), "only the matching parts are asked for their parts");
        assertEquals(draws.get(0), draws.get(1), "each part sees the random reseeded with one draw");
    }

    @Test
    void aSingleMatchingPartAddsOnlyItsOwnParts() {
        List<BlockModelPart> only = parts(2);
        BlockStateModel model = multipart(Blocks.OAK_FENCE.getDefaultState(), List.of(
                selector(s -> false, parts(1), new ArrayList<>()),
                selector(s -> true, only, new ArrayList<>())));

        List<BlockModelPart> result = new ArrayList<>();
        model.addParts(Random.create(7L), result);

        assertEquals(only, result);
    }

    @Test
    void aStateNoSelectorMatchesHasNoParts() {
        BlockStateModel model = multipart(Blocks.OAK_FENCE.getDefaultState(), List.of(
                selector(s -> false, parts(1), new ArrayList<>())));

        List<BlockModelPart> result = new ArrayList<>();
        model.addParts(Random.create(1L), result);

        assertTrue(result.isEmpty());
    }

    /** Bakes the selectors the way the model loader does, for one block state. */
    private static BlockStateModel multipart(
            BlockState state, List<MultipartBlockStateModel.Selector<BlockStateModel.Unbaked>> selectors) {
        Baker baker = new Baker() {
            @Override
            public BakedSimpleModel getModel(Identifier id) {
                throw new UnsupportedOperationException("getModel");
            }

            @Override
            public ErrorCollectingSpriteGetter getSpriteGetter() {
                throw new UnsupportedOperationException("getSpriteGetter");
            }

            @Override
            public <T> T compute(Baker.ResolvableCacheKey<T> key) {
                return key.compute(this);
            }
        };
        return new MultipartBlockStateModel.MultipartUnbaked(selectors).bake(state, baker);
    }

    private static MultipartBlockStateModel.Selector<BlockStateModel.Unbaked> selector(
            Predicate<BlockState> condition, List<BlockModelPart> parts, List<Long> draws) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("addParts")) {
                draws.add(((Random) args[0]).nextLong());
                @SuppressWarnings("unchecked")
                List<BlockModelPart> out = (List<BlockModelPart>) args[1];
                out.addAll(parts);
                return null;
            }
            if (method.getName().equals("particleSprite") && method.getParameterCount() == 0) {
                return null;
            }
            if (method.isDefault()) {
                // Fabric API's FabricBlockStateModel defaults.
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            throw new UnsupportedOperationException(method.getName());
        };
        BlockStateModel model = (BlockStateModel) Proxy.newProxyInstance(
                BlockStateModel.class.getClassLoader(), new Class<?>[] {BlockStateModel.class}, handler);
        BlockStateModel.Unbaked unbaked = new BlockStateModel.Unbaked() {
            @Override
            public BlockStateModel bake(Baker baker) {
                return model;
            }

            @Override
            public void resolve(ResolvableModel.Resolver resolver) {
            }
        };
        return new MultipartBlockStateModel.Selector<>(condition, unbaked);
    }

    private static List<BlockModelPart> parts(int count) {
        List<BlockModelPart> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            parts.add((BlockModelPart) Proxy.newProxyInstance(
                    BlockModelPart.class.getClassLoader(), new Class<?>[] {BlockModelPart.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "part@" + System.identityHashCode(proxy);
                        default -> throw new UnsupportedOperationException(method.getName());
                    }));
        }
        return parts;
    }
}
