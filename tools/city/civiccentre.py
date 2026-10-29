"""Grayhaven Civic Center: the cc_ kit for City Hall, the council chamber and the plaza
(world/county/CivicCentre.java, CivicTower.java, CivicChamber.java).

No photos of it in the library yet (city/civic-center-* to be queued): this follows what the
American city halls of the late sixties are known for, as they stood in the eighties. A slab
tower of white exposed-aggregate precast, its fins running the full height between deep-set
bronze glass, a colonnade under it at the plaza; the council chamber a concrete drum beside it
with its saucer roof; the plaza of pink-grey granite with the long reflecting pool, the flags and
a weathering-steel sculpture nobody liked. The last weeks left their own: the state's relief
station in tents on the plaza, the missing posters on the plywood, the vote board still showing
the quarantine ordinance, the emergency operations center on the top floor.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- outside: cc_plaza, cc_plaza_b, cc_plaza_band, cc_coping, cc_precast, cc_precast_b, cc_spandrel,
  cc_travertine, cc_corten, cc_corten_plate, cc_fin, cc_fin_cap, cc_win, cc_win_lit, cc_win_broken;
- flags: cc_flag_city, cc_flag_city_top (on a stand), cc_pole_state_top, cc_pole_city_top (on a
  flagpole);
- inside: cc_ticket, cc_serving, cc_serving_on, cc_detector_lo, cc_detector_hi, cc_xray, cc_model,
  cc_model_b;
- plates: cc_seal_0..8 (three by three, bronze), cc_name_KEY_i (one row), cc_sgn_KEY_0..1 (two by
  one), cc_vote_0..7 (four by two), cc_qmap_0..11 (four by three), cc_missing_0..7 (four by two),
  cc_banner_0..5 (six by one), cc_proclamation, cc_plaque, cc_spray_KEY_0..2 (three by one).

    python3 tools/city/civiccentre.py        rewrites the cc_ entries in blocks.json

photo_textures.py draws the textures (civiccentre.register()).
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
from furniture import area, disc, drips, stain  # noqa: E402
from school import Pic, HAND, OBLIQUE  # noqa: E402
from hospital import letters  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
SERIF = "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"
SPRAY = "/usr/share/fonts/truetype/freefont/FreeSansBoldOblique.ttf"
NAVY = (28, 44, 82)
CREAM = (236, 230, 212)
BRONZE = (132, 96, 52)
YELLOW = (226, 178, 30)

# City Hall's signs, two by one: text, field, ink
SIGNS = {
    "clerk": ("CITY CLERK", NAVY, CREAM),
    "tax": ("TAX COLLECTOR", NAVY, CREAM),
    "cashier": ("CASHIER", NAVY, CREAM),
    "info": ("INFORMATION", NAVY, CREAM),
    "security": ("SECURITY", NAVY, CREAM),
    "mail": ("MAIL ROOM", NAVY, CREAM),
    "permits": ("PERMITS & LICENSES", NAVY, CREAM),
    "assessor": ("ASSESSOR", NAVY, CREAM),
    "records": ("RECORDS", NAVY, CREAM),
    "works": ("PUBLIC WORKS", NAVY, CREAM),
    "planning": ("PLANNING", NAVY, CREAM),
    "data": ("DATA PROCESSING", NAVY, CREAM),
    "cafeteria": ("CAFETERIA", NAVY, CREAM),
    "personnel": ("PERSONNEL", NAVY, CREAM),
    "attorney": ("CITY ATTORNEY", NAVY, CREAM),
    "mayor": ("OFFICE OF THE MAYOR", NAVY, CREAM),
    "eoc": ("EMERGENCY OPERATIONS", (150, 26, 26), (250, 246, 236)),
    "council": ("COUNCIL CHAMBER", NAVY, CREAM),
    "relief": ("RELIEF STATION", YELLOW, (20, 20, 20)),
    "register": ("REGISTRATION", YELLOW, (20, 20, 20)),
    "water": ("WATER - 1 PER PERSON", YELLOW, (20, 20, 20)),
    "aid": ("FIRST AID", (30, 120, 60), (255, 255, 255)),
    "staff": ("STAFF ONLY", (120, 24, 28), (255, 255, 255)),
}
# the buildings' names, one row: text, font, cols, style (bronze, carved)
NAMES = {
    "hall": ("GRAYHAVEN CITY HALL", BOLD, 16, "bronze"),
    "council": ("COUNCIL CHAMBER", BOLD, 10, "bronze"),
    "plaza": ("CIVIC CENTER PLAZA", SERIF, 12, "carved"),
}
SPRAYS = {
    "mayor": ("WHERE IS THE MAYOR", 3),
    "lied": ("THEY LIED", 3),
    "coming": ("NOBODY IS COMING", 3),
}


# ---------------------------------------------------------------- outside

def plaza(seed, cracked=False, band=False):
    """The plaza's granite: pink-grey flamed setts two to a block in a running bond, the joints
    gone dark; the band in black granite; cracked, a slab sunk and the weeds through."""
    base = (52, 52, 56) if band else (168, 150, 142)
    t = F.plain(seed, base, 0.05, 0.25)
    rng = t.rng
    t.vary(0.06)
    t.speckle(0.35, (110, 96, 92) if not band else (90, 90, 96), 0.35)
    t.speckle(0.12, (220, 210, 200) if not band else (130, 130, 136), 0.4)
    for gy in (0, 32):
        for gx in (0, 32):
            shade = rng.uniform(0.9, 1.08)
            area(t, gx, gy, gx + 32, gy + 32, mul=shade)
            area(t, gx, gy, gx + 32, gy + 1, col=(70, 64, 60), dh=-0.3)
            area(t, gx, gy, gx + 1, gy + 32, col=(70, 64, 60), dh=-0.3)
    if cracked:
        t.crack(3, 0.3, (40, 36, 30))
        m = np.zeros((N, N), np.float32)
        m[0:2, :] = 1
        m[:, 0:2] = 1
        m[32:34, :] = 1
        t.mix(m * (rng.random((N, N)) < 0.5), (70, 90, 44), 0.8)
        stain(t, rng.uniform(10, 54), rng.uniform(10, 54), rng.uniform(8, 14), (70, 60, 50), 0.35)
    t.grime(0.25 if cracked else 0.12)
    return t


def precast(seed, stained=False):
    """White exposed-aggregate precast: the pebbles showing through the wash, the panel joint,
    the rain's streaks under every ledge."""
    t = F.plain(seed, (214, 208, 194), 0.04, 0.15)
    t.vary(0.05)
    t.speckle(0.4, (176, 166, 150), 0.45, 0.08)
    t.speckle(0.18, (120, 112, 100), 0.4, 0.12)
    t.speckle(0.1, (236, 232, 224), 0.5, 0.1)
    area(t, 0, 0, N, 1, mul=0.82, dh=-0.2)
    t.streaks(0.9 if stained else 0.45, 0.6 if stained else 0.8)
    if stained:
        t.grime(0.3)
        stain(t, t.rng.uniform(0, N), t.rng.uniform(30, N), t.rng.uniform(8, 16), (110, 112, 90), 0.3)
    return t


