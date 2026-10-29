"""The rs_ kit: a roadside billboard built to the landmarks' detail in place of a wall of concrete and
terracotta blocks on spruce fences (Josiah, 2026-10-29: "Everything in this world must be of the same
level of detail and effort"). world/county/RoadsideSite.billboard lays it out.

Every piece is drawn with the road on its north (z = 0), rotate=facing:

- rs_paper_<a..e><0..2>: a block of the face (three of each colour, so a face of them doesn't
  read as one block repeated), the poster sheets pasted over plywood, sun-faded, the top
  sheet peeling back to the old ad under it and the bare ply; the seams of the sheets; rust weeping
  from the frame; rs_paper_curl_<a|b> the same with a sheet hanging off it, curled;
- rs_ply: the bare face where the sheets and the boards blew off; rs_frame: the timber moulding round
  the face, standing proud of it;
- rs_batten / rs_stringer: the back of the face, an upright and the rails, or the rails alone;
- rs_brace: the kicker from the ground up to a leg, on its footing;
- rs_walk: the catwalk under the face, galvanised grating, the rail on the road side;
- rs_lamp(_lit): a gooseneck lamp on its arm out from the catwalk, the shade turned back to the face;
- rs_meter / rs_meter_bent: a parking meter on the sidewalk, the dial's window on the north, the
  red EXPIRED flag up in it; the bent one knocked over at the post by a car.

    python3 tools/city/roadside.py      rewrites the rs_ entries in blocks.json

photo_textures.py draws the textures (roadside.register()).
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
GALV = "st_galv"

# the top sheet, the old ad's paper under it
PAPERS = {"a": ((214, 208, 190), (70, 118, 160)), "b": ((172, 58, 46), (226, 190, 70)),
          "c": ((78, 116, 152), (214, 208, 190)), "d": ((222, 190, 72), (164, 52, 42)),
          "e": ((206, 204, 196), (96, 132, 92))}


# ---------------------------------------------------------------- textures

def ply_base(seed):
    t = F.mat("Wood049", (126, 118, 104), 0.55, seed=seed)
    t.vary(0.05)
    t.grime(0.18, (58, 52, 44))
    return t


def ply(seed):
    """Bare face ply: weathered grey, a sheet's joint across it, the nail lines, the glue's ghost."""
    t = ply_base(seed)
    t.rect(0, N // 2 - 1, N, N // 2, col=(52, 48, 42), h=0.4)
    for x in range(3, N, 10):
        for y in (4, N // 2 + 4, N - 5):
            t.rect(x, y, x + 1, y + 1, col=(70, 56, 44), h=0.5)
    t.streaks(0.8, 0.7)
    return t


def paper(seed, key):
    """A block of the poster: the top sheet faded and blistered, peeling back in sheets to the old
    ad under it and in places to the bare ply, a lifted paper edge round every tear; the sheets'
    seams; rain and rust weeping down."""
    top, under = PAPERS[key]
    t = F.mat("Paper001", top, 0.35, seed=seed)
    t.vary(0.07)
    under_c = np.array(under, np.float32)
    n1 = P.fbm(t.rng, ((2, 1.0), (4, 0.8), (8, 0.4), (16, 0.2)))
    n2 = P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.3)))
    thr = {"a": 0.25, "b": 0.15, "c": 0.3, "d": 0.05, "e": 0.35}[key]
    peel = n1 > thr
    bare = peel & (n2 > 0.35)
    wood = ply_base(seed + 7)
    t.c[peel] = under_c * (0.85 + 0.25 * (n2[peel, None] * 0.5 + 0.5))
    t.h[peel] = 0.62
    t.c[bare] = wood.c[bare]
    t.h[bare] = 0.5
    edge = peel & ~(np.roll(peel, 1, 0) & np.roll(peel, -1, 0) & np.roll(peel, 1, 1) & np.roll(peel, -1, 1))
    t.c[edge] = (236, 232, 220)
    t.h[edge] = 0.95
    shade = np.roll(edge, 1, 0) & peel & ~edge
    t.c[shade] *= 0.6
    # the sheets' seams: one down the middle, one across the top quarter
    for x in (N // 2,):
        t.c[:, x] *= 0.72
        t.c[:, x + 1] = t.c[:, x + 1] * 0.85 + 30
        t.h[:, x] = 0.55
    t.c[N // 4, :] *= 0.78
    t.c *= 1 + 0.08 * (P.fbm(t.rng) > 0.4)[..., None]  # blisters
    t.streaks(1.0, 0.66)
    t.grime(0.22, (66, 58, 46))
    t.smooth[:] = 0.12
    return t


def timber(seed):
    """The frame and the back's timber: grey weathered softwood, the grain up the board, rain-dark."""
    t = F.mat("Wood065", (116, 108, 96), 0.6, rot=True, seed=seed)
    t.vary(0.06)
    t.streaks(0.7, 0.72)
    t.grime(0.2, (52, 46, 40))
    return t


