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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetDiscovery;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;

public final class BedrockWorldDiscovery {

    private static final String MINECRAFT_SCID = "4fc10100-5f7a-4470-899b-280835760c07";
    private static final String MINECRAFT_SESSION_TEMPLATE = "MinecraftLobby";
    private static final int NETHERNET_DISCOVERY_PORT = 7551;
    private static final int XBOX_CONTRACT_VERSION = 107;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private static final Object LAN_LOCK = new Object();

    private static AddressAwareNetherNetDiscovery lanDiscovery;

    public static List<BedrockWorld> discoverXboxFriends(final BedrockAuthManager account) throws IOException, InterruptedException {
        final XblXstsToken token = account.getXboxLiveXstsToken().getUpToDate();
        final String authorization = token.getAuthorizationHeader();
        final Map<String, String> people = getXboxPeople(authorization);
        if (people.isEmpty()) {
            return List.of();
        }

        final List<BedrockWorld> worlds = new ArrayList<>();
        final Set<String> sessions = new HashSet<>();
        final List<String> xuids = new ArrayList<>(people.keySet());
        for (int offset = 0; offset < xuids.size(); offset += 100) {
            final List<String> batch = xuids.subList(offset, Math.min(offset + 100, xuids.size()));
            final JsonObject requestBody = new JsonObject();
            requestBody.addProperty("type", "activity");
            requestBody.addProperty("scid", MINECRAFT_SCID);
            final JsonArray ownerXuids = new JsonArray();
            batch.forEach(ownerXuids::add);
            final JsonObject owners = new JsonObject();
            owners.add("xuids", ownerXuids);
            requestBody.add("owners", owners);

            final JsonObject handles = sendJson(HttpRequest.newBuilder()
                .uri(URI.create("https://sessiondirectory.xboxlive.com/handles/query?include=relatedInfo"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorization)
                .header("x-xbl-contract-version", Integer.toString(XBOX_CONTRACT_VERSION))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build());

            for (final JsonElement resultElement : array(handles, "results")) {
                final JsonObject result = resultElement.getAsJsonObject();
                final JsonObject sessionRef = object(result, "sessionRef");
                if (sessionRef == null || !MINECRAFT_SCID.equalsIgnoreCase(string(sessionRef, "scid"))) {
                    continue;
                }
                final String template = string(sessionRef, "templateName");
                final String sessionName = string(sessionRef, "name");
                if (!MINECRAFT_SESSION_TEMPLATE.equalsIgnoreCase(template) || sessionName.isBlank() || !sessions.add(sessionName)) {
                    continue;
                }

                final String ownerXuid = string(result, "ownerXuid");
                try {
                    final BedrockWorld world = getXboxSession(authorization, sessionName, people.getOrDefault(ownerXuid, ownerXuid));
                    if (world != null) {
                        worlds.add(world);
                    }
                } catch (final IOException ignored) {
                    // Activity handles can briefly outlive their sessions. Keep loading the remaining worlds.
                }
            }
        }
        worlds.sort(Comparator.comparing(BedrockWorld::owner, String.CASE_INSENSITIVE_ORDER));
        return worlds;
    }

    public static List<BedrockWorld> discoverLanWorlds() throws InterruptedException {
        synchronized (LAN_LOCK) {
            if (lanDiscovery == null || !lanDiscovery.isActive()) {
                lanDiscovery = new AddressAwareNetherNetDiscovery(ThreadLocalRandom.current().nextLong());
                lanDiscovery.bind(new InetSocketAddress(0));
            }

            final Map<Long, BedrockWorld> worlds = new ConcurrentHashMap<>();
            final java.util.function.BiConsumer<Long, ByteBuf> callback = (networkId, data) -> {
                try {
                    final InetSocketAddress sender = lanDiscovery.sender();
                    if (sender != null) {
                        worlds.put(networkId, parseLanAdvertisement(data, sender));
                    }
                } catch (final Throwable ignored) {
                    // Invalid or outdated advertisements are ignored while other hosts remain discoverable.
                } finally {
                    data.release();
                }
            };

            for (final InetSocketAddress broadcast : broadcastAddresses()) {
                lanDiscovery.sendDiscoveryRequest(broadcast, callback);
            }
            Thread.sleep(2_500L);
            return worlds.values().stream().sorted(Comparator.comparing(BedrockWorld::name, String.CASE_INSENSITIVE_ORDER)).toList();
        }
    }

    private static Map<String, String> getXboxPeople(final String authorization) throws IOException, InterruptedException {
        final JsonObject response = sendJson(HttpRequest.newBuilder()
            .uri(URI.create("https://peoplehub.xboxlive.com/users/me/people/social"))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", authorization)
            .header("x-xbl-contract-version", "5")
            .header("Accept-Language", "en-GB")
            .GET()
            .build());
        final Map<String, String> people = new HashMap<>();
        for (final JsonElement personElement : array(response, "people")) {
            final JsonObject person = personElement.getAsJsonObject();
            final String xuid = string(person, "xuid");
            if (!xuid.isBlank()) {
                final String modernGamertag = string(person, "modernGamertag");
                final String gamertag = string(person, "gamertag");
                people.put(xuid, !modernGamertag.isBlank() ? modernGamertag : gamertag);
            }
        }
        return people;
    }

    private static BedrockWorld getXboxSession(final String authorization, final String sessionName, final String fallbackOwner) throws IOException, InterruptedException {
        final String encodedSession = URLEncoder.encode(sessionName, StandardCharsets.UTF_8).replace("+", "%20");
        final JsonObject response = sendJson(HttpRequest.newBuilder()
            .uri(URI.create("https://sessiondirectory.xboxlive.com/serviceconfigs/" + MINECRAFT_SCID + "/sessionTemplates/" + MINECRAFT_SESSION_TEMPLATE + "/sessions/" + encodedSession))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", authorization)
            .header("x-xbl-contract-version", Integer.toString(XBOX_CONTRACT_VERSION))
            .GET()
            .build());
        final JsonObject properties = object(response, "properties");
        final JsonObject custom = properties != null ? object(properties, "custom") : null;
        if (custom == null) {
            return null;
        }

        final BedrockWorld.Connection connection = findConnection(array(custom, "SupportedConnections"));
        if (connection == null) {
            return null;
        }
        final String hostName = string(custom, "hostName");
        final String worldName = string(custom, "worldName");
        final String owner = !hostName.isBlank() ? hostName : fallbackOwner;
        final String name = !worldName.isBlank() ? worldName : owner + "'s world";
        final JsonObject membersInfo = object(response, "membersInfo");
        final int players = integer(custom, "MemberCount", membersInfo != null ? integer(membersInfo, "count", -1) : -1);
        return new BedrockWorld(
            name,
            owner,
            BedrockWorld.Source.XBOX_FRIEND,
            string(custom, "version"),
            string(custom, "worldType"),
            players,
            integer(custom, "MaxMemberCount", -1),
            connection
        );
    }

    static BedrockWorld.Connection findConnection(final JsonArray connections) {
        BedrockWorld.Connection publicRakNet = null;
        BedrockWorld.Connection fallbackRakNet = null;
        for (final JsonElement connectionElement : connections) {
            final JsonObject connection = connectionElement.getAsJsonObject();
            String networkId = string(connection, "NetherNetId");
            if (networkId.isBlank()) {
                networkId = string(connection, "WebRTCNetworkId");
            }
            if (!networkId.isBlank()) {
                return integer(connection, "ConnectionType", 0) == 7
                    ? BedrockWorld.Connection.netherNetJsonRpc(networkId)
                    : BedrockWorld.Connection.netherNet(networkId);
            }

            final String host = string(connection, "HostIpAddress");
            final int port = integer(connection, "HostPort", 0);
            if (!host.isBlank() && port > 0 && port <= 65_535) {
                final BedrockWorld.Connection direct = BedrockWorld.Connection.rakNet(formatAddress(host, port));
                fallbackRakNet = direct;
                if (!isPrivateAddress(host)) {
                    publicRakNet = direct;
                }
            }
        }
        return publicRakNet != null ? publicRakNet : fallbackRakNet;
    }

    static BedrockWorld parseLanAdvertisement(final ByteBuf data, final InetSocketAddress sender) {
        if (data.readableBytes() < Integer.BYTES) {
            throw new IllegalArgumentException("Advertisement is too short");
        }
        final int hexLength = data.readIntLE();
        if (hexLength <= 0 || data.readableBytes() < hexLength) {
            throw new IllegalArgumentException("Invalid advertisement length");
        }
        final String hex = data.readCharSequence(hexLength, StandardCharsets.UTF_8).toString();
        final ByteBuf decoded = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(hex));
        try {
            final int advertisementVersion = decoded.readUnsignedByte();
            final String serverName = readString(decoded);
            final String levelName = readString(decoded);
            final int gameMode = decoded.readUnsignedByte() >> 1;
            final int playerCount = decoded.readIntLE();
            final int maxPlayerCount = decoded.readIntLE();
            final boolean editor = decoded.readBoolean();
            final boolean hardcore = decoded.readBoolean();
            final String gameModeName = switch (gameMode) {
                case 0 -> "Survival";
                case 1 -> "Creative";
                case 2 -> "Adventure";
                case 3 -> "Spectator";
                default -> "Game mode " + gameMode;
            };
            final String flags = editor ? "Editor" : hardcore ? "Hardcore" : gameModeName;
            return new BedrockWorld(
                !levelName.isBlank() ? levelName : serverName,
                serverName,
                BedrockWorld.Source.LAN,
                "NetherNet " + advertisementVersion,
                flags,
                playerCount,
                maxPlayerCount,
                BedrockWorld.Connection.discovery(sender)
            );
        } finally {
            decoded.release();
        }
    }

