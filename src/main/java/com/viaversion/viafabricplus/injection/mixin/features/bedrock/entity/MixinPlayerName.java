/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.injection.mixin.features.bedrock.entity;

import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayerName {

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void exposeBedrockName(final CallbackInfoReturnable<Component> cir) {
        final Component name = this.viaFabricPlus$bedrockName();
        if (name != null) {
            cir.setReturnValue(name);
        }
    }

    @Inject(method = "getPlainTextName", at = @At("RETURN"), cancellable = true)
    private void exposePlainBedrockName(final CallbackInfoReturnable<String> cir) {
        final Component name = this.viaFabricPlus$bedrockName();
        if (name != null) {
            cir.setReturnValue(name.getString());
        }
    }

    @ModifyArg(method = "getDisplayName", index = 1, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
    private Component avoidRepeatingTeamPrefix(final Component name) {
        // The team already includes the translated player name as its prefix.
        return this.viaFabricPlus$bedrockName() != null ? Component.empty() : name;
    }

    @Unique
    private Component viaFabricPlus$bedrockName() {
        if (!((Object) this instanceof RemotePlayer player)) {
            return null;
        }
        final var listener = Minecraft.getInstance().getConnection();
        if (listener == null || !ProtocolTranslator.isBedrock(((IConnection) listener.getConnection()).viaFabricPlus$getTargetVersion())) {
            return null;
        }
        final var team = player.getTeam();
        if (team == null || !team.getName().equals("vb_" + player.getId())) {
            return null;
        }
        final Component prefix = team.getPlayerPrefix();
        return prefix.getString().isEmpty() ? null : prefix;
    }

}
