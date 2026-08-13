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

package com.viaversion.viafabricplus.util.bedrock;

import com.viaversion.nbt.tag.CompoundTag;

public final class BedrockMappingDataFixer {

    private static final String DAPPLED_FOREST = "minecraft:dappled_forest";
    private static final String FOREST = "minecraft:forest";

    private BedrockMappingDataFixer() {
    }

    /**
     * ViaBedrock's initial 1.26.40 data contains the numeric id for dappled forest, but not its
     * biome definition. Supply a conservative forest definition until the server sends its own
     * biome definitions during login.
     */
    public static CompoundTag addMissingBiomeDefinitions(final CompoundTag definitions) {
        if (!definitions.contains(DAPPLED_FOREST)) {
            final CompoundTag forest = definitions.getCompoundTag(FOREST);
            if (forest != null) {
                definitions.put(DAPPLED_FOREST, forest.copy());
            }
        }
        return definitions;
    }

}
