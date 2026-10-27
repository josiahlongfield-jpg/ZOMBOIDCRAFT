"""The railway: the rw_ kit for the county's train lines (world/county/RailGen.java).

Two lines run through Grayhaven County, both made up: the GRAYHAVEN COUNTY RAILWAY (dark green
and cream, its stations' boards in cream capitals) round the whole county, on the ground in the
country, down its own street in the mainland's cities and on an el over the island's streets;
and the GRAYHAVEN SUBWAY under the island, its stations in white tile with each stop's name set
in a mosaic tablet in its own colour.

Drawn from what a North American line of the old kind is known to be (the photo library's rail
folders were still filling when this was made): crushed-stone ballast stained rust brown by
brake dust on a cinder shoulder, creosoted poles with glass insulators on their crossarms, a
small-town platform of broom-finished concrete with a yellow edge strip under a depot canopy
with a dagger-board valance, benches with cast-iron ends, gooseneck enamel lamps; the el's
riveted plate girders and built-up columns on their bents, painted dark green and rusting
through; plate-girder bridges on board-formed concrete piers; the subway's white subway tile
over a buff dado with a coloured band, its caged tunnel lamps and fluorescent strips, and up on
the sidewalk the green iron railings, the two globe lamps and the SUBWAY plate; crossbucks over
twin red lights on galvanized masts at the level crossings.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- the bed: rw_ballast, rw_ballast_{4,8,12} (part blocks where the bed top isn't whole),
  rw_subballast, rw_cinder, rw_street_bed (street track flush with the asphalt), rw_crossing;
- the el and the bridges: rw_deck, rw_girder, rw_grate, rw_guard (walkway railing on the north
  edge), rw_crossbeam (a bent's cross girder, along its axis), rw_column, rw_column_base,
  rw_column_cap, rw_pier, rw_pier_cap;
- the stations: rw_platform, rw_platform_edge (the yellow strip on the north, track, side),
  rw_platform_half, rw_platform_base, rw_canopy, rw_canopy_fascia, rw_post, rw_bench,
  rw_lamp_post, rw_lamp_head, rw_lamp_hang, rw_shelter_wall, rw_shelter_window, rw_timetable,
  rw_fence, rw_name_<stop>_0..3 (four boards);
- the subway: rw_tile, rw_tile_band, rw_tile_dado, rw_mosaic_<stop>_0..3, rw_tunnel,
  rw_tunnel_floor, rw_track_slab, rw_tunnel_lamp, rw_subway_lamp, rw_stair, rw_stair_steel,
  rw_subway_rail, rw_globe_post, rw_globe, rw_subway_sign_0..1;
- by the line: rw_xing_post, rw_xing_lights, rw_xing_buck, rw_pole, rw_pole_arm.

    python3 tools/city/railway.py        rewrites the rw_ entries in blocks.json

photo_textures.py draws the textures (railway.register()).
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
import school  # noqa: E402
from furniture import area, disc, edges  # noqa: E402
from school import Pic, mixc  # noqa: E402
from gasstation import glow, weather  # noqa: E402
from diner import stext, rivets, peel  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
BLACK = "/usr/share/fonts/opentype/inter/Inter-Black.otf"
SERIF = "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"
SANS = school.SANS

GREEN = (38, 62, 46)        # the railway's dark green
CREAM = (222, 210, 172)     # and its cream
EL_GREEN = (52, 68, 58)     # the el's paint, gone grey-green
SUB_GREEN = (40, 84, 58)    # the subway's iron railings and globes
RUST = (112, 64, 36)

# the stops with name boards (the railway's) and mosaics (the subway's): key -> what they say
STOPS = {
    "union": "UNION STATION", "uptown": "UPTOWN", "pinecrest": "PINECREST", "fort": "FORT CALDWELL",
    "riverside": "RIVERSIDE", "millbrook": "MILLBROOK", "dunmore": "DUNMORE", "fairview": "FAIRVIEW",
    "ashby": "ASHBY", "hollow": "HOLLOW CREEK", "kings": "KINGSBRIDGE", "seabright": "SEABRIGHT",
    "airport": "AIRPORT", "foundry": "FOUNDRY ROW", "southport": "SOUTHPORT",
}
MOSAICS = {
    # key: (what it says, the border's colour, the field's)
    "stadium": ("STADIUM", (122, 40, 34), (226, 214, 184)),
    "tower": ("BROADCAST|TOWER", (40, 74, 120), (226, 216, 190)),
    "power": ("POWER|STATION", (150, 106, 34), (224, 214, 186)),
    "waterfront": ("WATERFRONT", (36, 104, 104), (228, 220, 196)),
    "union": ("UNION|STATION", (92, 48, 92), (226, 214, 188)),
    "mercy": ("MERCY|GENERAL", (56, 96, 50), (228, 218, 192)),
}


# ---------------------------------------------------------------- the bed

def ballast(seed, dark=0.0):
    """Crushed trap rock: sharp grey stones, brake dust gone rust brown between them, oil drips."""
    t = F.mat("Gravel043", (118, 114, 106), 1.25, span=0.5, seed=seed, smooth=0.12)
    rng = t.rng
    # brake dust and rust settle in the gaps
    low = np.clip((0.62 - t.h) * 3, 0, 1)
    t.mix(low * (0.4 + 0.6 * P.fbm(rng)), (104, 70, 46), 0.5)
    for _ in range(int(2 + dark * 6)):
        F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(2, 5), col=(34, 30, 26), amount=0.22 + dark * 0.2, tide=False)
    t.speckle(0.03, (170, 166, 156), 0.4, 0.1)
    t.grime(0.12 + dark * 0.3, (36, 30, 26))
    return t


def subballast(seed):
    """The packed gravel under the ballast, finer and dirtier, seen on the embankment's edge."""
    t = F.mat("Gravel029", (112, 102, 88), 1.0, span=0.7, seed=seed, smooth=0.1)
    t.grime(0.25, (60, 50, 40))
    return t


def cinder(seed):
    """The cinder path along the line: black ash and clinker, rust flecks, weeds pushing through."""
    t = F.mat("tarred_gravel", (58, 54, 50), 1.1, span=0.8, seed=seed, smooth=0.08)
    rng = t.rng
    t.speckle(0.06, (96, 58, 34), 0.5)
    for _ in range(4):
        x, y = rng.uniform(0, N, 2)
        disc(t, x, y, rng.uniform(1.2, 2.6), (64, 82, 44), h=0.85, soft=0.5)
    t.grime(0.15)
    return t


