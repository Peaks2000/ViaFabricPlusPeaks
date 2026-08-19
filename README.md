
## ViaFabricPlusPeaks is ViaFabricPlus with the ability to join Xbox Friends and LAN worlds. 
and some ClassiCube and BetaCraft fixes thanks to SC-MC-Mod-Dev (shunnoni_31072 on Discord)!
# What changed from ViaFabricPlus
    -In server lists added a menu for LAN worlds and XBOX friends
    -Adds a join anyway button to bypass Mojang Blacklisted servers
    -Fixed bedrock crafting
    -Fixed creative menu
    -Fixed many many inventory bugs
    -Working offhand 
    -Fixed a bug in ViaFabricPlus forcing a relogin for classicube every time you restart your game thanks to SC-MC-Mod-Dev (shunnoni_31072 on Discord)
    -Added Version detection to Betacraft thanks to SC-MC-Mod-Dev 
    -Added ClassiCube Search bar and ability to join hidden servers thanks to SC-MC-Mod-Dev (again)
credit 
inventory concepts adapted from [Exterminate5573/ViaBedrock PR #3](https://github.com/Exterminate5573/ViaBedrock/pull/3)

# How to Join a LAN world!
install the mod from releases then launch Minecraft click on "ViaFabricPlus" then in the bottom left you should see "Server Lists" click it then Click "Bedrock friends & LAN" then the LAN worlds should show up

# How to Join a Bedrock Friends' worlds!
install the mod from releases then launch Minecraft click on "ViaFabricPlus" then in the top right you should see settings scroll down and click Add bedrock account sign in with your Mircrosoft account then back out into the main ViaFabricPlus menu  you should see "Server Lists" click it then Click "Bedrock friends & LAN" then the Bedrock Friends' worlds should show up


<!--suppress HtmlDeprecatedAttribute -->
<div align="center">
  <img src="src/main/resources/assets/viafabricplus/icon.png" width="150" alt="ViaFabricPlusPeaks logo">
  <h1>ViaFabricPlusPeaks</h1>
  <a href="https://fabricmc.net"><img src="https://img.shields.io/badge/Mod%20Loader-Fabric-lightyellow?logo=fabric" alt="Mod Loader: Fabric"></a>
  <img src="https://img.shields.io/badge/Environment-Client-purple" alt="Environment: Client">
  <a href="https://discord.gg/viaversion"><img src="https://img.shields.io/discord/316206679014244363?color=0098DB&label=Discord&logo=discord&logoColor=0098DB" alt="Discord"></a><br/>
  <a href="https://modrinth.com/mod/viafabricpluspeaks"><img src="https://img.shields.io/badge/dynamic/json?color=158000&label=downloads&prefix=+%20&query=downloads&url=https://api.modrinth.com/v2/project/rIC2XJV4&logo=modrinth" alt="Modrinth Downloads"></a>
  <a href="https://curseforge.com/minecraft/mc-mods/viafabricplus"><img src="https://cf.way2muchnoise.eu/full_830604_downloads.svg" alt="CurseForge Downloads"></a>
  <a href="https://github.com/Peaks2000/ViaFabricPlusPeaks/actions/workflows/build.yml"><img src="https://github.com/Peaks2000/ViaFabricPlusPeaks/actions/workflows/build.yml/badge.svg" alt="Build Status"></a>

  <p><strong>Minecraft Fabric mod that allows you to join <em>every</em> Minecraft server version (Classic, Alpha, Beta, Release, April Fools, Bedrock)</strong></p>
</div>

## Bedrock-maintained fork

This is the dedicated [ViaFabricPlusBedrock](https://github.com/Peaks2000/ViaFabricPlusBedrock) fork. It retains and updates experimental Bedrock support as upstream ViaFabricPlus removes it, including RakNet LAN worlds, iOS NetherNet/WebRTC LAN worlds, and Xbox-friend discovery and session joins.

Bedrock networking changes quickly and support remains experimental. When reporting a connection problem, include the Bedrock host version, whether it is LAN or an Xbox friend, and the first relevant error from `latest.log`—but remove account tokens, XUIDs, public addresses, and session identifiers.

**ViaFabricPlus** is a Minecraft mod for [Fabric](https://fabricmc.net/) that builds on
the [ViaVersion plugin](https://github.com/ViaVersion/ViaVersion).
It lets you connect to servers from almost every Minecraft version while fixing issues that the original project
couldn't.

These fixes make older servers feel much closer to how they originally played, with improvements to movement, block and
entity interactions, graphics, and more. In short, it recreates the classic Minecraft experience on today's client.

## Important to know

- Works **only with the newest Minecraft client version**
- Runs **only on [Fabric](https://fabricmc.net/)**. (There won't be a Forge or NeoForge version)
- **Multiplayer only** – it does not affect singleplayer worlds
- **Clientside only** – it does not run on Multiplayer servers
- **No cross-version resource packs** – resource packs from older versions are not supported
- If you want to play using **older Minecraft clients**, you should use the
  original [ViaFabric](https://viaversion.com/fabric) instead.
  For a detailed comparison between the two projects, check out
  the [ViaFabric vs ViaFabricPlus section](https://github.com/ViaVersion/ViaFabric?tab=readme-ov-file#differences-with-viafabricplus).

## How to use

- [Step-by-step installation and usage guide](docs/USAGE.md)
- Found a Bedrock fork bug? Please report it on the [fork issue tracker](https://github.com/Peaks2000/ViaFabricPlusBedrock/issues)
- Got questions? Join the [ViaVersion Discord](https://discord.gg/viaversion)

### Supported Server versions

- Release (1.0.0–latest supported release*)
- Beta (b1.0 – b1.8.1)
- Alpha (a1.0.15 – a1.2.6)
- Classic (c0.0.15 – c0.30 including [CPE](https://wiki.vg/Classic_Protocol_Extension))
- April Fools (3D Shareware, 20w14infinite, 25w14craftmine)
- Combat Snapshots (Combat Test 8c)
- Bedrock Edition 1.26.40/1.26.50 (experimental; [some features are missing](https://github.com/RaphiMC/ViaBedrock#features))

*[Support for new Mojang releases is usually added within a few days](https://github.com/ViaVersion/ViaVersion#snapshot-support)

## For Developers & Contributors

- [Contribution guide & dev setup](CONTRIBUTING.md)
- [API docs & integration examples](docs/DEVELOPER_API.md)

## Credits

Huge thanks to the original [ViaFabricPlus contributors](https://github.com/ViaVersion/ViaFabricPlus/graphs/contributors)
and the [ViaFabricPlusPeaks contributors](https://github.com/Peaks2000/ViaFabricPlusPeaks/graphs/contributors) who made
this project possible.

Fork-specific provenance and redistributed notices are documented in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Disclaimer

We cannot guarantee this mod will be allowed on every server.
Some servers may treat it as suspicious and flag it with anti-cheat plugins.
**Use responsibly and at your own risk!**
