"""Wave 5 decor models, batch 6: collectibles and oddities (wave 4 stage 5; specs in decor.py). Built from the photos in
refs/items/decor/<id>/ (lucky_cat_statue, ship_in_a_bottle and meteorite_fragment hold lucky_cat's, ship_in_bottle's
and meteorite's). The armour photos are a lacquered samurai suit on its chest, so that is the suit built here.

Every player, team, hero and maker here is made up. Small pieces run a little over the furniture's 0.4 units an inch
so they read on a shelf.
"""
import numpy as np

import art as A
import paint as P
from wave4 import blob, flat, flat_arc
from wave5games import face_paint, put, text
from wave5wall import TEAMS

# ================================================================== painters


def base_word(word, colour, ink):
    def draw(u, v, W, H, c, f):
        return text(c, u, v, word, W / 2, H / 2, A.fit_text(word, W * 0.86, H * 0.6), ink)
    return face_paint(draw, colour, ('front',), 0.03)


def globe_paint(ocean, land):
    """Continents by where they sit on the ball (model space), with the lines of latitude."""
    def fn(f):
        p = f.P
        c = A.plain(f, ocean, 0.03)
        q = (np.sin(p[..., 0] * 0.9 + 1.0) + np.sin(p[..., 1] * 1.3 + p[..., 2] * 0.7) + np.sin(p[..., 2] * 1.1 - 2.0)
             + 0.4 * np.sin(p[..., 0] * 2.7 + p[..., 1] * 2.1))
        c = put(c, q > 0.9, land)
        return put(c, (np.abs((p[..., 1] * 1.5) % 1.0 - 0.5) < 0.03), A.hexof(A.tone(ocean, 0.75)))
    return fn


def fossil_slab(kind):
    """The split stone with the fossil standing out darker: a fish with its fins and ribs, or a shrimp."""
    def draw(u, v, W, H, c, f):
        x, y = (u - W / 2) / W, (v - H / 2) / H
        ink = '#6a4a2a'
        if kind == 'fish':
            body = (x / 0.32) ** 2 + (y / 0.14) ** 2 < 1
            ribs = body & ((x * 40) % 1 < 0.3)
            tail = (x > 0.3) & (x < 0.42) & (np.abs(y) < (x - 0.28) * 1.6)
            c = put(c, body | tail, A.hexof(A.tone(ink, 1.2)))
            c = put(c, ribs, ink)
            return put(c, np.hypot(x + 0.24, y + 0.02) < 0.025, '#2a1a0a')
        a = np.arctan2(y, x)                                  # an ammonite coil in the stone
        r = np.hypot(x, y * W / H)
        coil = np.cos((r * 30 - a / np.pi * 2.4)) > 0.3
        c = put(c, (r < 0.3) & coil, ink)
        return put(c, (r < 0.3) & ((a * 12) % 1 < 0.2), '#4a2a1a')
    return face_paint(draw, '#d8c8a8', ('front',), 0.14)


def urchin(f):
    """A fossil sea urchin: the five petals and the bumps on the domed stone."""
    p = f.P
    a = np.arctan2(p[..., 2], p[..., 0])
    r = np.hypot(p[..., 0], p[..., 2])
    c = A.plain(f, '#a8987a', 0.12)
    return put(c, (np.cos(a * 5) > 0.82) & (r < 1.8) & (p[..., 1] > 1.0), '#6a5a42')


def rock(dark, light):
    def fn(f):
        c = A.plain(f, dark, 0.25)
        n = f.noise(6.0, 17, 3)
        return put(c, n > 0.72, light)
    return fn


# ================================================================== the pieces


def bobblehead(m, k=0):
    """A bobblehead as in the photos: the round base with the name on it, the little body in its uniform, the
    oversized head on the spring with its cap and grin, a bat in its hands."""
    m.rod(0.0, 0.7, 1.6, 'stand')
    m.box([-1.0, 0.1, 1.2], [1.0, 0.6, 1.62], 'plate')
    for s in (-1, 1):
        m.rod(0.7, 2.4, 0.32, 'pants', cx=s * 0.4)
        m.box([s * 0.4 - 0.3, 0.7, -0.3], [s * 0.4 + 0.3, 1.0, 0.5], 'shoe')
    blob(m, (0.0, 3.2, 0.0), (0.9, 1.0, 0.6), 'shirt')
    for s in (-1, 1):
        m.seg((s * 0.8, 3.6), (s * 0.5, 2.6), 0.36, 0.36, 'shirt', z=0.4)
    m.seg((0.3, 2.6), (1.4, 4.6), 0.18, 0.18, 'bat', z=0.6)
    blob(m, (0.0, 5.0, 0.0), (1.2, 1.3, 1.2), 'skin')
    blob(m, (0.0, 5.7, 0.0), (1.25, 0.8, 1.25), 'cap')
    m.box([-0.8, 5.5, 0.6], [0.8, 5.65, 1.9], 'cap')
    for s in (-1, 1):
        m.box([s * 0.4 - 0.15, 5.0, 1.1], [s * 0.4 + 0.15, 5.35, 1.3], 'dark')
    m.box([-0.45, 4.35, 1.0], [0.45, 4.55, 1.22], 'teeth')


