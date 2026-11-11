/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HalfFloatTest {

    @Test
    void knownValuesEncode() {
        assertEquals((short) 0x0000, HalfFloat.fromFloat(0.0f));
        assertEquals((short) 0x8000, HalfFloat.fromFloat(-0.0f));
        assertEquals((short) 0x3C00, HalfFloat.fromFloat(1.0f));
        assertEquals((short) 0xC000, HalfFloat.fromFloat(-2.0f));
        assertEquals((short) 0x3555, HalfFloat.fromFloat(1.0f / 3.0f));
        assertEquals((short) 0x7BFF, HalfFloat.fromFloat(65504.0f));
        assertEquals((short) 0x0400, HalfFloat.fromFloat(0x1.0p-14f));
        assertEquals((short) 0x0001, HalfFloat.fromFloat(0x1.0p-24f));
        assertEquals((short) 0x03FF, HalfFloat.fromFloat(1023 * 0x1.0p-24f));
    }

    @Test
    void roundsToNearestWithTiesToEven() {
        // Halfway between 1 and its successor, then halfway between that successor and the next.
        assertEquals((short) 0x3C00, HalfFloat.fromFloat(1.0f + 0x1.0p-11f));
        assertEquals((short) 0x3C02, HalfFloat.fromFloat(1.0f + 3 * 0x1.0p-11f));
        assertEquals((short) 0x3C01, HalfFloat.fromFloat(1.0f + 0x1.0p-11f + 0x1.0p-20f));
        // A mantissa that rounds up into the next binade.
        assertEquals((short) 0x4000, HalfFloat.fromFloat(2.0f - 0x1.0p-12f));
        // The largest subnormal's upper tie rounds to the smallest normal.
        assertEquals((short) 0x0400, HalfFloat.fromFloat(1023.5f * 0x1.0p-24f));
        // Half of the smallest subnormal is a tie with zero; just above it is not.
        assertEquals((short) 0x0000, HalfFloat.fromFloat(0x1.0p-25f));
        assertEquals((short) 0x0001, HalfFloat.fromFloat(0x1.000002p-25f));
        assertEquals((short) 0x0002, HalfFloat.fromFloat(1.5f * 0x1.0p-24f));
    }

    @Test
    void overflowAndSpecialValues() {
        assertEquals((short) 0x7BFF, HalfFloat.fromFloat(65519.996f));
        assertEquals((short) 0x7C00, HalfFloat.fromFloat(65520.0f));
        assertEquals((short) 0x7C00, HalfFloat.fromFloat(Float.POSITIVE_INFINITY));
        assertEquals((short) 0xFC00, HalfFloat.fromFloat(Float.NEGATIVE_INFINITY));
        assertEquals((short) 0x7E00, HalfFloat.fromFloat(Float.NaN));
        assertEquals((short) 0x0000, HalfFloat.fromFloat(Float.MIN_VALUE));
        assertTrue(Float.isNaN(HalfFloat.toFloat((short) 0x7E00)));
        assertEquals(Float.NEGATIVE_INFINITY, HalfFloat.toFloat((short) 0xFC00));
    }

    @Test
    void everyFiniteHalfRoundTrips() {
        for (int bits = 0; bits <= 0xFFFF; bits++) {
            if ((bits & 0x7C00) == 0x7C00) {
                continue;
            }
            float value = HalfFloat.toFloat((short) bits);
            assertEquals((short) bits, HalfFloat.fromFloat(value), "half bits " + Integer.toHexString(bits));
        }
        assertEquals(0x1.0p-24f, HalfFloat.toFloat((short) 0x0001));
        assertEquals(-0x1.0p-14f, HalfFloat.toFloat((short) 0x8400));
        assertEquals(65504.0f, HalfFloat.toFloat((short) 0x7BFF));
    }
}
