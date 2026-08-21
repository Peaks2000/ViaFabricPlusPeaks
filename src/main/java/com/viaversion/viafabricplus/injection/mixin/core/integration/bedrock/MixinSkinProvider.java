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

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.viaversion.viafabricplus.util.bedrock.BedrockProtocolCompatibility;
import com.viaversion.viafabricplus.util.bedrock.BedrockSkinBridge;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.util.Map;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.model.SkinData;
import net.raphimc.viabedrock.protocol.provider.SkinProvider;
import net.raphimc.viabedrock.protocol.storage.HandshakeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkinProvider.class, remap = false)
public abstract class MixinSkinProvider {

    @ModifyReturnValue(method = "getClientPlayerSkin", at = @At("RETURN"))
    private Map<String, Object> useSelectedGameVersion(final Map<String, Object> claims, final UserConnection user) {
        final HandshakeStorage handshake = user.get(HandshakeStorage.class);
        claims.put("GameVersion", BedrockProtocolCompatibility.gameVersion(
            handshake.protocolVersion(), ProtocolConstants.BEDROCK_VERSION_NAME
        ));
        BedrockSkinBridge.applyClientPlayerSkin(claims);
        return claims;
    }

    @Inject(method = "setSkin", at = @At("HEAD"))
    private void installSkinLocally(final UserConnection user, final java.util.UUID playerUuid,
                                    final SkinData skin, final CallbackInfo ci) {
        BedrockSkinBridge.installBedrockSkin(user, playerUuid, skin);
    }

}
