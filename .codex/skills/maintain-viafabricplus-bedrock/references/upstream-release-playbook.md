# Upstream release playbook

Use this playbook whenever Minecraft Bedrock, ViaBedrock, ViaFabricPlus, Minecraft Java, Fabric Loader/API, Loom, or ViaVersion changes. Preserve working fork-only features without allowing old compatibility overrides to shadow newer native implementations.

## Contents

1. Define the upgrade
2. Establish the native baseline
3. Audit the fork delta
4. Update ViaBedrock
5. Update ViaFabricPlus
6. Validate the compatibility matrix
7. Deliver and record evidence

## 1. Define the upgrade

Create a scratch upgrade ledger before editing. Do not commit it unless the repository already tracks this kind of record. Capture:

| Component | Current | Target | Source commit/tag |
| --- | --- | --- | --- |
| Minecraft Bedrock game/protocol | | | |
| ViaBedrock | | | |
| ViaFabricPlus | | | |
| Minecraft Java | | | |
| Fabric Loader/API and Loom | | | |
| ViaVersion/ViaBackwards | | | |

Classify the event:

- **Bedrock-only:** the wire protocol or game data changed; ViaFabricPlus may need only protocol selection and dependency updates.
- **ViaBedrock native support:** use the new native implementation as the baseline and retire equivalent fork codecs or mixins.
- **ViaFabricPlus/Minecraft/Fabric-only:** preserve Bedrock behavior while adapting integration points, mappings, mixin targets, and build configuration.
- **Combined:** update ViaBedrock and prove it independently before integrating the new ViaFabricPlus baseline.
- **Upstream Bedrock removal:** follow `viafabricplus-v5-migration.md`; take Java/Fabric changes from the post-removal target and Bedrock responsibilities from the pre-removal boundary plus maintained forks. Never merge the old subsystem wholesale over the new tree.

Use `gh` read-only against upstream repositories to inspect releases, branches, commits, and merged PRs. Use primary protocol sources when wire behavior changed. Record exact SHAs; branch names alone are not reproducible.

## 2. Establish the native baseline

Before porting old patches:

1. Fetch upstream references without pushing or rewriting user branches.
2. Identify the first upstream ViaBedrock commit that claims target Bedrock support and the upstream ViaFabricPlus tag/branch for the target Java version.
3. Build the clean target baseline where practical and record compilation, focused tests, and startup behavior.
4. Compare the baseline against the fork by commit and by behavior. Do not assume a similar class name means equivalent wire handling.

Prefer the implementation order:

1. native ViaBedrock target-version support;
2. narrowly retained fixes in `Peaks2000/ViaBedrock`;
3. fork-only transport/routing integration in `Peaks2000/ViaFabricPlusPeaks`;
4. a temporary ViaFabricPlus compatibility mixin only when the dependency cannot own the defect.

If upstream has removed Bedrock, "native baseline" means the current Java/Fabric architecture only. It does not mean the removed Bedrock feature has a new native replacement. Maintain two explicit baselines as described in `viafabricplus-v5-migration.md` and rebuild the retained Bedrock integration against current APIs.

Do not merge the previous `peaks/<version>-fixes` branch wholesale into a new native baseline. Start from the target baseline, then port justified deltas one at a time so obsolete serializers, generated enums, mappings, and packet layouts cannot survive silently.

## 3. Audit the fork delta

For every commit or logical patch absent from upstream, record:

| Field | Required evidence |
| --- | --- |
| Owner | ViaBedrock codec/translation, or ViaFabricPlus transport/integration |
| Status | `keep`, `drop`, `adapt`, or `quarantine` |
| Reason | First causal failure, schema difference, or fork-only requirement |
| Scope | Exact protocol, route, packet, platform, and version range |
| Test | Focused automated test and/or named real-host route |
| Removal condition | Upstream commit/release that makes the delta unnecessary |

Apply these rules:

- **Drop** code now provided natively, even if the fork copy still compiles.
- **Keep** deliberate fork features such as LAN/friends discovery, NetherNet authentication, platform natives, and stock-versus-maintained route isolation when upstream still lacks them.
- **Adapt** a retained feature to new APIs without carrying unrelated old implementation code.
- **Quarantine** an unverified patch outside the release path until evidence and a test exist; do not ship it enabled merely because it was present before.

