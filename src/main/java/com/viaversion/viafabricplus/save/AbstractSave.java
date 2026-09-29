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

package com.viaversion.viafabricplus.save;

import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.util.JsonSave;
import java.nio.file.Path;

public abstract class AbstractSave {
    private final Path path;

    protected AbstractSave(final String name) {
        this.path = ViaFabricPlusImpl.impl().path().resolve(name + ".json");
    }

    public void init() { JsonSave.read(this.path, this::read); }
    public void save() {
        JsonSave.write(this.path, () -> {
            final JsonObject object = new JsonObject();
            this.write(object);
            return object;
        });
    }
    public abstract void read(final JsonObject object);
    public abstract void write(final JsonObject object);
    public void postInit() { }
    public Path getPath() { return this.path; }
}
