import json
from pathlib import Path
import re
import sys


manifest = Path("peaksmanager.md").read_text(encoding="utf-8")
block = re.search(r"^```json[ \t]*\n(.*?)^```[ \t]*$", manifest, re.MULTILINE | re.DOTALL)
if block is None:
    sys.exit("peaksmanager.md has no fenced JSON block")

stable_assets = {line.strip() for line in sys.stdin}
releases = json.loads(block[1])["releases"]
unlisted_assets = [release["url"] for release in releases if release["url"] not in stable_assets]
if unlisted_assets:
    sys.exit("PeaksManager requires published, stable GitHub assets:\n" + "\n".join(unlisted_assets))
print(f"PeaksManager lists {len(releases)} stable release assets")
