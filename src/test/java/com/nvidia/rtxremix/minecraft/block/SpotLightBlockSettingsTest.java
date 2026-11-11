/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.block;

import com.nvidia.rtxremix.minecraft.RtxRemixCommonMod;
import java.util.Optional;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Since 1.21.2 block and item constructors reject settings without a registry key, so a variant's
 * static instance must be keyed before it is built. A missing key fails the {@code main}
 * entrypoint at game start, which no other test constructs.
 */
@Tag("fabric")
class SpotLightBlockSettingsTest {

    private static final String TRANSLATION_KEY = "block." + RtxRemixCommonMod.MOD_ID + "." + CopperBeaconBlock.ID;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void copperBeaconBlockIsKeyedToItsRegistryId() {
        CopperBeaconBlock block = assertDoesNotThrow(() -> CopperBeaconBlock.INSTANCE);

        assertEquals(
                Optional.of(RegistryKey.of(RegistryKeys.LOOT_TABLE,
                        Identifier.of(RtxRemixCommonMod.MOD_ID, "blocks/" + CopperBeaconBlock.ID))),
                block.getLootTableKey());
        assertEquals(TRANSLATION_KEY, block.getTranslationKey());
    }

    @Test
    void copperBeaconItemUsesTheBlockTranslationKey() {
        BlockItem item = assertDoesNotThrow(() -> new BlockItem(
                CopperBeaconBlock.INSTANCE, SpotLightBlock.itemSettings(CopperBeaconBlock.ID)));

        assertEquals(TRANSLATION_KEY, item.getTranslationKey());
    }
}
