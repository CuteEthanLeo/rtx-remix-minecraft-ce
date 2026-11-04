/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reads Flashback's export state by reflection, so the mod neither depends on Flashback nor
 * breaks without it: {@code Flashback.EXPORT_JOB}, its {@code isRunning()},
 * {@code getCurrentTickDouble()} and {@code getSettings().framerate()}.
 *
 * <p>The handles are typed exactly, so each call is a plain invocation with no boxing or
 * allocation on the per-frame path. If Flashback changes shape and a call fails, the failure
 * is logged once and the clock falls back to wall time for the rest of the session.
 */
final class FlashbackExport implements ExportClock.Export {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlashbackExport.class);
    private static final String FLASHBACK = "com.moulberry.flashback.Flashback";
    private static final String EXPORT_JOB = "com.moulberry.flashback.exporting.ExportJob";
    private static final String EXPORT_SETTINGS = "com.moulberry.flashback.exporting.ExportSettings";

    private final MethodHandle currentJob;
    private final MethodHandle isRunning;
    private final MethodHandle currentTick;
    private final MethodHandle framerate;
    private Object job;
    private boolean failed;

    private FlashbackExport(MethodHandle currentJob, MethodHandle isRunning,
                            MethodHandle currentTick, MethodHandle framerate) {
        this.currentJob = currentJob;
        this.isRunning = isRunning;
        this.currentTick = currentTick;
        this.framerate = framerate;
    }

    /** Flashback's export state, or null when Flashback is not installed or not recognised. */
    static ExportClock.Export find() {
        ClassLoader loader = FlashbackExport.class.getClassLoader();
        Class<?> flashback;
        try {
            flashback = Class.forName(FLASHBACK, false, loader);
        } catch (ClassNotFoundException absent) {
            return null;
        }
        try {
            Class<?> jobClass = Class.forName(EXPORT_JOB, false, loader);
            Class<?> settingsClass = Class.forName(EXPORT_SETTINGS, false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle currentJob = lookup.findStaticGetter(flashback, "EXPORT_JOB", jobClass)
                    .asType(MethodType.methodType(Object.class));
            MethodHandle isRunning = lookup.findVirtual(jobClass, "isRunning",
                            MethodType.methodType(boolean.class))
                    .asType(MethodType.methodType(boolean.class, Object.class));
            MethodHandle currentTick = lookup.findVirtual(jobClass, "getCurrentTickDouble",
                            MethodType.methodType(double.class))
                    .asType(MethodType.methodType(double.class, Object.class));
            MethodHandle settings = lookup.findVirtual(jobClass, "getSettings",
                    MethodType.methodType(settingsClass));
            MethodHandle settingsFramerate = lookup.findVirtual(settingsClass, "framerate",
                    MethodType.methodType(double.class));
            MethodHandle framerate = MethodHandles.filterReturnValue(settings, settingsFramerate)
                    .asType(MethodType.methodType(double.class, Object.class));
            LOGGER.info("Flashback found: exports drive the effect clock");
            return new FlashbackExport(currentJob, isRunning, currentTick, framerate);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Flashback is installed but its export API was not recognised ({}); "
                    + "time-driven effects use the wall clock during exports", e.toString());
            return null;
        }
    }

    @Override
    public boolean running() {
        if (failed) {
            return false;
        }
        try {
            job = (Object) currentJob.invokeExact();
            return job != null && (boolean) isRunning.invokeExact(job);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    @Override
    public double tick() {
        try {
            return (double) currentTick.invokeExact(job);
        } catch (Throwable t) {
            fail(t);
            return 0.0;
        }
    }

    @Override
    public double framerate() {
        try {
            return (double) framerate.invokeExact(job);
        } catch (Throwable t) {
            fail(t);
            return 60.0;
        }
    }

    private void fail(Throwable t) {
        if (!failed) {
            failed = true;
            LOGGER.error("Reading Flashback's export state failed; time-driven effects use the "
                    + "wall clock from now on", t);
        }
    }
}
