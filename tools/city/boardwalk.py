"""The sb_ kit: Seabright Boardwalk's own pieces (Josiah, 2026-10-30: the coast and industry
places, the boardwalk third), after refs/coast: the boardwalk's weathered deck and its piles
(boardwalk-arcade 01-03, beach-pier 01-04), the arcade's claw machines, skee-ball lanes, pinball
and prize wall (boardwalk-arcade 01, 03), the rides on the amusement pier (boardwalk-amusements
01-03: the wooden coaster's track and its white-painted bents, the cars; the swing ride), the
beach's umbrellas, chairs, towels, snow fence and lifeguard chairs (lifeguard-tower 01-04), the
neon over the doors. world/county/Boardwalk.java puts them together.

- sb_deck (along x) and sb_deck_z: the boardwalk's planks, grey where the sun has had them;
- sb_pile: a creosoted pile (none); sb_beam: a stringer under the deck (axis);
- sb_rail: the white rail along an edge (facing: the side it stands on);
- sb_claw_lo / _hi (+ _hi_on): a claw machine, glass box of plush over the cabinet (facing);
- sb_skee_a / _b (+ _b_on): a skee-ball lane, a block for the roll and one for the rings (facing);
- sb_pinball, sb_prize: the pinball table and the prize wall's shelves (facing);
- sb_track_K(_p/_m): the coaster's track in the ride's plane (x, y), K its direction in 22.5 degree
  steps from +x toward +y; sb_bent_K(_p/_m): the timber bents and braces the same way;
  sb_car: a coaster car (facing), sb_car_front with the headlamp and the name;
- sb_swing: a swing ride's seat on its chains (facing); sb_chain: the chains alone (none);
- sb_umbrella_lo / _hi_a / _hi_b: the pole and a striped canopy three blocks across (none);
- sb_chair (facing), sb_towel_a / _b (facing), sb_cooler (facing), sb_fence (axis);
- sb_guard_lo / _hi: the lifeguard chair, its ladder and its seat under the sunshade (facing);
- sb_marquee(_on): a strip of bulbs on a wall (facing);
- neon signs over the shops, sb_neon_KEY_I (+ _on): pictures several blocks wide (facing);
- signs sb_sgn_KEY_0..1.

    python3 tools/city/boardwalk.py      rewrites the sb_ entries in blocks.json

photo_textures.py draws the textures (boardwalk.register()).
"""
import json
import math
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import broadcast as B  # noqa: E402
import ports  # noqa: E402
from furniture import area, disc  # noqa: E402
from school import Pic, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = B.BOLD
INK = (30, 28, 26)
WHITE = (240, 238, 230)
RED = (176, 36, 34)
NAVY = (30, 44, 68)
GREEN = (36, 84, 54)
YELLOW = (236, 196, 40)


# ---------------------------------------------------------------- textures

def deck(seed, along_x=True):
    """Boardwalk planks: eight boards to the block, each its own grey-brown, the gaps between dark,
    nail heads in pairs where they cross the joists, worn pale down the middle."""
    t = F.plain(seed, (132, 116, 96), 0.04, 0.35)
    rng = t.rng
    for i in range(8):
        y0 = i * 8
        tone = rng.uniform(0.82, 1.12)
        grey = rng.uniform(0.0, 0.5)
        col = mixc((140, 122, 98), (150, 148, 140), grey)
        area(t, 0, y0, N, y0 + 8, col=tuple(c * tone for c in col), dh=0.05)
        area(t, 0, y0 + 7, N, y0 + 8, mul=(0.35, 0.33, 0.3), dh=-0.3)
        for x in (6, 38):
            area(t, x, y0 + 2, x + 1, y0 + 3, col=(70, 64, 58))
            area(t, x, y0 + 5, x + 1, y0 + 6, col=(70, 64, 58))
        # a butt joint now and then
        if rng.random() < 0.4:
            x = int(rng.integers(10, 54))
            area(t, x, y0, x + 1, y0 + 7, mul=(0.4, 0.38, 0.35))
    grain = np.clip(P.fbm(t.rng) * 1.2, 0, 1)
    t.mix(grain * 0.18, (96, 84, 70))
    t.grime(0.25, (70, 66, 60))
    if not along_x:
        t.c[:] = np.rot90(t.c).copy()
        t.h[:] = np.rot90(t.h).copy()
    return t


