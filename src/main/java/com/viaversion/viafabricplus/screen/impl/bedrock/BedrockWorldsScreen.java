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

package com.viaversion.viafabricplus.screen.impl.bedrock;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.save.SaveManager;
import com.viaversion.viafabricplus.screen.VFPList;
import com.viaversion.viafabricplus.screen.VFPListEntry;
import com.viaversion.viafabricplus.screen.VFPScreen;
import com.viaversion.viafabricplus.util.bedrock.BedrockProtocolCompatibility;
import com.viaversion.viafabricplus.util.bedrock.BedrockWorld;
import com.viaversion.viafabricplus.util.bedrock.BedrockWorldDiscovery;
import com.viaversion.viafabricplus.util.bedrock.NetherNetJsonRpcAddress;
import com.viaversion.viafabricplus.util.network.ConnectionUtil;
import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayStatus;
import org.apache.logging.log4j.Level;
import org.jspecify.annotations.NonNull;

public final class BedrockWorldsScreen extends VFPScreen {

    public static final BedrockWorldsScreen INSTANCE = new BedrockWorldsScreen();

    private List<BedrockWorld> worlds;
    private boolean loading;
    private volatile boolean loadFailed;

    private SlotList slotList;
    private Button joinButton;

    private final Set<Integer> attemptedProtocols = new HashSet<>();
    private BedrockWorld connectingWorld;
    private int connectingProtocol = BedrockProtocolCompatibility.UNKNOWN_PROTOCOL;

    public BedrockWorldsScreen() {
        super(Component.translatable("screen.viafabricplus.bedrock_worlds"), true);
    }

    @Override
    protected void init() {
        super.init();
        if (this.worlds == null) {
            this.setupSubtitle(Component.translatable("bedrock_worlds.viafabricplus.loading"));
            if (!this.loading) {
                this.loadWorlds();
            }
            return;
        }
        this.createView();
    }

    private void loadWorlds() {
        this.loading = true;
        this.loadFailed = false;
        final BedrockAuthManager account = SaveManager.INSTANCE.getAccountsSave().getBedrockAccount();
        final CompletableFuture<List<BedrockWorld>> lanWorlds = CompletableFuture.supplyAsync(() -> {
            try {
                return BedrockWorldDiscovery.discoverLanWorlds();
            } catch (final Throwable throwable) {
                return this.discoveryError("Failed to discover Bedrock LAN worlds", throwable);
            }
        }, Util.nonCriticalIoPool());
        final CompletableFuture<List<BedrockWorld>> friendWorlds = account == null
            ? CompletableFuture.completedFuture(List.of())
            : CompletableFuture.supplyAsync(() -> {
                try {
                    return BedrockWorldDiscovery.discoverXboxFriends(account);
                } catch (final Throwable throwable) {
                    return this.discoveryError("Failed to load Xbox friend worlds", throwable);
                }
            }, Util.nonCriticalIoPool());

        lanWorlds.thenCombine(friendWorlds, (lan, friends) -> {
            final List<BedrockWorld> result = new ArrayList<>(friends.size() + lan.size());
            result.addAll(friends);
            result.addAll(lan);
            result.sort(Comparator.comparing(BedrockWorld::source).thenComparing(BedrockWorld::name, String.CASE_INSENSITIVE_ORDER));
            return result;
        }).thenAccept(result -> Minecraft.getInstance().execute(() -> {
            this.worlds = result;
            this.loading = false;
            if (Minecraft.getInstance().gui.screen() == this) {
                Minecraft.getInstance().gui.setScreen(this);
            }
        }));
    }

    private List<BedrockWorld> discoveryError(final String message, final Throwable throwable) {
        this.loadFailed = true;
        ViaFabricPlusImpl.INSTANCE.getLogger().log(Level.ERROR, message, throwable);
        return List.of();
    }

