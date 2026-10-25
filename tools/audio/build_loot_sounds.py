"""Builds the looting and item sounds from Josiah's picks on the Dead Air Loot Picker (round 4).

    python3 tools/audio/build_loot_sounds.py RAW_DIR[,RAW_DIR...]

Same inputs as build_zombie_sounds.py. Writes assets/zomboidcraft/sounds/loot/... and item/..., the
loot.* and item.* entries in assets/zomboidcraft/sounds.json and the looting section of CREDITS.md.
Josiah: containers only need an opening and closing sound, nothing while you look through them, so
each container event is a one-shot played on open and again on close.
"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote

from build_zombie_sounds import RES, ROOT, cut, level

# event: (sound folder, peak dB, attenuation distance, picks); picks Josiah kept, 2026-10-25
PICKS = {
    "loot.drawer": ("loot/drawer", -6.0, 16, [
        ("108268", 17.86, 0.41), ("581404", 7.48, 1.68), ("sx9ecd4013", 9.7, 0.87), ("sx3ae1bf01", 5.02, 0.84),
        ("sx69f39f48", 11.79, 0.68), ("sxab7194ed", 0.0, 1.01), ("94986", 6.63, 0.92), ("sx4566efe7", 17.49, 0.83),
        ("185838", 2.12, 1.28), ("185842", 2.23, 1.29)]),
    "loot.cupboard": ("loot/cupboard", -6.0, 16, [("426392", 0.0, 1.18), ("272429", 0.52, 0.52)]),
    "loot.fridge": ("loot/fridge", -6.0, 16, [
        ("513340", 8.92, 0.58), ("265024", 13.06, 2.09), ("271454", 5.83, 0.76), ("183531", 0.0, 1.56),
        ("788308", 6.57, 0.59)]),
    "loot.register": ("loot/register", -4.0, 24, [("584168", 1.34, 2.0), ("217173", 0.0, 2.0)]),
    "loot.box": ("loot/box", -6.0, 16, [("364740", 0.0, 1.13), ("135014", 0.0, 0.54)]),
    "loot.safe": ("loot/safe", -4.0, 16, [("262277", 9.22, 0.93)]),
    "loot.shelf": ("loot/shelf", -9.0, 12, [("860292", 0.35, 1.09)]),
    "item.eat": ("item/eat", -8.0, 12, [
        ("235905", 0.0, 3.0), ("sx9001a7b9", 0.0, 0.69), ("275015", 0.0, 1.01), ("202145", 26.3, 1.08),
        ("256311", 3.31, 0.76)]),
    "item.drink": ("item/drink", -7.0, 12, [
        ("445239", 0.0, 0.82), ("418988", 0.0, 1.15), ("377991", 1.47, 0.77), ("56270", 1.42, 0.68)]),
    "item.bandage": ("item/bandage", -6.0, 12, [("sx9e6a87da", 0.0, 0.74), ("sx759c56ed", 9.28, 0.56), ("718668", 2.45, 0.73)]),
    "item.hammer.nail": ("item/hammer_nail", -2.0, 32, [("sx270f59df", 1.45, 1.25), ("17012", 0.0, 1.5), ("394891", 3.71, 1.5)]),
    "item.bag": ("item/bag", -6.0, 12, [("sx9839ee0a", 0.0, 0.49), ("531499", 0.0, 0.84)]),
}


def build(picks_by_event, heading, script):
    """Cuts and levels each event's picks, registers them in sounds.json and writes the CREDITS.md section."""
    manifest, raw = {}, {}
    for d in sys.argv[1].split(","):
        for sid, info in json.loads((Path(d) / "manifest.json").read_text()).items():
            manifest[sid], raw[sid] = info, Path(d)
    sounds = RES / "zomboidcraft/sounds"
    ours_path = RES / "zomboidcraft/sounds.json"
    ours = json.loads(ours_path.read_text())
    used = {}
    for event, (folder, peak, dist, picks) in picks_by_event.items():
        out = sounds / folder
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*.ogg"):
            old.unlink()
        for n, pick in enumerate(picks, 1):
            tmp = out / f"{n}.wav"
            cut(raw, manifest, pick, tmp)
            level(tmp, out / f"{n}.ogg", peak)
            used.setdefault(pick[0], set()).add(event)
        ours[event] = {"sounds": [{"name": f"zomboidcraft:{folder}/{n}", "attenuation_distance": dist}
                                 for n in range(1, len(picks) + 1)]}
    ours_path.write_text(json.dumps(ours, indent=2) + "\n")

    rows = []
    for sid, events in used.items():
        info = manifest[sid]
        user = unquote(info["user"])
        title = info["title"].removesuffix(f" by {user}")
        if "bundle" in info:
            rows.append(f"| {', '.join(sorted(events))} | {title} ({info['pack']}) | {info['bundle']} | Sonniss royalty-free |")
        else:
            rows.append(f"| {', '.join(sorted(events))} | [{title}]({info['page']}) | {user} | CC0 1.0 |")
    section = (f"## {heading}\n\nCC0 (public domain) recordings from [Freesound](https://freesound.org) and "
               "royalty-free sounds from the [Sonniss GameAudioGDC](https://sonniss.com/gameaudiogdc) bundles, picked by ear, "
               f"cut, mixed to mono and levelled by tools/audio/{script}.\n\n"
               "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + "\n".join(rows) + "\n")
    credits = ROOT / "CREDITS.md"
    text = credits.read_text()
    text = re.sub(rf"\n## {heading}\n.*?(?=\n## |\Z)", "", text, flags=re.S).rstrip("\n") + "\n\n" + section
    credits.write_text(text)
    print({e: len(p[3]) for e, p in picks_by_event.items()})


if __name__ == "__main__":
    build(PICKS, "Looting and item sounds", "build_loot_sounds.py")
