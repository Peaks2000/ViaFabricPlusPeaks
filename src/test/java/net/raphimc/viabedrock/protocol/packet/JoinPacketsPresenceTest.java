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

package net.raphimc.viabedrock.protocol.packet;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the protocol-2168/2169 StartGame Server Configuration presence field. It is a single
 * optional rich-presence id, so parsing it must never consume a second string as the older
 * two-string layout did, which over-read the buffer and prevented the CONFIGURATION to PLAY
 * transition.
 */
public final class JoinPacketsPresenceTest {

    @Test
    void absentPresenceConsumesOnlyItsFlag() {
        final ByteBuf input = Unpooled.buffer();
        Types.BOOLEAN.write(input, false); // has presence info

        final PacketWrapper wrapper = new PacketWrapperImpl(0, input, null);
        JoinPackets.readServerConfigurationPresence(wrapper);
        assertFalse(input.isReadable());
    }

    @Test
    void presentPresenceWithoutRichPresenceIdConsumesTwoFlags() {
        final ByteBuf input = Unpooled.buffer();
        Types.BOOLEAN.write(input, true); // has presence info
        Types.BOOLEAN.write(input, false); // no rich presence id

        final PacketWrapper wrapper = new PacketWrapperImpl(0, input, null);
        JoinPackets.readServerConfigurationPresence(wrapper);
        assertFalse(input.isReadable());
    }

    @Test
    void presentPresenceWithRichPresenceIdConsumesExactlyOneString() {
        final ByteBuf input = Unpooled.buffer();
        Types.BOOLEAN.write(input, true); // has presence info
        Types.BOOLEAN.write(input, true); // has rich presence id
        Types.STRING.write(input, "01234567-89ab-cdef-0123-456789abcdef"); // rich presence id

        final PacketWrapper wrapper = new PacketWrapperImpl(0, input, null);
        JoinPackets.readServerConfigurationPresence(wrapper);
        assertFalse(input.isReadable());
    }
}
