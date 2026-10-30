"""The rp_ kit: the ramp round the airliner at gate 1 (Josiah, 2026-10-29: the MW2 "Terminal" homage,
"Main concourse + one pier"), after refs/airport/concourse-jetbridges and apron-airliners: the jet
bridge from the pier to the plane's front door, the stair truck at its back door, and the ground
crew's gear left where it stood when the flights stopped. world/county/AirportTerminal sets them out.
Everything faces north (rotate=facing) unless said:

- the jet bridge, a box of ribbed off-white steel on its legs: rp_jb_floor (the ribbed rubber
  walk, the steel under it), rp_jb_wall / rp_jb_wall_band / rp_jb_window (a side, its outside to
  the north: plain, with the navy stripe, or with the band of tinted glass), rp_jb_corner (an
  outside corner, the sides north and west), rp_jb_roof / rp_jb_roof_lamp(_lit) (the roof over
  a cell, the ceiling's light), rp_jb_eave (the roof's edge over a wall, its lip to the north),
  rp_jb_bellows / rp_jb_bellows_top (the black rubber canopy round the plane's door, its face to the
  north), rp_jb_console (the driver's desk in the cab, facing the plane), rp_jb_leg (the lift
  column under the bridge), rp_jb_bogie (the drive wheels at its foot, rolling north-south),
  rp_jb_column (the round column under the rotunda);
- the stair truck, its stairs rising north: rp_as_step (two treads between the stringers, the
  rails either side; shaped like a stair), rp_as_top (the platform at the head), rp_as_rail (the
  rails round the platform, open to the north and south), rp_as_post (the canopy's posts),
  rp_as_roof (the canopy), rp_as_cab_lo / rp_as_cab_hi (the truck's cab, its windscreen north,
  under the platform), rp_as_bed (the chassis under the stairs, a wheel set when wheels),
  rp_as_lift (the scissor struts between the chassis and the stairs);
- rp_tug_front / rp_tug_rear: a baggage tractor, two blocks, the bonnet north;
- rp_dolly_front / rp_dolly_rear (_bags): a baggage cart, two blocks, the tow bar north; the bags
  piled on it or not; rp_dolly_tipped: one on its side;
- rp_uld / rp_uld_open / rp_uld_spill: a cargo container, its curtain door north;
- rp_bl_0..3: the belt loader rising north over four blocks (0 and 1 on the ground, 2 and 3 the
  block up): the chassis, the cab, the belt with its side rails;
- rp_gpu: the ground power cart, its cable out north; rp_chocks: a pair of wheel chocks on a rope;
  rp_wand: a marshaller's wands dropped;
- the escape slide off the plane's back door on the right, rising north as the stairs do (so set
  facing south to come down northward): rp_slide (a length at 45 degrees), rp_slide_top (its head
  on the sill, the door to the north of it), rp_slide_foot (the run-out on the apron).

    python3 tools/city/ramp.py          rewrites the rp_ entries in blocks.json

photo_textures.py draws the textures (ramp.register()).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area, disc  # noqa: E402

N = P.N
HERE = Path(__file__).parent

STEEL, CHROME, RUBBER, BLACK, TYRE = "st_galv", "home_chrome", "home_rubber", "home_plastic_black", "gs_tyre"
NAVY = (34, 46, 84)


# ---------------------------------------------------------------- textures

def painted(seed, rgb, grime=0.25, rust=0.0, smooth=0.45):
    t = F.plain(seed, rgb, 0.04, smooth)
    t.f0[:] = 30
    t.streaks(0.2, 0.85)
    t.grime(grime, (60, 54, 44))
    if rust:
        t.speckle(rust, (120, 66, 36), 0.7, -0.05)
    return t


def clad(seed, band=False):
    """The bridge's skin: off-white steel in vertical ribs every eight, rain streaks down from the
    roof's lip, the apron's dirt splashed up the bottom; a navy band along the middle when band."""
    t = painted(seed, (214, 214, 206), 0.2, 0.01)
    for x in range(0, N, 8):
        area(t, x, 0, x + 1, N, mul=(0.72, 0.72, 0.72), dh=-0.25)
        area(t, x + 1, 0, x + 2, N, mul=(1.1, 1.1, 1.1), dh=0.15)
    if band:
        area(t, 0, 26, N, 38, col=NAVY)
        for x in range(0, N, 8):
            area(t, x, 26, x + 1, 38, mul=(0.7, 0.7, 0.7))
    yy = np.mgrid[0:N, 0:N][0]
    t.c *= (1 - 0.18 * np.clip((yy - 44) / 20, 0, 1))[..., None]
    t.grime(0.25, (70, 62, 50))
    return t


def lining(seed):
    """Inside: beige laminate panels, the seam between them, scuffed."""
    t = F.plain(seed, (190, 182, 164), 0.03, 0.4)
    area(t, 31, 0, 32, N, mul=(0.7, 0.7, 0.7), dh=-0.2)
    t.speckle(0.02, (120, 110, 96), 0.4)
    t.grime(0.18)
    return t


def tread(seed):
    """The walk: grey rubber ribbed across, worn dark down the middle."""
    t = F.plain(seed, (84, 86, 88), 0.04, 0.25)
    for y in range(0, N, 4):
        area(t, 0, y, N, y + 1, mul=(0.7, 0.7, 0.7), dh=-0.12)
    xx = np.mgrid[0:N, 0:N][1]
    t.c *= (1 - 0.16 * np.exp(-((xx - N / 2) / 16) ** 2))[..., None]
    t.grime(0.25)
    return t


def ceiling(seed, lamp=0):
    """The ceiling: white perforated panels, a light box across the middle (lamp 1 dark, 2 lit)."""
    t = F.plain(seed, (206, 206, 200), 0.02, 0.4)
    for y in range(2, N, 4):
        for x in range(2, N, 4):
            area(t, x, y, x + 1, y + 1, mul=(0.8, 0.8, 0.8))
    area(t, 0, 0, N, 1, mul=(0.7, 0.7, 0.7))
    if lamp:
        area(t, 8, 22, 56, 42, col=(140, 140, 136))
        area(t, 10, 24, 54, 40, col=(236, 236, 226) if lamp == 2 else (180, 182, 178), smooth=0.8)
        if lamp == 2:
            t.emit[24:40, 10:54] = 0.9
    t.grime(0.15)
    return t


def bellows(seed):
    """The canopy's folds: black rubber in bands, each lit along its top and shadowed below."""
    t = F.plain(seed, (34, 34, 36), 0.04, 0.3)
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 2, mul=(1.5, 1.5, 1.5), dh=0.3)
        area(t, 0, y + 6, N, y + 8, mul=(0.5, 0.5, 0.5), dh=-0.3)
    t.grime(0.3, (90, 84, 70))
    return t


