"""The sawn-off: the side-by-side double cut down, its barrels sawn off at 12.5" and its stock cut away behind the
wrist and shaped into a pistol grip, about 20" overall. Short enough to swing indoors and spread wide.

It's the double's own action, triggers, top lever and forend (drawn by a private copy of double_barrel, so nothing
here touches the full-length gun), with new barrels and a new grip. Cut barrels keep the taper they had at that
point, so they end thicker than the double's muzzles; the choke went with the cut, the faces were filed square (a
bright ring of bare steel round each bore) and a bead put back on the rib. The grip is the wrist and the front of
the old semi-pistol grip, sawn down at the grip's own rake and rounded over. Measurements are inches from the bore
line (y) and the breech face (z), as on the double.

It breaks open about the same hinge to load two shells by hand.
"""
import importlib.util
import os

import numpy as np

import finishes as F
import paint as P
from inch import IN, Inch

_spec = importlib.util.spec_from_file_location('_sawn_double', os.path.join(os.path.dirname(__file__), 'double_barrel.py'))
D = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(D)

BORE_Y, Z0 = D.BORE_Y, D.Z0
BARREL_LEN = 12.5
MUZZLE = -BARREL_LEN
FULL_MUZZLE = D.MUZZLE          # where the barrels ran to before the cut: the taper is the double's
BEAD_Z = MUZZLE + 0.45
BEAD_R = D.BEAD_R
HINGE = D.HINGE
OPEN = D.OPEN
FOREND = D.FOREND               # the splinter forend stays: 1.5" of barrel shows past its tip
TRIGGERS = D.TRIGGERS
STANDING_TOP = D.STANDING_TOP

# the grip: its front and back lean back 22.5 degrees from upright, like the old grip's front
RAKE = np.tan(np.radians(22.5))
_D = np.array([np.sin(np.radians(22.5)), -np.cos(np.radians(22.5))])   # down the grip, as (z, y)
_NB = np.array([-_D[1], _D[0]])                                   # square to it, toward the back
WRIST_TOP = 0.34                # the wrist's top, level off the tang
FRONT = (4.75, -1.77)           # a point on the grip's front, just behind the guard
GRIP_W = 1.80                   # front to back, square to the rake
GRIP_HALF = 0.60                # half the grip's width
FOOT_Y = -4.30                  # the front corner of the sawn foot
HUMP_R = 0.80                   # the round over the web of the hand, where the wrist's top turns down the back
FILLET_R = 0.35                 # the round where the wrist's underside turns down the grip's front


def _front(z):
    return FRONT[1] - (z - FRONT[0]) / RAKE


_BACK_PT = np.array(FRONT) + _NB * GRIP_W


def _back(z):
    return _BACK_PT[1] - (z - _BACK_PT[0]) / RAKE


# the hump: tangent to the wrist's top and to the back; the fillet: tangent to the action's underside and the front
_turn = np.radians(67.5)
_corner_z = _BACK_PT[0] + (_BACK_PT[1] - WRIST_TOP) * RAKE
_tl = HUMP_R * np.tan(_turn / 2)
HUMP = (_corner_z - _tl, WRIST_TOP - HUMP_R)                          # its centre
HUMP_END = np.array([_corner_z, WRIST_TOP]) + _D * _tl                 # where it meets the back
_fz = FRONT[0] + (FRONT[1] - D.ACT_BOT) * RAKE
_fl = FILLET_R / np.tan((np.pi - _turn) / 2)
FILLET = (_fz - _fl, D.ACT_BOT - FILLET_R)
FILLET_END = np.array([_fz, D.ACT_BOT]) + _D * _fl
# the grip bar's ends: its top square to the rake from where the hump meets the back, its foot from FOOT_Y
GRIP_TOP = tuple(HUMP_END - _NB * GRIP_W / 2)
_foot_front = np.array([FRONT[0] + (FRONT[1] - FOOT_Y) * RAKE, FOOT_Y])
GRIP_FOOT = tuple(_foot_front + _NB * GRIP_W / 2)
STOCK_END = float(_foot_front[0] + _NB[0] * GRIP_W)                    # the back corner of the foot


def frame_point(x, y, z):
    return D.frame_point(x, y, z)


def _grip_y(t):
    """The grip's centre line at a fraction t from its top (0) to its foot (1), as (z, y)."""
    return tuple(np.array(GRIP_TOP) + (np.array(GRIP_FOOT) - np.array(GRIP_TOP)) * t)


HAND = frame_point(0.0, -2.30, GRIP_TOP[0] + (GRIP_TOP[1] + 2.30) * RAKE)   # middle of the shooting hand
SUPPORT = frame_point(0.0, -1.30, -6.80)                    # the off hand under the forend, as on the double
HINGE_POINT = D.HINGE_POINT


def barrel_r(z):
    """One barrel's outside radius: the double's, chamber and taper, cut short."""
    return D.barrel_r(z)


def rib_top(z):
    return barrel_r(z) + 0.05


