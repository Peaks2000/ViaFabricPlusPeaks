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
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.features.classic.cpe_extension;

import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.data.ExtendedClassicBlocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CPEAdditionsTest {

    @Test
    public void tracksAndResetsClassicClimbableDefinitions() {
        final int customBlock = 200;

        CPEAdditions.resetBlockDefinitions();
        assertTrue(CPEAdditions.isClimbableBlock(ExtendedClassicBlocks.ROPE));
        assertFalse(CPEAdditions.isClimbableBlock(customBlock));

        CPEAdditions.handleBlockDefinition(customBlock, (byte) CPEAdditions.COLLIDE_CLIMB);
        assertTrue(CPEAdditions.isClimbableBlock(customBlock));

        CPEAdditions.handleBlockDefinition(customBlock, (byte) CPEAdditions.COLLIDE_DEVCLIMB);
        assertTrue(CPEAdditions.isClimbableBlock(customBlock));

        CPEAdditions.handleBlockDefinition(customBlock, (byte) 0);
        assertFalse(CPEAdditions.isClimbableBlock(customBlock));

        CPEAdditions.handleBlockDefinition(customBlock, (byte) CPEAdditions.COLLIDE_CLIMB);
        CPEAdditions.removeBlockDefinition(customBlock);
        assertFalse(CPEAdditions.isClimbableBlock(customBlock));

        CPEAdditions.handleBlockDefinition(customBlock, (byte) CPEAdditions.COLLIDE_CLIMB);
        CPEAdditions.resetBlockDefinitions();
        assertFalse(CPEAdditions.isClimbableBlock(customBlock));
        assertTrue(CPEAdditions.isClimbableBlock(ExtendedClassicBlocks.ROPE));
    }
}
