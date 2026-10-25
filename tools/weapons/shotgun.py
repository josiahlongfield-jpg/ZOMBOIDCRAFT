"""The shotgun: a Remington 870-style 12 gauge pump (black synthetic stock and ribbed forend, 18.5" plain
barrel with a bead, a 4-shell tube magazine under it), drawn from the real thing's measurements: 38.5"
long, a 14" length of pull, an 8.4" receiver and a 3.6" pump stroke. Measurements are inches from the
bore's centre line (y) and the receiver's front face (z, positive back toward the shooter).

There is no detachable magazine: shells go in one at a time through the loading port under the receiver.
The forend is the moving part (its action bars run the bolt), drawn apart from the rest so first person
can pump it; the bolt shows in the ejection port and rides back with it.
"""
import numpy as np

import paint as P
from inch import IN, Inch

BORE_Y = 2.2            # where the bore sits in the build frame
Z0 = -9.0               # where the receiver's front face sits

RCVR_LEN = 8.40
RCVR_TOP = 0.62
RCVR_BOT = -1.62
RCVR_W = 0.64           # half its width
BREECH = 0.60           # the breech face, just inside the receiver: the 18.5" barrel runs from here
MUZZLE = BREECH - 18.5
BARREL_R = 0.42
TUBE_Y = -1.12          # the magazine tube's centre line under the bore
TUBE_R = 0.47
TUBE_END = -15.30       # the front of the tube, where the cap screws on over the barrel lug
CAP_END = -16.25
FOREND = (-9.30, -1.05)     # the forend's front and back at rest
FOREND_TOP, FOREND_BOT, FOREND_W = -0.40, -2.18, 0.98
PORT = (1.30, 4.15)     # the ejection port, right side
SIGHT = 0.56            # the line of sight above the bore: down the receiver's top to the bead
BEAD_R = 0.09           # the bead: big enough to find at a glance, as on a riot gun
BEAD_Z = MUZZLE + 0.325
BEAD_TOP = BARREL_R + 0.06 + BEAD_R
GUARD = (3.55, 6.55)    # the trigger guard's front and back
STOCK_END = 20.60       # the butt plate's back face (38.5" overall)

HANDLE_TRAVEL = 3.60    # inches the forend (and the bolt with it) runs back when it's pumped


def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


# the loose shell first person pushes into the loading port: its head, lying along the tube just under the port
# with the crimp toward the muzzle
LOOSE_SHELL = (0.0, RCVR_BOT - 0.45, 3.6)

HAND = frame_point(0.0, -2.70, 10.10)       # middle of the shooting hand on the stock's grip
SUPPORT = frame_point(0.0, -1.70, -5.40)    # the off hand round the middle of the forend


def _stock_line(z):
    """The stock's top and bottom lines at z, from the receiver's back to the butt: the top dips at the wrist
    to the comb (1.5" under the line of sight, 2.5" at the heel); the bottom swells into a semi-pistol grip,
    then the belly runs straight down to the toe of a 5.2" butt."""
    if z < 10.2:
        u = (z - RCVR_LEN) / (10.2 - RCVR_LEN)
        top = RCVR_TOP - 0.10 - (RCVR_TOP - 0.10 - (SIGHT - 1.45)) * (1 - np.cos(u * np.pi)) / 2
    else:
        top = (SIGHT - 1.45) - 1.0 * (z - 10.2) / (STOCK_END - 10.2)
    if z < 11.0:
        u = (z - RCVR_LEN) / (11.0 - RCVR_LEN)
        bot = RCVR_BOT - 0.42 - (3.90 + RCVR_BOT - 0.42) * np.sin(u * np.pi / 2)
    else:
        bot = -3.90 - 3.20 * (z - 11.0) / (STOCK_END - 11.0)
    return top, bot


