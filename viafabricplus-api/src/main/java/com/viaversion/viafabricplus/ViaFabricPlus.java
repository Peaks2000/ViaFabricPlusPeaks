/*
 * This file is part of ViaFabricPlus - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.viaversion.viafabricplus;

import com.viaversion.viafabricplus.api.ViaFabricPlusBase;
import org.jetbrains.annotations.ApiStatus;

/**
 * Holder class for the {@link ViaFabricPlusBase} implementation
 */
public final class ViaFabricPlus {

    private static ViaFabricPlusBase impl;

    @ApiStatus.Internal
    public static void init(final ViaFabricPlusBase impl) {
        if (ViaFabricPlus.impl != null) {
            throw new IllegalStateException("ViaFabricPlus has already been initialized!");
        }
        ViaFabricPlus.impl = impl;
    }

    /**
     * @return the ViaFabricPlusBase implementation that is set by the internals
     */
    public static ViaFabricPlusBase getImpl() {
        if (impl == null) {
            throw new IllegalStateException("ViaFabricPlus has not been initialized yet!");
        }
        return impl;
    }

}
