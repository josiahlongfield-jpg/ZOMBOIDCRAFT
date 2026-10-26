"""Headgear and the night vision that clips onto it, drawn in head pixels.

    python3 tools/weapons/headgear.py [--preview DIR]

The frame is the wearer's head: one unit is one pixel of the 8x8x8 player head, the origin is the middle of the
head, x to the wearer's right, y up, z toward the back (the face is the z = -4 side, the neck the y = -4 side).
Item models put the head's middle at (8, 8, 8) and their 'head' display scales them by 1.6 (vanilla draws head
items at 0.625), so a unit here is one head pixel on whoever wears the item. A real head is about 9 inches tall,
so a pixel is about 1.1 inches: the goggles keep their real proportions at that scale, a touch chunky, like the
rest of the character gear (Josiah, 2026-10-25: cartoonish but believable blocks, a thin outline round each part,
muted colours).

Josiah 2026-10-26: night vision is a headgear attachment. The goggles ride on a mount that clips into the
receiver on the front of a helmet (or of a strap head mount) and flips up out of the way about PIVOT; they run
on AA batteries. The game turns the goggles about PIVOT itself while they flip (client/headgear); the up and
down models here are for everyone else who draws them (armour stands, the inventory).
"""
import json
from pathlib import Path

import numpy as np

import kit
import model as M
import paint as P

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'
CENTRE = np.array([8.0, 8.0, 8.0])     # the head's middle in item model pixels

EYE_Y = -0.25                           # eye height (the player face's eyes are the rows just under the middle)
EYE_X = 2.0                             # each eye's distance from the middle; the right eye is +x
PIVOT = (0.0, 2.3, -5.62)               # the goggles' hinge: the middle of the mount's pivot block
RECEIVER = -5.2                         # the front face of the receiver every headgear carries
DOME = ((1.4, 2.9, 4.9, 0.5, 0.0), (2.9, 3.7, 4.78, 0.65, 0.0), (3.7, 4.3, 4.5, 0.8, 0.05),
        (4.3, 4.8, 4.05, 0.95, 0.1), (4.8, 5.15, 3.35, 1.0, 0.15), (5.15, 5.35, 2.3, 0.9, 0.2))
UP = 90                                 # degrees the goggles turn about +x to flip up (they end up pointing at the sky)


def rod_z(m, z0, z1, r, mat, x, y, **kw):
    return m.rod(z0, z1, r, mat, cx=x, cz=y, axis=2, **kw)


def rod_x(m, x0, x1, r, mat, y, z, **kw):
    return m.rod(x0, x1, r, mat, cx=y, cz=z, axis=0, **kw)


def rod_y(m, y0, y1, r, mat, x, z, **kw):
    return m.rod(y0, y1, r, mat, cx=x, cz=z, axis=1, **kw)


def strap(m, p0, p1, x, w=0.5, t=0.26, mat='web'):
    """A strap down the side of the head from p0 to p1, each (z, y), centred x across."""
    return m.seg(p0, p1, w, t, mat, z=x, plane='zy', over=0.06)


def both(fn):
    """Draws fn(side) on the right (+1) and the left (-1)."""
    for s in (1, -1):
        fn(s)


def receiver(m, plate):
    """The shroud that takes the goggle mount: a plate on the front, the raised receiver with its slot, and the
    screws holding the plate on. plate: (x half-width, y0, y1, z of its face)."""
    hw, y0, y1, z = plate
    m.box((-hw, y0, z), (hw, y1, z + 0.4), 'shroud')
    m.box((-0.7, 1.75, RECEIVER), (0.7, 2.9, z), 'shroud')
    m.box((-0.42, 1.98, RECEIVER - 0.02), (0.42, 2.66, RECEIVER), 'bore')
    for x in (-hw + 0.12, hw - 0.32):
        m.box((x, y1 - 0.34, z - 0.03), (x + 0.2, y1 - 0.14, z), 'screw')
    m.box((-0.1, y0 + 0.12, z - 0.03), (0.1, y0 + 0.32, z), 'screw')


def harness(m, front_top, rear_top, x):
    """A four-point chin harness on one side: a front and a rear strap meeting in a buckle under the ear, one strap
    on to the chin cup. The tops are (z, y) where each strap leaves the headgear."""
    s = 1 if x > 0 else -1
    knot = (0.0, -2.55)
    strap(m, front_top, knot, x)
    strap(m, rear_top, knot, x)
    strap(m, knot, (-3.15, -3.9), x)
    m.box((x - 0.2 * s - 0.0, -2.95, -0.36), (x + 0.2 * s, -2.25, 0.36), 'buckle')


