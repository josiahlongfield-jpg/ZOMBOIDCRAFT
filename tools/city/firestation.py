#!/usr/bin/env python3
"""The fire station's kit. There are no fire station photos in the library yet, so this follows
what small-town American stations of 1915 to 1960 are known for (and what was added by 1993): a
brick front of apparatus bays under a stone band with the station's name, the company numbers in
roundels, the Maltese cross in the parapet; red sectional doors with a row of windows (one rammed
open, one hanging up); the red lamps either side of the house door, the call box at the curb, the
hydrant, the old alarm bell on its plinth; KEEP CLEAR on the apron. Inside: the apparatus floor,
grey and stained, the stop line, turnout gear hung ready on its racks (coat and helmet over the
pants pulled down round the boots), air bottles, hose racked in rolls, the tool board, the brass
pole coming down through its hole onto the rubber mat; the watch desk with the radio, the alarm
board and the tape register, the chalkboard with the last calls; upstairs the bunks; the hose
tower with the hose hung in it to dry and the siren on top.

- fs_frieze_{a,b}_11_*: FIRE STATION No. 1, CITY FIRE DEPARTMENT; fs_company_{1,2}_{0,1} (ENGINE 1, 2), fs_cross;
- fs_door_{red,white}_0..3 (bottom to top), fs_door_red_dent, fs_door_open_{red,white};
- fs_floor_bay, fs_floor_stop, fs_apron, fs_apron_text_0..3 (KEEP CLEAR);
- fs_gear_lo, fs_gear_hi_{black,tan}, fs_gear_hi_empty, fs_locker(_top) (loot fire);
- fs_pole, fs_pole_mat, fs_pole_hole, fs_pole_cage;
- fs_watch_desk_a (radio, lit or not), fs_watch_desk_b (tape register, log), fs_alarm_board(_lit),
  fs_board_{a,b}_0..3 (chalkboards, two by two), fs_tools_{a,b}, fs_hose_rack, fs_hose_hung(_end), fs_scba_rack;
- fs_bed_head, fs_bed_foot; fs_siren, fs_lamp_red(_lit), fs_callbox(_post), fs_hydrant, fs_plinth,
  fs_louvre, plaques.

    python3 tools/city/firestation.py        rewrites the fs_ entries in blocks.json

photo_textures.py draws the textures (firestation.register()); world/county/FireStation.java builds
with them. The base model faces north: anything on a wall has its back at z = 16 against the wall.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
from furniture import area, disc, edges, text_mask  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
HAND = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
STONE = "stone_trim"
NAMES = {"a": "FIRE STATION No. 1", "b": "CITY FIRE DEPARTMENT"}
FW = 11
BOARDS = {
    "a": "SHIFT  B|CAPT. HOLT|ENG 1   IN SVC|ENG 2   OUT|RESCUE  OUT|HYD 4TH+ELM DOWN",
    "b": "10-26  03:40|ALL UNITS|COMMITTED|EVAC CTR AT|HIGH SCHOOL|DO NOT WAIT",
}
PLAQUES = {"chief": "CHIEF", "watch": "WATCH|ROOM", "captain": "CAPTAIN", "quarters": "QUARTERS"}
RED = (150, 30, 26)
BRASS = (186, 148, 72)


# ---------------------------------------------------------------- the front

def frieze(key, i):
    """The station's name cut in the stone band over the bays, one tile per block."""
    t = NS.build(STONE)
    t.rng = np.random.default_rng(9900 + i)
    m = text_mask(NAMES[key], SERIF, 26, N * FW - 40, 32, seed=FW, spacing=1.15)
    full = np.zeros((N, N * FW), np.float32)
    h, w = m.shape
    full[16:16 + h, 20:20 + w] = m
    cut = full[:, i * N:(i + 1) * N] > 0.4
    t.c[cut] *= 0.4
    t.h[cut] = 0.5
    area(t, 0, 0, N, 3, mul=1.1, dh=0.1)
    area(t, 0, N - 3, N, N, mul=0.75, dh=-0.1)
    t.streaks(0.7, 0.75)
    return t


def tablet(n, i):
    """The stone tablet over a bay, two blocks wide, ENGINE and the company's number cut in it
    (tile 0 the viewer's left half)."""
    t = NS.build(STONE)
    t.rng = np.random.default_rng(9920 + n * 2 + i)
    text = f"ENGINE {n}"
    m = text_mask(text, SERIF, civic.fit(text, SERIF, 22, N * 2 - 16), N * 2 - 16, 30, seed=n)
    full = np.zeros((N, N * 2), np.float32)
    full[18:48, 8:8 + m.shape[1]] = m
    cut = full[:, i * N:(i + 1) * N] > 0.4
    t.c[cut] *= 0.42
    t.h[cut] = 0.5
    edges(t, 0 if i == 0 else -4, 4, N + 4 if i == 0 else N, N - 4, w=3, light=1.15, dark=0.7)
    t.streaks(0.6, 0.8)
    return t


def cross(seed):
    """The Maltese cross: red, edged in gold, the department's initials in the middle disc."""
    t = F.plain(seed, (160, 32, 28), 0.06, 0.5)
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    u, v = np.abs(xx - 31.5), np.abs(yy - 31.5)
    a, b = np.maximum(u, v), np.minimum(u, v)
    arm = (a < 30) & (b < 4 + a * 0.42) & ~((a > 22) & (b < (a - 22) * 0.9))
    t.alpha[arm] = 255
    edge = arm & ~((a < 28) & (b < 2.5 + a * 0.38) & ~((a > 20.5) & (b < (a - 20.5) * 0.9 + 1.5)))
    t.c[edge] = (206, 168, 80)
    t.f0[edge] = 220
    r = np.hypot(xx - 31.5, yy - 31.5)
    t.c[r < 11] = (206, 168, 80)
    t.c[r < 9] = (232, 226, 210)
    m = text_mask("FD", SERIF, 11, 18, 14, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 23, 25, (140, 28, 24), h=0.9)
    t.grime(0.3)
    return t


