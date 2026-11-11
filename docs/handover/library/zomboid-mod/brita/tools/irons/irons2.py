"""Wave 2 (the other 27 Brita guns, 2026-11-03): their real iron sights, built the same way as irons.py.

python3 -I irons2.py [gun ...]   (reads guns-orig/, writes guns/; NOIRONS=1 writes guns-noirons/)
Refs: /mnt/project-files/zomboid-mod/refs/items/weapons/irons/<folder>/ (REFERENCES, 2026-11-03).
Every cut is patched over at the surface under it automatically (B.cut), so no holes are left.
"""
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import irons as I
from irons import Build, blade, ears, peep, notch_plate, gun, GUNS
import packirons


class B(Build):
    def cut(self, x0, x1, y0, y1, z0, z1, top=None, depth=0.05):
        """Cut a Brita sight block (triangles with a corner in the box) and fill its footprint at y0 (or top)."""
        self.cuts.append((x0, x1, y0, y1, z0, z1))
        self.patch(x0 + 0.02, x1 - 0.02, z0 + 0.02, z1 - 0.02, y0 if top is None else top, depth=depth)


def dots(s, X, y, z, sep, r, col='white'):
    """Two dots on the rear face of a rear sight (3-dot sights), sep apart, at height y."""
    for sg in (-1, 1):
        s.box(X + sg * sep / 2 - r, X + sg * sep / 2 + r, y - r, y + r, z, z + 0.004, col)


def handgun(b, X, front, rear, line, col):
    """A handgun's fixed pair. front = (z0, z1, base, width, ramp, insert); rear = (z0, z1, base, width, notch w,
    notch d, u, outline, dots). Returns the sight line."""
    s = b.s; fz0, fz1, fb, fw, ramp, ins = front
    F = blade(s, X, fb, line, fz0, fz1, fw, col, ramp=ramp, insert=ins, ih=b.mm(1.6) if ins else 0)
    rz0, rz1, rb, rw, nw, nd, u, outl, dt = rear
    R = notch_plate(s, X, rb, line, rz0, rz1, rw, nw, nd, col, u=u, outline=outl, ow=0.012 if outl else 0)
    if dt:
        dots(s, X, line - nd * 0.6, rz1, nw + b.mm(4), b.mm(0.9))
    b.sights = ([X, line, (fz0 + fz1) / 2], [X, line, rz1])
    return F, R


# ---------------------------------------------------------------- pistols and revolvers (upright; shooter at +z)
@gun
def Automag():
    """.44 AutoMag: tall ramped front blade on the barrel rib at the muzzle; adjustable rear with a square notch at
    the back of the slide (stainless, no dots)."""
    b = B('Automag', 29.5); m = b.mm; X = 8.0
    b.match('steel', (7.8, 8.2, 9.9, 10.11, 5.0, 9.0), 0.9)
    b.cut(7.85, 8.15, 10.118, 10.7, 2.9, 3.7, top=10.115)
    b.cut(7.6, 8.4, 10.318, 10.7, 12.22, 12.5, top=10.314)
    line = 10.314 + m(4.5)
    handgun(b, X, (3.0, 3.55, 10.1, m(3.2), 0.6, None), (12.2, 12.42, 10.31, m(14), m(4.6), m(2.6), False, None, False), line, 'steel')
    b.done()


@gun
def G17():
    """Glock 17: polymer front post with a white dot; rear U notch with a white outline (Glock's own)."""
    b = B('G17', 28.9); m = b.mm; X = 8.0
    b.cut(7.8, 8.2, 10.476, 10.8, 4.7, 5.25, top=10.473)
    b.cut(7.6, 8.4, 10.476, 10.8, 10.45, 10.75, top=10.473)
    line = 10.473 + m(4.6)
    handgun(b, X, (4.82, 5.12, 10.46, m(3.0), 0.0, 'white'), (10.5, 10.7, 10.46, m(17), m(4.6), m(3.0), True, 'white', False), line, 'black')
    b.done()


@gun
def HK_USP():
    """H&K USP: three-dot sights, a white-dot front post on the slide nose and a square notch rear with two dots."""
    b = B('HK_USP', 28.6); m = b.mm; X = 8.0
    b.cut(7.8, 8.2, 10.652, 11.1, 4.6, 5.15, top=10.648)
    b.cut(7.55, 8.45, 10.822, 11.1, 10.7, 11.0, top=10.819)
    line = 10.819 + m(4.2)
    handgun(b, X, (4.72, 5.02, 10.64, m(3.2), 0.2, 'white'), (10.72, 10.92, 10.81, m(17), m(4.6), m(3.0), False, None, True), line, 'black')
    b.done()


