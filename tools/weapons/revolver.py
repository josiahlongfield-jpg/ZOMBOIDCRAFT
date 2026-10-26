"""The revolver: a Smith & Wesson Model 10-style .38 Special service revolver (a K-frame with a 4" heavy barrel,
fixed sights, a square butt with its corners rounded and wood service stocks), drawn from the real thing's
measurements: about 9" long and 5.3" tall, a 1.44" six-shot cylinder 1.56" long, a 5.5" sight radius. Measurements are inches from the bore line (y) and
the cylinder's front face (z, positive back toward the shooter).

It loads with the cylinder swung out: the latch on the left is pushed forward, and the cylinder swings out to the
left on its crane, so the cylinder, crane and ejector rod are the moving part, drawn apart from the rest so first
person can swing them out and turn the cylinder a chamber for each shot. The cylinder turns counter-clockwise seen
from behind (the next chamber comes up from the right). Rounds are pushed into the chambers one at a time.

It comes in finishes (see FINISHES): blued with checkered walnut, nickel with smooth rosewood, and a royal blue one
engraved with gold-inlaid scroll and exhibition walnut.
"""
import numpy as np

import finishes as F
import model as M
import paint as P
from inch import IN, Inch

BORE_Y = 2.2            # where the bore line sits in the build frame
Z0 = -1.0               # where the cylinder's front face sits

BARREL_LEN = 4.0
MUZZLE = -BARREL_LEN
BARREL_R = 0.33         # the heavy barrel, 0.66" across
CYL_FRONT = 0.008       # the barrel-cylinder gap
CYL_LEN = 1.56
CYL_BACK = CYL_FRONT + CYL_LEN
CYL_R = 0.72
CYL_Y = -0.475          # the cylinder's axis: the top chamber's centre is on the bore
BOLT_R = -CYL_Y         # the chambers' circle
CHAMBER_R = 0.19
ROD_R = 0.11            # the ejector rod, on the cylinder's axis
ROD_HEAD = (-2.15, -1.92)   # its knurled head
LUG = (-2.42, -2.13)    # the lug under the barrel the rod's tip locks into
FRAME_FRONT = -0.62
FRAME_W = 0.45          # half the frame's width
STRAP_TOP = 0.60        # the top strap's top (the sight groove's edges)
GROOVE = 0.06           # the sight groove's depth
STRAP_BOT = CYL_Y + CYL_R + 0.015
FRAME_BOT = -1.42       # the frame's belly under the cylinder
SHIELD_BACK = CYL_BACK + 0.32   # the recoil shield behind the cylinder
FRAME_BACK = 2.95       # the side plate's back edge; the back strap runs on up behind it to the hammer
SIGHT = STRAP_TOP - GROOVE      # the sight groove's floor
NOTCH_TOP = STRAP_TOP + 0.035   # the line of sight: the rear notch's top, level with the front blade's
NOTCH_W = 0.085                 # half the notch's width: a sliver of light shows each side of the blade
REAR_SIGHT = 1.98
FRONT_SIGHT = (-3.85, -3.30)
CRANE = (-0.56, -0.012)  # the crane arm, in front of the cylinder
# the crane's pivot (x, y): the cylinder swings out OPEN degrees about it, out to the left and a little down
PIVOT = (-0.42, -1.12)
OPEN = 100.0
# the grip, leaned back 22.5 degrees: its centre line's top and length (the wood's), and its widths
GRIP_TOP = (2.95, -0.97)
GRIP_LEN = 3.45         # down to where the butt's corners start rounding: 5.3" tall overall, butt to hammer
GRIP_DIR = np.array([np.sin(np.radians(22.5)), -np.cos(np.radians(22.5))])     # (z, y), down the grip
GRIP_W = 1.45           # the grip frame front strap to back strap
WOOD_W = 1.35           # the stocks leave the straps bare
WOOD_X = 0.60           # half the grip's thickness over the stocks
BUTT_CUT = 0.36         # the square butt's corners, rounded off
STOCK_TOP, STOCK_CUT = -0.10, 0.40      # the stocks' top, up over the frame, and how much its corners are rounded
# the trigger guard's bow (z, y): down from the frame's belly, flat under the trigger, then up into the front strap
GUARD = ((0.40, -1.36), (0.60, -1.86), (0.95, -2.21), (1.85, -2.21), (2.25, -2.04), (2.55, -1.74))


