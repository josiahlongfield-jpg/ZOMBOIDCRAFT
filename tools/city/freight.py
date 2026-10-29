"""The fw_ kit: the freight wagons left on the sidings, built to the landmarks' detail in place of a
box of corrugated blocks on tyres and slabs (Josiah, 2026-10-29: "Everything in this world must be
of the same level of detail and effort"). world/county/Freight lays a wagon out, along x, three
blocks wide, its trucks on the tb_ track at y and its floor at y + 1.

The body, one set per paint (red, brown, green), rotate=facing, the outside on the north:

- fw_side_<c>: a side panel, the posts standing proud of it, the lining boards inside;
- fw_mark_<c>_<0..2>: the same with the reporting marks stencilled across three blocks;
- fw_door_<c>_<0..3>: the sliding door, two by two (row * 2 + column, column 0 the left as seen
  from outside), hung on its track, the handle and the seal on its leading edge; the door slid
  back over the side beside the opening;
- fw_end_<c>, fw_corner_<c>_<l|r>: the ribbed end, and its corners with the side turning off to
  the left or the right as seen from outside and the end ladder up them; fw_brake_<c>: the end
  with the hand brake's wheel on its staff;
- fw_roof_<c>: the side's last foot and the roof's eave over it; fw_ridge_<c> (rotate=axis):
  the middle of the roof with the running board on it; fw_end_top_<c>, fw_corner_top_<c>_<l|r>:
  the end and the corners under the eave.

Under it, unpainted: fw_floor (facing; the side sill and the floor boards, the crossbearers
under them), fw_floor_mid (axis; the centre sill), fw_truck (facing; the track with a wheel on
the rail, the truck's side frame and its journal box outside), fw_truck_mid (facing, the track along z; the track,
the axle and the bolster), fw_coupler (facing; the knuckle out to the north), tb_buffer / tb_buffer_mid
(facing; a buffer stop across the end of a siding, on a rail's block and between them, its beam
to the north).

    python3 tools/city/freight.py      rewrites the fw_ entries (and tb_buffer) in blocks.json

photo_textures.py draws the textures (freight.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import trackbed  # noqa: E402
from bunker import chips, stencil  # noqa: E402
from gasstation import weather  # noqa: E402

N = P.N
HERE = Path(__file__).parent
PAINTS = {"red": (118, 48, 36), "brown": (88, 60, 44), "green": (60, 78, 58)}
STEEL = "fw_steel"
MARKS = ("GHR", "40213", "")


# ---------------------------------------------------------------- textures

def panel(seed, rgb, ribs=False):
    """A painted steel side: the sheets' seams and their rivet rows, the paint gone chalky and
    chipped to rust, rust weeping from every rivet line, grime up from the trucks."""
    t = F.plain(seed, rgb, 0.05, 0.25)
    t.f0[:] = 20
    for x in (0, N // 2):
        t.rect(x, 0, x + 1, N, col=tuple(int(c * 0.6) for c in rgb), h=0.4)
        for y in range(3, N, 6):
            t.rect(x + 2, y, x + 3, y + 1, col=tuple(int(c * 1.2) for c in rgb), h=0.8)
    if ribs:
        for y in range(6, N, 16):
            t.rect(0, y, N, y + 4, col=tuple(int(c * 1.1) for c in rgb), h=0.85)
            t.rect(0, y + 4, N, y + 5, col=tuple(int(c * 0.55) for c in rgb), h=0.4)
    weather(t, rust=0.6, streak=0.7, grime=0.3)
    chips(t, 0.12, under=(104, 64, 44))
    dirt = np.clip(np.linspace(-0.6, 0.6, N)[:, None] + 0.3 * P.fbm(t.rng, ((8, 1.0), (16, 0.5))), 0, 1)
    t.mix(dirt * 0.6, (70, 60, 48), 0.8)
    return t


def marked(seed, rgb, i):
    """The side with the reporting marks across three blocks and the capacity data under them."""
    t = panel(seed, rgb)
    stencil(t, "GHR 40213", (222, 218, 204), 22, x=-i * N + 8, y=10, w=3 * N - 16, h=24, chip=0.3)
    if i == 1:
        stencil(t, "CAPY 100000|LD LMT 103500|LT WT 46500", (210, 206, 192), 7, x=4, y=38, w=N - 8, h=22, chip=0.35)
    return t


def door(seed, rgb):
    """The sliding door: pressed steel with its ribs across (the handle and the hasp are the
    model's)."""
    return panel(seed, rgb, ribs=True)


def lining(seed):
    """The inside: the lining boards, grey and splintered, nail lines at the posts."""
    t = F.mat("Wood049", (128, 112, 90), 0.6, rot=True, seed=seed)
    t.vary(0.06)
    for y in range(0, N, N // 6):
        t.rect(0, y, N, y + 1, col=(52, 46, 38), h=0.3)
    t.grime(0.35, (60, 54, 44))
    return t


def steel(seed):
    """The underframe and running gear: black once, brake dust and rust over it."""
    t = F.plain(seed, (46, 42, 38), 0.06, 0.3)
    weather(t, rust=0.7, streak=0.5, grime=0.4)
    return t


def target(seed):
    """The buffer stop's target board: a red disc with a white bar, faded, rust round the bolts."""
    t = F.plain(seed, (210, 206, 196), 0.05, 0.3)
    F.disc(t, N // 2, N // 2, N // 2 - 6, col=(170, 40, 32))
    t.rect(12, N // 2 - 4, N - 12, N // 2 + 4, col=(226, 222, 210))
    weather(t, rust=0.5, streak=0.8, grime=0.3)
    return t


def textures():
    out = {"fw_lining": lambda: lining(49440), "fw_steel": lambda: steel(49441), "fw_target": lambda: target(49442)}
    for j, (c, rgb) in enumerate(PAINTS.items()):
        s = 49450 + j * 10
        out[f"fw_side_{c}"] = lambda s=s, rgb=rgb: panel(s, rgb)
        out[f"fw_end_{c}"] = lambda s=s, rgb=rgb: panel(s + 1, rgb, ribs=True)
        out[f"fw_door_{c}"] = lambda s=s, rgb=rgb: door(s + 2, rgb)
        for i in range(3):
            out[f"fw_mark_{c}_{i}"] = lambda s=s, rgb=rgb, i=i: marked(s + 3 + i, rgb, i)
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

def wall(face):
    """A side or end panel, its outside on the north, the post at the block's west edge."""
    return [[0, 0, 0, 16, 16, 1.5, {"north": face, "south": "fw_lining", "side": face, "up": face, "down": face}],
            [0, 0, -0.8, 1.4, 16, 0, face]]


def ladder():
    b = [[2, 0, -2.6, 3, 16, -1.6, STEEL], [13, 0, -2.6, 14, 16, -1.6, STEEL]]
    for y in (2, 6, 10, 14):
        b += [[2, y, -2.8, 14, y + 0.9, -1.9, STEEL], [2.2, y, -1.9, 2.8, y + 0.9, 0, STEEL], [13.2, y, -1.9, 13.8, y + 0.9, 0, STEEL]]
    return b


def door_piece(side, dr, row, col):
    """A quarter of the door, two blocks by two: the panel stood off the side; on the top row the
    track it hangs from, on the bottom the guide it runs in; on the leading (left) column the
    handle up its edge, the hasp and the seal on it."""
    b = wall(side) + [[0, 0, -1.6, 16, 16 if row == 0 else 14.6, 0, dr]]
    if row == 1:
        b += [[0, 14.6, -2.4, 16, 16, 0, STEEL]] + [[x, 13.6, -2.8, x + 2, 15, -2.2, STEEL] for x in (3, 11)]
    else:
        b += [[0, -0.8, -2, 16, 0.4, 0, STEEL]]
    if col == 0:
        b += [[13.6, 2 + row * 0, -2.8, 14.6, 14, -2.2, STEEL]] if row == 0 else [[13.6, 0, -2.8, 14.6, 6, -2.2, STEEL]]
        if row == 0:
            b += [[13.2, 3, -2.2, 15, 4, -1.6, STEEL], [13.2, 12, -2.2, 15, 13, -1.6, STEEL],
                  [10.5, 8, -2.4, 13.6, 9.2, -1.6, STEEL], [11, 6.2, -2.8, 12, 8, -2.4, "fs_red"]]
    return b


def corner(face, end, left):
    """The end with the side turning off at the left as seen from outside (the east, x = 16) or the
    right, the corner post over the joint, the ladder up the end."""
    side = {"west" if left else "east": "fw_lining", "side": face, "up": face, "down": face}
    x0, x1 = (14.5, 16) if left else (0, 1.5)
    return wall(end) + [[x0, 0, 0, x1, 16, 16, side], [x0 if left else x0 - 0.4, 0, -0.6, x1 + 0.4 if left else x1, 16, 2, STEEL]] + ladder()


def clip(boxes, top=8):
    """The roof row's share of a body piece: what lies under the eave."""
    out = []
    for q in boxes:
        if len(q) > 8 or q[1] >= top:
            continue
        out.append(q[:4] + [min(q[4], top)] + q[5:])
    return out


def eave(face):
    return [[0, 8, -1, 16, 9.2, 16, face], [0, 7.2, -1.4, 16, 8, -0.6, face]]


def brake():
    """The hand brake: the staff up the end from its bracket, the wheel on top facing out."""
    b = [[7.4, 0, -3, 8.6, 12, -1.8, STEEL], [6, 2, -3.4, 10, 3, 0, STEEL], [7.6, 11, -4.5, 8.4, 12, -1.8, STEEL]]
    for d in ((2, 13.5, 14, 14.5), (2.5, 9.5, 13.5, 10.5), (2, 10, 3, 14), (13, 10, 14, 14), (7.5, 9.5, 8.5, 14.5), (2.5, 11.6, 13.5, 12.4)):
        b.append([d[0], d[1], -5.2, d[2], d[3], -4.4, STEEL])
    return b


def roof_edge(face):
    """The side's last foot under the eave, the roof sheet over it running in to the middle."""
    return [[0, 0, 0, 16, 8, 1.5, {"north": face, "south": "fw_lining", "side": face, "up": face, "down": face}],
            [0, 0, -0.8, 1.4, 8, 0, face], [0, 8, -1, 16, 9.2, 16, face], [0, 9.2, 4, 16, 10.2, 16, face],
            [0, 7.2, -1.4, 16, 8, -0.6, face]]


def ridge(face):
    """The middle of the roof along z, its seam caps across, the running board on its saddles."""
    b = [[0, 10.2, 0, 16, 11, 16, face], [0, 11, 3, 1, 11.6, 13, face], [8, 11, 3, 9, 11.6, 13, face],
         [5, 11.6, 0, 11, 12.6, 16, {"up": "fw_lining", "side": "fw_lining", "down": "fw_lining"}]]
    for z in (2, 10):
        b.append([5, 11, z, 11, 11.6, z + 1.5, STEEL])
    return b


def floor():
    """The floor at a side: the boards, the side sill along the outside, a crossbearer under."""
    return [[0, 12, 0, 16, 16, 16, {"up": "fw_lining", "side": STEEL, "down": STEEL}],
            [0, 8.5, 0, 16, 12, 1.5, STEEL], [6, 8, 1.5, 10, 12, 16, STEEL]]


def floor_mid():
    """The floor in the middle along z: the boards, the centre sill down under them."""
    return [[0, 12, 0, 16, 16, 16, {"up": "fw_lining", "side": STEEL, "down": STEEL}],
            [5, 2, 0, 11, 12, 16, STEEL], [4, 2, 0, 12, 3.4, 16, STEEL], [0, 8, 6, 16, 12, 10, STEEL]]


def along_x(boxes):
    """tb_ track drawn along z, turned to run along x (the wagon's length) under a facing piece."""
    out = []
    for q in boxes:
        q = list(q)
        out.append([q[2], q[1], q[0], q[5], q[4], q[3]] + q[6:])
    return out


def truck():
    """The track (along x) with a wheel on the rail, the side frame outside it with its journal box
    and springs, the brake shoe at the tread."""
    b = along_x(trackbed.rail())
    for d, w in ((7, 3), (5, 5), (3, 7)):
        b.append([8 - w, 14 - d, 7.2, 8 + w, 14 + d, 8.8, STEEL])
    b.append([8 - 7, 12, 8.8, 8 + 7, 16, 9.6, STEEL])
    b += [[6.5, 12, 4.6, 9.5, 16, 7.2, STEEL], [0, 15, 3, 16, 18, 5, STEEL], [0, 10, 3.4, 16, 12, 4.6, STEEL],
          [5.5, 12, 2.6, 10.5, 15, 5.4, STEEL]]
    for x in (1.5, 13):
        b.append([x, 12, 3.6, x + 1.5, 15, 4.4, STEEL])
    b.append([13.5, 9, 7.4, 15.5, 15, 8.6, STEEL])
    return b


def truck_mid():
    """The track along z, the axle across it under the bolster, the brake beam."""
    return trackbed.sleepers() + [[0, 13.2, 7.2, 16, 14.8, 8.8, STEEL], [0, 17, 3, 16, 20, 13, STEEL],
                                  [0, 10, 12.5, 16, 11, 13.5, STEEL]]


def coupler():
    """The coupler on its draft gear out of the end sill, the knuckle at the north, the cut lever."""
    return [[6, 4, 4, 10, 8, 16, STEEL], [5, 3, 0.5, 11, 9, 4, STEEL], [4, 5, 0, 6, 8, 2, STEEL],
            [2, 9.5, 2, 14, 10.3, 2.8, STEEL], [0, 3, 14, 16, 12, 16, STEEL]]


def buffer_stop(rail):
    """A buffer stop across the end of a siding, its beam to the north: on a rail's block the rail
    bent up into the post and the raking strut behind it; in the middle the beam alone, the
    target board on it."""
    rust = "tb_rust"
    beam = {"north": "hazard_stripes", "side": "tb_sleeper", "up": "tb_sleeper", "down": "tb_sleeper", "south": "tb_sleeper"}
    b = trackbed.sleepers() + [[0, 9, 0, 16, 14, 2.5, beam]]
    if rail:
        b += [[6.8, 2.5, 2.5, 9.2, 14, 4.5, rust], [6.8, 5.6, 4.5, 9.2, 7, 16, rust], [7.4, 3.6, 4.5, 8.6, 5.6, 16, rust],
              [6.8, 3, 4.5, 9.2, 5, 18, rust, None, {"axis": "x", "angle": 45, "origin": [8, 4, 4.5]}]]
    else:
        b += [[3, 14, 0.6, 13, 22, 1.6, {"north": "fw_target", "south": STEEL, "side": STEEL, "up": STEEL, "down": STEEL}],
              [7.4, 12, 1.6, 8.6, 14, 2.4, STEEL]]
    return b


def blocks():
    out = {}

    def add(name, boxes, title, rotate="facing", shape=None, sound="METAL"):
        out[name] = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                     "title": title, "sound": sound, "rotate": rotate, "shape": shape or [[0, 0, 0, 16, 16, 16]]}

    for c in PAINTS:
        side, end, dr = f"fw_side_{c}", f"fw_end_{c}", f"fw_door_{c}"
        add(f"fw_side_{c}", wall(side), "Boxcar", shape=[[0, 0, 0, 16, 16, 1.5]])
        for i in range(3):
            add(f"fw_mark_{c}_{i}", wall(f"fw_mark_{c}_{i}"), "Boxcar", shape=[[0, 0, 0, 16, 16, 1.5]])
        for n in range(4):
            add(f"fw_door_{c}_{n}", door_piece(side, dr, n // 2, n % 2), "Boxcar Door", shape=[[0, 0, -1.6, 16, 16, 1.5]])
        add(f"fw_end_{c}", wall(end), "Boxcar", shape=[[0, 0, 0, 16, 16, 1.5]])
        add(f"fw_brake_{c}", wall(end) + brake(), "Boxcar", shape=[[0, 0, 0, 16, 16, 1.5]])
        for lr in "lr":
            add(f"fw_corner_{c}_{lr}", corner(side, end, lr == "l"), "Boxcar",
                shape=[[0, 0, 0, 16, 16, 1.5], [14.5, 0, 0, 16, 16, 16] if lr == "l" else [0, 0, 0, 1.5, 16, 16]])
        add(f"fw_roof_{c}", roof_edge(side), "Boxcar Roof", shape=[[0, 0, 0, 16, 10.2, 16]])
        add(f"fw_end_top_{c}", clip(wall(end)) + eave(side), "Boxcar Roof", shape=[[0, 0, 0, 16, 9.2, 16]])
        for lr in "lr":
            add(f"fw_corner_top_{c}_{lr}", clip(corner(side, end, lr == "l")) + eave(side), "Boxcar Roof",
                shape=[[0, 0, 0, 16, 9.2, 16]])
        add(f"fw_ridge_{c}", ridge(side), "Boxcar Roof", rotate="axis", shape=[[0, 0, 0, 16, 12.6, 16]])
    add("fw_floor", floor(), "Boxcar Floor", shape=[[0, 8, 0, 16, 16, 16]])
    add("fw_floor_mid", floor_mid(), "Boxcar Floor", rotate="axis", shape=[[0, 2, 0, 16, 16, 16]])
    add("fw_truck", truck(), "Wagon Truck", shape=[[0, 0, 0, 16, 2.5, 16], [0, 2.5, 3, 16, 16, 9.6]])
    add("fw_truck_mid", truck_mid(), "Wagon Truck", shape=[[0, 0, 0, 16, 2.5, 16]])
    add("fw_coupler", coupler(), "Coupler", shape=[[4, 3, 0, 12, 12, 16]])
    add("tb_buffer", buffer_stop(True), "Buffer Stop", shape=[[0, 0, 0, 16, 14, 16]])
    add("tb_buffer_mid", buffer_stop(False), "Buffer Stop", shape=[[0, 0, 0, 16, 14, 2.5]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith(("fw_", "tb_buffer"))}
    made = blocks()
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} freight wagon blocks in {path}")


if __name__ == "__main__":
    main()