def mannequin(m, k=0):
    """A shop mannequin: the dress form on its turned post and tripod foot, or the full white figure standing on its
    glass plate with the rod up through one heel."""
    if k % 2 == 0:
        for a in (0, 120, 240):
            r = np.radians(a)
            flat(m, (0.0, 0.0), (np.cos(r) * 4.0, np.sin(r) * 4.0), 0.5, 0.5, 'stand')
        m.rod(0.0, 14.0, 0.35, 'stand')
        m.rod(0.0, 1.0, 0.8, 'stand')
        blob(m, (0.0, 15.6, 0.0), (3.2, 2.6, 2.3), 'cloth')
        blob(m, (0.0, 19.4, 0.0), (2.5, 2.6, 2.0), 'cloth')
        blob(m, (0.0, 23.0, 0.3), (3.3, 2.4, 2.3), 'cloth')
        m.rod(25.0, 26.4, 0.8, 'cloth')
        m.rod(26.4, 27.0, 1.0, 'stand')
        return
    m.rod(0.0, 0.3, 4.0, 'plate')
    for s in (-1, 1):
        m.rod(0.3, 12.0, 0.75, 'skin', cx=s * 1.0)
        m.box([s * 1.0 - 0.6, 0.3, -0.6], [s * 1.0 + 0.6, 0.9, 1.6], 'skin')
    blob(m, (0.0, 12.8, 0.0), (2.2, 1.6, 1.3), 'skin')
    blob(m, (0.0, 16.4, 0.0), (2.6, 3.4, 1.5), 'skin')
    for s in (-1, 1):
        blob(m, (s * 2.7, 19.0, 0.0), 0.9, 'skin')
        m.seg((s * 3.0, 19.0), (s * 3.6, 12.2), 0.8, 0.8, 'skin', z=0.0)
        blob(m, (s * 3.7, 11.6, 0.2), (0.4, 0.8, 0.5), 'skin')
    m.rod(19.6, 20.8, 0.6, 'skin')
    blob(m, (0.0, 22.2, 0.2), (1.1, 1.5, 1.2), 'skin')


def cardboard_cutout(m, k=0):
    """A life-size cardboard standee of a made-up hero, cut round the figure, propped on its fold-out leg."""
    t = 0.12
    z = 0.0
    hero = k % 5
    for s in (-1, 1):
        m.box([s * 1.1 - 0.8, 0.0, z], [s * 1.1 + 0.8, 1.6, z + t], 'boot')
        m.box([s * 1.1 - 0.75, 1.6, z], [s * 1.1 + 0.75, 12.0, z + t], 'legs')
    m.box([-2.6, 12.0, z], [2.6, 21.0, z + t], 'suit')
    m.box([-2.2, 12.0, z + t], [2.2, 13.0, z + t + 0.02], 'belt')
    for s in (-1, 1):
        m.seg((s * 2.9, 20.4), (s * (4.6 if hero != 2 else 3.4), 13.6), 1.4, t, 'suit', z=z + t / 2)
        blob(m, (s * (4.6 if hero != 2 else 3.4), 13.0, z + t / 2), (0.7, 0.8, t), 'skin')
    if hero in (0, 3):                                                    # the cape
        m.box([-3.0, 8.0, z - 0.02], [3.0, 21.0, z], 'cape')
    m.box([-0.7, 21.0, z], [0.7, 22.0, z + t], 'skin')
    blob(m, (0.0, 23.6, z + t / 2), (1.6, 2.0, t), 'skin')
    blob(m, (0.0, 24.8, z + t / 2), (1.7, 1.0, t), 'hair')
    m.box([-1.6, 23.4, z + t], [1.6, 24.0, z + t + 0.02], 'mask' if hero != 1 else 'skin')
    m.box([-2.2, 4.0, z - 0.02], [2.2, 16.0, z], 'card')
    m.seg((0.0, 14.0), (0.0, 0.0), 1.6, 0.1, 'card', z=-3.0)               # the easel leg
    flat(m, (0.0, -0.02), (0.0, -3.0), 1.6, 0.1, 'card', y0=0.0)


