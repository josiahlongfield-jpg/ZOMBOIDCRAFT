"""Harbor Arena: the ar_ kit (world/county/Arena.java, ArenaRooms.java).

No photos of it in the library yet (city/arena-* to be queued): this follows the American indoor
arenas built between the late sixties and the eighties for hockey and basketball, the precast
box with its fins and its glass entrance halls at the ends, the floor sunk below the street so
the concourse is at the doors, two decks of seats round the rink, the scoreboard hung from the
roof trusses over centre ice. The Grayhaven Gulls played here. The last weeks: the floor decked
over in plywood for the shelter's cots, the ice left to rot at the ends, the cold room under the
north stands used as a morgue, the families who moved up into the high corners.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- the rink: ar_ice, ar_ice_red, ar_ice_blue, ar_ice_crease, ar_ice_melt, ar_ice_logo_0..24 (five
  by five, row 0 north), ar_deck, ar_deck_b, ar_deck_blood (the plywood over it), ar_rubber;
- the boards: ar_dasher (facing: the rink side), ar_ad_KEY_0..3 (four wide, sponsors),
  ar_shield (the glass over them), ar_goal_0/1 (two wide, 0 at the viewer's left), ar_goal_down;
- the scoreboard: ar_sb_0..23 (six by four), ar_sb_ring_0..5, ar_sb_house;
- plates: ar_sgn_KEY_0..1 (two by one), ar_vban_KEY_0..2 (one by three, hung free from the
  rafters, row 0 the top), ar_marq_0..23 (the marquee, eight by three), ar_big_C_0..8 (the
  facade's letters, three by three);
- the rooms: ar_stall (a locker stall with its jersey), ar_sticks, ar_skates, ar_resurf_X_Y_Z
  (cells of the ice resurfacer, nose east; CELLS below), ar_carpet.

    python3 tools/city/arena.py          rewrites the ar_ entries in blocks.json
    python3 tools/city/arena.py cells    prints the resurfacer's cells for ArenaRooms.java

photo_textures.py draws the textures (arena.register()).
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
from furniture import area, disc  # noqa: E402
from school import Pic, HAND  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
STENCIL = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
NAVY = (22, 36, 72)
TEAL = (0, 122, 124)
WHITE = (240, 240, 236)
AMBER = (255, 168, 40)
RED = (186, 30, 36)
BLUE = (30, 70, 160)

SIGNS = {
    "home": ("GULLS LOCKER ROOM", NAVY, WHITE),
    "visitors": ("VISITORS", NAVY, WHITE),
    "officials": ("OFFICIALS", NAVY, WHITE),
    "iceplant": ("ICE PLANT", (60, 60, 64), WHITE),
    "cold": ("COLD ROOM", (60, 60, 64), WHITE),
    "resurfacer": ("RESURFACER", (60, 60, 64), WHITE),
    "dock": ("LOADING DOCK", (60, 60, 64), WHITE),
    "kitchen": ("COMMISSARY", (60, 60, 64), WHITE),
    "equipment": ("EQUIPMENT", NAVY, WHITE),
    "aid": ("FIRST AID", (170, 24, 30), WHITE),
    "isolation": ("ISOLATION", (170, 24, 30), WHITE),
    "press": ("PRESS ROOM", NAVY, WHITE),
    "security": ("SECURITY", NAVY, WHITE),
    "workshop": ("MAINTENANCE", (60, 60, 64), WHITE),
    "storage": ("STORAGE", (60, 60, 64), WHITE),
    "offices": ("ARENA OFFICES", NAVY, WHITE),
    "event": ("EVENT LEVEL", TEAL, WHITE),
    "register": ("SHELTER CHECK-IN", (236, 236, 230), (24, 56, 120)),
    "boxoffice": ("BOX OFFICE", TEAL, WHITE),
    "morgue": ("MORGUE - NO ENTRY", (236, 236, 230), (170, 20, 20)),
}
# sponsors along the dasher boards, four wide: text, field, ink
ADS = {
    "savings": ("HARBOR SAVINGS & LOAN", (240, 240, 236), NAVY),
    "cola": ("KESTREL COLA", (196, 26, 32), WHITE),
    "radio": ("WGHV RADIO 1050 AM", (250, 196, 30), (30, 30, 30)),
    "motors": ("MARLOWE MOTORS", (24, 60, 120), WHITE),
}
# the banners in the rafters: lines, field, ink
VBANS = {
    "champ": ("GULLS|1981|DIVISION|CHAMPIONS", NAVY, WHITE),
    "nine": ("9|HALVORSEN", TEAL, WHITE),
    "sixteen": ("16|DUQUESNE", TEAL, WHITE),
    "four": ("4|MACREADY", TEAL, WHITE),
}
WORD = "HARBOR ARENA"
LETTERS = sorted(set(WORD.replace(" ", "")))


# ---------------------------------------------------------------- the rink

def ice(seed, paint=None, melt=False):
    """Hockey ice: white over the painted slab, the skates' scratches criss-crossing it, a line's
    paint showing through dulled; melting, grey and wet, slush and pools on it."""
    t = F.plain(seed, (222, 230, 234), 0.03, 0.85)
    t.f0[:] = 24
    rng = t.rng
    if paint is not None:
        t.c[:] = np.array(paint, np.float32) * 0.82 + np.array((222, 230, 234), np.float32) * 0.18
        t.vary(0.04)
    for _ in range(70):
        x, y = rng.uniform(0, N, 2)
        a = rng.uniform(0, math.pi)
        ln = rng.uniform(8, 40)
        for s in range(int(ln)):
            px, py = int(x + math.cos(a) * s) % N, int(y + math.sin(a) * s) % N
            t.c[py, px] = np.minimum(255, t.c[py, px] * 1.08)
            t.h[py, px] -= 0.02
    t.grime(0.05, (120, 130, 140))
    if melt:
        t.c *= 0.82
        for _ in range(4):
            disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(6, 16), col=(150, 160, 166), smooth=0.98, soft=0.5)
        t.speckle(0.08, (120, 110, 96), 0.4)
    return t


@lru_cache(maxsize=None)
def logo_pic():
    """Centre ice: the Gulls' crest, a white gull over a navy disc ringed in teal, five by five."""
    p = Pic(5, 5, (222, 230, 234), 23010)
    W = p.W
    c = W / 2
    p.ellipse(c - 140, c - 140, c + 140, c + 140, fill=(TEAL[0] * 0.85 + 30, TEAL[1] * 0.85 + 30, TEAL[2] * 0.85 + 30))
    p.ellipse(c - 120, c - 120, c + 120, c + 120, fill=(46, 58, 92))
    # the gull: two swept wings and the head
    p.poly([(c - 96, c - 6), (c - 40, c - 46), (c - 6, c - 8), (c + 6, c - 8), (c + 40, c - 46), (c + 96, c - 6),
            (c + 40, c - 24), (c, c + 10), (c - 40, c - 24)], fill=(236, 238, 236))
    p.ellipse(c - 10, c - 22, c + 10, c - 2, fill=(236, 238, 236))
    p.text(c, c + 62, "GULLS", BLACK, 44, (236, 238, 236), anchor="mm")
    return p