def frame_point(x, y, z):
    """A measured point (inches) in the build frame."""
    return np.array([IN * x, BORE_Y + IN * y, Z0 + IN * z])


def _grip(t):
    """A point t inches down the grip's centre line (z, y)."""
    return tuple(np.array(GRIP_TOP) + GRIP_DIR * t)


HAND = frame_point(0.0, *reversed(_grip(1.15)))         # middle of the shooting hand on the grip
CYL_AXIS = frame_point(0.0, CYL_Y, CYL_FRONT)
AXIS_XY = CYL_AXIS[:2]
PIVOT_POINT = frame_point(PIVOT[0], PIVOT[1], CYL_FRONT)
# the loose round first person loads: in the top chamber, its rim on the cylinder's back face
LOOSE_ROUND = (0.0, 0.0, CYL_BACK)


def _round(b, x, y, r, z0, z1, mat, **kw):
    """A rounder bar along z than Inch.rod: four boxes in steps, for the big round parts."""
    for ca, cb in ((0.98, 0.195), (0.83, 0.556), (0.556, 0.83), (0.195, 0.98)):
        b.box((x - r * ca, y - r * cb, z0), (x + r * ca, y + r * cb, z1), mat, axis=2, round=IN * r, **kw)


def body(m):
    """Everything that stays put: the frame (top strap, recoil shield, the frame window round the cylinder), the
    barrel with its sight and lug, the hammer, trigger and guard, the latch, and the grip with its stocks."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    fr = 'frame'
    # ---- the barrel, its rib, the front sight, the lug under it for the ejector rod's tip
    b.rod(MUZZLE, FRAME_FRONT, BARREL_R, 'barrel', axis=2)
    b.box((-0.09, BARREL_R - 0.04, MUZZLE + 0.06), (0.09, BARREL_R + 0.03, FRAME_FRONT), 'barrel', axis=2)
    b.rod(MUZZLE - 0.012, MUZZLE + 0.03, 0.18, 'bore', axis=2)
    f0, f1 = FRONT_SIGHT
    b.box((-0.055, BARREL_R, f0), (0.055, SIGHT - 0.08, f1), 'sight')
    b.box((-0.055, SIGHT - 0.08, f0 + 0.10), (0.055, NOTCH_TOP, f1 - 0.10), 'sight')
    b.box((-0.13, CYL_Y - 0.17, LUG[0]), (0.13, -BARREL_R + 0.03, LUG[1]), fr)
    # ---- the frame: its nose round the barrel's shank, the top strap with the sight groove down it, the belly
    b.box((-0.40, CYL_Y + ROD_R + 0.05, FRAME_FRONT), (0.40, SIGHT, 0.0), fr)
    b.box((-0.36, STRAP_BOT, 0.0), (0.36, SIGHT, REAR_SIGHT + 0.06), fr)
    for s in (-1, 1):                                       # the groove's edges, and the notch's sides at the back
        b.box((s * NOTCH_W, SIGHT, FRAME_FRONT + 0.30), (s * 0.36, STRAP_TOP, REAR_SIGHT - 0.20), fr)
        b.box((s * NOTCH_W, SIGHT, REAR_SIGHT - 0.20), (s * 0.30, NOTCH_TOP, REAR_SIGHT + 0.06), fr)
    b.box((-FRAME_W, FRAME_BOT, FRAME_FRONT), (FRAME_W, CYL_Y - CYL_R - 0.015, CYL_BACK), fr)
    # ---- the recoil shield behind the cylinder, then the frame body the hammer and lockwork sit in
    _round(b, 0.0, CYL_Y, 0.66, CYL_BACK + 0.004, SHIELD_BACK, fr)
    b.box((-FRAME_W, FRAME_BOT, CYL_BACK + 0.004), (FRAME_W, STRAP_BOT, SHIELD_BACK), fr)
    b.box((-0.42, FRAME_BOT, SHIELD_BACK), (0.42, 0.30, FRAME_BACK), fr)
    # the side plate's screws (right) and the crane's lock screw at the front
    for x, y, z in ((0.42, -0.20, 2.62), (0.42, -1.05, 2.40), (FRAME_W, -1.30, -0.30)):
        b.rod(x, x + 0.035, 0.07, 'screw', axis=0, at=(y, z))
    # ---- the cylinder latch, left behind the cylinder: pushed forward to swing it out
    b.box((-0.52, -0.40, CYL_BACK + 0.36), (-0.42, -0.12, CYL_BACK + 0.90), 'latch')
    # ---- the hammer, its spur back over the frame, resting on the back strap's top; the trigger and its guard
    # (all of it under the line of sight: the spur's top is 0.05" below the rear notch's)
    b.box((-0.13, 0.0, 2.06), (0.13, 0.58, 2.40), 'hammer')
    b.seg((2.30, 0.49), (2.84, 0.49), 0.18, 0.34, 'hammer', over=0.0)
    b.seg((2.84, 0.49), (3.20, 0.34), 0.18, 0.34, 'hammer', over=0.04)
    b.box((-0.42, 0.25, 2.40), (0.42, 0.40, 3.0), fr)                     # the frame round the hammer, under its spur
    b.seg((1.65, FRAME_BOT), (1.65, -1.78), 0.17, 0.34, 'trigger', over=0.04)    # back in the guard,
    b.seg((1.65, -1.78), (1.53, -2.04), 0.16, 0.34, 'trigger', over=0.04)        # its tip curving forward
    for p0, p1 in zip(GUARD[:-1], GUARD[1:]):
        b.seg(p0, p1, 0.15, 0.30, fr, over=0.05)
    _grip_frame(b)


GRIP_BACK = np.array([GRIP_DIR[1] * -1, GRIP_DIR[0]])      # (z, y) across the grip, toward the back strap


def _corner(u, t):
    """A point u inches across the grip (toward the back strap) and t down it."""
    return tuple(np.array(_grip(t)) + GRIP_BACK * u)


def _end(b, t, half, cut, thick, mat, up=False, **kw):
    """The grip's end, t inches down its centre line (up=True: its top end): a flat end with its corners cut off
    at 45 degrees, `cut` inches each way, out from a grip `half` inches either side of the centre line. The middle
    is a bar; each corner is a square turned 45 degrees with its outer side on the cut, its inner half buried."""
    sgn = -1 if up else 1
    b.seg(_grip(t - sgn * 0.02), _grip(t + sgn * cut), 2 * (half - cut), thick, mat, over=0.0, **kw)
    side = cut * np.sqrt(2)
    for s in (-1, 1):
        a, c = np.array(_corner(s * half, t)), np.array(_corner(s * (half - cut), t + sgn * cut))
        mid, e = np.array(_corner(s * (half - cut), t)), (c - a) / np.linalg.norm(c - a)
        b.seg(tuple(mid - e * side / 2), tuple(mid + e * side / 2), side, thick, mat, over=0.0, **kw)


def _grip_frame(b):
    """The grip: the frame's steel straps (the back strap carries on up behind the side plate to meet the hammer's
    spur) down to the butt, its corners rounded off, and the wood stocks either side, rounded at the top and
    thicker down the middle, with a checkered panel and a medallion each. The steel shows round the stocks' edges."""
    top = _grip(-1.10)
    b.seg(top, _grip(0.30), GRIP_W, 0.84, 'frame', over=0.0)        # the frame's full width down to the stocks
    b.seg(top, _grip(GRIP_LEN), GRIP_W, 0.60, 'frame', over=0.0)
    _end(b, GRIP_LEN, GRIP_W / 2, BUTT_CUT, 0.60, 'frame')
    # the stocks: 0.05" in from the straps and the butt, so the cut corners sit 0.05" in from the steel's too
    inset, half = (GRIP_W - WOOD_W) / 2, WOOD_W / 2
    shift = half - (GRIP_W / 2 - inset * np.sqrt(2))
    t0, t1 = STOCK_TOP, GRIP_LEN - shift
    thin = 2 * WOOD_X - 0.14
    b.seg(_grip(t0), _grip(t1), WOOD_W, thin, 'stock', over=0.0)
    _end(b, t0, half, STOCK_CUT, thin, 'stock', up=True)
    _end(b, t1, half, BUTT_CUT + shift - inset, thin, 'stock')
    core = half - 0.18                                                # the core stays inside the cut corners
    b.seg(_grip(t0 - (STOCK_CUT - 0.18)), _grip(t1 + (BUTT_CUT + shift - inset) - 0.18), 2 * core, 2 * WOOD_X,
          'stock', over=0.0)
    for s in (-1, 1):
        b.seg(_grip(0.85), _grip(GRIP_LEN - 0.05), WOOD_W - 0.42, 0.012, 'gcheck', x=s * (WOOD_X + 0.006), over=0.0)
        mz, my = _grip(STOCK_TOP + 0.32)
        x0, x1 = sorted((s * WOOD_X, s * (WOOD_X + 0.016)))
        b.rod(x0, x1, 0.13, 'medal', axis=0, at=(my, mz))


