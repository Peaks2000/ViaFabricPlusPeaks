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

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.viaversion.viafabricplus.util.bedrock.BedrockProtocolCompatibility;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.util.Map;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.provider.SkinProvider;
import net.raphimc.viabedrock.protocol.storage.HandshakeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SkinProvider.class, remap = false)
public abstract class MixinSkinProvider {

    @ModifyReturnValue(method = "getClientPlayerSkin", at = @At("RETURN"))
    private Map<String, Object> useSelectedGameVersion(final Map<String, Object> claims, final UserConnection user) {
        final HandshakeStorage handshake = user.get(HandshakeStorage.class);
        claims.put("GameVersion", BedrockProtocolCompatibility.gameVersion(
            handshake.protocolVersion(), ProtocolConstants.BEDROCK_VERSION_NAME
        ));
        return claims;
    }

}
