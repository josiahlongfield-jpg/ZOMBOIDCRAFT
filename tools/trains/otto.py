#!/usr/bin/env python3
"""
OTTO, the robot conductor: his model and his textures.

He stands bolted to the cab floor at the controls, body to the windscreen and claws on the
throttle, with his head wrenched round backwards to stare down the train at the riders through the
cab window. A gaunt, rusted frame: an open back with its spine and cables showing and a red core
glowing between the ribs, a bone-pale skull plate with deep sockets and red eyes, a grille of a jaw
that drops when he talks, and a battered conductor's cap.

    python3 tools/trains/otto.py            # paints him into the train atlas, writes OttoModel.java,
                                            # and renders previews to --preview DIR (default: scratch)

The model is boxes, each face mapped to a painted region of the atlas's lower half (256 x 256 px at
(0, 256)). OttoModel.java holds the quads in his own frame: x across, y up from the cab floor, z
forward (to the windscreen); his head faces -z, back down the train. tools/trains/paint.py calls
paint_into() so repainting the atlas keeps him.
"""
import argparse
import math
import os
import random
import sys
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ATLAS = ROOT / 'src/main/resources/assets/zomboidcraft/textures/entity/train/atlas.png'
JAVA = ROOT / 'src/client/java/com/zomboidcraft/client/trains/OttoModel.java'
ATLAS_SIZE = 512
OX, OY = 0, 256          # where his 256 x 256 block sits in the atlas

# parts and lighting, matching TrainMesh
STATIC, HEAD, JAW, EYES = 0, 1, 3, 4
INSIDE, GLOW = 1, 2

# ---------------------------------------------------------------- painting

REGIONS = {}   # name -> (x, y, w, h) inside his 256 block


def clamp(v):
    return max(0, min(255, int(v)))


class Painter:
    def __init__(self):
        self.img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
        self.rng = random.Random(77)
        self.cursor = [0, 0, 0]   # x, y, row height

    def region(self, name, w, h):
        x, y, rh = self.cursor
        if x + w > 256:
            x, y, rh = 0, y + rh, 0
        assert y + h <= 256, 'OTTO texture full'
        self.cursor = [x + w, y, max(rh, h)]
        REGIONS[name] = (x, y, w, h)
        sub = Image.new('RGBA', (w, h), (0, 0, 0, 255))
        return sub, (x, y)

    def put(self, sub, at):
        self.img.paste(sub, at)


def noise(img, base, amount, rnd, streak=0.0):
    px = img.load()
    w, h = img.size
    cols = [rnd.uniform(-1, 1) for _ in range(w)]
    for y in range(h):
        for x in range(w):
            n = rnd.uniform(-1, 1) * amount + cols[x] * amount * streak
            px[x, y] = tuple(clamp(c + n) for c in base) + (255,)