def logo(i):
    t = logo_pic().tile(i, 23020 + i, smooth=0.85, grime=0.04)
    t.f0[:] = 24
    return t


def deck(seed, kind=""):
    """Plywood sheets over the ice: four feet by eight, the seams, the screws, the scuffing; muddy
    with the shelter's feet, or the blood soaked in."""
    t = F.plain(seed, (196, 160, 110), 0.06, 0.15)
    t.vary(0.08, tint=(10, 4, -6))
    rng = t.rng
    for y in range(0, N, 4):
        area(t, 0, y, N, y + 1, mul=rng.uniform(0.92, 1.04))
    area(t, 0, 0, N, 1, col=(110, 86, 58), dh=-0.3)
    area(t, 0, 0, 1, N, col=(110, 86, 58), dh=-0.3)
    for x, y in ((4, 4), (60, 4), (4, 60), (60, 60), (32, 4), (32, 60)):
        area(t, x, y, x + 1, y + 1, col=(90, 90, 92))
    if kind in ("b", "blood"):
        t.grime(0.3, (70, 56, 40))
        for _ in range(6):
            disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(2, 4), col=(90, 74, 54), soft=0.6)
    if kind == "blood":
        F.stain(t, 30, 34, 18, col=(96, 18, 16), amount=0.75, tide=True)
    return t


def rubber(seed):
    t = F.plain(seed, (40, 40, 42), 0.04, 0.2)
    t.speckle(0.3, (70, 70, 72), 0.4)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, col=(26, 26, 28), dh=-0.2)
    return t


# ---------------------------------------------------------------- the boards and glass

def board(seed):
    """The dasher boards' face: white polyethylene scarred black by pucks and blades, the yellow
    kick plate along the foot, the navy cap rail along the top."""
    t = F.plain(seed, (232, 232, 226), 0.03, 0.55)
    rng = t.rng
    area(t, 0, N - 10, N, N, col=(228, 190, 40), dh=0.1)
    area(t, 0, 0, N, 5, col=NAVY, dh=0.15, smooth=0.6)
    area(t, 0, 5, N, 6, col=(150, 150, 150))
    for _ in range(7):
        x, y = rng.uniform(0, N), rng.uniform(8, N - 10)
        area(t, int(x), int(y), int(x) + int(rng.uniform(2, 5)), int(y) + 1, col=(90, 90, 90), dh=-0.05)
    for _ in range(2):
        disc(t, rng.uniform(0, N), rng.uniform(10, N - 14), rng.uniform(0.8, 1.6), col=(60, 60, 60))
    t.grime(0.15)
    return t


@lru_cache(maxsize=None)
def ad_pic(key):
    text, field, ink = ADS[key]
    p = Pic(4, 1, (232, 232, 226), 23100 + len(key))
    W = p.W
    p.rect(4, 8, W - 4, N - 12, fill=field)
    p.text(W / 2, (N - 4) / 2, text, BLACK, civic.fit(text, BLACK, 26, W - 20), ink, anchor="mm")
    p.rect(0, N - 10, W, N, fill=(228, 190, 40))
    p.rect(0, 0, W, 5, fill=NAVY)
    return p


