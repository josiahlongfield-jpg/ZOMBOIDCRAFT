"""Grayhaven Ballpark: the bp_ kit (world/county/Ballpark.java, BallparkRooms.java).

No photos of it in the library yet (city/ballpark-* to be queued): this follows the American
city ballparks of the early century and the brick ones built to look like them, the red brick
outer wall with its arches, the green steel of the stands, two decks wrapped round home plate
and down the lines to the poles, the bleachers across the outfield under the hand-turned
scoreboard, the ivy on the outfield wall. The Grayhaven Skippers (forest green, cream and red)
played here. The last weeks: the Guard took it for an evacuation landing zone, the pads painted
on the outfield grass, the tents and the sandbags, the processing lines through the gates; the
grass gone long since.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- the field: bp_grass_a, bp_grass_b (mown bands), bp_grass_rough, bp_grass_paint (white),
  bp_grass_paint_y (yellow), bp_clay, bp_track, bp_line_grass / bp_line_clay (axis: the chalk
  line runs along the axis), bp_rubber (axis), bp_plate, bp_base (the bag, decor);
- the walls: bp_pad (the padded field wall, its face toward the field), bp_ivy, bp_ivy_bare,
  bp_dist_D_0..1 (the distance on the outfield wall, two by one), bp_pole, bp_net (the backstop
  netting), bp_eye (the batter's eye), bp_ad_KEY_0..3 (four by one);
- the scoreboard: bp_sb_0..47 (eight by six, row 0 the top), bp_sb_house; bp_gate_0..23 (the name
  over the main gate, twelve by two); bp_flag_KEY_0..1 (pennants on the poles, two by one);
- the brick: bp_arch_0/1 (the two halves of an arch's head, two wide), bp_green (the steel),
  bp_column (a round steel column);
- plates: bp_sgn_KEY_0..1 (two by one); stand headers bp_stand_K_0..7, menus bp_menu_K_0..1;
- the seats: bp_seat(_up, _mix, _blanket, _trash, _broken) (the stadium's seat in green);
- the rooms: bp_stall, bp_stall_away, bp_bats, bp_helmets, bp_cage (batting cage netting),
  bp_turf, bp_machine (pitching machine), bp_balls, bp_tarp (the infield tarp's roll, axis),
  bp_bases (spare bags), bp_rake, bp_mower (a reel mower), bp_chalker.

    python3 tools/city/ballpark.py          rewrites the bp_ entries in blocks.json

photo_textures.py draws the textures (ballpark.register()).
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
import school  # noqa: E402
import stadium  # noqa: E402
from furniture import area, disc  # noqa: E402
from school import Pic, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
STENCIL = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
GREEN = (24, 70, 48)
CREAM = (236, 226, 196)
RED = (168, 34, 36)
OLIVE = (84, 88, 58)
WHITE = (240, 240, 234)
GREY = (60, 60, 64)

SIGNS = {
    "home": ("SKIPPERS CLUBHOUSE", GREEN, CREAM),
    "visitors": ("VISITORS CLUBHOUSE", GREEN, CREAM),
    "umpires": ("UMPIRES", GREEN, CREAM),
    "cages": ("BATTING CAGES", GREEN, CREAM),
    "grounds": ("GROUNDS CREW", GREY, WHITE),
    "kitchen": ("COMMISSARY", GREY, WHITE),
    "office": ("FRONT OFFICE", GREEN, CREAM),
    "press": ("PRESS", GREEN, CREAM),
    "aid": ("FIRST AID", (170, 24, 30), WHITE),
    "field": ("TO FIELD", GREEN, CREAM),
    "bleachers": ("BLEACHERS", GREEN, CREAM),
    "upper": ("UPPER DECK", GREEN, CREAM),
    "tickets": ("TICKETS", RED, CREAM),
    "store": ("SKIPPERS SHOP", GREEN, CREAM),
    "dock": ("SERVICE GATE", GREY, WHITE),
    "laundry": ("LAUNDRY", GREY, WHITE),
    # the Guard's, stencilled on board
    "lz": ("LZ CONTROL", OLIVE, (20, 20, 18)),
    "decon": ("DECON", (226, 206, 60), (20, 20, 18)),
    "armory": ("ARMORY - AUTHORIZED ONLY", OLIVE, (20, 20, 18)),
    "evac": ("EVACUATION PROCESSING", (226, 226, 220), (20, 20, 18)),
    "morgue": ("MORGUE - NO ENTRY", (226, 226, 220), (170, 20, 20)),
    "quarantine": ("QUARANTINE - KEEP OUT", (226, 206, 60), (20, 20, 18)),
}
MILITARY = {"lz", "decon", "armory", "evac", "morgue", "quarantine"}
ADS = {
    "cola": ("KESTREL COLA", (196, 26, 32), WHITE),
    "savings": ("HARBOR SAVINGS & LOAN", CREAM, GREEN),
    "motors": ("MARLOWE MOTORS", (24, 60, 120), WHITE),
    "brew": ("BAYSIDE BREWING CO.", (30, 30, 30), (236, 196, 90)),
    "radio": ("WGHV RADIO 1050 AM", (250, 196, 30), (30, 30, 30)),
}
DISTANCES = ("315", "350", "375", "400")
FLAGS = {"a": ("1957", RED), "b": ("1974", GREEN), "c": ("SKIPPERS", (24, 60, 120))}


# ---------------------------------------------------------------- the field

def paint(t, mask, col):
    """Field paint over the grass, the blades poking through it."""
    holes = t.rng.random((N, N)) < 0.14
    m = np.clip(mask, 0, 1) * np.where(holes, 0.4, 1.0) * 0.9
    t.mix(m, col)
    return t


def grass(seed, dark=False):
    """The outfield a season after anyone mowed it: the bands still there under the long blades."""
    t = school.turf(seed, dark=dark, rough=True)
    return t


def clay(seed, raked=True):
    """Infield clay, red-brown, the drag's lines through it gone soft, cleat marks and a few weeds."""
    t = F.plain(seed, (150, 98, 64), 0.08, 0.05)
    rng = t.rng
    t.speckle(0.25, (120, 76, 50), 0.4, 0.2)
    t.speckle(0.08, (176, 128, 92), 0.3, 0.4)
    if raked:
        for y in range(0, N, 5):
            area(t, 0, y, N, y + 1, mul=0.9, h=0.3)
    for _ in range(14):
        x, y = rng.uniform(4, N - 8, 2)
        area(t, x, y, x + 3, y + 5, mul=0.82, h=0.25)
    for _ in range(4):
        disc(t, rng.uniform(0, N), rng.uniform(0, N), 1.6, (90, 116, 60), h=0.8)
    t.grime(0.1, (70, 50, 36))
    return t


