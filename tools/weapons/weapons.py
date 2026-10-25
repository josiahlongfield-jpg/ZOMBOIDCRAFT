"""Generates the weapon item models, textures, item definitions, lang lines and stats table.

    python3 tools/weapons/weapons.py [--preview DIR]

Each weapon is built from boxes in a local frame: the hand's grip at the origin, the weapon
running up +y, things that stick out sideways (axe blades, hammer claws, club heads) along +x
and thickness along z. The generator then moves it into item-model space and works out the
display transforms so the hand holds the grip in every view. Two textures per melee weapon:
fresh, and battered and bloodied for when its condition drops below half.

The assault rifle is modelled muzzle-forward (-z) with extra models for aiming down the
sights, the reload steps and the muzzle flash.

Writes:
  assets/zomboidcraft/models/item/weapon/*.json, textures/item/weapon/*.png, items/*.json
  src/main/java/com/zomboidcraft/combat/WeaponSpecs.java (generated stats)
  lang lines merged into en_us.json
"""
import json
import math
import sys
import zlib
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import model as M  # noqa: E402
import paint as P  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
JAVA = ROOT / 'src/main/java/com/zomboidcraft/combat/WeaponSpecs.java'
NS = 'zomboidcraft'

# Held size relative to vanilla's sword sprite. At 0.7 one model unit is about 3.2 cm.
K = 0.7
C = np.array([8.0, 8.0, 8.0])
V_GRIP = np.array([4.0, 4.0, 8.0])        # where a vanilla sword sprite is held
VANILLA = {  # handheld.json
    'firstperson_righthand': ([0, -90, 25], [1.13, 3.2, 1.13], 0.68),
    'thirdperson_righthand': ([0, -90, 55], [0, 4.0, 0.5], 0.85),
}


# ================================================================== looks


def look(wear, reach, blood=0.0, grime=0.5, scratch=1.0, **mats):
    d = dict(mats)
    brand = d.pop('_brand', None)
    d['_after'] = ([brand] if brand else []) + [
        lambda f, c: P.grime(f, c, grime * (0.4 + wear)),
        lambda f, c: P.scratches(f, c, scratch * (0.3 + wear)),
        lambda f, c: P.blood(f, c, blood * wear, reach),
    ]
    return d


def red_paint(colour='#a5150f', chips=0.6):
    return lambda f: P.rust(f, P.paint(f, colour, chips=chips), 0.25 * f.wear)


def rusty_steel(base=0.5, amount=0.35):
    return lambda f: P.rust(f, P.steel(f, base), amount * (0.5 + f.wear))


def bright_steel(base=0.72):
    return lambda f: P.steel(f, base)


def brand_oval(y0, y1, colour='#3b2414'):
    """A burnt-in maker's oval on the front of a bat barrel (no real brand)."""
    def fx(f, c):
        if f.n[2] < 0.5 and f.n[0] < 0.5:
            return c
        cy = (y0 + y1) / 2
        ry = (y1 - y0) / 2
        a, b = f.across()
        ax = a if f.n[2] > 0.5 else b
        r = np.sqrt((ax / 0.75) ** 2 + ((f.Y - cy) / ry) ** 2)
        ring = (r > 0.82) & (r < 1.0)
        bar = (np.abs(f.Y - cy) < ry * 0.18) & (np.abs(ax) < 0.5)
        m = ring | bar
        return np.where(m[..., None], P.mix(c, P.col(colour), 0.75), c)
    return fx


# ================================================================== melee roster

class Weapon:
    def __init__(self, name, title, kind, damage, speed, reach, knockback, durability, stamina, knockdown,
                 two_handed=False, sound='blunt', skill='BLUNT', notes=''):
        self.name, self.title, self.kind = name, title, kind
        self.damage, self.speed, self.reach = damage, speed, reach
        self.knockback, self.durability, self.stamina = knockback, durability, stamina
        self.knockdown, self.two_handed, self.sound, self.skill = knockdown, two_handed, sound, skill
        self.notes = notes


def bat_profile(m, mat, top=21.8):
    m.rod(-4.6, -4.0, 0.95, mat)
    prof = [(-4.0, 0.42), (3.5, 0.44), (6, 0.48), (8, 0.55), (10, 0.66), (12, 0.8), (14, 0.94), (16, 1.04),
            (18, 1.12), (top, 1.16)]
    for (y0, r0), (y1, r1) in zip(prof, prof[1:]):
        m.rod(y0, y1, (r0 + r1) / 2, mat)
    m.rod(top, top + 0.55, 1.06, mat)


def build_baseball_bat(m):
    bat_profile(m, 'wood')
    m.rod(-4.0, 3.5, 0.47, 'tape')


def build_spiked_bat(m):
    bat_profile(m, 'wood')
    m.rod(-4.0, 3.5, 0.47, 'tape')
    rng = np.random.default_rng(4)
    for i in range(12):
        y = 12.6 + i * 0.75
        r = 0.6 + (y - 10) * 0.05
        r = min(r, 1.1)
        d = i % 4
        L = 1.25 + rng.random() * 0.35
        s = 0.17
        if d == 0:
            m.box([r - 0.3, y, -s / 2], [r + L, y + s, s / 2], 'nail', rot=None)
        elif d == 1:
            m.box([-s / 2, y, r - 0.3], [s / 2, y + s, r + L], 'nail')
        elif d == 2:
            m.box([-r - L, y, -s / 2], [-r + 0.3, y + s, s / 2], 'nail')
        else:
            m.box([-s / 2, y, -r - L], [s / 2, y + s, -r + 0.3], 'nail')
        if i % 3 == 1:   # a few driven in at an angle
            m.parts[-1]['rot'] = dict(axis='y', angle=45 if i % 2 else -45, origin=[0, y, 0])


