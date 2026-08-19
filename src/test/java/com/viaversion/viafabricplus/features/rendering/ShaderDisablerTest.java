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

package com.viaversion.viafabricplus.features.rendering;

import com.viaversion.viafabricplus.features.rendering.ShaderDisabler.ConnectionType;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ShaderDisablerTest {

    @Test
    public void shaderRouteMarkerBelongsToTheConnectionAndSurvivesRetryCopies() {
        SharedConstants.tryDetectVersion();

        final ServerData source = new ServerData("ClassiCube", "classic.example", ServerData.Type.OTHER);
        final ServerData retry = new ServerData("Retry", "retry.example", ServerData.Type.OTHER);

        assertEquals(ConnectionType.NONE, ((IServerData) source).viaFabricPlus$shaderConnectionType());
        ((IServerData) source).viaFabricPlus$setShaderConnectionType(ConnectionType.CLASSICUBE);
        retry.copyNameIconFrom(source);

        assertEquals(ConnectionType.CLASSICUBE, ((IServerData) source).viaFabricPlus$shaderConnectionType());
        assertEquals(ConnectionType.CLASSICUBE, ((IServerData) retry).viaFabricPlus$shaderConnectionType());
    }
}
