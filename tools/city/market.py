"""The Old Market: the om_ kit for Grayhaven's market house on the island's old harbor side
(world/county/Market.java, MarketHall.java).

No photos of it in the library yet (city/old-market-* to be queued): this follows the market
houses the harbor cities built in the 1820s and kept as food halls. A long granite hall with a
Doric portico at each end and a copper dome over its middle, the name cut in the frieze; inside,
the stalls down both sides of the aisle under their painted boards (the fish man's ice, the
butcher's hooks, the cheese, the bread, the fruit stood up in tilted crates); the long room
upstairs. Either side a cobbled lane of pushcarts and umbrellas under strings of bulbs, and the
brick rows of shops and lofts beyond. The last weeks: the market was the island's food line until
the food ran out, then a barter post in the rotunda where the stall keepers traded what was left.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- the walls: om_granite, om_granite_b, om_granite_moss, om_sash, om_sash_broken, om_sash_lit
  (a twelve-light wooden sash set back in the wall), om_fan_0..9 (the fanlight over an end door
  and its sidelights, five by two), om_rake_l_0..1, om_rake_r_0..1 (the pediment's raking cornice, one in two), om_name_0..21 (the frieze), om_date_0..7 (the tympanum's panel, four by two);
- the stalls: om_post, om_beam (axis), om_board_KEY_0..2 (the painted boards hung over the
  counters), om_fish, om_fish_rot, om_oyster, om_meat_case, om_cheese, om_bread, om_produce_a,
  om_produce_b, om_produce_rot, om_spice, om_candy, om_coffee, om_flowers, om_counter, om_block,
  om_hooks, om_hooks_empty, om_scale, om_crates, om_sacks, om_pickles, om_chalk_0..3;
- the lanes: om_cobble, om_cobble_b, om_cobble_moss, om_flags, om_flags_b, om_cart_fruit,
  om_cart_flowers, om_cart_empty, om_cart_tipped, om_umb_pole, om_umb_red, om_umb_green,
  om_umb_cream, om_lights, om_lights_on (axis), om_planter, om_bollard;
- the barter post: om_barter, om_wire_wall (axis), om_prices_0..5 (three by two), om_banner_0..5, om_sgn_KEY_0..1,
  om_spray_KEY_i.

    python3 tools/city/market.py          rewrites the om_ entries in blocks.json

photo_textures.py draws the textures (market.register()).
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import broadcast as B  # noqa: E402
from furniture import area, disc, drips  # noqa: E402
from school import Pic, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = B.BOLD
STENCIL = B.STENCIL
SPRAY = B.SPRAY
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
OLD = "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"
OLDI = "/usr/share/fonts/truetype/crosextra/Caladea-BoldItalic.ttf"
GRANITE = (154, 152, 146)
CREAM = (232, 222, 196)
GOLD = (206, 166, 70)
WHITE = (244, 242, 236)
INK = (30, 28, 26)

# the stall boards: the name, the board's colour, the lettering's, a gold rule or not
BOARDS = {
    "fish": ("HARBOR FISH CO.", (30, 64, 92), CREAM, True),
    "meat": ("DOYLE BROS. MEATS", (120, 30, 28), CREAM, True),
    "cheese": ("OLD WORLD CHEESE", (232, 200, 110), (60, 40, 20), False),
    "bread": ("KAPLAN'S BAKERY", (70, 46, 30), GOLD, True),
    "produce": ("FRESH PRODUCE", (40, 92, 48), CREAM, False),
    "greens": ("GREEN'S FRUIT & VEG", (196, 82, 40), CREAM, False),
    "spice": ("SPICE MERCHANT", (110, 40, 70), GOLD, True),
    "candy": ("SWEET SHOP", (220, 140, 160), (90, 30, 50), False),
    "coffee": ("HARBOR COFFEE", (40, 30, 24), (220, 190, 140), True),
    "oyster": ("OYSTER BAR", (24, 52, 70), WHITE, True),
    "flowers": ("FLOWERS", (232, 226, 210), (130, 40, 70), False),
    "deli": ("DELICATESSEN", (20, 70, 60), CREAM, True),
    "poultry": ("POULTRY & EGGS", (214, 180, 70), (50, 30, 20), False),
    "nuts": ("NUTS & DRIED FRUIT", (150, 90, 40), CREAM, False),
    "pickles": ("PICKLE WORKS", (60, 90, 40), (240, 220, 120), False),
    "donuts": ("DONUT COUNTER", (240, 236, 226), (180, 60, 40), False),
}
# the bank kit's raking cornice boxes (tools/city: bk_rake_*), redrawn here in granite
RAKES = {"l_0": [[0, 0, 0, 16, 2, 16], [0, 0, -2, 4, 2, 0], [4, 0, 0, 16, 4, 16], [4, 2, -2, 8, 4, 0], [8, 0, 0, 16, 6, 16], [8, 4, -2, 12, 6, 0], [12, 0, 0, 16, 8, 16], [12, 6, -2, 16, 8, 0]], "l_1": [[0, 0, 0, 16, 10, 16], [0, 8, -2, 4, 10, 0], [4, 0, 0, 16, 12, 16], [4, 10, -2, 8, 12, 0], [8, 0, 0, 16, 14, 16], [8, 12, -2, 12, 14, 0], [12, 0, 0, 16, 16, 16], [12, 14, -2, 16, 16, 0]], "r_0": [[0, 0, 0, 16, 2, 16], [12, 0, -2, 16, 2, 0], [0, 0, 0, 12, 4, 16], [8, 2, -2, 12, 4, 0], [0, 0, 0, 8, 6, 16], [4, 4, -2, 8, 6, 0], [0, 0, 0, 4, 8, 16], [0, 6, -2, 4, 8, 0]], "r_1": [[0, 0, 0, 16, 10, 16], [12, 8, -2, 16, 10, 0], [0, 0, 0, 12, 12, 16], [8, 10, -2, 12, 12, 0], [0, 0, 0, 8, 14, 16], [4, 12, -2, 8, 14, 0], [0, 0, 0, 4, 16, 16], [0, 14, -2, 4, 16, 0]]}
SIGNS = {
    "hall": ("MARKET HALL", (40, 46, 50), CREAM),
    "long": ("THE LONG ROOM", (40, 46, 50), CREAM),
    "master": ("MARKET MASTER", (40, 46, 50), CREAM),
    "rest": ("RESTROOMS", (40, 46, 50), CREAM),
    "up": ("STAIRS", (40, 46, 50), CREAM),
    "noguns": ("NO GUNS PAST HERE", (170, 30, 26), WHITE),
    "loading": ("LOADING ONLY", (220, 180, 40), INK),
    "noparking": ("NO PARKING CARTS ONLY", (170, 30, 26), WHITE),
}
SPRAYS = {
    "trade": ("TRADE POST INSIDE", 4),
    "food": ("NO FOOD LEFT", 3),
    "rats": ("RATS", 2),
    "keep": ("STALL KEEPERS ONLY", 4),
}


# ---------------------------------------------------------------- walls

def granite(seed, moss=False, worn=False):
    """Ashlar granite: grey with its black and white grains, two courses to a block, the joints
    struck flush, the face weathered darker toward the bottom; worn has a skin of soot."""
    t = F.plain(seed, GRANITE, 0.05, 0.35)
    rng = t.rng
    t.speckle(0.18, (70, 70, 72), 0.55, -0.02)
    t.speckle(0.1, (214, 212, 206), 0.45, 0.02)
    t.speckle(0.03, (120, 104, 96), 0.4)
    for y0, xs in ((0, (0,)), (32, (32,))):
        area(t, 0, y0, N, y0 + 1, col=(104, 102, 98), dh=-0.25)
        for x in xs:
            area(t, x, y0, x + 1, y0 + 32, col=(104, 102, 98), dh=-0.25)
    for y0 in (0, 32):
        for x0 in ((0, 32) if y0 == 0 else (-32, 32)):
            area(t, x0 + 1, y0 + 1, x0 + 32, y0 + 32, mul=rng.uniform(0.9, 1.08))
    y = np.arange(N)[:, None] / N
    t.c *= (1.02 - 0.1 * y)[..., None]
    if worn:
        t.grime(0.45, (46, 44, 42))
    if moss:
        t.mix(np.clip(P.fbm(rng) * 1.4 + y * 0.6 - 0.2, 0, 1), (70, 86, 50), 0.6)
    t.streaks(0.4, 0.82)
    return t


def sash(seed, lit=False, broken=False):
    """A twelve-light wooden sash, the bars painted cream gone grey, the old glass a little green."""
    t = F.plain(seed, (90, 100, 96), 0.05, 0.9)
    rng = t.rng
    y = np.arange(N)[:, None] / N
    t.c[:] = np.array((96, 110, 106), np.float32) * (0.85 + 0.3 * (1 - y))[..., None]
    t.vary(0.06)
    t.alpha[:] = 140
    t.f0[:] = 20
    if lit:
        t.c[:] = mixc((236, 200, 130), (140, 110, 70), 0.25)
        t.alpha[:] = 225
        t.emit[:] = 0.55
    for k in range(3):
        for j in range(4):
            x0, y0 = k * 21 + 2, j * 16 + 2
            if broken and rng.random() < 0.3:
                area(t, x0, y0, x0 + 18, y0 + 13, alpha=0)
    for x in (0, 21, 42, 63):
        area(t, x - 1, 0, x + 2, N, col=(196, 190, 170), alpha=255, h=0.7, smooth=0.3)
    for yy in (0, 16, 31, 32, 48, 63):
        area(t, 0, yy - 1, N, yy + 1, col=(196, 190, 170), alpha=255, h=0.7, smooth=0.3)
    area(t, 0, 30, N, 34, col=(180, 172, 150), alpha=255, h=0.8)
    t.grime(0.25, (60, 56, 48))
    return t


@lru_cache(maxsize=None)
def fan_pics():
    """The fanlight over an end door and its sidelights: a half wheel of glass in its granite, five by two."""
    mask = Pic(5, 2, GRANITE, 43000)
    W, H = mask.W, mask.H
    for y in range(0, H, 32):
        mask.rect(0, y, W, y + 0.6, fill=(104, 102, 98))
    cx, cy, ro = W / 2, H, W / 2 - 4
    mask.ellipse(cx - ro - 6, cy - ro - 6, cx + ro + 6, cy + ro + 6, fill=(176, 172, 164))
    for k in range(0, 181, 12):
        a = np.radians(180 + k)
        mask.line([(cx + np.cos(a) * ro, cy + np.sin(a) * ro), (cx + np.cos(a) * (ro + 6), cy + np.sin(a) * (ro + 6))], (110, 108, 102), 0.6)
    mask.d.ellipse([(cx - ro) * 4, (cy - ro) * 4, (cx + ro) * 4, (cy + ro) * 4], fill=(0, 0, 0, 0))
    glass = Pic(5, 2, (0, 0, 0, 0), 43001)
    glass.d.ellipse([(cx - ro) * 4, (cy - ro) * 4, (cx + ro) * 4, (cy + ro) * 4], fill=(110, 124, 116, 140))
    for k in range(1, 12):
        a = np.radians(180 + k * 15)
        glass.line([(cx, cy), (cx + np.cos(a) * ro, cy + np.sin(a) * ro)], (210, 204, 186), 1.4)
    for r in (ro * 0.3, ro * 0.7):
        glass.ellipse(cx - r, cy - r, cx + r, cy + r, outline=(210, 204, 186), width=1.4)
    glass.ellipse(cx - ro * 0.3, cy - ro * 0.3, cx + ro * 0.3, cy + ro * 0.3, fill=(210, 204, 186))
    return mask, glass


def fan(i, part):
    mask, glass = fan_pics()
    if part == "mask":
        t = mask.tile(i, 43010 + i, smooth=0.3, grime=0.25)
        t.speckle(0.15, (70, 70, 72), 0.4)
        t.streaks(0.5, 0.8)
        return t
    t = glass.tile(i, 43020 + i, smooth=0.9, grime=0.0)
    t.f0[:] = 20
    return t


@lru_cache(maxsize=None)
def name_pic():
    p = Pic(22, 1, (0, 0, 0, 0), 43100)
    W, H = p.W, p.H
    text = "GRAYHAVEN   MARKET   HOUSE"
    size = civic.fit(text, SERIF, 42, W - 60)
    p.text(W / 2 + 2, H / 2 + 3, text, SERIF, size, (70, 68, 64), anchor="mm", a=200)
    p.text(W / 2, H / 2 + 1, text, SERIF, size, (204, 200, 190), anchor="mm")
    return p


def name(i):
    t = name_pic().tile(i, 43110 + i, smooth=0.2, grime=0.25)
    t.streaks(0.6, 0.75)
    return t


@lru_cache(maxsize=None)
def date_pic():
    """The panel in the pediment: the city's sheaf and anchor, the year in the stone."""
    p = Pic(4, 2, GRANITE, 43200)
    W, H = p.W, p.H
    p.rect(3, 3, W - 3, H - 3, outline=(112, 110, 104), width=3)
    p.rect(9, 9, W - 9, H - 9, outline=(190, 188, 180), width=1)
    p.text(W / 2, 30, "ERECTED BY THE CITY", SERIF, civic.fit("ERECTED BY THE CITY", SERIF, 18, W - 50), (90, 88, 82), anchor="mm")
    p.text(W / 2, 98, "MDCCCXXVI", SERIF, 22, (90, 88, 82), anchor="mm")
    cx = W / 2
    # the anchor
    p.line([(cx, 46), (cx, 80)], (110, 108, 100), 3)
    p.line([(cx - 9, 50), (cx + 9, 50)], (110, 108, 100), 3)
    p.ellipse(cx - 4, 40, cx + 4, 48, outline=(110, 108, 100), width=2)
    p.line([(cx - 14, 72), (cx - 8, 80), (cx, 82), (cx + 8, 80), (cx + 14, 72)], (110, 108, 100), 3)
    # the sheaves either side
    for sx in (cx - 60, cx + 60):
        for k in range(-3, 4):
            p.line([(sx, 84), (sx + k * 4, 50)], (126, 120, 104), 1.6)
            p.ellipse(sx + k * 4 - 2, 44, sx + k * 4 + 2, 54, fill=(140, 132, 112))
        p.line([(sx - 8, 70), (sx + 8, 70)], (100, 96, 86), 2)
    return p