def lucky_cat(m, k=0):
    """A beckoning cat on its red cushion: the round body, the big head with its ears, the raised paw, the red
    collar with the bell and the gold coin held to its chest."""
    m.box([-2.2, 0.0, -1.8], [2.2, 0.6, 1.8], 'cushion')
    blob(m, (0.0, 2.2, 0.0), (1.9, 1.7, 1.5), 'fur')
    blob(m, (0.0, 4.6, 0.1), (1.8, 1.5, 1.4), 'fur')
    for s in (-1, 1):
        m.seg((s * 1.0, 5.7), (s * 1.25, 6.4), 0.6, 0.5, 'fur', z=0.0)
        m.box([s * 1.15 - 0.15, 5.85, 0.25], [s * 1.15 + 0.15, 6.2, 0.3], 'ear')
        m.box([s * 0.65 - 0.25, 4.7, 1.35], [s * 0.65 + 0.25, 4.85, 1.5], 'dark')
    m.box([-0.15, 4.2, 1.45], [0.15, 4.4, 1.55], 'ear')
    m.seg((1.6, 3.4), (2.2, 5.6), 0.8, 0.8, 'fur', z=0.4)                    # the beckoning paw
    blob(m, (2.2, 5.9, 0.4), 0.5, 'fur')
    m.box([-1.4, 3.3, -0.4], [1.4, 3.6, 1.3], 'collar')
    blob(m, (0.0, 3.1, 1.3), 0.3, 'coin')
    m.rod(1.4, 1.6, 0.9, 'coin', cx=0.0, cz=2.2, axis=2)
    for s in (-1, 1):
        blob(m, (s * 0.9, 0.9, 1.1), (0.5, 0.4, 0.5), 'fur')


def garden_gnome(m, k=0):
    """A garden gnome as in the photos: the boots, the tunic and belt, the white beard to his belly, the round nose
    and cheeks, the tall pointed hat, and in his hands a lantern, a spade or his pipe."""
    for s in (-1, 1):
        m.box([s * 0.7 - 0.5, 0.0, -0.4], [s * 0.7 + 0.5, 0.7, 1.0], 'boot')
        m.rod(0.7, 2.0, 0.5, 'pants', cx=s * 0.7)
    for q, (y, r) in enumerate(((2.0, 1.8), (2.8, 1.7), (3.6, 1.5), (4.4, 1.3))):
        m.rod(y, y + 0.8, r, 'tunic')
    m.rod(2.6, 3.0, 1.78, 'belt')
    blob(m, (0.0, 4.0, 0.9), (1.0, 1.6, 0.6), 'beard')
    blob(m, (0.0, 5.6, 0.2), (1.1, 1.0, 1.0), 'skin')
    blob(m, (0.0, 5.5, 1.1), 0.35, 'nose')
    blob(m, (0.0, 5.2, 0.9), (0.9, 0.6, 0.4), 'beard')
    for q in range(12):
        m.rod(6.0 + q * 0.25, 6.3 + q * 0.25, 1.15 - q * 0.09, 'hat', cz=-q * 0.03)
    for s in (-1, 1):
        m.seg((s * 1.4, 4.8), (s * 1.0, 3.4), 0.5, 0.5, 'tunic', z=0.6)
    kind = k % 3
    if kind == 0:
        m.box([0.6, 2.2, 1.2], [1.4, 3.4, 2.0], 'lamp')
        m.box([0.7, 2.4, 1.3], [1.3, 3.2, 2.05], 'glow')
    elif kind == 1:
        m.seg((1.4, 0.0), (1.2, 4.6), 0.2, 0.2, 'wood', z=1.2)
        m.box([1.0, 0.0, 1.0], [1.8, 1.0, 1.4], 'metal')
    else:
        m.seg((0.2, 5.0), (1.0, 4.4), 0.15, 0.15, 'wood', z=1.2)
        m.rod(4.4, 4.9, 0.25, 'wood', cx=1.0, cz=1.2)


def ship_in_bottle(m, k=0):
    """A ship in a bottle on its wooden cradle: the bottle lying down with its cork, the sea of blue putty and the
    tall ship on it with her hull, masts, sails and rigging. The glass is the back of the bottle, so the ship shows."""
    L, R = 8.0, 2.2
    for x in (-2.4, 2.4):
        m.box([x - 0.4, 0.0, -1.6], [x + 0.4, 1.4, 1.6], 'cradle')
    m.box([-3.2, 0.0, -1.0], [3.2, 0.4, 1.0], 'cradle')
    cy = 1.0 + R
    m.box([-L / 2, cy - R, -R], [L / 2, cy - R + 0.12, R], 'glass')
    m.box([-L / 2, cy + R - 0.12, -0.4], [L / 2, cy + R, 0.4], 'glass')
    m.rod(-L / 2 - 0.2, -L / 2, R, 'glass', cx=cy, cz=0.0, axis=0)
    for x0, x1, r in ((L / 2, L / 2 + 1.0, R * 0.8), (L / 2 + 1.0, L / 2 + 2.4, 0.6), (L / 2 + 2.4, L / 2 + 3.2, 0.55)):
        m.rod(x0, x1, r, 'glass' if x1 < L / 2 + 3 else 'cork', cx=cy if r > 1 else cy + 0.6, cz=0.0, axis=0)
    m.box([-3.4, cy - R + 0.1, -1.4], [3.4, cy - R + 0.6, 1.4], 'sea')
    y = cy - R + 0.6
    m.box([-2.2, y, -0.45], [2.4, y + 0.7, 0.45], 'hull')
    m.box([-2.4, y + 0.5, -0.4], [-1.6, y + 1.1, 0.4], 'hull')
    for x, h in ((-1.2, 2.6), (0.2, 3.0), (1.6, 2.4)):
        m.rod(y + 0.7, y + 0.7 + h, 0.06, 'wood', cx=x)
        for q, w in enumerate((0.9, 0.75, 0.6)):
            m.box([x - w / 2, y + 1.0 + q * 0.8, -0.03], [x + w / 2, y + 1.6 + q * 0.8, 0.03], 'sail')
    m.seg((2.4, y + 0.6), (3.4, y + 1.2), 0.06, 0.06, 'wood', z=0.0)
    m.seg((3.4, y + 1.2), (1.6, y + 3.1), 0.03, 0.03, 'rope', z=0.0)


