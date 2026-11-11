/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads every class the mod's mixins target, so Mixin applies each mixin the way the game
 * would. The config requires at least one match per injector, so an injection point the
 * targeted Minecraft version no longer has fails here rather than at game start. Loading
 * does not initialize the classes, so nothing touches OpenGL or the game's registries.
 *
 * <p>The targets are read from the mixin class files, which may be read as resources but
 * never loaded as classes.
 */
@Tag("fabric")
class MixinTargetsApplyTest {

    private static final String CONFIG = "rtx-remix-renderer.mixins.json";
    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";

    @Test
    void everyMixinTargetLoadsWithItsMixinsApplied() throws IOException, ClassNotFoundException {
        // Knot, the loader that applies mixins; the context loader may be the app loader.
        ClassLoader loader = MixinTargetsApplyTest.class.getClassLoader();
        JsonObject config;
        try (InputStream in = loader.getResourceAsStream(CONFIG)) {
            assertNotNull(in, CONFIG + " is not on the classpath");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                config = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        String mixinPackage = config.get("package").getAsString();
        List<String> mixins = new ArrayList<>();
        for (String side : new String[] {"mixins", "client"}) {
            JsonArray entries = config.getAsJsonArray(side);
            if (entries != null) {
                for (JsonElement entry : entries) {
                    mixins.add(mixinPackage + "." + entry.getAsString());
                }
            }
        }
        assertFalse(mixins.isEmpty(), "no mixins registered");

        Set<String> targets = new TreeSet<>();
        for (String mixin : mixins) {
            String resource = mixin.replace('.', '/') + ".class";
            try (InputStream in = loader.getResourceAsStream(resource)) {
                assertNotNull(in, "mixin class file missing: " + resource);
                targets.addAll(targetsOf(in.readAllBytes()));
            }
        }

        List<String> failures = new ArrayList<>();
        for (String target : targets) {
            try {
                Class.forName(target, false, loader);
            } catch (Throwable failure) {
                StringBuilder chain = new StringBuilder(target).append(": ").append(failure);
                for (Throwable cause = failure.getCause(); cause != null && cause != cause.getCause();
                        cause = cause.getCause()) {
                    chain.append("\n    <- ").append(cause);
                }
                failures.add(chain.toString());
            }
        }
        assertTrue(failures.isEmpty(), "mixins failed to apply:\n" + String.join("\n", failures));

        // Proves the classes above were transformed rather than loaded untouched.
        Class<?> worldRenderer = Class.forName("net.minecraft.client.render.WorldRenderer", false, loader);
        assertTrue(com.nvidia.rtxremix.minecraft.sky.WorldRendererStarAccess.class.isAssignableFrom(worldRenderer),
                "WorldRenderer was loaded without WorldRendererMixin");
    }

    private static Set<String> targetsOf(byte[] classFile) {
        Set<String> targets = new TreeSet<>();
        new ClassReader(classFile).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MIXIN_ANNOTATION.equals(descriptor)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String ignored, Object value) {
                                if ("value".equals(name) && value instanceof Type type) {
                                    targets.add(type.getClassName());
                                } else if ("targets".equals(name) && value instanceof String target) {
                                    targets.add(target.replace('/', '.'));
                                }
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE);
        return targets;
    }
}