def chin_cup(m):
    m.box((-1.6, -4.4, -4.38), (1.6, -3.55, -3.05), 'cup')
    m.box((-4.5, -4.3, -3.55), (4.5, -4.02, -3.1), 'web')          # strap under the jaw, cup to each side
    m.box((-0.5, -4.46, -4.2), (0.5, -4.4, -3.3), 'buckle')         # the cup's quick-release


# ---------------------------------------------------------------- headgear

def helmet(m):
    """A high-cut ballistic helmet, the kind worn with night vision: a rounded shell cut high over the ears, rubber
    edge trim, the shroud on the front, rails down each side, loop patches, a counterweight pouch on the back
    (it balances the goggles) and a four-point chin harness."""
    S = 'shell'
    # the dome in layers stepping in toward the crown, each a plus of two boxes so the corners read rounded
    # (bottom, top, half-width, corner cut); the crown sits a touch back of the middle, like a real shell
    for y0, y1, w, cut, back in DOME:
        m.box((-w, y0, -w + cut), (w, y1, w - cut + back), S)
        m.box((-w + cut, y0, -w), (w - cut, y1, w + back), S)
    # the back comes down over the nape, and round the back corners to where it's cut high over the ears
    m.box((-3.9, -0.6, 4.0), (3.9, 1.4, 4.9), S)
    both(lambda s: m.box((s * 4.0, 0.4, 2.6), (s * 4.9, 1.4, 4.4), S))
    # rubber edge trim round the bottom
    T = 'trim'
    m.box((-4.96, 1.36, -4.46), (4.96, 1.62, 2.6), T)
    m.box((-4.46, 1.36, -4.96), (4.46, 1.62, -4.0), T)
    m.box((-3.96, -0.66, 4.3), (3.96, -0.4, 4.96), T)
    both(lambda s: m.box((s * 3.96, 0.34, 2.6), (s * 4.96, 0.6, 4.46), T))
    both(lambda s: m.box((s * 3.9, -0.66, 4.3), (s * 4.0, 0.6, 4.96), T))
    receiver(m, (1.25, 1.55, 3.5, -5.14))
    # rails down each side, and the loop patches over them
    for s in (1, -1):
        x0, x1 = sorted((s * 4.9, s * 5.18))
        m.box((x0, 1.9, -3.8), (x1, 2.75, 3.5), 'rail')
        for z in (-3.2, -0.15, 2.9):
            m.box((x0 - 0.03, 2.2, z), (x1 + 0.03, 2.45, z + 0.25), 'screw')
        x0, x1 = sorted((s * 4.78, s * 4.85))
        m.box((x0, 3.0, -2.5), (x1, 3.55, 1.8), 'loop')
    m.box((-1.3, 5.35, -1.9), (1.3, 5.42, 2.1), 'loop')
    # counterweight pouch on the back
    m.box((-1.7, 1.75, 4.8), (1.7, 3.5, 5.62), 'pouch')
    m.box((-1.76, 3.0, 4.8), (1.76, 3.58, 5.7), 'pouch_flap')
    m.box((-0.28, 2.3, 5.62), (0.28, 3.05, 5.74), 'buckle')
    # chin harness: the front strap leaves the shell in front of the ear, the rear one from the back corner
    for s in (1, -1):
        harness(m, (-1.5, 1.45), (3.2, 0.45), s * 4.43)
    chin_cup(m)


