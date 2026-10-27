#!/usr/bin/env python3
"""The bank's kit. There are no bank photos in the library yet, so this follows what the small-town
banks of 1900 to 1925 are known for (and what was added by 1993): a limestone temple front, two
columns between square piers, the bank's name cut in the frieze, bronze doors; inside, the banking
hall two storeys high under a coffered ceiling, a floor of black and white marble, the teller line
of green marble counters under bronze grilles with an arched wicket at each window and cresting
along the top, check desks with glass tops, the officers' rail; the vault behind the tellers with
its round door (left open by the robbers, or still shut), safe deposit boxes in walls of little
numbered doors; outside, the night depository, the ATM in the portico and the clock on its post.

- bk_frieze_a_10_* / bk_frieze_b_10_*: FARMERS & MERCHANTS BANK, CITIZENS SAVINGS BANK (made up);
- bk_floor_check, bk_floor_border: the marble floor; bk_coffer: a ceiling whose underside is the
  coffered plaster with its gilt rosette;
- bk_counter (green marble, white marble top), bk_grille, bk_wicket_0..2 (PAYING, RECEIVING,
  NOTES), bk_crest (the cresting over the grilles), bk_check_desk;
- the vault door three by three: bk_vault_frame_* (the steel collar in the wall, round hole),
  bk_vault_door_* (the door itself, its boltwork behind glass on the back), bk_vault_shut_* (the
  door shut in its frame); bk_deposit, bk_deposit_pried; bk_notes, bk_bags;
- bk_atm, bk_atm_lit, bk_night_drop, bk_notice (BANK CLOSED), bk_poster, bk_post_clock(_lit),
  plaques for the officers.

    python3 tools/city/bank.py        rewrites the bk_ entries in blocks.json

photo_textures.py draws the textures (bank.register()); world/county/Bank.java builds with them.
The base model faces north: anything on a wall has its back at z = 16 against the wall.
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
STONE = "stone_trim"
NAMES = {"a": "FARMERS & MERCHANTS BANK", "b": "CITIZENS SAVINGS BANK"}
WICKETS = ["PAYING", "RECEIVING", "NOTES"]
PLAQUES = {"president": "PRESI-|DENT", "cashier": "CASHIER", "loans": "LOANS", "board": "BOARD|ROOM", "vault": "VAULT"}


# ---------------------------------------------------------------- stone and marble

def frieze(key, i, w=10):
    """The bank's name cut in the limestone frieze, one tile per block."""
    t = NS.build(STONE)
    t.rng = np.random.default_rng(9800 + i)
    m = text_mask(NAMES[key], SERIF, 26, N * w - 24, 32, seed=w, spacing=1.15)
    full = np.zeros((N, N * w), np.float32)
    h, ww = m.shape
    full[16:16 + h, 12:12 + ww] = m
    cut = full[:, i * N:(i + 1) * N] > 0.4
    t.c[cut] *= 0.42
    t.h[cut] = 0.5
    area(t, 0, 0, N, 3, mul=1.1, dh=0.1)
    area(t, 0, N - 3, N, N, mul=0.75, dh=-0.1)
    t.streaks(0.6, 0.8)
    return t


def marble(seed, base, vein, veins=0.05, smooth=0.75):
    """Polished marble: a cloudy ground, veins where the noise crosses zero, a little crazing."""
    t = F.plain(seed, base, 0.05, smooth)
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.3), (32, 0.15)))
    t.c *= (1 + 0.08 * n)[..., None]
    v = np.abs(n) < veins
    t.c[v] = t.c[v] * 0.4 + np.array(vein, np.float32) * 0.6
    n2 = P.fbm(t.rng, ((8, 1.0), (16, 0.5)))
    v2 = np.abs(n2) < veins * 0.5
    t.c[v2] = t.c[v2] * 0.7 + np.array(vein, np.float32) * 0.3
    t.f0[:] = 40
    return t


