"""The lever-action carbine: a .30-30 saddle carbine (a Model 94 sort), drawn from the real thing's measurements:
37.7" overall, a 20" round barrel with a full-length tube magazine under it (six rounds, seven with one up), a
flat-sided receiver with the bolt riding in its top and an exposed hammer behind it, a loading gate on the right,
a saddle ring on the left, the finger lever's loop round the trigger, a straight-grip walnut stock with a curved
steel carbine butt plate (13.5" length of pull), a walnut forend held by a barrel band, a band near the muzzle
holding the magazine tube, a semi-buckhorn rear sight and a hooded bead front sight on a ramp. Measurements are
inches from the bore's centre line (y) and the breech face (z, positive back toward the shooter).

It's loaded a round at a time through the gate, and the lever is swung down and back up after each shot: the
lever is drawn apart (turning about LEVER_PIVOT) so first person can work it, and the bolt's top too, which rides
back BOLT_TRAVEL as the lever goes down.

It comes in finishes (see FINISHES): a plain blued one and a colour case hardened one with figured walnut.
"""
import numpy as np

import finishes as F
import paint as P
from inch import IN, Inch

BORE_Y = 2.2            # where the bore sits in the build frame
Z0 = -4.0               # where the breech face sits

MUZZLE = -20.0
RCVR = (-1.70, 5.30)    # the receiver's front (where the barrel screws in) and back (where the tangs start)
RCVR_HALF = 0.56
RCVR_TOP = 0.72
RCVR_BOT = -1.30
BOLT = (0.0, 4.40)      # the bolt riding in the top of the receiver, closed
TUBE_Y = -0.88          # the magazine tube's axis
TUBE_R = 0.34
TUBE_END = -19.30
FOREND = (-10.40, -1.70)
BAND = (-10.40, -9.90)  # the barrel band round the forend's tip
MAG_BAND = (-17.60, -17.15)
REAR_SIGHT = -5.60
FRONT_SIGHT = -19.55
SIGHT = 0.86            # the line from the buckhorn's notch to the bead
TRIGGER_Z = 4.20
LOOP = (3.55, 7.85)     # the lever loop's front and back
LOOP_BOT = -3.45
LEVER_PIVOT = (-1.05, -0.95)   # (y, z): the lever swings down about the pin at the receiver's front
LEVER_OPEN = 50.0       # degrees it swings down
BOLT_TRAVEL = 2.60      # how far the bolt rides back with the lever down: clear of a 2.55" round
GATE = (1.90, -0.69)    # (z, y): where a round's nose goes in at the gate, just inside its back edge
# a loose round lined up at the gate (its base, which the round is built from): first person swings its back out
# and pushes it forward through the gate, tucking it in toward the tube as it goes
LOOSE_ROUND = (0.50, GATE[1], GATE[0] + 2.55)
TANG_END = 8.10
STOCK_END = 17.70       # the butt plate's back (37.7" overall)
COMB_Y = RCVR_TOP - 1.75
HEEL_Y = RCVR_TOP - 2.50
TOE_Y = HEEL_Y - 4.40


def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


HAND = frame_point(0.0, -0.75, 7.10)        # round the wrist, the fingers through the loop
SUPPORT = frame_point(0.0, -1.40, -6.00)    # the off hand under the forend
PIVOT_POINT = frame_point(0.0, *LEVER_PIVOT)


def barrel_r(z):
    """The barrel's radius: the shank's 0.50" into the receiver, tapering from 4" out to 0.31" at the muzzle."""
    if z > -4.0:
        return 0.50
    return 0.50 - (0.50 - 0.31) * (z + 4.0) / (MUZZLE + 4.0)


