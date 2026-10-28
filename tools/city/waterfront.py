"""The Waterfront: the wf_ kit for Grayhaven island's harbour front (world/county/Waterfront.java,
WaterfrontTerminal.java, WaterfrontPier.java).

No photos of it in the library yet (city/waterfront-* to be queued): this follows the harbour fronts
the old ports rebuilt in the 1970s and 80s for the tourists. A granite seawall with an iron rail and
a promenade of brick pavers along it; the ferry terminal, a steel and glass shed with a clock tower,
its commuter ferry tied up alongside; a timber pier of little shingled shops (clams, taffy, gifts,
the arcade, the chowder house) with a carousel under a striped tent at its end; the marina's floats
and its boats, the harbormaster's office, the bait shop, the dry stack shed. The last weeks: the
terminal was the island's evacuation point, the queues held back by barriers and soldiers until the
last ferry went; then the crowd took what boats were left.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- the front: wf_pavers, wf_pavers_b, wf_pavers_moss, wf_cap, wf_wall, wf_wall_slime, wf_rail_iron,
  wf_fender, wf_telescope, wf_ring_post, wf_buoy_red, wf_buoy_green, wf_pots, wf_coil;
- the pier and floats: wf_deck, wf_deck_b, wf_pile, wf_pile_top, wf_stringer (axis), wf_rail,
  wf_float, wf_cleat, wf_gangway (stairs, facing the way up);
- the terminal: wf_steel, wf_panel, wf_panel_b, wf_glass, wf_glass_broken, wf_glass_lit, wf_ticket, wf_seats,
  wf_turnstile, wf_queue (axis), wf_queue_post, wf_bags_a, wf_bags_b, wf_bags_c, wf_name_0..13, wf_dep_0..7
  (four by two), wf_evac_0..5, wf_ferry_0..2 (the line's board over the gate);
- the pier's shops: wf_shingle, wf_shingle_b, wf_clap, wf_trim, wf_window, wf_freezer, wf_taffy,
  wf_tees, wf_gifts, wf_menu_0..1, wf_shop_KEY_0..3, wf_pier_0..5 (the arch's board, three by two);
- the carousel: wf_ride_floor, wf_horse_a, wf_horse_b, wf_horse_c, wf_brass, wf_canopy_a, wf_canopy_b,
  wf_drum, wf_crest;
- the marina: wf_pump, wf_rack (axis), wf_cover, wf_outboard, wf_mast, wf_boom (axis), wf_windshield;
- wf_sgn_KEY_0..1, wf_spray_KEY_i.

    python3 tools/city/waterfront.py          rewrites the wf_ entries in blocks.json

photo_textures.py draws the textures (waterfront.register()).
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
GRANITE = (150, 148, 142)
CREAM = (232, 222, 196)
NAVY = (28, 46, 74)
WHITE = (244, 242, 236)
INK = (30, 28, 26)
RED = (176, 36, 32)

# the pier's shops: the name, the board's colour, the lettering's, the face
SHOPS = {
    "clams": ("CLAM SHACK", (232, 226, 210), (30, 70, 110), OLD),
    "taffy": ("SALTWATER TAFFY", (236, 160, 180), (110, 30, 60), OLD),
    "gifts": ("HARBOR GIFTS", (30, 70, 110), CREAM, OLD),
    "arcade": ("SEA DOG ARCADE", (24, 24, 30), (250, 200, 60), BOLD),
    "chowder": ("CHOWDER HOUSE", (120, 30, 28), CREAM, OLD),
    "scoops": ("SCOOPS ICE CREAM", (250, 236, 200), (190, 60, 100), BOLD),
    "tees": ("PIER TEES", (40, 120, 140), WHITE, BOLD),
    "surf": ("SURF & SPORT", (240, 170, 40), (20, 30, 40), BOLD),
    "bait": ("BAIT & TACKLE", (60, 80, 50), CREAM, STENCIL),
    "master": ("HARBORMASTER", (30, 46, 70), WHITE, OLD),
}
SIGNS = {
    "tickets": ("TICKETS", NAVY, WHITE),
    "gate": ("BOARDING GATE", NAVY, WHITE),
    "wait": ("WAITING ROOM", NAVY, WHITE),
    "rest": ("RESTROOMS", NAVY, WHITE),
    "nowake": ("NO WAKE ZONE", WHITE, RED),
    "marina": ("MARINA OFFICE", NAVY, WHITE),
    "fuel": ("FUEL DOCK", (220, 180, 40), INK),
    "nodive": ("NO DIVING", WHITE, RED),
    "nofish": ("NO FISHING FROM PIER", WHITE, RED),
    "line": ("FORM ONE LINE", (220, 180, 40), INK),
    "family": ("FAMILIES WITH CHILDREN", (220, 180, 40), INK),
    "staff": ("STAFF ONLY", (170, 30, 26), WHITE),
    "storage": ("DRY STACK STORAGE", NAVY, WHITE),
}
SPRAYS = {
    "nomore": ("NO MORE BOATS", 4),
    "gone": ("LAST FERRY GONE", 4),
    "swim": ("SWIM FOR IT", 3),
    "help": ("HELP", 2),
}


# ---------------------------------------------------------------- the front

def pavers(seed, broken=False, moss=False):
    """Red brick pavers in herringbone, sanded joints, some sunk and stained."""
    t = F.plain(seed, (150, 76, 58), 0.05, 0.3)
    rng = t.rng
    area(t, 0, 0, N, N, col=(110, 100, 86), h=0.3)
    for k in range(-4, 8):
        for j in range(0, 8):
            x, y = k * 16 + j * 8, j * 8
            col = mixc((156, 74, 54), (112, 56, 44), rng.uniform(0, 1))
            col = mixc(col, (90, 80, 76), rng.uniform(0, 0.25))
            area(t, x + 1, y + 1, x + 15, y + 7, col=col, h=0.85, smooth=0.35)
            area(t, x + 9, y + 1 - 8, x + 15, y + 15 - 8, col=mixc(col, (130, 64, 50), 0.3), h=0.85, smooth=0.35)
    if broken:
        x, y = rng.integers(6, 30), rng.integers(6, 30)
        area(t, x, y, x + 22, y + 16, col=(70, 64, 56), h=0.4)
        t.speckle(0.06, (50, 46, 40), 0.5)
    if moss:
        m = np.clip(P.fbm(rng) * 1.5 + 0.1, 0, 1) * (t.h < 0.5)
        t.mix(m.astype(np.float32), (70, 92, 48), 0.8)
    t.grime(0.3, (40, 36, 30))
    return t


def granite(seed, slime=False, cap=False):
    """The seawall's granite, rough-faced; slime has the weed and the tide's green on it; the cap
    is the coping stone's top, tooled, with the iron rail's holes leaded."""
    t = F.plain(seed, GRANITE, 0.06, 0.3)
    rng = t.rng
    t.speckle(0.2, (80, 80, 82), 0.5, -0.03)
    t.speckle(0.08, (210, 208, 200), 0.4, 0.03)
    if cap:
        for y in range(0, N, 3):
            area(t, 0, y, N, y + 1, mul=0.94, dh=-0.05)
        disc(t, 32, 32, 3, col=(60, 62, 66))
    else:
        area(t, 0, 0, N, 2, col=(96, 94, 90), dh=-0.3)
        area(t, 0, 32, N, 34, col=(96, 94, 90), dh=-0.3)
        area(t, 20, 2, 22, 32, col=(96, 94, 90), dh=-0.3)
        area(t, 50, 34, 52, N, col=(96, 94, 90), dh=-0.3)
        t.h += P.fbm(rng, ((8, 1.0), (16, 0.6))) * 0.08
    y = np.arange(N)[:, None] / N
    if slime:
        t.mix(np.clip(P.fbm(rng) * 0.8 + y * 0.9 - 0.1, 0, 1).astype(np.float32) * np.ones((1, N), np.float32), (52, 70, 40), 0.8)
        t.speckle(0.05, (230, 230, 220), 0.6, 0.05)  # barnacles
        t.smooth[:] = 0.6
    t.streaks(0.5, 0.8)
    t.grime(0.25, (40, 40, 36))
    return t


def iron(seed):
    t = F.plain(seed, (34, 36, 38), 0.04, 0.5)
    t.speckle(0.06, (110, 70, 40), 0.5)
    return t


def rubber(seed):
    t = F.plain(seed, (30, 30, 32), 0.04, 0.2)
    t.speckle(0.04, (200, 200, 190), 0.3)
    return t


def buoy(seed, rgb):
    t = F.plain(seed, rgb, 0.05, 0.45)
    y = np.arange(N)[:, None] / N
    t.mix(np.clip((y - 0.7) * 3, 0, 1).astype(np.float32) * np.ones((1, N), np.float32), (50, 66, 40), 0.9)
    t.streaks(0.4, 0.8)
    return t


def net(seed, rgb=(170, 120, 60)):
    t = F.plain(seed, rgb, 0.05, 0.2)
    t.alpha[:] = 0
    for k in range(0, N, 6):
        area(t, k, 0, k + 1, N, col=rgb, alpha=255)
        area(t, 0, k, N, k + 1, col=mixc(rgb, (0, 0, 0), 0.15), alpha=255)
    return t


def rope(seed):
    t = F.plain(seed, (190, 170, 120), 0.05, 0.2)
    for k in range(-N, N, 4):
        for x in range(N):
            y = x + k
            if 0 <= y < N:
                area(t, x, y, x + 1, y + 1, mul=0.75)
    return t


# ---------------------------------------------------------------- the pier and floats

def deck(seed, dark=False):
    """The pier's planks across it, gone silver, the nail lines rusted, a gap between each."""
    t = F.wood(seed, "pine")
    rng = t.rng
    t.c = t.c * 0.5 + np.array((138, 132, 120), np.float32) * 0.5
    if dark:
        t.c *= 0.72
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, col=(30, 28, 26), dh=-0.4)
        area(t, 0, y + 1, N, y + 16, mul=rng.uniform(0.85, 1.1))
        for x in (6, 58):
            disc(t, x, y + 5, 0.8, col=(90, 50, 30))
            disc(t, x, y + 11, 0.8, col=(90, 50, 30))
    t.grime(0.3, (50, 46, 40))
    t.speckle(0.02, (230, 230, 226), 0.5)  # gull droppings
    return t


