/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.perf;

/**
 * Whether the puddle decals must stay in the scene.
 *
 * <p>At wetness level 0 the puddle mask is alpha 0 in every texel, so the
 * decals draw nothing, yet every ray that reaches a hard, rain-exposed block
 * top still tests them. Dry decals can therefore leave the scene with no
 * change to the image, provided they are back before the mask shows a puddle.
 *
 * <p>They become visible as soon as any non-zero level is wanted or being
 * computed, before its texture upload, and leave only after level 0 is
 * uploaded, nothing is computing and {@link #HIDE_AFTER_DRY_FRAMES} more frames
 * have passed. The margin covers the runtime applying a texture upload and an
 * instance update in different frames. Starts visible.
 */
public final class PuddleDecalVisibility {

    public static final int HIDE_AFTER_DRY_FRAMES = 3;

    private int dryFrames;
    private boolean visible = true;

    /**
     * Advances one frame.
     *
     * @param shownLevel the mask level last uploaded
     * @param computingLevel the level being computed, or -1 when none is
     * @param targetLevel the level the ground's wetness asks for
     * @return whether the decals must be in the scene this frame
     */
    public boolean update(int shownLevel, int computingLevel, int targetLevel) {
        boolean wet = shownLevel > 0 || computingLevel > 0 || targetLevel > 0;
        if (wet) {
            dryFrames = 0;
            visible = true;
        } else if (visible && ++dryFrames >= HIDE_AFTER_DRY_FRAMES) {
            visible = false;
        }
        return visible;
    }

    public boolean visible() {
        return visible;
    }
}
