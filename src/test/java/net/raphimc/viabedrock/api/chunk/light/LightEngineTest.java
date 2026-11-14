/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.raphimc.viabedrock.api.chunk.light;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LightEngineTest {

    @Test
    void addOnLightPropagatesAcrossChunkBordersAndRespectsDampening() {
        final int[][][] region = new int[9][][];
        region[4] = new int[][]{new int[4096]};
        region[5] = new int[][]{new int[4096]};
        final int sourceIndex = 8 * 256 + 8 * 16 + 15;
        final int neighborIndex = sourceIndex - 15;
        region[4][0][sourceIndex] = LightEngine.lightOverride(13, 2);
        region[5][0][neighborIndex] = LightEngine.lightOverride(0, 2);
        final byte[] vanillaAir = {0};

        final ChunkLight[] light = LightEngine.computeRegionLight(region, 1, false, vanillaAir, vanillaAir);

        assertEquals(13, lightAt(light[4], sourceIndex));
        assertEquals(12, lightAt(light[4], sourceIndex - 1));
        assertEquals(11, lightAt(light[5], neighborIndex));
        assertEquals(10, lightAt(light[5], neighborIndex + 1));
    }

    private static int lightAt(final ChunkLight chunkLight, final int index) {
        final int packed = chunkLight.blockLight(1)[index / 2] & 0xFF;
        return (packed >>> ((index & 1) * 4)) & 0xF;
    }
}