@gun
def Colt1903():
    """Colt 1903 Pocket Hammerless: a tiny half-round front blade on the slide nose and a shallow V/U groove rear at
    the back of the slide (a pocket gun's sights, opened up a little to be usable)."""
    b = B('Colt1903', 24.4); m = b.mm; X = 8.0
    b.match('blued', (7.8, 8.2, 10.2, 10.41, 6.0, 10.0), 0.9)
    b.cut(7.85, 8.15, 10.422, 10.7, 4.85, 5.2, top=10.418)
    b.cut(7.6, 8.4, 10.422, 10.7, 10.36, 10.56, top=10.418, depth=0.12)     # Brita's two-shoulder rear
    line = 10.418 + m(2.4)
    handgun(b, X, (4.92, 5.14, 10.41, m(1.8), 0.5, None), (10.38, 10.52, 10.41, m(9), m(3.0), m(1.6), True, None, False), line, 'blued')
    b.done()


@gun
def SW_327():
    """S&W M327 TRR8: ramped front post with a red insert on the barrel rib; fully adjustable black rear with a
    square notch on the top strap."""
    b = B('SW_327', 26.7); m = b.mm; X = 8.0
    b.cut(7.85, 8.15, 10.41, 10.75, 3.2, 3.9, top=10.405)
    b.cut(7.55, 8.45, 10.42, 10.8, 9.1, 9.88, top=10.412)
    s = b.s; line = 10.412 + m(5.2)
    s.box(X - m(5), X + m(5), 10.40, 10.43, 9.15, 9.72, 'black')                     # the rear's base on the strap
    handgun(b, X, (3.3, 3.82, 10.4, m(3.2), 0.55, 'red'), (9.52, 9.72, 10.41, m(13), m(4.4), m(2.8), False, None, False), line, 'black')
    b.done()


@gun
def Colt_Service38():
    """Colt New Service: half-moon front blade on the barrel; the rear is a groove along the top strap ending in a
    notch at its back, level with the front."""
    b = B('Colt_Service38', 24.8); m = b.mm; X = 8.0; s = b.s
    b.match('blued', (7.8, 8.2, 9.9, 10.08, 4.5, 6.5), 0.9)
    b.cut(7.85, 8.15, 10.092, 10.5, 3.3, 3.8, top=10.088)
    line = 10.31
    fz0, fz1 = 3.38, 3.72; h = line - 10.08
    prof = [(fz0, 10.08), (fz1, 10.08)] + [(fz1 - (fz1 - fz0) / 2 * (1 - math.cos(a)), 10.08 + h * math.sin(a)) for a in [i * math.pi / 10 for i in range(1, 10)]]
    s.prism(prof + [(fz0, 10.08 + h * 0.15)], X - m(1.5), X + m(1.5), 'blued', 'zy')
    # the groove's end: two shoulders either side of the notch, standing just over the strap
    notch_plate(s, X, 10.2, line, 9.72, 9.86, m(11), m(2.6), line - 10.262, 'blued', u=True)
    b.sights = ([X, line, (fz0 + fz1) / 2], [X, line, 9.86])
    b.done()


def pack_one(b, which, X, rail, line, z, colour='dark'):
    """Only one of the pack's flip-up sights (the gun keeps a fixed sight of its own at the other end)."""
    sc = (line - rail) / (packirons.REAR_C[1] - packirons.RAIL)
    ref = packirons.FRONT_T if which == 'front' else packirons.REAR_C
    suffix = '' if colour == 'base' else '_' + colour
    b.meshes.append((packirons.load(f'att_irons_{which}_up{suffix}'), sc, (X - sc * ref[0], line - sc * ref[1], z - sc * ref[2])))
    return (X, line, z)


def post_ears(b, X, base, line, zc, col, pw=1.8, gap=9.0, t=2.2, L=4.0, over=3.0, bar=True):
    """A front post between two protective ears (mm sizes), standing on base, its tip on line."""
    m = b.mm; s = b.s; z0, z1 = zc - m(L) / 2, zc + m(L) / 2
    if bar:
        s.box(X - m(gap / 2 + t), X + m(gap / 2 + t), base - 0.02, base + m(2.5), z0, z1, col)
    blade(s, X, base, line, z0 + m(0.6), z1 - m(0.6), m(pw), col)
    ears(s, X, base, line + m(over), z0, z1, m(gap), m(t), col)
    return (X, line, zc)


