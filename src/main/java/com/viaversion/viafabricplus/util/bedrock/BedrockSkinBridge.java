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
 * involved. Classic Bedrock pixels are reduced to the ordinary Java wide/slim model. Persona
 * atlases are not classic skin textures, so they use Minecraft's deterministic bundled player
 * skin instead of painting the creator UV map onto the Java model.
 */
public final class BedrockSkinBridge {

    private static final int JAVA_SKIN_SIZE = 64;
    private static final int JAVA_CAPE_WIDTH = 64;
    private static final int JAVA_CAPE_HEIGHT = 32;
    private static final int MAX_SOURCE_DIMENSION = 1024;
    private static final int OPAQUE_ALPHA = 0xFF;
    private static final int DEFAULT_MISSING_BASE_COLOR = 0xFFB57B61;
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

    public static void applyClientPlayerSkin(final UserConnection connection, final Map<String, Object> claims) {
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
            ViaFabricPlusImpl.INSTANCE.getLogger().warn(
                    "Local Java skin was unavailable for the Bedrock login; retaining ViaBedrock's fallback skin");
            return;
        }
        if (applyClientSkinClaims(claims, clientSkin.skin(), clientSkin.slim(), clientSkin.secure(), clientSkin.cape())) {
            // ViaBedrock replaces the Java account UUID with its Bedrock identity UUID before
            // creating this login JWT. Register under that translated UUID as well so the local
            // Java PlayerInfo can resolve the same pixels even if the server omits self from its
            // initial player list.
            installClientSkin(connection, clientSkin, connection.getProtocolInfo().getUuid());
            if (clientSkin.signedMojangTexture()) {
                ViaFabricPlusImpl.INSTANCE.getLogger().info(
                        "Applied the signed local Java skin to the Bedrock login");
            } else {
                ViaFabricPlusImpl.INSTANCE.getLogger().warn(
                        "Applied Minecraft's bundled local fallback skin to the Bedrock login; the signed Java skin was unavailable");
            }
        }
    }

    public static void installBedrockSkin(final UserConnection connection, final UUID playerUuid, final SkinData skin) {
        if (connection != activeConnection || playerUuid == null || skin.skinData() == null) {
            return;
        }

        final ClientSkin clientSkin = latestClientSkin;
        if (clientSkin != null && shouldPreferPreparedClientSkin(
                connection.getProtocolInfo().getUuid(), playerUuid)) {
            installClientSkin(connection, clientSkin, playerUuid);
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final BufferedImage normalizedCape = normalizeCape(skin.capeData());
        if (requiresPersonaFallback(skin)) {
            minecraft.execute(() -> registerPersonaFallback(
                    minecraft, connection, playerUuid, normalizedCape));
            return;
        }

        final BufferedImage normalizedSkin = normalizeSkin(skin.skinData());
        if (normalizedSkin == null) {
            return;
        }
        final PlayerModelType model = isSlim(skin) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        minecraft.execute(() -> registerBedrockSkin(minecraft, connection, playerUuid, normalizedSkin, normalizedCape, model));
    }

    static boolean requiresPersonaFallback(final SkinData skin) {
        return skin.persona() || skin.personaPieces() != null && !skin.personaPieces().isEmpty();
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

        // Some high-resolution Bedrock payloads still reuse the right-limb UVs and leave both
        // modern left-limb regions empty. Recover only those wholly absent limbs before making the
        // Java base layer opaque; real asymmetric left-side pixels must remain authoritative.
        mirrorMissingLeftLimb(normalized, 0, 16, 0, 32, 16, 48, 0, 48);    // leg
        mirrorMissingLeftLimb(normalized, 40, 16, 40, 32, 32, 48, 48, 48); // arm

        // Classic/custom geometry may deliberately leave parts of the ordinary skin base layer
        // transparent. Flatten the matching legal overlay into each missing base part first and
        // use a representative opaque colour only where the texture has no legal pixels.
        final int globalFallback = representativeOpaqueColor(normalized, 0, 0,
                normalized.getWidth(), normalized.getHeight(), 0, 0, 0, 0, DEFAULT_MISSING_BASE_COLOR);
        repairBaseLayerPart(normalized, 0, 0, 32, 16, 32, 0, globalFallback);       // head
        repairBaseLayerPart(normalized, 0, 16, 16, 16, 0, 32, globalFallback);      // right leg
        repairBaseLayerPart(normalized, 16, 16, 24, 16, 16, 32, globalFallback);    // body
        repairBaseLayerPart(normalized, 40, 16, 16, 16, 40, 32, globalFallback);    // right arm
        repairBaseLayerPart(normalized, 16, 48, 16, 16, 0, 48, globalFallback);     // left leg
        repairBaseLayerPart(normalized, 32, 48, 16, 16, 48, 48, globalFallback);    // left arm
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
        // This is an intentional replacement for the Bedrock account's equipped appearance. A
        // false value lets trusted-skins-only peers retain the old/default appearance even though
        // the signed Java pixels were included in the login client data.
        claims.put("OverrideSkin", true);
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
        return new ClientSkin(profile.id(), skin, cape, playerSkin.model() == PlayerModelType.SLIM,
                true, downloadedFromMojang);
    }

    private static ClientSkin captureBuiltInClientSkin(final Minecraft minecraft, final GameProfile profile) {
        return captureClientSkin(minecraft, profile, Optional.of(DefaultPlayerSkin.get(profile)));
    }

    static boolean isTrustedClientSource(final boolean secure, final boolean downloadedFromMojang,
                                         final boolean builtInResource) {
        return builtInResource || secure && downloadedFromMojang;
    }

    static boolean shouldPreferPreparedClientSkin(final UUID translatedLocalPlayerId, final UUID incomingPlayerId) {
        return translatedLocalPlayerId != null && translatedLocalPlayerId.equals(incomingPlayerId);
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
        installClientSkin(connection, clientSkin, clientSkin != null ? clientSkin.profileId() : null);
    }

    private static void installClientSkin(final UserConnection connection, final ClientSkin clientSkin,
                                          final UUID playerUuid) {
        if (clientSkin == null || !clientSkin.secure() || connection != activeConnection) {
            return;
        }
        if (playerUuid == null) {
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
                minecraft, connection, playerUuid, normalizedSkin, normalizedCape, model));
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

    private static void registerPersonaFallback(final Minecraft minecraft, final UserConnection connection,
                                                final UUID playerUuid, final BufferedImage capeImage) {
        if (connection != activeConnection) {
            return;
        }

        final ClientSkin fallback = captureBuiltInClientSkin(minecraft, new GameProfile(playerUuid, ""));
        if (fallback == null) {
            return;
        }
        final BufferedImage normalizedSkin = normalizeSkin(fallback.skin());
        if (normalizedSkin == null) {
            return;
        }
        registerBedrockSkin(minecraft, connection, playerUuid, normalizedSkin, capeImage,
                fallback.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE);
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

    private static void mirrorMissingLeftLimb(final BufferedImage image,
                                              final int sourceBaseX, final int sourceBaseY,
                                              final int sourceOverlayX, final int sourceOverlayY,
                                              final int destinationBaseX, final int destinationBaseY,
                                              final int destinationOverlayX, final int destinationOverlayY) {
        if (hasVisiblePixel(image, destinationBaseX, destinationBaseY, 16, 16)
                || hasVisiblePixel(image, destinationOverlayX, destinationOverlayY, 16, 16)) {
            return;
        }
        mirrorLimbFaces(image, sourceBaseX, sourceBaseY, destinationBaseX, destinationBaseY);
        mirrorLimbFaces(image, sourceOverlayX, sourceOverlayY, destinationOverlayX, destinationOverlayY);
    }

    private static void mirrorLimbFaces(final BufferedImage image,
                                        final int sourceX, final int sourceY,
                                        final int destinationX, final int destinationY) {
        copyRect(image, sourceX + 4, sourceY, destinationX + 4, destinationY, 4, 4, true);
        copyRect(image, sourceX + 8, sourceY, destinationX + 8, destinationY, 4, 4, true);
        copyRect(image, sourceX, sourceY + 4, destinationX + 8, destinationY + 4, 4, 12, true);
        copyRect(image, sourceX + 4, sourceY + 4, destinationX + 4, destinationY + 4, 4, 12, true);
        copyRect(image, sourceX + 8, sourceY + 4, destinationX, destinationY + 4, 4, 12, true);
        copyRect(image, sourceX + 12, sourceY + 4, destinationX + 12, destinationY + 4, 4, 12, true);
    }

    private static boolean hasVisiblePixel(final BufferedImage image,
                                           final int minX, final int minY,
                                           final int width, final int height) {
        for (int y = minY; y < minY + height; y++) {
            for (int x = minX; x < minX + width; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void repairBaseLayerPart(final BufferedImage image,
                                            final int baseX, final int baseY,
                                            final int width, final int height,
                                            final int overlayX, final int overlayY,
                                            final int globalFallback) {
        final int fallback = representativeOpaqueColor(image, baseX, baseY, width, height,
                overlayX, overlayY, width, height, globalFallback);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int base = image.getRGB(baseX + x, baseY + y);
                final int overlay = image.getRGB(overlayX + x, overlayY + y);
                final int background = compositeOpaque(overlay, fallback);
                image.setRGB(baseX + x, baseY + y, compositeOpaque(base, background));
            }
        }
    }

    private static int representativeOpaqueColor(final BufferedImage image,
                                                  final int firstX, final int firstY,
                                                  final int firstWidth, final int firstHeight,
                                                  final int secondX, final int secondY,
                                                  final int secondWidth, final int secondHeight,
                                                  final int defaultColor) {
        final long[] totals = new long[4];
        accumulateVisibleColor(image, firstX, firstY, firstWidth, firstHeight, totals);
        accumulateVisibleColor(image, secondX, secondY, secondWidth, secondHeight, totals);
        if (totals[3] == 0) {
            return defaultColor;
        }
        return 0xFF000000
                | (int) (totals[0] / totals[3]) << 16
                | (int) (totals[1] / totals[3]) << 8
                | (int) (totals[2] / totals[3]);
    }

    private static void accumulateVisibleColor(final BufferedImage image,
                                               final int minX, final int minY,
                                               final int width, final int height,
                                               final long[] totals) {
        for (int y = minY; y < minY + height; y++) {
            for (int x = minX; x < minX + width; x++) {
                final int argb = image.getRGB(x, y);
                final int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }
                totals[0] += (long) ((argb >>> 16) & 0xFF) * alpha;
                totals[1] += (long) ((argb >>> 8) & 0xFF) * alpha;
                totals[2] += (long) (argb & 0xFF) * alpha;
                totals[3] += alpha;
            }
        }
    }

    private static int compositeOpaque(final int foreground, final int opaqueBackground) {
        final int alpha = foreground >>> 24;
        if (alpha == OPAQUE_ALPHA) {
            return foreground;
        }
        if (alpha == 0) {
            return opaqueBackground | 0xFF000000;
        }
        final int inverseAlpha = OPAQUE_ALPHA - alpha;
        final int red = (((foreground >>> 16) & 0xFF) * alpha
                + ((opaqueBackground >>> 16) & 0xFF) * inverseAlpha + 127) / OPAQUE_ALPHA;
        final int green = (((foreground >>> 8) & 0xFF) * alpha
                + ((opaqueBackground >>> 8) & 0xFF) * inverseAlpha + 127) / OPAQUE_ALPHA;
        final int blue = ((foreground & 0xFF) * alpha
                + (opaqueBackground & 0xFF) * inverseAlpha + 127) / OPAQUE_ALPHA;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static String shortHash(final byte[] data) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return java.util.HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record ClientSkin(UUID profileId, BufferedImage skin, BufferedImage cape, boolean slim, boolean secure,
                              boolean signedMojangTexture) {
    }

    private record RegisteredSkin(UserConnection connection, PlayerSkin skin, Identifier skinPath, Identifier capePath) {
    }

}
