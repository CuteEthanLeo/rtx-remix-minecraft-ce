/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportClockTest {

    private static final long SECOND = 1_000_000_000L;

    /** A stand-in for Flashback's export job. */
    private static final class FakeExport implements ExportClock.Export {
        boolean running;
        double tick;
        double framerate = 60.0;

        @Override
        public boolean running() {
            return running;
        }

        @Override
        public double tick() {
            return tick;
        }

        @Override
        public double framerate() {
            return framerate;
        }
    }

    private long wall = 1000 * SECOND;

    @Test
    void withoutFlashbackItIsTheWallClock() {
        ExportClock clock = new ExportClock(null, () -> wall);
        Map<String, String> written = new LinkedHashMap<>();

        clock.onFrame(written::put);
        assertEquals(wall, clock.nanos());
        wall += SECOND / 7;
        assertEquals(wall, clock.nanos(), "read live, not once per frame");
        assertEquals(0.0f, clock.frameSeconds());
        assertFalse(clock.isExporting());
        assertTrue(written.isEmpty());
    }

    @Test
    void flashbackIsNotOnTheTestClasspath() {
        assertNull(FlashbackExport.find());
    }

    @Test
    void anExportAdvancesByItsTimelineNotByTheWallClock() {
        FakeExport export = new FakeExport();
        ExportClock clock = new ExportClock(export, () -> wall);
        Map<String, String> written = new LinkedHashMap<>();
        clock.onFrame(written::put);
        long before = clock.nanos();

        export.running = true;
        clock.onFrame(written::put);
        assertTrue(clock.isExporting());
        assertEquals(before, clock.nanos(), "the export clock starts where the wall clock was");
        assertEquals(1.0f / 60.0f, clock.frameSeconds(), 1.0e-7f);
        assertEquals(1000.0f / 60.0f, Float.parseFloat(written.get(ExportClock.TIME_DELTA_KEY)), 1.0e-3f);

        // Each exported frame takes two wall seconds but moves the timeline 1/60 s.
        for (int frame = 1; frame <= 60; frame++) {
            wall += 2 * SECOND;
            export.tick = frame * 20.0 / 60.0;
            clock.onFrame(written::put);
            assertEquals(before + frame * SECOND / 60, clock.nanos(), 1_000L);
        }
        assertEquals(before + SECOND, clock.nanos(), 1_000L);
    }

    @Test
    void framesRenderedAtOneInstantDoNotMoveTheClock() {
        FakeExport export = new FakeExport();
        ExportClock clock = new ExportClock(export, () -> wall);
        export.running = true;
        export.tick = 0.0;
        clock.onFrame((key, value) -> { });
        long start = clock.nanos();
        for (int dummy = 0; dummy < 5; dummy++) {
            wall += SECOND;
            clock.onFrame((key, value) -> { });
        }
        assertEquals(start, clock.nanos());
    }

    @Test
    void theWallClockResumesWhenTheExportEnds() {
        FakeExport export = new FakeExport();
        ExportClock clock = new ExportClock(export, () -> wall);
        Map<String, String> written = new LinkedHashMap<>();
        export.running = true;
        clock.onFrame(written::put);
        wall += 100 * SECOND;
        export.tick = 20.0;
        clock.onFrame(written::put);

        export.running = false;
        clock.onFrame(written::put);
        assertFalse(clock.isExporting());
        assertEquals(wall, clock.nanos());
        assertEquals(0.0f, clock.frameSeconds());
        assertEquals("0", written.get(ExportClock.TIME_DELTA_KEY));
    }

    @Test
    void aRejectedRuntimeOverrideDoesNotStopTheClock() {
        FakeExport export = new FakeExport();
        ExportClock clock = new ExportClock(export, () -> wall);
        export.running = true;
        clock.onFrame((key, value) -> {
            throw new IllegalArgumentException("unknown option " + key);
        });
        assertTrue(clock.isExporting());
        assertEquals(1.0f / 60.0f, clock.frameSeconds(), 1.0e-7f);
    }
}
