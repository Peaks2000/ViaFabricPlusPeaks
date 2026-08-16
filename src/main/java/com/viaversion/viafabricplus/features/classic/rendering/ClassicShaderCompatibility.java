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

package com.viaversion.viafabricplus.features.classic.rendering;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;

public final class ClassicShaderCompatibility {

    private static final String IRIS_CLASS = "net.irisshaders.iris.Iris";

    private static Method irisCurrentDimension;
    private static Field irisLastDimension;
    private static boolean irisUnavailable;

    public static boolean shouldPreservePipeline(final ProtocolVersion targetVersion, final boolean dimensionChanged) {
        return dimensionChanged && (targetVersion.equals(LegacyProtocolVersion.c0_30cpe)
                || targetVersion.olderThanOrEqualTo(LegacyProtocolVersion.c0_28toc0_30));
    }

    public static boolean preservePipelineForSyntheticDimensionChange() {
        if (!shouldPreservePipeline(ProtocolTranslator.getTargetVersion(), true) || irisUnavailable) {
            return false;
        }

        try {
            if (irisCurrentDimension == null || irisLastDimension == null) {
                final Class<?> irisClass = Class.forName(IRIS_CLASS);
                irisCurrentDimension = irisClass.getMethod("getCurrentDimension");
                irisLastDimension = irisClass.getField("lastDimension");
            }
            irisLastDimension.set(null, irisCurrentDimension.invoke(null));
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            irisUnavailable = true;
            ViaFabricPlusImpl.INSTANCE.getLogger().warn("Could not preserve the Iris pipeline during a synthetic Classic world switch", e);
            return false;
        }
    }

    private ClassicShaderCompatibility() {
    }
}
