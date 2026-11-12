/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Debounces the appliers of one screen's deferred sliders.
 *
 * <p>Minecraft 1.19.4's option slider applies its value on every drag step and has no
 * pending value of its own, so the pause lives here: a slider's applier runs once its
 * value has rested for {@link #DELAY_MS}, or when the screen applies everything on
 * close. A value that returns to the one last applied is not applied again.
 */
final class DeferredSliderApply {

    /** The pause after the last drag step before a deferred slider applies. */
    static final long DELAY_MS = 600L;

    private final LongSupplier clockMs;
    private final List<Pending<?>> sliders = new ArrayList<>();

    /** @param clockMs a monotonic clock in milliseconds */
    DeferredSliderApply(LongSupplier clockMs) {
        this.clockMs = clockMs;
    }

    /**
     * Registers a deferred slider.
     *
     * @param applier what a rested value is handed to
     * @param current the value the setting holds now
     * @return the change callback to give the slider's option
     */
    <T> Consumer<T> defer(Consumer<T> applier, T current) {
        Pending<T> slider = new Pending<>(applier, current);
        sliders.add(slider);
        return slider::changed;
    }

    /** Applies every value that has rested for {@link #DELAY_MS}. */
    void applyDue() {
        long nowMs = clockMs.getAsLong();
        for (Pending<?> slider : sliders) {
            if (slider.waiting && nowMs - slider.changedMs >= DELAY_MS) {
                slider.apply();
            }
        }
    }

    /** Applies every pending value now. */
    void applyAll() {
        for (Pending<?> slider : sliders) {
            slider.apply();
        }
    }

    /** Forgets every registered slider; pending values are dropped, so apply them first. */
    void clear() {
        sliders.clear();
    }

    private final class Pending<T> {
        private final Consumer<T> applier;
        private T applied;
        private T value;
        private boolean waiting;
        private long changedMs;

        Pending(Consumer<T> applier, T current) {
            this.applier = applier;
            this.applied = current;
            this.value = current;
        }

        void changed(T newValue) {
            value = newValue;
            waiting = true;
            changedMs = clockMs.getAsLong();
        }

        void apply() {
            waiting = false;
            if (!Objects.equals(value, applied)) {
                applied = value;
                applier.accept(value);
            }
        }
    }
}
