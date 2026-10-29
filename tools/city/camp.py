"""The cp_ kit: the army camp's own pieces, built to the landmarks' detail (Josiah, 2026-10-29: "the
time and effort you put into the major landmarks are only as good as the care and attention given
to the smaller parts that surround them"). world/county/Camp.java puts them together.

First pass from general knowledge; the photo sets asked for (refs/military/watchtowers,
gate-guard-booths, boom-barrier-gates, portable-toilets, radio-masts, range-targets-berms,
light-towers) refine it when they land.

- the watchtower, five by five: four timber legs in the corner cells (cp_tw_leg: the post in the
  cell's south-east corner as drawn, turned so it stands on the tower's inside corner), X braces
  between them in tiers three high (cp_tw_brace_a rising toward +x, _b falling, _x crossing, _a_g
  and _b_g with the girt along their top; cp_tw_girt alone), a plank deck on joists (cp_tw_deck,
  cp_tw_deck_post where a leg comes through), all facing so the tower's outside is their north; and
  a tin roof on purlins, never turned so its ribs all run one way (cp_tw_roof, and _n, _e, _s, _w,
  _nw, _ne, _se, _sw reaching over those sides with the fascia);
- lights: cp_flood(_lit) a floodlight on an L bracket (stands on a beam or bolts to a wall behind
  it), cp_flood_hang(_lit) hung from the roof above, cp_flood_bar(_lit) the pair on a mast's
  crossbar;
- the gate: cp_boom_post (the yellow housing, arm out toward +x), cp_boom_arm (the striped arm along
  x), cp_boom_tip (its end on the rest post), cp_boom_up / cp_boom_cap (raised);
- the range: cp_target_lo / cp_target_hi / cp_target_hi_shot (a silhouette target on its frame, two
  blocks), cp_target_down, cp_flagpole, cp_flag, cp_lane_post;
- the mast: cp_mast_r / cp_mast_w (lattice sections in the aviation bands), cp_mast_top (the
  whips), cp_cabinet (the equipment cabinet at its foot);
- fuel: the bladder in its berm (cp_bladder, _ring the cells inside its edge, _e the edge on the
  model's north, _c the north-west corner, _valve; each with a _slack twin, run dry);
- cp_porta_down: a portable toilet blown over onto its back, two blocks long;
- the dead: cp_grave_cross, cp_grave_helmet (a helmet hung on it), cp_stake (the trench's marker);
- the assault course: cp_post, cp_log (along x), cp_wall / cp_wall_top (the climbing wall),
  cp_step, cp_bars / cp_bars_end, cp_rope / cp_rope_end;
- cp_pad_yellow, cp_pad_white: the helipad's painted concrete.

    python3 tools/city/camp.py          rewrites the cp_ entries in blocks.json

photo_textures.py draws the textures (camp.register()).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area, disc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
S2 = math.sqrt(2)


# ---------------------------------------------------------------- textures

def timber(seed, across=False):
    """Pressure-treated pine gone grey: the grain, checks split along it, a knot, the green the
    treatment left in the end grain."""
    t = F.plain(seed, (132, 122, 100), 0.05, 0.15)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    g = P.tnoise(t.rng, 4) * 5
    grain = np.sin(xx * 1.3 + g) * 0.05 + np.sin(xx * 0.31 + g * 0.5) * 0.04
    t.c *= (1 + grain)[..., None]
    t.h += grain * 0.6
    t.mix(np.clip(P.fbm(t.rng) * 0.8, 0, 1) * 0.25, (118, 126, 108))
    for _ in range(3):
        x = int(t.rng.integers(4, N - 4))
        y0 = int(t.rng.integers(0, N - 30))
        area(t, x, y0, x + 1, y0 + int(t.rng.integers(12, 30)), col=(52, 46, 38), dh=-0.35)
    kx, ky = t.rng.uniform(12, 52, 2)
    disc(t, kx, ky, 2.4, col=(88, 70, 50), dh=-0.1)
    disc(t, kx, ky, 1.2, col=(60, 46, 34))
    t.streaks(0.25, 0.85)
    t.grime(0.2, (70, 64, 50))
    if across:
        for a in ("c", "h", "smooth", "f0", "alpha", "emit", "poro"):
            v = getattr(t, a)
            setattr(t, a, np.ascontiguousarray(np.swapaxes(v, 0, 1)))
    return t


def boards(seed):
    """The deck: four planks a block, gapped, nailed at the joists, worn pale down the middle."""
    t = timber(seed, across=True)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, col=(40, 36, 30), dh=-0.4)
        t.c[y + 1:y + 15] *= float(t.rng.uniform(0.9, 1.08))
        for x in (10, 11, 52, 53):
            disc(t, x, y + 5, 0.7, col=(70, 66, 60), f0=60)
            disc(t, x, y + 11, 0.7, col=(70, 66, 60), f0=60)
    t.mix(np.exp(-((np.arange(N)[None, :] - 32) / 18.0) ** 2) * np.ones((N, 1)) * 0.12, (176, 166, 144))
    return t


def boom(seed, across):
    """The arm: red and white in 50 cm bands, reflective tape at the joins, scuffed."""
    t = F.plain(seed, (236, 234, 228), 0.03, 0.55)
    t.f0[:] = 40
    for i in range(0, N, 32):
        area(t, i, 0, i + 16, N, col=(196, 30, 28))
        area(t, i + 15, 0, i + 17, N, col=(250, 250, 240), f0=200, smooth=0.9)
    t.grime(0.2, (60, 56, 50))
    t.speckle(0.01, (80, 80, 80), 0.6)
    if across:
        for a in ("c", "h", "smooth", "f0", "alpha", "emit", "poro"):
            setattr(t, a, np.ascontiguousarray(np.swapaxes(getattr(t, a), 0, 1)))
    return t


def painted(seed, rgb, grime=0.25, rust=0.0):
    t = F.plain(seed, rgb, 0.04, 0.45)
    t.f0[:] = 30
    t.streaks(0.2, 0.85)
    t.grime(grime, (60, 54, 44))
    if rust:
        t.speckle(rust, (120, 66, 36), 0.7, -0.05)
    return t


def hazard(seed):
    t = painted(seed, (226, 186, 40))
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) // 11) % 2 == 0] = (30, 30, 30)
    t.grime(0.3, (60, 54, 44))
    return t


def target(seed, half, shot):
    """The silhouette target: the olive figure, head and shoulders, on buff card stapled to the board;
    the scoring line round the chest; bullet holes, more round the middle; shot to rags at the top."""
    t = F.plain(seed, (196, 178, 136), 0.04, 0.1)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    Y = yy + (0 if half == "hi" else N)  # 0..128 down the whole target
    body = (np.abs(xx - 32) < 22 - np.clip((70 - Y) * 0.6, 0, 22)) & (Y > 44)
    head = np.hypot((xx - 32) / 10.0, (Y - 30) / 13.0) < 1
    fig = body | head
    t.c[fig] = (78, 84, 58)
    ring = np.abs(np.hypot((xx - 32) / 13.0, (Y - 70) / 17.0) - 1) < 0.06
    t.c[ring] = (40, 44, 30)
    area(t, 0, 0, N, 2, col=(150, 132, 96))
    for x in (3, 60):
        for y in (4, 60):
            area(t, x, y, x + 2, y + 1, col=(170, 170, 166), f0=80)
    n = 40 if shot else 14
    for _ in range(n):
        cx, cy = t.rng.normal(32, 9), t.rng.normal(70 if half == "lo" else 60, 18) - (N if half == "lo" else 0)
        if 0 <= cx < N and 0 <= cy < N:
            disc(t, cx, cy, 1.1, col=(24, 20, 16), dh=-0.4)
            disc(t, cx, cy, 2.0, mul=0.85, ring=0.8)
    if shot and half == "hi":
        torn = (yy < 14 + 10 * np.sin(xx * 0.3)) & (xx > 36)
        t.alpha[torn] = 0
    return t


def flag(seed):
    t = F.plain(seed, (184, 30, 30), 0.05, 0.2)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    t.c *= (1 + 0.08 * np.sin(xx * 0.25 + yy * 0.05))[..., None]
    t.streaks(0.3, 0.8)
    t.grime(0.25, (60, 40, 30))
    return t


def bladder(seed, top):
    """The fuel bladder: black coated nylon, a dull sheen, dust settled on top, the welded seams."""
    t = F.plain(seed, (38, 38, 38), 0.04, 0.55)
    t.f0[:] = 30
    if top:
        for v in (20, 44):
            area(t, v, 0, v + 1, N, col=(26, 26, 26), dh=0.1)
        t.mix(np.clip(P.fbm(t.rng) * 0.8 + 0.3, 0, 1) * 0.35, (130, 120, 100))
        t.speckle(0.01, (150, 140, 120), 0.5)
    else:
        area(t, 0, 8, N, 9, col=(26, 26, 26))
        t.grime(0.4, (110, 100, 80))
    return t


def cabinet(seed):
    t = painted(seed, (176, 178, 170), 0.3)
    area(t, 31, 2, 33, N - 2, col=(70, 70, 70), dh=-0.3)
    for y in range(8, 24, 3):
        area(t, 8, y, 24, y + 1, col=(50, 50, 50), dh=-0.3)
        area(t, 40, y, 56, y + 1, col=(50, 50, 50), dh=-0.3)
    area(t, 26, 34, 29, 40, col=(40, 40, 40), f0=80)
    area(t, 6, 44, 22, 52, col=(220, 196, 40))
    area(t, 9, 46, 19, 50, col=(30, 30, 30))
    return t


def pad(seed, paint):
    """Helipad concrete with its paint: the yellow ring, the white H, worn through to the concrete."""
    t = F.plain(seed, (150, 148, 140), 0.06, 0.2)
    t.speckle(0.02, (110, 108, 100), 0.5)
    if paint:
        w = np.clip(P.fbm(t.rng) * 1.2 + 0.85, 0, 1)
        t.mix(w * 0.92, paint)
    for v in (0,):
        area(t, v, 0, v + 1, N, col=(96, 94, 88), dh=-0.3)
        area(t, 0, v, N, v + 1, col=(96, 94, 88), dh=-0.3)
    t.grime(0.3, (70, 66, 58))
    return t


def lens(seed, lit):
    t = F.plain(seed, (250, 248, 232) if lit else (150, 156, 160), 0.02, 0.9)
    t.f0[:] = 200
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[(xx % 8 == 0) | (yy % 8 == 0)] *= 0.9
    if lit:
        t.emit[:] = 1.0
    return t


def tape(seed):
    t = F.plain(seed, (232, 112, 40), 0.04, 0.4)
    for x in range(0, N, 16):
        area(t, x, 0, x + 8, N, col=(236, 232, 220))
    return t


def textures():
    return {
        "cp_timber": lambda: timber(49000),
        "cp_timber_h": lambda: timber(49001, across=True),
        "cp_boards": lambda: boards(49002),
        "cp_boom": lambda: boom(49003, False),
        "cp_boom_v": lambda: boom(49004, True),
        "cp_yellow": lambda: painted(49005, (222, 180, 36), 0.35, 0.01),
        "cp_hazard": lambda: hazard(49006),
        "cp_target_lo": lambda: target(49007, "lo", False),
        "cp_target_hi": lambda: target(49008, "hi", False),
        "cp_target_hi_shot": lambda: target(49009, "hi", True),
        "cp_flag": lambda: flag(49010),
        "cp_bladder": lambda: bladder(49011, False),
        "cp_bladder_top": lambda: bladder(49012, True),
        "cp_mast_r": lambda: painted(49013, (196, 52, 36), 0.3, 0.02),
        "cp_mast_w": lambda: painted(49014, (226, 224, 216), 0.35, 0.02),
        "cp_cabinet": lambda: cabinet(49015),
        "cp_pad": lambda: pad(49016, None),
        "cp_pad_yellow": lambda: pad(49017, (232, 186, 30)),
        "cp_pad_white": lambda: pad(49018, (236, 236, 228)),
        "cp_lens": lambda: lens(49019, False),
        "cp_lens_lit": lambda: lens(49020, True),
        "cp_lamp": lambda: painted(49021, (70, 72, 70), 0.2),
        "cp_tape": lambda: tape(49022),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

def turn(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": [round(v, 3) for v in origin]}


def faces(side, up=None, north=None):
    f = {d: side for d in ("north", "south", "east", "west", "up", "down")}
    if up:
        f["up"] = f["down"] = up
    if north:
        f["north"] = north
    return f


TIM, TIMH, GALV, STEEL = "cp_timber", "cp_timber_h", "st_galv", "steel_olive"


def leg():
    """The leg in the cell's south-east corner, a steel strap and its bolts every block."""
    return [[12, 0, 12, 16, 16, 16, TIM],
            [11.8, 5, 11.8, 16.2, 6.4, 16.2, GALV],
            [11.6, 5.4, 13.6, 11.8, 6.0, 14.4, GALV]]


