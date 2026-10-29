"""Grayhaven City Park and the Grayhaven Museum: the mu_ kit (world/county/CityPark.java,
Museum.java, MuseumHalls.java).

No photos of either in the library yet (city/museum-*, city/park-* to be queued): this follows
the American city museums of the Beaux-Arts years as they stood in the eighties, and the big
Olmsted parks round them. The museum in pale limestone on its rusticated base, the portico of
six columns with the name in the frieze, the copper dome over the rotunda where the T. rex
stands; natural history in the west wing (the dioramas, the gems, the whale hung in the ocean
hall under the lantern), art and the ancient world in the east. The park behind it: the lake
with its island and footbridge, the boathouse, the green iron bandstand, the glass palm house.
The last weeks left their own: the curators packing the collection into crates on the dock, the
gem cases smashed, the families camped round the bandstand, the trench by the lake.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- building: mu_lime, mu_lime_b, mu_rustic, mu_rose_0..24 (the rotunda's star, five by five,
  tile i = row i / 5, column i % 5, row 0 north);
- plates: mu_name_museum_i (one row), mu_name_park_i, mu_sgn_KEY_0..1 (two by one),
  mu_vbanner_KEY_0..2 (one by three, row 0 the top), mu_bd_KEY_0..11 (diorama backdrops four by
  three), mu_art_KEY_i (paintings, see ART), mu_hiero_0..7 (four by two), mu_plan_0..1,
  mu_parkmap_0..5 (three by two), mu_board_KEY_0..2 (painted boards three by one), mu_label;
- the dioramas: mu_dio_glass (axis), mu_dio_ground, mu_dio_snow, mu_dio_rock; the animals
  (facing: the way they look) mu_bear_lo/hi, mu_deer_lo/hi, mu_wolf, mu_bison_0/1 (0 the head end),
  mu_fox, mu_eagle, mu_owl;
- cases: mu_vit_KEY (one-block vitrines: gems, minerals, shells, birds, pots, jars, smashed),
  mu_table_KEY (flat cases: fossils, coins, jewels, smashed);
- the skeletons: mu_rex_X_Y_Z, mu_whale_X_Y_Z (cells of one model; CELLS below lists them for the
  Java), mu_cable;
- the ancient world: mu_sarc_0/1 (0 the head end), mu_coffin_lo/hi, mu_canopic, mu_amphora,
  mu_urn, mu_armour_lo/hi, mu_swords, mu_shield, mu_bust, mu_plinth, mu_dino_base;
- furniture: mu_stanchion (axis), mu_ottoman (axis), mu_crate, mu_crate_open, mu_crate_flat;
- the park: mu_gravel, mu_gravel_b, mu_boards, mu_rail (axis), mu_bs_post, mu_music_stand,
  mu_gh_glass, mu_gh_glass_broken, mu_gh_post, mu_palm_trunk, mu_palm_crown, mu_fern, mu_bench,
  mu_icecream, mu_parasol, mu_boat_0/1, mu_lifering, mu_oars.

    python3 tools/city/museum.py         rewrites the mu_ entries in blocks.json
    python3 tools/city/museum.py cells   prints the skeletons' cells for Museum.java

photo_textures.py draws the textures (museum.register()).
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
from furniture import area, disc, stain  # noqa: E402
from school import Pic, HAND  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
SERIF = "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"
SERIF_R = "/usr/share/fonts/truetype/crosextra/Caladea-Regular.ttf"
STENCIL = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
MAROON = (96, 28, 34)
CREAM = (236, 228, 206)
GOLD = (196, 160, 84)
LIME = (206, 196, 170)

# the museum's signs, two by one: text, field, ink
SIGNS = {
    "mammals": ("HALL OF MAMMALS", MAROON, CREAM),
    "minerals": ("GEMS & MINERALS", MAROON, CREAM),
    "ocean": ("OCEAN HALL", MAROON, CREAM),
    "birds": ("BIRDS OF THE WORLD", MAROON, CREAM),
    "dinosaurs": ("HALL OF DINOSAURS", MAROON, CREAM),
    "painting": ("EUROPEAN PAINTING", MAROON, CREAM),
    "american": ("AMERICAN ART", MAROON, CREAM),
    "ancient": ("THE ANCIENT WORLD", MAROON, CREAM),
    "egypt": ("EGYPT", MAROON, CREAM),
    "arms": ("ARMS & ARMOR", MAROON, CREAM),
    "admissions": ("ADMISSIONS", MAROON, CREAM),
    "store": ("MUSEUM STORE", MAROON, CREAM),
    "cafe": ("CAFE", MAROON, CREAM),
    "coat": ("COAT CHECK", MAROON, CREAM),
    "restrooms": ("RESTROOMS", MAROON, CREAM),
    "staff": ("STAFF ONLY", (120, 24, 28), (255, 255, 255)),
    "conservation": ("CONSERVATION", MAROON, CREAM),
    "boats": ("BOAT RENTAL", (34, 70, 52), CREAM),
    "palms": ("PALM HOUSE", (34, 70, 52), CREAM),
    "bandstand": ("BANDSTAND", (34, 70, 52), CREAM),
    "shipping": ("SHIPPING & RECEIVING", (60, 60, 64), (250, 250, 240)),
}
NAMES = {
    "museum": ("GRAYHAVEN MUSEUM OF NATURAL HISTORY AND ART", SERIF, 28),
    "park": ("GRAYHAVEN CITY PARK", SERIF, 10),
}
# the banners down the front between the columns: title, subtitle, field, ink
BANNERS = {
    "rex": ("TYRANT KING", "TYRANNOSAURUS REX", (150, 34, 26), (250, 240, 220)),
    "nile": ("THE NILE", "TREASURES OF ANCIENT EGYPT", (20, 50, 110), (236, 196, 90)),
    "light": ("LIGHT", "MASTERS OF THE SEA COAST", (30, 80, 70), (246, 240, 220)),
}
# diorama backdrops, four by three
BACKDROPS = ("forest", "prairie", "winter", "marsh")
# paintings: cols, rows
ART = {
    "portrait": (2, 3),
    "lady": (2, 3),
    "landscape": (3, 2),
    "harbor": (4, 2),
    "still": (2, 2),
    "storm": (3, 2),
    "abstract": (2, 2),
    "gone": (2, 2),
}
BOARDS = {
    "burial": ("BURIAL SITE - KEEP OUT|HEALTH ORDER 14", (240, 236, 224), (170, 20, 20)),
    "camp": ("FAMILY CAMP|NO WEAPONS - NO SICK", (240, 236, 224), (30, 30, 30)),
    "crates": ("DO NOT OPEN|FOR THE STATE ARCHIVE", (220, 196, 140), (30, 30, 30)),
}


# ---------------------------------------------------------------- the stone

def lime(seed, stained=False):
    """Pale Indiana-style limestone in ashlar, two courses to the block, the joints fine, the fossil
    flecks in it; stained, the soot and the rain's streaks down it."""
    t = F.plain(seed, LIME, 0.04, 0.2)
    t.vary(0.05)
    t.speckle(0.25, (176, 164, 136), 0.35, 0.05)
    t.speckle(0.06, (230, 222, 200), 0.4)
    rng = t.rng
    for row, y0 in enumerate((0, 32)):
        off = 0 if row == 0 else 24
        area(t, 0, y0, N, y0 + 1, col=(150, 140, 118), dh=-0.25)
        for x in (off, off + 48 if off else 40):
            area(t, x % N, y0, x % N + 1, y0 + 32, col=(150, 140, 118), dh=-0.25)
        area(t, 0, y0 + 1, N, y0 + 32, mul=rng.uniform(0.94, 1.05))
    for _ in range(8):
        x, y = rng.uniform(0, N, 2)
        area(t, int(x), int(y), int(x) + 2, int(y) + 1, col=(150, 136, 110), dh=-0.1)
    if stained:
        t.streaks(0.9, 0.62)
        t.grime(0.32, (60, 56, 50))
    else:
        t.streaks(0.35, 0.85)
        t.grime(0.1)
    return t


def rustic(seed):
    """The base: rock-faced blocks with deep V joints, darker with the street's grime."""
    t = F.plain(seed, (184, 174, 150), 0.08, 0.15)
    t.vary(0.12)
    t.h += P.fbm(t.rng, ((8, 1.0), (16, 0.5))) * 0.15
    for y0 in (0, 32):
        area(t, 0, y0, N, y0 + 3, col=(96, 90, 78), dh=-0.5)
        x = 0 if y0 == 0 else 32
        area(t, x, y0, x + 3, y0 + 32, col=(96, 90, 78), dh=-0.5)
    t.streaks(0.6, 0.7)
    t.grime(0.3, (50, 46, 40))
    return t


@lru_cache(maxsize=None)
def rose_pic():
    """The rotunda's marble star, five by five: a compass rose in red, green and black marble in a
    cream field, the four winds in brass."""
    p = Pic(5, 5, (226, 220, 204), 21000)
    W = p.W
    c = W / 2
    p.ellipse(10, 10, W - 10, W - 10, fill=(60, 60, 62))
    p.ellipse(18, 18, W - 18, W - 18, fill=(230, 222, 204))
    p.ellipse(40, 40, W - 40, W - 40, outline=(120, 40, 36), width=4)
    for k in range(16):
        a = k * math.pi / 8
        long = k % 4 == 0
        r = c - 24 if long else (c - 70 if k % 2 == 0 else c - 100)
        wid = 0.18 if long else 0.12
        for side, col in ((-1, (150, 40, 36) if long else (40, 90, 66)), (1, (70, 20, 20) if long else (24, 54, 40))):
            p.poly([(c, c), (c + math.cos(a) * r, c + math.sin(a) * r),
                    (c + math.cos(a + side * wid) * r * 0.32, c + math.sin(a + side * wid) * r * 0.32)], fill=col)
    p.ellipse(c - 14, c - 14, c + 14, c + 14, fill=GOLD)
    for k, ch in enumerate("ESWN"):
        a = k * math.pi / 2
        p.text(c + math.cos(a) * (c - 14), c + math.sin(a) * (c - 14), ch, SERIF, 14, (176, 140, 60), anchor="mm")
    return p


def rose(i):
    t = rose_pic().tile(i, 21010 + i, smooth=0.72, grime=0.08)
    t.c *= (0.95 + 0.1 * P.fbm(t.rng, ((4, 1.0), (8, 0.5))))[..., None]
    t.f0[:] = 20
    return t


# ---------------------------------------------------------------- lettering

@lru_cache(maxsize=None)
def name_pic(key):
    text, font, cols = NAMES[key]
    p = Pic(cols, 1, (196, 186, 160, 255), 21100 + len(key))
    p.rect(0, 0, p.W, 3, fill=(160, 150, 126))
    p.rect(0, N - 3, p.W, N, fill=(160, 150, 126))
    size = civic.fit(text, font, 40, p.W - 40)
    p.text(p.W / 2 + 1.2, N / 2 + 1.2, text, font, size, (226, 218, 196), anchor="mm")
    p.text(p.W / 2, N / 2, text, font, size, (70, 62, 50), anchor="mm")
    return p


def name(key, i):
    t = name_pic(key).tile(i, 21110 + i + len(key) * 31, smooth=0.2, grime=0.15)
    t.streaks(0.6, 0.7)
    return t


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 21200 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 14, W - 1, H - 14, fill=field)
    p.rect(3, 16, W - 3, H - 16, outline=GOLD if field == MAROON else tuple(min(255, v + 50) for v in field), width=0.6)
    p.text(W / 2, H / 2 + 1, text, SERIF, civic.fit(text, SERIF, 15, W - 16), ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 21250 + i + len(key) * 11, smooth=0.45, grime=0.12)


