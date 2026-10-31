"""The as_ kit: the auto shops (Josiah 2026-10-31: an auto shop in every town sized to the town,
more in the city, large shops full of tools and car parts, working hoists, after Zomboid's
mechanic shops and their containers), after refs/autoshop/{garage-interior, two-post-lift},
refs/industry/{scrap-yard-magnets, machine-shop} and refs/roadside/truck-stop.
world/county/AutoShop sets them out; the two-post hoists themselves are VEHICLES' car_hoist
entities (Placer.hoist), not blocks.

Every facing piece is drawn with its front to the north (z = 0) and rotate=facing, so facing is
the way the front looks; wall pieces hang on the wall to the south as drawn.

- as_chest_<paint>_lo / _hi: the mechanic's roll cabinet and the top chest on it (red, blue,
  black, olive for the motor pools); as_cart: the rolling utility cart; as_bench /
  as_bench_vise / as_bench_grinder: the steel bench, with the vise or the grinder on it;
  as_pegwall_a / _b: the tool wall; as_creeper, as_stands (jack stands), as_drain (the orange
  waste-oil drain cart with its funnel), as_press_lo / _hi (the H-frame shop press), as_welder,
  as_torch (the oxy-acetylene cart), as_charger (the battery charger), as_analyzer_lo / _hi (the
  engine analyzer with its screen);
- the fluids: as_drum_oil / _atf / _coolant (a 55-gallon drum with its hand pump), as_tote (the
  waste-oil tote in its cage), as_reels (the wall bank of hose reels: air, oil, ATF, grease),
  as_oilrack (cases of quarts);
- the tyres: as_tyre_rack_lo / _hi (the tall rack, full), as_tyre_rack_low (picked over),
  as_tyre_stack, as_wheels (alloys stacked), as_tyre_cage (the scrap tyres out back),
  as_tyre_sale (the stand out front);
- the parts: as_shelf_filters / _brakes / _boxes / _batteries (the parts room's steel shelving,
  lo and hi), as_belts (belts and hoses hanging on the wall), as_pipes (the exhaust pipe rack),
  as_counter_parts / as_counter_end (the parts counter with the catalog and the terminal),
  as_cores (the core-return bin); loose: as_engine_stand, as_engine (a pulled engine on a
  pallet), as_trans, as_hood / as_cardoor / as_bumper (leaning on a wall), as_radiator, as_seat;
- the office: as_keyboard (the key board), as_desk (the service writer's desk), as_chairs_l /
  _r (the waiting room's row of chairs), as_coffee, as_certs (the framed certificates), as_rate
  (the labor rate board), as_clock;
- the building: as_metal / _tan / _rust (the metal building's ribbed panels), as_floor /
  _oil / _drain / _epoxy / _stripe, as_highbay(_lit) and as_strip(_lit) (the shop lights),
  as_coil (a rolled-up door's drum over the opening), as_opener (the door opener on its rail),
  as_column (the steel building's column), as_slat / as_slat_bar (the truck hall's roll-up
  door);
- the truck hall: as_pit_curb (the pit's yellow edge), as_pit_wall, as_pit_lamp, as_pit_steps,
  as_column_lift_lo / _hi (the mobile column lifts), as_bigtyre, as_grease (the grease cart);
- the scrapyard: as_flat_<paint> (a crushed car, rotate=axis, stacks), as_crusher_* (the
  portable car crusher), as_magnet (the crane's magnet on its chains), as_tin / as_tin_gate (the
  corrugated fence), as_scrap (a heap of scrap), as_partsbin (a bin of pulled parts), as_scale
  (the truck scale's deck);
- as_s_<sign>_<col><row>: the shop signs, one block a tile, col 0 at the reader's left;
  as_sign_<kind>: the small signs (bay numbers, office, customers keep out, parts).

    python3 tools/city/autoshop.py      rewrites the as_ entries in blocks.json

photo_textures.py draws the textures (autoshop.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area, disc, edges  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import chips  # noqa: E402
import highway as HW  # noqa: E402

N = P.N
HERE = Path(__file__).parent
SANS, SERIF = HW.SANS, HW.SERIF
WHITE, BLACK, YELLOW = HW.WHITE, HW.BLACK, HW.YELLOW
RED, BLUE, NAVY = (178, 30, 28), (24, 70, 150), (24, 34, 64)
OD, ORANGE, CREAM = (84, 88, 52), (226, 104, 26), (236, 226, 196)
STEEL, GALV, RUBBER = "as_steel", "as_galv", "as_rubber"

# the signs: tiles across and up, the board, the lettering, its lines (text, size in pixels at 64
# a block, the line's middle as a share of the height from the top, font). Fictional names only.
SIGNS = {
    # the village garages: the owner's name hand-painted on a board over the door
    "holloway": dict(cols=5, rows=1, bg=(196, 42, 34), fg=CREAM, lines=[("HOLLOWAY'S GARAGE", 30, 0.52, SERIF)], wood=True),
    "pruitt": dict(cols=5, rows=1, bg=(34, 74, 52), fg=CREAM, lines=[("PRUITT GARAGE & TOWING", 26, 0.52, SERIF)], wood=True),
    "dean": dict(cols=5, rows=1, bg=(32, 52, 104), fg=CREAM, lines=[("DEAN'S GARAGE", 32, 0.52, SERIF)], wood=True),
    # the independent repair shops on main street
    "seabright": dict(cols=6, rows=1, bg=WHITE, fg=(28, 60, 130), lines=[("SEABRIGHT AUTO SERVICE", 30, 0.52, SANS)]),
    "millbrook": dict(cols=6, rows=1, bg=(250, 214, 40), fg=BLACK, lines=[("MILLBROOK AUTO REPAIR", 30, 0.52, SANS)]),
    "kessler": dict(cols=6, rows=1, bg=WHITE, fg=(160, 26, 26), lines=[("KESSLER AUTO REPAIR", 32, 0.52, SANS)]),
    # the tyre and exhaust chain in the county seats
    "roadking": dict(cols=6, rows=1, bg=(20, 20, 24), fg=(250, 206, 30), lines=[("ROADKING", 34, 0.36, SANS),
                                                                               ("TIRE & EXHAUST CENTER", 17, 0.78, SANS)]),
    # the city's dealer service centre, its parts counter
    "motors": dict(cols=7, rows=1, bg=NAVY, fg=WHITE, lines=[("GRAYHAVEN MOTORS", 32, 0.36, SERIF), ("SERVICE  ·  PARTS", 17, 0.78, SANS)]),
    "service": dict(cols=3, rows=1, bg=NAVY, fg=WHITE, lines=[("SERVICE", 30, 0.52, SANS)]),
    "parts": dict(cols=3, rows=1, bg=(176, 24, 26), fg=WHITE, lines=[("PARTS", 32, 0.52, SANS)]),
    # the corner garages
    "repair": dict(cols=4, rows=1, bg=WHITE, fg=(176, 24, 26), lines=[("AUTO REPAIR", 30, 0.36, SANS),
                                                                    ("BRAKES · TUNE-UPS · MUFFLERS", 12, 0.8, SANS)]),
    "quikfix": dict(cols=4, rows=1, bg=(250, 120, 20), fg=WHITE, lines=[("QUIK-FIX AUTO", 28, 0.36, SANS),
                                                                       ("LUBE · OIL · FILTER", 14, 0.8, SANS)]),
    # the truck depot off I-88, the scrapyard by Foundry Row
    "diesel": dict(cols=6, rows=1, bg=(30, 92, 56), fg=WHITE, lines=[("I-88 TRUCK & DIESEL", 30, 0.36, SANS),
                                                                    ("24 HR ROAD SERVICE", 16, 0.8, SANS)]),
    "salvage": dict(cols=6, rows=1, bg=(236, 226, 196), fg=(40, 40, 40), lines=[("CALDER SALVAGE", 32, 0.36, SERIF),
                                                                               ("USED AUTO PARTS · WE BUY JUNK CARS", 13, 0.8, SANS)], wood=True),
    "motorpool": dict(cols=4, rows=1, bg=OD, fg=CREAM, lines=[("MOTOR POOL", 28, 0.36, SANS), ("MAINTENANCE BAY", 14, 0.8, SANS)]),
}

# the small signs, one block each: (lines, board, lettering)
SMALL = {
    "office": ([("OFFICE", 16, 0.5)], WHITE, BLACK),
    "keepout": ([("CUSTOMERS", 11, 0.3), ("NOT ALLOWED", 9, 0.55), ("IN SHOP AREA", 9, 0.78)], (250, 214, 40), BLACK),
    "parts": ([("PARTS", 18, 0.5)], (176, 24, 26), WHITE),
    "cashier": ([("CASHIER", 13, 0.5)], NAVY, WHITE),
    "rate": ([("LABOR", 12, 0.25), ("$38.00", 16, 0.55), ("PER HOUR", 9, 0.82)], WHITE, (176, 24, 26)),
    "noflame": ([("NO", 14, 0.3), ("OPEN FLAME", 10, 0.68)], (176, 24, 26), WHITE),
    "wasteoil": ([("WASTE", 12, 0.32), ("OIL", 16, 0.7)], (250, 214, 40), BLACK),
    "scale": ([("ALL TRUCKS", 10, 0.3), ("MUST WEIGH", 10, 0.7)], WHITE, BLACK),
    "pit": ([("DANGER", 13, 0.3), ("OPEN PIT", 11, 0.7)], (176, 24, 26), WHITE),
}
SMALL.update({f"bay{i}": ([(str(i), 34, 0.52)], (250, 214, 40), BLACK) for i in range(1, 7)})


# ---------------------------------------------------------------- the signs' art

ART = {}


def sign_tile(key, col, row, seed):
    """One block of a shop sign: its art, dulled and streaked, chipped where it's painted wood."""
    spec = SIGNS[key]
    if key not in ART:
        ART[key] = HW.sign_art(spec)
    art = ART[key]
    r0, c0 = (spec["rows"] - 1 - row) * N, col * N
    t = F.plain(seed, spec["bg"], 0.04, 0.45)
    t.c = art[r0:r0 + N, c0:c0 + N, :3].copy()
    t.vary(0.05)
    if spec.get("wood"):
        chips(t, 0.08, under=(120, 92, 62))
        weather(t, rust=0.0, streak=0.7, grime=0.35)
    else:
        weather(t, rust=0.2, streak=0.5, grime=0.25, dents=1)
    return t


