"""Things to throw (combat/throwing): each built from the reference photos in
/mnt/project-files/zomboid-mod/refs/items/throwables/<name>/ and painted in the gun style (toon.py, like new).

    python3 tools/weapons/throwables.py

Each is built standing on y = 0 with its front toward +z, at 1 unit = 1.5 cm, and its display sizes come from its
real size, so a grenade sits small in the fist and a bottle fills it. Only those with photos in so far are built;
the rest keep their stand-in models until theirs land.

Josiah 2026-10-27: the throwables plan (11 of them); blasts start contained fires instead of breaking blocks.
"""
import math
from pathlib import Path

import numpy as np

import kit
import model as M
import paint as P
from headgear import rod_x, rod_y as _rod_y


def rod_y(m, y0, y1, r, mat, x=0.0, z=0.0, **kw):
    return _rod_y(m, y0, y1, r, mat, x, z, **kw)

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'
CM = 1 / 1.5            # units per centimetre


def lathe(m, y0, y1, radius, mat, slices, x=0.0, z=0.0):
    """A turned shape up the y axis: slices round rods whose radius follows radius(t), t 0 at y0 to 1 at y1."""
    for i in range(slices):
        a, b = y0 + (y1 - y0) * i / slices, y0 + (y1 - y0) * (i + 1) / slices
        r = radius((i + 0.5) / slices)
        if r > 0.05:
            rod_y(m, a, b, r, mat, x, z)


# ---------------------------------------------------------------- frag grenade

FRAG_R = 2.85 * CM                      # the body's widest, 57 mm across (photo 04, the M26 shape)
FRAG_BODY = 7.7 * CM                    # foot to shoulder
FRAG_WIDEST = 0.45


def frag_radius(t):
    """The lemon-shaped body: widest a little below the middle, a rounder bottom and a longer taper to the top."""
    k = (t - FRAG_WIDEST) / (FRAG_WIDEST if t < FRAG_WIDEST else 1 - FRAG_WIDEST)
    return FRAG_R * max(0.36, math.sqrt(max(0.0, 1 - k * k)) ** 0.8)


def frag_grenade(m):
    """An army fragmentation grenade of the M26 shape (photo 04): an egg-shaped olive body on a short foot, the
    seam band round it below the middle, a yellow band at the shoulder (live, not practice), the fuze on top with
    its square head, the spoon running from over the head down the side of the body, and the pin through the
    head with its pull ring hanging off the far side and the safety clip over the spoon."""
    foot = 0.35 * CM
    rod_y(m, 0.0, foot, FRAG_R * 0.42, 'body')
    top = foot + FRAG_BODY
    lathe(m, foot, top, frag_radius, 'body', 9)
    seam = foot + FRAG_BODY * 0.4
    rod_y(m, seam, seam + 0.4 * CM, frag_radius(0.4) + 0.2, 'body')
    stripe = foot + FRAG_BODY * 0.86
    rod_y(m, stripe, stripe + 0.45 * CM, frag_radius(0.88) + 0.04, 'stripe')
    # the fuze: a neck out of the body and the square head the spoon hinges on
    neck = top + 1.0 * CM
    rod_y(m, top - 0.3, neck, 0.75 * CM, 'fuze')
    head = neck + 1.4 * CM
    m.box((-1.0 * CM, neck, -0.8 * CM), (0.9 * CM, head, 0.8 * CM), 'fuze')
    # the spoon: over the head, then down the side (-x) close to the body, its tail flared out a little
    w = 1.0 * CM
    m.box((-1.25 * CM, head, -w / 2), (0.6 * CM, head + 0.18 * CM, w / 2), 'spoon')
    m.box((-1.25 * CM, neck - 0.2 * CM, -w / 2), (-1.07 * CM, head + 0.18 * CM, w / 2), 'spoon')
    pts = [(-1.16 * CM, neck - 0.1 * CM)]
    for t in (0.9, 0.72, 0.52, 0.36):
        pts.append((-(frag_radius(t) + 0.1), foot + FRAG_BODY * t))
    for p0, p1 in zip(pts, pts[1:]):
        m.seg(p0, p1, 0.18 * CM, w, 'spoon', z=0.0, over=0.04)
    # the pin through the head, the ring on the far side, and the safety clip
    rod_x(m, -1.4 * CM, 1.25 * CM, 0.12 * CM, 'pin', neck + 0.75 * CM, 0.25 * CM)
    m.arc((1.25 * CM + 1.0 * CM, neck + 0.75 * CM - 0.5 * CM), 1.05 * CM, 0, 360, 0.16 * CM, 0.16 * CM, 'pin',
          steps=16, z=0.25 * CM)
    m.box((-1.45 * CM, neck - 0.6 * CM, -0.55 * CM), (-0.9 * CM, neck - 0.42 * CM, 0.55 * CM), 'clip')


