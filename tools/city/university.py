"""Grayhaven State University: the un_ kit for the island's campus (world/county/University.java and
its halls).

No photos of it in the library yet (city/university-* to be queued): this follows what the big
American state campuses built between the 1890s and the 1970s are known for, as they stood in the
eighties. Old Main in red brick and limestone with its white portico and clock tower over the quad;
the library a block of board-marked concrete with slot windows, carpeted floors of stacks and study
carrels; the science hall in buff brick with aluminium ribbon windows, black epoxy benches and fume
hoods; the lecture hall's raked seats under a wall of green boards; the student union's cafeteria,
bookstore and game room; the dormitory tower's double rooms off a long corridor. The last days left
their own: CAMPUS CLOSED on the doors, the vigil flyers on the boards, the board in the big hall
telling everyone to stay in the dorms, THEY'RE IN THE STACKS sprayed by the library's stairs.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- floors and ceilings: un_carpet, un_carpet_b, un_paver, un_paver_b, un_slab_carpet,
  un_slab_carpet_b, un_slab_vct, un_slab_dorm, un_slab_boards, un_slab_terrazzo (+ _lamp, _lamp_on for
  the four with troffers), un_roof, un_roof_lamp(_on);
- walls: un_brut, un_brut_b, un_brut_rib, un_hedge, un_hedge_low, un_slot, un_slot_broken;
- the library: un_catalog, un_carrel, un_table, un_table_lamp(_on), un_circ, un_circ_end,
  un_microfiche, un_magrack;
- the labs: un_lab_bench, un_lab_sink, un_lab_scope, un_fume_lo, un_fume_hi, un_chem_shelf,
  un_skeleton_lo, un_skeleton_hi, un_stool, un_periodic_0..5 (three by two);
- the halls: un_board_KEY_0..7 (four by two: calc, stay);
- the dorms: un_desk, un_wardrobe_lo, un_wardrobe_hi, un_minifridge, un_beanbag, un_records,
  un_poster_0..5;
- the union: un_arcade_lo, un_arcade_hi(_on), un_pool_a, un_pool_b, un_textbooks,
  un_bulletin_KEY_0..1 (two by one: vigil, rooms), un_banner_0..3 (four by one);
- outside: un_map_0..5 (three by two, on its posts), un_seal_0..8 (three by three, bronze),
  un_name_KEY_i (the buildings' names, one row), un_sgn_KEY_0..1 (two by one);
- the last days: un_notice, un_spray_KEY_0..2 (three by one).

    python3 tools/city/university.py        rewrites the un_ entries in blocks.json

photo_textures.py draws the textures (university.register()).
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
from furniture import area, disc, blood, drips, stain  # noqa: E402
from school import Pic, HAND, OBLIQUE, CHALK, chalky  # noqa: E402
from hospital import letters  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
SERIF = "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"
SPRAY = "/usr/share/fonts/truetype/freefont/FreeSansBoldOblique.ttf"
MAROON = (110, 30, 38)
CREAM = (238, 228, 204)
BRONZE = (132, 96, 52)
GREEN_BOARD = (44, 70, 56)

# the campus's signs, two by one: text, field, ink
SIGNS = {
    "registrar": ("REGISTRAR", MAROON, CREAM),
    "admissions": ("ADMISSIONS", MAROON, CREAM),
    "bursar": ("BURSAR", MAROON, CREAM),
    "president": ("PRESIDENT", MAROON, CREAM),
    "great": ("GREAT HALL", MAROON, CREAM),
    "circ": ("CIRCULATION", MAROON, CREAM),
    "reference": ("REFERENCE", MAROON, CREAM),
    "periodicals": ("PERIODICALS", MAROON, CREAM),
    "stacks": ("STACKS", MAROON, CREAM),
    "quiet": ("QUIET PLEASE", (40, 40, 44), CREAM),
    "chem": ("CHEMISTRY", MAROON, CREAM),
    "bio": ("BIOLOGY", MAROON, CREAM),
    "physics": ("PHYSICS", MAROON, CREAM),
    "faculty": ("FACULTY", MAROON, CREAM),
    "hall_a": ("LECTURE HALL A", MAROON, CREAM),
    "hall_b": ("LECTURE HALL B", MAROON, CREAM),
    "bookstore": ("BOOKSTORE", MAROON, CREAM),
    "cafeteria": ("CAFETERIA", MAROON, CREAM),
    "games": ("GAME ROOM", MAROON, CREAM),
    "ballroom": ("BALLROOM", MAROON, CREAM),
    "lounge": ("LOUNGE", MAROON, CREAM),
    "laundry": ("LAUNDRY", MAROON, CREAM),
    "mail": ("MAIL ROOM", MAROON, CREAM),
    "desk": ("FRONT DESK", MAROON, CREAM),
    "shelter": ("SHELTER", (200, 150, 20), (20, 20, 20)),
    "staff": ("STAFF ONLY", (120, 24, 28), (255, 255, 255)),
}
# the buildings' names, one row: text, font, cols, style (bronze, alu, carved, steel)
NAMES = {
    "gate": ("GRAYHAVEN STATE UNIVERSITY", SERIF, 24, "steel"),
    "oldmain": ("OLD MAIN", SERIF, 8, "carved"),
    "library": ("HALLORAN LIBRARY", BLACK, 12, "alu"),
    "science": ("PRESCOTT SCIENCE HALL", BOLD, 14, "bronze"),
    "union": ("STUDENT UNION", BLACK, 10, "alu"),
    "lecture": ("WELLS LECTURE HALL", BOLD, 12, "bronze"),
    "dorm": ("HAWTHORNE HALL", SERIF, 10, "bronze"),
}
SPRAYS = {
    "stacks": ("THEY'RE IN THE STACKS", 3),
    "finals": ("NO FINALS", 3),
    "gohome": ("GO HOME", 3),
}
BOARDS = ("calc", "stay")
BULLETINS = ("vigil", "rooms")


# ---------------------------------------------------------------- floors, walls, outside

def brut(seed, stained=False, ribbed=False):
    """Board-marked concrete: the boards' grain printed in it, a seam every eight rows, the tie
    holes in a grid; ribbed, the fins bush-hammered so the stone shows; the rain's dark streaks."""
    t = F.plain(seed, (150, 147, 138), 0.05, 0.2)
    rng = t.rng
    t.vary(0.06)
    if ribbed:
        for x in range(0, N, 8):
            area(t, x, 0, x + 2, N, mul=0.72, dh=-0.25)
            area(t, x + 2, 0, x + 3, N, mul=0.88, dh=-0.1)
            area(t, x + 3, 0, x + 8, N, mul=1.04, dh=0.08)
        t.speckle(0.25, (182, 176, 162), 0.4, 0.05)
        t.speckle(0.08, (96, 92, 86), 0.4, -0.05)
    else:
        for y in range(0, N, 8):
            area(t, 0, y, N, y + 1, mul=0.8, dh=-0.12)
            for _ in range(5):
                x0 = int(rng.integers(0, N))
                area(t, x0, y + int(rng.integers(2, 7)), x0 + int(rng.integers(6, 20)), y + int(rng.integers(2, 7)) + 1, mul=0.9)
        for x in (12, 44):
            for y in (20, 52):
                disc(t, x, y, 1.6, col=(80, 78, 72), dh=-0.3)
    t.streaks(0.8 if stained else 0.4, 0.62 if stained else 0.8)
    if stained:
        t.grime(0.3)
        stain(t, rng.uniform(0, N), rng.uniform(20, N), rng.uniform(8, 16), (90, 96, 70), 0.35)
    return t