def street_bed(seed):
    """The street track's panels: concrete laid flush with the asphalt, sawn joints, oil, wheel polish."""
    t = F.mat("Concrete034", (126, 124, 118), 0.7, span=1.5, seed=seed, smooth=0.2)
    rng = t.rng
    for y in (0, 31):
        area(t, 0, y, N, y + 1, mul=0.6, dh=-0.25)
    area(t, 0, 0, 1, N, mul=0.7, dh=-0.2)
    for _ in range(3):
        F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(3, 7), col=(40, 36, 32), amount=0.22, tide=False)
    t.crack(2, 0.2)
    t.grime(0.2)
    return t


def crossing(seed):
    """Rubber crossing panels: black, a raised diamond tread, worn smooth in the wheel paths."""
    t = F.plain(seed, (40, 40, 42), 0.04, 0.3)
    yy, xx = np.mgrid[0:N, 0:N]
    d = (np.abs(((xx + yy) % 8) - 4) + np.abs(((xx - yy) % 8) - 4))
    m = (d < 3).astype(np.float32)
    t.h += m * 0.15
    t.c *= (1 + m * 0.12)[..., None]
    for y in (0, 31):
        area(t, 0, y, N, y + 1, mul=0.5, dh=-0.3)
    t.grime(0.25, (70, 64, 56))
    return t


# ---------------------------------------------------------------- the el and the bridges

def el_paint(seed, rgb=EL_GREEN, rust=0.6):
    """The el's lead-based green, chalked and peeling, rust coming through at every rivet line."""
    t = F.mat("PaintedMetal004", rgb, 0.8, seed=seed, smooth=0.3)
    t.f0[:] = 40
    peel(t, 1.2, (126, 80, 48))
    weather(t, rust=rust, streak=0.6, grime=0.3)
    return t


def deck(seed):
    """The steel deck's underside: troughs of riveted plate, soot and rust dripping from the joints."""
    t = el_paint(seed, rust=0.8)
    for x in (0, 16, 32, 48):
        area(t, x, 0, x + 2, N, mul=0.7, dh=0.2)
        rivets(t, (x + 4,), step=6)
    t.streaks(0.8, dark=0.6)
    return t


def girder(seed):
    """A plate girder's web: stiffener angles every half block, rivet rows along the flanges."""
    t = el_paint(seed)
    area(t, 0, 0, N, 6, mul=1.12, dh=0.25)
    area(t, 0, N - 6, N, N, mul=0.82, dh=0.25)
    rivets(t, (2,), step=4)
    for y in (3, N - 3):
        for x in range(2, N, 5):
            disc(t, x, y, 0.9, mul=1.15, dh=0.3)
    for x in (14, 46):
        area(t, x, 6, x + 4, N - 6, mul=1.1, dh=0.3)
        area(t, x + 4, 6, x + 5, N - 6, mul=0.6)
        for y in range(10, N - 8, 7):
            disc(t, x + 2, y, 0.8, mul=1.2, dh=0.3)
    return t


def grate(seed):
    """Steel bar grating for the walkways: see-through between the bars, galvanizing worn to rust."""
    t = F.plain(seed, (110, 108, 102), 0.06, 0.35)
    t.f0[:] = 150
    t.alpha[:] = 0
    for x in range(0, N, 5):
        area(t, x, 0, x + 2, N, alpha=255, dh=0.2)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, alpha=255, mul=0.9)
    t.mix(np.clip(P.fbm(t.rng) * 1.4, 0, 1), RUST, 0.45)
    return t


def column(seed):
    """A built-up column: two channels laced together, the lacing's diagonals and rivets."""
    t = el_paint(seed)
    area(t, 0, 0, 5, N, mul=1.15, dh=0.3)
    area(t, N - 5, 0, N, N, mul=0.85, dh=0.3)
    yy = np.arange(N)
    for k in range(-N, N, 16):
        for y in yy:
            for w in (0, 1):
                x = 5 + ((y + k) % 32) * (N - 10) / 32
                xi = int(x) + w
                if 5 <= xi < N - 5:
                    t.c[y, xi] *= 1.1
                    t.h[y, xi] += 0.25
    rivets(t, (2.5, N - 2.5), step=6)
    return t


def concrete(seed, kind="pier"):
    """Board-formed concrete: the boards' grain and seams, water stains, a dark tide band low down."""
    t = F.mat("Concrete031", (146, 142, 134), 0.9, seed=seed, smooth=0.15)
    rng = t.rng
    if kind in ("pier", "base"):
        for y in range(0, N, 10):
            area(t, 0, y, N, y + 1, mul=0.86, dh=-0.1)
        t.streaks(0.9, dark=0.7)
    if kind == "pier":
        yy = np.arange(N, dtype=np.float32)[:, None]
        band = np.clip((yy - 40) / 10, 0, 1) * np.ones((1, N), np.float32)
        t.mix(band * (0.5 + 0.5 * P.fbm(rng)), (70, 72, 62), 0.6)
    if kind == "cap":
        area(t, 0, 0, N, 3, mul=1.1, dh=0.2)
    t.grime(0.15)
    return t


# ---------------------------------------------------------------- the stations

def platform(seed, edge=False):
    """Broom-finished concrete: joints every half block, gum, cracks; at the edge the yellow
    tactile strip and the white line (the track side is the top of the picture)."""
    t = F.mat("Concrete034", (150, 148, 140), 0.6, span=2, seed=seed, smooth=0.18)
    rng = t.rng
    for y in range(N):
        area(t, 0, y, N, y + 1, mul=float(rng.uniform(0.97, 1.03)))
    area(t, 0, 0, 1, N, mul=0.7, dh=-0.2)
    area(t, 31, 0, 32, N, mul=0.8, dh=-0.15)
    for _ in range(int(rng.integers(3, 8))):
        x, y = rng.uniform(2, N - 2, 2)
        disc(t, x, y, rng.uniform(0.6, 1.3), (70 + rng.integers(0, 30),) * 3, h=0.8)
    t.crack(int(rng.integers(0, 3)), 0.2)
    if edge:
        area(t, 0, 0, N, 4, (196, 196, 190), smooth=0.3, dh=0.05)        # the coping's white line
        area(t, 0, 4, N, 22, (204, 166, 40), smooth=0.35)                 # the tactile strip
        for y in range(6, 22, 4):
            for x in range(2, N, 4):
                disc(t, x, y, 1.1, mul=1.12, dh=0.35)
        t.mix(np.clip(P.fbm(rng) * 1.2 - 0.2, 0, 1) * (np.arange(N) < 22)[:, None], (120, 110, 90), 0.4)
    t.grime(0.18)
    return t


