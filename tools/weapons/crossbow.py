"""The crossbow: a compound hunting crossbow (a 185 lb, 370 fps sort), drawn from the real thing's measurements: 38"
overall with its stock in, 14.5" axle to axle cocked, a 13" power stroke, split limbs on a cast riser with a foot
stirrup in front, an aluminium flight rail, a trigger box with an ambidextrous safety and a dry-fire latch, a
pistol grip with a guard, a foregrip with finger flanges, and an adjustable stock on a buffer tube. It shoots 20"
carbon bolts (400 grains with a 100 grain field point): two red hen vanes and a white cock vane that rides in the
rail's groove, and an orange flat nock. It's silent but for the slap of the string, and its bolts can be pulled out
and shot again.

Measurements are inches from the bolt's centre line as it lies on the rail (y) and the latch that holds the string
(z, positive back toward the shooter). The limbs bend in the top (x-z) plane, so they're drawn there, 45 degrees
back from square; the cocked string runs from each cam's inside back edge to the latch at 22.5 degrees.

It's drawn cocked with the bolt on, the bolt being its magazine part (one at a time, laid on the rail under the
retention spring). Iron sights (a fibre post on the riser, a notch with two fibre dots on the trigger box) with a
short rail between them for an optic.
"""
import math

import numpy as np

import paint as P
from inch import IN, Inch, picatinny

BORE_Y = 2.2            # where the bolt's line sits in the build frame
Z0 = -4.0               # where the latch sits

RAIL_TOP = -0.17        # the rail's top, the bolt's shaft lying on it
RAIL_HALF = 0.62
RAIL_BOT = -1.30
GROOVE = 0.09           # half the flight groove's width, the cock vane running in it
GROOVE_BOT = -0.62
RISER = (-20.7, -18.0)  # the cast riser's front and back
RISER_HALF = 1.40
RISER_TOP = 1.05
RISER_BOT = -1.55
ROOT = (1.76, -19.50)   # where each limb leaves its pocket (x, z)
CAM = (7.26, -14.00)    # each cam's axle, cocked: 14.5" axle to axle
LIMB_Y = 0.80           # the split limbs' centres, above and below the cam
LIMB_T = 0.62           # each limb's height
LIMB_W = 0.40           # and its thickness, the way it bends
CAM_R = 1.35
CAM_T = 0.40
STRING_Y = -0.04
STRING_ANGLE = 22.5     # the cocked string's angle off the rail, each side
BOX = (-1.60, 4.40)     # the trigger box's front and back
BOX_HALF = 0.95
BOX_TOP = 0.85
BOX_BOT = -1.55
CHANNEL = 0.30          # half the box's front channel, where the bolt's nock and the string go in
RAIL_Z = (-1.30, 3.30)  # the optic rail on the box's top
SIGHT = 1.50            # the irons' line over the bolt
FRONT_SIGHT = -19.30
REAR_SIGHT = 4.15
FOREGRIP = (-12.40, -4.60)
GUARD = (0.55, 3.70)
TRIGGER_Z = 2.45
GRIP_TOP = (3.95, -1.40)  # the pistol grip's centre line at the top
GRIP_DROP = 3.95
TUBE_Y = -0.35          # the buffer tube's axis
TUBE_R = 0.58
TUBE_END = 13.00
STOCK = (9.30, 14.60)   # the stock's front and its back, where the pad goes on
STOCK_END = 15.40
BOLT_LEN = 20.0
BOLT_R = 0.17
VANES = (-5.0, -1.0)



def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


RAKE = math.tan(math.radians(22.5))
GRIP_FOOT = (GRIP_TOP[0] + RAKE * GRIP_DROP, GRIP_TOP[1] - GRIP_DROP)
HAND = frame_point(0.0, -3.30, GRIP_TOP[0] + RAKE * (GRIP_TOP[1] + 3.30))   # the shooting hand on the grip
SUPPORT = frame_point(0.0, -1.90, -8.50)                                     # the off hand on the foregrip


