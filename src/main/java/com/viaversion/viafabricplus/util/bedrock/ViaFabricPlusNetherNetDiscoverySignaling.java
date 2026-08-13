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

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetDiscoverySignaling;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetSignaling;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes LAN signaling tolerant of incomplete ICE candidates emitted by some
 * Bedrock hosts. A bad candidate must not invalidate the entire SDP answer;
 * valid candidates are also delivered separately through CANDIDATEADD.
 */
public final class ViaFabricPlusNetherNetDiscoverySignaling extends NetherNetDiscoverySignaling {

    private static final String CONNECT_RESPONSE = "CONNECTRESPONSE ";
    private static final String CANDIDATE_ADD = "CANDIDATEADD ";

    @Override
    public void setSignalHandler(final long connectionId, final NetherNetSignaling.SignalHandler handler) {
        super.setSignalHandler(connectionId, signal -> {
            final String sanitized = sanitizeSignal(signal);
            if (sanitized != null) {
                handler.onSignal(sanitized);
            }
        });
    }

    static String sanitizeSignal(final String signal) {
        if (signal == null) {
            return signal;
        }
        if (signal.startsWith(CANDIDATE_ADD)) {
            final String[] parts = signal.split(" ", 3);
            if (parts.length < 3 || !isSupportedCandidate(parts[2])) {
                ViaFabricPlusImpl.INSTANCE.getLogger().warn("Ignored an unsupported NetherNet LAN ICE candidate");
                return null;
            }
            return signal;
        }
        if (!signal.startsWith(CONNECT_RESPONSE)) {
            return signal;
        }

        final String[] lines = signal.split("\\r?\\n", -1);
        final List<String> validLines = new ArrayList<>(lines.length);
        int removedCandidates = 0;
        for (final String line : lines) {
            if (line.startsWith("a=candidate:") && !isSupportedCandidate(line)) {
                removedCandidates++;
                continue;
            }
            validLines.add(line);
        }
        if (removedCandidates == 0) {
            return signal;
        }

        ViaFabricPlusImpl.INSTANCE.getLogger().warn("Removed {} unsupported ICE candidate(s) from the NetherNet LAN SDP answer", removedCandidates);
        return String.join("\r\n", validLines);
    }

    private static boolean isSupportedCandidate(final String line) {
        final String[] fields = line.trim().split("\\s+");
        if (fields.length < 8 || !"typ".equals(fields[6]) || !isLanIpv4Address(fields[4])) {
            return false;
        }
        try {
            final int port = Integer.parseInt(fields[5]);
            return port > 0 && port <= 65_535;
        } catch (final NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isLanIpv4Address(final String address) {
        final String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        final int[] values = new int[4];
        try {
            for (int i = 0; i < octets.length; i++) {
                values[i] = Integer.parseInt(octets[i]);
                if (values[i] < 0 || values[i] > 255) {
                    return false;
                }
            }
        } catch (final NumberFormatException ignored) {
            return false;
        }
        return values[0] == 10
            || values[0] == 172 && values[1] >= 16 && values[1] <= 31
            || values[0] == 192 && values[1] == 168
            || values[0] == 169 && values[1] == 254;
    }

}
