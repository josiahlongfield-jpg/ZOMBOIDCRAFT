"""The rd_ kit: round walls that read as round. A silo, grain bin or tank built of whole blocks steps
round its circle like a staircase; these pieces put a thin panel through each block the circle
passes, turned to the circle's tangent and set at its radius, so the wall is a smooth sixteen-sided
drum (Josiah, 2026-10-29: no basic block-shaped stand-ins next to detailed builds). Round.java
picks the piece for each block.

A piece's panel faces out to the model's north, tangent along x. Sixteen bearings are four facings
times four turns (0, -22.5, +22.5, +45 about y); nine depths (-8 to +8 px out from the block's
middle, every 2) put it on the circle. Names: rd_{mat}_{turn}_{depth} with turn 0, m22, p22, p45
and depth 0..8.

- walls: stave (concrete staves), stave_hoop (with its steel hoop), blue (glass-lined), bin
  (corrugated grain bin), brick;
- a roof's tiers: rd_{galv,blue,tinred}_{s,m}_{turn}_{depth}, a block-high ring of four courses
  stepping in 2 px (s, steep) or 4 px (m, 45 degrees) each, filled flat behind; the top of the roof
  is rd_{galv,blue,tinred}_flat;
- rd_cage: a ladder in its safety cage (climbable); rd_finial: the dome's cap and rod;
  rd_bin_cap: a grain bin's top ring, lid and vent; rd_fan: a bin's aeration fan.

    python3 tools/city/round.py          rewrites the rd_ entries in blocks.json
"""
import json
import math
from pathlib import Path

HERE = Path(__file__).parent
TURNS = {"0": 0.0, "m22": -22.5, "p22": 22.5, "p45": 45.0}
DEPTHS = [-8, -6, -4, -2, 0, 2, 4, 6, 8]
INSIDE = "concrete_dark"
WALLS = {"stave": "silo_stave", "blue": "silo_blue", "bin": "bin_ribbed", "brick": "brick_red",
         "field": "fm_fieldstone", "tank": "ext_galv", "crib": "fm_mesh", "paddock": "fm_ring_boards"}
# what each wall shows inside (a stock tank and a corn crib's mesh are the same both sides)
INNER = {"tank": "ext_galv", "crib": "fm_mesh", "field": "fm_fieldstone", "paddock": "fm_ring_boards"}
TITLES = {"stave": "Stave Silo", "blue": "Silo", "bin": "Grain Bin", "brick": "Brick Silo", "field": "Stone Curb",
          "tank": "Stock Tank", "crib": "Corn Crib", "paddock": "Round Pen"}
TIERS = {"galv": "ext_galv", "blue": "silo_blue", "tinred": "ext_tin_red_tread"}
GALV = "ext_galv"


def length(turn):
    """The panel's length: long enough that neighbours meet wherever the circle crosses the grid
    (checked for radii 1.5 to 12 on and off the block grid)."""
    return 16.5 if turn == 0 else 24 if abs(turn) == 45 else 22


def centre(turn, depth):
    a = math.radians(turn)
    return 8 - depth * math.sin(a), 8 - depth * math.cos(a)


def rot(turn, cx, cz, y=8):
    if turn == 0:
        return None
    return {"axis": "y", "angle": turn, "origin": [round(cx, 3), y, round(cz, 3)]}


def box(x0, y0, z0, x1, y1, z1, tex, r):
    b = [round(x0, 3), y0, round(z0, 3), round(x1, 3), y1, round(z1, 3), tex]
    if r:
        b += [None, r]
    return b


def panel(turn, depth, tex, inner=INSIDE, hoop=False, thick=3):
    """The panel 3 px thick, its outside face on the circle; a hoop band proud of it."""
    L = length(turn)
    cx, cz = centre(turn, depth)
    r = rot(turn, cx, cz)
    faces = {"north": tex, "south": inner, "east": tex, "west": tex, "up": inner, "down": inner}
    out = [box(cx - L / 2, 0, cz, cx + L / 2, 16, cz + thick, faces, r)]
    if hoop:
        out.append(box(cx - L / 2, 7, cz - 0.6, cx + L / 2, 8, cz, GALV, r))
    return out


