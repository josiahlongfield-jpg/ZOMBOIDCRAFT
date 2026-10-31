"""The sy_ kit: Pelican Point Shipyard's own pieces (Josiah, 2026-10-30: the coast and industry
places, the shipyard first), after refs/shipyard: the graving dock's stepped granite altars, its
keel blocks and the timber shores to the hull, the caisson across its mouth (drydock-ship); the
launching ways, the cradle and the hull in red-oxide primer on the berth, the bottle hung at her
bow for a launch that never came (slipway-hull-construction); the ship blocks waiting on their
stools, their webs and stiffeners open to the sky (ship-blocks); the goliath crane's name across
its girder (goliath-crane); the steel plates and profiles in their stacks, the magnet beam and
the plasma table (steel-plate-yard, industry/rolling-mill). world/county/Shipyard.java and its
parts put them together.

- sy_primer(_seam, _chalk), sy_zinc(_seam): red-oxide and zinc shop primer over steel plate, the
  welds between plates, the fitters' chalk;
- sy_altar(_wet): the dock's dressed granite steps; sy_dock: its floor, always wet;
  sy_caisson(_weed): the caisson's black riveted face, its fendering, weed where the sea is;
- sy_keel / sy_keel_cap: a keel block, concrete with its oak caps and wedges / the caps alone;
  sy_shore(_end): a timber shore along z, square sawn, its wedges at the end (axis);
- sy_ways / sy_ribband: a launching way along z, the tallow on it, the guide strip (axis);
  sy_poppet: the cradle's timbers under the hull's ends (axis);
- sy_frame: a hull frame standing where the plating hasn't gone on yet (axis);
- sy_web: an open block's web frame, its lightening hole (axis, see-through); sy_stiff: the
  floor plating and its stiffeners (axis);
- sy_plates(_low): a stack of steel plates (axis); sy_profiles: a bundle of bulb bars (axis);
  sy_panel: a panel off the line, the plate and its stiffeners (axis);
- sy_magnet / sy_magnet_beam: the plate crane's magnets on their beam (axis);
- sy_cnc / sy_cnc_beam: the plasma table's slats with a plate half cut / its gantry (axis);
- sy_prop_hub, sy_prop_a, sy_prop_b: a propeller in the plane across the hull, the blades on the
  diagonals (facing); sy_rudder: the rudder plate (axis);
- sy_spmt: a module of the transporter, its deck and wheel sets (facing);
- sy_cyls: a cage of gas bottles (facing); sy_vapor: a cryogenic tank's vaporizer (none);
- sy_hose: welding leads and gas hoses coiled on the floor; sy_bottle: the launch bottle on its
  ribbon; sy_bunting: the launch flags strung along x (axis); sy_capstan: a dockside capstan;
  sy_chain: the dock edge's chain between its stanchions (facing);
- sy_jig: a pin jig's posts (none); sy_lug: a block's lifting lug and shackle (facing);
  sy_manhole: a deck manhole with its cover unbolted (facing);
- signs sy_sgn_KEY_0..1; the goliath's lettering sy_gname_0..59 (20 by 3); the company's name
  sy_name_0..11; the halls' names sy_hall_a_0..7, sy_hall_b_0..9; the hull number sy_hull_0..5;
  block numbers sy_blk_0..7; graffiti sy_spray_KEY_i.

    python3 tools/city/shipyard.py      rewrites the sy_ entries in blocks.json

photo_textures.py draws the textures (shipyard.register()).
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
import ports  # noqa: E402
from furniture import area, disc, drips  # noqa: E402
from school import Pic, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD, STENCIL, SPRAY = B.BOLD, B.STENCIL, B.SPRAY
PRIMER = (134, 58, 40)
ZINC = (128, 130, 126)
INK = (30, 28, 26)
WHITE = (240, 238, 230)
RED = (170, 32, 28)
NAVY = (30, 44, 68)
YELLOW = (226, 186, 40)
GRANITE = (112, 110, 104)
TIMBER = (88, 64, 42)
BLACK = (34, 34, 34)


# ---------------------------------------------------------------- textures

def primer(seed, rgb=PRIMER, seam=False, chalk=False):
    """Shop primer over steel plate: the mill scale's mottle under it, rust blooming at the edges,
    a butt weld up the plate every block, the seam weld along it in the _seam piece, the fitters'
    chalk marks and frame numbers in the _chalk one."""
    t = F.plain(seed, rgb, 0.05, 0.35)
    t.mix(np.clip(P.fbm(t.rng) * 1.4, 0, 1) * 0.25, mixc(rgb, (0, 0, 0), 0.25))
    area(t, 0, 0, 2, N, mul=(0.8, 0.78, 0.76), dh=0.35)
    area(t, 2, 0, 3, N, mul=(1.1, 1.1, 1.1), dh=0.1)
    if seam:
        area(t, 0, 30, N, 33, mul=(0.78, 0.74, 0.72), dh=0.4)
        for x in range(0, N, 3):
            area(t, x, 30, x + 1, 33, mul=(0.9, 0.9, 0.9))
    ports.rust(t, 0.18)
    if chalk:
        p = Pic(1, 1, (0, 0, 0, 0), seed)
        p.line([(10, 18), (52, 18)], (226, 224, 214), 1, a=200)
        p.text(14, 22, f"FR {seed % 90 + 20}", STENCIL, 8, (226, 224, 214), a=190)
        p.text(30, 40, "OK", SPRAY, 9, (226, 224, 214), a=170)
        a = p.shrunk()
        m = a[..., 3] > 60
        t.c[m] = t.c[m] * 0.3 + a[..., :3][m] * 0.7
    t.grime(0.2, (60, 40, 30))
    return t


def granite(seed, wet=False):
    """The dock's altars: dressed granite in courses a block high, the joints pointed, lime bled
    from them, green and black where the dock floods and drains."""
    t = F.plain(seed, GRANITE, 0.07, 0.15)
    t.speckle(0.18, (150, 146, 140), 0.3)
    t.speckle(0.12, (70, 68, 64), 0.3)
    area(t, 0, 0, N, 2, mul=(0.7, 0.7, 0.68), dh=-0.3)
    area(t, 0, 0, 2, N, mul=(0.72, 0.72, 0.7), dh=-0.3)
    area(t, 31, 2, 33, N, mul=(0.72, 0.72, 0.7), dh=-0.3)
    t.streaks(0.4, 0.75)
    if wet:
        t.mix(np.clip(P.fbm(t.rng) + 0.4, 0, 1) * 0.6, (54, 62, 50))
        t.mix(np.clip(P.fbm(t.rng) - 0.2, 0, 1) * 0.6, (40, 40, 36))
        t.smooth[:] = 0.65
    return t


def dock_floor(seed):
    """The dock floor: concrete under a skin of silt and paint chips, always wet, the drains'
    gratings across it."""
    t = F.plain(seed, (110, 108, 100), 0.06, 0.55)
    t.mix(np.clip(P.fbm(t.rng) + 0.3, 0, 1) * 0.55, (78, 72, 60))
    t.speckle(0.05, PRIMER, 0.4)
    t.speckle(0.03, (40, 40, 40), 0.3)
    t.smooth[:] = 0.7
    return t


def caisson(seed, weed=False):
    """The caisson's face: black bitumen paint over riveted plate, the rivet rows, the rubbing
    strake across it, rust at every rivet; weed and barnacles where the sea stands on it."""
    t = F.plain(seed, BLACK, 0.05, 0.4)
    for y in (4, 36):
        for x in range(2, N, 5):
            disc(t, x, y, 0.9, col=(52, 50, 48), dh=0.3)
    area(t, 0, 50, N, 58, col=(58, 46, 34), dh=0.5)
    ports.rust(t, 0.15)
    if weed:
        t.mix(np.clip(P.fbm(t.rng) + 0.5, 0, 1) * 0.8, (58, 72, 44))
        t.speckle(0.1, (190, 186, 170), 0.6, 0.3)
    return t


def timber(seed, grease=False):
    """Creosoted timber, the grain along it, checks and the bolts' heads; the ways' tallow over it."""
    t = F.wood(seed, "dark")
    t.c *= 0.75
    t.crack(3, 0.3, (20, 16, 12))
    if grease:
        t.mix(np.clip(P.fbm(t.rng) + 0.5, 0, 1) * 0.7, (186, 176, 140))
        t.smooth[:] = 0.75
    return t


def plate_top(seed):
    """A steel plate's face: mill scale, rust bloom, the heat number and grade stencilled in white."""
    t = F.plain(seed, (92, 90, 88), 0.06, 0.3)
    t.mix(np.clip(P.fbm(t.rng) * 1.6, 0, 1) * 0.45, (60, 62, 64))
    ports.rust(t, 0.3)
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(6, 8, f"AH36 {seed % 9000 + 1000}", STENCIL, 6, WHITE, a=170)
    a = p.shrunk()
    m = a[..., 3] > 60
    t.c[m] = a[..., :3][m] * 0.8
    return t


