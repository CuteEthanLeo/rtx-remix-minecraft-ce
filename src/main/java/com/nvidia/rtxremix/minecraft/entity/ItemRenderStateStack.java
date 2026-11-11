/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import net.minecraft.item.ItemStack;

/**
 * Implemented on vanilla's {@code ItemRenderState} by {@code ItemRenderStateMixin}.
 *
 * <p>Since 1.21.4 an item is drawn from a render state that no longer carries its
 * {@link ItemStack}, while the capture still classifies a held block and lights a held
 * torch by the stack. {@code ItemModelManagerMixin} records the stack here as the state is
 * filled, and the state hands it to {@link EntityRenderContext} while it draws.
 */
public interface ItemRenderStateStack {

    /**
     * Records the stack this state is being updated from. The state keeps the reference,
     * not a copy: vanilla fills and draws a state within one frame on the render thread.
     */
    void rtxremix$setStack(ItemStack stack);
}