@lru_cache(maxsize=None)
def banner_pic(key):
    title, sub, field, ink = BANNERS[key]
    p = Pic(1, 3, field, 21300 + len(key))
    W, H = p.W, p.H
    p.rect(3, 3, W - 3, H - 3, outline=ink, width=0.8)
    p.text(W / 2, 14, "GRAYHAVEN MUSEUM", BOLD, 4.4, ink, anchor="mm")
    if key == "rex":
        # the skull in white, side on
        p.poly([(10, 66), (52, 60), (56, 72), (40, 80), (12, 80)], fill=ink)
        p.poly([(14, 82), (50, 78), (46, 88), (16, 88)], fill=ink)
        for k in range(7):
            p.poly([(18 + k * 5, 80), (20 + k * 5, 85), (22 + k * 5, 80)], fill=field)
        p.ellipse(40, 64, 46, 70, fill=field)
    elif key == "nile":
        # the gold mask
        p.poly([(32, 50), (50, 58), (52, 96), (32, 104), (12, 96), (14, 58)], fill=(236, 196, 90))
        for k in range(6):
            p.line([(14, 60 + k * 7), (50, 60 + k * 7)], (30, 60, 140), 1.6)
        p.ellipse(24, 66, 40, 92, fill=(236, 196, 90))
        p.ellipse(26, 72, 31, 76, fill=(20, 20, 20))
        p.ellipse(33, 72, 38, 76, fill=(20, 20, 20))
    else:
        # a sail on the sea at evening
        p.rect(8, 60, W - 8, 100, fill=(236, 170, 90))
        p.rect(8, 84, W - 8, 100, fill=(40, 90, 110))
        p.poly([(30, 62), (30, 84), (44, 84)], fill=(246, 240, 220))
        p.ellipse(42, 66, 52, 76, fill=(250, 220, 140))
    for k, word in enumerate(title.split()):
        p.text(W / 2, 118 + k * 15, word, SERIF, civic.fit(word, SERIF, 13, W - 10), ink, anchor="mm")
    p.text(W / 2, 156, sub.replace(" ", "|", 2) if len(sub) > 14 else sub, BOLD, 4.0, ink, anchor="ma")
    p.text(W / 2, H - 12, "NOW - SPRING", BOLD, 4.2, ink, anchor="mm")
    return p


def banner(key, i):
    t = banner_pic(key).tile(i, 21310 + i + len(key) * 7, smooth=0.1, grime=0.2)
    t.c *= (0.9 + 0.1 * np.cos(np.arange(N)[None, :] / N * 2 * np.pi))[..., None]
    t.streaks(0.4, 0.8)
    if key == "light" and i == 2:
        yy, xx = np.mgrid[0:N, 0:N]
        t.alpha[(yy > 30 + 10 * np.sin(xx / 6)) & (xx > 30)] = 0  # torn away
    return t


# ---------------------------------------------------------------- pictures

def _sky(p, top, bottom, y0, y1):
    for k in range(int(y0), int(y1)):
        f = (k - y0) / max(1, y1 - y0)
        p.rect(0, k, p.W, k + 1, fill=tuple(int(a + (b - a) * f) for a, b in zip(top, bottom)))


def _ridge(p, y, amp, col, seed, rough=6):
    rng = np.random.default_rng(seed)
    pts = [(0, p.H)]
    for x in range(0, p.W + 8, 8):
        pts.append((x, y - amp * (0.5 + 0.5 * math.sin(x / 40 + seed)) - rng.uniform(0, rough)))
    pts.append((p.W, p.H))
    p.poly(pts, fill=col)


def _firs(p, y0, y1, n, col, seed, size=(10, 22)):
    rng = np.random.default_rng(seed)
    for _ in range(n):
        x, y = rng.uniform(0, p.W), rng.uniform(y0, y1)
        h = rng.uniform(*size)
        p.poly([(x, y - h), (x - h * 0.3, y), (x + h * 0.3, y)], fill=tuple(int(v * rng.uniform(0.8, 1.1)) for v in col))


@lru_cache(maxsize=None)
def backdrop_pic(key):
    """The painted backdrops at the back of the dioramas, four by three: the far country the
    animals stand before, done in the old museum painters' way."""
    p = Pic(4, 3, (120, 150, 180), 21400 + len(key))
    H = p.H
    if key == "forest":
        _sky(p, (110, 150, 196), (220, 214, 190), 0, H * 0.6)
        _ridge(p, H * 0.45, 30, (110, 130, 150), 1)
        _ridge(p, H * 0.6, 20, (70, 96, 80), 2)
        _firs(p, H * 0.55, H * 0.75, 120, (40, 70, 46), 3)
        p.rect(0, H * 0.74, p.W, H, fill=(88, 96, 54))
        _firs(p, H * 0.7, H * 0.95, 40, (30, 52, 34), 4, (30, 60))
        p.poly([(150, H), (200, H * 0.78), (230, H * 0.8), (260, H)], fill=(90, 120, 150))  # the stream
    elif key == "prairie":
        _sky(p, (120, 160, 210), (238, 214, 170), 0, H * 0.7)
        for k in range(5):
            x, y = 30 + k * 50, 30 + (k % 2) * 12
            p.ellipse(x, y, x + 40, y + 14, fill=(246, 244, 236), a=200)
        _ridge(p, H * 0.66, 8, (150, 140, 110), 5)
        p.rect(0, H * 0.7, p.W, H, fill=(184, 160, 96))
        rng = np.random.default_rng(6)
        for _ in range(400):
            x, y = rng.uniform(0, p.W), rng.uniform(H * 0.7, H)
            p.line([(x, y), (x + rng.uniform(-1, 1), y - rng.uniform(2, 5))], (150, 124, 64), 0.5)
        for k in range(14):
            x, y = rng.uniform(0, p.W), rng.uniform(H * 0.72, H * 0.8)
            p.ellipse(x, y, x + 6, y + 3, fill=(60, 40, 26))  # the far herd
    elif key == "winter":
        _sky(p, (150, 166, 186), (214, 214, 220), 0, H * 0.6)
        _ridge(p, H * 0.42, 40, (200, 206, 220), 7)
        _ridge(p, H * 0.58, 16, (150, 160, 170), 8)
        _firs(p, H * 0.56, H * 0.72, 90, (50, 70, 70), 9)
        p.rect(0, H * 0.72, p.W, H, fill=(226, 230, 236))
        rng = np.random.default_rng(10)
        for _ in range(300):
            x, y = rng.uniform(0, p.W), rng.uniform(0, H)
            p.ellipse(x, y, x + 1.2, y + 1.2, fill=(250, 250, 252), a=200)
    else:
        _sky(p, (236, 170, 110), (250, 220, 170), 0, H * 0.6)
        p.ellipse(180, 40, 210, 70, fill=(252, 236, 180))
        _ridge(p, H * 0.6, 6, (120, 110, 90), 11)
        p.rect(0, H * 0.62, p.W, H, fill=(110, 130, 120))
        rng = np.random.default_rng(12)
        for _ in range(200):
            x, y = rng.uniform(0, p.W), rng.uniform(H * 0.62, H)
            p.line([(x, y), (x, y - rng.uniform(6, 14))], (140, 130, 70), 0.8)
        for k in range(6):
            x, y = 40 + k * 30, 30 + (k % 3) * 8
            p.line([(x - 4, y), (x, y + 2), (x + 4, y)], (60, 40, 30), 0.7)  # geese
    return p


def backdrop(key, i):
    t = backdrop_pic(key).tile(i, 21450 + i + len(key) * 13, smooth=0.1, grime=0.06)
    t.c *= (0.9 + 0.1 * P.fbm(t.rng, ((8, 1.0), (16, 0.5))))[..., None]
    return t


def _frame(p, x0, y0, x1, y1, col=GOLD, w=7):
    p.rect(x0, y0, x1, y1, fill=col)
    p.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, outline=tuple(int(v * 0.7) for v in col), width=1)
    p.rect(x0 + w - 1, y0 + w - 1, x1 - w + 1, y1 - w + 1, fill=tuple(int(v * 0.55) for v in col))


