"""Clothing for ZomboidCraft: each garment built once in chunky 3D blocks round the player's body, then written out as
a loot model (the whole garment, as if on an invisible mannequin) and one worn model per body part, which
ClothingLayer draws on that part of the player so sleeves swing with the arms and trouser legs with the legs.

Style (memory zomboid-clothing-style): every garment and attachment is its own block over the body, flat muted
colour or pattern, a thin dark outline on the outer edges, a light line under top edges, lit tops and darker
undersides, stitching inside the edges, a little grime toward the bottom. Slightly worn, never bright.

Body parts are Minecraft's player model (y down, the front is -z, one unit is one skin pixel):
    head        (-4,-8,-4)..(4,0,4)        pivot (0,0,0)
    body        (-4,0,-2)..(4,12,2)        pivot (0,0,0)
    right_arm   (-3,-2,-2)..(1,10,2)       pivot (-5,2,0)
    left_arm    (-1,-2,-2)..(3,10,2)       pivot (5,2,0)
    right_leg   (-2,0,-2)..(2,12,2)        pivot (-1.9,12,0)
    left_leg    (-2,0,-2)..(2,12,2)        pivot (1.9,12,0)
Builders give boxes in a part's own coordinates; they're placed on the assembled body for painting and the loot model,
and moved back to the part for its worn model (model (8,8,8) is the part's pivot, +y up, +z the front).

Writes textures/item/clothing/<id>.png, models/item/clothing/<id>.json (+ /<id>/<part>.json), items/<id>.json,
items/clothing/<id>/<part>.json, and the names into lang/en_us.json.

    python3 tools/items/gen_clothes.py [id ...]
"""
import json
import re
import sys
import zlib
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/weapons'))
sys.path.insert(0, str(ROOT / 'tools/items'))
sys.path.insert(0, str(ROOT / 'tools/items/loot'))
import model as M  # noqa: E402
import paint as P  # noqa: E402
import gen_bags as B  # noqa: E402
import toon as T  # noqa: E402
import importlib.util  # noqa: E402
import io  # noqa: E402
import struct  # noqa: E402
_cars = importlib.util.spec_from_file_location('cars_pack', ROOT / 'tools/cars/pack.py')
_cars_pack = importlib.util.module_from_spec(_cars)
_cars.loader.exec_module(_cars_pack)

ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'
TX = 1.0 / M.PX      # one texel

PIVOTS = {
    'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0),
    'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0),
}
BOXES = {
    'head': ((-4, -8, -4), (4, 0, 4)), 'body': ((-4, 0, -2), (4, 12, 2)),
    'right_arm': ((-3, -2, -2), (1, 10, 2)), 'left_arm': ((-1, -2, -2), (3, 10, 2)),
    'right_leg': ((-2, 0, -2), (2, 12, 2)), 'left_leg': ((-2, 0, -2), (2, 12, 2)),
}
ARMS, LEGS = ('right_arm', 'left_arm'), ('right_leg', 'left_leg')
# assembled model space: x as the body, y up with the shoes' soles above the model limit, z flipped so +z is the front
Y0 = 12.0


# ================================================================== building


class Garment:
    """A garment under construction: boxes in body-part coordinates, kept on one model for painting."""

    def __init__(self, gid):
        self.m = M.Model(gid)

    def box(self, part, lo, hi, mat, **kw):
        """A box in a part's coordinates (y down, -z front)."""
        px, py, pz = PIVOTS[part]
        (x0, y0, z0), (x1, y1, z1) = lo, hi
        a = [8 + px + x0, Y0 - (py + y0), 8 - (pz + z0)]
        b = [8 + px + x1, Y0 - (py + y1), 8 - (pz + z1)]
        return self.m.box(a, b, mat, part=part, **kw)

    def shell(self, part, y0, y1, d, mat, box=None, **kw):
        """A sleeve of cloth d out from the part, from y0 down to y1.

        Started at the top of an arm or the torso, it closes over the shoulder d above the skin, as vanilla armour
        does: flush with the skin, the skin's outer layer (0.25 out) would show through on top of the shoulders."""
        if box is None and y0 <= BOXES[part][0][1] and part in ARMS + ('body',):
            y0 = BOXES[part][0][1] - d
        (bx0, _, bz0), (bx1, _, bz1) = box or BOXES[part]
        return self.box(part, (bx0 - d, y0, bz0 - d), (bx1 + d, y1, bz1 + d), mat, **kw)

    def both(self, parts, fn):
        """Runs fn(part, s) for each part, s = -1 on the right side (-x) and +1 on the left."""
        for p in parts:
            fn(p, -1 if p.startswith('right') else 1)

    def front(self, part, d, x0, y0, x1, y1, mat, t=0.18, **kw):
        """A flat piece on the front of a part's shell (pocket, patch, placket), t thick."""
        z = BOXES[part][0][2] - d
        return self.box(part, (x0, y0, z - t), (x1, y1, z), mat, **kw)

    def back(self, part, d, x0, y0, x1, y1, mat, t=0.18, **kw):
        z = BOXES[part][1][2] + d
        return self.box(part, (x0, y0, z), (x1, y1, z + t), mat, **kw)

    def side(self, part, d, sign, z0, y0, z1, y1, mat, t=0.18, **kw):
        """A flat piece on the outer side (sign -1: the -x side) of a part's shell."""
        lo, hi = BOXES[part]
        x = (lo[0] - d) if sign < 0 else (hi[0] + d)
        xa, xb = (x - t, x) if sign < 0 else (x, x + t)
        return self.box(part, (xa, y0, z0), (xb, y1, z1), mat, **kw)


# ================================================================== materials


def grade(c, amount=0.45):
    """The moody grade every character colour gets: desaturated and pulled toward soot."""
    lum = (c * np.array([0.3, 0.59, 0.11])).sum(-1, keepdims=True)
    c = c + (lum - c) * amount
    return P.mix(c, P.col('#1f1b15'), 0.12)


def toon(f, c, ink=True, stitch=None):
    """The cloth's own marks before the shared toon pass (tools/weapons/toon.py, as the guns and loot use): a light
    line under the top edge of side faces and, on faces big enough, a row of stitches just inside the garment's
    outside edge. The lit tops, shaded undersides, chipped edges and the outline round each piece come from
    toon.style in paint(); ink=False marks materials that never get an outline (they're listed in toon.PLAIN or
    are too small to take one)."""
    ny = f.n[1]
    if abs(ny) <= 0.5:
        top = f.box['hi'][1] - f.Y
        c = np.where(((top > TX) & (top < 2 * TX))[..., None], c * 1.18 + 0.02, c)
    if stitch is not None:
        big = min(f.dims[i] for i in range(3) if abs(f.n[i]) < 0.5) > 1.0
        if big:
            u, v = f.uv[..., 0], f.uv[..., 1]
            st = (np.abs(f.edge - 0.32) < TX * 0.6) & ((np.floor((u + v) / (2 * TX)) % 2) == 0)
            c = np.where(st[..., None], P.mix(c, P.col(stitch), 0.55), c)
    return c


def cloth(base, weave=0.1, mottle=0.14, stitch='#b8ad94', pattern=None):
    """Woven cloth: a fine weave, faded patches, the colour graded, then the toon pass."""
    def fn(f):
        c = P.plain(f, base, mottle)
        if pattern is not None:
            c = pattern(f, c)
        u, v = f.uv[..., 0], f.uv[..., 1]
        threads = ((np.floor(u / TX) + np.floor(v / TX)) % 2) * 2 - 1
        c = c * (1 + threads * weave * 0.3)[..., None]
        fade = np.clip((f.noise(0.4, 211, 3) - 0.52) * 1.8, 0, 1)
        c = P.mix(c, c * 1.15 + 0.03, fade * 0.45)
        return toon(f, grade(c), stitch=stitch)
    return fn


def knit(base, rib_axis=0):
    """Knitted wool: vertical ribs."""
    def fn(f):
        c = P.plain(f, base, 0.1)
        rib = (np.floor(f.P[..., rib_axis if abs(f.n[rib_axis]) < 0.5 else 2] / (2 * TX)) % 2)
        c = c * (0.86 + 0.18 * rib)[..., None]
        return toon(f, grade(c))
    return fn


def rib(base):
    """Ribbed cuffs and waistbands: ribs running up and down."""
    def fn(f):
        c = P.plain(f, base, 0.08)
        h = f.P[..., 0] if abs(f.n[0]) < 0.5 else f.P[..., 2]
        c = c * (0.82 + 0.22 * (np.floor(h / (1.5 * TX)) % 2))[..., None]
        return toon(f, grade(c))
    return fn


def plaid(a, b, size=1.6):
    """Flannel check: bands of the dark colour both ways over the base, darker where they cross."""
    def pat(f, c):
        h = f.P[..., 0] if abs(f.n[0]) < 0.5 else f.P[..., 2]
        v = f.P[..., 1]
        bh = (np.floor(h / (size / 2)) % 2) == 0
        bv = (np.floor(v / (size / 2)) % 2) == 0
        line_h = (np.abs((h % size) - size * 0.75) < TX * 0.6)
        line_v = (np.abs((v % size) - size * 0.75) < TX * 0.6)
        dark = P.col(b)
        c = np.where((bh & bv)[..., None], dark * 0.9, np.where((bh | bv)[..., None], P.mix(c, dark, 0.5), c))
        return np.where((line_h | line_v)[..., None], P.mix(c, P.col(a) * 1.4 + 0.1, 0.5), c)
    return pat


def denim(base='#34465e'):
    def pat(f, c):
        u, v = f.uv[..., 0], f.uv[..., 1]
        twill = (np.floor((u + v) / TX) % 3) == 0
        c = np.where(twill[..., None], c * 1.18 + 0.03, c)
        # worn pale on the knees, seat and thighs: the big faces fade in the middle
        mid = np.clip(f.edge / 0.9, 0, 1)
        return P.mix(c, c * 1.35 + 0.05, mid * 0.25)
    return cloth(base, weave=0.05, stitch='#a8834a', pattern=pat)


def leather(base, stitch=None, gloss=0.12):
    def fn(f):
        c = P.leather(f, base)
        rub = np.clip(1 - f.edge / 0.3, 0, 1) * gloss
        c = c + rub[..., None] * 0.3
        return toon(f, grade(c, 0.3), stitch=stitch)
    return fn


def rubber(base, stud=False):
    def fn(f):
        c = P.plain(f, base, 0.08)
        if stud and f.n[1] < -0.5:
            u, v = f.uv[..., 0], f.uv[..., 1]
            lug = ((np.floor(u / (3 * TX)) + np.floor(v / (3 * TX))) % 2) == 0
            c = np.where(lug[..., None], c * 0.6, c)
        return toon(f, grade(c, 0.3))
    return fn


def metal(kind='steel'):
    def fn(f):
        c = P.brass(f) if kind == 'brass' else P.steel(f, 0.5) if kind == 'steel' else P.plain(f, '#7a4a2a', 0.1) * 1.2
        return toon(f, grade(c, 0.25), ink=False)
    return fn


def flat(colour, noise=0.08, ink=True):
    def fn(f):
        return toon(f, grade(P.plain(f, colour, noise)), ink=ink)
    return fn


def lens(f):
    c = P.plain(f, '#121417', 0.04)
    u = f.uv[..., 0] - f.uv[..., 1]
    shine = np.abs((u % 1.6) - 0.4) < TX
    c = np.where(shine[..., None], c + 0.25, c)
    return toon(f, c)


def fur(base='#6d6152'):
    def fn(f):
        c = P.plain(f, base, 0.3)
        tuft = P.white(f.shape, f.rng) > 0.7
        c = np.where(tuft[..., None], c * 1.25, c)
        return toon(f, grade(c, 0.3), ink=False)
    return fn


def paisley(base, ink_col='#c8bfae'):
    def pat(f, c):
        u, v = f.uv[..., 0], f.uv[..., 1]
        dots = (np.abs((u % 1.0) - 0.5) < TX) & (np.abs((v % 1.0) - 0.5) < TX)
        rings = np.abs(np.hypot((u % 2.0) - 1.0, (v % 2.0) - 1.0) - 0.6) < TX * 0.7
        return np.where((dots | rings)[..., None], P.mix(c, P.col(ink_col), 0.6), c)
    return cloth(base, stitch=None, pattern=pat)


def worn(wear=0.3, low=None):
    """Light grime that gathers toward the bottom (low: the model-space y it's heaviest under) and a few stains."""
    def grime(f, c):
        g = P.grime(f, c, 0.12 + wear * 0.3)
        if low is not None:
            down = np.clip((low - f.Y) / 6.0, 0, 1)
            g = P.mix(g, g * P.col('#5a4c3a') * 1.3, down * 0.35 * wear)
        stain = np.clip((f.noise(0.7, 233, 3) - 0.68) * 5, 0, 1) * wear
        return P.mix(g, g * 0.72, stain)
    return [grime]


# ================================================================== the garments
# Inflation over the skin, so layers sit over each other: shirt, then jacket, then vest.
SHIRT, JACKET, PANTS, BOOT = 0.35, 0.75, 0.4, 0.55
# A skin's eyes are the head's fifth row of pixels. RULE (Josiah 2026-10-29): face pieces (bandanas, masks, scarves)
# rest below EYES_LOW and headwear above EYES_HIGH, so the eyes always show; only eyewear goes over them.
EYES_HIGH, EYES_LOW = -4.0, -3.0


def collar_flaps(g, d, mat, back=True):
    """A shirt collar: two points lying on the chest either side of the neck, and a band round the back."""
    z = -2 - d
    for s in (-1, 1):
        g.box('body', (s * 0.3, -0.35, z - 0.3), (s * 2.7, 1.5, z), mat)
        g.box('body', (s * 0.6, 1.5, z - 0.3), (s * 1.8, 2.1, z), mat)
    if back:
        g.box('body', (-3.0, -0.75, 0.6), (3.0, 0.15, 2 + d + 0.25), mat)


def buttons(g, part, d, x, ys, mat='button'):
    z = BOXES[part][0][2] - d
    for y in ys:
        g.box(part, (x - 0.3, y, z - 0.38), (x + 0.3, y + 0.55, z - 0.18), mat)


def build_t_shirt(g):
    g.shell('body', 0, 12, SHIRT, 'cloth')
    g.shell('body', 11.3, 12.25, SHIRT + 0.1, 'hem')
    # crew neck: a ribbed ring round the opening on top
    for s in (-1, 1):
        g.box('body', (s * 1.6, -0.3, -2.4), (s * 2.5, 0.25, 1.4), 'collar')
    g.box('body', (-2.5, -0.3, -2.45), (2.5, 0.25, -1.6), 'collar')
    g.box('body', (-2.5, -0.3, 0.6), (2.5, 0.25, 1.4), 'collar')
    g.both(ARMS, lambda p, s: (g.shell(p, -2.0, 3.0, SHIRT + 0.05, 'cloth'), g.shell(p, 2.5, 3.4, SHIRT + 0.15, 'hem')))


def build_flannel(g):
    g.shell('body', 0, 12, SHIRT, 'cloth')
    g.shell('body', 11.4, 12.5, SHIRT + 0.1, 'cloth')            # shirttail hanging over the waist
    g.front('body', SHIRT, -0.55, 0, 0.55, 12.5, 'placket')
    buttons(g, 'body', SHIRT + 0.18, 0, (2.0, 4.6, 7.2, 9.8))
    collar_flaps(g, SHIRT, 'collar')
    for s in (-1, 1):                                            # chest pockets with flaps and a button
        x0, x1 = (-3.4, -1.1) if s < 0 else (1.1, 3.4)
        g.front('body', SHIRT, x0, 2.4, x1, 5.2, 'pocket', t=0.14)
        g.front('body', SHIRT + 0.14, x0 - 0.1, 2.1, x1 + 0.1, 3.1, 'pocket', t=0.14)
        buttons(g, 'body', SHIRT + 0.28, (x0 + x1) / 2, (2.55,))
    def arm(p, s):
        g.shell(p, -2.0, 8.0, SHIRT + 0.05, 'cloth')
        g.shell(p, 7.6, 8.8, SHIRT + 0.2, 'cuff')
    g.both(ARMS, arm)


def build_hoodie(g):
    d = JACKET
    g.shell('body', 0, 10.8, d, 'cloth')
    g.shell('body', 10.6, 12.3, d + 0.15, 'rib')
    # the kangaroo pocket, its hand openings dark down the sides
    g.front('body', d, -2.9, 6.6, 2.9, 10.5, 'cloth', t=0.25)
    for s in (-1, 1):
        g.front('body', d + 0.25, s * 2.5, 7.0, s * 2.95, 10.2, 'shadow', t=0.02)
    # the hood lying in a roll round the back of the neck
    g.box('body', (-3.6, -1.9, 0.0), (3.6, 0.9, 2 + d + 0.9), 'hood')
    for s in (-1, 1):
        g.box('body', (s * 2.4, -1.4, -2.0 - d - 0.2), (s * 3.7, 0.6, 0.2), 'hood')
    g.box('body', (-2.4, -0.6, 1.0), (2.4, 0.1, 2.2), 'shadow')
    for s in (-1, 1):                                            # drawstrings with metal tips
        g.front('body', d, s * 1.25 - 0.13, 0.0, s * 1.25 + 0.13, 4.4, 'string', t=0.16)
        g.front('body', d, s * 1.25 - 0.17, 4.4, s * 1.25 + 0.17, 5.0, 'tip', t=0.2)
    def arm(p, s):
        g.shell(p, -2.0, 7.6, d, 'cloth')
        g.shell(p, 7.4, 8.9, d + 0.12, 'rib')
    g.both(ARMS, arm)


def build_denim_jacket(g):
    d = JACKET
    g.shell('body', 0, 10.8, d, 'denim')
    g.shell('body', 10.5, 11.8, d + 0.15, 'denim')               # waistband
    g.front('body', d, -0.6, 0, 0.6, 11.8, 'denim', t=0.16)
    buttons(g, 'body', d + 0.16, 0, (2.0, 4.6, 7.2, 9.6, 10.9), 'rivet')
    collar_flaps(g, d, 'denim')
    for s in (-1, 1):
        x0, x1 = (-3.5, -1.1) if s < 0 else (1.1, 3.5)
        g.front('body', d, x0, 2.6, x1, 5.4, 'denim', t=0.12)                 # chest pocket
        g.front('body', d + 0.12, x0 - 0.1, 2.3, x1 + 0.1, 3.4, 'denim', t=0.14)   # pointed flap
        g.front('body', d + 0.26, (x0 + x1) / 2 - 0.5, 3.4, (x0 + x1) / 2 + 0.5, 3.9, 'denim', t=0.14)
        buttons(g, 'body', d + 0.4, (x0 + x1) / 2, (3.0,), 'rivet')
        g.front('body', d, x0 - 0.3, 5.9, x1 + 0.3, 6.1, 'seam', t=0.06)      # the yoke seam under the pockets
    def arm(p, s):
        g.shell(p, -2.0, 8.0, d, 'denim')
        g.shell(p, 7.6, 8.8, d + 0.15, 'denim')
        buttons(g, p, d + 0.15, -1 if p == 'right_arm' else 1, (7.9,), 'rivet')
    g.both(ARMS, arm)


def build_parka(g):
    d = 1.1
    g.shell('body', 0, 12, d, 'cloth')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 3.6, d + 0.05, 'cloth'))   # the skirt over the hips
    g.front('body', d, -0.9, -0.2, 0.9, 12, 'cloth', t=0.25)              # storm flap over the zip
    buttons(g, 'body', d + 0.25, 0, (1.5, 4.5, 7.5, 10.5), 'toggle')
    # the hood down on the back, its fur ruff round the neck
    g.box('body', (-3.8, -2.3, 0.0), (3.8, 1.6, 2 + d + 1.2), 'cloth')
    g.box('body', (-4.2, -2.0, -2.0 - d - 0.4), (-2.6, 0.8, 0.6), 'fur')
    g.box('body', (2.6, -2.0, -2.0 - d - 0.4), (4.2, 0.8, 0.6), 'fur')
    g.box('body', (-4.2, -2.8, 0.2), (4.2, -1.6, 2 + d + 1.5), 'fur')
    for s in (-1, 1):
        x0, x1 = (-3.8, -1.3) if s < 0 else (1.3, 3.8)
        g.front('body', d, x0, 2.6, x1, 5.2, 'cloth', t=0.2)               # chest pockets
        g.front('body', d + 0.2, x0 - 0.1, 2.3, x1 + 0.1, 3.2, 'cloth', t=0.15)
        g.front('body', d, x0, 7.4, x1, 11.0, 'cloth', t=0.35)             # bellows pockets
        g.front('body', d + 0.35, x0 - 0.1, 7.1, x1 + 0.1, 8.2, 'cloth', t=0.15)
        buttons(g, 'body', d + 0.5, (x0 + x1) / 2, (7.5,), 'toggle')
    def arm(p, s):
        g.shell(p, -2.0, 8.0, d, 'cloth')
        g.shell(p, 7.6, 9.0, d + 0.15, 'cuff')
        g.side(p, d, s, -1.6, 0.5, 1.6, 3.0, 'patch', t=0.12)              # the shoulder pocket
    g.both(ARMS, arm)


def trouser_top(g, d, mat, loops='loop'):
    """The waistband round the bottom of the torso, with belt loops."""
    g.shell('body', 9.6, 12, d + 0.05, mat)
    for x in (-3.4, -1.2, 1.2, 3.4):
        g.front('body', d + 0.05, x - 0.25, 9.4, x + 0.25, 11.2, loops, t=0.14)
    for x in (-2.0, 2.0):
        g.back('body', d + 0.05, x - 0.25, 9.4, x + 0.25, 11.2, loops, t=0.14)


def build_jeans(g):
    d = PANTS
    trouser_top(g, d, 'denim')
    g.front('body', d + 0.05, -0.25, 10.4, 0.25, 12, 'seam', t=0.05)       # the fly
    buttons(g, 'body', d + 0.05, 0, (10.0,), 'rivet')
    def leg(p, s):
        g.shell(p, 0, 11.0, d, 'denim')
        g.shell(p, 10.8, 12.1, d + 0.15, 'cuff')                            # rolled hem, the pale inside out
        g.front(p, d, -1.9 if s > 0 else -0.1, 0.2, 0.1 if s > 0 else 1.9, 0.5, 'seam', t=0.05)   # pocket mouths
        g.back(p, d, -1.6, 0.6, 1.6, 3.8, 'denim', t=0.14)                  # back pocket
        g.back(p, d + 0.14, -1.0, 1.6, 1.0, 1.8, 'seam', t=0.03)
        g.side(p, d, s, -0.06, 0.5, 0.06, 10.8, 'seam', t=0.04)             # outseam
    g.both(LEGS, leg)


def build_work_pants(g):
    d = PANTS
    trouser_top(g, d, 'cloth')
    def leg(p, s):
        g.shell(p, 0, 11.0, d, 'cloth')
        g.shell(p, 10.9, 12.1, d + 0.12, 'hem')
        g.front(p, d, -1.8, 4.2, 1.8, 7.6, 'patch', t=0.16)                # double knees
        g.back(p, d, -1.5, 0.6, 1.5, 3.6, 'cloth', t=0.14)
        if s < 0:                                                           # hammer loop and rule pocket
            g.side(p, d, s, -0.9, 0.8, 0.3, 4.6, 'patch', t=0.14)
            g.side(p, d + 0.14, s, -0.6, 0.6, 0.0, 1.6, 'shadow', t=0.02)
            g.side(p, d, s, 0.6, 1.0, 1.2, 1.8, 'leather', t=0.4)
    g.both(LEGS, leg)


def shoe(g, p, s, upper_top, d=BOOT, sole='sole', upper='upper', toe=3.0):
    """A shoe or boot on the bottom of a leg: sole and heel, toe box out in front, the upper up the ankle."""
    g.box(p, (-2.3, 11.35, -2 - toe), (2.3, 12.25, 2.35), sole)
    g.box(p, (-2.3, 11.05, 1.0), (2.3, 11.4, 2.35), sole)                  # heel
    g.shell(p, upper_top, 11.4, d, upper)
    g.box(p, (-2.15, 10.0, -2 - toe + 0.1), (2.15, 11.4, -2 - d + 0.02), upper)   # toe box


def laces(g, p, d, y0, y1, step=0.9):
    z = BOXES[p][0][2] - d
    g.box(p, (-0.9, y0 - 0.2, z - 0.14), (0.9, y1, z), 'tongue')
    y = y0
    while y < y1 - 0.3:
        g.box(p, (-1.05, y, z - 0.26), (1.05, y + 0.28, z - 0.12), 'lace')
        y += step


def build_sneakers(g):
    def foot(p, s):
        shoe(g, p, s, 9.6, d=0.45, toe=2.9)
        laces(g, p, 0.45, 9.4, 10.6, 0.6)
        g.side(p, 0.45, s, -1.6, 10.2, 1.4, 10.9, 'stripe', t=0.08)        # side flash
        g.box(p, (-0.6, 9.1, 2.3), (0.6, 10.0, 2.6), 'stripe')             # heel tab
    g.both(LEGS, foot)


def build_work_boots(g):
    def foot(p, s):
        shoe(g, p, s, 6.3, sole='lugsole', toe=3.1)
        g.box(p, (-2.2, 10.3, -5.2), (2.2, 11.42, -3.6), 'toecap')
        g.shell(p, 6.0, 6.8, BOOT + 0.18, 'collar')                        # padded collar
        laces(g, p, BOOT, 6.9, 10.4)
        for y in (7.2, 8.4, 9.6):                                          # speed hooks
            for x in (-1.15, 1.15):
                g.front(p, BOOT, x - 0.15, y, x + 0.15, y + 0.3, 'hook', t=0.3)
        g.back(p, BOOT, -0.5, 5.4, 0.5, 6.8, 'collar', t=0.15)              # pull tab
        g.box(p, (-2.4, 11.6, -2 - 3.1), (2.4, 12.35, 2.45), 'lugsole')
    g.both(LEGS, foot)


def build_baseball_cap(g):
    d = 0.5
    g.box('head', (-4 - d, -8 - d, -4 - d), (4 + d, -5.0, 4 + d), 'cloth')
    g.box('head', (-3.6, -5.4, -8.6), (3.6, -4.95, -4 - d + 0.05), 'brim')
    g.box('head', (-2.8, -5.5, -8.9), (2.8, -5.05, -8.5), 'brim')
    g.box('head', (-0.55, -8 - d - 0.35, -0.55), (0.55, -8 - d, 0.55), 'cloth')   # button on top
    g.box('head', (-1.6, -7.6, -4 - d - 0.12), (1.6, -5.7, -4 - d), 'patch')      # front patch
    g.box('head', (-1.4, -6.4, 4 + d), (1.4, -5.0, 4 + d + 0.15), 'shadow')       # the strap gap at the back
    g.box('head', (-1.2, -6.1, 4 + d + 0.1), (1.2, -5.7, 4 + d + 0.28), 'strap')


def build_beanie(g):
    d = 0.55
    g.box('head', (-4 - d, -8 - d - 0.4, -4 - d), (4 + d, -4.4, 4 + d), 'knit')
    g.box('head', (-4 - d - 0.25, -5.9, -4 - d - 0.25), (4 + d + 0.25, EYES_HIGH - 0.15, 4 + d + 0.25), 'rib')   # turned-up cuff
    g.box('head', (-3.0, -8 - d - 1.0, -3.0), (3.0, -8 - d - 0.4, 3.0), 'knit')


def build_work_gloves(g):
    def hand(p, s):
        lo, hi = BOXES[p]
        g.shell(p, 8.4, 10.35, 0.45, 'leather')
        g.shell(p, 7.4, 8.9, 1.0, 'canvas')                                 # gauntlet cuff, over a sleeve's cuff
        g.side(p, 0.45, s, -1.6, 9.0, 1.6, 10.0, 'pad', t=0.2)              # knuckle pad on the back of the hand
        g.box(p, (lo[0] - 0.45, 10.35, lo[2] - 0.45), (hi[0] + 0.45, 10.6, hi[2] + 0.45), 'leather')
    g.both(ARMS, hand)


def build_sunglasses(g):
    z = -4.42
    for s in (-1, 1):
        x0, x1 = (-3.5, -0.45) if s < 0 else (0.45, 3.5)
        g.box('head', (x0, -5.2, z - 0.2), (x1, -3.7, z), 'lens')
        g.box('head', (x0 - 0.1, -5.4, z - 0.26), (x1 + 0.1, -5.15, z - 0.05), 'frame')
        xs = -4.45 if s < 0 else 4.3
        g.box('head', (xs, -5.15, z), (xs + 0.15, -4.75, 0.6), 'frame')    # temples back to the ears
    g.box('head', (-0.5, -5.0, z - 0.22), (0.5, -4.65, z - 0.05), 'frame')


def build_bandana(g):
    # face pieces sit below the eyes (a skin's eyes are the head's fifth row, y -4 to -3): RULE from Josiah
    z = -4.32
    g.box('head', (-4.35, EYES_LOW + 0.15, z - 0.18), (4.35, -0.6, z), 'cloth')    # over the nose and mouth
    for s in (-1, 1):
        x = -4.35 if s < 0 else 4.2
        g.box('head', (x, EYES_LOW + 0.15, z), (x + 0.15, -1.5, 4.3), 'cloth')    # round the sides
    g.box('head', (-3.2, -0.6, z - 0.18), (3.2, 0.6, z - 0.02), 'cloth')          # the hanging point
    g.box('head', (-1.9, 0.6, z - 0.18), (1.9, 1.4, z - 0.02), 'cloth')
    g.box('head', (-0.7, 1.4, z - 0.18), (0.7, 2.0, z - 0.02), 'cloth')
    g.box('head', (-0.8, -2.75, 4.3), (0.8, -1.8, 4.75), 'knot')                  # the knot and its tails
    g.box('head', (-0.9, -1.8, 4.35), (-0.3, 0.0, 4.6), 'knot')
    g.box('head', (0.3, -1.8, 4.35), (0.9, -0.6, 4.6), 'knot')


# id: (builder, materials, wear)
GARMENTS = {
    't_shirt': (build_t_shirt, dict(cloth=cloth('#55606b'), hem=cloth('#4c5660'), collar=rib('#4c5660')), 0.3),
    'flannel_shirt': (build_flannel, dict(
        cloth=cloth('#7a2e26', pattern=plaid('#7a2e26', '#1d1a19')), placket=cloth('#7a2e26', pattern=plaid('#7a2e26', '#1d1a19', 1.2)),
        pocket=cloth('#7a2e26', pattern=plaid('#7a2e26', '#1d1a19', 1.2)), collar=cloth('#6a2822', pattern=plaid('#6a2822', '#1d1a19', 1.0)),
        cuff=cloth('#6a2822', pattern=plaid('#6a2822', '#1d1a19', 1.0)), button=flat('#c9c1ad', ink=False)), 0.35),
    'hoodie': (build_hoodie, dict(cloth=cloth('#3d4552', weave=0.06), rib=rib('#353c48'), hood=cloth('#363e4a'),
                                  shadow=flat('#0e0f12', ink=False), string=flat('#b9b2a2'), tip=metal()), 0.3),
    'denim_jacket': (build_denim_jacket, dict(denim=denim('#3e5675'), rivet=metal('copper'), seam=flat('#8a6a3a', ink=False)), 0.4),
    'parka': (build_parka, dict(cloth=cloth('#4b4f36', weave=0.05), cuff=rib('#3a3d2a'), fur=fur(), toggle=flat('#2b2622'),
                                patch=cloth('#454930')), 0.35),
    'jeans': (build_jeans, dict(denim=denim(), cuff=denim('#56709a'), loop=denim(), seam=flat('#9a7a44', ink=False),
                                rivet=metal('copper')), 0.4),
    'work_pants': (build_work_pants, dict(cloth=cloth('#6b5a3e', weave=0.14, stitch='#d4b98a'), hem=cloth('#5e4f36'),
                                          patch=cloth('#5e4f36', stitch='#d4b98a'), loop=cloth('#5e4f36'),
                                          shadow=flat('#100e0b', ink=False), leather=leather('#4a2e1a')), 0.45),
    'sneakers': (build_sneakers, dict(sole=rubber('#a6a297'), upper=cloth('#55595f', stitch='#9a968c'), tongue=cloth('#4b4f55'),
                                      lace=flat('#bdb7aa'), stripe=leather('#6e2e28')), 0.4),
    'work_boots': (build_work_boots, dict(lugsole=rubber('#1e1b19', stud=True), sole=rubber('#1e1b19'), upper=leather('#5c4029', stitch='#c9a46a'),
                                          toecap=leather('#4e3622'), collar=leather('#3e2b1c'), tongue=leather('#553b26'),
                                          lace=flat('#8a7a5c'), hook=metal('brass')), 0.5),
    'baseball_cap': (build_baseball_cap, dict(cloth=cloth('#5c2a26'), brim=cloth('#4a221f'), patch=flat('#b8ab8c'),
                                              shadow=flat('#0d0c0b', ink=False), strap=metal()), 0.35),
    'beanie': (build_beanie, dict(knit=knit('#3a3d42'), rib=rib('#34373b')), 0.3),
    'work_gloves': (build_work_gloves, dict(leather=leather('#8a6a3e', stitch='#5a4228'), canvas=cloth('#6b5a3e'),
                                            pad=leather('#6e5230')), 0.5),
    'sunglasses': (build_sunglasses, dict(lens=lens, frame=flat('#1a1a1b')), 0.1),
    'bandana': (build_bandana, dict(cloth=paisley('#6c2a27'), knot=paisley('#5e2522')), 0.3),
}


# ------------------------------------------------------------------ batch 2: headwear
# Head pixels: the head is (-4,-8,-4)..(4,0,4), the front at -z. The eye row is y -4 to -3 (EYES_HIGH, EYES_LOW):
# hats and helmets stop above it at the front, face pieces start below it, and anything that has to cross it
# (a hockey mask, a gas mask, a full-face helmet) leaves the eyes open.
HX = 4.0


def ring(g, y0, y1, d, mat, t=0.4, part='head'):
    """A band round the head, d out from it, t thick."""
    a, b = HX + d, HX + d + t
    g.box(part, (-b, y0, -b), (b, y1, -a), mat)
    g.box(part, (-b, y0, a), (b, y1, b), mat)
    g.box(part, (-b, y0, -a), (-a, y1, a), mat)
    g.box(part, (a, y0, -a), (b, y1, a), mat)


def dome(g, top, bottom, d, mat, steps=((0.0, 0.45), (0.55, 0.4), (1.3, 0.35))):
    """A rounded shell: full width at the bottom, stepping in toward the top."""
    y = bottom
    rows = [(inset, h) for inset, h in steps]
    body_top = top + sum(h for _, h in rows[1:])
    g.box('head', (-HX - d, body_top, -HX - d), (HX + d, bottom, HX + d), mat)
    y = body_top
    for inset, h in rows[1:]:
        g.box('head', (-HX - d + inset, y - h, -HX - d + inset), (HX + d - inset, y, HX + d - inset), mat)
        y -= h


def eye_open_front(g, z0, z1, y_top, y_bot, half, mat, port=(3.6, EYES_HIGH - 0.6, EYES_LOW + 0.4)):
    """A face panel from y_top to y_bot across x -half..half at z0..z1, with a port left open over the eyes."""
    px, py0, py1 = port
    g.box('head', (-half, y_top, z0), (half, py0, z1), mat)
    g.box('head', (-half, py1, z0), (half, y_bot, z1), mat)
    g.box('head', (-half, py0, z0), (-px, py1, z1), mat)
    g.box('head', (px, py0, z0), (half, py1, z1), mat)


def build_trucker_cap(g):
    d = 0.5
    g.box('head', (-4 - d, -8 - d, -4 - d), (4 + d, -5.0, 0.4), 'foam')            # the foam front panels
    g.box('head', (-4 - d, -8 - d, 0.4), (4 + d, -5.0, 4 + d), 'mesh')              # mesh back
    g.box('head', (-3.6, -5.4, -8.4), (3.6, -4.95, -4 - d + 0.05), 'brim')
    g.box('head', (-2.6, -5.5, -8.7), (2.6, -5.05, -8.3), 'brim')
    g.box('head', (-0.55, -8 - d - 0.35, -0.55), (0.55, -8 - d, 0.55), 'mesh')
    g.box('head', (-2.4, -7.9, -4 - d - 0.12), (2.4, -5.6, -4 - d), 'patch')
    g.box('head', (-1.4, -6.4, 4 + d), (1.4, -5.0, 4 + d + 0.15), 'shadow')
    g.box('head', (-1.6, -6.0, 4 + d + 0.08), (1.6, -5.6, 4 + d + 0.3), 'snap')


def build_cowboy_hat(g):
    d = 0.5
    g.box('head', (-3.9, -10.6, -4.3), (3.9, -5.6, 4.3), 'felt')                    # the crown
    g.box('head', (-0.5, -10.7, -3.6), (0.5, -9.8, 3.6), 'crease')                  # the centre crease
    for s in (-1, 1):                                                               # front pinches
        g.box('head', (s * 2.4 - 0.5, -10.4, -4.4), (s * 2.4 + 0.5, -8.6, -3.2), 'crease')
    g.box('head', (-4.0, -6.6, -4.4), (4.0, -5.6, 4.4), 'band')                     # hat band
    g.box('head', (-4.15, -6.5, -4.55), (-2.8, -5.7, -4.4), 'concho')
    g.box('head', (-7.0, -5.6, -7.4), (7.0, -5.15, 7.4), 'felt')                    # wide brim
    for s in (-1, 1):                                                               # rolled-up sides
        g.box('head', (s * 6.2 - 0.8, -6.6, -6.4), (s * 6.2 + 0.8, -5.6, 6.4), 'felt')
    g.box('head', (-5.6, -5.15, -7.5), (5.6, -4.95, -6.6), 'felt')                  # brim dips a touch at the front


def build_bucket_hat(g):
    d = 0.5
    g.box('head', (-4 - d, -8 - d - 0.3, -4 - d), (4 + d, -5.2, 4 + d), 'cloth')
    g.box('head', (-4 - d - 0.1, -6.2, -4 - d - 0.1), (4 + d + 0.1, -5.2, 4 + d + 0.1), 'band')
    ring(g, -5.3, -4.8, d + 0.1, 'brim', t=1.4)                                    # the brim, sloping down
    ring(g, -4.8, -4.25, d + 1.5, 'brim', t=0.5)
    for s in (-1, 1):
        g.box('head', (s * 2.6 - 0.25, -7.0, -4 - d - 0.08), (s * 2.6 + 0.25, -6.5, -4 - d), 'eyelet')


def build_hard_hat(g):
    d = 0.9
    dome(g, -9.6, -5.3, d, 'shell', ((0, 0), (0.6, 0.5), (1.4, 0.4)))
    g.box('head', (-0.6, -10.0, -4 - d - 0.2), (0.6, -9.0, 4 + d + 0.2), 'shell')    # the ridge front to back
    ring(g, -5.6, -5.15, d, 'shell', t=0.45)                                        # rim
    g.box('head', (-3.8, -5.6, -7.4), (3.8, -5.15, -4 - d), 'shell')                 # peak
    g.box('head', (-2.2, -8.4, -4 - d - 0.1), (-0.8, -7.0, -4 - d), 'sticker')
    g.box('head', (0.9, -8.0, -4 - d - 0.1), (2.4, -6.9, -4 - d), 'sticker2')
    g.box('head', (-HX - 0.1, -5.3, -2.2), (HX + 0.1, -4.9, 2.2), 'harness')       # suspension band inside the rim


def build_bike_helmet(g):
    d = 0.8
    g.box('head', (-4 - d, -9.0, -4 - d), (4 + d, -5.3, 4 + d + 0.6), 'shell')
    g.box('head', (-3.8, -9.7, -3.6), (3.8, -9.0, 4.4), 'shell')
    for x in (-2.4, -0.6, 1.2):                                                      # vents front to back
        g.box('head', (x, -9.75, -3.0), (x + 1.0, -9.6, 3.8), 'vent')
    for x in (-3.4, 2.6):
        g.box('head', (x, -8.7, -4 - d - 0.05), (x + 0.8, -6.6, -4 - d + 0.1), 'vent')
    g.box('head', (-3.4, -5.7, -6.4), (3.4, -5.35, -4 - d), 'visor')
    g.box('head', (-4 - d, -5.6, -4 - d), (4 + d, -5.3, 4 + d + 0.6), 'trim')
    for s in (-1, 1):                                                                # straps round the ears to the chin
        x = s * (HX + 0.1) - (0.12 if s < 0 else 0)
        g.box('head', (x, -5.3, -1.4), (x + 0.12, 0.2, -0.9), 'strap')
        g.box('head', (x, -5.3, 1.0), (x + 0.12, -2.4, 1.5), 'strap')
    g.box('head', (-1.2, 0.0, -1.4), (1.2, 0.3, -0.9), 'strap')
    g.box('head', (-0.5, -0.05, -1.55), (0.5, 0.4, -0.75), 'buckle')


def build_football_helmet(g):
    d = 0.9
    g.box('head', (-4 - d, -9.0, -4 - d), (4 + d, -4.15, 4 + d), 'shell')           # shell, its front edge over the eyes
    g.box('head', (-3.8, -9.7, -3.8), (3.8, -9.0, 3.8), 'shell')
    for s in (-1, 1):                                                                # sides down over the ears and jaw
        x0, x1 = (-4 - d, -4 + 0.2) if s < 0 else (4 - 0.2, 4 + d)
        g.box('head', (x0, -4.15, -2.6), (x1, -0.8, 4 + d), 'shell')
        g.box('head', (x0 - 0.05, -4.0, -0.6), (x1 + 0.05, -2.4, 0.6), 'ear')        # ear hole
    g.box('head', (-4 - d, -4.15, 2.6), (4 + d, -1.6, 4 + d), 'shell')              # back of the neck
    g.box('head', (-0.6, -9.75, -4 - d - 0.05), (0.6, -4.2, 4 + d + 0.05), 'stripe')
    # facemask: bars across below the eyes, two struts up to the shell
    zf = -4 - d - 1.6
    for y in (-2.7, -1.2, 0.2):
        g.box('head', (-3.6, y, zf), (3.6, y + 0.35, zf + 0.35), 'cage')
    for x in (-1.3, 1.0):
        g.box('head', (x, -2.7, zf), (x + 0.3, 0.55, zf + 0.35), 'cage')
    for s in (-1, 1):
        x = s * 3.6 - (0.35 if s > 0 else 0)
        g.box('head', (x, -4.4, zf), (x + 0.35, 0.55, zf + 0.35), 'cage')
        g.box('head', (x, -4.4, zf), (x + 0.35, -4.05, -4 - d), 'cage')              # where the cage clips on
    g.box('head', (-1.4, -0.8, -4 - d - 0.2), (1.4, 0.4, -4 - d + 0.2), 'chin')     # chin cup


def build_hockey_mask(g):
    z0, z1 = -4.58, -4.3
    g.box('head', (-3.6, -7.8, z0), (3.6, EYES_HIGH - 0.15, z1), 'mask')             # forehead
    g.box('head', (-3.6, EYES_HIGH - 0.15, z0), (-3.2, EYES_LOW + 0.15, z1), 'mask')  # round the eye holes
    g.box('head', (-0.8, EYES_HIGH - 0.15, z0), (0.8, EYES_LOW + 0.15, z1), 'mask')
    g.box('head', (3.2, EYES_HIGH - 0.15, z0), (3.6, EYES_LOW + 0.15, z1), 'mask')
    g.box('head', (-3.6, EYES_LOW + 0.15, z0), (3.6, -0.6, z1), 'mask')             # cheeks and mouth
    g.box('head', (-2.4, -0.6, z0), (2.4, 0.3, z1), 'mask')                         # chin
    g.box('head', (-0.5, -5.9, z0 - 0.2), (0.5, -1.0, z0), 'mask')                  # the moulded nose ridge
    for s in (-1, 1):                                                               # red chevrons on the brow
        g.box('head', (s * 1.7 - 0.6, -7.2, z0 - 0.05), (s * 1.7 + 0.6, -6.6, z0 + 0.02), 'chevron')
    for s in (-1, 1):                                                               # straps round the head
        x = -4.32 if s < 0 else 4.2
        g.box('head', (x, -6.4, z1), (x + 0.12, -5.8, 4.32), 'strap')
        g.box('head', (x, -1.6, z1), (x + 0.12, -1.0, 4.32), 'strap')
    g.box('head', (-4.32, -6.4, 4.2), (4.32, -5.8, 4.32), 'strap')
    g.box('head', (-4.32, -1.6, 4.2), (4.32, -1.0, 4.32), 'strap')


def build_motorcycle_helmet(g):
    d = 1.1
    g.box('head', (-4 - d, -9.2, -3.6), (4 + d, 0.8, 4 + d), 'shell')                # behind the face
    g.box('head', (-3.9, -9.8, -3.6), (3.9, -9.2, 4.2), 'shell')
    eye_open_front(g, -4 - d, -3.6, -9.2, 0.8, 4 + d, 'shell')                     # the front, its eye port open
    g.box('head', (-2.4, -1.4, -4 - d - 0.3), (2.4, 0.6, -4 - d), 'shell')            # chin bar bulge
    for x in (-1.6, -0.6, 0.4, 1.4):
        g.box('head', (x, -0.9, -4 - d - 0.35), (x + 0.5, -0.4, -4 - d - 0.25), 'vent')
    # the visor flipped up over the brow, with its pivots at the sides
    g.box('head', (-4.0, -6.6, -4 - d - 0.5), (4.0, EYES_HIGH - 0.75, -4 - d - 0.15), 'visor')
    for s in (-1, 1):
        x = s * (HX + d) - (0.3 if s > 0 else 0)
        g.box('head', (x, -4.9, -2.6), (x + 0.3, -3.6, -1.3), 'pivot')
    g.box('head', (-0.5, -9.85, -3.6), (0.5, -9.2, 4.2), 'stripe')


def build_police_cap(g):
    g.box('head', (-4.45, -6.4, -4.45), (4.45, EYES_HIGH - 0.25, 4.45), 'cloth')     # band
    # the wide flat top, its crown clear of the skin's hat layer (0.25 over the head), or hair shows through it
    g.box('head', (-5.3, -8.4, -5.6), (5.3, -6.4, 5.0), 'cloth')
    g.box('head', (-4.9, -8.7, -5.2), (4.9, -8.4, 4.6), 'cloth')
    g.box('head', (-3.8, EYES_HIGH - 0.55, -7.0), (3.8, EYES_HIGH - 0.2, -4.45), 'visor')
    g.box('head', (-3.9, -5.2, -4.62), (3.9, -4.95, -4.45), 'cord')                 # gold chin cord
    for s in (-1, 1):
        g.box('head', (s * 3.9 - 0.2, -5.3, -4.7), (s * 3.9 + 0.2, -4.85, -4.5), 'button')
    g.box('head', (-0.9, -7.8, -5.7), (0.9, -5.8, -5.55), 'badge')
    g.box('head', (-4.5, -5.0, -4.5), (4.5, EYES_HIGH - 0.25, 4.5), 'checker')      # chequered band


def build_fire_helmet(g):
    d = 1.0
    dome(g, -10.0, -5.4, d, 'shell', ((0, 0), (0.6, 0.5), (1.5, 0.5)))
    for x in (-2.6, -0.3, 2.0):                                                     # moulded ribs
        g.box('head', (x, -10.1, -2.4), (x + 0.6, -5.4, 4 + d + 0.1), 'shell')
    ring(g, -5.6, -5.15, d, 'shell', t=0.6)
    g.box('head', (-3.6, -5.6, -6.6), (3.6, -5.15, -4 - d), 'shell')                 # short front brim
    g.box('head', (-5.4, -5.4, 4 + d), (5.4, -4.9, 4 + d + 3.4), 'shell')            # long back brim (the tail)
    g.box('head', (-4.6, -4.9, 4 + d + 2.0), (4.6, -4.4, 4 + d + 3.6), 'shell')
    g.box('head', (-2.4, -9.6, -4 - d - 0.6), (2.4, -5.6, -4 - d - 0.2), 'front')   # leather front shield
    g.box('head', (-1.2, -8.8, -4 - d - 0.75), (1.2, -6.6, -4 - d - 0.6), 'badge')
    g.box('head', (-0.9, -10.6, -4 - d - 0.5), (0.9, -9.6, -4 - d - 0.2), 'eagle')  # the brass eagle holder
    for s in (-1, 1):                                                               # reflective tape bands
        g.box('head', (s * (HX + d) - (0.08 if s > 0 else 0), -7.2, -2.0), (s * (HX + d) + (0.08 if s < 0 else 0), -6.6, 2.4), 'tape')


def build_riot_helmet(g):
    d = 1.0
    dome(g, -9.6, -4.4, d, 'shell', ((0, 0), (0.6, 0.45), (1.4, 0.45)))
    g.box('head', (-4 - d, -4.4, -1.0), (4 + d, -1.2, 4 + d), 'shell')               # sides and back down to the neck
    g.box('head', (-4.6, -1.2, 2.4), (4.6, 1.6, 4 + d + 0.6), 'guard')              # padded neck guard
    # the visor down: a frame round a clear shield (left undrawn, so the face shows through it)
    zf = -4 - d - 0.8
    g.box('head', (-4.8, -6.0, zf), (4.8, -5.3, -4 - d), 'frame')
    for s in (-1, 1):
        x0 = s * 4.8 - (0.5 if s > 0 else 0)
        g.box('head', (x0, -5.3, zf), (x0 + 0.5, 0.3, -1.0), 'frame')
    g.box('head', (-4.8, 0.3, zf), (4.8, 0.6, zf + 0.5), 'frame')
    for s in (-1, 1):
        g.box('head', (s * 5.2 - 0.4, -5.6, -2.0), (s * 5.2 + 0.4, -4.2, -0.6), 'pivot')
    g.box('head', (-1.6, -8.2, -4 - d - 0.08), (1.6, -7.2, -4 - d), 'tape')


def build_gas_mask(g):
    z0, z1 = -4.9, -4.3
    g.box('head', (-3.9, -6.6, z0), (3.9, EYES_HIGH - 0.35, z1), 'rubber')
    g.box('head', (-3.9, EYES_HIGH - 0.35, z0), (-3.4, EYES_LOW + 0.35, z1), 'rubber')
    g.box('head', (-0.6, EYES_HIGH - 0.35, z0), (0.6, EYES_LOW + 0.35, z1), 'rubber')
    g.box('head', (3.4, EYES_HIGH - 0.35, z0), (3.9, EYES_LOW + 0.35, z1), 'rubber')
    g.box('head', (-3.9, EYES_LOW + 0.35, z0), (3.9, 0.5, z1), 'rubber')
    for s in (-1, 1):                                                               # lens rims round the open eyes
        x0, x1 = (-3.5, -0.5) if s < 0 else (0.5, 3.5)
        g.box('head', (x0 - 0.1, EYES_HIGH - 0.45, z0 - 0.3), (x1 + 0.1, EYES_HIGH - 0.2, z0), 'rim')
        g.box('head', (x0 - 0.1, EYES_LOW + 0.2, z0 - 0.3), (x1 + 0.1, EYES_LOW + 0.45, z0), 'rim')
        g.box('head', (x0 - 0.1, EYES_HIGH - 0.2, z0 - 0.3), (x0 + 0.15, EYES_LOW + 0.2, z0), 'rim')
        g.box('head', (x1 - 0.15, EYES_HIGH - 0.2, z0 - 0.3), (x1 + 0.1, EYES_LOW + 0.2, z0), 'rim')
    g.box('head', (-1.3, -2.2, z0 - 0.7), (1.3, 0.2, z0), 'rubber')                  # the voicemitter snout
    g.box('head', (-1.1, -1.9, z0 - 3.0), (1.1, 0.0, z0 - 0.7), 'canister')        # filter canister
    g.box('head', (-1.25, -2.05, z0 - 3.2), (1.25, 0.15, z0 - 2.85), 'rim')
    for s in (-1, 1):                                                               # the head harness
        x = -4.3 if s < 0 else 4.18
        for y0, y1 in ((-6.2, -5.7), (-2.0, -1.5)):
            g.box('head', (x, y0, z1), (x + 0.12, y1, 2.4), 'strap')
    g.box('head', (-2.6, -7.6, 2.0), (2.6, -1.4, 4.32), 'strap')                   # the spider pad at the back


def build_welding_goggles(g):
    z = -4.4
    for s in (-1, 1):
        x0, x1 = (-3.6, -0.6) if s < 0 else (0.6, 3.6)
        g.box('head', (x0, EYES_HIGH - 0.6, z - 0.9), (x1, EYES_LOW + 0.6, z), 'cup')
        g.box('head', (x0 + 0.3, EYES_HIGH - 0.3, z - 1.0), (x1 - 0.3, EYES_LOW + 0.3, z - 0.9), 'lens')
        g.box('head', (x0 + 0.1, EYES_HIGH - 0.5, z - 1.1), (x1 - 0.1, EYES_HIGH - 0.3, z - 0.9), 'rim')
    g.box('head', (-0.6, -4.0, z - 0.6), (0.6, -3.4, z - 0.2), 'cup')                 # bridge chain
    for s in (-1, 1):
        x = -4.32 if s < 0 else 4.2
        g.box('head', (x, -4.4, z), (x + 0.12, -3.6, 4.32), 'strap')
    g.box('head', (-4.32, -4.4, 4.2), (4.32, -3.6, 4.32), 'strap')


def glasses(g, lens, frame, top=EYES_HIGH - 0.3, bot=EYES_LOW + 0.2, teardrop=False, thin=False):
    z = -4.42
    for s in (-1, 1):
        x0, x1 = (-3.5, -0.5) if s < 0 else (0.5, 3.5)
        if lens is not None:
            g.box('head', (x0, top, z - 0.15), (x1, bot, z - 0.02), lens)
            if teardrop:                                                            # aviators drop at the outer edge
                xo0, xo1 = (x0, x0 + 1.4) if s < 0 else (x1 - 1.4, x1)
                g.box('head', (xo0, bot, z - 0.15), (xo1, bot + 0.6, z - 0.02), lens)
        t = 0.12 if thin else 0.2
        g.box('head', (x0 - 0.1, top - t, z - 0.2), (x1 + 0.1, top, z), frame)
        if thin or lens is None:
            g.box('head', (x0 - 0.1, bot, z - 0.2), (x1 + 0.1, bot + t, z), frame)
            g.box('head', (x0 - 0.1, top, z - 0.2), (x0 + t - 0.1, bot, z), frame)
            g.box('head', (x1 - t + 0.1, top, z - 0.2), (x1 + 0.1, bot, z), frame)
        xs = -4.45 if s < 0 else 4.3
        g.box('head', (xs, top - 0.1, z), (xs + 0.15, top + 0.2, 0.6), frame)
    g.box('head', (-0.5, top + 0.1, z - 0.2), (0.5, top + 0.35, z - 0.05), frame)
    if teardrop:
        g.box('head', (-0.5, top - 0.35, z - 0.2), (0.5, top - 0.15, z - 0.05), frame)   # the double bridge


def build_aviator_sunglasses(g):
    glasses(g, 'lens', 'gold', teardrop=True, thin=True)


def build_reading_glasses(g):
    # clear lenses: only the frames are drawn, so the eyes show
    glasses(g, None, 'frame', top=EYES_HIGH - 0.1, bot=EYES_LOW + 0.05, thin=True)


def build_balaclava(g):
    d = 0.35
    g.box('head', (-4 - d, -8 - d, -4 + 0.4), (4 + d, 0.6, 4 + d), 'knit')           # top, sides and back
    eye_open_front(g, -4 - d, -4 + 0.4, -8 - d, 0.6, 4 + d, 'knit', port=(3.5, EYES_HIGH - 0.05, EYES_LOW + 0.05))
    g.box('head', (-3.7, EYES_HIGH - 0.15, -4 - d - 0.08), (3.7, EYES_HIGH - 0.05, -4 - d), 'rib')
    g.box('head', (-3.7, EYES_LOW + 0.05, -4 - d - 0.08), (3.7, EYES_LOW + 0.15, -4 - d), 'rib')
    g.box('head', (-4 - d - 0.2, 0.0, -4 - d - 0.2), (4 + d + 0.2, 1.0, 4 + d + 0.2), 'rib')   # rolled neck


def build_surgical_mask(g):
    z = -4.3
    g.box('head', (-3.3, EYES_LOW + 0.2, z - 0.25), (3.3, -0.3, z), 'mask')
    for y in (-2.2, -1.5, -0.9):                                                    # pleats
        g.box('head', (-3.3, y, z - 0.32), (3.3, y + 0.18, z - 0.25), 'pleat')
    g.box('head', (-1.4, EYES_LOW + 0.2, z - 0.32), (1.4, EYES_LOW + 0.4, z - 0.25), 'wire')
    for s in (-1, 1):                                                               # ear loops
        x = -4.25 if s < 0 else 4.15
        g.box('head', (x, -2.6, z), (x + 0.1, -2.4, 0.4), 'loop')
        g.box('head', (x, -1.0, z), (x + 0.1, -0.8, 0.4), 'loop')
        g.box('head', (x, -2.6, 0.2), (x + 0.1, -0.8, 0.4), 'loop')


def build_cranial_helmet(g):
    d = 0.5
    g.box('head', (-4 - d, -8 - d, -4 - d), (4 + d, -5.0, 4 + d), 'cloth')           # the cloth cranial
    g.box('head', (-3.2, -9.3, -2.2), (3.2, -8.5, 4.2), 'shell')                     # hard shells over it
    g.box('head', (-4.7, -8.2, 0.6), (4.7, -5.4, 4.9), 'shell')
    for s in (-1, 1):                                                               # big ear defenders
        x0, x1 = (-4 - d - 1.2, -4 - d) if s < 0 else (4 + d, 4 + d + 1.2)
        g.box('head', (x0, -5.6, -1.8), (x1, -1.4, 1.8), 'cup')
        g.box('head', (x0 + (0.0 if s < 0 else 0.2), -5.2, -1.4), (x1 - (0.2 if s < 0 else 0.0), -1.8, 1.4), 'cup2')
        g.box('head', (x0 + (0.2 if s < 0 else 0), -7.6, -0.4), (x1 - (0 if s < 0 else 0.2), -5.6, 0.4), 'strap')
    g.box('head', (-1.6, -1.2, -1.2), (1.6, -0.9, -0.6), 'strap')                     # chin strap
    for s in (-1, 1):                                                               # goggles pushed up on the cranial
        x0, x1 = (-3.4, -0.5) if s < 0 else (0.5, 3.4)
        g.box('head', (x0, -8.3, -4 - d - 0.8), (x1, -6.6, -4 - d), 'gogframe')
        g.box('head', (x0 + 0.3, -8.0, -4 - d - 0.9), (x1 - 0.3, -6.9, -4 - d - 0.8), 'goglens')
    g.box('head', (-4 - d - 0.05, -7.6, -4 - d), (4 + d + 0.05, -7.2, 4 + d), 'strap')
    g.box('head', (-1.8, -7.0, 4 + d), (1.8, -5.6, 4 + d + 0.1), 'tape')             # reflective tape on the back


def build_pilot_helmet(g):
    d = 1.1
    dome(g, -10.0, EYES_HIGH - 0.4, d, 'shell', ((0, 0), (0.6, 0.5), (1.5, 0.5)))    # its front edge over the brow
    g.box('head', (-4 - d, EYES_HIGH - 0.4, -1.4), (4 + d, 1.0, 4 + d), 'shell')       # down round the ears and neck
    g.box('head', (-4.6, -7.6, -4 - d - 0.7), (4.6, EYES_HIGH - 0.4, -4 - d), 'housing')   # visor housing, visor up
    g.box('head', (-4.3, -7.3, -4 - d - 0.8), (4.3, -6.4, -4 - d - 0.7), 'knob')
    g.box('head', (-0.4, -6.4, -4 - d - 1.0), (0.4, -5.8, -4 - d - 0.7), 'knob')
    g.box('head', (-0.6, -10.05, -3.0), (0.6, -6.0, 4 + d + 0.05), 'stripe')
    # the oxygen mask unclipped, hanging off the right bayonet with its hose
    g.box('head', (-4 - d - 0.4, -2.6, -4.4), (-4 - d + 0.4, -1.8, -3.0), 'bayonet')
    g.box('head', (-4 - d - 1.0, -2.2, -6.6), (-4 - d + 0.6, 0.6, -4.4), 'mask')
    g.box('head', (-4 - d - 0.7, 0.6, -6.0), (-4 - d + 0.3, 2.6, -5.0), 'hose')
    for s in (-1, 1):
        g.box('head', (s * (HX + d) - (0.3 if s > 0 else 0), -4.4, -1.2), (s * (HX + d) + (0.3 if s < 0 else 0), -2.6, 1.2), 'ear')


def plastic(base, gloss=0.18):
    def fn(f):
        c = P.plain(f, base, 0.06)
        top = np.clip(f.n[1], 0, 1)
        c = c * (1 + gloss * 0.6 * top) + gloss * 0.15 * np.clip(1 - f.edge / 0.4, 0, 1)[..., None]
        return toon(f, grade(c, 0.35))
    return fn


def mesh(base):
    def fn(f):
        c = P.plain(f, base, 0.06)
        u, v = f.uv[..., 0], f.uv[..., 1]
        hole = ((np.floor(u / TX) % 2) == 0) & ((np.floor(v / TX) % 2) == 0)
        c = np.where(hole[..., None], c * 0.35, c)
        return toon(f, grade(c))
    return fn


def checker(a, b):
    def fn(f):
        h = f.P[..., 0] if abs(f.n[0]) < 0.5 else f.P[..., 2]
        cell = ((np.floor(h / 0.7) + np.floor(f.P[..., 1] / 0.7)) % 2) == 0
        c = np.where(cell[..., None], P.col(a), P.col(b)) * (1 + (P.white(f.shape, f.rng) - 0.5) * 0.08)[..., None]
        return toon(f, grade(c, 0.35))
    return fn


def glow_tape(base='#a9a68e'):
    def fn(f):
        c = P.plain(f, base, 0.05)
        return toon(f, grade(c, 0.2), ink=False)
    return fn


HEADWEAR = {
    'trucker_cap': (build_trucker_cap, dict(foam=cloth('#8d8778', weave=0.03), mesh=mesh('#2d3a2c'), brim=cloth('#2d3a2c'),
                                            patch=flat('#6e3a24'), shadow=flat('#0d0c0b', ink=False), snap=plastic('#1d1d1d')), 0.4),
    'cowboy_hat': (build_cowboy_hat, dict(felt=cloth('#6b5236', weave=0.02, stitch=None), crease=cloth('#5e472e', weave=0.02, stitch=None),
                                          band=leather('#2e2018'), concho=metal('steel')), 0.4),
    'bucket_hat': (build_bucket_hat, dict(cloth=cloth('#5a5f45'), band=cloth('#4d513a'), brim=cloth('#55593f'), eyelet=metal('brass')), 0.35),
    'hard_hat': (build_hard_hat, dict(shell=plastic('#a8852a'), sticker=flat('#6a2a24'), sticker2=flat('#3d5a7a'),
                                      harness=plastic('#2a2a28')), 0.5),
    'bike_helmet': (build_bike_helmet, dict(shell=plastic('#3d6a72'), vent=flat('#0e1012', ink=False), visor=plastic('#1e1f22'),
                                            trim=plastic('#1e1f22'), strap=flat('#1d1d1f'), buckle=plastic('#111111')), 0.4),
    'football_helmet': (build_football_helmet, dict(shell=plastic('#6e2a2a', 0.25), ear=flat('#0e0d0d', ink=False),
                                                    stripe=plastic('#b8b2a0'), cage=plastic('#9a9890'), chin=plastic('#2a2a2a')), 0.45),
    'hockey_mask': (build_hockey_mask, dict(mask=plastic('#c2bba8', 0.1), chevron=flat('#7a2a24'), strap=flat('#1d1c1a')), 0.55),
    'motorcycle_helmet': (build_motorcycle_helmet, dict(shell=plastic('#1b1c1e', 0.35), vent=flat('#0a0a0b', ink=False),
                                                        visor=plastic('#141619', 0.5), pivot=plastic('#3a3b3d'), stripe=plastic('#7a2a24')), 0.4),
    'police_cap': (build_police_cap, dict(cloth=cloth('#1f2737', weave=0.05), visor=plastic('#0f0f10', 0.5), cord=metal('brass'),
                                          button=metal('brass'), badge=metal('brass'), checker=checker('#1d2433', '#a9a69a')), 0.3),
    'fire_helmet': (build_fire_helmet, dict(shell=plastic('#1d1c1b', 0.3), front=leather('#2a2622'), badge=flat('#7a2a22'),
                                            eagle=metal('brass'), tape=glow_tape()), 0.55),
    'riot_helmet': (build_riot_helmet, dict(shell=plastic('#1c1d1f', 0.3), guard=cloth('#1d1e20', stitch='#4a4a48'),
                                            frame=plastic('#111213'), pivot=plastic('#3a3b3d'),
                                            tape=flat('#a9a69a')), 0.4),
    'gas_mask': (build_gas_mask, dict(rubber=rubber('#2b2d2a'), rim=metal('steel'), canister=plastic('#4a4f3e'),
                                      strap=rubber('#1e1f1d')), 0.45),
    'welding_goggles': (build_welding_goggles, dict(cup=plastic('#2a2a28'), lens=lens, rim=metal('steel'), strap=cloth('#3a3a36')), 0.5),
    'aviator_sunglasses': (build_aviator_sunglasses, dict(lens=lens, gold=metal('brass')), 0.1),
    'reading_glasses': (build_reading_glasses, dict(frame=plastic('#3b2a1e')), 0.1),
    'balaclava': (build_balaclava, dict(knit=knit('#1e1f21'), rib=rib('#1b1c1e')), 0.35),
    'surgical_mask_worn': (build_surgical_mask, dict(mask=cloth('#7d9aa6', weave=0.03, stitch=None), pleat=cloth('#6f8b97', stitch=None),
                                                     wire=flat('#c8c4bb'), loop=flat('#d0ccc2')), 0.6),
    'cranial_helmet': (build_cranial_helmet, dict(cloth=cloth('#3f5684'), shell=plastic('#b4b1a8'), cup=plastic('#b4b1a8'),
                                                  cup2=plastic('#8d8a82'), strap=flat('#1f2023'), gogframe=rubber('#1d1e20'),
                                                  goglens=lens, tape=glow_tape()), 0.5),
    'pilot_helmet': (build_pilot_helmet, dict(shell=plastic('#5c6064', 0.3),
                                              housing=plastic('#4a4e52'), knob=plastic('#1e1f21'), stripe=plastic('#7a6a2a'),
                                              bayonet=metal('steel'), mask=rubber('#2e3034'), hose=rubber('#26282b'),
                                              ear=plastic('#4a4e52')), 0.35),
}


# ------------------------------------------------------------------ batch 3: tops and outerwear


def sleeves(g, d, mat, end=8.0, cuff=None, cuff_d=0.15, short=None):
    """Both sleeves: full to the wrist (end), with a cuff band, or short ones ending at `short`."""
    def arm(p, s):
        if short is not None:
            g.shell(p, -2.0, short, d, mat)
            if cuff:
                g.shell(p, short - 0.6, short + 0.3, d + cuff_d, cuff)
            return
        g.shell(p, -2.0, end, d, mat)
        if cuff:
            g.shell(p, end - 0.4, end + 0.8, d + cuff_d, cuff)
    g.both(ARMS, arm)


def crew(g, d, mat):
    """A round neck: a band round the opening on top of the torso."""
    for s in (-1, 1):
        g.box('body', (s * 1.6, -0.3, -2 - d + 0.1), (s * 2.5, 0.25, 1.4), mat)
    g.box('body', (-2.5, -0.3, -2 - d - 0.05), (2.5, 0.25, -1.6), mat)
    g.box('body', (-2.5, -0.3, 0.6), (2.5, 0.25, 1.4), mat)


def skirt(g, y1, d, mat, flare=0.0, y0=-0.2):
    """A skirt or coat tail on the legs, so it swings with them; flare widens it toward the hem."""
    def leg(p, s):
        g.shell(p, y0, (y0 + y1) / 2, d, mat)
        g.shell(p, (y0 + y1) / 2, y1, d + flare, mat)
    g.both(LEGS, leg)


def lapels(g, d, mat, y1=5.0, width=1.8):
    """Jacket lapels: the fronts folded back in a V down to y1."""
    z = -2 - d
    for s in (-1, 1):
        g.box('body', (s * 0.4, -0.3, z - 0.3), (s * (0.4 + width), y1 * 0.45, z), mat)
        g.box('body', (s * 0.4, y1 * 0.45, z - 0.3), (s * (0.4 + width * 0.55), y1, z), mat)
    g.box('body', (-3.2, -0.8, 0.5), (3.2, 0.2, 2 + d + 0.3), mat)                 # collar round the back


def build_band_tshirt(g):
    build_t_shirt(g)
    g.front('body', SHIRT, -2.6, 2.0, 2.6, 6.6, 'print', t=0.05)                    # the band's print on the chest
    g.back('body', SHIRT, -2.4, 2.0, 2.4, 6.0, 'print2', t=0.05)                    # tour dates on the back


def build_tank_top(g):
    g.shell('body', 1.4, 12, SHIRT, 'cloth')
    g.shell('body', 11.3, 12.25, SHIRT + 0.1, 'hem')
    for s in (-1, 1):                                                               # the straps over the shoulders
        x0, x1 = (-3.4, -1.6) if s < 0 else (1.6, 3.4)
        g.box('body', (x0, -0.3, -2 - SHIRT), (x1, 1.5, 2 + SHIRT), 'cloth')
    g.front('body', SHIRT, -1.6, 1.4, 1.6, 2.2, 'hem', t=0.08)                      # scooped neck edge


def build_dress_shirt(g):
    g.shell('body', 0, 12, SHIRT, 'cloth')
    g.shell('body', 11.4, 12.5, SHIRT + 0.1, 'cloth')
    g.front('body', SHIRT, -0.5, 0, 0.5, 12.5, 'placket')
    buttons(g, 'body', SHIRT + 0.18, 0, (1.6, 3.8, 6.0, 8.2, 10.4))
    collar_flaps(g, SHIRT, 'collar')
    g.front('body', SHIRT, -3.3, 2.4, -1.2, 5.0, 'cloth', t=0.1)                    # one chest pocket
    sleeves(g, SHIRT + 0.05, 'cloth', end=8.0, cuff='cuff', cuff_d=0.2)


def build_polo_shirt(g):
    g.shell('body', 0, 12, SHIRT, 'cloth')
    g.shell('body', 11.4, 12.4, SHIRT + 0.1, 'cloth')
    collar_flaps(g, SHIRT, 'collar')
    g.front('body', SHIRT, -0.55, 0, 0.55, 3.4, 'placket', t=0.12)
    buttons(g, 'body', SHIRT + 0.12, 0, (1.0, 2.4))
    g.front('body', SHIRT, 1.6, 2.4, 2.6, 3.4, 'logo', t=0.06)
    sleeves(g, SHIRT + 0.05, 'cloth', short=3.0, cuff='collar', cuff_d=0.12)


def build_hawaiian_shirt(g):
    g.shell('body', 0, 11.6, SHIRT + 0.05, 'cloth')
    g.shell('body', 11.0, 11.9, SHIRT + 0.15, 'cloth')                              # boxy hem worn out
    g.front('body', SHIRT + 0.05, -0.45, 0, 0.45, 11.9, 'cloth', t=0.1)
    buttons(g, 'body', SHIRT + 0.15, 0, (3.0, 5.4, 7.8, 10.2), 'shellbutton')
    collar_flaps(g, SHIRT + 0.05, 'cloth')
    g.front('body', SHIRT + 0.05, -3.3, 2.6, -1.3, 5.0, 'cloth', t=0.1)
    sleeves(g, SHIRT + 0.1, 'cloth', short=3.4, cuff='cloth', cuff_d=0.12)


def build_sweater(g):
    d = JACKET
    g.shell('body', 0, 10.8, d, 'knit')
    g.shell('body', 10.5, 12.2, d + 0.12, 'rib')
    crew(g, d, 'rib')
    g.front('body', d, -3.8, 2.6, 3.8, 3.4, 'band', t=0.08)                         # a cable band across the chest
    g.front('body', d, -0.8, 3.4, 0.8, 10.4, 'cable', t=0.12)                       # and a cable down the front
    sleeves(g, d, 'knit', end=7.6, cuff='rib', cuff_d=0.12)


def build_sweatshirt(g):
    d = JACKET
    g.shell('body', 0, 10.8, d, 'cloth')
    g.shell('body', 10.5, 12.2, d + 0.12, 'rib')
    crew(g, d, 'rib')
    g.front('body', d, -2.8, 2.2, 2.8, 5.6, 'letters', t=0.08)                      # the school's felt letters
    g.front('body', d, -0.6, 0.2, 0.6, 1.2, 'rib', t=0.05)                          # the V insert under the neck
    sleeves(g, d, 'cloth', end=7.6, cuff='rib', cuff_d=0.12)
    g.both(ARMS, lambda p, s: g.side(p, d, s, -0.3, -1.0, 0.3, 7.4, 'stripe', t=0.05))


def build_turtleneck(g):
    g.shell('body', 0, 12, SHIRT, 'knit')
    g.shell('body', 11.2, 12.3, SHIRT + 0.1, 'rib')
    g.box('body', (-2.8, -1.8, -2.4), (2.8, 0.3, 2.0), 'rib')                       # the rolled neck up to the jaw
    sleeves(g, SHIRT + 0.05, 'knit', end=8.0, cuff='rib', cuff_d=0.12)


def build_scrubs_top(g):
    g.shell('body', 0, 12, SHIRT, 'cloth')
    g.shell('body', 11.3, 12.3, SHIRT + 0.1, 'cloth')
    for s in (-1, 1):                                                               # V neck
        g.front('body', SHIRT, s * 0.2, 0, s * 1.6, 0.8, 'trim', t=0.08)
        g.front('body', SHIRT, s * 0.2, 0.8, s * 0.9, 2.4, 'trim', t=0.08)
    g.front('body', SHIRT, -3.4, 2.4, -1.2, 5.0, 'cloth', t=0.12)                   # breast pocket with pens
    g.front('body', SHIRT + 0.12, -3.0, 1.8, -2.75, 3.2, 'pen', t=0.12)
    g.front('body', SHIRT + 0.12, -2.4, 1.6, -2.15, 3.0, 'pen2', t=0.12)
    g.front('body', SHIRT, 0.8, 7.8, 3.4, 10.6, 'cloth', t=0.12)
    sleeves(g, SHIRT + 0.05, 'cloth', short=3.2)


def build_dress(g):
    g.shell('body', 0, 8.6, SHIRT, 'cloth')
    g.shell('body', 8.0, 9.0, SHIRT + 0.12, 'sash')                                 # the waist tie
    g.shell('body', 9.0, 12, SHIRT + 0.3, 'cloth')
    g.front('body', SHIRT, -1.4, 0, 1.4, 1.2, 'trim', t=0.08)
    sleeves(g, SHIRT + 0.05, 'cloth', short=2.6, cuff='trim', cuff_d=0.12)
    skirt(g, 6.5, SHIRT + 0.35, 'cloth', flare=0.35)
    g.both(LEGS, lambda p, s: g.shell(p, 6.1, 6.7, SHIRT + 0.75, 'trim'))


def build_wedding_dress(g):
    g.shell('body', 0, 8.6, SHIRT, 'satin')
    g.shell('body', 0, 4.6, SHIRT + 0.1, 'lace')                                    # lace bodice over it
    g.shell('body', 8.0, 9.0, SHIRT + 0.12, 'sash')
    g.shell('body', 9.0, 12, SHIRT + 0.45, 'satin')
    for x in (-1.8, 0.0, 1.8):                                                      # beading
        g.front('body', SHIRT + 0.1, x - 0.3, 5.2, x + 0.3, 7.6, 'bead', t=0.08)
    sleeves(g, SHIRT + 0.05, 'lace', end=8.0, cuff='satin', cuff_d=0.1)
    skirt(g, 11.6, SHIRT + 0.6, 'satin', flare=0.9)                                 # down to the shoes
    g.both(LEGS, lambda p, s: g.shell(p, 11.0, 11.8, SHIRT + 1.6, 'lace'))
    g.back('body', SHIRT + 0.45, -2.6, 9.0, 2.6, 12, 'satin', t=0.6)                 # a bustle bow at the back
    g.box('body', (-2.0, 8.6, 2 + SHIRT + 0.4), (2.0, 9.6, 2 + SHIRT + 1.2), 'sash')


def build_leather_jacket(g):
    d = JACKET
    g.shell('body', 0, 10.6, d, 'leather')
    g.shell('body', 10.3, 11.6, d + 0.15, 'leather')                                # waist belt with its buckle
    g.front('body', d + 0.15, 1.6, 10.4, 3.2, 11.5, 'buckle', t=0.15)
    lapels(g, d, 'leather', y1=4.6, width=2.0)
    for s in (-1, 1):                                                               # snap-down lapel points
        g.front('body', d + 0.3, s * 2.0 - 0.2, 1.6, s * 2.0 + 0.2, 2.0, 'snap', t=0.1)
    g.front('body', d, 1.0, 0.2, 1.3, 10.6, 'zip', t=0.15)                          # the off-centre zip
    g.front('body', d, -3.6, 6.4, -1.4, 6.8, 'zip', t=0.12)                         # zip pockets
    g.front('body', d, 1.6, 6.4, 3.6, 6.8, 'zip', t=0.12)
    g.front('body', d, -3.4, 2.8, -1.8, 3.1, 'zip', t=0.12)
    sleeves(g, d, 'leather', end=8.0, cuff='leather', cuff_d=0.1)
    g.both(ARMS, lambda p, s: g.side(p, d + 0.1, s, -0.2, 6.4, 0.2, 8.6, 'zip', t=0.1))   # cuff zips
    for s in (-1, 1):                                                               # epaulettes
        x0, x1 = (-4 - d, -1.6) if s < 0 else (1.6, 4 + d)
        g.box('body', (x0, -0.15, -0.6), (x1, 0.05, 0.6), 'leather')


def build_bomber_jacket(g):
    d = JACKET + 0.1
    g.shell('body', 0, 10.6, d, 'nylon')
    g.shell('body', 10.3, 12.0, d + 0.15, 'rib')
    g.box('body', (-3.4, -1.2, -2 - d - 0.3), (3.4, 0.4, 2 + d + 0.3), 'rib')       # knit collar
    g.front('body', d, -0.3, 0.2, 0.3, 10.6, 'zip', t=0.15)
    for s in (-1, 1):                                                               # slash pockets
        g.front('body', d, s * 1.4, 6.6, s * 3.4, 9.6, 'nylon', t=0.15)
    g.both(ARMS, lambda p, s: (g.shell(p, -2.0, 7.6, d, 'nylon'), g.shell(p, 7.4, 8.9, d + 0.12, 'rib')))
    g.side('left_arm', d, 1, -1.3, 0.6, 1.3, 3.4, 'nylon', t=0.18)                   # the pen pocket on the sleeve
    g.side('left_arm', d + 0.18, 1, -1.0, 0.8, -0.5, 2.8, 'zip', t=0.08)
    g.front('body', d, -3.5, 2.4, -1.4, 3.6, 'patch', t=0.06)


def build_varsity_jacket(g):
    d = JACKET
    g.shell('body', 0, 10.6, d, 'wool')
    g.shell('body', 10.3, 12.0, d + 0.15, 'stripe_rib')
    g.box('body', (-3.4, -1.0, -2 - d - 0.3), (3.4, 0.4, 2 + d + 0.3), 'stripe_rib')
    g.front('body', d, -0.45, 0.2, 0.45, 10.6, 'wool', t=0.12)
    buttons(g, 'body', d + 0.12, 0, (1.4, 3.6, 5.8, 8.0, 10.0), 'snapw')
    g.front('body', d, -3.6, 2.0, -1.0, 5.4, 'letter', t=0.12)                      # the chenille letter
    for s in (-1, 1):
        g.front('body', d, s * 1.3, 6.6, s * 3.4, 9.2, 'leatherwelt', t=0.1)
    g.both(ARMS, lambda p, s: (g.shell(p, -2.0, 7.6, d, 'leather'), g.shell(p, 7.4, 8.9, d + 0.12, 'stripe_rib')))


def build_windbreaker(g):
    d = JACKET - 0.1
    g.shell('body', 0, 11.2, d, 'nylon')
    g.shell('body', 10.8, 12.0, d + 0.12, 'nylon')
    g.front('body', d, -4 - d, 3.0, 4 + d, 5.2, 'panel', t=0.06)                     # a colour band across the chest
    g.box('body', (-3.0, -1.4, -2 - d - 0.2), (3.0, 0.2, 2 + d + 0.2), 'nylon')    # stand collar, hood rolled in it
    g.front('body', d, -0.25, -1.4, 0.25, 11.4, 'zip', t=0.14)
    g.front('body', d + 0.14, -0.4, -1.3, 0.4, -0.6, 'pull', t=0.1)
    g.front('body', d, 1.6, 6.8, 3.6, 7.2, 'zip', t=0.08)
    g.front('body', d, -3.6, 6.8, -1.6, 7.2, 'zip', t=0.08)
    sleeves(g, d, 'nylon', end=8.0, cuff='nylon', cuff_d=0.15)
    g.both(ARMS, lambda p, s: g.side(p, d, s, -2.0 - d, 0.0, 2.0 + d, 1.6, 'panel', t=0.05))


def build_raincoat(g):
    d = JACKET + 0.15
    g.shell('body', 0, 12, d, 'pvc')
    skirt(g, 6.4, d + 0.1, 'pvc', flare=0.2)
    g.box('body', (-3.8, -2.0, 0.2), (3.8, 1.2, 2 + d + 1.0), 'pvc')               # the hood down
    g.front('body', d, -0.9, -0.2, 0.9, 12, 'pvc', t=0.2)                           # storm flap with press studs
    buttons(g, 'body', d + 0.2, 0, (1.4, 4.0, 6.6, 9.2, 11.4), 'snap')
    for s in (-1, 1):
        g.front('body', d, s * 1.4, 7.0, s * 3.8, 10.8, 'pvc', t=0.2)
        g.front('body', d + 0.2, s * 1.3, 6.8, s * 3.9, 7.6, 'pvc', t=0.12)
    sleeves(g, d, 'pvc', end=8.0, cuff='pvc', cuff_d=0.12)
    g.front('body', d, -4 - d, 9.4, 4 + d, 9.7, 'tape', t=0.04)                      # reflective band
    g.both(LEGS, lambda p, s: g.shell(p, 3.6, 3.9, d + 0.12, 'tape'))


def build_trench_coat(g):
    d = JACKET + 0.15
    g.shell('body', 0, 12, d, 'gab')
    skirt(g, 9.0, d + 0.1, 'gab', flare=0.3)
    lapels(g, d, 'gab', y1=5.4, width=2.4)
    g.front('body', d, -3.6, 0.3, -0.4, 4.6, 'gab', t=0.2)                          # the gun flap on the chest
    for x in (-1.2, 1.2):                                                           # double-breasted buttons
        buttons(g, 'body', d, x, (5.6, 7.4, 9.2), 'horn')
    g.shell('body', 8.8, 9.6, d + 0.25, 'gab')                                     # the belt
    g.front('body', d + 0.25, -0.8, 8.6, 0.8, 9.8, 'buckle', t=0.15)
    g.box('body', (-0.5, 9.6, -2 - d - 0.4), (0.0, 12.2, -2 - d - 0.25), 'gab')    # its loose end hanging
    for s in (-1, 1):                                                               # epaulettes with buttons
        x0, x1 = (-4 - d, -1.6) if s < 0 else (1.6, 4 + d)
        g.box('body', (x0, -0.15, -0.7), (x1, 0.08, 0.7), 'gab')
    g.back('body', d, -4 - d + 0.2, 0, 4 + d - 0.2, 3.6, 'gab', t=0.2)               # the storm yoke on the back
    def arm(p, s):
        g.shell(p, -2.0, 8.2, d, 'gab')
        g.shell(p, 6.8, 7.4, d + 0.15, 'gab')                                       # sleeve straps
        g.side(p, d + 0.15, s, -0.3, 6.8, 0.3, 7.4, 'buckle', t=0.1)
    g.both(ARMS, arm)


def build_suit_jacket(g):
    d = JACKET - 0.15
    g.shell('body', 0, 12, d, 'wool')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 1.4, d + 0.05, 'wool'))             # cut to cover the seat
    lapels(g, d, 'lapel', y1=6.0, width=1.9)
    buttons(g, 'body', d, 0.6, (7.0, 9.2), 'horn')
    g.front('body', d, -3.4, 2.6, -1.6, 3.0, 'wool', t=0.1)                          # breast welt with a pocket square
    g.front('body', d + 0.1, -3.0, 2.2, -2.2, 2.7, 'square', t=0.08)
    for s in (-1, 1):
        g.front('body', d, s * 1.4, 8.8, s * 3.6, 9.4, 'wool', t=0.18)               # flap pockets
    g.front('body', d, -0.3, 0.3, 0.3, 5.6, 'tie', t=0.2)                            # the tie showing in the V
    sleeves(g, d, 'wool', end=8.2)
    g.both(ARMS, lambda p, s: [g.front(p, d, (-1.2 if p == 'right_arm' else 0.4) + i * 0.4, 7.3, (-1.0 if p == 'right_arm' else 0.6) + i * 0.4, 7.6, 'horn', t=0.1) for i in range(3)])


def build_lab_coat(g):
    d = JACKET
    g.shell('body', 0, 12, d, 'cotton')
    skirt(g, 7.4, d + 0.1, 'cotton', flare=0.15)
    lapels(g, d, 'cotton', y1=4.4, width=1.8)
    buttons(g, 'body', d, 0, (5.2, 7.4, 9.6, 11.6), 'button')
    g.front('body', d, -3.6, 2.8, -1.4, 5.2, 'cotton', t=0.12)                       # breast pocket with pens and a badge
    g.front('body', d + 0.12, -3.2, 2.0, -2.95, 3.4, 'pen', t=0.12)
    g.front('body', d + 0.12, -2.6, 2.2, -2.35, 3.4, 'pen2', t=0.12)
    g.front('body', d + 0.12, 1.6, 2.6, 3.4, 4.4, 'badge', t=0.06)
    for s in (-1, 1):
        g.front('body', d, s * 1.4, 8.4, s * 3.8, 11.6, 'cotton', t=0.15)
    sleeves(g, d, 'cotton', end=8.0, cuff='cotton', cuff_d=0.12)


def build_hi_vis_vest(g):
    d = VEST_D
    g.shell('body', 0.8, 10.6, d, 'mesh')
    for s in (-1, 1):                                                               # over the shoulders
        x0, x1 = (-3.6, -1.6) if s < 0 else (1.6, 3.6)
        g.box('body', (x0, -0.3, -2 - d), (x1, 0.8, 2 + d), 'mesh')
        g.box('body', (x0 + 0.5, -0.4, -2 - d - 0.06), (x0 + 1.3, 10.6, -2 - d), 'tape')   # braces of tape
        g.box('body', (x0 + 0.5, -0.4, 2 + d), (x0 + 1.3, 10.6, 2 + d + 0.06), 'tape')
    g.shell('body', 4.4, 5.4, d + 0.06, 'tape')                                    # two hoops, chest and waist
    g.shell('body', 7.8, 8.8, d + 0.06, 'tape')
    g.front('body', d, -0.25, 0.8, 0.25, 10.6, 'zip', t=0.1)
    g.front('body', d, -3.6, 4.6, -1.4, 6.6, 'mesh', t=0.12)                         # ID window pocket


def satin(base):
    def fn(f):
        c = P.plain(f, base, 0.05)
        sheen = np.clip(np.sin(f.P[..., 1] * 1.3 + f.P[..., 0] * 0.4) * 0.5 + 0.5, 0, 1)
        c = c * (0.9 + 0.2 * sheen)[..., None]
        return toon(f, grade(c, 0.35), stitch=None)
    return fn


def lace(base):
    def fn(f):
        c = P.plain(f, base, 0.04)
        u, v = f.uv[..., 0], f.uv[..., 1]
        holes = (np.abs(np.hypot((u % 0.8) - 0.4, (v % 0.8) - 0.4) - 0.22) < TX * 0.7)
        c = np.where(holes[..., None], c * 0.7, c)
        return toon(f, grade(c, 0.3))
    return fn


def flowers(base, petal='#a8574a', leaf='#3f5a3a'):
    """A Hawaiian print: hibiscus blobs and leaves over the base."""
    def pat(f, c):
        n1 = f.noise(1.6, 301, 2)
        n2 = f.noise(1.1, 302, 2)
        c = np.where((n2 > 0.62)[..., None], P.col(leaf) * 1.1, c)
        c = np.where((n1 > 0.66)[..., None], P.col(petal) * 1.15, c)
        dot = (n1 > 0.75) & (n1 < 0.78)
        return np.where(dot[..., None], P.col('#d8c6a0'), c)
    return cloth(base, stitch=None, pattern=pat)


def stripes(a, b, w=0.5):
    def fn(f):
        v = f.P[..., 1]
        band = (np.floor(v / w) % 2) == 0
        c = np.where(band[..., None], P.col(a), P.col(b)) * (1 + (P.white(f.shape, f.rng) - 0.5) * 0.08)[..., None]
        return toon(f, grade(c))
    return fn


def printed(base, ink):
    """A screen-printed graphic: blocky shapes and a band of lettering, cracked with age."""
    def fn(f):
        c = P.plain(f, base, 0.06)
        u, v = f.uv[..., 0], f.uv[..., 1]
        H = max(v.max(), 1e-3)
        skull = (np.hypot(u - u.mean(), (v - H * 0.45) * 1.2) < H * 0.3)
        eyes = (np.hypot(np.abs(u - u.mean()) - H * 0.12, v - H * 0.42) < H * 0.07)
        letters = (v < H * 0.18) & ((np.floor(u / (2 * TX)) % 3) != 0)
        mark = (skull & ~eyes) | letters
        crack = P.white(f.shape, f.rng) > 0.8
        c = np.where((mark & ~crack)[..., None], P.col(ink), c)
        return toon(f, grade(c), ink=False)
    return fn


TOPS = {
    'band_tshirt': (build_band_tshirt, dict(cloth=cloth('#24252a'), hem=cloth('#202126'), collar=rib('#202126'),
                                            print=printed('#24252a', '#b9a77e'), print2=printed('#24252a', '#8a8778')), 0.45),
    'tank_top': (build_tank_top, dict(cloth=rib('#a8a59a'), hem=rib('#9a978c')), 0.5),
    'dress_shirt': (build_dress_shirt, dict(cloth=cloth('#9aa7b4', weave=0.04), placket=cloth('#93a0ad', weave=0.04),
                                            collar=cloth('#93a0ad', weave=0.04), cuff=cloth('#93a0ad', weave=0.04),
                                            button=flat('#d8d4c8', ink=False)), 0.35),
    'polo_shirt': (build_polo_shirt, dict(cloth=cloth('#2f4a3c', weave=0.12), collar=rib('#2a4234'), placket=cloth('#2a4234'),
                                          button=flat('#d8d4c8', ink=False), logo=flat('#8a6a3a')), 0.35),
    'hawaiian_shirt': (build_hawaiian_shirt, dict(cloth=flowers('#7a2a26', petal='#c8b8a0', leaf='#4e1c1a'), shellbutton=flat('#d8ccb4', ink=False)), 0.35),
    'sweater': (build_sweater, dict(knit=knit('#6a5e48'), rib=rib('#5e533f'), band=knit('#4e4434'), cable=rib('#5e533f')), 0.35),
    'sweatshirt': (build_sweatshirt, dict(cloth=cloth('#5a5f66', weave=0.05), rib=rib('#50555b'), letters=cloth('#6e2a26'),
                                          stripe=flat('#6e2a26', ink=False)), 0.4),
    'turtleneck': (build_turtleneck, dict(knit=knit('#2a2b2e'), rib=rib('#262729')), 0.3),
    'scrubs_top': (build_scrubs_top, dict(cloth=cloth('#3f6f6a', weave=0.04), trim=cloth('#386360'), pen=plastic('#1d2d5a'),
                                          pen2=plastic('#7a2a24')), 0.45),
    'dress': (build_dress, dict(cloth=cloth('#5a3a4a', pattern=plaid('#5a3a4a', '#3a2532', 2.2), stitch=None),
                                sash=cloth('#3a2532'), trim=cloth('#3a2532')), 0.35),
    'wedding_dress': (build_wedding_dress, dict(satin=satin('#cfc8b8'), lace=lace('#d8d2c4'), sash=satin('#b9b09c'),
                                                bead=flat('#e8e4da', ink=False)), 0.55),
    'leather_jacket': (build_leather_jacket, dict(leather=leather('#1e1c1b', gloss=0.2), zip=metal('steel'), buckle=metal('steel'),
                                                  snap=metal('steel')), 0.45),
    'bomber_jacket': (build_bomber_jacket, dict(nylon=cloth('#5c6450', weave=0.02, stitch='#7a8068'), rib=rib('#3a3d32'),
                                                zip=metal('brass'), patch=flat('#6e5a2a')), 0.4),
    'varsity_jacket': (build_varsity_jacket, dict(wool=cloth('#3e2228', weave=0.02), stripe_rib=stripes('#3e2228', '#a89c80', 0.45),
                                                  snapw=metal('steel'), letter=cloth('#a89c80', stitch='#3e2228'),
                                                  leatherwelt=leather('#c0b8a4'), leather=leather('#c0b8a4')), 0.4),
    'windbreaker': (build_windbreaker, dict(nylon=cloth('#2d3e5a', weave=0.02), panel=cloth('#7a6a3a', weave=0.02),
                                            zip=metal('steel'), pull=flat('#1d1d1f')), 0.35),
    'raincoat': (build_raincoat, dict(pvc=plastic('#8a7a2a', 0.3), snap=metal('steel'), tape=glow_tape()), 0.45),
    'trench_coat': (build_trench_coat, dict(gab=cloth('#8c7a56', weave=0.06, stitch='#64543a'), horn=plastic('#2a2018'),
                                            buckle=leather('#3a2a1c')), 0.45),
    'suit_jacket': (build_suit_jacket, dict(wool=cloth('#2c2f36', weave=0.03, stitch=None), lapel=cloth('#272a30', stitch=None),
                                            horn=plastic('#1a1714'), square=cloth('#7a2a26'), tie=cloth('#5a2228', stitch=None)), 0.35),
    'lab_coat': (build_lab_coat, dict(cotton=cloth('#b6b3aa', weave=0.04), button=flat('#d8d4c8', ink=False), pen=plastic('#1d2d5a'),
                                      pen2=plastic('#2a2a2a'), badge=flat('#4a6a8a')), 0.5),
    'hi_vis_vest': (build_hi_vis_vest, dict(mesh=mesh('#a6b83a'), tape=glow_tape('#c4c6bc'), zip=metal('steel')), 0.55),
}


# ------------------------------------------------------------------ batch 4: legs, feet, hands and the neck
# Leg pixels: each leg is (-2,0,-2)..(2,12,2) from its hip pivot, the front at -z; trousers reach y 11, shoes sit 10 to 12.25.


def camo(cols, size=1.3):
    """Woodland blotches: a few overlapping waves, so they look torn rather than tiled."""
    def pat(f, c):
        x, y, z = f.P[..., 0], f.P[..., 1], f.P[..., 2]
        out = c
        for i, col in enumerate(cols):
            k = 1.0 / (size * (1 + 0.35 * i))
            w = np.sin(x * k * 2.1 + i * 1.7) + np.sin(y * k * 1.6 - z * k * 1.3 + i * 2.9) + np.sin((x + y) * k * 0.9 + i)
            out = np.where((w > 1.15 - 0.2 * i)[..., None], P.col(col) * (1 + (P.white(f.shape, f.rng) - 0.5) * 0.1)[..., None], out)
        return out
    return pat


def _waves(x, y, z, k, i):
    return np.sin(x * k * 2.1 + i * 1.7) + np.sin(y * k * 1.6 - z * k * 1.3 + i * 2.9) + np.sin((x + y) * k * 0.9 + i)


def digital(cols, size=1.3, cell=0.5):
    """Pixelated camo: the woodland blotches, read off a half-pixel grid."""
    def pat(f, c):
        x, y, z = (np.floor(f.P[..., i] / cell) * cell for i in range(3))
        out = c
        for i, col in enumerate(cols):
            k = 1.0 / (size * (1 + 0.35 * i))
            out = np.where((_waves(x, y, z, k, i) > 1.15 - 0.2 * i)[..., None], P.col(col), out)
        return out
    return pat


def tiger(cols, pitch=1.6):
    """Tiger stripe: dark brush strokes running round the body, wavering."""
    def pat(f, c):
        x, y, z = f.P[..., 0], f.P[..., 1], f.P[..., 2]
        out = c
        for i, col in enumerate(cols):
            w = np.sin(y * 2 * np.pi / pitch + 1.6 * np.sin(x * 0.45 + z * 0.35 + i * 2.1) + i * 1.3)
            breaks = np.sin(x * 0.9 - z * 0.7 + y * 0.2 + i) > -0.55
            out = np.where(((w > 0.72 - 0.12 * i) & breaks)[..., None], P.col(col), out)
        return out
    return pat


def _hash(ix, iy, iz, salt):
    return (np.sin(ix * 12.9898 + iy * 78.233 + iz * 37.719 + salt * 4.13) * 43758.5453) % 1.0


def fleck(cols, cell=0.55, under=None):
    """Dotted camo: small flecks of each colour over soft blotches."""
    def pat(f, c):
        out = under(f, c) if under else c
        ix, iy, iz = (np.floor(f.P[..., i] / cell) for i in range(3))
        for i, col in enumerate(cols):
            out = np.where((_hash(ix, iy, iz, i + 1) < 0.16)[..., None], P.col(col), out)
        return out
    return pat


def chips(cols, base_pat, cell=0.4, odds=0.05):
    """Desert six-colour: soft blotches with little black and white rock chips."""
    def pat(f, c):
        out = base_pat(f, c)
        ix, iy, iz = (np.floor(f.P[..., i] / cell) for i in range(3))
        for i, col in enumerate(cols):
            out = np.where((_hash(ix, iy, iz, i + 7) < odds)[..., None], P.col(col), out)
        return out
    return pat


def splinter(cols, size=2.0):
    """Splinter camo: sharp-edged shards of colour, cut by two slanted grids."""
    def pat(f, c):
        x, y, z = f.P[..., 0], f.P[..., 1], f.P[..., 2]
        a = np.floor((x * 0.8 + y * 0.45 + z * 0.3) / size)
        b = np.floor((-x * 0.3 + y * 0.9 - z * 0.4) / (size * 0.8))
        h = _hash(a, b, 0, 3)
        out = c
        for i, col in enumerate(cols):
            out = np.where(((h > i / (len(cols) + 1)) & (h < (i + 0.7) / (len(cols) + 1)))[..., None], P.col(col), out)
        return out
    return pat


# every camo a military piece can roll when it drops (clothing/ClothingLoot), with its ground colour
CAMOS = {
    'woodland': ('#5b5a43', camo(['#3f4430', '#6e6247', '#28261f'])),
    'desert': ('#b09a74', camo(['#8c7a58', '#6f6248'], size=1.6)),
    'chocolate_chip': ('#b8a27c', chips(['#1e1c18', '#d8d2c4'], camo(['#8a6e4c', '#6e5a40'], size=1.9))),
    'urban': ('#7d7f80', camo(['#5a5c5e', '#3a3b3d', '#a3a4a2'])),
    'snow': ('#c9cbc6', camo(['#a9ada6', '#8a8f88'], size=1.8)),
    'olive_drab': ('#545a3c', None),
    'coyote': ('#8a7552', None),
    'black': ('#2a2b2c', None),
    'tiger_stripe': ('#5d6a45', tiger(['#2a2a22', '#3d4a2c'])),
    'digital_woodland': ('#6a6b50', digital(['#4a5236', '#7b7458', '#2e2e26'])),
    'digital_desert': ('#b5a07a', digital(['#998563', '#7f7050'], size=1.5)),
    'digital_navy': ('#3a465a', digital(['#2a3344', '#4c5868'])),
    'multi_terrain': ('#8d8463', camo(['#6f6a4c', '#a39370', '#4f4a36', '#c2b591'], size=2.0)),
    'flecked': ('#6b6d4c', fleck(['#3b4a2e', '#2a2a22', '#8a7a55', '#5a6a3a'], under=camo(['#5f6646'], size=2.2))),
    'splinter': ('#8e9060', splinter(['#5e6f43', '#6d5a3f'])),
}


class Camo:
    """A material painted in whichever camo the variant being made is in: cloth(base, **kw) over the camo's ground
    and pattern, a little lighter or darker by shade (pockets and flaps read apart from the panel under them)."""

    def __init__(self, shade=1.0, **kw):
        self.shade, self.kw = shade, kw

    def make(self, name):
        base, pattern = CAMOS[name]
        if self.shade != 1.0:
            base = '#%02x%02x%02x' % tuple(min(255, int(int(base[i:i + 2], 16) * self.shade)) for i in (1, 3, 5))
        return cloth(base, pattern=pattern, **self.kw)


def cargo_pocket(g, p, s, d, mat, flap, y0=4.4, y1=7.8):
    g.side(p, d, s, -1.6, y0, 1.6, y1, mat, t=0.3)
    g.side(p, d + 0.3, s, -1.75, y0 - 0.25, 1.75, y0 + 0.9, flap, t=0.12)
    for y in (y0 + 1.6, y0 + 2.6):                                                  # the bellows pleat
        g.side(p, d + 0.3, s, -0.08, y, 0.08, y1 - 0.3, flap, t=0.05)


def build_cargo_pants(g):
    d = PANTS
    trouser_top(g, d, 'cloth')
    def leg(p, s):
        g.shell(p, 0, 11.0, d, 'cloth')
        g.shell(p, 10.8, 12.0, d + 0.12, 'hem')
        cargo_pocket(g, p, s, d, 'pocket', 'flap')
        g.front(p, d, -1.8, 5.6, 1.8, 7.4, 'pocket', t=0.12)                        # knee panels
        g.back(p, d, -1.5, 0.6, 1.5, 3.6, 'flap', t=0.14)
    g.both(LEGS, leg)


def build_camo_pants(g):
    d = PANTS
    trouser_top(g, d, 'cloth')
    g.shell('body', 10.3, 11.1, d + 0.12, 'web')                                    # a rigger's belt through the loops
    g.front('body', d + 0.12, -0.8, 10.1, 0.8, 11.3, 'buckle', t=0.2)
    def leg(p, s):
        g.shell(p, 0, 10.6, d, 'cloth')
        g.shell(p, 10.0, 11.0, d + 0.2, 'blouse')                                   # bloused into the boots
        cargo_pocket(g, p, s, d, 'cloth', 'pocket')
        g.front(p, d, -1.8, 5.4, 1.8, 7.6, 'pocket', t=0.14)
        g.back(p, d, -1.5, 0.6, 1.5, 3.6, 'pocket', t=0.14)
    g.both(LEGS, leg)


def build_slacks(g):
    d = PANTS - 0.05
    trouser_top(g, d, 'wool')
    g.shell('body', 10.2, 11.0, d + 0.12, 'belt')
    g.front('body', d + 0.12, -0.6, 10.15, 0.6, 11.05, 'buckle', t=0.16)
    def leg(p, s):
        g.shell(p, 0, 11.2, d, 'wool')
        g.front(p, d, -0.08, 0.2, 0.08, 11.2, 'crease', t=0.06)                     # pressed creases
        g.back(p, d, -0.08, 3.0, 0.08, 11.2, 'crease', t=0.06)
        g.back(p, d, -1.3, 1.0, 1.3, 1.3, 'crease', t=0.04)                         # jetted back pocket
        g.side(p, d, s, -0.06, 0.3, 0.06, 11.2, 'crease', t=0.04)
    g.both(LEGS, leg)


def build_sweatpants(g):
    d = PANTS + 0.1
    g.shell('body', 9.4, 12, d + 0.1, 'rib')                                         # elastic waist
    for x in (-0.5, 0.5):                                                           # drawstrings
        g.front('body', d + 0.1, x - 0.12, 10.4, x + 0.12, 12.6, 'string', t=0.14)
    def leg(p, s):
        g.shell(p, 0, 10.4, d, 'fleece')
        g.shell(p, 10.4, 11.4, d - 0.05, 'rib')                                      # cuffed ankles
        g.front(p, d, -1.8, 4.6, 1.8, 7.4, 'wear', t=0.06)                           # baggy, bobbled knees
        g.side(p, d, s, -0.25, 0.2, 0.25, 10.4, 'stripe', t=0.06)
    g.both(LEGS, leg)


def build_shorts(g):
    d = PANTS
    trouser_top(g, d, 'cloth')
    def leg(p, s):
        g.shell(p, 0, 5.2, d + 0.1, 'cloth')
        g.shell(p, 5.0, 5.8, d + 0.25, 'hem')                                       # rolled hem above the knee
        g.side(p, d + 0.1, s, -1.4, 1.2, 1.4, 4.4, 'pocket', t=0.24)
        g.side(p, d + 0.34, s, -1.5, 1.0, 1.5, 1.7, 'hem', t=0.1)
    g.both(LEGS, leg)


def build_skirt(g):
    d = PANTS
    g.shell('body', 9.6, 12, d + 0.15, 'band')
    g.front('body', d + 0.15, 1.6, 9.6, 2.4, 12.0, 'button', t=0.1)
    skirt(g, 7.4, d + 0.3, 'cloth', flare=0.35)
    def leg(p, s):                                                                  # pleats on the front and back
        for x in (-1.2, 0.0, 1.2):
            g.front(p, d + 0.65, x - 0.15, 3.6, x + 0.15, 7.4, 'pleat', t=0.08)
            g.back(p, d + 0.65, x - 0.15, 3.6, x + 0.15, 7.4, 'pleat', t=0.08)
        g.shell(p, 7.0, 7.4, d + 0.7, 'pleat')
    g.both(LEGS, leg)


def build_overalls(g):
    d = PANTS + 0.05
    g.front('body', SHIRT + 0.05, -3.2, 2.2, 3.2, 10.0, 'denim', t=0.3)              # the bib
    g.front('body', SHIRT + 0.35, -1.8, 3.0, 1.8, 5.8, 'denim', t=0.16)               # bib pocket
    g.front('body', SHIRT + 0.51, -1.9, 2.8, 1.9, 3.2, 'seam', t=0.04)
    g.front('body', SHIRT + 0.51, 0.4, 3.4, 0.7, 5.4, 'pencil', t=0.2)
    for s in (-1, 1):
        x0, x1 = (-3.5, -1.9) if s < 0 else (1.9, 3.5)
        g.box('body', (x0, -0.35, -2 - SHIRT - 0.35), (x1, 2.4, 2 + SHIRT + 0.3), 'strap')    # straps over the shoulders
        g.front('body', SHIRT + 0.35, x0 + 0.1, 2.0, x1 - 0.1, 3.2, 'clasp', t=0.22)
        g.box('body', (s * 1.2, -0.35, 2 + SHIRT), (s * 3.5, 6.0, 2 + SHIRT + 0.3), 'strap')  # crossed at the back
    g.shell('body', 9.0, 12, d + 0.05, 'denim')
    for x in (-3.9, 3.7):
        g.box('body', (x, 8.8, -1.0), (x + 0.2, 10.4, 1.0), 'clasp')                  # side buttons
    def leg(p, s):
        g.shell(p, 0, 11.0, d, 'denim')
        g.shell(p, 10.6, 11.6, d + 0.15, 'cuff')
        if s < 0:
            g.side(p, d, s, -0.9, 0.8, 0.3, 4.4, 'denim', t=0.14)                   # hammer loop
        g.front(p, d, -1.8, 5.0, 1.8, 7.6, 'wear', t=0.06)
    g.both(LEGS, leg)


def build_scrubs_pants(g):
    d = PANTS
    g.shell('body', 9.6, 12, d + 0.08, 'cloth')
    g.front('body', d + 0.08, -0.3, 10.0, 0.3, 12.8, 'string', t=0.12)               # the tie
    def leg(p, s):
        g.shell(p, 0, 11.0, d + 0.05, 'cloth')
        g.shell(p, 10.8, 11.6, d + 0.12, 'trim')
        g.side(p, d + 0.05, s, -1.3, 2.6, 1.3, 5.4, 'trim', t=0.16)
    g.both(LEGS, leg)


def build_leather_chaps(g):
    d = PANTS + 0.35                                                                # worn over trousers
    g.shell('body', 10.0, 11.2, d + 0.1, 'belt')
    g.front('body', d + 0.1, -1.0, 9.8, 1.0, 11.4, 'buckle', t=0.22)
    def leg(p, s):
        # open at the back: front and outer side, a strap behind
        lo, hi = BOXES[p]
        g.box(p, (lo[0] - d, 0, lo[2] - d), (hi[0] + d, 11.0, lo[2] - d + 0.3), 'leather')
        x = lo[0] - d if s < 0 else hi[0] + d - 0.3
        g.box(p, (x, 0, lo[2] - d), (x + 0.3, 11.0, hi[2] + 0.2), 'leather')
        for y in (1.2, 5.2, 9.2):
            g.back(p, d - 0.2, -2.2, y, 2.2, y + 0.5, 'strap', t=0.14)
        for y in (1.0 + 0.9 * i for i in range(11)):                                # fringe down the outseam
            g.side(p, d, s, 1.5, y, 2.6, y + 0.35, 'fringe', t=0.12)
        g.front(p, d + 0.3, -1.6, 1.0, 1.6, 3.4, 'tooling', t=0.08)
    g.both(LEGS, leg)


def build_running_shoes(g):
    def foot(p, s):
        shoe(g, p, s, 9.8, d=0.42, toe=2.9, sole='foam')
        g.box(p, (-2.35, 11.7, -2 - 2.9), (2.35, 12.25, 2.4), 'tread')
        laces(g, p, 0.42, 9.6, 10.6, 0.5)
        g.side(p, 0.42, s, -2.4, 10.0, 1.6, 10.6, 'swoosh', t=0.08)
        g.side(p, 0.42, s, -1.0, 10.6, 0.6, 11.2, 'swoosh', t=0.08)
        g.box(p, (-0.6, 9.0, 2.3), (0.6, 10.1, 2.7), 'reflect')                    # heel pull and reflector
    g.both(LEGS, foot)


def build_dress_shoes(g):
    def foot(p, s):
        shoe(g, p, s, 10.2, d=0.4, toe=3.2, sole='sole')
        g.box(p, (-1.9, 10.0, -5.3), (1.9, 10.7, -4.2), 'upper')                    # the rounded toe
        g.box(p, (-2.3, 10.7, -5.0), (2.3, 10.9, -4.7), 'stitch')                   # cap-toe line
        laces(g, p, 0.4, 10.0, 10.7, 0.35)
        g.box(p, (-2.3, 11.0, 1.0), (2.3, 11.45, 2.35), 'heel')
    g.both(LEGS, foot)


def build_cowboy_boots(g):
    def foot(p, s):
        shoe(g, p, s, 4.6, d=0.6, toe=3.6, sole='sole')
        g.box(p, (-1.4, 10.4, -5.9), (1.4, 11.4, -5.4), 'upper')                    # the pointed toe
        g.shell(p, 4.2, 4.8, 0.85, 'upper')                                          # flared shaft top
        for z0, z1 in ((-2.6, -2.1), (2.1, 2.6)):                                   # scalloped dip at the sides
            g.side(p, 0.85, s, z0, 4.2, z1, 5.0, 'shadow', t=0.02)
        g.box(p, (-2.2, 11.2, 0.6), (2.2, 12.6, 2.4), 'heel')                       # stacked riding heel
        g.front(p, 0.6, -1.4, 5.4, 1.4, 9.6, 'stitch', t=0.08)                       # the stitched shaft
        g.side(p, 0.6, s, -1.0, 5.6, 1.0, 9.2, 'stitch', t=0.08)
        g.side(p, 0.6, s, -0.4, 10.6, 0.4, 11.2, 'spur', t=0.25)
    g.both(LEGS, foot)


def build_hiking_boots(g):
    def foot(p, s):
        shoe(g, p, s, 7.6, sole='lugsole', toe=3.0)
        g.box(p, (-2.3, 9.9, -5.1), (2.3, 11.4, -3.8), 'rand')                      # rubber rand round the toe
        g.shell(p, 7.3, 8.1, BOOT + 0.2, 'collar')
        laces(g, p, BOOT, 8.2, 10.4, 0.7)
        g.side(p, BOOT, s, -1.6, 8.6, 1.8, 10.2, 'panel', t=0.1)                    # suede panels
        g.box(p, (-2.4, 11.6, -5.1), (2.4, 12.35, 2.45), 'lugsole')
    g.both(LEGS, foot)


def build_rubber_boots(g):
    def foot(p, s):
        shoe(g, p, s, 3.2, d=0.7, toe=3.0, upper='rubber', sole='lugsole')
        g.shell(p, 2.8, 3.6, 0.95, 'band')                                           # the rolled top
        g.shell(p, 10.4, 11.0, 0.78, 'band')                                         # foxing stripe
        g.box(p, (-2.3, 10.4, -5.0), (2.3, 11.0, -2.6), 'band')
        g.side(p, 0.7, s, -0.6, 3.6, 0.6, 4.6, 'band', t=0.18)                       # pull tab
        g.box(p, (-2.45, 11.6, -5.1), (2.45, 12.35, 2.5), 'lugsole')
    g.both(LEGS, foot)


def build_combat_boots(g):
    def foot(p, s):
        shoe(g, p, s, 4.4, sole='lugsole', toe=3.0)
        g.shell(p, 4.2, 5.0, BOOT + 0.18, 'collar')
        g.side(p, BOOT, s, -1.8, 5.2, 1.8, 9.2, 'nylon', t=0.08)                    # nylon shaft panels
        laces(g, p, BOOT, 5.2, 10.4, 0.8)
        for y in (5.4, 6.4):
            for x in (-1.15, 1.15):
                g.front(p, BOOT, x - 0.15, y, x + 0.15, y + 0.3, 'hook', t=0.3)
        g.side(p, BOOT, -s, -1.0, 9.6, 1.0, 10.4, 'vent', t=0.06)                    # drain vents, inner side
        g.box(p, (-2.2, 10.2, -5.2), (2.2, 11.42, -3.8), 'toecap')
        g.box(p, (-2.45, 11.6, -5.15), (2.45, 12.4, 2.5), 'lugsole')
    g.both(LEGS, foot)


def build_slippers(g):
    def foot(p, s):
        g.box(p, (-2.4, 11.4, -5.0), (2.4, 12.2, 2.4), 'sole')
        g.box(p, (-2.25, 9.8, -5.0), (2.25, 11.4, -0.4), 'plush')                    # open-backed mule
        g.box(p, (-2.45, 9.7, -2.2), (2.45, 10.3, -0.3), 'cuff')
        g.box(p, (-2.2, 10.9, -0.4), (2.2, 11.4, 2.3), 'plush')
    g.both(LEGS, foot)


def glove(g, p, s, mat, d=0.45, cuff=None, cuff_y=(7.6, 8.9), cuff_d=0.9):
    lo, hi = BOXES[p]
    g.shell(p, 8.4, 10.35, d, mat)
    g.box(p, (lo[0] - d, 10.35, lo[2] - d), (hi[0] + d, 10.6, hi[2] + d), mat)       # fingertips
    if cuff:
        g.shell(p, cuff_y[0], cuff_y[1], cuff_d, cuff)


def build_leather_gloves(g):
    def hand(p, s):
        glove(g, p, s, 'leather', cuff='leather', cuff_y=(8.0, 8.9), cuff_d=0.6)
        for z in (-1.0, 0.0, 1.0):                                                  # points on the back
            g.side(p, 0.45, s, z - 0.06, 8.6, z + 0.06, 9.8, 'stitch', t=0.04)
        g.side(p, 0.6, s, -1.2, 8.2, -0.4, 8.6, 'snap', t=0.12)
    g.both(ARMS, hand)


def build_winter_gloves(g):
    def hand(p, s):
        glove(g, p, s, 'shell', d=0.6, cuff='knit', cuff_y=(7.2, 8.8), cuff_d=0.85)
        g.side(p, 0.6, s, -1.8, 8.6, 1.8, 10.1, 'quilt', t=0.12)                     # padded back
        g.shell(p, 10.0, 10.6, 0.68, 'grip')
    g.both(ARMS, hand)


def build_rubber_gloves(g):
    def hand(p, s):
        glove(g, p, s, 'rubber', d=0.4, cuff='rubber', cuff_y=(5.8, 8.6), cuff_d=0.7)    # long dishwashing cuffs
        g.shell(p, 5.8, 6.2, 0.78, 'rubber')
        g.shell(p, 9.6, 10.35, 0.44, 'grip')
    g.both(ARMS, hand)


def build_tactical_gloves(g):
    def hand(p, s):
        glove(g, p, s, 'nylon', d=0.45, cuff='nylon', cuff_y=(7.8, 8.9), cuff_d=0.7)
        g.side(p, 0.45, s, -1.8, 9.2, 1.8, 10.0, 'knuckle', t=0.32)                  # hard knuckles
        g.side(p, 0.7, s, -1.4, 8.0, 0.6, 8.6, 'velcro', t=0.12)
        g.shell(p, 10.0, 10.6, 0.5, 'palm')
    g.both(ARMS, hand)


def build_scarf(g):
    d = SHIRT + 0.6
    g.box('body', (-3.6, -1.2, -2 - d), (3.6, 1.2, 2 + d), 'knit')                  # wrapped round the neck
    g.box('body', (-3.9, -0.4, -2 - d - 0.3), (3.9, 0.6, 2 + d + 0.3), 'knit')
    g.box('body', (1.0, 0.8, -2 - d - 0.6), (2.6, 7.2, -2 - d - 0.2), 'knit')       # the ends down the front
    g.box('body', (-2.2, 0.8, -2 - d - 0.4), (-0.8, 5.6, -2 - d), 'knit')
    for x in (1.1, 1.5, 1.9, 2.3):                                                  # tassels
        g.box('body', (x, 7.2, -2 - d - 0.5), (x + 0.22, 7.9, -2 - d - 0.3), 'fringe')
    g.box('body', (-3.0, -0.6, -2 - d - 0.5), (3.0, 0.0, -2 - d - 0.25), 'stripe')
    g.box('body', (1.0, 5.6, -2 - d - 0.66), (2.6, 6.1, -2 - d - 0.55), 'stripe')


def build_necktie(g):
    z = -2 - SHIRT - 0.34                                                        # clear of a shirt's buttons, under a jacket
    g.box('body', (-0.8, -0.2, z - 0.35), (0.8, 1.0, z), 'silk')                   # knot
    g.box('body', (-0.65, 1.0, z - 0.2), (0.65, 3.6, z), 'silk')
    g.box('body', (-0.95, 3.6, z - 0.2), (0.95, 7.4, z), 'silk')                   # the blade
    g.box('body', (-0.55, 7.4, z - 0.2), (0.55, 7.9, z), 'silk')
    g.box('body', (-1.0, 4.4, z - 0.26), (1.0, 4.65, z - 0.2), 'bar')               # tie bar
    g.box('body', (-3.0, -0.6, -2.3), (3.0, -0.1, 2.3), 'silk')                     # round the collar


LOWER = {
    'cargo_pants': (build_cargo_pants, dict(cloth=cloth('#5d5a48', weave=0.12), hem=cloth('#524f3f'), pocket=cloth('#565341'),
                                            flap=cloth('#4c4a3a'), loop=cloth('#524f3f')), 0.45),
    'camo_pants': (build_camo_pants, dict(cloth=Camo(), pocket=Camo(0.95), blouse=Camo(0.88), loop=Camo(0.88),
                                          web=cloth('#3b3a2c'), buckle=metal()), 0.5),
    'slacks': (build_slacks, dict(wool=cloth('#3a3b40', weave=0.04), crease=flat('#2c2d31', ink=False), loop=cloth('#34353a'),
                                  belt=leather('#22180f'), buckle=metal('brass')), 0.25),
    'sweatpants': (build_sweatpants, dict(fleece=cloth('#6a6c70', weave=0.03, stitch=None), rib=rib('#5c5e62'),
                                          string=flat('#c9c3b5'), wear=cloth('#74767a', stitch=None), stripe=flat('#2f3135')), 0.4),
    'shorts': (build_shorts, dict(cloth=cloth('#8a7a5a', weave=0.08), hem=cloth('#7c6d50'), pocket=cloth('#7c6d50'),
                                  loop=cloth('#7c6d50')), 0.35),
    'skirt': (build_skirt, dict(cloth=cloth('#3d4250', pattern=plaid('#3d4250', '#5a2a2a', 1.4), stitch=None),
                                band=cloth('#343846'), pleat=cloth('#30343f', stitch=None), button=metal()), 0.3),
    'overalls': (build_overalls, dict(denim=denim('#3a4e6a'), cuff=denim('#56709a'), seam=flat('#9a7a44', ink=False),
                                      strap=denim('#34465e'), clasp=metal('brass'), pencil=flat('#b08a2a'),
                                      wear=denim('#5a6e8a')), 0.5),
    'scrubs_pants': (build_scrubs_pants, dict(cloth=cloth('#3f6f6a', weave=0.04), trim=cloth('#386360'),
                                              string=flat('#c9c3b5')), 0.3),
    'leather_chaps': (build_leather_chaps, dict(leather=leather('#4a3020', stitch='#a88458'), belt=leather('#3a2618'),
                                                buckle=metal('steel'), strap=leather('#3a2618'), fringe=leather('#5a3b27'),
                                                tooling=leather('#3e281a', stitch='#a88458')), 0.45),
    'running_shoes': (build_running_shoes, dict(foam=rubber('#b4b0a6'), sole=rubber('#b4b0a6'), tread=rubber('#2b2a28'),
                                                upper=mesh('#4a5866'), tongue=cloth('#3e4a56'), lace=flat('#c4beb2'),
                                                swoosh=leather('#9a4a2a'), reflect=glow_tape()), 0.4),
    'dress_shoes': (build_dress_shoes, dict(sole=rubber('#1a1614'), heel=rubber('#1a1614'), upper=leather('#2a1a12', gloss=0.3),
                                            tongue=leather('#2a1a12'), lace=flat('#1c1410'), stitch=flat('#4a3424', ink=False)), 0.3),
    'cowboy_boots': (build_cowboy_boots, dict(sole=rubber('#2a1e16'), heel=leather('#2e2018'), upper=leather('#6a4428', stitch='#d2b07a'),
                                              stitch=leather('#80562e', stitch='#d8b47e'), shadow=flat('#120c08', ink=False),
                                              spur=metal('steel')), 0.45),
    'hiking_boots': (build_hiking_boots, dict(lugsole=rubber('#2a2522', stud=True), sole=rubber('#2a2522'), upper=leather('#5a4a38'),
                                              rand=rubber('#3a332c'), collar=cloth('#7a3a2a'), tongue=cloth('#5a4a38'),
                                              lace=flat('#a8442e'), panel=cloth('#6a5a46', weave=0.05)), 0.45),
    'rubber_boots': (build_rubber_boots, dict(rubber=rubber('#3e4a34'), band=rubber('#2c3426'), lugsole=rubber('#1e1c1a', stud=True),
                                              sole=rubber('#1e1c1a')), 0.45),
    'combat_boots': (build_combat_boots, dict(lugsole=rubber('#1c1a18', stud=True), sole=rubber('#1c1a18'), upper=leather('#5e5040'),
                                              collar=leather('#4a3f32'), nylon=cloth('#6a6048', weave=0.12), tongue=leather('#54483a'),
                                              lace=flat('#3a352c'), hook=metal(), vent=metal(), toecap=leather('#54483a')), 0.5),
    'slippers': (build_slippers, dict(sole=rubber('#3a2e26'), plush=fur('#6e5e50'), cuff=fur('#5e5044')), 0.4),
    'leather_gloves': (build_leather_gloves, dict(leather=leather('#2e221a', gloss=0.2), stitch=flat('#4a3828', ink=False),
                                                  snap=metal('brass')), 0.35),
    'winter_gloves': (build_winter_gloves, dict(shell=cloth('#2f3a46', weave=0.04), knit=rib('#2a333d'), quilt=cloth('#38444f'),
                                                grip=rubber('#1e1e1e')), 0.35),
    'rubber_gloves': (build_rubber_gloves, dict(rubber=plastic('#a8862e', gloss=0.25), grip=rubber('#8e7228')), 0.35),
    'tactical_gloves': (build_tactical_gloves, dict(nylon=cloth('#3e3c32', weave=0.1), knuckle=plastic('#2a2924'),
                                                    velcro=cloth('#4a473a'), palm=leather('#5e5546')), 0.45),
    'scarf': (build_scarf, dict(knit=knit('#6a2e2a'), fringe=flat('#5a2724'), stripe=knit('#b6a888')), 0.35),
    'necktie': (build_necktie, dict(silk=stripes('#3a2a4a', '#6a5a3a', 0.6),
                                    bar=metal('steel')), 0.2),
}


# ------------------------------------------------------------------ batch 5: uniforms, armour and aircrew
VEST_D = 1.4     # a vest sits over the bulkiest coat (a parka is 1.1 out, its storm flap 1.35)


def stencil(base, ink):
    """Big stencilled numbers or letters, chipped: a deck jersey's or a vest's lettering."""
    def fn(f):
        c = P.plain(f, base, 0.06)
        u, v = f.uv[..., 0], f.uv[..., 1]
        H = max(v.max(), 1e-3)
        W = max(u.max(), 1e-3)
        band = (v > H * 0.3) & (v < H * 0.62) & (u > W * 0.15) & (u < W * 0.85)
        gaps = (np.floor((u - W * 0.15) / (W * 0.7 / 7)) % 2) == 1
        mark = band & ~(gaps & (v > H * 0.36) & (v < H * 0.56))
        chip = P.white(f.shape, f.rng) > 0.78
        c = np.where((mark & ~chip)[..., None], P.col(ink), c)
        return toon(f, grade(c, 0.4), ink=False)
    return fn


def molle(g, part, d, x0, y0, x1, y1, mat, rows=None, t=0.14, back=False):
    """Rows of webbing straps (MOLLE) across a panel."""
    put = g.back if back else g.front
    y = y0
    while y + 0.5 <= y1:
        put(part, d, x0, y, x1, y + 0.5, mat, t=t)
        y += 1.0


def pouch(g, part, d, x0, y0, x1, y1, body, flap, t=0.8):
    g.front(part, d, x0, y0, x1, y1, body, t=t)
    g.front(part, d + t, x0 - 0.05, y0 - 0.1, x1 + 0.05, y0 + 0.9, flap, t=0.12)


def reflective_bands(g, d, mat, arm_ys=(5.8,), body_ys=(9.2,), leg_ys=()):
    for y in body_ys:
        g.shell('body', y, y + 0.7, d + 0.04, mat)
    for y in arm_ys:
        g.both(ARMS, lambda p, s, y=y: g.shell(p, y, y + 0.7, d + 0.04, mat))
    for y in leg_ys:
        g.both(LEGS, lambda p, s, y=y: g.shell(p, y, y + 0.7, d + 0.04, mat))


def build_flight_deck_jersey(g):
    d = SHIRT + 0.05
    g.shell('body', 0, 12, d, 'jersey')
    g.front('body', d, -3.0, 2.2, 3.0, 6.8, 'number', t=0.06)                       # stencilled job letters
    g.back('body', d, -3.0, 1.6, 3.0, 6.6, 'number', t=0.06)
    g.box('body', (-2.8, -0.8, -2 - d - 0.25), (2.8, 0.4, 2 + d + 0.2), 'rib')       # mock neck
    g.front('body', d, -4 - d, 10.6, 4 + d, 11.4, 'tape', t=0.04)
    sleeves(g, d, 'jersey', end=8.0, cuff='rib', cuff_d=0.12)
    g.both(ARMS, lambda p, s: g.shell(p, 0.6, 1.3, d + 0.04, 'tape'))


def build_police_jacket(g):
    d = JACKET
    g.shell('body', 0, 11.6, d, 'nylon')
    g.shell('body', 10.6, 12, d + 0.1, 'rib')
    collar_flaps(g, d, 'nylon')
    g.front('body', d, -0.25, 0, 0.25, 11.6, 'zip', t=0.12)
    g.front('body', d, -3.3, 1.8, -1.0, 2.9, 'badge', t=0.14)                         # the badge, a star on the left chest
    g.front('body', d + 0.14, -2.6, 1.6, -1.7, 3.2, 'badge', t=0.1)
    g.front('body', d, 1.0, 2.0, 3.4, 2.5, 'name', t=0.1)                            # name tape
    g.front('body', d, 1.2, 3.0, 3.4, 5.0, 'nylon', t=0.2)                           # radio pocket with a mic loop
    g.front('body', d + 0.2, 1.8, 2.6, 2.6, 3.0, 'loop', t=0.2)
    for s in (-1, 1):                                                                # epaulettes
        x0, x1 = (-4 - d, -1.6) if s < 0 else (1.6, 4 + d)
        g.box('body', (x0, -0.15, -0.8), (x1, 0.1, 0.8), 'nylon')
        g.front('body', d, s * 1.3, 7.6, s * 3.6, 10.0, 'nylon', t=0.16)
    g.back('body', d, -3.2, 3.0, 3.2, 5.0, 'stencil', t=0.05)                        # POLICE across the back
    def arm(p, s):
        g.shell(p, -2.0, 8.0, d, 'nylon')
        g.shell(p, 7.4, 8.4, d + 0.12, 'rib')
        g.side(p, d, s, -1.4, -0.6, 1.4, 2.2, 'patch', t=0.12)                        # shoulder flash
    g.both(ARMS, arm)


def build_fire_coat(g):
    d = JACKET + 0.25
    g.shell('body', 0, 12, d, 'turnout')
    skirt(g, 5.6, d + 0.12, 'turnout', flare=0.25)
    g.box('body', (-3.8, -2.2, -2 - d - 0.2), (3.8, 0.4, 2 + d + 0.25), 'turnout')    # collar turned up round the neck
    g.box('body', (-1.2, -2.2, -2 - d - 0.5), (1.4, 0.2, -2 - d - 0.2), 'turnout')    # throat tab
    g.front('body', d, -0.9, 0, 1.1, 12, 'turnout', t=0.3)                          # storm flap
    for y in (1.4, 4.0, 6.6, 9.2):                                                  # hook-and-dee closures
        g.front('body', d + 0.3, -0.4, y, 0.6, y + 0.5, 'dee', t=0.12)
    for s in (-1, 1):                                                               # bellows pockets
        g.front('body', d, s * 1.3, 7.6, s * 3.8, 11.4, 'turnout', t=0.45)
        g.front('body', d + 0.45, s * 1.2, 7.4, s * 3.9, 8.4, 'turnout', t=0.12)
    g.front('body', d, 1.3, 2.2, 3.4, 4.4, 'turnout', t=0.3)                         # radio pocket
    g.back('body', d, -3.4, 2.0, 3.4, 4.4, 'stencil', t=0.05)                       # the name across the back
    reflective_bands(g, d, 'tape', arm_ys=(3.2, 6.6), body_ys=(6.8, 11.0))
    g.both(LEGS, lambda p, s: g.shell(p, 4.6, 5.3, d + 0.42, 'tape'))
    sleeves(g, d, 'turnout', end=8.2, cuff='wristlet', cuff_d=-0.25)


def build_field_jacket(g):
    d = JACKET
    g.shell('body', 0, 12, d, 'sateen')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 1.8, d + 0.05, 'sateen'))
    g.box('body', (-3.4, -1.2, -2 - d - 0.25), (3.4, 0.3, 2 + d + 0.2), 'sateen')     # stand collar, the hood rolled in it
    g.front('body', d, -0.8, 0, 0.8, 12, 'sateen', t=0.22)
    buttons(g, 'body', d + 0.22, 0, (1.6, 4.2, 6.8, 9.4), 'snap')
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.3, s * 3.7))
        pouch(g, 'body', d, x0, 2.0, x1, 5.2, 'sateen', 'flap', t=0.3)              # chest pockets
        g.front('body', d, s * 1.3, 7.6, s * 3.7, 11.6, 'sateen', t=0.4)            # the big hip pockets
        g.front('body', d + 0.4, s * 1.2, 7.4, s * 3.8, 8.6, 'flap', t=0.12)
        x0, x1 = (-4 - d, -1.6) if s < 0 else (1.6, 4 + d)
        g.box('body', (x0, -0.15, -0.8), (x1, 0.1, 0.8), 'flap')
    g.shell('body', 6.6, 7.0, d + 0.06, 'cord')                                     # the waist drawcord
    g.front('body', d, 2.8, 2.4, 3.6, 3.0, 'name', t=0.1)
    def arm(p, s):
        g.shell(p, -2.0, 8.0, d, 'sateen')
        g.shell(p, 7.0, 8.2, d + 0.12, 'flap')                                       # cuff tabs
        g.side(p, d, s, -1.3, -0.4, 1.3, 2.0, 'patch', t=0.1)
    g.both(ARMS, arm)


def build_ghillie_suit(g):
    import random
    rnd = random.Random(7)
    d = JACKET + 0.1
    g.shell('body', 0, 12, d, 'net')
    g.box('body', (-4.6, -2.4, -0.2), (4.6, 0.6, 2 + d + 1.4), 'jute')               # the hood down
    sleeves(g, d, 'net', end=8.2)
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 11.0, PANTS + 0.4, 'net'))
    mats = ('jute', 'jute2', 'jute3')
    def tufts(part, n, lo, hi):
        for _ in range(n):
            face = rnd.choice(('front', 'back', 'side'))
            y = rnd.uniform(lo, hi)
            w = rnd.uniform(0.5, 1.4); L = rnd.uniform(1.2, 3.2)
            m = rnd.choice(mats)
            (bx0, _, bz0), (bx1, _, bz1) = BOXES[part]
            dd = d if part in ('body', 'right_arm', 'left_arm') else PANTS + 0.4
            if face == 'side':
                s = -1 if part.startswith('right') else 1 if part.startswith('left') else rnd.choice((-1, 1))
                z = rnd.uniform(bz0, bz1 - w)
                g.side(part, dd, s, z, y, z + w, y + L, m, t=rnd.uniform(0.2, 0.6))
            else:
                x = rnd.uniform(bx0 - dd, bx1 + dd - w)
                (g.front if face == 'front' else g.back)(part, dd, x, y, x + w, y + L, m, t=rnd.uniform(0.2, 0.6))
    tufts('body', 34, -0.6, 11.0)
    for p in ARMS:
        tufts(p, 12, -2.0, 7.0)
    for p in LEGS:
        tufts(p, 12, 0.0, 9.0)


def build_ww2_flight_jacket(g):
    d = JACKET + 0.2
    g.shell('body', 0, 11.0, d, 'hide')
    g.shell('body', 10.4, 12.0, d + 0.2, 'shearling')                               # the fleece waistband
    g.box('body', (-4.4, -1.6, -2 - d - 0.4), (4.4, 1.0, 2 + d + 0.3), 'shearling')   # huge collar, turned down
    for s in (-1, 1):
        g.box('body', (s * 0.4, 0.8, -2 - d - 0.4), (s * 3.4, 3.4, -2 - d - 0.05), 'shearling')
        g.box('body', (s * 0.2, -1.0, -2 - d - 0.55), (s * 1.6, -0.2, -2 - d - 0.35), 'strap')   # collar straps
    g.front('body', d, -1.2, 0.8, -0.6, 11.0, 'shearling', t=0.2)                   # the fleece edge of the front
    g.front('body', d, -0.6, 0.8, -0.3, 11.0, 'zip', t=0.12)
    g.shell('body', 9.6, 10.4, d + 0.12, 'strap')                                    # waist strap and buckle
    g.front('body', d + 0.12, 2.6, 9.4, 3.6, 10.6, 'buckle', t=0.15)
    for s in (-1, 1):
        g.front('body', d, s * 1.6, 6.0, s * 3.8, 9.0, 'hide', t=0.14)
    g.back('body', d, -3.6, 0.8, 3.6, 10.4, 'hide', t=0.06)                          # panel seams on the back
    def arm(p, s):
        g.shell(p, -2.0, 7.6, d, 'hide')
        g.shell(p, 7.2, 8.6, d + 0.15, 'shearling')
        g.side(p, d, s, -0.6, 1.2, 0.6, 7.0, 'seam', t=0.04)
    g.both(ARMS, arm)


def build_fur_coat(g):
    d = JACKET + 0.4
    g.shell('body', 0, 12, d, 'fur')
    skirt(g, 9.4, d + 0.15, 'fur', flare=0.4)
    g.box('body', (-4.6, -1.6, -2 - d - 0.5), (4.6, 1.6, 2 + d + 0.5), 'collar')     # the big shawl collar
    for s in (-1, 1):
        g.box('body', (s * 0.2, 1.6, -2 - d - 0.45), (s * 2.6, 6.0, -2 - d), 'collar')
    for y in (4.0, 7.0):
        g.front('body', d, -0.4, y, 0.4, y + 0.5, 'clasp', t=0.2)
    def arm(p, s):
        g.shell(p, -2.0, 7.6, d, 'fur')
        g.shell(p, 6.6, 8.4, d + 0.35, 'collar')                                     # wide fur cuffs
    g.both(ARMS, arm)


def build_bespoke_suit(g):
    build_suit_jacket(g)
    d = JACKET - 0.15
    g.box('body', (-2.6, 5.4, -2 - d - 0.1), (2.6, 7.2, -2 - d), 'vest')              # the waistcoat under the buttons
    g.front('body', d, -1.6, 5.6, -0.8, 6.0, 'chain', t=0.12)                       # watch chain
    def leg(p, s):
        g.shell(p, 1.4, 11.2, PANTS - 0.05, 'wool')
        g.shell(p, 10.8, 11.4, PANTS + 0.05, 'wool')                                 # turn-ups
        g.front(p, PANTS - 0.05, -0.08, 1.4, 0.08, 11.2, 'crease', t=0.06)
        g.side(p, PANTS - 0.05, s, -0.06, 1.4, 0.06, 11.2, 'stripe', t=0.04)        # pinstripe outseam
    g.both(LEGS, leg)


def build_poncho(g):
    d = JACKET + 0.2
    W = 7.6                                                                          # wide enough to drape the arms
    g.box('body', (-W, -0.4, -2 - d), (W, 1.0, 2 + d), 'pvc')                        # over the shoulders
    g.box('body', (-W + 0.6, 1.0, -2 - d - 0.1), (W - 0.6, 3.6, 2 + d + 0.1), 'pvc')
    g.box('body', (-W + 1.4, 3.6, -2 - d - 0.2), (W - 1.4, 9.8, 2 + d + 0.2), 'pvc')
    g.box('body', (-4.6, 9.8, -2 - d - 0.25), (4.6, 11.4, 2 + d + 0.25), 'pvc')
    g.box('body', (-4.0, -2.4, 0.0), (4.0, 0.0, 2 + d + 1.1), 'pvc')                 # the hood down the back
    g.box('body', (-1.6, -0.2, -2 - d - 0.15), (1.6, 0.6, -2 - d), 'shadow')          # neck opening
    for x in (-W + 0.6, W - 1.0):                                                    # grommets at the corners
        g.box('body', (x, 0.2, -2 - d - 0.15), (x + 0.4, 0.6, -2 - d), 'grommet')
    g.front('body', d + 0.2, -0.4, 1.0, 0.4, 9.8, 'seam', t=0.04)


def build_flak_vest(g):
    d = VEST_D
    g.shell('body', 0.4, 11.0, d, 'nylon')
    g.box('body', (-3.8, -0.8, -2 - d - 0.1), (3.8, 0.8, 2 + d + 0.2), 'collar')     # padded collar
    for y in (2.6, 4.8, 7.0):                                                        # quilted plate rows
        g.shell('body', y, y + 0.25, d + 0.05, 'seam')
    g.front('body', d, -0.3, 0.8, 0.3, 11.0, 'zip', t=0.14)
    for s in (-1, 1):
        pouch(g, 'body', d, s * 1.0, 8.4, s * 3.6, 10.6, 'nylon', 'flap', t=0.5)     # grenade pockets
        g.front('body', d, s * 3.0, 1.4, s * 3.6, 6.0, 'eyelet', t=0.2)
    g.back('body', d, -0.4, 1.0, 0.4, 3.0, 'eyelet', t=0.1)


def build_police_vest(g):
    d = VEST_D - 0.15
    g.shell('body', 1.0, 10.4, d, 'carrier')
    for s in (-1, 1):                                                                # shoulder straps
        x0, x1 = (-3.4, -1.6) if s < 0 else (1.6, 3.4)
        g.box('body', (x0, -0.3, -2 - d), (x1, 1.0, 2 + d), 'carrier')
        g.front('body', d, x0, 0.2, x1, 1.6, 'velcro', t=0.1)
        g.side('body', d, s, -1.4, 6.4, 1.4, 8.6, 'velcro', t=0.12)                  # cummerbund straps
    g.front('body', d, -3.0, 2.4, 3.0, 3.6, 'stencil', t=0.08)                       # POLICE panels
    g.back('body', d, -3.0, 2.0, 3.0, 4.0, 'stencil', t=0.08)
    g.front('body', d, -3.4, 5.0, -1.4, 7.6, 'carrier', t=0.4)                       # radio and pen pouches
    g.front('body', d, 1.4, 5.0, 3.4, 6.8, 'carrier', t=0.3)
    g.front('body', d + 0.3, 1.8, 4.6, 2.1, 5.6, 'pen', t=0.1)
    g.front('body', d, -2.4, 0.9, -1.6, 2.0, 'badge', t=0.12)


# plate_carrier is left out: it is already a carry vest (ModItems.PLATE_CARRIER, a BagItem with its own model)


def build_survival_vest(g):
    d = VEST_D - 0.1
    g.shell('body', 0.4, 9.2, d, 'mesh')
    g.front('body', d, -0.25, 0.4, 0.25, 9.2, 'zip', t=0.12)
    for s in (-1, 1):
        x0, x1 = (-3.6, -1.6) if s < 0 else (1.6, 3.6)
        g.box('body', (x0, -0.3, -2 - d), (x1, 0.6, 2 + d), 'mesh')
    pouch(g, 'body', d, -3.6, 1.2, -1.2, 4.2, 'nylon', 'flap', t=0.6)                 # radio pocket
    g.front('body', d + 0.6, -2.0, 0.2, -1.7, 1.2, 'aerial', t=0.2)
    pouch(g, 'body', d, 1.2, 1.2, 2.4, 3.6, 'nylon', 'flap', t=0.4)                  # flare pockets
    pouch(g, 'body', d, 2.5, 1.2, 3.6, 3.6, 'nylon', 'flap', t=0.4)
    pouch(g, 'body', d, -3.6, 5.2, -1.4, 8.4, 'nylon', 'flap', t=0.5)
    pouch(g, 'body', d, 1.4, 5.2, 3.6, 8.4, 'nylon', 'flap', t=0.5)
    g.side('body', d, -1, -1.2, 4.0, -0.4, 8.6, 'sheath', t=0.5)                      # a knife on the side
    g.side('body', d + 0.5, -1, -1.0, 3.2, -0.6, 4.0, 'grip', t=0.3)
    g.shell('body', 8.6, 9.4, d + 0.08, 'web')


def build_fire_gloves(g):
    def hand(p, s):
        glove(g, p, s, 'leather', d=0.55, cuff='gauntlet', cuff_y=(6.8, 8.8), cuff_d=1.05)
        g.shell(p, 7.4, 7.8, 1.1, 'tape')
        g.side(p, 0.55, s, -1.8, 9.0, 1.8, 10.1, 'pad', t=0.22)
    g.both(ARMS, hand)


def build_fire_pants(g):
    d = PANTS + 0.25
    g.shell('body', 9.2, 12, d + 0.05, 'turnout')
    g.front('body', d + 0.05, -0.8, 9.2, 1.0, 12.0, 'turnout', t=0.25)                # fly flap
    for s in (-1, 1):                                                                 # red suspenders
        g.box('body', (s * 1.6 + (-0.8 if s < 0 else 0), -0.3, -2 - SHIRT - 0.3), (s * 1.6 + (0 if s < 0 else 0.8), 9.2, -2 - SHIRT - 0.05), 'braces')
        g.box('body', (s * 1.6 + (-0.8 if s < 0 else 0), -0.3, 2 + SHIRT + 0.05), (s * 1.6 + (0 if s < 0 else 0.8), 9.2, 2 + SHIRT + 0.3), 'braces')
    def leg(p, s):
        g.shell(p, 0, 11.2, d, 'turnout')
        g.front(p, d, -2.0, 4.6, 2.0, 7.6, 'knee', t=0.25)                            # knee pads
        cargo_pocket(g, p, s, d, 'turnout', 'turnout', y0=1.6, y1=4.6)
        g.shell(p, 8.4, 9.1, d + 0.05, 'tape')
        g.shell(p, 10.4, 11.4, d + 0.12, 'cuff')
    g.both(LEGS, leg)


def build_g_suit(g):
    d = PANTS + 0.3                                                                   # chaps over the flight suit
    g.shell('body', 8.0, 11.4, d + 0.1, 'bladder')                                   # the belly bladder
    g.side('body', d + 0.1, 1, -1.2, 8.6, -0.2, 9.8, 'hose', t=0.5)                  # the hose fitting, left hip
    g.side('body', d + 0.6, 1, -0.9, 9.4, -0.5, 13.0, 'hose', t=0.3)
    g.front('body', d + 0.1, -3.8, 9.6, 3.8, 10.2, 'zip', t=0.1)
    def leg(p, s):
        g.shell(p, 0, 4.0, d, 'bladder')                                              # thigh
        g.shell(p, 6.2, 10.8, d, 'bladder')                                           # calf; the knee left open
        g.front(p, d, -1.8, 1.0, 1.8, 3.6, 'pocket', t=0.3)
        g.side(p, d, s, -0.15, 0, 0.15, 4.0, 'zip', t=0.1)                            # leg zips
        g.side(p, d, s, -0.15, 6.2, 0.15, 10.8, 'zip', t=0.1)
        for y in (0.6, 1.8, 3.0, 7.0, 8.4, 9.8):                                      # lacing adjusters
            g.back(p, d, -0.9, y, 0.9, y + 0.35, 'lace', t=0.08)
    g.both(LEGS, leg)


KIT = {
    'flight_deck_jersey': (build_flight_deck_jersey, dict(jersey=cloth('#a8922e', weave=0.05), number=stencil('#a8922e', '#1d1c1a'),
                                                          rib=rib('#988428'), tape=glow_tape()), 0.4),
    'police_jacket': (build_police_jacket, dict(nylon=cloth('#22283a', weave=0.05), rib=rib('#1d2232'), zip=metal(),
                                                badge=metal('brass'), name=flat('#b8b09a'), loop=cloth('#1a1f2c'),
                                                stencil=stencil('#22283a', '#b8b09a'), patch=cloth('#3a3a2a', stitch='#a8946a')), 0.4),
    'fire_coat': (build_fire_coat, dict(turnout=cloth('#8a7a52', weave=0.12, stitch='#3a2e1c'), tape=glow_tape('#b5b33a'),
                                        dee=metal(), stencil=stencil('#8a7a52', '#2a241a'), wristlet=knit('#1e1c1a')), 0.55),
    'field_jacket': (build_field_jacket, dict(sateen=cloth('#545a3e', weave=0.08), flap=cloth('#4c5238'), snap=metal('brass'),
                                              cord=flat('#3a3e2a'), name=flat('#3e4330'), patch=cloth('#4a4f36')), 0.5),
    'ghillie_suit': (build_ghillie_suit, dict(net=cloth('#3e4130', weave=0.2), jute=fur('#5d5a3a'), jute2=fur('#4a4f30'),
                                              jute3=fur('#6e6044')), 0.45),
    'ww2_flight_jacket': (build_ww2_flight_jacket, dict(hide=leather('#5a3a22', stitch='#2a1a10', gloss=0.1), shearling=fur('#b8a684'),
                                                        strap=leather('#3e2816'), zip=metal('brass'), buckle=metal('brass'),
                                                        seam=flat('#3a2614', ink=False)), 0.55),
    'fur_coat': (build_fur_coat, dict(fur=fur('#4e3a2a'), collar=fur('#5e4a38'), clasp=metal('brass')), 0.35),
    'bespoke_suit': (build_bespoke_suit, dict(wool=cloth('#2c3038', weave=0.03),
                                              lapel=satin('#262a31'), horn=flat('#2a221a'), square=flat('#a8a088'),
                                              tie=cloth('#5a2a2a', stitch=None), vest=cloth('#3a3530'), chain=metal('brass'),
                                              crease=flat('#22252c', ink=False), stripe=flat('#4a4f58', ink=False)), 0.2),
    'poncho': (build_poncho, dict(pvc=cloth('#4a5236', weave=0.03, stitch=None, pattern=camo(['#3a4230', '#5c5a40'])),
                                  shadow=flat('#0e0f0a', ink=False), grommet=metal('brass'), seam=flat('#3a4030', ink=False)), 0.4),
    'flak_vest': (build_flak_vest, dict(nylon=Camo(weave=0.1), collar=Camo(0.88), seam=flat('#3a3d28', ink=False),
                                        zip=metal(), flap=Camo(0.88), eyelet=metal('brass')), 0.55),
    'police_vest': (build_police_vest, dict(carrier=cloth('#1e2026', weave=0.1), velcro=cloth('#2a2c32', stitch=None),
                                            stencil=stencil('#1e2026', '#bfb8a4'), pen=flat('#1d2d5a'), badge=metal('brass')), 0.45),
    'survival_vest': (build_survival_vest, dict(mesh=mesh('#4e5436'), nylon=cloth('#545a3c'), flap=cloth('#4a5034'), zip=metal(),
                                                aerial=rubber('#1a1a1a'), sheath=leather('#2e2418'), grip=rubber('#1a1a1a'),
                                                web=cloth('#3e4430')), 0.5),
    'fire_gloves': (build_fire_gloves, dict(leather=leather('#8a6a3a', stitch='#3a2a18'), gauntlet=cloth('#8a7a52', weave=0.1),
                                            tape=glow_tape('#b5b33a'), pad=leather('#6e5230')), 0.55),
    'fire_pants': (build_fire_pants, dict(turnout=cloth('#8a7a52', weave=0.12, stitch='#3a2e1c'), knee=leather('#3a3026'),
                                          tape=glow_tape('#b5b33a'), cuff=leather('#3a3026'), braces=cloth('#7a2a24', stitch=None)), 0.55),
    'g_suit': (build_g_suit, dict(bladder=cloth('#5a5e44', weave=0.06), hose=rubber('#2a2a26'), zip=metal(),
                                  pocket=cloth('#52563e'), lace=flat('#3a3c2c')), 0.45),
}



# ------------------------------------------------------------------ wave 2, military batch 1: rigs, helmets, pads and field wear
# Camo() pieces roll their pattern when found (CAMOS above); webbing, buckles and hard parts keep their own colours.
# Refs: refs/items/clothing (plate-carrier, flak-vest, m65-field-jacket, camo-trousers, combat-boots, goggles) and the
# chest-rig / load-bearing-vest photos (Wikimedia, wave2-refs-wanted.md).


def strap_x(s, a, b):
    """The x span of a strap a..b px out from the middle, on side s."""
    return sorted((s * a, s * b))


def harness(g, d, mat, a=1.4, b=2.6, front_to=3.0, back_to=5.0, buckle=None):
    """Shoulder straps over the top, down the front to a panel and down the back to a cross strap."""
    for s in (-1, 1):
        x0, x1 = strap_x(s, a, b)
        g.box('body', (x0, -0.35, -2 - d), (x1, 0.3, 2 + d), mat)
        g.front('body', d, x0, 0.3, x1, front_to, mat, t=0.16)
        g.back('body', d, x0, 0.3, x1, back_to, mat, t=0.16)
        if buckle:
            g.front('body', d + 0.16, x0 - 0.1, front_to - 1.2, x1 + 0.1, front_to - 0.6, buckle, t=0.22)
    g.back('body', d, -b, back_to - 0.8, b, back_to, mat, t=0.18)


def waist_strap(g, d, y0, y1, mat, buckle=None):
    for s in (-1, 1):
        g.side('body', d, s, -2 - d, y0, 2 + d, y1, mat, t=0.14)
        if buckle:
            g.side('body', d + 0.14, s, -1.0, y0 - 0.1, 0.0, y1 + 0.1, buckle, t=0.2)
    g.back('body', d, -4 - d, y0, 4 + d, y1, mat, t=0.14)


def mag_pouch(g, d, x0, x1, y0, y1, t=0.75):
    """A rifle magazine pouch: a box with a lid flap and a pull tab."""
    pouch(g, 'body', d, x0, y0, x1, y1, 'pouch', 'flap', t=t)
    g.front('body', d + t + 0.12, (x0 + x1) / 2 - 0.2, y0 + 0.6, (x0 + x1) / 2 + 0.2, y0 + 1.5, 'tab', t=0.08)


def build_chest_rig(g):
    d = VEST_D - 0.3
    harness(g, d, 'web', front_to=2.6, back_to=5.2, buckle='buckle')
    g.front('body', d, -3.9, 2.4, 3.9, 8.9, 'panel', t=0.35)                          # the MOLLE panel across the chest
    molle(g, 'body', d + 0.35, -3.9, 2.6, 3.9, 3.9, 'web', t=0.08)
    for i in range(4):                                                               # four rifle mags in a row
        x0 = -3.7 + i * 1.88
        mag_pouch(g, d + 0.35, x0, x0 + 1.66, 4.2, 8.7)
    pouch(g, 'body', d + 0.35, -3.6, 2.5, -1.4, 4.0, 'pouch', 'flap', t=0.45)          # admin and pistol mag pouches
    pouch(g, 'body', d + 0.35, 1.4, 2.5, 3.6, 4.0, 'pouch', 'flap', t=0.45)
    g.front('body', d + 0.35, -1.2, 2.6, 1.2, 3.8, 'velcro', t=0.06)                  # name tape loop
    waist_strap(g, d, 8.0, 8.6, 'web', buckle='buckle')


def build_load_bearing_vest(g):
    d = VEST_D - 0.1
    for s in (-1, 1):                                                                # broad yoke over each shoulder
        x0, x1 = strap_x(s, 0.9, 3.5)
        g.box('body', (x0, -0.4, -2 - d), (x1, 0.4, 2 + d), 'panel')
        g.front('body', d, x0, 0.4, x1, 6.4, 'panel', t=0.2)
        pouch(g, 'body', d + 0.2, x0 + 0.1, 1.2, x1 - 0.1, 4.4, 'pouch', 'flap', t=0.85)  # grenade / radio pouches
        g.front('body', d + 1.17, (x0 + x1) / 2 - 0.25, 1.4, (x0 + x1) / 2 + 0.25, 2.6, 'tab', t=0.08)
    g.back('body', d, -3.5, 0.4, 3.5, 6.4, 'mesh', t=0.15)                            # mesh back
    g.shell('body', 6.4, 11.0, d, 'panel')                                           # the lower wrap
    g.front('body', d, -0.25, 0.4, 0.25, 11.0, 'zip', t=0.1)
    for s in (-1, 1):                                                                # two big mag pouches a side
        for x0, x1 in ((0.4, 2.1), (2.2, 3.9)):
            a, b = sorted((s * x0, s * x1))
            mag_pouch(g, d, a, b, 6.0, 10.6, t=1.0)
        a, b = sorted((s * 1.0, s * 3.6))                                            # canteen pouches round the back
        g.back('body', d, a, 6.8, b, 10.6, 'pouch', t=0.9)
        g.back('body', d + 0.9, a - 0.05, 6.7, b + 0.05, 7.6, 'flap', t=0.12)
    g.front('body', d + 0.2, -0.9, 4.6, 0.9, 5.2, 'web', t=0.12)                      # chest strap and buckle
    g.front('body', d + 0.32, -0.4, 4.5, 0.4, 5.3, 'buckle', t=0.2)
    g.shell('body', 10.6, 11.6, d + 0.15, 'web')                                     # the pistol belt
    g.front('body', d + 0.15, -0.9, 10.5, 0.9, 11.7, 'buckle', t=0.3)


def build_medic_vest(g):
    d = VEST_D - 0.1
    for s in (-1, 1):
        x0, x1 = strap_x(s, 1.0, 3.4)
        g.box('body', (x0, -0.4, -2 - d), (x1, 0.4, 2 + d), 'mesh')
        g.front('body', d, x0, 0.4, x1, 10.6, 'mesh', t=0.15)
    g.back('body', d, -3.6, 0.4, 3.6, 10.6, 'mesh', t=0.15)
    for s in (-1, 1):
        g.side('body', d, s, -2 - d, 2.4, 2 + d, 10.6, 'mesh', t=0.12)
    g.front('body', d, -0.25, 0.4, 0.25, 10.6, 'zip', t=0.1)
    pouch(g, 'body', d + 0.15, -3.4, 1.4, -1.0, 4.8, 'pouch', 'flap', t=0.9)            # the trauma kit (IFAK)
    g.front('body', d + 1.05, -2.8, 2.3, -1.6, 3.5, 'white', t=0.06)                   # its red-cross patch
    g.front('body', d + 1.11, -2.4, 2.4, -2.0, 3.4, 'cross', t=0.04)
    g.front('body', d + 1.11, -2.75, 2.75, -1.65, 3.05, 'cross', t=0.04)
    for x in (1.2, 2.0, 2.8):                                                         # tourniquets and shears
        g.front('body', d + 0.15, x, 1.0, x + 0.6, 4.4, 'tq', t=0.45)
    g.front('body', d + 0.6, 1.3, 0.6, 1.6, 1.4, 'shears', t=0.15)
    for x0 in (-3.6, -1.6, 0.4, 2.0):                                                 # dump and bandage pouches
        pouch(g, 'body', d + 0.15, x0, 5.6, x0 + 1.5, 9.4, 'pouch', 'flap', t=0.8)
    g.front('body', d + 0.15, -3.6, 9.8, 3.6, 10.6, 'white', t=0.06)                  # MEDIC tape across the bottom
    g.back('body', d + 0.15, -1.4, 2.0, 1.4, 4.8, 'white', t=0.06)                    # the big cross on the back
    g.back('body', d + 0.21, -0.4, 2.2, 0.4, 4.6, 'cross', t=0.04)
    g.back('body', d + 0.21, -1.2, 3.0, 1.2, 3.8, 'cross', t=0.04)


def build_bandolier(g):
    """A canvas ammo sash from the right shoulder to the left hip, front and back."""
    d = VEST_D - 0.4
    g.box('body', (-3.2, -0.35, -2 - d), (-1.6, 0.3, 2 + d), 'canvas')               # over the right shoulder
    for i in range(10):
        x, y = -3.0 + i * 0.62, 0.3 + i * 1.06
        g.front('body', d, x, y, x + 1.6, y + 1.2, 'canvas', t=0.18)
        g.back('body', d, x, y, x + 1.6, y + 1.2, 'canvas', t=0.18)
        if i % 2 == 0 and i < 9:                                                      # a stripper-clip pocket each step
            pouch(g, 'body', d + 0.18, x + 0.1, y + 0.2, x + 1.5, y + 1.7, 'canvas_b', 'flap', t=0.45)
            g.front('body', d + 0.75, x + 0.65, y + 0.5, x + 0.95, y + 0.75, 'snap', t=0.08)
    g.side('body', d, 1, -2 - d, 10.0, 2 + d, 11.4, 'canvas', t=0.18)                 # round the left hip
    g.front('body', d + 0.18, 2.4, 10.1, 3.4, 11.3, 'snap', t=0.15)


def build_heavy_plate_carrier(g):
    d = VEST_D - 0.05
    g.front('body', d, -3.7, 0.9, 3.7, 9.2, 'panel', t=0.7)                         # front and back plates
    g.back('body', d, -3.7, 0.6, 3.7, 9.6, 'panel', t=0.7)
    for s in (-1, 1):
        x0, x1 = strap_x(s, 1.4, 3.6)
        g.box('body', (x0, -0.6, -2 - d - 0.4), (x1, 0.9, 2 + d + 0.4), 'pad')        # padded shoulders
        g.side('body', d, s, -2 - d, 3.0, 2 + d, 9.8, 'panel', t=0.55)                 # side plates in the cummerbund
    g.box('body', (-3.0, -1.4, -2 - d - 1.0), (3.0, 0.4, -2 - d + 0.1), 'pad')        # the throat guard collar
    g.box('body', (-3.6, -1.2, 2 + d - 0.2), (3.6, 0.6, 2 + d + 0.8), 'pad')
    molle(g, 'body', d + 0.7, -3.7, 1.4, 3.7, 4.2, 'web', t=0.08)
    for i in range(3):                                                               # triple mag shingle
        x0 = -3.4 + i * 2.3
        mag_pouch(g, d + 0.7, x0, x0 + 2.0, 4.6, 8.4, t=0.8)
    g.front('body', d + 0.7, -1.4, 1.6, 1.4, 3.0, 'velcro', t=0.06)                  # patch panel
    g.front('body', d + 0.7, -3.6, 8.6, 3.6, 9.2, 'web', t=0.12)
    def leg(p, s):                                                                   # the groin flap and upper-arm guards
        if s > 0:
            g.front(p, PANTS + 0.5, -2.0, -0.4, 2.2, 2.6, 'pad', t=0.5)
        else:
            g.front(p, PANTS + 0.5, -2.2, -0.4, 2.0, 2.6, 'pad', t=0.5)
    g.both(LEGS, leg)
    def arm(p, s):
        g.side(p, JACKET + 0.2, s, -2.3, -1.6, 2.3, 3.0, 'pad', t=0.55)
        g.side(p, JACKET + 0.75, s, -1.6, -1.0, 1.6, 0.0, 'web', t=0.1)
    g.both(ARMS, arm)
    molle(g, 'body', d + 0.7, -3.6, 1.4, 3.6, 8.0, 'web', t=0.08, back=True)
    g.back('body', d + 0.7, -0.6, 0.2, 0.6, 1.2, 'web', t=0.3)                        # drag handle


def build_high_cut_helmet(g):
    d = 0.9
    dome(g, -9.7, -5.2, d, 'shell', ((0, 0), (0.6, 0.5), (1.4, 0.45)))
    for s in (-1, 1):                                                               # cut high over the ears: no sides below
        x0 = s * (HX + d) - (0.35 if s > 0 else 0)
        g.box('head', (x0, -6.6, -2.6), (x0 + 0.35, -5.2, 3.2), 'rail')              # accessory rails
        g.box('head', (x0 - s * 0.05, -6.2, -1.2), (x0 + 0.35 + s * 0.1, -5.7, -0.2), 'light')
        g.box('head', (s * 2.6 - 0.25, -5.2, -1.0), (s * 2.6 + 0.25, -0.2, -0.6), 'strap')
    g.box('head', (-1.0, -6.2, -HX - d - 0.4), (1.0, -5.2, -HX - d), 'shroud')        # the night-vision shroud
    g.box('head', (-2.6, -8.6, -HX - d - 0.08), (2.6, -7.0, -HX - d), 'velcro')
    g.box('head', (-2.4, -9.1, HX + d), (2.4, -7.4, HX + d + 0.08), 'velcro')
    g.box('head', (-1.3, -6.6, HX + d), (1.3, -5.6, HX + d + 0.5), 'pouch')          # counterweight pouch
    g.box('head', (-2.6, -0.2, -1.2), (2.6, 0.2, -0.6), 'strap')
    g.box('head', (-0.5, -0.35, -1.3), (0.5, 0.35, -0.5), 'buckle')


def build_steel_pot_helmet(g):
    d = 1.1
    dome(g, -9.8, -4.9, d, 'cover', ((0, 0), (0.7, 0.55), (1.6, 0.5)))
    ring(g, -5.0, -4.6, d, 'rim', t=0.55)                                            # the rolled rim, flaring out
    g.box('head', (-HX - d - 0.55, -4.6, -1.0), (HX + d + 0.55, -3.6, HX + d + 0.55), 'cover')   # the skirt down the back
    ring(g, -7.0, -6.3, d + 0.02, 'band')                                            # the elastic band
    for x in (-3.0, 0.4, 2.6):                                                       # sprigs and a card under the band
        g.box('head', (x, -7.6, -HX - d - 0.25), (x + 0.5, -6.0, -HX - d - 0.02), 'sprig')
    g.box('head', (-4.3 - d, -7.2, 0.6), (-4.0 - d, -6.1, 1.8), 'card')
    for s in (-1, 1):                                                                # chin strap hanging loose
        x = s * (HX + 0.1) - (0.12 if s < 0 else 0)
        g.box('head', (x, -4.6, -0.6), (x + 0.12, 0.6, -0.1), 'strap')
    g.box('head', (-0.5, 0.4, -0.6), (0.5, 0.75, -0.1), 'buckle')


def build_tanker_helmet(g):
    d = 0.7
    dome(g, -9.2, -4.8, d, 'shell', ((0, 0), (0.6, 0.45), (1.3, 0.4)))
    for x in (-2.2, -0.15, 1.9):                                                     # padded ribs front to back
        g.box('head', (x, -9.5, -3.6), (x + 0.3, -9.1, 4.0), 'rib')
    g.box('head', (-HX - d, -4.8, 0.4), (HX + d, -0.6, HX + d), 'shell')              # down over the back of the head
    for s in (-1, 1):                                                                # ear cups with the radio in them
        x0 = s * (HX + d) - (1.2 if s > 0 else 0)
        g.box('head', (x0, -5.2, -1.6), (x0 + 1.2, -1.4, 1.6), 'cup')
        g.box('head', (x0 + (0.9 if s > 0 else -0.2), -4.4, -1.0), (x0 + (1.5 if s > 0 else 0.3), -2.2, 1.0), 'shell')
    g.box('head', (-HX - d - 1.3, -2.6, -4.2), (-HX - d - 0.9, -2.1, -1.6), 'boom')   # the boom mic
    g.box('head', (-HX - d - 1.3, -2.7, -4.6), (-2.8, -2.0, -4.2), 'mic')
    g.box('head', (-HX - d - 0.6, -1.6, 0.8), (-HX - d - 0.2, 1.8, 1.3), 'cable')      # coiled cord
    g.box('head', (-3.0, -5.4, -HX - d - 0.2), (3.0, -4.8, -HX - d), 'rib')           # the brow pad


def build_boonie_hat(g):
    d = 0.5
    g.box('head', (-4 - d, -9.0, -4 - d), (4 + d, -5.2, 4 + d), 'cloth')
    g.box('head', (-4 - d + 0.4, -9.3, -4 - d + 0.4), (4 + d - 0.4, -9.0, 4 + d - 0.4), 'cloth')
    ring(g, -7.0, -6.2, d + 0.05, 'loops', t=0.12)                                   # the foliage-loop band
    for x in (-3.6, -1.2, 1.2, 3.6):
        g.box('head', (x - 0.2, -7.2, -4 - d - 0.3), (x + 0.2, -6.0, -4 - d - 0.12), 'loops')
    ring(g, -5.3, -4.85, d + 0.05, 'brim', t=1.9)                                    # floppy stitched brim, drooping
    ring(g, -4.85, -4.4, d + 1.95, 'brim', t=0.45)
    for s in (-1, 1):
        g.box('head', (s * 2.4 - 0.2, -8.0, -4 - d - 0.06), (s * 2.4 + 0.2, -7.6, -4 - d), 'eyelet')
        x = s * (HX + d + 0.05) - (0.12 if s < 0 else 0)                              # the chin cord
        g.box('head', (x, -4.4, -0.3), (x + 0.12, 0.4, 0.1), 'cord')
    g.box('head', (-0.4, 0.3, -0.4), (0.4, 0.7, 0.2), 'toggle')


def build_patrol_cap(g):
    d = 0.45
    g.box('head', (-4 - d, -9.4, -4 - d), (4 + d, -5.0, 4 + d), 'cloth')             # flat-topped crown, stiff front
    g.box('head', (-4 - d - 0.05, -6.0, -4 - d - 0.05), (4 + d + 0.05, -5.0, 4 + d + 0.05), 'band')
    g.box('head', (-3.8, -5.4, -7.6), (3.8, -5.0, -4 - d), 'cloth')                  # the short bill
    g.box('head', (-2.8, -5.45, -8.0), (2.8, -5.05, -7.5), 'cloth')
    g.box('head', (-1.0, -8.4, -4 - d - 0.08), (1.0, -6.8, -4 - d), 'rank')
    g.box('head', (-2.4, -9.0, 4 + d), (2.4, -6.4, 4 + d + 0.08), 'name')
    for y in (-9.1, -7.6, -6.1):
        g.box('head', (-4 - d - 0.02, y, -2.0), (-4 - d, y + 0.08, 2.0), 'seam')


def build_beret(g):
    """A wool beret, pulled down to the left (the badge over the right eye): stepped bulges lean it over."""
    d = 0.45
    g.box('head', (-4 - d, -6.2, -4 - d), (4 + d, -5.3, 4 + d), 'band')               # the leather sweatband
    g.box('head', (-4 - d - 0.1, -8.6, -4 - d - 0.1), (4 + d + 0.1, -6.2, 4 + d + 0.1), 'wool')
    g.box('head', (-4 - d - 0.2, -9.1, -3.6), (4.6, -8.6, 4.4), 'wool')
    g.box('head', (0.4, -8.4, -4.6), (4 + d + 1.4, -6.6, 4.6), 'wool')                 # the drape over the left ear
    g.box('head', (4 + d + 0.6, -6.6, -3.6), (4 + d + 1.4, -5.6, 3.6), 'wool')
    g.box('head', (-3.4, -8.0, -4 - d - 0.3), (-1.8, -6.4, -4 - d - 0.1), 'flash')     # cloth flash and cap badge
    g.box('head', (-3.1, -7.8, -4 - d - 0.45), (-2.1, -6.6, -4 - d - 0.3), 'badge')
    g.box('head', (-0.6, -6.0, 4 + d), (0.6, -5.4, 4 + d + 0.4), 'band')              # the tie at the back


def build_ballistic_glasses(g):
    """Wraparound shooting glasses: the clear lens left undrawn so the eyes show, its rim, side shields and swept arms."""
    z = -4.45
    top, bot = EYES_HIGH - 0.35, EYES_LOW + 0.3
    for s in (-1, 1):                                                                # the clear lens's rim
        x0, x1 = (-3.7, -0.4) if s < 0 else (0.4, 3.7)
        g.box('head', (x0, bot, z - 0.2), (x1, bot + 0.1, z - 0.04), 'rim')
    for s in (-1, 1):
        x0 = -4.25 if s < 0 else 3.7
        g.box('head', (x0, top, z - 0.05), (x0 + 0.55, bot - 0.2, z + 1.0), 'lens')    # wrapped round the side
        g.box('head', (x0 - (0.05 if s < 0 else -0.4), top - 0.05, z + 1.0), (x0 + (0.15 if s < 0 else 0.6), top + 0.35, 0.8), 'frame')
    g.box('head', (-3.8, top - 0.18, z - 0.22), (3.8, top, z - 0.02), 'frame')
    g.box('head', (-0.5, bot - 0.5, z - 0.25), (0.5, bot, z - 0.1), 'frame')           # nose pad


def build_ballistic_goggles(g):
    z = -4.4
    top, bot = EYES_HIGH - 0.9, EYES_LOW + 0.8
    g.box('head', (-3.9, top, z - 0.9), (3.9, top + 0.4, z), 'frame')                # foam frame round one wide lens
    g.box('head', (-3.9, bot - 0.4, z - 0.9), (-0.6, bot, z), 'frame')
    g.box('head', (0.6, bot - 0.4, z - 0.9), (3.9, bot, z), 'frame')
    g.box('head', (-0.6, bot - 0.9, z - 0.9), (0.6, bot - 0.2, z), 'frame')          # over the nose
    for s in (-1, 1):
        x0 = -3.9 if s < 0 else 3.4
        g.box('head', (x0, top + 0.4, z - 0.9), (x0 + 0.5, bot - 0.4, z), 'frame')
    g.box('head', (-3.4, top + 0.4, z - 0.95), (3.4, bot - 0.4, z - 0.85), 'lens')
    for x in (-3.0, -1.4, 0.6, 2.2):                                                # vents along the top
        g.box('head', (x, top - 0.02, z - 0.7), (x + 0.6, top + 0.06, z - 0.2), 'vent')
    for s in (-1, 1):                                                                # wide elastic strap
        x = -4.32 if s < 0 else 4.2
        g.box('head', (x, top + 0.2, z), (x + 0.12, bot - 0.2, 4.32), 'strap')
        g.box('head', (x - s * 0.05, top + 0.3, z + 0.5), (x + 0.12 + s * 0.08, bot - 0.3, z + 1.3), 'clip')
    g.box('head', (-4.32, top + 0.2, 4.2), (4.32, bot - 0.2, 4.32), 'strap')


def build_shemagh(g):
    """A shemagh wound round the neck: thick folds, a knot at the back and the fringed ends hanging at the front."""
    d = SHIRT + 0.7
    g.box('body', (-3.8, -1.4, -2 - d), (3.8, 1.0, 2 + d), 'cloth')
    g.box('body', (-4.1, -0.8, -2 - d - 0.35), (4.1, 0.2, 2 + d + 0.35), 'cloth')
    g.box('body', (-3.2, -1.9, -2 - d + 0.2), (3.2, -1.4, 2 + d - 0.2), 'cloth')
    g.box('body', (-1.0, -0.6, 2 + d + 0.3), (1.0, 0.8, 2 + d + 1.0), 'cloth')        # the knot
    g.box('body', (-0.6, 0.8, 2 + d + 0.35), (0.8, 3.6, 2 + d + 0.7), 'cloth')
    g.box('body', (-2.8, 0.8, -2 - d - 0.4), (-0.2, 4.6, -2 - d), 'cloth')            # the triangle over the chest
    g.box('body', (-2.2, 4.6, -2 - d - 0.4), (-0.8, 5.6, -2 - d), 'cloth')
    for x in (-2.1, -1.7, -1.3, -0.9):
        g.box('body', (x, 5.6, -2 - d - 0.3), (x + 0.2, 6.2, -2 - d - 0.1), 'fringe')
    g.box('body', (-4.1, -0.25, -2 - d - 0.4), (4.1, -0.05, -2 - d - 0.33), 'stripe')


def build_neck_gaiter(g):
    d = SHIRT + 0.25
    g.box('body', (-3.0, -1.6, -2 - d), (3.0, 1.0, 2 + d), 'cloth')                   # a tube scrunched round the neck
    for y in (-1.0, -0.1):
        g.box('body', (-3.1, y, -2 - d - 0.12), (3.1, y + 0.3, 2 + d + 0.12), 'cloth')
    g.box('body', (-3.2, 0.6, -2 - d - 0.1), (3.2, 1.2, 2 + d + 0.1), 'hem')


def build_knee_pads(g):
    def leg(p, s):
        g.front(p, PANTS + 0.1, -1.9, 4.2, 1.9, 7.6, 'foam', t=0.35)                    # foam backing
        g.front(p, PANTS + 0.45, -1.6, 4.5, 1.6, 7.2, 'cap', t=0.4)                     # the hard cap
        g.front(p, PANTS + 0.85, -1.0, 4.9, 1.0, 6.8, 'cap', t=0.15)
        for y in (4.4, 6.8):                                                          # elastic straps round the back
            g.shell(p, y, y + 0.55, PANTS + 0.1, 'strap')
        g.side(p, PANTS + 0.65, s, -1.4, 6.65, -0.7, 7.3, 'buckle', t=0.15)
    g.both(LEGS, leg)


def build_elbow_pads(g):
    def arm(p, s):
        g.back(p, JACKET + 0.1, -1.9, 3.2, 1.9, 6.2, 'foam', t=0.3)                     # the elbow points back
        g.back(p, JACKET + 0.4, -1.5, 3.5, 1.5, 5.9, 'cap', t=0.35)
        g.side(p, JACKET + 0.1, s, 0.4, 3.4, 2 + JACKET + 0.4, 6.0, 'foam', t=0.25)
        for y in (3.2, 5.6):
            g.shell(p, y, y + 0.5, JACKET + 0.1, 'strap')
    g.both(ARMS, arm)


def build_field_shirt(g):
    """A field blouse in camo: collar, four pockets, sleeves with elbow patches; worn tucked or over the trousers."""
    d = SHIRT + 0.05
    g.shell('body', 0, 12, d, 'cloth')
    sleeves(g, d, 'cloth', end=8.0, cuff='cuff', cuff_d=0.12)
    collar_flaps(g, d, 'collar')
    g.front('body', d, -0.5, 0, 0.5, 12, 'placket', t=0.12)
    buttons(g, 'body', d + 0.12, 0, (1.4, 3.6, 5.8, 8.0, 10.2), 'button')
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.0, s * 3.5))
        pouch(g, 'body', d, x0, 1.8, x1, 4.8, 'pocket', 'flap', t=0.25)               # slanted-flap chest pockets
        pouch(g, 'body', d, x0, 7.4, x1, 11.0, 'pocket', 'flap', t=0.25)              # lower pockets
    g.front('body', d + 0.25, 1.2, 1.3, 3.3, 1.8, 'tape', t=0.08)                      # name and service tapes
    g.front('body', d + 0.25, -3.3, 1.3, -1.2, 1.8, 'tape', t=0.08)
    def arm(p, s):
        g.back(p, d, -1.6, 2.8, 1.6, 5.6, 'pocket', t=0.12)                            # elbow patches
        g.side(p, d, s, -1.4, -0.6, 1.4, 1.6, 'patch', t=0.08)                         # shoulder patch
    g.both(ARMS, arm)


def build_combat_trousers(g):
    d = PANTS
    trouser_top(g, d, 'cloth')
    g.shell('body', 10.3, 11.1, d + 0.12, 'web')
    g.front('body', d + 0.12, -0.8, 10.1, 0.8, 11.3, 'buckle', t=0.2)
    def leg(p, s):
        g.shell(p, 0, 10.6, d, 'cloth')
        g.shell(p, 10.0, 11.0, d + 0.2, 'blouse')                                     # bloused into the boots
        cargo_pocket(g, p, s, d, 'cloth', 'pocket')
        g.front(p, d, -1.9, 4.4, 1.9, 7.8, 'knee', t=0.22)                            # knee-pad sleeves
        g.front(p, d + 0.22, -1.2, 5.0, 1.2, 7.2, 'insert', t=0.15)
        g.side(p, d, s, -1.4, 7.6, 0.6, 9.8, 'pocket', t=0.3)                          # calf pocket
        g.back(p, d, -1.5, 0.6, 1.5, 3.6, 'pocket', t=0.14)
        g.front(p, d, -1.9, 10.4, 1.9, 10.8, 'tie', t=0.06)                            # the drawcord at the ankle
    g.both(LEGS, leg)


def build_tactical_boots(g):
    def foot(p, s):
        shoe(g, p, s, 6.2, sole='lugsole', toe=3.0)                                    # cut lower than a combat boot
        g.shell(p, 6.0, 6.8, BOOT + 0.15, 'collar')
        g.side(p, BOOT, s, -1.9, 7.0, 1.9, 9.6, 'suede', t=0.08)                       # suede and nylon panels
        g.side(p, BOOT, -s, -0.15, 6.8, 0.15, 10.0, 'zip', t=0.12)                      # side zip, inner side
        laces(g, p, BOOT, 7.0, 10.2, 0.75)
        g.box(p, (-2.25, 10.1, -5.2), (2.25, 11.42, -3.7), 'toecap')
        g.box(p, (-2.4, 11.6, -5.1), (2.4, 12.4, 2.45), 'lugsole')
        g.back(p, BOOT, -0.4, 6.0, 0.4, 7.2, 'collar', t=0.2)                           # pull tab
    g.both(LEGS, foot)


def _web(c='#4a5038'):
    return cloth(c, weave=0.14, stitch=None)


MILITARY = {
    'chest_rig': (build_chest_rig, dict(panel=Camo(), pouch=Camo(0.82), flap=Camo(0.72), tab=_web(), web=_web(),
                                        buckle=plastic('#1e1f1c'), velcro=cloth('#4a4c3a', stitch=None)), 0.5),
    'load_bearing_vest': (build_load_bearing_vest, dict(panel=Camo(), pouch=Camo(0.82), flap=Camo(0.72), tab=_web(),
                                                        mesh=mesh('#3e4430'), zip=metal(), web=_web(), buckle=plastic('#1e1f1c')), 0.5),
    'medic_vest': (build_medic_vest, dict(mesh=mesh('#7a6a4c'), pouch=cloth('#7a6a4c', weave=0.12), flap=cloth('#6e5f44'),
                                          zip=metal(), white=flat('#c9c6bb'), cross=flat('#8a2a22'), tq=cloth('#1e1e1c'),
                                          shears=plastic('#2e4a6a')), 0.5),
    'bandolier': (build_bandolier, dict(canvas=cloth('#6a6448', weave=0.16), canvas_b=cloth('#625c42', weave=0.16),
                                        flap=cloth('#5a553c'), snap=metal('brass')), 0.55),
    'heavy_plate_carrier': (build_heavy_plate_carrier, dict(panel=Camo(), pouch=Camo(0.82), flap=Camo(0.72), pad=Camo(0.92),
                                                            tab=_web(), web=_web(), velcro=cloth('#4a4c3a', stitch=None)), 0.55),
    'high_cut_helmet': (build_high_cut_helmet, dict(shell=Camo(weave=0.04), rail=plastic('#262724'), light=plastic('#1a1a1a'),
                                                    strap=_web('#2e2f2a'), shroud=metal('steel'), velcro=cloth('#4a4c3a', stitch=None),
                                                    pouch=Camo(0.9), buckle=plastic('#111111')), 0.45),
    'steel_pot_helmet': (build_steel_pot_helmet, dict(cover=Camo(weave=0.1), rim=cloth('#4a4c36'), band=rubber('#3a3b30'),
                                                      sprig=fur('#4a5530'), card=flat('#c8c0a8'), strap=_web('#4a4c36'),
                                                      buckle=metal()), 0.55),
    'tanker_helmet': (build_tanker_helmet, dict(shell=cloth('#545a3c', weave=0.06), rib=cloth('#4a5034', weave=0.06),
                                                cup=plastic('#2e3026'), boom=metal('steel'), mic=rubber('#1a1a1a'),
                                                cable=rubber('#1e1e1e')), 0.5),
    'boonie_hat': (build_boonie_hat, dict(cloth=Camo(), brim=Camo(0.92), loops=Camo(0.85), eyelet=metal('brass'),
                                          cord=flat('#2e2e28'), toggle=plastic('#1e1e1e')), 0.45),
    'patrol_cap': (build_patrol_cap, dict(cloth=Camo(), band=Camo(0.9), rank=flat('#2a2a26'), name=Camo(0.85),
                                          seam=flat('#2a2a22', ink=False)), 0.35),
    'beret': (build_beret, dict(wool=knit('#3a4a2e'), band=leather('#1e1812'), flash=cloth('#6a2a24', stitch=None),
                                badge=metal('brass')), 0.3),
    'ballistic_glasses': (build_ballistic_glasses, dict(lens=plastic('#4a4e50', gloss=0.4), frame=plastic('#1c1d1f'),
                                                        rim=plastic('#6a6e70', gloss=0.4)), 0.15),
    'ballistic_goggles': (build_ballistic_goggles, dict(lens=lens, frame=plastic('#6a5e48'), vent=flat('#121210', ink=False),
                                                        strap=cloth('#5a5440', stitch=None), clip=plastic('#1e1e1e')), 0.4),
    'shemagh': (build_shemagh, dict(cloth=checker('#d2cbb8', '#2c2a28'), fringe=flat('#c8c0aa'), stripe=flat('#2c2a28')), 0.4),
    'neck_gaiter': (build_neck_gaiter, dict(cloth=Camo(weave=0.03), hem=Camo(0.9, weave=0.03)), 0.35),
    'knee_pads': (build_knee_pads, dict(foam=cloth('#2e302a', weave=0.06), cap=plastic('#24251f', gloss=0.22),
                                        strap=_web('#2a2b26'), buckle=plastic('#111111')), 0.5),
    'elbow_pads': (build_elbow_pads, dict(foam=cloth('#2e302a', weave=0.06), cap=plastic('#24251f', gloss=0.22),
                                          strap=_web('#2a2b26')), 0.5),
    'field_shirt': (build_field_shirt, dict(cloth=Camo(), collar=Camo(0.95), placket=Camo(0.95), cuff=Camo(0.92),
                                            pocket=Camo(0.95), flap=Camo(0.9), patch=Camo(0.9), button=flat('#2a2a22'),
                                            tape=flat('#c2b99e')), 0.45),
    'combat_trousers': (build_combat_trousers, dict(cloth=Camo(), pocket=Camo(0.95), knee=Camo(0.9), insert=plastic('#2a2b26'),
                                                    blouse=Camo(0.9), loop=Camo(0.9), web=_web('#2e2f2a'), buckle=metal(),
                                                    tie=flat('#2e2e28')), 0.5),
    'tactical_boots': (build_tactical_boots, dict(lugsole=rubber('#1c1a18', stud=True), sole=rubber('#1c1a18'),
                                                  upper=leather('#7a6448'), collar=leather('#5e4c36'), suede=cloth('#8a7454', weave=0.04),
                                                  tongue=leather('#6e5a40'), lace=flat('#4a3e2e'), zip=metal(), toecap=leather('#5e4c36')), 0.5),
}



# ------------------------------------------------------------------ wave 2, military batch 2: cold, wet, CBRN, aircrew and riot
# Refs: refs/items/clothing (fur-ushanka, cbrn-mask-hood, cbrn-suit, combat-shirt-ubacs, fleece-liner-jacket,
# wet-weather-jacket-military, flight-coverall, sniper-smock, arctic-parka-white-camo, snow-overwhites, jungle-boots,
# desert-boots, arctic-boots-mukluk, flight-gloves-nomex, shooting-gloves, arctic-mittens, riot-armour-set).


def build_fur_ushanka(g):
    d = 0.6
    g.box('head', (-4 - d, -9.6, -4 - d), (4 + d, -5.4, 4 + d), 'felt')               # the crown
    g.box('head', (-4 - d - 0.6, -7.4, -4 - d - 0.7), (4 + d + 0.6, -5.2, -4 - d), 'fur')   # front flap turned up
    for s in (-1, 1):                                                                # ear flaps tied up over the crown
        x0 = s * (4 + d) - (0.8 if s > 0 else 0)
        g.box('head', (x0, -8.8, -3.4), (x0 + 0.8, -4.6, 3.6), 'fur')
        g.box('head', (x0 + (0.4 if s > 0 else -0.1), -9.0, -0.4), (x0 + (0.9 if s > 0 else 0.4), -8.4, 0.4), 'tie')
    g.box('head', (-4 - d - 0.3, -8.2, 4 + d), (4 + d + 0.3, -4.4, 4 + d + 0.8), 'fur')   # the back flap
    g.box('head', (-0.5, -6.8, -4 - d - 0.85), (0.5, -5.9, -4 - d - 0.7), 'badge')
    g.box('head', (-4 - d + 0.6, -10.0, -4 - d + 0.6), (4 + d - 0.6, -9.6, 4 + d - 0.6), 'felt')


def build_watch_cap(g):
    d = 0.3
    g.box('head', (-4 - d, -8.8, -4 - d), (4 + d, -5.2, 4 + d), 'knit')
    g.box('head', (-4 - d - 0.25, -6.6, -4 - d - 0.25), (4 + d + 0.25, -5.0, 4 + d + 0.25), 'rib')   # the turn-up
    g.box('head', (-3.6, -9.2, -3.6), (3.6, -8.8, 3.6), 'knit')


def build_cbrn_mask(g):
    """A respirator with its hood: rubber face, two eye lenses, the side canister and a hood down over the shoulders."""
    d = 0.5
    g.box('head', (-4 - d, -8 - d, -4 + 0.6), (4 + d, 0.8, 4 + d), 'hood')           # the hood over head and neck
    eye_open_front(g, -4 - d, -4 + 0.6, -8 - d, -3.6, 4 + d, 'hood', port=(3.6, EYES_HIGH - 0.8, -3.6))
    z0, z1 = -4.95, -4.3
    g.box('head', (-3.9, -3.6, z0), (3.9, 0.6, z1), 'rubber')                         # the face piece, below the eyes
    for s in (-1, 1):                                                                # lens rims round the open eyes
        x0, x1 = (-3.5, -0.5) if s < 0 else (0.5, 3.5)
        g.box('head', (x0 - 0.2, EYES_HIGH - 0.6, z0 - 0.3), (x1 + 0.2, EYES_HIGH - 0.3, z1), 'rim')
        g.box('head', (x0 - 0.2, EYES_LOW + 0.3, z0 - 0.3), (x1 + 0.2, EYES_LOW + 0.6, z1), 'rim')
        g.box('head', (x0 - 0.2, EYES_HIGH - 0.3, z0 - 0.3), (x0 + 0.1, EYES_LOW + 0.3, z1), 'rim')
        g.box('head', (x1 - 0.1, EYES_HIGH - 0.3, z0 - 0.3), (x1 + 0.2, EYES_LOW + 0.3, z1), 'rim')
    g.box('head', (-1.1, -2.4, z0 - 0.6), (1.1, -0.2, z0), 'rubber')                  # outlet valve
    g.box('head', (-0.6, -2.0, z0 - 0.9), (0.6, -0.6, z0 - 0.6), 'rim')
    g.box('head', (-4.6, -2.6, z0 - 0.4), (-2.8, -0.4, z0 + 0.6), 'rubber')           # the canister port, left cheek
    g.box('head', (-6.4, -2.9, z0 - 1.0), (-4.4, -0.1, z0 + 1.0), 'canister')
    g.box('head', (-4 - d - 0.6, 0.2, -4 - d - 0.6), (4 + d + 0.6, 1.6, 4 + d + 0.6), 'hood')   # the skirt at the collar


def build_cbrn_suit(g):
    """A protective overgarment: hooded smock over trousers, taped cuffs, the mask carrier on the hip."""
    d = JACKET + 0.15
    g.shell('body', 0, 12, d, 'suit')
    g.box('body', (-3.8, -2.2, 0.0), (3.8, 1.0, 2 + d + 1.1), 'suit')                # the hood down
    g.front('body', d, -0.8, -0.4, 0.8, 11.0, 'suit', t=0.3)                          # storm flap
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.2, s * 3.6))
        g.front('body', d, x0, 7.0, x1, 10.4, 'suit', t=0.35)
        g.front('body', d + 0.35, x0 - 0.05, 6.8, x1 + 0.05, 7.8, 'flap', t=0.12)
    g.shell('body', 10.6, 12.0, d + 0.1, 'suit')
    g.front('body', d, -3.8, 1.6, -1.6, 2.2, 'tape', t=0.06)                          # the detector-paper patch
    def arm(p, s):
        g.shell(p, -2.0, 8.6, d, 'suit')
        g.shell(p, 7.6, 8.6, d + 0.12, 'tape')                                       # cuffs taped to the gloves
        g.side(p, d, s, -1.2, 0.6, 1.2, 1.6, 'tape', t=0.06)
    g.both(ARMS, arm)
    def leg(p, s):
        g.shell(p, -0.2, 11.0, PANTS + 0.35, 'suit')
        g.shell(p, 10.0, 11.0, PANTS + 0.5, 'tape')
        g.side(p, PANTS + 0.35, s, -1.6, 3.0, 1.0, 6.0, 'flap', t=0.3)                # thigh pocket
    g.both(LEGS, leg)
    g.side('body', d, 1, -1.4, 8.2, 1.4, 12.2, 'bag', t=0.9)                          # the mask carrier on the left hip


def build_combat_shirt(g):
    """A combat shirt worn under armour: plain wicking torso, camo sleeves with pockets, a zip mock neck."""
    d = SHIRT + 0.05
    g.shell('body', 0, 12, d, 'torso')
    g.box('body', (-3.0, -1.0, -2 - d - 0.25), (3.0, 0.4, 2 + d + 0.2), 'sleeve')    # the camo mock neck
    g.front('body', d, -0.25, -1.0, 0.25, 3.0, 'zip', t=0.12)
    def arm(p, s):
        g.shell(p, -2.0, 8.0, d, 'sleeve')
        g.shell(p, 7.4, 8.2, d + 0.1, 'cuff')
        g.side(p, d, s, -1.6, 0.0, 1.4, 2.8, 'pocket', t=0.25)                        # shoulder pockets
        g.side(p, d + 0.25, s, -1.7, -0.2, 1.5, 0.6, 'flap', t=0.1)
        g.side(p, d + 0.35, s, -1.2, 0.8, 1.0, 2.2, 'velcro', t=0.05)
        g.back(p, d, -1.6, 3.0, 1.6, 5.4, 'pad', t=0.15)                              # elbow pad pocket
    g.both(ARMS, arm)


def build_fleece_liner(g):
    d = JACKET - 0.05
    g.shell('body', 0, 11.6, d, 'fleece')
    g.shell('body', 11.2, 12.2, d + 0.12, 'binding')
    g.box('body', (-3.2, -1.6, -2 - d - 0.25), (3.2, 0.3, 2 + d + 0.25), 'fleece')   # tall collar
    g.front('body', d, -0.25, -1.6, 0.25, 12.0, 'zip', t=0.14)
    for s in (-1, 1):
        g.front('body', d, s * 1.6 - 0.1, 7.0, s * 1.6 + 0.1, 10.0, 'zip', t=0.08)    # zipped hand pockets
    g.front('body', d, 1.4, 2.4, 3.2, 4.8, 'velcro', t=0.08)
    sleeves(g, d, 'fleece', end=8.0, cuff='binding', cuff_d=0.12)
    g.both(ARMS, lambda p, s: g.side(p, d, s, -1.2, 0.4, 1.2, 2.0, 'velcro', t=0.06))


def build_wet_weather_jacket(g):
    d = JACKET + 0.05
    g.shell('body', 0, 12, d, 'shell')
    g.box('body', (-3.4, -1.8, -2 - d - 0.3), (3.4, 0.2, 2 + d + 0.3), 'shell')      # high collar, the hood in it
    g.box('body', (-3.0, -2.4, 1.0), (3.0, -1.6, 2 + d + 0.4), 'shell')
    g.front('body', d, -0.9, -1.6, 0.9, 12, 'shell', t=0.3)                            # storm flap with snaps
    buttons(g, 'body', d + 0.3, 0, (1.0, 3.6, 6.2, 8.8, 11.0), 'snap')
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.2, s * 3.7))
        g.front('body', d, x0, 7.4, x1, 10.8, 'shell', t=0.3)                        # slash pockets with flaps
        g.front('body', d + 0.3, x0 - 0.1, 7.2, x1 + 0.1, 8.0, 'flap', t=0.12)
        g.front('body', d, s * 3.8 - 0.25, 2.0, s * 3.8 + 0.25, 6.0, 'zip', t=0.1)   # pit zips
    g.shell('body', 5.6, 5.95, d + 0.05, 'seam')                                     # taped seams
    def arm(p, s):
        g.shell(p, -2.0, 8.2, d, 'shell')
        g.shell(p, 7.4, 8.4, d + 0.1, 'flap')                                        # velcro cuff tabs
        g.shell(p, 1.4, 1.75, d + 0.03, 'seam')
    g.both(ARMS, arm)


def build_flight_coverall(g):
    """A one-piece flight suit: zips everywhere, pen pockets on the sleeve, name tag, worn as the outer layer."""
    d = JACKET - 0.15
    g.shell('body', 0, 12, d, 'suit')
    collar_flaps(g, d, 'suit')
    g.front('body', d, -0.25, 0.2, 0.25, 12.0, 'zip', t=0.12)
    for s in (-1, 1):                                                                 # zipped chest pockets, slanted
        x0, x1 = sorted((s * 1.4, s * 3.4))
        g.front('body', d, x0, 2.0, x1, 4.6, 'pocket', t=0.15)
        g.front('body', d + 0.15, x0, 2.0, x1, 2.2, 'zip', t=0.06)
    g.front('body', d + 0.15, -3.4, 1.2, -1.5, 1.8, 'name', t=0.06)
    g.shell('body', 10.4, 11.0, d + 0.06, 'tab')                                       # waist adjusters
    def arm(p, s):
        g.shell(p, -2.0, 8.4, d, 'suit')
        g.shell(p, 7.8, 8.6, d + 0.1, 'tab')
        g.side(p, d, s, -1.4, 1.2, 1.0, 4.0, 'pocket', t=0.2)                         # pen pockets on the left sleeve
        for i in range(3):
            g.side(p, d + 0.2, s, -1.2 + i * 0.6, 0.8, -0.95 + i * 0.6, 1.6, 'pen', t=0.08)
        g.side(p, d, s, -1.2, 5.0, 1.2, 5.3, 'zip', t=0.06)
    g.both(ARMS, arm)
    def leg(p, s):
        g.shell(p, -0.2, 11.2, PANTS + 0.3, 'suit')
        g.side(p, PANTS + 0.3, s, -1.8, 2.4, 1.6, 6.4, 'pocket', t=0.25)              # thigh pockets
        g.side(p, PANTS + 0.55, s, -1.8, 2.4, 1.6, 2.65, 'zip', t=0.06)
        g.front(p, PANTS + 0.3, -1.8, 6.6, 1.4, 9.4, 'pocket', t=0.2)                 # shin pocket
        g.side(p, PANTS + 0.3, -s, -0.12, 8.4, 0.12, 11.2, 'zip', t=0.08)             # ankle zips
    g.both(LEGS, leg)


def build_sniper_smock(g):
    """A camo smock with a scrim net yoke and burlap strips sewn on the shoulders, not a full ghillie."""
    import random
    rnd = random.Random(11)
    d = JACKET + 0.05
    g.shell('body', 0, 12, d, 'cloth')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 2.6, d + 0.05, 'cloth'))
    g.box('body', (-3.8, -2.0, 0.0), (3.8, 1.0, 2 + d + 1.0), 'cloth')                # the hood down
    g.front('body', d, -0.8, -0.4, 0.8, 12, 'cloth', t=0.25)
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.1, s * 3.7))
        pouch(g, 'body', d, x0, 6.6, x1, 10.4, 'pocket', 'flap', t=0.4)
    g.shell('body', 0, 5.0, d + 0.12, 'scrim')                                        # the net yoke
    sleeves(g, d, 'cloth', end=8.0, cuff='flap', cuff_d=0.12)
    g.both(ARMS, lambda p, s: g.shell(p, -2.0, 3.0, d + 0.12, 'scrim'))
    for _ in range(26):                                                               # hessian strips on the yoke
        part = rnd.choice(('body', 'body', 'right_arm', 'left_arm'))
        y = rnd.uniform(-1.6 if part == 'body' else -2.0, 4.0 if part == 'body' else 2.4)
        w, L = rnd.uniform(0.4, 1.0), rnd.uniform(1.2, 2.6)
        m = rnd.choice(('jute', 'jute2'))
        dd = d + 0.12
        if part == 'body':
            x = rnd.uniform(-4 - dd, 4 + dd - w)
            (g.front if rnd.random() < 0.5 else g.back)(part, dd, x, y, x + w, y + L, m, t=rnd.uniform(0.15, 0.4))
        else:
            s = -1 if part.startswith('right') else 1
            z = rnd.uniform(-2.0, 2.0 - w)
            g.side(part, dd, s, z, y, z + w, y + L, m, t=rnd.uniform(0.15, 0.4))


def build_arctic_parka(g):
    """A white-camo cold weather parka: long, a fur-ruffed hood down, a drawcord waist."""
    d = 1.15
    g.shell('body', 0, 12, d, 'cloth')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 4.4, d + 0.05, 'cloth'))
    g.front('body', d, -0.9, -0.2, 0.9, 12, 'cloth', t=0.28)
    buttons(g, 'body', d + 0.28, 0, (1.5, 4.5, 7.5, 10.5), 'toggle')
    g.box('body', (-3.9, -2.4, 0.0), (3.9, 1.6, 2 + d + 1.3), 'cloth')
    g.box('body', (-4.3, -2.9, 0.2), (4.3, -1.7, 2 + d + 1.6), 'fur')
    for s in (-1, 1):
        g.box('body', (s * 2.6 - (1.7 if s < 0 else 0), -2.1, -2 - d - 0.4), (s * 2.6 + (1.7 if s > 0 else 0), 0.8, 0.6), 'fur')
        x0, x1 = sorted((s * 1.3, s * 3.8))
        pouch(g, 'body', d, x0, 7.6, x1, 11.0, 'cloth', 'flap', t=0.4)
    g.shell('body', 6.4, 6.8, d + 0.06, 'cord')
    def arm(p, s):
        g.shell(p, -2.0, 8.0, d, 'cloth')
        g.shell(p, 7.6, 9.0, d + 0.15, 'cuff')
    g.both(ARMS, arm)


def build_snow_overwhites(g):
    """White over-trousers for snow, pulled on over the uniform, with ties at the ankle."""
    d = PANTS + 0.3
    g.shell('body', 9.6, 12, d, 'cloth')
    g.shell('body', 9.4, 10.2, d + 0.1, 'cord')
    def leg(p, s):
        g.shell(p, 0, 11.0, d, 'cloth')
        g.shell(p, 10.2, 11.0, d + 0.15, 'cord')
        g.front(p, d, -1.8, 4.4, 1.8, 7.0, 'patch', t=0.12)
        g.side(p, d, s, -0.2, 0.2, 0.2, 9.0, 'seam', t=0.05)
    g.both(LEGS, leg)


def build_jungle_boots(g):
    def foot(p, s):
        shoe(g, p, s, 4.4, sole='lugsole', toe=3.0)
        g.side(p, BOOT, s, -1.9, 5.0, 1.9, 9.6, 'canvas', t=0.08)                    # canvas uppers
        g.side(p, BOOT, -s, -1.9, 5.0, 1.9, 9.6, 'canvas', t=0.08)
        g.shell(p, 4.2, 5.0, BOOT + 0.15, 'collar')
        laces(g, p, BOOT, 5.0, 10.4, 0.8)
        for y in (9.2, 9.8):                                                        # the drain eyelets
            g.side(p, BOOT + 0.08, -s, -0.3, y, 0.3, y + 0.3, 'eyelet', t=0.08)
        g.box(p, (-2.2, 10.2, -5.2), (2.2, 11.42, -3.8), 'toecap')
        g.box(p, (-2.45, 11.6, -5.15), (2.45, 12.4, 2.5), 'lugsole')
    g.both(LEGS, foot)


def build_desert_boots(g):
    def foot(p, s):
        shoe(g, p, s, 4.6, sole='lugsole', toe=3.0)
        g.shell(p, 4.4, 5.2, BOOT + 0.15, 'collar')
        g.side(p, BOOT, s, -1.9, 5.4, 1.9, 9.8, 'nylon', t=0.08)
        laces(g, p, BOOT, 5.2, 10.4, 0.8)
        g.box(p, (-2.2, 10.2, -5.2), (2.2, 11.42, -3.8), 'toecap')
        g.box(p, (-2.45, 11.6, -5.15), (2.45, 12.4, 2.5), 'lugsole')
    g.both(LEGS, foot)


def build_arctic_boots(g):
    """Mukluks: tall, soft, fur-cuffed, the moccasin foot puckered round the toe and tied at the ankle."""
    def foot(p, s):
        shoe(g, p, s, 2.6, d=0.8, toe=3.2, upper='hide', sole='sole')
        g.shell(p, 2.0, 3.2, 1.15, 'fur')                                            # the fur cuff
        g.shell(p, 8.4, 8.9, 0.85, 'tie')                                            # ankle wraps
        g.box(p, (-2.4, 10.0, -5.3), (2.4, 11.4, -4.6), 'pucker')
        for x in (-1.6, -0.6, 0.4, 1.4):
            g.box(p, (x, 10.4, -5.4), (x + 0.3, 11.2, -5.3), 'tie')
        g.side(p, 0.8, s, -1.6, 3.6, 1.6, 7.8, 'bead', t=0.06)                         # beaded panel
    g.both(LEGS, foot)


def build_flight_gloves(g):
    def hand(p, s):
        glove(g, p, s, 'nomex', d=0.35, cuff='nomex', cuff_y=(6.4, 8.6), cuff_d=0.5)    # long thin gauntlet
        g.shell(p, 9.8, 10.4, 0.4, 'palm')
        g.side(p, 0.35, s, -1.6, 8.6, 1.6, 9.6, 'seam', t=0.04)
    g.both(ARMS, hand)


def build_shooting_gloves(g):
    """Trigger-finger mitts: a mitten with the forefinger split out, camo shell, knit wrist."""
    def hand(p, s):
        glove(g, p, s, 'shell', d=0.6, cuff='knit', cuff_y=(7.2, 8.8), cuff_d=0.8)
        g.front(p, 0.6, -2.0 if s < 0 else 1.0, 10.6, -1.0 if s < 0 else 2.0, 11.3, 'shell', t=0.5)   # the trigger finger
        g.side(p, 0.6, s, -1.6, 8.6, 1.6, 8.9, 'seam', t=0.05)
    g.both(ARMS, hand)


def build_arctic_mittens(g):
    """Big gauntlet mittens: leather palm, fur back, the cuff to mid-forearm, a lanyard round the neck."""
    def hand(p, s):
        glove(g, p, s, 'leather', d=0.85, cuff='canvas', cuff_y=(5.2, 8.6), cuff_d=1.0)
        g.side(p, 0.85, s, -2.0, 8.6, 2.0, 10.6, 'fur', t=0.4)
        g.shell(p, 5.2, 5.7, 1.1, 'cord')
    g.both(ARMS, hand)
    for s in (-1, 1):                                                                # the idiot cord across the shoulders
        x0, x1 = sorted((s * 3.0, s * 3.5))
        g.box('body', (x0, -0.3, -2 - JACKET - 0.2), (x1, 0.15, 2 + JACKET + 0.2), 'cord')
        g.front('body', JACKET + 0.2, x0, 0.15, x1, 6.0, 'cord', t=0.12)


def build_riot_arm_guards(g):
    """Hard shells over the forearm and upper arm, a cap on the elbow, strapped over the sleeve."""
    def arm(p, s):
        d = JACKET + 0.15
        g.side(p, d, s, -2.4, -1.8, 2.4, 3.0, 'shell', t=0.45)                       # shoulder and upper arm
        g.front(p, d, -2.0, 4.8, 2.0, 8.4, 'shell', t=0.4)                           # forearm plate
        g.side(p, d, s, -2.0, 4.8, 2.0, 8.4, 'shell', t=0.35)
        g.back(p, d, -1.8, 3.0, 1.8, 5.0, 'cap', t=0.5)                              # elbow cup
        for y in (0.2, 5.6, 7.6):
            g.shell(p, y, y + 0.5, d, 'strap')
        g.side(p, d + 0.45, s, -1.6, -1.2, 1.6, -0.6, 'vent', t=0.04)
    g.both(ARMS, arm)


def build_riot_leg_guards(g):
    """Thigh plates, a knee cup and shin shells over the trousers, Velcro-strapped."""
    def leg(p, s):
        d = PANTS + 0.2
        g.front(p, d, -2.0, 0.4, 2.0, 4.0, 'shell', t=0.4)                           # thigh plate
        g.side(p, d, s, -1.8, 0.4, 1.8, 3.6, 'shell', t=0.35)
        g.front(p, d, -1.8, 4.2, 1.8, 6.6, 'cap', t=0.55)                            # knee cup
        g.front(p, d, -2.0, 6.8, 2.0, 10.6, 'shell', t=0.45)                         # shin shell
        g.side(p, d, s, -1.8, 6.8, 1.6, 10.4, 'shell', t=0.35)
        for y in (1.0, 3.2, 7.4, 9.8):
            g.shell(p, y, y + 0.5, d, 'strap')
        g.front(p, d + 0.45, -1.2, 8.0, 1.2, 8.4, 'vent', t=0.04)
    g.both(LEGS, leg)


def build_wool_commando_sweater(g):
    """A ribbed wool pullover with cloth patches on the shoulders and elbows, epaulettes and a pen pocket."""
    d = JACKET - 0.05
    g.shell('body', 0, 10.8, d, 'knit')
    g.shell('body', 10.5, 12.2, d + 0.12, 'rib')
    g.box('body', (-2.8, -0.9, -2 - d - 0.2), (2.8, 0.3, 2 + d + 0.2), 'rib')         # crew neck
    g.front('body', d, 1.2, 2.2, 3.0, 3.8, 'patch', t=0.08)                           # pen pocket
    sleeves(g, d, 'knit', end=7.8, cuff='rib', cuff_d=0.12)
    def arm(p, s):
        g.shell(p, -2.0, 1.4, d + 0.08, 'patch')                                      # shoulder patches
        g.back(p, d, -1.6, 3.2, 1.6, 5.6, 'patch', t=0.1)                              # elbow patches
        g.side(p, d + 0.08, s, -0.6, -2.1, 0.6, 1.4, 'epaulette', t=0.1)
    g.both(ARMS, arm)


MILITARY_2 = {
    'fur_ushanka': (build_fur_ushanka, dict(felt=cloth('#5a5c5e', weave=0.05), fur=fur('#6e6e6c'), tie=flat('#2a2a28'),
                                            badge=metal('brass')), 0.4),
    'watch_cap': (build_watch_cap, dict(knit=knit('#2e3226'), rib=rib('#2a2e22')), 0.3),
    'cbrn_mask': (build_cbrn_mask, dict(hood=cloth('#3a3e30', weave=0.05, stitch=None), rubber=rubber('#1e1f1d'),
                                        rim=metal('steel'), canister=plastic('#4a4f3e')), 0.4),
    'cbrn_suit': (build_cbrn_suit, dict(suit=Camo(weave=0.04), flap=Camo(0.85), tape=flat('#3a3a36'), bag=cloth('#4a4e38')), 0.4),
    'combat_shirt': (build_combat_shirt, dict(torso=cloth('#8a7a5a', weave=0.04, stitch=None), sleeve=Camo(), cuff=Camo(0.88),
                                              pocket=Camo(0.85), flap=Camo(0.75), velcro=cloth('#4a4c3a', stitch=None),
                                              pad=Camo(0.8), zip=metal()), 0.45),
    'fleece_liner': (build_fleece_liner, dict(fleece=fur('#4a4c3a'), binding=cloth('#3a3c2e', stitch=None), zip=metal(),
                                              velcro=cloth('#3e4032', stitch=None)), 0.35),
    'wet_weather_jacket': (build_wet_weather_jacket, dict(shell=Camo(weave=0.02), flap=Camo(0.85, weave=0.02), snap=metal(),
                                                          zip=flat('#1e1e1c'), seam=flat('#2a2a26', ink=False)), 0.4),
    'flight_coverall': (build_flight_coverall, dict(suit=cloth('#5e6450', weave=0.05), pocket=cloth('#575d4a'), zip=metal(),
                                                    name=leather('#2a2a26'), tab=cloth('#4e5442'), pen=plastic('#1e1e1e')), 0.45),
    'sniper_smock': (build_sniper_smock, dict(cloth=Camo(), pocket=Camo(0.85), flap=Camo(0.75), scrim=mesh('#3e4230'),
                                              jute=fur('#5d5a3a'), jute2=fur('#4a4f30')), 0.5),
    'arctic_parka': (build_arctic_parka, dict(cloth=cloth('#d4d6d0', weave=0.04, pattern=camo(['#b4b8b2', '#9aa098'], size=1.9)),
                                              flap=cloth('#c8cac4'), fur=fur('#a89a84'), toggle=plastic('#2a2a2a'),
                                              cord=flat('#b0b2ac'), cuff=rib('#bcbeb8')), 0.4),
    'snow_overwhites': (build_snow_overwhites, dict(cloth=cloth('#dcdcd6', weave=0.04, stitch=None), cord=flat('#b8b8b2'),
                                                    patch=cloth('#d0d0ca', stitch=None), seam=flat('#c4c4be', ink=False)), 0.4),
    'jungle_boots': (build_jungle_boots, dict(lugsole=rubber('#1c1a18', stud=True), sole=rubber('#1c1a18'), upper=leather('#262420'),
                                              canvas=cloth('#4e5236', weave=0.14), collar=leather('#22201c'), tongue=cloth('#4a4e34'),
                                              lace=flat('#2a2a26'), eyelet=metal(), toecap=leather('#22201c')), 0.55),
    'desert_boots': (build_desert_boots, dict(lugsole=rubber('#6a5a44', stud=True), sole=rubber('#6a5a44'), upper=cloth('#a88e66', weave=0.03),
                                              collar=leather('#8e7856'), nylon=cloth('#9a8460', weave=0.12), tongue=cloth('#9a8460'),
                                              lace=flat('#7a6a4e'), toecap=leather('#8e7856')), 0.5),
    'arctic_boots': (build_arctic_boots, dict(hide=leather('#6a5440', gloss=0.05), sole=leather('#4e3e2e'), fur=fur('#8a7a66'),
                                              tie=flat('#2e2a24'), pucker=leather('#5e4a38'), bead=checker('#7a2a24', '#c8c0aa')), 0.45),
    'flight_gloves': (build_flight_gloves, dict(nomex=cloth('#5e6450', weave=0.04, stitch=None), palm=leather('#c2b49a'),
                                                seam=flat('#4e5442', ink=False)), 0.4),
    'shooting_gloves': (build_shooting_gloves, dict(shell=Camo(weave=0.06), knit=rib('#2e3226'), seam=flat('#2e3226', ink=False)), 0.45),
    'arctic_mittens': (build_arctic_mittens, dict(leather=leather('#9a7a4a'), canvas=cloth('#5e5e48', weave=0.12),
                                                  fur=fur('#3a3228'), cord=flat('#2e2e28')), 0.45),
    'riot_arm_guards': (build_riot_arm_guards, dict(shell=plastic('#1c1d1f', gloss=0.25), cap=plastic('#232427', gloss=0.3),
                                                    strap=cloth('#1a1b1d', stitch=None), vent=flat('#0a0a0b', ink=False)), 0.45),
    'riot_leg_guards': (build_riot_leg_guards, dict(shell=plastic('#1c1d1f', gloss=0.25), cap=plastic('#232427', gloss=0.3),
                                                    strap=cloth('#1a1b1d', stitch=None), vent=flat('#0a0a0b', ink=False)), 0.45),
    'wool_commando_sweater': (build_wool_commando_sweater, dict(knit=rib('#3e4432'), rib=rib('#363c2c'),
                                                                patch=cloth('#4a5038', weave=0.1), epaulette=cloth('#4a5038')), 0.4),
}


# ------------------------------------------------------------------ wave 2, batch 3: useful kit, pads and sport armour
# Refs: refs/items/clothing (headlamp, shooting-earmuffs, dog-tags, chainsaw-chaps, beekeeper-suit, cut-resistant-gloves,
# shin-guards, gaiters, football-shoulder-pads, hockey-shoulder-pads, hockey-neck-guard, padded-football-pants,
# hockey-pants, catchers-vest, hockey-gloves, chainmail-sleeve, athletic-cup).


def build_headlamp(g):
    """An elastic band round the head with the lamp on the brow, above the eyes, and a battery pack at the back."""
    d = 0.2
    ring(g, -6.4, -5.4, d, 'band', t=0.15)
    g.box('head', (-0.5, -8.0 - d - 0.15, -2.0), (0.5, -8.0 - d, 2.0), 'band')        # the strap over the top
    g.box('head', (-1.4, -6.8, -4 - d - 1.1), (1.4, -5.0, -4 - d - 0.15), 'lamp')       # the lamp housing
    g.box('head', (-1.0, -6.5, -4 - d - 1.2), (1.0, -5.3, -4 - d - 1.1), 'lens')
    g.box('head', (1.5, -6.4, -4 - d - 0.7), (1.9, -5.8, -4 - d - 0.3), 'button')
    g.box('head', (-1.6, -7.0, 4 + d + 0.15), (1.6, -4.8, 4 + d + 1.0), 'lamp')         # battery box
    g.box('head', (-0.3, -6.9, 4 + d + 1.0), (0.3, -6.3, 4 + d + 1.2), 'button')


def build_shooting_earmuffs(g):
    d = 0.2
    g.box('head', (-HX - 1.1, -8.0 - d - 0.6, -0.7), (HX + 1.1, -8.0 - d, 0.7), 'band')   # the headband over the top
    for s in (-1, 1):
        x0 = s * (HX + d) - (1.2 if s > 0 else 0)
        g.box('head', (x0 + (0.9 if s > 0 else -0.2), -8.0 - d - 0.3, -0.5), (x0 + (1.3 if s > 0 else 0.3), -4.4, 0.5), 'band')
        g.box('head', (x0, -4.8, -1.8), (x0 + 1.2, -0.8, 1.8), 'cup')                  # the ear cups
        g.box('head', (x0 + (1.0 if s > 0 else -0.1), -4.4, -1.4), (x0 + (1.3 if s > 0 else 0.2), -1.2, 1.4), 'cap')
        g.box('head', (x0 + (-0.05 if s > 0 else 1.05), -4.6, -1.6), (x0 + (0.15 if s > 0 else 1.25), -1.0, 1.6), 'foam')


def build_dog_tags(g):
    """A ball chain round the neck and two stamped tags on the chest, worn under the shirt or over it."""
    d = SHIRT + 0.1
    for s in (-1, 1):
        g.box('body', (s * 1.6 - 0.1, -0.15, -2 - d - 0.1), (s * 1.6 + 0.1, 0.05, 1.6), 'chain')
        g.front('body', d, s * 1.6 - 0.1, 0.0, s * 1.6 + 0.1, 3.2 - abs(s) * 0.2, 'chain', t=0.1)
    g.box('body', (-1.6, -0.15, 1.4), (1.6, 0.05, 1.6), 'chain')
    g.front('body', d + 0.1, -0.9, 3.0, 0.3, 4.8, 'tag', t=0.08)
    g.front('body', d + 0.18, -0.3, 3.2, 0.9, 5.0, 'tag', t=0.08)
    g.front('body', d + 0.26, -0.15, 3.6, 0.75, 4.6, 'stamp', t=0.02)
    g.front('body', d + 0.1, -1.0, 3.0, 1.0, 3.2, 'chain', t=0.1)


def build_chainsaw_chaps(g):
    """Apron chaps over the trousers: a cut-resistant front from thigh to boot, buckled round the leg and waist."""
    d = PANTS + 0.35
    g.front('body', d, -3.8, 9.6, 3.8, 12.0, 'shell', t=0.3)
    g.shell('body', 9.4, 10.2, d + 0.3, 'belt')
    g.front('body', d + 0.6, -0.7, 9.3, 0.7, 10.3, 'buckle', t=0.18)
    def leg(p, s):
        g.front(p, d, -2.3, -0.2, 2.3, 10.6, 'shell', t=0.3)
        for side in (-1, 1):
            g.side(p, d, side, -2.3, -0.2, 0.2, 10.6, 'shell', t=0.3)
        for y in (2.0, 6.4, 9.4):                                                     # straps round the back of the leg
            g.back(p, d, -2.0, y, 2.0, y + 0.5, 'belt', t=0.14)
            g.side(p, d + 0.3, s, -0.6, y - 0.1, 0.4, y + 0.6, 'buckle', t=0.15)
        g.front(p, d + 0.3, -1.8, 0.4, 1.8, 1.4, 'label', t=0.04)
    g.both(LEGS, leg)


def build_beekeeper_suit(g):
    """A loose white smock and trousers zipped together, the round brimmed veil hood down on the back."""
    d = JACKET + 0.2
    g.shell('body', 0, 12, d, 'suit')
    g.front('body', d, -0.25, -0.4, 0.25, 11.0, 'zip', t=0.12)
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.2, s * 3.6))
        g.front('body', d, x0, 6.8, x1, 10.0, 'suit', t=0.3)
    g.box('body', (-4.2, -2.6, 0.6), (4.2, 0.4, 2 + d + 1.4), 'veil')                   # the veil hood folded down
    g.box('body', (-5.0, -3.0, 2 + d + 0.2), (5.0, -2.6, 2 + d + 1.8), 'brim')
    def arm(p, s):
        g.shell(p, -2.0, 8.4, d, 'suit')
        g.shell(p, 7.6, 8.6, d + 0.12, 'elastic')
    g.both(ARMS, arm)
    def leg(p, s):
        g.shell(p, -0.2, 11.0, PANTS + 0.45, 'suit')
        g.shell(p, 10.2, 11.0, PANTS + 0.55, 'elastic')
    g.both(LEGS, leg)


def build_cut_resistant_gloves(g):
    def hand(p, s):
        glove(g, p, s, 'knit', d=0.3, cuff='cuff', cuff_y=(7.6, 8.6), cuff_d=0.4)
        g.shell(p, 9.2, 10.4, 0.36, 'palm')                                           # the dipped palm coat
    g.both(ARMS, hand)


def build_shin_guards(g):
    def leg(p, s):
        d = PANTS + 0.15
        g.front(p, d, -2.1, 5.0, 2.1, 10.4, 'shell', t=0.45)
        g.front(p, d, -1.8, 3.8, 1.8, 5.2, 'cap', t=0.6)                               # the knee cup at the top
        for side in (-1, 1):
            g.side(p, d, side, -2.1, 5.4, -0.2, 10.0, 'shell', t=0.35)
        for y in (6.0, 9.0):
            g.back(p, d, -2.0, y, 2.0, y + 0.6, 'strap', t=0.12)
            g.side(p, d + 0.35, s, -1.0, y - 0.1, -0.2, y + 0.7, 'velcro', t=0.1)
        g.front(p, d + 0.45, -0.8, 6.0, 0.8, 7.0, 'badge', t=0.04)
    g.both(LEGS, leg)


def build_gaiters(g):
    def leg(p, s):
        d = BOOT + 0.25
        g.shell(p, 6.4, 11.0, d, 'shell')
        g.shell(p, 6.2, 6.9, d + 0.1, 'cord')
        g.front(p, d, -0.3, 6.4, 0.3, 11.0, 'zip', t=0.12)
        g.box(p, (-0.25, 11.5, -2.4), (0.25, 11.7, 2.4), 'strap')                       # the instep strap under the boot
        g.side(p, d, s, -1.8, 9.0, 1.8, 11.0, 'panel', t=0.08)
    g.both(LEGS, leg)


def build_football_shoulder_pads(g):
    """Big hard shoulder caps on a padded chest and back plate, laced at the front."""
    d = SHIRT + 0.6
    g.front('body', d, -3.8, 0.4, 3.8, 6.0, 'shell', t=0.5)
    g.back('body', d, -3.8, 0.0, 3.8, 6.6, 'shell', t=0.5)
    g.front('body', d + 0.5, -0.5, 1.0, 0.5, 5.2, 'lace', t=0.08)
    for s in (-1, 1):                                                                # the arches over each shoulder
        x0, x1 = sorted((s * 1.6, s * 4.2))
        g.box('body', (x0, -1.4, -2 - d - 0.6), (x1, 0.4, 2 + d + 0.6), 'shell')
        g.side('body', d, s, -2.0, 6.4, 2.0, 7.0, 'strap', t=0.15)
    def arm(p, s):
        g.box(p, (-3.6 if s < 0 else -1.4, -3.4, -3.2), (1.4 if s < 0 else 3.6, -1.4, 3.2), 'cap')   # shoulder cap
        g.side(p, JACKET, s, -2.8, -1.6, 2.8, 1.0, 'cap', t=0.5)
        g.side(p, JACKET + 0.5, s, -2.4, -0.8, 2.4, -0.4, 'stripe', t=0.04)
    g.both(ARMS, arm)


def build_hockey_shoulder_pads(g):
    d = SHIRT + 0.45
    g.front('body', d, -3.6, 0.6, 3.6, 8.4, 'foam', t=0.4)                              # chest and belly protector
    g.front('body', d + 0.4, -3.2, 1.0, 3.2, 4.0, 'shell', t=0.25)
    g.back('body', d, -3.6, 0.2, 3.6, 7.6, 'foam', t=0.4)
    g.back('body', d + 0.4, -1.0, 0.4, 1.0, 6.8, 'shell', t=0.3)                        # the spine plate
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.4, s * 4.0))
        g.box('body', (x0, -0.9, -2 - d - 0.4), (x1, 0.4, 2 + d + 0.4), 'foam')
        g.side('body', d, s, -2.0, 7.0, 2.0, 7.6, 'strap', t=0.15)
    def arm(p, s):
        g.box(p, (-3.4 if s < 0 else -1.2, -2.9, -2.8), (1.2 if s < 0 else 3.4, -1.6, 2.8), 'shell')   # shoulder caps
        g.side(p, JACKET - 0.1, s, -2.4, -1.6, 2.4, 2.6, 'foam', t=0.4)                # bicep pads
    g.both(ARMS, arm)


def build_hockey_neck_guard(g):
    d = SHIRT + 0.25
    g.box('body', (-3.0, -1.6, -2 - d - 0.3), (3.0, 0.4, 2 + d + 0.3), 'collar')
    g.front('body', d, -2.6, 0.2, 2.6, 2.6, 'bib', t=0.25)                             # the bib down the chest
    g.box('body', (-0.5, -1.4, 2 + d + 0.3), (0.5, 0.2, 2 + d + 0.5), 'velcro')


def build_leather_neck_guard(g):
    """A survivor's neck guard: layered leather strips riveted round a stiff collar, bite-proof at the throat."""
    d = SHIRT + 0.35
    g.box('body', (-3.2, -2.0, -2 - d - 0.35), (3.2, 0.6, 2 + d + 0.35), 'leather')
    for i, y in enumerate((-1.6, -0.6, 0.4)):
        g.box('body', (-3.3 - 0.1 * i, y, -2 - d - 0.45 - 0.08 * i), (3.3 + 0.1 * i, y + 0.6, 2 + d + 0.45 + 0.08 * i), 'band')
    for x in (-2.4, -0.8, 0.8, 2.4):
        g.front('body', d + 0.55, x - 0.15, -1.2, x + 0.15, -0.9, 'rivet', t=0.08)
    g.box('body', (-0.6, -1.8, 2 + d + 0.4), (0.6, 0.4, 2 + d + 0.7), 'buckle')


def build_padded_football_pants(g):
    d = PANTS
    trouser_top(g, d, 'cloth')
    def leg(p, s):
        g.shell(p, 0, 7.6, d, 'cloth')                                                # cut just below the knee
        g.shell(p, 7.2, 7.8, d + 0.08, 'hem')
        g.front(p, d, -1.8, 0.8, 1.8, 4.0, 'pad', t=0.3)                              # thigh pads
        g.front(p, d, -1.6, 4.6, 1.6, 6.8, 'pad', t=0.35)                             # knee pads
        g.side(p, d, s, -0.2, 0, 0.2, 7.6, 'stripe', t=0.04)
    g.both(LEGS, leg)
    for s in (-1, 1):                                                                 # hip pads
        g.side('body', d, s, -1.6, 9.6, 1.6, 12.0, 'pad', t=0.35)


def build_hockey_pants(g):
    """Girdle pants: bulky padded shorts to the knee, padded hips, the kidney panel at the back."""
    d = PANTS + 0.5
    g.shell('body', 8.8, 12, d, 'shell')
    g.shell('body', 8.6, 9.2, d + 0.1, 'belt')
    g.back('body', d, -3.0, 9.2, 3.0, 11.8, 'pad', t=0.3)                              # kidney pad
    def leg(p, s):
        g.shell(p, 0, 5.4, d, 'shell')
        g.shell(p, 4.8, 5.6, d + 0.1, 'hem')
        g.side(p, d, s, -2.0, 0, 2.0, 4.6, 'stripe', t=0.06)
        g.front(p, d, -1.8, 0.6, 1.8, 3.8, 'pad', t=0.2)
    g.both(LEGS, leg)


def build_catchers_vest(g):
    """A catcher's chest protector: padded ribs down the front and a groin flap, strapped across the back."""
    d = SHIRT + 0.35
    g.front('body', d, -3.6, -0.2, 3.6, 10.6, 'shell', t=0.55)
    for y in (1.2, 3.0, 4.8, 6.6, 8.4):                                               # the padded ribs
        g.front('body', d + 0.55, -3.4, y, 3.4, y + 1.2, 'pad', t=0.25)
    g.box('body', (-2.6, -1.0, -2 - d - 0.55), (2.6, 0.0, -2 - d + 0.2), 'shell')       # the throat collar
    g.front('body', d + 0.8, -1.8, 2.2, 1.8, 3.6, 'logo', t=0.04)
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.4, s * 2.8))
        g.box('body', (x0, -0.3, -2 - d), (x1, 0.3, 2 + d), 'strap')
        g.back('body', d, x0, 0.3, x1, 4.0, 'strap', t=0.12)
    g.back('body', d, -3.8, 4.0, 3.8, 4.6, 'strap', t=0.12)
    def leg(p, s):                                                                    # the groin flap on the thighs
        g.front(p, PANTS + 0.4, -1.8 if s < 0 else -2.2, -0.4, 2.2 if s < 0 else 1.8, 2.0, 'shell', t=0.45)
    g.both(LEGS, leg)


def build_hockey_gloves(g):
    def hand(p, s):
        glove(g, p, s, 'shell', d=0.75, cuff='cuff', cuff_y=(6.6, 8.6), cuff_d=1.1)
        for y in (8.6, 9.3, 10.0):                                                     # segmented finger rolls
            g.side(p, 0.75, s, -2.2, y, 2.2, y + 0.6, 'roll', t=0.3)
        g.shell(p, 9.6, 10.5, 0.8, 'palm')
        g.side(p, 1.1, s, -1.4, 7.0, 1.4, 8.0, 'logo', t=0.05)
    g.both(ARMS, hand)


def build_chainmail_butcher_glove(g):
    def hand(p, s):
        glove(g, p, s, 'mail', d=0.35, cuff='mail', cuff_y=(6.8, 8.6), cuff_d=0.45)
        g.shell(p, 6.6, 7.0, 0.55, 'strap')
    g.both(ARMS, hand)


def build_chainmail_sleeve(g):
    """Butcher's mail sleeves from the wrist past the elbow, over the shirt, an elastic strap at the top."""
    def arm(p, s):
        g.shell(p, 1.4, 8.6, JACKET + 0.05, 'mail')
        g.shell(p, 1.2, 1.8, JACKET + 0.12, 'strap')
    g.both(ARMS, arm)


def build_athletic_cup(g):
    """A jockstrap with a hard cup, worn under everything."""
    d = 0.12
    g.shell('body', 10.6, 11.4, d, 'band')
    g.front('body', d, -1.2, 11.0, 1.2, 12.0, 'cup', t=0.4)
    def leg(p, s):
        g.front(p, d, -1.0 if s > 0 else -0.6, -0.2, 0.6 if s > 0 else 1.0, 1.0, 'cup', t=0.4)
        g.back(p, d, -1.6, -0.2, 1.6, 0.4, 'band', t=0.06)
    g.both(LEGS, leg)


# worn models for items registered elsewhere (loot/items): their own item model and name stay theirs
OTHERS_ITEMS = {'headlamp', 'dog_tags'}

KIT_2 = {
    'headlamp': (build_headlamp, dict(band=cloth('#2a2a2c', stitch=None), lamp=plastic('#b83a2a'), lens=plastic('#e8e6d8', gloss=0.5),
                                      button=plastic('#1a1a1a')), 0.35),
    'shooting_earmuffs': (build_shooting_earmuffs, dict(band=plastic('#1e1f22'), cup=plastic('#2a4a7a', gloss=0.25),
                                                        cap=plastic('#1e1f22'), foam=cloth('#1a1a1a', stitch=None)), 0.35),
    'dog_tags': (build_dog_tags, dict(chain=metal('steel'), tag=metal('steel'), stamp=flat('#4a4a4a', ink=False)), 0.3),
    'chainsaw_chaps': (build_chainsaw_chaps, dict(shell=cloth('#c8682a', weave=0.08), belt=cloth('#2a2a2a', stitch=None),
                                                  buckle=plastic('#111111'), label=flat('#d8d4c4')), 0.5),
    'beekeeper_suit': (build_beekeeper_suit, dict(suit=cloth('#dedcd2', weave=0.05), zip=metal(), veil=mesh('#2a2a2a'),
                                                  brim=cloth('#d0ccbe'), elastic=rib('#cfcbbd')), 0.45),
    'cut_resistant_gloves': (build_cut_resistant_gloves, dict(knit=knit('#7a7c80'), palm=rubber('#3a3c40'), cuff=rib('#5a2a24')), 0.45),
    'shin_guards': (build_shin_guards, dict(shell=plastic('#1e1f22', gloss=0.3), cap=plastic('#26272a', gloss=0.3),
                                            strap=cloth('#1a1a1c', stitch=None), velcro=cloth('#2a2a2c', stitch=None),
                                            badge=flat('#c8c8c8')), 0.5),
    'gaiters': (build_gaiters, dict(shell=cloth('#2a2c2e', weave=0.04), panel=cloth('#c86a2a', weave=0.04), cord=flat('#1e1e1e'),
                                    zip=metal(), strap=rubber('#1a1a1a')), 0.45),
    'football_shoulder_pads': (build_football_shoulder_pads, dict(shell=plastic('#d8d4ca', gloss=0.25), cap=plastic('#d8d4ca', gloss=0.25),
                                                                  lace=flat('#1e1e1e'), strap=cloth('#2a2a2a', stitch=None),
                                                                  stripe=flat('#7a2a24')), 0.5),
    'hockey_shoulder_pads': (build_hockey_shoulder_pads, dict(foam=cloth('#3a3e44', weave=0.05), shell=plastic('#9aa0a6', gloss=0.2),
                                                              strap=cloth('#1e1e1e', stitch=None)), 0.5),
    'hockey_neck_guard': (build_hockey_neck_guard, dict(collar=cloth('#2a2c30', weave=0.04), bib=cloth('#2a2c30', weave=0.04),
                                                        velcro=cloth('#1e1e1e', stitch=None)), 0.4),
    'leather_neck_guard': (build_leather_neck_guard, dict(leather=leather('#4a3020'), band=leather('#5a3a26', stitch='#2a1a10'),
                                                          rivet=metal('steel'), buckle=metal('steel')), 0.55),
    'padded_football_pants': (build_padded_football_pants, dict(cloth=cloth('#c8a83a', weave=0.04, stitch=None), hem=rib('#b89a34'),
                                                                pad=cloth('#bc9e36', stitch=None), stripe=flat('#7a2a24'),
                                                                loop=cloth('#b89a34')), 0.5),
    'hockey_pants': (build_hockey_pants, dict(shell=cloth('#1e2a4a', weave=0.04), belt=cloth('#1a1a1a', stitch=None),
                                              pad=cloth('#1a2440'), hem=cloth('#1a2440'), stripe=flat('#b8b4a8')), 0.5),
    'catchers_vest': (build_catchers_vest, dict(shell=cloth('#1e2a4a', weave=0.04), pad=cloth('#24325a', weave=0.04),
                                                strap=cloth('#1a1a1a', stitch=None), logo=flat('#c8c4b8')), 0.5),
    'hockey_gloves': (build_hockey_gloves, dict(shell=cloth('#1e1f22', weave=0.04), cuff=cloth('#1e1f22', weave=0.04),
                                                roll=plastic('#26272a'), palm=leather('#c8b898'), logo=flat('#c8c4b8')), 0.5),
    'chainmail_butcher_glove': (build_chainmail_butcher_glove, dict(mail=mesh('#9a9ca0'), strap=plastic('#2a5a9a')), 0.4),
    'chainmail_sleeve': (build_chainmail_sleeve, dict(mail=mesh('#9a9ca0'), strap=rib('#2a5a9a')), 0.4),
    'athletic_cup': (build_athletic_cup, dict(band=rib('#d8d4c8'), cup=plastic('#d4d0c4', gloss=0.2)), 0.3),
}


# ------------------------------------------------------------------ wave 2, batch 4: civilian clothes
# Refs: refs/items/clothing (puffer-jacket, ski-jacket, ski-trousers, ski-goggles, cardigan, blazer, gilet, tracksuit,
# bathrobe, rugby-shirt, chef-whites, mechanic-coveralls, prison-jumpsuit, security-guard-uniform, flat-cap, fedora,
# straw-hat, fingerless-gloves, sandals).


def build_puffer_jacket(g):
    """A down jacket: fat quilted baffles round the body and arms, a tall collar, the hood rolled at the back."""
    d = JACKET + 0.2
    for i, y in enumerate((0, 2.4, 4.8, 7.2, 9.6)):                                   # the baffles bulge between seams
        g.shell('body', y, y + 2.4, d + (0.15 if i % 2 == 0 else 0.25), 'down')
        g.shell('body', y + 2.25, y + 2.4, d + 0.32, 'seam')
    g.box('body', (-3.2, -1.8, -2 - d - 0.4), (3.2, 0.2, 2 + d + 0.4), 'down')         # tall collar
    g.box('body', (-3.4, -2.4, 0.6), (3.4, -0.6, 2 + d + 1.2), 'down')                  # the hood rolled down
    g.front('body', d + 0.25, -0.2, -1.8, 0.2, 12.0, 'zip', t=0.12)
    for s in (-1, 1):
        g.front('body', d + 0.25, s * 2.2 - 0.1, 7.0, s * 2.2 + 0.1, 9.8, 'zip', t=0.08)
    def arm(p, s):
        for i, y in enumerate((-2.0, 0.4, 2.8, 5.2)):
            g.shell(p, y, y + 2.4, d + (0.12 if i % 2 == 0 else 0.22), 'down')
        g.shell(p, 7.4, 8.4, d + 0.05, 'cuff')
    g.both(ARMS, arm)


def build_ski_jacket(g):
    d = JACKET + 0.05
    g.shell('body', 0, 11.6, d, 'shell')
    g.shell('body', 11.2, 12.2, d + 0.12, 'shell')
    g.front('body', d, -4 - d, 2.6, 4 + d, 5.4, 'panel', t=0.06)                       # bold colour block across the chest
    g.both(ARMS, lambda p, s: g.shell(p, 0.6, 3.4, d + 0.04, 'panel'))
    g.box('body', (-3.2, -1.6, -2 - d - 0.3), (3.2, 0.2, 2 + d + 0.3), 'shell')
    g.front('body', d, -0.2, -1.6, 0.2, 12.0, 'zip', t=0.14)
    g.front('body', d + 0.14, -0.35, -1.5, 0.35, -0.8, 'pull', t=0.1)
    g.side('body', d, -1, -1.4, 6.0, -0.2, 7.2, 'pass_card', t=0.08)                        # the lift-pass pocket on the arm side
    g.front('body', d, 1.2, 1.4, 3.4, 2.0, 'zip', t=0.06)
    sleeves(g, d, 'shell', end=8.0, cuff='cuff', cuff_d=0.15)


def build_ski_trousers(g):
    d = PANTS + 0.3
    g.shell('body', 9.0, 12, d, 'shell')
    for s in (-1, 1):                                                                  # bib straps over the shoulders
        x0, x1 = sorted((s * 1.4, s * 2.4))
        g.front('body', SHIRT + 0.2, x0, 0.0, x1, 9.0, 'strap', t=0.1)
        g.back('body', SHIRT + 0.2, x0, 0.0, x1, 9.0, 'strap', t=0.1)
        g.box('body', (x0, -0.3, -2 - SHIRT - 0.3), (x1, 0.0, 2 + SHIRT + 0.3), 'strap')
    def leg(p, s):
        g.shell(p, 0, 11.4, d, 'shell')
        g.front(p, d, -1.8, 4.6, 1.8, 7.0, 'patch', t=0.12)                           # reinforced knees
        g.side(p, d, s, -0.15, 0.4, 0.15, 7.0, 'zip', t=0.08)                         # thigh vents
        g.shell(p, 10.4, 11.6, d + 0.15, 'cuff')                                      # the snow gaiter cuff
    g.both(LEGS, leg)


def build_ski_goggles(g):
    """A big spherical goggle on a wide strap; the lens is mirrored, worn over the eyes."""
    z = -4.4
    top, bot = EYES_HIGH - 1.0, EYES_LOW + 0.9
    g.box('head', (-4.0, top, z - 1.0), (4.0, bot, z - 0.2), 'frame')
    g.box('head', (-3.7, top + 0.25, z - 1.15), (3.7, bot - 0.25, z - 1.0), 'lens')
    g.box('head', (-0.6, bot - 0.5, z - 1.16), (0.6, bot, z - 1.0), 'frame')           # nose cut
    for s in (-1, 1):
        x = -4.36 if s < 0 else 4.2
        g.box('head', (x, top + 0.2, z - 0.2), (x + 0.16, bot - 0.2, 4.36), 'strap')
    g.box('head', (-4.36, top + 0.2, 4.2), (4.36, bot - 0.2, 4.36), 'strap')


def build_cardigan(g):
    d = JACKET - 0.15
    g.shell('body', 0, 11.0, d, 'knit')
    g.shell('body', 10.6, 12.2, d + 0.1, 'rib')
    for s in (-1, 1):                                                                 # the open V neck
        g.box('body', (s * 0.3 - (0.8 if s > 0 else 0), -0.3, -2 - d - 0.15), (s * 0.3 + (0.8 if s < 0 else 0), 4.6, -2 - d), 'rib')
    g.front('body', d, -0.4, 4.6, 0.4, 12.0, 'rib', t=0.15)
    buttons(g, 'body', d + 0.15, 0, (5.0, 6.8, 8.6, 10.4), 'button')
    for s in (-1, 1):
        g.front('body', d, s * 1.4 - (1.6 if s < 0 else 0), 7.8, s * 1.4 + (1.6 if s > 0 else 0), 10.2, 'knit', t=0.15)
    sleeves(g, d, 'knit', end=7.6, cuff='rib', cuff_d=0.1)


def build_blazer(g):
    d = JACKET - 0.05
    g.shell('body', 0, 12, d, 'wool')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 1.0, d + 0.05, 'wool'))
    lapels(g, d, 'lapel', y1=5.4, width=1.6)
    buttons(g, 'body', d + 0.05, 0, (6.6, 8.4), 'button')
    g.front('body', d, -3.4, 2.8, -1.8, 3.1, 'pocket', t=0.1)                          # breast pocket and hanky
    g.front('body', d + 0.1, -3.1, 2.4, -2.2, 2.9, 'square', t=0.08)
    for s in (-1, 1):
        g.front('body', d, s * 1.4 - (2.2 if s < 0 else 0), 8.6, s * 1.4 + (2.2 if s > 0 else 0), 9.0, 'pocket', t=0.12)
    sleeves(g, d, 'wool', end=8.0)
    g.both(ARMS, lambda p, s: buttons(g, p, d, 0.8, (7.2,), 'button'))


def build_gilet(g):
    d = JACKET + 0.15
    g.shell('body', 0.4, 11.4, d, 'down')
    for y in (2.6, 5.0, 7.4, 9.8):
        g.shell('body', y, y + 0.18, d + 0.1, 'seam')
    g.box('body', (-3.0, -1.2, -2 - d - 0.2), (3.0, 0.4, 2 + d + 0.2), 'down')
    g.front('body', d, -0.2, -1.2, 0.2, 11.4, 'zip', t=0.14)
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.4, s * 3.6))
        g.box('body', (x0, -0.3, -2 - d), (x1, 0.5, 2 + d), 'down')
        g.front('body', d, s * 2.6 - 0.1, 7.0, s * 2.6 + 0.1, 9.6, 'zip', t=0.08)
    g.front('body', d, 1.0, 1.6, 3.2, 3.0, 'logo', t=0.05)


def build_tracksuit_top(g):
    d = JACKET - 0.1
    g.shell('body', 0, 11.0, d, 'poly')
    g.shell('body', 10.6, 12.2, d + 0.1, 'rib')
    g.box('body', (-3.0, -1.4, -2 - d - 0.25), (3.0, 0.2, 2 + d + 0.25), 'poly')
    g.front('body', d, -0.2, -1.4, 0.2, 12.0, 'zip', t=0.12)
    sleeves(g, d, 'poly', end=7.6, cuff='rib', cuff_d=0.1)
    def arm(p, s):                                                                     # three stripes down each sleeve
        for i in range(3):
            z = -0.9 + i * 0.6
            g.side(p, d, s, z, -2.0, z + 0.3, 7.6, 'stripe', t=0.04)
    g.both(ARMS, arm)
    g.front('body', d, -3.0, 1.6, -1.8, 2.8, 'logo', t=0.05)


def build_tracksuit_bottoms(g):
    d = PANTS - 0.05
    g.shell('body', 9.6, 12, d, 'poly')
    g.shell('body', 9.4, 10.2, d + 0.1, 'rib')
    def leg(p, s):
        g.shell(p, 0, 10.8, d, 'poly')
        g.shell(p, 10.4, 11.4, d + 0.12, 'rib')
        for i in range(3):
            z = -0.9 + i * 0.6
            g.side(p, d, s, z, 0, z + 0.3, 10.6, 'stripe', t=0.04)
    g.both(LEGS, leg)


def build_bathrobe(g):
    d = JACKET + 0.05
    g.shell('body', 0, 12, d, 'terry')
    skirt(g, 8.6, d + 0.1, 'terry', flare=0.25)
    for s in (-1, 1):                                                                 # shawl collar crossing over
        g.box('body', (s * 0.2 - (1.6 if s > 0 else 0), -0.6, -2 - d - 0.3), (s * 0.2 + (1.6 if s < 0 else 0), 6.0, -2 - d), 'collar')
    g.box('body', (-3.4, -0.9, 0.4), (3.4, 0.2, 2 + d + 0.35), 'collar')
    g.shell('body', 7.2, 7.9, d + 0.12, 'belt')                                       # the tie belt and its knot
    g.front('body', d + 0.12, -1.6, 7.0, -0.4, 8.2, 'belt', t=0.3)
    g.front('body', d + 0.2, -1.4, 8.2, -1.0, 10.4, 'belt', t=0.12)
    g.front('body', d + 0.2, -0.9, 8.2, -0.5, 9.8, 'belt', t=0.12)
    sleeves(g, d + 0.1, 'terry', end=6.8, cuff='collar', cuff_d=0.15)


def build_rugby_shirt(g):
    d = SHIRT + 0.05
    g.shell('body', 0, 12, d, 'cloth')
    for y in (3.0, 6.6):                                                              # the hoops
        g.shell('body', y, y + 1.8, d + 0.02, 'hoop')
    g.box('body', (-3.0, -0.8, -2 - d - 0.25), (3.0, 0.3, 2 + d + 0.25), 'collar')     # white collar
    g.front('body', d, -0.6, -0.4, 0.6, 3.0, 'collar', t=0.12)
    buttons(g, 'body', d + 0.12, 0, (0.6, 1.8), 'button')
    sleeves(g, d, 'cloth', end=8.0, cuff='collar', cuff_d=0.1)
    g.both(ARMS, lambda p, s: g.shell(p, 0.8, 2.4, d + 0.02, 'hoop'))
    g.front('body', d + 0.02, 1.4, 1.2, 3.0, 2.6, 'badge', t=0.05)


def build_chef_jacket(g):
    d = JACKET - 0.15
    g.shell('body', 0, 12, d, 'cotton')
    g.front('body', d, -4 - d, 0.2, 1.2, 12.0, 'cotton', t=0.2)                        # the double-breasted front panel
    for x in (-2.6, 0.4):
        buttons(g, 'body', d + 0.2, x, (1.6, 4.0, 6.4, 8.8), 'knot')
    g.box('body', (-3.0, -1.0, -2 - d - 0.25), (3.0, 0.4, 2 + d + 0.25), 'cotton')      # mandarin collar
    g.front('body', d + 0.2, 2.0, 2.0, 3.4, 4.6, 'pen', t=0.1)                          # thermometer and pen sleeve
    sleeves(g, d, 'cotton', end=6.4, cuff='cotton', cuff_d=0.2)                         # sleeves rolled back
    g.front('body', d + 0.2, -3.0, 9.8, 3.0, 12.0, 'apron', t=0.06)
    g.shell('body', 9.6, 10.0, d + 0.24, 'tie')


def build_mechanic_coveralls(g):
    d = JACKET - 0.15
    g.shell('body', 0, 12, d, 'drill')
    collar_flaps(g, d, 'drill')
    g.front('body', d, -0.25, 0.2, 0.25, 12.0, 'zip', t=0.12)
    g.front('body', d, 1.4, 1.6, 3.4, 3.4, 'name', t=0.06)                             # the name oval
    g.front('body', d, -3.4, 1.8, -1.4, 4.4, 'drill', t=0.15)
    g.front('body', d, -3.0, 4.0, -2.4, 4.6, 'grease', t=0.02)
    g.shell('body', 10.4, 11.0, d + 0.06, 'drill')
    sleeves(g, d, 'drill', end=8.2, cuff='drill', cuff_d=0.1)
    def leg(p, s):
        g.shell(p, -0.2, 11.2, PANTS + 0.3, 'drill')
        g.back(p, PANTS + 0.3, -1.6, 0.6, 1.6, 3.4, 'drill', t=0.15)
        g.side(p, PANTS + 0.3, s, -1.6, 2.4, 1.0, 6.8, 'drill', t=0.15)                # the rule pocket
        g.front(p, PANTS + 0.3, -1.4 + s * 0.2, 3.6, 0.4 + s * 0.2, 5.4, 'grease', t=0.02)
    g.both(LEGS, leg)


def build_prison_jumpsuit(g):
    d = SHIRT + 0.2
    g.shell('body', 0, 12, d, 'cloth')
    g.box('body', (-2.6, -0.3, -2 - d - 0.12), (2.6, 0.25, 2 + d + 0.12), 'cloth')       # V neck band
    g.front('body', d, -0.6, 0.2, 0.6, 2.4, 'shadow', t=0.02)
    g.back('body', d, -2.8, 1.6, 2.8, 4.4, 'stencil', t=0.06)                           # COUNTY JAIL on the back
    g.front('body', d, 1.2, 1.6, 3.4, 2.6, 'number', t=0.04)
    sleeves(g, d, 'cloth', short=2.4, cuff='cloth', cuff_d=0.06)
    def leg(p, s):
        g.shell(p, -0.2, 11.4, PANTS + 0.15, 'cloth')
        g.side(p, PANTS + 0.15, s, -1.0, 0.6, 1.0, 9.8, 'stencil', t=0.05)
    g.both(LEGS, leg)


def build_security_shirt(g):
    d = SHIRT + 0.05
    g.shell('body', 0, 12, d, 'cloth')
    collar_flaps(g, d, 'collar')
    g.front('body', d, -0.45, 0, 0.45, 12, 'placket', t=0.12)
    buttons(g, 'body', d + 0.12, 0, (1.6, 3.8, 6.0, 8.2, 10.4), 'button')
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.0, s * 3.4))
        pouch(g, 'body', d, x0, 2.0, x1, 4.8, 'cloth', 'flap', t=0.2)
    g.front('body', d + 0.2, -3.0, 1.0, -1.6, 2.2, 'badge', t=0.12)                    # the badge
    g.front('body', d + 0.2, 1.4, 1.4, 3.2, 1.9, 'name', t=0.06)
    g.front('body', d + 0.2, 1.0, 2.4, 1.4, 4.2, 'radio', t=0.6)                        # shoulder radio mic on the clip
    sleeves(g, d, 'cloth', short=3.0, cuff='cloth', cuff_d=0.06)
    g.both(ARMS, lambda p, s: g.side(p, d, s, -1.2, -0.6, 1.2, 1.6, 'patch', t=0.08))   # SECURITY shoulder patches
    g.both(ARMS, lambda p, s: g.box(p, (-1.6 if s < 0 else 0.4, -2.0 - d - 0.2, -2.0), (-0.4 if s < 0 else 1.6, -2.0 - d, 2.0), 'collar'))


def build_flat_cap(g):
    d = 0.45
    g.box('head', (-4 - d, -8.6, -4 - d - 0.4), (4 + d, -5.4, 4 + d), 'tweed')
    g.box('head', (-4 - d + 0.2, -8.8, -4 - d), (4 + d - 0.2, -8.6, 4 + d - 0.4), 'tweed')
    g.box('head', (-3.8, -5.8, -6.6), (3.8, -5.3, -4 - d - 0.4), 'tweed')               # the short brim
    g.box('head', (-1.0, -8.9, -4 - d - 0.2), (1.0, -8.6, -4 - d + 0.8), 'button')       # the button where the top meets the brim


def build_fedora(g):
    g.box('head', (-3.9, -10.2, -4.2), (3.9, -5.6, 4.2), 'felt')                        # crown
    g.box('head', (-0.5, -10.3, -3.4), (0.5, -9.4, 3.4), 'crease')                     # the centre dent
    for s in (-1, 1):
        g.box('head', (s * 2.2 - 0.45, -10.1, -4.3), (s * 2.2 + 0.45, -8.4, -3.2), 'crease')   # front pinches
    g.box('head', (-4.0, -6.8, -4.3), (4.0, -5.6, 4.3), 'band')                        # ribbon band
    g.box('head', (3.9, -6.7, 1.0), (4.15, -5.7, 2.0), 'band')                         # its bow
    g.box('head', (-6.0, -5.6, -6.6), (6.0, -5.2, 6.0), 'felt')                         # the brim, snapped down at the front
    g.box('head', (-4.6, -5.2, -7.0), (4.6, -4.95, -6.2), 'felt')


def build_straw_hat(g):
    g.box('head', (-3.8, -9.4, -3.8), (3.8, -5.6, 3.8), 'straw')
    g.box('head', (-4.0, -6.8, -4.0), (4.0, -5.6, 4.0), 'band')
    ring(g, -5.6, -5.15, 0.2, 'straw', t=3.4)                                          # the wide brim
    ring(g, -5.15, -4.9, 3.6, 'straw', t=0.4)


def build_fingerless_gloves(g):
    def hand(p, s):
        g.shell(p, 7.4, 9.8, 0.4, 'knit')                                             # stops at the knuckles
        g.shell(p, 7.2, 8.0, 0.45, 'rib')
        g.side(p, 0.4, s, -1.6, 8.6, 1.6, 9.6, 'leather', t=0.12)
        g.box(p, (-2.4 if s < 0 else 0.4, 9.0, -2.4), (-1.6 if s < 0 else 1.2, 10.0, -1.6), 'knit')   # thumb hole edge
    g.both(ARMS, hand)


def build_sandals(g):
    """Sport sandals: a thick sole and open straps over the toes, instep and heel."""
    def foot(p, s):
        g.box(p, (-2.3, 11.35, -5.0), (2.3, 12.35, 2.35), 'sole')
        g.box(p, (-2.35, 11.0, -4.8), (2.35, 11.4, -3.8), 'strap')                      # toe strap
        g.box(p, (-2.35, 10.2, -2.6), (2.35, 11.4, -1.6), 'strap')                      # instep strap
        g.shell(p, 9.6, 10.4, 0.3, 'strap')                                             # ankle strap
        g.back(p, 0.3, -1.0, 10.4, 1.0, 11.4, 'strap', t=0.2)
        g.side(p, 0.32, s, -1.4, 9.5, -0.6, 10.5, 'buckle', t=0.12)
    g.both(LEGS, foot)


CIVILIAN = {
    'puffer_jacket': (build_puffer_jacket, dict(down=cloth('#2e3440', weave=0.02, stitch=None), seam=flat('#1e222a', ink=False),
                                                zip=metal(), cuff=rib('#262a32')), 0.35),
    'ski_jacket': (build_ski_jacket, dict(shell=cloth('#c84a2a', weave=0.02), panel=cloth('#1e2a44', weave=0.02), zip=flat('#1a1a1a'),
                                          pull=plastic('#d8d4c4'), pass_card=plastic('#2a2a2a'), cuff=rib('#1e2a44')), 0.35),
    'ski_trousers': (build_ski_trousers, dict(shell=cloth('#1e1f24', weave=0.02), strap=cloth('#2a2a30', stitch=None),
                                              patch=cloth('#2a2b30'), zip=flat('#3a3a3a'), cuff=cloth('#2a2b30')), 0.4),
    'ski_goggles': (build_ski_goggles, dict(frame=plastic('#e8e6e0', gloss=0.3), lens=plastic('#c8742a', gloss=0.6),
                                            strap=cloth('#c84a2a', stitch=None)), 0.25),
    'cardigan': (build_cardigan, dict(knit=knit('#7a2a2a'), rib=rib('#6a2424'), button=plastic('#2a1a14')), 0.35),
    'blazer': (build_blazer, dict(wool=cloth('#2a3346', weave=0.04), lapel=cloth('#253042', weave=0.04), button=metal('brass'),
                                  pocket=cloth('#253042'), square=flat('#c8c0a8')), 0.25),
    'gilet': (build_gilet, dict(down=cloth('#3e4a36', weave=0.02, stitch=None), seam=flat('#2a3224', ink=False), zip=metal(),
                                logo=flat('#c8c0a8')), 0.35),
    'tracksuit_top': (build_tracksuit_top, dict(poly=cloth('#1e2a5a', weave=0.02, stitch=None), rib=rib('#1a2450'), zip=metal(),
                                                stripe=flat('#e0dcd0'), logo=flat('#e0dcd0')), 0.35),
    'tracksuit_bottoms': (build_tracksuit_bottoms, dict(poly=cloth('#1e2a5a', weave=0.02, stitch=None), rib=rib('#1a2450'),
                                                        stripe=flat('#e0dcd0')), 0.4),
    'bathrobe': (build_bathrobe, dict(terry=fur('#d8d4c8'), collar=fur('#ccc8bc'), belt=fur('#ccc8bc')), 0.35),
    'rugby_shirt': (build_rugby_shirt, dict(cloth=cloth('#1e4a2e', weave=0.05), hoop=cloth('#c8a83a', weave=0.05),
                                            collar=cloth('#e0dcd0', stitch=None), button=plastic('#e0dcd0'), badge=flat('#c8a83a')), 0.4),
    'chef_jacket': (build_chef_jacket, dict(cotton=cloth('#e4e0d6', weave=0.03), knot=cloth('#1e1e1e', stitch=None),
                                            pen=plastic('#2a2a2a'), apron=cloth('#2e3440', weave=0.06), tie=cloth('#2e3440')), 0.4),
    'mechanic_coveralls': (build_mechanic_coveralls, dict(drill=cloth('#2a3a5a', weave=0.1), zip=metal(), name=flat('#d8d4c4'),
                                                          grease=flat('#14161a', ink=False)), 0.55),
    'prison_jumpsuit': (build_prison_jumpsuit, dict(cloth=cloth('#e8782a', weave=0.04), shadow=flat('#2a1a10', ink=False),
                                                    stencil=stencil('#e8782a', '#1e1e1e'), number=flat('#1e1e1e')), 0.45),
    'security_shirt': (build_security_shirt, dict(cloth=cloth('#2a2d34', weave=0.05), collar=cloth('#25282e'),
                                                  placket=cloth('#25282e'), flap=cloth('#25282e'), button=plastic('#1a1a1a'),
                                                  badge=metal('steel'), name=metal('brass'), radio=plastic('#111111'),
                                                  patch=stencil('#2a2d34', '#c8c0a8')), 0.4),
    'flat_cap': (build_flat_cap, dict(tweed=cloth('#5a5040', pattern=plaid('#5a5040', '#3a3428', 1.0)), button=cloth('#4a4234')), 0.35),
    'fedora': (build_fedora, dict(felt=cloth('#4a4a4c', weave=0.02, stitch=None), crease=cloth('#424244', weave=0.02, stitch=None),
                                  band=cloth('#1e1e1e', stitch=None)), 0.35),
    'straw_hat': (build_straw_hat, dict(straw=checker('#c8a868', '#b89858'), band=cloth('#7a2a24', stitch=None)), 0.4),
    'fingerless_gloves': (build_fingerless_gloves, dict(knit=knit('#2a2a2c'), rib=rib('#242426'), leather=leather('#2a2420')), 0.45),
    'sandals': (build_sandals, dict(sole=rubber('#3a3026'), strap=cloth('#4a4436', stitch=None), buckle=plastic('#1a1a1a')), 0.4),
}


# ------------------------------------------------------------------ wave 2, batch 5: Zomboid's headwear, face and eyes
# Refs: refs/items/clothing (ranger-hat, peaked-military-cap, captains-hat, chefs-hat, surgical-cap, visor, sweatband,
# winter-earmuffs, miner-helmet-lamp, batting-helmet, hockey-helmet, riding-helmet, boxing-headgear, swat-helmet,
# welder-mask, dust-mask, half-mask-respirator, safety-goggles, eyepatch, monocle, prescription-glasses).


def build_ranger_hat(g):
    """A campaign hat: tall crown pinched into four dents, a flat stiff brim, a leather band."""
    g.box('head', (-3.8, -10.8, -3.8), (3.8, -5.6, 3.8), 'felt')
    for x, z in ((-2.6, -2.6), (1.6, -2.6), (-2.6, 1.6), (1.6, 1.6)):                  # the Montana pinch
        g.box('head', (x, -10.9, z), (x + 1.0, -9.6, z + 1.0), 'dent')
    g.box('head', (-0.4, -11.0, -0.4), (0.4, -10.6, 0.4), 'felt')
    g.box('head', (-3.95, -6.6, -3.95), (3.95, -5.6, 3.95), 'band')
    g.box('head', (-7.2, -5.6, -7.2), (7.2, -5.2, 7.2), 'felt')                         # flat brim all round
    g.box('head', (-0.3, -6.6, -4.1), (0.3, -5.6, -3.95), 'acorn')


def build_peaked_cap(g):
    """An officer's peaked cap: stiff saucer top, black band, gloss peak, cap badge and chin strap."""
    g.box('head', (-4.45, -6.6, -4.45), (4.45, EYES_HIGH - 0.3, 4.45), 'band')
    g.box('head', (-5.6, -8.4, -6.0), (5.6, -6.6, 5.2), 'cloth')
    g.box('head', (-5.2, -8.7, -5.6), (5.2, -8.4, 4.8), 'cloth')
    g.box('head', (-5.6, -6.8, -6.0), (5.6, -6.6, 5.2), 'piping')
    g.box('head', (-3.8, EYES_HIGH - 0.6, -7.2), (3.8, EYES_HIGH - 0.25, -4.45), 'peak')
    g.box('head', (-3.9, -5.4, -4.62), (3.9, -5.1, -4.45), 'strap')
    g.box('head', (-0.9, -8.2, -6.15), (0.9, -6.5, -6.0), 'badge')


def build_captains_hat(g):
    """A white-topped skipper's cap with a black band, gold-braid peak and anchor badge."""
    g.box('head', (-4.45, -6.4, -4.45), (4.45, EYES_HIGH - 0.3, 4.45), 'band')
    g.box('head', (-5.0, -8.2, -5.2), (5.0, -6.4, 4.8), 'top')
    g.box('head', (-3.8, EYES_HIGH - 0.6, -7.0), (3.8, EYES_HIGH - 0.25, -4.45), 'peak')
    g.box('head', (-3.6, EYES_HIGH - 0.65, -6.9), (3.6, EYES_HIGH - 0.6, -5.4), 'braid')
    g.box('head', (-3.9, -5.4, -4.62), (3.9, -5.1, -4.45), 'braid')
    g.box('head', (-0.8, -6.3, -4.6), (0.8, -4.9, -4.45), 'badge')


def build_chefs_hat(g):
    """A pleated toque, tall and puffed out at the top."""
    g.box('head', (-4.3, -6.6, -4.3), (4.3, -5.2, 4.3), 'band')
    g.box('head', (-4.0, -11.4, -4.0), (4.0, -6.6, 4.0), 'cotton')
    g.box('head', (-4.6, -13.4, -4.6), (4.6, -11.4, 4.6), 'cotton')                     # the puff
    for x in (-3.2, -1.6, 0.0, 1.6, 3.2):                                               # pleats
        g.box('head', (x - 0.12, -11.4, -4.1), (x + 0.12, -6.6, -4.0), 'pleat')
        g.box('head', (x - 0.12, -11.4, 4.0), (x + 0.12, -6.6, 4.1), 'pleat')


def build_surgical_cap(g):
    d = 0.25
    g.box('head', (-4 - d, -8 - d, -4 - d), (4 + d, -5.0, 4 + d), 'cloth')
    g.box('head', (-4 - d - 0.08, -5.8, -4 - d - 0.08), (4 + d + 0.08, -5.0, 4 + d + 0.08), 'band')
    g.box('head', (-0.6, -6.4, 4 + d), (0.6, -4.2, 4 + d + 0.25), 'band')              # the tie at the back


def build_visor_cap(g):
    d = 0.25
    ring(g, -6.4, -5.0, d, 'band', t=0.2)
    g.box('head', (-3.8, -5.4, -8.2), (3.8, -5.0, -4 - d - 0.2), 'peak')
    g.box('head', (-1.6, -6.2, -4 - d - 0.3), (1.6, -5.2, -4 - d - 0.2), 'logo')


def build_sweatband(g):
    ring(g, -7.0, -5.6, 0.1, 'terry', t=0.3)
    g.box('head', (-1.0, -6.8, -4.45), (1.0, -5.8, -4.4), 'logo')


def build_winter_earmuffs(g):
    d = 0.2
    g.box('head', (-HX - 1.0, -8.0 - d - 0.4, -0.5), (HX + 1.0, -8.0 - d, 0.5), 'band')
    for s in (-1, 1):
        x0 = s * (HX + d) - (1.1 if s > 0 else 0)
        g.box('head', (x0 + (0.8 if s > 0 else -0.1), -8.2, -0.4), (x0 + (1.1 if s > 0 else 0.2), -4.6, 0.4), 'band')
        g.box('head', (x0, -5.0, -1.6), (x0 + 1.1, -1.2, 1.6), 'fluff')


def build_miner_helmet(g):
    d = 0.9
    dome(g, -9.6, -5.3, d, 'shell', ((0, 0), (0.6, 0.5), (1.4, 0.4)))
    ring(g, -5.6, -5.15, d, 'shell', t=0.6)                                              # the full brim
    g.box('head', (-1.4, -8.6, -4 - d - 0.5), (1.4, -6.6, -4 - d), 'bracket')           # the lamp bracket and lamp
    g.box('head', (-1.2, -8.4, -4 - d - 1.6), (1.2, -6.8, -4 - d - 0.5), 'lamp')
    g.box('head', (-0.9, -8.1, -4 - d - 1.7), (0.9, -7.1, -4 - d - 1.6), 'lens')
    g.box('head', (-0.25, -6.0, 4 + d), (0.25, -2.0, 4 + d + 0.25), 'cable')            # cable to the belt battery
    g.box('head', (-0.25, -5.3, 4 + d), (0.25, -5.0, 2.0), 'cable')


def build_batting_helmet(g):
    d = 0.9
    dome(g, -9.6, -5.0, d, 'shell', ((0, 0), (0.6, 0.5), (1.4, 0.4)))
    g.box('head', (-3.8, -5.4, -6.4), (3.8, -5.0, -4 - d), 'shell')                     # short peak
    g.box('head', (-HX - d, -5.0, -1.2), (HX + d, -1.4, HX + d), 'shell')               # down over the back
    for s in (-1, 1):                                                                    # ear flaps with holes
        x0 = s * (HX + d) - (0.3 if s > 0 else 0)
        g.box('head', (x0, -5.0, -2.4), (x0 + 0.3, -0.8, 0.4), 'shell')
        g.box('head', (x0 - s * 0.02, -3.4, -1.4), (x0 + 0.32, -2.4, -0.4), 'hole')
    g.box('head', (-0.4, -9.8, -3.0), (0.4, -9.6, 3.0), 'vent')
    g.box('head', (-1.4, -8.0, -4 - d - 0.06), (1.4, -6.6, -4 - d), 'logo')


def build_hockey_helmet(g):
    d = 0.8
    g.box('head', (-4 - d, -9.0, -4 - d), (4 + d, -5.2, 4 + d), 'shell')
    g.box('head', (-3.8, -9.5, -3.6), (3.8, -9.0, 4.2), 'shell')
    for x in (-2.4, -0.4, 1.6):
        g.box('head', (x, -9.55, -2.6), (x + 0.8, -9.4, 3.2), 'vent')
    for s in (-1, 1):                                                                    # ear guards
        x0 = s * (HX + d) - (0.3 if s > 0 else 0)
        g.box('head', (x0, -5.2, -1.6), (x0 + 0.3, -2.2, 1.4), 'shell')
    # the cage over the face: open bars, so the eyes show between them
    zc = -4 - d - 1.2
    for x in (-3.4, -1.7, 0.0, 1.7, 3.4):                                              # bars over the eyes start below them
        g.box('head', (x - 0.1, -5.2 if abs(x) in (0.0, 3.4) else EYES_LOW + 0.2, zc), (x + 0.1, 0.4, zc + 0.2), 'cage')
    for y in (-2.4, -0.8, 0.4):
        g.box('head', (-3.6, y - 0.1, zc), (3.6, y + 0.1, zc + 0.2), 'cage')
    for s in (-1, 1):
        g.box('head', (s * 3.6 - 0.1, -5.2, zc), (s * 3.6 + 0.1, -4.8, -4 - d), 'cage')
    g.box('head', (-1.2, 0.4, -4 - d - 1.0), (1.2, 0.8, -4 - d - 0.2), 'chin')


def build_riding_helmet(g):
    d = 0.8
    dome(g, -9.6, -5.1, d, 'shell', ((0, 0), (0.6, 0.5), (1.4, 0.45)))
    g.box('head', (-3.6, -5.5, -6.6), (3.6, -5.1, -4 - d), 'peak')
    g.box('head', (-0.4, -10.0, -0.4), (0.4, -9.6, 0.4), 'button')
    for s in (-1, 1):
        x = s * (HX + 0.1) - (0.12 if s < 0 else 0)
        g.box('head', (x, -5.1, -1.4), (x + 0.12, 0.2, -0.9), 'strap')
        g.box('head', (x, -5.1, 1.0), (x + 0.12, -2.4, 1.5), 'strap')
    g.box('head', (-1.2, 0.0, -1.4), (1.2, 0.3, -0.9), 'strap')


def build_boxing_headgear(g):
    d = 0.6
    g.box('head', (-4 - d, -8.8, -3.4), (4 + d, -1.4, 4 + d), 'leather')                 # the padded sides and back
    g.box('head', (-4 - d, -8.8, -4 - d), (4 + d, -6.2, -3.4), 'leather')                # brow bar above the eyes
    for s in (-1, 1):                                                                    # cheek pads, clear of the eye row
        x0, x1 = sorted((s * 2.4, s * (4 + d)))
        g.box('head', (x0, EYES_LOW + 0.2, -4 - d), (x1, -0.6, -3.4), 'leather')
    g.box('head', (-3.6, -0.4, -4 - d - 0.3), (3.6, 0.6, -3.6), 'leather')                # chin strap pad
    g.box('head', (-1.4, -8.6, -4 - d - 0.06), (1.4, -7.0, -4 - d), 'logo')
    g.box('head', (-0.6, -9.0, 4 + d), (0.6, -2.0, 4 + d + 0.4), 'lace')


def build_swat_helmet(g):
    d = 1.0
    dome(g, -9.8, -5.0, d, 'shell', ((0, 0), (0.6, 0.5), (1.4, 0.45)))
    g.box('head', (-HX - d, -5.0, 0.0), (HX + d, -2.6, HX + d), 'shell')                 # down round the back
    for s in (-1, 1):
        x0 = s * (HX + d) - (0.35 if s > 0 else 0)
        g.box('head', (x0, -6.4, -2.4), (x0 + 0.35, -5.0, 3.0), 'rail')
    # the ballistic visor flipped up over the brow
    g.box('head', (-4.2, -7.6, -4 - d - 0.6), (4.2, EYES_HIGH - 0.9, -4 - d - 0.2), 'visor')
    g.box('head', (-0.9, -6.4, -4 - d - 0.2), (0.9, -5.4, -4 - d), 'shroud')
    g.box('head', (-3.0, -9.0, HX + d), (3.0, -7.4, HX + d + 0.08), 'patch')             # POLICE on the back
    for s in (-1, 1):
        x = s * (HX + 0.1) - (0.12 if s < 0 else 0)
        g.box('head', (x, -5.0, -1.2), (x + 0.12, 0.2, -0.7), 'strap')
    g.box('head', (-2.4, 0.0, -1.2), (2.4, 0.3, -0.7), 'strap')


def build_welder_mask(g):
    """A welding helmet with its shield flipped up off the face, the dark window showing."""
    d = 0.4
    ring(g, -6.6, -5.6, d, 'harness', t=0.3)
    g.box('head', (-0.6, -8.0 - d - 0.3, -3.0), (0.6, -8.0 - d, 3.0), 'harness')
    g.box('head', (-4.6, -11.0, -4 - d - 2.6), (4.6, -7.2, -4 - d - 1.6), 'shield')        # the shield tilted up on top
    g.box('head', (-4.6, -11.6, -4 - d - 1.6), (4.6, -10.6, 1.4), 'shield')
    g.box('head', (-2.2, -10.4, -4 - d - 2.7), (2.2, -8.6, -4 - d - 2.6), 'window')
    for s in (-1, 1):
        x0 = s * (HX + d) - (0.4 if s > 0 else 0)
        g.box('head', (x0, -7.4, -2.0), (x0 + 0.4, -6.0, -0.6), 'knob')


def build_dust_mask(g):
    z = -4.35
    g.box('head', (-2.4, EYES_LOW + 0.4, z - 0.9), (2.4, -0.2, z), 'cup')
    g.box('head', (-1.8, EYES_LOW + 0.7, z - 1.1), (1.8, -0.5, z - 0.9), 'cup')
    g.box('head', (-0.5, -2.0, z - 1.25), (0.5, -1.2, z - 1.1), 'valve')
    g.box('head', (-1.4, EYES_LOW + 0.4, z - 0.95), (1.4, EYES_LOW + 0.55, z - 0.85), 'wire')
    for s in (-1, 1):
        x = -4.25 if s < 0 else 4.15
        g.box('head', (x, -4.6, z), (x + 0.1, -4.4, 4.25), 'band')
        g.box('head', (x, -1.4, z), (x + 0.1, -1.2, 4.25), 'band')


def build_half_mask_respirator(g):
    z = -4.35
    g.box('head', (-2.6, EYES_LOW + 0.3, z - 0.9), (2.6, 0.2, z), 'rubber')
    for s in (-1, 1):                                                                    # twin cartridges on the cheeks
        x0, x1 = sorted((s * 1.6, s * 3.8))
        g.box('head', (x0, -2.6, z - 1.8), (x1, -0.6, z - 0.4), 'cartridge')
        g.box('head', (x0 + 0.2, -2.4, z - 1.85), (x1 - 0.2, -0.8, z - 1.8), 'filter')
    g.box('head', (-0.7, -1.4, z - 1.2), (0.7, -0.2, z - 0.9), 'valve')
    for s in (-1, 1):
        x = -4.25 if s < 0 else 4.15
        g.box('head', (x, -5.0, z + 0.2), (x + 0.12, -4.6, 4.25), 'strap')
        g.box('head', (x, -0.6, z + 0.2), (x + 0.12, -0.2, 4.25), 'strap')


def build_safety_goggles(g):
    """Vented chemical goggles; the clear lens is left open so the eyes show through."""
    z = -4.4
    top, bot = EYES_HIGH - 0.8, EYES_LOW + 0.7
    g.box('head', (-3.9, top, z - 0.8), (3.9, top + 0.35, z), 'frame')
    g.box('head', (-3.9, bot - 0.35, z - 0.8), (-0.5, bot, z), 'frame')
    g.box('head', (0.5, bot - 0.35, z - 0.8), (3.9, bot, z), 'frame')
    g.box('head', (-0.5, bot - 0.8, z - 0.8), (0.5, bot - 0.2, z), 'frame')
    for s in (-1, 1):
        x0 = -3.9 if s < 0 else 3.45
        g.box('head', (x0, top + 0.35, z - 0.8), (x0 + 0.45, bot - 0.35, z), 'frame')
        g.box('head', (x0 + (0.1 if s > 0 else 0), top + 0.5, z - 0.82), (x0 + (0.35 if s > 0 else 0.25), top + 0.8, z - 0.78), 'vent')
        x = -4.32 if s < 0 else 4.2
        g.box('head', (x, top + 0.3, z), (x + 0.12, bot - 0.3, 4.32), 'strap')
    g.box('head', (-4.32, top + 0.3, 4.2), (4.32, bot - 0.3, 4.32), 'strap')


def build_eyepatch(g):
    z = -4.42
    g.box('head', (0.4, EYES_HIGH - 0.3, z - 0.2), (3.0, EYES_LOW + 0.3, z), 'patch')   # over the left eye only
    g.box('head', (-4.32, -5.6, z), (0.4, -5.4, z + 0.05), 'string')                    # the string over the brow
    g.box('head', (-4.32, -5.6, z), (-4.2, -5.4, 4.32), 'string')                       # and round the head
    g.box('head', (4.2, -3.6, z), (4.32, -3.4, 4.32), 'string')
    g.box('head', (-4.32, -5.6, 4.2), (4.32, -5.4, 4.32), 'string')


def build_monocle(g):
    z = -4.45
    x0, x1 = 0.5, 3.4
    g.box('head', (x0, EYES_HIGH - 0.3, z - 0.15), (x1, EYES_HIGH - 0.15, z), 'rim')
    g.box('head', (x0, EYES_LOW + 0.15, z - 0.15), (x1, EYES_LOW + 0.3, z), 'rim')
    g.box('head', (x0, EYES_HIGH - 0.15, z - 0.15), (x0 + 0.15, EYES_LOW + 0.15, z), 'rim')
    g.box('head', (x1 - 0.15, EYES_HIGH - 0.15, z - 0.15), (x1, EYES_LOW + 0.15, z), 'rim')
    g.box('head', (2.6, EYES_LOW + 0.3, z - 0.1), (2.75, 0.4, z), 'chain')               # the chain down to the collar


def build_prescription_glasses(g):
    glasses(g, None, 'frame')


CIVILIAN_2 = {
    'ranger_hat': (build_ranger_hat, dict(felt=cloth('#7a6446', weave=0.02, stitch=None), dent=cloth('#6e5a3e', weave=0.02, stitch=None),
                                          band=leather('#3a2a1a'), acorn=metal('brass')), 0.35),
    'peaked_cap': (build_peaked_cap, dict(band=cloth('#1a1a1c', stitch=None), cloth=cloth('#4a5038', weave=0.04),
                                          piping=flat('#7a2a24'), peak=plastic('#111111', gloss=0.5), strap=leather('#1a1a1a'),
                                          badge=metal('brass')), 0.3),
    'captains_hat': (build_captains_hat, dict(band=cloth('#1a1a1c', stitch=None), top=cloth('#e4e2dc', weave=0.02),
                                              peak=plastic('#111111', gloss=0.5), braid=metal('brass'), badge=metal('brass')), 0.3),
    'chefs_hat': (build_chefs_hat, dict(cotton=cloth('#ecebe6', weave=0.03, stitch=None), band=cloth('#e0dfda', stitch=None),
                                        pleat=flat('#c8c6c0', ink=False)), 0.3),
    'surgical_cap': (build_surgical_cap, dict(cloth=cloth('#3f6f6a', weave=0.03, stitch=None), band=cloth('#386360', stitch=None)), 0.3),
    'visor_cap': (build_visor_cap, dict(band=cloth('#e4e2dc', stitch=None), peak=plastic('#2a4a7a'), logo=flat('#2a4a7a')), 0.3),
    'sweatband': (build_sweatband, dict(terry=fur('#d8d4c8'), logo=flat('#7a2a24')), 0.3),
    'winter_earmuffs': (build_winter_earmuffs, dict(band=plastic('#1e1e1e'), fluff=fur('#c8b8a8')), 0.3),
    'miner_helmet': (build_miner_helmet, dict(shell=plastic('#c8a82a'), bracket=metal('steel'), lamp=plastic('#1e1e1e'),
                                              lens=plastic('#e8e6d8', gloss=0.5), cable=rubber('#1a1a1a')), 0.5),
    'batting_helmet': (build_batting_helmet, dict(shell=plastic('#1e2a5a', gloss=0.35), hole=flat('#0a0a0a', ink=False),
                                                  vent=flat('#0a0a0a', ink=False), logo=flat('#e0dcd0')), 0.4),
    'hockey_helmet': (build_hockey_helmet, dict(shell=plastic('#1e1f22', gloss=0.3), vent=flat('#0a0a0a', ink=False),
                                                cage=metal('steel'), chin=plastic('#1e1f22')), 0.4),
    'riding_helmet': (build_riding_helmet, dict(shell=cloth('#1e1e22', weave=0.03, stitch=None), peak=cloth('#1a1a1e', stitch=None),
                                                button=cloth('#1a1a1e'), strap=leather('#1a1a1a')), 0.35),
    'boxing_headgear': (build_boxing_headgear, dict(leather=leather('#8a2a24', gloss=0.2), logo=flat('#e0dcd0'),
                                                    lace=flat('#e0dcd0')), 0.45),
    'swat_helmet': (build_swat_helmet, dict(shell=plastic('#1c1d1f', gloss=0.2), rail=plastic('#111213'),
                                            visor=plastic('#3a4048', gloss=0.6), shroud=metal('steel'),
                                            patch=stencil('#1c1d1f', '#c8c0a8'), strap=cloth('#1a1a1a', stitch=None)), 0.4),
    'welder_mask': (build_welder_mask, dict(harness=plastic('#2a2a2a'), shield=plastic('#1e1f22', gloss=0.2),
                                            window=plastic('#2a4a2a', gloss=0.6), knob=plastic('#1a1a1a')), 0.5),
    'dust_mask': (build_dust_mask, dict(cup=cloth('#e4e2dc', weave=0.06, stitch=None), valve=plastic('#d8a82a'),
                                        wire=flat('#c8c4bb'), band=flat('#c8642a')), 0.5),
    'half_mask_respirator': (build_half_mask_respirator, dict(rubber=rubber('#3a3c3e'), cartridge=plastic('#c8a82a'),
                                                              filter=plastic('#d8d4c8'), valve=plastic('#2a2a2a'),
                                                              strap=rubber('#2a2a2c')), 0.45),
    'safety_goggles': (build_safety_goggles, dict(frame=plastic('#c8d0d4', gloss=0.3), vent=flat('#4a5054', ink=False),
                                                  strap=cloth('#2a4a7a', stitch=None)), 0.3),
    'eyepatch': (build_eyepatch, dict(patch=leather('#141414', gloss=0.2), string=flat('#141414')), 0.2),
    'monocle': (build_monocle, dict(rim=metal('brass'), chain=metal('brass')), 0.1),
    'prescription_glasses': (build_prescription_glasses, dict(frame=plastic('#2a2a2c')), 0.1),
}


# ------------------------------------------------------------------ wave 2, batch 6: the rest of Zomboid's wardrobe
# Refs: refs/items/clothing (hunting-vest, foreman-vest, waistcoat, leather-biker-vest, sheepskin-vest, sheepskin-jacket,
# padded-jacket, paramedic-uniform, apron, leather-apron, long-johns, socks, legwarmers, riding-boots, bowling-shoes,
# flip-flops, boxing-gloves, pyjamas, sports-jersey, postal-uniform, cycling-kit, fishing-waders, wetsuit,
# crafted-scrap-metal-armour, tyre-armour). Scrap and tyre armour are survivor-made: B42's own idea, our shapes.


def vstripes(a, b, w=0.55):
    """Pinstripes running down the cloth (pyjamas): bands across x and z so every face reads striped."""
    def fn(f):
        u = f.P[..., 0] + f.P[..., 2]
        band = (np.floor(u / w) % 3) == 0
        c = np.where(band[..., None], P.col(b), P.col(a)) * (1 + (P.white(f.shape, f.rng) - 0.5) * 0.08)[..., None]
        return toon(f, grade(c))
    return fn


def open_vest(g, d, mat, y0=0.4, y1=10.6, gap=0.5, arm=True):
    """A vest open down the front: two front panels either side of a gap, the back whole, deep armholes."""
    lo, hi = BOXES['body']
    g.box('body', (lo[0] - d, y0, hi[2]), (hi[0] + d, y1, hi[2] + d), mat)                      # back
    for s in (-1, 1):
        xo = lo[0] - d if s < 0 else hi[0]
        g.box('body', (xo, y0, lo[2] - d), (xo + d, y1, hi[2]), mat)                            # sides
        x0, x1 = sorted((s * gap, s * (4 + d)))
        g.box('body', (x0, y0, lo[2] - d), (x1, y1, lo[2]), mat)                                # front panel
        if arm:
            sx0, sx1 = sorted((s * 1.6, s * (4 + d)))
            g.box('body', (sx0, y0 - 0.3 - d, lo[2] - d), (sx1, y0, hi[2] + d), mat)            # over the shoulder


def build_hunting_vest(g):
    """Blaze orange: open mesh body, solid shoulders, shell loops across the chest and a game pouch behind."""
    d = VEST_D - 0.2
    open_vest(g, d, 'mesh', gap=0.15)
    g.front('body', d, -0.15, 0.4, 0.15, 10.6, 'zip', t=0.12)
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.6, s * 3.6))
        g.front('body', d, x0, 6.8, x1, 9.6, 'solid', t=0.3)                                  # hand pockets
        g.front('body', d + 0.3, x0, 6.6, x1, 7.2, 'solid', t=0.12)
    for i in range(6):                                                                       # shotgun shell loops
        x = -3.6 + i * 0.5
        g.front('body', d, x, 3.2, x + 0.34, 4.8, 'elastic', t=0.12)
        g.front('body', d + 0.12, x + 0.04, 2.6, x + 0.3, 3.2, 'shell', t=0.3)
        g.front('body', d + 0.12, x + 0.04, 4.6, x + 0.3, 4.9, 'brass', t=0.3)
    g.back('body', d, -3.8, 6.4, 3.8, 10.4, 'solid', t=0.6)                                   # game bag across the back
    g.back('body', d + 0.6, -3.6, 6.4, 3.6, 7.0, 'solid', t=0.1)
    g.back('body', d, -2.4, 1.4, 2.4, 3.2, 'patch', t=0.04)                                   # licence holder


def build_foreman_vest(g):
    """A site foreman's surveyor vest: lime two-tone, a dozen pockets, pen slots and a radio tab."""
    d = VEST_D
    open_vest(g, d, 'mesh', gap=0.15)
    g.box('body', (-4 - d, 6.6, -2 - d), (4 + d, 10.6, 2 + d), 'solid')                        # the solid lower half
    g.front('body', d, -0.15, 0.4, 0.15, 10.6, 'zip', t=0.12)
    g.shell('body', 5.2, 5.9, d + 0.05, 'tape')
    for s in (-1, 1):
        x0, x1 = (-3.6, -2.4) if s < 0 else (2.4, 3.6)
        g.box('body', (x0, -0.4 - d, -2 - d - 0.05), (x1, 10.6, -2 - d), 'tape')
        g.box('body', (x0, -0.4 - d, 2 + d), (x1, 10.6, 2 + d + 0.05), 'tape')
        for (a, b) in ((0.6, 2.1), (2.2, 3.7)):                                             # pocket banks
            xa, xb = sorted((s * a, s * b))
            g.front('body', d, xa, 7.2, xb, 9.8, 'solid', t=0.35)
            g.front('body', d + 0.35, xa, 7.0, xb, 7.5, 'flap', t=0.1)
    for i, c in enumerate(('pen_a', 'pen_b', 'pen_a')):                                     # pens in the chest slots
        x = 1.0 + i * 0.5
        g.front('body', d, x, 2.0, x + 0.28, 3.6, c, t=0.28)
    g.front('body', d, 0.8, 3.0, 2.6, 4.6, 'solid', t=0.2)
    g.front('body', d, -3.4, 1.0, -2.6, 2.4, 'flap', t=0.12)                                  # mic tab
    g.front('body', d, -3.8, 3.0, -2.0, 4.8, 'id', t=0.08)                                    # clear ID window


def build_waistcoat(g):
    """A three-piece suit's waistcoat: buttoned V front, welt pockets, satin back with a cinch."""
    d = SHIRT + 0.22
    lo, hi = BOXES['body']
    g.box('body', (lo[0] - d, 0.6, hi[2]), (hi[0] + d, 10.2, hi[2] + d), 'back')
    for s in (-1, 1):
        xo = lo[0] - d if s < 0 else hi[0]
        g.box('body', (xo, 2.0, lo[2] - d), (xo + d, 10.2, hi[2]), 'wool')
        g.box('body', (s * 0.0 if s > 0 else -2.0, 3.0, lo[2] - d), (2.0 if s > 0 else 0.0, 10.6, lo[2]), 'wool')
        x0, x1 = sorted((s * 2.0, s * (4 + d)))
        g.box('body', (x0, 0.6, lo[2] - d), (x1, 10.6, lo[2]), 'wool')
        x0, x1 = sorted((s * 1.4, s * 2.6))
        g.box('body', (x0, -0.3, -2 - d), (x1, 0.6, 2 + d), 'wool')
        for y in (5.0, 8.2):
            xa, xb = sorted((s * 1.4, s * 3.2))
            g.front('body', d, xa, y, xb, y + 0.3, 'welt', t=0.1)
    g.box('body', (-0.4, 10.6, lo[2] - d), (0.4, 11.4, lo[2]), 'wool')                        # the pointed hem
    buttons(g, 'body', d, 0, (3.6, 4.8, 6.0, 7.2, 8.4, 9.6), 'button')
    g.back('body', d, -1.6, 7.8, 1.6, 8.4, 'cinch', t=0.08)
    g.back('body', d + 0.08, -0.3, 7.7, 0.3, 8.5, 'buckle', t=0.1)
    g.front('body', d + 0.05, -2.8, 4.9, -2.2, 5.0, 'chain', t=0.2)                            # watch chain
    g.front('body', d + 0.1, -2.4, 5.0, 0.0, 5.3, 'chain', t=0.08)


def build_leather_biker_vest(g):
    """A cut-down leather vest: snap front, lapel collar, a back panel for club colours (left blank, original)."""
    d = JACKET + 0.12
    open_vest(g, d, 'leather', y0=0.2, y1=10.8, gap=0.4)
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.4, s * 1.8))
        g.box('body', (x0, -0.3, -2 - d - 0.3), (x1, 3.4, -2 - d), 'leather')                 # lapels laid back
        buttons(g, 'body', d, s * 0.9, (4.4, 6.2, 8.0, 9.8), 'snap')
        xa, xb = sorted((s * 1.6, s * 3.6))
        g.front('body', d, xa, 7.4, xb, 7.7, 'stitch', t=0.06)
        g.front('body', d, s * 2.2 - 0.6, 2.0, s * 2.2 + 0.6, 3.2, 'pin', t=0.14)              # pins and a patch
    g.back('body', d, -3.4, 1.4, 3.4, 2.4, 'rocker', t=0.06)                                   # top rocker, a plain tan arc
    g.back('body', d, -2.4, 3.0, 2.4, 7.2, 'emblem', t=0.06)
    g.back('body', d, -3.4, 7.8, 3.4, 8.8, 'rocker', t=0.06)
    g.back('body', d, -1.4, 10.0, 1.4, 10.6, 'chain', t=0.2)                                   # a wallet chain looped behind
    g.box('body', (-4 - d - 0.25, 1.4, -1.2), (-4 - d, 3.6, 1.2), 'lace')                      # laced sides


def build_sheepskin_vest(g):
    """Shearling turned inside out: suede outside, the wool curling out at every edge."""
    d = JACKET + 0.22
    open_vest(g, d, 'suede', y0=0.2, y1=10.4, gap=0.3)
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.3, s * 1.0))
        g.box('body', (x0, 0.2, -2 - d - 0.25), (x1, 10.4, -2 - d), 'wool')                    # fleece front edges
        g.box('body', (-3.4 if s < 0 else 1.6, -0.3 - d - 0.2, -2 - d - 0.2), (-1.6 if s < 0 else 3.4, 0.4, 2 + d + 0.2), 'wool')
        xa, xb = sorted((s * 1.4, s * 3.4))
        g.front('body', d, xa, 6.8, xb, 7.2, 'wool', t=0.2)                                    # fleece-lipped pockets
        g.front('body', d, xa, 7.2, xb, 9.4, 'suede', t=0.12)
        g.front('body', d + 0.12, xa + 0.2, 8.0, xb - 0.2, 8.1, 'stitch', t=0.04)
    g.shell('body', 10.2, 10.8, d + 0.2, 'wool')
    buttons(g, 'body', d + 0.25, -0.65, (3.4, 6.2), 'toggle')


def build_sheepskin_jacket(g):
    """A shearling coat: suede with wide fleece collar and cuffs, toggled front, patch pockets."""
    d = JACKET + 0.18
    g.shell('body', 0, 12, d, 'suede')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 1.4, d + 0.05, 'suede'))
    g.box('body', (-3.8, -1.4, -2 - d - 0.45), (3.8, 2.2, -2 - d), 'wool')                     # wide fleece collar
    g.box('body', (-4.2, -1.6, -2 - d), (4.2, 0.4, 2 + d + 0.5), 'wool')
    g.front('body', d, -1.0, 2.2, -0.4, 12.0, 'wool', t=0.18)
    for y in (4.0, 6.6, 9.2):
        g.front('body', d + 0.18, -1.4, y, 0.2, y + 0.4, 'toggle', t=0.2)
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.0, s * 3.6))
        g.front('body', d, x0, 8.0, x1, 10.8, 'suede', t=0.16)
        g.front('body', d + 0.16, x0, 8.0, x1, 8.5, 'wool', t=0.1)
        g.side('body', d, s, -1.8, 0.0, 1.8, 12.0, 'seam', t=0.03)
    sleeves(g, d, 'suede', end=7.6, cuff='wool', cuff_d=0.3)


def build_padded_jacket(g):
    """A quilted work jacket: diamond-stitched canvas, corduroy collar, two chest pockets and a hand-warmer pair."""
    d = JACKET + 0.12
    g.shell('body', 0, 11.6, d, 'quilt')
    g.shell('body', 11.0, 12.2, d + 0.1, 'rib')
    g.box('body', (-3.4, -1.4, -2 - d - 0.3), (3.4, 0.3, 2 + d + 0.3), 'cord')
    g.front('body', d, -0.25, -1.2, 0.25, 12.0, 'zip', t=0.12)
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.0, s * 3.4))
        g.front('body', d, x0, 2.4, x1, 4.8, 'canvas', t=0.18)
        g.front('body', d + 0.18, x0, 2.2, x1, 2.9, 'canvas', t=0.12)
        buttons(g, 'body', d + 0.3, s * 2.2, (2.4,), 'snap')
        g.front('body', d, s * 3.2 - 0.15, 6.8, s * 3.2 + 0.15, 9.6, 'canvas', t=0.14)
    sleeves(g, d, 'quilt', end=7.6, cuff='rib', cuff_d=0.12)
    g.both(ARMS, lambda p, s: g.side(p, d, s, -1.6, 0.0, 1.6, 2.2, 'canvas', t=0.1))         # elbow-proof shoulder panels


def build_hunting_jacket(g):
    """A camo hunting coat: game pocket across the back, shell-loop chest, blaze orange reversible hood rolled up."""
    d = JACKET + 0.15
    g.shell('body', 0, 12, d, 'shell')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 2.0, d + 0.05, 'shell'))
    g.box('body', (-3.4, -1.6, -2 - d - 0.3), (3.4, 0.2, 2 + d + 0.3), 'shell')
    g.box('body', (-3.6, -2.2, 0.8), (3.6, -0.6, 2 + d + 1.0), 'blaze')                        # hood rolled into the collar
    g.front('body', d, -0.25, -1.4, 0.25, 12.0, 'zip', t=0.12)
    for s in (-1, 1):
        pouch(g, 'body', d, s * 0.8, 7.0, s * 3.8, 10.4, 'pocket', 'flap', t=0.6)
    for i in range(5):
        x = 1.0 + i * 0.55
        g.front('body', d, x, 2.6, x + 0.36, 4.2, 'elastic', t=0.12)
        g.front('body', d + 0.12, x + 0.04, 2.0, x + 0.32, 2.6, 'cartridge', t=0.3)
    g.back('body', d, -4 - d, 6.0, 4 + d, 11.6, 'pocket', t=0.5)                              # game bag
    g.back('body', d + 0.5, -4 - d, 6.0, 4 + d, 6.5, 'flap', t=0.12)
    sleeves(g, d, 'shell', end=7.8, cuff='knit', cuff_d=0.1)


def build_paramedic_jacket(g):
    """An EMS jacket: navy shell, hi-vis yellow shoulder yoke, reflective hoops, star-of-life patch, radio loop."""
    d = JACKET + 0.05
    g.shell('body', 0, 11.6, d, 'shell')
    g.shell('body', -0.3, 2.6, d + 0.05, 'yoke')
    g.box('body', (-3.4, -1.4, -2 - d - 0.3), (3.4, 0.3, 2 + d + 0.3), 'shell')
    g.front('body', d, -0.25, -1.2, 0.25, 11.6, 'zip', t=0.12)
    reflective_bands(g, d, 'reflect', arm_ys=(4.4, 6.0), body_ys=(7.6, 9.0))
    g.both(ARMS, lambda p, s: g.shell(p, -2.0, 2.0, d + 0.06, 'yoke', box=((BOXES[p][0][0], -2, -2), (BOXES[p][1][0], 2, 2))))
    sleeves(g, d, 'shell', end=7.6, cuff='shell', cuff_d=0.1)
    g.front('body', d + 0.05, -3.4, 3.2, -1.6, 5.0, 'star', t=0.06)                            # star of life
    g.back('body', d, -3.0, 3.4, 3.0, 5.0, 'stencil', t=0.06)                                  # PARAMEDIC across the back
    g.front('body', d + 0.05, 1.2, 3.4, 2.4, 4.8, 'loop', t=0.3)
    g.front('body', d + 0.3, 1.3, 2.4, 2.3, 4.4, 'radio', t=0.6)
    g.front('body', d, -3.6, 8.2, -1.0, 10.6, 'shell', t=0.2)
    g.front('body', d, 1.0, 8.2, 3.6, 10.6, 'shell', t=0.2)


def build_apron(g):
    """A kitchen bib apron: neck loop, waist ties knotted at the back, a long front pocket."""
    d = VEST_D - 0.5
    lo = BOXES['body'][0]
    g.box('body', (-2.6, 1.0, lo[2] - d - 0.12), (2.6, 12.0, lo[2] - d), 'cloth')
    g.both(LEGS, lambda p, s: g.front(p, PANTS + 0.4, -2.0 if s < 0 else -2.0, -0.2, 2.0, 5.4, 'cloth', t=0.12))
    g.front('body', d + 0.12, -2.4, 7.4, 2.4, 9.6, 'pocket', t=0.08)
    g.front('body', d + 0.2, -0.05, 7.4, 0.05, 9.6, 'stitch', t=0.02)
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.8, s * 2.4))
        g.box('body', (x0, -0.3, lo[2] - d - 0.06), (x1, 1.0, 1.8), 'tie')                     # neck loop
    g.shell('body', 6.0, 6.4, SHIRT + 0.6, 'tie')                                              # waist ties
    g.back('body', SHIRT + 0.6, -0.8, 5.8, 0.8, 6.6, 'tie', t=0.3)
    g.back('body', SHIRT + 0.9, -0.6, 6.6, -0.2, 8.6, 'tie', t=0.1)
    g.back('body', SHIRT + 0.9, 0.2, 6.6, 0.6, 8.2, 'tie', t=0.1)
    g.front('body', d + 0.12, 1.4, 4.0, 2.2, 5.2, 'stain', t=0.02)


def build_leather_apron(g):
    """A blacksmith's or butcher's split-leg leather apron: thick hide, cross-back straps, a brass ring and a hammer loop."""
    d = VEST_D - 0.4
    lo = BOXES['body'][0]
    g.box('body', (-3.4, 0.6, lo[2] - d - 0.3), (3.4, 12.0, lo[2] - d), 'leather')
    def leg(p, s):                                                                             # split skirts strapped to each leg
        g.front(p, PANTS + 0.5, -2.2, -0.2, 2.2, 8.0, 'leather', t=0.3)
        g.side(p, PANTS + 0.5, s, -2.0, 6.0, 0.0, 6.6, 'strap', t=0.1)
    g.both(LEGS, leg)
    for s in (-1, 1):
        x0, x1 = sorted((s * 2.2, s * 3.2))
        g.box('body', (x0, -0.3, lo[2] - d - 0.1), (x1, 0.6, 2 + SHIRT + 0.3), 'strap')
        g.front('body', d + 0.3, x0, 0.6, x1, 1.2, 'ring', t=0.15)
    for (xa, xb) in ((-3.2, 2.4), (2.4, -3.2)):                                                # straps cross on the back
        for i in range(14):
            f = i / 13
            x = xa + (xb - xa) * f
            g.back('body', SHIRT + 0.3, x - 0.45, 0.4 + f * 6.6, x + 0.45, 1.1 + f * 6.6, 'strap', t=0.1)
    g.front('body', d + 0.3, -3.0, 4.4, -1.0, 6.6, 'leather', t=0.2)                           # tool pocket
    g.front('body', d + 0.3, 1.2, 5.0, 2.6, 5.6, 'strap', t=0.25)                              # hammer loop
    g.front('body', d + 0.3, -2.0, 8.6, 0.6, 10.2, 'scorch', t=0.02)


def build_long_johns(g):
    """A waffle-knit union suit next to the skin: button placket, ribbed cuffs and ankles."""
    d = SHIRT - 0.12
    g.shell('body', 0, 12, d, 'waffle')
    crew(g, d + 0.02, 'rib')
    g.front('body', d, -0.35, 0.2, 0.35, 5.6, 'waffle', t=0.1)
    buttons(g, 'body', d + 0.1, 0, (1.0, 2.6, 4.2), 'button')
    sleeves(g, d, 'waffle', end=8.2, cuff='rib', cuff_d=0.06)
    def leg(p, s):
        g.shell(p, -0.2, 11.0, PANTS - 0.2, 'waffle')
        g.shell(p, 10.4, 11.2, PANTS - 0.14, 'rib')
    g.both(LEGS, leg)


def build_socks(g):
    """Thick wool boot socks: ribbed tubes up the calf, a red stripe band and a darker heel and toe."""
    def foot(p, s):
        g.shell(p, 6.0, 11.4, 0.16, 'knit')
        g.shell(p, 6.0, 7.2, 0.24, 'rib')
        g.shell(p, 7.2, 7.6, 0.25, 'stripe')
        g.box(p, (-2.12, 10.0, -4.4), (2.12, 12.0, -2.0), 'heel')                              # toe
        g.box(p, (-2.12, 9.8, 1.6), (2.12, 12.0, 2.18), 'heel')                                # heel
        g.box(p, (-2.12, 11.4, -4.4), (2.12, 12.0, 2.18), 'knit')
    g.both(LEGS, foot)


def build_legwarmers(g):
    """Slouchy knit legwarmers bunched round the calf in rolls."""
    def leg(p, s):
        for i, y in enumerate((5.4, 6.6, 7.8, 9.0, 10.2)):
            g.shell(p, y, y + 1.2, PANTS + 0.4 + (0.12 if i % 2 else 0.0), 'knit')
        g.shell(p, 5.0, 5.6, PANTS + 0.55, 'rib')
    g.both(LEGS, leg)


def build_riding_boots(g):
    """Tall English riding boots: stiff knee-high shaft, a spur rest at the heel, a zip down the back."""
    def foot(p, s):
        shoe(g, p, s, 1.6, d=0.5, toe=3.1, sole='sole')
        g.shell(p, 1.2, 2.0, 0.62, 'upper')                                                    # the Spanish top
        g.front(p, 0.62, -2.0, 1.2, 2.0, 1.6, 'upper', t=0.3)
        g.back(p, 0.5, -0.2, 2.0, 0.2, 10.6, 'zip', t=0.1)
        g.box(p, (-2.25, 11.0, 0.8), (2.25, 12.3, 2.4), 'heel')
        g.back(p, 0.5, -1.4, 10.2, 1.4, 10.6, 'rest', t=0.3)
        g.box(p, (-1.9, 10.2, -5.3), (1.9, 11.4, -4.6), 'upper')
    g.both(LEGS, foot)


def build_bowling_shoes(g):
    """Two-tone rental bowling shoes: red, cream and navy panels and a size number on the heel."""
    def foot(p, s):
        shoe(g, p, s, 10.0, d=0.42, toe=3.0, sole='sole', upper='cream')
        g.side(p, 0.42, s, -2.4, 10.0, 0.4, 11.4, 'red', t=0.06)
        g.side(p, 0.42, -s, -2.4, 10.0, 0.4, 11.4, 'red', t=0.06)
        g.box(p, (-2.2, 10.0, 0.6), (2.2, 11.4, 2.5), 'navy')                                  # heel counter
        g.box(p, (-0.5, 10.2, 2.5), (0.5, 10.9, 2.6), 'number')
        laces(g, p, 0.42, 10.0, 10.8, 0.4)
    g.both(LEGS, foot)


def build_flip_flops(g):
    """Rubber thongs: a foam sole and a Y strap from between the toes."""
    def foot(p, s):
        g.box(p, (-2.35, 11.6, -5.0), (2.35, 12.2, 2.3), 'sole')
        g.box(p, (-2.25, 11.5, -5.0), (2.25, 11.62, 2.2), 'foot')
        g.box(p, (-0.2, 11.0, -4.4), (0.2, 11.6, -4.0), 'strap')
        for x0, x1 in ((-2.4, -0.2), (0.2, 2.4)):
            g.box(p, (x0, 10.6, -4.2), (x1, 11.0, -1.2), 'strap')
    g.both(LEGS, foot)


def build_boxing_gloves(g):
    """Laced boxing gloves: a big padded fist, thumb lump and white cuff."""
    def hand(p, s):
        lo, hi = BOXES[p]
        g.box(p, (lo[0] - 1.1, 7.6, lo[2] - 1.3), (hi[0] + 1.1, 10.6, hi[2] + 1.1), 'leather')
        g.box(p, (lo[0] - 0.9, 10.6, lo[2] - 1.0), (hi[0] + 0.9, 10.9, hi[2] + 0.8), 'leather')
        g.side(p, 1.1, -s, -1.8, 8.2, 0.2, 9.8, 'leather', t=0.5)                               # thumb, inner side
        g.shell(p, 5.6, 7.6, 0.75, 'cuff')
        g.front(p, 0.75, -0.6, 5.8, 0.6, 7.4, 'lace', t=0.1)
        g.side(p, 1.1, s, -1.0, 8.4, 1.0, 9.4, 'logo', t=0.05)
    g.both(ARMS, hand)


def build_pyjama_top(g):
    """Striped flannel pyjama jacket: notch collar, piped edges, one breast pocket."""
    d = SHIRT + 0.08
    g.shell('body', 0, 12, d, 'stripe')
    collar_flaps(g, d, 'piping')
    g.front('body', d, -0.3, 0.6, 0.3, 12.0, 'piping', t=0.08)
    buttons(g, 'body', d + 0.08, 0, (2.4, 4.6, 6.8, 9.0), 'button')
    g.front('body', d, 1.2, 2.4, 3.2, 4.4, 'stripe', t=0.1)
    g.front('body', d + 0.1, 1.2, 2.4, 3.2, 2.6, 'piping', t=0.04)
    sleeves(g, d, 'stripe', end=7.8, cuff='piping', cuff_d=0.06)


def build_pyjama_bottoms(g):
    d = PANTS - 0.05
    g.shell('body', 9.8, 12, d, 'stripe')
    g.shell('body', 9.6, 10.4, d + 0.08, 'piping')
    g.front('body', d + 0.08, -0.6, 10.4, -0.4, 11.4, 'tie', t=0.1)
    g.front('body', d + 0.08, 0.4, 10.4, 0.6, 11.2, 'tie', t=0.1)
    def leg(p, s):
        g.shell(p, 0, 11.2, d, 'stripe')
        g.shell(p, 10.8, 11.4, d + 0.08, 'piping')
    g.both(LEGS, leg)


def build_sports_jersey(g):
    """A mesh basketball or football jersey: big number front and back, contrast shoulders and side panels."""
    d = SHIRT + 0.1
    g.shell('body', 0, 12, d, 'mesh')
    crew(g, d + 0.02, 'trim')
    for s in (-1, 1):
        g.side('body', d, s, -2.2, 3.0, 2.2, 12.0, 'trim', t=0.05)
    g.front('body', d, -2.0, 3.2, 2.0, 7.6, 'number', t=0.05)
    g.back('body', d, -2.4, 1.0, 2.4, 2.2, 'name', t=0.05)
    g.back('body', d, -2.4, 2.8, 2.4, 8.2, 'number', t=0.05)
    sleeves(g, d, 'trim', short=1.6, cuff='mesh', cuff_d=0.05)


def build_postal_shirt(g):
    """A letter carrier's shirt: pale blue, navy stripe down the sleeves and trouser-style pocket flaps; generic patch."""
    d = SHIRT + 0.05
    g.shell('body', 0, 12, d, 'cloth')
    collar_flaps(g, d, 'cloth')
    g.front('body', d, -0.35, 0.6, 0.35, 12.0, 'cloth', t=0.1)
    buttons(g, 'body', d + 0.1, 0, (2.0, 4.0, 6.0, 8.0, 10.0), 'button')
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.2, s * 3.4))
        g.front('body', d, x0, 2.6, x1, 5.0, 'cloth', t=0.12)
        g.front('body', d + 0.12, x0, 2.4, x1, 3.2, 'flap', t=0.08)
    sleeves(g, d, 'cloth', short=3.2, cuff='cloth', cuff_d=0.06)
    g.both(ARMS, lambda p, s: g.side(p, d, s, -0.2, -2.0, 0.2, 3.2, 'stripe', t=0.04))
    g.both(ARMS, lambda p, s: g.side(p, d + 0.04, s, -1.0, -1.2, 1.0, 0.6, 'patch', t=0.05))  # sleeve patch
    g.front('body', d + 0.12, -3.2, 3.2, -1.6, 3.6, 'name', t=0.06)


def build_cycling_jersey(g):
    """A tight road jersey: full zip, bold side panel, three pockets low on the back."""
    d = SHIRT + 0.02
    g.shell('body', 0, 11.6, d, 'lycra')
    g.box('body', (-2.6, -0.8, -2 - d - 0.1), (2.6, 0.3, 2 + d + 0.1), 'lycra')
    g.front('body', d, -0.2, -0.8, 0.2, 11.6, 'zip', t=0.1)
    for s in (-1, 1):
        g.side('body', d, s, -2.2, 1.0, 2.2, 11.6, 'panel', t=0.04)
    g.front('body', d, -4 - d, 4.0, 4 + d, 5.2, 'panel', t=0.04)
    for x in (-3.6, -1.2, 1.2):
        g.back('body', d, x, 8.0, x + 2.4, 11.2, 'lycra', t=0.2)
    g.shell('body', 11.2, 11.8, d + 0.04, 'grip')
    sleeves(g, d, 'lycra', short=2.4, cuff='panel', cuff_d=0.04)


def build_cycling_shorts(g):
    """Padded bib shorts: straps over the shoulders, a gripper hem, the chamois showing as a seat lump."""
    d = PANTS - 0.2
    g.shell('body', 8.0, 12, d, 'lycra')
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.0, s * 2.2))
        g.front('body', SHIRT + 0.06, x0, 0.0, x1, 8.0, 'lycra', t=0.08)
        g.back('body', SHIRT + 0.06, x0, 0.0, x1, 8.0, 'lycra', t=0.08)
        g.box('body', (x0, -0.2 - SHIRT, -2 - SHIRT - 0.08), (x1, 0.0, 2 + SHIRT + 0.08), 'lycra')
    def leg(p, s):
        g.shell(p, -0.2, 5.4, d, 'lycra')
        g.shell(p, 4.8, 5.6, d + 0.06, 'grip')
        g.side(p, d, s, -1.6, 0.6, 1.6, 4.4, 'panel', t=0.04)
        g.back(p, d, -1.6, -0.2, 1.6, 1.6, 'pad', t=0.2)
    g.both(LEGS, leg)


def build_wetsuit(g):
    """A full neoprene steamer: back zip with a long pull cord, knee pads, contrast chest and shoulders."""
    d = SHIRT + 0.05
    g.shell('body', 0, 12, d, 'neo')
    g.box('body', (-2.6, -0.9, -2 - d - 0.15), (2.6, 0.2, 2 + d + 0.15), 'neo')
    g.shell('body', -0.2, 3.4, d + 0.04, 'panel')
    g.back('body', d + 0.04, -0.25, -0.6, 0.25, 9.8, 'zip', t=0.1)
    g.back('body', d + 0.14, -0.1, 9.8, 0.1, 12.0, 'cord', t=0.1)
    g.front('body', d + 0.04, 1.2, 4.2, 3.0, 5.0, 'logo', t=0.04)
    sleeves(g, d, 'neo', end=8.2, cuff='panel', cuff_d=0.04)
    def leg(p, s):
        g.shell(p, -0.2, 11.4, PANTS - 0.05, 'neo')
        g.front(p, PANTS - 0.05, -1.8, 5.0, 1.8, 7.4, 'knee', t=0.14)
        g.shell(p, 10.8, 11.4, PANTS + 0.0, 'panel')
    g.both(LEGS, leg)


def build_fishing_waders(g):
    """Chest waders: rubberised to the chest, braces with clips, a wading belt; the stockingfoot goes in your boots."""
    d = PANTS + 0.45
    g.shell('body', 2.6, 12, d, 'rubber')
    g.shell('body', 2.6, 3.2, d + 0.06, 'trim')
    g.shell('body', 9.6, 10.4, d + 0.08, 'belt')
    g.front('body', d + 0.08, -0.6, 9.4, 0.6, 10.6, 'buckle', t=0.2)
    g.front('body', d, -2.0, 4.0, 2.0, 6.6, 'rubber', t=0.2)                                     # chest pocket
    g.front('body', d + 0.2, -2.0, 4.0, 2.0, 4.5, 'trim', t=0.06)
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.4, s * 2.4))
        g.front('body', SHIRT + 0.3, x0, -0.3, x1, 2.8, 'strap', t=0.1)
        g.back('body', SHIRT + 0.3, x0, -0.3, x1, 2.8, 'strap', t=0.1)
        g.box('body', (x0, -0.3 - SHIRT - 0.3, -2 - SHIRT - 0.4), (x1, -0.3, 2 + SHIRT + 0.4), 'strap')
        g.front('body', d, x0, 2.4, x1, 3.2, 'buckle', t=0.2)
    def leg(p, s):
        g.shell(p, -0.2, 11.4, d, 'rubber')
        g.front(p, d, -1.8, 4.4, 1.8, 7.0, 'trim', t=0.12)
        g.shell(p, 10.6, 11.4, d + 0.08, 'trim')
    g.both(LEGS, leg)


# survivor-made armour: Build 42 lets you craft it from scrap; ours are original shapes off LARP and cosplay photos


def build_scrap_cuirass(g):
    """A breastplate beaten out of car panels and road signs, riveted over a leather harness, wired at the sides."""
    d = VEST_D - 0.1
    lo, hi = BOXES['body']
    g.box('body', (-4 - d, 0.4, hi[2]), (4 + d, 10.6, hi[2] + d), 'harness')
    for s in (-1, 1):
        xo = lo[0] - d if s < 0 else hi[0]
        g.box('body', (xo, 0.4, lo[2] - d), (xo + d, 10.6, hi[2]), 'harness')
        x0, x1 = sorted((s * 1.6, s * 3.6))
        g.box('body', (x0, -0.3 - d, lo[2] - d), (x1, 0.4, hi[2] + d), 'harness')
    plates = ((-4.2, 0.6, 0.2, 4.6, 'panel'), (-0.2, 0.4, 4.2, 4.2, 'sign'), (-4.0, 4.4, 4.0, 7.6, 'hood'),
              (-3.8, 7.4, 0.6, 10.4, 'panel'), (0.4, 7.6, 3.8, 10.6, 'hood'))
    for i, (x0, y0, x1, y1, m) in enumerate(plates):
        g.front('body', d + 0.02 * i, x0, y0, x1, y1, m, t=0.3)
        for (rx, ry) in ((x0 + 0.4, y0 + 0.4), (x1 - 0.7, y0 + 0.4), (x0 + 0.4, y1 - 0.7), (x1 - 0.7, y1 - 0.7)):
            g.front('body', d + 0.02 * i + 0.3, rx, ry, rx + 0.3, ry + 0.3, 'rivet', t=0.12)
    g.back('body', d, -3.6, 1.0, 3.6, 6.4, 'panel', t=0.3)                                        # back plate, a car door skin
    g.back('body', d + 0.3, -2.0, 2.0, -0.4, 2.6, 'rust', t=0.04)
    for s in (-1, 1):
        for y in (2.0, 4.2, 6.4, 8.6):
            g.side('body', d, s, -1.4, y, 1.4, y + 0.25, 'wire', t=0.12)                             # wire lacing at the sides
    g.front('body', d + 0.4, 1.0, 1.4, 3.0, 2.0, 'stripe', t=0.04)                                # what's left of the sign's stripe


def build_tyre_cuirass(g):
    """A vest of tyre tread: strips cut from a truck tyre, bolted in overlapping scales on a seatbelt-webbing harness."""
    d = VEST_D - 0.05
    lo, hi = BOXES['body']
    g.box('body', (-4 - d, 0.6, hi[2]), (4 + d, 10.4, hi[2] + d), 'webbing')
    for s in (-1, 1):
        xo = lo[0] - d if s < 0 else hi[0]
        g.box('body', (xo, 0.6, lo[2] - d), (xo + d, 10.4, hi[2]), 'webbing')
        x0, x1 = sorted((s * 1.6, s * 3.6))
        g.box('body', (x0, -0.3 - d, lo[2] - d), (x1, 0.6, hi[2] + d), 'webbing')
        g.box('body', (x0 - (0.3 if s < 0 else 0), -0.6 - d, lo[2] - d - 0.4), (x1 + (0.3 if s > 0 else 0), 0.2, hi[2] + d + 0.4), 'tread')
    for row, y in enumerate((0.8, 3.0, 5.2, 7.4)):                                                 # overlapping tread rows
        t = 0.45 + 0.1 * (row % 2)
        g.front('body', d, -4 - d, y, 4 + d, y + 2.6, 'tread', t=t)
        g.back('body', d, -4 - d, y, 4 + d, y + 2.6, 'tread', t=t)
        g.front('body', d + t, -4 - d, y + 2.2, 4 + d, y + 2.6, 'wall', t=0.06)                    # the sidewall edge of each strip
        g.back('body', d + t, -4 - d, y + 2.2, 4 + d, y + 2.6, 'wall', t=0.06)
        for x in (-3.0, 0.0, 2.6):
            g.front('body', d + t, x, y + 0.4, x + 0.4, y + 0.8, 'bolt', t=0.12)
    g.front('body', d + 0.6, -0.5, 9.6, 0.5, 10.4, 'buckle', t=0.15)


def build_scrap_arm_guards(g):
    """Forearm and upper-arm plates cut from cans and sheet, strapped on with belts."""
    def arm(p, s):
        g.shell(p, -2.0, 1.6, 0.6, 'panel')                                                     # a shoulder cap
        g.shell(p, 1.2, 1.6, 0.75, 'strap')
        g.shell(p, 3.0, 8.0, 0.85, 'can')
        g.side(p, 1.15, s, -0.4, 3.2, 0.4, 7.8, 'rust', t=0.05)
        for y in (3.6, 7.0):
            g.shell(p, y, y + 0.5, 0.95, 'strap')
        g.side(p, 0.95, s, -0.3, 0.2, 0.3, 0.8, 'rivet', t=0.12)
    g.both(ARMS, arm)


def build_scrap_leg_guards(g):
    """Shin and thigh plates: a hubcap at the knee, sheet shins, belts round the calf."""
    def leg(p, s):
        g.front(p, PANTS + 0.5, -2.4, 0.8, 2.4, 4.2, 'panel', t=0.3)
        g.front(p, PANTS + 0.8, -1.8, 4.2, 1.8, 7.0, 'hubcap', t=0.4)                                 # hubcap knee
        g.front(p, PANTS + 1.2, -0.5, 5.2, 0.5, 6.0, 'rivet', t=0.15)
        g.front(p, PANTS + 0.5, -2.3, 7.0, 2.3, 10.4, 'can', t=0.3)
        for y in (2.0, 8.0, 9.6):
            g.shell(p, y, y + 0.5, PANTS + 0.55, 'strap')
    g.both(LEGS, leg)


def build_scrap_helmet(g):
    """A cooking pot beaten into a helmet: welded rim, a bent strip nasal kept above the eyes, a strap under the chin."""
    d = 0.6
    g.box('head', (-4 - d, -8 - d - 0.6, -4 - d), (4 + d, -5.2, 4 + d), 'pot')
    g.box('head', (-4 - d - 0.3, -5.6, -4 - d - 0.3), (4 + d + 0.3, -5.0, 4 + d + 0.3), 'rim')
    g.box('head', (-0.4, -8 - d - 0.7, -4 - d - 0.15), (0.4, -5.6, -4 - d), 'rivet')               # the weld seam
    for s in (-1, 1):
        x = -4 - d if s < 0 else 4 + d - 0.6
        g.box('head', (x, -5.0, -1.0), (x + 0.6, -2.0, 1.0), 'strap')                               # ear flaps cut from a belt
    g.box('head', (-4.4, -2.0, -0.6), (4.4, -1.6, 0.6), 'strap')
    g.box('head', (-2.4, -8 - d - 0.62, -1.4), (-0.6, -8 - d - 0.5, 0.6), 'rust')
    g.box('head', (1.8, -7.0, -4 - d - 0.08), (3.0, -6.0, -4 - d), 'dent')


def build_hunting_cap(g):
    """A blaze orange hunter's cap with fold-down ear flaps tied up over the crown."""
    d = 0.5
    g.box('head', (-4 - d, -8 - d, -4 - d), (4 + d, -5.0, 4 + d), 'cloth')
    g.box('head', (-3.6, -5.4, -7.8), (3.6, -4.95, -4 - d + 0.05), 'brim')
    for s in (-1, 1):
        x = -4 - d - 0.2 if s < 0 else 4 + d
        g.box('head', (x, -7.0, -2.2), (x + 0.2, -5.0, 2.2), 'flap')                                 # flaps folded up
    g.box('head', (-0.4, -8 - d - 0.25, -1.0), (0.4, -8 - d, 1.0), 'tie')
    g.box('head', (-4 - d - 0.05, -6.0, -4 - d - 0.05), (4 + d + 0.05, -5.4, 4 + d + 0.05), 'band')


CIVILIAN_3 = {
    'hunting_vest': (build_hunting_vest, dict(mesh=mesh('#e8641a'), solid=cloth('#e0601a', weave=0.04), zip=flat('#1e1e1e'),
                                              elastic=cloth('#2a2a2a', stitch=None), shell=plastic('#a82a24'), brass=metal('brass'),
                                              patch=plastic('#d8d4c8', gloss=0.4)), 0.4),
    'foreman_vest': (build_foreman_vest, dict(mesh=mesh('#c8e02a'), solid=cloth('#b8d024', weave=0.03), zip=flat('#1e1e1e'),
                                              tape=glow_tape(), flap=cloth('#a8c020'), pen_a=plastic('#1e3a7a'),
                                              pen_b=plastic('#a82a24'), id=plastic('#c8d8d8', gloss=0.6)), 0.45),
    'waistcoat': (build_waistcoat, dict(wool=cloth('#3a3a42', weave=0.03, pattern=plaid('#3a3a42', '#34343c', 0.9)),
                                        back=cloth('#5a2a2a', weave=0.01, stitch=None), welt=flat('#2a2a30'), button=plastic('#1a1a1a'),
                                        cinch=cloth('#4a2424'), buckle=metal('steel'), chain=metal('brass')), 0.3),
    'leather_biker_vest': (build_leather_biker_vest, dict(leather=leather('#1e1c1c', gloss=0.18), snap=metal('steel'),
                                                          stitch=flat('#3a3634'), pin=metal('steel'), rocker=leather('#a8885a'),
                                                          emblem=leather('#5a4a3a'), chain=metal('steel'), lace=leather('#2a2622')), 0.45),
    'sheepskin_vest': (build_sheepskin_vest, dict(suede=leather('#8a6440', gloss=0.03), wool=fur('#e0d4bc'), stitch=flat('#5a4028'),
                                                  toggle=leather('#3a2a1a')), 0.35),
    'sheepskin_jacket': (build_sheepskin_jacket, dict(suede=leather('#7a5636', gloss=0.03), wool=fur('#e0d4bc'),
                                                      toggle=leather('#2e2014'), seam=flat('#5a3e24', ink=False)), 0.35),
    'padded_jacket': (build_padded_jacket, dict(quilt=checker('#7a5a2e', '#6e5028'),
                                                rib=rib('#4a3a20'), cord=cloth('#4a3420', weave=0.2), zip=metal('brass'),
                                                canvas=cloth('#6e5028', weave=0.14), snap=metal('brass')), 0.5),
    'hunting_jacket': (build_hunting_jacket, dict(shell=Camo(weave=0.08), blaze=cloth('#e0601a', weave=0.03), zip=flat('#1e1e1e'),
                                                  pocket=Camo(0.88), flap=Camo(0.8), elastic=cloth('#2a2a2a', stitch=None),
                                                  cartridge=plastic('#a82a24'), knit=rib('#2a2a22')), 0.45),
    'paramedic_jacket': (build_paramedic_jacket, dict(shell=cloth('#1e2a44', weave=0.03), yoke=cloth('#c8d82a', weave=0.02),
                                                      zip=flat('#111111'), reflect=glow_tape(), star=flat('#2a5ab8'),
                                                      stencil=stencil('#1e2a44', '#c8c8c0'), loop=cloth('#16203a'),
                                                      radio=plastic('#151515')), 0.45),
    'apron': (build_apron, dict(cloth=cloth('#d8d4c8', weave=0.06), pocket=cloth('#ccc8bc', weave=0.06), stitch=flat('#a8a498'),
                                tie=cloth('#ccc8bc', stitch=None), stain=flat('#6a4a3a', ink=False)), 0.45),
    'leather_apron': (build_leather_apron, dict(leather=leather('#5a3a24', gloss=0.06), strap=leather('#3a2616'),
                                                ring=metal('brass'), scorch=flat('#1e140c', ink=False)), 0.5),
    'long_johns': (build_long_johns, dict(waffle=checker('#d8ccb4', '#ccc0a8'), rib=rib('#ccc0a8'), button=plastic('#e8e4da')), 0.4),
    'socks': (build_socks, dict(knit=knit('#8a8478'), rib=rib('#7e7870'), stripe=rib('#a82a24'), heel=knit('#5a564e')), 0.55),
    'legwarmers': (build_legwarmers, dict(knit=knit('#b04a7a'), rib=rib('#9a3e6a')), 0.4),
    'riding_boots': (build_riding_boots, dict(upper=leather('#1a1614', gloss=0.3), sole=rubber('#121010'), heel=rubber('#121010'),
                                              zip=metal('steel'), rest=leather('#1a1614')), 0.35),
    'bowling_shoes': (build_bowling_shoes, dict(cream=leather('#e0d8c4', gloss=0.1), red=leather('#a82a24'), navy=leather('#1e2a4a'),
                                                sole=rubber('#c8c0b0'), number=flat('#e0d8c4'), tongue=leather('#e0d8c4'),
                                                lace=flat('#e8e4da')), 0.4),
    'flip_flops': (build_flip_flops, dict(sole=rubber('#2a6a8a'), foot=rubber('#e0d8c4'), strap=rubber('#1e1e1e')), 0.5),
    'boxing_gloves': (build_boxing_gloves, dict(leather=leather('#a01e1e', gloss=0.35), cuff=leather('#e8e4da', gloss=0.2),
                                                lace=flat('#e8e4da'), logo=flat('#e8e4da')), 0.4),
    'pyjama_top': (build_pyjama_top, dict(stripe=vstripes('#c8d0dc', '#3a4a6a'), piping=cloth('#2a3a5a', stitch=None),
                                          button=plastic('#e8e4da')), 0.35),
    'pyjama_bottoms': (build_pyjama_bottoms, dict(stripe=vstripes('#c8d0dc', '#3a4a6a'), piping=cloth('#2a3a5a', stitch=None),
                                                  tie=cloth('#2a3a5a', stitch=None)), 0.35),
    'sports_jersey': (build_sports_jersey, dict(mesh=mesh('#1e3a7a'), trim=cloth('#c8a82a', weave=0.02, stitch=None),
                                                number=stencil('#1e3a7a', '#e8e4da'), name=stencil('#1e3a7a', '#e8e4da')), 0.35),
    'postal_shirt': (build_postal_shirt, dict(cloth=cloth('#9ab4cc', weave=0.04), flap=cloth('#8ea8c0'), button=plastic('#e8e4da'),
                                              stripe=flat('#1e2a4a'), patch=flat('#1e2a4a'), name=plastic('#1e2a4a')), 0.4),
    'cycling_jersey': (build_cycling_jersey, dict(lycra=cloth('#e0e0dc', weave=0.01, stitch=None),
                                                  panel=cloth('#c82a2a', weave=0.01, stitch=None), zip=flat('#1e1e1e'),
                                                  grip=rubber('#1e1e1e')), 0.35),
    'cycling_shorts': (build_cycling_shorts, dict(lycra=cloth('#1a1a1c', weave=0.01, stitch=None),
                                                  panel=cloth('#c82a2a', weave=0.01, stitch=None), grip=rubber('#2a2a2a'),
                                                  pad=cloth('#222224', stitch=None)), 0.4),
    'wetsuit': (build_wetsuit, dict(neo=rubber('#141416'), panel=rubber('#1e4a6a'), zip=metal('steel'), cord=cloth('#1e4a6a', stitch=None),
                                    logo=flat('#c8c8c0'), knee=rubber('#2a2a2c', stud=True)), 0.45),
    'fishing_waders': (build_fishing_waders, dict(rubber=rubber('#3a4230'), trim=rubber('#2a2e24'), belt=cloth('#2a2a26', stitch=None),
                                                  buckle=plastic('#1a1a1a'), strap=cloth('#3a3a30', stitch=None)), 0.5),
    'scrap_cuirass': (build_scrap_cuirass, dict(harness=leather('#3a2a1c'), panel=metal('steel'), sign=metal('steel'),
                                                hood=flat('#6a2a24'), rivet=metal('brass'), rust=flat('#7a4422', ink=False),
                                                wire=metal('steel'), stripe=flat('#c8a82a')), 0.7),
    'tyre_cuirass': (build_tyre_cuirass, dict(webbing=cloth('#2a2a2c', stitch=None), tread=rubber('#1a1a1a', stud=True), wall=rubber('#3a3a3a'),
                                              bolt=metal('steel'), buckle=metal('steel')), 0.65),
    'scrap_arm_guards': (build_scrap_arm_guards, dict(panel=metal('steel'), can=flat('#7a7a6a'), rust=flat('#7a4422', ink=False),
                                                      strap=leather('#3a2a1c'), rivet=metal('brass')), 0.7),
    'scrap_leg_guards': (build_scrap_leg_guards, dict(panel=metal('steel'), hubcap=metal('steel'), can=flat('#6a6a5a'),
                                                      rivet=metal('brass'), strap=leather('#3a2a1c')), 0.7),
    'scrap_helmet': (build_scrap_helmet, dict(pot=metal('steel'), rim=metal('steel'), rivet=flat('#3a3a38'), strap=leather('#3a2a1c'),
                                              rust=flat('#7a4422', ink=False), dent=flat('#4a4a48', ink=False)), 0.7),
    'hunting_cap': (build_hunting_cap, dict(cloth=cloth('#e0601a', weave=0.04), brim=cloth('#d0581a'), flap=cloth('#d0581a'),
                                            tie=cloth('#2a2a2a', stitch=None), band=cloth('#2a2a2a', stitch=None)), 0.4),
}


# ------------------------------------------------------------------ wave 2, batch 7: prized and grail pieces
# Refs: refs/items/clothing (bomb-disposal-suit, shark-mesh-dive-suit, plate-armour-full-gothic, black-plate-armour-replica,
# crusader-knight-surcoat-great-helm, motorcycle-racing-leathers, scba-firefighter, armoured-suit-bodyguard).
# The black crusader kit is our own design off real 13th-14th century great helms, surcoats and later plate; no
# franchise markings. Every helmet keeps the eyes clear: a slit or port is cut where the skin's eyes are.


def blackened(base='#1c1c22'):
    """Blackened (fire-blued) plate: near black with a cold steel sheen along the light."""
    def fn(f):
        c = P.steel(f, 0.5) * 0.32 + P.col(base) * 0.55
        return toon(f, grade(c, 0.2), ink=False)
    return fn


def plate_arm(g, p, s, mat, edge, d=0.7, fan=True):
    """An arm harness: rerebrace, couter with a fan on the outside of the elbow, vambrace."""
    g.shell(p, 0.4, 3.6, d, mat)
    g.shell(p, 3.4, 3.7, d + 0.12, edge)
    g.shell(p, 3.8, 5.2, d + 0.25, mat)                                                      # couter
    if fan:
        g.side(p, d + 0.25, s, -1.8, 3.4, 1.4, 5.8, mat, t=0.3)                              # the fan
        g.side(p, d + 0.55, s, -0.3, 4.2, 0.3, 4.8, edge, t=0.12)
    g.shell(p, 5.4, 8.2, d, mat)
    g.shell(p, 7.9, 8.2, d + 0.1, edge)


def plate_leg(g, p, s, mat, edge, d=PANTS + 0.5, fan=True):
    """A leg harness: cuisse, poleyn with its side wing, greave down to the ankle."""
    g.shell(p, 0.4, 4.4, d, mat)
    g.shell(p, 4.1, 4.4, d + 0.12, edge)
    g.front(p, d, -2.2, 4.4, 2.2, 6.6, mat, t=0.45)                                          # poleyn
    if fan:
        g.side(p, d, s, -2.4, 4.2, 0.4, 6.8, mat, t=0.35)
    g.front(p, d + 0.45, -0.3, 5.0, 0.3, 6.0, edge, t=0.12)
    g.shell(p, 6.6, 10.6, d - 0.1, mat)
    g.front(p, d - 0.1, -0.2, 6.8, 0.2, 10.4, edge, t=0.14)                                  # the greave's ridge


def gauntlet(g, p, s, mat, edge):
    """An hourglass gauntlet: flared cuff, plated back of the hand, lames over the fingers."""
    lo, hi = BOXES[p]
    g.shell(p, 6.8, 8.0, 0.95, mat)
    g.shell(p, 6.8, 7.1, 1.05, edge)
    g.shell(p, 8.0, 8.6, 0.6, mat)
    g.shell(p, 8.6, 9.8, 0.55, mat)
    for y in (9.8, 10.15):
        g.shell(p, y, y + 0.35, 0.5 + (0.05 if y > 10 else 0), mat)
    g.box(p, (lo[0] - 0.5, 10.5, lo[2] - 0.5), (hi[0] + 0.5, 10.75, hi[2] + 0.5), mat)
    g.side(p, 0.55, s, -1.6, 9.2, 1.6, 9.6, edge, t=0.14)                                    # knuckle ridge


def sabaton(g, p, s, mat, edge, toe=3.2, point=0.0):
    """Plate shoes: laminated lames over the foot, a sole of leather, the toe as long as the fashion."""
    g.box(p, (-2.3, 11.4, -2 - toe - point), (2.3, 12.2, 2.4), 'sole')
    g.shell(p, 9.4, 11.4, 0.55, mat)
    for i in range(4):
        z = -2.6 - i * 0.7
        g.box(p, (-2.2, 10.0 + i * 0.25, z - 0.7), (2.2, 11.4, z), mat)
        g.box(p, (-2.2, 10.0 + i * 0.25, z - 0.12), (2.2, 10.2 + i * 0.25, z), edge)
    if point:
        g.box(p, (-0.6, 11.0, -2 - toe - point), (0.6, 11.4, -2 - toe), mat)


# ---- the black crusader kit (grail)


def build_crusader_great_helm(g):
    """A flat-topped great helm in blackened plate: a single eye slit, a brass cross over the face, breaths below,
    brass-edged, with a white and black torse round the crown."""
    d = 1.0
    top, bot = -9.6, 0.9
    py0, py1 = EYES_HIGH - 0.35, EYES_LOW + 0.35
    g.box('head', (-4 - d, top, -3.4), (4 + d, bot, 4 + d), 'plate')                          # sides and back
    eye_open_front(g, -4 - d, -3.4, top, bot, 4 + d, 'plate', port=(3.7, py0, py1))
    g.box('head', (-0.4, py0, -4 - d), (0.4, py1, -3.4), 'plate')                              # bar splitting the slit
    g.box('head', (-4 - d + 0.2, top - 0.4, -4 - d + 0.2), (4 + d - 0.2, top, 4 + d - 0.2), 'plate')   # the flat top
    # the brass cross: brow band and the upright below the slit
    g.box('head', (-4 - d - 0.05, py0 - 0.7, -4 - d - 0.2), (4 + d + 0.05, py0, -4 - d), 'brass')
    g.box('head', (-0.55, py1, -4 - d - 0.2), (0.55, bot - 0.4, -4 - d), 'brass')
    g.box('head', (-0.4, top + 0.6, -4 - d - 0.2), (0.4, py0 - 0.7, -4 - d), 'brass')
    g.box('head', (-4 - d - 0.1, bot - 0.4, -4 - d - 0.1), (4 + d + 0.1, bot, 4 + d + 0.1), 'brass')   # the hem
    g.box('head', (-4 - d - 0.1, top, -4 - d - 0.1), (4 + d + 0.1, top + 0.35, 4 + d + 0.1), 'brass')
    for row, y in enumerate((-2.2, -1.2)):                                                       # breaths, right side only
        for i in range(4):
            x = 1.4 + i * 0.75 + (0.35 if row else 0)
            g.box('head', (x, y, -4 - d - 0.06), (x + 0.3, y + 0.35, -4 - d), 'dark')
    for i in range(6):                                                                           # rivets round the brow
        x = -4.2 + i * 1.7
        g.box('head', (x, py0 - 1.3, -4 - d - 0.12), (x + 0.3, py0 - 1.0, -4 - d), 'brass')
    g.box('head', (-4 - d - 0.35, top - 0.7, -4 - d - 0.35), (4 + d + 0.35, top + 0.1, 4 + d + 0.35), 'torse')   # torse
    for i in range(5):
        x = -4.6 + i * 2.0
        g.box('head', (x, top - 0.75, -4 - d - 0.4), (x + 1.0, top + 0.15, -4 - d - 0.3), 'torse_b')
    g.box('head', (-0.35, top - 2.6, -0.35), (0.35, top - 0.7, 0.35), 'brass')                 # a small cross crest
    g.box('head', (-1.0, top - 2.1, -0.35), (1.0, top - 1.6, 0.35), 'brass')


def build_crusader_plate(g):
    """The harness under the surcoat: blackened breastplate with a medial ridge, faulds, arms in plate."""
    d = JACKET + 0.15
    g.shell('body', 0, 7.8, d, 'plate')
    g.front('body', d, -0.2, 0.6, 0.2, 7.6, 'ridge', t=0.25)
    for i, y in enumerate((7.8, 8.9, 10.0)):                                                     # faulds over the hips
        g.shell('body', y, y + 1.2, d + 0.1 + 0.08 * i, 'plate')
        g.shell('body', y + 1.0, y + 1.2, d + 0.2 + 0.08 * i, 'edge')
    g.box('body', (-3.2, -1.2, -2 - d - 0.2), (3.2, 0.3, 2 + d + 0.2), 'plate')                # gorget
    g.box('body', (-3.3, 0.1, -2 - d - 0.25), (3.3, 0.3, 2 + d + 0.25), 'edge')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 1.4, d, 'mail'))                                 # mail skirt peeks below
    g.both(ARMS, lambda p, s: plate_arm(g, p, s, 'plate', 'edge', d=0.7))


def build_crusader_tabard(g):
    """A long white surcoat over the plate, a black cross front and back, split for riding and belted."""
    d = VEST_D + 0.1
    g.shell('body', -0.2, 12, d, 'linen')
    for side in ('front', 'back'):
        fn = g.front if side == 'front' else g.back
        fn('body', d, -0.75, 1.0, 0.75, 10.0, 'cross', t=0.04)
        fn('body', d, -3.4, 3.6, 3.4, 5.0, 'cross', t=0.04)
    for s in (-1, 1):                                                                            # no sleeves, wide shoulders
        x0, x1 = sorted((s * 1.4, s * (4 + d)))
        g.box('body', (x0, -0.3 - d, -2 - d), (x1, 0.0, 2 + d), 'linen')
    g.shell('body', 8.6, 9.2, d + 0.1, 'belt')
    g.front('body', d + 0.1, -0.6, 8.4, 0.6, 9.4, 'buckle', t=0.2)
    g.front('body', d + 0.3, -0.25, 9.4, 0.25, 11.4, 'belt', t=0.1)                              # the belt's tail
    def leg(p, s):                                                                               # skirts to the knee
        g.front(p, PANTS + 0.9, -2.3, -0.2, 2.3, 6.4, 'linen', t=0.1)
        g.back(p, PANTS + 0.9, -2.3, -0.2, 2.3, 6.4, 'linen', t=0.1)
        g.side(p, PANTS + 0.9, s, -2.3, -0.2, 2.3, 6.0, 'linen', t=0.1)
        g.front(p, PANTS + 1.0, -2.3, 6.0, 2.3, 6.4, 'hem', t=0.04)
        g.back(p, PANTS + 1.0, -2.3, 6.0, 2.3, 6.4, 'hem', t=0.04)
        g.front(p, PANTS + 1.0, -2.3, 2.0, 2.3, 2.6, 'grime', t=0.02)
    g.both(LEGS, leg)


def build_crusader_pauldrons(g):
    """Huge layered spaulders in blackened plate, brass-edged, each with a white cross on a raised boss."""
    def arm(p, s):
        for i, (y0, y1, d) in enumerate(((-2.6, 0.6, 1.8), (0.2, 1.8, 1.6), (1.4, 3.0, 1.4), (2.6, 4.0, 1.2))):
            g.shell(p, y0, y1, d, 'plate')
            g.shell(p, y1 - 0.25, y1, d + 0.06, 'brass')
        lo, hi = BOXES[p]
        x = lo[0] - 1.8 if s < 0 else hi[0] + 1.8
        g.box(p, (min(x, x + s * 0.5), -2.2, -1.8), (max(x, x + s * 0.5), 0.6, 1.8), 'plate')   # the raised boss
        xb = x + s * 0.5
        g.box(p, (min(xb, xb + s * 0.1), -1.9, -0.25), (max(xb, xb + s * 0.1), 0.3, 0.25), 'cross')
        g.box(p, (min(xb, xb + s * 0.1), -1.1, -1.2), (max(xb, xb + s * 0.1), -0.5, 1.2), 'cross')
        g.box(p, (lo[0] - 2.0, -3.4, lo[2] - 2.0), (hi[0] + 2.0, -2.6, hi[2] + 2.0), 'plate')    # the top shelf
        g.box(p, (lo[0] - 2.1, -3.5, lo[2] - 2.1), (hi[0] + 2.1, -3.3, hi[2] + 2.1), 'brass')
    g.both(ARMS, arm)


def build_crusader_cloak(g):
    """A heavy black wool cloak from the shoulders to the calf, held by a brass cross clasp, lined in white."""
    lo, hi = BOXES['body']
    d = VEST_D + 0.5
    g.box('body', (-4 - d - 0.6, -0.6 - d, -2 - d), (4 + d + 0.6, 0.2, 2 + d + 0.3), 'wool')     # over the shoulders
    g.box('body', (-4 - d - 0.6, 0.2, hi[2] + d), (4 + d + 0.6, 12.2, hi[2] + d + 0.35), 'wool')
    for s in (-1, 1):
        x0 = -4 - d - 0.6 if s < 0 else 4 + d + 0.25
        g.box('body', (x0, 0.2, -1.0), (x0 + 0.35, 9.0, hi[2] + d), 'wool')                       # falling round the sides
        g.box('body', (x0 + (0.35 if s < 0 else -0.1), 0.2, -1.0), (x0 + (0.45 if s < 0 else 0.0), 9.0, -0.6), 'lining')
        g.front('body', d, s * 2.2 - 0.6, -0.2, s * 2.2 + 0.6, 0.8, 'brass', t=0.25)              # clasp plates
    g.front('body', d + 0.25, -2.0, 0.2, 2.0, 0.4, 'chain', t=0.1)
    def leg(p, s):
        g.back(p, d - 0.3, -2.6, -0.2, 2.6, 9.0, 'wool', t=0.35)
        g.back(p, d + 0.05, -2.6, 8.6, 2.6, 9.0, 'hem', t=0.04)
    g.both(LEGS, leg)
    for x in (-3.0, -0.8, 1.6):                                                                    # heavy folds
        g.back('body', d + 0.35, x, 1.0, x + 0.5, 12.0, 'fold', t=0.12)


def build_crusader_greaves(g):
    g.both(LEGS, lambda p, s: plate_leg(g, p, s, 'plate', 'brass'))


def build_crusader_gauntlets(g):
    g.both(ARMS, lambda p, s: gauntlet(g, p, s, 'plate', 'brass'))


def build_crusader_sabatons(g):
    g.both(LEGS, lambda p, s: sabaton(g, p, s, 'plate', 'brass'))


# ---- gothic plate (prized): bright, fluted, after the late 15th century German harnesses


def build_gothic_sallet(g):
    """A sallet: rounded skull swept into a long tail, the visor slit sitting just above the eyes, open below."""
    d = 0.9
    g.box('head', (-4 - d, -9.0, -4 - d), (4 + d, EYES_HIGH - 0.5, 4 + d), 'steel')
    g.box('head', (-3.6, -9.6, -3.6), (3.6, -9.0, 3.6), 'steel')
    g.box('head', (-0.25, -9.9, -3.8), (0.25, -9.0, 4.8), 'steel')                             # comb
    g.box('head', (-4 - d, EYES_HIGH - 0.5, 0.0), (4 + d, -1.8, 4 + d), 'steel')                # cheeks and back
    g.box('head', (-4.6, -2.4, 4 + d), (4.6, -1.2, 4 + d + 3.0), 'steel')                       # the tail
    g.box('head', (-4.2, -1.2, 4 + d + 1.0), (4.2, -0.8, 4 + d + 3.0), 'steel')
    g.box('head', (-4 - d - 0.1, EYES_HIGH - 0.75, -4 - d - 0.1), (4 + d + 0.1, EYES_HIGH - 0.5, 0.0), 'brass')
    g.box('head', (-3.6, EYES_HIGH - 1.4, -4 - d - 0.08), (3.6, EYES_HIGH - 1.1, -4 - d), 'dark')   # sight slit
    for s in (-1, 1):
        for y in (-8.2, -7.2, -6.2):                                                              # fluting
            g.box('head', (s * 2.0 - 0.15, y, -4 - d - 0.12), (s * 2.0 + 0.15, y + 0.8, -4 - d), 'flute')
        x = s * (4 + d) - (0.25 if s > 0 else 0)
        g.box('head', (x, -5.0, -1.0), (x + 0.25, -4.4, -0.4), 'brass')


def build_gothic_cuirass(g):
    """A fluted breastplate with a V plackart, brass-edged, faulds, a mail skirt below."""
    d = VEST_D - 0.2
    g.shell('body', 0.2, 7.6, d, 'steel')
    for i in range(5):                                                                           # radiating flutes
        x = -2.4 + i * 1.2
        g.front('body', d, x - 0.15, 2.4 + abs(i - 2) * 0.6, x + 0.15, 7.2, 'flute', t=0.18)
    for k in range(5):                                                                           # the V of the plackart
        y = 4.2 + k * 0.6
        w = 3.6 - k * 0.7
        g.front('body', d + 0.18, -w, y, w, y + 0.6, 'steel', t=0.12)
    g.front('body', d + 0.3, -3.8, 4.0, 3.8, 4.25, 'brass', t=0.06)
    for i, y in enumerate((7.6, 8.6, 9.6)):
        g.shell('body', y, y + 1.1, d + 0.08 * i, 'steel')
        g.shell('body', y + 0.9, y + 1.1, d + 0.1 + 0.08 * i, 'brass')
    g.box('body', (-3.2, -1.1, -2 - d - 0.2), (3.2, 0.3, 2 + d + 0.2), 'steel')
    g.box('body', (-3.3, 0.1, -2 - d - 0.25), (3.3, 0.3, 2 + d + 0.25), 'brass')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 2.4, PANTS + 0.45, 'mail'))
    g.front('body', d + 0.18, -0.4, 1.0, 0.4, 2.2, 'brass', t=0.12)                              # lance rest stub
    g.side('body', d, -1, -1.0, 2.0, 1.0, 3.0, 'leather', t=0.12)


def build_gothic_arm_harness(g):
    """Pauldrons with round besagews, fluted couters and vambraces, the arming points tied off in red."""
    def arm(p, s):
        for y0, y1, d in ((-2.4, 0.4, 1.3), (0.2, 1.6, 1.15)):
            g.shell(p, y0, y1, d, 'steel')
            g.shell(p, y1 - 0.2, y1, d + 0.05, 'brass')
        g.front(p, 1.35, -1.4, -1.6, 1.4, 1.0, 'besagew', t=0.3)                                 # the round besagew
        g.front(p, 1.65, -0.3, -0.6, 0.3, 0.0, 'brass', t=0.12)
        plate_arm(g, p, s, 'steel', 'brass', d=0.7)
        g.side(p, 0.95, -s, -0.5, 3.6, 0.5, 4.2, 'points', t=0.15)
    g.both(ARMS, arm)


def build_gothic_leg_harness(g):
    def leg(p, s):
        plate_leg(g, p, s, 'steel', 'brass')
        for z in (-1.0, 0.0, 1.0):
            g.front(p, PANTS + 0.5, z - 0.1, 0.8, z + 0.1, 3.8, 'flute', t=0.14)
    g.both(LEGS, leg)


def build_gothic_gauntlets(g):
    def hand(p, s):
        gauntlet(g, p, s, 'steel', 'brass')
        g.side(p, 1.05, s, -0.8, 6.9, 0.8, 7.9, 'flute', t=0.12)
    g.both(ARMS, hand)


def build_gothic_sabatons(g):
    g.both(LEGS, lambda p, s: sabaton(g, p, s, 'steel', 'brass', toe=3.2, point=1.4))


# ---- modern prized protection


def build_bomb_disposal_suit(g):
    """An EOD suit: bulky padded olive cloth, a black blast plate down the chest and groin, a high collar."""
    d = JACKET + 0.45
    g.shell('body', -0.2, 12, d, 'cloth')
    g.box('body', (-3.8, -2.2, -2 - d - 0.6), (3.8, 0.4, 2 + d + 0.6), 'collar')               # tall blast collar
    g.front('body', d, -3.4, 0.4, 3.4, 12.0, 'plate', t=0.8)
    g.front('body', d + 0.8, -3.0, 2.4, 3.0, 2.8, 'strap', t=0.08)
    g.front('body', d + 0.8, -3.0, 7.0, 3.0, 7.4, 'strap', t=0.08)
    for s in (-1, 1):
        g.side('body', d, s, -1.6, 4.0, 1.6, 5.0, 'strap', t=0.14)
    sleeves(g, d, 'cloth', end=7.8, cuff='strap', cuff_d=0.12)
    g.both(ARMS, lambda p, s: g.side(p, d, s, -2.4, -2.0, 2.4, 2.6, 'cloth', t=0.5))             # shoulder padding
    def leg(p, s):
        g.shell(p, -0.2, 11.4, PANTS + 0.55, 'cloth')
        g.front(p, PANTS + 0.55, -2.4, -0.2, 2.4, 10.4, 'plate', t=0.6)                          # leg blast plates
        g.front(p, PANTS + 1.15, -2.0, 4.6, 2.0, 5.0, 'strap', t=0.08)
        g.side(p, PANTS + 0.55, s, -1.6, 2.0, 1.6, 2.6, 'strap', t=0.12)
    g.both(LEGS, leg)
    g.back('body', d, -2.4, 1.0, 2.4, 6.0, 'cloth', t=0.8)                                         # the fan pack on the back
    g.back('body', d + 0.8, -1.6, 2.0, 1.6, 3.2, 'grille', t=0.1)


def build_bomb_disposal_helmet(g):
    """The EOD helmet: a big olive shell, the visor frame round an open port, a fan hose over the top."""
    d = 1.3
    g.box('head', (-4 - d, -9.6, -3.2), (4 + d, 1.2, 4 + d), 'shell')
    eye_open_front(g, -4 - d - 0.4, -3.2, -9.6, 1.2, 4 + d, 'frame', port=(3.6, -6.4, -1.0))      # big visor frame
    g.box('head', (-4 - d + 0.2, -10.2, -3.0), (4 + d - 0.2, -9.6, 4 + d - 0.2), 'shell')
    g.box('head', (-0.5, -11.0, -1.0), (0.5, -10.2, 6.2), 'hose')                                   # fan hose over the top
    g.box('head', (-0.5, -10.2, 5.4), (0.5, -2.0, 6.2), 'hose')
    for s in (-1, 1):
        x = s * (4 + d) - (0.3 if s > 0 else 0)
        g.box('head', (x, -5.6, -2.0), (x + 0.3, -3.6, 0.0), 'frame')                              # the speaker pods
    g.box('head', (-4 - d - 0.45, -6.6, -4 - d - 0.5), (4 + d + 0.45, -6.4, -4 - d - 0.3), 'frame')


def build_shark_mesh_suit(g):
    """A diver's chainmail shark suit: stainless mail over everything, neoprene cuffs and seams, straps at the joints."""
    d = SHIRT + 0.2
    g.shell('body', -0.2, 12, d, 'mail')
    g.box('body', (-2.8, -0.9, -2 - d - 0.12), (2.8, 0.2, 2 + d + 0.12), 'neo')
    g.front('body', d, -0.2, 0.2, 0.2, 9.8, 'neo', t=0.08)
    sleeves(g, d, 'mail', end=8.2, cuff='neo', cuff_d=0.06)
    g.both(ARMS, lambda p, s: g.shell(p, 4.0, 4.4, d + 0.06, 'neo'))
    def leg(p, s):
        g.shell(p, -0.2, 11.2, PANTS + 0.1, 'mail')
        g.shell(p, 5.0, 5.4, PANTS + 0.16, 'neo')
        g.shell(p, 10.6, 11.2, PANTS + 0.16, 'neo')
    g.both(LEGS, leg)


def build_motorcycle_leathers(g):
    """A one-piece racing suit: panelled leather, a speed hump, armour at shoulders, elbows and knees, knee sliders."""
    d = JACKET - 0.05
    g.shell('body', -0.2, 12, d, 'black')
    g.box('body', (-2.6, -0.9, -2 - d - 0.15), (2.6, 0.2, 2 + d + 0.15), 'black')
    g.front('body', d, -0.2, -0.8, 0.2, 11.0, 'zip', t=0.1)
    for s in (-1, 1):
        g.front('body', d, s * 0.6 if s > 0 else -3.8, 2.0, 3.8 if s > 0 else -0.6, 5.0, 'colour', t=0.06)
        g.side('body', d, s, -2.2, 3.0, 2.2, 12.0, 'stretch', t=0.04)
    g.back('body', d, -2.0, -1.4, 2.0, 5.0, 'black', t=1.0)                                         # the speed hump
    g.back('body', d + 1.0, -1.2, -1.0, 1.2, 4.4, 'colour', t=0.05)
    sleeves(g, d, 'black', end=8.0, cuff='black', cuff_d=0.08)
    def arm(p, s):
        g.side(p, d, s, -2.2, -1.8, 2.2, 1.6, 'colour', t=0.3)
        g.side(p, d, s, -1.4, 3.6, 1.4, 5.2, 'puck', t=0.3)
    g.both(ARMS, arm)
    def leg(p, s):
        g.shell(p, -0.2, 11.4, PANTS + 0.05, 'black')
        g.front(p, PANTS + 0.05, -2.0, 4.2, 2.0, 7.0, 'colour', t=0.25)
        g.side(p, PANTS + 0.05, s, -1.2, 4.6, 1.2, 6.6, 'puck', t=0.45)                             # knee slider
        g.front(p, PANTS + 0.05, -1.6, 0.6, 1.6, 3.4, 'stretch', t=0.06)
    g.both(LEGS, leg)


def build_scba_set(g):
    """Firefighter breathing apparatus: a backplate harness and a cylinder on the back, gauge hose over the shoulder."""
    d = VEST_D
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.4, s * 3.0))
        g.front('body', d, x0, 0.0, x1, 9.4, 'strap', t=0.14)
        g.box('body', (x0, -0.3 - d, -2 - d), (x1, 0.0, 2 + d), 'strap')
        g.front('body', d + 0.14, x0, 4.0, x1, 4.6, 'buckle', t=0.12)
    g.shell('body', 9.2, 10.2, d, 'strap')
    g.front('body', d, -0.8, 9.0, 0.8, 10.4, 'buckle', t=0.2)
    g.back('body', d, -3.4, 0.4, 3.4, 10.4, 'plate', t=0.3)                                         # backplate
    g.back('body', d + 0.3, -1.8, 0.2, 1.8, 9.0, 'cylinder', t=3.4)                                  # the bottle
    g.back('body', d + 0.3, -1.6, -0.5, 1.6, 0.2, 'cylinder', t=3.2)
    g.back('body', d + 1.4, -0.5, -1.4, 0.5, -0.5, 'valve', t=1.0)
    g.back('body', d + 3.7, -1.8, 3.0, 1.8, 3.4, 'band', t=0.06)
    g.back('body', d + 3.7, -1.8, 6.6, 1.8, 7.0, 'band', t=0.06)
    g.front('body', d + 0.14, -2.6, 3.0, -2.0, 7.6, 'hose', t=0.3)                                   # hose and gauge
    g.front('body', d + 0.44, -3.0, 7.4, -1.6, 8.8, 'gauge', t=0.4)
    g.front('body', d + 0.14, 1.9, 1.0, 2.6, 5.4, 'hose', t=0.3)


def build_scba_mask(g):
    """A full-face SCBA mask: rubber seal, a big lens frame left open over the eyes, the regulator out front."""
    z0, z1 = -5.0, -4.3
    eye_open_front(g, z0, z1, -7.0, 0.6, 4.1, 'rubber', port=(3.7, EYES_HIGH - 1.0, EYES_LOW + 1.0))
    g.box('head', (-3.9, EYES_HIGH - 1.2, z0 - 0.3), (3.9, EYES_HIGH - 0.9, z0), 'frame')
    g.box('head', (-3.9, EYES_LOW + 0.9, z0 - 0.3), (3.9, EYES_LOW + 1.2, z0), 'frame')
    g.box('head', (-1.4, -2.0, z0 - 1.2), (1.4, 0.4, z0), 'regulator')
    g.box('head', (-0.6, -1.4, z0 - 1.5), (0.6, -0.4, z0 - 1.2), 'frame')
    for s in (-1, 1):
        x = -4.3 if s < 0 else 4.18
        for y0, y1 in ((-6.6, -6.1), (-2.2, -1.7)):
            g.box('head', (x, y0, z1), (x + 0.12, y1, 2.6), 'strap')
    g.box('head', (-2.4, -7.8, 2.2), (2.4, -1.2, 4.32), 'strap')


def build_bodyguard_suit(g):
    """A protective detail's suit: charcoal, cut roomy over a concealed vest, an earpiece coil down the collar."""
    d = JACKET + 0.15
    g.shell('body', 0, 12, d, 'wool')
    g.both(LEGS, lambda p, s: g.shell(p, -0.2, 1.4, d + 0.05, 'wool'))
    g.front('body', d, -1.4, -0.2, 1.4, 5.8, 'shirt', t=0.05)                                        # shirt and tie in the V
    g.front('body', d + 0.05, -0.35, 0.4, 0.35, 5.6, 'tie', t=0.1)
    g.front('body', d + 0.05, -0.5, 0.0, 0.5, 0.7, 'tie', t=0.15)
    lapels(g, d, 'lapel', y1=6.0, width=1.0)
    buttons(g, 'body', d + 0.05, 0, (6.8,), 'button')
    g.front('body', d, -3.4, 2.8, -1.8, 3.1, 'lapel', t=0.1)
    g.front('body', d + 0.1, 2.2, 1.6, 2.8, 2.2, 'pin', t=0.1)                                       # detail lapel pin
    for s in (-1, 1):
        g.front('body', d, s * 1.4 - (2.2 if s < 0 else 0), 8.6, s * 1.4 + (2.2 if s > 0 else 0), 9.0, 'lapel', t=0.12)
    sleeves(g, d, 'wool', end=8.0)
    g.back('body', d, 2.4, -1.0, 2.6, 2.4, 'coil', t=0.1)                                            # earpiece coil
    def leg(p, s):
        g.shell(p, 1.4, 11.2, PANTS, 'wool')
        g.front(p, PANTS, -0.1, 1.4, 0.1, 11.0, 'crease', t=0.04)
    g.both(LEGS, leg)


GRAIL = {
    'crusader_great_helm': (build_crusader_great_helm, dict(plate=blackened(), brass=metal('brass'), dark=flat('#050505', ink=False),
                                                            torse=cloth('#e8e4da', weave=0.03, stitch=None),
                                                            torse_b=cloth('#141416', weave=0.03, stitch=None)), 0.3),
    'crusader_plate': (build_crusader_plate, dict(plate=blackened(), ridge=blackened('#26262c'), edge=metal('brass'),
                                                  mail=mesh('#3a3a40')), 0.3),
    'crusader_tabard': (build_crusader_tabard, dict(linen=cloth('#e6e2d6', weave=0.06), cross=cloth('#141416', weave=0.04, stitch=None),
                                                    belt=leather('#1e1814'), buckle=metal('brass'), hem=cloth('#d0ccc0'),
                                                    grime=flat('#8a8270', ink=False)), 0.35),
    'crusader_pauldrons': (build_crusader_pauldrons, dict(plate=blackened(), brass=metal('brass'),
                                                          cross=flat('#e6e2d6')), 0.3),
    'crusader_cloak': (build_crusader_cloak, dict(wool=cloth('#16161a', weave=0.08), lining=cloth('#e6e2d6'), brass=metal('brass'),
                                                  chain=metal('brass'), hem=cloth('#202026'), fold=cloth('#0e0e12', weave=0.08)), 0.35),
    'crusader_greaves': (build_crusader_greaves, dict(plate=blackened(), brass=metal('brass')), 0.3),
    'crusader_gauntlets': (build_crusader_gauntlets, dict(plate=blackened(), brass=metal('brass')), 0.3),
    'crusader_sabatons': (build_crusader_sabatons, dict(plate=blackened(), brass=metal('brass'), sole=leather('#1e1814')), 0.3),
    'gothic_sallet': (build_gothic_sallet, dict(steel=metal('steel'), brass=metal('brass'), dark=flat('#0a0a0a', ink=False),
                                                flute=metal('steel')), 0.3),
    'gothic_cuirass': (build_gothic_cuirass, dict(steel=metal('steel'), flute=metal('steel'), brass=metal('brass'),
                                                  mail=mesh('#8a8c90'), leather=leather('#4a3020')), 0.3),
    'gothic_arm_harness': (build_gothic_arm_harness, dict(steel=metal('steel'), brass=metal('brass'), besagew=metal('steel'),
                                                          points=cloth('#7a1e24', stitch=None)), 0.3),
    'gothic_leg_harness': (build_gothic_leg_harness, dict(steel=metal('steel'), brass=metal('brass'), flute=metal('steel')), 0.3),
    'gothic_gauntlets': (build_gothic_gauntlets, dict(steel=metal('steel'), brass=metal('brass'), flute=metal('steel')), 0.3),
    'gothic_sabatons': (build_gothic_sabatons, dict(steel=metal('steel'), brass=metal('brass'), sole=leather('#3a2616')), 0.3),
    'bomb_disposal_suit': (build_bomb_disposal_suit, dict(cloth=cloth('#4a5236', weave=0.06), collar=cloth('#3e452e', weave=0.06),
                                                          plate=cloth('#1c1e1a', weave=0.04), strap=cloth('#2a2c26', stitch=None),
                                                          grille=plastic('#1a1a1a')), 0.45),
    'bomb_disposal_helmet': (build_bomb_disposal_helmet, dict(shell=plastic('#4a5236', gloss=0.15), frame=plastic('#1a1a1a'),
                                                              hose=rib('#3e452e')), 0.4),
    'shark_mesh_suit': (build_shark_mesh_suit, dict(mail=mesh('#a8aab0'), neo=rubber('#141416')), 0.35),
    'motorcycle_leathers': (build_motorcycle_leathers, dict(black=leather('#18181a', gloss=0.25), colour=leather('#b8241e', gloss=0.25),
                                                            stretch=cloth('#2a2a2c', weave=0.02, stitch=None), zip=metal('steel'),
                                                            puck=plastic('#d8d4c8', gloss=0.3)), 0.4),
    'scba_set': (build_scba_set, dict(strap=cloth('#1e1e1e', stitch=None), buckle=metal('steel'), plate=plastic('#1e1e1e'),
                                      cylinder=plastic('#c8c4b8', gloss=0.35), valve=metal('steel'), band=glow_tape(),
                                      hose=rubber('#1a1a1a'), gauge=plastic('#2a2a2a', gloss=0.4)), 0.45),
    'scba_mask': (build_scba_mask, dict(rubber=rubber('#1a1a1a'), frame=plastic('#2a2a2a', gloss=0.3),
                                        regulator=plastic('#c8a82a'), strap=rubber('#1a1a1a')), 0.4),
    'bodyguard_suit': (build_bodyguard_suit, dict(wool=cloth('#26272b', weave=0.03), lapel=cloth('#222327', weave=0.03),
                                                  shirt=cloth('#e4e2dc', weave=0.02), tie=cloth('#141416', stitch=None),
                                                  button=plastic('#111111'), pin=metal('brass'), coil=plastic('#d0d4d8', gloss=0.5),
                                                  crease=flat('#1c1d20', ink=False)), 0.3),
}


# ------------------------------------------------------------------ medical supports seen on the body
# Not items: worn models ClothingLayer draws on whichever part has them applied (the synced worn-medical mask).
# Dressings sit under the clothes; splints, slings, tourniquets, the collar and the knee brace go over them.
MED_UNDER, MED_OVER = 0.3, 0.95


def build_worn_bandage(g):
    g.box('head', (-HX - 0.3, -7.2, -HX - 0.3), (HX + 0.3, -5.6, HX + 0.3), 'gauze')   # round the brow, above the eyes
    g.box('head', (-HX - 0.4, -7.0, HX + 0.2), (-HX + 1.4, -5.8, HX + 0.5), 'gauze')    # the tucked end
    g.box('head', (-1.6, -7.0, -HX - 0.45), (0.2, -5.8, -HX - 0.3), 'stain')
    g.shell('body', 4.6, 8.4, MED_UNDER, 'gauze')
    g.front('body', MED_UNDER, -2.6, 5.6, -0.6, 7.2, 'stain', t=0.06)
    g.both(ARMS, lambda p, s: (g.shell(p, 4.2, 7.0, MED_UNDER, 'gauze'), g.side(p, MED_UNDER, s, -1.0, 5.0, 0.6, 6.4, 'stain', t=0.06)))
    g.both(LEGS, lambda p, s: (g.shell(p, 1.6, 4.6, MED_UNDER, 'gauze'), g.front(p, MED_UNDER, -1.0, 2.4, 0.8, 3.8, 'stain', t=0.06)))


def build_worn_gauze(g):
    """Packed gauze: a thick pad taped down, under the clothes."""
    def pad(p, put, *a):
        put(p, MED_UNDER, *a, 'pad', t=0.4)
    g.box('head', (1.0, -7.4, -HX - 0.5), (3.2, -5.4, -HX), 'pad')
    g.box('head', (0.6, -6.6, -HX - 0.55), (3.6, -6.2, -HX - 0.45), 'tape')
    g.front('body', MED_UNDER, 0.6, 3.4, 3.0, 6.0, 'pad', t=0.4)
    g.front('body', MED_UNDER + 0.4, 0.2, 4.5, 3.4, 4.9, 'tape', t=0.05)
    g.front('body', MED_UNDER + 0.4, 1.6, 3.0, 2.0, 6.4, 'tape', t=0.05)
    def limb(p, s, y0):
        g.side(p, MED_UNDER, s, -1.2, y0, 1.2, y0 + 2.2, 'pad', t=0.4)
        g.side(p, MED_UNDER + 0.4, s, -1.6, y0 + 0.9, 1.6, y0 + 1.3, 'tape', t=0.05)
    g.both(ARMS, lambda p, s: limb(p, s, 1.0))
    g.both(LEGS, lambda p, s: limb(p, s, 2.0))


def build_worn_wrap(g):
    """Elastic wrap: the wrist and forearm, the knee down the shin, wound in overlapping turns."""
    def arm(p, s):
        for i, y in enumerate((5.6, 6.5, 7.4, 8.3)):
            g.shell(p, y, y + 0.95, MED_UNDER + (0.06 if i % 2 else 0), 'wrap')
        g.front(p, MED_UNDER + 0.06, 0.6, 6.0, 1.4, 6.6, 'clip', t=0.08)
    def leg(p, s):
        for i, y in enumerate((4.4, 5.3, 6.2, 7.1, 8.0)):
            g.shell(p, y, y + 0.95, MED_UNDER + (0.06 if i % 2 else 0), 'wrap')
        g.front(p, MED_UNDER + 0.06, 0.6, 5.0, 1.4, 5.6, 'clip', t=0.08)
    g.both(ARMS, arm)
    g.both(LEGS, leg)


def build_worn_tourniquet(g):
    """A windlass tourniquet high on the limb, over the clothes."""
    def limb(p, s, y):
        g.shell(p, y, y + 0.7, MED_OVER - 0.2, 'strap')
        g.side(p, MED_OVER - 0.2, s, -1.4, y - 0.2, 1.0, y + 0.9, 'buckle', t=0.18)
        g.front(p, MED_OVER - 0.2, -1.8, y - 0.05, 1.8, y + 0.55, 'rod', t=0.35)       # the windlass rod
        g.front(p, MED_OVER + 0.15, 1.2, y - 0.4, 1.9, y + 0.9, 'clip', t=0.25)        # its keeper clip
        g.front(p, MED_OVER - 0.2, -1.2, y + 0.8, 0.2, y + 1.2, 'tag', t=0.06)          # the time written on the tag
    g.both(ARMS, lambda p, s: limb(p, s, -0.6))
    g.both(LEGS, lambda p, s: limb(p, s, 0.8))


def build_worn_splint(g):
    """Boards either side of the limb, tied on with strips."""
    def limb(p, s, y0, y1):
        for side in (-1, 1):
            g.side(p, MED_OVER - 0.3, side, -1.0, y0, 1.0, y1, 'board', t=0.35)
        (bx0, _, bz0), (bx1, _, bz1) = BOXES[p]
        w = MED_OVER + 0.1
        for y in (y0 + 0.6, (y0 + y1) / 2, y1 - 1.2):                                 # ties round boards and limb
            g.box(p, (bx0 - w, y, bz0 - 0.55), (bx1 + w, y + 0.6, bz1 + 0.55), 'tie')
    g.both(ARMS, lambda p, s: limb(p, s, 1.2, 9.4))
    g.both(LEGS, lambda p, s: limb(p, s, 1.0, 11.0))


def build_worn_finger_splint(g):
    def hand(p, s):
        g.front(p, 0.25, -1.4, 9.0, -0.6, 10.6, 'alu', t=0.14)
        g.box(p, (-1.5, 10.3, -2.4), (-0.5, 10.7, -2.1), 'alu')
        g.shell(p, 9.3, 9.7, 0.3, 'tape')
    g.both(ARMS, hand)


def build_worn_moldable_splint(g):
    """An orange roll splint folded into a gutter under the forearm or the calf, wrapped on."""
    def limb(p, s, y0, y1, under):
        put = g.back if under else g.front
        put(p, MED_OVER - 0.3, -2.0, y0, 2.0, y1, 'foam', t=0.3)
        for side in (-1, 1):
            g.side(p, MED_OVER - 0.3, side, -0.4 if under else -2.4, y0, 2.4 if under else 0.4, y1, 'foam', t=0.3)
        for y in (y0 + 0.4, y1 - 1.2):
            g.shell(p, y, y + 0.8, MED_OVER + 0.05, 'wrap')
    g.both(ARMS, lambda p, s: limb(p, s, 3.6, 9.4, True))
    g.both(LEGS, lambda p, s: limb(p, s, 5.0, 11.0, True))


def build_worn_sling(g):
    """A triangular sling cradling the forearm, its strap over the opposite shoulder."""
    def arm(p, s):
        g.shell(p, 4.4, 9.6, JACKET + 0.1, 'sling')
        g.front(p, JACKET + 0.1, -2.1, 9.6, 2.1, 10.3, 'sling', t=0.3)                 # the cradle under the wrist
        g.back(p, JACKET + 0.1, -1.2, 4.6, 1.2, 5.2, 'pin', t=0.12)
    g.both(ARMS, arm)
    for i in range(7):                                                               # the strap across the chest
        x, y = -3.4 + i * 1.0, -0.2 + i * 0.95
        g.front('body', JACKET + 0.2, x, y, x + 1.2, y + 1.1, 'sling', t=0.2)
    g.box('body', (-3.6, -0.35, -2 - JACKET - 0.2), (-2.2, 0.25, 2 + JACKET + 0.2), 'sling')


def build_worn_collar(g):
    """A cervical collar: a stiff ring from the chin down to the collarbone, below the eyes."""
    g.box('head', (-HX - 0.5, -2.0, -HX - 0.6), (HX + 0.5, 0.0, HX + 0.5), 'foam')
    g.box('head', (-2.4, -2.6, -HX - 0.75), (2.4, -1.6, -HX - 0.1), 'foam')            # the chin rest
    g.box('head', (-0.8, -1.6, -HX - 0.75), (0.8, -0.4, -HX - 0.6), 'vent')
    g.box('head', (-HX - 0.55, -1.6, -0.6), (HX + 0.55, -0.4, 0.6), 'velcro')
    g.box('body', (-3.4, -0.4, -2 - SHIRT - 0.4), (3.4, 0.6, 2 + SHIRT + 0.3), 'foam')  # the collarbone plate


def build_worn_knee_brace(g):
    def leg(p, s):
        g.front(p, MED_OVER - 0.4, -1.2, 4.6, 1.2, 7.2, 'neo', t=0.3)                 # open patella ring
        for side in (-1, 1):
            g.side(p, MED_OVER - 0.4, side, -1.2, 2.6, 1.2, 9.4, 'bar', t=0.3)          # hinged uprights
            g.side(p, MED_OVER - 0.1, side, -0.7, 5.4, 0.7, 6.6, 'hinge', t=0.3)
        for y in (2.8, 8.4):
            g.shell(p, y, y + 0.9, MED_OVER - 0.4, 'strap')
        g.shell(p, 3.8, 4.6, MED_OVER - 0.45, 'neo')
        g.shell(p, 7.2, 8.0, MED_OVER - 0.45, 'neo')
    g.both(LEGS, leg)


MEDICAL = {
    'worn_bandage': (build_worn_bandage, dict(gauze=cloth('#d8d3c4', weave=0.2, stitch=None), stain=flat('#7a2a22')), 0.3),
    'worn_gauze': (build_worn_gauze, dict(pad=cloth('#e0dccf', weave=0.25, stitch=None), tape=flat('#c9c3b0')), 0.3),
    'worn_wrap': (build_worn_wrap, dict(wrap=knit('#b89a78'), clip=metal()), 0.3),
    'worn_tourniquet': (build_worn_tourniquet, dict(strap=cloth('#1e1e1c', weave=0.1), buckle=plastic('#111111'),
                                                    rod=plastic('#1a1a1a'), clip=plastic('#1a1a1a'), tag=flat('#c8c4b4')), 0.3),
    'worn_splint': (build_worn_splint, dict(board=cloth('#8a6e48', weave=0.04, stitch=None), tie=cloth('#cfc9b8', stitch=None)), 0.35),
    'worn_finger_splint': (build_worn_finger_splint, dict(alu=metal('steel'), tape=flat('#d6d0c0')), 0.2),
    'worn_moldable_splint': (build_worn_moldable_splint, dict(foam=plastic('#c86a2a', gloss=0.1), wrap=knit('#b89a78')), 0.3),
    'worn_sling': (build_worn_sling, dict(sling=cloth('#3a4a6a', weave=0.06), pin=metal()), 0.3),
    'worn_collar': (build_worn_collar, dict(foam=plastic('#d8d4c8', gloss=0.1), vent=flat('#2a2a2a', ink=False),
                                            velcro=cloth('#2a2b2e', stitch=None)), 0.2),
    'worn_knee_brace': (build_worn_knee_brace, dict(neo=cloth('#26272a', weave=0.04, stitch=None), bar=metal('steel'),
                                                    hinge=plastic('#3a3b3e'), strap=cloth('#2e2f33', stitch=None)), 0.3),
}


GARMENTS.update(HEADWEAR)
GARMENTS.update(TOPS)
GARMENTS.update(LOWER)
GARMENTS.update(KIT)
GARMENTS.update(MILITARY)
GARMENTS.update(MILITARY_2)
GARMENTS.update(KIT_2)
GARMENTS.update(CIVILIAN)
GARMENTS.update(CIVILIAN_2)
GARMENTS.update(CIVILIAN_3)
GARMENTS.update(GRAIL)
GARMENTS.update(MEDICAL)


# ================================================================== export


def part_model(m, part, name):
    """The garment's boxes on one body part, moved so the part's pivot is the model's (8,8,8)."""
    out = M.Model(f'{name}/{part}')
    px, py, pz = PIVOTS[part]
    d = np.array([-px, -(Y0 - 8) + py, pz])    # assembled (8+X, Y0-Y, 8-Z) back to (8+x, 8-y, 8-z)
    idx = []
    for i, p in enumerate(m.parts):
        if p.get('part') != part:
            continue
        q = dict(p, lo=p['lo'] + d, hi=p['hi'] + d)
        if p['rot']:
            q['rot'] = dict(p['rot'], origin=list(np.array(p['rot']['origin']) + d))
        out.parts.append(q)
        idx.append(i)
    return out, idx


def display(m):
    disp = B.display(m)
    gui_rot = (12, -24, 0)
    s, t = M.fit(m, M.R_xyz(*gui_rot), 15.0)
    disp['gui'] = M.transform(gui_rot, t, s)
    return disp


# ================================================================== tailoring supplies (loot only, never worn)


def build_strips(g):
    """A bundle of cloth strips, folded over and tied: what ripping a garment up gives, and what patches it."""
    for i, (x0, x1, y0) in enumerate(((-3.6, 3.4, 4.6), (-3.2, 3.8, 3.9), (-3.8, 3.0, 3.2))):
        z0 = -1.4 + i * 0.12
        g.box('body', (x0, y0, z0), (x1, y0 + 0.75, z0 + 2.6), 'strip' if i != 1 else 'strip_b')
        g.box('body', (x1 - 0.1, y0 - 0.2, z0 + 0.3), (x1 + 0.35, y0 + 0.75, z0 + 2.3), 'strip' if i != 1 else 'strip_b')
    g.box('body', (-0.5, 3.0, -1.6), (0.5, 5.5, 1.5), 'tie')


SUPPLIES = {
    'denim_strips': (build_strips, dict(strip=denim('#34465e'), strip_b=denim('#3e5270'), tie=cloth('#8a8170')), 0.4),
    'leather_strips': (build_strips, dict(strip=leather('#5a3a26'), strip_b=leather('#6b4630'), tie=cloth('#8a8170')), 0.4),
}


def names():
    import roster
    out = {}
    for cat in roster.ROSTER.values():
        for line in cat.splitlines():
            line = line.strip()
            if line and not line.startswith('#'):
                bits = [b.strip() for b in line.split('|')]
                out[bits[0].rstrip('*')] = bits[1]
    return out


def paint(m, look, wear, seed):
    """Paints every visible face in the shared toon style, the way kit.paint does the guns: each run of touching
    boxes of one material is one piece, so where a garment's blocks join there's no line, and only the outside
    of each piece (and each pocket, strap or buckle on it) gets the outline and worn edges."""
    piece = T.pieces(m.parts)
    rng = np.random.default_rng(seed)
    patches = []
    ink_min, T.INK_MIN = T.INK_MIN, float('inf')        # no painted outline: the mesh's ink hull draws it in game
    try:
        _paint_faces(m, look, wear, seed, piece, rng, patches)
    finally:
        T.INK_MIN = ink_min
    return patches


def _paint_faces(m, look, wear, seed, piece, rng, patches):
    for i, p in enumerate(m.parts):
        mates = [q for j, q in enumerate(m.parts) if j != i and piece[j] == piece[i]]
        for name, (_ax, _sgn, ua, _ud, va, _vd) in M.FACES.items():
            if name in p.get('skip', ()):
                continue
            pos, n, _edge, uv = M.face_grid(p, name)
            covers = T.cover(p, name, pos, n, mates, M.PX)
            if T.step_face(p, pos, n, mates):
                covers = [np.ones(pos.shape[:2], bool)] * 4
            edge = T.outer_edge(p, name, covers, M.PX)
            f = P.Face(pos, n, edge, p, rng, wear, seed + i, uv)
            rgb = P.round_shade(f, look[p['mat']](f))
            for fx in look.get('_after', []):
                rgb = fx(f, rgb)
            lu, lv = p['hi'][ua] - p['lo'][ua], p['hi'][va] - p['lo'][va]
            rgb, _ink = T.style(f, rgb, p['mat'], M.PX, n[1], lu, lv)
            patches.append((i, name, np.clip(rgb, 0, 1)))


OUTLINE = 0.04          # the ink hull round each garment, in pixels
FACE_CORNERS = {        # a face's corners at uv (u0,v0), (u1,v0), (u0,v1), as Minecraft maps a block model's faces
    'north': lambda a, b: ((b[0], b[1], a[2]), (a[0], b[1], a[2]), (b[0], a[1], a[2])),
    'south': lambda a, b: ((a[0], b[1], b[2]), (b[0], b[1], b[2]), (a[0], a[1], b[2])),
    'east': lambda a, b: ((b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], b[2])),
    'west': lambda a, b: ((a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], a[2])),
    'up': lambda a, b: ((a[0], b[1], a[2]), (b[0], b[1], a[2]), (a[0], b[1], b[2])),
    'down': lambda a, b: ((a[0], a[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], a[2])),
}
NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def mesh_triangles(model):
    """A block model's faces as triangles wound counter-clockwise from outside (the renderer culls), each corner
    with its uv (0..1) and the direction the ink hull pushes it: straight out of every face of its box, so the
    outline is the same width all round."""
    rows = []
    for e in model['elements']:
        a, b = np.array(e['from'], float), np.array(e['to'], float)
        mid = (a + b) / 2
        for face, d in e['faces'].items():
            c0, c1, c3 = (np.array(c, float) for c in FACE_CORNERS[face](a, b))
            c2 = c1 + c3 - c0
            u0, v0, u1, v1 = (x / 16 for x in d['uv'])
            corner = {0: (c0, u0, v0), 1: (c1, u1, v0), 2: (c2, u1, v1), 3: (c3, u0, v1)}
            n = np.array(NORMALS[face], float)
            for tri in ((0, 3, 2), (0, 2, 1)):
                pts = [corner[k][0] for k in tri]
                if np.dot(np.cross(pts[1] - pts[0], pts[2] - pts[0]), n) < 0:
                    tri = (tri[0], tri[2], tri[1])
                for k in tri:
                    pos, u, v = corner[k]
                    push = np.sign(np.round(pos - mid, 4))
                    rows.append((pos, u, v, push))
    return rows


def write_mesh(mesh_id, parts, png):
    """Packs parts ({name: block model json}) and the garment's texture into meshes/<id>.zmesh, the format the
    weapons thread's renderer reads (tools/meshguns/pack.py has it): drawn toon-style with an ink hull."""
    buf = io.BytesIO()
    png_bytes = png.read_bytes()
    buf.write(b'ZMSH')
    buf.write(struct.pack('>i', 3))
    buf.write(struct.pack('>f', OUTLINE))
    buf.write(struct.pack('>i', len(png_bytes)))
    buf.write(png_bytes)
    buf.write(struct.pack('>i', len(parts)))
    for name, model in parts.items():
        rows = mesh_triangles(model)
        b = name.encode()
        buf.write(struct.pack('>H', len(b)))
        buf.write(b)
        buf.write(struct.pack('>i', len(rows) // 3))
        for pos, u, v, push in rows:
            q = np.clip(np.round(pos * 1000), -32768, 32767).astype(int)
            buf.write(struct.pack('>hhhHHbbb', *q, int(round(u * 65535)), int(round(v * 65535)),
                                  *(int(x) * 127 for x in push)))
    data = _cars_pack.scramble(zlib.compress(buf.getvalue(), 9), mesh_id)
    dest = ASSETS / f'meshes/{mesh_id}.zmesh'
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)


def mesh_item(base, mesh_id, part, texture=None):
    """An item definition drawn by the shared mesh renderer, the block model still giving its display transforms."""
    model = {'type': f'{NS}:mesh', 'id': mesh_id, 'part': part}
    if texture:
        model['texture'] = texture
    return {'type': 'minecraft:special', 'base': base, 'model': model}


def camo_item(base, mesh_id, part, gid, camos):
    """The same, choosing the texture by the stack's camo (clothing/ClothingLoot.CAMO); the first is the default."""
    if not camos:
        return mesh_item(base, mesh_id, part)
    return {'type': 'minecraft:select', 'property': 'minecraft:component', 'component': f'{NS}:camo',
            'cases': [{'when': c, 'model': mesh_item(base, mesh_id, part, f'{NS}:textures/item/clothing/{gid}/camo_{c}.png')}
                      for c in camos[1:]],
            'fallback': mesh_item(base, mesh_id, part)}


def main(only):
    lang_path = ASSETS / 'lang/en_us.json'
    lang = json.loads(lang_path.read_text())
    titles = names()
    for gid, (build, mats, wear) in {**GARMENTS, **SUPPLIES}.items():
        if only and gid not in only:
            continue
        g = Garment(gid)
        build(g)
        m = g.m
        m.tidy()
        seed = zlib.crc32(gid.encode()) % 1000
        lo, hi = m.bounds()
        camos = list(CAMOS) if any(isinstance(v, Camo) for v in mats.values()) else []
        CAMO_FOR[gid] = camos

        def look(name):
            return dict({k: v.make(name) if isinstance(v, Camo) else v for k, v in mats.items()},
                        _after=worn(wear, low=lo[1] + 4))
        patches = paint(m, look(camos[0] if camos else None), wear, seed)
        img, uvs, T = M.pack(patches)
        tex = ASSETS / f'textures/item/clothing/{gid}.png'
        tex.parent.mkdir(parents=True, exist_ok=True)
        M.save_png(img, tex)
        # every other camo: the same boxes painted again, so they pack to the same layout
        for name in camos[1:]:
            cimg, cuvs, _ = M.pack(paint(m, look(name), wear, seed))
            assert cuvs == uvs, f'{gid} {name}: the camo texture packed differently'
            (ASSETS / f'textures/item/clothing/{gid}').mkdir(exist_ok=True)
            M.save_png(cimg, ASSETS / f'textures/item/clothing/{gid}/camo_{name}.png')
        ref = f'{NS}:item/clothing/{gid}'
        # the loot model: the whole garment, centred
        whole = M.Model(gid)
        whole.parts = [dict(p) for p in m.parts]
        blo, bhi = whole.bounds()
        shift = B.C - (blo + bhi) / 2
        whole.shift(shift)
        whole_json = M.model_json(whole, uvs, ref, display(whole), gui_light='side')
        M.dump(whole_json, ASSETS / f'models/item/clothing/{gid}.json')
        mesh_id = f'clothing/{gid}'
        # worn in the head slot, ClothingLayer draws it: vanilla's own head layer gets nothing
        if gid not in MEDICAL and gid not in OTHERS_ITEMS:   # drawn on the body only; the item is someone else's
            M.dump({'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                              'cases': [{'when': 'head', 'model': {'type': 'minecraft:empty'}}],
                              'fallback': camo_item(ref, mesh_id, 'loot', gid, camos)}}, ASSETS / f'items/{gid}.json')
        parts = [] if gid in SUPPLIES else sorted({p['part'] for p in m.parts})
        meshes = {'loot': whole_json}
        for part in parts:
            pm, idx = part_model(m, part, gid)
            puvs = {(k, f): uvs[(i, f)] for k, i in enumerate(idx) for f in M.FACES if (i, f) in uvs}
            pj = M.model_json(pm, puvs, ref, {}, gui_light='side')
            M.dump(pj, ASSETS / f'models/item/clothing/{gid}/{part}.json')
            M.dump({'model': camo_item(f'{ref}/{part}', mesh_id, part, gid, camos)}, ASSETS / f'items/clothing/{gid}/{part}.json')
            meshes[part] = pj
        write_mesh(mesh_id, meshes, tex)
        if gid not in MEDICAL and gid not in OTHERS_ITEMS:
            lang[f'item.{NS}.{gid}'] = titles.get(gid, gid.replace('_', ' ').title())
        print(f'{gid}: {len(m.parts)} boxes on {", ".join(parts)}, {T}px')
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + '\n')
    write_parts()
    write_camos()


CAMO_FOR = {}


def write_camos():
    """The server's list of the camos each piece comes in (clothing/Camos), from the garments made this run and the
    ones already listed."""
    java = ROOT / 'src/main/java/com/zomboidcraft/clothing/Camos.java'
    have = dict(re.findall(r'Map.entry\("(\w+)", List.of\(([^)]*)\)\)', java.read_text())) if java.exists() else {}
    rows = {gid: ', '.join(f'"{c}"' for c in camos) for gid, camos in CAMO_FOR.items()}
    rows = {**{k: v for k, v in have.items() if k in GARMENTS}, **rows}
    rows = {k: v for k, v in rows.items() if v}
    body = ',\n'.join(f'            Map.entry("{gid}", List.of({c}))' for gid, c in rows.items()) or '            Map.entry("none", List.of())'
    java.write_text('''package com.zomboidcraft.clothing;

import java.util.List;
import java.util.Map;

/** Generated by tools/items/gen_clothes.py: the camo patterns each piece comes in, the first its default. */
public final class Camos {
    public static final Map<String, List<String>> FOR = Map.ofEntries(
''' + body + ''');

    private Camos() {
    }
}
''')


def write_parts():
    """The client's list of which body parts each garment has a worn model for (a missing one would draw as the
    missing-model cube)."""
    rows = []
    for gid, (build, _, _) in GARMENTS.items():
        g = Garment(gid)
        build(g)
        parts = sorted({p['part'] for p in g.m.parts})
        rows.append(f'            Map.entry("{gid}", List.of({", ".join(chr(34) + q + chr(34) for q in parts)}))')
    java = ROOT / 'src/client/java/com/zomboidcraft/client/clothing/WornParts.java'
    java.parent.mkdir(parents=True, exist_ok=True)
    java.write_text('''package com.zomboidcraft.client.clothing;

import java.util.List;
import java.util.Map;

/** Generated by tools/items/gen_clothes.py: the body parts each garment has a worn model for. */
final class WornParts {
    static final Map<String, List<String>> PARTS = Map.ofEntries(
''' + ',\n'.join(rows) + ''');

    private WornParts() {
    }
}
''')


if __name__ == '__main__':
    main(set(sys.argv[1:]))