def head_mount(m):
    """A strap head mount for goggles without a helmet: a padded band round the head, straps over the crown,
    a thick front pad carrying the receiver, a cradle with a ratchet dial at the back, and a chin harness."""
    B = 'band'
    m.box((-4.45, 0.9, -4.45), (4.45, 1.85, 4.45), B)
    m.box((-0.55, 4.0, -4.45), (0.55, 4.3, 4.45), B)             # over the crown, front to back
    m.box((-4.45, 4.0, -0.5), (4.45, 4.3, 0.5), B)               # and side to side
    m.box((-0.55, 1.85, -4.45), (0.55, 4.0, -4.15), B)
    m.box((-0.55, 1.85, 4.15), (0.55, 4.0, 4.45), B)
    both(lambda s: m.box((s * 4.15, 1.85, -0.5), (s * 4.45, 4.0, 0.5), B))
    m.box((-0.35, 4.3, -1.2), (0.35, 4.42, 1.2), 'buckle')        # the crown strap's slider
    m.box((-1.5, 0.95, -4.94), (1.5, 3.3, -4.45), 'pad')
    receiver(m, (1.05, 1.5, 3.15, -5.14))
    m.box((-2.1, -1.6, 4.45), (2.1, 1.9, 4.8), 'pad')             # rear cradle
    rod_z(m, 4.8, 5.15, 0.75, 'dial', 0.0, 0.15)                  # ratchet dial
    m.box((-0.12, 0.7, 5.15), (0.12, 0.9, 5.2), 'screw')
    for s in (1, -1):
        harness(m, (-0.9, 0.95), (2.6, 0.95), s * 4.58)
    chin_cup(m)


# ---------------------------------------------------------------- night vision

def mount(m, arm_bottom):
    """The flip mount: a square pivot block against the receiver (square about PIVOT, so it turns in place), the
    pivot pin with a knob each side, and the arm down to the goggles."""
    px, py, pz = PIVOT
    m.box((-0.95, py - 0.45, pz - 0.45), (0.95, py + 0.45, pz + 0.45), 'mount')
    rod_x(m, -1.3, 1.3, 0.3, 'knob', py, pz)
    m.box((-0.5, arm_bottom, -6.0), (0.5, py - 0.45, -5.55), 'mount')
    m.box((-0.12, py - 0.2, pz - 0.48), (0.12, py + 0.2, pz - 0.45), 'screw')


def goggles_gen1(m):
    """Gen 1 night vision: a cheap hunter's monocular in olive rubber armour on a J-arm over the right eye. Bulky,
    with an infrared lamp on the inside of the lens (Gen 1 tubes need the help) and the battery cap on the right."""
    x, y = EYE_X, EYE_Y
    mount(m, 0.75)
    m.box((-0.5, 0.55, -6.0), (2.55, 1.15, -5.55), 'mount')                   # the J-arm's reach to the right eye
    m.box((1.2, 0.72, -6.25), (2.8, 1.0, -5.3), 'mount')                      # clamp over the tube
    rod_z(m, -7.7, -4.95, 1.02, 'housing', x, y)
    for z in (-7.3, -6.55, -5.8):                                              # the armour's grip ribs
        rod_z(m, z, z + 0.3, 1.08, 'housing', x, y)
    rod_z(m, -8.55, -7.7, 1.08, 'focus', x, y)                                # big focus ring
    m.box((x - 0.78, y - 0.78, -8.58), (x + 0.78, y + 0.78, -8.55), 'lens_nv')
    rod_z(m, -4.95, -4.55, 0.86, 'focus', x, y)                               # eyepiece diopter
    rod_z(m, -4.55, -4.1, 0.92, 'rubber', x, y)                               # eyecup
    rod_x(m, x + 0.95, x + 1.35, 0.4, 'cap', y + 0.15, -6.3)                  # battery cap, right side
    m.box((x + 1.35, y + 0.05, -6.4), (x + 1.4, y + 0.25, -6.2), 'screw')
    m.box((0.45, y - 0.55, -8.15), (1.05, y + 0.2, -6.85), 'housing')         # the infrared lamp, inside the lens
    m.box((0.53, y - 0.47, -8.18), (0.97, y + 0.12, -8.15), 'ir')
    rod_y(m, y + 1.0, y + 1.3, 0.26, 'knob', x, -6.95)                        # power and lamp switch


