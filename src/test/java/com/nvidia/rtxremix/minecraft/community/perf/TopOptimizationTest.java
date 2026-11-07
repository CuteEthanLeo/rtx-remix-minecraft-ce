/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nvidia.rtxremix.minecraft.community.perf.TopOptimization.Technique;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class TopOptimizationTest {

    private static TopOptimization fresh() {
        return new TopOptimization(new Properties());
    }

    private static TopOptimization withProperties(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return new TopOptimization(properties);
    }

    @Test
    void startsOffWithTheSafeTechniquesWanted() {
        TopOptimization topOpt = fresh();

        assertFalse(topOpt.isEnabled());
        assertFalse(topOpt.isMasterActive());
        assertEquals(0, topOpt.activeMask());
        assertTrue(topOpt.isWanted(Technique.PIPELINED_PRESENT));
        assertTrue(topOpt.isWanted(Technique.DRY_PUDDLE_CULL));
        assertFalse(topOpt.isWanted(Technique.SUBMIT_OVERLAP), "the aggressive technique is opt-in");
    }

    @Test
    void theMasterSwitchRunsExactlyTheWantedTechniques() {
        TopOptimization topOpt = fresh();
        int before = topOpt.generation();

        topOpt.setEnabled(true);

        assertTrue(topOpt.active(Technique.PIPELINED_PRESENT));
        assertTrue(topOpt.active(Technique.DRY_PUDDLE_CULL));
        assertFalse(topOpt.active(Technique.SUBMIT_OVERLAP));
        assertNotEquals(before, topOpt.generation());

        topOpt.setWanted(Technique.DRY_PUDDLE_CULL, false);
        topOpt.setWanted(Technique.SUBMIT_OVERLAP, true);
        assertFalse(topOpt.active(Technique.DRY_PUDDLE_CULL));
        assertTrue(topOpt.active(Technique.SUBMIT_OVERLAP));

        topOpt.setEnabled(false);
        assertEquals(0, topOpt.activeMask());
        assertTrue(topOpt.isWanted(Technique.SUBMIT_OVERLAP), "the choices survive the master switch");
    }

    @Test
    void generationMovesOnlyWhenWhatRunsChanges() {
        TopOptimization topOpt = fresh();
        int start = topOpt.generation();

        topOpt.setWanted(Technique.SUBMIT_OVERLAP, false);
        topOpt.setWanted(Technique.PIPELINED_PRESENT, false);
        topOpt.setWanted(Technique.PIPELINED_PRESENT, true);
        assertEquals(start, topOpt.generation(), "nothing runs while the master switch is off");

        topOpt.setEnabled(true);
        int enabled = topOpt.generation();
        topOpt.setEnabled(true);
        assertEquals(enabled, topOpt.generation());
    }

    @Test
    void aVetoLatchesForTheSessionAndKeepsItsFirstReason() {
        TopOptimization topOpt = fresh();
        topOpt.setEnabled(true);

        topOpt.veto(Technique.PIPELINED_PRESENT, "first");
        topOpt.veto(Technique.PIPELINED_PRESENT, "second");

        assertFalse(topOpt.active(Technique.PIPELINED_PRESENT));
        assertTrue(topOpt.isVetoed(Technique.PIPELINED_PRESENT));
        assertEquals("first", topOpt.blockedReason(Technique.PIPELINED_PRESENT));
        assertTrue(topOpt.active(Technique.DRY_PUDDLE_CULL), "a veto stops only its own technique");

        topOpt.setEnabled(false);
        topOpt.setEnabled(true);
        topOpt.setWanted(Technique.PIPELINED_PRESENT, true);
        assertFalse(topOpt.active(Technique.PIPELINED_PRESENT));
        assertTrue(topOpt.describe().contains("pipelinedPresent=vetoed(first)"), topOpt.describe());
    }

    @Test
    void jvmPropertiesPinTheMasterAndSingleTechniques() {
        TopOptimization topOpt = withProperties(
                "rtxremix.topopt", "true",
                "rtxremix.topopt.submitOverlap", "on",
                "rtxremix.topopt.dryPuddleCull", "false");

        assertFalse(topOpt.isEnabled(), "the persisted choice is untouched");
        assertTrue(topOpt.isMasterForced());
        assertTrue(topOpt.isMasterActive());
        assertTrue(topOpt.active(Technique.PIPELINED_PRESENT));
        assertTrue(topOpt.active(Technique.SUBMIT_OVERLAP));
        assertFalse(topOpt.active(Technique.DRY_PUDDLE_CULL));
        assertEquals("-Drtxremix.topopt.dryPuddleCull=false", topOpt.blockedReason(Technique.DRY_PUDDLE_CULL));
        assertEquals("-Drtxremix.topopt=true", topOpt.masterBlockedReason());

        topOpt.setWanted(Technique.DRY_PUDDLE_CULL, true);
        assertFalse(topOpt.active(Technique.DRY_PUDDLE_CULL), "forced off wins over the menu");
    }

    @Test
    void aMasterForcedOffIgnoresTheMenuAndTheAbOverride() {
        TopOptimization topOpt = withProperties("rtxremix.topopt", "off");

        topOpt.setEnabled(true);
        topOpt.setSessionOverride(true);

        assertFalse(topOpt.isMasterActive());
        assertEquals(0, topOpt.activeMask());
    }

    @Test
    void theAbOverrideNeverTouchesThePersistedChoice() {
        TopOptimization topOpt = fresh();

        topOpt.setSessionOverride(true);
        assertTrue(topOpt.active(Technique.PIPELINED_PRESENT));
        assertFalse(topOpt.isEnabled());
        assertTrue(topOpt.describe().startsWith("on (A/B run)"), topOpt.describe());

        topOpt.setSessionOverride(false);
        assertEquals(0, topOpt.activeMask());

        topOpt.setEnabled(true);
        topOpt.setSessionOverride(null);
        assertTrue(topOpt.isMasterActive(), "clearing the override returns to the player's choice");
    }

    @Test
    void switchValuesAcceptTheUsualSpellings() {
        for (String on : new String[]{"true", " TRUE ", "on", "1", "yes"}) {
            assertEquals(Boolean.TRUE, TopOptimization.parseSwitch(on), on);
        }
        for (String off : new String[]{"false", "Off", "0", "no"}) {
            assertEquals(Boolean.FALSE, TopOptimization.parseSwitch(off), off);
        }
        assertNull(TopOptimization.parseSwitch("maybe"));
        assertNull(TopOptimization.parseSwitch(null));
        assertNull(withProperties("rtxremix.topopt", "maybe").masterBlockedReason(),
                "an unrecognized value leaves the menu in charge");
    }

    @Test
    void techniquesAreFoundByIdAndOwnTheirSettingIds() {
        assertEquals(Technique.PIPELINED_PRESENT, Technique.byId("pipelinedpresent").orElseThrow());
        assertTrue(Technique.byId("nope").isEmpty());
        assertEquals("community.topOptimization.dryPuddleCull", Technique.DRY_PUDDLE_CULL.settingId());
        assertTrue(Technique.values().length < Integer.SIZE, "the active set is an int bit mask");
    }
}
