# Bedrock version update workflow

## Establish the target

1. Capture the exact Bedrock game version and protocol from a RakNet pong, an Xbox session custom field, or a documented protocol table. Preserve the raw numeric protocol in the diagnostic notes.
2. Capture the first decoder/login error with packet name, packet ID, reader index, writer index, expected type, and ViaBedrock handler line.
3. Check the current ViaBedrock VCS branch and resolved commit. Inspect its source from the Gradle VCS checkout rather than assuming the published artifact matches.

For protocol 2168/1.26.40, use ViaBedrock's merged `Update to 1.26.40` PR #389 and its `update/1.26.40` branch as the official baseline. Compare the maintained `peaks/1.26.40-fixes` branch from that merge commit forward; do not replace already-native support with a second parallel implementation.

Do not trust a changelog filename more than its body. Mojang's current `changelog_2168_07_07_26.md` header says protocol 2169. Its LAN-secret requirement and packet list must be gated as 2169 evidence unless a protocol-2168 host trace independently proves otherwise. Keep transport identity binding, game-login authentication, and a future LAN-secret field as separate hypotheses.

## Compare schemas

Use primary technical sources when browsing: Mojang/Microsoft documentation where it specifies the field, the checked-out ViaBedrock source, or the canonical protocol-data repository used by the implementation. Compare the last working and target schemas field by field.

For each changed packet record:

- packet ID and direction;
- field order;
- integer encoding (fixed little-endian, signed varint, unsigned varint);
- optional/presence conditions;
- array count encoding;
- enum additions and changed meanings;
- trailing fields and version gates.

Do not infer a schema change solely from a buffer-overrun offset. Use the offset to identify the first suspect field, then verify it against both schemas.

Check Mojang's release changelog for packets converted to Cereal. Cereal conversions are wire-format changes even when the packet ID and conceptual fields are unchanged. Audit nested types too: in protocol 2168/1.26.40, `AddActor`, `AddItemActor`, and `AddPlayer` use tagged entity-data payloads, while `AddItemActor` and `AddPlayer` carry `NetworkItemStackDescriptor` rather than the legacy Bedrock item encoding. A wrong nested item decoder can appear to succeed and leave hundreds of bytes unread.

Audit generated enum storage width separately from its use on the wire. `PacketCompressionAlgorithm.None` is `65535` in the uint16 `NetworkSettings` field but is `255` in the uint8 per-batch header. Likewise, do not keep a hand-written legacy enum when the target protocol generated a replacement with new values, as happened with `InteractPacketPayload_Action` in 1.26.40.

For protocol 2168 `PlayerAuthInputPacket`, use Cloudburst's `PlayerAuthInputSerializer_v2168` as the executable reference; the older Mojang graph and the optional JSON view do not expose the complete Cereal framing. After head yaw, write a field-presence `true`, an unsigned-varint input-flag count, and every flag as a signed varint; do not write the pre-2168 bitmask. Write input mode and play mode as unsigned varints and interaction model as a signed varint. Item-use, item-stack-request, block-actions, vehicle rotation, and predicted vehicle each have two booleans: field presence, then value presence. Block-action count is unsigned varint, and each record always includes action, position, and facing; use default position/facing for `StopDestroyBlock`.

For protocol 2168 crafting, parse the leading shaped and shapeless arrays in `CraftingData` so Java result-slot clicks can recover the Bedrock recipe network ID. A manual craft request is ordered as `CraftRecipe`, `CraftResultsDeprecated`, one `Consume` per matched grid input, then `Take` from `CreatedOutputContainer` slot 50. Cereal writes each mapped action ID as an unsigned varint and then a separate generated action-discriminator byte: mapped IDs 10 and 17 pair with discriminator bytes 12 and 19 because the deprecated item-container actions were removed only from the mapping. Encode every craft result as a nonempty `ItemStackRequestNetworkItemInstanceDescriptor`, including the descriptor discriminator and user-data length. Enforce Mojang's request constraints before sending: negative odd request ID, 1-100 actions, transfer counts 1-64, nonzero recipe ID, nonempty craft results, and craft counts 1-255. Add a byte-level regression test that decodes the complete request and finishes with zero readable bytes.

