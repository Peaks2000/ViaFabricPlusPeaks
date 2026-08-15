---
name: maintain-viafabricplus-bedrock
description: Diagnose, update, build, validate, package, and coordinate the three repositories in the ViaFabricPlus Bedrock-maintenance fork. Use for new Minecraft Bedrock, ViaBedrock, ViaFabricPlus, Minecraft Java, Fabric Loader, or Fabric API releases; upstream rebases and dependency refreshes; auditing or retiring fork overrides; cross-repository work in Peaks2000/ViaFabricPlusPeaks, Peaks2000/ViaBedrock, and Peaks2000/maintain-viafabricplus-bedrock; Bedrock LAN discovery; iOS NetherNet/WebRTC and native-library failures; RakNet; Xbox friends/MPSD; self-signed or transport-bound authentication; protocol bumps; packet decoder, crafting, inventory, terrain, or mapping regressions; Fabric JAR builds; and stock-versus-maintained route isolation.
---

# Maintain ViaFabricPlus Bedrock

## Purpose

Keep the fork's Bedrock transports and ViaBedrock translation layer working across Minecraft Bedrock releases. Base each change on a fresh runtime failure and the packet schema for the host's actual game protocol; do not bypass version checks without updating the corresponding codec.

## Coordinate the repositories

Read `references/repository-coordination.md` before cloning, branching, changing dependencies, synchronizing the skill, committing, pushing, or building a release across repositories. Treat ViaFabricPlus, ViaBedrock, and the standalone skill as independent Git repositories with distinct ownership and histories. Keep the standalone skill canonical and mirror its files exactly under ViaFabricPlus's `.codex/skills/maintain-viafabricplus-bedrock/` directory.

Read the references selectively:

- Read `references/upstream-release-playbook.md` first when any Bedrock, ViaBedrock, ViaFabricPlus, Minecraft Java, Fabric, or ViaVersion release changes the baseline.
- Read `references/viafabricplus-v5-migration.md` when the target branch has removed ViaBedrock/Bedrock support or when moving this fork to ViaFabricPlus 5.x. Treat that transition as a subsystem migration, not a normal merge.
- Read `references/fork-architecture.md` before changing routing, discovery, authentication, transport, or stock-runtime isolation.
- Read `references/nethernet-auth-and-natives.md` for iOS NetherNet, `PeerConnectionFactory`, JNI/native loading, SDP `a=identity`, `ServerIdConflict`, `NotAuthenticated`, or `NonceMissing` failures.
- Read `references/version-update-workflow.md` before changing a protocol number, packet field, serializer, mapping resource, or ViaBedrock revision.

## Upgrade a new release

Treat upstream native behavior as the new baseline and this fork as a reviewed delta. Follow `references/upstream-release-playbook.md` rather than merging the previous maintenance branch wholesale.

1. Record the old and target versions and exact commits across ViaFabricPlus, ViaBedrock, Minecraft/Fabric, and the VCS dependency before editing.
2. Build and test the target upstream/native baseline where practical. Inventory every existing fork delta as **keep**, **drop**, **adapt**, or **quarantine**.
3. Drop a delta when upstream now implements the same behavior. Keep transport and integration features that remain fork-only. Adapt only the smallest code owned by the lowest correct repository.
4. Reject raw overrides: do not replace whole handlers, codecs, classes, or routes when a narrow owned change works; do not swallow decoder errors, clear unread bytes, accept arbitrary protocols, or duplicate a wire fix in ViaBedrock and ViaFabricPlus.
5. Give every retained compatibility override an exact scope, evidence from a real failure or schema change, a regression test, and a removal condition tied to an upstream commit or release.
6. Validate the stock server route and maintained LAN/friends route independently, then deliver dependency-first using `references/repository-coordination.md`.

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
- `BedrockProtocolVersion.bedrockLatest` is ViaVersion's route identity and can differ from the wire protocol placed in `HandshakeStorage`.

When the server returns `LoginFailed_ClientOld` or `LoginFailed_ServerOld`, add only verified adjacent supported protocols to `BedrockProtocolCompatibility`. Never loop over arbitrary integers, infer a protocol from a filename, or treat successful version negotiation as proof that later packet layouts work.

For a decoder error, identify the packet and first incorrect field, then compare the last working and target schemas. Patch ViaBedrock when it owns the codec, serializer, handler, mapping, or inventory model. Use a narrow ViaFabricPlus mixin only for an integration defect the dependency cannot own, and remove it once the fix moves down. Treat all version-specific notes in `references/version-update-workflow.md` as scoped historical knowledge, not permission to copy an old override into a new protocol.

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

Never overwrite a mod JAR in place while Minecraft has it open. This can leave the running ZIP reader observing a mixture of old and new bytes, producing `invalid LOC header`, missing translations, broken buttons, or resource reload failures even when the built JAR is valid. Check whether the instance is running; if it is, move the installed JAR to a recoverable path and copy the replacement under the expected filename so it receives a new inode, then require a full Minecraft restart. After installation, require matching local/installed SHA-256 values, `unzip -t` success, exactly one ViaFabricPlus mod JAR, and the expected `assets/viafabricplus/lang/en_us.json` keys.

The current 4.x fork deliberately uses two ViaBedrock runtimes in one Fabric instance: ordinary server-list joins use the isolated embedded stock runtime, while the dedicated LAN/friends screen selects the maintained current runtime. Preserve this boundary on 4.x. When migrating to an upstream baseline that no longer contains ViaBedrock, follow `references/viafabricplus-v5-migration.md`: any retained ordinary-server runtime becomes a pinned, fork-owned compatibility runtime rather than "stock", and it must not be carried forward implicitly.

If a real host remains available, have the user retry once with the new JAR and immediately re-run the log collector. Record the next first causal error; Bedrock version updates commonly reveal packet changes one at a time.

Only commit, push, create repositories, publish artifacts, or mutate Xbox sessions when the user authorizes those actions. Follow the repository-specific commit and push order in `references/repository-coordination.md` so the dependency branch, consuming fork, distributable JAR, standalone skill, project mirror, and installed skill remain consistent.
