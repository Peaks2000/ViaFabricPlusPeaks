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

import com.viaversion.viafabricplus.api.settings.SettingGroup;
import com.viaversion.viafabricplus.api.settings.type.BooleanSetting;
import com.viaversion.viafabricplus.api.settings.type.VersionedBooleanSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersionRange;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;

public final class ClassiCubeSettings extends SettingGroup {

    public static final ClassiCubeSettings INSTANCE = new ClassiCubeSettings();

    public final BooleanSetting automaticallySelectCPEInClassiCubeServerList = AuthenticationSettings.INSTANCE.automaticallySelectCPEInClassiCubeServerList;
    public final BooleanSetting setSessionNameToClassiCubeNameInServerList = AuthenticationSettings.INSTANCE.setSessionNameToClassiCubeNameInServerList;
    public final BooleanSetting torchesStickToWallsClassicube = new BooleanSetting(this, Component.translatable("general_settings.viafabricplus.torches_stick_to_walls_classicube"), false);
    public final VersionedBooleanSetting oldWalkingAnimation = new VersionedBooleanSetting(this, Component.translatable("visual_settings.viafabricplus.old_walking_animation"), ProtocolVersionRange.andOlder(LegacyProtocolVersion.c0_28toc0_30));
    public final BooleanSetting slowDownClassicAnimation = new BooleanSetting(this, Component.translatable("visual_settings.viafabricplus.slow_down_classic_animation"), true);

    public ClassiCubeSettings() {
        super(Component.translatable("setting_group_name.viafabricplus.classicube"));
        torchesStickToWallsClassicube.lockValue();
        getSettings().add(automaticallySelectCPEInClassiCubeServerList);
        getSettings().add(setSessionNameToClassiCubeNameInServerList);
    }

}