def hseg(b, p0, p1, w, t, mat, y=0.0, over=None, **kw):
    """A straight bar in the top (x-z) plane from p0 to p1, each (x, z) in inches, at height y: w across it in the
    plane, t tall. Its direction snaps to 22.5 degree steps, as Inch.seg's do."""
    o = b.o
    x0, z0 = o[0] + IN * p0[0], o[2] + IN * p0[1]
    x1, z1 = o[0] + IN * p1[0], o[2] + IN * p1[1]
    dx, dz = x1 - x0, z1 - z0
    L = math.hypot(dx, dz)
    phi = math.degrees(math.atan2(dz, dx))
    snapped = round(phi / 22.5) * 22.5
    base = round(snapped / 90.0) * 90.0
    ang = snapped - base
    if ang > 45:
        ang -= 90
        base += 90
    mx, mz, Y = (x0 + x1) / 2, (z0 + z1) / 2, o[1] + IN * y
    ext = IN * over if over is not None else IN * w * 0.25
    half_l, half_w = L / 2 + ext, IN * w / 2
    along_x = round(base / 90.0) % 2 == 0
    hx, hz = (half_l, half_w) if along_x else (half_w, half_l)
    lo = [mx - hx, Y - IN * t / 2, mz - hz]
    hi = [mx + hx, Y + IN * t / 2, mz + hz]
    # a positive turn about +y takes +x toward -z, so the bar's turn from x toward z is the negative of that
    rot = dict(axis='y', angle=-ang, origin=[mx, Y, mz]) if abs(ang) > 1e-6 else None
    return b.m.box(lo, hi, mat, rot=rot, axis=0 if along_x else 2, **kw)


def string_end(s):
    """Where the cocked string leaves the cam on side s (+1 right): the point on the cam's rim square to the string."""
    a = math.radians(STRING_ANGLE)
    u = np.array([s * math.sin(a), -math.cos(a)])          # from the latch out toward the cam
    n = np.array([s * math.cos(a), math.sin(a)])           # square to it, away from the rail
    c = np.array([s * CAM[0], CAM[1]])
    # move the cam's centre along n to the string's line: the rim point is the centre less n times the radius
    return tuple(c - n * CAM_R), u


# the string's line when it's let go: straight across between where it leaves the cams
SLACK_Z = string_end(1)[0][1]