def plate_edge(seed):
    """The stack's side: a plate edge every few pixels, the gaps between dark."""
    t = F.plain(seed, (84, 80, 78), 0.06, 0.3)
    for y in range(0, N, 6):
        area(t, 0, y, N, y + 1, col=(30, 28, 26), dh=-0.4)
    ports.rust(t, 0.35)
    return t


def bronze(seed):
    t = F.plain(seed, (176, 128, 62), 0.06, 0.7)
    t.f0[:] = 200
    t.mix(np.clip(P.fbm(t.rng) - 0.1, 0, 1) * 0.5, (86, 112, 90))
    return t


def yellow_paint(seed):
    t = F.plain(seed, YELLOW, 0.05, 0.45)
    ports.rust(t, 0.1)
    t.grime(0.3, (60, 50, 30))
    return t


SIGNS = {
    "dock": ("No.1 DRY DOCK", NAVY, WHITE),
    "proud": ("1046 HULLS|LAUNCHED HERE", NAVY, WHITE),
    "deep": ("DANGER|DEEP DOCK", RED, WHITE),
    "berth": ("No.2 BERTH", NAVY, WHITE),
    "hats": ("HARD HATS|MUST BE WORN", (30, 110, 60), WHITE),
    "plate": ("PLATE YARD", NAVY, WHITE),
    "outfit": ("OUTFIT QUAY", NAVY, WHITE),
    "gate": ("GATE 1|ALL VISITORS|REPORT", NAVY, WHITE),
    "closed": ("YARD CLOSED|NO ENTRY", RED, WHITE),
    "caisson": ("CAISSON|NO CROSSING|WHEN FLOODING", YELLOW, INK),
    "load": ("SWL 40 T", YELLOW, INK),
    "days": ("0 DAYS|SINCE LAST|ACCIDENT", (30, 110, 60), WHITE),
    "gas": ("OXYGEN|NO NAKED FLAMES", YELLOW, INK),
    "blast": ("BLAST & PRIME", NAVY, WHITE),
    "pipe": ("PIPE SHOP", NAVY, WHITE),
    "canteen": ("CANTEEN", NAVY, WHITE),
    "office": ("MAIN OFFICE", NAVY, WHITE),
    "launch": ("HULL 1047|LAUNCH 14 OCT", WHITE, NAVY),
    "pump": ("DOCK PUMPS", NAVY, WHITE),
    "keep": ("KEEP CLEAR|CRANE RAIL", YELLOW, INK),
}
SPRAYS = {
    "still": ("STILL HERE", 4),
    "room": ("NO ROOM", 3),
    "launch": ("LAUNCH HER", 4),
    "pulled": ("LADDERS UP", 4),
}
GOLIATH = "PELICAN POINT"
NAME = "PELICAN POINT SHIPBUILDING CO"
HALLS = {"a": ("PANEL LINE", 8), "b": ("BLOCK ASSEMBLY", 10)}
BLOCKS = ["B 214", "B 215", "D 301", "S 122", "S 123", "E 410", "F 501", "B 216"]


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 56400 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 12, W - 1, H - 12, fill=field)
    p.rect(1, 12, W - 1, H - 12, outline=mixc(field, (0, 0, 0), 0.3), width=1)
    lines = text.split("|")
    font = STENCIL if field in (YELLOW, WHITE) else BOLD
    size = civic.fit(text, font, 12 if len(lines) == 1 else 9 if len(lines) == 2 else 7, W - 12)
    for i, line in enumerate(lines):
        y = H / 2 + (i - (len(lines) - 1) / 2) * size * 1.15
        p.text(W / 2, y, line, font, size, ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 56450 + i + len(key) * 11, smooth=0.5, grime=0.25)