    private static String readString(final ByteBuf buffer) {
        if (!buffer.isReadable()) {
            return "";
        }
        final int length = buffer.readUnsignedByte();
        if (buffer.readableBytes() < length) {
            throw new IllegalArgumentException("Invalid string length");
        }
        return buffer.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static List<InetSocketAddress> broadcastAddresses() {
        final Set<InetAddress> broadcasts = new HashSet<>();
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                final NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (final InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    if (interfaceAddress.getBroadcast() != null) {
                        broadcasts.add(interfaceAddress.getBroadcast());
                    }
                }
            }
        } catch (final IOException ignored) {
        }
        try {
            broadcasts.add(InetAddress.getByName("255.255.255.255"));
        } catch (final IOException ignored) {
        }
        return broadcasts.stream().map(address -> new InetSocketAddress(address, NETHERNET_DISCOVERY_PORT)).toList();
    }

    private static JsonObject sendJson(final HttpRequest request) throws IOException, InterruptedException {
        final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Xbox Live request failed with HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonArray array(final JsonObject object, final String name) {
        final JsonElement value = object != null ? object.get(name) : null;
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject object(final JsonObject object, final String name) {
        final JsonElement value = object != null ? object.get(name) : null;
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(final JsonObject object, final String name) {
        final JsonElement value = object != null ? object.get(name) : null;
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static int integer(final JsonObject object, final String name, final int fallback) {
        final JsonElement value = object != null ? object.get(name) : null;
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (final NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String formatAddress(final String host, final int port) {
        return host.indexOf(':') >= 0 ? "[" + host + "]:" + port : host + ":" + port;
    }

    private static boolean isPrivateAddress(final String host) {
        try {
            final InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (final IOException ignored) {
            return false;
        }
    }

    private static final class AddressAwareNetherNetDiscovery extends NetherNetDiscovery {

        private final ThreadLocal<InetSocketAddress> sender = new ThreadLocal<>();

        private AddressAwareNetherNetDiscovery(final long networkId) {
            super(networkId);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext context, final DatagramPacket packet) throws Exception {
            this.sender.set(packet.sender());
            try {
                super.channelRead0(context, packet);
            } finally {
                this.sender.remove();
            }
        }

        private InetSocketAddress sender() {
            return this.sender.get();
        }

    }

    private BedrockWorldDiscovery() {
    }

}
