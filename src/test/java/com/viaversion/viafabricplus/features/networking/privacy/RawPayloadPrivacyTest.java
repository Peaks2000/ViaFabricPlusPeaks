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

package com.viaversion.viafabricplus.features.networking.privacy;

import com.viaversion.viafabricplus.protocoltranslator.protocol.ViaFabricPlusProtocol;
import com.viaversion.viafabricplus.settings.impl.PrivacySettings;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v1_21_7to1_21_9.packet.ServerboundConfigurationPackets1_21_9;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ServerboundPackets26_3;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RawPayloadPrivacyTest {
    @AfterEach
    void resetPrivacy() {
        PrivacySettings.INSTANCE.privacyMode.setActive(true);
    }

    @Test
    void filtersRawPayloadsInBothProtocolStates() throws Exception {
        Class.forName("com.viaversion.viaversion.api.minecraft.data.StructuredDataKey");
        final var protocol = new ViaFabricPlusProtocol();
        // Register the real handlers without bootstrapping ViaVersion's global platform.
        final var registerPackets = ViaFabricPlusProtocol.class.getDeclaredMethod("registerPackets");
        registerPackets.setAccessible(true);
        registerPackets.invoke(protocol);
        final var mappingsField = AbstractProtocol.class.getDeclaredField("serverboundMappings");
        mappingsField.setAccessible(true);
        final var mappings = (PacketMappings) mappingsField.get(protocol);
        final ProtocolInfo info = (ProtocolInfo) Proxy.newProxyInstance(ProtocolInfo.class.getClassLoader(),
            new Class<?>[]{ProtocolInfo.class}, (proxy, method, args) -> ProtocolVersion.v26_3);
        final UserConnection user = (UserConnection) Proxy.newProxyInstance(UserConnection.class.getClassLoader(),
            new Class<?>[]{UserConnection.class}, (proxy, method, args) -> info);
        for (State state : List.of(State.CONFIGURATION, State.PLAY)) {
            for (boolean privacy : new boolean[]{true, false}) {
                PrivacySettings.INSTANCE.privacyMode.setActive(privacy);
                for (String name : List.of("minecraft:brand", "minecraft:register", "minecraft:unregister", "c:version", "c:register", "example:gameplay")) {
                    final var input = Unpooled.buffer();
                    try {
                        Types.STRING.write(input, name);
                        input.writeBytes(new byte[]{11, 22, 33});
                        final int id = state == State.PLAY ? ServerboundPackets26_3.CUSTOM_PAYLOAD.getId()
                            : ServerboundConfigurationPackets1_21_9.CUSTOM_PAYLOAD.getId();
                        final var packet = new PacketWrapperImpl(id, input, user);
                        mappings.mappedPacket(state, id).handler().handle(packet);
                        if (privacy && !name.equals("example:gameplay")) {
                            assertTrue(packet.isCancelled(), name);
                        } else {
                            assertFalse(packet.isCancelled(), name);
                            assertEquals(name, packet.get(Types.STRING, 0));
                            assertArrayEquals(new byte[]{11, 22, 33}, packet.read(Types.REMAINING_BYTES));
                        }
                    } finally {
                        input.release();
                    }
                }
            }
        }
    }

    @Test
    void coversLegacyAnnouncementsAndAllowsUnrelatedChannels() {
        for (String name : List.of("MC|Brand", "REGISTER", "UNREGISTER", "brand", "register", "unregister")) {
            assertTrue(ClientIdentityPrivacy.shouldSuppress(name));
        }
        assertFalse(ClientIdentityPrivacy.shouldSuppress("example:gameplay"));
        assertFalse(ClientIdentityPrivacy.shouldSuppress("minecraft:debug/path"));
    }
}
