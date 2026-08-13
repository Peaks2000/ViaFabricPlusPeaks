# Bedrock version update workflow

## Establish the target

1. Capture the exact Bedrock game version and protocol from a RakNet pong, an Xbox session custom field, or a documented protocol table. Preserve the raw numeric protocol in the diagnostic notes.
2. Capture the first decoder/login error with packet name, packet ID, reader index, writer index, expected type, and ViaBedrock handler line.
3. Check the current ViaBedrock VCS branch and resolved commit. Inspect its source from the Gradle VCS checkout rather than assuming the published artifact matches.

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

## Decide where to patch

Prefer, in order:

1. update the ViaBedrock dependency to a revision that fully supports the target version;
2. contribute/fork ViaBedrock and select that revision;
3. add a small ViaFabricPlus mixin for a verified isolated defect while the dependency catches up.

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
