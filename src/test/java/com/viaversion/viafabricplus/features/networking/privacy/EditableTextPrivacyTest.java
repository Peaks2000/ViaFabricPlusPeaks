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

package com.viaversion.viafabricplus.features.networking.privacy;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.api.ViaFabricPlusAPI;
import com.viaversion.viafabricplus.settings.impl.PrivacySettings;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.KeybindResolver;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;

final class EditableTextPrivacyTest {
    private static Minecraft originalMinecraft;
    private static Object originalApi;
    private final Language originalLanguage = Language.getInstance();

    @BeforeAll
    static void bootstrap() throws Exception {
        originalMinecraft = Minecraft.getInstance();
        final var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        final Unsafe unsafe = (Unsafe) unsafeField.get(null);
        final var instanceField = Minecraft.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, unsafe.allocateInstance(Minecraft.class));
        final var apiField = ViaFabricPlus.class.getDeclaredField("api");
        apiField.setAccessible(true);
        originalApi = apiField.get(null);
        if (originalApi == null) {
            final var api = Proxy.newProxyInstance(
                ViaFabricPlusAPI.class.getClassLoader(),
                new Class<?>[]{ViaFabricPlusAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("targetVersion")) {
                        return ProtocolVersion.v26_3;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
            apiField.set(null, api);
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterAll
    static void restoreMinecraft() throws Exception {
        final var instanceField = Minecraft.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, originalMinecraft);
        final var apiField = ViaFabricPlus.class.getDeclaredField("api");
        apiField.setAccessible(true);
        apiField.set(null, originalApi);
    }

    @AfterEach
    void restoreSettings() {
        Language.inject(originalLanguage);
        PrivacySettings.INSTANCE.privacyMode.setActive(true);
    }

    @Test
    void signEditorDoesNotReflectInstalledTranslations() throws Exception {
        installProbeLanguage();
        for (boolean privacy : new boolean[]{true, false}) {
            PrivacySettings.INSTANCE.privacyMode.setActive(privacy);
            final var text = new SignText(List.of(Component.translatable("base.viafabricplus.settings"), Component.literal("ordinary text")),
                List.of(Component.literal("filtered")), DyeColor.BLACK, false);
            final var sign = new SignBlockEntity(BlockPos.ZERO, Blocks.OAK_SIGN.defaultBlockState()) {
                @Override
                public SignText getText(SignTextSlot slot) {
                    return text;
                }
            };
            final var screen = new SignEditScreen(sign, SignTextSlot.FRONT, false);
            final Field messagesField = AbstractSignEditScreen.class.getDeclaredField("messages");
            messagesField.setAccessible(true);
            final String[] messages = (String[]) messagesField.get(screen);
            assertEquals(privacy ? "base.viafabricplus.settings" : "PRIVATE_INSTALLED_MOD", messages[0]);
            assertEquals("ordinary text", messages[1]);
            assertEquals("PRIVATE_INSTALLED_MOD", Component.translatable("base.viafabricplus.settings").getString());
        }
    }
    @Test
    void signProtectionCoversBothSidesFilteringAndHangingSigns() throws Exception {
        installProbeLanguage();
        final Field messagesField = AbstractSignEditScreen.class.getDeclaredField("messages");
        messagesField.setAccessible(true);
        for (boolean hanging : new boolean[]{true, false}) {
            final var state = (hanging ? Blocks.OAK_HANGING_SIGN : Blocks.OAK_SIGN).defaultBlockState();
            final var sign = new SignBlockEntity(hanging ? BlockEntityTypes.HANGING_SIGN
                : BlockEntityTypes.SIGN, BlockPos.ZERO, state) {
                @Override
                public SignText getText(SignTextSlot slot) {
                    final String side = slot.name();
                    return new SignText(List.of(Component.translatableWithFallback("base.viafabricplus.settings", side)),
                        List.of(Component.translatableWithFallback("base.viafabricplus.settings", side + " filtered")), DyeColor.BLACK, false);
                }
            };
            for (SignTextSlot slot : SignTextSlot.values()) {
                for (boolean filtered : new boolean[]{true, false}) {
                    final AbstractSignEditScreen screen = hanging
                        ? new HangingSignEditScreen(sign, slot, filtered)
                        : new SignEditScreen(sign, slot, filtered);
                    final String[] messages = (String[]) messagesField.get(screen);
                    assertEquals(slot.name() + (filtered ? " filtered" : ""), messages[0]);
                    final var packet = new ServerboundSignUpdatePacket(BlockPos.ZERO, List.of(messages), slot);
                    assertFalse(packet.lines().stream().anyMatch(line -> line.contains("PRIVATE_INSTALLED_MOD")));
                }
            }
        }
    }

    @Test
    void nestedProbesNeverResolveLocalResources() {
        final Component probe = Component.literal("prefix ")
            .append(Component.translatable("base.viafabricplus.settings", Component.keybind("key.secret")))
            .append(Component.keybind("key.secret"))
            .append(Component.object(new AtlasSprite(
                Identifier.parse("minecraft:blocks"), Identifier.parse("minecraft:stone")),
                Component.translatableWithFallback("probe.unknown", "server fallback")))
            .append(Component.literal(" suffix"));
        assertEquals("prefix base.viafabricplus.settingskey.secretserver fallback suffix", EditableTextPrivacy.getEditableText(probe));
        assertEquals("server %s", EditableTextPrivacy.getEditableText(Component.translatableWithFallback(
            "probe.unknown", "server %s", Component.translatable("base.viafabricplus.settings"))));
        final Component literal = Component.literal("My normal sign and renamed item");
        assertEquals(literal.getString(), EditableTextPrivacy.getEditableText(literal));
        PrivacySettings.INSTANCE.privacyMode.setActive(false);
        assertEquals(probe.getString(), EditableTextPrivacy.getEditableText(probe));
    }

    @Test
    void anvilDoesNotReflectLocalTextAndKeepsDefaultNamesUnchanged() throws Exception {
        installProbeLanguage();
        final var inventory = new Inventory(null, new EntityEquipment());
        final var returnedName = new AtomicReference<String>();
        final var menu = new AnvilMenu(1, inventory) {
            @Override
            public void createResult() {
            }

            @Override
            public boolean setItemName(String name) {
                returnedName.set(name);
                return false;
            }
        };
        final var screen = new AnvilScreen(menu, inventory, Component.literal("Anvil"));
        final var field = new EditBox(null, 100, 20, Component.empty()) {
            @Override
            public void setFocused(boolean focused) {
            }
        };
        final Field nameField = AnvilScreen.class.getDeclaredField("name");
        nameField.setAccessible(true);
        nameField.set(screen, field);
        final var onNameChanged = AnvilScreen.class.getDeclaredMethod("onNameChanged", String.class);
        onNameChanged.setAccessible(true);
        field.setMaxLength(50);
        field.setResponder(value -> {
            try {
                onNameChanged.invoke(screen, value);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        final var itemType = Holder.direct(Items.STONE,
            DataComponentMap.builder().set(DataComponents.ITEM_NAME,
                Component.translatable("block.minecraft.stone")).build());
        for (boolean privacy : new boolean[]{true, false}) {
            PrivacySettings.INSTANCE.privacyMode.setActive(privacy);
            final var item = new ItemStack(itemType);
            item.set(DataComponents.CUSTOM_NAME, Component.translatable("base.viafabricplus.settings"));
            menu.getSlot(0).set(item);
            screen.slotChanged(menu, 0, item);
            assertEquals(privacy ? "base.viafabricplus.settings" : "PRIVATE_INSTALLED_MOD", returnedName.get());
            final var ordinaryItem = new ItemStack(itemType);
            menu.getSlot(0).set(ordinaryItem);
            screen.slotChanged(menu, 0, ordinaryItem);
            assertEquals("", returnedName.get());
            assertEquals(EditableTextPrivacy.getEditableText(ordinaryItem.getHoverName()), field.getValue());
            field.setValue("Name I typed");
            assertEquals("Name I typed", returnedName.get());
            final Component longProbe = Component.literal("prefix ")
                .append(Component.translatable("base.viafabricplus.settings")).append("x".repeat(60));
            final var probeType = Holder.direct(Items.STONE,
                DataComponentMap.builder().set(DataComponents.ITEM_NAME, longProbe).build());
            final var probeItem = new ItemStack(probeType);
            menu.getSlot(0).set(probeItem);
            screen.slotChanged(menu, 0, probeItem);
            assertEquals("", returnedName.get());
            // Resizing the native screen restores the stored, truncated value through this same responder.
            field.setValue(field.getValue());
            assertEquals(EditableTextPrivacy.getEditableText(longProbe).substring(0, 50), returnedName.get());
            assertEquals(!privacy, returnedName.get().contains("PRIVATE_INSTALLED_MOD"));
            field.setValue(field.getValue().substring(0, 49));
            assertEquals(!privacy, returnedName.get().contains("PRIVATE_INSTALLED_MOD"));
        }
    }

    @Test
    void keybindProbeDoesNotReadLocalBindings() throws Exception {
        final var resolverField = KeybindResolver.class.getDeclaredField("keyResolver");
        resolverField.setAccessible(true);
        final Object originalResolver = resolverField.get(null);
        try {
            KeybindResolver.setKeyResolver(key -> () -> Component.literal("PRIVATE_KEY_BINDING"));
            final Component keybind = Component.keybind("key.secret.mod");
            assertEquals("PRIVATE_KEY_BINDING", keybind.getString());
            assertEquals("key.secret.mod", EditableTextPrivacy.getEditableText(keybind));
            final Component nested = Component.object(new AtlasSprite(
                Identifier.parse("minecraft:blocks"), Identifier.parse("minecraft:stone")), keybind);
            assertEquals("key.secret.mod", EditableTextPrivacy.getEditableText(nested));
            PrivacySettings.INSTANCE.privacyMode.setActive(false);
            assertEquals("PRIVATE_KEY_BINDING", EditableTextPrivacy.getEditableText(nested));
        } finally {
            resolverField.set(null, originalResolver);
        }
    }

    private void installProbeLanguage() {
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return key.equals("base.viafabricplus.settings") ? "PRIVATE_INSTALLED_MOD" : originalLanguage.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return key.equals("base.viafabricplus.settings") || originalLanguage.has(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return originalLanguage.isDefaultRightToLeft();
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return originalLanguage.getVisualOrder(text);
            }
        });
    }

}
