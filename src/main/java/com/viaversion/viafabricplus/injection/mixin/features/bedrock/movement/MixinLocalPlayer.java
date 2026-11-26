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

package com.viaversion.viafabricplus.injection.mixin.features.bedrock.movement;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Shadow
    protected abstract boolean isSprintingPossible(final boolean allowedInShallowWater);

    @Redirect(method = "shouldStopRunSprinting", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/player/LocalPlayer;horizontalCollision:Z", opcode = Opcodes.GETFIELD))
    private boolean keepSprintWhileJumpingAlongWalls(final LocalPlayer player) {
        if (player.horizontalCollision && ProtocolTranslator.isBedrock() && player.input.keyPresses.jump()
            && !player.isPassenger() && !player.isInWater() && !player.isInLava() && !player.getAbilities().flying) {
            final Vec3 motion = player.getDeltaMovement();
            final double yaw = Math.toRadians(player.getYRot());
            final double forward = motion.z * Math.cos(yaw) - motion.x * Math.sin(yaw);
            // Collision clipped one axis. Keep sprint only when substantial forward travel remains.
            if (forward > 0.04D && forward * forward >= motion.horizontalDistanceSqr() * 0.5D) {
                return false;
            }
        }
        return player.horizontalCollision;
    }

    @Redirect(method = {"shouldStopRunSprinting", "canStartSprinting"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSprintingPossible(Z)Z"))
    private boolean allowNonSwimWaterSprinting(LocalPlayer instance, boolean allowedInShallowWater) {
        return this.isSprintingPossible(allowedInShallowWater || ProtocolTranslator.isBedrock() && (instance.isSwimming() || instance.onGround()));
    }

}
