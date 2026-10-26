"""The hunting rifle: a Remington 700-style bolt action in .308 Winchester (black synthetic sporter stock, 22"
sporter barrel with open sights, a one-piece scope base on the receiver), drawn from the real thing's
measurements: 41.6" long, a 13.4" length of pull, a round 1.36" receiver and a 90-degree bolt lift.
Measurements are inches from the bore's centre line (y) and the receiver's front face (z, positive back toward
the shooter).

It feeds from a detachable 5-round steel box magazine in bottom metal with a latch inside the front of the guard
(the 700's DM conversion), and the bolt is worked by hand after every shot, and after a fresh magazine on an empty
gun: lifted, run back, pushed home and turned down. The bolt is the moving part (its body, handle and shroud),
drawn apart from the rest so first person can work it: it turns BOLT_LIFT about the bore, then runs back.
"""
import numpy as np

import paint as P
from inch import IN, Inch, picatinny

BORE_Y = 2.2            # where the bore sits in the build frame
Z0 = -9.0               # where the receiver's front face sits

RCVR_R = 0.68           # the round receiver (1.36" across)
RCVR_LEN = 6.90         # front ring to the back of the bridge; the tang runs on under the shroud
PORT = (1.45, 4.30)     # the ejection port, right side and top
BREECH = 0.95           # the barrel's tenon screws this far into the receiver
MUZZLE = BREECH - 22.0
RAIL_BASE = 0.62        # the one-piece picatinny base: its spine sits here, its teeth top out RAIL_TOP over the bore
RAIL_TOP = RAIL_BASE + 0.37
RAIL_Z = (0.15, 6.70)
SIGHT = 1.35            # the open sights' line over the bore: tall enough to see over the scope base
REAR_SIGHT = -8.0       # the rear sight's notch, out on the barrel
FRONT_SIGHT = MUZZLE + 1.0
GUARD = (5.05, 8.05)    # the trigger guard's front and back
MAG_Z = (1.40, 4.45)    # the magazine well's front and back
WELL_Y = -2.20          # the well's mouth: the underside of the bottom metal
TANG_END = 8.15
STOCK_END = 20.55       # the recoil pad's back face (41.6" overall)
FOREND_TIP = -11.0

BOLT_R = 0.35           # the bolt body (0.70" across)
BOLT_LIFT = 90.0        # degrees the handle turns up to unlock
HANDLE_TRAVEL = 3.80    # inches the bolt runs back when it's opened
HANDLE_ROOT = 6.35      # where the handle leaves the bolt body, resting in its notch at the bridge
KNOB = (1.62, -1.02, 6.95)   # the handle's ball knob when the bolt is shut (x, y, z)


def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


HAND = frame_point(0.0, -2.55, 10.40)       # middle of the shooting hand on the stock's grip
SUPPORT = frame_point(0.0, -1.55, -5.80)    # the off hand under the forend


def _barrel_r(z):
    """The sporter barrel's radius at z: a 1.06" shank tapering to 0.62" at the muzzle."""
    if z > -1.4:
        return 0.53
    u = (z + 1.4) / (MUZZLE + 1.4)
    return 0.50 - (0.50 - 0.31) * u


def _stock_line(z):
    """The stock's top and bottom lines at z behind the tang: the comb rises from the wrist to a straight
    comb 0.30" over the bore, then drops to the heel; the bottom swells into a sporter pistol grip, tucks in
    behind its cap, then the belly runs straight down to the toe of a 5.1" butt."""
    if z < 11.4:
        u = np.clip((z - TANG_END) / (11.4 - TANG_END), 0, 1)
        top = (RCVR_R - 0.25) - 0.70 * np.sin(u * np.pi) + (0.30 - (RCVR_R - 0.25)) * u
    else:
        top = 0.30 - 0.35 * (z - 11.4) / (STOCK_END - 11.4)
    if z < 11.6:
        u = np.clip((z - GUARD[1]) / (11.6 - GUARD[1]), 0, 1)
        bot = -2.15 - (3.75 - 2.15) * np.sin(u * np.pi / 2)
    elif z < 12.8:
        bot = -3.75 + 0.35 * np.sin((z - 11.6) / 1.2 * np.pi / 2)        # in behind the grip cap
    else:
        bot = -3.40 - 1.45 * (z - 12.8) / (STOCK_END - 12.8)
    return top, bot