def date(i):
    t = date_pic().tile(i, 43210 + i, smooth=0.25, grime=0.3)
    t.speckle(0.12, (70, 70, 72), 0.35)
    t.streaks(0.7, 0.75)
    return t


# ---------------------------------------------------------------- the stalls

@lru_cache(maxsize=None)
def board_pic(key):
    text, field, ink, rule = BOARDS[key]
    p = Pic(3, 1, (0, 0, 0, 0), 43300 + len(key) * 7)
    W, H = p.W, p.H
    p.rect(1, 10, W - 1, H - 10, fill=field)
    p.rect(1, 10, W - 1, H - 10, outline=mixc(field, (0, 0, 0), 0.35), width=2)
    if rule:
        p.rect(6, 15, W - 6, H - 15, outline=GOLD, width=1)
    size = civic.fit(text, OLD, 26, W - 30)
    p.text(W / 2 + 1, H / 2 + 2, text, OLD, size, mixc(field, (0, 0, 0), 0.5), anchor="mm")
    p.text(W / 2, H / 2, text, OLD, size, ink, anchor="mm")
    return p


def board(key, i):
    t = board_pic(key).tile(i, 43350 + i + len(key) * 13, smooth=0.35, grime=0.2)
    t.speckle(0.04, (230, 226, 210), 0.5)
    return t


