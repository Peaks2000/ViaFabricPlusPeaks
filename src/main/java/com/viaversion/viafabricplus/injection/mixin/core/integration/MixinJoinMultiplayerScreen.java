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

package com.viaversion.viafabricplus.injection.mixin.core.integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viafabricplus.settings.impl.BedrockSettings;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {

    @Shadow
    private ServerData editingServer;

    @Unique
    private ServerSelectionList.OnlineServerEntry viaFabricPlus$editingEntry;

    public MixinJoinMultiplayerScreen(final Component component) {
        super(component);
    }

    @WrapOperation(method = "join(Lnet/minecraft/client/multiplayer/ServerData;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/resolver/ServerAddress;parseString(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/resolver/ServerAddress;"))
    private ServerAddress replaceDefaultPort(String input, Operation<ServerAddress> original, @Local(argsOnly = true) ServerData data) {
        final IServerData mixinServerInfo = (IServerData) data;

        ProtocolVersion version;
        if (mixinServerInfo.viaFabricPlus$passedDirectConnectScreen()) {
            version = ProtocolTranslator.getTargetVersion();
        } else {
            version = mixinServerInfo.viaFabricPlus$forcedVersion();
        }
        return original.call(BedrockSettings.replaceDefaultPort(input, version));
    }

    @WrapOperation(method = "directJoinCallback", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen;join(Lnet/minecraft/client/multiplayer/ServerData;)V"))
    private void storeDirectConnectionPhase(JoinMultiplayerScreen instance, ServerData data, Operation<Void> original) {
        ((IServerData) data).viaFabricPlus$passDirectConnectScreen(true);
        original.call(instance, data);
    }

    @WrapOperation(method = "lambda$init$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList$OnlineServerEntry;getServerData()Lnet/minecraft/client/multiplayer/ServerData;"))
    private ServerData rememberEditedServer(ServerSelectionList.OnlineServerEntry entry, Operation<ServerData> original) {
        this.viaFabricPlus$editingEntry = entry;
        return original.call(entry);
    }

    @ModifyVariable(method = "editServerCallback", at = @At("STORE"), name = "entry")
    private ServerSelectionList.Entry useEditedServer(ServerSelectionList.Entry selected) {
        // The multiplayer list can lose or change its selection while the editor is open.
        final ServerSelectionList.OnlineServerEntry entry = this.viaFabricPlus$editingEntry;
        this.viaFabricPlus$editingEntry = null;
        return entry != null ? entry : selected;
    }

    @Inject(method = "editServerCallback", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;save()V"))
    private void storeForcedVersion(boolean result, CallbackInfo ci, @Local ServerData current) {
        ((IServerData) current).viaFabricPlus$forceVersion(((IServerData) this.editingServer).viaFabricPlus$forcedVersion());
    }

}
