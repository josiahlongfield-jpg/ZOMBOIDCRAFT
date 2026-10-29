"""The al_ kit: Gray Eagle flight 214, the twin-jet parked nose-in at gate 1 of Grayhaven
International (Josiah, 2026-10-29: the airport after Modern Warfare 2's "Terminal", with the airliner
you walk through from the jet bridge to the stairs at the back). Built after a 737-800 (refs/airport/
apron-airliners, airliner-cabin) at about one and a half times its size, so the cabin keeps the
mod's four clear blocks over the aisle: 56 blocks nose to tail, 54 across the wings, 8 through the
fuselage. No real airline: the livery is the made-up Gray Eagle's, navy and silver.

A whole round fuselage of whole blocks would step like a staircase, so every block the skin passes
through carries thin panels turned to the curve (as the rd_ kit does for silos, here about the
fuselage's axis): the fuselage, the nacelles and the tail cone are rings of them, the nose and the
tail cone in quarter-block slices that close in toward the tip. The wings, the tailplanes and the
fin are slabs in strips a quarter or half a block across, their thickness after an airfoil, a rod
turned to the sweep along each leading edge. The cabin's linings, bins, ceiling and floor are part
of the same blocks, since the skin passes through most of them.

Every distinct block of it is one generated block, al_s<n>; the plane faces north (the nose at
z = 0 of the model, the tail at z = 56), x across with the captain's side to the west, y = 0 the
apron's surface block (so the wheels stand on y = 1). Its own fittings are named blocks: the cabin
seats, the galleys, the lavatories, the cockpit. The layout of the whole plane, block by block, is
written to data/zomboidcraft/airliner/twinjet.txt for world/county/Airliner.java to stamp.

    python3 tools/city/airliner.py [--dump DIR]   rewrites the al_ entries in blocks.json and the
                                                  layout; --dump also writes a preview dump of it

photo_textures.py draws the textures (airliner.register()).
"""
import argparse
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
from furniture import area, disc as tdisc  # noqa: E402
from school import Pic  # noqa: E402

N = P.N
HERE = Path(__file__).parent
ROOT = HERE.parent.parent
LAYOUT = ROOT / "src/main/resources/data/zomboidcraft/airliner/twinjet.txt"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
NS = None

# the livery
NAVY, SILVER, WHITE, BELLY = (32, 44, 82), (186, 190, 196), (232, 233, 230), (168, 172, 176)
TITLE = "GRAY EAGLE"
REG = "N214GE"

# the fuselage (blocks): a circle of radius R about y = C, x = 0; the cabin floor's top at C - 1
R, C = 4.0, 8.0
FLOOR = C - 1
NOSE, TAIL0, LEN = 9.0, 42.0, 56.0
# the cabin's bands on the side (world y)
WIN = (8.05, 8.65)
CHEAT = (7.12, 7.62)
PIN = (7.7, 7.8)
BELLY_Y = 5.6
TITLE_Y = (9.25, 10.55)
TITLE_Z = (13, 23)
# doors: (side -1 west / +1 east, first z, blocks wide, open)
DOORS = [(-1, 10, 2, True), (1, 10, 2, False), (-1, 42, 2, True), (1, 42, 2, False)]
EXITS = [(-1, 27), (1, 27), (-1, 28), (1, 28)]
WINDOWS_Z = (14, 40)
REG_Y, REG_Z = (9.2, 9.9), (37, 40)


# ---------------------------------------------------------------- shape

def nose(z):
    """The fuselage's centre height and radius at z (blocks from the nose)."""
    if z < NOSE:
        s = min(1.0, max(0.0, z / NOSE))
        return C - 1.3 * (1 - s) ** 2, R * max(0.0, 1 - (1 - s) ** 2) ** 0.75
    if z <= TAIL0:
        return C, R
    s = min(1.0, (z - TAIL0) / (LEN - TAIL0))
    top = C + R - 0.9 * s * s
    bottom = C - R + (2 * R - 0.9 - 1.1) * s ** 1.6
    return (top + bottom) / 2, (top - bottom) / 2


def airfoil(f):
    """Thickness along the chord, 1 at its thickest (a third back), 0.06 at the trailing edge."""
    f = min(1.0, max(0.0, f))
    t = 1.48 * math.sqrt(f) * (1 - f) ** 0.9 + 0.06 * f
    return min(1.0, t / 0.86)


# ---------------------------------------------------------------- boxes

