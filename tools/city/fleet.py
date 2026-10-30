"""The an_ kit: the rest of Grayhaven International's aircraft, after the Air Guard's fighters and
helicopters went to Caldwell Army Airfield (Josiah, 2026-10-30: "replace the airport ones with
airliners of various types, private and commercial"). Built the way tools/city/airliner.py builds
the trijet, from its own pieces (the fuselage's rings of panels turned to the curve, the wings in
airfoil strips, the podded engines with their fans, the gear), each type made to its own sizes
from refs/airport/apron-airliners and blown up like the trijet so the cabin keeps its clear
height over the aisle; every one walk-in, its doors open on the side the jet bridge or the stairs
come to:

    twinjet    the narrowbody twin under the wings (after a 737-800): 60 nose to tail, 54 across
    widebody   the long-haul twin-aisle twin (after a 777-200): 92 nose to tail, 88 across
    turboprop  the high-winged commuter with two props and a T-tail (after an ATR 72): 41 long
    bizjet     the private jet, engines on the tail under a T-tail (after a Challenger 350): 31 long

Each type is painted in an airline's colours from the terminal's counters (tools/city/airport.py
AIRLINES; made-up, as are the registrations), or a private owner's; one type's liveries share the
blocks the paint doesn't touch. Every distinct block is one generated block an_s<n>; the plane
faces north (the nose at z = 0), x across with the captain's side to the west, y = 0 the apron's
surface block. The cabin's fittings are the trijet's named blocks (al_seat, al_lav_*, ...). Each
plane's layout goes to data/zomboidcraft/airliner/<type>_<livery>.txt, its first line "#! "
followed by its doors and size for world/county/Airliner.java.

    python3 tools/city/fleet.py [--dump DIR] [--only TYPE]   rewrites the an_ entries in blocks.json
                                                             and the layouts; --dump writes a preview
                                                             dump of each plane to DIR/<plane>

photo_textures.py draws the textures (fleet.register()).
"""
import argparse
import json
import math
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import airliner as A  # noqa: E402
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
from airliner import Box, Shell, ring, discfill, planform, oval, gear, airfoil, all_faces, FULL  # noqa: E402
from furniture import area, disc as tdisc  # noqa: E402
from school import Pic  # noqa: E402

N = P.N
HERE = Path(__file__).parent
ROOT = HERE.parent.parent
LAYOUTS = ROOT / "src/main/resources/data/zomboidcraft/airliner"
BOLD = A.BOLD

# ---------------------------------------------------------------- liveries

# key -> (title on the side, or None; the main colour: the cheat line, the fin; the pin stripe under it)
LIVERIES = {
    "coastal": ("COASTAL", (18, 108, 116), (214, 172, 70)),
    "skyline": ("SKYLINE", (226, 112, 28), (44, 44, 48)),
    "north": ("NORTHSTAR", (176, 30, 36), (186, 190, 196)),
    "private": (None, (34, 40, 56), (184, 146, 72)),
    "charter": (None, (112, 24, 40), (196, 198, 202)),
}
WHITE, BELLY = A.WHITE, A.BELLY


class Type:
    """One type's sizes (blocks). The fuselage is a circle of radius R about y = C, x = 0, the cabin
    floor's top at C - 1; the nose closes in over NOSE, the barrel runs to TAIL0, the tail cone to LEN.
    The bands on the side sit where the trijet's do over its middle, so the doors' pictures fit all."""

    def __init__(self, key, **kw):
        self.key = key
        self.R, self.C = 4.0, 8.0
        self.NOSE, self.TAIL0, self.LEN, self.AFT = 9, 46, 60, 51
        self.cone_up, self.cone_tip = 0.9, 1.1
        self.doors, self.exits = [], []
        self.windows = (14, 44)
        self.title_z, self.reg_z = (13, 25), (40, 44)
        self.bins = (14, 44)
        self.liveries = ()
        self.__dict__.update(kw)
        self.FLOOR = int(self.C) - 1
        # the side's bands, from the fuselage's middle as on the trijet
        c = self.C
        self.WIN = (c + 0.05, c + 0.65)
        self.CHEAT = (c - 0.88, c - 0.38)
        self.PIN = (c - 0.3, c - 0.2)
        self.BELLY_Y = c - 2.4
        self.TITLE_Y = (c + 1.25, c + 2.55) if self.R >= 3.5 else (c + 1.0, c + 1.9)
        self.REG_Y = (c + 1.2, c + 1.9) if self.R >= 3.5 else (c + 0.95, c + 1.6)
        # the ceiling: over the bins in the big ones, under the crown in the small
        self.ceil = c + 3.02 if self.R >= 3.9 else c + self.R - 0.72


TYPES = {
    "twinjet": Type("twinjet", R=4.0, C=8.0, NOSE=9, TAIL0=46, LEN=60, AFT=51,
                    doors=[(-1, 10, 2, True), (1, 10, 2, False), (-1, 46, 2, True), (1, 46, 2, False)],
                    exits=[(-1, 28), (1, 28), (-1, 29), (1, 29)], windows=(14, 45), title_z=(13, 25),
                    reg_z=(40, 44), bins=(14, 45), liveries=("coastal", "skyline")),
    "widebody": Type("widebody", R=6.5, C=10.0, NOSE=13, TAIL0=70, LEN=92, AFT=82,
                     cone_up=1.6, cone_tip=1.4,
                     doors=[(-1, 14, 2, True), (1, 14, 2, False), (-1, 33, 2, False), (1, 33, 2, False),
                            (-1, 56, 2, True), (1, 56, 2, False), (-1, 78, 2, False), (1, 78, 2, False)],
                     windows=(18, 77), title_z=(20, 36), reg_z=(64, 69), bins=(18, 77), liveries=("north",)),
    "turboprop": Type("turboprop", R=3.4, C=6.0, NOSE=7, TAIL0=30, LEN=41, AFT=34, cone_up=1.1, cone_tip=0.9,
                      doors=[(-1, 31, 2, True), (1, 8, 2, False)], windows=(10, 29), title_z=(10, 21),
                      reg_z=(24, 28), bins=(0, 0), liveries=("coastal",)),
    "bizjet": Type("bizjet", R=2.8, C=5.0, NOSE=7, TAIL0=21, LEN=31, AFT=23, cone_up=0.6, cone_tip=0.8,
                   doors=[(-1, 7, 2, True)], windows=(10, 21), title_z=(0, 0), reg_z=(17, 21), bins=(0, 0),
                   liveries=("private", "charter")),
}

T = TYPES["twinjet"]   # the type being built
LV = "coastal"         # and its livery


def L(name):
    """A livery texture's name for this plane: an_<livery>_<name>."""
    return f"an_{LV}_{name}"


# ---------------------------------------------------------------- shape

def nose(z):
    """The fuselage's centre height and radius at z (blocks from the nose)."""
    if z < T.NOSE:
        s = min(1.0, max(0.0, z / T.NOSE))
        return T.C - 0.325 * T.R * (1 - s) ** 2, T.R * max(0.0, 1 - (1 - s) ** 2) ** 0.75
    if z <= T.TAIL0:
        return T.C, T.R
    s = min(1.0, (z - T.TAIL0) / (T.LEN - T.TAIL0))
    top = T.C + T.R - T.cone_up * s * s
    bottom = T.C - T.R + (2 * T.R - T.cone_up - T.cone_tip) * s ** 1.6
    return (top + bottom) / 2, (top - bottom) / 2


