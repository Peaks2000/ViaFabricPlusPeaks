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

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.raphimc.viabedrock.protocol.model.SkinData;
import net.raphimc.viabedrock.protocol.types.primitive.ImageType;

/**
 * Bridges Bedrock skin packets into Minecraft's local texture manager and converts the signed
 * Java player's already-loaded skin into Bedrock client-data claims. No skin upload service is
 * involved. Unsupported Bedrock geometry and persona pieces are deliberately reduced to the
 * ordinary Java wide/slim model.
 */
public final class BedrockSkinBridge {

    private static final int JAVA_SKIN_SIZE = 64;
    private static final int JAVA_CAPE_WIDTH = 64;
    private static final int JAVA_CAPE_HEIGHT = 32;
    private static final int MAX_SOURCE_DIMENSION = 1024;
    private static final long CLIENT_SKIN_WAIT_SECONDS = 5L;
    private static final long OFFLINE_FALLBACK_DELAY_SECONDS = 4L;
    private static final String WIDE_RESOURCE_PATCH = "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}";
    private static final String SLIM_RESOURCE_PATCH = "{\"geometry\":{\"default\":\"geometry.humanoid.customSlim\"}}";

    private static final Map<UUID, RegisteredSkin> BEDROCK_SKINS = new ConcurrentHashMap<>();
    private static final AtomicLong TEXTURE_SEQUENCE = new AtomicLong();

    private static volatile UserConnection activeConnection;
    private static volatile CompletableFuture<ClientSkin> preparedClientSkin = CompletableFuture.completedFuture(null);
    private static volatile ClientSkin latestClientSkin;

    private BedrockSkinBridge() {
    }

    /**
     * Starts Minecraft's normal signed skin lookup before the Bedrock login packet needs it.
     * The lookup uses Mojang's own session service and texture cache. If that cannot complete
     * offline, the profile's deterministic bundled Minecraft skin is used instead.
     */
    public static void prepareClientSkin() {
        final Minecraft minecraft = Minecraft.getInstance();
        final CompletableFuture<ClientSkin> prepared = new CompletableFuture<>();
        preparedClientSkin = prepared;
        latestClientSkin = null;

        minecraft.execute(() -> {
            final GameProfile profile = minecraft.getGameProfile();
            final ClientSkin fallback = captureBuiltInClientSkin(minecraft, profile);
            CompletableFuture.delayedExecutor(OFFLINE_FALLBACK_DELAY_SECONDS, TimeUnit.SECONDS)
                    .execute(() -> acceptPreparedClientSkin(prepared, fallback, false));
            minecraft.getSkinManager().get(profile).whenComplete((skin, error) -> minecraft.execute(() -> {
                if (error != null) {
                    ViaFabricPlusImpl.INSTANCE.getLogger().debug("Could not prepare the local Java skin for Bedrock", error);
                    acceptPreparedClientSkin(prepared, fallback, false);
                    return;
                }
                final ClientSkin captured = captureClientSkin(minecraft, profile, skin);
                acceptPreparedClientSkin(prepared, captured != null ? captured : fallback, captured != null);
            }));
        });
    }

    public static void beginConnection(final UserConnection connection) {
        if (activeConnection == connection) {
            return;
        }
        activeConnection = connection;
        releaseAllRegisteredSkins();
        preparedClientSkin.thenAccept(clientSkin -> installClientSkin(connection, clientSkin));
    }

    public static void endConnection(final UserConnection connection) {
        if (activeConnection != connection) {
            return;
        }
        activeConnection = null;
        releaseAllRegisteredSkins();
    }

    public static void applyClientPlayerSkin(final Map<String, Object> claims) {
        final ClientSkin clientSkin;
        try {
            clientSkin = preparedClientSkin.get(CLIENT_SKIN_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            ViaFabricPlusImpl.INSTANCE.getLogger().debug("Could not read the prepared Java skin for Bedrock", e);
            return;
        }

        if (clientSkin == null || !java.util.Objects.equals(
                clientSkin.profileId(), Minecraft.getInstance().getGameProfile().id())) {
            return;
        }
        applyClientSkinClaims(claims, clientSkin.skin(), clientSkin.slim(), clientSkin.secure(), clientSkin.cape());
    }

    public static void installBedrockSkin(final UserConnection connection, final UUID playerUuid, final SkinData skin) {
        if (connection != activeConnection || skin.skinData() == null) {
            return;
        }

        final ClientSkin clientSkin = latestClientSkin;
        if (clientSkin != null && shouldPreferPreparedClientSkin(clientSkin.profileId(), playerUuid)) {
            installClientSkin(connection, clientSkin);
            return;
        }

        final BufferedImage normalizedSkin = normalizeSkin(skin.skinData());
        if (normalizedSkin == null) {
            return;
        }
        final BufferedImage normalizedCape = normalizeCape(skin.capeData());
        final PlayerModelType model = isSlim(skin) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> registerBedrockSkin(minecraft, connection, playerUuid, normalizedSkin, normalizedCape, model));
    }

