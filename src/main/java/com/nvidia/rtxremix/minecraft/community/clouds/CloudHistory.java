/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.clouds;

/** The scene state the accumulated cloud image was marched for. Render thread only. */
final class CloudHistory {
    private static final double RESET_DISTANCE_SQUARED = 32.0 * 32.0;
    private static final double MOVING_DISTANCE_SQUARED = 0.75 * 0.75;
    private static final double RESET_TIME_TICKS = 200.0;
    private static final double RESET_DIRECTION_COSINE = Math.cos(Math.toRadians(3.0));

    private boolean valid;
    private double cameraX, cameraY, cameraZ;
    private double worldTime, dayTime;
    private float base, sunX, sunY, sunZ;
    private boolean physicalAtmosphere;
    private float skyFill;

    boolean needsReset(CloudSkyCompositor.Inputs frame, double time, float cloudBase) {
        if (!valid || distanceSquared(frame) > RESET_DISTANCE_SQUARED
                || time < worldTime || Math.abs(time - worldTime) > RESET_TIME_TICKS
                || frame.dayTime() < dayTime
                || Math.abs((frame.dayTime() - dayTime) - (time - worldTime)) > RESET_TIME_TICKS
                || Float.compare(base, cloudBase) != 0
                || physicalAtmosphere != frame.physicalAtmosphere()
                || Float.compare(skyFill, frame.skyFill()) != 0) {
            return true;
        }
        float[] sun = frame.sunDirection();
        return sunX * sun[0] + sunY * sun[1] + sunZ * sun[2] < RESET_DIRECTION_COSINE;
    }

    boolean isMoving(CloudSkyCompositor.Inputs frame) {
        return !valid || distanceSquared(frame) > MOVING_DISTANCE_SQUARED;
    }

    void record(CloudSkyCompositor.Inputs frame, double time, float cloudBase) {
        cameraX = frame.cameraX();
        cameraY = frame.cameraY();
        cameraZ = frame.cameraZ();
        worldTime = time;
        dayTime = frame.dayTime();
        base = cloudBase;
        sunX = frame.sunDirection()[0];
        sunY = frame.sunDirection()[1];
        sunZ = frame.sunDirection()[2];
        physicalAtmosphere = frame.physicalAtmosphere();
        skyFill = frame.skyFill();
        valid = true;
    }

    void invalidate() {
        valid = false;
    }

    private double distanceSquared(CloudSkyCompositor.Inputs frame) {
        double dx = frame.cameraX() - cameraX;
        double dy = frame.cameraY() - cameraY;
        double dz = frame.cameraZ() - cameraZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
