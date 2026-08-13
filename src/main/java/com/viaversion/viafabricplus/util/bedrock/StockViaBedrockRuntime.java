/*
 * This file is part of ViaFabricPlus - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viafabricplus.util.bedrock;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.HandlerNames;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

/**
 * Loads ViaBedrock's stable protocol implementation in a child-first class loader. This keeps
 * its packet tables, mappings, providers, and static state independent from the maintained
 * 1.26.40 implementation while both routes remain available in the same ViaVersion manager.
 */
public final class StockViaBedrockRuntime {

    private static final String EMBEDDED_JAR = "/viafabricplus/stock/ViaBedrock-stock-1.26.30.jar";
    private static final String VIA_BEDROCK_PACKAGE = "net.raphimc.viabedrock.";
    private static final String VIA_BEDROCK_ASSETS = "assets/viabedrock";

    private static ProtocolVersion stockVersion;
    private static ChildFirstClassLoader classLoader;

    private StockViaBedrockRuntime() {
    }

    public static synchronized void initialize(final Path dataFolder) {
        if (stockVersion != null) {
            return;
        }

        try {
            final Path runtimeFolder = dataFolder.resolve("stock-viabedrock");
            final Path runtimeJar = runtimeFolder.resolve("ViaBedrock-stock-1.26.30.jar");
            Files.createDirectories(runtimeFolder);
            try (InputStream input = StockViaBedrockRuntime.class.getResourceAsStream(EMBEDDED_JAR)) {
                if (input == null) {
                    throw new IOException("Missing embedded stock ViaBedrock runtime " + EMBEDDED_JAR);
                }
                Files.copy(input, runtimeJar, StandardCopyOption.REPLACE_EXISTING);
            }

            classLoader = new ChildFirstClassLoader(runtimeJar.toUri().toURL(), StockViaBedrockRuntime.class.getClassLoader());
            final Thread thread = Thread.currentThread();
            final ClassLoader previousContextLoader = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(classLoader);
                classLoader.loadClass("net.raphimc.viabedrock.ViaBedrockPlatformImpl").getConstructor().newInstance();
                stockVersion = (ProtocolVersion) classLoader.loadClass("net.raphimc.viabedrock.api.BedrockProtocolVersion")
                    .getField("bedrockLatest").get(null);
            } finally {
                thread.setContextClassLoader(previousContextLoader);
            }
            ViaFabricPlusImpl.INSTANCE.getLogger().info("Registered isolated stock ViaBedrock route {}", stockVersion.getName());
        } catch (ReflectiveOperationException | IOException e) {
            throw new IllegalStateException("Failed to initialize the isolated stock ViaBedrock runtime", e);
        }
    }

    public static ProtocolVersion stockVersion() {
        if (stockVersion == null) {
            throw new IllegalStateException("Stock ViaBedrock runtime has not been initialized");
        }
        return stockVersion;
    }

    public static boolean isStock(final ProtocolVersion version) {
        return stockVersion != null && stockVersion.equals(version);
    }

    public static boolean isBedrock(final ProtocolVersion version) {
        return BedrockProtocolVersion.bedrockLatest.equals(version) || isStock(version);
    }

    public static void installStockPipeline(final ChannelPipeline pipeline) {
        if (classLoader == null) {
            throw new IllegalStateException("Stock ViaBedrock runtime has not been initialized");
        }

        final ChannelHandler disconnect = newHandler("net.raphimc.viabedrock.netty.DisconnectHandler");
        final ChannelHandler message = newHandler("net.raphimc.viabedrock.netty.raknet.MessageCodec");
        final ChannelHandler batchLength = newHandler("net.raphimc.viabedrock.netty.BatchLengthCodec");
        final ChannelHandler packet = newHandler("net.raphimc.viabedrock.netty.PacketCodec");
        pipeline.addBefore(HandlerNames.SPLITTER, handlerName(disconnect), disconnect);
        pipeline.addBefore(HandlerNames.SPLITTER, handlerName(message), message);
        pipeline.replace(HandlerNames.SPLITTER, HandlerNames.SPLITTER, batchLength);
        pipeline.remove(HandlerNames.PREPENDER);
        pipeline.addBefore(ViaDecodeHandler.NAME, handlerName(packet), packet);
    }

    public static void putAuthData(final UserConnection connection, final String multiplayerToken, final KeyPair sessionKeyPair, final UUID deviceId) {
        if (classLoader == null) {
            throw new IllegalStateException("Stock ViaBedrock runtime has not been initialized");
        }
        try {
            final Class<?> authDataClass = classLoader.loadClass("net.raphimc.viabedrock.protocol.storage.AuthData");
            final Object authData = authDataClass.getConstructor(String.class, KeyPair.class, UUID.class)
                .newInstance(multiplayerToken, sessionKeyPair, deviceId);
            connection.put((StorableObject) authData);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create stock ViaBedrock auth data", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void sendOpenInventory(final UserConnection connection) {
        if (classLoader == null) {
            throw new IllegalStateException("Stock ViaBedrock runtime has not been initialized");
        }

        try {
            final Class<?> entityTrackerClass = classLoader.loadClass("net.raphimc.viabedrock.protocol.storage.EntityTracker");
            final Object entityTracker = connection.getStoredObjects().get(entityTrackerClass);
            if (entityTracker == null) {
                return;
            }
            final Object clientPlayer = entityTrackerClass.getMethod("getClientPlayer").invoke(entityTracker);
            if (clientPlayer == null) {
                return;
            }
            final long runtimeId = ((Number) clientPlayer.getClass().getMethod("runtimeId").invoke(clientPlayer)).longValue();

            final PacketType interactPacket = (PacketType) classLoader.loadClass("net.raphimc.viabedrock.protocol.ServerboundBedrockPackets")
                .getField("INTERACT").get(null);
            final Object openInventoryAction = classLoader.loadClass("net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InteractPacket_Action")
                .getField("OpenInventory").get(null);
            final short actionValue = ((Number) openInventoryAction.getClass().getMethod("getValue").invoke(openInventoryAction)).shortValue();
            final Class<?> bedrockTypes = classLoader.loadClass("net.raphimc.viabedrock.protocol.types.BedrockTypes");
            final Type<Long> unsignedVarLong = (Type<Long>) bedrockTypes.getField("UNSIGNED_VAR_LONG").get(null);
            final Type<Object> optionalPosition = (Type<Object>) bedrockTypes.getField("OPTIONAL_POSITION_3F").get(null);
            final Class<? extends Protocol> protocolClass = (Class<? extends Protocol>) classLoader.loadClass("net.raphimc.viabedrock.protocol.BedrockProtocol");

            final PacketWrapper interact = PacketWrapper.create(interactPacket, connection);
            interact.write(Types.UNSIGNED_BYTE, actionValue);
            interact.write(unsignedVarLong, runtimeId);
            interact.write(optionalPosition, null);
            interact.sendToServer(protocolClass);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ViaFabricPlusImpl.INSTANCE.getLogger().warn("Could not send stock ViaBedrock inventory interaction", e);
        }
    }

    private static ChannelHandler newHandler(final String name) {
        try {
            final Class<?> handlerClass = classLoader.loadClass(name);
            final Constructor<?> constructor = handlerClass.getConstructor();
            return (ChannelHandler) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create stock ViaBedrock handler " + name, e);
        }
    }

    private static String handlerName(final ChannelHandler handler) {
        try {
            final Field name = handler.getClass().getField("NAME");
            return (String) name.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Stock ViaBedrock handler has no NAME: " + handler.getClass().getName(), e);
        }
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {

        private ChildFirstClassLoader(final URL jar, final ClassLoader parent) {
            super(new URL[]{jar}, parent);
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && name.startsWith(VIA_BEDROCK_PACKAGE)) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                if (loaded == null) {
                    loaded = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        @Override
        public URL getResource(final String name) {
            if (name.startsWith(VIA_BEDROCK_ASSETS)) {
                final URL resource = findResource(name);
                if (resource != null) {
                    return resource;
                }
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            if (!name.startsWith(VIA_BEDROCK_ASSETS)) {
                return super.getResources(name);
            }
            final Set<URL> resources = new LinkedHashSet<>();
            resources.addAll(Collections.list(findResources(name)));
            resources.addAll(Collections.list(getParent().getResources(name)));
            return Collections.enumeration(resources);
        }

    }

}
