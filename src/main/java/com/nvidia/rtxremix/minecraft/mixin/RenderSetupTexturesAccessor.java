/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderSetup;

/**
 * Sampler name to texture spec. The spec record is package-private in vanilla, so its
 * texture id is read through {@link RenderSetupTextureSpecAccessor}.
 */
@Mixin(RenderSetup.class)
public interface RenderSetupTexturesAccessor {
    @Accessor("textures")
    Map<String, ?> rtxremix$getTextures();
}