def goggles_binocular(m):
    """Gen 3 binocular goggles: two tubes on an articulating bridge, the mount on top, the AA battery in a tube
    across the bridge's front with its cap on the right, the power knob on top, a knurled focus ring on each
    lens and a diopter ring and rubber eyecup on each eyepiece."""
    y = EYE_Y
    mount(m, 0.8)
    m.box((-0.62, 0.8, -6.08), (0.62, 1.0, -5.47), 'mount')                   # the arm's clamp on the bridge
    m.box((-1.25, y + 0.1, -6.3), (1.25, 0.8, -4.95), 'housing')              # bridge
    m.box((-0.9, y - 0.35, -6.0), (0.9, y + 0.1, -5.2), 'housing')            # its hinge block, under
    rod_x(m, -1.15, 1.15, 0.3, 'housing', 0.35, -6.55)                        # battery tube across the front
    rod_x(m, 1.15, 1.42, 0.34, 'cap', 0.35, -6.55)                            # and its cap
    m.box((1.42, 0.27, -6.63), (1.45, 0.43, -6.47), 'screw')
    rod_y(m, 0.8, 1.05, 0.24, 'knob', -0.85, -5.35)                           # power knob
    m.box((-0.88, 1.05, -5.4), (-0.82, 1.06, -5.15), 'mark')                  # its pointer
    for s in (1, -1):
        x = s * EYE_X
        rod_z(m, -7.55, -4.95, 0.78, 'housing', x, y)
        rod_z(m, -8.3, -7.55, 0.86, 'focus', x, y)
        m.box((x - 0.62, y - 0.62, -8.33), (x + 0.62, y + 0.62, -8.3), 'lens_nv')
        rod_z(m, -4.95, -4.6, 0.72, 'focus', x, y)
        rod_z(m, -4.6, -4.12, 0.77, 'rubber', x, y)
        m.box((x - 0.06, y + 0.78, -7.2), (x + 0.06, y + 0.8, -5.4), 'mark')  # the tube's markings line
    m.box((-0.5, y + 0.12, -6.33), (0.5, y + 0.4, -6.3), 'mark')              # the bridge's data plate


# ---------------------------------------------------------------- comms headset

CUP = (-0.6, 0.45)                      # (y, z) of the ear cups' middle: over the ear, just behind the head's middle


def side(s, a, b):
    """The x range from a to b out on side s (+1 right, -1 left), low end first."""
    return sorted((s * a, s * b))


def rail_arm(m, s):
    """The adapter that clamps onto the helmet's side rail, its locking knob, and the arm the cup hangs from (the
    cup swings up on its hinge when it's not wanted, like a real one)."""
    x0, x1 = side(s, 5.18, 5.58)
    m.box((x0, 1.72, 0.15), (x1, 2.93, 1.15), 'clamp')
    for y0, y1 in ((2.75, 2.93), (1.72, 1.9)):                       # lips round the rail's top and bottom edges
        x0, x1 = side(s, 4.96, 5.18)
        m.box((x0, y0, 0.15), (x1, y1, 1.15), 'clamp')
    rod_x(m, *side(s, 5.58, 5.9), 0.34, 'knob', 2.32, 0.65)
    x0, x1 = side(s, 5.9, 5.94)
    m.box((x0, 2.22, 0.55), (x1, 2.42, 0.75), 'screw')
    rod_z(m, 0.25, 1.05, 0.24, 'clamp', s * 5.38, 1.6)               # the hinge
    x0, x1 = side(s, 5.24, 5.52)
    m.box((x0, 0.55, 0.38), (x1, 1.62, 0.92), 'clamp')


def ear_cup(m, s):
    """One ear cup: a soft cushion against the head, the shell, a stepped outer face with the grille over the
    microphone that lets quiet sounds through, and the cable down to the radio's connector on the left."""
    y, z = CUP
    x0, x1 = side(s, 4.45, 4.98)
    m.box((x0, y - 1.45, z - 1.15), (x1, y + 1.45, z + 1.15), 'cushion')
    m.box((x0, y - 1.15, z - 1.45), (x1, y + 1.15, z + 1.45), 'cushion')
    x0, x1 = side(s, 4.95, 6.1)
    m.box((x0, y - 1.55, z - 1.0), (x1, y + 1.55, z + 1.0), 'cup')
    m.box((x0, y - 1.25, z - 1.3), (x1, y + 1.25, z + 1.3), 'cup')
    x0, x1 = side(s, 6.1, 6.42)
    m.box((x0, y - 1.22, z - 0.78), (x1, y + 1.22, z + 0.78), 'cup')
    m.box((x0, y - 0.95, z - 1.05), (x1, y + 0.95, z + 1.05), 'cup')
    x0, x1 = side(s, 6.42, 6.45)
    m.box((x0, y + 0.08, z - 0.6), (x1, y + 0.8, z + 0.6), 'grille')
    m.box((x0, y - 0.95, z - 0.12), (x1, y - 0.5, z + 0.12), 'mark')  # the cup's data plate


