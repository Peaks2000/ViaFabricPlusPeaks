# PeaksManager releases for ViaFabricPlusPeaks

PeaksManager reads this file to find public Fabric JARs. It selects the most recently published release whose `minecraft` predicate matches the client. Use the exact mod `version` from `fabric.mod.json`, the GitHub release's `published_at` timestamp, the JAR download URL, and its SHA-256. Keep earlier entries so PeaksManager can recognize installed versions.

```json
{
  "schema": 1,
  "id": "viafabricplus",
  "releases": [
    {
      "minecraft": "26.3",
      "version": "5.1.3-peaks.1",
      "publishedAt": "2026-11-12T20:16:58Z",
      "url": "https://github.com/Peaks2000/ViaFabricPlusPeaks/releases/download/5.1.3-peaks.1/ViaFabricPlusPeaks-5.1.3-peaks.1.jar",
      "sha256": "ed795a7f41d5ce9275477ade28f64f526e2c33d9617808fac360c68d183cd9a5"
    },
    {
      "minecraft": "26.3",
      "version": "5.1.3",
      "publishedAt": "2026-11-11T23:38:57Z",
      "url": "https://github.com/Peaks2000/ViaFabricPlusPeaks/releases/download/5.1.3/ViaFabricPlusPeaks-5.1.3.jar",
      "sha256": "2ea5dd7d4ccbfac26e7082120ce6a8e8eb2fc5bb8fa4db8558eaa8814445afc6"
    },
    {
      "minecraft": "26.2",
      "version": "4.8.6",
      "publishedAt": "2026-09-10T18:11:12Z",
      "url": "https://github.com/Peaks2000/ViaFabricPlusPeaks/releases/download/4.8.6/ViaFabricPlusPeaks-4.8.6.jar",
      "sha256": "c15fdb1a3ac98e59a7bab4a9c22dda4526218c71c280e2fc43d8401e56e01e5c"
    }
  ]
}
```
