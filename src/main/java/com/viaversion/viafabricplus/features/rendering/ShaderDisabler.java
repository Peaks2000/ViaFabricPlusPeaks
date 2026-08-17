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

package com.viaversion.viafabricplus.features.rendering;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.injection.ViaFabricPlusMixinPlugin;
import com.viaversion.viafabricplus.settings.impl.PerformanceSettings;
import io.netty.channel.Channel;
import java.lang.reflect.Method;

/**
 * Temporarily disables the Iris shaders while connected to specific server types.
 * <p>
 * All interactions with Iris go through its public API ({@code net.irisshaders.iris.api.v0})
 * via reflection so that this mod keeps working without Iris being installed.
 */
public final class ShaderDisabler {

    public enum ConnectionType {
        CLASSICUBE,
        BETACRAFT,
        BEDROCK,
        NONE
    }

    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String IRIS_API_CONFIG_CLASS = "net.irisshaders.iris.api.v0.IrisApiConfig";

    private static Object irisApi;
    private static Method getConfig;
    private static Method areShadersEnabled;
    private static Method setShadersEnabledAndApply;
    private static boolean irisApiUnavailable;

    /**
     * The channel of the server connection for which the shaders were disabled.
     * Shaders are only restored once this exact connection has been closed.
     */
    private static Channel activeChannel;

    public static boolean shouldDisableShaders(final ConnectionType type) {
        return switch (type) {
            case CLASSICUBE -> PerformanceSettings.INSTANCE.disableShadersOnClassicubeServers.getValue();
            case BETACRAFT -> PerformanceSettings.INSTANCE.disableShadersOnBetacraftServers.getValue();
            case BEDROCK -> PerformanceSettings.INSTANCE.disableShadersOnBedrockServers.getValue();
            case NONE -> false;
        };
    }

    /**
     * Must be called on the render thread when a connection to a server is started.
     * <p>
     * The channel is used to make this robust against connections that fail before this
     * deferred call is executed: if the connection already closed, the shaders are not
     * disabled, so they can never be left disabled after leaving the server.
     */
    public static void onServerJoin(final Channel channel, final ConnectionType type) {
        if (!ViaFabricPlusMixinPlugin.IRIS_PRESENT || irisApiUnavailable || activeChannel != null || !channel.isOpen() || !shouldDisableShaders(type)) {
            return;
        }

        initIrisApi();
        if (irisApiUnavailable) {
            return;
        }

        try {
            final Object config = getConfig.invoke(irisApi);
            if ((boolean) areShadersEnabled.invoke(config)) {
                setShadersEnabledAndApply.invoke(config, false);
                activeChannel = channel;
                ViaFabricPlusImpl.INSTANCE.getLogger().info("Disabled Iris shaders while connected to a {} server", type.name().toLowerCase());
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            irisApiUnavailable = true;
            ViaFabricPlusImpl.INSTANCE.getLogger().warn("Failed to disable Iris shaders", e);
        }
    }

    /**
     * Must be called on the render thread when the connection to the server is closed.
     */
    public static void onServerLeave(final Channel channel) {
        if (!ViaFabricPlusMixinPlugin.IRIS_PRESENT || irisApiUnavailable || activeChannel != channel) {
            return;
        }

        activeChannel = null;
        try {
            setShadersEnabledAndApply.invoke(getConfig.invoke(irisApi), true);
            ViaFabricPlusImpl.INSTANCE.getLogger().info("Re-enabled Iris shaders");
        } catch (ReflectiveOperationException | LinkageError e) {
            irisApiUnavailable = true;
            ViaFabricPlusImpl.INSTANCE.getLogger().warn("Failed to re-enable Iris shaders", e);
        }
    }

    private static void initIrisApi() {
        try {
            if (getConfig == null) {
                final Class<?> irisApiClass = Class.forName(IRIS_API_CLASS);
                irisApi = irisApiClass.getMethod("getInstance").invoke(null);
                getConfig = irisApiClass.getMethod("getConfig");

                final Class<?> irisApiConfigClass = Class.forName(IRIS_API_CONFIG_CLASS);
                areShadersEnabled = irisApiConfigClass.getMethod("areShadersEnabled");
                setShadersEnabledAndApply = irisApiConfigClass.getMethod("setShadersEnabledAndApply", boolean.class);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            irisApiUnavailable = true;
            ViaFabricPlusImpl.INSTANCE.getLogger().warn("Iris shader API is not available, shader disabling is disabled", e);
        }
    }

    private ShaderDisabler() {
    }

}