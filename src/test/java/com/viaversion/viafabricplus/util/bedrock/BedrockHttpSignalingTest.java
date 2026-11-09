/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.viaversion.viafabricplus.util.bedrock;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class BedrockHttpSignalingTest {
    @Test
    void probesDedicatedServerAndLeavesUnsupportedServersOnTheirExistingTransport() throws Exception {
        final AtomicInteger status = new AtomicInteger(200);
        try (ServerSocket server = new ServerSocket(0, 4, java.net.InetAddress.getLoopbackAddress())) {
            final Thread responder = Thread.ofPlatform().daemon().start(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(2_000);
                        final var input = socket.getInputStream();
                        if (input.read() == 'G') {
                            int state = 0;
                            final byte[] ending = {'\r', '\n', '\r', '\n'};
                            while (state < ending.length) {
                                final int next = input.read();
                                if (next < 0) break;
                                state = next == ending[state] ? state + 1 : 0;
                            }
                        }
                        // A plaintext BDS endpoint answers the TLS probe with HTTP as well.
                        socket.getOutputStream().write(("HTTP/1.1 " + status.get()
                            + " Test\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    } catch (java.io.IOException ignored) {
                        // Closing the test listener interrupts accept.
                    }
                }
            });
            final InetSocketAddress address = new InetSocketAddress(server.getInetAddress(), server.getLocalPort());
            final java.net.URI endpoint = BedrockHttpSignaling.probe(address);
            assertNotNull(endpoint);
            assertEquals("http", endpoint.getScheme());
            assertEquals("/v1/join", endpoint.getPath());
            status.set(404);
            assertNull(BedrockHttpSignaling.probe(address));
        }
    }
}