def spandrel(seed):
    """The bronze-anodised spandrel panel between the windows, two pans with their stiffeners."""
    t = F.plain(seed, (78, 58, 40), 0.03, 0.55)
    t.f0[:] = 200
    for y0 in (0, 32):
        F.edges(t, 1, y0 + 1, N - 1, y0 + 31, w=1)
    area(t, 0, 31, N, 33, col=(46, 34, 24), dh=-0.2)
    t.streaks(0.5, 0.7)
    t.grime(0.12, (40, 40, 40))
    return t


def bronze_glass(seed, lit=False, broken=False):
    """The tower's glass: a dark bronze tint, the sky's sheen down it, opaque enough to read as glass from the plaza."""
    t = P.glass(seed, (70, 54, 40) if not lit else (236, 196, 130), lit=0.8 if lit else 0.0, broken=broken)
    if not lit:
        pane = (t.alpha > 0) & (t.alpha < 255)
        t.alpha[pane] = 238
        y = np.arange(N)[:, None] / N
        t.c += (np.array((70, 76, 84), np.float32) * np.clip(0.6 - y, 0, 1)[..., None]) * pane[..., None]
    return t


def travertine(seed):
    """Travertine in the lobby: the cream stone, its holes and veins running across."""
    t = F.plain(seed, (214, 198, 168), 0.05, 0.4)
    rng = t.rng
    for _ in range(9):
        y = rng.uniform(0, N)
        area(t, 0, int(y), N, int(y) + 1, mul=0.86)
    for _ in range(40):
        x, y = rng.uniform(0, N, 2)
        area(t, int(x), int(y), int(x) + int(rng.integers(2, 6)), int(y) + 1, col=(150, 132, 104), dh=-0.3)
    area(t, 0, 0, N, 1, mul=0.8)
    area(t, 0, 0, 1, N, mul=0.8)
    t.grime(0.1)
    return t


def corten(seed):
    """Weathering steel: the rust's orange-brown crust, darker where the water sits, its run-off."""
    t = F.plain(seed, (122, 62, 32), 0.08, 0.1)
    t.vary(0.12, tint=(20, 6, -6))
    t.speckle(0.3, (90, 42, 22), 0.5, -0.05)
    t.speckle(0.15, (160, 92, 46), 0.4, 0.05)
    t.streaks(0.7, 0.7)
    t.f0[:] = 30
    return t