def creosote(seed):
    t = F.wood(seed, "dark", rot=True)
    t.c *= 0.55
    t.streaks(0.4, 0.7)
    return t


def painted_wood(seed, rgb, flake=0.2):
    t = F.wood(seed, "pine", rot=True)
    t.c = t.c * 0.15 + np.array(rgb, np.float32) * 0.85
    t.vary(0.03)
    rng = t.rng
    for _ in range(int(flake * 30)):
        x, y = rng.integers(0, 60), rng.integers(0, 60)
        area(t, x, y, x + rng.integers(2, 6), y + rng.integers(1, 3), col=(130, 120, 100), dh=-0.1)
    t.grime(0.25, (60, 56, 48))
    return t


def float_drum(seed):
    t = F.plain(seed, (40, 40, 44), 0.04, 0.4)
    t.mix(np.clip(P.fbm(t.rng) * 1.2, 0, 1).astype(np.float32), (60, 76, 44), 0.6)
    return t


# ---------------------------------------------------------------- the terminal

def steel_paint(seed, rgb):
    t = F.plain(seed, rgb, 0.04, 0.55)
    t.f0[:] = 25
    t.speckle(0.03, (120, 70, 40), 0.5)
    t.streaks(0.3, 0.85)
    return t


def panel(seed, stained=False):
    """Cream metal cladding in tall ribbed panels."""
    t = F.plain(seed, (222, 214, 192), 0.03, 0.5)
    for x in range(0, N, 8):
        area(t, x, 0, x + 2, N, mul=0.86, dh=0.15)
        area(t, x + 2, 0, x + 3, N, mul=1.08)
    if stained:
        t.streaks(0.9, 0.7)
        t.grime(0.4, (70, 64, 50))
    else:
        t.grime(0.15, (70, 64, 50))
    return t


def curtain(seed, lit=False, broken=False):
    """Curtain wall: the big panes tinted green-blue, a mullion either side and a transom."""
    t = F.plain(seed, (110, 136, 140), 0.03, 0.95)
    y = np.arange(N)[:, None] / N
    t.c[:] = np.array((96, 128, 136), np.float32) * (0.8 + 0.35 * (1 - y))[..., None]
    t.alpha[:] = 120
    t.f0[:] = 40
    if lit:
        t.c[:] = mixc((230, 210, 160), (150, 140, 110), 0.3)
        t.alpha[:] = 210
        t.emit[:] = 0.5
    area(t, 0, 0, 3, N, col=NAVY, alpha=255, h=0.9, f0=20)
    area(t, N - 3, 0, N, N, col=NAVY, alpha=255, h=0.9, f0=20)
    area(t, 0, 40, N, 43, col=NAVY, alpha=255, h=0.9, f0=20)
    if broken:
        rng = t.rng
        cx, cy = rng.integers(14, 50), rng.integers(10, 36)
        for k in range(10):
            a = k * np.pi / 5 + rng.uniform(-0.2, 0.2)
            for r in range(2, 26):
                x, yy = int(cx + np.cos(a) * r), int(cy + np.sin(a) * r)
                area(t, x, yy, x + 1, yy + 1, col=(220, 230, 230), alpha=230)
        area(t, cx - 8, cy - 6, cx + 9, cy + 7, alpha=0)
    return t


