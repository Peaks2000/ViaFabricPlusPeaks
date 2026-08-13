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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class BedrockWorldDiscoveryTest {

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
        assertEquals("message-id", target.address());
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
        advertisement.writeByte(4);
        writeString(advertisement, "Host");
        writeString(advertisement, "Survival World");
        advertisement.writeByte(0);
        advertisement.writeIntLE(2);
        advertisement.writeIntLE(8);
        advertisement.writeBoolean(false);
        advertisement.writeBoolean(true);
        advertisement.writeByte(0);
        advertisement.writeByte(0);

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
            assertEquals(sender, world.connection().discoveryAddress());
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
        buffer.writeByte(bytes.length);
        buffer.writeBytes(bytes);
    }

}