DIRS = ["down", "up", "north", "south", "west", "east"]
OPP = {"up": "down", "down": "up", "east": "west", "west": "east", "north": "south", "south": "north"}
AUTO_UV = {
    "down": lambda f, t: [f[0], 16 - t[2], t[0], 16 - f[2]],
    "up": lambda f, t: [f[0], f[2], t[0], t[2]],
    "north": lambda f, t: [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
    "south": lambda f, t: [f[0], 16 - t[1], t[0], 16 - f[1]],
    "west": lambda f, t: [f[2], 16 - t[1], t[2], 16 - f[1]],
    "east": lambda f, t: [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
}
FULL = [0, 0, 16, 16]


def fit(a0, a1):
    span = a1 - a0
    if span > 16:
        return 0, 16
    if a0 < 0:
        return 0, span
    if a1 > 16:
        return 16 - span, 16
    return a0, a1


class Box:
    """A box of the plane in blocks (before any turn). faces: face -> (texture, uv) with uv None for
    Minecraft's own, FULL for the whole texture, "flip" for the whole texture mirrored; only these
    faces are drawn. rot: (axis, angle, origin) turns it about that point (blocks); a turned box
    goes to the block its origin is in, a straight one is cut at the blocks' edges."""
    __slots__ = ("lo", "hi", "faces", "rot", "glass")

    def __init__(self, lo, hi, faces, rot=None, glass=False):
        self.lo, self.hi, self.faces, self.rot, self.glass = list(lo), list(hi), faces, rot, glass


def all_faces(tex, uv=None, skip=()):
    return {f: (tex[f] if isinstance(tex, dict) else tex, uv) for f in DIRS if f not in skip}


def cut(b):
    """A straight box cut at the block edges: each piece in its block, the cut faces dropped."""
    if b.rot:
        return [b]
    pieces = [b]
    for ax, (lo_f, hi_f) in enumerate((("west", "east"), ("down", "up"), ("north", "south"))):
        out = []
        for q in pieces:
            a, z = q.lo[ax], q.hi[ax]
            k0, k1 = math.floor(a + 1e-6), math.floor(z - 1e-6)
            if k0 >= k1:
                out.append(q)
                continue
            edges = [a] + [float(k) for k in range(k0 + 1, k1 + 1)] + [z]
            for i in range(len(edges) - 1):
                lo, hi = list(q.lo), list(q.hi)
                lo[ax], hi[ax] = edges[i], edges[i + 1]
                faces = dict(q.faces)
                if i > 0:
                    faces.pop(lo_f, None)
                if i < len(edges) - 2:
                    faces.pop(hi_f, None)
                out.append(Box(lo, hi, faces, None, q.glass))
        pieces = out
    return pieces


class Shell:
    """The plane's boxes gathered block by block."""

    def __init__(self):
        self.cells = {}

    def add(self, b):
        for q in cut(b):
            if q.rot:
                o = q.rot[2]
            else:
                o = [(q.lo[i] + q.hi[i]) / 2 for i in range(3)]
            cell = tuple(math.floor(o[i] + 1e-9) for i in range(3))
            self.cells.setdefault(cell, []).append(q)

    def model(self, cell):
        """The block at cell as gen_blocks boxes, its collision box and whether it holds glass."""
        out, lo, hi, glass = [], [16.0] * 3, [0.0] * 3, False
        for q in self.cells[cell]:
            a = [round((q.lo[i] - cell[i]) * 16, 3) for i in range(3)]
            z = [round((q.hi[i] - cell[i]) * 16, 3) for i in range(3)]
            if min(a) < -16 or max(z) > 32:
                raise SystemExit(f"al_: box out of reach in {cell}: {a} {z}")
            if any(z[i] - a[i] < 0.01 for i in range(3)):
                continue
            tex, uvs = {}, {}
            for f, (t, uv) in q.faces.items():
                tex[f] = t
                if uv is None:
                    u0, v0, u1, v1 = AUTO_UV[f](a, z)
                    (u0, u1), (v0, v1) = fit(u0, u1), fit(v0, v1)
                    uvs[f] = [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]
                elif uv == "flip":
                    uvs[f] = [16, 0, 0, 16]
                elif uv == "auto_flip":
                    u0, v0, u1, v1 = AUTO_UV[f](a, z)
                    (u0, u1), (v0, v1) = fit(u0, u1), fit(v0, v1)
                    uvs[f] = [round(16 - u0, 3), round(v0, 3), round(16 - u1, 3), round(v1, 3)]
                else:
                    uvs[f] = list(uv)
            if not tex:
                continue
            for f in DIRS:
                tex.setdefault(f, next(iter(tex.values())))
            box = a + z + [tex, uvs]
            if q.rot:
                axis, ang, o = q.rot
                box.append({"axis": axis, "angle": round(ang, 3),
                            "origin": [round((o[i] - cell[i]) * 16, 3) for i in range(3)]})
                corners = turned_corners(a, z, box[-1])
            else:
                corners = [a, z]
            for p in corners:
                for i in range(3):
                    lo[i] = min(lo[i], max(0.0, p[i]))
                    hi[i] = max(hi[i], min(16.0, p[i]))
            out.append(box)
            glass |= q.glass
        shape = [[round(lo[i], 2) for i in range(3)] + [round(hi[i], 2) for i in range(3)]] \
            if all(hi[i] - lo[i] >= 0.5 for i in range(3)) else []
        return out, shape, glass


def turned_corners(a, z, rot):
    i, j = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[rot["axis"]]
    o = rot["origin"]
    t = math.radians(rot["angle"])
    pts = []
    for cx in (a[0], z[0]):
        for cy in (a[1], z[1]):
            for cz in (a[2], z[2]):
                p = [cx - o[0], cy - o[1], cz - o[2]]
                q = list(p)
                q[i] = p[i] * math.cos(t) - p[j] * math.sin(t)
                q[j] = p[i] * math.sin(t) + p[j] * math.cos(t)
                pts.append([q[0] + o[0], q[1] + o[1], q[2] + o[2]])
    return pts


# ---------------------------------------------------------------- rings: the fuselage, the nacelles

BASES = [(0, "up"), (90, "east"), (180, "down"), (270, "west")]
STEP = 0.05


def ring(sh, z0, z1, cx, cy, r, mat, thick=3.0, ends=(), skip=None, inner_ends=()):
    """Panels round the circle (cx, cy, r) from z0 to z1 (blocks): one per run of the circle through a
    block, its outer face on the circle, `thick` px thick. mat(x, y, z, theta) gives a point's
    (outer texture, inner texture, outer uv, inner uv, glass) or None for no skin; theta in degrees,
    0 the top, 90 the east. ends: the faces of the slice's ends to draw ("north", "south"), and
    inner_ends those that show the lining (the steps seen from inside a narrowing nose or tail).
    skip(i, j) leaves out the block's panels (a door)."""
    th = np.arange(0, 360, STEP)
    rad = np.radians(th)
    xs, ys = cx + r * np.sin(rad), cy + r * np.cos(rad)
    key = np.floor(xs).astype(int) * 4096 + np.floor(ys).astype(int)
    change = np.nonzero(key != np.roll(key, 1))[0]
    if len(change) == 0:
        return
    zm = (z0 + z1) / 2
    for n in range(len(change)):
        ka = change[n]
        kb = change[n + 1] if n + 1 < len(change) else change[0] + len(th)
        ta = ka * STEP
        tb = kb * STEP
        tm = (ta + tb) / 2
        half = math.radians(tb - ta) / 2
        length = 2 * r * math.sin(half) * 16 + 1.2
        tmr = math.radians(tm)
        px, py = cx + r * math.sin(tmr), cy + r * math.cos(tmr)
        cell = (math.floor(px), math.floor(py))
        if skip and skip(*cell):
            continue
        base, bname = min(BASES, key=lambda b: abs(((tm - b[0] + 180) % 360) - 180))
        ang = ((base - tm + 180) % 360) - 180
        s = 1 if bname in ("up", "west") else -1
        dx, dy = s * math.cos(tmr), -s * math.sin(tmr)
        # the panel's length cut where its look changes
        us = np.arange(-length / 2, length / 2 + 0.01, 0.5)
        runs = []
        for u in us:
            qx, qy = px + u / 16 * dx, py + u / 16 * dy
            m = mat(qx, qy, zm, math.degrees(math.atan2(qx - cx, qy - cy)) % 360)
            if runs and runs[-1][1] == m:
                runs[-1][0][1] = u
            else:
                runs.append([[u, u], m])
        for k, ((u0, u1), m) in enumerate(runs):
            if m is None:
                continue
            a = -length / 2 if k == 0 else (runs[k - 1][0][1] + u0) / 2
            b = length / 2 if k == len(runs) - 1 else (u1 + runs[k + 1][0][0]) / 2
            outer, inner, uvo, uvi, glass = m
            t = thick / 16
            pa, pb = a / 16, b / 16
            # u runs along the box's long side (x for up/down, y for east/west) as turned, so (dx, dy)
            if bname == "up":
                lo, hi = [px + pa, py - t, z0], [px + pb, py, z1]
            elif bname == "down":
                lo, hi = [px + pa, py, z0], [px + pb, py + t, z1]
            elif bname == "east":
                lo, hi = [px - t, py + pa, z0], [px, py + pb, z1]
            else:
                lo, hi = [px, py + pa, z0], [px + t, py + pb, z1]
            ya_, yb_ = py + pa * dy, py + pb * dy
            ylo, yhi = min(ya_, yb_), max(ya_, yb_)
            faces = {bname: (outer, band_uv(uvo, ylo, yhi))}
            if inner:
                faces[OPP[bname]] = (inner, band_uv(uvi, ylo, yhi))
            for e in ends:
                faces[e] = (outer, None)
            for e in inner_ends:
                faces[e] = (inner or outer, None)
            sh.add(Box(lo, hi, faces, ("z", ang, [px, py, zm]), glass))


def band_uv(uv, ylo, yhi):
    """("band", y0, y1): the part of a picture spread over that height (world y) this piece shows."""
    if not isinstance(uv, tuple):
        return uv
    _, y0, y1 = uv
    v0 = max(0.0, min(16.0, 16 * (y1 - yhi) / (y1 - y0)))
    v1 = max(0.0, min(16.0, 16 * (y1 - ylo) / (y1 - y0)))
    return [0, round(v0, 3), 16, round(v1, 3)]


def discfill(sh, z0, z1, cx, cy, r, tex, faces=("north", "south"), step=0.25, hole=None, glass=False):
    """The circle (cx, cy, r) filled across from z0 to z1, in strips `step` wide; tex(cell) or a name.
    hole(x, y) leaves a strip out (a doorway)."""
    x = math.floor((cx - r) / step) * step
    while x < cx + r:
        xa, xb = x, x + step
        far = max(abs(xa - cx), abs(xb - cx))
        if far < r:
            h = math.sqrt(r * r - far * far)
            y0, y1 = cy - h, cy + h
            if hole:
                spans = hole(xa, xb, y0, y1)
            else:
                spans = [(y0, y1)]
            for ya, yb in spans:
                # cut at the blocks' edges here, so each piece takes its own block's picture
                edges = [ya] + [float(k) for k in range(math.floor(ya) + 1, math.ceil(yb))] + [yb]
                for k in range(len(edges) - 1):
                    pa, pb = edges[k], edges[k + 1]
                    if pb - pa < 0.02:
                        continue
                    cell = (math.floor((xa + xb) / 2), math.floor((pa + pb) / 2))
                    t = tex(cell) if callable(tex) else tex
                    fs = {f: (t, None) for f in faces + ("east", "west")}
                    if k == 0:
                        fs["down"] = (t, None)
                    if k == len(edges) - 2:
                        fs["up"] = (t, None)
                    sh.add(Box([xa, pa, z0], [xb, pb, z1], fs, glass=glass))
        x += step


# ---------------------------------------------------------------- the skin's look

def door_at(side, z):
    for d in DOORS:
        if d[0] == side and d[1] <= z < d[1] + d[2]:
            return d
    return None


def skin(x, y, z, th, nacelle=False):
    if nacelle:
        return ("al_cowl", "al_intake", None, None, False)
    side = -1 if x < 0 else 1
    inside = "al_lining" if FLOOR - 0.2 < y < 9.6 and NOSE <= z < 47 else "al_dark"
    if z < NOSE:
        inside = "al_ck_lining" if y > FLOOR - 0.2 and z > 3 else "al_dark"
        # the cockpit's glazing: the windscreen up the nose's slope, the side windows behind it
        a = th if th <= 180 else 360 - th
        if 2.4 <= z < 4.4 and 6 <= a <= 50 and y > 8.3:
            return ("al_ck_glass", "al_ck_glass_in", None, None, True)
        if 4.4 <= z < 6.4 and 52 <= a <= 70 and 8.4 < y < 9.7:
            return ("al_ck_glass", "al_ck_glass_in", None, None, True)
        if z < 1.6:
            return ("al_radome", inside, None, None, False)
    zi = math.floor(z)
    # the doors and over-wing exits take the side's whole blocks
    d = door_at(side, zi)
    if d and 7 <= y < 10 and abs(x) > 3:
        k = zi - d[1] if side < 0 else d[1] + d[2] - 1 - zi
        j = math.floor(y)
        return (f"al_door_{k}_{9 - j}", inside, ("band", j, j + 1), None, False)
    if (side, zi) in EXITS and 7 <= y < 9 and abs(x) > 3:
        j = math.floor(y)
        return (f"al_exit_{8 - j}", inside, ("band", j, j + 1), None, False)
    if WINDOWS_Z[0] <= zi < WINDOWS_Z[1] and WIN[0] <= y < WIN[1] and abs(x) > 3:
        return ("al_win", "al_win_in", ("band",) + WIN, ("band",) + WIN, True)
    if TITLE_Z[0] <= zi < TITLE_Z[1] and TITLE_Y[0] <= y < TITLE_Y[1] and abs(x) > 2.5:
        k = zi - TITLE_Z[0] if side < 0 else TITLE_Z[1] - 1 - zi
        return (f"al_title_{k}", inside, ("band",) + TITLE_Y, None, False)
    if REG_Z[0] <= zi < REG_Z[1] and REG_Y[0] <= y < REG_Y[1] and abs(x) > 2.5:
        k = zi - REG_Z[0] if side < 0 else REG_Z[1] - 1 - zi
        return (f"al_reg_{k}", inside, ("band",) + REG_Y, None, False)
    if CHEAT[0] <= y < CHEAT[1]:
        return ("al_navy", inside, None, None, False)
    if PIN[0] <= y < PIN[1]:
        return ("al_silver", inside, None, None, False)
    if y < BELLY_Y:
        return ("al_belly", inside, None, None, False)
    return ("al_paint", inside, None, None, False)


def door_skip(i, j, z):
    for side, z0, w, open_ in DOORS:
        if open_ and z0 <= z < z0 + w and 7 <= j <= 9 and (i == -4 if side < 0 else i == 3):
            return True
    return False


def fuselage(sh):
    # the barrel: one ring a block, the cabin's fittings in the same blocks
    for z in range(int(NOSE), int(TAIL0)):
        ring(sh, z, z + 1, 0.0, C, R, lambda x, y, zz, th: skin(x, y, zz, th),
             ends=door_ends(z), skip=lambda i, j, z=z: door_skip(i, j, z))
    # the nose and the tail cone in quarter-block slices closing in to the tips
    for k in range(int(NOSE * 8)):
        z0, z1 = k / 8, (k + 1) / 8
        cy, r = nose((z0 + z1) / 2)
        cy0, r0 = nose(z0)
        ring(sh, z0, z1, 0.0, cy, r, lambda x, y, zz, th: skin(x, y, zz, th),
             thick=max(3.0, (r - r0) * 16 + 1.5), ends=("north",), inner_ends=("south",))
    discfill(sh, 0.0, 0.125, 0.0, nose(0.06)[0], max(0.1, nose(0.06)[1] - 0.05), "al_radome")
    for k in range(int((LEN - TAIL0) * 4)):
        z0, z1 = TAIL0 + k / 4, TAIL0 + (k + 1) / 4
        cy, r = nose((z0 + z1) / 2)
        cy1, r1 = nose(z1)
        ring(sh, z0, z1, 0.0, cy, r, lambda x, y, zz, th: skin(x, y, zz, th),
             thick=max(3.0, (r - r1) * 16 + 1.5), ends=("south",), inner_ends=("north",),
             skip=lambda i, j, z=math.floor(z0): door_skip(i, j, z))
    cy, r = nose(LEN - 0.125)
    discfill(sh, LEN - 0.25, LEN - 0.05, 0.0, cy, r - 0.08, "al_belly")
    discfill(sh, LEN - 0.05, LEN, 0.0, cy, r - 0.3, "al_exhaust", faces=("south",))


def door_ends(z):
    """The jambs of an open door: the panels either side of it show their cut ends."""
    out = []
    for side, z0, w, open_ in DOORS:
        if open_ and z == z0 - 1:
            out.append("south")
        if open_ and z == z0 + w:
            out.append("north")
    return tuple(out)


# ---------------------------------------------------------------- the cabin's fittings in the barrel

def cabin(sh, rows):
    """Floor, bins, the passenger service units, ceiling and coves from the cockpit door to the aft
    bulkhead. rows: z -> 'closed' | 'open' | 'masks' for the bins over that block."""
    # the floor: carpet from the nose gear bay to the aft bulkhead
    for k in range(int(3.5 * 4), int(47 * 4)):
        z0, z1 = k / 4, (k + 1) / 4
        cy, r = nose((z0 + z1) / 2)
        if r < 2:
            continue
        step = 0.25
        x = -math.floor(r / step) * step
        while x < r:
            xa, xb = x, x + step
            far = max(abs(xa), abs(xb))
            if far < r:
                low = cy - math.sqrt(r * r - far * far)
                ya = max(FLOOR - 1, low)
                if ya < FLOOR - 0.02:
                    sh.add(Box([xa, ya, z0], [xb, FLOOR, z1],
                               {"up": ("al_carpet", None), "down": ("al_dark", None), "east": ("al_dark", None),
                                "west": ("al_dark", None), "north": ("al_dark", None), "south": ("al_dark", None)}))
            x += step
    for z in range(14, 40):
        kind = rows.get(z, "closed")
        for side in (-1, 1):
            bins(sh, z, side, kind)
        # the ceiling between the bins and its light coves
        sh.add(Box([-1.85, 11.02, z], [1.85, 11.12, z + 1],
                   {"down": ("al_ceiling", None), "up": ("al_dark", None)}))
        for side in (-1, 1):
            x = 1.85 * side
            sh.add(Box([min(x, x - 0.06 * side), 10.6, z], [max(x, x - 0.06 * side), 11.02, z + 1],
                       {"east" if side < 0 else "west": ("al_cove", None)}))
    # the entry and galley areas fore and aft: ceiling without bins
    for z in list(range(9, 14)) + list(range(40, 47)):
        cy, r = nose(z + 0.5)
        hw = math.sqrt(max(0.0, r * r - (11.05 - cy) ** 2)) - 0.05
        if hw > 0.5:
            sh.add(Box([-hw, 11.02, z], [hw, 11.12, z + 1], {"down": ("al_ceiling_plain", None), "up": ("al_dark", None)}))


def bins(sh, z, side, kind):
    s = side
    face = "east" if s < 0 else "west"
    back = "west" if s < 0 else "east"

    def xr(a, b):
        return (min(a * s, b * s), max(a * s, b * s))

    x0, x1 = xr(3.3, 1.8)
    door = "al_bin_open" if kind == "open" else "al_bin"
    sh.add(Box([x0, 9.5, z], [x1, 10.1, z + 1],
               {face: (door, FULL), "down": ("al_psu", "flip" if s > 0 else FULL), back: ("al_dark", None)}))
    x0, x1 = xr(2.95, 1.8)
    sh.add(Box([x0, 10.1, z], [x1, 10.6, z + 1], {face: ("al_bin_top", None), "up": ("al_dark", None)}))
    if kind == "open":
        # the door swung up, a bag half out
        xa, xb = xr(1.8, 1.75)
        sh.add(Box([xa, 10.6, z + 0.05], [xb, 11.0, z + 0.95], all_faces("al_bin_door_in")))
        xa, xb = xr(2.6, 1.85)
        sh.add(Box([xa, 9.55, z + 0.25], [xb, 9.95, z + 0.75], all_faces("ap_bag_red" if (z * 7) % 3 == 0 else "ap_bag_navy")))
    if kind == "masks":
        for k, mx in enumerate((3.05, 2.6, 2.15)):
            x = mx * s
            mz = z + 0.3 + 0.2 * (k % 2)
            drop = 0.75 + 0.18 * ((z + k) % 3)
            sh.add(Box([x - 0.012, 9.5 - drop, mz - 0.012], [x + 0.012, 9.5, mz + 0.012], all_faces("al_tube")))
            sh.add(Box([x - 0.09, 9.5 - drop - 0.12, mz - 0.09], [x + 0.09, 9.5 - drop, mz + 0.09], all_faces("al_mask")))


# ---------------------------------------------------------------- wings, tailplanes, fin

def planform(sh, root, tip, le, te, ymid, thick, mats, side, dz=0.25, dx=0.5, edge="al_metal", top_tex=None):
    """A wing on one side: |x| from root to tip, the leading and trailing edges le(ax), te(ax) (z),
    its middle at ymid(ax) and thickness thick(ax), in strips dx across and dz along; mats(f) gives
    the top, the underside and the sides at chord fraction f. A rod turned to the sweep rounds the
    leading edge of each strip."""
    ax = root
    while ax < tip - 1e-6:
        a0, a1 = ax, min(tip, ax + dx)
        am = (a0 + a1) / 2
        zl, zt = le(am), te(am)
        ym, t = ymid(am), thick(am)
        z = math.floor(zl / dz) * dz
        while z < zt - 1e-6:
            za, zb = max(z, zl), min(z + dz, zt)
            if zb - za > 0.01:
                f = ((za + zb) / 2 - zl) / (zt - zl)
                h = t * airfoil(f)
                top, under, sides = mats(f)
                if top_tex:
                    top = top_tex(side * am, (za + zb) / 2, top)
                xa, xb = (a0, a1) if side > 0 else (-a1, -a0)
                sh.add(Box([xa, ym - 0.36 * h, za], [xb, ym + 0.64 * h, zb],
                           {"up": (top, None), "down": (under, None), "north": (sides, None), "south": (sides, None),
                            "east": (sides, None), "west": (sides, None)}))
            z += dz
        # the leading edge's rod along the sweep
        z_a, z_b = le(a0), le(a1)
        sweep = math.degrees(math.atan2(z_b - z_a, a1 - a0))
        h = t * 0.62
        span = math.hypot(a1 - a0, z_b - z_a) + 0.04
        mx = (a0 + a1) / 2 * side
        mz = (z_a + z_b) / 2 + 0.05
        ang = -sweep if side > 0 else sweep
        sh.add(Box([mx - span / 2, ym - 0.3 * h, mz - 0.06], [mx + span / 2, ym + 0.62 * h, mz + 0.06],
                   all_faces(edge), ("y", ang, [mx, ym, mz])))
        ax = a1


def wing_mats(f):
    if f < 0.07:
        return "al_metal", "al_metal", "al_metal"
    if f > 0.74:
        return "al_flap", "al_wing_under", "al_wing_under"
    return "al_wing", "al_wing_under", "al_wing_under"


def walkway(x, z, tex):
    # the black-edged walkway over the wing root, NO STEP beyond it
    if tex == "al_wing" and 5 < abs(x) < 7.5:
        return "al_walkway"
    return tex


def wings(sh):
    le = lambda a: 19 + 0.466 * (a - 2)  # noqa: E731
    te = lambda a: 30 + 0.13 * (a - 2)  # noqa: E731
    ym = lambda a: 4.6 + 0.105 * (a - 2)  # noqa: E731
    th = lambda a: 1.0 - 0.7 * (a - 2) / 25  # noqa: E731
    for side in (-1, 1):
        planform(sh, 2.0, 27.0, le, te, ym, th, wing_mats, side, top_tex=walkway)
        # the winglet, canted out and swept, navy: each piece of it turned about its own middle
        a = -14 * side
        for k in range(12):
            hk = 0.32 + k * 0.25
            ch = 2.3 - 0.12 * (k + 0.5)
            za = le(27) + 0.25 + 0.18 * (k + 0.5)
            cx = 27.0 * side - math.sin(math.radians(a)) * hk
            cy = ym(27) + math.cos(math.radians(a)) * hk
            z = za
            while z < za + ch - 1e-6:
                zb = min(za + ch, math.floor(z) + 1.0)
                sh.add(Box([cx - 0.07, cy - 0.125, z], [cx + 0.07, cy + 0.125, zb], all_faces("al_navy"),
                           ("z", a, [cx, cy, (z + zb) / 2])))
                z = zb
        # the navigation light at the tip
        x = 27.0 * side
        sh.add(Box([x - 0.1, ym(27) - 0.1, le(27) + 0.6], [x + 0.1, ym(27) + 0.1, le(27) + 0.9],
                   all_faces("al_nav_red" if side < 0 else "al_nav_green")))
        # flap track fairings under the trailing edge
        for a in (7.5, 12.5, 17.5):
            x = a * side
            z0 = te(a) - 2.2
            sh.add(Box([x - 0.18, ym(a) - 0.75, z0], [x + 0.18, ym(a) - 0.2, te(a) + 0.9],
                       all_faces("al_wing_under")))
        engine(sh, 11.0 * side, ym(11) - 0.36 * th(11))
    # the main gear under the wing roots
    for side in (-1, 1):
        gear(sh, 4.6 * side, 29.4, ym(4.6) - 0.36 * th(4.6), 0.92, 0.62)
    gear(sh, 0.0, 6.6, nose(6.6)[0] - nose(6.6)[1] + 0.2, 0.72, 0.4, nosegear=True)


def engine(sh, xe, wing_under):
    ye = 3.25
    z0, z1 = 18.0, 26.0
    prof = lambda z: 1.52 if z < 18.25 else 1.7 if z < 23.5 else 1.7 - 0.45 * (z - 23.5) / 2.5  # noqa: E731
    for k in range(int((z1 - z0) * 4)):
        za, zb = z0 + k / 4, z0 + (k + 1) / 4
        r = prof((za + zb) / 2)
        lip = k == 0
        ring(sh, za, zb, xe, ye, r,
             lambda x, y, z, t, lip=lip: ("al_metal" if lip else "al_cowl", "al_intake", None, None, False),
             thick=4.0 if lip else 2.5, ends=("north",) if lip else ("south",) if k == (z1 - z0) * 4 - 1 else ())
    # the fan behind the lip, the spinner in front of it
    discfill(sh, 18.55, 18.65, xe, ye, 1.45, lambda c: fan_tile(xe, ye, c), faces=("north",))
    for k, r in enumerate((0.42, 0.34, 0.22, 0.1)):
        discfill(sh, 18.55 - (k + 1) * 0.12, 18.55 - k * 0.12, xe, ye, r, "al_spinner", faces=("north", "up", "down", "east", "west"))
    # the exhaust: the hot nozzle's soot, the plug tapering out of it
    for k, r in enumerate((0.95, 0.82, 0.66, 0.48, 0.3, 0.14)):
        discfill(sh, 25.7 + k * 0.33, 26.03 + k * 0.33, xe, ye, r, "al_exhaust_plug", faces=("south", "up", "down", "east", "west"))
    # the pylon up to the wing
    sh.add(Box([xe - 0.17, ye + 1.55, 19.2], [xe + 0.17, wing_under + 0.2, 26.6], all_faces("al_cowl")))
    sh.add(Box([xe - 0.15, ye + 1.0, 25.5], [xe + 0.15, ye + 1.7, 27.6], all_faces("al_cowl")))


def fan_tile(xe, ye, cell):
    """The fan face: one picture over the 4 x 4 blocks round the engine's middle."""
    i = cell[0] - math.floor(xe - 2)
    j = math.floor(ye + 2) - cell[1]
    return f"al_fan_{max(0, min(3, j)) * 4 + max(0, min(3, i))}"


def gear(sh, x, z, top, wheel_r, gap, nosegear=False):
    axle = 1.0 + wheel_r
    # the oleo: grey upper, chrome slider, the torque links
    sh.add(Box([x - 0.16, axle + 0.25, z - 0.16], [x + 0.16, top, z + 0.16], all_faces("al_strut")))
    sh.add(Box([x - 0.11, axle - 0.05, z - 0.11], [x + 0.11, axle + 0.6, z + 0.11], all_faces("al_chrome")))
    sh.add(Box([x - 0.04, axle + 0.3, z + 0.11], [x + 0.04, axle + 0.75, z + 0.3], all_faces("al_strut")))
    # the axle and the wheels either side
    sh.add(Box([x - gap - 0.15, axle - 0.08, z - 0.08], [x + gap + 0.15, axle + 0.08, z + 0.08], all_faces("al_strut")))
    w = 0.36 if not nosegear else 0.26
    for s in (-1, 1):
        cx = x + s * gap
        rr = wheel_r * math.cos(math.radians(11.25))
        for k, ang in enumerate((0, 22.5, 45, -22.5)):
            inset = 0 if k == 0 else 0.004
            faces = {f: ("al_tyre", None) for f in ("up", "down", "north", "south")}
            if k == 0:
                faces["east"] = ("al_hub", FULL)
                faces["west"] = ("al_hub", FULL)
            sh.add(Box([cx - w / 2 + inset, axle - rr, z - rr], [cx + w / 2 - inset, axle + rr, z + rr], faces,
                       ("x", ang, [cx, axle, z])))
    if not nosegear:
        # the gear door hanging off the leg
        sh.add(Box([x + 0.2 * (1 if x > 0 else -1) - 0.03, axle + 0.6, z - 0.7],
                   [x + 0.2 * (1 if x > 0 else -1) + 0.03, top - 0.1, z + 0.5], all_faces("al_belly")))


def tail(sh):
    # the fin: chord along z, up from the tail cone's crown
    y0, y1 = 11.2, 21.5
    le = lambda y: 41.5 + 0.92 * (y - y0)  # noqa: E731
    te = lambda y: 53.2 + 0.17 * (y - y0)  # noqa: E731
    th = lambda y: 0.6 - 0.28 * (y - y0) / (y1 - y0)  # noqa: E731
    y = y0
    while y < y1 - 1e-6:
        # strips on the half-block grid, so each lies in one block and shows that block's picture
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
                           {"west": (tex, None), "east": (tex, "auto_flip"), "up": ("al_navy", None),
                            "down": ("al_navy", None), "north": ("al_navy", None), "south": ("al_navy", None)}))
            z += 0.5
        # the leading edge's rod
        za, zb = le(ya), le(yb)
        ang = math.degrees(math.atan2(zb - za, yb - ya))
        my, mz = (ya + yb) / 2, (za + zb) / 2 + 0.05
        span = math.hypot(yb - ya, zb - za) + 0.04
        sh.add(Box([-th(my) * 0.33, my - span / 2, mz - 0.06], [th(my) * 0.33, my + span / 2, mz + 0.06],
                   all_faces("al_navy"), ("x", ang, [0.0, my, mz])))
        y = yb
    # the dorsal fillet running into the fin's root
    for k in range(10):
        z0 = 38.0 + k * 0.35
        top = nose(z0 + 0.2)[0] + nose(z0 + 0.2)[1] + 0.05 + 0.06 * k * k / 9
        sh.add(Box([-0.22, top - 0.3, z0], [0.22, top, z0 + 0.35], all_faces("al_navy")))
    # the strobe on the cone's tip
    sh.add(Box([-0.08, nose(LEN - 0.2)[0] + 0.45, LEN - 0.4], [0.08, nose(LEN - 0.2)[0] + 0.6, LEN - 0.1],
               all_faces("al_nav_white")))
    # the tailplanes
    le2 = lambda a: 47.6 + 0.6 * (a - 1.4)  # noqa: E731
    te2 = lambda a: 53.4 + 0.15 * (a - 1.4)  # noqa: E731
    ym2 = lambda a: 9.05 + 0.12 * (a - 1.4)  # noqa: E731
    th2 = lambda a: 0.48 - 0.26 * (a - 1.4) / 9  # noqa: E731
    for side in (-1, 1):
        planform(sh, 1.4, 10.4, le2, te2, ym2, th2,
                 lambda f: ("al_metal", "al_metal", "al_metal") if f < 0.07 else ("al_tailplane", "al_wing_under", "al_wing_under"),
                 side, dx=0.5)