def cylinder(m):
    """The moving part: the cylinder with its six chambers, the crane it swings out on, the ejector rod down its
    axis with the knurled head. It swings out OPEN degrees about PIVOT to load, and turns a chamber each shot."""
    b = Inch(m, (0.0, BORE_Y, Z0))
    b.tube(CYL_FRONT, CYL_BACK, CYL_R - 0.20, CYL_R, 'cyl', at=(0.0, CYL_Y))       # sixteen staves round
    _round(b, 0.0, CYL_Y, CYL_R - 0.17, CYL_FRONT, CYL_BACK, 'cyl')                 # and a core for its faces
    # the crane: its arm in front of the cylinder, round the rod and down into the frame's belly
    b.box((-FRAME_W + 0.02, CYL_Y - CYL_R + 0.02, CRANE[0]), (FRAME_W - 0.02, CYL_Y + ROD_R + 0.04, CRANE[1]), 'crane')
    b.rod(ROD_HEAD[1], CRANE[0], ROD_R, 'rod', axis=2, at=(0.0, CYL_Y))
    b.rod(ROD_HEAD[0], ROD_HEAD[1], 0.15, 'knurl', axis=2, at=(0.0, CYL_Y))
    b.rod(LUG[0] + 0.10, ROD_HEAD[0], ROD_R * 0.8, 'rod', axis=2, at=(0.0, CYL_Y))


