/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleResources;

@Mixin(ParticleEngine.class)
public interface ParticleManagerAccessor {
    /** One renderer per texture sheet since 1.21.9; each renderer owns its particle queue. */
    @Accessor("particles")
    Map<ParticleRenderType, ParticleGroup<?>> getParticles();

    /** Sprite providers moved to {@link ParticleResources}; see {@link ParticleSpriteManagerAccessor}. */
    @Accessor("resourceManager")
    ParticleResources rtxremix$getSpriteManager();
}