def marble_check(seed):
    """The banking hall's floor: black and white marble squares, two to a block each way, the grout dark."""
    w = marble(seed, (226, 224, 216), (150, 150, 150))
    b = marble(seed + 1, (36, 36, 38), (110, 110, 112))
    yy, xx = np.mgrid[0:N, 0:N]
    black = ((xx // 32) + (yy // 32)) % 2 == 1
    w.c[black] = b.c[black]
    grout = (xx % 32 == 0) | (yy % 32 == 0)
    w.c[grout] *= 0.55
    w.h[grout] -= 0.2
    w.grime(0.18)
    return w


def marble_border(seed):
    t = marble(seed, (40, 74, 58), (150, 170, 150))
    area(t, 0, 0, N, 2, (176, 146, 80), f0=200, smooth=0.6)
    area(t, 0, N - 2, N, N, (176, 146, 80), f0=200, smooth=0.6)
    t.grime(0.18)
    return t


def counter_front(seed):
    """The counter's face: panels of green marble in white marble frames, the plinth dark."""
    t = marble(seed, (44, 82, 64), (160, 182, 160))
    white = marble(seed + 5, (222, 220, 210), (150, 150, 150))
    yy, xx = np.mgrid[0:N, 0:N]
    frame = (xx < 4) | (xx >= N - 4) | (yy < 6) | ((yy >= N - 12) & (yy < N - 8))
    t.c[frame] = white.c[frame]
    area(t, 0, N - 8, N, N, (28, 30, 30), h=0.6)
    edges(t, 4, 6, N - 4, N - 12, w=1, light=0.7, dark=1.15)
    t.grime(0.2)
    return t


# ---------------------------------------------------------------- bronze

def bronze(seed):
    t = F.plain(seed, (124, 92, 52), 0.08, 0.6)
    t.f0[:] = 210
    t.speckle(0.02, (70, 120, 100), 0.5)
    return t


def grille(seed, wicket=None):
    """Bronze bars with a band of lozenges near the top and rails; a wicket has its arched window
    and the name of the window on a black glass plate over it."""
    t = bronze(seed)
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    for x in range(2, N, 6):
        area(t, x, 0, x + 2, N, alpha=255, dh=0.15)
    for y0, y1 in ((0, 3), (17, 20), (N - 3, N)):
        area(t, 0, y0, N, y1, alpha=255, dh=0.2, mul=1.12)
    # the lozenges in the band
    for cx in range(5, N, 12):
        d = np.abs(xx - cx) + np.abs(yy - 10)
        t.alpha[(d > 4) & (d < 6.5) & (yy > 2) & (yy < 18)] = 255
    if wicket is not None:
        cx, top, r = 32, 40, 13
        hole = ((np.abs(xx - cx) < r) & (yy > top) & (yy < N - 3)) | (np.hypot(xx - cx, yy - top) < r)
        ring = ((np.abs(xx - cx) < r + 2) & (yy > top) & (yy < N - 3)) | (np.hypot(xx - cx, yy - top) < r + 2)
        t.alpha[ring] = 255
        t.c[ring] = t.c[ring] * 1.15
        t.alpha[hole] = 0
        area(t, 10, 21, 54, 30, (20, 20, 22), alpha=255, smooth=0.85, h=0.8)
        m = text_mask(WICKETS[wicket], SANS, civic.fit(WICKETS[wicket], SANS, 7, 40), 42, 8, seed=seed)
        F.paste(t, (m > 0.4).astype(np.float32), 11, 22, (214, 182, 104), h=0.9)
    return t


def crest(seed):
    """The cresting along the top of the teller line: scrolls between posts, spikes, a rail below."""
    t = bronze(seed)
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    area(t, 0, N - 4, N, N, alpha=255, dh=0.2)
    for x in (0, 30, 62):
        area(t, x, 26, x + 2, N, alpha=255)
    for cx in (15, 47):
        for s in (-1, 1):
            r = np.hypot(xx - (cx + s * 7), yy - 46)
            t.alpha[(np.abs(r - 7) < 1.3) & ((xx - cx) * s > -2)] = 255
        r = np.hypot(xx - cx, yy - 34)
        t.alpha[(np.abs(r - 5) < 1.2) & (yy > 30)] = 255
        t.alpha[(np.abs(xx - cx) < 1.2) & (yy > 18) & (yy < 30)] = 255
        t.alpha[np.hypot(xx - cx, yy - 17) < 2.6] = 255
    for x in range(4, N, 8):
        t.alpha[(np.abs(xx - x) < 1) & (yy > N - 12) & (yy < N - 4)] = 255
    return t


def coffer(seed):
    """A coffer of the banking hall's ceiling: stepped mouldings round a sunk panel, the gilt rosette,
    cream paint gone yellow and brown where the roof leaked."""
    t = F.plain(seed, (224, 214, 190), 0.03, 0.4)
    rng = t.rng
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    d = np.minimum(np.minimum(xx, N - 1 - xx), np.minimum(yy, N - 1 - yy))
    t.h[:] = np.where(d < 6, 0.95, np.where(d < 10, 0.8, np.where(d < 14, 0.65, 0.45)))
    t.c[(d >= 6) & (d < 7)] *= 0.8
    t.c[(d >= 10) & (d < 11)] *= 0.82
    t.c[(d >= 14) & (d < 15)] *= 0.8
    eggs = (d >= 11) & (d < 13) & (((xx + yy) % 5) < 2)
    t.c[eggs] = (196, 160, 86)
    t.f0[eggs] = 200
    t.c[d >= 15] *= 0.94
    r = np.hypot(xx - 31.5, yy - 31.5)
    ros = r < 8
    petals = ros & (np.cos(np.arctan2(yy - 31.5, xx - 31.5) * 8) > -0.2 + r / 10)
    t.c[ros] = (170, 132, 60)
    t.c[petals] = (214, 178, 96)
    t.h[ros] = 0.7
    t.f0[ros] = 210
    t.smooth[ros] = 0.6
    for _ in range(3):
        cx, cy = rng.integers(0, N, 2)
        rr = float(rng.uniform(6, 16))
        m = np.hypot(xx - cx, yy - cy) < rr * (0.75 + 0.5 * rng.random((N, N)))
        t.c[m] = t.c[m] * 0.78 + np.array([140, 100, 50]) * 0.22
    t.grime(0.25)
    return t


def check_top(seed):
    """A check desk's top: glass over the green blotter, deposit slips under it, the inkwell, a pen on its chain."""
    t = F.plain(seed, (44, 70, 54), 0.05, 0.85)
    rng = t.rng
    t.f0[:] = 40
    for _ in range(5):
        x, y = rng.integers(4, N - 22, 2)
        area(t, x, y, x + 18, y + 9, (226, 222, 204))
        for k in range(y + 2, y + 8, 2):
            area(t, x + 2, k, x + 16, k + 1, (150, 146, 140))
    area(t, 0, 0, N, 3, (176, 146, 80), f0=200)
    area(t, 0, N - 3, N, N, (176, 146, 80), f0=200)
    disc(t, 32, 32, 4, (30, 28, 30), h=0.9, smooth=0.9)
    disc(t, 32, 32, 2, (16, 18, 40))
    for k in range(14):
        area(t, 34 + k, 33 + k // 3, 35 + k, 34 + k // 3, (190, 190, 186))
    t.grime(0.3)
    return t


# ---------------------------------------------------------------- the vault

def ring_shape(t):
    """The collision of vault frame tile t (row by row from the top as you face the door): the
    collar round the hole in 4px squares, so you step over the low sill and walk through. Facing
    the door's north face your left is the block's east (+x), so the drawing runs the other way."""
    i, j = t % 3, t // 3
    out = []
    for y in range(0, 16, 4):
        run = None
        for x in range(0, 17, 4):
            solid = False
            if x < 16:
                cx, cy = i * 16 + 14 - x, (2 - j) * 16 + y + 2
                solid = (cx - 24) ** 2 + (cy - 24) ** 2 >= 20 ** 2
            if solid and run is None:
                run = x
            elif not solid and run is not None:
                out.append([run, y, 0, x, y + 4, 4])
                run = None
    return out


def vault_tiles(seed):
    """The vault door three blocks across and its frame: returns (frame, door, back, shut), nine
    tiles each, row by row from the top as you face the door."""
    S = 3 * N
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    cx = cy = S / 2 - 0.5
    r = np.hypot(xx - cx, yy - cy)
    ang = np.arctan2(yy - cy, xx - cx)

    # the frame: a dark steel plate riveted round its edge, the bright collar round the hole, the hinge on the east side
    fr = np.zeros((S, S, 3), np.float32) + (78, 80, 84)
    fr *= (0.94 + 0.08 * rng.random((S, S, 1)))
    fr[(r >= 80) & (r < 92)] = (168, 170, 170)
    fr[(r >= 80) & (r < 92)] *= (1 + 0.06 * np.sin(r[(r >= 80) & (r < 92)] * 2.1))[..., None]
    fr[(r >= 91) & (r < 93)] = (40, 40, 42)
    for k in range(24):
        a = k / 24 * 2 * np.pi
        px, py = cx + np.cos(a) * 100, cy + np.sin(a) * 100
        fr[np.hypot(xx - px, yy - py) < 2.5] = (130, 132, 134)
    hinge = (xx > S - 26) & (np.abs(yy - cy) < 30)
    fr[hinge] = (60, 62, 64)
    fr[hinge & (np.abs(yy - cy) % 14 < 2)] = (30, 30, 32)
    frame_alpha = np.where(r < 80, 0, 255).astype(np.float32)

    # the door: brushed steel turned in rings, the lip, the handwheel, the dial, the maker's plate
    dc = np.zeros((S, S, 3), np.float32) + (182, 184, 182)
    dc *= (1 + 0.05 * np.sin(r * 1.3))[..., None]
    dc[(r >= 82) & (r < 90)] = (140, 142, 142)
    dc[(r >= 58) & (r < 60)] = (90, 92, 92)
    dc[(r >= 30) & (r < 31.5)] = (100, 102, 102)
    for k in range(5):
        a = k / 5 * 2 * np.pi - np.pi / 2
        ux, uy = np.cos(a), np.sin(a)
        along = (xx - cx) * ux + (yy - cy) * uy
        across = np.abs(-(xx - cx) * uy + (yy - cy) * ux)
        dc[(along > 6) & (along < 40) & (across < 2.5)] = (210, 212, 214)
        dc[np.hypot(xx - (cx + ux * 42), yy - (cy + uy * 42)) < 5] = (220, 222, 224)
    dc[r < 9] = (226, 228, 230)
    dc[r < 4] = (120, 122, 124)
    dx, dy = cx + 34, cy - 40
    rd = np.hypot(xx - dx, yy - dy)
    dc[rd < 10] = (40, 40, 42)
    dc[rd < 8] = (200, 200, 196)
    ticks = (rd > 5.5) & (rd < 8) & ((np.degrees(np.arctan2(yy - dy, xx - dx)) % 30) < 6)
    dc[ticks] = (30, 30, 30)
    plate = (np.abs(xx - cx) < 32) & (yy > cy + 44) & (yy < cy + 62)
    dc[plate] = (176, 146, 80)
    m = text_mask("HALLORAN|SAFE & LOCK CO.", SERIF, 7, 60, 16, seed=seed, spacing=1.0)
    h, w = m.shape
    sub = dc[int(cy + 45):int(cy + 45) + h, int(cx - 30):int(cx - 30) + w]
    sub[m > 0.4] = (60, 44, 24)
    door_alpha = np.where(r < 90, 255, 0).astype(np.float32)

    # the back of the door: the boltwork behind its glass, radial bars, gears, the time locks
    bk = np.zeros((S, S, 3), np.float32) + (120, 122, 124)
    for k in range(8):
        a = k / 8 * 2 * np.pi
        ux, uy = np.cos(a), np.sin(a)
        along = (xx - cx) * ux + (yy - cy) * uy
        across = np.abs(-(xx - cx) * uy + (yy - cy) * ux)
        bk[(along > 20) & (along < 86) & (across < 4)] = (186, 188, 190)
        bk[(along > 80) & (along < 90) & (across < 6)] = (210, 212, 214)
    for gx, gy, gr in ((cx, cy, 20), (cx - 30, cy + 26, 11), (cx + 28, cy + 24, 9)):
        rg = np.hypot(xx - gx, yy - gy)
        teeth = (rg < gr + 2) & (np.cos(np.arctan2(yy - gy, xx - gx) * gr * 0.8) > 0)
        bk[(rg < gr) | teeth] = (176, 146, 80)
        bk[rg < gr * 0.3] = (90, 70, 40)
    tl = (np.abs(xx - cx) < 34) & (yy > cy - 66) & (yy < cy - 34)
    bk[tl] = (50, 52, 54)
    for k in range(3):
        bk[np.hypot(xx - (cx - 22 + k * 22), yy - (cy - 50)) < 8] = (226, 220, 200)
        bk[np.hypot(xx - (cx - 22 + k * 22), yy - (cy - 50)) < 1.5] = (30, 30, 30)
    bk *= (0.95 + 0.08 * rng.random((S, S, 1)))

    shut = fr.copy()
    inside = r < 86
    shut[inside] = dc[inside]

    def cut(col, alpha, smooth, f0, k, metal=True):
        tiles = []
        for j in range(3):
            for i in range(3):
                t = P.Tex(seed + k * 9 + j * 3 + i, (0, 0, 0))
                t.c = col[j * N:(j + 1) * N, i * N:(i + 1) * N].copy()
                t.smooth[:] = smooth
                t.f0[:] = f0
                t.h[:] = 0.75
                if alpha is not None:
                    t.alpha[:] = alpha[j * N:(j + 1) * N, i * N:(i + 1) * N]
                t.grime(0.18)
                tiles.append(t)
        return tiles

    return (cut(fr, frame_alpha, 0.5, 220, 0), cut(dc, door_alpha, 0.7, 230, 1), cut(bk, door_alpha, 0.55, 220, 2),
            cut(shut, None, 0.6, 225, 3))


def deposit(seed, pried):
    """A wall of safe deposit boxes: little nickel doors four across and six down, two keyholes and
    a number each; pried: the robbers' work, half the doors gone, a few hanging."""
    t = F.plain(seed, (150, 152, 148), 0.04, 0.6)
    t.f0[:] = 200
    rng = t.rng
    t.c *= (1 + 0.04 * np.sin(np.arange(N, dtype=np.float32) * 1.7))[None, :, None]
    for j in range(6):
        y0 = j * 11
        y1 = min(N, y0 + 11)
        for i in range(4):
            x0 = i * 16
            if pried and rng.random() < 0.5:
                area(t, x0 + 1, y0 + 1, x0 + 15, y1 - 1, (22, 20, 18), h=0.2)
                area(t, x0 + 2, y0 + 3, x0 + 14, y1 - 1, (60, 58, 54), h=0.3)
                if rng.random() < 0.3:
                    area(t, x0 + 1, y0 + 2, x0 + 3, y1 + 2, (170, 172, 168), h=0.9)
                continue
            F.gap(t, x0, y0, x0 + 16, y1, dark=0.45)
            disc(t, x0 + 5, y0 + 6, 1.2, (30, 30, 30))
            disc(t, x0 + 11, y0 + 6, 1.2, (30, 30, 30))
            area(t, x0 + 6, y0 + 2, x0 + 10, y0 + 4, (176, 146, 80), f0=200)
    t.grime(0.2)
    return t


def notes(seed):
    """Banknotes scattered on the floor, grey-green and creased, a few coins."""
    t = F.plain(seed, (150, 168, 140), 0.06, 0.2)
    rng = t.rng
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    for _ in range(16):
        cx, cy, a = rng.uniform(6, N - 6), rng.uniform(6, N - 6), rng.uniform(0, np.pi)
        u = (xx - cx) * np.cos(a) + (yy - cy) * np.sin(a)
        v = -(xx - cx) * np.sin(a) + (yy - cy) * np.cos(a)
        m = (np.abs(u) < 8) & (np.abs(v) < 3.5)
        tone = rng.uniform(0.85, 1.1)
        t.c[m] = np.array((150, 168, 140), np.float32) * tone
        t.c[m & (np.abs(u) < 6) & (np.abs(v) < 2.2)] *= 0.86
        t.c[m & (np.hypot(u, v) < 1.6)] = (90, 100, 84)
        t.alpha[m] = 255
    for _ in range(6):
        x, y = rng.uniform(3, N - 3, 2)
        m = np.hypot(xx - x, yy - y) < 2
        t.c[m] = (190, 190, 186)
        t.alpha[m] = 255
    t.grime(0.25)
    return t


def bag(seed):
    """A canvas money bag, the dollar sign and the bank's initials stencilled, the neck tied."""
    t = F.plain(seed, (186, 172, 136), 0.08, 0.15)
    x = np.arange(N, dtype=np.float32)
    t.c *= (1 + 0.04 * np.sin(x * 1.9))[None, :, None]
    m = text_mask("$", SANS, 22, 24, 26, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 20, 20, (60, 50, 40), h=0.6)
    m = text_mask("F & M", SANS, 7, 40, 9, seed=seed + 1)
    F.paste(t, (m > 0.4).astype(np.float32), 12, 48, (60, 50, 40), h=0.6)
    t.grime(0.35)
    return t


# ---------------------------------------------------------------- outside

def atm(seed, lit):
    """The cash machine set in the portico wall, 1993: the blue header, the screen (still glowing
    in some), the side keys, the card slot, the keypad, the cash and receipt slots."""
    t = F.plain(seed, (74, 76, 80), 0.04, 0.5)
    t.f0[:] = 120
    area(t, 0, 0, N, 2, (40, 40, 44))
    area(t, 2, 3, N - 2, 12, (30, 62, 126), h=0.8)
    m = text_mask("CASH 24", SANS, 8, 50, 9, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 7, 3, (236, 236, 236), h=0.85)
    area(t, 13, 15, 51, 36, (20, 22, 24), h=0.6, smooth=0.9)
    if lit:
        area(t, 15, 17, 49, 34, (22, 74, 86), smooth=0.9)
        m = text_mask("WELCOME|PLEASE INSERT|YOUR CARD", SANS, 5, 32, 15, seed=seed + 1)
        F.paste(t, (m > 0.4).astype(np.float32), 16, 18, (170, 240, 220), h=0.6)
        t.emit[17:34, 15:49] = 0.85
    else:
        area(t, 15, 17, 49, 34, (12, 14, 16), smooth=0.9)
        for k in range(16):
            t.c[20 + k, 22 + (k * 7) % 22] = (90, 96, 100)
    for y in (18, 23, 28, 33):
        area(t, 6, y, 11, y + 3, (150, 150, 150), h=0.9)
        area(t, 53, y, 58, y + 3, (150, 150, 150), h=0.9)
    area(t, 38, 39, 54, 42, (16, 16, 18), h=0.4)
    area(t, 38, 43, 41, 45, (60, 210, 90) if lit else (40, 60, 44))
    if lit:
        t.emit[43:45, 38:41] = 1.0
    for j in range(4):
        for i in range(3):
            area(t, 14 + i * 7, 40 + j * 5, 19 + i * 7, 44 + j * 5, (170, 170, 168), h=0.9)
    area(t, 36, 50, 56, 54, (14, 14, 16), h=0.3)
    area(t, 40, 57, 52, 59, (14, 14, 16), h=0.3)
    t.grime(0.3)
    return t


def night_drop(seed):
    """The night depository: a bronze plate, the hopper door with its pull, engraved letters, a keyhole."""
    t = bronze(seed)
    edges(t, 1, 1, N - 1, N - 1, w=2, light=1.2, dark=0.6)
    m = text_mask("NIGHT|DEPOSITORY", SERIF, civic.fit("DEPOSITORY", SERIF, 7, 48), 52, 16, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 6, 5, (54, 38, 20), h=0.5)
    area(t, 10, 24, 54, 46, mul=1.1, dh=0.15)
    edges(t, 10, 24, 54, 46, w=2)
    area(t, 16, 38, 48, 42, (190, 160, 96), dh=0.3, f0=220)
    disc(t, 32, 53, 2.5, (24, 20, 16))
    t.grime(0.3)
    return t


def notice(seed):
    """BANK CLOSED, typed and taped inside the door glass by the last cashier."""
    t = F.plain(seed, (234, 230, 216), 0.03, 0.2)
    m = text_mask("BANK CLOSED", SANS, civic.fit("BANK CLOSED", SANS, 8, 48), 50, 10, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 7, 7, (170, 26, 26), h=0.6)
    m = text_mask("WITHDRAWALS|SUSPENDED BY|ORDER OF THE|GOVERNOR", SANS, 5, 50, 30, seed=seed + 1)
    F.paste(t, (m > 0.4).astype(np.float32), 7, 20, (36, 34, 32), h=0.6)
    for x0, y0 in ((0, 0), (N - 10, 0), (0, N - 6), (N - 10, N - 6)):
        area(t, x0, y0, x0 + 10, y0 + 6, (214, 204, 150))
    t.grime(0.25)
    return t


def poster(seed):
    """A savings poster from the seventies: SAVE FOR A RAINY DAY under a red umbrella."""
    t = F.plain(seed, (34, 50, 88), 0.04, 0.2)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - 32, yy - 28)
    t.c[(r < 18) & (yy < 28)] = (196, 44, 40)
    for k in range(-2, 3):
        t.c[(np.abs(xx - 32 - k * 7) < 0.8) & (r < 18) & (yy < 28)] = (150, 30, 28)
    t.c[(np.abs(xx - 32) < 1) & (yy >= 28) & (yy < 40)] = (60, 40, 30)
    for k in range(10):
        x, y = 8 + (k * 13) % 48, 4 + (k * 7) % 12
        t.c[y:y + 3, x] = (170, 190, 220)
    m = text_mask("SAVE FOR A|RAINY DAY", SANS, 7, 54, 18, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 5, 42, (236, 226, 196), h=0.6)
    t.grime(0.3)
    return t


def clock_face(seed, lit):
    """The street clock's face: white enamel, black ticks and hands, a bronze bezel."""
    t = bronze(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - 31.5, yy - 31.5)
    face = r < 27
    t.c[face] = (234, 230, 214) if lit else (200, 196, 182)
    t.smooth[face] = 0.8
    t.f0[face] = 40
    a = np.degrees(np.arctan2(yy - 31.5, xx - 31.5)) % 30
    t.c[(r > 21) & (r < 26) & ((a < 3) | (a > 27))] = (30, 28, 26)
    ang = np.arctan2(xx - 31.5, -(yy - 31.5))
    for a0, length, wd in ((2.0, 13, 2.2), (4.6, 20, 1.4)):
        d = np.abs(((ang - a0 + np.pi) % (2 * np.pi)) - np.pi)
        t.c[(d < wd / np.maximum(r, 1)) & (r < length)] = (24, 22, 20)
    t.c[(r >= 27) & (r < 30)] *= 1.25
    if lit:
        t.emit[face] = 0.7
    t.grime(0.2)
    return t


def textures():
    T = {}
    for key in NAMES:
        for i in range(10):
            T[f"bk_frieze_{key}_10_{i}"] = lambda key=key, i=i: frieze(key, i)
    T["bk_marble_check"] = lambda: marble_check(9810)
    T["bk_marble_border"] = lambda: marble_border(9811)
    T["bk_marble_white"] = lambda: marble(9812, (226, 224, 214), (150, 150, 150))
    T["bk_marble_green"] = lambda: counter_front(9813)
    T["bk_bronze"] = lambda: bronze(9814)
    T["bk_grille"] = lambda: grille(9815)
    for k in range(len(WICKETS)):
        T[f"bk_wicket_{k}"] = lambda k=k: grille(9816 + k, k)
    T["bk_crest"] = lambda: crest(9820)
    T["bk_coffer"] = lambda: coffer(9821)
    T["bk_check_top"] = lambda: check_top(9822)
    vault = {}

    def vt(kind, i):
        if not vault:
            vault["t"] = vault_tiles(9830)
        return vault["t"][kind][i]

    for i in range(9):
        for k, name in enumerate(("frame", "door", "back", "shut")):
            T[f"bk_vault_{name}_{i}"] = lambda k=k, i=i: vt(k, i)
    T["bk_deposit"] = lambda: deposit(9840, False)
    T["bk_deposit_pried"] = lambda: deposit(9841, True)
    T["bk_notes"] = lambda: notes(9842)
    T["bk_bag"] = lambda: bag(9843)
    T["bk_atm"] = lambda: atm(9844, False)
    T["bk_atm_lit"] = lambda: atm(9844, True)
    T["bk_night_drop"] = lambda: night_drop(9845)
    T["bk_notice"] = lambda: notice(9846)
    T["bk_poster"] = lambda: poster(9847)
    T["bk_clock"] = lambda: clock_face(9848, False)
    T["bk_clock_lit"] = lambda: clock_face(9848, True)
    for k, (name, text) in enumerate(PLAQUES.items()):
        T[f"bk_plaque_{name}"] = lambda text=text, k=k: civic.plaque(9850 + k, text)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, render=None, tex=None, light=None, loot=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if loot:
            spec["loot"] = loot
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

    for key in NAMES:
        for i in range(10):
            add(f"bk_frieze_{key}_10_{i}", None, "Frieze", "STONE", tex={"north": f"bk_frieze_{key}_10_{i}", "side": STONE, "up": STONE, "down": STONE})
    add("bk_floor_check", None, "Marble Floor", "STONE", rotate=None, tex="bk_marble_check")
    add("bk_floor_border", None, "Marble Floor", "STONE", rotate=None, tex="bk_marble_border")
    add("bk_coffer", None, "Coffered Ceiling", "STONE", rotate=None, tex={"up": "roof_gravel", "down": "bk_coffer", "side": "cv_plaster"})

    # the teller line: the counter (public side north, its white top overhanging), the bronze above
    green, white = "bk_marble_green", "bk_marble_white"
    add("bk_counter", [[0, 0, 1, 16, 14, 16, {"north": green, "side": green, "south": "cv_oak", "up": white, "down": green}],
                       [0, 14, 0, 16, 16, 16, white]], "Teller Counter", "STONE")
    plate = {"side": "bk_bronze", "up": "bk_bronze", "down": "bk_bronze"}
    add("bk_grille", [[0, 0, 7, 16, 16, 9, {"north": "bk_grille", "south": "bk_grille", **plate}]], "Teller Grille", "METAL",
        render="cutout")
    for k in range(len(WICKETS)):
        add(f"bk_wicket_{k}", [[0, 0, 7, 16, 16, 9, {"north": f"bk_wicket_{k}", "south": f"bk_wicket_{k}", **plate}],
                               [4, 0, 2, 12, 1, 7, white]], "Teller Window", "METAL", render="cutout")
    add("bk_crest", [[0, 0, 7, 16, 16, 9, {"north": "bk_crest", "south": "bk_crest", **plate}]], "Grille Cresting", "METAL",
        render="cutout", decor=True)
    # the marble dado round the hall's walls: green panels in white frames, a white cap standing proud
    add("bk_dado", [[0, 0, 14, 16, 13, 16, {"north": green, "side": white, "up": white, "down": green}],
                    [0, 13, 13, 16, 15, 16, white]], "Marble Dado", "STONE", decor=True)
    add("bk_wall_clock", [[2, 2, 15, 14, 14, 16, {"north": "bk_clock", "side": "bk_bronze", "up": "bk_bronze", "down": "bk_bronze"}],
                          [7, 14, 15.5, 9, 16, 16, "bk_bronze"]], "Wall Clock", "METAL", decor=True)
    add("bk_check_desk", [[3, 0, 3, 13, 1, 13, "cv_oak"], [5, 1, 5, 11, 13, 11, "cv_panel_oak"],
                          [0, 13, 0, 16, 15, 16, {"up": "bk_check_top", "side": "cv_oak", "down": "cv_oak"}]],
        "Check Desk", "WOOD", rotate=None)

    # the vault door: the frame a plate in the wall's face, the door a slab at the back of its cell
    steel = "cv_iron"
    for i in range(9):
        add(f"bk_vault_frame_{i}", [[0, 0, 0, 16, 16, 4, {"north": f"bk_vault_frame_{i}", "south": f"bk_vault_frame_{i}", "side": steel}]],
            "Vault Door Frame", "METAL", render="cutout")
        out[f"bk_vault_frame_{i}"]["shape"] = ring_shape(i)
        add(f"bk_vault_door_{i}", [[0, 0, 11, 16, 16, 16, {"north": f"bk_vault_door_{i}", "south": f"bk_vault_back_{i}", "side": steel}]],
            "Vault Door", "METAL", render="cutout")
        add(f"bk_vault_shut_{i}", None, "Vault Door", "METAL", tex={"north": f"bk_vault_shut_{i}", "side": steel, "up": steel, "down": steel})
    add("bk_deposit", None, "Safe Deposit Boxes", "METAL", tex={"north": "bk_deposit", "side": steel, "up": steel, "down": steel}, loot="bank")
    add("bk_deposit_pried", None, "Safe Deposit Boxes", "METAL", tex={"north": "bk_deposit_pried", "side": steel, "up": steel, "down": steel})
    add("bk_notes", [[0, 0, 0, 16, 0.25, 16, {"up": "bk_notes", "down": "bk_notes", "side": "bk_notes"}]], "Banknotes", "WOOL",
        render="cutout", decor=True)
    add("bk_bags", [[2, 0, 3, 9, 6, 10, "bk_bag"], [3, 6, 5, 8, 8, 8, "bk_bag"], [8, 0, 7, 14, 5, 13, "bk_bag"]], "Money Bags", "WOOL",
        decor=True, loot="bank")

    # the low pediment's raking cornice, one in two: each side two pieces, outer (0) and inner (1)
    for side in ("l", "r"):
        for part in (0, 1):
            boxes = []
            for k in range(4):
                top = part * 8 + 2 * (k + 1)
                x0, x1 = 4 * k, 16
                if side == "r":
                    x0, x1 = 0, 16 - 4 * k
                boxes.append([x0, 0, 0, x1, top, 16, STONE])
                lx0, lx1 = (4 * k, 4 * k + 4) if side == "l" else (12 - 4 * k, 16 - 4 * k)
                boxes.append([lx0, top - 2, -2, lx1, top, 0, STONE])
            add(f"bk_rake_{side}_{part}", boxes, "Raking Cornice", "STONE")

    # outside
    add("bk_atm", None, "Cash Machine", "METAL", tex={"north": "bk_atm", "side": STONE, "up": STONE, "down": STONE})
    add("bk_atm_lit", None, "Cash Machine", "METAL", tex={"north": "bk_atm_lit", "side": STONE, "up": STONE, "down": STONE}, light=6)
    add("bk_night_drop", [[2, 1, 14, 14, 14, 16, {"north": "bk_night_drop", "side": "bk_bronze", "up": "bk_bronze", "down": "bk_bronze"}]],
        "Night Depository", "METAL", decor=True)
    add("bk_notice", [[3, 2, 15.6, 13, 14, 16, {"north": "bk_notice", "side": "paper"}]], "Notice", "WOOL", decor=True)
    add("bk_poster", [[1, 1, 15.6, 15, 15, 16, {"north": "bk_poster", "side": "paper"}]], "Poster", "WOOL", decor=True)
    for lit in ("", "_lit"):
        add(f"bk_post_clock{lit}", [[2, 2, 5, 14, 14, 11, {"north": f"bk_clock{lit}", "south": f"bk_clock{lit}", "side": "bk_bronze",
                                                          "up": "bk_bronze", "down": "bk_bronze"}],
                                   [6, 0, 6, 10, 2, 10, "bk_bronze"], [7, 14, 7, 9, 16, 9, "bk_bronze"]],
            "Street Clock", "METAL", **({"light": 10} if lit else {}))
    for k in PLAQUES:
        add(f"bk_plaque_{k}", [[3, 4, 15.4, 13, 11.5, 16, {"north": f"bk_plaque_{k}", "side": "cv_brass"}]], "Door Plaque", "METAL",
            decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("bk_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} bank blocks in {path}")


if __name__ == "__main__":
    main()