def round38(m):
    """A .38 Special round as it sits home: its rim on the cylinder's back face (z 0), the case forward of it, the
    lead round nose out ahead."""
    b = Inch(m)
    b.rod(0.0, 0.058, 0.218, 'brass', axis=2)
    b.rod(-1.097, 0.0, 0.190, 'brass', axis=2)
    b.rod(-1.40, -1.097, 0.178, 'lead', axis=2)
    b.rod(-1.53, -1.40, 0.12, 'lead', axis=2)
    b.rod(0.058, 0.064, 0.085, 'primer', axis=2)


def case38(m):
    """A fired .38 case: the rim and the case, its mouth open and sooted."""
    b = Inch(m)
    b.rod(0.0, 0.058, 0.218, 'brass', axis=2)
    b.rod(-1.097, 0.0, 0.190, 'brass', axis=2)
    b.rod(-1.105, -1.097, 0.15, 'soot', axis=2)
    b.rod(0.058, 0.064, 0.085, 'primer', axis=2)


def round_look():
    def brass(f):
        c = P.brass(f)
        if f.n[2] > 0.5:                                    # the head stamp's ring
            a, b2 = f.across()
            r = np.sqrt(a * a + b2 * b2)
            c = np.where((np.abs(r - 0.15 * IN) < 0.02)[..., None], c * 0.7, c)
        return c

    def lead(f):
        return P.steel(f, 0.42, tint='#8a8c90', spread=0.06) * np.array([0.92, 0.92, 0.96])

    def primer(f):
        return P.steel(f, 0.6, tint='#b9a77a', spread=0.06)

    def soot(f):
        return P.plain(f, '#1d1a16', 0.1)

    return dict(brass=brass, lead=lead, primer=primer, soot=soot,
                _spec={'brass': F.METAL_SPEC['gold'], 'primer': F.METAL_SPEC['nickel'], 'lead': (0.30, 230, None)})


def flash(m, z_tip, r=1.0):
    """Muzzle flash: crossed glowing planes and a core. (The side flash spat out of the barrel-cylinder gap is
    GAP_FLASH, drawn with it by the kit.)"""
    b = Inch(m, (0.0, BORE_Y, Z0))
    L = 1.8 * r
    b.box((-0.02, -1.0 * r, z_tip - L), (0.02, 1.0 * r, z_tip), 'flash', light=15, noshade=True)
    b.box((-1.0 * r, -0.02, z_tip - L), (1.0 * r, 0.02, z_tip), 'flash', light=15, noshade=True)
    b.box((-0.32 * r, -0.32 * r, z_tip - 0.7 * r), (0.32 * r, 0.32 * r, z_tip), 'flash', light=15, noshade=True)


