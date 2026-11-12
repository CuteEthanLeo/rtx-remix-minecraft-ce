/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.visibility;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.render.WorldRenderer.ChunkInfo;

import java.util.NoSuchElementException;

/**
 * Reusable iterator for a compact render-thread buffer of visibility-graph nodes.
 */
public final class ChunkInfoArrayIterator implements ObjectListIterator<ChunkInfo> {
    private ChunkInfo[] chunks;
    private int size;
    private int index;

    public ChunkInfoArrayIterator reset(ChunkInfo[] chunks, int size) {
        this.chunks = chunks;
        this.size = size;
        this.index = 0;
        return this;
    }

    @Override
    public boolean hasNext() {
        return index < size;
    }

    @Override
    public ChunkInfo next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return chunks[index++];
    }

    @Override
    public boolean hasPrevious() {
        return index > 0;
    }

    @Override
    public ChunkInfo previous() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        return chunks[--index];
    }

    @Override
    public int nextIndex() {
        return index;
    }

    @Override
    public int previousIndex() {
        return index - 1;
    }
}
