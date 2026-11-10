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

package com.viaversion.viafabricplus.util.bedrock;

import java.awt.image.BufferedImage;
import net.raphimc.viabedrock.protocol.model.SkinData;

/** Renderer payload shared by both runtimes without crossing their SkinData class identities. */
public record BedrockSkinAppearance(BufferedImage skinData, BufferedImage capeData, String skinResourcePatch,
                                    String geometryData, String armSize, boolean persona) {

    public static BedrockSkinAppearance from(final SkinData skin) {
        return new BedrockSkinAppearance(skin.skinData(), skin.capeData(), skin.skinResourcePatch(),
            skin.geometryData(), skin.armSize(), skin.persona()
                || skin.personaPieces() != null && !skin.personaPieces().isEmpty());
    }
}
