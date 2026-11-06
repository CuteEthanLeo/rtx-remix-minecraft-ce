/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.clouds;

import com.nvidia.rtxremix.minecraft.community.LightningFlash;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.*;



/** Actual GPU and readback coverage, run explicitly with Gradle's cloudGpuTest task. */
@EnabledIfSystemProperty(named = "rtxremix.test.cloudGpu", matches = "true")
class CloudSkyCompositorGpuTest {
    private static long window;
    private CloudSkyCompositor compositor;
    private int atmosphere, lut;

    @BeforeAll
    static void openContext() {
        assertTrue(GLFW.glfwInit(), "GLFW initialization");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        window = GLFW.glfwCreateWindow(32, 32, "Cloud shader validation", 0, 0);
        assertNotEquals(0, window, "OpenGL 4.4 context");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("Cloud GPU tests: " + GL11.glGetString(GL11.GL_RENDERER));
        CloudNoise.shared().join();
    }

    @AfterAll
    static void closeContext() {
        GL.setCapabilities(null);
        GLFW.glfwMakeContextCurrent(0);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    @BeforeEach
    void prepare() {
        LightningFlash.reset();
        VolumetricClouds.deactivate();
        // Thin, fully covered deck, wholly within the near mesh cap.
        VolumetricClouds.publishFrame(new CloudField.Params(400, 1, -1, 0), 0);
        VolumetricClouds.publishOccupancy(0, 0, (x, z) -> true);
        atmosphere = constantTexture(16, 9, 0);
        lut = constantTexture(1024, 256, 1);
        compositor = new CloudSkyCompositor(new VolumetricClouds.Quality(512, 96, 4, 320, 8, 1), 16, 8);
    }

    @AfterEach
    void release() {
        if (compositor != null) compositor.destroy();
        GL11.glDeleteTextures(atmosphere);
        GL11.glDeleteTextures(lut);
        VolumetricClouds.deactivate();
        LightningFlash.reset();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void nearCloudsOccludeTheDiscButNotTheAlreadyShadowedDistantLight() {
        float[] mean = new float[3];
        ByteBuffer pixels = render(frame(2), mean);
        assertNotNull(pixels);
        assertEquals(compositor.outputBytes(), pixels.remaining());
        assertEquals(1, VolumetricClouds.sunTransmittance(), 0.001);
        assertTrue(VolumetricClouds.sunDiscTransmittance() < 0.8f,
                "visible sun must be hidden by the near deck: " + VolumetricClouds.sunDiscTransmittance());
        assertTrue(mean[0] > 0, "the cloud shader produced illuminated clouds");
    }

    @Test
    void invalidationDiscardsAnAlreadyQueuedOldSceneReadback() throws Exception {
        float[] mean = new float[3];
        render(frame(2), mean);
        assertTrue(mean[0] > 0);
        // Queue a completed old image without consuming it, independent of GPU speed.
        Method enqueue = CloudSkyCompositor.class.getDeclaredMethod("enqueueReadback");
        enqueue.setAccessible(true);
        enqueue.invoke(compositor);
        GL11.glFinish();
        compositor.invalidate();
        render(frame(0), mean);
        assertArrayEquals(new float[]{0, 0, 0}, mean, 1.0e-6f,
                "a dark new scene must not receive the old illuminated dome or its mean");
    }

    @Test
    void lightningRecomposesWithoutRemarchingOrLeavingAFlashInHistory() throws Exception {
        float[] mean = new float[3];
        render(frame(0), mean);
        assertEquals(0, mean[2], 1.0e-6f);
        int marches = marchCount();
        LightningFlash.advance(2, 1.0f / 60);
        LightningFlash.observeStrike(0, 0, 0, 0);
        render(frame(0), mean);
        assertTrue(mean[2] > 0.001f, "the visible cloud deck must receive the lightning flash");
        assertTrue(mean[2] > mean[0], "the cloud flash has the bolt's cold tint");
        assertEquals(marches, marchCount(), "lightning must reuse the cloud march");
        LightningFlash.advance(0, 1);
        render(frame(0), mean);
        assertArrayEquals(new float[]{0, 0, 0}, mean, 1.0e-6f);
        assertEquals(marches, marchCount());
    }

    private ByteBuffer render(CloudSkyCompositor.Inputs frame, float[] mean) {
        ByteBuffer first = compositor.update(atmosphere, lut, frame, mean);
        GL11.glFinish();
        ByteBuffer completed = compositor.update(atmosphere, lut, frame, mean);
        return completed != null ? completed : first;
    }

    private int marchCount() throws Exception {
        Field field = CloudSkyCompositor.class.getDeclaredField("updateIndex");
        field.setAccessible(true);
        return field.getInt(compositor);
    }

    private static CloudSkyCompositor.Inputs frame(float sun) {
        return new CloudSkyCompositor.Inputs(new float[]{0, 1, 0}, new float[]{0, -1, 0},
                new float[]{sun, sun, sun}, new float[]{0, 0, 0}, 0.0046f, 0.0046f,
                0, 64, 0, 6000, true, 1);
    }

    private static int constantTexture(int width, int height, float value) {
        FloatBuffer data = BufferUtils.createFloatBuffer(width * height * 4);
        while (data.hasRemaining()) data.put(value);
        data.flip();
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, width, height, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, data);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }
}
