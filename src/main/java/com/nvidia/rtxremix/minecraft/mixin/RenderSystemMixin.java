/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.RemixRenderer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the OpenGL device's buffer swap to Remix's present, so Vulkan owns
 * the swapchain whenever the renderer is initialized. Before that, and if
 * initialization fails, the GLFW swap runs unchanged.
 *
 * <p>{@code RenderSystem.flipFrame} reaches the swap through the GPU device, in
 * {@code GlDevice.presentFrame}; the class keeps its name so the mixin config is unchanged.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class RenderSystemMixin {

    @Redirect(
        method = "presentFrame",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V")
    )
    private void rtxremix$redirectSwapBuffers(long window) {
        RemixRenderer renderer = RtxRemixMod.renderer;
        if (renderer != null && renderer.isInitialized()) {
            renderer.presentFrameVk();
        } else {
            GLFW.glfwSwapBuffers(window);
        }
    }
}