def glass(seed):
    t = F.plain(seed, (70, 86, 96), 0.02, 0.95)
    t.alpha[:] = 120
    t.f0[:] = 60
    t.grime(0.2, (90, 90, 80))
    return t


def roof_top(seed):
    t = F.plain(seed, (96, 98, 98), 0.05, 0.2)
    t.speckle(0.04, (70, 70, 70), 0.4)
    t.grime(0.4, (50, 46, 38))
    return t


def under(seed):
    """Underneath: steel channels, the cable trays."""
    t = painted(seed, (120, 122, 120), 0.4, 0.03)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 3, mul=(0.6, 0.6, 0.6), dh=-0.2)
    return t


def console(seed):
    """The driver's desk: grey panel, the joystick's boot, the buttons, a dead screen."""
    t = F.plain(seed, (90, 94, 98), 0.03, 0.45)
    area(t, 6, 8, 26, 24, col=(20, 22, 24), smooth=0.9)
    for i, col in enumerate(((40, 160, 60), (200, 40, 30), (230, 180, 40), (220, 220, 210))):
        disc(t, 36 + i * 6, 14, 2, col=col, dh=0.2)
    disc(t, 46, 40, 8, col=(30, 30, 30), dh=0.2)
    disc(t, 46, 40, 3, col=(60, 60, 60), dh=0.4)
    area(t, 6, 34, 26, 36, col=(230, 180, 40))
    t.grime(0.2)
    return t


def diamond(seed):
    """Tread plate: galvanised, the raised diamonds in rows, worn bright where the feet go."""
    t = F.plain(seed, (150, 152, 150), 0.04, 0.6)
    t.f0[:] = 160
    yy, xx = np.mgrid[0:N, 0:N]
    for row in range(0, N, 6):
        off = 3 if (row // 6) % 2 else 0
        for col in range(off, N, 6):
            m = (np.abs(xx - col) + np.abs(yy - row) * 2.5) < 3
            t.h[m] += 0.3
            t.c[m] *= 1.12
    t.grime(0.3)
    return t


def cab_side(seed, rgb, window=True):
    """A cab's side: the door's outline, the window, the handle."""
    t = painted(seed, rgb, 0.3, 0.01)
    if window:
        area(t, 6, 4, 58, 28, col=(40, 50, 56), smooth=0.95, f0=60)
        area(t, 6, 4, 58, 6, mul=(1.3, 1.3, 1.3))
    F.gap(t, 4, 2, 60, 60)
    area(t, 44, 34, 52, 36, col=(40, 40, 40), dh=0.2)
    return t


def windscreen(seed):
    t = F.plain(seed, (44, 56, 64), 0.03, 0.95)
    t.f0[:] = 80
    area(t, 0, 0, N, 4, col=(30, 30, 30))
    area(t, 0, N - 4, N, N, col=(30, 30, 30))
    t.grime(0.25, (100, 96, 84))
    return t


def grille(seed, rgb):
    t = painted(seed, rgb, 0.3)
    area(t, 8, 16, 56, 40, col=(30, 30, 30))
    for y in range(18, 40, 4):
        area(t, 8, y, 56, y + 1, col=(80, 80, 80), dh=0.2)
    for x in (12, 52):
        disc(t, x, 50, 4, col=(240, 230, 190), smooth=0.9)
    return t


def uld(seed, side):
    """A container's aluminium: the ribs, the rivets, the stencil and the red ID plate."""
    t = F.plain(seed, (170, 174, 176), 0.05, 0.55)
    t.f0[:] = 170
    for x in range(0, N, 12):
        area(t, x, 0, x + 2, N, mul=(0.82, 0.82, 0.82), dh=0.2)
    for y in (2, N - 3):
        for x in range(2, N, 4):
            area(t, x, y, x + 1, y + 1, mul=(0.6, 0.6, 0.6))
    if side:
        from bunker import stencil
        stencil(t, "AKE 21406 GE", (40, 40, 40), 7, x=6, y=10, w=52, h=10, chip=0.2)
        area(t, 8, 44, 24, 52, col=(170, 30, 30))
    t.grime(0.3)
    return t


def uld_door(seed, open_=False):
    """The curtain door: grey canvas in a frame, the straps; rolled up when open."""
    t = F.plain(seed, (110, 114, 110), 0.05, 0.2)
    if open_:
        area(t, 0, 0, N, 12, col=(96, 100, 96))
        for x in range(0, N, 6):
            area(t, x, 0, x + 1, 12, mul=(0.7, 0.7, 0.7))
        area(t, 0, 12, N, N, col=(20, 20, 20), alpha=0)
    else:
        for x in (16, 32, 48):
            area(t, x - 1, 0, x + 1, N, col=(196, 160, 40))
        for y in (20, 42):
            area(t, 0, y, N, y + 2, col=(196, 160, 40))
    t.grime(0.3)
    return t


def belt_tex(seed):
    """The belt: black rubber with its cleats across, the bags' scuffs."""
    t = F.plain(seed, (34, 34, 34), 0.05, 0.25)
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 2, (58, 58, 58), dh=0.3)
    t.grime(0.2)
    return t


