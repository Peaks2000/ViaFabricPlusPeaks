/*
 * This file is part of ViaFabricPlus - https://github.com/Peaks2000/ViaFabricPlusPeaks
 * Copyright (C) 2021-2026 the original authors
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viafabricplus.injection.mixin.core.integration.bedrock;

import com.viaversion.viafabricplus.util.bedrock.BedrockProtocolCompatibility;
import net.raphimc.viabedrock.protocol.storage.HandshakeStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HandshakeStorage.class, remap = false)
public abstract class MixinHandshakeStorage {

    @Shadow
    @Final
    private int protocolVersion;

    @Unique
    private int viaFabricPlus$wireProtocol = BedrockProtocolCompatibility.UNKNOWN_PROTOCOL;

    @Inject(method = "protocolVersion", at = @At("HEAD"), cancellable = true)
    private void useDiscoveredBedrockProtocol(final CallbackInfoReturnable<Integer> cir) {
        if (this.viaFabricPlus$wireProtocol == BedrockProtocolCompatibility.UNKNOWN_PROTOCOL) {
            this.viaFabricPlus$wireProtocol = BedrockProtocolCompatibility.consumeConnectionProtocol(this.protocolVersion);
        }
        cir.setReturnValue(this.viaFabricPlus$wireProtocol);
    }

}
