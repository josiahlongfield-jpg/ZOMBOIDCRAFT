"""Bag item models for ZomboidCraft: school backpack, hiking pack, military rucksack, duffel, plate carrier and
fanny pack, built from boxes and painted per face with the weapon tooling (tools/weapons/model.py, paint.py).

Writes models/item/bag/<name>.json, textures/item/bag/<name>.png and items/<name>.json.

    python3 tools/items/gen_bags.py
"""
import sys
import zlib
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/weapons'))
import model as M  # noqa: E402
import paint as P  # noqa: E402

ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'
C = np.array([8.0, 8.0, 8.0])


# ================================================================== materials


def fabric(base, weave=0.11, mottle=0.16, seam=0.32):
    """Canvas or nylon: a fine weave, faded patches, darker seams and a row of stitches just inside each edge."""
    def fn(f):
        c = P.plain(f, base, mottle)
        u, v = f.uv[..., 0], f.uv[..., 1]
        threads = ((np.floor(u * 6) + np.floor(v * 6)) % 2) * 2 - 1
        c = c * (1 + threads * weave * 0.35)[..., None]
        fade = np.clip((f.noise(0.35, 201, 3) - 0.5) * 1.6, 0, 1)
        c = P.mix(c, c * 1.18 + 0.03, fade * 0.5)
        shade = np.clip(f.edge / 0.6, 0, 1) * 0.22 + 0.78
        c = c * shade[..., None]
        stitch = (np.abs(f.edge - seam) < 0.07) & ((np.floor((u + v) * 3.2) % 2) == 0)
        if min(f.dims[i] for i in range(3) if abs(f.n[i]) < 0.5) > 1.2:
            c = np.where(stitch[..., None], c * 1.35 + 0.05, c)
        return c
    return fn


def webbing(base='#26261f'):
    """Woven strap: ribs across its length."""
    def fn(f):
        c = P.plain(f, base, 0.12)
        rib = np.sin(f.along() * 9.0) * 0.5 + 0.5
        c = c * (0.86 + rib * 0.2)[..., None]
        edge = np.clip(f.edge / 0.15, 0, 1)
        return c * (0.75 + 0.25 * edge)[..., None]
    return fn


def zipper(tape='#1d1d1f', teeth='#8a8d90'):
    def fn(f):
        c = P.plain(f, tape, 0.1)
        mid = np.abs(f.across()[0]) < 0.12
        t = (np.floor(f.along() * 5.0) % 2) == 0
        return np.where((mid & t)[..., None], P.col(teeth) * (0.8 + 0.3 * P.white(f.shape, f.rng))[..., None], c)
    return fn


def molle(base):
    """Military webbing rows stitched across a panel: the PALS grid."""
    inner = fabric(base, mottle=0.12)

    def fn(f):
        c = inner(f)
        row = (np.abs(((f.Y + 0.25) % 1.25) - 0.62) < 0.2)
        gap = (np.floor(f.X * 0.75 + f.Z * 0.75) % 2) == 0
        if abs(f.n[1]) < 0.5:
            c = np.where((row & gap)[..., None], c * 0.72, c)
        return c
    return fn


def reflective(f):
    c = P.plain(f, '#b9bcb6', 0.08)
    return c * (0.85 + 0.3 * P.white(f.shape, f.rng))[..., None]


def hide(base, stitch=None):
    """Bag leather: oiled grain, darker toward the cut edges, a saddle stitch inside them when stitch is given."""
    def fn(f):
        c = P.leather(f, base)
        c = c * (0.8 + 0.2 * np.clip(f.edge / 0.5, 0, 1))[..., None]
        if stitch is not None and min(f.dims[i] for i in range(3) if abs(f.n[i]) < 0.5) > 1.2:
            u, v = f.uv[..., 0], f.uv[..., 1]
            run = (np.abs(f.edge - 0.3) < 0.07) & ((np.floor((u + v) * 3.2) % 2) == 0)
            c = np.where(run[..., None], P.col(stitch) * 0.9, c)
        return c
    return fn