def small_sign(key, seed):
    lines, bg, fg = SMALL[key]
    t = F.plain(seed, bg, 0.03, 0.4)
    edges(t, 1, 1, N - 1, N - 1, w=2, light=0.8, dark=0.6)
    for text, size, y in lines:
        m = F.text_mask(text, SANS, size, N - 6, int(size * 1.4))
        F.paste(t, m, 3, int(y * N - size * 0.7), fg)
    weather(t, rust=0.2, streak=0.4, grime=0.3)
    return t


# ---------------------------------------------------------------- materials

def paint(seed, rgb, rust=0.25, grime=0.3, gloss=0.45):
    t = F.plain(seed, rgb, 0.04, gloss)
    weather(t, rust=rust, streak=0.4, grime=grime, dents=1)
    return t


def drawers(seed, rgb):
    """The roll cabinet's front: rows of drawers of different depths, each with its long chrome
    pull, the paint chipped round the pulls, grease thumbed onto the top ones."""
    t = F.plain(seed, rgb, 0.03, 0.55)
    y = 2
    for h in (5, 5, 6, 6, 8, 10, 12, 10):
        if y + h > N - 2:
            break
        F.sunk(t, 2, y, N - 2, y + h - 1)
        F.bar(t, 6, y + 1, N - 6, y + 2, col=(196, 198, 200))
        y += h
    edges(t, 0, 0, N, N, w=1)
    chips(t, 0.03, under=(150, 150, 150))
    t.grime(0.25, (40, 36, 30))
    return t


def pegboard(seed, kind):
    """The tool wall: brown hardboard drilled in a grid, the tools on their hooks, the outlines
    painted behind them, and the gaps where tools were taken and never put back."""
    t = F.plain(seed, (150, 112, 70), 0.05, 0.2)
    for yy in range(2, N, 4):
        for xx in range(2, N, 4):
            area(t, xx, yy, xx + 1, yy + 1, mul=0.45)
    rng = np.random.default_rng(seed)
    tools = [(4, 6, 2, 22), (10, 6, 2, 20), (16, 6, 2, 18), (22, 6, 2, 16), (28, 6, 2, 14)] if kind == "a" else \
        [(6, 8, 10, 4), (6, 16, 12, 4), (6, 24, 14, 4), (30, 8, 3, 16), (36, 8, 3, 16)]
    tools += [(44, 6, 12, 10), (44, 22, 3, 18), (50, 22, 3, 14), (8, 40, 20, 6), (34, 40, 6, 18), (44, 44, 14, 6)]
    for x, y, w, h in tools:
        area(t, x - 1, y - 1, x + w + 1, y + h + 1, col=(232, 232, 228) if rng.random() < 0.3 else (30, 30, 30))
        if rng.random() < 0.8:
            col = (176, 178, 180) if rng.random() < 0.7 else (178, 30, 28)
            area(t, x, y, x + w, y + h, col=col, dh=0.3, smooth=0.6)
            edges(t, x, y, x + w, y + h, w=1)
    t.grime(0.3, (50, 40, 30))
    return t