def stock_line(z):
    """The straight-grip stock's top and bottom behind the receiver: the top level under the upper tang, then down
    over the wrist to the comb's nose and straight on to the heel; the bottom level under the lower tang and the
    lever's latch, then easing into the belly's straight run down to the toe."""
    if z < 8.0:
        top = 0.56
    elif z < 10.6:
        u = (z - 8.0) / 2.6
        top = 0.56 - (0.56 - COMB_Y) * (1 - np.cos(u * np.pi)) / 2
    else:
        top = COMB_Y - (COMB_Y - HEEL_Y) * (z - 10.6) / (STOCK_END - 10.6)
    d = z - 7.6
    L = 2.6
    s = (-(TOE_Y - -1.48)) / (STOCK_END - 7.6 - L / 2)
    if d <= 0:
        bot = -1.48
    elif d < L:
        bot = -1.48 - s * d * d / (2 * L)
    else:
        bot = -1.48 - s * (d - L / 2)
    return top, bot


def _half(z):
    if z < 9.0:
        return 0.56 - 0.02 * np.clip((z - RCVR[1]) / 2.0, 0, 1)
    return 0.54 + 0.26 * np.clip((z - 9.0) / 7.0, 0, 1)


def body(m):
    """Everything that stays put: barrel, magazine tube, bands and sights; receiver, bolt, hammer, gate, saddle ring,
    tangs and trigger; the forend and the stock with its butt plate."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    rc, bar = 'rcvr', 'barrel'
    # ---- the barrel in tapered steps, its crown recessed
    steps = [RCVR[0] + 0.1, -4.0, -7.0, -10.0, -13.0, -16.0, -18.5, MUZZLE]
    for z1, z0 in zip(steps[:-1], steps[1:]):
        b.rod(z0, z1, barrel_r((z0 + z1) / 2), bar, axis=2)
    b.rod(MUZZLE - 0.01, MUZZLE + 0.05, 0.155, 'bore', axis=2)
    b.rod(MUZZLE - 0.005, MUZZLE + 0.012, 0.25, 'crown', axis=2)
    # ---- the magazine tube under it, its cap and screw, and the bands
    b.rod(TUBE_END, RCVR[0] + 0.1, TUBE_R, 'tube', axis=2, at=(0.0, TUBE_Y))
    b.rod(TUBE_END - 0.22, TUBE_END, TUBE_R + 0.02, 'tube', axis=2, at=(0.0, TUBE_Y))
    b.rod(TUBE_END - 0.24, TUBE_END - 0.20, 0.08, 'screw', axis=2, at=(0.0, TUBE_Y))
    z0, z1 = MAG_BAND
    r = barrel_r((z0 + z1) / 2)
    b.box((-0.40, TUBE_Y - TUBE_R - 0.06, z0), (0.40, r + 0.06, z1), 'band')
    b.rod(-0.42, 0.42, 0.05, 'screw', axis=0, at=(-0.45, (z0 + z1) / 2))
    # ---- the sights: the semi-buckhorn in its dovetail, the ramp with its hooded bead
    rz, rr = REAR_SIGHT, barrel_r(REAR_SIGHT)
    b.box((-0.30, rr - 0.08, rz - 0.30), (0.30, rr + 0.10, rz + 0.30), 'sight')
    b.box((-0.52, rr + 0.10, rz - 0.04), (0.52, SIGHT - 0.06, rz + 0.04), 'sight')
    for s in (-1, 1):                                                   # the buckhorn's ears either side of the notch
        x0, x1 = sorted((s * 0.07, s * 0.52))
        b.box((x0, SIGHT - 0.06, rz - 0.04), (x1, SIGHT + 0.06 + 0.06 * (abs(x1) > 0.4), rz + 0.04), 'sight')
    b.box((-0.18, rr + 0.10, rz + 0.04), (0.18, rr + 0.28, rz + 0.36), 'sight')                # the elevator
    fz, fr = FRONT_SIGHT, barrel_r(FRONT_SIGHT)
    b.box((-0.16, fr - 0.06, fz - 0.42), (0.16, fr + 0.22, fz + 0.30), 'sight')                # the ramp
    b.box((-0.035, fr + 0.22, fz - 0.10), (0.035, SIGHT - 0.04, fz + 0.06), 'sight')           # the blade
    b.rod(fz - 0.05, fz + 0.03, 0.045, 'bead', axis=2, at=(0.0, SIGHT - 0.02))
    for s in (-1, 1):                                                   # the hood
        x0, x1 = sorted((s * 0.20, s * 0.24))
        b.box((x0, fr + 0.10, fz - 0.30), (x1, SIGHT + 0.14, fz + 0.16), 'sight')
    b.box((-0.24, SIGHT + 0.14, fz - 0.30), (0.24, SIGHT + 0.18, fz + 0.16), 'sight')
    # ---- the receiver: flat sides, its top rounding down onto the barrel at the front, the bolt in its top
    f, k = RCVR
    b.box((-RCVR_HALF, RCVR_BOT, f + 0.55), (RCVR_HALF, RCVR_TOP - 0.18, k), rc)
    b.box((-RCVR_HALF, RCVR_BOT, f), (RCVR_HALF, 0.40, f + 0.55), rc)
    b.box((-RCVR_HALF + 0.10, RCVR_BOT + 0.10, f - 0.10), (RCVR_HALF - 0.10, 0.48, f + 0.02), rc)
    b.seg((f + 0.25, 0.34), (f + 0.85, RCVR_TOP - 0.06), 0.20, 2 * RCVR_HALF, rc, over=0.05)
    for s in (-1, 1):                                                   # the top's side rails, the bolt between
        x0, x1 = sorted((s * 0.28, s * RCVR_HALF))
        b.box((x0, RCVR_TOP - 0.18, f + 0.75), (x1, RCVR_TOP, k), rc)
    b.box((-0.28, RCVR_TOP - 0.30, BOLT[0] - 0.4), (0.28, RCVR_TOP - 0.10, BOLT[1]), 'bolt')   # its body (see bolt())
    b.box((-0.28, RCVR_TOP - 0.18, f + 0.75), (0.28, RCVR_TOP - 0.10, BOLT[0] - 0.4), 'slot')   # the port ahead of it
    # the bottom: round the tube's mouth at the front, the lever's slot along it
    b.rod(f - 0.06, f + 0.6, TUBE_R + 0.06, rc, axis=2, at=(0.0, TUBE_Y))
    # the loading gate on the right, a little proud, and its screw
    b.box((RCVR_HALF, -1.07, 0.20), (RCVR_HALF + 0.006, -0.31, 2.10), 'slot')
    b.box((RCVR_HALF, -1.02, 0.25), (RCVR_HALF + 0.014, -0.36, 2.05), 'gate')
    b.box((RCVR_HALF, -0.86, 0.25), (RCVR_HALF + 0.018, -0.52, 0.42), 'slot')         # the thumb notch at its front
    b.rod(RCVR_HALF, RCVR_HALF + 0.022, 0.06, 'screw', axis=0, at=(-0.69, 1.80))
    for s in (-1, 1):                                                   # pins and screws through the sides
        for y, z in ((-0.95, 0.0), (0.05, 3.10), (-0.45, 4.70), (-0.95, 2.6)):
            if s > 0 and 0.25 < z < 2.05 and y < -0.3:
                continue
            b.rod(s * RCVR_HALF, s * (RCVR_HALF + 0.015), 0.07, 'screw', axis=0, at=(y, z))
    # the saddle ring on the left: a stud near the back, the ring hanging from it
    b.rod(-RCVR_HALF - 0.08, -RCVR_HALF, 0.11, 'steel', axis=0, at=(-0.20, 4.55))
    ring = [(4.55 + 0.42 * np.sin(np.radians(a)), -0.62 + 0.42 * np.cos(np.radians(a))) for a in range(0, 361, 45)]
    for p0, p1 in zip(ring[:-1], ring[1:]):
        b.seg(p0, p1, 0.07, 0.07, 'ring', x=-RCVR_HALF - 0.08, over=0.02)
    # ---- the hammer, cocked: its body down in the receiver's back, the checkered spur laid back over the tang and
    # kept under the line of sight (the eye looks over it to the buckhorn's notch)
    b.box((-0.16, RCVR_TOP - 0.40, k - 0.65), (0.16, RCVR_TOP - 0.04, k - 0.05), 'hammer')
    b.seg((k - 0.25, RCVR_TOP - 0.24), (k + 0.25, RCVR_TOP - 0.04), 0.16, 0.30, 'hammer', over=0.05)
    b.seg((k + 0.22, RCVR_TOP - 0.04), (k + 0.58, RCVR_TOP - 0.04), 0.12, 0.36, 'spur', over=0.06)
    # ---- the tangs over and under the wrist, the trigger in the front of the loop
    b.box((-0.24, 0.50, k - 0.05), (0.24, 0.62, TANG_END), rc)
    b.box((-0.26, -1.48, k - 0.05), (0.26, -1.34, TANG_END + 0.4), rc)
    b.box((-RCVR_HALF, -1.48, f + 0.4), (RCVR_HALF, RCVR_BOT, k), rc)
    b.rod(0.56, 0.64, 0.06, 'screw', axis=1, at=(0.0, k + 1.5))
    knee = -1.48 - 0.50
    b.seg((TRIGGER_Z, -1.48), (TRIGGER_Z, knee), 0.17, 0.20, 'trigger', over=0.03)
    b.seg((TRIGGER_Z, knee), (TRIGGER_Z - 0.16, -1.48 - 0.86), 0.15, 0.20, 'trigger', over=0.03)
    _forend(b)
    _stock(b)


def _forend(b):
    """The walnut forend round the barrel's bottom half and the tube, and the band holding its tip."""
    st = 'stock'
    f0, f1 = FOREND
    z = f0
    while z < f1 - 1e-6:
        z1 = min(z + 0.85, f1)
        u = (z - f0) / (f1 - f0)
        half = 0.58 + 0.04 * u
        bot = TUBE_Y - TUBE_R - 0.18
        b.box((-half, bot + 0.12, z), (half, 0.02, z1), st, axis=2, seam=(2,))
        b.box((-half + 0.12, bot, z), (half - 0.12, 0.02, z1), st, axis=2, seam=(2,))
        z = z1
    z0, z1 = BAND
    r = barrel_r((z0 + z1) / 2)
    b.box((-0.66, TUBE_Y - TUBE_R - 0.26, z0), (0.66, r + 0.06, z1), 'band')
    b.rod(-0.68, 0.68, 0.05, 'screw', axis=0, at=(TUBE_Y - TUBE_R - 0.05, (z0 + z1) / 2))


