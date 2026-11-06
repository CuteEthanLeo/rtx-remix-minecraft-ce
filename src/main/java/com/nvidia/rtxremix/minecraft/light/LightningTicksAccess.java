/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.light;

/** Exposes {@code ClientWorld}'s private lightning tick count, implemented by {@code ClientWorldMixin}. */
public interface LightningTicksAccess {
    int rtxremix$getLightningTicksLeft();
}
