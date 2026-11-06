/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Formerly replaced vanilla's per-face model part selection with an allocation-free port.
 *
 * <p>TODO(1.21.11): 1.21.5 replaced the baked-model getQuads path with
 * {@code BlockStateModel.addParts(Random, List<BlockModelPart>)}, which already caches the
 * selected parts and no longer allocates a fresh Random per part. The allocation-free port
 * is disabled until it is re-measured against the new model pipeline; this mixin is an
 * empty shell so the mixin config keeps loading.
 */
@Mixin(WeightedVariants.class)
public abstract class WeightedBakedModelMixin {
}
