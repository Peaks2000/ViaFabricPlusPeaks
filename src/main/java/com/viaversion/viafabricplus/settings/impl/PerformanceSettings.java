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

import com.viaversion.viafabricplus.settings.SettingGroup;
import com.viaversion.viafabricplus.settings.type.BooleanSetting;
import net.minecraft.network.chat.Component;

public final class PerformanceSettings extends SettingGroup {

    public static final PerformanceSettings INSTANCE = new PerformanceSettings();

    public final BooleanSetting disableShadersOnClassicubeServers = new BooleanSetting(this, Component.translatable("performance_settings.viafabricplus.disable_shaders_on_classicube_servers"), false);
    public final BooleanSetting disableShadersOnBetacraftServers = new BooleanSetting(this, Component.translatable("performance_settings.viafabricplus.disable_shaders_on_betacraft_servers"), false);
    public final BooleanSetting disableShadersOnBedrockServers = new BooleanSetting(this, Component.translatable("performance_settings.viafabricplus.disable_shaders_on_bedrock_servers"), false);

    public PerformanceSettings() {
        super(Component.translatable("setting_group_name.viafabricplus.performance"));
    }

}