def brace(sign, girt=False, z=13.2):
    """A 2x6 at 45 degrees corner to corner of the cell in the plane of the legs (z 13.2..14.8)."""
    h = 22.63 / 2 + 1.4
    b = [[8 - h, 6.6, z, 8 + h, 9.4, z + 1.6, TIMH, None, turn("z", 45 * sign, (8, 8, z + 0.8))]]
    if girt:
        b.append([0, 13, 12.6, 16, 16, 15.4, TIMH])
    return b


def deck():
    b = [[0, 14, 0, 16, 16, 16, faces(TIMH, "cp_boards")]]
    for z in (2, 10):
        b.append([0, 9, z, 16, 14, z + 3, TIMH])
    return b


def roof(sides=""):
    """The tin: sheets with their ribs always running north-south (so the pieces are never turned),
    on two purlins; an edge piece reaches 4 over the sides named ("n", "e", "s", "w") with the
    fascia board along them."""
    x0, x1 = (-4 if "w" in sides else 0), (20 if "e" in sides else 16)
    z0, z1 = (-4 if "n" in sides else 0), (20 if "s" in sides else 16)
    tin = faces("corrugated_grey")
    b = [[x0, 0.2, z0, x1, 1.0, z1, tin]]
    for x in np.arange(x0 + 1.5, x1, 4):
        b.append([float(x), 1.0, z0, float(x) + 1.2, 1.6, z1, tin])
    for z in (3, 11):
        b.append([x0, -1.6, z, x1, 0.2, z + 2, TIMH])
    if "n" in sides:
        b.append([x0, -1.2, z0 - 0.6, x1, 1.6, z0, TIMH])
    if "s" in sides:
        b.append([x0, -1.2, z1, x1, 1.6, z1 + 0.6, TIMH])
    if "w" in sides:
        b.append([x0 - 0.6, -1.2, z0, x0, 1.6, z1, TIMH])
    if "e" in sides:
        b.append([x1, -1.2, z0, x1 + 0.6, 1.6, z1, TIMH])
    return b


