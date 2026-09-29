/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.viaversion.viafabricplus.util.bedrock;

import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.util.LegacySaveMigrator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BedrockAccountPreservationTest {
    @Test void classiCubeAnimationPreferenceMovesWithoutOverwritingExistingPreference() {
        final JsonObject settings = new JsonObject();
        final JsonObject oldVisual = new JsonObject();
        oldVisual.addProperty("slow_down_classic_animation", false);
        settings.add("peaks_visual", oldVisual);
        LegacySaveMigrator.migratePeaksGroupSettings(settings);
        assertFalse(settings.getAsJsonObject("classicube").get("slow_down_classic_animation").getAsBoolean());
        settings.getAsJsonObject("classicube").addProperty("slow_down_classic_animation", true);
        LegacySaveMigrator.migratePeaksGroupSettings(settings);
        assertTrue(settings.getAsJsonObject("classicube").get("slow_down_classic_animation").getAsBoolean());
    }

    @Test void classicMigrationKeepsCookiesAndDoesNotConsumeBedrockCredentials() {
        final JsonObject legacy = new JsonObject();
        final JsonObject classic = new JsonObject();
        classic.addProperty("username", "fixture-user");
        legacy.add("classicube", classic);
        final JsonObject cookies = new JsonObject();
        cookies.addProperty("fixture_session", "fixture-cookie");
        legacy.add("classicube_cookies", cookies);
        final JsonObject bedrock = new JsonObject();
        bedrock.addProperty("fixture", true);
        legacy.add("bedrockV3", bedrock);
        final JsonObject migrated = LegacySaveMigrator.migratedClassiCubeAccount(legacy);
        assertEquals("fixture-user", migrated.get("username").getAsString());
        assertEquals(cookies, migrated.getAsJsonObject("peaks_cookies"));
        assertEquals(bedrock, legacy.getAsJsonObject("bedrockV3"));
        migrated.getAsJsonObject("peaks_cookies").addProperty("fixture_session", "changed");
        assertEquals("fixture-cookie", cookies.get("fixture_session").getAsString());
    }
}
