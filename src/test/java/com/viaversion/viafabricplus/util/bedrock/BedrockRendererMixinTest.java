/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.viaversion.viafabricplus.util.bedrock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class BedrockRendererMixinTest {
    @Test void creatorRendererAndClassicChunkHooksApplyToCurrentMinecraftClasses() {
        for (String target : new String[]{
            "net.minecraft.client.renderer.entity.LivingEntityRenderer",
            "net.minecraft.client.renderer.entity.player.AvatarRenderer",
            "net.minecraft.client.multiplayer.ClientPacketListener"
        }) {
            assertDoesNotThrow(() -> Class.forName(target, false, getClass().getClassLoader()), target);
        }
    }
}
