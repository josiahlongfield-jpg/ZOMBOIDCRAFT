"""Gun attachments, each drawn from a real one's measurements in its own mount frame: the origin on the
top face of the rail it clamps to, +y away from the rail, the muzzle end toward -z. Muzzle devices sit on
the bore line with the origin at the end of the barrel. kit.py turns each one onto whichever rail it's
fitted to, so one drawing serves every gun.
"""
import numpy as np

import model as M
import paint as P
from inch import Inch

CO_WITNESS = 1.41       # optics put their reticle this far over the rail, level with folding irons


class Att:
    def __init__(self, key, item, title, slot, build, look, sight=None, note=''):
        self.key, self.item, self.title, self.slot = key, item, title, slot
        self.build, self.look = build, look
        self.sight = sight      # where the eye lines up, in the mount frame (inches): optics only
        self.note = note


def jaws(b, z0, z1, half=0.47, mat='anod'):
    """A rail clamp's two jaws, hooked under the rail's dovetail."""
    b.box((-half - 0.05, -0.26, z0), (-half + 0.05, 0.0, z1), mat)
    b.box((half - 0.05, -0.26, z0), (half + 0.05, 0.0, z1), mat)


# ---------------------------------------------------------------- optics

def red_dot(m):
    """A micro red dot (Aimpoint T-2 style) on a quick-release riser: the tube is open so you look
    straight through it to the dot."""
    b = Inch(m)
    H = CO_WITNESS
    b.box((-0.47, 0.0, -0.62), (0.47, 0.30, 0.62), 'anod')
    jaws(b, -0.62, 0.62)
    b.box((-0.62, 0.02, -0.50), (-0.52, 0.24, 0.40), 'anod')                 # throw lever
    b.box((-0.33, 0.30, -0.52), (0.33, 0.66, 0.52), 'anod')                  # riser
    b.box((-0.40, 0.66, -0.78), (0.40, 0.88, 0.78), 'anod')                  # sight's own base
    b.tube(-1.18, 1.18, 0.46, 0.60, 'anod', at=(0.0, H))
    b.tube(-1.26, -1.10, 0.46, 0.64, 'anod', at=(0.0, H))                    # lens rims
    b.tube(1.10, 1.26, 0.46, 0.64, 'anod', at=(0.0, H))
    b.rod(H + 0.50, H + 0.82, 0.27, 'anod', axis=1, at=(0.0, 0.05))          # elevation turret
    b.rod(0.52, 0.86, 0.30, 'knurl', axis=0, at=(H, 0.25))                    # brightness dial
    b.box((-0.03, H - 0.03, -0.96), (0.03, H + 0.03, -0.92), 'dot', light=15, noshade=True)


def holo(m):
    """A holographic sight (EOTech EXPS3 style): a hood open front and back over the body, the 65 MOA
    ring and dot floating in the window."""
    b = Inch(m)
    H = CO_WITNESS
    L0, L1 = -1.85, 1.30
    b.box((-0.68, 0.0, L0), (0.68, 0.32, 1.75), 'anod')
    jaws(b, -1.30, 0.70, half=0.66)
    for z0 in (-1.05, 0.05):                                                  # QD levers
        b.box((-0.84, 0.03, z0), (-0.70, 0.27, z0 + 0.75), 'anod')
    b.box((-0.62, 0.32, L0), (0.62, H - 0.52, 1.75), 'anod')                 # body, battery in the back
    for x0, x1 in ((-0.62, -0.44), (0.44, 0.62)):                             # hood walls
        b.box((x0, H - 0.52, L0), (x1, H + 0.76, L1), 'anod')
    b.box((-0.62, H + 0.54, L0), (0.62, H + 0.76, L1), 'anod')               # hood top
    b.box((-0.44, H - 0.52, L0), (0.44, H - 0.46, L1), 'anod')                # window sill
    for z0 in (L0, L1 - 0.06):                                                # window frames, front and back
        b.box((-0.44, H + 0.48, z0), (0.44, H + 0.54, z0 + 0.06), 'anod')
    for x0 in (-0.36, 0.10):                                                  # brightness buttons on the back
        b.box((x0, 0.55, 1.75), (x0 + 0.26, 0.75, 1.82), 'rubber')
    b.box((0.62, H - 0.30, -0.95), (0.70, H - 0.08, -0.55), 'steel')          # windage screw
    b.box((-0.08, H + 0.76, -0.55), (0.08, H + 0.80, -0.20), 'steel')         # elevation screw
    b.ring(-0.22, -0.20, 0.17, 'dot', at=(0.0, H), steps=8, w=0.025, light=15, noshade=True)
    b.box((-0.02, H - 0.02, -0.22), (0.02, H + 0.02, -0.20), 'dot', light=15, noshade=True)


