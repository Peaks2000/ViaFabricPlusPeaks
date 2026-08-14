---
name: maintain-viafabricplus-bedrock
description: Diagnose, update, build, validate, and coordinate the three repositories in the ViaFabricPlus Bedrock-maintenance fork. Use for cross-repository work in Peaks2000/ViaFabricPlusPeaks, Peaks2000/ViaBedrock, and Peaks2000/maintain-viafabricplus-bedrock; Bedrock LAN discovery; iOS NetherNet/WebRTC; RakNet; Xbox friends/MPSD sessions; ViaBedrock protocol bumps; packet decoder failures; mapping-data regressions; Fabric JAR builds; or reviewing what this fork changes after upstream ViaFabricPlus removes Bedrock support.
---

# Maintain ViaFabricPlus Bedrock

## Purpose

Keep the fork's Bedrock transports and ViaBedrock translation layer working across Minecraft Bedrock releases. Base each change on a fresh runtime failure and the packet schema for the host's actual game protocol; do not bypass version checks without updating the corresponding codec.

## Coordinate the repositories

Read `references/repository-coordination.md` before cloning, branching, changing dependencies, synchronizing the skill, committing, pushing, or building a release across repositories. Treat ViaFabricPlus, ViaBedrock, and the standalone skill as independent Git repositories with distinct ownership and histories. Keep the standalone skill canonical and mirror its files exactly under ViaFabricPlus's `.codex/skills/maintain-viafabricplus-bedrock/` directory.

## Start with evidence

1. Confirm the repository root and inspect `git status --short`. Preserve unrelated work and never commit launcher logs, tokens, Xbox identifiers, or account data.
2. Find the newest launcher log, not merely the newest log copied into the repository. Run `scripts/collect-bedrock-errors.sh` with no argument, or pass an explicit log path.
3. Read enough context around the first causal error. Later timeouts and disconnect screens are often consequences.
4. Classify the failing layer before editing:
   - discovery/advertisement parsing;
   - Xbox people, handles, or MPSD membership;
   - NetherNet signaling, SDP, ICE, or WebRTC;
   - RakNet transport;
   - Bedrock login/version negotiation;
   - packet schema/decoder;
   - ViaBedrock mapping data or Java translation.
5. Read `references/fork-architecture.md` for the relevant files and fork-only behavior.

## Update a Bedrock version

Read `references/version-update-workflow.md` before changing any protocol number, packet field, ViaBedrock revision, or mapping resource.

Treat these identifiers separately:

- RakNet MOTD protocol number is a Bedrock game protocol and is usable evidence.
- Xbox session custom game version is usable evidence after normalization.
- NetherNet LAN advertisement revision is a transport/discovery format. It is not a Bedrock game protocol.
- `BedrockProtocolVersion.bedrockLatest` is ViaVersion's route identity and can differ from the wire protocol placed in `HandshakeStorage`.

When the server returns `LoginFailed_ClientOld` or `LoginFailed_ServerOld`, add only verified adjacent supported protocols to `BedrockProtocolCompatibility`. Never loop over arbitrary integers. A successful login version check does not prove subsequent packet layouts are compatible.

For packet decoder errors, identify the packet and the first incorrect field from the stack trace. Compare the checked-out ViaBedrock handler with a protocol schema for both the last working and target versions. Patch upstream ViaBedrock when practical; use a focused mixin only when the fork must carry a small temporary correction. Every mixin targeting a synthetic `lambda$...` method needs a development-client startup check because upstream recompilation can change the target.

If Java reports that a translated packet was "larger than expected", inspect the ViaBedrock handler at the first `wrapper.send(...)`. Unread Bedrock input can leak into the Java packet. Do not merely clear the input buffer: verify whether Mojang changed an earlier field's encoding or converted the packet to Cereal. For 1.26.40, `AddItemActor` and `AddPlayer` must read their item with `ItemRewriter.newItemType()` (`NetworkItemStackDescriptor`), not the legacy `itemType()`.

## Xbox friends

Follow the MPSD request schema exactly. If a session enables `connectionRequiredForActiveMembers`, an active member PUT needs a stable per-process connection GUID at `members.me.properties.system.connection`. Keep contract header and template semantics distinct. Do not print authorization headers, XSTS tokens, Minecraft multiplayer tokens, or full response bodies that can contain personal data.

## Validate and deliver

Run, in order:

1. `./gradlew compileJava`
2. the narrowest relevant test task available in the branch;
3. `./gradlew build`
4. `./gradlew runClient`, wait for the main menu and ViaBedrock initialization, then stop it; treat missing optional narrator libraries as unrelated unless startup aborts.

Inspect `git diff --check`, the resulting JAR contents, and its SHA-256. The distributable is the remapped main JAR under `build/libs/`; exclude `-sources`, `-dev`, and submodule JARs. If the user wants it installed, locate the exact launcher instance and replace only the matching prior mod JAR.

This fork deliberately uses two ViaBedrock runtimes in one Fabric instance: ordinary server-list joins use the isolated embedded stock runtime, while the dedicated LAN/friends screen selects the maintained current runtime. Preserve this route boundary. A LAN codec fix must not alter the stock server route, and validation must cover one ordinary Bedrock server plus the affected LAN/friends transport.

If a real host remains available, have the user retry once with the new JAR and immediately re-run the log collector. Record the next first causal error; Bedrock version updates commonly reveal packet changes one at a time.

Only commit, push, create repositories, or mutate Xbox sessions when the user authorizes those actions. Follow the repository-specific commit and push order in `references/repository-coordination.md` so the dependency branch, consuming fork, distributable JAR, and both skill copies remain consistent.
