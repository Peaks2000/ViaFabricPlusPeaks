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
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.DataItemType;
import net.raphimc.viabedrock.protocol.model.GameRule;
import net.raphimc.viabedrock.protocol.model.InventoryStackRequest;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import net.raphimc.viabedrock.protocol.types.entitydata.EntityDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    public void craftingActionsKeepMappedIdsSeparateFromCerealDiscriminators() {
        final InventoryStackRequest.CraftRecipe craftRecipe = new InventoryStackRequest.CraftRecipe(123, 1);
        assertEquals(10, craftRecipe.mappedType());
        assertEquals(12, craftRecipe.type().getValue());

        final InventoryStackRequest.CraftResults craftResults = new InventoryStackRequest.CraftResults(java.util.List.of(), 1);
        assertEquals(17, craftResults.mappedType());
        assertEquals(19, craftResults.type().getValue());
    }

}