    private void createView() {
        final boolean missingAccount = SaveManager.INSTANCE.getAccountsSave().getBedrockAccount() == null;
        if (!this.worlds.isEmpty()) {
            if (missingAccount) {
                this.setupSubtitle(Component.translatable("bedrock_worlds.viafabricplus.account_warning"));
            } else {
                this.setupDefaultSubtitle();
            }
        } else if (this.loadFailed) {
            this.setupSubtitle(Component.translatable("bedrock_worlds.viafabricplus.error"));
        } else if (missingAccount) {
            this.setupSubtitle(Component.translatable("bedrock_worlds.viafabricplus.no_worlds_account"));
        } else {
            this.setupSubtitle(Component.translatable("bedrock_worlds.viafabricplus.no_worlds"));
        }

        this.addRenderableWidget(this.slotList = new SlotList(this.minecraft, this.width, this.height, 3 + 3 + (this.font.lineHeight + 2) * 3, 30, (this.font.lineHeight + 2) * 4));
        this.addRefreshButton(() -> {
            this.worlds = null;
            this.loading = false;
        });
        this.addRenderableWidget(this.joinButton = Button.builder(Component.translatable("bedrock_worlds.viafabricplus.join"), _ -> this.joinSelected())
            .pos(this.width / 2 - 100, this.height - 25)
            .size(200, 20)
            .build());
        this.joinButton.active = false;
    }

    private void joinSelected() {
        if (!(this.slotList.getFocused() instanceof SlotEntry entry)) {
            return;
        }
        final BedrockWorld world = entry.world;
        final BedrockAuthManager account = SaveManager.INSTANCE.getAccountsSave().getBedrockAccount();
        if (world.source() == BedrockWorld.Source.XBOX_FRIEND && account != null && world.connection().xboxSessionName() != null) {
            this.joinButton.active = false;
            this.setupSubtitle(Component.literal("Joining Xbox multiplayer session..."));
            CompletableFuture.runAsync(() -> {
                try {
                    BedrockWorldDiscovery.joinXboxSession(account, world.connection().xboxSessionName());
                } catch (final IOException | InterruptedException throwable) {
                    throw new CompletionException(throwable);
                }
            }, Util.nonCriticalIoPool()).whenComplete((_, throwable) -> Minecraft.getInstance().execute(() -> {
                if (throwable != null) {
                    ViaFabricPlusImpl.INSTANCE.getLogger().error("Failed to join Xbox multiplayer session", throwable);
                    this.setupSubtitle(Component.literal("Could not join the Xbox multiplayer session"));
                    this.joinButton.active = true;
                } else {
                    ViaFabricPlusImpl.INSTANCE.getLogger().info("Joined Xbox multiplayer session; starting game connection");
                    this.connect(world);
                }
            }));
            return;
        }
        this.connect(world);
    }

    private void connect(final BedrockWorld world) {
        final int protocolVersion = BedrockProtocolCompatibility.initialProtocol(world.protocolVersion());
        synchronized (this.attemptedProtocols) {
            this.connectingWorld = world;
            this.connectingProtocol = protocolVersion;
            this.attemptedProtocols.clear();
            this.attemptedProtocols.add(protocolVersion);
        }
        this.connect(world, protocolVersion);
    }

    private void connect(final BedrockWorld world, final int protocolVersion) {
        BedrockProtocolCompatibility.prepareConnection(protocolVersion);
        ViaFabricPlusImpl.INSTANCE.getLogger().info("Connecting to Bedrock world '{}' with wire protocol {}", world.name(), protocolVersion);
        final BedrockWorld.Connection connection = world.connection();
        switch (connection.type()) {
            case RAKNET -> ConnectionUtil.connect(world.name(), connection.address(), BedrockProtocolVersion.bedrockLatest);
            case NETHERNET -> ConnectionUtil.connectNetherNet(world.name(), new NetherNetAddress(connection.address()));
            case NETHERNET_JSON_RPC -> ConnectionUtil.connectNetherNet(world.name(), new NetherNetJsonRpcAddress(connection.address(), connection.signalingId()));
            case NETHERNET_DISCOVERY -> ConnectionUtil.connectNetherNet(world.name(), connection.discoveryAddress());
        }
    }

