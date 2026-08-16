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

package com.viaversion.viafabricplus.screen.impl.classic4j;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianreuth.classic4j.model.betacraft.BCServerInfo;
import java.util.Locale;
import java.util.Map;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;

final class BetaCraftServerListSupport {

    private static final Map<String, ProtocolVersion> GAME_VERSION_MAP = Map.ofEntries(
        Map.entry("c0.30-c-1900", LegacyProtocolVersion.c0_28toc0_30),
        Map.entry("a1.1.2_01", LegacyProtocolVersion.a1_1_0toa1_1_2_1),
        Map.entry("a1.2.6", LegacyProtocolVersion.a1_2_3_5toa1_2_6),
        Map.entry("b1.0", LegacyProtocolVersion.b1_0tob1_1_1),
        Map.entry("b1.1_02", LegacyProtocolVersion.b1_1_2),
        Map.entry("b1.2_01", LegacyProtocolVersion.b1_2_0tob1_2_2),
        Map.entry("b1.5_01", LegacyProtocolVersion.b1_5tob1_5_2),
        Map.entry("b1.6.6", LegacyProtocolVersion.b1_6tob1_6_6),
        Map.entry("b1.7.3", LegacyProtocolVersion.b1_7tob1_7_3),
        Map.entry("b1.9pre6", LegacyProtocolVersion.r1_0_0tor1_0_1),
        Map.entry("1.2.5", LegacyProtocolVersion.r1_2_4tor1_2_5),
        Map.entry("1.4", LegacyProtocolVersion.r1_4_2),
        Map.entry("1.4.7", LegacyProtocolVersion.r1_4_6tor1_4_7),
        Map.entry("1.5.2", LegacyProtocolVersion.r1_5_2),
        Map.entry("1.6.4", LegacyProtocolVersion.r1_6_4)
    );

    private BetaCraftServerListSupport() {
    }

    static String normalizeQuery(final String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    static boolean matchesQuery(final BCServerInfo server, final String query) {
        final String normalizedQuery = normalizeQuery(query);
        return normalizedQuery.isEmpty()
            || contains(server.name(), normalizedQuery)
            || contains(server.socket(), normalizedQuery)
            || contains(server.gameVersion(), normalizedQuery);
    }

    static ProtocolVersion determineVersion(final BCServerInfo server) {
        final String gameVersion = normalizeQuery(server.gameVersion());
        final ProtocolVersion mappedVersion = GAME_VERSION_MAP.get(gameVersion);
        if (mappedVersion != null) {
            return mappedVersion;
        }
        if (gameVersion.startsWith("c0.30")) {
            return LegacyProtocolVersion.c0_28toc0_30;
        }

        final String protocol = server.protocol();
        if (protocol == null) {
            return null;
        }
        try {
            final int protocolId = Integer.parseInt(protocol.trim());
            return ProtocolVersion.isRegistered(protocolId) ? ProtocolVersion.getProtocol(protocolId) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean contains(final String value, final String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

}
