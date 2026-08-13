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

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viafabricplus.screen.impl.bedrock.BedrockWorldsScreen;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayStatus;
import net.raphimc.viabedrock.protocol.packet.JoinPackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JoinPackets.class, remap = false)
public abstract class MixinJoinPackets {

    @Inject(method = "writePlayStatusKickMessage", at = @At("HEAD"), cancellable = true)
    private static void retryKnownBedrockProtocol(final PacketWrapper wrapper, final PlayStatus status, final CallbackInfo ci) {
        if (BedrockWorldsScreen.retryVersionMismatch(status)) {
            wrapper.cancel();
            wrapper.user().getChannel().close();
            ci.cancel();
        }
    }

}