def ribbed(seed, rgb, rust=0.2):
    """The metal building's wall panel: ribs every quarter, the screw lines, rust at the foot."""
    t = F.plain(seed, rgb, 0.03, 0.35)
    for x in range(0, N, N // 4):
        area(t, x, 0, x + 3, N, mul=0.82, dh=0.25)
        area(t, x + 3, 0, x + 4, N, mul=1.08)
    for y in (6, N - 6):
        for x in range(4, N, 8):
            area(t, x, y, x + 1, y + 1, mul=0.6)
    weather(t, rust=rust, streak=0.6, grime=0.3)
    return t


def shop_floor(seed, oil=0.0, epoxy=False):
    """The shop's sealed concrete: saw cuts, tyre marks, oil where cars stood and drained."""
    t = F.plain(seed, (168, 170, 168) if epoxy else (136, 134, 128), 0.05, 0.55 if epoxy else 0.25)
    t.vary(0.05)
    area(t, 0, 0, N, 1, mul=0.7)
    area(t, 0, 0, 1, N, mul=0.7)
    rng = np.random.default_rng(seed)
    for _ in range(int(oil * 4)):
        F.stain(t, rng.uniform(8, N - 8), rng.uniform(8, N - 8), rng.uniform(5, 14), col=(36, 32, 28), amount=0.6)
    t.grime(0.25 if not epoxy else 0.12, (60, 56, 50))
    t.speckle(0.01, (90, 86, 80), 0.4)
    return t


def drain(seed):
    t = shop_floor(seed, 0.5)
    area(t, 22, 0, 42, N, col=(70, 70, 70), dh=-0.2)
    for y in range(1, N, 3):
        area(t, 23, y, 41, y + 1, col=(26, 26, 26), dh=-0.4)
    return t


def stripe(seed):
    t = shop_floor(seed, 0.2)
    area(t, 0, 24, N, 40, col=(220, 182, 30))
    chips(t, 0.06, under=(130, 128, 122))
    t.grime(0.3, (60, 56, 50))
    return t


def tyre_side(seed):
    """A tyre's sidewall: the rim's hole, the lettering ring, scuffed."""
    t = F.plain(seed, (34, 34, 34), 0.04, 0.25)
    disc(t, N / 2, N / 2, N * 0.46, mul=1.15)
    disc(t, N / 2, N / 2, N * 0.40, mul=0.85)
    disc(t, N / 2, N / 2, N * 0.24, col=(18, 18, 18), dh=-0.6)
    t.speckle(0.02, (80, 80, 76), 0.4)
    return t


def tyre_rack(seed, full=True):
    """The rack's front: tyres stood on their treads side by side on two beams."""
    t = F.plain(seed, (40, 40, 40), 0.03, 0.2)
    rng = np.random.default_rng(seed)
    for y0 in (2, 34):
        area(t, 0, y0 + 28, N, y0 + 30, col=(220, 120, 30))
        x = 1
        while x < N - 6:
            w = int(rng.integers(7, 10))
            if full or rng.random() < 0.45:
                area(t, x, y0, x + w - 1, y0 + 28, col=(28, 28, 28), dh=0.3)
                for yy in range(y0 + 2, y0 + 27, 3):
                    area(t, x + 1, yy, x + w - 2, yy + 1, mul=1.5)
                edges(t, x, y0, x + w - 1, y0 + 28, w=1, light=1.3)
            x += w
    area(t, 0, 0, 2, N, col=(220, 120, 30))
    area(t, N - 2, 0, N, N, col=(220, 120, 30))
    return t


def shelf_front(seed, kind):
    """Steel shelving front: grey uprights, two shelves of stock (boxes, filters, rotors, batteries)."""
    t = F.plain(seed, (24, 24, 24), 0.03, 0.2)
    rng = np.random.default_rng(seed)
    cols = {"boxes": [(220, 140, 30), (40, 90, 170), (200, 40, 36), (236, 236, 228), (30, 120, 70)],
            "filters": [(30, 70, 160), (220, 120, 20), (240, 240, 236), (40, 40, 40)],
            "brakes": [(110, 112, 114), (160, 70, 30), (40, 40, 40)],
            "batteries": [(30, 30, 30), (200, 40, 36), (40, 90, 170)],
            "oil": [(240, 200, 30), (40, 90, 170), (200, 40, 36), (30, 120, 70)]}[kind]
    for y0 in (3, 35):
        area(t, 0, y0 + 26, N, y0 + 29, col=(150, 152, 154))
        x = 3
        while x < N - 4:
            w = int(rng.integers(4, 11)) if kind == "boxes" else (5 if kind == "filters" else 9 if kind == "batteries" else 7)
            h = int(rng.integers(8, 24)) if kind in ("boxes", "oil") else (12 if kind == "filters" else 18 if kind == "batteries" else 22)
            if rng.random() < 0.85:
                c = cols[int(rng.integers(len(cols)))]
                area(t, x, y0 + 26 - h, x + w - 1, y0 + 26, col=c, dh=0.3)
                edges(t, x, y0 + 26 - h, x + w - 1, y0 + 26, w=1)
                if kind == "brakes":
                    disc(t, x + w / 2, y0 + 26 - h / 2, w * 0.45, col=(120, 122, 124))
                    disc(t, x + w / 2, y0 + 26 - h / 2, w * 0.18, col=(70, 70, 70))
                if kind == "batteries":
                    area(t, x + 1, y0 + 26 - h, x + 3, y0 + 26 - h + 2, col=(200, 40, 36))
            x += w + 1
    area(t, 0, 0, 3, N, col=(150, 152, 154))
    area(t, N - 3, 0, N, N, col=(150, 152, 154))
    t.grime(0.2, (40, 36, 30))
    return t


def belts(seed):
    """The belt and hose wall: rubber loops on hooks along a rail, boxed in grey."""
    t = F.plain(seed, (190, 188, 180), 0.04, 0.2)
    rng = np.random.default_rng(seed)
    for y0 in (6, 36):
        area(t, 0, y0, N, y0 + 2, col=(120, 120, 120))
        for x in range(3, N - 3, 6):
            h = int(rng.integers(12, 24))
            area(t, x, y0 + 2, x + 2, y0 + h, col=(30, 30, 30))
            area(t, x + 3, y0 + 2, x + 4, y0 + h - 2, col=(30, 30, 30))
    t.grime(0.3, (50, 46, 40))
    return t


def pipes(seed):
    t = F.plain(seed, (60, 60, 60), 0.04, 0.3)
    rng = np.random.default_rng(seed)
    for y in range(2, N - 2, 5):
        c = (150, 152, 150) if rng.random() < 0.6 else (130, 80, 50)
        area(t, 0, y, N, y + 4, col=c, dh=0.3)
        area(t, 0, y, N, y + 1, mul=1.25)
    return t


def engine_block(seed):
    """Cast iron, oily, the bores and bolt bosses, rust bloom where it's sat."""
    t = F.plain(seed, (70, 72, 74), 0.08, 0.35)
    t.vary(0.1)
    for x in (10, 26, 42):
        disc(t, x + 6, 20, 6, col=(110, 110, 110))
        disc(t, x + 6, 20, 5, col=(40, 40, 40), dh=-0.4)
    for x in range(6, N, 10):
        disc(t, x, 46, 2, mul=1.4, dh=0.2)
    t.speckle(0.04, (120, 74, 40), 0.5)
    t.grime(0.5, (30, 26, 22))
    return t


def keyboard(seed):
    """The key board: white pegboard, rows of hooks, keys on numbered tags, some hooks bare."""
    t = F.plain(seed, (226, 222, 210), 0.03, 0.2)
    edges(t, 0, 0, N, N, w=2, light=0.7, dark=0.5)
    rng = np.random.default_rng(seed)
    for y in (8, 24, 40, 54):
        for x in range(6, N - 4, 7):
            area(t, x, y, x + 1, y + 1, col=(80, 80, 80))
            if rng.random() < 0.6:
                area(t, x - 1, y + 2, x + 2, y + 7, col=(196, 198, 200))
                area(t, x - 2, y + 7, x + 3, y + 11, col=[(220, 60, 50), (240, 210, 60), (90, 160, 220), (236, 236, 236)][int(rng.integers(4))])
    t.grime(0.2, (60, 50, 40))
    return t


def tin(seed, rust=0.6):
    """Corrugated tin: the waves, the nail lines, rust running from them, a patch of old paint."""
    t = F.plain(seed, (132, 128, 120), 0.08, 0.3)
    for x in range(N):
        t.c[:, x] *= 0.86 + 0.14 * np.cos(x / N * 8 * 2 * np.pi)
    weather(t, rust=rust, streak=0.7, grime=0.4, dents=2)
    return t


def flat(seed, rgb):
    """A crushed car's side: crumpled paint, folds of bare metal, a strip of window, a wheel arch."""
    t = F.plain(seed, rgb, 0.12, 0.35)
    rng = np.random.default_rng(seed)
    for _ in range(9):
        y = int(rng.integers(0, N))
        area(t, 0, y, N, y + int(rng.integers(1, 3)), mul=float(rng.uniform(0.5, 0.8)), dh=-0.3)
    area(t, 0, 18, N, 24, col=(60, 70, 74))
    area(t, 0, 20, N, 22, col=(150, 170, 176))
    disc(t, rng.uniform(10, 54), 56, 9, col=(26, 26, 26))
    weather(t, rust=0.9, streak=0.5, grime=0.5, dents=4)
    return t


def pit_curb(seed):
    t = HW.stripes(seed, (236, 190, 30), (24, 24, 24), 8)
    return t


def pit_wall(seed):
    t = F.plain(seed, (214, 212, 204), 0.04, 0.5)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=0.7)
    for x in range(0, N, 32):
        area(t, x, 0, x + 1, N, mul=0.7)
    t.grime(0.5, (50, 44, 36))
    t.streaks(0.6, 0.7)
    return t


def lamp(seed, lit):
    t = F.plain(seed, (250, 246, 220) if lit else (170, 170, 160), 0.02, 0.8)
    if lit:
        t.f0[:] = 1.0
    return t


def crt(seed):
    t = F.plain(seed, (30, 40, 34), 0.03, 0.9)
    for y in range(8, N - 8, 6):
        area(t, 8, y, N - 8, y + 1, col=(60, 120, 70))
    edges(t, 4, 4, N - 4, N - 4, w=3, light=0.6, dark=0.4)
    return t


