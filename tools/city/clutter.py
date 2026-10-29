"""The street and camp clutter every builder scatters, remodelled to the detail of the landmarks
round them (Josiah, 2026-10-29: "the time and effort you put into the major landmarks are only as
good as the care and attention given to the smaller parts that surround them").

Same names, same facings, so every place that set the old ones gets these:
- jersey_barrier: the F-shape profile (the foot, the sloped skirt, the near-vertical face, the
  chamfered top), drain slots at the foot, the rebar lifting loops on top; runs along x;
- sandbags: a wall of filled bags in four staggered courses, two deep at the bottom, bellied and
  tied, slumped a little; runs along x;
- hesco: the bastion: a galvanised mesh cage round a geotextile liner bulging with the fill, the
  coil rods at the corners, the dirt in the top;
- cardboard_box: two taped boxes, the small one's flaps open;
- rubble_pile, debris_chunk: broken slabs at angles, brick, rebar sticking out, dust;
- camo_net: the net sagging between its poles;
- tire: a truck tyre standing on its tread, an octagon of tread round the steel rim (it's also the
  carrier tugs' wheels); tire_flat a car tyre lying flat, tire_stack four of them piled.
The wooden crates and the bin bags come from the props' scans now (tools/props/props.py, always).

    python3 tools/city/clutter.py          rewrites those entries in blocks.json

photo_textures.py draws the new textures (clutter.register()).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NAMES = ("jersey_barrier", "sandbags", "hesco", "cardboard_box", "rubble_pile", "debris_chunk", "camo_net", "tire")


# ---------------------------------------------------------------- textures

def cage(seed):
    """The bastion's cage: galvanised wire in a 7.5 cm square mesh, the sky and the liner through it."""
    t = F.plain(seed, (168, 172, 170), 0.04, 0.6)
    t.f0[:] = 120
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N]
    wire = ((xx % 5) == 0) | ((yy % 5) == 0) | (xx == N - 1) | (yy == N - 1)
    t.alpha[wire] = 255
    t.c[(yy % 5) == 0] *= 0.9
    rust = (t.rng.random((N, N)) < 0.08) & wire
    t.c[rust] = t.c[rust] * 0.5 + np.array((120, 70, 40), np.float32) * 0.5
    return t


def liner(seed):
    """The geotextile: beige felt, stained with the fill's damp, darker toward the ground."""
    t = F.plain(seed, (190, 172, 132), 0.06, 0.15)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) % 2) == 0] *= 0.97
    t.mix(np.clip((yy - 30) / 34.0, 0, 1) * 0.35, (110, 92, 66))
    n = np.clip(P.fbm(t.rng) * 1.4, 0, 1)
    t.mix(n * 0.3, (120, 100, 70))
    t.streaks(0.3, 0.85)
    return t


def tape(seed):
    t = F.plain(seed, (176, 130, 70), 0.04, 0.7)
    t.f0[:] = 40
    return t


def tread(seed):
    """Tyre tread: chunky blocks either side of a centre rib, worn, dusty in the grooves."""
    t = F.plain(seed, (40, 40, 40), 0.04, 0.35)
    yy, xx = np.mgrid[0:N, 0:N]
    groove = ((yy % 8) < 2) & ((xx < 28) | (xx > 36)) | ((xx >= 28) & (xx < 30)) | ((xx > 34) & (xx <= 36))
    t.c[groove] = (22, 22, 22)
    t.h[groove] -= 0.3
    t.mix(groove * 0.25, (110, 100, 84))
    t.grime(0.3, (90, 82, 70))
    return t


def sidewall(seed):
    """The sidewall: black rubber, the raised rings and the moulded lettering's ghost, kerb scuffs."""
    t = F.plain(seed, (44, 44, 44), 0.04, 0.4)
    yy, xx = np.mgrid[0:N, 0:N]
    for y in (6, 58):
        area(t, 0, y, N, y + 1, mul=1.3, dh=0.15)
    for x in range(8, 56, 5):
        area(t, x, 30, x + 3, 34, mul=1.18, dh=0.1)
    t.speckle(0.01, (90, 90, 86), 0.5)
    t.grime(0.3, (100, 92, 78))
    return t