def body(m):
    """Everything that stays put: the rail, riser and stirrup, the limbs, cams, cables and cocked string, the trigger
    box with its latch, spring, safety and sights, the trigger, guard and grip, the foregrip, tube and stock."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    _rail(b)
    _riser(b)
    _limbs(b)
    _box(b)
    _grip(b)
    _stock(b)


def _rail(b):
    """The flight rail: two lands either side of the groove, side panels with their lightening cuts, the foregrip
    under it with finger flanges out each side, and the cable guard rod running back under the riser."""
    r0, r1 = RISER[1], BOX[0]
    for s in (-1, 1):
        x0, x1 = sorted((s * GROOVE, s * RAIL_HALF))
        b.box((x0, RAIL_BOT, r0), (x1, RAIL_TOP, r1), 'rail')
        g0, g1 = sorted((s * (GROOVE + 0.04), s * (GROOVE + 0.26)))
        b.box((g0, RAIL_TOP - 0.005, r0 + 0.5), (g1, RAIL_TOP + 0.004, r1), 'glide')    # where the string runs
    b.box((-GROOVE, RAIL_BOT, r0), (GROOVE, GROOVE_BOT, r1), 'rail')
    # the side cuts: long dark windows along each side
    for s in (-1, 1):
        x0, x1 = sorted((s * RAIL_HALF, s * (RAIL_HALF + 0.006)))
        for z0, z1 in ((-17.2, -13.4), (-3.9, -2.2)):
            b.box((x0, -0.98, z0), (x1, -0.52, z1), 'slot')
    # the foregrip: under the rail, rounded under, with finger grooves, and the flanges keeping fingers off the string
    f0, f1 = FOREGRIP
    b.box((-0.68, -2.45, f0), (0.68, RAIL_BOT, f1), 'grip', axis=2)
    b.box((-0.52, -2.62, f0 + 0.2), (0.52, -2.30, f1 - 0.2), 'grip', axis=2)
    b.box((-0.58, -2.30, f0 - 0.30), (0.58, RAIL_BOT, f0), 'grip', axis=2)
    for z in np.linspace(f0 + 1.2, f1 - 1.0, 4):
        for s in (-1, 1):
            x0, x1 = sorted((s * 0.68, s * 0.686))
            b.box((x0, -2.30, z - 0.06), (x1, -1.55, z + 0.06), 'groove')
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.68, s * 1.75))
        b.box((x0, -1.42, f0 + 0.6), (x1, -1.22, f1 - 0.8), 'grip')
        x0, x1 = sorted((s * 1.60, s * 1.78))
        b.box((x0, -1.22, f0 + 0.6), (x1, -0.98, f1 - 0.8), 'grip')
    # the cable guard: a rod back from the riser under the rail, its slide holding the cables down out of the vanes' way
    b.rod(-17.9, -12.6, 0.11, 'axle', axis=2, at=(0.0, -1.62))
    b.box((-0.36, -1.88, -14.45), (0.36, -1.36, -13.55), 'riser')


def _riser(b):
    """The cast riser, its limb pockets, the fibre front sight on top, and the stirrup out in front."""
    z0, z1 = RISER
    b.box((-RISER_HALF, RISER_BOT, z0), (RISER_HALF, RISER_TOP, z1), 'riser')
    b.box((-RISER_HALF + 0.15, RISER_BOT - 0.12, z0 + 0.15), (RISER_HALF - 0.15, RISER_TOP + 0.10, z1 - 0.15), 'riser')
    for s in (-1, 1):                                                   # its cast windows
        x0, x1 = sorted((s * RISER_HALF, s * (RISER_HALF + 0.006)))
        b.box((x0, -0.95, z0 + 0.55), (x1, 0.45, z1 - 0.55), 'slot')
    b.box((-0.50, RISER_TOP + 0.10, z0 - 0.003), (0.50, RISER_BOT - 0.12, z0), 'slot')   # the front's recess
    # the pockets: each holds both limbs' roots, turned 45 degrees back like the limbs
    for s in (-1, 1):
        rx, rz = ROOT
        hseg(b, (s * (rx - 0.55), rz - 0.55), (s * (rx + 0.75), rz + 0.75), 0.80, 2 * LIMB_Y + LIMB_T + 0.20, 'pocket',
             over=0.0)
        for yy in (-1, 1):                                              # the bolts that clamp them
            hx, hz = s * (rx + 0.05), rz + 0.05
            b.rod(yy * (LIMB_Y + LIMB_T / 2 + 0.10), yy * (LIMB_Y + LIMB_T / 2 + 0.16), 0.16, 'axle', axis=1, at=(hx, hz))
    # the front sight: a post with a green fibre, between two ears
    fz = FRONT_SIGHT
    b.box((-0.32, RISER_TOP + 0.10, fz - 0.45), (0.32, RISER_TOP + 0.24, fz + 0.45), 'riser')
    b.box((-0.07, RISER_TOP + 0.24, fz - 0.08), (0.07, SIGHT + 0.07, fz + 0.08), 'riser')
    b.box((-0.045, SIGHT - 0.045, fz - 0.10), (0.045, SIGHT + 0.045, fz + 0.10), 'fibre_g')
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.24, s * 0.32))
        b.box((x0, RISER_TOP + 0.24, fz - 0.35), (x1, SIGHT + 0.10, fz + 0.35), 'riser')
    # the stirrup: two arms out from under the riser, a rubbered bar across the front
    sy = -0.95
    for s in (-1, 1):
        hseg(b, (s * 1.05, z0 + 0.2), (s * 2.40, z0 - 3.05), 0.38, 0.38, 'stirrup', y=sy, over=0.1)
    b.box((-2.62, sy - 0.19, z0 - 3.55), (2.62, sy + 0.19, z0 - 3.17), 'stirrup')
    b.box((-1.80, sy - 0.25, z0 - 3.62), (1.80, sy + 0.25, z0 - 3.10), 'pad', axis=0)


def _limbs(b):
    """The split limbs, 45 degrees back off the pockets, each pair's tips forked round a cam on its axle; the cables
    across between the cams. (The string is drawn apart: see string().)"""
    for s in (-1, 1):
        root, tip = (s * ROOT[0], ROOT[1]), (s * CAM[0], CAM[1])
        for yy in (-1, 1):
            hseg(b, root, tip, LIMB_W, LIMB_T, 'limb', y=yy * LIMB_Y, over=0.30)
        cx, cz = tip
        b.rod(-LIMB_Y - LIMB_T / 2 - 0.08, LIMB_Y + LIMB_T / 2 + 0.08, 0.09, 'axle', axis=1, at=(cx, cz))
        # the cam: its main wheel, the string's module a step proud and set back, and holes cut through
        b.rod(-CAM_T / 2, CAM_T / 2, CAM_R, 'cam', axis=1, at=(cx, cz))
        b.rod(-CAM_T / 2 - 0.04, CAM_T / 2 + 0.04, CAM_R * 0.62, 'module', axis=1, at=(cx - s * 0.18, cz + 0.22))
        for a in (40, 160, 280):
            hx = cx + s * 0.85 * math.cos(math.radians(a))
            hz = cz + 0.85 * math.sin(math.radians(a))
            b.rod(-CAM_T / 2 - 0.006, CAM_T / 2 + 0.006, 0.20, 'slot', axis=1, at=(hx, hz))
        b.rod(-CAM_T / 2 - 0.10, CAM_T / 2 + 0.10, 0.16, 'axle', axis=1, at=(cx, cz))
    # the cables, cam to cam, one high and one low, through the slide under the rail
    for yy, dz in ((0.34, -0.12), (-0.34, 0.12)):
        hseg(b, (-CAM[0], CAM[1] + dz), (CAM[0], CAM[1] + dz), 0.08, 0.08, 'string', y=yy, over=0.0)


def string(m):
    """The cocked string, cam to latch each side, its tan centre serving round the latch: drawn apart, so first person
    can let it fly forward to the cams with a shot (flattened toward SLACK_Z) and draw it back to the latch to cock."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    for s in (-1, 1):
        end, u = string_end(s)
        serve = (s * 1.6 * math.sin(math.radians(STRING_ANGLE)), -1.6 * math.cos(math.radians(STRING_ANGLE)))
        hseg(b, (0.0, 0.0), serve, 0.13, 0.13, 'serving', y=STRING_Y, over=0.05)
        hseg(b, serve, end, 0.11, 0.11, 'string', y=STRING_Y, over=0.02)


