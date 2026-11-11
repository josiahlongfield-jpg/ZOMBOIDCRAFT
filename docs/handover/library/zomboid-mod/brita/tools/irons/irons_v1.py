"""Builds each local gun's real iron sights into its .zmesh and writes its sight line into its .json.

python3 -I irons.py [gun ...]     (reads guns-orig/, writes guns/)
Each gun: cut the Brita model's own sight blocks, add sights made from the kit to the real gun's measurements
(mm, scaled by the model's mm per item pixel), and record the line the eye looks along:
sights.front = top of the front post/blade/bead, sights.rear = middle of the aperture or top of the notch.
"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sightkit as K

BASE = '/mnt/project-files/zomboid-mod/brita/local-pc/'
GUNS = {}


def gun(f):
    GUNS[f.__name__] = f
    return f


class Build:
    def __init__(self, gid, mm_per_px):
        self.id = gid; self.k = 1.0 / mm_per_px
        self.m = K.read(BASE + 'guns-orig/' + gid + '.zmesh', 'local/' + gid)
        self.s = K.Shape(); self.cuts = []; self.sights = None; self.eye = 0

    def patch(self, x0, x1, z0, z1, top, depth=0.06):
        """Fills the hole a cut leaves in the surface under it, in that surface's own colour."""
        col = K.colour_at(self.m, (x0 - 0.3, x1 + 0.3, top - 0.08, top + 0.02, z0 - 0.6, z1 + 0.6))
        self.s.box(x0, x1, top - depth, top, z0, z1, col)

    def mm(self, v):
        return v * self.k

    def done(self):
        gone = K.cut(self.m, self.cuts) if self.cuts else 0
        K.add_shapes(self.m, self.s)
        K.write(self.m, BASE + 'guns/' + self.id + '.zmesh', 'local/' + self.id)
        spec = json.load(open(BASE + 'guns-orig/' + self.id + '.json'))
        spec['sights'] = {'front': [round(x, 4) for x in self.sights[0]], 'rear': [round(x, 4) for x in self.sights[1]]}
        if self.eye:
            spec['sights']['eye'] = self.eye
        spec.pop('irons', None)
        json.dump(spec, open(BASE + 'guns/' + self.id + '.json', 'w'), indent=1)
        print(f'{self.id}: cut {gone} triangles, added {sum(len(t) for t in self.s.tris)}, sights {spec["sights"]}')



# ---------------------------------------------------------------- sight pieces (item px; z0 < z1, the shooter is at +z)
def notch_plate(s, X, base, top, z0, z1, w, nw, nd, col, u=False, outline=None, ow=0.0):
    """A rear sight leaf seen from behind: w wide, from base to top, with a square (or U) notch nw wide, nd deep.
    outline: colour of a thin outline round the notch on its rear face (S&W white outline)."""
    if u:
        r = nw / 2; c = top - nd + r
        prof = [(X - w / 2, base), (X + w / 2, base), (X + w / 2, top), (X + r, top), (X + r, c)] + \
               [(X + r * math.cos(a), c - r * math.sin(a)) for a in [i * math.pi / 8 for i in range(1, 8)]] + [(X - r, c), (X - r, top), (X - w / 2, top)]
    else:
        prof = [(X - w / 2, base), (X + w / 2, base), (X + w / 2, top), (X + nw / 2, top), (X + nw / 2, top - nd),
                (X - nw / 2, top - nd), (X - nw / 2, top), (X - w / 2, top)]
    s.prism(prof, z0, z1, col, 'xy')
    if outline:
        e = z1 + 0.004
        s.box(X - nw / 2 - ow, X - nw / 2, top - nd - ow, top, z1, e, outline)
        s.box(X + nw / 2, X + nw / 2 + ow, top - nd - ow, top, z1, e, outline)
        s.box(X - nw / 2, X + nw / 2, top - nd - ow, top - nd, z1, e, outline)
    return (X, top)  # the usual sight picture: the front's tip level with the notch's shoulders


