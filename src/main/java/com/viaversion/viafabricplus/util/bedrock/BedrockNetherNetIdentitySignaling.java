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

package com.viaversion.viafabricplus.util.bedrock;

import dev.kastle.netty.channel.nethernet.signaling.NetherNetClientSignaling;
import java.net.SocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Adds the per-connection Bedrock identity to every outgoing NetherNet offer,
 * independent of whether Xbox, LAN discovery, or another signaling transport
 * carries it.
 */
public final class BedrockNetherNetIdentitySignaling implements NetherNetClientSignaling {

    private final NetherNetClientSignaling delegate;
    private final BedrockNetherNetIdentity identity;

    public BedrockNetherNetIdentitySignaling(final NetherNetClientSignaling delegate, final BedrockNetherNetIdentity identity) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    @Override
    public CompletableFuture<List<IceServerInfo>> connect(final SocketAddress remoteAddress) {
        return this.delegate.connect(remoteAddress);
    }

    @Override
    public void setNotFoundHandler(final NotFoundHandler handler) {
        this.delegate.setNotFoundHandler(handler);
    }

    @Override
    public void sendSignal(final String targetNetworkId, final String data) {
        this.delegate.sendSignal(targetNetworkId, this.identity.augmentConnectRequest(data));
    }

    @Override
    public void setSignalHandler(final long connectionId, final SignalHandler handler) {
        this.delegate.setSignalHandler(connectionId, handler);
    }

    @Override
    public void removeSignalHandler(final long connectionId) {
        this.delegate.removeSignalHandler(connectionId);
    }

    @Override
    public String getLocalNetworkId() {
        return this.delegate.getLocalNetworkId();
    }

    @Override
    public void close() {
        this.delegate.close();
    }

}
