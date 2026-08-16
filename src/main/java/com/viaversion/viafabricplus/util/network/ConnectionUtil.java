/*
 * This file is part of ViaFabricPlus - https://github.com/Peaks2000/ViaFabricPlusPeaks
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

package com.viaversion.viafabricplus.util.network;

import com.viaversion.viafabricplus.injection.access.core.IServerData;
import com.viaversion.viafabricplus.injection.access.core.bedrock.IServerAddress;
import com.viaversion.viafabricplus.util.bedrock.BedrockProtocolCompatibility;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;
import java.net.InetSocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

public final class ConnectionUtil {

    public static void connect(final String address, final ProtocolVersion version) {
        connect(address, address, version);
    }

    public static void connect(final String name, final String address) {
        connect(name, address, null);
    }

    public static void connect(final String name, final String address, final ProtocolVersion version) {
        connect(name, address, version, BedrockProtocolCompatibility.UNKNOWN_PROTOCOL);
    }

    public static void connect(final String name, final String address, final ProtocolVersion version, final int bedrockWireProtocol) {
        connect(name, address, version, bedrockWireProtocol, true);
    }

    public static void connect(final String name, final String address, final ProtocolVersion version, final int bedrockWireProtocol,
                               final boolean useBedrockAccount) {
        connect(name, address, version, bedrockWireProtocol, useBedrockAccount, null);
    }

    public static void connect(final String name, final String address, final ProtocolVersion version, final int bedrockWireProtocol,
                               final boolean useBedrockAccount, final String clientHostedNonce) {
        final ServerAddress serverAddress = ServerAddress.parseString(address);
        final ServerData entry = new ServerData(name, serverAddress.getHost(), ServerData.Type.OTHER);

        if (version != null) {
            ((IServerData) entry).viaFabricPlus$forceVersion(version);
        }
        ((IServerData) entry).viaFabricPlus$setBedrockWireProtocol(bedrockWireProtocol);
        ((IServerData) entry).viaFabricPlus$setUseBedrockAccount(useBedrockAccount);
        ((IServerData) entry).viaFabricPlus$setClientHostedNonce(clientHostedNonce);
        ConnectScreen.startConnecting(Minecraft.getInstance().gui.screen(), Minecraft.getInstance(), serverAddress, entry, false, null);
    }

    public static void connectNetherNet(final NetherNetAddress address) {
        connectNetherNet("Bedrock world " + address.getNetworkId(), address);
    }

    public static void connectNetherNet(final String name, final NetherNetAddress address) {
        connectNetherNet(name, address, BedrockProtocolCompatibility.UNKNOWN_PROTOCOL);
    }

    public static void connectNetherNet(final String name, final NetherNetAddress address, final int bedrockWireProtocol) {
        connectNetherNet(name, address, bedrockWireProtocol, true);
    }

    public static void connectNetherNet(final String name, final NetherNetAddress address, final int bedrockWireProtocol,
                                        final boolean useBedrockAccount) {
        connectNetherNet(name, address, bedrockWireProtocol, useBedrockAccount, null);
    }

    public static void connectNetherNet(final String name, final NetherNetAddress address, final int bedrockWireProtocol,
                                        final boolean useBedrockAccount, final String clientHostedNonce) {
        final ServerAddress serverAddress = ServerAddress.parseString(address.getNetworkId() + ".nethernet.viafabricplus.localhost");
        ((IServerAddress) (Object) serverAddress).viaFabricPlus$setNetherNetAddress(address);

        connectNetherNet(name, serverAddress, bedrockWireProtocol, useBedrockAccount, clientHostedNonce);
    }

    public static void connectNetherNet(final String name, final InetSocketAddress discoveryAddress) {
        connectNetherNet(name, discoveryAddress, BedrockProtocolCompatibility.UNKNOWN_PROTOCOL);
    }

    public static void connectNetherNet(final String name, final InetSocketAddress discoveryAddress, final int bedrockWireProtocol) {
        connectNetherNet(name, discoveryAddress, bedrockWireProtocol, true);
    }

    public static void connectNetherNet(final String name, final InetSocketAddress discoveryAddress, final int bedrockWireProtocol,
                                        final boolean useBedrockAccount) {
        connectNetherNet(name, discoveryAddress, bedrockWireProtocol, useBedrockAccount, null);
    }

    public static void connectNetherNet(final String name, final InetSocketAddress discoveryAddress, final int bedrockWireProtocol,
                                        final boolean useBedrockAccount, final String clientHostedNonce) {
        final ServerAddress serverAddress = ServerAddress.parseString("lan.nethernet.viafabricplus.localhost");
        ((IServerAddress) (Object) serverAddress).viaFabricPlus$setNetherNetDiscoveryAddress(discoveryAddress);

        connectNetherNet(name, serverAddress, bedrockWireProtocol, useBedrockAccount, clientHostedNonce);
    }

    private static void connectNetherNet(final String name, final ServerAddress serverAddress, final int bedrockWireProtocol,
                                         final boolean useBedrockAccount, final String clientHostedNonce) {
        final ServerData entry = new ServerData(name, serverAddress.getHost(), ServerData.Type.OTHER);
        ((IServerData) entry).viaFabricPlus$forceVersion(BedrockProtocolVersion.bedrockLatest);
        ((IServerData) entry).viaFabricPlus$setBedrockWireProtocol(bedrockWireProtocol);
        ((IServerData) entry).viaFabricPlus$setUseBedrockAccount(useBedrockAccount);
        ((IServerData) entry).viaFabricPlus$setClientHostedNonce(clientHostedNonce);

        ConnectScreen.startConnecting(Minecraft.getInstance().gui.screen(), Minecraft.getInstance(), serverAddress, entry, false, null);
    }

}