@lru_cache(maxsize=None)
def goliath_pic():
    """The name down the goliath's girder: black capitals on its yellow, twenty blocks by three."""
    p = Pic(20, 3, YELLOW, 56500)
    p.text(p.W / 2, p.H / 2 + 2, GOLIATH, BOLD, civic.fit(GOLIATH, BOLD, 150, p.W - 40), (24, 24, 24), anchor="mm")
    return p


def goliath(i):
    t = goliath_pic().tile(i, 56510 + i, smooth=0.45, grime=0.3)
    ports.rust(t, 0.08)
    return t


@lru_cache(maxsize=None)
def name_pic():
    """The company's name in cast letters across the office front, twelve blocks."""
    p = Pic(12, 1, (0, 0, 0, 0), 56600)
    p.text(p.W / 2, p.H / 2, NAME, BOLD, civic.fit(NAME, BOLD, 36, p.W - 16), (196, 192, 180), anchor="mm")
    return p


def name_tile(i):
    return name_pic().tile(i, 56610 + i, smooth=0.6, grime=0.3)


@lru_cache(maxsize=None)
def hall_pic(key):
    """A hall's name painted high on its end wall, two rows of blocks."""
    text, cols = HALLS[key]
    p = Pic(cols, 2, (0, 0, 0, 0), 56700 + cols)
    p.text(p.W / 2, p.H / 2, text, BOLD, civic.fit(text, BOLD, 90, p.W - 20), (232, 230, 220), anchor="mm")
    return p