def _box(b):
    """The trigger box: its cheeks either side of the channel the nock goes into, the latch's claws, the bolt's
    retention spring, the optic rail, the safety slide and the rear notch with its two fibre dots."""
    z0, z1 = BOX
    for s in (-1, 1):
        x0, x1 = sorted((s * CHANNEL, s * BOX_HALF))
        b.box((x0, BOX_BOT, z0), (x1, BOX_TOP, 0.35), 'box')
    b.box((-CHANNEL, BOX_BOT, z0), (CHANNEL, RAIL_TOP, 0.35), 'box')
    b.box((-BOX_HALF, BOX_BOT, 0.35), (BOX_HALF, BOX_TOP, z1), 'box')
    b.box((-BOX_HALF + 0.12, BOX_BOT - 0.06, z0 + 0.12), (BOX_HALF - 0.12, BOX_TOP + 0.05, z1 - 0.12), 'box')
    for s in (-1, 1):                                                   # the latch's claws, either side of the nock
        x0, x1 = sorted((s * 0.10, s * CHANNEL))
        b.box((x0, -0.30, 0.0), (x1, 0.22, 0.35), 'latch')
    for s in (-1, 1):                                                   # pins through the box's sides
        for y, z in ((-0.55, 1.10), (-0.95, 2.70)):
            b.rod(s * BOX_HALF, s * (BOX_HALF + 0.02), 0.09, 'axle', axis=0, at=(y, z))
    # the retention spring: off the box's front, pressing the bolt down onto the rail
    b.box((-0.20, 0.38, z0 - 0.22), (0.20, 0.82, z0), 'box')
    b.seg((z0 - 0.10, 0.66), (z0 - 1.45, 0.10), 0.06, 0.20, 'spring', over=0.04)
    b.seg((z0 - 1.40, 0.10 + 0.012), (z0 - 1.75, 0.10 + 0.012), 0.06, 0.20, 'spring', over=0.02)
    picatinny(b, *RAIL_Z, side='top', base=BOX_TOP, mat='box')
    # the safety: a slide on the top behind the rail, red showing when it's off
    b.box((-0.36, BOX_TOP, 3.50), (0.36, BOX_TOP + 0.12, 3.90), 'safety')
    b.box((-0.36, BOX_TOP, 3.42), (-0.26, BOX_TOP + 0.07, 3.50), 'fire')
    # the rear sight: a block with its notch, a red fibre dot either side
    rz = REAR_SIGHT
    b.box((-0.44, BOX_TOP, rz - 0.20), (0.44, SIGHT - 0.12, rz + 0.20), 'box')
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.08, s * 0.44))
        b.box((x0, SIGHT - 0.12, rz - 0.20), (x1, SIGHT + 0.12, rz + 0.20), 'box')
        b.box((s * 0.24 - 0.04, SIGHT - 0.04, rz + 0.20), (s * 0.24 + 0.04, SIGHT + 0.04, rz + 0.24), 'fibre_r')


