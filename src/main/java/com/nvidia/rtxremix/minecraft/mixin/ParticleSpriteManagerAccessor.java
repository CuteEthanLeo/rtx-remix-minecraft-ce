/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.resources.Identifier;

@Mixin(ParticleResources.class)
public interface ParticleSpriteManagerAccessor {
    /**
     * Sprite providers keyed by particle type id. The value class is a nested vanilla
     * type, so callers read it through the public {@code SpriteProvider} interface it
     * implements.
     */
    @Accessor("spriteSets")
    Map<Identifier, ?> getSpriteAwareFactories();
}