def blade(s, X, base, top, z0, z1, w, col, ramp=0.0, insert=None, ih=0.0):
    """A front blade or post: side profile with an optional ramp toward the muzzle (ramp = fraction of its length);
    insert: colour of a dot/insert on its rear face, ih tall below the top."""
    L = z1 - z0
    prof = [(z0, base), (z1, base), (z1, top), (z0 + L * ramp, top)] if ramp else [(z0, base), (z1, base), (z1, top), (z0, top)]
    s.prism(prof, X - w / 2, X + w / 2, col, 'zy')
    if insert:
        s.box(X - w / 2 - 0.002, X + w / 2 + 0.002, top - ih, top - 0.004, z1 - min(L * 0.35, 0.05), z1 + 0.004, insert)
    return (X, top)


def ears(s, X, base, top, z0, z1, gap, t, col):
    """Two protective wings either side of a front post, gap between their inner faces, t thick."""
    for sgn in (-1, 1):
        a, b = X + sgn * gap / 2, X + sgn * (gap / 2 + t)
        s.box(min(a, b), max(a, b), base, top, z0, z1, col)


def peep(s, X, cy, r_in, r_out, z0, z1, col, n=20):
    """A round rear aperture (the eye looks through its middle)."""
    s.ring(X, cy, r_in, r_out, z0, z1, col, n)
    return (X, cy)


def buis(s, X, rail, line, zf, zr, m, col='black', big=2.5, rw=None):
    """A Magpul MBUS-style pair of flip-up sights, deployed, clamped on a top rail (rail = its top), sight line at
    line (AR absolute co-witness: 35.8 mm over the rail). zf, zr: middle of the front and rear sight along z.
    m: mm -> px. Returns (front tip, rear aperture middle)."""
    bw, bh, bl = (m(26) if rw is None else min(m(26), rw + 0.12)), m(7.5), m(20)
    for zc in (zf, zr):
        s.box(X - bw / 2, X + bw / 2, rail, rail + bh, zc - bl / 2, zc + bl / 2, col)
    top = rail + bh
    # rear: a leaf with the large (ghost) aperture, a web under it and protective wings either side
    t = m(3.5); z0, z1 = zr - t / 2, zr + t / 2; ro = m(big + 2.2)
    s.ring(X, line, m(big), ro, z0, z1, col, 24)
    s.box(X - m(3), X + m(3), top, line - ro * 0.9, z0, z1, col)
    for sg in (-1, 1):
        s.box(min(X + sg * m(6.5), X + sg * m(9)), max(X + sg * m(6.5), X + sg * m(9)), top, line + m(5), z0 - m(1), z1 + m(1), col)
        s.box(min(X + sg * ro * 0.8, X + sg * m(6.5)), max(X + sg * ro * 0.8, X + sg * m(6.5)), line - m(1.5), line + m(1.5), z0, z1, col)
    # front: a square-topped post between two ears, on a cross bar
    t = m(4); z0, z1 = zf - t / 2, zf + t / 2
    s.box(X - m(6), X + m(6), top, top + m(4), z0, z1, col)
    blade(s, X, top + m(4), line, z0, z1, m(1.8), col)
    ears(s, X, top, line + m(3), z0, z1, m(10), m(2.2), col)
    return (X, line, zf), (X, line, zr)

