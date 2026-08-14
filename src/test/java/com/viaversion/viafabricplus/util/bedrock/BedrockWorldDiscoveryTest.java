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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BedrockWorldDiscoveryTest {

    @Test
    public void selectsAuthenticationByWorldSource() {
        final BedrockWorld.Connection netherNet = BedrockWorld.Connection.discovery(new InetSocketAddress("192.168.1.30", 7551));
        final BedrockWorld.Connection rakNet = BedrockWorld.Connection.rakNet("192.168.1.30:19132");
        final BedrockWorld clientHostedLan = new BedrockWorld("LAN", "Host", BedrockWorld.Source.LAN, "26.40", 2168, "Survival", 1, 8, netherNet);
        final BedrockWorld rakNetLan = new BedrockWorld("Server", "Host", BedrockWorld.Source.LAN, "26.40", 2168, "Survival", 1, 8, rakNet);
        final BedrockWorld friend = new BedrockWorld("Friend", "Host", BedrockWorld.Source.XBOX_FRIEND, "26.40", 2168, "Survival", 1, 8, netherNet);

        assertFalse(clientHostedLan.useBedrockAccount());
        assertTrue(rakNetLan.useBedrockAccount());
        assertTrue(friend.useBedrockAccount());
    }

    @Test
    public void selectsWireProtocolFromAdvertisement() {
        assertEquals(-1, BedrockProtocolCompatibility.protocolForNetherNetAdvertisement(4));
        assertEquals(-1, BedrockProtocolCompatibility.protocolForNetherNetAdvertisement(5));
        assertEquals(2169, BedrockProtocolCompatibility.protocolForGameVersion("1.26.50"));
        assertEquals(2168, BedrockProtocolCompatibility.protocolForGameVersion("26.40"));
        assertEquals(2168, BedrockProtocolCompatibility.initialProtocol(-1));
        assertEquals(2169, BedrockProtocolCompatibility.adjacentProtocol(2168, true));
        assertEquals(2168, BedrockProtocolCompatibility.adjacentProtocol(2169, false));
        assertEquals(-1, BedrockProtocolCompatibility.adjacentProtocol(2168, false));
    }

    @Test
    public void maintainedRouteCanBeSelectedForEveryReconnect() {
        assertEquals(
            BedrockProtocolVersion.bedrockLatest,
            BedrockProtocolCompatibility.routeForConnection(BedrockProtocolVersion.bedrockLatest, 2168)
        );
        assertEquals(2168, BedrockProtocolCompatibility.consumeConnectionProtocol(2167));

        assertEquals(
            BedrockProtocolVersion.bedrockLatest,
            BedrockProtocolCompatibility.routeForConnection(BedrockProtocolVersion.bedrockLatest, 2168)
        );
        assertEquals(2168, BedrockProtocolCompatibility.consumeConnectionProtocol(2167));
    }

    @Test
    public void includesConnectionGuidWhenJoiningXboxSession() {
        final UUID connectionId = UUID.fromString("21bc42d5-fce0-4fba-a88f-a34e770d5339");
        final JsonObject body = BedrockWorldDiscovery.xboxJoinBody("123456789", connectionId);
        final JsonObject me = body.getAsJsonObject("members").getAsJsonObject("me");
        assertEquals("123456789", me.getAsJsonObject("constants").getAsJsonObject("system").get("xuid").getAsString());
        assertEquals(connectionId.toString(), me.getAsJsonObject("properties").getAsJsonObject("system").get("connection").getAsString());
        assertEquals(true, me.getAsJsonObject("properties").getAsJsonObject("system").get("active").getAsBoolean());
    }

    @Test
    public void resolvesClientHostedNonceForJoinedXuid() {
        final JsonObject nonces = new JsonObject();
        nonces.addProperty("123456789", "0123456789abcdef");
        final JsonObject custom = new JsonObject();
        custom.add("nonces", nonces);
        final JsonObject properties = new JsonObject();
        properties.add("custom", custom);
        final JsonObject session = new JsonObject();
        session.add("properties", properties);

        assertEquals("0123456789abcdef", BedrockWorldDiscovery.sessionNonce(session, "123456789"));
        assertNull(BedrockWorldDiscovery.sessionNonce(session, "987654321"));
    }

    @Test
    public void rejectsMalformedClientHostedNonce() {
        final JsonObject nonces = new JsonObject();
        nonces.addProperty("numeric", 1234);
        nonces.addProperty("blank", "   ");
        nonces.addProperty("oversized", "x".repeat(1_025));
        final JsonObject custom = new JsonObject();
        custom.add("nonces", nonces);
        final JsonObject properties = new JsonObject();
        properties.add("custom", custom);
        final JsonObject session = new JsonObject();
        session.add("properties", properties);

        assertNull(BedrockWorldDiscovery.sessionNonce(session, "numeric"));
        assertNull(BedrockWorldDiscovery.sessionNonce(session, "blank"));
        assertNull(BedrockWorldDiscovery.sessionNonce(session, "oversized"));
        assertNull(BedrockWorldDiscovery.sessionNonce(new JsonObject(), "missing"));
    }

    @Test
    public void parsesNetherNetJsonRpcConnection() {
        final JsonObject connection = new JsonObject();
        connection.addProperty("ConnectionType", 7);
        connection.addProperty("NetherNetId", "18446744073709551615");
        connection.addProperty("PmsgId", "message-id");
        final JsonArray connections = new JsonArray();
        connections.add(connection);

        final BedrockWorld.Connection target = BedrockWorldDiscovery.findConnection(connections);
        assertEquals(BedrockWorld.Connection.Type.NETHERNET_JSON_RPC, target.type());
        assertEquals("18446744073709551615", target.address());
        assertEquals("message-id", target.signalingId());
    }

    @Test
    public void fallsBackToNetworkIdForJsonRpcConnection() {
        final JsonObject connection = new JsonObject();
        connection.addProperty("ConnectionType", 7);
        connection.addProperty("NetherNetId", "18446744073709551615");
        final JsonArray connections = new JsonArray();
        connections.add(connection);

        final BedrockWorld.Connection target = BedrockWorldDiscovery.findConnection(connections);
        assertEquals(BedrockWorld.Connection.Type.NETHERNET_JSON_RPC, target.type());
        assertEquals("18446744073709551615", target.address());
        assertEquals("18446744073709551615", target.signalingId());
    }

    @Test
    public void prefersPublicRakNetConnection() {
        final JsonArray connections = new JsonArray();
        connections.add(directConnection("192.168.1.20", 19132));
        connections.add(directConnection("203.0.113.10", 19133));

        final BedrockWorld.Connection target = BedrockWorldDiscovery.findConnection(connections);
        assertEquals(BedrockWorld.Connection.Type.RAKNET, target.type());
        assertEquals("203.0.113.10:19133", target.address());
    }

    @Test
    public void parsesLanAdvertisement() {
        final ByteBuf advertisement = Unpooled.buffer();
        advertisement.writeByte(6);
        writeString(advertisement, "Host");
        writeString(advertisement, "Survival World");
        writeSignedVarInt(advertisement, 0);
        advertisement.writeIntLE(2);
        advertisement.writeIntLE(8);
        advertisement.writeBoolean(false);
        advertisement.writeBoolean(true);
        advertisement.writeBoolean(true);
        advertisement.writeBoolean(true);
        writeString(advertisement, "0123456789abcdef");
        writeSignedVarInt(advertisement, 2);
        writeSignedVarInt(advertisement, 4);

        final String hex = ByteBufUtil.hexDump(advertisement);
        advertisement.release();
        final ByteBuf response = Unpooled.buffer();
        response.writeIntLE(hex.length());
        response.writeCharSequence(hex, StandardCharsets.UTF_8);
        final InetSocketAddress sender = new InetSocketAddress("192.168.1.30", 7551);
        try {
            final BedrockWorld world = BedrockWorldDiscovery.parseLanAdvertisement(response, sender);
            assertEquals("Survival World", world.name());
            assertEquals("Host", world.owner());
            assertEquals("Hardcore", world.gameMode());
            assertEquals(2, world.playerCount());
            assertEquals(8, world.maxPlayerCount());
            assertEquals(BedrockProtocolCompatibility.UNKNOWN_PROTOCOL, world.protocolVersion());
            assertEquals(sender, world.connection().discoveryAddress());
            assertEquals("0123456789abcdef", world.connection().clientHostedNonce());
        } finally {
            response.release();
        }
    }

    @Test
    public void parsesLegacyLanAdvertisementWithoutNonce() {
        final ByteBuf advertisement = Unpooled.buffer();
        advertisement.writeByte(4);
        writeString(advertisement, "Host");
        writeString(advertisement, "Legacy World");
        writeSignedVarInt(advertisement, 1);
        advertisement.writeIntLE(1);
        advertisement.writeIntLE(8);
        advertisement.writeBoolean(false);
        advertisement.writeBoolean(false);
        advertisement.writeBoolean(true);
        advertisement.writeBoolean(true);

        final ByteBuf response = wrapLanAdvertisement(advertisement);
        try {
            final BedrockWorld world = BedrockWorldDiscovery.parseLanAdvertisement(response, new InetSocketAddress("192.168.1.30", 7551));
            assertEquals("Creative", world.gameMode());
            assertNull(world.connection().clientHostedNonce());
        } finally {
            response.release();
        }
    }

    @Test
    public void parsesRakNetLanAdvertisement() {
        final String motd = "MCPE;Cool Server;2168;26.40;2;20;4041827097780214558;Survival World;Survival;1;19132;19133;";
        final byte[] motdBytes = motd.getBytes(StandardCharsets.UTF_8);
        final ByteBuf response = Unpooled.buffer();
        response.writeByte(0x1C);
        response.writeLong(1234L);
        response.writeLong(4041827097780214558L);
        response.writeBytes(ByteBufUtil.decodeHexDump("00ffff00fefefefefdfdfdfd12345678"));
        response.writeShort(motdBytes.length);
        response.writeBytes(motdBytes);
        final byte[] data = ByteBufUtil.getBytes(response);
        response.release();

        final BedrockWorld world = BedrockWorldDiscovery.parseRakNetAdvertisement(data, data.length, new InetSocketAddress("192.168.1.30", 19132));
        assertEquals("Survival World", world.name());
        assertEquals("Cool Server", world.owner());
        assertEquals("26.40", world.version());
        assertEquals(2168, world.protocolVersion());
        assertEquals("Survival", world.gameMode());
        assertEquals(2, world.playerCount());
        assertEquals(20, world.maxPlayerCount());
        assertEquals(BedrockWorld.Connection.Type.RAKNET, world.connection().type());
        assertEquals("192.168.1.30:19132", world.connection().address());
    }

    private static JsonObject directConnection(final String host, final int port) {
        final JsonObject connection = new JsonObject();
        connection.addProperty("HostIpAddress", host);
        connection.addProperty("HostPort", port);
        return connection;
    }

    private static void writeString(final ByteBuf buffer, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeUnsignedVarInt(buffer, bytes.length);
        buffer.writeBytes(bytes);
    }

    private static void writeSignedVarInt(final ByteBuf buffer, final int value) {
        writeUnsignedVarInt(buffer, (value << 1) ^ (value >> 31));
    }

    private static void writeUnsignedVarInt(final ByteBuf buffer, int value) {
        while ((value & ~0x7F) != 0) {
            buffer.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buffer.writeByte(value);
    }

    private static ByteBuf wrapLanAdvertisement(final ByteBuf advertisement) {
        final String hex = ByteBufUtil.hexDump(advertisement);
        advertisement.release();
        final ByteBuf response = Unpooled.buffer();
        response.writeIntLE(hex.length());
        response.writeCharSequence(hex, StandardCharsets.UTF_8);
        return response;
    }

}
