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

public final class BedrockRemotePlayerRespawn {

    private BedrockRemotePlayerRespawn() {
    }

    public static boolean shouldResetDeathTime(final boolean bedrockRoute, final boolean remotePlayer,
                                               final float health, final int deathTime) {
        return bedrockRoute && remotePlayer && health > 0.0F && deathTime > 0;
    }

}
