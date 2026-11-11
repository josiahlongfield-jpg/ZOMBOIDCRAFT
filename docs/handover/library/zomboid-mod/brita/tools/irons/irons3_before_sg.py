"""Wave 3 (the 88 new Brita guns of the refined list, 2026-11-06): their real iron sights, built the same way as
irons.py / irons2.py.

python3 -I irons3.py [gun ...]   (reads guns-orig/, writes guns/; NOIRONS=1 writes guns-noirons/)
Refs: /mnt/project-files/zomboid-mod/refs/items/weapons/irons/<folder>/ (request: brita/irons-ref-request-wave3.md).
Josiah 2026-11-06 06:07: model each gun's FACTORY irons from photos; the pack flip-ups only where the real gun has none.
mm per px = the real gun's overall length / the model's length (B3 works it out from the mesh).
"""
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import irons as I
import sightkit as K
from irons import blade, ears, peep, notch_plate, GUNS
from irons import gun as _gun

WAVE3 = []


def gun(f):
    WAVE3.append(f.__name__)
    return _gun(f)
from irons2 import B, hood, post_ears, aperture, pack_one, handgun, dots
import packirons


class B3(B):
    def __init__(self, gid, oal_mm):
        m = K.read(I.BASE + 'guns-orig/' + gid + '.zmesh', 'local/' + gid)
        P = np.concatenate([K.positions(r).reshape(-1, 3) for n, r in m['parts']])
        super().__init__(gid, float(oal_mm / (P[:, 2].max() - P[:, 2].min())))


def ak_rear(b, X, base, line, zf, zr, col='black', w=14.0, nw=2.4, nd=2.0):
    """AK tangent rear sight: the leaf lies along the top, hinged at its FRONT on the sight block (zf), its U notch
    at the REAR end (zr) toward the eye (AK-47 rear sight photo, refs ak-47/01)."""
    m = b.mm; s = b.s
    s.box(X - m(w / 2), X + m(w / 2), base, base + m(1.6), zf, zr - m(2.0), col)              # the leaf
    s.box(X - m(w / 2 + 1), X + m(w / 2 + 1), base, base + m(3.0), zf + (zr - zf) * 0.45, zf + (zr - zf) * 0.62, col)   # slider
    return notch_plate(s, X, base, line, zr - m(2.0), zr, m(w), m(nw), m(nd), col, u=True)


def ak(gid, oal, X, front, rear, line, col='black', eye=3.4, hood_r=4.0, post=9.0):
    """An AK-pattern gun. front = (cut box, barrel top y, zc); rear = (cut box, base y, z front, z rear).
    The front sight base is a block standing on the barrel up to the hood, the post showing post mm inside it."""
    b = B3(gid, oal); m = b.mm
    (fc, ftop), bt, zc = front[0], front[1], front[2]
    b.cut(*fc, top=ftop)
    fb = line - m(post)
    b.s.box(X - m(5.5), X + m(5.5), bt - m(2), fb, zc - m(6), zc + m(6), col)                 # front sight base
    b.s.box(X - m(8), X + m(8), bt - m(9), bt + m(3), zc - m(6), zc + m(6), col)            # its collar on the barrel
    F = hood(b, X, fb, line, zc, col, pw=1.6, r=hood_r, t=1.5, L=5.0)
    if rear[0]:                                      # Brita's own leaf, if it modelled one (else its block stays)
        rc, rtop = rear[0]
        b.cut(*rc, top=rtop)
    R = ak_rear(b, X, rear[1], line, rear[2], rear[3], col)
    b.sights = (F, [X, line, rear[3]]); b.eye = rear[3] + eye
    b.done()


# ---------------------------------------------------------------- AK family
@gun
def AK74():
    """AK-74 (fixed stock, 943 mm): hooded front post on the front sight block behind the brake; tangent leaf on the
    rear sight block, notch at its rear end. Brita's slanted front fin and flat leaf are cut."""
    ak('AK74', 943, 7.83,
       (((7.6, 8.06, 8.78, 9.8, 0.05, 0.6), 8.62), 8.6, 0.33),
       (((7.4, 8.26, 9.66, 9.8, 8.0, 9.85), 9.62), 9.62, 8.1, 9.8), 9.74)



@gun
def AK74_Custom():
    """Custom AK-74M (943 mm): AK-74 irons. Brita's front is a flat fin on the front sight block (cut, post + hood on
    the block); its rear is a plain sight block in front of the dust cover (kept, leaf laid on it running back)."""
    ak('AK74_Custom', 943, 7.90,
       (((7.7, 8.1, 8.9, 9.8, 0.45, 1.05), 8.86), 8.86, 0.75),
       (None, 9.5, 8.45, 9.9), 9.71)


@gun
def AKM_Custom():
    """Custom AKM (880 mm): AKM irons, as the AK-74 but the front sight block sits further back on the barrel."""
    ak('AKM_Custom', 880, 7.92,
       (((7.7, 8.15, 9.03, 9.6, 1.7, 2.3), 9.0), 9.0, 2.0),
       (None, 9.48, 8.95, 10.3), 9.64)



