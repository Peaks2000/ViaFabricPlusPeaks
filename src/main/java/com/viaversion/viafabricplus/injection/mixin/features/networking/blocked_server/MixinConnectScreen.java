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
 */

package com.viaversion.viafabricplus.injection.mixin.features.networking.blocked_server;

import com.viaversion.viafabricplus.features.networking.blocked_server.BlockedServerOverride;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class MixinConnectScreen {

    @Inject(method = "startConnecting", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;disconnectWithProgressScreen(Z)V"))
    private static void rememberConnectionAttempt(final Screen parent, final Minecraft minecraft,
                                                  final ServerAddress address, final ServerData serverData,
                                                  final boolean quickPlay, final @Nullable TransferState transferState,
                                                  final CallbackInfo ci) {
        BlockedServerOverride.beginAttempt(parent, minecraft, address, serverData, quickPlay, transferState);
    }
}
