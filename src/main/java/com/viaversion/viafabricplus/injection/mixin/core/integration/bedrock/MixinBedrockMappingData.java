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

package com.viaversion.viafabricplus.injection.mixin.core.integration.bedrock;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.google.common.collect.BiMap;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viafabricplus.util.bedrock.BedrockMappingDataFixer;
import com.viaversion.viaversion.libs.gson.JsonObject;
import net.raphimc.viabedrock.protocol.data.BedrockMappingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BedrockMappingData.class, remap = false)
public abstract class MixinBedrockMappingData {

    @Shadow
    private BiMap<String, Integer> bedrockEntities;

    @ModifyExpressionValue(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lcom/viaversion/nbt/stringified/SNBT;deserializeCompoundTag(Ljava/lang/String;)Lcom/viaversion/nbt/tag/CompoundTag;",
            ordinal = 0
        )
    )
    private CompoundTag addMissingBiomeDefinitions(final CompoundTag definitions) {
        return BedrockMappingDataFixer.addMissingBiomeDefinitions(definitions);
    }

    @WrapOperation(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/raphimc/viabedrock/protocol/data/BedrockMappingData;readJson(Ljava/lang/String;)Lcom/viaversion/viaversion/libs/gson/JsonObject;"
        )
    )
    private JsonObject removeUnknownEntitySoundMappings(BedrockMappingData instance, String file, Operation<JsonObject> original) {
        final JsonObject mappings = original.call(instance, file);
        if (file.equals("bedrock/level_sound_event_mappings.json")) {
            BedrockMappingDataFixer.removeUnknownEntitySoundMappings(mappings, this.bedrockEntities.keySet());
        }
        return mappings;
    }

}
