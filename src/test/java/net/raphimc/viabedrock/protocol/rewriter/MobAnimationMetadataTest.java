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
package net.raphimc.viabedrock.protocol.rewriter;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes26_3;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;
import net.raphimc.viabedrock.api.model.entity.Entity;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorDataIds;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorFlags;
import net.raphimc.viabedrock.protocol.data.generated.java.EntityDataFields;
import net.raphimc.viabedrock.protocol.types.entitydata.EntityDataTypesBedrock;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

final class MobAnimationMetadataTest {
    private static final List<String> FIELDS = List.of(EntityDataFields.SHARED_FLAGS, EntityDataFields.SILENT,
        EntityDataFields.NO_GRAVITY, EntityDataFields.MOB_FLAGS, EntityDataFields.IS_POWERED,
        EntityDataFields.IS_IGNITED, EntityDataFields.SWELL_DIR, EntityDataFields.LIVING_ENTITY_FLAGS,
        EntityDataFields.SHEARED, EntityDataFields.CUSTOM_NAME, EntityDataFields.CUSTOM_NAME_VISIBLE,
        EntityDataFields.WIDTH, EntityDataFields.HEIGHT);
    private final UserConnection user = (UserConnection) Proxy.newProxyInstance(UserConnection.class.getClassLoader(),
        new Class<?>[]{UserConnection.class}, (proxy, method, args) -> null);
    private Field mappingField;
    private Object previousMapping;

    @BeforeEach void installMetadataIndices() throws Exception {
        Class.forName("com.viaversion.viaversion.api.minecraft.data.StructuredDataKey");
        mappingField = BedrockProtocol.MAPPINGS.getClass().getDeclaredField("javaEntityDataFields");
        mappingField.setAccessible(true);
        previousMapping = mappingField.get(BedrockProtocol.MAPPINGS);
        final Map<EntityTypes26_3, List<String>> fields = new EnumMap<>(EntityTypes26_3.class);
        for (EntityTypes26_3 type : EntityTypes26_3.values()) {
            fields.put(type, FIELDS);
        }
        mappingField.set(BedrockProtocol.MAPPINGS, fields);
    }

    @AfterEach void restoreMetadataIndices() throws Exception {
        mappingField.set(BedrockProtocol.MAPPINGS, previousMapping);
    }

    @Test void creeperSwellingCanStartCancelAndRestart() {
        final Entity creeper = entity(EntityTypes26_3.CREEPER);
        for (boolean swelling : new boolean[]{true, false, true, false}) {
            final List<EntityData> output = flags(creeper, swelling ? Set.of(ActorFlags.IGNITED, ActorFlags.CHARGED) : Set.of(ActorFlags.CHARGED));
            assertEquals(swelling ? 1 : -1, value(output, EntityDataFields.SWELL_DIR));
            assertEquals(false, value(output, EntityDataFields.IS_IGNITED));
            assertEquals(true, value(output, EntityDataFields.IS_POWERED));
        }
    }

    @Test void skeletonAimingPreservesNoAiAndClearsWhenTargetIsLost() {
        for (EntityTypes26_3 type : List.of(EntityTypes26_3.SKELETON, EntityTypes26_3.STRAY, EntityTypes26_3.BOGGED, EntityTypes26_3.WITHER_SKELETON)) {
            final Entity skeleton = entity(type);
            assertEquals((byte) 5, value(flags(skeleton, Set.of(ActorFlags.NOAI, ActorFlags.FACING_TARGET_TO_RANGE_ATTACK)), EntityDataFields.MOB_FLAGS));
            assertEquals((byte) 1, value(flags(skeleton, Set.of(ActorFlags.NOAI)), EntityDataFields.MOB_FLAGS));
        }
    }

    @Test void skeletonBowUseStartsAndStopsForBothItemUseAndChargingFlags() {
        final Entity skeleton = entity(EntityTypes26_3.SKELETON);
        for (ActorFlags charging : List.of(ActorFlags.USINGITEM, ActorFlags.CHARGING)) {
            assertEquals((byte) 1, value(flags(skeleton, Set.of(charging)), EntityDataFields.LIVING_ENTITY_FLAGS));
            assertEquals((byte) 0, value(flags(skeleton, Set.of()), EntityDataFields.LIVING_ENTITY_FLAGS));
        }
        assertTrue(flags(entity(EntityTypes26_3.CREEPER), Set.of(ActorFlags.USINGITEM)).stream()
            .noneMatch(data -> data.id() == FIELDS.indexOf(EntityDataFields.LIVING_ENTITY_FLAGS)));
    }

