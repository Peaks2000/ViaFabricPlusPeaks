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

import com.viaversion.viafabricplus.settings.SettingGroup;
import com.viaversion.viafabricplus.settings.base.BooleanSettingImpl;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

public class BooleanSetting extends BooleanSettingImpl {
    private boolean locked;
    private Boolean initialValue;

    public BooleanSetting(final SettingGroup group, final MutableComponent name, final boolean defaultValue) {
        super(((TranslatableContents) name.getContents()).getKey().split("viafabricplus\\.", 2)[1], name, defaultValue);
        group.register(this.key(), this);
    }

    public boolean getValue() {
        if (!this.locked) return this.isActive();
        if (this.initialValue == null) this.initialValue = this.defaultValue();
        return this.initialValue;
    }

    public void setValue(final boolean value) {
        this.setActive(value);
    }

    @Override
    public void setActive(final boolean value) {
        if (this.locked && this.initialValue == null) this.initialValue = value;
        super.setActive(value);
    }

    public void lockValue() {
        this.locked = true;
    }
}
