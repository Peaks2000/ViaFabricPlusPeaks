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
package net.raphimc.viabedrock.api.io;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

final class BlobDbTest {

    @Test
    void saveFlushesQueuedWritesWithoutHoldingOutTheWriter(@TempDir final Path directory) throws Exception {
        try (BlobDb database = new BlobDb(directory.toFile())) {
            database.putNow(1L, new byte[]{1, 2, 3});
            database.queuePut(2L, new byte[]{4, 5, 6});
            assertTimeoutPreemptively(Duration.ofSeconds(3), database::save);
        }
        try (BlobDb reopened = new BlobDb(directory.toFile())) {
            assertArrayEquals(new byte[]{1, 2, 3}, reopened.get(1L));
            assertArrayEquals(new byte[]{4, 5, 6}, reopened.get(2L));
        }
    }

    @Test
    void savePersistsAnInitiallyEmptyDatabaseWithQueuedWrites(@TempDir final Path directory) throws Exception {
        try (BlobDb database = new BlobDb(directory.toFile())) {
            synchronized (database) {
                database.queuePut(3L, new byte[]{7, 8, 9});
                database.save();
                assertTrue(database.contains(3L));
            }
        }
        try (BlobDb reopened = new BlobDb(directory.toFile())) {
            assertArrayEquals(new byte[]{7, 8, 9}, reopened.get(3L));
        }
    }
    @Test
    void failedFlushRetainsPendingDataForRetry(@TempDir final Path directory) throws Exception {
        final AtomicBoolean failNextWrite = new AtomicBoolean(true);
        try (BlobDb database = new BlobDb(directory.toFile()) {
            @Override
            public synchronized void putNow(final long key, final byte[] value) throws IOException {
                if (failNextWrite.getAndSet(false)) {
                    throw new IOException("Simulated blob write failure");
                }
                super.putNow(key, value);
            }
        }) {
            synchronized (database) {
                database.queuePut(4L, new byte[]{10, 11, 12});
                assertThrows(IOException.class, database::save);
                assertArrayEquals(new byte[]{10, 11, 12}, database.get(4L));
                database.save();
            }
        }
        try (BlobDb reopened = new BlobDb(directory.toFile())) {
            assertArrayEquals(new byte[]{10, 11, 12}, reopened.get(4L));
        }
    }

}
