"""Mercy General Hospital: the hs_ kit for the island's general hospital (world/county/Hospital.java
and its parts).

No photos of it in the library yet (city/hospital-* were queued): this follows what the big American
city hospitals of the late sixties and the seventies are known for, as they stood in the eighties.
Outside, a brown brick podium with bronze ribbon windows and a white precast ward tower over it,
deep-set windows in a grid, MERCY GENERAL HOSPITAL in red letters along its top and the red cross
lit over the drive, EMERGENCY on the canopy over the ambulance bay, the helipad on the roof.
Inside, speckled sheet vinyl and acoustic tile, troffers, the walls in pale mint over a teal dado
with a steel crash rail, glazed white tile in the theatres and the morgue. The beds with their
rails and curtains on ceiling tracks, the monitors, the carts, the nurses' stations, the theatre
lamps, the x-ray rooms, the morgue's wall of doors. The last days left their own: the quarantine
tape across the wards, the polythene over the doors, the body bags on gurneys down the corridors,
DEAD INSIDE sprayed on the doors they chained.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- floors and ceilings: hs_lino, hs_lino_b, hs_lino_blood, hs_lino_green, hs_slab, hs_slab_b,
  hs_slab_green (vinyl over, tile under), hs_slab_lamp, hs_slab_lamp_on (a troffer in the tile
  under), hs_ceiling;
- walls: hs_wall, hs_wall_b, hs_wall_lo, hs_wall_lo_b (the dado and the crash rail), hs_tile,
  hs_tile_b, hs_pad (the quiet rooms);
- outside: hs_precast, hs_precast_b, hs_spandrel, hs_brick, hs_brick_b, hs_window_lo, hs_window_hi
  (+_broken: the tower's, two tall, facing out), hs_ribbon(_broken) (the podium's), hs_deck, hs_roof,
  hs_roof_lamp(_on) (the roof's deck over the top floor's ceiling), hs_pad_h_0..48 (the helipad's
  H, seven by seven, row 0 the north);
- the ward: hs_bed_head, hs_bed_foot (+_blood, hs_bed_foot_body), hs_nightstand, hs_overbed,
  hs_curtain, hs_curtain_top, hs_curtain_open, hs_curtain_open_top, hs_monitor(_on), hs_iv;
- the corridors: hs_gurney_a, hs_gurney_b (+ hs_gurney_bag_a/b), hs_wheelchair(_tipped),
  hs_cart_crash, hs_cart_med, hs_cart_linen, hs_cart_meals, hs_station, hs_station_end,
  hs_chairs, hs_chairs_b, hs_chairs_tipped, hs_oxygen, hs_sharps, hs_biohazard, hs_tray;
- the theatres: hs_or_table, hs_or_light(_on), hs_anesthesia, hs_mayo, hs_scrub_sink, hs_sink;
- radiology: hs_xray_table, hs_xray_arm, hs_lightbox(_on); the lab: hs_lab_bench, hs_bloodfridge(_on);
- the morgue: hs_morgue_lo, hs_morgue_hi, hs_morgue_open, hs_autopsy;
- the nursery: hs_bassinet, hs_incubator; staff: hs_locker, hs_locker_top, hs_cabinet, hs_cabinet_lo;
- signs: hs_sgn_KEY_0..1 (the departments, two by one), hs_name_KEY_i(_on) (the big names:
  mercy twenty by two, er eight by one), hs_cross_0..8(_on) (three by three), hs_h_0..3(_on);
- the last days: hs_tape, hs_plastic, hs_notice, hs_spray_KEY_0..2 (three by one).

    python3 tools/city/hospital.py        rewrites the hs_ entries in blocks.json

photo_textures.py draws the textures (hospital.register()).
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import transit  # noqa: E402
from furniture import area, disc, blood, drips, stain, text_mask  # noqa: E402
from school import Pic  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
SERIF = "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"
SPRAY = "/usr/share/fonts/truetype/freefont/FreeSansBoldOblique.ttf"
MINT = (190, 214, 196)
TEAL = (64, 118, 112)
BRONZE = (70, 52, 38)
RED = (206, 30, 36)
NAVY = (28, 44, 84)

# the departments' signs, two by one on the wall: text, arrow (None, "l", "r"), colours (field, ink)
SIGNS = {
    "emergency": ("EMERGENCY", None, (180, 26, 30), (255, 255, 255)),
    "admitting": ("ADMITTING", None, NAVY, (236, 236, 228)),
    "radiology": ("RADIOLOGY", None, NAVY, (236, 236, 228)),
    "surgery": ("SURGERY", None, NAVY, (236, 236, 228)),
    "icu": ("INTENSIVE CARE", None, NAVY, (236, 236, 228)),
    "lab": ("LABORATORY", None, NAVY, (236, 236, 228)),
    "pharmacy": ("PHARMACY", None, NAVY, (236, 236, 228)),
    "chapel": ("CHAPEL", None, NAVY, (236, 236, 228)),
    "cafeteria": ("CAFETERIA", None, NAVY, (236, 236, 228)),
    "morgue": ("MORGUE", None, (60, 60, 64), (236, 236, 228)),
    "maternity": ("MATERNITY", None, NAVY, (236, 236, 228)),
    "pediatrics": ("PEDIATRICS", None, NAVY, (236, 236, 228)),
    "wards": ("WARDS", None, NAVY, (236, 236, 228)),
    "isolation": ("ISOLATION", None, (200, 150, 20), (20, 20, 20)),
    "records": ("MEDICAL RECORDS", None, NAVY, (236, 236, 228)),
    "admin": ("ADMINISTRATION", None, NAVY, (236, 236, 228)),
    "lifts": ("ELEVATORS", "r", NAVY, (236, 236, 228)),
    "stairs": ("STAIRS", "l", NAVY, (236, 236, 228)),
    "staff": ("STAFF ONLY", None, (120, 24, 28), (255, 255, 255)),
    "visitors": ("VISITORS", None, NAVY, (236, 236, 228)),
    "psych": ("BEHAVIORAL HEALTH", None, NAVY, (236, 236, 228)),
    "ward3": ("WARD 3 MEDICAL", None, NAVY, (236, 236, 228)),
    "ward5": ("WARD 5 SURGICAL", None, NAVY, (236, 236, 228)),
    "cardiac": ("CARDIAC CARE", None, NAVY, (236, 236, 228)),
    "oncology": ("ONCOLOGY", None, NAVY, (236, 236, 228)),
}
# the big names: text, font, cols, rows, lit colour
NAMES = {
    "mercy": ("MERCY GENERAL HOSPITAL", BLACK, 20, 2, (255, 44, 44)),
    "er": ("EMERGENCY", BLACK, 8, 1, (255, 40, 40)),
    "front": ("MERCY GENERAL", BLACK, 10, 1, (255, 248, 236)),
}
# what's sprayed on the doors they chained: text, cols (one row)
SPRAYS = {
    "dead": ("DEAD INSIDE", 3),
    "dont": ("DON'T OPEN", 3),
    "nohelp": ("NO HELP COMING", 3),
}


# ---------------------------------------------------------------- floors, ceilings, walls

def lino(seed, rgb=(212, 204, 186), worn=False, bloody=False):
    """Sheet vinyl: a speckled field in one piece, the heat-welded seam across it, buffed dull
    down the middle of the corridor and scuffed black by the trolley wheels."""
    t = F.plain(seed, rgb, 0.025, 0.45)
    rng = t.rng
    dark = tuple(v * 0.55 for v in rgb)
    light = tuple(min(255, v * 1.12) for v in rgb)
    t.speckle(0.12, dark, 0.45)
    t.speckle(0.06, light, 0.4)
    t.speckle(0.02, (96, 120, 140), 0.4)
    if rng.random() < 0.6:
        y = int(rng.integers(8, N - 8))
        area(t, 0, y, N, y + 1, mul=0.8, dh=-0.04)
    for _ in range(6 if worn else 2):
        x, y = rng.integers(0, N, 2)
        area(t, x, y, x + rng.integers(4, 12), y + 1, mul=0.7)  # the trolleys' scuffs
    t.grime(0.32 if worn else 0.1)
    if worn:
        t.crack(1, 0.15, col=(80, 74, 64))
        for _ in range(2):
            stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(6, 12), (110, 96, 64), 0.35)
    if bloody:
        blood(t, 30, 34, 14, 0.9)
        for k in range(5):
            # the smear where something was dragged toward the door
            area(t, 26 + k * 7, 30 + k, 33 + k * 7, 34 + k, col=(84, 20, 16))
    return t


def ceiling(seed, lamp=None):
    """Two-by-four acoustic tile in its white T-bar grid; with lamp, a troffer's prismatic lens
    across the block, lit or dead."""
    t = F.plain(seed, (224, 222, 212), 0.02, 0.15)
    t.speckle(0.2, (178, 174, 162), 0.4, -0.05)
    for _ in range(40):
        x, y = t.rng.integers(0, N, 2)
        area(t, x, y, x + t.rng.integers(2, 5), y + 1, col=(170, 166, 154), dh=-0.08)
    for k in (0, 31):
        area(t, 0, k, N, k + 2, col=(238, 238, 234), h=0.95, smooth=0.5)
    area(t, 0, 0, 2, N, col=(238, 238, 234), h=0.95, smooth=0.5)
    if lamp is not None:
        area(t, 6, 4, 58, 60, col=(200, 202, 200), h=0.9, smooth=0.6, f0=150)
        on = lamp == "on"
        area(t, 9, 7, 55, 57, col=(255, 252, 238) if on else (176, 178, 176), h=0.7, smooth=0.8)
        for y in range(8, 57, 3):
            area(t, 9, y, 55, y + 1, mul=0.94)
        for x in range(10, 55, 3):
            area(t, x, 7, x + 1, 57, mul=0.95)
        if on:
            m = np.zeros((N, N), np.float32)
            m[7:57, 9:55] = 1.0
            t.emit[:] = np.maximum(t.emit, m)
        else:
            area(t, 20, 30, 30, 44, mul=0.8)  # the dead flies in the lens
    t.grime(0.08)
    return t


def wall(seed, part="up", stained=False):
    """Painted concrete block: pale mint above the dado, the teal dado below with the vinyl crash
    rail along its top; part "lo" is the floor-level block (the dado), "up" the rest."""
    t = F.plain(seed, MINT, 0.03, 0.35)
    rng = t.rng
    for row in range(4):
        y0 = row * 16
        off = 16 if row % 2 else 0
        area(t, 0, y0 + 15, N, y0 + 16, mul=0.9, dh=-0.06)
        for x in range(off, N + 32, 32):
            area(t, (x + 31) % N, y0, (x + 31) % N + 1, y0 + 16, mul=0.9, dh=-0.06)
    t.speckle(0.03, (150, 170, 156), 0.3)
    if part == "lo":
        area(t, 0, 14, N, N, col=TEAL)
        for row in range(1, 4):
            area(t, 0, row * 16 + 15, N, row * 16 + 16, mul=0.85, dh=-0.06)
        # the crash rail: a steel channel, the grey vinyl insert, scuffed by every gurney
        area(t, 0, 8, N, 15, col=(150, 152, 150), h=0.98, smooth=0.6, f0=200)
        area(t, 0, 10, N, 13, col=(96, 100, 104), h=1.0, smooth=0.45)
        area(t, 0, 15, N, 16, mul=0.5, dh=-0.2)
        for _ in range(5):
            x = int(rng.integers(0, N - 8))
            area(t, x, 10, x + rng.integers(3, 10), 13, mul=0.6)
        for _ in range(6):
            x, y = rng.integers(0, N, 2)
            area(t, x, max(17, y), x + rng.integers(3, 12), max(17, y) + 1, col=(40, 44, 42))
    t.grime(0.25 if stained else 0.08)
    if stained:
        t.streaks(0.4, 0.8)
        if part == "lo":
            blood(t, 22, 46, 10, 0.85)
            drips(t, 14, 30, 50, 4, (4, 12))
        else:
            # a hand dragged down the wall, the fingers' four tracks fading
            disc(t, 33, 18, 4.5, col=(92, 18, 14))
            for k in range(4):
                x = 29 + k * 2.6
                for y in range(10, 54):
                    fade = 1 - (y - 10) / 48
                    if t.rng.random() < 0.85 * fade + 0.1:
                        area(t, x, y, x + 1.6, y + 1, col=(92 + (1 - fade) * 40, 22, 16))
            drips(t, 30, 38, 22, 3, (6, 20))
    return t


def glazed(seed, dirty=False):
    """Glazed white wall tile, four-inch, the grey grout; dirty, the grout gone black and blood
    sprayed across it."""
    t = F.plain(seed, (232, 234, 230), 0.02, 0.75)
    t.f0[:] = 60
    for k in range(0, N, 16):
        area(t, k, 0, k + 1, N, col=(150, 150, 146) if not dirty else (70, 66, 60), h=0.6, smooth=0.2)
        area(t, 0, k, N, k + 1, col=(150, 150, 146) if not dirty else (70, 66, 60), h=0.6, smooth=0.2)
    for i in range(4):
        for j in range(4):
            area(t, i * 16 + 1, j * 16 + 1, i * 16 + 16, j * 16 + 16, mul=0.97 + 0.06 * t.rng.random())
    if dirty:
        t.grime(0.3)
        for _ in range(3):
            disc(t, t.rng.uniform(10, 54), t.rng.uniform(10, 54), t.rng.uniform(1, 3), col=(100, 18, 14))
        drips(t, 8, 56, 10, 6, (6, 30))
    else:
        t.grime(0.06)
    return t


def padded(seed):
    """The quiet room's walls: quilted off-white vinyl in squares, a button at every corner."""
    t = F.plain(seed, (226, 222, 206), 0.02, 0.3)
    yy, xx = np.mgrid[0:N, 0:N]
    q = np.sin(np.pi * (xx % 16) / 16) * np.sin(np.pi * (yy % 16) / 16)
    t.c *= (0.86 + 0.14 * q)[..., None]
    t.h[:] = 0.5 + 0.4 * q
    for x in range(0, N, 16):
        for y in range(0, N, 16):
            disc(t, x, y, 1.2, col=(160, 156, 140), dh=-0.2)
    t.grime(0.3, (110, 96, 70))
    return t