def door_at(side, z):
    for d in T.doors:
        if d[0] == side and d[1] <= z < d[1] + d[2]:
            return d
    return None


def skin(x, y, z, th):
    side = -1 if x < 0 else 1
    k = T.R / 4
    top = T.C + 1.6 if T.bins[1] > T.bins[0] else T.ceil + 0.2
    inside = "al_lining" if T.FLOOR - 0.2 < y < top and T.NOSE <= z < T.AFT + 1 else "al_dark"
    if z < T.NOSE:
        inside = "al_ck_lining" if y > T.FLOOR - 0.2 and z > T.NOSE / 3 else "al_dark"
        a = th if th <= 180 else 360 - th
        f = z / T.NOSE
        if 0.267 <= f < 0.489 and 6 <= a <= 50 and y > T.C + 0.3 * k:
            return ("al_ck_glass", "al_ck_glass_in", None, None, True)
        if 0.489 <= f < 0.711 and 52 <= a <= 70 and T.C + 0.4 * k < y < T.C + 1.7 * k:
            return ("al_ck_glass", "al_ck_glass_in", None, None, True)
        if f < 0.178:
            return ("al_radome", inside, None, None, False)
    zi = math.floor(z)
    d = door_at(side, zi)
    if d and T.FLOOR <= y < T.FLOOR + 3 and abs(x) > T.R - 1:
        kk = zi - d[1] if side < 0 else d[1] + d[2] - 1 - zi
        j = math.floor(y)
        return (L(f"door_{kk}_{T.FLOOR + 2 - j}"), inside, ("band", j, j + 1), None, False)
    if (side, zi) in T.exits and T.FLOOR <= y < T.FLOOR + 2 and abs(x) > T.R - 1:
        j = math.floor(y)
        return (L(f"exit_{T.FLOOR + 1 - j}"), inside, ("band", j, j + 1), None, False)
    if T.windows[0] <= zi < T.windows[1] and T.WIN[0] <= y < T.WIN[1] and abs(x) > T.R - 1:
        return ("al_win", "al_win_in", ("band",) + T.WIN, ("band",) + T.WIN, True)
    if LIVERIES[LV][0] and T.title_z[0] <= zi < T.title_z[1] and T.TITLE_Y[0] <= y < T.TITLE_Y[1] and abs(x) > T.R - 1.5:
        kk = zi - T.title_z[0] if side < 0 else T.title_z[1] - 1 - zi
        return (L(f"{T.key}_title_{kk}"), inside, ("band",) + T.TITLE_Y, None, False)
    if T.reg_z[0] <= zi < T.reg_z[1] and T.REG_Y[0] <= y < T.REG_Y[1] and abs(x) > T.R - 1.5:
        kk = zi - T.reg_z[0] if side < 0 else T.reg_z[1] - 1 - zi
        return (L(f"{T.key}_reg_{kk}"), inside, ("band",) + T.REG_Y, None, False)
    if T.CHEAT[0] <= y < T.CHEAT[1]:
        return (L("main"), inside, None, None, False)
    if T.PIN[0] <= y < T.PIN[1]:
        return (L("pin"), inside, None, None, False)
    if y < T.BELLY_Y:
        return ("al_belly", inside, None, None, False)
    return ("al_paint", inside, None, None, False)


def door_skip(i, j, z):
    """An open door's opening: the skin's blocks on that side over the door's three rows left out."""
    for side, z0, w, open_ in T.doors:
        if open_ and z0 <= z < z0 + w and T.FLOOR <= j <= T.FLOOR + 2:
            if (side < 0 and i + 1 <= -(T.R - 1.6) + 1e-9) or (side > 0 and i >= T.R - 1.6 - 1e-9):
                return True
    return False


def door_ends(z):
    out = []
    for side, z0, w, open_ in T.doors:
        if open_ and z == z0 - 1:
            out.append("south")
        if open_ and z == z0 + w:
            out.append("north")
    return tuple(out)


def fuselage(sh):
    for z in range(int(T.NOSE), int(T.TAIL0)):
        ring(sh, z, z + 1, 0.0, T.C, T.R, skin, ends=door_ends(z), skip=lambda i, j, z=z: door_skip(i, j, z))
    for k in range(int(T.NOSE * 8)):
        z0, z1 = k / 8, (k + 1) / 8
        cy, r = nose((z0 + z1) / 2)
        cy0, r0 = nose(z0)
        ring(sh, z0, z1, 0.0, cy, r, skin, thick=max(3.0, (r - r0) * 16 + 1.5), ends=("north",), inner_ends=("south",))
    discfill(sh, 0.0, 0.125, 0.0, nose(0.06)[0], max(0.1, nose(0.06)[1] - 0.05), "al_radome")
    for k in range(int((T.LEN - T.TAIL0) * 4)):
        z0, z1 = T.TAIL0 + k / 4, T.TAIL0 + (k + 1) / 4
        cy, r = nose((z0 + z1) / 2)
        cy1, r1 = nose(z1)
        ring(sh, z0, z1, 0.0, cy, r, skin, thick=max(3.0, (r - r1) * 16 + 1.5), ends=("south",), inner_ends=("north",),
             skip=lambda i, j, z=math.floor(z0): door_skip(i, j, z))
    cy, r = nose(T.LEN - 0.125)
    discfill(sh, T.LEN - 0.25, T.LEN - 0.05, 0.0, cy, r - 0.08, "al_belly")
    discfill(sh, T.LEN - 0.05, T.LEN, 0.0, cy, r - 0.3, "al_exhaust", faces=("south",))


# ---------------------------------------------------------------- the cabin

def cabin(sh):
    """The floor from the cockpit to the aft bulkhead, the bins (in the big ones), the ceiling."""
    for k in range(int(T.NOSE * 0.39 * 4), int((T.AFT + 1) * 4)):
        z0, z1 = k / 4, (k + 1) / 4
        cy, r = nose((z0 + z1) / 2)
        if r < 1.6:
            continue
        x = -math.floor(r / 0.25) * 0.25
        while x < r:
            xa, xb = x, x + 0.25
            far = max(abs(xa), abs(xb))
            if far < r:
                low = cy - math.sqrt(r * r - far * far)
                ya = max(T.FLOOR - 1, low)
                if ya < T.FLOOR - 0.02:
                    sh.add(Box([xa, ya, z0], [xb, T.FLOOR, z1],
                               {"up": ("al_carpet", None), "down": ("al_dark", None), "east": ("al_dark", None),
                                "west": ("al_dark", None), "north": ("al_dark", None), "south": ("al_dark", None)}))
            x += 0.25
    rng = np.random.default_rng(sum(map(ord, T.key)))
    for z in range(T.bins[0], T.bins[1]):
        r = rng.random()
        kind = "masks" if r < 0.3 else "open" if r < 0.6 else "closed"
        for side in (-1, 1):
            bins(sh, z, side, kind)
    hw0 = T.R - 2.15
    for z in range(int(T.NOSE), T.AFT + 1):
        cy, r = nose(z + 0.5)
        if T.bins[0] <= z < T.bins[1]:
            sh.add(Box([-hw0, T.ceil, z], [hw0, T.ceil + 0.1, z + 1], {"down": ("al_ceiling", None), "up": ("al_dark", None)}))
            for side in (-1, 1):
                x = hw0 * side
                sh.add(Box([min(x, x - 0.06 * side), T.ceil - 0.42, z], [max(x, x - 0.06 * side), T.ceil, z + 1],
                           {"east" if side < 0 else "west": ("al_cove", None)}))
        else:
            hw = math.sqrt(max(0.0, r * r - (T.ceil + 0.03 - cy) ** 2)) - 0.05
            if hw > 0.5:
                tex = "al_ceiling" if T.windows[0] <= z < T.windows[1] and z % 2 == 0 else "al_ceiling_plain"
                sh.add(Box([-hw, T.ceil, z], [hw, T.ceil + 0.1, z + 1], {"down": (tex, None), "up": ("al_dark", None)}))


