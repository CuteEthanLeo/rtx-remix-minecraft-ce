/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.capture;

import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableMeshImpl;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fabric")
class RemixRenderContextTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void consumesIndigoMeshThroughCaptureEmitterWithVertexDataIntact() {
        MutableMeshImpl builder = new MutableMeshImpl();
        QuadEmitter emitter = builder.emitter();
        emitter.square(Direction.UP, 0, 0, 1, 1, 0);
        for (int vertex = 0; vertex < 4; vertex++) {
            emitter.color(vertex, 0xFF123456 + vertex);
            emitter.uv(vertex, vertex * 0.125f, vertex * 0.25f);
            emitter.lightmap(vertex, 0x00F000A0 + vertex);
            emitter.normal(vertex, 0, 1, 0);
        }
        emitter.renderLayer(BlockRenderLayer.TRANSLUCENT)
                .emissive(true)
                .diffuseShade(false)
                .ambientOcclusion(TriState.FALSE)
                .shadeMode(ShadeMode.VANILLA);
        emitter.tintIndex(3).tag(42).emit();
        Mesh mesh = builder.immutableCopy();
        RemixRenderContext context = context();
        int[] visits = {0};
        context.pushTransform(quad -> {
            visits[0]++;
            assertSame(BlockRenderLayer.TRANSLUCENT, quad.renderLayer());
            assertTrue(quad.emissive());
            assertFalse(quad.diffuseShade());
            assertSame(TriState.FALSE, quad.ambientOcclusion());
            assertNull(quad.glint());
            assertSame(ShadeMode.VANILLA, quad.shadeMode());
            assertEquals(Direction.UP, quad.cullFace());
            assertEquals(Direction.UP, quad.nominalFace());
            assertEquals(3, quad.tintIndex());
            assertEquals(42, quad.tag());
            for (int vertex = 0; vertex < 4; vertex++) {
                assertEquals(0xFF123456 + vertex, quad.color(vertex));
                assertEquals(0x00F000A0 + vertex, quad.lightmap(vertex));
                assertTrue(quad.hasNormal(vertex));
                assertEquals(1.0f, quad.normalY(vertex));
            }
            return true;
        });

        mesh.outputTo(context.getEmitter());