def stall_front(seed, rgb):
    """A stall counter's front: tongue-and-groove boards painted, kicked at the bottom."""
    t = F.plain(seed, rgb, 0.04, 0.35)
    for x in range(0, N, 8):
        area(t, x, 0, x + 1, N, mul=0.7, dh=-0.2)
    area(t, 0, 0, N, 5, mul=1.15, dh=0.2)
    area(t, 0, 56, N, N, mul=0.6)
    t.speckle(0.05, (60, 50, 40), 0.5)
    t.grime(0.3, (50, 40, 30))
    return t


def marble(seed):
    t = F.mat("Marble016", (214, 210, 202), 1.0, seed=seed, smooth=0.75)
    t.grime(0.15)
    return t


def ice_fish(seed, rot=False):
    """Crushed ice on the slab with the catch laid on it: cod, mackerel, a row of shrimp; rotted,
    the ice gone to grey water and the fish to grey, the flies' black."""
    t = F.plain(seed, (214, 222, 226), 0.06, 0.8)
    rng = t.rng
    t.speckle(0.4, (246, 250, 252), 0.6, 0.05)
    t.speckle(0.2, (170, 186, 196), 0.5, -0.05)
    fish = [((150, 150, 140), (226, 226, 216)), ((70, 90, 110), (210, 214, 210)), ((150, 120, 100), (230, 210, 190))]
    for row in range(3):
        top, belly = fish[row]
        y = 10 + row * 18
        for x in range(4, 60, 14):
            if rot:
                top, belly = mixc(top, (90, 96, 80), 0.6), mixc(belly, (120, 120, 100), 0.6)
            area(t, x, y, x + 11, y + 4, col=top, h=0.9, smooth=0.8)
            area(t, x, y + 4, x + 11, y + 6, col=belly, h=0.85, smooth=0.8)
            area(t, x + 11, y + 1, x + 13, y + 5, col=mixc(top, (0, 0, 0), 0.3))
            disc(t, x + 2, y + 2, 0.8, col=(20, 20, 20))
    if rot:
        t.mix(np.clip(P.fbm(rng) * 1.3 + 0.2, 0, 1), (110, 112, 96), 0.6)
        t.speckle(0.03, (10, 10, 10), 0.9, 0.05)
    return t


def oysters(seed):
    t = F.plain(seed, (214, 222, 226), 0.06, 0.8)
    rng = t.rng
    t.speckle(0.4, (246, 250, 252), 0.6, 0.05)
    for x in range(4, 62, 9):
        for y in range(4, 62, 9):
            cx, cy = x + rng.uniform(-1, 1), y + rng.uniform(-1, 1)
            disc(t, cx, cy, 3.8, col=(120, 116, 104), h=0.9)
            disc(t, cx, cy, 2.4, col=(206, 200, 186), h=0.95)
            disc(t, cx + 0.5, cy, 1.0, col=(150, 140, 120))
    area(t, 26, 26, 38, 38, col=(230, 210, 80))
    return t


def cuts(seed):
    """Meat on white trays behind the glass, gone dark at the edges; the price tags stuck in."""
    t = F.plain(seed, (232, 232, 226), 0.03, 0.6)
    rng = t.rng
    for x in range(2, 62, 15):
        for y in (4, 34):
            area(t, x, y, x + 13, y + 26, col=(240, 240, 236), h=0.7)
            for _ in range(3):
                cx, cy = x + rng.uniform(3, 10), y + rng.uniform(5, 20)
                disc(t, cx, cy, rng.uniform(3, 5), col=mixc((150, 40, 40), (60, 40, 30), rng.uniform(0.2, 0.7)), h=0.85)
                disc(t, cx + 1, cy, 1.4, col=(230, 210, 200))
            area(t, x + 2, y + 21, x + 9, y + 25, col=(250, 250, 250))
            area(t, x + 3, y + 22, x + 8, y + 23, col=(200, 40, 40))
    return t


def cheese(seed):
    t = F.plain(seed, (222, 186, 90), 0.06, 0.3)
    t.speckle(0.05, (200, 160, 70), 0.6, -0.05)
    t.grime(0.2, (120, 90, 40))
    return t


def bread_rack(seed):
    """The bakery's rack seen from the aisle: three shelves of loaves, baguettes, rolls, all gone hard."""
    t = F.wood(seed, "dark")
    rng = t.rng
    for row, y in enumerate((6, 26, 46)):
        area(t, 0, y + 14, N, y + 17, col=(60, 40, 26), dh=0.2)
        x = 2
        while x < 60:
            kind = rng.integers(3)
            if kind == 0:
                disc(t, x + 6, y + 9, 6, col=(186, 120, 60), h=0.9)
                area(t, x + 2, y + 5, x + 10, y + 6, col=(220, 170, 110))
                x += 13
            elif kind == 1:
                area(t, x, y + 6, x + 4, y + 14, col=(200, 140, 70), h=0.9)
                x += 5
            else:
                for k in range(3):
                    disc(t, x + 3 + k * 6, y + 11, 3, col=(170, 104, 50), h=0.9)
                x += 19
    t.speckle(0.03, (80, 90, 70), 0.6)
    return t


