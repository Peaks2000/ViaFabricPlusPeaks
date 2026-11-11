/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.viaversion.viafabricplus.features.networking.privacy;

import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.settings.impl.PrivacySettings;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.impl.networking.CommonRegisterPayload;
import net.fabricmc.fabric.impl.networking.CommonVersionPayload;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClientBrandPrivacyTest {

    @AfterEach
    void resetPrivacy() {
        PrivacySettings.INSTANCE.privacyMode.setActive(true);
    }

    @Test
    void suppressesBrandThroughConnectionAndCompletesListener() {
        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel();
        connection.channel = channel;
        try {
            final ServerboundKeepAlivePacket queued = new ServerboundKeepAlivePacket(41L);
            connection.send(queued, null, false);
            assertNull(channel.readOutbound());
            final AtomicBoolean completed = new AtomicBoolean();
            connection.send(new ServerboundCustomPayloadPacket(new BrandPayload("fabric")),
                future -> completed.set(future.isSuccess()), true);
            channel.runPendingTasks();
            assertTrue(completed.get());
            assertSame(queued, channel.readOutbound());
            assertNull(channel.readOutbound());
            final ServerboundKeepAlivePacket keepAlive = new ServerboundKeepAlivePacket(42L);
            connection.send(keepAlive);
            channel.runPendingTasks();
            assertSame(keepAlive, channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void optInSendsOriginalBrandAndSettingSurvivesReload() {
        PrivacySettings.INSTANCE.privacyMode.setActive(false);
        final JsonObject saved = new JsonObject();
        PrivacySettings.INSTANCE.write(saved);
        PrivacySettings.INSTANCE.privacyMode.setActive(true);
        PrivacySettings.INSTANCE.read(saved);
        assertFalse(PrivacySettings.INSTANCE.privacyMode.isActive());

        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel();
        connection.channel = channel;
        try {
            final ServerboundCustomPayloadPacket brand = new ServerboundCustomPayloadPacket(new BrandPayload("fabric"));
            connection.send(brand);
            channel.runPendingTasks();
            assertSame(brand, channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void legacySettingsAlwaysUpgradeWithPrivacyEnabled() {
        for (boolean legacyValue : new boolean[]{true, false}) {
            final JsonObject root = new JsonObject();
            final JsonObject legacy = new JsonObject();
            legacy.addProperty("send_client_brand", legacyValue);
            root.add("privacy", legacy);
            PrivacySettings.INSTANCE.privacyMode.setActive(false);
            PrivacySettings.INSTANCE.read(root);
            assertTrue(PrivacySettings.INSTANCE.privacyMode.isActive());
            PrivacySettings.INSTANCE.write(root);
            assertTrue(root.getAsJsonObject("privacy").get("privacy_mode").getAsBoolean());
            assertFalse(root.getAsJsonObject("privacy").has("send_client_brand"));
        }
        PrivacySettings.INSTANCE.privacyMode.setActive(false);
        PrivacySettings.INSTANCE.read(new JsonObject());
        assertTrue(PrivacySettings.INSTANCE.privacyMode.isActive());
    }

    @Test
    void filtersFabricAnnouncementsAndAllowsThemWithPrivacyDisabled() {
        final Identifier identity = Identifier.parse("viafabricplus:example");
        final List<CustomPacketPayload> announcements = List.of(
            new BrandPayload("ViaFabricPlus"),
            new RegistrationPayload(RegistrationPayload.REGISTER, List.of(identity)),
            new RegistrationPayload(RegistrationPayload.UNREGISTER, List.of(identity)),
            new CommonRegisterPayload(1, "play", Set.of(identity)),
            new CommonRegisterPayload(1, "configuration", Set.of(identity)),
            new CommonVersionPayload(new int[]{1})
        );
        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel();
        connection.channel = channel;
        try {
            for (boolean privacy : new boolean[]{true, false}) {
                PrivacySettings.INSTANCE.privacyMode.setActive(privacy);
                for (CustomPacketPayload payload : announcements) {
                    final var packet = new ServerboundCustomPayloadPacket(payload);
                    final AtomicBoolean completed = new AtomicBoolean();
                    connection.send(packet, future -> completed.set(future.isSuccess()), true);
                    channel.runPendingTasks();
                    assertTrue(completed.get());
                    if (privacy) {
                        assertNull(channel.readOutbound(), payload.type().id().toString());
                    } else {
                        assertSame(packet, channel.readOutbound());
                    }
                }
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

}