FIN_PIC = (41, 22, 14, 11)   # the emblem's picture: from z 41 and y 22 down, 14 by 11 blocks


def fin_tex(cell, f):
    z0, ytop, w, h = FIN_PIC
    i, j = cell[0] - z0, ytop - 1 - cell[1]
    if 0 <= i < w and 0 <= j < h and (i, j) in fin_tiles():
        return f"al_fin_{j * w + i}"
    return "al_rudder" if f > 0.7 else "al_navy"


# ---------------------------------------------------------------- the plane

def bin_rows():
    rows = {}
    rng = np.random.default_rng(214)
    for z in range(14, 40):
        r = rng.random()
        rows[z] = "masks" if 22 <= z <= 36 and r < 0.55 else "open" if r < 0.75 else "closed"
    return rows


@lru_cache(maxsize=None)
def shell():
    sh = Shell()
    fuselage(sh)
    cabin(sh, bin_rows())
    wings(sh)
    tail(sh)
    bulkheads(sh)
    return sh


def bulkheads(sh):
    """The cockpit's rear wall with its doorway, the aft pressure bulkhead behind the galley."""
    def doorway(xa, xb, y0, y1):
        if xb > -1.0 and xa < 1.0:
            return [(max(y0, FLOOR + 3.4), y1)] if y1 > FLOOR + 3.4 else []
        return [(max(y0, FLOOR - 0.01), y1)]
    cy, r = nose(9.0)
    discfill(sh, 8.92, 9.0, 0.0, cy, r - 0.3, "al_ck_lining", hole=doorway)
    cy, r = nose(47.0)
    discfill(sh, 47.0, 47.1, 0.0, cy, r - 0.25, "al_lining", hole=lambda xa, xb, y0, y1: [(max(y0, FLOOR - 0.01), y1)])