SCOPE_H = 1.50


def scope_4x(m):
    """A fixed 4x hunting scope on a one-inch tube, in picatinny rings."""
    b = Inch(m)
    H = SCOPE_H
    for zc in (-1.55, 1.25):
        b.box((-0.47, 0.0, zc - 0.30), (0.47, 0.30, zc + 0.30), 'anod')
        jaws(b, zc - 0.30, zc + 0.30)
        b.rod(0.47, 0.62, 0.12, 'steel', axis=0, at=(0.15, zc))
        b.box((-0.30, 0.30, zc - 0.25), (0.30, H - 0.48, zc + 0.25), 'anod')
        b.tube(zc - 0.25, zc + 0.25, 0.50, 0.64, 'anod', at=(0.0, H))
        b.box((-0.66, H - 0.10, zc - 0.20), (-0.60, H + 0.10, zc + 0.20), 'steel')
        b.box((0.60, H - 0.10, zc - 0.20), (0.66, H + 0.10, zc + 0.20), 'steel')
    b.rod(-3.40, 2.60, 0.50, 'scope', axis=2, at=(0.0, H))
    b.box((-0.55, H - 0.55, -0.70), (0.55, H + 0.55, 0.40), 'scope')         # turret saddle
    b.rod(H + 0.55, H + 0.98, 0.38, 'knurl', axis=1, at=(0.0, -0.15))
    b.rod(0.55, 0.98, 0.38, 'knurl', axis=0, at=(H, -0.15))
    b.rod(-4.40, -3.40, 0.62, 'scope', axis=2, at=(0.0, H))
    b.rod(-5.60, -4.40, 0.80, 'scope', axis=2, at=(0.0, H))                  # objective bell
    b.box((-0.50, H - 0.50, -5.62), (0.50, H + 0.50, -5.60), 'lens')
    b.rod(2.60, 3.40, 0.62, 'scope', axis=2, at=(0.0, H))
    b.rod(3.40, 4.35, 0.72, 'scope', axis=2, at=(0.0, H))                    # ocular bell
    b.rod(4.35, 4.62, 0.70, 'rubber', axis=2, at=(0.0, H))
    b.box((-0.44, H - 0.44, 4.62), (0.44, H + 0.44, 4.64), 'lens')


# ---------------------------------------------------------------- muzzle, rails, stock

def suppressor(m):
    """A rifle suppressor that clamps over the flash hider: ratchet collar, can, end cap."""
    b = Inch(m)
    b.rod(-0.55, 0.10, 0.80, 'park', axis=2)
    b.rod(-0.45, -0.25, 0.84, 'steel', axis=2)
    b.rod(-6.60, -0.55, 0.75, 'can', axis=2)
    b.rod(-6.80, -6.60, 0.70, 'cap', axis=2)


def foregrip(m):
    """A vertical foregrip."""
    b = Inch(m)
    b.box((-0.48, 0.0, -0.75), (0.48, 0.34, 0.75), 'poly')
    jaws(b, -0.75, 0.75, mat='poly')
    b.rod(0.30, 3.50, 0.56, 'gripper', axis=1, at=(0.0, 0.0))
    b.rod(3.40, 3.68, 0.50, 'poly', axis=1, at=(0.0, 0.0))


