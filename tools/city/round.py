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
  (corrugated grain bin), brick; wood (a water tower's timber staves, wood_hoop with its iron hoop),
  wtw / wtb (a steel water tower's white or blue shell); soot (a smokestack's blackened lip);
  twr / twd (a control tower's pale concrete shaft, the dark band under its cab);
- rd_tglaze: a control tower cab's tall tinted panes in black frames (rd_glaze the lighthouse's);
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
         "field": "fm_fieldstone", "tank": "ext_galv", "crib": "fm_mesh", "paddock": "fm_ring_boards",
         "oil": "tk_oil", "oilrust": "tk_oil_rust", "slip": "tk_slip", "lhw": "lh_white", "lhr": "lh_red",
         "wood": "wt_stave", "wtw": "tank_white", "wtb": "tank_blue", "soot": "brick_dark_soot",
         "twr": "tx_concrete", "twd": "ext_black_metal"}
# what each wall shows inside (a stock tank and a corn crib's mesh are the same both sides)
INNER = {"wood": "wt_stave", "tank": "ext_galv", "crib": "fm_mesh", "field": "fm_fieldstone", "paddock": "fm_ring_boards",
         "twr": "tx_concrete"}
TITLES = {"stave": "Stave Silo", "blue": "Silo", "bin": "Grain Bin", "brick": "Brick Silo", "field": "Stone Curb",
          "tank": "Stock Tank", "crib": "Corn Crib", "paddock": "Round Pen", "oil": "Fuel Tank", "oilrust": "Fuel Tank",
          "slip": "Grain Silo", "lhw": "Lighthouse", "lhr": "Lighthouse", "wood": "Water Tank",
          "wtw": "Water Tank", "wtb": "Water Tank", "soot": "Smokestack", "twr": "Control Tower", "twd": "Control Tower"}
TIERS = {"galv": "ext_galv", "blue": "silo_blue", "tinred": "ext_tin_red_tread", "oil": "tk_oil_roof", "lhcap": "lh_cap",
         "wood": "wt_roof", "wtw": "tank_white", "wtb": "tank_blue", "twr": "tx_concrete"}
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


def panel(turn, depth, tex, inner=INSIDE, hoop=False, thick=3, girder=None):
    """The panel 3 px thick, its outside face on the circle; a hoop band proud of it, or a fuel
    tank's wind girder (an angle ring standing out, its lip turned down)."""
    L = length(turn)
    cx, cz = centre(turn, depth)
    r = rot(turn, cx, cz)
    faces = {"north": tex, "south": inner, "east": tex, "west": tex, "up": inner, "down": inner}
    out = [box(cx - L / 2, 0, cz, cx + L / 2, 16, cz + thick, faces, r)]
    if hoop:
        out.append(box(cx - L / 2, 7, cz - 0.6, cx + L / 2, 8, cz, GALV, r))
    if girder:
        out += [box(cx - L / 2, 12, cz - 2.4, cx + L / 2, 12.8, cz, girder, r),
                box(cx - L / 2, 10.6, cz - 2.4, cx + L / 2, 12, cz - 1.8, girder, r)]
    return out


def tier(turn, depth, tex, inset, rail=False):
    """A ring of roof a block high: four courses, each `inset` px in from the one below, solid back
    past the block's inner edge so the next ring up sits on it. A railed ring (a fuel tank's eave)
    has the handrail round its outer course: posts, knee rail and hand rail."""
    L = length(turn)
    cx, cz = centre(turn, depth)
    r = rot(turn, cx, cz)
    reach = 30 - cz
    out = []
    for j in range(4):
        f = {"north": tex, "up": tex, "east": tex, "west": tex, "south": INSIDE, "down": INSIDE}
        out.append(box(cx - L / 2, 4 * j, cz + min(j * inset, reach - 2), cx + L / 2, 4 * j + 4, cz + reach, f, r))
    if rail:
        out += [box(cx - L / 2, 14, cz + 0.4, cx + L / 2, 15, cz + 1.4, GALV, r),
                box(cx - L / 2, 9.4, cz + 0.4, cx + L / 2, 10.2, cz + 1.2, GALV, r)]
        x = cx - L / 2 + 1
        while x < cx + L / 2 - 1:
            out.append(box(x, 4, cz + 0.4, x + 1, 14, cz + 1.4, GALV, r))
            x += 8
    return out


