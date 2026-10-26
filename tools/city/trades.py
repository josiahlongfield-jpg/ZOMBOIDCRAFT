#!/usr/bin/env python3
"""Fittings for the main-street trades that aren't a shop of shelves, after small-town Kentucky:
the tavern's bar with its brass foot rail and tap tower, the back bar of bottles under a brewery
mirror, a pool table gone to mould, a dead jukebox, a dartboard and a neon sign in the window;
the barber's porcelain chairs facing a run of stations; the bank's painted vault door, walls of
deposit boxes (pried open, most of them), a queue of brass posts and velvet rope, the check desk
and the rates board; the hotel's key rack and luggage cart; the realty office's listings and its
water cooler; the VFW post's flag, honor roll, banquet tables and folding chairs; and the
decorator's stepladder and drop cloths in a shop that was never re-let.

    python3 tools/city/trades.py        rewrites the trade_ entries in blocks.json

photo_textures.py draws the textures (trades.register()); world/county/Trades.java fits the
rooms out. Pieces follow the house kit (tools/city/furniture.py): the front is the north side and
the facing is the way a piece looks into the room; wall-hung pieces face the wall they hang on.
Pieces of two blocks: the pool table and the flag run along x (_l at the low x facing north), the
jukebox and the vault door stack (_top above).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import (BRASS, SANS, area, blob_mask, disc, edges, knob, paste, plain, stain, sunk,  # noqa: E402
                       text_mask, wear, wood)

N = P.N
HERE = Path(__file__).parent
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
THIN = "/usr/share/fonts/truetype/freefont/FreeSans.ttf"


# ---------------------------------------------------------------- the tavern

def bar_front(seed):
    """The bar's customer side (rows 8-64 show): two raised panels in dark varnish, the moulding
    under the top, a kick plate scuffed pale by forty years of boots, a drink run down it."""
    t = wood(seed, "dark", rot=True)
    t.smooth[:] = 0.5
    rng = t.rng
    area(t, 0, 8, N, 12, mul=1.2, dh=0.15)
    area(t, 0, 12, N, 13, mul=0.55)
    for x0 in (3, 35):
        edges(t, x0, 16, x0 + 26, 52, 2)
        area(t, x0 + 4, 20, x0 + 22, 48, mul=1.1, dh=0.1)
        sunk(t, x0 + 4, 20, x0 + 22, 48, 1)
    area(t, 0, 54, N, N, mul=0.6, dh=-0.05)
    area(t, 0, 54, N, 55, mul=1.5)
    for _ in range(40):
        x, y = int(rng.uniform(0, N - 6)), int(rng.uniform(55, N - 1))
        area(t, x, y, x + int(rng.uniform(2, 7)), y + 1, mul=1.45)
    F.drips(t, 6, 58, 12, 3, (8, 30), col=(54, 34, 18))
    stain(t, rng.uniform(10, 54), 30, 6, (40, 26, 14), 0.3)
    return wear(t, 0.3, 10)


def bar_top(seed):
    """The varnished top: glass rings, a cigarette burn on the edge, a coaster, a sticky spill."""
    t = wood(seed, "dark")
    t.smooth[:] = 0.72
    t.f0[:] = 20
    rng = t.rng
    for _ in range(9):
        disc(t, rng.uniform(4, 60), rng.uniform(6, 58), rng.uniform(3, 5), mul=0.78, ring=1)
    disc(t, 44, 22, 5, (196, 176, 140), smooth=0.1)
    disc(t, 44, 22, 3.5, (160, 40, 34), ring=1)
    for x in (8, 30, 51):
        area(t, x, 2, x + 3, 4, (20, 14, 10), smooth=0.1)
    m = blob_mask(rng, 20, 40, 9)
    t.smooth = np.maximum(t.smooth, m * 0.95)
    t.mix(m * 0.35, (90, 60, 20), 1.0)
    return wear(t, 0.25, 18)


def brass(seed):
    t = plain(seed, BRASS, 0.07, 0.68)
    t.f0[:] = 230
    t.c *= (0.9 + 0.2 * np.sin(np.arange(N) * 0.9))[None, :, None]
    return wear(t, 0.35, 0, col=(58, 70, 44), specks=0.03)


def backbar_front(seed):
    """The back bar's cupboard doors (rows 24-64): two doors with sunk panels and brass knobs."""
    t = wood(seed, "dark", rot=True)
    t.smooth[:] = 0.45
    area(t, 0, 24, N, 26, mul=1.25, dh=0.1)
    for x0, kx in ((2, 27), (33, 37)):
        F.gap(t, x0, 27, x0 + 29, 62)
        sunk(t, x0 + 4, 31, x0 + 25, 58)
        knob(t, kx, 44)
    return wear(t, 0.32, 8)


BOTTLES = {"amber": ((120, 66, 18), (226, 214, 180), (150, 30, 24)), "green": ((36, 78, 40), (232, 226, 206), (40, 40, 44)),
           "clear": ((168, 176, 168), (30, 30, 32), (196, 160, 70))}


def glass_bottle(seed, kind):
    """One bottle's glass (rows 0-26 show): a glint down it, the label low on the body."""
    glass, label, band = BOTTLES[kind]
    t = plain(seed, glass, 0.05, 0.92)
    t.f0[:] = 40
    for x in range(1, N, 6):
        area(t, x, 0, x + 1, N, mul=1.7)
    area(t, 0, 12, N, 22, label, smooth=0.2, f0=4)
    area(t, 0, 15, N, 17, band)
    area(t, 0, 0, N, 3, (44, 40, 34), smooth=0.4)  # the cap
    return wear(t, 0.25, 0)


def bar_mirror(seed, logo):
    """The long mirror over the back bar, bevel-etched; one pane carries the brewery's name in
    gilt, half flaked away."""
    t = F.mirror(seed)
    area(t, 2, 6, N - 2, 7, mul=1.3)
    area(t, 2, 57, N - 2, 58, mul=1.3)
    if logo:
        m = text_mask("RIVER CITY|LAGER", SERIF, 9, 60, 30, seed=seed, spacing=1.1)
        flake = np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.6))) * 2 + 1.1, 0, 1)
        paste(t, m * flake[17:47, 2:62], 2, 17, (196, 160, 74), 0.95, smooth=0.6)
        disc(t, 32, 13, 3, (196, 160, 74), ring=1)
        area(t, 14, 47, 50, 48, (196, 160, 74))
    return t


