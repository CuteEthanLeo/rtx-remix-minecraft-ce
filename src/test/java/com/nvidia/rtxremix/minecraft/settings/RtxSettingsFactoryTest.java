/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Modified by RTX Remix for Minecraft Community Edition; see COMMUNITY.md.
 */
package com.nvidia.rtxremix.minecraft.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nvidia.rtxremix.ErrorCode;
import com.nvidia.rtxremix.RemixException;
import com.nvidia.rtxremix.minecraft.community.perf.TopOptimization;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RtxSettingsFactoryTest {

    private static final String OPTION = "rtx.materials.smoothReliefMapFiltering";

    private final Map<String, String> writes = new HashMap<>();

    @Test
    void aRemixBoolStartsFromTheResolvedValueWithoutWriting() {
        RtxSetting<Boolean> setting = remixBool(option -> "True");

        assertTrue(setting.get());
        assertTrue(setting.defaultValue());
        assertTrue(writes.isEmpty());
    }

    @Test
    void aRemixBoolReportsItsWriteBeforeRemixResolvesIt() {
        RtxSetting<Boolean> setting = remixBool(option -> "False");

        setting.apply(true);

        assertTrue(setting.get());
        assertFalse(setting.isDefault());
        assertEquals("true", writes.get(OPTION));
    }

    @Test
    void resettingARemixBoolWritesTheStartupValue() {
        RtxSetting<Boolean> setting = remixBool(option -> "False");
        setting.apply(true);

        setting.reset();

        assertFalse(setting.get());
        assertEquals("false", writes.get(OPTION));
    }

    @Test
    void aRemixBoolIsLeftOutWhenTheRuntimeCannotResolveTheOption() {
        Optional<RtxSetting<Boolean>> setting = RtxSettingsFactory.remixBool("t.bool",
                SettingGroup.MATERIALS, "k", OPTION,
                option -> {
                    throw new RemixException("GetConfigVariableResolved failed for " + option,
                            ErrorCode.GENERAL_FAILURE);
                },
                writes::put);

        assertTrue(setting.isEmpty());
        assertTrue(writes.isEmpty());
    }

    @Test
    void topOptimizationSettingsStartAtTheirDefaultsAndRoundTrip(@TempDir Path dir) {
        TopOptimization saved = new TopOptimization(new Properties());
        List<RtxSetting<Boolean>> settings = RtxSettingsFactory.topOptimization(saved);

        assertEquals(1 + TopOptimization.Technique.values().length, settings.size());
        assertEquals(TopOptimization.SETTING_ID, settings.get(0).id());
        for (RtxSetting<Boolean> setting : settings) {
            assertEquals(SettingGroup.TOP_OPTIMIZATION, setting.group());
            assertTrue(setting.isDefault(), setting.id() + " starts at its default");
            assertNull(setting.blockedReason());
        }

        settings.get(0).apply(true);
        settings.get(1).apply(false);
        RtxSettingsStore store = new RtxSettingsStore(dir.resolve("rtxremix.json"));
        store.save(new RtxSettings(new ArrayList<>(settings)));

        TopOptimization loaded = new TopOptimization(new Properties());
        store.load(new RtxSettings(new ArrayList<>(RtxSettingsFactory.topOptimization(loaded))));
        assertTrue(loaded.isEnabled());
        assertFalse(loaded.isWanted(TopOptimization.Technique.PIPELINED_PRESENT));
        assertTrue(loaded.isWanted(TopOptimization.Technique.DRY_PUDDLE_CULL));
    }

    @Test
    void aVetoedTechniqueSaysWhyInItsSetting() {
        TopOptimization topOpt = new TopOptimization(new Properties());
        List<RtxSetting<Boolean>> settings = RtxSettingsFactory.topOptimization(topOpt);

        topOpt.veto(TopOptimization.Technique.SUBMIT_OVERLAP, "stale interop info");

        RtxSetting<Boolean> overlap = settings.stream()
                .filter(s -> s.id().equals(TopOptimization.Technique.SUBMIT_OVERLAP.settingId()))
                .findFirst().orElseThrow();
        assertEquals("stale interop info", overlap.blockedReason());
    }

    private RtxSetting<Boolean> remixBool(Function<String, String> configReader) {
        return RtxSettingsFactory.remixBool("t.bool", SettingGroup.MATERIALS, "k", OPTION,
                configReader, writes::put).orElseThrow();
    }
}