def kraft(f):
    """Brown paper: a fibrous mottle and soft creases."""
    c = P.plain(f, '#a67c4f', 0.1)
    crease = np.abs(np.sin(f.along() * 2.3 + f.noise(0.4, 77, 2) * 4)) < 0.08
    return np.where(crease[..., None], c * 0.82, c)


def film(base='#e9e7df', print_='#b03a2e'):
    """A thin plastic carrier bag: pale, crumpled, a printed band across its middle."""
    def fn(f):
        c = P.plain(f, base, 0.08)
        crumple = f.noise(0.6, 91, 3)
        c = c * (0.86 + 0.22 * crumple)[..., None]
        if abs(f.n[2]) > 0.5:
            band = np.abs(f.Y - 6.5) < 1.1
            c = np.where(band[..., None], P.mix(c, P.col(print_), 0.75), c)
        return c
    return fn


def worn_look(wear, blood=0.25, **mats):
    d = dict(mats)
    d['_after'] = [
        lambda f, c: P.grime(f, c, 0.2 + wear * 0.35),
        lambda f, c: P.blood(f, c, blood, (-99, 99)),
    ]
    return d


# ================================================================== bags
# Built facing +z (the front), straps on -z, standing on y = 0.


def straps(m, x0, x1, h, back, mat='strap'):
    for x in (x0, x1):
        m.box([x - 0.6, h * 0.12, back - 0.7], [x + 0.6, h * 0.95, back - 0.25], mat)
        m.box([x - 0.6, h * 0.86, back - 0.7], [x + 0.6, h * 0.99, back + 0.6], mat)


def build_school(m):
    m.box([-4, 0, -2], [4, 10, 2], 'body')
    m.box([-3.6, 9.4, -1.8], [3.6, 10.6, 1.8], 'body')                 # rounded top
    m.box([-3, 1, 2], [3, 5.6, 3.4], 'pocket')                           # front pocket
    m.box([-2.9, 5.5, 2.05], [2.9, 5.85, 3.45], 'zip')
    m.box([-3.9, 8.6, 1.95], [3.9, 9.0, 2.15], 'zip')                    # main zip round the top
    m.box([-1.4, 10.55, -0.4], [1.4, 11.3, 0.4], 'strap')                # carry loop
    m.box([-1.0, 2.6, 3.38], [1.0, 3.3, 3.5], 'patch')
    straps(m, -2.4, 2.4, 10, -2)


def build_hiking(m):
    m.box([-4, 0, -2.5], [4, 12.5, 2.5], 'body')
    m.box([-4.3, 11.5, -2.8], [4.3, 14, 3.0], 'lid')                     # top lid
    m.box([-3.2, 2.5, 2.5], [3.2, 8.5, 3.6], 'pocket')
    m.box([-3.1, 8.4, 2.55], [3.1, 8.75, 3.65], 'zip')
    for x in (-4, 4):                                                    # side bottle pockets
        sgn = 1 if x > 0 else -1
        m.box([x if sgn > 0 else x - 1.3, 0.5, -1.6], [x + 1.3 if sgn > 0 else x, 5.5, 1.6], 'mesh')
    m.box([-5.5, -0.2, -1.4], [5.5, 2.6, 1.4], 'roll')                   # sleeping mat under the pack
    for x in (-3.5, 3.5):
        m.box([x - 0.35, -0.3, 1.35], [x + 0.35, 2.7, 1.55], 'strap')
    m.box([-3.6, 9.5, 3.55], [3.6, 9.9, 3.7], 'reflect')
    m.box([-4.4, 3.0, -3.0], [4.4, 4.2, -2.4], 'strap')                  # hip belt
    straps(m, -2.5, 2.5, 13, -2.5)


