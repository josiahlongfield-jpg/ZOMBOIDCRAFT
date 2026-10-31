"""The qs_ kit: Hollis Island Quarantine Station's own pieces (Josiah, 2026-10-30: the coast and
industry places, Hollis Island fourth), after refs/quarantine: the granite memorial by the old
burying ground (body-bags-mass-graves 03, 04), the yellow quarantine flag, the lime over the new
graves and the shrouds the burial detail had not got to (body-bags-mass-graves 01, 02), the white
ribbed sheeting, louvre windows and concrete stilts of the new isolation wards (isolation-ward
02, 03), and the station's signs. world/county/Hollis.java puts them together.

- qs_memorial_l / _r: the granite memorial on its plinth, two blocks wide, its words cut in (facing);
- qs_flag_q / qs_flag_q_top: the yellow jack on its pole (facing, like the civic flags);
- qs_lime: lime scattered thin over turned earth (none);
- qs_shroud_head / _foot: a body sewn into a sheet on the ground (facing);
- qs_louvre: a white aluminium louvre window, its glass slats half open (facing);
- qs_stilt: a whitewashed concrete stilt under a raised ward (none);
- qs_rib_white / _stain: the wards' white ribbed wall sheeting, clean and rust-streaked;
- signs qs_sgn_KEY_0..1.

    python3 tools/city/hollis.py      rewrites the qs_ entries in blocks.json

photo_textures.py draws the textures (hollis.register()).
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import broadcast as B  # noqa: E402
import ports  # noqa: E402
from furniture import area, disc  # noqa: E402
from school import Pic, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = B.BOLD
INK = (30, 28, 26)
WHITE = (240, 238, 230)
RED = (170, 34, 32)
NAVY = (30, 44, 68)
YELLOW = (236, 200, 40)
GREEN = (34, 80, 52)


# ---------------------------------------------------------------- textures

def granite(seed):
    """Polished grey-brown granite: the speckle of the stone, the polish gone dull with rain."""
    t = F.plain(seed, (84, 76, 72), 0.05, 0.75)
    rng = t.rng
    for _ in range(260):
        c = [(62, 56, 54), (112, 104, 98), (96, 78, 72), (124, 118, 110)][int(rng.integers(4))]
        disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(0.4, 1.0), col=c)
    t.mix(np.clip(P.fbm(t.rng) * 1.3 - 0.5, 0, 1) * 0.5, (60, 66, 54))
    return t


@lru_cache(maxsize=None)
def memorial_pic():
    """The memorial's face: the words cut in and gone pale, the dates."""
    p = Pic(2, 1, (0, 0, 0, 0), 59100)
    W, H = p.W, p.H
    lines = ["IN MEMORY", "OF THOSE WHO DIED", "ON HOLLIS ISLAND", "1893 - 1919"]
    for i, line in enumerate(lines):
        size = 10 if i == 0 else 7
        y = 12 + i * 13
        p.text(W / 2, y, line, BOLD, size, (200, 194, 184), anchor="mm")
    return p


def memorial_face(i):
    t = granite(59101 + i)
    pic = memorial_pic().tile(i, 59110 + i, smooth=0.6, grime=0.2)
    a = pic.c.sum(axis=2) > 300
    t.c[a] = pic.c[a]
    t.h[a] = t.h[a] - 0.3
    return t


