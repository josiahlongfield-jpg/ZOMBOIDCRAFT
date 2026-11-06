"""The hf_ kit: the harbour fort (world/county/FortSite), an old round sea fort on a shoal in the
bay, from the photos in refs/coast (sea-fort-exterior: Spitbank, Horse Sand, Fort Carroll;
gun-battery-casemate; gun-port-embrasure; coastal-battery-concrete). Its drum is grey granite laid
in big courses, dark and weeded in the splash zone; over it the iron armour of the gun tier, its
plates riveted and rusted, a gun port to each casemate, and rust run down from every port over the
plates below (the fort's clue from the shore).

Textures (rd_ panels of them come from round.py: rd_hfg, rd_hfgw, rd_hfa, rd_hfas, rd_hfpl, rd_hfpu):
- hf_granite: the ashlar, three courses to a block, rain-streaked; hf_granite_tide: the same in
  the splash zone, wet and dark, the weed and the barnacles on it (refs: sea-fort-exterior 01, 04);
- hf_armour: riveted iron plates gone to rust; hf_armour_streak: the plates under a port, the
  rust run down them in long streaks (refs: sea-fort-exterior 01, gun-port-embrasure);
- hf_port_lo / hf_port_hi: the port's sill and lintel in the armour with the opening cut out
  between, so the sea shows through from the casemate (refs: gun-battery-casemate 01, 04).

Blocks:
- hf_granite: the granite as a whole block (the casemates' walls, the light well);
- hf_gun / hf_gun_muzzle: the old muzzle-loader still on its slide in two casemates, its barrel
  run out to the port (facing: the muzzle's way; the muzzle block is the next cell that way);
- hf_racer: the iron racer rails the slide traversed on, set in the casemate floor (decor);
- hf_shot: round shot stacked in a pyramid (decor).

    python3 tools/city/harbourfort.py      rewrites the hf_ entries in blocks.json

photo_textures.py draws the textures (harbourfort.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from bunker import rivets, rust_runs  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
RUST = np.array((118, 64, 34), np.float32)


# ---------------------------------------------------------------- textures

def granite(seed, tide=False):
    """Grey granite ashlar in courses, the joints dark, streaked by the rain; in the splash zone wet
    and dark, green weed in the joints and up the faces, barnacles in white specks."""
    t = NS.photo("seaworn_stone_tiles", span=4, offset=(0.1 + (seed % 7) * 0.1, 0.2), sat=0.3, bright=0.8,
                 tint=(0.97, 1.0, 1.03), seed=seed)
    t.streaks(0.45, 0.8)
    t.grime(0.12)
    if tide:
        t.c *= 0.7
        weed = np.clip(P.fbm(t.rng, ((2, 1.0), (4, 0.8), (8, 0.5), (16, 0.3))) * 1.8 - 0.1, 0, 1)
        t.mix(weed, (44, 64, 30), 0.9)
        bar = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 2.0 - 0.6, 0, 1)
        m = (t.rng.random((N, N)) < 0.05 * bar)
        t.c[m] = t.c[m] * 0.55 + np.array((170, 166, 152), np.float32) * 0.45
        t.h[m] += 0.15
        t.smooth = np.maximum(t.smooth, 0.55)
    return t


def armour(seed, streak=False):
    """Wrought-iron armour plates, a block each, riveted along their seams, their dark grey paint
    flaking to rust; under a port the rust has run down them in long streaks."""
    t = NS.photo("rusty_metal_sheet", span=1, offset=(0.15 + (seed % 5) * 0.12, 0.3), sat=0.3, bright=0.42,
                 tint=(0.98, 0.98, 1.0), metal=False, seed=seed)
    r = NS.photo("rusty_metal_02", span=1, offset=(0.3, 0.1 + (seed % 3) * 0.2), sat=0.6, bright=0.5, seed=seed + 1)
    m = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.4))) * 1.3 - 0.45, 0, 0.45)
    t.c = t.c * (1 - m[..., None]) + r.c * m[..., None]
    F.area(t, 0, 0, N, 2, mul=0.45, dh=-0.25)
    F.area(t, 0, 0, 2, N, mul=0.45, dh=-0.25)
    rivets(t, range(5, N, 9), (5, N - 5))
    rivets(t, (5, N - 5), range(14, N - 8, 9))
    rust_runs(t, 10, 0.35)
    if streak:
        rng = t.rng
        for _ in range(14):
            x = int(rng.normal(N / 2, N / 5)) % N
            w = int(rng.uniform(1, 4))
            a = rng.uniform(0.55, 0.95)
            ph, gap = rng.uniform(0, 6), rng.uniform(0.1, 0.25)
            for y in range(N):
                f = a * np.clip(0.6 + 0.4 * np.sin(y * 0.21 + ph) + 0.3 * np.sin(y * 0.67 + 2 * ph) - gap, 0, 1)
                for dx in range(w):
                    xx = (x + dx) % N
                    t.c[y, xx] = t.c[y, xx] * (1 - f) + RUST * 1.3 * f
        t.c = np.clip(t.c, 0, 255)
    return F.wear(t, 0.2, 4)


def port(seed, part):
    """A gun port's sill (lo) or lintel (hi) in the armour, the opening cut clean out above or below
    it: a heavy iron lip along the cut edge, the rust starting to run from it."""
    t = armour(seed, streak=part == "lo")
    alpha = np.full((N, N), 255, np.float32)
    if part == "lo":
        alpha[:N - 16] = 0
        F.area(t, 0, N - 16, N, N - 12, mul=1.25, dh=0.3)   # the sill's lip, lit on top
    else:
        alpha[18:] = 0
        F.area(t, 0, 14, N, 18, mul=0.6, dh=0.3)            # the lintel's lip, in its own shadow
        rust_runs(t, 6, 0.5, top=12)
    t.alpha = alpha
    return t


def iron(seed):
    """The gun's iron: black paint worn brown, rust coming through in specks."""
    t = F.plain(seed, (40, 40, 38), 0.06, 0.45)
    t.speckle(0.05, (108, 62, 34), 0.6, 0.0)
    return F.wear(t, 0.15, 3)