SIGHT = D.SIGHT
SIGHT_Z = D.SIGHT_Z
BEAD_TOP = rib_top(BEAD_Z) + BEAD_R * 1.8
LOOSE_SHELL = D.LOOSE_SHELL
LOOSE_GAP = D.LOOSE_GAP


def _stock_line(z):
    """The wrist's top and bottom at z: the top level off the tang, then over the hump into the grip's back; the
    bottom the action's underside, then round the fillet into the grip's front (inside the grip bar from there)."""
    hz, hy = HUMP
    if z <= hz:
        top = WRIST_TOP
    elif z < HUMP_END[0]:
        top = hy + np.sqrt(max(HUMP_R ** 2 - (z - hz) ** 2, 0.0))
    else:
        top = _back(z)
    fz, fy = FILLET
    if z <= fz:
        bot = D.ACT_BOT
    elif z < FILLET_END[0]:
        bot = fy + np.sqrt(max(FILLET_R ** 2 - (z - fz) ** 2, 0.0))
    else:
        bot = _front(z)
    return top, bot


def _grip_top_face(z):
    """The grip bar's top end, square to the rake: the wrist's slices need only reach down into it."""
    return GRIP_TOP[1] + (z - GRIP_TOP[0]) * RAKE


def _stock_half(z):
    return 0.80 - 0.19 * np.clip((z - D.STOCK_START) / (4.4 - D.STOCK_START), 0, 1)


def _stock(b):
    """The walnut: the wrist in slices off the action (finest where the hump and fillet turn), then the grip as two bars
    along its rake (the wider for its sides, the narrower a touch proud front and back so the edges read rounded), with
    a flat of lighter end grain on the sawn foot."""
    st = 'stock'
    z = D.STOCK_START
    while z < HUMP_END[0] - 1e-6:
        e = 0.01
        slope = max(abs(_stock_line(z + e)[i] - _stock_line(z)[i]) / e for i in (0, 1))
        z1 = min(z + float(np.clip(0.05 / max(slope, 1e-3), 0.04, 0.40)), float(HUMP_END[0]))
        top = max(_stock_line(z)[0], _stock_line(z1)[0]) if z1 <= HUMP[0] else min(_stock_line(z)[0], _stock_line(z1)[0])
        if z1 <= FILLET_END[0]:
            bot = max(_stock_line(z)[1], _stock_line(z1)[1])      # never below the fillet
        else:
            bot = max(_front(z) + 0.03, _grip_top_face(z) - 0.15)
        half = _stock_half((z + z1) / 2)
        b.box((-half, bot, z), (half, top, z1), st, axis=2, seam=(2,))
        b.box((-half + 0.10, bot - (0.05 if z1 <= FILLET[0] else 0.0), z), (half - 0.10, top + 0.03, z1), st,
              axis=2, seam=(2,))
        z = z1
    b.seg(GRIP_TOP, GRIP_FOOT, GRIP_W, 2 * GRIP_HALF, st, over=0.0)
    b.seg(GRIP_TOP, GRIP_FOOT, GRIP_W + 0.08, 2 * GRIP_HALF - 0.18, st, over=0.0)
    # the sawn foot: a flat of end grain square to the rake, just proud of the grip's end
    p0 = np.array(GRIP_FOOT) - _D * 0.02
    b.seg(tuple(p0), tuple(p0 + _D * 0.05), GRIP_W - 0.16, 2 * GRIP_HALF - 0.16, 'endgrain', over=0.0)
    # checkering either side of the grip, leaned along its rake
    cz, cy = _grip_y(0.52)
    c = b.p(0, cy, cz)
    for s in (-1, 1):
        x0, x1 = sorted((s * GRIP_HALF, s * (GRIP_HALF + 0.012)))
        b.m.box(b.p(x0, cy - 1.10, cz - 0.52), b.p(x1, cy + 1.10, cz + 0.52), 'gcheck', axis=2,
                rot=dict(axis='x', angle=-22.5, origin=[float(c[0]), float(c[1]), float(c[2])]))


def _guard(b):
    """The double's guard bow, its tang cut short to run a little way down the grip's front."""
    zc, yc, az, ay = 2.75, -1.80, 1.80, 0.76
    angles = np.radians([168, 192, 218, 246, 270, 296, 322, 346, 362])
    pts = [(zc + az * np.cos(a), yc + ay * np.sin(a)) for a in angles]
    for p0, p1 in zip(pts[:-1], pts[1:]):
        b.seg(p0, p1, 0.20, 0.30, 'guard', over=0.06)
    b.box((-0.15, D.ACT_BOT - 0.12 - 0.12, pts[0][0] - 0.10), (0.15, D.ACT_BOT - 0.12, pts[0][0] + 0.18), 'guard')
    nf = -_NB * 0.04
    tang = [pts[-1]] + [tuple(np.array([FRONT[0] + (FRONT[1] - y) * RAKE, y]) + nf) for y in (-1.95, -2.55)]
    for p0, p1 in zip(tang[:-1], tang[1:]):
        b.seg(p0, p1, 0.10, 0.34, 'guard', over=0.05)