@lru_cache(maxsize=None)
def art_pic(key):
    """The paintings in their frames; gone, the pale square where one hung and the label left."""
    cols, rows = ART[key]
    p = Pic(cols, rows, (0, 0, 0, 0), 21500 + len(key) * 17)
    W, H = p.W, p.H
    rng = p.rng
    if key == "gone":
        p.rect(6, 6, W - 6, H - 20, fill=(236, 228, 210), a=120)
        p.rect(W / 2 - 1, 2, W / 2 + 1, 8, fill=(80, 70, 50))
        p.rect(W / 2 - 14, H - 14, W / 2 + 14, H - 4, fill=(244, 240, 228))
        p.text(W / 2, H - 9, "REMOVED FOR|SAFEKEEPING", BOLD, 2.6, (40, 40, 40), anchor="mm")
        return p
    gold = (180, 140, 70) if key not in ("abstract",) else (40, 40, 40)
    _frame(p, 2, 2, W - 2, H - 2, gold, 7 if key != "abstract" else 4)
    x0, y0, x1, y1 = 9, 9, W - 9, H - 9
    if key in ("portrait", "lady"):
        p.rect(x0, y0, x1, y1, fill=(40, 34, 28) if key == "portrait" else (60, 70, 74))
        cx = (x0 + x1) / 2
        coat = (30, 28, 30) if key == "portrait" else (120, 40, 50)
        p.poly([(cx - 40, y1), (cx - 34, y1 - 70), (cx, y1 - 86), (cx + 34, y1 - 70), (cx + 40, y1)], fill=coat)
        p.rect(cx - 8, y1 - 92, cx + 8, y1 - 78, fill=(214, 176, 146))
        p.ellipse(cx - 18, y1 - 136, cx + 18, y1 - 90, fill=(222, 184, 152))
        hair = (60, 50, 40) if key == "portrait" else (110, 70, 40)
        p.ellipse(cx - 20, y1 - 142, cx + 20, y1 - 116, fill=hair)
        if key == "lady":
            p.ellipse(cx - 24, y1 - 132, cx - 12, y1 - 96, fill=hair)
            p.ellipse(cx + 12, y1 - 132, cx + 24, y1 - 96, fill=hair)
        p.ellipse(cx - 9, y1 - 118, cx - 5, y1 - 114, fill=(40, 30, 30))
        p.ellipse(cx + 5, y1 - 118, cx + 9, y1 - 114, fill=(40, 30, 30))
        p.line([(cx - 5, y1 - 102), (cx + 5, y1 - 102)], (150, 80, 70), 1)
        if key == "portrait":
            p.poly([(cx - 6, y1 - 78), (cx + 6, y1 - 78), (cx, y1 - 60)], fill=(236, 232, 220))
    elif key in ("landscape", "storm"):
        sky_top, sky_bot = ((90, 130, 180), (230, 214, 180)) if key == "landscape" else ((50, 56, 66), (120, 120, 116))
        for k in range(int(y0), int(y1)):
            f = (k - y0) / (y1 - y0)
            p.rect(x0, k, x1, k + 1, fill=tuple(int(a + (b - a) * min(1, f * 1.6)) for a, b in zip(sky_top, sky_bot)))
        hill = (90, 110, 60) if key == "landscape" else (50, 60, 50)
        p.poly([(x0, y1), (x0, y0 + 60), (60, y0 + 50), (120, y0 + 70), (x1, y0 + 56), (x1, y1)], fill=hill)
        p.rect(x0, y0 + 84, x1, y1, fill=tuple(int(v * 0.8) for v in hill))
        if key == "landscape":
            p.ellipse(130, 24, 156, 50, fill=(60, 90, 40))
            p.rect(141, 48, 145, 80, fill=(70, 50, 30))
            p.poly([(40, y0 + 80), (70, y0 + 70), (100, y0 + 84)], fill=(160, 150, 140))  # a cottage roof
            p.rect(48, y0 + 80, 92, y0 + 96, fill=(220, 210, 190))
        else:
            # the ship heeled over under the storm
            p.poly([(x0, y0 + 80), (x1, y0 + 72), (x1, y1), (x0, y1)], fill=(40, 60, 64))
            p.poly([(70, y0 + 70), (120, y0 + 60), (114, y0 + 76), (74, y0 + 82)], fill=(50, 34, 24))
            p.poly([(94, y0 + 64), (104, y0 + 20), (108, y0 + 62)], fill=(214, 206, 186))
            for k in range(8):
                xs = rng.uniform(x0, x1)
                p.line([(xs, y0 + 76 + rng.uniform(0, 20)), (xs + 10, y0 + 70 + rng.uniform(0, 20))], (220, 230, 230), 1.2)
    elif key == "harbor":
        for k in range(int(y0), int(y1)):
            f = (k - y0) / (y1 - y0)
            p.rect(x0, k, x1, k + 1, fill=(int(240 - 60 * f), int(200 - 60 * f), int(140 + 20 * f)))
        p.rect(x0, y0 + 60, x1, y1, fill=(60, 100, 120))
        p.ellipse(180, 30, 210, 60, fill=(252, 230, 160))
        for k in range(30):
            y = y0 + 64 + k * 2
            p.line([(170 + rng.uniform(-20, 0), y), (220 + rng.uniform(0, 20), y)], (240, 200, 120), 0.6)
        for k in range(4):
            x = 40 + k * 46
            p.poly([(x, y0 + 74), (x + 30, y0 + 74), (x + 24, y0 + 82), (x + 6, y0 + 82)], fill=(50, 34, 28))
            p.line([(x + 15, y0 + 74), (x + 15, y0 + 20 + k * 6)], (40, 30, 26), 1.2)
            p.poly([(x + 16, y0 + 24 + k * 6), (x + 16, y0 + 70), (x + 34, y0 + 70)], fill=(236, 226, 200))
        p.poly([(x1 - 50, y0 + 60), (x1 - 40, y0 + 18), (x1 - 34, y0 + 60)], fill=(120, 110, 100))  # the lighthouse
    elif key == "still":
        p.rect(x0, y0, x1, y1, fill=(40, 34, 30))
        p.rect(x0, y0 + 70, x1, y1, fill=(110, 70, 40))
        p.ellipse(30, 44, 70, 84, fill=(200, 160, 60))
        p.ellipse(60, 54, 90, 84, fill=(160, 40, 40))
        p.ellipse(84, 40, 110, 80, fill=(80, 110, 50))
        p.poly([(18, 84), (40, 30), (44, 30), (36, 84)], fill=(150, 160, 170))
    else:
        p.rect(x0, y0, x1, y1, fill=(236, 232, 220))
        p.rect(20, 20, 70, 80, fill=(200, 40, 30))
        p.rect(74, 20, 110, 50, fill=(30, 60, 160))
        p.rect(74, 54, 110, 110, fill=(236, 200, 40))
        p.rect(20, 84, 70, 110, fill=(20, 20, 20))
    return p


def art(key, i):
    t = art_pic(key).tile(i, 21560 + i + len(key) * 19, smooth=0.5, grime=0.05)
    gold = (t.c[..., 0] > 150) & (t.c[..., 2] < 110) & (t.alpha > 200)
    t.f0[gold] = 220
    return t


@lru_cache(maxsize=None)
def hiero_pic():
    """A tomb wall's painted relief, four by two: the procession with offerings, the columns of
    glyphs, the sun disc."""
    p = Pic(4, 2, (200, 176, 130), 21700)
    W, H = p.W, p.H
    rng = p.rng
    p.rect(0, 0, W, 8, fill=(30, 60, 120))
    p.rect(0, H - 8, W, H, fill=(30, 60, 120))
    for k in range(0, W, 8):
        p.rect(k, 8, k + 4, 11, fill=(180, 40, 30))
    for k in range(5):
        x = 30 + k * 46
        p.ellipse(x - 5, 40, x + 5, 52, fill=(150, 80, 50))
        p.rect(x - 6, 52, x + 6, 82, fill=(236, 230, 210) if k % 2 else (150, 80, 50))
        p.line([(x - 4, 82), (x - 6, 108)], (150, 80, 50), 2)
        p.line([(x + 4, 82), (x + 6, 108)], (150, 80, 50), 2)
        p.line([(x + 6, 58), (x + 16, 52)], (150, 80, 50), 1.6)
        p.ellipse(x + 14, 44, x + 22, 52, fill=(60, 120, 90))
    p.ellipse(W - 30, 18, W - 10, 38, fill=(220, 50, 30))
    for c in range(18):
        x = 12 + c * 14
        for r in range(3):
            y = 14 + r * 9 if c % 2 else 112 + r * 4
            if y > H - 12:
                continue
            kind = rng.integers(4)
            col = (30, 30, 30) if rng.random() < 0.6 else (30, 60, 120)
            if kind == 0:
                p.ellipse(x, y, x + 5, y + 5, outline=col, width=0.6)
            elif kind == 1:
                p.line([(x, y + 5), (x + 3, y), (x + 6, y + 5)], col, 0.6)
            elif kind == 2:
                p.rect(x, y + 1, x + 6, y + 4, outline=col, width=0.6)
            else:
                p.line([(x + 3, y), (x + 3, y + 6)], col, 0.8)
    return p


def hiero(i):
    t = hiero_pic().tile(i, 21710 + i, smooth=0.15, grime=0.18)
    t.crack(3, 0.2, (120, 100, 70))
    return t


@lru_cache(maxsize=None)
def plan_pic():
    p = Pic(2, 1, (236, 230, 214), 21800)
    W, H = p.W, p.H
    p.rect(0, 0, W, 12, fill=MAROON)
    p.text(W / 2, 6, "MUSEUM PLAN", SERIF, 7, CREAM, anchor="mm")
    p.rect(10, 20, W - 10, H - 10, outline=(60, 60, 60), width=1)
    p.ellipse(W / 2 - 14, 24, W / 2 + 14, 52, outline=(60, 60, 60), width=1)
    p.line([(W / 2 - 20, 20), (W / 2 - 20, H - 10)], (60, 60, 60), 0.6)
    p.line([(W / 2 + 20, 20), (W / 2 + 20, H - 10)], (60, 60, 60), 0.6)
    p.text(32, 36, "NATURAL|HISTORY", BOLD, 4, (40, 40, 40), anchor="mm")
    p.text(W - 32, 36, "ART &|ANCIENT", BOLD, 4, (40, 40, 40), anchor="mm")
    p.ellipse(W / 2 - 3, H - 18, W / 2 + 3, H - 12, fill=(220, 30, 30))
    p.text(W / 2 + 6, H - 15, "YOU ARE HERE", BOLD, 3, (220, 30, 30), anchor="lm")
    return p


def plan(i):
    return plan_pic().tile(i, 21810 + i, smooth=0.4, grime=0.1)


@lru_cache(maxsize=None)
def parkmap_pic():
    """The park's map board, three by two: the lake and island, the paths, the boathouse, the
    bandstand, the palm house, the museum along the top."""
    p = Pic(3, 2, (40, 70, 52), 21850)
    W, H = p.W, p.H
    p.rect(4, 4, W - 4, H - 4, fill=(226, 220, 196))
    p.text(W / 2, 12, "GRAYHAVEN CITY PARK", SERIF, 9, (34, 70, 52), anchor="mm")
    p.rect(30, 20, W - 30, 36, fill=(190, 180, 160))
    p.text(W / 2, 28, "MUSEUM", BOLD, 5, (60, 50, 40), anchor="mm")
    p.ellipse(54, 50, 150, 106, fill=(120, 170, 196))
    p.ellipse(104, 70, 122, 88, fill=(150, 190, 120))
    p.ellipse(20, 60, 36, 76, outline=(120, 50, 30), width=1.2)
    p.rect(150, 46, 184, 66, outline=(60, 110, 140), width=1.2)
    p.text(28, 82, "BANDSTAND", BOLD, 3.2, (30, 30, 30), anchor="mm")
    p.text(167, 72, "PALM HOUSE", BOLD, 3.2, (30, 30, 30), anchor="mm")
    p.text(102, 46, "BOATHOUSE", BOLD, 3.2, (30, 30, 30), anchor="mm")
    p.text(102, 112, "LAKE", BOLD, 4, (40, 80, 110), anchor="mm")
    for k in range(40):
        a = k / 40 * math.pi * 2
        p.ellipse(102 + math.cos(a) * 58 - 0.6, 78 + math.sin(a) * 34 - 0.6, 102 + math.cos(a) * 58 + 0.6, 78 + math.sin(a) * 34 + 0.6, fill=(140, 110, 70))
    p.ellipse(100, H - 18, 106, H - 12, fill=(220, 30, 30))
    p.text(110, H - 15, "YOU ARE HERE", BOLD, 3.2, (220, 30, 30), anchor="lm")
    p.scrawl(140, 96, "CAMP AT|BANDSTAND|WATER HERE", HAND, 5, (30, 30, 140), 0.3)
    return p


def parkmap(i):
    t = parkmap_pic().tile(i, 21860 + i, smooth=0.3, grime=0.2)
    t.streaks(0.5)
    return t


@lru_cache(maxsize=None)
def board_pic(key):
    text, field, ink = BOARDS[key]
    p = Pic(3, 1, (0, 0, 0, 0), 21900 + len(key))
    W, H = p.W, p.H
    p.rect(2, 8, W - 2, H - 8, fill=field)
    for k in range(4):
        p.line([(2, 8 + k * 12), (W - 2, 8 + k * 12)], tuple(int(v * 0.85) for v in field), 0.4)
    p.scrawl(10, 16, text, STENCIL if key == "crates" else HAND, 11, ink, 0.2 if key == "crates" else 0.35)
    return p


def board(key, i):
    return board_pic(key).tile(i, 21910 + i + len(key) * 5, smooth=0.15, grime=0.25)


def label(seed):
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.rect(18, 26, 46, 40, fill=(244, 240, 230))
    for k in range(4):
        p.line([(21, 29 + k * 3), (43 - (k == 3) * 10, 29 + k * 3)], (60, 60, 60), 0.4)
    return p.tile(0, seed, smooth=0.3, grime=0.05)


# ---------------------------------------------------------------- materials

def bone(seed, kind="fossil"):
    """Fossil bone: brown and ochre mineral, darker in the cracks; whale bone ivory and grey."""
    base = (132, 100, 66) if kind == "fossil" else (214, 204, 180)
    t = F.plain(seed, base, 0.1, 0.35)
    t.vary(0.18, tint=(14, 8, -6))
    t.speckle(0.2, (80, 56, 34) if kind == "fossil" else (170, 160, 140), 0.4, -0.05)
    t.crack(5, 0.2, (50, 36, 24) if kind == "fossil" else (120, 110, 96))
    t.grime(0.1)
    return t


