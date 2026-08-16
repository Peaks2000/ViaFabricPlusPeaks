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

import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.libs.fastutil.ints.Int2ObjectOpenHashMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.raphimc.viabedrock.api.model.BlockState;
import net.raphimc.viabedrock.api.model.container.ChestContainer;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.api.model.container.FurnaceContainer;
import net.raphimc.viabedrock.api.model.container.player.HudContainer;
import net.raphimc.viabedrock.api.model.container.player.OffhandContainer;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.protocol.data.enums.DyeColor;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.DataItemType;
import net.raphimc.viabedrock.protocol.data.generated.bedrock.CustomBlockTags;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.model.GameRule;
import net.raphimc.viabedrock.protocol.model.InventoryStackRequest;
import net.raphimc.viabedrock.protocol.model.Position2f;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.packet.WorldEffectPackets;
import net.raphimc.viabedrock.protocol.rewriter.blockentity.BedBlockEntityRewriter;
import net.raphimc.viabedrock.protocol.rewriter.blockentity.ShulkerBoxBlockEntityRewriter;
import net.raphimc.viabedrock.protocol.storage.BlockPlacementPredictionTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import net.raphimc.viabedrock.protocol.types.entitydata.EntityDataType;
import net.raphimc.viabedrock.protocol.types.inventory.InventoryStackRequestType;
import net.raphimc.viabedrock.protocol.types.item.BedrockItemType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class Bedrock12640WireTypesTest {

    @Test
    public void placementAcknowledgementsWaitForAuthoritativeUpdatesInSequenceOrder() {
        final BlockPlacementPredictionTracker tracker = new BlockPlacementPredictionTracker(100L);
        final BlockPosition firstClicked = new BlockPosition(1, 64, 1);
        final BlockPosition firstPlaced = new BlockPosition(1, 65, 1);
        final BlockPosition secondClicked = new BlockPosition(2, 64, 1);
        final BlockPosition secondPlaced = new BlockPosition(2, 65, 1);

        tracker.track(1, firstClicked, firstPlaced, 0L);
        assertEquals(-1, tracker.requestAcknowledgement(1));
        tracker.track(2, secondClicked, secondPlaced, 1L);
        assertEquals(-1, tracker.requestAcknowledgement(2));
        assertEquals(-1, tracker.requestAcknowledgement(3));

        assertFalse(tracker.confirm(firstClicked));
        assertTrue(tracker.confirm(secondPlaced));
        assertEquals(-1, tracker.pollAcknowledgement());
        assertTrue(tracker.confirm(firstPlaced));
        assertEquals(3, tracker.pollAcknowledgement());
    }

    @Test
    public void placementTimeoutResyncsBothCandidatePositionsBeforeAcknowledging() {
        final BlockPlacementPredictionTracker tracker = new BlockPlacementPredictionTracker(100L);
        final BlockPosition clicked = new BlockPosition(4, 70, 4);
        final BlockPosition adjacent = new BlockPosition(4, 71, 4);

        tracker.track(7, clicked, adjacent, 10L);
        assertEquals(6, tracker.requestAcknowledgement(7));
        assertEquals(List.of(), tracker.expire(109L).resyncPositions());
        assertEquals(-1, tracker.pollAcknowledgement());

        assertEquals(List.of(clicked, adjacent), tracker.expire(110L).resyncPositions());
        assertEquals(7, tracker.pollAcknowledgement());
    }

    @Test
    public void boatRotationUsesBedrockQuarterTurnOffset() {
        final Position2f rotation = ClientPlayerEntity.bedrockBoatRotation(100F, 12F);
        assertEquals(12F, rotation.x());
        assertEquals(-170F, rotation.y());
    }

    @Test
    public void bedColourRewritePreservesStateProperties() {
        final BlockState whiteBed = new BlockState("white_bed", Map.of(
            "facing", "west",
            "occupied", "true",
            "part", "head"
        ));

        final BlockState blueBed = BedBlockEntityRewriter.coloredBedState(whiteBed, DyeColor.BLUE);
        assertEquals("blue_bed", blueBed.identifier());
        assertEquals(whiteBed.properties(), blueBed.properties());
    }

    @Test
    public void levelEventPositionsFloorNegativeCoordinates() {
        final BlockPosition position = WorldEffectPackets.levelEventBlockPosition(new Position3f(-4.5F, 72.5F, -9.5F));
        assertEquals(-5, position.x());
        assertEquals(72, position.y());
        assertEquals(-10, position.z());
    }

    @Test
    public void dyedParticleStatesShareTheirBlockFamily() {
        assertEquals("wool", WorldEffectPackets.particleBlockFamily("light_blue_wool"));
        assertEquals("wool", WorldEffectPackets.particleBlockFamily("cyan_wool"));
        assertEquals("stone", WorldEffectPackets.particleBlockFamily("stone"));

        final BlockState lightBlue = new BlockState("light_blue_wool", Map.of());
        final BlockState cyan = new BlockState("cyan_wool", Map.of());
        assertEquals(true, WorldEffectPackets.sameParticleBlockFamily(lightBlue, cyan));
        assertEquals(false, WorldEffectPackets.sameParticleBlockFamily(lightBlue, new BlockState("cyan_concrete", Map.of())));
    }

    @Test
    public void shulkerFacingUsesBedrockBlockEntityDirection() {
        final BlockState base = new BlockState("light_blue_shulker_box", Map.of("facing", "down"));
        assertEquals("up", ShulkerBoxBlockEntityRewriter.orientedState(base, 1).properties().get("facing"));
        assertEquals("north", ShulkerBoxBlockEntityRewriter.orientedState(base, 2).properties().get("facing"));
        assertEquals("east", ShulkerBoxBlockEntityRewriter.orientedState(base, 5).properties().get("facing"));
        assertEquals("up", ShulkerBoxBlockEntityRewriter.orientedState(base, 99).properties().get("facing"));
    }

    @Test
    public void offhandUsesDistinctStackRequestSlot() {
        final OffhandContainer offhand = new OffhandContainer(null);
        assertEquals(45, offhand.javaSlot(0));
        assertEquals(1, offhand.stackRequestSlot(0));
        assertEquals(0, offhand.stackResponseSlot(1));
    }

    @Test
    public void genericBlockContainersUseTheirStackRequestNames() {
        final ChestContainer barrel = new ChestContainer(null, (byte) 1, null, null, 27, CustomBlockTags.BARREL);
        final ChestContainer shulker = new ChestContainer(null, (byte) 2, null, null, 27, CustomBlockTags.SHULKER_BOX);
        final ChestContainer enderChest = new ChestContainer(null, (byte) 3, null, null, 27, CustomBlockTags.ENDER_CHEST);

        assertEquals(ContainerEnumName.BarrelContainer, barrel.getFullContainerName(0).name());
        assertEquals(ContainerEnumName.ShulkerBoxContainer, shulker.getFullContainerName(0).name());
        assertEquals(ContainerEnumName.LevelEntityContainer, enderChest.getFullContainerName(0).name());
        assertEquals(true, barrel.isValidBlockTag(CustomBlockTags.BARREL));
        assertEquals(true, shulker.isValidBlockTag(CustomBlockTags.SHULKER_BOX));
        assertEquals(true, enderChest.isValidBlockTag(CustomBlockTags.ENDER_CHEST));
    }

    @Test
    public void furnaceSlotsUseSpecializedStackRequestNames() {
        final FurnaceContainer furnace = new FurnaceContainer(null, (byte) 1, ContainerType.FURNACE, null, null);
        final FurnaceContainer blastFurnace = new FurnaceContainer(null, (byte) 2, ContainerType.BLAST_FURNACE, null, null);
        final FurnaceContainer smoker = new FurnaceContainer(null, (byte) 3, ContainerType.SMOKER, null, null);

        assertEquals(ContainerEnumName.FurnaceIngredientContainer, furnace.getFullContainerName(0).name());
        assertEquals(ContainerEnumName.BlastFurnaceIngredientContainer, blastFurnace.getFullContainerName(0).name());
        assertEquals(ContainerEnumName.SmokerIngredientContainer, smoker.getFullContainerName(0).name());
        assertEquals(ContainerEnumName.FurnaceFuelContainer, furnace.getFullContainerName(1).name());
        assertEquals(ContainerEnumName.FurnaceResultContainer, furnace.getFullContainerName(2).name());
        assertEquals(true, furnace.isValidBlockTag(CustomBlockTags.FURNACE));
    }

    @Test
    public void requestPredictionDoesNotPublishEquipmentBeforeHostResponse() {
        final SlotChangeCountingContainer container = new SlotChangeCountingContainer();
        container.setPredictedItem(0, new BedrockItem(1));
        assertEquals(0, container.slotChanges);

        container.setItem(0, BedrockItem.empty());
        assertEquals(1, container.slotChanges);
    }

    @Test
    public void craftingTableHudSlotsUseCraftingInputContainer() {
        final HudContainer hud = new HudContainer(null);
        for (int slot = 28; slot <= 40; slot++) {
            assertEquals(ContainerEnumName.CraftingInputContainer, hud.getFullContainerName(slot).name());
        }
        assertEquals(ContainerEnumName.CursorContainer, hud.getFullContainerName(0).name());
        assertEquals(ContainerEnumName.CraftingOutputPreviewContainer, hud.getFullContainerName(50).name());
    }

    private static final class SlotChangeCountingContainer extends Container {

        private int slotChanges;

        private SlotChangeCountingContainer() {
            super(null, (byte) 0, ContainerType.INVENTORY, null, null, 1);
        }

        @Override
        protected void onSlotChanged(final int slot, final BedrockItem oldItem, final BedrockItem newItem) {
            this.slotChanges++;
        }
    }

    @Test
    public void emptyItemInstanceConsumesCompleteProtocol2168Record() {
        final BedrockItemType itemType = new BedrockItemType(0, new Int2ObjectOpenHashMap<>());
        final ByteBuf buffer = Unpooled.buffer();
        try {
            BedrockTypes.VAR_INT.write(buffer, 0); // runtime id (air)
            buffer.writeShortLE(0); // count
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 0); // auxiliary value
            BedrockTypes.VAR_INT.write(buffer, 0); // block runtime id
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 0); // user-data length
            buffer.writeByte(0x5A); // next field

            final BedrockItem item = itemType.read(buffer);
            assertEquals(true, item.isEmpty());
            assertEquals(0x5A, buffer.readUnsignedByte());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void emptyItemInstanceWritesCompleteProtocol2168Record() {
        final BedrockItemType itemType = new BedrockItemType(0, new Int2ObjectOpenHashMap<>());
        final ByteBuf buffer = Unpooled.buffer();
        try {
            itemType.write(buffer, BedrockItem.empty());

            assertEquals(0, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(0, buffer.readUnsignedShortLE());
            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(0, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

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
            new InventoryStackRequest.Create(32),
            new InventoryStackRequest.Take(4,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CreatedOutputContainer, null), 50, -1),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.InventoryContainer, null), 9, 0)
            )
        ));
        final ByteBuf buffer = Unpooled.buffer();
        try {
            new InventoryStackRequestType(id -> id == 1 ? "minecraft:stone" : null).write(buffer, request);

            assertEquals(-1, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(5, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));

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

            assertEquals(6, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(6, buffer.readUnsignedByte());
            assertEquals(32, buffer.readUnsignedByte());

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

    @Test
    public void playerCraftingGridPlacementMatchesProtocol2168CerealLayout() {
        final InventoryStackRequest request = new InventoryStackRequest(-1, List.of(
            new InventoryStackRequest.Place(1,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CursorContainer, null), 0, 7),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CraftingInputContainer, null), 28, 0)
            )
        ));
        final ByteBuf buffer = Unpooled.buffer();
        try {
            new InventoryStackRequestType(id -> null).write(buffer, request);

            assertEquals(-1, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(1, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, buffer.readUnsignedByte());
            assertEquals(1, buffer.readUnsignedByte());
            assertSlot(buffer, ContainerEnumName.CursorContainer, 0, 7);
            assertSlot(buffer, ContainerEnumName.CraftingInputContainer, 28, 0);
            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(-1, buffer.readIntLE());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void rightDragUsesSequentialCursorStackIdsAcrossCraftingSlots() {
        final InventoryStackRequest request = new InventoryStackRequest(-1, List.of(
            new InventoryStackRequest.Place(1,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CursorContainer, null), 0, 7),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CraftingInputContainer, null), 28, 0)
            ),
            new InventoryStackRequest.Place(1,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CursorContainer, null), 0, -1),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CraftingInputContainer, null), 29, 0)
            )
        ));
        final ByteBuf buffer = Unpooled.buffer();
        try {
            new InventoryStackRequestType(id -> null).write(buffer, request);

            assertEquals(-1, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(2, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertPlace(buffer, 1, ContainerEnumName.CursorContainer, 0, 7,
                ContainerEnumName.CraftingInputContainer, 28, 0);
            assertPlace(buffer, 1, ContainerEnumName.CursorContainer, 0, -1,
                ContainerEnumName.CraftingInputContainer, 29, 0);
            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(-1, buffer.readIntLE());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void pickupAllUsesSequentialCursorStackIds() {
        final InventoryStackRequest request = new InventoryStackRequest(-1, List.of(
            new InventoryStackRequest.Take(12,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.InventoryContainer, null), 10, 41),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CursorContainer, null), 0, 7)
            ),
            new InventoryStackRequest.Take(20,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.HotbarContainer, null), 2, 42),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CursorContainer, null), 0, -1)
            )
        ));
        final ByteBuf buffer = Unpooled.buffer();
        try {
            new InventoryStackRequestType(id -> null).write(buffer, request);

            assertEquals(-1, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(2, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertTake(buffer, 12, ContainerEnumName.InventoryContainer, 10, 41,
                ContainerEnumName.CursorContainer, 0, 7);
            assertTake(buffer, 20, ContainerEnumName.HotbarContainer, 2, 42,
                ContainerEnumName.CursorContainer, 0, -1);
            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(-1, buffer.readIntLE());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void creativeRequestsUseCraftCreativeAndDestroyCerealVariants() {
        final InventoryStackRequest request = new InventoryStackRequest(-1, List.of(
            new InventoryStackRequest.CraftCreative(321, 1),
            new InventoryStackRequest.Destroy(64, new InventoryStackRequest.Slot(
                new FullContainerName(ContainerEnumName.HotbarContainer, null), 0, 7
            ))
        ));
        final ByteBuf buffer = Unpooled.buffer();
        try {
            new InventoryStackRequestType(id -> null).write(buffer, request);

            assertEquals(-1, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(2, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));

            assertEquals(12, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(14, buffer.readUnsignedByte());
            assertEquals(321, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, buffer.readUnsignedByte());

            assertEquals(4, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(4, buffer.readUnsignedByte());
            assertEquals(64, buffer.readUnsignedByte());
            assertSlot(buffer, ContainerEnumName.HotbarContainer, 0, 7);

            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(-1, buffer.readIntLE());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void creativeOffhandTransferStagesThroughCursor() {
        final InventoryStackRequest request = new InventoryStackRequest(-1, List.of(
            new InventoryStackRequest.Take(1,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CreatedOutputContainer, null), 50, -1),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CursorContainer, null), 0, 0)
            ),
            new InventoryStackRequest.Place(1,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CursorContainer, null), 0, -1),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.OffhandContainer, null), 1, 0)
            )
        ));
        final ByteBuf buffer = Unpooled.buffer();
        try {
            new InventoryStackRequestType(id -> null).write(buffer, request);

            assertEquals(-1, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(2, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertTake(buffer, 1, ContainerEnumName.CreatedOutputContainer, 50, -1,
                ContainerEnumName.CursorContainer, 0, 0);
            assertPlace(buffer, 1, ContainerEnumName.CursorContainer, 0, -1,
                ContainerEnumName.OffhandContainer, 1, 0);
            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(-1, buffer.readIntLE());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static void assertPlace(final ByteBuf buffer, final int count,
                                    final ContainerEnumName sourceContainer, final int sourceSlot, final int sourceStackNetworkId,
                                    final ContainerEnumName destinationContainer, final int destinationSlot, final int destinationStackNetworkId) {
        assertEquals(1, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
        assertEquals(1, buffer.readUnsignedByte());
        assertEquals(count, buffer.readUnsignedByte());
        assertSlot(buffer, sourceContainer, sourceSlot, sourceStackNetworkId);
        assertSlot(buffer, destinationContainer, destinationSlot, destinationStackNetworkId);
    }

    private static void assertTake(final ByteBuf buffer, final int count,
                                   final ContainerEnumName sourceContainer, final int sourceSlot, final int sourceStackNetworkId,
                                   final ContainerEnumName destinationContainer, final int destinationSlot, final int destinationStackNetworkId) {
        assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
        assertEquals(0, buffer.readUnsignedByte());
        assertEquals(count, buffer.readUnsignedByte());
        assertSlot(buffer, sourceContainer, sourceSlot, sourceStackNetworkId);
        assertSlot(buffer, destinationContainer, destinationSlot, destinationStackNetworkId);
    }

    private static void assertSlot(final ByteBuf buffer, final ContainerEnumName container, final int slot, final int stackNetworkId) {
        assertEquals(container, ContainerEnumName.getByValue(buffer.readByte()));
        assertEquals(false, buffer.readBoolean());
        assertEquals(slot, buffer.readUnsignedByte());
        assertEquals(stackNetworkId, buffer.readIntLE());
    }

}