def lamp(y0, lit, z0=4, x0=4.5, w=7, h=6, tilt=0):
    """A flood head: cast housing, the lens on its north face, fins behind, the yoke round it."""
    ln = "cp_lens_lit" if lit else "cp_lens"
    rot = turn("x", tilt, (8, y0 + h / 2, z0 + 2)) if tilt else None
    head = [[x0, y0, z0, x0 + w, y0 + h, z0 + 4, faces("cp_lamp", north=ln), None, rot],
            [x0 + 0.5, y0 + 0.5, z0 + 4, x0 + w - 0.5, y0 + h - 0.5, z0 + 5.4, "cp_lamp", None, rot],
            [x0 - 0.4, y0 + h - 0.4, z0 - 1.2, x0 + w + 0.4, y0 + h + 0.2, z0, "cp_lamp", None, rot]]
    for i in range(3):
        head.append([x0 + 1 + i * 2.2, y0 + 0.3, z0 + 5.4, x0 + 1.6 + i * 2.2, y0 + h - 0.3, z0 + 6.6, "cp_lamp", None, rot])
    yoke = [[x0 - 1.2, y0 + 1.5, z0 + 1.4, x0 - 0.2, y0 + 3, z0 + 2.6, GALV],
            [x0 + w + 0.2, y0 + 1.5, z0 + 1.4, x0 + w + 1.2, y0 + 3, z0 + 2.6, GALV]]
    return head + yoke