def build_crowbar(m):
    m.rod(-6.6, 12.5, 0.4, 'paint')
    m.seg((0, -6.4), (0.55, -8.3), 0.78, 0.5, 'bare')
    m.seg((0.55, -8.3), (0.8, -8.95), 0.62, 0.22, 'bare')
    m.arc((1.6, 12.5), 1.6, 180, 0, 0.82, 0.8, 'paint', steps=8)
    m.seg((3.2, 12.5), (3.35, 10.7), 0.86, 0.66, 'bare')
    for z in (-0.19, 0.19):
        m.seg((3.35, 10.7), (3.5, 9.85), 0.62, 0.26, 'bare', z=z)


def build_kitchen_knife(m):
    m.box([-0.45, -2.6, -0.3], [0.45, 2.0, 0.3], 'handle')
    m.box([-0.47, -2.55, -0.05], [0.47, 1.95, 0.05], 'steel')         # full tang shows round the edge
    for y in (-1.8, -0.3, 1.2):
        m.box([-0.12, y - 0.12, -0.33], [0.12, y + 0.12, 0.33], 'rivet')
    m.box([-0.5, 2.0, -0.22], [0.55, 2.35, 0.22], 'steel')
    for y0, y1, w in [(2.35, 5.0, 1.35), (5.0, 6.3, 1.22), (6.3, 7.2, 1.0), (7.2, 7.8, 0.74), (7.8, 8.3, 0.48),
                      (8.3, 8.6, 0.22)]:
        m.box([-0.45, y0, -0.06], [-0.45 + w, y1, 0.06], 'blade')


def build_machete(m):
    m.box([-0.5, -3.2, -0.36], [0.5, 2.8, 0.36], 'handle')
    for y in (-2.2, -0.6, 1.0):
        m.box([0.5, y, -0.3], [0.62, y + 0.6, 0.3], 'handle')
    m.box([-0.62, -3.5, -0.4], [0.62, -3.0, 0.4], 'handle')
    m.box([-0.6, 2.8, -0.38], [0.75, 3.1, 0.38], 'handle')
    for y in (-1.9, 0.0, 1.9):
        m.box([-0.13, y - 0.13, -0.39], [0.13, y + 0.13, 0.39], 'rivet')
    for y0, y1, x0, x1 in [(3.1, 8, -0.5, 0.95), (8, 12, -0.5, 1.15), (12, 14, -0.5, 1.35), (14, 14.8, -0.3, 1.38),
                           (14.8, 15.4, 0.0, 1.32), (15.4, 15.9, 0.35, 1.2), (15.9, 16.3, 0.65, 1.05)]:
        m.box([x0, y0, -0.07], [x1, y1, 0.07], 'blade')


def build_katana(m):
    m.box([-0.52, -5.6, -0.42], [0.52, -5.1, 0.42], 'iron')
    m.box([-0.48, -5.1, -0.38], [0.48, 3.9, 0.38], 'wrap')
    m.box([-0.52, 3.9, -0.42], [0.52, 4.2, 0.42], 'iron')
    for hx, hz in [(1.2, 0.75), (0.95, 1.0), (0.6, 1.15)]:
        m.box([-hx, 4.2, -hz], [hx, 4.5, hz], 'tsuba')
    m.box([-0.5, 4.5, -0.22], [0.55, 5.3, 0.22], 'brass')
    y = 5.3
    while y < 27.4:
        y1 = min(y + 2.2, 27.5)
        off = -0.0012 * ((y + y1) / 2 - 5.3) ** 2
        m.box([-0.44 + off, y, -0.09], [0.48 + off, y1, 0.09], 'blade')
        y = y1
    off = -0.0012 * (28.2 - 5.3) ** 2
    for y0, y1, w in [(27.5, 28.1, 0.82), (28.1, 28.6, 0.58), (28.6, 28.95, 0.3)]:
        m.box([-0.44 + off, y0, -0.08], [-0.44 + off + w, y1, 0.08], 'blade')


def build_fire_axe(m):
    m.rod(-4.6, -4.0, 0.56, 'wood')
    m.rod(-4.0, -2.0, 0.5, 'wood')
    m.rod(-2.0, 19.4, 0.42, 'wood')
    m.box([-0.75, 19.2, -0.62], [0.75, 22.6, 0.62], 'head')
    m.box([-0.4, 22.6, -0.33], [0.4, 22.72, 0.33], 'wood')
    m.box([-0.05, 22.6, -0.36], [0.05, 22.76, 0.36], 'steel')
    for x0, x1, y0, y1, t in [(0.75, 1.8, 19.3, 22.5, 1.0), (1.8, 2.8, 19.0, 22.8, 0.74), (2.8, 3.6, 18.6, 23.1, 0.5),
                              (3.6, 4.25, 18.2, 23.4, 0.28)]:
        m.box([x0, y0, -t / 2], [x1, y1, t / 2], 'head')
    for x0, x1, y0, y1, t in [(-1.8, -0.75, 20.0, 22.0, 0.8), (-2.8, -1.8, 20.3, 21.8, 0.6),
                              (-3.6, -2.8, 20.5, 21.5, 0.42)]:
        m.box([x0, y0, -t / 2], [x1, y1, t / 2], 'head')
    m.seg((-3.5, 21.0), (-4.35, 20.6), 0.5, 0.3, 'steel')


def build_sledgehammer(m):
    m.rod(-4.4, -4.0, 0.58, 'rubber')
    m.rod(-4.0, 3.0, 0.53, 'rubber')
    m.rod(3.0, 22.6, 0.46, 'glass')
    m.box([-0.6, 22.3, -0.56], [0.6, 22.8, 0.56], 'forged')
    m.box([-2.4, 22.8, -1.05], [2.4, 24.9, 1.05], 'forged')
    for s in (1, -1):
        m.box([min(s * 2.4, s * 2.72), 23.0, -0.86], [max(s * 2.4, s * 2.72), 24.7, 0.86], 'strike')
    m.box([-0.5, 24.9, -0.13], [0.5, 24.98, 0.13], 'steel')


