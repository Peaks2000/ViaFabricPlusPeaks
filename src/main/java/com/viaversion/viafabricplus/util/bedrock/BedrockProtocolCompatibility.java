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

package com.viaversion.viafabricplus.util.bedrock;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

/**
 * Selects the Bedrock wire version independently of ViaVersion's internal
 * protocol route. ViaBedrock's current update contains the 2169 packet layout,
 * but still exposes its route under the older 2168 special protocol id.
 */
public final class BedrockProtocolCompatibility {

    public static final int UNKNOWN_PROTOCOL = -1;
    public static final int VIA_BEDROCK_ROUTE_PROTOCOL = 2168;
    public static final int CURRENT_PROTOCOL = 2169;
    public static final String CURRENT_GAME_VERSION = "1.26.50";

    private static final AtomicInteger NEXT_CONNECTION_PROTOCOL = new AtomicInteger(UNKNOWN_PROTOCOL);
    private static final AtomicBoolean NEXT_CONNECTION_USES_MAINTAINED_ROUTE = new AtomicBoolean();

    private BedrockProtocolCompatibility() {
    }

    public static int protocolForNetherNetAdvertisement(final int advertisementVersion) {
        // This is a NetherNet discovery format revision, not a Bedrock game
        // protocol. Treating revision 5 as protocol 2169 caused 1.26.40 hosts
        // to report the normal "server old" login failure.
        return UNKNOWN_PROTOCOL;
    }

    public static int protocolForGameVersion(final String version) {
        if (version == null) {
            return UNKNOWN_PROTOCOL;
        }
        final String normalized = version.startsWith("1.") ? version.substring(2) : version;
        if (normalized.startsWith("26.50")) {
            return CURRENT_PROTOCOL;
        }
        if (normalized.startsWith("26.40")) {
            return VIA_BEDROCK_ROUTE_PROTOCOL;
        }
        return UNKNOWN_PROTOCOL;
    }

    public static void prepareConnection(final int protocolVersion) {
        NEXT_CONNECTION_PROTOCOL.set(initialProtocol(protocolVersion));
        NEXT_CONNECTION_USES_MAINTAINED_ROUTE.set(true);
    }

    /**
     * Keeps the normal multiplayer menu on stock ViaBedrock while the dedicated LAN/friends
     * menu opts into the maintained 1.26.40 route through {@link #prepareConnection(int)}.
     */
    public static ProtocolVersion routeForConnection(final ProtocolVersion requestedVersion) {
        if (!BedrockProtocolVersion.bedrockLatest.equals(requestedVersion)) {
            return requestedVersion;
        }
        if (NEXT_CONNECTION_USES_MAINTAINED_ROUTE.getAndSet(false)) {
            ViaFabricPlusImpl.INSTANCE.getLogger().info("Selected maintained Bedrock route for LAN/friends menu: {}", requestedVersion.getName());
            return requestedVersion;
        }
        NEXT_CONNECTION_PROTOCOL.set(UNKNOWN_PROTOCOL);
        final ProtocolVersion stockVersion = StockViaBedrockRuntime.stockVersion();
        ViaFabricPlusImpl.INSTANCE.getLogger().info("Selected stock Bedrock route for normal server menu: {}", stockVersion.getName());
        return stockVersion;
    }

    public static int consumeConnectionProtocol(final int fallbackProtocolVersion) {
        final int protocolVersion = NEXT_CONNECTION_PROTOCOL.getAndSet(UNKNOWN_PROTOCOL);
        return isSupported(protocolVersion) ? protocolVersion : fallbackProtocolVersion;
    }

    public static String gameVersion(final int protocolVersion, final String fallbackVersion) {
        return protocolVersion == CURRENT_PROTOCOL ? CURRENT_GAME_VERSION : fallbackVersion;
    }

    public static int initialProtocol(final int advertisedProtocolVersion) {
        return isSupported(advertisedProtocolVersion) ? advertisedProtocolVersion : VIA_BEDROCK_ROUTE_PROTOCOL;
    }

    public static int adjacentProtocol(final int protocolVersion, final boolean serverIsNewer) {
        if (serverIsNewer && protocolVersion == VIA_BEDROCK_ROUTE_PROTOCOL) {
            return CURRENT_PROTOCOL;
        }
        if (!serverIsNewer && protocolVersion == CURRENT_PROTOCOL) {
            return VIA_BEDROCK_ROUTE_PROTOCOL;
        }
        return UNKNOWN_PROTOCOL;
    }

    public static boolean isSupported(final int protocolVersion) {
        return protocolVersion == VIA_BEDROCK_ROUTE_PROTOCOL || protocolVersion == CURRENT_PROTOCOL;
    }

}
