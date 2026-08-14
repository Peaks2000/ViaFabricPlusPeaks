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
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.DataItemType;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.model.GameRule;
import net.raphimc.viabedrock.protocol.model.InventoryStackRequest;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import net.raphimc.viabedrock.protocol.types.entitydata.EntityDataType;
import net.raphimc.viabedrock.protocol.types.inventory.InventoryStackRequestType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class Bedrock12640WireTypesTest {

    @Test
    public void startGameIntegerRulesUseFixedLittleEndianValues() {
        final ByteBuf buffer = Unpooled.buffer();
        try {
            BedrockTypes.STRING.write(buffer, "randomtickspeed");
            buffer.writeBoolean(true);
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 2);
            buffer.writeIntLE(4_118);

            final GameRule gameRule = BedrockTypes.GAME_RULE.read(buffer);
            assertEquals("randomtickspeed", gameRule.name());
            assertEquals(4_118, gameRule.value());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void entityDataConsumesRepeatedOneOfDiscriminator() {
        final ByteBuf buffer = Unpooled.buffer();
        try {
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 92);
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, DataItemType.Int.getValue());
            buffer.writeByte(DataItemType.Int.getValue());
            BedrockTypes.VAR_INT.write(buffer, 123);

            final com.viaversion.viaversion.api.minecraft.entitydata.EntityData entityData = new EntityDataType().read(buffer);
            assertEquals(92, entityData.id());
            assertEquals(123, ((Integer) entityData.value()).intValue());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void craftingRequestMatchesProtocol2168CerealLayout() {
        final BedrockItem output = new BedrockItem(1, (short) 0, (byte) 4);
        final InventoryStackRequest request = new InventoryStackRequest(-1, List.of(
            new InventoryStackRequest.CraftRecipe(123, 1),
            new InventoryStackRequest.CraftResultsDeprecated(List.of(output), 1),
            new InventoryStackRequest.Consume(1, new InventoryStackRequest.Slot(
                new FullContainerName(ContainerEnumName.CraftingInputContainer, null), 32, 7
            )),
            new InventoryStackRequest.Take(4,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CreatedOutputContainer, null), 50, -1),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.InventoryContainer, null), 9, 0)
            )
        ));
        final ByteBuf buffer = Unpooled.buffer();
        try {
            new InventoryStackRequestType(id -> id == 1 ? "minecraft:stone" : null).write(buffer, request);

            assertEquals(-1, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(4, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));

            assertEquals(10, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(12, buffer.readUnsignedByte());
            assertEquals(123, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, buffer.readUnsignedByte());

            assertEquals(17, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(19, buffer.readUnsignedByte());
            assertEquals(1, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, buffer.readUnsignedByte());
            assertEquals("minecraft:stone", BedrockTypes.STRING.read(buffer));
            assertEquals(0, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(4, buffer.readUnsignedShortLE());
            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            final ByteBuf userData = buffer.readSlice(BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(0, userData.readShortLE());
            assertEquals(0, userData.readUnsignedIntLE());
            assertEquals(0, userData.readUnsignedIntLE());
            assertEquals(0, userData.readableBytes());
            assertEquals(1, buffer.readUnsignedByte());

            assertEquals(5, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(5, buffer.readUnsignedByte());
            assertEquals(1, buffer.readUnsignedByte());
            assertSlot(buffer, ContainerEnumName.CraftingInputContainer, 32, 7);

            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(0, buffer.readUnsignedByte());
            assertEquals(4, buffer.readUnsignedByte());
            assertSlot(buffer, ContainerEnumName.CreatedOutputContainer, 50, -1);
            assertSlot(buffer, ContainerEnumName.InventoryContainer, 9, 0);

            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(-1, buffer.readIntLE());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void craftingRequestsRejectMalformedCountsAndEmptyResults() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryStackRequest.CraftRecipe(123, 0));
        assertThrows(IllegalArgumentException.class, () -> new InventoryStackRequest.CraftResultsDeprecated(List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> new InventoryStackRequest.Consume(65,
            new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CraftingInputContainer, null), 32, 7)
        ));
    }

    private static void assertSlot(final ByteBuf buffer, final ContainerEnumName container, final int slot, final int stackNetworkId) {
        assertEquals(container, ContainerEnumName.getByValue(buffer.readByte()));
        assertEquals(false, buffer.readBoolean());
        assertEquals(slot, buffer.readUnsignedByte());
        assertEquals(stackNetworkId, buffer.readIntLE());
    }

}
