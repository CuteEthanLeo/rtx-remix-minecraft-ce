/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * A buffer per render type. Minecraft 26.x removed {@code MultiBufferSource}: its feature
 * renderers write into a staged GPU buffer, so the capture passes keep this shape for
 * themselves and {@link RemixCommandCapture} replays submitted commands into it.
 */
@FunctionalInterface
public interface RemixBufferSource {
    VertexConsumer getBuffer(RenderType layer);
}