def flood(lit):
    """On an L bracket: the foot plate to stand it on a beam, the back plate to bolt it to a wall."""
    b = lamp(3.2, lit, tilt=-22.5)
    b += [[2.8, 0, 3, 13.2, 1, 16, GALV], [2.8, 0, 15, 13.2, 7, 16, GALV],
          [3.3, 1, 5.4, 4.3, 5.2, 6.6, GALV], [11.7, 1, 5.4, 12.7, 5.2, 6.6, GALV],
          [5, 1, 8, 11, 2.4, 13, "cp_lamp"]]
    return b


def flood_hang(lit):
    b = lamp(4, lit, tilt=-22.5)
    b += [[7.2, 10, 6.2, 8.8, 16, 7.8, GALV], [3.3, 9.2, 5.6, 12.7, 10.2, 7.2, GALV],
          [3.3, 5.2, 5.6, 4.3, 10.2, 7.2, GALV], [11.7, 5.2, 5.6, 12.7, 10.2, 7.2, GALV],
          [6, 15, 4, 10, 16, 10, GALV]]
    return b


def flood_bar(lit):
    """The crossbar on a mast's top with a head each end, the junction box in the middle."""
    b = [[-6, 0, 7, 22, 1.6, 9, GALV], [6, 0, 6.5, 10, 4, 9.5, "cp_cabinet"], [7, -4, 7, 9, 0, 9, GALV]]
    b += lamp(1.6, lit, x0=-5.5, tilt=-22.5) + lamp(1.6, lit, x0=14.5, tilt=-22.5)
    return b


def boom_post():
    """The barrier's housing: the yellow cabinet on its plinth, the hazard band, the hub the arm
    pivots on (toward +x), the counterweight the other side."""
    y = "cp_yellow"
    return [[2, 0, 3, 14, 1.2, 13, "concrete_panel"],
            [3.5, 1.2, 4.5, 11.5, 14, 11.5, faces(y)],
            [3.5, 1.2, 4.4, 11.5, 4, 4.5, "cp_hazard"],
            [3.2, 14, 4.2, 11.8, 15, 11.8, y],
            [11.5, 10, 6.5, 13.5, 14, 9.5, GALV],
            [0.5, 9.5, 6, 3.5, 14.5, 10, "cp_lamp"],
            [6, 6, 4.2, 9, 9, 4.5, "cp_lamp"]]


