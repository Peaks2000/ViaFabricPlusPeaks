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

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.raphimc.viabedrock.protocol.model.SkinData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public final class CompatibilityViaBedrockRuntimeTest {

    @Test
    public void ordinaryServersUseCurrentWireSemanticsThroughAnIsolatedRoute() {
        assertFalse(CompatibilityViaBedrockRuntime.isolatedRouteVersion().olderThanOrEqualTo(ProtocolVersion.v26_2));
        assertEquals(1001, CompatibilityViaBedrockRuntime.routeProtocolVersion());
        assertEquals(2225, CompatibilityViaBedrockRuntime.wireProtocolVersion());
        assertNotEquals(CompatibilityViaBedrockRuntime.routeProtocolVersion(), CompatibilityViaBedrockRuntime.wireProtocolVersion());
        assertEquals("Bedrock 1.26.63 (isolated servers)", CompatibilityViaBedrockRuntime.isolatedRouteVersion().getName());
    }

    @Test
    public void isolatedSkinProviderSharesOnlyRendererPayload(@TempDir final Path temporary) throws Exception {
        final Path jar = temporary.resolve("runtime.jar");
        try (var input = getClass().getResourceAsStream("/viafabricplus/compatibility/ViaBedrock-compatibility-1.26.63.jar")) {
            assertNotNull(input);
            Files.copy(input, jar);
        }
        final var loaderType = Class.forName(CompatibilityViaBedrockRuntime.class.getName() + "$ChildFirstClassLoader");
        final var constructor = loaderType.getDeclaredConstructor(URL.class, ClassLoader.class);
        constructor.setAccessible(true);
        try (var loader = (URLClassLoader) constructor.newInstance(jar.toUri().toURL(), getClass().getClassLoader())) {
            final var skinType = loader.loadClass("net.raphimc.viabedrock.protocol.model.SkinData");
            assertNotSame(SkinData.class, skinType);
            final var provider = loader.loadClass("com.viaversion.viafabricplus.protocoltranslator.impl.provider.viabedrock.ViaFabricPlusSkinProvider");
            assertSame(loader, provider.getClassLoader());
            assertSame(loader, provider.getSuperclass().getClassLoader());
            final var pixels = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            final Object skin = skinType.getConstructors()[0].newInstance("creator", "patch", pixels, List.of(), null,
                "geometry", "1.12.0", "", false, true, false, false, "", "", "slim", "", List.of(), List.of(), false);
            final var appearance = (BedrockSkinAppearance) provider.getMethod("appearance", skinType).invoke(null, skin);
            assertSame(pixels, appearance.skinData());
            assertTrue(appearance.persona());
            assertEquals("patch", appearance.skinResourcePatch());
            assertEquals("geometry", appearance.geometryData());
            assertEquals("slim", appearance.armSize());
        }
    }

}
