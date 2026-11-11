/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.settings;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.option.SimpleOption;
import org.jetbrains.annotations.Nullable;

/**
 * A scrolling list of rows that hold one or two widgets each.
 *
 * <p>Minecraft 1.20.4's {@code OptionListWidget} takes only options, so a section header
 * or a label beside a slider cannot be a row of it. This list lays rows out the same way:
 * 310 wide and centred, a second widget 160 to the right of the first.
 */
final class RtxOptionList extends ElementListWidget<RtxOptionList.Row> {

    private static final int ITEM_HEIGHT = 25;
    private static final int ROW_WIDTH = 310;
    private static final int COLUMN_WIDTH = 150;
    private static final int COLUMN_PITCH = 160;

    RtxOptionList(MinecraftClient client, int width, int height, int y) {
        super(client, width, height, y, ITEM_HEIGHT);
        this.centerListVertically = false;
    }

    /** Adds a row of one widget, or of two side by side. */
    void addWidgetEntry(ClickableWidget first, @Nullable ClickableWidget second) {
        addEntry(new Row(second == null ? List.of(first) : List.of(first, second)));
    }

    /** Adds one option across the whole row. */
    void addSingleOptionEntry(SimpleOption<?> option) {
        addWidgetEntry(option.createWidget(this.client.options, 0, 0, ROW_WIDTH), null);
    }

    /** Adds options two to a row, the last alone when their number is odd. */
    void addAll(SimpleOption<?>[] options) {
        for (int i = 0; i < options.length; i += 2) {
            addWidgetEntry(
                    options[i].createWidget(this.client.options, 0, 0, COLUMN_WIDTH),
                    i + 1 < options.length
                            ? options[i + 1].createWidget(this.client.options, 0, 0, COLUMN_WIDTH)
                            : null);
        }
    }

    @Override
    public int getRowWidth() {
        return 400;
    }

    @Override
    protected int getScrollbarPositionX() {
        return super.getScrollbarPositionX() + 32;
    }

    final class Row extends ElementListWidget.Entry<Row> {
        private final List<ClickableWidget> widgets;

        private Row(List<ClickableWidget> widgets) {
            this.widgets = widgets;
        }

        @Override
        public void render(
                DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int left = RtxOptionList.this.getX() + RtxOptionList.this.getWidth() / 2 - ROW_WIDTH / 2;
            for (int column = 0; column < widgets.size(); column++) {
                ClickableWidget widget = widgets.get(column);
                widget.setPosition(left + column * COLUMN_PITCH, y);
                widget.render(context, mouseX, mouseY, tickDelta);
            }
        }

        @Override
        public List<? extends Element> children() {
            return widgets;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return widgets;
        }
    }
}