def _grip(b):
    """The trigger (straight down in the back of the guard, its tip curving forward), the guard and the raked
    pistol grip."""
    knee = BOX_BOT - 0.42
    b.seg((TRIGGER_Z, BOX_BOT), (TRIGGER_Z, knee), 0.20, 0.22, 'trigger', over=0.03)
    b.seg((TRIGGER_Z, knee), (TRIGGER_Z - 0.14, BOX_BOT - 0.78), 0.17, 0.22, 'trigger', over=0.03)
    g0, g1 = GUARD
    b.seg((g0, BOX_BOT), (g0, -2.55), 0.22, 0.42, 'grip', over=0.0)
    b.seg((g0 - 0.11, -2.55 + 0.11), (g1, -2.55 + 0.11), 0.22, 0.42, 'grip', over=0.0)
    top, foot = GRIP_TOP, GRIP_FOOT
    b.seg(top, foot, 1.30, 1.12, 'grip', over=0.0)
    b.seg(top, (top[0] + RAKE * (GRIP_DROP - 0.45), top[1] - GRIP_DROP + 0.45), 1.36, 1.00, 'stipple', over=-0.40)
    fz, fy = foot
    d = np.array([RAKE, -1.0]) / math.hypot(RAKE, 1.0)
    b.seg(tuple(np.array(foot) - d * 0.05), tuple(np.array(foot) + d * 0.12), 1.42, 1.18, 'grip', over=0.0)