FRAG_LOOK = {
    'body': lambda f: P.polymer(f, '#4c5233', 0.07),
    'stripe': lambda f: P.polymer(f, '#c9a32a', 0.05),
    'fuze': lambda f: P.parkerised(f, '#3f423a'),
    'spoon': lambda f: P.polymer(f, '#555b3d', 0.05),
    'pin': lambda f: P.steel(f, 0.62, tint='#a9adb0', spread=0.1),
    'clip': lambda f: P.steel(f, 0.5, tint='#8a8e90', spread=0.1),
}


# ---------------------------------------------------------------- molotov

def molotov(m):
    """A molotov (photos 01-03): a wine bottle of dark green glass with its paper label, full of fuel, and a
    fuel-soaked rag stuffed in the neck, its tail hanging down the shoulder."""
    r = 3.8 * CM
    body = 19.0 * CM
    rod_y(m, 0.0, 0.5, r - 0.15, 'glass')               # the base, a little in from the side
    rod_y(m, 0.5, body, r, 'glass')
    rod_y(m, 4.0 * CM, 13.0 * CM, r + 0.04, 'label')    # the label round the body
    shoulder, neck_r = 6.0 * CM, 1.35 * CM
    lathe(m, body, body + shoulder, lambda t: neck_r + (r - neck_r) * (1 - t) ** 1.6, 'glass', 6)
    top = body + shoulder + 8.0 * CM
    rod_y(m, body + shoulder - 0.2, top, neck_r, 'glass')
    rod_y(m, top - 0.8 * CM, top, neck_r + 0.12, 'glass')          # the lip
    # the rag: tied round the top of the neck, a wad stuffed in the mouth, and its tail hanging down the side
    rod_y(m, top - 3.2 * CM, top + 0.3 * CM, neck_r + 0.22, 'rag')
    rod_y(m, top + 0.3 * CM, top + 2.6 * CM, neck_r * 0.9, 'rag')
    m.box((-1.3 * CM, top + 2.2 * CM, -1.2 * CM), (1.2 * CM, top + 3.4 * CM, 1.3 * CM), 'rag')
    x = neck_r + 0.22
    tail = [(x, top - 1.0 * CM), (x + 0.5 * CM, top - 5.0 * CM), (x + 1.4 * CM, top - 9.0 * CM)]
    for i, (p0, p1) in enumerate(zip(tail, tail[1:])):
        m.seg(p0, p1, 0.45 * CM, (2.6 - 0.6 * i) * CM, 'rag', z=0.0, over=0.15)


def glass(f):
    """Dark green bottle glass with the fuel showing through: darker down the middle, a cold highlight up one side."""
    c = P.plain(f, '#2f5a2c', 0.04)
    side = np.clip((f.P[..., 0] * f.n[2] - f.P[..., 2] * f.n[0] + 1.5) / 3, 0, 1) if f.n[1] == 0 else 0.5
    c = c * (0.8 + 0.35 * side)[..., None] if np.ndim(side) else c
    streak = (np.abs(f.uv[..., 0] - 0.35) < 0.12) & (f.n[1] == 0)
    return np.where(streak[..., None], P.mix(c, P.col('#b9d6b0'), 0.45), c)