def pile(seed):
    """A creosoted pile: near black, split along its length, barnacles and green slime low down."""
    t = F.wood(seed, "oak")
    t.c[:] = t.c * 0.35 + np.array([40, 34, 28]) * 0.65
    for x in range(4, N, 13):
        area(t, x, 0, x + 1, N, mul=(0.5, 0.5, 0.5), dh=-0.2)
    t.mix(np.clip(P.fbm(t.rng) - 0.3, 0, 1) * 0.6, (60, 80, 52))
    return t


def white_wood(seed):
    """Painted white timber, the paint gone grey and peeling to the grain."""
    t = F.wood(seed, "oak")
    t.c[:] = t.c * 0.2 + np.array([226, 224, 214]) * 0.8
    t.mix(np.clip(P.fbm(t.rng) * 1.4 - 0.45, 0, 1) * 0.8, (128, 112, 90))
    t.grime(0.25, (90, 92, 88))
    return t


def painted(seed, rgb):
    t = F.plain(seed, rgb, 0.05, 0.55)
    t.mix(np.clip(P.fbm(t.rng) * 1.4 - 0.5, 0, 1) * 0.6, mixc(rgb, (60, 56, 50), 0.6))
    ports.rust(t, 0.06)
    return t


def chrome_trim(seed):
    t = F.plain(seed, (190, 192, 196), 0.05, 0.85)
    t.f0[:] = 230
    return t


def plush(seed):
    """A heap of prizes behind glass: bears and bananas and dolphins, every colour at once."""
    t = F.plain(seed, (200, 180, 160), 0.1, 0.1)
    rng = t.rng
    cols = [(236, 92, 140), (90, 170, 230), (250, 214, 60), (120, 200, 110), (240, 140, 60), (180, 120, 220), (246, 246, 240)]
    for _ in range(60):
        disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(4, 9), col=cols[int(rng.integers(len(cols)))], dh=0.3)
    for _ in range(40):
        disc(t, rng.uniform(0, N), rng.uniform(0, N), 1.2, col=(20, 20, 20))
    return t


def lights(seed, rgb, lit):
    """A marquee band of bulbs on a coloured panel, the bulbs bright when the power's on."""
    t = F.plain(seed, rgb, 0.03, 0.6)
    for x in range(4, N, 8):
        for y in (6, N - 6):
            disc(t, x, y, 2.4, col=(255, 236, 170) if lit else (120, 110, 90), dh=0.3)
    return t


def skee(seed):
    """A skee-ball lane: the cloth-covered roll toward the hump, the side rails, the balls."""
    t = F.plain(seed, (46, 120, 70), 0.04, 0.2)
    area(t, 0, 0, 8, N, col=(220, 210, 190), dh=0.3)
    area(t, N - 8, 0, N, N, col=(220, 210, 190), dh=0.3)
    for y in range(12, N, 14):
        area(t, 8, y, N - 8, y + 1, mul=(0.8, 0.8, 0.8))
    return t


def rings(seed, lit):
    """The rings at the lane's head, each hole with its score in lights."""
    t = F.plain(seed, (40, 40, 44), 0.03, 0.4)
    for r, col in ((28, (220, 60, 60)), (20, (60, 120, 220)), (12, (240, 200, 40)), (5, (30, 30, 30))):
        disc(t, N / 2, N / 2 + 6, r, col=col, dh=0.2)
    for x in (12, N - 12):
        disc(t, x, 12, 5, col=(30, 30, 30))
    area(t, 0, 0, N, 4, col=(255, 230, 140) if lit else (120, 110, 90))
    return t


def screen(seed, lit):
    t = F.plain(seed, (24, 30, 60) if lit else (14, 16, 18), 0.03, 0.9)
    if lit:
        rng = t.rng
        for _ in range(30):
            disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(1, 4),
                 col=[(255, 80, 120), (90, 220, 255), (255, 230, 80)][int(rng.integers(3))])
    return t


def pinball(seed):
    """The playfield under its glass: bumpers, the lanes, the flippers at the foot."""
    t = F.plain(seed, (30, 40, 120), 0.04, 0.8)
    for x, y, c in ((20, 18, (240, 60, 60)), (40, 22, (240, 200, 40)), (30, 34, (80, 200, 240))):
        disc(t, x, y, 5, col=c, dh=0.4)
    area(t, 6, 0, 8, 50, col=(220, 220, 220))
    area(t, 56, 0, 58, 50, col=(220, 220, 220))
    area(t, 18, 54, 30, 57, col=(240, 240, 240))
    area(t, 34, 54, 46, 57, col=(240, 240, 240))
    return t


