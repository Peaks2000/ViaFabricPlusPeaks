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
        if (object.has("servers")) {
            object.getAsJsonArray("servers").forEach(element -> servers.add(element.getAsString()));
        }
    }

    public List<String> getServers() {
        return servers;
    }

    public void addServer(final String address) {
        if (!servers.contains(address)) {
            servers.add(address);
            save();
        }
    }

    public void removeServer(final String address) {
        if (servers.remove(address)) {
            save();
        }
    }

}