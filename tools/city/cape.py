"""The cm_ kit: Cape Merrow Lighthouse's own pieces (Josiah, 2026-10-30: the coast and industry
places, the lighthouse second), after refs/coast and refs/port/lighthouse: the first-order Fresnel
lens on its red cast-iron pedestal, its drum of brass-bound prisms and the bull's-eye panels round
its middle (lighthouse-interior-stairs-lens 04-06); the newel pipe the iron stair winds round
(01-03); the lantern's ventilator ball and lightning rod and the corbels under the gallery
(port/lighthouse 04-06); the fog signal's horns; the coastal path's fingerpost (cliff-headland
01); the station's signs. world/county/Cape.java puts them together with the rd_ ring pieces
(round.py: the tower's white and red courses, the gallery's X-lattice rail, the lantern's
diagonal astragals).

- cm_iron, cm_red_iron, cm_brass: black and red-painted cast iron, polished brass;
- cm_prism(_lit), cm_bull(_lit): the lens's prism rings and its bull's-eye panels, dark or lit;
- cm_grate: the watch room's and gallery's iron grating; cm_tread: chequer plate;
- cm_lens_lo / _mid / _top (+ _lit): the lens a block at a time, two and three-quarter blocks
  across, centred on its block (none); cm_ped: its pedestal (none);
- cm_newel: the stair's central pipe, a collar at each joint (none);
- cm_vent: the lantern roof's ventilator ball and lightning rod (none);
- cm_corbel: a bracket under the gallery, its back to the tower (facing);
- cm_horn: the fog signal's pair of horns out of the wall behind (facing);
- cm_fpost: the coastal path's fingerpost, its two arms (facing);
- signs cm_sgn_KEY_0..1.

    python3 tools/city/cape.py      rewrites the cm_ entries in blocks.json

photo_textures.py draws the textures (cape.register()).
"""
import json
import math
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
BOLD, STENCIL = B.BOLD, B.STENCIL
INK = (30, 28, 26)
WHITE = (240, 238, 230)
RED = (160, 34, 30)
NAVY = (30, 44, 68)
GREEN = (36, 84, 54)
BRASS = (190, 150, 70)


# ---------------------------------------------------------------- textures

def iron(seed, rgb=(38, 38, 40)):
    """Cast iron under coats of paint: the paint's sheen, chips to rust at the edges."""
    t = F.plain(seed, rgb, 0.05, 0.5)
    t.mix(np.clip(P.fbm(t.rng) * 1.3, 0, 1) * 0.2, mixc(rgb, (0, 0, 0), 0.3))
    ports.rust(t, 0.12)
    return t


def brass(seed):
    t = F.plain(seed, BRASS, 0.07, 0.8)
    t.f0[:] = 210
    t.mix(np.clip(P.fbm(t.rng) - 0.2, 0, 1) * 0.5, (110, 90, 50))
    return t


def prism(seed, lit=False):
    """The lens's prisms in rings: glass bands a few pixels high, each bright along its top edge
    where it bends the light, the brass between the rings."""
    base = (232, 222, 170) if lit else (150, 172, 168)
    t = F.plain(seed, base, 0.03, 0.9)
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 1, col=BRASS, dh=0.3)
        area(t, 0, y + 1, N, y + 3, col=(255, 250, 220) if lit else (210, 226, 222), dh=0.1)
        area(t, 0, y + 6, N, y + 8, mul=(0.75, 0.78, 0.8))
    for x in range(0, N, 16):
        area(t, x, 0, x + 1, N, col=BRASS, dh=0.3)
    t.f0[:] = 120
    return t


def bull(seed, lit=False):
    """A bull's-eye panel: the lens's rings round its centre, the brass frame round the panel."""
    base = (236, 226, 176) if lit else (146, 168, 166)
    t = F.plain(seed, base, 0.02, 0.9)
    for r in range(4, 30, 5):
        disc(t, N / 2, N / 2, r, col=(255, 252, 224) if lit else (206, 222, 220), ring=1.2)
    disc(t, N / 2, N / 2, 3, col=(255, 255, 240) if lit else (220, 234, 232))
    area(t, 0, 0, N, 3, col=BRASS, dh=0.3)
    area(t, 0, N - 3, N, N, col=BRASS, dh=0.3)
    area(t, 0, 0, 2, N, col=BRASS, dh=0.3)
    area(t, N - 2, 0, N, N, col=BRASS, dh=0.3)
    t.f0[:] = 120
    return t


def grate(seed):
    """Iron grating: the bars a few pixels apart both ways, dark gaps between, worn bright on top."""
    t = F.plain(seed, (24, 24, 26), 0.03, 0.3)
    for i in range(0, N, 8):
        area(t, i, 0, i + 3, N, col=(66, 64, 62), dh=0.4)
        area(t, 0, i, N, i + 3, col=(70, 68, 66), dh=0.4)
    ports.rust(t, 0.15)
    return t