def fur(seed, rgb, shaggy=0.0):
    t = F.plain(seed, rgb, 0.06, 0.2)
    rng = t.rng
    for _ in range(int(900 + shaggy * 900)):
        x, y = rng.uniform(0, N, 2)
        ln = rng.uniform(2, 5 + shaggy * 6)
        col = np.array(rgb) * rng.uniform(0.6, 1.3)
        area(t, int(x), int(y), int(x) + 1, int(y + ln), col=tuple(np.clip(col, 0, 255)), dh=0.05)
    return t


def glass_case(seed, broken=False):
    t = P.glass(seed, (196, 206, 210), broken=broken)
    pane = (t.alpha > 0) & (t.alpha < 255)
    t.alpha[pane] = 60
    m = t.frame(1, (60, 50, 40), 1.0, 0.5)
    t.alpha[m] = 255
    return t


def gh_glass(seed, broken=False):
    """The palm house's glass: small panes in white-painted iron glazing bars, green with algae at the laps."""
    t = P.Tex(seed, (200, 214, 206))
    t.alpha[:] = 70
    t.smooth[:] = 0.9
    t.f0[:] = 20
    t.poro[:] = 0
    for k in (0, 32):
        area(t, 0, k, N, k + 2, col=(232, 230, 222), alpha=255, smooth=0.4)
    for k in (0, 21, 42):
        area(t, k, 0, k + 2, N, col=(232, 230, 222), alpha=255, smooth=0.4)
    for k in (2, 34):
        area(t, 0, k + 26, N, k + 30, col=(110, 140, 90), alpha=160)
    if broken:
        yy, xx = np.mgrid[0:N, 0:N]
        hole = (np.hypot(xx - 30, yy - 44) < 12 + 4 * np.sin(np.arctan2(yy - 44, xx - 30) * 5))
        t.alpha[hole & (t.alpha < 255)] = 0
    t.grime(0.15, (80, 90, 70))
    return t


def frond(seed):
    """A palm frond: the midrib and the leaflets fanned off it, see-through between."""
    t = P.Tex(seed, (60, 110, 50))
    t.alpha[:] = 0
    rng = t.rng
    yy, xx = np.mgrid[0:N, 0:N]
    for y in range(2, N - 2, 3):
        ln = 28 * math.sin(math.pi * y / N) + 2
        for side in (-1, 1):
            m = (np.abs(yy - (y + np.abs(xx - 32) * 0.4)) < 0.9) & (np.abs(xx - 32) < ln) & (np.sign(xx - 32) == side)
            t.alpha[m] = 255
            t.c[m] = np.array((60, 110, 50)) * rng.uniform(0.7, 1.2)
    t.alpha[:, 31:33] = 255
    t.c[:, 31:33] = (110, 120, 60)
    brown = rng.random((N, N)) < 0.1
    t.c[brown] = (130, 110, 60)
    t.sss = np.where(t.alpha > 0, 0.6, 0)
    return t


def fern(seed):
    t = frond(seed)
    t.c *= np.array((0.8, 1.05, 0.7))
    return t


def crate_side(seed, stencil=True):
    t = F.mat("Planks009", (176, 140, 92), 0.9, seed=seed, smooth=0.2)
    for k in (0, 21, 42):
        area(t, 0, k, N, k + 1, col=(90, 70, 46), dh=-0.3)
    area(t, 0, 0, 5, N, col=(150, 116, 74), dh=0.2)
    area(t, N - 5, 0, N, N, col=(150, 116, 74), dh=0.2)
    if stencil:
        m = F.text_mask("FRAGILE", STENCIL, 9, N, N, seed=seed)
        F.paste(t, m, 0, -8, (30, 30, 30), 0.9)
        m = F.text_mask("GRAYHAVEN|MUSEUM", STENCIL, 6, N, N, seed=seed + 1)
        F.paste(t, m, 0, 12, (150, 30, 30), 0.85)
    t.grime(0.1)
    return t


def straw(seed):
    t = F.plain(seed, (190, 160, 90), 0.08, 0.1)
    rng = t.rng
    for _ in range(500):
        x, y = rng.uniform(0, N, 2)
        a = rng.uniform(0, math.pi)
        for s in range(int(rng.uniform(4, 10))):
            xi, yi = int(x + math.cos(a) * s) % N, int(y + math.sin(a) * s) % N
            t.c[yi, xi] = np.array((220, 190, 110)) * rng.uniform(0.7, 1.1)
    return t


