/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.viaversion.viafabricplus.util.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SubChunkEntryTypeTest {
    @Test
    void readsEachOptionalBlobOnceWithoutConsumingTheNextEntry() {
        final ByteBuf bytes = Unpooled.buffer();
        try {
            bytes.writeBytes(new byte[]{0, 1, 0}); // first offset
            bytes.writeByte(1); // successful data
            bytes.writeBoolean(true);
            BedrockTypes.BYTE_ARRAY.write(bytes, new byte[]{8, 0, 1});
            bytes.writeByte(1); // heightmap
            bytes.writeBoolean(true);
            bytes.writeZero(272);
            bytes.writeByte(0); // render heightmap
            bytes.writeBoolean(false);
            bytes.writeBoolean(false); // first blob absent
            bytes.writeBytes(new byte[]{1, 2, 3}); // second offset
            bytes.writeByte(6); // all air
            bytes.writeBoolean(false); // no data
            bytes.writeByte(0);
            bytes.writeBoolean(false);
            bytes.writeByte(1);
            bytes.writeBoolean(true);
            bytes.writeZero(272);
            bytes.writeBoolean(true); // second blob present
            bytes.writeLongLE(42L);

            final var first = BedrockTypes.SUB_CHUNK_ENTRY.read(bytes);
            assertArrayEquals(new byte[]{8, 0, 1}, first.data());
            assertNull(first.blobId());
            final var second = BedrockTypes.SUB_CHUNK_ENTRY.read(bytes);
            assertEquals(1, second.offset().x());
            assertEquals(2, second.offset().y());
            assertEquals(3, second.offset().z());
            assertEquals(42L, second.blobId());
            assertEquals(0, bytes.readableBytes());
        } finally {
            bytes.release();
        }
    }
}
