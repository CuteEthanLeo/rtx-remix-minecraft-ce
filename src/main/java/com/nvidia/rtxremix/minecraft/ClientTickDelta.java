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
 * <p>Minecraft 1.19.4 has no single owner for it: the render tick counter keeps advancing
 * while the game is paused and {@code MinecraftClient} holds the value it had at the pause.
 * This read is the one {@code GameRenderer.render} and {@code WorldRenderer.render} work
 * with; the tick rate cannot be frozen on this version.
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
}
