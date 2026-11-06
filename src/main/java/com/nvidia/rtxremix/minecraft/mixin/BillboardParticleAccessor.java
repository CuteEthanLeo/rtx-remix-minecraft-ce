/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.particle.BillboardParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BillboardParticle.class)
public interface BillboardParticleAccessor {
    @Accessor("scale")
    float getScale();

    @Accessor("red") float getRed();
    @Accessor("green") float getGreen();
    @Accessor("blue") float getBlue();
    @Accessor("alpha") float getAlpha();
    @Accessor("zRotation") float getAngle();
    @Accessor("lastZRotation") float getPrevAngle();

    @Invoker("getRenderType")
    BillboardParticle.RenderType invokeGetRenderType();

    @Invoker("getMinU")
    float invokeGetMinU();

    @Invoker("getMaxU")
    float invokeGetMaxU();

    @Invoker("getMinV")
    float invokeGetMinV();

    @Invoker("getMaxV")
    float invokeGetMaxV();
}
