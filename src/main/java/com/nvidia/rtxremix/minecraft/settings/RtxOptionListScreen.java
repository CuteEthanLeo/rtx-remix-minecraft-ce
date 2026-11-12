/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * An options screen whose body is one option list between a title and a footer.
 *
 * <p>Minecraft 1.20.1's {@code GameOptionsScreen} lays nothing out, and a resize runs
 * {@code init} again, so the layout and the list are built there from the current size.
 * A screen draws its own background on this version, and the list is added first so the
 * header and footer strips it draws stay under the title and the footer buttons.
 */
abstract class RtxOptionListScreen extends GameOptionsScreen {

    private static final int HEADER_HEIGHT = 33;
    private static final int FOOTER_HEIGHT = 33;

    protected final DeferredSliderApply deferredSliders = new DeferredSliderApply(Util::getMeasuringTimeMs);
    protected ThreePartsLayoutWidget layout;
    protected RtxOptionList body;

    RtxOptionListScreen(Screen parent, GameOptions gameOptions, Text title) {
        super(parent, gameOptions, title);
    }

    @Override
    protected void init() {
        // The sliders of the list a resize replaces must not lose a value still pending.
        this.deferredSliders.applyAll();
        this.deferredSliders.clear();

        this.layout = new ThreePartsLayoutWidget(this, HEADER_HEIGHT, FOOTER_HEIGHT);
        this.layout.addHeader(new TextWidget(this.title, this.textRenderer));
        this.body = this.addDrawableChild(new RtxOptionList(
                this.client, this.width, this.height, HEADER_HEIGHT, this.height - FOOTER_HEIGHT));
        // Over a world the list stays see-through, so a setting's effect shows behind it.
        boolean opaque = this.client.world == null;
        this.body.setRenderBackground(opaque);
        this.body.setRenderHorizontalShadows(opaque);
        this.addOptions();
        this.initFooter();
        this.layout.forEachChild(this::addDrawableChild);
        this.layout.refreshPositions();
    }

    /** Fills {@link #body}; called once per {@code init}. */
    protected abstract void addOptions();

    /** Adds the footer to {@link #layout}; called once per {@code init}. */
    protected abstract void initFooter();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.deferredSliders.applyDue();
    }
}
