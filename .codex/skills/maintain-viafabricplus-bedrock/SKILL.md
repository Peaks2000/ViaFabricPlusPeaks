---
name: maintain-viafabricplus-bedrock
description: Diagnose, update, build, validate, package, and coordinate the three repositories in the ViaFabricPlus Bedrock-maintenance fork. Use for cross-repository work in Peaks2000/ViaFabricPlusPeaks, Peaks2000/ViaBedrock, and Peaks2000/maintain-viafabricplus-bedrock; Bedrock LAN discovery; iOS NetherNet/WebRTC and native-library failures; RakNet; Xbox friends/MPSD; self-signed or transport-bound authentication; ViaBedrock protocol bumps; packet decoder, crafting, inventory, terrain, or mapping regressions; Fabric JAR builds; and stock-versus-maintained route isolation.
---

# Maintain ViaFabricPlus Bedrock

## Purpose

Keep the fork's Bedrock transports and ViaBedrock translation layer working across Minecraft Bedrock releases. Base each change on a fresh runtime failure and the packet schema for the host's actual game protocol; do not bypass version checks without updating the corresponding codec.

## Coordinate the repositories

Read `references/repository-coordination.md` before cloning, branching, changing dependencies, synchronizing the skill, committing, pushing, or building a release across repositories. Treat ViaFabricPlus, ViaBedrock, and the standalone skill as independent Git repositories with distinct ownership and histories. Keep the standalone skill canonical and mirror its files exactly under ViaFabricPlus's `.codex/skills/maintain-viafabricplus-bedrock/` directory.

Read the references selectively:

- Read `references/fork-architecture.md` before changing routing, discovery, authentication, transport, or stock-runtime isolation.
- Read `references/nethernet-auth-and-natives.md` for iOS NetherNet, `PeerConnectionFactory`, JNI/native loading, SDP `a=identity`, `ServerIdConflict`, `NotAuthenticated`, or `NonceMissing` failures.
- Read `references/version-update-workflow.md` before changing a protocol number, packet field, serializer, mapping resource, or ViaBedrock revision.

## Start with evidence

1. Confirm the repository root and inspect `git status --short`. Preserve unrelated work and never commit launcher logs, tokens, Xbox identifiers, or account data.
2. Find the newest launcher log, not merely the newest log copied into the repository. Run `scripts/collect-bedrock-errors.sh` with no argument, or pass an explicit log path. Treat the first exception or disconnect as causal until disproved; later `NoClassDefFoundError`, timeouts, and reconnect failures often repeat an earlier initializer failure.
3. Read enough context around the first causal error. Later timeouts and disconnect screens are often consequences.
4. Classify the failing layer before editing:
   - discovery/advertisement parsing;
   - Xbox people, handles, or MPSD membership;
   - NetherNet signaling, SDP, ICE, or WebRTC;
   - WebRTC native packaging/JNI initialization;
   - RakNet transport;
   - Bedrock login/version negotiation;
   - packet schema/decoder;
   - ViaBedrock mapping data or Java translation.
5. Read `references/fork-architecture.md` for the relevant files and fork-only behavior.

If Xbox TURN/signaling requests succeed and the next inbound message is `SIGNAL_CONNECT_ERROR`, check whether the outgoing `CONNECTREQUEST` offer carries the authenticated account's `a=identity` assertion. This rejection happens before Bedrock login, so do not patch protocol, crafting, or inventory code for it.

If TURN and every outbound Xbox JSON-RPC request succeed but there is no inbound `Signaling_ReceiveMessage_v1_0`, `CONNECTRESPONSE`, or `CONNECTERROR`, the remote host has not answered signaling. Wait for the bounded NetherNet handshake timeout and report that stage accurately; nonce, login, inventory, and crafting code have not run yet. Do not restore automatic handshake retries merely to hide this state because reusing a host-side connection can revive `ServerIdConflict`.

## Update a Bedrock version

Read `references/version-update-workflow.md` before changing any protocol number, packet field, ViaBedrock revision, or mapping resource.

Treat these identifiers separately:

- RakNet MOTD protocol number is a Bedrock game protocol and is usable evidence.
- Xbox session custom game version is usable evidence after normalization.
- NetherNet LAN advertisement revision is a transport/discovery format. It is not a Bedrock game protocol.