def build_shovel(m):
    m.rod(-3.4, -3.0, 0.49, 'cap')
    m.rod(-3.0, 22.8, 0.45, 'wood')
    m.rod(22.8, 25.0, 0.55, 'blade')
    m.rod(25.0, 25.75, 0.7, 'blade')
    for y0, y1, hw in [(25.6, 28.8, 2.4), (28.8, 29.8, 2.2), (29.8, 30.6, 1.8), (30.6, 31.2, 1.3), (31.2, 31.6, 0.75),
                       (31.6, 31.9, 0.3)]:
        m.box([-hw, y0, -0.07], [hw, y1, 0.07], 'blade')
    m.box([-2.4, 25.6, -0.36], [2.4, 25.95, -0.07], 'blade')
    m.box([-0.35, 25.8, 0.07], [0.35, 29.5, 0.2], 'blade')


def build_golf_club(m):
    m.rod(-3.2, 1.0, 0.37, 'grip')
    m.rod(1.0, 4.8, 0.32, 'grip')
    m.rod(4.8, 27.6, 0.17, 'chrome')
    for y in (10, 16, 22):
        m.rod(y, y + 0.15, 0.21, 'chrome')
    m.rod(27.6, 29.0, 0.26, 'steel')
    m.box([-0.35, 28.6, -0.22], [2.6, 30.0, 0.22], 'head')
    m.box([1.8, 28.6, -0.22], [2.9, 30.35, 0.22], 'head')
    m.box([-0.3, 28.42, -0.3], [2.85, 28.62, 0.32], 'head')
    m.box([0.2, 28.7, -0.42], [2.4, 29.3, -0.22], 'head')


def build_hockey_stick(m):
    m.box([-0.58, -3.8, -0.42], [0.58, -3.2, 0.42], 'tape_white')
    m.box([-0.55, -3.2, -0.39], [0.55, 1.5, 0.39], 'tape_white')
    m.box([-0.52, -3.5, -0.36], [0.52, 27.6, 0.36], 'shaft')
    m.box([-0.52, 26.8, -0.36], [0.9, 28.4, 0.36], 'shaft')
    m.seg((0.2, 27.6), (5.0, 29.6), 1.5, 0.32, 'tape_black', over=0.2)


def build_lead_pipe(m):
    m.rod(-4.3, -3.6, 0.5, 'pipe')
    m.rod(-3.6, 2.0, 0.6, 'duct')
    m.rod(-3.6, 15.6, 0.52, 'pipe')
    m.rod(15.4, 17.5, 0.68, 'fitting')
    m.rod(0.0, 2.3, 0.68, 'fitting', cx=16.6, cz=0, axis=0)
    m.rod(2.3, 3.7, 0.52, 'pipe', cx=16.6, cz=0, axis=0)


def build_wrench(m):
    m.box([-0.5, -4.3, -0.3], [0.5, -4.0, 0.3], 'red')
    m.box([-0.55, -4.0, -0.32], [0.55, 7.6, 0.32], 'red')
    m.box([-0.6, -3.0, -0.38], [0.6, 6.6, -0.32], 'red')
    m.box([-0.6, -3.0, 0.32], [0.6, 6.6, 0.38], 'red')
    m.box([-0.7, 7.6, -0.48], [0.7, 9.0, 0.48], 'red')
    m.box([-0.7, 9.0, -0.48], [-0.3, 10.2, 0.48], 'red')
    m.box([0.7, 8.6, -0.42], [2.1, 9.3, 0.42], 'jaw')
    m.box([-0.45, 9.0, -0.3], [0.25, 10.8, 0.3], 'jaw')
    m.rod(9.2, 10.0, 0.6, 'knurl', cx=-0.05)
    m.box([-0.45, 10.6, -0.42], [2.3, 11.5, 0.42], 'jaw')


def build_frying_pan(m):
    m.box([-0.32, -3.4, -0.2], [0.32, 5.0, 0.2], 'iron')
    m.box([-0.42, 3.5, -0.28], [0.42, 5.4, 0.28], 'iron')
    cy, R, dy = 9.6, 4.2, 0.6
    rows = []
    y = cy - R
    while y < cy + R - 1e-6:
        ym = y + dy / 2 - cy
        rows.append((y, y + dy, math.sqrt(max(R * R - ym * ym, 0.3))))
        y += dy
    for i, (y0, y1, hw) in enumerate(rows):
        m.box([-hw, y0, -0.2], [hw, y1, 0.1], 'base')
        nb = min(rows[max(i - 1, 0)][2], rows[min(i + 1, len(rows) - 1)][2])
        inner = min(hw, nb) - 0.35
        if i in (0, len(rows) - 1):
            m.box([-hw, y0, 0.1], [hw, y1, 0.95], 'iron')
        else:
            m.box([inner, y0, 0.1], [hw, y1, 0.95], 'iron')
            m.box([-hw, y0, 0.1], [-inner, y1, 0.95], 'iron')


def build_hammer(m):
    m.rod(-3.6, -3.2, 0.45, 'wood')
    m.rod(-3.2, 6.3, 0.38, 'wood')
    m.box([-0.55, 5.9, -0.48], [0.55, 7.9, 0.48], 'forged')
    m.rod(-1.6, -0.55, 0.42, 'forged', cx=6.9, cz=0, axis=0)
    m.rod(-2.2, -1.6, 0.58, 'strike', cx=6.9, cz=0, axis=0)
    m.seg((0.55, 7.3), (1.6, 7.0), 0.75, 0.75, 'forged')
    for z in (-0.22, 0.22):
        m.seg((1.6, 7.0), (2.5, 6.3), 0.55, 0.28, 'forged', z=z)
        m.seg((2.5, 6.3), (2.9, 5.6), 0.4, 0.28, 'forged', z=z)


def blade_fn(base=0.66, hamon=False, dark=None, bevel=0.3, edge=None):
    def fn(f):
        if edge is not None:
            f.box = dict(f.box, hi=np.array([edge, f.box['hi'][1], f.box['hi'][2]]))
        return P.blade(f, base, bevel=bevel, hamon=hamon, dark=dark)
    return fn


