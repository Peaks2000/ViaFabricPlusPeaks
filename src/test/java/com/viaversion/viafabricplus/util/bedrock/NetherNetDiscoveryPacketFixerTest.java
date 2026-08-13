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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class NetherNetDiscoveryPacketFixerTest {

    @Test
    public void repairsConnectResponseWithSdpAttributesOutsideClaimedLength() {
        final String declared = "CONNECTRESPONSE 42 v=0\r\na=candidate:1 1 udp 1 192.168.4.172 5000 typ host\r\n";
        final String omitted = "a=ice-ufrag:test\r\na=ice-pwd:password\r\na=fingerprint:sha-256 00:11\r\na=setup:active\r\n";
        final ByteBuf message = discoveryMessage(declared, omitted);
        try {
            assertEquals(omitted.length(), NetherNetDiscoveryPacketFixer.repairTruncatedConnectResponseLength(message));
            assertEquals(declared.length() + omitted.length(), message.getIntLE(message.readerIndex() + 8));
        } finally {
            message.release();
        }
    }

    @Test
    public void ignoresUnrelatedTrailingData() {
        final String declared = "CANDIDATEADD 42 candidate:1 1 udp 1 192.168.4.172 5000 typ host";
        final ByteBuf message = discoveryMessage(declared, "unrelated");
        try {
            assertEquals(0, NetherNetDiscoveryPacketFixer.repairTruncatedConnectResponseLength(message));
            assertEquals(declared.length(), message.getIntLE(message.readerIndex() + 8));
        } finally {
            message.release();
        }
    }

    private static ByteBuf discoveryMessage(final String declared, final String omitted) {
        final byte[] declaredBytes = declared.getBytes(StandardCharsets.UTF_8);
        final ByteBuf message = Unpooled.buffer()
            .writeLongLE(0)
            .writeIntLE(declaredBytes.length)
            .writeBytes(declaredBytes);
        message.writeCharSequence(omitted, StandardCharsets.UTF_8);
        return message;
    }

}