def flash_paint(f):
    a, b = f.across()
    t = np.clip(1 - np.sqrt(a ** 2 + b ** 2) / 0.9, 0, 1)
    return P.mix(P.col('#ff8a1a'), P.col('#fff6c8'), t ** 0.7)


# ---------------------------------------------------------------- finishes

# key: (title, metal, stock wood, checkered stocks, medallion)
FINISHES = {
    'service': ('Service', 'blued', 'figured', True, 'silver'),
    'nickel': ('Nickel', 'nickel', 'rosewood', False, 'silver'),
    'engraved': ('Engraved', 'royal', 'exhibition', True, 'gold'),
}


def _drawn(f):
    """A face's pixel positions where the element puts them (turned, if it's turned)."""
    rot = f.box.get('rot')
    if not rot:
        return f.P
    R = M.rot_matrix(rot['axis'], rot['angle'])
    o = np.array(rot['origin'])
    return (f.P - o) @ R.T + o


def _cyl_marks(f, c):
    """The cylinder's flutes between the chambers, its stop notches, the chamber mouths in front, the chambers and
    the ejector star behind."""
    pos = _drawn(f)
    dx, dy = pos[..., 0] - AXIS_XY[0], pos[..., 1] - AXIS_XY[1]
    th = np.degrees(np.arctan2(dx, dy)) % 360          # 0 at the top chamber
    z0, z1 = f.box['lo'][2], f.box['hi'][2]
    if abs(f.n[2]) < 0.5:
        m = (th - 30) % 60                              # a flute's middle at 30
        signed = (m - 30) / 30                          # -1 .. 1 across the gap between two chambers
        z = pos[..., 2]
        ends = np.clip(np.minimum(z - (z0 + 0.16 * IN), (z1 - 0.42 * IN) - z) / (0.10 * IN), 0, 1)
        width = 0.42 * np.sqrt(ends)
        inside = np.abs(signed) < width
        across = np.where(inside, signed / np.maximum(width, 1e-3), 0)
        shade = 0.55 + 0.40 * (across + 1) / 2          # lit on one wall, in shadow on the other
        c = np.where(inside[..., None], c * shade[..., None], c)
        n = np.minimum(th % 60, 60 - th % 60)
        notch = (n < 3.5) & (z > z1 - 0.36 * IN) & (z < z1 - 0.20 * IN)
        return np.where(notch[..., None], c * 0.3, c)
    r = np.sqrt(dx * dx + dy * dy)
    near = np.full(f.shape, 9.0)
    for k in range(6):
        a = np.radians(60 * k)
        cx, cy = np.sin(a) * BOLT_R * IN, np.cos(a) * BOLT_R * IN
        near = np.minimum(near, np.sqrt((dx - cx) ** 2 + (dy - cy) ** 2))
    hole = near < CHAMBER_R * IN * 0.92
    if f.n[2] < 0:                                      # the front: chamber mouths with a bright chamfer
        chamfer = (near >= CHAMBER_R * IN * 0.92) & (near < CHAMBER_R * IN * 1.12)
        c = np.where(chamfer[..., None], c * 1.35, c)
        return np.where(hole[..., None], P.col('#080808'), c)
    # the back: the chambers, the ejector star in the middle with its pin
    star_r = (0.30 + 0.05 * np.cos(np.radians(th * 6))) * IN
    star = r < star_r
    c = np.where(hole[..., None], P.col('#0a0a0a'), c)
    c = np.where((star & ~hole)[..., None], c * 1.18, c)
    c = np.where((np.abs(r - star_r) < 0.012 * IN * 2)[..., None] & ~hole[..., None], c * 0.45, c)
    return np.where((r < 0.06 * IN)[..., None], c * 0.6, c)


