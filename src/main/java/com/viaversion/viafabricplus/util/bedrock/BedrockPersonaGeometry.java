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
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viabedrock.ViaFabricPlusSkinProvider;
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
import org.joml.Vector3f;

/** Bakes a self-contained Bedrock creator atlas, cubes, and polygon meshes locally. */
public final class BedrockPersonaGeometry {

    private static final int MAX_BONES = 128;
    private static final int MAX_CUBES = 512;
    private static final int MAX_POLYGONS = 4096;
    private static final int MAX_JSON_LENGTH = 1_048_576;

    private BedrockPersonaGeometry() {
    }

    static boolean usesCustomGeometry(final BedrockSkinAppearance skin) {
        if (skin.persona()) {
            return true;
        }
        final String identifier = geometryIdentifier(skin.skinResourcePatch(), "default");
        return identifier != null && !identifier.equalsIgnoreCase("geometry.humanoid.custom")
            && !identifier.equalsIgnoreCase("geometry.humanoid.customSlim")
            && !identifier.equalsIgnoreCase("geometry.humanoid");
    }

    static String traceGeometryIdentifier(final String resourcePatch) {
        final String identifier = geometryIdentifier(resourcePatch, "default");
        return identifier != null && identifier.matches("[a-zA-Z0-9_.:-]{1,100}") ? identifier : "none";
    }

    static List<String> traceAnimationSlots(final String resourcePatch) {
        return List.of("animated_face", "animated_32x32", "animated_128x128").stream()
            .filter(slot -> geometryIdentifier(resourcePatch, slot) != null).toList();
    }

