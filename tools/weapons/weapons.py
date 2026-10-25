"""Generates the weapon item models, textures, item definitions, lang lines and stats table.

    python3 tools/weapons/weapons.py [--preview DIR]

Each weapon is built from boxes in a local frame: the hand's grip at the origin, the weapon
running up +y, things that stick out sideways (axe blades, hammer claws, club heads) along +x
and thickness along z. The generator then moves it into item-model space and works out the
display transforms so the hand holds the grip in every view. Two textures per melee weapon, each
with LabPBR _s and _n maps: fresh, and battered and bloodied for when its condition drops below half.

The guns are modular (kit.py): a bare body plus parts in slots, drawn from real measurements
(rifle.py, attachments.py), with LabPBR material maps and a ghost model per slot for the weapon
screen.

Writes:
  assets/zomboidcraft/models/item/weapon/*.json, textures/item/weapon/*.png, items/*.json
  assets/zomboidcraft/gun_slots/*.json (slot mount points and what fits, for the weapon screen)
  src/main/java/com/zomboidcraft/combat/WeaponSpecs.java (generated stats)
  src/main/java/com/zomboidcraft/combat/GunSlotData.java (which parts fit which gun)
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
import poses as Q  # noqa: E402
import kit  # noqa: E402
import pbr  # noqa: E402
import rifle as RF  # noqa: E402
import pistol as PI  # noqa: E402
import attachments as AT  # noqa: E402
from inch import IN  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
JAVA = ROOT / 'src/main/java/com/zomboidcraft/combat/WeaponSpecs.java'
SLOT_JAVA = ROOT / 'src/main/java/com/zomboidcraft/combat/GunSlotData.java'
POSE_JAVA = ROOT / 'src/client/java/com/zomboidcraft/client/combat/HoldPoseData.java'
RIG_JAVA = ROOT / 'src/client/java/com/zomboidcraft/client/combat/GunRigs.java'
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


def tagged(tag, fx):
    """Names a finishing pass, so the LabPBR maps know where grime, scratches and blood landed."""
    fx.tag = tag
    return fx


def look(wear, reach, blood=0.0, grime=0.5, scratch=1.0, **mats):
    d = dict(mats)
    brand = d.pop('_brand', None)
    d['_after'] = ([brand] if brand else []) + [
        tagged('grime', lambda f, c: P.grime(f, c, grime * (0.4 + wear))),
        tagged('scratch', lambda f, c: P.scratches(f, c, scratch * (0.3 + wear))),
        tagged('blood', lambda f, c: P.blood(f, c, blood * wear, reach)),
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


# What each melee material is made of, for its LabPBR maps (the kinds are in pbr.py).
MELEE_PBR = {
    'kitchen_knife': dict(handle='polymer', steel='steel', rivet='steel', blade='blade'),
    'hammer': dict(wood='varnish', forged='steel', strike='steel'),
    'frying_pan': dict(iron='cast_iron', base='cast_iron'),
    'crowbar': dict(paint='painted', bare='steel'),
    'pipe_wrench': dict(red='painted', jaw='steel', knurl='steel'),
    'lead_pipe': dict(pipe='galvanised', duct='duct', fitting='cast_iron'),
    'golf_club': dict(grip='rubber', chrome='chrome', steel='steel', head='steel'),
    'hockey_stick': dict(tape_white='cloth_tape', tape_black='cloth_tape', shaft='lacquer'),
    'baseball_bat': dict(wood='lacquer', tape='grip_tape'),
    'spiked_bat': dict(wood='varnish', tape='grip_tape', nail='steel'),
    'shovel': dict(cap='polymer', wood='varnish', blade='painted'),
    'machete': dict(handle='polymer', rivet='steel', blade='blade'),
    'katana': dict(iron='cast_iron', wrap='cord', tsuba='cast_iron', brass='brass', blade='blade'),
    'fire_axe': dict(wood='varnish', head='painted', steel='steel'),
    'sledgehammer': dict(rubber='rubber', glass='fibreglass', forged='steel', strike='steel', steel='steel'),
    'rifle_ammo': dict(carton='card'),
    'pistol_ammo': dict(carton='card'),
}
MELEE_PX = 16       # texture pixels per unit, the rifle's density, so a held weapon's texels stay small


# ================================================================== display transforms
# First person: the hand low on the right and the weapon upright, leaning a little outward, its head (and a blade's

# First person: the hand low on the right and the weapon leaning in toward the crosshair, its head (and a blade's
# edge) turned to the target. Worked in the hand camera's space: blocks, looking down -z, a 70 degree tall view.
FP_ARM = np.array([0.56, -0.52, -0.72])   # vanilla's arm offset, taken back out of the translation
FP_GRIP = (0.40, -0.82, 0.75)             # where the hand sits: screen x and y (-1..1 on a 16:9 screen), depth
FP_SHAFT = (0.30, 0.86, -0.41)            # the handle's direction: up, leaning a little outward, and away
FP_HEAD = (-0.70, 0.0, -0.70)             # the way the head faces
FP_TIP = 0.36                             # long weapons are scaled so their far end reaches this screen height
FP_SCALE = (0.52, 0.92)                   # model scale range, a little over life size up to twice it for short ones
# a pan is turned about its handle so its inside shows rather than its whole bottom, and kept smaller: its face
# would otherwise fill the middle of the screen
FP_SPIN = {'frying_pan': 145}
FP_TOP = {'frying_pan': 0.62}
FP_TAN = math.tan(math.radians(35))


def fp_screen(p):
    return p[0] / (-p[2] * FP_TAN * 16 / 9), p[1] / (-p[2] * FP_TAN)


def melee_first_person(m, grip):
    Y = np.array(FP_SHAFT) / np.linalg.norm(FP_SHAFT)
    X = np.array(FP_HEAD) - Y * np.dot(FP_HEAD, Y)
    X /= np.linalg.norm(X)
    a = math.radians(FP_SPIN.get(m.name, 0))
    X = math.cos(a) * X + math.sin(a) * np.cross(X, Y)
    R = np.column_stack([X, Y, np.cross(X, Y)])
    gx, gy, d = FP_GRIP
    G = np.array([gx * d * FP_TAN * 16 / 9, gy * d * FP_TAN, -d])
    pts = np.array([c for p in m.parts for c in M.corners(p)])
    tip = pts[np.argmax(pts[:, 1])] - grip

    def tip_height(s):
        return fp_screen(G + s * (R @ tip) / 16)[1]
    lo, hi = FP_SCALE[0], FP_TOP.get(m.name, FP_SCALE[1])
    if tip_height(hi) <= FP_TIP:
        s = hi
    elif tip_height(lo) >= FP_TIP:
        s = lo
    else:
        for _ in range(40):
            mid = (lo + hi) / 2
            lo, hi = (mid, hi) if tip_height(mid) < FP_TIP else (lo, mid)
        s = (lo + hi) / 2
    T = 16 * (G - FP_ARM) - s * (R @ (np.asarray(grip) - C))
    return M.transform(M.euler_xyz(R), T, s)


def melee_display(m):
    """Vanilla-equivalent third-person transform (the model is a sword sprite turned upright), the first-person
    pose above, and fitted views."""
    disp = {}
    for ctx, (rot, trans, s) in VANILLA.items():
        disp[ctx] = M.transform([rot[0], rot[1], rot[2] - 45], trans, s * K)
    disp['firstperson_righthand'] = melee_first_person(m, [8, grip_y(), 8])
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


# ================================================================== third-person holds (see poses.py)

S3 = 0.68       # rifle third-person scale, model pixels per build unit: a touch over life size so the arms don't hide it
S3M = 0.85 * K  # melee third-person scale, vanilla's

# Weapons carried in both hands, and where the off hand holds the handle (build-frame y range). A right-
# handed grip puts the left hand below the right on bats, axes and swords, and up the shaft on a shovel.
TWO_HAND_GRIPS = {
    'baseball_bat': (-2.6, -3.7), 'spiked_bat': (-2.6, -3.7), 'golf_club': (-2.4, -3.0),
    'hockey_stick': (-2.6, -3.4), 'shovel': (3.0, 6.5), 'katana': (-2.6, -4.4), 'fire_axe': (-2.6, -3.9),
    'sledgehammer': (-2.6, -3.9),
}

# where the off hand's centre goes: under the magazine well and forward along the handguard, an arm's
# half-thickness below them so the rifle rests on the forearm instead of sinking into it
RIFLE_SUPPORT = [tuple(RF.frame_point(0, -2.6, 1.7) + (0, -1.7, 0)), tuple(RF.frame_point(0, -RF.RAIL, -9.0) + (0, -2.55, 0))]
RIFLE_HAND = (-2.1, 0.5, 0.0)     # the fist beside the grip, outboard and a little low
RIFLE_BUTT = tuple(RF.frame_point(0, -1.15, 17.9))
RIFLE_CLEAR = [tuple(RF.frame_point(*p)) for p in (
    (0, 1.1, 12.2), (0, 1.1, 17.6), (0, -3.45, 17.9), (0, 2.85, 6.2), (0, 0.8, 7.4), (0, -7.9, 0.2),
    (0, -5.1, 7.5), (0, 0, -15.1))]


def rifle_holds():
    """Low ready (muzzle down and across, stock at the right shoulder) and aiming (shouldered, muzzle on
    the line of sight, as high as Minecraft's big head allows)."""
    out = {}
    for name, R, G0, butt_at, hand in (
            ('ready', Q.Ry(-35) @ Q.Rx(35) @ Q.UPRIGHT, [0, 5.5, -6.5], [-2.3, 2.8, -2.4], RIFLE_HAND),
            # the rifle rides on top of the forearms, stock in the right of the chest, top just under the chin
            ('aim', Q.UPRIGHT, [0, 2.5, -7.8], [-1.6, 1.5, -2.3], (-1.2, 1.7, 0.0))):
        h = Q.Hold(R, S3, RF.HAND, RIFLE_SUPPORT, hand_off=hand)
        G = Q.solve_grip(h, G0, [(RIFLE_BUTT, np.array(butt_at), 1.0)], RIFLE_CLEAR)
        arm, _ = Q.arm_for(h, G)
        out[name] = (h, G, arm)
    return out


# the off hand wraps the shooting hand round the grip's left side; the slide, muzzle and grip's foot stay clear
PISTOL_SUPPORT = [tuple(PI.frame_point(-0.95, -3.7, 6.45)), tuple(PI.frame_point(-0.95, -1.9, 5.95))]
PISTOL_HAND = (-1.0, 0.5, 0.0)
PISTOL_CLEAR = [tuple(PI.frame_point(*p)) for p in ((0, 0.6, 8.0), (0, 0, -0.5), (0, -4.4, 6.6), (0, 0.6, 3.0))]


def pistol_holds():
    """Low ready (both hands on it in front of the chest, muzzle down) and aiming (arms out straight, the
    sights up at the eyes)."""
    out = {}
    for name, R, G0 in (('ready', Q.Rx(45) @ Q.UPRIGHT, [0, 6.5, -6.0]),
                        ('aim', Q.UPRIGHT, [0, -1.5, -7.5])):
        h = Q.Hold(R, S3, PI.HAND, PISTOL_SUPPORT, hand_off=PISTOL_HAND)
        G = Q.solve_grip(h, G0, [], PISTOL_CLEAR)
        arm, _ = Q.arm_for(h, G)
        out[name] = (h, G, arm)
    return out


def melee_holds():
    """Two-handed ready (held across the body, head up past the right shoulder, both hands low in front)
    and the swing's wind-up and follow-through: the gun arm sweeps round from the right, up and back, to
    low on the left, and the off hand follows the handle."""
    R = Q.frame(Q.unit(-0.62, -0.72, -0.3), (0, 0, -1))
    h = Q.Hold(R, S3M, (0, 0, 0), [(0, -2.6, 0), (0, -3.7, 0)])
    G = Q.solve_grip(h, [0, 6.3, -6.8], [((0, 0, 0), np.array([-0.5, 6.3, -6.8]), 0.6)], [(0, 14, 0), (0, 22, 0)])
    arm, _ = Q.arm_for(h, G)
    Ra = Q.R_zyx(*arm)
    keys = {'ready': arm,
            'windup': Q.euler_zyx(Q.Ry(60) @ Q.Rx(-30) @ Ra),
            'strike': Q.euler_zyx(Q.Ry(-60) @ Q.Rx(15) @ Ra)}
    return h, G, keys


# ================================================================== the assault rifle (see rifle.py, kit.py)


def assault_rifle():
    look = RF.look()
    parts = {a.key: kit.Part(a.key, a.item, a.title, a.slot, a.build, a.look, a.sight, a.note) for a in AT.ALL}
    parts['stock'] = kit.Part('stock', 'rifle_stock', 'Rifle Stock', 'stock', RF.stock, look, note='Steadier aim, less kick')
    parts['mag'] = kit.Part('mag', 'rifle_magazine', 'Rifle Magazine', 'magazine', RF.magazine, look)
    fp = RF.frame_point
    slots = {
        'optic': kit.Slot(fp(0, RF.RAIL, 2.6), 'top', ['red_dot', 'holo', 'scope_4x']),
        'muzzle': kit.Slot(fp(0, 0, RF.MUZZLE), 'axial', ['suppressor']),
        'underbarrel': kit.Slot(fp(0, -RF.RAIL, -5.2), 'bottom', ['foregrip']),
        'side_rail': kit.Slot(fp(-RF.RAIL, 0, -7.6), 'left', ['light', 'laser']),
        'stock': kit.Slot(fp(0, 0, RF.UPPER_LEN), 'axial', ['stock'], default='stock'),
        'magazine': kit.Slot(fp(0, -1.55, 0.40), 'axial', ['mag'], default='mag'),
    }

    def flash(m, muzzle):
        if muzzle is None:
            RF.flash(m, RF.MUZZLE - RF.HIDER_LEN)
        else:
            RF.flash(m, RF.MUZZLE - 6.8, 0.45)

    return kit.Gun('assault_rifle', 'Assault Rifle', RF.body, look, RF.HAND, slots, parts, irons=RF.irons,
                   aperture=fp(0, RF.SIGHT, 6.21), flash=flash, handle=RF.charging_handle)


def pistol():
    """The M9-style pistol: fixed sights, a threaded barrel for its suppressor, a rail under the dust cover
    for the light or laser (drawn compact on a pistol), and its 15-round magazine."""
    look = PI.look()
    alook = AT.LOOK
    parts = {
        'suppressor': kit.Part('suppressor', 'pistol_suppressor', 'Pistol Suppressor', 'muzzle', PI.suppressor, alook,
                               note='Much quieter shots'),
        'light': kit.Part('light', 'weapon_light', 'Weapon Light', 'side_rail', PI.light, alook, seed=AT_SEED['light']),
        'laser': kit.Part('laser', 'laser_sight', 'Laser Sight', 'side_rail', PI.laser, alook, seed=AT_SEED['laser']),
        'mag': kit.Part('mag', 'pistol_magazine', 'Pistol Magazine', 'magazine', PI.magazine, look),
    }
    fp = PI.frame_point
    slots = {
        'muzzle': kit.Slot(fp(0, 0, PI.MUZZLE), 'axial', ['suppressor']),
        'side_rail': kit.Slot(fp(0, -(PI.RAIL_BASE + 0.23), sum(PI.RAIL_Z) / 2), 'bottom', ['light', 'laser']),
        'magazine': kit.Slot(fp(0, PI.MAG_BASE, 6.48), 'axial', ['mag'], default='mag'),   # the well's mouth
    }

    def flash(m, muzzle):
        PI.flash(m, PI.CAP_TIP)

    return kit.Gun('pistol', 'M9 Pistol', PI.body, look, PI.HAND, slots, parts,
                   aperture=fp(0, PI.SIGHT, PI.REAR_SIGHT), flash=flash, handle=PI.slide, seed=912, flash_size=0.7)


AT_SEED = {a.key: kit.Part(a.key, a.item, a.title, a.slot, a.build, a.look).seed for a in AT.ALL}


def rifle_frame():
    """Muzzle direction mapped onto a held sword sprite (third person), and the grip point that keeps it
    in the hand: P maps rifle axes to sprite axes; the grip goes where the sprite's grip goes."""
    a = math.radians(30)   # a held sword's blade points along ~45 degrees; the muzzle sits a little lower
    fwd = np.array([math.cos(a), math.sin(a), 0])
    up = np.array([-math.sin(a), math.cos(a), 0])
    Pm = np.column_stack([np.array([0, 0, 1.0]), up, -fwd])
    g = C + Pm.T @ ((V_GRIP - C) / K)
    return Pm, g


class FirstPerson:
    """How a gun sits in first person, for its item model and the first-person rig (RifleView):
    hip: where the grip sits in the hand camera (blocks), the gun's turn (degrees: muzzle up, muzzle in toward
      the crosshair, roll) and its scale;
    reload: the reload steps' (cant degrees, drop blocks), applied to the hip pose;
    irons: where the sight point sits in front of the eye when aiming the irons (pixels) and the scale;
    charge: how first person turns (degrees, about the grip) and moves (blocks) the gun to work its handle;
    locks_back: the handle (a pistol's slide) stays back on an empty gun; cycles: it runs back with every shot;
    support_grip: the off hand wraps a grip (a pistol's) rather than cupping a handguard;
    mag_axis: the way a magazine slides out of the well, in item pixels."""

    def __init__(self, hip_grip, hip_turn, hip_scale, reload, irons, irons_scale, handle_travel, charge_turn,
                 charge_push, locks_back=False, cycles=False, support_grip=False, mag_axis=(0.0, -1.0, 0.0)):
        self.hip_grip, self.hip_turn, self.hip_scale = hip_grip, hip_turn, hip_scale
        self.reload, self.irons, self.irons_scale = reload, np.asarray(irons, float), irons_scale
        self.handle_travel, self.charge_turn, self.charge_push = handle_travel, charge_turn, charge_push
        self.locks_back, self.cycles, self.support_grip = locks_back, cycles, support_grip
        a = np.asarray(mag_axis, float)
        self.mag_axis = a / np.linalg.norm(a)


# the rifle at the hip shows its upper receiver and magazine, the front sight a little under the crosshair; on
# the irons the rear sight sits far enough from the eye not to fill the view
RIFLE_FP = FirstPerson(hip_grip=(0.25, -0.30, -0.58), hip_turn=(2.0, 8.0, -3.0), hip_scale=0.85,
                       reload={'mag_out': (28, 0.06), 'mag_in': (28, 0.06), 'charge': (-14, 0.03)},
                       irons=(0.0, 0.0, -4.2), irons_scale=0.72, handle_travel=RF.HANDLE_TRAVEL,
                       charge_turn=(20, 25, 0), charge_push=(-0.08, 0.10, -0.30))
# the pistol held out in both hands, low and a little right; on the sights the slide's back is a hand's width off
_dz = (PI.GRIP_BOT[0] - PI.GRIP_TOP[0]) / (PI.GRIP_BOT[1] - PI.GRIP_TOP[1])
PISTOL_FP = FirstPerson(hip_grip=(0.17, -0.30, -0.50), hip_turn=(1.0, 5.0, -2.0), hip_scale=1.05,
                        reload={'mag_out': (30, 0.05), 'mag_in': (30, 0.05), 'charge': (-16, 0.02)},
                        irons=(0.0, 0.0, -6.0), irons_scale=1.0, handle_travel=PI.HANDLE_TRAVEL,
                        charge_turn=(10, 18, 0), charge_push=(-0.04, 0.07, -0.14),
                        locks_back=True, cycles=True, support_grip=True, mag_axis=(0.0, -1.0, -_dz))

# Red dots and holos are drawn bigger and closer than life: the eye sits under two inches behind the back of
# the sight, so its window fills a good part of the view. Inches from the sight point to the eye (the back
# face's distance plus the eye relief the sight was shaped for), and the scale they're drawn at. Through the
# scope the gun isn't drawn at all: the game draws the scope picture over the screen.
ADS_EYE = {'red_dot': 0.85 + AT.RED_DOT_EYE, 'holo': 0.20 + AT.HOLO_EYE}
ADS_SCALE = 1.2
HIDDEN_WHEN_AIMING = ('scope_4x',)


def ads_eyes(gun, fp):
    """Where the eye is when aiming through each sight, in the build frame (the sight point, then straight
    back toward the stock): the faces these see up close get finer textures."""
    eyes = [gun.sight_point(None) + np.array([0, 0, -fp.irons[2] / fp.irons_scale])]
    for optic, inches in ADS_EYE.items():
        if optic in gun.parts:
            eyes.append(gun.sight_point(optic) + np.array([0, 0, inches * IN]))
    return eyes


def gun_displays(g, holds, fp):
    """Display transforms for each pose: third person from the holds, first person at the hip (canted for
    the reload steps) or with the sight on the eye line, and the inventory, ground and frame views."""
    def disp(state, fit, sight):
        ads = state.startswith('ads')
        cant, drop = fp.reload.get(state, (0.0, 0.0))
        d = {}
        h, G, arm = holds['aim' if ads else 'ready']
        d['thirdperson_righthand'] = Q.display_for(h, G, arm, g, S3)
        armv = np.array([0.56, -0.52, -0.72]) * 16
        S = fp.irons_scale
        optic = state[4:] or None
        if ads and optic in HIDDEN_WHEN_AIMING:
            d['firstperson_righthand'] = M.transform([0, 0, 0], [0, 0, 0], 0)
        elif ads and optic in ADS_EYE:
            Sa = ADS_SCALE
            T = np.array([0.0, 0.0, -ADS_EYE[optic] * IN * Sa]) - armv - Sa * (np.asarray(sight) - C)
            d['firstperson_righthand'] = M.transform([0, 0, 0], T, Sa)
        elif ads:
            T = fp.irons - armv - S * (np.asarray(sight) - C)
            d['firstperson_righthand'] = M.transform([0, 0, 0], T, S)
        else:
            ht = fp.hip_turn
            R = M.R_xyz(ht[0] + cant * 0.6, ht[1] + cant * 0.3, ht[2] - cant)
            target = (np.array(fp.hip_grip) + [0, -drop, 0]) * 16
            T = target - armv - fp.hip_scale * (R @ (g - C))
            d['firstperson_righthand'] = M.transform(M.euler_xyz(R), T, fp.hip_scale)
        Rg = M.R_xyz(12, 0, 0) @ M.R_xyz(0, -90 - 22, 0)
        s2, t2 = M.fit(fit, Rg, 15.6)
        d['gui'] = M.transform(M.euler_xyz(Rg), t2, s2)
        Rgr = M.R_xyz(0, -90, 0)
        s3, t3 = M.fit(fit, Rgr, 9.0, (0, 2, 0))
        d['ground'] = M.transform(M.euler_xyz(Rgr), t3, s3)
        Rf = M.R_xyz(0, 90, 0)
        s4, t4 = M.fit(fit, Rf, 15.5)
        d['fixed'] = M.transform(M.euler_xyz(Rf), t4, s4)
        return d
    return disp


def build_ammo_box(m):
    m.box([-2.6, 0, -1.4], [2.6, 3.0, 1.4], 'carton')
    m.box([-2.65, 2.85, -1.45], [2.65, 3.1, 1.45], 'carton')


def carton(f, colour='#4b5a37'):
    c = P.plain(f, colour, 0.18)
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


def paint_melee(m, lk, wear, seed, kinds):
    """Paints every visible face like model.paint_model, plus its LabPBR _s and _n patches. Each finishing pass
    is first run on flat grey (with the same random draws) to see where it lands."""
    rng = np.random.default_rng(seed)
    patches = []
    off = m.offset
    old, M.PX = M.PX, MELEE_PX
    try:
        for i, p in enumerate(m.parts):
            local = dict(p, lo=p['lo'] - off, hi=p['hi'] - off)
            for name, (ax, sgn, ua, ud, va, vd) in M.FACES.items():
                if name in p.get('skip', ()):
                    continue
                pos, n, edge, uv = M.face_grid(local, name)
                f = P.Face(pos, n, edge, local, rng, wear, seed + i, uv)
                base = P.round_shade(f, lk[p['mat']](f))
                rgb, marks = base, {}
                for fx in lk.get('_after', []):
                    tag = getattr(fx, 'tag', None)
                    if tag:
                        state = rng.bit_generator.state
                        grey = np.full(rgb.shape, 0.5)
                        moved = np.linalg.norm(fx(f, grey) - grey, axis=-1)
                        rng.bit_generator.state = state
                        if tag == 'grime':
                            marks[tag] = np.clip(moved / (0.5 * np.linalg.norm(1 - P.col('#4a4136') * 1.4)), 0, 1)
                        else:
                            marks[tag] = moved > 0.02
                    rgb = fx(f, rgb)
                rgb = np.clip(rgb, 0, 1)
                lu, lv = local['hi'][ua] - local['lo'][ua], local['hi'][va] - local['lo'][va]
                sp, nm = pbr.melee_layers(kinds[p['mat']], f, base, rgb, wear, marks, lu, lv, M.PX)
                patches.append((i, name, dict(rgb=rgb, s=sp, n=nm)))
    finally:
        M.PX = old
    return patches


def write_texture(m, lk, wear, seed, name):
    patches = paint_melee(m, lk, wear, seed, MELEE_PBR[m.name])
    img, uvs, T = kit.pack(patches)
    kit.save_textures(img, ASSETS / f'textures/item/weapon/{name}.png')
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
    mh, mG, mkeys = melee_holds()
    for spec, build, mats in WEAPONS:
        m = M.Model(spec.name)
        build(m)
        lo, hi = m.bounds()
        reach = (lo[1] + (hi[1] - lo[1]) * 0.55, hi[1])
        m.shift([8, gy, 8])
        m.tidy()
        disp = melee_display(m)
        if spec.name in TWO_HAND_GRIPS:
            disp['thirdperson_righthand'] = Q.display_for(mh, mG, mkeys['ready'], [8, gy, 8], S3M)
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

    # the modular guns and their parts
    rifle = assault_rifle()
    _Pm, g = rifle_frame()
    holds = rifle_holds()
    line, _ = kit.write_gun(rifle, ASSETS, g - rifle.hand, gun_displays(g, holds, RIFLE_FP), lang, ads_eyes(rifle, RIFLE_FP))
    summary.append(line)
    pistol_gun = pistol()
    gp = centred_grip(pistol_gun)
    pistol_hold = pistol_holds()
    line, _ = kit.write_gun(pistol_gun, ASSETS, gp - pistol_gun.hand, gun_displays(gp, pistol_hold, PISTOL_FP), lang,
                            ads_eyes(pistol_gun, PISTOL_FP))
    summary.append(line)
    part_display = lambda m: small_display(m, (20, 60, 0))
    kit.write_part_items(list(rifle.parts.values()), ASSETS, part_display, lang)
    # the light and laser are the rifle's own items (drawn compact on the pistol); the rest are the pistol's
    own = [p for p in pistol_gun.parts.values() if p.item not in {q.item for q in rifle.parts.values()}]
    kit.write_part_items(own, ASSETS, part_display, lang, file=lambda p: p.item)
    kit.write_ghost_texture(ASSETS)
    kit.write_flash_texture(ASSETS)
    AT.scope_overlay(ASSETS / 'textures/gui/scope_4x.png')
    write_slot_java([rifle, pistol_gun])
    write_rigs_java([(rifle, g, holds, RIFLE_FP, rifle_points(rifle, g)),
                     (pistol_gun, gp, pistol_hold, PISTOL_FP, pistol_points(pistol_gun, gp))])

    for name, title, colour, seed in (('rifle_ammo', '5.56mm Rounds', '#4b5a37', 558),
                                      ('pistol_ammo', '9mm Rounds', '#5a3b2e', 559)):
        box = M.Model(name)
        build_ammo_box(box)
        lo, hi = box.bounds()
        box.shift(C - (lo + hi) / 2)
        box.tidy()
        lk = {'carton': lambda f, c=colour: carton(f, c)}
        uvs, T = write_texture(box, lk, 0.3, seed, name)
        M.dump(M.model_json(box, uvs, f'{NS}:item/weapon/{name}', small_display(box)),
               ASSETS / f'models/item/weapon/{name}.json')
        item_def(name, ref(name))
        lang[f'item.{NS}.{name}'] = title

    merge_lang(lang)
    write_java()
    pose_data = write_pose_java({'rifle': holds, 'pistol': pistol_hold}, (mh, mG, mkeys))
    print('\n'.join(summary))
    if preview:
        write_preview(preview)
        (preview / 'poses.json').write_text(json.dumps(pose_data, indent=1))


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


def write_slot_java(guns):
    """GunSlotData.java: which parts (by item id) go in which slot of which gun, what a gun comes with when
    nothing says otherwise, and the model key each part's item draws as."""
    def entries(pairs, indent='        '):
        return ',\n'.join(f'{indent}Map.entry({k}, {v})' for k, v in pairs)

    fits, defaults, keys = [], [], {}
    for gun in guns:
        slots, comes_with = [], []
        for name in kit.SLOTS:
            sl = gun.slots.get(name)
            if not sl:
                continue
            items = ', '.join(f'"{gun.parts[k].item}"' for k in sl.fits)
            slots.append((f'Attachments.Slot.{name.upper()}', f'List.of({items})'))
            if sl.default and name != 'magazine':      # the magazine's state lives in GunState
                comes_with.append((f'Attachments.Slot.{name.upper()}', f'"{gun.parts[sl.default].item}"'))
        defaults.append((f'"{gun.name}"', 'Map.ofEntries(' + ', '.join(f'Map.entry({a}, {b})' for a, b in comes_with) + ')'))
        fits.append((f'"{gun.name}"', 'Map.ofEntries(\n' + entries(slots, '                ') + ')'))
        for k, part in gun.parts.items():
            keys[f'"{part.item}"'] = f'"{k}"'
    SLOT_JAVA.write_text(f'''package com.zomboidcraft.combat;

import java.util.List;
import java.util.Map;

/**
 * Which parts fit which gun, slot by slot, by item id; the parts a gun has when its stack says nothing
 * (found guns, and guns from before attachments); and the model key each part's item draws as.
 * Generated by tools/weapons/weapons.py from the guns' slot lists: edit them there.
 */
public final class GunSlotData {{
    public static final Map<String, Map<Attachments.Slot, List<String>>> FITS = Map.ofEntries(
{entries(fits)});

    public static final Map<String, Map<Attachments.Slot, String>> DEFAULTS = Map.ofEntries(
{entries(defaults)});

    public static final Map<String, String> MODEL_KEYS = Map.ofEntries(
{entries(keys.items())});

    private GunSlotData() {{
    }}
}}
''')


def write_pose_java(guns, melee):
    """HoldPoseData.java: gun-arm angles and the support lines the off hand reaches for, in the gun arm's frame."""
    def f3(v):
        return ', '.join(f'{float(x):.5f}f' for x in v)

    data = {'arms': {}, 'support': {}, 'reach': Q.REACH}
    for gun, holds in guns.items():
        for name in ('ready', 'aim'):
            h, G, arm = holds[name]
            data['arms'][f'{gun}_{name}'] = [float(x) for x in arm]
            data['support'][f'{gun}_{name}'] = [float(x) for b in h.support for x in Q.arm_local(h, G, arm, b)]
    mh, mG, keys = melee
    for k, arm in keys.items():
        data['arms']['melee_' + k] = [float(x) for x in arm]
    for name, (y0, y1) in TWO_HAND_GRIPS.items():
        data['support'][name] = [float(x) for b in ((0, y0, 0), (0, y1, 0))
                                 for x in Q.arm_local(mh, mG, keys['ready'], b)]
    arms = '\n'.join(f'    static final float[] {k.upper()} = {{{f3(v)}}};' for k, v in data['arms'].items())
    held = tuple(f'{gun}_' for gun in guns)
    rifle_sup = '\n'.join(f'    static final float[] {k.upper()}_SUPPORT = {{{f3(v)}}};'
                           for k, v in data['support'].items() if k.startswith(held))
    melee_sup = ',\n'.join(f'            "{k}", new float[] {{{f3(v)}}}' for k, v in data['support'].items()
                            if not k.startswith(held))
    POSE_JAVA.parent.mkdir(parents=True, exist_ok=True)
    POSE_JAVA.write_text(f'''package com.zomboidcraft.client.combat;

import java.util.Map;

/**
 * Third-person holds. Generated by tools/weapons/weapons.py (poses.py): edit the poses there, since the
 * weapons' item models are fitted to these same arm angles.
 * Arms: gun-arm angles (x, y, z radians, the order ModelPart turns in). Supports: the line the off hand
 * reaches for (x0, y0, z0, x1, y1, z1), in the gun arm's own frame, in model pixels.
 */
final class HoldPoseData {{
    static final float REACH = {Q.REACH}f;
{arms}
{rifle_sup}
    /** Two-handed melee weapons and where the off hand holds the handle. */
    static final Map<String, float[]> MELEE_SUPPORT = Map.of(
{melee_sup});

    private HoldPoseData() {{
    }}
}}
''')
    return data


def centred_grip(gun):
    """Where a compact gun's grip goes in the item model so the whole gun sits round the model's centre."""
    models = [kit.drawn('body', gun.body)]
    if gun.handle:
        models.append(kit.drawn('handle', gun.handle))
    for s in gun.slots.values():
        if s.default:
            R, t = gun.mount(s.default)
            models.append(kit.drawn(s.default, gun.parts[s.default].build, R, t))
    fit = kit.shifted_model(models, np.zeros(3))
    lo, hi = fit.bounds()
    return C + gun.hand - (lo + hi) / 2


def mag_points(gun, shift):
    """The loose magazine's centre, and where the off hand holds a new one: low on its body (item pixels)."""
    key = gun.slots['magazine'].default
    R, t = gun.mount(key)
    mag = kit.drawn(key, gun.parts[key].build, R, t)
    lo, hi = mag.bounds()
    lo, hi = lo + shift, hi + shift
    band = lo[1] + (hi[1] - lo[1]) * np.array([0.25, 0.45])
    zs = [((p['lo'][2] + p['hi'][2]) / 2 + shift[2]) for p in mag.parts
          if p['lo'][1] + shift[1] < band[1] and p['hi'][1] + shift[1] > band[0]]
    return (lo + hi) / 2, np.array([8.0, band.mean(), float(np.mean(zs))])


def lens_point(gun, shift):
    """The front of the laser on the side rail, where its beam starts."""
    R, t = gun.mount('laser')
    llo, lhi = kit.drawn('laser', gun.parts['laser'].build, R, t).bounds()
    return np.array([(llo[0] + lhi[0]) / 2, (llo[1] + lhi[1]) / 2, llo[2]]) + shift


def rifle_points(gun, g):
    shift = g - gun.hand
    fp = RF.frame_point
    centre, hold = mag_points(gun, shift)
    R, t = gun.mount('foregrip')
    flo, fhi = kit.drawn('foregrip', gun.parts['foregrip'].build, R, t).bounds()
    flo, fhi = flo + shift, fhi + shift
    return {
        'grip': g,
        # the off hand cups the handguard from below: its centre a little under the rail
        'support': fp(0, -RF.RAIL, -6.4) + shift + np.array([0, -0.8, 0]),
        'foregrip': np.array([8.0, flo[1] + (fhi[1] - flo[1]) * 0.45, (flo[2] + fhi[2]) / 2]),
        'magTop': gun.slots['magazine'].at + shift,
        'magHold': hold,
        'magCentre': centre,
        'handle': fp(-0.75, 0.67, RF.UPPER_LEN + 0.25) + shift,
        'laser': lens_point(gun, shift),
        'muzzle': kit.flash_tip(gun) + shift,
    }


def pistol_points(gun, g):
    shift = g - gun.hand
    fp = PI.frame_point
    centre, _ = mag_points(gun, shift)
    key = gun.slots['magazine'].default
    R, t = gun.mount(key)
    mlo, mhi = kit.drawn(key, gun.parts[key].build, R, t).bounds()
    # the palm under the base plate: it pushes the magazine up the grip and slaps it home
    hold = np.array([8.0, mlo[1] + shift[1] - 0.6, (mlo[2] + mhi[2]) / 2 + shift[2] + 0.4])
    support = fp(-1.0, -2.9, 5.7) + shift     # the off hand wrapped round the shooting hand's fingers
    return {
        'grip': g,
        'support': support,
        'foregrip': support,
        'magTop': gun.slots['magazine'].at + shift,
        'magHold': hold,
        'magCentre': centre,
        # racking: fingers and thumb pinch the serrations at the back of the slide, from above
        'handle': fp(-0.30, PI.SLIDE_TOP + 0.30, PI.SLIDE_LEN - 0.55) + shift,
        'laser': lens_point(gun, shift),
        'muzzle': kit.flash_tip(gun) + shift,
    }


def write_rigs_java(rigs):
    """GunRigs.java: what first person needs to pose each gun itself (the hip pose's numbers, so a reload can
    cant it smoothly, and each sight's aiming pose) and to put hands on it, in item model pixels."""
    def v3(v):
        return 'new float[] {' + ', '.join(f'{float(x):.4f}f' for x in v) + '}'

    blocks = []
    for gun, g, holds, fp, points in rigs:
        shift = g - gun.hand
        fit = kit.shifted_model([kit.drawn('body', gun.body)], shift)
        disp = gun_displays(g, holds, fp)
        ads = {}
        for optic in [None] + [k for k in gun.optics() if k not in HIDDEN_WHEN_AIMING]:
            st = 'ads' if optic is None else f'ads_{optic}'
            d = disp(st, fit, gun.sight_point(optic) + shift)['firstperson_righthand']
            ads[optic or 'irons'] = v3(list(d['translation']) + [d['scale'][0]])
        sights = ', '.join(f'"{k}", {v}' for k, v in ads.items())
        args = [
            (v3(fp.hip_grip), 'hip: the grip in the hand camera (blocks)'),
            (v3(fp.hip_turn), 'hip: muzzle up, muzzle in, roll (degrees)'),
            (f'{fp.hip_scale}f', 'hip scale'),
            (v3(fp.reload['mag_out']), 'magazine steps: cant (degrees), drop (blocks)'),
            (v3(fp.reload['charge']), 'charging step: cant, drop'),
            (f'Map.of({sights})', 'aiming: translation (pixels) and scale, by sight'),
            (f'{fp.handle_travel * IN:.4f}f', 'how far the handle or slide comes back (pixels)'),
            (v3(fp.charge_turn), 'worked: turned about the grip (degrees)'),
            (v3(fp.charge_push), 'worked: pushed (blocks)'),
        ] + [(v3(v), k) for k, v in points.items()] + [
            (v3(fp.mag_axis), 'the way a magazine leaves the well'),
            (str(fp.locks_back).lower(), 'locks back when empty'),
            (str(fp.cycles).lower(), 'cycles with each shot'),
            (str(fp.support_grip).lower(), 'the off hand wraps the grip'),
        ]
        lines = ',\n'.join(f'            {a} /* {c} */' for a, c in args)
        blocks.append(f'            Map.entry("{gun.name}", new GunRig(\n{lines}))')
    RIG_JAVA.write_text(f'''package com.zomboidcraft.client.combat;

import java.util.Map;

/**
 * Each gun's first-person rig (see {{@link GunRig}}). Generated by tools/weapons/weapons.py: the item models'
 * first-person poses are made from these same numbers, so edit them there. Points are in item model pixels,
 * before the display transform.
 */
final class GunRigs {{
    /** Vanilla's arm offset, which the hand camera's numbers leave out. */
    static final float[] ARM = {{0.56f, -0.52f, -0.72f}};

    static final Map<String, GunRig> ALL = Map.ofEntries(
{',\n'.join(blocks)});

    private GunRigs() {{
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
        'rifle': gun_models('assault_rifle'), 'pistol': gun_models('pistol'),
        'other': ['rifle_magazine', 'rifle_ammo', 'pistol_ammo'], 'all': names}, indent=1))


def gun_models(name):
    return sorted(str(p.relative_to(ASSETS / 'models/item/weapon').with_suffix(''))
                  for p in (ASSETS / f'models/item/weapon/{name}').rglob('*.json'))


if __name__ == '__main__':
    main()
