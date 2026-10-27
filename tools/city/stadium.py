"""Grayhaven Stadium: the st_ kit for the city's football bowl (world/county/Stadium.java).

Home of the Grayhaven Gales (storm blue and amber gold). Every name is made up: the Gales, the
sponsors on the field wall (RIDGE COLA, MERCY GENERAL, KESTREL TIRE, WGHN 104.3), the shelter's
signs.

Blocks (tile 0 is always the viewer's left; a picture laid on the ground reads upright for a
viewer standing on the side its facing points away from):
- the rows: st_seat_{b,g,s}(_up,_broken,_blanket,_trash) (two moulded seats to a block on a
  rail bolted to the riser, the sitter faces north in the base), st_tread, st_tread_gum, st_step
  (an aisle step with its yellow nosing), st_rail_glass (the glass guard with its cap rail),
  st_rail (galvanized pipe), st_rowplate;
- the field wall: st_pad, st_wallcap, st_ad_{a,b,c,d}_0..7 (four by two, row 0 the upper);
- the field: st_turf_white, st_yard_0..3 (a yard line across the block, a quarter further south
  each), st_hash_0..3, st_num_{1..5}{l,r,x}_{0,2,3,5} and st_num_{1..5}m{0..3}_{1,4} (three by two;
  the middle column carries the yard line at its quarter), st_ez, st_ez_0..179 (thirty by six);
- the ribbon board: st_ribbon_{a,b}_0..31 (sixteen by two, row 0 the upper), st_ribbon_off.

    python3 tools/city/stadium.py        rewrites the st_ entries in blocks.json

photo_textures.py draws the textures (stadium.register()). The base model faces north: anything
on a wall has its back at z = 16. A face only shows the part of its texture under its box.
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import school  # noqa: E402
import sheriff  # noqa: E402
from furniture import area, disc, edges  # noqa: E402
from school import Pic, mixc  # noqa: E402
from gasstation import glow, weather  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/Inter-Black.otf"
BLACKI = "/usr/share/fonts/opentype/inter/Inter-BlackItalic.otf"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
SANS = school.SANS
COND = school.COND
WHITE = (236, 236, 230)
STORM = (34, 62, 112)
GOLD = (216, 160, 40)
SLATE = (118, 126, 134)
NAVY = (22, 30, 52)
SEATS = {"b": STORM, "g": GOLD, "s": SLATE}


def plastic(seed, rgb, fade=0.0):
    """Moulded seat plastic: a fine stipple, a soft sheen, bleached chalky by the sun where faded;
    at the top of each seat's back (columns 12..18 and 45..51, where the two backs' boxes put them)
    its little black number plate, the digits worn white."""
    t = F.plain(seed, rgb, 0.04, 0.55)
    t.f0[:] = 40
    t.speckle(0.25, mixc(rgb, (0, 0, 0), 0.25), 0.25)
    if fade:
        m = np.clip(P.fbm(t.rng) * 0.8 + fade, 0, 1)
        t.mix(m, mixc(rgb, (210, 210, 205), 0.45), 0.7)
        t.smooth -= m * 0.35
    for x in (12, 45):
        area(t, x, 5, x + 7, 10, (26, 26, 28), f0=60)
        for k in range(2):
            area(t, x + 1 + k * 3, 6, x + 3 + k * 3, 9, (214, 214, 206))
    t.grime(0.12)
    return t


def powder(seed, rgb=(58, 60, 64)):
    t = F.plain(seed, rgb, 0.04, 0.45)
    t.f0[:] = 60
    weather(t, rust=0.3, streak=0.3, grime=0.2)
    return t


def galv(seed):
    """Galvanized pipe: spangled grey, dull, white rust in spots."""
    t = F.plain(seed, (150, 154, 156), 0.08, 0.4)
    t.f0[:] = 180
    t.speckle(0.2, (186, 190, 190), 0.4)
    t.speckle(0.05, (210, 210, 200), 0.5)
    t.grime(0.2)
    return t


def tread_top(seed, gum=0.0):
    """The precast tread: broom-finished grey concrete, sealer worn off along the walkway, the
    dark ring of a spilt drink, gum trodden flat, a sawn joint."""
    t = F.mat("Concrete034", (156, 154, 148), 0.6, span=2, seed=seed, smooth=0.18)
    rng = t.rng
    for y in range(N):
        area(t, 0, y, N, y + 1, mul=float(rng.uniform(0.97, 1.03)))
    if rng.random() < 0.5:
        area(t, 0, 31, N, 32, mul=0.7, dh=-0.2)
    for _ in range(int(2 + gum * 18)):
        x, y = rng.uniform(2, N - 2, 2)
        disc(t, x, y, rng.uniform(0.6, 1.4), (60 + rng.integers(0, 40),) * 3, h=0.8)
    for _ in range(int(rng.integers(0, 3))):
        F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(4, 9), col=(96, 84, 64), amount=0.3)
    t.grime(0.2)
    return t


def riser(seed):
    """A riser's face: smooth formed concrete, rain run down it from the tread's lip."""
    t = F.mat("Concrete031", (150, 148, 142), 0.6, seed=seed, smooth=0.15)
    t.streaks(0.8, dark=0.8)
    area(t, 0, 0, N, 2, mul=0.85)
    t.grime(0.15)
    return t


def nosing(seed):
    t = F.plain(seed, (214, 176, 40), 0.06, 0.4)
    for x in range(0, N, 4):
        area(t, x, 0, x + 2, N, mul=0.9, dh=-0.1)
    t.grime(0.3)
    t.speckle(0.04, (60, 60, 60), 0.5)
    return t


def rail_glass(seed):
    """Laminated guard glass: a green edge tint, smears, dust settled at the bottom."""
    t = F.plain(seed, (170, 196, 190), 0.02, 0.92)
    t.f0[:] = 30
    t.alpha[:] = 90
    yy = np.arange(N, dtype=np.float32)[:, None]
    dust = np.clip((yy - (N - 18)) / 18, 0, 1) * (0.5 + 0.5 * P.fbm(t.rng)) * np.ones((1, N), np.float32)
    t.mix(dust, (150, 140, 120), 0.6)
    t.alpha[:] = np.clip(t.alpha + dust * 120, 0, 255)
    for _ in range(4):
        x = t.rng.uniform(0, N)
        F.fingers(t, x, t.rng.uniform(10, 40), 5, 0.2)
    area(t, 0, 0, N, 2, (200, 220, 214), alpha=200)
    return t


def pad(seed, rgb=STORM):
    """The field wall's padding: vinyl over foam in panels, the seams pulled in, scuffed by cleats."""
    t = F.plain(seed, mixc(rgb, (0, 0, 0), 0.2), 0.05, 0.5)
    t.f0[:] = 30
    for x in (0, 31):
        area(t, x, 0, x + 2, N, mul=0.55, dh=-0.3)
    area(t, 0, 0, N, 2, mul=0.55, dh=-0.3)
    xx = np.arange(N, dtype=np.float32)[None, :]
    t.h += (np.sin(xx / N * np.pi * 2) * 0.03)[0][None, :]
    rng = t.rng
    for _ in range(6):
        x, y = rng.uniform(0, N), rng.uniform(N * 0.6, N)
        area(t, x, y, x + rng.uniform(2, 5), y + 0.6, mul=0.75)
    t.grime(0.25)
    return t


def wallcap(seed):
    t = F.mat("Concrete031", (164, 162, 156), 0.5, seed=seed, smooth=0.2)
    t.grime(0.2)
    return t


# ---- the field wall's sponsors (four by two each, at 4 px to the texel)
ADS = {
    "a": ("RIDGE", "COLA", (176, 28, 32), WHITE, (232, 196, 60)),
    "b": ("MERCY GENERAL", "HEALTH SYSTEM", (238, 238, 236), (20, 74, 140), (200, 40, 40)),
    "c": ("KESTREL", "TIRE & AUTO", (24, 24, 26), (238, 196, 36), (238, 196, 36)),
    "d": ("WGHN", "104.3 FM", (232, 106, 28), (16, 16, 18), WHITE),
}


@lru_cache(maxsize=None)
def ad_pic(k):
    """A sponsor's panel, 256 x 128 texels."""
    big, small, bg, ink, accent = ADS[k]
    p = Pic(4, 2, bg, seed=ord(k))
    W, H = p.W, p.H
    if k == "a":
        # the soda's roundel: a white ring and a peak in it, the name beside
        p.ellipse(14, 30, 82, 98, fill=WHITE)
        p.ellipse(20, 36, 76, 92, fill=bg)
        p.poly([(28, 80), (48, 46), (68, 80), (60, 80), (48, 60), (36, 80)], fill=WHITE)
        p.text(168, 54, big, BLACKI, 44, ink, anchor="mm")
        p.text(168, 94, small, BLACKI, 30, accent, anchor="mm")
    elif k == "b":
        p.rect(16, 36, 32, 92, fill=accent)
        p.rect(-4, 56, 52, 72, fill=accent)
        p.text(152, 56, big, BLACK, 21, ink, anchor="mm")
        p.text(152, 84, small, BOLD, 15, ink, anchor="mm")
    elif k == "c":
        p.rect(0, 0, W, 9, fill=accent)
        p.rect(0, H - 9, W, H, fill=accent)
        p.text(W / 2, 56, big, BLACKI, 46, ink, anchor="mm")
        p.text(W / 2, 96, small, BOLD, 17, WHITE, anchor="mm")
    else:
        p.ellipse(18, 22, 102, 106, fill=ink)
        p.text(60, 66, big, BLACK, 24, bg, anchor="mm")
        p.text(184, 52, small.split()[0], BLACKI, 44, ink, anchor="mm")
        p.text(184, 92, small.split()[1], BLACK, 22, accent, anchor="mm")
    return p


def ad(k, i):
    t = ad_pic(k).tile(i, 9600 + ord(k) * 8 + i, smooth=0.5, grime=0.2)
    rng = t.rng
    # the padding's seams through the print, scuffs, a cleat gash now and then
    area(t, 0, 0, 1, N, mul=0.6, dh=-0.3)
    if i >= 4:
        for _ in range(int(rng.integers(0, 6))):
            x, y = rng.uniform(0, N), rng.uniform(N * 0.6, N)
            area(t, x, y, x + rng.uniform(2, 5), y + 0.6, mul=0.75)
    return t


# ---- the field

def turf(seed, dark=False):
    return school.turf(seed, dark=dark)


def paint(t, mask, amount=0.92):
    """Field paint: white over the grass, the blades poking through it."""
    rng = t.rng
    holes = rng.random((N, N)) < 0.12
    m = np.clip(mask, 0, 1) * np.where(holes, 0.45, 1.0) * amount
    t.mix(m, (234, 234, 226))
    t.h = t.h * (1 - m * 0.3) + 0.7 * m * 0.3
    return t


def turf_white(seed):
    t = turf(seed)
    return paint(t, np.ones((N, N), np.float32))


def yard(seed, k, tick=False):
    """A yard line across the block (a hash mark's tick, short, when tick): six texels wide (4 in.)
    centred 8 + 16k texels from the block's north edge."""
    t = turf(seed, dark=bool(k % 2))
    m = np.zeros((N, N), np.float32)
    y = 8 + 16 * k
    x0, x1 = (13, 51) if tick else (0, N)
    m[y - 3:y + 3, x0:x1] = 1
    return paint(t, m)