def certs(seed):
    t = F.plain(seed, (190, 170, 140), 0.04, 0.2)
    for x, y in ((4, 6), (34, 6), (4, 36), (34, 36)):
        area(t, x, y, x + 26, y + 22, col=(80, 50, 30))
        area(t, x + 2, y + 2, x + 24, y + 20, col=(236, 232, 220))
        area(t, x + 6, y + 6, x + 20, y + 7, col=(60, 60, 60))
        disc(t, x + 18, y + 15, 3, col=(196, 160, 40))
    return t


def vinyl(seed):
    """Seat vinyl: dark, creased, split along a seam with the foam showing."""
    t = F.plain(seed, (52, 46, 42), 0.05, 0.5)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=0.6)
    area(t, 20, 30, 34, 33, col=(196, 176, 110))
    t.grime(0.3, (30, 26, 22))
    return t


def textures():
    out = {
        "as_red": lambda: paint(51000, (176, 30, 28), gloss=0.6), "as_blue": lambda: paint(51001, (28, 70, 150), gloss=0.6),
        "as_black": lambda: paint(51002, (30, 30, 32), gloss=0.6), "as_od": lambda: paint(51003, OD, gloss=0.2),
        "as_drawers_red": lambda: drawers(51004, (176, 30, 28)), "as_drawers_blue": lambda: drawers(51005, (28, 70, 150)),
        "as_drawers_black": lambda: drawers(51006, (30, 30, 32)), "as_drawers_od": lambda: drawers(51007, OD),
        "as_steel": lambda: paint(51008, (128, 130, 132), rust=0.3), "as_galv": lambda: paint(51009, (176, 178, 176), rust=0.1),
        "as_rubber": lambda: F.plain(51010, (28, 28, 28), 0.04, 0.2), "as_chrome": lambda: F.chrome(51011),
        "as_yellow": lambda: paint(51012, (236, 190, 30)), "as_orange": lambda: paint(51013, ORANGE, gloss=0.5),
        "as_green": lambda: paint(51014, (40, 110, 60)), "as_grey": lambda: paint(51015, (96, 98, 100)),
        "as_peg_a": lambda: pegboard(51016, "a"), "as_peg_b": lambda: pegboard(51017, "b"),
        "as_metal": lambda: ribbed(51018, (226, 226, 220)), "as_metal_tan": lambda: ribbed(51019, (196, 178, 140)),
        "as_metal_rust": lambda: ribbed(51020, (180, 170, 150), rust=1.0),
        "as_floor": lambda: shop_floor(51021, 0.3), "as_floor_oil": lambda: shop_floor(51022, 1.2),
        "as_floor_epoxy": lambda: shop_floor(51023, 0.1, True), "as_floor_drain": lambda: drain(51024), "as_floor_stripe": lambda: stripe(51025),
        "as_tyre": lambda: tyre_side(51026), "as_tread": lambda: F.plain(51027, (30, 30, 30), 0.06, 0.2),
        "as_rack_full": lambda: tyre_rack(51028, True), "as_rack_low": lambda: tyre_rack(51029, False),
        "as_shelf_boxes": lambda: shelf_front(51030, "boxes"), "as_shelf_filters": lambda: shelf_front(51031, "filters"),
        "as_shelf_brakes": lambda: shelf_front(51032, "brakes"), "as_shelf_batteries": lambda: shelf_front(51033, "batteries"),
        "as_shelf_oil": lambda: shelf_front(51034, "oil"), "as_belts": lambda: belts(51035), "as_pipes": lambda: pipes(51036),
        "as_engine": lambda: engine_block(51037), "as_keys": lambda: keyboard(51038),
        "as_tin": lambda: tin(51039), "as_tin_paint": lambda: tin(51040, 0.3),
        "as_pit_curb": lambda: pit_curb(51041), "as_pit_wall": lambda: pit_wall(51042),
        "as_lamp": lambda: lamp(51043, False), "as_lamp_lit": lambda: lamp(51044, True), "as_crt": lambda: crt(51045),
        "as_certs": lambda: certs(51046), "as_laminate": lambda: F.worktop("white", 51047),
        "as_vinyl": lambda: vinyl(51048),
        "as_crusher": lambda: paint(51049, (220, 170, 30), rust=0.8, grime=0.6), "as_rust": lambda: P.rust(),
        "as_scrap": lambda: flat(51050, (110, 90, 70)), "as_scale": lambda: HW.conc(51051, (130, 128, 122)),
        "as_concrete": lambda: HW.conc(51052),
    }
    for i, (name, rgb) in enumerate((("red", (150, 34, 30)), ("blue", (40, 70, 120)), ("white", (200, 200, 192)),
                                     ("green", (50, 90, 60)), ("rust", (120, 76, 44)), ("tan", (170, 150, 110)))):
        out[f"as_flat_{name}"] = lambda rgb=rgb, i=i: flat(51060 + i, rgb)
    i = 0
    for key, spec in SIGNS.items():
        for c in range(spec["cols"]):
            for r in range(spec["rows"]):
                out[f"as_s_{key}_{c}{r}"] = lambda key=key, c=c, r=r, i=i: sign_tile(key, c, r, 51100 + i)
                i += 1
    for j, key in enumerate(SMALL):
        out[f"as_sign_{key}"] = lambda key=key, j=j: small_sign(key, 51300 + j)
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- models

def faces(front, rest):
    return {"north": front, "south": rest, "east": rest, "west": rest, "up": rest, "down": rest}


def casters(y=0, z0=3, z1=13, x0=2, x1=14):
    return [[x0, y, z0, x0 + 2, y + 2, z0 + 2, RUBBER], [x1 - 2, y, z0, x1, y + 2, z0 + 2, RUBBER],
            [x0, y, z1 - 2, x0 + 2, y + 2, z1, RUBBER], [x1 - 2, y, z1 - 2, x1, y + 2, z1, RUBBER]]


def chest(paint, hi):
    body, front = f"as_{paint}", f"as_drawers_{paint}"
    if not hi:
        # the roll cabinet: on casters, its drawers, the side handle, the worktop
        return casters(0, 3, 15, 1, 15) + [[1, 2, 3, 15, 15, 15, faces(front, body)], [0.5, 15, 2.5, 15.5, 16, 15.5, RUBBER],
                                           [15, 8, 6, 16, 9, 12, "as_chrome"]]
    # the top chest on it, its lid propped a little open
    return [[1.5, 0, 4, 14.5, 9, 15, faces(front, body)], [1.5, 9, 4, 14.5, 10.5, 15, body],
            [1.5, 10.5, 5, 14.5, 11.5, 15.5, body, None, {"axis": "x", "angle": 22.5, "origin": [8, 10.5, 15]}],
            [5, 9.5, 3.5, 11, 10, 4, "as_chrome"]]


def cart():
    return casters(0, 4, 13, 2, 14) + [[2, 3, 4, 14, 4, 13, "as_red"], [2, 10, 4, 14, 11, 13, "as_red"],
                                       [2, 3, 4, 2.6, 12, 13, "as_red"], [13.4, 3, 4, 14, 12, 13, "as_red"],
                                       [2, 11, 4, 14, 12, 4.6, "as_red"], [2, 11, 12.4, 14, 12, 13, "as_red"],
                                       [3, 11, 6, 9, 12.5, 8, "as_chrome"], [9, 11, 9, 12, 13, 11, "as_black"],
                                       [3, 4, 6, 8, 6, 11, "as_blue"], [14, 9, 6, 15.5, 10, 11, "as_chrome"]]


