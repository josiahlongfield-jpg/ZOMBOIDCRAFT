"""The mm_ kit: the old field guns either side of a town's war memorial, in place of an anvil between
two blackstone walls (Josiah, 2026-10-29: "Everything in this world must be of the same level of
detail and effort"). world/county/Landmarks.memorial sets them out.

rotate=facing, the muzzle to the north:

- mm_gun: the breech end: the carriage's cheeks on the axle, the two spoked wheels with their
  iron tyres, the barrel's breech and cascabel on its trunnions, the trail running back south to
  its spade on the ground;
- mm_gun_muzzle: the chase of the barrel out over the block in front, the swell of the muzzle;
- mm_wreath: a wreath long dead, propped on its little stand, the ribbon faded;
- mm_die, mm_shaft_<0..3>, mm_shaft_top: the obelisk in grey granite: the die with the
  dedication cut in its north face, the shaft's courses tapering up to the pyramidion.

    python3 tools/city/memorial.py      rewrites the mm_ entries in blocks.json

photo_textures.py draws the textures (memorial.register()).
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import stencil  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BRONZE, WOOD, IRON = "mm_bronze", "rs_timber", "fw_steel"


def bronze(seed):
    """Gun bronze gone to verdigris: green-blue bloom over brown metal, streaked where the rain runs
    off, polished dull gold where a century of hands have patted it."""
    t = F.plain(seed, (74, 92, 76), 0.1, 0.3)
    t.f0[:] = 40
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.3)))
    t.mix((n > 0.35).astype("float32") * 0.7, (112, 84, 52), 0.8)
    weather(t, rust=0.0, streak=0.8, grime=0.25)
    return t


def leaves(seed):
    """A dead wreath's leaves: laurel gone brown and grey, a few still dull green."""
    t = F.plain(seed, (96, 84, 60), 0.12, 0.1)
    t.speckle(0.2, (70, 82, 52), 0.6, 0.3)
    t.speckle(0.15, (130, 116, 92), 0.5, 0.2)
    t.grime(0.3, (50, 44, 34))
    return t


def granite(seed, text=None):
    """Grey granite, the polish long gone, lichen and the rain's black streaks down it; with the
    dedication cut into it and the letters gone dark."""
    t = F.plain(seed, (150, 148, 142), 0.1, 0.3)
    t.speckle(0.25, (110, 108, 104), 0.5, 0.0)
    t.speckle(0.15, (184, 182, 176), 0.4, 0.0)
    if text:
        stencil(t, text, (58, 56, 52), 9, x=4, y=6, w=N - 8, h=N - 12, chip=0.05)
    weather(t, rust=0.0, streak=0.9, grime=0.3)
    t.speckle(0.03, (150, 160, 110), 0.5, 0.1)
    return t


def textures():
    return {"mm_bronze": lambda: bronze(49480), "mm_leaves": lambda: leaves(49481), "mm_granite": lambda: granite(49482),
            "mm_die": lambda: granite(49483, "IN MEMORY|OF THE MEN OF|GRAYHAVEN COUNTY|WHO GAVE ALL|1917 - 1918")}


def register(ns):
    ns.EXTRA.update(textures())


def wheel(x0, x1):
    """A spoked wheel in the y-z plane on the axle at y 7, z 8: the iron tyre, the felloes, six
    spokes, the hub."""
    b = [[x0, 0, 4, x1, 1.2, 12, IRON], [x0, 12.8, 4, x1, 14, 12, IRON], [x0, 4, 1, x1, 10, 2.2, IRON], [x0, 4, 13.8, x1, 10, 15, IRON]]
    for y0, z0, y1, z1, oy, oz in ((1, 2, 3.2, 4, 2, 3), (1, 12, 3.2, 14, 2, 13), (10.8, 2, 13, 4, 12, 3), (10.8, 12, 13, 14, 12, 13)):
        b.append([x0, y0, z0, x1, y1, z1, IRON])
    m = (x0 + x1) / 2
    b += [[m - 0.4, 1, 7.4, m + 0.4, 13, 8.6, WOOD], [m - 0.4, 6.4, 2, m + 0.4, 7.6, 14, WOOD],
          [m - 0.4, 1.2, 7.4, m + 0.4, 12.8, 8.6, WOOD, None, {"axis": "x", "angle": 45, "origin": [m, 7, 8]}],
          [m - 0.4, 1.2, 7.4, m + 0.4, 12.8, 8.6, WOOD, None, {"axis": "x", "angle": -45, "origin": [m, 7, 8]}],
          [x0 - 0.6, 5.5, 6.5, x1 + 0.6, 8.5, 9.5, IRON]]
    return b


