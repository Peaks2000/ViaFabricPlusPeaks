/*
 * This file is part of ViaFabricPlus - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class VFPListEntry extends com.viaversion.viafabricplus.screen.base.list.VFPListEntry {
    private GuiGraphicsExtractor context;
    private int mouseX;
    private int mouseY;
    private boolean hovered;
    private float tickDelta;
    private MouseButtonEvent click;

    public void mappedRender(final GuiGraphicsExtractor context, final int x, final int y,
                             final int width, final int height, final int mouseX, final int mouseY,
                             final boolean hovered, final float tickDelta) { }
    public void mappedMouseClicked(final double x, final double y, final int button) { }

    @Override
    public void extractContent(final GuiGraphicsExtractor context, final int mouseX, final int mouseY,
                               final boolean hovered, final float tickDelta) {
        this.context = context;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.hovered = hovered;
        this.tickDelta = tickDelta;
        super.extractContent(context, mouseX, mouseY, hovered, tickDelta);
    }

    @Override
    public void mappedRender(final GuiGraphicsExtractor context, final int width, final int height) {
        this.mappedRender(context, this.getContentX(), this.getContentY(), width, height,
            this.mouseX, this.mouseY, this.hovered, this.tickDelta);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent click, final boolean doubled) {
        this.click = click;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void mappedMouseClicked() {
        if (this.click != null) this.mappedMouseClicked(this.click.x(), this.click.y(), this.click.button());
    }

    public void renderScrollableText(final Component text, final int offset) {
        super.renderScrollableText(this.context, text, offset);
    }

    public void renderScrollableText(final Component text, final int textY, final int offset) {
        final var matrices = this.context.pose();
        matrices.pushMatrix();
        matrices.translate(0, textY - (this.getContentHeight() - Minecraft.getInstance().font.lineHeight) / 2);
        super.renderScrollableText(this.context, text, offset);
        matrices.popMatrix();
    }

    public void renderTooltip(final Component tooltip, final int mouseX, final int mouseY) {
        if (tooltip != null && this.hovered) {
            this.context.setTooltipForNextFrame(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
        }
    }
}
