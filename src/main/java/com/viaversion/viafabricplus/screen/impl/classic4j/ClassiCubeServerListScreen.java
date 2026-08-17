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

package com.viaversion.viafabricplus.screen.impl.classic4j;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.injection.access.core.IEditBox;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.vialegacy.ViaFabricPlusClassicMPPassProvider;
import com.viaversion.viafabricplus.save.SaveManager;
import com.viaversion.viafabricplus.screen.VFPList;
import com.viaversion.viafabricplus.screen.VFPListEntry;
import com.viaversion.viafabricplus.screen.VFPScreen;
import com.viaversion.viafabricplus.settings.impl.ClassiCubeSettings;
import com.viaversion.viafabricplus.util.network.ConnectionUtil;
import de.florianreuth.classic4j.ClassiCubeHandler;
import de.florianreuth.classic4j.api.LoginProcessHandler;
import de.florianreuth.classic4j.model.classicube.account.CCAccount;
import de.florianreuth.classic4j.model.classicube.server.CCServerInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.jspecify.annotations.NonNull;

import static com.viaversion.viafabricplus.screen.VFPListEntry.SLOT_MARGIN;

public final class ClassiCubeServerListScreen extends VFPScreen {

    public static final ClassiCubeServerListScreen INSTANCE = new ClassiCubeServerListScreen();

    /**
     * Whether the current connection attempt was started from the ClassiCube server list.
     * It is set when clicking a server and consumed when the connection is started.
     */
    public static boolean connecting;

    private static List<CCServerInfo> SERVER_LIST;
    private static final String CLASSICUBE_SERVER_LIST_URL = "https://www.classicube.net/server/list/";
    private boolean reauthenticating;
    private EditBox searchField;
    private SlotList slotList;

    public ClassiCubeServerListScreen() {
        super("ClassiCube", true);
    }

    /**
     * The server list API only returns an mppass for each server when the account is logged in.
     * If the session expired, the servers are returned without an mppass, which would result in the servers
     * kicking the client with "Unknown host" when trying to join.
     */
    private boolean needsReauthentication() {
        return !SERVER_LIST.isEmpty() && SERVER_LIST.stream().noneMatch(server -> server.mpPass() != null && !server.mpPass().isEmpty());
    }

    private void reauthenticate(final CCAccount account) {
        if (reauthenticating) {
            return;
        }
        reauthenticating = true;

        ClassiCubeHandler.requestAuthentication(account, null, new LoginProcessHandler() {

            @Override
            public void handleMfa(CCAccount account) {
                reauthenticating = false;
                SERVER_LIST = null;
                ClassiCubeMFAScreen.INSTANCE.open(prevScreen);
            }

            @Override
            public void handleSuccessfulLogin(CCAccount account) {
                reauthenticating = false;
                SERVER_LIST = null;
                open(prevScreen);
            }

            @Override
            public void handleException(Throwable throwable) {
                reauthenticating = false;
                ViaFabricPlusImpl.INSTANCE.getLogger().error("Error while re-authenticating to ClassiCube!", throwable);
                showErrorScreen(INSTANCE.getTitle(), throwable, prevScreen);
            }
        });
    }