def flag_city(seed):
    """The city flag: a white field, the navy band at the hoist with the lighthouse, gold fringe."""
    t = P.Tex(seed, (226, 222, 210))
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    t.c[xx < 20] = NAVY
    lh = (np.abs(xx - 10) < 2 + (yy - 16) / 20) & (yy > 16) & (yy < 44)
    t.c[lh] = (236, 230, 212)
    t.c[(np.abs(xx - 10) < 4) & (yy > 12) & (yy < 17)] = (240, 200, 90)
    for k in range(3):
        wave = (np.abs(yy - (50 + k * 4) - 1.5 * np.sin(xx / 3)) < 0.8) & (xx > 24)
        t.c[wave] = NAVY
    fold = np.cos(xx / N * 2 * np.pi * 4)
    t.c *= (0.8 + 0.2 * fold)[..., None]
    t.c[yy > N - 5] = (196, 162, 80)
    t.c = t.c * 0.85 + 22
    t.smooth[:] = 0.1
    t.alpha[(yy > N - 2) & ((xx % 3) == 0)] = 0
    return t


# ---------------------------------------------------------------- pictures

@lru_cache(maxsize=None)
def seal_pic():
    """The city's seal in bronze, three by three: the lighthouse on its rock over the waves, the
    ship under sail, CITY OF GRAYHAVEN round it."""
    p = Pic(3, 3, (0, 0, 0, 0), 18500)
    W = p.W
    p.ellipse(4, 4, W - 4, W - 4, fill=(120, 88, 48), outline=(70, 50, 28), width=2)
    p.ellipse(28, 28, W - 28, W - 28, fill=(140, 104, 58), outline=(70, 50, 28), width=1.2)
    cx = W / 2
    p.poly([(cx - 9, 128), (cx + 9, 128), (cx + 5, 70), (cx - 5, 70)], fill=(196, 160, 96))
    p.rect(cx - 7, 62, cx + 7, 70, fill=(220, 186, 110))
    p.poly([(cx - 8, 62), (cx + 8, 62), (cx, 52)], fill=(176, 140, 80))
    for k in (84, 104):
        p.rect(cx - 6, k, cx + 6, k + 5, fill=(110, 80, 44))
    p.poly([(cx - 26, 140), (cx + 26, 140), (cx + 14, 126), (cx - 14, 126)], fill=(104, 76, 40))
    for a in (-0.5, 0.5):
        p.line([(cx, 66), (cx + math.copysign(34, a), 66 + abs(a) * 10)], (230, 200, 130), 1.2)
    p.poly([(56, 116), (76, 116), (72, 122), (60, 122)], fill=(96, 70, 38))
    p.poly([(66, 92), (66, 114), (76, 114)], fill=(196, 162, 98))
    for k in range(3):
        y = 146 + k * 7
        p.line([(48 + j * 8, y + (j % 2) * 3) for j in range(13)], (90, 64, 36), 1.4)
    text = "CITY  OF  GRAYHAVEN  *  INCORPORATED  1838  *"
    r = W / 2 - 15
    for k, ch in enumerate(text):
        a = math.pi * 2 * k / len(text) - math.pi / 2
        p.text(cx + math.cos(a) * r, cx + math.sin(a) * r, ch, SERIF, 7.5, (210, 176, 110), anchor="mm")
    return p


def seal(i):
    t = seal_pic().tile(i, 18510 + i, smooth=0.45, grime=0.15)
    t.f0[t.alpha > 128] = 230
    t.streaks(0.4)
    return t


@lru_cache(maxsize=None)
def name_pic(key):
    text, font, cols, style = NAMES[key]
    bg = (150, 140, 134, 255) if style == "carved" else (0, 0, 0, 0)
    p = Pic(cols, 1, bg, 18600 + len(key))
    if style == "carved":
        p.rect(0, 0, p.W, 3, fill=(120, 112, 108))
        p.rect(0, N - 3, p.W, N, fill=(120, 112, 108))
        size = civic.fit(text, font, 44, p.W - 30)
        p.text(p.W / 2 + 1, N / 2 + 1, text, font, size, (200, 190, 184), anchor="mm")
        p.text(p.W / 2, N / 2, text, font, size, (46, 40, 38), anchor="mm")
    else:
        letters(p, text, font, 6, 6, p.W - 6, N - 6, (150, 108, 56), True, stroke=0.12)
    return p


def name(key, i):
    style = NAMES[key][3]
    t = name_pic(key).tile(i, 18610 + i + len(key) * 23, smooth=0.5 if style != "carved" else 0.2, grime=0.15)
    if style == "bronze":
        t.f0[t.alpha > 128] = 230
    else:
        t.streaks(0.5)
    return t


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 18700 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 16, W - 1, H - 16, fill=field)
    p.rect(3, 18, W - 3, H - 18, outline=tuple(min(255, v + 50) for v in field), width=0.5)
    font = BOLD
    p.text(W / 2, H / 2, text, font, civic.fit(text, font, 14, W - 16), ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 18750 + i + len(key) * 11, smooth=0.5, grime=0.12)