def ad(key, i):
    t = ad_pic(key).tile(i, 23110 + i + len(key) * 7, smooth=0.55, grime=0.15)
    rng = t.rng
    for _ in range(4):
        x, y = rng.uniform(0, N), rng.uniform(8, N - 10)
        area(t, int(x), int(y), int(x) + int(rng.uniform(2, 5)), int(y) + 1, col=(90, 90, 90))
    return t


def shield(seed):
    """The seamless glass over the boards: a faint green edge, scuffed, the puck marks starred."""
    t = P.Tex(seed, (214, 228, 224))
    t.alpha[:] = 46
    t.smooth[:] = 0.95
    t.f0[:] = 22
    t.poro[:] = 0
    rng = t.rng
    area(t, 0, 0, 2, N, col=(160, 190, 180), alpha=140)
    area(t, N - 2, 0, N, N, col=(160, 190, 180), alpha=140)
    for _ in range(3):
        cx, cy = rng.uniform(8, 56, 2)
        for k in range(6):
            a = rng.uniform(0, 6.3)
            for s in range(int(rng.uniform(3, 9))):
                px, py = int(cx + math.cos(a) * s), int(cy + math.sin(a) * s)
                if 0 <= px < N and 0 <= py < N:
                    t.c[py, px] = (240, 244, 244)
                    t.alpha[py, px] = 150
    for _ in range(40):
        x, y = rng.uniform(0, N, 2)
        t.alpha[int(y), int(x)] = 90
    return t


def net(seed):
    t = P.Tex(seed, (236, 236, 232))
    t.alpha[:] = 0
    for k in range(0, N, 6):
        t.alpha[:, k] = 255
        t.alpha[k, :] = 255
    return t


# ---------------------------------------------------------------- the scoreboard

@lru_cache(maxsize=None)
def sb_pic():
    """A face of the centre-hung board, six by four: the last game's score in amber bulbs across
    the top, the video board under it frozen on the shelter's message, the period and the clock."""
    p = Pic(6, 4, (18, 18, 20), 23200)
    W, H = p.W, p.H
    p.rect(0, 0, W, N, fill=(26, 26, 30))
    p.text(W * 0.25, N * 0.32, "GULLS", BLACK, 20, WHITE, anchor="mm")
    p.text(W * 0.75, N * 0.32, "VISITORS", BLACK, 20, WHITE, anchor="mm")
    p.text(W * 0.25, N * 0.78, "2", STENCIL, 26, AMBER, anchor="mm")
    p.text(W * 0.75, N * 0.78, "4", STENCIL, 26, AMBER, anchor="mm")
    # the video board: black modules, the message in white, a band of dead ones
    p.rect(8, N + 4, W - 8, 3 * N - 4, fill=(10, 12, 14))
    for x in range(8, W - 8, 8):
        p.line([(x, N + 4), (x, 3 * N - 4)], (24, 26, 28), 0.5)
    for y in range(N + 4, 3 * N - 4, 8):
        p.line([(8, y), (W - 8, y)], (24, 26, 28), 0.5)
    p.text(W / 2, N + 36, "EMERGENCY SHELTER", BLACK, 30, (236, 236, 236), anchor="mm")
    p.text(W / 2, N + 76, "CHECK IN AT THE EAST TUNNEL", BOLD, 17, (236, 220, 120), anchor="mm")
    p.text(W / 2, N + 104, "NO WEAPONS  -  REPORT ALL BITES", BOLD, 15, (236, 120, 110), anchor="mm")
    p.rect(W * 0.62, N + 4, W * 0.78, 3 * N - 4, fill=(6, 6, 8))
    p.text(W * 0.2, 3 * N + 22, "PERIOD", BLACK, 13, WHITE, anchor="mm")
    p.text(W * 0.2, 3 * N + 46, "3", STENCIL, 20, AMBER, anchor="mm")
    p.text(W * 0.5, 3 * N + 32, "00:00", STENCIL, 30, AMBER, anchor="mm")
    p.text(W * 0.8, 3 * N + 22, "SHOTS", BLACK, 13, WHITE, anchor="mm")
    p.text(W * 0.8, 3 * N + 46, "31  28", STENCIL, 16, AMBER, anchor="mm")
    return p


def sb(i):
    t = sb_pic().tile(i, 23210 + i, smooth=0.6, grime=0.12)
    t.f0[:] = 40
    lum = t.c @ np.array((0.3, 0.59, 0.11), np.float32)
    t.emit[:] = np.clip((lum - 120) / 135, 0, 1) * 0.5
    return t


@lru_cache(maxsize=None)
def ring_pic():
    p = Pic(6, 1, TEAL, 23300)
    W = p.W
    p.rect(0, 0, W, 4, fill=NAVY)
    p.rect(0, N - 4, W, N, fill=NAVY)
    p.text(W / 2, N / 2, "GRAYHAVEN GULLS", BLACK, 30, WHITE, anchor="mm")
    return p


def ring(i):
    return ring_pic().tile(i, 23310 + i, smooth=0.5, grime=0.15)