def bay_door(seed, colour, row, dent=False):
    """One section of a sectional overhead door, a block tall: two raised panels to the block, the
    third section up glazed; red or white paint gone chalky, rust at the joints, dirt splashed up."""
    base = RED if colour == "red" else (214, 210, 200)
    t = F.plain(seed, base, 0.06, 0.45)
    t.c *= (1 + 0.05 * P.fbm(t.rng, ((2, 1.0), (4, 0.5))))[..., None]
    area(t, 0, 0, N, 2, mul=0.35, dh=-0.3)
    for x0 in (0, 32):
        if row == 2:
            area(t, x0 + 3, 9, x0 + 29, 55, (24, 26, 30), h=0.4, smooth=0.9, f0=40)
            area(t, x0 + 5, 11, x0 + 15, 53, mul=1.35)
            edges(t, x0 + 3, 9, x0 + 29, 55, w=2, light=0.7, dark=1.2)
        else:
            edges(t, x0 + 4, 10, x0 + 28, 54, w=2)
            area(t, x0 + 6, 12, x0 + 26, 52, dh=0.12)
    area(t, 31, 2, 33, N, mul=0.7)
    if row == 0:
        area(t, 0, N - 3, N, N, (30, 30, 30), h=0.6)
        t.mix(np.clip(np.linspace(-1, 1, N)[:, None] * np.ones((1, N)) + P.fbm(t.rng) * 0.5, 0, 1), (70, 58, 44), 0.6)
        area(t, 26, 30, 38, 34, (40, 40, 40), h=0.9)
    if row == 3:
        for x in (6, 58):
            disc(t, x, 32, 2, (60, 60, 60))
    t.speckle(0.02, (110, 70, 40), 0.6)
    if dent:
        yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
        d = np.hypot((xx - 30) / 1.3, yy - 40)
        t.h -= np.clip(1 - d / 26, 0, 1) * 0.5
        t.c *= (1 - 0.35 * np.clip(1 - d / 26, 0, 1))[..., None]
        for k in range(5):
            t.c[(np.abs(d - 6 - k * 4.5) < 0.8)] *= 0.6
        t.speckle(0.05, (150, 150, 150), 0.7)
    t.grime(0.25)
    return t


def door_under(seed, colour):
    """The door run up on its tracks, seen from underneath: the backs of its sections and the struts."""
    base = (120, 30, 26) if colour == "red" else (186, 182, 172)
    t = F.plain(seed, base, 0.05, 0.3)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=0.4)
    for x in (8, 54):
        area(t, x, 0, x + 3, N, (150, 150, 150), h=0.9, f0=180)
    t.grime(0.3)
    return t


def louvre(seed):
    """The hose tower's louvres: weathered grey slats with the dark showing between."""
    t = F.plain(seed, (118, 114, 104), 0.08, 0.2)
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 2, (20, 20, 20), h=0.2)
        area(t, 0, y + 2, N, y + 3, mul=1.2)
    t.frame(3, (90, 86, 78))
    t.streaks(0.7, 0.7)
    return t


# ---------------------------------------------------------------- the floors

def bay_floor(seed, stop=False):
    """The apparatus floor: sealed concrete gone dull, tyre tracks, oil under where the engines stood."""
    t = NS.build("floor_concrete")
    t.rng = np.random.default_rng(seed)
    t.c *= 0.92
    t.smooth[:] = 0.45
    for x in (14, 46):
        t.c[:, x - 4:x + 4] *= (0.86 + 0.05 * P.fbm(t.rng, ((4, 1.0), (8, 0.5))))[:, x - 4:x + 4, None]
    for _ in range(2):
        F.stain(t, *t.rng.uniform(8, 56, 2), t.rng.uniform(5, 10), col=(26, 24, 22), amount=0.6)
    if stop:
        area(t, 0, 26, N, 38, (206, 168, 40), smooth=0.4)
        t.speckle(0.08, (120, 120, 116), 0.7)
    t.grime(0.2)
    return t


def apron(seed, text_i=None):
    """The concrete apron in front of the bays, its joints, and KEEP CLEAR stencilled across four slabs."""
    t = NS.build("floor_concrete")
    t.rng = np.random.default_rng(seed)
    area(t, 0, 0, N, 1, mul=0.55, dh=-0.3)
    area(t, 0, 0, 1, N, mul=0.55, dh=-0.3)
    if text_i is not None:
        m = text_mask("KEEP CLEAR", SANS, civic.fit("KEEP CLEAR", SANS, 44, N * 4 - 16), N * 4 - 16, 52, seed=seed)
        full = np.zeros((N, N * 4), np.float32)
        full[6:58, 8:8 + m.shape[1]] = m
        cut = full[:, text_i * N:(text_i + 1) * N]
        cut = cut * np.clip(0.75 + P.fbm(t.rng) * 0.6, 0, 1)
        t.mix(cut > 0.4, (214, 176, 44), 0.9)
    t.speckle(0.03, (60, 58, 54), 0.5)
    t.grime(0.3)
    return t


