/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.nvidia.rtxremix.minecraft.RtxRemixCommonMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.BlockItem;
import net.minecraft.loot.LootTable;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decodes the Copper Beacon's data files with the targeted Minecraft version's own codecs and
 * checks they sit where that version looks for them. Data formats and folder names change
 * between versions, a file the game cannot decode is only logged at world load, and no other
 * test reads these files, so a stale format otherwise ships as a block with no recipe or drop.
 * Registering the beacon for that also constructs its block and item, which nothing else in
 * the suite does.
 */
@Tag("fabric")
class CopperBeaconDataTest {

    private static final String DATA = "/data/" + RtxRemixCommonMod.MOD_ID + "/";
    private static final String ASSETS = "/assets/" + RtxRemixCommonMod.MOD_ID + "/";
    private static final Identifier ID = new Identifier(RtxRemixCommonMod.MOD_ID, CopperBeaconBlock.ID);

    private static DynamicOps<JsonElement> ops;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        // The data files name the beacon's item, so it has to be in the registry they decode against.
        if (!Registries.ITEM.containsId(ID)) {
            SpotLightBlock.registerAll();
        }
        ops = RegistryOps.of(JsonOps.INSTANCE, DynamicRegistryManager.of(Registries.REGISTRIES));
    }

    @Test
    void recipeDecodesAsAShapedRecipe() throws IOException {
        DataResult<Recipe<?>> recipe = Recipe.CODEC.parse(ops, read(DATA + "recipes/copper_beacon.json"));

        assertTrue(recipe.error().isEmpty(), () -> "recipe does not decode: " + recipe.error().get().message());
        assertInstanceOf(ShapedRecipe.class, recipe.result().orElseThrow());
    }

    @Test
    void lootTableDecodes() throws IOException {
        DataResult<LootTable> table =
                LootTable.CODEC.parse(ops, read(DATA + "loot_tables/blocks/copper_beacon.json"));

        assertTrue(table.error().isEmpty(), () -> "loot table does not decode: " + table.error().get().message());
    }

    @Test
    void miningTagsNameTheBeacon() throws IOException {
        for (String tag : new String[] {"mineable/pickaxe", "needs_stone_tool"}) {
            JsonElement json = read("/data/minecraft/tags/blocks/" + tag + ".json");

            assertTrue(json.toString().contains('"' + ID.toString() + '"'), tag + " does not list " + ID);
        }
    }

    @Test
    void itemModelIsWhereThisVersionLooksForIt() {
        assertNotNull(CopperBeaconDataTest.class.getResource(ASSETS + "models/item/copper_beacon.json"),
                "the beacon has no item model");
    }

    /** The lang files carry one {@code block.*} key, which the block and its item both have to resolve to. */
    @Test
    void blockAndItemShareTheBlockTranslationKey() {
        String key = "block." + RtxRemixCommonMod.MOD_ID + "." + CopperBeaconBlock.ID;

        assertEquals(key, CopperBeaconBlock.INSTANCE.getTranslationKey());
        assertEquals(key, assertInstanceOf(BlockItem.class, Registries.ITEM.get(ID)).getTranslationKey());
    }

    private static JsonElement read(String resource) throws IOException {
        try (InputStream in = CopperBeaconDataTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        }
    }
}
