/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import net.minecraft.client.Camera;

/**
 * Duck interface on {@code GameRenderer} (implemented by {@code GameRendererMixin}) exposing the
 * world FOV, which Minecraft 1.21.11 made private.
 */
public interface GameRendererFovAccess {
    float rtxremix$getFov(Camera camera, float tickDelta, boolean changingFov);
}
