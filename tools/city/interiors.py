#!/usr/bin/env python3
"""Shop fittings: shelves of stock for each kind of shop, coolers, counters and tills, washers
and dryers, gun cases and racks, teller glass, a safe, diner booths and stools, a grill, a pizza
oven, clothes rails, pegboards and paint cans, plus the floors and wall tiles that set each shop
apart. Everything has been picked over: gaps on the shelves, stock on the floor, grime.

    python3 tools/city/interiors.py        rewrites the store_ entries in blocks.json

photo_textures.py draws the textures (interiors.textures()); world/city/Shops.java fits out the
shops. Like the signs, wall pieces face the wall they're fixed to (north side against it).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402

N = P.N
HERE = Path(__file__).parent

# label colours for packaging, muted by age
PACK = [(176, 40, 36), (40, 84, 150), (214, 176, 52), (44, 120, 64), (220, 214, 200), (200, 96, 36),
        (120, 50, 120), (30, 30, 30), (90, 160, 190), (230, 120, 140)]


def tex(seed, base):
    t = P.Tex(seed, base)
    return t


def age(t, grime=0.45, streak=0.0):
    t.c = t.c * 0.82 + np.array((120, 116, 108), np.float32) * 0.18 * (t.c.mean(axis=2, keepdims=True) / 128)
    t.grime(grime)
    if streak:
        t.streaks(streak, 0.85)
    t.speckle(0.015, (30, 28, 24), 0.4)
    return t


def rect(t, x0, y0, x1, y1, col, h=None, smooth=None):
    x0, y0, x1, y1 = max(0, int(x0)), max(0, int(y0)), min(N, int(x1)), min(N, int(y1))
    if x1 <= x0 or y1 <= y0:
        return
    t.c[y0:y1, x0:x1] = col
    if h is not None:
        t.h[y0:y1, x0:x1] = h
    if smooth is not None:
        t.smooth[y0:y1, x0:x1] = smooth


def shade_cyl(t, x0, y0, x1, y1):
    """Round shading across an item: dark edges, a highlight left of centre."""
    w = int(x1) - int(x0)
    if w <= 1:
        return
    s = 0.65 + 0.5 * np.sin(np.linspace(0.2, np.pi - 0.2, w)) ** 0.7
    s[max(0, w // 3 - 1):w // 3 + 1] *= 1.25
    y0, y1 = max(0, int(y0)), min(N, int(y1))
    t.c[y0:y1, int(x0):int(x1)] *= s[None, :, None]


# ---------------------------------------------------------------- stock on shelves

def item(t, rng, kind, x, base, room):
    """Draws one item standing on a shelf at baseline y=base, starting at x; returns its width."""
    if kind == "cans":
        w, h = 6, int(rng.uniform(8, 11))
        col = PACK[rng.integers(len(PACK))]
        rect(t, x, base - h, x + w, base, (150, 150, 146), 0.9, 0.6)
        rect(t, x, base - h + 2, x + w, base - 2, col)
        rect(t, x, base - h + 4, x + w, base - h + 5, (230, 226, 210))
        shade_cyl(t, x, base - h, x + w, base)
        return w + 1
    if kind == "boxes":
        w, h = int(rng.uniform(7, 12)), int(rng.uniform(10, min(room, 16)))
        col = np.array(PACK[rng.integers(len(PACK))], np.float32)
        rect(t, x, base - h, x + w, base, col, 0.88, 0.25)
        rect(t, x + 1, base - h + 3, x + w - 1, base - h + 6, (232, 228, 214))
        rect(t, x + 2, base - h + 4, x + w - 3, base - h + 5, col * 0.5)
        t.c[base - h:base, x + w - 1:x + w] *= 0.6
        return w + 1
    if kind in ("bottles", "wine"):
        w = 4
        h = int(rng.uniform(12, min(room, 16)))
        glass = [(40, 80, 40), (90, 52, 20), (170, 180, 170), (60, 30, 30), (110, 90, 40)][rng.integers(5)]
        rect(t, x, base - h + 5, x + w, base, glass, 0.9, 0.9)
        rect(t, x + 1, base - h, x + w - 1, base - h + 5, glass, 0.9, 0.9)
        rect(t, x + 1, base - h, x + w - 1, base - h + 1, (180, 150, 60) if kind == "wine" else (200, 30, 30))
        rect(t, x, base - h + 8, x + w, base - h + 12, PACK[rng.integers(len(PACK))])
        t.c[base - h + 5:base, x:x + 1] *= 1.5  # glint down one side
        return w + 1
    if kind == "pills":
        w, h = int(rng.uniform(4, 7)), int(rng.uniform(5, 9))
        rect(t, x, base - h, x + w, base, (226, 224, 216), 0.86, 0.4)
        rect(t, x, base - h + 1, x + w, base - h + 3, PACK[rng.integers(len(PACK))])
        if rng.random() < 0.4:  # a pill bottle instead: orange plastic, white cap
            rect(t, x, base - h, x + w, base, (196, 110, 30), 0.86, 0.7)
            rect(t, x, base - h, x + w, base - h + 2, (230, 230, 226))
            rect(t, x, base - h + 3, x + w, base - h + 5, (236, 232, 220))
            shade_cyl(t, x, base - h, x + w, base)
        return w + 1
    if kind == "paint":
        w, h = 8, 9
        col = PACK[rng.integers(len(PACK))]
        rect(t, x, base - h, x + w, base, (160, 160, 156), 0.9, 0.6)
        rect(t, x, base - h + 2, x + w, base - 1, (226, 224, 214))
        rect(t, x + 1, base - h + 4, x + w - 1, base - h + 7, col)
        for dx in range(w):  # drips of the colour down the side
            if rng.random() < 0.3 and x + dx < N:
                t.c[base - h:base - h + int(rng.uniform(1, 5)), x + dx] = col
        shade_cyl(t, x, base - h, x + w, base)
        return w + 1
    if kind == "tools":
        w, h = int(rng.uniform(6, 10)), int(rng.uniform(6, 10))
        col = [(214, 120, 30), (200, 170, 40), (170, 40, 30), (40, 60, 120)][rng.integers(4)]
        rect(t, x, base - h, x + w, base, col, 0.9, 0.4)
        rect(t, x + 1, base - h + 2, x + w - 1, base - h + 4, (30, 30, 30))
        return w + 1
    if kind == "electronics":
        w, h = int(rng.uniform(8, 14)), int(rng.uniform(6, min(room, 12)))
        rect(t, x, base - h, x + w, base, (34, 34, 36), 0.9, 0.5)
        rect(t, x + 1, base - h + 1, x + w - 1, base - h + 3, (70, 72, 76))
        t.c[base - 3, x + 2:x + 4] = (200, 40, 30)
        return w + 1
    if kind == "ammo":
        w, h = int(rng.uniform(6, 9)), int(rng.uniform(5, 8))
        col = [(40, 70, 40), (150, 40, 30), (200, 160, 40), (40, 40, 44), (30, 70, 130)][rng.integers(5)]
        rect(t, x, base - h, x + w, base, col, 0.88, 0.3)
        rect(t, x + 1, base - h + 2, x + w - 1, base - h + 3, (220, 210, 180))
        return w
    if kind == "folded":  # folded clothes and linens
        w, h = int(rng.uniform(8, 12)), int(rng.uniform(5, 10))
        col = np.array([(90, 100, 120), (150, 60, 50), (190, 180, 160), (60, 70, 50), (120, 90, 130),
                        (40, 40, 44), (170, 140, 90)][rng.integers(7)], np.float32)
        for k in range(h // 2):
            rect(t, x, base - 2 * k - 2, x + w, base - 2 * k, col * (0.85 + 0.25 * rng.random()), 0.8, 0.1)
            t.c[base - 2 * k - 1, x:x + w] *= 0.7
        return w + 1
    raise ValueError(kind)


# how far up the next layer of small stock sits
STACK = {"cans": 11, "pills": 9, "ammo": 8, "tools": 10, "paint": 10, "electronics": 12, "folded": 10}

SHELF_STOCK = {
    "grocery": ["cans", "cans", "boxes", "boxes", "bottles"],
    "pharmacy": ["pills", "pills", "pills", "boxes"],
    "liquor": ["bottles", "wine", "wine", "bottles"],
    "hardware": ["paint", "tools", "tools", "boxes"],
    "thrift": ["folded", "folded", "boxes", "electronics"],
    "pawn": ["electronics", "electronics", "boxes", "tools"],
    "ammo": ["ammo", "ammo", "ammo", "boxes"],
    "empty": [],
}


def shelf(kind, seed):
    """A steel gondola bay, two shelves well stocked (it looks full; what's really there is the
    loot), with dust and the odd gap."""
    t = tex(seed, (54, 56, 56))  # pegboard back
    rng = t.rng
    for y in range(4, N, 6):
        for x in range(3, N, 6):
            t.c[y, x] = (24, 24, 24)
    rows = [(2, 30), (34, 60)]
    stock = SHELF_STOCK[kind]
    for top, base in rows:
        if stock:
            x = 4
            while x < N - 10:
                if rng.random() < 0.06:  # the odd gap
                    x += int(rng.uniform(4, 8))
                    continue
                kind = stock[rng.integers(len(stock))]
                w = item(t, rng, kind, x, base, base - top)
                # small stock is stacked up to fill the shelf
                lift = STACK.get(kind, 0)
                y = base - lift
                while lift and y - lift >= top and rng.random() < 0.85:
                    item(t, rng, kind, x, y, lift)
                    y -= lift
                x += w
        else:
            for _ in range(int(rng.uniform(0, 3))):
                item(t, rng, "boxes", int(rng.uniform(6, N - 18)), base, base - top)
        # shelf lip with a price strip, and its shadow
        rect(t, 0, base, N, base + 4, (150, 152, 150), 0.95, 0.5)
        rect(t, 0, base + 1, N, base + 3, (212, 206, 170))
        for x in range(6, N - 4, 11):
            rect(t, x, base + 1, x + 4, base + 3, (40, 40, 40))
        t.c[base + 4:base + 6] *= 0.55
    # uprights at each end
    rect(t, 0, 0, 3, N, (120, 122, 122), 1.0, 0.5)
    rect(t, N - 3, 0, N, N, (120, 122, 122), 1.0, 0.5)
    t.f0[:, :3] = t.f0[:, -3:] = 200
    return age(t, 0.35)


# ---------------------------------------------------------------- appliances and fittings

def appliance_side(seed=300):
    t = tex(seed, (196, 194, 186))
    t.vary(0.04)
    t.smooth[:] = 0.55
    return age(t, 0.5, 0.4)


def cooler(top, seed):
    """Glass-door drinks cooler: steel frame, bottles behind fogged glass, a dead light box."""
    t = tex(seed, (24, 26, 28))
    rng = t.rng
    rect(t, 0, 0, N, N, (170, 172, 170), 1.0, 0.5)
    rect(t, 5, 5 if not top else 14, N - 5, N - 3 if not top else N, (20, 24, 26), 0.8, 0.9)
    y0 = 5 if not top else 14
    for base in range(y0 + 15, N, 15):
        x = 7
        while x < N - 10:
            if rng.random() < 0.06:
                x += int(rng.uniform(4, 8))
                continue
            x += item(t, rng, "bottles" if rng.random() < 0.6 else "cans", x, base, 14)
        rect(t, 6, base, N - 6, base + 1, (90, 92, 92))
    # the glass: grey fog, a long reflection, grime along the bottom, a handle
    g = slice(y0, N - 3 if not top else N)
    t.c[g, 5:N - 5] = t.c[g, 5:N - 5] * 0.6 + np.array((70, 80, 84), np.float32) * 0.4
    for k in range(N):
        x = 14 + k // 3
        if 5 < x < N - 5 and y0 <= k < N:
            t.c[k, x:x + 3] = t.c[k, x:x + 3] * 0.6 + 90
    rect(t, N - 10, y0 + 4, N - 8, N - 6, (210, 210, 206), 1.0, 0.8)
    if top:  # the header box that used to light up
        rect(t, 0, 0, N, 12, (190, 30, 34), 0.95, 0.4)
        rect(t, 8, 4, N - 8, 8, (230, 224, 210))
    return age(t, 0.4)


def counter_front(seed=310):
    t = tex(seed, (126, 92, 62))  # wood-grain laminate
    t.vary(0.08, ((2, 1.0), (16, 0.6), (64, 0.3)))
    rect(t, 0, N - 6, N, N, (34, 32, 30), 0.6)  # kick plate, scuffed
    rect(t, 0, 0, N, 3, (170, 168, 160), 0.95, 0.5)
    t.speckle(0.03, (180, 170, 150), 0.4)
    return age(t, 0.5)


def counter_top(seed=311):
    t = tex(seed, (182, 176, 162))
    t.vary(0.05)
    t.smooth[:] = 0.6
    t.speckle(0.05, (120, 116, 104), 0.5)
    return age(t, 0.45)


def register(seed=312):
    t = tex(seed, (52, 52, 54))
    rect(t, 8, 6, 40, 20, (28, 60, 40), 0.95, 0.9)  # little green display
    for y in range(28, 56, 5):  # a keypad of small worn keys
        for x in range(8, 42, 5):
            rect(t, x, y, x + 3, y + 3, (176, 172, 160), 1.0, 0.5)
    rect(t, 46, 28, 58, 40, (150, 30, 30), 0.95)  # the drawer key
    rect(t, 46, 44, 58, 56, (36, 36, 38), 0.95)  # receipt slot
    return age(t, 0.3)


def case_glass(contents, seed):
    """A glass display case seen from above and the front: velvet, the goods, cracked glass."""
    t = tex(seed, (52, 14, 20) if contents == "jewelry" else (30, 32, 36))
    rng = t.rng
    t.vary(0.08)
    if contents == "guns":
        for y in (12, 34):  # two pistols and a rifle laid out
            for x in (6, 34):
                if rng.random() < 0.7:
                    rect(t, x, y, x + 16, y + 4, (40, 40, 42), 0.95, 0.7)
                    rect(t, x + 2, y + 4, x + 6, y + 12, (60, 44, 30), 0.95, 0.4)
        rect(t, 6, 52, 58, 55, (44, 44, 46), 0.95, 0.7)
        rect(t, 40, 51, 58, 57, (86, 58, 34), 0.95, 0.4)
    else:
        for _ in range(14):  # rings, watches, chains
            x, y = rng.integers(4, N - 8, 2)
            col = (210, 176, 70) if rng.random() < 0.6 else (200, 200, 204)
            rect(t, x, y, x + 3, y + 3, col, 1.0, 0.9)
            t.c[y + 1, x + 1] = (60, 20, 24)
        for _ in range(3):
            x, y = rng.integers(4, N - 20, 2)
            for k in range(16):
                t.c[min(N - 1, y + int(3 * np.sin(k / 3))), x + k] = (220, 186, 80)
    # glass over it: greyed, a streak of reflection, a crack
    t.c = t.c * 0.75 + 30
    for k in range(N):
        t.c[k, max(0, min(N - 3, 40 - k // 2)):max(0, min(N, 43 - k // 2))] += 35
    t.smooth[:] = 0.9
    return age(t, 0.3)


def case_base(seed=320):
    t = tex(seed, (40, 38, 36))
    t.vary(0.05)
    rect(t, 0, 0, N, 3, (150, 150, 146), 1.0, 0.6)
    return age(t, 0.4)


def wall_board(kind, seed):
    """Wall fittings: a rifle rack, the pawn shop's guitars and TV, a tool pegboard, a menu."""
    if kind == "menu":
        t = tex(seed, (26, 28, 26))
        rect(t, 0, 0, N, 3, (92, 64, 40), 1.0)
        rect(t, 0, N - 3, N, N, (92, 64, 40), 1.0)
        rng = t.rng
        for y in range(9, N - 6, 6):
            w = int(rng.uniform(20, 36))
            rect(t, 6, y, 6 + w, y + 2, (200, 198, 186))
            rect(t, N - 14, y, N - 6, y + 2, (220, 190, 90))
        return age(t, 0.3)
    if kind == "tile":
        t = tex(seed, (214, 214, 206))
        for k in range(0, N, 8):
            t.c[k] *= 0.7
            t.c[:, k] *= 0.7
            t.h[k] = t.h[:, k] = 0.6
        t.smooth[:] = 0.75
        rng = t.rng
        for _ in range(3):  # tiles knocked off, bare render behind
            x, y = rng.integers(0, 8, 2) * 8
            rect(t, x + 1, y + 1, x + 8, y + 8, (120, 112, 100), 0.5, 0.1)
        t.grime(0.6, (60, 54, 44))
        t.streaks(0.6, 0.75)
        return t
    t = tex(seed, (120, 92, 60) if kind != "pegboard" else (150, 120, 84))
    t.vary(0.08)
    rng = t.rng
    if kind == "pegboard":
        for y in range(3, N, 5):
            for x in range(3, N, 5):
                t.c[y, x] = (60, 44, 30)
        tools = [((150, 40, 30), 10, 3), ((40, 60, 140), 3, 16), ((200, 160, 40), 14, 4), ((70, 70, 72), 3, 20),
                 ((160, 90, 30), 12, 3)]
        for _ in range(9):
            if rng.random() < 0.3:
                continue  # an empty hook
            col, w, h = tools[rng.integers(len(tools))]
            x, y = rng.integers(2, N - w - 2), rng.integers(4, N - h - 2)
            rect(t, x, y, x + w, y + h, col, 0.95, 0.5)
            rect(t, x + w // 2, y - 2, x + w // 2 + 1, y, (180, 180, 176))
    elif kind == "gun_rack":
        for y in range(0, N, 8):  # slat wall
            t.c[y] *= 0.6
        for i, x in enumerate((8, 26, 44)):
            if rng.random() < 0.25:
                continue  # someone took it
            rect(t, x, 4, x + 4, 40, (36, 36, 38), 0.98, 0.7)  # barrel and action
            rect(t, x - 1, 40, x + 6, 60, (96, 62, 34), 0.98, 0.4)  # stock
            rect(t, x + 4, 30, x + 7, 34, (36, 36, 38), 0.98)
        rect(t, 0, 2, N, 4, (60, 60, 60), 1.0)
    elif kind == "pawn_wall":
        for y in range(0, N, 8):
            t.c[y] *= 0.6
        # a guitar, and an old TV set on a bracket
        rect(t, 10, 4, 13, 30, (60, 40, 26), 0.98)
        cy = 44
        yy, xx = np.mgrid[0:N, 0:N]
        body = ((xx - 11.5) ** 2 / 64 + (yy - cy) ** 2 / 100) < 1
        t.c[body] = (150, 60, 30)
        t.h[body] = 0.98
        t.c[((xx - 11.5) ** 2 + (yy - cy + 2) ** 2) < 6] = (20, 16, 14)
        rect(t, 30, 18, 60, 42, (40, 40, 42), 0.98, 0.5)
        rect(t, 33, 21, 52, 39, (30, 40, 44), 0.98, 0.9)
        rect(t, 54, 24, 58, 28, (120, 120, 120))
        rect(t, 30, 50, 58, 58, (60, 60, 64), 0.98)  # a stereo
        t.c[53, 33:55:3] = (180, 180, 170)
    return age(t, 0.4)


def washer(dryer, seed):
    """Coin laundry front: white enamel, a round porthole door (dark glass or steel drum), coin slot."""
    t = appliance_side(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    cx, cy = 32, 38
    r = np.hypot(xx - cx, yy - cy)
    t.c[r < 20] = (150, 152, 152)
    t.h[r < 20] = 0.95
    t.c[r < 16] = (36, 42, 46) if not dryer else (110, 112, 112)
    if dryer:
        t.c[(r < 16) & ((xx + yy) % 4 == 0)] = (70, 72, 72)  # perforated drum
    else:
        t.c[(r < 16) & (yy > cy + 4)] = (60, 70, 76)  # grey water left in the drum
    t.c[(r > 12) & (r < 14) & (xx < cx) & (yy < cy)] += 50
    rect(t, 6, 4, N - 6, 13, (60, 62, 64), 1.0, 0.5)  # control panel
    rect(t, 10, 6, 22, 11, (30, 50, 40))
    rect(t, 44, 6, 50, 11, (170, 170, 166))
    t.smooth[r < 16] = 0.9
    return age(t, 0.5)


def grill(top, seed):
    if top:
        t = tex(seed, (40, 40, 40))
        for y in range(4, N, 6):
            rect(t, 2, y, N - 2, y + 2, (70, 70, 70), 1.0, 0.4)
        t.mix(np.clip(P.fbm(t.rng) + 0.4, 0, 1), (30, 22, 14), 0.6)  # old grease
        return age(t, 0.4)
    t = tex(seed, (150, 152, 150))
    t.vary(0.05)
    t.f0[:] = 210
    t.smooth[:] = 0.55
    for x in range(10, N, 14):
        rect(t, x, 8, x + 5, 13, (30, 30, 30), 1.0)
    rect(t, 4, 20, N - 4, 56, (130, 132, 130), 0.85)
    rect(t, 10, 24, N - 10, 27, (200, 200, 196), 1.0, 0.8)
    return age(t, 0.55, 0.3)


def pizza_oven(seed=360):
    t = tex(seed, (150, 152, 150))
    t.f0[:] = 210
    t.smooth[:] = 0.5
    rect(t, 4, 14, N - 4, 46, (40, 40, 42), 0.85)
    rect(t, 8, 18, N - 8, 42, (60, 34, 20), 0.75)  # the deck inside, scorched
    rect(t, 4, 12, N - 4, 15, (200, 200, 196), 1.0, 0.8)
    for x in range(10, N - 6, 10):
        rect(t, x, 52, x + 4, 56, (30, 30, 30), 1.0)
    return age(t, 0.6, 0.3)


def safe(front, seed):
    t = tex(seed, (52, 58, 56))
    t.vary(0.06)
    t.f0[:] = 200
    t.smooth[:] = 0.45
    if front:
        rect(t, 4, 4, N - 4, N - 4, (44, 50, 48), 0.85)
        yy, xx = np.mgrid[0:N, 0:N]
        r = np.hypot(xx - 26, yy - 30)
        t.c[r < 8] = (170, 168, 160)
        t.c[r < 3] = (60, 60, 60)
        rect(t, 42, 26, 54, 34, (170, 168, 160), 1.0, 0.8)
        for y in (8, N - 12):
            rect(t, N - 8, y, N - 5, y + 6, (120, 120, 116), 1.0)
    return age(t, 0.4)


def teller_glass(seed=380):
    """Bulletproof teller glass: thick, scratched, a speaking grille."""
    t = P.glass(seed, (70, 80, 84))
    t.alpha[:] = 110
    rect(t, 0, 0, N, 3, (90, 92, 92), 1.0)
    t.alpha[0:3] = 255
    yy, xx = np.mgrid[0:N, 0:N]
    grille = (np.hypot(xx - 32, yy - 40) < 7) & ((xx + yy) % 3 == 0)
    t.c[grille] = (150, 150, 146)
    t.alpha[grille] = 255
    rng = t.rng
    for _ in range(6):  # faint scratches from years of keys and coins
        x, y = rng.integers(4, N - 20), rng.integers(6, N - 8)
        for k in range(int(rng.uniform(6, 16))):
            if x + k < N:
                t.c[y, x + k] = (120, 128, 130)
                t.alpha[y, x + k] = 140
    return t


def clothes(seed=390):
    """Clothes hanging on a rail: coats and shirts side by side, some hangers bare."""
    t = tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    rng = t.rng
    x = 0
    while x < N:
        w = int(rng.uniform(3, 6))
        if rng.random() < 0.2:
            t.c[0:3, x + 1] = (150, 150, 150)  # a bare hanger
            t.alpha[0:3, x + 1] = 255
            x += w
            continue
        length = int(rng.uniform(34, 60))
        col = np.array([(70, 80, 100), (130, 40, 40), (180, 170, 150), (50, 60, 44), (110, 80, 120), (40, 40, 44),
                        (160, 120, 70), (90, 110, 120)][rng.integers(8)], np.float32)
        t.c[0:length, x:x + w] = col * (0.85 + 0.2 * rng.random())
        t.c[0:length, x + w - 1:x + w] *= 0.6
        t.alpha[0:length, x:x + w] = 255
        t.h[0:length, x:x + w] = 0.85
        x += w
    t.smooth[:] = 0.1
    return age(t, 0.3)


def vinyl(seed=395):
    t = tex(seed, (150, 34, 36))
    t.vary(0.08)
    t.smooth[:] = 0.6
    for _ in range(4):  # splits in the vinyl, foam showing
        x, y = t.rng.integers(4, N - 12, 2)
        rect(t, x, y, x + int(t.rng.uniform(4, 10)), y + 2, (210, 190, 120), 0.6, 0.1)
    return age(t, 0.45)


def table_top(seed=396):
    t = tex(seed, (196, 190, 176))
    t.vary(0.04)
    rect(t, 0, 0, N, 2, (160, 160, 156), 1.0, 0.7)
    rect(t, 0, N - 2, N, N, (160, 160, 156), 1.0, 0.7)
    t.smooth[:] = 0.6
    return age(t, 0.4)


def spill(seed=397):
    """Stock dragged off the shelves and trodden into the floor."""
    t = tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    rng = t.rng
    for _ in range(int(rng.uniform(5, 9))):
        kind = ["cans", "boxes", "pills", "bottles"][rng.integers(4)]
        x, y = rng.integers(2, N - 14), rng.integers(14, N - 2)
        before = t.c.copy()
        w = item(t, rng, kind, x, y, 12)
        drawn = np.any(t.c != before, axis=2)
        t.alpha[drawn] = 255
    for _ in range(40):  # crumbs, pills, glass
        x, y = rng.integers(0, N, 2)
        t.c[y, x] = [(220, 216, 200), (60, 90, 40), (180, 40, 30)][rng.integers(3)]
        t.alpha[y, x] = 255
    return t


def shelf_fallen(seed=398):
    """The back of a toppled gondola: pegboard face up, a dented upright, stock crushed under it."""
    t = tex(seed, (54, 56, 56))
    for y in range(4, N, 6):
        for x in range(3, N, 6):
            t.c[y, x] = (24, 24, 24)
    rect(t, 0, 0, N, 3, (120, 122, 122), 1.0, 0.5)
    rect(t, 0, N - 3, N, N, (120, 122, 122), 1.0, 0.5)
    return age(t, 0.5)


def floor(kind, seed):
    if kind == "checker":
        t = tex(seed, (30, 30, 32))
        for y in range(0, N, 16):
            for x in range(0, N, 16):
                if (x // 16 + y // 16) % 2 == 0:
                    rect(t, x, y, x + 16, y + 16, (200, 196, 184))
        for k in range(0, N, 16):
            t.c[k] *= 0.8
            t.c[:, k] *= 0.8
        t.smooth[:] = 0.6
        t.grime(0.7, (60, 50, 38))
        return t
    if kind == "lino":
        t = tex(seed, (168, 164, 150))
        t.vary(0.05)
        t.speckle(0.2, (110, 106, 96), 0.4)
        for k in range(0, N, 32):
            t.c[k] *= 0.85
            t.c[:, k] *= 0.85
        t.smooth[:] = 0.55
        t.grime(0.75, (70, 60, 46))
        return t
    t = tex(seed, (110, 80, 52))  # boards
    t.vary(0.1, ((2, 1.0), (32, 0.7), (64, 0.4)))
    for y in range(0, N, 8):
        t.c[y] *= 0.5
        t.h[y] = 0.55
        off = (y // 8) * 23 % N
        t.c[y:y + 8, off] *= 0.55
    t.smooth[:] = 0.35
    t.grime(0.6, (50, 40, 30))
    return t


def produce(seed=500):
    """Produce bins seen from above: crates of fruit and veg, most of it gone soft and black."""
    t = tex(seed, (96, 70, 44))
    rng = t.rng
    for bx in (0, 32):
        for by in (0, 32):
            rect(t, bx + 2, by + 2, bx + 30, by + 30, (40, 30, 20), 0.6)
            col = np.array([(170, 40, 30), (200, 140, 40), (90, 130, 40), (120, 90, 50), (190, 170, 60)][rng.integers(5)], np.float32)
            for _ in range(28):
                x, y = rng.integers(bx + 3, bx + 27), rng.integers(by + 3, by + 27)
                rot = rng.random() < 0.45
                c = col * 0.35 + np.array((30, 28, 20)) if rot else col * (0.8 + 0.3 * rng.random())
                rect(t, x, y, x + 4, y + 4, c, 0.85, 0.5)
                t.c[y, x:x + 4] *= 1.25
    return age(t, 0.4)


def vending(top, seed):
    """A drinks machine: lit panel (dead now), rows of cans behind glass, coin slot and flap."""
    t = tex(seed, (150, 30, 30))
    t.vary(0.05)
    if top:
        rect(t, 4, 4, 44, N, (24, 26, 28), 0.85, 0.9)
        for base in (18, 34, 50, 64):
            x = 6
            while x < 40:
                x += item(t, t.rng, "cans", x, base - 2, 12)
        t.c[4:N, 4:44] = t.c[4:N, 4:44] * 0.7 + 25
        for y in range(8, 40, 7):
            rect(t, 48, y, 58, y + 4, (210, 206, 196), 1.0, 0.5)
    else:
        rect(t, 4, 0, 44, 30, (24, 26, 28), 0.85, 0.9)
        rect(t, 8, 40, 40, 54, (20, 20, 20), 0.7)  # the flap
        rect(t, 48, 6, 56, 16, (180, 180, 176), 1.0)
        rect(t, 50, 9, 54, 13, (30, 30, 30))
    return age(t, 0.4, 0.3)


def cart(seed=510):
    """Wire basket of a shopping cart, side on."""
    t = tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    for x in range(0, N, 4):
        t.c[8:44, x] = (170, 172, 172)
        t.alpha[8:44, x] = 255
    for y in range(8, 45, 6):
        t.c[y, :] = (170, 172, 172)
        t.alpha[y, :] = 255
    rect(t, 0, 44, N, 47, (60, 60, 62))
    t.alpha[44:47] = 255
    for x in (6, N - 10):
        rect(t, x, 54, x + 6, 62, (30, 30, 30))
        t.alpha[54:62, x:x + 6] = 255
        rect(t, x + 2, 46, x + 4, 54, (150, 150, 150))
        t.alpha[46:54, x + 2:x + 4] = 255
    rect(t, 0, 4, N, 7, (170, 30, 30))  # the plastic handle
    t.alpha[4:7] = 255
    t.f0[:] = 200
    return t


def coffee(seed=520):
    """Back counter with a coffee machine: two burners, a smashed pot, a stack of cups."""
    t = tex(seed, (40, 40, 42))
    rect(t, 6, 4, 58, 20, (30, 30, 32), 0.95, 0.5)
    rect(t, 10, 8, 30, 12, (200, 60, 40))
    for x in (8, 34):
        rect(t, x, 30, x + 20, 34, (20, 20, 20), 1.0)
    rect(t, 10, 36, 26, 56, (60, 44, 30), 0.85, 0.9)  # the one pot left
    rect(t, 10, 36, 26, 40, (30, 30, 30))
    rect(t, 44, 38, 52, 60, (226, 222, 210), 0.9)
    return age(t, 0.5)


def pies(seed=530):
    t = case_glass("guns", seed)
    rng = np.random.default_rng(seed)
    t.c[:] = (190, 184, 170)
    yy, xx = np.mgrid[0:N, 0:N]
    for cx, cy in ((16, 18), (46, 18), (16, 46), (46, 46)):
        r = np.hypot(xx - cx, yy - cy)
        t.c[r < 12] = (170, 120, 60)
        t.c[r < 9] = (110, 40, 40) if rng.random() < 0.5 else (200, 170, 90)
        t.c[(r < 9) & (np.abs(xx - cx) < 1)] = (170, 120, 60)
        if rng.random() < 0.6:  # mould
            t.c[(r < 9) & (np.sin(xx * 0.9 + yy * 1.3) > 0.4)] = (90, 110, 70)
    t.c = t.c * 0.75 + 30
    return age(t, 0.3)


def lumber(seed=540):
    t = tex(seed, (60, 60, 62))
    rng = t.rng
    for y in range(2, N - 4, 5):
        if rng.random() < 0.15:
            continue
        rect(t, 0, y, N, y + 4, (170, 140, 96), 0.9, 0.2)
        t.c[y + 3, :] *= 0.7
        for x in range(0, N, int(rng.uniform(10, 20))):
            t.c[y + 1, x] = (120, 90, 60)
    return age(t, 0.4)


def bin_top(seed=550):
    """A thrift bin heaped with shoes and bags."""
    t = tex(seed, (70, 60, 50))
    rng = t.rng
    rect(t, 0, 0, N, 4, (110, 110, 110), 1.0)
    for _ in range(26):
        x, y = rng.integers(2, N - 14), rng.integers(4, N - 8)
        col = np.array([(40, 30, 24), (120, 80, 50), (30, 30, 34), (150, 140, 120), (100, 30, 30)][rng.integers(5)], np.float32)
        rect(t, x, y, x + int(rng.uniform(6, 14)), y + int(rng.uniform(4, 8)), col * (0.8 + 0.3 * rng.random()), 0.85, 0.3)
    return age(t, 0.4)


def chair(seed=570):
    t = tex(seed, (60, 90, 130))
    t.vary(0.06)
    t.smooth[:] = 0.4
    return age(t, 0.4)


# ---------------------------------------------------------------- registry

def textures():
    out = {f"store_shelf_{k}": (lambda k=k, i=i: shelf(k, 400 + i)) for i, k in enumerate(SHELF_STOCK)}
    out.update({
        "store_cooler": lambda: cooler(False, 420),
        "store_cooler_top": lambda: cooler(True, 421),
        "store_appliance": lambda: appliance_side(),
        "store_counter_front": lambda: counter_front(),
        "store_counter_top": lambda: counter_top(),
        "store_register": lambda: register(),
        "store_case_guns": lambda: case_glass("guns", 430),
        "store_case_jewelry": lambda: case_glass("jewelry", 431),
        "store_case_base": lambda: case_base(),
        "store_gun_rack": lambda: wall_board("gun_rack", 440),
        "store_pawn_wall": lambda: wall_board("pawn_wall", 441),
        "store_pegboard": lambda: wall_board("pegboard", 442),
        "store_menu": lambda: wall_board("menu", 443),
        "store_tile": lambda: wall_board("tile", 444),
        "store_washer": lambda: washer(False, 450),
        "store_dryer": lambda: washer(True, 451),
        "store_grill": lambda: grill(False, 460),
        "store_grill_top": lambda: grill(True, 461),
        "store_oven": lambda: pizza_oven(),
        "store_safe": lambda: safe(True, 470),
        "store_safe_side": lambda: safe(False, 471),
        "store_teller_glass": lambda: teller_glass(),
        "store_clothes": lambda: clothes(),
        "store_vinyl": lambda: vinyl(),
        "store_table_top": lambda: table_top(),
        "store_spill": lambda: spill(),
        "store_shelf_fallen": lambda: shelf_fallen(),
        "store_paint_can": lambda: paint_can(),
        "store_produce": lambda: produce(),
        "store_vending": lambda: vending(False, 511),
        "store_vending_top": lambda: vending(True, 512),
        "store_cart": lambda: cart(),
        "store_coffee": lambda: coffee(),
        "store_case_pies": lambda: pies(),
        "store_lumber": lambda: lumber(),
        "store_bin": lambda: bin_top(),
        "store_plastic": lambda: chair(),
        "floor_checker": lambda: floor("checker", 480),
        "floor_lino": lambda: floor("lino", 481),
        "floor_boards": lambda: floor("boards", 482),
    })
    return out


def paint_can(seed=490):
    t = tex(seed, (150, 150, 146))
    rng = t.rng
    x = 0
    while x < N - 8:
        x += item(t, rng, "paint", x, N - 2, 12)
    return age(t, 0.4)


def blocks():
    steel = "streetlight_metal"
    out = {}
    for k in SHELF_STOCK:
        # steel end panels hide the stock's edges; the stock shows front and back
        out[f"store_shelf_{k}"] = {"model": "boxes", "rotate": "facing", "boxes": [
            [1, 0, 2, 15, 16, 14, f"store_shelf_{k}"], [0, 0, 2, 1, 16, 14, steel], [15, 0, 2, 16, 16, 14, steel]]}
        # the upper block of a two-high bay: the same look, its own name so the prop packs'
        # two-block shelves (tools/props) can tell the halves apart
        out[f"store_shelf_{k}_top"] = out[f"store_shelf_{k}"]
    out.update({
        "store_cooler": {"tex": {"north": "store_cooler", "side": "store_appliance"}, "rotate": "facing"},
        "store_cooler_top": {"tex": {"north": "store_cooler_top", "side": "store_appliance"}, "rotate": "facing"},
        "store_counter": {"model": "boxes", "rotate": "facing", "boxes": [
            [0, 0, 0, 16, 14, 16, "store_counter_front"], [0, 14, 0, 16, 15, 16, "store_counter_top"]]},
        "store_register": {"model": "boxes", "rotate": "facing", "boxes": [
            [0, 0, 0, 16, 14, 16, "store_counter_front"], [0, 14, 0, 16, 15, 16, "store_counter_top"],
            [4, 15, 5, 12, 19, 12, "store_register"]]},
        "store_case_guns": {"model": "boxes", "rotate": "facing", "boxes": [
            [0, 0, 0, 16, 10, 16, "store_case_base"], [0, 10, 0, 16, 15, 16, "store_case_guns"]]},
        "store_case_jewelry": {"model": "boxes", "rotate": "facing", "boxes": [
            [0, 0, 0, 16, 10, 16, "store_case_base"], [0, 10, 0, 16, 15, 16, "store_case_jewelry"]]},
        "store_washer": {"tex": {"north": "store_washer", "side": "store_appliance"}, "rotate": "facing"},
        "store_dryer": {"tex": {"north": "store_dryer", "side": "store_appliance"}, "rotate": "facing"},
        "store_grill": {"tex": {"north": "store_grill", "up": "store_grill_top", "side": "store_appliance"}, "rotate": "facing", "height": 15},
        "store_oven": {"tex": {"north": "store_oven", "side": "store_appliance"}, "rotate": "facing"},
        "store_safe": {"model": "boxes", "rotate": "facing", "boxes": [[2, 0, 2, 14, 14, 14, "store_safe_side"], [2, 0, 1.5, 14, 14, 2, "store_safe"]]},
        "store_teller_glass": {"model": "boxes", "rotate": "axis", "render": "translucent", "boxes": [[0, 0, 7, 16, 16, 9, "store_teller_glass"]]},
        "store_clothes_rack": {"model": "boxes", "rotate": "facing", "render": "cutout", "boxes": [
            [0, 15, 7.5, 16, 16, 8.5, steel], [0, 0, 7.5, 1, 15, 8.5, steel], [15, 0, 7.5, 16, 15, 8.5, steel],
            [1, 3, 5, 15, 15, 11, "store_clothes"]]},
        "store_table": {"model": "boxes", "boxes": [[0, 12, 0, 16, 13, 16, "store_table_top"], [7, 0, 7, 9, 12, 9, steel], [4, 0, 4, 12, 0.5, 12, steel]]},
        "store_stool": {"model": "boxes", "boxes": [[4, 10, 4, 12, 12, 12, "store_vinyl"], [7.5, 0, 7.5, 8.5, 10, 8.5, steel], [5, 0, 5, 11, 0.5, 11, steel]]},
        "store_booth": {"model": "boxes", "rotate": "facing", "boxes": [[0, 0, 3, 16, 7, 13, "store_vinyl"], [0, 0, 13, 16, 16, 16, "store_vinyl"]]},
        "store_paint_cans": {"model": "boxes", "boxes": [[1, 0, 1, 8, 7, 8, "store_paint_can"], [9, 0, 2, 15, 7, 8, "store_paint_can"],
                                                         [3, 0, 9, 10, 7, 15, "store_paint_can"], [4, 7, 3, 11, 14, 10, "store_paint_can"]]},
        "store_spill": {"model": "boxes", "render": "cutout", "boxes": [[0, 0, 0, 16, 0.5, 16, "store_spill"]]},
        "store_shelf_fallen": {"model": "boxes", "rotate": "facing", "boxes": [[0, 0, 1, 16, 4, 15, "store_shelf_fallen"]]},
    })
    out.update({
        "store_produce": {"model": "boxes", "rotate": "facing", "boxes": [[0, 0, 0, 16, 11, 16, "crate_wood"], [0.5, 11, 0.5, 15.5, 11.5, 15.5, "store_produce"]]},
        "store_vending": {"tex": {"north": "store_vending", "side": "store_appliance"}, "rotate": "facing"},
        "store_vending_top": {"tex": {"north": "store_vending_top", "side": "store_appliance"}, "rotate": "facing"},
        "store_cart": {"model": "boxes", "rotate": "facing", "render": "cutout", "boxes": [[3, 0, 1, 13, 14, 15, "store_cart"]]},
        "store_coffee": {"model": "boxes", "rotate": "facing", "boxes": [
            [0, 0, 0, 16, 14, 16, "store_counter_front"], [0, 14, 0, 16, 15, 16, "store_counter_top"], [2, 15, 6, 14, 24, 14, "store_coffee"]]},
        "store_case_pies": {"model": "boxes", "rotate": "facing", "boxes": [
            [0, 0, 0, 16, 10, 16, "store_case_base"], [0, 10, 0, 16, 15, 16, "store_case_pies"]]},
        "store_lumber": {"model": "boxes", "rotate": "facing", "boxes": [[0, 0, 8, 16, 16, 16, "store_lumber"]]},
        "store_bin": {"model": "boxes", "boxes": [[1, 0, 1, 15, 10, 15, "store_case_base"], [1.5, 10, 1.5, 14.5, 10.5, 14.5, "store_bin"]]},
        "store_chair": {"model": "boxes", "rotate": "facing", "boxes": [
            [3, 7, 3, 13, 8, 13, "store_plastic"], [3, 8, 12, 13, 16, 13, "store_plastic"],
            [3, 0, 3, 4, 7, 4, "streetlight_metal"], [12, 0, 3, 13, 7, 4, "streetlight_metal"],
            [3, 0, 12, 4, 7, 13, "streetlight_metal"], [12, 0, 12, 13, 7, 13, "streetlight_metal"]]},
    })
    for kind in ("gun_rack", "pawn_wall", "pegboard", "menu", "tile"):
        out[f"store_{kind}"] = {"model": "boxes", "rotate": "facing", "boxes": [[0, 0, 0, 16, 16, 1, f"store_{kind}"]]}
    for kind in ("checker", "lino", "boards"):
        out[f"floor_{kind}"] = {"tex": f"floor_{kind}"}
    return out


# Searchable fittings and the loot table each rolls when nobody set one (see tools/city/shop_loot.py):
# the city generator names the shop's own table on everything it places in a shop.
LOOT = {**{f"store_shelf_{k}": k for k in ("grocery", "pharmacy", "liquor", "hardware", "thrift", "pawn")},
        "store_shelf_ammo": "guns", "store_shelf_empty": "scraps",
        "store_cooler": "drinks", "store_vending": "drinks", "store_counter": "storage", "store_register": "till",
        "store_safe": "till", "store_case_guns": "guns", "store_case_jewelry": "pawn", "store_case_pies": "kitchen",
        "store_gun_rack": "guns", "store_pawn_wall": "pawn", "store_pegboard": "hardware", "store_washer": "clothes",
        "store_dryer": "clothes", "store_clothes_rack": "clothes", "store_bin": "clothes", "store_oven": "kitchen",
        "store_grill": "kitchen", "store_coffee": "kitchen", "store_produce": "grocery", "store_lumber": "hardware",
        "store_paint_cans": "hardware", "crate": "storage", "cardboard_box": "storage", "filing_cabinet": "office"}
LOOT.update({f"{k}_top": v for k, v in LOOT.items() if k.startswith("store_shelf_")})


def main():
    path = HERE / "blocks.json"
    text = path.read_text()
    data = json.loads(text)
    shop = blocks()
    kept = {}
    for k, v in data["blocks"].items():   # shop entries are replaced where they stand
        if not k.startswith(("store_", "floor_checker", "floor_lino", "floor_boards")):
            kept[k] = v
        elif k in shop:
            kept[k] = shop[k]
            if f"{k}_top" in shop:
                kept[f"{k}_top"] = shop[f"{k}_top"]
    kept.update({k: v for k, v in shop.items() if k not in kept})
    for name, spec in kept.items():
        if name.startswith("home_"):  # the house kit sets its own (tools/city/furniture.py)
            continue
        spec.pop("loot", None)
        if name in LOOT:
            spec["loot"] = LOOT[name]
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")   # the layout the other kit tools write
    print(f"{len(blocks())} shop blocks in {path}")


if __name__ == "__main__":
    main()