def desk_globe(m, k=0):
    """A desk globe as in the photos: the ball on its tilted axis inside the half meridian ring, the turned post and
    the round foot."""
    m.rod(0.0, 0.5, 2.6, 'stand')
    m.rod(0.5, 2.2, 0.35, 'stand')
    m.rod(2.2, 2.5, 0.8, 'stand')
    cy = 6.4
    blob(m, (0.0, cy, 0.0), (3.6, 3.6, 3.6), 'sea')
    m.arc((0.0, cy), 3.9, 200, 380, 0.3, 0.3, 'metal', z=0.0)
    m.seg((-1.4, cy - 3.6), (1.4, cy + 3.6), 0.15, 0.15, 'metal', z=0.0)
    m.seg((0.0, 2.5), (0.0, cy - 3.9), 0.4, 0.3, 'metal', z=0.0)


def gumball_machine(m, k=0):
    """A gumball machine on its stand as in the photos: the cast iron head with the coin mechanism and chute, the
    glass globe full of coloured gumballs and its cap, on a post with a round foot."""
    m.rod(0.0, 0.5, 3.0, 'iron')
    m.rod(0.5, 10.0, 0.5, 'iron')
    m.rod(10.0, 10.6, 1.6, 'iron')
    m.box([-2.0, 10.6, -2.0], [2.0, 14.0, 2.0], 'iron')
    m.box([-1.2, 11.2, 2.0], [1.2, 13.4, 2.2], 'metal')
    m.rod(2.2, 2.6, 0.5, 'metal', cx=0.0, cz=12.6, axis=2)
    m.box([-0.5, 10.8, 2.0], [0.5, 11.4, 2.6], 'metal')
    m.rod(14.0, 14.4, 2.2, 'iron')
    rng = np.random.default_rng(k + 3)
    cy, R = 17.0, 2.8
    blob(m, (0.0, cy, -0.6), (R, R, R * 0.7), 'glass')
    for q in range(46):
        p = rng.normal(size=3)
        p = p / np.linalg.norm(p) * R * rng.random() ** 0.3 * 0.86
        if p[1] < 2.0:
            blob(m, (p[0], cy + p[1], p[2] * 0.8 + 0.5), 0.45, f'gum{q % 6}')
    m.rod(cy + R - 0.2, cy + R + 0.4, 1.6, 'iron')
    m.rod(cy + R + 0.4, cy + R + 0.8, 0.5, 'iron')


def model_train_set(m, k=0):
    """A model railway on its board as in the photos: the green baseboard with a hill, the oval of track with its
    sleepers, a locomotive with its train of cars, a little station house and trees."""
    W, D, H = 22.0, 12.0, 1.2
    m.box([-W / 2, 0.0, -D / 2], [W / 2, H, D / 2], 'board')
    m.box([-W / 2, H, -D / 2], [W / 2, H + 0.1, D / 2], 'grass')
    blob(m, (-6.0, H, -3.0), (4.0, 2.4, 2.6), 'grass')
    for x0, x1 in ((-5.0, 5.0),):
        for z in (-3.6, 3.6):
            for q in range(14):
                x = x0 + q * (x1 - x0) / 13
                m.box([x - 0.12, H + 0.1, z - 0.7], [x + 0.12, H + 0.2, z + 0.7], 'sleeper')
            for dz in (-0.4, 0.4):
                m.box([x0, H + 0.2, z + dz - 0.06], [x1, H + 0.32, z + dz + 0.06], 'rail')
    for s in (-1, 1):
        flat_arc(m, (s * 5.0, 0.0), 3.6, -90, 90, 1.4, 0.1, 'sleeper', y0=H + 0.1, steps=7) if s > 0 else \
            flat_arc(m, (s * 5.0, 0.0), 3.6, 90, 270, 1.4, 0.1, 'sleeper', y0=H + 0.1, steps=7)
    z = 3.6
    y = H + 0.32
    m.box([0.0, y, z - 0.5], [2.6, y + 1.2, z + 0.5], 'loco')
    m.box([1.8, y + 1.2, z - 0.5], [2.6, y + 2.0, z + 0.5], 'loco')
    m.rod(0.2, 1.8, 0.45, 'loco', cx=y + 1.0, cz=z, axis=0)
    m.rod(y + 1.4, y + 2.0, 0.2, 'dark', cx=0.5, cz=z)
    m.box([-0.1, y + 0.6, z - 0.3], [0.0, y + 1.0, z + 0.3], 'glow')
    for q, mat in enumerate(('car1', 'car2', 'car1')):
        x = 2.9 + q * 2.4
        m.box([x, y + 0.1, z - 0.5], [x + 2.2, y + 1.3, z + 0.5], mat)
        m.box([x - 0.1, y + 1.3, z - 0.55], [x + 2.3, y + 1.45, z + 0.55], 'dark')
    m.box([1.0, H + 0.1, -1.6], [4.0, H + 2.0, 0.6], 'house')
    m.box([0.8, H + 2.0, -1.8], [4.2, H + 2.4, 0.8], 'roof')
    m.box([2.2, H + 0.1, 0.6], [2.8, H + 1.2, 0.62], 'dark')
    for x, zt in ((-8.0, 4.0), (8.6, -4.2), (-2.0, -4.8)):
        m.rod(H, H + 0.8, 0.15, 'wood', cx=x, cz=zt)
        blob(m, (x, H + 1.8, zt), (0.8, 1.2, 0.8), 'tree')


