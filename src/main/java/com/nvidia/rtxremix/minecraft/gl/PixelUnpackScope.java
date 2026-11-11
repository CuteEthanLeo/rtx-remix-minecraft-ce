/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

/**
 * Gives an upload from client memory the default pixel-unpack state and puts back what it found.
 *
 * <p>Vanilla's texture uploads set {@code GL_UNPACK_ROW_LENGTH}, {@code GL_UNPACK_SKIP_PIXELS}
 * and {@code GL_UNPACK_SKIP_ROWS} for the image they upload and never reset them: the skips to
 * the part uploaded, and the row length to the image's width whenever that part is narrower. An
 * upload that sets only the alignment inherits them: after part of a 64-pixel-wide image, a
 * 32-texel-wide upload is read with a 64-pixel row stride, so the driver reads past the end of
 * the buffer and faults on a threaded driver's worker thread. So every client-memory upload
 * opens this scope, which zeroes the row length, skips and image height, sets the alignment, and unbinds any
 * pixel-unpack buffer so the pointer is read as client memory.
 *
 * <p>One instance is reused: {@link #open} saves the state into fields, so an upload allocates
 * nothing. Scopes do not nest. Render thread only, with the GL context current.
 */
public final class PixelUnpackScope implements AutoCloseable {

    /** The GL calls the scope makes, so its order can be tested without a context. */
    public interface Gl {
        int getInteger(int name);

        void pixelStore(int name, int value);

        void bindPixelUnpackBuffer(int buffer);
    }

    /** The unpack parameters zeroed for the upload; their default value is 0. */
    static final int[] ZEROED = {
            GL11.GL_UNPACK_ROW_LENGTH,
            GL11.GL_UNPACK_SKIP_PIXELS,
            GL11.GL_UNPACK_SKIP_ROWS,
            GL12.GL_UNPACK_IMAGE_HEIGHT,
            GL12.GL_UNPACK_SKIP_IMAGES,
    };

    private static final Gl LWJGL = new Gl() {
        @Override
        public int getInteger(int name) {
            return GL11.glGetInteger(name);
        }

        @Override
        public void pixelStore(int name, int value) {
            GL11.glPixelStorei(name, value);
        }

        @Override
        public void bindPixelUnpackBuffer(int buffer) {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
        }
    };

    private final Gl gl;
    private final int[] saved = new int[ZEROED.length];
    private int savedAlignment;
    private int savedBuffer;
    private boolean open;

    /** A scope over the current LWJGL context. */
    public PixelUnpackScope() {
        this(LWJGL);
    }

    PixelUnpackScope(Gl gl) {
        this.gl = gl;
    }

    /**
     * Saves the unpack state, then sets the defaults with {@code alignment}.
     *
     * @param alignment the {@code GL_UNPACK_ALIGNMENT} of the rows about to be uploaded: 1, 2, 4 or 8
     * @return this scope, to close after the upload
     */
    public PixelUnpackScope open(int alignment) {
        if (open) {
            throw new IllegalStateException("Pixel unpack scopes do not nest");
        }
        savedBuffer = gl.getInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        savedAlignment = gl.getInteger(GL11.GL_UNPACK_ALIGNMENT);
        for (int index = 0; index < ZEROED.length; index++) {
            saved[index] = gl.getInteger(ZEROED[index]);
        }
        open = true;
        if (savedBuffer != 0) {
            gl.bindPixelUnpackBuffer(0);
        }
        for (int index = 0; index < ZEROED.length; index++) {
            if (saved[index] != 0) {
                gl.pixelStore(ZEROED[index], 0);
            }
        }
        if (savedAlignment != alignment) {
            gl.pixelStore(GL11.GL_UNPACK_ALIGNMENT, alignment);
        }
        return this;
    }

    /** Restores the state {@link #open} saved; does nothing if the scope is not open. */
    @Override
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        for (int index = 0; index < ZEROED.length; index++) {
            if (saved[index] != 0) {
                gl.pixelStore(ZEROED[index], saved[index]);
            }
        }
        gl.pixelStore(GL11.GL_UNPACK_ALIGNMENT, savedAlignment);
        if (savedBuffer != 0) {
            gl.bindPixelUnpackBuffer(savedBuffer);
        }
    }
}
