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
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;

public final class BedrockWorldDiscovery {

    private static final String MINECRAFT_SCID = "4fc10100-5f7a-4470-899b-280835760c07";
    private static final String MINECRAFT_SESSION_TEMPLATE = "MinecraftLobby";
    private static final int NETHERNET_DISCOVERY_PORT = 7551;
    private static final int RAKNET_DISCOVERY_PORT = 19132;
    private static final byte[] RAKNET_MAGIC = ByteBufUtil.decodeHexDump("00ffff00fefefefefdfdfdfd12345678");
    private static final int XBOX_CONTRACT_VERSION = 107;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration CLIENT_HOSTED_NONCE_TIMEOUT = Duration.ofSeconds(10);
    private static final long CLIENT_HOSTED_NONCE_POLL_MILLIS = 250L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private static final UUID XBOX_CONNECTION_ID = UUID.randomUUID();
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

            final Map<String, BedrockWorld> worlds = new ConcurrentHashMap<>();
            final java.util.function.BiConsumer<Long, ByteBuf> callback = (networkId, data) -> {
                try {
                    final InetSocketAddress sender = lanDiscovery.sender();
                    if (sender != null) {
                        worlds.put("nethernet:" + Long.toUnsignedString(networkId), parseLanAdvertisement(data, sender));
                    }
                } catch (final Throwable ignored) {
                    // Invalid or outdated advertisements are ignored while other hosts remain discoverable.
                } finally {
                    data.release();
                }
            };