def track(seed):
    """The warning track: crushed red brick, darker and coarser than the infield."""
    t = F.plain(seed, (122, 70, 52), 0.1, 0.05)
    t.speckle(0.4, (90, 52, 40), 0.5, 0.5)
    t.speckle(0.15, (160, 110, 90), 0.4, 0.6)
    for _ in range(5):
        disc(t, t.rng.uniform(0, N), t.rng.uniform(0, N), 1.4, (80, 106, 56), h=0.8)
    return t


def line(t):
    """A chalk line four inches wide down the middle of the block (north-south in the texture)."""
    m = np.zeros((N, N), np.float32)
    m[:, 29:35] = 1
    keep = (t.rng.random(N) > 0.25)
    m *= keep[:, None]
    return paint(t, m, (236, 236, 228))


def rubber(seed):
    t = clay(seed, raked=False)
    area(t, 26, 0, 38, N, col=(232, 230, 222), h=0.7)
    t.grime(0.2, (90, 60, 40))
    return t


def plate(seed):
    """Home plate in the clay, the batter's boxes chalked either side."""
    t = clay(seed, raked=False)
    p = [(20, 22), (44, 22), (44, 36), (32, 46), (20, 36)]
    from PIL import Image, ImageDraw
    im = Image.new("L", (N * 4, N * 4), 0)
    ImageDraw.Draw(im).polygon([(x * 4, y * 4) for x, y in p], fill=255)
    m = np.asarray(im.resize((N, N), Image.BOX), np.float32) / 255
    t.mix(m, (234, 232, 224))
    t.h = np.maximum(t.h, m * 0.7)
    for x in (4, 58):
        area(t, x, 8, x + 2, 56, col=(226, 224, 214))
    t.grime(0.15, (90, 60, 40))
    return t


def canvas(seed):
    t = F.plain(seed, (236, 234, 226), 0.04, 0.2)
    t.grime(0.35, (110, 90, 70))
    return t


# ---------------------------------------------------------------- the walls

def pad(seed):
    """The padded field wall: green vinyl in panels, the seams, the yellow home-run line along its top."""
    t = F.plain(seed, (30, 78, 52), 0.04, 0.35)
    for x in (0, 31, 32, 63):
        area(t, x, 0, x + 1, N, mul=0.6, h=0.4)
    area(t, 0, 0, N, 5, col=(232, 196, 40))
    t.speckle(0.03, (200, 200, 190), 0.3)
    t.streaks(0.3, 0.85)
    t.grime(0.2)
    return t