def _stock(b):
    """The wrist and butt in slices, thinner where the lines curve fastest, each with a narrower one over it for the
    rounded edges; the curved carbine butt plate."""
    st = 'stock'
    z = RCVR[1] - 0.05
    end = STOCK_END - 0.30
    while z < end - 1e-6:
        e = 0.02
        slope = max(abs(stock_line(z + e)[i] - stock_line(z)[i]) / e for i in (0, 1))
        z1 = min(z + float(np.clip(0.06 / max(slope, 1e-3), 0.10, 0.45)), end)
        if end - z1 < 0.08:
            z1 = end
        top, bot = stock_line((z + z1) / 2)
        half = _half((z + z1) / 2)
        b.box((-half, bot + 0.05, z), (half, top, z1), st, axis=2, seam=(2,))
        b.box((-half + 0.10, bot, z), (half - 0.10, top + 0.04, z1), st, axis=2, seam=(2,))
        z = z1
    # the butt plate: curved, so its middle sits further forward than its heel and toe
    top, bot = stock_line(end - 0.05)
    h = top - bot
    for (a, c), dz in (((0.00, 0.22), 0.00), ((0.22, 0.78), -0.10), ((0.78, 1.00), 0.00)):
        y1, y0 = top + 0.03 - a * (h + 0.06), top + 0.03 - c * (h + 0.06)
        b.box((-0.80, y0, end + dz), (0.80, y1, STOCK_END + dz), 'buttplate')
    b.box((-0.66, top - 0.02, end - 0.6), (0.66, top + 0.05, end + 0.05), 'buttplate')     # its heel tang
    b.rod(top - 0.03, top + 0.07, 0.08, 'screw', axis=1, at=(0.0, end - 0.32))


