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

package com.viaversion.viafabricplus.features.classic.block_sounds;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import static net.raphimc.vialegacy.api.LegacyProtocolVersion.c0_30cpe;

public final class ClassicubeBlockSounds {

    private ClassicubeBlockSounds() {
    }

    public static boolean isApplicable() {
        return ProtocolTranslator.getTargetVersion().equals(c0_30cpe);
    }

    public static void playSounds(final Level level, final BlockPos pos, final BlockState newState, final int flags) {
        if (!isApplicable() || (flags & 1) == 0) {
            return;
        }
        final BlockState oldState = level.getBlockState(pos);
        if (oldState == newState) {
            return;
        }
        if (newState.isAir() && !oldState.isAir()) {
            playBlockBreakSound(level, pos, oldState);
        } else if (!newState.isAir() && oldState.isAir()) {
            playBlockPlaceSound(level, pos, newState);
        }
    }

    private static void playBlockBreakSound(final Level level, final BlockPos pos, final BlockState oldState) {
        if (level instanceof ClientLevel clientLevel) {
            clientLevel.levelEvent(null, 2001, pos, Block.getId(oldState));
        } else {
            final SoundType soundType = oldState.getSoundType();
            level.playLocalSound(pos, soundType.getBreakSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F, false);
        }
    }

    private static void playBlockPlaceSound(final Level level, final BlockPos pos, final BlockState newState) {
        final SoundType soundType = newState.getSoundType();
        level.playLocalSound(pos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F, false);
    }

}