    @Test void customEntityUsesServerHitboxAndName() {
        final Entity npc = entity(EntityTypes26_3.INTERACTION);
        final List<EntityData> output = new ArrayList<>();
        EntityMetadataRewriter.rewrite(user, npc, ActorDataIds.RESERVED_053,
            new EntityData(53, EntityDataTypesBedrock.FLOAT, 0.75F), output);
        EntityMetadataRewriter.rewrite(user, npc, ActorDataIds.RESERVED_054,
            new EntityData(54, EntityDataTypesBedrock.FLOAT, 2.0F), output);
        EntityMetadataRewriter.rewrite(user, npc, ActorDataIds.RESERVED_054,
            new EntityData(54, EntityDataTypesBedrock.FLOAT, Float.NaN), output);
        EntityMetadataRewriter.rewrite(user, npc, ActorDataIds.NAME,
            new EntityData(4, EntityDataTypesBedrock.STRING, "SkyWars"), output);
        assertEquals(0.75F, value(output, EntityDataFields.WIDTH));
        assertEquals(2.0F, value(output, EntityDataFields.HEIGHT));
        assertEquals(3, output.size());
        assertNotNull(value(output, EntityDataFields.CUSTOM_NAME));
        final EntityData visibility = new EntityData(81, EntityDataTypesBedrock.BYTE, (byte) 1);
        npc.entityData().put(ActorDataIds.NAMETAG_ALWAYS_SHOW, visibility);
        EntityMetadataRewriter.rewrite(user, npc, ActorDataIds.NAMETAG_ALWAYS_SHOW, visibility, output);
        assertEquals(true, value(output, EntityDataFields.CUSTOM_NAME_VISIBLE));
    }

    @Test void zeroScaleArmorStandHidesOnlyTheModel() {
        final Entity stand = entity(EntityTypes26_3.ARMOR_STAND);
        assertEquals((byte) 0, value(flags(stand, Set.of()), EntityDataFields.SHARED_FLAGS));
        final EntityData zero = new EntityData(38, EntityDataTypesBedrock.FLOAT, 0F);
        stand.entityData().put(ActorDataIds.RESERVED_038, zero);
        final List<EntityData> output = new ArrayList<>();
        EntityMetadataRewriter.rewrite(user, stand, ActorDataIds.RESERVED_038, zero, output);
        assertEquals((byte) 32, value(output, EntityDataFields.SHARED_FLAGS));
        final EntityData name = new EntityData(4, EntityDataTypesBedrock.STRING, "EggWars");
        stand.entityData().put(ActorDataIds.NAME, name);
        final List<EntityData> named = flags(stand, Set.of(ActorFlags.ALWAYS_SHOW_NAME));
        assertEquals((byte) 32, value(named, EntityDataFields.SHARED_FLAGS));
        assertEquals(true, value(named, EntityDataFields.CUSTOM_NAME_VISIBLE));

        stand.entityData().put(ActorDataIds.RESERVED_038, new EntityData(38, EntityDataTypesBedrock.FLOAT, 1F));
        assertEquals((byte) 0, value(flags(stand, Set.of()), EntityDataFields.SHARED_FLAGS));
        assertEquals((byte) 32, value(flags(stand, Set.of(ActorFlags.INVISIBLE)), EntityDataFields.SHARED_FLAGS));
        stand.entityData().remove(ActorDataIds.NAME);
        assertEquals(false, value(flags(stand, Set.of(ActorFlags.ALWAYS_SHOW_NAME)), EntityDataFields.CUSTOM_NAME_VISIBLE));

        final Entity other = entity(EntityTypes26_3.CREEPER);
        other.entityData().put(ActorDataIds.RESERVED_038, zero);
        assertEquals((byte) 0, value(flags(other, Set.of()), EntityDataFields.SHARED_FLAGS));
    }

    private Entity entity(final EntityTypes26_3 type) {
        return new Entity(user, 1, 2, "minecraft:test", 3, UUID.randomUUID(), type);
    }

    private List<EntityData> flags(final Entity entity, final Set<ActorFlags> flags) {
        BigInteger mask = BigInteger.ZERO;
        for (ActorFlags flag : flags) {
            mask = mask.setBit(flag.getValue());
        }
        final EntityData low = new EntityData(ActorDataIds.RESERVED_0.getValue(), EntityDataTypesBedrock.LONG, mask.longValue());
        final EntityData high = new EntityData(ActorDataIds.RESERVED_092.getValue(), EntityDataTypesBedrock.LONG, mask.shiftRight(64).longValue());
        entity.entityData().put(ActorDataIds.RESERVED_0, low);
        entity.entityData().put(ActorDataIds.RESERVED_092, high);
        final List<EntityData> output = new ArrayList<>();
        EntityMetadataRewriter.rewrite(user, entity, ActorDataIds.RESERVED_0, low, output);
        return output;
    }

    private Object value(final List<EntityData> output, final String field) {
        return output.stream().filter(data -> data.id() == FIELDS.indexOf(field)).findFirst().orElseThrow().value();
    }
}
