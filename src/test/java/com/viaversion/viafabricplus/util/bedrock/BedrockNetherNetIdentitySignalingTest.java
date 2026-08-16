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
import dev.kastle.netty.channel.nethernet.signaling.NetherNetClientSignaling.NotFoundHandler;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetSignaling.SignalHandler;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BedrockNetherNetIdentitySignalingTest {

    private static final String OFFER = "v=0\r\n"
        + "a=fingerprint:sha-256 AA:BB:CC:DD\r\n"
        + "m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n";

    @Test
    public void augmentsOffersOnEverySignalingTransport() {
        final RecordingSignaling delegate = new RecordingSignaling();
        final BedrockNetherNetIdentitySignaling signaling = new BedrockNetherNetIdentitySignaling(
            delegate,
            BedrockNetherNetIdentity.createSelfSigned("LanPlayer")
        );

        signaling.sendSignal("remote-id", "CONNECTREQUEST 42 " + OFFER);
        assertEquals("remote-id", delegate.targetNetworkId);
        assertTrue(delegate.signal.contains("a=identity:"));

        signaling.sendSignal("remote-id", "CANDIDATEADD 42 candidate");
        assertEquals("CANDIDATEADD 42 candidate", delegate.signal);

        final SignalHandler signalHandler = _ -> {
        };
        final NotFoundHandler notFoundHandler = _ -> {
        };
        signaling.setSignalHandler(42, signalHandler);
        signaling.setNotFoundHandler(notFoundHandler);
        assertSame(signalHandler, delegate.signalHandler);
        assertSame(notFoundHandler, delegate.notFoundHandler);
        assertEquals("local-id", signaling.getLocalNetworkId());

        signaling.close();
        assertTrue(delegate.closed);
    }

    private static final class RecordingSignaling implements NetherNetClientSignaling {

        private String targetNetworkId;
        private String signal;
        private SignalHandler signalHandler;
        private NotFoundHandler notFoundHandler;
        private boolean closed;

        @Override
        public CompletableFuture<List<IceServerInfo>> connect(final SocketAddress remoteAddress) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public void setNotFoundHandler(final NotFoundHandler handler) {
            this.notFoundHandler = handler;
        }

        @Override
        public void sendSignal(final String targetNetworkId, final String data) {
            this.targetNetworkId = targetNetworkId;
            this.signal = data;
        }

        @Override
        public void setSignalHandler(final long connectionId, final SignalHandler handler) {
            this.signalHandler = handler;
        }

        @Override
        public void removeSignalHandler(final long connectionId) {
            this.signalHandler = null;
        }

        @Override
        public String getLocalNetworkId() {
            return "local-id";
        }

        @Override
        public void close() {
            this.closed = true;
        }

    }

}
