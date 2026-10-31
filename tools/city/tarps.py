"""The tp_ kit: the tarps that were still full green cubes on the farms (a stack of hay with a block
of tarp on it, a barn's rolled-up side curtain drawn as a block of tarp). From refs/farm/barn-interior
and dairy-barn: the sheet pulled over the top bales and roped down, weighed with old tyres, blown
half off; the curtain rolled up under the eave on its ropes. world/county/FarmSite.hayStack and
Farmstead set them out.

- tp_bales / tp_bales_tyre: a course of bales with the tarp laid over the top, the ropes across it;
  the same with a tyre on it to hold it down (rotate=axis, the bales along z as fm_bales_low);
- tp_bales_blown: the tarp blown half back, folded on itself, the far edge lifting (rotate=facing:
  the bare half to the north);
- tp_roll: the side curtain rolled up under the eave, the ropes it's lashed with (rotate=axis,
  the roll along z); tp_roll_torn: the same with a torn strip hanging down from it.

    python3 tools/city/tarps.py      rewrites the tp_ entries in blocks.json

Uses the shared tarp_green, tire_wall and fm_sq_* textures; draws none of its own.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from clutter import ring  # noqa: E402

HERE = Path(__file__).parent
TARP, ROPE = "tarp_green", "tire"
BALE = {"north": "fm_sq_end", "south": "fm_sq_end", "east": "fm_sq_side", "west": "fm_sq_side", "up": "fm_sq_top", "down": "fm_sq_top"}


def bales():
    """The lower course of two bales, as fm_bales_low lays them."""
    return [[0.28, 0, 0.68, 7.88, 7.8, 16, BALE], [8.37, 0, 0, 15.97, 7.8, 15.47, BALE]]


def covered(tyre=False):
    b = bales() + [[0, 7.8, 0, 16, 8.3, 16, TARP],
                   [3, 8.3, 0, 4.6, 8.7, 16, TARP], [10.5, 8.3, 0, 12, 8.6, 16, TARP]]
    b += [[0, 8.3, 7.6, 16, 8.7, 8.2, ROPE], [7.7, 8.3, 0, 8.3, 8.7, 16, ROPE]]
    if tyre:
        b += ring("y", (8, 8), 6.2, 3.6, 8.3, 11.3, "tire_wall")
    return b


def blown():
    """The south half still covered, the sheet folded back on itself over the middle, the bales bare
    to the north and a corner of the fold lifting in the wind."""
    b = bales() + [[0, 7.8, 8, 16, 8.3, 16, TARP], [0, 8.3, 6.5, 16, 9.6, 9, TARP],
                   [0, 9.6, 7, 16, 10, 10.5, TARP]]
    b.append([0, 9.6, 2, 7, 10, 7, TARP, None, {"axis": "x", "angle": -22.5, "origin": [4, 9.6, 7]}])
    b.append([7.7, 8.3, 9, 8.3, 8.7, 16, ROPE])
    return b


def roll(torn=False):
    """The curtain rolled up along z at the top of the open side, lashed every half block."""
    b = [[6, 10, 0, 10, 14, 16, TARP], [5.4, 10.6, 0, 10.6, 13.4, 16, TARP]]
    for z in (3, 11):
        b += [[5.1, 10.2, z, 10.9, 14.2, z + 0.6, ROPE], [7.7, 14.2, z, 8.3, 16, z + 0.6, ROPE]]
    if torn:
        b += [[7.6, 2, 5, 8.2, 10.6, 9.5, TARP], [7.6, 0, 6, 8.2, 2, 8, TARP, None, {"axis": "z", "angle": 22.5, "origin": [8, 2, 7]}]]
    return b


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate, sound="WOOL"):
        out[name] = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                     "title": title, "sound": sound, "rotate": rotate, "shape": shape, "render": "cutout"}

    add("tp_bales", covered(), "Hay Bales", [[0, 0, 0, 16, 8.7, 16]], "axis", "GRASS")
    add("tp_bales_tyre", covered(True), "Hay Bales", [[0, 0, 0, 16, 11.3, 16]], "axis", "GRASS")
    add("tp_bales_blown", blown(), "Hay Bales", [[0, 0, 0, 16, 10, 16]], "facing", "GRASS")
    add("tp_roll", roll(), "Rolled Curtain", [[5.1, 10, 0, 10.9, 16, 16]], "axis")
    add("tp_roll_torn", roll(True), "Rolled Curtain", [[5.1, 0, 0, 10.9, 16, 16]], "axis")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    # in place: blocks.json is append-only, its order is the registry's
    data["blocks"] = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith("tp_") or k in made}
    data["blocks"].update({k: v for k, v in made.items() if k not in data["blocks"]})
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} tarp blocks in {path}")


if __name__ == "__main__":
    main()
