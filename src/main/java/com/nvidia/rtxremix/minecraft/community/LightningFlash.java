/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * Lightning that lights the storm: each bolt becomes a short column of very
 * bright, cold lights that scatter strongly into the rain haze, and every
 * flash briefly raises the sky ambient, so the whole storm lights up and the
 * ground throws hard shadows for an instant.
 *
 * <p>Vanilla already times the flash: while any bolt is in its bright phase
 * the client world's {@code lightningTicksLeft} is held above zero, and a bolt
 * re-flashes a few times before it fades. {@link #advance} turns that on/off
 * signal into a level that jumps to 1 on a flash and decays within a tenth of
 * a second, which keeps vanilla's flicker crisp without single-frame pops.
 *
 * <p>Render thread only.
 */
public final class LightningFlash {

    public static final boolean DEFAULT_ENABLED = true;
    /** Radiance of each light along a bolt at full flash. */
    public static final float DEFAULT_BOLT_RADIANCE = 1200.0f;
    /** Heights of the bolt lights above the strike point, in blocks. */
    static final float[] BOLT_LIGHT_HEIGHTS = {1.5f, 24.0f, 64.0f};
    /** Emitter radius of each bolt light; larger sources soften the flash shadows a little. */
    public static final float BOLT_LIGHT_RADIUS = 1.5f;
    /** Bolt lights scatter this much more into the haze than into surfaces. */
    public static final float BOLT_VOLUMETRIC_SCALE = 4.0f;
    /** Cold, slightly blue white. */
    static final float[] BOLT_TINT = {0.85f, 0.9f, 1.0f};
    /** Extra sky-ambient multiplier at full flash, for strikes too far away to light directly. */
    static final float SKY_FLASH_AMBIENT = 1.5f;
    /** Half-life of the decay after a flash, in seconds. */
    static final float DECAY_HALF_LIFE_SECONDS = 0.035f;

    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile float boltRadiance = DEFAULT_BOLT_RADIANCE;
    private static float level;

    private LightningFlash() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    /** Live tuning for {@code /rtx ce lightning}. */
    public static float getBoltRadiance() { return boltRadiance; }
    public static void setBoltRadiance(float value) { boltRadiance = Math.max(0.0f, value); }

    /** Number of lights along one bolt. */
    public static int boltLightCount() {
        return BOLT_LIGHT_HEIGHTS.length;
    }

    /** Height above the strike point of light {@code index} along a bolt. */
    public static float boltLightHeight(int index) {
        return BOLT_LIGHT_HEIGHTS[index];
    }

    /**
     * Advances the flash level by one frame.
     *
     * @param lightningTicksLeft the client world's flash counter, above zero while a bolt is bright
     */
    public static void advance(int lightningTicksLeft, float dtSeconds) {
        level = enabled ? nextLevel(level, lightningTicksLeft > 0, dtSeconds) : 0.0f;
    }

    static float nextLevel(float previous, boolean flashing, float dtSeconds) {
        if (flashing) {
            return 1.0f;
        }
        float decayed = previous * (float) Math.pow(0.5, Math.max(0.0f, dtSeconds) / DECAY_HALF_LIFE_SECONDS);
        return decayed < 1.0e-3f ? 0.0f : decayed;
    }

    /** The current flash level, 0..1. */
    public static float level() {
        return level;
    }

    /** RGB radiance of each bolt light at the current level, or null when it is dark. */
    public static float[] boltRadianceNow() {
        float scale = level * boltRadiance;
        if (scale <= 0.0f) {
            return null;
        }
        return new float[]{BOLT_TINT[0] * scale, BOLT_TINT[1] * scale, BOLT_TINT[2] * scale};
    }

    /** Extra sky-ambient multiplier for the current flash, scaled by how stormy the sky is. */
    public static float skyAmbientBoost(float weatherOpacity) {
        return SKY_FLASH_AMBIENT * level * Math.max(0.0f, Math.min(1.0f, weatherOpacity));
    }

    /** Clears the flash, for a world change. */
    public static void reset() {
        level = 0.0f;
    }
}