def build_rucksack(m):
    m.box([-5, 0, -3], [5, 12, 3], 'body')
    m.box([-5.3, 10.5, -3.2], [5.3, 13, 3.6], 'flap')                    # top flap over the front
    m.box([-5.3, 8.2, 3.0], [5.3, 13, 3.6], 'flap')
    for x0, x1 in ((-8, -5), (5, 8)):                                     # side pouches
        m.box([x0, 1, -2], [x1, 8, 2], 'pouch')
        m.box([x0 - 0.05, 7.4, -2.05], [x1 + 0.05, 8.4, 2.05], 'pouch')
    m.box([-3.5, 1.5, 3], [3.5, 7.5, 4.5], 'pouch')                      # front pouch
    m.box([-3.6, 6.8, 3], [3.6, 7.8, 4.6], 'pouch')
    for x in (-2.6, 2.6):                                                 # compression straps down the flap and pouch
        m.box([x - 0.45, 4.6, 3.55], [x + 0.45, 12.6, 3.8], 'strap')
        m.box([x - 0.45, 4.6, 4.5], [x + 0.45, 7.8, 4.75], 'strap')
        m.box([x - 0.6, 8.6, 3.75], [x + 0.6, 9.5, 4.0], 'buckle')
        m.box([x - 0.6, 5.0, 4.7], [x + 0.6, 5.9, 4.95], 'buckle')
    m.box([-5.2, 0.6, -3.6], [5.2, 2.0, -2.9], 'strap')
    straps(m, -3, 3, 12, -3)


def build_duffel(m):
    m.box([-7, 0, -3], [7, 5.6, 3], 'body')
    m.box([-6.6, 5.4, -2.6], [6.6, 6.2, 2.6], 'body')                    # soft top
    for x in (-7.4, 7.0):                                                 # round end panels
        m.box([x, 0.4, -2.6], [x + 0.4, 5.4, 2.6], 'end')
    m.box([-6.4, 6.1, -0.18], [6.4, 6.45, 0.18], 'zip')
    for z in (-1.3, 1.3):                                                 # two carry handles, sewn on each side
        for x in (-2.2, 1.6):
            m.box([x, 6.0, z - 0.35], [x + 0.6, 7.6, z + 0.35], 'strap')
        m.box([-2.2, 7.4, z - 0.35], [2.2, 8.0, z + 0.35], 'strap')
    m.box([-3.5, 1.0, 3.0], [3.5, 4.2, 3.5], 'pocket')                   # end pocket on the side
    m.box([-3.4, 4.1, 3.05], [3.4, 4.4, 3.55], 'zip')
    m.box([-6.0, 0.8, 2.95], [-4.0, 1.4, 3.15], 'patch')


def build_carrier(m):
    # the plates sit a torso's depth apart (4.6 px), so the carrier wraps the body when it is worn
    m.box([-4.5, 0, 2.3], [4.5, 10, 3.6], 'body')                         # front plate bag
    m.box([-4.5, 0, -3.6], [4.5, 10, -2.3], 'body')                       # back plate bag
    for x in (-3.6, 2.4):                                                 # shoulder straps
        m.box([x, 9.6, -3.6], [x + 1.2, 10.8, 3.6], 'strap')
    for x in (-5.1, 4.5):                                                 # cummerbund
        m.box([x, 1.5, -3.6], [x + 0.6, 6, 3.6], 'strap')
    for i, x in enumerate((-3.6, -1.1, 1.4)):                             # three magazine pouches
        m.box([x, 1.2, 3.6], [x + 2.2, 5.8, 5.0], 'pouch')
        m.box([x - 0.05, 5.2, 3.55], [x + 2.25, 6.3, 5.1], 'pouch')
    m.box([-3.0, 7.0, 3.6], [3.0, 9.2, 4.0], 'velcro')                    # name tape panel
    m.box([-1.8, 7.5, 3.98], [1.8, 8.7, 4.1], 'patch')


def build_fanny(m):
    m.box([-3.5, 0, -1.4], [3.5, 3.6, 1.4], 'body')
    m.box([-3.3, 3.3, -1.2], [3.3, 3.95, 1.2], 'body')
    m.box([-3.4, 3.2, 1.35], [3.4, 3.55, 1.5], 'zip')
    m.box([-2.2, 0.6, 1.4], [2.2, 2.4, 2.1], 'pocket')
    m.box([-8, 1.6, -1.6], [-3.5, 2.6, -1.0], 'strap')                    # belt
    m.box([3.5, 1.6, -1.6], [8, 2.6, -1.0], 'strap')
    m.box([-0.6, 1.5, -1.75], [0.6, 2.7, -1.45], 'buckle')


