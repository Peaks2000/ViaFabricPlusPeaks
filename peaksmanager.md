# PeaksManager releases for ViaFabricPlusPeaks

PeaksManager reads the JSON block below to find compatible public Fabric JARs. PeaksManager selects the highest matching mod version. If versions are equal, it uses the first matching entry. Use a Fabric version predicate for `minecraft`, the exact version from `fabric.mod.json` for `version`, and the SHA-256 of the GitHub release asset.

```json
{
  "schema": 1,
  "id": "viafabricplus",
  "releases": [
    {
      "minecraft": "26.3",
      "version": "5.1.3",
      "url": "https://github.com/Peaks2000/ViaFabricPlusPeaks/releases/download/5.1.3/ViaFabricPlusPeaks-5.1.3.jar",
      "sha256": "2ea5dd7d4ccbfac26e7082120ce6a8e8eb2fc5bb8fa4db8558eaa8814445afc6"
    },
    {
      "minecraft": "26.2",
      "version": "4.8.6",
      "url": "https://github.com/Peaks2000/ViaFabricPlusPeaks/releases/download/4.8.6/ViaFabricPlusPeaks-4.8.6.jar",
      "sha256": "c15fdb1a3ac98e59a7bab4a9c22dda4526218c71c280e2fc43d8401e56e01e5c"
    }
  ]
}
```
