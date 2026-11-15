/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.util.bedrock;

import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InventorySourceFlags;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InventorySourceType;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.inventory.InventoryActionData;
import net.raphimc.viabedrock.protocol.model.inventory.InventorySource;
import net.raphimc.viabedrock.protocol.storage.AuthoritativePickupTracker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AuthoritativePickupTrackerTest {

    @Test
    void theWorldActorUsesTheAcceptedPickupOnceWithoutRepeatingTheInventoryGain() {
        final AuthoritativePickupTracker tracker = new AuthoritativePickupTracker();
        final BedrockItem log = oak(1);
        final BedrockItem full = oak(64);
        full.setNetId(3);

        tracker.remember(List.of(inventory(oak(63), full), fromWorld(log, BedrockItem.empty())), Set.of(9), 0);

        final BedrockItem differentBlockState = log.copy();
        differentBlockState.setBlockRuntimeId(1);
        assertEquals(0, tracker.consume(differentBlockState, 1));
        assertEquals(1, tracker.consume(log, 2));
        assertEquals(0, tracker.consume(log, 3));
    }

    @Test
    void aSlotUpdateWithoutAWorldRemovalOrWithoutAnAcceptedSlotCannotConfirmPickup() {
        final AuthoritativePickupTracker tracker = new AuthoritativePickupTracker();
        final InventoryActionData slot = inventory(oak(63), oak(64));
        final InventoryActionData ground = fromWorld(oak(1), BedrockItem.empty());

        tracker.remember(List.of(slot), Set.of(9), 0);
        tracker.remember(List.of(slot, ground), Set.of(), 1);

        assertEquals(0, tracker.consume(oak(1), 2));
    }

    @Test
    void aPartialPickupOnlyAccountsForTheInventoryGainEvenWithRepeatedWorldActions() {
        final AuthoritativePickupTracker tracker = new AuthoritativePickupTracker();
        final InventoryActionData slot = inventory(oak(63), oak(64));
        final InventoryActionData ground = fromWorld(oak(4), oak(3));

        tracker.remember(List.of(slot, ground, ground), Set.of(9), 0);

        assertEquals(1, tracker.consume(oak(4), 1));
        assertEquals(0, tracker.consume(oak(4), 2));
    }

    @Test
    void anUnmatchedReceiptExpiresBeforeAnotherPickupCanUseIt() {
        final AuthoritativePickupTracker tracker = new AuthoritativePickupTracker();
        tracker.remember(List.of(inventory(oak(63), oak(64)), fromWorld(oak(1), BedrockItem.empty())), Set.of(9), 0);

        assertEquals(0, tracker.consume(oak(1), TimeUnit.SECONDS.toNanos(1)));
    }

    private static BedrockItem oak(final int amount) {
        final BedrockItem log = new BedrockItem(17, (short) 0, (byte) amount);
        log.setBlockRuntimeId(825916963);
        return log;
    }

    private static InventoryActionData inventory(final BedrockItem before, final BedrockItem after) {
        return new InventoryActionData(new InventorySource(InventorySourceType.Container_Inventory, 0, InventorySourceFlags.No_Flag),
            9, before, after);
    }

    private static InventoryActionData fromWorld(final BedrockItem before, final BedrockItem after) {
        return new InventoryActionData(new InventorySource(InventorySourceType.World_Interaction, -1, InventorySourceFlags.No_Flag),
            1, before, after);
    }
}
