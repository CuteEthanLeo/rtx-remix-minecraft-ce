/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import java.util.Queue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 26.x dropped {@code ParticleGroup.getAll()}; this reads the live queue in its place. */
@Mixin(ParticleGroup.class)
public interface ParticleGroupAccessor {
    @Accessor("particles")
    Queue<? extends Particle> rtxremix$getParticles();
}