NetherNet LAN advertisement revision 6 uses unsigned-varint UTF-8 lengths, signed zigzag varints, and includes `AcceptsOnlineAuth`, `AcceptsSelfSignedAuth`, a host-generated `Nonce`, transport layer, and connection type after the shared world fields. Parse the full revision-6 payload and carry its nonce only in memory into the signed client-data claim. A one-byte string-length parser silently corrupts names longer than 127 bytes and must not be used.
- `BedrockProtocolVersion.bedrockLatest` is ViaVersion's route identity and can differ from the wire protocol placed in `HandshakeStorage`.

When the server returns `LoginFailed_ClientOld` or `LoginFailed_ServerOld`, add only verified adjacent supported protocols to `BedrockProtocolCompatibility`. Never loop over arbitrary integers. A successful login version check does not prove subsequent packet layouts are compatible.

Do not infer a protocol from a changelog filename. Mojang's file named `changelog_2168_07_07_26.md` currently declares network protocol 2169 in its header. Its note that client-hosted self-signed authentication requires a LAN secret therefore does not establish the first affected wire version. Capture the host's advertised/login protocol and its runtime disconnect reason. A protocol-2168 client-hosted world can return `NonceMissing`; handle the observed capability rather than widening every 2168 route.

For packet decoder errors, identify the packet and the first incorrect field from the stack trace. Compare the checked-out ViaBedrock handler with a protocol schema for both the last working and target versions. Patch the dedicated ViaBedrock fork whenever it owns the codec, serializer, packet handler, or inventory model. Use a focused ViaFabricPlus mixin only when the dependency cannot reasonably own a small temporary correction, and delete the mixin after moving the fix into ViaBedrock. Every mixin targeting a synthetic `lambda$...` method needs a development-client startup check because upstream recompilation can change the target.

If Java reports that a translated packet was "larger than expected", inspect the ViaBedrock handler at the first `wrapper.send(...)`. Unread Bedrock input can leak into the Java packet. Do not merely clear the input buffer: verify whether Mojang changed an earlier field's encoding or converted the packet to Cereal. For 1.26.40, `AddItemActor` and `AddPlayer` must read their item with `ItemRewriter.newItemType()` (`NetworkItemStackDescriptor`), not the legacy `itemType()`.

## Xbox friends

Follow the MPSD request schema exactly. If a session enables `connectionRequiredForActiveMembers`, an active member PUT needs a stable per-process connection GUID at `members.me.properties.system.connection`. Keep contract header and template semantics distinct. Do not print authorization headers, XSTS tokens, Minecraft multiplayer tokens, or full response bodies that can contain personal data.

For a client-hosted friend world, membership PUT and game connection are not one atomic step. After the successful PUT, read or poll the session until `properties.custom.nonces[<joining-xuid>]` is a non-empty string. Carry that ephemeral value only in memory and add it as the `Nonce` claim in ViaBedrock's signed client/skin JWT. Preserve the normal Xbox multiplayer token and its `AuthenticationType`; replacing the outer `AuthenticationInfo.Token` with the nonce causes authentication regressions. Never log or persist the nonce.

## Validate and deliver

Run, in order:

1. `./gradlew --include-build ../ViaBedrock compileJava`
2. the narrowest relevant test selection, explicitly enabling the branch's disabled test task with `-I .codex/skills/maintain-viafabricplus-bedrock/scripts/enable-vfp-tests.gradle`;
3. `./gradlew --include-build ../ViaBedrock build`
4. `./gradlew runClient`, wait for the main menu and ViaBedrock initialization, then stop it; treat missing optional narrator libraries as unrelated unless startup aborts.

Inspect `git diff --check`, the resulting JAR contents, and its SHA-256. Confirm the main JAR nests the maintained ViaBedrock JAR and every native classifier required by the reported client platform. The distributable is the remapped main JAR under `build/libs/`; exclude `-sources`, `-dev`, and submodule JARs. If the user wants it installed, locate the exact launcher instance and replace only the matching prior mod JAR.

This fork deliberately uses two ViaBedrock runtimes in one Fabric instance: ordinary server-list joins use the isolated embedded stock runtime, while the dedicated LAN/friends screen selects the maintained current runtime. Preserve this route boundary. A LAN codec fix must not alter the stock server route, and validation must cover one ordinary Bedrock server plus the affected LAN/friends transport.

If a real host remains available, have the user retry once with the new JAR and immediately re-run the log collector. Record the next first causal error; Bedrock version updates commonly reveal packet changes one at a time.

Only commit, push, create repositories, publish artifacts, or mutate Xbox sessions when the user authorizes those actions. Follow the repository-specific commit and push order in `references/repository-coordination.md` so the dependency branch, consuming fork, distributable JAR, standalone skill, project mirror, and installed skill remain consistent.