def compile_shell():
    """The shell's blocks, deduplicated: name -> spec, and the layout cell -> name."""
    sh = shell()
    specs, names, layout = {}, {}, {}
    for cell in sorted(sh.cells, key=lambda c: (c[2], c[1], c[0])):
        boxes, shape, glass = sh.model(cell)
        if not boxes:
            continue
        key = json.dumps([boxes, shape, glass], sort_keys=True)
        if key not in names:
            name = f"al_s{len(names)}"
            names[key] = name
            spec = {"model": "boxes", "boxes": boxes, "title": "Airliner", "sound": "METAL", "rotate": "facing",
                    "shape": shape}
            spec["render"] = "translucent" if glass else "cutout"
            specs[name] = spec
        layout[cell] = names[key]
    return specs, layout


# ---------------------------------------------------------------- textures

def paint(seed, rgb=WHITE, seams=True, gloss=0.62):
    """Aircraft paint: a clean gloss with the skin's lap joints and rivet rows faint under it, the
    grime of years of jet wash low down."""
    t = F.plain(seed, rgb, 0.025, gloss)
    t.f0[:] = 30
    if seams:
        for y in (0, 32):
            area(t, 0, y, N, y + 1, mul=0.9, dh=-0.05)
        for x in (0, 21, 43):
            area(t, x, 0, x + 1, N, mul=0.93, dh=-0.04)
        for y in (4, 36):
            for x in range(2, N, 4):
                area(t, x, y, x + 1, y + 1, mul=0.86)
    t.grime(0.08)
    return t


def lining(seed, rgb=(214, 210, 200)):
    t = F.plain(seed, rgb, 0.03, 0.4)
    t.speckle(0.05, (180, 176, 168), 0.3)
    t.grime(0.12)
    return t


def win(seed, inner=False):
    """Two cabin windows: the pane see-through, the paint (or, inside, the lining's deep reveal)
    round it; inside, a shade pulled half down on some."""
    rng = np.random.default_rng(seed)
    t = lining(seed) if inner else paint(seed, seams=False)
    yy, xx = np.mgrid[0:N, 0:N]
    for cx in (16, 48):
        rx, ry = (11, 27) if inner else (8.5, 24)
        d = ((xx - cx) / rx) ** 4 + ((yy - 32) / ry) ** 4
        frame = (d < 1.0)
        pane = ((xx - cx) / (rx - 2.5)) ** 4 + ((yy - 32) / (ry - 3)) ** 4 < 1.0
        t.c[frame & ~pane] = (150, 150, 146) if inner else (96, 100, 106)
        t.c[pane] = (60, 74, 86)
        t.alpha[pane] = 70
        t.smooth[pane] = 0.95
        if inner and rng.random() < 0.6:
            down = int(rng.uniform(0.25, 0.7) * N)
            m = pane & (yy < down)
            t.c[m] = (200, 198, 190)
            t.alpha[m] = 255
            t.c[pane & (yy == down)] = (120, 120, 116)
        if not inner:
            # the sky in the glass
            t.c[pane & (yy < 22)] = (110, 126, 140)
    return t


