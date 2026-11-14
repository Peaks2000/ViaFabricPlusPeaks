/*
 * This file is part of ViaFabricPlusPeaks.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.util.bedrock;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import io.netty.util.AttributeKey;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class BedrockAddonBlockRender {

    private static final AttributeKey<Set<Long>> SLAB_CARRIERS = AttributeKey.valueOf("viafabricplus:bedrock_addon_slab_carriers");

    public static RenderShape renderShape(final BlockState state, final BlockPos position, final RenderShape original) {
        if (state.is(Blocks.PETRIFIED_OAK_SLAB)) {
            final var user = ProtocolTranslator.getPlayNetworkUserConnection();
            final var carriers = user == null ? null : user.getChannel().attr(SLAB_CARRIERS).get();
            if (carriers != null && carriers.contains(position.asLong())) return RenderShape.INVISIBLE;
        }
        return original;
    }

    private BedrockAddonBlockRender() { }
}
