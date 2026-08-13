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
 */

package com.viaversion.viafabricplus.util.bedrock;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

public final class NetherNetDiscoveryPacketFixer {

    private static final int MESSAGE_HEADER_LENGTH = Long.BYTES + Integer.BYTES;

    public static int repairTruncatedConnectResponseLength(final ByteBuf buffer) {
        final int readerIndex = buffer.readerIndex();
        if (buffer.readableBytes() < MESSAGE_HEADER_LENGTH) {
            return 0;
        }

        final int claimedLength = buffer.getIntLE(readerIndex + Long.BYTES);
        final int availableLength = buffer.readableBytes() - MESSAGE_HEADER_LENGTH;
        if (claimedLength < 0 || availableLength <= claimedLength) {
            return 0;
        }

        final int messageIndex = readerIndex + MESSAGE_HEADER_LENGTH;
        final String declaredMessage = buffer.toString(messageIndex, claimedLength, StandardCharsets.UTF_8);
        final String trailingData = buffer.toString(messageIndex + claimedLength, availableLength - claimedLength, StandardCharsets.UTF_8);
        if (!declaredMessage.startsWith("CONNECTRESPONSE ")
            || !trailingData.contains("a=fingerprint:")
            || !trailingData.contains("a=ice-ufrag:")
            || !trailingData.contains("a=ice-pwd:")
            || !trailingData.contains("a=setup:")) {
            return 0;
        }

        buffer.setIntLE(readerIndex + Long.BYTES, availableLength);
        return availableLength - claimedLength;
    }

    private NetherNetDiscoveryPacketFixer() {
    }

}
