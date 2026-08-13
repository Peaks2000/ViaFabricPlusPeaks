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

Never add launcher logs or account files to Git. Sanitize XUIDs, session names, public IPs, tokens, and authorization headers in issues or commit messages.