# The twelve everyday bags (refs/items/bags/): what people were carrying when it happened.


def handle(m, x0, x1, y, z, mat='handle', h=1.6, t=0.45):
    """A carry handle: two posts and a grip over them."""
    for x in (x0, x1 - t):
        m.box([x, y, z - t / 2], [x + t, y + h, z + t / 2], mat)
    m.box([x0, y + h - t, z - t / 2], [x1, y + h, z + t / 2], mat)


def build_purse(m):
    # a stiff handbag: a trapezoid body, a flap with a clasp, one rolled top handle (purse/03)
    m.box([-3.5, 0, -1.3], [3.5, 3.2, 1.3], 'body')
    m.box([-3.1, 3.2, -1.1], [3.1, 4.4, 1.1], 'body')
    m.box([-3.2, 2.2, 1.25], [3.2, 4.5, 1.5], 'flap')
    m.box([-0.6, 2.4, 1.48], [0.6, 3.0, 1.68], 'clasp')
    m.box([-3.6, -0.15, -1.4], [3.6, 0.15, 1.4], 'flap')                # base welt
    handle(m, -2.2, 2.2, 4.4, 0, h=2.2, t=0.5)


def build_briefcase(m):
    # hard leather case: slim, two brass latches, a stitched handle (briefcase/02-03)
    m.box([-6, 0, -1.2], [6, 8, 1.2], 'body')
    m.box([-6.1, 7.4, -1.3], [6.1, 8.2, 1.3], 'lid')                     # the lid's lip
    for x in (-4.4, 3.4):
        m.box([x, 6.6, 1.2], [x + 1.0, 7.8, 1.45], 'brass')
        m.box([x + 0.2, -0.3, -0.9], [x + 0.8, 0, 0.9], 'brass')        # feet
    m.box([-0.8, 7.1, 1.2], [0.8, 7.6, 1.38], 'brass')                   # lock
    handle(m, -2, 2, 8.2, 0, h=1.5, t=0.55)


def build_gym(m):
    # a barrel bag: a long round body, end discs, a printed side band, two handles (gym-bag/04)
    m.box([-6.5, 0.4, -2.6], [6.5, 5.0, 2.6], 'body')
    m.box([-6.5, 0, -2.1], [6.5, 5.4, 2.1], 'body')
    for x in (-6.9, 6.5):
        m.box([x, 0.5, -2.3], [x + 0.4, 4.9, 2.3], 'end')
    m.box([-4.8, 1.4, 2.55], [4.8, 3.0, 2.7], 'band')
    m.box([-5.8, 5.3, -0.18], [5.8, 5.6, 0.18], 'zip')
    for z in (-1.2, 1.2):
        handle(m, -2, 2, 5.3, z, 'strap', h=1.8)


def build_doctors(m):
    # a Gladstone bag: wide flat base, hinged frame mouth, a strap and buckle round it (doctors-bag/01, 04)
    m.box([-6, 0, -2.8], [6, 5.2, 2.8], 'body')
    m.box([-5.5, 5.2, -2.2], [5.5, 6.4, 2.2], 'body')
    m.box([-5.7, 6.2, -0.35], [5.7, 6.9, 0.35], 'frame')                 # the steel frame along the mouth
    m.box([-6.2, -0.2, -3.0], [6.2, 0.4, 3.0], 'welt')
    for x in (-3.6, 3.0):                                                 # a strap round it
        m.box([x, 0, 2.8], [x + 0.6, 6.3, 3.0], 'welt')
    m.box([-0.5, 5.6, 2.2], [0.5, 6.4, 2.45], 'clasp')
    handle(m, -1.8, 1.8, 6.8, 0, h=1.8, t=0.6)


def build_plastic(m):
    # a T-shirt carrier bag, full and sagging, its two loop handles up (plastic-shopping-bag/01)
    m.box([-3.8, 0, -1.8], [3.8, 7, 1.8], 'body')
    m.box([-3.4, 7, -1.4], [3.4, 8.2, 1.4], 'body')
    for x in (-3.4, 1.8):
        m.box([x, 8.2, -0.5], [x + 1.6, 10.8, 0.5], 'body')
    m.box([-3.4, 10.4, -0.4], [3.4, 11.0, 0.4], 'body')