# the council as it voted on the last night: name, aye
COUNCIL = [("HARTWELL", None), ("ABERNATHY", True), ("DUNMORE", True), ("KESSLER", False), ("OKAFOR", True),
           ("PRUITT", True), ("LINDQVIST", False), ("VASQUEZ", True), ("TOBIN", True), ("McCREADY", True)]


@lru_cache(maxsize=None)
def vote_pic():
    """The vote board, four by two: the ordinance and every member's light, ayes green, nays red."""
    p = Pic(4, 2, (24, 24, 26), 18800)
    W, H = p.W, p.H
    p.rect(2, 2, W - 2, H - 2, outline=(90, 90, 92), width=1.5)
    p.text(W / 2, 12, "CITY COUNCIL  -  VOTE", BOLD, 8, (230, 200, 120), anchor="mm")
    p.text(W / 2, 24, "ORD. 87-14  EMERGENCY QUARANTINE OF ZONES A AND B", BOLD, 5.2, (220, 220, 210), anchor="mm")
    for k, (who, aye) in enumerate(COUNCIL[1:]):
        col, row = k % 3, k // 3
        x, y = 22 + col * 80, 40 + row * 24
        p.text(x + 10, y + 4, who, BOLD, 6, (220, 220, 210), anchor="lm")
        light = (60, 230, 90) if aye else (240, 60, 50)
        p.ellipse(x - 4, y, x + 4, y + 8, fill=light)
        p.text(x + 62, y + 4, "AYE" if aye else "NAY", BOLD, 5, light, anchor="lm")
    p.text(W / 2, H - 14, "AYES 7    NAYS 2    -    CARRIED", BOLD, 8, (60, 230, 90), anchor="mm")
    return p


def vote(i):
    t = vote_pic().tile(i, 18810 + i, smooth=0.5, grime=0.08)
    lum = t.c.max(-1)
    sat = t.c.max(-1) - t.c.min(-1)
    t.emit[:] = np.where((lum > 150) & (sat > 60), 0.9, 0.0)
    return t


@lru_cache(maxsize=None)
def qmap_pic():
    """The quarantine map in the operations room, four by three: the island and the county shore,
    the zones hatched in red and grease pencil, the pins, what they wrote on the acetate."""
    p = Pic(4, 3, (210, 222, 226), 18900)
    W, H = p.W, p.H
    rng = p.rng
    p.rect(0, 0, W, 14, fill=(36, 36, 40))
    p.text(W / 2, 7, "GRAYHAVEN COUNTY  -  QUARANTINE ZONES  (EOC)", BOLD, 7, (240, 236, 220), anchor="mm")
    # the mainland down the west and south, the island in the harbor
    p.poly([(0, 14), (70, 14), (64, 60), (80, 110), (60, 150), (90, 180), (W, 170), (W, H), (0, H)], fill=(222, 216, 190))
    p.poly([(120, 40), (170, 30), (196, 60), (190, 120), (160, 150), (126, 130), (112, 80)], fill=(226, 220, 196))
    for x in range(0, W, 16):
        p.line([(x, 14), (x, H)], (190, 190, 180), 0.3)
    for y in range(14, H, 16):
        p.line([(0, y), (W, y)], (190, 190, 180), 0.3)
    # zone A: the island's south end, hatched; zone B the shore across
    p.poly([(126, 100), (190, 92), (190, 120), (160, 150), (126, 130)], fill=(230, 120, 110), a=150)
    for k in range(10):
        p.line([(124 + k * 7, 150), (150 + k * 7, 96)], (180, 30, 30), 0.8, a=200)
    p.poly([(60, 150), (90, 180), (150, 176), (130, 150)], fill=(240, 180, 90), a=150)
    p.text(156, 112, "ZONE A", BLACK, 8, (150, 20, 20), anchor="mm")
    p.text(100, 170, "ZONE B", BLACK, 7, (150, 90, 10), anchor="mm")
    p.ellipse(150, 52, 170, 66, fill=(120, 200, 120), a=180)
    p.text(160, 72, "STADIUM SHELTER", BOLD, 4.2, (20, 90, 30), anchor="mm")
    p.line([(64, 60), (118, 70)], (30, 30, 30), 1.2)
    p.line([(80, 110), (114, 100)], (30, 30, 30), 1.2)
    for x, y in ((90, 64), (96, 105)):
        p.text(x, y - 5, "X", BLACK, 8, (200, 20, 20), anchor="mm")
    p.scrawl(12, 30, "BRIDGES CLOSED|0400 23rd", HAND, 6, (200, 20, 20), 0.25)
    p.scrawl(200, 140, "lost contact|Mercy 0310", HAND, 6, (30, 30, 160), 0.25)
    p.scrawl(14, 120, "GUARD pulled|back to|stadium??", HAND, 6, (30, 30, 30), 0.3)
    p.scrawl(196, 30, "ZONE A = NO|ENTRY - Mayor", HAND, 5.5, (200, 20, 20), 0.25)
    for _ in range(22):
        x, y = rng.uniform(110, 200), rng.uniform(40, 160)
        p.ellipse(x - 2, y - 2, x + 2, y + 2, fill=(220, 30, 30) if rng.random() < 0.7 else (30, 60, 200))
    p.rect(0, 0, W, H, outline=(60, 60, 60), width=2)
    return p