def gpu_side(seed):
    t = painted(seed, (226, 186, 40), 0.35, 0.02)
    F.gap(t, 6, 6, 58, 50)
    for y in range(12, 44, 4):
        area(t, 12, y, 52, y + 2, col=(40, 40, 30), dh=-0.3)
    from bunker import stencil
    stencil(t, "400 HZ", (30, 30, 30), 8, x=8, y=52, w=48, h=10, chip=0.3)
    return t


def hazard(seed):
    t = painted(seed, (226, 186, 40))
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) // 11) % 2 == 0] = (30, 30, 30)
    t.grime(0.3, (60, 54, 44))
    return t


def slide_tex(seed, rgb, seams=True):
    """The slide's coated fabric: a soft sheen, the welded seams across it, scuffs and grime."""
    t = F.plain(seed, rgb, 0.03, 0.55)
    if seams:
        for k in range(0, N, N // 4):
            area(t, 0, k, N, k + 2, tuple(int(c * 0.8) for c in rgb))
    else:
        for k in range(0, N, N // 2):
            area(t, k, 0, k + 1, N, tuple(int(c * 0.85) for c in rgb))
    t.grime(0.3, (70, 64, 54))
    return t


def textures():
    return {
        "rp_clad": lambda: clad(41001),
        "rp_clad_band": lambda: clad(41002, True),
        "rp_lining": lambda: lining(41003),
        "rp_tread": lambda: tread(41004),
        "rp_ceiling": lambda: ceiling(41005),
        "rp_ceiling_lamp": lambda: ceiling(41006, 1),
        "rp_ceiling_lit": lambda: ceiling(41007, 2),
        "rp_bellows": lambda: bellows(41008),
        "rp_glass": lambda: glass(41009),
        "rp_roof": lambda: roof_top(41010),
        "rp_under": lambda: under(41011),
        "rp_console": lambda: console(41012),
        "rp_diamond": lambda: diamond(41013),
        "rp_navy": lambda: painted(41014, NAVY, 0.3, 0.02),
        "rp_yellow": lambda: painted(41015, (226, 186, 40), 0.3, 0.02),
        "rp_white": lambda: painted(41016, (226, 226, 220), 0.35, 0.01),
        "rp_orange": lambda: painted(41017, (214, 96, 30), 0.35, 0.02),
        "rp_navy_door": lambda: cab_side(41018, NAVY),
        "rp_white_door": lambda: cab_side(41019, (226, 226, 220)),
        "rp_yellow_door": lambda: cab_side(41020, (226, 186, 40)),
        "rp_windscreen": lambda: windscreen(41021),
        "rp_grille_white": lambda: grille(41022, (226, 226, 220)),
        "rp_grille_navy": lambda: grille(41023, NAVY),
        "rp_uld": lambda: uld(41024, False),
        "rp_uld_side": lambda: uld(41025, True),
        "rp_uld_door": lambda: uld_door(41026),
        "rp_uld_door_open": lambda: uld_door(41027, True),
        "rp_belt": lambda: belt_tex(41028),
        "rp_gpu": lambda: gpu_side(41029),
        "rp_hazard": lambda: hazard(41030),
        "rp_seat": lambda: F.plain(41031, (30, 30, 32), 0.05, 0.35),
        "rp_slide": lambda: slide_tex(41032, (228, 128, 34)),
        "rp_slide_bed": lambda: slide_tex(41033, (214, 200, 160), seams=False),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

FULL = {d: [0, 0, 16, 16] for d in ("north", "south", "east", "west", "up", "down")}


def turn(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": [round(v, 3) for v in origin]}


def faces(side, **over):
    """Every face side, but those named (up=, north=, ...)."""
    f = {d: side for d in ("north", "south", "east", "west", "up", "down")}
    f.update({k: v for k, v in over.items() if v})
    return f


def skin(x0, y0, z0, x1, y1, z1, tex):
    """A box with each face's texture stretched over the whole face (a door or a grille drawn on one tile)."""
    return [x0, y0, z0, x1, y1, z1, tex, FULL]


def wheel(x0, x1, z, r=4.0, y=None, hub=STEEL):
    """A wheel on an axle along x at z: the tyre an octagon of two boxes, the hub."""
    cy = r if y is None else y
    a = r * 0.4142
    t = turn("x", 45, (8, cy, z))
    tyre = [[x0, cy - r, z - a, x1, cy + r, z + a, TYRE], [x0, cy - a, z - r, x1, cy + a, z + r, TYRE]]
    return tyre + [q + [None, t] for q in tyre] + [[x0 - 0.2, cy - r * 0.5, z - r * 0.5, x1 + 0.2, cy + r * 0.5, z + r * 0.5, hub]]


# ---- the jet bridge

def jb_floor():
    return [[0, 0, 0, 16, 16, 16, faces("rp_clad", up="rp_tread", down="rp_under")]]


def jb_wall(window=False, band=False):
    """A side: the skin on the north, the lining inside, the handrail; the glass band in the middle
    when window; the livery's navy stripe when band."""
    if not window:
        return [[0, 0, 0, 16, 16, 2, faces("rp_clad", north="rp_clad_band" if band else "rp_clad", south="rp_lining")],
                [0, 13, 2, 16, 14, 3, STEEL]]
    f_lo = faces("rp_clad", north="rp_clad", south="rp_lining")
    return [[0, 0, 0, 16, 5, 2, f_lo], [0, 12, 0, 16, 16, 2, f_lo],
            [0, 5, 0.6, 16, 12, 1.4, "rp_glass"],
            [0, 5, 0, 1, 12, 2, STEEL], [15, 5, 0, 16, 12, 2, STEEL],
            [0, 4.5, 2, 16, 5.5, 3, STEEL]]


def jb_corner():
    """An outside corner: the sides on the north and the west."""
    f = faces("rp_clad", north="rp_clad", south="rp_lining", west="rp_clad", east="rp_lining")
    return [[0, 0, 0, 16, 16, 2, f], [0, 0, 2, 2, 16, 16, f]]


def jb_roof(lamp=None):
    under_tex = "rp_ceiling" if lamp is None else "rp_ceiling_lit" if lamp else "rp_ceiling_lamp"
    return [[0, 12, 0, 16, 16, 16, faces("rp_clad", up="rp_roof", down=under_tex)]]


def jb_eave():
    """The roof over a wall: the roof slab, its lip reaching out past the wall on the north."""
    return [[0, 12, 0, 16, 16, 16, faces("rp_clad", up="rp_roof", down="rp_ceiling")],
            [0, 13, -1.5, 16, 16.5, 0, faces("rp_clad", up="rp_roof")]]


def jb_bellows(top=False):
    """The canopy's rubber folds round the door: a side's column, or the top's beam."""
    if top:
        b = [[0, 8, 0, 16, 16, 6, "rp_bellows"]]
        b += [[0, 7.4 + k * 0.1, 1 + k * 1.6, 16, 8, 1.8 + k * 1.6, BLACK] for k in range(3)]
        return b
    b = [[0, 0, 0, 7, 16, 6, "rp_bellows"]]
    b += [[7, 0, 0.5 + k * 1.8, 8, 16, 1.4 + k * 1.8, BLACK] for k in range(3)]
    return b


def jb_console():
    desk = faces("rp_white", up="rp_console", north="rp_white")
    return [[2, 0, 3, 14, 10, 8, desk], [2, 10, 3, 14, 11, 9, "rp_console"],
            [7, 11, 5, 8, 15, 6, BLACK], [6.5, 15, 4.5, 8.5, 16, 6.5, "rp_orange"],
            [10, 11, 5, 13, 11.6, 7, BLACK]]


def jb_leg(lower=False):
    w = 3.5 if lower else 2.5
    return [[8 - w, 0, 8 - w, 8 + w, 16, 8 + w, faces("rp_white", up=STEEL, down=STEEL)],
            [8 - w - 0.4, 14, 8 - w - 0.4, 8 + w + 0.4, 16, 8 + w + 0.4, STEEL]]


def jb_bogie():
    """The drive bogie: the beam across, the two wheels, the motor housings, the column's foot."""
    b = [[1, 6, 6, 15, 9, 10, "rp_white"], [4.5, 9, 4.5, 11.5, 16, 11.5, faces("rp_white", up=STEEL)],
         [2, 2, 3.5, 5, 6, 12.5, faces("rp_yellow", north="rp_hazard", south="rp_hazard")],
         [11, 2, 3.5, 14, 6, 12.5, faces("rp_yellow", north="rp_hazard", south="rp_hazard")]]
    b += wheel(-1.5, 2, 8, 4) + wheel(14, 17.5, 8, 4)
    return b


def jb_column():
    """The rotunda's column: an octagon of two boxes, a band at the top."""
    r, a = 5.5, 5.5 * 0.4142
    sq = [[8 - r, 0, 8 - a, 8 + r, 16, 8 + a, "rp_white"], [8 - a, 0, 8 - r, 8 + a, 16, 8 + r, "rp_white"]]
    return sq + [q + [None, turn("y", 45, (8, 8, 8))] for q in sq]


# ---- the stair truck

def stringers():
    """The two stringers and the handrails on them, rising north at 45 degrees through the cell."""
    t = turn("x", 45, (8, 8, 8))
    L = 11.3  # half the cell's diagonal
    b = []
    for x0, x1 in ((0, 1.5), (14.5, 16)):
        b.append([x0, 6, 8 - L, x1, 9, 8 + L, "rp_navy", None, t])
        b.append([x0 + 0.2, 21.5, 8 - L, x1 - 0.2, 22.5, 8 + L, "rp_yellow", None, t])
    return b


def as_step():
    """Two treads of tread plate between the stringers, the rails over them, open risers."""
    tread_f = faces("rp_navy", up="rp_diamond")
    b = [[1.5, 7, 8, 14.5, 8, 16, tread_f], [1.5, 15, 0, 14.5, 16, 8, tread_f],
         [1.5, 6, 15, 14.5, 7, 16, "rp_navy"], [1.5, 14, 7, 14.5, 15, 8, "rp_navy"]]
    b += stringers()
    for x in (0.4, 14.6):
        b += [[x, 8, 11.5, x + 1, 22, 12.5, "rp_yellow"]]
    return b


def as_top():
    """The platform: tread plate deck on its frame, the rubber bumper at the plane's end."""
    return [[0, 13, 0, 16, 16, 16, faces("rp_navy", up="rp_diamond", down="rp_under")],
            [0, 9, 0, 2, 13, 16, "rp_navy"], [14, 9, 0, 16, 13, 16, "rp_navy"],
            [0, 10, 0, 16, 16, 1.5, faces(RUBBER, up=RUBBER)]]


def as_rail():
    b = []
    for x in (0.2, 14.8):
        b += [[x, 0, 1, x + 1, 15, 2, "rp_yellow"], [x, 0, 14, x + 1, 15, 15, "rp_yellow"],
              [x, 14, 1, x + 1, 15, 15, "rp_yellow"], [x + 0.2, 7, 1.5, x + 0.8, 7.6, 14.5, "rp_yellow"],
              [x - 0.2, 0, 0, x + 1.2, 2.5, 16, "rp_navy"]]
    return b


def as_post():
    return [[0.4, 0, 1, 1.4, 16, 2, "rp_navy"], [14.6, 0, 1, 15.6, 16, 2, "rp_navy"],
            [0.4, 0, 14, 1.4, 16, 15, "rp_navy"], [14.6, 0, 14, 15.6, 16, 15, "rp_navy"]]


def as_roof():
    return [[-1, 0, -1, 17, 2, 17, faces("rp_white", up="rp_roof", down="rp_white")],
            [-1, 2, 7.5, 17, 3.5, 8.5, "rp_white"]]


def as_cab(hi):
    """The cab under the platform: the windscreen north, the doors either side; the roof when hi."""
    if not hi:
        b = [[0, 3, 0, 16, 16, 16, faces("rp_navy", north="rp_grille_navy", east="rp_navy_door", west="rp_navy_door")],
             [-0.5, 3, -0.5, 16.5, 5, 1.5, BLACK]]
        b += wheel(-1, 2, 5, 4.5) + wheel(14, 17, 5, 4.5)
        return b
    return [[0, 0, 4, 16, 10, 16, faces("rp_navy", north="rp_windscreen", east="rp_windscreen", west="rp_windscreen")],
            [0, 0, 1, 16, 8, 4, faces("rp_navy", north="rp_windscreen", up="rp_windscreen")],
            [0, 10, 4, 16, 11, 16, "rp_white"], [3, 11, 6, 13, 12.5, 8, "rp_orange"]]


def as_bed(wheels):
    b = [[1, 4, 0, 4, 7, 16, "rp_navy"], [12, 4, 0, 15, 7, 16, "rp_navy"], [1, 4, 6, 15, 6, 10, "rp_navy"],
         [4, 7, 0, 12, 9, 16, faces(STEEL, up="rp_diamond")]]
    if wheels:
        b += wheel(-1, 2, 8, 4.5) + wheel(14, 17, 8, 4.5)
    return b


def as_lift():
    t1, t2 = turn("x", 45, (8, 8, 8)), turn("x", -45, (8, 8, 8))
    return [[2, 7, -3, 3.5, 9, 19, "rp_navy", None, t1], [12.5, 7, -3, 14, 9, 19, "rp_navy", None, t1],
            [2, 7, -3, 3.5, 9, 19, "rp_navy", None, t2], [12.5, 7, -3, 14, 9, 19, "rp_navy", None, t2],
            [1, 7, 7, 15, 9, 9, STEEL]]


# ---- the tug, the dollies, the containers

def tug(front):
    if front:
        b = [[1, 3, 2, 15, 11, 16, faces("rp_white", north="rp_grille_white", up="rp_white")],
             [0.5, 3, 0.5, 15.5, 6, 2, BLACK]]
        b += wheel(-1, 2, 6, 4) + wheel(14, 17, 6, 4)
        return b
    b = [[1, 3, 0, 15, 8, 14, faces("rp_white", east="rp_white_door", west="rp_white_door")],
         [3, 8, 4, 13, 10, 11, faces("rp_seat")], [3, 10, 10, 13, 18, 12, faces("rp_seat")],
         [7, 8, 0, 9, 14, 2, BLACK], [5, 14, 0, 11, 15, 2.5, BLACK],
         [6, 4, 14, 10, 6, 17, STEEL]]
    for x in (1, 14):
        b += [[x, 8, 1, x + 1, 26, 2, BLACK], [x, 8, 13, x + 1, 26, 14, BLACK]]
    b += [[0.5, 26, 0, 15.5, 27.5, 15, faces("rp_white", up="rp_roof")]]
    b += wheel(-1, 2, 10, 4) + wheel(14, 17, 10, 4)
    return b


def bag_box(x0, y0, z0, x1, y1, z1, col):
    return [x0, y0, z0, x1, y1, z1, f"ap_bag_{col}"]


def dolly(front, bags=False):
    """A baggage cart: the bed on its frame, the side rails and canvas curtains, the tow bar."""
    b = [[0.5, 4, 0, 15.5, 5, 16, faces(STEEL, up="rp_diamond")],
         [0.5, 3, 0, 2, 4, 16, "rp_orange"], [14, 3, 0, 15.5, 4, 16, "rp_orange"]]
    for x in (0.5, 14.5):
        b += [[x, 5, 0, x + 1, 22, 1, "rp_orange"], [x, 21, 0, x + 1, 22, 16, "rp_orange"],
              [x + 0.2, 5, 0.5, x + 0.8, 21, 15.5, "rp_uld_door"]]
    b += [[0, 22, -0.5, 16, 23, 16.5, faces("rp_orange", up="rp_uld_door")]]
    if front:
        b += [[0.5, 5, 0, 15.5, 22, 1, faces("rp_orange")]]
        b += [[7, 3, -12, 9, 4, 0, STEEL], [6, 2, -13, 10, 5, -11, STEEL]]
        b += wheel(-1, 1.5, 5, 3) + wheel(14.5, 17, 5, 3)
    else:
        b += [[0.5, 5, 15, 15.5, 22, 16, faces("rp_orange")]]
        b += wheel(-1, 1.5, 11, 3) + wheel(14.5, 17, 11, 3)
    if bags:
        b += [bag_box(2, 5, 1, 9, 10, 9, "black"), bag_box(9.5, 5, 2, 14, 13, 7, "navy"),
              bag_box(2.5, 10, 2, 8, 14, 8, "red"), bag_box(3, 5, 10, 13, 9, 15, "grey"),
              bag_box(9, 5, 8, 13.5, 11, 14, "tan"), bag_box(4, 9, 10, 10, 13, 15, "green")]
    return b


def dolly_tipped():
    """A cart blown over on its side (the rails down to the west)."""
    b = [[0, 0, 0, 16, 1.5, 16, "rp_orange"], [4, 1.5, 0, 5, 16, 16, faces(STEEL, east="rp_diamond")],
         [0, 1.5, 0, 4, 2.5, 16, "rp_orange"]]
    b += [[5, 3, 2, 12, 9, 9, "ap_bag_black"], [6, 2, 10, 15, 6, 14, "ap_bag_red"]]
    return b


def uld_box(door="closed"):
    """An LD3: aluminium, the sloped corner at the bottom on the east, the curtain door north."""
    door_tex = "rp_uld_door_open" if door != "closed" else "rp_uld_door"
    f = faces("rp_uld", north=door_tex, east="rp_uld_side", west="rp_uld_side", up="rp_uld")
    b = [[0, 1, 0, 11, 16, 16, f], [11, 6, 0, 16, 16, 16, f], [0, 0, 0, 11, 1, 16, STEEL],
         # the slope: a slab turned across the corner
         [11, 0.4, 0, 18.1, 1.4, 16, "rp_uld", None, turn("z", 45, (11, 1.4, 8))]]
    if door == "spill":
        b += [[2, 1, -9, 9, 5, -1, "ap_bag_navy"], [9, 0, -12, 15, 3, -5, "ap_bag_red"]]
    return b


# ---- the belt loader

def bl_belt(lo):
    """A cell's run of the belt rising north 8 in 16 (lo the run from 4 to 12, else 12 to 20), the
    side rails along it."""
    ang = math.degrees(math.atan2(8, 16))
    y = 8 if lo else 16
    t = turn("x", -ang, (8, y, 8))
    L = 8 * math.hypot(16, 8) / 16 + 0.1
    return [[2, y - 1, 8 - L, 14, y, 8 + L, faces("rp_white", up="rp_belt"), None, t],
            [1, y - 1.5, 8 - L, 2, y + 2, 8 + L, "rp_white", None, t],
            [14, y - 1.5, 8 - L, 15, y + 2, 8 + L, "rp_white", None, t]]


def bl(k):
    if k == 0:
        b = bl_belt(True) + [[1, 2, 0, 15, 4, 16, "rp_white"]]
        b += wheel(-1, 2, 6, 3.5) + wheel(14, 17, 6, 3.5)
        return b
    if k == 1:
        b = bl_belt(False) + [[1, 2, 0, 15, 4, 16, "rp_white"],
                              [-2, 2, 2, 1, 12, 14, faces("rp_white", west="rp_windscreen", north="rp_windscreen")],
                              [-2, 12, 2, 1, 13, 14, "rp_white"]]
        b += wheel(-1, 2, 10, 3.5) + wheel(14, 17, 10, 3.5)
        return b
    if k == 2:
        return bl_belt(True) + [[7, -12, 6, 9, 7, 8, "rp_yellow"]]
    return bl_belt(False) + [[1, 18, 0, 15, 20, 2, faces(RUBBER)], [2, 20, 0, 14, 21, 1, "rp_hazard"]]


def gpu():
    f = faces("rp_yellow", east="rp_gpu", west="rp_gpu")
    b = [[1, 3, 2, 15, 14, 14, f], [1, 14, 2, 15, 15, 14, "rp_yellow"],
         [7, 8, -1, 9, 10, 2, BLACK], [8, 0, -8, 9, 1, -1, BLACK], [5, 0.2, -10, 12, 1.6, -8, BLACK]]
    b += wheel(-1, 1.5, 4, 3) + wheel(14.5, 17, 4, 3) + wheel(-1, 1.5, 12, 3) + wheel(14.5, 17, 12, 3)
    return b


def chocks():
    b = []
    for x in (2, 10):
        b += [[x, 0, 4, x + 4, 3, 8, "rp_yellow"], [x, 0, 8, x + 4, 1.5, 10, "rp_yellow"]]
    b += [[6, 0.4, 5.5, 10, 0.8, 6.5, "rp_white"]]
    return b


def wand():
    return [[3, 0, 4, 4.5, 1.5, 13, "rp_orange"], [3, 0, 12, 4.5, 1.5, 15, BLACK],
            [9, 0, 2, 14, 1.5, 3.5, "rp_orange"], [6, 0, 2, 9, 1.5, 3.5, BLACK]]


# ---- the escape slide off the back door, falling north to south down to the apron (rotate=facing:
# the model rises north, so it is set facing south to run down northward)

def slide():
    """A length of the slide at 45 degrees: the bed between the two fat tubes, the tube under it."""
    t = turn("x", 45, (8, 8, 8))
    L = 11.3
    return [[2.5, 6.6, 8 - L, 13.5, 8, 8 + L, "rp_slide_bed", None, t],
            [0, 6, 8 - L, 3.5, 11, 8 + L, "rp_slide", None, t], [12.5, 6, 8 - L, 16, 11, 8 + L, "rp_slide", None, t],
            [4, 3.5, 8 - L, 12, 6.6, 8 + L, "rp_slide", None, t]]


def slide_top():
    """The head of the slide on the sill outside the door (the door to the north): the girt bar,
    the flat head, the tubes rising round it, the arch over the doorway."""
    return [[2.5, 0, 0, 13.5, 2, 16, faces("rp_slide", up="rp_slide_bed")],
            [0, 0, 0, 3.5, 8, 16, "rp_slide"], [12.5, 0, 0, 16, 8, 16, "rp_slide"],
            [0, 8, 0, 3.5, 30, 3.5, "rp_slide"], [12.5, 8, 0, 16, 30, 3.5, "rp_slide"],
            [0, 30, 0, 16, 33.5, 3.5, "rp_slide"], [1, 1, -0.5, 15, 2.5, 0.5, "st_galv"]]


def slide_foot():
    """The run-out flat on the apron where the slide comes down (from the north), the bumper tube
    across its end."""
    return [[2.5, 0, 0, 13.5, 2, 16, faces("rp_slide", up="rp_slide_bed")],
            [0, 0, 0, 3.5, 5, 16, "rp_slide"], [12.5, 0, 0, 16, 5, 16, "rp_slide"],
            [0, 0, 12.5, 16, 6, 16, "rp_slide"]]


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound="METAL", rotate="facing", decor=False, light=None, **extra):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    add("rp_jb_floor", jb_floor(), "Jet Bridge Floor", shape=[[0, 0, 0, 16, 16, 16]])
    add("rp_jb_wall", jb_wall(), "Jet Bridge Wall", shape=[[0, 0, 0, 16, 16, 2]])
    add("rp_jb_wall_band", jb_wall(band=True), "Jet Bridge Wall", shape=[[0, 0, 0, 16, 16, 2]])
    add("rp_jb_window", jb_wall(True), "Jet Bridge Window", shape=[[0, 0, 0, 16, 16, 2]])
    add("rp_jb_corner", jb_corner(), "Jet Bridge Wall", shape=[[0, 0, 0, 16, 16, 2], [0, 0, 0, 2, 16, 16]])
    add("rp_jb_roof", jb_roof(), "Jet Bridge Roof", shape=[[0, 12, 0, 16, 16, 16]])
    add("rp_jb_roof_lamp", jb_roof(False), "Jet Bridge Roof", shape=[[0, 12, 0, 16, 16, 16]])
    add("rp_jb_roof_lamp_lit", jb_roof(True), "Jet Bridge Roof", shape=[[0, 12, 0, 16, 16, 16]], light=12)
    add("rp_jb_eave", jb_eave(), "Jet Bridge Roof", shape=[[0, 12, 0, 16, 16, 16]])
    add("rp_jb_bellows", jb_bellows(), "Jet Bridge Canopy", sound="WOOL", shape=[[0, 0, 0, 8, 16, 6]])
    add("rp_jb_bellows_top", jb_bellows(True), "Jet Bridge Canopy", sound="WOOL", shape=[[0, 8, 0, 16, 16, 6]])
    add("rp_jb_console", jb_console(), "Jet Bridge Controls", shape=[[2, 0, 3, 14, 11, 9]])
    add("rp_jb_leg", jb_leg(), "Jet Bridge Leg", shape=[[5.5, 0, 5.5, 10.5, 16, 10.5]])
    add("rp_jb_leg_lo", jb_leg(True), "Jet Bridge Leg", shape=[[4.5, 0, 4.5, 11.5, 16, 11.5]])
    add("rp_jb_bogie", jb_bogie(), "Jet Bridge Wheels", shape=[[0, 0, 3, 16, 16, 13]])
    add("rp_jb_column", jb_column(), "Rotunda Column", shape=[[2.5, 0, 2.5, 13.5, 16, 13.5]])
    add("rp_as_step", as_step(), "Air Stairs", shape=[[0, 0, 8, 16, 8, 16], [0, 0, 0, 16, 16, 8]])
    add("rp_as_top", as_top(), "Air Stairs Platform", shape=[[0, 0, 0, 16, 16, 16]])
    add("rp_as_rail", as_rail(), "Air Stairs Rail", shape=[[0, 0, 0, 1.5, 16, 16], [14.5, 0, 0, 16, 16, 16]])
    add("rp_as_post", as_post(), "Air Stairs Canopy", decor=True)
    add("rp_as_roof", as_roof(), "Air Stairs Canopy", shape=[[0, 0, 0, 16, 2, 16]])
    add("rp_as_cab_lo", as_cab(False), "Stair Truck", shape=[[0, 0, 0, 16, 16, 16]])
    add("rp_as_cab_hi", as_cab(True), "Stair Truck", shape=[[0, 0, 1, 16, 12.5, 16]])
    add("rp_as_bed", as_bed(False), "Stair Truck", shape=[[1, 0, 0, 15, 9, 16]])
    add("rp_as_bed_wheels", as_bed(True), "Stair Truck", shape=[[1, 0, 0, 15, 9, 16]])
    add("rp_as_lift", as_lift(), "Stair Truck", decor=True)
    add("rp_tug_front", tug(True), "Baggage Tug", shape=[[0, 0, 0, 16, 11, 16]])
    add("rp_tug_rear", tug(False), "Baggage Tug", shape=[[0, 0, 0, 16, 16, 16]])
    for bags in (False, True):
        sfx = "_bags" if bags else ""
        add("rp_dolly_front" + sfx, dolly(True, bags), "Baggage Cart", shape=[[0, 0, 0, 16, 16, 16]])
        add("rp_dolly_rear" + sfx, dolly(False, bags), "Baggage Cart", shape=[[0, 0, 0, 16, 16, 16]])
    add("rp_dolly_tipped", dolly_tipped(), "Baggage Cart", shape=[[0, 0, 0, 16, 9, 16]])
    for door in ("closed", "open", "spill"):
        add("rp_uld" + ("" if door == "closed" else "_" + door), uld_box(door), "Cargo Container",
            shape=[[0, 0, 0, 16, 16, 16]])
    for k in range(4):
        add(f"rp_bl_{k}", bl(k), "Belt Loader", shape=[[0, 0, 0, 16, 12 if k % 2 == 0 else 16, 16]])
    add("rp_gpu", gpu(), "Ground Power Unit", shape=[[0, 0, 1, 16, 15, 15]])
    add("rp_chocks", chocks(), "Wheel Chocks", decor=True)
    add("rp_wand", wand(), "Marshalling Wands", sound="WOOD", decor=True)
    add("rp_slide", slide(), "Escape Slide", sound="WOOL", shape=[[0, 0, 8, 16, 8, 16], [0, 0, 0, 16, 16, 8]])
    add("rp_slide_top", slide_top(), "Escape Slide", sound="WOOL", shape=[[0, 0, 0, 16, 2, 16]])
    add("rp_slide_foot", slide_foot(), "Escape Slide", sound="WOOL", shape=[[0, 0, 0, 16, 2, 16]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("rp_")}
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} ramp blocks in {path}")


if __name__ == "__main__":
    main()
