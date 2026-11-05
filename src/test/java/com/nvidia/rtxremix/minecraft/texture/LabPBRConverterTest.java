/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Modified by RTX Remix for Minecraft Community Edition; see COMMUNITY.md.
 */
package com.nvidia.rtxremix.minecraft.texture;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabPBRConverterTest {

    @Test
    void applyAoToPatchSamplesTheAoTableAtTheUploadSourceOffset() {
        ByteBuffer src = ByteBuffer.allocate(8);
        putPixel(src, 0, 100, 150, 200, 250);
        putPixel(src, 1, 64, 128, 255, 255);

        byte[] dst = new byte[8];
        byte[] aoTable = bytes(255, 128, 64, 0);

        LabPBRConverter.applyAOToPatch(dst, 8, src, 8, aoTable, 4, 1, 2, 0, 2, 1);

        assertEquals(25, unsigned(dst[0]));
        assertEquals(37, unsigned(dst[1]));
        assertEquals(50, unsigned(dst[2]));
        assertEquals(250, unsigned(dst[3]));
        assertEquals(0, unsigned(dst[4]));
        assertEquals(0, unsigned(dst[5]));
        assertEquals(0, unsigned(dst[6]));
        assertEquals(255, unsigned(dst[7]));
    }

    @Test
    void buildEmissivePatchSamplesTheAlphaTableAtTheUploadSourceOffset() {
        ByteBuffer src = ByteBuffer.allocate(8);
        putPixel(src, 0, 100, 100, 100, 255);
        putPixel(src, 1, 200, 50, 20, 255);

        byte[] dst = new byte[8];
        byte[] sAlphaTable = bytes(255, 0, 127, 255);

        boolean anyEmission = LabPBRConverter.buildEmissivePatch(
            dst, 8, src, 8, sAlphaTable, 4, 1, 1, 0, 2, 1, 1.0f);

        assertTrue(anyEmission);
        assertEquals(0, unsigned(dst[0]));
        assertEquals(0, unsigned(dst[1]));
        assertEquals(0, unsigned(dst[2]));
        assertEquals(0, unsigned(dst[3]));
        assertEquals(100, unsigned(dst[4]));
        assertEquals(25, unsigned(dst[5]));
        assertEquals(10, unsigned(dst[6]));
        assertEquals(255, unsigned(dst[7]));
    }

    @Test
    void buildEmissiveMaskPatchScalesFrameAlbedoByTheMaskAndMarksEmittingTexels() {
        // A 2x1 frame whose mask lights only the right half at 0.5: the left texel must
        // come out fully dark with alpha 0, the right one at half its albedo with alpha 255.
        ByteBuffer src = ByteBuffer.allocate(8);
        putPixel(src, 0, 100, 100, 100, 255);
        putPixel(src, 1, 200, 50, 20, 255);
        byte[] dst = new byte[8];

        LabPBRConverter.buildEmissiveMaskPatch(dst, 8, src, 8, 2, 1, (u, v) -> u > 0.5f ? 0.5f : 0f);

        assertEquals(0, unsigned(dst[0]));
        assertEquals(0, unsigned(dst[3]));
        assertEquals(100, unsigned(dst[4]));
        assertEquals(25, unsigned(dst[5]));
        assertEquals(10, unsigned(dst[6]));
        assertEquals(255, unsigned(dst[7]));
    }

    @Test
    void applyEmissionGainScalesAuthoredEmissionAndLeavesUnauthoredTexelsAlone() {
        // 255 means "no emission" and must survive any gain; everything else scales and
        // caps at 254, the alpha that reads back as factor 1.
        byte[] table = {(byte) 255, (byte) 100, (byte) 200, (byte) 0};
        LabPBRConverter.applyEmissionGain(table, 2.5f);
        assertEquals(255, table[0] & 0xFF, "unauthored stays unauthored");
        assertEquals(250, table[1] & 0xFF, "100 * 2.5");
        assertEquals(254, table[2] & 0xFF, "capped at full emission, not wrapped");
        assertEquals(0, table[3] & 0xFF, "zero stays zero");

        byte[] untouched = {(byte) 255, (byte) 100};
        LabPBRConverter.applyEmissionGain(untouched, 1.0f);
        assertEquals(100, untouched[1] & 0xFF, "gain 1 is a no-op");
    }

    @Test
    void perceptualEncodingWritesTheSmoothnessComplementThatRemixSquaresOnce() {
        // Remix squares a sampled roughness itself, so the faithful texel is 1 - s.
        LabPBRConverter.RoughnessEncoding perceptual = LabPBRConverter.RoughnessEncoding.PERCEPTUAL;
        assertEquals(255, LabPBRConverter.smoothnessToRoughness(0, perceptual));
        assertEquals(128, LabPBRConverter.smoothnessToRoughness(127, perceptual));
        assertEquals(0, LabPBRConverter.smoothnessToRoughness(255, perceptual));
    }

    @Test
    void squaredEncodingKeepsTheUpstreamValues() {
        LabPBRConverter.RoughnessEncoding squared = LabPBRConverter.RoughnessEncoding.SQUARED;
        assertEquals(255, LabPBRConverter.smoothnessToRoughness(0, squared));
        assertEquals(64, LabPBRConverter.smoothnessToRoughness(127, squared));
        assertEquals(0, LabPBRConverter.smoothnessToRoughness(255, squared));
        assertTrue(LabPBRConverter.smoothnessToRoughness(176, squared)
                < LabPBRConverter.smoothnessToRoughness(176, LabPBRConverter.RoughnessEncoding.PERCEPTUAL),
                "the squared encoding is glossier for every partly smooth texel");
    }

    private static void putPixel(ByteBuffer buffer, int pixelIndex, int r, int g, int b, int a) {
        int offset = pixelIndex * 4;
        buffer.put(offset, (byte) r);
        buffer.put(offset + 1, (byte) g);
        buffer.put(offset + 2, (byte) b);
        buffer.put(offset + 3, (byte) a);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    @Test
    void dilationGivesHolesTheirNeighboursColourAndKeepsThemTransparent() {
        // 4x1 atlas, sprite covers texels 0..2; texel 3 lies outside it.
        byte[] atlas = bytes(
                90, 160, 30, 255,
                0, 0, 0, 0,
                110, 180, 50, 255,
                0, 0, 0, 0);

        LabPBRConverter.dilateTransparentRgb(atlas, 4, 0, 0, 3, 1);

        assertEquals(100, unsigned(atlas[4]));
        assertEquals(170, unsigned(atlas[5]));
        assertEquals(40, unsigned(atlas[6]));
        assertEquals(0, unsigned(atlas[7]));
        // Opaque texels are untouched.
        assertEquals(90, unsigned(atlas[0]));
        assertEquals(110, unsigned(atlas[8]));
        // Nothing outside the sprite is written.
        assertEquals(0, unsigned(atlas[12]));
        assertEquals(0, unsigned(atlas[13]));
    }

    @Test
    void dilationReachesHolesFarFromAnyOpaqueTexel() {
        // 5x1 sprite with one opaque texel at the left end.
        byte[] atlas = new byte[5 * 4];
        atlas[0] = (byte) 200;
        atlas[1] = (byte) 120;
        atlas[2] = (byte) 40;
        atlas[3] = (byte) 255;

        LabPBRConverter.dilateTransparentRgb(atlas, 5, 0, 0, 5, 1);

        for (int i = 1; i < 5; i++) {
            assertEquals(200, unsigned(atlas[i * 4]));
            assertEquals(120, unsigned(atlas[i * 4 + 1]));
            assertEquals(40, unsigned(atlas[i * 4 + 2]));
            assertEquals(0, unsigned(atlas[i * 4 + 3]));
        }
    }

    @Test
    void dilationFillsALargeSpriteFromOneCorner() {
        int size = 64;
        byte[] atlas = new byte[size * size * 4];
        atlas[0] = (byte) 30;
        atlas[1] = (byte) 140;
        atlas[2] = (byte) 70;
        atlas[3] = (byte) 255;

        LabPBRConverter.dilateTransparentRgb(atlas, size, 0, 0, size, size);

        for (int i = 1; i < size * size; i++) {
            assertEquals(30, unsigned(atlas[i * 4]));
            assertEquals(140, unsigned(atlas[i * 4 + 1]));
            assertEquals(70, unsigned(atlas[i * 4 + 2]));
            assertEquals(0, unsigned(atlas[i * 4 + 3]));
        }
    }

    @Test
    void dilationLeavesFullyTransparentSpritesBlack() {
        byte[] atlas = new byte[2 * 2 * 4];

        LabPBRConverter.dilateTransparentRgb(atlas, 2, 0, 0, 2, 2);

        for (byte b : atlas) {
            assertEquals(0, unsigned(b));
        }
    }

    @Test
    void dilationStaysInsideItsSpriteRectangle() {
        // 2x2 atlas: the sprite is the right column, its neighbour the opaque left column.
        byte[] atlas = bytes(
                255, 255, 255, 255,   0, 0, 0, 0,
                255, 255, 255, 255,   0, 0, 0, 0);

        LabPBRConverter.dilateTransparentRgb(atlas, 2, 1, 0, 1, 2);

        // The sprite has no opaque texel of its own, so the neighbour's white must not bleed in.
        assertEquals(0, unsigned(atlas[4]));
        assertEquals(0, unsigned(atlas[12]));
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    @Test
    void ambientOcclusionDarkensTheDecodedAlbedoNotTheEncodedBytes() {
        // Half the light of white is sRGB byte 188, not 128.
        assertEquals(188, LabPBRConverter.occludeSrgb(255, 0.5f));
        assertEquals(255, LabPBRConverter.occludeSrgb(255, 1.0f));
        assertEquals(0, LabPBRConverter.occludeSrgb(200, 0.0f));
    }

    @Test
    void foliageSingleScatteringAlbedoLosesEnergyPerBounce() {
        // The baseline leaves' green 240 (0.94) would bounce green almost without loss.
        assertEquals(168, LabPBRConverter.scaleSingleScatteringAlbedo(240));
        assertEquals(0, LabPBRConverter.scaleSingleScatteringAlbedo(0));
    }
}
