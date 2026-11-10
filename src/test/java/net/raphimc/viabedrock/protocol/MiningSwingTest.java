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
package net.raphimc.viabedrock.protocol;

import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Locale;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ActorSwingSource;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MiningSwingTest {
    @Test void miningAndAttackSwingSourcesStayDistinctOnTheWire() throws Exception {
        for (ActorSwingSource source : List.of(ActorSwingSource.Mine, ActorSwingSource.Attack)) {
            final var packet = new PacketWrapperImpl(-1, null, null);
            PlayerActionPacketFactory.writeSwing(packet, 42L, source);
            final var buffer = Unpooled.buffer();
            try {
                packet.writeToBuffer(buffer);
                assertEquals(1, buffer.readUnsignedByte());
                assertEquals(42L, BedrockTypes.UNSIGNED_VAR_LONG.read(buffer));
                assertEquals(0F, BedrockTypes.FLOAT_LE.read(buffer));
                assertEquals(source.name().toLowerCase(Locale.ROOT), BedrockTypes.OPTIONAL_STRING.read(buffer));
                assertEquals((byte) 0, Types.BYTE.read(buffer));
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
        }
    }
}