def lever(m):
    """The finger lever: the bar along the receiver's bottom from its pivot, and the loop round the trigger and the
    fingers, its back latching against the lower tang. It turns down LEVER_OPEN degrees about LEVER_PIVOT."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    py, pz = LEVER_PIVOT
    b.rod(-0.30, 0.30, 0.16, 'lever', axis=0, at=(py, pz))
    b.box((-0.22, -1.62, pz - 0.30), (0.22, -1.48, LOOP[0] + 0.15), 'lever')
    b.box((-0.18, -1.48, pz), (0.18, py + 0.10, pz + 0.45), 'lever')
    # the loop hangs from the receiver's underside: down in front of the trigger, round, and up to the lower tang
    zc, yc = (LOOP[0] + LOOP[1]) / 2, -1.55
    az, ay = (LOOP[1] - LOOP[0]) / 2, yc - LOOP_BOT
    angles = np.radians(np.arange(180, 361, 22.5))
    pts = [(zc + az * np.cos(a), yc + ay * np.sin(a)) for a in angles]
    for p0, p1 in zip(pts[:-1], pts[1:]):
        b.seg(p0, p1, 0.22, 0.34, 'lever', over=0.06)
    b.box((-0.17, -1.62, LOOP[1] - 0.25), (0.17, -1.48, LOOP[1] + 0.35), 'lever')        # its latch at the back


def bolt(m):
    """The bolt's top, seen between the receiver's rails, and the firing pin out of its back: what rides back
    BOLT_TRAVEL over the hammer and the upper tang as the lever goes down."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    b.box((-0.28, RCVR_TOP - 0.10, BOLT[0] - 0.4), (0.28, RCVR_TOP - 0.03, BOLT[1]), 'bolt')
    b.box((-0.10, RCVR_TOP - 0.035, BOLT[0] + 0.6), (0.10, RCVR_TOP - 0.025, BOLT[1] - 0.6), 'slot')
    b.rod(BOLT[1], BOLT[1] + 0.12, 0.07, 'steel', axis=2, at=(0.0, RCVR_TOP - 0.06))


