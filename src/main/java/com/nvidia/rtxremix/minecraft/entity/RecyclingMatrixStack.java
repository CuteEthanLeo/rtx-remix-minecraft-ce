/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

/**
 * Matrix stack dedicated to Remix entity capture.
 *
 * <p>Carries a pooled quaternion for {@code ModelPartMixin}'s allocation-free
 * part transform. Since 1.21.5 vanilla keeps popped entries and copies into them
 * on the next push, so the stack itself needs no entry pool.
 */
public final class RecyclingMatrixStack extends MatrixStack {

    private final Quaternionf modelPartRotation = new Quaternionf();

    /** Restores the identity root after a normal render or renderer exception. */
    void reset() {
        while (!isEmpty()) {
            pop();
        }
        loadIdentity();
    }

    /** Applies vanilla's {@code ModelPart.rotate} transform without allocating a quaternion. */
    public void applyModelPartTransform(float pivotX, float pivotY, float pivotZ,
                                        float pitch, float yaw, float roll,
                                        float xScale, float yScale, float zScale) {
        translate(pivotX / 16.0F, pivotY / 16.0F, pivotZ / 16.0F);
        if (pitch != 0.0F || yaw != 0.0F || roll != 0.0F) {
            multiply(modelPartRotation.rotationZYX(roll, yaw, pitch));
        }
        if (xScale != 1.0F || yScale != 1.0F || zScale != 1.0F) {
            scale(xScale, yScale, zScale);
        }
    }
}