        assertEquals(1, visits[0]);
        assertEquals(1, context.harvest().size());
        SectionBlockData.BlockQuad captured = context.harvest().getFirst();
        assertArrayEquals(new float[]{0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0},
                captured.positions());
        assertArrayEquals(new float[]{0, 0, 0.125f, 0.25f, 0.25f, 0.5f, 0.375f, 0.75f},
                captured.uvs());
        assertEquals(0xFF123456, captured.color());
        assertEquals(1.0f, captured.ny());
    }

    @Test
    void meshQuadsFollowTransformStackFilteringAndFaceCulling() {
        MutableMeshImpl builder = new MutableMeshImpl();
        QuadEmitter emitter = builder.emitter();
        for (int tag = 0; tag < 3; tag++) {
            emitter.square(Direction.UP, 0, 0, 1, 1, 0).tag(tag).emit();
        }
        Mesh mesh = builder.immutableCopy();
        RemixRenderContext context = context();
        context.prepare(null, null, BlockPos.ORIGIN, face -> face == Direction.NORTH);
        context.pushTransform(quad -> {
            // Transforms run in reverse push order: tag 0 has already been rejected.
            assertTrue(quad.tag() > 0);
            if (quad.tag() == 1) {
                quad.cullFace(Direction.NORTH);
            }
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.pos(vertex, quad.x(vertex) + 2, quad.y(vertex), quad.z(vertex));
            }
            return true;
        });
        context.pushTransform(quad -> quad.tag() != 0);

        mesh.outputTo(context.getEmitter());

        assertEquals(1, context.harvest().size());
        assertEquals(2.0f, context.harvest().getFirst().positions()[0]);
        context.popTransform();
        context.popTransform();
        assertFalse(context.hasTransform());
    }

    @Test
    void repeatedMeshConsumptionClearsStaleEmitterStateAndKeepsEarlierCaptures() {
        MutableMeshImpl builder = new MutableMeshImpl();
        builder.emitter().square(Direction.UP, 0, 0, 1, 1, 0).emit();
        Mesh mesh = builder.immutableCopy();
        RemixRenderContext context = context();
        context.getEmitter().normal(0, 1, 0, 0).tag(99);
        context.pushTransform(quad -> {
            assertFalse(quad.hasNormal(0));
            assertEquals(0, quad.tag());
            return true;
        });

        mesh.outputTo(context.getEmitter());
        mesh.outputTo(context.getEmitter());

        assertEquals(2, context.harvest().size());
        context.prepare(null, null, BlockPos.ORIGIN, null);
        mesh.outputTo(context.getEmitter());
        assertEquals(1, context.harvest().size());
        assertFalse(context.hasTransform());
    }

    @Test
    void meshOutputReplacesStaleNormalsOnTheSameEmitter() {
        MutableMeshImpl builder = new MutableMeshImpl();
        builder.emitter().square(Direction.UP, 0, 0, 1, 1, 0).normal(1, 0, 1, 0).emit();
        Mesh mesh = builder.immutableCopy();
        RemixRenderContext context = context();
        QuadEmitter emitter = context.getEmitter();
        for (int vertex = 0; vertex < 4; vertex++) {
            emitter.normal(vertex, 1, 0, 0);
        }
        int[] vanilla = new int[32];
        int[] visits = {0};
        context.pushTransform(quad -> {
            visits[0]++;
            quad.toVanilla(vanilla, 0);
            for (int vertex = 0; vertex < 4; vertex++) {
                if (vertex == 1) {
                    assertTrue(quad.hasNormal(vertex));
                    assertEquals(0.0f, quad.normalX(vertex));
                    assertEquals(1.0f, quad.normalY(vertex));
                    assertEquals(0.0f, quad.normalZ(vertex));
                    assertEquals(0x00007F00, vanilla[vertex * 8 + 7]);
                } else {
                    assertFalse(quad.hasNormal(vertex));
                    assertTrue(Float.isNaN(quad.normalX(vertex)));
                    assertTrue(Float.isNaN(quad.normalY(vertex)));
                    assertTrue(Float.isNaN(quad.normalZ(vertex)));
                    assertNull(quad.copyNormal(vertex, null));
                    assertEquals(0, vanilla[vertex * 8 + 7]);
                }
            }
            return true;
        });

        mesh.outputTo(emitter);

        assertEquals(1, visits[0]);
        assertEquals(1, context.harvest().size());
    }

    @Test
    void emptyMeshDoesNotEmitAndClearsStaleEmitterState() {
        Mesh empty = new MutableMeshImpl().immutableCopy();
        RemixRenderContext context = context();
        QuadEmitter emitter = context.getEmitter().normal(0, 1, 0, 0).tag(99);
        context.pushTransform(quad -> {
            assertFalse(quad.hasNormal(0));
            assertEquals(0, quad.tag());
            return true;
        });

        empty.outputTo(context.getEmitter());

        assertTrue(context.harvest().isEmpty());
        emitter.square(Direction.UP, 0, 0, 1, 1, 0).emit();
        assertEquals(1, context.harvest().size());
    }

    @Test
    void outputToIndigoEmitterPreservesQuadsAndClearsEmitter() {
        MutableMeshImpl source = new MutableMeshImpl();
        source.emitter().square(Direction.UP, 0, 0, 1, 1, 0).tag(42).emit();
        source.emitter().square(Direction.NORTH, 0, 0, 1, 1, 0).tag(43).emit();
        MutableMeshImpl target = new MutableMeshImpl();
        QuadEmitter emitter = target.emitter().normal(0, 1, 0, 0).tag(99);

        source.immutableCopy().outputTo(emitter);

        assertEquals(0, emitter.tag());
        assertFalse(emitter.hasNormal(0));
        int[] visits = {0};
        target.immutableCopy().forEach(quad -> {
            assertEquals(42 + visits[0], quad.tag());
            assertEquals(visits[0] == 0 ? Direction.UP : Direction.NORTH, quad.cullFace());
            assertFalse(quad.hasNormal(0));
            visits[0]++;
        });
        assertEquals(2, visits[0]);
    }

    @Test
    void nestedMeshOutputRestoresTheOuterEmitter() {
        MutableMeshImpl outerBuilder = new MutableMeshImpl();
        outerBuilder.emitter().square(Direction.UP, 0, 0, 1, 1, 0).tag(1).emit();
        outerBuilder.emitter().square(Direction.UP, 0, 0, 1, 1, 0).tag(2).emit();
        MutableMeshImpl innerBuilder = new MutableMeshImpl();
        innerBuilder.emitter().square(Direction.NORTH, 0, 0, 1, 1, 0).emit();
        Mesh innerMesh = innerBuilder.immutableCopy();
        RemixRenderContext outer = context();
        RemixRenderContext inner = context();
        outer.pushTransform(quad -> {
            if (quad.tag() == 1) {
                innerMesh.outputTo(inner.getEmitter());
            }
            return true;
        });

        outerBuilder.immutableCopy().outputTo(outer.getEmitter());

        assertEquals(2, outer.harvest().size());
        assertEquals(1, inner.harvest().size());
        assertEquals(1.0f, outer.harvest().getLast().ny());
        assertEquals(-1.0f, inner.harvest().getFirst().nz());
    }

    private static RemixRenderContext context() {
        RemixRenderContext context = new RemixRenderContext();
        context.prepare(null, null, BlockPos.ORIGIN, null);
        return context;
    }
}