def qmap(i):
    return qmap_pic().tile(i, 18910 + i, smooth=0.35, grime=0.15)


@lru_cache(maxsize=None)
def missing_pic():
    """The missing wall, four by two: snapshots and notes taped over each other on the plywood."""
    p = Pic(4, 2, (176, 144, 98), 19000)
    W, H = p.W, p.H
    rng = p.rng
    for _ in range(500):
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        p.line([(x, y), (x + rng.uniform(4, 14), y)], (150, 120, 80), 0.4)
    first = ["MARY", "DALE", "JOSH", "KIM", "RAY", "ANN", "TOMMY", "LUIS", "BETH", "CARL", "DEB", "NINA", "WES", "JUNE"]
    last = ["HOLT", "REYES", "PARK", "GREER", "MOSS", "LANE", "FOX", "PIKE", "WARD", "COLE"]
    for k in range(34):
        x, y = rng.uniform(-4, W - 24), rng.uniform(-4, H - 30)
        if rng.random() < 0.55:
            p.rect(x, y, x + 22, y + 28, fill=(236, 232, 220), outline=(170, 160, 150), width=0.3)
            sky = tuple(int(v) for v in rng.integers(60, 200, 3))
            p.rect(x + 2, y + 2, x + 20, y + 18, fill=sky)
            p.ellipse(x + 7, y + 5, x + 15, y + 14, fill=(200, 160, 130))
            p.rect(x + 6, y + 14, x + 16, y + 18, fill=tuple(int(v) for v in rng.integers(30, 160, 3)))
            p.text(x + 11, y + 22, rng.choice(first), BOLD, 3.2, (30, 30, 30), anchor="mm")
        else:
            col = (255, 255, 255) if rng.random() < 0.6 else (250, 240, 170)
            p.rect(x, y, x + 26, y + 20, fill=col, outline=(170, 160, 150), width=0.3)
            p.text(x + 13, y + 3, "MISSING" if rng.random() < 0.6 else "HAVE YOU SEEN", BLACK, 3.4, (180, 20, 20), anchor="ma")
            p.text(x + 13, y + 9, rng.choice(first) + " " + rng.choice(last), BOLD, 3, (30, 30, 30), anchor="ma")
            p.text(x + 13, y + 14, "555-01" + str(int(rng.integers(10, 99))), BOLD, 2.6, (30, 30, 30), anchor="ma")
        p.rect(x + 8, y - 1, x + 14, y + 2, fill=(220, 216, 196), a=200)
    p.scrawl(30, H - 20, "WE ARE AT THE STADIUM - MOM", HAND, 7, (30, 30, 120), 0.3)
    return p


def missing(i):
    return missing_pic().tile(i, 19010 + i, smooth=0.2, grime=0.2)


@lru_cache(maxsize=None)
def banner_pic():
    p = Pic(6, 1, (24, 64, 140), 19100)
    W, H = p.W, p.H
    p.rect(2, 4, W - 2, H - 4, outline=(250, 250, 250), width=1)
    p.text(W / 2, 22, "STATE EMERGENCY RELIEF", BLACK, civic.fit("STATE EMERGENCY RELIEF", BLACK, 20, W - 40), (250, 250, 250), anchor="mm")
    p.text(W / 2, 46, "REGISTRATION  -  WATER  -  FIRST AID  -  MISSING PERSONS", BOLD, 7.5, (250, 220, 90), anchor="mm")
    return p


def banner(i):
    t = banner_pic().tile(i, 19110 + i, smooth=0.1, grime=0.2)
    yy = np.arange(N)[:, None]
    t.c *= (0.9 + 0.1 * np.cos(np.arange(N)[None, :] / N * 2 * np.pi * 2))[..., None]
    if i == 5:
        t.alpha[(yy > 40 + 6 * np.sin(np.arange(N)[None, :] / 5)) & (np.arange(N)[None, :] > 34)] = 0  # torn
    return t


