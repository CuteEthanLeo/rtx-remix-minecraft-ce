/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.settings;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;

/**
 * An options screen whose body is one option list.
 *
 * <p>Minecraft 1.20.6's {@code GameOptionsScreen} lays out only a header and a footer, so the
 * list is created and positioned here the way vanilla's {@code SimpleOptionsScreen} does it.
 */
abstract class RtxOptionListScreen extends GameOptionsScreen {

    protected OptionListWidget body;

    RtxOptionListScreen(Screen parent, GameOptions gameOptions, Text title) {
        super(parent, gameOptions, title);
    }

    @Override
    protected void init() {
        this.body = this.addDrawableChild(new OptionListWidget(this.client, this.width, this.height, this));
        this.addOptions();
        super.init();
    }

    /** Fills {@link #body}; called once per {@code init}. */
    protected abstract void addOptions();

    @Override
    protected void initTabNavigation() {
        super.initTabNavigation();
        this.body.position(this.width, this.layout);
    }
}