def stripes(seed, a, b, w=8):
    t = F.plain(seed, a, 0.03, 0.2)
    for x in range(0, N, 2 * w):
        area(t, x + w, 0, x + 2 * w, N, col=b)
    t.grime(0.3, (110, 100, 80))
    return t


def towel(seed, a, b):
    t = stripes(seed, a, b, 6)
    area(t, 0, 0, N, 4, col=WHITE)
    area(t, 0, N - 4, N, N, col=WHITE)
    return t


def track(seed, along_x=True):
    """The coaster's track seen from above: two steel-capped running rails on the ties."""
    t = F.wood(seed, "oak")
    t.c[:] = t.c * 0.5 + np.array([92, 80, 66]) * 0.5
    for x in range(0, N, 8):
        area(t, x, 0, x + 5, N, mul=(0.8, 0.8, 0.8))
    for y in (10, N - 14):
        area(t, 0, y, N, y + 4, col=(150, 152, 156), dh=0.4, f0=200)
    if not along_x:
        t.c[:] = np.rot90(t.c).copy()
        t.h[:] = np.rot90(t.h).copy()
    return t


# ---------------------------------------------------------------- signs and neon

SIGNS = {
    "beach": ("SEABRIGHT BEACH|NO LIFEGUARD ON DUTY", RED, WHITE),
    "closed": ("BEACH CLOSED|BY ORDER", RED, WHITE),
    "ride": ("YOU MUST BE THIS|TALL TO RIDE", YELLOW, INK),
    "tickets": ("TICKETS|RIDES & GAMES", NAVY, WHITE),
    "nodogs": ("NO DOGS|NO GLASS NO FIRES", NAVY, WHITE),
    "evac": ("EVACUATION ROUTE|PIER CLOSED", RED, WHITE),
    "bait": ("BAIT & TACKLE|FISHING PIER", GREEN, WHITE),
    "ramp": ("BEACH ACCESS", NAVY, WHITE),
}


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 58400 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 12, W - 1, H - 12, fill=field)
    p.rect(3, 14, W - 3, H - 14, outline=ink, width=1)
    lines = text.split("|")
    size = civic.fit(text, BOLD, 12 if len(lines) == 1 else 9, W - 14)
    for i, line in enumerate(lines):
        y = H / 2 + (i - (len(lines) - 1) / 2) * size * 1.15
        p.text(W / 2, y, line, BOLD, size, ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 58450 + i + len(key) * 11, smooth=0.5, grime=0.3)


# the neon: (cols, rows, text, tube colours by line, the board behind)
NEON = {
    "funland": (12, 3, "FUNLAND|ARCADE", [(255, 70, 160), (90, 220, 255)], (30, 20, 40)),
    "skee": (4, 1, "SKEE BALL - WIN PRIZES", [(255, 230, 80)], (24, 30, 60)),
    "taffy": (6, 1, "SALT WATER TAFFY", [(255, 120, 200)], (240, 236, 226)),
    "fries": (5, 1, "BOARDWALK FRIES", [(255, 180, 40)], (150, 30, 30)),
    "pizza": (6, 1, "PIZZA BY THE SLICE", [(255, 90, 60)], (236, 232, 220)),
    "cream": (4, 1, "ICE CREAM", [(120, 230, 255)], (236, 140, 180)),
    "pier": (6, 2, "SEABRIGHT|PIER", [(255, 240, 200), (255, 80, 80)], (28, 40, 70)),
    "noreaster": (6, 1, "THE NOR'EASTER", [(90, 220, 255)], (20, 24, 40)),
    "surf": (4, 1, "SURF SHOP", [(120, 255, 160)], (40, 90, 120)),
    "games": (3, 1, "GAMES", [(255, 230, 80)], (150, 30, 60)),
}


