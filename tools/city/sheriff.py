"""The sheriff's office kit (sh_): what a small county's sheriff kept in one low building by the
eighties. There are no photos of one in the library yet, so it follows what such offices are known
for: painted block walls, the county's name in metal letters over the door and the star beside it;
inside, the lobby behind its bullet-resistant window with the deal tray and the speaker, the
dispatch console with its radios and the county map, the squad room's desks and typewriters, the
wanted board, the gun lockers, the evidence shelves, booking (the counter, the height chart, the
camera, the ink plate, the cuff bench) and the cells: wall bunks, steel toilets, glass-block lights
and tally marks scratched in the paint. Outside, the radio tower, the after-hours phone and the
reserved signs.

Blocks:
- sh_letters_{a,b}_R_i (8 across, a line to each row R, 0 the upper), sh_star_0..3 (two by two: 0 top left, 1 top right,
  2 bottom left, 3 bottom right as the viewer sees them);
- walls: sh_cmu (cream), sh_cmu_grey, sh_cmu_scratch (tally marks), sh_cmu_band (the corridor
  stripe), sh_cmu_tan (the outside, split face), sh_glassblock; floor sh_floor_vct;
- the window: sh_window_lo (the deal tray and speaker), sh_window_hi, sh_counter;
- dispatch: sh_dispatch_{l,r}(_lit), sh_map_0..3 (two by two as the star), sh_wanted_{0,1};
- squad room: sh_typewriter, sh_gun_rack (loot police), sh_locker(_top) (loot police);
- evidence: sh_evidence (loot police);
- booking: sh_booking, sh_height_{0,1} (0 below), sh_camera, sh_ink, sh_cuff_bench;
- cells: sh_bunk_{l,r}, sh_cell_sign_1..6; interview: sh_mirror, sh_interview_table;
- outside: sh_tower, sh_tower_top(_lit), sh_phone(_lit), sh_sign_reserved, sh_sign_post;
- plaques sh_plaque_*.

    python3 tools/city/sheriff.py        rewrites the sh_ entries in blocks.json

photo_textures.py draws the textures (sheriff.register()); world/county/Sheriff.java builds with
them. The base model faces north: anything on a wall has its back at z = 16 against the wall.
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
COND = "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf"
HAND = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
LW = 8
LETTERS = {"a": ("GRAYHAVEN COUNTY|SHERIFF'S OFFICE", (204, 206, 204)), "b": ("COUNTY SHERIFF|AND JAIL", (74, 54, 34))}
PLAQUES = {"sheriff": "SHERIFF", "dispatch": "DISPATCH", "booking": "BOOKING", "evidence": "EVIDENCE",
           "interview": "INTERVIEW", "armory": "ARMORY", "holding": "HOLDING", "staff": "AUTHORIZED|PERSONNEL ONLY"}
GOLD = (196, 160, 70)
CREAM = (206, 198, 170)


# ---------------------------------------------------------------- the front

def letters(key, row, i):
    """The county's name in metal letters on stand-offs across the entry block, a line to each row
    of blocks (row 0 the upper), the shadow each letter throws on the wall under and to its right."""
    text, col = LETTERS[key]
    line = text.split("|")[row]
    t = F.plain(9500 + row * 10 + i + (0 if key == "a" else 40), col, 0.03, 0.75)
    t.f0[:] = 220 if key == "a" else 120
    width = N * LW - 24
    m = text_mask(line, SANS, civic.fit(line, SANS, 40, width), width, 52, seed=88)
    full = np.zeros((N, N * LW), np.float32)
    h, w = m.shape
    full[6:6 + h, 12:12 + w] = m
    part = full[:, i * N:(i + 1) * N] > 0.45
    shade = np.zeros_like(part)
    shade[2:, 2:] = part[:-2, :-2]
    shade &= ~part
    t.alpha[:] = np.where(part | shade, 255, 0)
    t.c[shade] = (46, 42, 36)
    t.smooth[shade] = 0.1
    t.f0[shade] = 10
    t.h[:] = np.where(part, 0.9, 0)
    t.c[part] *= (0.9 + 0.15 * t.rng.random(int(part.sum())))[..., None]
    # rust run from the screws on the bronze, the aluminium only dulled
    t.grime(0.25 if key == "b" else 0.12)
    return t


def star(i):
    """The sheriff's star, two blocks by two: six points balled at the tips, the ring with SHERIFF
    and the county round it, a seal in the middle. Tile i: 0 top left ... 3 bottom right."""
    S = N * 2
    t = F.plain(9530 + i, GOLD, 0.05, 0.7)
    t.f0[:] = 200
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    cx = cy = S / 2 - 0.5
    ang = np.arctan2(yy - cy, xx - cx)
    r = np.hypot(xx - cx, yy - cy)
    # a six-pointed star with straight sides: points out at 54, the notches in at 54 / sqrt 3
    from PIL import Image, ImageDraw, ImageFont
    poly = []
    for p in range(12):
        a = -np.pi / 2 + p * np.pi / 6
        rad = 54 if p % 2 == 0 else 54 / np.sqrt(3)
        poly.append(((cx + rad * np.cos(a)) * 4, (cy + rad * np.sin(a)) * 4))
    im = Image.new("L", (S * 4, S * 4), 0)
    ImageDraw.Draw(im).polygon(poly, fill=255)
    body = np.asarray(im.resize((S, S), Image.BOX), np.float32) / 255 > 0.5
    balls = np.zeros_like(body)
    for p in range(6):
        a = -np.pi / 2 + p * np.pi / 3
        bx, by = cx + 56 * np.cos(a), cy + 56 * np.sin(a)
        balls |= np.hypot(xx - bx, yy - by) < 6.5
    shape = body | balls
    alpha = np.where(shape, 255, 0).astype(np.float32)
    col = np.zeros((S, S, 3), np.float32) + np.array(GOLD, np.float32)
    # each point bevelled along its ridge: lit on one flank, shaded on the other
    k = ((ang + np.pi / 2) / (np.pi / 3)) % 1 - 0.5
    col *= (1 + 0.22 * np.sign(k) * np.clip(r / 54, 0, 1))[..., None] * (1 + 0.15 * np.clip(1 - r / 60, 0, 1))[..., None]
    ring = (r > 22) & (r < 34)
    col[ring] = (140, 110, 50)
    col[(np.abs(r - 22) < 1) | (np.abs(r - 34) < 1)] = (230, 200, 110)
    col[r < 21] = (214, 182, 96)
    hgt = np.where(shape, 0.8, 0) + np.where(r < 21, 0.1, 0)
    # the ring's lettering: SHERIFF over the top, the county under, laid round in little stamps
    im = Image.new("L", (S * 4, S * 4), 0)
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(SANS, 26)
    for word, a0, a1, flip in (("SHERIFF", -150, -30, False), ("GRAYHAVEN CO", 160, 20, True)):
        n = len(word)
        for j, ch in enumerate(word):
            a = np.radians(a0 + (a1 - a0) * (j + 0.5) / n)
            layer = Image.new("L", (40, 40), 0)
            ImageDraw.Draw(layer).text((10, 4), ch, fill=255, font=f)
            rot = -np.degrees(a) - 90 + (180 if flip else 0)
            layer = layer.rotate(rot, resample=Image.BICUBIC)
            px, py = (cx + 28 * np.cos(a)) * 4 - 20, (cy + 28 * np.sin(a)) * 4 - 20
            im.paste(255, (int(px), int(py)), layer)
    m = np.asarray(im.resize((S, S), Image.BOX), np.float32) / 255
    col[(m > 0.4) & ring] = (240, 214, 130)
    # the seal: scales and a star stamped in the middle
    col[(np.abs(xx - cx) < 9) & (np.abs(yy - cy - 2) < 1)] = (120, 92, 40)
    col[(np.abs(xx - cx) < 1) & (yy > cy - 10) & (yy < cy + 8)] = (120, 92, 40)
    for sx in (-7, 7):
        col[np.hypot(xx - cx - sx, yy - cy - 6) < 3.5] = (120, 92, 40)
    ox, oy = (i % 2) * N, (i // 2) * N
    sl = (slice(oy, oy + N), slice(ox, ox + N))
    t.c[:] = col[sl]
    t.alpha[:] = alpha[sl]
    t.h[:] = hgt[sl]
    t.grime(0.25)
    t.streaks(0.4, 0.8)
    return t


# ---------------------------------------------------------------- walls and floors

def cmu(seed, base, scratch=False, band=False, outside=False):
    """Concrete block painted over in gloss, the joints showing through: 32 by 16 texel units in
    running bond. Scratched: tally marks and names dug into the paint; band: the corridor stripe at
    hip height; outside: split-face tan, unpainted, rain-stained."""
    t = F.plain(seed, base, 0.03 if not outside else 0.08, 0.55 if not outside else 0.2)
    if outside:
        t.vary(0.1)
        t.speckle(0.2, (150, 130, 100), 0.4, height=0.1)
    for row in range(4):
        y = row * 16
        area(t, 0, y, N, y + 1, mul=0.72 if outside else 0.86, dh=-0.12)
        off = 16 if row % 2 else 0
        for x in range(off, N + 32, 32):
            area(t, x % N, y, x % N + 1, y + 16, mul=0.72 if outside else 0.86, dh=-0.12)
        if outside:
            for x in range(off, N + 32, 32):
                area(t, x % N + 1, y + 1, x % N + 31, y + 4, mul=1.08, dh=0.05)
    if band:
        area(t, 0, 36, N, 44, (70, 86, 110), smooth=0.6)
        area(t, 0, 36, N, 37, mul=1.2)
    if scratch:
        rng = t.rng
        for _ in range(rng.integers(3, 6)):
            x0, y0 = rng.integers(4, 50), rng.integers(6, 52)
            n = rng.integers(3, 6)
            for j in range(n):
                xs = x0 + j * 2
                area(t, xs, y0, xs + 1, y0 + 8, mul=0.55, dh=-0.2)
            if n == 5:
                for j in range(9):
                    area(t, x0 - 1 + j, y0 + 6 - j * 0.7, x0 + j, y0 + 7 - j * 0.7, mul=0.55, dh=-0.2)
        words = ["DAY 9", "JR WAS HERE", "THEY BITE", "NO WAY OUT", "LET US OUT", "R.I.P. DOC"]
        m = text_mask(words[int(rng.integers(len(words)))], HAND, 8, 54, 12, angle=float(rng.normal(0, 6)), jitter=0.4, seed=seed)
        F.paste(t, (m > 0.4).astype(np.float32), 5, 46, (90, 84, 70), h=0.55)
    t.grime(0.18 if not outside else 0.3)
    if outside:
        t.streaks(0.8, 0.72)
    return t


def pocks(t, seed):
    """Bullet strikes on block: each a shallow crater, the face chipped off pale round a dark core,
    a few close together where someone emptied a magazine at the door."""
    rng = np.random.default_rng(seed)
    cx, cy = rng.uniform(16, 48, 2)
    for _ in range(int(rng.integers(4, 8))):
        x, y = cx + rng.normal(0, 9), cy + rng.normal(0, 7)
        r = rng.uniform(2.2, 3.8)
        disc(t, x, y, r + 1.6, mul=1.25, dh=-0.12)
        disc(t, x, y, r, (126, 116, 98), dh=-0.25)
        disc(t, x, y, r * 0.45, (40, 36, 30), dh=-0.4)
        for a in rng.uniform(0, np.pi * 2, 3):
            for k in range(int(rng.integers(3, 7))):
                px, py = int(x + np.cos(a) * (r + k)), int(y + np.sin(a) * (r + k))
                if 0 <= px < N and 0 <= py < N:
                    t.c[py, px] *= 0.6
    return t


def glassblock(seed):
    """Glass block: a four by four of thick wavy squares in grey mortar, the light coming through green."""
    t = F.plain(seed, (170, 196, 188), 0.04, 0.9)
    t.f0[:] = 60
    t.alpha[:] = 190
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    wav = np.sin(xx * 0.9) * np.sin(yy * 0.9)
    t.c *= (1 + 0.12 * wav)[..., None]
    for k in range(0, N, 16):
        area(t, k, 0, k + 2, N, (150, 148, 140), h=0.95, smooth=0.2, alpha=255)
        area(t, 0, k, N, k + 2, (150, 148, 140), h=0.95, smooth=0.2, alpha=255)
    t.grime(0.35)
    return t


def vct(seed):
    """Vinyl tile, twelve inch squares checkered cream and grey-green, the fleck in them, worn to the
    glue along the path, waxed long ago."""
    t = F.plain(seed, (196, 190, 168), 0.03, 0.55)
    for j in range(4):
        for i in range(4):
            if (i + j) % 2:
                area(t, i * 16, j * 16, i * 16 + 16, j * 16 + 16, (150, 160, 146))
            area(t, i * 16, j * 16, i * 16 + 16, j * 16 + 1, mul=0.85)
            area(t, i * 16, j * 16, i * 16 + 1, j * 16 + 16, mul=0.85)
    t.speckle(0.12, (90, 90, 80), 0.5)
    t.speckle(0.05, (230, 226, 210), 0.5)
    t.grime(0.35)
    return t


# ---------------------------------------------------------------- the lobby window

def window(seed, low):
    """The bullet-resistant glass: thick, greenish at the edge, scratched; the steel frame; low: the
    speaker disc and the stainless deal tray; high: the sticker."""
    t = F.plain(seed, (150, 170, 166), 0.03, 0.92)
    t.alpha[:] = 110
    t.f0[:] = 60
    t.frame(4, (70, 72, 74), f0=120)
    t.alpha[:4], t.alpha[-4:], t.alpha[:, :4], t.alpha[:, -4:] = 255, 255, 255, 255
    rng = t.rng
    for _ in range(6):
        x, y = rng.integers(6, 56, 2)
        L = rng.integers(6, 20)
        for j in range(L):
            xi, yi = min(N - 5, x + j), min(N - 5, int(y + j * rng.uniform(-0.3, 0.3)))
            t.c[yi, xi] = (220, 226, 224)
            t.alpha[yi, xi] = 170
    if low:
        disc(t, 32, 22, 7, (150, 152, 150), alpha=255, f0=200, smooth=0.6)
        for a in range(0, 360, 45):
            disc(t, 32 + 4 * np.cos(np.radians(a)), 22 + 4 * np.sin(np.radians(a)), 1, (40, 40, 40), alpha=255)
        disc(t, 32, 22, 1, (40, 40, 40), alpha=255)
        area(t, 14, 52, 50, 60, (176, 178, 176), alpha=255, f0=220, smooth=0.7)
        area(t, 16, 54, 48, 58, (60, 60, 60), alpha=255)
    else:
        area(t, 40, 40, 56, 54, (230, 226, 200), alpha=255)
        m = text_mask("NO|WEAPONS", SANS, 5, 14, 12, seed=seed)
        F.paste(t, (m > 0.4).astype(np.float32), 41, 41, (170, 30, 26))
        t.alpha[41:53, 41:55] = 255
    return t


def plate(seed, text, bg=(30, 30, 32), fg=(230, 230, 226), size=9):
    """A small sign plate filling the texture."""
    t = F.plain(seed, bg, 0.04, 0.5)
    m = text_mask(text, SANS, civic.fit(text, SANS, size, 56), 56, 52, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 4, 6, fg, h=0.9)
    edges(t, 0, 0, N, N, w=2)
    t.grime(0.2)
    return t


# ---------------------------------------------------------------- dispatch

def radio_panel(seed, lit):
    """The dispatch console's face: two radio decks, channel readouts (amber when there's power),
    the meters, rows of knobs and toggles, the footswitch label."""
    t = F.plain(seed, (70, 72, 70), 0.04, 0.4)
    t.f0[:] = 70
    for k, y in enumerate((4, 32)):
        area(t, 3, y, 61, y + 24, (52, 54, 52))
        edges(t, 3, y, 61, y + 24, w=1)
        area(t, 6, y + 3, 26, y + 10, (14, 12, 8), smooth=0.9)
        if lit:
            m = text_mask(("CH 2  SO" if k == 0 else "CH 7  FIRE"), SANS, 5, 18, 6, seed=seed + k)
            F.paste(t, (m > 0.4).astype(np.float32), 7, y + 4, (250, 170, 60))
            t.emit[y + 3:y + 10, 6:26] = 0.8
        area(t, 30, y + 3, 44, y + 11, (210, 204, 180))
        area(t, 36, y + 4, 37, y + 10, (30, 30, 30))
        for x in range(8, 58, 8):
            disc(t, x, y + 17, 2.5, (24, 24, 24), h=0.95)
        for x in (48, 52, 56):
            area(t, x, y + 3, x + 2, y + 10, (180, 180, 176), h=0.95)
        if lit and k == 0:
            disc(t, 58, y + 6, 1.5, (255, 60, 40))
            t.emit[y + 4:y + 8, 56:60] = 0.9
    t.grime(0.25)
    return t


def county_map(i):
    """The county map, two blocks by two: roads, the river, the towns in grey, the grid; the pins
    and the marker where they drew the line. Tile i: 0 top left ... 3 bottom right."""
    S = N * 2
    rng = np.random.default_rng(9560)
    c = np.zeros((S, S, 3), np.float32) + np.array((226, 222, 200), np.float32)
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    c *= (1 + 0.03 * rng.standard_normal((S, S)))[..., None]
    river = np.abs(yy - (70 + 18 * np.sin(xx / 17) + xx * 0.2)) < 2.2
    c[river] = (110, 150, 190)
    for k in range(0, S, 16):
        c[k, :] *= 0.92
        c[:, k] *= 0.92
    for x0, y0, x1, y1 in ((4, 20, 124, 40), (10, 110, 120, 90), (60, 4, 70, 124), (30, 4, 100, 124)):
        n = 200
        for s in np.linspace(0, 1, n):
            px, py = x0 + (x1 - x0) * s, y0 + (y1 - y0) * s + 3 * np.sin(s * 9)
            c[int(py) - 1:int(py) + 1, int(px) - 1:int(px) + 1] = (200, 60, 40)
    for tx, ty, r in ((64, 34, 9), (40, 96, 6), (96, 92, 5), (20, 30, 4)):
        c[np.hypot(xx - tx, yy - ty) < r] = (170, 166, 150)
    m = text_mask("GRAYHAVEN COUNTY", SANS, civic.fit("GRAYHAVEN COUNTY", SANS, 10, 104), 104, 12, seed=1)
    c[4:16, 12:116][m > 0.4] = (30, 30, 30)
    # the pins, and the red line drawn round the town
    for _ in range(18):
        px, py = rng.integers(10, 118, 2)
        col = (200, 30, 30) if rng.random() < 0.6 else (40, 70, 200)
        c[np.hypot(xx - px, yy - py) < 2] = col
    ring = np.abs(np.hypot((xx - 64) / 1.3, yy - 36) - 20) < 1.2
    c[ring] = (190, 20, 20)
    ox, oy = (i % 2) * N, (i // 2) * N
    t = F.plain(9561 + i, (226, 222, 200), 0.02, 0.4)
    t.c[:] = c[oy:oy + N, ox:ox + N]
    if i // 2 == 0:
        area(t, 0, 0, N, 2, (60, 60, 60))
    else:
        area(t, 0, N - 2, N, N, (60, 60, 60))
    if i % 2 == 0:
        area(t, 0, 0, 2, N, (60, 60, 60))
    else:
        area(t, N - 2, 0, N, N, (60, 60, 60))
    t.grime(0.2)
    return t


def wanted(i):
    """The corkboard in the lobby: WANTED sheets with their faces, a MISSING poster, the county's
    quarantine order nailed over the lot. Two tiles, 0 the viewer's left."""
    S = N * 2
    rng = np.random.default_rng(9570)
    t = F.plain(9570 + i, (166, 124, 80), 0.06, 0.2)
    big = np.zeros((N, S, 3), np.float32) + np.array((166, 124, 80), np.float32)
    big *= (1 + 0.1 * rng.standard_normal((N, S)))[..., None]
    hgt = np.full((N, S), 0.6, np.float32)

    def sheet(x, y, w, h, head, col=(232, 228, 214), face=True, text_col=(20, 20, 20)):
        big[y:y + h, x:x + w] = col
        hgt[y:y + h, x:x + w] = 0.7
        m = text_mask(head, SANS, civic.fit(head, SANS, 7, w - 2), w - 2, 8, seed=x)
        big[y + 1:y + 9, x + 1:x + w - 1][m > 0.4] = text_col
        if face:
            fx, fy = x + w // 2, y + 18
            yy, xx = np.mgrid[0:N, 0:S]
            big[(np.hypot(xx - fx, (yy - fy) * 0.8) < 5) & (yy < y + h - 6)] = (80, 76, 70)
            big[(np.abs(xx - fx) < 8) & (yy > fy + 5) & (yy < y + h - 7)] = (80, 76, 70)
        for ly in range(y + h - 6, y + h - 2, 2):
            big[ly, x + 3:x + w - 3] = (120, 116, 106)
        big[y + 1:y + 3, x + w // 2 - 1:x + w // 2 + 1] = (200, 40, 40)

    sheet(4, 6, 24, 34, "WANTED")
    sheet(32, 4, 24, 34, "WANTED")
    sheet(60, 10, 26, 36, "MISSING", col=(240, 236, 220), text_col=(160, 20, 20))
    sheet(14, 40, 30, 20, "BOIL WATER", face=False)
    sheet(90, 6, 34, 46, "QUARANTINE", col=(236, 220, 120), face=False, text_col=(150, 20, 20))
    m = text_mask("BY ORDER OF|THE COUNTY|STAY INDOORS|REPORT ALL|BITES", SANS, 5, 30, 30, seed=3)
    big[16:46, 92:122][m > 0.4] = (40, 30, 20)
    big[:3, :] = (110, 80, 50)
    big[-3:, :] = (110, 80, 50)
    if i == 0:
        big[:, :3] = (110, 80, 50)
    else:
        big[:, -3:] = (110, 80, 50)
    t.c[:] = big[:, i * N:(i + 1) * N]
    t.h[:] = hgt[:, i * N:(i + 1) * N]
    t.grime(0.25)
    return t


# ---------------------------------------------------------------- squad room, armory, evidence

def typewriter(seed):
    """An office electric: putty case, the keys dark, the platen black."""
    t = F.plain(seed, (190, 180, 156), 0.04, 0.5)
    for y in range(28, 56, 6):
        for x in range(8, 56, 5):
            area(t, x, y, x + 3, y + 3, (40, 40, 40), h=0.95)
    area(t, 4, 6, 60, 16, (26, 26, 26), smooth=0.7)
    area(t, 20, 10, 44, 13, (230, 226, 210))
    t.grime(0.2)
    return t


def gun_board(seed):
    """The back board of the shotgun rack: pegboard, the stencilled numbers under each slot."""
    t = F.plain(seed, (110, 92, 66), 0.05, 0.3)
    for y in range(4, N, 6):
        for x in range(4, N, 6):
            disc(t, x, y, 0.8, (40, 32, 24))
    for k, x in enumerate((10, 32, 54)):
        m = text_mask(f"{k + 1}", SANS, 6, 8, 8, seed=k)
        F.paste(t, (m > 0.4).astype(np.float32), x - 4, 54, (230, 226, 210))
    t.grime(0.3)
    return t


def evidence_bag(seed):
    """Brown paper evidence bags sealed with red tape, the tags hand-written."""
    t = F.plain(seed, (170, 140, 100), 0.08, 0.2)
    rng = t.rng
    for x in range(0, N, 16):
        area(t, x, 0, x + 1, N, mul=0.7)
        area(t, x + 2, 10, x + 14, 13, (190, 30, 30))
        area(t, x + 3, 20, x + 13, 30, (236, 232, 220))
        for y in (22, 25, 28):
            area(t, x + 4, y, x + 4 + int(rng.integers(4, 9)), y + 1, (40, 40, 80))
    t.grime(0.25)
    return t


# ---------------------------------------------------------------- booking

def height_chart(part):
    """The mugshot wall: white board, a line every three inches, every foot heavy and numbered.
    Part 0 the lower block (0 ft to 3 ft 3), 1 the upper (to 6 ft 6)."""
    t = F.plain(9590 + part, (226, 226, 220), 0.02, 0.4)
    ppi = N / 39.37
    for inch in range(0, 40, 3):
        total = part * 39.37 + inch
        y = int(round(N - inch * ppi)) - 1
        if y < 0 or y >= N:
            continue
        foot = abs(total / 12 - round(total / 12)) < 0.13
        half = abs(total / 6 - round(total / 6)) < 0.13
        w = N if foot else 40 if half else 18
        area(t, 0, y, w, y + (2 if foot else 1), (20, 20, 20))
        if foot and round(total / 12) > 0:
            label = f"{int(round(total / 12))}'"
            m = text_mask(label, SANS, 9, 16, 12, seed=part)
            F.paste(t, (m > 0.4).astype(np.float32), 44, max(0, y - 13), (20, 20, 20))
    t.grime(0.15)
    return t


def ink(seed):
    """The fingerprint station's top: the glass ink plate rolled black, the roller, cards in the rack."""
    t = F.plain(seed, (170, 170, 166), 0.03, 0.5)
    area(t, 4, 4, 36, 30, (16, 16, 18), smooth=0.95, f0=60)
    area(t, 6, 34, 34, 40, (40, 40, 40))
    for y in (44, 50, 56):
        area(t, 40, y, 60, y + 5, (236, 232, 220))
        for x in (42, 46, 50, 54):
            disc(t, x, y + 2, 1.5, (40, 40, 60))
    t.grime(0.3)
    return t


def booking_front(seed):
    """The booking counter's face: steel panels, kicked, PLACE ALL PROPERTY IN BIN."""
    t = F.plain(seed, (130, 134, 136), 0.04, 0.45)
    t.f0[:] = 110
    area(t, 0, 0, N, 4, (90, 92, 94))
    edges(t, 4, 8, 60, 60, w=2)
    area(t, 8, 12, 56, 22, (230, 226, 200))
    m = text_mask("EMPTY ALL|POCKETS", SANS, 5, 46, 10, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 9, 12, (30, 30, 30))
    for _ in range(4):
        x, y = t.rng.integers(10, 54, 2)
        disc(t, x, y, 3, mul=0.8, dh=-0.2)
    t.grime(0.3)
    return t


# ---------------------------------------------------------------- outside

def phone_face(seed, lit):
    """The after-hours phone: blue steel box, EMERGENCY PHONE, the handset on its hook, the button."""
    t = F.plain(seed, (40, 70, 140), 0.05, 0.5)
    m = text_mask("SHERIFF|AFTER HOURS", SANS, 6, 52, 14, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 6, 4, (236, 236, 230))
    area(t, 12, 22, 24, 56, (24, 24, 24), h=0.95)
    area(t, 30, 26, 52, 46, (170, 170, 166), f0=160)
    for y in range(29, 44, 4):
        for x in range(33, 50, 5):
            disc(t, x, y, 1.2, (40, 40, 40))
    disc(t, 41, 52, 3, (220, 40, 30) if not lit else (255, 90, 70), h=0.95)
    if lit:
        t.emit[49:56, 38:45] = 0.9
    t.grime(0.3)
    return t


def reserved(seed):
    """The parking sign: white, a red star and SHERIFF VEHICLES ONLY, TOW-AWAY under."""
    t = F.plain(seed, (230, 230, 226), 0.03, 0.5)
    t.frame(2, (170, 30, 30))
    m = text_mask("RESERVED|SHERIFF|VEHICLES|ONLY", SANS, 8, 54, 38, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 5, 4, (170, 30, 30))
    m = text_mask("TOW-AWAY ZONE", SANS, 5, 50, 8, seed=seed + 1)
    F.paste(t, (m > 0.4).astype(np.float32), 7, 48, (30, 30, 30))
    t.streaks(0.6, 0.75)
    t.grime(0.2)
    return t


def lattice(seed):
    """Galvanised tower steel."""
    t = F.plain(seed, (160, 164, 160), 0.06, 0.45)
    t.f0[:] = 160
    t.speckle(0.04, (120, 90, 60), 0.6)
    t.streaks(0.6, 0.75)
    return t


def beacon(seed, lit):
    t = F.plain(seed, (200, 30, 24) if lit else (110, 26, 20), 0.04, 0.85)
    if lit:
        t.emit[:] = 0.95
    return t


def textures():
    T = {}
    for key in LETTERS:
        for row in range(2):
            for i in range(LW):
                T[f"sh_letters_{key}_{row}_{i}"] = lambda key=key, row=row, i=i: letters(key, row, i)
    for i in range(4):
        T[f"sh_star_{i}"] = lambda i=i: star(i)
        T[f"sh_map_{i}"] = lambda i=i: county_map(i)
    for i in range(2):
        T[f"sh_wanted_{i}"] = lambda i=i: wanted(i)
        T[f"sh_height_{i}"] = lambda i=i: height_chart(i)
    T["sh_cmu"] = lambda: cmu(9600, CREAM)
    T["sh_cmu_grey"] = lambda: cmu(9601, (176, 182, 184))
    T["sh_cmu_scratch"] = lambda: cmu(9602, CREAM, scratch=True)
    T["sh_cmu_scratch_b"] = lambda: cmu(9603, CREAM, scratch=True)
    T["sh_cmu_band"] = lambda: cmu(9604, CREAM, band=True)
    T["sh_cmu_tan"] = lambda: cmu(9605, (176, 156, 124), outside=True)
    T["sh_glassblock"] = lambda: glassblock(9606)
    T["sh_floor_vct"] = lambda: vct(9607)
    T["sh_window_lo"] = lambda: window(9608, True)
    T["sh_window_hi"] = lambda: window(9609, False)
    T["sh_radio_panel"] = lambda: radio_panel(9610, False)
    T["sh_radio_panel_lit"] = lambda: radio_panel(9610, True)
    T["sh_typewriter"] = lambda: typewriter(9611)
    T["sh_gun_board"] = lambda: gun_board(9612)
    T["sh_evbag"] = lambda: evidence_bag(9613)
    T["sh_ink"] = lambda: ink(9614)
    T["sh_booking_front"] = lambda: booking_front(9615)
    T["sh_phone_face"] = lambda: phone_face(9616, False)
    T["sh_phone_face_lit"] = lambda: phone_face(9616, True)
    T["sh_reserved"] = lambda: reserved(9617)
    T["sh_lattice"] = lambda: lattice(9618)
    T["sh_beacon"] = lambda: beacon(9619, False)
    T["sh_beacon_lit"] = lambda: beacon(9619, True)
    T["sh_mirror"] = lambda: mirror(9620)
    for n in range(1, 7):
        T[f"sh_cell_sign_{n}"] = lambda n=n: plate(9630 + n, f"CELL {n}", bg=(30, 30, 32), size=12)
    for k, text in PLAQUES.items():
        T[f"sh_plaque_{k}"] = lambda k=k, text=text: civic.plaque(9640 + list(PLAQUES).index(k), text)
    return T


def mirror(seed):
    """The interview room's one-way glass: dark, a little reflective, smeared."""
    t = F.plain(seed, (40, 44, 48), 0.03, 0.92)
    t.f0[:] = 120
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    t.c *= (1 + 0.25 * np.clip(1 - np.abs(xx - yy - 10) / 14, 0, 1))[..., None]
    t.frame(3, (90, 92, 94), f0=150)
    t.grime(0.2)
    return t


OUTSIDE_KINDS = ("soot", "moss", "stain", "crack")


def register(ns):
    """Hands the textures over; the outside block also goes in with the photo materials so the
    painter's weathering (soot, pocks, moss, stains, cracks) can be run over it."""
    global NS
    NS = ns
    made = textures()
    ns.PHOTO.setdefault("sh_cmu_tan", made["sh_cmu_tan"])
    for k in OUTSIDE_KINDS:
        made[f"sh_cmu_tan_{k}"] = lambda k=k: NS.KINDS[k]("sh_cmu_tan")
    made["sh_cmu_tan_pocks"] = lambda: pocks(NS.PHOTO["sh_cmu_tan"](), 9650)
    made["sh_cmu_pocks"] = lambda: pocks(cmu(9600, CREAM), 9651)
    ns.EXTRA.update(made)


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

    iron, steel, lam = "cv_iron", "streetlight_metal", "desk_laminate"
    for key in LETTERS:
        for row, i in ((r, i) for r in range(2) for i in range(LW)):
            name = f"sh_letters_{key}_{row}_{i}"
            add(name, [[0, 0, 15.2, 16, 16, 16, name]], "Lettering", "METAL", decor=True, render="cutout")
    for i in range(4):
        add(f"sh_star_{i}", [[0, 0, 15, 16, 16, 16, {"north": f"sh_star_{i}", "side": f"sh_star_{i}"}]], "Sheriff's Star", "METAL",
            decor=True, render="cutout")
        add(f"sh_map_{i}", [[0, 0, 15.5, 16, 16, 16, {"north": f"sh_map_{i}", "side": "cv_oak"}]], "County Map", "WOOD", decor=True)
    for i in range(2):
        add(f"sh_wanted_{i}", [[0, 1, 15, 16, 15, 16, {"north": f"sh_wanted_{i}", "side": "cv_oak"}]], "Wanted Board", "WOOD", decor=True)
        add(f"sh_height_{i}", [[1, 0, 15.6, 15, 16, 16, {"north": f"sh_height_{i}", "side": "drywall"}]], "Height Chart", "WOOD",
            decor=True)

    # walls and floor: plain blocks, the outside block and the glass block
    for name, title in (("sh_cmu", "Painted Block"), ("sh_cmu_grey", "Painted Block"), ("sh_cmu_scratch", "Scratched Block"),
                        ("sh_cmu_scratch_b", "Scratched Block"), ("sh_cmu_band", "Painted Block"), ("sh_cmu_tan", "Split-Face Block")):
        add(name, None, title, "STONE", rotate=None, tex={"all": name})
    add("sh_cmu_tan_pocks", None, "Split-Face Block", "STONE", rotate=None, tex={"all": "sh_cmu_tan_pocks"})
    add("sh_cmu_pocks", None, "Painted Block", "STONE", rotate=None, tex={"all": "sh_cmu_pocks"})
    for k in OUTSIDE_KINDS:
        add(f"sh_cmu_tan_{k}", None, "Split-Face Block", "STONE", rotate=None, tex={"all": f"sh_cmu_tan_{k}"})
    add("sh_glassblock", None, "Glass Block", "GLASS", rotate=None, tex={"all": "sh_glassblock"}, render="translucent")
    add("sh_floor_vct", None, "Vinyl Tile", "STONE", rotate=None, tex={"up": "sh_floor_vct", "side": "floor_concrete", "down": "floor_concrete"})

    # the lobby window: glass in a steel frame in the wall's middle, the shelf under it on both sides
    frame = {"north": steel, "south": steel, "side": steel, "up": steel, "down": steel}
    for part in ("lo", "hi"):
        g = f"sh_window_{part}"
        boxes = [[0, 0, 6.5, 16, 16, 9.5, {"north": g, "south": g, "side": steel, "up": steel, "down": steel}]]
        if part == "lo":
            boxes += [[0, 0, 0, 16, 1.5, 16, {"up": lam, "side": lam, "down": lam}], [4, 1.5, 0, 12, 2.5, 16, frame]]
        add(g, boxes, "Security Window", "GLASS", render="translucent")
    add("sh_counter", [[0, 14, 0, 16, 16, 16, {"up": lam, "side": lam, "down": lam}], [1, 0, 1, 15, 14, 15, {"north": "sh_booking_front", "side": steel}]],
        "Counter", "METAL")

    # dispatch: the console in two halves, the radio decks on the desk's back edge
    desk = [[0, 12.5, 0, 16, 14, 16, {"up": lam, "side": lam, "down": lam}], [0, 0, 14, 16, 12.5, 16, "desk_laminate"]]
    for lit in ("", "_lit"):
        for side in ("l", "r"):
            boxes = desk + [[0 if side == "l" else 0, 14, 9, 16, 26, 16, {"north": f"sh_radio_panel{lit}", "side": steel, "up": steel}],
                            [0 if side == "l" else 15, 0, 0, 1 if side == "l" else 16, 12.5, 14, lam]]
            if side == "r":
                boxes += [[3, 14, 3, 6, 14.6, 6, "ext_black_metal"], [4.2, 14.6, 4.2, 4.8, 19, 4.8, iron], [3.5, 19, 3.5, 5.5, 21, 5.5, "ext_black_metal"]]
            add(f"sh_dispatch_{side}{lit}", boxes, "Dispatch Console", "METAL", **({"light": 4} if lit else {}))

    # squad room: the typewriter on its desk, the shotgun rack, the lockers
    add("sh_typewriter", [[3, 0, 3, 13, 3, 13, {"up": "sh_typewriter", "side": "sh_typewriter", "north": "sh_typewriter"}],
                          [3, 3, 9, 13, 5, 12, {"up": "ext_black_metal", "side": "ext_black_metal"}], [5, 3, 11, 11, 7, 11.4, "drywall"]],
        "Typewriter", "METAL")
    guns = [[1, 0, 14.5, 15, 16, 16, {"north": "sh_gun_board", "side": "cv_oak"}]]
    for x in (2.5, 7, 11.5):
        guns += [[x, 1, 12.5, x + 2, 6, 14.5, "cv_oak"], [x + 0.6, 6, 12.8, x + 1.4, 15, 14, "ext_black_metal"],
                 [x + 0.3, 7, 12.5, x + 1.7, 9, 14.3, "cv_oak"]]
    guns += [[1, 9.5, 12, 15, 10, 12.5, iron]]
    add("sh_gun_rack", guns, "Shotgun Rack", "WOOD", loot="police")
    add("sh_locker", [[1, 0, 3, 15, 16, 16, {"north": "bunk_locker", "side": "bunk_grey", "up": "bunk_grey", "down": "bunk_grey"}]],
        "Locker", "METAL", loot="police")
    add("sh_locker_top", [[1, 0, 3, 15, 14, 16, {"north": "bunk_locker_top", "side": "bunk_grey", "up": "bunk_grey", "down": "bunk_grey"}]],
        "Locker", "METAL")
    shelf = [[0, 0, 4, 1, 16, 16, iron], [15, 0, 4, 16, 16, 16, iron]] + [[1, y, 4, 15, y + 0.6, 16, "bunk_mesh"] for y in (0.5, 5.5, 10.5)]
    add("sh_evidence", shelf + [[1.5, 1.1, 6, 14.5, 5, 15, {"north": "sh_evbag", "side": "sh_evbag", "up": "sh_evbag"}],
                                [2, 6.1, 5, 8, 10, 15, "cardboard"], [9, 6.1, 7, 14, 9, 15, {"north": "sh_evbag", "side": "sh_evbag", "up": "sh_evbag"}],
                                [2, 11.1, 6, 14, 14, 15, {"north": "sh_evbag", "side": "sh_evbag", "up": "sh_evbag"}]],
        "Evidence Shelf", "METAL", loot="police")

    # booking
    add("sh_booking", [[0, 14, 0, 16, 16, 16, {"up": lam, "side": lam, "down": lam}], [0, 0, 1, 16, 14, 3, {"north": "sh_booking_front", "side": steel}],
                       [0, 0, 3, 1, 14, 16, steel], [15, 0, 3, 16, 14, 16, steel], [1, 6, 3, 15, 7, 15, steel],
                       [2, 7, 5, 8, 10, 13, "bunk_grey"], [9, 7, 5, 14, 9, 13, "cardboard"]],
        "Booking Counter", "METAL", loot="office")
    add("sh_camera", [[7, 0, 7, 9, 1, 9, iron], [7.5, 1, 7.5, 8.5, 18, 8.5, iron], [3, 0, 7.6, 7.6, 1, 8.4, iron], [8.4, 0, 7.6, 13, 1, 8.4, iron],
                      [7.6, 0, 3, 8.4, 1, 7.6, iron], [5, 18, 5, 11, 23, 12, "ext_black_metal"], [6.5, 19, 3, 9.5, 22, 5, steel],
                      [6, 23, 6, 10, 24.5, 9, "ext_black_metal"]], "Booking Camera", "METAL")
    add("sh_ink", [[1, 13, 1, 15, 14, 15, {"up": "sh_ink", "side": steel, "down": steel}], [2, 0, 2, 14, 13, 14, steel]], "Fingerprint Station", "METAL")
    add("sh_cuff_bench", [[0, 7, 6, 16, 8.5, 15, {"up": steel, "side": steel, "down": steel}], [1, 0, 7, 2, 7, 14, steel], [14, 0, 7, 16, 7, 14, steel],
                          [0, 8.5, 15, 16, 9.5, 16, iron], [3, 9.5, 15.2, 4, 12, 15.8, iron], [9, 9.5, 15.2, 10, 12, 15.8, iron]],
        "Cuff Bench", "METAL")

    # the cells: the steel bunk on its brackets off the wall, the number over the door
    bunk = {"up": "bunk_grey", "side": steel, "down": steel}
    for side in ("l", "r"):
        boxes = [[0, 6.5, 4, 16, 8, 16, bunk], [0, 8, 5, 16, 9.2, 15.5, "home_mattress"]]
        if side == "l":
            boxes += [[1, 9.2, 9, 6, 10.8, 15, "home_pillow"], [1, 0, 14, 2, 6.5, 16, steel], [0, 8, 4, 0.6, 10, 16, steel]]
        else:
            boxes += [[14, 0, 14, 15, 6.5, 16, steel], [15.4, 8, 4, 16, 10, 16, steel], [6, 9.2, 6, 15, 9.8, 15, "fs_blanket"]]
        add(f"sh_bunk_{side}", boxes, "Cell Bunk", "METAL")
    for n in range(1, 7):
        add(f"sh_cell_sign_{n}", [[3, 4, 15.4, 13, 11, 16, {"north": f"sh_cell_sign_{n}", "side": steel}]], "Cell Number", "METAL", decor=True)

    # interview: the one-way glass, the table with the cuff ring
    add("sh_mirror", [[0, 0, 7, 16, 16, 9, {"north": "sh_mirror", "south": "sh_mirror", "side": steel, "up": steel, "down": steel}]],
        "One-Way Glass", "GLASS")
    add("sh_interview_table", [[0, 13, 0, 16, 14.5, 16, {"up": steel, "side": steel, "down": steel}], [7, 0, 7, 9, 13, 9, iron],
                               [4, 0, 4, 12, 0.6, 12, iron], [6.5, 14.5, 7.5, 9.5, 16, 8.5, iron]], "Interview Table", "METAL")

    # outside: the radio tower, the phone, the sign
    leg = "sh_lattice"
    tower = [[1, 0, 1, 2, 16, 2, leg], [14, 0, 1, 15, 16, 2, leg], [7.5, 0, 14, 8.5, 16, 15, leg],
             [2, 0, 1.2, 14, 0.6, 1.8, leg], [2, 8, 1.2, 14, 8.6, 1.8, leg],
             [1.6, 0, 2, 2.2, 0.6, 14, leg], [1.6, 8, 2, 2.2, 8.6, 14, leg], [13.8, 0, 2, 14.4, 0.6, 14, leg], [13.8, 8, 2, 14.4, 8.6, 14, leg]]
    for y in range(0, 16, 4):
        tower += [[2 + y * 0.5, y, 1.3, 3 + y * 0.5, y + 4, 1.7, leg]]
    add("sh_tower", tower, "Radio Tower", "METAL", rotate=None, render="cutout")
    for lit in ("", "_lit"):
        top = tower[:3] + [[7.5, 16 - 0.5, 7.5, 8.5, 16, 8.5, leg], [1, 15, 1, 15, 16, 15, leg],
                           [3, 16, 3, 3.6, 30, 3.6, steel], [12, 16, 4, 12.6, 26, 4.6, steel],
                           [6.5, 16, 6.5, 9.5, 18, 9.5, f"sh_beacon{lit}"]]
        add(f"sh_tower_top{lit}", top, "Radio Tower", "METAL", rotate=None, **({"light": 9} if lit else {}))
    for lit in ("", "_lit"):
        add(f"sh_phone{lit}", [[3, 2, 10, 13, 14, 16, {"north": f"sh_phone_face{lit}", "side": steel,
                                                       "up": steel, "down": steel}], [4, 14, 9, 12, 15, 16, steel]],
            "Emergency Phone", "METAL", decor=True, **({"light": 4} if lit else {}))
    add("sh_sign_post", [[7, 0, 7, 9, 16, 9, steel]], "Sign Post", "METAL", rotate=None)
    add("sh_sign_reserved", [[7, 0, 7, 9, 4, 9, steel], [2, 4, 9, 14, 16, 9.6, {"north": "sh_reserved", "south": steel, "side": steel}]],
        "Reserved Sign", "METAL")
    for k in PLAQUES:
        add(f"sh_plaque_{k}", [[3, 4, 15.4, 13, 11.5, 16, {"north": f"sh_plaque_{k}", "side": "cv_brass"}]], "Door Plaque", "METAL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("sh_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} sheriff blocks in {path}")


if __name__ == "__main__":
    main()
