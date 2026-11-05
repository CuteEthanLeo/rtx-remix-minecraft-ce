/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import java.util.Arrays;

/** Samples compared with the last successful mesh upload, so small changes accumulate. */
final class SkyVisibility {
    private float[] sampled = new float[0];
    private float[] published = new float[0];

    void reset(int size) {
        sampled = new float[size];
        published = new float[size];
        Arrays.fill(sampled, 1.0f);
        Arrays.fill(published, 1.0f);
    }

    float[] values() {
        return sampled;
    }

    boolean changed(float threshold) {
        for (int i = 0; i < sampled.length; i++) {
            if (Math.abs(sampled[i] - published[i]) >= threshold) {
                return true;
            }
        }
        return false;
    }

    void publish() {
        System.arraycopy(sampled, 0, published, 0, sampled.length);
    }
}