def body(m):
    """Everything that stays put: barrel and sights, receiver and scope base, trigger group, stock and pad."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    rc, bar, steel = 'rcvr', 'barrel', 'steel'
    # ---- the barrel: its shank against the recoil lug, then the sporter taper in steps, the crowned muzzle
    b.rod(-1.4, 0.0, 0.53, bar, axis=2)
    steps = 7
    for i in range(steps):
        z1 = -1.4 + (MUZZLE + 1.4) * i / steps
        z0 = -1.4 + (MUZZLE + 1.4) * (i + 1) / steps
        b.rod(z0, z1, _barrel_r((z0 + z1) / 2), bar, axis=2)
    b.rod(MUZZLE - 0.02, MUZZLE + 0.05, 0.155, 'bore', axis=2)
    # ---- open sights: a notched rear leaf on its base, a hooded post on a ramp near the muzzle
    rr = _barrel_r(REAR_SIGHT)
    b.box((-0.22, rr - 0.08, REAR_SIGHT - 0.35), (0.22, rr + 0.10, REAR_SIGHT + 0.35), steel)
    # the leaf, with a wide shallow V notch so it reads at arm's length (a 700 ADL's is narrow and hard to find)
    b.box((-0.48, rr + 0.10, REAR_SIGHT - 0.05), (-0.11, SIGHT + 0.06, REAR_SIGHT + 0.05), steel)
    b.box((0.11, rr + 0.10, REAR_SIGHT - 0.05), (0.48, SIGHT + 0.06, REAR_SIGHT + 0.05), steel)
    b.box((-0.11, rr + 0.10, REAR_SIGHT - 0.05), (0.11, SIGHT - 0.06, REAR_SIGHT + 0.05), steel)
    b.box((-0.025, SIGHT - 0.08, REAR_SIGHT - 0.055), (0.025, SIGHT - 0.06, REAR_SIGHT + 0.055), 'bead')   # a gold line under the notch
    b.rod(-0.42, 0.42, 0.05, 'screw', axis=0, at=(rr + 0.02, REAR_SIGHT - 0.22))
    fr = _barrel_r(FRONT_SIGHT)
    b.seg((FRONT_SIGHT + 1.10, fr - 0.02), (FRONT_SIGHT - 0.15, SIGHT - 0.40), 0.30, 0.30, steel, over=0.05)  # ramp
    b.box((-0.15, fr - 0.04, FRONT_SIGHT - 0.25), (0.15, SIGHT - 0.25, FRONT_SIGHT + 0.25), steel)
    b.box((-0.055, SIGHT - 0.30, FRONT_SIGHT - 0.06), (0.055, SIGHT - 0.06, FRONT_SIGHT + 0.06), 'post')
    b.rod(FRONT_SIGHT + 0.07, FRONT_SIGHT + 0.10, 0.075, 'bead', axis=2, at=(0.0, SIGHT - 0.04))   # a gold bead on the post
    # ---- the recoil lug between barrel and receiver, the round receiver, its port and the bridge
    b.box((-0.62, -1.15, -0.19), (0.62, 0.62, 0.0), rc)
    b.rod(0.0, RCVR_LEN, RCVR_R, rc, axis=2)
    b.box((0.30, -0.10, PORT[0]), (RCVR_R + 0.005, RCVR_R - 0.10, PORT[1]), 'port')        # the port, cut from the top right
    b.box((-0.30, RCVR_R - 0.18, PORT[0]), (0.30, RCVR_R + 0.005, PORT[1]), 'port')
    b.box((RCVR_R - 0.20, -0.32, RCVR_LEN - 0.60), (RCVR_R + 0.005, 0.10, RCVR_LEN + 0.005), 'port')  # handle notch
    b.box((-0.40, -0.55, RCVR_LEN), (0.40, 0.10, TANG_END), rc)                              # the tang
    for z in (0.65, 5.75):                                                                   # base screws
        b.rod(RCVR_R - 0.02, RCVR_R + 0.03, 0.10, 'screw', axis=1, at=(0.0, z))
    # ---- the scope base: a picatinny rail from the front ring over the port to the bridge
    picatinny(b, RAIL_Z[0], RAIL_Z[1], 'top', base=RAIL_BASE, mat='base')
    b.box((-0.31, RCVR_R - 0.12, RAIL_Z[0]), (0.31, RAIL_BASE + 0.02, RAIL_Z[1]), 'base')
    # ---- the safety on the right of the tang, the bolt release in front of the guard
    b.box((0.40, -0.32, 7.05), (0.52, -0.08, 7.55), steel)
    b.box((0.44, -0.12, 7.25), (0.56, 0.02, 7.50), steel)
    # ---- the bottom metal round the magazine well, the trigger guard and trigger
    g0, g1 = GUARD
    m0, m1 = MAG_Z
    b.box((-0.48, WELL_Y, m0 - 0.30), (0.48, -2.06, m0), 'floor')
    for s in (-1, 1):
        b.box((min(s * 0.44, s * 0.52), WELL_Y, m0), (max(s * 0.44, s * 0.52), -2.06, m1), 'floor')
    b.box((-0.48, WELL_Y, m1), (0.48, -2.06, g0 + 0.05), 'floor')
    b.box((-0.43, -2.12, m0), (0.43, -2.06, m1), 'port')                                    # the empty well
    b.box((-0.26, WELL_Y - 0.02, g0), (0.26, -2.06, g1 + 0.1), 'floor')
    b.box((-0.20, -2.95, g0), (0.20, -2.06, g0 + 0.22), 'floor')                             # the guard's bow
    b.box((-0.20, -3.00, g0), (0.20, -2.78, g1 - 0.6), 'floor')
    b.seg((g1 - 0.75, -2.90), (g1, -2.10), 0.22, 0.40, 'floor', over=0.03)
    b.box((-0.13, -2.44, g0 + 0.16), (0.13, -2.20, g0 + 0.38), steel)                        # magazine latch
    b.seg((6.55, -2.06), (6.70, -2.55), 0.22, 0.24, steel, over=0.03)                         # trigger
    b.seg((6.70, -2.55), (6.98, -2.78), 0.20, 0.24, steel, over=0.03)
    # ---- the stock: forend under the barrel, inletted round the receiver, then the wrist, grip and butt
    _stock(b)


def _stock(b):
    st = 'stock'
    # the forend, free of the barrel, narrowing and thinning to its tip
    z = FOREND_TIP
    step = 0.85
    while z < 0.0:
        z1 = min(z + step, 0.0)
        u = (z - FOREND_TIP) / -FOREND_TIP
        half = 0.72 + 0.16 * u
        bot = -1.10 - 0.38 * u
        b.box((-half, bot, z), (half, -0.30, z1), st)
        b.box((-half, -0.30, z), (-half + 0.24, 0.05 + 0.10 * u, z1), st)                    # channel sides
        b.box((half - 0.24, -0.30, z), (half, 0.05 + 0.10 * u, z1), st)
        z = z1
    b.box((-0.66, -1.20, FOREND_TIP - 0.20), (0.66, -0.40, FOREND_TIP), st)                  # rounded tip
    # round the receiver: up its sides to the middle, deep under it for the magazine
    b.box((-0.88, -2.06, 0.0), (0.88, -0.30, TANG_END), st)
    b.box((-0.88, -0.30, 0.0), (-RCVR_R + 0.02, 0.12, TANG_END - 0.4), st)
    b.box((RCVR_R - 0.02, -0.30, 0.0), (0.88, 0.12, PORT[1]), st)
    b.box((RCVR_R - 0.02, -0.30, RCVR_LEN - 0.2), (0.88, -0.36, TANG_END - 0.4), st)
    # the wrist, grip and butt in slices so the lines run true
    z = TANG_END - 0.4
    step = 0.45
    while z < STOCK_END - 1.0:
        z1 = min(z + step, STOCK_END - 1.0)
        top, bot = _stock_line((z + z1) / 2)
        half = 0.66 + 0.16 * np.clip((z - 11.0) / 6.0, 0, 1)
        if z < GUARD[1] + 0.3:
            bot = max(bot, -2.10)
        b.box((-half, bot, z), (half, top, z1), st)
        z = z1
    top, bot = _stock_line(STOCK_END - 1.05)
    b.box((-0.80, bot - 0.03, STOCK_END - 1.0), (0.80, top + 0.03, STOCK_END), 'pad')       # recoil pad
    b.box((-0.82, bot - 0.02, STOCK_END - 1.02), (0.82, top + 0.02, STOCK_END - 0.95), 'spacer')
    _, toe = _stock_line(STOCK_END - 3.2)
    b.rod(toe - 0.18, toe + 0.05, 0.16, 'steel', axis=1, at=(0.0, STOCK_END - 3.2))          # sling studs
    b.rod(-1.48 - 0.24, -1.48 + 0.02, 0.16, 'steel', axis=1, at=(0.0, FOREND_TIP + 2.2))
    # stippled grip panels each side of the grip and under the forend
    for s in (-1, 1):
        b.box((s * 0.67 - 0.02, -2.95, 9.4), (s * 0.67 + 0.02, -1.20, 11.2), 'stipple')
    b.box((-0.62, -1.50, -7.6), (0.62, -1.44, -3.4), 'stipple')


def magazine(m):
    """The detachable 5-round box: blued steel with pressed ribs, a polymer floorplate with a finger lip, the follower
    under its feed lips, and the catch the latch takes at the back. Origin: the top front of the magazine well's
    mouth (WELL_Y, MAG_Z[0])."""
    b = Inch(m)
    L = MAG_Z[1] - MAG_Z[0]
    up = -0.80 - WELL_Y                                                     # its top, up inside the action
    b.box((-0.40, 0.0, 0.05), (0.40, up - 0.22, L - 0.05), 'mag')           # up inside the well
    b.box((-0.31, up - 0.22, 0.25), (0.31, up, 2.10), 'mag')                # feed lips
    b.box((-0.25, up - 0.30, 0.30), (0.25, up - 0.08, L - 0.25), 'follower')
    b.box((-0.44, -0.55, 0.0), (0.44, 0.0, L), 'mag')                       # below the bottom metal
    for s in (-1, 1):                                                       # pressed ribs down each side
        x0, x1 = sorted((s * 0.44, s * 0.465))
        for z in (0.55, L - 0.55):
            b.box((x0, -0.47, z - 0.10), (x1, -0.06, z + 0.10), 'rib')
    b.box((-0.47, -0.71, -0.06), (0.47, -0.55, L + 0.06), 'mag_base')       # floorplate
    b.box((-0.32, -0.80, -0.12), (0.32, -0.62, 0.32), 'mag_base')          # finger lip at the front
    b.box((-0.17, 0.0, L), (0.17, 0.30, L + 0.08), 'steel')                 # the latch's catch


def bolt(m):
    """The bolt: its body (bright, seen in the port), the handle and ball knob out to the right, the shroud and
    cocking piece behind the bridge. This whole part turns BOLT_LIFT about the bore and runs back HANDLE_TRAVEL."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    b.rod(BREECH + 0.05, HANDLE_ROOT + 0.35, BOLT_R, 'bolt', axis=2)
    b.box((BOLT_R - 0.06, -0.10, BREECH + 0.25), (BOLT_R + 0.02, 0.10, BREECH + 0.90), 'bolt')      # extractor
    b.rod(HANDLE_ROOT + 0.35, TANG_END - 0.30, 0.44, 'shroud', axis=2)                             # shroud
    b.rod(TANG_END - 0.30, TANG_END + 0.10, 0.34, 'shroud', axis=2)
    b.rod(TANG_END + 0.10, TANG_END + 0.32, 0.20, 'shroud', axis=2)                               # cocking piece
    kx, ky, kz = KNOB
    m.seg((b.p(0.20, 0, 0)[0], b.p(0, -0.05, 0)[1]), (b.p(kx - 0.18, 0, 0)[0], b.p(0, ky + 0.14, 0)[1]),
          IN * 0.30, IN * 0.30, 'bolt', z=b.p(0, 0, HANDLE_ROOT + 0.3)[2], plane='xy')
    b.rod(kz - 0.30, kz + 0.30, 0.30, 'knob', axis=2, at=(kx, ky))
    b.rod(HANDLE_ROOT + 0.10, kz, 0.17, 'bolt', axis=2, at=(kx - 0.12, ky + 0.10))                 # swept back to it