# ---------------------------------------------------------------- the guns
@gun
def Colt1911():
    """GI M1911A1: small staked front blade, small dovetailed rear with a U notch, plain blued (no dots)."""
    b = Build('Colt1911', 31.0); m = b.mm; X = 8.0; top = 10.457
    b.cuts = [(7.8, 8.2, 10.47, 11, 4.7, 5.2), (7.6, 8.4, 10.47, 11, 9.8, 10.3)]
    b.patch(7.9, 8.1, 4.8, 5.06, 10.452); b.patch(7.81, 8.19, 9.88, 10.16, 10.452)
    # front blade: 3.2 mm tall, 1.6 mm wide, 6.4 mm long, rounded back
    fz0, fz1 = 4.78, 4.78 + m(6.4); fh = m(3.2); fw = m(1.6)
    b.s.prism([(fz0, top - 0.02), (fz1, top - 0.02), (fz1, top + fh * 0.7), (fz1 - m(1.5), top + fh), (fz0, top + fh)],
              X - fw / 2, X + fw / 2, 'blued', 'zy')
    # rear: 11.4 mm wide, 3.3 mm tall, 7.6 mm long, U notch 2.0 mm wide 1.3 mm deep, sloped front face
    rz0, rz1 = 10.05, 10.05 + m(7.6); rh = m(3.3); rw = m(11.4); nw = m(2.0); nd = m(1.3)
    prof = [(X - rw / 2, top - 0.02), (X + rw / 2, top - 0.02), (X + rw / 2, top + rh), (X + nw / 2, top + rh),
            (X + nw / 2, top + rh - nd), (X - nw / 2, top + rh - nd), (X - nw / 2, top + rh), (X - rw / 2, top + rh)]
    b.s.prism(prof, rz0, rz1, 'blued', 'xy')
    b.sights = ([X, top + fh, (fz0 + fz1) / 2], [X, top + rh, (rz0 + rz1) / 2])
    b.done()


@gun
def SW_M500():
    """S&W Model 500 (8 3/8" with compensator): interchangeable ramp front with an orange insert on the barrel rib,
    fully adjustable rear on the top strap with a square notch and a white outline (S&W's own)."""
    b = Build('SW_M500', 36.0); m = b.mm; X = 8.0
    b.cuts = [(7.85, 8.15, 10.18, 10.5, 3.8, 4.25), (7.9, 8.1, 10.29, 10.5, 10.6, 11.2)]
    s = b.s
    # front: a base over the old sight's hole, then a 3.2 mm ramped blade with an orange insert facing the shooter
    s.box(7.87, 8.13, 10.0, 10.19, 3.83, 4.22, 'blued')
    F = blade(s, X, 10.18, 10.43, 3.8, 4.22, m(3.2), 'blued', ramp=0.55, insert='orange', ih=m(3.0))
    # rear: flat base on the strap, leaf 12.7 mm wide, 3.3 mm notch 2.8 mm deep, white outline
    s.box(X - m(5), X + m(5), 10.27, 10.32, 10.55, 11.06, 'blued')
    R = notch_plate(s, X, 10.27, 10.45, 10.93, 11.1, m(12.7), m(3.3), m(2.8), 'blued', outline='white', ow=0.018)
    b.sights = ([F[0], F[1], 4.2], [R[0], R[1], 11.1])
    b.done()


