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

package com.viaversion.viafabricplus.protocoltranslator;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.api.protocoltranslator.ProtocolTranslation;
import com.viaversion.viafabricplus.util.bedrock.CompatibilityViaBedrockRuntime;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;

public final class ProtocolTranslator {
    public static final AttributeKey<Connection> CLIENT_CONNECTION_ATTRIBUTE_KEY = ProtocolTranslationImpl.MINECRAFT_CONNECTION_ATTRIBUTE_KEY;
    public static final AttributeKey<ProtocolVersion> TARGET_VERSION_ATTRIBUTE_KEY = ProtocolTranslationImpl.TARGET_VERSION_ATTRIBUTE_KEY;
    public static final ProtocolVersion NATIVE_VERSION = ProtocolTranslationImpl.NATIVE_VERSION;
    public static final ProtocolVersion AUTO_DETECT_PROTOCOL = ProtocolTranslation.AUTO_DETECT_VERSION;

    private ProtocolTranslator() { }
    public static ProtocolVersion getTargetVersion() { return ViaFabricPlus.api().targetVersion(); }
    public static ProtocolVersion getTargetVersion(final Channel channel) { return ViaFabricPlus.api().protocolTranslation().targetVersion(channel); }
    public static void setTargetVersion(final ProtocolVersion version) { ViaFabricPlus.api().setTargetVersion(version); }
    public static void setTargetVersion(final ProtocolVersion version, final boolean restore) { ViaFabricPlus.api().protocolTranslation().setTargetVersion(version, restore); }
    public static boolean isBedrock() { return isBedrock(getTargetVersion()); }
    public static boolean isBedrock(final ProtocolVersion version) { return CompatibilityViaBedrockRuntime.isBedrock(version); }
    public static UserConnection getPlayNetworkUserConnection() { return ViaFabricPlus.api().protocolTranslation().userConnection(); }
    public static void injectPreviousVersionReset(final Channel channel) { ViaFabricPlusImpl.impl().protocolTranslation().injectionPreviousVersionHandler(channel); }
}
