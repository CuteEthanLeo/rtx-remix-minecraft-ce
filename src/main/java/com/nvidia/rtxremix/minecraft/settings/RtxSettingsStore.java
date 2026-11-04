/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Modified by RTX Remix for Minecraft Community Edition; see COMMUNITY.md.
 */
package com.nvidia.rtxremix.minecraft.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads/writes non-default setting overrides as {version, settings:{id:value}}. */
public final class RtxSettingsStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RtxSettingsStore.class);
    // A file whose version does not match is rejected, never converted: the
    // meaning of a stored value can change with the version, and a mismatched
    // file read as if it matched is a silent wrong setting. Defaults re-apply
    // on the next launch.
    private static final int VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public RtxSettingsStore(Path file) {
        this.file = file;
    }

    public void save(RtxSettings settings) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject values = new JsonObject();
        for (RtxSetting<?> s : settings.all()) {
            if (s.isDefault()) {
                continue;
            }
            switch (s.kind()) {
                case BOOL -> values.addProperty(s.id(), (Boolean) s.get());
                case INT -> values.addProperty(s.id(), (Integer) s.get());
                case FLOAT -> values.addProperty(s.id(), (Double) s.get());
            }
        }
        recordVolumetricChoice(settings, values);
        root.add("settings", values);
        try {
            if (values.size() == 0) {
                Files.deleteIfExists(file);
                return;
            }
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to save RTX settings to {}", file, e);
        }
    }

    public void load(RtxSettings settings) {
        if (!Files.exists(file)) {
            return;
        }
        JsonObject values;
        try (Reader r = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 0;
            if (version != VERSION) {
                LOGGER.warn("RTX settings file {} is version {}, expected {}; ignoring it and "
                        + "using defaults, because a file from another version cannot be read "
                        + "safely.", file, version, VERSION);
                return;
            }
            if (!root.has("settings")) {
                return;
            }
            values = root.getAsJsonObject("settings");
        } catch (Exception e) {
            LOGGER.warn("Failed to read RTX settings from {}, ignoring", file, e);
            return;
        }
        keepChosenCloudStyle(values);
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            RtxSetting<?> setting = settings.byId(entry.getKey());
            if (setting == null) {
                LOGGER.warn("Unknown RTX setting id '{}' in config, skipping", entry.getKey());
                continue;
            }
            applyEntry(setting, entry.getValue());
        }
    }

    /**
     * Community Edition: the volumetric clouds are on by default and replace the chosen style.
     * Only changed settings are saved, so a saved Procedural style is a player's own choice;
     * a file from before the volumetric clouds that holds one keeps it by saving them off.
     */
    static void keepChosenCloudStyle(JsonObject values) {
        if (values.has(VOLUMETRIC_CLOUDS_ID) || !values.has(PROCEDURAL_CLOUDS_ID)) {
            return;
        }
        try {
            if (values.get(PROCEDURAL_CLOUDS_ID).getAsBoolean()) {
                values.addProperty(VOLUMETRIC_CLOUDS_ID, false);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Ignoring a malformed saved cloud style", e);
        }
    }

    /**
     * Writes the volumetric clouds' switch even at its default whenever a Procedural style is
     * saved beside it. Otherwise "on" is never saved, and {@link #keepChosenCloudStyle} would
     * switch the clouds off again on every launch.
     */
    static void recordVolumetricChoice(RtxSettings settings, JsonObject values) {
        if (!values.has(PROCEDURAL_CLOUDS_ID) || values.has(VOLUMETRIC_CLOUDS_ID)) {
            return;
        }
        RtxSetting<?> volumetric = settings.byId(VOLUMETRIC_CLOUDS_ID);
        if (volumetric != null && volumetric.get() instanceof Boolean value) {
            values.addProperty(VOLUMETRIC_CLOUDS_ID, value);
        }
    }

    private static final String VOLUMETRIC_CLOUDS_ID = "clouds.volumetric";
    private static final String PROCEDURAL_CLOUDS_ID = "clouds.procedural";

    @SuppressWarnings("unchecked")
    private void applyEntry(RtxSetting<?> setting, JsonElement value) {
        try {
            switch (setting.kind()) {
                case BOOL -> {
                    boolean v = value.getAsBoolean();
                    RtxSetting<Boolean> typed = (RtxSetting<Boolean>) setting;
                    if (!typed.isDefaultValue(v)) {
                        typed.apply(v);
                    }
                }
                case INT -> {
                    int v = value.getAsInt();
                    if (!setting.inRange(v)) {
                        warnRange(setting, v);
                        return;
                    }
                    RtxSetting<Integer> typed = (RtxSetting<Integer>) setting;
                    if (!typed.isDefaultValue(v)) {
                        typed.apply(v);
                    }
                }
                case FLOAT -> {
                    double v = value.getAsDouble();
                    if (!setting.inRange(v)) {
                        warnRange(setting, v);
                        return;
                    }
                    RtxSetting<Double> typed = (RtxSetting<Double>) setting;
                    if (!typed.isDefaultValue(v)) {
                        typed.apply(v);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to apply saved RTX setting '{}', skipping", setting.id(), e);
        }
    }

    private void warnRange(RtxSetting<?> setting, double v) {
        LOGGER.warn("Saved RTX setting '{}' value {} out of range [{}, {}], skipping",
                setting.id(), v, setting.min(), setting.max());
    }
}
