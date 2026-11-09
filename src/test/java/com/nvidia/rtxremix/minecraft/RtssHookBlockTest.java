/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtssHookBlockTest {

    @Test
    void blocksOnlyWhileRtssRuns() {
        assertEquals(RtssHookBlock.Decision.BLOCK, RtssHookBlock.decide(null, true));
        assertEquals(RtssHookBlock.Decision.BLOCK, RtssHookBlock.decide("false", true));
        assertEquals(RtssHookBlock.Decision.NOT_RUNNING, RtssHookBlock.decide(null, false));
    }

    @Test
    void thePlayerCanLetRtssIn() {
        assertEquals(RtssHookBlock.Decision.ALLOWED, RtssHookBlock.decide("true", true));
        assertEquals(RtssHookBlock.Decision.ALLOWED, RtssHookBlock.decide("TRUE", false));
    }

    // The bindings below are only read, never used to set the policy: the mitigation cannot be
    // lifted and would outlive the test in a reused Gradle test JVM.

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void moduleLookupBindsToKernel32() throws Throwable {
        assertTrue(RtssHookBlock.Win32.moduleLoaded("kernel32.dll"));
        assertFalse(RtssHookBlock.Win32.moduleLoaded("rtxremix-no-such-module.dll"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void sharedMemoryLookupReportsAMissingMapping() throws Throwable {
        assertFalse(RtssHookBlock.Win32.sharedMemoryExists("rtxremix-no-such-mapping-7f3a"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void mitigationPolicyReadBinds() throws Throwable {
        // A test JVM never sets the policy, so the read must succeed and report it off.
        assertFalse(RtssHookBlock.Win32.extensionPointsDisabled());
    }
}
