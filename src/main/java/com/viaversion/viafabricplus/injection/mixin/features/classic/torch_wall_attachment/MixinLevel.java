/*
 * This file is part of ViaFabricPlus - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.injection.mixin.features.classic.torch_wall_attachment;

import com.viaversion.viafabricplus.features.classic.block_sounds.ClassicubeBlockSounds;
import com.viaversion.viafabricplus.features.classic.torch_wall_attachment.TorchWallAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Level.class)
public abstract class MixinLevel {

    @Redirect(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState viafabricplus$redirectSetBlockState(final LevelChunk chunk, final BlockPos pos, final BlockState state, final int flags) {
        BlockState newState = state;
        if (TorchWallAttachment.isApplicable()) {
            if (state.getBlock() == Blocks.TORCH) {
                newState = TorchWallAttachment.getAttachmentState(state, (Level) (Object) this, pos);
            }
            if (!newState.isSolid()) {
                TorchWallAttachment.updateWallTorchNeighbors((Level) (Object) this, pos);
            }
        }
        ClassicubeBlockSounds.playSounds((Level) (Object) this, pos, newState, flags);
        return chunk.setBlockState(pos, newState, flags);
    }

}