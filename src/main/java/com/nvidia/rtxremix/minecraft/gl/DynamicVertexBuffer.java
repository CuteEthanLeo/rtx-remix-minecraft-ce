/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.gl;

/**
 * A {@code VertexBuffer} that can be told vanilla refills it on every draw, implemented by
 * the VertexBuffer mixin.
 *
 * <p>Minecraft 1.19.4 gives a vertex buffer no usage: every upload passes the STATIC hint,
 * including the buffer each vertex format shares between immediate draws, which later
 * versions create as DYNAMIC. That shared buffer is marked here instead.
 */
public interface DynamicVertexBuffer {

    /** Routes this buffer's uploads through {@link DynamicBufferUpload}. Render thread only. */
    void rtxremix$markDynamic();
}
