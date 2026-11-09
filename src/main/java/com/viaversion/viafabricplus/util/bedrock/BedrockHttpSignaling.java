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

package com.viaversion.viafabricplus.util.bedrock;

import dev.kastle.netty.channel.nethernet.NetherNetClientChannel;
import dev.kastle.netty.channel.nethernet.NetherNetConstants;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetClientSignaling;
import dev.kastle.webrtc.PeerConnectionFactory;
import dev.kastle.webrtc.RTCIceGatheringState;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

/** The dedicated-server HTTP handshake carries a complete, non-trickled WebRTC offer. */
public final class BedrockHttpSignaling implements NetherNetClientSignaling {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI endpoint;
    private final BedrockNetherNetIdentity identity;
    private final String networkId = Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits());
    private volatile SignalHandler handler;
    private volatile boolean closed;
    private HttpChannel channel;
    private long connectionId;
    private boolean offerPending;

    public BedrockHttpSignaling(final URI endpoint, final BedrockNetherNetIdentity identity) {
        this.endpoint = endpoint;
        this.identity = identity;
    }

    public static URI probe(final InetSocketAddress address) {
        final String host = address.getHostString();
        final String authority = (host.contains(":") ? "[" + host + "]" : host) + ":" + address.getPort();
        final URI secure = URI.create("https://" + authority + "/v1/join");
        try {
            return accepts(secure) ? secure : null;
        } catch (Exception failure) {
            Throwable cause = failure;
            while (cause.getCause() != null && !(cause instanceof SSLException)) {
                cause = cause.getCause();
            }
            if (cause instanceof SSLException) {
                final URI plain = URI.create("http://" + authority + "/v1/join");
                try {
                    return accepts(plain) ? plain : null;
                } catch (Exception ignored) {
                    return null;
                }
            }
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static boolean accepts(final URI uri) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2))
            .header("Accept", "application/json").GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    }

    public NetherNetClientChannel newChannel() {
        this.channel = new HttpChannel(new PeerConnectionFactory(), this);
        this.channel.setTargetNetworkId("0");
        return this.channel;
    }

    @Override
    public CompletableFuture<List<IceServerInfo>> connect(final SocketAddress address) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public void sendSignal(final String targetNetworkId, final String signal) {
        if (this.closed || !signal.startsWith("CONNECTREQUEST ") || this.offerPending) {
            return;
        }
        this.connectionId = Long.parseUnsignedLong(signal.split(" ", 3)[1]);
        this.offerPending = true;
        this.channel.eventLoop().execute(this::sendGatheredOffer);
    }

    private void sendGatheredOffer() {
        if (this.closed || !this.channel.isOpen()) {
            return;
        }
        final String offer = this.channel.gatheredOffer();
        if (offer == null) {
            this.channel.eventLoop().schedule(this::sendGatheredOffer, 50, TimeUnit.MILLISECONDS);
            return;
        }
        final URI join = URI.create(this.endpoint.toString() + "/" + this.networkId);
        final HttpRequest request = HttpRequest.newBuilder(join).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/sdp").header("Accept", "application/sdp")
            .POST(HttpRequest.BodyPublishers.ofString(this.identity.augmentOffer(offer))).build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, failure) ->
            this.channel.eventLoop().execute(() -> {
                if (this.closed) {
                    return;
                }
                if (failure != null || response.statusCode() / 100 != 2 || !response.body().startsWith("v=")) {
                    this.channel.pipeline().fireExceptionCaught(new java.net.ConnectException(
                        failure != null ? "NetherNet HTTP signaling failed" : "NetherNet HTTP signaling returned " + response.statusCode()));
                    this.channel.close();
                } else if (this.handler != null) {
                    this.handler.onSignal(NetherNetConstants.buildSignalConnectResponse(this.connectionId, response.body()));
                }
            }));
    }

    @Override
    public void setNotFoundHandler(final NotFoundHandler handler) {
    }

    @Override
    public void setSignalHandler(final long connectionId, final SignalHandler handler) {
        this.handler = handler;
    }

    @Override
    public void removeSignalHandler(final long connectionId) {
        this.handler = null;
    }

    @Override
    public String getLocalNetworkId() {
        return this.networkId;
    }

    @Override
    public void close() {
        this.closed = true;
        this.handler = null;
    }

    private static final class HttpChannel extends NetherNetClientChannel {
        HttpChannel(final PeerConnectionFactory factory, final NetherNetClientSignaling signaling) {
            super(factory, signaling);
        }

        String gatheredOffer() {
            return this.peerConnection != null && this.peerConnection.getIceGatheringState() == RTCIceGatheringState.COMPLETE
                ? this.peerConnection.getLocalDescription().sdp : null;
        }
    }
}
