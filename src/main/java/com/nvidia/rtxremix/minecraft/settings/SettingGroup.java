/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Modified by RTX Remix for Minecraft Community Edition; see COMMUNITY.md.
 */
package com.nvidia.rtxremix.minecraft.settings;

/** Ordering of constants drives ordering of rows in the settings screen. */
public enum SettingGroup {
    RENDER_DISTANCE(SettingCategory.PERFORMANCE, "rtxremix.settings.group.renderDistance"),
    FRAMERATE(SettingCategory.PERFORMANCE, "rtxremix.settings.group.framerate"),
    SKY(SettingCategory.LIGHTING, "rtxremix.settings.group.sky"),
    CLOUDS(SettingCategory.LIGHTING, "rtxremix.settings.group.clouds"),
    BLOCK_LIGHTS(SettingCategory.LIGHTING, "rtxremix.settings.group.blockLights"),
    TONEMAPPING(SettingCategory.IMAGE, "rtxremix.settings.group.tonemapping"),
    EYE_ADAPTATION(SettingCategory.IMAGE, "rtxremix.settings.group.eyeAdaptation"),
    CAMERA_EFFECTS(SettingCategory.IMAGE, "rtxremix.settings.group.cameraEffects"),
    MATERIALS(SettingCategory.MATERIALS, "rtxremix.settings.group.materials"),
    COMMUNITY_LIGHT(SettingCategory.COMMUNITY, "rtxremix.settings.group.communityLight"),
    COMMUNITY_WEATHER(SettingCategory.COMMUNITY, "rtxremix.settings.group.communityWeather"),
    COMMUNITY_WATER(SettingCategory.COMMUNITY, "rtxremix.settings.group.communityWater"),
    COMMUNITY_NIGHT(SettingCategory.COMMUNITY, "rtxremix.settings.group.communityNight");

    private final SettingCategory category;
    private final String labelKey;

    SettingGroup(SettingCategory category, String labelKey) {
        this.category = category;
        this.labelKey = labelKey;
    }

    SettingCategory category() {
        return category;
    }

    String labelKey() {
        return labelKey;
    }
}