def glass(seed, inner=False):
    t = F.plain(seed, (40, 52, 62), 0.02, 0.95)
    t.alpha[:] = 60 if inner else 120
    t.c[: N // 3] = (84, 100, 116)
    if not inner:
        t.speckle(0.02, (200, 200, 190), 0.2)
    return t


def radome(seed):
    t = paint(seed, (54, 56, 60), seams=False, gloss=0.5)
    t.speckle(0.04, (120, 120, 116), 0.4)
    return t


def carpet(seed):
    """Cabin carpet: blue-grey with a small diamond figure, trodden dark down the aisle."""
    t = F.plain(seed, (58, 70, 96), 0.05, 0.1)
    for y in range(0, N, 8):
        for x in range((y // 8 % 2) * 4, N, 8):
            area(t, x, y, x + 2, y + 2, (96, 104, 130))
    t.speckle(0.15, (40, 48, 66), 0.4)
    t.grime(0.3)
    return t


def bin_door(seed, open_=False):
    """An overhead bin seen from the aisle: the door's curve shaded, the latch, the row's number; or
    open, the bin's dark inside with a bag in it."""
    t = lining(seed, (222, 220, 212))
    for y in range(N):
        t.c[y] *= 0.86 + 0.18 * math.sin(math.pi * y / N)
    area(t, 0, 0, N, 2, mul=0.7)
    area(t, 0, N - 2, N, N, mul=0.6)
    area(t, 0, 0, 1, N, mul=0.7)
    if open_:
        area(t, 3, 4, N - 3, N - 4, (26, 26, 28))
        area(t, 10, 22, 34, N - 4, (40, 46, 70))
        area(t, 38, 30, 58, N - 4, (110, 30, 30))
    else:
        area(t, 24, N - 14, 40, N - 8, (150, 150, 150), dh=-0.1)
        area(t, 26, N - 12, 38, N - 10, (60, 60, 60))
    return t


def psu(seed):
    """The passenger service unit under the bins: reading lights, the air vents, the attendant
    call button, the seat-belt sign; and the mask door hanging open where the masks dropped."""
    t = lining(seed, (206, 204, 196))
    for cx in (12, 28, 44):
        tdisc(t, cx, 20, 4, (230, 226, 200), dh=0.1)
        tdisc(t, cx, 20, 2, (250, 244, 210))
    for cx in (12, 28, 44):
        tdisc(t, cx, 34, 3.5, (120, 120, 120), dh=0.15)
        tdisc(t, cx, 34, 1.5, (40, 40, 40))
    area(t, 52, 16, 60, 24, (180, 180, 180))
    tdisc(t, 56, 20, 2.5, (220, 120, 40))
    area(t, 4, 44, 60, 60, (30, 30, 30))
    area(t, 6, 46, 58, 58, (60, 60, 64))
    return t


def ceiling(seed, lit=True):
    t = lining(seed, (226, 224, 218))
    for x in (0, 31):
        area(t, x, 0, x + 1, N, mul=0.8)
    if lit:
        area(t, 0, 0, N, 5, (238, 236, 226))
        area(t, 0, N - 5, N, N, (238, 236, 226))
        area(t, 26, 28, 38, 36, (200, 60, 50))
    return t


def cockpit_lining(seed):
    t = F.plain(seed, (110, 114, 118), 0.04, 0.3)
    t.speckle(0.05, (80, 84, 88), 0.4)
    t.grime(0.15)
    return t


def metal(seed):
    t = F.plain(seed, (176, 180, 184), 0.04, 0.7)
    t.f0[:] = 230
    t.grime(0.1)
    return t


def wing_top(seed, walk=False):
    t = paint(seed, (196, 200, 204), gloss=0.5)
    if walk:
        t.c[:] = (70, 72, 76)
        area(t, 0, 0, 3, N, (20, 20, 20))
        area(t, N - 3, 0, N, N, (20, 20, 20))
        t.speckle(0.2, (40, 40, 42), 0.5)
    return t


def flap(seed):
    t = wing_top(seed)
    area(t, 0, 31, N, 33, (80, 84, 88), dh=-0.2)
    return t


def nav(seed, rgb):
    t = F.plain(seed, rgb, 0.03, 0.9)
    t.c[N // 3:] *= 0.8
    return t


def exhaust(seed):
    t = F.plain(seed, (44, 40, 36), 0.08, 0.3)
    t.f0[:] = 120
    t.grime(0.5, (20, 18, 16))
    return t


def tyre(seed):
    t = F.plain(seed, (30, 30, 30), 0.04, 0.2)
    for x in range(4, N, 10):
        area(t, x, 0, x + 3, N, mul=0.7, dh=-0.2)
    t.grime(0.2, (80, 76, 70))
    return t


def hub(seed):
    t = tyre(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - 32, yy - 32)
    m = d < 22
    t.c[m] = (150, 152, 154)
    t.f0[m] = 220
    for a in range(10):
        ang = a * math.pi / 5
        tdisc(t, 32 + 15 * math.cos(ang), 32 + 15 * math.sin(ang), 1.6, (60, 60, 60))
    tdisc(t, 32, 32, 6, (90, 92, 94))
    return t


def strut(seed, chrome=False):
    t = F.plain(seed, (190, 194, 198) if chrome else (120, 124, 128), 0.03, 0.85 if chrome else 0.4)
    t.f0[:] = 240 if chrome else 60
    t.grime(0.15 if chrome else 0.3)
    return t


def mask(seed):
    t = F.plain(seed, (232, 190, 40), 0.04, 0.4)
    return t


@lru_cache(maxsize=None)
def door_pic():
    """A passenger door 2 blocks wide and 3 tall over the livery's bands: the seam round it, the
    small window, the handle in its recess, the arming placard."""
    p = Pic(2, 3, WHITE + (255,), 31400)

    def band(y0, y1, col):
        p.rect(0, (10 - y1) * N, 2 * N, (10 - y0) * N, fill=col)
    band(*CHEAT, NAVY)
    band(*PIN, SILVER)
    p.rect(5, 6, 2 * N - 5, 3 * N - 4, outline=(90, 94, 100), width=1.2)
    p.ellipse(N - 14, (10 - WIN[1]) * N + 2, N + 14, (10 - WIN[0]) * N - 2, fill=(60, 74, 86), outline=(96, 100, 106), width=2)
    p.rect(N - 16, 1.6 * N, N + 16, 1.6 * N + 12, fill=(60, 60, 64), outline=(30, 30, 30))
    p.rect(N - 12, 1.6 * N + 3, N + 12, 1.6 * N + 9, fill=(180, 180, 176))
    p.text(N, 2.25 * N, "PUSH|TO OPEN", BOLD, 5, (160, 40, 30), anchor="ma")
    return p


@lru_cache(maxsize=None)
def exit_pic():
    p = Pic(1, 2, WHITE + (255,), 31410)

    def band(y0, y1, col):
        p.rect(0, (9 - y1) * N, N, (9 - y0) * N, fill=col)
    band(*CHEAT, NAVY)
    band(*PIN, SILVER)
    p.rect(6, 22, N - 6, 2 * N - 18, outline=(90, 94, 100), width=1.2)
    p.ellipse(N / 2 - 9, (9 - WIN[1]) * N + 2, N / 2 + 9, (9 - WIN[0]) * N - 2, fill=(60, 74, 86), outline=(96, 100, 106), width=2)
    p.rect(N / 2 - 12, 10, N / 2 + 12, 19, fill=(200, 40, 30))
    p.text(N / 2, 10.5, "EXIT", BOLD, 7, (250, 250, 250), anchor="ma")
    return p


@lru_cache(maxsize=None)
def title_pic():
    n = TITLE_Z[1] - TITLE_Z[0]
    p = Pic(n, 1, (0, 0, 0, 0), 31420)
    size = civic.fit(TITLE, BOLD, 60, n * N - 40)
    p.text(n * N / 2, 6, TITLE, BOLD, size, NAVY, anchor="ma")
    return p


@lru_cache(maxsize=None)
def reg_pic():
    n = REG_Z[1] - REG_Z[0]
    p = Pic(n, 1, (0, 0, 0, 0), 31430)
    p.text(n * N / 2, 10, REG, BOLD, 40, (50, 54, 60), anchor="ma")
    return p


def on_paint(pic, i, seed):
    """A tile of lettering over the white paint."""
    t = paint(seed)
    a = pic.shrunk()
    cols = pic.cols
    cx, cy = (i % cols) * N, (i // cols) * N
    sub = a[cy:cy + N, cx:cx + N]
    w = sub[..., 3:4] / 255
    t.c = t.c * (1 - w) + sub[..., :3] * w
    return t


@lru_cache(maxsize=None)
def fin_pic():
    """The tail: the silver eagle's head looking forward (to the picture's left), a silver flash
    sweeping up behind it."""
    z0, ytop, w, h = FIN_PIC
    p = Pic(w, h, (0, 0, 0, 0), 31440)

    def at(z, y):
        return (z - z0) * N, (ytop - y) * N
    # the flash up the fin's rear
    p.poly([at(50.0, 11.2), at(54.0, 11.2), at(55.5, 21.5), at(52.8, 21.5)], fill=SILVER + (255,))
    # the head: a crown sweeping back, the hooked beak forward, the eye
    def at(z, y, at=at):
        return at(z + 1.3, y)
    head = [at(46.2, 15.2), at(47.0, 17.4), at(48.6, 18.8), at(50.6, 19.3), at(52.4, 18.8), at(51.4, 18.0),
            at(52.8, 17.2), at(51.2, 16.6), at(52.2, 15.6), at(50.0, 15.6), at(49.2, 14.6), at(47.6, 14.4)]
    p.poly(head, fill=(236, 236, 232, 255))
    beak = [at(46.2, 15.2), at(45.0, 15.6), at(44.6, 14.8), at(45.2, 14.0), at(46.2, 14.3), at(47.6, 14.4)]
    p.poly(beak, fill=(220, 176, 50, 255))
    ex, ey = at(47.6, 16.4)
    p.ellipse(ex - 7, ey - 5, ex + 7, ey + 5, fill=(20, 24, 40, 255))
    return p


@lru_cache(maxsize=None)
def fin_tiles():
    z0, ytop, w, h = FIN_PIC
    a = fin_pic().shrunk()
    out = set()
    for j in range(h):
        for i in range(w):
            if a[j * N:(j + 1) * N, i * N:(i + 1) * N, 3].max() > 8:
                out.add((i, j))
    return frozenset(out)


def fin_tile(k):
    z0, ytop, w, h = FIN_PIC
    t = paint(31450 + k, NAVY, seams=False)
    a = fin_pic().shrunk()
    i, j = k % w, k // w
    sub = a[j * N:(j + 1) * N, i * N:(i + 1) * N]
    wgt = sub[..., 3:4] / 255
    t.c = t.c * (1 - wgt) + sub[..., :3] * wgt
    return t


@lru_cache(maxsize=None)
def fan_pic():
    p = Pic(4, 4, (0, 0, 0, 0), 31460)
    cx, cy = 2 * N, (4 - 2.25) * N
    r = 1.45 * N
    p.ellipse(cx - r, cy - r, cx + r, cy + r, fill=(40, 42, 46, 255))
    for k in range(22):
        a0 = k * 2 * math.pi / 22
        pts = [(cx + 0.3 * N * math.cos(a0), cy + 0.3 * N * math.sin(a0))]
        for s in range(6):
            rr = 0.3 * N + (r - 0.3 * N) * (s + 1) / 6
            a = a0 + 0.35 * (s + 1) / 6
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        for s in range(6, -1, -1):
            rr = 0.3 * N + (r - 0.3 * N) * s / 6
            a = a0 + 0.12 + 0.35 * s / 6
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        p.poly(pts, fill=(150, 154, 160, 255))
    return p


def fan_tile_tex(k):
    t = F.plain(31470 + k, (40, 42, 46), 0.02, 0.6)
    t.f0[:] = 200
    a = fan_pic().shrunk()
    i, j = k % 4, k // 4
    sub = a[j * N:(j + 1) * N, i * N:(i + 1) * N]
    t.c[:] = sub[..., :3]
    return t


def textures():
    out = {
        "al_paint": lambda: paint(31001),
        "al_belly": lambda: paint(31002, BELLY),
        "al_navy": lambda: paint(31003, NAVY, seams=False),
        "al_rudder": lambda: paint(31004, tuple(v * 0.92 for v in NAVY)),
        "al_silver": lambda: paint(31005, SILVER, seams=False),
        "al_lining": lambda: lining(31006),
        "al_dark": lambda: F.plain(31007, (30, 30, 32), 0.02, 0.2),
        "al_ck_lining": lambda: cockpit_lining(31008),
        "al_ck_glass": lambda: glass(31009),
        "al_ck_glass_in": lambda: glass(31010, True),
        "al_radome": lambda: paint(31011, (196, 198, 200), seams=False),
        "al_win": lambda: win(31012),
        "al_win_in": lambda: win(31013, True),
        "al_cowl": lambda: paint(31014, (206, 208, 210)),
        "al_intake": lambda: F.plain(31015, (60, 62, 66), 0.03, 0.5),
        "al_carpet": lambda: carpet(31016),
        "al_bin": lambda: bin_door(31017),
        "al_bin_open": lambda: bin_door(31018, True),
        "al_bin_top": lambda: lining(31019, (200, 198, 190)),
        "al_bin_door_in": lambda: lining(31020, (190, 188, 180)),
        "al_psu": lambda: psu(31021),
        "al_tube": lambda: F.plain(31022, (230, 230, 226), 0.02, 0.5),
        "al_mask": lambda: mask(31023),
        "al_ceiling": lambda: ceiling(31024),
        "al_ceiling_plain": lambda: ceiling(31025, False),
        "al_cove": lambda: F.plain(31026, (244, 238, 220), 0.02, 0.5),
        "al_metal": lambda: metal(31027),
        "al_wing": lambda: wing_top(31028),
        "al_walkway": lambda: wing_top(31029, True),
        "al_flap": lambda: flap(31030),
        "al_wing_under": lambda: paint(31031, (176, 180, 184)),
        "al_tailplane": lambda: paint(31032, (200, 204, 206)),
        "al_nav_red": lambda: nav(31033, (200, 30, 30)),
        "al_nav_green": lambda: nav(31034, (30, 170, 60)),
        "al_nav_white": lambda: nav(31035, (240, 240, 240)),
        "al_spinner": lambda: metal(31036),
        "al_exhaust_plug": lambda: exhaust(31037),
        "al_exhaust": lambda: exhaust(31038),
        "al_strut": lambda: strut(31039),
        "al_chrome": lambda: strut(31040, True),
        "al_tyre": lambda: tyre(31041),
        "al_hub": lambda: hub(31042),
    }
    for c in range(2):
        for r in range(3):
            out[f"al_door_{c}_{r}"] = lambda c=c, r=r: on_paint(door_pic(), r * 2 + c, 31100 + r * 2 + c)
    for r in range(2):
        out[f"al_exit_{r}"] = lambda r=r: on_paint(exit_pic(), r, 31110 + r)
    for k in range(TITLE_Z[1] - TITLE_Z[0]):
        out[f"al_title_{k}"] = lambda k=k: on_paint(title_pic(), k, 31120 + k)
    for k in range(REG_Z[1] - REG_Z[0]):
        out[f"al_reg_{k}"] = lambda k=k: on_paint(reg_pic(), k, 31140 + k)
    z0, ytop, w, h = FIN_PIC
    for i, j in fin_tiles():
        out[f"al_fin_{j * w + i}"] = lambda k=j * w + i: fin_tile(k)
    for k in range(16):
        out[f"al_fan_{k}"] = lambda k=k: fan_tile_tex(k)
    out.update(prop_textures())
    return out


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the fittings (named blocks)

PROPS = {}


def prop(name, title, sound="METAL", render="cutout", shape=None):
    def deco(f):
        PROPS[name] = (f, title, sound, render, shape)
        return f
    return deco


def bx(x0, y0, z0, x1, y1, z1, tex, rot=None):
    b = [x0, y0, z0, x1, y1, z1, tex]
    if rot:
        b += [None, rot]
    return b


SEAT_FAB = {"up": "al_seat_fab", "north": "al_seat_fab", "side": "al_seat_fab", "down": "al_armrest", "south": "al_seat_fab"}


def seat_boxes(kind="plain", biz=False):
    fab = "al_leather" if biz else "al_seat_fab"
    f = {"up": fab, "side": fab, "down": "al_armrest"}
    b = [bx(2, 0, 4, 3, 5, 12, "al_armrest"), bx(13, 0, 4, 14, 5, 12, "al_armrest"),
         bx(1.5, 0, 3.5, 3.5, 0.5, 12.5, "al_armrest"), bx(12.5, 0, 3.5, 14.5, 0.5, 12.5, "al_armrest"),
         bx(1, 4.5, 2, 15, 5.2, 12.5, "al_armrest")]
    if kind == "torn":
        b += [bx(1, 5.2, 2, 15, 6.4, 12, {"up": "al_seat_torn", "side": fab, "down": fab})]
    else:
        b += [bx(1, 5.2, 2, 15, 8, 12, f)]
    back = [bx(1, 7, 12, 15, 26, 14.6, {"north": fab, "south": "al_seat_back", "side": fab, "up": fab, "down": fab}),
            bx(2.5, 21, 11.6, 13.5, 25.6, 12, {"north": "al_antimac", "side": "al_antimac", "up": "al_antimac", "down": "al_antimac", "south": "al_antimac"})]
    if kind == "recline":
        back = [q + [None, {"axis": "x", "angle": -15, "origin": [8, 7, 14]}] for q in back]
    b += back
    arm = 1.4 if biz else 1.0
    b += [bx(0, 9, 3, arm, 10.5, 13, "al_armrest"), bx(16 - arm, 9, 3, 16, 10.5, 13, "al_armrest"),
          bx(0.2, 5, 11, arm - 0.2, 9, 12, "al_armrest"), bx(16 - arm + 0.2, 5, 11, 15.8, 9, 12, "al_armrest")]
    if kind == "bag":
        b += [bx(3.5, 8, 4, 12.5, 12.5, 10, {"side": "ap_bag_black", "up": "ap_bag_black", "down": "ap_bag_black"}),
              bx(6.5, 12.5, 6.5, 9.5, 13.2, 7.5, "home_plastic_black")]
    if kind == "belt":
        b += [bx(1.5, 8, 6, 6, 8.3, 7, "al_belt"), bx(10, 8, 6, 14.5, 8.3, 7, "al_belt"), bx(6, 8, 5.6, 7.5, 8.5, 7.4, "home_chrome")]
    return b


for _k in ("plain", "bag", "torn", "recline", "belt"):
    PROPS["al_seat" + ("" if _k == "plain" else "_" + _k)] = (lambda k=_k: seat_boxes(k), "Airline Seat", "WOOL", "cutout",
                                                               [[0, 0, 2, 16, 16, 14.6]])
    PROPS["al_seat_biz" + ("" if _k == "plain" else "_" + _k)] = (lambda k=_k: seat_boxes(k, True), "First Class Seat",
                                                                   "WOOL", "cutout", [[0, 0, 2, 16, 16, 14.6]])


@prop("al_galley_lo", "Galley Carts", shape=[[0, 0, 3, 16, 16, 16]])
def galley_lo():
    return [bx(0, 0, 3, 16, 16, 16, {"north": "al_galley_carts", "side": "al_galley_steel", "up": "al_galley_steel", "down": "al_galley_steel", "south": "al_galley_steel"}),
            bx(1, 2, 2.4, 15, 2.8, 3, "al_galley_steel")]


@prop("al_galley_mid", "Galley", shape=[[0, 0, 3, 16, 16, 16]])
def galley_mid():
    return [bx(0, 0, 2, 16, 1.5, 16, "al_galley_steel"),
            bx(0, 1.5, 9, 16, 16, 16, {"north": "al_galley_ovens", "side": "al_galley_steel", "up": "al_galley_steel", "down": "al_galley_steel", "south": "al_galley_steel"}),
            bx(2, 1.5, 6, 6, 6, 9, "al_coffee"), bx(9, 1.5, 6, 13, 6, 9, "al_coffee"),
            bx(3, 1.5, 4, 5, 3.5, 6, "home_white")]


@prop("al_galley_hi", "Galley Lockers", shape=[[0, 0, 3, 16, 16, 16]])
def galley_hi():
    return [bx(0, 0, 3, 16, 16, 16, {"north": "al_galley_lockers", "side": "al_galley_steel", "up": "al_galley_steel", "down": "al_galley_steel", "south": "al_galley_steel"})]


@prop("al_cart_out", "Galley Cart", shape=[[3, 0, 1, 13, 15, 15]])
def cart_out():
    return [bx(3, 1, 1, 13, 15, 15, {"north": "al_cart_front", "south": "al_cart_front", "side": "al_galley_steel", "up": "al_galley_steel", "down": "al_galley_steel"}),
            bx(3.5, 0, 2, 5, 1, 3.5, "home_rubber"), bx(11, 0, 2, 12.5, 1, 3.5, "home_rubber"),
            bx(3.5, 0, 12.5, 5, 1, 14, "home_rubber"), bx(11, 0, 12.5, 12.5, 1, 14, "home_rubber"),
            bx(4, 15, 3, 12, 16.5, 6, "al_coffee")]


@prop("al_cart_tipped", "Galley Cart", shape=[[1, 0, 3, 15, 10, 13]])
def cart_tipped():
    return [bx(1, 0, 3, 15, 10, 13, {"up": "al_cart_front", "down": "al_cart_front", "side": "al_galley_steel"}),
            bx(4, 0, 13, 9, 1.2, 16, "home_white"), bx(9, 0, 0, 12, 0.8, 3, "home_white")]


LAV = "al_lav_wall"


def lav_walls(walls, h=32):
    out = []
    for w in walls:
        if w == "north":
            out.append(bx(0, 0, 0, 16, h, 1, LAV))
        elif w == "south":
            out.append(bx(0, 0, 15, 16, h, 16, LAV))
        elif w == "west":
            out.append(bx(0, 0, 0, 1, h, 16, LAV))
        elif w == "east":
            out.append(bx(15, 0, 0, 16, h, 16, LAV))
    return out


@prop("al_lav_fl", "Lavatory", shape=[[0, 0, 0, 16, 16, 1], [0, 0, 0, 1, 16, 16]])
def lav_fl():
    return lav_walls(("north", "west")) + [bx(1, 0, 1, 15, 0.3, 16, "al_lav_floor")]


@prop("al_lav_fr", "Lavatory", shape=[[15, 0, 0, 16, 16, 16]])
def lav_fr():
    # the folding door pushed open against the east wall, its sign over the doorway
    return lav_walls(("east",)) + [bx(0, 0, 0, 1, 32, 1, LAV), bx(0, 28, 0, 15, 32, 1, LAV),
                                   bx(11, 0, 1, 14.5, 27.5, 2, {"north": "al_lav_door", "south": LAV, "side": LAV, "up": LAV, "down": LAV}),
                                   bx(4, 28.3, -0.4, 12, 31.5, 0, "al_lav_sign"), bx(1, 0, 1, 15, 0.3, 16, "al_lav_floor")]


@prop("al_lav_bl", "Lavatory", shape=[[0, 0, 0, 16, 16, 16]])
def lav_bl():
    return lav_walls(("south", "west")) + [bx(1, 0, 0, 15, 0.3, 15, "al_lav_floor"),
                                           bx(3, 0, 8, 13, 7, 15, "al_toilet_shroud"),
                                           bx(4, 7, 6, 12, 7.8, 14, {"up": "al_toilet", "side": "al_toilet_shroud", "down": "al_toilet_shroud"}),
                                           bx(4, 7.8, 13.6, 12, 16, 14.6, "al_toilet_shroud"),
                                           bx(1, 18, 10, 6, 24, 15, "al_galley_steel")]


@prop("al_lav_br", "Lavatory", shape=[[0, 0, 0, 16, 16, 16]])
def lav_br():
    return lav_walls(("south", "east")) + [bx(1, 0, 0, 15, 0.3, 15, "al_lav_floor"),
                                           bx(2, 0, 7, 15, 14, 15, {"up": "al_lav_counter", "side": LAV, "down": LAV}),
                                           bx(6, 14, 11, 11, 15, 14, "al_galley_steel"),
                                           bx(2, 16, 14.6, 15, 30, 15, "al_mirror")]


for _n, _w in (("fl", ("north", "west")), ("fr", ("north", "east")), ("bl", ("south", "west")), ("br", ("south", "east"))):
    PROPS[f"al_lav_{_n}_up"] = (lambda w=_w: lav_walls(w), "Lavatory", "METAL", "cutout",
                                [[0, 0, 0, 16, 16, 1] if "north" in _w else [0, 0, 15, 16, 16, 16]])


@prop("al_partition", "Cabin Divider", shape=[[0, 0, 7, 16, 16, 9]])
def partition():
    return [bx(0, 0, 7, 16, 32, 9, {"north": "al_lining", "south": "al_partition", "side": "al_lining", "up": "al_lining", "down": "al_lining"})]


@prop("al_curtain", "Curtain", sound="WOOL", shape=[])
def curtain():
    return [bx(0, 30, 6, 16, 31, 10, "al_galley_steel"),
            bx(0.5, -12, 7, 5, 30, 9, "al_curtain"), bx(5, -10, 7.4, 9, 30, 8.6, "al_curtain")]


@prop("al_door_leaf", "Aircraft Door", shape=[[0, 0, 6, 16, 16, 10]])
def door_leaf():
    return [bx(0, 0, 6, 16, 48, 10, {"north": "al_door_out", "south": "al_door_in", "side": "al_paint", "up": "al_paint", "down": "al_paint"})]


# the cockpit
@prop("al_ck_seat", "Pilot Seat", sound="WOOL", shape=[[1, 0, 2, 15, 16, 15]])
def ck_seat():
    f = {"up": "al_ck_seat", "side": "al_ck_seat", "down": "al_armrest"}
    return [bx(5, 0, 4, 11, 1, 12, "al_strut"), bx(6.5, 1, 6.5, 9.5, 6, 9.5, "al_chrome"),
            bx(1.5, 6, 2, 14.5, 9, 13, f), bx(1.5, 8, 12.5, 14.5, 30, 15, f),
            bx(3, 26, 12, 13, 30, 12.6, "al_ck_seat"),
            bx(0, 12, 3, 1.6, 13.5, 12, "al_armrest"), bx(14.4, 12, 3, 16, 13.5, 12, "al_armrest"),
            bx(2, 9.2, 4, 3, 26, 5, "al_belt"), bx(13, 9.2, 4, 14, 26, 5, "al_belt")]


@prop("al_ck_yoke", "Control Column", shape=[[5, 0, 4, 11, 16, 12]])
def ck_yoke():
    return [bx(5, 0, 6, 11, 2, 12, "al_ck_lower"),
            bx(7, 2, 8, 9, 20, 10, "al_strut", {"axis": "x", "angle": -10, "origin": [8, 2, 9]}),
            bx(2.5, 19, 11, 13.5, 20.5, 12, "al_armrest"), bx(2.5, 19, 11, 4, 24, 12, "al_armrest"),
            bx(12, 19, 11, 13.5, 24, 12, "al_armrest"), bx(6.5, 20.5, 11.2, 9.5, 22.5, 12, "al_ck_clip")]


def panel_pic_tile(k):
    return f"al_ck_panel_{k}"


for _k in range(4):
    PROPS[f"al_ck_panel_{_k}"] = (lambda k=_k: [bx(0, 0, 4, 16, 16, 12, {"south": f"al_ck_panel_{k}", "side": "al_ck_lining", "up": "al_ck_lining", "down": "al_ck_lining", "north": "al_ck_lining"}),
                                               bx(0, 16, 2, 16, 22, 10, {"up": "al_ck_glare", "south": "al_ck_mcp" if k in (1, 2) else "al_ck_glare", "side": "al_ck_glare", "down": "al_ck_glare", "north": "al_ck_glare"})],
                                  "Instrument Panel", "METAL", "cutout", [[0, 0, 2, 16, 16, 12]])
    PROPS[f"al_ck_knee_{_k}"] = (lambda k=_k: [bx(0, 0, 4, 16, 16, 9, {"south": "al_ck_knee", "side": "al_ck_lining", "up": "al_ck_lining", "down": "al_ck_lining", "north": "al_ck_lining"})]
                                 + ([bx(3, 0, 9, 6, 4, 13, "al_ck_lower", {"axis": "x", "angle": 30, "origin": [4.5, 0, 9]}),
                                     bx(10, 0, 9, 13, 4, 13, "al_ck_lower", {"axis": "x", "angle": 30, "origin": [11.5, 0, 9]})] if k in (0, 3) else []),
                                 "Instrument Panel", "METAL", "cutout", [[0, 0, 4, 16, 16, 9]])


@prop("al_ck_ped", "Throttle Quadrant", shape=[[1, 0, 0, 15, 14, 16]])
def ck_ped():
    return [bx(1, 0, 0, 15, 12, 16, {"up": "al_ck_ped_top", "side": "al_ck_lining", "down": "al_ck_lining", "north": "al_ck_lining", "south": "al_ck_lining"}),
            bx(4, 12, 4, 5.5, 18, 5.5, "al_chrome", {"axis": "x", "angle": 20, "origin": [4.75, 12, 4.75]}),
            bx(10.5, 12, 4, 12, 18, 5.5, "al_chrome", {"axis": "x", "angle": 20, "origin": [11.25, 12, 4.75]}),
            bx(3.5, 17.6, 5, 6, 19, 7, "al_armrest"), bx(10, 17.6, 5, 12.5, 19, 7, "al_armrest"),
            bx(7, 12, 9, 9, 15, 11, "al_armrest")]


@prop("al_ck_ped_aft", "Radio Panel", shape=[[1, 0, 0, 15, 12, 16]])
def ck_ped_aft():
    return [bx(1, 0, 0, 15, 12, 16, {"up": "al_ck_radios", "side": "al_ck_lining", "down": "al_ck_lining", "north": "al_ck_lining", "south": "al_ck_lining"})]


@prop("al_ck_over", "Overhead Panel", shape=[])
def ck_over():
    return [bx(0, 10, 0, 16, 16, 16, {"down": "al_ck_overhead", "side": "al_ck_lining", "up": "al_ck_lining"})]


@prop("al_ck_side", "Side Console", shape=[[0, 0, 0, 16, 12, 16]])
def ck_side():
    return [bx(0, 0, 0, 16, 11, 16, {"up": "al_ck_side", "side": "al_ck_lining", "down": "al_ck_lining"}),
            bx(5, 11, 8, 9, 13, 12, "al_armrest"), bx(10, 11, 2, 15, 16, 7, "al_ck_mask_box")]


def prop_textures():
    def fab(seed, rgb, patt):
        t = F.plain(seed, rgb, 0.06, 0.15)
        for y in range(0, N, 6):
            for x in range((y // 6 % 2) * 3, N, 6):
                area(t, x, y, x + 1, y + 1, patt)
        t.grime(0.2)
        return t

    def seat_back(seed):
        t = F.plain(seed, (70, 74, 80), 0.03, 0.4)
        area(t, 6, 6, N - 6, 30, (80, 84, 90), dh=0.05)
        area(t, 26, 8, 38, 11, (40, 40, 40))
        area(t, 6, 40, N - 6, N - 6, (40, 48, 70))
        area(t, 6, 40, N - 6, 43, (24, 28, 40))
        area(t, 14, 44, 30, 58, (220, 214, 200))
        t.grime(0.2)
        return t

    def antimac(seed):
        t = F.plain(seed, (234, 234, 228), 0.03, 0.3)
        area(t, 0, 50, N, 56, NAVY)
        t.grime(0.15)
        return t

    def steel(seed):
        t = F.plain(seed, (170, 174, 176), 0.03, 0.75)
        t.f0[:] = 230
        for y in range(0, N, 16):
            area(t, 0, y, N, y + 1, mul=0.7)
        t.grime(0.15)
        return t

    def carts(seed):
        t = steel(seed)
        for x0 in (2, 34):
            area(t, x0, 4, x0 + 28, N - 2, (150, 152, 156))
            area(t, x0 + 2, 6, x0 + 26, 14, NAVY)
            area(t, x0 + 10, 20, x0 + 18, 24, (60, 60, 60))
            area(t, x0, N - 4, x0 + 28, N - 2, (40, 40, 40))
        return t

    def ovens(seed):
        t = steel(seed)
        for x0 in (2, 34):
            area(t, x0, 6, x0 + 28, 34, (120, 124, 128))
            area(t, x0 + 4, 10, x0 + 24, 26, (40, 40, 44))
            area(t, x0 + 8, 28, x0 + 20, 31, (200, 200, 200))
        area(t, 0, 40, N, 42, (90, 90, 90))
        return t

    def lockers(seed):
        t = steel(seed)
        for x0 in (2, 22, 42):
            area(t, x0, 4, x0 + 18, N - 4, (156, 160, 164))
            area(t, x0 + 7, 28, x0 + 11, 36, (210, 120, 30))
        return t

    def cart_front(seed):
        t = steel(seed)
        area(t, 4, 4, N - 4, N - 4, (150, 152, 156))
        area(t, 8, 8, N - 8, 20, NAVY)
        area(t, 26, 30, 38, 36, (60, 60, 60))
        return t

    def lav_door(seed):
        t = lining(seed, (196, 192, 180))
        for y in (20, 40):
            area(t, 0, y, N, y + 1, mul=0.7)
        return t

    def lav_sign(seed):
        p = Pic(1, 1, (30, 30, 30, 255), seed)
        p.rect(4, 20, N - 4, N - 20, fill=(40, 160, 70))
        p.text(N / 2, 25, "VACANT", BOLD, 9, (240, 240, 240), anchor="ma")
        return p.tile(0, seed, smooth=0.5, grime=0.1)

    def panel_tile(k):
        p = panel_pic()
        return p.tile(k, 31600 + k, smooth=0.6, grime=0.15)

    def mcp(seed):
        t = F.plain(seed, (90, 96, 104), 0.03, 0.4)
        for x in range(4, N - 4, 10):
            area(t, x, 20, x + 7, 30, (20, 20, 20))
            area(t, x + 1, 22, x + 6, 26, (220, 150, 40))
            tdisc(t, x + 3.5, 42, 3, (40, 40, 40))
        return t

    def knee(seed):
        t = F.plain(seed, (80, 86, 92), 0.03, 0.4)
        for x in range(6, N - 6, 12):
            tdisc(t, x, 24, 3, (40, 40, 40))
            area(t, x - 3, 40, x + 3, 44, (200, 200, 200))
        return t

    def switches(seed, rows=6):
        t = F.plain(seed, (84, 90, 98), 0.03, 0.4)
        for y in range(6, N - 4, N // rows):
            area(t, 0, y - 3, N, y - 2, (60, 64, 70))
            for x in range(5, N - 3, 7):
                area(t, x, y, x + 2, y + 4, (220, 220, 220))
                if (x + y) % 3 == 0:
                    area(t, x - 1, y + 5, x + 3, y + 7, (240, 180, 40))
        return t

    def ped_top(seed):
        t = switches(seed, 4)
        area(t, 10, 10, N - 10, N - 10, (40, 40, 44))
        for x in (16, 44):
            area(t, x, 14, x + 4, N - 14, (20, 20, 20))
        return t

    def radios(seed):
        t = F.plain(seed, (60, 64, 70), 0.03, 0.4)
        for y in range(4, N - 8, 12):
            area(t, 4, y, N - 4, y + 9, (40, 42, 46))
            area(t, 10, y + 2, 26, y + 7, (30, 10, 8))
            area(t, 38, y + 2, 54, y + 7, (30, 10, 8))
            tdisc(t, 32, y + 4.5, 2.5, (150, 150, 150))
        return t

    def ck_seat_tex(seed):
        t = F.plain(seed, (200, 196, 184), 0.1, 0.05)
        t.speckle(0.4, (170, 164, 150), 0.5, 0.1)
        return t

    def door_in(seed):
        t = lining(seed, (196, 196, 190))
        area(t, 8, 30, 56, 56, (150, 150, 146))
        area(t, 26, 18, 38, 24, (200, 40, 30))
        return t

    def partition_tex(seed):
        t = lining(seed)
        area(t, 10, 34, N - 10, 52, (60, 66, 84))
        area(t, 10, 34, N - 10, 36, (40, 44, 60))
        return t

    return {
        "al_seat_fab": lambda: fab(31500, (60, 72, 104), (88, 100, 130)),
        "al_seat_torn": lambda: fab(31501, (210, 200, 170), (180, 170, 140)),
        "al_leather": lambda: fab(31502, (104, 66, 44), (96, 60, 40)),
        "al_seat_back": lambda: seat_back(31503),
        "al_antimac": lambda: antimac(31504),
        "al_armrest": lambda: F.plain(31505, (58, 60, 64), 0.03, 0.4),
        "al_belt": lambda: F.plain(31506, (40, 44, 60), 0.05, 0.2),
        "al_galley_steel": lambda: steel(31507),
        "al_galley_carts": lambda: carts(31508),
        "al_galley_ovens": lambda: ovens(31509),
        "al_galley_lockers": lambda: lockers(31510),
        "al_coffee": lambda: F.plain(31511, (30, 30, 32), 0.03, 0.6),
        "al_cart_front": lambda: cart_front(31512),
        "al_lav_wall": lambda: lining(31513, (220, 214, 196)),
        "al_lav_floor": lambda: F.plain(31514, (90, 92, 96), 0.05, 0.3),
        "al_lav_door": lambda: lav_door(31515),
        "al_lav_sign": lambda: lav_sign(31516),
        "al_toilet": lambda: metal(31517),
        "al_toilet_shroud": lambda: lining(31518, (230, 228, 220)),
        "al_lav_counter": lambda: lining(31519, (180, 170, 150)),
        "al_mirror": lambda: glass(31520),
        "al_curtain": lambda: fab(31521, NAVY, (44, 58, 100)),
        "al_partition": lambda: partition_tex(31522),
        "al_door_out": lambda: paint(31523),
        "al_door_in": lambda: door_in(31524),
        "al_ck_seat": lambda: ck_seat_tex(31525),
        "al_ck_lower": lambda: F.plain(31526, (60, 64, 70), 0.03, 0.4),
        "al_ck_clip": lambda: F.plain(31527, (220, 216, 200), 0.03, 0.3),
        "al_ck_glare": lambda: F.plain(31528, (36, 38, 42), 0.03, 0.2),
        "al_ck_mcp": lambda: mcp(31529),
        "al_ck_knee": lambda: knee(31530),
        "al_ck_ped_top": lambda: ped_top(31531),
        "al_ck_radios": lambda: radios(31532),
        "al_ck_overhead": lambda: switches(31533),
        "al_ck_side": lambda: switches(31534, 3),
        "al_ck_mask_box": lambda: F.plain(31535, (200, 190, 60), 0.03, 0.3),
        **{f"al_ck_panel_{k}": (lambda k=k: panel_tile(k)) for k in range(4)},
    }


@lru_cache(maxsize=None)
def panel_pic():
    """The main panel across four blocks: the captain's and first officer's attitude and map
    screens, the engine instruments in the middle, the round standby gauges."""
    p = Pic(4, 1, (84, 90, 98, 255), 31590)
    W = p.W

    def screen(x, y, w, h, kind):
        p.rect(x - 2, y - 2, x + w + 2, y + h + 2, fill=(30, 30, 32))
        p.rect(x, y, x + w, y + h, fill=(16, 20, 26))
        if kind == "pfd":
            p.rect(x, y, x + w, y + h / 2, fill=(40, 90, 150))
            p.rect(x, y + h / 2, x + w, y + h, fill=(110, 76, 40))
            p.line([(x + w * 0.3, y + h / 2), (x + w * 0.7, y + h / 2)], (240, 240, 60), 1.5)
        elif kind == "nd":
            p.ellipse(x + 6, y + 6, x + w - 6, y + h + 20, outline=(240, 240, 240), width=1)
            p.line([(x + w / 2, y + h - 4), (x + w / 2 - 6, y + 14)], (230, 60, 230), 1)
        else:
            for k in range(4):
                cx, cy = x + 10 + (k % 2) * (w - 20), y + 10 + (k // 2) * (h / 2)
                p.ellipse(cx - 7, cy - 7, cx + 7, cy + 7, outline=(240, 240, 240), width=1)
    screen(8, 14, 44, 40, "pfd")
    screen(72, 14, 44, 40, "nd")
    screen(W / 2 - 30, 10, 60, 46, "eng")
    screen(W - 116, 14, 44, 40, "nd")
    screen(W - 52, 14, 44, 40, "pfd")
    for cx in (W / 2 - 46, W / 2 + 46):
        p.ellipse(cx - 9, 30 - 9, cx + 9, 30 + 9, fill=(20, 20, 20), outline=(200, 200, 200), width=1)
    return p


# ---------------------------------------------------------------- the layout

# a block turned to face north/east/south/west: the model's (x, z) offsets go to these
TURN = {"north": lambda x, z: (x, z), "east": lambda x, z: (-z, x), "south": lambda x, z: (-x, -z),
        "west": lambda x, z: (z, -x)}


def fittings():
    """Where the named fittings go: (x, y, z) -> block with its facing."""
    out = {}

    def put(x, y, z, name, facing="north"):
        out[(x, y, z)] = f"{name}[facing={facing}]"

    rng = np.random.default_rng(2140)

    def seat(x, z, biz=False):
        r = rng.random()
        k = "_bag" if r < 0.12 else "_torn" if r < 0.2 else "_recline" if r < 0.34 else "_belt" if r < 0.5 else ""
        put(x, 7, z, ("al_seat_biz" if biz else "al_seat") + k)

    # the cockpit: the panel across the nose, the pedestal, the seats and columns, the overhead
    for k, x in enumerate(range(-2, 2)):
        put(x, 8, 4, f"al_ck_panel_{k}")
        put(x, 7, 4, f"al_ck_knee_{k}")
    for x in (-1, 0):
        put(x, 7, 5, "al_ck_ped")
        put(x, 7, 6, "al_ck_ped_aft")
        put(x, 10, 5, "al_ck_over")
        put(x, 10, 6, "al_ck_over")
    for x in (-2, 1):
        put(x, 7, 5, "al_ck_yoke")
        put(x, 7, 6, "al_ck_seat")
    put(-3, 7, 6, "al_ck_side")
    put(2, 7, 6, "al_ck_side")
    # forward: the lavatory on the left, the galley on the right, door the L1 vestibule
    lav(out, -3, 12, "east")
    for z in (12, 13):
        put(2, 7, z, "al_galley_lo", "west")
        put(2, 8, z, "al_galley_mid", "west")
        put(2, 9, z, "al_galley_hi", "west")
    put(-1, 7, 11, "al_cart_tipped", "east")
    # business, the divider, economy with the exit row clear
    for z in (14, 16, 18):
        for x in (-3, -2, 1, 2):
            seat(x, z, True)
    for x in (-3, -2, 1, 2):
        put(x, 7, 20, "al_partition")
    put(-1, 9, 20, "al_curtain")
    for z in range(21, 40):
        if z == 27:
            continue
        for x in (-3, -2, 1, 2):
            seat(x, z)
    put(0, 7, 33, "al_cart_out")
    # aft: the lavatories either side, the doors, the galley across the back
    lav(out, -3, 40, "east")
    lav(out, 1, 40, "west")
    for x in range(-2, 2):
        put(x, 7, 46, "al_galley_lo")
        put(x, 8, 46, "al_galley_mid")
        put(x, 9, 46, "al_galley_hi")
    # the doors open: L1 forward of its opening, L2 the same
    put(-5, 7, 9, "al_door_leaf", "west")
    put(-5, 7, 41, "al_door_leaf", "west")
    return out


def lav(out, x0, z0, facing):
    """A lavatory 2 x 2 at x0..x0+1, z0..z0+1, its door toward the aisle (east or west)."""
    pieces = {(0, 0): "fl", (1, 0): "fr", (0, 1): "bl", (1, 1): "br"}
    turn = TURN[facing]
    cells = {turn(dx, dz): n for (dx, dz), n in pieces.items()}
    mx = min(c[0] for c in cells)
    mz = min(c[1] for c in cells)
    for (cx, cz), n in cells.items():
        out[(x0 + cx - mx, 7, z0 + cz - mz)] = f"al_lav_{n}[facing={facing}]"
        out[(x0 + cx - mx, 9, z0 + cz - mz)] = f"al_lav_{n}_up[facing={facing}]"


def layout():
    specs, shell_cells = compile_shell()
    cells = {c: f"{n}[facing=north]" for c, n in shell_cells.items()}
    for c, b in fittings().items():
        if c in cells:
            raise SystemExit(f"al_: {b} at {c} is in the shell's {cells[c]}")
        cells[c] = b
    return specs, cells


def blocks():
    specs, _ = compile_shell()
    out = dict(specs)
    for name, (fn, title, sound, render, shape) in PROPS.items():
        boxes = [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in fn()]
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound, "rotate": "facing", "render": render}
        if shape is not None:
            spec["shape"] = shape
        out[name] = spec
    return out


def dump(cells, out_dir):
    """A preview dump of the plane alone on a strip of apron."""
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
    out = Path(out_dir)
    out.parent.mkdir(parents=True, exist_ok=True)
    (out.parent / (out.name + ".json")).write_text(json.dumps({"size": [sx, H, sz], "origin": [x0, 0, z0], "palette": palette,
                                                               "lamps": [], "cars": []}))
    vox.astype("<u2").tofile(out.parent / (out.name + ".vox"))
    print(f"dump {out}: {sx} x {H} x {sz}, origin {x0},{z0}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump")
    a = ap.parse_args()
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    stray = [k for k in data["blocks"] if k.startswith("al_") and k not in made]
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("al_")}
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    specs, cells = layout()
    LAYOUT.parent.mkdir(parents=True, exist_ok=True)
    with open(LAYOUT, "w") as f:
        f.write("# Gray Eagle 214, written by tools/city/airliner.py: x y z block, the nose to the north\n")
        for (x, y, z), b in sorted(cells.items(), key=lambda kv: (kv[0][2], kv[0][1], kv[0][0])):
            f.write(f"{x} {y} {z} {b}\n")
    print(f"{len(made)} airliner blocks ({len(specs)} of its shell, {len(stray)} dropped), {len(cells)} placed; {LAYOUT}")
    if a.dump:
        dump(cells, a.dump)


if __name__ == "__main__":
    main()