D._stock_line = _stock_line
D._stock_half = _stock_half
D._stock = _stock
D._guard = _guard


def body(m):
    """The double's action, top lever, safety, trigger plate, guard and triggers, and the cut-down grip."""
    D.body(m)


def barrels(m):
    """The moving part: the cut barrels with their rib and refitted bead, the lumps and extractor, and the forend
    with its iron. It swings down OPEN degrees about HINGE to load."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    bar = 'barrel'
    steps = [0.0, -D.CHAMBER, -4.4, -6.0, -9.0, MUZZLE]
    for z1, z0 in zip(steps[:-1], steps[1:]):
        r = barrel_r((z0 + z1) / 2)
        for s in (-1, 1):
            b.rod(z0, z1, r, bar, axis=2, at=(s * r, 0.0))
        b.box((-D.RIB_W, 0.0, z0), (D.RIB_W, rib_top((z0 + z1) / 2), z1), 'rib')
        b.box((-D.RIB_W, -r + 0.12, z0), (D.RIB_W, 0.0, z1), 'rib')
    b.box((-0.045, rib_top(-6) - 0.002, MUZZLE + 0.2), (0.045, rib_top(-6) + 0.004, -0.3), 'line')
    rm = barrel_r(MUZZLE - 1.0)
    for s in (-1, 1):
        b.rod(MUZZLE - 0.012, MUZZLE + 0.03, rm - 0.02, 'cut', axis=2, at=(s * rm, 0.0))     # the filed faces
        b.rod(MUZZLE - 0.02, MUZZLE + 0.04, 0.37, 'bore', axis=2, at=(s * rm, 0.0))
        b.rod(-0.02, 0.004, 0.405, 'chamber', axis=2, at=(s * barrel_r(0), 0.0))
    b.rod(BEAD_Z - BEAD_R, BEAD_Z + BEAD_R, BEAD_R, 'bead', axis=2, at=(0.0, rib_top(BEAD_Z) + BEAD_R * 0.8))
    hy, hz = HINGE
    b.box((-0.34, D.BAR_TOP + 0.02, hz + 0.25), (0.34, -0.40, -0.25), 'lump')
    b.box((-0.34, hy + 0.30, hz + 0.25), (0.34, D.BAR_TOP + 0.02, hz + 0.85), 'lump')
    b.box((-0.22, -0.28, -0.02), (0.22, 0.36, 0.006), 'extractor')
    f0, f1 = FOREND
    z = f0
    while z < f1:
        z1 = min(z + 0.80, f1)
        u = (z - f0) / (f1 - f0)
        r = barrel_r((z + z1) / 2)
        half = 0.84 + 0.12 * u
        bot = -r - 0.48 - 0.08 * u
        b.box((-half, bot + 0.16, z), (half, -0.12, z1), 'forend', axis=2)
        b.box((-half + 0.14, bot, z), (half - 0.14, -0.12, z1), 'forend', axis=2)
        z = z1
    b.box((-0.66, -0.95, f0 - 0.18), (0.66, -0.25, f0), 'forend', axis=2)
    b.rod(f0 - 0.24, f0 - 0.17, 0.08, 'steel', axis=2, at=(0.0, -0.62))
    for s in (-1, 1):
        x0, x1 = sorted((s * (0.84 + 0.12 * 0.62), s * (0.85 + 0.12 * 0.62)))
        b.box((x0, -0.95, -9.6), (x1, -0.30, -4.6), 'fcheck', axis=2)
    b.box((-0.58, -1.12, f1 - 0.65), (0.58, -0.90, f1), 'iron')
    b.box((-0.30, -1.02, f1), (0.30, -0.70, f1 + 0.10), 'iron')


def flash(m, z_tip, r=1.0):
    """The flash from a sawn-off is wide and short: unburnt powder that a full barrel would have used."""
    b = Inch(m, (barrel_r(MUZZLE) * IN, BORE_Y, Z0))
    L = 1.7 * r
    b.box((-0.02, -1.5 * r, z_tip - L), (0.02, 1.5 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.5 * r, -0.02, z_tip - L), (1.5 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.45 * r, -0.45 * r, z_tip - 0.8 * r), (0.45 * r, 0.45 * r, z_tip), 'flash', light=15, noshade=True)


flash_paint = D.flash_paint


def look(wear=0.4):
    """The double's field finish, with the filed muzzle faces bare and the sawn end grain."""
    d = D.look('field', wear)

    def cut(f):
        return P.steel(f, 0.62, tint='#b9bec4', spread=0.05)

    def endgrain(f):
        return F.walnut(f, 'field', 'x') * 1.12

    d['cut'] = cut
    d['endgrain'] = endgrain
    d['_spec'] = dict(d['_spec'], cut=F.METAL_SPEC['blued'], endgrain=F.WOOD_SPEC['satin'] + (12,))
    return d
