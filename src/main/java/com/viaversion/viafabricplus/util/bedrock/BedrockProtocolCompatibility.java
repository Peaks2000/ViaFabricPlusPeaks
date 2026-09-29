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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.concurrent.atomic.AtomicInteger;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

/** Selects the verified wire codec independently of ViaVersion's route identity. */
public final class BedrockProtocolCompatibility {

    private static final Logger LOGGER = LogManager.getLogger("ViaFabricPlus/Bedrock");

    public static final int UNKNOWN_PROTOCOL = -1;
    public static final int VIA_BEDROCK_ROUTE_PROTOCOL = 2193;
    public static final int CURRENT_PROTOCOL = 2193;
    public static final String CURRENT_GAME_VERSION = "1.26.52";

    private static final AtomicInteger NEXT_CONNECTION_PROTOCOL = new AtomicInteger(UNKNOWN_PROTOCOL);

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
        // 26.51 and the 26.52 hotfix use the same 2193 schema. Match complete
        // dotted version components, never 26.520 or unknown preview releases.
        return normalized.matches("26\\.(51|52)(\\.[0-9]+)*") ? CURRENT_PROTOCOL : UNKNOWN_PROTOCOL;
    }

    public static void prepareConnection(final int protocolVersion) {
        NEXT_CONNECTION_PROTOCOL.set(initialProtocol(protocolVersion));
    }

    /**
     * Keeps the normal multiplayer menu on compatibility ViaBedrock while the dedicated LAN/friends
     * menu opts into the maintained route by carrying its wire protocol on the server entry.
     * Keeping that identity on the entry is important because Minecraft's reconnect button starts
     * a fresh connection after the one-shot handshake state has already been consumed.
     */
    public static ProtocolVersion routeForConnection(final ProtocolVersion requestedVersion, final int maintainedWireProtocol) {
        if (!BedrockProtocolVersion.BEDROCK_LATEST.equals(requestedVersion)) {
            return requestedVersion;
        }
        if (isSupported(maintainedWireProtocol)) {
            prepareConnection(maintainedWireProtocol);
            LOGGER.info(
                "Selected maintained Bedrock route for LAN/friends menu: {} (wire protocol {})",
                requestedVersion.getName(), maintainedWireProtocol
            );
            return requestedVersion;
        }
        NEXT_CONNECTION_PROTOCOL.set(UNKNOWN_PROTOCOL);
        final ProtocolVersion compatibilityVersion = CompatibilityViaBedrockRuntime.compatibilityVersion();
        LOGGER.info("Selected compatibility Bedrock route for normal server menu: {}", compatibilityVersion.getName());
        return compatibilityVersion;
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
        // No second compatible codec is verified for this baseline.
        return UNKNOWN_PROTOCOL;
    }

    public static boolean isSupported(final int protocolVersion) {
        return protocolVersion == CURRENT_PROTOCOL;
    }

}
