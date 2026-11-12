/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;

/**
 * The camera's orientation in the frame the capture code works in: looking down -Z with +X
 * to its right, so the rotation turns a quad in the XY plane toward the viewer and its
 * conjugate is the view rotation.
 *
 * <p>Minecraft 1.19.4's {@code Camera.getRotation()} instead looks down +Z with +X to its
 * left, half a turn about Y away.
 */
public final class CameraFacing {

    private CameraFacing() {
    }

    /**
     * Writes the camera's orientation into {@code dest}.
     *
     * @return {@code dest}
     */
    public static Quaternionf get(Camera camera, Quaternionf dest) {
        Quaternionf rotation = camera.getRotation();
        // The product of the rotation with a half turn about Y, whose quaternion is exactly (0, 1, 0, 0).
        return dest.set(-rotation.z, rotation.w, rotation.x, -rotation.y);
    }
}
