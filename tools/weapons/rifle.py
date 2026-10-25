"""The assault rifle: an M4A1 carbine in its Block II fit (9.5" quad rail, flat-top upper, folding sights),
drawn from the real thing's measurements. Measurements are inches from the bore's centre line (y) and the
front face of the upper receiver (z, positive toward the stock).

The gun is modular: the body is always there, and the stock, magazine and everything on the rails are
parts in slots (see kit.py). The folding sights stand up on their own and lie down under an optic.
"""
import numpy as np

import paint as P
from inch import IN, Inch, picatinny

BORE_Y = 2.95           # where the bore sits in the build frame
Z0 = -4.49              # where the upper receiver's front face sits

SIGHT = 2.60            # the irons' line of sight above the bore (a flat-top with folding sights)
RAIL = 1.20             # top of the rails above the bore
UPPER_LEN = 7.06
GUARD_LEN = 9.5
MUZZLE = -13.0          # end of the 14.5" barrel
HIDER_LEN = 2.1


def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


HAND = frame_point(0.0, -3.0, 6.6)      # middle of the shooting hand on the pistol grip
HANDLE_TRAVEL = 2.8     # inches the charging handle comes back when it's worked


def charging_handle(m):
    """The charging handle sticking out of the back of the upper, latch on the left. A part of its own so the
    first-person reload can pull it back."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    b.box((-0.30, 0.52, UPPER_LEN), (0.30, 0.80, UPPER_LEN + 0.40), 'park')
    b.box((-0.62, 0.55, UPPER_LEN + 0.18), (0.30, 0.78, UPPER_LEN + 0.40), 'park')
    b.box((-0.88, 0.58, UPPER_LEN + 0.10), (-0.62, 0.76, UPPER_LEN + 0.40), 'park')


def body(m):
    b = Inch(m, (0.0, BORE_Y, Z0))
    anod, park, steel, poly = 'anod', 'park', 'steel', 'poly'
    # ---- upper receiver: flat-top with its rail, ejection port and dust cover on the right
    b.box((-0.43, -0.60, 0.0), (0.43, 0.62, 6.3), anod)
    b.box((-0.43, 0.15, 6.3), (0.43, 0.62, UPPER_LEN), anod)
    b.box((-0.35, 0.62, 0.0), (0.35, 0.83, UPPER_LEN), anod)
    picatinny(b, 0.05, UPPER_LEN - 0.08, 'top')
    b.box((0.43, -0.30, 1.12), (0.47, 0.40, 3.72), 'port')                 # dust cover, shut
    b.box((0.47, 0.00, 1.30), (0.49, 0.10, 3.55), 'port')                  # its stiffening rib
    b.rod(1.10, 3.75, 0.05, steel, axis=2, at=(0.46, -0.33))                # its hinge pin
    b.box((0.43, 0.10, 3.75), (0.60, 0.62, 4.25), anod)                     # brass deflector
    b.box((0.43, 0.30, 3.75), (0.66, 0.55, 4.05), anod)
    b.rod(4.25, 5.30, 0.23, anod, axis=2, at=(0.48, 0.38))                  # forward assist housing
    b.rod(5.30, 5.70, 0.21, steel, axis=2, at=(0.48, 0.38))                 # and its plunger
    # ---- lower receiver
    b.box((-0.45, -1.55, 0.30), (0.45, -0.60, UPPER_LEN), anod)
    b.box((-0.50, -0.60, 6.30), (0.50, 0.15, UPPER_LEN), anod)               # buffer tower
    b.box((-0.35, -1.00, 0.00), (0.35, -0.60, 0.40), anod)                   # front pivot lug
    b.box((-0.56, -2.45, 0.40), (0.56, -1.55, 3.05), anod)                   # magazine well
    b.box((-0.60, -2.60, 0.32), (0.60, -2.42, 3.12), anod)                   # its flared lip
    b.box((-0.56, -1.80, 3.05), (0.56, -1.55, 3.20), anod)
    b.box((0.45, -1.50, 3.15), (0.53, -1.08, 3.58), anod)                    # fence round the mag release
    b.rod(0.45, 0.58, 0.14, steel, axis=0, at=(-1.29, 3.37))                 # mag release button
    b.box((-0.53, -1.30, 3.10), (-0.45, -0.70, 3.55), steel)                 # bolt catch
    b.box((-0.58, -1.38, 3.12), (-0.45, -1.18, 3.60), steel)                 # and its paddle
    b.box((-0.18, -2.42, 3.05), (0.18, -2.26, 5.45), anod)                   # trigger guard
    b.box((-0.18, -2.42, 5.20), (0.18, -1.55, 5.45), anod)
    b.seg((4.02, -1.55), (4.30, -2.15), 0.13, 0.20, steel)                    # trigger
    b.box((-0.10, -2.22, 4.18), (0.10, -2.08, 4.42), steel)
    b.box((-0.55, -1.22, 5.05), (-0.45, -1.02, 5.85), steel)                 # safety selector, left
    b.rod(5.30, 5.62, 0.10, steel, axis=2, at=(0.48, -1.12))                 # and its stub on the right
    for z, y in ((0.20, -0.80), (6.65, -0.25)):                              # pivot and takedown pins
        b.rod(-0.52, 0.52, 0.08, steel, axis=0, at=(y, z))
    for z, y in ((4.20, -1.08), (5.02, -0.98)):                              # trigger and hammer pins
        b.rod(-0.48, 0.48, 0.06, steel, axis=0, at=(y, z))
    # ---- pistol grip (A2 angle), with its beavertail under the buffer tower
    b.seg((5.95, -1.45), (7.45, -5.10), 1.25, 0.95, poly)
    b.box((-0.42, -1.70, 6.30), (0.42, -1.50, 6.85), poly)
    b.box((-0.47, -5.35, 6.55), (0.47, -5.05, 7.95), poly)
    # ---- receiver extension (buffer tube), castle nut and sling plate
    b.rod(UPPER_LEN, UPPER_LEN + 7.10, 0.574, anod, axis=2)
    b.rod(UPPER_LEN, UPPER_LEN + 0.30, 0.66, park, axis=2)
    b.box((-0.62, -0.78, UPPER_LEN), (0.62, 0.40, UPPER_LEN + 0.07), steel)
    b.box((-0.64, -0.78, UPPER_LEN + 0.07), (-0.58, -0.45, UPPER_LEN + 0.42), steel)   # sling loop
    # ---- the quad rail: chamfered square tube, a rail on each face, free-floated off the barrel nut
    g0, g1 = -GUARD_LEN, -0.06
    b.box((-0.83, -0.62, g0), (0.83, 0.62, g1), 'guard')
    b.box((-0.62, -0.83, g0), (0.62, 0.83, g1), 'guard')
    for side in ('top', 'bottom', 'left', 'right'):
        picatinny(b, g0 + 0.05, g1 - 0.05, side)
    b.box((-0.75, -0.75, g1), (0.75, 0.75, 0.0), park)                      # barrel nut showing at the join
    b.box((0.83, -0.35, -0.95), (0.95, 0.35, -0.45), steel)                  # QD sling socket, right rear
    # ---- barrel, crush washer and A2 flash hider
    b.rod(MUZZLE, g0, 0.375, park, axis=2)
    b.rod(MUZZLE - 0.05, MUZZLE + 0.05, 0.43, steel, axis=2)
    b.rod(MUZZLE - HIDER_LEN, MUZZLE - 0.05, 0.4375, 'hider', axis=2)


def irons(m, up=True):
    """KAC-style folding sights: the front on the end of the handguard rail, the rear on the upper."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    anod = 'anod'
    # front: clamp, then the post between protective ears
    b.box((-0.45, RAIL, -9.40), (0.45, RAIL + 0.24, -8.20), anod)
    if up:
        for x0, x1 in ((-0.30, -0.13), (0.13, 0.30)):
            b.box((x0, RAIL + 0.24, -9.12), (x1, SIGHT - 0.05, -8.62), anod)
        b.box((-0.13, RAIL + 0.24, -9.05), (0.13, RAIL + 0.60, -8.70), anod)
        b.box((-0.045, RAIL + 0.60, -8.92), (0.045, SIGHT + 0.06, -8.83), anod)
        b.box((-0.30, SIGHT - 0.12, -9.12), (0.30, SIGHT - 0.05, -8.98), anod)    # ear tops braced
    else:
        b.box((-0.30, RAIL + 0.24, -9.38), (0.30, RAIL + 0.48, -8.25), anod)
    # rear: clamp, protective ears, the flip aperture with its peep at the sight line
    b.box((-0.45, RAIL, 5.60), (0.45, RAIL + 0.24, 6.80), anod)
    b.rod(0.45, 0.68, 0.15, 'steel', axis=0, at=(RAIL + 0.36, 6.45))          # windage knob
    if up:
        for x0, x1 in ((-0.45, -0.32), (0.32, 0.45)):
            b.box((x0, RAIL + 0.24, 5.95), (x1, SIGHT + 0.12, 6.55), anod)
        hole, t0, t1 = 0.10, 6.14, 6.28         # the large (0.2") aperture
        b.box((-0.30, RAIL + 0.24, t0), (0.30, SIGHT - 0.20, t1), anod)
        b.box((-0.30, SIGHT - 0.20, t0), (-hole, SIGHT + 0.25, t1), anod)
        b.box((hole, SIGHT - 0.20, t0), (0.30, SIGHT + 0.25, t1), anod)
        b.box((-hole, SIGHT - 0.20, t0), (hole, SIGHT - hole, t1), anod)
        b.box((-hole, SIGHT + hole, t0), (hole, SIGHT + 0.25, t1), anod)
    else:
        b.box((-0.38, RAIL + 0.24, 5.70), (0.38, RAIL + 0.46, 6.80), anod)