def bins(sh, z, side, kind):
    """The trijet's bins, set against this fuselage's wall."""
    s = side
    face = "east" if s < 0 else "west"
    back = "west" if s < 0 else "east"
    out = math.sqrt(T.R ** 2 - 2.1 ** 2) - 0.35
    lo = T.C + 1.5

    def xr(a, b):
        return (min(a * s, b * s), max(a * s, b * s))

    x0, x1 = xr(out, out - 1.5)
    door = "al_bin_open" if kind == "open" else "al_bin"
    sh.add(Box([x0, lo, z], [x1, lo + 0.6, z + 1],
               {face: (door, FULL), "down": ("al_psu", "flip" if s > 0 else FULL), back: ("al_dark", None)}))
    x0, x1 = xr(out - 0.35, out - 1.5)
    sh.add(Box([x0, lo + 0.6, z], [x1, lo + 1.1, z + 1], {face: ("al_bin_top", None), "up": ("al_dark", None)}))
    if kind == "open":
        xa, xb = xr(out - 1.5, out - 1.55)
        sh.add(Box([xa, lo + 1.1, z + 0.05], [xb, lo + 1.5, z + 0.95], all_faces("al_bin_door_in")))
        xa, xb = xr(out - 0.7, out - 1.45)
        sh.add(Box([xa, lo + 0.05, z + 0.25], [xb, lo + 0.45, z + 0.75], all_faces("ap_bag_red" if (z * 7) % 3 == 0 else "ap_bag_navy")))
    if kind == "masks":
        for k, mx in enumerate((out - 0.25, out - 0.7, out - 1.15)):
            x = mx * s
            mz = z + 0.3 + 0.2 * (k % 2)
            drop = 0.75 + 0.18 * ((z + k) % 3)
            sh.add(Box([x - 0.012, lo - drop, mz - 0.012], [x + 0.012, lo, mz + 0.012], all_faces("al_tube")))
            sh.add(Box([x - 0.09, lo - drop - 0.12, mz - 0.09], [x + 0.09, lo - drop, mz + 0.09], all_faces("al_mask")))


def bulkheads(sh):
    def doorway(xa, xb, y0, y1):
        if xb > -1.0 and xa < 1.0:
            top = min(T.FLOOR + 3.4, T.ceil)
            return [(max(y0, top), y1)] if y1 > top else []
        return [(max(y0, T.FLOOR - 0.01), y1)]
    cy, r = nose(T.NOSE)
    discfill(sh, T.NOSE - 0.08, T.NOSE, 0.0, cy, r - 0.3, "al_ck_lining", hole=doorway)
    cy, r = nose(T.AFT + 1)
    discfill(sh, T.AFT + 1, T.AFT + 1.1, 0.0, cy, r - 0.25, "al_lining", hole=lambda xa, xb, y0, y1: [(max(y0, T.FLOOR - 0.01), y1)])


# ---------------------------------------------------------------- wings, engines, props

def wing_mats(f):
    return A.wing_mats(f)


def wing(sh, root, tip, le, te, ym, th, walk=None, flaps=(), winglet=None):
    def top(x, z, tex):
        if walk and tex == "al_wing" and walk[0] < abs(x) < walk[1]:
            return "al_walkway"
        return tex
    for side in (-1, 1):
        planform(sh, root, tip, le, te, ym, th, wing_mats, side, top_tex=top)
        x = tip * side
        sh.add(Box([x - 0.1, ym(tip) - 0.1, le(tip) + 0.4], [x + 0.1, ym(tip) + 0.1, le(tip) + 0.7],
                   all_faces("al_nav_red" if side < 0 else "al_nav_green")))
        for a in flaps:
            x = a * side
            sh.add(Box([x - 0.18, ym(a) - 0.75, te(a) - 2.2], [x + 0.18, ym(a) - 0.2, te(a) + 0.9], all_faces("al_wing_under")))
        if winglet:
            h, lean = winglet
            y0 = ym(tip)
            zl, zt = le(tip), te(tip)
            for k in range(int(h * 4)):
                ya, yb = y0 + k / 4, y0 + (k + 1) / 4
                f = (k + 0.5) / (h * 4)
                za, zb = zl + (zt - zl) * 0.55 * f, zt - (zt - zl) * 0.12 * f
                xo = (tip + lean * f) * side
                w = 0.14 * (1 - 0.5 * f)
                sh.add(Box([xo - w, ya, za], [xo + w, yb, zb],
                           {"west": (L("main"), None), "east": (L("main"), None), "north": ("al_metal", None),
                            "south": (L("main"), None), "up": (L("main"), None), "down": (L("main"), None)}))


ENGINES = {}


def fan_name(key, k):
    return f"an_fan_{key}_{k}"


def podded(sh, xe, ye, z0, z1, rmax):
    """The trijet's engine pod, with a fan picture drawn for this size and spot."""
    key = f"{int(round(rmax * 10))}_{int(round((xe % 1) * 10))}_{int(round((ye % 1) * 10))}"
    n = math.ceil(rmax) * 2 + 1
    gx, gy = math.floor(xe - n / 2 + 0.5), math.floor(ye + n / 2 - 0.5)
    ENGINES[key] = (rmax, xe - gx, gy + 1 - ye, n)
    n_ = int((z1 - z0) * 4)

    def prof(z):
        f = (z - z0) / (z1 - z0)
        return rmax * (0.9 if f < 0.04 else 1.0 if f < 0.66 else 1.0 - 0.27 * (f - 0.66) / 0.34)
    for k in range(n_):
        za, zb = z0 + k / 4, z0 + (k + 1) / 4
        r = prof((za + zb) / 2)
        lip = k == 0
        ring(sh, za, zb, xe, ye, r, lambda x, y, z, t, lip=lip: ("al_metal" if lip else "al_cowl", "al_intake", None, None, False),
             thick=4.0 if lip else 2.5, ends=("north",) if lip else ("south",) if k == n_ - 1 else ())
    fz = z0 + 0.55

    def fan(c):
        i, j = c[0] - gx, gy - c[1]
        return fan_name(key, max(0, min(n - 1, j)) * n + max(0, min(n - 1, i)))
    discfill(sh, fz, fz + 0.1, xe, ye, rmax - 0.25, fan, faces=("north",))
    for k, r in enumerate((0.28, 0.22, 0.15, 0.07)):
        discfill(sh, fz - (k + 1) * 0.1, fz - k * 0.1, xe, ye, r * rmax, "al_spinner", faces=("north", "up", "down", "east", "west"))
    for k, r in enumerate((0.56, 0.48, 0.38, 0.27, 0.16, 0.08)):
        discfill(sh, z1 - 0.3 + k * 0.25, z1 - 0.05 + k * 0.25, xe, ye, r * rmax, "al_exhaust_plug",
                 faces=("south", "up", "down", "east", "west"))