# ---------------------------------------------------------------- the gear

def coat(seed, colour):
    """A turnout coat on its hook, seen from the front: the collar up, the storm flap, the reflective
    bands round the chest and hem and the cuffs, a radio pocket."""
    base = (34, 32, 30) if colour == "black" else (150, 128, 84)
    t = F.plain(seed, base, 0.08, 0.15)
    t.c *= (1 + 0.05 * np.sin(np.arange(N, dtype=np.float32) * 0.9))[None, :, None]
    area(t, 22, 0, 42, 6, mul=0.7)
    area(t, 30, 6, 34, N, mul=0.7, dh=-0.1)
    for y0 in (28, 52):
        area(t, 0, y0, N, y0 + 5, (200, 196, 120), smooth=0.6, f0=60)
        area(t, 0, y0 + 1, N, y0 + 4, (180, 182, 176), smooth=0.7, f0=90)
    area(t, 8, 12, 20, 22, mul=0.85)
    edges(t, 8, 12, 20, 22, w=1)
    area(t, 44, 36, 56, 48, mul=0.85)
    t.grime(0.45, (60, 50, 36))
    return t


def helmet(seed, colour):
    """A helmet's crown: black, yellow or the captain's white, scuffed, a little soot."""
    base = {"black": (28, 28, 28), "yellow": (206, 170, 40), "white": (220, 218, 208)}[colour]
    t = F.plain(seed, base, 0.06, 0.7)
    t.f0[:] = 60
    t.speckle(0.04, (90, 90, 90), 0.5)
    t.grime(0.25)
    return t


def shield(seed):
    """The helmet's leather front piece: a black shield with the company number."""
    t = helmet(seed, "black")
    disc(t, 32, 32, 20, (170, 140, 76), f0=200)
    disc(t, 32, 32, 16, (24, 22, 20))
    m = text_mask("1", SERIF, 20, 20, 24, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 22, 20, (220, 214, 196), h=0.9)
    return t


def pants(seed):
    """Bunker pants pulled down round the boots, ready to step into: folds, bands, rubber boots."""
    t = F.plain(seed, (40, 38, 34), 0.08, 0.15)
    for y in range(4, 34, 6):
        area(t, 0, y, N, y + 2, mul=0.7, dh=-0.1)
    area(t, 0, 30, N, 34, (190, 190, 182), smooth=0.7, f0=90)
    for x0 in (4, 34):
        area(t, x0, 36, x0 + 26, N, (22, 22, 22), smooth=0.5)
        area(t, x0, N - 8, x0 + 26, N - 4, (200, 168, 40))
    t.grime(0.4, (70, 60, 44))
    return t


def tools(seed, missing):
    """The tool board: a flat-head axe, a pike pole, the halligan, bolt cutters on pegboard; where a
    tool went out on the last call its outline is painted on the board."""
    t = F.plain(seed, (120, 110, 90), 0.05, 0.2)
    for y in range(3, N, 6):
        for x in range(3, N, 6):
            t.c[y, x] = (40, 36, 30)
    rng = t.rng
    items = [
        ("axe", [(6, 6, 9, 58, (150, 104, 60)), (2, 6, 14, 18, (160, 32, 28)), (2, 6, 4, 12, (180, 180, 176))]),
        ("pike", [(20, 2, 22, 62, (136, 96, 56)), (18, 2, 24, 8, (120, 120, 120))]),
        ("halligan", [(32, 8, 35, 56, (150, 152, 150)), (28, 6, 39, 10, (150, 152, 150)), (30, 52, 38, 56, (150, 152, 150))]),
        ("cutters", [(46, 6, 49, 40, (170, 30, 26)), (52, 6, 55, 40, (170, 30, 26)), (46, 40, 55, 52, (120, 120, 120))]),
    ]
    for name, parts in items:
        gone = missing and rng.random() < 0.6
        for x0, y0, x1, y1, col in parts:
            if gone:
                area(t, x0, y0, x1, y0 + 1, (230, 230, 230))
                area(t, x0, y1 - 1, x1, y1, (230, 230, 230))
                area(t, x0, y0, x0 + 1, y1, (230, 230, 230))
                area(t, x1 - 1, y0, x1, y1, (230, 230, 230))
            else:
                area(t, x0, y0, x1, y1, col, h=0.95, smooth=0.4)
                area(t, x1 - 1, y0, x1, y1, mul=0.7)
    t.grime(0.3)
    return t


def hose_coil(seed):
    """The end of a rolled length of hose: the canvas jacket spiralled round, the coupling at the centre."""
    t = F.plain(seed, (204, 196, 170), 0.05, 0.2)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - 31.5, yy - 31.5)
    a = np.arctan2(yy - 31.5, xx - 31.5)
    spiral = np.sin((r + a / (2 * np.pi) * 4.5) * (2 * np.pi / 4.5))
    t.c *= (0.85 + 0.15 * spiral)[..., None]
    t.c[r > 31] *= 0.6
    disc(t, 32, 32, 6, BRASS, f0=210, smooth=0.6)
    disc(t, 32, 32, 3, (40, 36, 30))
    t.grime(0.35)
    return t


def canvas(seed, colour=(204, 196, 170)):
    """Woven hose jacket."""
    t = F.plain(seed, colour, 0.05, 0.15)
    x = np.arange(N, dtype=np.float32)
    t.c *= (1 + 0.05 * np.sin(x * 2.4))[None, :, None] * (1 + 0.05 * np.sin(x * 2.4))[:, None, None]
    t.grime(0.4)
    return t


