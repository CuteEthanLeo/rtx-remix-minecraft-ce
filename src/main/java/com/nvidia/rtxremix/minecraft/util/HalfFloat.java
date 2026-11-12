/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.util;

/**
 * IEEE 754 binary16 conversions, the ones {@code Float.floatToFloat16} and
 * {@code Float.float16ToFloat} provide from Java 20. Minecraft 1.20.1 runs on Java 17.
 */
public final class HalfFloat {

    private static final int SIGN = 0x8000;
    private static final int INFINITY = 0x7C00;
    private static final int QUIET_NAN = 0x7E00;
    private static final int FLOAT_INFINITY_BITS = 0x7F800000;
    /** 65520, the smallest magnitude that rounds past the largest finite half, 65504. */
    private static final int OVERFLOW_BITS = 0x477FF000;
    /** Biased float exponent of 2^-14, the smallest normal half. */
    private static final int SMALLEST_NORMAL_EXPONENT = 113;
    /** Biased float exponent of 2^-25, half of the smallest subnormal half. */
    private static final int HALF_ULP_EXPONENT = 102;

    private HalfFloat() {
    }

    /** The half nearest {@code value}, ties to even; a NaN becomes a quiet NaN of its sign. */
    public static short fromFloat(float value) {
        int bits = Float.floatToRawIntBits(value);
        int sign = (bits >>> 16) & SIGN;
        int magnitude = bits & 0x7FFFFFFF;
        if (magnitude > FLOAT_INFINITY_BITS) {
            return (short) (sign | QUIET_NAN);
        }
        if (magnitude >= OVERFLOW_BITS) {
            return (short) (sign | INFINITY);
        }
        int exponent = magnitude >>> 23;
        if (exponent >= SMALLEST_NORMAL_EXPONENT) {
            int mantissa = magnitude & 0x7FFFFF;
            int half = ((exponent - 112) << 10) | (mantissa >>> 13);
            // A carry out of the mantissa lands in the exponent, which is the next binade.
            return (short) (sign | roundToEven(half, mantissa & 0x1FFF, 0x1000));
        }
        if (exponent < HALF_ULP_EXPONENT) {
            return (short) sign;
        }
        // A subnormal half counts units of 2^-24.
        int significand = (magnitude & 0x7FFFFF) | 0x800000;
        int shift = 126 - exponent;
        int half = significand >>> shift;
        return (short) (sign | roundToEven(half, significand & ((1 << shift) - 1), 1 << (shift - 1)));
    }

    /** The float equal to {@code half}; every half is exactly representable. */
    public static float toFloat(short half) {
        int bits = half & 0xFFFF;
        int sign = (bits & SIGN) << 16;
        int exponent = (bits >>> 10) & 0x1F;
        int mantissa = bits & 0x3FF;
        if (exponent == 0x1F) {
            return Float.intBitsToFloat(sign | FLOAT_INFINITY_BITS | (mantissa << 13));
        }
        if (exponent == 0) {
            float magnitude = mantissa * 0x1.0p-24f;
            return sign != 0 ? -magnitude : magnitude;
        }
        return Float.intBitsToFloat(sign | ((exponent + 112) << 23) | (mantissa << 13));
    }

    private static int roundToEven(int truncated, int remainder, int halfway) {
        boolean up = remainder > halfway || (remainder == halfway && (truncated & 1) != 0);
        return up ? truncated + 1 : truncated;
    }
}
