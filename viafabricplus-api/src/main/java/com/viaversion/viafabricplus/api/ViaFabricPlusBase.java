/*
 * This file is part of ViaFabricPlus - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.viaversion.viafabricplus.api;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.nio.file.Path;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

/**
 * General API point for mods. Get the instance via {@link ViaFabricPlus#getImpl()}.
 */
public interface ViaFabricPlusBase {

    /**
     * @return an <b>internally based API version</b> incremented with meaningful or breaking changes.
     */
    default int apiVersion() {
        return 7;
    }

    /**
     * @return The version of the mod as displayed in mod lists (e.g., 4.0.0)
     */
    String getVersion();

    /**
     * @return The implementation version formatted as "git-ViaFabricPlus-{@link #getVersion()}-hash of the commit the jar was built on"
     */
    String getImplVersion();

    /**
     * @return The path where ViaFabricPlus stores its configuration files.
     */
    Path getPath();

    /**
     * @return The active version ViaFabricPlus is translating to.
     */
    ProtocolVersion getTargetVersion();

    /**
     * Sets active version ViaFabricPlus will translate future (!) connections to.
     *
     * @param targetVersion the target version
     * @throws IllegalStateException if there is an active connection to a server
     */
    void setTargetVersion(final ProtocolVersion targetVersion) throws IllegalStateException;

    /**
     * Sets active version ViaFabricPlus will translate future (!) connections to.
     *
     * @param targetVersion      the target version
     * @param revertOnDisconnect if true, the previous version will be set when the player disconnects from the server
     * @throws IllegalStateException if there is an active connection to a server
     */
    void setTargetVersion(final ProtocolVersion targetVersion, final boolean revertOnDisconnect) throws IllegalStateException;

    /**
     * @return the current UserConnection of the connection to the server, if the player isn't connected to a server, it will return null
     */
    @Nullable UserConnection getUserConnection();

    /**
     * Get the UserConnection for the given connection {@link Connection}.
     *
     * @param connection the connection
     * @return the UserConnection
     */
    @Nullable UserConnection getUserConnection(final Connection connection);

    /**
     * Gets the per-server protocol version for the given server.
     *
     * @param serverInfo the server info
     * @return the server version
     */
    @Nullable ProtocolVersion getServerVersion(final ServerData serverInfo);

    /**
     * Register a callback for when the user changes the target version in the screen, or if the user joins a server with a different version.
     *
     * @param callback the callback
     */
    void registerOnChangeProtocolVersionCallback(final ChangeProtocolVersionCallback callback);

    /**
     * Calculates the maximum chat length for given {@link ProtocolVersion} instance.
     *
     * @return The maximum chat length
     */
    int getMaxChatLength(final ProtocolVersion version);

    /**
     * Gets a boolean setting from the settings screen by its translation key.
     *
     * @param translationKey The translation key of the setting.
     * @return The boolean value of the setting.
     */
    boolean getBooleanSetting(final String translationKey);

    /**
     * Gets the mode setting from the settings screen by its translation key.
     *
     * @param translationKey The translation key of the setting.
     * @return The translation key of the selected mode.
     */
    String getModeSetting(final String translationKey);

    /**
     * Gets the auto version setting from the settings screen by its translation key.
     *
     * @param translationKey The translation key of the setting.
     * @return The translation key, which can be "base.viafabricplus.auto", "base.viafabricplus.off" or "base.viafabricplus.on".
     */
    String getAutoVersionSetting(final String translationKey);

    /**
     * Converts a Minecraft item stack {@link ItemStack} to a ViaVersion item {@link Item}
     *
     * @param stack         The Minecraft item stack to convert {@link ItemStack}
     * @param targetVersion The target version to convert to (e.g., v1.13) {@link ProtocolVersion}
     * @return The ViaVersion item for the target version {@link Item}
     */
    @Nullable Item translateItem(final ItemStack stack, final ProtocolVersion targetVersion);

    /**
     * Converts a ViaVersion item {@link Item} to a Minecraft item stack {@link ItemStack}
     *
     * @param item          The ViaVersion item to convert {@link Item}
     * @param sourceVersion The source version of the item (e.g., b1.8) {@link ProtocolVersion}
     * @return The Minecraft item stack for the source version {@link ItemStack}
     */
    @Nullable ItemStack translateItem(final Item item, final ProtocolVersion sourceVersion);

    /**
     * @param item    The item to check
     * @param version The version to check for
     * @return true if the item exists in the given version, false otherwise; this will also check for CPE items (CustomBlocks V1 extension)
     */
    boolean itemExists(final net.minecraft.world.item.Item item, final ProtocolVersion version);

    /**
     * @param enchantment The enchantment to check
     * @param version     The version to check for
     * @return true if the enchantment exists in the given version, false otherwise
     */
    boolean enchantmentExists(final ResourceKey<Enchantment> enchantment, final ProtocolVersion version);

    /**
     * @param effect  The status effect to check
     * @param version The version to check for
     * @return true if the status effect exists in the given version, false otherwise
     */
    boolean effectExists(final Holder<MobEffect> effect, final ProtocolVersion version);

    /**
     * Similar to {@link #itemExists(net.minecraft.world.item.Item, ProtocolVersion)}, but takes in the current connection details (e.g., classic protocol extensions being loaded)
     *
     * @param item The item to check
     * @return true if the item exists in the current connection, false otherwise
     */
    boolean itemExistsInConnection(final net.minecraft.world.item.Item item);

    /**
     * Same as {@link #itemExists(net.minecraft.world.item.Item, ProtocolVersion)}, but for item stacks. This also compares against certain data components like enchantments or banner patterns.
     *
     * @param stack The item stack to check
     * @return true if the item stack exists in the given version, false otherwise
     */
    boolean itemExistsInConnection(final ItemStack stack);

    /**
     * Similar to {@link ItemStack#getCount()}, but also handles negative item counts in pre 1.11 versions
     *
     * @param stack The item stack to get the count of
     * @return the count of the item stack can be negative in pre 1.11 versions
     */
    int getStackCount(final ItemStack stack);


}