def pylon(sh, xe, y0, y1, z0, z1, w=0.22):
    sh.add(Box([xe - w, y0, z0], [xe + w, y1, z1], all_faces("al_cowl")))
    sh.add(Box([xe - w * 0.8, y0 + 0.05, z0 - 0.25], [xe + w * 0.8, y1 - 0.05, z0], all_faces("al_metal")))


def nacelle(sh, xe, ye, z0, z1, r):
    """A turboprop's nacelle under the wing: rounded in front behind the spinner, long and slim, its
    tail tapering off behind the wing; the exhaust stack on its outboard side."""
    n = int((z1 - z0) * 4)
    for k in range(n):
        za, zb = z0 + k / 4, z0 + (k + 1) / 4
        f = (za + zb) / 2 - z0
        g = (z1 - (za + zb) / 2)
        rr = r * min(1.0, 0.55 + 0.45 * min(1.0, f / 1.2)) * min(1.0, 0.35 + 0.65 * min(1.0, g / 4.0))
        ring(sh, za, zb, xe, ye, rr, lambda x, y, z, t: ("al_cowl", "al_dark", None, None, False), thick=2.5,
             ends=("north",) if k == 0 else ("south",) if k == n - 1 else ())
    s = 1 if xe > 0 else -1
    sh.add(Box([xe + s * r * 0.75 - 0.15, ye - 0.15, z0 + 4.0], [xe + s * r * 0.75 + 0.15, ye + 0.15, z0 + 5.2], all_faces("al_exhaust")))
    # the oil cooler's scoop under the chin
    sh.add(Box([xe - 0.3, ye - r - 0.2, z0 + 0.9], [xe + 0.3, ye - r + 0.2, z0 + 2.4], all_faces("al_intake")))


def propeller(sh, xe, ye, zp, rp, blades=6, phase=0.0):
    """The prop: a pointed spinner and six blades feathered, each in two pieces turned about the
    shaft (no piece turned more than 45 degrees from straight up or across)."""
    for k, (r, d) in enumerate(((0.62, 0.28), (0.55, 0.25), (0.45, 0.2), (0.32, 0.18), (0.18, 0.15), (0.06, 0.12))):
        z1 = zp - sum(dd for _, dd in ((0.62, 0.28), (0.55, 0.25), (0.45, 0.2), (0.32, 0.18), (0.18, 0.15), (0.06, 0.12))[:k])
        discfill(sh, z1 - d, z1, xe, ye, r, "an_spinner", faces=("north", "up", "down", "east", "west"))
    for b in range(blades):
        phi = (phase + b * 360 / blades) % 360
        for seg in range(3):
            r0 = 0.55 + (rp - 0.55) * seg / 3
            r1 = 0.55 + (rp - 0.55) * (seg + 1) / 3
            rm = (r0 + r1) / 2
            ln = (r1 - r0) + 0.03
            w = 0.26 * (1.15 - 0.4 * seg / 3)
            rad = math.radians(phi)
            px, py = xe + rm * math.sin(rad), ye + rm * math.cos(rad)
            # nearest base: along y (0 / 180) or along x (90 / 270)
            base = min((0, 90, 180, 270), key=lambda q: abs(((phi - q + 180) % 360) - 180))
            ang = ((base - phi + 180) % 360) - 180
            tex = "an_blade_tip" if seg == 2 else "an_blade"
            if base in (0, 180):
                lo, hi = [px - w / 2, py - ln / 2, zp - 0.12], [px + w / 2, py + ln / 2, zp - 0.04]
            else:
                lo, hi = [px - ln / 2, py - w / 2, zp - 0.12], [px + ln / 2, py + w / 2, zp - 0.04]
            sh.add(Box(lo, hi, all_faces(tex), ("z", ang, [px, py, zp - 0.08])))


# ---------------------------------------------------------------- tails

def fin(sh, y0, y1, le, te, th, cap_up=True):
    """The fin: chord along z, from y0 on the cone's crown to y1, the livery's picture on it."""
    y = y0
    while y < y1 - 1e-6:
        ya, yb = y, min(y1, math.floor(y * 2 + 1e-6) / 2 + 0.5)
        ymid = (ya + yb) / 2
        zl, zt = le(ymid), te(ymid)
        z = math.floor(zl / 0.5) * 0.5
        while z < zt - 1e-6:
            za, zb = max(z, zl), min(z + 0.5, zt)
            if zb - za > 0.01:
                f = ((za + zb) / 2 - zl) / (zt - zl)
                h = th(ymid) * airfoil(f)
                cell = (math.floor((za + zb) / 2), math.floor(ymid))
                tex = fin_tex(cell, f)
                sh.add(Box([-h / 2, ya, za], [h / 2, yb, zb],
                           {"west": (tex, None), "east": (tex, "auto_flip"), "up": (L("main"), None),
                            "down": (L("main"), None), "north": (L("main"), None), "south": (L("main"), None)}))
            z += 0.5
        za, zb = le(ya), le(yb)
        ang = math.degrees(math.atan2(zb - za, yb - ya))
        my, mz = (ya + yb) / 2, (za + zb) / 2 + 0.05
        span = math.hypot(yb - ya, zb - za) + 0.04
        sh.add(Box([-th(my) * 0.33, my - span / 2, mz - 0.06], [th(my) * 0.33, my + span / 2, mz + 0.06],
                   all_faces(L("main")), ("x", ang, [0.0, my, mz])))
        y = yb
    # the dorsal fillet running forward along the crown into the fin's root
    zl = le(y0)
    for k in range(int(3.0 * 4)):
        za, zb = zl - 3.0 + k / 4, zl - 3.0 + (k + 1) / 4
        cy, r = nose((za + zb) / 2)
        hgt = 0.6 * (k + 1) / 12
        sh.add(Box([-0.18, cy + r - 0.1, za], [0.18, cy + r + hgt, zb], all_faces(L("main"))))
    sh.add(Box([-0.08, nose(T.LEN - 0.2)[0] + 0.45, T.LEN - 0.4], [0.08, nose(T.LEN - 0.2)[0] + 0.6, T.LEN - 0.1],
               all_faces("al_nav_white")))


FINS = {}


def fin_tex(cell, f):
    box = FINS.get(T.key)
    if box:
        z0, ytop, w, h = box
        i, j = cell[0] - z0, ytop - 1 - cell[1]
        if 0 <= i < w and 0 <= j < h and (i, j) in fin_tiles(LV, T.key):
            return L(f"{T.key}_fin_{j * w + i}")
    return L("rudder") if f > 0.7 else L("main")


def tailplanes(sh, root, tip, le, te, ym, th):
    for side in (-1, 1):
        planform(sh, root, tip, le, te, ym, th,
                 lambda f: ("al_metal", "al_metal", "al_metal") if f < 0.07 else ("al_tailplane", "al_wing_under", "al_wing_under"),
                 side, dx=0.5)


def bullet(sh, yt, z0, z1):
    z = z0
    while z < z1 - 1e-6:
        zb = min(z1, math.floor(z) + 1.0)
        f = (z - z0) / (z1 - z0)
        w = 0.38 * (1 - (2 * f - 1) ** 4) + 0.06
        sh.add(Box([-w, yt - w, z], [w, yt + w, zb], all_faces(L("main"))))
        z = zb


# ---------------------------------------------------------------- the types

