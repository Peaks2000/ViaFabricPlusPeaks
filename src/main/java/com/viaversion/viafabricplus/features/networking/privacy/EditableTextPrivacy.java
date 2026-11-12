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

package com.viaversion.viafabricplus.features.networking.privacy;

import com.viaversion.viafabricplus.settings.impl.PrivacySettings;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class EditableTextPrivacy {

    private EditableTextPrivacy() {
    }

    public static String getEditableText(final Component component) {
        if (!PrivacySettings.INSTANCE.privacyMode.isActive()) {
            return component.getString();
        }
        final StringBuilder text = new StringBuilder();
        appendServerText(component, text);
        return text.toString();
    }

    private static void appendServerText(final Component component, final StringBuilder text) {
        // Editable text is returned to the server, so resolving local resources here exposes installed mods and bindings.
        switch (component.getContents()) {
            case TranslatableContents translation -> text.append(translation.getFallback() != null ? translation.getFallback() : translation.getKey());
            case KeybindContents keybind -> text.append(keybind.getName());
            case ObjectContents object -> {
                if (object.fallback().isPresent()) {
                    appendServerText(object.fallback().get(), text);
                } else {
                    text.append(object.contents().defaultFallback());
                }
            }
            default -> component.getContents().visit(value -> {
                text.append(value);
                return Optional.empty();
            });
        }
        for (Component sibling : component.getSiblings()) {
            appendServerText(sibling, text);
        }
    }
}
