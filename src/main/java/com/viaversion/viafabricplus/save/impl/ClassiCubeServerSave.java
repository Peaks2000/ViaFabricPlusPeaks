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

package com.viaversion.viafabricplus.save.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.save.AbstractSave;
import java.util.ArrayList;
import java.util.List;

public final class ClassiCubeServerSave extends AbstractSave {

    private static final int MAX_SERVER_ADDRESS_LENGTH = 2_048;

    private final List<String> servers = new ArrayList<>();

    public ClassiCubeServerSave() {
        super("classicube_servers");
    }

    @Override
    public void write(JsonObject object) {
        final JsonArray array = new JsonArray();
        for (String server : servers) {
            array.add(server);
        }
        object.add("servers", array);
    }

    @Override
    public void read(JsonObject object) {
        servers.clear();
        if (object.has("servers") && object.get("servers").isJsonArray()) {
            object.getAsJsonArray("servers").forEach(element -> {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    addServer(element.getAsString(), false);
                }
            });
        }
    }

    public List<String> getServers() {
        return List.copyOf(servers);
    }

    public void addServer(final String address) {
        addServer(address, true);
    }

    public void removeServer(final String address) {
        if (servers.remove(address)) {
            save();
        }
    }

    private void addServer(final String address, final boolean persist) {
        if (address == null) {
            return;
        }
        final String normalizedAddress = address.trim();
        if (normalizedAddress.isEmpty() || normalizedAddress.length() > MAX_SERVER_ADDRESS_LENGTH || servers.contains(normalizedAddress)) {
            return;
        }
        servers.add(normalizedAddress);
        if (persist) {
            save();
        }
    }

}