Treat crafting input placement and crafting result collection as separate operations. Java's player inventory screen exposes output slot 0 and a 2x2 input grid in slots 1-4; Bedrock stores that grid in `PlayerOnlyUI`/HUD slots 28-31 and exposes it through `CraftingInputContainer`. Java's crafting-table screen exposes output slot 0 and a 3x3 grid in slots 1-9; Bedrock stores that grid in HUD slots 32-40. Both screens use HUD slot 50 for the output. Translate ordinary pickup, place, swap, and quick-move clicks on the Java input slots through those Bedrock slot numbers instead of rejecting them as result-only recipe actions. Add a byte-level placement test that verifies the mapped Place action, discriminator, full container names, slot numbers, stack network IDs, and zero trailing bytes.

Do not wait exclusively for a server inventory correction to populate output slot 50. Client-hosted 1.26.40 worlds can accept an input `ItemStackRequest` while sending no crafting-output preview because the native Bedrock client predicts recipes locally from `CraftingData`. Retain the parsed shaped and shapeless recipes, match the complete 2x2 or 3x3 HUD grid after each accepted input click, and copy the first matching recipe output into HUD slot 50. Match shaped recipes at every valid offset, honor their symmetry flag, require all cells outside the recipe footprint to be empty, support wildcard auxiliary value 32767, and resolve item-tag ingredients through the Bedrock item-tag mappings. Match shapeless recipes one-to-one so a grid slot cannot satisfy multiple ingredients. If nothing matches, clear slot 50.

After predicting the output, send a full Java `CONTAINER_SET_CONTENT` for the open inventory or crafting table. This publishes the input and result atomically and prevents intermittent Java-side rollback while the Bedrock request is pending. Keep the request tracker's snapshot and rejection path authoritative: failed Bedrock responses restore the pre-request state, and later authoritative container packets replace prediction. Never manufacture a recipe result from Java mappings alone, and do not send `CraftRecipe` until the user actually clicks the result slot.

Java held-button distribution arrives as a stateful `QUICK_CRAFT` sequence, not ordinary repeated clicks. Decode the button's low two bits as stage (`0` start, `1` add hovered slot, `2` finish) and the next two bits as type (`0` evenly distributed left-drag, `1` one-per-slot right-drag, `2` creative middle-drag). Accept start/add packets without sending fallback container refreshes, retain unique eligible Java slots, and emit one Bedrock request only at finish. Divide the original cursor count by the selected-slot count for left-drag and place one for right-drag, respecting stack capacity. After every `Place`, use the negative request ID as the next cursor stack network ID so later actions in the same request reference the optimistic stack. Snapshot every touched container, recalculate crafting output if a grid changed, and add a multi-action byte-level regression test with zero trailing bytes.

Protocol 2168 converts `CreativeContent` to Cereal. Read each group's category as a generated `uint8`, then its string and `NetworkItemInstanceDescriptorData` icon; do not reuse the fixed little-endian category or `NetworkItemStackDescriptorData` inventory-slot reader. Each creative item carries its unsigned-varint creative network ID, the same item-instance descriptor, then an unsigned-varint group ID. The item-instance descriptor uses signed-varint item and block runtime IDs and has no stack-network-ID presence flag. Runtime ID zero still carries count, auxiliary value, block runtime ID, and user-data length, so never return early for air; an empty group icon otherwise shifts the rest of the packet and commonly fails later at the user-data marker. Keep read and write layouts symmetric and add byte-level tests proving an empty instance consumes and emits the complete record with zero trailing bytes.

Creative inventory clicks require the server's `CreativeContent` catalog; do not translate Java items from static mappings and guess a creative network ID. Decode and cache each advertised creative network ID with its Bedrock item and translated Java item, prefer an exact Java item match, and use the identifier only as a fallback for components the translator does not yet preserve. A nonempty Java `SET_CREATIVE_MODE_SLOT` becomes `CraftCreative`, `CraftResultsDeprecated`, optional `Destroy` for the old destination stack, then `Take` from `CreatedOutputContainer` slot 50; Java slot `-1` ends with `Drop`. An empty destination update becomes `Destroy`. In protocol 2168, `CraftCreative` uses mapped ID 12 plus discriminator 14 and carries the creative network ID as an unsigned varint followed by the requested-craft byte. Track snapshots, assign the negative request ID to the optimistic destination stack, restore failures, reject creative actions when the tracked Java mode is not creative, and cover the mapped ID, discriminator, fields, and zero trailing bytes in a wire test.

