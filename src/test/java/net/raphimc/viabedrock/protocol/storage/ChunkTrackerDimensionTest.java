/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.raphimc.viabedrock.protocol.storage;

import com.google.common.collect.HashBiMap;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import net.raphimc.viabedrock.api.model.BlockState;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.enums.Dimension;
import net.raphimc.viabedrock.protocol.data.generated.java.RegistryKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChunkTrackerDimensionTest {

    @Test
    void replacedDimensionCannotSendQueuedChunks() throws ReflectiveOperationException {
        Class.forName("com.viaversion.viaversion.api.minecraft.data.StructuredDataKey");
        final var mappings = BedrockProtocol.MAPPINGS;
        final Field javaBlockStates = mappingField("javaBlockStates");
        final Field bedrockEntities = mappingField("bedrockEntities");
        final Object previousStates = javaBlockStates.get(mappings);
        final Object previousEntities = bedrockEntities.get(mappings);

        try {
            final var states = HashBiMap.<BlockState, Integer>create();
            states.put(BlockState.fromString("minecraft:air"), 0);
            javaBlockStates.set(mappings, states);
            bedrockEntities.set(mappings, HashBiMap.create());

            final CompoundTag dimensions = new CompoundTag();
            dimensions.put(Dimension.END.getKey(), dimension(0, 256));
            dimensions.put(Dimension.OVERWORLD.getKey(), dimension(-64, 384));
            final CompoundTag registries = new CompoundTag();
            registries.put(RegistryKeys.DIMENSION_TYPE, dimensions);
            registries.put(RegistryKeys.WORLDGEN_BIOME, new CompoundTag());
            final AtomicReference<ChunkTracker> currentTracker = new AtomicReference<>();
            final var settings = new ClientSettingsStorage("en_us", 8, 0, true, (short) 0, 0, false, true, 0);
            final var session = new GameSessionStorage(null) {
                @Override
                public CompoundTag getJavaRegistries() {
                    return registries;
                }
            };
            final UserConnection user = (UserConnection) Proxy.newProxyInstance(
                UserConnection.class.getClassLoader(), new Class<?>[]{UserConnection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isClientSide")) return false;
                    if (method.getName().equals("get")) {
                        if (args[0] == GameSessionStorage.class) return session;
                        if (args[0] == ClientSettingsStorage.class) return settings;
                        if (args[0] == ChunkTracker.class) return currentTracker.get();
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
            );

            final var end = new RecordingChunkTracker(user, Dimension.END);
            currentTracker.set(end);
            assertEquals(16, end.createChunk(0, 0, 0).getSections().length);
            end.sendChunkInNextTick(0, 0);

            final var overworld = new RecordingChunkTracker(user, Dimension.OVERWORLD);
            currentTracker.set(overworld);
            assertEquals(24, overworld.createChunk(0, 0, 0).getSections().length);
            overworld.sendChunkInNextTick(0, 0);
            end.tick();
            overworld.tick();

            assertEquals(0, end.sentChunks);
            assertEquals(1, overworld.sentChunks);
        } finally {
            javaBlockStates.set(mappings, previousStates);
            bedrockEntities.set(mappings, previousEntities);
        }
    }

    private static CompoundTag dimension(final int minY, final int height) {
        final CompoundTag dimension = new CompoundTag();
        dimension.putInt("min_y", minY);
        dimension.putInt("height", height);
        return dimension;
    }

    private static Field mappingField(final String name) throws ReflectiveOperationException {
        final Field field = BedrockProtocol.MAPPINGS.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class RecordingChunkTracker extends ChunkTracker {
        private int sentChunks;

        private RecordingChunkTracker(final UserConnection user, final Dimension dimension) {
            super(user, dimension);
        }

        @Override
        public void sendChunk(final int chunkX, final int chunkZ) {
            this.sentChunks++;
        }
    }
}