def precast(seed, stain_=False):
    """The tower's skin: white precast concrete, exposed aggregate in the field, a smooth margin
    round each panel, the joint dark between; the rain's streaks down from the sills."""
    t = F.mat("Concrete034", (214, 208, 196), 1.0, seed=seed, smooth=0.15)
    t.speckle(0.18, (160, 150, 136), 0.4, 0.05)
    t.speckle(0.06, (246, 242, 232), 0.3)
    area(t, 0, 0, N, 3, col=(222, 218, 206), dh=0.05)
    area(t, 0, N - 3, N, N, col=(222, 218, 206), dh=0.05)
    area(t, 0, N - 1, N, N, mul=0.55, dh=-0.2)
    t.streaks(0.55 if stain_ else 0.3, 0.7)
    t.grime(0.3 if stain_ else 0.12)
    return t


def spandrel(seed):
    """Dark bronze aluminium spandrel under the ribbon windows, ribbed, chalky with age."""
    t = transit.steel(seed, BRONZE, rust=0.0, chips=0.2)
    for x in range(0, N, 8):
        area(t, x, 0, x + 1, N, mul=0.7, dh=-0.1)
        area(t, x + 1, 0, x + 2, N, mul=1.2)
    t.smooth[:] = 0.4
    t.f0[:] = 160
    t.streaks(0.35, 0.8)
    return t


def brick(seed, stain_=False):
    """The podium's brown brick, raked joints, iron spots; soot and the white bloom of salts."""
    t = F.mat("Bricks085", (112, 70, 50), 1.1, seed=seed, smooth=0.15)
    t.speckle(0.04, (60, 40, 30), 0.4)
    t.streaks(0.45 if stain_ else 0.2, 0.75)
    t.grime(0.3 if stain_ else 0.1)
    if stain_:
        t.speckle(0.05, (210, 206, 196), 0.3)
    return t


def deck(seed):
    """The roof's deck: grey painted concrete, the paint worn through, a joint across it."""
    t = F.plain(seed, (132, 136, 136), 0.05, 0.25)
    t.speckle(0.1, (100, 104, 104), 0.4)
    t.crack(2, 0.2, col=(70, 72, 72))
    t.grime(0.3)
    area(t, 0, 0, 1, N, mul=0.6, dh=-0.1)
    return t


