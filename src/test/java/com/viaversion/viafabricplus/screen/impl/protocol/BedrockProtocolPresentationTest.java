/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.viaversion.viafabricplus.screen.impl.protocol;

import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BedrockProtocolPresentationTest {
    @Test
    void bedrockAppearsUnderModernWithItsBlockTexture() {
        assertEquals(ProtocolCategory.MODERN, ProtocolCategory.of(BedrockProtocolVersion.BEDROCK_LATEST));
        assertEquals(Identifier.withDefaultNamespace("textures/block/bedrock.png"),
            ProtocolVersionMetadata.icon(BedrockProtocolVersion.BEDROCK_LATEST));
    }
}