def build_twinjet(sh):
    le = lambda a: 21 + 0.45 * (a - 2)  # noqa: E731
    te = lambda a: 33 + 0.12 * (a - 2)  # noqa: E731
    ym = lambda a: 4.6 + 0.1 * (a - 2)  # noqa: E731
    th = lambda a: 1.0 - 0.7 * (a - 2) / 25  # noqa: E731
    wing(sh, 2.0, 27.0, le, te, ym, th, walk=(5, 7.5), flaps=(9.5, 14.5, 19.5), winglet=(3.2, 0.6))
    for side in (-1, 1):
        xe = 7.6 * side
        podded(sh, xe, 3.35, 18.6, 25.4, 1.55)
        pylon(sh, xe, 3.35 + 1.3, ym(7.6) - 0.2, 20.8, 26.6)
        gear(sh, 4.4 * side, 31.8, ym(4.4) - 0.36 * th(4.4), 0.85, 0.55)
    gear(sh, 0.0, 6.6, nose(6.6)[0] - nose(6.6)[1] + 0.2, 0.72, 0.4, nosegear=True)
    y0, y1 = 11.5, 20.0
    fle = lambda y: 47.0 + 1.0 * (y - y0)  # noqa: E731
    fte = lambda y: 58.4 + 0.22 * (y - y0)  # noqa: E731
    fth = lambda y: 0.6 - 0.28 * (y - y0) / (y1 - y0)  # noqa: E731
    FINS["twinjet"] = (49, 20, 11, 8)
    fin(sh, y0, y1, fle, fte, fth)
    tailplanes(sh, 1.0, 10.6, lambda a: 50.6 + 0.75 * a, lambda a: 50.6 + 0.75 * a + 4.8 - 3.0 * a / 10.6,
               lambda a: 8.5 + 0.06 * a, lambda a: 0.42 - 0.2 * a / 10.6)


def build_widebody(sh):
    le = lambda a: 33 + 0.55 * (a - 3)  # noqa: E731
    te = lambda a: 50 + 0.22 * (a - 3)  # noqa: E731
    ym = lambda a: 4.7 + 0.1 * (a - 3)  # noqa: E731
    th = lambda a: 1.5 - 1.05 * (a - 3) / 41  # noqa: E731
    wing(sh, 3.0, 44.0, le, te, ym, th, walk=(7.5, 10.5), flaps=(11, 19, 26, 33))
    for side in (-1, 1):
        xe = 14.5 * side
        podded(sh, xe, 4.75, le(14.5) - 7.6, le(14.5) + 1.6, 2.9)
        pylon(sh, xe, 4.75 + 2.4, ym(14.5) - 0.3, le(14.5) - 4.0, le(14.5) + 3.0, w=0.32)
        for dz in (0.0, 1.9):
            gear(sh, 5.6 * side, 49.0 + dz, ym(5.6) - 0.36 * th(5.6), 0.95, 0.62)
    gear(sh, 0.0, 9.5, nose(9.5)[0] - nose(9.5)[1] + 0.2, 0.8, 0.45, nosegear=True)
    y0, y1 = 15.9, 28.0
    fle = lambda y: 72.0 + 1.05 * (y - y0)  # noqa: E731
    fte = lambda y: 88.8 + 0.25 * (y - y0)  # noqa: E731
    fth = lambda y: 0.9 - 0.45 * (y - y0) / (y1 - y0)  # noqa: E731
    FINS["widebody"] = (76, 27, 14, 10)
    fin(sh, y0, y1, fle, fte, fth)
    tailplanes(sh, 1.5, 16.0, lambda a: 78.5 + 0.75 * a, lambda a: 78.5 + 0.75 * a + 7.0 - 4.5 * a / 16,
               lambda a: 10.6 + 0.06 * a, lambda a: 0.6 - 0.3 * a / 16)


def build_turboprop(sh):
    # the wing over the cabin's roof, straight and slender, on a fairing along the crown
    yw = T.C + T.R + 0.2
    le = lambda a: 14.0 + 0.05 * a  # noqa: E731
    te = lambda a: 18.6 - 0.07 * a  # noqa: E731
    ym = lambda a: yw - 0.012 * a  # noqa: E731
    th = lambda a: 0.75 - 0.3 * a / 20  # noqa: E731
    wing(sh, 0.0, 20.0, le, te, ym, th, flaps=(4.5, 9.5, 14.5))
    for k in range(int(9.0 * 4)):
        za, zb = 12.5 + k / 4, 12.5 + (k + 1) / 4
        f = (k + 0.5) / 36
        w = 1.6 * math.sin(math.pi * min(1.0, f * 1.15)) ** 0.5
        if w > 0.05:
            cy, r = nose((za + zb) / 2)
            sh.add(Box([-w, cy + r - 0.2, za], [w, yw + 0.1, zb], all_faces("al_paint")))
    for side in (-1, 1):
        xe = 6.2 * side
        nacelle(sh, xe, yw - 1.1, 11.6, 22.0, 1.05)
        propeller(sh, xe, yw - 1.1, 11.6, 2.9, phase=15 if side < 0 else 45)
        # the main gear in its fairing low on the side
        xs = (T.R - 0.4) * side
        for k in range(int(8 * 4)):
            za, zb = 16.0 + k / 4, 16.0 + (k + 1) / 4
            f = (k + 0.5) / 32
            w = 0.9 * math.sin(math.pi * f) ** 0.6
            x0, x1 = sorted((xs, xs + w * side))
            sh.add(Box([x0, T.C - T.R + 0.5, za], [x1, T.C - T.R + 1.6, zb], all_faces("al_belly")))
        gear(sh, (T.R - 0.2) * side, 20.0, T.C - T.R + 0.7, 0.7, 0.35)
    gear(sh, 0.0, 4.2, nose(4.2)[0] - nose(4.2)[1] + 0.2, 0.55, 0.3, nosegear=True)
    y0, y1 = 9.0, 17.5
    fle = lambda y: 31.0 + 0.95 * (y - y0)  # noqa: E731
    fte = lambda y: 38.8 + 0.22 * (y - y0)  # noqa: E731
    fth = lambda y: 0.5 - 0.22 * (y - y0) / (y1 - y0)  # noqa: E731
    FINS["turboprop"] = (32, 17, 8, 7)
    fin(sh, y0, y1, fle, fte, fth)
    yt = y1 - 0.2
    bullet(sh, yt, fle(yt) - 0.5, fte(yt) + 1.0)
    tailplanes(sh, 0.3, 6.6, lambda a: fle(yt) + 0.2 + 0.3 * a, lambda a: fle(yt) + 0.2 + 0.3 * a + 3.2 - 1.2 * a / 6.6,
               lambda a: yt, lambda a: 0.34 - 0.14 * a / 6.6)