    public static boolean retryVersionMismatch(final PlayStatus status) {
        final boolean serverIsNewer;
        if (status == PlayStatus.LoginFailed_ClientOld) {
            serverIsNewer = true;
        } else if (status == PlayStatus.LoginFailed_ServerOld) {
            serverIsNewer = false;
        } else {
            return false;
        }
        return INSTANCE.scheduleProtocolRetry(serverIsNewer);
    }

    private boolean scheduleProtocolRetry(final boolean serverIsNewer) {
        final BedrockWorld world;
        final int previousProtocol;
        final int nextProtocol;
        synchronized (this.attemptedProtocols) {
            world = this.connectingWorld;
            previousProtocol = this.connectingProtocol;
            nextProtocol = BedrockProtocolCompatibility.adjacentProtocol(previousProtocol, serverIsNewer);
            if (world == null || nextProtocol == BedrockProtocolCompatibility.UNKNOWN_PROTOCOL || !this.attemptedProtocols.add(nextProtocol)) {
                return false;
            }
            this.connectingProtocol = nextProtocol;
        }

        ViaFabricPlusImpl.INSTANCE.getLogger().info(
            "Bedrock host rejected protocol {} as {}; retrying '{}' with protocol {}",
            previousProtocol, serverIsNewer ? "client-old" : "server-old", world.name(), nextProtocol
        );
        // Let the rejected connection finish closing before replacing its
        // ConnectScreen. Otherwise its disconnect callback can race and cover
        // the retry screen with the old version error.
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS).execute(
            () -> Minecraft.getInstance().execute(() -> this.connect(world, nextProtocol))
        );
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.slotList != null && this.joinButton != null) {
            this.joinButton.active = this.slotList.getFocused() instanceof SlotEntry;
        }
    }

    @Override
    protected boolean subtitleCentered() {
        return this.worlds == null;
    }

    public final class SlotList extends VFPList {

        private static double scrollAmount;

        public SlotList(final Minecraft minecraft, final int width, final int height, final int top, final int bottom, final int entryHeight) {
            super(minecraft, width, height, top, bottom, entryHeight);
            for (final BedrockWorld world : BedrockWorldsScreen.this.worlds) {
                this.addEntry(new SlotEntry(this, world));
            }
            this.initScrollY(scrollAmount);
        }

        @Override
        protected void updateSlotAmount(final double amount) {
            scrollAmount = amount;
        }

        @Override
        public int getRowWidth() {
            return super.getRowWidth() + 140;
        }
    }

    public final class SlotEntry extends VFPListEntry {

        private final SlotList slotList;
        private final BedrockWorld world;

        public SlotEntry(final SlotList slotList, final BedrockWorld world) {
            this.slotList = slotList;
            this.world = world;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.nullToEmpty(this.world.name());
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor context, final int x, final int y, final int entryWidth, final int entryHeight, final int mouseX, final int mouseY, final boolean hovered, final float tickDelta) {
            final Font font = Minecraft.getInstance().font;
            final String source = Component.translatable(this.world.source() == BedrockWorld.Source.LAN
                ? "bedrock_worlds.viafabricplus.source_lan"
                : "bedrock_worlds.viafabricplus.source_xbox").getString();
            final String owner = this.world.owner().isBlank() ? "" : this.world.owner() + " - ";
            context.text(font, owner + this.world.name(), 3, 3, this.slotList.getFocused() == this ? Color.ORANGE.getRGB() : -1);
            context.text(font, source, entryWidth - font.width(source) - 3, 3, -1);

            final String players = this.world.playerCount() >= 0
                ? this.world.playerCount() + (this.world.maxPlayerCount() >= 0 ? "/" + this.world.maxPlayerCount() : "") + " players"
                : "";
            final String details = List.of(this.world.gameMode(), this.world.version(), players).stream()
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " - " + right)
                .orElse("");
            this.renderScrollableText(Component.nullToEmpty(details), entryHeight - font.lineHeight - 3, 0);
        }
    }

}
