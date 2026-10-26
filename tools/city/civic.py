#!/usr/bin/env python3
"""The public buildings' kit, after the courthouses, libraries, post offices and churches in the
photo library (refs/town/courthouse: Warren, Scott, Hancock and Woodford counties in Kentucky,
the Hopkins and Greenbrier courtrooms, the Conway and Cleburne halls; refs/town/library: the
Carnegie libraries at Bayliss, Dunfermline and Gilroy; refs/town/post-office; refs/town/church-*):

- the classical front: a column's base, fluted shaft and Ionic capital (painted white, or
  limestone), the dentil cornice and raking cornice of a pediment, the frieze with the county's
  name cut in it, balustrades along a roof, stone steps, water table and belt courses;
- tall round-arched windows (the arched head over a window, in red or buff brick or stone,
  its fanlight's glass leaded) and a round oculus for a pediment;
- the clock tower: a four-by-four dial (some still lit from behind, amber), copper gone green
  in sheets and slabs, louvres, the bell, a ball-and-spike finial;
- a slate hip roof's corners (the courses come from the house kit);
- inside: the hall's hexagon mosaic with its black border, terrazzo, marble wainscot, cream
  plaster, the courtroom's oak panelling, the judge's bench, the bar rail, pews, a hall bench,
  the county seal, flags on their poles, portraits, the felt-letter directory, door plaques,
  lit EXIT signs (red, and green), a chain for hanging lamps;
- outside: the wrought-iron fence round the lawn and the globe lamps either side of the steps.

    python3 tools/city/civic.py        rewrites the cv_ entries in blocks.json

photo_textures.py draws the textures (civic.register()); world/county/Civic.java builds with
them. Like the exterior kit, the base model faces north: anything on a wall has its back at
z = 16 against the wall, so it goes in the cell in front of the wall facing away from it.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import exterior as E  # noqa: E402
from furniture import area, disc, edges, paste, sunk, text_mask  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

COUNTY = "GRAYHAVEN"
STONE = "stone_trim"
WHITE = "ext_trim_white"
BRICK = {"red": "brick_red", "buff": "brick_buff", "stone": STONE}
ROOF_KINDS = ("slate", "charcoal", "green", "red")
PLAQUES = {"clerk": "COUNTY|CLERK", "circuit": "CIRCUIT|CLERK", "court": "COURT|ROOM", "judge": "JUDGE'S|CHAMBERS",
           "records": "RECORDS", "pva": "PROPERTY|VALUATION", "treasurer": "TREASURER", "restroom": "REST|ROOMS",
           "sheriff": "SHERIFF", "library": "READING|ROOM", "postmaster": "POST|MASTER", "office": "OFFICE",
           "jury": "JURY|ROOM"}


# ---------------------------------------------------------------- textures

def chain(seed):
    """A light chain: oval links, dark iron gone brown, on nothing."""
    t = F.plain(seed, (52, 46, 40), 0.05, 0.5)
    t.f0[:] = 200
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    for k in range(0, N, 12):
        # a link seen face on, then one edge on
        d = ((xx - 32) / 5.0) ** 2 + ((yy - k - 6) / 7.0) ** 2
        ring = (d > 0.45) & (d < 1.15)
        t.alpha[ring] = 255
        edge = (np.abs(xx - 32) < 1.2) & (yy >= k + 10) & (yy < k + 14)
        t.alpha[edge] = 255
    t.c *= (0.8 + 0.4 * t.rng.random((N, N)))[..., None]
    return t


def copper(seed, seams=False):
    """Copper sheet gone to verdigris: blue-green bloom, dark runs under the laps, brown where it wears."""
    t = F.mat("green_metal_rust", (82, 140, 116), 0.9, seed=seed, smooth=0.35)
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.4)))
    m = np.clip((n - 0.35) * 2.5, 0, 1)
    t.c = t.c * (1 - m[..., None] * 0.35) + np.array((112, 168, 150), np.float32) * (m[..., None] * 0.35)
    if seams:
        for x in range(0, N, 16):
            area(t, x, 0, x + 2, N, mul=1.18, dh=0.25)
            area(t, x + 2, 0, x + 3, N, mul=0.62, dh=-0.1)
    else:
        for y in range(0, N, 16):
            area(t, 0, y, N, y + 1, mul=0.6, dh=-0.1)
            area(t, 0, y + 1, N, y + 2, mul=1.15)
    # bronze showing through where the bloom has washed off
    w = np.clip((P.fbm(t.rng, ((8, 1.0), (32, 0.5))) - 0.62) * 6, 0, 1)
    t.c = t.c * (1 - w[..., None]) + np.array((96, 70, 48), np.float32) * w[..., None]
    t.streaks(0.8, 0.7)
    return t


def column(seed, rgb, stone=False):
    """A fluted shaft: twelve flutes to the block's width, lit on one side of each, peeling paint or weathered stone."""
    t = NS.build(STONE) if stone else E.paint(seed, rgb, 0.3, grain=False, vertical=True)
    t.rng = np.random.default_rng(seed)
    x = np.arange(N, dtype=np.float32)
    flute = np.cos((x % 8) / 8 * 2 * np.pi)
    t.c *= (1 + 0.16 * flute)[None, :, None]
    t.h += (flute * 0.12)[None, :]
    for xx in range(0, N, 8):
        area(t, xx, 0, xx + 1, N, mul=1.12, dh=0.1)  # the fillet between flutes
    area(t, 0, N - 6, N, N, mul=0.82)  # splashed dirt at the foot
    t.streaks(0.7, 0.8)
    t.crack(1, 0.15)
    return t