def build_paper(m):
    # a grocery sack: square-bottomed, its top folded over once
    m.box([-3.6, 0, -2.2], [3.6, 9, 2.2], 'body')
    m.box([-3.7, 8.2, -2.3], [3.7, 9.4, 2.3], 'fold')
    m.box([-3.0, 9.4, -1.6], [-1.6, 10.6, 1.6], 'greens')                # a bunch of something sticking out


def build_suitcase(m):
    # an old hard suitcase: two shells, a lid seam, corner caps, two latches, a top handle (suitcase/02-03)
    m.box([-7, 0, -2.4], [7, 10, 2.4], 'body')
    m.box([-7.1, 4.6, -2.5], [7.1, 5.2, 2.5], 'seam')
    for x in (-7.15, 6.15):
        for y in (-0.1, 9.1):
            m.box([x, y, -2.55], [x + 1.0, y + 1.0, 2.55], 'corner')
    for x in (-4.6, 3.6):
        m.box([x, 9.4, -0.5], [x + 1.0, 10.15, 0.5], 'brass')
    m.box([-3.5, 3.0, 2.4], [-1.0, 4.8, 2.5], 'sticker')
    m.box([2.0, 6.0, 2.4], [4.2, 7.6, 2.5], 'sticker2')
    handle(m, -2, 2, 10, 0, h=1.5, t=0.6)


def build_lunchbox(m):
    # a domed metal lunch pail with a wire handle and side latch (lunchbox/02)
    m.box([-4, 0, -2], [4, 3.6, 2], 'tin')
    m.box([-4, 3.6, -1.6], [4, 4.6, 1.6], 'lid')
    m.box([-3.6, 4.6, -1.0], [3.6, 5.1, 1.0], 'lid')
    m.box([-0.4, 2.6, 2.0], [0.4, 4.0, 2.25], 'steel')
    handle(m, -2.4, 2.4, 5.1, 0, 'steel', h=1.4, t=0.3)


def build_cooler(m):
    # a picnic cooler: red tub, white lid, moulded handles at the ends (cooler/04)
    m.box([-6, 0, -3.5], [6, 6.5, 3.5], 'tub')
    m.box([-6.2, 6.5, -3.7], [6.2, 8, 3.7], 'lid')
    m.box([-5.4, 8, -3.0], [5.4, 8.3, 3.0], 'lid')
    for x in (-6.9, 6.0):
        m.box([x, 4.4, -1.6], [x + 0.9, 5.2, 1.6], 'lid')
    m.box([-4.5, 1.2, 3.5], [4.5, 1.6, 3.6], 'tub')                     # moulded rib
    m.box([-2.5, 6.0, 3.55], [2.5, 6.6, 3.75], 'lid')                    # the lid's lip at the front


def build_mail(m):
    # a carrier's satchel: canvas body, a leather flap and a wide strap over the shoulder (mail-bag/04, 01)
    m.box([-5, 0, -1.6], [5, 8, 1.6], 'body')
    m.box([-5.1, 4.8, 1.5], [5.1, 8.1, 1.9], 'flap')
    m.box([-0.6, 4.4, 1.85], [0.6, 5.4, 2.05], 'brass')
    for x in (-5.6, 5.0):
        m.box([x, 7, -0.5], [x + 0.6, 12, 0.5], 'strap')
    m.box([-5.6, 12, -0.5], [5.6, 12.6, 0.5], 'strap')
    m.box([-3.8, 2.4, 1.6], [3.8, 3.4, 1.7], 'print')