def arm(x0=0, x1=16):
    """The arm: an octagon of two boxes along x, its centre 12 up."""
    f = {d: "cp_boom_v" for d in ("north", "south", "east", "west")}
    f["up"] = f["down"] = "cp_boom"
    return [[x0, 10.4, 6.4, x1, 13.6, 9.6, f],
            [x0, 10.4, 6.4, x1, 13.6, 9.6, f, None, turn("x", 45, (8, 12, 8))]]


def boom_tip():
    b = arm(0, 12)
    b += [[12, 10, 6, 13, 14, 10, "home_plastic_black"],
          [9, 0, 6.5, 11, 10.4, 9.5, "cp_yellow"], [8.5, 9.6, 5.5, 11.5, 10.4, 10.5, GALV],
          [8, 0, 5.5, 12, 0.8, 10.5, "concrete_panel"]]
    return b


def boom_up():
    """The arm raised: an octagon up the cell over the hub, the stripes running across."""
    f = {d: "cp_boom" for d in ("north", "south", "east", "west")}
    return [[10.9, 0, 6.4, 14.1, 16, 9.6, f], [10.9, 0, 6.4, 14.1, 16, 9.6, f, None, turn("y", 45, (12.5, 8, 8))]]


def boom_cap():
    return boom_up()[:0] + [[10.9, 0, 6.4, 14.1, 10, 9.6, faces("cp_boom")],
                            [10.9, 0, 6.4, 14.1, 10, 9.6, faces("cp_boom"), None, turn("y", 45, (12.5, 5, 8))],
                            [10.5, 10, 6, 14.5, 11, 10, "home_plastic_black"]]


def target_lo():
    """The frame: two 2x2 stakes driven in, the board between from 10 up, the card on its face."""
    b = [[1.5, 0, 8, 3.5, 16, 10, TIM], [12.5, 0, 8, 14.5, 16, 10, TIM],
         [1, 10, 7.2, 15, 16, 8, faces("tn_ply", north="cp_target_lo")],
         [0.5, 0, 7.5, 4.5, 1.2, 10.5, "rubble"], [11.5, 0, 7.5, 15.5, 1.2, 10.5, "rubble"]]
    return b


def target_hi(shot):
    t = "cp_target_hi_shot" if shot else "cp_target_hi"
    b = [[1.5, 0, 8, 3.5, 14, 10, TIM], [12.5, 0, 8, 14.5, 14, 10, TIM],
         [1, 0, 7.2, 15, 15, 8, faces("tn_ply", north=t)]]
    if shot:
        b[2] = [1, 0, 7.2, 15, 11, 8, faces("tn_ply", north=t)]
        b.append([7, 11, 7.2, 15, 13, 8, faces("tn_ply", north=t), None, turn("z", -22.5, (7, 11, 7.6))])
    return b


def target_down():
    return [[1, 0, 2, 15, 0.8, 16, faces("tn_ply", up="cp_target_lo")],
            [1.5, 0, 0, 3.5, 2, 6, TIM], [12.5, 0, 0, 14.5, 2, 4.5, TIM],
            [1.5, 0, 14, 3.5, 3, 16, TIM], [12.5, 0, 13, 14.5, 4, 15, TIM]]


def mast(tex):
    """A lattice section 10 wide: the four legs, a ring at the foot, a diagonal on each face."""
    a, b = 3, 13
    out = []
    for x, z in ((a, a), (b - 1, a), (a, b - 1), (b - 1, b - 1)):
        out.append([x, 0, z, x + 1, 16, z + 1, tex])
    out += [[a, 0, a, b, 0.8, a + 0.8, tex], [a, 0, b - 0.8, b, 0.8, b, tex],
            [a, 0, a, a + 0.8, 0.8, b, tex], [b - 0.8, 0, a, b, 0.8, b, tex]]
    h = 14.14 / 2 + 0.6
    out += [[8 - h, 7.6, a, 8 + h, 8.4, a + 0.6, tex, None, turn("z", 45, (8, 8, a + 0.3))],
            [8 - h, 7.6, b - 0.6, 8 + h, 8.4, b, tex, None, turn("z", -45, (8, 8, b - 0.3))],
            [a, 7.6, 8 - h, a + 0.6, 8.4, 8 + h, tex, None, turn("x", -45, (a + 0.3, 8, 8))],
            [b - 0.6, 7.6, 8 - h, b, 8.4, 8 + h, tex, None, turn("x", 45, (b - 0.3, 8, 8))]]
    return out


def mast_top():
    """The head: a platform ring, the whip aerials, a yagi pointing out, the cable down."""
    w = "cp_mast_w"
    b = [[2, 0, 2, 14, 1, 14, GALV], [2, 1, 2, 2.6, 4, 14, GALV], [13.4, 1, 2, 14, 4, 14, GALV],
         [2, 1, 2, 14, 4, 2.6, GALV], [2, 1, 13.4, 14, 4, 14, GALV],
         [7.5, 1, 7.5, 8.5, 16, 8.5, w], [3.2, 1, 3.2, 3.8, 28, 3.8, GALV], [12.2, 1, 12.2, 12.8, 24, 12.8, GALV],
         [7.6, 11, -6, 8.4, 11.8, 8, GALV]]
    for z in (-5, -2, 1, 4):
        b.append([5, 11, z, 11, 11.8, z + 0.8, GALV])
    return b


