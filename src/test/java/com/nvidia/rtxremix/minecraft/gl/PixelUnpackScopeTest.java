/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.gl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PixelUnpackScopeTest {

    private static final int ROW_LENGTH = 0x0CF2;
    private static final int SKIP_ROWS = 0x0CF3;
    private static final int SKIP_PIXELS = 0x0CF4;
    private static final int ALIGNMENT = 0x0CF5;
    private static final int SKIP_IMAGES = 0x806D;
    private static final int IMAGE_HEIGHT = 0x806E;
    private static final int UNPACK_BUFFER_BINDING = 0x88EF;

    /** A GL that holds unpack state and records every write. */
    private static final class FakeGl implements PixelUnpackScope.Gl {
        final Map<Integer, Integer> state = new HashMap<>();
        final List<String> writes = new ArrayList<>();

        FakeGl() {
            state.put(ALIGNMENT, 4);
        }

        @Override
        public int getInteger(int name) {
            return state.getOrDefault(name, 0);
        }

        @Override
        public void pixelStore(int name, int value) {
            state.put(name, value);
            writes.add(Integer.toHexString(name) + "=" + value);
        }

        @Override
        public void bindPixelUnpackBuffer(int buffer) {
            state.put(UNPACK_BUFFER_BINDING, buffer);
            writes.add("pbo=" + buffer);
        }
    }

    @Test
    void zeroesWhatVanillaLeftForTheUploadAndRestoresIt() {
        FakeGl gl = new FakeGl();
        // What vanilla leaves after uploading a 64-pixel-wide skin from an offset.
        gl.state.put(ROW_LENGTH, 64);
        gl.state.put(SKIP_PIXELS, 3);
        gl.state.put(SKIP_ROWS, 5);
        PixelUnpackScope scope = new PixelUnpackScope(gl);

        try (PixelUnpackScope ignored = scope.open(1)) {
            assertEquals(0, gl.getInteger(ROW_LENGTH));
            assertEquals(0, gl.getInteger(SKIP_PIXELS));
            assertEquals(0, gl.getInteger(SKIP_ROWS));
            assertEquals(0, gl.getInteger(IMAGE_HEIGHT));
            assertEquals(0, gl.getInteger(SKIP_IMAGES));
            assertEquals(1, gl.getInteger(ALIGNMENT));
        }

        assertEquals(64, gl.getInteger(ROW_LENGTH));
        assertEquals(3, gl.getInteger(SKIP_PIXELS));
        assertEquals(5, gl.getInteger(SKIP_ROWS));
        assertEquals(4, gl.getInteger(ALIGNMENT));
    }

    @Test
    void writesNothingButTheAlignmentWhenTheStateIsAlreadyDefault() {
        FakeGl gl = new FakeGl();
        PixelUnpackScope scope = new PixelUnpackScope(gl);

        scope.open(1);
        scope.close();

        assertEquals(List.of("cf5=1", "cf5=4"), gl.writes);
    }

    @Test
    void unbindsAPixelUnpackBufferForTheUploadAndRebindsIt() {
        FakeGl gl = new FakeGl();
        gl.state.put(UNPACK_BUFFER_BINDING, 7);
        gl.state.put(IMAGE_HEIGHT, 16);
        gl.state.put(SKIP_IMAGES, 2);
        PixelUnpackScope scope = new PixelUnpackScope(gl);

        scope.open(4);
        assertEquals(0, gl.getInteger(UNPACK_BUFFER_BINDING), "the pointer must be read as client memory");
        assertEquals(0, gl.getInteger(IMAGE_HEIGHT));
        assertEquals(0, gl.getInteger(SKIP_IMAGES));
        scope.close();

        assertEquals(7, gl.getInteger(UNPACK_BUFFER_BINDING));
        assertEquals(16, gl.getInteger(IMAGE_HEIGHT));
        assertEquals(2, gl.getInteger(SKIP_IMAGES));
    }

    @Test
    void isReusableAndRefusesToNest() {
        FakeGl gl = new FakeGl();
        PixelUnpackScope scope = new PixelUnpackScope(gl);

        scope.open(1);
        assertThrows(IllegalStateException.class, () -> scope.open(1));
        scope.close();
        scope.close();

        gl.state.put(ROW_LENGTH, 32);
        try (PixelUnpackScope ignored = scope.open(2)) {
            assertEquals(0, gl.getInteger(ROW_LENGTH));
        }
        assertEquals(32, gl.getInteger(ROW_LENGTH));
        assertTrue(gl.writes.contains("cf5=2"));
    }
}
