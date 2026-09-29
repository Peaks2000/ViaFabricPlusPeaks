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
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.injection.mixin.core.connection;

import com.viaversion.viafabricplus.util.bedrock.CompatibilityViaBedrockRuntime;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import dev.kastle.netty.channel.nethernet.config.NetherChannelOption;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.netty.DisconnectHandler;
import net.raphimc.viabedrock.netty.raknet.MessageCodec;
import net.raphimc.viabedrock.netty.BatchLengthCodec;
import net.raphimc.viabedrock.netty.PacketCodec;
import java.util.concurrent.ThreadLocalRandom;
import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.protocoltranslator.netty.NoReadFlowControlHandler;
import com.viaversion.viafabricplus.protocoltranslator.netty.ViaFabricPlusDecoder;
import com.viaversion.viafabricplus.protocoltranslator.protocol.ViaFabricPlusProtocol;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import com.viaversion.viaversion.platform.ViaEncodeHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.netty.PreNettyLengthPrepender;
import net.raphimc.vialegacy.netty.PreNettyLengthRemover;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl.MINECRAFT_CONNECTION_ATTRIBUTE_KEY;
import static com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl.TARGET_VERSION_ATTRIBUTE_KEY;

@Mixin(targets = "net.minecraft.network.Connection$1")
public abstract class MixinConnection_1 {

    @Final
    @Shadow
    Connection val$connection;

    @Inject(method = "initChannel", at = @At("RETURN"))
    private void injectViaIntoPipeline(Channel channel, CallbackInfo ci) {
        final IConnection mixinClientConnection = (IConnection) this.val$connection;
        final ProtocolVersion serverVersion = mixinClientConnection.viaFabricPlus$getTargetVersion();

        channel.attr(MINECRAFT_CONNECTION_ATTRIBUTE_KEY).set(this.val$connection);
        channel.attr(TARGET_VERSION_ATTRIBUTE_KEY).set(serverVersion);

        if (CompatibilityViaBedrockRuntime.isBedrock(serverVersion)) {
            final ChannelConfig config = channel.config();
            // RakNet config
            config.setOption(RakChannelOption.RAK_PROTOCOL_VERSION, ProtocolConstants.BEDROCK_RAKNET_PROTOCOL_VERSION);
            config.setOption(RakChannelOption.RAK_COMPATIBILITY_MODE, true);
            config.setOption(RakChannelOption.RAK_CLIENT_INTERNAL_ADDRESSES, 20);
            config.setOption(RakChannelOption.RAK_TIME_BETWEEN_SEND_CONNECTION_ATTEMPTS_MS, 500);
            config.setOption(RakChannelOption.RAK_CONNECT_TIMEOUT, config.getOption(ChannelOption.CONNECT_TIMEOUT_MILLIS).longValue());
            config.setOption(RakChannelOption.RAK_SESSION_TIMEOUT, 30_000L);
            config.setOption(RakChannelOption.RAK_GUID, ThreadLocalRandom.current().nextLong());

            // NetherNet config
            config.setOption(NetherChannelOption.NETHER_CLIENT_HANDSHAKE_TIMEOUT_MS, config.getOption(ChannelOption.CONNECT_TIMEOUT_MILLIS));
            config.setOption(NetherChannelOption.NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS, 1);
        }

        final UserConnection user = ViaChannelInitializer.createUserConnection(channel, true);
        mixinClientConnection.viaFabricPlus$setUserConnection(user);

        final ChannelPipeline pipeline = channel.pipeline();

        // ViaVersion
        pipeline.addBefore(HandlerNames.INBOUND_CONFIG, ViaDecodeHandler.NAME, new ViaFabricPlusDecoder(user));
        pipeline.addBefore(HandlerNames.ENCODER, ViaEncodeHandler.NAME, new ViaEncodeHandler(user));

        if (serverVersion.olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {
            // ViaLegacy
            pipeline.addBefore(HandlerNames.SPLITTER, PreNettyLengthPrepender.NAME, new PreNettyLengthPrepender(user));
            pipeline.addBefore(HandlerNames.PREPENDER, PreNettyLengthRemover.NAME, new PreNettyLengthRemover(user));
        } else if (CompatibilityViaBedrockRuntime.isCompatibility(serverVersion)) {
            CompatibilityViaBedrockRuntime.installCompatibilityPipeline(pipeline);
        } else if (serverVersion.equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            // ViaBedrock
            pipeline.addBefore(HandlerNames.SPLITTER, DisconnectHandler.NAME, new DisconnectHandler());
            pipeline.addBefore(HandlerNames.SPLITTER, MessageCodec.NAME, new MessageCodec());
            pipeline.replace(HandlerNames.SPLITTER, HandlerNames.SPLITTER, new BatchLengthCodec());
            pipeline.remove(HandlerNames.PREPENDER);
            pipeline.addBefore(ViaDecodeHandler.NAME, PacketCodec.NAME, new PacketCodec());
        }

        pipeline.addAfter(ViaDecodeHandler.NAME, NoReadFlowControlHandler.NAME, new NoReadFlowControlHandler());
        user.getProtocolInfo().getPipeline().add(ViaFabricPlusProtocol.INSTANCE);
    }

}
