/*
 * This file is part of ViaFabricPlus - https://github.com/Peaks2000/ViaFabricPlusPeaks
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

package com.viaversion.viafabricplus.util.bedrock;

import java.util.Locale;

/**
 * Checks the platform-specific M152 WebRTC resource before JNI class
 * initialization. The upstream loader otherwise reports a misleading
 * {@link NullPointerException} when a classifier was omitted from the JAR.
 */
public final class WebRtcNativeLibrary {

    public static void ensureAvailable() {
        final String resourceName = resourceName(
            System.getProperty("os.name", ""),
            System.getProperty("os.arch", "")
        );
        if (WebRtcNativeLibrary.class.getClassLoader().getResource(resourceName) == null) {
            throw new IllegalStateException(
                "The ViaFabricPlus JAR does not contain the M152 WebRTC native for this platform: " + resourceName
            );
        }
    }

    static String resourceName(final String osName, final String osArch) {
        final String os = osName.toLowerCase(Locale.ROOT);
        final String family;
        final String extension;
        if (os.startsWith("mac os")) {
            family = "macos";
            extension = ".dylib";
        } else if (os.startsWith("linux")) {
            family = "linux";
            extension = ".so";
        } else if (os.startsWith("windows")) {
            family = "windows";
            extension = ".dll";
        } else {
            throw new IllegalStateException("Unsupported WebRTC operating system: " + osName);
        }

        final String architecture = switch (osArch.toLowerCase(Locale.ROOT)) {
            case "x86_64", "x86-64", "amd64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "aarch32", "arm" -> "aarch32";
            default -> throw new IllegalStateException("Unsupported WebRTC architecture: " + osArch);
        };
        final String prefix = "windows".equals(family) ? "" : "lib";
        return prefix + "webrtc-java-" + family + "-" + architecture + extension;
    }

    private WebRtcNativeLibrary() {
    }

}
