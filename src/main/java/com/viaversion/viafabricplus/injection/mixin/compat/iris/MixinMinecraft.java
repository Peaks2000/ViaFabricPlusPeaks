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

package com.viaversion.viafabricplus.injection.mixin.compat.iris;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.features.classic.rendering.ClassicShaderCompatibility;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Unique
    @Nullable
    private ResourceKey<Level> viaFabricPlus$previousDimension;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void capturePreviousDimension(ClientLevel level, CallbackInfo ci) {
        final ClientLevel currentLevel = Minecraft.getInstance().level;
        this.viaFabricPlus$previousDimension = currentLevel != null ? currentLevel.dimension() : null;
    }

    @Inject(method = "setLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;)V"))
    private void preserveIrisPipeline(ClientLevel level, CallbackInfo ci) {
        final boolean dimensionChanged = this.viaFabricPlus$previousDimension != null
                && !this.viaFabricPlus$previousDimension.equals(level.dimension());
        if (ClassicShaderCompatibility.shouldPreservePipeline(ProtocolTranslator.getTargetVersion(), dimensionChanged)) {
            if (ClassicShaderCompatibility.preservePipelineForSyntheticDimensionChange()) {
                ViaFabricPlusImpl.INSTANCE.getLogger().info(
                        "Preserved the Iris pipeline across synthetic Classic dimension change: {} => {}",
                        this.viaFabricPlus$previousDimension.identifier(), level.dimension().identifier()
                );
            }
        }
        this.viaFabricPlus$previousDimension = null;
    }
}
