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

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BedrockSkinBridgeTest {

    @Test
    public void creatorTextureIsReducedToAnOpaqueStandardJavaModel() {
        final BufferedImage creatorSkin = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        creatorSkin.setRGB(20, 20, 0x00112233);
        creatorSkin.setRGB(80, 16, 0x00445566);

        final BufferedImage normalized = BedrockSkinBridge.normalizeSkin(creatorSkin);

        assertNotNull(normalized);
        assertEquals(64, normalized.getWidth());
        assertEquals(64, normalized.getHeight());
        assertEquals(0xFF112233, normalized.getRGB(10, 10));
        assertEquals(0x00445566, normalized.getRGB(40, 8));
    }

    @Test
    public void nonSkinPersonaAtlasFallsBackInsteadOfRegisteringIllegalGeometry() {
        assertNull(BedrockSkinBridge.normalizeSkin(
                new BufferedImage(96, 64, BufferedImage.TYPE_INT_ARGB)));
        assertNull(BedrockSkinBridge.normalizeSkin(
                new BufferedImage(2048, 2048, BufferedImage.TYPE_INT_ARGB)));
    }

    @Test
    public void signedJavaSkinBecomesTrustedStandardBedrockClaims() {
        final BufferedImage javaSkin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        javaSkin.setRGB(1, 1, 0xFF123456);
        final Map<String, Object> claims = new HashMap<>();
        claims.put("SkinGeometryData", "preserved-standard-geometry");

        assertTrue(BedrockSkinBridge.applyClientSkinClaims(claims, javaSkin, true, true, null));
        assertEquals(true, claims.get("TrustedSkin"));
        assertEquals(false, claims.get("PersonaSkin"));
        assertEquals("slim", claims.get("ArmSize"));
        assertEquals(64, claims.get("SkinImageWidth"));
        assertEquals(64, claims.get("SkinImageHeight"));
        assertEquals("preserved-standard-geometry", claims.get("SkinGeometryData"));

        final String resourcePatch = new String(Base64.getDecoder().decode(
                (String) claims.get("SkinResourcePatch")), StandardCharsets.UTF_8);
        assertTrue(resourcePatch.contains("geometry.humanoid.customSlim"));
    }

    @Test
    public void unsignedJavaTextureCannotClaimBedrockTrust() {
        final BufferedImage javaSkin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        final Map<String, Object> claims = new HashMap<>();

        assertFalse(BedrockSkinBridge.applyClientSkinClaims(claims, javaSkin, false, false, null));
        assertFalse(claims.containsKey("TrustedSkin"));
    }

    @Test
    public void bundledDefaultSkinIsAValidOfflineTrustedSource() {
        assertTrue(BedrockSkinBridge.isTrustedClientSource(false, false, true));
        assertTrue(BedrockSkinBridge.isTrustedClientSource(true, true, false));
        assertFalse(BedrockSkinBridge.isTrustedClientSource(false, true, false));
        assertFalse(BedrockSkinBridge.isTrustedClientSource(true, false, false));
    }

    @Test
    public void preparedJavaSkinWinsOnlyForTheLocalPlayer() {
        final UUID profileId = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        assertTrue(BedrockSkinBridge.shouldPreferPreparedClientSkin(profileId, profileId));
        assertFalse(BedrockSkinBridge.shouldPreferPreparedClientSkin(
                profileId, UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210")));
    }

}