def stock(m):
    """A SOPMOD-style stock with its battery tubes, on the buffer tube, fully extended. Origin: the
    buffer tube's centre at the back of the lower receiver."""
    b = Inch(m)
    poly = 'poly'
    s0, s1 = 3.85, 10.40
    b.box((-0.70, -0.85, s0 + 0.15), (0.70, 0.80, s1), poly)                 # sleeve round the tube
    b.box((-0.62, 0.80, s0 + 2.70), (0.62, 1.10, s1), poly)                  # cheek weld on the back half
    b.box((-0.62, 0.80, s0 + 2.00), (0.62, 0.95, s0 + 2.70), poly)
    b.box((-0.73, -0.55, s0 + 4.40), (0.73, 0.55, s1 - 0.25), 'grip')         # moulded side panels
    b.seg((s0 + 0.40, -0.40), (s1 - 0.10, -2.75), 1.15, 1.30, poly, over=0.1)  # underside running down to the toe
    b.box((-0.65, -2.20, s0 + 2.75), (0.65, -0.85, s1), poly)
    for x in (-0.86, 0.86):                                                   # battery tubes and caps
        b.rod(s0 + 0.30, s0 + 4.20, 0.33, poly, axis=2, at=(x, -0.20))
        b.rod(s0 + 0.12, s0 + 0.30, 0.36, 'rubber', axis=2, at=(x, -0.20))
    b.box((-0.25, -1.08, s0 + 0.35), (0.25, -0.85, s0 + 1.35), poly)         # adjustment lever
    b.box((-0.76, -3.48, s1), (0.76, 1.16, s1 + 0.45), 'rubber')             # butt pad
    b.box((0.70, -0.45, s0 + 5.0), (0.76, -0.05, s0 + 5.4), 'steel')          # QD sling socket


