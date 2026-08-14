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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.io.Serializer;
import io.netty.util.AttributeKey;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.viabedrock.api.util.CryptUtil;
import net.raphimc.viabedrock.api.util.FNV1;
import net.raphimc.viabedrock.protocol.storage.AuthData;

/**
 * A Bedrock identity shared by the NetherNet transport assertion and
 * ViaBedrock's login packet. It may contain either an authenticated Minecraft
 * multiplayer token or the local self-signed token used by LAN discovery.
 */
public final class BedrockNetherNetIdentity {

    public static final AttributeKey<BedrockNetherNetIdentity> CHANNEL_ATTRIBUTE = AttributeKey.valueOf("viafabricplus-bedrock-nethernet-identity");
    private static final String CONNECT_REQUEST = "CONNECTREQUEST ";
    private static final String IDENTITY_ATTRIBUTE = "a=identity:";
    private static final int TOKEN_LIFETIME_DAYS = 365;
    private static final Gson GSON = new Gson();
    // Supplying the serializer avoids JJWT's ServiceLoader, which is not
    // reliable when Fabric has isolated and maintained dependency versions.
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

    private final AuthData authData;
    private final String identityProviderDomain;

    private BedrockNetherNetIdentity(final AuthData authData, final String identityProviderDomain) {
        this.authData = authData;
        this.identityProviderDomain = identityProviderDomain;
    }

    public static BedrockNetherNetIdentity createSelfSigned(final String username) {
        Objects.requireNonNull(username, "username");
        if (username.isBlank()) {
            throw new IllegalArgumentException("A Bedrock LAN identity requires a non-blank username");
        }

        final Instant now = Instant.now();
        final KeyPair sessionKeyPair = CryptUtil.generateEcdsa384KeyPair();
        final String encodedPublicKey = Base64.getEncoder().encodeToString(sessionKeyPair.getPublic().getEncoded());
        final long rawXuid = FNV1.fnv1_64(username.getBytes(StandardCharsets.UTF_8));
        final String xuid = Long.toUnsignedString(rawXuid);
        final UUID identity = UUID.nameUUIDFromBytes(("pocket-auth-1-xuid:" + xuid).getBytes(StandardCharsets.UTF_8));
        final String multiplayerToken = Jwts.builder()
            .json(JWT_SERIALIZER)
            .signWith(sessionKeyPair.getPrivate(), Jwts.SIG.ES384)
            .header().add("x5u", encodedPublicKey).and()
            .claim(Claims.AUDIENCE, "api://auth-minecraft-services/multiplayer")
            .claim("cpk", encodedPublicKey)
            .claim("leguuid", identity)
            .claim("mid", Long.toHexString(rawXuid).toUpperCase(Locale.ROOT))
            .claim("nid", "")
            .claim("nname", "")
            .claim("pid", "")
            .claim("pname", "")
            .claim("xid", xuid)
            .claim("xname", username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(TOKEN_LIFETIME_DAYS, ChronoUnit.DAYS)))
            .compact();

