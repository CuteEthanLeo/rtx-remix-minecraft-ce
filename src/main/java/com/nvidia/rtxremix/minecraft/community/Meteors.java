/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import java.util.Random;

/**
 * Shooting stars for the starry night: one at a time, at random intervals,
 * more often on a meteor-shower night.
 *
 * <p>A meteor starts 30 to 80 degrees up, falls along a great circle at 15 to
 * 35 degrees a second, and lives under a second and a half; its tail grows to
 * 6 to 14 degrees behind the head. It brightens fast and fades out. Meteors
 * move in real time, not with the sky, because they burn up in the
 * atmosphere. This class only decides where they are; {@code NightSkyRenderer}
 * draws them.
 */
public final class Meteors {

    /** Mean seconds between meteors on an ordinary night. */
    static final float MEAN_INTERVAL_SECONDS = 7.0f;
    /** Mean seconds between meteors on a shower night. */
    static final float SHOWER_INTERVAL_SECONDS = 1.6f;
    /** One night in this many is a meteor-shower night. */
    static final int SHOWER_NIGHT_PERIOD = 5;
    /** Radius of the sphere the streaks are drawn on, inside the stars at 100. */
    public static final float RADIUS = 90.0f;
    /** Half-width of the streak at the head, in blocks at {@link #RADIUS}. */
    static final float HALF_WIDTH = 0.35f;
    /** Stars must be at least this visible for a meteor to start. */
    static final float MIN_VISIBILITY = 0.3f;

    private final Random random;
    private long nextSpawnNanos = -1L;
    private boolean active;
    private long startNanos;
    private float durationSeconds;
    private float angularSpeed;
    private float trailAngle;
    private float peakBrightness;
    private final float[] start = new float[3];
    private final float[] axis = new float[3];

    // Current streak, valid after update() returned true.
    private final float[] head = new float[3];
    private final float[] tail = new float[3];
    private float brightness;

    public Meteors() {
        this(new Random());
    }

    Meteors(Random random) {
        this.random = random;
    }

    /**
     * Advances the meteor to {@code nowNanos} and starts a new one when it is due.
     *
     * @param visibility how visible the stars are, 0 to 1 (night and clear sky)
     * @param day the world's day number, which picks the shower nights
     * @return whether a meteor is in flight and should be drawn
     */
    public boolean update(long nowNanos, float visibility, long day) {
        boolean shower = Math.floorMod(day, SHOWER_NIGHT_PERIOD) == 0;
        if (nextSpawnNanos < 0L) {
            scheduleNext(nowNanos, shower);
        }
        if (!active && visibility >= MIN_VISIBILITY && nowNanos >= nextSpawnNanos) {
            spawn(nowNanos);
        }
        if (!active) {
            return false;
        }
        float age = (nowNanos - startNanos) * 1.0e-9f;
        if (age >= durationSeconds || age < 0.0f) {
            active = false;
            scheduleNext(nowNanos, shower);
            return false;
        }
        float travelled = age * angularSpeed;
        rotate(start, axis, travelled, head);
        rotate(start, axis, Math.max(0.0f, travelled - trailAngle), tail);
        brightness = peakBrightness * envelope(age / durationSeconds) * clamp01(visibility);
        return brightness > 0.0f;
    }

    /** Unit direction of the head, valid after {@link #update} returned true. */
    public float[] head() { return head; }

    /** Unit direction of the end of the tail. */
    public float[] tail() { return tail; }

    /** Brightness factor for the streak's radiance. */
    public float brightness() { return brightness; }

