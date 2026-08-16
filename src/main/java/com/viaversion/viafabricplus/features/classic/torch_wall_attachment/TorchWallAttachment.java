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

package com.viaversion.viafabricplus.features.classic.torch_wall_attachment;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viafabricplus.settings.impl.GeneralSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import static net.raphimc.vialegacy.api.LegacyProtocolVersion.c0_30cpe;

public final class TorchWallAttachment {

    private static final int UPDATE_FLAGS = 18;

    public static void updateChunkConnections(final LevelReader levelReader, final int chunkX, final int chunkZ) {
        scanChunkConnections(levelReader, chunkX, chunkZ);
        scanChunkConnections(levelReader, chunkX + 1, chunkZ);
        scanChunkConnections(levelReader, chunkX - 1, chunkZ);
        scanChunkConnections(levelReader, chunkX, chunkZ + 1);
        scanChunkConnections(levelReader, chunkX, chunkZ - 1);
    }

    private static void scanChunkConnections(final LevelReader levelReader, final int chunkX, final int chunkZ) {
        if (!isApplicable() || !levelReader.hasChunk(chunkX, chunkZ)) {
            return;
        }

        final ChunkAccess chunkAccess = levelReader.getChunk(chunkX, chunkZ);
        final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (int sectionY = chunkAccess.getMinSectionY(); sectionY < chunkAccess.getMaxSectionY(); sectionY++) {
            final LevelChunkSection section = chunkAccess.getSection(chunkAccess.getSectionIndexFromSectionY(sectionY));
            if (section.hasOnlyAir()) {
                continue;
            }

            final int baseY = SectionPos.sectionToBlockCoord(sectionY);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        final BlockState blockState = section.getBlockState(x, y, z);
                        if (!isTorch(blockState)) {
                            continue;
                        }

                        blockPos.set(SectionPos.sectionToBlockCoord(chunkX) + x, baseY + y, SectionPos.sectionToBlockCoord(chunkZ) + z);
                        final BlockState newState = getAttachmentState(blockState, levelReader, blockPos);
                        if (newState != blockState) {
                            chunkAccess.setBlockState(blockPos, newState, UPDATE_FLAGS);
                        }
                    }
                }
            }
        }
    }

    public static void updateBlockConnections(final LevelReader levelReader, final BlockPos blockPos) {
        if (!isApplicable()) {
            return;
        }

        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    mutableBlockPos.set(blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z);
                    if (!levelReader.hasChunk(SectionPos.blockToSectionCoord(mutableBlockPos.getX()), SectionPos.blockToSectionCoord(mutableBlockPos.getZ()))) {
                        continue;
                    }

                    final BlockState blockState = levelReader.getBlockState(mutableBlockPos);
                    if (!isTorch(blockState)) {
                        continue;
                    }

                    final BlockState newState = getAttachmentState(blockState, levelReader, mutableBlockPos);
                    if (newState != blockState) {
                        levelReader.getChunk(SectionPos.blockToSectionCoord(mutableBlockPos.getX()), SectionPos.blockToSectionCoord(mutableBlockPos.getZ())).setBlockState(mutableBlockPos, newState, UPDATE_FLAGS);
                    }
                }
            }
        }
    }

    private static BlockState getAttachmentState(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        if (blockState.getBlock() == Blocks.WALL_TORCH) {
            final Direction facing = blockState.getValue(WallTorchBlock.FACING);
            if (isSolidWall(levelReader, blockPos.relative(facing.getOpposite()))) {
                return blockState;
            }
            return Blocks.TORCH.defaultBlockState();
        }

        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            if (isSolidWall(levelReader, blockPos.relative(direction))) {
                return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction);
            }
        }
        return blockState;
    }

    private static boolean isSolidWall(final LevelReader levelReader, final BlockPos blockPos) {
        if (!levelReader.hasChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()))) {
            return false;
        }
        return levelReader.getBlockState(blockPos).isSolid();
    }

    private static boolean isTorch(final BlockState blockState) {
        return blockState.getBlock() == Blocks.TORCH || blockState.getBlock() == Blocks.WALL_TORCH;
    }

    private static boolean isApplicable() {
        return GeneralSettings.INSTANCE.torchesStickToWallsClassicube.getValue() && ProtocolTranslator.getTargetVersion().equals(c0_30cpe);
    }

}