def _stock(b):
    """The buffer tube with its castle nut, and the adjustable stock on it: cheek on top, the toe sloping down to a
    rubber pad, the lock lever under the tube."""
    b.rod(BOX[1], TUBE_END, TUBE_R, 'tube', axis=2, at=(0.0, TUBE_Y))
    b.rod(BOX[1], BOX[1] + 0.35, TUBE_R + 0.08, 'tube', axis=2, at=(0.0, TUBE_Y))
    s0, s1 = STOCK
    top = 0.52
    b.box((-0.70, -1.00, s0), (0.70, top, s1), 'stock', axis=2)
    b.box((-0.56, -1.00, s0 + 0.15), (0.56, top + 0.10, s1 - 0.1), 'stock', axis=2)
    toe = (s1, -3.10)
    heel = (s0, -1.00)
    k = (toe[1] - heel[1]) / (toe[0] - heel[0])
    for z0, z1, w in ((s0, s0 + 2.4, 1.0), (s0 + 2.1, s1, 1.94)):
        p0, p1 = np.array([z0, heel[1] + k * (z0 - s0)]), np.array([z1, heel[1] + k * (z1 - s0)])
        d = (p1 - p0) / np.linalg.norm(p1 - p0)
        n = np.array([-d[1], d[0]])
        if n[1] < 0:
            n = -n
        b.seg(tuple(p0 + n * w / 2), tuple(p1 + n * w / 2), w, 1.40, 'stock', over=0.0)
    b.box((-0.30, -1.30, s0 + 0.35), (0.30, -0.92, s0 + 1.65), 'tube')         # the lock lever
    b.box((-0.74, -3.30, s1), (0.74, top + 0.14, STOCK_END), 'pad', axis=2)
    b.box((-0.62, -3.42, s1 + 0.1), (0.62, top + 0.24, STOCK_END - 0.1), 'pad', axis=2)
    b.box((-0.18, -2.90, s1 - 1.6), (0.18, -2.55, s1 - 0.6), 'slot')           # the sling slot


def bolt(m):
    """A 20" carbon bolt, lying on the rail with its nock in the latch: field point, insert, shaft, two red hen vanes
    up either side and the white cock vane down in the groove, the orange nock. Origin: the nock's end, on the bolt's
    line at the latch."""
    b = Inch(m)
    t = -BOLT_LEN
    b.rod(t + 0.15, t + 0.40, 0.06, 'point', axis=2)
    b.rod(t + 0.35, t + 0.62, 0.12, 'point', axis=2)
    b.rod(t + 0.58, t + 1.05, BOLT_R, 'point', axis=2)
    b.rod(t + 1.05, t + 1.25, BOLT_R + 0.01, 'insert', axis=2)
    b.rod(t + 1.25, -0.50, BOLT_R, 'shaft', axis=2)
    b.rod(-0.55, 0.0, BOLT_R + 0.01, 'nock', axis=2)
    v0, v1 = VANES
    vm = (v0 + v1) / 2 + 0.6
    # the cock vane: down into the groove, low at the front and rising to the back
    b.box((-0.02, -BOLT_R - 0.20, v0), (0.02, -BOLT_R + 0.02, vm), 'cock')
    b.box((-0.02, -BOLT_R - 0.36, vm), (0.02, -BOLT_R + 0.02, v1), 'cock')
    c = b.p(0, 0, 0)
    for s in (-1, 1):
        for z0, z1, h in ((v0, vm, 0.20), (vm, v1, 0.36)):
            x0, x1 = sorted((s * (BOLT_R - 0.02), s * (BOLT_R + h)))
            b.m.box(b.p(x0, -0.02, z0), b.p(x1, 0.02, z1), 'hen', axis=2,
                    rot=dict(axis='z', angle=s * 22.5, origin=[float(c[0]), float(c[1]), float(c[2])]))