@gun
def AAC_Honey():
    """AAC Honey Badger: no sights of its own on the model; real ones are fitted with flip-up BUIS on the top rail,
    here Magpul MBUS-style (black polymer), front at the rail's muzzle end, rear at its back end."""
    b = Build('AAC_Honey', 35.0); m = b.mm
    F, R = buis(b.s, 8.0, 9.398, 9.398 + m(35.8), 0.55, 11.15, m)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def AR57_PDW():
    """AR-57 (57 Center upper on an AR lower, FN P90 magazine lying on top under a full-length rail): sold bare,
    fitted with flip-up BUIS. Brita's little block on the gas block rail is cut; the front BUIS takes its place and
    the rear sits at the back of the top rail, ahead of the charging handle."""
    b = Build('AR57_PDW', 30.0); m = b.mm
    b.cuts = [(7.7, 8.3, 9.375, 9.7, 4.6, 5.45)]
    F, R = buis(b.s, 8.0, 9.369, 9.369 + m(35.8), 5.0, 16.6, m, rw=0.3)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def AssaultRifle2():
    """M14: front blade between two protective ears on the flash suppressor; rear aperture on its leaf at the
    receiver heel, between the windage knob (left) and the elevation knob (right). The aperture is opened up from
    the real 1.5 mm so it can be seen through in game."""
    b = Build('AssaultRifle2', 46.6); m = b.mm; X = 7.70; s = b.s
    b.cuts = [(7.55, 7.85, 8.67, 9.1, -0.45, 0.15), (7.4, 8.0, 8.87, 9.2, 13.95, 14.45)]
    # front: dovetailed base, 1.6 mm blade (shown 2.1), ears 9 mm apart
    s.box(X - 0.12, X + 0.12, 8.6, 8.7, -0.45, 0.12, 'park')
    F = blade(s, X, 8.7, 8.97, -0.38, 0.05, 0.045, 'park', ramp=0.35)
    ears(s, X, 8.7, 8.95, -0.3, 0.05, m(9), m(2.2), 'park')
    # rear: base on the heel, aperture leaf, the two knobs
    s.box(7.44, 7.96, 8.84, 8.92, 13.98, 14.45, 'park')
    t0, t1 = 14.18, 14.24; cy = 9.06
    R = peep(s, X, cy, m(1.9), m(5.5), t0, t1, 'park')
    s.box(X - m(6), X + m(6), 8.92, cy - m(5), t0, t1, 'park')
    s.cyl(14.2, 8.93, m(4.8), 7.30, 7.45, 'park', 12, plane='zy')
    s.cyl(14.2, 8.95, m(5.6), 7.95, 8.12, 'park', 12, plane='zy')
    b.sights = (F + (-0.16,), R + (14.21,)); b.eye = 14.21 + 1.6
    b.done()


@gun
def BOAR():
    """LAR Grizzly Big Boar: sold scope-only, so it gets the back-up irons a .50 rifle wears, Barrett M107-style
    folding steel sights (post between ears, aperture between wings), at the two ends of its scope rail."""
    b = Build('BOAR', 42.5); m = b.mm
    F, R = buis(b.s, 7.4, 9.288, 9.288 + m(35.8), 4.45, 11.55, m, col='park', rw=0.5)
    b.sights = (F, R); b.eye = R[2] + 3.0
    b.done()


@gun
def DSHK():
    """DShK: front post between protective ears on its tall barrel stand; rear folding frame (ladder) leaf, raised,
    with the range slider's notch set low (battle range) so the frame's open slot shows above it. Brita's leaf was
    a solid slider on a 17 cm pole; the real leaf is shorter and open."""
    b = Build('DSHK', 47.7); m = b.mm; X = 8.0; s = b.s
    b.cuts = [(7.7, 8.3, 9.91, 10.8, -5.6, -4.4), (7.6, 8.4, 9.8, 13.2, 18.2, 18.8)]
    # front: head on the stand, 2.9 mm post, ears
    s.box(X - 0.13, X + 0.13, 9.88, 10.02, -5.4, -4.7, 'park')
    F = blade(s, X, 10.02, 10.6, -5.2, -4.95, 0.06, 'park', ramp=0.3)
    ears(s, X, 10.02, 10.64, -5.25, -4.9, 0.26, 0.05, 'park')
    # rear: hinge block, two frame rails with a slot between, top bar, slider with its notch
    z0, z1 = 18.38, 18.52
    s.box(X - 0.24, X + 0.24, 9.76, 9.86, 18.3, 18.6, 'park')
    for sg in (-1, 1):
        a, c = X + sg * 0.11, X + sg * 0.17
        s.box(min(a, c), max(a, c), 9.86, 11.9, z0, z1, 'park')
    notch_plate(s, X, 11.82, 11.95, z0, z1, 0.34, 0.06, 0.05, 'park')
    R = notch_plate(s, X, 10.47, 10.66, z0 - 0.04, z1 + 0.04, 0.44, m(4), m(3), 'park')
    b.sights = (F + (-5.07,), R + (z1 + 0.04,)); b.eye = 22.0
    b.done()