def axe_head(edge_x):
    def fn(f):
        c = P.paint(f, '#ad1a12', chips=0.7)
        d = edge_x - f.X
        grind = (d < 0.95 + (f.noise(1.4, 3, 2) - 0.5) * 0.2) & (f.X > 0.7)
        c = np.where(grind[..., None], P.steel(f, 0.8, spread=0.12), c)
        tip = f.X < -2.7
        return np.where(tip[..., None], P.steel(f, 0.6), c)
    return fn


def rail_slots(fn, period=0.5):
    def out(f):
        c = fn(f)
        if abs(f.n[2]) > 0.5:
            return c
        slot = (f.Z % period) / period < 0.45
        return np.where(slot[..., None], c * 0.45, c)
    return out


def shovel_blade(f):
    c = P.paint(f, '#3f4a34', chips=1.2, steel_base=0.5)
    return P.rust(f, c, 0.25 + 0.4 * f.wear)


WEAPONS = [
    (Weapon('kitchen_knife', 'Kitchen Knife', 'short_blade', 4.5, 2.0, 2.6, 0.0, 140, 3, 0.0, sound='blade',
            skill='BLADE'), build_kitchen_knife,
     dict(handle=lambda f: P.polymer(f, '#141414'), steel=bright_steel(0.7), rivet=bright_steel(0.8),
          blade=blade_fn(0.7, bevel=0.22))),
    (Weapon('hammer', 'Hammer', 'small_blunt', 5.0, 1.6, 2.7, 0.2, 260, 5, 0.08), build_hammer,
     dict(wood=lambda f: P.wood(f, '#b98d5c', '#7a5230'), forged=rusty_steel(0.36, 0.3),
          strike=bright_steel(0.7))),
    (Weapon('frying_pan', 'Frying Pan', 'small_blunt', 5.5, 1.3, 2.8, 0.5, 170, 7, 0.22, sound='pan'),
     build_frying_pan, dict(iron=P.cast_iron, base=lambda f: P.cast_iron(f, rim=False))),
    (Weapon('crowbar', 'Crowbar', 'blunt', 6.0, 1.2, 3.0, 0.4, 480, 7, 0.18), build_crowbar,
     dict(paint=red_paint('#a0130d', 0.8), bare=rusty_steel(0.55, 0.2))),
    (Weapon('pipe_wrench', 'Pipe Wrench', 'blunt', 6.5, 1.1, 2.9, 0.5, 420, 8, 0.24), build_wrench,
     dict(red=red_paint('#b3170f', 0.7), jaw=rusty_steel(0.42, 0.3), knurl=rusty_steel(0.4, 0.3))),
    (Weapon('lead_pipe', 'Lead Pipe', 'blunt', 6.0, 1.15, 3.1, 0.45, 380, 7, 0.22), build_lead_pipe,
     dict(pipe=P.galvanised, duct=P.duct_tape, fitting=lambda f: P.rust(f, P.cast_iron(f), 0.4))),
    (Weapon('golf_club', 'Golf Club', 'blunt', 6.0, 1.1, 3.3, 0.5, 110, 7, 0.28), build_golf_club,
     dict(grip=lambda f: P.rubber(f, '#151515', 0.25), chrome=P.chrome, steel=bright_steel(0.7),
          head=bright_steel(0.62))),
    (Weapon('hockey_stick', 'Hockey Stick', 'blunt', 5.5, 1.1, 3.4, 0.6, 120, 7, 0.3), build_hockey_stick,
     dict(tape_white=lambda f: P.tape(f, '#d9d6cc', 0.6, 0.12), tape_black=lambda f: P.tape(f, '#111112', 0.5),
          shaft=lambda f: P.wood(f, '#caa676', '#94683f', 1.4))),
    (Weapon('baseball_bat', 'Baseball Bat', 'blunt', 7.0, 1.0, 3.2, 0.7, 220, 9, 0.4), build_baseball_bat,
     dict(wood=lambda f: P.wood(f, '#d9b98a', '#a4794b'), tape=lambda f: P.tape(f, '#16161a'),
          _brand=brand_oval(14.2, 16.6))),
    (Weapon('spiked_bat', 'Spiked Bat', 'blunt', 8.5, 0.95, 3.2, 0.7, 200, 9, 0.4, notes='nails'),
     build_spiked_bat,
     dict(wood=lambda f: P.wood(f, '#a8835a', '#6b4a2c'), tape=lambda f: P.tape(f, '#16161a'),
          nail=rusty_steel(0.55, 0.5))),
    (Weapon('shovel', 'Shovel', 'blunt', 7.0, 0.9, 3.4, 0.8, 300, 10, 0.45, two_handed=True), build_shovel,
     dict(cap=lambda f: P.polymer(f, '#222222'), wood=lambda f: P.wood(f, '#b48a5a', '#7a5532', 1.8),
          blade=shovel_blade)),
    (Weapon('machete', 'Machete', 'long_blade', 9.0, 1.2, 3.1, 0.3, 300, 8, 0.12, sound='blade', skill='BLADE'),
     build_machete,
     dict(handle=lambda f: P.polymer(f, '#1a1a1a', 0.12), rivet=bright_steel(0.78),
          blade=blade_fn(dark='#4d4f50', bevel=0.34))),
    (Weapon('katana', 'Katana', 'long_blade', 12.0, 1.1, 3.3, 0.3, 360, 8, 0.12, two_handed=True, sound='blade',
            skill='BLADE'), build_katana,
     dict(iron=lambda f: P.cast_iron(f, '#24211f'), wrap=P.silk_wrap, tsuba=lambda f: P.cast_iron(f, '#2a2622'),
          brass=P.brass, blade=blade_fn(0.74, hamon=True, bevel=0.18))),
    (Weapon('fire_axe', 'Fire Axe', 'axe', 13.0, 0.8, 3.2, 0.6, 420, 13, 0.35, two_handed=True, sound='blade',
            skill='BLADE'), build_fire_axe,
     dict(wood=lambda f: P.wood(f, '#c79c66', '#8c6339'), head=axe_head(4.25), steel=bright_steel(0.6))),
    (Weapon('sledgehammer', 'Sledgehammer', 'heavy_blunt', 16.0, 0.6, 3.3, 1.2, 600, 17, 0.8, two_handed=True),
     build_sledgehammer,
     dict(rubber=lambda f: P.rubber(f, '#121212', 0.3), glass=P.fibreglass, forged=rusty_steel(0.3, 0.45),
          strike=bright_steel(0.62), steel=bright_steel(0.55))),
]