def comms_headset(m):
    """A rail-mount comms headset: two hearing-protection ear cups that hang from adapters on the helmet's side
    rails, a boom microphone off the left cup, the battery door on the right and the buttons on the left. Through
    it you hear your radio and nobody else does; it lets quiet sounds through and dulls gunshots."""
    for s in (1, -1):
        rail_arm(m, s)
        ear_cup(m, s)
    y, z = CUP
    # right cup: the AA battery door with its latch
    m.box((6.42, y - 1.05, z - 0.62), (6.52, y - 0.2, z + 0.62), 'cup')
    m.box((6.52, y - 0.75, z - 0.2), (6.56, y - 0.5, z + 0.2), 'screw')
    # left cup: volume up, down and the mode button
    for dz in (-0.48, 0.0, 0.48):
        m.box((-6.56, y - 1.0, z + dz - 0.17), (-6.42, y - 0.6, z + dz + 0.17), 'button')
    # boom microphone: a swivel on the front of the left cup, the boom forward past the cheek, round in front of
    # the face to the mouth's corner, and the foam windscreen
    rod_x(m, -6.1, -5.0, 0.42, 'cup', y - 1.0, z - 1.32)
    m.seg((-1.2, -1.6), (-4.0, -2.6), 0.3, 0.3, 'boom', z=-5.5, plane='zy')
    m.box((-5.68, -2.88, -4.52), (-5.32, -2.52, -3.95), 'boom')
    m.box((-5.5, -2.83, -4.43), (-2.1, -2.57, -4.17), 'boom')
    rod_x(m, -2.2, -0.85, 0.44, 'foam', -2.7, -4.3)
    # the cable down from the left cup and its plug
    rod_y(m, -3.0, y - 1.5, 0.17, 'boom', -5.5, z + 0.55)
    m.box((-5.76, -3.42, z + 0.3), (-5.24, -2.95, z + 0.8), 'cup')


def battery(m):
    """An AA cell: the wrapper, a band of colour round the top, the button and the flat base. Drawn big (the
    item display shrinks it)."""
    rod_y(m, 0.3, 6.2, 0.95, 'wrap', 0.0, 0.0)
    rod_y(m, 4.5, 6.2, 0.97, 'band', 0.0, 0.0)
    rod_y(m, 6.2, 6.35, 0.8, 'steel', 0.0, 0.0)
    rod_y(m, 6.35, 6.7, 0.32, 'steel', 0.0, 0.0)
    rod_y(m, 0.0, 0.3, 0.85, 'steel', 0.0, 0.0)


# ---------------------------------------------------------------- looks

def weave(f, c, k=9.0, depth=0.12):
    """Woven nylon: a fine basket weave across the face."""
    u, v = f.uv[..., 0] * k, f.uv[..., 1] * k
    w = ((np.floor(u) + np.floor(v)) % 2 == 0)
    ribs = np.where(w, np.sin(u * np.pi * 2), np.sin(v * np.pi * 2)) * 0.5 + 0.5
    return c * (1 - depth + depth * 2 * ribs)[..., None]


def stitched(f, c, inset=0.12, colour=None):
    """A line of stitches just inside the edge of a fabric face."""
    line = (np.abs(f.edge - inset) < 0.025) & ((f.uv[..., 0] + f.uv[..., 1]) * 10 % 1 < 0.6)
    return np.where(line[..., None], c * 0.62 if colour is None else P.col(colour), c)