def felt(seed, cushion=False):
    """The cloth: green baize worn pale where the break shot goes, blue chalk, mould blooming
    grey across it since the roof leaked, a burn and a tear."""
    t = plain(seed, (34, 92, 54) if not cushion else (28, 78, 46), 0.09, 0.08)
    rng = t.rng
    t.c *= (0.94 + 0.08 * P.fbm(rng, ((32, 1.0),)))[..., None]
    if not cushion:
        t.mix(blob_mask(rng, 34, 30, 16, 0.6) * 0.25, (110, 140, 100), 1.0)
        for _ in range(3):
            t.mix(blob_mask(rng, rng.uniform(0, N), rng.uniform(0, N), 2.5) * 0.7, (70, 110, 170), 1.0)
        disc(t, 50, 12, 1.6, (30, 22, 16))
        for i in range(9):
            area(t, 12 + i, 48 + i // 3, 13 + i, 49 + i // 3, (18, 30, 20), dh=-0.3)
    for _ in range(5):
        m = blob_mask(rng, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(4, 11), 0.8)
        t.mix(m * 0.5, (150, 156, 136), 1.0)
        t.speckle(0.01, (196, 196, 180), 0.5)
    return wear(t, 0.25, 0, specks=0.02)


def pool_wood(seed, sights=True):
    """Mahogany for the rails and the apron; the rails' tops have pearl sights let in (three a half
    on each long rail at cols 16/32/48 rows 2-4 and 59-61; on the ends at rows 16/32/48)."""
    t = wood(seed, "walnut")
    t.c = t.c * np.array((1.08, 0.86, 0.8), np.float32)
    t.smooth[:] = 0.55
    for c in (16, 32, 48) if sights else ():
        for r in (2, 59):
            area(t, c - 1, r, c + 1, r + 2, (226, 222, 206), smooth=0.8)
            area(t, r, c - 1, r + 2, c + 1, (226, 222, 206), smooth=0.8)
    return wear(t, 0.3, 12)


def ball(seed, rgb, stripe=False):
    t = plain(seed, (226, 220, 204) if stripe else rgb, 0.03, 0.85)
    t.f0[:] = 30
    if stripe:
        area(t, 0, 20, N, 44, rgb)
    disc(t, 22, 22, 6, mul=1.25, soft=6)
    return wear(t, 0.2, 0)


def jukebox(seed):
    """A fifties jukebox's front (cols 4-60): bubble-tube pilasters in amber and red each side,
    the record window with a disc on the deck, the title strips and push buttons, a coin slot,
    and the chrome speaker grille; long dead, dusty, a crack across the glass."""
    t = wood(seed, "walnut", rot=True)
    t.smooth[:] = 0.5
    rng = t.rng
    for x0 in (4, 52):
        for y in range(N):
            k = y / N
            area(t, x0, y, x0 + 8, y + 1, (200 - 40 * k, 120 - 80 * k, 30 + 10 * k), smooth=0.8, f0=30)
        area(t, x0 + 3, 0, x0 + 5, N, mul=1.35)
        for _ in range(10):
            disc(t, x0 + 4, rng.uniform(0, N), 0.8, (240, 220, 160))
        area(t, x0, 0, x0 + 1, N, (206, 208, 210), smooth=0.8, f0=230)
        area(t, x0 + 7, 0, x0 + 8, N, (150, 152, 154), smooth=0.8, f0=230)
    area(t, 14, 4, 50, 29, (22, 24, 26), smooth=0.92, f0=40)
    disc(t, 32, 17, 9, (16, 16, 16), smooth=0.8)
    disc(t, 32, 17, 3, (170, 40, 30))
    disc(t, 32, 17, 7, mul=1.4, ring=1)
    for i in range(12):
        area(t, 42 - i // 2, 8 + i, 44 - i // 2, 9 + i, (180, 182, 184), f0=230)
    for i in range(30):  # the reflection, and the crack across it
        area(t, 16 + i, 27 - i // 2, 17 + i, 28 - i // 2, mul=1.4)
        if i % 3:
            area(t, 18 + i, 9 + int(4 * np.sin(i * 0.5)), 19 + i, 10 + int(4 * np.sin(i * 0.5)), (190, 196, 198))
    edges(t, 13, 3, 51, 30, 1, light=1.6, dark=0.5)
    for col in range(5):
        for row in range(2):
            x, y = 15 + col * 7, 31 + row * 4
            area(t, x, y, x + 6, y + 3, (222, 210, 170))
            area(t, x + 1, y + 1, x + 5, y + 2, (90, 70, 60))
    for i in range(10):
        area(t, 15 + i * 3.5, 40, 17 + i * 3.5, 42, (226, 220, 196) if i % 2 else (180, 40, 30), smooth=0.7)
    area(t, 14, 43, 50, 45, (206, 208, 210), smooth=0.85, f0=230)
    area(t, 14, 46, 50, 62, (36, 30, 26), smooth=0.2)
    for x in range(15, 50, 3):
        area(t, x, 46, x + 1, 62, (190, 192, 194), smooth=0.85, f0=230)
    area(t, 44, 31, 49, 38, (190, 192, 194), smooth=0.8, f0=230)
    area(t, 46, 32, 47, 37, (20, 20, 20))
    return wear(t, 0.4, 10)


def jukebox_top(seed):
    """The arch over the jukebox, step by step from the bottom: amber, orange, red plastic, each
    behind a chrome rim and ribbed."""
    t = plain(seed, (200, 140, 40), 0.04, 0.8)
    t.f0[:] = 30
    for y0, y1, col in ((28, 36, (170, 40, 30)), (36, 48, (210, 110, 30)), (48, N, (214, 160, 50))):
        area(t, 0, y0, N, y1, col)
        area(t, 0, y0, N, y0 + 1, (210, 212, 214), smooth=0.85, f0=230)
        for x in range(0, N, 4):
            area(t, x, y0 + 2, x + 1, y1, mul=0.82)
    area(t, 0, 0, N, 28, (206, 208, 210), smooth=0.85, f0=230)
    return wear(t, 0.4, 6)


def dartboard(seed):
    """A bristle dartboard (cols and rows 12-52, round): black and cream singles, red and green
    doubles and trebles, the wire, the bull; holes all over the twenty."""
    t = P.Tex(seed, (24, 22, 20))
    t.smooth[:] = 0.1
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N]
    dx, dy = xx - 31.5, yy - 31.5
    r = np.hypot(dx, dy)
    seg = ((np.degrees(np.arctan2(dy, dx)) + 99) % 360 // 18).astype(int)
    even = seg % 2 == 0
    t.c[:] = np.where(even[..., None], np.array((22, 20, 18), np.float32), np.array((214, 198, 160), np.float32))
    ring = ((r > 17) & (r <= 19)) | ((r > 10) & (r <= 11.6))
    t.c[ring] = np.where(even[ring][..., None], np.array((160, 30, 28), np.float32), np.array((30, 104, 50), np.float32))
    t.c[r <= 3.2] = (30, 104, 50)
    t.c[r <= 1.6] = (160, 30, 28)
    t.c[(r > 19) & (r <= 20)] = (16, 16, 16)
    wire = (np.abs(((np.degrees(np.arctan2(dy, dx)) + 99) % 18)) < 1.2) & (r > 3.2) & (r < 19)
    t.c[wire] = (170, 170, 168)
    t.alpha[r <= 20] = 255
    t.speckle(0.06, (90, 80, 60), 0.6)
    return wear(t, 0.2, 0, specks=0.0)


def neon(seed, on):
    """A window neon, COLD BEER over a blue frame (cols 4-60, rows 12-52 show); the rest is the
    clear acrylic it's hung from."""
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    t.smooth[:] = 0.8
    m = text_mask("COLD|BEER", THIN, 12, 52, 34, seed=seed, spacing=0.95)
    word = (255, 96, 60) if on else (120, 66, 54)
    frame = (80, 160, 255) if on else (54, 70, 96)
    hit = m > 0.35
    sub = (slice(15, 49), slice(6, 58))
    t.c[sub][hit] = word
    t.alpha[sub][hit] = 255
    for x0, y0, x1, y1 in ((5, 13, 59, 14), (5, 50, 59, 51), (5, 13, 6, 51), (58, 13, 59, 51)):
        area(t, x0, y0, x1, y1, frame, alpha=255)
    if on:
        t.emit[t.alpha > 0] = 1.0
    return t


# ---------------------------------------------------------------- the barber

def porcelain(seed):
    t = F.enamel(seed, (228, 224, 212))
    t.f0[:] = 40
    t.smooth[:] = 0.75
    return wear(t, 0.35, 6, col=(70, 60, 44))


def oxblood(seed):
    t = F.leather(seed, (118, 34, 30))
    rng = t.rng
    for _ in range(4):  # splits, the stuffing showing
        x, y = rng.integers(6, N - 12, 2)
        area(t, x, y, x + int(rng.uniform(4, 10)), y + 2, (196, 180, 130), dh=-0.2, smooth=0.1)
    return t


def station_front(seed):
    """Under the barber's counter (rows 8-64): a bank of drawers in pale green laminate with
    chrome pulls, and a towel cupboard."""
    t = plain(seed, (176, 196, 178), 0.04, 0.5)
    for y0 in (10, 22, 34):
        F.gap(t, 2, y0, 30, y0 + 11)
        F.bar(t, 12, y0 + 5, 20, y0 + 6, F.CHROME)
    F.gap(t, 33, 10, 62, 60)
    F.bar(t, 36, 30, 37, 40, F.CHROME)
    area(t, 0, 60, N, N, (40, 38, 36))
    return wear(t, 0.3, 6)


def station_top(seed):
    """The counter top: pink laminate under hair clippings, combs, scissors, a strop's scuffs."""
    t = plain(seed, (204, 170, 168), 0.04, 0.55)
    rng = t.rng
    for _ in range(140):
        x, y = rng.uniform(0, N, 2)
        a = rng.uniform(0, np.pi)
        for i in range(int(rng.integers(2, 5))):
            xi, yi = int(x + np.cos(a) * i) % N, int(y + np.sin(a) * i) % N
            t.c[yi, xi] = (60, 44, 32) if rng.random() < 0.7 else (150, 140, 128)
    area(t, 6, 40, 26, 43, (30, 30, 32))
    for x in range(7, 26, 2):
        area(t, x, 43, x + 1, 46, (30, 30, 32))
    area(t, 34, 12, 54, 13, (190, 192, 194), f0=230)
    area(t, 34, 16, 50, 17, (190, 192, 194), f0=230)
    disc(t, 33, 12, 2, (190, 192, 194), ring=1)
    disc(t, 33, 17, 2, (190, 192, 194), ring=1)
    return wear(t, 0.35, 10)


def disinfectant(seed):
    """The tall jar of blue disinfectant with combs standing in it (rows 0-20 show)."""
    t = plain(seed, (40, 110, 170), 0.04, 0.9)
    t.f0[:] = 40
    area(t, 0, 0, N, 3, (190, 192, 194), smooth=0.8, f0=230)
    for x in range(4, N, 9):
        area(t, x, 3, x + 2, 16, (20, 20, 24))
    area(t, 0, 3, N, 6, mul=1.3)
    return wear(t, 0.2, 0)


def hair(seed):
    """Clippings across the floor round a chair, dark and grey."""
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    rng = t.rng
    for _ in range(9):
        cx, cy = rng.uniform(0, N, 2)
        for _ in range(int(rng.integers(20, 50))):
            x, y = cx + rng.normal(0, 4), cy + rng.normal(0, 4)
            a = rng.uniform(0, np.pi)
            col = (54, 40, 30) if rng.random() < 0.6 else (130, 124, 116) if rng.random() < 0.5 else (150, 110, 60)
            for i in range(int(rng.integers(2, 5))):
                xi, yi = int(x + np.cos(a) * i) % N, int(y + np.sin(a) * i) % N
                t.c[yi, xi] = col
                t.alpha[yi, xi] = 255
    return t


# ---------------------------------------------------------------- the bank

def vault_face(seed, top):
    """The outside of an old square vault door, painted: dark green enamel in a gilt pinstripe
    border, the maker's plate and a painted landscape up top, the combination dials and the
    spoked handle below; scratched round the handle."""
    t = plain(seed, (34, 52, 42), 0.05, 0.6)
    t.f0[:] = 30
    rng = t.rng
    gold = (196, 160, 74)
    if top:
        area(t, 4, 4, N - 4, 5, gold)
        area(t, 4, 4, 5, N, gold)
        area(t, N - 5, 4, N - 4, N, gold)
        area(t, 12, 12, 52, 38, (120, 150, 170))
        area(t, 12, 28, 52, 38, (64, 96, 56))
        for x in range(12, 52):
            h = int(26 + 4 * np.sin(x * 0.2) + 2 * np.sin(x * 0.7))
            area(t, x, h, x + 1, 38, (70, 104, 60))
        area(t, 34, 22, 40, 30, (150, 60, 44))
        edges(t, 11, 11, 53, 39, 1, light=1.0, dark=1.0)
        area(t, 11, 11, 53, 12, gold)
        area(t, 11, 38, 53, 39, gold)
        area(t, 14, 44, 50, 54, (176, 146, 70), smooth=0.7, f0=230)
        m = text_mask("GRAYHAVEN|SAFE & LOCK", SANS, 4, 34, 9, seed=seed)
        paste(t, m, 15, 45, (60, 44, 20))
        for x in (24, 40):
            disc(t, x, 60, 3.5, (190, 186, 176), f0=230, smooth=0.8)
            disc(t, x, 60, 1.2, (40, 40, 40))
    else:
        area(t, 4, 0, 5, N - 4, gold)
        area(t, N - 5, 0, N - 4, N - 4, gold)
        area(t, 4, N - 5, N - 4, N - 4, gold)
        disc(t, 40, 26, 11, (190, 186, 176), ring=2, f0=230, smooth=0.8)
        for a in np.linspace(0, np.pi, 4, endpoint=False):
            for i in range(-11, 12):
                x, y = 40 + np.cos(a) * i, 26 + np.sin(a) * i
                area(t, x, y, x + 1.5, y + 1.5, (190, 186, 176), f0=230)
        disc(t, 40, 26, 3, (150, 146, 136), f0=230)
        for a in np.linspace(0, 2 * np.pi, 8, endpoint=False):
            disc(t, 40 + np.cos(a) * 11, 26 + np.sin(a) * 11, 1.8, (210, 206, 196), f0=230)
        F.fingers(t, 40, 26, 14, 0.3)
        area(t, 10, 44, 54, 46, gold)
    for _ in range(14):
        x, y = rng.uniform(6, 58, 2)
        area(t, x, y, x + rng.uniform(2, 8), y + 1, (120, 120, 112))
    return wear(t, 0.3, 12)


def vault_back(seed, top):
    """The inside of the door: bare steel, the boltwork's bars and the bolts along the free edge,
    and up top the time lock's three clocks behind their glass."""
    t = F.steel(seed, (150, 152, 150))
    if top:
        area(t, 16, 18, 48, 42, (60, 62, 60))
        for x in (22, 32, 42):
            disc(t, x, 30, 4, (222, 218, 204))
            area(t, x, 27, x + 1, 31, (20, 20, 20))
        edges(t, 15, 17, 49, 43, 1, light=1.4, dark=0.5)
        F.bar(t, 2, 52, 60, 55)
    else:
        for y in (10, 30, 50):
            F.bar(t, 2, y, 54, y + 3)
            area(t, 0, y - 1, 6, y + 5, (200, 202, 202), f0=230, smooth=0.8)
        F.bar(t, 30, 6, 33, 56)
    return wear(t, 0.35, 10)


def vault_edge(seed):
    t = F.steel(seed, (120, 122, 120))
    for y in range(4, N, 10):
        disc(t, 32, y, 3, (200, 202, 202), f0=230, smooth=0.8)
    return wear(t, 0.3, 6)


def deposit(seed, big):
    """A wall of safe deposit boxes: brass-faced doors in rows, each with its two keyholes and a
    number plate; most pried open since, the drawers gone, the doors hanging off one hinge."""
    t = plain(seed, (150, 126, 70), 0.05, 0.6)
    t.f0[:] = 230
    rng = t.rng
    rows = [(0, 8)] * 8 if not big else [(0, 8), (8, 16), (16, 24), (24, 40), (40, 56), (56, 64)]
    cols = 4 if not big else 2
    for k, (a, b) in enumerate(rows):
        y0, y1 = (k * 8, k * 8 + 8) if not big else (a, b)
        for c in range(cols):
            x0, x1 = c * (N // cols), (c + 1) * (N // cols)
            F.gap(t, x0, y0, x1, y1, 0.35)
            if rng.random() < 0.55:
                area(t, x0 + 1, y0 + 1, x1 - 1, y1 - 1, (22, 20, 18), h=0.2, smooth=0.1, f0=10)
                if rng.random() < 0.6:
                    area(t, x0 + 1, y0 + 1, x0 + 3, y1 - 1, (150, 126, 70), f0=230)
                for _ in range(3):
                    sx = int(rng.uniform(x0, x1 - 2))
                    area(t, sx, y0, sx + 2, y0 + 1, (220, 214, 196))
            else:
                area(t, x0 + 2, y0 + 2, x0 + 6, y0 + 4, (210, 200, 170))
                area(t, x1 - 6, y0 + 3, x1 - 5, y0 + 5, (20, 18, 16))
                area(t, x1 - 4, y0 + 3, x1 - 3, y0 + 5, (20, 18, 16))
    return wear(t, 0.35, 8, col=(58, 70, 44))


def check_top(seed):
    """The check desk under its glass: deposit slips, a calendar of rates, a pen on its chain."""
    t = plain(seed, (196, 192, 180), 0.05, 0.85)
    rng = t.rng
    for _ in range(7):
        x, y = rng.uniform(4, 40), rng.uniform(4, 44)
        area(t, x, y, x + 18, y + 10, (228, 222, 200) if rng.random() < 0.6 else (200, 220, 200))
        for ly in range(int(y) + 2, int(y) + 9, 2):
            area(t, x + 2, ly, x + 15, ly + 1, (150, 150, 160))
    area(t, 48, 10, 50, 40, (30, 30, 34))
    for y in range(40, 60, 2):
        area(t, 49 + (y % 4 == 0), y, 50 + (y % 4 == 0), y + 1, (180, 180, 176))
    return wear(t, 0.3, 10)


def rates_board(seed):
    """The rates board: black felt letter board in an oak frame, white letters, gaps where they fell."""
    t = plain(seed, (26, 26, 28), 0.04, 0.1)
    for y in range(6, 58, 2):
        area(t, 4, y, 60, y + 1, mul=0.7)
    m = text_mask("SAVINGS 5.25|CDS    7.50|LOANS  9.75|OPEN 9 TO 3", "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
                  6, 50, 38, seed=seed)
    holes = np.clip(P.fbm(t.rng, ((8, 1.0),)) * 3 + 1.2, 0, 1)
    paste(t, m * holes[9:47, 7:57], 7, 9, (222, 220, 210))
    for x0, y0, x1, y1 in ((4, 4, 60, 7), (4, 49, 60, 52), (4, 4, 7, 52), (57, 4, 60, 52)):
        area(t, x0, y0, x1, y1, (130, 96, 60), h=1.0)
    return wear(t, 0.3, 4)


def velvet(seed):
    t = plain(seed, (124, 22, 32), 0.08, 0.15)
    return wear(t, 0.35, 0, specks=0.02)


# ---------------------------------------------------------------- the hotel, the realty office, the post

def key_rack(seed):
    """Behind the hotel desk: oak pigeonholes, mail in a few, and a brass hook under each with
    its room key on a red fob, most of them gone."""
    t = wood(seed, "oak")
    rng = t.rng
    for row in range(3):
        for col in range(8):
            x0, y0 = col * 8, 8 + row * 16
            area(t, x0 + 1, y0, x0 + 7, y0 + 8, (40, 30, 22), h=0.3)
            if rng.random() < 0.25:
                area(t, x0 + 2, y0 + 3, x0 + 6, y0 + 8, (226, 220, 200))
            area(t, x0 + 3, y0 + 9, x0 + 5, y0 + 10, BRASS, f0=230)
            if rng.random() < 0.45:
                area(t, x0 + 3, y0 + 10, x0 + 4, y0 + 13, (180, 176, 160), f0=230)
                area(t, x0 + 2, y0 + 13, x0 + 6, y0 + 16, (150, 34, 30))
    return wear(t, 0.3, 4)


def suitcase(seed, rgb):
    t = F.leather(seed, rgb)
    area(t, 0, 20, N, 23, (60, 40, 26))
    area(t, 0, 40, N, 43, (60, 40, 26))
    for y in (20, 40):
        area(t, 28, y - 1, 34, y + 4, BRASS, f0=230)
    return t


def listings(seed):
    """The realty office's cork board: listing sheets, each a photo of a house and its price,
    pinned up crooked; one stamped SOLD, some fallen to a single pin."""
    t = plain(seed, (168, 126, 80), 0.12, 0.1)
    rng = t.rng
    t.speckle(0.2, (120, 84, 50), 0.6)
    for k in range(6):
        x0, y0 = 4 + (k % 3) * 20 + int(rng.uniform(-2, 2)), 6 + (k // 3) * 28 + int(rng.uniform(-2, 2))
        area(t, x0, y0, x0 + 16, y0 + 22, (232, 228, 214))
        sky = (120, 150, 176) if rng.random() < 0.6 else (150, 160, 166)
        area(t, x0 + 2, y0 + 2, x0 + 14, y0 + 12, sky)
        wall = [(220, 214, 196), (170, 60, 50), (120, 140, 160), (200, 190, 140)][int(rng.integers(4))]
        area(t, x0 + 4, y0 + 6, x0 + 12, y0 + 12, wall)
        for i in range(5):
            area(t, x0 + 3 + i, y0 + 6 - i // 2, x0 + 13 - i, y0 + 7 - i // 2, (70, 60, 56))
        for ly in (14, 17, 19):
            area(t, x0 + 2, y0 + ly, x0 + 12 - (ly == 19) * 4, y0 + ly + 1, (60, 60, 66))
        disc(t, x0 + 8, y0, 1.2, (200, 30, 30) if k % 2 else (30, 80, 190))
        if k == int(rng.integers(6)):
            for i in range(14):
                area(t, x0 + 1 + i, y0 + 16 - i, x0 + 3 + i, y0 + 19 - i, (190, 30, 28))
    return wear(t, 0.3, 0)


def flag(seed, part):
    """A flag hung on the wall, two blocks wide, faded and water-stained: thirteen stripes and the
    canton of stars on the left half."""
    t = plain(seed, (210, 204, 190), 0.05, 0.15)
    for i in range(13):
        if i % 2 == 0:
            area(t, 0, 8 + i * 48 / 13, N, 8 + (i + 1) * 48 / 13, (150, 40, 44))
    if part == "l":
        area(t, 0, 8, 40, 34, (44, 52, 86))
        for r in range(5):
            for c in range(6):
                disc(t, 4 + c * 6.4 + (r % 2) * 3.2, 11 + r * 5, 0.9, (220, 216, 200))
    t.c *= (0.9 + 0.1 * np.sin(np.arange(N) * 0.4))[None, :, None]  # the folds it hangs in
    stain(t, 40, 40, 14, (120, 96, 60), 0.35)
    return wear(t, 0.3, 0)


def honor_board(seed):
    """The post's honor roll: walnut, a brass header, rows of small brass name plates."""
    t = wood(seed, "walnut")
    area(t, 8, 6, 56, 14, BRASS, f0=230, smooth=0.7)
    m = text_mask("HONOR ROLL", SERIF, 5, 46, 7, seed=seed)
    paste(t, m, 9, 7, (60, 44, 20))
    for r in range(8):
        for c in range(2):
            x, y = 8 + c * 26, 18 + r * 5
            area(t, x, y, x + 22, y + 3, (170, 140, 74), f0=230, smooth=0.7)
            area(t, x + 2, y + 1, x + 4 + (r * 7 + c * 3) % 14, y + 2, (70, 54, 30))
    return wear(t, 0.3, 4)


def cooler_body(seed):
    """A water cooler's front (rows 24-64): beige, two taps' plate, the drip grille."""
    t = F.enamel(seed, (214, 206, 186))
    area(t, 18, 28, 46, 40, (170, 170, 168), f0=230)
    area(t, 20, 42, 44, 46, (60, 60, 60))
    for x in range(21, 44, 2):
        area(t, x, 42, x + 1, 46, (150, 150, 150))
    return wear(t, 0.35, 6)


def cooler_bottle(seed):
    t = plain(seed, (120, 170, 200), 0.04, 0.9)
    t.f0[:] = 40
    t.alpha[:] = 150
    area(t, 0, 0, N, 26, (190, 214, 226), alpha=90)
    for y in (30, 46):
        area(t, 0, y, N, y + 2, mul=0.8, alpha=200)
    return t


def folding_metal(seed):
    t = plain(seed, (150, 136, 112), 0.05, 0.5)
    t.f0[:] = 120
    return wear(t, 0.35, 12, col=(80, 50, 30))


def tablecloth(seed):
    """A banquet table's paper cloth over wood-grain laminate: rings, crumbs, a burn."""
    t = plain(seed, (226, 222, 210), 0.04, 0.2)
    rng = t.rng
    for _ in range(4):
        disc(t, rng.uniform(6, 58), rng.uniform(6, 58), 4, mul=0.82, ring=1)
    stain(t, rng.uniform(10, 54), rng.uniform(10, 54), 8, (140, 110, 70), 0.35)
    t.speckle(0.02, (120, 100, 70), 0.6)
    area(t, 0, 0, N, 2, (160, 120, 80))
    area(t, 0, N - 2, N, N, (160, 120, 80))
    return wear(t, 0.3, 0)


# ---------------------------------------------------------------- the empty shop

def ladder_metal(seed):
    t = F.steel(seed, (180, 182, 180))
    rng = t.rng
    for _ in range(18):
        c = [(220, 214, 196), (150, 170, 150), (196, 170, 160)][int(rng.integers(3))]
        disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(0.8, 2.4), c)
    return wear(t, 0.3, 8)


def drop_cloth(seed):
    """A painter's canvas drop cloth: folds, and paint dripped and trodden across it."""
    t = plain(seed, (196, 184, 160), 0.06, 0.1)
    rng = t.rng
    for y in (14, 38):
        area(t, 0, y, N, y + 2, mul=0.82, dh=-0.1)
        area(t, 0, y + 2, N, y + 3, mul=1.1, dh=0.05)
    for _ in range(26):
        c = [(222, 216, 200), (150, 170, 150), (196, 170, 160), (90, 110, 140)][int(rng.integers(4))]
        disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(0.8, 3), c)
    for _ in range(3):
        x, y = rng.uniform(0, N, 2)
        for i in range(6):
            disc(t, x + i * 4, y + (i % 2) * 2, 1.4, (150, 146, 136))
    return wear(t, 0.35, 0, specks=0.02)


def textures():
    return {
        "trade_bar_front": lambda: bar_front(7000), "trade_bar_top": lambda: bar_top(7001),
        "trade_brass": lambda: brass(7002), "trade_backbar_front": lambda: backbar_front(7003),
        **{f"trade_glass_{k}": (lambda k=k, i=i: glass_bottle(7004 + i, k)) for i, k in enumerate(BOTTLES)},
        "trade_bar_mirror": lambda: bar_mirror(7010, False), "trade_bar_mirror_logo": lambda: bar_mirror(7011, True),
        "trade_felt": lambda: felt(7012), "trade_cushion": lambda: felt(7013, True), "trade_pool_wood": lambda: pool_wood(7014),
        "trade_pool_apron": lambda: pool_wood(7054, False),
        "trade_ball_white": lambda: ball(7015, (226, 222, 206)), "trade_ball_red": lambda: ball(7016, (170, 30, 24)),
        "trade_ball_yellow": lambda: ball(7017, (220, 170, 30), True), "trade_ball_blue": lambda: ball(7018, (36, 56, 140)),
        "trade_ball_black": lambda: ball(7019, (22, 22, 24)),
        "trade_jukebox": lambda: jukebox(7020), "trade_jukebox_top": lambda: jukebox_top(7021),
        "trade_dartboard": lambda: dartboard(7022),
        "trade_neon_beer": lambda: neon(7023, False), "trade_neon_beer_on": lambda: neon(7023, True),
        "trade_porcelain": lambda: porcelain(7024), "trade_oxblood": lambda: oxblood(7025),
        "trade_station_front": lambda: station_front(7026), "trade_station_top": lambda: station_top(7027),
        "trade_disinfectant": lambda: disinfectant(7028), "trade_hair": lambda: hair(7029),
        "trade_vault_face": lambda: vault_face(7030, False), "trade_vault_face_top": lambda: vault_face(7031, True),
        "trade_vault_back": lambda: vault_back(7032, False), "trade_vault_back_top": lambda: vault_back(7033, True),
        "trade_vault_edge": lambda: vault_edge(7034),
        "trade_deposit": lambda: deposit(7035, False), "trade_deposit_b": lambda: deposit(7036, True),
        "trade_check_top": lambda: check_top(7037), "trade_rates_board": lambda: rates_board(7038),
        "trade_velvet": lambda: velvet(7039),
        "trade_key_rack": lambda: key_rack(7040), "trade_suitcase_brown": lambda: suitcase(7041, (110, 72, 40)),
        "trade_suitcase_blue": lambda: suitcase(7042, (50, 66, 96)),
        "trade_listings": lambda: listings(7043), "trade_listings_b": lambda: listings(7044),
        "trade_flag_l": lambda: flag(7045, "l"), "trade_flag_r": lambda: flag(7046, "r"),
        "trade_honor_board": lambda: honor_board(7047),
        "trade_cooler_body": lambda: cooler_body(7048), "trade_cooler_bottle": lambda: cooler_bottle(7049),
        "trade_folding_metal": lambda: folding_metal(7050), "trade_tablecloth": lambda: tablecloth(7051),
        "trade_ladder": lambda: ladder_metal(7052), "trade_drop_cloth": lambda: drop_cloth(7053),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def mirror_x(boxes):
    """A piece's boxes mirrored east to west (the right half of a two-block piece from its left)."""
    return [[16 - b[3], b[1], b[2], 16 - b[0], b[4], b[5], *b[6:]] for b in boxes]


def pool(part):
    wood_, apron, felt_, cush, pocket = "trade_pool_wood", "trade_pool_apron", "trade_felt", "trade_cushion", "home_plastic_black"
    left = [[1, 0, 1, 4, 9, 4, apron], [1, 0, 12, 4, 9, 15, apron], [0.5, 9, 0.5, 16, 12.5, 15.5, apron],
            [1.5, 12.5, 1.5, 16, 13, 14.5, felt_],
            [2.5, 13, 1.5, 16, 14, 2.5, cush], [2.5, 13, 13.5, 16, 14, 14.5, cush], [1.5, 13, 2.5, 2.5, 14, 13.5, cush],
            [2.5, 12.5, 0, 15, 14.5, 1.5, wood_], [2.5, 12.5, 14.5, 15, 14.5, 16, wood_], [0, 12.5, 2.5, 1.5, 14.5, 13.5, wood_],
            [0, 12.5, 0, 2.5, 14.6, 2.5, pocket], [0, 12.5, 13.5, 2.5, 14.6, 16, pocket],
            [15, 12.5, 0, 16, 14.6, 1.5, pocket], [15, 12.5, 14.5, 16, 14.6, 16, pocket]]
    if part == "l":
        return left + [[5, 13, 7.5, 6, 14, 8.5, "trade_ball_white"], [3, 13, 4, 15.5, 13.5, 4.5, "home_wood_oak"]]
    return mirror_x(left) + [[3, 13, 6, 4, 14, 7, "trade_ball_red"], [4.2, 13, 7.2, 5.2, 14, 8.2, "trade_ball_yellow"],
                             [3.4, 13, 8.6, 4.4, 14, 9.6, "trade_ball_blue"], [9, 13, 11, 10, 14, 12, "trade_ball_black"],
                             [12, 13, 4, 13, 14, 5, "trade_ball_red"]]


def bottles(z0):
    out = []
    for i, (x, h, k) in enumerate(((1.5, 5, "amber"), (3.8, 6, "clear"), (6, 4.5, "green"), (8.3, 5.5, "amber"),
                                   (10.6, 6.5, "clear"), (12.9, 5, "green"))):
        g = f"trade_glass_{k}"
        out += [[x, 11, z0, x + 1.6, 11 + h, z0 + 1.6, g], [x + 0.5, 11 + h, z0 + 0.5, x + 1.1, 13 + h, z0 + 1.1, g]]
    return out


def blocks():
    out = {}

    def add(name, boxes, title, sound, loot=None, rotate="facing", decor=False, render=None, light=None):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if loot:
            spec["loot"] = loot
        if decor:
            spec["decor"] = True
        if render:
            spec["render"] = render
        if light:
            spec["light"] = light
        out[name] = spec

    dark, brass, chrome = "home_wood_dark", "trade_brass", "home_chrome"
    body = [0, 0, 2, 16, 14, 16, {"north": "trade_bar_front", "side": dark, "up": dark, "down": dark}]
    top = [0, 14, 0, 16, 16, 16, {"up": "trade_bar_top", "side": "trade_bar_top", "down": dark}]
    rail = [[0, 2, 0.3, 16, 3, 1.3, brass], [1.5, 0.5, 0.6, 2.5, 2, 2, brass], [13.5, 0.5, 0.6, 14.5, 2, 2, brass]]
    add("trade_bar", [body, top] + rail, "Bar", "WOOD", "liquor")
    add("trade_bar_taps", [body, top] + rail + [
        [3, 16, 8, 13, 17, 11, "home_steel"], [7, 17, 8.5, 9, 23, 10.5, brass], [3.5, 21, 8.5, 12.5, 23, 10.5, brass],
        [4.5, 23, 9, 5.5, 27, 10, "home_plastic_black"], [7.5, 23, 9, 8.5, 26.5, 10, "home_plastic_black"],
        [10.5, 23, 9, 11.5, 27, 10, "home_plastic_black"], [4.6, 19.5, 8.8, 5.4, 21, 9.6, chrome],
        [7.6, 19.5, 8.8, 8.4, 21, 9.6, chrome], [10.6, 19.5, 8.8, 11.4, 21, 9.6, chrome]], "Bar Taps", "WOOD", "liquor")
    add("trade_back_bar", [[0, 0, 4, 16, 10, 16, {"north": "trade_backbar_front", "side": dark, "up": dark, "down": dark}],
                           [0, 10, 3, 16, 11, 16, {"up": "trade_bar_top", "side": dark, "down": dark}]]
        + bottles(5) + [b for i, b in enumerate(bottles(9.5)) if i // 2 in (0, 3, 4)], "Back Bar", "WOOD", "liquor")
    frame = [[0, 0, 0, 16, 1, 0.9, dark], [0, 15, 0, 16, 16, 0.9, dark]]
    add("trade_bar_mirror", [[0, 1, 0, 16, 15, 0.5, "trade_bar_mirror"]] + frame, "Bar Mirror", "GLASS", decor=True)
    add("trade_bar_mirror_logo", [[0, 1, 0, 16, 15, 0.5, "trade_bar_mirror_logo"]] + frame, "Bar Mirror", "GLASS", decor=True)
    add("trade_pool_l", pool("l"), "Pool Table", "WOOD")
    add("trade_pool_r", pool("r"), "Pool Table", "WOOD")
    side = "home_wood_walnut"
    add("trade_jukebox", [[1, 0, 3, 15, 16, 15, {"north": "trade_jukebox", "side": side, "up": side, "down": side}],
                          [0.5, 0, 2.5, 15.5, 1, 15.5, chrome]], "Jukebox", "WOOD")
    add("trade_jukebox_top", [[1, 0, 3, 15, 4, 15, {"north": "trade_jukebox_top", "side": side, "up": "trade_jukebox_top"}],
                              [2, 4, 3, 14, 7, 15, {"north": "trade_jukebox_top", "side": side, "up": "trade_jukebox_top"}],
                              [4, 7, 3, 12, 9, 15, {"north": "trade_jukebox_top", "side": side, "up": "trade_jukebox_top"}]],
        "Jukebox", "WOOD")
    add("trade_dartboard", [[2, 2, 0, 14, 14, 0.5, dark],
                            [3, 3, 0.5, 13, 13, 1.5, {"south": "trade_dartboard", "north": "trade_dartboard", "side": "home_plastic_black",
                                                      "up": "home_plastic_black", "down": "home_plastic_black"}],
                            [6.6, 8.2, 1.5, 7, 8.6, 3.2, chrome], [6.3, 7.9, 3.2, 7.3, 8.9, 3.8, "trade_velvet"],
                            [9.2, 6.4, 1.5, 9.6, 6.8, 3.2, chrome], [8.9, 6.1, 3.2, 9.9, 7.1, 3.8, "trade_velvet"]],
        "Dartboard", "WOOD", decor=True)
    for k, light in (("trade_neon_beer", None), ("trade_neon_beer_on", 7)):
        add(k, [[1, 3, 0.2, 15, 13, 0.6, {"north": k, "south": k, "side": k}]], "Neon Sign", "GLASS", decor=True, light=light)

    porc, ox = "trade_porcelain", "trade_oxblood"
    add("trade_barber_chair", [[3, 0, 3, 13, 1.5, 13, porc], [6, 1.5, 6, 10, 6, 10, chrome], [2, 6, 2, 14, 7, 14, porc],
                               [2.5, 7, 2.5, 13.5, 9, 12.5, ox], [2.5, 8, 12.5, 13.5, 20, 14.5, ox],
                               [2.5, 7, 14.5, 13.5, 20, 15, porc], [7.5, 19.5, 13.5, 8.5, 21, 14.2, chrome],
                               [5, 21, 12.8, 11, 23.5, 14.4, ox],
                               [0.5, 10, 3, 2.5, 11.5, 12.5, ox], [13.5, 10, 3, 15.5, 11.5, 12.5, ox],
                               [1, 7, 3.5, 2, 10, 4.5, chrome], [14, 7, 3.5, 15, 10, 4.5, chrome],
                               [3, 3, -1.5, 13, 3.8, 1.5, chrome], [7.5, 3.8, 0, 8.5, 6.5, 2, chrome],
                               [11.5, 0.3, 1, 13.5, 1, 3, chrome]], "Barber Chair", "METAL")
    add("trade_barber_station", [[0, 0, 4, 16, 14, 16, {"north": "trade_station_front", "side": "home_white", "up": "home_white",
                                                        "down": "home_white"}],
                                 [0, 14, 3, 16, 15, 16, {"up": "trade_station_top", "side": chrome, "down": "home_white"}],
                                 [10, 15, 8, 13, 20, 11, "trade_disinfectant"], [3.5, 15, 5, 6, 16, 9.5, "home_plastic_black"],
                                 [3, 15, 12, 7, 17, 15, "home_ceramic"]], "Barber's Station", "WOOD", "home_bathroom")
    add("trade_hair", [[0, 0, 0, 16, 0.2, 16, "trade_hair"]], "Hair", "WOOL", rotate=None, decor=True)

    edge = "trade_vault_edge"
    for name, face, back in (("trade_vault_door", "trade_vault_face", "trade_vault_back"),
                             ("trade_vault_door_top", "trade_vault_face_top", "trade_vault_back_top")):
        add(name, [[0, 0, 0, 5, 16, 16, {"east": face, "west": back, "north": edge, "south": edge, "up": edge, "down": edge}],
                   [4.5, 3, 14.5, 6.5, 7, 16.5, brass], [4.5, 10, 14.5, 6.5, 14, 16.5, brass]],
            "Vault Door", "METAL")
    for k in ("trade_deposit", "trade_deposit_b"):
        add(k.replace("deposit", "deposit_boxes"), [[0, 0, 1, 16, 16, 16, {"north": k, "side": "home_steel", "up": "home_steel",
                                                                          "down": "home_steel"}]], "Deposit Boxes", "METAL", "bank")
    post = [[5, 0, 5, 11, 1, 11, brass], [7.25, 1, 7.25, 8.75, 14, 8.75, brass], [6.75, 14, 6.75, 9.25, 15.5, 9.25, brass]]
    sag = [[8.75, 12.5, 7.6, 11, 13.3, 8.4], [11, 11.8, 7.6, 13.5, 12.6, 8.4], [13.5, 11.3, 7.6, 16, 12.1, 8.4]]
    swag = [b + ["trade_velvet"] for b in sag] + [[16 - b[3], b[1], b[2], 16 - b[0], b[4], b[5], "trade_velvet"] for b in sag]
    add("trade_rope_post", post + swag, "Rope Post", "METAL", rotate="axis")
    add("trade_rope_post_end", post + [[b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0], "trade_velvet"] for b in sag],
        "Rope Post", "METAL")
    add("trade_check_desk", [[3, 0, 3, 13, 13, 13, dark], [0, 13, 0, 16, 14.5, 16, {"up": "trade_check_top", "side": dark, "down": dark}],
                             [2, 0, 2, 14, 1, 14, dark]], "Check Desk", "WOOD", "office", rotate=None)
    add("trade_rates_board", [[1, 3, 0, 15, 15, 0.8, "trade_rates_board"]], "Rates Board", "WOOD", decor=True)

    add("trade_key_rack", [[0, 2, 0, 16, 15, 2, {"south": "trade_key_rack", "side": "home_wood_oak", "up": "home_wood_oak",
                                                 "down": "home_wood_oak", "north": "home_wood_oak"}]], "Key Rack", "WOOD", decor=True)
    add("trade_luggage_cart", [[1, 1.5, 1, 15, 2.5, 15, "home_rug_red"], [1.5, 0, 1.5, 3, 1.5, 3, "home_rubber"],
                               [13, 0, 1.5, 14.5, 1.5, 3, "home_rubber"], [1.5, 0, 13, 3, 1.5, 14.5, "home_rubber"],
                               [13, 0, 13, 14.5, 1.5, 14.5, "home_rubber"],
                               [1, 2.5, 7.25, 2, 26, 8.75, brass], [14, 2.5, 7.25, 15, 26, 8.75, brass],
                               [1, 25, 7.25, 15, 26, 8.75, brass],
                               [2.5, 2.5, 2, 9.5, 8, 13, "trade_suitcase_brown"], [9, 2.5, 3, 13.5, 11, 9, "trade_suitcase_blue"],
                               [10, 11, 5, 12.5, 11.8, 7, "home_plastic_black"]], "Luggage Cart", "METAL", "clothes")

    for k in ("trade_listings", "trade_listings_b"):
        add(k, [[0.5, 1, 0, 15.5, 15, 0.6, k], [0, 0.5, 0, 16, 1, 0.9, "home_wood_oak"], [0, 15, 0, 16, 15.5, 0.9, "home_wood_oak"]],
            "Listings Board", "WOOD", decor=True)
    add("trade_water_cooler", [[4, 0, 4, 12, 10, 12, {"north": "trade_cooler_body", "side": "home_almond", "up": "home_almond",
                                                       "down": "home_almond"}],
                               [4.5, 10.5, 4.5, 11.5, 19, 11.5, "trade_cooler_bottle"], [7, 10, 7, 9, 10.5, 9, "trade_cooler_bottle"],
                               [5.5, 7, 3.3, 6.5, 8.3, 4, "home_red_steel"], [9.5, 7, 3.3, 10.5, 8.3, 4, "home_plastic_black"]],
        "Water Cooler", "METAL", render="translucent")
    for part in ("l", "r"):
        add(f"trade_flag_{part}", [[0, 2, 0, 16, 14, 0.3, f"trade_flag_{part}"]], "Flag", "WOOL", decor=True)
    add("trade_honor_board", [[2, 1, 0, 14, 15, 0.8, "trade_honor_board"]], "Honor Roll", "WOOD", decor=True)
    chair = [b + ["trade_folding_metal"] for b in F.CHAIR]
    add("trade_folding_chair", chair, "Folding Chair", "METAL")
    add("trade_folding_chair_tipped", [b + ["trade_folding_metal"] for b in F.tipped(F.CHAIR)], "Folding Chair", "METAL")
    add("trade_banquet_table", [[0, 12, 0, 16, 13, 16, {"up": "trade_tablecloth", "side": "trade_tablecloth", "down": "trade_folding_metal"}]]
        + F.legs(1, 1, 15, 15, 12, "trade_folding_metal", 1), "Banquet Table", "WOOD", rotate=None)

    ladder = "trade_ladder"
    steps = []
    for i, y in enumerate((0, 4, 8, 12)):
        z = 2 + i
        steps += [[2, y, z, 3, y + 4, z + 1, ladder], [13, y, z, 14, y + 4, z + 1, ladder], [3, y + 3, z - 0.5, 13, y + 3.6, z + 1, ladder]]
        zb = 14 - i
        steps += [[2, y, zb, 3, y + 4, zb + 1, ladder], [13, y, zb, 14, y + 4, zb + 1, ladder]]
    steps += [[2, 15.5, 5, 14, 16.5, 11, "home_plastic_beige"], [3, 7, 6, 13, 7.5, 13, ladder]]
    add("trade_stepladder", steps, "Stepladder", "METAL")
    add("trade_drop_cloth", [[0, 0, 0, 16, 0.4, 16, "trade_drop_cloth"]], "Drop Cloth", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("trade_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    lines = ",\n".join(f"    {json.dumps(k)}: {json.dumps(v)}" for k, v in kept.items())
    rest = {k: v for k, v in data.items() if k != "blocks"}
    head = "".join(f'  {json.dumps(k)}: {json.dumps(v)},\n' for k, v in rest.items())
    path.write_text("{\n" + head + '  "blocks": {\n' + lines + "\n  }\n}\n")
    print(f"{len(made)} trade blocks in {path}")


if __name__ == "__main__":
    main()
