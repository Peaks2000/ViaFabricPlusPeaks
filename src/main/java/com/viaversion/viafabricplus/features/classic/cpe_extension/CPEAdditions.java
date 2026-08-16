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

package com.viaversion.viafabricplus.features.classic.cpe_extension;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.lenni0451.reflect.Enums;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.data.ClassicProtocolExtension;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.data.ExtendedClassicBlocks;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.packet.ClientboundPacketsc0_30cpe;

public final class CPEAdditions {

    public final static List<ClassicProtocolExtension> ALLOWED_EXTENSIONS = new ArrayList<>();
    public final static Map<Integer, ClientboundPacketsc0_30cpe> CUSTOM_PACKETS = new HashMap<>();
    public static final List<Item> EXTENDED_CLASSIC_ITEMS = new ArrayList<>();
    public static final Set<Integer> CLIMBABLE_BLOCKS = ConcurrentHashMap.newKeySet();

    public static final int COLLIDE_CLIMB = 5;
    public static final int COLLIDE_DEVCLIMB = 7;

    public static ClientboundPacketsc0_30cpe EXT_WEATHER_TYPE;
    public static ClientboundPacketsc0_30cpe EXT_BLOCK_DEFINITIONS;
    public static ClientboundPacketsc0_30cpe EXT_BLOCK_DEFINITIONS_EXT;
    public static ClientboundPacketsc0_30cpe EXT_UNDEFINE_BLOCK;

    private static boolean snowing = false;

    public static void init() {
        EXTENDED_CLASSIC_ITEMS.add(Items.COBBLESTONE_SLAB);
        EXTENDED_CLASSIC_ITEMS.add(Items.DEAD_BUSH);
        EXTENDED_CLASSIC_ITEMS.add(Items.SANDSTONE);
        EXTENDED_CLASSIC_ITEMS.add(Items.SNOW);
        EXTENDED_CLASSIC_ITEMS.add(Items.TORCH);
        EXTENDED_CLASSIC_ITEMS.add(Items.WOOL.brown());
        EXTENDED_CLASSIC_ITEMS.add(Items.ICE);
        EXTENDED_CLASSIC_ITEMS.add(Items.CHISELED_QUARTZ_BLOCK);
        EXTENDED_CLASSIC_ITEMS.add(Items.NETHER_QUARTZ_ORE);
        EXTENDED_CLASSIC_ITEMS.add(Items.QUARTZ_PILLAR);
        EXTENDED_CLASSIC_ITEMS.add(Items.JUKEBOX);
        EXTENDED_CLASSIC_ITEMS.add(Items.STONE_BRICKS);

        allowExtension(ClassicProtocolExtension.ENV_WEATHER_TYPE);
        EXT_WEATHER_TYPE = createNewPacket(ClassicProtocolExtension.ENV_WEATHER_TYPE, 31, (user, buf) -> buf.readByte());

        allowExtension(ClassicProtocolExtension.BLOCK_DEFINITIONS);
        allowExtension(ClassicProtocolExtension.BLOCK_DEFINITIONS_EXT);
        EXT_BLOCK_DEFINITIONS = createNewPacket(ClassicProtocolExtension.BLOCK_DEFINITIONS, 35, (user, buf) -> buf.skipBytes(79));
        EXT_UNDEFINE_BLOCK = createNewPacket(ClassicProtocolExtension.BLOCK_DEFINITIONS, 36, (user, buf) -> buf.skipBytes(1));
        EXT_BLOCK_DEFINITIONS_EXT = createNewPacket(ClassicProtocolExtension.BLOCK_DEFINITIONS_EXT, 37, (user, buf) -> buf.skipBytes(84));

        resetBlockDefinitions();
    }

    public static boolean isClimbableBlock(final int blockId) {
        return CLIMBABLE_BLOCKS.contains(blockId);
    }

    public static void handleBlockDefinition(final int blockId, final byte collideType) {
        if (collideType == COLLIDE_CLIMB || collideType == COLLIDE_DEVCLIMB) {
            CLIMBABLE_BLOCKS.add(blockId);
        } else {
            CLIMBABLE_BLOCKS.remove(blockId);
        }
    }

    public static void removeBlockDefinition(final int blockId) {
        CLIMBABLE_BLOCKS.remove(blockId);
    }

    public static void resetBlockDefinitions() {
        CLIMBABLE_BLOCKS.clear();
        CLIMBABLE_BLOCKS.add(ExtendedClassicBlocks.ROPE);
    }

    public static boolean isSnowing() {
        return ProtocolTranslator.getTargetVersion().equals(LegacyProtocolVersion.c0_30cpe) && snowing;
    }

    public static void setSnowing(boolean snowing) {
        CPEAdditions.snowing = snowing;
    }

    public static void allowExtension(final ClassicProtocolExtension classicProtocolExtension) {
        ALLOWED_EXTENSIONS.add(classicProtocolExtension);
    }

    public static ClientboundPacketsc0_30cpe createNewPacket(final ClassicProtocolExtension classicProtocolExtension, final int packetId, final BiConsumer<UserConnection, ByteBuf> packetSplitter) {
        final ClientboundPacketsc0_30cpe packet = Enums.newInstance(ClientboundPacketsc0_30cpe.class, classicProtocolExtension.getName(), ClassicProtocolExtension.values().length, new Class[]{int.class, BiConsumer.class}, new Object[]{packetId, packetSplitter});
        Enums.addEnumInstance(ClientboundPacketsc0_30cpe.class, packet);
        CUSTOM_PACKETS.put(packetId, packet);

        return packet;
    }

}