def carriage(seed):
    """The slide and carriage: riveted plate in dull grey paint, rusted where it's chipped."""
    t = F.plain(seed, (70, 72, 68), 0.05, 0.3)
    rivets(t, range(4, N, 8), (4, N - 4))
    rust_runs(t, 6, 0.4)
    return F.wear(t, 0.3, 4)


def textures():
    return {
        "hf_granite": lambda: granite(89600),
        "hf_granite_tide": lambda: granite(89601, tide=True),
        "hf_armour": lambda: armour(89602),
        "hf_armour_streak": lambda: armour(89603, streak=True),
        "hf_port_lo": lambda: port(89604, "lo"),
        "hf_port_hi": lambda: port(89605, "hi"),
        "hf_iron": lambda: iron(89606),
        "hf_carriage": lambda: carriage(89607),
    }


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

def gun():
    """The breech half (facing north: the muzzle's way): the slide's two girders on their trucks,
    the carriage's cheeks, the trunnions, the fat breech and its cascabel at the back."""
    S, C, I = "hf_carriage", "hf_carriage", "hf_iron"
    return [[1, 0, 0, 4, 3.5, 16, S], [12, 0, 0, 15, 3.5, 16, S],          # the slide's girders
            [1, 0, 13, 15, 2, 15, S],                                       # a cross girder, the rear truck
            [2.5, 3.5, 2, 4.5, 10, 13, C], [11.5, 3.5, 2, 13.5, 10, 13, C],  # the carriage's cheeks
            [4.5, 3.5, 3, 11.5, 5, 12, C],                                  # the transom between them
            [2, 8, 6, 14, 10, 8.5, I],                                      # the trunnions on their caps
            [3.5, 5, 0, 12.5, 14, 13, I],                                   # the breech
            [5.5, 7, 13, 10.5, 12, 15.5, I], [7, 8.5, 15.5, 9, 10.5, 16, I]]  # the cascabel and its knob


def gun_muzzle():
    """The barrel run out (facing north), tapering to the muzzle's swell."""
    I = "hf_iron"
    return [[4.2, 5.7, 8, 11.8, 13.3, 16, I], [4.8, 6.3, 2, 11.2, 12.7, 8, I], [4, 5.5, 0, 12, 13.5, 2, I],
            [1, 0, 8, 4, 3.5, 16, "hf_carriage"], [12, 0, 8, 15, 3.5, 16, "hf_carriage"],
            [1, 0, 9, 15, 2, 11, "hf_carriage"]]                            # the slide's front, the fore truck


def racer():
    """The two iron racer rails in the floor, the slide's trucks ran round on them."""
    return [[0, 0, 2.5, 16, 0.6, 4.5, "hf_iron"], [0, 0, 11.5, 16, 0.6, 13.5, "hf_iron"]]


def shot():
    """Round shot stacked three, two and one."""
    b = []
    for (layer, n) in ((0, 3), (1, 2), (2, 1)):
        off = 2 + layer * 2.2
        for i in range(n):
            for j in range(n):
                x, z = off + i * 4.4, off + j * 4.4
                b.append([round(x, 2), layer * 3.6, round(z, 2), round(x + 4, 2), layer * 3.6 + 4, round(z + 4, 2), "hf_iron"])
    return b


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, **extra):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        spec.update(extra)
        out[name] = spec

    out["hf_granite"] = {"tex": "hf_granite", "title": "Granite", "sound": "STONE"}
    add("hf_gun", gun(), "Old Fort Gun", "METAL", shape=[[1, 0, 0, 15, 14, 16]])
    add("hf_gun_muzzle", gun_muzzle(), "Old Fort Gun", "METAL", shape=[[1, 0, 0, 15, 13.5, 16]])
    add("hf_racer", racer(), "Racer Rails", "METAL", decor=True)
    add("hf_shot", shot(), "Round Shot", "METAL", rotate=None, decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    for k in [k for k in blocks_ if k.startswith("hf_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} harbour fort blocks in {path}")


if __name__ == "__main__":
    main()
