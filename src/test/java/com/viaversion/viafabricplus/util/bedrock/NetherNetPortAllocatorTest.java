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

import dev.kastle.webrtc.PortAllocatorConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class NetherNetPortAllocatorTest {

    @Test
    public void keepsWifiEligibleOnMultiHomedDesktopClients() {
        final PortAllocatorConfig config = new PortAllocatorConfig().setDisableCostlyNetworks(true);
        assertTrue(config.isCostlyNetworksDisabled());

        assertSame(config, NetherNetPortAllocator.includeAllLocalAdapters(config));
        assertFalse(config.isCostlyNetworksDisabled());
    }

}