def rust(img, rnd, spots, streaks=True):
    d = ImageDraw.Draw(img)
    w, h = img.size
    for _ in range(spots):
        x, y = rnd.randrange(w), rnd.randrange(h)
        r = rnd.randrange(1, max(2, w // 10))
        col = (rnd.randrange(90, 140), rnd.randrange(40, 64), rnd.randrange(18, 30), 255)
        d.ellipse([x - r, y - r, x + r, y + r], fill=col)
        if streaks:
            ln = rnd.randrange(3, max(4, h // 2))
            d.line([(x, y), (x + rnd.choice((-1, 0, 0, 1)), y + ln)], fill=(col[0] - 20, col[1] - 10, col[2], 200))


def scratches(img, rnd, count, col):
    d = ImageDraw.Draw(img)
    w, h = img.size
    for _ in range(count):
        x, y = rnd.randrange(w), rnd.randrange(h)
        a = rnd.uniform(-0.6, 0.6)
        ln = rnd.randrange(3, max(4, w // 4))
        d.line([(x, y), (x + math.cos(a) * ln, y + math.sin(a) * ln)], fill=col)


def rivets(img, every, inset=2):
    d = ImageDraw.Draw(img)
    w, h = img.size
    for x in range(inset, w - inset, every):
        for y in (inset, h - inset - 2):
            d.rectangle([x, y, x + 1, y + 1], fill=(150, 150, 140, 255))
            d.point((x + 1, y + 1), fill=(20, 20, 20, 255))


def blood(img, rnd, amount):
    d = ImageDraw.Draw(img)
    w, h = img.size
    for _ in range(amount):
        x, y = rnd.randrange(w), rnd.randrange(h // 2)
        r = rnd.randrange(1, 4)
        col = (rnd.randrange(70, 110), 8, 8, 255)
        d.ellipse([x - r, y - r, x + r, y + r], fill=col)
        d.line([(x, y), (x, y + rnd.randrange(3, h // 2 + 4))], fill=(80, 6, 6, 230), width=1)


def edge_wear(img, rnd, col=(150, 148, 138)):
    px = img.load()
    w, h = img.size
    for x in range(w):
        for y in (0, h - 1):
            if rnd.random() < 0.45:
                px[x, y] = col + (255,)
    for y in range(h):
        for x in (0, w - 1):
            if rnd.random() < 0.45:
                px[x, y] = col + (255,)


def paint():
    p = Painter()
    r = p.rng
    GUN = (58, 62, 60)

    # gunmetal plating, rusted and scratched (most of his shell)
    for k in range(2):
        img, at = p.region(f'metal{k}', 64, 64)
        noise(img, GUN, 9, r, streak=0.5)
        rust(img, r, 26)
        scratches(img, r, 30, (110, 112, 104, 255))
        d = ImageDraw.Draw(img)
        d.line([(0, 31), (63, 31)], fill=(26, 28, 27, 255))          # a panel seam
        d.line([(31, 0), (31, 63)], fill=(26, 28, 27, 255))
        rivets(img, 8)
        edge_wear(img, r)
        p.put(img, at)
    # heavy rust
    img, at = p.region('rust', 32, 32)
    noise(img, (104, 56, 26), 22, r)
    rust(img, r, 30, streaks=False)
    p.put(img, at)
    # black rubber hose / cable, ribbed
    img, at = p.region('cable', 16, 32)
    noise(img, (24, 24, 24), 6, r)
    d = ImageDraw.Draw(img)
    for y in range(0, 32, 3):
        d.line([(0, y), (15, y)], fill=(44, 44, 42, 255))
    p.put(img, at)
    # red and yellow wires
    img, at = p.region('wires', 16, 32)
    noise(img, (20, 20, 20), 4, r)
    d = ImageDraw.Draw(img)
    for i, c in enumerate([(150, 24, 20), (190, 150, 30), (40, 60, 120), (150, 24, 20)]):
        d.line([(2 + i * 4, 0), (2 + i * 4, 31)], fill=c + (255,), width=2)
    p.put(img, at)
    # scuffed chrome piston
    img, at = p.region('chrome', 16, 32)
    for x in range(16):
        v = 120 + 90 * math.sin(x / 15 * math.pi)
        for y in range(32):
            img.putpixel((x, y), (clamp(v), clamp(v), clamp(v + 6), 255))
    scratches(img, r, 6, (70, 70, 70, 255))
    p.put(img, at)
    # dark joints and the recess inside him
    img, at = p.region('dark', 32, 32)
    noise(img, (22, 22, 22), 6, r)
    rust(img, r, 4, streaks=False)
    p.put(img, at)
    # his back: plates, a cooling grille, stencils, warning chevrons
    img, at = p.region('back', 64, 64)
    noise(img, GUN, 8, r, streak=0.4)
    d = ImageDraw.Draw(img)
    for y in range(8, 30, 3):                                         # cooling slots
        d.rectangle([10, y, 53, y + 1], fill=(14, 14, 14, 255))
    for i in range(0, 64, 8):                                         # hazard chevrons along the bottom
        d.polygon([(i, 63), (i + 4, 56), (i + 8, 63)], fill=(180, 150, 30, 255))
    d.text((22, 36), "07", fill=(170, 165, 140, 255))
    rust(img, r, 18)
    scratches(img, r, 20, (110, 112, 104, 255))
    rivets(img, 9)
    edge_wear(img, r)
    p.put(img, at)
    # the core behind his ribs
    img, at = p.region('core', 32, 32)
    for y in range(32):
        for x in range(32):
            dd = math.hypot(x - 15.5, y - 15.5) / 16
            v = max(0, 1 - dd)
            img.putpixel((x, y), (clamp(120 + 135 * v), clamp(10 + 120 * v ** 3), clamp(6 + 60 * v ** 6), 255))
    p.put(img, at)
    # eye lens: red, white-hot in the middle
    img, at = p.region('eye', 16, 16)
    for y in range(16):
        for x in range(16):
            dd = math.hypot(x - 7.5, y - 7.5) / 8
            v = max(0, 1 - dd)
            if dd > 0.92:                                           # the socket's black rim
                img.putpixel((x, y), (18, 6, 5, 255))
            elif dd > 0.7:                                          # a dull red ring round the lens
                img.putpixel((x, y), (120, 10, 6, 255))
            else:
                img.putpixel((x, y), (clamp(215 + 40 * v), clamp(10 + 150 * v ** 4), clamp(6 + 90 * v ** 6), 255))
    p.put(img, at)
    # the skull plate: bone-pale enamel, cracked, rust tears from the sockets, nose slit, cheek vents
    img, at = p.region('face', 64, 64)
    noise(img, (176, 170, 150), 10, r)
    d = ImageDraw.Draw(img)
    for ex in (17, 46):                                             # sunken sockets (the lenses sit in them)
        d.ellipse([ex - 13, 10, ex + 13, 34], fill=(46, 40, 34, 255))
        d.ellipse([ex - 10, 13, ex + 10, 31], fill=(16, 14, 12, 255))
        for t in range(3):                                          # rust tears
            x = ex - 6 + t * 6 + r.randrange(-1, 2)
            d.line([(x, 30), (x + r.randrange(-1, 2), 30 + r.randrange(10, 26))], fill=(110, 46, 20, 230), width=1)
    d.polygon([(31, 34), (28, 46), (35, 46)], fill=(24, 22, 20, 255))  # the nose cavity
    for side in (1, 54):                                            # cheek vents
        for y in range(38, 54, 4):
            d.rectangle([side, y, side + 8, y + 1], fill=(30, 28, 26, 255))
    for _ in range(5):                                              # cracks in the enamel
        x, y = r.randrange(64), r.randrange(64)
        for _ in range(6):
            nx, ny = x + r.randrange(-4, 5), y + r.randrange(1, 6)
            d.line([(x, y), (nx, ny)], fill=(60, 54, 46, 255))
            x, y = nx, ny
    d.rectangle([0, 0, 63, 5], fill=(40, 38, 34, 255))              # shadow under the cap's brim
    edge_wear(img, r, (90, 86, 76))
    p.put(img, at)
    # the sides and top of his skull: the same pale plate, plainer
    img, at = p.region('skull', 32, 32)
    noise(img, (160, 154, 136), 10, r)
    rust(img, r, 6)
    scratches(img, r, 8, (110, 104, 90, 255))
    p.put(img, at)
    # jaw: a steel grille of teeth, with blood
    img, at = p.region('jaw', 48, 16)
    noise(img, (90, 92, 88), 8, r)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 3, 47, 12], fill=(12, 10, 10, 255))
    for x in range(1, 47, 3):
        d.rectangle([x, 3, x + 1, 12 - (x * 7) % 3], fill=(150, 138, 100, 255))   # yellowed, uneven steel teeth
    blood(img, r, 5)
    p.put(img, at)
    # the cap: navy wool, grimy; band with a brass badge; a cracked black visor
    img, at = p.region('cap', 32, 32)
    noise(img, (30, 34, 54), 8, r)
    d = ImageDraw.Draw(img)
    for _ in range(40):
        d.point((r.randrange(32), r.randrange(32)), fill=(60, 58, 52, 255))
    d.line([(4, 6), (28, 8)], fill=(22, 24, 36, 255))
    p.put(img, at)
    img, at = p.region('band', 64, 16)
    noise(img, (18, 18, 20), 5, r)
    d = ImageDraw.Draw(img)
    d.line([(0, 2), (63, 2)], fill=(150, 120, 50, 255))           # gold piping
    d.line([(0, 13), (63, 13)], fill=(150, 120, 50, 255))
    d.ellipse([26, 3, 37, 12], fill=(170, 136, 50, 255))           # the badge: a wheel with wings
    d.ellipse([29, 5, 34, 10], fill=(100, 76, 26, 255))
    d.polygon([(26, 7), (16, 4), (19, 8), (16, 11)], fill=(170, 136, 50, 255))
    d.polygon([(37, 7), (47, 4), (44, 8), (47, 11)], fill=(170, 136, 50, 255))
    p.put(img, at)
    img, at = p.region('visor', 32, 16)
    noise(img, (12, 12, 14), 4, r)
    d = ImageDraw.Draw(img)
    d.line([(4, 2), (26, 4)], fill=(70, 70, 76, 255))              # the shine
    d.line([(18, 0), (15, 7), (19, 15)], fill=(56, 56, 60, 255))   # the crack
    p.put(img, at)
    # his claws: steel, bloodied
    img, at = p.region('claw', 16, 32)
    noise(img, (84, 86, 82), 10, r)
    blood(img, r, 4)
    p.put(img, at)
    # floor plate: rusty steel, bolts
    img, at = p.region('plate', 32, 32)
    noise(img, (70, 60, 50), 14, r)
    rust(img, r, 14, streaks=False)
    d = ImageDraw.Draw(img)
    for x, y in ((3, 3), (27, 3), (3, 27), (27, 27)):
        d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(130, 128, 120, 255))
    p.put(img, at)
    # what's left of his conductor's jacket: navy wool torn to ragged strips (the bottom edge cut out)
    img, at = p.region('cloth', 32, 64)
    noise(img, (28, 32, 50), 7, r, streak=0.8)
    d = ImageDraw.Draw(img)
    d.line([(15, 0), (15, 40)], fill=(150, 120, 50, 255))          # a gold seam stripe
    for _ in range(30):
        d.point((r.randrange(32), r.randrange(64)), fill=(58, 54, 48, 255))
    blood(img, r, 2)
    px = img.load()
    edge = 44
    for x in range(32):
        edge = max(30, min(62, edge + r.randrange(-5, 6)))
        for y in range(edge, 64):
            px[x, y] = (0, 0, 0, 0)
    p.put(img, at)
    # the throttle lever's knob: worn red bakelite
    img, at = p.region('knob', 16, 16)
    noise(img, (120, 18, 14), 14, r)
    p.put(img, at)
    return p.img


# ---------------------------------------------------------------- the model

QUADS = []   # (corners[4][3], uv[4][2], normal, part, light)
_state = {'part': STATIC, 'light': INSIDE}


def uv_rect(name, sub=None):
    """A region's corners in the atlas, as u0, v0, u1, v1; sub is a fraction (fx0, fy0, fx1, fy1) of it."""
    x, y, w, h = REGIONS[name]
    fx0, fy0, fx1, fy1 = sub or (0, 0, 1, 1)
    return ((OX + x + w * fx0) / ATLAS_SIZE, (OY + y + h * fy0) / ATLAS_SIZE,
            (OX + x + w * fx1) / ATLAS_SIZE, (OY + y + h * fy1) / ATLAS_SIZE)


def face(mat, o, rdir, rl, ddir, dl, n):
    if mat is None or rl <= 1e-5 or dl <= 1e-5:
        return
    if isinstance(mat, tuple):
        name, sub = mat
    else:
        name, sub = mat, None
    # a plain material shows a piece of its region in proportion to the face (64 px a block), so small
    # parts don't stretch one big texture; named-feature faces (sub given) map exactly
    if sub is None:
        x, y, w, h = REGIONS[name]
        fw, fh = min(1, rl * 64 / w), min(1, dl * 64 / h)
        hsh = (zlib.crc32(repr((round(o[0], 2), round(o[1], 2), round(o[2], 2), name)).encode()) & 0xffff) / 0xffff
        fx0 = (1 - fw) * hsh
        fy0 = (1 - fh) * ((hsh * 7.31) % 1)
        sub = (fx0, fy0, fx0 + fw, fy0 + fh)
    u0, v0, u1, v1 = uv_rect(name, sub)
    corners = []
    for cu, cv in ((0, 0), (0, 1), (1, 1), (1, 0)):
        corners.append(tuple(o[i] + rdir[i] * rl * cu + ddir[i] * dl * cv for i in range(3)))
    uvs = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
    QUADS.append((corners, uvs, n, _state['part'], _state['light']))


def box(x0, y0, z0, x1, y1, z1, nx=None, px=None, ny=None, py=None, nz=None, pz=None, all=None):
    """A box, faces -x, +x, -y, +y, -z, +z (as TrainMesh.box); all= gives every face one material."""
    if all is not None:
        nx = px = ny = py = nz = pz = all
    face(px, (x1, y1, z1), (0, 0, -1), z1 - z0, (0, -1, 0), y1 - y0, (1, 0, 0))
    face(nx, (x0, y1, z0), (0, 0, 1), z1 - z0, (0, -1, 0), y1 - y0, (-1, 0, 0))
    face(pz, (x0, y1, z1), (1, 0, 0), x1 - x0, (0, -1, 0), y1 - y0, (0, 0, 1))
    face(nz, (x1, y1, z0), (-1, 0, 0), x1 - x0, (0, -1, 0), y1 - y0, (0, 0, -1))
    face(py, (x0, y1, z0), (1, 0, 0), x1 - x0, (0, 0, 1), z1 - z0, (0, 1, 0))
    face(ny, (x0, y0, z1), (1, 0, 0), x1 - x0, (0, 0, -1), z1 - z0, (0, -1, 0))


def build():
    QUADS.clear()
    M, M2, D, R, C, W, P = 'metal0', 'metal1', 'dark', 'rust', 'cable', 'wires', 'chrome'
    st = _state
    st['part'], st['light'] = STATIC, INSIDE

    # bolted to the floor: a rusty plate, clamped feet
    box(-0.36, 0, -0.3, 0.36, 0.04, 0.3, all='plate')
    for sx in (-1, 1):
        x = sx * 0.16
        box(x - 0.08, 0.04, -0.12, x + 0.08, 0.13, 0.2, all=M)                 # foot
        box(x - 0.1, 0.04, 0.12, x + 0.1, 0.09, 0.24, all=R)                    # the clamp over its toe
        box(x - 0.055, 0.13, -0.05, x + 0.055, 0.55, 0.06, all=M2)              # shin
        box(x + sx * 0.06, 0.16, -0.02, x + sx * 0.085, 0.52, 0.01, all=P)      # a piston up the shin
        box(x - 0.075, 0.53, -0.07, x + 0.075, 0.67, 0.08, all=D)               # knee
        box(x - 0.06, 0.66, -0.06, x + 0.06, 0.98, 0.06, all=M)                 # thigh
    box(-0.25, 0.94, -0.13, 0.25, 1.12, 0.13, all=M2)                           # pelvis
    # the spine: bare vertebrae with dark discs between, cables either side
    for i in range(5):
        y = 1.1 + i * 0.07
        box(-0.06, y, -0.07, 0.06, y + 0.045, 0.05, all=M if i % 2 else M2)
        box(-0.045, y + 0.045, -0.055, 0.045, y + 0.07, 0.04, all=D)
    for sx in (-1, 1):
        box(sx * 0.1 - 0.025, 1.1, -0.04, sx * 0.1 + 0.025, 1.42, 0.0, all=C)
    # the torso: a cage of plates, hunched forward, open at the back
    box(-0.3, 1.42, -0.04, 0.3, 1.96, 0.16, all=M, nz=None)                     # front shell (back face left open)
    box(-0.3, 1.42, -0.15, -0.2, 1.96, -0.04, all=M2)                           # back plates either side of the opening
    box(0.2, 1.42, -0.15, 0.3, 1.96, -0.04, all=M2)
    box(-0.2, 1.82, -0.15, 0.2, 1.96, -0.04, nx=M, px=M, py=M, ny=D, nz='back', pz=None)  # upper back plate
    box(-0.2, 1.42, -0.15, 0.2, 1.5, -0.04, nx=M, px=M, py=D, ny=M, nz=('back', (0, 0.8, 1, 1)), pz=None)
    box(-0.2, 1.5, -0.02, 0.2, 1.82, -0.01, nz=D)                               # the cavity's back wall
    st['light'] = GLOW
    box(-0.09, 1.56, -0.06, 0.09, 1.76, -0.02, all='core')                     # the core, glowing between the ribs
    st['light'] = INSIDE
    for i in range(5):                                                         # ribs across the opening
        y = 1.52 + i * 0.065
        box(-0.2, y, -0.13, 0.2, y + 0.022, -0.1, all=P if i == 2 else M2)
    box(-0.03, 1.5, -0.16, 0.03, 1.82, -0.1, all=M)                            # the spine up the opening
    box(0.12, 1.55, -0.12, 0.15, 1.8, -0.09, all=W)                            # loose wiring in the cavity
    box(-0.16, 1.58, -0.12, -0.14, 1.78, -0.09, all=C)
    # hunched shoulders under heavy plates
    for sx in (-1, 1):
        x = sx * 0.38
        box(x - 0.12, 1.84, -0.17, x + 0.12, 2.0, 0.17, nx=M, px=M, ny=D, py=M2, nz=M2, pz=M2)
        box(x - 0.13, 1.82, -0.18, x + 0.13, 1.85, 0.18, all=R)               # the plate's rusted lip
        box(x - 0.06, 1.44, -0.06, x + 0.06, 1.84, 0.06, all=M)               # upper arm, hanging
        box(x - 0.025, 1.5, -0.08, x + 0.025, 1.8, -0.06, all=P)              # its piston
        box(x - 0.07, 1.36, -0.07, x + 0.07, 1.47, 0.07, all=D)               # elbow
        box(x - 0.05, 1.37, 0.07, x + 0.05, 1.46, 0.5, all=M2)                # forearm reaching to the console
        box(x - 0.065, 1.35, 0.46, x + 0.065, 1.47, 0.58, all=D)              # wrist
        for f in (-1, 0, 1):                                                   # three long claws
            fx = x + f * 0.04
            box(fx - 0.013, 1.25, 0.56, fx + 0.013, 1.42, 0.6, all='claw')
            box(fx - 0.013, 1.18, 0.6, fx + 0.013, 1.27, 0.66, all='claw')
    # rags of his jacket hanging off the shoulders down his back, and its collar
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 0.22, sx * 0.37))
        box(x0, 1.38, -0.19, x1, 1.84, -0.185, nz=('cloth', (0, 0, 0.5, 1)), pz=('cloth', (0, 0, 0.5, 1)))
        box(x0 if sx < 0 else x1 - 0.005, 1.42, -0.17, (x0 + 0.005) if sx < 0 else x1, 1.84, 0.17,
            nx=('cloth', (0.5, 0, 1, 1)), px=('cloth', (0.5, 0, 1, 1)))
    box(-0.15, 1.93, -0.15, 0.15, 2.0, 0.15, nx='cloth', px='cloth', nz='cloth', pz='cloth', py='dark')
    # the throttle under his right claws
    box(0.33, 1.0, 0.6, 0.39, 1.22, 0.66, all=P)
    box(0.31, 1.18, 0.58, 0.41, 1.26, 0.68, all='knob')
    # the neck: a piston and a bundle of hoses
    box(-0.035, 1.95, -0.035, 0.035, 2.12, 0.035, all=P)
    for dx, dz in ((-0.07, 0.02), (0.07, 0.02), (0.0, 0.07), (-0.04, -0.06)):
        box(dx - 0.025, 1.94, dz - 0.025, dx + 0.025, 2.1, dz + 0.025, all=C if dz < 0.05 else W)

    # ---- the head: turned right round to face back down the train (-z)
    st['part'] = HEAD
    # cranium and the skull plate of a face
    box(-0.19, 2.12, -0.17, 0.19, 2.44, 0.19, nx='skull', px='skull', ny=D, py='skull', nz=('face', (0, 0.08, 1, 1)), pz='skull')
    box(-0.205, 2.33, -0.215, 0.205, 2.38, -0.165, nx=M, px=M, ny=D, py=M, nz=('face', (0, 0, 1, 0.08)), pz=None)  # brow ridge
    for sx in (-1, 1):                                                         # cheekbones jutting
        box(sx * 0.19 - (0.03 if sx > 0 else 0), 2.15, -0.15, sx * 0.19 + (0.0 if sx > 0 else 0.03), 2.26, 0.05, all='skull')
    box(-0.025, 2.12, 0.19, 0.025, 2.4, 0.22, all=C)                           # a hose down the back of the skull
    # the eyes, deep in their sockets
    st['part'], st['light'] = EYES, GLOW
    for sx in (-1, 1):
        box(sx * 0.085 - 0.03, 2.284, -0.173, sx * 0.085 + 0.03, 2.328, -0.168, nz=('eye', (0, 0, 1, 1)))
    st['part'], st['light'] = HEAD, INSIDE
    # the cap: crown, band and badge, and a cracked brim, knocked a little askew
    box(-0.21, 2.46, -0.2, 0.22, 2.56, 0.22, nx='cap', px='cap', py='cap', nz='cap', pz='cap', ny=D)
    box(-0.215, 2.4, -0.2, 0.215, 2.47, 0.21, nx='band', px='band', pz='band', nz='band', ny=D)
    box(-0.2, 2.395, -0.33, 0.21, 2.415, -0.2, all='visor')
    # a snapped antenna
    box(0.19, 2.3, 0.05, 0.215, 2.34, 0.08, all=D)
    box(0.205, 2.34, 0.06, 0.215, 2.62, 0.07, all=P)
    box(0.12, 2.6, 0.06, 0.215, 2.61, 0.07, all=P)
    # the jaw, which drops when he talks
    st['part'] = JAW
    box(-0.15, 2.04, -0.19, 0.15, 2.13, 0.06, nx=M, px=M, ny=M2, py=D, nz='jaw', pz=D)
    box(-0.17, 2.08, 0.0, -0.15, 2.14, 0.06, all=P)                            # hinges
    box(0.15, 2.08, 0.0, 0.17, 2.14, 0.06, all=P)
    st['part'] = STATIC


HEAD_PIVOT = (0.0, 2.12, 0.0)
JAW_PIVOT = (0.0, 2.11, 0.04)


# ---------------------------------------------------------------- output

def paint_into(atlas):
    """Paints him into the train atlas (paint.py calls this) and builds his model."""
    img = paint()
    atlas.paste(img, (OX, OY))
    build()
    return img


BIN = ROOT / 'src/main/resources/assets/zomboidcraft/models/train/otto.bin'


def write_java():
    """His quads go in a resource file (too many for a Java array); OttoModel.java reads it."""
    import struct
    floats = []
    for corners, uvs, n, part, light in QUADS:
        for c in corners:
            floats += list(c)
        for uv in uvs:
            floats += list(uv)
        floats += list(n)
        floats += [part, light]
    BIN.parent.mkdir(parents=True, exist_ok=True)
    BIN.write_bytes(struct.pack('>i', len(floats)) + struct.pack('>%df' % len(floats), *floats))
    out = f"""package com.zomboidcraft.client.trains;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * OTTO's model, made by tools/trains/otto.py (assets/zomboidcraft/models/train/otto.bin): quads in his
 * own frame (x across, y up from the cab floor, z toward the windscreen; his head faces -z, down the
 * train). Each quad is 25 floats: four corners (x, y, z), four atlas uvs, the normal, the part (0
 * still, 1 head, 3 jaw, 4 eyes) and how it's lit (1 the car's light, 2 glowing).
 */
final class OttoModel {{
    static final int STRIDE = 25;
    static final float[] HEAD_PIVOT = {{{HEAD_PIVOT[0]}f, {HEAD_PIVOT[1]}f, {HEAD_PIVOT[2]}f}};
    static final float[] JAW_PIVOT = {{{JAW_PIVOT[0]}f, {JAW_PIVOT[1]}f, {JAW_PIVOT[2]}f}};
    static final float[] QUADS = load();

    private static float[] load() {{
        try (InputStream in = OttoModel.class.getResourceAsStream("/assets/zomboidcraft/models/train/otto.bin")) {{
            if (in == null) {{
                return new float[0];
            }}
            DataInputStream data = new DataInputStream(in);
            float[] q = new float[data.readInt()];
            for (int i = 0; i < q.length; i++) {{
                q[i] = data.readFloat();
            }}
            return q;
        }} catch (IOException e) {{
            return new float[0];
        }}
    }}

    private OttoModel() {{
    }}
}}
"""
    JAVA.write_text(out)


def render(atlas, out, eye, look, size=(720, 900), fov=32, head_yaw=0.0, jaw=0.0, light_dir=(-0.4, 0.8, -0.5)):
    """A quick software render of him (textured, z-buffered) for previews."""
    W, H = size
    tex = np.asarray(atlas.convert('RGBA'), dtype=np.float32) / 255
    img = np.zeros((H, W, 3), np.float32)
    # a dim cab behind him
    img[:] = np.linspace(0.07, 0.03, H)[:, None, None] * np.array([1.0, 0.95, 0.9])
    zbuf = np.full((H, W), np.inf, np.float32)
    eye = np.array(eye, float)
    f = np.array(look, float) - eye
    f /= np.linalg.norm(f)
    rgt = np.cross(f, (0, 1, 0))
    rgt /= np.linalg.norm(rgt)
    up = np.cross(rgt, f)
    focal = (H / 2) / math.tan(math.radians(fov) / 2)
    ld = np.array(light_dir, float)
    ld /= np.linalg.norm(ld)
    hc, hs = math.cos(math.radians(head_yaw)), math.sin(math.radians(head_yaw))

    def xform(p, part):
        x, y, z = p
        if part == JAW:
            y -= jaw
        if part in (HEAD, JAW, EYES):
            dx, dz = x - HEAD_PIVOT[0], z - HEAD_PIVOT[2]
            x, z = HEAD_PIVOT[0] + dx * hc + dz * hs, HEAD_PIVOT[2] - dx * hs + dz * hc
        return np.array((x, y, z))

    for corners, uvs, n, part, light in QUADS:
        pts = [xform(c, part) for c in corners]
        nn = xform(np.array(n) + np.array(HEAD_PIVOT), part) - xform(np.array(HEAD_PIVOT), part) if part in (HEAD, JAW, EYES) else np.array(n)
        cam = [np.array((np.dot(p - eye, rgt), np.dot(p - eye, up), np.dot(p - eye, f))) for p in pts]
        if any(c[2] <= 0.05 for c in cam):
            continue
        # back-face cull
        if np.dot(nn, pts[0] - eye) >= 0:
            continue
        scr = [(W / 2 + c[0] / c[2] * focal, H / 2 - c[1] / c[2] * focal, c[2]) for c in cam]
        shade = 1.0 if light == GLOW else 0.3 + 0.7 * max(0.0, float(np.dot(nn, ld))) + 0.15 * max(0.0, float(-nn[2]))
        for tri in ((0, 1, 2), (0, 2, 3)):
            (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = (scr[i] for i in tri)
            uv = [np.array(uvs[i]) for i in tri]
            minx, maxx = int(max(0, math.floor(min(x0, x1, x2)))), int(min(W - 1, math.ceil(max(x0, x1, x2))))
            miny, maxy = int(max(0, math.floor(min(y0, y1, y2)))), int(min(H - 1, math.ceil(max(y0, y1, y2))))
            if minx > maxx or miny > maxy:
                continue
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            w0 = ((x1 - xs) * (y2 - ys) - (x2 - xs) * (y1 - ys)) / area
            w1 = ((x2 - xs) * (y0 - ys) - (x0 - xs) * (y2 - ys)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
            if not inside.any():
                continue
            iz = w0 / z0 + w1 / z1 + w2 / z2
            depth = 1 / np.where(inside, iz, 1)
            u = (w0 * uv[0][0] / z0 + w1 * uv[1][0] / z1 + w2 * uv[2][0] / z2) * depth
            v = (w0 * uv[0][1] / z0 + w1 * uv[1][1] / z1 + w2 * uv[2][1] / z2) * depth
            tx = np.clip((u * ATLAS_SIZE).astype(int), 0, ATLAS_SIZE - 1)
            ty = np.clip((v * ATLAS_SIZE).astype(int), 0, ATLAS_SIZE - 1)
            col = tex[ty, tx]
            zb = zbuf[miny:maxy + 1, minx:maxx + 1]
            mask = inside & (depth < zb) & (col[..., 3] > 0.5)
            zb[mask] = depth[mask]
            img[miny:maxy + 1, minx:maxx + 1][mask] = col[..., :3][mask] * shade
    out_img = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    # the glow bleeding a little, as shaders bloom it
    return out_img


def previews(atlas, folder):
    folder = Path(folder)
    folder.mkdir(parents=True, exist_ok=True)
    shots = [
        ('otto-rider-view', (0.35, 1.75, -2.6), (0, 2.0, 0), 0, 0),        # what a rider sees through the cab window
        ('otto-face', (0.1, 2.28, -1.05), (0, 2.25, 0), 0, 0.04),          # up close, mid-word
        ('otto-side', (3.9, 1.5, -1.2), (0, 1.35, 0.1), 0, 0),                # the whole of him, side on
        ('otto-glance', (-0.9, 1.9, -2.3), (0, 1.9, 0), -35, 0),            # a sudden look aside
    ]
    paths = []
    for name, eye, look, yaw, jaw in shots:
        im = render(atlas, None, eye, look, head_yaw=yaw, jaw=jaw)
        p = folder / (name + '.png')
        im.save(p)
        paths.append(p)
    # a contact sheet
    ims = [Image.open(p) for p in paths]
    sheet = Image.new('RGB', (sum(i.width for i in ims), ims[0].height), (0, 0, 0))
    x = 0
    for i in ims:
        sheet.paste(i, (x, 0))
        x += i.width
    sheet.save(folder / 'otto-sheet.png')
    return folder / 'otto-sheet.png'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', default=os.environ.get('OTTO_PREVIEW', '/tmp/otto-preview'))
    args = ap.parse_args()
    atlas = Image.open(ATLAS).convert('RGBA')
    paint_into(atlas)
    atlas.save(ATLAS)
    write_java()
    print(len(QUADS), 'quads')
    print('preview', previews(atlas, args.preview))


if __name__ == '__main__':
    main()