@lru_cache(maxsize=None)
def neon_pic(key, lit):
    cols, rows, text, tubes, board = NEON[key]
    p = Pic(cols, rows, (0, 0, 0, 0), 58600 + len(key) * 7 + lit)
    W, H = p.W, p.H
    p.rect(1, 2, W - 1, H - 2, fill=board)
    p.rect(3, 4, W - 3, H - 4, outline=(200, 200, 200) if lit else (110, 110, 110), width=1)
    lines = text.split("|")
    size = civic.fit(max(lines, key=len), BOLD, (H - 10) / len(lines) * 0.8, W - 12)
    for i, line in enumerate(lines):
        y = H / 2 + (i - (len(lines) - 1) / 2) * size * 1.1
        tube = tubes[i % len(tubes)]
        col = tube if lit else mixc(tube, (70, 70, 74), 0.7)
        if lit:
            # the glow off the tubes on the board
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                p.text(W / 2 + dx, y + dy, line, BOLD, size, tube, a=70, anchor="mm")
        p.text(W / 2, y, line, BOLD, size, col, anchor="mm")
    # bulbs round the board's edge
    for x in range(6, W - 4, 8):
        for y in (3, H - 3):
            p.ellipse(x - 1.2, y - 1.2, x + 1.2, y + 1.2, fill=(255, 240, 180) if lit else (110, 104, 90))
    return p


def neon(key, i, lit):
    return neon_pic(key, lit).tile(i, 58700 + i + len(key) * 13 + lit, smooth=0.7, grime=0.15)


