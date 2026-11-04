/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * The clock for effects that move in real time: shooting stars, motion blur, the lightning
 * flash, ground wetness and the conf-layer blends.
 *
 * <p>While playing it is the wall clock. While Flashback exports a replay it is the export's
 * own timeline instead: exporting renders frame by frame, far slower than real time, so a
 * wall-clock effect would race ahead of the picture (a meteor would cross the sky in one or two
 * frames, and the motion-blur shutter would shrink to nothing). The export clock starts at the
 * wall time the export began, so schedules made before it stay valid, and advances by the
 * export's frame time per rendered frame, read from Flashback's timeline so that the extra
 * frames Flashback renders at one instant do not count. The Remix runtime's own frame time,
 * which drives water animation and eye adaptation, is pinned to the export frame time too.
 *
 * <p>Without Flashback this is the wall clock and nothing else.
 */
public final class ExportClock {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportClock.class);
    /** Length of one game tick; Flashback's export timeline counts in ticks. */
    static final long NANOS_PER_TICK = 50_000_000L;
    /** Remix's frame-time override in milliseconds; 0 uses the measured frame time. */
    static final String TIME_DELTA_KEY = "rtx.timeDeltaBetweenFrames";

    /** What the clock needs to know about an export in progress. */
    interface Export {
        /** Whether an export is running now. Called first each frame. */
        boolean running();

        /** Ticks of the export timeline passed so far, fractional; only while running. */
        double tick();

        /** Frames per second of the export; only while running. */
        double framerate();
    }

    private static final class Holder {
        static final ExportClock INSTANCE = new ExportClock(FlashbackExport.find(), System::nanoTime);
    }

    private final Export export;
    private final LongSupplier wallClock;
    private boolean exporting;
    private long startNanos;
    private long exportNanos;
    private float frameSeconds;

    ExportClock(Export export, LongSupplier wallClock) {
        this.export = export;
        this.wallClock = wallClock;
    }

    /** The clock the renderer uses. */
    public static ExportClock get() {
        return Holder.INSTANCE;
    }

    /**
     * Notices an export starting or ending and moves the export clock to this frame. Render
     * thread only; call once at the start of each rendered frame.
     *
     * @param configWriter receives Remix's frame-time override when an export starts or ends
     */
    public void onFrame(BiConsumer<String, String> configWriter) {
        boolean running = export != null && export.running();
        if (running && !exporting) {
            startNanos = wallClock.getAsLong();
            frameSeconds = (float) (1.0 / export.framerate());
            writeTimeDelta(configWriter, Float.toString(frameSeconds * 1000.0f));
            LOGGER.info("Flashback export started: effects follow the export clock at {} fps",
                    export.framerate());
        } else if (!running && exporting) {
            frameSeconds = 0.0f;
            writeTimeDelta(configWriter, "0");
            LOGGER.info("Flashback export ended: effects follow the wall clock again");
        }
        exporting = running;
        if (running) {
            exportNanos = startNanos + Math.round(export.tick() * NANOS_PER_TICK);
        }
    }

    private static void writeTimeDelta(BiConsumer<String, String> configWriter, String millis) {
        try {
            configWriter.accept(TIME_DELTA_KEY, millis);
        } catch (RuntimeException e) {
            // The effect clock still holds; only water animation and eye adaptation keep
            // measuring their own time.
            LOGGER.warn("Remix rejected {} = {}: {}", TIME_DELTA_KEY, millis, e.toString());
        }
    }

    /** Now, in nanoseconds: the export clock during an export, else {@link System#nanoTime()}. */
    public long nanos() {
        return exporting ? exportNanos : wallClock.getAsLong();
    }

    /** The export's frame time in seconds during an export, else 0 for "measure it". */
    public float frameSeconds() {
        return frameSeconds;
    }

    /** Whether a Flashback export is running. */
    public boolean isExporting() {
        return exporting;
    }
}
