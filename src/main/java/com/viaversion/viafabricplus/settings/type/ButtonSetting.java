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

package com.viaversion.viafabricplus.settings.type;

import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.api.settings.base.Setting;
import com.viaversion.viafabricplus.settings.SettingGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ButtonSetting implements Setting {
    private final MutableComponent name;
    private final Runnable action;

    public ButtonSetting(final SettingGroup group, final MutableComponent name, final Runnable action) {
        this.name = name;
        this.action = action;
        group.register(name.getString(), this);
    }

    @Override public Component name() { return this.name; }
    public MutableComponent displayValue() { return this.name; }
    public Runnable getValue() { return this.action; }
    @Override public void read(final JsonObject object) { }
    @Override public void write(final JsonObject object) { }
}