@lru_cache(maxsize=None)
def name_pic():
    p = Pic(14, 1, (0, 0, 0, 0), 44100)
    W, H = p.W, p.H
    text = "HARBOR  LANDING"
    size = civic.fit(text, BOLD, 46, W - 40)
    p.text(W / 2 + 2, H / 2 + 3, text, BOLD, size, (20, 20, 24), anchor="mm", a=160)
    p.text(W / 2, H / 2 + 1, text, BOLD, size, (238, 236, 228), anchor="mm")
    return p


def name(i):
    t = name_pic().tile(i, 44110 + i, smooth=0.6, grime=0.2)
    return t


@lru_cache(maxsize=None)
def ferry_pic():
    p = Pic(3, 1, (0, 0, 0, 0), 44130)
    W, H = p.W, p.H
    p.rect(1, 8, W - 1, H - 8, fill=NAVY)
    p.rect(1, 40, W - 1, H - 8, fill=(200, 40, 36))
    p.text(W / 2, 24, "GRAYHAVEN HARBOR FERRIES", BOLD, civic.fit("GRAYHAVEN HARBOR FERRIES", BOLD, 14, W - 12), WHITE, anchor="mm")
    p.text(W / 2, 48, "GATE 1", BOLD, 11, WHITE, anchor="mm")
    return p


def ferry_board(i):
    return ferry_pic().tile(i, 44140 + i, smooth=0.5, grime=0.2)


@lru_cache(maxsize=None)
def dep_pic():
    """The departures board: split-flap rows, every one of them CANCELLED but the last."""
    p = Pic(4, 2, (16, 16, 18), 44150)
    W, H = p.W, p.H
    p.rect(0, 0, W, H, outline=(60, 60, 64), width=4)
    p.text(10, 8, "DEPARTURES", BOLD, 15, (250, 200, 60))
    p.text(W - 10, 8, "TIME   STATUS", BOLD, 11, (180, 180, 170), anchor="ra")
    rows = [("MERCER POINT", "6:15", "CANCELLED"), ("SEABRIGHT", "6:40", "CANCELLED"), ("HOLLIS ISLAND", "7:00", "CANCELLED"),
            ("EASTSIDE QUAY", "7:30", "CANCELLED"), ("EVACUATION", "8:00", "BOARDING"), ("EVACUATION", "9:30", "FINAL CALL")]
    for k, (dest, tm, st) in enumerate(rows):
        y = 32 + k * 15
        for x in range(10, W - 10, 9):
            p.rect(x, y, x + 8, y + 12, fill=(30, 30, 34))
        col = (240, 90, 60) if st == "CANCELLED" else (120, 230, 110)
        p.text(12, y + 1, dest, STENCIL, 10, (236, 232, 220))
        p.text(W - 110, y + 1, tm, STENCIL, 10, (236, 232, 220))
        p.text(W - 12, y + 1, st, STENCIL, 10, col, anchor="ra")
    return p


def dep(i):
    t = dep_pic().tile(i, 44160 + i, smooth=0.6, grime=0.1)
    t.emit[:] = 0.15
    return t


@lru_cache(maxsize=None)
def evac_pic():
    p = Pic(6, 1, (0, 0, 0, 0), 44170)
    W, H = p.W, p.H
    p.rect(1, 6, W - 1, H - 6, fill=(236, 232, 220))
    p.rect(1, 6, W - 1, 16, fill=RED)
    p.rect(1, H - 16, W - 1, H - 6, fill=RED)
    text = "EVACUATION FERRY  -  BOARDING HERE"
    p.text(W / 2, H / 2, text, BOLD, civic.fit(text, BOLD, 22, W - 30), INK, anchor="mm")
    return p


def evac(i):
    t = evac_pic().tile(i, 44180 + i, smooth=0.15, grime=0.3)
    t.c *= (0.9 + 0.1 * np.cos(np.arange(N)[None, :] / N * 2 * np.pi * 2))[..., None]
    return t


