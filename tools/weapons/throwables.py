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



# ---------------------------------------------------------------- glass bottle and brick

def glass_bottle(m):
    """An empty beer bottle (refs/items/drinks photo 07 for the shape): a straight body, a round shoulder into a
    long neck and a thick lip, brown glass with a paper label. The empties drinks leave behind."""
    r = 3.0 * CM
    body = 13.0 * CM
    rod_y(m, 0.0, 0.4, r - 0.15, 'amber')
    rod_y(m, 0.4, body, r, 'amber')
    rod_y(m, 4.0 * CM, 10.0 * CM, r + 0.04, 'beer_label')
    neck_r = 1.25 * CM
    lathe(m, body, body + 4.0 * CM, lambda t: neck_r + (r - neck_r) * math.cos(t * math.pi / 2) ** 1.2, 'amber', 5)
    top = body + 4.0 * CM + 6.0 * CM
    rod_y(m, body + 3.8 * CM, top, neck_r, 'amber')
    rod_y(m, top - 1.0 * CM, top, neck_r + 0.2, 'amber')


def amber(f):
    """Brown bottle glass, a pale highlight streak up one side."""
    c = P.plain(f, '#5a3312', 0.04)
    streak = (np.abs(f.uv[..., 0] - 0.35) < 0.12) & (f.n[1] == 0)
    return np.where(streak[..., None], P.mix(c, P.col('#d9b98a'), 0.45), c)


def beer_label(f):
    """A plain made-up beer label: cream with a red oval band and a line of print."""
    c = P.plain(f, '#e8dfc4', 0.05)
    v = f.uv[..., 1] / max(f.uv[..., 1].max(), 1e-3)
    c = np.where((np.abs(v - 0.45) < 0.2)[..., None], P.plain(f, '#a3241d', 0.05), c)
    print_ = (np.abs(v - 0.82) < 0.04)
    return np.where(print_[..., None], P.mix(c, P.col('#2a2420'), 0.6), c)


BRICK = (20.3 * CM, 5.7 * CM, 9.2 * CM)      # a standard American brick, laid flat


def brick(m):
    """A red building brick (the brick walls in refs/roadside/diner-brick): a standard size block with its
    three cored holes through it top to bottom."""
    L, H, W = BRICK
    m.box((-L / 2, 0, -W / 2), (L / 2, H, W / 2), 'brick')
    for x in (-L / 3, 0, L / 3):
        for y0, y1 in ((H - 0.02, H + 0.01), (-0.01, 0.02)):
            m.box((x - 1.2 * CM, y0, -1.6 * CM), (x + 1.2 * CM, y1, 1.6 * CM), 'hole')


def brick_paint(f):
    c = P.plain(f, '#9c4a32', 0.14)
    c = P.mix(c, P.col('#6e3424'), np.clip((f.noise(1.6, 31, 3) - 0.45) * 2, 0, 1) * 0.5)
    return P.mix(c, P.col('#b98a6a'), np.clip((f.noise(4.0, 32, 2) - 0.7) * 3, 0, 1) * 0.4)


BOTTLE_LOOK = {'amber': amber, 'beer_label': beer_label}
BRICK_LOOK = {'brick': brick_paint, 'hole': lambda f: P.plain(f, '#2a1610', 0.05)}


# ---------------------------------------------------------------- the grenade fuze (smoke, flashbang)

def fuze_top(m, top, neck_r, ring=1.05 * CM, second=False):
    """The fuze screwed into the top of a can grenade: a short neck and square head, the spoon over the head
    and down the -x side, the pin through the head with its round pull ring off +x, and (second) the flashbang's
    triangle ring above it."""
    neck = top + 0.9 * CM
    rod_y(m, top - 0.3, neck, neck_r, 'fuze')
    head = neck + 1.3 * CM
    m.box((-0.95 * CM, neck, -0.75 * CM), (0.85 * CM, head, 0.75 * CM), 'fuze')
    w = 0.95 * CM
    m.box((-1.2 * CM, head, -w / 2), (0.55 * CM, head + 0.18 * CM, w / 2), 'spoon')
    m.box((-1.2 * CM, top - 4.5 * CM, -w / 2), (-1.02 * CM, head + 0.18 * CM, w / 2), 'spoon')
    py = neck + 0.7 * CM
    rod_x(m, -1.35 * CM, 1.2 * CM, 0.12 * CM, 'pin', py, 0.25 * CM)
    m.arc((1.2 * CM + ring, py - 0.45 * CM), ring, 0, 360, 0.16 * CM, 0.16 * CM, 'pin', steps=16, z=0.25 * CM)
    if second:
        # the triangle ring, standing up over the head
        a, b, c = (-0.9 * CM, head + 0.3 * CM), (0.9 * CM, head + 0.3 * CM), (0.0, head + 2.2 * CM)
        for p0, p1 in ((a, b), (b, c), (c, a)):
            m.seg(p0, p1, 0.16 * CM, 0.16 * CM, 'pin', z=-0.2 * CM, over=0.08)
    return head


