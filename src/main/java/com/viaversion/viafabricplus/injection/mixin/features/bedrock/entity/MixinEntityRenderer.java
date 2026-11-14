/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.injection.mixin.features.bedrock.entity;

import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
        at = @At("RETURN"))
    private void hideBedrockItemAndArrowNames(final Entity entity, final EntityRenderState state,
                                              final float partialTick, final double nameRange,
                                              final double scoreRange, final CallbackInfo ci) {
        if (state.nameTag == null || !(entity instanceof ItemEntity || entity instanceof AbstractArrow)) {
            return;
        }
        final var listener = Minecraft.getInstance().getConnection();
        if (listener != null && ProtocolTranslator.isBedrock(((IConnection) listener.getConnection()).viaFabricPlus$getTargetVersion())) {
            state.nameTag = null;
        }
    }

}