def hall_tile(key, i):
    return hall_pic(key).tile(i, 56710 + i + len(key), smooth=0.3, grime=0.35)


@lru_cache(maxsize=None)
def hull_pic():
    """The yard number on her bow: HULL 1047 in white, six blocks."""
    p = Pic(6, 1, (0, 0, 0, 0), 56800)
    p.text(p.W / 2, p.H / 2, "HULL 1047", STENCIL, civic.fit("HULL 1047", STENCIL, 40, p.W - 10), WHITE, anchor="mm")
    return p


def hull_tile(i):
    return hull_pic().tile(i, 56810 + i, smooth=0.3, grime=0.2)


@lru_cache(maxsize=None)
def blk_pic(i):
    p = Pic(1, 1, (0, 0, 0, 0), 56900 + i)
    p.text(p.W / 2, p.H / 2, BLOCKS[i], STENCIL, civic.fit(BLOCKS[i], STENCIL, 18, p.W - 6), WHITE, anchor="mm")
    return p


def blk(i):
    return blk_pic(i).tile(0, 56910 + i, smooth=0.3, grime=0.2)


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 56950 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (226, 222, 210), jitter=0.4, a=235, angle=-2)
    return p


def spray(key, i):
    return spray_pic(key).tile(i, 56960 + i + len(key) * 5, smooth=0.3, grime=0.0)