def rail(turn, depth):
    """A ring railing (a lighthouse gallery's): toe plate, posts, a knee rail and a hand rail on
    the circle, nothing behind."""
    L = length(turn)
    cx, cz = centre(turn, depth)
    r = rot(turn, cx, cz)
    out = [box(cx - L / 2, 0, cz, cx + L / 2, 1.5, cz + 0.6, GALV, r), box(cx - L / 2, 14, cz, cx + L / 2, 15.2, cz + 1.2, "lh_cap", r),
           box(cx - L / 2, 7.5, cz + 0.2, cx + L / 2, 8.3, cz + 1, "lh_cap", r)]
    x = cx - L / 2 + 2
    while x < cx + L / 2 - 1:
        out.append(box(x, 0, cz + 0.2, x + 1, 14, cz + 1, "lh_cap", r))
        x += 6
    return out


def glaze(turn, depth, glass="lamp_glass", frame="lh_cap", bar=True):
    """A lantern's glazing on the circle: the panes in their astragals, a bar across the middle
    (none on a control tower's cab, its panes tall), the sill and the head."""
    L = length(turn)
    cx, cz = centre(turn, depth)
    r = rot(turn, cx, cz)
    out = [box(cx - L / 2, 1, cz + 0.6, cx + L / 2, 15, cz + 1, glass, r),
           box(cx - L / 2, 0, cz, cx + L / 2, 1.2, cz + 1.6, frame, r), box(cx - L / 2, 14.8, cz, cx + L / 2, 16, cz + 1.6, frame, r),
           box(cx - 0.6, 0, cz + 0.1, cx + 0.6, 16, cz + 1.5, frame, r)]
    if bar:
        out.append(box(cx - L / 2, 7.6, cz + 0.2, cx + L / 2, 8.4, cz + 1.4, frame, r))
    return out


def diag(cx, cz, r, x0, x1, y0, y1, z0, z1, tex, steps=7):
    """A bar from (x0, y0) to (x1, y1) across a panel, as steps (one axis of turn per element)."""
    out = []
    for k in range(steps):
        xa = x0 + (x1 - x0) * k / steps
        xb = x0 + (x1 - x0) * (k + 1) / steps
        ya = y0 + (y1 - y0) * k / steps
        yb = y0 + (y1 - y0) * (k + 1) / steps
        lo, hi = min(xa, xb), max(xa, xb)
        out.append(box(lo, round(min(ya, yb), 3), z0, hi + 0.6, round(max(ya, yb), 3) + 0.6, z1, tex, r))
    return out


def xrail(turn, depth):
    """A gallery railing of X-lattice panels (refs/port/lighthouse 07-09): toe plate, top rail and
    a post each end, the diagonals crossing between."""
    L = length(turn)
    cx, cz = centre(turn, depth)
    r = rot(turn, cx, cz)
    a, b = cx - L / 2, cx + L / 2
    out = [box(a, 0, cz, b, 1.5, cz + 0.6, GALV, r), box(a, 14, cz, b, 15.2, cz + 1.2, "lh_cap", r),
           box(a, 1.5, cz + 0.2, b, 2.3, cz + 1, "lh_cap", r), box(cx - 0.5, 0, cz + 0.1, cx + 0.5, 15, cz + 1.1, "lh_cap", r)]
    z0, z1 = round(cz + 0.3, 3), round(cz + 0.9, 3)
    out += diag(cx, cz, r, a + 0.5, cx - 0.5, 2.3, 13.4, z0, z1, "lh_cap", 6)
    out += diag(cx, cz, r, cx - 0.5, a + 0.5, 2.3, 13.4, z0, z1, "lh_cap", 6)
    out += diag(cx, cz, r, cx + 0.5, b - 1.1, 2.3, 13.4, z0, z1, "lh_cap", 6)
    out += diag(cx, cz, r, b - 1.1, cx + 0.5, 2.3, 13.4, z0, z1, "lh_cap", 6)
    return out