        final AuthData authData = new AuthData(multiplayerToken, sessionKeyPair);
        authData.setSelfSignedId(identity);
        authData.setClientRandomId(FNV1.fnv1_64(identity.toString().getBytes(StandardCharsets.UTF_8)));
        return new BedrockNetherNetIdentity(authData, "self");
    }

    public static BedrockNetherNetIdentity createAuthenticated(final MinecraftMultiplayerToken multiplayerToken,
                                                                final KeyPair sessionKeyPair,
                                                                final UUID deviceId) {
        Objects.requireNonNull(multiplayerToken, "multiplayerToken");
        Objects.requireNonNull(sessionKeyPair, "sessionKeyPair");
        Objects.requireNonNull(deviceId, "deviceId");

        final String encodedPublicKey = Base64.getEncoder().encodeToString(sessionKeyPair.getPublic().getEncoded());
        final String tokenPublicKey = multiplayerToken.getParsedToken().getPayload().reqString("cpk");
        if (!encodedPublicKey.equals(tokenPublicKey)) {
            throw new IllegalArgumentException("Minecraft multiplayer token is not bound to the current session key");
        }

        final URI issuer = URI.create(multiplayerToken.getParsedToken().getPayload().reqString("iss"));
        final String identityProviderDomain = issuer.getHost();
        if (!"https".equalsIgnoreCase(issuer.getScheme()) || identityProviderDomain == null || identityProviderDomain.isBlank()) {
            throw new IllegalArgumentException("Minecraft multiplayer token has an invalid issuer");
        }
        return new BedrockNetherNetIdentity(
            new AuthData(multiplayerToken.getToken(), sessionKeyPair, deviceId),
            identityProviderDomain
        );
    }

    public AuthData authData() {
        return this.authData;
    }

    public String augmentConnectRequest(final String signal) {
        if (signal == null || !signal.startsWith(CONNECT_REQUEST)) {
            return signal;
        }
        final String[] parts = signal.split(" ", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed NetherNet CONNECTREQUEST");
        }
        return parts[0] + " " + parts[1] + " " + augmentOffer(parts[2]);
    }

    String augmentOffer(final String offer) {
        Objects.requireNonNull(offer, "offer");
        if (offer.contains("\n" + IDENTITY_ATTRIBUTE) || offer.startsWith(IDENTITY_ATTRIBUTE)) {
            return offer;
        }

        final String canonicalFingerprints = canonicalFingerprints(offer);
        final String signedFingerprints = Jwts.builder()
            .json(JWT_SERIALIZER)
            .content(canonicalFingerprints.getBytes(StandardCharsets.UTF_8))
            .signWith(this.authData.getSessionKeyPair().getPrivate(), Jwts.SIG.ES384)
            .compact();
        final int firstDot = signedFingerprints.indexOf('.');
        final int lastDot = signedFingerprints.lastIndexOf('.');
        if (firstDot <= 0 || lastDot <= firstDot) {
            throw new IllegalStateException("Could not create NetherNet fingerprint assertion");
        }

        final JsonObject assertion = new JsonObject();
        assertion.addProperty("token", this.authData.getMultiplayerToken());
        assertion.addProperty("fingerprints", signedFingerprints.substring(0, firstDot + 1) + signedFingerprints.substring(lastDot));
        final JsonObject idp = new JsonObject();
        idp.addProperty("domain", this.identityProviderDomain);
        idp.addProperty("protocol", "default");
        final JsonObject identity = new JsonObject();
        identity.add("idp", idp);
        identity.addProperty("assertion", assertion.toString());
        final String identityLine = IDENTITY_ATTRIBUTE + Base64.getEncoder().encodeToString(identity.toString().getBytes(StandardCharsets.UTF_8));

        final String lineEnding = offer.contains("\r\n") ? "\r\n" : "\n";
        final int mediaLine = firstMediaLine(offer);
        if (mediaLine < 0) {
            throw new IllegalArgumentException("NetherNet offer has no media section");
        }
        return offer.substring(0, mediaLine) + identityLine + lineEnding + offer.substring(mediaLine);
    }

    private static String canonicalFingerprints(final String offer) {
        final StringBuilder fingerprints = new StringBuilder("{\"fingerprint\":[");
        boolean found = false;
        for (final String rawLine : offer.split("\\r?\\n")) {
            if (!rawLine.startsWith("a=fingerprint:")) {
                continue;
            }
            final String value = rawLine.substring("a=fingerprint:".length()).trim();
            final int separator = value.indexOf(' ');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new IllegalArgumentException("Malformed NetherNet fingerprint attribute");
            }
            final String algorithm = value.substring(0, separator);
            final String digest = value.substring(separator + 1).trim();
            if (!algorithm.matches("[A-Za-z0-9-]+") || !digest.matches("[0-9A-Fa-f:]+")) {
                throw new IllegalArgumentException("Malformed NetherNet fingerprint value");
            }
            if (found) {
                fingerprints.append(',');
            }
            fingerprints.append("{\"algorithm\":\"")
                .append(algorithm)
                .append("\",\"digest\":\"")
                .append(digest)
                .append("\"}");
            found = true;
        }
        if (!found) {
            throw new IllegalArgumentException("NetherNet offer has no fingerprint attribute");
        }
        return fingerprints.append("]}").toString();
    }

    private static int firstMediaLine(final String offer) {
        if (offer.startsWith("m=")) {
            return 0;
        }
        final int lf = offer.indexOf("\nm=");
        return lf == -1 ? -1 : lf + 1;
    }

}
