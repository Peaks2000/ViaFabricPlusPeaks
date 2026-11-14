/*
 * This file is part of ViaFabricPlusPeaks.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.injection.mixin.compat.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viafabricplus.util.bedrock.BedrockAddonBlockRender;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class MixinChunkBuilderMeshingTask {

    @ModifyExpressionValue(method = "execute", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"), require = 0)
    private RenderShape hideBedrockSlabCarrier(final RenderShape original, @Local final BlockState state, @Local(ordinal = 0) final BlockPos.MutableBlockPos position) {
        return BedrockAddonBlockRender.renderShape(state, position, original);
    }
}