def proclamation(seed):
    p = Pic(1, 1, (240, 236, 224), seed)
    W = p.W
    p.text(W / 2, 6, "CITY OF GRAYHAVEN", BOLD, 4.2, (30, 30, 30), anchor="mm")
    p.text(W / 2, 13, "EMERGENCY|PROCLAMATION No. 3", BLACK, 4.4, (170, 20, 20), anchor="ma")
    p.text(W / 2, 27, "Curfew 6 PM - 6 AM.|Residents of Zones A|and B report to the|Civic Center relief|station to register.", BOLD, 3.1, (30, 30, 30), anchor="ma")
    p.text(W / 2, 58, "- E. Hartwell, Mayor", OBLIQUE, 3.0, (30, 30, 30), anchor="mm")
    t = p.tile(0, seed + 1, smooth=0.2, grime=0.2)
    t.streaks(0.4)
    return t


def plaque(seed):
    """The sculpture's bronze plaque: THE MARINER, the artist, 1972."""
    t = F.plain(seed, (120, 88, 50), 0.05, 0.55)
    t.f0[:] = 220
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(32, 16, "THE MARINER", SERIF, 7, (220, 190, 130), anchor="mm")
    p.text(32, 30, "R. Okonkwo-Hale|1972|Cor-Ten steel|Gift of the|Harbor Trust", SERIF, 4.2, (210, 180, 120), anchor="ma")
    m = p.tile(0, seed, smooth=0.3, grime=0)
    a = m.alpha / 255
    t.c[:] = t.c * (1 - a[..., None]) + m.c * a[..., None]
    F.edges(t, 1, 1, 63, 63, w=2)
    t.streaks(0.5)
    return t


def serving(seed, on):
    """NOW SERVING over the counter: red seven-segment digits, dead when the power is."""
    t = F.plain(seed, (30, 30, 32), 0.02, 0.4)
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(32, 14, "NOW SERVING", BOLD, 6, (220, 220, 210), anchor="mm")
    p.rect(10, 22, 54, 52, fill=(16, 8, 8))
    p.text(32, 37, "47", BLACK, 22, (255, 40, 30) if on else (70, 24, 22), anchor="mm")
    m = p.tile(0, seed, smooth=0.3, grime=0)
    a = m.alpha / 255
    t.c[:] = t.c * (1 - a[..., None]) + m.c * a[..., None]
    if on:
        t.emit[:] = np.where(t.c[..., 0] > 180, 1.0, 0.0) * (t.c[..., 1] < 100)
    F.edges(t, 0, 0, 64, 64, w=2)
    return t


def model_top(seed):
    """The model of downtown under its dusty case: the grid of streets, the river, green squares."""
    t = F.plain(seed, (196, 192, 180), 0.03, 0.4)
    for k in range(0, N, 16):
        area(t, k, 0, k + 3, N, col=(90, 90, 92))
        area(t, 0, k, N, k + 3, col=(90, 90, 92))
    area(t, 35, 35, 48, 48, col=(90, 140, 70))
    area(t, 0, 52, N, 58, col=(70, 110, 150))
    t.grime(0.2, (150, 150, 140))
    return t


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 19200 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (210, 24, 30) if key != "coming" else (30, 30, 30), jitter=0.4, a=235, angle=-2)
    return p


def spray(key, i):
    t = spray_pic(key).tile(i, 19210 + i + len(key) * 5, smooth=0.3, grime=0.0)
    lum = t.alpha > 20
    for _ in range(4):
        x, y = int(t.rng.integers(4, 60)), int(t.rng.integers(30, 50))
        if lum[y, x]:
            drips(t, x, x + 1, y, 1, (4, 12), col=(180, 20, 20) if key != "coming" else (30, 30, 30))
            t.alpha[y:y + 12, x:x + 1] = 230
    return t