def glazex(turn, depth):
    """Lantern glazing with diagonal astragals (refs/port/lighthouse 04-06): the panes, sill and
    head, and the bars crossing the pane corner to corner both ways."""
    L = length(turn)
    cx, cz = centre(turn, depth)
    r = rot(turn, cx, cz)
    a, b = cx - L / 2, cx + L / 2
    out = [box(a, 1, cz + 0.6, b, 15, cz + 1, "lamp_glass", r),
           box(a, 0, cz, b, 1.2, cz + 1.6, "lh_cap", r), box(a, 14.8, cz, b, 16, cz + 1.6, "lh_cap", r)]
    z0, z1 = round(cz + 0.1, 3), round(cz + 0.5, 3)
    out += diag(cx, cz, r, a, b - 0.6, 1.2, 14.2, z0, z1, "lh_cap", 8) + diag(cx, cz, r, b - 0.6, a, 1.2, 14.2, z0, z1, "lh_cap", 8)
    return out


def lens(lit):
    """The light: a Fresnel lens's drum of glass prisms on its pedestal, the bull's-eye, a cap."""
    pr = "lh_prism_lit" if lit else "lh_prism"
    c = [8, 8, 8]
    return [[5, 0, 5, 11, 3, 11, "lh_cap"], [6.5, 3, 6.5, 9.5, 4, 9.5, GALV],
            [3, 4, 3, 13, 14, 13, pr], [3, 4, 3, 13, 14, 13, pr, None, {"axis": "y", "angle": 45, "origin": c}],
            [4, 14, 4, 12, 15.5, 12, "lh_cap"], [6, 15.5, 6, 10, 16, 10, "lh_cap"]]


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
                add(f"rd_{mat}_{k}_{i}", panel(t, d, tex, INNER.get(mat, INSIDE), thick=1 if mat in ("tank", "crib", "paddock", "oil", "oilrust", "wtw", "wtb") else 3),
                    TITLES[mat], "METAL" if mat in ("blue", "bin", "tank", "crib", "oil", "oilrust", "wtw", "wtb")
                    else "WOOD" if mat in ("paddock", "wood") else "STONE", **extra)
            add(f"rd_stave_hoop_{k}_{i}", panel(t, d, WALLS["stave"], hoop=True), "Stave Silo", "STONE")
            add(f"rd_wood_hoop_{k}_{i}", panel(t, d, WALLS["wood"], INNER.get("wood", INSIDE), hoop=True), "Water Tank", "WOOD")
            for mat in ("oil", "oilrust"):
                add(f"rd_{mat}_hoop_{k}_{i}", panel(t, d, WALLS[mat], thick=1, girder=WALLS[mat]), "Fuel Tank", "METAL")
            for mat, tex in TIERS.items():
                for slope, inset in (("s", 2), ("m", 4)):
                    add(f"rd_{mat}_{slope}_{k}_{i}", tier(t, d, tex, inset), "Silo Roof", "METAL")
            # a fuel tank's low cone: half a block in a course, the eave ring railed
            add(f"rd_oil_l_{k}_{i}", tier(t, d, TIERS["oil"], 8), "Tank Roof", "METAL")
            add(f"rd_oil_r_{k}_{i}", tier(t, d, TIERS["oil"], 8, rail=True), "Tank Roof", "METAL", render="cutout")
    for mat, tex in TIERS.items():
        out[f"rd_{mat}_flat"] = {"tex": {"up": tex, "side": tex, "down": INSIDE}, "title": "Silo Roof", "sound": "METAL"}
    for k, t in TURNS.items():
        for i, d in enumerate(DEPTHS):
            add(f"rd_rail_{k}_{i}", rail(t, d), "Gallery Rail", "METAL", render="cutout")
            add(f"rd_glaze_{k}_{i}", glaze(t, d), "Lantern Glazing", "GLASS", render="cutout")
            add(f"rd_xrail_{k}_{i}", xrail(t, d), "Gallery Rail", "METAL", render="cutout")
            add(f"rd_glazex_{k}_{i}", glazex(t, d), "Lantern Glazing", "GLASS", render="cutout")
            add(f"rd_tglaze_{k}_{i}", glaze(t, d, "rp_glass", "ext_black_metal", False), "Tower Cab Glazing", "GLASS", render="translucent")
    add("rd_lens", lens(False), "Lighthouse Lens", "GLASS", rotate=None)
    add("rd_lens_lit", lens(True), "Lighthouse Lens", "GLASS", rotate=None, light=15)
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