def build_bizjet(sh):
    le = lambda a: 12.4 + 0.5 * (a - 1.5)  # noqa: E731
    te = lambda a: 18.6 + 0.12 * (a - 1.5)  # noqa: E731
    ym = lambda a: 2.95 + 0.12 * (a - 1.5)  # noqa: E731
    th = lambda a: 0.75 - 0.45 * (a - 1.5) / 14  # noqa: E731
    wing(sh, 1.5, 15.5, le, te, ym, th, flaps=(5.5, 10.0), winglet=(2.4, 0.5))
    for side in (-1, 1):
        xe = (T.R + 1.45) * side
        podded(sh, xe, T.C + 0.9, 21.2, 26.8, 1.0)
        x0, x1 = sorted(((T.R - 0.6) * side, (abs(xe) - 0.8) * side))
        sh.add(Box([x0, T.C + 0.75, 22.6], [x1, T.C + 1.05, 25.6], all_faces("al_cowl")))
        gear(sh, 3.3 * side, 17.4, ym(3.3) - 0.36 * th(3.3), 0.6, 0.3)
    gear(sh, 0.0, 4.4, nose(4.4)[0] - nose(4.4)[1] + 0.2, 0.45, 0.25, nosegear=True)
    y0, y1 = 7.4, 11.6
    fle = lambda y: 22.6 + 1.25 * (y - y0)  # noqa: E731
    fte = lambda y: 29.8 + 0.4 * (y - y0)  # noqa: E731
    fth = lambda y: 0.42 - 0.16 * (y - y0) / (y1 - y0)  # noqa: E731
    FINS["bizjet"] = (24, 11, 7, 4)
    fin(sh, y0, y1, fle, fte, fth)
    yt = y1 - 0.2
    bullet(sh, yt, fle(yt) - 0.5, fte(yt) + 0.8)
    tailplanes(sh, 0.3, 5.6, lambda a: fle(yt) + 0.2 + 0.55 * a, lambda a: fle(yt) + 0.2 + 0.55 * a + 2.6 - 1.2 * a / 5.6,
               lambda a: yt + 0.02 * a, lambda a: 0.3 - 0.1 * a / 5.6)


BUILD = {"twinjet": build_twinjet, "widebody": build_widebody, "turboprop": build_turboprop, "bizjet": build_bizjet}


def use(tp, lv):
    global T, LV
    T, LV = TYPES[tp], lv


@lru_cache(maxsize=None)
def shell(tp, lv):
    use(tp, lv)
    sh = Shell()
    fuselage(sh)
    cabin(sh)
    BUILD[tp](sh)
    bulkheads(sh)
    return sh


# ---------------------------------------------------------------- the fittings

def fittings(tp, lv):
    """Where the named fittings go: (x, y, z) -> block with its facing."""
    use(tp, lv)
    sh = shell(tp, lv)
    out = {}
    fl = T.FLOOR
    rng = np.random.default_rng(sum(map(ord, tp + lv)))

    def free(x, y, z):
        return (x, y, z) not in sh.cells and (x, y, z) not in out

    def put(x, y, z, name, facing="north"):
        if (x, y, z) in sh.cells:
            raise SystemExit(f"an_: {tp} {name} at {(x, y, z)} is in the shell")
        out[(x, y, z)] = f"{name}[facing={facing}]"

    def seat(x, z, biz=False, facing="north"):
        if not free(x, fl, z):
            return
        r = rng.random()
        k = "_bag" if r < 0.12 else "_torn" if r < 0.2 else "_recline" if r < 0.34 else "_belt" if r < 0.5 else ""
        put(x, fl, z, ("al_seat_biz" if biz else "al_seat") + k, facing)

    def lav(x0, z0, facing):
        sub = {}
        A.lav(sub, x0, z0, facing)
        for (x, y, z), b in sub.items():
            name, face = b[:-1].split("[facing=")
            yy = fl if y == 7 else fl + 2
            if y == 7 or free(x, yy, z):
                put(x, yy, z, name, face)

    def galley(xs, z, facing):
        for x in xs:
            for dy, part in enumerate(("al_galley_lo", "al_galley_mid", "al_galley_hi")):
                if fl + dy < T.ceil - 0.2 and free(x, fl + dy, z):
                    put(x, fl + dy, z, part, facing)

    # the cockpit: the panel across the nose, the pedestal and the seats, as far forward as they fit
    def room(z, deep):
        return all(free(x, y, zz) for x in (-2, -1, 0, 1) for y in (fl, fl + 1) for zz in range(z, z + deep))
    deep = 3 if any(room(z, 3) for z in range(2, T.NOSE)) else 2
    zc = next(z for z in range(2, T.NOSE) if room(z, deep))
    for k, x in enumerate(range(-2, 2)):
        put(x, fl + 1, zc, f"al_ck_panel_{k}")
        put(x, fl, zc, f"al_ck_knee_{k}")
    for x in (-1, 0):
        put(x, fl, zc + 1, "al_ck_ped")
        if deep == 3:
            put(x, fl, zc + 2, "al_ck_ped_aft")
        if T.ceil - fl > 3.2 and free(x, fl + 3, zc + 1):
            put(x, fl + 3, zc + 1, "al_ck_over")
    for x in (-2, 1):
        if deep == 3:
            put(x, fl, zc + 1, "al_ck_yoke")
        put(x, fl, zc + deep - 1, "al_ck_seat")
    if tp == "twinjet":
        lav(-3, 12, "east")
        galley((2,), 12, "west")
        galley((2,), 13, "west")
        for z in (14, 16, 18):
            for x in (-3, -2, 1, 2):
                seat(x, z, True)
        for x in (-3, -2, 1, 2):
            put(x, fl, 20, "al_partition")
        put(-1, fl + 2, 20, "al_curtain")
        for z in range(21, 45):
            if z in (28, 29):
                continue
            for x in (-3, -2, 1, 2):
                seat(x, z)
        put(0, fl, 36, "al_cart_out")
        lav(1, 48, "west")
        for x in range(-2, 1):
            galley((x,), 50, "north")
        put(-5, fl, 9, "al_door_leaf", "west")
        put(-5, fl, 45, "al_door_leaf", "west")
    elif tp == "widebody":
        lav(-5, 16, "east")
        lav(3, 16, "west")
        for x in range(-2, 2):
            galley((x,), 16, "south")
        # business in pairs by the windows and in the middle, then economy 2-4-2
        for z in range(19, 31, 2):
            for x in (-5, -4, -1, 0, 3, 4):
                seat(x, z, True)
        for x in (-5, -4, -2, -1, 0, 1, 3, 4):
            put(x, fl, 31, "al_partition")
        for z in range(35, 77):
            if z in (55, 56, 57):
                continue
            for x in (-5, -4, -2, -1, 0, 1, 3, 4):
                seat(x, z)
        for x in range(-2, 2):
            galley((x,), 53, "south")
        lav(-4, 79, "east")
        lav(2, 79, "west")
        for x in range(-2, 2):
            galley((x,), 81, "north")
        put(-3, fl, 45, "al_cart_out")
        put(-8, fl, 13, "al_door_leaf", "west")
        put(-8, fl, 55, "al_door_leaf", "west")
    elif tp == "turboprop":
        # the washroom and the baggage racks behind the cockpit, then seats 2-2, the galley by the door aft
        lav(1, 9, "west")
        for x in (-3, -2):
            for z in (9, 10):
                if free(x, fl, z):
                    put(x, fl, z, "al_galley_lo", "east")
        for z in range(12, 30):
            for x in (-3, -2, 1, 2):
                seat(x, z)
        for z in (31, 32):
            galley((1,), z, "west")
        put(-5, fl, 30, "al_door_leaf", "west")
    elif tp == "bizjet":
        # the club seats facing each other over the tables, the divan, the galley and the washroom aft
        for z, facing in ((10, "south"), (12, "north"), (13, "south"), (15, "north")):
            for x in (-2, 1):
                seat(x, z, True, facing)
        for x in (-2, 1):
            for z in (11, 14):
                put(x, fl, z, "al_galley_lo", "east" if x < 0 else "west")
        for z in (17, 18, 19):
            seat(1, z, True, "west")
        galley((-2,), 17, "east")
        galley((-2,), 18, "east")
        lav(-2, 19, "east")
        put(-4, fl, 6, "al_door_leaf", "west")
    return out


# ---------------------------------------------------------------- compiling the shells