def textures():
    T = {}
    T["cc_plaza"] = lambda: plaza(18400)
    T["cc_plaza_b"] = lambda: plaza(18401, cracked=True)
    T["cc_plaza_band"] = lambda: plaza(18402, band=True)
    T["cc_precast"] = lambda: precast(18403)
    T["cc_precast_b"] = lambda: precast(18404, stained=True)
    T["cc_spandrel"] = lambda: spandrel(18405)
    T["cc_glass"] = lambda: bronze_glass(18406)
    T["cc_glass_lit"] = lambda: bronze_glass(18407, lit=True)
    T["cc_glass_broken"] = lambda: bronze_glass(18408, broken=True)
    T["cc_travertine"] = lambda: travertine(18409)
    T["cc_corten"] = lambda: corten(18410)
    T["cc_flag_city"] = lambda: flag_city(18411)
    T["cc_proclamation"] = lambda: proclamation(18412)
    T["cc_plaque"] = lambda: plaque(18413)
    T["cc_serving"] = lambda: serving(18414, False)
    T["cc_serving_on"] = lambda: serving(18415, True)
    T["cc_model"] = lambda: model_top(18416)
    for i in range(9):
        T[f"cc_seal_{i}"] = lambda i=i: seal(i)
    for key, (_, _, cols, _) in NAMES.items():
        for i in range(cols):
            T[f"cc_name_{key}_{i}"] = lambda key=key, i=i: name(key, i)
    for key in SIGNS:
        for i in range(2):
            T[f"cc_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for i in range(8):
        T[f"cc_vote_{i}"] = lambda i=i: vote(i)
        T[f"cc_missing_{i}"] = lambda i=i: missing(i)
    for i in range(12):
        T[f"cc_qmap_{i}"] = lambda i=i: qmap(i)
    for i in range(6):
        T[f"cc_banner_{i}"] = lambda i=i: banner(i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"cc_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
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

    steel, chrome, black, white, brass = "hs_steel", "home_chrome", "home_plastic_black", "home_white", "cv_brass"
    grey = "bunk_grey"

    def face(front, side):
        return {"north": front, "side": side, "up": side, "down": side}

    # ---- the plaza and the walls
    for name in ("cc_plaza", "cc_plaza_b", "cc_plaza_band"):
        add(name, None, "Granite Paving", "STONE", rotate=None, tex={"up": name, "side": "cc_plaza_band", "down": "floor_concrete"})
    add("cc_coping", [[0, 0, 0, 16, 7, 16, {"up": "cc_plaza_band", "side": "cc_plaza_band", "down": "floor_concrete"}]],
        "Granite Coping", "STONE", rotate=None, shape=[[0, 0, 0, 16, 7, 16]])
    for name in ("cc_precast", "cc_precast_b", "cc_spandrel", "cc_travertine", "cc_corten"):
        title = {"cc_spandrel": "Spandrel Panel", "cc_travertine": "Travertine", "cc_corten": "Weathering Steel"}.get(name, "Precast Concrete")
        add(name, None, title, "METAL" if name in ("cc_spandrel", "cc_corten") else "STONE", rotate=None, tex={"all": name})
    # a plate of the sculpture, standing on edge along x (facing: which way it leans)
    add("cc_corten_plate", [[0, 0, 6, 16, 16, 10, "cc_corten"]], "Weathering Steel", "METAL", shape=[[0, 0, 6, 16, 16, 10]])
    # the tower's fins: a precast blade standing out from the wall behind it (its back at z = 16)
    add("cc_fin", [[6, 0, 3, 10, 16, 16, "cc_precast"]], "Precast Fin", "STONE", shape=[[6, 0, 3, 10, 16, 16]])
    add("cc_fin_cap", [[6, 0, 3, 10, 8, 16, "cc_precast"], [6, 8, 8, 10, 13, 16, "cc_precast"], [5, 13, 2, 11, 16, 16, "cc_precast_b"]],
        "Precast Fin", "STONE", shape=[[6, 0, 3, 10, 16, 16]])
    # the deep-set window: a thin precast reveal round a bronze pane set back six (facing: out); side by side
    # between the piers they read as one band of glass
    for suffix, glass, light in (("", "cc_glass", None), ("_lit", "cc_glass_lit", 8), ("_broken", "cc_glass_broken", None)):
        add(f"cc_win{suffix}", [[0, 0, 0, 0.75, 16, 16, "cc_precast"], [15.25, 0, 0, 16, 16, 16, "cc_precast"],
                                [0.75, 15, 0, 15.25, 16, 6, "cc_precast"], [0.75, 0, 0, 15.25, 1.5, 6, "cc_precast_b"],
                                [0.75, 1.5, 5.6, 15.25, 2.2, 6.6, "cc_spandrel"], [0.75, 14.4, 5.6, 15.25, 15, 6.6, "cc_spandrel"],
                                [0.75, 2.2, 6, 15.25, 14.4, 6.2, glass], [0.75, 0, 6, 15.25, 1.5, 16, "cc_precast"],
                                [0.75, 15, 6, 15.25, 16, 16, "cc_precast"]],
            "Window", "GLASS", render="translucent", shape=[[0, 0, 0, 16, 16, 16]], **({"light": light} if light else {}))

    # ---- the flags
    add("cc_flag_city", [[7.25, 0, 7.25, 8.75, 16, 8.75, brass], [6, 0, 6, 10, 1.5, 10, brass], [8.75, 2, 7.5, 16, 15.5, 8.5, "cc_flag_city"]],
        "Flag", "WOOL", decor=True, render="cutout")
    add("cc_flag_city_top", [[7.25, 0, 7.25, 8.75, 8, 8.75, brass], [8.75, 0, 7.5, 16, 6, 8.5, "cc_flag_city"], [6.5, 8, 6.5, 9.5, 11, 9.5, brass],
                             [5, 10.5, 7.5, 11, 12, 8.5, brass]], "Flag", "WOOL", decor=True, render="cutout")
    for key, tex in (("state", "cv_flag_state"), ("city", "cc_flag_city")):
        add(f"cc_pole_{key}_top", [[7.25, 0, 7.25, 8.75, 13, 8.75, "ext_trim_white"], [6.5, 13, 6.5, 9.5, 16, 9.5, brass],
                                   [8.75, 1, 7.6, 32, 12.5, 8.4, tex]], "Flagpole", "METAL", decor=True, render="cutout")

    # ---- the lobby and the counters
    add("cc_ticket", [[7, 0, 7, 9, 10, 9, steel], [5, 0, 5, 11, 1, 11, steel], [5, 10, 6, 11, 15, 10, "dn_vinyl_r"],
                      [6.5, 11, 5.4, 9.5, 12, 6, "home_white"]], "Take a Number", "METAL", shape=[[5, 0, 5, 11, 15, 11]])
    for suffix, light in (("", None), ("_on", 6)):
        add(f"cc_serving{suffix}", [[1, 2, 13, 15, 14, 16, face(f"cc_serving{suffix}", black)], [7.5, 14, 14, 8.5, 16, 15, steel]],
            "Now Serving", "METAL", decor=True, **({"light": light} if light else {}))
    # the walk-through metal detector: two posts and the head, lo the posts, hi the head over them
    add("cc_detector_lo", [[0, 0, 3, 2.5, 16, 13, {"all": "home_plastic_black", "north": "bunk_grey"}], [13.5, 0, 3, 16, 16, 13, {"all": "home_plastic_black", "north": "bunk_grey"}],
                           [0, 0, 2, 2.5, 1, 14, grey], [13.5, 0, 2, 16, 1, 14, grey]], "Metal Detector", "METAL",
        shape=[[0, 0, 3, 2.5, 16, 13], [13.5, 0, 3, 16, 16, 13]])
    add("cc_detector_hi", [[0, 0, 3, 2.5, 13, 13, "home_plastic_black"], [13.5, 0, 3, 16, 13, 13, "home_plastic_black"],
                           [0, 13, 3, 16, 16, 13, grey], [6, 13.5, 2.6, 10, 15.5, 3, "dn_vinyl_r"]], "Metal Detector", "METAL",
        shape=[[0, 0, 3, 2.5, 13, 13], [13.5, 0, 3, 16, 13, 13], [0, 13, 3, 16, 16, 13]])
    add("cc_xray", [[0, 0, 1, 16, 9, 15, grey], [0, 9, 3, 16, 10, 13, black], [3, 10, 2, 13, 16, 14, face("hs_screen", grey)],
                    [3, 10, 1.6, 13, 15.5, 2, "home_plastic_black"]], "Baggage X-Ray", "METAL")
    # the model of downtown on its table under the glass case (two kinds of block, either way round)
    for suffix, towers in (("", ((2, 2, 8), (10, 4, 14), (4, 10, 5), (12, 12, 9))), ("_b", ((3, 4, 11), (9, 2, 6), (11, 10, 16), (2, 12, 7)))):
        boxes = [[0, 0, 1, 16, 9, 15, face("cv_oak", "cv_oak")], [0, 9, 0, 16, 10, 16, {"up": "cc_model", "side": "cv_oak", "down": "cv_oak"}]]
        for x, z, h in towers:
            boxes.append([x, 10, z, x + 3, 10 + h * 0.5, z + 3, "home_white"])
        boxes.append([0.2, 10, 0.2, 15.8, 16, 15.8, "tx_glass"])
        add(f"cc_model{suffix}", boxes, "City Model", "WOOD", rotate=None, render="translucent")

    # ---- plates on the walls (the back at z = 16)
    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    for i in range(9):
        add(f"cc_seal_{i}", plate(f"cc_seal_{i}", 14.8), "City Seal", "METAL", decor=True)
    for key, (_, _, cols, style) in NAMES.items():
        for i in range(cols):
            add(f"cc_name_{key}_{i}", plate(f"cc_name_{key}_{i}", 14.6 if style != "carved" else 15.0), "Lettering", "METAL", decor=True)
    for key in SIGNS:
        for i in range(2):
            add(f"cc_sgn_{key}_{i}", plate(f"cc_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for i in range(8):
        add(f"cc_vote_{i}", plate(f"cc_vote_{i}", 14.6), "Vote Board", "METAL", decor=True, light=4)
        add(f"cc_missing_{i}", plate(f"cc_missing_{i}", 15.6), "Missing Posters", "WOOL", decor=True)
    for i in range(12):
        add(f"cc_qmap_{i}", plate(f"cc_qmap_{i}", 15.4), "Quarantine Map", "WOOL", decor=True)
    for i in range(6):
        add(f"cc_banner_{i}", plate(f"cc_banner_{i}", 15.6), "Banner", "WOOL", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"cc_spray_{key}_{i}", plate(f"cc_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)
    add("cc_proclamation", plate("cc_proclamation", 15.7), "Proclamation", "WOOL", decor=True)
    add("cc_plaque", [[3, 3, 14.6, 13, 13, 16, "cc_plaque"]], "Plaque", "METAL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("cc_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} civic center blocks in {path}")


if __name__ == "__main__":
    main()