def build_tool(m):
    # an open-mouth tool bag: stiff canvas, leather base and handles, tools showing (tool-bag/01)
    m.box([-6, 0, -2.4], [6, 6, 2.4], 'body')
    m.box([-6.2, 0, -2.6], [6.2, 1.2, 2.6], 'base')
    m.box([-6.1, 5.6, -2.5], [6.1, 6.3, 2.5], 'base')                    # the stiffened rim
    for x0, x1 in ((-5.2, -2.4), (2.4, 5.2)):                             # pockets on the front
        m.box([x0, 1.4, 2.4], [x1, 4.4, 2.9], 'body')
    m.box([-3.0, 6.3, -1.0], [-2.4, 9.2, -0.4], 'steel')                  # a hammer shaft
    m.box([-3.8, 8.6, -1.2], [-1.6, 9.4, -0.2], 'steel')
    m.box([1.0, 6.3, 0.4], [1.5, 8.2, 0.9], 'grip')                      # a screwdriver
    for z in (-1.4, 1.4):
        handle(m, -3, 3, 6.3, z, 'base', h=2.2, t=0.55)


def build_ammo_pouch(m):
    # a belt pouch for magazines: a box with a snap flap, belt loop behind (ammo-pouch/02, 04)
    m.box([-2.4, 0, -1.4], [2.4, 4.4, 1.4], 'body')
    m.box([-2.5, 3.0, -1.5], [2.5, 4.8, 1.6], 'flap')
    m.box([-0.4, 3.2, 1.55], [0.4, 3.9, 1.75], 'snap')
    m.box([-1.8, 1.0, -1.9], [1.8, 4.2, -1.4], 'body')                    # belt loop
    m.box([-8, 2.0, -2.2], [-2.4, 3.0, -1.6], 'belt')
    m.box([2.4, 2.0, -2.2], [8, 3.0, -1.6], 'belt')


BAGS = [
    ('school_backpack', build_school, 0.6, 0.3, dict(
        body=fabric('#7a2f2a'), pocket=fabric('#2d3a55'), zip=zipper(), strap=webbing('#1f2026'),
        patch=reflective)),
    ('hiking_backpack', build_hiking, 0.5, 0.2, dict(
        body=fabric('#3f5236'), lid=fabric('#9a5a1f'), pocket=fabric('#2f3d2a'), zip=zipper(), mesh=fabric('#232a22', weave=0.4),
        roll=fabric('#5d6a74', seam=0.2), strap=webbing('#202219'), reflect=reflective)),
    ('military_rucksack', build_rucksack, 0.7, 0.35, dict(
        body=molle('#4b4d33'), flap=fabric('#47492f'), pouch=molle('#505236'), strap=webbing('#30311f'),
        buckle=lambda f: P.polymer(f, '#1b1b1a'))),
    ('duffel_bag', build_duffel, 0.6, 0.25, dict(
        body=fabric('#1f2a3c'), end=fabric('#141a24'), pocket=fabric('#1a2332'), zip=zipper('#121212'),
        strap=webbing('#141416'), patch=reflective)),
    ('plate_carrier', build_carrier, 0.75, 0.4, dict(
        body=molle('#7d6b4c'), pouch=molle('#75644a'), strap=webbing('#5e5039'), velcro=fabric('#6e5f45', weave=0.3),
        patch=lambda f: P.plain(f, '#2a2b25', 0.2))),
    ('fanny_pack', build_fanny, 0.55, 0.15, dict(
        body=fabric('#1d2b2c'), pocket=fabric('#2a6a6c'), zip=zipper(), strap=webbing('#151515'),
        buckle=lambda f: P.polymer(f, '#121212'))),
    ('purse', build_purse, 0.4, 0.15, dict(
        body=hide('#5f7a2c'), flap=hide('#506826', '#d9d2b4'), clasp=lambda f: P.chrome(f), handle=hide('#4a6022'))),
    ('briefcase', build_briefcase, 0.5, 0.15, dict(
        body=hide('#1b1715', '#4a4038'), lid=hide('#141110'), brass=lambda f: P.brass(f), handle=hide('#141110'))),
    ('gym_bag', build_gym, 0.5, 0.2, dict(
        body=fabric('#17181a'), end=fabric('#101112'), band=lambda f: P.plain(f, '#d9c22a', 0.1), zip=zipper(),
        strap=webbing('#121213'))),
    ('doctors_bag', build_doctors, 0.6, 0.3, dict(
        body=hide('#4b2a17', '#a88a62'), welt=hide('#3a2012'), frame=lambda f: P.steel(f, 0.45),
        clasp=lambda f: P.brass(f), handle=hide('#3a2012'))),
    ('plastic_bag', build_plastic, 0.3, 0.1, dict(body=film())),
    ('paper_bag', build_paper, 0.4, 0.1, dict(body=kraft, fold=kraft, greens=lambda f: P.plain(f, '#4f7a2e', 0.25))),
    ('suitcase', build_suitcase, 0.6, 0.15, dict(
        body=hide('#7a3a22'), seam=hide('#4a2414'), corner=hide('#3a1f12'), brass=lambda f: P.brass(f),
        sticker=lambda f: P.plain(f, '#d8c79a', 0.15), sticker2=lambda f: P.plain(f, '#3e6d8a', 0.15),
        handle=hide('#3a1f12'))),
    ('lunchbox', build_lunchbox, 0.6, 0.1, dict(
        tin=lambda f: P.paint(f, '#1c1d20', chips=0.12), lid=lambda f: P.paint(f, '#24262a', chips=0.12),
        steel=lambda f: P.steel(f, 0.5))),
    ('cooler', build_cooler, 0.5, 0.2, dict(
        tub=lambda f: P.polymer(f, '#b8262a'), lid=lambda f: P.polymer(f, '#e3e0d6', 0.05))),
    ('mail_bag', build_mail, 0.6, 0.15, dict(
        body=fabric('#b5a27a'), flap=hide('#6b4524', '#d9c9a0'), brass=lambda f: P.brass(f), strap=hide('#5a3a1e'),
        print=lambda f: P.plain(f, '#2f3a5a', 0.15))),
    ('tool_bag', build_tool, 0.7, 0.2, dict(
        body=fabric('#6d5f3e', weave=0.2), base=hide('#3b2617'), steel=lambda f: P.steel(f, 0.5),
        grip=lambda f: P.polymer(f, '#b8352a'))),
    ('ammo_pouch_belt', build_ammo_pouch, 0.6, 0.15, dict(
        body=fabric('#4d4f36'), flap=fabric('#45472f'), snap=lambda f: P.polymer(f, '#141414'), belt=webbing('#3c3d2a'))),
]