            for (final InetSocketAddress broadcast : broadcastAddresses(NETHERNET_DISCOVERY_PORT)) {
                lanDiscovery.sendDiscoveryRequest(broadcast, callback);
            }
            discoverRakNetLanWorlds(worlds);
            return worlds.values().stream().sorted(Comparator.comparing(BedrockWorld::name, String.CASE_INSENSITIVE_ORDER)).toList();
        }
    }

    public static String joinXboxSession(final BedrockAuthManager account, final String sessionName) throws IOException, InterruptedException {
        if (sessionName == null || sessionName.isBlank()) {
            throw new IllegalArgumentException("Xbox session name must not be blank");
        }

        final String xuid = account.getMinecraftMultiplayerToken().getUpToDate().getXuid();
        if (xuid == null || xuid.isBlank()) {
            throw new IOException("Minecraft multiplayer token did not contain an Xbox user ID");
        }
        final JsonObject body = xboxJoinBody(xuid, XBOX_CONNECTION_ID);

        final String authorization = account.getXboxLiveXstsToken().getUpToDate().getAuthorizationHeader();
        final URI sessionUri = xboxSessionUri(sessionName);
        final HttpResponse<String> response = HTTP_CLIENT.send(HttpRequest.newBuilder()
            .uri(sessionUri)
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", authorization)
            .header("x-xbl-contract-version", Integer.toString(XBOX_CONTRACT_VERSION))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Could not join Xbox multiplayer session: HTTP " + response.statusCode() + " - " + summarizeResponse(response.body()));
        }

        String nonce = sessionNonce(parseJsonObject(response.body()), xuid);
        final long deadline = System.nanoTime() + CLIENT_HOSTED_NONCE_TIMEOUT.toNanos();
        while (nonce == null && System.nanoTime() < deadline) {
            Thread.sleep(CLIENT_HOSTED_NONCE_POLL_MILLIS);
            final JsonObject session = sendJson(HttpRequest.newBuilder()
                .uri(sessionUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorization)
                .header("x-xbl-contract-version", Integer.toString(XBOX_CONTRACT_VERSION))
                .GET()
                .build());
            nonce = sessionNonce(session, xuid);
        }
        if (nonce == null) {
            throw new IOException("Xbox multiplayer session did not issue a client-hosted nonce within " + CLIENT_HOSTED_NONCE_TIMEOUT.toSeconds() + " seconds");
        }
        return nonce;
    }

    static String sessionNonce(final JsonObject session, final String xuid) {
        final JsonObject properties = object(session, "properties");
        final JsonObject custom = properties != null ? object(properties, "custom") : null;
        final JsonObject nonces = custom != null ? object(custom, "nonces") : null;
        final JsonElement value = nonces != null ? nonces.get(xuid) : null;
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        final String nonce = value.getAsString();
        return !nonce.isBlank() && nonce.length() <= 1_024 ? nonce : null;
    }

    static JsonObject xboxJoinBody(final String xuid, final UUID connectionId) {
        final JsonObject system = new JsonObject();
        system.addProperty("active", true);
        system.addProperty("connection", connectionId.toString());
        final JsonObject properties = new JsonObject();
        properties.add("system", system);
        final JsonObject constantSystem = new JsonObject();
        constantSystem.addProperty("xuid", xuid);
        constantSystem.addProperty("initialize", true);
        final JsonObject constants = new JsonObject();
        constants.add("system", constantSystem);
        final JsonObject me = new JsonObject();
        me.add("constants", constants);
        me.add("properties", properties);
        final JsonObject members = new JsonObject();
        members.add("me", me);
        final JsonObject body = new JsonObject();
        body.add("members", members);
        return body;
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
        final JsonObject response = sendJson(HttpRequest.newBuilder()
            .uri(xboxSessionUri(sessionName))
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
            BedrockProtocolCompatibility.protocolForGameVersion(string(custom, "version")),
            string(custom, "worldType"),
            players,
            integer(custom, "MaxMemberCount", -1),
            connection.withXboxSession(sessionName)
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
                if (integer(connection, "ConnectionType", 0) == 7) {
                    final String messagingId = string(connection, "PmsgId");
                    return BedrockWorld.Connection.netherNetJsonRpc(networkId, !messagingId.isBlank() ? messagingId : networkId);
                }
                return BedrockWorld.Connection.netherNet(networkId);
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
                BedrockProtocolCompatibility.protocolForNetherNetAdvertisement(advertisementVersion),
                flags,
                playerCount,
                maxPlayerCount,
                BedrockWorld.Connection.discovery(sender)
            );
        } finally {
            decoded.release();
        }
    }

    static BedrockWorld parseRakNetAdvertisement(final byte[] data, final int length, final InetSocketAddress sender) {
        final ByteBuf buffer = Unpooled.wrappedBuffer(data, 0, length);
        try {
            if (buffer.readableBytes() < 35 || buffer.readUnsignedByte() != 0x1C) {
                throw new IllegalArgumentException("Not a RakNet unconnected pong");
            }
            buffer.skipBytes(Long.BYTES); // Ping timestamp
            buffer.readLong(); // Server GUID
            final byte[] magic = new byte[RAKNET_MAGIC.length];
            buffer.readBytes(magic);
            if (!java.util.Arrays.equals(magic, RAKNET_MAGIC)) {
                throw new IllegalArgumentException("Invalid RakNet magic");
            }

            final int motdLength = buffer.readUnsignedShort();
            if (motdLength <= 0 || buffer.readableBytes() < motdLength) {
                throw new IllegalArgumentException("Invalid RakNet advertisement length");
            }
            final String[] fields = buffer.readCharSequence(motdLength, StandardCharsets.UTF_8).toString().split(";", -1);
            if (fields.length < 6 || !"MCPE".equals(fields[0])) {
                throw new IllegalArgumentException("Invalid Bedrock advertisement");
            }

            final String serverName = fields[1];
            final String levelName = fields.length > 7 ? fields[7] : "";
            final String gameMode = fields.length > 8 ? fields[8] : "";
            final int port = fields.length > 10 ? integer(fields[10], sender.getPort()) : sender.getPort();
            final InetSocketAddress address = new InetSocketAddress(sender.getAddress(), port > 0 && port <= 65_535 ? port : sender.getPort());
            return new BedrockWorld(
                !levelName.isBlank() ? levelName : serverName,
                serverName,
                BedrockWorld.Source.LAN,
                fields.length > 3 ? fields[3] : "",
                fields.length > 2 ? integer(fields[2], BedrockProtocolCompatibility.UNKNOWN_PROTOCOL) : BedrockProtocolCompatibility.UNKNOWN_PROTOCOL,
                gameMode,
                integer(fields[4], -1),
                integer(fields[5], -1),
                BedrockWorld.Connection.rakNet(formatAddress(address.getAddress().getHostAddress(), address.getPort()))
            );
        } finally {
            buffer.release();
        }
    }

    private static void discoverRakNetLanWorlds(final Map<String, BedrockWorld> worlds) throws InterruptedException {
        final ByteBuf ping = Unpooled.buffer(33);
        final byte[] pingData;
        try {
            ping.writeByte(0x01);
            ping.writeLong(System.currentTimeMillis());
            ping.writeBytes(RAKNET_MAGIC);
            ping.writeLong(ThreadLocalRandom.current().nextLong());
            pingData = ByteBufUtil.getBytes(ping);
        } finally {
            ping.release();
        }

        try (final DatagramSocket socket = new DatagramSocket(null)) {
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(0));
            for (final InetSocketAddress broadcast : broadcastAddresses(RAKNET_DISCOVERY_PORT)) {
                try {
                    socket.send(new java.net.DatagramPacket(pingData, pingData.length, broadcast));
                } catch (final IOException ignored) {
                    // Continue probing the remaining network interfaces.
                }
            }

            final long deadline = System.nanoTime() + 2_500_000_000L;
            final byte[] responseData = new byte[65_535];
            while (System.nanoTime() < deadline) {
                socket.setSoTimeout((int) Math.min(250L, Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L)));
                final java.net.DatagramPacket response = new java.net.DatagramPacket(responseData, responseData.length);
                try {
                    socket.receive(response);
                    final InetSocketAddress sender = (InetSocketAddress) response.getSocketAddress();
                    final BedrockWorld world = parseRakNetAdvertisement(response.getData(), response.getLength(), sender);
                    worlds.put("raknet:" + world.connection().address(), world);
                } catch (final SocketTimeoutException ignored) {
                } catch (final IllegalArgumentException ignored) {
                    // Other UDP services and invalid advertisements are ignored.
                }
            }
        } catch (final IOException ignored) {
            // NetherNet LAN results remain available if RakNet discovery fails.
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
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

    private static List<InetSocketAddress> broadcastAddresses(final int port) {
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
        return broadcasts.stream().map(address -> new InetSocketAddress(address, port)).toList();
    }

    private static URI xboxSessionUri(final String sessionName) {
        final String encodedSession = URLEncoder.encode(sessionName, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create("https://sessiondirectory.xboxlive.com/serviceconfigs/" + MINECRAFT_SCID + "/sessionTemplates/" + MINECRAFT_SESSION_TEMPLATE + "/sessions/" + encodedSession);
    }

    private static JsonObject sendJson(final HttpRequest request) throws IOException, InterruptedException {
        final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Xbox Live request failed with HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonObject parseJsonObject(final String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            final JsonElement value = JsonParser.parseString(body);
            return value.isJsonObject() ? value.getAsJsonObject() : null;
        } catch (final RuntimeException ignored) {
            return null;
        }
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

    private static int integer(final String value, final int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String summarizeResponse(final String body) {
        if (body == null || body.isBlank()) {
            return "empty response";
        }
        final String summary = body.replaceAll("\\s+", " ").trim();
        return summary.length() <= 1_000 ? summary : summary.substring(0, 1_000) + "...";
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