def dial(seed, lit, n=4):
    """A clock dial n blocks across: cream glass, black Roman hours, minute ring, hands stopped at
    seventeen minutes to four. Returns n * n tiles of 64, row by row from the top."""
    S = N * n
    k = n / 3  # the drawing below is for three blocks across
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    cx = cy = S / 2 - 0.5
    r = np.hypot(xx - cx, yy - cy)
    ang = np.arctan2(xx - cx, -(yy - cy))  # 0 at twelve, clockwise
    col = np.zeros((S, S, 3), np.float32)
    col[:] = (34, 32, 30)  # the tower's dark surround behind the dial's frame
    face = r < 88 * k
    col[face] = (226, 214, 180) if lit else (196, 188, 164)
    # the copper frame ring and the minute track
    col[(r >= 88 * k) & (r < 94 * k)] = (80, 128, 108)
    col[(r >= 94 * k) & (r < 96 * k)] = (40, 60, 50)
    col[(r > 78 * k) & (r < 79.5 * k) & face] = (30, 28, 26)
    col[(r > 72 * k) & (r < 73.2 * k) & face] = (30, 28, 26)
    for i in range(60):
        a = i / 60 * 2 * np.pi
        tick = (np.abs(((ang - a + np.pi) % (2 * np.pi)) - np.pi) < (0.03 if i % 5 else 0.05)) & (r > 72 * k) & (r < 79 * k)
        col[tick] = (30, 28, 26)
    # the hours, cut from a Roman serif and stood round the ring
    numerals = ["XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI"]
    alpha = np.zeros((S, S), np.float32)
    for i, s in enumerate(numerals):
        a = i / 12 * 2 * np.pi
        m = text_mask(s, SERIF, round(15 * k), round(34 * k), round(18 * k), seed=seed + i)
        h, w = m.shape
        px, py = cx + np.sin(a) * 58 * k - w / 2, cy - np.cos(a) * 58 * k - h / 2
        x0, y0 = int(round(px)), int(round(py))
        alpha[y0:y0 + h, x0:x0 + w] = np.maximum(alpha[y0:y0 + h, x0:x0 + w], m)
    col = col * (1 - alpha[..., None] * 0.9) + np.array((28, 26, 24), np.float32) * alpha[..., None] * 0.9

    def hand(a, length, width):
        ux, uy = np.sin(a), -np.cos(a)
        along = (xx - cx) * ux + (yy - cy) * uy
        across = np.abs(-(xx - cx) * uy + (yy - cy) * ux)
        return (along > -10) & (along < length) & (across < width * (1 - np.clip(along / length, 0, 1) * 0.6))

    hour = (3 + 43 / 60) / 12 * 2 * np.pi
    minute = 43 / 60 * 2 * np.pi
    col[hand(hour, 46 * k, 4.5 * k)] = (24, 22, 20)
    col[hand(minute, 68 * k, 3.2 * k)] = (24, 22, 20)
    col[r < 5 * k] = (60, 56, 50)
    # grime down from the top and a crack or two across the glass
    col[face] *= (0.9 + 0.1 * np.clip((yy[face] / S), 0, 1))[..., None]
    for _ in range(2):
        a0, x0, y0 = rng.uniform(0, np.pi), rng.uniform(40, S - 40), rng.uniform(40, S - 40)
        for s in range(round(60 * k)):
            x0 += np.cos(a0) * 1.0 + rng.normal(0, 0.3)
            y0 += np.sin(a0) * 1.0 + rng.normal(0, 0.3)
            xi, yi = int(x0), int(y0)
            if 0 <= xi < S and 0 <= yi < S and r[yi, xi] < 86 * k:
                col[yi, xi] = (70, 66, 58)
    tiles = []
    for j in range(n):
        for i in range(n):
            t = P.Tex(seed + j * n + i, (0, 0, 0))
            t.c = col[j * N:(j + 1) * N, i * N:(i + 1) * N].copy()
            f = face[j * N:(j + 1) * N, i * N:(i + 1) * N]
            t.smooth[:] = np.where(f, 0.75, 0.3)
            t.h[:] = np.where(f, 0.6, 0.75)
            if lit:
                t.emit[:] = np.where(f, 0.55, 0.0)
            t.c *= (0.94 + 0.12 * t.rng.random((N, N)))[..., None]
            tiles.append(t)
    return tiles


def arch(brick, part, span, seed):
    """The head of a round-arched window in a wall of brick: the fanlight's leaded glass (see-through),
    its painted frame, a ring of stone (or rubbed brick) voussoirs and the keystone. span is 1 or 2
    blocks; part which of them."""
    t = NS.build(BRICK[brick])
    t.rng = np.random.default_rng(seed)
    half = span * 32 - 3
    rise = half if span == 1 else 36
    R = (half * half + rise * rise) / (2 * rise)
    cx = span * 32 - 0.5 - part * N
    cy = N - rise + R
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - cx, yy - cy)
    above = yy < N  # every row is above the spring line
    glass = (r < R) & above
    frame = (r >= R) & (r < R + 3)
    ring = (r >= R + 3) & (r < R + 12)
    ang = np.degrees(np.arctan2(xx - cx, cy - yy))
    vous = "stone_trim" if brick != "stone" else "stone_trim_b"
    s = NS.build(vous).c
    t.c[ring] = s[ring] * (0.95 if brick != "stone" else 0.85)
    joints = ring & (np.abs(((ang + 90) % 12) - 6) > 5.3)
    t.c[joints] *= 0.55
    t.h[ring] = 0.85
    key = (np.abs(ang) < 7) & (r >= R) & (r < R + 15)
    t.c[key] = s[key] * 1.05
    t.h[key] = 1.0
    t.c[frame] = (214, 210, 196)
    t.h[frame] = 0.7
    t.smooth[frame] = 0.4
    # the glass: dark, a little sky in it, lead cames radiating and a ring
    g = np.array((44, 54, 62), np.float32) + (yy[..., None] / N) * np.array((30, 30, 26), np.float32)
    t.c[glass] = g[glass]
    t.smooth[glass] = 0.9
    t.f0[glass] = 30
    t.h[glass] = 0.5
    t.alpha[glass] = 150
    came = glass & ((np.abs(((ang + 90) % 30) - 15) > 14.2) | (np.abs(r - R * 0.45) < 0.8))
    t.c[came] = (40, 40, 40)
    t.alpha[came] = 255
    # soot and wet under the arch
    t.c[frame | ring] *= (0.85 + 0.15 * (yy[frame | ring] / N))[..., None]
    return t