VANILLA = {  # how a carried bag sits in the hand
    'thirdperson_righthand': ([0, 0, 0], [0, -1.5, 1.5], 0.55),
    'firstperson_righthand': ([0, -20, 0], [1.5, -1.0, 0], 0.5),
}


def display(m):
    disp = {}
    gui_rot = (18, -32, 0)
    s, t = M.fit(m, M.R_xyz(*gui_rot), 14.0)
    disp['gui'] = M.transform(gui_rot, t, s)
    for ctx, (rot, trans, scale) in VANILLA.items():
        R = M.R_xyz(*rot)
        s2, t2 = M.fit(m, R, 10.0)
        disp[ctx] = M.transform(rot, np.array(trans) + t2, s2)
    s3, t3 = M.fit(m, np.eye(3), 8.0, (0, 2, 0))
    disp['ground'] = M.transform([0, 0, 0], t3, s3)
    s4, t4 = M.fit(m, M.R_xyz(0, 180, 0), 13)
    disp['fixed'] = M.transform([0, 180, 0], t4, s4)
    disp['head'] = M.transform([0, 180, 0], [0, -4, -7], 0.9)
    return disp


def main():
    for name, build, wear, blood, mats in BAGS:
        m = M.Model(name)
        build(m)
        lo, hi = m.bounds()
        m.shift(C - (lo + hi) / 2)
        m.tidy()
        seed = zlib.crc32(name.encode()) % 1000
        patches = M.paint_model(m, worn_look(wear, blood, **mats), wear, seed)
        img, uvs, T = M.pack(patches)
        tex = ASSETS / f'textures/item/bag/{name}.png'
        tex.parent.mkdir(parents=True, exist_ok=True)
        M.save_png(img, tex)
        M.dump(M.model_json(m, uvs, f'{NS}:item/bag/{name}', display(m), gui_light='side'),
               ASSETS / f'models/item/bag/{name}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/bag/{name}'}}, ASSETS / f'items/{name}.json')
        print(f'{name}: {len(m.parts)} boxes, {T}px')


if __name__ == '__main__':
    main()