def platform_face(seed):
    """The platform's face over the track: concrete, the coping's lip, grime splashed up from the bed."""
    t = concrete(seed, "base")
    area(t, 0, 0, N, 5, (166, 162, 152), dh=0.25)
    area(t, 0, 5, N, 6, mul=0.5)
    yy = np.arange(N, dtype=np.float32)[:, None]
    t.mix(np.clip((yy - 30) / 34, 0, 1) * (0.6 + 0.4 * P.fbm(t.rng)), (66, 56, 46), 0.6)
    return t


def roof(seed):
    """Corrugated steel on the canopy, rusting along the ribs' bottoms."""
    t = F.mat("rusty_corrugated_iron", (120, 116, 108), 1.0, seed=seed, smooth=0.3)
    t.f0[:] = 120
    return t


def boards(seed, rgb, vertical=True, batten=True):
    """Painted board-and-batten: the depot's walls and the canopy's soffit."""
    t = F.mat("weathered_plank_siding", rgb, 0.6, rot=vertical, seed=seed, smooth=0.25)
    if batten:
        for x in range(0, N, 16):
            area(t, x, 0, x + 2, N, mul=1.1, dh=0.3) if vertical else area(t, 0, x, N, x + 2, mul=1.1, dh=0.3)
    peel(t, 0.8, (150, 136, 112))
    t.streaks(0.5, dark=0.8)
    t.grime(0.15)
    return t


def valance(seed):
    """The canopy's dagger-board valance: boards cut to points along their bottoms, the railway's green."""
    t = boards(seed, GREEN, batten=False)
    t.alpha[:] = 255
    yy, xx = np.mgrid[0:N, 0:N]
    point = (N - 18) + np.abs((xx % 8) - 4) * 4.5
    t.alpha[yy > point] = 0
    area(t, 0, 0, N, 6, CREAM, dh=0.2)
    return t


def cast_iron(seed, rgb=GREEN):
    t = F.plain(seed, rgb, 0.05, 0.4)
    t.f0[:] = 60
    peel(t, 0.6, (96, 64, 44))
    weather(t, rust=0.5, streak=0.4, grime=0.25)
    return t


def bench_wood(seed):
    t = F.mat("weathered_planks", (126, 96, 66), 0.8, seed=seed, smooth=0.2)
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 1, mul=0.55, dh=-0.25)
    t.grime(0.2)
    return t


