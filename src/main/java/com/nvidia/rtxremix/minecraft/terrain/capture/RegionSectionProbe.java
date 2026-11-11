/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.capture;

/**
 * Whether a render region snapshot holds any block. On a region the answer is for the section
 * being built, the centre of its 27 snapshots; on a section snapshot it is for that section.
 *
 * <p>Vanilla builds a region for every section, air included. A section with no blocks has
 * nothing to capture: its empty render data makes it a removal candidate instead; see
 * {@code BuiltChunkMixin}.
 */
public interface RegionSectionProbe {
    boolean rtxremix$isSectionEmpty();
}