def ivy(seed, bare=False):
    """Ivy on the brick of the outfield wall, gone red and bronze at the end of October; where it's
    bare the brick shows through between the old stems."""
    t = F.plain(seed, (130, 60, 46), 0.05, 0.1)
    rng = t.rng
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 1, (170, 160, 140))
        off = 0 if (y // 8) % 2 == 0 else 8
        for x in range(off, N, 16):
            area(t, x, y, x + 1, y + 8, (170, 160, 140))
    leaves = 260 if not bare else 50
    for _ in range(leaves):
        x, y = rng.uniform(0, N, 2)
        r = rng.uniform(1.6, 3.4)
        k = rng.random()
        col = (64, 96, 44) if k < 0.35 else (150, 52, 34) if k < 0.7 else (176, 120, 46) if k < 0.88 else (96, 60, 38)
        disc(t, x, y, r, col, h=rng.uniform(0.6, 1.0))
    for _ in range(10 if not bare else 18):
        x = rng.uniform(0, N)
        for y in range(N):
            x += rng.uniform(-0.8, 0.8)
            area(t, int(x) % N, y, int(x) % N + 1, y + 1, (70, 52, 38), h=0.7)
    t.grime(0.1)
    return t


@lru_cache(maxsize=None)
def dist_pic(d):
    p = Pic(2, 1, (0, 0, 0, 0), 31000 + int(d))
    p.rect(2, 8, p.W - 2, p.H - 8, fill=GREEN)
    p.rect(2, 8, p.W - 2, 11, fill=CREAM)
    p.rect(2, p.H - 11, p.W - 2, p.H - 8, fill=CREAM)
    p.text(p.W / 2, p.H / 2 + 1, d, SERIF, 34, CREAM, anchor="mm")
    return p


def dist(d, i):
    return dist_pic(d).tile(i, 31010 + int(d) + i, smooth=0.3, grime=0.2)


def net(seed):
    """The backstop's netting, dark knotted mesh you see the field through."""
    t = F.plain(seed, (30, 30, 30), 0.03, 0.2)
    t.alpha[:] = 0
    for k in range(0, N, 6):
        t.alpha[:, k] = 255
        t.alpha[k, :] = 255
    return t


@lru_cache(maxsize=None)
def ad_pic(key):
    text, field, ink = ADS[key]
    p = Pic(4, 1, field, 31100 + len(key) * 7)
    p.rect(0, 0, p.W, 3, fill=mixc(field, (0, 0, 0), 0.3))
    p.rect(0, p.H - 3, p.W, p.H, fill=mixc(field, (0, 0, 0), 0.3))
    p.text(p.W / 2, p.H / 2 + 1, text, BLACK, civic.fit(text, BLACK, 34, p.W - 30), ink, anchor="mm")
    return p


def ad(key, i):
    t = ad_pic(key).tile(i, 31120 + len(key) * 5 + i, smooth=0.3, grime=0.25)
    t.streaks(0.5, 0.8)
    return t


# ---------------------------------------------------------------- the scoreboard

LINE = {"VISITORS": ["0", "1", "0", "0", "2", "0", "", "", ""], "SKIPPERS": ["0", "0", "3", "0", "0", "", "", "", ""]}


@lru_cache(maxsize=None)
def sb_pic():
    """The hand-turned scoreboard over the bleachers, eight by six: the name across the top round
    the clock, the line score by innings, the game stopped in the sixth, the Guard's notice
    painted over the out-of-town board."""
    p = Pic(8, 6, (30, 70, 50), 31200)
    W, H = p.W, p.H
    p.rect(0, 0, W, 10, fill=(22, 52, 38))
    p.text(W / 2 - 128, 46, "GRAYHAVEN", SERIF, civic.fit("GRAYHAVEN", SERIF, 34, 196), CREAM, anchor="mm")
    p.text(W / 2 + 128, 46, "SKIPPERS", SERIF, civic.fit("SKIPPERS", SERIF, 34, 196), CREAM, anchor="mm")
    p.ellipse(W / 2 - 28, 18, W / 2 + 28, 74, fill=CREAM, outline=(20, 20, 20), width=3)
    for k in range(12):
        a = k * math.pi / 6
        p.line([(W / 2 + math.sin(a) * 22, 46 - math.cos(a) * 22), (W / 2 + math.sin(a) * 26, 46 - math.cos(a) * 26)], (20, 20, 20), 2)
    p.line([(W / 2, 46), (W / 2 + 12, 40)], (20, 20, 20), 3)
    p.line([(W / 2, 46), (W / 2 - 4, 28)], (20, 20, 20), 2)
    x0, cw = 140, 26
    top = 96
    for k in range(9):
        p.text(x0 + k * cw + cw / 2, top, str(k + 1), SERIF, 18, CREAM, anchor="mm")
    for j, k in enumerate(("R", "H", "E")):
        p.text(x0 + (10 + j) * cw + cw / 2, top, k, SERIF, 18, CREAM, anchor="mm")
    for r, (team, runs) in enumerate(LINE.items()):
        y = top + 34 + r * 40
        p.text(14, y, team, SERIF, 20, CREAM, anchor="lm")
        for k in range(9):
            cx = x0 + k * cw + 2
            p.rect(cx, y - 15, cx + cw - 4, y + 15, fill=(18, 20, 18))
            if runs[k]:
                p.text(cx + (cw - 4) / 2, y + 1, runs[k], BLACK, 22, WHITE, anchor="mm")
        for j, v in enumerate(("3", "7", "1") if r == 0 else ("3", "6", "0")):
            cx = x0 + (10 + j) * cw + 2
            p.rect(cx, y - 15, cx + cw - 4, y + 15, fill=(18, 20, 18))
            p.text(cx + (cw - 4) / 2, y + 1, v, BLACK, 22, WHITE, anchor="mm")
    y = top + 120
    for j, (k, v) in enumerate((("BALL", "2"), ("STRIKE", "1"), ("OUT", "1"))):
        cx = 40 + j * 150
        p.text(cx, y, k, SERIF, 16, CREAM, anchor="lm")
        p.rect(cx + 76, y - 13, cx + 104, y + 13, fill=(18, 20, 18))
        p.text(cx + 90, y + 1, v, BLACK, 20, (240, 200, 60), anchor="mm")
    # the notice over the out-of-town board, painted on plywood
    by = y + 30
    p.rect(20, by, W - 20, H - 18, fill=(214, 206, 180))
    p.rect(20, by, W - 20, by + 4, fill=(150, 140, 110))
    p.text(W / 2, by + 26, "GAME SUSPENDED", BLACK, 26, (170, 20, 20), anchor="mm")
    p.text(W / 2, by + 60, "EVACUATION LZ - FOLLOW THE SIGNS", STENCIL, 18, (20, 20, 18), anchor="mm")
    p.text(W / 2, by + 88, "ONE BAG EACH - NO PETS - NO WEAPONS", STENCIL, 15, (20, 20, 18), anchor="mm")
    return p


def sb(i):
    t = sb_pic().tile(i, 31210 + i, smooth=0.2, grime=0.25)
    t.streaks(0.5, 0.8)
    return t


@lru_cache(maxsize=None)
def gate_pic():
    """The name over the main gate, twelve by two: cream serif capitals on green steel, the
    Skippers' line under it."""
    p = Pic(12, 2, GREEN, 31300)
    W, H = p.W, p.H
    p.rect(0, 0, W, 6, fill=CREAM)
    p.rect(0, H - 6, W, H, fill=CREAM)
    p.rect(8, 12, W - 8, H - 12, outline=CREAM, width=2)
    p.text(W / 2, H * 0.4, "GRAYHAVEN BALLPARK", SERIF, civic.fit("GRAYHAVEN BALLPARK", SERIF, 62, W - 60), CREAM, anchor="mm")
    p.text(W / 2, H * 0.78, "HOME OF THE SKIPPERS", SERIF, 24, (232, 196, 90), anchor="mm")
    return p


def gate(i):
    t = gate_pic().tile(i, 31310 + i, smooth=0.45, grime=0.2)
    t.streaks(0.6, 0.75)
    return t


@lru_cache(maxsize=None)
def flag_pic(k):
    text, col = FLAGS[k]
    p = Pic(2, 1, (0, 0, 0, 0), 31400 + ord(k))
    p.poly([(4, 6), (p.W - 6, p.H / 2), (4, p.H - 6)], fill=col)
    p.text(p.W * 0.36, p.H / 2, text, SERIF, civic.fit(text, SERIF, 18, p.W * 0.5), CREAM, anchor="mm")
    return p


def flag(k, i):
    return flag_pic(k).tile(i, 31410 + ord(k) * 2 + i, smooth=0.1, grime=0.15)


# ---------------------------------------------------------------- plates

@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    mil = key in MILITARY
    p = Pic(2, 1, (0, 0, 0, 0), 31500 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 14, W - 1, H - 14, fill=field)
    if mil:
        p.text(W / 2, H / 2 + 1, text, STENCIL, civic.fit(text, STENCIL, 15, W - 14), ink, anchor="mm")
    else:
        p.rect(1, 14, W - 1, 18, fill=CREAM if field == GREEN else mixc(field, (0, 0, 0), 0.2))
        p.text(W / 2, H / 2 + 2, text, SERIF, civic.fit(text, SERIF, 14, W - 14), ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 31550 + i + len(key) * 11, smooth=0.3 if key in MILITARY else 0.45, grime=0.25)


STANDS = {
    "dogs": ("FRANKS & BREW", "HOT DOGS • BRATS • BEER", GREEN, CREAM,
             [("HOT DOG", "1.75"), ("BRATWURST", "2.50"), ("PEANUTS", "1.25"), ("CRACKER JACK", "1.00"), ("DRAFT BEER", "2.00"), ("SODA", "1.00")]),
    "crab": ("THE CRAB SHACK", "CRAB CAKES • CHOWDER • FRIES", RED, CREAM,
             [("CRAB CAKE", "4.50"), ("CHOWDER CUP", "2.25"), ("FISH & CHIPS", "3.75"), ("FRIES", "1.50"), ("LEMONADE", "1.25"), ("COFFEE", "0.75")]),
    "brew": ("BAYSIDE TAPROOM", "ON TAP • BOTTLES • PRETZELS", (30, 30, 30), (236, 196, 90),
             [("BAYSIDE LAGER", "2.25"), ("HARBOR ALE", "2.50"), ("BOTTLED", "2.75"), ("SOFT PRETZEL", "1.50"), ("PEANUTS", "1.25"), ("SODA", "1.00")]),
}
HEADERS = {**{k: v[:4] for k, v in STANDS.items()}, "store": ("SKIPPERS SHOP", "CAPS • JERSEYS • PENNANTS", GREEN, (232, 196, 90))}


@lru_cache(maxsize=None)
def stand_pic(k):
    name, sub, bg, ink = HEADERS[k]
    p = Pic(8, 1, bg, seed=31600 + ord(k[0]))
    W, H = p.W, p.H
    p.rect(0, 0, W, 3, fill=mixc(bg, (255, 255, 255), 0.3))
    p.rect(0, H - 3, W, H, fill=mixc(bg, (0, 0, 0), 0.4))
    p.text(150, H / 2 + 1, name, SERIF, civic.fit(name, SERIF, 30, 290), ink, anchor="mm")
    p.text(W - 112, H / 2 + 1, sub, BOLD, civic.fit(sub, BOLD, 16, 200), ink, anchor="mm")
    return p


def stand(k, i):
    t = stand_pic(k).tile(i, 31610 + ord(k[0]) * 8 + i, smooth=0.6, grime=0.15)
    t.emit[:] = np.clip((t.c @ np.array((0.3, 0.59, 0.11), np.float32)) / 255, 0, 1) * 0.5
    if t.rng.random() < 0.25:
        x = t.rng.uniform(10, 50)
        area(t, x, 0, x + 18, N, mul=0.45)
        t.emit[:, int(x):int(x) + 18] = 0
    return t


@lru_cache(maxsize=None)
def menu_pic(k):
    items, bg, ink = STANDS[k][4], STANDS[k][2], STANDS[k][3]
    p = Pic(2, 1, (18, 18, 20), seed=31650 + ord(k[0]))
    p.rect(0, 0, p.W, 9, fill=bg)
    p.text(p.W / 2, 4.5, STANDS[k][0], BLACK, 6, ink, anchor="mm")
    for j, (item, price) in enumerate(items):
        y = 15 + j * 8
        p.text(6, y, item, BOLD, 5, (232, 232, 226), anchor="lm")
        p.text(p.W - 6, y, price, BLACK, 5, (240, 200, 60), anchor="rm")
    return p


def menu(k, i):
    return menu_pic(k).tile(i, 31660 + ord(k[0]) * 2 + i, smooth=0.4, grime=0.2)


# ---------------------------------------------------------------- the rooms

def jersey(seed, home=True):
    """A jersey on its hanger: cream with green pinstripes (home), road grey with the city in red."""
    t = F.plain(seed, CREAM if home else (150, 150, 150), 0.04, 0.15)
    if home:
        for x in range(2, N, 6):
            area(t, x, 0, x + 1, N, col=GREEN)
    else:
        area(t, 10, 18, 54, 26, col=RED)
    area(t, 0, 0, N, 5, col=GREEN if home else RED)
    return t


def steel(seed, rgb=GREEN):
    """Riveted steel painted the park's green, flaking to rust along the edges."""
    t = F.plain(seed, rgb, 0.05, 0.45)
    t.speckle(0.04, (120, 72, 44), 0.6)
    for x in (4, 60):
        for y in range(4, N, 12):
            disc(t, x, y, 1.2, mixc(rgb, (255, 255, 255), 0.15), h=0.9)
    t.streaks(0.4, 0.8)
    t.grime(0.2)
    return t


def turf_mat(seed):
    t = F.plain(seed, (44, 110, 60), 0.06, 0.1)
    t.speckle(0.5, (34, 90, 50), 0.4, 0.4)
    t.grime(0.25)
    return t


def tarp(seed):
    t = F.plain(seed, (40, 60, 120), 0.05, 0.5)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=0.7)
    t.grime(0.3)
    return t


