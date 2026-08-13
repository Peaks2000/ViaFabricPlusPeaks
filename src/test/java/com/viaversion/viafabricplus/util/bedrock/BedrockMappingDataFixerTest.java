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
import com.viaversion.nbt.stringified.SNBT;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.util.GsonUtil;
import com.viaversion.viaversion.util.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BedrockMappingDataFixerTest {

    @Test
    void suppliesIndependentDappledForestFallback() {
        final CompoundTag definitions = new CompoundTag();
        final CompoundTag forest = new CompoundTag();
        forest.putFloat("temperature", 0.7F);
        definitions.put("minecraft:forest", forest);

        BedrockMappingDataFixer.addMissingBiomeDefinitions(definitions);

        final CompoundTag dappledForest = definitions.getCompoundTag("minecraft:dappled_forest");
        assertNotSame(forest, dappledForest);
        assertEquals(0.7F, dappledForest.getFloat("temperature"));
    }

    @Test
    void coversEveryBiomeInViaBedrocksIdMapping() throws IOException {
        final JsonObject biomeIds = readJson("/assets/viabedrock/data/bedrock/biomes.json");
        final CompoundTag definitions = SNBT.deserializeCompoundTag(
            readJson("/assets/viabedrock/data/bedrock/biome_definitions.json").toString()
        );

        BedrockMappingDataFixer.addMissingBiomeDefinitions(definitions);

        for (String biome : biomeIds.keySet()) {
            assertTrue(definitions.contains(Key.namespaced(biome)), () -> "Missing definition for " + biome);
        }
    }

    private static JsonObject readJson(final String path) throws IOException {
        try (InputStream input = BedrockMappingDataFixerTest.class.getResourceAsStream(path)) {
            assertNotNull(input, () -> "Missing test resource " + path);
            return GsonUtil.getGson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

}