def meter(seed):
    """The parking meter's head: a dull grey casting, the dome window with the dial behind it,
    the red EXPIRED flag up, the coin slot and the key lock under, scratched and fly-posted."""
    t = F.plain(seed, (92, 96, 94), 0.06, 0.45)
    t.rect(10, 6, N - 10, N // 2 + 6, col=(150, 162, 168), h=0.9, smooth=0.9)
    t.rect(13, 9, N - 13, N // 2 + 3, col=(220, 214, 196), h=0.85, smooth=0.6)
    t.rect(N // 2 - 2, 12, N // 2 + 8, 20, col=(196, 36, 32), h=0.9)
    t.rect(N // 2 - 1, 22, N // 2 + 1, N // 2, col=(40, 40, 40), h=0.9)
    t.rect(N // 2 - 4, N - 18, N // 2 + 4, N - 16, col=(24, 24, 24), h=0.2)
    t.rect(N // 2 - 2, N - 10, N // 2 + 2, N - 6, col=(170, 160, 120), h=0.8, smooth=0.7)
    t.grime(0.3, (52, 50, 46))
    t.streaks(0.6, 0.75)
    return t


def textures():
    out = {"rs_ply": lambda: ply(49400), "rs_timber": lambda: timber(49401), "rs_meter": lambda: meter(49402)}
    for i, k in enumerate(PAPERS):
        for j in range(3):
            out[f"rs_paper_{k}{j}"] = lambda k=k, i=i, j=j: paper(49410 + i * 3 + j, k)
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

def face(k):
    return [[0, 0, 0, 16, 16, 16, {"north": "rs_paper_" + k, "side": "rs_ply", "up": "rs_ply", "down": "rs_ply"}]]


def curl(k, which):
    """A face block with a sheet hanging off it: torn along the top, sagging out from the face and
    curling back at its foot."""
    tex = {"north": "rs_paper_" + k, "south": "rs_paper_" + k, "side": "rs_ply", "up": "rs_ply", "down": "rs_ply"}
    b = face(k)
    if which == "a":
        b += [[1, 7, -0.6, 9, 15, 0, tex, None, {"axis": "x", "angle": 22.5, "origin": [5, 15, 0]}],
              [1, 3, -4.0, 9, 7.2, -3.4, tex, None, {"axis": "x", "angle": -22.5, "origin": [5, 7, -3.7]}]]
    else:
        b += [[6, 2, -0.6, 15, 12, 0, tex, None, {"axis": "z", "angle": 22.5, "origin": [6, 12, 0]}],
              [5, 1, -0.6, 10, 6, 0, tex, None, {"axis": "x", "angle": 45, "origin": [7.5, 6, 0]}]]
    return b


def frame():
    """The moulding: the board round the face, its front edge standing 1.5 px proud, a fillet."""
    return [[0, 0, 0, 16, 16, 16, "rs_timber"], [0, 0, -1.5, 16, 16, 0, "rs_timber"],
            [0, 6.5, -2.2, 16, 9.5, -1.5, "rs_timber"]]


def rails():
    return [[0, 2.5, 0, 16, 5, 2.2, "rs_timber"], [0, 11, 0, 16, 13.5, 2.2, "rs_timber"]]


def batten():
    return rails() + [[6.5, 0, 2.2, 9.5, 16, 5.5, "rs_timber"], [7, 3, 2.2, 9, 4.5, 2.6, GALV], [7, 11.5, 2.2, 9, 13, 2.6, GALV]]


def brace():
    """The kicker: a 3 x 3 timber from its footing at the south foot up into the leg on the north
    (a pole_wood, its face 5.5 px into the next cell), strapped to it."""
    turn = {"axis": "x", "angle": 45, "origin": [8, 11.25, 4.75]}
    return [[6.5, 9.75, -9.75, 9.5, 12.75, 19.25, "rs_timber", None, turn],
            [5, 0, 12, 11, 2, 16, "bw_concrete"], [5.5, 19, -6.2, 10.5, 23, -5.5, GALV]]


def walk():
    """The catwalk: a strip of grating on two stringers, a toe board and the rail on the road side."""
    b = [[0, 0, 0, 16, 1, 16, "grate"], [0, -2, 0.5, 16, 0, 2, GALV], [0, -2, 13, 16, 0, 14.5, GALV],
         [0, 1, 0, 16, 3, 0.6, GALV], [0, 7.6, 0.2, 16, 8.6, 1.2, GALV], [0, 14.2, 0, 16, 15.4, 1.4, GALV],
         [0.4, 1, 0.2, 1.6, 14.2, 1.4, GALV], [8.4, 1, 0.2, 9.6, 14.2, 1.4, GALV],
         [7, -6, 13.5, 9, -2, 15.5, GALV, None, {"axis": "x", "angle": -45, "origin": [8, -4, 14.5]}]]
    return b


def lamp(lit):
    """A gooseneck lamp: its arm bolted under the catwalk's south edge, out north and up, the shade
    turned back over toward the face, the lens under it."""
    lens = "cp_lens_lit" if lit else "cp_lens"
    tilt = {"axis": "x", "angle": 22.5, "origin": [8, 13, 6]}
    return [[7, 1, 9, 9, 3, 16, GALV], [7.2, 1, 7, 8.8, 12, 8.8, GALV],
            [7.2, 10, 7.4, 8.8, 11.6, 9.5, GALV, None, {"axis": "x", "angle": 45, "origin": [8, 11, 8]}],
            [5, 11, 2.5, 11, 14, 9.5, "cp_lamp", None, tilt],
            [5.5, 10.4, 3, 10.5, 11, 9, {"down": lens, "side": "cp_lamp", "up": "cp_lamp"}, None, tilt]]


def meter_head():
    head = {"north": "rs_meter", "side": GALV, "up": GALV, "down": GALV}
    return [[5, 11, 6, 11, 15.5, 10, head], [5.5, 15.5, 6.5, 10.5, 16.5, 9.5, GALV], [6.5, 10, 7, 9.5, 11, 9, GALV]]


def blocks():
    out = {}

    def add(name, boxes, title, sound="WOOD", shape=None, cutout=False, light=None):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q] for q in boxes],
                "title": title, "sound": sound, "rotate": "facing", "shape": shape or [[0, 0, 0, 16, 16, 16]]}
        if cutout:
            spec["render"] = "cutout"
        if light:
            spec["light"] = light
            spec["decor"] = True
        out[name] = spec

    for k in PAPERS:
        for j in range(3):
            add(f"rs_paper_{k}{j}", face(f"{k}{j}"), "Billboard")
    for k in "ab":
        add("rs_paper_curl_" + k, curl("b1" if k == "a" else "c2", k), "Billboard")
    out["rs_ply"] = {"tex": "rs_ply", "title": "Plywood", "sound": "WOOD"}
    add("rs_frame", frame(), "Billboard Frame")
    add("rs_batten", batten(), "Billboard Back", shape=[[0, 0, 0, 16, 16, 5.5]])
    add("rs_stringer", rails(), "Billboard Back", shape=[[0, 2.5, 0, 16, 13.5, 2.2]])
    add("rs_brace", brace(), "Brace", shape=[[5, 0, 0, 11, 16, 16]], cutout=False)
    add("rs_walk", walk(), "Catwalk", "METAL", shape=[[0, 0, 0, 16, 1, 16], [0, 1, 0, 16, 15.4, 1.4]], cutout=True)
    for lit in (False, True):
        add("rs_lamp" + ("_lit" if lit else ""), lamp(lit), "Billboard Lamp", "METAL", shape=[[5, 1, 2, 11, 15, 16]],
            light=12 if lit else None)
    add("rs_meter", [[7.2, 0, 7.2, 8.8, 10, 8.8, GALV], [6.5, 0, 6.5, 9.5, 0.6, 9.5, GALV]] + meter_head(), "Parking Meter",
        "METAL", shape=[[5, 0, 6, 11, 16, 10]], cutout=True)
    lean = {"axis": "x", "angle": 45, "origin": [8, 1, 8]}
    add("rs_meter_bent", [[6.5, 0, 6.5, 9.5, 0.6, 9.5, GALV], [7.2, 0, 7.2, 8.8, 1.5, 8.8, GALV]]
        + [q + [None, lean] for q in [[7.2, 1, 7.2, 8.8, 10, 8.8, GALV]] + meter_head()], "Parking Meter", "METAL",
        shape=[[5, 0, 0, 11, 6, 10]], cutout=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    clash = [k for k in data["blocks"] if k.startswith("rs_")]
    made = blocks()
    stray = [k for k in clash if k not in made and not k.startswith("rs_paper_")]
    if stray:
        sys.exit(f"rs_ prefix already used by {stray[:5]}")
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("rs_")}
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} billboard blocks in {path}")


if __name__ == "__main__":
    main()
