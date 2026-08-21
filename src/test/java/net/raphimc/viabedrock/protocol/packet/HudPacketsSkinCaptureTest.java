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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.raphimc.viabedrock.protocol.model.SkinData;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public final class HudPacketsSkinCaptureTest {

    @Test
    void capturesProtocol2168AddSkinsAndConsumesRemoveEntries() throws Exception {
        final UUID addedUuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        final UUID removedUuid = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");
        final BufferedImage pixels = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(3, 7, 0xFF2468AC);

        final ByteBuf input = Unpooled.buffer();
        BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(input, 2);

        BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(input, 1); // add marker
        Types.BYTE.write(input, (byte) 0); // add action
        BedrockTypes.UUID.write(input, addedUuid);
        BedrockTypes.VAR_LONG.write(input, 42L);
        BedrockTypes.STRING.write(input, "player");
        BedrockTypes.STRING.write(input, "xuid");
        BedrockTypes.STRING.write(input, "platform-chat");
        BedrockTypes.INT_LE.write(input, 7);
        writeProtocol2168Skin(input, pixels);
        Types.BOOLEAN.write(input, false);
        Types.BOOLEAN.write(input, true);
        Types.BOOLEAN.write(input, false);
        BedrockTypes.INT_LE.write(input, 0xFF010203);

        BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(input, 0); // remove marker
        Types.BYTE.write(input, (byte) 1); // remove action
        BedrockTypes.UUID.write(input, removedUuid);

        final PacketWrapper wrapper = new PacketWrapperImpl(0, input, null);
        final List<UUID> capturedUuids = new ArrayList<>();
        final List<SkinData> capturedSkins = new ArrayList<>();

        assertEquals(1, HudPackets.captureInitialSkins(wrapper, (uuid, skin) -> {
            capturedUuids.add(uuid);
            capturedSkins.add(skin);
        }));
        assertEquals(List.of(addedUuid), capturedUuids);
        assertEquals(0xFF2468AC, capturedSkins.getFirst().skinData().getRGB(3, 7));
        assertFalse(input.isReadable());
    }

    private static void writeProtocol2168Skin(final ByteBuf output, final BufferedImage pixels) {
        BedrockTypes.STRING.write(output, "skin-id");
        BedrockTypes.STRING.write(output, "playfab-id");
        BedrockTypes.STRING.write(output, "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}");
        BedrockTypes.IMAGE.write(output, pixels);
        BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(output, 0); // animations
        BedrockTypes.IMAGE.write(output, null); // cape
        BedrockTypes.STRING.write(output, "{}");
        BedrockTypes.STRING.write(output, "1.0.0");
        BedrockTypes.STRING.write(output, "");
        BedrockTypes.STRING.write(output, "");
        BedrockTypes.STRING.write(output, "full-skin-id");
        output.writeByte(1); // wide arms
        output.writeIntLE(0xFF112233);
        BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(output, 0); // persona pieces
        BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(output, 0); // tints
        output.writeBoolean(false); // premium
        output.writeBoolean(false); // persona
        output.writeBoolean(false); // cape on classic
        output.writeBoolean(true); // primary user
        output.writeBoolean(false); // overriding appearance
        BedrockTypes.STRING.write(output, "true"); // trusted
        BedrockTypes.STRING.write(output, "profile-hash");
    }

}
