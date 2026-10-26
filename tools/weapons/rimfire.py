""".22 rifle: a small-game semi-auto carbine in .22 Long Rifle (a Ruger 10/22 Carbine sort): a blued 18.5" carbine
barrel held in an alloy receiver, a birch carbine stock with a barrel band, a gold bead front sight and a folding
leaf rear, and a 10-round rotary magazine that sits flush in the stock. Drawn from the real thing's measurements:
37" overall, a 13.5" length of pull, a receiver about 1.1" wide and 6.4" long. Measurements are inches from the
bore's centre line (y) and the receiver's front face (z, positive back toward the shooter).

It's blowback: every shot throws the bolt back and chambers the next round, so the only part worked by hand is the
little charging handle on the right, pulled back after a fresh magazine on an empty gun. It's the moving part
(the handle and the bolt face seen in the port), drawn apart so first person can work it.
"""
import numpy as np

import finishes as F
import paint as P
from inch import IN, Inch

BORE_Y = 2.2            # where the bore sits in the build frame
Z0 = -6.0               # where the receiver's front face sits

RCVR_LEN = 6.40         # the receiver, front face to its back
RCVR_HALF = 0.55        # half its width (1.1")
RCVR_TOP = 0.62         # the top of its rounded back, over the bore
RCVR_BOT = -0.82        # its flat underside
PORT = (0.95, 2.70)     # the ejection port on the right
SLOT = (0.95, 3.80)     # the charging handle's slot under the port
HANDLE_Y = -0.34        # the handle's height
HANDLE_TRAVEL = 2.30    # how far the handle comes back
BARREL_LEN = 18.5
MUZZLE = -BARREL_LEN + 0.25     # the shank runs a quarter inch into the receiver
SIGHT = 0.78            # the open sights' line over the bore
REAR_SIGHT = -7.6       # the rear leaf, out on the barrel
FRONT_SIGHT = MUZZLE + 0.55
BAND = (-12.35, -11.85)  # the barrel band round barrel and forend
FOREND_TIP = -12.80
MAG_Z = (0.55, 2.45)    # the magazine well's front and back
WELL_Y = -1.05          # the magazine's floor sits flush with the stock here
GUARD = (3.10, 5.75)    # the trigger guard's front and back
TRIGGER_Z = 4.95        # the trigger: straight down, then its tip forward
STOCK_END = 18.70       # the butt plate's back face (37" overall)


def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


HAND = frame_point(0.0, -2.05, 7.10)        # the shooting hand on the wrist
SUPPORT = frame_point(0.0, -1.35, -5.50)    # the off hand under the forend


def barrel_r(z):
    """The carbine barrel's radius: a 0.92" shank, tapering to 0.625" at the muzzle."""
    if z > -1.5:
        return 0.46
    u = (z + 1.5) / (MUZZLE + 1.5)
    return 0.44 - (0.44 - 0.3125) * u


def stock_line(z):
    """The carbine stock's top and bottom behind the receiver: a straight comb 1.0" under the sights, a gentle
    half-pistol grip, then the belly running down to the toe of a 4.7" butt."""
    if z < 8.6:
        u = np.clip((z - RCVR_LEN) / (8.6 - RCVR_LEN), 0, 1)
        top = (RCVR_TOP - 0.38) - 0.30 * np.sin(u * np.pi) + (-0.20 - (RCVR_TOP - 0.38)) * u
    else:
        top = -0.20 - 0.55 * (z - 8.6) / (STOCK_END - 8.6)
    if z < 8.4:
        u = np.clip((z - GUARD[1]) / (8.4 - GUARD[1]), 0, 1)
        bot = -1.75 - (2.95 - 1.75) * np.sin(u * np.pi / 2)
    elif z < 9.4:
        bot = -2.95 + 0.18 * np.sin((z - 8.4) / 1.0 * np.pi / 2)
    else:
        bot = -2.77 - 2.68 * (z - 9.4) / (STOCK_END - 9.4)
    return top, bot


