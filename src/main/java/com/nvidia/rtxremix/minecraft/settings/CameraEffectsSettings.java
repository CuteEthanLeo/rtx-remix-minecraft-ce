/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.settings;

import java.util.function.BiConsumer;

/**
 * Motion blur through the Remix post-effects pass, off by default.
 *
 * <p>Remix gates motion blur, chromatic aberration, and vignette behind one
 * {@code rtx.postfx.enable} switch, and its graphics presets write that switch
 * to the user configuration. Every apply therefore writes the whole set: the
 * master switch, motion blur, and the other two effects explicitly off, so a
 * preset or a runtime default never brings them along.
 */
public final class CameraEffectsSettings {

    /** Motion blur quality steps, as the integer setting stores them. */
    public static final int OFF = 0;
    public static final int STANDARD = 1;
    public static final int HIGH = 2;
    public static final int CINEMATIC = 3;

    public static final int DEFAULT_QUALITY = OFF;
    public static final float DEFAULT_SHUTTER = 0.4f;
    public static final float DEFAULT_MAX_BLUR = 0.03f;

    static final String ENABLE_KEY = "rtx.postfx.enable";
    static final String MOTION_BLUR_KEY = "rtx.postfx.enableMotionBlur";
    static final String CHROMATIC_ABERRATION_KEY = "rtx.postfx.enableChromaticAberration";
    static final String VIGNETTE_KEY = "rtx.postfx.enableVignette";
    static final String SAMPLE_COUNT_KEY = "rtx.postfx.motionBlurSampleCount";
    static final String NOISE_SAMPLE_KEY = "rtx.postfx.enableMotionBlurNoiseSample";
    static final String JITTER_KEY = "rtx.postfx.motionBlurJitterStrength";
    static final String EMISSIVE_KEY = "rtx.postfx.enableMotionBlurEmissive";
    static final String MIN_VELOCITY_KEY = "rtx.postfx.motionBlurMinimumVelocityThresholdInPixel";
    static final String SHUTTER_KEY = "rtx.postfx.exposureFraction";
    static final String MAX_BLUR_KEY = "rtx.postfx.blurDiameterFraction";

    private final BiConsumer<String, String> configWriter;

    private int quality = DEFAULT_QUALITY;
    private float shutter = DEFAULT_SHUTTER;
    private float maxBlur = DEFAULT_MAX_BLUR;

    public CameraEffectsSettings(BiConsumer<String, String> configWriter) {
        this.configWriter = configWriter;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int value) {
        quality = Math.max(OFF, Math.min(CINEMATIC, value));
        apply();
    }

    public float getShutter() {
        return shutter;
    }

    public void setShutter(float value) {
        shutter = value;
        apply();
    }

    public float getMaxBlur() {
        return maxBlur;
    }

    public void setMaxBlur(float value) {
        maxBlur = value;
        apply();
    }

    /** Writes the current state again, for after something else rewrote the post-effect options. */
    public void reapply() {
        apply();
    }

    private void apply() {
        boolean on = quality != OFF;
        write(ENABLE_KEY, on);
        write(MOTION_BLUR_KEY, on);
        write(CHROMATIC_ABERRATION_KEY, false);
        write(VIGNETTE_KEY, false);
        if (!on) {
            return;
        }
        configWriter.accept(SAMPLE_COUNT_KEY, Integer.toString(sampleCount(quality)));
        write(NOISE_SAMPLE_KEY, quality >= HIGH);
        configWriter.accept(JITTER_KEY, Float.toString(quality == CINEMATIC ? 0.8f : 0.6f));
        write(EMISSIVE_KEY, quality >= HIGH);
        configWriter.accept(MIN_VELOCITY_KEY, Float.toString(1.0f));
        configWriter.accept(SHUTTER_KEY, Float.toString(shutter));
        configWriter.accept(MAX_BLUR_KEY, Float.toString(maxBlur));
    }

    static int sampleCount(int quality) {
        return switch (quality) {
            case STANDARD -> 8;
            case HIGH -> 16;
            case CINEMATIC -> 32;
            default -> 0;
        };
    }

    private void write(String key, boolean value) {
        configWriter.accept(key, Boolean.toString(value));
    }
}
