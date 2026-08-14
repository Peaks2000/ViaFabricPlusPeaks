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

package com.viaversion.viafabricplus.util.bedrock;

import java.net.InetSocketAddress;

public record BedrockWorld(
    String name,
    String owner,
    Source source,
    String version,
    int protocolVersion,
    String gameMode,
    int playerCount,
    int maxPlayerCount,
    Connection connection
) {

    public BedrockWorld withConnection(final Connection connection) {
        return new BedrockWorld(this.name, this.owner, this.source, this.version, this.protocolVersion, this.gameMode, this.playerCount, this.maxPlayerCount, connection);
    }

    public boolean useBedrockAccount() {
        // Modern client-hosted LAN worlds use self-signed identities over
        // NetherNet. A RakNet advertisement, however, is a normal Bedrock
        // server (including Geyser) and generally expects Xbox authentication.
        return this.source != Source.LAN || this.connection.type() == Connection.Type.RAKNET;
    }

    public enum Source {
        XBOX_FRIEND,
        LAN
    }

    public record Connection(Type type, String address, String signalingId, InetSocketAddress discoveryAddress, String xboxSessionName, String clientHostedNonce) {

        public static Connection rakNet(final String address) {
            return new Connection(Type.RAKNET, address, null, null, null, null);
        }

        public static Connection netherNet(final String networkId) {
            return new Connection(Type.NETHERNET, networkId, null, null, null, null);
        }

        public static Connection netherNetJsonRpc(final String networkId, final String signalingId) {
            return new Connection(Type.NETHERNET_JSON_RPC, networkId, signalingId, null, null, null);
        }

        public static Connection discovery(final InetSocketAddress address) {
            return new Connection(Type.NETHERNET_DISCOVERY, null, null, address, null, null);
        }

        public Connection withXboxSession(final String sessionName) {
            return new Connection(this.type, this.address, this.signalingId, this.discoveryAddress, sessionName, this.clientHostedNonce);
        }

        public Connection withClientHostedNonce(final String nonce) {
            return new Connection(this.type, this.address, this.signalingId, this.discoveryAddress, this.xboxSessionName, nonce);
        }

        public enum Type {
            RAKNET,
            NETHERNET,
            NETHERNET_JSON_RPC,
            NETHERNET_DISCOVERY
        }

    }

}
