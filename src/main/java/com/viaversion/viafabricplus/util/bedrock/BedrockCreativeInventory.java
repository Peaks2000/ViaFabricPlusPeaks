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

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

public final class BedrockCreativeInventory {

    private BedrockCreativeInventory() {
    }

    public static boolean shouldRestoreRejectedCursor(final ProtocolVersion targetVersion, final int containerId,
                                                       final boolean carriedItemEmpty, final boolean creativeScreenOpen) {
        return BedrockProtocolVersion.bedrockLatest.equals(targetVersion)
                && containerId == 0
                && !carriedItemEmpty
                && creativeScreenOpen;
    }

}
