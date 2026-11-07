/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.visibility;

import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

@Tag("fabric")
class SectionOcclusionGraphLifecycleTest {
    @Test
    void transformedGraphInitializesAndClearsItsPropagationQueue() throws Exception {
        SectionOcclusionGraph graph = new SectionOcclusionGraph();
        var field = SectionOcclusionGraph.class.getDeclaredField("rtxremix$propagationQueue");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        TimeSlicedWorkQueue<Object> queue = (TimeSlicedWorkQueue<Object>)
                assertInstanceOf(TimeSlicedWorkQueue.class, field.get(graph));
        queue.add(new Object());

        assertDoesNotThrow(() -> graph.waitAndReset(null));
        assertSame(queue, field.get(graph));
        assertFalse(queue.hasPendingWork());
        assertDoesNotThrow(() -> graph.waitAndReset(null));
    }
}