def bladder_boxes(kind, top=9.5):
    """The bladder pillow: full in the middle (top), a little lower in the ring inside its edge, the
    edge rounding down to the ground in steps (e on the model's north, c the north-west corner).
    Slack ones, run dry, lie a few sixteenths deep."""
    t, side = "cp_bladder_top", "cp_bladder"
    f = faces(side, t)
    if kind in ("mid", "valve"):
        b = [[0, 0, 0, 16, top, 16, f]]
        if kind == "valve":
            b += [[6, top, 6, 10, top + 1.5, 10, GALV], [6.5, top + 1.5, 6.5, 9.5, top + 3.2, 9.5, "cp_yellow"],
                  [7.2, top, 9.5, 8.8, top + 1.4, 16, "home_plastic_black"]]
        return b
    steps = [(4.5, 16, top * 0.8), (2.8, 4.5, top * 0.62), (1.4, 2.8, top * 0.42), (0.4, 1.4, top * 0.2)]
    if kind == "e":
        return [[0, 0, z0, 16, round(h, 2), z1, f] for z0, z1, h in steps]
    return [[z0, 0, z0, 16, round(h, 2), 16, f] for z0, _, h in steps]


def porta_down():
    """The toilet on its back: the shell two blocks long, the door up, the roof dome at the end."""
    s = "st_porta"
    return [[1, 0, 0, 15, 14, 15, {"north": s, "south": s, "east": s, "west": s, "down": s, "up": "st_porta_hi"}],
            [1, 0, 15, 15, 14, 30, {"north": s, "south": s, "east": s, "west": s, "down": s, "up": "st_porta_lo"}],
            [0.5, 0.5, -1.2, 15.5, 14.5, 0, s], [1, 1, -2.4, 15, 14, -1.2, "st_porta_roof"],
            [2.2, 2.2, -3.6, 13.8, 12.8, -2.4, "st_porta_roof"], [4, 4, -4.4, 12, 11, -3.6, "st_porta_roof"],
            [12.4, 12.4, -7.5, 13.8, 13.8, -1.2, "home_plastic_black"],
            [0.5, 0, 30, 15.5, 14.5, 30.6, s]]


def cross(helmet):
    b = [[7, 0, 7, 9, 15, 8.6, TIM], [3.5, 9.5, 6.4, 12.5, 11.3, 7, TIMH], [6.8, 9, 6.2, 9.2, 11.8, 6.5, "tn_cord"],
         [5, 0, 5, 11, 0.8, 11, "rubble"]]
    if helmet:
        b += [[4.6, 14.6, 4.6, 11.4, 17.6, 11.4, "bunk_olive"], [5.4, 17.6, 5.4, 10.6, 18.8, 10.6, "bunk_olive"],
              [4.2, 14.4, 4.2, 11.8, 15, 11.8, "bunk_olive"], [7.6, 13, 4.2, 8.4, 14.6, 4.6, "tn_cord"]]
    return b


def stake():
    return [[7.2, 0, 7.2, 8.8, 9, 8.8, TIM], [7.1, 6.6, 7.1, 8.9, 7.8, 8.9, "cp_tape"], [8.8, 5, 7.6, 12, 7.6, 8, "cp_tape", None,
                                                                                        turn("z", -22.5, (8.8, 7.6, 7.8))]]


def post():
    return [[5.5, 0, 6, 10.5, 16, 10, TIM], [6, 0, 5.5, 10, 16, 10.5, TIM]]


def log():
    """A log along x: a round section of two boxes, centre 5 up, the ends cut pale."""
    return [[0, 0.4, 3.6, 16, 9.6, 12.4, faces("cp_timber_h")],
            [0, 0.4, 3.6, 16, 9.6, 12.4, faces("cp_timber_h"), None, turn("x", 45, (8, 5, 8))]]


def climb_wall(top):
    b = [[0, 0, 7, 16, 16 if not top else 14, 9, faces(TIMH)]]
    for y in (3, 8, 13) if not top else (3, 8):
        b.append([0, y, 6, 16, y + 1.4, 7, TIMH])
    if top:
        b += [[0, 14, 6.5, 16, 16, 9.5, TIMH]]
    return b


def step():
    return [[0, 6, 0, 16, 8, 16, faces(TIMH, "cp_boards")], [1, 0, 1, 3, 6, 3, TIM], [13, 0, 1, 15, 6, 3, TIM],
            [1, 0, 13, 3, 6, 15, TIM], [13, 0, 13, 15, 6, 15, TIM]]


