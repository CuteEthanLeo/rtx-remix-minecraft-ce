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
 *
 * <p>The shutter is a time, not a share of a frame. Remix stretches each pixel
 * along its motion over one frame times {@code exposureFraction}, with no frame
 * time in the formula, so a fixed fraction blurs half as far at 120 fps as at
 * 60. {@link #onFrame(long)} divides the shutter time by a smoothed frame time
 * every frame instead, so a streak is as long at any frame rate as a real
 * camera with that shutter would draw it.
 */
public final class CameraEffectsSettings {

    /** Motion blur quality steps, as the integer setting stores them. */
    public static final int OFF = 0;
    public static final int STANDARD = 1;
    public static final int HIGH = 2;
    public static final int CINEMATIC = 3;
    public static final int ULTRA = 4;

    public static final int DEFAULT_QUALITY = OFF;
    /** Shutter time in milliseconds: 1/48 s, a film camera's 180-degree shutter at 24 fps. */
    public static final float DEFAULT_SHUTTER_MS = 1000.0f / 48.0f;
    /** 1/250 s. */
    public static final float MIN_SHUTTER_MS = 4.0f;
    /** 1/24 s, a full-open shutter at film frame rate. */
    public static final float MAX_SHUTTER_MS = 1000.0f / 24.0f;
    public static final float DEFAULT_MAX_BLUR = 0.06f;
    public static final float MIN_MAX_BLUR = 0.01f;
    public static final float MAX_MAX_BLUR = 0.25f;

    /** Bounds on the per-frame fraction written to Remix; its own UI allows 0.01 to 3. */
    static final float MIN_EXPOSURE_FRACTION = 0.05f;
    static final float MAX_EXPOSURE_FRACTION = 3.0f;
    /** Relative change of the fraction that is written; smaller drifts are not worth a config write. */
    static final float EXPOSURE_WRITE_THRESHOLD = 0.02f;
    /** Weight of the newest frame in the smoothed frame time. */
    static final float FRAME_TIME_SMOOTHING = 0.1f;
    /** Frame time assumed before any frame is measured. */
    static final float INITIAL_FRAME_SECONDS = 1.0f / 60.0f;
    /** Frame times outside this range (a hitch, a loading screen, a pause) are not averaged in. */
    static final float MIN_FRAME_SECONDS = 1.0f / 1000.0f;
    static final float MAX_FRAME_SECONDS = 0.25f;

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
    private float shutterMs = DEFAULT_SHUTTER_MS;
    private float maxBlur = DEFAULT_MAX_BLUR;

    private float smoothedFrameSeconds = INITIAL_FRAME_SECONDS;
    private long lastFrameNanos;
    private boolean hasLastFrame;
    /** Fraction last written to Remix, or a negative value when none was. */
    private float writtenExposureFraction = -1.0f;

    public CameraEffectsSettings(BiConsumer<String, String> configWriter) {
        this.configWriter = configWriter;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int value) {
        quality = Math.max(OFF, Math.min(ULTRA, value));
        apply();
    }

    public float getShutterMs() {
        return shutterMs;
    }

    public void setShutterMs(float value) {
        shutterMs = Math.max(MIN_SHUTTER_MS, Math.min(MAX_SHUTTER_MS, value));
        apply();
    }

    public float getMaxBlur() {
        return maxBlur;
    }

    public void setMaxBlur(float value) {
        maxBlur = Math.max(MIN_MAX_BLUR, Math.min(MAX_MAX_BLUR, value));
        apply();
    }

    /** Writes the current state again, for after something else rewrote the post-effect options. */
    public void reapply() {
        apply();
    }

    /**
     * Folds this frame's time into the smoothed frame time and, while blur is on, updates
     * Remix's exposure fraction when it moved enough to matter. Call once per rendered frame.
     */
    public void onFrame(long nowNanos) {
        if (hasLastFrame) {
            float seconds = (nowNanos - lastFrameNanos) * 1.0e-9f;
            if (seconds >= MIN_FRAME_SECONDS && seconds <= MAX_FRAME_SECONDS) {
                smoothedFrameSeconds += (seconds - smoothedFrameSeconds) * FRAME_TIME_SMOOTHING;
            }
        }
        lastFrameNanos = nowNanos;
        hasLastFrame = true;
        if (quality == OFF) {
            return;
        }
        float fraction = exposureFraction(shutterMs, smoothedFrameSeconds);
        if (writtenExposureFraction < 0.0f
                || Math.abs(fraction - writtenExposureFraction)
                        > EXPOSURE_WRITE_THRESHOLD * writtenExposureFraction) {
            writeExposureFraction(fraction);
        }
    }

    /** Share of a frame the shutter stays open for, given its time and the frame time. */
    static float exposureFraction(float shutterMs, float frameSeconds) {
        float fraction = shutterMs * 1.0e-3f / Math.max(MIN_FRAME_SECONDS, frameSeconds);
        return Math.max(MIN_EXPOSURE_FRACTION, Math.min(MAX_EXPOSURE_FRACTION, fraction));
    }

    private void apply() {
        boolean on = quality != OFF;
        write(ENABLE_KEY, on);
        write(MOTION_BLUR_KEY, on);
        write(CHROMATIC_ABERRATION_KEY, false);
        write(VIGNETTE_KEY, false);
        if (!on) {
            writtenExposureFraction = -1.0f;
            return;
        }
        configWriter.accept(SAMPLE_COUNT_KEY, Integer.toString(sampleCount(quality)));
        // Without noise sampling Remix's two sample walkers degenerate: one takes every full
        // step and the other only the minimum, so the streak is drawn to one side only.
        write(NOISE_SAMPLE_KEY, true);
        configWriter.accept(JITTER_KEY, Float.toString(quality >= CINEMATIC ? 0.8f : 0.6f));
        write(EMISSIVE_KEY, quality >= HIGH);
        configWriter.accept(MIN_VELOCITY_KEY, Float.toString(1.0f));
        configWriter.accept(MAX_BLUR_KEY, Float.toString(maxBlur));
        writeExposureFraction(exposureFraction(shutterMs, smoothedFrameSeconds));
    }

    private void writeExposureFraction(float fraction) {
        configWriter.accept(SHUTTER_KEY, Float.toString(fraction));
        writtenExposureFraction = fraction;
    }

    static int sampleCount(int quality) {
        return switch (quality) {
            case STANDARD -> 8;
            case HIGH -> 16;
            case CINEMATIC -> 32;
            case ULTRA -> 48;
            default -> 0;
        };
    }

    private void write(String key, boolean value) {
        configWriter.accept(key, Boolean.toString(value));
    }
}