    public static PlayerSkin localBedrockSkin(final UUID playerUuid, final PlayerSkin fallback) {
        final UserConnection connection = activeConnection;
        if (connection == null || ProtocolTranslator.getPlayNetworkUserConnection() != connection) {
            return fallback;
        }
        final RegisteredSkin registered = BEDROCK_SKINS.get(playerUuid);
        return registered != null && registered.connection() == connection ? registered.skin() : fallback;
    }

    static BufferedImage normalizeSkin(final BufferedImage source) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0
                || source.getWidth() > MAX_SOURCE_DIMENSION || source.getHeight() > MAX_SOURCE_DIMENSION) {
            return null;
        }

        final BufferedImage normalized;
        if (source.getWidth() == source.getHeight() && source.getWidth() >= JAVA_SKIN_SIZE) {
            normalized = scaleNearest(source, JAVA_SKIN_SIZE, JAVA_SKIN_SIZE);
        } else if (source.getWidth() == source.getHeight() * 2 && source.getWidth() >= JAVA_SKIN_SIZE) {
            normalized = expandLegacySkin(scaleNearest(source, JAVA_SKIN_SIZE, JAVA_SKIN_SIZE / 2));
        } else {
            return null;
        }

        forceOpaque(normalized, 0, 0, 32, 16);
        forceOpaque(normalized, 0, 16, 64, 32);
        forceOpaque(normalized, 16, 48, 48, 64);
        return normalized;
    }

    static BufferedImage normalizeCape(final BufferedImage source) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0
                || source.getWidth() > MAX_SOURCE_DIMENSION || source.getHeight() > MAX_SOURCE_DIMENSION
                || source.getWidth() != source.getHeight() * 2) {
            return null;
        }
        return scaleNearest(source, JAVA_CAPE_WIDTH, JAVA_CAPE_HEIGHT);
    }

    static boolean applyClientSkinClaims(final Map<String, Object> claims, final BufferedImage skin,
                                         final boolean slim, final boolean secure, final BufferedImage cape) {
        final BufferedImage normalizedSkin = normalizeSkin(skin);
        if (normalizedSkin == null || !secure) {
            return false;
        }

        final byte[] skinBytes = ImageType.getImageData(normalizedSkin);
        final String skinHash = shortHash(skinBytes);
        claims.put("SkinId", "ViaFabricPlusPeaks-" + skinHash);
        claims.put("SkinData", Base64.getEncoder().encodeToString(skinBytes));
        claims.put("SkinImageWidth", normalizedSkin.getWidth());
        claims.put("SkinImageHeight", normalizedSkin.getHeight());
        claims.put("SkinResourcePatch", Base64.getEncoder().encodeToString(
                (slim ? SLIM_RESOURCE_PATCH : WIDE_RESOURCE_PATCH).getBytes(StandardCharsets.UTF_8)));
        claims.put("ArmSize", slim ? "slim" : "wide");
        claims.put("PremiumSkin", false);
        claims.put("PersonaSkin", false);
        claims.put("TrustedSkin", true);
        claims.put("OverrideSkin", false);
        claims.put("AnimatedImageData", java.util.List.of());
        claims.put("PersonaPieces", java.util.List.of());
        claims.put("PieceTintColors", java.util.List.of());

        final BufferedImage normalizedCape = normalizeCape(cape);
        if (normalizedCape != null) {
            final byte[] capeBytes = ImageType.getImageData(normalizedCape);
            claims.put("CapeId", "ViaFabricPlusPeaks-" + shortHash(capeBytes));
            claims.put("CapeData", Base64.getEncoder().encodeToString(capeBytes));
            claims.put("CapeImageWidth", normalizedCape.getWidth());
            claims.put("CapeImageHeight", normalizedCape.getHeight());
            claims.put("CapeOnClassicSkin", true);
        }
        return true;
    }

    private static ClientSkin captureClientSkin(final Minecraft minecraft, final GameProfile profile,
                                                final Optional<PlayerSkin> optionalSkin) {
        if (optionalSkin.isEmpty()) {
            return null;
        }
        final PlayerSkin playerSkin = optionalSkin.get();
        final boolean downloadedFromMojang = playerSkin.body() instanceof ClientAsset.DownloadedTexture downloaded
                && isOfficialTexture(downloaded.url());
        final boolean builtInResource = playerSkin.body() instanceof ClientAsset.ResourceTexture;
        if (!isTrustedClientSource(playerSkin.secure(), downloadedFromMojang, builtInResource)) {
            return null;
        }

        final BufferedImage skin = copyTexturePixels(minecraft, playerSkin.body());
        if (skin == null) {
            return null;
        }
        final BufferedImage cape = copyTexturePixels(minecraft, playerSkin.cape());
        return new ClientSkin(profile.id(), skin, cape, playerSkin.model() == PlayerModelType.SLIM, true);
    }

    private static ClientSkin captureBuiltInClientSkin(final Minecraft minecraft, final GameProfile profile) {
        return captureClientSkin(minecraft, profile, Optional.of(DefaultPlayerSkin.get(profile)));
    }

    static boolean isTrustedClientSource(final boolean secure, final boolean downloadedFromMojang,
                                         final boolean builtInResource) {
        return builtInResource || secure && downloadedFromMojang;
    }

    static boolean shouldPreferPreparedClientSkin(final UUID profileId, final UUID incomingPlayerId) {
        return profileId != null && profileId.equals(incomingPlayerId);
    }

    private static void acceptPreparedClientSkin(final CompletableFuture<ClientSkin> prepared,
                                                 final ClientSkin clientSkin,
                                                 final boolean replaceCompletedFallback) {
        if (preparedClientSkin != prepared) {
            return;
        }
        if (clientSkin == null) {
            prepared.complete(null);
            return;
        }

        final boolean firstResult = prepared.complete(clientSkin);
        if (!firstResult && !replaceCompletedFallback) {
            return;
        }
        latestClientSkin = clientSkin;
        final UserConnection connection = activeConnection;
        if (connection != null) {
            installClientSkin(connection, clientSkin);
        }
    }

    private static boolean isOfficialTexture(final String url) {
        try {
            final java.net.URI uri = java.net.URI.create(url);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && "textures.minecraft.net".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static BufferedImage copyTexturePixels(final Minecraft minecraft, final ClientAsset.Texture texture) {
        if (texture == null) {
            return null;
        }
        final AbstractTexture abstractTexture = minecraft.getTextureManager().getTexture(texture.texturePath());
        if (abstractTexture instanceof DynamicTexture dynamicTexture && dynamicTexture.getPixels() != null) {
            return copyPixels(dynamicTexture.getPixels());
        }

        if (texture instanceof ClientAsset.ResourceTexture) {
            final Optional<Resource> resource = minecraft.getResourceManager().getResource(texture.texturePath());
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open(); NativeImage pixels = NativeImage.read(stream)) {
                    return copyPixels(pixels);
                } catch (IOException e) {
                    ViaFabricPlusImpl.INSTANCE.getLogger().debug(
                            "Could not read bundled fallback skin {}", texture.texturePath(), e);
                }
            }
        }
        return null;
    }

    private static BufferedImage copyPixels(final NativeImage pixels) {
        final BufferedImage image = new BufferedImage(pixels.getWidth(), pixels.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) {
            for (int x = 0; x < pixels.getWidth(); x++) {
                image.setRGB(x, y, pixels.getPixel(x, y));
            }
        }
        return image;
    }

    private static void installClientSkin(final UserConnection connection, final ClientSkin clientSkin) {
        if (clientSkin == null || !clientSkin.secure() || connection != activeConnection) {
            return;
        }
        final BufferedImage normalizedSkin = normalizeSkin(clientSkin.skin());
        if (normalizedSkin == null) {
            return;
        }
        final BufferedImage normalizedCape = normalizeCape(clientSkin.cape());
        final PlayerModelType model = clientSkin.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> registerBedrockSkin(
                minecraft, connection, clientSkin.profileId(), normalizedSkin, normalizedCape, model));
    }

    private static void registerBedrockSkin(final Minecraft minecraft, final UserConnection connection,
                                            final UUID playerUuid, final BufferedImage skinImage,
                                            final BufferedImage capeImage, final PlayerModelType model) {
        if (connection != activeConnection) {
            return;
        }

        final RegisteredSkin previous = BEDROCK_SKINS.remove(playerUuid);
        if (previous != null) {
            releaseRegisteredSkin(minecraft, previous);
        }

        final long sequence = TEXTURE_SEQUENCE.incrementAndGet();
        final Identifier skinPath = texturePath(playerUuid, "skin", sequence);
        minecraft.getTextureManager().register(skinPath, dynamicTexture(playerUuid, "skin", skinImage));
        final ClientAsset.Texture body = new ClientAsset.DownloadedTexture(skinPath, "local://viafabricplus/bedrock-skin/" + playerUuid);

        Identifier capePath = null;
        ClientAsset.Texture cape = null;
        if (capeImage != null) {
            capePath = texturePath(playerUuid, "cape", sequence);
            minecraft.getTextureManager().register(capePath, dynamicTexture(playerUuid, "cape", capeImage));
            cape = new ClientAsset.DownloadedTexture(capePath, "local://viafabricplus/bedrock-cape/" + playerUuid);
        }

        BEDROCK_SKINS.put(playerUuid, new RegisteredSkin(
                connection, new PlayerSkin(body, cape, null, model, true), skinPath, capePath));
    }

    private static DynamicTexture dynamicTexture(final UUID playerUuid, final String kind, final BufferedImage image) {
        final NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setPixel(x, y, image.getRGB(x, y));
            }
        }
        return new DynamicTexture(() -> "ViaFabricPlus " + kind + " for " + playerUuid, nativeImage);
    }

    private static Identifier texturePath(final UUID playerUuid, final String kind, final long sequence) {
        return Identifier.fromNamespaceAndPath("viafabricplus",
                "bedrock_skins/" + playerUuid.toString().replace("-", "") + "/" + kind + "_" + sequence);
    }

    private static void releaseAllRegisteredSkins() {
        final RegisteredSkin[] registered = BEDROCK_SKINS.values().toArray(RegisteredSkin[]::new);
        BEDROCK_SKINS.clear();
        if (registered.length == 0) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            for (RegisteredSkin skin : registered) {
                releaseRegisteredSkin(minecraft, skin);
            }
        });
    }

    private static void releaseRegisteredSkin(final Minecraft minecraft, final RegisteredSkin skin) {
        minecraft.getTextureManager().release(skin.skinPath());
        if (skin.capePath() != null) {
            minecraft.getTextureManager().release(skin.capePath());
        }
    }

    private static boolean isSlim(final SkinData skin) {
        final String resourcePatch = skin.skinResourcePatch();
        return "slim".equalsIgnoreCase(skin.armSize())
                || resourcePatch != null && resourcePatch.toLowerCase(java.util.Locale.ROOT).contains("customslim");
    }

    private static BufferedImage scaleNearest(final BufferedImage source, final int width, final int height) {
        final BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            final int sourceY = y * source.getHeight() / height;
            for (int x = 0; x < width; x++) {
                final int sourceX = x * source.getWidth() / width;
                scaled.setRGB(x, y, source.getRGB(sourceX, sourceY));
            }
        }
        return scaled;
    }

    private static BufferedImage expandLegacySkin(final BufferedImage legacy) {
        final BufferedImage expanded = new BufferedImage(JAVA_SKIN_SIZE, JAVA_SKIN_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < legacy.getHeight(); y++) {
            for (int x = 0; x < legacy.getWidth(); x++) {
                expanded.setRGB(x, y, legacy.getRGB(x, y));
            }
        }

        copyRect(expanded, 4, 16, 20, 48, 4, 4, true);
        copyRect(expanded, 8, 16, 24, 48, 4, 4, true);
        copyRect(expanded, 0, 20, 24, 52, 4, 12, true);
        copyRect(expanded, 4, 20, 20, 52, 4, 12, true);
        copyRect(expanded, 8, 20, 16, 52, 4, 12, true);
        copyRect(expanded, 12, 20, 28, 52, 4, 12, true);
        copyRect(expanded, 44, 16, 36, 48, 4, 4, true);
        copyRect(expanded, 48, 16, 40, 48, 4, 4, true);
        copyRect(expanded, 40, 20, 40, 52, 4, 12, true);
        copyRect(expanded, 44, 20, 36, 52, 4, 12, true);
        copyRect(expanded, 48, 20, 32, 52, 4, 12, true);
        copyRect(expanded, 52, 20, 44, 52, 4, 12, true);
        return expanded;
    }

    private static void copyRect(final BufferedImage image, final int sourceX, final int sourceY,
                                 final int destinationX, final int destinationY,
                                 final int width, final int height, final boolean mirrorX) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int readX = mirrorX ? sourceX + width - 1 - x : sourceX + x;
                image.setRGB(destinationX + x, destinationY + y, image.getRGB(readX, sourceY + y));
            }
        }
    }

    private static void forceOpaque(final BufferedImage image, final int minX, final int minY,
                                    final int maxX, final int maxY) {
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                image.setRGB(x, y, image.getRGB(x, y) | 0xFF000000);
            }
        }
    }

    private static String shortHash(final byte[] data) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return java.util.HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record ClientSkin(UUID profileId, BufferedImage skin, BufferedImage cape, boolean slim, boolean secure) {
    }

    private record RegisteredSkin(UserConnection connection, PlayerSkin skin, Identifier skinPath, Identifier capePath) {
    }

}