def diving_helmet(m, k=0):
    """A copper diving helmet like the photos on its wooden stand: the domed bonnet, the round front window with
    its guard bars, the two side windows, the brass breastplate bolted on, the air fittings at the back."""
    m.box([-4.0, 0.0, -3.6], [4.0, 1.0, 3.6], 'stand')
    blob(m, (0.0, 2.6, 0.0), (5.0, 1.8, 4.4), 'brass')
    m.rod(1.0, 1.4, 4.6, 'brass')
    for a in range(0, 360, 45):
        r = np.radians(a)
        blob(m, (np.cos(r) * 4.2, 1.8, np.sin(r) * 3.6), 0.3, 'bolt')
    m.rod(3.2, 4.6, 2.8, 'copper')
    blob(m, (0.0, 7.4, 0.0), (3.6, 3.8, 3.6), 'copper')
    m.rod(3.4, 4.0, 1.6, 'brass', cx=0.0, cz=7.4, axis=2)
    m.rod(3.6, 4.05, 1.2, 'window', cx=0.0, cz=7.4, axis=2)
    for dy in (-0.5, 0.0, 0.5):
        m.box([-1.3, 7.4 + dy - 0.06, 4.0], [1.3, 7.4 + dy + 0.06, 4.15], 'brass')
    for s in (-1, 1):
        m.rod(s * 3.2, s * 3.8, 1.1, 'brass', cx=7.6, cz=0.6, axis=0)
        m.rod(s * 3.5, s * 3.85, 0.8, 'window', cx=7.6, cz=0.6, axis=0)
    m.rod(10.8, 11.6, 0.9, 'brass')
    m.rod(-4.2, -3.2, 0.5, 'brass', cx=0.0, cz=8.0, axis=2)


def fossil(m, k=0):
    """A fossil on show: the split slab of stone with a fish or an ammonite in it, propped on a little stand, or
    a fossil sea urchin on its square plinth with a label."""
    kind = k % 3
    if kind == 2:
        m.box([-2.6, 0.0, -2.6], [2.6, 1.0, 2.6], 'plinth')
        m.box([-1.4, 0.2, 2.6], [1.4, 0.8, 2.65], 'plate')
        blob(m, (0.0, 1.9, 0.0), (2.2, 1.2, 2.2), 'urchin')
        return
    m.box([-4.0, 0.0, -1.6], [4.0, 0.5, 1.6], 'plinth')
    m.box([-3.6, 0.5, -0.8], [3.6, 6.0, -0.2], 'slab', rot=dict(axis='x', angle=-22.5, origin=[0, 0.5, -0.5]))
    m.box([-1.0, 0.5, -2.8], [1.0, 3.0, -2.4], 'plinth')


