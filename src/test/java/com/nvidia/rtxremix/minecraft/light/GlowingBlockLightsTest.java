/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.light;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fabric")
class GlowingBlockLightsTest {

    private static final BlockPos POS = new BlockPos(10, 64, -3);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    private static List<LightEntry> resolve(BlockState state, BlockPos pos,
                                            BlockLightRuleManager.ModelSampler sampler) {
        List<LightEntry> lights = new ArrayList<>();
        for (BlockLightRuleManager.Rule rule : BlockLightDefaults.create().values()) {
            if (rule.matches(state)) {
                lights.addAll(rule.resolve(state, pos, sampler));
            }
        }
        return lights;
    }

    @Test
    void aFreeGlowstoneBlockHasOneOutwardRectPerFaceAndEmitsLikeATorch() {
        List<LightEntry> lights = resolve(
                Blocks.GLOWSTONE.getDefaultState(), POS, BlockLightRuleManager.fixedCentroid(new float[3]));
        assertEquals(6, lights.size());
        float power = 0.0f;
        for (LightEntry light : lights) {
            assertEquals(LightShape.RECT, light.shape());
            // Centre of the block plus half a block and the offset along the facing direction.
            float distance = Math.abs(light.x() - (POS.getX() + 0.5f))
                    + Math.abs(light.y() - (POS.getY() + 0.5f))
                    + Math.abs(light.z() - (POS.getZ() + 0.5f));
            assertEquals(0.5f + BlockLightDefaults.FACE_LIGHT_OFFSET, distance, 1.0e-4f);
            float outward = (light.x() - (POS.getX() + 0.5f)) * light.directionX()
                    + (light.y() - (POS.getY() + 0.5f)) * light.directionY()
                    + (light.z() - (POS.getZ() + 0.5f)) * light.directionZ();
            assertTrue(outward > 0.0f, "faces outward");
            float intensity = light.intensity() * light.authoredIntensityMultiplier();
            power += LightPower.of(light, intensity, intensity, intensity);
        }
        float expected = LightPower.torchReference() * 15.0f / BlockLightDefaults.TORCH_LIGHT_LEVEL;
        assertEquals(expected, power, expected * 1.0e-3f);
    }

    @Test
    void coveredFacesGetNoLight() {
        BlockLightRuleManager.ModelSampler buried = new BlockLightRuleManager.ModelSampler() {
            @Override
            public float[] centroid() {
                return new float[]{0.5f, 0.5f, 0.5f};
            }

            @Override
            public boolean isFaceExposed(Direction face) {
                return face == Direction.UP;
            }
        };
        List<LightEntry> lights = resolve(Blocks.SEA_LANTERN.getDefaultState(), POS, buried);
        assertEquals(1, lights.size());
        assertEquals(1.0f, lights.getFirst().directionY(), 0.0f);
    }

    @Test
    void anUnlitLampHasNoLightAndAJackOLanternLightsOnlyThroughItsFace() {
        float[] centroid = {0.5f, 0.5f, 0.5f};
        assertTrue(resolve(Blocks.REDSTONE_LAMP.getDefaultState(), POS,
                BlockLightRuleManager.fixedCentroid(centroid)).isEmpty());

        BlockState pumpkin = Blocks.JACK_O_LANTERN.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.EAST);
        List<LightEntry> lights = resolve(pumpkin, POS,
                BlockLightRuleManager.fixedCentroid(centroid));
        assertEquals(1, lights.size());
        assertEquals(1.0f, lights.getFirst().directionX(), 0.0f);
    }

    @Test
    void lanternsAndEndRodsNowCastLight() {
        float[] centroid = {0.5f, 0.5f, 0.5f};
        assertEquals(1, resolve(Blocks.LANTERN.getDefaultState(), POS,
                BlockLightRuleManager.fixedCentroid(centroid)).size());
        assertEquals(1, resolve(Blocks.END_ROD.getDefaultState(), POS,
                BlockLightRuleManager.fixedCentroid(centroid)).size());
    }
}
