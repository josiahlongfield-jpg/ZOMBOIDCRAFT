"""The ct_ kit: shipping containers built to the landmarks' detail in place of 12 x 3 x 3 painted
cubes (Josiah, 2026-10-29: "Everything in this world must be of the same level of detail and
effort"). world/county/Boxes.java lays them out; Harbour, BigShip, Camp and the civic yards call it.

A container is three blocks square and len long. Every piece is drawn in its own frame with the
face it shows on the north (z = 0), rotate=facing:

- ct_<c>_side / _side_top / _side_bot: a length of side wall, the corrugations standing proud of
  it (four a block), the top rail with the roof's corrugations over it, or the bottom rail;
- ct_<c>_roof: the roof between the two side walls, its corrugations across the box;
- ct_<c>_end_<row><col> and ct_<c>_door_<row><col>: the closed end and the door end, row b/m/t,
  col 0/1/2 from the low x of the piece's frame: corner posts and castings, the side walls' first
  corrugations round the corner, the doors' four locking bars with their cams, handles and
  keepers, the hinges, the header and the sill, the owner's code stencilled on the left door.

The middle of the box (never seen) stays the plain container_<c> cube.

    python3 tools/city/ports.py         rewrites the ct_ entries in blocks.json

photo_textures.py draws the textures (ports.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402

N = P.N
HERE = Path(__file__).parent

COLOURS = {"red": (146, 52, 40), "blue": (44, 76, 116), "green": (62, 98, 68), "orange": (190, 102, 40),
           "white": (192, 192, 186), "grey": (108, 112, 114)}
CODES = {"red": "ZCLU 412093", "blue": "GHVU 280415", "green": "MRKU 663710", "orange": "TSLU 509126",
         "white": "CRXU 174382", "grey": "PNSU 931057"}


# ---------------------------------------------------------------- textures

def rust(t, amount):
    """Rust blooming through the paint at the edges and in the troughs: orange-brown, rougher."""
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.5), (32, 0.3)))
    m = np.clip((n - (0.55 - amount)) * 4, 0, 1)
    col = np.array((104, 58, 34), np.float32) * (0.8 + 0.4 * (P.fbm(t.rng) * 0.5 + 0.5))[..., None]
    t.c = t.c * (1 - m[..., None]) + col * m[..., None]
    t.smooth = t.smooth * (1 - m * 0.6)


def paint(seed, rgb):
    """Container paint: a marine enamel gone chalky, rust through it, salt streaks down from the top."""
    t = F.mat("PaintedMetal004", rgb, 0.3, span=3, seed=seed)
    t.vary(0.05)
    rust(t, 0.18)
    t.streaks(0.8, 0.72)
    t.grime(0.15, (60, 54, 46))
    return t


def code(seed, rgb, text):
    """The left door's top: the owner's code in white stencil over the paint."""
    t = paint(seed, rgb)
    a, b = text.split()
    for line, y0 in ((a, 8), (b, 28)):
        m = F.text_mask(line, F.SANS, 15, N, 18, seed=seed)
        F.paste(t, m, 0, y0, (226, 226, 218))
    return t


def casting(seed):
    """A corner casting: cast steel, the oval hole the twistlocks go into, worn bright round it."""
    t = F.mat("rusty_metal", (92, 88, 82), 0.9, seed=seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    hole = ((xx - N / 2) / (N * 0.28)) ** 2 + ((yy - N / 2) / (N * 0.16)) ** 2 < 1
    ring = ((xx - N / 2) / (N * 0.36)) ** 2 + ((yy - N / 2) / (N * 0.24)) ** 2 < 1
    t.c[ring & ~hole] = t.c[ring & ~hole] * 0.6 + np.array((150, 146, 140), np.float32) * 0.4
    t.c[hole] = (14, 12, 12)
    t.h[hole] = 0.1
    return t


def textures():
    out = {"ct_casting": lambda: casting(49300)}
    for i, (c, rgb) in enumerate(COLOURS.items()):
        out["ct_" + c] = lambda rgb=rgb, i=i: paint(49310 + i, rgb)
        out["ct_" + c + "_code"] = lambda rgb=rgb, i=i, c=c: code(49320 + i, rgb, CODES[c])
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

PITCH = 4.0  # corrugations: four a block
DEEP = 2.0   # the wall's skin, the ridges standing out of it to the face


def corr_north(tex, y0, y1, a0=0.0, a1=16.0, depth=0.8):
    """Vertical corrugations on the north face from x = a0 to a1: the trough skin and the ridges."""
    b = [[a0, y0, DEEP - depth, a1, y1, DEEP, tex]]
    x = a0 + 0.6
    while x + 2 <= a1:
        b.append([x, y0, DEEP - depth - 1.2, x + 2, y1, DEEP - depth, tex])
        x += PITCH
    return b


def corr_side(tex, side, y0, y1, a0, a1):
    """The same on the west (x = 0) or east (x = 16) face, from z = a0 to a1."""
    b = []
    for box in corr_north(tex, y0, y1, a0, a1):
        x0, yy0, z0, x1, yy1, z1 = box[:6]
        # x along the face becomes z; depth (z) becomes x from the west face, mirrored for the east
        if side == "west":
            b.append([z0, yy0, x0, z1, yy1, x1, tex])
        else:
            b.append([16 - z1, yy0, x0, 16 - z0, yy1, x1, tex])
    return b


def roof(tex, along, x0=0.0, x1=16.0, z0=0.0, z1=16.0):
    """The roof sheet a little under the rails and its shallow corrugations; along = the axis each
    ridge runs along (across the box)."""
    b = [[x0, 14.6, z0, x1, 15.3, z1, tex]]
    if along == "z":
        x = x0 + 0.6
        while x + 2 <= x1:
            b.append([x, 15.3, z0, x + 2, 15.8, z1, tex])
            x += PITCH
    else:
        z = z0 + 0.6
        while z + 2 <= z1:
            b.append([x0, 15.3, z, x1, 15.8, z + 2, tex])
            z += PITCH
    return b


def side(c, row):
    """A length of side wall: the corrugations, the rail at the top or bottom, the roof behind."""
    tex = "ct_" + c
    top, bot = row == "t", row == "b"
    y0, y1 = (2.6 if bot else 0), (13.4 if top else 16)
    b = corr_north(tex, y0, y1)
    if bot:
        b.append([0, 0, 0, 16, 2.6, DEEP, tex])
        b.append([0, 2.6, 0.2, 16, 3.0, DEEP, tex])
    if top:
        b.append([0, 13.4, 0, 16, 16, DEEP, tex])
        b.append([0, 13.0, 0.2, 16, 13.4, DEEP, tex])
        b += roof(tex, "z", z0=DEEP)
    b.append([0, 0, DEEP, 16, 14.6 if top else 16, 16, tex])
    return b


def top(c):
    tex = "ct_" + c
    return [[0, 0, 0, 16, 14.6, 16, tex]] + roof(tex, "z")


LOCK_BARS = {0: (7.0,), 1: (1.0, 15.0), 2: (9.0,)}


def end(c, row, col, door):
    """One of the nine cells across an end. The end face is on the north; the box's sides are the
    west face of col 0 and the east face of col 2."""
    tex = "ct_" + c
    top_, bot = row == "t", row == "b"
    west, east = col == 0, col == 2
    b = []
    # the face: corrugations between the corner posts, or the door leaves
    a0, a1 = (2.6 if west else 0), (13.4 if east else 16)
    y0, y1 = (2.6 if bot else 0), (13.4 if top_ else 16)
    if door:
        face = tex + "_code" if west and top_ else tex
        if col == 1:
            b += [[a0, y0, 0.6, 7.8, y1, DEEP, tex], [8.2, y0, 0.6, a1, y1, DEEP, tex]]
        else:
            b.append([a0, y0, 0.6, a1, y1, DEEP, face, {"north": [0, 0, 16, 16]} if face != tex else None])
        # the leaves' shallow ribs, three a leaf
        for x in ((4.5, 10.5) if west else (3.5, 12.5) if col == 1 else (5.5, 11.5)):
            if not (col == 1 and 7.4 < x < 8.6):
                b.append([x - 0.5, y0, 0.2, x + 0.5, y1, 0.6, tex])
        for x in LOCK_BARS[col]:
            b.append([x - 0.5, 0 if bot else -0.01, -0.9, x + 0.5, 16 if not top_ else 14.2, -0.1, "ext_galv"])
            if row == "m":
                # the handle, swung down against its leaf, and its retainer
                hx = x + (1 if x < 8 or col == 2 else -5)
                b.append([hx, 5.5, -1.6, hx + 4, 6.5, -0.9, "ext_galv"])
                b.append([hx + (3 if x < 8 or col == 2 else 0), 4.6, -1.1, hx + (4 if x < 8 or col == 2 else 1), 7.4, -0.1, "ext_galv"])
            if bot:
                b.append([x - 1.2, 2.6, -1.2, x + 1.2, 4.2, -0.1, "ext_galv"])  # the cam in its keeper
            if top_:
                b.append([x - 1.2, 11.8, -1.2, x + 1.2, 13.4, -0.1, "ext_galv"])
        if west or east:
            hx0 = 2.6 if west else 12.2
            for hy in ((3.5,) if bot else (6.5,) if row == "m" else (10.5,)):
                b.append([hx0, hy, -0.6, hx0 + 1.2, hy + 2.4, 0.6, "ext_galv"])  # a hinge
    else:
        b += corr_north(tex, y0, y1, a0, a1)
    # the corner posts and the box's sides round the corner
    if west:
        b.append([0, 0, 0, 2.6, 16, 2.6, tex])
        b += corr_side(tex, "west", y0, y1, 2.6, 16)
    if east:
        b.append([13.4, 0, 0, 16, 16, 2.6, tex])
        b += corr_side(tex, "east", y0, y1, 2.6, 16)
    # the sill and the header, and the side rails running back from them
    if bot:
        b.append([0, 0, 0, 16, 2.6, DEEP + (0.6 if door else 0), tex])
        if west:
            b.append([0, 0, 0, DEEP, 2.6, 16, tex])
        if east:
            b.append([16 - DEEP, 0, 0, 16, 2.6, 16, tex])
    if top_:
        b.append([0, 13.4, 0, 16, 16, DEEP + (1.0 if door else 0), tex])
        if west:
            b.append([0, 13.4, 0, DEEP, 16, 16, tex])
        if east:
            b.append([16 - DEEP, 13.4, 0, 16, 16, 16, tex])
        b += roof(tex, "x", DEEP if west else 0, 16 - DEEP if east else 16, DEEP + (1.0 if door else 0), 16)
    # the corner castings
    for x0 in ((0,) if west else ()) + ((13.4,) if east else ()):
        for cy in ((0,) if bot else ()) + ((13.6,) if top_ else ()):
            b.append([x0, cy, -0.2, x0 + 2.6, cy + 2.4, 2.6, "ct_casting"])
    # what's behind it all
    b.append([DEEP if west else 0, 0, DEEP + (1.0 if door and top_ else 0), 16 - DEEP if east else 16, 14.6 if top_ else 16, 16, tex])
    return b


def blocks():
    out = {}

    def add(name, boxes, title):
        out[name] = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q] for q in boxes],
                     "title": title, "sound": "METAL", "rotate": "facing", "shape": [[0, 0, 0, 16, 16, 16]]}

    for c in COLOURS:
        add(f"ct_{c}_side", side(c, "m"), "Shipping Container")
        add(f"ct_{c}_side_top", side(c, "t"), "Shipping Container")
        add(f"ct_{c}_side_bot", side(c, "b"), "Shipping Container")
        add(f"ct_{c}_roof", top(c), "Shipping Container")
        for row in "bmt":
            for col in range(3):
                add(f"ct_{c}_end_{row}{col}", end(c, row, col, False), "Shipping Container")
                add(f"ct_{c}_door_{row}{col}", end(c, row, col, True), "Container Doors")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("ct_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} container blocks in {path}")


if __name__ == "__main__":
    main()