def carousel_horse(m, k=0):
    """A carousel horse on its brass pole as in the photos: the leaping body, the arched neck and the carved head
    with its bridle, the flying mane and tail, the tucked front legs and stretched hind ones, the saddle and blanket
    and the stirrup, and the round base the pole stands in."""
    m.rod(0.0, 1.0, 4.0, 'plinth')
    m.rod(1.0, 30.0, 0.35, 'brass')
    blob(m, (0.0, 30.0, 0.0), 0.7, 'brass')
    cy = 14.0
    blob(m, (0.0, cy, 0.0), (7.0, 2.8, 2.2), 'coat')
    m.seg((5.0, cy + 1.0), (8.4, cy + 6.0), 2.6, 2.0, 'coat', z=0.0)
    blob(m, (9.6, cy + 6.4, 0.0), (2.4, 1.2, 1.1), 'coat')
    m.seg((9.0, cy + 6.6), (11.6, cy + 5.6), 1.0, 1.0, 'coat', z=0.0)
    m.box([8.6, cy + 7.0, -0.4], [9.2, cy + 7.8, 0.4], 'coat')
    m.seg((5.6, cy + 3.4), (8.0, cy + 7.6), 0.8, 2.4, 'mane', z=0.0)
    m.seg((10.0, cy + 6.0), (11.2, cy + 5.6), 0.2, 2.3, 'bridle', z=0.0)
    m.seg((-6.6, cy + 0.6), (-10.0, cy - 3.0), 1.0, 1.2, 'mane', z=0.0)
    for s in (-1, 1):
        m.seg((5.0, cy - 1.6), (7.6, cy - 3.6), 0.8, 0.8, 'coat', z=s * 1.1)
        m.seg((7.6, cy - 3.6), (6.4, cy - 6.6), 0.7, 0.7, 'coat', z=s * 1.1)
        m.seg((-5.0, cy - 1.2), (-8.6, cy - 5.6), 0.9, 0.9, 'coat', z=s * 1.1)
        m.seg((-8.6, cy - 5.6), (-11.0, cy - 6.6), 0.7, 0.7, 'coat', z=s * 1.1)
        m.box([-1.0, cy - 3.6, s * 2.2 - 0.1], [1.0, cy + 2.0, s * 2.2 + 0.1], 'blanket')
        m.seg((0.0, cy - 1.0), (0.0, cy - 4.2), 0.08, 0.08, 'brass', z=s * 2.4)
        m.box([-0.4, cy - 4.4, s * 2.4 - 0.2], [0.4, cy - 4.2, s * 2.4 + 0.2], 'brass')
    blob(m, (0.0, cy + 2.6, 0.0), (2.4, 0.7, 2.0), 'saddle')
    m.box([-0.4, cy + 2.8, -0.5], [0.4, cy + 4.0, 0.5], 'saddle')


def suit_of_armour(m, k=0):
    """A lacquered samurai armour on its storage chest, as in the photos: the chest, the laced skirt of plates, the
    body plate, the big square shoulder guards, the sleeves, the face mask, the bowl helmet with its flaring neck
    guard and turned-back wings, and the gold horn crest."""
    m.box([-5.0, 0.0, -3.6], [5.0, 6.0, 3.6], 'chest')
    m.box([-1.6, 3.0, 3.6], [1.6, 4.6, 3.7], 'gold')
    m.rod(6.0, 12.0, 0.4, 'chest')
    for q in range(4):                                                  # the skirt, row on row
        y = 7.0 + q * 1.3
        m.box([-4.0 + q * 0.2, y, -2.6], [4.0 - q * 0.2, y + 1.5, 2.6], 'plate' if q % 2 else 'lace')
    m.box([-3.6, 12.2, -2.4], [3.6, 19.0, 2.4], 'plate')
    m.box([-3.0, 13.0, 2.4], [3.0, 18.4, 2.6], 'lace')
    for s in (-1, 1):
        m.box([s * 3.6, 14.6, -2.0] if s > 0 else [-6.6, 14.6, -2.0], [6.6, 19.2, 2.0] if s > 0 else [-3.6, 19.2, 2.0],
              'lace')
        m.seg((s * 5.6, 15.0), (s * 6.0, 9.6), 1.6, 1.6, 'sleeve', z=0.6)
        m.box([s * 6.0 - 0.9, 8.6, -0.2], [s * 6.0 + 0.9, 9.8, 1.4], 'plate')
    m.box([-1.4, 19.0, -1.4], [1.4, 20.6, 1.6], 'plate')
    m.box([-1.6, 20.0, 1.6], [1.6, 21.6, 2.4], 'mask')
    m.box([-1.0, 20.6, 2.4], [1.0, 20.9, 2.5], 'gold')
    blob(m, (0.0, 23.0, 0.0), (2.4, 1.8, 2.4), 'helmet')
    for q in range(3):
        m.rod(19.6 + q * 0.6, 20.2 + q * 0.6, 3.6 - q * 0.6, 'lace' if q % 2 else 'plate', cz=-0.4)
    for s in (-1, 1):
        m.box([s * 2.4 - 0.6, 21.4, 0.8], [s * 2.4 + 0.6, 22.8, 1.4], 'gold')
        m.seg((s * 0.6, 23.6), (s * 3.0, 29.4), 0.4, 0.15, 'gold', z=2.2)
    m.box([-0.6, 23.0, 2.2], [0.6, 24.2, 2.5], 'gold')


def meteorite(m, k=0):
    """A meteorite on its plinth: the lumpy black fusion crust, one cut and polished face showing the bright metal
    flecked through the stone, and the brass label."""
    m.box([-3.0, 0.0, -3.0], [3.0, 2.0, 3.0], 'plinth')
    m.box([-1.6, 0.6, 3.0], [1.6, 1.4, 3.05], 'plate')
    blob(m, (0.0, 3.8, 0.0), (2.4, 1.9, 2.0), 'crust')
    blob(m, (0.9, 4.4, -0.6), (1.6, 1.4, 1.5), 'crust')
    blob(m, (-1.0, 3.4, 0.5), (1.4, 1.2, 1.4), 'crust')
    m.box([-1.4, 2.8, 1.7], [1.2, 4.8, 1.95], 'cut')