def cylinder(seed):
    """An air bottle: painted yellow, the label band, scuffs."""
    t = F.plain(seed, (200, 170, 44), 0.06, 0.55)
    t.f0[:] = 50
    area(t, 0, 24, N, 40, (220, 218, 210), smooth=0.4)
    m = text_mask("AIR", SANS, 9, 30, 12, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 17, 26, (30, 30, 30))
    t.speckle(0.04, (90, 90, 90), 0.6)
    t.grime(0.25)
    return t


def blanket(seed):
    """A navy wool blanket with a red stripe, folded square at the foot of the bunk."""
    t = F.plain(seed, (40, 46, 70), 0.06, 0.1)
    area(t, 0, 50, N, 54, (150, 36, 32))
    t.speckle(0.05, (70, 76, 100), 0.4)
    t.grime(0.2)
    return t


# ---------------------------------------------------------------- the watch desk

def alarm_board(seed, lit):
    """The box alarm board: a dark wood panel, rows of brass box numbers, an indicator lamp at each;
    lit: the last boxes pulled still showing red."""
    t = NS.build("cv_oak")
    t.rng = np.random.default_rng(seed)
    t.c *= 0.55
    edges(t, 1, 1, N - 1, N - 1, w=2)
    m = text_mask("ALARM BOXES", SANS, 6, 50, 8, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 7, 3, (210, 190, 140))
    rng = t.rng
    for j in range(5):
        for i in range(4):
            x, y = 6 + i * 14, 14 + j * 10
            area(t, x, y, x + 8, y + 5, BRASS, f0=200, smooth=0.6, h=0.9)
            m = text_mask(str(11 + j * 4 + i), SANS, 4, 8, 5, seed=seed + i + j)
            F.paste(t, (m > 0.4).astype(np.float32), x, y, (40, 30, 20))
            on = lit and rng.random() < 0.3
            disc(t, x + 11, y + 2.5, 1.6, (230, 40, 30) if on else (70, 24, 22), smooth=0.9)
            if on:
                t.emit[int(y + 1):int(y + 5), int(x + 9):int(x + 13)] = 1.0
    t.grime(0.25)
    return t


def radio(seed, lit):
    """The base station on the watch desk: grey case, a channel display (still green on battery in
    some), the speaker grille, the knobs, the hand mic on its hook."""
    t = F.plain(seed, (96, 98, 100), 0.04, 0.4)
    t.f0[:] = 80
    area(t, 4, 6, 30, 16, (10, 12, 10), smooth=0.9)
    if lit:
        m = text_mask("CH 1  FD", SANS, 6, 24, 8, seed=seed)
        F.paste(t, (m > 0.4).astype(np.float32), 5, 7, (90, 250, 120))
        t.emit[6:16, 4:30] = 0.7
    for y in range(22, 50, 3):
        area(t, 4, y, 30, y + 1, (30, 30, 30))
    for x in (38, 48):
        disc(t, x, 14, 3.5, (30, 30, 30), h=0.95)
    area(t, 36, 26, 58, 30, (30, 30, 30))
    area(t, 40, 34, 52, 54, (24, 24, 24), h=0.95)
    disc(t, 46, 40, 2, (60, 60, 60))
    area(t, 0, N - 4, N, N, mul=0.6)
    t.grime(0.25)
    return t


def register_front(seed):
    """The tape register: a brass works under its glass, the paper tape run out across the desk."""
    t = F.plain(seed, (40, 34, 26), 0.06, 0.4)
    area(t, 4, 6, 60, 44, BRASS, f0=210, smooth=0.65, h=0.9)
    for x in (16, 32, 48):
        disc(t, x, 22, 7, mul=0.75, ring=2)
        disc(t, x, 22, 2, (30, 26, 20))
    area(t, 6, 40, 58, 44, (40, 30, 20))
    area(t, 0, 48, N, 58, (230, 226, 210))
    for x in range(4, N, 5):
        disc(t, x, 53, 1, (30, 30, 30))
    t.grime(0.3)
    return t


def log_top(seed):
    """The desk top beside the radio: the run log open, a phone, a coffee mug ring, a pen."""
    t = NS.build("desk_laminate")
    t.rng = np.random.default_rng(seed)
    area(t, 6, 8, 40, 48, (232, 228, 214), smooth=0.2)
    area(t, 22, 8, 24, 48, mul=0.8)
    for y in range(14, 46, 4):
        area(t, 9, y, 21, y + 1, (60, 60, 90))
        area(t, 26, y, 38, y + 1, (60, 60, 90))
    area(t, 46, 10, 60, 30, (40, 40, 40), smooth=0.6)
    area(t, 44, 12, 62, 16, (30, 30, 30))
    disc(t, 52, 46, 5, mul=0.7, ring=1)
    area(t, 30, 52, 50, 54, (30, 40, 140))
    t.grime(0.25)
    return t