def body(m):
    """Everything that stays put: barrel, sights and band; receiver, trigger group and stock."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    rc, bar, steel = 'rcvr', 'barrel', 'steel'
    # ---- the barrel, its taper in steps, a recessed crown
    b.rod(-1.5, 0.25, 0.46, bar, axis=2)
    steps = 6
    for i in range(steps):
        z1 = -1.5 + (MUZZLE + 1.5) * i / steps
        z0 = -1.5 + (MUZZLE + 1.5) * (i + 1) / steps
        b.rod(z0, z1, barrel_r((z0 + z1) / 2), bar, axis=2)
    b.rod(MUZZLE - 0.02, MUZZLE + 0.04, 0.10, 'bore', axis=2)
    # ---- the rear leaf (a folding sporting leaf with an elevation step) and the gold bead on its ramp
    rr = barrel_r(REAR_SIGHT)
    b.box((-0.20, rr - 0.06, REAR_SIGHT - 0.55), (0.20, rr + 0.07, REAR_SIGHT + 0.45), steel)     # its spring base
    b.box((-0.36, rr + 0.07, REAR_SIGHT - 0.05), (-0.09, SIGHT + 0.05, REAR_SIGHT + 0.05), steel)
    b.box((0.09, rr + 0.07, REAR_SIGHT - 0.05), (0.36, SIGHT + 0.05, REAR_SIGHT + 0.05), steel)
    b.box((-0.09, rr + 0.07, REAR_SIGHT - 0.05), (0.09, SIGHT - 0.07, REAR_SIGHT + 0.05), steel)
    b.box((-0.16, rr + 0.07, REAR_SIGHT + 0.12), (0.16, rr + 0.18, REAR_SIGHT + 0.42), 'steel')   # the elevator
    fr = barrel_r(FRONT_SIGHT)
    b.seg((FRONT_SIGHT + 0.95, fr - 0.02), (FRONT_SIGHT - 0.10, SIGHT - 0.30), 0.26, 0.26, steel, over=0.05)
    b.box((-0.12, fr - 0.04, FRONT_SIGHT - 0.20), (0.12, SIGHT - 0.20, FRONT_SIGHT + 0.20), steel)
    b.box((-0.045, SIGHT - 0.24, FRONT_SIGHT - 0.05), (0.045, SIGHT - 0.06, FRONT_SIGHT + 0.05), 'post')
    b.rod(FRONT_SIGHT + 0.06, FRONT_SIGHT + 0.09, 0.065, 'bead', axis=2, at=(0.0, SIGHT - 0.04))
    # ---- the receiver: flat sides, a rounded back, the port and the handle's slot on the right
    h = RCVR_HALF
    b.box((-h, RCVR_BOT, 0.0), (h, RCVR_TOP - 0.22, RCVR_LEN), rc)
    b.box((-h + 0.13, RCVR_TOP - 0.22, 0.0), (h - 0.13, RCVR_TOP, RCVR_LEN), rc)
    b.box((-h + 0.05, RCVR_TOP - 0.24, 0.0), (h - 0.05, RCVR_TOP - 0.08, RCVR_LEN), rc)
    b.box((-h - 0.005, RCVR_TOP - 0.40, RCVR_LEN - 0.35), (h + 0.005, RCVR_TOP - 0.10, RCVR_LEN), rc)   # rounded tail
    b.box((h - 0.12, -0.20, PORT[0]), (h + 0.005, 0.30, PORT[1]), 'port')
    b.box((h - 0.10, HANDLE_Y - 0.10, SLOT[0]), (h + 0.005, HANDLE_Y + 0.10, SLOT[1]), 'port')
    for z in (0.75, 5.55):                                                       # the tapped scope-base holes
        b.rod(RCVR_TOP - 0.02, RCVR_TOP + 0.01, 0.08, 'screw', axis=1, at=(0.0, z))
    b.box((-0.16, RCVR_BOT - 0.10, 0.20), (0.16, RCVR_BOT, 0.70), steel)          # the V-block screw boss
    # the bolt hold-open on the left, the cross pins through the receiver
    b.box((-h - 0.05, -0.55, 4.35), (-h, -0.30, 4.80), steel)
    for z in (3.40, 5.30):
        for s in (-1, 1):
            x0, x1 = sorted((s * h, s * (h + 0.02)))
            b.box((x0, -0.66, z - 0.06), (x1, -0.54, z + 0.06), 'screw')
    # ---- the trigger housing under the receiver, the guard, the magazine release and the cross-bolt safety
    g0, g1 = GUARD
    b.box((-0.38, -1.12, MAG_Z[1]), (0.38, RCVR_BOT, g1 + 0.30), 'housing')
    b.box((-0.20, -1.85, g0), (0.20, -1.12, g0 + 0.22), 'housing')               # the guard's bow
    b.box((-0.20, -1.90, g0), (0.20, -1.70, g1 - 0.55), 'housing')
    b.seg((g1 - 0.75, -1.80), (g1, -1.12), 0.20, 0.40, 'housing', over=0.03)
    b.seg((TRIGGER_Z, -1.12), (TRIGGER_Z, -1.38), 0.20, 0.22, steel, over=0.03)  # trigger: back in the guard,
    b.seg((TRIGGER_Z, -1.38), (TRIGGER_Z - 0.12, -1.66), 0.18, 0.22, steel, over=0.03)   # tip curving forward
    b.box((-0.12, -1.48, g0 + 0.20), (0.12, -1.12, g0 + 0.44), steel)            # magazine release lever
    b.rod(-0.48, 0.48, 0.09, steel, axis=0, at=(-1.00, g0 + 0.62))                # cross-bolt safety
    b.rod(0.44, 0.50, 0.06, 'red', axis=0, at=(-1.00, g0 + 0.62))                # its red band, showing it's off
    _stock(b)


def _stock(b):
    st = 'stock'
    # the forend, round-bellied under the barrel, its sides up to the barrel's middle
    z = FOREND_TIP
    step = 0.85
    while z < 0.0:
        z1 = min(z + step, 0.0)
        u = (z - FOREND_TIP) / -FOREND_TIP
        half = 0.62 + 0.10 * u
        bot = -0.95 - 0.30 * u
        b.box((-half, bot + 0.12, z), (half, -0.20, z1), st, axis=2, seam=(2,))
        b.box((-half + 0.12, bot, z), (half - 0.12, -0.20, z1), st, axis=2, seam=(2,))
        b.box((-half, -0.20, z), (-half + 0.22, 0.02 + 0.04 * u, z1), st, axis=2, seam=(2,))
        b.box((half - 0.22, -0.20, z), (half, 0.02 + 0.04 * u, z1), st, axis=2, seam=(2,))
        z = z1
    b.box((-0.55, -1.02, FOREND_TIP - 0.18), (0.55, -0.35, FOREND_TIP), st)                 # rounded tip
    # the barrel band round barrel and forend
    z0, z1 = BAND
    b.box((-0.72, -1.25, z0), (0.72, 0.36, z1), 'band')
    b.box((-0.30, 0.30, z0), (0.30, 0.48, z1), 'band')
    b.rod(-0.75, 0.75, 0.05, 'screw', axis=0, at=(-0.95, (z0 + z1) / 2))
    # round the receiver: up its sides, under it round the magazine well
    b.box((-0.72, -1.05, 0.0), (0.72, RCVR_BOT, RCVR_LEN), st)
    b.box((-0.72, RCVR_BOT, 0.0), (-RCVR_HALF + 0.01, -0.05, RCVR_LEN), st)
    b.box((RCVR_HALF - 0.01, RCVR_BOT, 0.0), (0.72, HANDLE_Y - 0.14, RCVR_LEN), st)
    b.box((-0.66, -1.75, GUARD[0] - 0.40), (0.66, -1.05, GUARD[0]), st)                     # in front of the guard
    b.box((-0.66, -1.75, GUARD[1] - 0.10), (0.66, -1.05, GUARD[1] + 0.40), st)              # behind it
    b.box((-0.60, -1.30, MAG_Z[0] - 0.40), (0.60, -1.05, MAG_Z[0]), st)                    # round the mag well's front
    # the wrist, grip and butt in slices, thinner where the lines curve fastest so they step by no more than
    # six hundredths of an inch, each with a narrower one over it for the rounded edges
    z = RCVR_LEN - 0.1
    end = STOCK_END - 0.25
    while z < end - 1e-6:
        e = 0.02
        slope = max(abs(stock_line(z + e)[i] - stock_line(z)[i]) / e for i in (0, 1))
        z1 = min(z + float(np.clip(0.06 / max(slope, 1e-3), 0.10, 0.45)), end)
        if end - z1 < 0.08:
            z1 = end
        top, bot = stock_line((z + z1) / 2)
        half = 0.62 + 0.18 * np.clip((z - 9.0) / 6.0, 0, 1)
        if z < GUARD[1] + 0.4:
            bot = max(bot, -1.75)
        b.box((-half, bot + 0.05, z), (half, top, z1), st, axis=2, seam=(2,))
        b.box((-half + 0.10, bot, z), (half - 0.10, top + 0.04, z1), st, axis=2, seam=(2,))
        z = z1
    top, bot = stock_line(STOCK_END - 0.3)
    b.box((-0.80, bot - 0.02, STOCK_END - 0.25), (0.80, top + 0.02, STOCK_END), 'buttplate')
    # sling swivel studs at the toe and under the band
    _, toe = stock_line(STOCK_END - 2.6)
    b.rod(toe - 0.20, toe + 0.04, 0.12, 'steel', axis=1, at=(0.0, STOCK_END - 2.6))
    b.rod(-1.50, -1.20, 0.12, 'steel', axis=1, at=(0.0, (BAND[0] + BAND[1]) / 2))


def magazine(m):
    """The 10-round rotary magazine: a black polymer body that sits flush with the stock, its smoked see-through
    back plate showing the rotor and rounds, and the feed lips up inside the well. Origin: the front of the well's
    mouth (WELL_Y, MAG_Z[0])."""
    b = Inch(m)
    L = MAG_Z[1] - MAG_Z[0]
    up = RCVR_BOT - 0.15 - WELL_Y                           # its top, up inside the receiver
    b.box((-0.44, 0.0, 0.0), (0.44, up - 0.20, L), 'mag')
    b.box((-0.25, up - 0.20, 0.15), (0.25, up, 1.20), 'mag')          # feed lips
    b.box((-0.40, -0.12, 0.05), (0.40, 0.0, L - 0.05), 'mag')         # the floor, flush with the stock
    b.box((-0.36, 0.05, L - 0.04), (0.36, up - 0.30, L + 0.005), 'window')   # the clear back plate
    b.rod(L - 0.01, L + 0.02, 0.10, 'screw', axis=2, at=(0.0, (up - 0.25) / 2))  # the rotor's hub
    b.box((-0.30, -0.16, 0.20), (0.30, -0.10, 0.60), 'mag')           # a grip ridge at the front of the floor


def handle(m):
    """The charging handle out of its slot on the right, with the bolt's face seen through the port. It runs back
    HANDLE_TRAVEL and springs home."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    b.box((RCVR_HALF - 0.18, -0.18, PORT[0] + 0.05), (RCVR_HALF - 0.08, 0.26, PORT[1] - 0.05), 'bolt')
    b.box((RCVR_HALF - 0.10, HANDLE_Y - 0.08, SLOT[0] + 0.05), (RCVR_HALF + 0.10, HANDLE_Y + 0.08, SLOT[0] + 0.42), 'bolt')
    b.box((RCVR_HALF + 0.10, HANDLE_Y - 0.12, SLOT[0] + 0.02), (RCVR_HALF + 0.42, HANDLE_Y + 0.12, SLOT[0] + 0.46), 'knob')


