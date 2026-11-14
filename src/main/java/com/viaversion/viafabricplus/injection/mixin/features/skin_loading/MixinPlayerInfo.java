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

package com.viaversion.viafabricplus.injection.mixin.features.skin_loading;

import com.mojang.authlib.GameProfile;
import com.viaversion.viafabricplus.util.bedrock.BedrockSkinBridge;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class MixinPlayerInfo {

    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void useLocalBedrockSkin(final CallbackInfoReturnable<PlayerSkin> result) {
        final PlayerSkin skin = BedrockSkinBridge.localBedrockSkin(getProfile().id(), null);
        if (skin != null) {
            result.setReturnValue(skin);
        }
    }

}
