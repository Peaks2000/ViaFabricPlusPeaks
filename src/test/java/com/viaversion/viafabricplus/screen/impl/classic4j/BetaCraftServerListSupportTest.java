/*
 * This file is part of ViaFabricPlus - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viafabricplus.screen.impl.classic4j;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianreuth.classic4j.model.betacraft.BCServerInfo;
import de.florianreuth.classic4j.model.betacraft.BCVersionCategory;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BetaCraftServerListSupportTest {

    @Test
    public void searchMatchesNameAddressAndVersionWithoutNullFailures() {
        final BCServerInfo server = server("Classic Realm", "A1.2.6", "alpha_6", "example.test:25565");
        assertTrue(BetaCraftServerListSupport.matchesQuery(server, " CLASSIC "));
        assertTrue(BetaCraftServerListSupport.matchesQuery(server, "example.test"));
        assertTrue(BetaCraftServerListSupport.matchesQuery(server, "a1.2.6"));
        assertFalse(BetaCraftServerListSupport.matchesQuery(server, "release"));

        final BCServerInfo missingFields = server(null, null, null, null);
        assertTrue(BetaCraftServerListSupport.matchesQuery(missingFields, ""));
        assertFalse(BetaCraftServerListSupport.matchesQuery(missingFields, "anything"));
    }

    @Test
    public void knownLegacyAndNumericProtocolsResolve() {
        assertEquals(LegacyProtocolVersion.c0_28toc0_30,
            BetaCraftServerListSupport.determineVersion(server("Classic", "c0.30-c-1900", "classic_7", "localhost")));
        assertEquals(LegacyProtocolVersion.a1_2_3_5toa1_2_6,
            BetaCraftServerListSupport.determineVersion(server("Alpha", "A1.2.6", "alpha_6", "localhost")));
        assertEquals(LegacyProtocolVersion.b1_0tob1_1_1,
            BetaCraftServerListSupport.determineVersion(server("Beta", "b1.0", "beta_7", "localhost")));
        assertEquals(LegacyProtocolVersion.r1_0_0tor1_0_1,
            BetaCraftServerListSupport.determineVersion(server("Beta", "b1.9pre6", "release_22", "localhost")));
        assertEquals(ProtocolVersion.v1_12_2,
            BetaCraftServerListSupport.determineVersion(server("Modern", "1.12.2", "340", "localhost")));
    }

    @Test
    public void unsupportedLabelsFallBackInsteadOfGuessing() {
        assertNull(BetaCraftServerListSupport.determineVersion(server("Snapshot", "12w32a", "release_40", "localhost")));
        assertNull(BetaCraftServerListSupport.determineVersion(server("Unknown", null, "unknown", "localhost")));
    }

    private static BCServerInfo server(final String name, final String gameVersion, final String protocol, final String socket) {
        return new BCServerInfo(name, null, null, true, null, 20, 0, null, 0L,
            BCVersionCategory.BETA, gameVersion, protocol, socket, null, false);
    }

}
