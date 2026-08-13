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

import com.viaversion.viaversion.api.type.Type;
import net.raphimc.viabedrock.protocol.packet.ResourcePackPackets;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ResourcePackPackets.class, remap = false)
public abstract class MixinResourcePackPackets {

    @ModifyArg(
        method = "lambda$register$0",
        at = @At(
            value = "INVOKE",
            target = "Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;read(Lcom/viaversion/viaversion/api/type/Type;)Ljava/lang/Object;",
            ordinal = 6
        ),
        index = 0
    )
    private static Type<?> useVariableLengthResourcePackCount(final Type<?> original) {
        return BedrockTypes.UNSIGNED_VAR_INT;
    }

}