# ---------------------------------------------------------------- plates

@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 23400 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 16, W - 1, H - 16, fill=field)
    p.rect(1, 16, W - 1, 20, fill=TEAL if field == NAVY else tuple(int(v * 0.8) for v in field))
    p.text(W / 2, H / 2 + 2, text, BLACK, civic.fit(text, BLACK, 15, W - 14), ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 23450 + i + len(key) * 11, smooth=0.45, grime=0.15)


@lru_cache(maxsize=None)
def vban_pic(key):
    """A banner hung in the rafters, one by three: the field, the teal and white stripes, the
    words read down it."""
    text, field, ink = VBANS[key]
    p = Pic(1, 3, (0, 0, 0, 0), 23500 + len(key))
    H = p.H
    x0, x1 = 6, N - 6
    p.rect(x0, 0, x1, H - 16, fill=field)
    p.poly([(x0, H - 16), (x1, H - 16), (N / 2, H - 2)], fill=field)
    p.rect(x0, 10, x1, 14, fill=WHITE)
    p.rect(x0, H - 30, x1, H - 26, fill=WHITE)
    lines = text.split("|")
    if lines[0].isdigit():
        p.text(N / 2, 56, lines[0], BLACK, 40, ink, anchor="mm")
        p.text(N / 2, 104, lines[1], BLACK, civic.fit(lines[1], BLACK, 10, x1 - x0 - 4), ink, anchor="mm")
    else:
        y = 30
        for ln in lines:
            s = civic.fit(ln, BLACK, 12, x1 - x0 - 6)
            p.text(N / 2, y, ln, BLACK, s, ink, anchor="mm")
            y += s * 1.6 + 6
    return p


def vban(key, i):
    return vban_pic(key).tile(i, 23520 + i + len(key) * 3, smooth=0.1, grime=0.2)


@lru_cache(maxsize=None)
def marq_pic():
    """The marquee on its pylon by the east plaza, eight by three: HARBOR ARENA in teal over the
    letter board, the last game postponed and the shelter put up in its place."""
    p = Pic(8, 3, (24, 24, 26), 23600)
    W, H = p.W, p.H
    p.rect(0, 0, W, N, fill=TEAL)
    p.rect(0, N - 4, W, N, fill=NAVY)
    p.text(W / 2, N / 2, "HARBOR ARENA", BLACK, 44, WHITE, anchor="mm")
    p.rect(10, N + 6, W - 10, H - 8, fill=(236, 232, 214))
    for y in range(N + 6, H - 8, 30):
        p.line([(10, y), (W - 10, y)], (120, 116, 100), 0.8)
    p.text(W / 2, N + 22, "SAT GULLS VS RIVERMEN 7:30", BLACK, 18, (20, 20, 20), anchor="mm")
    p.text(W / 2, N + 52, "POSTPONED", BLACK, 18, (170, 20, 20), anchor="mm")
    p.text(W / 2, N + 82, "EMERGENCY SHELTER OPEN", BLACK, 18, (20, 20, 20), anchor="mm")
    p.text(W / 2, N + 110, "ALL WELCOME - NO WEAPONS", BLACK, 16, (20, 20, 20), anchor="mm")
    # the letters gone where the wind took them
    p.rect(W * 0.31, N + 74, W * 0.37, N + 92, fill=(236, 232, 214))
    p.rect(W * 0.66, N + 102, W * 0.74, N + 120, fill=(236, 232, 214))
    return p


def marq(i):
    t = marq_pic().tile(i, 23610 + i, smooth=0.4, grime=0.25)
    t.streaks(0.4, 0.8)
    return t


@lru_cache(maxsize=None)
def big_pic(c):
    """A channel letter of the name across the facade, three blocks square: teal metal edged in
    white, its shadow on the precast."""
    from PIL import ImageFont
    p = Pic(3, 3, (0, 0, 0, 0), seed=ord(c) + 23700)
    f = ImageFont.truetype(BLACK, int(176 * 4))
    cx, cy = p.W * 2, p.H * 2 + 16
    p.d.text((cx + 20, cy + 26), c, font=f, fill=(30, 28, 26, 150), anchor="mm")
    p.d.text((cx, cy), c, font=f, fill=TEAL + (255,), anchor="mm", stroke_width=14, stroke_fill=WHITE + (255,))
    return p


def big(c, i):
    t = big_pic(c).tile(i, 23710 + ord(c) * 9 + i, smooth=0.5, grime=0.15)
    t.f0[:] = np.where(t.alpha > 0, 60, t.f0)
    t.streaks(0.5, 0.75)
    return t


# ---------------------------------------------------------------- the rooms

def jersey(seed, home=True):
    """A jersey on its hanger: teal with the white and navy hoops (home), white with teal (away)."""
    base = TEAL if home else WHITE
    hoop = WHITE if home else TEAL
    t = F.plain(seed, base, 0.04, 0.15)
    area(t, 0, 36, N, 42, col=hoop)
    area(t, 0, 44, N, 47, col=NAVY)
    area(t, 0, 0, N, 6, col=NAVY)
    return t


