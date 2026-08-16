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

package com.viaversion.viafabricplus.injection.mixin.features.classic.cpe_extension;

import com.viaversion.viafabricplus.features.classic.cpe_extension.CPEAdditions;
import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.model.ClassicLevel;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.storage.ClassicLevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.raphimc.vialegacy.api.LegacyProtocolVersion.c0_30cpe;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void classicClimbing(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof LocalPlayer player)) return;
        if (!ProtocolTranslator.getTargetVersion().equals(c0_30cpe)) return;
        if (player.isSpectator()) return;

        final ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        final UserConnection user = ((IConnection) connection.getConnection()).viaFabricPlus$getUserConnection();
        final ClassicLevelStorage levelStorage = user.get(ClassicLevelStorage.class);
        if (levelStorage == null || !levelStorage.hasReceivedLevel()) return;
        final ClassicLevel classicLevel = levelStorage.getClassicLevel();
        if (classicLevel == null) return;

        final AABB bounds = player.getBoundingBox().expandTowards(0.0, 0.5 / 16.0, 0.0);
        final int minX = Mth.floor(bounds.minX);
        final int maxX = Mth.floor(bounds.maxX);
        final int minY = Mth.floor(bounds.minY);
        final int maxY = Mth.floor(bounds.maxY);
        final int minZ = Mth.floor(bounds.minZ);
        final int maxZ = Mth.floor(bounds.maxZ);

        for (final BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (!classicLevel.isInBounds(pos.getX(), pos.getY(), pos.getZ())) continue;
            if (!CPEAdditions.isClimbableBlock(classicLevel.getBlock(pos.getX(), pos.getY(), pos.getZ()))) continue;

            boolean blocked = false;
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                if (player.level().getBlockState(pos.relative(direction)).isSolid()) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

}