def round_308(m):
    """One .308 Winchester round, 2.80" long: a brass case with a rim, a body, a shoulder and neck, and a soft
    point bullet. Origin: the middle of the case head, lying along z with the bullet toward -z."""
    b = Inch(m)
    b.rod(-0.05, 0.0, 0.236, 'case', axis=2)
    b.rod(-0.10, -0.05, 0.20, 'case', axis=2)                     # the extractor groove
    b.rod(-1.60, -0.10, 0.234, 'case', axis=2)
    b.rod(-1.78, -1.60, 0.20, 'case', axis=2)                     # shoulder
    b.rod(-2.015, -1.78, 0.172, 'case', axis=2)                   # neck
    b.rod(-2.55, -2.015, 0.154, 'jacket', axis=2)
    b.rod(-2.80, -2.55, 0.10, 'point', axis=2)


def look(wear=0.4):
    """The hunting rifle's materials: matte blued barrel and receiver, a black synthetic stock with stippled
    panels, a bright bolt."""
    def rcvr(f):
        return P.anodised(f, '#17181a', wear_to='#545a60')

    def barrel(f):
        return P.anodised(f, '#18191c', wear_to='#5b6066')

    def base(f):
        return P.anodised(f, '#141517', wear_to='#4c5156')

    def port(f):
        return P.plain(f, '#070707', 0.05)

    def follower(f):
        return P.steel(f, 0.18, tint='#6d7176', spread=0.06)

    def mag(f):
        return P.anodised(f, '#161719', wear_to='#5a5f64')

    def rib(f):
        return P.anodised(f, '#1d1e21', wear_to='#6a6f75')

    def mag_base(f):
        return P.polymer(f, '#121213', 0.16)

    def floor(f):
        return P.anodised(f, '#1b1c1f', wear_to='#62666b')

    def stock(f):
        return P.polymer(f, '#151516', 0.13)

    def stipple(f):
        c = P.polymer(f, '#121213', 0.30)
        dots = (np.sin(f.X * 70) * np.sin(f.Y * 70 + f.Z * 70)) > 0.55
        return np.where(dots[..., None], c * 0.6, c)

    def pad(f):
        c = P.rubber(f, '#111111')
        if abs(f.n[2]) > 0.5:
            y = (f.Y - BORE_Y) / IN
            c = c * (1 - 0.35 * ((y * 2.4) % 1 < 0.3))[..., None]
        return c

    def spacer(f):
        return P.plain(f, '#0e0e0e', 0.05)

    def steel(f):
        return P.steel(f, 0.20, tint='#7d8186', spread=0.08)

    def bolt_paint(f):
        c = P.steel(f, 0.46, tint='#9aa0a6', spread=0.08)
        if abs(f.n[2]) < 0.5:           # engine-turned swirls down the bolt body
            sw = (np.sin(f.Z / IN * 9.0) * np.cos((f.X + f.Y) / IN * 9.0)) > 0.4
            c = np.where(sw[..., None], c * 0.85, c)
        return c

    def shroud(f):
        return P.anodised(f, '#1a1b1d', wear_to='#5d6166')

    def knob(f):
        return P.anodised(f, '#1b1c1e', wear_to='#6a6f75')

    def screw(f):
        return P.steel(f, 0.24, spread=0.08)

    def post(f):
        return P.anodised(f, '#0c0c0d')

    def bead(f):
        return P.brass(f)

    def bore(f):
        return P.plain(f, '#050505', 0.02)

    def case(f):
        return P.brass(f)

    def jacket(f):
        return P.mix(P.brass(f), P.col('#b5673e'), 0.55)

    def point(f):
        return P.plain(f, '#77787a', 0.10)

    d = dict(rcvr=rcvr, barrel=barrel, base=base, port=port, follower=follower, mag=mag, rib=rib,
             mag_base=mag_base, floor=floor, stock=stock,
             stipple=stipple, pad=pad, spacer=spacer, steel=steel, bolt=bolt_paint, shroud=shroud, knob=knob,
             screw=screw, post=post, bead=bead, bore=bore, case=case, jacket=jacket, point=point,
             flash=flash_paint)
    d['_after'] = [lambda f, c: P.grime(f, c, 0.3 * (0.4 + f.wear)),
                   lambda f, c: P.scratches(f, c, 0.25 * (0.3 + f.wear))]
    return d