FUZE_LOOK = {
    'fuze': lambda f: P.parkerised(f, '#3f423a'),
    'spoon': lambda f: P.polymer(f, '#555b3d', 0.05),
    'pin': lambda f: P.steel(f, 0.62, tint='#a9adb0', spread=0.1),
}


# ---------------------------------------------------------------- smoke grenade

def smoke_grenade(m):
    """An M18 coloured smoke grenade (photos 01-03): a plain olive drab steel can 6.4 cm across with a rolled rim
    top and bottom, its colour band and stencilled lettering near the top, and the fuze on top."""
    r, body = 3.2 * CM, 11.5 * CM
    rod_y(m, 0.0, 0.5 * CM, r + 0.08, 'can')
    rod_y(m, 0.5 * CM, body - 0.5 * CM, r, 'can')
    rod_y(m, body - 0.5 * CM, body, r + 0.08, 'can')
    rod_y(m, body - 3.2 * CM, body - 1.4 * CM, r + 0.03, 'smoke_band')
    rod_y(m, body, body + 0.4 * CM, r * 0.75, 'can')
    fuze_top(m, body + 0.4 * CM, 0.75 * CM)


def smoke_can(f):
    c = P.polymer(f, '#4a4d36', 0.06)
    v = f.uv[..., 1] / max(f.uv[..., 1].max(), 1e-3)
    print_ = (np.abs(((v - 0.35) * 9) % 1 - 0.5) < 0.1) & (v > 0.3) & (v < 0.62) & (f.n[1] == 0)
    return np.where(print_[..., None], P.mix(c, P.col('#d8d3b8'), 0.75), c)


SMOKE_LOOK = dict(FUZE_LOOK, can=smoke_can, smoke_band=lambda f: P.polymer(f, '#7d5aa0', 0.05))


# ---------------------------------------------------------------- flashbang

def flashbang(m):
    """An M84 stun grenade (photos 01-02): a steel tube 13.3 cm tall with all its fuze, three rows of big oval
    ports cut round it, a hexagon foot, a pale green band near the top and a brown one below, and the fuze with
    both pull rings."""
    r, body = 2.2 * CM, 9.2 * CM
    m.box((-r * 1.05, 0.0, -r * 0.6), (r * 1.05, 1.2 * CM, r * 0.6), 'steel')     # the hexagon foot
    m.box((-r * 0.6, 0.0, -r * 1.05), (r * 0.6, 1.2 * CM, r * 1.05), 'steel')
    rod_y(m, 1.2 * CM, body, r, 'steel')
    for row in range(3):
        y = 2.0 * CM + row * 2.2 * CM
        for x, z, w, d in ((0, r, 1.0 * CM, 0.04), (0, -r, 1.0 * CM, 0.04), (r, 0, 0.04, 1.0 * CM), (-r, 0, 0.04, 1.0 * CM)):
            lo = (x - w / 2 if x == 0 else x - 0.02 * math.copysign(1, x), y, z - d / 2 if z == 0 else z - 0.02 * math.copysign(1, z))
            hi = (x + w / 2 if x == 0 else x + 0.02 * math.copysign(1, x), y + 1.5 * CM, z + d / 2 if z == 0 else z + 0.02 * math.copysign(1, z))
            m.box(lo, hi, 'port')
    rod_y(m, body - 1.6 * CM, body - 1.0 * CM, r + 0.04, 'green_band')
    rod_y(m, 1.2 * CM, 1.7 * CM, r + 0.04, 'brown_band')
    fuze_top(m, body, 0.7 * CM, ring=1.15 * CM, second=True)


