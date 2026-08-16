/*
 * This file is part of ViaFabricPlus - https://github.com/Peaks2000/ViaFabricPlusPeaks
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
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.limiter.TagLimiter;
import com.viaversion.nbt.stringified.SNBT;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.util.GsonUtil;
import com.viaversion.viaversion.util.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

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

    @Test
    void removesOnlyUnknownEntitySoundSpecializations() {
        final JsonObject mappings = new JsonObject();
        final JsonObject event = new JsonObject();
        event.add("entity:minecraft:cushion", new JsonObject());
        event.add("entity:minecraft:llama", new JsonObject());
        event.add("", new JsonObject());
        mappings.add("break", event);

        BedrockMappingDataFixer.removeUnknownEntitySoundMappings(mappings, Set.of("minecraft:llama"));

        assertTrue(!event.has("entity:minecraft:cushion"));
        assertTrue(event.has("entity:minecraft:llama"));
        assertTrue(event.has(""));
    }

    @Test
    void allBundledEntitySoundSpecializationsReferenceKnownEntities() throws IOException {
        final CompoundTag identifiers = readNbt("/assets/viabedrock/data/bedrock/entity_identifiers.nbt");
        final Set<String> knownEntities = new HashSet<>();
        for (CompoundTag identifier : identifiers.getListTag("idlist", CompoundTag.class)) {
            knownEntities.add(identifier.getString("id"));
        }

        final JsonObject mappings = readJson("/assets/viabedrock/data/bedrock/level_sound_event_mappings.json");
        BedrockMappingDataFixer.removeUnknownEntitySoundMappings(mappings, knownEntities);

        for (var event : mappings.entrySet()) {
            final JsonElement eventMappings = event.getValue();
            if (!eventMappings.isJsonObject()) {
                continue;
            }
            for (var mapping : eventMappings.getAsJsonObject().entrySet()) {
                final String key = mapping.getKey();
                if (key.startsWith("entity:")) {
                    final String entity = key.substring("entity:".length());
                    assertTrue(knownEntities.contains(entity), () -> "Unknown entity sound specialization " + entity);
                }
            }
        }
    }

    private static JsonObject readJson(final String path) throws IOException {
        try (InputStream input = BedrockMappingDataFixerTest.class.getResourceAsStream(path)) {
            assertNotNull(input, () -> "Missing test resource " + path);
            return GsonUtil.getGson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    private static CompoundTag readNbt(final String path) throws IOException {
        try (InputStream input = BedrockMappingDataFixerTest.class.getResourceAsStream(path)) {
            assertNotNull(input, () -> "Missing test resource " + path);
            return NBTIO.readTag(new DataInputStream(new GZIPInputStream(input)), TagLimiter.noop(), true, CompoundTag.class);
        }
    }

}
