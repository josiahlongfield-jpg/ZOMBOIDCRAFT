"""Downloads chosen Sonniss GameAudioGDC sounds (royalty-free for games) from the archive.org mirror.

    python3 tools/audio/fetch_sonniss.py SELECTED.json OUT_DIR

SELECTED.json is a list of {item, pack, name, path, cat} rows picked from the mirror's file
listings (https://archive.org/metadata/<item>/files). Saves OUT_DIR/<cat>/<id>.mp3 and
OUT_DIR/manifest.json in the same shape fetch_freesound.py writes, so the slicer and pickers read
both. Keep OUT_DIR out of the public repo: the licence allows shipping the sounds inside the mod
but not passing the raw library on.
"""
import hashlib
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

UA = {"User-Agent": "ZomboidCraft-sound-collector"}


def main():
    rows = json.loads(Path(sys.argv[1]).read_text())
    out = Path(sys.argv[2])
    manifest_path = out / "manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    for r in rows:
        sid = "sx" + hashlib.sha1((r["item"] + r["path"]).encode()).hexdigest()[:8]
        dst = out / r["cat"] / f"{sid}.mp3"
        if sid in manifest and dst.exists():
            continue
        url = f"https://archive.org/download/{r['item']}/" + urllib.parse.quote(r["path"])
        for attempt in range(3):
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as resp:
                    data = resp.read()
                break
            except Exception as e:  # noqa: BLE001
                print("  retry", r["name"], e)
                time.sleep(3 + attempt * 5)
        else:
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_bytes(data)
        year = r["item"].split("-")[2]
        manifest[sid] = {"category": r["cat"], "file": f"{r['cat']}/{sid}.mp3", "user": r["pack"].split(" - ")[0].strip(),
                         "title": r["name"], "pack": r["pack"], "bundle": f"Sonniss GameAudioGDC {year}",
                         "page": f"https://archive.org/details/{r['item']}", "license": "Sonniss GDC royalty-free"}
        manifest_path.write_text(json.dumps(manifest, indent=1))
        print("ok", r["cat"], r["name"][:60])
        time.sleep(0.5)
    print(len(manifest), "sounds")


if __name__ == "__main__":
    main()
