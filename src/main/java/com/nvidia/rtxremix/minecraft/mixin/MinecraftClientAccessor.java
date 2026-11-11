/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the partial tick the client froze when it paused; see {@code ClientTickDelta}. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("pausedTickDelta")
    float rtxremix$getPausedTickDelta();
}