def ribbed(seed, stain):
    """White ribbed wall sheet: a rib every quarter block, the paint chalky, rust from the fixings when stained."""
    t = F.plain(seed, (226, 228, 222), 0.03, 0.4)
    for x in range(0, N, N // 4):
        area(t, x, 0, x + 3, N, mul=(1.04, 1.04, 1.04), dh=0.5)
        area(t, x + 3, 0, x + 4, N, mul=(0.72, 0.72, 0.72), dh=-0.2)
    t.grime(0.3, (150, 150, 140))
    if stain:
        rng = t.rng
        for _ in range(5):
            x = int(rng.integers(2, N - 3))
            y = int(rng.integers(0, N // 3))
            area(t, x, y, x + 1, min(N, y + int(rng.integers(14, 50))), mul=(0.92, 0.8, 0.7))
        ports.rust(t, 0.08)
    return t


def flag(seed):
    """The yellow jack: plain yellow bunting, the folds darker, frayed at the fly."""
    t = F.plain(seed, YELLOW, 0.05, 0.1)
    for x in range(0, N, 12):
        area(t, x, 0, x + 5, N, mul=(0.86, 0.84, 0.8))
    t.grime(0.3, (120, 110, 70))
    return t


def lime(seed):
    """Quicklime thrown over turned earth: white drifts, the dirt through it."""
    t = F.plain(seed, (110, 88, 66), 0.08, 0.1)
    t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.2, 0, 1), (232, 230, 220))
    return t


def shroud(seed):
    """A linen sheet sewn round a body: off-white, the stitches down the seam, stained."""
    t = F.plain(seed, (214, 208, 192), 0.05, 0.1)
    area(t, N // 2 - 1, 0, N // 2 + 1, N, mul=(0.85, 0.85, 0.82))
    for y in range(2, N, 6):
        area(t, N // 2 - 3, y, N // 2 + 3, y + 1, col=(120, 110, 96))
    t.mix(np.clip(P.fbm(t.rng) * 1.5 - 0.6, 0, 1) * 0.7, (130, 104, 80))
    return t


def louvre_glass(seed):
    t = F.plain(seed, (186, 204, 206), 0.03, 0.9)
    t.grime(0.4, (130, 130, 120))
    return t


def stilt(seed):
    """A whitewashed concrete stilt, the shuttering lines, splash dirt at the foot."""
    t = F.plain(seed, (214, 212, 204), 0.05, 0.3)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=(0.85, 0.85, 0.85))
    t.mix(np.clip(P.fbm(t.rng) * 1.2 - 0.5, 0, 1) * 0.6, (120, 110, 96))
    return t


SIGNS = {
    "station": ("HOLLIS ISLAND|QUARANTINE STATION", NAVY, WHITE),
    "plaque": ("U.S. QUARANTINE STATION|HOLLIS ISLAND EST. 1893", (60, 52, 40), (210, 180, 110)),
    "landing": ("NO LANDING|QUARANTINE AREA", RED, WHITE),
    "decon": ("DECONTAMINATION|ALL PERSONS", YELLOW, INK),
    "isolation": ("ISOLATION WARD|STAFF IN PPE ONLY", RED, WHITE),
    "restricted": ("RESTRICTED AREA|NO ENTRY", RED, WHITE),
    "ferry": ("FERRY LANDING|TO SEABRIGHT", NAVY, WHITE),
    "plant": ("DISINFECTING PLANT", (60, 52, 40), (210, 180, 110)),
    "office": ("ADMINISTRATION", (60, 52, 40), (210, 180, 110)),
    "burial": ("BURIAL DETAIL|ONLY BEYOND THIS POINT", YELLOW, INK),
    "triage": ("TRIAGE|WAIT TO BE CALLED", YELLOW, INK),
    "ward_a": ("WARD A", (60, 52, 40), (210, 180, 110)),
    "ward_b": ("WARD B", (60, 52, 40), (210, 180, 110)),
    "ward_c": ("WARD C", (60, 52, 40), (210, 180, 110)),
    "ward_d": ("WARD D", (60, 52, 40), (210, 180, 110)),
    "ward_e": ("WARD E", (60, 52, 40), (210, 180, 110)),
    "ward_f": ("WARD F", (60, 52, 40), (210, 180, 110)),
}


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 59200 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 12, W - 1, H - 12, fill=field)
    p.rect(3, 14, W - 3, H - 14, outline=ink, width=1)
    lines = text.split("|")
    size = civic.fit(text, BOLD, 12 if len(lines) == 1 else 9, W - 14)
    for i, line in enumerate(lines):
        y = H / 2 + (i - (len(lines) - 1) / 2) * size * 1.15
        p.text(W / 2, y, line, BOLD, size, ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 59250 + i + len(key) * 11, smooth=0.5, grime=0.35)


def textures():
    T = {
        "qs_granite": lambda: granite(59000),
        "qs_memorial_0": lambda: memorial_face(0),
        "qs_memorial_1": lambda: memorial_face(1),
        "qs_rib_white": lambda: ribbed(59002, False),
        "qs_rib_white_stain": lambda: ribbed(59003, True),
        "qs_flag": lambda: flag(59004),
        "qs_lime": lambda: lime(59005),
        "qs_shroud": lambda: shroud(59006),
        "qs_louvre_glass": lambda: louvre_glass(59007),
        "qs_stilt": lambda: stilt(59008),
    }
    for key in SIGNS:
        for i in range(2):
            T[f"qs_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

TRIM, BRASS, GRAN = "ext_trim_white", "cv_brass", "qs_granite"


def memorial(i):
    face = f"qs_memorial_{i}"
    # the slab over its plinth; the two halves meet at x 16 / 0
    return [[0, 0, 3, 16, 3, 13, GRAN],
            [0, 3, 5.5, 16, 15, 10.5, {"north": face, "south": GRAN, "side": GRAN, "up": GRAN, "down": GRAN}]]


def louvre():
    boxes = [[0, 0, 7, 16, 1, 9, TRIM], [0, 15, 7, 16, 16, 9, TRIM], [0, 1, 7, 1, 15, 9, TRIM], [15, 1, 7, 16, 15, 9, TRIM]]
    for y in range(2, 15, 2):
        boxes.append([1, y, 6.5, 15, y + 0.8, 9.5, "qs_louvre_glass"])
    return boxes


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", tex=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        spec.update(extra)
        out[name] = spec

    add("qs_memorial_l", memorial(0), "Memorial Stone", "STONE", shape=[[0, 0, 3, 16, 15, 13]])
    add("qs_memorial_r", memorial(1), "Memorial Stone", "STONE", shape=[[0, 0, 3, 16, 15, 13]])
    add("qs_flag_q", [[7.25, 0, 7.25, 8.75, 16, 8.75, TRIM], [6, 0, 6, 10, 1.5, 10, BRASS], [8.75, 2, 7.5, 16, 15.5, 8.5, "qs_flag"]],
        "Quarantine Flag", "WOOL", decor=True, render="cutout")
    add("qs_flag_q_top", [[7.25, 0, 7.25, 8.75, 8, 8.75, TRIM], [8.75, 0, 7.5, 16, 6, 8.5, "qs_flag"], [6.5, 8, 6.5, 9.5, 11, 9.5, BRASS]],
        "Quarantine Flag", "WOOL", decor=True, render="cutout")
    add("qs_lime", [[0, 0, 0, 16, 0.6, 16, "qs_lime"]], "Lime", "SAND", rotate=None, decor=True, shape=[[0, 0, 0, 16, 1, 16]])
    add("qs_shroud_head", [[4, 0, 2, 12, 4, 16, "qs_shroud"], [5, 0, 0.5, 11, 3.5, 2, "qs_shroud"]], "Shrouded Body", "WOOL",
        decor=True, shape=[[4, 0, 0, 12, 4, 16]])
    add("qs_shroud_foot", [[4, 0, 0, 12, 3.5, 12, "qs_shroud"], [5, 0, 12, 11, 2.5, 15, "qs_shroud"]], "Shrouded Body", "WOOL",
        decor=True, shape=[[4, 0, 0, 12, 4, 15]])
    add("qs_louvre", louvre(), "Louvre Window", "GLASS", render="translucent", shape=[[0, 0, 7, 16, 16, 9]])
    add("qs_stilt", [[4, 0, 4, 12, 16, 12, "qs_stilt"]], "Concrete Stilt", "STONE", rotate=None, shape=[[4, 0, 4, 12, 16, 12]])
    add("qs_rib_white", None, "Wall Sheeting", "METAL", rotate=None, tex="qs_rib_white")
    add("qs_rib_white_stain", None, "Wall Sheeting", "METAL", rotate=None, tex="qs_rib_white_stain")
    plate = lambda tex: [[0, 0, 15.2, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]
    for key in SIGNS:
        for i in range(2):
            add(f"qs_sgn_{key}_{i}", plate(f"qs_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    for k in [k for k in blocks_ if k.startswith("qs_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} quarantine station blocks in {path}")


if __name__ == "__main__":
    main()
