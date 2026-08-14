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

package com.viaversion.viafabricplus.util.bedrock;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.io.Deserializer;
import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.io.Serializer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.viabedrock.api.util.CryptUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BedrockNetherNetIdentityTest {

    private static final String OFFER = "v=0\r\n"
        + "o=- 1 2 IN IP4 127.0.0.1\r\n"
        + "s=-\r\n"
        + "t=0 0\r\n"
        + "a=fingerprint:sha-256 AA:BB:CC:DD\r\n"
        + "m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n";
    private static final byte[] CANONICAL_FINGERPRINTS = "{\"fingerprint\":[{\"algorithm\":\"sha-256\",\"digest\":\"AA:BB:CC:DD\"}]}"
        .getBytes(StandardCharsets.UTF_8);
    private static final Gson GSON = new Gson();
    private static final TypeToken<Map<String, ?>> JWT_MAP_TYPE = new TypeToken<>() {
    };
    private static final Serializer<Map<String, ?>> JWT_SERIALIZER = new Serializer<>() {
        @Override
        public byte[] serialize(final Map<String, ?> value) throws SerializationException {
            return GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void serialize(final Map<String, ?> value, final OutputStream outputStream) throws SerializationException {
            try {
                outputStream.write(serialize(value));
            } catch (final IOException e) {
                throw new SerializationException("Could not serialize JWT JSON", e);
            }
        }
    };
    private static final Deserializer<Map<String, ?>> JWT_DESERIALIZER = new Deserializer<>() {
        @Override
        public Map<String, ?> deserialize(final byte[] bytes) throws DeserializationException {
            return deserialize(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public Map<String, ?> deserialize(final Reader reader) throws DeserializationException {
            try {
                return GSON.fromJson(reader, JWT_MAP_TYPE.getType());
            } catch (final JsonParseException e) {
                throw new DeserializationException("Could not deserialize JWT JSON", e);
            }
        }

        private Map<String, ?> deserialize(final String json) throws DeserializationException {
            try {
                return GSON.fromJson(json, JWT_MAP_TYPE.getType());
            } catch (final JsonParseException e) {
                throw new DeserializationException("Could not deserialize JWT JSON", e);
            }
        }
    };

    @Test
    public void bindsLoginTokenToNetherNetOffer() {
        final BedrockNetherNetIdentity identity = BedrockNetherNetIdentity.createSelfSigned("LanPlayer");
        final String signal = identity.augmentConnectRequest("CONNECTREQUEST 42 " + OFFER);
        final String offer = signal.split(" ", 3)[2];
        final String identityLine = offer.lines().filter(line -> line.startsWith("a=identity:")).findFirst().orElseThrow();
        final JsonObject envelope = JsonParser.parseString(new String(
            Base64.getDecoder().decode(identityLine.substring("a=identity:".length())),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        final JsonObject assertion = JsonParser.parseString(envelope.get("assertion").getAsString()).getAsJsonObject();

        assertEquals("self", envelope.getAsJsonObject("idp").get("domain").getAsString());
        assertEquals("default", envelope.getAsJsonObject("idp").get("protocol").getAsString());
        assertEquals(identity.authData().getMultiplayerToken(), assertion.get("token").getAsString());
        assertTrue(assertion.get("fingerprints").getAsString().matches("[^.]+\\.\\.[^.]+"));
        assertTrue(offer.indexOf("a=identity:") < offer.indexOf("m=application"));
        assertEquals("LanPlayer", identity.authData().getDisplayName());

        final PublicKey publicKey = identity.authData().getSessionKeyPair().getPublic();
        final JwtParser parser = Jwts.parser().json(JWT_DESERIALIZER).verifyWith(publicKey).build();
        final Jws<Claims> token = parser.parseSignedClaims(identity.authData().getMultiplayerToken());
        assertEquals(Base64.getEncoder().encodeToString(publicKey.getEncoded()), token.getPayload().get("cpk"));
        final String detachedFingerprints = assertion.get("fingerprints").getAsString();
        final String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(CANONICAL_FINGERPRINTS);
        final Jws<Claims> fingerprints = parser.parseSignedClaims(detachedFingerprints.replace("..", "." + encodedPayload + "."));
        assertTrue(fingerprints.getPayload().containsKey("fingerprint"));
    }

    @Test
    public void retainsIdentityForLoginAndDoesNotTouchOtherSignals() {
        final BedrockNetherNetIdentity identity = BedrockNetherNetIdentity.createSelfSigned("LanPlayer");

        assertSame(identity.authData(), identity.authData());
        assertEquals("CANDIDATEADD 42 candidate", identity.augmentConnectRequest("CANDIDATEADD 42 candidate"));
        final String augmented = identity.augmentOffer(OFFER);
        assertEquals(augmented, identity.augmentOffer(augmented));
        assertFalse(identity.authData().getXuid().isBlank());
    }

    @Test
    public void bindsAuthenticatedAccountTokenToOfferAndLogin() {
        final Instant now = Instant.now();
        final KeyPair sessionKeyPair = CryptUtil.generateEcdsa384KeyPair();
        final String encodedPublicKey = Base64.getEncoder().encodeToString(sessionKeyPair.getPublic().getEncoded());
        final String token = Jwts.builder()
            .json(JWT_SERIALIZER)
            .signWith(sessionKeyPair.getPrivate(), Jwts.SIG.ES384)
            .claim("iss", "https://authorization.franchise.minecraft-services.net/")
            .claim("aud", "api://auth-minecraft-services/multiplayer")
            .claim("cpk", encodedPublicKey)
            .claim("xid", "123456789")
            .claim("xname", "XboxPlayer")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
            .compact();
        final MinecraftMultiplayerToken multiplayerToken = new MinecraftMultiplayerToken(
            now.plus(1, ChronoUnit.HOURS).toEpochMilli(),
            token
        );
        final BedrockNetherNetIdentity identity = BedrockNetherNetIdentity.createAuthenticated(
            multiplayerToken,
            sessionKeyPair,
            UUID.randomUUID()
        );

        final String offer = identity.augmentConnectRequest("CONNECTREQUEST 42 " + OFFER).split(" ", 3)[2];
        final String identityLine = offer.lines().filter(line -> line.startsWith("a=identity:")).findFirst().orElseThrow();
        final JsonObject envelope = JsonParser.parseString(new String(
            Base64.getDecoder().decode(identityLine.substring("a=identity:".length())),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        final JsonObject assertion = JsonParser.parseString(envelope.get("assertion").getAsString()).getAsJsonObject();

        assertEquals("authorization.franchise.minecraft-services.net", envelope.getAsJsonObject("idp").get("domain").getAsString());
        assertEquals(token, assertion.get("token").getAsString());
        assertEquals(token, identity.authData().getMultiplayerToken());
        assertSame(sessionKeyPair, identity.authData().getSessionKeyPair());

        final KeyPair unrelatedKeyPair = CryptUtil.generateEcdsa384KeyPair();
        assertThrows(IllegalArgumentException.class, () -> BedrockNetherNetIdentity.createAuthenticated(
            multiplayerToken,
            unrelatedKeyPair,
            UUID.randomUUID()
        ));
    }

    @Test
    public void rejectsMalformedIdentityInputs() {
        assertThrows(NullPointerException.class, () -> BedrockNetherNetIdentity.createSelfSigned(null));
        assertThrows(IllegalArgumentException.class, () -> BedrockNetherNetIdentity.createSelfSigned("  "));

        final BedrockNetherNetIdentity identity = BedrockNetherNetIdentity.createSelfSigned("LanPlayer");
        assertThrows(IllegalArgumentException.class, () -> identity.augmentConnectRequest("CONNECTREQUEST missing-offer"));
        assertThrows(IllegalArgumentException.class, () -> identity.augmentOffer("v=0\r\na=fingerprint:sha-256 AA:BB\r\n"));
        assertThrows(IllegalArgumentException.class, () -> identity.augmentOffer(
            "v=0\r\na=fingerprint:sha-256 not-a-digest\r\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n"
        ));
    }

}
