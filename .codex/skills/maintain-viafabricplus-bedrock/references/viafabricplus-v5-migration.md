# ViaFabricPlus 5.x Bedrock-removal migration

Use this reference when rebasing onto a ViaFabricPlus baseline where upstream no longer ships Bedrock support. This is a subsystem extraction and reintegration, not an ordinary conflict resolution.

## Verified upstream boundary

As of 2026-08-15, upstream's `next/v5` branch has completed roadmap item `ViaBedrock Removal`. Commit `ec9ca0472946da17aa747905234eacb95d9d213c` removes the ViaBedrock dependency and the Bedrock accounts, screens, settings, routes, client fixes, pipeline provider, and RakNet integration. Its immediate pre-removal parent is `bc522f3c4a8c157471cad4597e92f972919fdbb8`.

Re-query the official branch, roadmap, and removal commit before every migration; these SHAs document the observed boundary, not a permanent target. Upstream has removed Bedrock rather than replacing ViaBedrock with a new native translator. Once the target no longer embeds ViaBedrock, do not describe any retained child runtime as "upstream stock". Call it an explicitly pinned **compatibility runtime**, record its source SHA, and treat its security and protocol maintenance as fork-owned.

## Use two baselines

Record both baselines before changing files:

1. **Java/Fabric baseline:** the target upstream ViaFabricPlus tag or exact `next/v5` commit. Take current Minecraft Java integration, ViaVersion APIs, Fabric/Loom configuration, mixin targets, resources, UI conventions, and non-Bedrock fixes from here.
2. **Bedrock subsystem baseline:** the last upstream commit before removal plus the current Peaks fork and dedicated ViaBedrock commit. Take only still-required Bedrock responsibilities from here.

Do not merge the pre-removal tree over the target tree or resolve whole files as `ours`. Use `git diff --name-status <pre-removal>..<removal>` to inventory what upstream deleted, `git log --left-right --cherry-pick` and `git range-diff` to inventory fork commits, then rebuild each retained responsibility on the target APIs.

## Build a responsibility ledger

For every removed or fork-modified Bedrock component, record:

| Responsibility | Target owner | Decision | Evidence and tests | Removal condition |
| --- | --- | --- | --- | --- |
| ViaBedrock protocol dependency and providers | ViaFabricPlus integration + ViaBedrock | keep/adapt/drop | | |
| route detection and connection pipeline | ViaFabricPlus | | | |
| Bedrock account/auth screens and storage | ViaFabricPlus | | | |
| LAN/Xbox discovery and worlds screen | ViaFabricPlus | | | |
| RakNet/NetherNet transports and native libraries | ViaFabricPlus | | | |
| Bedrock client-behavior mixins | ViaFabricPlus | | | |
| codecs, mappings, entities, inventory, crafting | ViaBedrock | | | |
| compatibility runtime for ordinary servers | ViaFabricPlus | | | |

Use `keep`, `drop`, `adapt`, or `quarantine`. A compile failure is not evidence that an old class should be restored. A behavior is retained only when the fork still needs it and a focused or real-route test names it.

## Re-home integration behind fork-owned seams

Prefer fork-owned packages and narrow interfaces over restoring upstream's deleted package layout wholesale. Keep explicit seams for:

- route identity and selected Bedrock wire protocol;
- translator/runtime selection;
- account and authentication context;
- RakNet or NetherNet channel preparation;
- LAN/Xbox discovery and join preparation;
- Bedrock-only screens, settings, resource packs, and client behavior hooks.

This keeps later upstream merges from confusing deliberate fork code with resurrected upstream code. Avoid reflection and child-classloader crossings where ordinary typed providers can work. Where a compatibility runtime must remain isolated, keep every class, packet type, tracker, and protocol object on its own classloader side and cross the boundary through primitive values or fork-owned DTOs.

Do not let removal collapse route isolation accidentally. During the transition, test maintained LAN/friends and any pinned ordinary-server compatibility runtime separately. If the compatibility runtime is retired, delete its route, artifact, tests, and documentation in the same release; do not silently send ordinary joins through the maintained route.

## Upgrade the Java/Fabric side safely

For each Minecraft Java or ViaFabricPlus release:

1. update Gradle/Loom, Java toolchain, mappings, Fabric Loader/API, ViaVersion/ViaBackwards, and Minecraft version coherently;
2. compile the clean upstream target and the fork after each responsibility group is restored;
3. inspect renamed client packets and field layouts, screen/menu slot numbering, connection states, and registry access;
4. verify every mixin target and invocation ordinal against target bytecode with `javap -c -p` and a development-client startup;
5. update access wideners, mixin JSON, service metadata, translations, icons, and mod metadata without overwriting upstream resource changes;
6. remove compatibility shims once all supported targets use the new API.

Treat compile-only success as insufficient. A Java packet rename can preserve compilation through a broad type while changing its field order or ownership model.

## Upgrade the Bedrock side independently

Follow `version-update-workflow.md` from a native ViaBedrock target-version commit whenever one exists. Keep wire serializers, mappings, packet handlers, entity translation, inventory, and crafting in ViaBedrock. Use ViaFabricPlus only for transport, route, UI, Minecraft-client integration, or a temporary verified seam that ViaBedrock cannot own.

After login succeeds, exercise the whole lifecycle: resource packs, spawn, terrain, time/weather, entity spawn/removal, movement, mounts, inventory, creative inventory, armor, crafting, death/respawn, dimension changes, and disconnect. New protocols commonly expose failures one packet at a time.

## Required transition matrix

Before releasing a v5-based build, record results for:

- ordinary Java server join, menu resources, and non-Bedrock ViaVersion routes;
- ordinary Bedrock/Geyser compatibility route, if retained;
- RakNet LAN, iOS/client-hosted NetherNet LAN, and Xbox-friend routes;
- server/client game-mode and time changes;
- boats: enter, forward/back, left/right steering, second passenger, dismount, re-enter;
- beds: every available colour, sleep, wake, both halves, and chunk reload;
- survival pickup/move/double-click and 2x2/3x3 crafting;
- creative catalog, cursor moves, armor equip/remove, drop, and mode changes;
- death/respawn in Overworld and Nether, entity cleanup, and dimension transitions;
- supported Linux/Windows/macOS native classifiers and final nested-JAR integrity.

Publish exact source SHAs and explicitly label untested routes. Do not tag or release while a required real-host regression is merely inferred from unit tests.
