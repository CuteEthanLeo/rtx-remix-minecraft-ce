/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Modified by RTX Remix for Minecraft Community Edition; see COMMUNITY.md.
 */
package com.nvidia.rtxremix.minecraft.settings;

/** Top-level pages in the RTX settings menu. */
enum SettingCategory {
    PERFORMANCE("rtxremix.settings.category.performance"),
    LIGHTING("rtxremix.settings.category.lighting"),
    IMAGE("rtxremix.settings.category.image"),
    MATERIALS("rtxremix.settings.category.materials"),
    COMMUNITY("rtxremix.settings.category.community");

    private final String labelKey;

    SettingCategory(String labelKey) {
        this.labelKey = labelKey;
    }

    String labelKey() {
        return labelKey;
    }
}