def compile_all(only=None):
    """Every plane's shell blocks, one name table over them all: name -> spec, and per plane its
    layout cell -> name."""
    specs, names, layouts = {}, {}, {}
    for tp, t in TYPES.items():
        if only and tp != only:
            continue
        for lv in t.liveries:
            sh = shell(tp, lv)
            lay = {}
            for cell in sorted(sh.cells, key=lambda c: (c[2], c[1], c[0])):
                boxes, shape, glass = sh.model(cell)
                if not boxes:
                    continue
                key = json.dumps([boxes, shape, glass], sort_keys=True)
                if key not in names:
                    name = f"an_s{len(names)}"
                    names[key] = name
                    specs[name] = {"model": "boxes", "boxes": boxes, "title": "Airliner", "sound": "METAL",
                                   "rotate": "facing", "shape": shape, "render": "translucent" if glass else "cutout"}
                lay[cell] = names[key]
            layouts[(tp, lv)] = lay
    return specs, layouts


def header(tp):
    """The plane's doors and size for Airliner.java: floor, the open doors' first z west and east,
    the skins' x at the doors, length, half span."""
    t = TYPES[tp]
    west = [d[1] for d in t.doors if d[0] < 0 and d[3]]
    east = [d[1] for d in t.doors if d[0] > 0 and d[3]]
    span = {"twinjet": 28, "widebody": 45, "turboprop": 21, "bizjet": 17}[tp]
    return (f"#! floor={t.FLOOR} west={','.join(map(str, west))} east={','.join(map(str, east)) or '-'}"
            f" skin={-math.ceil(t.R)},{math.ceil(t.R) - 1} length={t.LEN} span={span}")


# ---------------------------------------------------------------- textures

def door_pic(lv, tp):
    use(tp, lv)
    return _door_pic(lv, T.FLOOR + 3, T.CHEAT, T.PIN, T.WIN)


@lru_cache(maxsize=None)
def _door_pic(lv, top, cheat, pin, win):
    _, main, pinc = LIVERIES[lv]
    p = Pic(2, 3, WHITE + (255,), 41400 + sum(map(ord, lv)))

    def band(y0, y1, col):
        p.rect(0, (top - y1) * N, 2 * N, (top - y0) * N, fill=col)
    band(*cheat, main)
    band(*pin, pinc)
    p.rect(5, 6, 2 * N - 5, 3 * N - 4, outline=(90, 94, 100), width=1.2)
    p.ellipse(N - 14, (top - win[1]) * N + 2, N + 14, (top - win[0]) * N - 2, fill=(60, 74, 86), outline=(96, 100, 106), width=2)
    p.rect(N - 16, 1.6 * N, N + 16, 1.6 * N + 12, fill=(60, 60, 64), outline=(30, 30, 30))
    p.rect(N - 12, 1.6 * N + 3, N + 12, 1.6 * N + 9, fill=(180, 180, 176))
    p.text(N, 2.25 * N, "PUSH|TO OPEN", BOLD, 5, (160, 40, 30), anchor="ma")
    return p


@lru_cache(maxsize=None)
def exit_pic(lv):
    t = TYPES["twinjet"]
    _, main, pinc = LIVERIES[lv]
    top = t.FLOOR + 2
    p = Pic(1, 2, WHITE + (255,), 41410 + sum(map(ord, lv)))

    def band(y0, y1, col):
        p.rect(0, (top - y1) * N, N, (top - y0) * N, fill=col)
    band(*t.CHEAT, main)
    band(*t.PIN, pinc)
    p.rect(6, 22, N - 6, 2 * N - 18, outline=(90, 94, 100), width=1.2)
    p.ellipse(N / 2 - 9, (top - t.WIN[1]) * N + 2, N / 2 + 9, (top - t.WIN[0]) * N - 2, fill=(60, 74, 86), outline=(96, 100, 106), width=2)
    p.rect(N / 2 - 12, 10, N / 2 + 12, 19, fill=(200, 40, 30))
    p.text(N / 2, 10.5, "EXIT", BOLD, 7, (250, 250, 250), anchor="ma")
    return p


@lru_cache(maxsize=None)
def title_pic(lv, tp):
    t = TYPES[tp]
    name, main, _ = LIVERIES[lv]
    n = t.title_z[1] - t.title_z[0]
    hgt = t.TITLE_Y[1] - t.TITLE_Y[0]
    p = Pic(n, 1, (0, 0, 0, 0), 41420 + sum(map(ord, lv + tp)))
    size = min(civic.fit(name, BOLD, 60, n * N - 40), hgt * N * 0.8)
    p.text(n * N / 2, max(2, (N - size * 0.75) / 2), name, BOLD, size, main, anchor="ma")
    return p


REGS = {("twinjet", "coastal"): "N618CA", ("twinjet", "skyline"): "N402SX", ("widebody", "north"): "N770NS",
        ("turboprop", "coastal"): "N72CC", ("bizjet", "private"): "N350GH", ("bizjet", "charter"): "N88RJ"}


@lru_cache(maxsize=None)
def reg_pic(lv, tp):
    t = TYPES[tp]
    n = t.reg_z[1] - t.reg_z[0]
    p = Pic(n, 1, (0, 0, 0, 0), 41430 + sum(map(ord, lv + tp)))
    p.text(n * N / 2, 10, REGS[(tp, lv)], BOLD, min(40, n * N / 5), (50, 54, 60), anchor="ma")
    return p


