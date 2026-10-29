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
    g.box('head', (-5.3, -7.6, -5.6), (5.3, -6.4, 5.0), 'cloth')                    # the wide flat top
    g.box('head', (-4.9, -7.9, -5.2), (4.9, -7.6, 4.6), 'cloth')
    g.box('head', (-3.8, EYES_HIGH - 0.55, -7.0), (3.8, EYES_HIGH - 0.2, -4.45), 'visor')
    g.box('head', (-3.9, -5.2, -4.62), (3.9, -4.95, -4.45), 'cord')                 # gold chin cord
    for s in (-1, 1):
        g.box('head', (s * 3.9 - 0.2, -5.3, -4.7), (s * 3.9 + 0.2, -4.85, -4.5), 'button')
    g.box('head', (-0.9, -7.2, -5.7), (0.9, -5.4, -5.55), 'badge')
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
    g.front('body', -0.8 + SHIRT, -0.3, 0.3, 0.3, 5.6, 'tie', t=0.2)                 # the shirt and tie showing in the V
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
    d = 1.0
    g.shell('body', 0.8, 10.6, d, 'mesh')
    for s in (-1, 1):                                                               # over the shoulders
        x0, x1 = (-3.6, -1.6) if s < 0 else (1.6, 3.6)
        g.box('body', (x0, -0.3, -2 - d), (x1, 0.8, 2 + d), 'mesh')
        g.box('body', (x0 + 0.5, -0.4, -2 - d - 0.06), (x0 + 1.3, 10.6, -2 - d), 'tape')   # braces of tape
        g.box('body', (x0 + 0.5, -0.4, 2 + d), (x0 + 1.3, 10.6, 2 + d + 0.06), 'tape')
    g.shell('body', 7.4, 8.4, d + 0.06, 'tape')                                    # the waist hoop
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
    'hawaiian_shirt': (build_hawaiian_shirt, dict(cloth=flowers('#2c5560'), shellbutton=flat('#d8ccb4', ink=False)), 0.35),
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
    'bomber_jacket': (build_bomber_jacket, dict(nylon=cloth('#3d4430', weave=0.02, stitch='#5a604a'), rib=rib('#2a2d24'),
                                                zip=metal('brass'), patch=flat('#6e5a2a')), 0.4),
    'varsity_jacket': (build_varsity_jacket, dict(wool=cloth('#3e2228', weave=0.02), stripe_rib=stripes('#3e2228', '#a89c80', 0.45),
                                                  snapw=metal('steel'), letter=cloth('#a89c80', stitch='#3e2228'),
                                                  leatherwelt=leather('#c0b8a4'), leather=leather('#c0b8a4')), 0.4),
    'windbreaker': (build_windbreaker, dict(nylon=cloth('#2d3e5a', weave=0.02), panel=cloth('#7a6a3a', weave=0.02),
                                            zip=metal('steel'), pull=flat('#1d1d1f')), 0.35),
    'raincoat': (build_raincoat, dict(pvc=plastic('#8a7a2a', 0.3), snap=metal('steel'), tape=glow_tape()), 0.45),
    'trench_coat': (build_trench_coat, dict(gab=cloth('#7a6a4c', weave=0.06, stitch='#5a4c36'), horn=plastic('#2a2018'),
                                            buckle=leather('#3a2a1c')), 0.45),
    'suit_jacket': (build_suit_jacket, dict(wool=cloth('#2c2f36', weave=0.03, stitch=None), lapel=cloth('#272a30', stitch=None),
                                            horn=plastic('#1a1714'), square=cloth('#7a2a26'), tie=cloth('#5a2228', stitch=None)), 0.35),
    'lab_coat': (build_lab_coat, dict(cotton=cloth('#b6b3aa', weave=0.04), button=flat('#d8d4c8', ink=False), pen=plastic('#1d2d5a'),
                                      pen2=plastic('#2a2a2a'), badge=flat('#4a6a8a')), 0.5),
    'hi_vis_vest': (build_hi_vis_vest, dict(mesh=mesh('#b5b33a'), tape=glow_tape(), zip=metal('steel')), 0.55),
}


GARMENTS.update(HEADWEAR)
GARMENTS.update(TOPS)


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
