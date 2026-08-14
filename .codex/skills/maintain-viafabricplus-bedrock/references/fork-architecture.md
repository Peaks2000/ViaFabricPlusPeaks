# Fork architecture

## Repository relationship

- Upstream source remote: `ViaVersion/ViaFabricPlus`.
- Dedicated Bedrock fork: maintained separately because upstream ViaFabricPlus is removing Bedrock support.
- ViaBedrock is resolved as a Gradle VCS dependency in `settings.gradle.kts`; the selected maintained branch is in `build.gradle.kts`. At the time this reference was written it is `peaks/1.26.40-fixes` from `Peaks2000/ViaBedrock`.
- Vendored `webrtc-java-m152test` artifacts under `vendor/maven/` provide the NetherNet/WebRTC runtime and native library needed by iOS-hosted LAN worlds.
- `StockViaBedrockRuntime` loads the embedded stock 1.26.30 ViaBedrock JAR in a child-first class loader. Ordinary server-list connections use that runtime; `BedrockProtocolCompatibility.prepareConnection` marks only dedicated LAN/friends connections for the maintained 1.26.40 route. This separation is intentional and must remain within one Prism instance.

## User-facing flow

- `ServerListScreen` opens `screen/impl/bedrock/BedrockWorldsScreen`.
- `BedrockWorldsScreen` combines LAN and Xbox-friend discovery, joins the selected Xbox MPSD session when necessary, selects the initial wire protocol, and performs one bounded adjacent-version retry when the host explicitly reports client-old/server-old.
- `util/bedrock/BedrockWorld` carries display metadata, discovered protocol metadata, source, and connection type.
- `util/network/ConnectionUtil` dispatches RakNet, direct NetherNet, Xbox JSON-RPC NetherNet, and LAN-discovery NetherNet connections.

## Discovery and Xbox services

- `BedrockWorldDiscovery` implements Xbox people/activity/session reads, MPSD join PUTs, RakNet broadcast discovery, and NetherNet LAN advertisements.
- Xbox connection selection prefers a public direct RakNet address, then a private direct address, while retaining NetherNet connection types.
- `NetherNetJsonRpcAddress` and `ViaFabricPlusNetherNetXboxRpcSignaling` cover Xbox friend signaling.
- `ViaFabricPlusNetherNetDiscoverySignaling` and `NetherNetDiscoveryPacketFixer` cover LAN discovery signaling, malformed announced lengths, candidate filtering, SDP normalization, and separately trickled ICE candidates.
- Connection/channel integration lives under `injection/mixin/core/connection/bedrock/`, especially `MixinNetherNetClientChannel`, `MixinNetherNetDiscovery`, `MixinConnection`, and the address/name-resolver mixins.

## Protocol and translation compatibility

- `BedrockProtocolCompatibility` is the single registry for accepted Bedrock wire protocols and game-version strings. Unknown NetherNet advertisements currently start at the ViaBedrock route version and may retry only after an explicit PlayStatus mismatch.
- `ProtocolTranslator.isBedrock()` covers both the maintained and isolated-stock routes for client behavior. Do not replace route selection with a single global target protocol.
- `MixinHandshakeStorage` substitutes the selected wire protocol into the Bedrock login payload without changing ViaVersion's route identity.
- `MixinSkinProvider` keeps the advertised game-version string aligned with that wire protocol.
- `MixinJoinPackets` suppresses the vanilla-looking version disconnect only while scheduling a verified adjacent-protocol retry.
- `MixinResourcePackPackets` carries the 1.26.40 resource-pack info count change from a little-endian unsigned short to an unsigned varint. It also rewrites `ResourcePackClientResponse`'s leading status from the old fixed int8 values `1-4` to the protocol-2168 unsigned-varint values `0-3`, while preserving the following lowercase response-name string.
- `MixinResourcePackLoadStateTracker` applies that response-status rewrite to the downloading path and changes its pack-ID array count from an unsigned little-endian short to an unsigned varint. Recheck both temporary synthetic-lambda mixins whenever ViaBedrock changes.
- `BedrockMappingDataFixer`, `MixinBedrockMappingData`, and `MixinBrewingStandBlockEntityRewriter` repair incomplete or inconsistent ViaBedrock mapping data. Corresponding regression tests live under `src/test/java/.../util/bedrock/`.

