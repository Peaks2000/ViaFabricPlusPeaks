/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.injection.mixin.features.bedrock.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.raphimc.viabedrock.protocol.model.PlayerFluidInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    public LocalPlayer player;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void sendLocalFluidInput(final ClientPacketListener listener, final Packet<?> packet,
                                    final Operation<Void> original) {
        final var connection = listener.getConnection();
        if (packet instanceof ServerboundClientTickEndPacket && this.player != null
            && ProtocolTranslator.isBedrock(((IConnection) connection).viaFabricPlus$getTargetVersion())) {
            final var velocity = this.player.getDeltaMovement();
            final float[] snapshot = {this.player.isSwimming() ? 1 : 0, this.player.isInWater() || this.player.isInLava() ? 1 : 0,
                (float) velocity.x(), (float) velocity.y(), (float) velocity.z()};
            // Connection.send queues the packet on the same event loop immediately afterward.
            connection.channel.eventLoop().execute(() -> connection.channel.attr(PlayerFluidInput.KEY).set(snapshot));
        }
        original.call(listener, packet);
    }
}