# ================================================================== display transforms


def melee_display(m):
    """Vanilla-equivalent hand transforms (the model is a sword sprite turned upright), fitted views."""
    disp = {}
    for ctx, (rot, trans, s) in VANILLA.items():
        disp[ctx] = M.transform([rot[0], rot[1], rot[2] - 45], trans, s * K)
    Rg = M.R_xyz(0, 0, -45) @ M.R_xyz(0, 28, 0)
    rg = M.euler_xyz(Rg)
    s, t = M.fit(m, Rg, 15.2)
    disp['gui'] = M.transform(rg, t, s)
    Rgr = M.R_xyz(0, 0, -45)
    s, t = M.fit(m, Rgr, 9.0, (0, 2, 0))
    disp['ground'] = M.transform(M.euler_xyz(Rgr), t, s)
    Rf = M.R_xyz(0, 180, 0) @ M.R_xyz(0, 0, -45)
    s, t = M.fit(m, Rf, 15.0)
    disp['fixed'] = M.transform(M.euler_xyz(Rf), t, s)
    disp['head'] = M.transform([0, 180, -45], [0, 13, 7], 0.6)
    return disp


def grip_y():
    # a sword sprite held at V_GRIP, turned upright: S*0.707*a == -4*S_v  =>  a = -4/(0.707 K)
    return 8 - 4 / (math.sqrt(0.5) * K)


# ================================================================== the assault rifle

SIGHT_Y = 4.30


def build_rifle(m, mag=True, mag_drop=0.0):
    anod, poly, park = 'anod', 'poly', 'park'
    # lower receiver
    m.box([-0.42, 1.35, -3.0], [0.42, 2.45, 2.9], anod)
    m.box([-0.46, 0.35, -3.65], [0.46, 2.0, -1.55], anod)
    m.box([-0.5, 0.3, -3.75], [0.5, 0.6, -1.45], anod)
    m.box([-0.18, 0.75, -1.55], [0.18, 0.95, 0.35], anod)
    m.box([-0.18, 0.75, 0.25], [0.18, 1.4, 0.5], anod)
    m.box([-0.07, 0.95, -0.85], [0.07, 1.4, -0.68], 'steel')
    m.box([-0.07, 1.05, -0.95], [0.07, 1.25, -0.8], 'steel')
    m.seg((0.35, 1.4), (1.2, -1.7), 1.05, 0.78, poly, plane='zy')
    m.box([-0.3, 1.2, 0.3], [0.3, 1.45, 1.1], anod)
    # buffer tube and stock
    m.rod(2.9, 9.5, 0.52, anod, cx=0, cz=2.75, axis=2)
    m.rod(2.9, 3.25, 0.62, park, cx=0, cz=2.75, axis=2)
    m.box([-0.48, 1.6, 6.0], [0.48, 3.3, 9.9], poly)
    m.seg((6.0, 1.9), (9.9, 0.55), 1.0, 0.9, poly, plane='zy')
    m.box([-0.52, 0.4, 9.9], [0.52, 3.35, 10.35], 'rubber')
    m.box([-0.42, 3.3, 6.5], [0.42, 3.5, 9.6], poly)
    m.box([-0.15, 1.45, 6.3], [0.15, 1.6, 7.6], poly)
    # upper receiver
    m.box([-0.43, 2.45, -3.85], [0.43, 3.45, 2.9], anod)
    m.box([-0.33, 3.45, -3.85], [0.33, 3.75, 2.75], 'rail')
    m.box([0.43, 2.75, -1.3], [0.45, 3.2, 0.6], 'port')
    m.box([0.43, 2.45, -1.3], [0.52, 2.68, 0.6], anod)
    m.box([0.43, 2.9, 0.6], [0.62, 3.4, 1.0], anod)
    m.rod(1.0, 1.9, 0.25, anod, cx=0.5, cz=3.05, axis=2)
    m.rod(1.9, 2.15, 0.2, 'steel', cx=0.5, cz=3.05, axis=2)
    m.box([-0.3, 3.45, 2.75], [0.3, 3.65, 3.35], anod)
    m.box([-0.55, 3.45, 3.05], [-0.3, 3.62, 3.35], anod)
    # controls
    m.box([-0.52, 2.0, 0.0], [-0.42, 2.25, 0.55], 'steel')
    m.box([0.42, 2.02, 0.1], [0.5, 2.22, 0.4], 'steel')
    m.box([0.42, 1.75, -1.5], [0.55, 2.05, -1.2], 'steel')
    m.box([-0.52, 1.6, -1.3], [-0.42, 2.2, -1.05], anod)
    for z in (-3.3, 2.5):
        m.box([-0.47, 2.25, z - 0.1], [0.47, 2.45, z + 0.1], 'steel')
    # handguard with four rails
    m.box([-0.6, 2.25, -10.3], [0.6, 3.65, -3.95], 'guard')
    m.box([-0.33, 3.65, -10.3], [0.33, 3.95, -3.95], 'rail')
    m.box([-0.33, 1.95, -10.3], [0.33, 2.25, -3.95], 'rail')
    m.box([-0.9, 2.62, -10.3], [-0.6, 3.28, -3.95], 'rail')
    m.box([0.6, 2.62, -10.3], [0.9, 3.28, -3.95], 'rail')
    m.box([-0.66, 2.2, -3.95], [0.66, 3.7, -3.85], anod)
    # barrel, front sight, flash hider
    m.rod(-15.4, -10.3, 0.27, park, cx=0, cz=2.95, axis=2)
    m.box([-0.42, 2.55, -11.0], [0.42, 3.35, -10.4], park)
    m.box([-0.38, 3.35, -10.95], [0.38, 3.75, -10.45], park)
    for x0, x1 in ((0.2, 0.38), (-0.38, -0.2)):
        m.box([x0, 3.75, -10.9], [x1, 4.45, -10.5], park)
    m.box([-0.06, 3.75, -10.75], [0.06, SIGHT_Y, -10.62], park)
    m.box([-0.12, 2.2, -10.95], [0.12, 2.55, -10.6], park)
    m.rod(-16.9, -15.4, 0.36, 'hider', cx=0, cz=2.95, axis=2)
    # flip-up rear sight with its aperture at SIGHT_Y
    m.box([-0.36, 3.75, 1.7], [0.36, 4.0, 2.6], anod)
    for x0, x1 in ((0.22, 0.36), (-0.36, -0.22)):
        m.box([x0, 4.0, 2.2], [x1, 4.62, 2.4], anod)
    hole = 0.09
    m.box([-0.22, 4.0, 2.22], [-hole, 4.55, 2.38], anod)
    m.box([hole, 4.0, 2.22], [0.22, 4.55, 2.38], anod)
    m.box([-hole, SIGHT_Y + hole, 2.22], [hole, 4.55, 2.38], anod)
    m.box([-hole, 4.0, 2.22], [hole, SIGHT_Y - hole, 2.38], anod)
    if mag:
        build_mag(m, drop=mag_drop)