@lru_cache(maxsize=None)
def helipad_pic():
    """The helipad, seven blocks across: a white circle on grey, the big H in it, the edge lights'
    yellow line, the tyre marks."""
    p = Pic(7, 7, (130, 134, 134), 16900)
    W = p.W
    p.rect(3, 3, W - 3, W - 3, outline=(226, 196, 50), width=3)
    p.ellipse(W * 0.12, W * 0.12, W * 0.88, W * 0.88, outline=(236, 236, 230), width=10)
    hw, hh = W * 0.32, W * 0.4
    cx = W / 2
    p.rect(cx - hw / 2, cx - hh / 2, cx - hw / 2 + 26, cx + hh / 2, fill=(236, 236, 230))
    p.rect(cx + hw / 2 - 26, cx - hh / 2, cx + hw / 2, cx + hh / 2, fill=(236, 236, 230))
    p.rect(cx - hw / 2, cx - 13, cx + hw / 2, cx + 13, fill=(236, 236, 230))
    for k in range(6):
        y = W * (0.3 + k * 0.07)
        p.line([(W * 0.2, y), (W * 0.8, y + 30)], fill=(40, 40, 40), width=3, a=60)
    return p


def helipad(i):
    t = helipad_pic().tile(i, 16910 + i, smooth=0.25, grime=0.3)
    t.speckle(0.08, (100, 104, 104), 0.3)
    t.crack(1 if i % 5 == 0 else 0, 0.2, col=(70, 72, 72))
    return t


# ---------------------------------------------------------------- the furniture's own textures

def sheet(seed, bloody=False, body=False):
    """A hospital sheet: white cotton, its folds, MERCY GEN stamped in blue at the hem."""
    t = F.plain(seed, (232, 232, 226), 0.03, 0.12)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.95 + 0.05 * np.sin(xx * 0.2 + np.sin(yy * 0.09) * 2))[..., None]
    m = text_mask("MERCY GEN", BOLD, 6, 40, 8, seed=seed)
    F.paste(t, m, 12, 54, (70, 90, 150), 0.7)
    t.grime(0.15, (150, 140, 110))
    if bloody or body:
        blood(t, 32, 26 if body else 30, 9 if body else 15, 0.8 if body else 0.95)
    return t


def vinyl(seed, rgb):
    """Upholstery vinyl: the seat's tufting, its cracks along the seams."""
    t = F.plain(seed, rgb, 0.04, 0.45)
    t.f0[:] = 50
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.9 + 0.1 * np.sin(np.pi * (xx % 32) / 32) * np.sin(np.pi * (yy % 32) / 32))[..., None]
    t.crack(2, 0.2, col=tuple(v * 0.5 for v in rgb))
    t.grime(0.2)
    return t


