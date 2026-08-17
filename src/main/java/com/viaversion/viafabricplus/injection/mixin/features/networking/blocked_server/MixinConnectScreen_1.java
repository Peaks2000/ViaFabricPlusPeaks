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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.features.networking.blocked_server.BlockedServerOverride;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scopes blocked-server tracking to the resolver call made by the actual connection worker.
 * Server-list pings use the same singleton resolver and must not finish this attempt.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public abstract class MixinConnectScreen_1 {

    @WrapOperation(method = "run", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/resolver/ServerNameResolver;resolveAddress(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Ljava/util/Optional;"))
    private Optional<ResolvedServerAddress> scopeConnectionResolution(final ServerNameResolver resolver,
                                                                      final ServerAddress address,
                                                                      final Operation<Optional<ResolvedServerAddress>> original) {
        BlockedServerOverride.beginConnectionResolution(address);
        try {
            return original.call(resolver, address);
        } finally {
            BlockedServerOverride.finishConnectionResolution();
        }
    }
}
