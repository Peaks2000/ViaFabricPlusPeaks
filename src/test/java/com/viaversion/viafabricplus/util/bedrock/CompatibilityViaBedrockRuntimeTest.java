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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public final class CompatibilityViaBedrockRuntimeTest {

    @Test
    public void ordinaryServersUseCurrentWireSemanticsThroughAnIsolatedRoute() {
        assertEquals(1001, CompatibilityViaBedrockRuntime.routeProtocolVersion());
        assertEquals(2168, CompatibilityViaBedrockRuntime.wireProtocolVersion());
        assertNotEquals(CompatibilityViaBedrockRuntime.routeProtocolVersion(), CompatibilityViaBedrockRuntime.wireProtocolVersion());
    }

}
