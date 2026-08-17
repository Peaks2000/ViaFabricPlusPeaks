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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class MixinDisconnectedScreen {

    @Shadow
    @Final
    private DisconnectionDetails details;

    @Shadow
    @Final
    private LinearLayout layout;

    @Inject(method = "init", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;allowsMultiplayer()Z"))
    private void addJoinAnywayButton(final CallbackInfo ci) {
        if (!this.details.reason().equals(ConnectScreen.UNKNOWN_HOST_MESSAGE)) {
            return;
        }
        BlockedServerOverride.consumeBlockedAttempt().ifPresent(attempt -> this.layout.addChild(Button.builder(
            Component.translatable("base.viafabricplus.join_blocked_server_anyway"),
            button -> BlockedServerOverride.joinAnyway(attempt)
        ).width(200).build()));
    }
}