def suppressor(m):
    """The rifle suppressor as it goes on this gun: a direct-thread .30 calibre can (an Omega 36M sort, 1.6" across,
    7.2" long, as long as the item model's limits allow) screwed onto the threaded muzzle in place of the AR's ratchet
    collar. Its own inches, mount at the crown, the can running on to -z."""
    b = Inch(m)
    b.rod(-0.30, 0.06, 0.40, 'steel', axis=2)                # the thread mount, seated against the crown
    b.rod(-0.48, -0.30, 0.70, 'cap', axis=2)                 # the rear cap, stepped down to the mount
    b.rod(-0.78, -0.48, 0.80, 'cap', axis=2)
    b.rod(-0.86, -0.78, 0.76, 'park', axis=2)                # the seam where the tube screws into the rear cap
    b.rod(-2.60, -0.86, 0.78, 'can', axis=2)
    b.rod(-4.40, -2.60, 0.78, 'can_mark', axis=2)            # the maker's engraving
    b.rod(-6.65, -4.40, 0.78, 'can', axis=2)
    b.rod(-6.73, -6.65, 0.76, 'park', axis=2)                # and into the front cap
    b.rod(-7.03, -6.73, 0.79, 'cap', axis=2)
    b.rod(-7.15, -7.03, 0.68, 'cap', axis=2)                 # its chamfered face, the bore hole in it


def flash(m, z_tip, r=1.0):
    """Muzzle flash: crossed glowing planes and a core just past the muzzle (inches from the receiver front)."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    L = 2.2 * r
    b.box((-0.02, -1.1 * r, z_tip - L), (0.02, 1.1 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.1 * r, -0.02, z_tip - L), (1.1 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.35 * r, -0.35 * r, z_tip - 0.8 * r), (0.35 * r, 0.35 * r, z_tip), 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    t = np.clip(1 - np.sqrt(a ** 2 + b ** 2) / 0.9, 0, 1)
    return P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)
