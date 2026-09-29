/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.viaversion.viafabricplus.util.bedrock;

import java.awt.image.BufferedImage;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.raphimc.viabedrock.protocol.model.SkinData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BedrockPersonaGeometryTest {
    private static final String BODY = "{\"name\":\"body\",\"pivot\":[0,12,0],\"cubes\":[{\"origin\":[-4,0,-2],\"size\":[8,12,4],\"uv\":[16,16]}]}";
    private static final String HEAD = "{\"name\":\"head\",\"parent\":\"body\",\"pivot\":[0,24,0],\"cubes\":[{\"origin\":[-4,24,-4],\"size\":[8,8,8],\"uv\":[0,0],\"rotation\":[0,45,0]}]}";

    @Test void preservesCreatorAtlasAndBakesTheParentSkeleton() {
        final SkinData skin = skin(BODY + "," + HEAD);
        final var geometry = BedrockPersonaGeometry.parse(skin);
        assertNotNull(geometry);
        assertFalse(BedrockSkinBridge.requiresPersonaFallback(skin));
        assertArrayEquals(new float[]{0,28,0}, geometry.bones().get("head").cubes().getFirst().pivot());
        final var body = geometry.bake(false).root().getChild("body");
        assertEquals(12, body.y);
        assertEquals(-12, body.getChild("head").y);
        final var cube = body.getChild("head").getChild("cube_0");
        assertEquals(-4, cube.y);
        assertEquals((float) (Math.PI / 4), cube.yRot, 0.00001);
        assertEquals(6, cube.getRandomCube(RandomSource.create(1)).polygons.length);
    }

    @Test void mirroredPerFaceUvChangesTextureAndKeepsOutwardNormals() {
        final var geometry = BedrockPersonaGeometry.parse(skin("{\"name\":\"body\",\"cubes\":[{\"origin\":[0,0,0],\"size\":[4,4,4],\"mirror\":true,\"uv\":{\"east\":{\"uv\":[8,12],\"uv_size\":[4,4]}}}]}"));
        assertNotNull(geometry);
        final var cube = geometry.bake(false).root().getChild("body").getChild("cube_0").getRandomCube(RandomSource.create(1));
        assertEquals(1, cube.polygons.length);
        final var polygon = cube.polygons[0];
        assertEquals(-1, polygon.normal().x());
        float minU = 1, maxU = 0;
        for (var vertex : polygon.vertices()) { minU = Math.min(minU, vertex.u()); maxU = Math.max(maxU, vertex.u()); }
        assertEquals(8F / 64, minU);
        assertEquals(12F / 64, maxU);
    }

    @Test void rejectsMissingPiecesCyclesAndInvalidAtlasBounds() {
        assertNull(BedrockPersonaGeometry.parse(skin(HEAD)));
        assertNull(BedrockPersonaGeometry.parse(skin(BODY.replace("\"body\"", "\"body\",\"parent\":\"head\"") + "," + HEAD)));
        assertNull(BedrockPersonaGeometry.parse(skin(BODY.replace("[16,16]", "[63,63]"))));
        assertNull(BedrockPersonaGeometry.parse(skin(BODY.replace("\"uv\":[16,16]", "\"inflate\":-8,\"uv\":[16,16]"))));
    }

    @Test void usesOnlyResourcePatchSelectedGeometry() {
        SkinData original = skin(BODY);
        SkinData missing = new SkinData(original.skinId(), "", "{\"geometry\":{\"default\":\"missing\"}}", original.skinData(), List.of(), null, original.geometryData(), "", "", false, true, false, false, "", "", "Wide", "#FFFFFFFF", List.of(), List.of(), false);
        assertNull(BedrockPersonaGeometry.parse(missing));
    }

    private static SkinData skin(final String bones) {
        return new SkinData("creator", "", "{\"geometry\":{\"default\":\"geometry.creator\"}}", new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB), List.of(), null,
            "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.creator\",\"texture_width\":64,\"texture_height\":64},\"bones\":[" + bones + "]}]}", "1.12.0", "", false, true, false, false, "", "", "Wide", "#FFFFFFFF", List.of(), List.of(), false);
    }
}
