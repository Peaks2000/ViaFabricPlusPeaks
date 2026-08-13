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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import net.raphimc.viabedrock.protocol.storage.ResourcePackLoadStateTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ResourcePackLoadStateTracker.class, remap = false)
public abstract class MixinResourcePackLoadStateTracker {

    @WrapOperation(
        method = "lambda$loadRequestedResourcePacks$5",
        at = @At(
            value = "INVOKE",
            target = "Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;write(Lcom/viaversion/viaversion/api/type/Type;Ljava/lang/Object;)V"
        )
    )
    private static void writeDownloadingResponse(
        final PacketWrapper wrapper,
        final Type<?> type,
        final Object value,
        final Operation<Void> original
    ) {
        if (type == Types.BYTE && value instanceof final Byte responseType) {
            // Protocol 2168 uses a zero-based unsigned VarInt status followed by its name string.
            original.call(wrapper, BedrockTypes.UNSIGNED_VAR_INT, Byte.toUnsignedInt(responseType) - 1);
        } else if (type == BedrockTypes.SHORT_LE_STRING_ARRAY) {
            original.call(wrapper, BedrockTypes.STRING_ARRAY, value);
        } else {
            original.call(wrapper, type, value);
        }
    }

}