def light(m):
    """A scout weapon light (SureFire M600 style) on an offset mount."""
    b = Inch(m)
    b.box((-0.47, 0.0, -0.62), (0.47, 0.34, 0.62), 'anod')
    jaws(b, -0.62, 0.62)
    b.rod(0.47, 0.72, 0.17, 'steel', axis=0, at=(0.17, 0.0))                 # thumbscrew
    b.box((-0.30, 0.34, -0.50), (0.30, 0.62, 0.50), 'anod')
    b.rod(-2.90, 1.90, 0.50, 'anod', axis=2, at=(0.0, 1.05))
    b.rod(-3.60, -2.90, 0.62, 'bezel', axis=2, at=(0.0, 1.05))
    b.box((-0.45, 0.60, -3.62), (0.45, 1.50, -3.60), 'lamp')
    b.rod(1.90, 2.45, 0.47, 'rubber', axis=2, at=(0.0, 1.05))


def laser(m):
    """A visible laser in a tan box (PEQ style)."""
    b = Inch(m)
    b.box((-0.47, 0.0, -0.85), (0.47, 0.30, 0.85), 'anod')
    jaws(b, -0.85, 0.85)
    b.box((-0.64, 0.30, -1.35), (0.64, 1.20, 1.25), 'tan')
    b.box((-0.60, 0.40, -1.42), (0.60, 1.10, -1.35), 'tan')
    b.box((-0.45, 0.55, -1.45), (-0.12, 0.88, -1.42), 'lens_red')
    b.box((0.12, 0.55, -1.45), (0.45, 0.88, -1.42), 'lens')
    b.box((-0.30, 0.50, 1.25), (0.30, 0.95, 1.36), 'rubber')
    for x0 in (-0.42, 0.16):
        b.box((x0, 1.20, -0.60), (x0 + 0.26, 1.27, -0.34), 'steel')
    b.rod(0.64, 0.78, 0.28, 'tan', axis=0, at=(0.75, 0.30))                  # battery cap


# ---------------------------------------------------------------- looks

def look(wear=0.4):
    def anod(f):
        return P.anodised(f, '#25282c')

    def scope(f):
        return P.anodised(f, '#1d1f22', '#7d8288')

    def knurl(f):
        c = P.anodised(f, '#222428')
        lines = (np.sin((f.X + f.Y + f.Z) * 40) > 0.3)
        return np.where(lines[..., None], c * 0.6, c)

    def lens(f):
        t = np.clip((f.Y - f.box['lo'][1]) / max(f.dims[1], 1e-3), 0, 1)
        c = P.mix(P.col('#2b5a6e'), P.col('#11202b'), t)
        glint = (np.abs(f.X - f.Y * 0.6 - 0.1) % 0.6) < 0.05
        return np.where(glint[..., None], P.col('#9fd3e0'), c)

    def lens_red(f):
        return P.plain(f, '#5c1410', 0.2)

    def lamp(f):
        a, b = f.across()
        r = np.sqrt(a ** 2 + b ** 2)
        c = P.mix(P.col('#f4f1e2'), P.col('#a9a89c'), np.clip(r / 0.4, 0, 1))
        return np.where(((r * 12) % 1 < 0.2)[..., None], c * 0.85, c)

    def dot(f):
        return np.ones(f.shape + (3,)) * P.col('#ff2a1a')

    def can(f):
        c = P.parkerised(f, '#34332f')
        heat = f.noise(0.8, 211, 3)
        burn = P.mix(P.col('#4a3a2c'), P.col('#3a3f4c'), f.noise(1.6, 212, 2))
        return P.mix(c, c * 0.5 + burn * 0.5, np.clip((heat - 0.45) * 2.5, 0, 0.7))

    def cap(f):
        c = P.parkerised(f, '#2f2f2c')
        if abs(f.n[2]) > 0.5:
            a, b = f.across()
            return np.where((np.sqrt(a ** 2 + b ** 2) < 0.2)[..., None], c * 0.1, c)
        return c

    def gripper(f):
        c = P.polymer(f, '#232326', 0.14)
        ribs = np.sin(f.Y * 22) > 0.55
        return np.where(ribs[..., None], c * 0.75, c)

    def bezel(f):
        c = P.anodised(f, '#25282c')
        if abs(f.n[2]) > 0.5:
            return c
        crenel = (np.sin(np.arctan2(f.Y - f.box['lo'][1] - f.dims[1] / 2, f.X) * 8) > 0.4) & (f.Z < f.box['lo'][2] + 0.12)
        return np.where(crenel[..., None], c * 0.4, c)

    d = dict(anod=anod, scope=scope, knurl=knurl, lens=lens, lens_red=lens_red, lamp=lamp, dot=dot, can=can,
             cap=cap, gripper=gripper, bezel=bezel, park=P.parkerised, steel=lambda f: P.steel(f, 0.45, spread=0.14),
             poly=lambda f: P.polymer(f, '#26262a'), rubber=lambda f: P.rubber(f, '#121212', 0.1),
             tan=lambda f: P.polymer(f, '#9b8a69', 0.08))
    d['_after'] = [lambda f, c: P.grime(f, c, 0.3 * (0.4 + wear)),
                   lambda f, c: P.scratches(f, c, 0.4 * (0.3 + wear))]
    return d


