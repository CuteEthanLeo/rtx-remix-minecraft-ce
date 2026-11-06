/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.render.entity.feature.Deadmau5FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Formerly redirected a per-frame {@code getName()} read to the cached player name text.
 *
 * <p>TODO(1.21.11): the renderer now reads names into its render state
 * ({@code updateRenderState}) and the per-frame call this redirected is gone; the mixin
 * is an empty shell until the cache is re-pointed at the render-state extraction.
 */
@Mixin(Deadmau5FeatureRenderer.class)
public abstract class Deadmau5FeatureRendererMixin {
}
