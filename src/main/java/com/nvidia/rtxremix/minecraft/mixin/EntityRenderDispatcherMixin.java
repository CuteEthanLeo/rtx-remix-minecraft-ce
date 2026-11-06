/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.render.entity.EntityRenderManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Formerly bracketed each {@code EntityRenderDispatcher.render()} with a capture boundary
 * on the thread-local {@code EntityRenderContext}.
 *
 * <p>Since 1.21.9 the dispatcher only submits commands to an
 * {@code OrderedRenderCommandQueue}; vertices are written later, when the queue is
 * flushed. {@code RemixEntityPass} therefore opens the boundary itself around both the
 * render and the flush of its private queue, and this mixin is an empty shell.
 * TODO(1.21.11): drop it from the mixin config once nothing needs the target.
 */
@Mixin(EntityRenderManager.class)
public abstract class EntityRenderDispatcherMixin {
}
