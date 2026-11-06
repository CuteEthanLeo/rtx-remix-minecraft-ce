/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.texture.Sprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.9 folded SpriteBillboardParticle into {@link BillboardParticle}; the sprite lives there. */
@Mixin(BillboardParticle.class)
public interface SpriteBillboardParticleAccessor {
    @Accessor("sprite")
    Sprite getSprite();
}
