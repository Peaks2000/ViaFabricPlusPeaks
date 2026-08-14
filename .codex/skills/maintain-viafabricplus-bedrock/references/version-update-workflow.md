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

Treat `Exterminate5573/ViaBedrock` PR #3 as a behavioral reference, not a merge target. It is a pre-1.26.40 experimental-inventory series whose container hierarchy, Java packet types, and legacy stack-request serializer were removed by the native ViaBedrock update. Port only still-missing concepts into `api/model/container` and the protocol-2168 Cereal request model: request-local optimistic stack IDs, actual item maximum stack sizes, multi-destination shift-crafting, number-key result placement, and crafting-remainder `Create` actions. Do not restore `experimental/model/container`, cherry-pick the series wholesale, or replace the mapped-ID plus discriminator serializer.

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
