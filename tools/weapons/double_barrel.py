"""The double-barrel: a side-by-side 12 gauge boxlock field gun (a Stoeger Uplander sort), drawn from the real
thing's measurements: 26" barrels, 42" overall, a 14.5" length of pull, 1.5" drop at the comb and 2.5" at the heel,
double triggers, a tang safety, extractors, a splinter forend and a semi-pistol grip. Measurements are inches from
the bore line (y, both barrels' centres) and the breech face (z, positive back toward the shooter).

It breaks open to load: the top lever is pushed over, and the barrels and forend swing down together about the
hinge pin at the front of the action, so the barrels (with the forend) are the moving part, drawn apart from the
rest so first person can open them; two shells go into the chambers by hand.

It comes in finishes (see FINISHES): a plain field gun, a colour case hardened one with figured walnut, and an
engraved coin-finish one with exhibition walnut.
"""
import numpy as np

import finishes as F
import paint as P
from inch import IN, Inch

BORE_Y = 2.2            # where the bore line sits in the build frame
Z0 = -9.0               # where the breech face sits

BARREL_LEN = 26.0
MUZZLE = -BARREL_LEN
CHAMBER = 2.9           # the thick chamber end of each barrel
RIB_W = 0.15            # half the rib's width
BEAD_Z = MUZZLE + 0.35
BEAD_R = 0.075
HINGE = (-1.02, -2.62)  # the hinge pin (y, z): the barrels swing down about it
BAR_TOP = -0.62         # the action bar's flat, under the barrels' breech
ACT_BOT = -1.42         # the action's belly
ACT_W = 0.98            # half the action's width at the breech
STANDING_TOP = 0.66     # the top of the standing breech, a touch over the rib
TANG_TOP = 0.42
TRIGGERS = (2.85, 3.60)  # front (right barrel) and back (left barrel), in the back half of the guard
FOREND = (-11.0, -2.98)  # the splinter forend's tip and back
STOCK_START = 2.90      # where the walnut meets the action
STOCK_END = 16.30       # the butt plate's back face (42.3" overall)
COMB_Y = 0.63 - 1.5     # the comb, 1.5" under the rib's line
HEEL_Y = 0.63 - 2.5
TOE_Y = HEEL_Y - 4.7
GRIP_Y = -3.72          # the bottom of the semi-pistol grip
BELLY_Z = 8.7           # where the belly starts its straight run down to the toe
BUTT_Z = STOCK_END - 0.32                                # the butt plate's front face
BELLY_Y = TOE_Y + np.tan(np.radians(22.5)) * (BUTT_Z - BELLY_Z)   # the belly runs at 22.5 degrees, so one
                                                                  # turned element draws it as a clean line
OPEN = 34.0             # degrees the barrels drop when it's broken open


def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


HAND = frame_point(0.0, -2.30, 6.30)        # middle of the shooting hand on the grip
SUPPORT = frame_point(0.0, -1.30, -6.80)    # the off hand under the forend
HINGE_POINT = frame_point(0.0, *HINGE)


def barrel_r(z):
    """One barrel's outside radius at z: the chamber, then a taper to the muzzle."""
    if z > -CHAMBER:
        return 0.58
    if z > -6.0:
        return 0.58 - (0.58 - 0.47) * (z + CHAMBER) / (CHAMBER - 6.0)
    return 0.47 - (0.47 - 0.40) * (z + 6.0) / (MUZZLE + 6.0)


def rib_top(z):
    return barrel_r(z) + 0.05


SIGHT = rib_top(-0.5)   # the line of sight: along the rib from its back end to the bead
SIGHT_Z = -0.5
BEAD_TOP = rib_top(BEAD_Z) + BEAD_R * 1.8


# the loose shell first person puts into the right barrel: its head at the chamber's mouth (as it sits once
# home), lying along the bore with the crimp toward the muzzle. LOOSE_GAP: how far apart the two chambers are.
LOOSE_SHELL = (barrel_r(0), 0.0, 0.0)
LOOSE_GAP = 2 * barrel_r(0)


