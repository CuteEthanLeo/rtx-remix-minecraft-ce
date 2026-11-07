/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.opengl.GlBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_DOUBLEBUFFER;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;

/**
 * Raises the OpenGL context the window is created with to what the Remix GL
 * interop needs. {@code Window.createGlfwWindow} asks the backend for its
 * window hints immediately before {@code glfwCreateWindow}, and vanilla's GL
 * backend requests a 3.3 core context.
 */
@Mixin(GlBackend.class)
public abstract class GlBackendMixin {

    @Inject(method = "setWindowHints", at = @At("TAIL"))
    private void rtxremix$requireOpenGl45(CallbackInfo ci) {
        // Core-profile 4.5 supplies DSA framebuffer blits and upper-left clip control.
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        // Vulkan owns presentation; a WGL back buffer can reserve the HWND
        // before Remix creates its swapchain.
        glfwWindowHint(GLFW_DOUBLEBUFFER, GLFW_FALSE);
    }
}