def body(m):
    """Everything that stays put: barrel, tube and cap, receiver, trigger group, stock and pad."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    rc, steel, bar = 'rcvr', 'steel', 'barrel'
    # ---- the barrel: its thick chamber end in the receiver, the plain barrel, the muzzle and bead
    b.rod(-2.8, BREECH, 0.56, bar, axis=2)
    b.rod(MUZZLE, -2.8, BARREL_R, bar, axis=2)
    b.rod(MUZZLE - 0.02, MUZZLE + 0.06, 0.37, 'bore', axis=2)          # the dark 12-bore at the muzzle
    b.rod(BEAD_Z - 0.075, BEAD_Z + 0.075, BEAD_R, 'bead', axis=2, at=(0.0, BARREL_R + 0.06))
    # ---- the magazine tube under it, the lug that clamps the barrel to it, the knurled cap
    b.rod(TUBE_END, 0.0, TUBE_R, 'tube', axis=2, at=(0.0, TUBE_Y))
    b.box((-0.30, TUBE_Y + 0.25, TUBE_END + 0.25), (0.30, -0.25, TUBE_END + 0.95), bar)   # barrel lug
    b.rod(CAP_END, TUBE_END, 0.53, 'magcap', axis=2, at=(0.0, TUBE_Y))
    b.rod(CAP_END - 0.16, CAP_END, 0.18, steel, axis=2, at=(0.0, TUBE_Y - 0.36))          # sling stud
    # ---- the receiver: a long box with a crowned top, the port on the right, the loading port underneath
    w = RCVR_W
    b.box((-w, RCVR_BOT, 0.0), (w, RCVR_TOP - 0.10, RCVR_LEN), rc)
    b.box((-w + 0.10, RCVR_TOP - 0.10, 0.0), (w - 0.10, RCVR_TOP, RCVR_LEN - 0.30), rc)  # the crown
    b.box((-0.07, RCVR_TOP - 0.02, 0.2), (0.07, RCVR_TOP + 0.005, RCVR_LEN - 0.4), 'groove')   # sighting groove
    b.box((w - 0.02, -0.62, PORT[0]), (w + 0.005, 0.34, PORT[1]), 'port')              # the port's dark opening
    b.box((-0.48, RCVR_BOT - 0.005, 0.70), (0.48, RCVR_BOT + 0.02, 4.05), 'port')       # loading port
    b.box((-0.40, RCVR_BOT + 0.02, 0.85), (0.40, RCVR_BOT + 0.10, 3.85), 'lifter')      # the shell lifter in it
    for z in (1.05, 5.10):                                                               # trigger-plate pins
        b.rod(-w - 0.02, w + 0.02, 0.10, 'screw', axis=0, at=(-1.15, z))
    # ---- the trigger plate, guard, trigger, crossbolt safety, the action-bar lock lever
    b.box((-0.52, RCVR_BOT - 0.42, 3.05), (0.52, RCVR_BOT, 7.95), 'trigplate')
    g0, g1 = GUARD
    b.box((-0.24, -3.32, g0), (0.24, RCVR_BOT - 0.42, g0 + 0.24), 'trigplate')          # the guard's front
    b.box((-0.24, -3.32, g0), (0.24, -3.08, g1), 'trigplate')                           # its bow
    b.seg((g1 - 0.2, -3.20), (g1 + 0.45, RCVR_BOT - 0.42), 0.22, 0.48, 'trigplate', over=0.03)
    b.seg((4.55, RCVR_BOT - 0.42), (4.75, -2.62), 0.24, 0.26, steel, over=0.03)        # trigger
    b.seg((4.75, -2.62), (5.05, -2.95), 0.22, 0.26, steel, over=0.03)
    b.rod(-0.60, 0.60, 0.13, steel, axis=0, at=(-2.22, 6.10))                           # crossbolt safety
    b.box((-0.60, -2.42, 3.30), (-0.52, -2.18, 3.70), steel)                            # action-bar lock, left
    # ---- the stock: wrist and semi-pistol grip, then the butt, built up in slices so its lines run true
    z = RCVR_LEN
    step = 0.45
    while z < STOCK_END - 1.0:
        z1 = min(z + step, STOCK_END - 1.0)
        top, bot = _stock_line((z + z1) / 2)
        half = 0.65 + 0.17 * np.clip((z - 10.5) / 6.0, 0, 1)
        b.box((-half, bot, z), (half, top, z1), 'stock')
        z = z1
    top, bot = _stock_line(STOCK_END - 1.05)
    b.box((-0.80, bot - 0.03, STOCK_END - 1.0), (0.80, top + 0.03, STOCK_END), 'pad')   # recoil pad
    b.box((-0.82, bot - 0.02, STOCK_END - 1.02), (0.82, top + 0.02, STOCK_END - 0.95), 'spacer')
    _, toe = _stock_line(STOCK_END - 3.2)
    b.rod(toe - 0.18, toe + 0.05, 0.16, steel, axis=1, at=(0.0, STOCK_END - 3.2))                # rear sling stud
    for s in (-1, 1):                                                                    # stock bolt cap pin
        b.rod(s * 0.66, s * 0.70, 0.07, 'screw', axis=0, at=(-0.55, RCVR_LEN + 0.35))


def forend(m):
    """The pump: the ribbed forend round the tube, its two action bars back into the receiver, and the bolt
    they drive, seen in the ejection port. This whole part slides back HANDLE_TRAVEL when it's worked."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    f0, f1 = FOREND
    w = FOREND_W
    b.box((-w, FOREND_BOT + 0.18, f0), (w, FOREND_TOP - 0.12, f1), 'forend')
    b.box((-w + 0.16, FOREND_BOT, f0), (w - 0.16, FOREND_TOP, f1), 'forend')            # rounded off top and bottom
    b.box((-w - 0.02, FOREND_BOT + 0.40, f0 + 0.35), (w + 0.02, FOREND_TOP - 0.34, f1 - 0.45), 'ribs')
    b.box((-0.52, FOREND_TOP - 0.02, f0 - 0.10), (0.52, FOREND_TOP + 0.03, f0 + 0.25), 'forend')   # front lip
    for s in (-1, 1):                                                                    # the action bars
        b.box((s * 0.50 - 0.05, TUBE_Y - 0.12, f1), (s * 0.50 + 0.05, TUBE_Y + 0.12, 2.4), 'steel')
    # the bolt, the bright face of it just showing through the port
    b.box((RCVR_W - 0.10, -0.50, PORT[0] + 0.15), (RCVR_W - 0.03, 0.24, PORT[1] - 0.10), 'bolt')
    b.box((RCVR_W - 0.03, -0.30, PORT[1] - 0.95), (RCVR_W - 0.01, 0.06, PORT[1] - 0.25), 'bolt')    # extractor