def build_mag(m, drop=0.0, origin=(0, 0, 0)):
    ox, oy, oz = origin
    oy -= drop
    m.box([ox - 0.36, oy - 0.6, oz - 3.55], [ox + 0.36, oy + 0.4, oz - 1.65], 'mag')
    m.seg((oz - 2.6, oy - 0.5), (oz - 2.75, oy - 1.9), 1.9, 0.72, 'mag', z=ox, plane='zy')
    m.seg((oz - 2.75, oy - 1.9), (oz - 3.3, oy - 3.2), 1.9, 0.72, 'mag', z=ox, plane='zy')
    m.seg((oz - 3.3, oy - 3.2), (oz - 3.42, oy - 3.5), 2.05, 0.82, 'mag', z=ox, plane='zy', over=0.1)


def build_flash(m):
    """Muzzle flash: crossed glowing planes and a core just past the flash hider."""
    y, z = 2.95, -16.95
    m.box([-0.02, y - 1.1, z - 2.4], [0.02, y + 1.1, z], 'flash', light=15, noshade=True)
    m.box([-1.1, y - 0.02, z - 2.4], [1.1, y + 0.02, z], 'flash', light=15, noshade=True)
    m.box([-0.35, y - 0.35, z - 0.9], [0.35, y + 0.35, z], 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    ax = f.along()
    r = np.sqrt(a ** 2 + b ** 2)
    t = np.clip(1 - r / 1.1 - (16.95 + ax) * 0.0, 0, 1)
    c = P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)
    return c


def rifle_look(wear):
    def fde(f):
        c = P.polymer(f, '#9a845f', 0.07)
        ribs = (np.sin(f.Y * 9) > 0.7) & (np.abs(f.n[0]) > 0.5)
        return np.where(ribs[..., None], c * 0.85, c)

    def guard(f):
        c = P.anodised(f)
        if abs(f.n[2]) > 0.5:
            return c
        vents = ((f.Z % 1.2) < 0.55) & (np.abs(f.Y - 2.95) < 0.25)
        return np.where(vents[..., None], c * 0.3, c)

    def port(f):
        c = P.brass(f) * 0.8
        lug = (f.Z % 0.6) < 0.2
        return np.where(lug[..., None], c * 0.6, c)

    def hider(f):
        c = P.parkerised(f)
        if abs(f.n[2]) > 0.5:
            return c
        slot = ((f.Z % 0.5) < 0.35) & (f.Z > -16.7) & (f.Z < -15.6)
        return np.where(slot[..., None], c * 0.25, c)

    d = dict(
        anod=P.anodised, poly=lambda f: P.polymer(f, '#2a2a2c'), park=P.parkerised, steel=bright_steel(0.45),
        rubber=lambda f: P.rubber(f, '#111111', 0.2), rail=rail_slots(P.anodised), guard=guard, port=port,
        hider=hider, mag=fde, flash=flash_paint)
    d['_after'] = [lambda f, c: P.grime(f, c, 0.35 * (0.4 + wear)),
                   lambda f, c: P.scratches(f, c, 0.5 * (0.3 + wear))]
    return d


def rifle_frame():
    """Muzzle direction mapped onto a held sword sprite (third person), and the grip point that keeps it
    in the hand: P maps rifle axes to sprite axes; the grip goes where the sprite's grip goes."""
    a = math.radians(30)   # a held sword's blade points along ~45 degrees; the muzzle sits a little lower
    fwd = np.array([math.cos(a), math.sin(a), 0])
    up = np.array([-math.sin(a), math.cos(a), 0])
    Pm = np.column_stack([np.array([0, 0, 1.0]), up, -fwd])
    g = C + Pm.T @ ((V_GRIP - C) / K)
    return Pm, g


def rifle_display(m, g, ads=False, cant=0.0, drop=0.0):
    Pm, _ = rifle_frame()
    disp = {}
    rot, trans, s = VANILLA['thirdperson_righthand']
    R = M.R_xyz(*rot) @ Pm
    disp['thirdperson_righthand'] = M.transform(M.euler_xyz(R), trans, s * K)
    arm = np.array([0.56, -0.52, -0.72]) * 16
    S = 0.72
    if ads:
        R = np.eye(3)
        aperture = np.array([8.0, g[1] + SIGHT_Y, g[2] + 2.3 - HAND[2]])
        target = np.array([0.0, 0.0, -2.8])
        T = target - arm - S * (R @ (aperture - C))
        disp['firstperson_righthand'] = M.transform([0, 0, 0], T, S)
    else:
        R = M.R_xyz(2 + cant * 0.6, 4 + cant * 0.3, -cant)
        target = np.array([0.30, -0.43 - drop, -0.58]) * 16
        T = target - arm - S * (R @ (g - C))
        disp['firstperson_righthand'] = M.transform(M.euler_xyz(R), T, S)
    Rg = M.R_xyz(12, 0, 0) @ M.R_xyz(0, -90 - 22, 0)
    s2, t2 = M.fit(m, Rg, 15.6)
    disp['gui'] = M.transform(M.euler_xyz(Rg), t2, s2)
    Rgr = M.R_xyz(0, -90, 0)
    s3, t3 = M.fit(m, Rgr, 9.0, (0, 2, 0))
    disp['ground'] = M.transform(M.euler_xyz(Rgr), t3, s3)
    Rf = M.R_xyz(0, 90, 0)
    s4, t4 = M.fit(m, Rf, 15.5)
    disp['fixed'] = M.transform(M.euler_xyz(Rf), t4, s4)
    return disp


