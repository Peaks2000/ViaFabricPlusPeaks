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

import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.util.bedrock.BedrockCreativeInventory;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.connection.ConnectionDetails;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Unique
    private static final int viaFabricPlus$REJECTED_CREATIVE_CURSOR_GUARD_TICKS = 20;

    @Unique
    private ItemStack viaFabricPlus$pendingRejectedCreativeCursor = ItemStack.EMPTY;

    @Unique
    private int viaFabricPlus$pendingRejectedCreativeCursorTicks;

    @Unique
    private boolean viaFabricPlus$restoreRejectedCreativeCursorAfterContent;

    @Shadow
    public abstract Connection getConnection();

    @Inject(method = "handleLogin", at = @At("RETURN"))
    public void sendConnectionDetails(ClientboundLoginPacket packet, CallbackInfo ci) {
        final UserConnection user = ((IConnection) getConnection()).viaFabricPlus$getUserConnection();
        if (user != null) {
            ConnectionDetails.sendConnectionDetails(user, ConnectionDetails.MOD_CHANNEL);
        }
    }

    @Inject(method = "handleContainerContent", at = @At("HEAD"))
    private void guardMaintainedBedrockCreativeCursor(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        final var screen = Minecraft.getInstance().gui.screen();
        final ItemStack currentCursor = screen instanceof CreativeModeInventoryScreen creativeScreen
                ? creativeScreen.getMenu().getCarried()
                : ItemStack.EMPTY;
        final boolean currentCursorMatchesPending = !currentCursor.isEmpty()
                && currentCursor.getCount() == viaFabricPlus$pendingRejectedCreativeCursor.getCount()
                && ItemStack.isSameItemSameComponents(currentCursor, viaFabricPlus$pendingRejectedCreativeCursor);

        viaFabricPlus$restoreRejectedCreativeCursorAfterContent =
                BedrockCreativeInventory.shouldProtectRejectedCursorFromEmptyContent(
                        ((IConnection) getConnection()).viaFabricPlus$getTargetVersion(),
                        packet.containerId(),
                        packet.carriedItem().isEmpty(),
                        viaFabricPlus$pendingRejectedCreativeCursor.isEmpty(),
                        currentCursorMatchesPending,
                        screen instanceof CreativeModeInventoryScreen);

        if (!viaFabricPlus$pendingRejectedCreativeCursor.isEmpty() && !currentCursorMatchesPending) {
            viaFabricPlus$clearRejectedCreativeCursorGuard();
        }
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void restoreMaintainedBedrockCreativeCursor(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        final var screen = Minecraft.getInstance().gui.screen();
        if (BedrockCreativeInventory.shouldRestoreRejectedCursor(
                ((IConnection) getConnection()).viaFabricPlus$getTargetVersion(),
                packet.containerId(),
                packet.carriedItem().isEmpty(),
                screen instanceof CreativeModeInventoryScreen)) {
            // Keep a bounded copy so a later authoritative empty-cursor packet cannot erase
            // Bedrock's rejected item after the initial rollback packet has been handled.
            viaFabricPlus$pendingRejectedCreativeCursor = packet.carriedItem().copy();
            viaFabricPlus$pendingRejectedCreativeCursorTicks = viaFabricPlus$REJECTED_CREATIVE_CURSOR_GUARD_TICKS;
            ((CreativeModeInventoryScreen) screen).getMenu().setCarried(viaFabricPlus$pendingRejectedCreativeCursor.copy());
        } else if (viaFabricPlus$restoreRejectedCreativeCursorAfterContent
                && screen instanceof CreativeModeInventoryScreen creativeScreen) {
            creativeScreen.getMenu().setCarried(viaFabricPlus$pendingRejectedCreativeCursor.copy());
        }
        viaFabricPlus$restoreRejectedCreativeCursorAfterContent = false;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void expireMaintainedBedrockCreativeCursorGuard(CallbackInfo ci) {
        final var screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof CreativeModeInventoryScreen) || --viaFabricPlus$pendingRejectedCreativeCursorTicks <= 0) {
            viaFabricPlus$clearRejectedCreativeCursorGuard();
        }
    }

    @Unique
    private void viaFabricPlus$clearRejectedCreativeCursorGuard() {
        viaFabricPlus$pendingRejectedCreativeCursor = ItemStack.EMPTY;
        viaFabricPlus$pendingRejectedCreativeCursorTicks = 0;
        viaFabricPlus$restoreRejectedCreativeCursorAfterContent = false;
    }

}
