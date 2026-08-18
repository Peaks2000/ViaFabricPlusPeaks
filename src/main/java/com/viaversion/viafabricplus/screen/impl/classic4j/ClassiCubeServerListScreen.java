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
import net.minecraft.client.input.MouseButtonEvent;
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

    /**
     * The play link or IP which was used to start a direct connection from the play link field.
     * It is only set when joining from the play link field and consumed when the connection
     * was established successfully, so the server gets added to the personal server list.
     */
    public static String pendingServerAddress;

    private static List<CCServerInfo> SERVER_LIST;
    private static final String CLASSICUBE_SERVER_LIST_URL = "https://www.classicube.net/server/list/";
    private boolean reauthenticating;
    private boolean showMyServers;
    private EditBox searchField;
    private EditBox playLinkField;
    private Button joinButton;
    private VFPList slotList;

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
        // Reset to the featured servers whenever the screen is opened again,
        // e.g. when leaving the ClassiCube menu or when returning after leaving a server.
        this.showMyServers = false;

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

        createView();
    }

    private void createView() {
        final int entryHeight = (font.lineHeight + 2) * 3; // title is 2
        final int searchBarY = 2 * SLOT_MARGIN + entryHeight;
        final int linkBarY = searchBarY + 24;
        final int listTop = linkBarY + 48; // Below the "My servers" toggle button

        if (showMyServers) {
            this.setupSubtitle(Component.translatable("classicube.viafabricplus.my_servers_hint"));
        } else {
            this.setupUrlSubtitle(CLASSICUBE_SERVER_LIST_URL);
        }

        this.addRenderableWidget(searchField = new EditBox(font, 5, searchBarY, width - 10, 20, Component.empty()));
        searchField.setHint(Component.translatable("base.viafabricplus.search"));
        searchField.setResponder(query -> updateSearch());
        ((IEditBox) searchField).viaFabricPlus$unlockForbiddenCharacters();

        this.addRenderableWidget(playLinkField = new EditBox(font, 5, linkBarY, width - 70, 20, Component.empty()));
        playLinkField.setMaxLength(Integer.MAX_VALUE);
        playLinkField.setHint(Component.translatable("classicube.viafabricplus.play_link_hint"));
        ((IEditBox) playLinkField).viaFabricPlus$unlockForbiddenCharacters();
        this.addRenderableWidget(joinButton = Button.builder(Component.translatable("classicube.viafabricplus.join_by_link"), button -> joinByPlayLink()).pos(width - 60, linkBarY).size(55, 20).build());
        joinButton.active = false;
        playLinkField.setResponder(text -> joinButton.active = !text.trim().isEmpty());

        this.addRenderableWidget(Button.builder(Component.translatable(showMyServers ? "classicube.viafabricplus.featured_servers" : "classicube.viafabricplus.my_servers"), button -> switchMode()).pos(width / 2 - 75, linkBarY + 24).size(150, 20).build());

        this.addRenderableWidget(slotList = showMyServers
            ? new SavedServersSlotList(this.minecraft, width, height, listTop, -5, entryHeight)
            : new SlotList(this.minecraft, width, height, listTop, -5, entryHeight, normalizeQuery(searchField.getValue())));

        this.addRenderableWidget(Button.builder(Component.translatable("base.viafabricplus.logout"), button -> {
            SaveManager.INSTANCE.getAccountsSave().setClassicubeAccount(null);
            SERVER_LIST = null;
            onClose();
        }).pos(width - 60 - 5, 5).size(60, 20).build());

        super.init();
    }

    private void switchMode() {
        showMyServers = !showMyServers;
        this.clearWidgets();
        createView();
    }

    private void updateSearch() {
        if (slotList == null) {
            return;
        }
        removeWidget(slotList);
        final int entryHeight = (font.lineHeight + 2) * 3;
        final int listTop = 2 * SLOT_MARGIN + entryHeight + 72;
        addRenderableWidget(slotList = new SlotList(this.minecraft, width, height, listTop, -5, entryHeight, normalizeQuery(searchField.getValue())));
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

    private static String extractHash(final String input) {
        final String trimmed = input.trim();
        final int index = trimmed.lastIndexOf("/play/");
        final String hash = index != -1 ? trimmed.substring(index + "/play/".length()) : trimmed;
        return hash.replace("/", "").trim();
    }

    private void joinByPlayLink() {
        final String input = playLinkField.getValue().trim();
        if (input.isEmpty()) {
            return;
        }

        // Only direct connects from the "My servers" tab add the address to the personal server list
        pendingServerAddress = showMyServers ? input : null;
        if (input.contains("/play/") || input.matches("[0-9a-fA-F]{32}")) {
            joinClassiCubeServer(extractHash(input));
        } else {
            joinDirectly(input);
        }
    }

    private void joinSavedServer(final String address) {
        pendingServerAddress = null; // Already part of the server list, no need to add it again
        if (address.contains("/play/") || address.matches("[0-9a-fA-F]{32}")) {
            joinClassiCubeServer(extractHash(address));
        } else {
            joinDirectly(address);
        }
    }

    private void deleteSavedServer(final String address) {
        SaveManager.INSTANCE.getClassiCubeServerSave().removeServer(address);
        removeWidget(slotList);
        final int entryHeight = (font.lineHeight + 2) * 3;
        final int listTop = 2 * SLOT_MARGIN + entryHeight + 72;
        addRenderableWidget(slotList = new SavedServersSlotList(this.minecraft, width, height, listTop, -5, entryHeight));
    }

    private void joinClassiCubeServer(final String hash) {
        final CCAccount account = SaveManager.INSTANCE.getAccountsSave().getClassicubeAccount();
        if (account == null) {
            return;
        }
        ClassiCubeHandler.requestServerInfo(account, List.of(hash), serverList -> {
            if (serverList.servers().isEmpty()) {
                showErrorScreen(getTitle(), new IllegalStateException("The play link returned no servers"), prevScreen);
                return;
            }
            final CCServerInfo server = serverList.servers().get(0);
            connecting = true;
            final boolean selectCPE = ClassiCubeSettings.INSTANCE.automaticallySelectCPEInClassiCubeServerList.getValue();
            ViaFabricPlusClassicMPPassProvider.classicubeMPPass = server.mpPass();

            ConnectionUtil.connect(server.name(), server.ip() + ":" + server.port(), selectCPE ? LegacyProtocolVersion.c0_30cpe : null);
        }, throwable -> showErrorScreen(getTitle(), throwable, prevScreen));
    }

    private void joinDirectly(final String address) {
        connecting = true;
        final boolean selectCPE = ClassiCubeSettings.INSTANCE.automaticallySelectCPEInClassiCubeServerList.getValue();
        ConnectionUtil.connect(address, address, selectCPE ? LegacyProtocolVersion.c0_30cpe : null);
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
            pendingServerAddress = null; // Only the play link field adds servers to the personal server list
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

    public static class SavedServersSlotList extends VFPList {
        private static double scrollAmount;

        public SavedServersSlotList(Minecraft minecraftClient, int width, int height, int top, int bottom, int entryHeight) {
            super(minecraftClient, width, height, top, bottom, entryHeight);

            SaveManager.INSTANCE.getClassiCubeServerSave().getServers().forEach(address -> this.addEntry(new SavedServerSlot(address)));
            initScrollY(scrollAmount);
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

    public static class SavedServerSlot extends VFPListEntry {
        private final String address;

        public SavedServerSlot(String address) {
            this.address = address;
        }

        @Override
        public Component getNarration() {
            return Component.nullToEmpty(address);
        }

        @Override
        public boolean mouseClicked(final MouseButtonEvent click, final boolean doubled) {
            if (click.hasShiftDown()) {
                INSTANCE.deleteSavedServer(address);
            } else {
                INSTANCE.joinSavedServer(address);
            }
            return super.mouseClicked(click, doubled);
        }

        @Override
        public void mappedMouseClicked(double mouseX, double mouseY, int button) {
            // The action is handled in mouseClicked so that shift-clicking deletes the server
        }

        @Override
        public void mappedRender(GuiGraphicsExtractor context, int x, int y, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            renderScrollableText(Component.literal(address), 1);
            renderTooltip(Component.translatable("classicube.viafabricplus.delete_hint"), mouseX, mouseY);
        }
    }

}