def curtain(seed, top=False, bloody=False):
    """The privacy curtain: a pale teal print with a darker diamond, the mesh band at its top on
    the ceiling track's hooks."""
    t = F.mat("Fabric028", (150, 196, 196), 1.1, seed=seed, smooth=0.1)
    xx = np.arange(N)
    t.c *= (0.78 + 0.22 * np.abs(np.sin(xx * 0.42)))[None, :, None]
    for gy in range(0, N, 16):
        for gx in range(0, N, 16):
            cx, cy = gx + (8 if (gy // 16) % 2 else 0), gy + 8
            for d in range(4):
                area(t, cx - 3 + d, cy - d, cx + 3 - d, cy - d + 1, mul=0.82)
                area(t, cx - 3 + d, cy + d, cx + 3 - d, cy + d + 1, mul=0.82)
    if top:
        area(t, 0, 0, N, 20, col=(226, 230, 226))
        for x in range(0, N, 3):
            area(t, x, 0, x + 1, 20, mul=0.55, alpha=90)
        for y in range(0, 20, 3):
            area(t, 0, y, N, y + 1, mul=0.55, alpha=90)
        area(t, 0, 19, N, 21, mul=0.75)
        for x in range(2, N, 8):
            area(t, x, 0, x + 2, 3, col=(170, 172, 172), alpha=255)
    if bloody:
        blood(t, 40, 40 if not top else 44, 12, 0.85)
        drips(t, 34, 48, 46, 5, (6, 16))
    t.grime(0.2, (90, 80, 60))
    return t


def enamel(seed, rgb=(214, 208, 190)):
    """Equipment enamel: the beige of everything electrical in a hospital, chipped at the corners."""
    t = F.plain(seed, rgb, 0.03, 0.45)
    t.f0[:] = 40
    t.speckle(0.01, (90, 84, 70), 0.4)
    t.grime(0.18)
    return t


def screen(seed, on):
    """A monitor's screen: the green trace of a heart that stopped, the numbers in their boxes."""
    t = F.plain(seed, (14, 18, 16), 0.02, 0.85)
    t.f0[:] = 120
    if on:
        ys = 22
        pts = [0, 0, 0, 0, -2, 0, 14, -12, 0, 2, 0, 0, 0, 0, 0, 0]
        for x in range(4, 60):
            k = (x // 2) % len(pts) if x < 34 else 0
            y = ys - pts[k]
            area(t, x, min(y, ys), x + 1, max(y, ys) + 1, col=(80, 255, 120))
        for x in range(4, 60):
            area(t, x, 38, x + 1, 39, col=(255, 220, 60) if x < 30 else (60, 200, 255))
        m = text_mask("0", BOLD, 12, 14, 14, seed=seed)
        F.paste(t, m, 44, 44, (80, 255, 120))
        lit = (t.c.mean(-1) > 50).astype(np.float32)
        t.emit[:] = np.maximum(t.emit, lit)
    else:
        t.crack(2 if seed % 2 else 0, 0.1, col=(140, 150, 150))
    t.grime(0.1)
    return t


def drawers(seed, rgb, label=None):
    """A cart's front: four drawers in coloured steel, chrome bin pulls, the tape labels."""
    t = F.plain(seed, rgb, 0.04, 0.45)
    t.f0[:] = 60
    for k in range(4):
        y0 = 4 + k * 15
        area(t, 3, y0, N - 3, y0 + 13, mul=1.06, dh=0.1)
        area(t, 3, y0 + 13, N - 3, y0 + 14, mul=0.5, dh=-0.2)
        area(t, 24, y0 + 3, 40, y0 + 6, col=(200, 202, 204), smooth=0.7, f0=200)
        area(t, 8, y0 + 8, 20, y0 + 11, col=(236, 232, 210))
    if label:
        m = text_mask(label, BOLD, 7, 52, 10, seed=seed)
        F.paste(t, m, 6, 1, (255, 255, 255), 0.9)
    t.grime(0.22)
    return t


def laminate(seed):
    """The nurses' station's front: walnut-grain laminate, the steel kick plate kicked dull."""
    t = F.wood(seed, "walnut")
    t.smooth[:] = 0.5
    area(t, 0, N - 10, N, N, col=(150, 152, 150), smooth=0.5, f0=180)
    for _ in range(6):
        x = int(t.rng.integers(0, N - 6))
        area(t, x, N - 9, x + t.rng.integers(2, 6), N - 4, mul=0.6)
    return t


def counter_top(seed):
    """Speckled grey laminate, binders and paper on it."""
    t = F.plain(seed, (176, 174, 168), 0.03, 0.5)
    t.speckle(0.1, (120, 118, 112), 0.4)
    area(t, 6, 8, 26, 34, col=(244, 242, 236))
    area(t, 34, 14, 46, 46, col=(40, 70, 140))
    area(t, 46, 14, 56, 46, col=(160, 36, 36))
    for y in range(12, 32, 3):
        area(t, 8, y, 8 + int(t.rng.integers(8, 16)), y + 1, col=(60, 60, 80))
    t.grime(0.2)
    return t


def chart_rack(seed):
    """The chart rack: the aluminium binders on their slots, a number on each."""
    t = F.plain(seed, (60, 62, 66), 0.03, 0.3)
    for k in range(6):
        x0 = 4 + k * 10
        area(t, x0, 6, x0 + 8, 58, col=(176, 178, 180), smooth=0.6, f0=180)
        area(t, x0 + 1, 8, x0 + 7, 14, col=(236, 232, 210))
        area(t, x0, 6, x0 + 8, 8, col=((200, 40, 40), (40, 90, 200), (230, 180, 40))[k % 3])
    t.grime(0.15)
    return t


def lightbox(seed, on):
    """The viewing box: a chest film clipped up, lit from behind or dead grey."""
    t = F.plain(seed, (170, 172, 172) if not on else (250, 252, 255), 0.02, 0.6)
    area(t, 0, 0, N, 4, col=(150, 152, 150))
    area(t, 0, N - 4, N, N, col=(150, 152, 150))
    area(t, 6, 6, 58, 60, col=(24, 26, 30) if on else (40, 42, 46))
    yy, xx = np.mgrid[0:N, 0:N]
    for cx in (22, 42):
        lung = np.clip(1 - np.hypot((xx - cx) / 9, (yy - 32) / 20), 0, 1)
        t.mix(lung * (0.55 if on else 0.3), (120, 124, 130) if on else (70, 72, 76), 1.0)
    for k in range(7):
        area(t, 12, 16 + k * 5, 52, 17 + k * 5, col=(180, 184, 190) if on else (90, 92, 96))
    area(t, 31, 10, 33, 58, col=(200, 204, 210) if on else (96, 98, 100))
    area(t, 28, 4, 36, 8, col=(200, 200, 196), smooth=0.7, f0=200)
    if on:
        t.emit[:] = np.maximum(t.emit, (t.c.mean(-1) > 100) * 0.9)
    t.grime(0.1)
    return t


def morgue(seed, part="lo"):
    """The morgue's wall: stainless doors, two to the block, the lever latches, the card holders."""
    t = F.steel(seed, (188, 190, 190))
    t.smooth[:] = 0.6
    t.f0[:] = 210
    for k in (0, 32):
        area(t, k + 2, 4, k + 30, 60, mul=1.04, dh=0.1)
        area(t, k + 2, 59, k + 30, 60, mul=0.55, dh=-0.2)
        area(t, k + 22, 26, k + 26, 38, col=(120, 122, 122), dh=0.2)
        area(t, k + 6, 10, k + 16, 16, col=(236, 232, 210))
        if t.rng.random() < 0.5:
            area(t, k + 7, 12, k + 15, 13, col=(40, 40, 60))
    if part == "hi":
        area(t, 0, 0, N, 3, mul=0.7)
    t.grime(0.15)
    t.streaks(0.2, 0.85)
    return t


def bag(seed):
    """The black body bag: heavy vinyl, the zip down it, the tag."""
    t = F.plain(seed, (30, 30, 32), 0.03, 0.55)
    t.f0[:] = 40
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.85 + 0.15 * np.sin(xx * 0.3 + yy * 0.05))[..., None]
    area(t, 30, 0, 33, N, col=(90, 90, 92), smooth=0.7, f0=200)
    area(t, 40, 10, 52, 18, col=(232, 160, 40))
    return t


def tape(seed):
    """Yellow barrier tape: QUARANTINE in black, the stripes."""
    t = F.plain(seed, (236, 196, 30), 0.03, 0.4)
    t.alpha[:] = 0
    t.alpha[24:40] = 255
    m = text_mask("QUARANTINE", BLACK, 8, 60, 12, seed=seed)
    F.paste(t, m, 2, 26, (20, 20, 20))
    t.grime(0.15)
    return t


def polythene(seed):
    """Polythene sheeting taped over a door: milky, creased, the duct tape round its edge."""
    t = F.plain(seed, (210, 220, 220), 0.03, 0.8)
    t.f0[:] = 40
    t.alpha[:] = 110
    yy, xx = np.mgrid[0:N, 0:N]
    crease = (np.abs(np.sin(xx * 0.09 + yy * 0.04)) > 0.97) | (np.abs(np.sin(xx * 0.05 - yy * 0.13)) > 0.98)
    t.c[crease] *= 1.15
    t.alpha[crease] = 170
    area(t, 0, 0, N, 3, col=(150, 150, 146), alpha=255)
    area(t, 0, 0, 3, N, col=(150, 150, 146), alpha=255)
    area(t, N - 3, 0, N, N, col=(150, 150, 146), alpha=255)
    return t


def notice(seed):
    """PUBLIC HEALTH NOTICE: the county's flyer taped up, curling."""
    t = F.plain(seed, (240, 238, 228), 0.02, 0.2)
    area(t, 4, 4, 60, 16, col=(180, 26, 30))
    m = text_mask("PUBLIC HEALTH|NOTICE", BLACK, 5, 54, 12, seed=seed)
    F.paste(t, m, 5, 4, (255, 255, 255))
    m = text_mask("REPORT ALL BITES|DO NOT APPROACH|THE INFECTED|STAY INDOORS", BOLD, 4, 54, 30, seed=seed + 1)
    F.paste(t, m, 5, 20, (30, 30, 30))
    area(t, 4, 54, 60, 58, col=(40, 70, 140))
    t.alpha[:] = 255
    t.alpha[:3, :] = 0
    t.alpha[:, :2] = 0
    t.grime(0.25, (120, 100, 60))
    return t


# ---------------------------------------------------------------- signs

def letters(p, text, font, x0, y0, x1, y1, col, lit, size=None, stroke=0.35):
    w, h = x1 - x0, y1 - y0
    size = size or civic.fit(text, font, int(h * 0.78), int(w))
    f = p.font(font, size)
    face = tuple(int(v) for v in col) if lit else tuple(int(v * 0.55 + 40) for v in col)
    layer = Image.new("RGBA", p.im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text(((x0 + x1) / 2 * 4, (y0 + y1) / 2 * 4), text, font=f, fill=face + (255,), anchor="mm",
           stroke_width=int(size * stroke), stroke_fill=(30, 28, 26, 255))
    d.text(((x0 + x1) / 2 * 4, (y0 + y1) / 2 * 4), text, font=f, fill=face + (255,), anchor="mm")
    p.im.alpha_composite(layer)


def lit_tile(p, i, seed, lit, grime=0.12):
    t = p.tile(i, seed, smooth=0.5, grime=grime if lit else grime + 0.1)
    if lit:
        face = (t.alpha > 128) & (t.c.mean(-1) > 60)
        t.emit[:] = np.maximum(t.emit, face * 1.0)
    return t


@lru_cache(maxsize=None)
def sign_pic(key):
    text, arrow, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 16100 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 14, W - 1, H - 14, fill=field)
    p.rect(3, 16, W - 3, H - 16, outline=tuple(min(255, v + 40) for v in field), width=0.6)
    tx0, tx1 = 8, W - 8
    if arrow == "r":
        p.poly([(W - 18, 26), (W - 8, 32), (W - 18, 38)], fill=ink)
        p.rect(W - 26, 30, W - 17, 34, fill=ink)
        tx1 = W - 28
    elif arrow == "l":
        p.poly([(18, 26), (8, 32), (18, 38)], fill=ink)
        p.rect(17, 30, 26, 34, fill=ink)
        tx0 = 28
    size = civic.fit(text, BOLD, 15, tx1 - tx0)
    p.text((tx0 + tx1) / 2, 32, text, BOLD, size, ink, anchor="mm")
    return p


def sign(key, i):
    t = sign_pic(key).tile(i, 16150 + i + len(key) * 11, smooth=0.6, grime=0.15)
    return t


@lru_cache(maxsize=None)
def name_pic(key, lit):
    text, font, cols, rows, col = NAMES[key]
    p = Pic(cols, rows, (0, 0, 0, 0), 16300 + len(key) + lit)
    letters(p, text, font, 6, 8, p.W - 6, p.H - 8, col, lit, stroke=0.18)
    return p


def name(key, i, lit):
    return lit_tile(name_pic(key, lit), i, 16400 + i + len(key) * 17 + lit, lit)


@lru_cache(maxsize=None)
def cross_pic(lit):
    """The red cross over the drive, three by three: the box's white face, the cross lit in it."""
    p = Pic(3, 3, (226, 226, 220) if lit else (190, 190, 184), 16500 + lit)
    W = p.W
    p.rect(0, 0, W, W, outline=(150, 152, 150), width=4)
    a, b = W / 3 + 4, 2 * W / 3 - 4
    red = (255, 34, 34) if lit else (150, 40, 40)
    p.rect(a, 14, b, W - 14, fill=red)
    p.rect(14, a, W - 14, b, fill=red)
    return p


def cross(i, lit):
    t = cross_pic(lit).tile(i, 16510 + i + lit, smooth=0.5, grime=0.15)
    if lit:
        red = (t.c[..., 0] > 180) & (t.c[..., 1] < 90)
        t.emit[:] = np.maximum(t.emit, red * 1.0)
    t.streaks(0.25, 0.8)
    return t


@lru_cache(maxsize=None)
def h_pic(lit):
    """The blue H that points the way to a hospital, two by two."""
    p = Pic(2, 2, (24, 70, 160) if lit else (30, 56, 110), 16600 + lit)
    W = p.W
    p.rect(4, 4, W - 4, W - 4, outline=(236, 236, 236), width=2)
    white = (255, 255, 255) if lit else (196, 196, 196)
    p.rect(W * 0.28, W * 0.2, W * 0.4, W * 0.8, fill=white)
    p.rect(W * 0.6, W * 0.2, W * 0.72, W * 0.8, fill=white)
    p.rect(W * 0.28, W * 0.45, W * 0.72, W * 0.56, fill=white)
    return p


def hsign(i, lit):
    t = h_pic(lit).tile(i, 16610 + i + lit, smooth=0.5, grime=0.15)
    if lit:
        t.emit[:] = np.maximum(t.emit, (t.c.mean(-1) > 40) * 0.8)
    return t


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 16700 + len(key))
    p.scrawl(8, 14, text, SPRAY, 30, (200, 20, 20), jitter=0.4, a=235, angle=-3)
    if key == "dead":
        p.line([(20, 52), (p.W - 20, 50)], fill=(200, 20, 20), width=2, a=200)
    return p


def spray(key, i):
    t = spray_pic(key).tile(i, 16710 + i + len(key) * 5, smooth=0.3, grime=0.0)
    lum = t.alpha > 20
    for _ in range(4):
        x = int(t.rng.integers(4, 60))
        y = int(t.rng.integers(30, 50))
        if lum[y, x]:
            drips(t, x, x + 1, y, 1, (4, 12), col=(180, 20, 20))
            t.alpha[y:y + 12, x:x + 1] = 230
    return t


def textures():
    T = {}
    T["hs_lino"] = lambda: lino(16000)
    T["hs_lino_b"] = lambda: lino(16001, worn=True)
    T["hs_lino_blood"] = lambda: lino(16002, worn=True, bloody=True)
    T["hs_lino_green"] = lambda: lino(16003, (150, 186, 170))
    T["hs_ceiling"] = lambda: ceiling(16004)
    T["hs_ceiling_stain"] = _stained_ceiling
    T["hs_troffer"] = lambda: ceiling(16006, "off")
    T["hs_troffer_on"] = lambda: ceiling(16007, "on")
    T["hs_wall"] = lambda: wall(16008)
    T["hs_wall_b"] = lambda: wall(16009, stained=True)
    T["hs_wall_lo"] = lambda: wall(16010, "lo")
    T["hs_wall_lo_b"] = lambda: wall(16011, "lo", stained=True)
    T["hs_tile"] = lambda: glazed(16012)
    T["hs_tile_b"] = lambda: glazed(16013, dirty=True)
    T["hs_pad"] = lambda: padded(16014)
    T["hs_precast"] = lambda: precast(16015)
    T["hs_precast_b"] = lambda: precast(16016, True)
    T["hs_spandrel"] = lambda: spandrel(16017)
    T["hs_brick"] = lambda: brick(16018)
    T["hs_brick_b"] = lambda: brick(16019, True)
    T["hs_deck"] = lambda: deck(16020)
    for i in range(49):
        T[f"hs_pad_h_{i}"] = lambda i=i: helipad(i)
    T["hs_sheet"] = lambda: sheet(16030)
    T["hs_sheet_blood"] = lambda: sheet(16031, bloody=True)
    T["hs_sheet_body"] = lambda: sheet(16032, body=True)
    T["hs_vinyl_blue"] = lambda: vinyl(16033, (60, 96, 130))
    T["hs_vinyl_orange"] = lambda: vinyl(16034, (200, 110, 40))
    T["hs_vinyl_teal"] = lambda: vinyl(16035, (40, 120, 116))
    T["hs_vinyl_black"] = lambda: vinyl(16036, (34, 34, 36))
    T["hs_curtain"] = lambda: curtain(16037)
    T["hs_curtain_top"] = lambda: curtain(16038, top=True)
    T["hs_curtain_blood"] = lambda: curtain(16039, bloody=True)
    T["hs_enamel"] = lambda: enamel(16040)
    T["hs_enamel_white"] = lambda: enamel(16041, (226, 226, 220))
    T["hs_enamel_green"] = lambda: enamel(16042, (120, 160, 140))
    T["hs_screen"] = lambda: screen(16043, False)
    T["hs_screen_on"] = lambda: screen(16044, True)
    T["hs_cart_red"] = lambda: drawers(16045, (178, 30, 30), "CRASH CART")
    T["hs_cart_blue"] = lambda: drawers(16046, (50, 90, 150), "MEDS")
    T["hs_cart_grey"] = lambda: drawers(16047, (150, 152, 150))
    T["hs_laminate"] = lambda: laminate(16048)
    T["hs_counter"] = lambda: counter_top(16049)
    T["hs_charts"] = lambda: chart_rack(16050)
    T["hs_lightbox"] = lambda: lightbox(16051, False)
    T["hs_lightbox_on"] = lambda: lightbox(16052, True)
    T["hs_morgue"] = lambda: morgue(16053)
    T["hs_morgue_hi"] = lambda: morgue(16054, "hi")
    T["hs_bag"] = lambda: bag(16055)
    T["hs_tape"] = lambda: tape(16056)
    T["hs_plastic"] = lambda: polythene(16057)
    T["hs_notice"] = lambda: notice(16058)
    T["hs_steel"] = lambda: F.steel(16059, (176, 178, 178))
    for key in SIGNS:
        for i in range(2):
            T[f"hs_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key, (_, _, cols, rows, _) in NAMES.items():
        for i in range(cols * rows):
            T[f"hs_name_{key}_{i}"] = lambda key=key, i=i: name(key, i, False)
            T[f"hs_name_{key}_{i}_on"] = lambda key=key, i=i: name(key, i, True)
    for i in range(9):
        T[f"hs_cross_{i}"] = lambda i=i: cross(i, False)
        T[f"hs_cross_{i}_on"] = lambda i=i: cross(i, True)
    for i in range(4):
        T[f"hs_h_{i}"] = lambda i=i: hsign(i, False)
        T[f"hs_h_{i}_on"] = lambda i=i: hsign(i, True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"hs_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
    return T


def _stained_ceiling():
    t = ceiling(16005)
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.hypot(xx - 40, yy - 22)
    ring = np.clip(1 - np.abs(r - 13) / 3, 0, 1) + np.clip(1 - r / 13, 0, 1) * 0.35
    t.mix(ring, (150, 116, 70), 0.7)
    area(t, 2, 33, 31, 62, col=(60, 56, 52), h=0.3)  # a tile fallen out, the dark over it
    return t


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

    steel, chrome, black, white = "hs_steel", "home_chrome", "home_plastic_black", "hs_enamel_white"
    beige, grey = "hs_enamel", "bunk_grey"

    def face(front, side):
        return {"north": front, "side": side, "up": side, "down": side}

    # ---- floors and ceilings
    for name in ("hs_lino", "hs_lino_b", "hs_lino_blood", "hs_lino_green"):
        add(name, None, "Vinyl Floor", "STONE", rotate=None, tex={"up": name, "side": "floor_concrete", "down": "floor_concrete"})
    add("hs_slab", None, "Floor", "STONE", rotate=None, tex={"up": "hs_lino", "down": "hs_ceiling", "side": "floor_concrete"})
    add("hs_slab_b", None, "Floor", "STONE", rotate=None, tex={"up": "hs_lino_b", "down": "hs_ceiling_stain", "side": "floor_concrete"})
    add("hs_slab_green", None, "Floor", "STONE", rotate=None, tex={"up": "hs_lino_green", "down": "hs_ceiling", "side": "floor_concrete"})
    add("hs_slab_lamp", None, "Floor", "STONE", rotate=None, tex={"up": "hs_lino", "down": "hs_troffer", "side": "floor_concrete"})
    add("hs_slab_lamp_on", None, "Floor", "STONE", rotate=None, tex={"up": "hs_lino", "down": "hs_troffer_on", "side": "floor_concrete"}, light=13)
    add("hs_ceiling", None, "Ceiling Tile", "STONE", rotate=None, tex={"all": "hs_ceiling"})
    add("hs_deck", None, "Roof Deck", "STONE", rotate=None, tex={"up": "hs_deck", "side": "concrete_dark", "down": "concrete_dark"})
    for i in range(49):
        add(f"hs_pad_h_{i}", None, "Helipad", "STONE", rotate=None, tex={"up": f"hs_pad_h_{i}", "side": "concrete_dark", "down": "concrete_dark"})

    # ---- walls
    for name in ("hs_wall", "hs_wall_b", "hs_tile", "hs_tile_b", "hs_pad"):
        add(name, None, {"hs_pad": "Padded Wall", "hs_tile": "Wall Tile", "hs_tile_b": "Wall Tile"}.get(name, "Painted Block"),
            "WOOL" if name == "hs_pad" else "STONE", rotate=None, tex={"all": name})
    add("hs_wall_lo", None, "Painted Block", "STONE", rotate=None, tex={"side": "hs_wall_lo", "up": "hs_wall", "down": "hs_wall"})
    add("hs_wall_lo_b", None, "Painted Block", "STONE", rotate=None, tex={"side": "hs_wall_lo_b", "up": "hs_wall", "down": "hs_wall"})

    # ---- outside
    for name in ("hs_precast", "hs_precast_b", "hs_spandrel", "hs_brick", "hs_brick_b"):
        add(name, None, {"hs_spandrel": "Spandrel Panel"}.get(name, "Brick" if "brick" in name else "Precast Concrete"),
            "METAL" if name == "hs_spandrel" else "STONE", rotate=None, tex={"all": name})
    # the tower's windows, two blocks tall: a deep precast reveal round a bronze frame, the glass set
    # back toward the room (facing: out); the sill on the lower block, the head on the upper
    for suffix, glass in (("", "tx_glass"), ("_broken", "tx_glass_broken")):
        sides = [[0, 0, 0, 2, 16, 16, "hs_precast"], [14, 0, 0, 16, 16, 16, "hs_precast"], [7.5, 0, 9, 8.5, 16, 10, "hs_spandrel"]]
        add(f"hs_window_lo{suffix}", sides + [[2, 0, 0, 14, 3, 16, "hs_precast"], [2, 3, 9, 14, 4, 10, "hs_spandrel"],
                                              [2, 4, 9.4, 14, 16, 9.6, glass], [2, 2.5, 0, 14, 3, 1, "hs_precast_b"]],
            "Window", "GLASS", render="translucent", shape=[[0, 0, 0, 16, 16, 16]])
        add(f"hs_window_hi{suffix}", sides + [[2, 13, 0, 14, 16, 16, "hs_precast"], [2, 12, 9, 14, 13, 10, "hs_spandrel"],
                                              [2, 0, 9.4, 14, 12, 9.6, glass]],
            "Window", "GLASS", render="translucent", shape=[[0, 0, 0, 16, 16, 16]])
        # the podium's ribbon: bronze framed glass in the wall's middle, a mullion at the block's west edge
        add(f"hs_ribbon{suffix}", [[0, 0, 7, 1.2, 16, 9, "hs_spandrel"], [0, 0, 6.5, 16, 1.5, 9.5, "hs_spandrel"],
                                   [0, 14.5, 7, 16, 16, 9, "hs_spandrel"], [1.2, 1.5, 7.9, 16, 14.5, 8.1, glass]],
            "Window", "GLASS", render="translucent", shape=[[0, 0, 7, 16, 16, 9]])
    add("hs_roof", None, "Roof", "STONE", rotate=None, tex={"up": "hs_deck", "down": "hs_ceiling", "side": "concrete_dark"})
    add("hs_roof_lamp", None, "Roof", "STONE", rotate=None, tex={"up": "hs_deck", "down": "hs_troffer", "side": "concrete_dark"})
    add("hs_roof_lamp_on", None, "Roof", "STONE", rotate=None, tex={"up": "hs_deck", "down": "hs_troffer_on", "side": "concrete_dark"}, light=13)

    # ---- the ward: the bed in two (the head at the block's north), its rails, the sheet over the mattress
    frame = [[1, 2.5, 0, 15, 3.5, 16, beige], [1.5, 0, 1, 2.5, 2.5, 2, grey], [13.5, 0, 1, 14.5, 2.5, 2, grey]]
    castor = [[1.2, 0, 13.5, 2.8, 1.6, 15, black], [13.2, 0, 13.5, 14.8, 1.6, 15, black]]
    rail = [[0.4, 7, 3, 1.2, 10, 12, chrome], [14.8, 7, 3, 15.6, 10, 12, chrome]]
    for suffix, sh in (("", "hs_sheet"), ("_blood", "hs_sheet_blood")):
        add(f"hs_bed_head{suffix}", frame + rail + [[1, 3.5, 0, 15, 7, 16, {"up": sh, "side": sh, "down": beige}],
                                                     [1, 3.5, 0, 15, 16, 1.4, beige], [3, 7, 1.5, 13, 9, 6, "home_pillow"],
                                                     [1, 7, 1.4, 15, 12, 2.2, sh]],
            "Hospital Bed", "WOOL", loot="home_medicine")
        add(f"hs_bed_foot{suffix}", frame + castor + [[1, 3.5, 0, 15, 7, 16, {"up": sh, "side": sh, "down": beige}],
                                                      [1, 3.5, 14.6, 15, 10, 16, beige], [5, 8, 15.6, 11, 10.5, 16.4, "sc_clipboard"]],
            "Hospital Bed", "WOOL")
    add("hs_bed_foot_body", frame + castor + [[1, 3.5, 0, 15, 7, 16, {"up": "hs_sheet", "side": "hs_sheet", "down": beige}],
                                              [1, 3.5, 14.6, 15, 10, 16, beige], [3.5, 7, 0, 12.5, 10, 9, "hs_sheet_body"],
                                              [5, 7, 9, 11, 8.5, 14, "hs_sheet_body"], [4, 7, 0, 7.5, 9, 6, "hs_sheet_body"]],
        "Hospital Bed", "WOOL")
    add("hs_nightstand", [[2, 0, 4, 14, 11, 15, face("hs_cart_grey", beige)], [1.5, 11, 3.5, 14.5, 12, 15.5, "hs_counter"],
                          [4, 12, 6, 7, 15, 9, "home_white"], [9, 12, 7, 12, 13, 12, "paper"]], "Bedside Cabinet", "METAL", loot="home_medicine")
    add("hs_overbed", [[1, 0, 10, 15, 1, 14, grey], [12, 1, 11, 14, 11, 13, chrome], [1, 11, 2, 15, 12, 13, "hs_counter"],
                       [4, 12, 4, 10, 12.6, 9, "home_plastic_beige"]], "Overbed Table", "METAL", shape=[[1, 0, 2, 15, 12, 14]])
    # the curtain on its ceiling track (along the block's x, hanging in its middle): drawn, or bunched to one end
    add("hs_curtain", [[0, 1.5, 7.6, 16, 16, 8.4, "hs_curtain"]], "Privacy Curtain", "WOOL", decor=True)
    add("hs_curtain_blood", [[0, 1.5, 7.6, 16, 16, 8.4, "hs_curtain_blood"]], "Privacy Curtain", "WOOL", decor=True)
    add("hs_curtain_top", [[0, 0, 7.6, 16, 15, 8.4, "hs_curtain_top"], [0, 15, 7, 16, 16, 9, chrome]], "Privacy Curtain", "WOOL", decor=True)
    add("hs_curtain_open", [[0, 1.5, 6, 3, 16, 10, "hs_curtain"]], "Privacy Curtain", "WOOL", decor=True)
    add("hs_curtain_open_top", [[0, 0, 6, 3, 15, 10, "hs_curtain_top"], [0, 15, 7, 16, 16, 9, chrome]], "Privacy Curtain", "WOOL", decor=True)
    add("hs_track", [[0, 15, 7, 16, 16, 9, chrome]], "Curtain Track", "METAL", decor=True)
    for suffix, scr in (("", "hs_screen"), ("_on", "hs_screen_on")):
        add(f"hs_monitor{suffix}", [[7, 0, 7, 9, 9, 9, chrome], [4, 0, 4, 12, 1, 12, black], [3, 9, 5, 13, 16, 13, {"north": scr, "side": beige, "up": beige, "down": beige}]],
            "Patient Monitor", "METAL", **({"light": 4} if suffix else {}))
    add("hs_iv", [[7.5, 0, 7.5, 8.5, 16, 8.5, chrome], [4, 0, 4, 12, 0.8, 12, chrome], [6, 11, 7.4, 10, 15, 8.6, "home_white"],
                  [7.8, 4, 7.8, 8.2, 11, 8.2, "home_white"]], "IV Stand", "METAL", decor=True)

    # ---- the corridors: the gurney in two, one with a body bag on it
    g_legs = [[2, 0, 2, 3, 6, 3, chrome], [13, 0, 2, 14, 6, 3, chrome], [2, 0, 13, 3, 6, 14, chrome], [13, 0, 13, 14, 6, 14, chrome]]
    g_base = [[1.5, 1, 1, 14.5, 1.6, 15, chrome], [2, 6, 0, 14, 7, 16, chrome], [2.5, 7, 0, 13.5, 8.5, 16, "hs_vinyl_black"]]
    add("hs_gurney_a", g_legs + g_base + [[3, 8.5, 1, 13, 10, 5, "home_pillow"], [1.5, 7.5, 0, 2, 10, 14, chrome], [14, 7.5, 0, 14.5, 10, 14, chrome]],
        "Gurney", "METAL")
    add("hs_gurney_b", g_legs + g_base + [[3, 8.5, 2, 13, 9, 15, "hs_sheet"]], "Gurney", "METAL")
    add("hs_gurney_bag_a", g_legs + g_base + [[3.5, 8.5, 0, 12.5, 12, 16, "hs_bag"], [5, 12, 1, 11, 13.5, 6, "hs_bag"]], "Gurney", "METAL")
    add("hs_gurney_bag_b", g_legs + g_base + [[3.5, 8.5, 0, 12.5, 11, 13, "hs_bag"], [5, 8.5, 13, 11, 10, 15, "hs_bag"]], "Gurney", "METAL")
    wheel = "home_plastic_black"
    add("hs_wheelchair", [[2, 0, 4, 2.8, 12, 12, wheel], [13.2, 0, 4, 14, 12, 12, wheel], [3, 6, 3, 13, 7, 12, "hs_vinyl_blue"],
                          [3, 7, 11, 13, 15, 12, "hs_vinyl_blue"], [2.8, 6, 2, 3.4, 16, 3, chrome], [12.6, 6, 2, 13.2, 16, 3, chrome],
                          [3, 0, 0, 5, 1, 3, chrome], [11, 0, 0, 13, 1, 3, chrome], [2.4, 9, 3, 3, 10, 12, chrome], [13, 9, 3, 13.6, 10, 12, chrome]],
        "Wheelchair", "METAL", shape=[[2, 0, 2, 14, 12, 13]])
    add("hs_wheelchair_tipped", [[2, 0, 2, 14, 2, 14, "hs_vinyl_blue"], [2, 2, 3, 2.8, 10, 11, wheel], [2, 2, 3, 14, 2.8, 11, wheel],
                                 [8, 0, 10, 15, 6, 11, chrome]], "Wheelchair", "METAL", decor=True)
    add("hs_cart_crash", [[2, 1, 3, 14, 13, 14, face("hs_cart_red", "hs_cart_red")], [1.5, 13, 2.5, 14.5, 14, 14.5, black],
                          [3, 14, 4, 9, 16, 9, face("hs_screen", beige)], [10, 14, 5, 13, 15.5, 12, face("hs_screen_on", beige)]] + [[x, 0, z, x + 2, 1, z + 2, black] for x in (2, 12) for z in (3, 12)],
        "Crash Cart", "METAL", loot="pharmacy")
    add("hs_cart_med", [[2, 1, 3, 14, 13, 14, face("hs_cart_blue", "hs_cart_blue")], [1.5, 13, 2.5, 14.5, 14, 14.5, "hs_counter"],
                        [3, 14, 5, 8, 15, 10, "paper"]] + [[x, 0, z, x + 2, 1, z + 2, black] for x in (2, 12) for z in (3, 12)],
        "Medication Cart", "METAL", loot="pharmacy")
    add("hs_cart_linen", [[2, 0, 3, 3, 13, 4, chrome], [13, 0, 3, 14, 13, 4, chrome], [2, 0, 12, 3, 13, 13, chrome], [13, 0, 12, 14, 13, 13, chrome],
                          [2.5, 3, 3.5, 13.5, 13, 12.5, "sc_canvas"], [3, 10, 4, 13, 14, 12, "hs_sheet"]], "Linen Cart", "WOOL", loot="home_laundry")
    add("hs_cart_meals", [[2, 0, 2, 14, 16, 14, face("fit_stainless", "fit_stainless")], [3, 2, 1.8, 13, 15, 2, "hs_steel"]], "Meal Cart", "METAL", loot="food")
    add("hs_station", [[0, 0, 4, 16, 11, 16, {"north": "hs_laminate", "side": "hs_laminate", "up": "hs_counter", "down": grey}],
                       [0, 11, 3, 16, 12, 6, "hs_counter"], [0, 6, 6, 16, 7, 16, "hs_counter"], [2, 7, 9, 8, 10, 14, "paper"],
                       [10, 7, 10, 14, 9, 13, black]], "Nurses' Station", "WOOD", loot="office", shape=[[0, 0, 3, 16, 12, 16]])
    add("hs_station_end", [[0, 0, 4, 16, 11, 16, {"north": "hs_laminate", "side": "hs_laminate", "up": "hs_counter", "down": grey}],
                           [0, 11, 3, 16, 12, 6, "hs_counter"], [0, 6, 6, 16, 7, 16, "hs_counter"], [3, 7, 8, 13, 15, 15, face("hs_charts", grey)]],
        "Nurses' Station", "WOOD", loot="office", shape=[[0, 0, 3, 16, 12, 16]])
    for name, vin in (("hs_chairs", "hs_vinyl_orange"), ("hs_chairs_b", "hs_vinyl_teal")):
        add(name, [[0, 0, 4, 16, 1, 12, chrome], [1, 1, 7, 2, 6, 8, chrome], [14, 1, 7, 15, 6, 8, chrome], [0.5, 6, 3, 7.6, 7.5, 12, vin],
                   [8.4, 6, 3, 15.5, 7.5, 12, vin], [0.5, 7.5, 11, 7.6, 15, 12.5, vin], [8.4, 7.5, 11, 15.5, 15, 12.5, vin],
                   [7.6, 6, 4, 8.4, 10, 11, chrome]], "Waiting Chairs", "METAL", shape=[[0, 0, 3, 16, 8, 12.5]])
    add("hs_chairs_tipped", [[0, 0, 2, 16, 1.5, 14, "hs_vinyl_orange"], [1, 1.5, 3, 7, 8, 4.5, "hs_vinyl_orange"], [0, 0, 12, 16, 3, 13, chrome]],
        "Waiting Chairs", "METAL", decor=True)
    add("hs_oxygen", [[3, 0, 5, 5.5, 15, 7.5, "bunk_pipe_green"], [6.5, 0, 5, 9, 15, 7.5, "bunk_pipe_green"], [10, 0, 5, 12.5, 14, 7.5, "bunk_pipe_green"],
                      [2, 0, 4, 14, 2, 9, grey], [3.5, 15, 5.5, 5, 16, 7, chrome], [7, 15, 5.5, 8.5, 16, 7, chrome]], "Oxygen Cylinders", "METAL")
    add("hs_sharps", [[5, 6, 12, 11, 12, 16, {"north": "bunk_red_off", "side": "bunk_red_off", "up": "home_white", "down": "bunk_red_off"}]],
        "Sharps Bin", "METAL", decor=True)
    add("hs_biohazard", [[3, 0, 3, 13, 11, 13, "bunk_red_off"], [2.6, 11, 2.6, 13.4, 12, 13.4, "bunk_red_off"]], "Biohazard Bin", "METAL", loot="home_trash")
    add("hs_tray", [[3, 0, 4, 12, 0.6, 12, "home_plastic_beige"], [4, 0.6, 5, 7, 1.5, 8, "home_white"], [8, 0.6, 7, 11, 1, 11, "home_blood_pool"]],
        "Meal Tray", "WOOD", decor=True)

    # ---- the theatres
    add("hs_or_table", [[6, 0, 3, 10, 1, 13, chrome], [7, 1, 6, 9, 7, 10, chrome], [3, 7, 0, 13, 8.5, 16, chrome],
                        [3.5, 8.5, 0, 12.5, 10, 16, "hs_vinyl_black"], [1.5, 7.5, 6, 3, 8.5, 9, chrome], [13, 7.5, 6, 14.5, 8.5, 9, chrome]],
        "Operating Table", "METAL")
    for suffix in ("", "_on"):
        lamp = "bunk_glass_on" if suffix else "bunk_glass_off"
        add(f"hs_or_light{suffix}", [[7.5, 10, 7.5, 8.5, 16, 8.5, chrome], [2, 6, 2, 14, 9, 14, {"down": lamp, "side": white, "up": white}],
                                     [7, 9, 7, 9, 10, 9, chrome]], "Surgical Light", "METAL", decor=True, **({"light": 14} if suffix else {}))
    add("hs_anesthesia", [[2, 0, 4, 14, 9, 14, face("hs_cart_grey", beige)], [2, 9, 4, 14, 10, 14, "hs_counter"], [3, 10, 10, 13, 16, 13, face("hs_screen", beige)],
                          [4, 10, 5, 6, 14, 7, "bunk_pipe_green"], [8, 10, 5, 10, 14, 7, "bunk_pipe_blue"], [12, 10, 5, 13, 15, 6, black]],
        "Anaesthesia Machine", "METAL", loot="pharmacy")
    add("hs_mayo", [[7.5, 0, 7.5, 8.5, 9, 8.5, chrome], [4, 0, 4, 12, 0.8, 12, chrome], [3, 9, 3, 13, 9.6, 13, "fit_stainless"],
                    [4, 9.6, 5, 9, 10, 6, chrome], [5, 9.6, 8, 11, 10, 9, chrome], [10, 9.6, 4, 11, 10, 11, "home_blood_pool"]],
        "Instrument Stand", "METAL", decor=True)
    add("hs_scrub_sink", [[0, 5, 9, 16, 11, 16, "fit_stainless"], [0, 0, 14, 16, 5, 16, "fit_stainless"], [3, 11, 14, 4, 15, 15, chrome],
                          [12, 11, 14, 13, 15, 15, chrome], [3, 14, 11, 4, 15, 15, chrome], [12, 14, 11, 13, 15, 15, chrome]],
        "Scrub Sink", "METAL", loot="home_sink")
    add("hs_sink", [[2, 6, 7, 14, 10, 16, {"up": "home_basin", "side": "home_white", "down": "home_white"}], [7, 0, 13, 9, 6, 15, chrome], [7.5, 10, 13, 8.5, 13, 14, chrome],
                    [7.5, 12, 10, 8.5, 13, 14, chrome]], "Basin", "STONE")

    # ---- radiology and the lab
    add("hs_xray_table", [[4, 0, 2, 12, 6, 14, face("hs_enamel_white", white)], [1, 6, 0, 15, 8, 16, white], [2, 8, 0, 14, 8.6, 16, "hs_sheet"]],
        "X-Ray Table", "METAL")
    add("hs_xray_arm", [[7, 9, 7, 9, 16, 9, white], [4, 4, 4, 12, 9, 12, beige], [5, 3, 5, 11, 4, 11, black], [0, 15, 7, 16, 16, 9, steel]],
        "X-Ray Tube", "METAL", decor=True)
    for suffix in ("", "_on"):
        add(f"hs_lightbox{suffix}", [[1, 2, 13, 15, 14, 16, {"north": f"hs_lightbox{suffix}", "side": white, "up": white, "down": white}]],
            "Film Viewer", "METAL", decor=True, **({"light": 9} if suffix else {}))
    add("hs_lab_bench", [[0, 0, 2, 16, 10, 16, {"north": "hs_cart_grey", "side": beige, "up": "hs_counter", "down": grey}],
                         [0, 10, 1.5, 16, 11, 16, "hs_counter"], [2, 11, 6, 5, 16, 10, black], [2.5, 15, 5, 4.5, 16, 7, black],
                         [8, 11, 7, 13, 14, 12, "hs_enamel_white"], [9, 14, 8, 12, 14.5, 11, black], [6, 11, 11, 7, 13, 12, "bunk_glass_on"]],
        "Lab Bench", "METAL", loot="pharmacy")
    for suffix in ("", "_on"):
        add(f"hs_bloodfridge{suffix}", [[1, 0, 2, 15, 16, 16, face("bunk_glass_on" if suffix else "bunk_glass_off", "hs_enamel_white")],
                                        [3, 3, 4, 13, 4, 15, chrome], [3, 8, 4, 13, 9, 15, chrome], [4, 9, 6, 7, 12, 8, "bunk_red_off"],
                                        [9, 4, 6, 12, 7, 8, "bunk_red_off"]], "Blood Bank Fridge", "METAL", loot="pharmacy",
            **({"light": 6} if suffix else {}))

    # ---- the morgue: the wall of doors (a lower and an upper row), one drawer pulled out with its sheet
    add("hs_morgue_lo", [[0, 0, 2, 16, 16, 16, face("hs_morgue", "hs_morgue")]], "Morgue Cooler", "METAL", loot="scraps")
    add("hs_morgue_hi", [[0, 0, 2, 16, 16, 16, face("hs_morgue_hi", "hs_morgue")]], "Morgue Cooler", "METAL")
    add("hs_morgue_open", [[0, 0, 15, 16, 16, 16, "hs_morgue"], [1, 6, 0, 15, 7, 15, "fit_stainless"], [2, 7, 0, 14, 10, 14, "hs_sheet_body"],
                           [4, 10, 2, 12, 11, 8, "hs_sheet_body"], [0.5, 4, 0, 1, 7, 15, chrome], [15, 4, 0, 15.5, 7, 15, chrome]],
        "Morgue Drawer", "METAL", shape=[[0, 0, 0, 16, 10, 16]])
    add("hs_autopsy", [[6, 0, 6, 10, 8, 10, "fit_stainless"], [1, 8, 0, 15, 10, 16, "fit_stainless"], [7, 8, 7, 9, 10.2, 9, black],
                       [2, 10, 1, 14, 11, 4, "fit_stainless"]], "Autopsy Table", "METAL")

    # ---- the nursery
    add("hs_bassinet", [[3, 0, 3, 13, 1, 13, chrome], [4, 1, 4, 5, 7, 5, chrome], [11, 1, 4, 12, 7, 5, chrome], [4, 1, 11, 5, 7, 12, chrome],
                        [11, 1, 11, 12, 7, 12, chrome], [3, 7, 2, 13, 8, 14, chrome], [3.4, 8, 2.4, 12.6, 12, 13.6, "tx_glass"],
                        [5, 8, 4, 11, 9, 12, "hs_sheet"]], "Bassinet", "METAL", render="translucent")
    add("hs_incubator", [[2, 0, 2, 14, 7, 14, face("hs_cart_grey", beige)], [2, 7, 2, 14, 8, 14, beige], [2.5, 8, 2.5, 13.5, 14, 13.5, "tx_glass"],
                         [5, 8, 5, 11, 9, 11, "hs_sheet"]], "Incubator", "METAL", render="translucent", loot="home_medicine")

    # ---- staff
    add("hs_locker", [[1, 0, 2, 15, 16, 16, face("sc_locker_lo_b", "sc_locker_side_b")]], "Locker", "METAL", loot="clothes")
    add("hs_locker_top", [[1, 0, 2, 15, 14, 16, face("sc_locker_hi_b", "sc_locker_side_b")]], "Locker", "METAL")
    add("hs_cabinet", [[1, 2, 10, 15, 15, 16, face("bunk_glass_off", "hs_enamel_white")], [2, 6, 11, 14, 6.5, 15.5, white],
                       [3, 6.5, 11.5, 6, 9, 14, "home_white"], [8, 6.5, 12, 10, 8.5, 14, "bunk_red_off"]], "Medicine Cabinet", "METAL",
        loot="pharmacy", decor=True)
    add("hs_cabinet_lo", [[0, 0, 2, 16, 10, 16, face("hs_cart_grey", beige)], [0, 10, 1.5, 16, 11, 16, "hs_counter"],
                          [3, 11, 8, 6, 13, 11, "home_white"], [9, 11, 9, 13, 12, 14, "paper"]], "Supply Cabinet", "METAL", loot="pharmacy")

    # ---- signs on the walls: two by one, the plate's back at z = 16
    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    for key in SIGNS:
        for i in range(2):
            add(f"hs_sgn_{key}_{i}", plate(f"hs_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key, (_, _, cols, rows, _) in NAMES.items():
        for i in range(cols * rows):
            add(f"hs_name_{key}_{i}", plate(f"hs_name_{key}_{i}", 14.5), "Sign", "METAL", decor=True)
            add(f"hs_name_{key}_{i}_on", plate(f"hs_name_{key}_{i}_on", 14.5), "Sign", "METAL", decor=True,
                **({"light": 12} if i % 3 == 1 else {}))
    for i in range(9):
        for suffix in ("", "_on"):
            add(f"hs_cross_{i}{suffix}", [[0, 0, 10, 16, 16, 16, {"north": f"hs_cross_{i}{suffix}", "side": "hs_steel", "south": "hs_steel"}]],
                "Hospital Sign", "METAL", **({"light": 13} if suffix and i % 2 == 1 else {}))
    for i in range(4):
        for suffix in ("", "_on"):
            add(f"hs_h_{i}{suffix}", [[0, 0, 12, 16, 16, 16, {"north": f"hs_h_{i}{suffix}", "side": "hs_steel", "south": "hs_steel"}]],
                "Hospital Sign", "METAL", **({"light": 9} if suffix and i == 1 else {}))
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"hs_spray_{key}_{i}", plate(f"hs_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)

    # ---- the last days
    add("hs_tape", [[0, 7, 7.9, 16, 11, 8.1, "hs_tape"]], "Quarantine Tape", "WOOL", decor=True)
    add("hs_plastic", [[0, 0, 7.8, 16, 16, 8.2, "hs_plastic"]], "Plastic Sheeting", "WOOL", decor=True, render="translucent")
    add("hs_notice", plate("hs_notice", 15.7), "Notice", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("hs_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} hospital blocks in {path}")


if __name__ == "__main__":
    main()