def carpet(seed):
    t = F.plain(seed, (30, 60, 80), 0.05, 0.08)
    t.speckle(0.4, (50, 90, 110), 0.3)
    t.grime(0.2)
    return t


def resurf_body(seed):
    """The resurfacer's body: white enamel, the teal band, chipped and rust-streaked at the seams."""
    t = F.plain(seed, (232, 232, 226), 0.04, 0.6)
    area(t, 0, 26, N, 38, col=TEAL)
    area(t, 0, 38, N, 40, col=NAVY)
    t.speckle(0.04, (120, 80, 50), 0.5)
    t.streaks(0.5, 0.8)
    t.grime(0.2)
    return t


# ---------------------------------------------------------------- the stands on the concourse

STANDS = {
    "grill": ("GULLS GRILL", "HOT DOGS • BURGERS • FRIES", NAVY, WHITE,
              [("HOT DOG", "2.25"), ("FOOTLONG", "3.50"), ("CHEESEBURGER", "3.75"), ("FRIES", "1.75"), ("CHILI DOG", "2.75"), ("SODA", "1.50")]),
    "nacho": ("THE PENALTY BOX", "NACHOS • CHILI • PRETZELS", TEAL, WHITE,
              [("NACHOS", "2.75"), ("LOADED NACHOS", "4.00"), ("CHILI CUP", "2.50"), ("SOFT PRETZEL", "2.00"), ("HOT COCOA", "1.25"), ("COFFEE", "1.00")]),
}
HEADERS = {**{k: v[:4] for k, v in STANDS.items()}, "store": ("GULLS TEAM STORE", "JERSEYS • STICKS • PUCKS", NAVY, (236, 196, 90))}


@lru_cache(maxsize=None)
def stand_pic(k):
    """A stand's lit header over its counter, eight blocks across (the stadium's, in the Gulls' colours)."""
    from school import mixc
    name, sub, bg, ink = HEADERS[k]
    p = Pic(8, 1, bg, seed=23900 + ord(k[0]))
    W, H = p.W, p.H
    p.rect(0, 0, W, 3, fill=mixc(bg, (255, 255, 255), 0.3))
    p.rect(0, H - 3, W, H, fill=mixc(bg, (0, 0, 0), 0.4))
    p.text(150, H / 2 + 1, name, BLACK, civic.fit(name, BLACK, 30, 300), ink, anchor="mm")
    p.text(W - 112, H / 2 + 1, sub, BOLD, civic.fit(sub, BOLD, 16, 200), ink, anchor="mm")
    return p


def stand(k, i):
    t = stand_pic(k).tile(i, 23910 + ord(k[0]) * 8 + i, smooth=0.6, grime=0.15)
    t.emit[:] = np.clip((t.c @ np.array((0.3, 0.59, 0.11), np.float32)) / 255, 0, 1) * 0.5
    if t.rng.random() < 0.25:
        x = t.rng.uniform(10, 50)
        area(t, x, 0, x + 18, N, mul=0.45)
        t.emit[:, int(x):int(x) + 18] = 0
    return t


@lru_cache(maxsize=None)
def menu_pic(k):
    items, bg, ink = STANDS[k][4], STANDS[k][2], STANDS[k][3]
    p = Pic(2, 1, (18, 18, 20), seed=23950 + ord(k[0]))
    p.rect(0, 0, p.W, 9, fill=bg)
    p.text(p.W / 2, 4.5, STANDS[k][0], BLACK, 6, ink, anchor="mm")
    for j, (item, price) in enumerate(items):
        y = 15 + j * 8
        p.text(6, y, item, BOLD, 5, (232, 232, 226), anchor="lm")
        p.text(p.W - 6, y, price, BLACK, 5, AMBER, anchor="rm")
    return p


def menu(k, i):
    return menu_pic(k).tile(i, 23960 + ord(k[0]) * 2 + i, smooth=0.4, grime=0.2)


# ---------------------------------------------------------------- the ribbon board

RIBBON = {"a": "GRAYHAVEN GULLS  •  HARBOR ARENA  •",
          "b": "EMERGENCY SHELTER  •  STAY IN YOUR SECTION  •"}