def tread(seed):
    """Chequer plate: the raised lozenges in rows, alternately turned."""
    t = F.plain(seed, (84, 84, 86), 0.04, 0.5)
    for y in range(4, N, 10):
        for x in range(4 + (y // 10 % 2) * 5, N, 10):
            area(t, x, y, x + 5, y + 2, mul=(1.3, 1.3, 1.3), dh=0.3)
    ports.rust(t, 0.1)
    return t


SIGNS = {
    "light": ("CAPE MERROW LIGHT|ESTABLISHED 1871", NAVY, WHITE),
    "private": ("KEEPERS QUARTERS|PRIVATE", NAVY, WHITE),
    "cliff": ("DANGER|CLIFF EDGE", RED, WHITE),
    "fog": ("FOG SIGNAL", NAVY, WHITE),
    "path": ("COASTAL PATH", GREEN, WHITE),
    "noentry": ("NO|ADMITTANCE", RED, WHITE),
    "oil": ("OIL HOUSE", NAVY, WHITE),
    "tower": ("TOWER CLOSED|TO VISITORS", RED, WHITE),
    "quiet": ("PLEASE KEEP|TO THE PATH", GREEN, WHITE),
    "steps": ("LANDING STEPS|SLIPPERY WHEN WET", RED, WHITE),
}


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 57400 + len(key) * 3)
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
    return sign_pic(key).tile(i, 57450 + i + len(key) * 11, smooth=0.5, grime=0.3)


def fingerpost(seed):
    """The fingerpost's arms: weathered white paint, the place each points to in black, on the
    bands the arms' faces take (rows 8..20 and 28..40)."""
    t = F.wood(seed, "oak")
    t.c[:] = t.c * 0.25 + np.array([214, 210, 196]) * 0.75
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(p.W / 2, 14 * p.W / N, "COAST PATH", BOLD, civic.fit("COAST PATH", BOLD, 8, p.W - 4), INK, anchor="mm")
    p.text(p.W / 2, 34 * p.W / N, "LIGHTHOUSE", BOLD, civic.fit("LIGHTHOUSE", BOLD, 8, p.W - 4), INK, anchor="mm")
    a = p.shrunk()
    m = a[..., 3] > 60
    t.c[m] = a[..., :3][m]
    t.grime(0.3, (70, 80, 60))
    return t


def textures():
    T = {
        "cm_iron": lambda: iron(57000),
        "cm_red_iron": lambda: iron(57001, (128, 30, 28)),
        "cm_brass": lambda: brass(57002),
        "cm_prism": lambda: prism(57003),
        "cm_prism_lit": lambda: prism(57004, True),
        "cm_bull": lambda: bull(57005),
        "cm_bull_lit": lambda: bull(57006, True),
        "cm_grate": lambda: grate(57007),
        "cm_tread": lambda: tread(57008),
        "cm_fp": lambda: fingerpost(57009),
    }
    for key in SIGNS:
        for i in range(2):
            T[f"cm_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

IRON, RIRON, BR, CAP = "cm_iron", "cm_red_iron", "cm_brass", "lh_cap"


def poly(y0, y1, r, tex, sides=16):
    """A drum of `sides` (8 or 16) faces round the block's middle, r px to the corners' circle."""
    angles = (0, 45) if sides == 8 else (0, 22.5, 45, -22.5)
    a = r * math.cos(math.radians(180 / sides))
    out = []
    for ang in angles:
        b = [round(8 - a, 3), y0, round(8 - a, 3), round(8 + a, 3), y1, round(8 + a, 3), tex]
        if ang:
            b += [None, {"axis": "y", "angle": ang, "origin": [8, 8, 8]}]
        out.append(b)
    return out


def bars(y0, y1, r, n=8, w=1.2):
    """Brass astragals up the lens at n points round it."""
    out = []
    for k in range(n):
        a = math.radians(360 * k / n + 180 / n)
        x, z = 8 + r * math.cos(a), 8 + r * math.sin(a)
        out.append([round(x - w / 2, 3), y0, round(z - w / 2, 3), round(x + w / 2, 3), y1, round(z + w / 2, 3), BR])
    return out


def lens(part, lit):
    pr = "cm_prism_lit" if lit else "cm_prism"
    face = {"north": "cm_bull_lit" if lit else "cm_bull", "south": "cm_bull_lit" if lit else "cm_bull",
            "east": "cm_bull_lit" if lit else "cm_bull", "west": "cm_bull_lit" if lit else "cm_bull", "up": pr, "down": pr}
    if part == "lo":
        b = poly(0, 2, 13, BR)
        for y0, y1, r in ((2, 6, 15), (6, 10, 18), (10, 14, 20), (14, 16, 21)):
            b += poly(y0, y1, r, pr)
        return b + bars(2, 16, 20.6)
    if part == "mid":
        return poly(0, 16, 21, face, 8) + bars(0, 16, 21.4) + poly(7.5, 8.5, 21.6, BR, 8)
    b = []
    for y0, y1, r in ((0, 3, 21), (3, 7, 19), (7, 11, 15.5), (11, 14, 10)):
        b += poly(y0, y1, r, pr)
    return b + poly(14, 16, 6, BR) + bars(0, 11, 19.6)


def pedestal():
    return poly(0, 2, 13, RIRON) + poly(2, 12, 7.5, RIRON) + poly(5, 6, 8.2, BR) + poly(12, 16, 11, RIRON)


def newel():
    return [[5.5, 0, 5.5, 10.5, 16, 10.5, IRON], [4.8, 0, 4.8, 11.2, 1.2, 11.2, IRON]]


def vent():
    """The ventilator ball on its neck, the lightning rod through it."""
    b = poly(0, 1.5, 6, CAP) + poly(1.5, 4, 2.5, CAP)
    for y0, y1, r in ((4, 5.5, 4), (5.5, 8, 5.5), (8, 10, 5.5), (10, 11.5, 4)):
        b += poly(y0, y1, r, CAP, 8)
    return b + [[7.5, 11.5, 7.5, 8.5, 16, 8.5, BR], [7.2, 11.5, 7.2, 8.8, 12.5, 8.8, BR]]


def corbel():
    """A gallery bracket, its back on the cell's south edge and the tower behind it, stepping out
    to carry the deck."""
    w = "lh_white"
    return [[5, 12, 0, 11, 16, 16, w], [5.5, 8, 5, 10.5, 12, 16, w], [6, 4, 10, 10, 8, 16, w], [6.5, 0, 13, 9.5, 4, 16, w]]


def horn():
    """Two diaphone horns out of the wall to the south: throat, cone and bell, the bell's mouth dark."""
    b = [[1, 2, 12, 15, 4, 16, RIRON]]
    for x in (4.5, 11.5):
        b += [[x - 1, 4, 10, x + 1, 6, 16, RIRON], [x - 2, 3, 6, x + 2, 7, 10, RIRON],
              [x - 3, 2, 2, x + 3, 8, 6, RIRON], [x - 2.4, 2.6, 1.9, x + 2.4, 7.4, 2, IRON]]
    return b


def fpost():
    """The fingerpost: the post, its cap, the two arms across it."""
    w = "cm_fp"
    return [[6.5, 0, 6.5, 9.5, 16, 9.5, "sy_timber"], [6, 15, 6, 10, 16, 10, "sy_timber"],
            [0, 10, 7.2, 16, 13, 8.8, w], [1, 5, 7.2, 15, 8, 8.8, w]]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", tex=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        spec.update(extra)
        out[name] = spec

    for n, title in {"cm_iron": "Cast Iron", "cm_red_iron": "Cast Iron", "cm_brass": "Brass", "cm_grate": "Grating",
                     "cm_tread": "Chequer Plate"}.items():
        add(n, None, title, "METAL", rotate=None, tex=n)
    for part in ("lo", "mid", "top"):
        add(f"cm_lens_{part}", lens(part, False), "Fresnel Lens", "GLASS", rotate=None, shape=[[0, 0, 0, 16, 16, 16]])
        add(f"cm_lens_{part}_lit", lens(part, True), "Fresnel Lens", "GLASS", rotate=None, light=15, shape=[[0, 0, 0, 16, 16, 16]])
    add("cm_ped", pedestal(), "Lens Pedestal", "METAL", rotate=None, shape=[[2, 0, 2, 14, 16, 14]])
    add("cm_newel", newel(), "Newel", "METAL", rotate=None, shape=[[5, 0, 5, 11, 16, 11]])
    add("cm_vent", vent(), "Ventilator", "METAL", rotate=None, shape=[[3, 0, 3, 13, 12, 13]])
    add("cm_corbel", corbel(), "Gallery Bracket", "STONE", shape=[[5, 0, 0, 11, 16, 16]])
    add("cm_horn", horn(), "Fog Horn", "METAL", shape=[[1, 2, 2, 15, 8, 16]])
    add("cm_fpost", fpost(), "Fingerpost", "WOOD", shape=[[6.5, 0, 6.5, 9.5, 16, 9.5]])
    plate = lambda tex: [[0, 0, 15.2, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]
    for key in SIGNS:
        for i in range(2):
            add(f"cm_sgn_{key}_{i}", plate(f"cm_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    for k in [k for k in blocks_ if k.startswith("cm_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} cape blocks in {path}")


if __name__ == "__main__":
    main()