Treat a live game-mode transition as local player state, player-list state, and abilities state together. `UpdatePlayerGameType` can name the local unique entity ID even when that player is absent from the Bedrock player-list cache, so resolve the local player directly before discarding an unknown entry. For the local player, update the effective mode, flush Java `PLAYER_INFO_UPDATE`, then send `CHANGE_GAME_MODE`, then resend `PLAYER_ABILITIES`. This order lets an already-open Java creative inventory replace itself with the survival inventory immediately. Keep remote player updates on the ordinary player-list path, and do not accept stale creative-slot packets after the local mode becomes survival.

If the creative screen opens but cannot create items, or creative mode retains survival hearts, search the client log for `CREATIVE_CONTENT`, `SET_PLAYER_GAME_TYPE`, or `UPDATE_ABILITIES outside PLAY state`. Resource-pack and mapping work inside `START_GAME` can be expensive enough that later Bedrock packets overtake its Java configuration transition. Do not add those packets to the configuration whitelist and emit Java play packets in the wrong protocol state. Copy the unread payload of stateful packets into a per-connection, packet-count- and byte-bounded queue; after `START_GAME` has installed the item rewriter and entity tracker, sent Java login, and changed the server state to play, replay the packets through the normal ViaBedrock pipeline in original order. Include `INVENTORY_CONTENT`, `INVENTORY_SLOT`, `PLAYER_HOTBAR`, and `PLAYER_LIST` when the log shows the same race so the initial inventory and local-player identity are not lost. Preserve the existing ignore behavior for unrelated pre-start packets, log queue overflow once, and require a real client restart before testing a replaced Fabric JAR.

Treat `Exterminate5573/ViaBedrock` PR #3 as a behavioral reference, not a merge target. It is a pre-1.26.40 experimental-inventory series whose container hierarchy, Java packet types, and legacy stack-request serializer were removed by the native ViaBedrock update. Port only still-missing concepts into `api/model/container` and the protocol-2168 Cereal request model: request-local optimistic stack IDs, actual item maximum stack sizes, multi-destination shift-crafting, number-key result placement, and crafting-remainder `Create` actions. Do not restore `experimental/model/container`, cherry-pick the series wholesale, or replace the mapped-ID plus discriminator serializer.

For inventory pickups, distinguish authoritative corrections from Bedrock client prediction. Trace `InventorySlot`, `InventoryContent`, and clientbound `InventoryTransaction` around `TakeItemActor`. When a correction exists, update the tracked container first and send a fresh Java combined-player `CONTAINER_SET_CONTENT` for inventory, armor, or offhand; this keeps the hotbar and every player-inventory view synchronized even while another screen is open. Cancel the mapped slot wrapper instead of re-typing it, because the original mapping can remain active in part of the pipeline and make the Java client discard the full-content payload. Decode normal clientbound inventory transactions outside the experimental feature gate, apply their container actions once, coalesce one refresh per corrected container, and preserve the specialized cursor, crafting-table, and non-player-container paths.

`TakeItemActor` alone identifies only the collected and collecting actors, with no item descriptor or destination slot. Do not guess from it alone. However, protocol 2168 `AddItemActor` supplies a `NetworkItemStackDescriptor`; retain a copy on a dedicated tracked item entity. If a real-host trace proves that natural block and mob drops produce a local-player `TakeItemActor` but no authoritative slot, content, or transaction packet, mirror Bedrock's client prediction from the retained actor stack. Fill compatible stacks before empty slots, prefer the selected hotbar slot only when merging a compatible stack, respect `ItemRewriter.maxStackSize`, preserve existing inventory stack network IDs when merging, and copy the actor stack when filling an empty slot. Change the tracked array without triggering serverbound held-item/equipment side effects. Decrement the retained actor amount by the inserted amount so a duplicate or partial collection cannot duplicate items, and publish the combined player inventory after a short coalescing delay. Any later authoritative packet must overwrite this prediction. If the actor or descriptor is missing, skip prediction.

