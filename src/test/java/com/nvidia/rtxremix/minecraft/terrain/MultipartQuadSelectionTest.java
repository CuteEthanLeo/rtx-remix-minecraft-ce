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
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.MultipartBakedModel;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@code MultipartBakedModelMixin}'s part selection under Fabric Loader, with the mixin
 * applied. Its selectors field is a generic list, so a selector type that no longer matches
 * the targeted Minecraft version compiles and passes {@code MixinTargetsApplyTest}, then throws
 * {@code ClassCastException} on the first fence or wall a section build meets.
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
        List<BakedQuad> first = quads(2);
        List<BakedQuad> skipped = quads(1);
        List<BakedQuad> second = quads(3);
        MultipartBakedModel model = new MultipartBakedModel(List.of(
                part(s -> true, first, draws),
                part(s -> false, skipped, draws),
                part(s -> true, second, draws)));

        List<BakedQuad> result = model.getQuads(state, Direction.UP, Random.create(42L));

        List<BakedQuad> expected = new ArrayList<>(first);
        expected.addAll(second);
        assertEquals(expected, result);
        assertEquals(2, draws.size(), "only the matching parts are asked for quads");
        assertEquals(draws.get(0), draws.get(1), "each part sees the random reseeded with one draw");
    }

    @Test
    void aSingleMatchingPartReturnsItsOwnList() {
        List<BakedQuad> only = quads(2);
        MultipartBakedModel model = new MultipartBakedModel(List.of(
                part(s -> false, quads(1), new ArrayList<>()),
                part(s -> true, only, new ArrayList<>())));

        assertSame(only, model.getQuads(Blocks.OAK_FENCE.getDefaultState(), null, Random.create(7L)));
    }

    @Test
    void aNullStateHasNoQuads() {
        MultipartBakedModel model = new MultipartBakedModel(List.of(part(s -> true, quads(1), new ArrayList<>())));

        assertTrue(model.getQuads(null, Direction.UP, Random.create(1L)).isEmpty());
    }

    private static MultipartBakedModel.Selector part(
            Predicate<BlockState> condition, List<BakedQuad> quads, List<Long> draws) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("getQuads")) {
                draws.add(((Random) args[2]).nextLong());
                return quads;
            }
            if (method.isDefault()) {
                // Fabric API's FabricBakedModel defaults, such as isVanillaAdapter.
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            throw new UnsupportedOperationException(method.getName());
        };
        BakedModel part = (BakedModel) Proxy.newProxyInstance(
                BakedModel.class.getClassLoader(), new Class<?>[] {BakedModel.class}, handler);
        return new MultipartBakedModel.Selector(condition, part);
    }

    private static List<BakedQuad> quads(int count) {
        List<BakedQuad> quads = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            quads.add(new BakedQuad(new int[32], i, Direction.UP, null, true, 0));
        }
        return quads;
    }
}
