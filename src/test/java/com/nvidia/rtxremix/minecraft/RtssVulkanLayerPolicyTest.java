/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtssVulkanLayerPolicyTest {

    @Test
    void disablesTheLayerByDefault() {
        assertTrue(RtssVulkanLayerPolicy.shouldDisable(null, null));
        assertTrue(RtssVulkanLayerPolicy.shouldDisable("false", null));
        assertTrue(RtssVulkanLayerPolicy.shouldDisable("not-a-boolean", null));
    }

    @Test
    void keepsTheLayerWhenThePlayerAllowsIt() {
        assertFalse(RtssVulkanLayerPolicy.shouldDisable("true", null));
        assertFalse(RtssVulkanLayerPolicy.shouldDisable("TRUE", null));
    }

    @Test
    void neverOverridesAValueThePlayerSet() {
        assertFalse(RtssVulkanLayerPolicy.shouldDisable(null, "1"));
        assertFalse(RtssVulkanLayerPolicy.shouldDisable(null, "0"));
        assertFalse(RtssVulkanLayerPolicy.shouldDisable(null, ""));
    }

    @Test
    void usesTheSwitchRtssDeclares() {
        // RTSSVkLayer64.json: "disable_environment": { "DISABLE_RTSS_LAYER": "1" }
        assertEquals("DISABLE_RTSS_LAYER", RtssVulkanLayerPolicy.DISABLE_VARIABLE);
        assertEquals("1", RtssVulkanLayerPolicy.DISABLE_VALUE);
    }
}
