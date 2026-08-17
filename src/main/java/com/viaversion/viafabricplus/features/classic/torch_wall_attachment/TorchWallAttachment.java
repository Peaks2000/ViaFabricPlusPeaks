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
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import static net.raphimc.vialegacy.api.LegacyProtocolVersion.c0_30cpe;

public final class TorchWallAttachment {

    private static final int UPDATE_FLAGS = 18;

    private static final Predicate<BlockState> IS_TORCH = state -> state.getBlock() == Blocks.TORCH || state.getBlock() == Blocks.WALL_TORCH;

    public static void updateChunkConnections(final LevelReader levelReader, final int chunkX, final int chunkZ) {
        if (!isApplicable() || !levelReader.hasChunk(chunkX, chunkZ)) {
            return;
        }

        // Scan the loaded chunk plus its 1 block wide border. Torches in the border belong to the
        // neighboring chunk but may have their wall inside this chunk, so they are re-evaluated and
        // written back to their own chunk. This covers all load orders with a single chunk scan.
        final ChunkAccess chunkAccess = levelReader.getChunk(chunkX, chunkZ);
        final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        final int chunkBlockX = SectionPos.sectionToBlockCoord(chunkX);
        final int chunkBlockZ = SectionPos.sectionToBlockCoord(chunkZ);
        for (int sectionY = chunkAccess.getMinSectionY(); sectionY < chunkAccess.getMaxSectionY(); sectionY++) {
            final LevelChunkSection section = chunkAccess.getSection(chunkAccess.getSectionIndexFromSectionY(sectionY));
            if (section.hasOnlyAir() || !section.getStates().maybeHas(IS_TORCH)) {
                continue;
            }

            final int baseY = SectionPos.sectionToBlockCoord(sectionY);
            for (int x = -1; x <= 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = -1; z <= 16; z++) {
                        blockPos.set(chunkBlockX + x, baseY + y, chunkBlockZ + z);
                        if (!levelReader.hasChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()))) {
                            continue;
                        }

                        final BlockState blockState = levelReader.getBlockState(blockPos);
                        if (!isTorch(blockState)) {
                            continue;
                        }

                        final BlockState newState = getAttachmentState(blockState, levelReader, blockPos);
                        if (newState != blockState) {
                            setAttachmentState(levelReader, blockPos, blockState, newState);
                        }
                    }
                }
            }
        }
    }

    public static void updateWallTorchNeighbors(final LevelReader levelReader, final BlockPos blockPos) {
        if (!isApplicable()) {
            return;
        }

        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            mutableBlockPos.set(blockPos).move(direction);
            if (!levelReader.hasChunk(SectionPos.blockToSectionCoord(mutableBlockPos.getX()), SectionPos.blockToSectionCoord(mutableBlockPos.getZ()))) {
                continue;
            }

            final BlockState blockState = levelReader.getBlockState(mutableBlockPos);
            if (blockState.getBlock() != Blocks.WALL_TORCH || blockState.getValue(WallTorchBlock.FACING) != direction) {
                continue;
            }

            final BlockState newState = getAttachmentState(blockState, levelReader, mutableBlockPos);
            if (newState != blockState) {
                setAttachmentState(levelReader, mutableBlockPos, blockState, newState);
            }
        }
    }

    public static BlockState getAttachmentState(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        if (blockState.getBlock() == Blocks.WALL_TORCH) {
            final Direction facing = blockState.getValue(WallTorchBlock.FACING);
            if (isSolidWall(levelReader, blockPos.relative(facing.getOpposite()))) {
                return blockState;
            }
            return Blocks.TORCH.defaultBlockState();
        }

        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            if (isSolidWall(levelReader, blockPos.relative(direction))) {
                // The wall is on the side the torch is facing towards, so the FACING property points away from the wall
                return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction.getOpposite());
            }
        }
        return blockState;
    }

    public static boolean isApplicable() {
        return GeneralSettings.INSTANCE.torchesStickToWallsClassicube.getValue() && ProtocolTranslator.getTargetVersion().equals(c0_30cpe);
    }

    private static void setAttachmentState(final LevelReader levelReader, final BlockPos blockPos, final BlockState oldState, final BlockState newState) {
        levelReader.getChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ())).setBlockState(blockPos, newState, UPDATE_FLAGS);
        if (levelReader instanceof Level level) {
            level.setBlocksDirty(blockPos, oldState, newState);
        }
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

}