"""The submachine gun: a roller-delayed 9mm (an MP5A2 sort), drawn from the real thing's measurements: 26.8" overall
with its fixed stock, an 8.85" barrel, a pressed-steel receiver with the cocking tube along its top, a slim
handguard, a drum rear sight and a hooded front post, a polymer grip frame with a big trigger guard and a curved
30-round magazine. Measurements are inches from the bore's centre line (y) and the barrel trunnion at the front of
the receiver (z, positive back toward the shooter).

It fires from a closed bolt, single shots or full auto. The cocking handle sits up on the left of the cocking tube:
after a fresh magazine on an empty gun it's pulled back, and slapped down out of its notch to send the bolt home.
The handle is the moving part, drawn apart so first person can work it.
"""
import numpy as np

import paint as P
from inch import IN, Inch

BORE_Y = 2.2            # where the bore sits in the build frame
Z0 = -2.0               # where the trunnion sits

RCVR_END = 11.20        # the receiver's back, where the end cap and stock go on
RCVR_HALF = 0.62        # the pressed receiver's half width
RCVR_BOT = -0.95
TUBE_Y = 0.86           # the cocking tube along the top
TUBE_R = 0.42
TUBE_FRONT = -6.20      # it runs out over the barrel to the front sight
BREECH = 1.60
MUZZLE = BREECH - 8.85
SIGHT = 1.88            # the sight line over the bore
FRONT_SIGHT = -5.85
REAR_SIGHT = 9.95       # the drum's aperture
HANDGUARD = (-5.05, -0.15)
MAG_Z = (0.55, 2.05)    # the magazine well's front and back
WELL_Y = -1.45          # the well's mouth
GUARD = (5.30, 7.55)
TRIGGER_Z = 6.85
GRIP = (7.55, 9.10)     # the pistol grip's front and back at the top
HANDLE_SLOT = (-3.75, -1.45)
HANDLE_TRAVEL = 2.05
STOCK_END = 19.60       # the butt pad's back face (26.8" overall)


def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


HAND = frame_point(0.0, -2.55, 8.55)        # the shooting hand on the grip
SUPPORT = frame_point(0.0, -1.45, -2.60)    # the off hand under the handguard


def stock_line(z):
    """The fixed stock's top and bottom: the top runs on from the receiver, dropping a little to the heel; the bottom
    slopes down from the end cap to the toe of a 4.4" butt."""
    u = np.clip((z - RCVR_END) / (STOCK_END - RCVR_END), 0, 1)
    top = 1.05 - 0.30 * u
    bot = -0.85 - 2.55 * u ** 0.9
    return top, bot