## Diagnostic signatures

- Xbox HTTP 400 mentioning `connectionRequiredForActiveMembers`: inspect `xboxJoinBody` and the stable connection GUID.
- `PlayStatus LoginFailed_ClientOld` / `LoginFailed_ServerOld`: inspect protocol discovery and the bounded retry; do not remove the check.
- `RESOURCE_PACKS_INFO`, `RESOURCE_PACK_PUSH`, `LongLE`, and an index overrun: verify the resource-pack array count type first.
- `ResourcePackClientResponse`, packet 8, and `wrong const value for member "Response Type"`: for protocol 2168 verify the leading status is an unsigned varint in `0-3`, the next field is the matching lowercase response-name string, and only `downloading` carries a varint-counted pack-ID array. Cloudburst's `ResourcePackClientResponseSerializer_v2168` is the known-good primary implementation.
- Java `ClientboundAddEntityPacket was larger than I expected` from `EntityPackets` line containing `wrapper.send`, especially with about 348 trailing bytes: inspect Bedrock `AddItemActor` packet 15. In 1.26.40 it is Cereal and its item is `NetworkItemStackDescriptor`; use `ItemRewriter.newItemType()`.
- `Received truncated synthetic ADD_PLAYER payload` with hundreds of bytes remaining: first verify that `AddPlayer` uses `ItemRewriter.newItemType()`. A legacy held-item decoder shifts all following fields and makes valid payloads look truncated.
- Repeated `Dropping packet with unknown PacketCompressionAlgorithm: 255`, followed by an inventory-only/invisible world: the generated enum stores `None` as uint16 `65535`, but the per-batch header carries its low byte `0xFF`. Normalize header `255` to `PacketCompressionAlgorithm.None` in `CompressionCodec`; otherwise uncompressed chunks and movement batches are discarded.
- `Packet violation warning: PacketMalformed`, `Violating Packet: Interact`, and `invalid enum value`: 1.26.40 uses `InteractPacketPayload_Action` values (`InteractUpdate = 4`, `OpenInventory = 6`), not the legacy `InteractPacket_Action` values (`2`, `4`). Check both `JoinPackets` and `InventoryPackets`.
- `InventoryScreen` crashes with `UserConnection.get(EntityTracker.class) is null` on an ordinary Bedrock server after stock-runtime isolation: the tracker belongs to the child classloader and cannot be queried with the maintained runtime's `EntityTracker.class`. Route the open-inventory interaction through `StockViaBedrockRuntime` using its packet, tracker, types, and protocol classes. Keep the maintained LAN path separate and null-safe, with `InteractPacketPayload_Action.OpenInventory` (`6`).
- `Packet violation warning: PacketMalformed`, `Violating Packet: PlayerAuthInputPacket`, `invalid enum value`, and `packetId: 144` immediately after the player briefly appears in an iOS world: mirror Cloudburst's `PlayerAuthInputSerializer_v2168`. Protocol 2168 replaces the old input bitmask with `bool(true)`, an unsigned-varint flag count, then each flag as a signed varint. The interaction model is a signed varint. Item-use, item-stack-request, block-actions, vehicle rotation, and predicted vehicle each use a Cereal field-presence `true` followed by a value-presence boolean. Block-action count is unsigned varint, and every action record carries action, position, and facing, including `StopDestroyBlock` with defaults.
- Entities and players render but all terrain is absent, the Java player falls, and there is no explicit chunk exception on protocol 2168: inspect the Cereal `LevelChunk` and `SubChunk` framing before mapping data. `LevelChunk` uses a regular unsigned-varint section count plus an optional signed-varint client request limit; its cache-metadata vector is always present, even when caching is disabled. `SubChunk` uses three fixed little-endian ints for its center, an unsigned-varint response count, and independent presence bytes for data, both heightmaps, and the blob ID. Reading the legacy widths can silently produce a zero response count instead of throwing.
- Unknown clientbound packet 337 is the optional Cereal `VoxelShapesPacket`. It can be ignored while unsupported and is not, by itself, evidence that configuration failed.
- SDP set-local/set-remote errors: inspect normalization before candidate networking.
- ICE candidate timeout with omitted candidates: inspect candidate filtering, interface address discovery, and trickle ordering.
- Missing biome/entity/sound mapping: repair mapping data separately from transport and packet framing.