def carpet(seed, rgb=(126, 78, 46), accent=(168, 112, 52), worn=False):
    """Eighties carpet tile: a loop pile in the ground colour, a small figure in the accent every
    sixteen, the tiles' seams; worn, a path walked pale and the coffee spilt on it."""
    t = F.plain(seed, rgb, 0.04, 0.05)
    rng = t.rng
    t.speckle(0.35, tuple(v * 0.8 for v in rgb), 0.5)
    t.speckle(0.2, tuple(min(255, v * 1.15) for v in rgb), 0.4)
    for gy in range(4, N, 16):
        for gx in range(4, N, 16):
            area(t, gx, gy + 3, gx + 8, gy + 5, col=accent)
            area(t, gx + 3, gy, gx + 5, gy + 8, col=accent)
    for k in (0, 32):
        area(t, k, 0, k + 1, N, mul=0.85)
        area(t, 0, k, N, k + 1, mul=0.85)
    t.h[:] = 0.7 + rng.random((N, N)) * 0.1
    if worn:
        area(t, 18, 0, 46, N, mul=1.12)
        stain(t, rng.uniform(10, 54), rng.uniform(10, 54), rng.uniform(5, 9), (70, 46, 24), 0.5)
        t.grime(0.25)
    return t


def pavers(seed, mossy=False):
    """Red brick pavers laid herringbone in sand, a few sunk, weeds in the joints."""
    t = F.plain(seed, (150, 72, 52), 0.05, 0.2)
    rng = t.rng
    t.vary(0.08)
    for gy in range(0, N, 8):
        for gx in range(0, N, 8):
            shade = rng.uniform(0.82, 1.12)
            if (gx // 8 + gy // 8) % 2:
                area(t, gx, gy, gx + 8, gy + 4, mul=shade)
                area(t, gx, gy + 4, gx + 8, gy + 8, mul=shade * rng.uniform(0.9, 1.08))
                area(t, gx, gy + 4, gx + 8, gy + 5, col=(118, 108, 92), dh=-0.25)
            else:
                area(t, gx, gy, gx + 4, gy + 8, mul=shade)
                area(t, gx + 4, gy, gx + 8, gy + 8, mul=shade * rng.uniform(0.9, 1.08))
                area(t, gx + 4, gy, gx + 5, gy + 8, col=(118, 108, 92), dh=-0.25)
            area(t, gx, gy, gx + 8, gy + 1, col=(118, 108, 92), dh=-0.25)
            area(t, gx, gy, gx + 1, gy + 8, col=(118, 108, 92), dh=-0.25)
    if mossy:
        m = np.clip(F.periodic(rng, 8) * 1.6 - 0.5, 0, 1)
        t.mix(m * 0.8, (70, 92, 44), 0.8)
    t.grime(0.2)
    return t


def hedge(seed, dead=False):
    """Clipped boxwood: tiny leaves packed tight, darker in the hollows, gone ragged since."""
    base = (64, 92, 44) if not dead else (104, 88, 54)
    t = F.plain(seed, base, 0.06, 0.15)
    t.speckle(0.4, tuple(v * 0.55 for v in base), 0.6, -0.2)
    t.speckle(0.3, tuple(min(255, v * 1.35) for v in base), 0.5, 0.15)
    t.speckle(0.04, (40, 30, 20), 0.6, -0.3)
    if dead:
        t.speckle(0.15, (140, 120, 70), 0.5)
    t.sss = np.full((N, N), 0.4, np.float32)
    return t


def epoxy(seed):
    """Black epoxy resin bench top: dull, the rings where the beakers stood, a burn, a spill."""
    t = F.plain(seed, (30, 30, 32), 0.03, 0.55)
    rng = t.rng
    for _ in range(6):
        disc(t, rng.uniform(4, 60), rng.uniform(4, 60), rng.uniform(2, 5), ring=0.8, col=(70, 70, 70), alpha=0.7)
    stain(t, rng.uniform(10, 54), rng.uniform(10, 54), 6, (110, 96, 40), 0.4)
    t.grime(0.1, (80, 80, 80))
    return t


# ---------------------------------------------------------------- furniture faces

def catalog(seed):
    """The card catalogue's oak face: six by five little drawers, each with its brass pull and
    the card in its frame (A - AB ...)."""
    t = F.wood(seed, "oak")
    for gy in range(0, N, 12):
        for gx in range(0, N, 11):
            F.edges(t, gx, gy, gx + 10, gy + 11, w=1)
            area(t, gx + 3, gy + 2, gx + 8, gy + 5, col=(220, 214, 190))
            area(t, gx + 2, gy + 1, gx + 9, gy + 2, col=(150, 120, 60))
            disc(t, gx + 5.5, gy + 7.5, 1.3, col=(170, 140, 70), dh=0.2)
    F.wear(t, 0.2)
    return t


def fume(seed, part):
    """The fume hood: lo the base's two doors and the sash's foot; hi the sash glass (the bottles
    and the stand behind it), the label over it and the airflow light."""
    t = F.plain(seed, (204, 200, 190), 0.03, 0.4)
    if part == "lo":
        F.edges(t, 2, 2, 31, 40, w=1)
        F.edges(t, 33, 2, 62, 40, w=1)
        F.bar(t, 26, 18, 28, 26)
        F.bar(t, 36, 18, 38, 26)
        area(t, 0, 44, N, 50, col=(30, 30, 32))
        area(t, 0, 50, N, N, col=(150, 170, 176), h=0.4)
        area(t, 0, 50, N, 52, col=(170, 172, 172))
    else:
        area(t, 0, 0, N, 10, col=(196, 192, 182))
        area(t, 10, 2, 54, 8, col=(30, 30, 32))
        t.emit[3:7, 50:53] = 1.0
        area(t, 50, 3, 53, 7, col=(240, 70, 50))
        area(t, 0, 10, N, N, col=(150, 170, 176), h=0.4)
        for x in (8, 18, 30, 44):
            area(t, x, 44, x + 5, 60, col=(90 + x, 120, 140 - x))
            area(t, x + 1, 38, x + 4, 44, col=(200, 200, 196))
        area(t, 0, 10, N, 12, col=(170, 172, 172))
        area(t, 30, 58, 34, 62, col=(120, 120, 120))
    t.grime(0.15)
    return t


def chem_shelf(seed):
    """A glass-fronted chemicals cabinet: the brown and clear bottles in rows, the labels."""
    t = F.plain(seed, (220, 218, 208), 0.03, 0.4)
    rng = t.rng
    for y0 in (4, 24, 44):
        area(t, 3, y0, 61, y0 + 17, col=(60, 66, 64))
        x = 4
        while x < 58:
            w = int(rng.integers(3, 6))
            col = (110, 64, 24) if rng.random() < 0.5 else (170, 186, 180)
            if rng.random() < 0.2:
                x += w + 1
                continue
            hgt = int(rng.integers(9, 15))
            area(t, x, y0 + 17 - hgt, x + w, y0 + 17, col=col)
            area(t, x, y0 + 17 - hgt + 3, x + w, y0 + 17 - hgt + 6, col=(232, 228, 210))
            x += w + 1
        area(t, 3, y0 + 17, 61, y0 + 19, col=(200, 198, 190))
    F.edges(t, 1, 1, 63, 63, w=2)
    t.grime(0.12)
    return t


@lru_cache(maxsize=None)
def periodic_pic():
    """The periodic table, three by two: the blocks of elements in their colours."""
    p = Pic(3, 2, (236, 232, 218), 17100)
    W, H = p.W, p.H
    p.text(W / 2, 8, "PERIODIC TABLE OF THE ELEMENTS", BOLD, 7, (30, 30, 30), anchor="mm")
    cw, ch = (W - 16) / 18, (H - 40) / 7
    layout = {1: [1, 18], 2: [1, 2] + list(range(13, 19)), 3: [1, 2] + list(range(13, 19))}
    for row in range(1, 8):
        cols = layout.get(row, list(range(1, 19)))
        for col in cols:
            x, y = 8 + (col - 1) * cw, 18 + (row - 1) * ch
            if col <= 2:
                fill = (230, 170, 120)
            elif col <= 12:
                fill = (240, 220, 140)
            elif col <= 16:
                fill = (160, 210, 170)
            elif col == 17:
                fill = (150, 200, 230)
            else:
                fill = (200, 170, 220)
            p.rect(x + 0.5, y + 0.5, x + cw - 0.5, y + ch - 0.5, fill=fill, outline=(60, 60, 60), width=0.3)
    for k in range(2):
        for col in range(3, 18):
            x, y = 8 + (col - 1) * cw, H - 18 + k * ch * 0.7
            p.rect(x + 0.5, y, x + cw - 0.5, y + ch * 0.65, fill=(240, 196, 200), outline=(60, 60, 60), width=0.3)
    return p


def periodic(i):
    t = periodic_pic().tile(i, 17110 + i, smooth=0.3, grime=0.15)
    return t


@lru_cache(maxsize=None)
def board_pic(kind):
    """The lecture hall's boards, four by two: the last lecture's calculus, or the note left after."""
    p = Pic(4, 2, GREEN_BOARD, 17200 + BOARDS.index(kind))
    rng = p.rng
    W, H = p.W, p.H
    for _ in range(40):
        x, y = rng.uniform(0, W), rng.uniform(4, H - 10)
        p.ellipse(x - rng.uniform(10, 26), y - 3, x + rng.uniform(10, 26), y + 4, fill=(110, 130, 116), a=int(rng.integers(12, 30)))
    if kind == "calc":
        p.scrawl(10, 8, "MATH 221  Lecture 19", HAND, 9, CHALK, 0.2)
        p.scrawl(10, 26, "d/dx [ sin(x^2) ] = 2x cos(x^2)|||  integral 0..pi  sin x dx = 2|||  lim (1 + 1/n)^n = e", HAND, 7.5, CHALK, 0.25)
        p.scrawl(140, 24, "Chain rule:|  (f o g)' = f'(g) g'||Ex. 4  y = e^(3x^2)|  y' = 6x e^(3x^2)", HAND, 7.5, CHALK, 0.25)
        p.ellipse(150, 82, 200, 112, outline=CHALK, width=1.2)
        p.line([(140, 100), (240, 100)], CHALK, 0.8)
        p.line([(150, 120), (150, 70)], CHALK, 0.8)
        p.scrawl(10, 104, "MIDTERM  FRI  (ch. 3-5)", HAND, 8, (250, 220, 120), 0.25)
        p.scrawl(176, 10, "OFFICE HRS CANCELLED", HAND, 6.5, (246, 180, 190), 0.3)
    else:
        p.scrawl(14, 8, "ALL CLASSES CANCELLED", HAND, 13, CHALK, 0.2)
        p.scrawl(16, 34, "by order of the Governor|until further notice", OBLIQUE, 8, CHALK, 0.3)
        p.scrawl(16, 64, "STAY IN THE DORMS.|Lock your doors. Do NOT|go out after dark.", HAND, 8.5, (250, 220, 120), 0.3)
        p.scrawl(150, 40, "buses to the|STADIUM shelter|from the gate|8am + noon", HAND, 8, CHALK, 0.3)
        p.scrawl(150, 98, "last bus left. nobody came back", OBLIQUE, 6.5, (246, 160, 170), 0.35)
        p.line([(146, 86), (238, 84)], CHALK, 1.0, a=200)
    p.rect(0, 0, W, 2, fill=(150, 152, 150))
    p.rect(0, H - 6, W, H, fill=(150, 152, 150))
    return p


def board(kind, i):
    t = board_pic(kind).tile(i, 17210 + BOARDS.index(kind) * 9 + i, smooth=0.15, grime=0.06)
    chalky(t, (0, N, 0, N), 0.3)
    return t


POSTERS = [
    ("THE VOLTS", "LIVE AT THE|ARMORY", (220, 40, 120), (20, 20, 40)),
    ("NIGHT DRIVE", "NOW PLAYING", (250, 120, 30), (30, 20, 60)),
    ("SPRING|BREAK '87", "SEABRIGHT", (40, 190, 210), (250, 230, 120)),
    ("OSPREYS", "GO STATE!", (240, 230, 210), MAROON),
    ("NEON RIOT", "WORLD TOUR", (60, 230, 120), (14, 14, 20)),
    ("THINK.", "", (20, 20, 20), (228, 224, 210)),
]


@lru_cache(maxsize=None)
def poster_pic(k):
    title, sub, ink, field = POSTERS[k]
    p = Pic(1, 1, field, 17300 + k)
    W = p.W
    p.rect(3, 3, W - 3, W - 3, outline=ink, width=0.8)
    size = civic.fit(title.split("|")[0], BLACK, 12, W - 10)
    p.text(W / 2, 14, title, BLACK, size, ink, anchor="mm")
    if sub:
        p.text(W / 2, 46, sub, BOLD, 5, ink, anchor="mm")
    if k == 4:
        for r in (6, 10, 14):
            p.ellipse(W / 2 - r, 34 - r / 2, W / 2 + r, 34 + r / 2, outline=ink, width=0.6)
    return p


def poster(k):
    t = poster_pic(k).tile(0, 17310 + k, smooth=0.25, grime=0.18)
    if k % 2:
        area(t, 54, 0, N, 10, mul=0.75)  # the corner come unstuck
    return t


@lru_cache(maxsize=None)
def bulletin_pic(kind):
    """A cork board two by one, its flyers pinned four deep."""
    p = Pic(2, 1, (168, 126, 78), 17400 + BULLETINS.index(kind))
    rng = p.rng
    W, H = p.W, p.H
    for _ in range(300):
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        p.rect(x, y, x + 1, y + 1, fill=(120, 84, 50))
    notes = {
        "vigil": [("CANDLELIGHT|VIGIL|the Quad 8pm", (250, 246, 210)), ("MISSING|Kelly Ann R.|Hawthorne 6", (255, 255, 255)),
                  ("SHUTTLE TO|STADIUM SHELTER|from the gate", (250, 220, 120)), ("WHERE IS|EVERYONE??", (210, 236, 255))],
        "rooms": [("ROOMMATE|WANTED|no smokers", (255, 255, 255)), ("LOST CAT|answers to|Biscuit", (250, 230, 200)),
                  ("TYPING|$1 a page|call Dee", (210, 250, 210)), ("BAKE SALE|Fri 11-2", (250, 210, 230))],
    }[kind]
    for k, (text, col) in enumerate(notes):
        x = 6 + k * 30 + rng.uniform(-2, 2)
        y = 6 + (k % 2) * 6 + rng.uniform(-2, 2)
        p.rect(x, y, x + 26, y + 36, fill=col, outline=(150, 150, 140), width=0.3)
        p.text(x + 13, y + 6, text, BOLD, 3.6, (30, 30, 34), anchor="ma")
        p.ellipse(x + 11, y - 1, x + 15, y + 3, fill=(220, 40, 40))
    p.rect(0, 0, W, H, outline=(150, 112, 70), width=2.5)
    return p


def bulletin(kind, i):
    return bulletin_pic(kind).tile(i, 17410 + BULLETINS.index(kind) * 3 + i, smooth=0.2, grime=0.15)


@lru_cache(maxsize=None)
def banner_pic():
    p = Pic(4, 1, MAROON, 17500)
    W, H = p.W, p.H
    p.rect(2, 4, W - 2, H - 4, outline=CREAM, width=1)
    p.text(W / 2, H / 2, "WELCOME BACK OSPREYS", BLACK, civic.fit("WELCOME BACK OSPREYS", BLACK, 22, W - 20), CREAM, anchor="mm")
    return p


def banner(i):
    t = banner_pic().tile(i, 17510 + i, smooth=0.1, grime=0.2)
    if i == 3:
        t.alpha[:, 40:] = 0  # torn off at the end
        for y in range(N):
            t.alpha[y, 36 + int(4 * np.sin(y / 3)):40] = 0
    return t


@lru_cache(maxsize=None)
def map_pic():
    """The campus map, three by two, YOU ARE HERE at the gate."""
    p = Pic(3, 2, (240, 236, 222), 17600)
    W, H = p.W, p.H
    p.rect(0, 0, W, 14, fill=MAROON)
    p.text(W / 2, 7, "GRAYHAVEN STATE UNIVERSITY  -  CAMPUS MAP", BOLD, 6, CREAM, anchor="mm")
    ox, oy, s = 30, 20, 0.62
    p.rect(ox, oy, ox + 213 * s, oy + 160 * s, fill=(200, 222, 180))
    def b(x0, z0, x1, z1, label, col=(180, 120, 100)):
        p.rect(ox + x0 * s, oy + z0 * s * 0.75, ox + x1 * s, oy + z1 * s * 0.75, fill=col, outline=(80, 60, 50), width=0.4)
        p.text(ox + (x0 + x1) / 2 * s, oy + (z0 + z1) / 2 * s * 0.75, label, BOLD, 3.4, (20, 20, 20), anchor="mm")
    b(8, 52, 58, 120, "LIBRARY", (170, 170, 164))
    b(8, 128, 58, 200, "LECTURE")
    b(156, 52, 206, 120, "SCIENCE", (200, 180, 120))
    b(156, 128, 206, 200, "UNION")
    b(66, 164, 146, 206, "OLD MAIN")
    b(152, 10, 206, 40, "DORM")
    b(8, 8, 74, 44, "P", (150, 150, 150))
    p.rect(ox + 102 * s, oy, ox + 110 * s, oy + 160 * s, fill=(210, 190, 170))
    p.ellipse(ox + 104 * s - 3, oy + 78 * s - 2, ox + 104 * s + 3, oy + 78 * s + 4, fill=(220, 30, 30))
    p.text(ox + 104 * s + 5, oy + 76 * s, "YOU ARE HERE", BOLD, 3.2, (200, 20, 20))
    return p


def campus_map(i):
    return map_pic().tile(i, 17610 + i, smooth=0.3, grime=0.2)


@lru_cache(maxsize=None)
def seal_pic():
    """The university's seal in bronze, three by three: the lamp of learning over the waves."""
    p = Pic(3, 3, (0, 0, 0, 0), 17700)
    W = p.W
    p.ellipse(4, 4, W - 4, W - 4, fill=(120, 88, 48), outline=(70, 50, 28), width=2)
    p.ellipse(28, 28, W - 28, W - 28, fill=(140, 104, 58), outline=(70, 50, 28), width=1.2)
    cx = W / 2
    p.poly([(cx - 18, 110), (cx + 18, 110), (cx + 10, 96), (cx - 10, 96)], fill=(176, 140, 80))
    p.poly([(cx - 4, 96), (cx + 4, 96), (cx, 74)], fill=(220, 180, 100))
    for k in range(3):
        y = 124 + k * 8
        p.line([(52 + j * 8, y + (j % 2) * 3) for j in range(12)], (90, 64, 36), 1.4)
    import math
    text = "GRAYHAVEN  STATE  UNIVERSITY  *  1891  *"
    r = W / 2 - 15
    for k, ch in enumerate(text):
        a = math.pi * 2 * k / len(text) - math.pi / 2
        p.text(cx + math.cos(a) * r, cx + math.sin(a) * r, ch, SERIF, 8, (210, 176, 110), anchor="mm")
    return p


def seal(i):
    t = seal_pic().tile(i, 17710 + i, smooth=0.45, grime=0.15)
    t.f0[t.alpha > 128] = 230
    t.streaks(0.4)
    return t


@lru_cache(maxsize=None)
def name_pic(key):
    text, font, cols, style = NAMES[key]
    bg = (176, 170, 156, 255) if style == "carved" else (0, 0, 0, 0)
    p = Pic(cols, 1, bg, 17800 + len(key))
    if style == "carved":
        p.rect(0, 0, p.W, 3, fill=(150, 144, 130))
        p.rect(0, N - 3, p.W, N, fill=(150, 144, 130))
        size = civic.fit(text, font, 44, p.W - 30)
        p.text(p.W / 2 + 1, N / 2 + 1, text, font, size, (226, 220, 206), anchor="mm")
        p.text(p.W / 2, N / 2, text, font, size, (70, 64, 56), anchor="mm")
    else:
        col = {"bronze": (150, 108, 56), "alu": (200, 202, 200), "steel": (40, 40, 42)}[style]
        letters(p, text, font, 6, 6, p.W - 6, N - 6, col, True, stroke=0.12)
    return p


def name(key, i):
    style = NAMES[key][3]
    t = name_pic(key).tile(i, 17810 + i + len(key) * 23, smooth=0.5 if style != "carved" else 0.2, grime=0.15)
    if style in ("bronze", "alu"):
        t.f0[t.alpha > 128] = 230 if style == "bronze" else 220
    if style == "carved":
        t.streaks(0.5)
    return t


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 17900 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 16, W - 1, H - 16, fill=field)
    p.rect(3, 18, W - 3, H - 18, outline=tuple(min(255, v + 50) for v in field), width=0.5)
    p.text(W / 2, H / 2, text, SERIF, civic.fit(text, SERIF, 15, W - 16), ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 17950 + i + len(key) * 11, smooth=0.5, grime=0.12)


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 18000 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (210, 24, 30) if key != "gohome" else (30, 30, 30), jitter=0.4, a=235, angle=-2)
    return p