    /**
     * Writes a 3x4 row-major transform that maps the unit streak quad (x from tail 0 to head 1,
     * y from -0.5 to 0.5) onto the sky around {@code camera}.
     */
    public void writeTransform(double cameraX, double cameraY, double cameraZ, float[] matrix) {
        float ax = (head[0] - tail[0]) * RADIUS;
        float ay = (head[1] - tail[1]) * RADIUS;
        float az = (head[2] - tail[2]) * RADIUS;
        // Across the streak: perpendicular to it and to the line of sight.
        float sx = head[1] * az - head[2] * ay;
        float sy = head[2] * ax - head[0] * az;
        float sz = head[0] * ay - head[1] * ax;
        float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        float width = 2.0f * HALF_WIDTH;
        if (length > 1.0e-6f) {
            sx = sx / length * width;
            sy = sy / length * width;
            sz = sz / length * width;
        }
        matrix[0] = ax;
        matrix[1] = sx;
        matrix[2] = head[0];
        matrix[3] = (float) cameraX + tail[0] * RADIUS;
        matrix[4] = ay;
        matrix[5] = sy;
        matrix[6] = head[1];
        matrix[7] = (float) cameraY + tail[1] * RADIUS;
        matrix[8] = az;
        matrix[9] = sz;
        matrix[10] = head[2];
        matrix[11] = (float) cameraZ + tail[2] * RADIUS;
    }

    /** Brightness over a meteor's life, 0 to 1: a fast flare-up, then a fade. */
    static float envelope(float life) {
        if (life <= 0.0f || life >= 1.0f) {
            return 0.0f;
        }
        float rise = Math.min(1.0f, life / 0.15f);
        float fall = 1.0f - smoothstep(0.55f, 1.0f, life);
        return rise * rise * fall;
    }

    private void scheduleNext(long nowNanos, boolean shower) {
        float mean = shower ? SHOWER_INTERVAL_SECONDS : MEAN_INTERVAL_SECONDS;
        // Exponential waiting time, kept from bunching or stalling.
        float wait = (float) (-Math.log(1.0 - random.nextDouble() * 0.95)) * mean;
        wait = Math.max(0.4f * mean, Math.min(3.0f * mean, wait));
        nextSpawnNanos = nowNanos + (long) (wait * 1.0e9f);
    }

    private void spawn(long nowNanos) {
        double elevation = Math.toRadians(30.0 + 50.0 * random.nextDouble());
        double azimuth = random.nextDouble() * Math.PI * 2.0;
        start[0] = (float) (Math.cos(elevation) * Math.cos(azimuth));
        start[1] = (float) Math.sin(elevation);
        start[2] = (float) (Math.cos(elevation) * Math.sin(azimuth));

        // A direction of travel tangent to the sky, mostly downward.
        double heading = Math.toRadians(-90.0 + (random.nextDouble() - 0.5) * 150.0);
        float[] east = {(float) -Math.sin(azimuth), 0.0f, (float) Math.cos(azimuth)};
        float[] up = cross(east, start);
        float c = (float) Math.cos(heading);
        float s = (float) Math.sin(heading);
        float[] travel = {
                east[0] * c + up[0] * s,
                east[1] * c + up[1] * s,
                east[2] * c + up[2] * s};
        float[] spin = cross(start, travel);
        normalize(spin);
        System.arraycopy(spin, 0, axis, 0, 3);

        angularSpeed = (float) Math.toRadians(15.0 + 20.0 * random.nextDouble());
        durationSeconds = 0.5f + 0.9f * random.nextFloat();
        trailAngle = (float) Math.toRadians(6.0 + 8.0 * random.nextDouble());
        peakBrightness = 0.5f + random.nextFloat();
        startNanos = nowNanos;
        active = true;
    }

    /** Rotates {@code v} about the unit {@code axis} by {@code angle} radians (Rodrigues). */
    static void rotate(float[] v, float[] axis, float angle, float[] out) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        float dot = v[0] * axis[0] + v[1] * axis[1] + v[2] * axis[2];
        float cx = axis[1] * v[2] - axis[2] * v[1];
        float cy = axis[2] * v[0] - axis[0] * v[2];
        float cz = axis[0] * v[1] - axis[1] * v[0];
        out[0] = v[0] * c + cx * s + axis[0] * dot * (1.0f - c);
        out[1] = v[1] * c + cy * s + axis[1] * dot * (1.0f - c);
        out[2] = v[2] * c + cz * s + axis[2] * dot * (1.0f - c);
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static void normalize(float[] v) {
        float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length > 0.0f) {
            v[0] /= length;
            v[1] /= length;
            v[2] /= length;
        }
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