def body(m):
    """Everything that stays put: the action (bar, knuckle, standing breech, fences), top lever and safety,
    trigger plate, guard and triggers, and the stock."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    rc, steel = 'rcvr', 'steel'
    hy, hz = HINGE
    # ---- the action: the knuckle round the hinge pin, the bar the barrels lie on, the standing breech
    b.rod(-ACT_W, ACT_W, (BAR_TOP - ACT_BOT) / 2, rc, axis=0, at=(hy, hz))
    b.box((-ACT_W, ACT_BOT, hz), (ACT_W, BAR_TOP, 0.0), rc)
    for s in (-1, 1):                                                   # the bar's slots for the barrel lumps
        b.box((s * 0.36 - 0.02, BAR_TOP - 0.005, -2.40), (s * 0.36 + 0.02, BAR_TOP + 0.002, -0.30), 'slot')
    b.box((-ACT_W, ACT_BOT, 0.0), (ACT_W, STANDING_TOP, 0.30), rc)
    b.box((-0.30, -0.25, -0.006), (0.30, 0.30, 0.0), 'slot')            # firing pin bushings in the breech face
    for x in (-barrel_r(0), barrel_r(0)):
        b.rod(-0.01, 0.0, 0.05, 'steel', axis=2, at=(x, 0.0))
    # the fences: rounded shoulders behind the breech, then the action body narrowing into the wrist
    b.box((-0.94, ACT_BOT, 0.30), (0.94, STANDING_TOP - 0.08, 0.75), rc)
    b.box((-0.88, ACT_BOT, 0.75), (0.88, 0.40, 1.80), rc)
    b.box((-0.80, ACT_BOT, 1.80), (0.80, 0.34, STOCK_START), rc)
    for s in (-1, 1):                                                   # pins for the hammers and sears
        for y, z in ((-0.70, 1.25), (-1.10, 2.25)):
            b.rod(s * 0.80, s * 0.885, 0.075, 'screw', axis=0, at=(y, z))
    # ---- the top tang, the top lever (pushed right to open) and the tang safety
    b.box((-0.30, 0.30, 0.75), (0.30, TANG_TOP, 4.40), rc)
    b.box((-0.20, TANG_TOP, 0.85), (0.20, TANG_TOP + 0.16, 1.30), steel)     # the lever's boss over its spindle
    b.box((-0.05, TANG_TOP, 1.20), (0.20, TANG_TOP + 0.14, 2.20), steel)     # its arm, back and to the right
    b.box((0.10, TANG_TOP, 2.05), (0.62, TANG_TOP + 0.16, 2.50), steel)      # the thumb piece
    b.box((-0.13, TANG_TOP, 2.80), (0.13, TANG_TOP + 0.10, 3.25), 'safety')  # the safety slide
    # ---- the trigger plate, the guard and its tang under the grip, the two triggers
    b.box((-0.45, ACT_BOT - 0.12, -0.20), (0.45, ACT_BOT, 4.70), rc)
    _guard(b)
    for z, drop in zip(TRIGGERS, (0.70, 0.62)):                             # straight down, the tips curving forward
        knee = ACT_BOT - 0.12 - drop * 0.55
        b.seg((z, ACT_BOT - 0.12), (z, knee), 0.17, 0.20, 'trigger', over=0.03)
        b.seg((z, knee), (z - drop * 0.45 * 0.4142, ACT_BOT - 0.12 - drop), 0.15, 0.20, 'trigger', over=0.03)
    _stock(b)


def _stock_line(z):
    """The stock's top and bottom at z: the top drops from the tang down the wrist to the comb's nose, then
    runs straight down to the heel; the bottom swells into a semi-pistol grip under the wrist, tucks in behind
    the grip cap, then the belly runs straight down to the toe."""
    if z < 8.4:
        u = np.clip((z - 4.4) / 4.0, 0, 1)
        top = 0.34 if z < 4.4 else 0.34 - (0.34 - COMB_Y) * (1 - np.cos(u * np.pi)) / 2
    else:
        top = COMB_Y - (COMB_Y - HEEL_Y) * (z - 8.4) / (STOCK_END - 8.4)
    if z < 4.6:
        bot = ACT_BOT
    elif z < 7.4:
        u = (z - 4.6) / (7.4 - 4.6)
        bot = -1.62 - (-1.62 - GRIP_Y) * np.sin(u * np.pi / 2)
    elif z < BELLY_Z:
        u = (z - 7.4) / (BELLY_Z - 7.4)
        bot = GRIP_Y + (BELLY_Y - GRIP_Y) * (1 - np.cos(u * np.pi)) / 2
    else:
        bot = BELLY_Y + (TOE_Y - BELLY_Y) * (z - BELLY_Z) / (BUTT_Z - BELLY_Z)
    return top, bot


def _stock_half(z):
    if z < 4.0:
        return 0.80 - 0.18 * (z - STOCK_START) / (4.0 - STOCK_START)
    return 0.62 + 0.18 * np.clip((z - 7.8) / 6.0, 0, 1)


def _stock(b):
    """The walnut: slices across the stock, thinner where its lines curve fastest so they step by no more than
    a tenth of an inch, each with a narrower one over it for the rounded edges. The belly is drawn by three
    turned planks underneath, so its line is clean and the slices there stop just above it."""
    st = 'stock'
    plank_end = BUTT_Z - 0.45            # a plank's top corner reaches 0.42" past its bottom line's end
    z = STOCK_START
    while z < BUTT_Z - 1e-6:
        e = 0.02
        slope = max(abs(_stock_line(z + e)[i] - _stock_line(z)[i]) / e for i in (0, 1) if i == 0 or z < BELLY_Z - 0.1)
        z1 = min(z + float(np.clip(0.10 / max(slope, 1e-3), 0.12, 0.40)), BUTT_Z)
        if BUTT_Z - z1 < 0.08:
            z1 = BUTT_Z
        top, bot = _stock_line((z + z1) / 2)
        planked = z >= BELLY_Z - 1e-6 and z1 <= plank_end + 1e-6
        if planked:                      # sit on the plank, never below its line
            bot = max(_stock_line(z)[1], _stock_line(z1)[1]) + 0.03
        half = _stock_half((z + z1) / 2)
        b.box((-half, bot, z), (half, top, z1), st, axis=2, seam=(2,))
        b.box((-half + 0.10, bot if planked else bot - 0.05, z), (half - 0.10, top + 0.04, z1), st, axis=2, seam=(2,))
        z = z1
    # the belly planks: their bottom faces on the belly's line
    t = 1.0
    d = np.array([BUTT_Z - BELLY_Z, TOE_Y - BELLY_Y])
    d /= np.linalg.norm(d)
    n = np.array([-d[1], d[0]])          # up and back, square to the line
    cuts = np.linspace(BELLY_Z, plank_end, 4)
    for z0, z1 in zip(cuts[:-1], cuts[1:]):
        p0 = np.array([z0, _stock_line(z0)[1]]) + n * t / 2
        p1 = np.array([z1, _stock_line(z1)[1]]) + n * t / 2
        half = min(_stock_half(z0), _stock_half(z1)) - 0.02
        b.seg(tuple(p0), tuple(p1), t, 2 * half, st, over=0.0)
    top, bot = _stock_line(BUTT_Z - 0.03)
    b.box((-0.80, bot - 0.02, BUTT_Z), (0.80, top + 0.02, STOCK_END), 'butt')     # the butt plate
    b.rod(-0.82, 0.82, 0.04, 'butt', axis=0, at=(top + 0.02, BUTT_Z))
    for y in np.linspace(top - 0.5, bot + 0.5, 5):                                 # its ribs
        b.box((-0.70, y - 0.03, STOCK_END - 0.005), (0.70, y + 0.03, STOCK_END + 0.012), 'butt')
    gb = _stock_line(7.4)[1]
    b.box((-0.48, gb - 0.11, 6.95), (0.48, gb + 0.05, 7.95), 'cap')                # the grip cap
    # checkering either side of the grip: a panel leaned back along the grip's slant
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.62, s * 0.632))
        c = b.p(0, -1.55, 5.95)
        b.m.box(b.p(x0, -2.08, 4.75), b.p(x1, -1.02, 7.15), 'gcheck', axis=2,
                rot=dict(axis='x', angle=22.5, origin=[float(c[0]), float(c[1]), float(c[2])]))


def _guard(b):
    """The trigger guard: a bow round the triggers in bars (22.5 degree steps), its front foot in the trigger
    plate, its back running on as the guard tang under the grip."""
    zc, yc, az, ay = 2.75, -1.80, 1.80, 0.76
    angles = np.radians([168, 192, 218, 246, 270, 296, 322, 346, 362])
    pts = [(zc + az * np.cos(a), yc + ay * np.sin(a)) for a in angles]
    for p0, p1 in zip(pts[:-1], pts[1:]):
        b.seg(p0, p1, 0.20, 0.30, 'guard', over=0.06)
    b.box((-0.15, ACT_BOT - 0.12 - 0.12, pts[0][0] - 0.10), (0.15, ACT_BOT - 0.12, pts[0][0] + 0.18), 'guard')
    # the tang: along the front of the grip to behind the grip cap
    tang = [(z, _stock_line(z)[1] - 0.02) for z in (4.62, 5.05, 5.55, 6.15, 6.85)]
    tang.insert(0, pts[-1])
    for p0, p1 in zip(tang[:-1], tang[1:]):
        b.seg(p0, p1, 0.10, 0.34, 'guard', over=0.05)


def barrels(m):
    """The moving part: the two barrels with their rib and bead, the lumps under the breech that lock into the
    action, the extractor, and the forend with its iron. It swings down OPEN degrees about HINGE to load."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    bar = 'barrel'
    steps = [0.0, -CHAMBER, -4.4, -6.0, -9.0, -12.5, -16.0, -19.5, -23.0, MUZZLE]
    for z1, z0 in zip(steps[:-1], steps[1:]):
        r = barrel_r((z0 + z1) / 2)
        for s in (-1, 1):
            b.rod(z0, z1, r, bar, axis=2, at=(s * r, 0.0))
        b.box((-RIB_W, 0.0, z0), (RIB_W, rib_top((z0 + z1) / 2), z1), 'rib')               # the top rib
        b.box((-RIB_W, -r + 0.12, z0), (RIB_W, 0.0, z1), 'rib')                            # and the one under
    b.box((-0.045, rib_top(-13) - 0.002, MUZZLE + 0.2), (0.045, rib_top(-13) + 0.004, -0.3), 'line')   # matted line
    rm = barrel_r(MUZZLE)
    for s in (-1, 1):
        b.rod(MUZZLE - 0.02, MUZZLE + 0.04, 0.36, 'bore', axis=2, at=(s * rm, 0.0))
        b.rod(-0.02, 0.004, 0.405, 'chamber', axis=2, at=(s * barrel_r(0), 0.0))
    b.rod(BEAD_Z - BEAD_R, BEAD_Z + BEAD_R, BEAD_R, 'bead', axis=2, at=(0.0, rib_top(BEAD_Z) + BEAD_R * 0.8))
    # the lumps under the breech, hooked round the hinge pin, and the extractor between the chambers
    hy, hz = HINGE
    b.box((-0.34, BAR_TOP + 0.02, hz + 0.25), (0.34, -0.40, -0.25), 'lump')
    b.box((-0.34, hy + 0.30, hz + 0.25), (0.34, BAR_TOP + 0.02, hz + 0.85), 'lump')
    b.box((-0.22, -0.28, -0.02), (0.22, 0.36, 0.006), 'extractor')
    # ---- the forend: walnut under the barrels, its checkering, the iron at the back, the latch at the tip
    f0, f1 = FOREND
    z = f0
    step = 0.80
    while z < f1:
        z1 = min(z + step, f1)
        u = (z - f0) / (f1 - f0)
        r = barrel_r((z + z1) / 2)
        half = 0.84 + 0.12 * u
        bot = -r - 0.48 - 0.08 * u
        b.box((-half, bot + 0.16, z), (half, -0.12, z1), 'forend', axis=2)
        b.box((-half + 0.14, bot, z), (half - 0.14, -0.12, z1), 'forend', axis=2)          # rounded under
        z = z1
    b.box((-0.66, -0.95, f0 - 0.18), (0.66, -0.25, f0), 'forend', axis=2)                   # the rounded tip
    b.rod(f0 - 0.24, f0 - 0.17, 0.08, 'steel', axis=2, at=(0.0, -0.62))                      # the push rod latch
    for s in (-1, 1):
        x0, x1 = sorted((s * (0.84 + 0.12 * 0.62), s * (0.85 + 0.12 * 0.62)))
        b.box((x0, -0.95, -9.6), (x1, -0.30, -4.6), 'fcheck', axis=2)
    b.box((-0.58, -1.12, f1 - 0.65), (0.58, -0.90, f1), 'iron')                               # the forend iron
    b.box((-0.30, -1.02, f1), (0.30, -0.70, f1 + 0.10), 'iron')


