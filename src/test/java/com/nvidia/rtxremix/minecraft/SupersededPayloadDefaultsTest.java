/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SupersededPayloadDefaultsTest {

    private static final String OLD_DEFAULT = "rtx.autoExposure.evMaxValue = -1.0\n";
    private static final String REL_PATH = "rtx-remix/mods/minecraft-logic/confs/weather/rain.conf";

    @Test
    void anUntouchedOldDefaultIsSupersededWhateverItsLineEndings(@TempDir Path tmp) throws IOException {
        SupersededPayloadDefaults defaults = listing(OLD_DEFAULT, REL_PATH);
        Path lf = write(tmp.resolve("lf.conf"), OLD_DEFAULT);
        Path crlf = write(tmp.resolve("crlf.conf"), OLD_DEFAULT.replace("\n", "\r\n"));

        assertTrue(defaults.isSuperseded(REL_PATH, lf));
        assertTrue(defaults.isSuperseded(REL_PATH.replace('/', '\\'), crlf));
    }

    @Test
    void anEditedFileOrAnotherPathIsNeverSuperseded(@TempDir Path tmp) throws IOException {
        SupersededPayloadDefaults defaults = listing(OLD_DEFAULT, REL_PATH);
        Path edited = write(tmp.resolve("edited.conf"), OLD_DEFAULT + "rtx.bloom.steps = 4\n");
        Path same = write(tmp.resolve("same.conf"), OLD_DEFAULT);

        assertFalse(defaults.isSuperseded(REL_PATH, edited));
        assertFalse(defaults.isSuperseded("rtx-remix/mods/minecraft-logic/confs/time/night.conf", same));
    }

    @Test
    void aMalformedEntryIsRejected() {
        byte[] listing = "no-path-here\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class,
                () -> SupersededPayloadDefaults.parse(new ByteArrayInputStream(listing)));
    }

    @Test
    void extractTreeReplacesOnlyTheExistingFilesItIsAllowedTo(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("src");
        Path dst = tmp.resolve("dst");
        write(src.resolve("old.conf"), "new default\n");
        write(src.resolve("edited.conf"), "new default\n");
        write(dst.resolve("old.conf"), "old default\n");
        write(dst.resolve("edited.conf"), "author tuning\n");

        AtomicInteger kept = new AtomicInteger();
        NativeExtractor.extractTree(src, dst, new AtomicInteger(), new AtomicLong(), kept,
                target -> target.getFileName().toString().equals("old.conf"));

        assertEquals("new default\n", Files.readString(dst.resolve("old.conf")));
        assertEquals("author tuning\n", Files.readString(dst.resolve("edited.conf")));
        assertEquals(1, kept.get());
    }

    private static SupersededPayloadDefaults listing(String content, String path) throws IOException {
        String hash = SupersededPayloadDefaults.normalizedSha256(content.getBytes(StandardCharsets.UTF_8));
        String text = "# comment\n\n" + hash + " " + path + "\n";
        return SupersededPayloadDefaults.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content);
    }
}
