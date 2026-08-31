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

package net.raphimc.viabedrock.api.resourcepack;

import net.raphimc.viabedrock.api.resourcepack.content.ZipContent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@link ResourcePack} against the "Missing manifest.json" crash where a server packages
 * its resource pack with the files nested under a subdirectory (e.g. Minehut). The pack must be
 * rebased so the directory holding the manifest sits at the root.
 */
public final class ResourcePackNestedManifestTest {

    private static final String MANIFEST = """
            {
              "format_version": 2,
              "header": {
                "name": "Test Pack",
                "uuid": "01234567-89ab-cdef-0123-456789abcdef",
                "version": [1, 0, 0]
              }
            }
            """;

    @Test
    void manifestNestedUnderADirectoryIsRebasedToThePackRoot() throws Exception {
        final byte[] zip = zip(
                "TestPack/manifest.json", MANIFEST,
                "TestPack/textures/blocks/test_block.png", "data"
        );

        final ResourcePack pack = new ResourcePack(new ZipContent(zip));

        assertNotNull(pack.id());
        assertEquals("Test Pack", pack.name());
        assertEquals("1.0.0", pack.version());
        assertTrue(pack.content().contains("textures/blocks/test_block.png"));
    }

    @Test
    void legacyPackManifestNestedUnderADirectoryIsRebasedToThePackRoot() throws Exception {
        final byte[] zip = zip(
                "pack/pack_manifest.json", MANIFEST,
                "pack/textures/blocks/test_block.png", "data"
        );

        final ResourcePack pack = new ResourcePack(new ZipContent(zip));

        assertEquals("Test Pack", pack.name());
        assertEquals("1.0.0", pack.version());
        assertTrue(pack.content().contains("textures/blocks/test_block.png"));
    }

    @Test
    void packWithoutAManifestAnywhereIsRejected() throws Exception {
        final byte[] zip = zip("random/file.txt", "hello");

        assertThrows(RuntimeException.class, () -> new ResourcePack(new ZipContent(zip)));
    }

    private static byte[] zip(final String... entries) throws Exception {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int i = 0; i < entries.length; i += 2) {
                zip.putNextEntry(new ZipEntry(entries[i]));
                zip.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