HAND = np.array([0.0, 0.0, 0.73])     # centre of the hand on the pistol grip, in the build frame


def rifle_models():
    Pm, g = rifle_frame()
    variants = {
        'assault_rifle': dict(),
        'assault_rifle_ads': dict(ads=True),
        'assault_rifle_flash': dict(flash=True),
        'assault_rifle_ads_flash': dict(ads=True, flash=True),
        'assault_rifle_mag_out': dict(mag=False, cant=28, drop=0.06),
        'assault_rifle_mag_in': dict(mag_drop=1.3, cant=28, drop=0.06),
        'assault_rifle_charge': dict(cant=-14, drop=0.03),
    }
    out = {}
    for name, v in variants.items():
        m = M.Model(name)
        build_rifle(m, mag=v.get('mag', True), mag_drop=v.get('mag_drop', 0.0))
        if v.get('flash'):
            build_flash(m)
        m.shift(-HAND)
        m.shift(g)
        m.tidy()
        out[name] = (m, v)
    return out, g


def build_magazine_item(m):
    build_mag(m)


def build_ammo_box(m):
    m.box([-2.6, 0, -1.4], [2.6, 3.0, 1.4], 'carton')
    m.box([-2.65, 2.85, -1.45], [2.65, 3.1, 1.45], 'carton')


def carton(f):
    c = P.plain(f, '#4b5a37', 0.18)
    stripe = np.abs(f.Y - 1.6) < 0.35
    c = np.where(stripe[..., None], P.plain(f, '#c9a33a', 0.1), c)
    if abs(f.n[2]) > 0.5:   # little cartridge icons on the front and back
        x = f.X
        for cx in (-1.6, -0.8, 0.0, 0.8, 1.6):
            case = (np.abs(x - cx) < 0.18) & (np.abs(f.Y - 0.85) < 0.45)
            tip = (np.abs(x - cx) < 0.11) & (np.abs(f.Y - 1.45) < 0.15)
            c = np.where(case[..., None], P.col('#c99a45'), c)
            c = np.where(tip[..., None], P.col('#8a5a2b'), c)
    return P.grime(f, c, 0.4)


def small_display(m, gui_rot=(25, -35, 0)):
    disp = {}
    Rg = M.R_xyz(*gui_rot)
    s, t = M.fit(m, Rg, 13.5)
    disp['gui'] = M.transform(gui_rot, t, s)
    for ctx, (rot, trans, sc) in VANILLA.items():
        R = M.R_xyz(*rot)
        s2, t2 = M.fit(m, R, 9.0)
        disp[ctx] = M.transform(rot, np.array(trans) + t2, s2)
    Rgr = np.eye(3)
    s3, t3 = M.fit(m, Rgr, 7.0, (0, 2, 0))
    disp['ground'] = M.transform([0, 0, 0], t3, s3)
    s4, t4 = M.fit(m, M.R_xyz(0, 180, 0), 12)
    disp['fixed'] = M.transform([0, 180, 0], t4, s4)
    return disp


# ================================================================== writing


def write_texture(m, lk, wear, seed, name):
    patches = M.paint_model(m, lk, wear, seed)
    img, uvs, T = M.pack(patches)
    path = ASSETS / f'textures/item/weapon/{name}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    M.save_png(img, path)
    return uvs, T


def item_def(path, model):
    M.dump({'model': model}, ASSETS / f'items/{path}.json')


def ref(name):
    return {'type': 'minecraft:model', 'model': f'{NS}:item/weapon/{name}'}


