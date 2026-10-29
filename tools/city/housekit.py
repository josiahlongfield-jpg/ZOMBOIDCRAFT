"""The hk_ kit: the parts of a house that were still vanilla blocks, built to the landmarks' detail
(Josiah, 2026-10-29: "Everything in this world must be of the same level of detail and effort").
world/county/House.java puts them in.

- hk_cabin_log: a log cabin's wall, two round logs a block with the chinking between them, the
  logs along the wall's row (rotate=axis); hk_cabin_corner: the corner where the walls cross, the
  lower log along x and the upper along z, their sawn ends showing;
- hk_ridge_beam: the hewn ridge beam under an attic's roof, along its row;
- hk_pot: a terracotta pot on its saucer by the front step, what was planted in it long dead.

    python3 tools/city/housekit.py      rewrites the hk_ entries in blocks.json

photo_textures.py draws the textures (housekit.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from yard import along_z, turn, white_paint  # noqa: E402

N = P.N
HERE = Path(__file__).parent


# ---------------------------------------------------------------- textures

def log_side(seed):
    """Weathered pine logs: the grain along the log, silvered, dark where the rain runs off."""
    t = F.mat("wood_trunk_wall", (112, 96, 80), 1.2, rot=True, seed=seed)
    t.grime(0.2, (50, 42, 34))
    t.speckle(0.006, (30, 26, 20), 0.6)
    return t


def charred(seed):
    """A log the fire got to: blackened, the surface crazed into alligator checks."""
    t = log_side(seed)
    n = np.clip(P.fbm(t.rng) * 0.8 + 0.75, 0, 1)
    t.mix(n, (26, 22, 20), 0.9)
    t.crack(10, 0.3, (8, 6, 6))
    t.speckle(0.03, (90, 80, 72), 0.4)
    t.smooth[:] = 0.08
    return t


def chink(seed):
    """Lime chinking between the logs, cracked and fallen out in places."""
    t = F.mat("rough_plaster_broken", (196, 190, 176), 0.9, seed=seed)
    t.grime(0.35, (90, 84, 72))
    t.crack(4, 0.2, (60, 54, 46))
    return t


def timber(seed):
    """An adzed beam: dry, dusty, cobwebbed grey on top."""
    t = F.mat("rough_wood", (120, 96, 70), 1.1, seed=seed)
    t.grime(0.25, (70, 66, 60))
    return t


def terracotta(seed):
    t = F.plain(seed, (176, 98, 64), 0.08, 0.15)
    t.grime(0.3, (110, 100, 86))
    yy = np.mgrid[0:N, 0:N][0].astype(np.float32)
    t.mix(np.clip((yy - 40) / 24, 0, 1) * 0.5, (210, 206, 190))  # lime bloom at the foot
    t.speckle(0.01, (90, 60, 40), 0.4)
    return t


def soil(seed):
    t = F.plain(seed, (58, 44, 32), 0.2, 0.05)
    t.speckle(0.05, (110, 96, 74), 0.5, 0.05)
    return t


def stalk(seed):
    t = F.plain(seed, (118, 96, 60), 0.15, 0.1)
    t.speckle(0.08, (70, 56, 38), 0.6)
    return t


def porch_skirt(seed):
    """The side of a porch deck: the white fascia board along the top, the lattice skirting under it
    (slats crossing on the diagonal, dark under the porch behind), mud splashed up its foot."""
    t = white_paint(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    band = N * 0.2
    pitch, w = N / 4, N / 22
    a = np.abs(((xx + yy) % pitch) - pitch / 2) < w
    b = np.abs(((xx - yy) % pitch) - pitch / 2) < w
    slat = (a | b) & (yy > band + 1)
    hole = ~slat & (yy > band + 1)
    t.c[hole] = np.array((24, 22, 20), np.float32) * (0.8 + 0.4 * t.rng.random((int(hole.sum()), 1)))
    t.h[hole] = 0.3
    t.h[b & ~a & (yy > band + 1)] -= 0.06  # the back layer of slats
    t.smooth[hole] = 0.05
    t.c[(yy > band) & (yy <= band + 1)] *= 0.55  # the shadow line under the fascia
    t.mix(np.clip((yy - N * 0.78) / (N * 0.22), 0, 1) * (P.fbm(t.rng) * 0.5 + 0.6), (78, 64, 48), 0.7)
    return t


def textures():
    return {
        "hk_log": lambda: log_side(49200),
        "hk_chink": lambda: chink(49201),
        "hk_log_soot": lambda: charred(49207),
        "hk_timber": lambda: timber(49202),
        "hk_terracotta": lambda: terracotta(49203),
        "hk_soil": lambda: soil(49204),
        "hk_stalk": lambda: stalk(49205),
        "hk_porch_skirt": lambda: porch_skirt(49206),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

LOG = {"north": "hk_log", "south": "hk_log", "up": "hk_log", "down": "hk_log", "east": "bark_top", "west": "bark_top"}


def log_x(y, x0=0, x1=16):
    """One log along x, its middle at height y + 4: the body and the bulge that rounds it."""
    return [[x0, y + 0.8, 1, x1, y + 7.2, 15, LOG], [x0, y + 2, 0, x1, y + 6, 16, LOG]]


def log_z(y, z0=0, z1=16):
    return [[1, y + 0.8, z0, 15, y + 7.2, z1, {"east": "hk_log", "west": "hk_log", "up": "hk_log", "down": "hk_log",
                                                 "north": "bark_top", "south": "bark_top"}],
            [0, y + 2, z0, 16, y + 6, z1, {"east": "hk_log", "west": "hk_log", "up": "hk_log", "down": "hk_log",
                                           "north": "bark_top", "south": "bark_top"}]]


def cabin_log(soot=False):
    """Two logs along x and the chinking pressed in between, under and over them."""
    b = log_x(0) + log_x(8)
    if soot:
        b = [q[:6] + [{k: ("hk_log_soot" if v == "hk_log" else v) for k, v in q[6].items()}] for q in b]
    for y in (0, 7, 15.2):
        b.append([0, y, 2.2, 16, y + (0.8 if y != 7 else 2), 13.8, "hk_chink"])
    return b


def cabin_corner():
    """The lower log along x, the upper along z, each running through and showing its sawn end."""
    return log_x(0) + log_z(8) + [[1.6, 7, 1.6, 14.4, 9, 14.4, "hk_chink"]]


def ridge_beam():
    """An 8 by 10 inch hewn beam along x, an iron strap where two lengths were scarfed together."""
    return [[0, 5, 5, 16, 14, 11, "hk_timber"],
            [7, 4.6, 4.6, 9, 14.4, 11.4, "ext_galv"]]


def pot():
    """A terracotta pot on its saucer, the rim rolled, the stalks of what grew in it."""
    b = [[4.5, 0, 4.5, 11.5, 0.8, 11.5, "hk_terracotta"],
         [5.5, 0.8, 5.5, 10.5, 6, 10.5, "hk_terracotta"],
         [5, 6, 5, 11, 7.2, 11, "hk_terracotta"],
         [5.8, 6.4, 5.8, 10.2, 6.9, 10.2, "hk_soil"]]
    for x, z, a, ax in ((7, 7, 22.5, "x"), (8.6, 8, -22.5, "z"), (8, 9, 0, "x"), (7.4, 8.6, -45, "x")):
        box = [x, 6.9, z, x + 0.5, 13, z + 0.5, "hk_stalk"]
        if a:
            box += [None, turn(ax, a, (x + 0.25, 6.9, z + 0.25))]
        b.append(box)
    return b


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate=None, decor=False, **extra):
        if rotate == "axis":
            boxes = along_z(boxes)
            if "shape" in extra:
                extra["shape"] = [q[:6] for q in along_z([list(r) + [None] for r in extra["shape"]])]
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        spec.update(extra)
        out[name] = spec

    add("hk_cabin_log", cabin_log(), "Log Wall", "WOOD", rotate="axis")
    add("hk_cabin_log_soot", cabin_log(True), "Log Wall", "WOOD", rotate="axis")
    add("hk_cabin_corner", cabin_corner(), "Log Wall", "WOOD")
    add("hk_ridge_beam", ridge_beam(), "Ridge Beam", "WOOD", rotate="axis", shape=[[0, 5, 5, 16, 14, 11]])
    add("hk_pot", pot(), "Flower Pot", "PACKED_MUD", decor=True, shape=[[4.5, 0, 4.5, 11.5, 7.2, 11.5]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("hk_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} house kit blocks in {path}")


if __name__ == "__main__":
    main()
