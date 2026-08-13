# Fork architecture

## Repository relationship

- Upstream source remote: `ViaVersion/ViaFabricPlus`.
- Dedicated Bedrock fork: maintained separately because upstream ViaFabricPlus is removing Bedrock support.
- ViaBedrock is resolved as a Gradle VCS dependency in `settings.gradle.kts`; the selected branch is in `build.gradle.kts`. At the time this reference was written it is `update/1.26.40`.
- Vendored `webrtc-java-m152test` artifacts under `vendor/maven/` provide the NetherNet/WebRTC runtime and native library needed by iOS-hosted LAN worlds.

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
- `MixinHandshakeStorage` substitutes the selected wire protocol into the Bedrock login payload without changing ViaVersion's route identity.
- `MixinSkinProvider` keeps the advertised game-version string aligned with that wire protocol.
- `MixinJoinPackets` suppresses the vanilla-looking version disconnect only while scheduling a verified adjacent-protocol retry.
- `MixinResourcePackPackets` carries the 1.26.40 change from a little-endian unsigned-short resource-pack count to an unsigned varint. Recheck this temporary synthetic-lambda mixin whenever ViaBedrock changes.
- `BedrockMappingDataFixer`, `MixinBedrockMappingData`, and `MixinBrewingStandBlockEntityRewriter` repair incomplete or inconsistent ViaBedrock mapping data. Corresponding regression tests live under `src/test/java/.../util/bedrock/`.

## Diagnostic signatures

- Xbox HTTP 400 mentioning `connectionRequiredForActiveMembers`: inspect `xboxJoinBody` and the stable connection GUID.
- `PlayStatus LoginFailed_ClientOld` / `LoginFailed_ServerOld`: inspect protocol discovery and the bounded retry; do not remove the check.
- `RESOURCE_PACKS_INFO`, `RESOURCE_PACK_PUSH`, `LongLE`, and an index overrun: verify the resource-pack array count type first.
- SDP set-local/set-remote errors: inspect normalization before candidate networking.
- ICE candidate timeout with omitted candidates: inspect candidate filtering, interface address discovery, and trickle ordering.
- Missing biome/entity/sound mapping: repair mapping data separately from transport and packet framing.