def bags(seed, kind):
    """Suitcases and duffels heaped, seen from the side: hard cases in navy, maroon, tan, a holdall."""
    t = F.plain(seed, (60, 60, 64), 0.05, 0.3)
    rng = t.rng
    t.alpha[:] = 0
    cols = {"a": [(30, 40, 70), (110, 30, 40), (170, 140, 100)], "b": [(40, 70, 50), (60, 60, 64), (200, 190, 170)],
            "c": [(90, 50, 30), (30, 30, 34), (130, 30, 60)]}[kind]
    y = N
    k = 0
    while y > 12:
        h = int(rng.integers(12, 22))
        x0, x1 = int(rng.integers(0, 10)), int(rng.integers(50, N))
        col = cols[k % 3]
        area(t, x0, y - h, x1, y, col=col, alpha=255, h=0.8)
        area(t, x0, y - h, x1, y - h + 2, mul=1.2)
        area(t, (x0 + x1) // 2 - 4, y - h - 2, (x0 + x1) // 2 + 4, y - h, col=(30, 30, 30), alpha=255)
        y -= h
        k += 1
    t.grime(0.3)
    return t


# ---------------------------------------------------------------- the pier's shops

def shingle(seed, weathered=False):
    """Cedar shingles in courses gone silver-grey, the butts ragged."""
    t = F.wood(seed, "pine", rot=True)
    rng = t.rng
    base = (126, 120, 110) if weathered else (150, 116, 80)
    t.c = t.c * 0.3 + np.array(base, np.float32) * 0.7
    for row in range(4):
        y0 = row * 16
        off = (row % 2) * 6
        area(t, 0, y0 + 14, N, y0 + 16, col=(50, 46, 40), dh=-0.3)
        x = -off
        while x < N:
            wdt = int(rng.integers(6, 14))
            area(t, x, y0, x + wdt, y0 + 14, mul=rng.uniform(0.82, 1.12))
            area(t, x + wdt - 1, y0, x + wdt, y0 + 14, col=(60, 56, 48), dh=-0.2)
            x += wdt
    t.grime(0.25, (50, 46, 40))
    return t


def clap(seed):
    t = painted_wood(seed, (232, 230, 220), 0.4)
    for y in range(0, N, 10):
        area(t, 0, y, N, y + 1, mul=0.65, dh=-0.3)
        area(t, 0, y + 1, N, y + 2, mul=1.08)
    t.streaks(0.4, 0.8)
    return t


def shop_window(seed):
    """A shop's window, small panes in white bars, the glass salted."""
    t = F.plain(seed, (110, 130, 134), 0.03, 0.9)
    t.alpha[:] = 130
    t.f0[:] = 30
    for x in (0, 31, 62):
        area(t, x, 0, x + 2, N, col=(236, 234, 226), alpha=255, h=0.9)
    for y in (0, 31, 62):
        area(t, 0, y, N, y + 2, col=(236, 234, 226), alpha=255, h=0.9)
    t.speckle(0.08, (220, 226, 226), 0.3)
    return t


@lru_cache(maxsize=None)
def shop_pic(key):
    text, field, ink, font = SHOPS[key]
    p = Pic(4, 1, (0, 0, 0, 0), 44200 + len(key) * 7)
    W, H = p.W, p.H
    p.rect(1, 9, W - 1, H - 9, fill=field)
    p.rect(1, 9, W - 1, H - 9, outline=mixc(field, (0, 0, 0), 0.4), width=2)
    p.rect(5, 13, W - 5, H - 13, outline=mixc(field, ink, 0.5), width=1)
    size = civic.fit(text, font, 30, W - 30)
    p.text(W / 2 + 1, H / 2 + 2, text, font, size, mixc(field, (0, 0, 0), 0.5), anchor="mm")
    p.text(W / 2, H / 2, text, font, size, ink, anchor="mm")
    return p


def shop(key, i):
    t = shop_pic(key).tile(i, 44250 + i + len(key) * 13, smooth=0.35, grime=0.25)
    t.speckle(0.008, (230, 226, 210), 0.5)
    return t


@lru_cache(maxsize=None)
def menu_pic():
    p = Pic(2, 1, (24, 30, 26), 44300)
    W, H = p.W, p.H
    p.rect(0, 0, W, H, outline=(110, 80, 50), width=4)
    lines = ["CHOWDER  CUP 1.25", "FRIED CLAMS  4.95", "LOBSTER ROLL  6.50", "FISH & CHIPS  3.95", "SODA  .60"]
    for k, s in enumerate(lines):
        p.text(10, 8 + k * 11, s, STENCIL, 8, (230, 228, 220))
    return p


def menu(i):
    t = menu_pic().tile(i, 44310 + i, smooth=0.3, grime=0.15)
    return t


@lru_cache(maxsize=None)
def pier_pic():
    """The arch's board over the pier's root: PIER 9 in gold on navy, the shops' names under."""
    p = Pic(3, 2, (0, 0, 0, 0), 44320)
    W, H = p.W, p.H
    p.ellipse(2, 6, W - 2, H + 60, fill=NAVY)
    p.ellipse(2, 6, W - 2, H + 60, outline=(206, 166, 70), width=3)
    p.text(W / 2, 46, "PIER 9", OLD, 40, (206, 166, 70), anchor="mm")
    p.text(W / 2, 86, "SHOPS  -  FOOD  -  CAROUSEL", BOLD, civic.fit("SHOPS  -  FOOD  -  CAROUSEL", BOLD, 11, W - 40), CREAM, anchor="mm")
    p.rect(2, 104, W - 2, H, fill=NAVY)
    return p


def pier_board(i):
    return pier_pic().tile(i, 44330 + i, smooth=0.35, grime=0.25)


def tees(seed):
    """A wall of T-shirts on hangers: the harbour's gull, the lighthouse, the island's name."""
    t = F.plain(seed, (210, 204, 190), 0.03, 0.3)
    rng = t.rng
    cols = [(240, 240, 236), (40, 90, 150), (200, 60, 50), (60, 140, 120), (240, 200, 60), (30, 30, 34)]
    for row in (2, 33):
        for x in range(2, 62, 15):
            c = cols[rng.integers(len(cols))]
            area(t, x, row + 3, x + 13, row + 28, col=c, h=0.8)
            area(t, x - 1, row + 3, x + 1, row + 12, col=c)
            area(t, x + 12, row + 3, x + 14, row + 12, col=c)
            area(t, x + 4, row + 10, x + 9, row + 16, col=mixc(c, (0, 0, 0), 0.5) if sum(c) > 300 else (230, 230, 220))
            area(t, x + 5, row, x + 8, row + 3, col=(150, 150, 150))
    return t


def gifts(seed):
    """Shelves of shells, lighthouses in resin, snow globes, mugs, all dusty."""
    t = F.wood(seed, "oak")
    rng = t.rng
    for y in (20, 42, 62):
        area(t, 0, y, N, y + 2, col=(70, 50, 34), dh=0.2)
    for y0 in (4, 26, 48):
        x = 2
        while x < 60:
            k = rng.integers(4)
            if k == 0:
                disc(t, x + 4, y0 + 10, 4, col=(240, 220, 200))
                disc(t, x + 4, y0 + 10, 1.5, col=(220, 160, 140))
                x += 10
            elif k == 1:
                area(t, x + 2, y0 + 2, x + 5, y0 + 15, col=(240, 240, 236))
                area(t, x + 2, y0 + 6, x + 5, y0 + 8, col=(200, 40, 40))
                x += 8
            elif k == 2:
                disc(t, x + 4, y0 + 10, 4, col=(170, 200, 220))
                area(t, x + 1, y0 + 13, x + 8, y0 + 16, col=(80, 50, 30))
                x += 10
            else:
                area(t, x + 1, y0 + 7, x + 8, y0 + 15, col=(30, 60, 110))
                x += 10
    t.grime(0.3)
    return t


def freezer_top(seed):
    """An ice cream dipping cabinet from above: the tubs, gone to coloured slop."""
    t = F.plain(seed, (220, 224, 226), 0.03, 0.8)
    rng = t.rng
    cols = [(240, 230, 200), (110, 60, 40), (230, 150, 170), (150, 200, 140), (250, 220, 120), (120, 90, 140)]
    for x in range(2, 62, 15):
        for y in (4, 34):
            area(t, x, y, x + 13, y + 26, col=(200, 204, 206), h=0.5)
            c = cols[rng.integers(len(cols))]
            area(t, x + 1, y + 1, x + 12, y + 25, col=mixc(c, (90, 80, 70), 0.3), h=0.6, smooth=0.9)
    return t


# ---------------------------------------------------------------- the carousel

def ride_floor(seed):
    """The carousel's platform: boards painted in wedges of red and cream, scuffed to the wood."""
    t = F.wood(seed, "pine")
    yy, xx = np.mgrid[0:N, 0:N]
    a = np.arctan2(yy - 32, xx - 32)
    red = (np.floor(a / (np.pi / 4)) % 2 == 0)
    col = np.where(red[..., None], np.array((170, 40, 36), np.float32), np.array((226, 214, 186), np.float32))
    t.c = t.c * 0.25 + col * 0.75
    t.grime(0.35, (60, 50, 40))
    return t


def horse_coat(seed, rgb):
    t = F.plain(seed, rgb, 0.04, 0.7)
    t.f0[:] = 25
    t.speckle(0.04, (60, 50, 40), 0.4)
    t.grime(0.15)
    return t


def gilt(seed):
    t = F.plain(seed, (204, 164, 70), 0.06, 0.8)
    t.f0[:] = 90
    t.speckle(0.05, (110, 80, 30), 0.4)
    return t


def drum(seed):
    """The carousel's centre drum: mirrors in gilt frames between painted panels of the sea."""
    t = F.plain(seed, (150, 40, 50), 0.04, 0.6)
    area(t, 0, 0, N, 6, col=(204, 164, 70), f0=80)
    area(t, 0, N - 6, N, N, col=(204, 164, 70), f0=80)
    area(t, 10, 12, 54, 52, col=(204, 164, 70), f0=80)
    area(t, 13, 15, 51, 49, col=(180, 196, 200), smooth=0.98, f0=200)
    t.speckle(0.05, (100, 100, 100), 0.5)
    return t


# ---------------------------------------------------------------- the marina

def cover(seed):
    t = F.plain(seed, (40, 70, 110), 0.05, 0.2)
    t.grime(0.35, (60, 70, 60))
    t.streaks(0.4, 0.8)
    return t


def alu(seed):
    t = F.plain(seed, (186, 190, 192), 0.03, 0.75)
    t.f0[:] = 70
    t.grime(0.15)
    return t


def pump_face(seed):
    t = F.plain(seed, (210, 40, 36), 0.04, 0.55)
    area(t, 12, 8, 52, 26, col=(20, 20, 22))
    for k in range(3):
        area(t, 16 + k * 12, 12, 26 + k * 12, 22, col=(240, 236, 220))
    area(t, 10, 34, 54, 50, col=(240, 236, 220))
    p = F.text_mask("MARINE", BOLD, 9, 44, 16)
    F.paste(t, p, 10, 34, (20, 30, 60))
    t.grime(0.3)
    return t


# ---------------------------------------------------------------- signs

@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 44400 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 16, W - 1, H - 16, fill=field)
    p.rect(1, 16, W - 1, H - 16, outline=mixc(field, (0, 0, 0), 0.3), width=1)
    font = STENCIL if field[0] > 200 else BOLD
    p.text(W / 2, H / 2, text, font, civic.fit(text, font, 14, W - 14), ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 44450 + i + len(key) * 11, smooth=0.5, grime=0.2)


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 44500 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (210, 24, 30) if key != "swim" else (30, 30, 30), jitter=0.4, a=235, angle=-2)
    return p


