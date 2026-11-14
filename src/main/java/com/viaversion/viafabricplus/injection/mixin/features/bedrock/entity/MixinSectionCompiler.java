/*
 * This file is part of ViaFabricPlusPeaks.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.injection.mixin.features.bedrock.entity;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viafabricplus.util.bedrock.BedrockAddonBlockRender;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionCompiler.class)
public abstract class MixinSectionCompiler {

    @ModifyExpressionValue(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape hideBedrockSlabCarrier(final RenderShape original, @Local final BlockState state, @Local(name = "pos") final BlockPos position) {
        return BedrockAddonBlockRender.renderShape(state, position, original);
    }
}
