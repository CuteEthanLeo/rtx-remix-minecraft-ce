package com.nvidia.rtxremix.minecraft.util;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.ColorHelper;

/**
 * Reads a {@link NativeImage} pixel in its native little-endian RGBA (big-endian ABGR) order.
 * Minecraft 1.21.11 made {@code NativeImage.getColor} private and only exposes the ARGB view.
 */
public final class NativeImagePixels {
    private NativeImagePixels() {}

    /** Returns the pixel as ABGR (red in the lowest byte), matching the 1.21.1 {@code getColor}. */
    public static int abgr(NativeImage image, int x, int y) {
        return ColorHelper.toAbgr(image.getColorArgb(x, y));
    }
}
