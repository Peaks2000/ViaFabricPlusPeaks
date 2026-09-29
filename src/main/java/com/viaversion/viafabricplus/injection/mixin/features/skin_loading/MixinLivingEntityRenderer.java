/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.injection.mixin.features.skin_loading;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.viaversion.viafabricplus.util.bedrock.BedrockSkinBridge;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
    private void renderCreatorGeometry(final SubmitNodeCollector collector, final Model<?> originalModel,
                                      final Object state, final PoseStack poses, final RenderType renderType,
                                      final int light, final int overlay, final int color, final UvMapping uv,
                                      final int outline, final Operation<Void> original) {
        if (state instanceof AvatarRenderState avatar) {
            final Model<AvatarRenderState> creator = BedrockSkinBridge.creatorModel(avatar.skin);
            if (creator != null) {
                collector.submitModel(creator, avatar, poses, renderType, light, overlay, color, uv, outline);
                return;
            }
        }
        original.call(collector, originalModel, state, poses, renderType, light, overlay, color, uv, outline);
    }
}