def body(m):
    """Everything that stays put: barrel, cocking tube and sights, receiver, grip frame and stock."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    rc, steel = 'rcvr', 'steel'
    # ---- the barrel out past the handguard, its three locking lugs for a suppressor and the bore
    b.rod(MUZZLE + 0.70, HANDGUARD[0], 0.36, 'barrel', axis=2)
    for i in range(3):
        a = np.radians(90 + 120 * i)
        b.box((0.30 * np.cos(a) - 0.09, 0.30 * np.sin(a) - 0.09, MUZZLE + 0.95), (0.30 * np.cos(a) + 0.09, 0.30 * np.sin(a) + 0.09, MUZZLE + 1.30), steel)
    b.rod(MUZZLE, MUZZLE + 0.70, 0.30, 'barrel', axis=2)
    b.rod(MUZZLE - 0.02, MUZZLE + 0.04, 0.12, 'bore', axis=2)
    # ---- the cocking tube over the barrel, the front sight's hood on its end
    b.rod(TUBE_FRONT, 0.0, TUBE_R, rc, axis=2, at=(0.0, TUBE_Y))
    b.box((-0.10, TUBE_Y - TUBE_R, TUBE_FRONT + 0.15), (0.10, 0.30, TUBE_FRONT + 0.75), rc)        # the barrel's collar
    b.rod(TUBE_FRONT - 0.30, TUBE_FRONT + 0.30, 0.34, rc, axis=2, at=(0.0, 0.0))
    f0, f1 = FRONT_SIGHT - 0.32, FRONT_SIGHT + 0.32
    b.box((-0.26, TUBE_Y, f0), (0.26, SIGHT - 0.42, f1), rc)                                     # the sight's base
    for s in (-1, 1):                                                                             # the round hood
        x0, x1 = sorted((s * 0.30, s * 0.40))
        b.box((x0, SIGHT - 0.42, f0), (x1, SIGHT + 0.30, f1), rc)
    b.box((-0.40, SIGHT + 0.26, f0), (0.40, SIGHT + 0.36, f1), rc)
    b.box((-0.05, SIGHT - 0.42, FRONT_SIGHT - 0.05), (0.05, SIGHT, FRONT_SIGHT + 0.05), 'post')
    # the handle's slot along the left of the tube, and its notch to hold it back
    b.box((-TUBE_R - 0.005, TUBE_Y - 0.08, HANDLE_SLOT[0]), (-TUBE_R + 0.06, TUBE_Y + 0.08, HANDLE_SLOT[1]), 'port')
    b.box((-TUBE_R - 0.005, TUBE_Y + 0.08, HANDLE_SLOT[1] - 0.20), (-TUBE_R + 0.10, TUBE_Y + 0.26, HANDLE_SLOT[1]), 'port')
    # ---- the receiver: a pressed U with the tube along its top, its ribs, the ejection port on the right
    h = RCVR_HALF
    b.box((-h, RCVR_BOT, 0.0), (h, TUBE_Y - 0.10, RCVR_END), rc)
    b.rod(0.0, RCVR_END, TUBE_R + 0.04, rc, axis=2, at=(0.0, TUBE_Y))
    for s in (-1, 1):                                                                             # pressed ribs
        x0, x1 = sorted((s * h, s * (h + 0.03)))
        b.box((x0, 0.05, 2.6), (x1, 0.20, RCVR_END - 0.6), 'rib')
    b.box((h - 0.08, -0.25, 1.90), (h + 0.005, 0.40, 3.90), 'port')
    b.box((h - 0.02, -0.25, 3.90), (h + 0.06, 0.40, 4.10), 'rib')                                 # its deflector bump
    b.box((-h - 0.02, RCVR_BOT, RCVR_END - 0.35), (h + 0.02, TUBE_Y + 0.40, RCVR_END), 'cap')      # the end cap
    for z in (4.55, 9.75):                                                                        # the grip frame's pins
        for s in (-1, 1):
            x0, x1 = sorted((s * h, s * (h + 0.04)))
            b.box((x0, -0.80, z - 0.07), (x1, -0.66, z + 0.07), 'screw')
    # the magazine well and its catch: the paddle behind it, the button on the right
    m0, m1 = MAG_Z
    b.box((-0.66, WELL_Y, m0 - 0.15), (0.66, RCVR_BOT, m1 + 0.15), rc)
    b.box((-0.56, WELL_Y - 0.005, m0), (0.56, WELL_Y + 0.05, m1), 'port')
    b.box((-0.30, WELL_Y - 0.15, m1 + 0.15), (0.30, RCVR_BOT, m1 + 0.38), steel)                  # paddle
    b.rod(h, h + 0.10, 0.12, steel, axis=0, at=(-1.20, m1 + 0.05))                                # button
    # ---- the rear drum sight on its base
    b.box((-0.38, TUBE_Y + TUBE_R, REAR_SIGHT - 0.55), (0.38, TUBE_Y + TUBE_R + 0.22, REAR_SIGHT + 0.70), rc)
    # the drum, its aperture a real hole through it: opened right up, since in game the eye sits much further back
    # than a cheek on the stock and a true-size peep would be filled by the front sight's hood
    hole, dz = 0.26, REAR_SIGHT + 0.10
    for x0, x1 in ((-0.42, -hole), (hole, 0.42)):
        b.rod(x0, x1, 0.44, 'drum', axis=0, at=(SIGHT, dz))
    b.box((-hole, SIGHT + hole, dz - 0.36), (hole, SIGHT + 0.44, dz + 0.36), 'drum')
    b.box((-hole, SIGHT - 0.44, dz - 0.36), (hole, SIGHT - hole, dz + 0.36), 'drum')
    for x0, x1 in ((-0.48, -0.42), (0.42, 0.48)):
        b.rod(x0, x1, 0.10, steel, axis=0, at=(SIGHT, dz))
    for s in (-1, 1):                                                                             # its wings
        x0, x1 = sorted((s * 0.44, s * 0.54))
        b.box((x0, TUBE_Y + TUBE_R, REAR_SIGHT - 0.40), (x1, SIGHT + 0.42, REAR_SIGHT + 0.60), rc)
    _handguard(b)
    _grip(b)
    _stock(b)


def _handguard(b):
    """The slim handguard: round-bellied, finger grooves along its sides, its back tucked into the receiver."""
    z = HANDGUARD[0]
    while z < HANDGUARD[1] - 1e-6:
        z1 = min(z + 0.6, HANDGUARD[1])
        u = (z - HANDGUARD[0]) / (HANDGUARD[1] - HANDGUARD[0])
        half = 0.70 + 0.10 * u
        b.box((-half, -1.20, z), (half, 0.30, z1), 'guard', axis=2, seam=(2,))
        b.box((-half + 0.14, -1.38, z), (half - 0.14, -1.20, z1), 'guard', axis=2, seam=(2,))
        z = z1
    for s in (-1, 1):                                                                             # finger grooves
        for k in range(4):
            zc = HANDGUARD[0] + 0.7 + k * 1.05
            x0, x1 = sorted((s * 0.79, s * 0.82))
            b.box((x0, -0.95, zc - 0.12), (x1, -0.10, zc + 0.12), 'groove')
    b.box((-0.66, -1.10, HANDGUARD[0] - 0.12), (0.66, 0.20, HANDGUARD[0]), 'guard')             # its nose


def _grip(b):
    """The polymer grip frame pinned under the receiver: its trigger guard, the trigger, the selector over the grip
    and the pistol grip raked back."""
    g0, g1 = GUARD
    pf = 'frame'
    b.box((-0.52, -1.45, 3.95), (0.52, RCVR_BOT, 10.10), pf)
    b.box((-0.30, -2.75, g0 - 0.10), (0.30, -1.45, g0 + 0.18), pf)                                # the guard's front
    b.box((-0.30, -2.80, g0), (0.30, -2.55, g1 - 0.30), pf)                                       # its bottom
    b.seg((g1 - 0.45, -2.68), (g1 + 0.10, -2.00), 0.24, 0.60, pf, over=0.03)
    b.seg((TRIGGER_Z, -1.45), (TRIGGER_Z, -1.80), 0.20, 0.24, 'steel', over=0.03)                # trigger: down,
    b.seg((TRIGGER_Z, -1.80), (TRIGGER_Z - 0.14, -2.12), 0.18, 0.24, 'steel', over=0.03)        # its tip forward
    # the pistol grip, raked 22.5 degrees back, a flared base at its foot and stippled panels on its sides
    gf, gb = GRIP
    drop = 3.75
    zc = (gf + gb) / 2
    top, foot = (zc, -1.30), (zc + 0.4142 * drop, -1.30 - drop)
    b.seg(top, foot, gb - gf, 1.16, pf, over=0.0)
    b.seg(top, foot, gb - gf - 0.24, 1.22, 'stipple', over=-0.45)
    b.box((-0.62, foot[1] - 0.10, foot[0] - (gb - gf) / 2 - 0.12), (0.62, foot[1] + 0.12, foot[0] + (gb - gf) / 2 + 0.22), pf)
    # the selector on the left over the grip: safe, single, auto
    b.rod(-0.60, -0.52, 0.30, 'frame', axis=0, at=(-1.05, 8.20))
    b.box((-0.66, -1.12, 8.10), (-0.60, -0.62, 8.30), 'steel')


def _stock(b):
    """The fixed polymer stock off the end cap: in slices down to the rubber butt pad."""
    z = RCVR_END
    end = STOCK_END - 0.55
    while z < end - 1e-6:
        e = 0.02
        slope = max(abs(stock_line(z + e)[i] - stock_line(z)[i]) / e for i in (0, 1))
        z1 = min(z + float(np.clip(0.06 / max(slope, 1e-3), 0.12, 0.45)), end)
        if end - z1 < 0.08:
            z1 = end
        top, bot = stock_line((z + z1) / 2)
        u = (z - RCVR_END) / (STOCK_END - RCVR_END)
        half = 0.58 + 0.20 * u
        b.box((-half, bot + 0.06, z), (half, top, z1), 'stock', axis=2, seam=(2,))
        b.box((-half + 0.10, bot, z), (half - 0.10, top + 0.05, z1), 'stock', axis=2, seam=(2,))
        z = z1
    top, bot = stock_line(STOCK_END - 0.6)
    b.box((-0.82, bot - 0.05, end), (0.82, top + 0.04, STOCK_END), 'pad')
    for s in (-1, 1):                                                                             # the sling loop
        x0, x1 = sorted((s * 0.62, s * 0.70))
        b.box((x0, -0.20, RCVR_END + 0.10), (x1, 0.20, RCVR_END + 0.60), 'steel')


def magazine(m):
    """The curved 30-round magazine: pressed steel with ribs down its sides, curving forward as it drops. Origin:
    the front of the well's mouth (WELL_Y, MAG_Z[0])."""
    b = Inch(m)
    L = MAG_Z[1] - MAG_Z[0]
    up = RCVR_BOT + 0.45 - WELL_Y
    b.box((-0.46, 0.0, 0.05), (0.46, up - 0.18, L - 0.05), 'mag')
    b.box((-0.30, up - 0.18, 0.15), (0.30, up, 1.10), 'mag')                                      # feed lips
    # straight for its top third, then curving forward: a bar leaned 22.5 degrees
    knee = -2.40
    b.box((-0.46, knee, 0.0), (0.46, 0.0, L), 'mag', axis=1, seam=(1,))
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 0.46, sx * 0.48))
        b.box((x0, knee + 0.10, 0.35), (x1, -0.25, 0.55), 'rib')
    drop = 3.70
    a = (L / 2, knee + 0.35)                           # starts up inside the straight part, so the bend is closed
    e = (L / 2 - 0.4142 * drop, knee - drop + 0.35)
    b.seg(a, e, L, 0.92, 'mag', over=0.0)
    lean = (e[0] - a[0]) / (a[1] - e[1])               # z per inch of drop
    b.seg((a[0] - L / 2 + 0.45 + lean * 0.6, a[1] - 0.6), (e[0] - L / 2 + 0.45 - lean * 0.3, e[1] + 0.3),
          0.20, 0.96, 'rib', over=0.0)                 # a rib down both sides
    b.seg(e, (e[0] + lean * 0.18, e[1] - 0.18), L + 0.10, 1.00, 'mag_base', over=0.0)