@lru_cache(maxsize=None)
def ribbon_mask(k):
    """The LED board's message, sixteen by two, lit a dot to every two texels."""
    from PIL import Image, ImageDraw, ImageFont
    W, H = 16 * N, 2 * N
    im = Image.new("L", (W * 4, H * 4), 0)
    d = ImageDraw.Draw(im)
    size = 300
    f = ImageFont.truetype(BLACK, size)
    while d.textlength(RIBBON[k], font=f) > W * 4 - 80 and size > 60:
        size -= 10
        f = ImageFont.truetype(BLACK, size)
    d.text((W * 2, H * 2), RIBBON[k], font=f, fill=255, anchor="mm")
    a = np.asarray(im.resize((W, H), Image.BOX), np.float32) / 255
    yy, xx = np.mgrid[0:H, 0:W]
    dots = ((yy % 2 == 0) & (xx % 2 == 0)).astype(np.float32)
    blocky = a.reshape(H // 2, 2, W // 2, 2).max(axis=(1, 3))
    return np.repeat(np.repeat(blocky, 2, 0), 2, 1) * (0.55 + 0.45 * dots)


def ribbon(k, i):
    from gasstation import glow
    t = F.plain(23800 + ord(k) * 40 + i, (14, 14, 16), 0.03, 0.7)
    yy, xx = np.mgrid[0:N, 0:N]
    t.mix(((yy % 2 == 0) & (xx % 2 == 0)).astype(np.float32), (34, 30, 26), 0.8)
    m = ribbon_mask(k)
    cx, cy = (i % 16) * N, (i // 16) * N
    tile = m[cy:cy + N, cx:cx + N]
    if t.rng.random() < 0.15:
        x, y = t.rng.integers(0, N - 16, 2)
        tile = tile.copy()
        tile[y:y + 16, x:x + 16] = 0
    glow(t, tile, (255, 178, 60) if k == "a" else (250, 250, 240), 1.0, 0.95)
    return t


def textures():
    T = {}
    T["ar_ice"] = lambda: ice(23001)
    T["ar_ice_red"] = lambda: ice(23002, RED)
    T["ar_ice_blue"] = lambda: ice(23003, BLUE)
    T["ar_ice_crease"] = lambda: ice(23004, (120, 170, 220))
    T["ar_ice_melt"] = lambda: ice(23005, melt=True)
    for i in range(25):
        T[f"ar_ice_logo_{i}"] = lambda i=i: logo(i)
    T["ar_deck"] = lambda: deck(23050)
    T["ar_deck_b"] = lambda: deck(23051, "b")
    T["ar_deck_blood"] = lambda: deck(23052, "blood")
    T["ar_rubber"] = lambda: rubber(23053)
    T["ar_board"] = lambda: board(23054)
    for key in ADS:
        for i in range(4):
            T[f"ar_ad_{key}_{i}"] = lambda key=key, i=i: ad(key, i)
    T["ar_shield"] = lambda: shield(23060)
    T["ar_net"] = lambda: net(23061)
    T["ar_red"] = lambda: F.wear(F.plain(23062, RED, 0.04, 0.7), 0.15, 0)
    for i in range(24):
        T[f"ar_sb_{i}"] = lambda i=i: sb(i)
    for i in range(6):
        T[f"ar_sb_ring_{i}"] = lambda i=i: ring(i)
    T["ar_sb_house"] = lambda: F.wear(F.plain(23070, (34, 34, 38), 0.04, 0.5), 0.2, 0)
    for key in SIGNS:
        for i in range(2):
            T[f"ar_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key in VBANS:
        for i in range(3):
            T[f"ar_vban_{key}_{i}"] = lambda key=key, i=i: vban(key, i)
    for i in range(24):
        T[f"ar_marq_{i}"] = lambda i=i: marq(i)
    for c in LETTERS:
        for i in range(9):
            T[f"ar_big_{c.lower()}_{i}"] = lambda c=c, i=i: big(c, i)
    for k in HEADERS:
        for i in range(8):
            T[f"ar_stand_{k}_{i}"] = lambda k=k, i=i: stand(k, i)
    for k in STANDS:
        for i in range(2):
            T[f"ar_menu_{k}_{i}"] = lambda k=k, i=i: menu(k, i)
    for k in RIBBON:
        for i in range(32):
            T[f"ar_ribbon_{k}_{i}"] = lambda k=k, i=i: ribbon(k, i)
    T["ar_jersey"] = lambda: jersey(23080)
    T["ar_jersey_away"] = lambda: jersey(23081, False)
    T["ar_carpet"] = lambda: carpet(23082)
    T["ar_resurf"] = lambda: resurf_body(23083)
    T["ar_stick"] = lambda: F.plain(23084, (150, 110, 70), 0.06, 0.3)
    T["ar_tape"] = lambda: F.plain(23085, (30, 30, 32), 0.03, 0.1)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the resurfacer

def resurfacer():
    """The ice resurfacer, nose east (+x), four blocks long, two across (z 0..32), in 16ths."""
    body, black, steel = "ar_resurf", "home_plastic_black", "fit_stainless"
    B = []
    # the snow tank and the body
    B.append([0, 6, 2, 46, 26, 30, {"all": body}])
    B.append([2, 26, 4, 42, 30, 28, {"all": body}])
    # the driver's seat, the column and the wheel, the guard rail behind
    B.append([46, 6, 4, 60, 12, 28, {"all": body}])
    B.append([50, 12, 10, 56, 16, 22, black])
    B.append([54, 16, 10, 56, 24, 22, black])
    B.append([40, 24, 14, 42, 30, 18, black])
    B.append([36, 30, 10, 44, 31, 22, black])
    B.append([58, 12, 4, 60, 22, 6, steel])
    B.append([58, 12, 26, 60, 22, 28, steel])
    B.append([58, 20, 6, 60, 22, 26, steel])
    # the conditioner (the blade and the towel) at the back
    B.append([60, 2, 2, 64, 12, 30, {"all": "fit_stainless"}])
    B.append([56, 0, 3, 64, 2, 29, "ar_tape"])
    # the wheels
    for x in (8, 38):
        for z in (0, 26):
            B.append([x, 0, z, x + 12, 12, z + 6, black])
    # the headlights and the windscreen
    B.append([0, 20, 4, 1, 24, 8, "home_white"])
    B.append([0, 20, 24, 1, 24, 28, "home_white"])
    B.append([42, 30, 6, 43, 40, 8, black])
    B.append([42, 30, 24, 43, 40, 26, black])
    B.append([42, 38, 6, 43, 40, 26, black])
    return B


def _cells(boxes):
    cells = {}
    for b in boxes:
        x0, y0, z0, x1, y1, z1 = b[:6]
        tex = b[6]
        for bx in range(int(math.floor(x0 / 16)), int(math.floor((x1 - 1e-6) / 16)) + 1):
            for by in range(int(math.floor(y0 / 16)), int(math.floor((y1 - 1e-6) / 16)) + 1):
                for bz in range(int(math.floor(z0 / 16)), int(math.floor((z1 - 1e-6) / 16)) + 1):
                    c = [max(x0, bx * 16) - bx * 16, max(y0, by * 16) - by * 16, max(z0, bz * 16) - bz * 16,
                         min(x1, bx * 16 + 16) - bx * 16, min(y1, by * 16 + 16) - by * 16, min(z1, bz * 16 + 16) - bz * 16]
                    if min(c[3] - c[0], c[4] - c[1], c[5] - c[2]) < 0.2:
                        continue
                    cells.setdefault((bx, by, bz), []).append([round(v, 2) for v in c] + [tex])
    return cells


@lru_cache(maxsize=None)
def resurf_cells():
    return _cells(resurfacer())


def cells_text():
    return ";".join(f"{x},{y},{z}" for x, y, z in sorted(resurf_cells()))


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

    black, white, chrome, steel = "home_plastic_black", "home_white", "home_chrome", "fit_stainless"

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    # ---- the rink
    for n in ("ar_ice", "ar_ice_red", "ar_ice_blue", "ar_ice_crease", "ar_ice_melt"):
        add(n, None, "Ice", "GLASS", rotate=None, tex={"up": n, "side": "concrete_panel", "down": "concrete_panel"})
    for i in range(25):
        add(f"ar_ice_logo_{i}", None, "Ice", "GLASS", rotate=None,
            tex={"up": f"ar_ice_logo_{i}", "side": "concrete_panel", "down": "concrete_panel"})
    for n in ("ar_deck", "ar_deck_b", "ar_deck_blood"):
        add(n, None, "Plywood Deck", "WOOD", rotate=None, tex={"up": n, "side": "concrete_panel", "down": "concrete_panel"})
    add("ar_rubber", None, "Rubber Matting", "WOOL", rotate=None, tex={"up": "ar_rubber", "side": "concrete_panel", "down": "concrete_panel"})
    add("ar_carpet", None, "Carpet", "WOOL", rotate=None, tex={"up": "ar_carpet", "side": "concrete_panel", "down": "concrete_panel"})

    # ---- the boards: the face toward the rink (north in the base model), white behind
    def dasher(front):
        return [[0, 0, 0, 16, 16, 16, {"north": front, "south": "ar_board", "side": "ar_board", "up": "ar_sb_house", "down": "ar_board"}]]

    add("ar_dasher", dasher("ar_board"), "Dasher Boards", "STONE")
    for key in ADS:
        for i in range(4):
            add(f"ar_ad_{key}_{i}", dasher(f"ar_ad_{key}_{i}"), "Dasher Boards", "STONE")
    add("ar_shield", None, "Rink Glass", "GLASS", rotate=None, tex={"all": "ar_shield"}, render="translucent")
    # the goal, two blocks wide, its mouth north; 0 the viewer's left
    for i in range(2):
        post_x = (14, 16) if i == 0 else (0, 2)
        boxes = [[post_x[0], 0, 4, post_x[1], 12, 6, "ar_red"], [0, 12, 4, 16, 14, 6, "ar_red"],
                 [0, 0, 4, 16, 12, 4.4, "ar_net"], [0, 0, 14, 16, 10, 14.4, "ar_net"], [0, 10, 6, 16, 12.2, 14, "ar_net"],
                 [0, 0, 14.4, 16, 1, 16, "ar_red"]]
        add(f"ar_goal_{i}", boxes, "Hockey Goal", "METAL", render="cutout")
    add("ar_goal_down", [[0, 0, 2, 16, 2, 4, "ar_red"], [2, 0, 4, 14, 0.5, 14, "ar_net"], [0, 0, 14, 16, 1.6, 15.6, "ar_red"]],
        "Hockey Goal", "METAL", decor=True, render="cutout")

    # ---- the scoreboard
    for i in range(24):
        add(f"ar_sb_{i}", [[0, 0, 0, 16, 16, 16, {"north": f"ar_sb_{i}", "side": "ar_sb_house", "up": "ar_sb_house", "down": "ar_sb_house"}]],
            "Scoreboard", "METAL", light=5)
    for i in range(6):
        add(f"ar_sb_ring_{i}", [[0, 0, 0, 16, 16, 16, {"north": f"ar_sb_ring_{i}", "side": "ar_sb_house", "up": "ar_sb_house",
                                                      "down": "ar_sb_house"}]], "Scoreboard", "METAL")
    add("ar_sb_house", None, "Scoreboard", "METAL", rotate=None, tex={"all": "ar_sb_house"})
    for k in HEADERS:
        for i in range(8):
            n = f"ar_stand_{k}_{i}"
            add(n, [[0, 0, 0, 16, 16, 16, {"north": n, "side": "st_housing", "up": "st_housing", "down": "st_housing"}]], "Stand Sign", "METAL", light=4)
    for k in STANDS:
        for i in range(2):
            n = f"ar_menu_{k}_{i}"
            add(n, [[0, 2, 15, 16, 14, 16, {"north": n, "side": "st_housing", "up": "st_housing", "down": "st_housing"}]], "Menu Board", "METAL",
                decor=True)
    for k in RIBBON:
        for i in range(32):
            n = f"ar_ribbon_{k}_{i}"
            add(n, [[0, 0, 0, 16, 16, 16, {"north": n, "side": "st_housing", "up": "st_housing", "down": "st_housing"}]], "Ribbon Board", "METAL",
                light=6)

    # ---- plates
    for key in SIGNS:
        for i in range(2):
            add(f"ar_sgn_{key}_{i}", plate(f"ar_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key in VBANS:
        for i in range(3):
            n = f"ar_vban_{key}_{i}"
            add(n, [[1, 0, 7.6, 15, 16, 8.4, {"north": n, "south": n, "side": "ar_sb_house", "up": "ar_sb_house", "down": "ar_sb_house"}]]
                + ([[0, 15, 7, 16, 16, 9, chrome]] if i == 0 else []), "Banner", "WOOL", decor=True, render="cutout")
    for i in range(24):
        add(f"ar_marq_{i}", [[0, 0, 0, 16, 16, 16, {"north": f"ar_marq_{i}", "side": "ar_sb_house", "up": "ar_sb_house", "down": "ar_sb_house"}]],
            "Marquee", "METAL", light=4 if i < 8 else None)
    for c in LETTERS:
        for i in range(9):
            n = f"ar_big_{c.lower()}_{i}"
            add(n, [[0, 0, 14.5, 16, 16, 16, {"north": n, "side": n}]], "Letter", "METAL", decor=True, render="cutout")

    # ---- the rooms
    wood = "cv_oak"
    add("ar_stall", [[0, 0, 15, 16, 16, 16, wood], [0, 0, 0, 1, 16, 16, wood], [15, 0, 0, 16, 16, 16, wood],
                     [1, 4, 2, 15, 5, 15, wood], [1, 13, 4, 15, 14, 15, wood],
                     [3, 5.5, 9, 13, 12.5, 13, {"north": "ar_jersey", "south": "ar_jersey", "side": "ar_jersey", "up": "ar_jersey",
                                                 "down": "ar_jersey"}],
                     [7.5, 12.5, 11, 8.5, 13, 12, chrome], [3, 0, 6, 7, 3, 13, black], [9, 0, 6, 13, 3, 13, black]],
        "Locker Stall", "WOOD", loot="storage", shape=[[0, 0, 0, 16, 16, 16]])
    add("ar_stall_away", [[0, 0, 15, 16, 16, 16, wood], [0, 0, 0, 1, 16, 16, wood], [15, 0, 0, 16, 16, 16, wood],
                          [1, 4, 2, 15, 5, 15, wood], [1, 13, 4, 15, 14, 15, wood],
                          [3, 5.5, 9, 13, 12.5, 13, {"all": "ar_jersey_away"}], [7.5, 12.5, 11, 8.5, 13, 12, chrome]],
        "Locker Stall", "WOOD", loot="storage", shape=[[0, 0, 0, 16, 16, 16]])
    sticks = []
    for k in range(6):
        x = 1.5 + k * 2.3
        sticks.append([x, 0, 12, x + 1, 15, 13, "ar_stick", None, {"axis": "x", "angle": -11.25, "origin": [x, 0, 13]}])
        sticks.append([x - 0.5, 0, 10, x + 1.4, 1.2, 13, "ar_tape"])
    add("ar_sticks", sticks + [[0, 14, 14, 16, 15, 16, steel]], "Stick Rack", "WOOD", decor=True)
    skates = []
    for k, x in enumerate((2, 9)):
        skates += [[x, 1.5, 3 + k * 4, x + 4, 5, 9 + k * 4, black], [x, 0, 3 + k * 4, x + 4, 0.6, 10 + k * 4, steel],
                   [x + 1.6, 0.6, 3 + k * 4, x + 2.4, 1.5, 10 + k * 4, steel]]
    add("ar_skates", skates, "Skates", "WOOL", decor=True)
    for (x, y, z), boxes in resurf_cells().items():
        add(f"ar_resurf_{x}_{y}_{z}", boxes, "Ice Resurfacer", "METAL", rotate=None, decor=True)
    return out


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "cells":
        print(cells_text())
        return
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("ar_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} arena blocks in {path}")


if __name__ == "__main__":
    main()
