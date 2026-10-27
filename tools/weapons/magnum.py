"""The magnum: a big-frame .44 Magnum revolver (a Model 29 sort), drawn from the real thing's measurements: a 6.5"
ribbed barrel with its ejector rod in a half-length shroud underneath, a 1.7" six-shot cylinder, an adjustable rear
sight with a white outlined notch and a ramp front sight with a red insert, target stocks; about 12" long and 6.1"
tall, 47 ounces.

It's the service revolver's frame, lockwork, cylinder and grip grown to the large frame: a private copy of revolver
drawn 1.18 times bigger (its inches are 1.18 real inches, so every measurement below is in its inches), with its
barrel and cylinder set to the magnum's real lengths. The shroud, rib and sights are drawn over it here. It loads
like the service revolver: the cylinder swings out about the crane and takes a round at a time.
"""
import importlib.util
import os

import numpy as np

import paint as P
from inch import IN as REAL_IN, Inch

SCALE = 1.18
U = REAL_IN * SCALE


class ScaledInch(Inch):
    """Inch, with an inch SCALE real inches long."""

    def p(self, x, y, z):
        return self.o + U * np.array([x, y, z], float)

    def rod(self, a0, a1, r, mat, axis=2, at=(0.0, 0.0), **kw):
        others = [i for i in range(3) if i != axis]
        o = self.o
        return self.m.rod(o[axis] + U * a0, o[axis] + U * a1, U * r, mat,
                          cx=o[others[0]] + U * at[0], cz=o[others[1]] + U * at[1], axis=axis, **kw)

    def seg(self, p0, p1, w, t, mat, x=0.0, **kw):
        o = self.o
        return self.m.seg((o[2] + U * p0[0], o[1] + U * p0[1]), (o[2] + U * p1[0], o[1] + U * p1[1]),
                          U * w, U * t, mat, z=o[0] + U * x, plane='zy', **kw)

    def tube(self, z0, z1, r_in, r_out, mat, at=(0.0, 0.0), **kw):
        o = self.o
        rm, w = (r_in + r_out) / 2, r_out - r_in
        return self.m.arc((o[0] + U * at[0], o[1] + U * at[1]), U * rm, -11.25, 348.75, U * w,
                          U * abs(z1 - z0), mat, steps=16, z=o[2] + U * (z0 + z1) / 2, **kw)


def _load():
    """revolver.py, exec'd as a private module with its inches scaled and the magnum's barrel and cylinder."""
    path = os.path.join(os.path.dirname(__file__), 'revolver.py')
    src = open(path).read()
    swaps = [
        ('from inch import IN, Inch', 'IN, Inch = _IN, _Inch'),
        ('BARREL_LEN = 4.0', f'BARREL_LEN = {6.5 / SCALE:.4f}'),
        ('CYL_LEN = 1.56', f'CYL_LEN = {1.67 / SCALE:.4f}'),
        ('FRONT_SIGHT = (-3.85, -3.30)', f'FRONT_SIGHT = ({-6.05 / SCALE:.4f}, {-5.45 / SCALE:.4f})'),
        ("    'service': ('Service', 'blued', 'figured', True, 'silver'),\n"
         "    'nickel': ('Nickel', 'nickel', 'rosewood', False, 'silver'),\n"
         "    'engraved': ('Engraved', 'royal', 'exhibition', True, 'gold'),\n",
         "    'blued': ('Blued', 'blued', 'rosewood', True, 'silver'),\n"
         "    'nickel': ('Nickel', 'nickel', 'exhibition', True, 'silver'),\n"),
    ]
    for a, b in swaps:
        if a not in src:
            raise ValueError(f'magnum: revolver.py has changed, no {a!r} to swap')
        src = src.replace(a, b)
    spec = importlib.util.spec_from_file_location('_magnum_revolver', path)
    mod = importlib.util.module_from_spec(spec)
    mod.__dict__.update(_IN=U, _Inch=ScaledInch)
    exec(compile(src, path, 'exec'), mod.__dict__)
    return mod


D = _load()

BORE_Y, Z0 = D.BORE_Y, D.Z0
MUZZLE = D.MUZZLE
CYL_FRONT, CYL_BACK, CYL_R, CYL_Y = D.CYL_FRONT, D.CYL_BACK, D.CYL_R, D.CYL_Y
SHROUD_END = D.LUG[0] - 0.05        # the shroud runs out to just past the rod's tip
RIB_HALF = 0.15
RIB_TOP = D.BARREL_R + 0.08
SIGHT = D.STRAP_TOP + 0.24          # the line from the rear notch's floor to the top of the front blade
REAR_SIGHT = D.REAR_SIGHT
FRONT_SIGHT = D.FRONT_SIGHT
OPEN = D.OPEN
FINISHES = D.FINISHES
HAND = D.HAND
PIVOT_POINT = D.PIVOT_POINT
HAMMER_PIN = D.HAMMER_PIN
HAMMER_COCK = D.HAMMER_COCK
hammer_piece = D.hammer_piece
CYL_AXIS = D.CYL_AXIS
LOOSE_ROUND = D.LOOSE_ROUND
NOTCH_TOP = SIGHT
frame_point = D.frame_point
_grip = D._grip


