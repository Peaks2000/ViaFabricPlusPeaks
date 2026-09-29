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

package com.viaversion.viafabricplus.screen.impl.settings;

import com.viaversion.viafabricplus.screen.VFPListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class TitleEntry extends VFPListEntry {
    private final Component name;
    public TitleEntry(final Component name) { this.name = name; }
    @Override public Component getNarration() { return this.name; }
    @Override public void mappedRender(final GuiGraphicsExtractor context, final int x, final int y,
        final int width, final int height, final int mouseX, final int mouseY, final boolean hovered, final float tickDelta) {
        context.text(Minecraft.getInstance().font, this.name.copy().withStyle(ChatFormatting.BOLD),
            SLOT_MARGIN, (height - Minecraft.getInstance().font.lineHeight) / 2, -1);
    }
}