# ---------------------------------------------------------------- AR family (CAR-15 / M16 refs: car-15-smg, ar15-moe)
def ar_handle(gid, oal, X, leg_top, zf, hand_top, zr, over=4.0, eye=2.5, ri=1.8, ro=5.0):
    """An AR with Brita's fixed triangle and carrying handle kept: the post and its ears stand on the top of the
    triangle's front leg (zf); the A1 rear aperture between its guard wings rises out of the handle's rear sight
    housing (zr). The sight line runs over the aperture's middle, over mm above the handle top."""
    b = B3(gid, oal); m = b.mm; line = float(hand_top + m(over))
    F = post_ears(b, X, leg_top - 0.03, line, zf, 'black', pw=1.8, gap=9.0, t=2.0, L=4.5, bar=False)
    R = aperture(b, X, hand_top - 0.03, line, zr - 0.04, zr + 0.04, 'black', ri=ri, ro=ro, wing_h=3.0)
    b.sights = (F, R); b.eye = R[2] + eye
    b.done()


@gun
def M723():
    """Colt M723 (838 mm): A2 front sight post between the ears on the triangle; A1 carrying handle with its rear
    aperture at the back of the handle."""
    ar_handle('M723', 838, 8.0, 10.2, 3.15, 10.2, 13.6)


@gun
def CAR15_Survival():
    """Colt CAR-15 Survival Rifle (737 mm): A1 front post in the triangle, A1 carrying handle rear aperture."""
    ar_handle('CAR15_Survival', 737, 8.0, 10.43, 0.95, 10.5, 12.85)


@gun
def AssaultRifle():
    """M16A1 (990 mm): A1 front post in the triangle, A1 carrying handle rear aperture."""
    ar_handle('AssaultRifle', 990, 8.0, 10.03, 1.4, 10.02, 14.0)



# ---------------------------------------------------------------- pistols (upright; shooter at +z)
@gun
def G21():
    """Glock 21 (209 mm): Glock's own sights (refs glock-17): polymer front post with a white dot; rear U notch
    with a white outline. Brita's two sight bumps are cut."""
    b = B3('G21', 209); m = b.mm; X = 8.0; top = 10.42
    b.cut(7.8, 8.2, top + 0.02, 10.8, 4.7, 5.25, top=top)
    b.cut(7.6, 8.4, top + 0.02, 10.8, 10.5, 10.85, top=top)
    line = top + m(4.6)
    handgun(b, X, (4.85, 5.15, top - 0.01, m(3.0), 0.0, 'white'), (10.55, 10.75, top - 0.01, m(17), m(4.6), m(3.0), True, 'white', False), line, 'black')
    b.done()



