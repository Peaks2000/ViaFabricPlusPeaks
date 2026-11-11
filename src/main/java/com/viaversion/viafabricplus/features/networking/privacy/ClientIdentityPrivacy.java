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
import java.util.Set;

public final class ClientIdentityPrivacy {

    private static final Set<String> ANNOUNCEMENT_CHANNELS = Set.of(
        "minecraft:brand", "minecraft:register", "minecraft:unregister", "c:register", "c:version",
        "brand", "register", "unregister", "MC|Brand", "REGISTER", "UNREGISTER"
    );

    private ClientIdentityPrivacy() {
    }

    public static boolean shouldSuppress(final String channel) {
        return PrivacySettings.INSTANCE.privacyMode.isActive() && ANNOUNCEMENT_CHANNELS.contains(channel);
    }
}