Reject raw overrides. Never replace an entire packet pipeline, handler, class, or global route when the defect has a narrower owner. Never clear unread packet bytes to hide a schema mismatch, accept arbitrary protocol integers, catch and suppress a causal decoder exception, globally force the maintained route, or implement the same wire correction in both repositories. Prefer generated target-version types and native extension points over hard-coded copies.

Keep authoritative state above prediction. Local inventory/crafting prediction may bridge a proven host omission, but later authoritative Bedrock state must replace it. Scope any prediction to the observed route and packet pattern.

## 4. Update ViaBedrock

Create or select the maintained target branch only after locating native target support. Change protocol codecs, serializers, generated data, mappings, packet handlers, inventory models, and translator tests here.

For a new wire protocol, compare the last working and target schemas field-by-field using `version-update-workflow.md`. Add byte-level tests for every changed serializer and require zero trailing bytes. A successful login is only the first checkpoint; continue through resource packs, spawn, terrain, entities, movement, inventory, pickup, 2x2/3x3 crafting, and disconnect.

Commit ViaBedrock independently. During development, substitute the sibling checkout explicitly from ViaFabricPlus with `--include-build ../ViaBedrock`.

## 5. Update ViaFabricPlus

Integrate the target upstream ViaFabricPlus/Minecraft/Fabric baseline without choosing whole-file “ours” resolutions for files that contain both upstream and fork behavior. Resolve by responsibility:

- preserve dedicated LAN/friends discovery and its durable `ServerData` route marker;
- preserve authenticated RakNet/Xbox and self-signed client-hosted LAN identity separation;
- preserve the isolated stock runtime for ordinary server-list joins on 4.x; on a post-removal baseline, either retire it explicitly or pin and rename it as a fork-owned compatibility runtime;
- adapt mixins to verified target bytecode and delete mixins superseded by native ViaBedrock;
- update Gradle properties, Loom/Fabric dependencies, access wideners, mappings, and Java source compatibility together;
- point the VCS dependency at the maintained target ViaBedrock branch, never a permanent local path.

Search for synthetic-lambda mixins, invocation ordinals, reflection, child-classloader crossings, and copied protocol constants after every upstream update. These are high-risk integration seams. Confirm mixin targets with bytecode inspection and a development-client startup.

## 6. Validate the compatibility matrix

Run focused unit/wire tests first, then the full sibling build. After pushing ViaBedrock, run a clean `--refresh-dependencies` build without `--include-build` and verify the Gradle VCS checkout resolved the pushed SHA.

Test these routes separately; one success does not cover another:

| Route/feature | Minimum evidence |
| --- | --- |
| Ordinary Bedrock/Geyser server list | isolated stock runtime selected; join and inventory open work |
| RakNet LAN | authenticated account route; discovery and join |
| Client-hosted NetherNet LAN/iOS | local identity, signaling, login, spawn, movement |
| Xbox friend world | MPSD join, nonce/signaling, login, spawn |
| Core translation | resource packs, terrain, entities/skins, movement, disconnect |
| Mounts and block state | boat steering/dismount/re-entry; coloured bed sleep/wake and chunk reload |
| Inventory | natural block and mob pickup, drop/re-pickup, hotbar and screen refresh |
| Creative ownership | catalog selection, cursor moves, armor equip/remove, drop, and mode transition |
| Crafting | input placement, held left/right drag, output preview, collect, shift-craft, remainders in 2x2 and 3x3 |
| Regression | last supported Bedrock protocol still follows its intended route |

Do not declare target support from version negotiation alone. Record untested host routes explicitly rather than treating them as passed.

## 7. Deliver and record evidence

Follow `repository-coordination.md` exactly: push ViaBedrock first, build ViaFabricPlus from that remote commit, then synchronize the canonical skill, project mirror, and installed skill. Publish only the remapped main JAR and record its SHA-256, nested ViaBedrock identity, source commits, target versions, tests, real-host results, and known gaps.

Before final delivery, require clean tracked status in every repository, empty canonical/mirror/installed skill diffs, and exact local/upstream commit equality. Preserve unrelated logs and user work. Never push to upstream repositories or publish a release without explicit user authorization.
