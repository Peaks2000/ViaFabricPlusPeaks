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

package com.viaversion.viafabricplus.features.networking.blocked_server;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BlockedServerOverrideTest {

    @Test
    public void confirmedBlockReasonIsAnnotatedWithoutChangingVanillaErrors() {
        final Component vanillaReason = Component.literal("Unknown host");
        final Component annotatedReason = BlockedServerOverride.confirmedBlockedReason(vanillaReason);

        assertEquals("Unknown host", vanillaReason.getString());
        assertEquals(2, annotatedReason.getSiblings().size());
        assertEquals(" ", annotatedReason.getSiblings().getFirst().getString());
        final TranslatableContents annotation = assertInstanceOf(TranslatableContents.class,
            annotatedReason.getSiblings().get(1).getContents());
        assertEquals("base.viafabricplus.possible_blacklisted_server", annotation.getKey());
    }

    @Test
    public void onlyOffersExplicitlyBlockedAttemptOnce() {
        final AtomicLong clock = new AtomicLong();
        final BlockedServerOverride.OneShotGate<String, String> gate =
            new BlockedServerOverride.OneShotGate<>(100, clock::get);

        gate.begin("blocked.example", "connection context");
        assertTrue(gate.consumeBlocked().isEmpty());

        gate.markBlocked("different.example");
        assertTrue(gate.consumeBlocked().isEmpty());

        gate.markBlocked("blocked.example");
        assertEquals("connection context", gate.consumeBlocked().orElseThrow());
        assertTrue(gate.consumeBlocked().isEmpty());
    }

    @Test
    public void bypassIsExactAddressAndOneShot() {
        final BlockedServerOverride.OneShotGate<String, String> gate =
            new BlockedServerOverride.OneShotGate<>(100, () -> 0L);

        gate.armBypass("blocked.example");
        assertFalse(gate.consumeBypass("different.example"));
        assertTrue(gate.consumeBypass("blocked.example"));
        assertFalse(gate.consumeBypass("blocked.example"));
    }

    @Test
    public void aDifferentConnectionAndExpiryRevokeState() {
        final AtomicLong clock = new AtomicLong();
        final BlockedServerOverride.OneShotGate<String, String> gate =
            new BlockedServerOverride.OneShotGate<>(100, clock::get);

        gate.armBypass("blocked.example");
        gate.begin("different.example", "different context");
        assertFalse(gate.consumeBypass("blocked.example"));

        gate.markBlocked("different.example");
        clock.set(101);
        assertTrue(gate.consumeBlocked().isEmpty());
    }

    @Test
    public void connectionResolutionScopeExcludesOtherThreads() throws InterruptedException {
        final ServerAddress address = ServerAddress.parseString("blocked.example");
        final AtomicBoolean visibleToServerPinger = new AtomicBoolean(true);

        BlockedServerOverride.beginConnectionResolution(address);
        try {
            assertTrue(BlockedServerOverride.isConnectionResolution(address));
            final Thread serverPinger = new Thread(() -> visibleToServerPinger.set(
                BlockedServerOverride.isConnectionResolution(address)));
            serverPinger.start();
            serverPinger.join();
            assertFalse(visibleToServerPinger.get());
        } finally {
            BlockedServerOverride.finishConnectionResolution();
        }
        assertFalse(BlockedServerOverride.isConnectionResolution(address));
    }
}