def round_look():
    """A loose .30-30 round's paint."""
    lk = look()
    d = {k: lk[k] for k in ('case', 'jacket', 'lead')}
    d['_spec'] = {'case': F.METAL_SPEC['gold'], 'jacket': F.METAL_SPEC['gold']}
    return d


def round_3030(m):
    """A .30-30 round, base at the origin, nose toward -z: a rimmed bottlenecked brass case and a flat-nosed soft
    point (it has to be, nose to primer in a tube)."""
    b = Inch(m)
    b.rod(-0.06, 0.0, 0.253, 'case', axis=2)
    b.rod(-1.42, -0.06, 0.211, 'case', axis=2)
    b.rod(-1.62, -1.42, 0.19, 'case', axis=2)
    b.rod(-2.04, -1.62, 0.166, 'case', axis=2)
    b.rod(-2.38, -2.04, 0.154, 'jacket', axis=2)
    b.rod(-2.55, -2.38, 0.11, 'lead', axis=2)


# ---------------------------------------------------------------- finishes

# key: (title, receiver finish, walnut grade, wood sheen)
FINISHES = {
    'field': ('Field', 'blued', 'field', 'satin'),
    'case_hardened': ('Case Hardened', 'case', 'figured', 'gloss'),
}


def look(finish='field', wear=0.4):
    _title, action, grade, sheen = FINISHES[finish]
    rcvr = F.case_hardened if action == 'case' else (lambda f: F.blued(f))

    def barrel(f):
        return F.blued(f)

    def tube(f):
        return F.blued(f, polish=0.4)

    def band(f):
        return F.blued(f, polish=0.3)

    def bolt(f):
        return F.blued(f, base='#1d222b', hi='#55606e', polish=0.75)

    def hammer(f):
        return F.case_hardened(f) if action == 'case' else F.blued(f, polish=0.6)

    def spur(f):
        c = hammer(f)
        if f.n[1] > 0.5:
            groove = (np.sin(f.Z / IN * 60) > 0.3)
            c = np.where(groove[..., None], c * 0.6, c)
        return c

    def lever(f):
        return rcvr(f)

    def gate(f):
        return rcvr(f) * 0.92

    def sight(f):
        return F.blued(f, base='#14171c', hi='#2d343e', polish=0.2)

    def bead(f):
        return P.brass(f)

    def wood(f):
        return F.walnut(f, grade, 'z')

    def buttplate(f):
        return F.blued(f, polish=0.25)

    def steel(f):
        return F.blued(f, base='#20252c', hi='#4a5460', polish=0.6)

    def ring(f):
        return F.blued(f, polish=0.7)

    def trigger(f):
        return F.blued(f, polish=0.7)

    def screw(f):
        return P.steel(f, 0.30, tint='#7d8186', spread=0.08)

    def slot(f):
        return P.plain(f, '#0b0b0c', 0.05)

    def bore(f):
        return P.plain(f, '#050505', 0.02)

    def crown(f):
        return F.blued(f, polish=0.8)

    def case(f):
        return P.brass(f)

    def jacket(f):
        return P.mix(P.brass(f), P.col('#b5673e'), 0.55)

    def lead(f):
        return P.plain(f, '#6d6f73', 0.10)

    metal = F.METAL_SPEC['case' if action == 'case' else 'blued']
    d = dict(rcvr=rcvr, barrel=barrel, tube=tube, band=band, bolt=bolt, hammer=hammer, spur=spur, lever=lever,
             gate=gate, sight=sight, bead=bead, stock=wood, buttplate=buttplate, steel=steel, ring=ring,
             trigger=trigger, screw=screw, slot=slot, bore=bore, crown=crown, case=case, jacket=jacket, lead=lead,
             flash=flash_paint)
    d['_spec'] = {'rcvr': metal, 'lever': metal, 'hammer': metal, 'spur': metal,
                  'barrel': F.METAL_SPEC['blued'], 'tube': F.METAL_SPEC['blued'], 'band': F.METAL_SPEC['blued'],
                  'bolt': F.METAL_SPEC['blued'], 'gate': metal, 'buttplate': F.METAL_SPEC['blued'],
                  'stock': F.WOOD_SPEC[sheen] + (12,)}
    d['_after'] = [lambda f, c: P.grime(f, c, 0.25 * (0.4 + f.wear)),
                   lambda f, c: P.scratches(f, c, 0.2 * (0.3 + f.wear))]
    return d


def flash(m, z_tip, r=1.0):
    b = Inch(m, (0.0, BORE_Y, Z0))
    L = 2.0 * r
    b.box((-0.02, -1.1 * r, z_tip - L), (0.02, 1.1 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.1 * r, -0.02, z_tip - L), (1.1 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.35 * r, -0.35 * r, z_tip - 0.8 * r), (0.35 * r, 0.35 * r, z_tip), 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    t = np.clip(1 - np.sqrt(a ** 2 + b ** 2) / 0.9, 0, 1)
    return P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)
