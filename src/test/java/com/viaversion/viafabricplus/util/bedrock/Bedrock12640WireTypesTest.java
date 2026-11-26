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

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.VillagerData;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.libs.fastutil.ints.Int2ObjectOpenHashMap;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.raphimc.viabedrock.api.chunk.BedrockBlockEntity;
import net.raphimc.viabedrock.api.chunk.section.BedrockChunkSectionImpl;
import net.raphimc.viabedrock.api.model.BlockState;
import net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter;
import net.raphimc.viabedrock.api.model.container.ChestContainer;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.api.model.container.CraftingTableContainer;
import net.raphimc.viabedrock.api.model.container.FurnaceContainer;
import net.raphimc.viabedrock.api.model.container.MerchantContainer;
import net.raphimc.viabedrock.api.model.container.player.HudContainer;
import net.raphimc.viabedrock.api.model.container.player.OffhandContainer;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.api.model.entity.LivingEntity;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.content.InMemoryContent;
import net.raphimc.viabedrock.api.util.MathUtil;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.enums.DyeColor;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorDataIds;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.LevelEvent;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.DataItemType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerAuthInputData;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.GameMode;
import net.raphimc.viabedrock.protocol.data.generated.bedrock.CustomBlockTags;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.BedrockTradeOffer;
import net.raphimc.viabedrock.protocol.model.CommandOriginData;
import net.raphimc.viabedrock.protocol.model.EntityAttribute;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.model.GameRule;
import net.raphimc.viabedrock.protocol.model.InventoryStackRequest;
import net.raphimc.viabedrock.protocol.model.Position2f;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.model.SkinData;
import net.raphimc.viabedrock.protocol.packet.WorldEffectPackets;
import net.raphimc.viabedrock.protocol.packet.ClientPlayerPackets;
import net.raphimc.viabedrock.protocol.packet.EntityPackets;
import net.raphimc.viabedrock.protocol.packet.InventoryPackets;
import net.raphimc.viabedrock.protocol.rewriter.EntityMetadataRewriter;
import net.raphimc.viabedrock.protocol.rewriter.ResourcePackRewriter;
import net.raphimc.viabedrock.protocol.rewriter.resourcepack.CustomEntityResourceRewriter;
import net.raphimc.viabedrock.protocol.rewriter.blockentity.BedBlockEntityRewriter;
import net.raphimc.viabedrock.protocol.rewriter.blockentity.BrewingStandBlockEntityRewriter;
import net.raphimc.viabedrock.protocol.rewriter.blockentity.ShulkerBoxBlockEntityRewriter;
import net.raphimc.viabedrock.protocol.storage.BlockPlacementPredictionTracker;
import net.raphimc.viabedrock.protocol.storage.BreakingTracker;
import net.raphimc.viabedrock.protocol.storage.ChunkTracker;
import net.raphimc.viabedrock.protocol.storage.InventoryRequestTracker;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.storage.MovementPredictionTracker;
import net.raphimc.viabedrock.protocol.storage.PlayerChatDuplicateTracker;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import net.raphimc.viabedrock.protocol.types.entitydata.EntityDataType;
import net.raphimc.viabedrock.protocol.types.entitydata.EntityDataTypesBedrock;
import net.raphimc.viabedrock.protocol.types.inventory.InventoryStackRequestType;
import net.raphimc.viabedrock.protocol.types.item.BedrockItemType;
import net.raphimc.viabedrock.protocol.util.BoundedDiagnosticLimiter;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class Bedrock12640WireTypesTest {

    @Test
    public void fallingBlockSpawnUsesItsBedrockVariantState() {
        final EntityData variant = new EntityData(
            ActorDataIds.VARIANT.getValue(), EntityDataTypesBedrock.INT, 123_456
        );

        assertEquals(123_456, EntityPackets.bedrockFallingBlockState(
            "minecraft:falling_block", new EntityData[]{variant}
        ));
        assertEquals(-1, EntityPackets.bedrockFallingBlockState(
            "minecraft:zombie", new EntityData[]{variant}
        ));
    }

    @Test
    public void visibleExtraLayerFluidReplacesAirButWaterlogsSolids() {
        assertEquals(42, ChunkTracker.visibleLayerBlockState(0, 42, 0));
        assertEquals(7, ChunkTracker.visibleLayerBlockState(7, 42, 0));
        assertEquals(0, ChunkTracker.visibleLayerBlockState(0, 0, 0));
    }

    @Test
    public void allPlayersSleepingAdvancesClientHostedWorldsToDawn() {
        assertTrue(WorldEffectPackets.advancesJavaTimeToDawn(LevelEvent.AllPlayersSleeping));
        assertFalse(WorldEffectPackets.advancesJavaTimeToDawn(LevelEvent.SleepingPlayers));
    }

    @Test
    public void emptyPendingChunkSectionReadsAsAirInsteadOfDisconnecting() {
        assertFalse(ChunkTracker.hasBlockPalette(new BedrockChunkSectionImpl()));
        assertFalse(ChunkTracker.hasBlockPalette(null));
    }

    @Test
    public void isolatedServerRouteKeepsItsRouteIdentityOffTheBedrockWire() {
        assertEquals(2168, new BedrockProtocol(2168).wireProtocolVersion(1001));
        assertEquals(2169, new BedrockProtocol().wireProtocolVersion(2169));
    }

    @Test
    public void brewingStandWithoutAnItemsListStillOpensSafely() {
        final CompoundTag bedrockTag = new CompoundTag();
        final CompoundTag javaTag = new BrewingStandBlockEntityRewriter()
                .toJava(null, new BedrockBlockEntity(bedrockTag)).tag();

        assertTrue(javaTag.getListTag("Items", CompoundTag.class).isEmpty());
    }

    @Test
    public void creatorPlayerSkinConsumesTheProtocol2168Layout() {
        final ByteBuf buffer = Unpooled.buffer();
        try {
            BedrockTypes.STRING.write(buffer, "creator-skin");
            BedrockTypes.STRING.write(buffer, "{\"geometry\":{\"default\":\"geometry.humanoid.customSlim\"}}");
            BedrockTypes.IMAGE.write(buffer, new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB));

            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 1); // animations
            BedrockTypes.IMAGE.write(buffer, null);
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 2); // animation type
            buffer.writeFloatLE(3.5F);
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 1); // expression type

            BedrockTypes.IMAGE.write(buffer, null); // cape
            BedrockTypes.STRING.write(buffer, "geometry-data");
            BedrockTypes.STRING.write(buffer, "1.21.0");
            BedrockTypes.STRING.write(buffer, "animation-data");
            BedrockTypes.STRING.write(buffer, "cape-id");
            BedrockTypes.STRING.write(buffer, "full-skin-id");
            buffer.writeByte(0); // slim arm size
            buffer.writeIntLE(0x78123456);

            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 1); // persona pieces
            BedrockTypes.STRING.write(buffer, "piece-id");
            buffer.writeIntLE(14); // hair
            BedrockTypes.UUID.write(buffer, UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));
            buffer.writeBoolean(false);
            BedrockTypes.STRING.write(buffer, "product-id");

            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 1); // persona tints
            BedrockTypes.STRING.write(buffer, "persona_hair");
            buffer.writeIntLE(0x12010203);
            buffer.writeIntLE(0xFF040506);
            buffer.writeIntLE(0xFF070809);
            buffer.writeIntLE(0xFF0A0B0C);

            buffer.writeBoolean(true); // premium
            buffer.writeBoolean(true); // persona
            buffer.writeBoolean(false); // cape on classic
            buffer.writeBoolean(false); // primary user
            buffer.writeBoolean(true); // overriding appearance
            BedrockTypes.STRING.write(buffer, "true"); // trusted
            BedrockTypes.STRING.write(buffer, "profile-hash");
            BedrockTypes.STRING.write(buffer, "new-skin-name"); // next PLAYER_SKIN field

            final SkinData skin = BedrockTypes.SKIN.read(buffer);
            assertEquals("creator-skin", skin.skinId());
            assertEquals(128, skin.skinData().getWidth());
            assertEquals(1, skin.animations().size());
            assertEquals(2, skin.animations().getFirst().type());
            assertEquals("Slim", skin.armSize());
            assertEquals("#78123456", skin.skinColor());
            assertEquals("#12010203", skin.tintColors().getFirst().colors().getFirst());
            assertEquals("Hair", skin.personaPieces().getFirst().type());
            assertEquals("persona_hair", skin.tintColors().getFirst().type());
            assertTrue(skin.persona());
            assertTrue(skin.overridingPlayerAppearance());
            assertEquals("new-skin-name", BedrockTypes.STRING.read(buffer));
            assertEquals(0, buffer.readableBytes());
            buffer.clear();
            BedrockTypes.SKIN.write(buffer, skin);
            BedrockTypes.STRING.write(buffer, "after-reencoded-skin");
            final SkinData rewritten = BedrockTypes.SKIN.read(buffer);
            assertEquals(skin.skinColor(), rewritten.skinColor());
            assertEquals(skin.personaPieces(), rewritten.personaPieces());
            assertEquals(skin.tintColors(), rewritten.tintColors());
            assertEquals("after-reencoded-skin", BedrockTypes.STRING.read(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void repeatedWorldDiagnosticsAreBoundedPerConnection() {
        final BoundedDiagnosticLimiter limiter = new BoundedDiagnosticLimiter(2);

        assertTrue(limiter.shouldLog(11L));
        assertFalse(limiter.shouldLog(11L));
        assertTrue(limiter.shouldLog(12L));
        assertTrue(limiter.isFull());
        assertFalse(limiter.shouldLog(13L));
    }

    @Test
    public void immediateIdenticalPlayerChatCopiesAreSuppressedNarrowly() {
        final PlayerChatDuplicateTracker tracker = new PlayerChatDuplicateTracker(100L);

        assertFalse(tracker.shouldSuppress("Alex", "hello", false, 1_000L));
        assertTrue(tracker.shouldSuppress("Alex", "hello", false, 1_050L));
        assertFalse(tracker.shouldSuppress("Alex", "hello", false, 1_101L));
        assertFalse(tracker.shouldSuppress("Steve", "hello", false, 1_110L));
        assertFalse(tracker.shouldSuppress("Steve", "different", false, 1_120L));
        assertFalse(tracker.shouldSuppress("Steve", "hello", false, 1_130L));
        assertFalse(tracker.shouldSuppress("Steve", "hello", true, 1_140L));
    }

    @Test
    public void remotePlayerRespawnRestoresHealthFromTheDeadSnapshot() {
        final EntityAttribute deadHealth = new EntityAttribute("minecraft:health", 0F, 0F, 40F);
        final EntityAttribute restoredHealth = LivingEntity.respawnHealthAttribute(deadHealth);

        assertEquals(0F, deadHealth.currentValue());
        assertEquals(40F, restoredHealth.currentValue());
    }

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

        assertFalse(tracker.confirm(firstClicked, false));
        assertTrue(tracker.confirm(secondPlaced, false));
        assertEquals(-1, tracker.pollAcknowledgement());
        assertTrue(tracker.confirm(firstPlaced, false));
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
    public void chainedPlacementUsesPendingSupportOnlyUntilTheServerCanReportItsState() {
        final BlockPlacementPredictionTracker tracker = new BlockPlacementPredictionTracker(50);
        final BlockPosition clicked = new BlockPosition(0, 99, 12);
        final BlockPosition placed = new BlockPosition(0, 99, 13);
        final int air = -604749536;
        final int stone = -2144268767;

        tracker.trackPlacement(1, clicked, placed, stone, 100);
        assertEquals(stone, tracker.predictedBlockState(placed, air, air, 110));
        assertEquals(750, tracker.predictedBlockState(placed, 750, air, 110));
        assertEquals(air, tracker.predictedBlockState(clicked, air, air, 110));
        assertEquals(air, tracker.predictedBlockState(placed, air, air, 150));
        tracker.confirm(placed, false);
        assertEquals(air, tracker.predictedBlockState(placed, air, air, 120));

        tracker.trackPlacement(2, clicked, placed, stone, 130);
        tracker.clearPredictionsForDimensionChange();
        assertEquals(air, tracker.predictedBlockState(placed, air, air, 140));
    }

    @Test
    public void instantBreakEffectDoesNotCreateAPendingBlockUpdate() {
        final BlockPlacementPredictionTracker tracker = new BlockPlacementPredictionTracker(50);
        final BlockPosition position = new BlockPosition(0, 101, 1);

        tracker.rememberInstantBreakSound(position, 100);

        assertFalse(tracker.isPendingBreaking(position));
        assertTrue(tracker.consumeLocalBreakSound(position, 101));
        assertFalse(tracker.consumeLocalBreakSound(position, 102));
    }

    @Test
    public void localBreakEffectIsMatchedOnceEvenAfterAcknowledgement() {
        final var position = new BlockPosition(1, 61, -2);
        final var other = new BlockPosition(2, 61, -2);
        final var tracker = new BlockPlacementPredictionTracker(200L);
        tracker.trackBreaking(1, position, 0L);
        assertTrue(tracker.confirm(position, true));
        assertEquals(1, tracker.requestAcknowledgement(1));

        assertFalse(tracker.consumeLocalBreakSound(other, 40L));
        assertTrue(tracker.consumeLocalBreakSound(position, 40L));
        assertFalse(tracker.consumeLocalBreakSound(position, 41L));

        tracker.trackBreaking(2, position, 100L);
        assertFalse(tracker.consumeLocalBreakSound(position, 300L));
        tracker.trackBreaking(3, position, 310L);
        tracker.clearPredictionsForDimensionChange();
        assertFalse(tracker.consumeLocalBreakSound(position, 311L));
    }

    @Test
    public void breakingAcknowledgementWaitsForItsAuthoritativeBlockUpdate() {
        final BlockPlacementPredictionTracker tracker = new BlockPlacementPredictionTracker(100L);
        final BlockPosition broken = new BlockPosition(4, 70, 4);

        tracker.trackBreaking(12, broken, 0L);
        assertEquals(11, tracker.requestAcknowledgement(12));
        assertTrue(tracker.hasPendingBreaking());
        assertTrue(tracker.isPendingBreaking(broken));
        assertTrue(tracker.shouldSuppressBreakingReassertion(broken, false));
        assertFalse(tracker.shouldSuppressBreakingReassertion(broken, true));
        assertFalse(tracker.confirm(broken, false));
        assertEquals(-1, tracker.pollAcknowledgement());
        assertTrue(tracker.isPendingBreaking(broken));
        assertTrue(tracker.confirm(broken, true));
        assertFalse(tracker.hasPendingBreaking());
        assertFalse(tracker.isPendingBreaking(broken));
        assertFalse(tracker.shouldSuppressBreakingReassertion(broken, false));
        assertEquals(12, tracker.pollAcknowledgement());
    }

    @Test
    public void placementAndBreakingConfirmOnlyTheirPredictedBlockState() {
        final BlockPlacementPredictionTracker tracker = new BlockPlacementPredictionTracker(100L);
        final BlockPosition placed = new BlockPosition(8, 70, 8);
        final BlockPosition broken = new BlockPosition(9, 70, 8);

        tracker.track(20, new BlockPosition(8, 69, 8), placed, 0L);
        tracker.trackBreaking(21, broken, 1L);

        assertFalse(tracker.confirm(placed, true));
        assertFalse(tracker.confirm(broken, false));
        assertTrue(tracker.confirm(placed, false));
        assertEquals(20, tracker.requestAcknowledgement(21));
        assertTrue(tracker.confirm(broken, true));
        assertEquals(21, tracker.pollAcknowledgement());
    }

    @Test
    public void localCrackingSuppressionUsesFlooredBedrockPosition() {
        final BlockPosition localTarget = new BlockPosition(-5, 72, -10);

        assertTrue(BreakingTracker.isSameBlock(localTarget, new Position3f(-4.5F, 72.9F, -9.5F)));
        assertFalse(BreakingTracker.isSameBlock(localTarget, new Position3f(-3.9F, 72.9F, -9.5F)));
    }

    @Test
    public void repeatedMiningSwingsDoNotBecomeBedrockAttackAnimations() {
        assertEquals(new ClientPlayerPackets.SwingHandling(false, false, false),
            ClientPlayerPackets.swingHandling(true, false, true));
        assertEquals(new ClientPlayerPackets.SwingHandling(false, true, false),
            ClientPlayerPackets.swingHandling(true, false, false));
        assertEquals(new ClientPlayerPackets.SwingHandling(false, false, false),
            ClientPlayerPackets.swingHandling(false, true, true));
        assertEquals(new ClientPlayerPackets.SwingHandling(false, false, false),
            ClientPlayerPackets.swingHandling(false, true, false));
        assertEquals(new ClientPlayerPackets.SwingHandling(true, false, true),
            ClientPlayerPackets.swingHandling(false, false, true));
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
    public void creativeFailureKeepsAnIndependentCursorRollbackItem() {
        final StructuredItem cursor = new StructuredItem(123, 4);
        final InventoryRequestTracker.PendingRequest pending = new InventoryRequestTracker.PendingRequest(
            Map.of(), List.of(), true, cursor
        );

        cursor.setAmount(1);
        assertEquals(4, pending.javaCursorOnFailure().amount());
    }

    @Test
    public void creativeFailureUsesAnAtomicFullContentCursorRollback() {
        assertEquals(ClientboundPackets26_3.CONTAINER_SET_CONTENT, PacketFactory.javaCreativeCursorRollbackPacketType());
    }

    @Test
    public void creativeFailureResyncDoesNotQueueAFullCursorErasingRefresh() {
        final InventoryTracker tracker = new InventoryTracker(null);

        tracker.schedulePlayerInventoryResync(false);

        assertFalse(tracker.isJavaInventoryRefreshPending());
        tracker.schedulePlayerInventoryResync(true);
        assertTrue(tracker.isJavaInventoryRefreshPending());
    }

    @Test
    public void dimensionChangeFinishesPendingContainerCloseWithoutSendingItAgain() {
        final InventoryTracker tracker = new InventoryTracker(null);
        final Container closing = new SlotChangeCountingContainer();
        tracker.setCurrentContainer(closing);
        tracker.markPendingClose(closing);
        assertTrue(tracker.isContainerOpen());
        assertNull(tracker.getCurrentContainer());
        assertSame(closing, tracker.getPendingCloseContainer());

        tracker.setCurrentContainerClosed(true);

        assertFalse(tracker.isContainerOpen());
        assertNull(tracker.getPendingCloseContainer());
        final Container next = new SlotChangeCountingContainer();
        tracker.setCurrentContainer(next);
        assertSame(next, tracker.getCurrentContainer());
    }

    @Test
    public void creativePlayerTransactionsPreserveTheJavaOwnedCursor() {
        assertTrue(InventoryPackets.shouldPreserveCreativeCursor(GameMode.CREATIVE, true));
        assertFalse(InventoryPackets.shouldPreserveCreativeCursor(GameMode.SURVIVAL, true));
        assertFalse(InventoryPackets.shouldPreserveCreativeCursor(GameMode.CREATIVE, false));
    }

    @Test
    public void creativeHudRefreshCannotEraseARejectedOffhandCursor() {
        assertTrue(InventoryPackets.shouldSuppressCreativeHudFullRefresh(GameMode.CREATIVE, true, false));
        assertFalse(InventoryPackets.shouldSuppressCreativeHudFullRefresh(GameMode.SURVIVAL, true, false));
        assertFalse(InventoryPackets.shouldSuppressCreativeHudFullRefresh(GameMode.CREATIVE, false, false));
        assertFalse(InventoryPackets.shouldSuppressCreativeHudFullRefresh(GameMode.CREATIVE, true, true));
    }

    @Test
    public void localBlockPlacementSoundEchoIsSuppressedWithoutMutingOtherPlayers() {
        assertTrue(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound("place", 42L, 42L));
        assertFalse(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound("place", 43L, 42L));
        assertFalse(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound("step", 42L, 42L));
        assertFalse(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound("place", 0L, 0L));
        assertFalse(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound("place", -1L, 42L));

        final BlockPlacementPredictionTracker tracker = new BlockPlacementPredictionTracker(100L);
        final BlockPosition clicked = new BlockPosition(4, 63, 7);
        final BlockPosition placed = new BlockPosition(4, 64, 7);
        tracker.track(1, clicked, placed, 0L);
        assertTrue(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound(
            "place", -1L, 42L, clicked, tracker, 1L
        ));
        assertFalse(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound(
            "place", -1L, 42L, placed, tracker, 2L
        ));

        tracker.track(2, clicked, placed, 3L);
        assertFalse(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound(
            "place", 0L, 42L, new BlockPosition(5, 63, 7), tracker, 4L
        ));
        assertFalse(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound(
            "place", 43L, 42L, placed, tracker, 5L
        ));
        assertTrue(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound(
            "place", 0L, 42L, placed, tracker, 6L
        ));

        tracker.track(3, clicked, placed, 7L);
        assertFalse(WorldEffectPackets.shouldSuppressLocalBlockPlaceSound(
            "place", 0L, 42L, placed, tracker, 107L
        ));
    }

    @Test
    public void historicalMovementCorrectionPreservesLaterJumpDisplacement() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(100, new Position3f(0F, 64F, 0F), Position3f.ZERO, true, false, false, 20);
        tracker.record(101, new Position3f(0F, 64.42F, 0F), Position3f.ZERO, false, false, false, 20);
        tracker.record(102, new Position3f(0F, 65F, 0F), Position3f.ZERO, false, false, false, 20);

        final MovementPredictionTracker.Correction correction = tracker.replay(
            100, new Position3f(-0.1F, 64F, 0F), Position3f.ZERO, true,
            102, new Position3f(0F, 65F, 0F), false, false, false, false
        );

        assertTrue(correction.replayed());
        assertEquals(new Position3f(-0.1F, 65F, 0F), correction.position());
        assertFalse(correction.onGround());
    }

    @Test
    public void currentMovementCorrectionUsesAuthoritativeCollisionState() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(200, new Position3f(3F, 70F, 4F), Position3f.ZERO, false, false, false, 20);

        final MovementPredictionTracker.Correction correction = tracker.replay(
            200, new Position3f(3F, 69.9F, 4F), Position3f.ZERO, true,
            200, new Position3f(3F, 70F, 4F), false, false, false, false
        );

        assertEquals(new Position3f(3F, 69.9F, 4F), correction.position());
        assertTrue(correction.onGround());
    }

    @Test
    public void delayedCorrectionCannotReplayAcrossAnAbsolutePositionSync() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(590, new Position3f(0F, 64F, 1F), Position3f.ZERO, true, false, false, 20);
        assertTrue(tracker.acceptsCorrection(590, 591, 20));

        tracker.reset(591);

        assertFalse(tracker.acceptsCorrection(590, 592, 20));
        assertFalse(tracker.acceptsCorrection(591, 592, 20));
        assertTrue(tracker.acceptsCorrection(592, 592, 20));
        assertFalse(tracker.acceptsCorrection(593, 592, 20));
    }

    @Test
    public void repeatedWallCorrectionsDoNotPushTheCurrentPredictionBackAgain() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(300, new Position3f(0.98F, 64F, 0F), Position3f.ZERO, true, true, false, 20);
        tracker.record(301, new Position3f(0.98F, 64F, 0F), Position3f.ZERO, true, true, false, 20);

        final MovementPredictionTracker.Correction firstCorrection = tracker.replay(
            300, new Position3f(0.7F, 64F, 0F), Position3f.ZERO, true,
            302, new Position3f(0.98F, 64F, 0F), true, true, false, false
        );
        assertEquals(new Position3f(0.7F, 64F, 0F), firstCorrection.position());

        // Java has already walked back up to the wall before the next historical
        // correction arrives. It must settle to the server point, not apply the old
        // offset to 0.98 and get shoved farther backwards.
        final MovementPredictionTracker.Correction secondCorrection = tracker.replay(
            301, new Position3f(0.7F, 64F, 0F), Position3f.ZERO, true,
            303, new Position3f(0.98F, 64F, 0F), true, true, false, false
        );
        assertEquals(new Position3f(0.7F, 64F, 0F), secondCorrection.position());
    }

    @Test
    public void doorUsesLowerFacingAndOpenStateAndUpperHinge() {
        final var lower = BlockState.fromString(
            "oak_door[facing=east,half=lower,hinge=left,open=true,powered=false]");
        final var upper = BlockState.fromString(
            "oak_door[facing=north,half=upper,hinge=right,open=false,powered=false]");
        final var rejoined = lower.withProperty("hinge", "right");

        assertEquals(rejoined, BlockStateRewriter.pairDoorHalves(lower, upper));
        assertEquals(rejoined.withProperty("half", "upper"), BlockStateRewriter.pairDoorHalves(upper, lower));
        assertEquals(upper, BlockStateRewriter.pairDoorHalves(upper, lower.withIdentifier("iron_door")));
        assertEquals(upper, BlockStateRewriter.pairDoorHalves(upper, null));
        assertEquals(upper, BlockStateRewriter.pairDoorHalves(upper, upper));
    }

    @Test
    public void walkingIntoADoorEdgeStopsTheRejectedAxisWithoutRewindingTheSlide() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        final Position3f step = new Position3f(0.05F, 0F, 0.04F);
        tracker.record(606, new Position3f(0.85F, 64F, 0.4F), step, true, false, false, 20);

        final var correction = tracker.replay(
            606, new Position3f(0.8F, 64F, 0.4F), new Position3f(0F, -0.0784F, 0.03F), true,
            607, new Position3f(0.9F, 64F, 0.44F), true, false, false, false
        );

        assertEquals(new Position3f(0.8F, 64F, 0.44F), correction.position());
        assertTrue(correction.stopX());
        assertFalse(correction.stopZ());
    }

    @Test
    public void olderGroundCorrectionKeepsANewerJumpOrUphillStep() {
        for (boolean onGround : new boolean[]{false, true}) {
            final MovementPredictionTracker tracker = new MovementPredictionTracker();
            tracker.record(10, new Position3f(0F, 64F, 0F), Position3f.ZERO, true, false, false, 20);
            final var raised = new Position3f(0.2F, onGround ? 64.5F : 64.42F, 0F);
            tracker.record(11, raised, new Position3f(0.2F, 0.42F, 0F), onGround, false, false, 20);

            final var correction = tracker.replay(10, new Position3f(0F, 63.9F, 0F), Position3f.ZERO, true,
                11, raised, onGround, false, false, false);

            assertEquals(raised, correction.position());
            assertTrue(correction.preserveVertical());
            assertFalse(correction.changesClientMovement(raised, onGround));
        }
    }

    @Test
    public void wallCorrectionPreservesMovementAlongTheSurface() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(350, new Position3f(0.98F, 64F, 0F), Position3f.ZERO, true, true, false, 20);
        tracker.record(351, new Position3f(0.98F, 64F, 0.2F), Position3f.ZERO, true, true, false, 20);

        final MovementPredictionTracker.Correction correction = tracker.replay(
            350, new Position3f(0.7F, 64F, 0F), Position3f.ZERO, true,
            352, new Position3f(0.98F, 64F, 0.4F), true, true, false, false
        );

        assertEquals(new Position3f(0.7F, 64F, 0.4F), correction.position());
        assertTrue(correction.replayed());

        // A later zero-delta correction still remembers that X was the rejected
        // axis, without freezing the continued Z movement along the wall.
        final MovementPredictionTracker.Correction repeatedCorrection = tracker.replay(
            351, new Position3f(0.7F, 64F, 0.2F), Position3f.ZERO, true,
            353, new Position3f(0.98F, 64F, 0.6F), true, true, false, false
        );
        assertEquals(new Position3f(0.7F, 64F, 0.6F), repeatedCorrection.position());
        assertTrue(repeatedCorrection.replayed());
    }

    @Test
    public void delayedWallCorrectionRetainsLaterTravelOnAnAxisStillMovingOnTheServer() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(165, new Position3f(0.33F, 101.62F, 8.48F),
            new Position3f(0F, 0F, 0.15F), true, true, false, 20);

        final var correction = tracker.replay(
            165, new Position3f(0.3F, 101.62F, 8.52F), new Position3f(0F, -0.0784F, 0.094F),
            true, 168, new Position3f(0.38F, 101.62F, 8.94F), true, true, false, false
        );

        assertEquals(0.3F, correction.position().x(), 0.0001F);
        assertEquals(8.98F, correction.position().z(), 0.0001F);
        assertTrue(correction.stopX());
        assertFalse(correction.stopZ());
    }

    @Test
    public void cornerCorrectionCanSettleBothHorizontalAxes() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(360, new Position3f(0.98F, 64F, 0.98F), Position3f.ZERO, true, true, false, 20);

        final MovementPredictionTracker.Correction correction = tracker.replay(
            360, new Position3f(0.7F, 64F, 0.7F), Position3f.ZERO, true,
            361, new Position3f(0.98F, 64F, 0.98F), true, true, false, false
        );

        assertEquals(new Position3f(0.7F, 64F, 0.7F), correction.position());
        assertTrue(correction.replayed());
    }

    @Test
    public void legacyMobModelRequiresItsOwnAtlasAndAnActiveClientPack() throws Exception {
        final var pack = new InMemoryContent();
        pack.putString("manifest.json", """
            {"format_version":1,"header":{"name":"Compatibility test",
            "uuid":"4edbc40b-fecb-4ef9-970b-1268c6883e0e","version":[1,0,0]}}
            """);
        pack.putString("models/mobs.json", """
            {"geometry.pigzombie":{"texturewidth":32,"textureheight":32,"bones":[
            {"name":"body","pivot":[0,0,0],"cubes":[{"origin":[-4,0,-4],"size":[8,8,8],"uv":[0,0]}]}]}}
            """);
        final var storageWithoutAtlas = new ResourcePackStorage(List.of(new ResourcePack(pack)));
        ResourcePackRewriter.bedrockToJava(storageWithoutAtlas);
        storageWithoutAtlas.setLoadedOnJavaClient();
        assertNull(CustomEntityResourceRewriter.legacyModel(storageWithoutAtlas, "minecraft:zombie_pigman"));

        pack.putPngImage("textures/entity/pig/pigzombie.png", new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
        final var storage = new ResourcePackStorage(List.of(new ResourcePack(pack)));
        final var converted = ResourcePackRewriter.bedrockToJava(storage);
        assertTrue(converted.contains("assets/viabedrock/items/entities/legacy_minecraft/zombie_pigman.json"));
        assertNull(CustomEntityResourceRewriter.legacyModel(storage, "minecraft:zombie_pigman"));
        storage.setLoadedOnJavaClient();
        assertNotNull(CustomEntityResourceRewriter.legacyModel(storage, "minecraft:zombie_pigman"));
        assertNull(CustomEntityResourceRewriter.legacyModel(storage, "minecraft:zombie"));
    }

    @Test
    public void sprintJumpOffLowerTerrainKeepsTheNewerJumpHeight() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(10, new Position3f(0F, 66F, 0F), Position3f.ZERO, true, false, false, 20);
        tracker.record(11, new Position3f(0F, 65.8F, 0.3F), new Position3f(0F, -0.2F, 0.3F), false, false, false, 20);
        tracker.record(14, new Position3f(0F, 64F, 1.2F), new Position3f(0F, -0.3F, 0.3F), true, false, false, 20);
        final var jumping = new Position3f(0F, 64.42F, 1.55F);
        tracker.record(15, jumping, new Position3f(0F, 0.42F, 0.35F), false, false, false, 20);

        final var correction = tracker.replay(11, new Position3f(0F, 65.6F, 0.35F),
            new Position3f(0F, -0.2F, 0.3F), false, 15, jumping, false, false, false, false);

        assertEquals(64.42F, correction.position().y(), 0.0001F);
        assertEquals(1.6F, correction.position().z(), 0.0001F);
        assertTrue(correction.preserveVertical());
    }

    @Test
    public void historicalGroundedDescentDoesNotReplayAnOldVerticalOffset() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(400, new Position3f(0F, 65F, 0F), Position3f.ZERO, true, false, false, 20);
        tracker.record(401, new Position3f(0.2F, 64.5F, 0F), Position3f.ZERO, true, false, false, 20);

        final MovementPredictionTracker.Correction correction = tracker.replay(
            400, new Position3f(0.1F, 65.2F, 0F), Position3f.ZERO, true,
            401, new Position3f(0.2F, 64.5F, 0F), true, false, false, false
        );

        assertEquals(new Position3f(0.3F, 64.5F, 0F), correction.position());
        assertTrue(correction.replayed());
    }

    @Test
    public void historicalAirborneServerCorrectionDoesNotPushThroughCurrentSupport() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        final Position3f past = new Position3f(0.5F, 101.62F, 14.61248F);
        final Position3f current = new Position3f(0.5F, 101.62F, 15.69172F);
        tracker.record(155, past, Position3f.ZERO, true, false, false, 20);
        tracker.record(160, current, Position3f.ZERO, true, false, false, 20);

        final var correction = tracker.replay(
            155, new Position3f(past.x(), 101.5416F, past.z()), new Position3f(0F, -0.15523948F, 0.1178346F),
            false, 160, current, true, false, false, false
        );

        assertEquals(current, correction.position());
        assertTrue(correction.preserveVertical());
        assertFalse(correction.changesClientMovement(current, true));
    }

    @Test
    public void historicalAirborneServerCorrectionStillMovesAStationaryPlayerDown() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        final Position3f supported = new Position3f(0.5F, 101.62F, 14.61248F);
        final Position3f falling = new Position3f(supported.x(), 101.5416F, supported.z());
        tracker.record(155, supported, Position3f.ZERO, true, false, false, 20);
        tracker.record(160, supported, Position3f.ZERO, true, false, false, 20);

        final var correction = tracker.replay(
            155, falling, new Position3f(0F, -0.15523948F, 0F), false,
            160, supported, true, false, false, false
        );

        assertEquals(falling, correction.position());
        assertFalse(correction.preserveVertical());
    }

    @Test
    public void historicalFlightDescentDoesNotReplayAnOldVerticalOffset() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(500, new Position3f(2F, 80F, 3F), Position3f.ZERO, false, false, true, 20);
        tracker.record(501, new Position3f(2F, 79.5F, 3F), Position3f.ZERO, false, false, true, 20);

        final MovementPredictionTracker.Correction correction = tracker.replay(
            500, new Position3f(2F, 80.25F, 3F), Position3f.ZERO, false,
            501, new Position3f(2F, 79.5F, 3F), false, false, true, true
        );

        assertEquals(new Position3f(2F, 79.5F, 3F), correction.position());
        assertTrue(correction.replayed());
    }

    @Test
    public void historicalCrouchedEdgeCorrectionKeepsAStableJavaHeight() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(600, new Position3f(4F, 64F, 7F), Position3f.ZERO, true, false, false, 20);
        tracker.record(601, new Position3f(4.1F, 64F, 7F), Position3f.ZERO, false, false, false, 20);

        // Safe-walk at a block edge can transiently clear onGround without changing Y.
        // Continuous Shift must prevent the older positive Y delta from causing a snap.
        final MovementPredictionTracker.Correction correction = tracker.replay(
            600, new Position3f(4F, 64.2F, 7F), Position3f.ZERO, true,
            601, new Position3f(4.1F, 64F, 7F), false, false, true, false
        );

        assertEquals(new Position3f(4.1F, 64F, 7F), correction.position());
        assertFalse(correction.onGround());
        assertTrue(correction.replayed());
    }

    @Test
    public void crouchedPaneCorrectionStopsTheRejectedAxisAndKeepsTangentialMotion() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        final Position3f step = new Position3f(0.05F, 0F, 0.04F);
        tracker.record(606, new Position3f(0.85F, 64F, 0.4F), step, true, false, false, 20);
        tracker.record(607, new Position3f(0.9F, 64F, 0.44F), step, true, false, false, 20);

        final var correction = tracker.replay(
            606, new Position3f(0.8F, 64F, 0.4F), new Position3f(0F, -0.0784F, 0.03F), true,
            607, new Position3f(0.9F, 64F, 0.44F), true, false, true, false
        );

        assertEquals(new Position3f(0.8F, 64F, 0.44F), correction.position());
        assertTrue(correction.stopX());
        assertFalse(correction.stopZ());
    }

    @Test
    public void crouchedPaneCorrectionKeepsMomentumAfterMovingAwayFromThePane() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(608, new Position3f(0.85F, 64F, 0F), new Position3f(0.05F, 0F, 0F), true, false, false, 20);

        final var correction = tracker.replay(
            608, new Position3f(0.8F, 64F, 0F), Position3f.ZERO, true,
            609, new Position3f(0.7F, 64F, 0F), true, false, true, false
        );

        assertEquals(0.65F, correction.position().x(), 0.0001F);
        assertFalse(correction.stopX());
    }

    @Test
    public void staleGroundCorrectionKeepsTheCrouchJumpAndDoesNotSendAnEmptyTeleport() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(610, new Position3f(0F, 64F, 0F), Position3f.ZERO, true, false, false, 20);
        final Position3f airborne = new Position3f(0F, 64.42F, 0F);
        tracker.record(611, airborne, new Position3f(0F, 0.42F, 0F), false, false, false, 20);

        final var correction = tracker.replay(
            610, new Position3f(0F, 63.9F, 0F), Position3f.ZERO, true,
            611, airborne, false, false, true, false
        );
        assertEquals(airborne, correction.position());
        assertTrue(correction.preserveVertical());
        assertFalse(correction.changesClientMovement(airborne, false));

        final var nextCorrection = tracker.replay(
            611, airborne, Position3f.ZERO, false, 612, airborne, false, false, true, false
        );
        assertFalse(nextCorrection.changesClientMovement(airborne, false));
    }

    @Test
    public void anOlderFallingCorrectionDoesNotSinkThePlayerIntoNewBridgeSupport() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(620, new Position3f(0F, 64.2F, 1F), new Position3f(0F, -0.2F, 0.2F), false, false, false, 20);
        final Position3f landed = new Position3f(0F, 64F, 1.4F);
        tracker.record(621, landed, new Position3f(0F, -0.2F, 0.4F), true, false, false, 20);

        final var correction = tracker.replay(
            620, new Position3f(0F, 63.8F, 1.1F), new Position3f(0F, -0.3F, 0.1F), false,
            621, landed, true, false, false, false
        );
        assertEquals(new Position3f(0F, 64F, 1.5F), correction.position());
        assertTrue(correction.onGround());
        assertTrue(correction.preserveVertical());
    }

    @Test
    public void flyingVerticalKeysCarryExplicitAscendAndDescendFlags() {
        final var ascending = ClientPlayerPackets.verticalMovementInput(true, true, false, false, 0F);
        assertTrue(ascending.contains(PlayerAuthInputData.Ascend));
        assertTrue(ascending.contains(PlayerAuthInputData.WantUp));

        final var descending = ClientPlayerPackets.verticalMovementInput(true, false, true, false, 0F);
        assertTrue(descending.contains(PlayerAuthInputData.Descend));
        assertTrue(descending.contains(PlayerAuthInputData.WantDown));

        assertFalse(ClientPlayerPackets.verticalMovementInput(false, false, true, false, 0F)
            .contains(PlayerAuthInputData.Descend));
    }

    @Test
    public void historicalFlightAscentKeepsTheNewerJavaHeight() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(700, new Position3f(2F, 80F, 3F), Position3f.ZERO, false, false, true, 20);
        tracker.record(701, new Position3f(2F, 80.5F, 3F), Position3f.ZERO, false, false, true, 20);

        final MovementPredictionTracker.Correction correction = tracker.replay(
            700, new Position3f(2F, 79.75F, 3F), Position3f.ZERO, false,
            701, new Position3f(2F, 80.5F, 3F), false, false, false, true
        );

        assertEquals(new Position3f(2F, 80.5F, 3F), correction.position());
        assertTrue(correction.replayed());
    }

    @Test
    public void sameTickFlightCorrectionRemainsAuthoritative() {
        final MovementPredictionTracker tracker = new MovementPredictionTracker();
        tracker.record(710, new Position3f(2F, 80F, 3F), Position3f.ZERO, false, false, true, 20);

        final MovementPredictionTracker.Correction correction = tracker.replay(
            710, new Position3f(2F, 79.75F, 3F), Position3f.ZERO, false,
            710, new Position3f(2F, 80F, 3F), false, false, false, true
        );

        assertEquals(new Position3f(2F, 79.75F, 3F), correction.position());
    }

    @Test
    public void ladderMotionCarriesDirectionWithoutSynthesizingRawKeyEdges() {
        final var ascending = ClientPlayerPackets.verticalMovementInput(false, false, false, true, 0.2F);
        assertTrue(ascending.contains(PlayerAuthInputData.Jumping));
        assertTrue(ascending.contains(PlayerAuthInputData.WantUp));
        assertFalse(ascending.contains(PlayerAuthInputData.JumpCurrentRaw));

        final var descending = ClientPlayerPackets.verticalMovementInput(false, false, false, true, -0.2F);
        assertTrue(descending.contains(PlayerAuthInputData.WantDown));
        assertFalse(descending.contains(PlayerAuthInputData.Sneaking));
    }

    @Test
    public void climbableIdentifierDetectionIsNarrow() {
        assertTrue(ClientPlayerEntity.isClimbableBlockIdentifier("ladder"));
        assertTrue(ClientPlayerEntity.isClimbableBlockIdentifier("twisting_vines_plant"));
        assertFalse(ClientPlayerEntity.isClimbableBlockIdentifier("stone"));
    }

    @Test
    public void partialMoveActorDeltaPreservesCoordinatesThatWereNotSent() {
        final Position3f current = new Position3f(12.5F, 71F, -8.25F);

        assertEquals(new Position3f(12.5F, 70.25F, -8.25F),
            current.withOptionalCoordinates(false, 0F, true, 70.25F, false, 0F));
        assertEquals(new Position3f(13F, 71F, -9F),
            current.withOptionalCoordinates(true, 13F, false, 0F, true, -9F));
    }

    @Test
    public void playerCommandOriginCarriesTheSessionUniqueEntityId() {
        final UUID uuid = UUID.fromString("2b141a3a-66d0-46df-bcf8-402ad6d3729e");
        final CommandOriginData expected = CommandOriginData.player(uuid, 92837465L);
        final ByteBuf buffer = Unpooled.buffer();
        try {
            BedrockTypes.COMMAND_ORIGIN_DATA.write(buffer, expected);

            assertEquals(expected, BedrockTypes.COMMAND_ORIGIN_DATA.read(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void villagerV2ProfessionRegionAndTierMapToJavaVillagerData() {
        assertEquals(new VillagerData(2, 9, 1), EntityMetadataRewriter.villagerData(5, 0, 0)); // plains librarian
        assertEquals(new VillagerData(0, 1, 5), EntityMetadataRewriter.villagerData(8, 1, 9)); // desert armorer, clamped
        assertEquals(new VillagerData(2, 0, 1), EntityMetadataRewriter.villagerData(99, 99, -3));
    }

    @Test
    public void creativeScreenEchoUsesTheTrackedBedrockItemInsteadOfRecraftingIt() {
        final BedrockItem tracked = new BedrockItem(453, (short) 0, (byte) 1);
        tracked.setNetId(252);
        final BedrockItem catalog = new BedrockItem(453, (short) 0, (byte) 1);

        assertTrue(InventoryPackets.isCreativeInventoryEcho(tracked, catalog));
        catalog.setAmount(64);
        assertFalse(InventoryPackets.isCreativeInventoryEcho(tracked, catalog));
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
    public void merchantSlotsUseTrade2RequestAndResponseIdentities() {
        final MerchantContainer merchant = new MerchantContainer(null, (byte) 7, null);

        assertEquals(ContainerEnumName.Trade2Ingredient1Container, merchant.getFullContainerName(0).name());
        assertEquals(ContainerEnumName.Trade2Ingredient2Container, merchant.getFullContainerName(1).name());
        assertEquals(ContainerEnumName.Trade2ResultPreviewContainer, merchant.getFullContainerName(2).name());
        assertEquals(ContainerEnumName.Trade2Ingredient1Container, merchant.getFullContainerName(4).name());
        assertEquals(ContainerEnumName.Trade2Ingredient2Container, merchant.getFullContainerName(5).name());
        assertEquals(ContainerEnumName.Trade2ResultPreviewContainer, merchant.getFullContainerName(50).name());
        assertEquals(4, merchant.stackRequestSlot(0));
        assertEquals(5, merchant.stackRequestSlot(1));
        assertEquals(50, merchant.stackRequestSlot(2));
        assertEquals(0, merchant.stackResponseSlot(4));
        assertEquals(1, merchant.stackResponseSlot(5));
        assertEquals(2, merchant.stackResponseSlot(50));
    }

    @Test
    public void merchantOfferStockStateUsesBedrockUsesAndMaxUses() {
        final BedrockItem emerald = new BedrockItem(1, (short) 0, (byte) 2);
        final BedrockItem bread = new BedrockItem(2, (short) 0, (byte) 6);

        assertFalse(new BedrockTradeOffer(11, emerald, BedrockItem.empty(), bread, 2, 12, 1, 0F, 0).outOfStock());
        assertTrue(new BedrockTradeOffer(11, emerald, BedrockItem.empty(), bread, 12, 12, 1, 0F, 0).outOfStock());
        assertTrue(new BedrockTradeOffer(11, emerald, BedrockItem.empty(), bread, 0, 0, 1, 0F, 0).outOfStock());
    }

    @Test
    public void merchantWildcardAuxAcceptsRealInventoryMetadata() {
        final BedrockItem wildcardPaper = new BedrockItem(1, (short) BedrockItem.WILDCARD_AUX_VALUE, (byte) 24);
        final BedrockItem inventoryPaper = new BedrockItem(1, (short) 0, (byte) 32);
        final BedrockItem wrongItem = new BedrockItem(2, (short) 0, (byte) 32);
        final BedrockItem exactVariant = new BedrockItem(1, (short) 4, (byte) 1);

        assertTrue(wildcardPaper.hasWildcardData());
        assertTrue(MerchantContainer.matchesCost(inventoryPaper, wildcardPaper));
        assertFalse(MerchantContainer.matchesCost(wrongItem, wildcardPaper));
        assertFalse(MerchantContainer.matchesCost(inventoryPaper, exactVariant));
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
    public void hudAcceptsBothVerifiedProtocol2225SnapshotLengths() {
        final HudContainer hud = new HudContainer(null);
        final BedrockItem[] latest = new BedrockItem[57];
        latest[0] = new BedrockItem(1, (short) 0, (byte) 4);
        assertTrue(hud.setItems(latest));
        assertEquals(4, hud.getItems()[0].amount());
        assertTrue(hud.setItems(new BedrockItem[54]));
        assertTrue(hud.getItems()[0].isEmpty());
        assertEquals(57, hud.getItems().length);
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

    @Test
    public void craftingTablePredictionWritesThroughToItsHudBackingSlots() {
        final InventoryTracker[] tracker = new InventoryTracker[1];
        final UserConnection user = (UserConnection) Proxy.newProxyInstance(
            UserConnection.class.getClassLoader(), new Class<?>[]{UserConnection.class},
            (proxy, method, args) -> {
                if (method.getName().equals("get") && args != null && args.length == 1
                    && args[0] == InventoryTracker.class) {
                    return tracker[0];
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
        tracker[0] = new InventoryTracker(user);
        final CraftingTableContainer craftingTable = new CraftingTableContainer(user, (byte) 1, null);
        final BedrockItem ingredient = new BedrockItem(123, (short) 0, (byte) 4);

        assertTrue(craftingTable.setPredictedItem(37, ingredient));
        assertEquals(123, tracker[0].getHudContainer().getItem(37).identifier());
        assertEquals(4, tracker[0].getHudContainer().getItem(37).amount());
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

            assertEquals(18, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(20, buffer.readUnsignedByte());
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
    public void merchantRequestUsesRecipeConsumeAndCreatedOutputActions() {
        final BedrockItem bread = new BedrockItem(2, (short) 0, (byte) 6);
        final InventoryStackRequest request = new InventoryStackRequest(-1, List.of(
            new InventoryStackRequest.CraftRecipe(11, 1),
            new InventoryStackRequest.CraftResultsDeprecated(List.of(bread), 1),
            new InventoryStackRequest.Consume(2, new InventoryStackRequest.Slot(
                new FullContainerName(ContainerEnumName.Trade2Ingredient1Container, null), 4, 71
            )),
            new InventoryStackRequest.Take(6,
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.CreatedOutputContainer, null), 50, -1),
                new InventoryStackRequest.Slot(new FullContainerName(ContainerEnumName.InventoryContainer, null), 9, 0)
            )
        ));
        final ByteBuf buffer = Unpooled.buffer();
        try {
            new InventoryStackRequestType(id -> id == 2 ? "minecraft:bread" : null).write(buffer, request);

            assertEquals(-1, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(4, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(10, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(12, buffer.readUnsignedByte());
            assertEquals(11, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, buffer.readUnsignedByte());

            assertEquals(18, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(20, buffer.readUnsignedByte());
            assertEquals(1, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, buffer.readUnsignedByte());
            assertEquals("minecraft:bread", BedrockTypes.STRING.read(buffer));
            assertEquals(0, BedrockTypes.VAR_INT.read(buffer));
            assertEquals(6, buffer.readUnsignedShortLE());
            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            buffer.skipBytes(BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(1, buffer.readUnsignedByte());

            assertEquals(5, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(5, buffer.readUnsignedByte());
            assertEquals(2, buffer.readUnsignedByte());
            assertSlot(buffer, ContainerEnumName.Trade2Ingredient1Container, 4, 71);

            assertEquals(0, BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
            assertEquals(0, buffer.readUnsignedByte());
            assertEquals(6, buffer.readUnsignedByte());
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

    @Test
    public void opposingMovementKeysCancelBeforeDiagonalNormalization() {
        final PlayerAuthInputData[] directions = {PlayerAuthInputData.Left, PlayerAuthInputData.Right,
            PlayerAuthInputData.Up, PlayerAuthInputData.Down};
        for (int mask = 0; mask < 16; mask++) {
            final java.util.Set<PlayerAuthInputData> input = java.util.EnumSet.noneOf(PlayerAuthInputData.class);
            for (int bit = 0; bit < directions.length; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    input.add(directions[bit]);
                }
            }
            final float x = ((mask & 1) != 0 ? 1F : 0F) - ((mask & 2) != 0 ? 1F : 0F);
            final float y = ((mask & 4) != 0 ? 1F : 0F) - ((mask & 8) != 0 ? 1F : 0F);
            final float length = Math.max(1F, (float) Math.sqrt(x * x + y * y));
            final var walking = MathUtil.calculateMovementDirections(input, false);
            final var sneaking = MathUtil.calculateMovementDirections(input, true);
            assertEquals(x / length, walking.x(), 0.000001F, "walking X mask " + mask);
            assertEquals(y / length, walking.y(), 0.000001F, "walking Y mask " + mask);
            assertEquals(x / length * 0.3F, sneaking.x(), 0.000001F, "sneaking X mask " + mask);
            assertEquals(y / length * 0.3F, sneaking.y(), 0.000001F, "sneaking Y mask " + mask);
        }
    }

}