def hood(b, X, base, line, zc, col, pw=1.8, r=5.0, t=1.6, L=6.0):
    """A hooded front post (AK, SV-98, PKM): post inside an open-bottomed ring."""
    m = b.mm; s = b.s; z0, z1 = zc - m(L) / 2, zc + m(L) / 2
    blade(s, X, base, line, zc - m(1.2), zc + m(1.2), m(pw), col)
    cy = line - m(0.5)
    s.ring(X, cy, m(r), m(r + t), z0, z1, col, 20)
    for sg in (-1, 1):
        a, c = X + sg * m(r), X + sg * m(r + t)
        s.box(min(a, c), max(a, c), base, cy, z0, z1, col)
    return (X, line, zc)


def aperture(b, X, base, line, z0, z1, col, ri=2.0, ro=5.0, wings=True, wing_h=4.0):
    """A rear peep on a leaf standing on base, aperture middle on line, guard wings either side."""
    m = b.mm; s = b.s
    peep(s, X, line, m(ri), m(ro), z0, z1, col)
    s.box(X - m(3), X + m(3), base, line - m(ro) * 0.9, z0, z1, col)
    if wings:
        for sg in (-1, 1):
            a, c = X + sg * m(ro + 1.5), X + sg * m(ro + 3.5)
            s.box(min(a, c), max(a, c), base, line + m(wing_h), z0 - m(2), z1 + m(2), col)
            a, c = X + sg * m(ro - 0.5), X + sg * m(ro + 1.5)
            s.box(min(a, c), max(a, c), line - m(1.2), line + m(1.2), z0, z1, col)
    return (X, line, (z0 + z1) / 2)


# ---------------------------------------------------------------- SMGs and rifles (long guns; shooter at +z)
@gun
def CAR15SMG():
    """Colt CAR-15 SMG (XM177 style): A1 front sight, a post between the triangle's ears; rear aperture in the
    A1 carrying handle's rear sight housing, between its guard wings."""
    b = B('CAR15SMG', 34.5); m = b.mm; X = 8.0; line = 10.25
    b.cut(7.7, 8.3, 9.87, 10.5, 5.38, 5.95, top=9.85)
    F = post_ears(b, X, 9.85, line, 5.66, 'black', pw=1.8, gap=9.5, L=5)
    R = aperture(b, X, 10.10, line, 16.5, 16.58, 'black', ri=1.8, ro=5.0)
    b.s.box(X - m(6), X + m(6), 10.06, 10.12, 16.3, 16.9, 'black')
    b.sights = (F, R); b.eye = R[2] + 2.5
    b.done()


@gun
def MP7():
    """H&K MP7: its own flip-up sights at the two ends of the top rail; Brita's folded sight blocks are cut and
    the attachment pack's flip-ups stand in their places (the flip-up rule)."""
    b = B('MP7', 26.8); m = b.mm
    b.cut(7.6, 8.4, 10.9, 11.5, 7.6, 10.3, top=10.891)
    b.cut(7.6, 8.75, 10.9, 11.9, 19.9, 22.7, top=10.891)   # incl. the drum on its right side
    F, R = packirons.fit(b, 8.0, 10.891, 10.891 + m(28), 8.6, 21.4)
    b.sights = (F, R); b.eye = R[2] + 2.8
    b.done()


@gun
def UZI():
    """IMI UZI: front post between tall protective ears at the front of the receiver; rear flip L aperture at the
    back of the receiver between its guards."""
    b = B('UZI', 29.0); m = b.mm; X = 8.0; line = 10.17   # over the cocking knob (top 10.12), level with the ears
    # Brita's front ears stand at z 6.0-6.9 and rear ears at 16.0-17.1 (x +-0.6); the knob at 8.7-9.2 stays
    b.cut(7.6, 8.4, 9.33, 10.3, 16.05, 17.0, top=9.3)
    blade(b.s, X, 9.34, line, 6.25, 6.55, m(2.0), 'black', ramp=0.3)
    F = [X, line, 6.4]
    b.s.box(X - m(8), X + m(8), 9.28, 9.40, 16.15, 16.9, 'black')
    R = aperture(b, X, 9.40, line, 16.5, 16.58, 'black', ri=1.9, ro=3.8, wing_h=4.0)
    b.sights = (F, R); b.eye = R[2] + 2.6
    b.done()


