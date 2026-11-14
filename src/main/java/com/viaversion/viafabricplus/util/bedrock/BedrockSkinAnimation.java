/*
 * This file is part of ViaFabricPlusPeaks.
 * Copyright (C) 2026 Peaks2000 and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.viaversion.viafabricplus.util.bedrock;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.UvMapping;

/** The sprite strips and geometry slots defined by Bedrock's persona render controllers. */
final class BedrockSkinAnimation {

    private static final int MAX_STRIP_SIZE = 2048;
    private static final int MAX_FRAMES = 128;

    private BedrockSkinAnimation() {
    }

    static List<Layer> layers(final BedrockSkinAppearance skin) {
        return layers(skin, false);
    }

    static List<Layer> layers(final BedrockSkinAppearance skin, final boolean trace) {
        final List<Layer> layers = new ArrayList<>(3);
        addLayer(layers, skin, 2, "animated_32x32", trace);
        addLayer(layers, skin, 3, "animated_128x128", trace);
        addLayer(layers, skin, 1, "animated_face", trace);
        return List.copyOf(layers);
    }

    private static void addLayer(final List<Layer> layers, final BedrockSkinAppearance skin,
                                 final int type, final String slot, final boolean trace) {
        for (var animation : skin.animations()) {
            final BufferedImage strip = animation.image();
            final int frames = (int) animation.frames();
            if (animation.type() != type || strip == null || frames < 1 || frames > MAX_FRAMES
                || frames != animation.frames() || strip.getHeight() > MAX_STRIP_SIZE
                || strip.getWidth() > MAX_STRIP_SIZE || strip.getHeight() % frames != 0
                || (animation.expression() != 0 && animation.expression() != 1)) {
                continue;
            }
            final int frameHeight = strip.getHeight() / frames;
            final var geometry = BedrockPersonaGeometry.parseLayer(skin, slot, strip.getWidth(), frameHeight, frames, trace);
            if (geometry != null) {
                layers.add(new Layer(geometry, strip, frames, type != 1, animation.expression() == 1));
                return;
            }
        }
    }

    static int frame(final float ageInTicks, final int entityId, final int frames, final boolean blinking) {
        if (frames == 1 || !Float.isFinite(ageInTicks) || ageInTicks <= 0) {
            return 0;
        }
        if (blinking) {
            // The wire value specifies the expression, with no per-player blink schedule.
            final int interval = 80 + Math.floorMod(entityId, 81);
            return (ageInTicks + Math.floorMod(entityId * 31, interval)) % interval < 3 ? 1 : 0;
        }
        return (int) (Math.floor(ageInTicks * (7D / 20D)) % frames);
    }

    record Layer(BedrockPersonaGeometry.Geometry geometry, BufferedImage strip, int frames, boolean body, boolean blinking) { }

    record FrameUv(int frame, int frames, int stripHeight) implements UvMapping {
        @Override
        public float getU(final float u) {
            return u;
        }

        @Override
        public float getV(final float v) {
            // Keep UVs inside the current frame even at its top and bottom edges.
            final float inset = 0.5F / this.stripHeight;
            return Math.clamp((this.frame + v) / this.frames,
                (float) this.frame / this.frames + inset, (float) (this.frame + 1) / this.frames - inset);
        }
    }
}