def gun():
    b = wheel(0.5, 2) + wheel(14, 15.5)
    b += [[2, 6.4, 7.4, 14, 7.6, 8.6, IRON],  # the axle
          [4, 6, 2, 5.5, 11, 16, WOOD], [10.5, 6, 2, 12, 11, 16, WOOD],  # the cheeks
          [5.5, 3, 12, 10.5, 5, 16, WOOD], [6, 1, 15, 10, 3.5, 16, IRON],  # the trail running back, its spade
          [5.5, 9.5, 2, 10.5, 14, 13, BRONZE], [6, 10, 13, 10, 13.5, 14.5, BRONZE], [7, 11, 14.5, 9, 12.5, 15.6, BRONZE],
          [4.5, 11, 5.5, 11.5, 12.6, 7, BRONZE]]
    return b


def muzzle():
    return [[6, 10, 0, 10, 13.5, 16, BRONZE], [5.6, 9.6, 0, 10.4, 13.9, 2, BRONZE], [6.3, 10.3, 14, 9.7, 13.2, 16, BRONZE],
            [7.3, 11.2, -0.1, 8.7, 12.6, 0.2, IRON]]


def wreath():
    leaf = "mm_leaves"
    return [[3, 4, 7.5, 13, 6, 9, leaf], [3, 13, 7.5, 13, 15, 9, leaf], [2, 6, 7.5, 4, 13, 9, leaf], [12, 6, 7.5, 14, 13, 9, leaf],
            [6, 2, 7.2, 10, 4.5, 8.6, "rs_paper_b0"], [7.6, 0, 9, 8.4, 9, 9.8, WOOD],
            [7.6, 0, 9, 8.4, 9, 9.8, WOOD, None, {"axis": "x", "angle": 22.5, "origin": [8, 9, 9.4]}]]


GRANITE = "mm_granite"


def die():
    """The die: the inscribed block on the base, its moulded cap."""
    face = {"north": "mm_die", "side": GRANITE, "up": GRANITE, "down": GRANITE}
    return [[2, 0, 2, 14, 13, 14, face], [1.5, 0, 1.5, 14.5, 1.5, 14.5, GRANITE], [1.4, 13, 1.4, 14.6, 14.6, 14.6, GRANITE],
            [2.4, 14.6, 2.4, 13.6, 16, 13.6, GRANITE]]


def shaft(k):
    """A course of the shaft, k from the bottom, tapering half a pixel a side each course."""
    h = 4.6 - k * 0.5
    return [[8 - h, 0, 8 - h, 8 + h, 16, 8 + h, GRANITE]]


def pyramidion():
    b = []
    for i, h in enumerate((2.6, 2.0, 1.4, 0.8)):
        b.append([8 - h, i * 2, 8 - h, 8 + h, i * 2 + 2, 8 + h, GRANITE])
    return b


def blocks():
    out = {}

    def add(name, boxes, title, shape, sound="METAL"):
        out[name] = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                     "title": title, "sound": sound, "rotate": "facing", "shape": shape, "render": "cutout"}

    add("mm_gun", gun(), "Field Gun", [[0.5, 0, 1, 15.5, 14, 16]])
    add("mm_gun_muzzle", muzzle(), "Field Gun", [[5.6, 9.6, 0, 10.4, 13.9, 16]])
    add("mm_wreath", wreath(), "Wreath", [[2, 0, 7, 14, 15, 10]], "GRASS")
    add("mm_die", die(), "Memorial", [[1.5, 0, 1.5, 14.5, 16, 14.5]], "STONE")
    for k in range(4):
        add(f"mm_shaft_{k}", shaft(k), "Memorial", [[3.4, 0, 3.4, 12.6, 16, 12.6]], "STONE")
    add("mm_shaft_top", pyramidion(), "Memorial", [[5.4, 0, 5.4, 10.6, 8, 10.6]], "STONE")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("mm_")}
    made = blocks()
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} memorial blocks in {path}")


if __name__ == "__main__":
    main()