def label(f):
    """The wine label: cream paper, a broad orange band across it and a few lines of print."""
    c = P.plain(f, '#e4dcc3', 0.05)
    v = f.uv[..., 1] / max(f.uv[..., 1].max(), 1e-3)
    c = np.where((np.abs(v - 0.42) < 0.17)[..., None], P.plain(f, '#d96a2b', 0.05), c)
    lines = (np.abs(((v - 0.72) * 14) % 1 - 0.5) < 0.12) & (v > 0.68) & (v < 0.9)
    return np.where(lines[..., None], P.mix(c, P.col('#3a3026'), 0.6), c)


def rag(f):
    c = P.plain(f, '#b7a585', 0.12)
    soaked = np.clip((f.noise(0.9, 77, 3) - 0.4) * 2.5, 0, 1)
    return P.mix(c, P.col('#6d5a3a'), soaked * 0.6)


MOLOTOV_LOOK = {'glass': glass, 'label': label, 'rag': rag}


# ---------------------------------------------------------------- the list, and writing them

class Throwable:
    def __init__(self, key, build, look, seed, cm):
        """cm: its longest size in real life, for the display sizes."""
        self.key, self.build, self.look, self.seed, self.cm = key, build, look, seed, cm


THROWABLES = [
    Throwable('frag_grenade', frag_grenade, FRAG_LOOK, 1101, 11.0),
    Throwable('molotov', molotov, MOLOTOV_LOOK, 1102, 34.0),
]


def display(m, cm):
    """GUI: fills the slot. In the hand and on the ground: sized by how big it really is (a 26 cm radio is 8
    units in the hand), never so small it can't be seen."""
    hand = max(3.2, cm * 8.0 / 26.0)
    d = {}
    gui_rot = (18, -32, 0)
    s, t = M.fit(m, M.R_xyz(*gui_rot), 14.0)
    d['gui'] = M.transform(gui_rot, t, s)
    for ctx, rot, trans, size in (('thirdperson_righthand', [0, 0, 0], [0, 1.5, 1.0], hand),
                                  ('firstperson_righthand', [0, -25, 0], [1.2, 1.6, 0], hand * 0.95)):
        s2, t2 = M.fit(m, M.R_xyz(*rot), size)
        d[ctx] = M.transform(rot, np.array(trans) + t2, s2)
    s3, t3 = M.fit(m, np.eye(3), max(2.4, cm * 7.0 / 26.0), (0, 2, 0))
    d['ground'] = M.transform([0, 0, 0], t3, s3)
    s4, t4 = M.fit(m, M.R_xyz(0, 180, 0), 12)
    d['fixed'] = M.transform([0, 180, 0], t4, s4)
    return d


def main():
    for th in THROWABLES:
        m = kit.drawn(th.key, th.build, t=(8.0, 0.5, 8.0), look=th.look, seed=th.seed, wear=kit.LIKE_NEW)
        patches = kit.paint([m])
        img, uvs, T = kit.pack(patches)
        tex = f'item/throwable/{th.key}'
        kit.save_textures(img, ASSETS / f'textures/{tex}.png')
        M.dump({'gui_light': 'front', 'textures': {'0': f'{NS}:{tex}', 'particle': f'{NS}:{tex}'},
                'elements': kit.elements(m, 0, uvs, np.zeros(3)), 'display': display(m, th.cm)},
               ASSETS / f'models/item/throwable/{th.key}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/throwable/{th.key}'}},
               ASSETS / f'items/{th.key}.json')
        print(f'{th.key}: {len(m.parts)} boxes, {T}px')


if __name__ == '__main__':
    main()
