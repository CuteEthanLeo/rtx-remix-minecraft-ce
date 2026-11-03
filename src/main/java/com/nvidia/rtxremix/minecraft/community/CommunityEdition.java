/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * Where the Community Edition comes from. Update {@link #UPSTREAM_BASE} with
 * every merge of upstream, as COMMUNITY.md describes.
 */
public final class CommunityEdition {

    public static final String NAME = "RTX Remix for Minecraft Community Edition (Unofficial)";
    public static final String UPSTREAM_REPOSITORY = "https://github.com/NVIDIAGameWorks/rtx-remix-minecraft";
    /** The upstream commit this edition was last merged with. */
    public static final String UPSTREAM_BASE = "a518eff";

    private CommunityEdition() {}

    /** One log line naming the edition and its upstream base. */
    public static String banner() {
        return NAME + ", based on " + UPSTREAM_REPOSITORY + " at " + UPSTREAM_BASE
                + "; not affiliated with or endorsed by NVIDIA";
    }
}
