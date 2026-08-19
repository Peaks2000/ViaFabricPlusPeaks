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

package com.viaversion.viafabricplus.screen.impl.classic4j;

import com.viaversion.viafabricplus.injection.access.core.IServerData;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ClassiCubeServerListScreenTest {

    @Test
    public void savedServerSearchStaysScopedAndCaseInsensitive() {
        assertTrue(ClassiCubeServerListSupport.matchesSavedQuery("Play.Example.test:25565", " example "));
        assertTrue(ClassiCubeServerListSupport.matchesSavedQuery("play.example.test:25565", ""));
        assertFalse(ClassiCubeServerListSupport.matchesSavedQuery("play.example.test:25565", "other"));
    }

    @Test
    public void pendingSaveAddressBelongsToTheConnectionAndSurvivesRetryCopies() {
        SharedConstants.tryDetectVersion();

        final ServerData attempt = new ServerData("ClassiCube", "classic.example", ServerData.Type.OTHER);
        final ServerData retry = new ServerData("Retry", "retry.example", ServerData.Type.OTHER);
        final ServerData unrelated = new ServerData("Unrelated", "unrelated.example", ServerData.Type.OTHER);
        ((IServerData) attempt).viaFabricPlus$setClassiCubeSaveAddress("https://www.classicube.net/server/play/0123456789abcdef0123456789abcdef");

        retry.copyNameIconFrom(attempt);

        assertEquals("https://www.classicube.net/server/play/0123456789abcdef0123456789abcdef",
            ((IServerData) retry).viaFabricPlus$classiCubeSaveAddress());
        assertNull(((IServerData) unrelated).viaFabricPlus$classiCubeSaveAddress());
    }
}
