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

package com.viaversion.viafabricplus.settings.impl;

import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.api.settings.base.BooleanSetting;
import com.viaversion.viafabricplus.settings.base.SettingGroupImpl;

public final class PrivacySettings extends SettingGroupImpl {

    public static final PrivacySettings INSTANCE = new PrivacySettings();

    public final BooleanSetting privacyMode = registerBoolean("privacy_mode", true);

    private PrivacySettings() {
        super("privacy");
    }

    @Override
    public void read(final JsonObject object) {
        // Legacy send_client_brand settings upgrade to the privacy-preserving default.
        this.privacyMode.setActive(true);
        super.read(object);
    }

}