def round_22(m):
    """One .22 Long Rifle round, 0.98" long: a rimmed brass case and a round-nosed lead bullet. Origin: the middle of
    the case head, lying along z with the bullet toward -z."""
    b = Inch(m)
    b.rod(-0.043, 0.0, 0.139, 'case', axis=2)
    b.rod(-0.613, -0.043, 0.113, 'case', axis=2)
    b.rod(-0.86, -0.613, 0.112, 'lead', axis=2)
    b.rod(-0.98, -0.86, 0.07, 'lead', axis=2)


def look(wear=0.4):
    """Blued barrel and band, a satin black alloy receiver, a birch stock with a warm stain, a black polymer trigger
    housing and magazine."""
    def rcvr(f):
        return P.anodised(f, '#1a1b1e', wear_to='#5c6167')

    def barrel(f):
        return F.blued(f)

    def band(f):
        return F.blued(f, polish=0.3)

    def port(f):
        return P.plain(f, '#070707', 0.05)

    def housing(f):
        return P.polymer(f, '#141415', 0.10)

    def mag(f):
        return P.polymer(f, '#111112', 0.12)

    def window(f):
        return P.plain(f, '#3c3a34', 0.08)

    def stock(f):
        return F.walnut(f, 'hardwood', 'z')

    def buttplate(f):
        return P.polymer(f, '#121213', 0.14)

    def steel(f):
        return P.steel(f, 0.20, tint='#7d8186', spread=0.08)

    def bolt(f):
        return P.steel(f, 0.50, tint='#a0a6ac', spread=0.08)

    def knob(f):
        return P.steel(f, 0.40, tint='#8d9399', spread=0.08)

    def screw(f):
        return P.steel(f, 0.24, spread=0.08)

    def post(f):
        return P.anodised(f, '#0c0c0d')

    def bead(f):
        return P.brass(f)

    def red(f):
        return P.plain(f, '#a3201b', 0.05)

    def bore(f):
        return P.plain(f, '#050505', 0.02)

    def case(f):
        return P.brass(f)

    def lead(f):
        return P.plain(f, '#6f6d6a', 0.10)

    d = dict(rcvr=rcvr, barrel=barrel, band=band, port=port, housing=housing, mag=mag, window=window, stock=stock,
             buttplate=buttplate, steel=steel, bolt=bolt, knob=knob, screw=screw, post=post,
             bead=bead, red=red, bore=bore, case=case, lead=lead, flash=flash_paint)
    d['_spec'] = {'barrel': F.METAL_SPEC['blued'], 'band': F.METAL_SPEC['blued'],
                  'stock': F.WOOD_SPEC['satin'] + (12,)}
    d['_after'] = [lambda f, c: P.grime(f, c, 0.3 * (0.4 + f.wear)),
                   lambda f, c: P.scratches(f, c, 0.25 * (0.3 + f.wear))]
    return d


def flash(m, z_tip, r=0.55):
    """A small muzzle flash: a .22 barely flashes."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    L = 1.4 * r
    b.box((-0.02, -1.0 * r, z_tip - L), (0.02, 1.0 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.0 * r, -0.02, z_tip - L), (1.0 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.3 * r, -0.3 * r, z_tip - 0.6 * r), (0.3 * r, 0.3 * r, z_tip), 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    t = np.clip(1 - np.sqrt(a ** 2 + b ** 2) / 0.9, 0, 1)
    return P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)