def bars(end):
    """Monkey bars along x: the two rails, a rung every 4; at the end the crossbeam onto the posts."""
    b = [[0, 13, 1, 16, 15, 3, GALV], [0, 13, 13, 16, 15, 15, GALV]]
    for x in range(2, 16, 4):
        b.append([x, 13.4, 3, x + 1, 14.4, 13, GALV])
    if end:
        b = [[6, 11, -8, 10, 15, 24, TIMH], [8, 13, 1, 16, 15, 3, GALV], [8, 13, 13, 16, 15, 15, GALV],
             [12, 13.4, 3, 13, 14.4, 13, GALV]]
    return b


def rope(end):
    b = [[7.4, 0 if not end else 4, 7.4, 8.6, 16, 8.6, "tn_cord"]]
    if end:
        b.append([6.6, 2, 6.6, 9.4, 4.6, 9.4, "tn_cord"])
    return b


def lane_post():
    return [[7, 0, 7, 9, 12, 9, TIM], [4, 8, 6.4, 12, 12, 7, "cp_mast_w"], [4, 8, 6.3, 12, 9, 6.4, "cp_mast_r"]]


def flagpole(top):
    b = [[7.3, 0, 7.3, 8.7, 16, 8.7, GALV]]
    if top:
        b += [[7, 15, 7, 9, 16, 9, GALV],
              [8.7, 6, 7.7, 16.7, 15, 8.3, faces("cp_flag"), None, turn("y", -22.5, (8.7, 10, 8))],
              [16, 6, 10, 24, 15, 10.6, faces("cp_flag"), None, turn("y", 22.5, (16, 10, 10.3))]]
    return b


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, light=None, loot=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {}
        spec.update(title=title, sound=sound)
        if loot:
            spec["loot"] = loot
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    add("cp_tw_leg", leg(), "Tower Leg", "WOOD", shape=[[12, 0, 12, 16, 16, 16]])
    add("cp_tw_brace_a", brace(1), "Tower Brace", "WOOD", decor=True)
    add("cp_tw_brace_b", brace(-1), "Tower Brace", "WOOD", decor=True)
    add("cp_tw_brace_x", brace(1) + brace(-1, z=12.0), "Tower Brace", "WOOD", decor=True)
    add("cp_tw_brace_a_g", brace(1, True), "Tower Brace", "WOOD", shape=[[0, 13, 12.6, 16, 16, 15.4]])
    add("cp_tw_brace_b_g", brace(-1, True), "Tower Brace", "WOOD", shape=[[0, 13, 12.6, 16, 16, 15.4]])
    add("cp_tw_girt", [[0, 13, 12.6, 16, 16, 15.4, TIMH]], "Tower Girt", "WOOD", shape=[[0, 13, 12.6, 16, 16, 15.4]])
    add("cp_tw_deck", deck(), "Tower Deck", "WOOD", rotate=None, shape=[[0, 9, 0, 16, 16, 16]])
    add("cp_tw_deck_post", deck() + leg(), "Tower Deck", "WOOD", shape=[[0, 9, 0, 16, 16, 16]])
    for sides in ("", "n", "e", "s", "w", "nw", "ne", "se", "sw"):
        add("cp_tw_roof" + ("_" + sides if sides else ""), roof(sides), "Tin Roof", "METAL", rotate=None,
            shape=[[0, 0, 0, 16, 1.6, 16]])
    for lit in (False, True):
        s = "_lit" if lit else ""
        add("cp_flood" + s, flood(lit), "Floodlight", "METAL", decor=True, light=15 if lit else None)
        add("cp_flood_hang" + s, flood_hang(lit), "Floodlight", "METAL", decor=True, light=15 if lit else None)
        add("cp_flood_bar" + s, flood_bar(lit), "Floodlights", "METAL", decor=True, light=15 if lit else None)
    add("cp_boom_post", boom_post(), "Barrier", "METAL", shape=[[3.5, 0, 4.5, 11.5, 15, 11.5]])
    add("cp_boom_arm", arm(), "Barrier Arm", "METAL", shape=[[0, 10, 6, 16, 14, 10]])
    add("cp_boom_tip", boom_tip(), "Barrier Arm", "METAL", shape=[[0, 10, 6, 13, 14, 10], [9, 0, 6.5, 11, 10, 9.5]])
    add("cp_boom_up", boom_up(), "Barrier Arm", "METAL", shape=[[10.5, 0, 6, 14.5, 16, 10]])
    add("cp_boom_cap", boom_cap(), "Barrier Arm", "METAL", shape=[[10.5, 0, 6, 14.5, 11, 10]])
    add("cp_target_lo", target_lo(), "Target", "WOOD", shape=[[1, 0, 7.2, 15, 16, 10]])
    add("cp_target_hi", target_hi(False), "Target", "WOOD", shape=[[1, 0, 7.2, 15, 15, 10]])
    add("cp_target_hi_shot", target_hi(True), "Target", "WOOD", shape=[[1, 0, 7.2, 15, 13, 10]])
    add("cp_target_down", target_down(), "Target", "WOOD", decor=True)
    add("cp_flagpole", flagpole(False), "Flagpole", "METAL", rotate=None, shape=[[7, 0, 7, 9, 16, 9]])
    add("cp_flag", flagpole(True), "Range Flag", "WOOL", shape=[[7, 0, 7, 9, 16, 9]])
    add("cp_lane_post", lane_post(), "Lane Marker", "WOOD", shape=[[6.5, 0, 6.5, 9.5, 12, 9.5]])
    add("cp_mast_r", mast("cp_mast_r"), "Radio Mast", "METAL", rotate=None, shape=[[3, 0, 3, 13, 16, 13]])
    add("cp_mast_w", mast("cp_mast_w"), "Radio Mast", "METAL", rotate=None, shape=[[3, 0, 3, 13, 16, 13]])
    add("cp_mast_top", mast_top(), "Mast Head", "METAL", shape=[[2, 0, 2, 14, 4, 14]])
    add("cp_cabinet", [[2, 0, 3, 14, 15, 13, faces("cp_mast_w", north="cp_cabinet")], [1.5, 15, 2.5, 14.5, 16, 13.5, GALV],
                       [3, 0, 2, 13, 1, 14, "concrete_panel"]], "Equipment Cabinet", "METAL",
        shape=[[2, 0, 3, 14, 16, 13]], loot="basement_supplies")
    for v, top in (("", 9.5), ("_slack", 3.0)):
        add("cp_bladder" + v, bladder_boxes("mid", top), "Fuel Bladder", "WOOL", rotate=None, shape=[[0, 0, 0, 16, top, 16]])
        add("cp_bladder_ring" + v, bladder_boxes("mid", top * 0.88), "Fuel Bladder", "WOOL", rotate=None,
            shape=[[0, 0, 0, 16, top * 0.88, 16]])
        add("cp_bladder_valve" + v, bladder_boxes("valve", top), "Fuel Bladder", "WOOL", shape=[[0, 0, 0, 16, top, 16]])
        add("cp_bladder_e" + v, bladder_boxes("e", top * 0.88), "Fuel Bladder", "WOOL", shape=[[0, 0, 3, 16, top * 0.7, 16]])
        add("cp_bladder_c" + v, bladder_boxes("c", top * 0.88), "Fuel Bladder", "WOOL", shape=[[3, 0, 3, 16, top * 0.7, 16]])
    add("cp_porta_down", porta_down(), "Portable Toilet", "WOOD", shape=[[1, 0, 0, 15, 14, 16]])
    add("cp_grave_cross", cross(False), "Grave Marker", "WOOD", shape=[[6.5, 0, 6.5, 9.5, 15, 9]])
    add("cp_grave_helmet", cross(True), "Grave Marker", "WOOD", shape=[[6.5, 0, 6.5, 9.5, 15, 9]])
    add("cp_stake", stake(), "Marker Stake", "WOOD", decor=True)
    add("cp_post", post(), "Post", "WOOD", rotate=None, shape=[[5.5, 0, 5.5, 10.5, 16, 10.5]])
    add("cp_log", log(), "Log", "WOOD", shape=[[0, 0, 3.6, 16, 9.6, 12.4]])
    add("cp_wall", climb_wall(False), "Climbing Wall", "WOOD", shape=[[0, 0, 6, 16, 16, 9]])
    add("cp_wall_top", climb_wall(True), "Climbing Wall", "WOOD", shape=[[0, 0, 6, 16, 16, 9.5]])
    add("cp_step", step(), "Platform", "WOOD", shape=[[0, 0, 0, 16, 8, 16]])
    add("cp_bars", bars(False), "Monkey Bars", "METAL", decor=True)
    add("cp_bars_end", bars(True), "Monkey Bars", "METAL", shape=[[6, 11, 0, 10, 15, 16]])
    add("cp_rope", rope(False), "Rope", "WOOL", rotate=None, decor=True, climb=True)
    add("cp_rope_end", rope(True), "Rope", "WOOL", rotate=None, decor=True, climb=True)
    add("cp_pad", None, "Helipad", "STONE", rotate=None, tex="cp_pad")
    add("cp_pad_yellow", None, "Helipad", "STONE", rotate=None, tex="cp_pad_yellow")
    add("cp_pad_white", None, "Helipad", "STONE", rotate=None, tex="cp_pad_white")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("cp_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} camp blocks in {path}")


if __name__ == "__main__":
    main()
