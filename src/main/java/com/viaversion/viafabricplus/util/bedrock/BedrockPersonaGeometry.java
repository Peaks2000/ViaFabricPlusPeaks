/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.util.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.raphimc.viabedrock.protocol.model.SkinData;

/** Bakes a self-contained Bedrock creator atlas and cube skeleton locally, without uploads. */
public final class BedrockPersonaGeometry {

    private static final int MAX_BONES = 128;
    private static final int MAX_CUBES = 512;
    private static final int MAX_JSON_LENGTH = 1_048_576;

    private BedrockPersonaGeometry() {
    }

    public static Geometry parse(final SkinData skin) {
        try {
            final BufferedImage atlas = skin.skinData();
            require(atlas != null && atlas.getWidth() > 0 && atlas.getHeight() > 0
                && atlas.getWidth() <= 2048 && atlas.getHeight() <= 2048);
            final JsonObject patch = boundedJson(skin.skinResourcePatch());
            final String identifier = patch.getAsJsonObject("geometry").get("default").getAsString();
            final JsonObject document = boundedJson(skin.geometryData());
            JsonObject geometry = null;
            JsonObject description = null;
            if (document.has("minecraft:geometry")) {
                final JsonArray candidates = document.getAsJsonArray("minecraft:geometry");
                require(candidates.size() <= 16);
                for (JsonElement candidate : candidates) {
                    final JsonObject object = candidate.getAsJsonObject();
                    final JsonObject desc = object.getAsJsonObject("description");
                    if (identifier.equals(desc.get("identifier").getAsString())) {
                        require(geometry == null);
                        geometry = object;
                        description = desc;
                    }
                }
            } else if (document.has(identifier)) {
                geometry = document.getAsJsonObject(identifier);
                description = geometry;
            }
            require(geometry != null && description != null);
            final int textureWidth = dimension(description, "texture_width", "texturewidth");
            final int textureHeight = dimension(description, "texture_height", "textureheight");
            require((long) textureWidth * atlas.getHeight() == (long) textureHeight * atlas.getWidth());
            final JsonArray boneArray = geometry.getAsJsonArray("bones");
            require(boneArray != null && !boneArray.isEmpty() && boneArray.size() <= MAX_BONES);
            final Map<String, Bone> bones = new LinkedHashMap<>();
            int cubeCount = 0;
            for (JsonElement element : boneArray) {
                final JsonObject bone = element.getAsJsonObject();
                require(!bone.has("poly_mesh") && !bone.has("texture_meshes") && !bone.has("binding"));
                final String name = bone.get("name").getAsString();
                require(!name.isBlank() && name.length() <= 128 && !bones.containsKey(name));
                final String parent = bone.has("parent") ? bone.get("parent").getAsString() : null;
                final float[] pivot = vector(bone, "pivot", new float[]{0, 0, 0}, 128);
                final float[] rotation = vector(bone, "rotation", new float[]{0, 0, 0}, 360);
                final boolean mirror = bone.has("mirror") && bone.get("mirror").getAsBoolean();
                final float inflate = number(bone, "inflate", 0, 8);
                final List<Cube> cubes = new ArrayList<>();
                if (bone.has("cubes")) {
                    for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                        require(++cubeCount <= MAX_CUBES);
                        final JsonObject cube = cubeElement.getAsJsonObject();
                        require(!cube.has("poly_mesh"));
                        final float[] origin = vector(cube, "origin", null, 128);
                        final float[] size = vector(cube, "size", null, 128);
                        require(size[0] >= 0 && size[1] >= 0 && size[2] >= 0);
                        final float[] cubePivot = vector(cube, "pivot", new float[]{origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2}, 192);
                        final float[] cubeRotation = vector(cube, "rotation", new float[]{0, 0, 0}, 360);
                        final boolean cubeMirror = cube.has("mirror") ? cube.get("mirror").getAsBoolean() : mirror;
                        final float cubeInflate = number(cube, "inflate", inflate, 8);
                        require(size[0] + 2 * cubeInflate >= 0 && size[1] + 2 * cubeInflate >= 0 && size[2] + 2 * cubeInflate >= 0);
                        final JsonElement uv = cube.get("uv");
                        require(uv != null);
                        final Map<Direction, Face> faces = faces(uv, size, textureWidth, textureHeight);
                        cubes.add(new Cube(origin, size, cubePivot, cubeRotation, cubeMirror, cubeInflate, faces));
                    }
                }
                bones.put(name, new Bone(name, parent, pivot, rotation, List.copyOf(cubes)));
            }
            require(cubeCount > 0);
            for (Bone bone : bones.values()) {
                String parent = bone.parent();
                int depth = 0;
                while (parent != null) {
                    require(++depth <= MAX_BONES && !parent.equals(bone.name()) && bones.containsKey(parent));
                    parent = bones.get(parent).parent();
                }
            }
            return new Geometry(textureWidth, textureHeight, Map.copyOf(bones));
        } catch (final RuntimeException ignored) {
            // Missing piece geometry, external references, malformed UVs and unsupported meshes
            // remain on the deterministic bundled skin. Never apply a creator atlas to Java UVs.
            return null;
        }
    }

    private static JsonObject boundedJson(final String input) {
        require(input != null && input.length() <= MAX_JSON_LENGTH);
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') require(++depth <= 32);
            else if (c == '}' || c == ']') require(--depth >= 0);
        }
        require(depth == 0 && !quoted);
        return JsonParser.parseString(input).getAsJsonObject();
    }

    private static int dimension(final JsonObject object, final String modern, final String legacy) {
        final float value = number(object, object.has(modern) ? modern : legacy, -1, 2048);
        require(value >= 1 && value == (int) value);
        return (int) value;
    }

    private static float number(final JsonObject object, final String key, final float fallback, final float limit) {
        final float value = object.has(key) ? object.get(key).getAsFloat() : fallback;
        require(Float.isFinite(value) && Math.abs(value) <= limit);
        return value;
    }

    private static float[] vector(final JsonObject object, final String key, final float[] fallback, final float limit) {
        if (!object.has(key)) {
            require(fallback != null);
            return fallback.clone();
        }
        final JsonArray array = object.getAsJsonArray(key);
        require(array.size() == 3);
        final float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = array.get(i).getAsFloat();
            require(Float.isFinite(result[i]) && Math.abs(result[i]) <= limit);
        }
        return result;
    }

    private static Map<Direction, Face> faces(final JsonElement uv, final float[] size, final int width, final int height) {
        final Map<Direction, Face> result = new LinkedHashMap<>();
        final float x = size[0], y = size[1], z = size[2];
        if (uv.isJsonArray()) {
            final JsonArray array = uv.getAsJsonArray();
            require(array.size() == 2);
            final float u = array.get(0).getAsFloat(), v = array.get(1).getAsFloat();
            result.put(Direction.DOWN, new Face(u + z, v, x, z));
            result.put(Direction.UP, new Face(u + z + x, v + z, x, -z));
            result.put(Direction.WEST, new Face(u, v + z, z, y));
            result.put(Direction.NORTH, new Face(u + z, v + z, x, y));
            result.put(Direction.EAST, new Face(u + z + x, v + z, z, y));
            result.put(Direction.SOUTH, new Face(u + z + x + z, v + z, x, y));
        } else {
            final JsonObject object = uv.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                final Direction direction = switch (entry.getKey()) {
                    case "up" -> Direction.DOWN;
                    case "down" -> Direction.UP;
                    case "north" -> Direction.NORTH;
                    case "south" -> Direction.SOUTH;
                    case "west" -> Direction.WEST;
                    case "east" -> Direction.EAST;
                    default -> throw new IllegalArgumentException("Unknown cube face");
                };
                final JsonObject face = entry.getValue().getAsJsonObject();
                require(!face.has("material_instance"));
                final JsonArray origin = face.getAsJsonArray("uv");
                require(origin != null && origin.size() == 2);
                final JsonArray extent = face.getAsJsonArray("uv_size");
                if (extent != null) require(extent.size() == 2);
                final float defaultWidth = direction.getAxis() == Direction.Axis.X ? z : x;
                final float defaultHeight = direction.getAxis() == Direction.Axis.Y ? z : y;
                result.put(direction, new Face(origin.get(0).getAsFloat(), origin.get(1).getAsFloat(),
                    extent == null ? defaultWidth : extent.get(0).getAsFloat(),
                    extent == null ? defaultHeight : extent.get(1).getAsFloat()));
            }
        }
        require(!result.isEmpty());
        for (Face face : result.values()) {
            require(Float.isFinite(face.u()) && Float.isFinite(face.v()) && Float.isFinite(face.width()) && Float.isFinite(face.height()));
            require(face.u() >= 0 && face.u() <= width && face.u() + face.width() >= 0 && face.u() + face.width() <= width);
            require(face.v() >= 0 && face.v() <= height && face.v() + face.height() >= 0 && face.v() + face.height() <= height);
        }
        return Map.copyOf(result);
    }

    private static void require(final boolean condition) {
        if (!condition) throw new IllegalArgumentException("Incomplete or unsupported creator geometry");
    }

    public record Geometry(int textureWidth, int textureHeight, Map<String, Bone> bones) {
        public Model<AvatarRenderState> bake(final boolean slim) {
            final Map<String, ModelPart> parts = new LinkedHashMap<>();
            final Map<String, Map<String, ModelPart>> children = new LinkedHashMap<>();
            for (Bone bone : bones.values()) {
                final Map<String, ModelPart> childParts = new LinkedHashMap<>();
                children.put(bone.name(), childParts);
                int index = 0;
                for (Cube cube : bone.cubes()) {
                    final float[] origin = cube.origin(), size = cube.size(), pivot = cube.pivot();
                    final ModelPart.Cube modelCube = new ModelPart.Cube(0, 0,
                        origin[0] - pivot[0], pivot[1] - origin[1] - size[1], origin[2] - pivot[2],
                        size[0], size[1], size[2], cube.inflate(), cube.inflate(), cube.inflate(),
                        false, textureWidth, textureHeight, faceDirections(cube));
                    for (int i = 0; i < modelCube.polygons.length; i++) {
                        final ModelPart.Polygon polygon = modelCube.polygons[i];
                        final Direction direction = Direction.getApproximateNearest(polygon.normal().x(), polygon.normal().y(), polygon.normal().z());
                        final Direction uvDirection = cube.mirror() && direction.getAxis() == Direction.Axis.X ? direction.getOpposite() : direction;
                        final Face face = cube.faces().get(uvDirection);
                        require(face != null);
                        modelCube.polygons[i] = new ModelPart.Polygon(polygon.vertices(), cube.mirror() ? face.u() + face.width() : face.u(), face.v(),
                            cube.mirror() ? face.u() : face.u() + face.width(), face.v() + face.height(), textureWidth, textureHeight, false, direction);
                    }
                    final ModelPart part = new ModelPart(List.of(modelCube), Map.of());
                    pose(part, pivot[0] - bone.pivot()[0], bone.pivot()[1] - pivot[1], pivot[2] - bone.pivot()[2], cube.rotation());
                    childParts.put("cube_" + index++, part);
                }
                final ModelPart part = new ModelPart(List.of(), childParts);
                final Bone parent = bone.parent() == null ? null : bones.get(bone.parent());
                final float[] pivot = bone.pivot();
                pose(part, pivot[0] - (parent == null ? 0 : parent.pivot()[0]),
                    (parent == null ? 24 : parent.pivot()[1]) - pivot[1],
                    pivot[2] - (parent == null ? 0 : parent.pivot()[2]), bone.rotation());
                parts.put(bone.name(), part);
            }
            final Map<String, ModelPart> roots = new LinkedHashMap<>();
            for (Bone bone : bones.values()) {
                if (bone.parent() == null) roots.put(bone.name(), parts.get(bone.name()));
                else children.get(bone.parent()).put(bone.name(), parts.get(bone.name()));
            }
            return new CreatorModel(new ModelPart(List.of(), roots), parts, slim);
        }
    }

    private static EnumSet<Direction> faceDirections(final Cube cube) {
        final EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        for (Direction direction : cube.faces().keySet()) {
            directions.add(cube.mirror() && direction.getAxis() == Direction.Axis.X ? direction.getOpposite() : direction);
        }
        return directions;
    }

    private static void pose(final ModelPart part, final float x, final float y, final float z, final float[] rotation) {
        final float radians = (float) (Math.PI / 180);
        final PartPose pose = PartPose.offsetAndRotation(x, y, z, -rotation[0] * radians, rotation[1] * radians, -rotation[2] * radians);
        part.setInitialPose(pose);
        part.loadPose(pose);
    }

    public record Bone(String name, String parent, float[] pivot, float[] rotation, List<Cube> cubes) { }
    public record Cube(float[] origin, float[] size, float[] pivot, float[] rotation, boolean mirror, float inflate, Map<Direction, Face> faces) { }
    public record Face(float u, float v, float width, float height) { }

    private static final class CreatorModel extends Model<AvatarRenderState> {
        private final Map<String, ModelPart> parts;
        private final PlayerModel animator;

        private CreatorModel(final ModelPart root, final Map<String, ModelPart> parts, final boolean slim) {
            super(root, RenderTypes::entityTranslucent);
            this.parts = parts;
            this.animator = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
        }

        @Override
        public void setupAnim(final AvatarRenderState state) {
            this.resetPose();
            this.animator.setupAnim(state);
            for (Map.Entry<String, ModelPart> entry : this.parts.entrySet()) {
                final ModelPart animated = switch (entry.getKey().replace("_", "").toLowerCase(Locale.ROOT)) {
                    case "head" -> this.animator.head;
                    case "body" -> this.animator.body;
                    case "rightarm" -> this.animator.rightArm;
                    case "leftarm" -> this.animator.leftArm;
                    case "rightleg" -> this.animator.rightLeg;
                    case "leftleg" -> this.animator.leftLeg;
                    default -> null;
                };
                if (animated == null) continue;
                final ModelPart part = entry.getValue();
                final PartPose initial = animated.getInitialPose();
                part.x += animated.x - initial.x();
                part.y += animated.y - initial.y();
                part.z += animated.z - initial.z();
                part.xRot += animated.xRot - initial.xRot();
                part.yRot += animated.yRot - initial.yRot();
                part.zRot += animated.zRot - initial.zRot();
                part.visible = !state.isSpectator || animated == this.animator.head;
            }
        }
    }
}
