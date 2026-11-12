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

package com.viaversion.viafabricplus.injection.mixin.core.privacy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.features.networking.privacy.EditableTextPrivacy;
import com.viaversion.viafabricplus.settings.impl.PrivacySettings;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractSignEditScreen.class)
public abstract class MixinAbstractSignEditScreen {

    @WrapOperation(method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;Lnet/minecraft/world/level/block/entity/SignTextSlot;ZLnet/minecraft/network/chat/Component;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"))
    private String protectEditableServerText(final Component component, final Operation<String> original) {
        if (PrivacySettings.INSTANCE.privacyMode.isActive()) {
            return EditableTextPrivacy.getEditableText(component);
        }
        return original.call(component);
    }
}