def textures():
    T = {}
    T["bp_grass_a"] = lambda: grass(31001)
    T["bp_grass_b"] = lambda: grass(31002, dark=True)
    T["bp_grass_rough"] = lambda: school.turf(31003, rough=True, mud=True)
    T["bp_grass_paint"] = lambda: paint(grass(31004), np.ones((N, N), np.float32), (236, 236, 228))
    T["bp_grass_paint_y"] = lambda: paint(grass(31005), np.ones((N, N), np.float32), (232, 196, 40))
    T["bp_clay"] = lambda: clay(31006)
    T["bp_track"] = lambda: track(31007)
    T["bp_line_grass"] = lambda: line(grass(31008))
    T["bp_line_clay"] = lambda: line(clay(31009, raked=False))
    T["bp_rubber"] = lambda: rubber(31010)
    T["bp_plate"] = lambda: plate(31011)
    T["bp_canvas"] = lambda: canvas(31012)
    T["bp_pad"] = lambda: pad(31013)
    T["bp_ivy"] = lambda: ivy(31014)
    T["bp_ivy_bare"] = lambda: ivy(31015, bare=True)
    for d in DISTANCES:
        for i in range(2):
            T[f"bp_dist_{d}_{i}"] = lambda d=d, i=i: dist(d, i)
    T["bp_net"] = lambda: net(31016)
    T["bp_eye"] = lambda: F.wear(F.plain(31017, (22, 44, 32), 0.03, 0.05), 0.15, 0)
    T["bp_pole"] = lambda: F.wear(F.plain(31018, (232, 196, 40), 0.04, 0.5), 0.2, 0)
    for key in ADS:
        for i in range(4):
            T[f"bp_ad_{key}_{i}"] = lambda key=key, i=i: ad(key, i)
    for i in range(48):
        T[f"bp_sb_{i}"] = lambda i=i: sb(i)
    T["bp_sb_house"] = lambda: steel(31019, (26, 58, 42))
    for i in range(24):
        T[f"bp_gate_{i}"] = lambda i=i: gate(i)
    for k in FLAGS:
        for i in range(2):
            T[f"bp_flag_{k}_{i}"] = lambda k=k, i=i: flag(k, i)
    T["bp_green"] = lambda: steel(31020)
    for key in SIGNS:
        for i in range(2):
            T[f"bp_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for k in HEADERS:
        for i in range(8):
            T[f"bp_stand_{k}_{i}"] = lambda k=k, i=i: stand(k, i)
    for k in STANDS:
        for i in range(2):
            T[f"bp_menu_{k}_{i}"] = lambda k=k, i=i: menu(k, i)
    T["bp_plastic"] = lambda: stadium.plastic(31021, (36, 96, 60))
    T["bp_plastic_fade"] = lambda: stadium.plastic(31022, (36, 96, 60), 0.5)
    T["bp_jersey"] = lambda: jersey(31023)
    T["bp_jersey_away"] = lambda: jersey(31024, False)
    T["bp_turf"] = lambda: turf_mat(31025)
    T["bp_tarp"] = lambda: tarp(31026)
    T["bp_ash"] = lambda: F.plain(31027, (200, 170, 120), 0.06, 0.4)
    T["bp_ball"] = lambda: F.wear(F.plain(31028, (236, 232, 220), 0.03, 0.3), 0.25, 0)
    return T


def register(ns):
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

    black, white, chrome, steelt = "home_plastic_black", "home_white", "home_chrome", "fit_stainless"
    green = "bp_green"

    def plate_(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    # ---- the field
    for n in ("bp_grass_a", "bp_grass_b", "bp_grass_rough", "bp_grass_paint", "bp_grass_paint_y"):
        add(n, None, "Grass", "GRASS", rotate=None, tex={"up": n, "side": "bp_clay", "down": "bp_clay"})
    for n in ("bp_clay", "bp_track"):
        add(n, None, "Infield Clay" if n == "bp_clay" else "Warning Track", "GRAVEL", rotate=None, tex={"all": n})
    add("bp_line_grass", None, "Chalk Line", "GRASS", rotate="axis", tex={"up": "bp_line_grass", "side": "bp_clay", "down": "bp_clay"})
    add("bp_line_clay", None, "Chalk Line", "GRAVEL", rotate="axis", tex={"up": "bp_line_clay", "side": "bp_clay", "down": "bp_clay"})
    add("bp_rubber", None, "Pitcher's Rubber", "GRAVEL", rotate="axis", tex={"up": "bp_rubber", "side": "bp_clay", "down": "bp_clay"})
    add("bp_plate", None, "Home Plate", "GRAVEL", rotate="facing", tex={"up": "bp_plate", "side": "bp_clay", "down": "bp_clay"})
    add("bp_base", [[3, 0, 3, 13, 3, 13, "bp_canvas"]], "Base", "WOOL", rotate=None, decor=True)

    # ---- the walls
    add("bp_pad", [[0, 0, 0, 16, 16, 16, {"north": "bp_pad", "south": "concrete_panel", "side": "bp_pad", "up": "bp_green",
                                          "down": "concrete_panel"}]], "Padded Wall", "WOOL")
    for n in ("bp_ivy", "bp_ivy_bare"):
        add(n, [[0, 0, 0, 16, 16, 16, {"north": n, "south": "brick_red", "side": "brick_red", "up": "brick_red", "down": "brick_red"}]],
            "Ivy Wall", "STONE")
    for d in DISTANCES:
        for i in range(2):
            add(f"bp_dist_{d}_{i}", plate_(f"bp_dist_{d}_{i}"), "Distance Marker", "METAL", decor=True)
    add("bp_net", None, "Backstop Netting", "WOOL", rotate=None, tex={"all": "bp_net"}, render="cutout")
    add("bp_eye", None, "Batter's Eye", "METAL", rotate=None, tex={"all": "bp_eye"})
    add("bp_pole", [[6, 0, 6, 10, 16, 10, "bp_pole"], [10, 0, 7.6, 16, 16, 8.4, "bp_pole"]], "Foul Pole", "METAL", render="cutout")
    for key in ADS:
        for i in range(4):
            n = f"bp_ad_{key}_{i}"
            add(n, [[0, 0, 14, 16, 16, 16, {"north": n, "south": green, "side": green, "up": green, "down": green}]], "Billboard", "METAL")

    # ---- the scoreboard and the name over the gate
    for i in range(48):
        add(f"bp_sb_{i}", [[0, 0, 0, 16, 16, 16, {"north": f"bp_sb_{i}", "side": "bp_sb_house", "up": "bp_sb_house", "down": "bp_sb_house"}]],
            "Scoreboard", "METAL")
    add("bp_sb_house", None, "Scoreboard", "METAL", rotate=None, tex={"all": "bp_sb_house"})
    for i in range(24):
        add(f"bp_gate_{i}", [[0, 0, 13, 16, 16, 16, {"north": f"bp_gate_{i}", "south": green, "side": green, "up": green, "down": green}]],
            "Gate Sign", "METAL")
    for k in FLAGS:
        for i in range(2):
            n = f"bp_flag_{k}_{i}"
            add(n, [[0, 0, 7.6, 16, 16, 8.4, {"north": n, "south": n, "side": n, "up": n, "down": n}]], "Pennant", "WOOL", decor=True,
                render="cutout")

    # ---- the brick and the steel
    for i in range(2):
        # the arch's head over a two-wide opening: radius 16 about the seam at its foot; the viewer
        # of a north face sees x = 16 at the left, so tile 0's seam edge is its x = 0
        boxes = []
        for c in range(0, 16, 2):
            xm = c + 1
            r = abs(xm if i == 0 else 16 - xm)            # the crown at the seam: x = 0 on tile 0 (the viewer's left)
            y = math.sqrt(max(0.0, 256 - r * r))
            boxes.append([c, round(y, 2), 0, c + 2, 16, 16, "brick_red"])
        add(f"bp_arch_{i}", boxes, "Brick Arch", "STONE", shape=[[0, 8, 0, 16, 16, 16]])
    add("bp_green", None, "Painted Steel", "METAL", rotate=None, tex={"all": "bp_green"})
    add("bp_column", [[5, 0, 5, 11, 16, 11, "bp_green"], [4, 14, 4, 12, 16, 12, "bp_green"]], "Column", "METAL", rotate=None)

    # ---- plates
    for key in SIGNS:
        for i in range(2):
            add(f"bp_sgn_{key}_{i}", plate_(f"bp_sgn_{key}_{i}"), "Sign", "METAL" if key not in MILITARY else "WOOD", decor=True)
    for k in HEADERS:
        for i in range(8):
            n = f"bp_stand_{k}_{i}"
            add(n, [[0, 0, 0, 16, 16, 16, {"north": n, "side": "st_housing", "up": "st_housing", "down": "st_housing"}]], "Stand Sign", "METAL",
                light=4)
    for k in STANDS:
        for i in range(2):
            n = f"bp_menu_{k}_{i}"
            add(n, [[0, 2, 15, 16, 14, 16, {"north": n, "side": "st_housing", "up": "st_housing", "down": "st_housing"}]], "Menu Board",
                "METAL", decor=True)

    # ---- the seats: the stadium's, in the park's green
    data = json.loads((HERE / "blocks.json").read_text())["blocks"]
    for suf in ("", "_up", "_mix", "_blanket", "_trash", "_broken"):
        src = data.get("st_seat_g" + suf)
        if not src:
            continue
        spec = json.loads(json.dumps(src).replace("st_plastic_g_fade", "bp_plastic_fade").replace("st_plastic_g", "bp_plastic"))
        out["bp_seat" + suf] = spec

    # ---- the rooms
    wood = "cv_oak"
    for n, jt in (("bp_stall", "bp_jersey"), ("bp_stall_away", "bp_jersey_away")):
        add(n, [[0, 0, 15, 16, 16, 16, wood], [0, 0, 0, 1, 16, 16, wood], [15, 0, 0, 16, 16, 16, wood],
                [1, 4, 2, 15, 5, 15, wood], [1, 13, 4, 15, 14, 15, wood],
                [3, 5.5, 9, 13, 12.5, 13, {"all": jt}], [7.5, 12.5, 11, 8.5, 13, 12, chrome],
                [3, 0, 6, 7, 2.5, 13, black], [9, 0, 6, 13, 2.5, 13, black]],
            "Locker Stall", "WOOD", loot="clothes", shape=[[0, 0, 0, 16, 16, 16]])
    bats = []
    for k in range(7):
        x = 1.2 + k * 2.1
        bats.append([x, 0, 11.5, x + 1.1, 13, 12.6, "bp_ash", None, {"axis": "x", "angle": -11.25, "origin": [x, 0, 12.6]}])
    add("bp_bats", bats + [[0, 10, 13, 16, 11, 16, wood], [0, 0, 13, 16, 1, 16, wood]], "Bat Rack", "WOOD", decor=True)
    helm = []
    for r in range(2):
        for c in range(3):
            x, y = 1 + c * 5, 2 + r * 7
            helm += [[x, y, 9, x + 4, y + 3.5, 15, black], [x + 0.5, y, 7, x + 3.5, y + 0.6, 9, black]]
    add("bp_helmets", helm + [[0, 0, 15, 16, 16, 16, wood], [0, 8.5, 8, 16, 9, 16, wood], [0, 1.5, 8, 16, 2, 16, wood]],
        "Helmet Rack", "WOOD")
    add("bp_cage", None, "Cage Netting", "WOOL", rotate=None, tex={"all": "bp_net"}, render="cutout")
    add("bp_turf", None, "Turf Mat", "WOOL", rotate=None, tex={"up": "bp_turf", "side": "concrete_panel", "down": "concrete_panel"})
    add("bp_machine", [[3, 0, 3, 5, 6, 5, steelt], [11, 0, 3, 13, 6, 5, steelt], [7, 0, 11, 9, 6, 13, steelt],
                       [3, 6, 3, 13, 9, 13, "steel_yellow"], [4, 9, 2, 12, 13, 8, black], [5, 9, 8, 11, 15, 14, "steel_yellow"],
                       [6, 13, 1, 10, 15, 3, steelt]], "Pitching Machine", "METAL", decor=True)
    add("bp_balls", [[3, 0, 3, 13, 9, 13, "steel_red"], [3.6, 9, 3.6, 12.4, 10, 12.4, "bp_ball"]], "Ball Bucket", "METAL", decor=True)
    add("bp_tarp", [[1, 0, 0, 15, 14, 16, "bp_tarp"], [2, 14, 0, 14, 15, 16, "bp_tarp"]], "Tarp Roll", "WOOL", rotate="axis")
    add("bp_bases", [[2, 0, 2, 14, 3, 14, "bp_canvas"], [3, 3, 3, 13, 6, 13, "bp_canvas"], [2.5, 6, 2.5, 13.5, 9, 13.5, "bp_canvas"]],
        "Spare Bases", "WOOL", decor=True)
    add("bp_rake", [[7.4, 0, 12, 8.6, 15, 13.2, "bp_ash", None, {"axis": "x", "angle": -22.5, "origin": [8, 0, 13]}],
                    [2, 14, 7.5, 14, 16, 9.5, steelt]], "Field Rake", "WOOD", decor=True)
    add("bp_mower", [[2, 0, 3, 14, 6, 13, "steel_red"], [0, 1, 6, 2, 5, 10, black], [14, 1, 6, 16, 5, 10, black],
                     [7, 6, 11, 9, 16, 13, steelt], [3, 15, 11, 13, 16, 13, black]], "Reel Mower", "METAL", decor=True)
    add("bp_chalker", [[3, 0, 4, 13, 8, 12, "steel_red"], [1, 0, 6, 3, 4, 10, black], [13, 0, 6, 15, 4, 10, black],
                       [7, 8, 11, 9, 15, 13, steelt], [4, 14, 11, 12, 15, 13, black]], "Line Marker", "METAL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("bp_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} ballpark blocks in {path}")


if __name__ == "__main__":
    main()
