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
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.viaversion.viafabricplus.features.networking.blocked_server.BlockedServerOverride;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.client.multiplayer.resolver.ServerRedirectHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerNameResolver.class)
public abstract class MixinServerNameResolver {

    @Shadow
    @Final
    private ServerAddressResolver resolver;

    @Shadow
    @Final
    private ServerRedirectHandler redirectHandler;

    @Shadow
    @Final
    private AddressCheck addressCheck;

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void prepareBlockedServerCheck(final ServerAddress address,
                                           final CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir,
                                           @Share("trackAttempt") final LocalBooleanRef trackAttempt) {
        final boolean track = BlockedServerOverride.isEligibleRoute()
            && BlockedServerOverride.isCurrentAttempt(address)
            && BlockedServerOverride.isConnectionResolution(address);
        trackAttempt.set(track);
        if (track && BlockedServerOverride.consumeBypass(address)) {
            cir.setReturnValue(BlockedServerOverride.resolveWithoutBlocklist(address, this.resolver, this.redirectHandler));
        }
    }

    @WrapOperation(method = "resolveAddress", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/resolver/ServerAddressResolver;resolve(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Ljava/util/Optional;"))
    private Optional<ResolvedServerAddress> detectResolvedAddressBlock(final ServerAddressResolver resolver,
                                                                       final ServerAddress address,
                                                                       final Operation<Optional<ResolvedServerAddress>> original,
                                                                       @Share("trackAttempt") final LocalBooleanRef trackAttempt,
                                                                       @Share("blocked") final LocalBooleanRef blocked) {
        final Optional<ResolvedServerAddress> result = original.call(resolver, address);
        if (trackAttempt.get() && result.isPresent() && !this.addressCheck.isAllowed(result.get())) {
            blocked.set(true);
        }
        return result;
    }

    @WrapOperation(method = "resolveAddress", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/resolver/AddressCheck;isAllowed(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Z"))
    private boolean detectNamedAddressBlock(final AddressCheck addressCheck, final ServerAddress address,
                                            final Operation<Boolean> original,
                                            @Share("trackAttempt") final LocalBooleanRef trackAttempt,
                                            @Share("blocked") final LocalBooleanRef blocked) {
        final boolean allowed = original.call(addressCheck, address);
        if (trackAttempt.get() && !allowed) {
            blocked.set(true);
        }
        return allowed;
    }

    @Inject(method = "resolveAddress", at = @At("RETURN"))
    private void finishBlockedServerCheck(final ServerAddress address,
                                          final CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir,
                                          @Share("trackAttempt") final LocalBooleanRef trackAttempt,
                                          @Share("blocked") final LocalBooleanRef blocked) {
        if (!trackAttempt.get()) {
            return;
        }
        if (cir.getReturnValue().isEmpty() && blocked.get()) {
            BlockedServerOverride.markBlocked(address);
        } else {
            BlockedServerOverride.finishResolution(address);
        }
    }
}
