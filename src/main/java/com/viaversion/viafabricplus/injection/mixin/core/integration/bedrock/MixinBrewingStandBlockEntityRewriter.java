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

package com.viaversion.viafabricplus.injection.mixin.core.integration.bedrock;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import net.raphimc.viabedrock.protocol.rewriter.blockentity.BrewingStandBlockEntityRewriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BrewingStandBlockEntityRewriter.class, remap = false)
public abstract class MixinBrewingStandBlockEntityRewriter {

    @Redirect(
        method = "toJava",
        at = @At(value = "INVOKE", target = "Lcom/viaversion/nbt/tag/CompoundTag;getListTag(Ljava/lang/String;Ljava/lang/Class;)Lcom/viaversion/nbt/tag/ListTag;")
    )
    private ListTag<CompoundTag> handleMissingItems(CompoundTag instance, String key, Class<CompoundTag> elementType) {
        final ListTag<CompoundTag> items = instance.getListTag(key, elementType);
        return items != null ? items : new ListTag<>(elementType);
    }

}