@lru_cache(maxsize=None)
def num_pic(n, arrow):
    """A yard number six feet tall, read from the sideline: the two digits either side of the yard
    line (which the middle tiles carry, drawn where the line falls), the arrow toward the nearer
    goal on the left (l) or the right (r)."""
    p = Pic(3, 2, (0, 0, 0, 0), seed=n)
    W, H = p.W, p.H
    if arrow == "l":
        p.poly([(4, H / 2), (20, H / 2 - 10), (20, H / 2 + 10)], fill=(255, 255, 255))
    elif arrow == "r":
        p.poly([(W - 4, H / 2), (W - 20, H / 2 - 10), (W - 20, H / 2 + 10)], fill=(255, 255, 255))
    p.text(W / 2 - 6, H / 2 + 2, str(n), BLACK, 116, (255, 255, 255), anchor="rm")
    p.text(W / 2 + 6, H / 2 + 2, "0", BLACK, 116, (255, 255, 255), anchor="lm")
    return p


def num(n, arrow, i, q=None):
    """Tile i of a yard number; a middle tile (i 1 or 4) with the yard line at quarter q."""
    t = turf(9700 + n * 8 + i + (q or 0) * 16 + ord(arrow), dark=bool(q or 0) and q % 2 == 1)
    a = num_pic(n, arrow).shrunk()
    cx, cy = (i % 3) * N, (i // 3) * N
    m = a[cy:cy + N, cx:cx + N, 3] / 255
    if q is not None:
        line = np.zeros((N, N), np.float32)
        x = 8 + 16 * q
        line[:, x - 3:x + 3] = 1
        m = np.maximum(m, line)
    return paint(t, m)


@lru_cache(maxsize=None)
def ez_pic():
    """The end zone: GALES in heavy italic capitals, white with a gold edge, on storm blue."""
    p = Pic(30, 6, (0, 0, 0, 0), seed=77)
    W, H = p.W, p.H
    f = p.font(BLACKI, 330)
    from PIL import ImageDraw
    big = Image.new("RGBA", p.im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.text((W * 2, H * 2 + 40), "GALES", font=f, fill=(255, 255, 255, 255), anchor="mm", stroke_width=40, stroke_fill=(216, 160, 40, 255))
    p.im.alpha_composite(big)
    return p


def ez(i=None, seed=0):
    t = turf(9800 + (i or 0) if i is not None else 9799)
    # the end zone's paint: storm blue, worn where the players ran
    m = np.ones((N, N), np.float32) * 0.9
    rng = t.rng
    holes = rng.random((N, N)) < 0.15
    m = np.where(holes, 0.5, m)
    t.mix(m, STORM)
    if i is not None:
        a = ez_pic().shrunk()
        cx, cy = (i % 30) * N, (i // 30) * N
        tile = a[cy:cy + N, cx:cx + N]
        alpha = tile[..., 3:] / 255 * np.where(holes, 0.6, 0.95)[..., None]
        t.c = t.c * (1 - alpha) + tile[..., :3] * alpha
    t.grime(0.1)
    return t


# ---- the ribbon board on the upper deck's fascia (sixteen by two)
RIBBON = {"a": "EMERGENCY SHELTER  •  STAY IN YOUR AREA  •",
          "b": "GO GALES  •  GRAYHAVEN STADIUM  •"}


@lru_cache(maxsize=None)
def ribbon_mask(k):
    from PIL import ImageDraw, ImageFont
    W, H = 16 * N, 2 * N
    im = Image.new("L", (W * 4, H * 4), 0)
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(BLACK, 300)
    s = RIBBON[k]
    size = 300
    while d.textlength(s, font=f) > W * 4 - 80 and size > 60:
        size -= 10
        f = ImageFont.truetype(BLACK, size)
    d.text((W * 2, H * 2), s, font=f, fill=255, anchor="mm")
    a = np.asarray(im.resize((W, H), Image.BOX), np.float32) / 255
    # light it as a grid of LEDs: two texels to a pixel
    yy, xx = np.mgrid[0:H, 0:W]
    dots = ((yy % 2 == 0) & (xx % 2 == 0)).astype(np.float32)
    blocky = a.reshape(H // 2, 2, W // 2, 2).max(axis=(1, 3))
    return np.repeat(np.repeat(blocky, 2, 0), 2, 1) * (0.55 + 0.45 * dots)


def ribbon(k, i):
    t = F.plain(9900 + ord(k) * 40 + i, (14, 14, 16), 0.03, 0.7)
    yy, xx = np.mgrid[0:N, 0:N]
    t.mix(((yy % 2 == 0) & (xx % 2 == 0)).astype(np.float32), (34, 30, 26), 0.8)
    m = ribbon_mask(k)
    cx, cy = (i % 16) * N, (i // 16) * N
    tile = m[cy:cy + N, cx:cx + N]
    # a dead module now and then: a dark square across the letters
    if t.rng.random() < 0.15:
        x, y = t.rng.integers(0, N - 16, 2)
        tile = tile.copy()
        tile[y:y + 16, x:x + 16] = 0
    glow(t, tile, (255, 178, 60), 1.0, 0.95)
    return t


def ribbon_off(seed):
    t = F.plain(seed, (16, 16, 18), 0.03, 0.7)
    yy, xx = np.mgrid[0:N, 0:N]
    t.mix(((yy % 2 == 0) & (xx % 2 == 0)).astype(np.float32), (40, 38, 36), 0.8)
    t.grime(0.2)
    return t


def housing(seed):
    t = powder(seed, (36, 38, 42))
    return t


def rowplate(seed):
    t = F.plain(seed, (210, 210, 204), 0.03, 0.5)
    m = F.text_mask("ROW", BOLD, 7, 24, 9, seed=seed)
    F.paste(t, m, 20, 18, (30, 30, 30))
    m = F.text_mask(str(seed % 27 + 1), BLACK, 18, 40, 22, seed=seed)
    F.paste(t, m, 12, 28, STORM)
    t.grime(0.2)
    return t



# ---- the concourses: walls, floor, the stands' signs and menus, the wayfinding

def floor(seed, spill=False):
    """The concourse floor: sealed grey concrete gone dull in the traffic lanes, gum, a spill."""
    t = F.mat("Concrete034", (138, 138, 134), 0.5, span=2, seed=seed, smooth=0.45)
    rng = t.rng
    for _ in range(int(rng.integers(4, 10))):
        disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(0.5, 1.2), (70, 68, 66), h=0.8)
    if spill:
        F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(5, 9), col=(96, 84, 64), amount=0.25)
    t.grime(0.15)
    return t


STANDS = {
    # header text, the panel colour, the ink, the menu (name, price) pairs
    "grill": ("GALES GRILL", "HOT DOGS • BURGERS • FRIES", STORM, WHITE,
              [("HOT DOG", "5.50"), ("FOOTLONG", "7.75"), ("CHEESEBURGER", "8.00"), ("FRIES", "4.50"), ("CHICKEN TENDERS", "9.25"), ("SODA", "4.75")]),
    "pizza": ("SLICE", "PIZZA BY THE SLICE", (176, 40, 34), (250, 236, 200),
              [("CHEESE SLICE", "6.00"), ("PEPPERONI", "6.75"), ("SUPREME", "7.50"), ("BREADSTICKS", "4.25"), ("SALAD", "6.50"), ("WATER", "4.00")]),
    "brew": ("GRAYHAVEN BREWING", "COLD BEER ON TAP", (28, 30, 32), GOLD,
             [("DOMESTIC DRAFT", "8.50"), ("HARBOR LAGER", "9.75"), ("STORM IPA", "10.50"), ("CANNED", "9.00"), ("SELTZER", "9.00"), ("PRETZEL", "6.00")]),
    "sweet": ("SWEET SPOT", "KETTLE CORN • PRETZELS • ICE CREAM", (232, 196, 60), (60, 30, 20),
              [("KETTLE CORN", "6.25"), ("SOFT PRETZEL", "6.00"), ("ICE CREAM", "5.50"), ("COTTON CANDY", "5.00"), ("COFFEE", "3.75"), ("COCOA", "3.75")]),
    "nacho": ("THE END ZONE", "NACHOS • TACOS • BBQ", (36, 96, 64), (240, 230, 200),
              [("NACHOS", "7.25"), ("LOADED NACHOS", "10.00"), ("TACOS (2)", "8.50"), ("BBQ SANDWICH", "11.00"), ("CHURRO", "5.00"), ("SODA", "4.75")]),
}


HEADERS = {**{k: v[:4] for k, v in STANDS.items()},
           "store": ("GALES PRO SHOP", "JERSEYS • CAPS • GEAR", NAVY, GOLD),
           "guest": ("GUEST SERVICES", "FIRST AID • LOST & FOUND", (30, 104, 60), WHITE),
           "tickets": ("TICKETS", "ALL EVENTS • WILL CALL", STORM, WHITE)}


@lru_cache(maxsize=None)
def stand_pic(k):
    """A stand's lit header over its counter, eight blocks across."""
    name, sub, bg, ink = HEADERS[k]
    p = Pic(8, 1, bg, seed=ord(k[0]))
    W, H = p.W, p.H
    p.rect(0, 0, W, 3, fill=mixc(bg, (255, 255, 255), 0.3))
    p.rect(0, H - 3, W, H, fill=mixc(bg, (0, 0, 0), 0.4))
    f = civic.fit(name, BLACK, 30, 300)
    p.text(150, H / 2 + 1, name, BLACK, f, ink, anchor="mm")
    f2 = civic.fit(sub, BOLD, 16, 200)
    p.text(W - 112, H / 2 + 1, sub, BOLD, f2, ink, anchor="mm")
    return p


def stand(k, i):
    t = stand_pic(k).tile(i, 9550 + ord(k[0]) * 8 + i, smooth=0.6, grime=0.15)
    lit = t.c @ F.LUM
    t.emit[:] = np.clip(lit / 255, 0, 1) * 0.5
    if t.rng.random() < 0.25:
        # a cracked panel, the tube behind it dead
        x = t.rng.uniform(10, 50)
        area(t, x, 0, x + 18, N, mul=0.45)
        t.emit[:, int(x):int(x) + 18] = 0
    return t


@lru_cache(maxsize=None)
def menu_pic(k):
    """The menu board behind the counter, two blocks across: black, prices in the stand's colour."""
    items, bg, ink = STANDS[k][4], STANDS[k][2], STANDS[k][3]
    p = Pic(2, 1, (18, 18, 20), seed=3 + ord(k[0]))
    p.rect(0, 0, p.W, 9, fill=bg)
    p.text(p.W / 2, 4.5, STANDS[k][0], BLACK, 6, ink, anchor="mm")
    for j, (item, price) in enumerate(items):
        y = 15 + j * 8
        p.text(6, y, item, BOLD, 5, (232, 232, 226), anchor="lm")
        p.text(p.W - 6, y, price, BLACK, 5, GOLD, anchor="rm")
    return p


def menu(k, i):
    t = menu_pic(k).tile(i, 9560 + ord(k[0]) * 2 + i, smooth=0.7, grime=0.1)
    lit = t.c @ F.LUM
    t.emit[:] = np.clip(lit / 255, 0, 1) * 0.6
    return t


WAYS = {
    "sec_l": "◀  SECTIONS", "sec_r": "SECTIONS  ▶", "rest": "RESTROOMS", "gate": "EXIT • GATES",
    "aid": "FIRST AID", "stairs": "STAIRS • 300 LEVEL", "club": "CLUB LEVEL", "shelter": "SHELTER CHECK-IN",
}


@lru_cache(maxsize=None)
def way_pic(k):
    p = Pic(2, 1, (20, 46, 92), seed=len(k))
    p.rect(0, 0, p.W, 2, fill=GOLD)
    p.rect(0, p.H - 2, p.W, p.H, fill=GOLD)
    text = WAYS[k]
    font = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    f = civic.fit(text, font, 14, p.W - 8)
    p.text(p.W / 2, p.H / 2 + 1, text, font, f, WHITE, anchor="mm")
    if k == "shelter":
        p.rect(0, 0, p.W, p.H, outline=(200, 40, 40), width=2)
    return p


def way(k, i):
    t = way_pic(k).tile(i, 9570 + len(k) * 2 + i, smooth=0.55, grime=0.12)
    weather(t, rust=0.1, streak=0.2, grime=0.15)
    return t


def grille(seed):
    """A roll-down counter grille: rods and links in a brick pattern, the gaps cut out."""
    t = F.plain(seed, (168, 170, 170), 0.04, 0.6)
    t.f0[:] = 200
    yy, xx = np.mgrid[0:N, 0:N]
    bars = (yy % 6 < 2) | (((xx + (yy // 6 % 2) * 8) % 16) < 2)
    t.alpha[:] = np.where(bars, 255, 0)
    t.grime(0.25)
    return t


def slats(seed):
    """A closed roll-down door: steel slats with their ribs."""
    t = F.plain(seed, (150, 152, 150), 0.04, 0.45)
    t.f0[:] = 180
    for y in range(0, N, 5):
        area(t, 0, y, N, y + 1, mul=0.65, dh=-0.2)
        area(t, 0, y + 1, N, y + 2, mul=1.12, dh=0.1)
    weather(t, rust=0.3, streak=0.5, grime=0.3)
    return t


def warmer_glass(seed):
    """The hot-dog roller's case: glass, the rollers behind it, the dogs gone grey."""
    t = F.plain(seed, (40, 40, 40), 0.03, 0.8)
    for y in range(20, 56, 6):
        area(t, 4, y, N - 4, y + 3, (150, 150, 150), f0=200)
        if t.rng.random() < 0.6:
            area(t, 8 + t.rng.uniform(0, 20), y, 30 + t.rng.uniform(0, 26), y + 3, (120, 90, 70))
    area(t, 0, 0, N, 14, (176, 40, 34))
    m = F.text_mask("HOT DOGS", BOLD, 7, 56, 10, seed=1)
    F.paste(t, m, 4, 2, WHITE)
    t.grime(0.2)
    return t


def fountain_face(seed):
    """A soda fountain: six lit valve badges over the drip tray, the ice lever."""
    t = F.plain(seed, (40, 42, 46), 0.03, 0.6)
    cols = [(176, 28, 32), (30, 90, 170), (40, 140, 60), (230, 180, 40), (200, 90, 30), (120, 40, 140)]
    for j, c in enumerate(cols):
        x = 4 + j * 10
        area(t, x, 14, x + 8, 26, c)
        area(t, x + 2, 26, x + 6, 34, (200, 200, 200), f0=200)
    area(t, 0, 44, N, 52, (170, 172, 172), f0=200)
    m = F.text_mask("RIDGE COLA", BLACKI, 8, 60, 10, seed=2)
    F.paste(t, m, 2, 2, (220, 40, 40))
    glow(t, np.pad(np.ones((12, 58), np.float32), ((14, 38), (4, 2))) * 0.5, (255, 255, 240), 0.2, 0.4)
    t.grime(0.2)
    return t


def trough(seed):
    t = F.plain(seed, (190, 194, 196), 0.03, 0.8)
    t.f0[:] = 230
    yy = np.arange(N, dtype=np.float32)[:, None]
    t.c *= (1.05 - 0.15 * yy / N)[..., None]
    t.grime(0.3, col=(150, 130, 70))
    return t


def tile_wall(seed, rgb=(214, 214, 208)):
    """Restroom wall tile: 4-inch squares, grout gone grey."""
    t = F.plain(seed, rgb, 0.03, 0.7)
    for v in range(0, N, 8):
        area(t, 0, v, N, v + 1, mul=0.8, dh=-0.1)
        area(t, v, 0, v + 1, N, mul=0.8, dh=-0.1)
    t.grime(0.2)
    return t


# ---- the club level

def carpet(seed):
    """Club carpet: storm blue with a gold fleck, walked flat along the corridor."""
    t = F.mat("Carpet006", STORM, 0.3, seed=seed, smooth=0.05)
    t.speckle(0.03, GOLD, 0.6)
    t.grime(0.2)
    return t


def glazing(seed, broken=False, tint=(84, 104, 116)):
    """Curtain wall glass: a pane in its dark mullions, the sky in it."""
    t = P.glass(seed, tint, 0.0, broken)
    t.frame(2, (46, 48, 52), smooth=0.5)
    area(t, 0, 31, N, 33, (46, 48, 52), alpha=255)
    return t


# ---- the outside: precast, fins, the canopy, the lights, the video boards

def precast(seed, reveal=True, stain=0.5):
    """The facade's precast panels: warm grey architectural concrete, a sandblasted face, the
    reveal between panels, rust streaks from the anchors, rain."""
    t = F.mat("Concrete031", (178, 170, 156), 0.55, seed=seed, smooth=0.15)
    t.speckle(0.15, (196, 188, 172), 0.3)
    if reveal:
        area(t, 0, N - 2, N, N, mul=0.55, dh=-0.4)
        area(t, N - 2, 0, N, N, mul=0.55, dh=-0.4)
    t.streaks(stain, dark=0.75)
    if t.rng.random() < stain * 0.5:
        x = t.rng.uniform(8, 56)
        m = np.zeros((N, N), np.float32)
        m[10:10 + int(t.rng.uniform(15, 45)), int(x):int(x) + 2] = 0.6
        t.mix(P.blur(m, 1), (120, 72, 40), 0.6)
    return t


def deck(seed):
    """The canopy's underside: ribbed metal deck painted white, gone grey and streaked."""
    t = F.plain(seed, (190, 192, 190), 0.04, 0.4)
    for x in range(0, N, 8):
        area(t, x, 0, x + 3, N, mul=0.82, dh=-0.25)
    weather(t, rust=0.3, streak=0.4, grime=0.35)
    return t


def membrane(seed):
    t = F.plain(seed, (150, 152, 150), 0.06, 0.25)
    t.grime(0.4)
    return t


def truss_paint(seed):
    t = F.plain(seed, (210, 212, 210), 0.04, 0.45)
    t.f0[:] = 60
    weather(t, rust=0.5, streak=0.4, grime=0.3)
    return t


def lamp_face(seed, on):
    """A floodlight's face: the reflector, the arc tube, the guard."""
    t = F.plain(seed, (60, 62, 64), 0.03, 0.5)
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.hypot(xx - 32, yy - 32)
    m = np.clip((26 - r) / 4, 0, 1)
    t.mix(m, (220, 224, 226) if on else (120, 124, 126))
    if on:
        glow(t, np.clip((18 - r) / 6, 0, 1), (255, 250, 230), 1.0, 1.0)
    for x in range(8, N, 8):
        area(t, x, 6, x + 1, N - 6, (40, 40, 40))
    t.frame(3, (52, 54, 56))
    return t


def screen(seed, dead=False):
    """A video board's face: black LED modules on a grid, dust, a dead block."""
    t = F.plain(seed, (14, 14, 16), 0.03, 0.7)
    yy, xx = np.mgrid[0:N, 0:N]
    t.mix(((yy % 2 == 0) & (xx % 2 == 0)).astype(np.float32), (32, 32, 34), 0.8)
    for v in (0, 31, 63):
        area(t, 0, v, N, v + 1, (6, 6, 8))
        area(t, v, 0, v + 1, N, (6, 6, 8))
    if dead:
        t.crack(6, 0.3, (60, 60, 60))
        x, y = t.rng.integers(0, 33, 2)
        area(t, x, y, x + 31, y + 31, (30, 30, 30))
    t.grime(0.2)
    return t


# ---- part 3: section numbers, plaques, and what the shelter left behind

@lru_cache(maxsize=None)
def sec_pic():
    """The section plates over the vomitories: SEC then one digit a block, storm blue, a gold rule
    top and bottom running on from block to block."""
    p = Pic(11, 1, STORM, seed=77)
    p.rect(0, 10, p.W, 12, fill=GOLD)
    p.rect(0, p.H - 12, p.W, p.H - 10, fill=GOLD)
    p.text(N / 2, N / 2 + 1, "SEC", BLACK, 22, WHITE, anchor="mm")
    for d in range(10):
        p.text(N * (d + 1.5), N / 2 + 2, str(d), BLACK, 34, WHITE, anchor="mm")
    return p


def sec(i):
    t = sec_pic().tile(i, 9650 + i, smooth=0.5, grime=0.12)
    weather(t, rust=0.05, streak=0.2, grime=0.1)
    return t


PLAQUES = {"men": "MEN", "women": "WOMEN", "family": "FAMILY", "staff": "STAFF|ONLY", "aid": "FIRST|AID",
           "store": "PRO|SHOP", "triage": "TRIAGE"}


def plaque_face(k):
    """A door's sign, drawn in the middle ten sixteenths the plate's face shows."""
    p = Pic(1, 1, NAVY if k not in ("aid", "triage") else (176, 30, 30), seed=len(k))
    p.rect(12, 12, 52, 52, outline=(200, 200, 196), width=1)
    text = PLAQUES[k]
    f = civic.fit(text, BLACK, 12, 34)
    lines = text.count("|") + 1
    p.text(N / 2, N / 2 - (lines - 1) * f * 0.58 + 1, text, BLACK, f, WHITE, anchor="mm")
    return p.tile(0, 9660 + len(k), smooth=0.6, grime=0.15)


def canvas(seed, rgb=(214, 212, 200)):
    """Shelter tent canvas: cream vinyl, the welded seams, mildew and mud up from the ground."""
    t = F.plain(seed, rgb, 0.05, 0.35)
    for x in (0, 31):
        area(t, x, 0, x + 2, N, mul=0.86, dh=0.15)
    yy = np.arange(N, dtype=np.float32)[:, None]
    t.c *= (1.0 - 0.25 * np.clip((yy - 44) / 20, 0, 1))[..., None]
    t.speckle(0.02, (90, 96, 70), 0.4)
    t.grime(0.3)
    return t


@lru_cache(maxsize=None)
def tent_pic(k):
    """The word on a tent's end, two blocks across, stencilled on the canvas."""
    p = Pic(2, 1, (0, 0, 0, 0), seed=len(k))
    col = (176, 30, 30) if k == "triage" else (40, 60, 110)
    f = civic.fit(k.upper(), BLACK, 22, p.W - 12)
    p.text(p.W / 2, p.H / 2, k.upper(), BLACK, f, col, anchor="mm")
    p.rect(8, p.H / 2 + f * 0.6, p.W - 8, p.H / 2 + f * 0.6 + 2, fill=col)
    return p


def tent_label(k, i):
    t = canvas(9670 + i + len(k))
    m = tent_pic(k).shrunk()[:, i * N:(i + 1) * N]
    a = m[..., 3:4] / 255.0 * 0.9
    t.c[:] = t.c * (1 - a) + m[..., :3] * a
    return t


def tarp(seed, rgb):
    """A triage tarp on the turf: creased poly, a grommet at each corner."""
    t = F.plain(seed, rgb, 0.06, 0.35)
    for v in (21, 42):
        area(t, v, 0, v + 1, N, mul=0.85, dh=-0.1)
        area(t, 0, v, N, v + 1, mul=0.85, dh=-0.1)
    for x, y in ((3, 3), (N - 5, 3), (3, N - 5), (N - 5, N - 5)):
        area(t, x, y, x + 2, y + 2, (200, 200, 190), f0=200)
    t.grime(0.35, col=(60, 50, 36))
    return t


def wrap_water(seed, top=False):
    """Bottled water by the pallet, still in its shrink-wrap: the cases' labels under the shine."""
    t = school.water_case(seed)
    if top:
        t = F.plain(seed, (210, 222, 230), 0.05, 0.9)
        for x in range(0, N, 8):
            for y in range(0, N, 8):
                disc(t, x + 4, y + 4, 2.6, (150, 190, 220), h=0.6)
    yy, xx = np.mgrid[0:N, 0:N]
    m = (np.sin((xx * 0.9 + yy * 0.4) * 0.35 + t.rng.uniform(0, 6)) > 0.85).astype(np.float32) * 0.3
    t.mix(m, (250, 250, 250))
    t.smooth[:] = 0.9
    return t


def box_meals(seed):
    """A case of shelf-stable meals: kraft board, the stencil, the tape."""
    t = F.plain(seed, (170, 134, 90), 0.06, 0.2)
    area(t, 0, 4, N, 7, (200, 180, 140))
    m = F.text_mask("EMERGENCY|MEALS", COND, 9, 60, 24, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 2, 14, (36, 32, 28), amount=0.85)
    m = F.text_mask("24 MEALS • KEEP DRY", COND, 5, 56, 8, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 4, 42, (36, 32, 28), amount=0.8)
    t.grime(0.25)
    return t


def genset(seed):
    """A towable light tower's genset: safety yellow, louvres, the stencil, rust."""
    t = F.plain(seed, (214, 176, 40), 0.04, 0.45)
    for y in range(18, 46, 4):
        area(t, 8, y, 56, y + 2, mul=0.55, dh=-0.3)
    m = F.text_mask("LIGHT TOWER 4", COND, 6, 50, 8, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 7, 6, (30, 30, 30))
    weather(t, rust=0.4, streak=0.4, grime=0.3)
    return t


def cooler(seed, top=False):
    """The sideline drink cooler: storm blue, GALES in white, the lid amber."""
    t = F.plain(seed, GOLD if top else STORM, 0.04, 0.6)
    if not top:
        m = F.text_mask("GALES", BLACK, 10, 60, 14, seed=seed)
        F.paste(t, (m > 0.4).astype(np.float32), 2, 22, WHITE)
        area(t, 0, 0, N, 4, GOLD)
    t.grime(0.2)
    return t


def poster(seed, missing):
    """Paper on the concourse walls: the shelter's rules, or a missing-person sheet, a photo and
    the details in marker."""
    p = Pic(1, 1, (0, 0, 0, 0), seed=seed)
    rng = np.random.default_rng(seed)
    w, h = 40, 52
    x0, y0 = 12 + rng.integers(-4, 5), 6 + rng.integers(-3, 4)
    p.rect(x0, y0, x0 + w, y0 + h, fill=(236, 232, 218))
    if missing:
        p.text(x0 + w / 2, y0 + 6, "MISSING", BLACK, 8, (30, 30, 30), anchor="mm")
        p.rect(x0 + 8, y0 + 12, x0 + w - 8, y0 + 34, fill=(110, 100, 90))
        p.ellipse(x0 + 15, y0 + 15, x0 + w - 15, y0 + 28, fill=(170, 140, 120))
        for k in range(3):
            p.rect(x0 + 5, y0 + 38 + k * 4, x0 + w - 5 - rng.integers(0, 12), y0 + 39 + k * 4, fill=(40, 40, 120))
    else:
        p.rect(x0, y0, x0 + w, y0 + 8, fill=(176, 30, 30))
        p.text(x0 + w / 2, y0 + 4, "SHELTER RULES", BOLD, 5, WHITE, anchor="mm")
        for k in range(8):
            p.rect(x0 + 4, y0 + 13 + k * 5, x0 + w - 4 - rng.integers(0, 14), y0 + 14 + k * 5, fill=(70, 70, 70))
    p.rect(x0 + w / 2 - 3, y0 - 1, x0 + w / 2 + 3, y0 + 2, fill=(200, 190, 140), a=200)
    return p.tile(0, seed, smooth=0.2, grime=0.2, h=0.6)


def duct(seed):
    """Galvanized spiral duct: the seam winding round it, dust on top."""
    t = galv(seed)
    for y in range(0, N, 12):
        area(t, 0, y, N, y + 1, mul=0.7, dh=0.3)
    t.grime(0.25)
    return t


LETTERS = "ABCDEFGHILMNORSTUVY"
BIG = "ADEGHILMNRSTUVY"


@lru_cache(maxsize=None)
def letter_pic(c):
    """A raised letter for the facade, two blocks square: brushed steel, the edge in shadow."""
    p = Pic(2, 2, (0, 0, 0, 0), seed=ord(c))
    p.text(N + 3, N + 5, c, BLACK, 118, (40, 40, 42, 200), anchor="mm")
    p.text(N, N + 2, c, BLACK, 118, (196, 198, 196), anchor="mm")
    return p


@lru_cache(maxsize=None)
def big_pic(c):
    """A channel letter for the stadium's name, three blocks square: storm-blue painted metal,
    a gold return round its edge, its shadow on the concrete."""
    from PIL import ImageDraw, ImageFont
    p = Pic(3, 3, (0, 0, 0, 0), seed=ord(c) + 7)
    f = ImageFont.truetype(BLACK, int(176 * 4))
    cx, cy = p.W * 2, p.H * 2 + 16
    p.d.text((cx + 20, cy + 26), c, font=f, fill=(30, 28, 26, 150), anchor="mm")
    p.d.text((cx, cy), c, font=f, fill=STORM + (255,), anchor="mm", stroke_width=14, stroke_fill=GOLD + (255,))
    return p


def big(c, i):
    t = big_pic(c).tile(i, 9900 + ord(c) * 9 + i, smooth=0.5, grime=0.1)
    t.f0[:] = np.where(t.alpha > 0, 60, t.f0)
    weather(t, rust=0.3, streak=0.5, grime=0.2)
    return t


def letter(c, i):
    t = letter_pic(c).tile(i, 9710 + ord(c) * 4 + i, smooth=0.55, grime=0.1)
    t.f0[:] = np.where(t.alpha > 0, 200, t.f0)
    weather(t, rust=0.15, streak=0.4, grime=0.15)
    return t


# ---- part 4: the outside worn, the ramp towers, the gates, the checkpoint, the lots

NS = None  # photo_textures, for the city's wear kinds

PRECAST = {"st_precast": lambda: precast(9603), "st_precast_b": lambda: precast(9604, stain=0.9),
           "st_precast_plain": lambda: precast(9605, reveal=False, stain=0.3)}
WEAR = ("stain", "soot", "moss", "crack", "damp")


def worn(base, kind):
    """Precast gone the way the city's walls go: photo_textures' wear kinds run on our concrete
    (rain and rust down from the ledges, soot fanned up from a fire, moss, settlement cracks);
    damp is the moss turned over, up from the ground instead of down from a ledge."""
    NS.PHOTO[base] = PRECAST[base]
    try:
        t = NS.KINDS["moss" if kind == "damp" else kind](base)
    finally:
        del NS.PHOTO[base]
    if kind == "damp":
        for a in ("c", "h", "smooth", "f0", "poro", "emit", "alpha"):
            setattr(t, a, getattr(t, a)[::-1].copy())
    return t


def ramp_top(seed):
    """The ramp's walking face: the concourse's broomed concrete, grooves across it for grip."""
    t = floor(seed)
    for y in range(4, N, 8):
        area(t, 0, y, N, y + 2, mul=0.72, dh=-0.35)
    t.grime(0.2)
    return t


def fascia(seed):
    """A gate canopy's fascia: storm-blue metal panels, a gold pinstripe, rust from the fixings."""
    t = F.plain(seed, STORM, 0.04, 0.5)
    t.f0[:] = 50
    area(t, 0, 52, N, 55, GOLD)
    for x in (0, 31):
        area(t, x, 0, x + 1, N, mul=0.6, dh=-0.2)
    weather(t, rust=0.4, streak=0.5, grime=0.3)
    return t


def col_paint(seed):
    t = F.plain(seed, STORM, 0.03, 0.45)
    t.f0[:] = 50
    weather(t, rust=0.5, streak=0.4, grime=0.35, dents=2)
    return t


def ticket_glass(seed, n, broken=False):
    """A ticket window from the plaza: dark glass in a steel frame, the speak-through disc, the
    tray slot, the window's number in gold leaf."""
    t = P.glass(seed, (52, 66, 74), 0.0, broken)
    t.alpha[:] = np.where(t.alpha > 0, 255, 0)
    if broken:
        # the hole shows the dark booth behind, not the sky
        t.c[t.alpha == 0] = (16, 16, 18)
        t.alpha[:] = 255
    t.frame(4, (128, 130, 132), smooth=0.55, f0=180)
    for k in range(12):
        a = k / 12 * 2 * np.pi
        disc(t, 32 + np.cos(a) * 5, 22 + np.sin(a) * 5, 0.9, (28, 28, 30))
    disc(t, 32, 22, 0.9, (28, 28, 30))
    area(t, 18, 54, 46, 60, (14, 14, 14))
    area(t, 16, 52, 48, 54, (150, 152, 154), f0=180)
    m = F.text_mask(str(n), BOLD, 9, 12, 12, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 46, 6, GOLD, amount=0.9)
    t.grime(0.15)
    return t


VBANS = ("gales", "fema", "season")


@lru_cache(maxsize=None)
def vban_pic(k):
    """A pole banner on a pier, three blocks tall, drawn across columns 12..52 (where its box
    sits): GALES in storm blue, the shelter's FEMA banner, the season's gold one."""
    p = Pic(1, 3, (0, 0, 0, 0), seed=len(k) * 7)
    x0, x1, H = 12, 52, p.H
    if k == "gales":
        bg, ink, rule = STORM, WHITE, GOLD
    elif k == "fema":
        bg, ink, rule = (236, 236, 230), (24, 56, 120), (24, 56, 120)
    else:
        bg, ink, rule = GOLD, NAVY, NAVY
    p.rect(x0, 6, x1, H - 14, fill=bg)
    p.poly([(x0, H - 14), (x1, H - 14), (x1, H - 4), ((x0 + x1) / 2, H - 12), (x0, H - 4)], fill=bg)
    p.rect(x0 - 2, 2, x1 + 2, 6, fill=(150, 154, 156))
    p.rect(x0, 6, x1, 10, fill=rule)
    p.rect(x0, H - 20, x1, H - 16, fill=rule)
    cx = (x0 + x1) / 2
    if k == "gales":
        # the wave: three gold crests over the name read down the banner
        for i in range(3):
            p.ellipse(cx - 14 + i * 3, 22 + i * 7, cx + 14 + i * 3, 40 + i * 7, outline=GOLD, width=2.5)
        p.text(cx, 70, "G|A|L|E|S", BLACK, 17, ink, anchor="ma", spacing=1.0)
    elif k == "fema":
        p.ellipse(cx - 13, 18, cx + 13, 44, fill=ink)
        p.ellipse(cx - 9, 22, cx + 9, 40, fill=bg)
        p.text(cx, 31, "FEMA", BLACK, 6, ink, anchor="mm")
        p.text(cx, 56, "EMER-|GENCY|SHEL-|TER", BLACK, 9, ink, anchor="ma", spacing=1.2)
        p.rect(x0 + 4, 150, x1 - 4, 151, fill=ink)
        p.text(cx, 156, "ALL|EVACUEES|CHECK IN|AT GATE A", BOLD, 4.6, (40, 40, 40), anchor="ma", spacing=1.3)
    else:
        p.text(cx, 30, "1993", BLACK, 11, ink, anchor="ma")
        p.text(cx, 52, "S|E|A|S|O|N", BLACK, 13, ink, anchor="ma", spacing=1.0)
    return p


def vban(k, i, torn=False):
    t = vban_pic(k).tile(i, 9760 + i + len(k) * 3 + (5 if torn else 0), smooth=0.1, grime=0.25)
    if torn and i == 2:
        # ripped off below a ragged line, the rest flapping
        xx = np.arange(N)
        cut = (18 + 10 * np.sin(xx * 0.31 + 1.0) + 6 * np.sin(xx * 0.9)).astype(int)
        for x in range(N):
            t.alpha[cut[x]:, x] = 0
    weather(t, rust=0.0, streak=0.4, grime=0.2)
    return t


@lru_cache(maxsize=None)
def fema_pic():
    """The vinyl banner tied up over each gate, eight blocks by two: the seal, EMERGENCY SHELTER,
    the intake line; grommets along the hems."""
    blue = (24, 56, 120)
    p = Pic(8, 2, (238, 238, 232), seed=4)
    W, H = p.W, p.H
    p.rect(0, 0, W, 9, fill=blue)
    p.rect(0, H - 9, W, H, fill=blue)
    p.ellipse(16, 22, 100, 106, fill=blue)
    p.ellipse(24, 30, 92, 98, fill=(238, 238, 232))
    p.ellipse(30, 36, 86, 92, fill=blue)
    p.text(58, 64, "FEMA", BLACK, 15, (238, 238, 232), anchor="mm")
    cx = (112 + W) / 2
    f = civic.fit("EMERGENCY SHELTER", BLACK, 48, W - 136)
    p.text(cx, 18, "EMERGENCY SHELTER", BLACK, f, blue, anchor="ma")
    f2 = civic.fit("REGISTRATION • INTAKE • MEDICAL — THIS GATE", BOLD, 18, W - 136)
    p.text(cx, 84, "REGISTRATION • INTAKE • MEDICAL — THIS GATE", BOLD, f2, (40, 40, 40), anchor="ma")
    for x in np.arange(10, W, 32):
        for y in (4, H - 5):
            p.ellipse(x - 2.2, y - 2.2, x + 2.2, y + 2.2, fill=(190, 190, 186))
    return p


def fema(i):
    t = fema_pic().tile(i, 9780 + i, smooth=0.25, grime=0.2)
    weather(t, rust=0.1, streak=0.6, grime=0.25)
    if i in (3, 12):
        # a corner torn loose and folded
        yy, xx = np.mgrid[0:N, 0:N]
        tri = (xx + yy < 26) if i == 12 else (xx - yy > 40)
        t.c[tri] = t.c[tri] * 0.55
    return t


def cone(seed):
    """A traffic cone: dayglow orange gone pale, the reflective collar, scuffs."""
    t = F.plain(seed, (226, 98, 34), 0.05, 0.55)
    area(t, 0, 28, N, 40, (230, 230, 226), f0=200)
    t.grime(0.3)
    return t


def porta(seed, part=None):
    """A portable toilet's moulded shell: blue, the ribs, dirt splashed up; the door with its
    VACANT latch below, the vent slots above."""
    t = F.plain(seed, (38, 92, 156), 0.04, 0.5)
    t.f0[:] = 30
    for x in range(5, N, 11):
        area(t, x, 0, x + 2, N, mul=0.82, dh=0.2)
    if part == "lo":
        area(t, 6, 0, 58, N, mul=0.94)
        edges(t, 6, 0, 58, N, w=1)
        area(t, 46, 4, 52, 16, (200, 200, 196))
        area(t, 47, 6, 51, 10, (40, 150, 60))
        t.grime(0.45, col=(70, 56, 40))
    elif part == "hi":
        area(t, 6, 0, 58, N, mul=0.94)
        edges(t, 6, 0, 58, N, w=1)
        for y in range(10, 26, 4):
            area(t, 14, y, 50, y + 2, (20, 30, 40))
        t.grime(0.2)
    else:
        t.grime(0.3, col=(70, 56, 40))
    return t


@lru_cache(maxsize=None)
def razor_pic():
    p = Pic(1, 1, (0, 0, 0, 0), seed=13)
    rng = np.random.default_rng(13)
    for k in range(5):
        x = 6 + k * 13
        p.ellipse(x - 12, 6, x + 12, 58, outline=(170, 172, 170), width=1.4)
        for y in range(10, 58, 8):
            p.line([(x - 12 + rng.uniform(-1, 1), y), (x - 15, y - 2)], (180, 182, 180), width=1)
    return p


def razor():
    t = razor_pic().tile(0, 9795, smooth=0.6, grime=0.0)
    t.f0[:] = np.where(t.alpha > 0, 200, t.f0)
    weather(t, rust=0.3, streak=0.0, grime=0.15)
    return t


# ---- the scoreboard hung under the canopy's edge at each end: twenty-four by ten

BOARD_W, BOARD_H = 24, 10
BOARD_LINES = (("EMERGENCY SHELTER", (255, 60, 50), 180),
               ("FOLLOW STAFF INSTRUCTIONS", (255, 205, 110), 100),
               ("STAY CALM • STAY INSIDE", (255, 205, 110), 100),
               ("REGISTRATION AT GATES A AND C", (255, 205, 110), 72))


@lru_cache(maxsize=None)
def board_rgb():
    """The LED screen's message, rows 1..7 of the board, as lit colour."""
    from PIL import ImageDraw, ImageFont
    W, H = BOARD_W * N, 7 * N
    im = Image.new("RGB", (W * 2, H * 2), (0, 0, 0))
    d = ImageDraw.Draw(im)
    y = 70
    for text, col, sz in BOARD_LINES:
        f = ImageFont.truetype(BLACK, sz)
        while d.textlength(text, font=f) > W * 2 - 140:
            sz -= 6
            f = ImageFont.truetype(BLACK, sz)
        d.text((W, y), text, font=f, fill=col, anchor="ma")
        y += sz + 64
    return np.asarray(im.resize((W, H), Image.BOX), np.float32)


@lru_cache(maxsize=None)
def board_pic():
    """The board's frame rows: GRAYHAVEN GALES lit across the top; under the screen the old
    scoreboard's bulbs still holding the last game, GALES 21 VISITORS 17, fourth quarter, 0:00."""
    p = Pic(BOARD_W, BOARD_H, (14, 14, 16), seed=91)
    W = p.W
    p.rect(0, 0, W, N, fill=STORM)
    p.rect(0, N - 5, W, N, fill=GOLD)
    p.rect(0, 0, W, 3, fill=mixc(STORM, (255, 255, 255), 0.3))
    p.text(W / 2, N / 2 - 1, "GRAYHAVEN  GALES", BLACK, 42, WHITE, anchor="mm")
    for x in (3 * N, W - 3 * N):
        for i in range(3):
            p.ellipse(x - 26 + i * 6, 12 + i * 8, x + 26 + i * 6, 40 + i * 8, outline=GOLD, width=4)
    y0 = 8 * N
    p.rect(0, y0, W, y0 + 2, fill=(60, 60, 64))
    amber = (255, 176, 60)
    for x, label, value in ((W * 0.13, "GALES", "21"), (W * 0.40, "QTR", "4"), (W * 0.60, "TIME", "0:00"),
                            (W * 0.87, "VISITORS", "17")):
        p.text(x, y0 + 8, label, BLACK, 20, WHITE, anchor="ma")
        p.text(x, y0 + 36, value, BLACK, 70, amber, anchor="ma")
    return p


def board(i):
    col, row = i % BOARD_W, i // BOARD_W
    if 1 <= row <= 7:
        t = screen(9590 + (i * 7) % 13, dead=(i * 37) % 29 == 0)
        rgb = board_rgb()[(row - 1) * N:row * N, col * N:(col + 1) * N]
        yy, xx = np.mgrid[0:N, 0:N]
        dots = ((yy % 2 == 0) & (xx % 2 == 0)).astype(np.float32)
        lum = np.clip(rgb.max(axis=2) / 255, 0, 1) * dots
        hue = np.where(rgb[..., 1:2] < 120, np.array((255, 60, 50), np.float32), np.array((255, 205, 110), np.float32))
        glow(t, lum, (255, 205, 110), 1.0, 0.95)
        t.c[:] = np.where(lum[..., None] > 0.05, hue * lum[..., None] + t.c * (1 - lum[..., None]), t.c)
        return t
    t = board_pic().tile(i, 9800 + i, smooth=0.5, grime=0.15)
    if row == 0:
        lit = t.c @ F.LUM
        t.emit[:] = np.clip(lit / 255, 0, 1) * 0.6
    else:
        # the bulbs: amber dots where the digits are, white paint for the labels unlit
        yy, xx = np.mgrid[0:N, 0:N]
        amber = (t.c[..., 0] > 180) & (t.c[..., 2] < 120)
        bulbs = amber & (yy % 3 == 1) & (xx % 3 == 1)
        t.c[amber & ~bulbs] = (40, 30, 20)
        glow(t, bulbs.astype(np.float32), (255, 176, 60), 1.0, 0.9)
    return t


def textures():
    T = {}
    for k, rgb in SEATS.items():
        T[f"st_plastic_{k}"] = lambda k=k, rgb=rgb: plastic(9500 + ord(k), rgb, 0.0 if k != "s" else 0.15)
        T[f"st_plastic_{k}_fade"] = lambda k=k, rgb=rgb: plastic(9510 + ord(k), rgb, 0.5)
    T["st_seat_steel"] = lambda: powder(9520)
    T["st_galv"] = lambda: galv(9521)
    T["st_tread_top"] = lambda: tread_top(9522)
    T["st_tread_gum"] = lambda: tread_top(9523, gum=1.0)
    T["st_riser"] = lambda: riser(9524)
    T["st_nosing"] = lambda: nosing(9525)
    T["st_glass"] = lambda: rail_glass(9526)
    T["st_pad"] = lambda: pad(9527)
    T["st_wallcap"] = lambda: wallcap(9528)
    for k in ADS:
        for i in range(8):
            T[f"st_ad_{k}_{i}"] = lambda k=k, i=i: ad(k, i)
    T["st_turf_white"] = lambda: turf_white(9530)
    for k in range(4):
        T[f"st_yard_{k}"] = lambda k=k: yard(9531 + k, k)
        T[f"st_hash_{k}"] = lambda k=k: yard(9535 + k, k, tick=True)
    for n in range(1, 6):
        for a in ("lr" if n < 5 else "x"):
            for i in (0, 2, 3, 5):
                T[f"st_num_{n}{a}_{i}"] = lambda n=n, a=a, i=i: num(n, a, i)
        for q in range(4):
            for i in (1, 4):
                T[f"st_num_{n}m{q}_{i}"] = lambda n=n, q=q, i=i: num(n, "x", i, q)
    T["st_ez"] = lambda: ez()
    for i in range(180):
        T[f"st_ez_{i}"] = lambda i=i: ez(i)
    for k in RIBBON:
        for i in range(32):
            T[f"st_ribbon_{k}_{i}"] = lambda k=k, i=i: ribbon(k, i)
    T["st_ribbon_off"] = lambda: ribbon_off(9540)
    T["st_housing"] = lambda: housing(9541)
    T["st_rowplate"] = lambda: rowplate(9542)
    T["st_cmu"] = lambda: sheriff.cmu(9580, (196, 196, 190))
    T["st_cmu_band"] = lambda: sheriff.cmu(9581, (196, 196, 190), band=True)
    T["st_floor"] = lambda: floor(9582)
    T["st_floor_b"] = lambda: floor(9583, spill=True)
    for k in HEADERS:
        for i in range(8):
            T[f"st_stand_{k}_{i}"] = lambda k=k, i=i: stand(k, i)
    for k in STANDS:
        for i in range(2):
            T[f"st_menu_{k}_{i}"] = lambda k=k, i=i: menu(k, i)
    for k in WAYS:
        for i in range(2):
            T[f"st_way_{k}_{i}"] = lambda k=k, i=i: way(k, i)
    T["st_grille"] = lambda: grille(9584)
    T["st_slats"] = lambda: slats(9585)
    T["st_warmer"] = lambda: warmer_glass(9586)
    T["st_fountain"] = lambda: fountain_face(9587)
    T["st_trough"] = lambda: trough(9588)
    T["st_tile"] = lambda: tile_wall(9589)
    T["st_carpet"] = lambda: carpet(9600)
    T["st_glazing"] = lambda: glazing(9601)
    T["st_glazing_broken"] = lambda: glazing(9602, True)
    T.update(PRECAST)
    T["st_deck"] = lambda: deck(9606)
    T["st_membrane"] = lambda: membrane(9607)
    T["st_truss"] = lambda: truss_paint(9608)
    T["st_lamp"] = lambda: lamp_face(9609, False)
    T["st_lamp_on"] = lambda: lamp_face(9610, True)
    T["st_screen"] = lambda: screen(9611)
    T["st_screen_dead"] = lambda: screen(9612, True)
    for i in range(BOARD_W * BOARD_H):
        T[f"st_board_{i}"] = lambda i=i: board(i)
    for i in range(11):
        T[f"st_sec_{i}"] = lambda i=i: sec(i)
    for k in PLAQUES:
        T[f"st_plaque_{k}"] = lambda k=k: plaque_face(k)
    T["st_canvas"] = lambda: canvas(9680)
    for k in TENTS:
        for i in range(2):
            T[f"st_tent_{k}_{i}"] = lambda k=k, i=i: tent_label(k, i)
    for k, rgb in TARPS.items():
        T[f"st_tarp_{k}"] = lambda k=k, rgb=rgb: tarp(9690 + len(k), rgb)
    T["st_wrap_water"] = lambda: wrap_water(9695)
    T["st_wrap_top"] = lambda: wrap_water(9696, top=True)
    T["st_box_meals"] = lambda: box_meals(9697)
    T["st_genset"] = lambda: genset(9698)
    T["st_cooler"] = lambda: cooler(9699)
    T["st_cooler_top"] = lambda: cooler(9700, top=True)
    T["st_duct"] = lambda: duct(9704)
    for c in LETTERS:
        for i in range(4):
            T[f"st_let_{c.lower()}_{i}"] = lambda c=c, i=i: letter(c, i)
    for c in BIG:
        for i in range(9):
            T[f"st_big_{c.lower()}_{i}"] = lambda c=c, i=i: big(c, i)
    T["st_poster_rules"] = lambda: poster(9701, False)
    T["st_poster_missing"] = lambda: poster(9702, True)
    T["st_poster_missing_b"] = lambda: poster(9703, True)
    for base in PRECAST:
        for k in WEAR:
            T[f"{base}_{k}"] = lambda base=base, k=k: worn(base, k)
    T["st_ramp_top"] = lambda: ramp_top(9750)
    T["st_fascia"] = lambda: fascia(9751)
    T["st_col_paint"] = lambda: col_paint(9752)
    for n in range(1, 5):
        T[f"st_ticket_{n}"] = lambda n=n: ticket_glass(9752 + n, n)
    T["st_ticket_broken"] = lambda: ticket_glass(9757, 2, broken=True)
    for k in VBANS:
        for i in range(3):
            T[f"st_vban_{k}_{i}"] = lambda k=k, i=i: vban(k, i)
    for i in range(3):
        T[f"st_vban_torn_{i}"] = lambda i=i: vban("gales", i, torn=True)
    for i in range(16):
        T[f"st_fema_{i}"] = lambda i=i: fema(i)
    T["st_cone"] = lambda: cone(9796)
    T["st_porta"] = lambda: porta(9797)
    T["st_porta_lo"] = lambda: porta(9798, "lo")
    T["st_porta_hi"] = lambda: porta(9799, "hi")
    T["st_razor"] = lambda: razor()
    return T


TENTS = ("triage", "shelter", "supply")
TARPS = {"red": (170, 36, 30), "yellow": (214, 180, 40), "green": (50, 120, 60), "black": (36, 36, 38)}


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        if boxes and rotate == "axis":
            boxes = P.along_z(boxes)  # RULE: axis models run along z as drawn
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

    def cube(front, side, up=None, down=None, south=None):
        face = {"north": front, "side": side, "up": up or side, "down": down or side}
        if south:
            face["south"] = south
        return [[0, 0, 0, 16, 16, 16, face]]

    steel, galvd, black = "st_seat_steel", "st_galv", "home_plastic_black"

    # ---- the seats: two to a block, cantilevered off a rail bolted to the riser behind (z = 16)
    def seat(x0, state, col):
        x1 = x0 + 6.6
        p = f"st_plastic_{col}"
        if state == "gone":
            return [[x0 + 2, 5, 13, x1 - 2, 6, 15, steel]]
        back = [[x0, 7, 12.4, x1, 15.5, 13.8, p], [x0 + 0.4, 15.5, 12.6, x1 - 0.4, 16, 13.8, p]]
        if state == "up":
            return back + [[x0 + 0.2, 7.5, 10.6, x1 - 0.2, 14.5, 12.2, p], [x0 + 0.2, 14.5, 10.8, x1 - 0.2, 15, 12.2, p]]
        if state == "cracked":
            return [[x0, 7, 12.4, x1, 12, 13.8, p], [x0 + 0.5, 12, 12.6, x1 - 2.5, 14, 13.6, p],
                    [x0, 6, 4, x1, 7.4, 12.2, p], [x0, 7.4, 4, x1, 8.2, 5.2, p]]
        return back + [[x0, 6, 4, x1, 7.4, 12.2, p], [x0, 7.4, 4, x1, 8.2, 5.2, p]]

    frame = [[0, 3, 14.6, 16, 5, 16, steel], [7.4, 0, 12, 8.6, 6.4, 14.6, steel], [7.4, 0, 8, 8.6, 0.6, 14, steel],
             [7.3, 9.6, 5, 8.7, 10.4, 12, black], [7.5, 6.4, 10.5, 8.5, 9.6, 11.5, steel],
             [0, 9.6, 5, 0.6, 10.4, 12, black], [15.4, 9.6, 5, 16, 10.4, 12, black]]
    for c in SEATS:
        kinds = {
            "": seat(0.6, "down", c) + seat(8.8, "down", c),
            "_up": seat(0.6, "up", c) + seat(8.8, "up", c),
            "_mix": seat(0.6, "down", c) + seat(8.8, "up", c),
            "_broken": seat(0.6, "gone", c) + seat(8.8, "cracked", c),
            "_blanket": seat(0.6, "down", c) + seat(8.8, "down", c) + [[0.2, 8.2, 3.6, 8.4, 9, 12.4, "sc_blanket"],
                                                                         [0.2, 2, 3.2, 8.4, 9, 3.8, "sc_blanket"]],
            "_trash": seat(0.6, "up", c) + seat(8.8, "down", c) + [[10, 8.2, 6, 13, 12, 9, "tr_cart_red"],
                                                                     [2, 0, 6, 5, 0.4, 9, "trash_bag"]],
        }
        for suffix, parts in kinds.items():
            add(f"st_seat_{c}{suffix}", frame + parts, "Stadium Seat", "STONE", render="cutout")
            if c == "s":
                fade = [[*b[:6], "st_plastic_s_fade"] if b[6] == "st_plastic_s" else b for b in frame + parts]
                add(f"st_seat_f{suffix}", fade, "Stadium Seat", "STONE", render="cutout")

    # ---- the rows and the aisles
    tread = {"up": "st_tread_top", "side": "st_riser", "down": "st_riser"}
    add("st_tread", None, "Precast Concrete", "STONE", rotate=None, tex=tread)
    add("st_tread_gum", None, "Precast Concrete", "STONE", rotate=None,
        tex={"up": "st_tread_gum", "side": "st_riser", "down": "st_riser"})
    step = {"up": "st_tread_top", "side": "st_riser", "down": "st_riser", "north": "st_riser"}
    add("st_step", [[0, 0, 0, 16, 8, 16, step], [0, 8, 0, 16, 16, 8, step],
                    [0, 7.6, 15, 16, 8.05, 16, "st_nosing"], [0, 15.6, 7, 16, 16.05, 8, "st_nosing"]], "Aisle Step", "STONE")
    add("st_rail_glass", [[0, 0, 0.6, 16, 13.4, 1.4, "st_glass"], [0, 13.4, 0, 16, 15, 2, galvd],
                          [3, 0, 0.2, 5, 2, 1.8, galvd], [11, 0, 0.2, 13, 2, 1.8, galvd]], "Glass Guard", "GLASS", render="translucent")
    add("st_rail", [[0, 14, 0.4, 16, 15.4, 1.8, galvd], [0, 7, 0.6, 16, 8.2, 1.6, galvd], [7.3, 0, 0.4, 8.7, 14, 1.8, galvd]],
        "Railing", "METAL", render="cutout")
    add("st_rail_post", [[7.2, 0, 7.2, 8.8, 15, 8.8, galvd], [6.8, 15, 6.8, 9.2, 15.6, 9.2, galvd]], "Railing", "METAL", rotate=None)
    add("st_rowplate", [[5, 2, 15.4, 11, 7, 16, "st_rowplate"]], "Row Plate", "METAL", decor=True)

    # ---- the field wall
    add("st_pad", cube("st_pad", "st_pad", up="st_wallcap", down="st_wallcap"), "Wall Padding", "WOOL")
    add("st_wallcap", None, "Concrete", "STONE", rotate=None, tex={"all": "st_wallcap"})
    for k in ADS:
        for i in range(8):
            add(f"st_ad_{k}_{i}", cube(f"st_ad_{k}_{i}", "st_pad", up="st_wallcap", down="st_wallcap"), "Wall Padding", "WOOL")

    # ---- the field: laid flat, the picture's top to the north in the base
    def ground(name):
        return [[0, 0, 0, 16, 16, 16, {"up": name, "side": "sc_turf_side", "down": "sc_turf_side"}]]

    add("st_turf_white", None, "Turf", "GRASS", rotate=None, tex={"up": "st_turf_white", "side": "sc_turf_side", "down": "sc_turf_side"})
    for k in range(4):
        add(f"st_yard_{k}", None, "Turf", "GRASS", rotate=None, tex={"up": f"st_yard_{k}", "side": "sc_turf_side", "down": "sc_turf_side"})
        add(f"st_hash_{k}", None, "Turf", "GRASS", rotate=None, tex={"up": f"st_hash_{k}", "side": "sc_turf_side", "down": "sc_turf_side"})
    for n in range(1, 6):
        for a in ("lr" if n < 5 else "x"):
            for i in (0, 2, 3, 5):
                add(f"st_num_{n}{a}_{i}", ground(f"st_num_{n}{a}_{i}"), "Turf", "GRASS")
        for q in range(4):
            for i in (1, 4):
                add(f"st_num_{n}m{q}_{i}", ground(f"st_num_{n}m{q}_{i}"), "Turf", "GRASS")
    add("st_ez", None, "Turf", "GRASS", rotate=None, tex={"up": "st_ez", "side": "sc_turf_side", "down": "sc_turf_side"})
    for i in range(180):
        add(f"st_ez_{i}", ground(f"st_ez_{i}"), "Turf", "GRASS")

    # ---- the ribbon board
    for k in RIBBON:
        for i in range(32):
            add(f"st_ribbon_{k}_{i}", cube(f"st_ribbon_{k}_{i}", "st_housing"), "Ribbon Board", "METAL", light=6)
    add("st_ribbon_off", cube("st_ribbon_off", "st_housing"), "Ribbon Board", "METAL")

    # ---- the concourses
    add("st_cmu", None, "Painted Block", "STONE", rotate=None, tex={"all": "st_cmu"})
    add("st_cmu_band", None, "Painted Block", "STONE", rotate=None, tex={"all": "st_cmu_band"})
    add("st_floor", None, "Concrete Floor", "STONE", rotate=None, tex={"up": "st_floor", "side": "concrete_panel", "down": "concrete_panel"})
    add("st_floor_b", None, "Concrete Floor", "STONE", rotate=None, tex={"up": "st_floor_b", "side": "concrete_panel", "down": "concrete_panel"})
    add("st_tile", None, "Tile", "STONE", rotate=None, tex={"all": "st_tile"})
    for k in HEADERS:
        for i in range(8):
            add(f"st_stand_{k}_{i}", cube(f"st_stand_{k}_{i}", "st_housing"), "Stand Sign", "METAL", light=4)
    for k in STANDS:
        for i in range(2):
            add(f"st_menu_{k}_{i}", [[0, 1, 15, 16, 15, 16, f"st_menu_{k}_{i}"]], "Menu Board", "METAL", decor=True, light=3)
    for k in WAYS:
        for i in range(2):
            face = {"north": f"st_way_{k}_{i}", "south": f"st_way_{k}_{1 - i}", "side": "st_housing", "up": "st_housing",
                    "down": "st_housing"}
            add(f"st_way_{k}_{i}", [[0, 2, 7, 16, 10, 9, face], [7.6, 10, 7.6, 8.4, 16, 8.4, galvd]], "Sign", "METAL", decor=True)
    add("st_grille", [[0, 0, 7.5, 16, 16, 8.5, "st_grille"]], "Counter Grille", "METAL", render="cutout")
    add("st_grille_half", [[0, 8, 7.5, 16, 16, 8.5, "st_grille"], [0, 7.5, 7, 16, 8, 9, galvd]], "Counter Grille", "METAL", render="cutout")
    add("st_grille_bent", [[0, 5, 7.5, 9, 16, 8.5, "st_grille"], [9, 9, 7.5, 16, 16, 8.5, "st_grille"], [0, 4.5, 6, 9, 5, 9, galvd]],
        "Counter Grille", "METAL", render="cutout")
    add("st_grille_box", [[0, 9, 4, 16, 16, 12, {"north": "st_slats", "side": "st_housing", "up": "st_housing", "down": "st_housing"}]],
        "Grille Box", "METAL")
    add("st_slats", cube("st_slats", "st_housing"), "Roll-Down Door", "METAL")
    add("st_warmer", [[1, 0, 3, 15, 9, 13, {"north": "st_warmer", "side": "fit_stainless", "up": "fit_stainless", "down": "fit_stainless"}]],
        "Hot Dog Roller", "METAL", loot="food")
    add("st_fountain", [[0, 0, 4, 16, 16, 15, {"north": "st_fountain", "side": "fit_stainless", "up": "fit_stainless", "down": "fit_stainless"}],
                        [1, 3, 1, 15, 4, 4, "fit_stainless"]], "Soda Fountain", "METAL", loot="drinks")
    add("st_condiments", [[1, 0, 2, 15, 14, 14, {"north": "fit_stainless", "side": "fit_stainless", "up": "fit_stainless"}],
                          [3, 14, 5, 5, 18, 7, "dn_enamel_red"], [7, 14, 5, 9, 18, 7, "st_nosing"],
                          [11, 14, 5, 13, 18, 7, "home_plastic_black"], [2, 14, 9, 14, 15, 13, "home_chrome"]], "Condiment Stand", "METAL")
    add("st_trough", [[0, 3, 10, 16, 8, 16, {"north": "st_trough", "side": "st_trough", "up": "st_trough", "down": "st_trough"}],
                      [0, 8, 15, 16, 16, 16, "st_trough"], [7, 13, 14, 9, 14, 15.5, "home_chrome"]], "Urinal Trough", "METAL")
    add("st_dryer", [[4, 8, 12, 12, 13, 16, "home_white"], [6.5, 7, 13, 9.5, 8, 15, "home_chrome"]], "Hand Dryer", "METAL", decor=True)

    # ---- the club level and the outside
    add("st_carpet", None, "Carpet", "WOOL", rotate=None, tex={"up": "st_carpet", "side": "concrete_panel", "down": "concrete_panel"})
    add("st_glazing", [[0, 0, 7, 16, 16, 9, "st_glazing"]], "Glazing", "GLASS", render="translucent")
    add("st_glazing_broken", [[0, 0, 7, 16, 16, 9, "st_glazing_broken"]], "Glazing", "GLASS", render="translucent")
    for v in ("", "_b", "_plain"):
        add(f"st_precast{v}", None, "Precast Concrete", "STONE", rotate=None, tex={"all": f"st_precast{v}"})
    add("st_fin", [[5, 0, 0, 11, 16, 16, "st_precast_plain"]], "Precast Fin", "STONE", rotate="axis")
    add("st_roof", None, "Canopy", "METAL", rotate=None, tex={"up": "st_membrane", "down": "st_deck", "side": "st_truss"})
    add("st_truss", [[0, 6, 0, 16, 8, 16, "st_truss"], [0, 0, 7, 16, 16, 9, "st_truss"], [7, 0, 0, 9, 16, 16, "st_truss"]],
        "Truss", "METAL", rotate=None, render="cutout")
    add("st_chord", [[0, 6, 6, 16, 10, 10, "st_truss"]], "Truss", "METAL", rotate="axis")
    for s in ("", "_on"):
        lamps = [[1 + c * 5, 2 + r * 6, 2, 5 + c * 5, 7 + r * 6, 6, {"north": f"st_lamp{s}", "side": "st_housing", "up": "st_housing",
                                                                     "down": "st_housing"}] for c in range(3) for r in range(2)]
        add(f"st_floods{s}", lamps + [[0, 1, 6, 16, 2, 8, "st_truss"], [0, 14, 6, 16, 15, 8, "st_truss"], [7, 0, 7, 9, 16, 14, "st_truss"]],
            "Floodlights", "METAL", render="cutout", **({"light": 15} if s else {}))
    add("st_screen", cube("st_screen", "st_housing"), "Video Board", "METAL")
    add("st_screen_dead", cube("st_screen_dead", "st_housing"), "Video Board", "METAL")
    for i in range(BOARD_W * BOARD_H):
        add(f"st_board_{i}", cube(f"st_board_{i}", "st_housing"), "Scoreboard", "METAL", light=7 if 1 <= i // BOARD_W <= 7 else 4)

    # ---- part 3: the way to the seats, the doors' plaques
    for i in range(2):
        # SECTIONS with its arrow to the right on the north face, to the left on the south: hung
        # facing one turn clockwise from the field, both arrows point at the seats
        face = {"north": f"st_way_sec_r_{i}", "south": f"st_way_sec_l_{1 - i}", "side": "st_housing", "up": "st_housing",
                "down": "st_housing"}
        add(f"st_way_secs_{i}", [[0, 2, 7, 16, 10, 9, face], [7.6, 10, 7.6, 8.4, 16, 8.4, galvd]], "Sign", "METAL", decor=True)
    for i in range(11):
        add(f"st_sec_{i}", [[0, 2, 14.6, 16, 14, 16, {"north": f"st_sec_{i}", "side": "st_housing", "up": "st_housing",
                                                     "down": "st_housing"}]], "Section Sign", "METAL", decor=True)
    for k in PLAQUES:
        add(f"st_plaque_{k}", [[3, 3, 15.2, 13, 13, 16, {"north": f"st_plaque_{k}", "side": "home_plastic_black"}]], "Sign", "METAL",
            decor=True)
    add("st_poster_rules", [[0, 0, 15.8, 16, 16, 16, "st_poster_rules"]], "Notice", "WOOL", decor=True, render="cutout")
    add("st_poster_missing", [[0, 0, 15.8, 16, 16, 16, "st_poster_missing"]], "Missing Poster", "WOOL", decor=True, render="cutout")
    add("st_poster_missing_b", [[0, 0, 15.8, 16, 16, 16, "st_poster_missing_b"]], "Missing Poster", "WOOL", decor=True,
        render="cutout")

    # ---- the gates: turnstiles, the crowd barriers
    add("st_turnstile", [[10, 0, 3, 16, 15, 13, {"north": "fit_stainless", "side": "fit_stainless", "up": "home_plastic_black"}],
                         [9, 12, 7.4, 10, 13, 8.6, "home_chrome"], [1, 12.3, 7.4, 9, 13.1, 8.6, "home_chrome"],
                         [7.5, 9, 3, 8.5, 13, 4, "home_chrome"], [7.5, 9, 12, 8.5, 13, 13, "home_chrome"],
                         [11, 15, 5, 15, 15.4, 9, "cv_exit_red"]], "Turnstile", "METAL")
    bars = [[x, 1.5, 7.6, x + 0.7, 14, 8.4, galvd] for x in (2.5, 4.5, 6.5, 8.5, 10.5, 12.5)]
    add("st_barrier", [[0, 14, 7.2, 16, 15.2, 8.8, galvd], [0, 1, 7.2, 16, 2, 8.8, galvd], [0.4, 0, 7.2, 1.6, 15, 8.8, galvd],
                       [14.4, 0, 7.2, 15.6, 15, 8.8, galvd], [0, 0, 3, 2, 0.6, 13, galvd], [14, 0, 3, 16, 0.6, 13, galvd]] + bars,
        "Crowd Barrier", "METAL")
    add("st_barrier_down", [[0, 0, 1, 16, 1.2, 2.2, galvd], [0, 0, 13.6, 16, 1.2, 14.8, galvd], [0.4, 0, 1, 1.6, 1.2, 15, galvd],
                            [14.4, 0, 1, 15.6, 1.2, 15, galvd]] + [[x, 0, 2.2, x + 0.7, 0.8, 13.6, galvd] for x in (2.5, 5.5, 8.5, 11.5)],
        "Crowd Barrier", "METAL", decor=True)

    # ---- the shelter on the field: tents, pallets, triage tarps, light towers, stretchers
    canv = "st_canvas"
    add("st_tent_wall", [[0, 0, 7.4, 16, 16, 8.6, canv]], "Tent", "WOOL")
    for k in TENTS:
        for i in range(2):
            add(f"st_tent_{k}_{i}", [[0, 0, 7.4, 16, 16, 8.6, {"north": f"st_tent_{k}_{i}", "south": canv, "side": canv}]],
                "Tent", "WOOL")
    # the roof pitched half a block a block: lo rises 0..8, hi 8..16, toward the back
    for name, y0 in (("lo", 0), ("hi", 8)):
        add(f"st_tent_roof_{name}", [[0, y0 + s * 2, s * 4, 16, y0 + s * 2 + 1.6, s * 4 + 4, canv] for s in range(4)], "Tent", "WOOL")
    add("st_tent_ridge", [[0, 0, 5, 16, 1.6, 11, canv]], "Tent", "WOOL", rotate="axis")
    add("st_tent_flap", [[0, 12, 6.5, 16, 16, 9.5, canv], [2, 10, 7.2, 3, 12, 8.8, "home_plastic_black"],
                         [13, 10, 7.2, 14, 12, 8.8, "home_plastic_black"]], "Tent", "WOOL", decor=True)
    pallet = [[0, 0, 0, 16, 2, 16, "bunk_pallet"]]
    add("st_pallet", pallet + [[1, 2, 2, 9, 2.4, 9, "sc_canvas"]], "Pallet", "WOOD", decor=True)
    add("st_pallet_water", pallet + [[1, 2, 1, 15, 15, 15, {"side": "st_wrap_water", "up": "st_wrap_top", "down": "bunk_pallet"}]],
        "Water", "WOOL", loot="drinks")
    add("st_pallet_water_half", pallet + [[1, 2, 1, 15, 8, 15, {"side": "st_wrap_water", "up": "st_wrap_top", "down": "bunk_pallet"}],
                                          [1, 8, 1, 8, 13, 8, {"side": "st_wrap_water", "up": "st_wrap_top"}]],
        "Water", "WOOL", loot="drinks")
    add("st_pallet_meals", pallet + [[1, 2, 1, 15, 9, 15, {"north": "st_box_meals", "side": "st_box_meals", "up": "cardboard"}],
                                     [2, 9, 2, 14, 15, 9, {"north": "st_box_meals", "side": "st_box_meals", "up": "cardboard"}]],
        "Meals", "WOOD", loot="food")
    add("st_pallet_cots", pallet + [[1, 2, 1, 15, 12, 15, {"north": "sc_box_cots", "side": "cardboard", "up": "cardboard"}]],
        "Supplies", "WOOD", loot="home_boxes")
    for k in TARPS:
        add(f"st_tarp_{k}", [[0, 0, 0, 16, 0.3, 16, f"st_tarp_{k}"]], "Tarp", "WOOL", rotate=None, decor=True)
    add("st_stretcher", [[1, 4, 0, 2, 5.4, 16, "ext_alu"], [14, 4, 0, 15, 5.4, 16, "ext_alu"], [2, 4.4, 1, 14, 5, 15, "sc_canvas"],
                         [1, 0, 2, 2, 4, 3, "ext_alu"], [14, 0, 2, 15, 4, 3, "ext_alu"], [1, 0, 13, 2, 4, 14, "ext_alu"],
                         [14, 0, 13, 15, 4, 14, "ext_alu"]], "Stretcher", "WOOL")
    add("st_stretcher_bag", [[1, 4, 0, 2, 5.4, 16, "ext_alu"], [14, 4, 0, 15, 5.4, 16, "ext_alu"], [2, 4.4, 1, 14, 5, 15, "sc_canvas"],
                             [1, 0, 2, 2, 4, 3, "ext_alu"], [14, 0, 2, 15, 4, 3, "ext_alu"], [1, 0, 13, 2, 4, 14, "ext_alu"],
                             [14, 0, 13, 15, 4, 14, "ext_alu"], [3, 5, 0.5, 13, 8, 15.5, "bunk_bodybag"],
                             [5, 8, 2, 11, 9, 14, "bunk_bodybag"]], "Stretcher", "WOOL")
    gen = {"north": "st_genset", "side": "st_genset", "up": "fit_stainless", "down": "home_plastic_black"}
    add("st_lt_base", [[1, 3, 0, 15, 14, 16, gen], [0, 0, 5, 1.5, 5, 11, "home_plastic_black"],
                       [14.5, 0, 5, 16, 5, 11, "home_plastic_black"], [7, 0, -6, 9, 3, 0, "st_galv"], [7.2, 14, 7.2, 8.8, 16, 8.8, "st_galv"]],
        "Light Tower", "METAL")
    add("st_lt_mast", [[7, 0, 7, 9, 16, 9, "st_galv"], [7.5, 0, 9, 8.5, 16, 9.6, "home_plastic_black"]], "Light Tower", "METAL",
        rotate=None, render="cutout")
    for s in ("", "_on"):
        lamp = {"north": f"st_lamp{s}", "side": "st_housing", "up": "st_housing", "down": "st_housing"}
        add(f"st_lt_head{s}", [[0, 6, 7, 16, 7.5, 9, "st_galv"], [7, 0, 7, 9, 7.5, 9, "st_galv"],
                               [0.5, 7.5, 5, 7.5, 13, 9, lamp], [8.5, 7.5, 5, 15.5, 13, 9, lamp],
                               [0.5, 1, 5, 7.5, 6, 9, lamp], [8.5, 1, 5, 15.5, 6, 9, lamp]], "Light Tower", "METAL",
            render="cutout", **({"light": 15} if s else {}))

    # ---- over the walk: the ducts and the pipes, run along z as drawn
    ductf = {"north": "st_duct", "south": "st_duct", "side": "st_duct", "up": "st_duct", "down": "st_duct"}
    add("st_duct", [[2, 3, 0, 14, 15, 16, ductf], [1.5, 2.5, 0, 14.5, 15.5, 1, "st_galv"]], "Duct", "METAL", rotate="axis")
    add("st_duct_tee", [[2, 3, 0, 14, 15, 16, ductf], [5, 0, 5, 11, 3, 11, "st_galv"], [4, -0.5, 4, 12, 0.5, 12, "bunk_grille"]],
        "Duct", "METAL", rotate="axis")
    add("st_pipes", [[1, 12, 0, 4, 15, 16, "dn_enamel_red"], [6, 13, 0, 8, 15, 16, "st_galv"], [10, 13, 0, 12, 15, 16, "st_galv"],
                     [0, 15, 7, 16, 16, 9, "st_galv"], [2, 10.5, 7.5, 3, 12, 8.5, "home_chrome"]], "Pipes", "METAL", rotate="axis",
        decor=True)
    add("st_beam", [[0, 0, 4, 16, 16, 12, "st_precast_plain"]], "Precast Beam", "STONE", rotate="axis")

    for c in LETTERS:
        for i in range(4):
            # block and texture ids are lower case (the game refuses capitals in a resource path)
            n = f"st_let_{c.lower()}_{i}"
            add(n, [[0, 0, 15, 16, 16, 16, n]], "Letter", "METAL", decor=True, render="cutout")
    for c in BIG:
        for i in range(9):
            n = f"st_big_{c.lower()}_{i}"
            add(n, [[0, 0, 14.5, 16, 16, 16, {"north": n, "side": n}]], "Letter",
                "METAL", decor=True, render="cutout")

    # ---- the sideline: the team benches and their coolers
    add("st_bench", [[0, 6, 3, 16, 7.4, 11, "sc_alu_plank"], [0, 9, 13, 16, 15, 14.4, "sc_alu_plank"],
                     [1, 0, 4, 2.4, 6, 12, "bunk_grey"], [13.6, 0, 4, 15, 6, 12, "bunk_grey"],
                     [1, 6, 12, 2.4, 15, 13, "bunk_grey"], [13.6, 6, 12, 15, 15, 13, "bunk_grey"]], "Team Bench", "METAL")
    add("st_cooler", [[3.5, 5, 3.5, 12.5, 15, 12.5, {"side": "st_cooler", "up": "st_cooler_top", "down": "st_cooler"}],
                      [4, 15, 4, 12, 16, 12, "st_cooler_top"], [7, 9, 1.5, 9, 11, 3.5, "home_chrome"],
                      [3, 0, 3, 4, 5, 4, "bunk_grey"], [12, 0, 3, 13, 5, 4, "bunk_grey"], [3, 0, 12, 4, 5, 13, "bunk_grey"],
                      [12, 0, 12, 13, 5, 13, "bunk_grey"], [3, 4.5, 3, 13, 5, 13, "bunk_grey"]], "Drink Cooler", "METAL",
        loot="drinks")
    # ---- part 4: the precast worn, the ramp towers, the gates, the checkpoint, the lots
    for base in PRECAST:
        for k in WEAR:
            add(f"{base}_{k}", None, "Precast Concrete", "STONE", rotate=None, tex={"all": f"{base}_{k}"})
    # the ramp's deck a sixteenth of a block at a time: st_ramp_n stands 2(n + 1) high
    for n in range(8):
        add(f"st_ramp_{n}", [[0, 0, 0, 16, 2 * (n + 1), 16, {"up": "st_ramp_top", "side": "st_precast_plain",
                                                             "down": "st_precast_plain"}]], "Ramp", "STONE", rotate=None)
    # and its parapet's bands: st_parapet_n stands 2(n + 1) up from the floor, st_soffit_n hangs as far down from the top
    for n in range(7):
        add(f"st_parapet_{n}", [[0, 0, 0, 16, 2 * (n + 1), 16, "st_precast_plain"]], "Precast Concrete", "STONE", rotate=None)
        add(f"st_soffit_{n}", [[0, 14 - 2 * n, 0, 16, 16, 16, "st_precast_plain"]], "Precast Concrete", "STONE", rotate=None)
    add("st_canopy", [[0, 10, 0, 16, 16, 16, {"up": "st_membrane", "down": "st_deck", "side": "st_fascia"}]], "Canopy", "METAL",
        rotate=None)
    add("st_fascia", None, "Fascia", "METAL", rotate=None, tex={"all": "st_fascia"})
    add("st_column", [[5.5, 0, 5.5, 10.5, 16, 10.5, "st_col_paint"]], "Column", "METAL", rotate=None)
    add("st_column_base", [[4, 0, 4, 12, 1, 12, "st_galv"], [5.5, 1, 5.5, 10.5, 16, 10.5, "st_col_paint"]], "Column", "METAL",
        rotate=None)
    for n in list(range(1, 5)) + ["broken"]:
        add(f"st_ticket_{n}", cube(f"st_ticket_{n}", "st_precast_b"), "Ticket Window", "GLASS")
    add("st_ticket_ledge", [[0, 13, 9, 16, 15, 16, "fit_stainless"], [2, 9, 14, 3, 13, 16, "fit_stainless"],
                            [13, 9, 14, 14, 13, 16, "fit_stainless"]], "Counter", "METAL", decor=True)
    for k in list(VBANS) + ["torn"]:
        for i in range(3):
            add(f"st_vban_{k}_{i}", [[3, 0, 15.2, 13, 16, 16, {"north": f"st_vban_{k}_{i}", "south": f"st_vban_{k}_{i}",
                                                              "side": "st_galv"}]], "Banner", "WOOL", decor=True, render="cutout")
    for i in range(16):
        add(f"st_fema_{i}", [[0, 0, 15.4, 16, 16, 16, f"st_fema_{i}"]], "Banner", "WOOL", decor=True)
    add("st_cone", [[4, 0, 4, 12, 1, 12, "home_plastic_black"], [5.5, 1, 5.5, 10.5, 5, 10.5, "st_cone"],
                    [6.3, 5, 6.3, 9.7, 9, 9.7, "st_cone"], [7, 9, 7, 9, 12, 9, "st_cone"]], "Traffic Cone", "WOOL", rotate=None,
        decor=True)
    shell = {"side": "st_porta", "up": "st_porta", "down": "st_porta"}
    add("st_porta_lo", [[1, 0, 1, 15, 16, 15, {**shell, "north": "st_porta_lo"}], [0.5, 0, 0.5, 15.5, 0.6, 15.5, "home_plastic_black"]],
        "Portable Toilet", "WOOD")
    add("st_porta_hi", [[1, 0, 1, 15, 14, 15, {**shell, "north": "st_porta_hi"}], [0.5, 14, 0.5, 15.5, 16, 15.5, "st_porta"]],
        "Portable Toilet", "WOOD")
    add("st_razor", None, "Razor Wire", "METAL", rotate=None, tex="st_razor", model="cross")
    add("st_lot_base", [[4, 0, 4, 12, 10, 12, "st_precast_plain"], [6.5, 10, 6.5, 9.5, 16, 9.5, "st_galv"]], "Light Pole", "STONE",
        rotate=None)
    add("st_lot_pole", [[6.5, 0, 6.5, 9.5, 16, 9.5, "st_galv"]], "Light Pole", "METAL", rotate=None)
    for s in ("", "_on"):
        box = {"down": f"st_lamp{s}", "north": "st_housing", "side": "st_housing", "up": "st_housing"}
        add(f"st_lot_head{s}", [[6.5, 0, 6.5, 9.5, 9, 9.5, "st_galv"], [0, 8, 7, 16, 9.5, 9, "st_galv"],
                                [0, 9.5, 3, 7, 13, 13, box], [9, 9.5, 3, 16, 13, 13, box]], "Light Pole", "METAL",
            **({"light": 15} if s else {}))
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("st_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} stadium blocks in {path}")


if __name__ == "__main__":
    main()