def fruit(seed, kind):
    """The tilted crates' fruit seen from above: apples and oranges, or greens and roots; rot
    turns it all brown and grey-green."""
    t = F.wood(seed, "pine")
    rng = t.rng
    if kind == "a":
        cols = [(196, 30, 30), (230, 140, 30), (200, 190, 60), (150, 30, 40)]
    elif kind == "b":
        cols = [(70, 130, 50), (110, 160, 60), (190, 110, 50), (120, 60, 110)]
    else:
        cols = [(100, 80, 50), (120, 110, 70), (80, 90, 60), (70, 60, 40)]
    for cx0 in (0, 32):
        c = cols[(cx0 // 32 + rng.integers(2)) % len(cols)]
        c2 = cols[(cx0 // 32 + 2) % len(cols)]
        for y in range(3, 62, 7):
            for x in range(cx0 + 3, cx0 + 31, 7):
                col = c if y < 32 else c2
                disc(t, x + rng.uniform(-1, 1), y + rng.uniform(-1, 1), 3.3, col=mixc(col, (0, 0, 0), rng.uniform(0, 0.25)), h=0.95)
        area(t, cx0, 0, cx0 + 1, N, col=(110, 80, 50), h=1.0)
    area(t, 0, 31, N, 33, col=(110, 80, 50), h=1.0)
    if kind == "rot":
        t.speckle(0.08, (180, 190, 170), 0.7)
        t.speckle(0.03, (10, 10, 10), 0.8)
    return t


def crate_side(seed, label=True):
    """A produce crate's end: the slats, the grower's label, a fruit on it."""
    t = F.wood(seed, "pine")
    rng = t.rng
    for y in (0, 21, 42):
        area(t, 0, y, N, y + 2, mul=0.6, dh=-0.2)
    if label:
        col = [(40, 80, 140), (180, 40, 30), (30, 110, 60)][rng.integers(3)]
        area(t, 10, 24, 54, 40, col=col, smooth=0.5)
        disc(t, 22, 32, 5, col=(220, 60, 40))
        area(t, 30, 28, 50, 31, col=(240, 230, 200))
        area(t, 30, 34, 46, 36, col=(240, 230, 200))
    t.grime(0.2)
    return t


def burlap(seed, top=None):
    t = F.plain(seed, (170, 140, 96), 0.05, 0.15)
    for k in range(0, N, 2):
        area(t, k, 0, k + 1, N, mul=0.9)
        area(t, 0, k, N, k + 1, mul=0.92)
    if top:
        disc(t, 32, 32, 22, col=top, h=0.9)
        t.speckle(0.2, mixc(top, (0, 0, 0), 0.3), 0.6)
    t.grime(0.2)
    return t


def jars(seed):
    t = F.plain(seed, (230, 230, 226), 0.03, 0.9)
    rng = t.rng
    for x in range(4, 60, 12):
        col = [(220, 60, 60), (240, 200, 60), (90, 160, 220), (240, 140, 180), (120, 200, 90)][rng.integers(5)]
        area(t, x, 16, x + 10, 56, col=mixc(col, (255, 255, 255), 0.2), h=0.9)
        t.speckle(0.0, col)
        area(t, x + 1, 10, x + 9, 16, col=(200, 190, 170))
        for _ in range(16):
            disc(t, x + rng.uniform(2, 8), rng.uniform(20, 54), 1.4, col=mixc(col, (255, 255, 255), rng.uniform(0, 0.5)))
    return t


def flowers(seed):
    t = F.plain(seed, (60, 80, 40), 0.1, 0.2)
    rng = t.rng
    for _ in range(40):
        col = [(200, 60, 90), (230, 200, 70), (240, 240, 230), (170, 60, 140), (220, 110, 50)][rng.integers(5)]
        col = mixc(col, (110, 80, 50), 0.55)
        disc(t, rng.uniform(4, 60), rng.uniform(4, 60), rng.uniform(2.5, 4.5), col=col, h=0.95)
    t.speckle(0.1, (90, 70, 40), 0.6)
    return t


def carcass(seed):
    t = F.plain(seed, (150, 70, 66), 0.12, 0.6)
    t.mix(np.clip(P.fbm(t.rng) * 1.5, 0, 1), (230, 220, 200), 0.6)
    t.mix(np.clip(P.fbm(t.rng) * 1.3 - 0.1, 0, 1), (90, 100, 70), 0.5)
    t.speckle(0.03, (10, 10, 10), 0.8)
    return t


def end_grain(seed):
    t = F.wood(seed, "maple")
    for k in range(0, N, 8):
        area(t, k, 0, k + 1, N, mul=0.75)
        area(t, 0, k, N, k + 1, mul=0.8)
    disc(t, 30, 28, 14, mul=0.82, soft=6)
    t.grime(0.3, (90, 30, 26))
    return t


@lru_cache(maxsize=None)
def chalk_pic():
    """Chalk prices on the stall slates, four of them."""
    p = Pic(4, 1, (0, 0, 0, 0), 43400)
    lines = ["COD 1.89|HADDOCK 2.49|SHRIMP 4.99", "CHUCK 1.79|CHOPS 1.49|GROUND 1.09",
             "APPLES .39|ORANGES 6/1|PEARS .59", "RYE 1.10|ROLLS .25|BAGELS .30"]
    for k, s in enumerate(lines):
        x0 = k * N
        p.rect(x0 + 4, 4, x0 + 60, 60, fill=(36, 40, 38))
        p.rect(x0 + 4, 4, x0 + 60, 60, outline=(120, 80, 50), width=3)
        p.scrawl(x0 + 8, 12, s, SPRAY, 6.2, (230, 230, 220), jitter=0.2, spacing=1.5)
    return p


def chalk(i):
    return chalk_pic().tile(i, 43410 + i, smooth=0.2, grime=0.1)


# ---------------------------------------------------------------- the lanes

def cobble(seed, patched=False, moss=False):
    """Granite setts laid in courses, four to a block across, the joints full of grit."""
    t = F.plain(seed, (118, 116, 112), 0.04, 0.3)
    rng = t.rng
    area(t, 0, 0, N, N, col=(70, 66, 60), h=0.3)
    for row in range(5):
        y0 = row * 13
        off = 0 if row % 2 == 0 else 8
        for x0 in range(-off, N, 16):
            col = mixc((134, 132, 126), (96, 92, 88), rng.uniform(0, 1))
            col = mixc(col, (150, 120, 100), rng.uniform(0, 0.2))
            area(t, x0 + 1, y0 + 1, x0 + 15, y0 + 12, col=col, h=0.8 + rng.uniform(0, 0.1), smooth=0.45)
            area(t, x0 + 2, y0 + 1, x0 + 14, y0 + 3, mul=1.12)
    t.speckle(0.12, (60, 60, 62), 0.3)
    if patched:
        x, y = rng.integers(8, 30), rng.integers(8, 30)
        area(t, x, y, x + 28, y + 22, col=(54, 52, 50), h=0.5, smooth=0.2)
        t.speckle(0.05, (90, 88, 84), 0.4)
    if moss:
        m = np.zeros((N, N), np.float32)
        for row in range(6):
            m[max(0, row * 13 - 1):row * 13 + 1, :] = 1
        m[:, ::16] = 1
        t.mix(m * np.clip(P.fbm(rng) + 0.6, 0, 1), (70, 92, 48), 0.8)
    t.grime(0.25, (40, 36, 30))
    return t


def flags(seed, cracked=False):
    """The hall's floor: big slabs of bluestone, worn hollow down the aisle."""
    t = F.plain(seed, (104, 110, 116), 0.06, 0.4)
    rng = t.rng
    area(t, 0, 0, N, 1, col=(60, 62, 64), dh=-0.2)
    area(t, 0, 0, 1, N, col=(60, 62, 64), dh=-0.2)
    area(t, 0, 32, 40, 33, col=(60, 62, 64), dh=-0.2)
    area(t, 40, 0, 41, 32, col=(60, 62, 64), dh=-0.2)
    area(t, 22, 33, 23, N, col=(60, 62, 64), dh=-0.2)
    for (x0, y0, x1, y1) in ((1, 1, 40, 32), (41, 1, N, 32), (1, 33, 22, N), (23, 33, N, N)):
        area(t, x0, y0, x1, y1, mul=rng.uniform(0.88, 1.1))
    t.speckle(0.06, (140, 146, 150), 0.3)
    if cracked:
        x, y = 10, rng.integers(10, 50)
        for _ in range(40):
            area(t, x, y, x + 2, y + 1, col=(40, 40, 42), dh=-0.3)
            x += 1
            y += int(rng.integers(-1, 2))
    t.grime(0.3, (50, 46, 40))
    return t


def canvas(seed, rgb, stripes=True):
    t = F.plain(seed, rgb, 0.05, 0.15)
    if stripes:
        for x in range(0, N, 16):
            area(t, x, 0, x + 8, N, col=(236, 230, 214))
    t.grime(0.3, (60, 56, 48))
    t.speckle(0.02, (40, 40, 30), 0.5)
    return t


def wheel(seed):
    t = F.plain(seed, (60, 40, 30), 0.05, 0.3)
    area(t, 0, 0, N, N, alpha=0)
    disc(t, 32, 32, 30, col=(70, 50, 34), alpha=255, ring=5)
    disc(t, 32, 32, 30, col=(40, 40, 40), alpha=255, ring=2)
    disc(t, 32, 32, 5, col=(60, 44, 30), alpha=255)
    for k in range(8):
        a = k * np.pi / 4
        for r in range(5, 26):
            x, y = int(32 + np.cos(a) * r), int(32 + np.sin(a) * r)
            area(t, x - 1, y - 1, x + 2, y + 2, col=(80, 58, 40), alpha=255)
    return t


def bulb(seed, on):
    t = F.plain(seed, (240, 220, 170) if on else (180, 176, 160), 0.03, 0.9)
    if on:
        t.emit[:] = 1.0
    return t


# ---------------------------------------------------------------- the barter post

@lru_cache(maxsize=None)
def prices_pic():
    """The barter rates the stall keepers chalked up in the rotunda."""
    p = Pic(3, 2, (34, 38, 36), 43500)
    W, H = p.W, p.H
    p.rect(0, 0, W, H, outline=(120, 80, 50), width=5)
    p.scrawl(14, 10, "TRADE RATES", SPRAY, 18, (240, 240, 230), jitter=0.2)
    s = "1 CAN = 5 ROUNDS|WATER JUG = 1 CAN|BATTERIES 4 = 1 CAN|BANDAGES = 2 CANS|NO CASH  NO IOUs"
    p.scrawl(14, 36, s, SPRAY, 11, (230, 230, 216), jitter=0.25, spacing=1.25)
    p.scrawl(130, 100, "RATS - NO", SPRAY, 9, (220, 120, 120), jitter=0.3)
    return p


def prices(i):
    return prices_pic().tile(i, 43510 + i, smooth=0.15, grime=0.15)


@lru_cache(maxsize=None)
def banner_pic():
    p = Pic(6, 1, (232, 228, 214), 43600)
    W = p.W
    p.scrawl(14, 6, "MARKET OPEN - TRADE ONLY", SPRAY, civic.fit("MARKET OPEN - TRADE ONLY", SPRAY, 30, W - 40), (200, 24, 24), 0.25)
    p.scrawl(60, 40, "WATER  AMMO  MEDS  - KNOCK AT SIDE DOOR", SPRAY,
             civic.fit("WATER  AMMO  MEDS  - KNOCK AT SIDE DOOR", SPRAY, 15, W - 120), (20, 20, 20), 0.3)
    return p


def banner(i):
    t = banner_pic().tile(i, 43610 + i, smooth=0.1, grime=0.3)
    t.c *= (0.9 + 0.1 * np.cos(np.arange(N)[None, :] / N * 2 * np.pi * 2))[..., None]
    return t


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 43700 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 16, W - 1, H - 16, fill=field)
    font = STENCIL if field[0] > 150 else OLD
    p.text(W / 2, H / 2, text, font, civic.fit(text, font, 15, W - 14), ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 43750 + i + len(key) * 11, smooth=0.5, grime=0.2)


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 43800 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (210, 24, 30) if key != "rats" else (30, 30, 30), jitter=0.4, a=235, angle=-2)
    return p


def spray(key, i):
    t = spray_pic(key).tile(i, 43810 + i + len(key) * 5, smooth=0.3, grime=0.0)
    lum = t.alpha > 20
    for _ in range(4):
        x, y = int(t.rng.integers(4, 60)), int(t.rng.integers(30, 50))
        if lum[y, x]:
            drips(t, x, x + 1, y, 1, (4, 12), col=(180, 20, 20) if key != "rats" else (30, 30, 30))
            t.alpha[y:y + 12, x:x + 1] = 230
    return t


def chicken_wire(seed):
    t = F.plain(seed, (150, 150, 146), 0.05, 0.8)
    t.alpha[:] = 0
    for k in range(-N, N, 8):
        for x in range(N):
            for y in (x + k, -x + k + N):
                if 0 <= y < N:
                    area(t, x, y, x + 1, y + 1, col=(170, 170, 166), alpha=255)
    t.f0[:] = 60
    return t


def textures():
    T = {}
    T["om_granite"] = lambda: granite(43900)
    T["om_granite_b"] = lambda: granite(43901, worn=True)
    T["om_granite_moss"] = lambda: granite(43902, moss=True)
    T["om_sash"] = lambda: sash(43903)
    T["om_sash_broken"] = lambda: sash(43904, broken=True)
    T["om_sash_lit"] = lambda: sash(43905, lit=True)
    for i in range(10):
        T[f"om_fan_{i}"] = lambda i=i: fan(i, "mask")
        T[f"om_fang_{i}"] = lambda i=i: fan(i, "glass")
    for i in range(22):
        T[f"om_name_{i}"] = lambda i=i: name(i)
    for i in range(8):
        T[f"om_date_{i}"] = lambda i=i: date(i)
    for key in BOARDS:
        for i in range(3):
            T[f"om_board_{key}_{i}"] = lambda key=key, i=i: board(key, i)
    T["om_front_green"] = lambda: stall_front(43910, (52, 86, 62))
    T["om_front_red"] = lambda: stall_front(43911, (130, 40, 34))
    T["om_front_cream"] = lambda: stall_front(43912, (214, 204, 176))
    T["om_front_blue"] = lambda: stall_front(43913, (44, 70, 100))
    T["om_marble"] = lambda: marble(43914)
    T["om_ice_fish"] = lambda: ice_fish(43915)
    T["om_ice_rot"] = lambda: ice_fish(43916, rot=True)
    T["om_oysters"] = lambda: oysters(43917)
    T["om_cuts"] = lambda: cuts(43918)
    T["om_cheese"] = lambda: cheese(43919)
    T["om_bread"] = lambda: bread_rack(43920)
    T["om_fruit_a"] = lambda: fruit(43921, "a")
    T["om_fruit_b"] = lambda: fruit(43922, "b")
    T["om_fruit_rot"] = lambda: fruit(43923, "rot")
    T["om_crate"] = lambda: crate_side(43924)
    T["om_crate_plain"] = lambda: crate_side(43925, label=False)
    T["om_burlap"] = lambda: burlap(43926)
    T["om_spice_red"] = lambda: burlap(43927, (170, 60, 30))
    T["om_spice_gold"] = lambda: burlap(43928, (210, 160, 40))
    T["om_beans"] = lambda: burlap(43929, (70, 44, 30))
    T["om_jars"] = lambda: jars(43930)
    T["om_flowers"] = lambda: flowers(43931)
    T["om_carcass"] = lambda: carcass(43932)
    T["om_endgrain"] = lambda: end_grain(43933)
    for i in range(4):
        T[f"om_chalk_{i}"] = lambda i=i: chalk(i)
    T["om_cobble"] = lambda: cobble(43934)
    T["om_cobble_b"] = lambda: cobble(43935, patched=True)
    T["om_cobble_moss"] = lambda: cobble(43936, moss=True)
    T["om_flags"] = lambda: flags(43937)
    T["om_flags_b"] = lambda: flags(43938, cracked=True)
    T["om_canvas_red"] = lambda: canvas(43939, (170, 40, 36))
    T["om_canvas_green"] = lambda: canvas(43940, (40, 100, 60))
    T["om_canvas_cream"] = lambda: canvas(43941, (226, 216, 190), stripes=False)
    T["om_wheel"] = lambda: wheel(43942)
    T["om_bulb"] = lambda: bulb(43943, False)
    T["om_bulb_on"] = lambda: bulb(43944, True)
    T["om_wire"] = lambda: chicken_wire(43945)
    for i in range(6):
        T[f"om_prices_{i}"] = lambda i=i: prices(i)
        T[f"om_banner_{i}"] = lambda i=i: banner(i)
    for key in SIGNS:
        for i in range(2):
            T[f"om_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"om_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        if boxes and rotate == "axis" and P.drawn_along(boxes) == "x":
            boxes = P.along_z(boxes)  # RULE: axis models run along z as drawn
            if "shape" in extra:
                extra["shape"] = [[b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]] for b in extra["shape"]]
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

    black, chrome, steel, glass = "home_plastic_black", "home_chrome", "painted_steel", "cc_glass"
    oak, dark, pine = "cv_oak", "om_front_cream", "om_crate_plain"

    def face(front, side, up=None):
        return {"north": front, "side": side, "up": up or side, "down": side}

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    # ---- the walls
    for n in ("om_granite", "om_granite_b", "om_granite_moss"):
        add(n, None, "Granite", "STONE", rotate=None, tex={"all": n})
    for n, light in (("om_sash", None), ("om_sash_broken", None), ("om_sash_lit", 5)):
        add(n, [[0, 0, 7, 16, 16, 9, {"north": n, "south": n, "side": "om_front_cream", "up": "om_front_cream", "down": "om_front_cream"}]],
            "Window", "GLASS", render="translucent", shape=[[0, 0, 7, 16, 16, 9]], **({"light": light} if light else {}))
    for i in range(10):
        m, g = f"om_fan_{i}", f"om_fang_{i}"
        add(m, [[0, 0, 0, 16, 16, 7, {"north": m, "south": m, "side": "om_granite", "up": "om_granite", "down": "om_granite"}],
                [0, 0, 7, 16, 16, 9, {"north": g, "south": g, "side": "om_front_cream", "up": "om_front_cream", "down": "om_front_cream"}],
                [0, 0, 9, 16, 16, 16, {"north": m, "south": m, "side": "om_granite", "up": "om_granite", "down": "om_granite"}]],
            "Fanlight", "GLASS", render="translucent", shape=[[0, 0, 0, 16, 16, 16]])
    for i in range(22):
        add(f"om_name_{i}", plate(f"om_name_{i}", 14.6), "Carved Letters", "STONE", decor=True)
    for i in range(8):
        add(f"om_date_{i}", [[0, 0, 0, 16, 16, 16, {"north": f"om_date_{i}", "south": "om_granite", "side": "om_granite",
                                                    "up": "om_granite", "down": "om_granite"}]], "Carved Stone", "STONE")

    # the pediment's raking cornice, one in two over a pair of blocks (the bank's, in granite)
    for n in ("l_0", "l_1", "r_0", "r_1"):
        add(f"om_rake_{n}", [bx[:6] + ["om_granite"] for bx in RAKES[n]], "Raking Cornice", "STONE")

    # ---- the stalls: posts and beams, the boards hung under them on two chains
    add("om_post", [[6, 0, 6, 10, 16, 10, "cv_oak"]], "Stall Post", "WOOD", rotate=None, shape=[[6, 0, 6, 10, 16, 10]])
    add("om_beam", [[6, 11, 0, 10, 16, 16, "cv_oak"]], "Stall Beam", "WOOD", rotate="axis", shape=[[6, 11, 0, 10, 16, 16]])
    for key in BOARDS:
        for i in range(3):
            n = f"om_board_{key}_{i}"
            add(n, [[0, 1, 7, 16, 13, 9, {"north": n, "south": "cv_oak", "side": "cv_oak", "up": "cv_oak", "down": "cv_oak"}],
                    [3, 13, 7.6, 4, 16, 8.4, chrome], [12, 13, 7.6, 13, 16, 8.4, chrome]], "Stall Board", "WOOD",
                decor=True, shape=[[0, 1, 7, 16, 13, 9]])
    # the counters: the painted front to the aisle (north), the top what the stall sold
    def counter(n, front, top_boxes, title, loot, render=None, top_h=14):
        boxes = [[0, 0, 1, 16, top_h, 16, face(front, front, "om_marble")]] + top_boxes
        extra = {"render": render} if render else {}
        add(n, boxes, title, "WOOD", loot=loot, shape=[[0, 0, 1, 16, 16, 16]], **extra)

    def ice(tex):
        return [[0, 12, 1, 16, 13, 16, face("om_front_blue", "om_front_blue", tex)],
                [0, 13, 6, 16, 14, 16, face("om_front_blue", "om_front_blue", tex)],
                [0, 14, 11, 16, 15, 16, face("om_front_blue", "om_front_blue", tex)]]
    add("om_fish", [[0, 0, 1, 16, 12, 16, face("om_front_blue", "om_front_blue")]] + ice("om_ice_fish"), "Fish Counter", "STONE",
        loot="food", shape=[[0, 0, 1, 16, 15, 16]])
    add("om_fish_rot", [[0, 0, 1, 16, 12, 16, face("om_front_blue", "om_front_blue")]] + ice("om_ice_rot"), "Fish Counter", "STONE",
        loot="scraps", shape=[[0, 0, 1, 16, 15, 16]])
    add("om_oyster", [[0, 0, 1, 16, 12, 16, face("om_front_blue", "om_front_blue")],
                      [0, 12, 1, 16, 13, 16, face("om_front_blue", "om_front_blue", "om_oysters")],
                      [0, 13, 0, 16, 14, 4, face("om_marble", "om_marble", "om_marble")]], "Oyster Bar", "STONE",
        loot="food", shape=[[0, 0, 0, 16, 14, 16]])
    add("om_meat_case", [[0, 0, 1, 16, 9, 16, face("om_front_red", "om_front_red", "om_marble")],
                         [1, 9, 2, 15, 10, 15, face("om_cuts", "om_cuts", "om_cuts")],
                         [0, 9, 1, 16, 16, 16, glass], [0, 15.5, 1, 16, 16, 16, steel]], "Meat Case", "GLASS",
        loot="home_fridge", render="translucent", shape=[[0, 0, 1, 16, 16, 16]])
    counter("om_cheese", "om_front_cream", [[2, 14, 3, 8, 16, 9, "om_cheese"], [9, 14, 4, 14, 15.5, 9, "om_cheese"],
                                           [4, 14, 10, 12, 15, 15, "om_cheese"]], "Cheese Counter", "food", top_h=14)
    add("om_bread", [[0, 0, 4, 16, 16, 16, face("om_bread", "cv_oak", "cv_oak")]], "Bread Rack", "WOOD", loot="food",
        shape=[[0, 0, 4, 16, 16, 16]])
    for n, tex, loot in (("om_produce_a", "om_fruit_a", "grocery"), ("om_produce_b", "om_fruit_b", "grocery"),
                         ("om_produce_rot", "om_fruit_rot", "scraps")):
        add(n, [[0, 0, 0, 16, 7, 16, face("om_crate", "om_crate_plain")],
                [0, 7, 0, 16, 9, 6, face("om_crate", "om_crate_plain", tex)],
                [0, 7, 6, 16, 11, 11, face("om_crate", "om_crate_plain", tex)],
                [0, 7, 11, 16, 13, 16, face("om_crate", "om_crate_plain", tex)]], "Produce Stand", "WOOD", loot=loot,
            shape=[[0, 0, 0, 16, 13, 16]])
    add("om_spice", [[1, 0, 1, 7, 10, 7, face("om_burlap", "om_burlap", "om_spice_red")],
                     [9, 0, 1, 15, 11, 7, face("om_burlap", "om_burlap", "om_spice_gold")],
                     [1, 0, 9, 7, 12, 15, face("om_burlap", "om_burlap", "om_beans")],
                     [9, 0, 9, 15, 10, 15, face("om_burlap", "om_burlap", "om_spice_red")]], "Spice Sacks", "WOOL",
        loot="kitchen", shape=[[1, 0, 1, 15, 12, 15]])
    counter("om_candy", "om_front_cream", [[1, 14, 4, 5, 16, 8, face("om_jars", "om_jars", glass)],
                                          [6, 14, 4, 10, 16, 8, face("om_jars", "om_jars", glass)],
                                          [11, 14, 4, 15, 16, 8, face("om_jars", "om_jars", glass)]], "Candy Counter", "theatre_candy",
            render="translucent", top_h=14)
    counter("om_coffee", "om_front_red", [[2, 14, 6, 6, 16, 10, face("om_front_red", "home_plastic_black")],
                                         [8, 14, 4, 14, 16, 12, face("om_beans", "om_burlap", "om_beans")]], "Coffee Counter", "drinks",
            top_h=14)
    add("om_flowers", [[1, 0, 1, 7, 6, 7, chrome], [9, 0, 1, 15, 7, 7, chrome], [1, 0, 9, 7, 5, 15, chrome], [9, 0, 9, 15, 6, 15, chrome],
                       [0, 6, 0, 8, 10, 8, face("om_flowers", "om_flowers", "om_flowers")],
                       [8, 7, 0, 16, 11, 8, face("om_flowers", "om_flowers", "om_flowers")],
                       [0, 5, 8, 8, 9, 16, face("om_flowers", "om_flowers", "om_flowers")],
                       [8, 6, 8, 16, 10, 16, face("om_flowers", "om_flowers", "om_flowers")]], "Flower Buckets", "GRASS",
        rotate=None, shape=[[0, 0, 0, 16, 11, 16]])
    counter("om_counter", "om_front_green", [], "Stall Counter", "storage", top_h=14)
    add("om_block", [[1, 0, 1, 15, 3, 15, "cv_oak"], [2, 3, 2, 14, 6, 14, "cv_oak"],
                     [1, 6, 1, 15, 14, 15, {"side": "om_endgrain", "up": "om_endgrain", "down": "om_endgrain"}]], "Butcher Block", "WOOD",
        rotate=None, shape=[[1, 0, 1, 15, 14, 15]])
    # the hooks rail under the ceiling, a side of beef on each hook (the block hangs from the one above)
    add("om_hooks", [[0, 15, 7, 16, 16, 9, steel], [7.5, 12, 7.5, 8.5, 15, 8.5, chrome],
                     [5, 0, 5, 11, 12, 11, "om_carcass"], [6, -6, 6, 10, 0, 10, "om_carcass"]], "Meat Hooks", "METAL",
        shape=[[5, 0, 5, 11, 16, 11]])
    add("om_hooks_empty", [[0, 15, 7, 16, 16, 9, steel], [3.5, 11, 7.5, 4.5, 15, 8.5, chrome], [11.5, 12, 7.5, 12.5, 15, 8.5, chrome]],
        "Meat Hooks", "METAL", decor=True, shape=[[0, 11, 7, 16, 16, 9]])
    add("om_scale", [[7.5, 10, 7.5, 8.5, 16, 8.5, chrome], [4, 4, 7, 12, 10, 9, face("fit_gauges", chrome)],
                     [7.5, 2, 7.5, 8.5, 4, 8.5, chrome], [3, 0, 3, 13, 2, 13, chrome]], "Hanging Scale", "METAL",
        decor=True, shape=[[3, 0, 3, 13, 16, 13]])
    add("om_crates", [[0, 0, 0, 16, 8, 16, face("om_crate", "om_crate")], [1, 8, 1, 15, 16, 15, face("om_crate", "om_crate", "om_fruit_rot")]],
        "Produce Crates", "WOOD", loot="grocery")
    add("om_sacks", [[0, 0, 0, 16, 6, 16, "om_burlap"], [1, 6, 1, 15, 11, 15, "om_burlap"], [3, 11, 2, 13, 14, 9, "om_burlap"]],
        "Sacks", "WOOL", loot="basement_food", shape=[[0, 0, 0, 16, 14, 16]])
    add("om_pickles", [[2, 0, 2, 14, 14, 14, {"side": "cv_oak", "up": "om_fruit_b", "down": "cv_oak"}],
                       [1.6, 3, 1.6, 14.4, 4, 14.4, steel], [1.6, 10, 1.6, 14.4, 11, 14.4, steel]], "Pickle Barrel", "WOOD",
        rotate=None, loot="home_pantry", shape=[[2, 0, 2, 14, 14, 14]])
    for i in range(4):
        add(f"om_chalk_{i}", [[1, 0, 14.5, 15, 15, 16, face(f"om_chalk_{i}", "cv_oak")]], "Price Slate", "STONE", decor=True)

    # ---- the lanes
    for n in ("om_cobble", "om_cobble_b", "om_cobble_moss", "om_flags", "om_flags_b"):
        add(n, None, "Granite Setts" if "cobble" in n else "Bluestone", "STONE", rotate=None,
            tex={"up": n, "side": "om_granite", "down": "om_granite"})
    # the pushcarts: two spoked wheels, the box, the handles out the back (south)
    def cart(n, top, loot, tipped=False):
        boxes = [[1, 5, 2, 15, 11, 14, face(pine, pine, pine)], [0, 0, 4, 1, 10, 13, {"all": "om_wheel"}],
                 [15, 0, 4, 16, 10, 13, {"all": "om_wheel"}], [3, 9, 14, 4, 10, 16, oak], [12, 9, 14, 13, 10, 16, oak],
                 [3, 0, 12, 4, 5, 13, oak], [12, 0, 12, 13, 5, 13, oak]]
        if top:
            boxes.append([2, 11, 3, 14, 13, 13, face(pine, pine, top)])
        add(n, boxes, "Pushcart", "WOOD", loot=loot, shape=[[0, 0, 2, 16, 13, 16]])
    cart("om_cart_fruit", "om_fruit_a", "grocery")
    cart("om_cart_flowers", "om_flowers", None)
    cart("om_cart_empty", None, "scraps")
    add("om_cart_tipped", [[1, 0, 2, 15, 6, 14, face(pine, pine, pine)], [0, 6, 2, 16, 7, 11, {"all": "om_wheel"}],
                           [2, 0, 14, 14, 1, 16, "om_fruit_rot"], [12, 0, 0, 13, 1, 3, oak]], "Pushcart", "WOOD", decor=True,
        shape=[[0, 0, 2, 16, 7, 14]])
    add("om_umb_pole", [[7, 0, 7, 9, 16, 9, chrome], [5, 0, 5, 11, 1, 11, black]], "Umbrella Pole", "METAL", rotate=None,
        shape=[[7, 0, 7, 9, 16, 9]])
    for c in ("red", "green", "cream"):
        tex = f"om_canvas_{c}"
        add(f"om_umb_{c}", [[7, 0, 7, 9, 10, 9, chrome], [-12, 8, -12, 28, 10, 28, tex], [-6, 10, -6, 22, 12, 22, tex],
                            [2, 12, 2, 14, 13, 14, tex], [7, 13, 7, 9, 15, 9, chrome]], "Market Umbrella", "WOOL", rotate=None,
            decor=True, shape=[[7, 0, 7, 9, 10, 9]])
    # the strings of bulbs over the lanes, the wire along z, a bulb every half block
    for n, on in (("om_lights", False), ("om_lights_on", True)):
        bt = "om_bulb_on" if on else "om_bulb"
        add(n, [[7.6, 14.6, 0, 8.4, 15.2, 16, black], [7.5, 13.6, 3.5, 8.5, 14.6, 4.5, black], [7, 11.8, 3.2, 9, 13.6, 4.8, bt],
                [7.5, 13.6, 11.5, 8.5, 14.6, 12.5, black], [7, 11.8, 11.2, 9, 13.6, 12.8, bt]], "String Lights", "GLASS",
            rotate="axis", decor=True, **({"light": 7} if on else {}))
    add("om_planter", [[0, 0, 0, 16, 10, 16, "om_granite"], [1, 10, 1, 15, 11, 15, "om_flowers"]], "Granite Planter", "STONE",
        rotate=None, shape=[[0, 0, 0, 16, 11, 16]])
    add("om_bollard", [[5, 0, 5, 11, 12, 11, "om_granite"], [6, 12, 6, 10, 14, 10, "om_granite"]], "Granite Bollard", "STONE",
        rotate=None, shape=[[5, 0, 5, 11, 16, 11]])

    # ---- the barter post: a counter with chicken wire to the ceiling, the slot under it
    add("om_barter", [[0, 0, 1, 16, 12, 16, face("om_front_green", "om_front_green", "om_marble")],
                      [0, 12, 7.5, 16, 16, 8.5, {"all": "om_wire"}], [0, 12, 7, 16, 13, 9, steel]], "Barter Counter", "WOOD",
        loot="food", render="cutout", shape=[[0, 0, 1, 16, 16, 16]])
    add("om_wire_wall", [[7.5, 0, 0, 8.5, 16, 16, {"all": "om_wire"}]], "Chicken Wire", "METAL", rotate="axis", render="cutout",
        shape=[[7.5, 0, 0, 8.5, 16, 16]])
    for i in range(6):
        add(f"om_prices_{i}", plate(f"om_prices_{i}", 15.3), "Barter Board", "STONE", decor=True)
        add(f"om_banner_{i}", plate(f"om_banner_{i}", 15.6), "Banner", "WOOL", decor=True)
    for key in SIGNS:
        for i in range(2):
            add(f"om_sgn_{key}_{i}", plate(f"om_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"om_spray_{key}_{i}", plate(f"om_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("om_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} market blocks in {path}")


if __name__ == "__main__":
    main()
