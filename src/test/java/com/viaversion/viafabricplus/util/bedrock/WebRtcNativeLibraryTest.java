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

import dev.kastle.webrtc.PeerConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class WebRtcNativeLibraryTest {

    @Test
    public void mapsJvmPlatformsToNativeResources() {
        assertEquals("libwebrtc-java-linux-x86_64.so", WebRtcNativeLibrary.resourceName("Linux", "amd64"));
        assertEquals("libwebrtc-java-macos-aarch64.dylib", WebRtcNativeLibrary.resourceName("Mac OS X", "arm64"));
        assertEquals("webrtc-java-windows-x86_64.dll", WebRtcNativeLibrary.resourceName("Windows 11", "x86-64"));
        assertThrows(IllegalStateException.class, () -> WebRtcNativeLibrary.resourceName("Plan 9", "amd64"));
        assertThrows(IllegalStateException.class, () -> WebRtcNativeLibrary.resourceName("Linux", "riscv64"));
    }

    @Test
    public void packagesEverySupportedNative() {
        final ClassLoader classLoader = WebRtcNativeLibrary.class.getClassLoader();
        assertNotNull(classLoader.getResource("libwebrtc-java-linux-x86_64.so"));
        assertNotNull(classLoader.getResource("libwebrtc-java-macos-aarch64.dylib"));
    }

    @Test
    public void loadsNativeOnCurrentPlatform() {
        WebRtcNativeLibrary.ensureAvailable();

        final PeerConnectionFactory factory = new PeerConnectionFactory();
        factory.dispose();
    }

}
