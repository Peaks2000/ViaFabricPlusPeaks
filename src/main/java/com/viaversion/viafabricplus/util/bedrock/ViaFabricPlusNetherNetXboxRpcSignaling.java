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

package com.viaversion.viafabricplus.util.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetXboxRpcSignaling;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * Adds non-sensitive lifecycle messages around Xbox JSON-RPC signaling. The
 * transport's useful diagnostics are otherwise debug-only, which makes an
 * unsuccessful friend connection indistinguishable from a frozen client.
 */
public final class ViaFabricPlusNetherNetXboxRpcSignaling extends NetherNetXboxRpcSignaling {

    public ViaFabricPlusNetherNetXboxRpcSignaling(final String authorizationHeader) {
        super(authorizationHeader);
    }

    @Override
    protected void onConnected(final ChannelHandlerContext context) {
        ViaFabricPlusImpl.INSTANCE.getLogger().info("Xbox Friends signaling connected; requesting TURN credentials");
        super.onConnected(context);
    }

    @Override
    public void sendSignal(final String target, final String signal) {
        ViaFabricPlusImpl.INSTANCE.getLogger().info("Sending NetherNet WebRTC offer through Xbox Friends signaling");
        super.sendSignal(target, signal);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext context, final TextWebSocketFrame frame) {
        try {
            final JsonObject message = JsonParser.parseString(frame.text()).getAsJsonObject();
            if (message.has("error")) {
                final JsonElement error = message.get("error");
                ViaFabricPlusImpl.INSTANCE.getLogger().warn("Xbox Friends signaling returned an error: {}", summarizeError(error));
            } else if (message.has("method")) {
                ViaFabricPlusImpl.INSTANCE.getLogger().info("Xbox Friends signaling received method {}", message.get("method").getAsString());
            } else if (message.has("result")) {
                ViaFabricPlusImpl.INSTANCE.getLogger().info("Xbox Friends signaling request succeeded");
            }
        } catch (final Throwable throwable) {
            ViaFabricPlusImpl.INSTANCE.getLogger().warn("Could not inspect an Xbox Friends signaling response", throwable);
        }
        super.channelRead0(context, frame);
    }

    private static String summarizeError(final JsonElement error) {
        if (!error.isJsonObject()) {
            return error.toString();
        }
        final JsonObject object = error.getAsJsonObject();
        final String code = object.has("code") ? object.get("code").getAsString() : "unknown";
        final String message = object.has("message") ? object.get("message").getAsString() : "no message";
        return code + " (" + message + ")";
    }

}
