/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Formerly read the dispatcher's {@code renderShadows} flag so Remix could suppress blob
 * shadows around its own renders and restore the player's setting.
 *
 * <p>TODO(1.21.11): the flag is gone; shadows are now {@code EntityRenderState.shadowPieces},
 * which the Remix passes clear on the state they render. This accessor is an empty shell.
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
}