def look(wear=0.4):
    """A gunmetal anodised rail and limb pockets, a black riser, black composite limbs, black anodised cams with grey modules and bright
    axles, a dark string with a tan centre serving, black polymer box, grip, foregrip and stock; the bolt black
    carbon with red and white vanes and an orange nock."""
    def rail(f):
        return P.anodised(f, '#4a4f56', wear_to='#9aa1a8')

    def glide(f):
        return P.anodised(f, '#646a72', wear_to='#a9b0b7')

    def riser(f):
        return P.anodised(f, '#1d1e21', wear_to='#62676d')

    def pocket(f):
        return P.anodised(f, '#3c4047', wear_to='#868c93')

    def limb(f):
        return P.polymer(f, '#25282c', 0.05)

    def cam(f):
        return P.anodised(f, '#1a1b1e', wear_to='#5e6369')

    def module(f):
        return P.anodised(f, '#50555d', wear_to='#959ba2')

    def axle(f):
        return P.steel(f, 0.55, tint='#a9aeb4', spread=0.06)

    def string(f):
        c = P.plain(f, '#2c2b27', 0.10)
        twist = np.sin((f.X + f.Z) / IN * 18) > 0.4
        return np.where(twist[..., None], c * 0.75, c)

    def serving(f):
        c = P.plain(f, '#8e7c58', 0.08)
        wrap = np.sin((f.X + f.Z) / IN * 40) > 0.5
        return np.where(wrap[..., None], c * 0.82, c)

    def stirrup(f):
        return P.anodised(f, '#1d1e21', wear_to='#62676d')

    def pad(f):
        return P.rubber(f, '#111111')

    def box(f):
        return P.polymer(f, '#161617', 0.10)

    def latch(f):
        return P.steel(f, 0.40, tint='#8a9096', spread=0.06)

    def spring(f):
        return P.steel(f, 0.62, tint='#b4b9be', spread=0.05)

    def safety(f):
        return P.polymer(f, '#121213', 0.25)

    def fire(f):
        return P.plain(f, '#c8302a', 0.05)

    def fibre_g(f):
        return P.plain(f, '#5cff6e', 0.03)

    def fibre_r(f):
        return P.plain(f, '#ff4a2e', 0.03)

    def trigger(f):
        return P.anodised(f, '#18191b', wear_to='#5d6268')

    def grip(f):
        return P.polymer(f, '#171718', 0.10)

    def groove(f):
        return P.polymer(f, '#0e0e0f', 0.10)

    def stipple(f):
        c = P.polymer(f, '#121213', 0.30)
        dots = (np.sin(f.X * 70) * np.sin(f.Y * 70 + f.Z * 70)) > 0.55
        return np.where(dots[..., None], c * 0.6, c)

    def tube(f):
        return P.anodised(f, '#1b1c1f', wear_to='#5a5f65')

    def stock(f):
        return P.polymer(f, '#161617', 0.11)

    def slot(f):
        return P.plain(f, '#070707', 0.05)

    def point(f):
        return P.steel(f, 0.58, tint='#b9bec3', spread=0.05)

    def insert(f):
        return P.steel(f, 0.45, tint='#9aa0a6', spread=0.05)

    def shaft(f):
        c = P.plain(f, '#141414', 0.04)
        weave = (np.sin(f.Z / IN * 30) * np.sin((f.X + f.Y) / IN * 30)) > 0.3
        return np.where(weave[..., None], c * 1.35, c)

    def nock(f):
        return P.plain(f, '#ff7a1c', 0.05)

    def hen(f):
        return P.plain(f, '#c4242a', 0.06)

    def cock(f):
        return P.plain(f, '#e6e4dc', 0.05)

    d = dict(rail=rail, glide=glide, riser=riser, pocket=pocket, limb=limb, cam=cam, module=module, axle=axle,
             string=string, serving=serving, stirrup=stirrup, pad=pad, box=box, latch=latch, spring=spring,
             safety=safety, fire=fire, fibre_g=fibre_g, fibre_r=fibre_r, trigger=trigger, grip=grip, groove=groove,
             stipple=stipple, tube=tube, stock=stock, slot=slot, point=point, insert=insert, shaft=shaft, nock=nock,
             hen=hen, cock=cock)
    d['_after'] = [lambda f, c: P.grime(f, c, 0.3 * (0.4 + f.wear)),
                   lambda f, c: P.scratches(f, c, 0.22 * (0.3 + f.wear))]
    return d
