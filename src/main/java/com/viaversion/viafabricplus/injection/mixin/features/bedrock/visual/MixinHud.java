/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.injection.mixin.features.bedrock.visual;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class MixinHud {

    @Unique
    private static final EquipmentSlot[] VIAFABRICPLUS_ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @WrapOperation(method = "extractArmor", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;getArmorValue()I"))
    private static int showBedrockArmor(final Player player, final Operation<Integer> original) {
        final int reportedArmor = original.call(player);
        final var connection = Minecraft.getInstance().getConnection();
        if (reportedArmor != 0 || !(player instanceof LocalPlayer) || connection == null
            || !ProtocolTranslator.isBedrock(((IConnection) connection.getConnection()).viaFabricPlus$getTargetVersion())) {
            return reportedArmor;
        }

        // Bedrock sends the equipped stack without a separate Java armor-attribute update.
        final AttributeInstance armor = new AttributeInstance(Attributes.ARMOR, ignored -> {});
        for (final EquipmentSlot slot : VIAFABRICPLUS_ARMOR_SLOTS) {
            player.getItemBySlot(slot).forEachModifier(slot, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ARMOR)) {
                    armor.addOrUpdateTransientModifier(modifier);
                }
            });
        }
        return (int) armor.getValue();
    }
}
