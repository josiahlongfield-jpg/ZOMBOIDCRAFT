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
- the gate booth, three by three (refs/military/gate-guard-booths): cp_gb_wall / cp_gb_wall_corner (the
  waist-high wall on the model's north, _corner with the west side too), cp_gb_win(_corner)(_broken)
  the glazing above it, cp_gb_head(_corner) the band over the windows with the roof reaching over,
  cp_gb_roof the middle with the ceiling light and the beacon;
- the light tower trailer (refs/military/light-towers), its lamps looking north and the tow bar to
  the south: cp_lt_rear_{olive,white} (the mast end over the axle) with cp_lt_front_* in the cell
  south of it, cp_lt_cap_rear / cp_lt_cap_front on top of them, then cp_lt_mast sections and
  cp_lt_head(_lit), the four lamps; with no mast sections it's wound down for the road;
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


# the light tower trailer's genset housing (refs/military/light-towers): olive for the army, white
# with the red band for the hire fleet
LT = {"olive": ((84, 92, 58), None, (222, 218, 196), "LT-07"),
      "white": ((226, 224, 214), (184, 40, 32), (184, 40, 32), "BRIGHTMAST")}


def lt_paint(seed, c):
    return painted(seed, LT[c][0], 0.3, 0.015)


def lt_seams(t, x0, y0, x1, y1):
    """A door's outline: the shadowed gap and a lit edge inside it."""
    area(t, x0, y0, x1, y0 + 1, mul=(0.45, 0.45, 0.45), dh=-0.4)
    area(t, x0, y1 - 1, x1, y1, mul=(0.45, 0.45, 0.45), dh=-0.4)
    area(t, x0, y0, x0 + 1, y1, mul=(0.45, 0.45, 0.45), dh=-0.4)
    area(t, x1 - 1, y0, x1, y1, mul=(0.45, 0.45, 0.45), dh=-0.4)
    area(t, x0 + 1, y0 + 1, x1 - 1, y0 + 2, mul=(1.12, 1.12, 1.12))


def lt_louvres(t, x0, y0, x1, y1):
    for y in range(y0, y1, 3):
        area(t, x0, y, x1, y + 1, mul=(0.3, 0.3, 0.3), dh=-0.5)
        area(t, x0, y + 1, x1, y + 2, mul=(1.15, 1.15, 1.15), dh=0.2)


def lt_side(seed, c, panel):
    """A side door of the housing: the louvred one over the engine at the back, at the front the
    control panel door with the maker's name; the hire fleet's red band across both."""
    rgb, band, ink, name = LT[c]
    t = lt_paint(seed, c)
    lt_seams(t, 3, 3, 61, 61)
    if band:
        area(t, 0, 36, N, 42, col=band)
    if panel:
        from bunker import stencil
        stencil(t, name, ink, 9 if c == "white" else 12, x=8, y=14, w=48, h=16, chip=0.15)
        area(t, 40, 46, 52, 54, col=(222, 196, 40))
        area(t, 42, 48, 50, 49, col=(30, 30, 30))
        area(t, 42, 51, 48, 52, col=(30, 30, 30))
    else:
        lt_louvres(t, 9, 8, 55, 32)
        lt_louvres(t, 9, 45, 55, 57)
    area(t, 54, 30, 58, 36, col=(34, 34, 34), dh=-0.3)
    area(t, 5, 8, 7, 14, col=(70, 70, 66), dh=0.2)
    area(t, 5, 50, 7, 56, col=(70, 70, 66), dh=0.2)
    t.grime(0.35, (58, 52, 40))
    return t


def lt_grille(seed, c):
    """The radiator end: the grille of slots, the lifting slots under it."""
    t = lt_paint(seed, c)
    lt_seams(t, 4, 6, 60, 46)
    for x in range(8, 57, 4):
        area(t, x, 10, x + 2, 42, col=(28, 28, 26), dh=-0.6)
    if LT[c][1]:
        area(t, 0, 50, N, 55, col=LT[c][1])
    t.grime(0.45, (40, 36, 30))
    return t


def lt_end(seed, c):
    """The mast end: the cable gland, the data plate, the hazard label."""
    t = lt_paint(seed, c)
    lt_seams(t, 4, 4, 60, 60)
    area(t, 40, 10, 54, 18, col=(196, 196, 186))
    for y in (12, 14, 16):
        area(t, 41, y, 52, y + 1, col=(60, 60, 60))
    area(t, 10, 40, 22, 52, col=(230, 196, 36))
    area(t, 13, 43, 19, 49, col=(24, 24, 24))
    if LT[c][1]:
        area(t, 0, 30, N, 35, col=LT[c][1])
    t.grime(0.4, (58, 52, 40))
    return t


def lt_top(seed, c):
    t = lt_paint(seed, c)
    area(t, 0, 31, N, 33, mul=(0.6, 0.6, 0.6), dh=-0.3)
    lt_louvres(t, 12, 6, 52, 24)
    t.grime(0.5, (60, 54, 44))
    t.speckle(0.03, (110, 64, 34), 0.6, -0.05)
    return t


# the gate booth (refs/military/gate-guard-booths): a prefab of ribbed white steel, glazed all round
# above the waist, a flat roof reaching well out over the sides

def gb_clad(seed):
    """The cladding: vertical ribs every quarter, rust weeping from the foot, road dirt splashed up."""
    t = painted(seed, (224, 222, 212), 0.2, 0.01)
    for x in range(0, N, 16):
        area(t, x, 0, x + 2, N, mul=(0.78, 0.78, 0.78), dh=0.5)
        area(t, x + 2, 0, x + 3, N, mul=(1.1, 1.1, 1.1), dh=0.3)
    yy = np.mgrid[0:N, 0:N][0]
    splash = np.clip((yy - 40) / 24.0, 0, 1) * np.clip(P.fbm(t.rng) + 0.6, 0, 1)
    t.mix(splash * 0.55, (96, 84, 64))
    return t


def gb_roof(seed):
    """Roofing felt, patched, a puddle's dried ring, moss in the low spots."""
    t = F.plain(seed, (74, 74, 72), 0.08, 0.3)
    t.speckle(0.05, (120, 118, 110), 0.4)
    area(t, 20, 8, 44, 30, mul=(0.85, 0.85, 0.85), dh=0.2)
    t.mix(np.clip(P.fbm(t.rng) - 0.2, 0, 1) * 0.5, (70, 84, 48))
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
        "cp_gb_clad": lambda: gb_clad(49060),
        "cp_gb_inner": lambda: painted(49061, (206, 204, 190), 0.25),
        "cp_gb_trim": lambda: painted(49062, (70, 72, 74), 0.15),
        "cp_gb_roof": lambda: gb_roof(49063),
        "cp_lt_paint_olive": lambda: lt_paint(49030, "olive"),
        "cp_lt_door_olive": lambda: lt_side(49031, "olive", False),
        "cp_lt_panel_olive": lambda: lt_side(49032, "olive", True),
        "cp_lt_grille_olive": lambda: lt_grille(49033, "olive"),
        "cp_lt_end_olive": lambda: lt_end(49034, "olive"),
        "cp_lt_top_olive": lambda: lt_top(49035, "olive"),
        "cp_lt_paint_white": lambda: lt_paint(49040, "white"),
        "cp_lt_door_white": lambda: lt_side(49041, "white", False),
        "cp_lt_panel_white": lambda: lt_side(49042, "white", True),
        "cp_lt_grille_white": lambda: lt_grille(49043, "white"),
        "cp_lt_end_white": lambda: lt_end(49044, "white"),
        "cp_lt_top_white": lambda: lt_top(49045, "white"),
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


# ---------------------------------------------------------------- the light tower trailer

FULL = {d: [0, 0, 16, 16] for d in ("north", "south", "east", "west", "up", "down")}
TYRE, DARK = "gs_tyre", "cp_lamp"


def skin(x0, y0, z0, x1, y1, z1, tex):
    """A box with each face's texture stretched over the whole face, so a door or a grille drawn
    on one tile fits the side of the housing however big it is."""
    return [x0, y0, z0, x1, y1, z1, tex, FULL]


def wheel(x0, x1, z, r=5.0):
    """A road wheel on the axle at z: the tyre an octagon of two boxes, the rim and hub cap."""
    turned = turn("x", 45, (8, r, z))
    rim, a = "st_galv", r * 0.4142
    tyre = [[x0, 0, z - a, x1, 2 * r, z + a, TYRE], [x0, r - a, z - r, x1, r + a, z + r, TYRE]]
    return tyre + [t + [None, turned] for t in tyre] + [
            [x0 - 0.2, r - 2.6, z - 2.6, x1 + 0.2, r + 2.6, z + 2.6, rim],
            [x0 - 0.5, r - 1, z - 1, x1 + 0.5, r + 1, z + 1, DARK]]


def outrigger(z, side):
    """A stabiliser leg swung out from the chassis: the box beam, the screw jack at its end, the
    crank on top and the foot pad on the ground."""
    if side < 0:
        beam, jx = (-9, 1), -9
    else:
        beam, jx = (15, 25), 23
    return [[beam[0], 5, z, beam[1], 7, z + 2, "cp_yellow"],
            [jx, 1, z - 0.2, jx + 2, 9, z + 2.2, "cp_yellow"],
            [jx + 0.6, 0.6, z + 0.4, jx + 1.4, 2, z + 1.6, "st_galv"],
            [jx - 1.5, 0, z - 1.5, jx + 3.5, 0.6, z + 3.5, "st_galv"],
            [jx + 0.6, 9, z + 0.6, jx + 1.4, 11, z + 1.4, "st_galv"],
            [jx - 1.4, 10.2, z + 0.6, jx + 3.4, 11, z + 1.4, "st_galv"]]


def lt_rear(c):
    """The mast end of the trailer: the housing's back half over the axle, the wheels and their
    mudguards, the mast foot on its bracket with the winch beside it, the tail lights and the
    stabiliser legs swung out."""
    pt = "cp_lt_paint_" + c
    side = {"east": "cp_lt_door_" + c, "west": "cp_lt_door_" + c, "north": "cp_lt_end_" + c,
            "south": pt, "up": "cp_lt_top_" + c, "down": DARK}
    b = [skin(1, 6.5, 4, 15, 22, 16, side),
         [0.6, 22, 3.6, 15.4, 23, 16, pt],
         [3, 4.5, 3.4, 5, 6.5, 16, DARK], [11, 4.5, 3.4, 13, 6.5, 16, DARK], [1, 4.5, 3.4, 15, 6.5, 4.6, DARK],
         [1, 4.2, 15.2, 15, 5.2, 16.8, DARK]]
    b += wheel(-1.6, 1.0, 16) + wheel(15.0, 17.6, 16)
    for x0, x1 in ((-2, 1.2), (14.8, 18)):
        b += [[x0, 10.4, 10, x1, 11.2, 22, pt], [x0, 6, 9.6, x1, 11.2, 10.4, pt], [x0, 6, 21.6, x1, 11.2, 22.4, pt]]
    b += outrigger(5, -1) + outrigger(5, 1)
    # the mast foot: the hinge bracket bolted to the end wall, the first section rising out of it,
    # the winch that runs it up with its crank, the coiled cable to the lamps
    b += [[5.4, 6.5, 0, 10.6, 9, 4, "st_galv"], [5.4, 9, 3, 10.6, 20, 4, "st_galv"],
          [6.25, 9, 0.3, 9.75, 22, 3.7, "st_galv"], [5.9, 9, 0, 10.1, 10.4, 4, "st_galv"],
          [10.8, 11, 1.4, 14, 14.4, 3.6, DARK], [11.2, 11.4, 0.8, 13.6, 14, 1.4, "st_galv"],
          [13.2, 12.4, 0, 14, 13.2, 0.8, "st_galv"], [13.2, 9.6, -0.4, 14, 13.2, 0.4, "st_galv"]]
    for i, y in enumerate((10, 12.4, 14.8, 17.2, 19.6)):
        b.append([4.6 if i % 2 else 5.0, y, 1.2, 6.2, y + 1.2, 2.6, "home_plastic_black"])
    # tail lights and reflectors either side, the plate between them low down
    b += [[1.4, 7.2, 3.4, 3.8, 9, 4, "ch_redglass"], [12.2, 7.2, 3.4, 14.6, 9, 4, "ch_redglass"],
          [1.4, 9.2, 3.5, 3.8, 10.2, 4, "trade_glass_amber"], [12.2, 9.2, 3.5, 14.6, 10.2, 4, "trade_glass_amber"],
          [1.2, 4.6, 2.6, 4.6, 7.2, 3.4, "cp_mast_w"]]
    return b


def lt_front(c):
    """The tow end: the housing's front half with the control panel door and the radiator grille,
    the A-frame drawbar out to the coupling, the jockey wheel wound down, the safety chains, the
    extinguisher on its bracket and the front stabilisers."""
    pt = "cp_lt_paint_" + c
    side = {"east": "cp_lt_panel_" + c, "west": "cp_lt_panel_" + c, "north": pt,
            "south": "cp_lt_grille_" + c, "up": "cp_lt_top_" + c, "down": DARK}
    b = [skin(1, 6.5, 0, 15, 22, 16, side),
         [0.6, 22, 0, 15.4, 23, 16.4, pt],
         [3, 4.5, 0, 5, 6.5, 16, DARK], [11, 4.5, 0, 13, 6.5, 16, DARK], [1, 4.5, 15, 15, 6.5, 16.4, DARK]]
    # the A-frame: two arms from the chassis rails meeting at the coupling
    b += [[3.6, 4.6, 16, 5.4, 6.4, 27.4, DARK, None, turn("y", 22.5, (4.5, 5.5, 16))],
          [10.6, 4.6, 16, 12.4, 6.4, 27.4, DARK, None, turn("y", -22.5, (11.5, 5.5, 16))],
          [7, 4.6, 16.4, 9, 6.4, 28, DARK],
          [6.4, 4.2, 26, 9.6, 7.2, 30.6, "st_galv"], [6.8, 3.2, 28.4, 9.2, 4.2, 30.6, DARK],
          [7.4, 7.2, 27, 8.6, 9, 28.6, "steel_red"]]
    # the chains, a link or two hanging slack
    b += [[6, 3.8, 24, 6.6, 4.6, 29, "st_galv"], [9.4, 3.8, 24, 10, 4.6, 29, "st_galv"]]
    # the jockey wheel: the clamp on the drawbar, the tube, the handle, the little wheel
    b += [[9, 4, 21, 11.6, 7.4, 23.4, "st_galv"], [9.6, 2.6, 21.5, 11, 12, 22.9, "st_galv"],
          [9.4, 12, 21.3, 11.2, 12.8, 23.1, DARK], [10.1, 12.8, 22, 10.5, 13.6, 22.4, "st_galv"],
          [10.1, 13.2, 22, 13.6, 13.8, 22.4, "st_galv"],
          [9.8, 0, 20.6, 10.8, 3.4, 24, TYRE], [9.4, 1.2, 21.8, 11.2, 2.2, 22.8, "st_galv"]]
    # the extinguisher strapped to the front of the chassis
    b += [[2.2, 6.5, 16, 4.8, 13, 18.6, "steel_red"], [2.8, 13, 16.6, 4.2, 14.4, 18, DARK],
          [3.2, 14, 16.8, 5.8, 14.6, 17.6, DARK], [2, 9, 15.8, 5, 10, 18.8, "st_galv"]]
    b += outrigger(12, -1) + outrigger(12, 1)
    # the fuel filler, the door handle, the panel's sockets under their flap
    b += [[15, 13, 3, 15.6, 15, 5, DARK], [14.6, 15, 3.4, 15.4, 15.4, 4.6, "st_galv"],
          [0.4, 9, 9, 1, 11.4, 13, DARK], [0.6, 11.4, 9, 1.2, 11.8, 13, pt]]
    return b


def lt_cap_rear():
    """On top at the mast end: the mast going on up through its guide, the lifting eye."""
    return [[6.25, 0, 0.3, 9.75, 16, 3.7, "st_galv"], [5.6, 6, -0.2, 10.4, 8, 4.2, "st_galv"],
            [5.4, 7, 4, 10.6, 8, 6, "st_galv"],
            [6, 7, 12, 7, 11, 13, "st_galv"], [9, 7, 12, 10, 11, 13, "st_galv"], [6, 10, 12, 10, 11, 13, "st_galv"],
            [4.7, 6, 1.4, 6.2, 16, 2.4, "home_plastic_black"]]


def lt_cap_front():
    """On top at the tow end: the exhaust stack with its rain flap, the radiator cap."""
    flap = turn("x", 22.5, (12.25, 13.6, 7.2))
    return [[11.6, 7, 6, 12.9, 13.6, 7.3, DARK], [11, 7, 5.4, 13.5, 8, 7.9, DARK],
            [11.5, 13.6, 5.9, 13, 13.9, 7.4, DARK, None, flap],
            [3, 7, 12, 5, 7.8, 14, "st_galv"], [3.4, 7.8, 12.4, 4.6, 8.4, 13.6, DARK]]


def lt_mast():
    """A telescoping section: the tube, the collar where the next one slides in, the cable coil."""
    b = [[6.6, 0, 0.6, 9.4, 16, 3.4, "st_galv"], [6.1, 0, 0.1, 9.9, 1.4, 3.9, "st_galv"],
         [6.4, 1.4, 0.4, 9.6, 1.8, 3.6, DARK]]
    for i, y in enumerate((2, 5.2, 8.4, 11.6, 14.8)):
        b.append([5.0 if i % 2 else 5.4, y, 1.4, 6.6, y + 1.1, 2.6, "home_plastic_black"])
    return b


def lt_head(lit):
    """The lamp frame on the mast's top: the crossbar, four heads two by two angled down, the
    junction box between them and the cable going in."""
    b = [[6.25, 0, 0.3, 9.75, 3, 3.7, "st_galv"], [-3.4, 6, 0.8, 19.4, 7.6, 2.8, "st_galv"],
         [-3.4, 0, 0.8, -1.8, 14.6, 2.8, "st_galv"], [17.8, 0, 0.8, 19.4, 14.6, 2.8, "st_galv"],
         [-3.4, 13.4, 0.8, 19.4, 14.6, 2.8, "st_galv"],
         [6, 7.6, 0, 10, 12.4, 3.6, DARK], [6.6, 12.4, 0.4, 9.4, 13.4, 3.2, "st_galv"]]
    for x0 in (-1.2, 10.2):
        for y0 in (0.2, 7.8):
            b += lamp(y0, lit, z0=-6.2, x0=x0, w=7, h=5.4, tilt=-22.5)
    return b


# ---------------------------------------------------------------- the gate booth

GB, GBI, GBT = "cp_gb_clad", "cp_gb_inner", "cp_gb_trim"


def gb_panel(corner):
    """The waist-high wall on the model's north (and west, for a corner): cladding outside, the
    painted lining in, on a concrete plinth with the kick plate along its foot."""
    f = {"north": GB, "south": GBI, "east": GB, "west": GB, "up": GBT, "down": GBT}
    b = [[0, 0, -1, 16, 2, 2.5, "concrete_panel"], [0, 2, 0, 16, 16, 2, f], [0, 2, -0.4, 16, 3.2, 0, GBT]]
    if corner:
        fw = {"west": GB, "east": GBI, "north": GB, "south": GB, "up": GBT, "down": GBT}
        b += [[-1, 0, 0, 2.5, 2, 16, "concrete_panel"], [0, 2, 0, 2, 16, 16, fw], [-0.4, 2, 0, 0, 3.2, 16, GBT],
              [-0.8, 2, -0.8, 1.6, 16, 1.6, GBT]]
    return b


def gb_window(corner, broken):
    """The glazing above the waist: the aluminium frame, a mullion, the pane, the sill outside and
    the ledge inside the guard writes on."""
    glass = "cc_glass_broken" if broken else "cc_glass"
    b = [[0, 0, -1.2, 16, 1, 2, GBT], [0, 0, 0, 16, 1.4, 2, GBT], [0, 14.6, 0, 16, 16, 2, GBT],
         [7.4, 1.4, 0, 8.6, 14.6, 2, GBT], [15.2, 1.4, 0, 16, 14.6, 2, GBT],
         [0, 1.4, 0.8, 16, 14.6, 1.2, glass],
         [0, 0, 2, 16, 0.8, 6, "desk_laminate"]]
    if corner:
        b += [[-1.2, 0, 0, 0, 1, 16, GBT], [0, 0, 0, 2, 1.4, 16, GBT], [0, 14.6, 0, 2, 16, 16, GBT],
              [0, 1.4, 7.4, 2, 14.6, 8.6, GBT], [0, 1.4, 15.2, 2, 14.6, 16, GBT],
              [0.8, 1.4, 0, 1.2, 14.6, 16, glass], [0, 1.4, 0, 1.6, 14.6, 1.6, GBT],
              [2, 0, 6, 6, 0.8, 16, "desk_laminate"]]
    return b


def gb_head(corner):
    """The band over the windows and the roof on it, reaching 6 over the side with its fascia, the
    drip edge and the soffit under it."""
    f = {"north": GB, "south": GBI, "east": GB, "west": GB, "up": GBT, "down": GBT}
    x0 = -6 if corner else 0
    b = [[0, 0, 0, 16, 12.6, 2, f],
         [x0, 12.6, -6, 16, 14.6, 16, faces("cp_gb_roof", north=GBT, up="cp_gb_roof")],
         [x0 - 0.6, 12, -6.6, 16, 15.2, -6, GBT],
         [x0, 12.4, -6, 16, 12.6, 0, "ext_soffit"]]
    if corner:
        fw = {"west": GB, "east": GBI, "north": GB, "south": GB, "up": GBT, "down": GBT}
        b += [[0, 0, 0, 2, 12.6, 16, fw], [-6.6, 12, -6.6, -6, 15.2, 16, GBT], [-6, 12.4, 0, 0, 12.6, 16, "ext_soffit"],
              [-0.8, 0, -0.8, 1.6, 12.6, 1.6, GBT]]
    return b


def gb_roof_mid():
    """The middle of the roof: the ceiling under it with the strip light, the beacon on top."""
    return [[0, 12.6, 0, 16, 14.6, 16, faces("cp_gb_roof", up="cp_gb_roof")],
            [0, 12.4, 0, 16, 12.6, 16, GBI],
            [3, 11.4, 7, 13, 12.4, 9, faces(GBT, up=GBT, north=GBT)], [3.4, 11.2, 7.3, 12.6, 11.4, 8.7, "lamp_glass"],
            [6.5, 14.6, 6.5, 9.5, 15.4, 9.5, GBT], [6.8, 15.4, 6.8, 9.2, 17.8, 9.2, "trade_glass_amber"],
            [7.2, 17.8, 7.2, 8.8, 18.4, 8.8, GBT], [11, 14.6, 2, 13, 16.6, 4, "st_galv"]]


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
    add("cp_gb_wall", gb_panel(False), "Booth Wall", "METAL", shape=[[0, 0, 0, 16, 16, 2]])
    add("cp_gb_wall_corner", gb_panel(True), "Booth Wall", "METAL", shape=[[0, 0, 0, 16, 16, 2], [0, 0, 0, 2, 16, 16]])
    for v, broken in (("", False), ("_broken", True)):
        add("cp_gb_win" + v, gb_window(False, broken), "Booth Window", "GLASS", shape=[[0, 0, 0, 16, 16, 2]])
        add("cp_gb_win_corner" + v, gb_window(True, broken), "Booth Window", "GLASS",
            shape=[[0, 0, 0, 16, 16, 2], [0, 0, 0, 2, 16, 16]])
    add("cp_gb_head", gb_head(False), "Booth Roof", "METAL", shape=[[0, 0, 0, 16, 12.6, 2], [0, 12.6, 0, 16, 14.6, 16]])
    add("cp_gb_head_corner", gb_head(True), "Booth Roof", "METAL",
        shape=[[0, 0, 0, 16, 12.6, 2], [0, 0, 0, 2, 12.6, 16], [0, 12.6, 0, 16, 14.6, 16]])
    add("cp_gb_roof", gb_roof_mid(), "Booth Roof", "METAL", shape=[[0, 12.6, 0, 16, 14.6, 16]])
    for c in LT:
        add("cp_lt_rear_" + c, lt_rear(c), "Light Tower", "METAL", shape=[[1, 0, 4, 15, 16, 16]])
        add("cp_lt_front_" + c, lt_front(c), "Light Tower", "METAL", shape=[[1, 0, 0, 15, 16, 16]],
            loot="basement_supplies")
    add("cp_lt_cap_rear", lt_cap_rear(), "Light Tower", "METAL", shape=[[1, 0, 4, 15, 7, 16], [6.25, 0, 0.3, 9.75, 16, 3.7]])
    add("cp_lt_cap_front", lt_cap_front(), "Light Tower", "METAL", shape=[[1, 0, 0, 15, 7, 16]])
    add("cp_lt_mast", lt_mast(), "Light Tower Mast", "METAL", shape=[[6.1, 0, 0.1, 9.9, 16, 3.9]])
    for lit in (False, True):
        add("cp_lt_head" + ("_lit" if lit else ""), lt_head(lit), "Light Tower Lamps", "METAL",
            shape=[[6.25, 0, 0.3, 9.75, 3, 3.7]], light=15 if lit else None)
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