def look(finish='service', wear=0.35):
    """The revolver's materials for one finish: the metal (blued, nickel, or engraved royal blue), the stocks'
    wood and whether they're checkered, the medallion."""
    _title, metal_kind, grade, checkered, medal = FINISHES[finish]

    def metal(f):
        if metal_kind == 'nickel':
            return F.nickel(f)
        if metal_kind == 'royal':
            return F.blued(f, base='#0e1630', hi='#3c5aa0', polish=0.9)
        return F.blued(f, polish=0.55)

    def engraved(f):
        if metal_kind != 'royal':
            return metal(f)
        return F.coin(f, engraved=True, cell=0.55, base=metal, ink='#d9b25a', inlay=0.7, line=0.04, border=False)

    def frame(f):
        return engraved(f)

    def cyl(f):
        return _cyl_marks(f, metal(f))

    def sight(f):
        c = metal(f)
        if f.n[2] > 0.5:                                # the blade's serrated back
            c = np.where((np.sin(f.Y / IN * 70) > 0.2)[..., None], c * 0.55, c)
        return c

    def hammer(f):
        c = F.case_hardened(f) * 0.8
        if f.box.get('rot') and f.n[1] > 0.5:           # the spur's checkered top
            a, b2 = f.uv[..., 0], f.uv[..., 1]
            grid = (np.sin(a / IN * 55) > 0.3) | (np.sin(b2 / IN * 55) > 0.3)
            c = np.where(grid[..., None], c * 0.5, c)
        return c

    def trigger(f):
        return F.gold(f) if metal_kind == 'royal' else hammer(f)

    def latch(f):
        c = metal(f)
        if f.n[0] < -0.5:
            c = np.where((np.sin(f.Z / IN * 48) > 0.25)[..., None], c * 0.5, c)
        return c

    def wood(f):
        return F.walnut(f, grade, 'y')

    def gcheck(f):
        base = F.walnut(f, grade, 'y')
        return F.checkering(f, base, pitch=0.15) if checkered else base

    def medal_paint(f):
        c = F.gold(f) if medal == 'gold' else P.steel(f, 0.72, tint='#d4d6d8', spread=0.06)
        a, b2 = f.across()
        r = np.sqrt(a * a + b2 * b2)
        return np.where((np.abs(r - 0.09 * IN) < 0.015)[..., None], c * 0.6, c)

    def screw(f):
        return P.steel(f, 0.32, tint='#7d8186', spread=0.08) if metal_kind == 'blued' else P.steel(f, 0.7, tint='#cfd2d4')

    def bore(f):
        return P.plain(f, '#050505', 0.02)

    def crane(f):
        return metal(f)

    def rod(f):
        return P.steel(f, 0.5, tint='#9aa0a6', spread=0.08) if metal_kind == 'blued' else metal(f)

    def knurl(f):
        c = rod(f)
        if abs(f.n[2]) < 0.5:
            c = np.where((np.sin(f.Z / IN * 90) > 0.0)[..., None], c * 0.55, c)
        return c

    mspec = F.METAL_SPEC['nickel' if metal_kind == 'nickel' else 'blued']
    wood_spec = F.WOOD_SPEC['gloss' if grade != 'figured' else 'satin']
    d = dict(frame=frame, barrel=engraved, cyl=cyl, sight=sight, hammer=hammer, trigger=trigger,
             guard=frame, latch=latch, stock=wood, gcheck=gcheck, medal=medal_paint, screw=screw, bore=bore,
             crane=crane, rod=rod, knurl=knurl, flash=flash_paint)
    d['_spec'] = {k: mspec for k in ('frame', 'barrel', 'cyl', 'sight', 'guard', 'latch', 'crane', 'rod', 'knurl')}
    d['_spec'].update({'hammer': F.METAL_SPEC['case'],
                       'trigger': F.METAL_SPEC['gold' if metal_kind == 'royal' else 'case'],
                       'medal': F.METAL_SPEC['gold' if medal == 'gold' else 'nickel'],
                       'stock': wood_spec + (12,), 'gcheck': F.WOOD_SPEC['check' if checkered else 'gloss'] + (30,)})
    # the checkering, the engraving and the cylinder's marks get twice the texels
    d['_px'] = dict(gcheck=32, cyl=32, medal=32, **({'frame': 32, 'barrel': 32, 'guard': 32} if metal_kind == 'royal' else {}))
    d['_after'] = [lambda f, c: P.grime(f, c, 0.22 * (0.4 + f.wear)),
                   lambda f, c: P.scratches(f, c, 0.18 * (0.3 + f.wear))]
    return d
