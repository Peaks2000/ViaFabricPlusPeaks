# NetherNet authentication and native runtime

## Contents

1. [Classify the first failure](#classify-the-first-failure)
2. [WebRTC native packaging](#webrtc-native-packaging)
3. [Transport identity binding](#transport-identity-binding)
4. [Protocol 2168 versus the LAN-secret change](#protocol-2168-versus-the-lan-secret-change)
5. [Code ownership](#code-ownership)
6. [Regression and release checks](#regression-and-release-checks)

## Classify the first failure

Use the first failure in the connection attempt. NetherNet failures often cascade:

| First signature | Layer | Action |
|---|---|---|
| `PeerConnectionFactory.<clinit>`, `Load library 'webrtc-java' failed`, `Files.copy` NPE | Native packaging | Add or repair the platform classifier before examining SDP or login. |
| `NoClassDefFoundError: Could not initialize class PeerConnectionFactory` | Repeated native failure | Find the earlier initializer exception in the same process. Restart after replacing the JAR. |
| set-local/set-remote SDP error | WebRTC/SDP | Inspect SDP normalization, M152 compatibility, and identity placement. |
| ICE timeout | Candidate/network path | Inspect candidate families, reachability, filtering, and trickle order. |
| Xbox signaling succeeds, then `SIGNAL_CONNECT_ERROR` before `CONNECTRESPONSE` | Client offer authentication | Add the authenticated account's transport-bound `a=identity`; this is earlier than Bedrock login. |
| `ServerIdConflict` | Game login identity | Stop reusing the host/configured Microsoft identity for a LAN guest. |
| `NotAuthenticated` after a distinct local identity was introduced | Transport/login binding | Reuse one identity object for the offer assertion and login `AuthData`. |
| `LoginFailed_ClientOld` / `LoginFailed_ServerOld` | Game protocol | Use verified adjacent protocol retry; do not alter authentication as a workaround. |

Ignore unrelated rendering, shader, resource-pack metadata, Realms HTTP, and optional narrator warnings unless they abort startup before ViaBedrock initializes.

## WebRTC native packaging

`dev.kastle.webrtc.internal.NativeLoader` derives a resource name from `os.name` and `os.arch`, opens it through the shared class loader, copies it to a temporary file, and calls `System.load`. Its current implementation passes a missing resource stream to `Files.copy`, producing an unhelpful `NullPointerException`. Treat that NPE as "classifier absent," not as a filesystem permissions error.

Expected names include:

- Linux x86-64: `libwebrtc-java-linux-x86_64.so`
- macOS Apple Silicon: `libwebrtc-java-macos-aarch64.dylib`
- macOS Intel: `libwebrtc-java-macos-x86_64.dylib`
- Windows x86-64: `webrtc-java-windows-x86_64.dll`

The maintained fork uses M152 because the earlier M138 native rejects SDP from current iOS Bedrock hosts. The known cross-platform source is `Kas-tle/webrtc-java` PR #4 at commit `04a3d55ca22254a3d508173ac7fa250f44ff2151`, successfully built in `SendableMetatype/webrtc-java` workflow run `31000968829`. Keep Java classes and every native classifier on that exact JNI commit.

Current fork coverage is Linux x86-64 and macOS arm64. When adding a platform:

1. Obtain the classifier from the same successful M152 run or a newer single commit whose full platform matrix passed.
2. Verify the original SHA-256 and archive contents. The archive must contain the exact resource name the loader derives.
3. Put it under the existing local Maven coordinate in `vendor/maven/` and add the classifier to `configureBedrockDependencies`.
4. Extend `WebRtcNativeLibrary.resourceName` only if upstream naming changed; do not create aliases that hide a mixed artifact.
5. Extend `WebRtcNativeLibraryTest.packagesEverySupportedNative`.
6. Inspect the remapped VFP JAR and its nested classifier JAR after `build`; compile/runtime classpaths alone do not prove release packaging.
7. On the target OS, start the client and begin one NetherNet connection so `PeerConnectionFactory` actually initializes.

Do not solve a missing M152 macOS native by adding the published M138 classifier. Java/JNI symbols may appear compatible while SDP behavior regresses to the original iOS failure.

## Transport identity binding

Keep three concepts distinct:

- The configured Microsoft/Bedrock account used by online servers and Xbox sessions.
- A distinct self-signed LAN guest identity used to avoid colliding with the host's account/player ID.
- The proof that binds the WebRTC transport to the game-login identity.

Create one `BedrockNetherNetIdentity` per connection and store it on the NetherNet channel. For Xbox/friends, refresh the account's Minecraft multiplayer token before creating the WebRTC channel, require its `cpk` to match the account's P-384 session key, and derive `idp.domain` from the token's HTTPS `iss` host. For direct LAN discovery, create a distinct self-signed P-384 keypair and multiplayer token with `idp.domain` set to `self`. In both cases, reuse the identity's exact `AuthData` when ViaBedrock creates the game login; never refresh or regenerate it between SDP and login.

Apply the identity through `BedrockNetherNetIdentitySignaling`, a decorator around `NetherNetClientSignaling`. This keeps the assertion independent of Xbox JSON-RPC, legacy Xbox WebSocket, and LAN discovery transports. A transport-specific signaling subclass must not be the sole owner of client identity injection.

The outgoing SDP offer needs a session-level `a=identity` attribute. Its base64-decoded envelope is:

```json
{
  "idp": {"domain": "self", "protocol": "default"},
  "assertion": "{\"token\":\"<JWT>\",\"fingerprints\":\"<header>..<signature>\"}"
}
```

Build and verify it as follows:

1. Put the same public key in the multiplayer token's `cpk` claim and `x5u` header.
2. Collect every SDP `a=fingerprint:` value in wire order.
3. Serialize canonical JSON with no whitespace: `{"fingerprint":[{"algorithm":"sha-256","digest":"..."}]}`.
4. Sign that payload with the private key corresponding to `cpk` using ES384.
5. Remove only the compact JWS payload, leaving `<base64url header>..<base64url signature>`.
6. Place `a=identity` before the first `m=` line. Preserve the offer's CRLF/LF convention.
7. Put the exact same token and keypair into ViaBedrock `AuthData`.

Regression tests must do more than count dots. Reconstruct the compact fingerprint JWS with the known canonical payload, verify its ES384 signature with the `AuthData` public key, parse the multiplayer token with the same key, and compare its `cpk` claim to the encoded public key.

Fabric may expose more than one JJWT implementation through isolated runtimes. Avoid JJWT `ServiceLoader` discovery in this path: supply an explicit JSON serializer/deserializer. A `ServiceConfigurationError` saying `GsonSerializer not a subtype` is a dependency/classloader problem, not a bad LAN token.

Never log the multiplayer token, assertion envelope, authorization header, private key, device ID, XUID, Xbox session body, or full SDP. Safe logs may state that a transport-bound local identity was selected.

## Protocol 2168 versus the LAN-secret change

Mojang's repository currently contains a file named `changelog_2168_07_07_26.md`, but its document header states network protocol 2169. The document says a LAN secret is required for self-signed authentication on client-hosted games. Do not assume that note applies to protocol 2168 merely because of the filename, and do not rename the SDP identity binding as a LAN-secret implementation.

If a real host still returns `NotAuthenticated` after native loading and cryptographic identity binding are proven:

1. Capture its game protocol from RakNet/Xbox/login evidence.
2. If it is 2169, update the codec/schema first and locate the verified source of the LAN secret.
3. Capture a vanilla discovery/signaling/login trace or authoritative schema showing where the secret originates and how it is carried or derived.
4. Implement it in the lowest owning layer and add a byte-level or cryptographic regression test.

Do not invent a random secret, derive one from the network ID, or reuse an Xbox token without evidence. Those approaches can hide the real version mismatch and weaken authentication.

## Code ownership

- ViaFabricPlus owns classifier packaging, platform preflight, LAN-versus-account routing, channel attributes, client-offer identity decoration across every signaling transport, discovery signaling, and injecting the transport-bound `AuthData` at the Fabric connection boundary.
- ViaBedrock owns reusable login-chain generation, Bedrock auth fields, packet serialization, and protocol-specific LAN-secret fields once their schema is known.
- NetworkCompatible/netty-transport-nethernet owns general WebRTC offer/answer and signaling behavior. Carry a small VFP override only while upstream lacks the required client assertion hook.

Avoid implementing the same assertion in both VFP signaling and NetworkCompatible. If upstream adds an identity provider API, migrate the VFP code to it and keep only route-specific identity selection.

## Regression and release checks

From the ViaFabricPlus checkout with the sibling ViaBedrock checkout available:

```bash
./gradlew --include-build ../ViaBedrock compileJava
./gradlew --include-build ../ViaBedrock test \
  --tests com.viaversion.viafabricplus.util.bedrock.BedrockNetherNetIdentityTest \
  --tests com.viaversion.viafabricplus.util.bedrock.BedrockNetherNetIdentitySignalingTest \
  --tests com.viaversion.viafabricplus.util.bedrock.WebRtcNativeLibraryTest \
  -I .codex/skills/maintain-viafabricplus-bedrock/scripts/enable-vfp-tests.gradle
./gradlew --include-build ../ViaBedrock build
```

Then require all of the following:

- focused authentication, native-resource, signaling, discovery, protocol-wire, inventory, and crafting tests pass;
- `runClient` reaches the main menu and reports both the isolated stock route and maintained route initialization;
- starting a NetherNet attempt initializes `PeerConnectionFactory` on the reported client OS;
- the final main JAR contains `WebRtcNativeLibrary`, `BedrockNetherNetIdentity`, maintained ViaBedrock, and the target native classifier JAR;
- the user tests one LAN discovery connection and, when relevant, one Xbox-friend NetherNet connection;
- an ordinary Bedrock server-list join still uses the isolated stock route;
- the final JAR size and SHA-256 are recorded.

If the target OS is unavailable locally, say so explicitly. Packaging tests and Linux startup prove structure but do not replace Apple Silicon JNI initialization or a real iOS-hosted LAN join.