def tier(turn, depth, tex, inset):
    """A ring of roof a block high: four courses, each `inset` px in from the one below, solid back
    past the block's inner edge so the next ring up sits on it."""
    L = length(turn)
    cx, cz = centre(turn, depth)
    r = rot(turn, cx, cz)
    reach = 30 - cz
    out = []
    for j in range(4):
        f = {"north": tex, "up": tex, "east": tex, "west": tex, "south": INSIDE, "down": INSIDE}
        out.append(box(cx - L / 2, 4 * j, cz + j * inset, cx + L / 2, 4 * j + 4, cz + reach, f, r))
    return out


def cage():
    """A ladder up a silo in its cage: two stiles and rungs against the wall (the model's south), the
    hoop round the climber and the straps up its sides."""
    st = "painted_steel"
    b = [[3, 0, 13, 4, 16, 14.5, st], [12, 0, 13, 13, 16, 14.5, st]]
    for y in (1, 5, 9, 13):
        b.append([4, y, 13.5, 12, y + 1, 14.2, GALV])
    b += [[0.5, 8, 0, 15.5, 9, 1, GALV], [0, 8, 0, 1, 9, 13, GALV], [15, 8, 0, 16, 9, 13, GALV]]
    for x in (0, 7.5, 15):
        b.append([x, 0, 0 if x == 7.5 else 6, x + 1, 16, 1 if x == 7.5 else 7, GALV])
    return b


def finial():
    """The dome's crown: a collar, the fill hatch's cap and the lightning rod."""
    return [[3, 0, 3, 13, 2, 13, GALV], [4, 2, 4, 12, 4, 12, GALV], [5, 4, 5, 11, 5, 11, "ext_galv_lid"],
            [7.5, 5, 7.5, 8.5, 16, 8.5, "painted_steel"], [7, 12, 7, 9, 13, 9, GALV]]


def bin_cap():
    """A grain bin's peak: the ring the roof sheets meet at, the lid on its hinge and the vent."""
    return [[1, 0, 1, 15, 3, 15, GALV], [2, 3, 2, 14, 4, 14, "ext_galv_lid"],
            [4, 4, 4, 12, 7, 12, GALV], [3, 7, 3, 13, 8.5, 13, GALV], [6, 8.5, 6, 10, 9.5, 10, "ext_galv_lid"],
            [14, 2, 7, 16, 5, 9, GALV]]


def fan():
    """A bin's aeration fan on its transition duct: the drum housing, the motor, the guard and the
    duct into the bin wall behind (the model's south)."""
    h = {"north": "bd_fan", "south": GALV, "side": GALV, "up": GALV, "down": GALV}
    return [[3, 1, 3, 13, 11, 9, h], [4, 2, 1.5, 12, 10, 3, GALV], [6, 4, 0, 10, 8, 1.5, "painted_steel"],
            [4, 0, 9, 12, 12, 16, GALV], [2, 0, 4, 4, 2, 8, "painted_steel"], [12, 0, 4, 14, 2, 8, "painted_steel"]]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", **extra):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        spec.update(extra)
        out[name] = spec

    for k, t in TURNS.items():
        for i, d in enumerate(DEPTHS):
            for mat, tex in WALLS.items():
                extra = {"render": "cutout"} if mat in ("crib", "paddock") else {}
                add(f"rd_{mat}_{k}_{i}", panel(t, d, tex, INNER.get(mat, INSIDE), thick=1 if mat in ("tank", "crib", "paddock") else 3),
                    TITLES[mat], "METAL" if mat in ("blue", "bin", "tank", "crib") else "WOOD" if mat == "paddock" else "STONE", **extra)
            add(f"rd_stave_hoop_{k}_{i}", panel(t, d, WALLS["stave"], hoop=True), "Stave Silo", "STONE")
            for mat, tex in TIERS.items():
                for slope, inset in (("s", 2), ("m", 4)):
                    add(f"rd_{mat}_{slope}_{k}_{i}", tier(t, d, tex, inset), "Silo Roof", "METAL")
    for mat, tex in TIERS.items():
        out[f"rd_{mat}_flat"] = {"tex": {"up": tex, "side": tex, "down": INSIDE}, "title": "Silo Roof", "sound": "METAL"}
    add("rd_cage", cage(), "Caged Ladder", "METAL", climb=True)
    add("rd_finial", finial(), "Silo Cap", "METAL", rotate=None)
    add("rd_bin_cap", bin_cap(), "Bin Cap", "METAL", rotate=None)
    add("rd_fan", fan(), "Aeration Fan", "METAL")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    for k in [k for k in blocks_ if k.startswith("rd_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} round blocks in {path}")


if __name__ == "__main__":
    main()
