/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.viaversion.viafabricplus.features.networking.privacy;

import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.settings.impl.PrivacySettings;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.atomic.AtomicBoolean;
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
        PrivacySettings.INSTANCE.sendClientBrand.setActive(false);
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
        PrivacySettings.INSTANCE.sendClientBrand.setActive(true);
        final JsonObject saved = new JsonObject();
        PrivacySettings.INSTANCE.write(saved);
        PrivacySettings.INSTANCE.sendClientBrand.setActive(false);
        PrivacySettings.INSTANCE.read(saved);
        assertTrue(PrivacySettings.INSTANCE.sendClientBrand.isActive());

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
}