def body(m):
    """The big frame, and over it the barrel's rib, the ejector rod's shroud and the target sights."""
    D.body(m)
    b = ScaledInch(m, (0.0, BORE_Y, Z0))
    fr = 'frame'
    # the rib down the barrel's top, matted down its middle
    b.box((-RIB_HALF, D.BARREL_R - 0.06, MUZZLE + 0.02), (RIB_HALF, RIB_TOP, D.FRAME_FRONT), 'barrel', axis=2)
    b.box((-0.08, RIB_TOP, MUZZLE + 0.30), (0.08, RIB_TOP + 0.004, D.FRAME_FRONT - 0.1), 'matte')
    # the shroud: under the barrel from the frame out past the rod's tip, round at its front
    bot = CYL_Y - D.ROD_R - 0.09
    b.box((-0.30, bot, SHROUD_END + 0.18), (0.30, -0.05, D.FRAME_FRONT + 0.02), 'barrel', axis=2)
    b.box((-0.24, bot - 0.04, SHROUD_END + 0.30), (0.24, -0.05, D.FRAME_FRONT + 0.02), 'barrel', axis=2)
    b.rod(-0.30, 0.30, 0.18, 'barrel', axis=0, at=(bot + 0.18, SHROUD_END + 0.18))
    b.box((-0.30, bot + 0.18, SHROUD_END), (0.30, -0.05, SHROUD_END + 0.2), 'barrel', axis=2)
    b.rod(SHROUD_END - 0.006, SHROUD_END + 0.01, D.ROD_R + 0.02, 'bore', axis=2, at=(0.0, CYL_Y))
    # the front sight: a ramp up off the rib, its blade's back face square with a red insert
    f0, f1 = FRONT_SIGHT
    b.box((-0.07, RIB_TOP, f0 - 0.32), (0.07, RIB_TOP + 0.10, f1 + 0.04), 'sight')
    for k in range(3):                                                  # the ramp, stepping up to the blade
        z = f0 - 0.30 + 0.10 * k
        b.box((-0.06, RIB_TOP + 0.10, z), (0.06, RIB_TOP + 0.10 + (SIGHT - RIB_TOP - 0.10) * (k + 1) / 4, f0 + 0.05),
              'sight')
    b.box((-0.06, RIB_TOP + 0.04, f0 + 0.05), (0.06, SIGHT, f1), 'sight')
    b.box((-0.062, SIGHT - 0.18, f1 - 0.16), (0.062, SIGHT - 0.03, f1 - 0.02), 'insert')
    # the rear sight: its base along the top strap, the leaf with a white outline round the notch, the screws
    rz = REAR_SIGHT
    st = D.STRAP_TOP
    b.box((-0.22, st - 0.02, rz - 0.85), (0.22, st + 0.10, rz + 0.06), 'sight')
    b.box((-0.30, st + 0.10, rz - 0.16), (0.30, SIGHT - 0.12, rz + 0.04), 'sight')
    for s in (-1, 1):
        x0, x1 = sorted((s * 0.065, s * 0.30))
        b.box((x0, SIGHT - 0.12, rz - 0.16), (x1, SIGHT + 0.10, rz + 0.04), 'sight')
        x0, x1 = sorted((s * 0.065, s * 0.085))
        b.box((x0, SIGHT - 0.10, rz + 0.04), (x1, SIGHT + 0.10, rz + 0.046), 'outline')
    b.box((-0.085, SIGHT - 0.12, rz + 0.04), (0.085, SIGHT - 0.10, rz + 0.046), 'outline')
    b.rod(0.30, 0.34, 0.05, 'screw', axis=0, at=(SIGHT - 0.02, rz - 0.06))
    b.rod(st + 0.10, st + 0.13, 0.05, 'screw', axis=1, at=(0.0, rz - 0.55))


cylinder = D.cylinder


def round44(m):
    """A .44 Magnum round as it sits home: its rim on the cylinder's back face (z 0), the 1.285" case, the
    semi-wadcutter's lead nose out ahead (1.61" overall). Drawn at real size, not the frame's."""
    b = Inch(m)
    b.round_rod(0.0, 0.06, 0.257, 'brass')
    b.round_rod(-1.285, 0.0, 0.228, 'brass')
    b.round_rod(-1.42, -1.285, 0.215, 'lead')       # the semi-wadcutter's shoulder, then its flat-topped nose
    b.ogive(-1.42, -1.61, 0.17, 'lead', steps=3)
    b.round_rod(0.06, 0.066, 0.10, 'primer')


def case44(m):
    """A fired .44 Magnum case: the rim and the case, its mouth open and sooted."""
    b = Inch(m)
    b.round_rod(0.0, 0.06, 0.257, 'brass')
    b.round_rod(-1.285, 0.0, 0.228, 'brass')
    b.round_rod(-1.293, -1.285, 0.19, 'soot')
    b.round_rod(0.06, 0.066, 0.10, 'primer')


round_look = D.round_look
flash = D.flash
flash_paint = D.flash_paint


def look(finish='blued', wear=0.35):
    """The service revolver's materials for the magnum's finishes, with the rib's matting, the red front insert and
    the rear notch's white outline."""
    d = D.look(finish, wear)

    def matte(f):
        c = d['barrel'](f)
        return np.where((np.sin(f.Z / D.IN * 60) > 0.0)[..., None], c * 0.7, c)

    d['matte'] = matte
    d['insert'] = lambda f: P.plain(f, '#d8282a', 0.04)
    d['outline'] = lambda f: P.plain(f, '#ecebe6', 0.04)
    return d
