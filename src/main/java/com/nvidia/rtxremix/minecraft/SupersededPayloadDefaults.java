/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/**
 * Recognizes author-payload files that are still byte-for-byte a default an
 * earlier build shipped, so extraction can replace them with the current
 * default.
 *
 * <p>The {@code rtx-remix/} payload is author-owned: an existing file is never
 * overwritten, because it may hold an author's tuning. That also means a
 * shipped default that changes never reaches an install that already has the
 * old one. A file whose content hashes to a superseded default carries no
 * author edit, so replacing it loses nothing. An edited file never matches and
 * is kept as before.
 *
 * <p>The list lives in {@value #RESOURCE}, one {@code <sha256> <path>} entry per
 * line, the path relative to the game directory with forward slashes. Hashes
 * are taken over the content with CR bytes removed, so a checkout's line
 * endings do not matter. Every time a shipped payload file changes, the hash of
 * the version it replaces is appended there.
 */
final class SupersededPayloadDefaults {

    static final String RESOURCE = "rtx-remix-superseded-defaults.txt";

    private final Map<String, Set<String>> hashesByPath;

    SupersededPayloadDefaults(Map<String, Set<String>> hashesByPath) {
        this.hashesByPath = hashesByPath;
    }

    /** Loads the list bundled with the mod, or an empty one when it is absent. */
    static SupersededPayloadDefaults bundled() throws IOException {
        try (InputStream in = SupersededPayloadDefaults.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            return in == null ? new SupersededPayloadDefaults(Map.of()) : parse(in);
        }
    }

    static SupersededPayloadDefaults parse(InputStream in) throws IOException {
        Map<String, Set<String>> hashes = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int split = line.indexOf(' ');
                if (split <= 0) {
                    throw new IOException("Malformed superseded default entry: " + line);
                }
                String hash = line.substring(0, split).toLowerCase(java.util.Locale.ROOT);
                String path = line.substring(split + 1).strip();
                hashes.computeIfAbsent(path, p -> new HashSet<>()).add(hash);
            }
        }
        return new SupersededPayloadDefaults(hashes);
    }

    /**
     * Whether {@code file}, which sits at {@code relativePath} under the game
     * directory, is an untouched copy of a superseded default.
     */
    boolean isSuperseded(String relativePath, Path file) throws IOException {
        Set<String> hashes = hashesByPath.get(relativePath.replace('\\', '/'));
        if (hashes == null || !Files.isRegularFile(file)) return false;
        return hashes.contains(normalizedSha256(Files.readAllBytes(file)));
    }

    static String normalizedSha256(byte[] content) {
        byte[] normalized = new byte[content.length];
        int length = 0;
        for (byte b : content) {
            if (b != '\r') normalized[length++] = b;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(normalized, 0, length);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
        }
    }
}