def enamel(seed, rgb=GREEN, inside=(232, 230, 220)):
    """Vitreous enamel: a lamp shade's green outside, its white inside, chips gone black."""
    t = F.plain(seed, rgb, 0.03, 0.75)
    t.f0[:] = 50
    area(t, 0, N // 2, N, N, inside, smooth=0.8)
    for _ in range(8):
        x, y = t.rng.uniform(0, N, 2)
        disc(t, x, y, t.rng.uniform(0.6, 1.6), (26, 24, 22), smooth=0.2)
    return t


def bulb(seed, on):
    t = F.plain(seed, (250, 236, 196) if on else (150, 146, 132), 0.02, 0.85)
    if on:
        t.emit[:] = 0.9
    return t


def window(seed, broken=False):
    """A shelter's window: four panes in a green sash, dust, one cracked or gone."""
    t = F.plain(seed, (96, 112, 112), 0.03, 0.9)
    t.f0[:] = 30
    t.alpha[:] = 120
    m = t.frame(4, GREEN, h=0.95, smooth=0.4)
    t.alpha[m] = 255
    area(t, 30, 0, 34, N, GREEN, alpha=255, dh=0.2)
    area(t, 0, 30, N, 34, GREEN, alpha=255, dh=0.2)
    dust = np.clip(P.fbm(t.rng) * 1.5, 0, 1)
    t.mix(dust, (150, 140, 120), 0.4)
    if broken:
        area(t, 4, 34, 30, 60, alpha=0)
        for _ in range(6):
            x, y = t.rng.uniform(5, 28), t.rng.uniform(36, 58)
            disc(t, x, y, t.rng.uniform(1, 3), (110, 130, 130), alpha=150)
    return t


@lru_cache(maxsize=None)
def timetable_pic():
    """The timetable board on the shelter wall: the railway's header over faded columns of times,
    and a notice pasted over half of it."""
    p = Pic(1, 1, (60, 64, 52))
    p.rect(4, 6, 60, 50, fill=(226, 220, 196))
    p.rect(4, 6, 60, 13, fill=GREEN)
    p.text(32, 7.2, "TIMETABLE", BOLD, 5, CREAM, anchor="ma")
    for r in range(8):
        y = 16 + r * 4
        p.text(7, y, "7:%02d" % (r * 7 + 2), SANS, 2.6, (60, 60, 60))
        p.text(22, y, "UNION · ASHBY", SANS, 2.4, (90, 90, 90))
    p.rect(30, 22, 58, 46, fill=(236, 232, 214))
    p.text(44, 24, "SERVICE|SUSPENDED|UNTIL|FURTHER|NOTICE", BOLD, 4, (150, 30, 24), anchor="ma")
    return p


def timetable(seed):
    t = timetable_pic().tile(0, seed, smooth=0.3, grime=0.25)
    return t


@lru_cache(maxsize=None)
def name_pic(key):
    """A station's name board, four blocks long: cream capitals on the railway's green, a cream
    border and the rivets that hold it to its frame."""
    p = Pic(4, 1, GREEN, 7300 + sum(map(ord, key)))
    W = p.W
    p.rect(0, 12, W, 52, fill=GREEN)
    p.rect(3, 15, W - 3, 49, outline=CREAM, width=1.6)
    text = STOPS[key]
    size = civic.fit(text, BOLD, 26, W - 30)
    p.text(W / 2, 32 - size * 0.62, text, BOLD, size, CREAM, anchor="ma")
    for x in (6, W - 6):
        for y in (17, 47):
            p.ellipse(x - 1, y - 1, x + 1, y + 1, fill=(30, 46, 34))
    return p


def name(key, i):
    t = name_pic(key).tile(i, 7400 + sum(map(ord, key)) * 3 + i, smooth=0.45, grime=0.2)
    t.alpha[:12] = 0
    t.alpha[52:] = 0
    t.f0[:] = 40
    weather(t, rust=0.4, streak=0.5, grime=0.15)
    return t


# ---------------------------------------------------------------- the subway

def tile_wall(seed, rgb=(226, 224, 214), w=16, h=8, dirt=0.35):
    """Glazed subway tile laid running bond: grey grout, a little crazing, soot and seep."""
    t = F.plain(seed, rgb, 0.03, 0.8)
    t.f0[:] = 40
    rng = t.rng
    for r in range(N // h):
        y = r * h
        area(t, 0, y, N, y + 1, (128, 124, 116), smooth=0.2, dh=-0.25)
        off = (r % 2) * (w // 2)
        for x in range(off, N + w, w):
            area(t, x % N, y, x % N + 1, y + h, (128, 124, 116), smooth=0.2, dh=-0.25)
        for x in range(off, N + w, w):
            area(t, x % N + 1, y + 1, x % N + w, y + h, mul=float(rng.uniform(0.96, 1.04)))
    t.mix(np.clip(P.fbm(rng) * 1.6 - 0.3, 0, 1), (96, 88, 70), dirt)
    t.streaks(0.6, dark=0.85)
    return t


def tile_broken(seed, rgb=(226, 224, 214), dirt=0.5, gone=0.22):
    """The same tile after decades without upkeep: tiles fallen off to the mortar bed and the
    brick behind, cracked ones, rust and lime weeping from the joints, black mould low down."""
    t = tile_wall(seed, rgb, dirt=dirt)
    rng = t.rng
    w, h = 16, 8
    for r in range(N // h):
        off = (r % 2) * (w // 2)
        for x in range(off - w, N, w):
            if rng.random() < gone:
                x0, x1 = max(0, x), min(N, x + w + 1)
                if x1 <= x0:
                    continue
                y0, y1 = r * h, r * h + h + 1
                # the mortar bed, grey and pitted, and where it has gone too the old brick
                area(t, x0, y0, x1, y1, (112, 108, 100), smooth=0.4, dh=-0.5)
                if rng.random() < 0.3:
                    bx = int(rng.uniform(x0, max(x0 + 1, x1 - 6)))
                    area(t, bx, y0 + 2, min(x1, bx + 7), y1 - 2, (98, 70, 58), smooth=0.5, dh=-0.6)
                t.f0[y0:y1, x0:x1] = 10
            elif rng.random() < 0.15:
                cx = x + rng.uniform(3, w - 3)
                for k in range(6):
                    yy = r * h + 1 + k
                    xx = int(cx + rng.uniform(-1, 1) + k * 0.4) % N
                    if yy < N:
                        area(t, xx, yy, xx + 1, yy + 1, (90, 86, 78), dh=-0.2)
    t.streaks(1.2, dark=0.7)
    t.mix(np.clip(P.fbm(rng) * 2 - 0.9, 0, 1) * np.linspace(0.2, 1, N)[:, None], (34, 40, 30), 0.6)
    return t


def tile_band(seed):
    """The band along the wall at eye height: a key pattern in maroon and ochre mosaic."""
    t = F.plain(seed, (204, 170, 96), 0.04, 0.7)
    t.f0[:] = 40
    for x in range(0, N, 2):
        for y in range(0, N, 2):
            area(t, x, y, x + 2, y + 2, mul=float(t.rng.uniform(0.9, 1.08)))
    area(t, 0, 0, N, 8, (120, 40, 36))
    area(t, 0, N - 8, N, N, (120, 40, 36))
    for k in range(0, N, 16):
        area(t, k, 16, k + 12, 20, (120, 40, 36))
        area(t, k + 8, 20, k + 12, 44, (120, 40, 36))
        area(t, k, 44, k + 12, 48, (120, 40, 36))
        area(t, k, 24, k + 4, 48, (120, 40, 36))
    for y in range(0, N, 2):
        area(t, 0, y, N, y + 1, mul=0.92, dh=-0.1)
    t.mix(np.clip(P.fbm(t.rng) * 1.4 - 0.2, 0, 1), (80, 70, 54), 0.3)
    return t


@lru_cache(maxsize=None)
def mosaic_pic(key):
    """A stop's name in mosaic, four blocks long: a field of cream tesserae inside a border in the
    stop's colour, the letters in dark tile, a fret at each end."""
    text, border, field = MOSAICS[key]
    p = Pic(4, 1, border, 7600 + sum(map(ord, key)))
    W = p.W
    p.rect(5, 5, W - 5, N - 5, fill=field)
    p.rect(8, 8, W - 8, N - 8, outline=border, width=1.5)
    for x0 in (10, W - 26):
        for k in range(4):
            p.rect(x0 + k * 4, 18 + (k % 2) * 6, x0 + k * 4 + 3, 40 - (k % 2) * 6, fill=border)
    size = civic.fit(text, SERIF, 22 if "|" in text else 30, W - 80)
    lines = text.split("|")
    y = N / 2 - size * 1.15 * len(lines) / 2 + 1
    p.text(W / 2, y, text, SERIF, size, (40, 34, 30), anchor="ma")
    return p


def mosaic(key, i):
    t = mosaic_pic(key).tile(i, 7700 + sum(map(ord, key)) * 3 + i, smooth=0.6, grime=0.15)
    # the tesserae: a grout grid over the picture
    for k in range(0, N, 2):
        area(t, k, 0, k + 1, N, mul=0.9, dh=-0.1)
        area(t, 0, k, N, k + 1, mul=0.9, dh=-0.1)
    t.f0[:] = 40
    t.mix(np.clip(P.fbm(t.rng) * 1.4 - 0.3, 0, 1), (90, 80, 62), 0.3)
    return t


def tunnel(seed):
    """The tunnel's concrete: soot thick from a century of trains, damp seeping white from the joints."""
    t = F.mat("Concrete042C", (96, 94, 88), 0.9, seed=seed, smooth=0.15)
    rng = t.rng
    t.grime(0.5, (26, 24, 22))
    for _ in range(3):
        x = rng.uniform(0, N)
        m = np.zeros((N, N), np.float32)
        m[:, int(x):int(x) + 2] = 1
        t.mix(P.blur(m, 2) * np.clip(P.fbm(rng) + 0.5, 0, 1), (176, 172, 158), 0.35)
    return t


def track_slab(seed):
    """The track slab between the walls: concrete black with brake dust, oil and grease in the middle."""
    t = F.mat("Concrete042C", (70, 66, 60), 0.9, seed=seed, smooth=0.2)
    t.grime(0.6, (24, 20, 18))
    F.stain(t, N / 2, N / 2, 12, col=(20, 18, 16), amount=0.3, tide=False)
    t.speckle(0.03, (110, 70, 40), 0.4)
    return t


def walkway(seed):
    t = F.mat("Concrete034", (110, 108, 100), 0.8, seed=seed, smooth=0.2)
    area(t, 0, 0, N, 6, (180, 150, 40), smooth=0.3)
    t.grime(0.45, (30, 28, 24))
    return t


def nosing(seed):
    t = F.plain(seed, (206, 170, 44), 0.06, 0.4)
    for x in range(0, N, 4):
        area(t, x, 0, x + 2, N, mul=0.88, dh=-0.1)
    t.grime(0.3)
    return t


def diamond(seed):
    t = F.mat("DiamondPlate006C", (120, 118, 112), 1.0, seed=seed, smooth=0.4)
    t.f0[:] = 160
    t.mix(np.clip(P.fbm(t.rng) * 1.3 - 0.1, 0, 1), RUST, 0.4)
    return t


def globe(seed, on):
    """The entrance globe: green glass, the lamp inside it burning or long dead."""
    t = F.plain(seed, (90, 170, 110) if on else (56, 96, 70), 0.04, 0.85)
    t.f0[:] = 40
    if on:
        yy, xx = np.mgrid[0:N, 0:N]
        r = np.hypot(xx - N / 2, yy - N / 2) / (N / 2)
        t.c += (np.clip(1 - r, 0, 1) * 90)[..., None]
        t.emit[:] = 0.8
    return t


@lru_cache(maxsize=None)
def sub_sign_pic():
    """The SUBWAY plate on the railing: white letters on the iron's green, two blocks long."""
    p = Pic(2, 1, SUB_GREEN)
    p.rect(0, 0, p.W, N, fill=(0, 0, 0, 0))
    p.rect(2, 18, p.W - 2, 46, fill=SUB_GREEN)
    p.rect(4, 20, p.W - 4, 44, outline=(232, 232, 224), width=1.2)
    size = civic.fit("SUBWAY", BLACK, 20, p.W - 20)
    p.text(p.W / 2, 32 - size * 0.62, "SUBWAY", BLACK, size, (236, 236, 228), anchor="ma")
    return p


def sub_sign(i):
    t = sub_sign_pic().tile(i, 7820 + i, smooth=0.5, grime=0.2)
    t.f0[:] = 50
    return t


def tube(seed, on):
    t = F.plain(seed, (240, 244, 236) if on else (170, 172, 166), 0.02, 0.8)
    if on:
        t.emit[:] = 0.95
    return t


def cage(seed):
    """A tunnel lamp's guard: heavy wire, black with soot."""
    t = F.plain(seed, (34, 32, 30), 0.05, 0.3)
    t.alpha[:] = 0
    for k in range(0, N, 8):
        area(t, k, 0, k + 2, N, alpha=255)
        area(t, 0, k, N, k + 2, alpha=255)
    return t


# ---------------------------------------------------------------- by the line

def galv(seed):
    t = F.plain(seed, (156, 160, 160), 0.08, 0.4)
    t.f0[:] = 180
    t.speckle(0.2, (190, 194, 192), 0.4)
    t.grime(0.2)
    return t


def mast_base(seed):
    """The mast's foot: black and white diagonal stripes."""
    t = galv(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    m = ((xx + yy) // 16) % 2 == 0
    t.c[m] = (30, 30, 30)
    t.c[~m] = (226, 226, 220)
    weather(t, rust=0.3, streak=0.4, grime=0.3)
    return t


@lru_cache(maxsize=None)
def buck_pic():
    """One crossbuck: two white boards crossed, RAILROAD and CROSSING down them in black."""
    from PIL import Image
    S = 4 * N
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    board = Image.new("RGBA", (int(S * 1.32), int(S * 0.2)), (232, 232, 226, 255))
    from PIL import ImageDraw, ImageFont
    d = ImageDraw.Draw(board)
    d.rectangle([0, 0, board.width - 1, board.height - 1], outline=(20, 20, 20, 255), width=6)
    return im, board


def buck(seed):
    """The crossbuck's face, the picture's top up: the two boards and their lettering."""
    from PIL import Image, ImageDraw, ImageFont
    S = 4 * N
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    font = ImageFont.truetype(BOLD, int(S * 0.1))
    for word, ang in (("RAILROAD", 38), ("CROSSING", -38)):
        board = Image.new("RGBA", (int(S * 1.3), int(S * 0.19)), (234, 234, 228, 255))
        d = ImageDraw.Draw(board)
        d.rectangle([0, 0, board.width - 1, board.height - 1], outline=(24, 24, 24, 255), width=5)
        d.text((board.width / 2, board.height / 2), word, font=font, fill=(18, 18, 18, 255), anchor="mm")
        board = board.rotate(ang, expand=True, resample=Image.BICUBIC)
        im.alpha_composite(board, (int(S / 2 - board.width / 2), int(S / 2 - board.height / 2)))
    a = np.asarray(im.resize((N, N), Image.BOX), np.float32)
    t = F.plain(seed, (128, 128, 128), 0, 0.5)
    t.c[:] = a[..., :3]
    t.alpha[:] = np.where(a[..., 3] > 100, 255, 0)
    t.grime(0.2)
    return t


def lights_face(seed):
    """The crossing lights: two red lenses in black hoods on their backboard, dark now."""
    t = F.plain(seed, (24, 24, 24), 0.03, 0.4)
    for cx in (14, 50):
        disc(t, cx, 32, 12, (20, 20, 20), dh=0.3)
        disc(t, cx, 32, 9, (122, 22, 18), smooth=0.85, dh=0.4)
        disc(t, cx - 3, 29, 2.5, (176, 70, 60), smooth=0.9)
    area(t, 26, 28, 38, 36, (150, 150, 146))
    return t


def creosote(seed):
    """A creosoted pole: dark brown, split along its grain, grey where the weather got it."""
    t = F.mat("Bark008", (70, 54, 40), 0.7, rot=True, seed=seed, smooth=0.1)
    for _ in range(5):
        x = t.rng.uniform(0, N)
        area(t, x, 0, x + 1, N, mul=0.55, dh=-0.3)
    t.mix(np.clip(P.fbm(t.rng) - 0.2, 0, 1), (118, 112, 100), 0.4)
    return t


def insulator(seed):
    t = F.plain(seed, (96, 160, 150), 0.05, 0.9)
    t.f0[:] = 40
    t.alpha[:] = 210
    return t


# ---------------------------------------------------------------- registering

def textures():
    T = {}
    T["rw_ballast"] = lambda: ballast(7001)
    T["rw_ballast_side"] = lambda: ballast(7002, dark=0.4)
    T["rw_subballast"] = lambda: subballast(7003)
    T["rw_cinder"] = lambda: cinder(7004)
    T["rw_street_bed"] = lambda: street_bed(7005)
    T["rw_crossing"] = lambda: crossing(7006)
    T["rw_deck"] = lambda: deck(7010)
    T["rw_girder"] = lambda: girder(7011)
    T["rw_el_paint"] = lambda: el_paint(7012)
    T["rw_grate"] = lambda: grate(7013)
    T["rw_column"] = lambda: column(7014)
    T["rw_pier"] = lambda: concrete(7015, "pier")
    T["rw_pier_cap"] = lambda: concrete(7016, "cap")
    T["rw_concrete"] = lambda: concrete(7017, "base")
    T["rw_platform"] = lambda: platform(7020)
    T["rw_platform_edge"] = lambda: platform(7021, edge=True)
    T["rw_platform_face"] = lambda: platform_face(7022)
    T["rw_roof"] = lambda: roof(7023)
    T["rw_soffit"] = lambda: boards(7024, CREAM, vertical=False)
    T["rw_wall_green"] = lambda: boards(7025, GREEN)
    T["rw_wall_cream"] = lambda: boards(7026, CREAM)
    T["rw_valance"] = lambda: valance(7027)
    T["rw_iron"] = lambda: cast_iron(7028)
    T["rw_bench_wood"] = lambda: bench_wood(7029)
    T["rw_enamel"] = lambda: enamel(7030)
    T["rw_bulb"] = lambda: bulb(7031, False)
    T["rw_bulb_on"] = lambda: bulb(7032, True)
    T["rw_window"] = lambda: window(7033)
    T["rw_window_broken"] = lambda: window(7034, broken=True)
    T["rw_timetable"] = lambda: timetable(7035)
    for k in STOPS:
        for i in range(4):
            T[f"rw_name_{k}_{i}"] = lambda k=k, i=i: name(k, i)
    T["rw_tile"] = lambda: tile_wall(7040)
    T["rw_tile_dado"] = lambda: tile_wall(7041, (176, 162, 118), dirt=0.5)
    T["rw_tile_band"] = lambda: tile_band(7042)
    T["rw_tile_broken"] = lambda: tile_broken(7060)
    T["rw_tile_dado_broken"] = lambda: tile_broken(7061, (176, 162, 118), dirt=0.6, gone=0.3)
    for k in MOSAICS:
        for i in range(4):
            T[f"rw_mosaic_{k}_{i}"] = lambda k=k, i=i: mosaic(k, i)
    T["rw_tunnel"] = lambda: tunnel(7043)
    T["rw_track_slab"] = lambda: track_slab(7044)
    T["rw_walkway"] = lambda: walkway(7045)
    T["rw_nosing"] = lambda: nosing(7046)
    T["rw_diamond"] = lambda: diamond(7047)
    T["rw_sub_iron"] = lambda: cast_iron(7048, SUB_GREEN)
    T["rw_globe"] = lambda: globe(7049, False)
    T["rw_globe_on"] = lambda: globe(7050, True)
    for i in range(2):
        T[f"rw_subway_sign_{i}"] = lambda i=i: sub_sign(i)
    T["rw_tube"] = lambda: tube(7051, False)
    T["rw_tube_on"] = lambda: tube(7052, True)
    T["rw_cage"] = lambda: cage(7053)
    T["rw_galv"] = lambda: galv(7054)
    T["rw_mast_base"] = lambda: mast_base(7055)
    T["rw_buck"] = lambda: buck(7056)
    T["rw_lights"] = lambda: lights_face(7057)
    T["rw_creosote"] = lambda: creosote(7058)
    T["rw_insulator"] = lambda: insulator(7059)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, **extra):
        if boxes and rotate == "axis":
            boxes = P.along_z(boxes)  # RULE: axis models run along z as drawn
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
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

    paint, iron, galvd = "rw_el_paint", "rw_iron", "rw_galv"

    # ---- the bed
    bal = {"up": "rw_ballast", "side": "rw_ballast_side", "down": "rw_subballast"}
    add("rw_ballast", None, "Ballast", "GRAVEL", rotate=None, tex=bal)
    for h in (4, 8, 12):
        add(f"rw_ballast_{h}", [[0, 0, 0, 16, h, 16, bal]], "Ballast", "GRAVEL", rotate=None)
    add("rw_subballast", None, "Subballast", "GRAVEL", rotate=None, tex={"all": "rw_subballast"})
    add("rw_cinder", None, "Cinders", "GRAVEL", rotate=None, tex={"up": "rw_cinder", "side": "rw_subballast", "down": "rw_subballast"})
    add("rw_street_bed", None, "Street Track", "STONE", rotate=None,
        tex={"up": "rw_street_bed", "side": "rw_concrete", "down": "rw_concrete"})
    xing = {"up": "rw_crossing", "side": "rw_concrete", "down": "rw_concrete"}
    add("rw_crossing", None, "Crossing Panel", "STONE", rotate=None, tex=xing)
    for h in (4, 8, 12):
        add(f"rw_crossing_{h}", [[0, 0, 0, 16, h, 16, xing]], "Crossing Panel", "STONE", rotate=None)

    # ---- the el and the bridges
    add("rw_deck", None, "Steel Deck", "METAL", rotate=None, tex={"up": "rw_el_paint", "side": "rw_girder", "down": "rw_deck"})
    add("rw_girder", None, "Plate Girder", "METAL", rotate=None, tex={"up": "rw_el_paint", "side": "rw_girder", "down": "rw_el_paint"})
    add("rw_grate", [[0, 13, 0, 16, 16, 16, "rw_grate"], [0, 10, 0, 16, 13, 2, paint], [0, 10, 14, 16, 13, 16, paint]],
        "Walkway Grating", "METAL", rotate=None, render="cutout")
    add("rw_guard", [[0, 14, 0, 16, 15.5, 1.6, paint], [0, 7, 0.3, 16, 8.2, 1.3, paint], [1, 0, 0, 2.6, 14, 1.6, paint],
                     [13.4, 0, 0, 15, 14, 1.6, paint]], "Railing", "METAL", render="cutout")
    add("rw_crossbeam", [[0, 0, 4, 16, 16, 12, {"north": "rw_girder", "south": "rw_girder", "side": paint, "up": paint, "down": paint}],
                         [0, 0, 2, 16, 2, 14, paint], [0, 14, 2, 16, 16, 14, paint]], "Cross Girder", "METAL", rotate="axis")
    col = [[4, 0, 4, 12, 16, 12, {"north": "rw_column", "south": "rw_column", "side": "rw_column", "up": paint, "down": paint}],
           [3, 0, 4, 4, 16, 12, paint], [12, 0, 4, 13, 16, 12, paint]]
    add("rw_column", col, "El Column", "METAL", rotate=None)
    add("rw_column_base", col + [[1, 0, 1, 15, 2, 15, "rw_concrete"], [2.5, 2, 2.5, 13.5, 3, 13.5, paint]], "El Column", "METAL", rotate=None)
    add("rw_column_cap", col + [[1, 12, 3, 15, 16, 13, paint], [2, 9, 4, 14, 12, 12, paint]], "El Column", "METAL", rotate=None)
    add("rw_pier", None, "Concrete Pier", "STONE", rotate=None, tex={"all": "rw_pier"})
    add("rw_pier_cap", None, "Pier Cap", "STONE", rotate=None, tex={"up": "rw_pier_cap", "side": "rw_pier_cap", "down": "rw_pier"})

    # ---- the stations
    add("rw_platform", None, "Platform", "STONE", rotate=None, tex={"up": "rw_platform", "side": "rw_platform_face", "down": "rw_concrete"})
    add("rw_platform_edge", cube("rw_platform_face", "rw_platform_face", up="rw_platform_edge", down="rw_concrete"), "Platform Edge", "STONE")
    add("rw_platform_half", [[0, 0, 0, 16, 8, 16, {"up": "rw_platform", "side": "rw_platform_face", "down": "rw_concrete"}]],
        "Platform Step", "STONE", rotate=None)
    add("rw_platform_base", None, "Concrete", "STONE", rotate=None, tex={"all": "rw_concrete"})
    roof_box = [[0, 12, 0, 16, 14, 16, {"up": "rw_roof", "side": "rw_soffit", "down": "rw_soffit"}],
                [0, 10, 0, 1.5, 12, 16, "rw_soffit"], [14.5, 10, 0, 16, 12, 16, "rw_soffit"]]
    add("rw_canopy", roof_box[:1], "Canopy", "METAL", rotate=None)
    add("rw_canopy_fascia", roof_box[:1] + [[0, 4, 0, 16, 14, 1.2, {"north": "rw_valance", "south": "rw_valance", "side": "rw_wall_green",
                                                                      "up": "rw_wall_green", "down": "rw_wall_green"}]],
        "Canopy", "METAL", render="cutout")
    add("rw_post", [[6, 0, 6, 10, 16, 10, iron], [5.5, 0, 5.5, 10.5, 2, 10.5, iron], [5.5, 14, 5.5, 10.5, 16, 10.5, iron]],
        "Post", "METAL", rotate=None)
    bench = [[0.5, 6, 2, 15.5, 7, 4.6, "rw_bench_wood"], [0.5, 6, 5, 15.5, 7, 7.6, "rw_bench_wood"], [0.5, 6, 8, 15.5, 7, 10.6, "rw_bench_wood"],
             [0.5, 9, 11, 15.5, 11, 12, "rw_bench_wood"], [0.5, 12, 11.4, 15.5, 14, 12.4, "rw_bench_wood"],
             [1.5, 0, 2, 2.5, 6, 11, iron], [13.5, 0, 2, 14.5, 6, 11, iron], [1.5, 6, 10.6, 2.5, 14.5, 12, iron],
             [13.5, 6, 10.6, 14.5, 14.5, 12, iron], [1.5, 7, 2, 2.5, 9, 3, iron], [13.5, 7, 2, 14.5, 9, 3, iron]]
    add("rw_bench", bench, "Bench", "WOOD")
    add("rw_lamp_post", [[7, 0, 7, 9, 16, 9, iron], [6, 0, 6, 10, 3, 10, iron]], "Lamp Post", "METAL", rotate=None)
    shade = {"up": "rw_enamel", "side": "rw_enamel", "down": "rw_enamel"}
    head = [[7, 0, 7, 9, 6, 9, iron], [7.2, 5, 1, 8.8, 6.6, 9, iron], [4.5, 3, -1, 11.5, 5, 6, shade], [6.5, 2, 1, 9.5, 3, 4, "rw_bulb"]]
    add("rw_lamp_head", head, "Platform Lamp", "METAL", flicker={"light": 13, "on": {"rw_bulb": "rw_bulb_on"}})
    hang = [[7.5, 9, 7.5, 8.5, 16, 8.5, iron], [4, 6, 4, 12, 9, 12, shade], [6.5, 5, 6.5, 9.5, 6, 9.5, "rw_bulb"]]
    add("rw_lamp_hang", hang, "Hanging Lamp", "METAL", rotate=None, decor=True, flicker={"light": 13, "on": {"rw_bulb": "rw_bulb_on"}})
    add("rw_shelter_wall", cube("rw_wall_cream", "rw_wall_green", up="rw_wall_green", down="rw_wall_green"), "Shelter Wall", "WOOD")
    add("rw_shelter_window", [[0, 0, 7, 16, 16, 9, {"north": "rw_window", "south": "rw_window", "side": "rw_wall_green",
                                                      "up": "rw_wall_green", "down": "rw_wall_green"}],
                              [0, 0, 6, 16, 1.5, 10, "rw_wall_green"]], "Shelter Window", "GLASS", render="translucent")
    add("rw_timetable", cube("rw_timetable", "rw_wall_green", up="rw_wall_green", down="rw_wall_green"), "Timetable", "WOOD")
    add("rw_fence", [[1, 0, 0, 2.5, 13, 1.5, iron], [13.5, 0, 0, 15, 13, 1.5, iron], [0, 11, 0.2, 16, 12.5, 1.3, iron],
                     [0, 5, 0.2, 16, 6.5, 1.3, iron], [0, 12.5, 0.4, 16, 13.2, 1.1, iron]], "Platform Fence", "METAL", render="cutout")
    for k in STOPS:
        for i in range(4):
            n = f"rw_name_{k}_{i}"
            boxes = [[0, 3, 7, 16, 13, 9, {"north": n, "south": "rw_wall_green", "side": "rw_wall_green", "up": "rw_wall_green",
                                            "down": "rw_wall_green"}]]
            add(n, boxes, "Station Sign", "WOOD")

    # ---- the subway
    add("rw_tile", None, "Subway Tile", "STONE", rotate=None, tex={"all": "rw_tile"})
    add("rw_tile_dado", None, "Subway Tile", "STONE", rotate=None, tex={"all": "rw_tile_dado"})
    add("rw_tile_band", None, "Subway Tile", "STONE", rotate=None, tex={"all": "rw_tile_band"})
    add("rw_tile_broken", None, "Subway Tile", "STONE", rotate=None, tex={"all": "rw_tile_broken"})
    add("rw_tile_dado_broken", None, "Subway Tile", "STONE", rotate=None, tex={"all": "rw_tile_dado_broken"})
    for k in MOSAICS:
        for i in range(4):
            n = f"rw_mosaic_{k}_{i}"
            add(n, cube(n, "rw_tile"), "Name Mosaic", "STONE")
    add("rw_tunnel", None, "Tunnel Concrete", "STONE", rotate=None, tex={"all": "rw_tunnel"})
    add("rw_tunnel_floor", None, "Tunnel Walkway", "STONE", rotate=None, tex={"up": "rw_walkway", "side": "rw_tunnel", "down": "rw_tunnel"})
    add("rw_track_slab", None, "Track Slab", "STONE", rotate=None, tex={"up": "rw_track_slab", "side": "rw_tunnel", "down": "rw_tunnel"})
    tlamp = [[5, 6, 14, 11, 12, 16, "rw_el_paint"], [6, 6.5, 9, 10, 11.5, 14, "rw_cage"], [6.8, 7.3, 10, 9.2, 10.7, 13, "rw_bulb"]]
    add("rw_tunnel_lamp", tlamp, "Tunnel Lamp", "METAL", decor=True, render="cutout",
        flicker={"light": 10, "on": {"rw_bulb": "rw_bulb_on"}})
    fixture = [[5, 13, 0, 11, 16, 16, {"up": paint, "side": paint, "down": "rw_tube", "north": paint, "south": paint}],
               [5.5, 12, 0.5, 10.5, 13, 15.5, "rw_tube"], [7.6, 16, 2, 8.4, 16, 3, paint]]
    add("rw_subway_lamp", fixture, "Strip Light", "METAL", rotate="axis", decor=True, flicker={"light": 14, "on": {"rw_tube": "rw_tube_on"}})
    step = {"up": "rw_platform", "side": "rw_concrete", "down": "rw_concrete", "north": "rw_concrete"}
    add("rw_stair", [[0, 0, 0, 16, 8, 16, step], [0, 8, 0, 16, 16, 8, step],
                     [0, 7.6, 15, 16, 8.05, 16, "rw_nosing"], [0, 15.6, 7, 16, 16.05, 8, "rw_nosing"]], "Stairs", "STONE")
    sstep = {"up": "rw_diamond", "side": paint, "down": paint, "north": paint}
    add("rw_stair_steel", [[0, 0, 0, 16, 8, 16, sstep], [0, 8, 0, 16, 16, 8, sstep]], "Steel Stairs", "METAL")
    sub_iron = "rw_sub_iron"
    rail = [[0, 14, 0, 16, 15.5, 1.6, sub_iron], [0, 2, 0.3, 16, 3, 1.3, sub_iron], [1, 0, 0, 2.6, 14, 1.6, sub_iron],
            [13.4, 0, 0, 15, 14, 1.6, sub_iron]] + [[x, 3, 0.5, x + 0.8, 14, 1.1, sub_iron] for x in (4, 6.5, 9, 11.5)]
    add("rw_subway_rail", rail, "Subway Railing", "METAL", render="cutout")
    add("rw_globe_post", [[6.5, 0, 6.5, 9.5, 16, 9.5, sub_iron], [5, 0, 5, 11, 4, 11, sub_iron], [5.5, 13, 5.5, 10.5, 16, 10.5, sub_iron]],
        "Globe Lamp", "METAL", rotate=None)
    add("rw_globe", [[4, 0, 4, 12, 9, 12, {"up": "rw_globe", "side": "rw_globe", "down": sub_iron}], [7, 9, 7, 9, 11, 9, sub_iron]],
        "Globe Lamp", "GLASS", rotate=None, render="translucent", flicker={"light": 12, "on": {"rw_globe": "rw_globe_on"}})
    for i in range(2):
        n = f"rw_subway_sign_{i}"
        add(n, rail[:4] + [[0, 4, -0.2, 16, 12, 0, {"north": n, "south": n, "side": sub_iron, "up": sub_iron, "down": sub_iron}]],
            "Subway Sign", "METAL", render="cutout")

    # ---- by the line
    add("rw_xing_post", [[6.5, 0, 6.5, 9.5, 16, 9.5, {"side": "rw_mast_base", "up": galvd, "down": galvd}]], "Crossing Signal", "METAL",
        rotate=None)
    add("rw_xing_lights", [[6.5, 0, 6.5, 9.5, 16, 9.5, galvd], [-4, 4, 5, 20, 12, 6.5, {"north": "rw_lights", "side": "rw_lights",
                                                                                      "up": "rw_lights", "down": "rw_lights",
                                                                                      "south": galvd}],
                           [-3, 11, 3, 3, 12, 5, "rw_lights"], [13, 11, 3, 19, 12, 5, "rw_lights"]], "Crossing Signal", "METAL")
    add("rw_xing_buck", [[6.5, 0, 6.5, 9.5, 12, 9.5, galvd], [-6, -2, 5.6, 22, 18, 6, {"north": "rw_buck", "south": "rw_buck",
                                                                                       "side": galvd, "up": galvd, "down": galvd}],
                         [6, 12, 6, 10, 16, 10, "rw_lights"]], "Crossbuck", "METAL", render="cutout")
    add("rw_pole", [[5, 0, 5, 11, 16, 11, "rw_creosote"]], "Telegraph Pole", "WOOD", rotate=None)
    arm = [[5, 0, 5, 11, 4, 11, "rw_creosote"], [-8, 4, 6.5, 24, 7, 9.5, "rw_creosote"]]
    for x in (-6, -1, 17, 22):
        arm += [[x, 7, 7, x + 2, 9.5, 9, "rw_insulator"]]
    add("rw_pole_arm", arm, "Telegraph Pole", "WOOD", rotate="axis")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("rw_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} railway blocks in {path}")


if __name__ == "__main__":
    main()