    private static String geometryIdentifier(final String resourcePatch, final String slot) {
        try {
            return boundedJson(resourcePatch).getAsJsonObject("geometry").get(slot).getAsString();
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    public static Geometry parse(final SkinData skin) {
        return skin == null ? null : parse(ViaFabricPlusSkinProvider.appearance(skin));
    }

    public static Geometry parse(final BedrockSkinAppearance skin) {
        return parse(skin, false);
    }

    static Geometry parse(final BedrockSkinAppearance skin, final boolean trace) {
        final BufferedImage atlas = skin.skinData();
        return atlas == null ? null : parseLayer(skin, "default", atlas.getWidth(), atlas.getHeight(), 1, trace);
    }

    static Geometry parseLayer(final BedrockSkinAppearance skin, final String slot, final int width,
                               final int frameHeight, final int frames, final boolean trace) {
        try {
            require(width > 0 && frameHeight > 0 && width <= 2048 && frameHeight <= 2048);
            final String identifier = geometryIdentifier(skin.skinResourcePatch(), slot);
            require(identifier != null || slot.equals("default"));
            final JsonObject document = boundedJson(skin.geometryData());
            JsonObject geometry = null;
            JsonObject description = null;
            if (document.has("minecraft:geometry")) {
                final JsonArray candidates = document.getAsJsonArray("minecraft:geometry");
                require(candidates.size() <= 16);
                for (JsonElement candidate : candidates) {
                    final JsonObject object = candidate.getAsJsonObject();
                    final JsonObject desc = object.getAsJsonObject("description");
                    if (desc == null || !desc.has("identifier")) {
                        continue;
                    }
                    final boolean selected = identifier != null ? identifier.equals(desc.get("identifier").getAsString())
                        : candidates.size() == 1;
                    if (selected) {
                        require(geometry == null);
                        geometry = object;
                        description = desc;
                    }
                }
            } else if (identifier != null && document.has(identifier)) {
                geometry = document.getAsJsonObject(identifier);
                description = geometry;
            }
            require(geometry != null && description != null);
            final int textureWidth = dimension(description, "texture_width", "texturewidth", width);
            final int textureHeight = dimension(description, "texture_height", "textureheight", frameHeight);
            final JsonArray boneArray = geometry.getAsJsonArray("bones");
            if (trace) {
                int meshes = 0, textureMeshes = 0, inherited = 0;
                if (boneArray != null) {
                    for (final JsonElement entry : boneArray) {
                        final JsonObject bone = entry.getAsJsonObject();
                        if (bone.has("poly_mesh")) meshes++;
                        if (bone.has("texture_meshes")) textureMeshes++;
                        if (bone.has("parent")) inherited++;
                    }
                }
                ViaFabricPlusImpl.impl().logger().info(
                    "Skin trace geometry slot={} declared={}x{} pixels={}x{} frames={} bones={} children={} meshBones={} textureMeshBones={}",
                    slot, textureWidth, textureHeight, width, frameHeight, frames, boneArray == null ? 0 : boneArray.size(),
                    inherited, meshes, textureMeshes);
            }
            final long scaledHeight = (long) textureHeight * width;
            final long scaledFrameHeight = (long) textureWidth * frameHeight;
            final boolean fullStripUv = frames > 1 && scaledHeight == scaledFrameHeight * frames;
            require(!fullStripUv || textureHeight % frames == 0);
            final int textureFrameHeight = fullStripUv ? textureHeight / frames : textureHeight;
            require(boneArray != null && !boneArray.isEmpty() && boneArray.size() <= MAX_BONES);
            final Map<String, Bone> bones = new LinkedHashMap<>();
            int cubeCount = 0;
            int polygonCount = 0;
            for (JsonElement element : boneArray) {
                final JsonObject bone = element.getAsJsonObject();
                require(!bone.has("texture_meshes"));
                final String name = bone.get("name").getAsString();
                require(!name.isBlank() && name.length() <= 128 && !bones.containsKey(name));
                final String parent = bone.has("parent") ? bone.get("parent").getAsString() : null;
                final float[] pivot = vector(bone, "pivot", new float[]{0, 0, 0}, 128);
                final float[] rotation = vector(bone, "rotation", new float[]{0, 0, 0}, 360);
                final boolean mirror = bone.has("mirror") && bone.get("mirror").getAsBoolean();
                final float inflate = number(bone, "inflate", 0, 8);
                final List<Cube> cubes = new ArrayList<>();
                final boolean visible = !bone.has("neverRender") || !bone.get("neverRender").getAsBoolean();
                if (visible && bone.has("cubes")) {
                    require(scaledHeight == scaledFrameHeight || fullStripUv);
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
                        final Map<Direction, Face> faces = faces(uv, size, textureWidth, textureFrameHeight);
                        cubes.add(new Cube(origin, size, cubePivot, cubeRotation, cubeMirror, cubeInflate, faces));
                    }
                }
                final List<ModelPart.Polygon> polygons = visible && bone.has("poly_mesh")
                    ? mesh(bone.getAsJsonObject("poly_mesh"), pivot, textureWidth, textureFrameHeight,
                        fullStripUv ? frames : 1, MAX_POLYGONS - polygonCount)
                    : List.of();
                polygonCount += polygons.size();
                bones.put(name, new Bone(name, parent, pivot, rotation, List.copyOf(cubes), polygons));
            }
            require(cubeCount > 0 || polygonCount > 0);
            for (Bone bone : bones.values()) {
                String parent = bone.parent();
                int depth = 0;
                while (parent != null) {
                    require(++depth <= MAX_BONES && !parent.equals(bone.name()) && bones.containsKey(parent));
                    parent = bones.get(parent).parent();
                }
            }
            return new Geometry(textureWidth, textureFrameHeight, Map.copyOf(bones));
        } catch (final RuntimeException error) {
            if (trace) {
                final var location = java.util.Arrays.stream(error.getStackTrace())
                    .filter(frame -> frame.getClassName().equals(BedrockPersonaGeometry.class.getName())
                        && !frame.getMethodName().equals("require")).findFirst();
                ViaFabricPlusImpl.impl().logger().info("Skin trace geometry rejected slot={} reason={} location={}",
                    slot, error.getClass().getSimpleName(), location.map(frame -> frame.getMethodName()
                        + ":" + frame.getLineNumber()).orElse("unknown"));
            }
            // Missing piece geometry, external references, malformed UVs and unsupported meshes
            // remain on the deterministic bundled skin. Never apply a creator atlas to Java UVs.
            return null;
        }
    }

    private static List<ModelPart.Polygon> mesh(final JsonObject mesh, final float[] pivot, final int width,
                                                 final int height, final int uvFrames, final int remainingPolygons) {
        final boolean normalized = mesh.has("normalized_uvs") && mesh.get("normalized_uvs").getAsBoolean();
        final float[][] positions = meshVectors(mesh, "positions", 3, 256);
        final float[][] normals = meshVectors(mesh, "normals", 3, 1.001F);
        final float[][] uvs = meshVectors(mesh, "uvs", 2, normalized ? 1.001F : Math.max(width, height * uvFrames));
        final JsonElement definition = mesh.get("polys");
        final List<ModelPart.Polygon> polygons = new ArrayList<>();
        if (definition.isJsonArray()) {
            final JsonArray faces = definition.getAsJsonArray();
            require(!faces.isEmpty() && faces.size() <= remainingPolygons);
            for (final JsonElement face : faces) {
                final JsonArray entries = face.getAsJsonArray();
                require(entries.size() == 3 || entries.size() == 4);
                final int[][] corners = new int[entries.size()][];
                for (int i = 0; i < corners.length; i++) {
                    final JsonArray indices = entries.get(i).getAsJsonArray();
                    require(indices.size() == 3);
                    corners[i] = new int[]{meshIndex(indices.get(0), positions.length),
                        meshIndex(indices.get(1), normals.length), meshIndex(indices.get(2), uvs.length)};
                }
                polygons.add(meshPolygon(corners, positions, normals, uvs, pivot, width, height, uvFrames, normalized));
            }
        } else {
            final int stride = switch (definition.getAsString()) {
                case "tri_list" -> 3;
                case "quad_list" -> 4;
                default -> throw new IllegalArgumentException("Unknown polygon arrangement");
            };
            require(positions.length == normals.length && positions.length == uvs.length
                && positions.length % stride == 0 && positions.length / stride <= remainingPolygons);
            for (int start = 0; start < positions.length; start += stride) {
                final int[][] corners = new int[stride][];
                for (int i = 0; i < stride; i++) {
                    final int index = start + i;
                    corners[i] = new int[]{index, index, index};
                }
                polygons.add(meshPolygon(corners, positions, normals, uvs, pivot, width, height, uvFrames, normalized));
            }
        }
        return List.copyOf(polygons);
    }

    private static float[][] meshVectors(final JsonObject mesh, final String key, final int size, final float limit) {
        final JsonArray entries = mesh.getAsJsonArray(key);
        require(entries != null && !entries.isEmpty() && entries.size() <= MAX_POLYGONS * 4);
        final float[][] result = new float[entries.size()][size];
        for (int i = 0; i < entries.size(); i++) {
            final JsonArray entry = entries.get(i).getAsJsonArray();
            require(entry.size() == size);
            for (int j = 0; j < size; j++) {
                final float value = entry.get(j).getAsFloat();
                require(Float.isFinite(value) && Math.abs(value) <= limit);
                result[i][j] = value;
            }
        }
        return result;
    }

    private static int meshIndex(final JsonElement value, final int size) {
        final double index = value.getAsDouble();
        require(index >= 0 && index < size && index == (int) index);
        return (int) index;
    }

    private static ModelPart.Polygon meshPolygon(final int[][] corners, final float[][] positions,
                                                   final float[][] normals, final float[][] uvs, final float[] pivot,
                                                   final int width, final int height, final int uvFrames,
                                                   final boolean normalized) {
        final ModelPart.Vertex[] vertices = new ModelPart.Vertex[4];
        final Vector3f normal = new Vector3f();
        for (int i = 0; i < corners.length; i++) {
            final int[] corner = corners[i];
            final float[] position = positions[corner[0]];
            final float[] direction = normals[corner[1]];
            final float[] uv = uvs[corner[2]];
            final float u = normalized ? uv[0] : uv[0] / width;
            // Persona mesh UVs start at the bottom of the image. Animated geometry can
            // address its first frame in full-strip coordinates.
            final float v = normalized ? (1 - uv[1]) * uvFrames : uvFrames - uv[1] / height;
            require(u >= -0.0001F && u <= 1.0001F && v >= -0.0001F && v <= 1.0001F);
            normal.add(direction[0], -direction[1], direction[2]);
            vertices[i] = new ModelPart.Vertex(position[0] - pivot[0], pivot[1] - position[1], position[2] - pivot[2],
                Math.clamp(u, 0, 1), Math.clamp(v, 0, 1));
        }
        if (corners.length == 3) vertices[3] = vertices[2];
        if (normal.lengthSquared() < 0.000001F) {
            final float[] direction = normals[corners[0][1]];
            normal.set(direction[0], -direction[1], direction[2]);
            require(normal.lengthSquared() >= 0.000001F);
        }
        return new ModelPart.Polygon(vertices, normal.normalize());
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

    private static int dimension(final JsonObject object, final String modern, final String legacy, final int fallback) {
        final float value = number(object, object.has(modern) ? modern : legacy, fallback, 2048);
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
                // A serialized skin carries a single composed atlas. Named material instances
                // can address that atlas just like the default material.
                final JsonArray origin = face.getAsJsonArray("uv");
                require(origin != null && origin.size() == 2);
                final JsonArray extent = face.getAsJsonArray("uv_size");
                if (extent != null) require(extent.size() == 2);
                final float defaultWidth = direction.getAxis() == Direction.Axis.X ? z : x;
                final float defaultHeight = direction.getAxis() == Direction.Axis.Y ? z : y;
                result.put(direction, new Face(origin.get(0).getAsFloat(), origin.get(1).getAsFloat(),
                    extent == null ? defaultWidth : extent.get(0).getAsFloat(),
                    extent == null ? defaultHeight : extent.get(1).getAsFloat(), uvRotation(face)));
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

    private static int uvRotation(final JsonObject face) {
        final float rotation = number(face, "uv_rotation", 0, 270);
        require(rotation >= 0 && rotation % 90 == 0);
        return (int) rotation / 90;
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
                        final ModelPart.Polygon textured = new ModelPart.Polygon(polygon.vertices(),
                            cube.mirror() ? face.u() + face.width() : face.u(), face.v(),
                            cube.mirror() ? face.u() : face.u() + face.width(), face.v() + face.height(),
                            textureWidth, textureHeight, false, direction);
                        if (face.quarterTurns() != 0) {
                            final ModelPart.Vertex[] vertices = textured.vertices();
                            final ModelPart.Vertex[] unrotated = vertices.clone();
                            for (int vertex = 0; vertex < vertices.length; vertex++) {
                                final ModelPart.Vertex uv = unrotated[(vertex + face.quarterTurns()) % vertices.length];
                                vertices[vertex] = unrotated[vertex].remap(uv.u(), uv.v());
                            }
                        }
                        modelCube.polygons[i] = textured;
                    }
                    final ModelPart part = new ModelPart(List.of(modelCube), Map.of());
                    pose(part, pivot[0] - bone.pivot()[0], bone.pivot()[1] - pivot[1], pivot[2] - bone.pivot()[2], cube.rotation());
                    childParts.put("cube_" + index++, part);
                }
                final List<ModelPart.Cube> meshFaces = new ArrayList<>(bone.polygons().size());
                for (final ModelPart.Polygon polygon : bone.polygons()) {
                    // ModelPart batches quads in cube slots. Replace the placeholder face with the wire polygon.
                    final ModelPart.Cube face = new ModelPart.Cube(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        false, 1, 1, EnumSet.of(Direction.NORTH));
                    face.polygons[0] = polygon;
                    meshFaces.add(face);
                }
                final ModelPart part = new ModelPart(meshFaces, childParts);
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

    public record Bone(String name, String parent, float[] pivot, float[] rotation,
                       List<Cube> cubes, List<ModelPart.Polygon> polygons) { }
    public record Cube(float[] origin, float[] size, float[] pivot, float[] rotation, boolean mirror, float inflate, Map<Direction, Face> faces) { }
    public record Face(float u, float v, float width, float height, int quarterTurns) {
        public Face(final float u, final float v, final float width, final float height) {
            this(u, v, width, height, 0);
        }
    }

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
                    case "body", "torso" -> this.animator.body;
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
