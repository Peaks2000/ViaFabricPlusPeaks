/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.util.bedrock;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.properties.Property;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

final class SignedJavaSkinProfileTest {

    @Test
    void cachedTextureMustHaveTheCurrentOwnerAValidSignatureAndAnOfficialUrl(@TempDir final Path directory) throws Exception {
        final var profile = new GameProfile(UUID.randomUUID(), "Local");
        final var value = Base64.getEncoder().encodeToString(
            ("{\"profileId\":\"" + profile.id().toString().replace("-", "") + "\"}").getBytes(StandardCharsets.UTF_8));
        final var cache = directory.resolve("skin.txt");
        Files.writeString(cache, value + "\nfixture-signature");

        final var signed = session(SignatureState.SIGNED, "https://textures.minecraft.net/texture/fixture");
        final var restored = SignedJavaSkinProfile.read(cache, signed, profile);
        assertNotNull(restored);
        assertEquals(profile.id(), restored.id());
        assertEquals(new Property("textures", value, "fixture-signature"),
            restored.properties().get("textures").iterator().next());
        assertNull(SignedJavaSkinProfile.read(cache, signed, new GameProfile(UUID.randomUUID(), "Other")));
        assertNull(SignedJavaSkinProfile.read(cache, session(SignatureState.INVALID,
            "https://textures.minecraft.net/texture/fixture"), profile));
        assertNull(SignedJavaSkinProfile.read(cache, session(SignatureState.SIGNED,
            "https://textures.minecraft.net.example.org/texture/fixture"), profile));
        Files.writeString(cache, value + "\n");
        assertNull(SignedJavaSkinProfile.read(cache, signed, profile));
    }

    private static SessionService session(final SignatureState signature, final String url) {
        return (SessionService) Proxy.newProxyInstance(SessionService.class.getClassLoader(), new Class<?>[]{SessionService.class},
            (proxy, method, args) -> {
                assertEquals("unpackTextures", method.getName());
                return new MinecraftProfileTextures(new MinecraftProfileTexture(url, Map.of()), null, null, signature);
            });
    }
}