@gun
def AUG9():
    """Steyr AUG 9mm: its sight is the integral optic; the real gun carries emergency irons on top of the optic
    housing (a small front blade and a rear notch), built here on the housing's top."""
    b = B('AUG9', 30.2); m = b.mm; X = 8.0; s = b.s; line = 11.0 + m(5.5)
    F = blade(s, X, 10.92, line, 11.02, 11.18, m(2.0), 'black', ramp=0.5)
    R = notch_plate(s, X, 11.0, line, 17.0, 17.1, m(12), m(3.6), m(3.0), 'black')
    b.sights = ([X, line, 11.1], [X, line, 17.1]); b.eye = 17.1 + 2.6
    b.done()


@gun
def Bush_AR15_MOE():
    """Bushmaster AR-15 with Magpul MOE furniture: the A2 fixed front sight (post between the triangle's ears)
    and a Magpul MBUS rear on the flat-top, which is the attachment pack's flip-up rear (the flip-up rule)."""
    b = B('Bush_AR15_MOE', 34.9); m = b.mm; X = 8.0; rail = 9.174; line = rail + m(35.8)
    b.cut(7.7, 8.3, 9.97, 10.6, 3.75, 4.3, top=9.95)
    F = post_ears(b, X, 9.95, line, 4.0, 'black', pw=1.8, gap=9.5, L=5)
    R = pack_one(b, 'rear', X, rail, line, 15.0)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def AK47():
    """AK-47: hooded front post on the front sight block at the muzzle; tangent rear leaf with its U notch at the
    rear end of the leaf, on the rear sight base ahead of the receiver."""
    b = B('AK47', 30.0); m = b.mm; X = 8.0; line = 9.18
    b.cut(7.75, 8.25, 8.97, 9.5, -1.75, -1.2, top=8.95)
    F = hood(b, X, 8.95, line, -1.48, 'black', pw=1.6, r=4.0, t=1.5, L=5.0)
    b.cut(7.7, 8.3, 9.07, 9.4, 8.6, 9.1, top=9.05)
    s = b.s
    s.box(X - m(6), X + m(6), 9.03, 9.08, 8.62, 9.05, 'black')                        # the leaf, hinged at its front
    # 2026-11-06: the U notch is at the leaf's REAR end, toward the eye (AK-47 rear sight photo refs ak-47/01)
    R = notch_plate(s, X, 9.05, line, 8.97, 9.05, m(14), m(3.0), m(2.2), 'black', u=True)
    b.sights = (F, [X, line, 9.05]); b.eye = 9.05 + 3.4
    b.done()


@gun
def F2000():
    """FN F2000 Tactical: the optic is replaced by the rail, which carries flip-up back-up irons (pack flip-ups)."""
    b = B('F2000', 31.3); m = b.mm
    F, R = packirons.fit(b, 8.0, 9.964, 9.964 + m(35.8), 3.8, 12.0)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def G36C():
    """H&K G36C (the rail handle with its own sights): front post on the front end of the handle, rear aperture
    at the handle's back end, both standing on the handle's top."""
    b = B('G36C', 30.0); m = b.mm; X = 8.0; top = 10.344; line = top + m(9)
    F = post_ears(b, X, top, line, 3.7, 'black', pw=1.8, gap=8.5, L=5)
    R = aperture(b, X, top, line, 13.1, 13.18, 'black', ri=1.9, ro=4.6)
    b.sights = (F, R); b.eye = R[2] + 2.6
    b.done()


@gun
def SCARL():
    """FN SCAR-L: folding front and rear sights at the two ends of its top rail; Brita's are cut and the pack's
    flip-ups stand in their places (the flip-up rule)."""
    b = B('SCARL', 34.0); m = b.mm
    b.cut(7.7, 8.3, 9.56, 10.9, 3.95, 4.5, top=9.53)
    b.cut(7.6, 8.4, 9.62, 10.2, 13.95, 14.9, top=9.6)
    F, R = packirons.fit(b, 8.0, 9.531, 9.531 + m(35.8), 4.25, 14.4)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