def flash(m, z_tip, r=1.0):
    """Muzzle flash from the right barrel (the front trigger's): crossed glowing planes and a core."""
    b = Inch(m, (barrel_r(MUZZLE) * IN, BORE_Y, Z0))
    L = 2.0 * r
    b.box((-0.02, -1.1 * r, z_tip - L), (0.02, 1.1 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.1 * r, -0.02, z_tip - L), (1.1 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.35 * r, -0.35 * r, z_tip - 0.8 * r), (0.35 * r, 0.35 * r, z_tip), 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    t = np.clip(1 - np.sqrt(a ** 2 + b ** 2) / 0.9, 0, 1)
    return P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)


# ---------------------------------------------------------------- finishes

# key: (title, action finish, barrel finish, walnut grade, wood sheen)
FINISHES = {
    'field': ('Field', 'blued', 'blued', 'field', 'satin'),
    'case_hardened': ('Case Hardened', 'case', 'blued', 'figured', 'gloss'),
    'engraved': ('Engraved', 'coin', 'rust_blue', 'exhibition', 'gloss'),
}


def look(finish='field', wear=0.4):
    """The double's materials for one finish: the action (blued, case coloured or engraved coin), blued barrels,
    walnut stock and forend with cut checkering, black butt plate."""
    _title, action, barrel_fin, grade, sheen = FINISHES[finish]

    def metal(kind):
        if kind == 'blued':
            return lambda f: F.blued(f)
        if kind == 'rust_blue':
            return lambda f: F.blued(f, base='#141a29', hi='#2c3954', polish=0.25)
        if kind == 'case':
            return F.case_hardened
        return lambda f: F.coin(f, engraved=True)

    rcvr = metal(action)
    barrel = metal(barrel_fin)

    def rib(f):
        return F.blued(f, polish=0.2) * 0.85

    def line(f):
        c = F.blued(f, base='#101318', hi='#1d222a', polish=0.1)
        hatch = (np.sin(f.Z / IN * 40) > 0.2)
        return np.where(hatch[..., None], c * 0.7, c)

    def wood(f):
        return F.walnut(f, grade, 'z')

    def gcheck(f):
        return F.checkering(f, F.walnut(f, grade, 'z'), pitch=0.15)

    def butt(f):
        c = P.polymer(f, '#141313', 0.08)
        return c

    def cap(f):
        return F.blued(f, polish=0.3) if action != 'coin' else F.coin(f, engraved=False)

    def steel(f):
        return F.blued(f, base='#20252c', hi='#4a5460', polish=0.6)

    def trigger(f):
        return F.gold(f) if action == 'coin' else F.blued(f, polish=0.7)

    def safety(f):
        c = steel(f)
        if f.n[1] > 0.5:
            groove = (np.sin(f.Z / IN * 60) > 0.3)
            c = np.where(groove[..., None], c * 0.6, c)
        return c

    def screw(f):
        return P.steel(f, 0.30, tint='#7d8186', spread=0.08) if action != 'coin' else P.steel(f, 0.7, tint='#cfd2d4')

    def slot(f):
        return P.plain(f, '#0b0b0c', 0.05)

    def bore(f):
        return P.plain(f, '#050505', 0.02)

    def chamber(f):
        a, b2 = f.across()
        r = np.sqrt(a * a + b2 * b2)
        return np.where((r < 0.36 * IN)[..., None], P.col('#060606'), P.col('#2a2d31') * 1.0)

    def bead(f):
        return P.brass(f)

    def lump(f):
        return P.steel(f, 0.34, tint='#80858b', spread=0.06)

    def extractor(f):
        return P.steel(f, 0.46, tint='#9aa0a6', spread=0.06)

    def iron(f):
        return rcvr(f)

    wood_spec = F.WOOD_SPEC[sheen]
    metal_spec = lambda k: F.METAL_SPEC['coin' if k == 'coin' else 'case' if k == 'case' else 'blued']
    d = dict(rcvr=rcvr, barrel=barrel, rib=rib, line=line, stock=wood, forend=wood, gcheck=gcheck, fcheck=gcheck,
             butt=butt, cap=cap, steel=steel, trigger=trigger, safety=safety, screw=screw, slot=slot, bore=bore,
             chamber=chamber, bead=bead, lump=lump, extractor=extractor, iron=iron, guard=rcvr, flash=flash_paint)
    d['_spec'] = {'rcvr': metal_spec(action), 'guard': metal_spec(action), 'iron': metal_spec(action),
                  'barrel': F.METAL_SPEC['blued'], 'rib': F.METAL_SPEC['blued'], 'steel': F.METAL_SPEC['blued'],
                  'safety': F.METAL_SPEC['blued'], 'cap': metal_spec(action),
                  'trigger': F.METAL_SPEC['gold' if action == 'coin' else 'blued'],
                  'stock': wood_spec + (12,), 'forend': wood_spec + (12,),
                  'gcheck': F.WOOD_SPEC['check'] + (30,), 'fcheck': F.WOOD_SPEC['check'] + (30,),
                  'butt': (0.22, 10, None), 'line': (0.30, 10, None)}
    # checkering and engraving get twice the texels, so their cuts read as cuts
    d['_px'] = dict(gcheck=32, fcheck=32, **({'rcvr': 32, 'guard': 32, 'iron': 32} if action == 'coin' else {}))
    d['_after'] = [lambda f, c: P.grime(f, c, 0.25 * (0.4 + f.wear)),
                   lambda f, c: P.scratches(f, c, 0.2 * (0.3 + f.wear))]
    return d