def spray(key, i):
    t = spray_pic(key).tile(i, 18010 + i + len(key) * 5, smooth=0.3, grime=0.0)
    lum = t.alpha > 20
    for _ in range(4):
        x, y = int(t.rng.integers(4, 60)), int(t.rng.integers(30, 50))
        if lum[y, x]:
            drips(t, x, x + 1, y, 1, (4, 12), col=(180, 20, 20) if key != "gohome" else (30, 30, 30))
            t.alpha[y:y + 12, x:x + 1] = 230
    return t


def notice(seed):
    p = Pic(1, 1, (238, 236, 226), seed)
    W = p.W
    p.text(W / 2, 8, "CAMPUS CLOSED", BLACK, 6.5, (180, 20, 20), anchor="mm")
    p.text(W / 2, 22, "By order of the|Governor all classes|are suspended.|Residents report to|the STUDENT UNION.", BOLD, 3.6, (30, 30, 30), anchor="ma")
    p.text(W / 2, 56, "- Office of the President", OBLIQUE, 3.2, (30, 30, 30), anchor="mm")
    t = p.tile(0, seed + 1, smooth=0.2, grime=0.2)
    t.streaks(0.4)
    return t


def arcade(seed, on):
    """ASTRO RAID: the marquee over the screen, the attract mode still running if there's power."""
    t = F.plain(seed, (24, 22, 40), 0.03, 0.4)
    area(t, 0, 0, N, 14, col=(250, 140, 30))
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(32, 7, "ASTRO RAID", BLACK, 8, (30, 20, 60), anchor="mm")
    m = p.tile(0, seed, smooth=0.3, grime=0)
    a = m.alpha / 255
    t.c[:] = t.c * (1 - a[..., None]) + m.c * a[..., None]
    area(t, 8, 20, 56, 56, col=(10, 14, 12) if not on else (14, 24, 40))
    if on:
        rng = t.rng
        for _ in range(30):
            x, y = int(rng.integers(10, 54)), int(rng.integers(22, 54))
            area(t, x, y, x + 2, y + 1, col=(240, 240, 120))
        area(t, 26, 48, 38, 52, col=(80, 240, 120))
        t.emit[20:56, 8:56] = 0.8
        t.emit[0:14] = 0.6
    t.grime(0.15)
    return t