# ================================================================== looks


def _looks():
    paint = lambda c: (lambda f: P.paint(f, c, steel_base=0.4, chips=0.3))      # noqa: E731
    wood = lambda a, b: (lambda f: P.wood(f, a, b))                              # noqa: E731
    dark = A.flat('#141414')
    skin = ['#e8b890', '#c88a5a', '#8a5a3a', '#f0c8a0', '#a86a44']
    out = {}
    out['bobblehead'] = [dict(b=bobblehead, stand=A.flat(sh), plate=base_word(nm, sh, ink), shirt=A.flat(sh),
                              pants=A.flat('#f0f0f0' if q % 2 == 0 else '#8a8a84'), shoe=dark, bat=wood('#c89860', '#8a6040'),
                              skin=A.flat(skin[q % 5]), cap=A.flat(tr if tr != '#f0f0f0' else ink), dark=dark,
                              teeth=A.flat('#f8f8f8'), _px={'plate': 12})
                         for q, (nm, num, sh, tr, ink) in enumerate(TEAMS)]
    out['mannequin'] = [dict(b=mannequin, stand=wood('#3a2414', '#1a0a04') if q != 2 else A.chrome,
                             cloth=A.fabric(c), skin=A.plastic(c), plate=A.flat('#a8c0c0', 0.02))
                        for q, c in enumerate(('#e8dcc0', '#f0f0f0', '#141414', '#c8c4bc'))]
    HEROES = [('#1e3a8a', '#c8202a', '#f0c020', '#2a2a2a'), ('#c8a040', '#6a4a2a', '#3a2a1a', '#3a2a1a'),
              ('#f0f0f0', '#c8ccd0', '#2a2a2a', '#40c0f0'), ('#141414', '#3a3a3a', '#f0c020', '#141414'),
              ('#2a8a3a', '#e8e040', '#e8e040', '#141414'), ('#c8202a', '#141414', '#f0f0f0', '#f0c020'),
              ('#6a2a8a', '#2a2a2a', '#e8e040', '#e86a1a'), ('#e86a1a', '#2a2a5a', '#f0f0f0', '#141414'),
              ('#40c0c0', '#f0f0f0', '#c8202a', '#e8e4da'), ('#8a8a84', '#c8202a', '#141414', '#3a2a1a')]
    out['cardboard_cutout'] = [dict(b=cardboard_cutout, suit=A.flat(s), legs=A.flat(lg), boot=A.flat(bt), belt=A.flat(bt),
                                    cape=A.flat('#c8202a' if q % 2 == 0 else '#f0c020'), skin=A.flat(skin[q % 5]),
                                    hair=A.flat(hr), mask=A.flat(s), card=A.cardboard)
                               for q, (s, lg, bt, hr) in enumerate(HEROES)]
    out['lucky_cat'] = [dict(b=lucky_cat, fur=A.plastic(fu), ear=A.flat('#e85a6a'), dark=dark, collar=A.flat('#c8202a'),
                             coin=P.brass, cushion=A.fabric('#c8202a' if q != 1 else '#6a2a8a'))
                        for q, fu in enumerate(('#f8f6f0', '#e8c020', '#141414'))]
    GN = [('#c8202a', '#2a5a2a', '#3a5a8a'), ('#2a8a3a', '#c8202a', '#5a3a1a'), ('#2a4ae8', '#e8c020', '#3a3a3a'),
          ('#c8202a', '#3a5a8a', '#6a4a2a'), ('#e86a1a', '#2a8a3a', '#2a2a5a'), ('#6a2a8a', '#c8202a', '#3a5a2a')]
    out['garden_gnome'] = [dict(b=garden_gnome, hat=paint(h), tunic=paint(t), pants=paint(p_), boot=paint('#3a2414'),
                                belt=paint('#141414'), beard=paint('#f0ece0'), skin=paint('#f0b890'), nose=paint('#e88a7a'),
                                lamp=paint('#2a2a2a'), glow=A.flat('#f0c060', 0.02), wood=wood('#8a6040', '#5a3a1a'),
                                metal=A.steel)
                           for h, t, p_ in GN]
    out['ship_in_bottle'] = [dict(b=ship_in_bottle, cradle=wood('#6a3a1e', '#3a1a0a'), glass=A.flat(g, 0.02),
                                  cork=A.flat('#b8946a', 0.2), sea=A.flat('#2a5a8a', 0.1), hull=paint(hl),
                                  wood=wood('#8a6040', '#5a3a1a'), sail=A.fabric('#f0ece0'), rope=A.flat('#c8b080'))
                             for g, hl in (('#a8d0c8', '#3a1a0a'), ('#b8d0e0', '#141414'), ('#8ab89a', '#7a1a1a'))]
    out['desk_globe'] = [dict(b=desk_globe, sea=globe_paint(o, l), stand=st, metal=mt)
                         for o, l, st, mt in (('#5a8ac0', '#e8d8a0', wood('#3a2414', '#1a0a04'), P.brass),
                                              ('#2a3a5a', '#c8a060', wood('#5a3418', '#3a1a0a'), P.brass),
                                              ('#c8d8c0', '#8a6a44', A.plastic('#141414'), A.chrome))]
    GUM = ['#e8202a', '#f0c020', '#2a8ae8', '#40c040', '#f070b0', '#f0f0f0']
    out['gumball_machine'] = []
    for c in ('#c8202a', '#1e3a8a', '#c8c8cc', '#2a8a3a'):
        d = dict(b=gumball_machine, iron=paint(c), metal=A.chrome, glass=A.flat('#d0e0e8', 0.02))
        for q, g in enumerate(GUM):
            d[f'gum{q}'] = A.plastic(g)
        out['gumball_machine'].append(d)
    out['model_train_set'] = [dict(b=model_train_set, board=wood('#8a6040', '#5a3a1a'), grass=A.flat('#4a7a3a', 0.2),
                                   sleeper=A.flat('#4a3a2a'), rail=A.steel, loco=paint(lc), car1=paint(c1),
                                   car2=paint(c2), dark=dark, glow=A.flat('#f0e080', 0.02), house=paint('#e8dcc0'),
                                   roof=paint('#8a2a1a'), wood=wood('#6a4a2a', '#3a2a1a'), tree=A.flat('#2a5a2a', 0.2))
                              for lc, c1, c2 in (('#141414', '#a8281f', '#2a4a8a'), ('#2a6a3a', '#e8c020', '#8a8a84'),
                                                 ('#1e3a8a', '#c8c8cc', '#c8202a'), ('#a8281f', '#e8dcc0', '#3a3a3a'))]
    out['diving_helmet'] = [dict(b=diving_helmet, stand=wood('#5a3418', '#3a1a0a'), brass=P.brass,
                                 copper=lambda f: P.paint(f, '#b8683a', steel_base=0.6, chips=0.5), bolt=P.brass,
                                 window=A.flat('#2a3a3a', 0.02)),
                            dict(b=diving_helmet, stand=wood('#3a1a0a', '#1a0a04'), brass=P.brass,
                                 copper=lambda f: P.paint(f, '#5a8a6a', steel_base=0.6, chips=0.6), bolt=P.brass,
                                 window=A.flat('#2a3a3a', 0.02))]
    out['fossil'] = [dict(b=fossil, plinth=wood('#3a2414', '#1a0a04'), slab=fossil_slab('fish'), plate=P.brass,
                          _px={'slab': 10}),
                     dict(b=fossil, plinth=wood('#5a3418', '#3a1a0a'), slab=fossil_slab('ammonite'), plate=P.brass,
                          _px={'slab': 10}),
                     dict(b=fossil, plinth=paint('#141414'), urchin=urchin, plate=P.brass)]
    out['carousel_horse'] = [dict(b=carousel_horse, plinth=paint('#2a2a2a'), brass=P.brass, coat=paint(ct),
                                  mane=paint(mn), bridle=paint('#c8202a'), blanket=paint(bl), saddle=paint(sd))
                             for ct, mn, bl, sd in (('#f0ece0', '#3a2a1a', '#c8202a', '#3a2414'),
                                                    ('#8a8a84', '#f0ece0', '#1e3a8a', '#141414'),
                                                    ('#2a2a2a', '#c8a040', '#2a8a3a', '#6a1a1a'))]
    out['suit_of_armour'] = [dict(b=suit_of_armour, chest=wood('#1a1008', '#0a0604'), plate=paint(pl), lace=A.fabric(lc),
                                  sleeve=A.fabric('#2a2a2a'), mask=paint(pl), helmet=paint(pl), gold=P.brass)
                             for pl, lc in (('#141414', '#1e3a6a'), ('#7a1a1a', '#2a2a2a'))]
    out['meteorite'] = [dict(b=meteorite, plinth=wood('#3a2414', '#1a0a04'), plate=P.brass, crust=rock('#1e1c1a', '#3a3632'),
                             cut=rock('#5a5a54', '#d8dcd8')),
                        dict(b=meteorite, plinth=paint('#141414'), plate=P.brass, crust=rock('#2a2420', '#5a4a3a'),
                             cut=rock('#8a7a6a', '#c8ccd0'))]
    for ls in out.values():
        for k, d in enumerate(ls):
            d['b'] = (lambda fn, k: (lambda m: fn(m, k)))(d['b'], k)
    return out


def looks(it, wrap):
    return {i: [wrap(it, d) for d in ls] for i, ls in _looks().items()}
