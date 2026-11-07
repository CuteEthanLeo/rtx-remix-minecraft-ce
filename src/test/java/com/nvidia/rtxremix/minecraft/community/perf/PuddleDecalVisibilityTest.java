/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.perf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PuddleDecalVisibilityTest {

    private static final int NONE = -1;

    private static PuddleDecalVisibility hidden() {
        PuddleDecalVisibility visibility = new PuddleDecalVisibility();
        for (int i = 0; i < PuddleDecalVisibility.HIDE_AFTER_DRY_FRAMES; i++) {
            visibility.update(0, NONE, 0);
        }
        assertFalse(visibility.visible());
        return visibility;
    }

    @Test
    void startsVisibleAndHidesOnlyOnceTheEmptyMaskHasSettled() {
        PuddleDecalVisibility visibility = new PuddleDecalVisibility();
        assertTrue(visibility.visible());
        for (int i = 1; i < PuddleDecalVisibility.HIDE_AFTER_DRY_FRAMES; i++) {
            assertTrue(visibility.update(0, NONE, 0), "dry frame " + i);
        }
        assertFalse(visibility.update(0, NONE, 0));
    }

    @Test
    void comesBackAsSoonAsAnyPuddleIsWantedBeforeItsMaskUploads() {
        PuddleDecalVisibility visibility = hidden();

        assertTrue(visibility.update(0, NONE, 3), "the rain asks for level 3");
        assertTrue(visibility.update(0, 3, 3), "level 3 is computing");
        assertTrue(visibility.update(3, NONE, 3), "level 3 is shown");
    }

    @Test
    void stayVisibleWhileTheMaskDriesToEmpty() {
        PuddleDecalVisibility visibility = new PuddleDecalVisibility();
        visibility.update(2, NONE, 2);

        assertTrue(visibility.update(2, NONE, 0), "the shown mask still has puddles");
        assertTrue(visibility.update(2, 0, 0), "level 0 is computing");
        for (int i = 1; i < PuddleDecalVisibility.HIDE_AFTER_DRY_FRAMES; i++) {
            assertTrue(visibility.update(0, NONE, 0), "settling frame " + i);
        }
        assertFalse(visibility.update(0, NONE, 0));
    }

    @Test
    void anyWetFrameRestartsTheDryCount() {
        PuddleDecalVisibility visibility = new PuddleDecalVisibility();
        visibility.update(0, NONE, 0);
        visibility.update(0, NONE, 0);
        assertTrue(visibility.update(0, 1, 0));
        for (int i = 1; i < PuddleDecalVisibility.HIDE_AFTER_DRY_FRAMES; i++) {
            assertTrue(visibility.update(0, NONE, 0));
        }
        assertFalse(visibility.update(0, NONE, 0));
    }
}