def oculus(bg, seed, part=None):
    """A round window in a pediment or a gable: four keystones, the glass and its cross of cames.
    part 0 or 1 draws the left or right half of one centred on the line between two blocks."""
    t = NS.build(bg)
    t.rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    if part is not None:
        xx = xx - 32 + part * N
    r = np.hypot(xx - 31.5, yy - 31.5)
    s = NS.build("stone_trim").c
    ring = (r >= 19) & (r < 27)
    t.c[ring] = s[ring]
    t.h[ring] = 0.9
    for a in (0, 90, 180, 270):
        ang = np.degrees(np.arctan2(xx - 31.5, 31.5 - yy)) % 360
        k = (np.abs(((ang - a + 180) % 360) - 180) < 9) & (r >= 18) & (r < 30)
        t.c[k] = s[k] * 1.06
        t.h[k] = 1.0
    frame = (r >= 16) & (r < 19)
    t.c[frame] = (210, 206, 192)
    glass = r < 16
    t.c[glass] = (46, 56, 64)
    t.smooth[glass] = 0.9
    cross = glass & ((np.abs(xx - 31.5) < 0.8) | (np.abs(yy - 31.5) < 0.8) | (np.abs(r - 8) < 0.7))
    t.c[cross] = (36, 36, 36)
    t.streaks(0.6, 0.8)
    return t


def hex_tile(seed, border=False):
    """The hall's mosaic: little white hexagons, a black flower every so often, grout gone grey; or the black border band."""
    t = P.Tex(seed, (214, 210, 200))
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    # hexagons 4 texels across on a staggered grid
    s = 4.0
    q = xx / s
    row = np.floor(yy / (s * 0.866))
    q = q - (row % 2) * 0.5
    cxg = (np.floor(q) + 0.5 + (row % 2) * 0.5) * s
    cyg = (row + 0.5) * s * 0.866
    d = np.maximum(np.abs(xx - cxg), np.abs(yy - cyg) * 1.15)
    grout = d > s * 0.42
    cell = (np.floor(q) + 1000).astype(np.int64) * 7 + row.astype(np.int64) * 13
    if border:
        t.c[:] = (30, 30, 30)
        area(t, 0, 0, N, 4, (214, 210, 200))
        area(t, 0, N - 4, N, N, (214, 210, 200))
    else:
        # rosettes: a black hexagon ringed by six, every 16 texels
        fx, fy = (xx % 16) - 8, (yy % 16) - 8
        flower = np.hypot(fx, fy) < 5.2
        t.c[flower] = (34, 34, 34)
    t.c *= (0.94 + 0.08 * ((cell % 5) / 4.0))[..., None]
    t.c[grout] = t.c[grout] * 0.55 + np.array((70, 66, 60), np.float32) * 0.45
    t.h = np.where(grout, 0.6, 0.8).astype(np.float32)
    t.smooth[:] = np.where(grout, 0.2, 0.55)
    t.grime(0.35, (70, 60, 46))
    t.crack(1, 0.2)
    return t


def wainscot(seed):
    """Grey veined marble to waist height, its moulded cap along the top."""
    t = F.mat("Marble016", (150, 150, 146), 1.1, seed=seed, smooth=0.6)
    area(t, 0, 0, N, 6, (170, 168, 162), smooth=0.5, h=0.95)
    area(t, 0, 6, N, 7, mul=0.6, dh=-0.1)
    area(t, 0, 2, N, 3, mul=1.15)
    area(t, 0, N - 3, N, N, mul=0.7)
    for x in range(0, N, 32):
        area(t, x, 7, x + 1, N, mul=0.6, dh=-0.05)
    t.grime(0.2)
    return t


def panel_oak(seed):
    """Dark oak panelling: two raised fielded panels in a frame of rails and stiles, varnish gone dull."""
    t = F.wood(seed, "walnut")
    for x0 in (4, 36):
        area(t, x0, 6, x0 + 24, 58, mul=0.92)
        edges(t, x0, 6, x0 + 24, 58, w=2, light=1.25, dark=0.55, dh=0.12)
        edges(t, x0 + 4, 10, x0 + 20, 54, w=1, light=0.7, dark=1.2, dh=-0.06)
    t.smooth[:] = 0.45
    t.grime(0.18)
    return t


def directory(seed):
    """The black felt letter board in the hall: who's where, white letters pushed in crooked, some gone."""
    t = F.plain(seed, (28, 28, 30), 0.05, 0.1)
    for y in range(4, N - 4, 3):
        area(t, 3, y, N - 3, y + 1, mul=0.75)
    lines = ["COUNTY CLERK  101", "CIRCUIT CLERK 104", "PVA  108", "TREASURER  110", "SHERIFF  B1",
             "CIRCUIT COURT  2F", "DISTRICT COURT  2F", "JUDGE EXEC  201"]
    m = text_mask("|".join(lines), SANS, 5, N - 8, N - 12, seed=seed, jitter=0.4)
    keep = (t.rng.random(m.shape) > 0.08)
    paste(t, (m > 0.35).astype(np.float32) * keep, 4, 6, (220, 218, 210), 0.95, h=0.85)
    area(t, 0, 0, N, 3, (150, 116, 60), h=1.0, smooth=0.6)
    area(t, 0, N - 3, N, N, (150, 116, 60), h=1.0, smooth=0.6)
    area(t, 0, 0, 3, N, (150, 116, 60), h=1.0, smooth=0.6)
    area(t, N - 3, 0, N, N, (150, 116, 60), h=1.0, smooth=0.6)
    return t


def plaque(seed, text):
    """A door's plaque: white letters on black, a brass edge; drawn where the plate's face samples
    it (texels 12-52 across, 18-48 down)."""
    t = F.plain(seed, (24, 24, 26), 0.04, 0.5)
    m = text_mask(text, SANS, 9, 36, 24, seed=seed)
    paste(t, (m > 0.4).astype(np.float32), 14, 21, (230, 228, 220), 0.95, h=0.9)
    brass = dict(smooth=0.7, f0=230)
    area(t, 12, 18, 52, 20, (170, 140, 76), **brass)
    area(t, 12, 46, 52, 48, (170, 140, 76), **brass)
    area(t, 12, 18, 14, 48, (170, 140, 76), **brass)
    area(t, 50, 18, 52, 48, (170, 140, 76), **brass)
    t.grime(0.2)
    return t


def exit_sign(seed, rgb):
    """An EXIT sign's face, the letters lit (still on its battery, or the building's last circuit);
    drawn where the sign's face samples it (texels 8-56 across, 20-48 down)."""
    t = F.plain(seed, (226, 224, 216), 0.03, 0.5)
    m = text_mask("EXIT", SANS, 16, 36, 18, seed=seed)
    on = m > 0.35
    h, w = m.shape
    paste(t, on.astype(np.float32), 14, 25, rgb, 1.0)
    t.emit[25:25 + h, 14:14 + w] = np.where(on, 0.95, 0.0)
    for x in (10, 50):
        area(t, x, 33, x + 4, 36, rgb)  # the arrows
        t.emit[33:36, x:x + 4] = 0.9
    area(t, 8, 20, 56, 21, mul=0.7)
    area(t, 8, 47, 56, 48, mul=0.7)
    t.grime(0.2)
    return t


