"""Coloured light for the mod's lamps under Photon (Josiah 2026-11-06: "any coloured lights id want
to have that colour projected onto the surroundings"). Photon colours a light by the block id its
shaders/block.properties gives the block. It has sixteen slots for modded lights (block.10064 to
10079: white, light gray, gray, black, brown, red, orange, yellow, lime, green, cyan, light blue,
blue, purple, magenta, pink); a block in none of them casts the plain blocklight colour (his is a
sodium amber). So every coloured lamp of ours goes in the slot its glow is nearest.

    python3 tools/photon/photon_lights.py                 writes tools/photon/zomboidcraft_lights.txt
    python3 tools/photon/photon_lights.py PACK.zip OUT.zip  writes a copy of the pack with ours added

The glow colour is the emitting pixels of the block's textures (LabPBR _s alpha below 255),
weighted by how strongly they emit. White and warm white lamps stay out on purpose: they keep his
blocklight colour, as the street lamps always have. Flickering and signal lamps (a lit state)
are left out too: their dark state would light.

The patch only appends ids to the slots' lines, so it needs nothing outside Python's standard
library. An older Photon without the modded slots gets the nearest vanilla light instead.
"""
import colorsys
import json
import re
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).parent
ROOT = HERE.parent.parent
TEX = ROOT / "src/main/resources/assets/zomboidcraft/textures/block/city"
LIST = HERE / "zomboidcraft_lights.txt"
NS = "zomboidcraft"

# slot: (Photon's modded id, the vanilla id an older pack falls back on)
SLOTS = {
    "red": (10069, 10038), "orange": (10070, 10040), "yellow": (10071, 10051), "lime": (10072, 10052),
    "green": (10073, 10052), "cyan": (10074, 10043), "light_blue": (10075, 10043), "blue": (10076, 10044),
    "purple": (10077, 10053), "magenta": (10078, 10047), "pink": (10079, 10053),
}


def glow(names):
    """The emission-weighted colour of the textures; for a lamp none of whose textures is marked
    emitting (old neon, drawn bright instead), the colour of its brightest pixels; None if blank."""
    import numpy as np
    from PIL import Image
    maps = []
    for n in set(names):
        c, s = TEX / f"{n}.png", TEX / f"{n}_s.png"
        if c.exists() and s.exists():
            a = np.asarray(Image.open(c).convert("RGB"), np.float64).reshape(-1, 3)
            e = np.asarray(Image.open(s).convert("RGBA"), np.float64)[..., 3].reshape(-1)
            maps.append((a, np.where(e < 255, e / 254, 0)))
    if not maps:
        return None
    if not any(w.any() for _, w in maps):
        maps = [(a, np.where(a.sum(1) >= np.percentile(a.sum(1), 85), 1.0, 0)) for a, _ in maps]
    a = np.concatenate([m[0] for m in maps])
    w = np.concatenate([m[1] for m in maps])
    if w.sum() < 1:
        return None
    # neon and lit signs burn white-hot down the tube with the colour round it: the colour is
    # what the saturated pixels say, when there are enough of them
    hi, lo = a.max(1), a.min(1)
    sat = np.where(hi > 0, (hi - lo) / np.maximum(hi, 1), 0)
    tinted = w * (sat >= 0.3)
    if tinted.sum() >= 0.12 * w.sum():
        w = tinted
    return (a * w[:, None]).sum(0) / w.sum()


def textures(spec):
    out = []
    if isinstance(spec.get("tex"), str):
        out.append(spec["tex"])
    elif isinstance(spec.get("tex"), dict):
        out += list(spec["tex"].values())
    for b in spec.get("boxes", []):
        t = b[6] if len(b) > 6 else None
        out += [t] if isinstance(t, str) else list(t.values()) if isinstance(t, dict) else []
    return [t for t in out if isinstance(t, str)]


def slot(rgb):
    """The modded slot nearest a glow colour, or None for white and warm white."""
    r, g, b = (float(v) / 255 for v in rgb)
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    deg = h * 360
    if s < 0.35 or (15 <= deg < 60 and s < 0.7):
        return None                       # white, or a warm tungsten white: the blocklight colour
    for top, name in ((12, "red"), (38, "orange"), (65, "yellow"), (95, "lime"), (150, "green"),
                      (190, "cyan"), (215, "light_blue"), (250, "blue"), (285, "purple"), (320, "magenta"),
                      (345, "pink"), (361, "red")):
        if deg < top:
            return name
    return "red"


# lamps whose glow the textures can't tell: the threshold bar shows green to the approach (its
# red face looks back up the runway)
FIXED = {"af_light_thr": "green"}


def classify():
    blocks = json.loads((ROOT / "tools/city/blocks.json").read_text())["blocks"]
    got = {}
    for name, s in FIXED.items():
        got.setdefault(s, []).append(name)
    for name, spec in sorted(blocks.items()):
        if (not isinstance(spec, dict) or not spec.get("light") or spec.get("flicker") or spec.get("signal")
                or name in FIXED):
            continue
        rgb = glow(textures(spec))
        if rgb is None:
            continue
        s = slot(rgb)
        if s:
            got.setdefault(s, []).append(name)
    return got


def write_list():
    got = classify()
    LIST.write_text("# coloured lamps by Photon light slot: written by tools/photon/photon_lights.py\n"
                    + "".join(f"{s} {' '.join(got[s])}\n" for s in SLOTS if s in got))
    print(", ".join(f"{s} {len(got[s])}" for s in SLOTS if s in got))


def read_list():
    got = {}
    for line in LIST.read_text().splitlines():
        if line and not line.startswith("#"):
            s, *names = line.split()
            got[s] = names
    return got


def patch(props, got):
    """block.properties with our ids appended to each slot's line (the vanilla fallback if absent)."""
    lines = props.split("\n")
    where = {int(m.group(1)): i for i, l in enumerate(lines) if (m := re.match(r"\s*block\.(\d+)\s*=", l))}
    ours = re.compile(r"\s+" + NS + r":\S+")
    for i in where.values():
        lines[i] = ours.sub("", lines[i])                 # patching twice doesn't double up
    for s, names in got.items():
        modded, vanilla = SLOTS[s]
        i = where.get(modded, where.get(vanilla))
        if i is not None:
            lines[i] = lines[i].rstrip() + " " + " ".join(f"{NS}:{n}" for n in names)
    return "\n".join(lines)


def patch_zip(src, dst):
    got = read_list()
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        hit = False
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename.endswith("shaders/block.properties"):
                data = patch(data.decode("utf-8"), got).encode("utf-8")
                hit = True
            zout.writestr(item, data)
    print(f"{'patched' if hit else 'NO block.properties in'} {src} -> {dst}")


if __name__ == "__main__":
    if len(sys.argv) == 3:
        patch_zip(sys.argv[1], sys.argv[2])
    else:
        write_list()
