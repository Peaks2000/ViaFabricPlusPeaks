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

import dev.kastle.netty.channel.nethernet.NetherNetClientChannel;
import dev.kastle.netty.channel.nethernet.config.NetherChannelOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetherNetClientChannel.class, remap = false)
public abstract class MixinNetherNetClientChannel {

    @Inject(method = "startHandshake", at = @At("HEAD"))
    private void configureHandshakeTimeout(final CallbackInfo ci) {
        final NetherNetClientChannel channel = (NetherNetClientChannel) (Object) this;
        channel.config().setOption(NetherChannelOption.NETHER_CLIENT_HANDSHAKE_TIMEOUT_MS, 20_000);
        channel.config().setOption(NetherChannelOption.NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS, 0);
    }

}