def bench(top=None):
    b = [[0, 13, 2, 16, 14.5, 15, "as_laminate"], [0.5, 0, 2.5, 2, 13, 4, STEEL], [14, 0, 2.5, 15.5, 13, 4, STEEL],
         [0.5, 0, 13, 2, 13, 14.5, STEEL], [14, 0, 13, 15.5, 13, 14.5, STEEL], [0.5, 3, 3, 15.5, 4, 14.5, STEEL],
         [2, 9.5, 2.5, 9, 13, 14, faces("as_drawers_blue", "as_blue")], [10, 4, 5, 14, 7, 12, "as_grey"]]
    if top == "vise":
        b += [[3, 14.5, 2, 9, 16, 6, "as_blue"], [4, 16, 0, 8, 19, 6, "as_blue"], [5.5, 17, -1, 6.5, 18, 2, "as_chrome"]]
    elif top == "grinder":
        b += [[5, 14.5, 6, 11, 16, 11, "as_grey"], [6, 16, 6.5, 10, 20, 10.5, "as_green"], [3.5, 16.5, 7, 6, 19.5, 10, "as_grey"],
              [10, 16.5, 7, 12.5, 19.5, 10, "as_grey"]]
    else:
        b += [[4, 14.5, 4, 10, 15.5, 10, "as_red"], [11, 14.5, 9, 13, 18, 11, "as_engine"]]
    return b