def shell(m):
    """One 12-gauge shell, 2.75" long: a brass head and a ribbed red hull, crimped shut. Origin: the middle of
    the head, lying along z with the crimp toward -z."""
    b = Inch(m)
    b.rod(0.0, 0.07, 0.43, 'head', axis=2)                    # the rim
    b.rod(-0.55, 0.0, 0.40, 'head', axis=2)
    b.rod(-2.62, -0.55, 0.39, 'hull', axis=2)
    b.rod(-2.75, -2.62, 0.33, 'crimp', axis=2)


def look(wear=0.4):
    """The shotgun's materials: matte black receiver and barrel, black synthetic stock and forend."""
    def rcvr(f):
        return P.anodised(f, '#18191b', wear_to='#4f5357')

    def barrel(f):
        return P.anodised(f, '#1a1b1d', wear_to='#5d6166')

    def groove(f):
        return P.anodised(f, '#0c0c0d')

    def port(f):
        return P.plain(f, '#070707', 0.05)

    def lifter(f):
        return P.steel(f, 0.16, tint='#6d7176', spread=0.06)

    def tube(f):
        return P.anodised(f, '#1a1b1d', wear_to='#5d6166')

    def magcap(f):
        c = P.anodised(f, '#18191b', wear_to='#55595e')
        if abs(f.n[2]) < 0.5:
            knurl = (np.sin(f.Z * 38) * np.sin((f.X + f.Y) * 38)) > 0.25
            c = np.where(knurl[..., None], c * 0.65, c)
        return c

    def trigplate(f):
        return P.anodised(f, '#1c1d20', wear_to='#62666b')

    def stock(f):
        return P.polymer(f, '#161617', 0.14)

    def forend_paint(f):
        return P.polymer(f, '#171718', 0.12)

    def ribs(f):
        c = forend_paint(f)
        if abs(f.n[0]) > 0.5:          # the grip grooves running round the forend
            z = (f.Z - Z0) / IN
            groove = ((z % 0.42) < 0.17)
            c = np.where(groove[..., None], c * 0.45, c)
        return c

    def pad(f):
        c = P.rubber(f, '#121212')
        if abs(f.n[2]) > 0.5:
            y = (f.Y - BORE_Y) / IN
            c = c * (1 - 0.35 * ((y * 2.4) % 1 < 0.3))[..., None]
        return c

    def spacer(f):
        return P.plain(f, '#0e0e0e', 0.05)

    def steel(f):
        return P.steel(f, 0.20, tint='#7d8186', spread=0.08)

    def bolt(f):
        return P.steel(f, 0.34, tint='#8b9096', spread=0.08)

    def screw(f):
        return P.steel(f, 0.24, spread=0.08)

    def bead(f):
        return P.brass(f)

    def bore(f):
        return P.plain(f, '#050505', 0.02)

    def head(f):
        return P.brass(f)

    def hull(f):
        c = P.plain(f, '#8c1d1a', 0.10)
        if abs(f.n[2]) < 0.5:
            c = c * (1 - 0.18 * ((f.Z / IN * 9) % 1 < 0.4))[..., None]
        return c

    def crimp(f):
        c = P.plain(f, '#7a1916', 0.10)
        if abs(f.n[2]) > 0.5:
            a, b2 = f.across()
            star = np.abs(np.sin(np.arctan2(a, b2) * 3)) < 0.25
            c = np.where(star[..., None], c * 0.55, c)
        return c

    d = dict(rcvr=rcvr, barrel=barrel, groove=groove, port=port, lifter=lifter, tube=tube, magcap=magcap,
             trigplate=trigplate, stock=stock, forend=forend_paint, ribs=ribs, pad=pad, spacer=spacer,
             steel=steel, bolt=bolt, screw=screw, bead=bead, bore=bore, head=head, hull=hull, crimp=crimp,
             flash=flash_paint)
    d['_after'] = [lambda f, c: P.grime(f, c, 0.3 * (0.4 + wear)),
                   lambda f, c: P.scratches(f, c, 0.25 * (0.3 + wear))]
    return d


def flash(m, z_tip, r=1.0):
    """Muzzle flash: crossed glowing planes and a core just past the muzzle (inches from the receiver front)."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    L = 2.0 * r
    b.box((-0.02, -1.1 * r, z_tip - L), (0.02, 1.1 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.1 * r, -0.02, z_tip - L), (1.1 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.35 * r, -0.35 * r, z_tip - 0.8 * r), (0.35 * r, 0.35 * r, z_tip), 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    t = np.clip(1 - np.sqrt(a ** 2 + b ** 2) / 0.9, 0, 1)
    return P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)