    @Override
    protected void init() {
        final CCAccount account = SaveManager.INSTANCE.getAccountsSave().getClassicubeAccount();
        if (SERVER_LIST == null) {
            ClassiCubeHandler.requestServerList(account, serverList -> {
                SERVER_LIST = new ArrayList<>(serverList.servers());
                if (needsReauthentication()) {
                    reauthenticate(account);
                    return;
                }
                open(prevScreen);
                setupUrlSubtitle(CLASSICUBE_SERVER_LIST_URL);
            }, throwable -> {
                ViaFabricPlusImpl.INSTANCE.getLogger().error("Error while loading ClassiCube servers!", throwable);
                showErrorScreen(INSTANCE.getTitle(), throwable, prevScreen);
            });
            setupSubtitle(Component.translatable("betacraft.viafabricplus.loading"));
            return;
        }

        final int entryHeight = (font.lineHeight + 2) * 3; // title is 2
        final int searchBarY = 2 * SLOT_MARGIN + entryHeight;

        this.addRenderableWidget(searchField = new EditBox(font, 5, searchBarY, width - 10, 20, Component.empty()));
        searchField.setHint(Component.translatable("base.viafabricplus.search"));
        searchField.setResponder(query -> updateSearch());
        ((IEditBox) searchField).viaFabricPlus$unlockForbiddenCharacters();

        this.addRenderableWidget(slotList = new SlotList(this.minecraft, width, height, searchBarY + 24, -5, entryHeight, normalizeQuery(searchField.getValue())));

        this.addRenderableWidget(Button.builder(Component.translatable("base.viafabricplus.logout"), button -> {
            SaveManager.INSTANCE.getAccountsSave().setClassicubeAccount(null);
            SERVER_LIST = null;
            onClose();
        }).pos(width - 60 - 5, 5).size(60, 20).build());

        super.init();
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

    private static boolean matchesQuery(final CCServerInfo server, final String query) {
        if (query.isEmpty()) {
            return true;
        }
        return server.name().toLowerCase(Locale.ROOT).contains(query)
            || server.software().toLowerCase(Locale.ROOT).contains(query)
            || server.ip().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        if (SERVER_LIST == null) {
            return;
        }

        final CCAccount account = SaveManager.INSTANCE.getAccountsSave().getClassicubeAccount();
        graphics.text(font, Component.translatable("classicube.viafabricplus.profile"), 32, 6, -1);
        graphics.text(font, Component.nullToEmpty(account.username()), 32, 16, -1);
    }

    @Override
    protected boolean subtitleCentered() {
        return SERVER_LIST == null;
    }

    public static class SlotList extends VFPList {
        private static double scrollAmount;

        public SlotList(Minecraft minecraftClient, int width, int height, int top, int bottom, int entryHeight, String query) {
            super(minecraftClient, width, height, top, bottom, entryHeight);

            SERVER_LIST.stream()
                .filter(serverInfo -> matchesQuery(serverInfo, query))
                .forEach(serverInfo -> this.addEntry(new ServerSlot(serverInfo)));
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
        private final CCServerInfo classiCubeServerInfo;

        public ServerSlot(CCServerInfo classiCubeServerInfo) {
            this.classiCubeServerInfo = classiCubeServerInfo;
        }

        @Override
        public Component getNarration() {
            return Component.nullToEmpty(classiCubeServerInfo.name());
        }

        @Override
        public void mappedMouseClicked(double mouseX, double mouseY, int button) {
            connecting = true;
            final boolean selectCPE = ClassiCubeSettings.INSTANCE.automaticallySelectCPEInClassiCubeServerList.getValue();
            ViaFabricPlusClassicMPPassProvider.classicubeMPPass = classiCubeServerInfo.mpPass();

            ConnectionUtil.connect(classiCubeServerInfo.name(), classiCubeServerInfo.ip() + ":" + classiCubeServerInfo.port(), selectCPE ? LegacyProtocolVersion.c0_30cpe : null);
        }

        @Override
        public void mappedRender(GuiGraphicsExtractor context, int x, int y, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            final Font textRenderer = Minecraft.getInstance().font;
            context.centeredText(textRenderer, classiCubeServerInfo.name(), entryWidth / 2, entryHeight / 2 - textRenderer.lineHeight / 2, -1);

            context.text(textRenderer, classiCubeServerInfo.software().replace('&', ChatFormatting.PREFIX_CODE), 1, 1, -1);
            final String playerText = classiCubeServerInfo.players() + "/" + classiCubeServerInfo.maxPlayers();
            context.text(textRenderer, playerText, entryWidth - textRenderer.width(playerText) - 1, 1, -1);
        }
    }

}
