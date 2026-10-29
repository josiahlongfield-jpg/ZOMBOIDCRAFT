"""The gz_ kit: the park bandstand, in place of a pale oak floor, oak fences and a lightning rod for a
finial (Josiah, 2026-10-29: "Everything in this world must be of the same level of detail and
effort"). From the refs in city/city-park (01, 02): a raised deck on a boarded skirt, cream turned
posts with scroll brackets, a spindle frieze under the plate, a balustrade with a gap at the steps,
the hipped roof (the house kit's slate, laid by Bldg.hip) and the spire on top. world/county/
Kit.gazebo sets it out.

- gz_deck: the deck: grey boards on top, the green boarded skirt round the sides, a cream fascia;
- gz_step: the steps up to the deck, the high tread on the north (toward the deck), rotate=facing;
- gz_post: a turned post, its plinth and collar; gz_post_top: the same up to the plate with its
  capital and the scroll brackets out to the south and the east (rotate=facing: north for the
  north-west corner, east for north-east, south for south-east, west for south-west);
- gz_frieze: the plate between two posts with the spindles hung under it, rotate=axis (drawn along z);
- gz_rail / gz_rail_broken: the balustrade, top and bottom rails and turned balusters, and the same
  kicked in, the top rail snapped and hanging, rotate=axis;
- gz_finial: the spire: the drum, the ball, the spike.

    python3 tools/city/gazebo.py      rewrites the gz_ entries in blocks.json

photo_textures.py draws the textures (gazebo.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import chips  # noqa: E402

N = P.N
HERE = Path(__file__).parent
CREAM, GREEN, BOARDS = "gz_cream", "gz_green", "gz_boards"


def paint(seed, rgb):
    """Gloss paint over softwood gone chalky, flaking along the grain to the grey wood, mildew low down."""
    t = F.plain(seed, rgb, 0.05, 0.35)
    for x in range(0, N, N // 8):
        t.c[:, x] *= 0.9
    chips(t, 0.1, under=(118, 110, 98))
    weather(t, rust=0.0, streak=0.5, grime=0.3)
    t.speckle(0.03, (70, 84, 60), 0.5, 0.1)
    return t


def boards(seed):
    """The deck: grey boards, the paint long walked off, the seams dark, a nail head at each joist."""
    t = F.mat("Wood065", (124, 116, 104), 0.6, rot=True, seed=seed)
    yy, xx = np.mgrid[0:N, 0:N]
    seam = (xx % (N // 4)) < 1
    t.c[seam] *= 0.45
    t.h[seam] = 0.1
    for x in range(N // 8, N, N // 4):
        for y in (N // 4, 3 * N // 4):
            t.rect(x - 1, y - 1, x + 1, y + 1, col=(70, 66, 60), h=0.6)
    t.grime(0.35, (60, 54, 46))
    return t


def skirt(seed):
    """The skirt: green shiplap boards, lapped, the paint blistered, rot along the bottom board."""
    t = paint(seed, (58, 92, 78))
    for y in range(0, N, N // 4):
        t.rect(0, y, N, y + 2, col=(36, 52, 44), h=0.2)
    t.grime(0.4, (40, 36, 30))
    return t


def textures():
    return {"gz_cream": lambda: paint(49490, (222, 214, 188)), "gz_green": lambda: paint(49491, (58, 92, 78)),
            "gz_boards": lambda: boards(49492), "gz_skirt": lambda: skirt(49493)}


def register(ns):
    ns.EXTRA.update(textures())


def deck():
    side = {"side": "gz_skirt", "up": BOARDS, "down": BOARDS}
    return [[0, 0, 0, 16, 13, 16, side], [0, 13, 0, 16, 16, 16, {"side": CREAM, "up": BOARDS, "down": BOARDS}],
            [-0.3, 12, -0.3, 16.3, 13, 16.3, GREEN]]


def step():
    tread = {"side": CREAM, "up": BOARDS, "down": CREAM}
    return [[1, 0, 0, 15, 16, 8, tread], [1, 0, 8, 15, 8, 16, tread],
            [0, 0, 0, 1, 16, 8, GREEN], [15, 0, 0, 16, 16, 8, GREEN], [0, 0, 8, 1, 8, 16, GREEN], [15, 0, 8, 16, 8, 16, GREEN]]


def post(top):
    b = [[6, 0, 6, 10, 16, 10, CREAM], [5, 0, 5, 11, 3, 11, GREEN], [5.4, 3, 5.4, 10.6, 3.6, 10.6, CREAM]]
    if not top:
        b.append([5.5, 9, 5.5, 10.5, 10, 10.5, GREEN])
        return b
    b += [[5.5, 12, 5.5, 10.5, 13, 10.5, GREEN], [5, 14, 5, 11, 16, 11, CREAM]]
    # the scroll brackets: stepped out to the plate, south and east as drawn
    for y0, y1, reach in ((13, 16, 16), (10, 13, 13.5), (7, 10, 11.5)):
        b.append([7.4, y0, 10, 8.6, y1, reach, CREAM])
        b.append([10, y0, 7.4, reach, y1, 8.6, CREAM])
    return b


def frieze():
    b = [[6.5, 13, 0, 9.5, 16, 16, CREAM], [6, 15.4, 0, 10, 16, 16, GREEN], [7.2, 8.4, 0, 8.8, 9.2, 16, CREAM]]
    for z in range(1, 16, 2):
        b.append([7.6, 9.2, z, 8.4, 13, z + 0.8, CREAM])
    for z in (4, 12):
        b.append([7.4, 6.8, z - 0.8, 8.6, 8.4, z + 0.8, GREEN])
    return b


def rail(broken):
    b = [[7, 0, 0, 9, 2.5, 16, CREAM]]
    zs = (1.5, 4.5, 7.5, 10.5, 13.5)
    keep = zs if not broken else (1.5, 13.5)
    for z in keep:
        b += [[7.3, 2.5, z, 8.7, 9, z + 1.2, CREAM], [6.9, 4.5, z - 0.2, 9.1, 6, z + 1.4, GREEN]]
    if broken:
        b += [[6.5, 9, 0, 9.5, 10.5, 6, CREAM], [6.5, 9, 10, 9.5, 10.5, 16, CREAM],
              [6.5, 6, 6, 9.5, 7.5, 11, CREAM, None, {"axis": "x", "angle": -45, "origin": [8, 7, 6]}],
              [7.3, 2.5, 8, 8.7, 6, 9.2, CREAM, None, {"axis": "x", "angle": 22.5, "origin": [8, 2.5, 8.6]}]]
    else:
        b.append([6.5, 9, 0, 9.5, 10.5, 16, CREAM])
    return b


def finial():
    return [[5.5, 0, 5.5, 10.5, 2, 10.5, GREEN], [6.3, 2, 6.3, 9.7, 4, 9.7, CREAM], [6.6, 4, 6.6, 9.4, 6.6, 9.4, "mm_bronze"],
            [7.3, 6.6, 7.3, 8.7, 8, 8.7, GREEN], [7.6, 8, 7.6, 8.4, 15, 8.4, "front_iron"],
            [5, 11, 7.8, 11, 11.4, 8.2, "front_iron"], [7.8, 11, 5, 8.2, 11.4, 11, "front_iron"]]


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate=None, cutout=True, sound="WOOD"):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "shape": shape}
        if rotate:
            spec["rotate"] = rotate
        if cutout:
            spec["render"] = "cutout"
        out[name] = spec

    add("gz_deck", deck(), "Bandstand", [[0, 0, 0, 16, 16, 16]], cutout=False)
    add("gz_step", step(), "Bandstand Steps", [[0, 0, 0, 16, 16, 8], [0, 0, 8, 16, 8, 16]], "facing", cutout=False)
    add("gz_post", post(False), "Bandstand Post", [[5, 0, 5, 11, 16, 11]])
    add("gz_post_top", post(True), "Bandstand Post", [[5, 0, 5, 11, 16, 11]], "facing")
    add("gz_frieze", frieze(), "Bandstand Frieze", [[6.5, 8.4, 0, 9.5, 16, 16]], "axis")
    add("gz_rail", rail(False), "Balustrade", [[6.5, 0, 0, 9.5, 16, 16]], "axis")
    add("gz_rail_broken", rail(True), "Balustrade", [[6.5, 0, 0, 9.5, 10.5, 16]], "axis")
    add("gz_finial", finial(), "Bandstand Spire", [[5.5, 0, 5.5, 10.5, 15, 10.5]], sound="METAL")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("gz_")}
    made = blocks()
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} bandstand blocks in {path}")


if __name__ == "__main__":
    main()
