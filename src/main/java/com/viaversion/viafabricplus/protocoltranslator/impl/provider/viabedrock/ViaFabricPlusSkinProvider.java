/*
 * This file is part of ViaFabricPlus - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viafabricplus.protocoltranslator.impl.provider.viabedrock;

import com.viaversion.viafabricplus.util.bedrock.BedrockSkinAppearance;
import com.viaversion.viafabricplus.util.bedrock.BedrockSkinBridge;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.raphimc.viabedrock.protocol.model.SkinData;
import net.raphimc.viabedrock.protocol.provider.SkinProvider;

/** Loaded with the isolated runtime so its overrides use that runtime's SkinData type. */
public final class ViaFabricPlusSkinProvider extends SkinProvider {

    @Override
    public Map<String, Object> getClientPlayerSkin(final UserConnection user) {
        final Map<String, Object> claims = super.getClientPlayerSkin(user);
        BedrockSkinBridge.applyClientPlayerSkin(user, claims);
        return claims;
    }

    public static BedrockSkinAppearance appearance(final SkinData skin) {
        final var animations = skin.animations() == null ? List.<BedrockSkinAppearance.Animation>of()
            : skin.animations().stream().map(animation -> new BedrockSkinAppearance.Animation(
                animation.image(), animation.type(), animation.frames(), animation.expression())).toList();
        return new BedrockSkinAppearance(skin.skinData(), skin.capeData(), skin.skinResourcePatch(),
            skin.geometryData(), skin.armSize(), skin.persona()
                || skin.personaPieces() != null && !skin.personaPieces().isEmpty(), skin.premium(), animations);
    }

    @Override
    public void setSkin(final UserConnection user, final UUID playerUuid, final SkinData skin) {
        BedrockSkinBridge.installAppearance(user, playerUuid, appearance(skin));
        super.setSkin(user, playerUuid, skin);
    }
}