Use placement as a differential test, not a diagnosis by itself. If placement makes the item appear and an authoritative inventory correction preceded `TakeItemActor`, the tracker likely contains the item and the Java refresh path failed. If placement is the first event that causes any authoritative correction, the Bedrock host expected client-side pickup prediction and the tracker was stale. Compare timestamps for the actor event, predicted refresh, and every inventory packet. Treat `InventorySlot outside PLAY state` during initial configuration as a separate baseline-loss problem; inspect it if predicted destination selection is wrong for an already-populated inventory.

For protocol 2168 terrain, compare against Cloudburst's `LevelChunkSerializer_v2168` and `SubChunkSerializer_v2168`. `LevelChunk` no longer uses negative subchunk-count sentinels: read the unsigned-varint section count, optional request-limit presence and signed-varint value, cache-enabled flag, always-present cache-metadata vector, then data. `SubChunk` reads its center as fixed little-endian x/y/z ints and its response count as an unsigned varint. Each response independently carries presence bytes for data, heightmap data, render-heightmap data, and blob ID; consume present values regardless of the result enum or cache-enabled flag. A stale fixed-width response count can decode as zero and leave the world invisible without logging a decoder exception.

Request-mode `LevelChunk` packets commonly embed zero sections and provide the usable vertical range through `requestSectionCount`. Initialize the chunk tracker's mergeable section prefix with `requestSubChunks ? requestSectionCount : sectionCount`; otherwise every valid `SubChunk` response targets a section constructed as already complete and fails with `This section already has been merged with another section`. Preserve the pending state so block updates queue until the response arrives. Do not suppress the merge exception or treat the first response as a network duplicate.

## Decide where to patch

Prefer, in order:

1. update the ViaBedrock dependency to a revision that fully supports the target version;
2. patch the selected dedicated ViaBedrock fork for remaining codec, serializer, packet-handler, or inventory defects;
3. add a small ViaFabricPlus mixin only for a verified integration defect the dependency cannot own.

When ViaBedrock gains native target-version support, establish that implementation as the baseline, retain only independently justified fork fixes, and move any equivalent temporary ViaFabricPlus protocol mixins into ViaBedrock source. Never carry the same wire override in both repositories.

If a mixin changes a `PacketWrapper.read(Type)` argument, document the invocation ordinal and confirm the surrounding bytecode with `javap -c -p`. Start the development client so Mixin validates the target at runtime.

## Extend protocol selection

Update together:

- protocol constants and game-version strings in `BedrockProtocolCompatibility`;
- `protocolForGameVersion` normalization;
- the ordered adjacent retry relationship;
- handshake and skin-provider behavior;
- tests for exact, unknown, lower, and higher versions.

Never use the NetherNet LAN advertisement revision as the game protocol. Unknown versions must fall back to a known codec, and only an explicit Bedrock PlayStatus mismatch may trigger one bounded retry. If the target packet layout is unsupported, add/update the codec before declaring the new protocol supported.

## Mapping-data audit

After login framing works, run the mapping tests and inspect ViaBedrock resources for:

- biome identifiers and numeric IDs;
- block states and item runtime IDs;
- entity identifier NBT;
- level-sound event keys and defaults;
- container/block-entity schema changes.

Keep mapping fixes deterministic and idempotent. A missing mapping warning can be survivable; a mapping exception during initialization is not.

## Build evidence

Record:

- commands run and whether tests are configured or skipped;
- development-client startup result;
- final artifact name, size, and SHA-256;
- commit and remote branch;
- real-host result for LAN RakNet, LAN NetherNet/iOS, and Xbox friend connection separately.

For this fork, also verify an ordinary server-list Bedrock join still selects the isolated stock route. Do not treat a successful maintained LAN join as coverage for the stock route, or vice versa.

Never add launcher logs or account files to Git. Sanitize XUIDs, session names, public IPs, tokens, and authorization headers in issues or commit messages.
