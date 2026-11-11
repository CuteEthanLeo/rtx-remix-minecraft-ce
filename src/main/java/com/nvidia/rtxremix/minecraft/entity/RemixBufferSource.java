/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * A buffer per render type: the shape the capture passes render into.
 * {@link RemixCommandCapture} has vanilla's feature dispatcher replay submitted commands
 * into it through {@link CapturingImmediate}.
 */
@FunctionalInterface
public interface RemixBufferSource {
    VertexConsumer getBuffer(RenderType layer);
}
