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

import dev.kastle.netty.channel.nethernet.NetherNetChannelFactory;
import dev.kastle.netty.channel.nethernet.config.NetherChannelOption;
import dev.kastle.webrtc.PeerConnectionFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public final class ViaFabricPlusNetherNetLanIntegrationTest {

    @Test
    public void connectsToConfiguredLanWorld() throws InterruptedException {
        final String host = System.getenv("VFP_LAN_TEST_HOST");
        assumeTrue(host != null && !host.isBlank());

        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        try {
            final Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channelFactory(NetherNetChannelFactory.client(
                    new PeerConnectionFactory(),
                    new BedrockNetherNetIdentitySignaling(
                        new ViaFabricPlusNetherNetDiscoverySignaling(),
                        BedrockNetherNetIdentity.createSelfSigned("LanIntegrationTest")
                    )
                ))
                .handler(new ChannelInboundHandlerAdapter())
                .option(NetherChannelOption.NETHER_CLIENT_HANDSHAKE_TIMEOUT_MS, 20_000)
                .option(NetherChannelOption.NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS, 0);

            final ChannelFuture connectFuture = bootstrap.connect(new InetSocketAddress(host, 7_551));
            assertTrue(connectFuture.awaitUninterruptibly(25_000));
            assertTrue(connectFuture.isSuccess(), () -> "Connection failed: " + connectFuture.cause());
        } finally {
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

}