def case_top(seed, kind):
    """What lies in the flat cases, under the glass: fossils on grey felt, coins on blue, jewels on black."""
    bg = {"fossils": (110, 110, 106), "coins": (40, 60, 100), "jewels": (24, 24, 28), "gems": (30, 20, 40),
          "minerals": (200, 196, 186), "shells": (220, 210, 190)}[kind]
    t = F.plain(seed, bg, 0.03, 0.2)
    rng = t.rng
    for k in range(9):
        x, y = 8 + (k % 3) * 22 + rng.uniform(-3, 3), 8 + (k // 3) * 22 + rng.uniform(-3, 3)
        if kind == "fossils":
            disc(t, x, y, 7, col=(150, 130, 100), h=0.9)
            for a in range(0, 360, 40):
                r = 2 + a / 90
                area(t, x + math.cos(math.radians(a)) * r, y + math.sin(math.radians(a)) * r,
                     x + math.cos(math.radians(a)) * r + 1, y + math.sin(math.radians(a)) * r + 1, col=(90, 70, 50))
        elif kind == "coins":
            disc(t, x, y, 4, col=(200, 170, 80) if k % 3 else (180, 180, 186), h=0.9, f0=230)
        elif kind == "jewels":
            disc(t, x, y, 5, col=(220, 200, 120), h=0.85, f0=230, ring=1.2)
            disc(t, x, y - 4, 2, col=tuple(int(v) for v in rng.integers(60, 255, 3)), h=0.95, f0=40)
        elif kind == "gems":
            col = [(220, 30, 60), (40, 90, 220), (40, 180, 90), (240, 200, 60), (230, 230, 250)][k % 5]
            disc(t, x, y, 4, col=col, h=0.95, smooth=0.95)
            area(t, x - 1, y - 2, x + 1, y - 1, col=(255, 255, 255))
        elif kind == "minerals":
            for s in range(5):
                area(t, x + s * 2 - 4, y - s, x + s * 2 - 2, y + 4, col=(150 + s * 15, 120 + s * 20, 200), h=0.9)
        else:
            disc(t, x, y, 5, col=(236, 200, 170), h=0.9)
            for r in range(1, 5, 2):
                disc(t, x, y, r, mul=0.85)
        area(t, x - 4, y + 8, x + 4, y + 10, col=(240, 236, 226))  # the label
    t.grime(0.06)
    return t


def coffin_face(seed, part):
    """The standing mummy case, painted: the gold face and wig over the broad collar (hi), the
    wings and the column of text over the feet (lo)."""
    t = F.plain(seed, (40, 60, 120), 0.05, 0.5)
    yy, xx = np.mgrid[0:N, 0:N]
    if part == "hi":
        wig = (np.abs(xx - 32) < 22) & (yy > 4)
        t.c[wig] = (30, 50, 110)
        for k in range(10, N, 6):
            t.c[(np.abs(yy - k) < 1) & wig] = (220, 180, 70)
        face = ((xx - 32) ** 2 / 12 ** 2 + (yy - 26) ** 2 / 15 ** 2) < 1
        t.c[face] = (226, 180, 76)
        t.f0[face] = 230
        t.c[(np.abs(yy - 23) < 2) & (np.abs(np.abs(xx - 32) - 5) < 2.5)] = (20, 20, 20)
        collar = (yy > 44)
        for k in range(46, N, 4):
            t.c[(np.abs(yy - k) < 1.2) & collar] = [(200, 40, 30), (220, 180, 70), (40, 140, 120)][k % 3]
    else:
        t.c[:] = (220, 180, 76)
        t.f0[:] = 200
        for k in range(0, N, 5):
            t.c[(np.abs(yy - k) < 1)] = (30, 60, 130)
        col = np.abs(xx - 32) < 6
        t.c[col] = (236, 226, 196)
        for k in range(4, N, 7):
            t.c[(np.abs(yy - k) < 1.5) & (np.abs(xx - 32) < 3)] = (30, 30, 30)
    t.grime(0.15)
    return t


def sarc_lid(seed):
    t = coffin_face(seed, "lo")
    return t


def pottery(seed, black=False):
    """Terracotta; black, the black-figure ware: the band of runners round the belly."""
    t = F.plain(seed, (176, 96, 56), 0.06, 0.4)
    if black:
        yy, xx = np.mgrid[0:N, 0:N]
        band = (yy > 20) & (yy < 44)
        rng = t.rng
        for k in range(5):
            x = 6 + k * 13
            fig = ((xx - x) ** 2 + (yy - 26) ** 2 < 9) | ((np.abs(xx - x) < 2) & (yy > 28) & (yy < 40)) \
                | ((np.abs(xx - x - (yy - 34) * 0.5) < 1.2) & (yy > 34) & (yy < 44))
            t.c[fig] = (24, 20, 18)
        t.c[(np.abs(yy - 18) < 1.5) | (np.abs(yy - 46) < 1.5)] = (24, 20, 18)
        t.c[yy > 50] = (24, 20, 18)
        t.smooth[band] = 0.6
    t.crack(2, 0.2, (100, 60, 40))
    t.grime(0.1)
    return t


def mail(seed):
    t = F.plain(seed, (150, 152, 156), 0.05, 0.6)
    t.f0[:] = 230
    for y in range(0, N, 3):
        for x in range((y // 3) % 2 * 2, N, 4):
            disc(t, x, y, 1.6, mul=0.7, ring=0.6)
    t.grime(0.15)
    return t


def gravel(seed, weeds=False):
    t = F.mat("Gravel023", (176, 164, 140), 1.0, seed=seed, smooth=0.15)
    if weeds:
        rng = t.rng
        stain(t, rng.uniform(10, 54), rng.uniform(10, 54), rng.uniform(8, 16), (70, 76, 70), 0.5)
        m = rng.random((N, N)) < 0.04
        t.c[m] = (80, 110, 50)
    t.grime(0.1)
    return t


def boards(seed):
    t = F.mat("Planks009", (128, 100, 70), 0.9, seed=seed, smooth=0.2)
    for k in range(0, N, 16):
        area(t, 0, k, N, k + 1, col=(50, 40, 30), dh=-0.3)
    t.grime(0.25, (60, 60, 50))
    t.streaks(0.3)
    return t


def white_iron(seed):
    t = F.plain(seed, (226, 224, 216), 0.04, 0.5)
    t.speckle(0.04, (120, 80, 60), 0.6)
    t.grime(0.15)
    return t


def green_iron(seed):
    t = F.plain(seed, (46, 80, 62), 0.05, 0.45)
    t.speckle(0.05, (110, 70, 50), 0.5)
    t.grime(0.15)
    return t


def boat_hull(seed, col):
    t = F.mat("PaintedWood004", col, 0.8, seed=seed, smooth=0.3)
    for k in range(0, N, 10):
        area(t, 0, k, N, k + 1, mul=0.75)
    t.grime(0.2)
    return t


def stripes(seed, a, b, w=8):
    t = F.plain(seed, a, 0.03, 0.15)
    for x in range(0, N, w * 2):
        area(t, x, 0, x + w, N, col=b)
    t.streaks(0.4)
    t.grime(0.1)
    return t


def icecream_side(seed):
    t = F.plain(seed, (236, 232, 222), 0.03, 0.4)
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(32, 14, "ICE CREAM", BLACK, 8, (200, 40, 60), anchor="mm")
    for k, col in enumerate(((250, 220, 230), (120, 70, 40), (250, 250, 240))):
        x = 14 + k * 18
        p.poly([(x - 5, 34), (x + 5, 34), (x, 52)], fill=(210, 160, 90))
        p.ellipse(x - 6, 24, x + 6, 36, fill=col)
    p.text(32, 58, "25c", BOLD, 6, (40, 40, 40), anchor="mm")
    m = p.tile(0, seed, smooth=0.3, grime=0)
    a = m.alpha / 255
    t.c[:] = t.c * (1 - a[..., None]) + m.c * a[..., None]
    t.grime(0.2)
    return t


def textures():
    T = {}
    T["mu_lime"] = lambda: lime(21001)
    T["mu_lime_b"] = lambda: lime(21002, stained=True)
    T["mu_rustic"] = lambda: rustic(21003)
    for i in range(25):
        T[f"mu_rose_{i}"] = lambda i=i: rose(i)
    for key, (_, _, cols) in NAMES.items():
        for i in range(cols):
            T[f"mu_name_{key}_{i}"] = lambda key=key, i=i: name(key, i)
    for key in SIGNS:
        for i in range(2):
            T[f"mu_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key in BANNERS:
        for i in range(3):
            T[f"mu_vbanner_{key}_{i}"] = lambda key=key, i=i: banner(key, i)
    for key in BACKDROPS:
        for i in range(12):
            T[f"mu_bd_{key}_{i}"] = lambda key=key, i=i: backdrop(key, i)
    for key, (cols, rows) in ART.items():
        for i in range(cols * rows):
            T[f"mu_art_{key}_{i}"] = lambda key=key, i=i: art(key, i)
    for i in range(8):
        T[f"mu_hiero_{i}"] = lambda i=i: hiero(i)
    for i in range(2):
        T[f"mu_plan_{i}"] = lambda i=i: plan(i)
    for i in range(6):
        T[f"mu_parkmap_{i}"] = lambda i=i: parkmap(i)
    for key in BOARDS:
        for i in range(3):
            T[f"mu_board_{key}_{i}"] = lambda key=key, i=i: board(key, i)
    T["mu_label"] = lambda: label(21950)
    T["mu_bone"] = lambda: bone(21951)
    T["mu_whalebone"] = lambda: bone(21952, "whale")
    T["mu_fur_bear"] = lambda: fur(21953, (70, 50, 34), 0.5)
    T["mu_fur_deer"] = lambda: fur(21954, (146, 110, 74))
    T["mu_fur_wolf"] = lambda: fur(21955, (120, 118, 112), 0.3)
    T["mu_fur_bison"] = lambda: fur(21956, (64, 44, 30), 1.0)
    T["mu_fur_fox"] = lambda: fur(21957, (184, 92, 40))
    T["mu_feather"] = lambda: fur(21958, (92, 66, 44))
    T["mu_feather_white"] = lambda: fur(21959, (232, 228, 220))
    T["mu_antler"] = lambda: F.plain(21960, (196, 176, 140), 0.08, 0.3)
    T["mu_case_glass"] = lambda: glass_case(21961)
    T["mu_case_glass_broken"] = lambda: glass_case(21962, broken=True)
    for k, kind in enumerate(("fossils", "coins", "jewels", "gems", "minerals", "shells")):
        T[f"mu_top_{kind}"] = lambda kind=kind, k=k: case_top(21963 + k, kind)
    T["mu_coffin_hi"] = lambda: coffin_face(21970, "hi")
    T["mu_coffin_lo"] = lambda: coffin_face(21971, "lo")
    T["mu_lapis"] = lambda: F.plain(21972, (34, 56, 120), 0.08, 0.5)
    T["mu_gold"] = lambda: F.wear(F.plain(21973, (214, 170, 70), 0.06, 0.7), 0.15, 0)
    T["mu_terracotta"] = lambda: pottery(21974)
    T["mu_blackfig"] = lambda: pottery(21975, True)
    T["mu_mail"] = lambda: mail(21976)
    T["mu_gravel"] = lambda: gravel(21977)
    T["mu_gravel_b"] = lambda: gravel(21978, True)
    T["mu_boards"] = lambda: boards(21979)
    T["mu_white_iron"] = lambda: white_iron(21980)
    T["mu_green_iron"] = lambda: green_iron(21981)
    T["mu_gh_glass"] = lambda: gh_glass(21982)
    T["mu_gh_glass_broken"] = lambda: gh_glass(21983, broken=True)
    T["mu_frond"] = lambda: frond(21984)
    T["mu_fern"] = lambda: fern(21985)
    T["mu_palm_bark"] = lambda: F.mat("Bark006", (120, 100, 70), 1.0, seed=21986)
    T["mu_crate"] = lambda: crate_side(21987)
    T["mu_crate_plain"] = lambda: crate_side(21988, False)
    T["mu_straw"] = lambda: straw(21989)
    T["mu_boat_green"] = lambda: boat_hull(21990, (40, 90, 70))
    T["mu_boat_red"] = lambda: boat_hull(21991, (150, 46, 40))
    T["mu_parasol"] = lambda: stripes(21992, (236, 232, 222), (200, 50, 60))
    T["mu_lifering"] = lambda: stripes(21993, (236, 236, 230), (220, 60, 40), 16)
    T["mu_icecream"] = lambda: icecream_side(21994)
    T["mu_dio_ground"] = lambda: F.wear(F.mat("Ground037", (110, 100, 60), 1.0, seed=21995), 0.1, 0)
    T["mu_dio_snow"] = lambda: F.plain(21996, (232, 236, 240), 0.05, 0.4)
    T["mu_dio_rock"] = lambda: F.mat("Rock036", (130, 124, 116), 1.0, seed=21997)
    T["mu_velvet"] = lambda: F.plain(21998, (110, 20, 30), 0.06, 0.25)
    T["mu_leather"] = lambda: F.plain(21999, (40, 30, 26), 0.05, 0.55)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the skeletons

def _chain(pts, r0, r1=None, step=None):
    """Bone along the polyline pts (in 16ths), r0 thick at the start tapering to r1 at the end, as
    little cubes laid along it."""
    r1 = r0 if r1 is None else r1
    out = []
    total = sum(math.dist(a, b) for a, b in zip(pts, pts[1:]))
    done = 0.0
    for a, b in zip(pts, pts[1:]):
        seg = math.dist(a, b)
        n = max(1, int(seg / (step or max(1.6, min(r0, r1) * 1.1))))
        for k in range(n + 1):
            f = k / n
            p = [a[i] + (b[i] - a[i]) * f for i in range(3)]
            r = r0 + (r1 - r0) * (done + seg * f) / max(total, 1e-6)
            h = max(0.6, r / 2)
            out.append([p[0] - h, p[1] - h, p[2] - h, p[0] + h, p[1] + h, p[2] + h])
        done += seg
    return out


def _box(x0, y0, z0, x1, y1, z1):
    return [[x0, y0, z0, x1, y1, z1]]


def _mirror(boxes, zc):
    return boxes + [[b[0], b[1], 2 * zc - b[5], b[3], b[4], 2 * zc - b[2]] for b in boxes]


def rex():
    """Tyrannosaurus as it was mounted in the seventies' style, head east (+x), the plinth's top
    at y = 0, three blocks across (z 0..48, the spine at 24)."""
    zc = 24
    B = []
    spine = [(0, 26, zc), (30, 40, zc), (60, 54, zc), (88, 62, zc), (116, 62, zc), (140, 58, zc)]
    B += _chain(spine, 2.5, 7)
    for x in range(20, 140, 6):
        y = 26 + min(36, x * 0.42) if x < 88 else 62 - max(0, x - 116) * 0.17
        B += _box(x - 0.8, y + 2, zc - 0.8, x + 0.8, y + 2 + (8 if 60 < x < 120 else 4), zc + 0.8)
    for x in range(10, 80, 7):  # the chevrons under the tail
        y = 26 + min(36, x * 0.42)
        B += _box(x - 0.6, y - 6, zc - 0.6, x + 0.6, y - 2, zc + 0.6)
    B += _chain([(140, 58, zc), (156, 66, zc), (170, 74, zc)], 7, 6)
    # the pelvis: the ilium blade and the pubis down to its boot
    B += _box(72, 56, zc - 4, 106, 70, zc + 4)
    B += _chain([(96, 56, zc), (100, 40, zc), (102, 32, zc)], 4)
    B += _box(94, 30, zc - 4, 110, 34, zc + 4)
    # the legs, one striding
    for z, knee, ankle, foot in ((zc - 9, (100, 34), (86, 12), (94, 3)), (zc + 9, (82, 34), (70, 12), (76, 3))):
        B += _chain([(88, 60, z), (knee[0], knee[1], z)], 7, 5)
        B += _chain([(knee[0], knee[1], z), (ankle[0], ankle[1], z)], 5, 4)
        B += _chain([(ankle[0], ankle[1], z), (foot[0], foot[1], z)], 4, 3)
        for dz in (-3, 0, 3):
            B += _chain([(foot[0], 2, z + dz), (foot[0] + 14, 1, z + dz * 2)], 2.2, 1.5)
        B += _chain([(foot[0], 2, z), (foot[0] - 6, 1, z)], 1.6)
    # the ribs, and the gastralia across the belly
    for x in range(96, 142, 5):
        ys = 62 - max(0, x - 116) * 0.17
        for side in (-1, 1):
            B += _chain([(x, ys - 2, zc + side * 3), (x + 2, ys - 12, zc + side * 15), (x + 4, 38, zc + side * 11), (x + 5, 30, zc + side * 4)], 1.6)
    for x in range(100, 140, 5):
        B += _box(x, 28, zc - 6, x + 1.2, 29.2, zc + 6)
    # the little arms
    for side in (-1, 1):
        B += _chain([(140, 50, zc + side * 8), (150, 40, zc + side * 9), (156, 36, zc + side * 8)], 2.2, 1.4)
    # the skull: the cranium, the snout, the jaw a little open, the teeth
    B += _box(168, 64, zc - 7, 190, 82, zc + 7)
    B += _box(190, 62, zc - 5, 206, 74, zc + 5)
    B += _box(170, 54, zc - 6, 204, 60, zc + 6)
    for x in range(176, 204, 3):
        for side in (-1, 1):
            B += _box(x, 60, zc + side * 4.6 - 0.5, x + 1, 63, zc + side * 4.6 + 0.5)
    # holes in the skull: the eye and the windows, cut as dark boxes laid in (fenestrae)
    holes = [[175, 71, zc - 7.2, 182, 78, zc + 7.2], [191, 66, zc - 5.2, 198, 71, zc + 5.2]]
    # the steel armature from the plinth to the spine
    rods = _box(58, 0, zc - 1, 60, 52, zc + 1) + _box(126, 0, zc - 1, 128, 30, zc + 1)
    return B, holes, rods


def whale():
    """The blue whale's skeleton as it hangs in the ocean hall, head east (+x), 22 blocks long,
    six across (z 0..96, the spine at 48), four high."""
    zc = 48
    B = []
    spine = [(0, 40, zc), (60, 42, zc), (120, 48, zc), (180, 50, zc), (240, 46, zc), (262, 44, zc)]
    B += _chain(spine, 3, 10)
    for x in range(12, 250, 8):
        y = 40 + 10 * math.sin(math.pi * x / 300)
        B += _box(x - 1.2, y + 3, zc - 1.2, x + 1.2, y + 3 + (10 if x > 80 else 5), zc + 1.2)
        if 80 < x < 180:
            B += _box(x - 1, y - 1, zc - 12, x + 1, y + 1, zc + 12)
        if x < 140:
            B += _box(x - 0.8, y - 8, zc - 0.8, x + 0.8, y - 3, zc + 0.8)
    # the skull: the broad flat rostrum out to the tip, the jaw bows apart under it
    for k in range(10):
        x = 262 + k * 10
        half = 30 - k * 2.4
        B += _box(x, 38, zc - half, x + 10, 46, zc + half)
    for side in (-1, 1):
        B += _chain([(258, 30, zc + side * 30), (300, 30, zc + side * 26), (340, 32, zc + side * 16), (356, 34, zc + side * 6)], 4)
    # the ribs, long and curved
    for x in range(176, 250, 8):
        for side in (-1, 1):
            B += _chain([(x, 46, zc + side * 6), (x + 2, 36, zc + side * 34), (x + 6, 16, zc + side * 38), (x + 10, 4, zc + side * 30)], 2.2)
    # the flippers
    for side in (-1, 1):
        B += _chain([(236, 28, zc + side * 32), (226, 14, zc + side * 44), (210, 4, zc + side * 46)], 5, 2.5)
        for dx in (-4, 0, 4):
            B += _chain([(210, 4, zc + side * 46), (196 + dx, 0, zc + side * (46 + dx))], 1.6)
    return B


def _cells(boxes, name):
    """Splits a model's boxes over the blocks they fall in: {(bx, by, bz): [boxes in that block's 16ths]}."""
    cells = {}
    for b in boxes:
        x0, y0, z0, x1, y1, z1 = b[:6]
        tex = b[6] if len(b) > 6 else name
        for bx in range(int(math.floor(x0 / 16)), int(math.floor((x1 - 1e-6) / 16)) + 1):
            for by in range(int(math.floor(y0 / 16)), int(math.floor((y1 - 1e-6) / 16)) + 1):
                for bz in range(int(math.floor(z0 / 16)), int(math.floor((z1 - 1e-6) / 16)) + 1):
                    c = [max(x0, bx * 16) - bx * 16, max(y0, by * 16) - by * 16, max(z0, bz * 16) - bz * 16,
                         min(x1, bx * 16 + 16) - bx * 16, min(y1, by * 16 + 16) - by * 16, min(z1, bz * 16 + 16) - bz * 16]
                    if min(c[3] - c[0], c[4] - c[1], c[5] - c[2]) < 0.2:
                        continue
                    if by < 0:
                        continue
                    cells.setdefault((bx, by, bz), []).append([round(v, 2) for v in c] + [tex])
    return cells


@lru_cache(maxsize=None)
def skeletons():
    B, holes, rods = rex()
    r = _cells([b + ["mu_bone"] for b in B] + [h + ["mu_leather"] for h in holes] + [b + ["home_plastic_black"] for b in rods], "mu_bone")
    w = _cells([b + ["mu_whalebone"] for b in whale()], "mu_whalebone")
    return _zeroed(r), _zeroed(w)


def _zeroed(cells):
    """The cells moved so the lowest corner is 0, 0, 0 (block names take no minus)."""
    m = [min(k[i] for k in cells) for i in range(3)]
    return {(x - m[0], y - m[1], z - m[2]): v for (x, y, z), v in cells.items()}


def cells_text():
    r, w = skeletons()
    return {"rex": ";".join(f"{x},{y},{z}" for x, y, z in sorted(r)), "whale": ";".join(f"{x},{y},{z}" for x, y, z in sorted(w))}


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

    brass, oak, black, white, chrome = "cv_brass", "cv_oak", "home_plastic_black", "home_white", "home_chrome"

    def face(front, side):
        return {"north": front, "side": side, "up": side, "down": side}

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    # ---- the stone
    for name, title in (("mu_lime", "Limestone"), ("mu_lime_b", "Limestone"), ("mu_rustic", "Rusticated Stone")):
        add(name, None, title, "STONE", rotate=None, tex={"all": name})
    for i in range(25):
        add(f"mu_rose_{i}", None, "Marble Floor", "STONE", rotate=None, tex={"up": f"mu_rose_{i}", "side": "us_marble_a", "down": "us_marble_a"})

    # ---- plates
    for key, (_, _, cols) in NAMES.items():
        for i in range(cols):
            add(f"mu_name_{key}_{i}", plate(f"mu_name_{key}_{i}", 15.0), "Lettering", "STONE", decor=True)
    for key in SIGNS:
        for i in range(2):
            add(f"mu_sgn_{key}_{i}", plate(f"mu_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key in BANNERS:
        for i in range(3):
            add(f"mu_vbanner_{key}_{i}", plate(f"mu_vbanner_{key}_{i}", 14.0), "Banner", "WOOL", decor=True)
    for key in BACKDROPS:
        for i in range(12):
            add(f"mu_bd_{key}_{i}", plate(f"mu_bd_{key}_{i}", 15.6), "Painted Backdrop", "WOOL", decor=True)
    for key, (cols, rows) in ART.items():
        for i in range(cols * rows):
            add(f"mu_art_{key}_{i}", plate(f"mu_art_{key}_{i}", 14.6 if key != "gone" else 15.8), "Painting", "WOOD", decor=True)
    for i in range(8):
        add(f"mu_hiero_{i}", plate(f"mu_hiero_{i}", 15.4), "Tomb Relief", "STONE", decor=True)
    for i in range(2):
        add(f"mu_plan_{i}", plate(f"mu_plan_{i}"), "Museum Plan", "METAL", decor=True)
    for i in range(6):
        add(f"mu_parkmap_{i}", plate(f"mu_parkmap_{i}"), "Park Map", "WOOD", decor=True)
    for key in BOARDS:
        for i in range(3):
            add(f"mu_board_{key}_{i}", plate(f"mu_board_{key}_{i}", 15.0), "Painted Board", "WOOD", decor=True)
    add("mu_label", [[4, 4, 15.4, 12, 12, 16, "mu_label"]], "Label", "WOOL", decor=True)

    # ---- the dioramas
    add("mu_dio_glass", [[7.6, 0, 0, 8.4, 16, 16, "mu_case_glass"], [7, 0, 0, 9, 1.5, 16, brass], [7, 15, 0, 9, 16, 16, brass]],
        "Diorama Glass", "GLASS", rotate="axis", render="translucent", shape=[[7.6, 0, 0, 8.4, 16, 16]])
    add("mu_dio_ground", None, "Diorama Ground", "GRAVEL", rotate=None, tex={"up": "mu_dio_ground", "side": "mu_dio_rock", "down": "mu_dio_rock"})
    add("mu_dio_snow", None, "Diorama Snow", "GRAVEL", rotate=None, tex={"up": "mu_dio_snow", "side": "mu_dio_rock", "down": "mu_dio_rock"})
    add("mu_dio_rock", [[0, 0, 0, 16, 9, 16, "mu_dio_rock"], [2, 9, 3, 13, 14, 12, "mu_dio_rock"], [5, 14, 5, 10, 16, 10, "mu_dio_rock"]],
        "Rock", "STONE", rotate=None)

    # ---- the animals (facing: the way the head looks; the base model's head to the north, z = 0)
    bear, deer, wolf, bison, foxf, eye, antler = "mu_fur_bear", "mu_fur_deer", "mu_fur_wolf", "mu_fur_bison", "mu_fur_fox", black, "mu_antler"
    add("mu_bear_lo", [[4, 0, 6, 7, 10, 11, bear], [9, 0, 6, 12, 10, 11, bear], [3, 8, 4, 13, 16, 13, bear]], "Grizzly Bear", "WOOL")
    add("mu_bear_hi", [[3, 0, 4, 13, 9, 13, bear], [4, 9, 5, 12, 12, 11, bear], [5, 11, 2, 11, 16, 8, bear], [6.5, 12, 0, 9.5, 14, 2, bear],
                       [6.5, 13.5, -0.2, 9.5, 14.5, 0.5, eye], [5, 15, 4, 6.5, 17, 6, bear], [9.5, 15, 4, 11, 17, 6, bear],
                       [1, 4, 3, 3, 12, 6, bear], [13, 4, 3, 15, 12, 6, bear], [1, 2, 1, 3, 4, 4, "bunk_grey"], [13, 2, 1, 15, 4, 4, "bunk_grey"]],
        "Grizzly Bear", "WOOL", shape=[[3, 0, 2, 13, 16, 13]])
    add("mu_deer_lo", [[5, 0, 3, 6.5, 10, 4.5, deer], [9.5, 0, 3, 11, 10, 4.5, deer], [5, 0, 12, 6.5, 10, 13.5, deer], [9.5, 0, 12, 11, 10, 13.5, deer],
                       [4.5, 9, 2, 11.5, 16, 15, deer], [7, 13, 15, 9, 15, 16.5, white]], "White-tailed Deer", "WOOL", shape=[[4.5, 0, 2, 11.5, 16, 15]])
    add("mu_deer_hi", [[6, 0, 1, 10, 7, 5, deer], [6.5, 5, -2, 9.5, 9, 3, deer], [7, 5.5, -4, 9, 7.5, -2, deer], [5.5, 8, 2, 7, 10, 3, deer],
                       [9, 8, 2, 10.5, 10, 3, deer], [6.3, 7.5, -1, 6.6, 8.2, 0, eye], [9.4, 7.5, -1, 9.7, 8.2, 0, eye],
                       [6, 9, 0, 7, 15, 1, antler], [9, 9, 0, 10, 15, 1, antler], [3, 13, 0, 7, 14, 1, antler], [9, 13, 0, 13, 14, 1, antler],
                       [3, 14, 0, 4, 16, 1, antler], [12, 14, 0, 13, 16, 1, antler], [5, 11, 0, 6, 13, 1, antler], [10, 11, 0, 11, 13, 1, antler]],
        "White-tailed Deer", "WOOL", shape=[[6, 0, 0, 10, 9, 5]])
    add("mu_wolf", [[5, 0, 3, 6.5, 6, 4.5, wolf], [9.5, 0, 3, 11, 6, 4.5, wolf], [5, 0, 12, 6.5, 6, 13.5, wolf], [9.5, 0, 12, 11, 6, 13.5, wolf],
                    [4.5, 5, 2.5, 11.5, 11, 14, wolf], [5.5, 8, -1, 10.5, 13, 3, wolf], [6.5, 8, -4, 9.5, 11, -1, wolf], [7, 8, -4.5, 9, 9, -4, eye],
                    [5.5, 12.5, 0, 7, 15, 1.5, wolf], [9, 12.5, 0, 10.5, 15, 1.5, wolf], [7, 7, 14, 9, 9, 19, wolf]],
        "Grey Wolf", "WOOL", shape=[[4.5, 0, 0, 11.5, 13, 14]])
    add("mu_fox", [[6, 0, 5, 7, 4, 6, foxf], [9, 0, 5, 10, 4, 6, foxf], [6, 0, 11, 7, 4, 12, foxf], [9, 0, 11, 10, 4, 12, foxf],
                   [5.5, 3.5, 4, 10.5, 7.5, 13, foxf], [6, 6, 1, 10, 9.5, 4.5, foxf], [7, 6, -1, 9, 8, 1, white], [6, 9.5, 2, 7, 11, 3, foxf],
                   [9, 9.5, 2, 10, 11, 3, foxf], [7, 4, 13, 9, 7, 17, foxf], [7.3, 4.2, 16, 8.7, 6, 18, white]], "Red Fox", "WOOL",
        shape=[[5.5, 0, 1, 10.5, 9.5, 13]])
    add("mu_bison_0", [[4, 0, 6, 6, 8, 8.5, bison], [10, 0, 6, 12, 8, 8.5, bison], [3, 6, 2, 13, 16, 16, bison], [4, 15, 4, 12, 18, 14, bison],
                       [5, 4, -3, 11, 12, 3, bison], [6, 4, -5, 10, 9, -3, bison], [3, 10, -1, 5, 11, 1, antler], [11, 10, -1, 13, 11, 1, antler],
                       [3, 11, -1, 4, 13, 0, antler], [12, 11, -1, 13, 13, 0, antler], [5.7, 8, -3.2, 6.2, 9, -2.6, eye], [9.8, 8, -3.2, 10.3, 9, -2.6, eye]],
        "American Bison", "WOOL", shape=[[3, 0, -3, 13, 16, 16]])
    add("mu_bison_1", [[4.5, 0, 9, 6.5, 8, 11, bison], [9.5, 0, 9, 11.5, 8, 11, bison], [4, 7, 0, 12, 14, 12, bison], [7.5, 6, 12, 8.5, 12, 13, bison]],
        "American Bison", "WOOL", shape=[[4, 0, 0, 12, 14, 13]])
    feather, fw = "mu_feather", "mu_feather_white"
    add("mu_eagle", [[2, 0, 6, 14, 2, 9, "tree_oak_bark"], [6, 2, 6, 10, 10, 10, feather], [6.5, 9, 6, 9.5, 13, 9, fw], [7, 10, 4, 9, 12, 6, "mu_gold"],
                     [3, 5, 6.5, 6, 11, 11, feather], [10, 5, 6.5, 13, 11, 11, feather], [7, 2, 10, 9, 6, 13, fw], [6.5, 1.5, 6.5, 7.5, 2.5, 8, "mu_gold"],
                     [8.5, 1.5, 6.5, 9.5, 2.5, 8, "mu_gold"]], "Bald Eagle", "WOOL", shape=[[2, 0, 6, 14, 13, 13]])
    add("mu_owl", [[4, 0, 6, 12, 2, 9, "tree_oak_bark"], [6, 2, 6, 10, 9, 10, "mu_fur_deer"], [6, 8, 5.5, 10, 12, 10, "mu_fur_deer"],
                   [6.5, 9.5, 5.3, 7.8, 10.8, 5.6, "mu_gold"], [8.2, 9.5, 5.3, 9.5, 10.8, 5.6, "mu_gold"], [6, 12, 6, 7, 13.5, 7, "mu_fur_deer"],
                   [9, 12, 6, 10, 13.5, 7, "mu_fur_deer"]], "Great Horned Owl", "WOOL", shape=[[4, 0, 5, 12, 13, 10]])

    # ---- the cases: one-block vitrines on their oak bases, flat table cases
    vit = {
        "gems": [[4, 9, 5, 6, 11, 7, "mu_top_gems"], [8, 9, 4, 10, 10.5, 6, "mu_top_gems"], [7, 9, 9, 9, 12, 11, "mu_top_gems"], [10, 9, 9, 12, 10, 11, "mu_top_jewels"]],
        "minerals": [[3, 9, 4, 7, 13, 8, "mu_top_minerals"], [9, 9, 6, 12, 12, 10, "mu_dio_rock"], [5, 9, 10, 8, 11, 12, "mu_top_minerals"]],
        "shells": [[3, 9, 4, 7, 10.5, 8, "mu_top_shells"], [9, 9, 5, 13, 11, 9, "mu_top_shells"], [6, 9, 10, 10, 10, 13, "mu_top_shells"]],
        "birds": [[4, 9, 7, 6, 13, 9, "mu_feather"], [4.2, 13, 6, 5.8, 14, 8, "mu_gold"], [10, 9, 6, 12, 12, 8, "mu_feather_white"], [7, 9, 10, 9, 11.5, 12, "mu_fur_fox"]],
        "pots": [[4, 9, 5, 7, 13, 8, "mu_blackfig"], [4.5, 13, 5.5, 6.5, 14, 7.5, "mu_blackfig"], [9, 9, 8, 12, 12, 11, "mu_terracotta"]],
        "jars": [[3, 9, 6, 5, 12, 8, "mu_terracotta"], [6, 9, 6, 8, 12, 8, "mu_gold"], [9, 9, 6, 11, 12, 8, "mu_terracotta"], [12, 9, 6, 14, 12, 8, "mu_lapis"]],
        "smashed": [[5, 9, 6, 6, 9.5, 7, "glass_shards"]],
    }
    for key, inside in vit.items():
        glass = "mu_case_glass_broken" if key == "smashed" else "mu_case_glass"
        boxes = [[1, 0, 1, 15, 8, 15, oak], [0.5, 8, 0.5, 15.5, 9, 15.5, oak], [1, 9, 1, 15, 16, 15, glass], [0.8, 15.6, 0.8, 15.2, 16, 15.2, brass]]
        add(f"mu_vit_{key}", boxes + inside, "Display Case", "GLASS", rotate=None, render="translucent", shape=[[0.5, 0, 0.5, 15.5, 16, 15.5]],
            **({"loot": "pawn"} if key in ("gems",) else {}))
    for key in ("fossils", "coins", "jewels", "smashed"):
        top = "mu_case_glass_broken" if key == "smashed" else "mu_case_glass"
        felt = "mu_top_fossils" if key == "smashed" else f"mu_top_{key}"
        add(f"mu_table_{key}", [[1, 0, 1, 3, 9, 3, oak], [13, 0, 1, 15, 9, 3, oak], [1, 0, 13, 3, 9, 15, oak], [13, 0, 13, 15, 9, 15, oak],
                                [0.5, 9, 0.5, 15.5, 11, 15.5, {"up": felt, "side": oak, "down": oak}], [0.5, 11, 0.5, 15.5, 14, 15.5, top]],
            "Table Case", "GLASS", rotate=None, render="translucent", shape=[[0.5, 0, 0.5, 15.5, 14, 15.5]],
            **({"loot": "pawn"} if key == "jewels" else {}))

    # ---- the skeletons, a block per cell of each model
    r, w = skeletons()
    for prefix, cells, title in (("mu_rex", r, "Tyrannosaurus Skeleton"), ("mu_whale", w, "Blue Whale Skeleton")):
        for (x, y, z), bx in cells.items():
            add(f"{prefix}_{x}_{y}_{z}", bx, title, "STONE", rotate=None, decor=True)
    add("mu_cable", [[7.8, 0, 7.8, 8.2, 16, 8.2, chrome]], "Steel Cable", "METAL", rotate=None, decor=True)
    add("mu_dino_base", [[0, 0, 0, 16, 6, 16, {"up": "mu_dio_rock", "side": black, "down": black}]], "Plinth", "STONE", rotate=None)
    add("mu_plinth", [[2, 0, 2, 14, 16, 14, white], [1.5, 0, 1.5, 14.5, 1, 14.5, "bunk_grey"]], "Plinth", "WOOD", rotate=None)

    # ---- the ancient world
    gold, lapis = "mu_gold", "mu_lapis"
    add("mu_sarc_0", [[1, 0, 0, 15, 9, 16, {"up": "mu_coffin_hi", "side": "ch_marble", "down": "ch_marble"}], [2, 9, 2, 14, 11, 16, {"up": "mu_coffin_hi", "side": gold, "down": gold}],
                      [4, 11, 3, 12, 13, 8, gold], [5, 13, 3.5, 11, 13.5, 7.5, "mu_coffin_hi"]], "Sarcophagus", "STONE")
    add("mu_sarc_1", [[1, 0, 0, 15, 9, 16, {"up": "mu_coffin_lo", "side": "ch_marble", "down": "ch_marble"}], [2, 9, 0, 14, 11, 13, {"up": "mu_coffin_lo", "side": gold, "down": gold}],
                      [5, 11, 9, 11, 13, 13, gold]], "Sarcophagus", "STONE")
    add("mu_coffin_lo", [[3, 0, 5, 13, 16, 12, {"north": "mu_coffin_lo", "side": lapis, "up": lapis, "down": lapis}], [2, 0, 4, 14, 2, 13, black]],
        "Mummy Case", "WOOD")
    add("mu_coffin_hi", [[3, 0, 5, 13, 10, 12, {"north": "mu_coffin_hi", "side": lapis, "up": lapis, "down": lapis}],
                         [4, 10, 5.5, 12, 14, 11.5, {"north": "mu_coffin_hi", "side": lapis, "up": gold, "down": lapis}]], "Mummy Case", "WOOD")
    add("mu_canopic", [[1, 0, 1, 15, 2, 15, "ch_marble"]] + [[x, 2, z, x + 4, 8, z + 4, "mu_terracotta"] for x, z in ((2, 2), (10, 2), (2, 10), (10, 10))]
        + [[x + 0.5, 8, z + 0.5, x + 3.5, 10.5, z + 3.5, gold] for x, z in ((2, 2), (10, 2), (2, 10), (10, 10))], "Canopic Jars", "STONE", rotate=None)
    add("mu_amphora", [[5, 0, 5, 11, 2, 11, "mu_terracotta"], [4, 2, 4, 12, 9, 12, "mu_terracotta"], [5, 9, 5, 11, 11, 11, "mu_terracotta"],
                       [6.5, 11, 6.5, 9.5, 15, 9.5, "mu_terracotta"], [6, 15, 6, 10, 16, 10, "mu_terracotta"], [3, 10, 7.5, 4.5, 14, 8.5, "mu_terracotta"],
                       [11.5, 10, 7.5, 13, 14, 8.5, "mu_terracotta"]], "Amphora", "STONE", rotate=None)
    add("mu_urn", [[5, 0, 5, 11, 1.5, 11, "mu_blackfig"], [3.5, 1.5, 3.5, 12.5, 10, 12.5, "mu_blackfig"], [5, 10, 5, 11, 13, 11, "mu_blackfig"],
                   [4, 13, 4, 12, 14.5, 12, "mu_blackfig"]], "Painted Vase", "STONE", rotate=None)
    steel = chrome
    add("mu_armour_lo", [[1, 0, 1, 15, 1, 15, oak], [5, 1, 6, 7.5, 12, 9.5, steel], [8.5, 1, 6, 11, 12, 9.5, steel], [4.5, 1, 5, 7.5, 2.5, 10, steel],
                         [8.5, 1, 5, 11.5, 2.5, 10, steel], [4.5, 11, 5, 11.5, 16, 10.5, "mu_mail"], [7.5, 0, 12, 8.5, 16, 13, oak]], "Suit of Armor", "METAL",
        shape=[[1, 0, 1, 15, 16, 15]])
    add("mu_armour_hi", [[4.5, 0, 5, 11.5, 7, 10.5, steel], [2.5, 4, 6, 4.5, 7, 9.5, steel], [11.5, 4, 6, 13.5, 7, 9.5, steel],
                         [2.8, -4, 6.5, 4.2, 4, 9, steel], [11.8, -4, 6.5, 13.2, 4, 9, steel], [5.5, 7, 5.5, 10.5, 13, 10.5, steel],
                         [6, 10, 5.2, 10, 10.6, 5.5, black], [7.5, 13, 7.5, 8.5, 15, 8.5, "mu_velvet"], [7.5, 0, 12, 8.5, 9, 13, oak],
                         [13.5, -8, 7, 14.2, 15, 7.8, oak], [12.8, 12, 6.3, 15, 16, 8.5, steel]], "Suit of Armor", "METAL", shape=[[2.5, 0, 5, 13.5, 13, 10.5]])
    add("mu_swords", [[1, 4, 15, 15, 12, 16, oak], [3, 6, 14.2, 13, 6.8, 15, steel], [3, 9, 14.2, 13, 9.8, 15, steel], [12, 5, 14, 13, 11, 15, brass]],
        "Swords", "METAL", decor=True)
    add("mu_shield", [[3, 2, 14.4, 13, 14, 16, {"north": "cv_cushion_red", "side": steel, "up": steel, "down": steel}], [7, 4, 14, 9, 12, 14.4, gold],
                      [4, 7, 14, 12, 9, 14.4, gold]], "Shield", "METAL", decor=True)
    add("mu_bust", [[4, 0, 4, 12, 3, 12, "ch_marble"], [4.5, 3, 5, 11.5, 8, 11, "ch_marble"], [5.5, 8, 5.5, 10.5, 14, 11, "ch_marble"],
                    [7, 9, 4.5, 9, 11, 5.5, "ch_marble"]], "Marble Bust", "STONE")

    # ---- furniture
    add("mu_stanchion", [[7, 0, 7, 9, 1, 9, brass], [7.5, 1, 7.5, 8.5, 9, 8.5, brass], [7, 9, 7, 9, 10, 9, brass],
                         [7.6, 7.5, 0, 8.4, 8.3, 16, "mu_velvet"]], "Rope Barrier", "METAL", rotate="axis", shape=[[7, 0, 0, 9, 10, 16]])
    add("mu_ottoman", [[1, 0, 1, 15, 3, 15, black], [0, 3, 0, 16, 7, 16, "mu_leather"]], "Gallery Bench", "WOOL", rotate="axis")
    add("mu_crate", [[0.5, 0, 0.5, 15.5, 15, 15.5, {"north": "mu_crate", "south": "mu_crate", "side": "mu_crate_plain", "up": "mu_crate_plain", "down": "mu_crate_plain"}]],
        "Packing Crate", "WOOD", loot="storage")
    add("mu_crate_open", [[0.5, 0, 0.5, 15.5, 13, 15.5, {"north": "mu_crate", "south": "mu_crate", "side": "mu_crate_plain", "up": "mu_straw", "down": "mu_crate_plain"}],
                          [-3, 0, 2, -1, 15, 14, "mu_crate_plain"]], "Packing Crate", "WOOD", loot="storage")
    add("mu_crate_flat", [[0, 0, 9, 16, 16, 14, {"north": "mu_crate", "south": "mu_crate_plain", "side": "mu_crate_plain", "up": "mu_crate_plain", "down": "mu_crate_plain"}]],
        "Painting Crate", "WOOD", shape=[[0, 0, 9, 16, 16, 14]])

    # ---- the park
    add("mu_gravel", None, "Gravel Path", "GRAVEL", rotate=None, tex={"all": "mu_gravel"})
    add("mu_gravel_b", None, "Gravel Path", "GRAVEL", rotate=None, tex={"up": "mu_gravel_b", "side": "mu_gravel", "down": "mu_gravel"})
    add("mu_boards", None, "Boardwalk", "WOOD", rotate="axis", tex={"all": "mu_boards"})
    wi = "mu_white_iron"
    add("mu_rail", [[7, 14, 0, 9, 15.5, 16, wi], [7.2, 0, 0, 8.8, 1.2, 16, wi], [7.4, 1, 1, 8.6, 14, 2.2, wi], [7.4, 1, 13.8, 8.6, 14, 15, wi],
                    [7.6, 1, 7.4, 8.4, 14, 8.6, wi], [7.6, 9, 2, 8.4, 9.8, 14, wi], [7.6, 3, 4.5, 8.4, 9, 5.3, wi], [7.6, 3, 10.7, 8.4, 9, 11.5, wi]],
        "Railing", "METAL", rotate="axis", render="cutout")
    gi = "mu_green_iron"
    add("mu_bs_post", [[6, 0, 6, 10, 2, 10, gi], [6.8, 2, 6.8, 9.2, 14, 9.2, gi], [6, 13, 6, 10, 14.5, 10, gi], [7, 14.5, 1, 9, 15.5, 15, gi],
                       [1, 14.5, 7, 15, 15.5, 9, gi], [7.4, 11, 2, 8.6, 14.5, 3, gi], [7.4, 11, 13, 8.6, 14.5, 14, gi]], "Bandstand Column", "METAL",
        rotate=None, shape=[[6.8, 0, 6.8, 9.2, 16, 9.2]])
    add("mu_music_stand", [[7.5, 0, 7.5, 8.5, 9, 8.5, black], [5, 0, 5, 11, 0.5, 11, black], [3, 9, 7, 13, 15, 8, black], [3.5, 10, 6.6, 12.5, 14.5, 7, "paper"]],
        "Music Stand", "METAL", decor=True)
    for suffix, tex in (("", "mu_gh_glass"), ("_broken", "mu_gh_glass_broken")):
        add(f"mu_gh_glass{suffix}", None, "Glasshouse Glass", "GLASS", rotate=None, tex={"all": tex}, render="translucent")
    add("mu_gh_post", [[6, 0, 6, 10, 16, 10, wi], [5, 0, 5, 11, 1.5, 11, wi]], "Iron Column", "METAL", rotate=None)
    add("mu_palm_trunk", [[5.5, 0, 5.5, 10.5, 16, 10.5, "mu_palm_bark"], [5, 4, 5, 11, 5, 11, "mu_palm_bark"], [5, 11, 5, 11, 12, 11, "mu_palm_bark"]],
        "Palm", "WOOD", rotate=None)
    crown = [[5, 0, 5, 11, 4, 11, "mu_palm_bark"],
             [-16, 2, 0, 8, 2.5, 16, "mu_frond", None, {"axis": "z", "angle": -22.5, "origin": [8, 4, 8]}],
             [8, 2, 0, 32, 2.5, 16, "mu_frond", None, {"axis": "z", "angle": 22.5, "origin": [8, 4, 8]}],
             [0, 2, -16, 16, 2.5, 8, "mu_frond", None, {"axis": "x", "angle": 22.5, "origin": [8, 4, 8]}],
             [0, 2, 8, 16, 2.5, 32, "mu_frond", None, {"axis": "x", "angle": -22.5, "origin": [8, 4, 8]}],
             [-8, 6, -8, 24, 6.5, 24, "mu_frond", None, {"axis": "y", "angle": 45, "origin": [8, 8, 8]}]]
    add("mu_palm_crown", crown, "Palm", "GRASS", rotate=None, decor=True, render="cutout")
    add("mu_fern", [[0, 0, 7.9, 16, 12, 8.1, "mu_fern", None, {"axis": "y", "angle": 45, "origin": [8, 8, 8]}],
                    [7.9, 0, 0, 8.1, 12, 16, "mu_fern", None, {"axis": "y", "angle": 45, "origin": [8, 8, 8]}],
                    [-2, 4, 0, 18, 4.5, 16, "mu_fern", None, {"axis": "z", "angle": 22.5, "origin": [8, 4, 8]}]], "Fern", "GRASS", rotate=None,
        decor=True, render="cutout")
    add("mu_bench", [[0, 6, 4, 16, 7, 12, {"all": "mu_boards"}], [0, 8, 12.5, 16, 15, 13.5, "mu_boards"], [1, 0, 4, 2.5, 7, 12, gi],
                     [13.5, 0, 4, 15, 7, 12, gi], [1, 7, 12, 2.5, 15, 13.5, gi], [13.5, 7, 12, 15, 15, 13.5, gi], [1, 9, 4, 2.5, 10, 12, gi],
                     [13.5, 9, 4, 15, 10, 12, gi]], "Park Bench", "WOOD", shape=[[0, 0, 4, 16, 8, 13.5]])
    add("mu_icecream", [[1, 4, 2, 15, 14, 14, {"north": "mu_icecream", "south": "mu_icecream", "side": "home_white", "up": "fit_stainless", "down": black}],
                        [0, 0, 1, 1.5, 6, 6, black], [14.5, 0, 1, 16, 6, 6, black], [0, 0, 10, 16, 1, 12, black], [7.5, 14, 7.5, 8.5, 16, 8.5, chrome]],
        "Ice Cream Cart", "METAL", loot="drinks")
    add("mu_parasol", [[7.5, 0, 7.5, 8.5, 12, 8.5, chrome], [-6, 12, -6, 22, 13, 22, "mu_parasol"], [-2, 13, -2, 18, 14.5, 18, "mu_parasol"],
                       [5, 14.5, 5, 11, 15.5, 11, "mu_parasol"]], "Parasol", "WOOL", rotate=None, decor=True)
    hull = "mu_boat_green"
    add("mu_boat_0", [[2, -3, 0, 14, 4, 16, {"up": "mu_boards", "side": hull, "down": hull}], [1, 2, 0, 2.5, 6, 16, hull], [13.5, 2, 0, 15, 6, 16, hull],
                      [2, 2, 12, 14, 4.5, 15, "mu_boards"], [6, -3, -6, 10, 6, 0, hull], [4, -2, -3, 6, 5, 0, hull], [10, -2, -3, 12, 5, 0, hull]],
        "Rowboat", "WOOD", shape=[[1, 0, 0, 15, 6, 16]])
    add("mu_boat_1", [[2, -3, 0, 14, 4, 16, {"up": "mu_boards", "side": hull, "down": hull}], [1, 2, 0, 2.5, 6, 16, hull], [13.5, 2, 0, 15, 6, 16, hull],
                      [2, 2, 4, 14, 4.5, 7, "mu_boards"], [3, -3, 16, 13, 6, 18, hull], [0, 5, 6, 16, 5.6, 6.6, "mu_boards"]], "Rowboat", "WOOD",
        shape=[[1, 0, 0, 15, 6, 16]])
    add("mu_lifering", [[3, 3, 15, 13, 5, 16, "mu_lifering"], [3, 11, 15, 13, 13, 16, "mu_lifering"], [3, 5, 15, 5, 11, 16, "mu_lifering"],
                        [11, 5, 15, 13, 11, 16, "mu_lifering"]], "Life Ring", "WOOL", decor=True)
    add("mu_oars", [[3, 0, 13, 4, 16, 14, "mu_boards"], [2.5, 0, 12.5, 4.5, 4, 14.5, "mu_boards"], [6, 0, 13.5, 7, 16, 14.5, "mu_boards"],
                    [5.5, 0, 13, 7.5, 4, 15, "mu_boards"]], "Oars", "WOOD", decor=True)
    return out


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "cells":
        for k, v in cells_text().items():
            print(k, v)
        return
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("mu_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} museum and park blocks in {path}")


if __name__ == "__main__":
    main()