def handle(m):
    """The cocking handle on the left of the tube, angled up and out, at the front of its slot. It runs back
    HANDLE_TRAVEL and is slapped home."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    z0 = HANDLE_SLOT[0] + 0.05
    b.box((-TUBE_R - 0.18, TUBE_Y - 0.07, z0), (-TUBE_R + 0.04, TUBE_Y + 0.07, z0 + 0.32), 'steel')
    m.seg((b.p(-TUBE_R - 0.10, 0, 0)[0], b.p(0, TUBE_Y, 0)[1]), (b.p(-TUBE_R - 0.95, 0, 0)[0], b.p(0, TUBE_Y + 0.55, 0)[1]),
          IN * 0.16, IN * 0.30, 'steel', z=b.p(0, 0, z0 + 0.16)[2], plane='xy')
    b.box((-TUBE_R - 1.25, TUBE_Y + 0.38, z0 - 0.08), (-TUBE_R - 0.80, TUBE_Y + 0.78, z0 + 0.40), 'knob')


def round_9mm(m):
    """One 9x19mm round, 1.17" long: a brass case and a full metal jacket. Origin: the middle of the case head,
    lying along z with the bullet toward -z."""
    b = Inch(m)
    b.rod(-0.75, 0.0, 0.195, 'case', axis=2)
    b.rod(-1.00, -0.75, 0.178, 'jacket', axis=2)
    b.rod(-1.17, -1.00, 0.11, 'jacket', axis=2)


def look(wear=0.4):
    """Black-painted pressed steel, a black polymer grip frame, handguard and stock, a dark grey drum sight."""
    def rcvr(f):
        return P.anodised(f, '#1b1c1f', wear_to='#5a5f65')

    def cap(f):
        return P.anodised(f, '#161719', wear_to='#565b61')

    def rib(f):
        return P.anodised(f, '#222327', wear_to='#666b71')

    def barrel(f):
        return P.anodised(f, '#18191c', wear_to='#5b6066')

    def port(f):
        return P.plain(f, '#070707', 0.05)

    def guard(f):
        return P.polymer(f, '#151516', 0.12)

    def groove(f):
        return P.polymer(f, '#0f0f10', 0.10)

    def frame(f):
        return P.polymer(f, '#171718', 0.10)

    def stipple(f):
        c = P.polymer(f, '#121213', 0.30)
        dots = (np.sin(f.X * 70) * np.sin(f.Y * 70 + f.Z * 70)) > 0.55
        return np.where(dots[..., None], c * 0.6, c)

    def stock(f):
        return P.polymer(f, '#161617', 0.11)

    def pad(f):
        return P.rubber(f, '#111111')

    def drum(f):
        return P.anodised(f, '#26282c', wear_to='#6a6f75')

    def mag(f):
        return P.anodised(f, '#1c1d20', wear_to='#62676d')

    def mag_base(f):
        return P.anodised(f, '#18191b', wear_to='#5d6268')

    def steel(f):
        return P.steel(f, 0.20, tint='#7d8186', spread=0.08)

    def knob(f):
        return P.polymer(f, '#121213', 0.10)

    def screw(f):
        return P.steel(f, 0.24, spread=0.08)

    def post(f):
        return P.anodised(f, '#0c0c0d')

    def bore(f):
        return P.plain(f, '#050505', 0.02)

    def case(f):
        return P.brass(f)

    def jacket(f):
        return P.mix(P.brass(f), P.col('#b5673e'), 0.55)

    d = dict(rcvr=rcvr, cap=cap, rib=rib, barrel=barrel, port=port, guard=guard, groove=groove, frame=frame,
             stipple=stipple, stock=stock, pad=pad, drum=drum, mag=mag, mag_base=mag_base, steel=steel, knob=knob,
             screw=screw, post=post, bore=bore, case=case, jacket=jacket, flash=flash_paint)
    d['_after'] = [lambda f, c: P.grime(f, c, 0.3 * (0.4 + f.wear)),
                   lambda f, c: P.scratches(f, c, 0.25 * (0.3 + f.wear))]
    return d


def flash(m, z_tip, r=0.8):
    b = Inch(m, (0.0, BORE_Y, Z0))
    L = 1.8 * r
    b.box((-0.02, -1.0 * r, z_tip - L), (0.02, 1.0 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.0 * r, -0.02, z_tip - L), (1.0 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.32 * r, -0.32 * r, z_tip - 0.7 * r), (0.32 * r, 0.32 * r, z_tip), 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    t = np.clip(1 - np.sqrt(a ** 2 + b ** 2) / 0.9, 0, 1)
    return P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)
