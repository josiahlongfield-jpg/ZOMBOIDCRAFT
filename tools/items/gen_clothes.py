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
        """A sleeve of cloth d out from the part, from y0 down to y1."""
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
    """Lit tops, dark undersides, a light line under the top edge of side faces, a 1-texel ink outline and, on
    faces big enough, a row of stitches just inside the edge."""
    ny = f.n[1]
    if ny > 0.5:
        c = c * 1.1
    elif ny < -0.5:
        c = c * 0.72
    else:
        top = f.box['hi'][1] - f.Y
        c = np.where(((top > TX) & (top < 2 * TX))[..., None], c * 1.18 + 0.02, c)
    if stitch is not None:
        big = min(f.dims[i] for i in range(3) if abs(f.n[i]) < 0.5) > 1.0
        if big:
            u, v = f.uv[..., 0], f.uv[..., 1]
            st = (np.abs(f.edge - 0.32) < TX * 0.6) & ((np.floor((u + v) / (2 * TX)) % 2) == 0)
            c = np.where(st[..., None], P.mix(c, P.col(stitch), 0.55), c)
    if ink:
        c = np.where((f.edge < TX)[..., None], c * 0.32, c)
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


def main(only):
    lang_path = ASSETS / 'lang/en_us.json'
    lang = json.loads(lang_path.read_text())
    titles = names()
    for gid, (build, mats, wear) in GARMENTS.items():
        if only and gid not in only:
            continue
        g = Garment(gid)
        build(g)
        m = g.m
        m.tidy()
        seed = zlib.crc32(gid.encode()) % 1000
        lo, hi = m.bounds()
        look = dict(mats, _after=worn(wear, low=lo[1] + 4))
        patches = M.paint_model(m, look, wear, seed)
        img, uvs, T = M.pack(patches)
        tex = ASSETS / f'textures/item/clothing/{gid}.png'
        tex.parent.mkdir(parents=True, exist_ok=True)
        M.save_png(img, tex)
        ref = f'{NS}:item/clothing/{gid}'
        # the loot model: the whole garment, centred
        whole = M.Model(gid)
        whole.parts = [dict(p) for p in m.parts]
        blo, bhi = whole.bounds()
        shift = B.C - (blo + bhi) / 2
        whole.shift(shift)
        M.dump(M.model_json(whole, uvs, ref, display(whole), gui_light='side'), ASSETS / f'models/item/clothing/{gid}.json')
        # worn in the head slot, ClothingLayer draws it: vanilla's own head layer gets nothing
        M.dump({'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                          'cases': [{'when': 'head', 'model': {'type': 'minecraft:empty'}}],
                          'fallback': {'type': 'minecraft:model', 'model': ref}}}, ASSETS / f'items/{gid}.json')
        parts = sorted({p['part'] for p in m.parts})
        for part in parts:
            pm, idx = part_model(m, part, gid)
            puvs = {(k, f): uvs[(i, f)] for k, i in enumerate(idx) for f in M.FACES if (i, f) in uvs}
            M.dump(M.model_json(pm, puvs, ref, {}, gui_light='side'), ASSETS / f'models/item/clothing/{gid}/{part}.json')
            M.dump({'model': {'type': 'minecraft:model', 'model': f'{ref}/{part}'}}, ASSETS / f'items/clothing/{gid}/{part}.json')
        lang[f'item.{NS}.{gid}'] = titles.get(gid, gid.replace('_', ' ').title())
        print(f'{gid}: {len(m.parts)} boxes on {", ".join(parts)}, {T}px')
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + '\n')
    write_parts()


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
