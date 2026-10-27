#!/usr/bin/env python3
"""Writes the block tag that makes city blocks stop rain, snow and falling things.

Minecraft 26.3 builds its MOTION_BLOCKING heightmaps (where rain lands and its sound plays, where
mobs and lightning come down) from the blocks_motion_in_heightmap tags instead of from each
block's shape, so a block outside them lets rain fall through to the floor below. This lists the
city blocks that the old rule counted as solid: anything with collision whose box averages at
least 0.73 of a block per side or is a full block tall (walls, floors, roofs, windows, doors).

    python3 tools/city/heightmap_tags.py

Reads the generated CitySpecs.java; gen_blocks.py runs it after writing that.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SPECS = ROOT / "src/main/java/com/zomboidcraft/world/blocks/CitySpecs.java"
OUT = ROOT / "src/main/resources/data/zomboidcraft/tags/block/city_blocks_motion.json"
# thin, but they're roofs: rain should drum on them and stay off whoever shelters underneath (and the
# Glass Line's roof glass holds the harbour back: water flows through anything left out)
SHELTER = ("awning", "water_tank_top", "gl_crown")
LINE = re.compile(r'\s*s\("([a-z0-9_]+)", (\w+), ([\w |]+), \d+, SoundType\.\w+, [\d.]+f(?:, ([-\d., ]+))?\);')


def solid(kind, flags, boxes):
    if "DECOR" in flags:
        return False  # no collision
    if kind in ("DOOR", "TRAPDOOR"):
        return True  # a door's 3/16 slab just makes the cut, as in vanilla
    if not boxes:
        return False
    v = [float(x) / 16 for x in boxes.split(",")]
    lo = [min(v[i::6]) for i in range(3)]
    hi = [max(v[i + 3::6]) for i in range(3)]
    size = [h - l for l, h in zip(lo, hi)]
    return sum(size) / 3 >= 0.7291666666666666 or size[1] >= 1.0


def write():
    names = []
    for line in SPECS.read_text().splitlines():
        m = LINE.match(line)
        if m and (solid(m.group(2), m.group(3), m.group(4)) or m.group(1).startswith(SHELTER)):
            names.append(f"zomboidcraft:{m.group(1)}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"replace": False, "values": names}, indent=2) + "\n")
    return len(names)


if __name__ == "__main__":
    print(f"{write()} solid city blocks in {OUT.relative_to(ROOT)}")