def chalkboard(key, i):
    """The chalkboard by the watch desk, two blocks by two (tile i: 0, 1 along the top, 2, 3 under):
    the shift and what was out, or the last thing written on it."""
    t = F.plain(9960 + i + (10 if key == "b" else 0), (34, 46, 40), 0.05, 0.2)
    text = BOARDS[key]
    m = text_mask(text, HAND, civic.fit(text, HAND, 16, N * 2 - 16), N * 2 - 16, N * 2 - 16, seed=7 if key == "a" else 8,
                  jitter=0.3, align="left")
    full = np.zeros((N * 2, N * 2), np.float32)
    full[8:8 + m.shape[0], 8:8 + m.shape[1]] = m
    cx, cy = i % 2, i // 2
    cut = full[cy * N:(cy + 1) * N, cx * N:(cx + 1) * N]
    t.mix(np.clip(cut * 1.2, 0, 1), (220, 220, 210), 0.9)
    t.mix(np.clip(P.fbm(t.rng) * 0.8, 0, 1), (90, 100, 94), 0.3)
    wood = (110, 80, 50)
    area(t, 0, 0, 3, N, wood) if cx == 0 else area(t, N - 3, 0, N, N, wood)
    area(t, 0, 0, N, 3, wood) if cy == 0 else area(t, 0, N - 5, N, N, wood)
    if cy == 1:
        area(t, 0, N - 8, N, N - 5, mul=0.8)
        for x in (14, 30, 44):
            area(t, x, N - 8, x + 6, N - 6, (230, 230, 220))
    return t


# ---------------------------------------------------------------- outside

def red_glass(seed, lit):
    """The red globe of the lamp by the house door."""
    t = F.plain(seed, (190, 30, 24) if lit else (110, 24, 20), 0.05, 0.85)
    t.f0[:] = 40
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    t.c *= (1 + 0.25 * np.exp(-((xx - 22) ** 2 + (yy - 20) ** 2) / 150))[..., None]
    if lit:
        t.emit[:] = 0.9
    t.grime(0.2)
    return t


def callbox_front(seed):
    """The street fire alarm box: red cast iron, FIRE ALARM raised on it, the little glass door and
    the hook to pull down inside."""
    t = F.plain(seed, (160, 30, 26), 0.06, 0.45)
    edges(t, 2, 2, N - 2, N - 2, w=2)
    m = text_mask("FIRE|ALARM", SANS, 9, 48, 22, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 8, 4, (226, 210, 170), h=0.95)
    area(t, 16, 30, 48, 56, (30, 30, 34), smooth=0.9, f0=40)
    area(t, 28, 34, 36, 48, (200, 190, 150), h=0.95)
    m = text_mask("PULL", SANS, 5, 20, 6, seed=seed + 1)
    F.paste(t, (m > 0.4).astype(np.float32), 22, 50, (226, 210, 170))
    t.speckle(0.03, (100, 60, 40), 0.6)
    t.grime(0.3)
    return t


def hydrant(seed):
    """Hydrant paint: yellow, flaking to the red undercoat and the rust under that."""
    t = F.plain(seed, (206, 172, 40), 0.07, 0.45)
    t.speckle(0.05, (150, 40, 30), 0.8)
    t.speckle(0.03, (110, 70, 40), 0.8)
    t.grime(0.3)
    return t


def memorial(seed):
    """The stone plinth under the old alarm bell, its bronze plate."""
    t = NS.build(STONE)
    t.rng = np.random.default_rng(seed)
    area(t, 8, 10, 56, 52, (56, 62, 46), f0=120, smooth=0.5, h=0.9)
    edges(t, 8, 10, 56, 52, w=2, light=1.6, dark=0.8)
    text = "IN|MEMORIAM"
    m = text_mask(text, SERIF, civic.fit(text, SERIF, 14, 46), 46, 38, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 9, 12, (206, 172, 100), h=0.95)
    t.streaks(0.6, 0.75)
    return t


def siren(seed):
    """The siren's housing: galvanised steel gone dull, the slots of the horns, bird mess."""
    t = F.plain(seed, (150, 152, 148), 0.06, 0.4)
    t.f0[:] = 140
    for x in range(4, N, 10):
        area(t, x, 14, x + 6, 50, (24, 24, 24), h=0.3)
    t.speckle(0.03, (230, 230, 226), 0.8)
    t.streaks(0.8, 0.7)
    return t