def on_paint(pic, i, seed):
    t = A.paint(seed)
    a = pic.shrunk()
    cols = pic.cols
    cx, cy = (i % cols) * N, (i // cols) * N
    sub = a[cy:cy + N, cx:cx + N]
    w = sub[..., 3:4] / 255
    t.c = t.c * (1 - w) + sub[..., :3] * w
    return t


@lru_cache(maxsize=None)
def fin_pic(lv, tp):
    """The airline's mark on the fin: the wave under the sun for Coastal, the city's towers for
    Skyline, the star for Northstar, a swept band for the private jets."""
    z0, ytop, w, h = FINS[tp]
    _, main, pinc = LIVERIES[lv]
    p = Pic(w, h, (0, 0, 0, 0), 41440 + sum(map(ord, lv + tp)))
    W, H = w * N, h * N
    light = (240, 238, 232, 255)
    if lv == "coastal":
        p.ellipse(W * 0.38, H * 0.12, W * 0.78, H * 0.12 + W * 0.4, fill=pinc + (255,))
        pts = [(W * 0.1, H * 0.78)]
        for k in range(21):
            x = W * (0.1 + 0.85 * k / 20)
            pts.append((x, H * (0.58 - 0.1 * math.sin(k / 20 * math.pi * 2.2))))
        pts.append((W * 0.95, H * 0.78))
        p.poly(pts, fill=light)
    elif lv == "skyline":
        x = W * 0.12
        for k, (bw, bh) in enumerate(((0.1, 0.45), (0.08, 0.7), (0.12, 0.55), (0.07, 0.85), (0.11, 0.5), (0.09, 0.62))):
            p.rect(x, H * (0.9 - bh * 0.8), x + W * bw, H * 0.9, fill=light if k % 2 == 0 else pinc)
            x += W * (bw + 0.025)
    elif lv == "north":
        cx, cy, r = W * 0.55, H * 0.45, min(W, H) * 0.32
        pts = []
        for k in range(8):
            rr = r if k % 2 == 0 else r * 0.28
            a = k * math.pi / 4
            pts.append((cx + rr * math.sin(a), cy - rr * math.cos(a)))
        p.poly(pts, fill=light)
    else:
        p.poly([(0, H * 0.95), (W * 0.35, H * 0.95), (W, H * 0.05), (W * 0.75, H * 0.05)], fill=pinc + (255,))
    return p


@lru_cache(maxsize=None)
def fin_tiles(lv, tp):
    z0, ytop, w, h = FINS[tp]
    a = fin_pic(lv, tp).shrunk()
    return frozenset((i, j) for j in range(h) for i in range(w) if a[j * N:(j + 1) * N, i * N:(i + 1) * N, 3].max() > 8)


def fin_tile(lv, tp, k):
    z0, ytop, w, h = FINS[tp]
    t = A.paint(41450 + k + sum(map(ord, lv + tp)), LIVERIES[lv][1], seams=False)
    a = fin_pic(lv, tp).shrunk()
    i, j = k % w, k // w
    sub = a[j * N:(j + 1) * N, i * N:(i + 1) * N]
    wgt = sub[..., 3:4] / 255
    t.c = t.c * (1 - wgt) + sub[..., :3] * wgt
    return t


@lru_cache(maxsize=None)
def fan_pic(key):
    rmax, fx, fy, n = ENGINES[key]
    p = Pic(n, n, (0, 0, 0, 0), 41460)
    cx, cy = fx * N, fy * N
    r = (rmax - 0.25) * N
    p.ellipse(cx - r, cy - r, cx + r, cy + r, fill=(40, 42, 46, 255))
    blades = 22 if rmax > 1.2 else 18
    hub = 0.22 * r
    for k in range(blades):
        a0 = k * 2 * math.pi / blades
        pts = [(cx + hub * math.cos(a0), cy + hub * math.sin(a0))]
        for s in range(6):
            rr = hub + (r - hub) * (s + 1) / 6
            a = a0 + 0.35 * (s + 1) / 6
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        for s in range(6, -1, -1):
            rr = hub + (r - hub) * s / 6
            a = a0 + 0.12 + 0.35 * s / 6
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        p.poly(pts, fill=(150, 154, 160, 255))
    return p


def fan_tile_tex(key, k):
    n = ENGINES[key][3]
    t = F.plain(41470 + k, (40, 42, 46), 0.02, 0.6)
    t.f0[:] = 200
    a = fan_pic(key).shrunk()
    i, j = k % n, k // n
    t.c[:] = a[j * N:(j + 1) * N, i * N:(i + 1) * N, :3]
    return t


def blade(seed, tip=False):
    t = F.plain(seed, (34, 36, 40), 0.03, 0.35)
    if tip:
        area(t, 0, 0, N, N // 5, (214, 176, 40))
    t.grime(0.15)
    return t


def textures():
    for tp, t in TYPES.items():
        for lv in t.liveries:
            shell(tp, lv)   # records the engines' fan pictures and the fins' boxes
    out = {"an_blade": lambda: blade(41001), "an_blade_tip": lambda: blade(41002, True),
           "an_spinner": lambda: A.paint(41003, (196, 198, 200), seams=False)}
    for lv, (name, main, pinc) in LIVERIES.items():
        out[f"an_{lv}_main"] = lambda m=main, lv=lv: A.paint(41010 + sum(map(ord, lv)), m, seams=False)
        out[f"an_{lv}_rudder"] = lambda m=main, lv=lv: A.paint(41011 + sum(map(ord, lv)), tuple(v * 0.92 for v in m))
        out[f"an_{lv}_pin"] = lambda m=pinc, lv=lv: A.paint(41012 + sum(map(ord, lv)), m, seams=False)
        for r in range(2):
            out[f"an_{lv}_exit_{r}"] = lambda lv=lv, r=r: on_paint(exit_pic(lv), r, 41110 + r)
    for tp, t in TYPES.items():
        for lv in t.liveries:
            for c in range(2):
                for r in range(3):
                    out[f"an_{lv}_door_{c}_{r}"] = lambda c=c, r=r, lv=lv, tp=tp: on_paint(door_pic(lv, tp), r * 2 + c, 41100 + r * 2 + c)
            if LIVERIES[lv][0]:
                for k in range(t.title_z[1] - t.title_z[0]):
                    out[f"an_{lv}_{tp}_title_{k}"] = lambda k=k, lv=lv, tp=tp: on_paint(title_pic(lv, tp), k, 41120 + k)
            for k in range(t.reg_z[1] - t.reg_z[0]):
                out[f"an_{lv}_{tp}_reg_{k}"] = lambda k=k, lv=lv, tp=tp: on_paint(reg_pic(lv, tp), k, 41140 + k)
            z0, ytop, w, h = FINS[tp]
            for i, j in fin_tiles(lv, tp):
                out[f"an_{lv}_{tp}_fin_{j * w + i}"] = lambda k=j * w + i, lv=lv, tp=tp: fin_tile(lv, tp, k)
    for key, (rmax, fx, fy, n) in ENGINES.items():
        for k in range(n * n):
            out[fan_name(key, k)] = lambda key=key, k=k: fan_tile_tex(key, k)
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- output

def dump(cells, out):
    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    zs = [c[2] for c in cells]
    pad = 6
    x0, z0 = min(xs) - pad, min(zs) - pad
    sx, sz = max(xs) - x0 + pad + 1, max(zs) - z0 + pad + 1
    H = max(ys) + 4
    palette = ["air", "af_apron"]
    ids = {b: i for i, b in enumerate(palette)}
    vox = np.zeros((H, sz, sx), np.uint16)
    vox[0] = 1
    for (x, y, z), b in cells.items():
        if b not in ids:
            ids[b] = len(palette)
            palette.append(b)
        vox[y, z - z0, x - x0] = ids[b]
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    (out.parent / (out.name + ".json")).write_text(json.dumps({"size": [sx, H, sz], "origin": [x0, 0, z0], "palette": palette,
                                                               "lamps": [], "cars": []}))
    vox.astype("<u2").tofile(out.parent / (out.name + ".vox"))
    print(f"dump {out}: {sx} x {H} x {sz}, origin {x0},{z0}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump")
    ap.add_argument("--only")
    a = ap.parse_args()
    specs, layouts = compile_all(a.only)
    if not a.only:
        path = HERE / "blocks.json"
        data = json.loads(path.read_text())
        data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("an_")}
        data["blocks"].update(specs)
        path.write_text(json.dumps(data, indent=1) + "\n")
    for (tp, lv), lay in layouts.items():
        cells = {c: f"{n}[facing=north]" for c, n in lay.items()}
        for c, b in fittings(tp, lv).items():
            if c in cells:
                raise SystemExit(f"an_: {b} at {c} is in the shell's {cells[c]}")
            cells[c] = b
        name = f"{tp}_{lv}"
        if not a.only:
            with open(LAYOUTS / f"{name}.txt", "w") as f:
                f.write(f"# {name}, written by tools/city/fleet.py: x y z block, the nose to the north\n")
                f.write(header(tp) + "\n")
                for (x, y, z), b in sorted(cells.items(), key=lambda kv: (kv[0][2], kv[0][1], kv[0][0])):
                    f.write(f"{x} {y} {z} {b}\n")
        print(f"{name}: {len(lay)} shell cells, {len(cells)} placed")
        if a.dump:
            dump(cells, Path(a.dump) / name)
    print(f"{len(specs)} an_ shell blocks")


if __name__ == "__main__":
    main()