def gear_look(shell='#665a4a', housing=None, lens=('#3b2a52', '#1b3a2c')):
    """Colours for headgear and goggles: muted like the character gear (never bright)."""
    def paintwork(f):
        c = P.polymer(f, shell, 0.05)
        return P.mix(c, c * 0.86, np.clip((f.noise(1.1, 401, 3) - 0.5) * 2, 0, 1) * 0.4)

    def loop(f):
        c = P.plain(f, '#5a5243', 0.08)
        return c * (1 + (P.white(f.shape, f.rng) - 0.5) * 0.28)[..., None]

    def rail(f):
        c = P.polymer(f, '#1d1d1f', 0.06)
        slots = (f.Z * 1.3) % 1 < 0.35
        return np.where(slots[..., None], c * 0.55, c)

    def web(f):
        return stitched(f, weave(f, P.plain(f, '#34322a', 0.06)), 0.07)

    def band(f):
        return weave(f, P.plain(f, '#232321', 0.05), 7, 0.18)

    def pouch(f):
        return stitched(f, weave(f, P.plain(f, '#575040', 0.06), 12, 0.08))

    def lens_nv(f):
        # coated glass: violet at the top fading to green, a soft highlight across
        t = np.clip((f.Y - f.box['lo'][1]) / max(f.dims[1], 1e-3), 0, 1)
        c = P.mix(P.col(lens[1]), P.col(lens[0]), t)
        a, b = f.uv[..., 0], f.uv[..., 1]
        glint = np.abs(a - b - f.uv[..., 0].max() * 0.25) < 0.08
        return np.where(glint[..., None], P.mix(c, P.col('#b9c7d6'), 0.5), c)

    def housing_fn(f):
        if housing is None:
            return P.anodised(f, '#1c1d1f', '#5a5f64')
        return P.polymer(f, housing, 0.08)

    def knurl(f):
        c = housing_fn(f)
        lines = np.sin((f.X + f.Y + f.Z) * 26) > 0.35
        return np.where(lines[..., None], c * 0.62, c)

    def mark(f):
        c = housing_fn(f)
        a, b = f.uv[..., 0], f.uv[..., 1]
        rows = (b * 30) % 1 < 0.45
        chars = np.sin(a * 140 + np.floor(b * 30) * 2.1) + np.sin(a * 57 + np.floor(b * 30)) > 0.6
        return np.where((rows & chars)[..., None], P.mix(c, P.col('#8d9186'), 0.55), c)

    def screw(f):
        return P.steel(f, 0.3, tint='#6f7378', spread=0.08)

    d = dict(shell=paintwork, trim=lambda f: P.rubber(f, '#1a1918', 0.2), loop=loop, rail=rail, web=web, band=band,
             pouch=pouch, pouch_flap=pouch, buckle=lambda f: P.polymer(f, '#161616', 0.06),
             cup=lambda f: P.polymer(f, '#1b1b1a', 0.1), pad=lambda f: P.plain(f, '#1e1e1d', 0.1),
             dial=lambda f: P.rubber(f, '#181818', 0.12), shroud=lambda f: P.anodised(f, '#1b1c1e', '#5d6166'),
             bore=lambda f: P.plain(f, '#0d0d0e', 0.04), screw=screw,
             mount=lambda f: P.anodised(f, '#1c1d1f', '#5a5f64'), knob=knurl, housing=housing_fn, focus=knurl,
             mark=mark, cap=knurl, rubber=lambda f: P.rubber(f, '#151514', 0.14), lens_nv=lens_nv,
             ir=lambda f: P.plain(f, '#3a1416', 0.1), wrap=lambda f: P.plain(f, '#26262a', 0.05),
             steel=lambda f: P.steel(f, 0.55, tint='#9a9da1', spread=0.1))
    d['_after'] = [lambda f, c: P.grime(f, c, 0.12)]
    return d


HELMET_LOOK = gear_look()
GEN1_LOOK = gear_look(housing='#3e4232', lens=('#3a3247', '#203826'))
GEN3_LOOK = gear_look()
WHITE_LOOK = gear_look(housing='#5e594d', lens=('#3e3a5c', '#24384a'))
BATTERY_LOOK = dict(gear_look(), band=lambda f: P.plain(f, '#7a5a34', 0.06))


def grille(f, c, k=7.0):
    """A punched grille: rows of round holes."""
    u, v = (f.uv[..., 0] * k) % 1 - 0.5, (f.uv[..., 1] * k) % 1 - 0.5
    return np.where((u * u + v * v < 0.09)[..., None], c * 0.25, c)


def headset_look(shell='#33362d'):
    def cup(f):
        c = P.polymer(f, shell, 0.06)
        return P.mix(c, c * 0.85, np.clip((f.noise(1.3, 411, 3) - 0.5) * 2, 0, 1) * 0.35)

    def cushion(f):
        # leatherette over foam: a soft sheen toward the middle and creases round the edge
        c = P.plain(f, '#1d1d1c', 0.05)
        c = c * (1 + np.clip(f.edge / 0.6, 0, 1) * 0.22)[..., None]
        crease = np.sin(f.uv[..., 0] * 9 + np.sin(f.uv[..., 1] * 3) * 2) > 0.85
        return np.where(crease[..., None], c * 0.72, c)

    def foam(f):
        c = P.plain(f, '#1b1c1a', 0.1)
        return c * (1 + (P.white(f.shape, f.rng) - 0.5) * 0.55)[..., None]

    return dict(gear_look(), cup=cup, cushion=cushion, clamp=lambda f: P.anodised(f, '#1d1e1f', '#55595e'),
                grille=lambda f: grille(f, P.polymer(f, '#26281f', 0.05)), button=lambda f: P.rubber(f, '#191918', 0.1),
                boom=lambda f: P.rubber(f, '#141414', 0.12), foam=foam)


