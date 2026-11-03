/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

/**
 * Water optics per biome.
 *
 * <p>Upstream gives every water surface one transmittance, measured at one
 * metre, so light dies within a few blocks: shallow water looks like ink and a
 * lake under an overcast sky goes black. The realistic look measures over a
 * longer distance, so shallows show their bed and depth turns blue-green, and it
 * takes each biome's vanilla water colour into account: warm oceans clear and
 * turquoise, swamps murky and short-sighted.
 *
 * <p>Capture sorts each water quad into one of a fixed palette by its vanilla
 * biome colour, so every palette entry is a material created up front on the
 * render thread. The palette's bucket keys are {@code water} for the default
 * entry and {@code water<N>} for the others.
 */
public final class WaterLook {

    /** Transmittance colour and measurement distance of one palette entry. */
    public record Optics(float r, float g, float b, float distance) {}

    private record Entry(int biomeRgb, Optics optics) {}

    private static final String BUCKET_PREFIX = "water";

    /** Index 0 is the default water colour, shared by rivers, plains, and most other biomes. */
    private static final Entry[] PALETTE = {
            new Entry(0x3F76E4, new Optics(0.55f, 0.86f, 0.90f, 3.5f)),
            new Entry(0x43D5EE, new Optics(0.62f, 0.94f, 0.93f, 6.0f)),   // warm ocean
            new Entry(0x45ADF2, new Optics(0.58f, 0.90f, 0.93f, 4.5f)),   // lukewarm ocean
            new Entry(0x3D57D6, new Optics(0.50f, 0.80f, 0.88f, 3.0f)),   // cold ocean
            new Entry(0x3938C9, new Optics(0.47f, 0.73f, 0.86f, 3.0f)),   // frozen ocean and rivers
            new Entry(0x617B64, new Optics(0.62f, 0.66f, 0.46f, 1.4f)),   // swamp
            new Entry(0x3A7A6A, new Optics(0.55f, 0.72f, 0.60f, 1.8f)),   // mangrove swamp
            new Entry(0x5DB7EF, new Optics(0.60f, 0.89f, 0.95f, 4.0f)),   // cherry grove
            new Entry(0x0E4ECF, new Optics(0.45f, 0.72f, 0.92f, 3.0f)),   // meadow
    };

    public static final int PALETTE_SIZE = PALETTE.length;

    /** Upstream's single water optics, the look the switch restores. */
    public static final Optics UPSTREAM = new Optics(0.6f, 0.9f, 0.89f, 1.0f);

    private static volatile boolean realisticWater = true;

    private WaterLook() {}

    public static boolean isRealisticWater() { return realisticWater; }
    public static void setRealisticWater(boolean enabled) { realisticWater = enabled; }

    /** The palette entry nearest to a vanilla water colour, by RGB distance. */
    public static int paletteIndex(int rgb) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            int distance = rgbDistanceSquared(rgb, PALETTE[i].biomeRgb());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    public static String bucketKey(int index) {
        return index == 0 ? BUCKET_PREFIX : BUCKET_PREFIX + index;
    }

    public static boolean isWaterBucket(String bucketKey) {
        return bucketKey.startsWith(BUCKET_PREFIX) && indexOfBucketKey(bucketKey) >= 0;
    }

    /** The palette index a water bucket key names, or -1 when it names none. */
    public static int indexOfBucketKey(String bucketKey) {
        if (!bucketKey.startsWith(BUCKET_PREFIX)) return -1;
        String suffix = bucketKey.substring(BUCKET_PREFIX.length());
        if (suffix.isEmpty()) return 0;
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) return -1;
        }
        int index = Integer.parseInt(suffix);
        return index > 0 && index < PALETTE.length ? index : -1;
    }

    /**
     * The optics for a palette entry. {@code base} carries the player's
     * session overrides from {@code /rtx water}; with the realistic look on,
     * each entry is scaled by how far {@code base} has moved from upstream, so
     * the command still steers every biome together.
     */
    public static Optics optics(int index, Optics base) {
        if (!realisticWater) {
            return base;
        }
        Optics entry = PALETTE[Math.max(0, Math.min(PALETTE.length - 1, index))].optics();
        return new Optics(
                clamp01(entry.r() * base.r() / UPSTREAM.r()),
                clamp01(entry.g() * base.g() / UPSTREAM.g()),
                clamp01(entry.b() * base.b() / UPSTREAM.b()),
                entry.distance() * base.distance() / UPSTREAM.distance());
    }

    private static int rgbDistanceSquared(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
