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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class ViaFabricPlusNetherNetDiscoverySignalingTest {

    @Test
    public void removesIncompleteCandidateFromConnectResponse() {
        final String signal = "CONNECTRESPONSE 42 v=0\r\n"
            + "a=candidate:2646057034 1 udp 2122066175 2a04:4a43:119:a9\r\n"
            + "a=candidate:1 1 udp 2122260223 192.168.4.235 53555 typ host generation 0\r\n"
            + "a=ice-ufrag:test\r\n";

        assertEquals(
            "CONNECTRESPONSE 42 v=0\r\n"
                + "a=candidate:1 1 udp 2122260223 192.168.4.235 53555 typ host generation 0\r\n"
                + "a=ice-ufrag:test\r\n",
            ViaFabricPlusNetherNetDiscoverySignaling.sanitizeSignal(signal)
        );
    }

    @Test
    public void leavesCandidateMessagesUnchanged() {
        final String signal = "CANDIDATEADD 42 candidate:1 1 udp 1 192.168.4.235 53555 typ host";
        assertEquals(signal, ViaFabricPlusNetherNetDiscoverySignaling.sanitizeSignal(signal));
    }

    @Test
    public void removesIpv6CandidatesFromAnswerAndTrickle() {
        final String answer = "CONNECTRESPONSE 42 v=0\r\n"
            + "a=candidate:1 1 udp 2121937663 fd74:6572:6d6e:7573:c:beb5:a989:471e 62511 typ host generation 0\r\n"
            + "a=candidate:2 1 udp 2122260223 192.168.4.235 53555 typ host generation 0\r\n";
        assertEquals(
            "CONNECTRESPONSE 42 v=0\r\n"
                + "a=candidate:2 1 udp 2122260223 192.168.4.235 53555 typ host generation 0\r\n",
            ViaFabricPlusNetherNetDiscoverySignaling.sanitizeSignal(answer)
        );
        assertNull(ViaFabricPlusNetherNetDiscoverySignaling.sanitizeSignal(
            "CANDIDATEADD 42 candidate:1 1 udp 1 fd74:6572:6d6e:7573:c:beb5:a989:471e 62511 typ host"
        ));
    }

}