HEADSET_LOOK = headset_look()


class Gear:
    def __init__(self, key, title, build, look, seed, goggles=False, rails=False):
        self.key, self.title, self.build, self.look, self.seed = key, title, build, look, seed
        self.goggles = goggles
        self.rails = rails          # side rails that take a headset


HEADGEAR = [
    Gear('combat_helmet', 'High-Cut Combat Helmet', helmet, HELMET_LOOK, 931, rails=True),
    Gear('nvg_head_mount', 'Night Vision Head Mount', head_mount, HELMET_LOOK, 932),
]
GOGGLES = [
    Gear('nvg_gen1', 'Gen 1 Night Vision Monocular', goggles_gen1, GEN1_LOOK, 941, True),
    Gear('nvg_gen3', 'Gen 3 Night Vision Goggles', goggles_binocular, GEN3_LOOK, 942, True),
    Gear('nvg_white', 'White Phosphor Night Vision Goggles', goggles_binocular, WHITE_LOOK, 943, True),
]
HEADSETS = [
    Gear('comms_headset', 'Rail-Mount Comms Headset', comms_headset, HEADSET_LOOK, 961),
]


# ---------------------------------------------------------------- writing

def flip(angle):
    """R and t that turn the goggles about PIVOT by angle (degrees about +x) and put the head's middle at CENTRE."""
    R = np.round(M.rot_matrix('x', angle), 9)
    p = np.array(PIVOT)
    return R, p + CENTRE - R @ p


def drawn(gear, angle=0):
    R, t = flip(angle) if angle else (np.eye(3), CENTRE)
    return kit.drawn(gear.key, gear.build, R, t, look=gear.look, seed=gear.seed, wear=kit.LIKE_NEW)


VANILLA = {  # handheld.json, as weapons.py uses it
    'firstperson_righthand': ([0, -90, 25], [1.13, 3.2, 1.13]),
    'thirdperson_righthand': ([0, -90, 55], [0, 4.0, 0.5]),
}


def display_for(m, gui_rot=(20, 145, 0), gui=13.5, hand=8.0):
    """Display transforms that fit m: the inventory turns it to show its front three-quarters. 'head' and 'none'
    are left to headgear_display."""
    d = {}
    s, t = M.fit(m, M.R_xyz(*gui_rot), gui)
    d['gui'] = M.transform(gui_rot, t, s)
    for ctx, (rot, trans) in VANILLA.items():
        s2, t2 = M.fit(m, M.R_xyz(*rot), hand)
        d[ctx] = M.transform(rot, np.array(trans) + t2, s2)
    s3, t3 = M.fit(m, np.eye(3), 7.0, (0, 2, 0))
    d['ground'] = M.transform([0, 0, 0], t3, s3)
    s4, t4 = M.fit(m, M.R_xyz(0, 180, 0), 12)
    d['fixed'] = M.transform([0, 180, 0], t4, s4)
    return d


def headgear_display(fit):
    """One display for every headgear model and the goggles drawn on it, so they line up in every view: on the head
    a unit is a head pixel, 'none' (the game's own head layer) is untouched, the rest fit the helmet with its goggles
    flipped up, the biggest of them."""
    d = display_for(fit)
    d['head'] = M.transform([0, 0, 0], [0, 0, 0], 1.6)
    d['none'] = M.transform([0, 0, 0], [0, 0, 0], 1.0)
    return d


def ref(path):
    return {'type': 'minecraft:model', 'model': f'{NS}:item/headgear/{path}'}


def write_models(models, path, display, extra=None):
    """Paints models into one texture and writes each as item/headgear/<name>, all sharing it."""
    patches = kit.paint([m for _n, m in models])
    img, uvs, T = kit.pack(patches)
    tex = f'item/headgear/{path}'
    kit.save_textures(img, ASSETS / f'textures/{tex}.png')
    for mi, (name, m) in enumerate(models):
        M.dump({'gui_light': 'front', 'textures': {'0': f'{NS}:{tex}', 'particle': f'{NS}:{tex}'},
                'elements': kit.elements(m, mi, uvs, np.zeros(3)), 'display': display},
               ASSETS / f'models/item/headgear/{name}.json')
    return T


