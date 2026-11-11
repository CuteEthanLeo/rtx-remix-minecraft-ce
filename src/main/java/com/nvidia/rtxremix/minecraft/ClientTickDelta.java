/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import com.nvidia.rtxremix.minecraft.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;

/**
 * The partial tick vanilla draws the current frame with.
 *
 * <p>Minecraft 1.20.4 has no single owner for it: the render tick counter keeps advancing
 * while the game is paused, {@code MinecraftClient} holds the value it had at the pause, and
 * {@code WorldRenderer} substitutes 1 while the world's ticks are frozen. These two reads
 * are the ones {@code GameRenderer.render} and {@code WorldRenderer.render} work with.
 */
public final class ClientTickDelta {

    private ClientTickDelta() {
    }

    /** The frame's partial tick, held at its value from the moment the game paused. */
    public static float frame(MinecraftClient client) {
        return client.isPaused()
                ? ((MinecraftClientAccessor) client).rtxremix$getPausedTickDelta()
                : client.getTickDelta();
    }

    /**
     * {@link #frame} for motion that follows the tick rate: 1 while the world's ticks are
     * frozen, so nothing interpolates between two ticks that will not advance.
     */
    public static float ticking(MinecraftClient client) {
        return client.world == null || client.world.getTickManager().shouldTick()
                ? frame(client)
                : 1.0f;
    }
}
