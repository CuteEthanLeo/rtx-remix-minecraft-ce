/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Position and liveness; colour and rotation live on {@link BillboardParticleAccessor} since 1.21.9. */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("x") double getX();
    @Accessor("y") double getY();
    @Accessor("z") double getZ();
    @Accessor("lastX") double getPrevPosX();
    @Accessor("lastY") double getPrevPosY();
    @Accessor("lastZ") double getPrevPosZ();
    @Accessor("dead") boolean getDead();
}