def textures():
    return {
        "tire_tread": lambda: tread(48003),
        "tire_wall": lambda: sidewall(48004),
        "hesco_cage": lambda: cage(48000),
        "hesco_liner": lambda: liner(48001),
        "tape_brown": lambda: tape(48002),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def turn(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": [round(v, 3) for v in origin]}


def jersey():
    c, dark, rebar = "jersey_barrier", "home_plastic_black", "rust"
    b = [[0, 0, 3.1, 16, 1.2, 12.9, c],
         [0, 1.2, 4.2, 16, 5.3, 11.8, c],
         # the sloped skirt each side, leaning in from the foot to the face
         [0, 1.03, 3.95, 16, 5.47, 5.15, c, None, turn("x", 22.5, (8, 3.25, 3.95))],
         [0, 1.03, 10.85, 16, 5.47, 12.05, c, None, turn("x", -22.5, (8, 3.25, 12.05))],
         [0, 5.3, 4.8, 16, 9.0, 11.2, c],
         [0, 9.0, 5.15, 16, 12.4, 10.85, c],
         [0, 12.4, 5.6, 16, 13, 10.4, c],
         # the drain slots through the foot
         [6, 0, 3.0, 10, 0.9, 3.1, dark], [6, 0, 12.9, 10, 0.9, 13.0, dark]]
    for x in (2.6, 11.6):
        b += [[x, 13, 7.7, x + 0.5, 14.3, 8.3, rebar], [x, 13.8, 7.7, x + 1.8, 14.3, 8.3, rebar],
              [x + 1.3, 13, 7.7, x + 1.8, 14.3, 8.3, rebar]]
    return b


def sandbags():
    """Four courses of bags, the joints broken course to course; each bag a filled belly with its
    tied end, nudged a little off true."""
    rng = np.random.default_rng(48010)
    courses = [  # y0, height, rows (z0, z1), joints along x
        (0.0, 2.6, ((2.2, 7.8), (8.2, 13.8)), (0, 8, 16)),
        (2.5, 2.6, ((2.6, 7.9), (8.1, 13.4)), (0, 4, 12, 16)),
        (5.0, 2.6, ((3.2, 8.0), (8.0, 12.8)), (0, 8, 16)),
        (7.5, 2.5, ((5.0, 11.0),), (0, 4, 12, 16)),
    ]
    out = []
    for y0, h, rows, cuts in courses:
        for z0, z1 in rows:
            for x0, x1 in zip(cuts, cuts[1:]):
                top = round(y0 + h + float(rng.uniform(-0.45, 0.15)), 2)
                dz = float(rng.uniform(-0.35, 0.35))
                a0, a1 = z0 + dz, z1 + dz
                # the belly: full height in the middle, rounding off to its seams and ends
                out.append([x0 + 1.0, y0, round(a0 + 0.2, 2), x1 - 1.0, top, round(a1 - 0.2, 2), "sandbag"])
                out.append([x0 + 0.3, y0 + 0.3, round(a0 + 0.5, 2), x1 - 0.3, round(top - 0.4, 2), round(a1 - 0.5, 2), "sandbag"])
                out.append([x0, y0 + 0.6, round(a0, 2), x1, round(top - 0.9, 2), round(a1, 2), "sandbag"])
                if x1 - x0 == 8:
                    # the tied end: gathered and folded under
                    zc = (a0 + a1) / 2
                    out.append([x1 - 1.0, y0 + 0.7, round(zc - 0.9, 2), x1 - 0.1, round(top - 0.6, 2), round(zc + 0.9, 2), "sandbag"])
    return out


def hesco():
    sides = {d: [0, 0, 16, 16] for d in ("north", "south", "east", "west")}
    lin = {"north": "hesco_liner", "south": "hesco_liner", "east": "hesco_liner", "west": "hesco_liner",
           "up": "dirt_withered", "down": "dirt_withered"}
    b = [[0.5, 0, 0.5, 15.5, 15.2, 15.5, lin],
         [0.25, 2.5, 0.25, 15.75, 12.5, 15.75, lin],
         [0, 0, 0, 16, 16, 16, "hesco_cage", sides]]
    for x, z in ((0, 0), (15.4, 0), (0, 15.4), (15.4, 15.4)):
        b.append([x, 0, z, x + 0.6, 16, z + 0.6, "st_galv"])
    return b


def cardboard():
    cb, tp = "cardboard", "tape_brown"
    return [[2, 0, 3, 11, 8, 12, cb], [6.1, 8, 3, 6.9, 8.15, 12, tp], [6.1, 5.4, 2.85, 6.9, 8, 3, tp],
            [6.1, 5.4, 12, 6.9, 8, 12.15, tp], [2, 8, 3, 6.4, 8.25, 12, cb],
            [8, 0, 9, 15, 5, 15, cb], [8, 1.5, 8.85, 15, 2.3, 9, tp],
            # its flaps folded back open, leaning out over the sides
            [8, 5, 8.7, 15, 8.2, 9, cb, None, turn("x", -45, (11.5, 5, 9))],
            [8, 5, 15, 15, 7.8, 15.3, cb, None, turn("x", 22.5, (11.5, 5, 15))],
            [7.7, 5, 9.3, 8, 7.4, 14.7, cb, None, turn("z", 45, (8, 5, 12))]]


def rubble(seed, big):
    """Broken slab and block: chunks at angles about a heap, rebar out of them, dust under."""
    rng = np.random.default_rng(seed)
    mats = ("concrete_dark", "rubble", "rubble", "brick_red", "stone_trim")
    # the dust and grit it fell in, not square to anything
    out = [[2, 0, 2, 14, 0.6, 14, "rubble", None, turn("y", 45, (8, 0, 8))],
           [1, 0, 3, 13, 0.9, 12, "rubble", None, turn("y", 22.5, (7, 0, 7.5))]] if big else []
    n = 9 if big else 4
    for i in range(n):
        s = float(rng.uniform(3, 6.5 if big else 5))
        cx, cz = (float(rng.uniform(3.5, 12.5)) for _ in range(2))
        d = math.hypot(cx - 8, cz - 8)
        y0 = 0.6 if big and d > 4 else (2.4 if big and i > 4 else 0)
        hgt = float(rng.uniform(1.5, 3.5))
        x0, z0 = cx - s / 2, cz - s * 0.4
        box = [round(x0, 2), y0, round(z0, 2), round(x0 + s, 2), round(y0 + hgt, 2), round(z0 + s * 0.8, 2),
               mats[int(rng.integers(len(mats)))], None, turn("y", float(rng.choice((-45, -22.5, 22.5, 45))), (cx, y0, cz))]
        if rng.random() < 0.5:
            # tipped up on an edge
            box[8] = turn("z" if rng.random() < 0.5 else "x", float(rng.choice((-22.5, 22.5))), (cx, y0, cz))
        out.append(box)
    for i in range(2 if big else 1):
        x, z = float(rng.uniform(4, 12)), float(rng.uniform(4, 12))
        ln = float(rng.uniform(5, 9))
        out.append([round(x, 2), 1, round(z, 2), round(x + 0.45, 2), round(1 + ln, 2), round(z + 0.45, 2), "rust", None,
                    turn("x" if i else "z", float(rng.choice((-45, -22.5, 22.5, 45))), (x, 1, z))])
    return out


def camo():
    """The net sagging from its edges to the middle of the span; the hanging garnish is in the texture."""
    n = "camo_net"
    hl = 2.0 / math.cos(math.radians(22.5)) + 0.1
    return [[4, 13.25, 4, 12, 13.65, 12, n],
            [0, 14.0, 2 - hl, 16, 14.4, 2 + hl, n, None, turn("x", 22.5, (8, 14.2, 2))],
            [0, 14.0, 14 - hl, 16, 14.4, 14 + hl, n, None, turn("x", -22.5, (8, 14.2, 14))],
            [2 - hl, 14.0, 4, 2 + hl, 14.4, 12, n, None, turn("z", -22.5, (2, 14.2, 8))],
            [14 - hl, 14.0, 4, 14 + hl, 14.4, 12, n, None, turn("z", 22.5, (14, 14.2, 8))]]


def ring(axis, c, R, r, a0, a1, side, rim=None):
    """An octagon ring about `axis` (z: in the xy plane, y: lying in xz) centred c, outer apothem R,
    inner r, spanning a0..a1 along the axis: four straight slabs and four turned 45 degrees."""
    s = R * math.tan(math.radians(22.5)) + 0.25
    cx, cy = c
    out = []
    for k, (u0, u1, v0, v1) in enumerate(((-s, s, r, R), (-s, s, -R, -r), (r, R, -s, s), (-R, -r, -s, s))):
        for ang in ((0,) if k > 1 else (0, 45, -45)):
            if axis == "z":
                b = [cx + u0, cy + v0, a0, cx + u1, cy + v1, a1]
                f = {"north": side, "south": side, "east": "tire_tread", "west": "tire_tread", "up": "tire_tread", "down": "tire_tread"}
                rot = turn("z", ang, (cx, cy, (a0 + a1) / 2)) if ang else None
            else:
                b = [cx + u0, a0, cy + v0, cx + u1, a1, cy + v1]
                f = {"up": side, "down": side, "north": "tire_tread", "south": "tire_tread", "east": "tire_tread", "west": "tire_tread"}
                rot = turn("y", ang, (cx, (a0 + a1) / 2, cy)) if ang else None
            out.append([round(v, 3) for v in b] + [f] + ([None, rot] if rot else []))
    if rim:
        m = r + 0.3
        for ang in (0, 45):
            if axis == "z":
                b = [cx - m, cy - m, rim[0], cx + m, cy + m, rim[1]]
                rot = turn("z", ang, (cx, cy, 8)) if ang else None
            else:
                b = [cx - m, rim[0], cy - m, cx + m, rim[1], cy + m]
                rot = turn("y", ang, (cx, 8, cy)) if ang else None
            out.append([round(v, 3) for v in b] + [rim[2]] + ([None, rot] if rot else []))
    return out


def tire():
    b = ring("z", (8, 8), 7.6, 4.4, 4.5, 11.5, "tire_wall", (6, 10, "rust"))
    b += [[6.5, 6.5, 5.6, 9.5, 9.5, 10.4, "st_galv"]]
    return b


def tire_flat(y0=0.0, dx=0.0):
    return ring("y", (8 + dx, 8), 6.6, 4.0, y0, y0 + 3.4, "tire_wall")


def blocks(old):
    out = {}

    def keep(name, boxes, **extra):
        spec = {k: v for k, v in old[name].items() if k not in ("tex", "boxes", "model", "shape")}
        spec.update({"model": "boxes", "boxes": boxes})
        spec.update(extra)
        out[name] = spec

    keep("jersey_barrier", jersey(), shape=[[0, 0, 3, 16, 13, 13]], title="Jersey Barrier", sound="STONE")
    keep("sandbags", sandbags(), shape=[[0, 0, 2, 16, 5, 14], [1, 5, 3, 15, 10, 13]], title="Sandbags", sound="WOOL")
    keep("hesco", hesco(), shape=[[0, 0, 0, 16, 16, 16]], title="Hesco Bastion", sound="WOOL")
    keep("cardboard_box", cardboard(), shape=[[2, 0, 3, 11, 8, 12], [8, 0, 9, 15, 5, 15]])
    keep("rubble_pile", rubble(48020, True), shape=[[0, 0, 0, 16, 3, 16], [3, 3, 3, 13, 6, 13]])
    keep("debris_chunk", rubble(48021, False))
    keep("camo_net", camo())
    keep("tire", tire(), model="boxes", shape=[[0.5, 0, 4.5, 15.5, 15.5, 11.5]], title="Tyre", sound="WOOL")
    out["tire_flat"] = {"model": "boxes", "boxes": tire_flat(), "title": "Tyre", "sound": "WOOL", "shape": [[1.5, 0, 1.5, 14.5, 3.4, 14.5]]}
    out["tire_stack"] = {"model": "boxes", "boxes": sum((tire_flat(i * 3.45, (0.6, -0.4, 0.3, -0.5)[i]) for i in range(4)), []),
                         "title": "Tyres", "sound": "WOOL", "shape": [[1.5, 0, 1.5, 14.5, 13.8, 14.5]]}
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks(data["blocks"])
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} clutter blocks in {path}")


if __name__ == "__main__":
    main()
