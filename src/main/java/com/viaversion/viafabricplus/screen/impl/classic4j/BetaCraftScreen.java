/*
 * This file is part of ViaFabricPlus - https://github.com/Peaks2000/ViaFabricPlusPeaks
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

package com.viaversion.viafabricplus.screen.impl.classic4j;

import com.viaversion.viafabricplus.injection.access.core.IEditBox;
import com.viaversion.viafabricplus.screen.VFPList;
import com.viaversion.viafabricplus.screen.VFPListEntry;
import com.viaversion.viafabricplus.screen.VFPScreen;
import com.viaversion.viafabricplus.screen.impl.settings.TitleEntry;
import com.viaversion.viafabricplus.util.network.ConnectionUtil;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianreuth.classic4j.BetaCraftHandler;
import de.florianreuth.classic4j.model.betacraft.BCServerInfo;
import de.florianreuth.classic4j.model.betacraft.BCServerList;
import de.florianreuth.classic4j.model.betacraft.BCVersionCategory;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.jspecify.annotations.NonNull;

import static com.viaversion.viafabricplus.screen.VFPListEntry.SLOT_MARGIN;

public final class BetaCraftScreen extends VFPScreen {

    public static final BetaCraftScreen INSTANCE = new BetaCraftScreen();

    public static BCServerList SERVER_LIST;
    private static final String BETA_CRAFT_SERVER_LIST_URL = "https://betacraft.uk/serverlist/";

    /**
     * The server list API provides the game version of each server as a string, which can't be resolved by the
     * protocol auto-detection (it only supports the modern status ping). This map resolves the known versions.
     */
    private static final Map<String, ProtocolVersion> GAME_VERSION_MAP = new HashMap<>();

    static {
        GAME_VERSION_MAP.put("c0.30-c-1900", LegacyProtocolVersion.c0_28toc0_30);
        GAME_VERSION_MAP.put("a1.1.2_01", LegacyProtocolVersion.a1_1_0toa1_1_2_1);
        GAME_VERSION_MAP.put("a1.2.6", LegacyProtocolVersion.a1_2_3_5toa1_2_6);
        GAME_VERSION_MAP.put("b1.1_02", LegacyProtocolVersion.b1_1_2);
        GAME_VERSION_MAP.put("b1.2_01", LegacyProtocolVersion.b1_2_0tob1_2_2);
        GAME_VERSION_MAP.put("b1.5_01", LegacyProtocolVersion.b1_5tob1_5_2);
        GAME_VERSION_MAP.put("b1.6.6", LegacyProtocolVersion.b1_6tob1_6_6);
        GAME_VERSION_MAP.put("b1.7.3", LegacyProtocolVersion.b1_7tob1_7_3);
        GAME_VERSION_MAP.put("1.2.5", LegacyProtocolVersion.r1_2_4tor1_2_5);
        GAME_VERSION_MAP.put("1.4", LegacyProtocolVersion.r1_4_2);
        GAME_VERSION_MAP.put("1.4.7", LegacyProtocolVersion.r1_4_6tor1_4_7);
        GAME_VERSION_MAP.put("1.5.2", LegacyProtocolVersion.r1_5_2);
        GAME_VERSION_MAP.put("1.6.4", LegacyProtocolVersion.r1_6_4);
    }

    private EditBox searchField;
    private SlotList slotList;

    private BetaCraftScreen() {
        super("BetaCraft", true);
    }

    @Override
    protected void init() {
        super.init();
        if (SERVER_LIST != null) {
            createView();
            return;
        }
        setupSubtitle(Component.translatable("betacraft.viafabricplus.loading"));
        BetaCraftHandler.requestServerList(serverList -> {
            BetaCraftScreen.SERVER_LIST = serverList;
            createView();
        }, throwable -> showErrorScreen(BetaCraftScreen.INSTANCE.getTitle(), throwable, this));
    }

    private void createView() {
        this.setupSubtitle(Component.nullToEmpty(BETA_CRAFT_SERVER_LIST_URL), ConfirmLinkScreen.confirmLink(this, BETA_CRAFT_SERVER_LIST_URL));

        final int entryHeight = (font.lineHeight + 2) * 3; // title is 2
        final int searchBarY = 2 * SLOT_MARGIN + entryHeight;

        this.addRenderableWidget(searchField = new EditBox(font, 5, searchBarY, width - 10, 20, Component.empty()));
        searchField.setHint(Component.translatable("base.viafabricplus.search"));
        searchField.setResponder(query -> updateSearch());
        ((IEditBox) searchField).viaFabricPlus$unlockForbiddenCharacters();

        this.addRenderableWidget(slotList = new SlotList(this.minecraft, width, height, searchBarY + 24, -5, entryHeight, normalizeQuery(searchField.getValue())));

        this.addRefreshButton(() -> SERVER_LIST = null);
    }

    private void updateSearch() {
        if (slotList == null) {
            return;
        }
        removeWidget(slotList);
        final int entryHeight = (font.lineHeight + 2) * 3;
        addRenderableWidget(slotList = new SlotList(this.minecraft, width, height, 2 * SLOT_MARGIN + entryHeight + 24, -5, entryHeight, normalizeQuery(searchField.getValue())));
    }

    private static String normalizeQuery(final String query) {
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesQuery(final BCServerInfo server, final String query) {
        if (query.isEmpty()) {
            return true;
        }
        return server.name().toLowerCase(Locale.ROOT).contains(query)
            || server.socket().toLowerCase(Locale.ROOT).contains(query)
            || server.gameVersion().toLowerCase(Locale.ROOT).contains(query);
    }

    private static ProtocolVersion determineVersion(final BCServerInfo server) {
        final String gameVersion = server.gameVersion() == null ? "" : server.gameVersion().trim().toLowerCase(Locale.ROOT);
        final ProtocolVersion version = GAME_VERSION_MAP.get(gameVersion);
        if (version != null) {
            return version;
        }
        if (gameVersion.startsWith("c0.30")) {
            return LegacyProtocolVersion.c0_28toc0_30;
        }
        final String protocol = server.protocol();
        if (protocol != null) {
            try {
                final int protocolId = Integer.parseInt(protocol);
                if (ProtocolVersion.isRegistered(protocolId)) {
                    return ProtocolVersion.getProtocol(protocolId);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @Override
    protected boolean subtitleCentered() {
        return SERVER_LIST == null;
    }

    public static class SlotList extends VFPList {
        private static double scrollAmount;

        public SlotList(Minecraft minecraftClient, int width, int height, int top, int bottom, int entryHeight, String query) {
            super(minecraftClient, width, height, top, bottom, entryHeight);
            if (SERVER_LIST == null) {
                return;
            }

            for (BCVersionCategory value : BCVersionCategory.values()) {
                final List<BCServerInfo> servers = SERVER_LIST.serversOfVersionCategory(value);
                if (servers.isEmpty()) {
                    continue;
                }
                final List<BCServerInfo> filtered = servers.stream().filter(server -> matchesQuery(server, query)).toList();
                if (filtered.isEmpty()) {
                    continue;
                }
                addEntry(new TitleEntry(Component.nullToEmpty(value.name())));
                for (BCServerInfo server : filtered) {
                    addEntry(new ServerSlot(server));
                }
            }

            if (query.isEmpty()) {
                initScrollY(scrollAmount);
            }
        }

        @Override
        public int getRowWidth() {
            return super.getRowWidth() + 140;
        }

        @Override
        protected void updateSlotAmount(double amount) {
            scrollAmount = amount;
        }
    }

    public static class ServerSlot extends VFPListEntry {
        private final BCServerInfo server;

        public ServerSlot(BCServerInfo server) {
            this.server = server;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.nullToEmpty(server.name());
        }

        @Override
        public void mappedMouseClicked(double mouseX, double mouseY, int button) {
            ConnectionUtil.connect(server.name(), server.socket(), determineVersion(server));
            super.mappedMouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void mappedRender(GuiGraphicsExtractor context, int x, int y, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            final Font textRenderer = Minecraft.getInstance().font;
            context.centeredText(textRenderer, server.name() + ChatFormatting.DARK_GRAY + " [" + server.gameVersion() + "]", entryWidth / 2, entryHeight / 2 - textRenderer.lineHeight / 2, -1);

            if (server.onlineMode()) {
                context.text(textRenderer, Component.translatable("base.viafabricplus.online_mode").withStyle(ChatFormatting.GREEN), 1, 1, -1);
            }
            final String serverIP = server.socket();
            final String playerText = server.playerCount() + "/" + server.playerLimit();

            context.text(textRenderer, Component.literal(serverIP).withStyle(ChatFormatting.DARK_GRAY), entryWidth - textRenderer.width(serverIP) - 1, entryHeight - textRenderer.lineHeight - 1, -1);
            context.text(textRenderer, playerText, entryWidth - textRenderer.width(playerText) - 1, 1, -1);
        }
    }

}