@gun
def L86():
    """L86A1 with the iron-sight carrying handle the L85 family wears without SUSAT: rear aperture in a hooded
    housing at the back of the handle, front post between tall ears on the block over the gas block (Brita's stub
    there is cut and rebuilt)."""
    b = Build('L86', 37.5); m = b.mm; X = 7.9; s = b.s; line = 10.95; rail = 9.978
    b.cuts = [(7.6, 8.2, 10.17, 10.5, 6.55, 7.05)]
    # front
    s.box(X - 0.24, X + 0.24, 10.12, 10.36, 6.6, 7.0, 'black')
    F = blade(s, X, 10.36, line, 6.72, 6.86, m(2), 'black')
    ears(s, X, 10.36, line + m(2), 6.66, 6.92, m(12), m(2.4), 'black')
    # carrying handle: two feet on the rail, the handle bar, the rear sight housing
    for z0, z1 in ((13.0, 13.8), (19.2, 20.0)):
        s.box(X - 0.2, X + 0.2, rail, 10.45, z0, z1, 'black')
    s.box(X - 0.12, X + 0.12, 10.45, 10.6, 13.0, 19.3, 'black')
    t0, t1 = 19.72, 19.84
    R = peep(s, X, line, m(2.4), m(6), t0, t1, 'black')
    s.box(X - m(4), X + m(4), 10.45, line - m(5.5), t0, t1, 'black')
    for sg in (-1, 1):
        a, c = X + sg * m(7), X + sg * m(10)
        s.box(min(a, c), max(a, c), 10.45, line + m(4.5), 19.4, 20.0, 'black')
        a, c = X + sg * m(5.5), X + sg * m(7)
        s.box(min(a, c), max(a, c), line - m(1.5), line + m(1.5), t0, t1, 'black')
    b.sights = (F + (6.79,), R + (19.78,)); b.eye = 22.3
    b.done()


@gun
def M2Carbine():
    """M2 carbine: front post between two protective wings on the barrel band at the muzzle; adjustable rear
    aperture (the late ramp-type sight) on the receiver heel, between protective ears, windage drum on the right."""
    b = Build('M2Carbine', 41.0); m = b.mm; X = 7.75; s = b.s; line = 8.9
    b.cuts = [(7.5, 8.0, 8.45, 8.9, 3.08, 3.58), (7.5, 8.0, 8.69, 9.0, 16.2, 17.0)]
    # front
    s.box(X - 0.16, X + 0.16, 8.38, 8.5, 3.13, 3.53, 'park')
    F = blade(s, X, 8.5, 8.8, 3.22, 3.42, m(2.1), 'park', ramp=0.4)
    ears(s, X, 8.5, 8.84, 3.18, 3.48, m(8), m(2.2), 'park')
    # rear
    s.box(X - 0.17, X + 0.17, 8.64, 8.72, 16.35, 16.95, 'park')
    t0, t1 = 16.72, 16.79
    R = peep(s, X, line, m(1.9), m(4.6), t0, t1, 'park')
    s.box(X - m(3), X + m(3), 8.72, line - m(4.2), t0, t1, 'park')
    for sg in (-1, 1):
        a, c = X + sg * m(5.5), X + sg * m(8)
        s.box(min(a, c), max(a, c), 8.72, line + m(3.5), 16.6, 16.92, 'park')
    s.cyl(16.62, 8.76, m(3.2), X + 0.17, X + 0.27, 'park', 12, plane='zy')
    b.sights = (F + (3.32,), R + (16.755,)); b.eye = 18.6
    b.done()