def textures():
    T = {}
    for key in NAMES:
        for i in range(FW):
            T[f"fs_frieze_{key}_{FW}_{i}"] = lambda key=key, i=i: frieze(key, i)
    for n in (1, 2):
        for i in range(2):
            T[f"fs_company_{n}_{i}"] = lambda n=n, i=i: tablet(n, i)
    T["fs_cross"] = lambda: cross(9923)
    for c, colour in enumerate(("red", "white")):
        for row in range(4):
            T[f"fs_door_{colour}_{row}"] = lambda colour=colour, row=row, c=c: bay_door(9924 + c * 4 + row, colour, row)
        T[f"fs_door_under_{colour}"] = lambda colour=colour, c=c: door_under(9932 + c, colour)
    T["fs_door_red_dent"] = lambda: bay_door(9934, "red", 0, dent=True)
    T["fs_louvre"] = lambda: louvre(9935)
    T["fs_floor_bay"] = lambda: bay_floor(9936)
    T["fs_floor_stop"] = lambda: bay_floor(9937, stop=True)
    T["fs_apron"] = lambda: apron(9938)
    for i in range(4):
        T[f"fs_apron_text_{i}"] = lambda i=i: apron(9939, i)
    T["fs_coat_black"] = lambda: coat(9943, "black")
    T["fs_coat_tan"] = lambda: coat(9944, "tan")
    for k, colour in enumerate(("black", "yellow", "white")):
        T[f"fs_helmet_{colour}"] = lambda colour=colour, k=k: helmet(9945 + k, colour)
    T["fs_shield"] = lambda: shield(9948)
    T["fs_pants"] = lambda: pants(9949)
    T["fs_tools_a"] = lambda: tools(9950, False)
    T["fs_tools_b"] = lambda: tools(9951, True)
    T["fs_hose_coil"] = lambda: hose_coil(9952)
    T["fs_canvas"] = lambda: canvas(9953)
    T["fs_canvas_wet"] = lambda: canvas(9954, (150, 144, 124))
    T["fs_cylinder"] = lambda: cylinder(9955)
    T["fs_blanket"] = lambda: blanket(9956)
    T["fs_brass"] = lambda: F.plain(9957, BRASS, 0.05, 0.75)
    T["fs_rubber"] = lambda: F.plain(9958, (26, 26, 26), 0.06, 0.2)
    T["fs_red"] = lambda: F.plain(9959, (160, 30, 26), 0.07, 0.45)
    for key in BOARDS:
        for i in range(4):
            T[f"fs_board_{key}_{i}"] = lambda key=key, i=i: chalkboard(key, i)
    T["fs_alarm_board"] = lambda: alarm_board(9970, False)
    T["fs_alarm_board_lit"] = lambda: alarm_board(9970, True)
    T["fs_radio"] = lambda: radio(9971, False)
    T["fs_radio_lit"] = lambda: radio(9971, True)
    T["fs_register"] = lambda: register_front(9972)
    T["fs_log_top"] = lambda: log_top(9973)
    T["fs_red_glass"] = lambda: red_glass(9974, False)
    T["fs_red_glass_lit"] = lambda: red_glass(9974, True)
    T["fs_callbox"] = lambda: callbox_front(9975)
    T["fs_hydrant"] = lambda: hydrant(9976)
    T["fs_memorial"] = lambda: memorial(9977)
    T["fs_siren"] = lambda: siren(9978)
    for k, (name, text) in enumerate(PLAQUES.items()):
        T[f"fs_plaque_{name}"] = lambda text=text, k=k: civic.plaque(9980 + k, text)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
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

    iron, brass, red = "cv_iron", "fs_brass", "fs_red"
    for key in NAMES:
        for i in range(FW):
            add(f"fs_frieze_{key}_{FW}_{i}", None, "Name Band", "STONE",
                tex={"north": f"fs_frieze_{key}_{FW}_{i}", "side": STONE, "up": STONE, "down": STONE})
    for n in (1, 2):
        for i in range(2):
            add(f"fs_company_{n}_{i}", [[0, 0, 14, 16, 16, 16, {"north": f"fs_company_{n}_{i}", "side": STONE}]], "Company Tablet", "STONE",
                decor=True)
    add("fs_cross", [[0, 0, 15, 16, 16, 16, {"north": "fs_cross", "side": "fs_red"}]], "Maltese Cross", "METAL", decor=True)

    # the bay doors, a section a block, bottom (0) to top (3); run up on their tracks, a slab under the head
    for colour in ("red", "white"):
        for row in range(4):
            add(f"fs_door_{colour}_{row}", None, "Bay Door", "METAL",
                tex={"north": f"fs_door_{colour}_{row}", "south": f"fs_door_{colour}_{row}", "side": "streetlight_metal"})
        add(f"fs_door_open_{colour}", [[0, 12, 0, 16, 16, 16, {"down": f"fs_door_under_{colour}", "up": f"fs_door_under_{colour}",
                                                               "north": f"fs_door_{colour}_3", "south": f"fs_door_{colour}_3",
                                                               "side": "streetlight_metal"}]],
            "Bay Door (Open)", "METAL")
    add("fs_door_red_dent", None, "Bay Door", "METAL",
        tex={"north": "fs_door_red_dent", "south": "fs_door_red_0", "side": "streetlight_metal"})
    add("fs_louvre", None, "Louvres", "WOOD", tex={"north": "fs_louvre", "side": "fs_louvre", "up": STONE, "down": STONE})

    # the floors: the stop line and the apron's lettering turn with the building
    add("fs_floor_bay", None, "Apparatus Floor", "STONE", rotate=None, tex={"up": "fs_floor_bay", "side": "floor_concrete", "down": "floor_concrete"})
    add("fs_floor_stop", None, "Apparatus Floor", "STONE", tex={"up": "fs_floor_stop", "side": "floor_concrete", "down": "floor_concrete"})
    add("fs_apron", None, "Apron", "STONE", rotate=None, tex={"up": "fs_apron", "side": "floor_concrete", "down": "floor_concrete"})
    for i in range(4):
        add(f"fs_apron_text_{i}", None, "Apron", "STONE", tex={"up": f"fs_apron_text_{i}", "side": "floor_concrete", "down": "floor_concrete"})

    # the turnout gear racks: posts and shelves of steel; the coat and helmet over the pants and boots
    posts = [[0, 0, 13, 1, 16, 16, iron], [15, 0, 13, 16, 16, 16, iron]]
    add("fs_gear_lo", posts + [[1, 0, 9, 15, 1.5, 16, iron], [2, 1.5, 9, 14, 10, 14.5, {"north": "fs_pants", "side": "fs_pants", "up": "fs_pants"}]],
        "Turnout Gear", "WOOL", loot="fire")
    for colour, hcol in (("black", "black"), ("tan", "yellow")):
        add(f"fs_gear_hi_{colour}", posts + [
            [1, 11, 8, 15, 12, 16, iron],
            [3, 0, 10, 13, 10.5, 14.5, {"north": f"fs_coat_{colour}", "south": f"fs_coat_{colour}", "side": f"fs_coat_{colour}", "up": f"fs_coat_{colour}"}],
            [7, 10.5, 13, 9, 11, 15, iron],
            [4.5, 12, 9, 11.5, 15, 15, f"fs_helmet_{hcol}"],
            [3, 12, 8, 13, 12.8, 16, f"fs_helmet_{hcol}"],
            [6, 12.5, 7.6, 10, 15.5, 8, {"north": "fs_shield", "side": "fs_helmet_black"}]], "Turnout Gear", "WOOL", loot="fire")
    add("fs_gear_hi_empty", posts + [[1, 11, 8, 15, 12, 16, iron], [7, 8, 13, 9, 11, 15, iron]], "Gear Rack", "METAL")
    add("fs_locker", [[1, 0, 3, 15, 16, 16, {"north": "bunk_locker", "side": "bunk_grey", "up": "bunk_grey", "down": "bunk_grey"}]],
        "Locker", "METAL", loot="fire")
    add("fs_locker_top", [[1, 0, 3, 15, 14, 16, {"north": "bunk_locker_top", "side": "bunk_grey", "up": "bunk_grey", "down": "bunk_grey"}]],
        "Locker", "METAL")

    # the pole: down through the floor (its hole, the brass guard round it upstairs), onto the mat
    pole = [7, 0, 7, 9, 16, 9, brass]
    add("fs_pole", [pole], "Brass Pole", "METAL", rotate=None)
    add("fs_pole_mat", [[1, 0, 1, 15, 0.6, 15, {"up": "fs_rubber", "side": "fs_rubber", "down": "fs_rubber"}], pole], "Brass Pole", "METAL",
        rotate=None)
    boards = "floor_boards"
    add("fs_pole_hole", [[0, 13, 0, 16, 16, 4, boards], [0, 13, 12, 16, 16, 16, boards], [0, 13, 4, 4, 16, 12, boards],
                         [12, 13, 4, 16, 16, 12, boards], [4, 15.6, 4, 12, 16, 5, brass], [4, 15.6, 11, 12, 16, 12, brass],
                         [4, 15.6, 5, 5, 16, 11, brass], [11, 15.6, 5, 12, 16, 11, brass], pole], "Pole Hole", "WOOD", rotate=None)
    add("fs_pole_cage", [pole, [1, 0, 15, 2, 15, 16, brass], [14, 0, 15, 15, 15, 16, brass], [1, 13, 15, 15, 15, 16, brass],
                         [1, 6, 15, 15, 7, 16, brass], [1, 0, 1, 2, 15, 15, brass], [14, 0, 1, 15, 15, 15, brass],
                         [1, 13, 1, 2, 15, 15, brass], [14, 13, 1, 15, 15, 15, brass], [1, 6, 1, 2, 7, 15, brass], [14, 6, 1, 15, 7, 15, brass]],
        "Pole Guard", "METAL")

    # the watch desk: the radio end and the register end
    desk = [[0, 13, 0, 16, 14.5, 16, {"up": "desk_laminate", "side": "desk_laminate", "down": "desk_laminate"}],
            [0, 0, 1, 16, 13, 2, "desk_laminate"], [0, 0, 2, 1, 13, 16, "desk_laminate"], [15, 0, 2, 16, 13, 16, "desk_laminate"]]
    for lit in ("", "_lit"):
        add(f"fs_watch_desk_a{lit}", desk + [[2, 14.5, 7, 14, 19, 15, {"south": f"fs_radio{lit}", "north": "streetlight_metal",
                                                                         "side": "streetlight_metal", "up": "streetlight_metal"}],
                                            [12, 19, 10, 13, 23, 11, iron]], "Watch Desk", "WOOD", **({"light": 3} if lit else {}))
    add("fs_watch_desk_b", desk[:1] + [[0, 0, 1, 16, 13, 2, "desk_laminate"], [0, 0, 2, 1, 13, 16, "desk_laminate"], [15, 0, 2, 16, 13, 16, "desk_laminate"],
                                       [0, 14.5, 0, 16, 14.6, 16, {"up": "fs_log_top", "side": "desk_laminate"}],
                                       [3, 14.5, 3, 13, 20, 8, {"south": "fs_register", "north": "fs_brass", "side": "fs_brass", "up": "fs_brass"}]],
        "Watch Desk", "WOOD", loot="office")
    for lit in ("", "_lit"):
        add(f"fs_alarm_board{lit}", [[1, 0, 15, 15, 16, 16, {"north": f"fs_alarm_board{lit}", "side": "cv_oak"}]], "Alarm Board", "WOOD",
            decor=True, **({"light": 4} if lit else {}))
    for key in BOARDS:
        for i in range(4):
            add(f"fs_board_{key}_{i}", [[0, 1, 15, 16, 15, 16, {"north": f"fs_board_{key}_{i}", "side": "cv_oak"}]], "Chalkboard", "WOOD",
                decor=True)
    for k in ("a", "b"):
        add(f"fs_tools_{k}", [[0, 0, 15, 16, 16, 16, {"north": f"fs_tools_{k}", "side": "cv_oak"}]], "Tool Board", "WOOD", decor=True)

    # hose: racked in rolls; hung in the tower to dry, the couplings at the bottom
    coil = {"north": "fs_hose_coil", "south": "fs_hose_coil", "side": "fs_canvas", "up": "fs_canvas", "down": "fs_canvas"}
    add("fs_hose_rack", [[0, 0, 4, 1, 16, 16, iron], [15, 0, 4, 16, 16, 16, iron], [1, 0.5, 4, 15, 1.5, 16, iron], [1, 8, 4, 15, 9, 16, iron],
                         [1.5, 1.5, 6, 7.5, 7.5, 14, coil], [8.5, 1.5, 6, 14.5, 7.5, 14, coil], [1.5, 9, 6, 7.5, 15, 14, coil],
                         [8.5, 9, 6, 14.5, 15, 14, coil]], "Hose Rack", "WOOL")
    strips = [[x, 0, 7 + (x % 3), x + 2, 16, 8 + (x % 3), "fs_canvas_wet"] for x in (2, 6, 9, 13)]
    add("fs_hose_hung", strips, "Drying Hose", "WOOL", rotate=None)
    add("fs_hose_hung_end", [[s[0], 3, s[2], s[3], 16, s[5], s[6]] for s in strips]
        + [[s[0] - 0.5, 0, s[2] - 0.5, s[3] + 0.5, 3, s[5] + 0.5, brass] for s in strips], "Drying Hose", "WOOL", rotate=None)
    add("fs_scba_rack", [[0, 13, 13, 16, 14, 16, iron], [0, 4, 13, 16, 5, 16, iron]]
        + [[x, 0, 9.5, x + 4, 13, 13.5, {"side": "fs_cylinder", "up": "streetlight_metal", "down": "streetlight_metal"}] for x in (1, 6, 11)]
        + [[x + 1.5, 13, 11, x + 2.5, 15, 12, brass] for x in (1, 6, 11)], "Air Bottles", "METAL")

    # the bunks: a steel-framed single bed, the head block (pillow at the south) and the foot block north of it
    frame = {"side": iron, "up": iron, "down": iron}
    add("fs_bed_head", [[0, 0, 14.5, 1.5, 11, 16, frame], [14.5, 0, 14.5, 16, 11, 16, frame], [1.5, 8, 15, 14.5, 10.5, 15.5, frame],
                        [0.5, 3, 0, 15.5, 4, 15, frame], [1, 4, 0, 15, 7, 15, "home_mattress"], [3, 7, 9.5, 13, 9, 14.5, "home_pillow"],
                        [1, 7, 0, 15, 7.6, 9.5, "fs_blanket"]], "Bunk", "WOOL")
    add("fs_bed_foot", [[0, 0, 0, 1.5, 8, 1.5, frame], [14.5, 0, 0, 16, 8, 1.5, frame], [1.5, 5, 0.5, 14.5, 7.5, 1, frame],
                        [0.5, 3, 1, 15.5, 4, 16, frame], [1, 4, 1, 15, 7, 16, "home_mattress"], [1, 7, 1, 15, 7.6, 16, "fs_blanket"],
                        [2, 7.6, 2, 14, 9, 7, "fs_blanket"]], "Bunk", "WOOL")

    # outside: the red lamps, the siren, the call box, the hydrant, the bell's plinth
    for lit in ("", "_lit"):
        glass = f"fs_red_glass{lit}"
        add(f"fs_lamp_red{lit}", [[7, 9, 13, 9, 13, 16, "ext_black_metal"], [7.5, 12, 7, 8.5, 13, 13, "ext_black_metal"],
                                  [5, 4, 4, 11, 11, 10, glass], [6, 11, 5, 10, 12, 9, "ext_black_metal"], [7, 3, 6, 9, 4, 8, "ext_black_metal"]],
            "Station Lamp", "GLASS", **({"light": 10} if lit else {}))
    add("fs_siren", [[7, 0, 7, 9, 6, 9, iron], [3, 6, 3, 13, 13, 13, {"north": "fs_siren", "south": "fs_siren", "east": "fs_siren",
                                                                      "west": "fs_siren", "up": "streetlight_metal", "down": "streetlight_metal"}],
                     [5, 13, 5, 11, 15, 11, "streetlight_metal"], [7.5, 15, 7.5, 8.5, 16, 8.5, iron]], "Siren", "METAL")
    add("fs_callbox_post", [[6, 0, 6, 10, 16, 10, red], [5, 0, 5, 11, 2, 11, red]], "Alarm Post", "METAL")
    for lit in ("", "_lit"):
        add(f"fs_callbox{lit}", [[3, 0, 4, 13, 12, 12, {"north": "fs_callbox", "side": red, "up": red, "down": red}],
                                 [6.5, 12, 6.5, 9.5, 15, 9.5, f"fs_red_glass{lit}"], [6, 15, 6, 10, 16, 10, red]],
            "Fire Alarm Box", "METAL", **({"light": 5} if lit else {}))
    hyd = "fs_hydrant"
    add("fs_hydrant", [[4.5, 0, 4.5, 11.5, 1.5, 11.5, hyd], [5.5, 1.5, 5.5, 10.5, 10, 10.5, hyd], [3.5, 5, 6.5, 12.5, 7.5, 9.5, hyd],
                       [6.5, 5, 3.5, 9.5, 7.5, 5.5, "fs_red"], [5, 10, 5, 11, 11, 11, hyd], [6, 11, 6, 10, 12.5, 10, hyd],
                       [7.5, 12.5, 7.5, 8.5, 13.5, 8.5, "fs_red"]], "Fire Hydrant", "METAL")
    add("fs_plinth", None, "Memorial", "STONE", tex={"north": "fs_memorial", "side": STONE, "up": STONE, "down": STONE})
    for k in PLAQUES:
        add(f"fs_plaque_{k}", [[3, 4, 15.4, 13, 11.5, 16, {"north": f"fs_plaque_{k}", "side": "cv_brass"}]], "Door Plaque", "METAL",
            decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("fs_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} fire station blocks in {path}")


if __name__ == "__main__":
    main()
