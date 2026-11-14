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
package net.raphimc.viabedrock.protocol.rewriter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FormTextTest {

    @Test void selectorLabelsKeepNamesAndCountsWithoutLayoutMarkers() {
        assertEquals("SkyWars · 1.5K", FormText.selectorButton("````SkyWars&selector_featured````1.5K"));
        assertEquals("BedWars", FormText.selectorButton("````BedWars&selector_favourite_empty`"));
        assertEquals("Add favourite", FormText.selectorButton("&selector_favourite_empty"));
        assertEquals("Remove favourite", FormText.selectorButton("&selector_favourite_selected"));
    }

    @Test void selectorTitleKeepsVisibleText() {
        assertEquals("Game Selector", FormText.selectorTitle("&game_selector"));
        assertEquals("Favourite", FormText.selectorTitle("Favourite"));
    }
}
