/*
 * This file is part of ViaFabricPlus - https://github.com/Peaks2000/ViaFabricPlusPeaks
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.injection.mixin.core.connection.bedrock;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.util.bedrock.NetherNetDiscoveryPacketFixer;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetDiscovery;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetherNetDiscovery.class, remap = false)
public abstract class MixinNetherNetDiscovery {

    private static final int MAX_UDP_PACKET_SIZE = 65_535;

    @WrapOperation(
        method = {"bind(I)V", "bind(Ljava/net/InetSocketAddress;)V"},
        at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;handler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/AbstractBootstrap;")
    )
    private AbstractBootstrap<?, ?> increaseSignalReceiveBuffer(Bootstrap instance, ChannelHandler handler, Operation<AbstractBootstrap<?, ?>> original) {
        instance.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(MAX_UDP_PACKET_SIZE));
        return original.call(instance, handler);
    }

    @Inject(method = "handleMessage", at = @At("HEAD"))
    private void repairTruncatedConnectResponse(ByteBuf buffer, long senderNetworkId, CallbackInfo ci) {
        final int omittedBytes = NetherNetDiscoveryPacketFixer.repairTruncatedConnectResponseLength(buffer);
        if (omittedBytes > 0) {
            ViaFabricPlusImpl.INSTANCE.getLogger().warn("Repaired a malformed NetherNet LAN response with {} omitted byte(s)", omittedBytes);
        }
    }

}