def spray(key, i):
    t = spray_pic(key).tile(i, 44510 + i + len(key) * 5, smooth=0.3, grime=0.0)
    lum = t.alpha > 20
    for _ in range(4):
        x, y = int(t.rng.integers(4, 60)), int(t.rng.integers(30, 50))
        if lum[y, x]:
            drips(t, x, x + 1, y, 1, (4, 12), col=(180, 20, 20) if key != "swim" else (30, 30, 30))
            t.alpha[y:y + 12, x:x + 1] = 230
    return t


def textures():
    T = {}
    T["wf_pavers"] = lambda: pavers(44000)
    T["wf_pavers_b"] = lambda: pavers(44001, broken=True)
    T["wf_pavers_moss"] = lambda: pavers(44002, moss=True)
    T["wf_cap"] = lambda: granite(44003, cap=True)
    T["wf_wall"] = lambda: granite(44004)
    T["wf_wall_slime"] = lambda: granite(44005, slime=True)
    T["wf_iron"] = lambda: iron(44006)
    T["wf_rubber"] = lambda: rubber(44007)
    T["wf_buoy_red"] = lambda: buoy(44008, (190, 40, 34))
    T["wf_buoy_green"] = lambda: buoy(44009, (40, 120, 60))
    T["wf_net"] = lambda: net(44010)
    T["wf_rope"] = lambda: rope(44011)
    T["wf_deck"] = lambda: deck(44012)
    T["wf_deck_b"] = lambda: deck(44013, dark=True)
    T["wf_creosote"] = lambda: creosote(44014)
    T["wf_white"] = lambda: painted_wood(44015, (232, 230, 220))
    T["wf_float_drum"] = lambda: float_drum(44016)
    T["wf_steel"] = lambda: steel_paint(44017, (40, 74, 120))
    T["wf_panel"] = lambda: panel(44018)
    T["wf_panel_b"] = lambda: panel(44019, stained=True)
    T["wf_glass"] = lambda: curtain(44020)
    T["wf_glass_broken"] = lambda: curtain(44021, broken=True)
    T["wf_glass_lit"] = lambda: curtain(44022, lit=True)
    for i in range(14):
        T[f"wf_name_{i}"] = lambda i=i: name(i)
    for i in range(3):
        T[f"wf_ferry_{i}"] = lambda i=i: ferry_board(i)
    for i in range(8):
        T[f"wf_dep_{i}"] = lambda i=i: dep(i)
    for i in range(6):
        T[f"wf_evac_{i}"] = lambda i=i: evac(i)
    for k in "abc":
        T[f"wf_bags_{k}"] = lambda k=k: bags(44030 + ord(k), k)
    T["wf_shingle"] = lambda: shingle(44040)
    T["wf_shingle_b"] = lambda: shingle(44041, weathered=True)
    T["wf_clap"] = lambda: clap(44042)
    T["wf_window"] = lambda: shop_window(44043)
    T["wf_tees"] = lambda: tees(44044)
    T["wf_gifts"] = lambda: gifts(44045)
    T["wf_freezer"] = lambda: freezer_top(44046)
    T["wf_taffy_pink"] = lambda: horse_coat(44047, (236, 150, 176))
    for i in range(2):
        T[f"wf_menu_{i}"] = lambda i=i: menu(i)
    for key in SHOPS:
        for i in range(4):
            T[f"wf_shop_{key}_{i}"] = lambda key=key, i=i: shop(key, i)
    for i in range(6):
        T[f"wf_pier_{i}"] = lambda i=i: pier_board(i)
    T["wf_ride_floor"] = lambda: ride_floor(44050)
    T["wf_coat_white"] = lambda: horse_coat(44051, (236, 232, 222))
    T["wf_coat_black"] = lambda: horse_coat(44052, (40, 38, 40))
    T["wf_coat_grey"] = lambda: horse_coat(44053, (160, 150, 140))
    T["wf_saddle_red"] = lambda: horse_coat(44054, (170, 36, 40))
    T["wf_saddle_blue"] = lambda: horse_coat(44055, (40, 70, 150))
    T["wf_gilt"] = lambda: gilt(44056)
    T["wf_canopy_a"] = lambda: P_canvas(44057, (176, 36, 34))
    T["wf_canopy_b"] = lambda: P_canvas(44058, (36, 110, 70))
    T["wf_drum"] = lambda: drum(44059)
    T["wf_cover"] = lambda: cover(44060)
    T["wf_alu"] = lambda: alu(44061)
    T["wf_pump"] = lambda: pump_face(44062)
    T["wf_sail"] = lambda: F.plain(44063, (232, 228, 214), 0.04, 0.2)
    for key in SIGNS:
        for i in range(2):
            T[f"wf_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"wf_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
    return T


def P_canvas(seed, rgb):
    """Awning canvas in wide stripes with white, faded and mildewed."""
    t = F.plain(seed, rgb, 0.05, 0.15)
    for x in range(0, N, 16):
        area(t, x, 0, x + 8, N, col=(236, 230, 214))
    t.grime(0.35, (60, 56, 48))
    t.speckle(0.03, (40, 40, 30), 0.5)
    return t


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
    ir, wd, wh = "wf_iron", "wf_creosote", "wf_white"

    def face(front, side, up=None):
        return {"north": front, "side": side, "up": up or side, "down": side}

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    # ---- the front: paving, the seawall, its rail and fenders
    for n in ("wf_pavers", "wf_pavers_b", "wf_pavers_moss"):
        add(n, None, "Brick Pavers", "STONE", rotate=None, tex={"up": n, "side": "wf_wall", "down": "wf_wall"})
    add("wf_cap", None, "Coping Stone", "STONE", rotate=None, tex={"up": "wf_cap", "side": "wf_wall", "down": "wf_wall"})
    for n in ("wf_wall", "wf_wall_slime"):
        add(n, None, "Seawall", "STONE", rotate=None, tex={"all": n})
    # the iron rail on the coping: posts with ball finials, two rails and dog bars between
    add("wf_rail_iron", [[0, 14, 1, 16, 15, 3, ir], [0, 3, 1.5, 16, 4, 2.5, ir], [7, 0, 0.5, 9, 15, 3.5, ir],
                         [7.2, 15, 1.2, 8.8, 16.5, 2.8, ir], [3, 4, 1.6, 3.8, 14, 2.4, ir], [12, 4, 1.6, 12.8, 14, 2.4, ir]],
        "Iron Railing", "METAL", render="cutout", shape=[[0, 0, 0, 16, 16, 3.5]])
    add("wf_fender", [[3, 0, 13, 13, 16, 16, "wf_rubber"], [4, 0, 12, 12, 16, 13, "wf_rubber"]], "Fender", "WOOL", decor=True,
        shape=[[3, 0, 12, 13, 16, 16]])
    add("wf_telescope", [[6, 0, 6, 10, 9, 10, steel], [5, 0, 5, 11, 1, 11, steel], [4, 9, 5, 12, 13, 12, face(steel, steel)],
                         [5, 10, 1, 7.5, 12.5, 5, black], [8.5, 10, 1, 11, 12.5, 5, black], [6.5, 13, 7, 9.5, 14, 9, chrome]],
        "Coin Telescope", "METAL", loot="scraps", shape=[[4, 0, 1, 12, 14, 12]])
    add("wf_ring_post", [[6, 0, 6, 10, 16, 10, wh], [3, 4, 4.5, 13, 6, 5.5, "ship_lifebuoy"], [3, 12, 4.5, 13, 14, 5.5, "ship_lifebuoy"],
                         [3, 6, 4.5, 5, 12, 5.5, "ship_lifebuoy"], [11, 6, 4.5, 13, 12, 5.5, "ship_lifebuoy"]],
        "Life Ring", "WOOD", shape=[[6, 0, 6, 10, 16, 10]])
    for c in ("red", "green"):
        add(f"wf_buoy_{c}", [[4, 0, 4, 12, 9, 12, f"wf_buoy_{c}"], [5, 9, 5, 11, 15, 11, f"wf_buoy_{c}"], [7, 15, 7, 9, 18, 9, ir]],
            "Channel Buoy", "METAL", rotate=None, shape=[[4, 0, 4, 12, 15, 12]])
    add("wf_pots", [[0, 0, 0, 16, 7, 8, face("wf_net", wd, wd)], [0, 0, 8, 16, 7, 16, face("wf_net", wd, wd)],
                    [1, 7, 2, 15, 14, 14, face("wf_net", wd, wd)]], "Lobster Pots", "WOOD", render="cutout", loot="scraps",
        shape=[[0, 0, 0, 16, 14, 16]])
    add("wf_coil", [[2, 0, 2, 14, 3, 14, "wf_rope"], [4, 3, 4, 12, 5, 12, "wf_rope"]], "Coiled Rope", "WOOL", rotate=None, decor=True)

    # ---- the pier and the floats
    for n in ("wf_deck", "wf_deck_b"):
        add(n, None, "Pier Decking", "WOOD", rotate=None, tex={"up": n, "side": wd, "down": wd})
    add("wf_pile", [[4, 0, 4, 12, 16, 12, wd]], "Pile", "WOOD", rotate=None, shape=[[4, 0, 4, 12, 16, 12]])
    add("wf_pile_top", [[4, 0, 4, 12, 12, 12, wd], [3.5, 12, 3.5, 12.5, 13, 12.5, ir], [6, 13, 6, 10, 15, 10, wd]], "Pile", "WOOD",
        rotate=None, shape=[[4, 0, 4, 12, 15, 12]])
    add("wf_stringer", [[0, 10, 4, 16, 16, 12, wd], [0, 4, 7, 16, 10, 9, wd]], "Stringer", "WOOD", rotate="axis",
        shape=[[0, 4, 4, 16, 16, 12]])
    add("wf_rail", [[0, 13, 0, 16, 15.5, 3, wh], [0, 6, 0.5, 16, 7.5, 2.5, wh], [6.5, 0, 0, 9.5, 15.5, 3, wh],
                    [1.5, 7.5, 1, 2.5, 13, 2, wh], [13.5, 7.5, 1, 14.5, 13, 2, wh]], "Pier Railing", "WOOD",
        shape=[[0, 0, 0, 16, 16, 3]])
    add("wf_float", [[0, 4, 0, 16, 7, 16, {"up": "wf_deck", "side": wd, "down": wd}], [1, 0, 1, 7, 4, 15, "wf_float_drum"],
                     [9, 0, 1, 15, 4, 15, "wf_float_drum"], [0, 7, 0, 16, 8, 1, "wf_rubber"]], "Floating Dock", "WOOD",
        rotate=None, shape=[[0, 0, 0, 16, 8, 16]])
    add("wf_cleat", [[3, 0, 6, 13, 1, 10, chrome], [6, 1, 7, 10, 3, 9, chrome], [2, 3, 7, 14, 4, 9, chrome]], "Cleat", "METAL",
        rotate="axis", decor=True)
    # aluminium gangway stairs: two steps a block, the treads chequered, rails each side
    add("wf_gangway", [[1, 0, 8, 15, 8, 16, face("wf_alu", "wf_alu")], [1, 8, 0, 15, 16, 8, face("wf_alu", "wf_alu")],
                       [0, 0, 0, 1, 16, 16, "wf_alu"], [15, 0, 0, 16, 16, 16, "wf_alu"]], "Gangway Stairs", "METAL",
        shape=[[1, 0, 8, 15, 8, 16], [1, 0, 0, 15, 16, 8]])

    # ---- the terminal
    for n in ("wf_steel", "wf_panel", "wf_panel_b"):
        add(n, None, "Steel" if n == "wf_steel" else "Cladding", "METAL", rotate=None, tex={"all": n})
    for n, light in (("wf_glass", None), ("wf_glass_broken", None), ("wf_glass_lit", 6)):
        add(n, [[0, 0, 7, 16, 16, 9, {"north": n, "south": n, "side": "wf_steel", "up": "wf_steel", "down": "wf_steel"}]],
            "Curtain Wall", "GLASS", render="translucent", shape=[[0, 0, 7, 16, 16, 9]], **({"light": light} if light else {}))
    for i in range(14):
        add(f"wf_name_{i}", plate(f"wf_name_{i}", 14.8), "Letters", "METAL", decor=True)
    for i in range(3):
        add(f"wf_ferry_{i}", plate(f"wf_ferry_{i}", 15.2), "Sign", "METAL", decor=True)
    for i in range(8):
        add(f"wf_dep_{i}", [[0, 0, 13, 16, 16, 16, {"north": f"wf_dep_{i}", "side": black, "up": black, "down": black}]],
            "Departures Board", "METAL", decor=True)
    for i in range(6):
        add(f"wf_evac_{i}", plate(f"wf_evac_{i}", 15.6), "Banner", "WOOL", decor=True)
    add("wf_ticket", [[0, 0, 6, 16, 11, 16, face("wf_panel", "wf_panel", "hs_counter")], [0, 11, 6, 16, 16, 7, glass],
                      [6, 11, 5.5, 10, 11.5, 7, chrome], [2, 11, 10, 6, 13, 13, face("home_plastic_black", black)]],
        "Ticket Window", "GLASS", render="translucent", loot="desk", shape=[[0, 0, 6, 16, 16, 16]])
    add("wf_seats", [[0, 6, 3, 16, 7.5, 12, "wf_steel"], [0, 7.5, 11, 16, 16, 12.5, "wf_steel"], [1, 0, 6, 3, 6, 9, chrome],
                     [13, 0, 6, 15, 6, 9, chrome], [7.5, 7.5, 3, 8.5, 10, 12, chrome]], "Terminal Seats", "METAL",
        shape=[[0, 0, 3, 16, 16, 12.5]])
    add("wf_turnstile", [[0, 0, 4, 4, 13, 12, face(steel, steel, chrome)], [4, 9, 7.5, 12, 10, 8.5, chrome],
                         [4, 10, 3.5, 5, 11, 12.5, chrome], [12, 0, 4, 16, 13, 12, face(steel, steel, chrome)]], "Turnstile", "METAL",
        shape=[[0, 0, 4, 16, 13, 12]])
    add("wf_queue", [[7, 0, 0, 9, 1, 3, black], [7.5, 1, 1, 8.5, 14, 2, chrome], [7, 14, 0.5, 9, 15, 2.5, chrome],
                     [7.8, 12, 2, 8.2, 13.5, 16, "wf_steel"]], "Queue Barrier", "METAL", rotate="axis",
        shape=[[7, 0, 0, 9, 15, 3]])
    add("wf_queue_post", [[6, 0, 6, 10, 1, 10, black], [7.5, 1, 7.5, 8.5, 14, 8.5, chrome], [7, 14, 7, 9, 15, 9, chrome]],
        "Queue Post", "METAL", rotate=None, shape=[[7, 0, 7, 9, 15, 9]])
    for k in "abc":
        n = f"wf_bags_{k}"
        add(n, [[0, 0, 1, 16, 7, 15, face(n, n, "wf_bags_" + k)], [2, 7, 2, 14, 12, 12, face(n, n)],
                [4, 12, 4, 11, 15, 10, face(n, n)]], "Luggage", "WOOL", loot="bag" if k != "c" else "clothes",
            shape=[[0, 0, 1, 16, 15, 15]])

    # ---- the pier's shops
    for n in ("wf_shingle", "wf_shingle_b", "wf_clap"):
        add(n, None, "Shingles" if "shingle" in n else "Clapboard", "WOOD", rotate=None, tex={"all": n})
    add("wf_trim", None, "Trim", "WOOD", rotate=None, tex={"all": wh})
    add("wf_window", [[0, 0, 7, 16, 16, 9, {"north": "wf_window", "south": "wf_window", "side": wh, "up": wh, "down": wh}]],
        "Window", "GLASS", render="translucent", shape=[[0, 0, 7, 16, 16, 9]])
    add("wf_freezer", [[0, 0, 2, 16, 12, 16, face("hs_counter", "hs_counter", "wf_freezer")],
                       [0, 12, 2, 16, 15, 3, glass], [0, 14.5, 2, 16, 15, 16, glass]], "Ice Cream Freezer", "METAL",
        render="translucent", loot="home_fridge", shape=[[0, 0, 2, 16, 15, 16]])
    add("wf_taffy", [[2, 0, 4, 14, 8, 14, face(steel, steel)], [7, 8, 8, 9, 14, 10, chrome], [3, 12, 8, 13, 13, 10, chrome],
                     [3, 9, 7, 6, 12, 11, "wf_taffy_pink"], [10, 9, 7, 13, 12, 11, "wf_taffy_pink"]], "Taffy Puller", "METAL",
        shape=[[2, 0, 4, 14, 14, 14]])
    add("wf_tees", [[0, 0, 14, 16, 16, 16, face("wf_tees", wh)]], "T-Shirt Wall", "WOOL", decor=True, loot="clothes")
    add("wf_gifts", [[0, 0, 4, 16, 16, 16, face("wf_gifts", "cv_oak", "cv_oak")]], "Gift Shelves", "WOOD", loot="storage",
        shape=[[0, 0, 4, 16, 16, 16]])
    for i in range(2):
        add(f"wf_menu_{i}", [[0, 0, 14.5, 16, 16, 16, face(f"wf_menu_{i}", "cv_oak")]], "Menu Board", "WOOD", decor=True)
    for key in SHOPS:
        for i in range(4):
            n = f"wf_shop_{key}_{i}"
            add(n, [[0, 0, 14, 16, 16, 16, face(n, wh)]], "Shop Sign", "WOOD", decor=True)
    for i in range(6):
        add(f"wf_pier_{i}", [[0, 0, 7, 16, 16, 9, {"north": f"wf_pier_{i}", "south": f"wf_pier_{i}", "side": wh, "up": wh, "down": wh}]],
            "Pier Sign", "WOOD", render="cutout", decor=True)

    # ---- the carousel
    add("wf_ride_floor", None, "Carousel Platform", "WOOD", rotate=None, tex={"up": "wf_ride_floor", "side": "wf_gilt", "down": wd})
    add("wf_brass", [[7, 0, 7, 9, 16, 9, "wf_gilt"]], "Brass Pole", "METAL", rotate=None, shape=[[7, 0, 7, 9, 16, 9]])
    for k, coat, saddle in (("a", "wf_coat_white", "wf_saddle_red"), ("b", "wf_coat_black", "wf_saddle_blue"),
                            ("c", "wf_coat_grey", "wf_saddle_red")):
        # the horse leaping, head to the north, the brass pole through its back
        add(f"wf_horse_{k}", [[7, 0, 7, 9, 16, 9, "wf_gilt"], [5, 6, 3, 11, 11, 13, coat], [5.5, 9, 0, 10.5, 13, 4, coat],
                              [6, 12, -2, 10, 16, 2, coat], [6.5, 13, -4, 9.5, 15, -2, coat], [7, 16, -1, 9, 18, 2, saddle],
                              [5, 11, 5, 11, 12, 10, saddle], [5.5, 1, 3, 7, 7, 5, coat], [9, 2, 2, 10.5, 7, 4, coat],
                              [5.5, 2, 11, 7, 7, 13, coat], [9, 1, 12, 10.5, 7, 14, coat], [7, 7, 13, 9, 10, 16, "wf_gilt"]],
            "Carousel Horse", "WOOD", shape=[[5, 0, 0, 11, 16, 14]])
    for n in ("wf_canopy_a", "wf_canopy_b"):
        add(n, None, "Canopy", "WOOL", rotate=None, tex={"all": n})
    add("wf_drum", None, "Carousel Drum", "WOOD", rotate=None, tex={"side": "wf_drum", "up": "wf_gilt", "down": "wf_gilt"})
    add("wf_crest", [[3, 0, 3, 13, 4, 13, "wf_gilt"], [5, 4, 5, 11, 9, 11, "wf_canopy_a"], [7, 9, 7, 9, 16, 9, "wf_gilt"]],
        "Carousel Crest", "METAL", rotate=None, shape=[[3, 0, 3, 13, 16, 13]])

    # ---- the marina
    add("wf_pump", [[3, 0, 5, 13, 15, 11, face("wf_pump", "vessel_hull_grey")], [4, 15, 6, 12, 16, 10, steel],
                    [13, 5, 7, 15, 11, 9, black], [14, 3, 7.5, 15, 5, 8.5, black]], "Marine Fuel Pump", "METAL",
        shape=[[3, 0, 5, 15, 16, 11]])
    add("wf_rack", [[0, 0, 6, 16, 2, 10, "wf_steel"], [0, 2, 7, 16, 3, 9, "wf_steel"]], "Boat Rack", "METAL", rotate="axis",
        shape=[[0, 0, 6, 16, 3, 10]])
    add("wf_cover", None, "Boat Cover", "WOOL", rotate=None, tex={"all": "wf_cover"})
    add("wf_outboard", [[5, 4, 0, 11, 14, 8, face(black, black)], [6.5, 0, 2, 9.5, 4, 6, black], [7, -4, 3, 9, 0, 5, chrome],
                        [7.5, 10, 8, 8.5, 11, 14, chrome]], "Outboard Motor", "METAL", shape=[[5, 0, 0, 11, 14, 8]])
    add("wf_mast", [[7, 0, 7, 9, 16, 9, "wf_alu"]], "Mast", "METAL", rotate=None, shape=[[7, 0, 7, 9, 16, 9]])
    add("wf_boom", [[7, 2, 0, 9, 4, 16, "wf_alu"], [6, 4, 0, 10, 7, 16, "wf_sail"]], "Boom", "METAL", rotate="axis",
        shape=[[6, 2, 0, 10, 7, 16]])
    add("wf_windshield", [[0, 0, 7, 16, 8, 9, glass], [0, 8, 7, 16, 9, 9, chrome]], "Windshield", "GLASS", render="translucent",
        shape=[[0, 0, 7, 16, 9, 9]])

    for key in SIGNS:
        for i in range(2):
            add(f"wf_sgn_{key}_{i}", plate(f"wf_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"wf_spray_{key}_{i}", plate(f"wf_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("wf_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} waterfront blocks in {path}")


if __name__ == "__main__":
    main()