def felt(seed):
    t = F.plain(seed, (34, 98, 60), 0.04, 0.05)
    t.speckle(0.2, (24, 76, 46), 0.5)
    stain(t, 30, 30, 8, (70, 50, 30), 0.3)
    return t


def textbooks(seed):
    t = F.books(seed)
    rng = t.rng
    for y0 in (4, 24, 44):
        x = int(rng.integers(2, 10))
        area(t, x, y0 + 2, x + 10, y0 + 6, col=(250, 220, 60))  # the USED price stickers
    return t


def textures():
    T = {}
    T["un_brut"] = lambda: brut(17000)
    T["un_brut_b"] = lambda: brut(17001, stained=True)
    T["un_brut_rib"] = lambda: brut(17002, ribbed=True)
    T["un_carpet"] = lambda: carpet(17003)
    T["un_carpet_b"] = lambda: carpet(17004, worn=True)
    T["un_carpet_dorm"] = lambda: carpet(17005, (78, 88, 104), (96, 108, 124), worn=True)
    T["un_paver"] = lambda: pavers(17006)
    T["un_paver_b"] = lambda: pavers(17007, mossy=True)
    T["un_hedge"] = lambda: hedge(17008)
    T["un_hedge_dead"] = lambda: hedge(17009, dead=True)
    T["un_epoxy"] = lambda: epoxy(17010)
    T["un_catalog"] = lambda: catalog(17011)
    T["un_fume_lo"] = lambda: fume(17012, "lo")
    T["un_fume_hi"] = lambda: fume(17013, "hi")
    T["un_chem"] = lambda: chem_shelf(17014)
    T["un_felt"] = lambda: felt(17015)
    T["un_textbooks"] = lambda: textbooks(17016)
    T["un_arcade"] = lambda: arcade(17017, False)
    T["un_arcade_on"] = lambda: arcade(17018, True)
    T["un_notice"] = lambda: notice(17019)
    for i in range(6):
        T[f"un_periodic_{i}"] = lambda i=i: periodic(i)
        T[f"un_poster_{i}"] = lambda i=i: poster(i)
        T[f"un_map_{i}"] = lambda i=i: campus_map(i)
    for kind in BOARDS:
        for i in range(8):
            T[f"un_board_{kind}_{i}"] = lambda kind=kind, i=i: board(kind, i)
    for kind in BULLETINS:
        for i in range(2):
            T[f"un_bulletin_{kind}_{i}"] = lambda kind=kind, i=i: bulletin(kind, i)
    for i in range(4):
        T[f"un_banner_{i}"] = lambda i=i: banner(i)
    for i in range(9):
        T[f"un_seal_{i}"] = lambda i=i: seal(i)
    for key, (_, _, cols, _) in NAMES.items():
        for i in range(cols):
            T[f"un_name_{key}_{i}"] = lambda key=key, i=i: name(key, i)
    for key in SIGNS:
        for i in range(2):
            T[f"un_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"un_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
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

    oak, steel, chrome, black, white = "cv_oak", "hs_steel", "home_chrome", "home_plastic_black", "home_white"
    grey, brass, paper = "bunk_grey", "cv_brass", "paper"

    def face(front, side):
        return {"north": front, "side": side, "up": side, "down": side}

    # ---- floors and ceilings
    for name in ("un_carpet", "un_carpet_b", "un_paver", "un_paver_b"):
        add(name, None, "Brick Pavers" if "paver" in name else "Carpet", "STONE" if "paver" in name else "WOOL", rotate=None,
            tex={"up": name, "side": "floor_concrete", "down": "floor_concrete"})
    slabs = {"un_slab_carpet": "un_carpet", "un_slab_carpet_b": "un_carpet_b", "un_slab_vct": "sc_vct", "un_slab_dorm": "un_carpet_dorm"}
    for name, top in slabs.items():
        add(name, None, "Floor", "STONE", rotate=None, tex={"up": top, "down": "hs_ceiling", "side": "floor_concrete"})
        add(name + "_lamp", None, "Floor", "STONE", rotate=None, tex={"up": top, "down": "hs_troffer", "side": "floor_concrete"})
        add(name + "_lamp_on", None, "Floor", "STONE", rotate=None, tex={"up": top, "down": "hs_troffer_on", "side": "floor_concrete"}, light=13)
    add("un_slab_boards", None, "Floor", "WOOD", rotate=None, tex={"up": "floor_boards", "down": "cv_plaster", "side": "floor_concrete"})
    add("un_slab_terrazzo", None, "Floor", "STONE", rotate=None, tex={"up": "cv_terrazzo", "down": "cv_plaster", "side": "floor_concrete"})
    add("un_roof", None, "Roof", "STONE", rotate=None, tex={"up": "hs_deck", "down": "hs_ceiling", "side": "concrete_dark"})
    add("un_roof_lamp", None, "Roof", "STONE", rotate=None, tex={"up": "hs_deck", "down": "hs_troffer", "side": "concrete_dark"})
    add("un_roof_lamp_on", None, "Roof", "STONE", rotate=None, tex={"up": "hs_deck", "down": "hs_troffer_on", "side": "concrete_dark"}, light=13)

    # ---- walls and the outside
    for name in ("un_brut", "un_brut_b", "un_brut_rib"):
        add(name, None, "Concrete", "STONE", rotate=None, tex={"all": name})
    add("un_hedge", None, "Hedge", "GRASS", rotate=None, tex={"all": "un_hedge"})
    add("un_hedge_low", [[0, 0, 0, 16, 10, 16, "un_hedge"]], "Hedge", "GRASS", rotate=None, shape=[[0, 0, 0, 16, 10, 16]])
    add("un_hedge_dead", [[0, 0, 0, 16, 10, 16, "un_hedge_dead"], [3, 10, 4, 6, 14, 7, "un_hedge_dead"], [10, 10, 9, 13, 13, 12, "un_hedge_dead"]],
        "Dead Hedge", "GRASS", rotate=None, shape=[[0, 0, 0, 16, 10, 16]])
    # the library's slot window: a deep concrete reveal round a narrow pane set back (facing: out)
    for suffix, glass in (("", "tx_glass"), ("_broken", "tx_glass_broken")):
        add(f"un_slot{suffix}", [[0, 0, 0, 5, 16, 16, "un_brut"], [11, 0, 0, 16, 16, 16, "un_brut"],
                                 [5, 0, 10, 11, 1, 16, "un_brut"], [5, 15, 10, 11, 16, 16, "un_brut"],
                                 [5, 1, 11.4, 11, 15, 11.6, glass], [5, 0, 0, 11, 1, 10, "un_brut_b"]],
            "Window", "GLASS", render="translucent", shape=[[0, 0, 0, 16, 16, 16]])

    # ---- the library
    add("un_catalog", [[0, 0, 2, 16, 15, 16, face("un_catalog", oak)], [0, 15, 1, 16, 16, 16, oak]], "Card Catalogue", "WOOD", loot="office")
    add("un_carrel", [[0, 10, 4, 16, 11, 16, oak], [0, 0, 4, 1.5, 16, 16, "cubicle_fabric"], [14.5, 0, 4, 16, 16, 16, "cubicle_fabric"],
                      [0, 0, 14.5, 16, 16, 16, "cubicle_fabric"], [1.5, 13, 12, 14.5, 14, 14.5, oak], [3, 11, 8, 9, 11.6, 12, "cv_books_a"]],
        "Study Carrel", "WOOD", loot="office")
    add("un_table", [[0, 11, 0, 16, 12.5, 16, oak], [1, 0, 1, 3, 11, 3, oak], [13, 0, 1, 15, 11, 3, oak], [1, 0, 13, 3, 11, 15, oak], [13, 0, 13, 15, 11, 15, oak]],
        "Reading Table", "WOOD")
    for suffix, glow in (("", "home_plastic_black"), ("_on", "cv_globe_lit")):
        add(f"un_table_lamp{suffix}", [[0, 11, 0, 16, 12.5, 16, oak], [1, 0, 1, 3, 11, 3, oak], [13, 0, 1, 15, 11, 3, oak], [1, 0, 13, 3, 11, 15, oak],
                                       [13, 0, 13, 15, 11, 15, oak], [6, 12.5, 6, 10, 13, 10, brass], [7.5, 13, 7.5, 8.5, 15, 8.5, brass],
                                       [5, 15, 6.5, 11, 16, 9.5, {"all": "dn_enamel_green", "down": glow}]],
            "Reading Table", "WOOD", **({"light": 9} if suffix else {}))
    add("un_circ", [[0, 0, 2, 16, 14, 16, face(oak, oak)], [0, 14, 1, 16, 15.5, 16, "hs_counter"], [0, 0, 1, 16, 2, 2, "home_plastic_black"]],
        "Circulation Desk", "WOOD", loot="office")
    add("un_circ_end", [[0, 0, 2, 16, 14, 16, face(oak, oak)], [0, 14, 1, 16, 15.5, 16, "hs_counter"], [10, 15.5, 6, 15, 16, 12, paper]],
        "Circulation Desk", "WOOD", loot="office")
    add("un_microfiche", [[0, 10, 2, 16, 11.5, 16, "hs_laminate"], [1, 0, 3, 3, 10, 15, grey], [13, 0, 3, 15, 10, 15, grey],
                          [3, 11.5, 6, 13, 16, 15, {"north": "hs_screen", "side": grey, "up": grey, "down": grey}]],
        "Microfiche Reader", "METAL")
    add("un_magrack", [[0, 0, 12, 16, 16, 16, oak], [0, 1, 9, 16, 5, 12, "cv_books_b"], [0, 6, 9, 16, 10, 12, "cv_books_a"], [0, 11, 10, 16, 15, 12, "home_books"]],
        "Periodicals Rack", "WOOD", loot="office")

    # ---- the labs
    bench = [[0, 0, 2, 16, 13, 16, face("hs_cart_grey", "hs_enamel")], [0, 13, 0, 16, 15, 16, "un_epoxy"]]
    add("un_lab_bench", bench + [[12, 15, 12, 13, 18, 13, brass], [12, 17, 10.5, 13, 18, 13, brass]], "Lab Bench", "STONE", loot="pharmacy")
    add("un_lab_sink", bench[:1] + [[0, 13, 0, 16, 15, 3, "un_epoxy"], [0, 13, 13, 16, 15, 16, "un_epoxy"], [0, 13, 3, 3, 15, 13, "un_epoxy"],
                                    [13, 13, 3, 16, 15, 13, "un_epoxy"], [3, 12, 3, 13, 13, 13, "un_epoxy"], [7.5, 15, 13, 8.5, 22, 14, chrome],
                                    [7.5, 21, 9, 8.5, 22, 14, chrome]], "Lab Sink", "STONE")
    add("un_lab_scope", bench + [[5, 15, 6, 11, 15.6, 11, black], [7, 15.6, 9, 9, 21, 11, black], [7.2, 19, 5, 8.8, 22, 9, black],
                                 [6, 17, 6, 10, 17.6, 10, chrome]], "Lab Bench", "STONE", loot="pharmacy")
    add("un_fume_lo", [[0, 0, 2, 16, 16, 16, face("un_fume_lo", "hs_enamel_white")]], "Fume Hood", "METAL", loot="pharmacy")
    add("un_fume_hi", [[0, 0, 2, 16, 14, 16, face("un_fume_hi", "hs_enamel_white")], [0, 14, 4, 16, 16, 16, "hs_enamel_white"],
                       [6, 16, 8, 10, 32, 12, steel]], "Fume Hood", "METAL")
    add("un_chem_shelf", [[0, 0, 4, 16, 16, 16, face("un_chem", "hs_enamel")]], "Chemicals Cabinet", "GLASS", loot="pharmacy")
    add("un_skeleton_lo", [[6, 0, 6, 10, 1, 10, steel], [7.6, 1, 7.6, 8.4, 6, 8.4, steel], [5.5, 6, 7.5, 7, 16, 8.5, "home_white"],
                           [9, 6, 7.5, 10.5, 16, 8.5, "home_white"], [6, 13, 7, 10, 16, 9, "home_white"]], "Anatomy Skeleton", "STONE",
        shape=[[4, 0, 4, 12, 16, 12]])
    add("un_skeleton_hi", [[7.2, 0, 7.4, 8.8, 7, 8.6, "home_white"], [5, 4, 7, 11, 9, 9, "home_white"], [4, 0, 7.6, 5, 9, 8.4, "home_white"],
                           [11, 0, 7.6, 12, 9, 8.4, "home_white"], [6, 9, 6, 10, 14, 10, "home_white"], [6.6, 11, 5.8, 7.6, 12, 6, black],
                           [8.4, 11, 5.8, 9.4, 12, 6, black]], "Anatomy Skeleton", "STONE", shape=[[4, 0, 4, 12, 14, 12]])
    add("un_stool", [[4, 9, 4, 12, 10.5, 12, black], [7.4, 0, 7.4, 8.6, 9, 8.6, steel], [4.5, 0, 4.5, 11.5, 0.5, 11.5, steel],
                     [5, 4, 7.5, 11, 4.6, 8.5, steel]], "Lab Stool", "METAL", rotate=None)

    # ---- the dorms
    add("un_desk", [[0, 11, 4, 16, 12.5, 16, "home_oak"], [0, 0, 4, 5, 11, 16, face("home_oak", "home_oak")], [14, 0, 4, 16, 11, 16, "home_oak"],
                    [7, 12.5, 10, 12, 14, 15, "cv_books_a"], [2, 12.5, 12, 3, 17, 13, steel], [1, 17, 11, 4.5, 19, 14, "dn_enamel_green"]],
        "Desk", "WOOD", loot="office")
    add("un_wardrobe_lo", [[0, 0, 4, 16, 16, 16, face("home_oak", "home_oak")], [7.5, 6, 3.4, 8.5, 10, 4, brass]], "Closet", "WOOD", loot="clothes")
    add("un_wardrobe_hi", [[0, 0, 4, 16, 14, 16, face("home_oak", "home_oak")], [0, 14, 3.5, 16, 16, 16, "home_oak"]], "Closet", "WOOD")
    add("un_minifridge", [[2, 0, 4, 14, 13, 15, face("home_white", "home_white")], [11, 5, 3.4, 12, 9, 4, chrome],
                          [4, 13, 6, 12, 13.5, 13, "cv_books_b"]], "Mini Fridge", "METAL", loot="food")
    add("un_beanbag", [[2, 0, 2, 14, 6, 14, "dn_vinyl_r"], [3, 6, 6, 13, 11, 14, "dn_vinyl_r"]], "Beanbag", "WOOL")
    add("un_records", [[2, 0, 3, 14, 9, 13, black], [3, 1, 4, 13, 9.5, 12, "cv_books_b"]], "Record Crate", "WOOD", loot="scraps")
    for i in range(6):
        add(f"un_poster_{i}", [[1, 1, 15.6, 15, 15, 16, f"un_poster_{i}"]], "Poster", "WOOL", decor=True)

    # ---- the union
    add("un_arcade_lo", [[1, 0, 3, 15, 16, 16, face("home_plastic_black", "home_plastic_black")], [1, 12, 0, 15, 15, 5, black],
                         [4, 15, 1.5, 6, 16.5, 3.5, "dn_vinyl_r"], [9, 15, 2, 10, 15.5, 3, "dn_vinyl_r"], [11, 15, 2, 12, 15.5, 3, "dn_vinyl_b"]],
        "Arcade Cabinet", "METAL", loot="scraps")
    for suffix in ("", "_on"):
        add(f"un_arcade_hi{suffix}", [[1, 0, 3, 15, 16, 16, face(f"un_arcade{suffix}", "home_plastic_black")]], "Arcade Cabinet", "METAL",
            **({"light": 7} if suffix else {}))
    add("un_pool_a", [[0, 10, 0, 16, 12, 16, "un_felt"], [0, 10, 0, 16, 13, 1.5, oak], [0, 10, 14.5, 16, 13, 16, oak], [0, 10, 0, 1.5, 13, 16, oak],
                      [2, 0, 2, 6, 10, 14, oak], [2, 12, 1.5, 3.5, 12.4, 3, black], [8, 12, 7, 9, 12.6, 8, white], [11, 12, 9, 12, 12.6, 10, "dn_vinyl_r"]],
        "Pool Table", "WOOD")
    add("un_pool_b", [[0, 10, 0, 16, 12, 16, "un_felt"], [0, 10, 0, 16, 13, 1.5, oak], [0, 10, 14.5, 16, 13, 16, oak], [14.5, 10, 0, 16, 13, 16, oak],
                      [10, 0, 2, 14, 10, 14, oak], [4, 12, 5, 5, 12.6, 6, "dn_vinyl_b"], [-6, 12.4, 12, 14, 12.8, 12.4, "home_oak"]],
        "Pool Table", "WOOD")
    add("un_textbooks", [[0, 0, 4, 16, 16, 16, face("un_textbooks", "home_oak")]], "Textbooks", "WOOD", loot="office")

    # ---- plates on the walls (the back at z = 16)
    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    for i in range(6):
        add(f"un_periodic_{i}", plate(f"un_periodic_{i}", 15.7), "Periodic Table", "WOOL", decor=True)
    for kind in BOARDS:
        for i in range(8):
            add(f"un_board_{kind}_{i}", plate(f"un_board_{kind}_{i}", 15.0), "Chalkboard", "STONE", decor=True)
    for kind in BULLETINS:
        for i in range(2):
            add(f"un_bulletin_{kind}_{i}", plate(f"un_bulletin_{kind}_{i}", 15.0), "Bulletin Board", "WOOD", decor=True)
    for i in range(4):
        add(f"un_banner_{i}", plate(f"un_banner_{i}", 15.6), "Banner", "WOOL", decor=True)
    for i in range(9):
        add(f"un_seal_{i}", plate(f"un_seal_{i}", 14.8), "University Seal", "METAL", decor=True)
    for key, (_, _, cols, style) in NAMES.items():
        for i in range(cols):
            add(f"un_name_{key}_{i}", plate(f"un_name_{key}_{i}", 14.6 if style != "carved" else 15.0), "Lettering", "METAL", decor=True)
    for key in SIGNS:
        for i in range(2):
            add(f"un_sgn_{key}_{i}", plate(f"un_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"un_spray_{key}_{i}", plate(f"un_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)
    add("un_notice", plate("un_notice", 15.7), "Notice", "WOOL", decor=True)
    # the campus map on its two posts: the board in the middle of the block, the posts under the bottom row
    for i in range(6):
        boxes = [[0, 0, 7.5, 16, 16, 8.5, {"north": f"un_map_{i}", "south": "cv_oak", "side": "cv_oak", "up": "cv_oak", "down": "cv_oak"}]]
        if i == 3:
            boxes.append([2, -16, 7, 4, 0, 9, "hs_steel"])
        if i == 5:
            boxes.append([12, -16, 7, 14, 0, 9, "hs_steel"])
        add(f"un_map_{i}", boxes, "Campus Map", "WOOD", shape=[[0, 0, 7.5, 16, 16, 8.5]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("un_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} university blocks in {path}")


if __name__ == "__main__":
    main()