# ---------------------------------------------------------------- bead shotguns (Josiah 2026-11-01: just a bead at the muzzle)
def surface(b, x, z):
    """Highest point of the gun's own mesh over (x, z), or None."""
    T = np.concatenate([K.positions(r) for n, r in b.m['parts'] if n != 'mag'])
    a, c, e = T[:, 0], T[:, 1], T[:, 2]
    d = (c[:, 2] - e[:, 2]) * (a[:, 0] - e[:, 0]) + (e[:, 0] - c[:, 0]) * (a[:, 2] - e[:, 2]); ok = abs(d) > 1e-12; d = np.where(ok, d, 1)
    w0 = ((c[:, 2] - e[:, 2]) * (x - e[:, 0]) + (e[:, 0] - c[:, 0]) * (z - e[:, 2])) / d
    w1 = ((e[:, 2] - a[:, 2]) * (x - e[:, 0]) + (a[:, 0] - e[:, 0]) * (z - e[:, 2])) / d
    w2 = 1 - w0 - w1; k = ok & (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
    if not k.any(): return None
    y = w0[k] * a[k, 1] + w1[k] * c[k, 1] + w2[k] * e[k, 1]
    return float(y.max())


def bead(gid, oal, zb, zr, X=8.0, r=3.0, rib=None, eye=3.0):
    """A brass bead on the barrel (or rib) top at zb; no rear sight, the eye looks along the receiver top at zr.
    rib = (y0, width mm) builds a short rib stub between side-by-side barrels for the bead to sit on."""
    b = B3(gid, oal); m = b.mm; s = b.s
    if rib:
        top = rib[0] + m(2.5)
        s.box(X - m(rib[1] / 2), X + m(rib[1] / 2), rib[0], top, zb - m(6), zb + m(10), 'black')
    else:
        top = surface(b, X, zb)
    rr = m(r); c = top + rr * 0.8
    s.sphere((X, c, zb), rr, 'brass', 8)
    blade(s, X, top - 0.02, c - rr * 0.5, zb - rr, zb + rr, rr * 1.2, 'black')       # its little seat
    b.sights = ((X, c, zb), (X, c, zr)); b.eye = zr + eye
    b.done()


@gun
def M870_Wingmaster():
    """Remington 870 Wingmaster (28 in barrel, 1219 mm): a brass bead at the muzzle (Josiah's bead rule)."""
    bead('M870_Wingmaster', 1219, -6.85, 15.0)


@gun
def VIPER_G2():
    """Viper G2 .410: a bead at the muzzle."""
    bead('VIPER_G2', 1180, -6.85, 15.0)


@gun
def M1887():
    """Winchester 1887: a brass bead at the muzzle."""
    bead('M1887', 1020, -6.85, 13.4)


@gun
def ROYAL_SXS():
    """Holland & Holland Royal side-by-side 10 gauge: the bead on the rib at the muzzle."""
    bead('ROYAL_SXS', 1180, -6.85, 14.4)


@gun
def Coachgun():
    """Stoeger coach gun: side-by-side, the bead on a short rib between the barrels at the muzzle."""
    bead('Coachgun', 940, -6.85, 12.0, rib=(9.3, 9.0))


@gun
def A400():
    """Beretta A400 Xcel Multitarget: the bead at the front of its vent rib."""
    bead('A400', 1270, -6.4, 11.9, r=2.6)



@gun
def SPAS12():
    """Franchi SPAS-12 (folding-stock version; refs spas-12): as SPAS12_Fixed, its sights sit 0.7 px further back
    on this model. Tall front blade on its base at the muzzle; rear aperture on the receiver hump."""
    b = B('SPAS12', 32.0); m = b.mm; X = 8.0; line = 10.83; s = b.s
    b.cut(7.85, 8.15, 9.81, 10.45, -5.35, -4.85, top=9.79)
    blade(s, X, 9.79, line, -5.3, -5.0, m(2.5), 'black', ramp=0.4)
    b.cut(7.85, 8.15, 10.65, 10.95, 8.4, 8.7, top=10.63)
    R = aperture(b, X, 10.63, line, 8.53, 8.6, 'black', ri=1.6, ro=3.6, wing_h=2.5)
    b.sights = ([X, line, -5.15], R); b.eye = R[2] + 3.0
    b.done()



def halfwidth(b, X, z, top):
    """How far either side of X the gun's top surface at z stays within 0.08 px of top (so a cut's patch never hangs
    out past the slide)."""
    w = 0.0
    for dx in np.arange(0.02, 0.6, 0.02):
        a, c = surface(b, X - dx, z), surface(b, X + dx, z)
        if a is None or c is None or a < top - 0.08 or c < top - 0.08: break
        w = dx
    return max(w, 0.08)


def pistol(gid, oal, fcut, ftop, fz, rcut, rtop, rz, over, fw=3.0, ramp=0.0, insert=None, rw=15.0, nw=3.0, nd=2.4,
           u=False, outline=None, dots=False, X=8.0, col='black'):
    """A handgun's fixed pair from the refs: Brita's sight bumps (z ranges fcut/rcut, standing on ftop/rtop) are cut,
    a blade/post (fz) and a notch plate (rz) put back; the line runs over mm above the higher slide top."""
    b = B3(gid, oal); m = b.mm
    hf = min(0.25, halfwidth(b, X, (fcut[0] + fcut[1]) / 2, ftop)); hr = min(0.4, halfwidth(b, X, (rcut[0] + rcut[1]) / 2, rtop))
    b.cut(X - hf, X + hf, ftop + 0.02, ftop + 0.6, *fcut, top=ftop)
    b.cut(X - hr, X + hr, rtop + 0.02, rtop + 0.6, *rcut, top=rtop)
    line = float(max(ftop, rtop) + m(over))
    handgun(b, X, (fz[0], fz[1], ftop - 0.01, m(fw), ramp, insert), (rz[0], rz[1], rtop - 0.01, m(rw), m(nw), m(nd), u, outline, dots), line, col)
    b.done()


@gun
def DEagle():
    """Desert Eagle (270 mm; refs deagle): fixed ramped front blade on the barrel; fixed square rear notch."""
    pistol('DEagle', 270, (3.0, 3.55), 10.30, (3.1, 3.45), (10.75, 11.2), 10.27, (10.85, 11.1), 6.0, fw=3.2, ramp=0.5, rw=16, nw=3.2, nd=2.5)


@gun
def AMT1911():
    """AMT Hardballer (216 mm; refs amt1911): 1911 blade front, notch rear."""
    pistol('AMT1911', 216, (4.65, 5.25), 10.29, (4.75, 5.1), (9.95, 10.35), 10.29, (10.0, 10.25), 5.0, ramp=0.3)


@gun
def B93R():
    """Beretta 93R (240 mm): blade front behind the compensator, notch rear."""
    pistol('B93R', 240, (5.65, 6.15), 10.18, (5.75, 6.05), (10.15, 10.5), 10.18, (10.2, 10.45), 5.0, ramp=0.4, rw=14)


@gun
def M9():
    """Beretta M9 (217 mm; refs m9): 3-dot blade front and notch rear."""
    pistol('M9', 217, (4.75, 5.25), 10.19, (4.85, 5.15), (9.95, 10.4), 10.22, (10.05, 10.3), 5.0, insert='white', dots=True)


@gun
def G34():
    """Taran Tactical Glock 34 (207 mm; refs g34): dot front post, adjustable U-notch rear."""
    pistol('G34', 207, (4.6, 5.1), 10.28, (4.7, 5.0), (10.4, 10.9), 10.28, (10.5, 10.75), 4.6, insert='white', rw=17, nw=4.2, nd=3.0, u=True)


@gun
def VP70():
    """H&K VP70M (204 mm; refs vp70): ramped blade front; the rear 'shadow' notch (a wide notch over a dark
    groove)."""
    pistol('VP70', 204, (4.6, 5.15), 10.31, (4.7, 5.05), (10.55, 10.85), 10.33, (10.6, 10.8), 4.5, ramp=0.4, nw=3.0, nd=2.8)


@gun
def MP45():
    """S&W M&P45 (203 mm): 3-dot."""
    pistol('MP45', 203, (4.55, 5.05), 10.48, (4.65, 4.95), (10.35, 10.85), 10.50, (10.45, 10.75), 5.0, insert='white', nw=3.2, nd=2.6, dots=True)


@gun
def XD4():
    """Springfield XD 4in (182 mm): 3-dot."""
    pistol('XD4', 182, (4.5, 5.0), 10.60, (4.6, 4.9), (10.8, 11.2), 10.56, (10.85, 11.1), 5.0, insert='white', nw=3.2, nd=2.6, dots=True)


@gun
def Makarov():
    """Makarov PM (161 mm): small fixed blade front, notch rear."""
    pistol('Makarov', 161, (4.7, 4.95), 10.36, (4.72, 4.92), (10.55, 10.95), 10.44, (10.6, 10.85), 4.0, fw=2.5, ramp=0.3, rw=12, nw=2.8, nd=2.2)


@gun
def FN_57():
    """FN Five-seveN (208 mm; refs fn-57): 3-dot."""
    pistol('FN_57', 208, (4.8, 5.2), 10.40, (4.85, 5.15), (10.25, 10.8), 10.48, (10.4, 10.7), 4.5, insert='white', nw=3.2, nd=2.6, dots=True)



@gun
def Judge513_45LC():
    """Taurus 513 Judge (241 mm; refs judge513-45lc): fibre-optic (red) front blade on the rib; the rear is the
    top strap's own groove ending in a shallow U notch."""
    pistol('Judge513_45LC', 241, (3.05, 3.95), 10.42, (3.2, 3.8), (9.3, 9.62), 10.50, (9.4, 9.6), 2.0, fw=3.2, ramp=0.5,
           insert='red', rw=10, nw=2.8, nd=1.5, u=True)


@gun
def GP100_6():
    """Ruger GP100 6 in (298 mm; refs gp100-6): ramped front blade with a red insert; adjustable rear with a white
    outline round its notch."""
    pistol('GP100_6', 298, (3.0, 4.15), 10.40, (3.2, 4.0), (9.8, 10.2), 10.47, (9.9, 10.12), 6.0, fw=3.2, ramp=0.5,
           insert='red', rw=14, nw=3.2, nd=2.6, outline='white')


@gun
def M29_44Carbine():
    """Revolver carbine (a .44 revolver with a long barrel and stock; refs m29-44carbine): blade front at the
    muzzle, notch in the frame top."""
    pistol('M29_44Carbine', 700, (2.94, 3.35), 9.86, (3.0, 3.25), (8.6, 9.15), 9.88, (8.9, 9.1), 5.0, ramp=0.3, rw=10, nw=2.6, nd=2.0)


@gun
def M1911_Carbine():
    """M1911 in a carbine kit: the pistol's own 1911 blade and notch (the kit adds no sights)."""
    pistol('M1911_Carbine', 770, (8.4, 8.95), 8.76, (8.5, 8.85), (13.8, 14.2), 8.76, (13.85, 14.1), 5.0, ramp=0.3)



# ---------------------------------------------------------------- SMGs and PDWs
def flipups(gid, oal, X, rail, zf, zr, over=28.0, cuts=(), eye=2.8):
    """A gun whose factory irons are folding diopter sights on its top rail (KRISS type), built deployed from the
    kit (irons.buis), Brita's folded blocks cut first. cuts = [(x0, x1, y0, y1, z0, z1, top)]."""
    b = B3(gid, oal); m = b.mm
    for c in cuts: b.cut(*c[:6], top=c[6])
    F, R = I.buis(b.s, X, rail, float(rail + m(over)), zf, zr, m)
    b.sights = (F, R); b.eye = R[2] + eye
    b.done()


@gun
def Model55():
    """Reising Model 55 (787 mm; refs model55): blade front at the muzzle; adjustable aperture rear on the
    receiver."""
    b = B3('Model55', 787); m = b.mm; X = 8.07; line = float(8.78 + m(6))
    b.cut(X - 0.12, X + 0.12, 8.62, 9.0, 3.1, 3.4, top=8.6)
    blade(b.s, X, 8.58, line, 3.17, 3.33, m(2.0), 'black', ramp=0.4)
    b.cut(X - 0.2, X + 0.2, 8.8, 9.0, 14.85, 15.25, top=8.78)
    R = aperture(b, X, 8.77, line, 15.0, 15.08, 'black', ri=1.5, ro=3.8, wing_h=2.5)
    b.sights = ([X, line, 3.25], R); b.eye = R[2] + 2.6
    b.done()


@gun
def Bizon2_01():
    """PP-19 Bizon-2-01 (660 mm; refs bizon2-01): AK-pattern hooded front post and tangent leaf rear."""
    ak('Bizon2_01', 660, 8.0,
       (((7.75, 8.25, 8.56, 10.1, 4.15, 4.75), 8.52), 8.52, 4.45),
       (((7.7, 8.3, 9.88, 10.0, 9.5, 10.9), 9.86), 9.86, 9.3, 10.6), 10.0)


@gun
def EVO():
    """CZ Scorpion EVO 3 A1 (670 mm; refs evo): folding hooded front post and rotary-drum rear aperture, both on
    the top rail; Brita's two sight blocks are cut."""
    b = B3('EVO', 670); m = b.mm; X = 8.0; rail = 9.53; line = float(rail + m(10))
    b.cut(7.7, 8.3, rail + 0.02, 10.25, 8.5, 9.6, top=rail)  # Brita's folded front block
    b.cut(7.6, 8.4, rail + 0.02, 10.25, 17.4, 18.7, top=rail)
    b.s.box(X - m(8), X + m(8), rail, rail + m(3), 8.75, 9.35, 'black')
    F = hood(b, X, rail + m(3), line, 9.05, 'black', pw=1.8, r=4.5, t=1.6, L=6.0)
    b.s.box(X - m(9), X + m(9), rail, rail + m(3), 17.75, 18.35, 'black')
    R = aperture(b, X, rail + m(3), line, 17.98, 18.08, 'black', ri=1.6, ro=4.0, wing_h=3.0)
    b.sights = (F, R); b.eye = R[2] + 2.6
    b.done()


@gun
def KRISS():
    """KRISS Vector (KRISS's own folding sights, refs kriss): hooded post front, folding diopter rear, on its rail."""
    flipups('KRISS', 740, 8.0, 10.38, 5.9, 16.3, over=24.0, cuts=[(7.9, 8.1, 10.4, 10.6, 16.8, 17.2, 10.38)])



@gun
def MAC10():
    """Ingram MAC-10 (548 mm; refs mac10): front post between protective ears at the front of the receiver; fixed
    rear aperture between ears at the back of the receiver, level over the cocking knob."""
    b = B3('MAC10', 548); m = b.mm; X = 8.0; top = 9.95; line = 10.72
    b.cut(7.6, 8.4, top + 0.02, 10.8, 5.2, 6.4, top=top)
    F = post_ears(b, X, top, line, 5.8, 'black', pw=2.0, gap=8.0, t=2.5, L=7.0)
    b.cut(7.8, 8.2, top + 0.02, 10.6, 15.85, 16.25, top=top)
    R = aperture(b, X, top, line, 15.95, 16.05, 'black', ri=1.6, ro=4.0, wing_h=3.0)
    b.sights = (F, R); b.eye = R[2] + 2.6
    b.done()


def ump(gid):
    """H&K UMP (690 mm; refs ump9/ump45): hooded front post on its block at the muzzle; rotary-drum rear aperture
    at the back of the receiver top."""
    b = B3(gid, 690); m = b.mm; X = 8.0; line = 10.72
    b.cut(7.75, 8.25, 10.3, 10.9, 4.3, 5.1, top=10.28)
    F = hood(b, X, 10.28, line, 4.7, 'black', pw=1.8, r=5.0, t=1.8, L=6.0)
    b.cut(7.7, 8.3, 10.25, 10.7, 14.65, 16.3, top=10.22)
    b.s.box(X - m(10), X + m(10), 10.2, line - m(5), 15.0, 15.5, 'black')                        # the drum's housing
    R = aperture(b, X, line - m(5), line, 15.2, 15.3, 'black', ri=1.8, ro=4.5, wing_h=3.0)
    b.sights = (F, R); b.eye = R[2] + 2.6
    b.done()


@gun
def UMP9():
    ump('UMP9')


@gun
def UMP45():
    ump('UMP45')


@gun
def UZI_Micro():
    """IMI Micro UZI (460 mm): front post between Brita's front ears; flip L rear aperture between the rear
    guards (as the UZI); the line clears the cocking knob."""
    b = B3('UZI_Micro', 460); m = b.mm; X = 8.0; line = 10.72
    blade(b.s, X, 10.08, line, 5.75, 6.05, m(2.0), 'black', ramp=0.3)
    F = [X, line, 5.9]
    b.cut(7.6, 8.4, 10.12, 10.8, 13.0, 14.1, top=10.1)
    b.s.box(X - m(7), X + m(7), 10.08, 10.2, 13.1, 14.0, 'black')
    R = aperture(b, X, 10.2, line, 13.55, 13.65, 'black', ri=1.9, ro=3.8, wing_h=4.0)
    b.sights = (F, R); b.eye = R[2] + 2.6
    b.done()


@gun
def Carbon15_97():
    """Carbon-15 Type 97 (889 mm): flat-top with no factory irons, so the attachment pack's flip-ups go on the
    rail (Josiah 06:07: pack irons only where the real gun has none)."""
    b = B3('Carbon15_97', 889); m = b.mm
    F, R = packirons.fit(b, 8.0, 10.15, float(10.15 + m(35.8)), 12.3, 18.5)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def MPA57DMG():
    """Masterpiece Arms 5.7 pistol: post front at the front of the receiver, notch rear at its back; the line clears
    the raised strip between them."""
    pistol('MPA57DMG', 280, (6.5, 7.05), 10.31, (6.6, 6.95), (10.15, 10.65), 10.31, (10.3, 10.6), 12.0, rw=15, nw=3.2, nd=2.6)



# ---------------------------------------------------------------- scope-only rifles: no factory irons, so the pack's flip-ups
def bore_x(b):
    """x of the muzzle face's middle."""
    P = np.concatenate([K.positions(r).reshape(-1, 3) for n, r in b.m['parts'] if n != 'mag'])
    f = P[P[:, 2] < P[:, 2].min() + 0.15]
    return float((f[:, 0].min() + f[:, 0].max()) / 2)


def scoped(gid, oal, rail, zf, zr):
    """A rifle sold scope-only (refs say no factory irons): the attachment pack's flip-ups on the two ends of its
    scope rail/bases, as the M40A3 in wave 2, so it has irons to start with."""
    b = B3(gid, oal); m = b.mm; X = bore_x(b)
    F, R = packirons.fit(b, X, rail, float(rail + m(35.8)), zf, zr)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def TAC50():
    """McMillan TAC-50 (1448 mm): scope rail only."""
    scoped('TAC50', 1448, 8.78, 11.2, 13.8)


@gun
def CheyTac50():
    """CheyTac M200 Intervention (1400 mm): scope rail only."""
    scoped('CheyTac50', 1400, 9.01, 10.2, 15.3)


@gun
def Sako85():
    """Sako 85 Varmint (1130 mm): scope bases only (front base 8.5-9.5, rear 11.5-12.5)."""
    scoped('Sako85', 1130, 8.43, 9.0, 12.1)


@gun
def L96():
    """L118A1 Arctic Warfare (1180 mm): scope rail only."""
    scoped('L96', 1180, 8.54, 6.8, 11.8)


@gun
def M40():
    """Remington M40 (1117 mm): scope only."""
    scoped('M40', 1117, 8.56, 9.8, 12.3)


@gun
def WA2000():
    """Walther WA2000 (905 mm): scope mount only, on top of the bullpup body."""
    scoped('WA2000', 905, 9.68, 6.8, 11.3)



# ---------------------------------------------------------------- rifles: post (hooded or eared) + rear aperture
def rifle(gid, oal, X, line, fcut, fbase, zf, rcut, rbase, zr, front='hood', pillar=None, ri=1.8, ro=4.5, wing_h=3.5,
          eye=2.6, notes=None):
    """fcut/rcut = (y0, y1, z0, z1, patch top) of Brita's own sight blocks (cut across X +- 0.3), or None.
    front 'hood': hooded post on fbase; 'ears': post between ears (bar under it). pillar = barrel top y: a front
    sight base block from the barrel up to fbase."""
    b = B3(gid, oal); m = b.mm; line = float(line)
    if fcut: b.cut(X - 0.3, X + 0.3, *fcut[:4], top=fcut[4])
    if pillar is not None:
        b.s.box(X - m(5.5), X + m(5.5), pillar - m(2), fbase, zf - m(6), zf + m(6), 'black')
        b.s.box(X - m(8), X + m(8), pillar - m(9), pillar + m(3), zf - m(6), zf + m(6), 'black')
    if front == 'hood':
        F = hood(b, X, fbase, line, zf, 'black', pw=1.8, r=4.5, t=1.6, L=6.0)
    else:
        F = post_ears(b, X, fbase, line, zf, 'black', pw=1.8, gap=9.0, t=2.2, L=5.0)
    if rcut: b.cut(X - 0.35, X + 0.35, *rcut[:4], top=rcut[4])
    if rbase is None: rbase = surface(b, X, zr) - 0.01                      # stand it on whatever is under it
    R = aperture(b, X, rbase, line, zr - 0.04, zr + 0.04, 'black', ri=ri, ro=ro, wing_h=wing_h)
    b.sights = (F, R); b.eye = R[2] + eye
    b.done()


@gun
def AK12_New():
    """AK-12 (2016, 945 mm; refs ak12-new): hooded front post on the gas block; diopter rear at the back of the
    dust-cover rail."""
    rifle('AK12_New', 945, 7.66, 10.05, (9.42, 10.1, 2.25, 2.85, 9.38), 9.38, 2.55, None, None, 14.9)


@gun
def AK308():
    """AK-308 (945 mm; refs ak308): AK-12 pattern, hooded front post and dust-cover diopter rear."""
    rifle('AK308', 945, 7.66, 10.05, (9.42, 10.1, 2.25, 2.85, 9.38), 9.38, 2.55, None, None, 14.9)


@gun
def AN94():
    """AN-94 (943 mm; refs an94): hooded front post; rotary-drum rear aperture on the receiver's back."""
    rifle('AN94', 943, 8.35, 9.64, (8.97, 9.7, 0.75, 1.25, 8.95), 8.95, 1.0, (9.28, 9.6, 13.75, 14.65, 9.26), 9.26, 14.2,
          pillar=None)


@gun
def AR18():
    """ArmaLite AR-18 (965 mm; refs ar18): winged front post on a tall base; flip rear aperture at the back of
    the receiver."""
    rifle('AR18', 965, 7.82, 9.95, (9.14, 9.6, 1.35, 1.95, 9.12), 9.12, 1.65, (9.21, 10.0, 13.15, 14.15, 9.19), 9.19, 13.65,
          front='ears', wing_h=4.0)


@gun
def FAMAS():
    """FAMAS F1 (757 mm; refs famas): post front and aperture rear on the carrying handle's two ends, between
    its wings."""
    rifle('FAMAS', 757, 8.0, 11.42, None, 11.0, 2.75, None, 11.18, 12.9, front='ears')


@gun
def Galil():
    """IMI Galil ARM (979 mm; refs galil): hooded front post; L flip aperture rear at the back of the receiver."""
    rifle('Galil', 979, 8.0, 9.93, (9.2, 10.0, 3.7, 4.2, 9.17), 9.17, 3.95, (9.5, 9.8, 14.4, 15.0, 9.48), 9.48, 14.7)


@gun
def SKS():
    """SKS (1020 mm; refs sks): hooded front post at the muzzle; tangent leaf rear on its block at the front of the
    receiver (AK-type, notch at the leaf's rear end)."""
    ak('SKS', 1020, 8.0, (((7.85, 8.15, 8.27, 9.1, -1.7, -1.3), 8.25), 8.25, -1.5), (None, 8.85, 4.55, 5.6), 9.02)


@gun
def Vz58():
    """Vz. 58 (845 mm; refs vz58 empty, known design): hooded front post; tangent leaf rear at the front of the
    receiver."""
    ak('Vz58', 845, 8.0, (((7.8, 8.2, 8.72, 9.8, -1.1, -0.8), 8.7), 8.7, -0.95), (None, 9.36, 5.15, 6.4), 9.71)



def railflips(gid, oal, X, rf, zf, rr, zr, line=None, cuts=()):
    """No factory irons (refs): the pack's flip-ups, each on its own rail height (front rf at zf, rear rr at zr)."""
    b = B3(gid, oal); m = b.mm
    for c in cuts: b.cut(*c[:6], top=c[6])
    line = float(line or max(rf, rr) + m(35.8))
    F = pack_one(b, 'front', X, rf, line, zf); R = pack_one(b, 'rear', X, rr, line, zr)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


def fsb_rear(gid, oal, X, leg_top, zf, rear, zr, line, pack=False, cuts=()):
    """AR with Brita's fixed front sight base kept: post between ears on the triangle's leg (zf). Rear: a fixed-style
    flip aperture of our own on rear y at zr, or the pack's flip-up rear (pack=True, flat-tops with no factory rear)."""
    b = B3(gid, oal); m = b.mm; line = float(line)
    for c in cuts: b.cut(*c[:6], top=c[6])
    F = post_ears(b, X, leg_top - 0.03, line, zf, 'black', pw=1.8, gap=9.0, t=2.0, L=4.5, bar=False)
    if pack:
        R = pack_one(b, 'rear', X, rear, line, zr)
    else:
        b.s.box(X - m(9), X + m(9), rear, rear + m(4), zr - m(8), zr + m(8), 'black')
        R = aperture(b, X, rear + m(4), line, zr - 0.04, zr + 0.04, 'black', ri=1.8, ro=4.5, wing_h=4.0)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def AUG_A3():
    """Steyr AUG A3 (790 mm): rail only (refs), the pack's flip-ups."""
    railflips('AUG_A3', 790, 8.0, 9.38, 7.1, 9.38, 14.8)


@gun
def L85A2():
    """L85A2 (785 mm): issued with the SUSAT optic and no irons (refs), so the pack's flip-ups on its rail."""
    b = B3('L85A2', 785)
    railflips('L85A2', 785, 7.89, surface(b, 7.89, 9.6), 9.6, surface(b, 7.89, 15.3), 15.3)


@gun
def MK47():
    """CMMG MK-47 Mutant (rail only, refs): the pack's flip-ups, front on the handguard rail, rear on the
    receiver's."""
    b = B3('MK47', 860)
    railflips('MK47', 860, 8.0, surface(b, 8.0, 0.8), 0.8, surface(b, 8.0, 13.2), 13.2)


@gun
def ADAR():
    """ADAR 2-15 (flat-top, refs no factory rear): Brita's fixed front sight base kept with the AR post; the pack's
    flip-up rear on the flat-top."""
    b = B3('ADAR', 860); m = b.mm; rr = surface(b, 8.0, 14.4)
    fsb_rear('ADAR', 860, 8.0, 10.22, 2.75, rr, 14.4, rr + m(35.8), pack=True)


@gun
def Bush_XM15():
    """Bushmaster XM15 flat-top: fixed front sight base with the AR post; the pack's flip-up rear in place of
    Brita's folded block."""
    b = B3('Bush_XM15', 860); m = b.mm
    fsb_rear('Bush_XM15', 860, 8.0, 10.13, 4.1, 9.21, 13.6, 9.21 + m(35.8), pack=True, cuts=[(7.6, 8.4, 9.23, 9.6, 13.05, 14.05, 9.21)])


@gun
def Bush_XM15_Custom():
    """Bushmaster XM15 custom (flat-top): as the XM15."""
    b = B3('Bush_XM15_Custom', 860); m = b.mm; rr = surface(b, 7.86, 14.2)
    fsb_rear('Bush_XM15_Custom', 860, 7.86, 10.15, 4.05, rr, 14.2, rr + m(35.8), pack=True)


@gun
def M4():
    """Colt M4 (840 mm; refs m4): A2 post on the fixed front sight base; a fixed flip-aperture rear on the
    flat-top's back end where Brita has its rear block."""
    fsb_rear('M4', 840, 8.0, 10.39, 4.3, 9.44, 15.45, 10.45)


@gun
def G33():
    """H&K 33 (G33; refs g33 empty, H&K pattern): hooded front post, rotary-drum rear aperture."""
    rifle('G33', 860, 8.0, 9.97, (9.3, 10.0, 1.65, 2.25, 9.28), 9.28, 1.95, (9.34, 9.9, 13.95, 14.95, 9.32), 9.32, 14.45)


@gun
def XM8():
    """H&K XM8 (840 mm): its sight is the integrated optic; back-up irons on the optic's top (front blade, rear
    notch), as the AUG 9mm in wave 2."""
    b = B3('XM8', 840); m = b.mm; X = 8.0; s = b.s; line = float(11.58 + m(5))
    F = blade(s, X, 11.44, line, 10.85, 11.0, m(2.0), 'black', ramp=0.5)
    R = notch_plate(s, X, 11.5, line, 13.8, 13.9, m(12), m(3.6), m(3.0), 'black')
    b.sights = ([X, line, 10.92], [X, line, 13.9]); b.eye = 13.9 + 2.6
    b.done()


@gun
def AK74_Alpha():
    """AK-74 'Alpha' 12 in barrel (railed; refs show the AKS-74U irons): hooded front post on its block; L flip
    aperture at the back of the dust-cover rail."""
    rifle('AK74_Alpha', 830, 7.86, 10.12, None, 9.79, 0.25, None, None, 14.2)


@gun
def Ots14_4A_03():
    """OTs-14 Groza 4A-03 (610 mm; refs ots14-4a-03): post front and notch rear on the carrying handle's ends."""
    b = B3('Ots14_4A_03', 610); m = b.mm; X = 7.74; s = b.s; line = 11.12
    b.cut(X - 0.25, X + 0.25, 10.6, 11.1, 7.95, 8.45, top=10.57)
    F = blade(s, X, 10.57, line, 8.1, 8.3, m(2.0), 'black', ramp=0.3)
    R = notch_plate(s, X, 11.0, line, 12.95, 13.05, m(12), m(3.0), m(2.5), 'black')
    b.sights = ([X, line, 8.2], [X, line, 13.05]); b.eye = 13.05 + 2.6
    b.done()


# ---------------------------------------------------------------- battle rifles and machine guns
@gun
def M14EBR():
    """Mk 14 EBR (889 mm; refs m14ebr): the M14's winged front post on Brita's sight block at the muzzle; the M14
    aperture rear on Brita's rear sight hump behind the rail."""
    rifle('M14EBR', 889, 7.99, 9.31, None, 8.99, -0.42, None, None, 14.68, front='ears')


@gun
def MAS49():
    """MAS 49 (1100 mm; refs mas49): hooded front post on its tall base; aperture rear at the back of the
    receiver."""
    rifle('MAS49', 1100, 8.0, 9.42, (9.06, 9.45, 1.25, 1.95, 9.05), 9.05, 1.6, (9.2, 9.5, 14.65, 15.2, 9.19), 9.19, 14.9)


@gun
def OTS_03_SVU():
    """OTs-03 SVU (900 mm; refs ots-03-svu): folding hooded front post on its block; the diopter rear on the block
    over the receiver."""
    rifle('OTS_03_SVU', 900, 7.79, 9.67, None, 9.4, 1.1, (9.31, 9.62, 12.7, 13.9, 9.3), 9.3, 13.3)


@gun
def M1903():
    """M1903 Springfield (1097 mm; refs m1903): blade front on the muzzle band; the leaf rear on its base ahead
    of the receiver, battle-sight notch at the top of the folded leaf."""
    b = B3('M1903', 1097); m = b.mm; X = 7.71; s = b.s
    b.cut(X - 0.25, X + 0.25, 8.06, 8.7, -9.7, -9.3, top=8.04)
    rb = surface(b, X, 7.0); line = float(rb + m(4.0))
    F = blade(s, X, 8.03, line, -9.6, -9.4, m(2.0), 'black', ramp=0.4)
    s.box(X - m(8), X + m(8), rb - 0.01, rb + m(1.6), 5.9, 7.2, 'black')                 # the folded leaf
    R = notch_plate(s, X, rb, line, 7.2, 7.3, m(16), m(2.6), m(2.0), 'black', u=False)
    b.sights = ([X, line, -9.5], [X, line, 7.3]); b.eye = 7.3 + 3.0
    b.done()


@gun
def RPK16():
    """RPK-16 (1076 mm; refs rpk16): AK-12 pattern, hooded front post on the gas block; diopter rear at the back
    of the top rail."""
    rifle('RPK16', 1076, 7.65, 10.12, (9.73, 10.1, 1.6, 2.5, 9.72), 9.72, 2.1, None, None, 14.6, pillar=9.3)


@gun
def G21LMG():
    """H&K 21 (1021 mm; refs g21lmg): hooded front post; rotary-drum rear aperture on the receiver's back."""
    rifle('G21LMG', 1021, 8.0, 9.78, (9.45, 9.9, -0.85, -0.25, 9.43), 9.43, -0.55, (9.27, 9.72, 13.85, 14.7, 9.26), 9.26, 14.3)


@gun
def M249E3():
    """M249 (1040 mm; refs m249e3): plain post on the tall front base; aperture rear on the feed cover between
    Brita's two protective wings."""
    b = B3('M249E3', 1040); m = b.mm; X = 8.17; s = b.s; line = 9.8
    b.cut(X - 0.25, X + 0.25, 9.53, 10.05, 0.5, 1.25, top=9.52)
    F = blade(s, X, 9.51, line, 0.75, 0.95, m(2.0), 'black')
    R = aperture(b, X, 9.56, line, 14.46, 14.54, 'black', ri=1.8, ro=4.5, wings=False)
    b.sights = ([X, line, 0.85], R); b.eye = R[2] + 2.6
    b.done()


@gun
def RPD():
    """RPD (1037 mm; refs rpd): hooded front post on a block at the muzzle; tangent leaf rear (notch at its rear
    end) on Brita's sight block."""
    ak('RPD', 1037, 8.0, (((7.8, 8.2, 8.07, 9.2, -2.0, -1.45), 8.05), 8.05, -1.72),
       (((7.7, 8.3, 8.94, 9.15, 10.4, 12.8), 8.93), 8.93, 10.6, 12.3), 9.03)


if __name__ == '__main__':
    for name in sys.argv[1:] or WAVE3:
        GUNS[name]()