def textures():
    T = {
        "sb_deck": lambda: deck(58000),
        "sb_deck_z": lambda: deck(58000, False),
        "sb_pile": lambda: pile(58001),
        "sb_white": lambda: white_wood(58002),
        "sb_red": lambda: painted(58003, (176, 40, 36)),
        "sb_blue": lambda: painted(58004, (40, 90, 170)),
        "sb_yellow": lambda: painted(58005, (230, 190, 50)),
        "sb_chrome": lambda: chrome_trim(58006),
        "sb_plush": lambda: plush(58007),
        "sb_bulbs": lambda: lights(58008, (176, 40, 36), False),
        "sb_bulbs_on": lambda: lights(58009, (176, 40, 36), True),
        "sb_skee": lambda: skee(58010),
        "sb_rings": lambda: rings(58011, False),
        "sb_rings_on": lambda: rings(58012, True),
        "sb_screen": lambda: screen(58013, False),
        "sb_screen_on": lambda: screen(58014, True),
        "sb_pinball": lambda: pinball(58015),
        "sb_stripe_a": lambda: stripes(58016, (220, 50, 50), (240, 236, 226)),
        "sb_stripe_b": lambda: stripes(58017, (40, 100, 190), (240, 236, 226)),
        "sb_stripe_c": lambda: stripes(58018, (240, 190, 40), (40, 150, 140)),
        "sb_towel_a": lambda: towel(58019, (236, 92, 140), (250, 214, 60)),
        "sb_towel_b": lambda: towel(58020, (40, 120, 200), (240, 236, 226)),
        "sb_track": lambda: track(58021),
        "sb_track_z": lambda: track(58021, False),
        "sb_fence": lambda: F.wood(58022, "oak"),
    }
    for key in SIGNS:
        for i in range(2):
            T[f"sb_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key, (cols, rows, *_r) in NEON.items():
        for i in range(cols * rows):
            T[f"sb_neon_{key}_{i}"] = lambda key=key, i=i: neon(key, i, False)
            T[f"sb_neon_{key}_{i}_on"] = lambda key=key, i=i: neon(key, i, True)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

WH, PILE, CH, BLACK = "sb_white", "sb_pile", "sb_chrome", "cm_iron"


def member(k, half, lo, hi, tex, o=0.0, depth=None):
    """A member in direction k (22.5 degree steps from +x toward +y) in the plane (x, y), o pixels
    off the cell's centre along its left normal (waterfront.py's wheel members, the same way)."""
    dz = half if depth is None else depth
    a = (k % 8) * 22.5
    if a <= 45:
        box = [lo, 8 + o - half, 8 - dz, hi, 8 + o + half, 8 + dz, tex]
        return [box + [None, {"axis": "z", "angle": a, "origin": [8, 8, 8]}]] if a else [box]
    if a < 135:
        r = a - 90
        box = [8 - o - half, lo, 8 - dz, 8 - o + half, hi, 8 + dz, tex]
        return [box + [None, {"axis": "z", "angle": r, "origin": [8, 8, 8]}]] if r else [box]
    box = [lo, 8 - o - half, 8 - dz, hi, 8 - o + half, 8 + dz, tex]
    return [box + [None, {"axis": "z", "angle": a - 180, "origin": [8, 8, 8]}]]


def track_member(k, o):
    """The coaster's track: the ledger under it, the ties, the two rails a gauge apart across z.
    Faces: the ties' top is the track texture, the ledger white."""
    a = (k % 8) * 22.5
    out = []
    # built as if along +x (k = 0) then turned like a member
    parts = [[-3, 4 + o, 1, 19, 8 + o, 15, WH],                   # the ledger
             [-3, 8 + o, 0, 19, 9.5 + o, 16, {"up": "sb_track", "side": "sb_pile", "down": "sb_pile"}],
             [-3, 9.5 + o, 2, 19, 11 + o, 4, CH], [-3, 9.5 + o, 12, 19, 11 + o, 14, CH]]
    for b in parts:
        b = list(b)
        if a == 0:
            out.append(b)
            continue
        out.append(b + [None, {"axis": "z", "angle": a if a <= 45 else a - 180 if a >= 135 else a - 90, "origin": [8, 8, 8]}])
    if 45 < a < 135:
        # the near-vertical ones: lay the box up the block instead
        out = []
        r = a - 90
        for b in parts:
            x0, y0, z0, x1, y1, z1, tex = b
            nb = [16 - y1, x0, z0, 16 - y0, x1, z1, tex]
            out.append(nb + ([None, {"axis": "z", "angle": r, "origin": [8, 8, 8]}] if r else []))
    return out


def claw_lo():
    return [[1, 0, 1, 15, 14, 15, {"side": "sb_red", "north": "sb_bulbs", "up": "sb_red", "down": "sb_red"}],
            [2, 6, 0, 14, 10, 1, CH], [6, 10, 0.2, 10, 12, 1, BLACK]]


def claw_hi(lit):
    glass = "wf_glass"
    return [[1, 0, 1, 15, 7, 15, "sb_plush"],
            [1, 0, 1, 15, 13, 1.6, glass], [1, 0, 14.4, 15, 13, 15, glass],
            [1, 0, 1, 1.6, 13, 15, glass], [14.4, 0, 1, 15, 13, 15, glass],
            [7.5, 7, 7.5, 8.5, 12, 8.5, CH], [6, 6, 6, 10, 7, 10, CH],
            [0.5, 13, 0.5, 15.5, 16, 15.5, {"side": "sb_bulbs_on" if lit else "sb_bulbs", "up": "sb_red", "down": "sb_red"}]]


def skee_a():
    """The roll: the cabinet's sides, the sloped cloth, the balls in the gutter at the player's end."""
    out = [[0, 0, 0, 2, 9, 16, "sb_yellow"], [14, 0, 0, 16, 9, 16, "sb_yellow"],
           [2, 0, 0, 14, 6, 16, {"up": "sb_skee", "side": "sb_yellow", "down": "sb_yellow"}],
           [2, 6, 13, 14, 9, 16, "sb_yellow"]]
    for x in (4, 7, 10):
        out.append([x, 9, 13.5, x + 2.4, 11.4, 15.9, "sb_chrome"])
    return out


def skee_b(lit):
    """The head: the hump, the rings tipped toward the player, the score board on its post."""
    r = "sb_rings_on" if lit else "sb_rings"
    return [[0, 0, 0, 2, 16, 16, "sb_yellow"], [14, 0, 0, 16, 16, 16, "sb_yellow"],
            [2, 0, 0, 14, 8, 16, "sb_yellow"],
            [2, 8, 4, 14, 16, 16, {"north": r, "up": "sb_yellow", "side": "sb_yellow", "down": "sb_yellow"},
             None, {"axis": "x", "angle": -22.5, "origin": [8, 8, 10]}],
            [0, 16, 12, 16, 22, 16, {"north": "sb_bulbs_on" if lit else "sb_bulbs", "side": "sb_red", "up": "sb_red", "down": "sb_red"}]]


def pinball_table():
    return [[2, 0, 1, 4, 8, 3, BLACK], [12, 0, 1, 14, 8, 3, BLACK], [2, 0, 13, 4, 8, 15, BLACK], [12, 0, 13, 14, 8, 15, BLACK],
            [1, 8, 0, 15, 11, 16, {"up": "sb_pinball", "side": "sb_blue", "down": "sb_blue"},
             None, {"axis": "x", "angle": 5, "origin": [8, 9, 8]}],
            [1, 11, 14, 15, 20, 16, {"north": "sb_screen_on", "side": "sb_blue", "up": "sb_blue", "south": "sb_blue"}]]


def prize_wall():
    out = [[0, 0, 13, 16, 16, 16, "sb_red"]]
    for y in (2, 7, 12):
        out += [[0, y, 8, 16, y + 1, 13, "sb_white"], [0.5, y + 1, 8.5, 15.5, y + 4.5, 12.5, "sb_plush"]]
    return out


def car(front):
    """A coaster car along z, nose to the north: the tub, two benches, the lap bar; the front one with
    its headlamp."""
    out = [[2, 2, 0, 14, 9, 16, {"side": "sb_red", "up": "sb_red", "down": "sb_pile"}],
           [3, 3, 2, 13, 9.2, 7, BLACK], [3, 3, 9, 13, 9.2, 14, BLACK],
           [3, 9, 6, 13, 12, 7, "sb_red"], [3, 9, 13, 13, 12, 14, "sb_red"],
           [3, 10, 2, 13, 11, 3, CH], [3, 10, 9, 13, 11, 10, CH],
           [3, 0, 2, 5, 2, 5, BLACK], [11, 0, 2, 13, 2, 5, BLACK], [3, 0, 11, 5, 2, 14, BLACK], [11, 0, 11, 13, 2, 14, BLACK]]
    if front:
        out += [[4, 4, -1, 12, 11, 0, {"north": "sb_bulbs_on", "side": "sb_red", "up": "sb_red", "down": "sb_red"}],
                [6.5, 7, -2, 9.5, 10, -1, CH]]
    return out


def swing():
    """A swing ride's chair on its four chains from above."""
    return [[4, 0, 4, 12, 1.5, 12, "sb_yellow"], [4, 1.5, 11, 12, 7, 12.5, "sb_yellow"],
            [4.5, 3, 4, 5.2, 16, 4.7, CH], [10.8, 3, 4, 11.5, 16, 4.7, CH],
            [4.5, 7, 11.3, 5.2, 16, 12, CH], [10.8, 7, 11.3, 11.5, 16, 12, CH],
            [4, 4, 3.5, 12, 4.6, 4.3, CH]]


def umbrella_lo():
    return [[7.3, 0, 7.3, 8.7, 16, 8.7, CH]]


def umbrella_hi(tex):
    """The pole and the canopy, three blocks across, in eight panels sloping down from the crown."""
    out = [[7.3, 0, 7.3, 8.7, 6, 8.7, CH], [6.8, 6.5, 6.8, 9.2, 8, 9.2, tex]]
    # the panels: four flat quarters sloping from the crown out
    for ax, sgn in (("x", 1), ("x", -1), ("z", 1), ("z", -1)):
        if ax == "x":
            box = [-8, 4, -8, 8, 5, 24, tex] if sgn < 0 else [8, 4, -8, 24, 5, 24, tex]
            out.append(box + [None, {"axis": "z", "angle": 15 * sgn, "origin": [8, 6.5, 8]}])
        else:
            box = [-8, 4, -8, 24, 5, 8, tex] if sgn < 0 else [-8, 4, 8, 24, 5, 24, tex]
            out.append(box + [None, {"axis": "x", "angle": -15 * sgn, "origin": [8, 6.5, 8]}])
    return out


def chair():
    """A folding beach chair, its back to the south, the striped sling."""
    return [[2, 0, 2, 3, 6, 3, CH], [13, 0, 2, 14, 6, 3, CH], [2, 0, 12, 3, 4, 13, CH], [13, 0, 12, 14, 4, 13, CH],
            [2.5, 3, 2, 13.5, 4, 12, "sb_stripe_b"],
            [2.5, 4, 12, 13.5, 13, 13, "sb_stripe_b", None, {"axis": "x", "angle": -20, "origin": [8, 4, 12]}],
            [2, 6, 2, 3, 7, 10, CH], [13, 6, 2, 14, 7, 10, CH]]


def cooler():
    return [[3, 0, 4, 13, 8, 12, {"side": "sb_blue", "up": "sb_white", "down": "sb_blue"}],
            [3, 8, 4, 13, 9.5, 12, "sb_white"], [5, 9.5, 7.5, 11, 10.5, 8.5, BLACK]]


def guard_lo():
    """The lifeguard chair's legs splayed out, the ladder up the front (north)."""
    out = []
    for x in (1, 14):
        for z in (1, 14):
            out.append([x, 0, z, x + 1.5, 16, z + 1.5, WH])
    for y in range(2, 16, 4):
        out.append([3, y, 0.5, 13, y + 1, 2.5, WH])
    out += [[1, 7, 1, 15.5, 8, 2.5, WH], [1, 7, 14, 15.5, 8, 15.5, WH]]
    return out


def guard_hi():
    """The seat, its arms and back, the sunshade on its pole, the rescue can hung off the side."""
    out = [[0, 0, 0, 16, 2, 16, WH], [1, 2, 13, 15, 10, 15, WH], [0, 2, 1, 2, 6, 15, WH], [14, 2, 1, 16, 6, 15, WH],
           [7.3, 10, 13.3, 8.7, 22, 14.7, CH], [-4, 21, -2, 20, 22, 18, "sb_stripe_a"],
           [15.5, 3, 6, 17.5, 9, 9, "sb_red"]]
    return out


def fence():
    """Snow fence: wired slats running along z (the axis it is placed with)."""
    out = [[7.6, 3, 0, 8.4, 3.6, 16, BLACK], [7.6, 10, 0, 8.4, 10.6, 16, BLACK]]
    for z in range(1, 16, 3):
        out.append([7.3, 0, z, 8.7, 13, z + 1.5, "sb_fence"])
    return out


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", tex=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        spec.update(extra)
        out[name] = spec

    add("sb_deck", None, "Boardwalk", "WOOD", rotate=None, tex={"up": "sb_deck", "down": "sb_pile", "side": "sb_pile"})
    add("sb_deck_z", None, "Boardwalk", "WOOD", rotate=None, tex={"up": "sb_deck_z", "down": "sb_pile", "side": "sb_pile"})
    add("sb_pile", [[5, 0, 5, 11, 16, 11, PILE]], "Pile", "WOOD", rotate=None, shape=[[5, 0, 5, 11, 16, 11]])
    add("sb_beam", [[5, 4, 0, 11, 16, 16, PILE]], "Stringer", "WOOD", rotate="axis", shape=[[5, 4, 0, 11, 16, 16]])
    # the rail stands on the edge it faces
    add("sb_rail", [[0, 0, 0.5, 2, 15, 2.5, WH], [14, 0, 0.5, 16, 15, 2.5, WH], [0, 13, 0, 16, 15, 3, WH],
                    [0, 6, 1, 16, 7.5, 2, WH]], "Boardwalk Rail", "WOOD", shape=[[0, 0, 0, 16, 16, 3]])
    for n in ("sb_white", "sb_red", "sb_blue", "sb_yellow", "sb_stripe_a", "sb_stripe_b", "sb_stripe_c"):
        add(n, None, "Painted Boards" if "stripe" not in n else "Canvas", "WOOD" if "stripe" not in n else "WOOL", rotate=None, tex=n)
    add("sb_claw_lo", claw_lo(), "Claw Machine", "METAL", shape=[[1, 0, 1, 15, 16, 15]])
    add("sb_claw_hi", claw_hi(False), "Claw Machine", "GLASS", render="translucent", shape=[[1, 0, 1, 15, 16, 15]])
    add("sb_claw_hi_on", claw_hi(True), "Claw Machine", "GLASS", render="translucent", light=10, shape=[[1, 0, 1, 15, 16, 15]])
    add("sb_skee_a", skee_a(), "Skee-Ball Lane", "WOOD", shape=[[0, 0, 0, 16, 9, 16]])
    add("sb_skee_b", skee_b(False), "Skee-Ball Lane", "WOOD", shape=[[0, 0, 0, 16, 16, 16]])
    add("sb_skee_b_on", skee_b(True), "Skee-Ball Lane", "WOOD", light=8, shape=[[0, 0, 0, 16, 16, 16]])
    add("sb_pinball", pinball_table(), "Pinball", "METAL", shape=[[1, 0, 0, 15, 16, 16]])
    add("sb_prize", prize_wall(), "Prize Wall", "WOOD", shape=[[0, 0, 8, 16, 16, 16]])
    for k in range(8):
        for sfx, o in (("", 0.0), ("_p", 6.0), ("_m", -6.0)):
            add(f"sb_track_{k}{sfx}", track_member(k, o), "Coaster Track", "WOOD", rotate=None, shape=[[0, 5, 0, 16, 11, 16]])
            add(f"sb_bent_{k}{sfx}", member(k, 1.6, -4, 20, WH, o, 1.6), "Coaster Bent", "WOOD", rotate=None,
                render="cutout", shape=[[6, 6, 6, 10, 10, 10]])
    # the flat track running along z, for the turnarounds at the coaster's ends
    add("sb_track_z", [[1, 4, -3, 15, 8, 19, WH], [0, 8, -3, 16, 9.5, 19, {"up": "sb_track_z", "side": "sb_pile", "down": "sb_pile"}],
                       [2, 9.5, -3, 4, 11, 19, CH], [12, 9.5, -3, 14, 11, 19, CH]], "Coaster Track", "WOOD", rotate=None,
        shape=[[0, 5, 0, 16, 11, 16]])
    add("sb_car", car(False), "Coaster Car", "METAL", shape=[[2, 0, 0, 14, 12, 16]])
    add("sb_car_front", car(True), "Coaster Car", "METAL", light=6, shape=[[2, 0, 0, 14, 12, 16]])
    add("sb_swing", swing(), "Swing Seat", "METAL", decor=True, shape=[[4, 0, 4, 12, 16, 12]])
    add("sb_chain", [[7.6, 0, 4, 8.4, 16, 4.8, CH], [7.6, 0, 11.2, 8.4, 16, 12, CH]], "Chains", "METAL", decor=True,
        shape=[[7, 0, 4, 9, 16, 12]])
    add("sb_umbrella_lo", umbrella_lo(), "Beach Umbrella", "WOOL", rotate=None, decor=True, shape=[[7, 0, 7, 9, 16, 9]])
    add("sb_umbrella_hi_a", umbrella_hi("sb_stripe_a"), "Beach Umbrella", "WOOL", rotate=None, decor=True, shape=[[7, 0, 7, 9, 8, 9]])
    add("sb_umbrella_hi_b", umbrella_hi("sb_stripe_c"), "Beach Umbrella", "WOOL", rotate=None, decor=True, shape=[[7, 0, 7, 9, 8, 9]])
    add("sb_chair", chair(), "Beach Chair", "WOOL", decor=True, shape=[[2, 0, 2, 14, 13, 14]])
    for k in ("a", "b"):
        add(f"sb_towel_{k}", [[1, 0, 3, 15, 0.4, 13, f"sb_towel_{k}"]], "Beach Towel", "WOOL", decor=True, shape=[[1, 0, 3, 15, 1, 13]])
    add("sb_cooler", cooler(), "Cooler", "METAL", shape=[[3, 0, 4, 13, 10, 12]], loot="st_cooler")
    add("sb_fence", fence(), "Snow Fence", "WOOD", rotate="axis", render="cutout", shape=[[7, 0, 0, 9, 13, 16]])
    add("sb_guard_lo", guard_lo(), "Lifeguard Chair", "WOOD", shape=[[1, 0, 0, 15.5, 16, 15.5]])
    add("sb_guard_hi", guard_hi(), "Lifeguard Chair", "WOOD", shape=[[0, 0, 0, 16, 10, 16]])
    plate = lambda tex: [[0, 0, 15.2, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]
    add("sb_marquee", plate("sb_bulbs"), "Marquee Lights", "GLASS", decor=True)
    add("sb_marquee_on", plate("sb_bulbs_on"), "Marquee Lights", "GLASS", decor=True, light=12)
    for key in SIGNS:
        for i in range(2):
            add(f"sb_sgn_{key}_{i}", plate(f"sb_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key, (cols, rows, *_r) in NEON.items():
        for i in range(cols * rows):
            add(f"sb_neon_{key}_{i}", plate(f"sb_neon_{key}_{i}"), "Neon Sign", "GLASS", decor=True)
            add(f"sb_neon_{key}_{i}_on", plate(f"sb_neon_{key}_{i}_on"), "Neon Sign", "GLASS", decor=True, light=12)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    for k in [k for k in blocks_ if k.startswith("sb_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} boardwalk blocks in {path}")


if __name__ == "__main__":
    main()