def magazine(m):
    """A 30-round polymer magazine (PMAG style). Origin: the top front of the magazine well opening."""
    b = Inch(m)
    mag = 'mag'
    b.box((-0.40, -1.00, 0.16), (0.40, 0.55, 2.40), mag)                     # top, up inside the well and receiver
    b.box((-0.46, -2.55, 0.10), (0.46, -0.95, 2.45), mag)                    # body below the well
    b.box((-0.50, -1.22, 0.04), (0.50, -1.06, 2.52), mag)                    # over-insertion stop
    b.seg((1.20, -2.40), (-0.05, -5.55), 2.25, 0.95, mag)                     # the curve forward
    b.seg((-0.08, -5.60), (-0.22, -5.95), 2.50, 1.08, mag, over=0.05)          # floorplate
    b.box((-0.34, 0.40, 0.25), (0.34, 0.62, 1.70), 'steel')                  # follower and feed lips



def look(wear=0.4):
    """The rifle's materials."""
    def guard(f):
        return P.anodised(f, '#1b1d20')

    def port(f):
        c = P.parkerised(f, '#2a2b28')
        rib = (f.Z % 0.6) < 0.08
        return np.where(rib[..., None], c * 0.75, c)

    def hider(f):
        c = P.parkerised(f, '#2c2d2a')
        if abs(f.n[2]) > 0.5:
            r = np.hypot(f.X, f.Y - BORE_Y)
            return np.where((r < 0.16)[..., None], c * 0.12, c)
        up = f.n[1] > 0.5 or abs(f.n[0]) > 0.5
        slot = up & ((f.Z % 0.42) < 0.26) & (f.Z > Z0 + IN * (MUZZLE - HIDER_LEN + 0.25)) & (f.Z < Z0 + IN * (MUZZLE - 0.6))
        return np.where(slot[..., None], c * 0.18, c)

    def poly(f):
        c = P.polymer(f, '#1d1d20')
        return c

    def grip(f):
        c = P.polymer(f, '#19191c', 0.16)
        return c * (1 + 0.1 * (np.sin(f.Y * 30) > 0.5))[..., None]

    def mag(f):
        c = P.polymer(f, '#9a845f', 0.07)
        ribs = (np.sin(f.Y * 14) > 0.75) & (np.abs(f.n[0]) > 0.5) & (f.Y > -1.6) & (f.Y < -0.4)
        dots = (np.abs(f.n[0]) > 0.5) & (f.Y < -2.0) & (f.Y > -3.6) & ((np.sin(f.Y * 30) * np.sin(f.Z * 30)) > 0.6)
        c = np.where(ribs[..., None], c * 0.82, c)
        return np.where(dots[..., None], c * 0.9, c)

    def steel(f):
        return P.steel(f, 0.20, tint='#7d8186', spread=0.08)     # blacked pins and controls

    def rubber(f):
        return P.rubber(f, '#121212', 0.12)

    d = dict(anod=lambda f: P.anodised(f, '#1e2023'), guard=guard, park=lambda f: P.parkerised(f, '#2c2d2a'), steel=steel, poly=poly, grip=grip,
             rubber=rubber, port=port, hider=hider, mag=mag, flash=flash_paint)
    d['_after'] = [lambda f, c: P.grime(f, c, 0.35 * (0.4 + wear)),
                   lambda f, c: P.scratches(f, c, 0.25 * (0.3 + wear))]
    return d


def flash(m, z_tip, r=1.0):
    """Muzzle flash: crossed glowing planes and a core just past the muzzle (measured from the bore)."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    L = 2.6 * r
    b.box((-0.02, -1.3 * r, z_tip - L), (0.02, 1.3 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.3 * r, -0.02, z_tip - L), (1.3 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.4 * r, -0.4 * r, z_tip - 1.0 * r), (0.4 * r, 0.4 * r, z_tip), 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    t = np.clip(1 - np.sqrt(a ** 2 + b ** 2) / 1.1, 0, 1)
    return P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)