def drum(label):
    return [[2, 0, 2, 14, 15, 14, faces(label, label)], [1.6, 4, 1.6, 14.4, 5, 14.4, label], [1.6, 10, 1.6, 14.4, 11, 14.4, label],
            [7, 15, 7, 9, 21, 9, STEEL], [7.5, 19, 4, 8.5, 20, 9, STEEL], [5, 15, 5, 6.5, 16, 6.5, STEEL]]


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate="facing", sound="METAL", **extra):
        if rotate == "axis":
            boxes, shape = P.along_z(boxes), P.along_z(shape)
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "shape": shape, "render": "cutout", **extra}
        if rotate:
            spec["rotate"] = rotate
        out[name] = spec

    full = [[0, 0, 0, 16, 16, 16]]
    # ---- the tools
    for paint in ("red", "blue", "black", "od"):
        add(f"as_chest_{paint}_lo", chest(paint, False), "Tool Cabinet", [[0.5, 0, 2.5, 15.5, 16, 15.5]],
            loot="auto_military" if paint == "od" else "auto_tools")
        add(f"as_chest_{paint}_hi", chest(paint, True), "Tool Chest", [[1.5, 0, 3.5, 14.5, 12, 15.5]],
            loot="auto_military" if paint == "od" else "auto_tools")
    add("as_cart", cart(), "Tool Cart", [[2, 0, 4, 15.5, 13, 13]], loot="auto_tools")
    add("as_bench", bench(), "Workbench", [[0, 0, 2, 16, 15, 15]], loot="auto_tools")
    add("as_bench_vise", bench("vise"), "Workbench", [[0, 0, 0, 16, 19, 15]], loot="auto_tools")
    add("as_bench_grinder", bench("grinder"), "Workbench", [[0, 0, 2, 16, 20, 15]], loot="auto_tools")
    for k in ("a", "b"):
        add(f"as_pegwall_{k}", [[0, 0, 15, 16, 16, 16, faces(f"as_peg_{k}", "as_peg_a")]], "Tool Wall", [[0, 0, 15, 16, 16, 16]],
            sound="WOOD", **({"loot": "auto_tools"} if k == "a" else {"decor": True}))
    add("as_creeper", [[3, 1, 1, 13, 2, 15, "as_red"], [4, 2, 12, 12, 3, 15, "as_vinyl"]] + casters(0, 2, 14, 3, 13),
        "Creeper", [[3, 0, 1, 13, 3, 15]], decor=True)
    add("as_stands", [[1, 0, 4, 6, 1, 9, "as_red"], [3, 1, 6, 4, 9, 7, "as_red"], [2, 9, 5.5, 5, 10, 7.5, "as_red"],
                      [10, 0, 7, 15, 1, 12, "as_red"], [12, 1, 9, 13, 9, 10, "as_red"], [11, 9, 8.5, 14, 10, 10.5, "as_red"]],
        "Jack Stands", [[1, 0, 4, 15, 10, 12]], decor=True)
    add("as_drain", casters(0, 4, 12, 4, 12) + [[4.5, 2, 4.5, 11.5, 14, 11.5, faces("as_orange", "as_orange")],
                                                 [7, 14, 7, 9, 18, 9, "as_orange"], [3, 18, 3, 13, 20, 13, "as_black"],
                                                 [11.5, 6, 7, 13.5, 16, 8, "as_chrome"]], "Oil Drain", [[3, 0, 3, 13, 20, 13]])
    add("as_press_lo", [[1, 0, 6, 15, 1.5, 10, STEEL], [2, 0, 7, 4, 16, 9, "as_red"], [12, 0, 7, 14, 16, 9, "as_red"],
                        [2, 7, 6.5, 14, 9, 9.5, "as_red"], [6, 9, 7, 10, 10, 9, STEEL]], "Shop Press", [[1, 0, 6, 15, 16, 10]])
    add("as_press_hi", [[2, 0, 7, 4, 10, 9, "as_red"], [12, 0, 7, 14, 10, 9, "as_red"], [1.5, 10, 6.5, 14.5, 13, 9.5, "as_red"],
                        [7, 2, 7, 9, 10, 9, "as_chrome"], [6, 3, 6, 10, 8, 10, "as_black"], [14.5, 4, 7.5, 16, 5, 8.5, "as_chrome"]],
        "Shop Press", [[1.5, 0, 6.5, 16, 13, 9.5]])
    add("as_welder", casters(0, 3, 13, 3, 13) + [[3, 2, 3, 13, 11, 13, faces("as_blue", "as_blue")], [4, 11, 5, 12, 12, 12, "as_black"],
                                                  [6, 2, 13, 10, 20, 15.5, "as_green"], [7, 20, 13.5, 9, 22, 15, "as_chrome"],
                                                  [5, 5, 2.5, 7, 7, 3, "as_chrome"], [9, 5, 2.5, 11, 7, 3, "as_black"]],
        "Welder", [[3, 0, 3, 13, 22, 15.5]])
    add("as_torch", casters(0, 4, 13, 3, 13) + [[3, 2, 4, 13, 3, 13, "as_red"], [4, 3, 5, 8, 24, 9, "as_green"],
                                                 [9, 3, 5, 12, 20, 9, "as_red"], [3, 3, 11, 13, 14, 12, "as_red"],
                                                 [5, 24, 6, 7, 25.5, 8, "as_chrome"], [9.5, 20, 6, 11.5, 21.5, 8, "as_chrome"]],
        "Cutting Torch", [[3, 0, 4, 13, 25.5, 13]])
    add("as_charger", casters(0, 4, 12, 3, 13) + [[3.5, 2, 4, 12.5, 12, 12, faces("as_crt", "as_red")], [3, 12, 7, 13, 13, 9, "as_black"],
                                                   [5, 4, 3, 6, 9, 4, "as_red"], [10, 4, 3, 11, 9, 4, "as_black"]],
        "Battery Charger", [[3, 0, 3, 13, 13, 12]], loot="auto_electric")
    add("as_analyzer_lo", casters(0, 3, 13, 2, 14) + [[2, 2, 3, 14, 16, 13, faces("as_drawers_black", "as_grey")]],
        "Engine Analyzer", [[2, 0, 3, 14, 16, 13]])
    add("as_analyzer_hi", [[2, 0, 3, 14, 11, 13, faces("as_crt", "as_grey")], [3, 11, 4, 13, 12, 12, "as_grey"],
                           [2, 2, 1.5, 14, 4, 3, "as_black"], [13, 4, 6, 16, 6, 9, "as_red"]], "Engine Analyzer", [[2, 0, 1.5, 16, 12, 13]])
    # ---- the fluids
    for k, col in (("oil", "as_black"), ("atf", "as_red"), ("coolant", "as_green")):
        add(f"as_drum_{k}", drum(col), "Drum", [[1.6, 0, 1.6, 14.4, 21, 14.4]], rotate=None, loot="auto_fluids")
    add("as_tote", [[1, 0, 1, 15, 2, 15, "as_black"], [1.5, 2, 1.5, 14.5, 15, 14.5, faces("as_sign_wasteoil", "as_black")],
                    [1, 2, 1, 15, 15.5, 1.5, GALV], [1, 2, 14.5, 15, 15.5, 15, GALV], [1, 2, 1, 1.5, 15.5, 15, GALV],
                    [14.5, 2, 1, 15, 15.5, 15, GALV], [6, 15, 6, 10, 16.5, 10, "as_black"]], "Waste Oil Tote", [[1, 0, 1, 15, 16.5, 15]])
    add("as_reels", [[0, 2, 12, 16, 3, 16, STEEL]] + [[1 + i * 4, 3, 10, 4 + i * 4, 10, 15, c] for i, c in
                                                      enumerate(("as_blue", "as_black", "as_red", "as_yellow"))]
        + [[2 + i * 4, -8, 9, 3 + i * 4, 4, 10, RUBBER] for i in range(4)], "Hose Reels", [[0, 2, 10, 16, 10, 16]], decor=True)
    add("as_oilrack", [[0, 0, 2, 16, 16, 15, faces("as_shelf_oil", STEEL)]], "Oil Shelf", [[0, 0, 2, 16, 16, 15]], loot="auto_fluids")
    # ---- the tyres
    add("as_tyre_rack_lo", [[0, 0, 1, 16, 16, 15, faces("as_rack_full", "as_tread")]], "Tire Rack", [[0, 0, 1, 16, 16, 15]],
        loot="auto_wheels_standard")
    add("as_tyre_rack_hi", [[0, 0, 1, 16, 16, 15, faces("as_rack_full", "as_tread")]], "Tire Rack", [[0, 0, 1, 16, 16, 15]],
        loot="auto_wheels_standard")
    add("as_tyre_rack_low", [[0, 0, 1, 16, 16, 15, faces("as_rack_low", "as_tread")]], "Tire Rack", [[0, 0, 1, 16, 16, 15]],
        loot="auto_wheels_standard")
    stack = []
    for i in range(4):
        stack += [[1.5, i * 4, 1.5, 14.5, i * 4 + 3.6, 14.5, {"up": "as_tyre", "down": "as_tyre", "north": "as_tread", "south": "as_tread",
                                                              "east": "as_tread", "west": "as_tread"}]]
    add("as_tyre_stack", stack, "Tires", [[1.5, 0, 1.5, 14.5, 16, 14.5]], rotate=None, sound="WOOL")
    add("as_wheels", [[2, i * 3.5, 2, 14, i * 3.5 + 3, 14, {"up": "as_tyre", "down": "as_chrome", "north": "as_chrome",
                                                            "south": "as_chrome", "east": "as_chrome", "west": "as_chrome"}]
                      for i in range(3)], "Wheels", [[2, 0, 2, 14, 10.5, 14]], rotate=None, loot="auto_wheels_sport")
    add("as_tyre_cage", [[0, 0, 0, 16, 1, 16, STEEL]] + [[1 + (i % 3) * 5, 1 + (i // 3) * 4, 1 + (i % 2) * 2, 5 + (i % 3) * 5,
                                                          4.6 + (i // 3) * 4, 14 + (i % 2) * 1,
                                                          {"up": "as_tyre", "down": "as_tyre", "north": "as_tyre", "south": "as_tyre",
                                                           "east": "as_tread", "west": "as_tread"}] for i in range(9)]
        + [[0, 0, 0, 16, 16, 0.6, "as_tin_paint"], [0, 0, 15.4, 16, 16, 16, "as_tin_paint"], [0, 0, 0, 0.6, 16, 16, "as_tin_paint"],
           [15.4, 0, 0, 16, 16, 16, "as_tin_paint"]], "Tire Cage", full, rotate=None)
    add("as_tyre_sale", [[7, 0, 7, 9, 10, 9, STEEL], [2, 6, 6, 14, 15, 7, faces("as_sign_parts", "as_tyre")],
                         [4, 0, 7, 12, 1, 9, STEEL]], "Tire Stand", [[2, 0, 6, 14, 15, 9]])
    # ---- the parts
    for k in ("filters", "brakes", "boxes", "batteries"):
        for lvl in ("lo", "hi"):
            loot = {"filters": "auto_suspension_standard", "brakes": "auto_brakes_standard", "boxes": "auto_exhaust_standard",
                    "batteries": "auto_electric"}[k]
            add(f"as_shelf_{k}_{lvl}", [[0, 0, 1, 16, 16, 15, faces(f"as_shelf_{k}", STEEL)]], "Parts Shelf", [[0, 0, 1, 16, 16, 15]],
                loot=loot)
    add("as_belts", [[0, 0, 13, 16, 16, 16, faces("as_belts", STEEL)]], "Belts and Hoses", [[0, 0, 13, 16, 16, 16]],
        loot="auto_suspension_standard")
    add("as_pipes", [[0, 0, 3, 16, 16, 15, faces("as_pipes", STEEL)], [0, 0, 2, 1, 16, 16, STEEL], [15, 0, 2, 16, 16, 16, STEEL]],
        "Exhaust Rack", [[0, 0, 2, 16, 16, 16]], loot="auto_exhaust_standard")
    add("as_counter_parts", [[0, 0, 2, 16, 14, 16, faces("as_drawers_black", "as_laminate")], [-0.5, 14, 0, 16.5, 15.5, 16, "as_laminate"],
                             [3, 15.5, 9, 10, 22, 14, faces("as_crt", "as_grey")], [11, 15.5, 4, 15, 18, 12, "as_shelf_boxes"]],
        "Parts Counter", [[0, 0, 0, 16, 16, 16]], loot="auto_books")
    add("as_counter_end", [[0, 0, 2, 16, 14, 16, faces("as_drawers_black", "as_laminate")], [-0.5, 14, 0, 16.5, 15.5, 16, "as_laminate"],
                           [2, 15.5, 4, 8, 16.5, 10, "as_certs"]], "Parts Counter", [[0, 0, 0, 16, 16, 16]], loot="auto_electric")
    add("as_cores", [[1, 0, 1, 15, 10, 15, faces("as_grey", "as_grey")], [2, 8, 2, 14, 11, 14, "as_engine"], [3, 10, 4, 7, 13, 8, "as_rust"],
                     [8, 10, 6, 13, 12, 12, "as_shelf_brakes"]], "Core Returns", [[1, 0, 1, 15, 13, 15]], loot="auto_scrap")
    add("as_engine_stand", [[3, 0, 2, 13, 1, 4, "as_red"], [7, 0, 2, 9, 1, 15, "as_red"], [7, 1, 12, 9, 8, 14, "as_red"],
                            [6, 6, 9, 10, 9, 12, "as_red"], [2, 5, 1, 14, 15, 9, faces("as_engine", "as_engine")],
                            [3, 15, 2, 13, 16.5, 8, "as_engine"]], "Engine Stand", [[2, 0, 1, 14, 16.5, 15]])
    add("as_engine", [[1, 0, 1, 15, 1.5, 15, "as_laminate"], [3, 1.5, 3, 13, 11, 13, faces("as_engine", "as_engine")],
                      [4, 11, 4, 12, 13, 12, "as_engine"], [13, 3, 5, 15, 9, 11, "as_engine"]], "Engine", [[1, 0, 1, 15, 13, 15]])
    add("as_trans", [[2, 0, 4, 14, 6, 12, "as_engine"], [4, 0, 3, 12, 8, 13, "as_engine"], [13, 2, 6, 16, 4, 10, "as_grey"]],
        "Transmission", [[2, 0, 3, 16, 8, 13]], decor=True)
    add("as_hood", [[0, 0, 13, 16, 16, 15.5, "as_flat_blue", None, {"axis": "x", "angle": -22.5, "origin": [8, 0, 15]}]],
        "Car Hood", [[0, 0, 10, 16, 16, 16]], decor=True)
    add("as_cardoor", [[1, 0, 13, 15, 14, 15, "as_flat_red", None, {"axis": "x", "angle": -22.5, "origin": [8, 0, 15]}],
                       [2, 9, 13.5, 14, 13, 14, "as_crt"]], "Car Door", [[1, 0, 10, 15, 14, 16]], decor=True)
    add("as_bumper", [[0, 0, 13, 16, 4, 15, "as_chrome", None, {"axis": "x", "angle": -22.5, "origin": [8, 0, 15]}]],
        "Bumper", [[0, 0, 12, 16, 4, 16]], decor=True)
    add("as_radiator", [[2, 0, 11, 14, 14, 13, faces("as_pipes", "as_black")]], "Radiator", [[2, 0, 11, 14, 14, 13]], decor=True)
    add("as_seat", [[2, 0, 2, 14, 4, 13, "as_vinyl"], [2, 4, 11, 14, 15, 14, "as_vinyl"], [5, 15, 11.5, 11, 18, 13.5, "as_vinyl"]],
        "Car Seat", [[2, 0, 2, 14, 18, 14]], decor=True)
    # ---- the office
    # the key board is a fitting with no table of its own: the first look hangs the customer cars' keys
    # on it (CarKeys.board, from Fittings), never loot
    add("as_keyboard", [[1, 3, 15, 15, 15, 16, faces("as_keys", "as_laminate")]], "Key Board", [[1, 3, 15, 15, 15, 16]], sound="WOOD",
        loot="auto_keyboard")
    add("as_desk", [[0, 13, 1, 16, 14.5, 16, "as_laminate"], [0.5, 0, 2, 6, 13, 15, faces("as_drawers_black", "as_grey")],
                    [14, 0, 2, 15.5, 13, 15, "as_grey"], [7, 14.5, 9, 13, 20, 14, faces("as_crt", "as_grey")],
                    [2, 14.5, 3, 6, 15, 7, "as_certs"]], "Service Desk", [[0, 0, 1, 16, 16, 16]], sound="WOOD", loot="auto_special")
    for side in ("l", "r"):
        arm = [0, 0, 3, 1.5, 9, 14] if side == "l" else [14.5, 0, 3, 16, 9, 14]
        add(f"as_chairs_{side}", [[0, 4, 3, 16, 5.5, 13, "as_vinyl"], [0, 5.5, 12, 16, 15, 14, "as_vinyl"], arm + [STEEL],
                                  [1, 0, 6, 15, 1, 8, STEEL], [7.5, 5, 3, 8.5, 15, 14, STEEL]], "Waiting Chairs", [[0, 0, 3, 16, 15, 14]],
            sound="WOOD")
    add("as_coffee", [[1, 0, 3, 15, 13, 14, faces("as_drawers_black", "as_laminate")], [0.5, 13, 2.5, 15.5, 14, 14.5, "as_laminate"],
                      [2, 14, 7, 7, 21, 12, "as_black"], [3, 14, 4, 6, 18, 7, "as_crt"], [9, 14, 6, 14, 17, 11, "as_certs"]],
        "Coffee Station", [[0.5, 0, 2.5, 15.5, 21, 14.5]], sound="WOOD")
    add("as_certs", [[0, 2, 15.5, 16, 14, 16, faces("as_certs", "as_certs")]], "Certificates", [[0, 2, 15.5, 16, 14, 16]], decor=True, sound="WOOD")
    add("as_clock", [[4, 4, 15, 12, 12, 16, faces("as_lamp", "as_black")]], "Clock", [[4, 4, 15, 12, 12, 16]], decor=True)
    # ---- the building
    add("as_highbay", [[7.5, 10, 7.5, 8.5, 16, 8.5, STEEL], [4, 5, 4, 12, 10, 12, "as_grey"], [5, 4, 5, 11, 5, 11, "as_lamp"]],
        "Shop Light", [[4, 4, 4, 12, 16, 12]], rotate=None)
    add("as_highbay_lit", [[7.5, 10, 7.5, 8.5, 16, 8.5, STEEL], [4, 5, 4, 12, 10, 12, "as_grey"], [5, 4, 5, 11, 5, 11, "as_lamp_lit"]],
        "Shop Light", [[4, 4, 4, 12, 16, 12]], rotate=None, light=14)
    for lit in (False, True):
        add("as_strip" + ("_lit" if lit else ""), [[3, 14, 0, 4, 16, 1, STEEL], [3, 14, 15, 4, 16, 16, STEEL], [2, 11, 0, 10, 14, 16, "as_grey"],
                                                   [3, 10.5, 0.5, 9, 11, 15.5, "as_lamp_lit" if lit else "as_lamp"]],
            "Shop Light", [[2, 10, 0, 10, 16, 16]], rotate="axis", **({"light": 13} if lit else {}))
    add("as_coil", [[0, 2, 3, 16, 12, 13, faces("as_metal", "as_metal")], [0, 0, 4, 16, 2, 12, "as_metal"]], "Door Roll", [[0, 0, 3, 16, 12, 13]],
        rotate="axis")
    add("as_opener", [[4, 9, 4, 12, 14, 12, "as_grey"], [7, 14, 7, 9, 16, 9, STEEL], [7.5, 12, 0, 8.5, 13, 4, STEEL], [5, 8.5, 5, 11, 9, 11, "as_lamp"]],
        "Door Opener", [[4, 8.5, 0, 12, 16, 12]])
    add("as_rail", [[7, 12, 0, 9, 13.5, 16, STEEL]], "Door Rail", [[7, 12, 0, 9, 13.5, 16]], rotate="axis", decor=True)
    add("as_column", [[5, 0, 5, 11, 16, 11, "as_grey"], [5, 0, 7.5, 11, 16, 8.5, "as_grey"]], "Steel Column", [[5, 0, 5, 11, 16, 11]],
        rotate=None)
    add("as_column_base", [[5, 0, 5, 11, 16, 11, "as_grey"], [3, 0, 3, 13, 4, 13, "as_yellow"]], "Steel Column", [[3, 0, 3, 13, 16, 13]],
        rotate=None)
    add("as_slat", [[0, 0, 13, 16, 16, 15, faces("as_metal", "as_metal")]], "Roll-Up Door", [[0, 0, 13, 16, 16, 15]])
    add("as_slat_bar", [[0, 0, 13, 16, 16, 15, faces("as_metal", "as_metal")], [0, 0, 12, 16, 2, 15.5, "as_black"]],
        "Roll-Up Door", [[0, 0, 12, 16, 16, 15.5]])
    # ---- the truck hall
    add("as_pit_curb", [[0, 0, 0, 16, 16, 16, {"up": "as_pit_curb", "north": "as_pit_wall", "south": "as_concrete", "east": "as_concrete",
                                               "west": "as_concrete", "down": "as_concrete"}], [0, 16, 0, 16, 17, 2, "as_yellow"]],
        "Pit Edge", full, sound="STONE")
    add("as_pit_wall", [[0, 0, 0, 16, 16, 16, faces("as_pit_wall", "as_concrete")]], "Pit Wall", full, sound="STONE")
    # the steps down into the pit at its end, the treads rising to the south, yellow nosings
    add("as_pit_steps", [[0, 0, 0, 16, 5.5, 16, "as_concrete"], [0, 5.5, 5.5, 16, 11, 16, "as_concrete"], [0, 11, 11, 16, 16, 16, "as_concrete"],
                         [0, 4.5, 0, 16, 5.6, 1, "as_yellow"], [0, 10, 5.5, 16, 11.1, 6.5, "as_yellow"], [0, 15, 11, 16, 16.1, 12, "as_yellow"]],
        "Pit Steps", [[0, 0, 0, 16, 5.5, 16], [0, 0, 5.5, 16, 11, 16], [0, 0, 11, 16, 16, 16]], sound="STONE")
    for lit in (False, True):
        add("as_pit_lamp" + ("_lit" if lit else ""), [[0, 0, 0, 16, 16, 16, faces("as_pit_wall", "as_concrete")],
                                                      [3, 5, -0.5, 13, 11, 0, "as_lamp_lit" if lit else "as_lamp"]],
            "Pit Light", full, sound="STONE", **({"light": 12} if lit else {}))
    add("as_column_lift_lo", [[1, 0, 1, 15, 3, 15, "as_yellow"]] + casters(0, 1, 15, 1, 15)
        + [[5, 3, 9, 11, 16, 15, "as_yellow"], [4, 1, 0, 12, 3, 5, "as_black"], [12, 5, 10, 15, 11, 14, faces("as_crt", "as_grey")]],
        "Column Lift", [[1, 0, 0, 15, 16, 15]])
    add("as_column_lift_hi", [[5, 0, 9, 11, 14, 15, "as_yellow"], [4.5, 14, 8.5, 11.5, 16, 15.5, "as_black"],
                              [5.5, 6, 2, 10.5, 8, 9, "as_black"]], "Column Lift", [[4.5, 0, 2, 11.5, 16, 15.5]])
    add("as_bigtyre", [[0, 0, 4, 16, 16, 12, {"north": "as_tyre", "south": "as_tyre", "east": "as_tread", "west": "as_tread",
                                              "up": "as_tread", "down": "as_tread"}]], "Truck Tire", [[0, 0, 4, 16, 16, 12]], sound="WOOL",
        loot="auto_wheels_heavy")
    add("as_grease", casters(0, 4, 12, 4, 12) + [[4, 2, 4, 12, 14, 12, faces("as_red", "as_red")], [7, 14, 7, 9, 20, 9, STEEL],
                                                  [9, 18, 7.5, 14, 19, 8.5, STEEL]], "Grease Cart", [[4, 0, 4, 14, 20, 12]])
    # ---- the scrapyard
    for name in ("red", "blue", "white", "green", "rust", "tan"):
        tex = f"as_flat_{name}"
        add(f"as_flat_{name}", [[0, 0, 0.5, 16, 15.5, 15.5, {"north": tex, "south": tex, "up": "as_scrap", "down": "as_scrap",
                                                            "east": tex, "west": tex}]], "Crushed Car", [[0, 0, 0.5, 16, 15.5, 15.5]],
            rotate="axis")
    add("as_crusher_bed", [[0, 0, 0, 16, 4, 16, faces("as_crusher", "as_crusher")], [0, 4, 0, 16, 5, 2, "as_crusher"],
                           [0, 4, 14, 16, 5, 16, "as_crusher"]], "Car Crusher", [[0, 0, 0, 16, 5, 16]], rotate="axis")
    add("as_crusher_post", [[3, 0, 3, 13, 16, 13, "as_crusher"], [6, 0, 2, 10, 16, 3, "as_chrome"]], "Car Crusher", [[3, 0, 2, 13, 16, 13]])
    add("as_crusher_lid", [[0, 10, 0, 16, 16, 16, faces("as_crusher", "as_crusher")], [0, 8, 2, 16, 10, 14, "as_crusher"]],
        "Car Crusher", [[0, 8, 0, 16, 16, 16]], rotate="axis")
    add("as_crusher_cab", [[1, 0, 2, 15, 12, 14, faces("as_crusher", "as_crusher")], [2, 12, 3, 14, 13, 13, "as_black"],
                           [3, 6, 1.5, 13, 11, 2, "as_crt"], [12, 13, 10, 14, 20, 12, "as_black"]], "Car Crusher", [[1, 0, 1.5, 15, 20, 14]])
    add("as_magnet", [[2, 0, 2, 14, 5, 14, faces("as_engine", "as_engine")], [3, 5, 3, 13, 7, 13, "as_engine"],
                      [7, 7, 4, 9, 16, 5, STEEL], [7, 7, 11, 9, 16, 12, STEEL], [4, 7, 7, 5, 16, 9, STEEL], [11, 7, 7, 12, 16, 9, STEEL]],
        "Crane Magnet", [[2, 0, 2, 14, 16, 14]], rotate=None)
    add("as_chain", [[7, 0, 7.5, 9, 16, 8.5, STEEL], [7.5, 0, 7, 8.5, 16, 9, STEEL]], "Chain", [[7, 0, 7, 9, 16, 9]], rotate=None, decor=True)
    add("as_tin", [[0, 0, 7, 16, 16, 8.5, faces("as_tin", "as_tin")]], "Tin Fence", [[0, 0, 7, 16, 24, 8.5]])
    add("as_tin_post", [[0, 0, 7, 16, 16, 8.5, faces("as_tin", "as_tin")], [6.5, 0, 8.5, 9.5, 18, 11.5, "as_rust"]], "Tin Fence",
        [[0, 0, 7, 16, 24, 11.5]])
    add("as_tin_gate", [[0, 0, 7.5, 16, 15, 9, faces("as_tin_paint", "as_tin_paint")], [0, 0, 7, 16, 1.5, 9.5, "as_rust"],
                        [0, 13.5, 7, 16, 15, 9.5, "as_rust"], [0, 1, 7, 16, 2.5, 9.5, "as_rust", None,
                                                              {"axis": "z", "angle": 22.5, "origin": [8, 8, 8]}]],
        "Yard Gate", [[0, 0, 7, 16, 24, 9.5]])
    add("as_scrap", [[1, 0, 1, 15, 5, 15, "as_scrap"], [3, 5, 2, 12, 9, 13, "as_rust"], [5, 9, 5, 10, 12, 10, "as_engine"],
                     [0, 2, 6, 16, 3, 8, "as_rust", None, {"axis": "y", "angle": 22.5, "origin": [8, 2, 7]}],
                     [6, 4, 0, 8, 12, 2, "as_rust", None, {"axis": "x", "angle": 22.5, "origin": [7, 4, 1]}]],
        "Scrap", [[0, 0, 0, 16, 12, 16]], rotate=None)
    add("as_partsbin", [[0, 0, 0, 16, 10, 16, faces("as_steel", "as_steel")], [2, 8, 2, 14, 12, 14, "as_engine"],
                        [3, 10, 3, 8, 14, 8, "as_shelf_brakes"], [9, 10, 8, 14, 13, 13, "as_rust"]], "Parts Bin", [[0, 0, 0, 16, 14, 16]],
        loot="auto_scrap")
    out["as_scale"] = {"tex": {"up": "as_scale", "down": "as_concrete", "side": "as_concrete"}, "title": "Truck Scale", "sound": "STONE"}
    # ---- the signs
    for key, spec in SIGNS.items():
        for c in range(spec["cols"]):
            for r in range(spec["rows"]):
                add(f"as_s_{key}_{c}{r}", [[0, 0, 13.5, 16, 16, 16, faces(f"as_s_{key}_{c}{r}", "as_galv" if not spec.get("wood") else "as_laminate")]],
                    "Shop Sign", [[0, 0, 13.5, 16, 16, 16]], sound="WOOD" if spec.get("wood") else "METAL")
    for key in SMALL:
        add(f"as_sign_{key}", [[2, 2, 15, 14, 14, 16, faces(f"as_sign_{key}", GALV)]], "Sign", [[2, 2, 15, 14, 14, 16]], decor=True)
    # ---- the plain cubes
    for k in ("metal", "metal_tan", "metal_rust"):
        out[f"as_{k}"] = {"tex": f"as_{k}", "title": "Metal Wall", "sound": "METAL"}
    for k in ("floor", "floor_oil", "floor_epoxy", "floor_drain", "floor_stripe"):
        out[f"as_{k}"] = {"tex": {"up": f"as_{k}", "down": "as_concrete", "side": "as_concrete"}, "title": "Shop Floor", "sound": "STONE"}
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    # in place: blocks.json is append-only, its order is the registry's
    data["blocks"] = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith("as_") or k in made}
    data["blocks"].update({k: v for k, v in made.items() if k not in data["blocks"]})
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} auto shop blocks in {path}")


if __name__ == "__main__":
    main()