# ---------------------------------------------------------------- battle and sniper rifles (batch 3)
@gun
def FAL():
    """FN FAL: front post between protective ears on the front sight block at the gas block; rear aperture with
    protective ears at the back of the receiver cover."""
    b = B('FAL', 45.4); m = b.mm; X = 8.0; line = 9.44
    b.cut(7.85, 8.15, 9.1, 9.6, 3.55, 4.1, top=9.07)
    F = post_ears(b, X, 9.07, line, 3.82, 'black', pw=2.0, gap=8.0, t=2.0, L=6, over=1.5)
    b.cut(7.6, 8.4, 9.1, 9.6, 15.5, 16.2, top=8.98)
    R = aperture(b, X, 8.98, line, 15.8, 15.88, 'black', ri=1.1, ro=4.2, wing_h=3.0)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def SCARH():
    """FN SCAR-H: same folding sights as the SCAR-L (same mesh): Brita's are cut and the pack's flip-ups stand in
    their places (the flip-up rule)."""
    b = B('SCARH', 34.0); m = b.mm
    b.cut(7.7, 8.3, 9.56, 10.9, 3.95, 4.5, top=9.53)
    b.cut(7.6, 8.4, 9.62, 10.2, 13.95, 14.9, top=9.6)
    F, R = packirons.fit(b, 8.0, 9.531, 9.531 + m(35.8), 4.25, 14.4)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def M1():
    """M1 Garand (its bore sits at x 7.68): front post between two wings at the muzzle; rear aperture on the
    receiver heel (z 14) with its guard wings and the windage (right) and elevation (left) knobs."""
    b = B('M1', 45.8); m = b.mm; X = 7.68; line = 9.28; s = b.s
    b.cut(7.5, 7.86, 8.75, 9.3, -1.4, -0.9, top=8.73)
    F = post_ears(b, X, 8.73, line, -1.15, 'black', pw=2.0, gap=9.0, t=2.0, L=7, over=2.0)
    # Brita's rear sight lump on the receiver heel (z 13.6-14.35) is cut and the real sight built in its place
    b.cut(7.5, 7.9, 9.03, 9.35, 13.6, 14.35, top=9.01)
    R = aperture(b, X, 9.01, line, 13.95, 14.01, 'black', ri=0.9, ro=3.2, wing_h=2.5)
    for sg in (-1, 1):
        a, c = X + sg * m(8), X + sg * m(15)
        s.cyl(13.98, 9.07, m(6), min(a, c), max(a, c), 'black', plane='zy')
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def M82():
    """Barrett M82A1: folding front and rear sights at the two ends of its top rail (the pack's flip-ups)."""
    b = B('M82', 42.6); m = b.mm
    F, R = packirons.fit(b, 8.0, 9.195, 9.195 + m(35.8), -3.9, 14.2)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def M40A3():
    """M40A3 (bore at x 7.75): a scope rifle with no irons of its own; back-up flip-ups (the pack's) on the two
    ends of its scope rail so it has irons to start with."""
    b = B('M40A3', 33.0); m = b.mm
    F, R = packirons.fit(b, 7.75, 8.653, 8.653 + m(35.8), 8.45, 12.7)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def SV_98():
    """SV-98 (bore at x 7.8): hooded front post on top of its front sight block at the muzzle; rear diopter
    (aperture with guard wings) at the back of the receiver."""
    b = B('SV_98', 35.0); m = b.mm; X = 7.8; line = 9.08
    F = hood(b, X, 8.955, line, -7.6, 'black', pw=1.8, r=4.0, t=1.5, L=6.0)
    R = aperture(b, X, 8.545, line, 13.1, 13.18, 'black', ri=1.0, ro=4.0, wing_h=3.0)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


# ---------------------------------------------------------------- shotguns and the Ruger No. 1 (batch 4)
@gun
def UTS15():
    """UTS-15: folding front and rear sights on its full-length top rail; Brita's folded ones are cut and the pack's
    flip-ups stand in their places (the flip-up rule)."""
    b = B('UTS15', 32.0); m = b.mm
    b.cut(7.3, 8.7, 10.88, 11.2, -6.3, -4.15, top=10.86)
    b.cut(7.3, 8.7, 10.88, 11.35, 12.05, 14.3, top=10.86)
    F, R = packirons.fit(b, 8.0, 10.86, 10.86 + m(35.8), -5.2, 13.6)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def M1014_Wick():
    """Benelli M4 / M1014: front post between protective ears near the muzzle; rear ghost ring (a big aperture)
    between tall protective ears at the back of the receiver rail."""
    b = B('M1014_Wick', 32.0); m = b.mm; X = 8.0; line = 10.52
    b.cut(7.85, 8.15, 9.97, 10.6, -6.5, -5.45, top=9.91)
    F = post_ears(b, X, 9.91, line, -5.95, 'black', pw=2.2, gap=10.0, t=2.5, L=8, over=1.5)
    b.cut(7.65, 8.35, 10.0, 10.75, 12.5, 13.5, top=9.98)
    R = aperture(b, X, 9.98, line, 12.95, 13.05, 'black', ri=3.0, ro=5.5, wing_h=3.5)
    b.sights = (F, R); b.eye = R[2] + 2.8
    b.done()