def headgear_item(gear):
    """The headgear's item model: on a player's head nothing (the game's head layer draws it, so the goggles can
    flip smoothly), in the game's head layer the helmet alone, everywhere else the helmet with whatever goggles
    are on it, up or down as the model data says ([0] the goggles' key, [1] 'up' or 'down'), and on a helmet with
    rails the headset ([2] its key)."""
    def with_goggles():
        state = {'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
                 'cases': [{'when': g.key, 'model': {
                     'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 1,
                     'cases': [{'when': 'up', 'model': ref(g.key + '_up')}], 'fallback': ref(g.key + '_down')}}
                     for g in GOGGLES],
                 'fallback': kit.EMPTY}
        models = [ref(gear.key), state]
        if gear.rails:
            models.append({'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 2,
                           'cases': [{'when': h.key, 'model': ref(h.key + '_worn')} for h in HEADSETS],
                           'fallback': kit.EMPTY})
        return {'type': 'minecraft:composite', 'models': models}

    return {'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context', 'cases': [
        {'when': 'head', 'model': {'type': 'minecraft:select', 'property': 'minecraft:context_entity_type',
                                   'cases': [{'when': 'minecraft:player', 'model': kit.EMPTY}],
                                   'fallback': with_goggles()}},
        {'when': 'none', 'model': ref(gear.key)}],
        'fallback': with_goggles()}}


def main():
    lang = {}
    helmet_m = drawn(HEADGEAR[0])
    up_m = drawn(GOGGLES[1], UP)
    fit = M.Model('fit')
    fit.parts = helmet_m.parts + up_m.parts + drawn(HEADSETS[0]).parts
    hd = headgear_display(fit)
    summary = []
    for gear in HEADGEAR:
        T = write_models([(gear.key, drawn(gear))], gear.key, hd)
        M.dump(headgear_item(gear), ASSETS / f'items/{gear.key}.json')
        lang[f'item.{NS}.{gear.key}'] = gear.title
        summary.append(f'{gear.key}: {len(drawn(gear).parts)} boxes, {T}px')
    for gear in GOGGLES:
        down, up = drawn(gear), drawn(gear, UP)
        T = write_models([(gear.key + '_down', down), (gear.key + '_up', up)], gear.key, hd)
        loose = display_for(down, gui_rot=(15, 150, 0))
        M.dump({'parent': f'{NS}:item/headgear/{gear.key}_down', 'display': loose},
               ASSETS / f'models/item/headgear/{gear.key}.json')
        M.dump({'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                          'cases': [{'when': 'none', 'model': ref(gear.key + '_down')}], 'fallback': ref(gear.key)}},
               ASSETS / f'items/{gear.key}.json')
        lang[f'item.{NS}.{gear.key}'] = gear.title
        summary.append(f'{gear.key}: {len(down.parts)} boxes, {T}px')
    for gear in HEADSETS:
        worn = drawn(gear)
        T = write_models([(gear.key + '_worn', worn)], gear.key, hd)
        M.dump({'parent': f'{NS}:item/headgear/{gear.key}_worn', 'display': display_for(worn, gui_rot=(20, -125, 0))},
               ASSETS / f'models/item/headgear/{gear.key}.json')
        M.dump({'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                          'cases': [{'when': 'none', 'model': ref(gear.key + '_worn')}], 'fallback': ref(gear.key)}},
               ASSETS / f'items/{gear.key}.json')
        lang[f'item.{NS}.{gear.key}'] = gear.title
        summary.append(f'{gear.key}: {len(worn.parts)} boxes, {T}px')
    cell = kit.drawn('aa_battery', battery, t=CENTRE - (0, 3.35, 0), look=BATTERY_LOOK, seed=951)
    write_models([('aa_battery', cell)], 'aa_battery', display_for(cell, gui_rot=(25, -35, 20), gui=12))
    M.dump({'model': ref('aa_battery')}, ASSETS / 'items/aa_battery.json')
    lang[f'item.{NS}.aa_battery'] = 'AA Battery'
    path = ASSETS / 'lang/en_us.json'
    data = json.loads(path.read_text())
    data.update(lang)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')
    print('\n'.join(summary))


if __name__ == '__main__':
    main()