LOOK = look()

ALL = [
    Att('red_dot', 'red_dot_sight', 'Red Dot Sight', 'optic', red_dot, LOOK, sight=(0.0, CO_WITNESS, 0.0),
        note='Steadier aim'),
    Att('holo', 'holo_sight', 'Holographic Sight', 'optic', holo, LOOK, sight=(0.0, CO_WITNESS, 0.0),
        note='Steadier aim, quick to pick up'),
    Att('scope_4x', 'scope_4x', '4x Scope', 'optic', scope_4x, LOOK, sight=(0.0, SCOPE_H, 4.6),
        note='Four times zoom when aiming'),
    Att('suppressor', 'rifle_suppressor', 'Rifle Suppressor', 'muzzle', suppressor, LOOK,
        note='Much quieter shots'),
    Att('foregrip', 'foregrip', 'Vertical Foregrip', 'underbarrel', foregrip, LOOK, note='Less recoil'),
    Att('light', 'weapon_light', 'Weapon Light', 'side_rail', light, LOOK, note='Lights the way ahead'),
    Att('laser', 'laser_sight', 'Laser Sight', 'side_rail', laser, LOOK, note='Tighter shots from the hip'),
]
BY_KEY = {a.key: a for a in ALL}


def scope_overlay(path, T=1024):
    """What you see looking through the 4x scope: a round eyepiece that darkens at its rim, a duplex
    reticle (heavy posts that thin to fine hairs at the centre), and black all round. The game stretches
    it to the screen's height and fills the sides with the same black."""
    ss = 3
    n = T * ss
    yy, xx = np.mgrid[0:n, 0:n]
    c = (n - 1) / 2
    dx, dy = (xx - c) / n, (yy - c) / n
    r = np.hypot(dx, dy)
    R, inner = 0.46, 0.12               # eyepiece radius, where the posts stop (both as shares of the side)
    heavy, fine = 0.0062, 0.0016        # half-widths of the posts and the hairs
    black = 0.02
    alpha = np.clip((r / R - 0.84) / 0.16, 0, 1) ** 1.6 * 0.88     # the rim shadow
    ax, ay = np.abs(dx), np.abs(dy)
    post = ((ay < heavy) & (ax > inner)) | ((ax < heavy) & (ay > inner))
    hair = ((ay < fine) & (ax <= inner)) | ((ax < fine) & (ay <= inner))
    alpha = np.where(post | hair, 0.97, alpha)
    alpha = np.where(r >= R, 1.0, alpha)
    alpha = alpha.reshape(T, ss, T, ss).mean(axis=(1, 3))
    img = np.zeros((T, T, 4))
    img[..., :3] = black
    img[..., 3] = alpha
    path.parent.mkdir(parents=True, exist_ok=True)
    M.save_png(img, path)