def textures():
    T = {
        "sy_primer": lambda: primer(56000),
        "sy_primer_seam": lambda: primer(56001, seam=True),
        "sy_primer_chalk": lambda: primer(56002, chalk=True),
        "sy_zinc": lambda: primer(56003, ZINC),
        "sy_zinc_seam": lambda: primer(56004, ZINC, seam=True),
        "sy_altar": lambda: granite(56005),
        "sy_altar_wet": lambda: granite(56006, True),
        "sy_dock": lambda: dock_floor(56007),
        "sy_caisson": lambda: caisson(56008),
        "sy_caisson_weed": lambda: caisson(56009, True),
        "sy_timber": lambda: timber(56010),
        "sy_tallow": lambda: timber(56011, True),
        "sy_plate_top": lambda: plate_top(56012),
        "sy_plate_edge": lambda: plate_edge(56013),
        "sy_bronze": lambda: bronze(56014),
        "sy_yellow": lambda: yellow_paint(56015),
    }
    for key in SIGNS:
        for i in range(2):
            T[f"sy_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for i in range(60):
        T[f"sy_gname_{i}"] = lambda i=i: goliath(i)
    for i in range(12):
        T[f"sy_name_{i}"] = lambda i=i: name_tile(i)
    for key, (_, cols) in HALLS.items():
        for i in range(cols * 2):
            T[f"sy_hall_{key}_{i}"] = lambda key=key, i=i: hall_tile(key, i)
    for i in range(6):
        T[f"sy_hull_{i}"] = lambda i=i: hull_tile(i)
    for i in range(len(BLOCKS)):
        T[f"sy_blk_{i}"] = lambda i=i: blk(i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"sy_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

STEEL, GALV, DARK, CONC = "painted_steel", "st_galv", "home_plastic_black", "concrete_dark"
TIM, TAL, PRI = "sy_timber", "sy_tallow", "sy_primer"


def keel(cap_only=False):
    """A keel block: the concrete block, two oak caps crossed on it, the hardwood wedges on top."""
    b = [[2, 12, 0, 14, 15, 16, TIM], [5, 15, 1, 11, 16, 15, TAL]]
    if not cap_only:
        b = [[1, 0, 1, 15, 8, 15, CONC], [0, 8, 2, 16, 12, 14, TIM]] + b
    else:
        b = [[0, 0, 2, 16, 4, 14, TIM]] + [[x[0], x[1] - 8, x[2], x[3], x[4] - 8, x[5], x[6]] for x in b]
    return b


def shore(end=False):
    """A shore along z, ten inches square; at its end the folding wedges under the hull's plating."""
    b = [[3, 3, 0, 13, 13, 16 if not end else 12, TIM]]
    if end:
        b += [[2, 4, 12, 14, 8, 16, TAL], [2, 8, 12, 14, 12, 15, TIM]]
    return b


def ways():
    """A standing way along z: the timber laid on the slip, the sliding way on the tallow over it."""
    return [[0, 0, 0, 16, 6, 16, TIM], [1, 6, 0, 15, 8, 16, TAL], [2, 8, 0, 14, 12, 16, TIM]]


def ribband():
    """The ribband along the way's edge that keeps the sliding way on it, bolted down."""
    return [[0, 0, 0, 16, 4, 16, TIM], [10, 4, 0, 15, 9, 16, TIM], [11.5, 9, 3, 13.5, 10, 5, STEEL],
            [11.5, 9, 11, 13.5, 10, 13, STEEL]]


def poppet():
    """The cradle under the hull's fine ends: the timber posts up to the plating, the cross braces."""
    b = [[1, 0, 1, 5, 16, 5, TIM], [11, 0, 1, 15, 16, 5, TIM], [1, 0, 11, 5, 16, 15, TIM], [11, 0, 11, 15, 16, 15, TIM]]
    b += [[0, 6, 2, 16, 9, 4, TIM], [0, 11, 12, 16, 14, 14, TIM], [2, 3, 0, 4, 6, 16, TIM]]
    return b


def frame():
    """A frame standing where the shell plating is still to come: a bulb bar up the block, its flange."""
    return [[6, 0, 0, 10, 16, 2.5, PRI], [6, 0, 2.5, 7.5, 16, 9, PRI], [6, 0, 8, 10, 16, 9.5, "sy_primer_seam"]]


def web():
    """An open block's web frame across it: the plate round its lightening hole, the face flat."""
    b = [[0, 0, 7, 16, 4, 9, PRI], [0, 12, 7, 16, 16, 9, PRI], [0, 4, 7, 3, 12, 9, PRI], [13, 4, 7, 16, 12, 9, PRI]]
    b += [[0, 15, 6, 16, 16, 10, "sy_primer_seam"]]
    return b


def stiff():
    """The floor plating of an open block and the longitudinals on it every half block."""
    return [[0, 0, 0, 16, 2, 16, "sy_primer_seam"], [3, 2, 0, 4.5, 7, 16, PRI], [2.5, 6, 0, 5, 7.5, 16, PRI],
            [11, 2, 0, 12.5, 7, 16, PRI], [10.5, 6, 0, 13, 7.5, 16, PRI]]


def plates(n):
    """A stack of n plates along z, each a little off the one below."""
    b = []
    for k in range(n):
        o = (k * 37 % 5) * 0.3
        b.append([0.2 + o, k * 1.6, 0, 15.8 - o * 0.5, k * 1.6 + 1.4, 16, {"side": "sy_plate_edge", "up": "sy_plate_top",
                                                                              "down": "sy_plate_edge"}])
    return b


def profiles():
    """A bundle of bulb flats along z, the steel straps round it."""
    b = []
    for i in range(4):
        for j in range(2):
            x, y = 1.5 + i * 3.4, j * 4.5
            b += [[x, y, 0, x + 1, y + 4, 16, "sy_plate_edge"], [x, y + 3, 0, x + 2, y + 4.2, 16, "sy_plate_edge"]]
    b += [[1, 0, z, 15, 9.2, z + 0.6, GALV] for z in (3, 12.4)]
    return b


def panel():
    """A panel off the line: the plate, its stiffeners welded on along z."""
    b = [[0, 0, 0, 16, 1.5, 16, {"side": "sy_plate_edge", "up": "sy_zinc_seam", "down": "sy_plate_edge"}]]
    for x in (3, 11):
        b += [[x, 1.5, 0, x + 1.2, 6, 16, "sy_zinc"], [x - 0.6, 5, 0, x + 1.8, 6.2, 16, "sy_zinc"]]
    return b


def magnet():
    """A lifting magnet: the drum, its cable up to the beam."""
    return [[3, 0, 3, 13, 5, 13, DARK], [2.5, 4, 2.5, 13.5, 5.5, 13.5, "sy_yellow"], [7, 5.5, 7, 9, 16, 9, DARK]]


def magnet_beam():
    """The plate crane's spreader beam along z, two magnets hung under it."""
    b = [[4, 8, 0, 12, 14, 16, "sy_yellow"], [3, 13, 0, 13, 15, 16, "sy_yellow"]]
    b += [[4, 0, z, 12, 4, z + 5, DARK] for z in (1, 10)] + [[7, 4, z + 2, 9, 8, z + 3, DARK] for z in (1, 10)]
    return b


def cnc():
    """The plasma table: its frame, the slats on edge, a plate on them with the parts cut from it."""
    b = [[0, 0, 0, 16, 9, 16, {"side": STEEL, "up": "sy_plate_edge", "down": STEEL}]]
    b += [[x, 9, 0, x + 0.6, 12, 16, "sy_plate_edge"] for x in np.arange(0.5, 16, 2)]
    b += [[1, 12, 1, 15, 12.6, 15, {"side": "sy_plate_edge", "up": "sy_plate_top", "down": "sy_plate_edge"}]]
    return b


def cnc_beam():
    """The plasma table's gantry along z: the beam, the torch carriages hung off it."""
    b = [[3, 10, 0, 13, 15, 16, "sy_yellow"], [2, 15, 0, 14, 16, 16, "sy_yellow"]]
    b += [[6, 3, z, 10, 10, z + 4, STEEL] for z in (2, 10)] + [[7.5, 0, z + 1.5, 8.5, 3, z + 2.5, "al_chrome"] for z in (2, 10)]
    return b


def prop_hub():
    return [[3, 3, 3, 13, 13, 13, "sy_bronze"], [5, 5, 0, 11, 11, 3, "sy_bronze"], [6, 6, 13, 10, 10, 16, STEEL]]


def prop_blade(sign):
    """A blade across the block's diagonal in the plane z = 8, twisted: two plates at their angles."""
    return [[-3, 6.5, 6, 19, 9.5, 8, "sy_bronze", None, {"axis": "z", "angle": 45 * sign, "origin": [8, 8, 8]}],
            [-1, 6, 8, 17, 10, 9.5, "sy_bronze", None, {"axis": "z", "angle": 45 * sign, "origin": [8, 8, 8]}]]


def rudder():
    """The rudder: a streamlined plate along z, its stock up the middle."""
    return [[6, 0, 0, 10, 16, 16, "sy_primer_seam"], [5.5, 0, 6, 10.5, 16, 10, PRI]]


def spmt():
    """A transporter module: the yellow deck, the wheel sets under it on their pendulum arms."""
    b = [[0, 7, 0, 16, 11, 16, "sy_yellow"], [0, 11, 0, 16, 11.5, 16, "deck_steel"]]
    for x in (1, 9):
        for z in (1, 9):
            b += [[x, 0, z, x + 2.5, 6, z + 6, "tire"], [x + 3.5, 0, z, x + 6, 6, z + 6, "tire"], [x + 2.4, 5, z + 2, x + 3.6, 8, z + 4, STEEL]]
    return b


def cyls():
    """A cage of gas bottles: the mesh, the oxygen black with white shoulders, the acetylene maroon."""
    b = [[0, 0, 0, 16, 1, 16, GALV], [0, 1, 0, 1, 16, 16, "chainlink"], [15, 1, 0, 16, 16, 16, "chainlink"],
         [0, 1, 15, 16, 16, 16, "chainlink"], [0, 15.4, 0, 16, 16, 16, GALV]]
    for i, (x, z) in enumerate([(2, 2), (6, 2), (10, 2), (2, 8), (6, 8), (10, 8)]):
        col = DARK if i % 3 else "car_red"
        b += [[x, 1, z, x + 3.4, 12, z + 3.4, col], [x + 0.4, 12, z + 0.4, x + 3, 13.5, z + 3, "bunk_white_steel" if i % 3 else col],
              [x + 1.2, 13.5, z + 1.2, x + 2.2, 15, z + 2.2, "al_chrome"]]
    return b


def vapor():
    """A vaporizer column: the tube up the middle, the aluminium fins out to the corners."""
    return [[6, 0, 6, 10, 16, 10, GALV], [0, 0, 7.5, 16, 16, 8.5, GALV], [7.5, 0, 0, 8.5, 16, 16, GALV]]


def hose():
    """Welding leads and gas hoses coiled on the floor."""
    return [[2, 0, 2, 14, 1, 4, DARK], [2, 0, 12, 14, 1, 14, DARK], [2, 0, 4, 4, 1, 12, DARK], [12, 0, 4, 14, 1, 12, DARK],
            [5, 1, 5, 11, 1.8, 6.5, "car_red"], [5, 1, 9.5, 11, 1.8, 11, "car_red"], [5, 1, 6.5, 6.5, 1.8, 9.5, "car_red"]]


def bottle():
    """The launch bottle on its ribbon, hung from the platform's rail and never swung."""
    return [[7.5, 6, 7.5, 8.5, 16, 8.5, "bunk_white_steel"], [6.8, 0, 6.8, 9.2, 4, 9.2, "car_red"],
            [7.2, 4, 7.2, 8.8, 6, 8.8, "al_chrome"]]


def bunting():
    """The launch flags strung along z: the line and its pennants, red, white and blue."""
    b = [[7.8, 13, 0, 8.2, 13.4, 16, "bunk_white_steel"]]
    for i, col in enumerate(("car_red", "bunk_white_steel", "al_navy")):
        z = 1 + i * 5.3
        b += [[7.7, 10, z, 8.3, 13, z + 3, col], [7.7, 8, z + 1, 8.3, 10, z + 2, col]]
    return b


def capstan():
    """A dockside capstan: the iron drum, its waist and cap, the bed plate."""
    return [[2, 0, 2, 14, 2, 14, "pw_crane"], [4, 2, 4, 12, 12, 12, DARK], [5, 5, 5, 11, 9, 11, DARK],
            [3.5, 12, 3.5, 12.5, 14, 12.5, DARK]]


def chain():
    """The dock edge's guard: the stanchion on the cell's north edge, the chain sagging along x."""
    return [[7, 0, 1, 9, 14, 3, "sy_yellow"], [0, 11, 1.6, 16, 11.6, 2.4, DARK], [3, 10, 1.6, 13, 10.6, 2.4, DARK]]


def jig():
    """A pin jig: four screw posts at their heights to hold a curved plate."""
    b = [[0, 0, 0, 16, 1, 16, STEEL]]
    for i, (x, z) in enumerate([(2, 2), (11, 2), (2, 11), (11, 11)]):
        b += [[x, 1, z, x + 3, 6 + i * 2, z + 3, "sy_yellow"]]
    return b


def lug():
    """A lifting lug welded to a block's deck: the padeye plate along x with its doubler, the shackle
    hung through the eye."""
    return [[3, 0, 5, 13, 0.6, 11, "sy_primer_seam"], [5, 0.6, 7.2, 11, 7, 8.8, "sy_primer"],
            [6.5, 7, 7.2, 9.5, 8.5, 8.8, "sy_primer"], [7.3, 4.5, 6.4, 8.7, 10, 7, DARK], [7.3, 4.5, 9, 8.7, 10, 9.6, DARK],
            [7.3, 9.5, 6.4, 8.7, 11, 9.6, DARK], [6.6, 4, 6, 9.4, 4.6, 10, GALV]]


def manhole():
    """A deck manhole's bolted coaming with its cover unbolted and leant open beside it."""
    b = [[2, 0, 2, 14, 1.2, 3, STEEL], [2, 0, 13, 14, 1.2, 14, STEEL], [2, 0, 3, 3, 1.2, 13, STEEL],
         [13, 0, 3, 14, 1.2, 13, STEEL], [3, 0, 3, 13, 0.1, 13, DARK]]
    b += [[1, 1.2, 0.4, 15, 2, 2, "sy_primer"]]
    for x in (3, 6, 9.5, 12.5):
        b += [[x, 1.2, 2.2, x + 0.8, 1.8, 2.8, DARK]]
    return b


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        if boxes and rotate == "axis":
            # RULE: axis models run along z as drawn; axis= names the way a row of them runs
            if P.drawn_along(boxes) == "x" and "shape" in extra:
                extra["shape"] = [[b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]] for b in extra["shape"]]
            boxes = P.along_z(boxes)
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

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    cubes = {"sy_primer": "Primed Plate", "sy_primer_seam": "Primed Plate", "sy_primer_chalk": "Primed Plate",
             "sy_zinc": "Primed Plate", "sy_zinc_seam": "Primed Plate", "sy_altar": "Dock Altar", "sy_altar_wet": "Dock Altar",
             "sy_dock": "Dock Floor", "sy_caisson": "Caisson", "sy_caisson_weed": "Caisson", "sy_timber": "Timber",
             "sy_yellow": "Crane Steel"}
    for n, title in cubes.items():
        add(n, None, title, "STONE" if n.startswith(("sy_altar", "sy_dock")) else "WOOD" if n == "sy_timber" else "METAL",
            rotate=None, tex=n)
    add("sy_keel", keel(), "Keel Block", "STONE", rotate="axis", shape=[[0, 0, 0, 16, 16, 16]])
    add("sy_keel_cap", keel(True), "Keel Block Cap", "WOOD", rotate="axis", shape=[[0, 0, 0, 16, 8, 16]])
    add("sy_shore", shore(), "Shore", "WOOD", rotate="axis", shape=[[3, 3, 0, 13, 13, 16]])
    add("sy_shore_end", shore(True), "Shore", "WOOD", rotate="facing", shape=[[2, 3, 0, 14, 13, 16]])
    add("sy_ways", ways(), "Launching Way", "WOOD", rotate="axis", shape=[[0, 0, 0, 16, 12, 16]])
    add("sy_ribband", ribband(), "Ribband", "WOOD", rotate="axis", shape=[[0, 0, 0, 16, 9, 16]])
    add("sy_poppet", poppet(), "Cradle", "WOOD", rotate="axis", render="cutout", shape=[[0, 0, 0, 16, 16, 16]])
    add("sy_frame", frame(), "Hull Frame", "METAL", rotate="facing", shape=[[6, 0, 0, 10, 16, 9.5]])
    add("sy_web", web(), "Web Frame", "METAL", rotate="axis", shape=[[0, 0, 6, 16, 16, 10]])
    add("sy_stiff", stiff(), "Stiffened Plating", "METAL", rotate="axis", shape=[[0, 0, 0, 16, 7.5, 16]])
    add("sy_plates", plates(10), "Steel Plates", "METAL", rotate="axis", shape=[[0, 0, 0, 16, 16, 16]])
    add("sy_plates_low", plates(4), "Steel Plates", "METAL", rotate="axis", shape=[[0, 0, 0, 16, 6.4, 16]])
    add("sy_profiles", profiles(), "Bulb Bars", "METAL", rotate="axis", shape=[[1, 0, 0, 15, 9.2, 16]])
    add("sy_panel", panel(), "Stiffened Panel", "METAL", rotate="axis", shape=[[0, 0, 0, 16, 6.2, 16]])
    add("sy_magnet", magnet(), "Lifting Magnet", "METAL", rotate=None, shape=[[3, 0, 3, 13, 5.5, 13]])
    add("sy_magnet_beam", magnet_beam(), "Magnet Beam", "METAL", rotate="axis", shape=[[3, 0, 0, 13, 15, 16]])
    add("sy_cnc", cnc(), "Plasma Table", "METAL", rotate="axis", shape=[[0, 0, 0, 16, 12.6, 16]])
    add("sy_cnc_beam", cnc_beam(), "Plasma Gantry", "METAL", rotate="axis", shape=[[2, 3, 0, 14, 16, 16]])
    add("sy_prop_hub", prop_hub(), "Propeller", "METAL", shape=[[3, 3, 0, 13, 13, 16]])
    add("sy_prop_a", prop_blade(1), "Propeller Blade", "METAL", shape=[[0, 0, 6, 16, 16, 10]])
    add("sy_prop_b", prop_blade(-1), "Propeller Blade", "METAL", shape=[[0, 0, 6, 16, 16, 10]])
    add("sy_rudder", rudder(), "Rudder", "METAL", rotate="axis", shape=[[5.5, 0, 0, 10.5, 16, 16]])
    add("sy_spmt", spmt(), "Transporter", "METAL", shape=[[0, 0, 0, 16, 11.5, 16]])
    add("sy_cyls", cyls(), "Gas Bottles", "METAL", render="cutout", shape=[[0, 0, 0, 16, 16, 16]])
    add("sy_vapor", vapor(), "Vaporizer", "METAL", rotate=None, shape=[[0, 0, 0, 16, 16, 16]])
    add("sy_hose", hose(), "Welding Leads", "WOOL", decor=True)
    add("sy_bottle", bottle(), "Launch Bottle", "GLASS", rotate=None, decor=True)
    add("sy_bunting", bunting(), "Bunting", "WOOL", rotate="axis", decor=True)
    add("sy_capstan", capstan(), "Capstan", "METAL", rotate=None, shape=[[2, 0, 2, 14, 14, 14]])
    add("sy_chain", chain(), "Dock Chain", "METAL", render="cutout", shape=[[7, 0, 1, 9, 14, 3]])
    add("sy_lug", lug(), "Lifting Lug", "METAL", shape=[[3, 0, 5, 13, 11, 11]])
    add("sy_manhole", manhole(), "Manhole", "METAL", decor=True)
    add("sy_jig", jig(), "Pin Jig", "METAL", rotate=None, shape=[[0, 0, 0, 16, 12, 16]])
    for key in SIGNS:
        for i in range(2):
            add(f"sy_sgn_{key}_{i}", plate(f"sy_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for i in range(60):
        add(f"sy_gname_{i}", plate(f"sy_gname_{i}", 15.6), "Painted Name", "METAL", decor=True)
    for i in range(12):
        add(f"sy_name_{i}", plate(f"sy_name_{i}", 14.6), "Cast Lettering", "METAL", decor=True)
    for key, (_, cols) in HALLS.items():
        for i in range(cols * 2):
            add(f"sy_hall_{key}_{i}", plate(f"sy_hall_{key}_{i}", 15.8), "Painted Name", "METAL", decor=True)
    for i in range(6):
        add(f"sy_hull_{i}", plate(f"sy_hull_{i}", 15.8), "Hull Number", "METAL", decor=True)
    for i in range(len(BLOCKS)):
        add(f"sy_blk_{i}", plate(f"sy_blk_{i}", 15.8), "Block Number", "METAL", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"sy_spray_{key}_{i}", plate(f"sy_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("sy_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} shipyard blocks in {path}")


if __name__ == "__main__":
    main()