FLASH_LOOK = dict(FUZE_LOOK, steel=lambda f: P.parkerised(f, '#4b4f3e'),
                  port=lambda f: P.plain(f, '#141511', 0.05),
                  green_band=lambda f: P.plain(f, '#9fc79a', 0.05),
                  brown_band=lambda f: P.plain(f, '#6b4a2c', 0.05))


# ---------------------------------------------------------------- road flare

def road_flare(m):
    """A road flare (fusee, photos 01 and 05): a red waxed-paper tube 3 cm across and about 30 cm long, the
    striker end on top under its brown paper cap, and the print down the side."""
    r, L = 1.5 * CM, 27.0 * CM
    rod_y(m, 0.0, 0.4 * CM, r - 0.08, 'flare_end')                  # the plugged bottom
    rod_y(m, 0.4 * CM, L, r, 'flare_tube')
    rod_y(m, L - 4.5 * CM, L + 0.2 * CM, r + 0.1, 'cap')            # the striker cap over the top
    rod_y(m, L + 0.2 * CM, L + 0.35 * CM, r * 0.8, 'cap')


def flare_tube(f):
    c = P.plain(f, '#c2302a', 0.08)
    v = f.uv[..., 1] / max(f.uv[..., 1].max(), 1e-3)
    print_ = (np.abs(((v - 0.2) * 7) % 1 - 0.5) < 0.12) & (v > 0.18) & (v < 0.75) & (f.n[1] == 0)
    return np.where(print_[..., None], P.mix(c, P.col('#f1e3c4'), 0.7), c)


FLARE_LOOK = {'flare_tube': flare_tube, 'cap': lambda f: P.plain(f, '#7a5232', 0.1),
              'flare_end': lambda f: P.plain(f, '#8f231f', 0.06)}


# ---------------------------------------------------------------- firecrackers

def firecrackers(m):
    """A string of firecrackers (photos 02, 09): little red paper tubes plaited in pairs down a centre fuse,
    each pair angled down and out like a fishbone, the long fuse hanging out of the top."""
    pairs, step = 10, 1.3 * CM
    top = pairs * step + 1.0 * CM
    rod_y(m, 0.0, top, 0.25 * CM, 'braid')
    for i in range(pairs):
        y = 0.6 * CM + i * step
        z = 0.35 * CM if i % 2 else -0.35 * CM
        for s in (1, -1):
            m.seg((s * 0.2 * CM, y + 1.4 * CM), (s * 3.6 * CM, y), 0.6 * CM, 0.6 * CM, 'cracker', z=z, over=0.05)
    m.seg((0.0, top), (0.6 * CM, top + 4.0 * CM), 0.15 * CM, 0.15 * CM, 'wick', z=0.0)


def cracker(f):
    c = P.plain(f, '#d0283a', 0.1)
    return P.mix(c, P.col('#f0c23a'), np.clip((f.noise(3.0, 51, 2) - 0.72) * 4, 0, 1) * 0.6)


CRACKER_LOOK = {'cracker': cracker, 'braid': lambda f: P.plain(f, '#a3203a', 0.08),
                'wick': lambda f: P.plain(f, '#5b4a35', 0.1)}


# ---------------------------------------------------------------- the list, and writing them

class Throwable:
    def __init__(self, key, build, look, seed, cm):
        """cm: its longest size in real life, for the display sizes."""
        self.key, self.build, self.look, self.seed, self.cm = key, build, look, seed, cm


THROWABLES = [
    Throwable('frag_grenade', frag_grenade, FRAG_LOOK, 1101, 11.0),
    Throwable('molotov', molotov, MOLOTOV_LOOK, 1102, 34.0),
    Throwable('glass_bottle', glass_bottle, BOTTLE_LOOK, 1103, 23.0),
    Throwable('brick', brick, BRICK_LOOK, 1104, 20.3),
    Throwable('smoke_grenade', smoke_grenade, SMOKE_LOOK, 1105, 15.0),
    Throwable('flashbang', flashbang, FLASH_LOOK, 1106, 13.3),
    Throwable('road_flare', road_flare, FLARE_LOOK, 1107, 30.0),
    Throwable('firecrackers', firecrackers, CRACKER_LOOK, 1108, 18.0),
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