def seal(seed):
    """The county seal: a gilt ring with the county's name, a plough and a sheaf in the middle, on dark blue."""
    t = F.plain(seed, (34, 44, 74), 0.05, 0.4)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - 31.5, yy - 31.5)
    gold = (186, 152, 76)
    t.c[(r > 26) & (r < 30)] = gold
    t.c[(r > 18.5) & (r < 19.5)] = gold
    m = text_mask(COUNTY, SERIF, 7, 40, 9, seed=seed)
    paste(t, (m > 0.4).astype(np.float32), 12, 11, gold, 1.0)
    m = text_mask("COUNTY", SERIF, 7, 32, 9, seed=seed + 1)
    paste(t, (m > 0.4).astype(np.float32), 16, 44, gold, 1.0)
    # the sheaf
    for i in range(-3, 4):
        area(t, 31 + i, 22, 32 + i, 38, gold if i % 2 == 0 else (150, 120, 56))
    area(t, 27, 30, 36, 32, (120, 90, 40))
    t.f0[(r > 26) & (r < 30)] = 230
    t.smooth[(r > 26) & (r < 30)] = 0.6
    t.c[r > 30] = (40, 30, 22)
    t.grime(0.25)
    return t


def flag_us(seed):
    """The flag on its pole in the courtroom, hanging in folds: the canton, thirteen stripes, faded, dusty."""
    t = P.Tex(seed, (180, 40, 46))
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    stripe = (np.floor(yy / (N / 13)) % 2) == 1
    t.c[stripe] = (226, 222, 210)
    canton = (xx < 28) & (yy < 34)
    t.c[canton] = (40, 48, 98)
    stars = canton & ((xx % 5) < 1.5) & ((yy % 5) < 1.5) & (((xx // 5) + (yy // 5)) % 2 == 0)
    t.c[stars] = (230, 228, 220)
    fold = np.cos(xx / N * 2 * np.pi * 4)
    t.c *= (0.8 + 0.2 * fold)[..., None]
    t.h[:] = 0.7 + 0.15 * fold
    t.c = t.c * 0.82 + 30  # faded
    t.alpha[(yy > N - 4 + 3 * np.sin(xx * 0.4))] = 0
    t.smooth[:] = 0.1
    return t


def flag_state(seed):
    """The state flag: navy, the seal in the middle, gold fringe; hanging in folds."""
    t = P.Tex(seed, (36, 48, 96))
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - 32, yy - 30)
    t.c[(r > 9) & (r < 11)] = (200, 180, 110)
    t.c[r < 9] = (220, 214, 196)
    area(t, 26, 26, 38, 34, (60, 90, 60))
    fold = np.cos(xx / N * 2 * np.pi * 4)
    t.c *= (0.8 + 0.2 * fold)[..., None]
    t.c[yy > N - 5] = (196, 162, 80)
    t.c = t.c * 0.85 + 22
    t.smooth[:] = 0.1
    t.alpha[(yy > N - 2) & ((xx % 3) == 0)] = 0
    return t


def portrait(seed):
    """A past judge in oils: dark ground, black robe, a pale face, the gilt frame chipped."""
    t = F.plain(seed, (40, 30, 22), 0.05, 0.3)
    x0, y0, x1, y1 = 12, 6, 52, 58
    for y in range(y0, y1):
        f = (y - y0) / (y1 - y0)
        area(t, x0, y, x1, y + 1, (int(70 - 30 * f), int(56 - 24 * f), int(40 - 16 * f)))
    disc(t, 32, 24, 7, (196, 164, 136))
    area(t, 22, 32, 42, y1, (22, 20, 22))
    area(t, 29, 31, 35, 36, (220, 216, 206))
    disc(t, 32, 17, 7.5, (180, 176, 170))  # grey hair
    disc(t, 32, 25, 6, (196, 164, 136))
    frame = (176, 140, 64)
    area(t, x0 - 4, y0 - 4, x1 + 4, y0, frame, h=1.0, smooth=0.6, f0=230)
    area(t, x0 - 4, y1, x1 + 4, y1 + 4, frame, h=1.0, smooth=0.6, f0=230)
    area(t, x0 - 4, y0, x0, y1, frame, h=1.0, smooth=0.6, f0=230)
    area(t, x1, y0, x1 + 4, y1, frame, h=1.0, smooth=0.6, f0=230)
    edges(t, x0 - 4, y0 - 4, x1 + 4, y1 + 4, light=1.2, dark=0.6)
    t.c[y0:y1, x0:x1] *= 0.92  # yellowed varnish
    return F.wear(t, 0.25, 1)


def iron_fence(seed):
    """Wrought-iron railings: pickets with spear tips, two rails, painted black and rusting, on nothing."""
    t = F.plain(seed, (30, 30, 30), 0.06, 0.4)
    t.alpha[:] = 0
    for x in range(2, N, 8):
        area(t, x, 8, x + 3, N, alpha=255)
        for k in range(5):
            area(t, x + 1.5 - (4 - k) * 0.5 - 0.5, 2 + k, x + 1.5 + (4 - k) * 0.5 + 0.5 + 1, 3 + k, alpha=255)
    area(t, 0, 12, N, 15, alpha=255)
    area(t, 0, N - 8, N, N - 5, alpha=255)
    n = P.fbm(t.rng, ((8, 1.0), (16, 0.6)))
    m = np.clip((n - 0.35) * 3, 0, 1)
    t.c = t.c * (1 - m[..., None]) + np.array((96, 54, 30), np.float32) * m[..., None]
    return t


def globe(seed, lit):
    """A frosted glass globe: milky, the dirt of years in the bottom; lit warm or dead."""
    t = F.plain(seed, (230, 222, 196) if lit else (190, 188, 180), 0.04, 0.7)
    yy = np.arange(N, dtype=np.float32)[:, None]
    t.c *= (1.05 - 0.2 * (yy / N))[..., None] * np.ones((1, N, 1), np.float32)
    area(t, 0, N - 8, N, N, mul=0.8)
    for _ in range(12):
        disc(t, t.rng.uniform(4, N - 4), t.rng.uniform(N - 14, N - 4), t.rng.uniform(0.8, 1.6), (40, 34, 28))
    if lit:
        t.emit[:] = 0.8
    return t


def pew_wood(seed):
    """A pew's oak, darkened where a century of hands and coats wore it."""
    t = F.wood(seed, "oak")
    t.c *= 0.82
    t.smooth[:] = 0.5
    return F.wear(t, 0.2, 2)


def bench_cushion(seed, rgb):
    t = F.plain(seed, rgb, 0.08, 0.15)
    for x in range(0, N, 16):
        disc(t, x + 8, 32, 1.2, mul=0.5, dh=-0.2)
    t.grime(0.3)
    return t


def textures():
    T = {}
    T["cv_chain"] = lambda: chain(9001)
    T["cv_copper"] = lambda: copper(9002)
    T["cv_copper_seam"] = lambda: copper(9003, True)
    T["cv_column_white"] = lambda: column(9004, (226, 222, 210))
    T["cv_column_stone"] = lambda: column(9005, None, stone=True)
    for lit in (False, True):
        for i in range(16):
            T[f"cv_dial{'_lit' if lit else ''}_{i}"] = lambda i=i, lit=lit: dial(9010, lit)[i]
    for b in BRICK:
        T[f"cv_arch_{b}_1"] = lambda b=b: arch(b, 0, 1, 9030)
        T[f"cv_arch_{b}_l"] = lambda b=b: arch(b, 0, 2, 9031)
        T[f"cv_arch_{b}_r"] = lambda b=b: arch(b, 1, 2, 9032)
    T["cv_oculus_white"] = lambda: oculus(WHITE, 9040)
    T["cv_oculus_red"] = lambda: oculus("brick_red", 9041)
    T["cv_oculus_white_l"] = lambda: oculus(WHITE, 9040, 0)
    T["cv_oculus_white_r"] = lambda: oculus(WHITE, 9040, 1)
    T["cv_hex_tile"] = lambda: hex_tile(9042)
    T["cv_hex_border"] = lambda: hex_tile(9043, True)
    T["cv_terrazzo"] = lambda: F.wear(F.mat("Terrazzo003", None, seed=9044, smooth=0.6), 0.2, 2)
    T["cv_wainscot"] = lambda: wainscot(9045)
    T["cv_plaster"] = lambda: F.wear(F.mat("PaintedPlaster017", (214, 206, 186), 0.6, seed=9046), 0.25, 0)
    T["cv_panel_oak"] = lambda: panel_oak(9047)
    T["cv_oak"] = lambda: F.wear(F.wood(9048, "walnut"), 0.15, 1)
    T["cv_carpet_red"] = lambda: F.wear(F.mat("Carpet004", (120, 30, 34), 1.0, seed=9049), 0.35, 0)
    T["cv_directory"] = lambda: directory(9050)
    for i, (k, text) in enumerate(PLAQUES.items()):
        T[f"cv_plaque_{k}"] = lambda text=text, i=i: plaque(9060 + i, text)
    T["cv_exit_red"] = lambda: exit_sign(9080, (210, 30, 26))
    T["cv_exit_green"] = lambda: exit_sign(9081, (40, 190, 80))
    T["cv_seal"] = lambda: seal(9082)
    T["cv_flag_us"] = lambda: flag_us(9083)
    T["cv_flag_state"] = lambda: flag_state(9084)
    T["cv_portrait"] = lambda: portrait(9085)
    T["cv_portrait_b"] = lambda: portrait(9086)
    T["cv_iron_fence"] = lambda: iron_fence(9087)
    T["cv_globe"] = lambda: globe(9088, False)
    T["cv_globe_lit"] = lambda: globe(9089, True)
    T["cv_pew"] = lambda: pew_wood(9090)
    T["cv_cushion_red"] = lambda: bench_cushion(9091, (118, 34, 36))
    T["cv_cushion_green"] = lambda: bench_cushion(9092, (40, 76, 52))
    T["cv_louvre"] = lambda: E.louvres(9093, (96, 104, 94))
    T["cv_bell"] = lambda: F.wear(F.mat("green_metal_rust", (120, 92, 56), 0.8, seed=9094, smooth=0.5), 0.3, 0)
    T["cv_brass"] = lambda: F.plain(9095, (172, 140, 78), 0.06, 0.65)
    T["cv_iron"] = lambda: F.plain(9096, (34, 34, 34), 0.06, 0.4)
    T["cv_cellbar"] = lambda: F.wear(F.plain(9097, (118, 120, 112), 0.07, 0.45), 0.35, 1)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())
    ns.EXTRA.update(frieze_tiles()[0])


# ---------------------------------------------------------------- the blocks

def mirror_x(boxes):
    return [[16 - b[3], b[1], b[2], 16 - b[0], b[4], b[5], *b[6:]] for b in boxes]


def shift(boxes, dx, dz):
    return [[b[0] + dx, b[1], b[2] + dz, b[3] + dx, b[4], b[5] + dz, *b[6:]] for b in boxes]


def blocks_of(out, name):
    return out[name]["boxes"]


def round_post(y0, y1, r, tex, cx=8.0, cz=8.0):
    """A round post as two crossed boxes and a square one (an octagon, near enough)."""
    a, b = r * 0.92, r * 0.42
    return [[cx - a, y0, cz - b, cx + a, y1, cz + b, tex], [cx - b, y0, cz - a, cx + b, y1, cz + a, tex],
            [cx - r * 0.72, y0, cz - r * 0.72, cx + r * 0.72, y1, cz + r * 0.72, tex]]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, render=None, tex=None, light=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if render:
            spec["render"] = render
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    add("cv_chain", [[7.5, 0, 6, 8.5, 16, 10, "cv_chain"], [6, 0, 7.5, 10, 16, 8.5, "cv_chain"]], "Chain", "CHAIN",
        rotate=None, decor=True, render="cutout")

    # ---- the classical front: columns, cornice, pediment, balustrade (painted white, or limestone)
    for m, tex, shaft in (("white", WHITE, "cv_column_white"), ("stone", STONE, "cv_column_stone")):
        add(f"cv_column_base_{m}", [[1, 0, 1, 15, 3, 15, tex], [2, 3, 2, 14, 5, 14, tex], [3, 5, 3, 13, 6, 13, tex]]
            + round_post(6, 16, 5.5, shaft), "Column Base", "STONE", rotate=None)
        add(f"cv_column_{m}", round_post(0, 16, 5.5, shaft), "Column", "STONE", rotate=None)
        # an Ionic capital: the echinus, the scrolls either side seen from the front, the abacus
        cap = round_post(0, 7, 5.5, shaft) + [[2.5, 7, 2.5, 13.5, 10, 13.5, tex], [0, 10, 1, 16, 13, 15, tex],
                                                  [-0.5, 9, 0.5, 2.5, 13.5, 15.5, tex], [13.5, 9, 0.5, 16.5, 13.5, 15.5, tex],
                                                  [0, 13, 0, 16, 16, 16, tex]]
        add(f"cv_column_cap_{m}", cap, "Column Capital", "STONE")
        # the entablature's cornice: bed moulding, dentils, corona with its soffit, the crown on top
        dentils = [[x, 4, 9, x + 2, 7, 12, tex] for x in (0.5, 4.5, 8.5, 12.5)]
        add(f"cv_cornice_{m}", [[0, 0, 12, 16, 4, 16, tex], [0, 4, 12, 16, 7, 16, tex]] + dentils
            + [[0, 7, 4, 16, 12, 16, {"down": "ext_soffit" if m == "white" else "stone_trim_soot", "side": tex}],
               [0, 12, 2, 16, 14, 16, tex], [0, 14, 3, 16, 16, 16, tex]], "Cornice", "STONE")
        # the raking cornice up a pediment's sides, rising to the middle (l rises east, r west)
        rake = [[0, 0, 0, 16, 4, 16, tex], [4, 4, 0, 16, 8, 16, tex], [8, 8, 0, 16, 12, 16, tex], [12, 12, 0, 16, 16, 16, tex],
                [0, 3, -2, 16, 5, 0, tex]]
        rake += [[x, x - 1, -2, x + 4, x + 1, 16, tex] for x in (4, 8, 12)]
        add(f"cv_rake_{m}_l", rake, "Raking Cornice", "STONE")
        add(f"cv_rake_{m}_r", mirror_x(rake), "Raking Cornice", "STONE")
        # a balustrade between piers, across a roof's edge or a portico (rotate axis: runs along x)
        bal = [[0, 0, 4, 16, 2.5, 12, tex], [0, 13, 3, 16, 16, 13, tex]]
        for x in (2, 6, 10, 14):
            bal += [[x - 1.25, 2.5, 6.75, x + 1.25, 4, 9.25, tex], [x - 1.75, 4, 6.25, x + 1.75, 9, 9.75, tex],
                    [x - 1, 9, 7, x + 1, 13, 9, tex]]
        add(f"cv_balustrade_{m}", bal, "Balustrade", "STONE", rotate="axis")
        add(f"cv_pier_{m}", [[2, 0, 2, 14, 13, 14, tex], [1, 13, 1, 15, 15, 15, tex], [5, 15, 5, 11, 16, 11, tex]],
            "Balustrade Pier", "STONE", rotate=None)
        # a stone step (climb toward facing), and the water table where the walls step in over the basement
        add(f"cv_step_{m}", [[0, 0, 0, 16, 8, 16, tex], [0, 8, 0, 16, 16, 8, tex], [0, 7, 16, 16, 8, 17, tex]], "Steps", "STONE")
    add("cv_water_table", [[0, 0, 8, 16, 6, 16, STONE], [0, 6, 11, 16, 9, 16, STONE], [0, 9, 13.5, 16, 11, 16, STONE]],
        "Water Table", "STONE")
    add("cv_belt", [[0, 5, 12, 16, 10, 16, STONE], [0, 4, 12.5, 16, 5, 16, "stone_trim_soot"]], "Belt Course", "STONE", decor=True)
    add("cv_keystone", [[5, 0, 13, 11, 16, 16.5, STONE]], "Keystone", "STONE", decor=True)

    # ---- arched window heads in the wall itself (see-through glass in the fanlight)
    for b in BRICK:
        for part in ("1", "l", "r"):
            add(f"cv_arch_{b}_{part}", [[0, 0, 0, 16, 16, 16, {"north": f"cv_arch_{b}_{part}", "south": f"cv_arch_{b}_{part}",
                                                               "side": BRICK[b], "up": BRICK[b], "down": BRICK[b]}]],
                "Arched Window", "GLASS", render="translucent")
    add("cv_oculus_white", None, "Round Window", "STONE", tex={"north": "cv_oculus_white", "side": WHITE, "up": WHITE, "down": WHITE})
    for part in ("l", "r"):
        add(f"cv_oculus_white_{part}", None, "Round Window", "STONE",
            tex={"north": f"cv_oculus_white_{part}", "side": WHITE, "up": WHITE, "down": WHITE})
    add("cv_oculus_red", None, "Round Window", "STONE", tex={"north": "cv_oculus_red", "side": "brick_red", "up": "brick_red",
                                                            "down": "brick_red"})

    # ---- the clock: sixteen dial pieces four by four (0 top left), dark or lit from behind
    for lit in ("", "_lit"):
        for i in range(16):
            add(f"cv_dial{lit}_{i}", [[0, 0, 15, 16, 16, 16.5, {"north": f"cv_dial{lit}_{i}", "south": "cv_iron", "side": "cv_iron"}]],
                "Clock Dial", "GLASS", decor=True, **({"light": 8} if lit else {}))
    # ---- copper: sheets, a slab, a ball-and-spike finial, a bell and louvres for the belfry
    add("cv_copper", None, "Copper Roof", "METAL", rotate=None, tex={"side": "cv_copper_seam", "up": "cv_copper", "down": "cv_copper"})
    add("cv_copper_slab", [[0, 0, 0, 16, 8, 16, {"side": "cv_copper_seam", "up": "cv_copper", "down": "cv_copper"}]],
        "Copper Roof", "METAL", rotate=None)
    add("cv_copper_step", [[0, 0, 0, 16, 8, 16, {"side": "cv_copper_seam", "up": "cv_copper", "down": "cv_copper"}],
                           [0, 8, 0, 16, 16, 8, {"side": "cv_copper_seam", "up": "cv_copper", "down": "cv_copper"}]],
        "Copper Roof", "METAL")
    add("cv_finial", [[4, 0, 4, 12, 3, 12, "cv_copper"], [5, 3, 5, 11, 9, 11, "cv_copper"], [6, 9, 6, 10, 11, 10, "cv_copper"],
                      [7.25, 11, 7.25, 8.75, 16, 8.75, "cv_iron"]], "Finial", "METAL", rotate=None)
    # the same on the corner where four blocks meet (south-east of its own cell), atop an even-sided tower
    add("cv_finial_c", shift(blocks_of(out, "cv_finial"), 8, 8), "Finial", "METAL", rotate=None)
    add("cv_bell", [[3, 2, 3, 13, 4, 13, "cv_bell"], [4, 4, 4, 12, 10, 12, "cv_bell"], [5, 10, 5, 11, 13, 11, "cv_bell"],
                    [7, 13, 3, 9, 16, 13, "cv_oak"], [7.5, 0, 7.5, 8.5, 2, 8.5, "cv_iron"]], "Bell", "METAL")
    add("cv_bell_c", shift(blocks_of(out, "cv_bell"), 8, 8), "Bell", "METAL", rotate=None)
    add("cv_louvre", [[0, 0, 7, 16, 16, 9, "cv_louvre"], [0, 0, 6, 1.5, 16, 10, WHITE], [14.5, 0, 6, 16, 16, 10, WHITE]],
        "Louvres", "WOOD", rotate="axis")

    # ---- a slate (charcoal, green, red) hip roof's outer corner: the courses rising north and east
    for kind in ROOF_KINDS:
        f = {"up": f"ext_{kind}_tread", "south": f"ext_{kind}_riser", "west": f"ext_{kind}_riser", "north": "ext_soffit",
             "east": "ext_soffit", "down": "ext_soffit"}
        boxes = []
        for i in range(4):
            for j in range(4):
                # cell i along x (0 west), j along z (0 north); height rises to the north and the east
                h = min(16 - 4 * j, 4 + 4 * i)
                boxes.append([4 * i, 0, 4 * j, 4 * i + 4, h, 4 * j + 4, f])
        add(f"cv_hip_{kind}", boxes, "Hip Roof", "STONE")

    # ---- inside: floors, walls
    add("cv_hex_tile", None, "Mosaic Floor", "STONE", rotate=None, tex="cv_hex_tile")
    add("cv_hex_border", None, "Mosaic Floor", "STONE", rotate=None, tex="cv_hex_border")
    add("cv_terrazzo", None, "Terrazzo", "STONE", rotate=None, tex="cv_terrazzo")
    add("cv_wainscot", None, "Marble Wainscot", "STONE", rotate=None, tex={"side": "cv_wainscot", "up": "cv_plaster", "down": "cv_plaster"})
    add("cv_plaster", None, "Plaster", "STONE", rotate=None, tex="cv_plaster")
    add("cv_panel_oak", None, "Oak Panelling", "WOOD", rotate=None, tex={"side": "cv_panel_oak", "up": "cv_oak", "down": "cv_oak"})
    add("cv_carpet_red", [[0, 0, 0, 16, 1, 16, "cv_carpet_red"]], "Carpet", "WOOL", rotate=None, decor=True)

    # ---- the courtroom
    # the judge's bench: panelled front, the desk overhanging toward the judge's side (south, behind)
    add("cv_judge_bench", [[0, 0, 0, 16, 16, 3, {"north": "cv_panel_oak", "side": "cv_oak"}], [0, 0, 3, 16, 1, 16, "cv_oak"],
                           [0, 14, 0, 16, 16, 12, "cv_oak"], [0, 13, -1, 16, 14, 1, "cv_oak"]], "Judge's Bench", "WOOD")
    add("cv_judge_bench_top", [[0, 0, 0, 16, 6, 3, {"north": "cv_panel_oak", "side": "cv_oak"}], [0, 6, -1, 16, 8, 10, "cv_oak"]],
        "Judge's Bench", "WOOD")
    add("cv_seal", [[1, 1, 15, 15, 15, 16, {"north": "cv_seal", "side": "cv_oak"}]], "County Seal", "WOOD", decor=True)
    # the seal hung across the line between two blocks (the cell to its right as you face it, facing out)
    add("cv_seal_c", shift(blocks_of(out, "cv_seal"), 8, 0), "County Seal", "WOOD", decor=True)
    # the bar between the well of the court and the public (and round the jury box): turned spindles under a rail
    bar = [[0, 13, 6, 16, 15.5, 10, "cv_oak"], [0, 0, 6.5, 16, 2, 9.5, "cv_oak"]]
    bar += [[x - 0.75, 2, 7.25, x + 0.75, 13, 8.75, "cv_oak"] for x in (2, 6, 10, 14)]
    add("cv_bar_rail", bar, "Bar Rail", "WOOD", rotate="axis")
    add("cv_bar_post", [[5, 0, 5, 11, 15, 11, "cv_oak"], [4, 15, 4, 12, 16.5, 12, "cv_oak"]], "Rail Post", "WOOD", rotate=None)
    # a pew: seat, sloped back, a hymnal shelf behind; the ends with their panels
    pew = [[0, 6, 4, 16, 8, 14, "cv_pew"], [0, 0, 12, 16, 6, 13, "cv_pew"], [0, 8, 13, 16, 18, 15, "cv_pew"],
           [0, 13, 15, 16, 14, 17, "cv_pew"]]
    add("cv_pew", pew, "Pew", "WOOD")
    end = [[14.5, 0, 3, 16, 18.5, 16, "cv_pew"], [14, 18, 3, 16.5, 19, 16, "cv_pew"]]
    add("cv_pew_end", pew + end, "Pew", "WOOD")
    add("cv_pew_end_l", pew + mirror_x(end), "Pew", "WOOD")
    add("cv_pew_cushion", pew + [[0, 8, 4.5, 16, 9, 13, "cv_cushion_red"]], "Pew", "WOOD")
    # a hall bench, green leatherette on an oak frame, its back to the wall
    add("cv_hall_bench", [[0, 6, 6, 16, 9, 15, "cv_cushion_green"], [0, 9, 13, 16, 18, 16, "cv_cushion_green"],
                          [0.5, 0, 6.5, 2, 6, 15.5, "cv_oak"], [14, 0, 6.5, 15.5, 6, 15.5, "cv_oak"]], "Bench", "WOOD")
    # flags on their poles with the gilt eagle on top: a pole in the corner and the cloth hanging from it
    for k in ("us", "state"):
        add(f"cv_flag_{k}", [[7.25, 0, 7.25, 8.75, 16, 8.75, "cv_brass"], [6, 0, 6, 10, 1.5, 10, "cv_brass"],
                             [8.75, 2, 7.5, 16, 15.5, 8.5, f"cv_flag_{k}"]], "Flag", "WOOL", decor=True, render="cutout")
        add(f"cv_flag_{k}_top", [[7.25, 0, 7.25, 8.75, 8, 8.75, "cv_brass"], [8.75, 0, 7.5, 16, 6, 8.5, f"cv_flag_{k}"],
                                 [6.5, 8, 6.5, 9.5, 11, 9.5, "cv_brass"], [5, 10.5, 7.5, 11, 12, 8.5, "cv_brass"]],
            "Flag", "WOOL", decor=True, render="cutout")
    for k in ("", "_b"):
        add(f"cv_portrait{k}", [[1, 0, 15, 15, 16, 16, {"north": f"cv_portrait{k}", "side": "cv_brass"}]], "Portrait", "WOOD", decor=True)
    add("cv_directory", [[1, 2, 14.5, 15, 15, 16, {"north": "cv_directory", "side": "cv_brass"}]], "Directory", "WOOD", decor=True)
    for k in PLAQUES:
        add(f"cv_plaque_{k}", [[3, 4, 15.4, 13, 11.5, 16, {"north": f"cv_plaque_{k}", "side": "cv_brass"}]], "Door Plaque", "METAL",
            decor=True)
    for c in ("red", "green"):
        add(f"cv_exit_{c}", [[2, 4, 14.5, 14, 11, 16, {"north": f"cv_exit_{c}", "side": "cv_iron", "up": "cv_iron", "down": "cv_iron"}]],
            "Exit Sign", "METAL", decor=True, light=4)

    # ---- the flagpole out front: a white pole, the flag flying off its top, a gilt ball
    add("cv_flagpole", [[7.25, 0, 7.25, 8.75, 16, 8.75, WHITE]], "Flagpole", "METAL", rotate=None)
    add("cv_flagpole_base", [[4, 0, 4, 12, 4, 12, "concrete_dark"], [7, 4, 7, 9, 16, 9, WHITE]], "Flagpole", "METAL", rotate=None)
    add("cv_flagpole_top", [[7.25, 0, 7.25, 8.75, 13, 8.75, WHITE], [6.5, 13, 6.5, 9.5, 16, 9.5, "cv_brass"],
                            [8.75, 1, 7.6, 32, 12.5, 8.4, "cv_flag_us"]], "Flagpole", "METAL", decor=True, render="cutout")

    # ---- the county jail's holding cells in the basement: painted flat bars, riveted bands
    bars = [[x - 0.8, 0, 7.2, x + 0.8, 16, 8.8, "cv_cellbar"] for x in (1.5, 4.5, 7.5, 10.5, 13.5)]
    bars += [[0, 1, 7, 16, 2.5, 9, "cv_cellbar"], [0, 13.5, 7, 16, 15, 9, "cv_cellbar"]]
    add("cv_cell_bars", bars, "Cell Bars", "METAL", rotate="axis")

    # ---- outside: the lawn's railings, the globe lamps by the steps
    add("cv_iron_fence", [[0, 0, 7.5, 16, 16, 8.5, "cv_iron_fence"]], "Iron Railings", "METAL", rotate="axis", render="cutout")
    add("cv_iron_post", [[6, 0, 6, 10, 15, 10, "cv_iron"], [5.5, 0, 5.5, 10.5, 2, 10.5, "cv_iron"], [6.5, 15, 6.5, 9.5, 17, 9.5, "cv_iron"]],
        "Iron Post", "METAL", rotate=None)
    add("cv_lamp_post", [[4.5, 0, 4.5, 11.5, 3, 11.5, "front_iron"], [5.5, 3, 5.5, 10.5, 5, 10.5, "front_iron"],
                         [6.5, 5, 6.5, 9.5, 16, 9.5, "front_iron_fluted"]], "Lamp Standard", "METAL", rotate=None)
    for lit in ("", "_lit"):
        add(f"cv_lamp_globe{lit}", [[6, 0, 6, 10, 2, 10, "front_iron"], [5, 2, 5, 11, 4, 11, "front_iron"]]
            + round_post(4, 13, 5.5, f"cv_globe{lit}") + [[5.5, 13, 5.5, 10.5, 14.5, 10.5, f"cv_globe{lit}"],
                                                         [7, 14.5, 7, 9, 16, 9, "front_iron"]],
            "Globe Lamp", "GLASS", rotate=None, **({"light": 14} if lit else {}))
    return out


def frieze_tiles():
    """The county's name cut in the frieze, one tile per block across the portico (even widths 8 to 14)."""
    T, B = {}, {}
    for w in (8, 10, 12, 14):
        text = f"{COUNTY} COUNTY COURTHOUSE" if w >= 11 else "COUNTY COURTHOUSE"

        def build(w=w, text=text):
            S = N * w
            m = text_mask(text, SERIF, 26, S - 24, 32, seed=w, spacing=1.15)
            return m

        for i in range(w):
            def tile(i=i, build=build):
                t = NS.build(STONE)
                t.rng = np.random.default_rng(9100 + i)
                m = build()
                full = np.zeros((N, N * w), np.float32)
                h, ww = m.shape
                full[16:16 + h, 12:12 + ww] = m
                part = full[:, i * N:(i + 1) * N]
                cut = part > 0.4
                t.c[cut] *= 0.42
                t.h[cut] = 0.5
                area(t, 0, 0, N, 3, mul=1.1, dh=0.1)
                area(t, 0, N - 3, N, N, mul=0.75, dh=-0.1)
                t.streaks(0.6, 0.8)
                return t
            name = f"cv_frieze_{w}_{i}"
            T[name] = tile
            B[name] = {"tex": {"north": name, "side": STONE, "up": STONE, "down": STONE}, "title": "Frieze", "sound": "STONE",
                       "rotate": "facing"}
    return T, B


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("cv_")}
    made = blocks()
    made.update(frieze_tiles()[1])
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} civic blocks in {path}")


if __name__ == "__main__":
    main()
