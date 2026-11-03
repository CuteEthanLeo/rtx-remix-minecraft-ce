/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * The Community Edition's look switches. Each one turns a group of changes on
 * or off as a whole, so a player can compare it against the upstream look; the
 * settings screen binds them, and the renderers read them every frame.
 *
 * <p>Values written from the settings screen are read on the render thread,
 * so they are volatile, the same as the sky settings they sit beside.
 */
public final class CommunityLook {

    public static final boolean DEFAULT_OVERCAST_WEATHER = true;
    public static final boolean DEFAULT_CLOUD_TINT = true;
    public static final boolean DEFAULT_STARRY_NIGHT = true;

    private static volatile boolean overcastWeather = DEFAULT_OVERCAST_WEATHER;
    private static volatile boolean cloudTint = DEFAULT_CLOUD_TINT;
    private static volatile boolean starryNight = DEFAULT_STARRY_NIGHT;

    private CommunityLook() {}

    /** Overcast sky: a dimmer rain dome and a soft daytime sky ambient while it rains. */
    public static boolean isOvercastWeather() { return overcastWeather; }
    public static void setOvercastWeather(boolean enabled) { overcastWeather = enabled; }

    /** Clouds follow the time of day and the weather instead of staying near white. */
    public static boolean isCloudTint() { return cloudTint; }
    public static void setCloudTint(boolean enabled) { cloudTint = enabled; }

    /** The Community Edition star field, Milky Way, and moonlight. */
    public static boolean isStarryNight() { return starryNight; }
    public static void setStarryNight(boolean enabled) { starryNight = enabled; }
}
