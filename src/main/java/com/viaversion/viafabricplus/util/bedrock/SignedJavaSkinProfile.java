/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.util.bedrock;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.services.ProfileResult;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.util.Util;

/** Retains the local account's signed texture property so vanilla can reuse its texture cache. */
final class SignedJavaSkinProfile {

    private static final int MAX_PROPERTY_BYTES = 16_384;
    private static final long RETRY_COOLDOWN = TimeUnit.SECONDS.toNanos(30);
    private static Retry lastRetry;

    private SignedJavaSkinProfile() {
    }

    static CompletableFuture<GameProfile> resolve(final SessionService service, final GameProfile startupProfile, final Path cache) {
        final Property startup = service.getPackedTextures(startupProfile);
        if (isValid(service, startupProfile.id(), startup)) {
            CompletableFuture.runAsync(() -> write(cache, startup), Util.backgroundExecutor());
            return CompletableFuture.completedFuture(startupProfile);
        }

        final var cached = CompletableFuture.supplyAsync(() -> read(cache, service, startupProfile), Util.backgroundExecutor());
        // Time out only the login's wait. A slower successful response can prime the next join.
        return retry(service, startupProfile, cache).copy().completeOnTimeout(null, 2, TimeUnit.SECONDS)
            .thenCompose(profile -> profile != null ? CompletableFuture.completedFuture(profile) : cached);
    }

    private static synchronized CompletableFuture<GameProfile> retry(final SessionService service, final GameProfile profile, final Path cache) {
        final long now = System.nanoTime();
        if (lastRetry != null && lastRetry.profileId().equals(profile.id())
            && (!lastRetry.lookup().isDone() || now - lastRetry.started() < RETRY_COOLDOWN)) {
            return lastRetry.lookup();
        }
        final var lookup = CompletableFuture.supplyAsync(() -> {
            final ProfileResult result = service.fetchProfile(profile.id(), true);
            if (result == null || !result.profile().id().equals(profile.id())) return null;
            final Property property = service.getPackedTextures(result.profile());
            if (!isValid(service, profile.id(), property)) return null;
            write(cache, property);
            return result.profile();
        }, Util.backgroundExecutor()).exceptionally(error -> {
            ViaFabricPlusImpl.impl().logger().debug("Could not refresh the signed Java skin profile", error);
            return null;
        });
        lastRetry = new Retry(profile.id(), now, lookup);
        return lookup;
    }

    static GameProfile read(final Path cache, final SessionService service, final GameProfile profile) {
        try (var input = Files.newInputStream(cache)) {
            final byte[] data = input.readNBytes(MAX_PROPERTY_BYTES + 1);
            if (data.length > MAX_PROPERTY_BYTES) return null;
            final String[] fields = new String(data, StandardCharsets.UTF_8).split("\\R", -1);
            if (fields.length != 2 || fields[0].isBlank() || fields[1].isBlank()) return null;
            final Property property = new Property("textures", fields[0], fields[1]);
            return isValid(service, profile.id(), property)
                ? new GameProfile(profile.id(), profile.name(), new PropertyMap(ImmutableMultimap.of("textures", property)))
                : null;
        } catch (final NoSuchFileException ignored) {
            return null;
        } catch (final IOException e) {
            ViaFabricPlusImpl.impl().logger().debug("Could not read the local signed skin profile", e);
            return null;
        }
    }

    static boolean isValid(final SessionService service, final UUID profileId, final Property property) {
        if (property == null || !property.hasSignature()
            || property.value().length() + property.signature().length() >= MAX_PROPERTY_BYTES) return false;
        try {
            final var payload = JsonParser.parseString(new String(Base64.getDecoder().decode(property.value()),
                StandardCharsets.UTF_8)).getAsJsonObject();
            final String owner = payload.get("profileId").getAsString().replace("-", "");
            if (!profileId.toString().replace("-", "").equalsIgnoreCase(owner)) return false;
            final var textures = service.unpackTextures(property);
            return textures.signatureState() == SignatureState.SIGNED && textures.skin() != null
                && BedrockSkinBridge.isOfficialTexture(textures.skin().getUrl());
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    private static void write(final Path cache, final Property property) {
        try {
            final Path temporary = Files.createTempFile(cache.getParent(), "java-skin-", ".tmp");
            try {
                Files.writeString(temporary, property.value() + "\n" + property.signature());
                try {
                    Files.move(temporary, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (final AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, cache, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (final IOException e) {
            ViaFabricPlusImpl.impl().logger().debug("Could not cache the signed Java skin profile", e);
        }
    }

    private record Retry(UUID profileId, long started, CompletableFuture<GameProfile> lookup) { }
}
