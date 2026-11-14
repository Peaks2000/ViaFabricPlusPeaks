/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.util.bedrock;

import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class BedrockSkinAnimationTest {

    private static final String PATCH = """
        {"geometry":{"default":"geometry.base","animated_face":"geometry.blink","animated_32x32":"geometry.portal",
        "animated_128x128":"geometry.hd"}}
        """;

    @Test
    void pairsBodyAndFaceStripsWithTheirOwnGeometry() {
        final var body = pixels(32, 96);
        final var hdBody = pixels(128, 256);
        final var face = pixels(16, 32);
        final var skin = new BedrockSkinAppearance(pixels(32, 32), null, PATCH, """
            {"minecraft:geometry":[
            %s,%s,%s,%s]}
            """.formatted(geometry("base", 32, 32, "body"), geometry("portal", 32, 96, "portal"),
                geometry("hd", 128, 128, "layer"), geometry("blink", 16, 16, "head")),
            "slim", true, false, List.of(new BedrockSkinAppearance.Animation(face, 1, 2F, 1),
                new BedrockSkinAppearance.Animation(body, 2, 3F, 0), new BedrockSkinAppearance.Animation(hdBody, 3, 2F, 0)));
        final var layers = BedrockSkinAnimation.layers(skin);
        assertEquals(3, layers.size());
        assertSame(body, layers.get(0).strip());
        assertEquals(32, layers.get(0).geometry().textureHeight());
        assertTrue(layers.get(0).geometry().bones().containsKey("portal"));
        assertTrue(layers.get(0).body());
        assertSame(hdBody, layers.get(1).strip());
        assertSame(face, layers.get(2).strip());
        assertTrue(layers.get(2).geometry().bones().containsKey("head"));
        assertTrue(layers.get(2).blinking());
        assertNotNull(BedrockPersonaGeometry.parse(skin));
    }

    @Test
    void invalidStripsLeaveTheStaticCreatorGeometryUsable() {
        final var skin = new BedrockSkinAppearance(pixels(32, 32), null, PATCH, """
            {"minecraft:geometry":[%s,%s]}
            """.formatted(geometry("base", 32, 32, "head"), geometry("portal", 32, 32, "head")),
            "slim", true, false, List.of(new BedrockSkinAppearance.Animation(pixels(32, 65), 2, 2F, 0),
                new BedrockSkinAppearance.Animation(pixels(32, 64), 2, Float.NaN, 0),
                new BedrockSkinAppearance.Animation(pixels(32, 64), 2, 2.5F, 0),
                new BedrockSkinAppearance.Animation(pixels(32, 64), 2, 2F, 99),
                new BedrockSkinAppearance.Animation(pixels(16, 32), 1, 2F, 1)));
        assertTrue(BedrockSkinAnimation.layers(skin).isEmpty());
        assertNotNull(BedrockPersonaGeometry.parse(skin));
    }

    @Test
    void loopsAtSevenFramesPerSecondAndSamplesInsideTheSelectedFrame() {
        assertEquals(0, BedrockSkinAnimation.frame(0, 0, 4, false));
        assertEquals(1, BedrockSkinAnimation.frame(3, 0, 4, false));
        assertEquals(2, BedrockSkinAnimation.frame(6, 0, 4, false));
        assertEquals(0, BedrockSkinAnimation.frame(12, 0, 4, false));
        assertEquals(0, BedrockSkinAnimation.frame(79, 0, 2, true));
        assertEquals(1, BedrockSkinAnimation.frame(81, 0, 2, true));
        assertEquals(0, BedrockSkinAnimation.frame(83, 0, 2, true));
        final var uv = new BedrockSkinAnimation.FrameUv(1, 4, 128);
        assertEquals(0.5F, uv.getU(0.5F));
        assertEquals(0.375F, uv.getV(0.5F));
        assertTrue(uv.getV(0) > 0.25F);
        assertTrue(uv.getV(1) < 0.5F);
    }

    @Test
    void bakesPersonaMeshesWithBottomOriginUvsAndFrameStripCoordinates() {
        final var skin = new BedrockSkinAppearance(pixels(32, 32), null, PATCH, """
            {"minecraft:geometry":[%s,%s]}
            """.formatted(meshGeometry("base", 256, 128, "[[0,0],[1,0],[1,1],[0,1]]"),
                meshGeometry("blink", 32, 64, "[[0,0.5],[1,0.5],[1,1],[0,1]]")),
            "slim", true, false, List.of(new BedrockSkinAppearance.Animation(pixels(32, 64), 1, 2F, 1)));
        final var body = BedrockPersonaGeometry.parse(skin);
        assertNotNull(body);
        final var face = BedrockSkinAnimation.layers(skin).getFirst().geometry();
        final var polygon = face.bones().get("head").polygons().getFirst();
        assertEquals(-1F, polygon.normal().z());
        assertEquals(1F, polygon.vertices()[0].v());
        assertEquals(0F, polygon.vertices()[2].v());
        assertEquals(-8F, polygon.vertices()[2].y());
        final var baked = face.bake(true).root().getChild("head")
            .getRandomCube(net.minecraft.util.RandomSource.create(1));
        assertSame(polygon, baked.polygons[0]);
    }

    @Test
    void rejectsOutOfRangePersonaMeshIndices() {
        final String broken = meshGeometry("base", 32, 32, "[[0,0],[1,0],[1,1],[0,1]]")
            .replace("[3,0,3]", "[99,0,3]");
        final var skin = new BedrockSkinAppearance(pixels(32, 32), null, PATCH,
            "{\"minecraft:geometry\":[" + broken + "]}", "slim", true, false, List.of());
        assertNull(BedrockPersonaGeometry.parse(skin));
    }

    private static String meshGeometry(final String id, final int width, final int height, final String uv) {
        return """
            {"description":{"identifier":"geometry.%s","texture_width":%d,"texture_height":%d},
            "bones":[{"name":"head","pivot":[0,24,0],"poly_mesh":{"normalized_uvs":true,
            "positions":[[-4,24,-4],[4,24,-4],[4,32,-4],[-4,32,-4]],"normals":[[0,0,-1]],
            "uvs":%s,"polys":[[[0,0,0],[1,0,1],[2,0,2],[3,0,3]]]}}]}
            """.formatted(id, width, height, uv);
    }

    private static BufferedImage pixels(final int width, final int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private static String geometry(final String id, final int width, final int height, final String bone) {
        return """
            {"description":{"identifier":"geometry.%s","texture_width":%d,"texture_height":%d},
            "bones":[{"name":"%s","cubes":[{"origin":[-2,0,-2],"size":[4,4,4],"uv":[0,0]}]}]}
            """.formatted(id, width, height, bone);
    }
}
