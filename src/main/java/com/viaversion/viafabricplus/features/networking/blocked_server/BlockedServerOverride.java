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

package com.viaversion.viafabricplus.features.networking.blocked_server;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import net.minecraft.client.multiplayer.resolver.ServerRedirectHandler;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Keeps Mojang's blocked-server check enabled while allowing an explicit, exact-address retry.
 */
public final class BlockedServerOverride {

    private static final OneShotGate<ServerAddress, ConnectionAttempt> GATE =
        new OneShotGate<>(TimeUnit.SECONDS.toNanos(30), System::nanoTime);
    private static final ThreadLocal<ServerAddress> CONNECTION_RESOLUTION = new ThreadLocal<>();

    private BlockedServerOverride() {
    }

    public static boolean isEligibleRoute() {
        return !ProtocolTranslator.isBedrock()
            && ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_16_4);
    }

    public static void beginAttempt(final Screen parent, final Minecraft minecraft, final ServerAddress address,
                                    final ServerData serverData, final boolean quickPlay,
                                    final @Nullable TransferState transferState) {
        GATE.begin(address, new ConnectionAttempt(parent, minecraft, address, serverData, quickPlay, transferState));
    }

    public static boolean isCurrentAttempt(final ServerAddress address) {
        return GATE.isCurrent(address);
    }

    public static void beginConnectionResolution(final ServerAddress address) {
        CONNECTION_RESOLUTION.set(address);
    }

    public static boolean isConnectionResolution(final ServerAddress address) {
        return Objects.equals(CONNECTION_RESOLUTION.get(), address);
    }

    public static void finishConnectionResolution() {
        CONNECTION_RESOLUTION.remove();
    }

    public static void markBlocked(final ServerAddress address) {
        GATE.markBlocked(address);
    }

    public static void finishResolution(final ServerAddress address) {
        GATE.finish(address);
    }

    public static Optional<ConnectionAttempt> consumeBlockedAttempt() {
        return GATE.consumeBlocked();
    }

    public static Component confirmedBlockedReason(final Component originalReason) {
        return originalReason.copy()
            .append(" ")
            .append(Component.translatable("base.viafabricplus.possible_blacklisted_server"));
    }

    public static boolean consumeBypass(final ServerAddress address) {
        return GATE.consumeBypass(address);
    }

    public static Optional<ResolvedServerAddress> resolveWithoutBlocklist(final ServerAddress address,
                                                                          final ServerAddressResolver resolver,
                                                                          final ServerRedirectHandler redirectHandler) {
        Optional<ResolvedServerAddress> resolvedAddress = resolver.resolve(address);
        final Optional<ServerAddress> redirect = redirectHandler.lookupRedirect(address);
        if (redirect.isPresent()) {
            resolvedAddress = resolver.resolve(redirect.get());
        }
        return resolvedAddress;
    }

    public static void joinAnyway(final ConnectionAttempt attempt) {
        GATE.armBypass(attempt.address());
        ConnectScreen.startConnecting(attempt.parent(), attempt.minecraft(), attempt.address(), attempt.serverData(),
            attempt.quickPlay(), attempt.transferState());
    }

    public record ConnectionAttempt(Screen parent, Minecraft minecraft, ServerAddress address, ServerData serverData,
                                    boolean quickPlay, @Nullable TransferState transferState) {
    }

    static final class OneShotGate<K, V> {

        private final long lifetimeNanos;
        private final LongSupplier clock;
        private @Nullable Attempt<K, V> attempt;
        private @Nullable Permit<K> permit;

        OneShotGate(final long lifetimeNanos, final LongSupplier clock) {
            if (lifetimeNanos <= 0) {
                throw new IllegalArgumentException("lifetimeNanos must be positive");
            }
            this.lifetimeNanos = lifetimeNanos;
            this.clock = clock;
        }

        synchronized void begin(final K key, final V value) {
            final long now = this.clock.getAsLong();
            this.expire(now);
            if (this.permit != null && !Objects.equals(this.permit.key(), key)) {
                this.permit = null;
            }
            this.attempt = new Attempt<>(key, value, now, false);
        }

        synchronized boolean isCurrent(final K key) {
            this.expire(this.clock.getAsLong());
            return this.attempt != null && Objects.equals(this.attempt.key(), key);
        }

        synchronized void markBlocked(final K key) {
            this.expire(this.clock.getAsLong());
            if (this.attempt != null && Objects.equals(this.attempt.key(), key)) {
                this.attempt = new Attempt<>(this.attempt.key(), this.attempt.value(), this.attempt.createdAtNanos(), true);
            }
        }

        synchronized void finish(final K key) {
            this.expire(this.clock.getAsLong());
            if (this.attempt != null && Objects.equals(this.attempt.key(), key)) {
                this.attempt = null;
            }
        }

        synchronized Optional<V> consumeBlocked() {
            this.expire(this.clock.getAsLong());
            if (this.attempt == null || !this.attempt.blocked()) {
                return Optional.empty();
            }
            final V value = this.attempt.value();
            this.attempt = null;
            return Optional.of(value);
        }

        synchronized void armBypass(final K key) {
            this.permit = new Permit<>(key, this.clock.getAsLong());
        }

        synchronized boolean consumeBypass(final K key) {
            this.expire(this.clock.getAsLong());
            if (this.permit == null || !Objects.equals(this.permit.key(), key)) {
                return false;
            }
            this.permit = null;
            return true;
        }

        private void expire(final long now) {
            if (this.attempt != null && now - this.attempt.createdAtNanos() > this.lifetimeNanos) {
                this.attempt = null;
            }
            if (this.permit != null && now - this.permit.createdAtNanos() > this.lifetimeNanos) {
                this.permit = null;
            }
        }

        private record Attempt<K, V>(K key, V value, long createdAtNanos, boolean blocked) {
        }

        private record Permit<K>(K key, long createdAtNanos) {
        }
    }
}
