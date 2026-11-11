/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.weather;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nvidia.rtxremix.minecraft.RtxRemixCommonMod;
import com.nvidia.rtxremix.minecraft.gl.ExternalGlTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
/**
 * Draws rain and snow with GL directly onto the composited Remix colour image,
 * depth-testing against the interop depth texture so weather is occluded by
 * path-traced geometry.
 *
 * <p>Render thread only, and after Remix has presented its image into GL: the
 * renderer owns its GL buffers and reads the interop textures the frame just
 * produced.
 */
public final class RemixWeatherRenderer {
    private static final float NEAR_PLANE = 0.05f;
    private static final float FAR_PLANE = 1000.0f;
    private static final Identifier RAIN_TEXTURE =
            Identifier.ofVanilla("textures/environment/rain.png");
    /** Vanilla's weather layer blend: colour over, destination alpha kept. */
    private static final BlendFunction WEATHER_BLEND = new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ZERO);
    /**
     * The rain refraction pipeline. A pipeline compiles on first use from the shader sources
     * the shader loader read under {@code shaders/} on resource reload, so building it is all
     * the registration there is.
     */
    private static final RenderPipeline RAIN_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_SNIPPET)
            .withLocation(Identifier.of(RtxRemixCommonMod.MOD_ID, "pipeline/rain_refraction"))
            .withVertexShader(Identifier.of(RtxRemixCommonMod.MOD_ID, "core/rain_refraction"))
            .withFragmentShader(Identifier.of(RtxRemixCommonMod.MOD_ID, "core/rain_refraction"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
            .withBlend(WEATHER_BLEND)
            .withCull(false)
            .withDepthWrite(false)
            .build();
    /** Vanilla's particle program, which snow draws with, under the same fixed state as rain. */
    private static final RenderPipeline SNOW_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Identifier.of(RtxRemixCommonMod.MOD_ID, "pipeline/snow"))
            .withBlend(WEATHER_BLEND)
            .withCull(false)
            .withDepthWrite(false)
            .build();
    private final List<DeferredDraw> draws = new ArrayList<>(2);
    private Matrix4f projection;
    private Matrix4f modelView;
    private LightmapTextureManager lightmap;
    private DepthSeeder depthSeeder;
    private ExternalGlTexture finalColor;
    private int capturedQuadCount;
    private int lastDrawCount;
    private int lastQuadCount;

    public void beginCaptureFrame(LightmapTextureManager lightmapTextureManager) {
        closePendingDraws();
        lightmap = lightmapTextureManager;
        capturedQuadCount = 0;
        lastDrawCount = 0;
        lastQuadCount = 0;
    }

    public void updateCamera(Camera camera, float fovYDegrees, float aspect) {
        Quaternionf cameraRotation = camera.getRotation().conjugate(new Quaternionf());
        modelView = new Matrix4f().rotation(cameraRotation);
        projection = new Matrix4f().perspective(
                (float) Math.toRadians(fovYDegrees),
                aspect,
                NEAR_PLANE,
                FAR_PLANE);
    }

    public void capture(BuiltBuffer buffer, Identifier texture) {
        if (texture == null) {
            buffer.close();
            throw new IllegalStateException("Weather draw did not bind a texture");
        }
        capturedQuadCount += buffer.getDrawParameters().vertexCount() / 4;
        draws.add(new DeferredDraw(buffer, texture, texture.equals(RAIN_TEXTURE)));
    }

    public void render(int finalColorTexture, int depthTexture) {
        if (draws.isEmpty()) {
            return;
        }
        if (depthTexture == 0) {
            closePendingDraws();
            throw new IllegalStateException("Remix depth texture is unavailable");
        }
        if (finalColorTexture == 0) {
            closePendingDraws();
            throw new IllegalStateException("Remix final-color texture is unavailable");
        }
        if (projection == null || modelView == null || lightmap == null) {
            closePendingDraws();
            throw new IllegalStateException("Weather camera state was not initialized");
        }

        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        try {
            if (depthSeeder == null) {
                depthSeeder = new DepthSeeder();
            }
            depthSeeder.seed(depthTexture, framebuffer);
            lastDrawCount = draws.size();
            lastQuadCount = capturedQuadCount;
            renderDeferredDraws(framebuffer, finalColorTexture);
        } catch (RuntimeException exception) {
            closePendingDraws();
            throw exception;
        }
    }

    private void renderDeferredDraws(Framebuffer framebuffer, int finalColorTexture) {
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        ProjectionType savedProjectionType = RenderSystem.getProjectionType();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        float[] shaderColor = RenderSystem.getShaderColor().clone();

        RenderSystem.setProjectionMatrix(projection, ProjectionType.PERSPECTIVE);
        modelViewStack.pushMatrix();
        modelViewStack.set(modelView);

        try {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            renderRain(framebuffer, finalColorTexture);
            renderSnow(framebuffer);
        } finally {
            closePendingDraws();
            modelViewStack.popMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
            RenderSystem.setShaderColor(
                    shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        }
    }

    private void renderRain(Framebuffer framebuffer, int finalColorTexture) {
        if (!hasRainDraw()) {
            return;
        }
        renderDraws(framebuffer, true, RAIN_PIPELINE, wrapFinalColor(finalColorTexture));
    }

    private boolean hasRainDraw() {
        for (DeferredDraw draw : draws) {
            if (draw != null && draw.rain()) {
                return true;
            }
        }
        return false;
    }

    private void renderSnow(Framebuffer framebuffer) {
        renderDraws(framebuffer, false, SNOW_PIPELINE, null);
    }

    private GpuTexture wrapFinalColor(int finalColorTexture) {
        int width = GL45.glGetTextureLevelParameteri(finalColorTexture, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL45.glGetTextureLevelParameteri(finalColorTexture, 0, GL11.GL_TEXTURE_HEIGHT);
        if (finalColor == null || !finalColor.wraps(finalColorTexture, width, height)) {
            finalColor = new ExternalGlTexture("Remix final color", finalColorTexture, width, height);
        }
        return finalColor;
    }

    /**
     * Draws the pending rain or snow buffers in one pass over the main framebuffer. The pass
     * applies the pipeline's blend, depth and cull state itself, and every later vanilla pass
     * applies its own, so nothing is saved or restored here.
     */
    private void renderDraws(
            Framebuffer framebuffer, boolean rain, RenderPipeline pipeline, GpuTexture sceneColor) {
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        for (int i = 0; i < draws.size(); i++) {
            DeferredDraw draw = draws.get(i);
            if (draw == null || draw.rain() != rain) {
                continue;
            }
            try (BuiltBuffer buffer = draw.buffer()) {
                draws.set(i, null);
                BuiltBuffer.DrawParameters parameters = buffer.getDrawParameters();
                GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(buffer.getBuffer());
                RenderSystem.ShapeIndexBuffer sequentialIndices = RenderSystem.getSequentialBuffer(parameters.mode());
                GpuBuffer indices = sequentialIndices.getIndexBuffer(parameters.indexCount());
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        framebuffer.getColorAttachment(),
                        OptionalInt.empty(),
                        framebuffer.getDepthAttachment(),
                        OptionalDouble.empty())) {
                    pass.setPipeline(pipeline);
                    pass.setVertexBuffer(0, vertices);
                    pass.bindSampler("Sampler0", textureManager.getTexture(draw.texture()).getGlTexture());
                    if (sceneColor != null) {
                        pass.bindSampler("Sampler1", sceneColor);
                    } else {
                        pass.bindSampler("Sampler2", lightmap.getGlTexture());
                    }
                    pass.setIndexBuffer(indices, sequentialIndices.getIndexType());
                    pass.drawIndexed(0, parameters.indexCount());
                }
            }
        }
    }
    private static int[] readScissorBox() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, values);
            return new int[]{values.get(0), values.get(1), values.get(2), values.get(3)};
        }
    }

    private static boolean[] readColorMask() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer values = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, values);
            return new boolean[]{
                    values.get(0) != 0,
                    values.get(1) != 0,
                    values.get(2) != 0,
                    values.get(3) != 0
            };
        }
    }

    private void closePendingDraws() {
        for (DeferredDraw draw : draws) {
            if (draw != null) {
                draw.buffer().close();
            }
        }
        draws.clear();
    }

    public int getLastDrawCount() {
        return lastDrawCount;
    }

    public int getLastQuadCount() {
        return lastQuadCount;
    }

    public void cleanup() {
        closePendingDraws();
        if (depthSeeder != null) {
            depthSeeder.destroy();
            depthSeeder = null;
        }
    }

    private record DeferredDraw(BuiltBuffer buffer, Identifier texture, boolean rain) {
    }

    private static final class DepthSeeder {
        private static final String VERTEX_SHADER = """
                #version 150
                void main() {
                    vec2 position = vec2(
                        float((gl_VertexID << 1) & 2),
                        float(gl_VertexID & 2));
                    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
                }
                """;

        private static final String FRAGMENT_SHADER = """
                #version 150
                uniform sampler2D RemixDepth;
                uniform vec2 FramebufferSize;
                void main() {
                    vec2 uv = vec2(
                        gl_FragCoord.x / FramebufferSize.x,
                        1.0 - gl_FragCoord.y / FramebufferSize.y);
                    gl_FragDepth = texture(RemixDepth, uv).r;
                }
                """;

        private final int program;
        private final int vertexArray;
        private final int remixDepthLocation;
        private final int framebufferSizeLocation;

        private DepthSeeder() {
            int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = 0;
            int linkedProgram = 0;
            try {
                fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
                linkedProgram = GL20.glCreateProgram();
                GL20.glAttachShader(linkedProgram, vertexShader);
                GL20.glAttachShader(linkedProgram, fragmentShader);
                GL20.glLinkProgram(linkedProgram);
                if (GL20.glGetProgrami(linkedProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                    String log = GL20.glGetProgramInfoLog(linkedProgram);
                    GL20.glDeleteProgram(linkedProgram);
                    linkedProgram = 0;
                    throw new IllegalStateException("Failed to link depth seeding shader: " + log);
                }
            } finally {
                GL20.glDeleteShader(vertexShader);
                if (fragmentShader != 0) {
                    GL20.glDeleteShader(fragmentShader);
                }
            }
            program = linkedProgram;
            remixDepthLocation = GL20.glGetUniformLocation(program, "RemixDepth");
            framebufferSizeLocation = GL20.glGetUniformLocation(program, "FramebufferSize");
            vertexArray = GL30.glGenVertexArrays();
        }

        /**
         * Copies the traced depth into the main framebuffer's depth attachment. The
         * framebuffer is bound through the state manager so its binding cache stays true; the
         * render passes that follow bind their own targets.
         */
        private void seed(int depthTexture, Framebuffer framebuffer) {
            int width = framebuffer.textureWidth;
            int height = framebuffer.textureHeight;
            int target = ((GlTexture) framebuffer.getColorAttachment()).getOrCreateFramebuffer(
                    ((GlBackend) RenderSystem.getDevice()).getFramebufferManager(),
                    framebuffer.getDepthAttachment());
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, target);
            GlStateManager._viewport(0, 0, width, height);            int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            int previousDepthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            boolean previousScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            int[] previousScissorBox = readScissorBox();
            boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            boolean[] previousColorMask = readColorMask();

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                // OpenGL suppresses depth writes entirely when the depth test is disabled.
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_ALWAYS);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDepthMask(true);
                GL11.glColorMask(false, false, false, false);
                GL20.glUseProgram(program);
                GL20.glUniform1i(remixDepthLocation, 0);
                GL20.glUniform2f(framebufferSizeLocation, width, height);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
                GL30.glBindVertexArray(vertexArray);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            } finally {
                GL30.glBindVertexArray(previousVertexArray);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                GL13.glActiveTexture(previousActiveTexture);
                GL20.glUseProgram(previousProgram);
                GL11.glColorMask(
                        previousColorMask[0],
                        previousColorMask[1],
                        previousColorMask[2],
                        previousColorMask[3]);
                GL11.glDepthMask(previousDepthMask);
                GL11.glDepthFunc(previousDepthFunction);
                if (previousDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
                else GL11.glDisable(GL11.GL_DEPTH_TEST);
                if (previousScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(
                        previousScissorBox[0],
                        previousScissorBox[1],
                        previousScissorBox[2],
                        previousScissorBox[3]);
                if (previousBlend) GL11.glEnable(GL11.GL_BLEND);
                else GL11.glDisable(GL11.GL_BLEND);
                if (previousCull) GL11.glEnable(GL11.GL_CULL_FACE);
                else GL11.glDisable(GL11.GL_CULL_FACE);
            }
        }

        private void destroy() {
            GL30.glDeleteVertexArrays(vertexArray);
            GL20.glDeleteProgram(program);
        }

        private static int compileShader(int type, String source) {
            int shader = GL20.glCreateShader(type);
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetShaderInfoLog(shader);
                GL20.glDeleteShader(shader);
                throw new IllegalStateException("Failed to compile depth seeding shader: " + log);
            }
            return shader;
        }
    }
}