@gun
def M870_CQB():
    """Remington 870 (short barrel): Josiah's call, a plain bead at the muzzle and no rear sight; the eye looks along
    the receiver. Brita's receiver carries a rail that stands above the barrel, so the brass bead sits on a short
    ramp (as on Remington's police barrels) to show over it."""
    b = Build('M870_CQB', 30.0); m = b.mm; X = 8.0; s = b.s
    # the bead sits on the receiver line: the eye looks along the rail's top and sees the whole bead resting on it
    r = m(3.5); c = 9.762 + r
    blade(s, X, 9.34, c - r + 0.01, -6.98, -6.62, m(3.6), 'black', ramp=0.6)
    s.sphere((X, c, -6.72), r, 'brass', 8)
    b.sights = ((X, c, -6.72), (X, c, 8.8))
    b.done()


@gun
def RPG_7():
    """RPG-7 (checked against References' rpg-7/05): front sight a tall folding frame with a post inside it; rear a
    folding range leaf with an open slot and a slider carrying the U notch (set low, 200 m) and its side knob, on a
    hinge block. The line slopes down toward the muzzle, so the tube rides nose-up for the rocket's drop."""
    b = Build('RPG_7', 30.0); m = b.mm; X = 8.0; s = b.s
    b.cuts = [(7.75, 8.25, 9.41, 10.7, 1.1, 1.6), (7.75, 8.25, 9.69, 10.8, 8.75, 9.2)]
    # front frame round a post
    z0, z1 = 1.27, 1.39
    s.box(X - 0.2, X + 0.2, 9.40, 9.52, z0 - 0.06, z1 + 0.06, 'blued')
    for sg in (-1, 1):
        a_, c = X + sg * 0.14, X + sg * 0.19
        s.box(min(a_, c), max(a_, c), 9.52, 10.42, z0, z1, 'blued')
    s.box(X - 0.19, X + 0.19, 10.40, 10.46, z0, z1, 'blued')
    F = blade(s, X, 9.52, 10.18, z0 + 0.02, z1 - 0.02, m(1.8), 'blued')
    # rear leaf: hinge block, two rails with the slot between, rounded top bar, slider with notch and knob
    zr0, zr1 = 8.86, 8.94
    s.box(X - 0.2, X + 0.2, 9.68, 9.80, 8.72, 9.06, 'blued')
    for sg in (-1, 1):
        a_, c = X + sg * 0.12, X + sg * 0.18
        s.box(min(a_, c), max(a_, c), 9.80, 10.60, zr0, zr1, 'blued')
    s.box(X - 0.18, X + 0.18, 10.56, 10.63, zr0, zr1, 'blued')
    R = notch_plate(s, X, 10.16, 10.32, zr0 - 0.03, zr1 + 0.03, 0.42, m(4), m(4), 'blued', u=True)
    s.cyl(8.9, 10.22, 0.045, X + 0.21, X + 0.34, 'blued', 10, plane='zy')
    b.sights = (F + (1.33,), R + (zr1 + 0.03,))
    b.done()


@gun
def American180():
    """American-180: the pan magazine lies flat on top, so the real gun's sights stand tall to look over it: a
    front post between ears on the muzzle block and a rear peep on a tower behind the pan. Brita's ears had no
    post and its rear was a solid wedge level with the pan; both are rebuilt with the line 11 mm over the pan."""
    b = Build('American180', 30.0); m = b.mm; X = 8.0; s = b.s; line = 8.236 + m(11)
    b.cuts = [(7.7, 8.3, 7.9, 8.6, -5.05, -3.7), (7.65, 8.35, 7.97, 8.5, 15.1, 16.6)]
    # front: post and ears on the muzzle block
    F = blade(s, X, 7.88, line, -4.75, -4.45, m(2), 'blued', ramp=0.4)
    ears(s, X, 7.88, line + m(0.5), -4.85, -4.4, m(9), m(2), 'blued')
    # rear: tower behind the pan with the aperture plate and guard wings
    s.box(X - 0.22, X + 0.22, 7.95, 8.12, 15.2, 16.45, 'blued')
    t0, t1 = 15.88, 15.96
    R = peep(s, X, line, m(2), m(5), t0, t1, 'blued')
    s.box(X - m(3), X + m(3), 8.12, line - m(4.6), t0, t1, 'blued')
    for sg in (-1, 1):
        a, c = X + sg * m(6), X + sg * m(8)
        s.box(min(a, c), max(a, c), 8.12, line + m(4), 15.7, 16.15, 'blued')
        a, c = X + sg * m(4.6), X + sg * m(6)
        s.box(min(a, c), max(a, c), line - m(1.2), line + m(1.2), t0, t1, 'blued')
    b.sights = (F + (-4.6,), R + (15.92,)); b.eye = 18.4
    b.done()


