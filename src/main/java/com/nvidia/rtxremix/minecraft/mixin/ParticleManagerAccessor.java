/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleRenderer;
import net.minecraft.client.particle.ParticleSpriteManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ParticleManager.class)
public interface ParticleManagerAccessor {
    /** One renderer per texture sheet since 1.21.9; each renderer owns its particle queue. */
    @Accessor("particles")
    Map<ParticleTextureSheet, ParticleRenderer<?>> getParticles();

    /** Sprite providers moved to {@link ParticleSpriteManager}; see {@link ParticleSpriteManagerAccessor}. */
    @Accessor("spriteManager")
    ParticleSpriteManager rtxremix$getSpriteManager();
}