def main():
    preview = None
    if '--preview' in sys.argv:
        preview = Path(sys.argv[sys.argv.index('--preview') + 1])
    gy = grip_y()
    lang = {}
    summary = []
    for spec, build, mats in WEAPONS:
        m = M.Model(spec.name)
        build(m)
        lo, hi = m.bounds()
        reach = (lo[1] + (hi[1] - lo[1]) * 0.55, hi[1])
        m.shift([8, gy, 8])
        m.tidy()
        disp = melee_display(m)
        for variant, wear, blood in (('', 0.2, 0.0), ('_worn', 0.85, 1.0)):
            lk = look(wear, reach, blood=blood, **mats)
            uvs, T = write_texture(m, lk, wear, zlib.crc32(spec.name.encode()) % 1000 + (7 if variant else 0), spec.name + variant)
            mj = M.model_json(m, uvs, f'{NS}:item/weapon/{spec.name}{variant}', disp)
            M.dump(mj, ASSETS / f'models/item/weapon/{spec.name}{variant}.json')
        item_def(spec.name, {'type': 'minecraft:range_dispatch', 'property': 'minecraft:damage',
                             'entries': [{'threshold': 0.5, 'model': ref(spec.name + '_worn')}],
                             'fallback': ref(spec.name)})
        lang[f'item.{NS}.{spec.name}'] = spec.title
        summary.append(f'{spec.name}: {len(m.parts)} boxes, {T}px')

    # the rifle: every variant shares one texture, painted once over the union of their parts
    models, g = rifle_models()
    lk = rifle_look(0.4)
    union, seen = M.Model('assault_rifle_all'), {}
    union.offset = next(iter(models.values()))[0].offset
    for name, (m, v) in models.items():
        for p in m.parts:
            if key_of(p) not in seen:
                seen[key_of(p)] = len(union.parts)
                union.parts.append(p)
    uvs, T = write_texture(union, lk, 0.4, 556, 'assault_rifle')
    for name, (m, v) in models.items():
        sub = {}
        for i, p in enumerate(m.parts):
            j = seen[key_of(p)]
            for face in M.FACES:
                if (j, face) in uvs and face not in p.get('skip', ()):
                    sub[(i, face)] = uvs[(j, face)]
        disp = rifle_display(m, g, ads=v.get('ads', False), cant=v.get('cant', 0.0), drop=v.get('drop', 0.0))
        M.dump(M.model_json(m, sub, f'{NS}:item/weapon/assault_rifle', disp), ASSETS / f'models/item/weapon/{name}.json')
    summary.append(f'assault_rifle: {len(union.parts)} boxes, {T}px')

    def cmd(index, cases, fallback):
        return {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data', 'index': index,
                'entries': [{'threshold': t, 'model': mdl} for t, mdl in cases], 'fallback': fallback}

    hip = cmd(1, [(1, ref('assault_rifle_flash'))], ref('assault_rifle'))
    ads = cmd(1, [(1, ref('assault_rifle_ads_flash'))], ref('assault_rifle_ads'))
    item_def('assault_rifle', cmd(0, [(1, ref('assault_rifle_mag_out')), (2, ref('assault_rifle_mag_in')),
                                      (3, ref('assault_rifle_charge'))],
                                  {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                                   'on_true': ads, 'on_false': hip}))
    lang[f'item.{NS}.assault_rifle'] = 'Assault Rifle'

    # magazine and ammo
    mag = M.Model('rifle_magazine')
    build_magazine_item(mag)
    lo, hi = mag.bounds()
    mag.shift(C - (lo + hi) / 2)
    mag.tidy()
    uvs, T = write_texture(mag, rifle_look(0.3), 0.3, 557, 'rifle_magazine')
    M.dump(M.model_json(mag, uvs, f'{NS}:item/weapon/rifle_magazine', small_display(mag, (20, 60, 0))),
           ASSETS / 'models/item/weapon/rifle_magazine.json')
    item_def('rifle_magazine', ref('rifle_magazine'))
    lang[f'item.{NS}.rifle_magazine'] = 'Rifle Magazine'

    box = M.Model('rifle_ammo')
    build_ammo_box(box)
    lo, hi = box.bounds()
    box.shift(C - (lo + hi) / 2)
    box.tidy()
    lk = {'carton': carton}
    uvs, T = write_texture(box, lk, 0.3, 558, 'rifle_ammo')
    M.dump(M.model_json(box, uvs, f'{NS}:item/weapon/rifle_ammo', small_display(box)),
           ASSETS / 'models/item/weapon/rifle_ammo.json')
    item_def('rifle_ammo', ref('rifle_ammo'))
    lang[f'item.{NS}.rifle_ammo'] = '5.56mm Rounds'

    merge_lang(lang)
    write_java()
    print('\n'.join(summary))
    if preview:
        write_preview(preview)


def key_of(p):
    return (p['mat'], tuple(np.round(p['lo'], 3)), tuple(np.round(p['hi'], 3)),
            json.dumps(p['rot'], sort_keys=True, default=lambda o: [round(float(x), 3) for x in o]))


def merge_lang(lines):
    path = ASSETS / 'lang/en_us.json'
    data = json.loads(path.read_text())
    data.update(lines)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')


def write_java():
    rows = []
    for spec, _b, _m in WEAPONS:
        rows.append(f'        new WeaponSpec("{spec.name}", Kind.{spec.kind.upper()}, {spec.damage}f, {spec.speed}f, '
                    f'{spec.reach}f, {spec.knockback}f, {spec.durability}, {spec.stamina}f, {spec.knockdown}f, '
                    f'{str(spec.two_handed).lower()}, Sound.{spec.sound.upper()}, Skill.{spec.skill})')
    body = ',\n'.join(rows)
    JAVA.parent.mkdir(parents=True, exist_ok=True)
    JAVA.write_text(f'''package com.zomboidcraft.combat;

import com.zomboidcraft.skills.Skill;
import java.util.List;

/**
 * Melee weapon stats. Generated by tools/weapons/weapons.py: edit the roster there.
 * damage = total hit damage (zombies have 20 health), speed = swings a second, reach in blocks,
 * stamina = endurance spent per swing, knockdown = base chance a hit puts a zombie on the ground.
 */
public final class WeaponSpecs {{
    public enum Kind {{ SHORT_BLADE, SMALL_BLUNT, BLUNT, LONG_BLADE, AXE, HEAVY_BLUNT }}

    public enum Sound {{ BLUNT, BLADE, PAN }}

    public record WeaponSpec(String name, Kind kind, float damage, float speed, float reach, float knockback,
                             int durability, float stamina, float knockdown, boolean twoHanded, Sound sound,
                             Skill skill) {{
    }}

    public static final List<WeaponSpec> ALL = List.of(
{body});

    private WeaponSpecs() {{
    }}
}}
''')


def write_preview(out):
    """Copies the models and textures next to the three.js preview page."""
    import shutil
    out.mkdir(parents=True, exist_ok=True)
    for sub in ('models/item/weapon', 'textures/item/weapon'):
        dst = out / sub
        if dst.exists():
            shutil.rmtree(dst)
        shutil.copytree(ASSETS / sub, dst)
    names = [s.name for s, _b, _m in WEAPONS]
    (out / 'roster.json').write_text(json.dumps({
        'melee': [{'name': s.name, 'title': s.title, 'damage': s.damage, 'speed': s.speed, 'reach': s.reach,
                   'durability': s.durability, 'two_handed': s.two_handed} for s, _b, _m in WEAPONS],
        'rifle': sorted(p.stem for p in (ASSETS / 'models/item/weapon').glob('assault_rifle*.json')),
        'other': ['rifle_magazine', 'rifle_ammo'], 'all': names}, indent=1))


if __name__ == '__main__':
    main()