@gun
def TenPointTurbo_XLT():
    """TenPoint Turbo XLT: sold with a scope, so it gets the open sights a hunting crossbow wears instead: a front
    post with a green fibre-optic tip in a hood, on top of the riser, and a rear peep clamped to the back of its
    rail; the line clears the riser and rail."""
    b = Build('TenPointTurbo_XLT', 28.5); m = b.mm; X = 8.0; s = b.s; line = 9.832 + m(18)
    # front: mount on the riser, post with fibre tip, hood ears
    s.box(X - 0.18, X + 0.18, 9.86, 9.98, -5.25, -4.6, 'black')
    F = blade(s, X, 9.98, line, -5.0, -4.8, m(2.2), 'black', insert='green', ih=m(3))
    ears(s, X, 9.98, line + m(2), -5.1, -4.72, m(9), m(2), 'black')
    # rear: peep on a rail clamp
    s.box(X - 0.3, X + 0.3, 9.83, 9.96, 12.6, 13.4, 'black')
    t0, t1 = 13.1, 13.18
    R = peep(s, X, line, m(2.5), m(5.5), t0, t1, 'black')
    s.box(X - m(3), X + m(3), 9.96, line - m(5), t0, t1, 'black')
    b.sights = (F + (-4.9,), R + (13.14,)); b.eye = 16.2
    b.done()


@gun
def Winchester1866():
    """Winchester 1866 "Yellow Boy": brass knife blade front dovetailed near the muzzle; semi-buckhorn rear leaf
    with a V notch on a stepped elevator, on the barrel ahead of the receiver. Brita's front stood higher than its
    rear (the bore would point down); the new pair puts them level."""
    b = Build('Winchester1866', 38.0); m = b.mm; X = 8.0; s = b.s; line = 8.10
    b.cuts = [(7.88, 8.12, 7.93, 8.2, -5.645, -5.25), (7.88, 8.12, 8.0, 8.2, -5.66, -5.64), (7.88, 8.12, 7.895, 8.1, 7.4, 7.6)]
    # front: dovetail base and brass blade
    s.box(X - 0.07, X + 0.07, 7.86, 7.95, -5.62, -5.3, 'blued')
    F = blade(s, X, 7.95, line, -5.58, -5.34, 0.05, 'brass', ramp=0.45)
    # rear: elevator and semi-buckhorn leaf
    s.box(X - 0.1, X + 0.1, 7.86, 7.94, 7.15, 7.45, 'blued')
    prof = [(X - 0.2, 7.86), (X + 0.2, 7.86), (X + 0.2, 8.06), (X + 0.15, 8.16), (X + 0.09, 8.13), (X + 0.055, line),
            (X, line - m(2.6)), (X - 0.055, line), (X - 0.09, 8.13), (X - 0.15, 8.16), (X - 0.2, 8.06)]
    s.prism(prof, 7.44, 7.5, 'blued', 'xy')
    b.sights = ((X, line, -5.46), (X, line, 7.5)); b.eye = 11.0
    b.done()


if __name__ == '__main__':
    for name in sys.argv[1:] or list(GUNS):
        GUNS[name]()
