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

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BedrockCreativeInventoryTest {

    @Test
    public void rejectedCursorIsMirroredOnlyIntoTheMaintainedBedrockCreativeMenu() {
        assertTrue(BedrockCreativeInventory.shouldRestoreRejectedCursor(
                BedrockProtocolVersion.bedrockLatest, 0, false, true));

        assertFalse(BedrockCreativeInventory.shouldRestoreRejectedCursor(
                ProtocolVersion.v1_21_11, 0, false, true));
        assertFalse(BedrockCreativeInventory.shouldRestoreRejectedCursor(
                BedrockProtocolVersion.bedrockLatest, 1, false, true));
        assertFalse(BedrockCreativeInventory.shouldRestoreRejectedCursor(
                BedrockProtocolVersion.bedrockLatest, 0, true, true));
        assertFalse(BedrockCreativeInventory.shouldRestoreRejectedCursor(
                BedrockProtocolVersion.bedrockLatest, 0, false, false));
    }

    @Test
    public void deferredRestoreRemainsMaintainedBedrockCreativeOnly() {
        assertTrue(BedrockCreativeInventory.shouldApplyDeferredCursorRestore(
                BedrockProtocolVersion.bedrockLatest, false, true));

        assertFalse(BedrockCreativeInventory.shouldApplyDeferredCursorRestore(
                ProtocolVersion.v1_21_11, false, true));
        assertFalse(BedrockCreativeInventory.shouldApplyDeferredCursorRestore(
                BedrockProtocolVersion.bedrockLatest, true, true));
        assertFalse(BedrockCreativeInventory.shouldApplyDeferredCursorRestore(
                BedrockProtocolVersion.bedrockLatest, false, false));
    }

}
