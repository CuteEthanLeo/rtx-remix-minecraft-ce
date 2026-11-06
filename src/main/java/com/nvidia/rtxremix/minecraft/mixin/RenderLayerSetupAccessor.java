/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.11 replaced RenderLayer.MultiPhase phases with one {@link RenderSetup}; the capture reads its textures. */
@Mixin(RenderType.class)
public interface RenderLayerSetupAccessor {
    @Accessor("state")
    RenderSetup rtxremix$getRenderSetup();
}