@gun
def SPAS12_Fixed():
    """Franchi SPAS-12: tall front blade on its base at the muzzle; rear aperture on the hump at the front of the
    receiver top."""
    b = B('SPAS12_Fixed', 32.0); m = b.mm; X = 8.0; line = 10.78; s = b.s
    b.cut(7.85, 8.15, 9.78, 10.4, -5.45, -5.05, top=9.76)
    blade(s, X, 9.76, line, -5.4, -5.1, m(2.5), 'black', ramp=0.4)
    b.cut(7.85, 8.15, 10.6, 10.9, 7.7, 8.0, top=10.58)
    R = aperture(b, X, 10.58, line, 7.83, 7.9, 'black', ri=1.6, ro=3.6, wing_h=2.5)
    b.sights = ([X, line, -5.25], R); b.eye = R[2] + 3.0
    b.done()


@gun
def Ruger_No1_308():
    """Ruger No. 1 (.308): ramp front with a brass bead on a barrel band at the muzzle; folding leaf rear with a
    shallow notch on the barrel ahead of the quarter rib (the sight line clears the rib)."""
    b = B('Ruger_No1_308', 35.0); m = b.mm; X = 8.0; line = 8.02; s = b.s
    s.box(X - m(5), X + m(5), 7.60, 7.66, -5.95, -5.45, 'black')
    blade(s, X, 7.645, line, -5.9, -5.55, m(2.0), 'black', ramp=0.6)
    s.sphere((X, line - m(1.2), -5.56), m(1.3), 'brass', 8)
    s.box(X - m(5), X + m(5), 7.67, 7.72, 5.85, 6.15, 'black')
    notch_plate(s, X, 7.69, line, 5.95, 6.02, m(16), m(3.0), m(2.4), 'black')
    b.sights = ([X, line, -5.6], [X, line, 6.02]); b.eye = 6.02 + 4.5
    b.done()


# ---------------------------------------------------------------- machine guns (batch 5)
@gun
def M60():
    """M60 (bore at x 9.05): tall front blade on its sloped base near the muzzle; rear folding leaf with its
    aperture, raised, on the receiver ahead of the feed cover."""
    b = B('M60', 46.0); m = b.mm; X = 9.05; line = 10.05; s = b.s
    b.cut(8.9, 9.2, 8.75, 10.2, 0.4, 1.2, top=8.65)
    blade(s, X, 8.65, line, 0.45, 0.85, m(2.5), 'black', ramp=0.5)
    b.cut(8.85, 9.25, 9.45, 9.7, 10.95, 12.5, top=9.32)
    s.box(X - m(8), X + m(8), 9.30, 9.36, 12.1, 12.45, 'black')                     # the leaf's hinge base
    R = aperture(b, X, 9.36, line, 12.25, 12.31, 'black', ri=1.2, ro=3.6, wings=False)
    b.sights = ([X, line, 0.65], R); b.eye = R[2] + 3.0
    b.done()


@gun
def PKM():
    """PKM (bore at x 8.28): hooded front post on top of the tall front sight tower at the muzzle; tangent rear
    leaf with a U notch on the receiver top cover."""
    b = B('PKM', 49.0); m = b.mm; X = 8.28; line = 9.80; s = b.s
    F = hood(b, X, 9.635, line, 0.08, 'black', pw=1.8, r=4.0, t=1.5, L=5.0)
    b.cut(8.2, 8.45, 9.6, 9.75, 13.35, 13.72, top=9.6)
    s.box(X - m(7), X + m(7), 9.58, 9.62, 13.2, 13.7, 'black')                       # the leaf lying forward
    notch_plate(s, X, 9.6, line, 13.6, 13.66, m(14), m(3.0), m(2.4), 'black', u=True)
    b.sights = (F, [X, line, 13.66]); b.eye = 13.66 + 3.4
    b.done()


if __name__ == '__main__':
    W2 = [n for n, f in GUNS.items() if f.__module__ == '__main__']
    for name in sys.argv[1:] or W2:
        GUNS[name]()
