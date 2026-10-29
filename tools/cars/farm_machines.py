#!/usr/bin/env python3
"""Builds the farm machines: made here from measurements and photos (refs/farm/tractor, hay-wagon,
horse-trailer), not from a bought pack.

    python3 tools/cars/farm_machines.py [--only tractor_green] [--preview OUT_DIR]

- The tractors are row-crop utility tractors of the 1960s and 70s: lugged rear tyres on their
  rims and wheel weights, ribbed fronts on a wide axle, the engine open under a long hood with
  its maker's stripe, a stack and an air intake standing up through it, the operator's platform
  between flat-top fenders with their lamps, a roll bar (a sun canopy on some), the hitch, drawbar
  and PTO behind. They're driven: their wheels are packed apart, so they turn and steer.
- The hay wagon is a flat rack on steel running gear, a ladder rack at each end, its tongue down
  on the ground; loaded with square bales (hay_wagon) or near empty (hay_wagon_empty).
- The horse trailer is a two-horse bumper pull: painted aluminium sides with the maker's stripe,
  slat windows with bars, drop-down head windows, a tack door, tread-plate stone guard, tandem
  axles under their fenders, the tongue on its jack, swing doors behind.

Everything is painted into one atlas per machine and state: decals (hood sides, grille, dash,
trailer sides, the slow-moving-vehicle sign, the plate) and tiles for the materials. The states
are the cars': usable, abandoned (grime, sun-faded paint, rust), wrecked (lamps broken, a hood
side gone, the seat torn off) and burnt (charred, the rubber cracked, nothing soft left).

Model space as the cars: metres, wheels on y = 0, nose toward +z, the driver's side +x. They're
packed as .zcar and listed in models.json with kind "farm" (CarModels.Model.farm): never picked
for a street, only placed by name by the farms.
"""
import argparse
import io
import json
import math
import os
import struct
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from boats import (Geo, blob, box, font, frame, grouped, lathe, periodic_noise, rot_x, rot_y, rot_z,  # noqa: E402
                   shade, subdivide, tube, unit, v3)
from pack import CELL, VARIANTS, heights, java_hash, scramble  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))

ATLAS = 1024
TILE = 128              # a material's square: its 64 px pattern twice each way
TILE_M = 2.0            # metres one pattern covers
DECALS = {              # region (x, y, w, h) in the atlas
    'trailer_l': (0, 0, 768, 160), 'trailer_r': (0, 160, 768, 160),
    'hood_l': (0, 320, 512, 96), 'hood_r': (0, 416, 512, 96),
    'grille': (768, 0, 128, 160), 'plate': (896, 0, 96, 48), 'dash': (768, 160, 256, 96),
    'smv': (768, 256, 96, 84), 'fender_l': (512, 320, 256, 64), 'fender_r': (512, 384, 256, 64),
}
MATERIALS = ['paint', 'paint_dark', 'rim', 'engine', 'black', 'rubber', 'steel', 'chrome', 'rust', 'seat',
             'lens', 'red_lens', 'amber_lens', 'smoked', 'treadplate', 'wood', 'wood_dark', 'bale_side',
             'bale_end', 'twine', 'straw', 'white', 'alu', 'alu_tread', 'interior', 'galv', 'canopy', 'char_rubber']
GLASSY = ('lens', 'red_lens', 'amber_lens', 'smoked')


class Machine(Geo):
    """Geo that also notes which wheel (packed apart to turn) each triangle belongs to, -1 for the body."""

    def __init__(self):
        super().__init__()
        self.W = []
        self.wheel = -1

    def tri(self, *a, **k):
        super().tri(*a, **k)
        self.W += [self.wheel] * (len(self.T) - len(self.W))


class on_wheel:
    def __init__(self, g, k):
        self.g, self.k = g, k

    def __enter__(self):
        self.old = self.g.wheel
        self.g.wheel = self.k

    def __exit__(self, *a):
        self.g.wheel = self.old


# ---------------------------------------------------------------- shapes

def faces_box(g, c, h, mats, R=None):
    """A box whose faces take their own materials: mats {'+x': ..., '-y': ..., 'side': ..., 'end': ...}
    ('side' is any x or y face, 'end' a z face; a face named outright wins)."""
    c, h = np.asarray(c, float), np.asarray(h, float)
    R = np.eye(3) if R is None else R
    for a in range(3):
        u, v = [i for i in range(3) if i != a]
        for s in (-1, 1):
            name = ('+' if s > 0 else '-') + 'xyz'[a]
            mat = mats.get(name) or mats.get('end' if a == 2 else 'side')
            if mat is None:
                continue
            pts = []
            for su, sv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                e = np.zeros(3)
                e[a], e[u], e[v] = s, su, sv
                pts.append(c + R @ (h * e))
            n = np.zeros(3)
            n[a] = s
            g.poly(pts, mat, R @ n)


def inner_box(g, lo, hi, mat):
    """The inside of a box: its faces looking in (a trailer's dark inside, seen through its slats)."""
    lo, hi = np.asarray(lo, float), np.asarray(hi, float)
    c = (lo + hi) / 2
    h = (hi - lo) / 2
    for a in range(3):
        u, v = [i for i in range(3) if i != a]
        for s in (-1, 1):
            pts = []
            for su, sv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                e = np.zeros(3)
                e[a], e[u], e[v] = s, su, sv
                pts.append(c + h * e)
            n = np.zeros(3)
            n[a] = -s
            g.poly(pts, mat, n)


def prism(g, pts, z0, z1, mat, decal=None, caps=True):
    """A convex section [(x, y)...] run from z0 to z1. decal {edge index: name} maps that edge's face
    to a decal, the front (z1) at the left of a +x face and at the right of a -x one."""
    P = [np.asarray(p, float) for p in pts]
    cen = np.mean(P, 0)
    n = len(P)
    for i in range(n):
        a, b = P[i], P[(i + 1) % n]
        out = (a + b) / 2 - cen
        quad = [v3(a[0], a[1], z0), v3(b[0], b[1], z0), v3(b[0], b[1], z1), v3(a[0], a[1], z1)]
        name = (decal or {}).get(i)
        if name:
            top, bot = max(a[1], b[1]), min(a[1], b[1])
            uv = []
            for q in quad:
                u = (z1 - q[2]) / (z1 - z0) if out[0] > 0 else (q[2] - z0) / (z1 - z0)
                uv.append((u, (top - q[1]) / (top - bot)))
            g.poly(quad, name, v3(out[0], out[1], 0), uv=uv)
        else:
            g.poly(quad, mat, v3(out[0], out[1], 0))
    if caps:
        g.poly([v3(p[0], p[1], z1) for p in P], mat, v3(0, 0, 1))
        g.poly([v3(p[0], p[1], z0) for p in P], mat, v3(0, 0, -1))


def decal_quad(g, corners, name, out):
    """A quad (top left, top right, bottom right, bottom left as seen from outside) wearing a decal."""
    g.poly([np.asarray(c, float) for c in corners], name, out, uv=[(0, 0), (1, 0), (1, 1), (0, 1)])


def side_panel(g, poly_zy, x, name, z_front, z_back, y_top, y_bot):
    """A flat side at x (its face looking out, away from the middle) in the outline [(z, y)...],
    wearing a decal laid over z_back..z_front and y_bot..y_top, the front at the left of a +x side."""
    pts, uv = [], []
    for z, y in poly_zy:
        pts.append(v3(x, y, z))
        u = (z_front - z) / (z_front - z_back) if x > 0 else (z - z_back) / (z_front - z_back)
        uv.append((u, (y_top - y) / (y_top - y_bot)))
    g.poly(pts, name, v3(math.copysign(1, x), 0, 0), uv=uv)


def arc(c, r, a0, a1, n, plane='yz'):
    """Points round c (in the y-z plane, angles from +z toward +y) from a0 to a1 degrees."""
    out = []
    for k in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * k / n)
        out.append(v3(c[0], c[1] + r * math.sin(a), c[2] + r * math.cos(a)))
    return out


def lamp(g, c, facing, w, h, lens='lens', body='black', depth=0.08, round_=False):
    """A lamp: its housing and the lens on the face looking along facing."""
    f = unit(facing)
    R = rot_y(math.atan2(f[0], f[2]))
    c = np.asarray(c, float)
    if round_:
        lathe(g, c - f * depth, [(0, 0), (w, 0), (w * 1.05, depth * 0.8), (w * 0.95, depth), (0, depth)], body, n=12, axis=f)
        with grouped(g, 'glass'):
            lathe(g, c + f * 0.002, [(w * 0.9, 0), (w * 0.6, 0.012), (0, 0.016)], lens, n=12, axis=f)
    else:
        box(g, c - f * depth / 2, v3(w, h, depth / 2), body, R=R)
        with grouped(g, 'glass'):
            box(g, c + f * 0.004, v3(w * 0.85, h * 0.8, 0.006), lens, R=R)


# ---------------------------------------------------------------- wheels

def tyre_profile(r_rim, r_out, w, ribs=0):
    """A tyre's section from its inside bead out over the tread to its outside bead (lathe order)."""
    hw = w / 2
    side = [(r_rim, -hw * 0.82), (r_rim + (r_out - r_rim) * 0.35, -hw), (r_out - (r_out - r_rim) * 0.18, -hw * 0.98),
            (r_out - 0.02, -hw * 0.86)]
    tread = []
    if ribs:
        xs = np.linspace(-hw * 0.8, hw * 0.8, 2 * ribs + 2)
        for i, x in enumerate(xs):
            tread.append((r_out if i % 2 == 0 or i == len(xs) - 1 else r_out - 0.012, x))
            if 0 < i < len(xs) - 1:
                tread.append((r_out - 0.012 if i % 2 == 0 else r_out, x + 0.002))
    else:
        tread = [(r_out, -hw * 0.75), (r_out, hw * 0.75)]
    other = [(r, -y) for r, y in reversed(side)]
    return side + tread + other


def wheel_rim(g, c, axis, r_rim, w, mat, dish=0.04, spokes=0, weight=False):
    """The rim from its outside flange down into the dish to the hub, its nuts and valve, facing out
    along axis; spokes > 0 cuts a disc of that many round holes' worth of ribs (drawn as ribs)."""
    hw = w / 2
    prof = [(r_rim + 0.025, hw * 0.8), (r_rim + 0.012, hw * 0.92), (r_rim - 0.01, hw * 0.82), (r_rim - 0.02, dish + 0.02),
            (r_rim * 0.82, dish), (r_rim * 0.42, dish), (r_rim * 0.36, dish + 0.04), (r_rim * 0.22, dish + 0.05),
            (r_rim * 0.2, dish + 0.11), (r_rim * 0.12, dish + 0.12), (0, dish + 0.125)]
    lathe(g, c, prof, mat, n=24, axis=axis)
    a, b = frame(axis)
    for k in range(8 if r_rim > 0.35 else 6):
        ang = 2 * math.pi * k / (8 if r_rim > 0.35 else 6)
        p = c + axis * (dish + 0.05) + r_rim * 0.29 * (math.cos(ang) * a + math.sin(ang) * b)
        lathe(g, p, [(0, 0), (0.018, 0), (0.018, 0.02), (0, 0.024)], 'steel', n=6, axis=axis)
    if spokes:
        for k in range(spokes):
            ang = 2 * math.pi * (k + 0.5) / spokes
            d = math.cos(ang) * a + math.sin(ang) * b
            tube(g, [c + axis * (dish + 0.012) + d * r_rim * 0.45, c + axis * (dish + 0.012) + d * r_rim * 0.78], 0.035, mat, n=4)
    vang = 0.7
    vp = c + axis * dish + r_rim * 0.86 * (math.cos(vang) * a + math.sin(vang) * b)
    tube(g, [vp, vp + axis * 0.04], 0.008, 'black', n=4)
    if weight:
        # a cast wheel weight bolted to the dish
        lathe(g, c + axis * (dish + 0.02), [(0, 0), (r_rim * 0.62, 0), (r_rim * 0.62, 0.05), (r_rim * 0.3, 0.06), (0, 0.06)],
              mat if mat != 'rim' else 'paint_dark', n=20, axis=axis)


def rear_wheel(g, k, c, side, r_out=0.8, w=0.42, r_rim=0.48, rim='rim', weight=False, lugs=22):
    """A drive wheel: the lugged tyre (chevrons, each side's half a lug apart) on its rim."""
    axis = v3(side, 0, 0)
    base = r_out - 0.05
    with on_wheel(g, k):
        with grouped(g, 'tyre'):
            lathe(g, c, tyre_profile(r_rim, base, w), 'rubber', n=32, axis=axis)
        with grouped(g, 'lugs'):
            for i in range(lugs):
                for q in (-1, 1):
                    th = 2 * math.pi * (i + (0.5 if q > 0 else 0)) / lugs
                    rad = v3(0, math.cos(th), math.sin(th))
                    tan = v3(0, -math.sin(th), math.cos(th))
                    L = unit(axis * q * math.cos(math.radians(48)) + tan * math.sin(math.radians(48)))
                    Wd = np.cross(L, rad)
                    R = np.stack([L, rad, Wd], 1)
                    box(g, c + rad * (base + 0.022) + axis * q * w * 0.22 + tan * 0.04, v3(0.12, 0.026, 0.032), 'rubber', R=R)
        with grouped(g, 'rim'):
            wheel_rim(g, c, axis, r_rim, w, rim, dish=0.02, weight=weight)


def front_wheel(g, k, c, side, r_out=0.4, w=0.19, r_rim=0.25, rim='rim', ribs=3):
    axis = v3(side, 0, 0)
    with on_wheel(g, k):
        with grouped(g, 'tyre'):
            lathe(g, c, tyre_profile(r_rim, r_out, w, ribs), 'rubber', n=28, axis=axis)
        with grouped(g, 'rim'):
            wheel_rim(g, c, axis, r_rim, w, rim, dish=0.03)
            # the front hub's grease cap
            lathe(g, c + axis * 0.12, [(0, 0), (0.06, 0), (0.05, 0.05), (0, 0.06)], rim, n=10, axis=axis)


def trailer_wheel(g, c, side, r_out=0.36, w=0.2, r_rim=0.22, rim='white'):
    axis = v3(side, 0, 0)
    with grouped(g, 'tyre'):
        lathe(g, c, tyre_profile(r_rim, r_out, w, 2), 'rubber', n=24, axis=axis)
    with grouped(g, 'rim'):
        wheel_rim(g, c, axis, r_rim, w, rim, dish=0.05, spokes=5)


# ---------------------------------------------------------------- the tractor

def tractor(spec):
    g = Machine()
    wheels = []
    RZ, RY, RX = -0.85, 0.80, 0.90          # rear axle, wheel centre height, track
    FZ, FY, FX = 1.45, 0.40, 0.72
    for k, s in enumerate((1, -1)):
        rear_wheel(g, k, v3(s * RX, RY, RZ), s, rim='rim', weight=spec.get('weights', False))
        wheels.append((v3(s * RX, RY, RZ), 0.80, False))
    for k, s in enumerate((1, -1)):
        front_wheel(g, k + 2, v3(s * FX, FY, FZ), s, rim='rim')
        wheels.append((v3(s * FX, FY, FZ), 0.40, True))

    # the rear end: transmission and differential housing, the axle trumpets out to the final drives
    blob(g, v3(0, 0.84, RZ + 0.02), v3(0.36, 0.32, 0.40), 'paint', e=(0.15, 0.2))
    for s in (1, -1):
        tube(g, [v3(s * 0.32, RY, RZ), v3(s * 0.58, RY, RZ)], [0.13, 0.10], 'paint', n=12)
        lathe(g, v3(s * 0.58, RY, RZ), [(0, 0), (0.17, 0), (0.17, 0.07), (0.12, 0.1), (0, 0.1)], 'paint', n=14, axis=v3(s, 0, 0))
        # the brake housing on the axle and the rockshaft's lift arm over the back
        lathe(g, v3(s * 0.38, RY, RZ), [(0, 0), (0.15, 0), (0.15, 0.07), (0, 0.07)], 'paint_dark', n=12, axis=v3(s, 0, 0))
    # the engine open under the hood: block, head, oil pan, starter, filters, the injection pump
    box(g, v3(0, 0.80, 0.62), v3(0.23, 0.22, 0.78), 'paint')
    box(g, v3(0, 1.05, 0.62), v3(0.20, 0.06, 0.72), 'paint_dark')
    box(g, v3(0, 0.52, 0.58), v3(0.20, 0.06, 0.70), 'paint')
    blob(g, v3(0, 0.80, -0.22), v3(0.34, 0.30, 0.18), 'paint', e=(0.2, 0.2))      # the bell housing
    tube(g, [v3(0.27, 0.66, -0.05), v3(0.27, 0.66, 0.30)], 0.06, 'black', n=10)   # starter
    lathe(g, v3(-0.26, 0.98, 0.40), [(0, 0), (0.05, 0), (0.05, 0.18), (0, 0.19)], 'paint_dark', n=10, axis=v3(0, -1, 0))  # filter
    box(g, v3(-0.27, 0.85, 0.75), v3(0.05, 0.10, 0.16), 'black')                    # injection pump
    for z in (0.15, 0.45, 0.75, 1.05):
        tube(g, [v3(-0.24, 0.98, z), v3(-0.30, 0.90, z + 0.1), v3(-0.25, 0.80, z + 0.15)], 0.007, 'steel', n=4)
    tube(g, [v3(0.0, 1.0, 1.45), v3(0.0, 1.1, 1.62)], 0.035, 'black', n=8)         # top hose
    lathe(g, v3(0, 0.80, 1.42), [(0, 0), (0.09, 0), (0.09, 0.04), (0, 0.04)], 'steel', n=12, axis=v3(0, 0, 1))
    box(g, v3(0, 0.98, 1.72), v3(0.30, 0.32, 0.07), 'black')                        # radiator core
    # the front: bolster, the axle on its pivot with the spindles and tie rod, the weights
    box(g, v3(0, 0.62, 1.78), v3(0.26, 0.14, 0.24), 'paint')
    box(g, v3(0, FY + 0.10, FZ), v3(FX - 0.08, 0.055, 0.07), 'paint')
    for s in (1, -1):
        tube(g, [v3(s * (FX - 0.1), FY - 0.02, FZ), v3(s * (FX - 0.1), FY + 0.2, FZ)], 0.035, 'steel', n=8)
        tube(g, [v3(s * (FX - 0.1), FY, FZ), v3(s * (FX - 0.02), FY, FZ)], 0.03, 'steel', n=8)
        box(g, v3(s * (FX - 0.1), FY + 0.02, FZ - 0.11), v3(0.03, 0.015, 0.09), 'steel')
    tube(g, [v3(-(FX - 0.1), FY + 0.02, FZ - 0.18), v3(FX - 0.1, FY + 0.02, FZ - 0.18)], 0.016, 'steel', n=6)
    box(g, v3(0, 0.70, 2.04), v3(0.32, 0.10, 0.04), 'paint_dark')                    # weight bracket
    for i in range(6):
        x = -0.27 + i * 0.108
        faces_box(g, v3(x, 0.72, 2.18), v3(0.05, 0.15, 0.11), {'side': 'paint_dark', 'end': 'paint_dark'})
        tube(g, [v3(x, 0.84, 2.22), v3(x, 0.92, 2.25), v3(x, 0.92, 2.31), v3(x, 0.84, 2.31)], 0.012, 'paint_dark', n=4, caps=False)
    # the hood and its nose: the maker's stripe along each side, the grille in front
    hood = [(0.33, 0.96), (0.33, 1.40), (0.28, 1.50), (0.15, 1.54), (-0.15, 1.54), (-0.28, 1.50), (-0.33, 1.40), (-0.33, 0.96)]
    with grouped(g, 'hoodside_r'):
        side_panel(g, [(0.0, 1.40), (1.99, 1.40), (1.99, 0.96), (0.0, 0.96)], -0.331, 'hood_r', 1.99, 0.0, 1.40, 0.96)
    with grouped(g, 'hoodside_l'):
        side_panel(g, [(0.0, 1.40), (1.99, 1.40), (1.99, 0.96), (0.0, 0.96)], 0.331, 'hood_l', 1.99, 0.0, 1.40, 0.96)
    prism(g, hood[1:-1] + [(-0.33, 1.38), (0.33, 1.38)], 0.0, 2.0, 'paint', caps=False)
    g.poly([v3(p[0], p[1], 2.0) for p in hood], 'paint', v3(0, 0, 1))
    decal_quad(g, [v3(-0.30, 1.47, 2.012), v3(0.30, 1.47, 2.012), v3(0.30, 0.66, 2.012), v3(-0.30, 0.66, 2.012)], 'grille', v3(0, 0, 1))
    box(g, v3(0, 0.81, 1.98), v3(0.33, 0.16, 0.03), 'paint')
    for s in (1, -1):
        tube(g, [v3(s * 0.33, 0.95, 2.0), v3(s * 0.33, 0.95, 0.0)], 0.012, 'paint', n=4)     # the hood's lower lip
        lamp(g, v3(s * 0.25, 1.36, 2.05), v3(0, 0, 1), 0.065, 0.065, round_=True, body='chrome')
    # through the hood: the stack (muffler, its rain cap), the air intake's bowl, the fuel cap
    sx, sz = 0.16, 1.30
    tube(g, [v3(sx, 1.50, sz), v3(sx, 1.75, sz)], 0.045, 'black', n=10)
    tube(g, [v3(sx, 1.75, sz), v3(sx, 2.15, sz)], 0.07, 'black', n=12)
    tube(g, [v3(sx, 2.15, sz), v3(sx, 2.42, sz)], 0.045, 'black', n=10)
    with grouped(g, 'cap'):
        box(g, v3(sx, 2.44, sz - 0.01), v3(0.05, 0.004, 0.055), 'rust', R=rot_x(-0.35))
    ax_, az_ = -0.16, 1.45
    tube(g, [v3(ax_, 1.50, az_), v3(ax_, 1.86, az_)], 0.05, 'paint', n=10)
    lathe(g, v3(ax_, 1.86, az_), [(0, 0), (0.08, 0), (0.09, 0.08), (0.06, 0.13), (0, 0.14)], 'smoked', n=12)
    lathe(g, v3(0.0, 1.54, 0.30), [(0, 0), (0.05, 0), (0.05, 0.03), (0, 0.035)], 'chrome', n=10)
    # the cowl and dash, the steering column and wheel
    blob(g, v3(0, 1.22, -0.08), v3(0.36, 0.34, 0.10), 'paint', e=(0.12, 0.25))
    decal_quad(g, [v3(0.28, 1.52, -0.181), v3(-0.28, 1.52, -0.181), v3(-0.28, 1.28, -0.181), v3(0.28, 1.28, -0.181)], 'dash', v3(0, 0, -1))
    n = unit(v3(0, 0.8, -0.6))
    wc = v3(0, 1.74, -0.46)
    tube(g, [v3(0, 1.38, -0.12), wc - n * 0.03], 0.028, 'black', n=8)
    steering_wheel(g, wc, n, 0.2)
    for s, x in ((1, 0.16), (-1, -0.16)):
        tube(g, [v3(x, 1.0, -0.3), v3(x, 1.12, -0.42)], 0.014, 'steel', n=4)                 # pedals / levers
        box(g, v3(x, 1.12, -0.44), v3(0.05, 0.01, 0.06), 'treadplate')
    tube(g, [v3(0.22, 1.30, -0.20), v3(0.30, 1.62, -0.42)], 0.012, 'steel', n=4)              # throttle
    lathe(g, v3(0.30, 1.62, -0.42), [(0, 0), (0.025, 0), (0, 0.04)], 'red_lens', n=6)
    tube(g, [v3(-0.10, 1.12, -0.55), v3(-0.13, 1.45, -0.75)], 0.012, 'steel', n=4)            # gear lever
    lathe(g, v3(-0.13, 1.45, -0.75), [(0, 0), (0.03, 0), (0.03, 0.03), (0, 0.06)], 'black', n=8)
    # the platform each side of the housing, the step and grab handle up on the left
    for s in (1, -1):
        faces_box(g, v3(s * 0.48, 1.02, -0.52), v3(0.18, 0.02, 0.50), {'+y': 'treadplate', 'side': 'black', 'end': 'black'})
    faces_box(g, v3(0.56, 0.58, 0.10), v3(0.16, 0.015, 0.12), {'+y': 'treadplate', 'side': 'black', 'end': 'black'})
    tube(g, [v3(0.42, 0.58, 0.02), v3(0.42, 1.0, 0.02)], 0.02, 'black', n=6)
    tube(g, [v3(0.42, 0.58, 0.20), v3(0.42, 1.0, 0.20)], 0.02, 'black', n=6)
    faces_box(g, v3(-0.56, 0.62, 0.10), v3(0.14, 0.14, 0.14), {'side': 'black', 'end': 'black'})     # battery box
    tube(g, [v3(0.40, 1.04, -0.05), v3(0.40, 1.50, -0.05), v3(0.36, 1.58, -0.12)], 0.016, 'chrome', n=6)
    # the seat on its spring post
    with grouped(g, 'cushion'):
        blob(g, v3(0, 1.36, -1.06), v3(0.25, 0.06, 0.21), 'seat', e=(0.25, 0.35))
    with grouped(g, 'backrest'):
        blob(g, v3(0, 1.62, -1.27), v3(0.24, 0.19, 0.06), 'seat', e=(0.25, 0.35), R=rot_x(-0.25))
    box(g, v3(0, 1.30, -1.06), v3(0.22, 0.015, 0.19), 'black')
    tube(g, [v3(0, 1.29, -1.06), v3(0, 1.16, -0.86)], 0.03, 'black', n=8)
    tube(g, [v3(0, 1.30, -1.24), v3(0, 1.62, -1.32)], 0.02, 'black', n=6)
    # the flat-top fenders over the drive wheels, their lamps and handholds
    for s in (1, -1):
        fender(g, s, RX, RY, RZ, spec)
    # the roll bar (and a sun canopy over it on some), the slow-moving-vehicle sign at its back
    rops = [v3(0.66, 1.42, -1.60), v3(0.66, 2.46, -1.60), v3(0.56, 2.58, -1.60), v3(-0.56, 2.58, -1.60),
            v3(-0.66, 2.46, -1.60), v3(-0.66, 1.42, -1.60)]
    tube(g, rops, 0.045, 'black', n=10)
    for s in (1, -1):
        box(g, v3(s * 0.66, 1.42, -1.60), v3(0.07, 0.015, 0.07), 'black')
    if spec.get('canopy'):
        for s in (1, -1):
            tube(g, [v3(s * 0.62, 2.58, -1.60), v3(s * 0.62, 2.62, -1.0)], 0.022, 'black', n=6)
        with grouped(g, 'canopy'):
            pts = [v3(0.78, 2.66, -1.85), v3(-0.78, 2.66, -1.85), v3(-0.78, 2.66, -0.25), v3(0.78, 2.66, -0.25)]
            g.poly(pts, 'canopy', v3(0, 1, 0), both=True)
            for a, b in ((0, 1), (1, 2), (2, 3), (3, 0)):
                tube(g, [pts[a] + v3(0, -0.04, 0), pts[b] + v3(0, -0.04, 0)], 0.04, 'canopy', n=4,
                     prof=[(-1, -1), (1, -1), (1, 1), (-1, 1)])
    g.poly([v3(0.0, 2.12, -1.66), v3(-0.18, 1.82, -1.66), v3(0.18, 1.82, -1.66)], 'smv', v3(0, 0, -1),
           uv=[(0.5, 0.02), (0.02, 0.98), (0.98, 0.98)])
    box(g, v3(0, 1.97, -1.62), v3(0.05, 0.13, 0.02), 'black')
    # behind: three-point hitch, its lift arms and rods, the drawbar and the PTO under its shield
    for s in (1, -1):
        tube(g, [v3(s * 0.22, 0.60, -1.18), v3(s * 0.40, 0.48, -1.92)], 0.035, 'paint_dark', n=8)
        lathe(g, v3(s * 0.40, 0.48, -1.92), [(0, 0), (0.05, 0), (0.05, 0.04), (0, 0.04)], 'steel', n=10, axis=v3(s, 0, 0))
        tube(g, [v3(s * 0.26, 1.18, -1.18), v3(s * 0.31, 1.20, -1.56)], 0.03, 'paint_dark', n=6)
        tube(g, [v3(s * 0.31, 1.18, -1.54), v3(s * 0.33, 0.52, -1.62)], 0.018, 'steel', n=6)
    tube(g, [v3(-0.26, 1.18, -1.18), v3(0.26, 1.18, -1.18)], 0.045, 'paint', n=10)
    tube(g, [v3(0, 1.00, -1.22), v3(0, 0.95, -1.88)], 0.03, 'steel', n=8)
    faces_box(g, v3(0, 0.46, -1.55), v3(0.06, 0.025, 0.48), {'side': 'black', 'end': 'black'})
    tube(g, [v3(0, 0.40, -1.88), v3(0, 0.55, -1.88)], 0.018, 'steel', n=6)
    tube(g, [v3(0, 0.72, -1.22), v3(0, 0.72, -1.38)], 0.035, 'steel', n=10)
    tube(g, [v3(0.14, 0.62, -1.24), v3(0.14, 0.86, -1.36), v3(-0.14, 0.86, -1.36), v3(-0.14, 0.62, -1.24)], 0.02,
         'paint', n=4, prof=[(-1, -0.4), (1, -0.4), (1, 0.4), (-1, 0.4)], caps=False)
    with grouped(g, 'loose'):
        for s in (1, -1):
            tube(g, [v3(s * 0.18, 1.26, -1.30), v3(s * 0.24, 1.10, -1.55), v3(s * 0.22, 0.85, -1.70), v3(s * 0.30, 0.55, -1.66)],
                 0.013, 'black', n=5)
            lathe(g, v3(s * 0.18, 1.26, -1.24), [(0, 0), (0.025, 0), (0.025, 0.07), (0, 0.07)], 'steel', n=6, axis=v3(0, 0, -1))
    # a toolbox bolted under the right fender's front edge
    faces_box(g, v3(-0.56, 1.10, -0.02), v3(0.10, 0.07, 0.07), {'side': 'paint_dark', 'end': 'paint_dark'})
    meta = dict(seats=[[0.0, 1.98, -1.02]], handles=[[0.60, 1.30, 0.10]], steering=[*wc, *n, 0.2], wheelbase=FZ - RZ)
    return g, wheels, meta


def steering_wheel(g, c, normal, r):
    """A three-spoke wheel, the rim's grip ribs and the hub's horn button, square to normal."""
    a, b = frame(normal)
    ring = [c + r * (math.cos(k * math.pi / 10) * a + math.sin(k * math.pi / 10) * b) for k in range(20)]
    tube(g, ring, 0.018, 'black', n=6, closed=True)
    for k in range(3):
        ang = k * 2 * math.pi / 3 - math.pi / 2
        tube(g, [c, c + (r - 0.01) * (math.cos(ang) * a + math.sin(ang) * b)], 0.012, 'steel', n=4)
    lathe(g, c - normal * 0.02, [(0, 0), (0.045, 0), (0.04, 0.04), (0, 0.05)], 'black', n=10, axis=normal)


def fender(g, s, RX, RY, RZ, spec):
    """A flat-top fender: the curved sheet over the tyre with its rolled outer lip, the flat top
    reaching in to the platform, the inner panel down to it, a lamp on its back corner."""
    x_in, x_out = s * 0.58, s * 1.18
    R = 0.90
    top = RY + 0.80 + 0.16
    pts = arc(v3(0, RY, RZ), R, 18, 162, 16)
    # the curved sheet, clipped by the flat top
    prof = [p for p in pts if p[1] <= top]
    for a, b in zip(prof[:-1], prof[1:]):
        q = [v3(x_in, a[1], a[2]), v3(x_out, a[1], a[2]), v3(x_out, b[1], b[2]), v3(x_in, b[1], b[2])]
        g.poly(q, 'paint', (a + b) / 2 - v3(0, RY, RZ), both=True)
    # the flat top across between the arc's ends at that height
    zs = [p[2] for p in pts if p[1] > top - 0.001]
    if zs:
        za, zb = min(zs) - 0.06, max(zs) + 0.06
        faces_box(g, v3((x_in + x_out) / 2, top + 0.01, (za + zb) / 2), v3(abs(x_out - x_in) / 2, 0.012, (zb - za) / 2),
                  {'side': 'paint', 'end': 'paint'})
    lip = [v3(x_out, p[1], p[2]) for p in prof]
    tube(g, lip, 0.016, 'paint', n=5)
    # inner panel down to the platform
    inner = [v3(x_in, max(p[1], 1.03), p[2]) for p in prof]
    g.poly(inner + [v3(x_in, 1.03, inner[-1][2]), v3(x_in, 1.03, inner[0][2])], 'paint', v3(-s, 0, 0), both=True)
    # the outer face of the curve's skirt: a band hanging below the top at the outside, with the decal
    zf, zr = RZ + 0.62, RZ - 0.62
    yb = top - 0.16
    g.poly([v3(x_out, top, zf), v3(x_out, top, zr), v3(x_out, yb, zr), v3(x_out, yb, zf)], 'fender_l' if s > 0 else 'fender_r',
           v3(s, 0, 0), uv=[(0, 0), (1, 0), (1, 1), (0, 1)] if s > 0 else [(1, 0), (0, 0), (0, 1), (1, 1)])
    # the lamp on the back corner (a work light looking back, the red lens below it) and a handhold
    lamp(g, v3(s * 0.98, top + 0.12, RZ - 0.55), v3(0, 0, -1), 0.07, 0.055)
    box(g, v3(s * 0.98, top + 0.04, RZ - 0.52), v3(0.02, 0.04, 0.02), 'black')
    lamp(g, v3(s * 1.12, top - 0.06, RZ - 0.66), v3(0, 0, -1), 0.04, 0.03, lens='red_lens', depth=0.04)
    lamp(g, v3(s * 1.12, top - 0.06, RZ + 0.66), v3(0, 0, 1), 0.04, 0.03, lens='amber_lens', depth=0.04)
    tube(g, [v3(s * 0.66, top + 0.02, RZ + 0.25), v3(s * 0.66, top + 0.14, RZ + 0.2), v3(s * 0.66, top + 0.14, RZ - 0.2),
             v3(s * 0.66, top + 0.02, RZ - 0.25)], 0.014, 'black', n=5)


# ---------------------------------------------------------------- the hay wagon

def bale(g, c, yaw, rng, along=0.92, wide=0.46, high=0.36):
    """A small square bale: its sides of laid straw, its cut ends, two strings of twine round it."""
    R = rot_y(yaw) @ rot_z(rng.uniform(-0.02, 0.02))
    h = v3(wide / 2, high / 2, along / 2) * (1 + rng.uniform(-0.03, 0.03, 3))
    faces_box(g, c, h, {'side': 'bale_side', 'end': 'bale_end'}, R=R)
    for q in (-0.28, 0.28):
        x = q * h[0] * 2
        box(g, c + R @ v3(x, h[1] + 0.003, 0), v3(0.006, 0.003, h[2] + 0.004), 'twine', R=R)
        for e in (-1, 1):
            box(g, c + R @ v3(x, 0, e * (h[2] + 0.003)), v3(0.006, h[1] + 0.004, 0.003), 'twine', R=R)


def hay_wagon(spec):
    g = Machine()
    rng = np.random.default_rng(java_hash(spec['id']) & 0xFFFF)
    L, W = 2.45, 1.18                # half length and width of the rack
    DY = 1.02                        # deck top
    with grouped(g, 'gear'):
        for z in (-1.55, 1.55):
            box(g, v3(0, 0.39, z), v3(0.86, 0.04, 0.045), 'paint')
            box(g, v3(0, DY - 0.20, z), v3(0.72, 0.07, 0.07), 'paint')            # bolster
            for s in (1, -1):
                tube(g, [v3(s * 0.62, 0.42, z), v3(s * 0.62, DY - 0.26, z)], 0.035, 'paint', n=6)
                trailer_wheel(g, v3(s * 0.86, 0.38, z), s, r_out=0.38, w=0.2, r_rim=0.24, rim='rim')
        lathe(g, v3(0, DY - 0.29, 1.55), [(0, 0), (0.32, 0), (0.32, 0.03), (0, 0.03)], 'steel', n=16)
        tube(g, [v3(0, 0.62, -1.75), v3(0, 0.62, 1.45)], 0.05, 'paint', n=8)              # reach pole
        for s in (1, -1):
            tube(g, [v3(s * 0.70, 0.40, -1.55), v3(0, 0.62, -0.9)], 0.025, 'paint', n=6)   # hounds
            tube(g, [v3(s * 0.38, 0.40, 1.62), v3(0, 0.14, 3.25)], 0.035, 'paint', n=8)   # tongue
        tube(g, [v3(-0.30, 0.36, 1.85), v3(0.30, 0.36, 1.85)], 0.02, 'paint', n=6)
        lathe(g, v3(0, 0.12, 3.36), [(0.07, -0.02), (0.08, 0), (0.07, 0.02), (0.05, 0.02), (0.05, -0.02)], 'steel', n=12)
        box(g, v3(0, 0.13, 3.27), v3(0.04, 0.03, 0.06), 'steel')
    with grouped(g, 'loose'):
        tube(g, [v3(0.05, 0.13, 3.20), v3(0.12, 0.03, 3.05), v3(0.20, 0.02, 2.85), v3(0.25, 0.03, 2.70)], 0.012, 'galv', n=4)
    # the deck: sills, cross members, the boards with their gaps, stake pockets along the sides
    for s in (1, -1):
        box(g, v3(s * 0.95, DY - 0.11, 0), v3(0.06, 0.08, L), 'wood_dark')
    for z in np.linspace(-L + 0.1, L - 0.1, 8):
        box(g, v3(0, DY - 0.07, z), v3(W, 0.045, 0.045), 'wood_dark')
    n = 12
    bw = 2 * W / n
    with grouped(g, 'boards'):
        for i in range(n):
            x = -W + bw * (i + 0.5)
            z0, z1 = -L, L
            if rng.random() < 0.15:
                z1 -= rng.uniform(0.1, 0.4)
            box(g, v3(x, DY - 0.015, (z0 + z1) / 2), v3(bw / 2 - 0.008, 0.016, (z1 - z0) / 2), 'wood')
    for s in (1, -1):
        for z in np.linspace(-L + 0.3, L - 0.3, 5):
            box(g, v3(s * (W + 0.02), DY - 0.06, z), v3(0.025, 0.06, 0.05), 'galv')
    # the ladder racks: posts, boards, a brace, front tall and back lower
    for z, hgt in ((L - 0.05, 2.0), (-L + 0.05, 1.3 if spec.get('low_back', True) else 2.0)):
        with grouped(g, 'rack'):
            for s in (1, -1):
                box(g, v3(s * (W - 0.08), DY + hgt / 2, z), v3(0.05, hgt / 2, 0.05), 'wood_dark')
            for y in np.arange(0.35, hgt - 0.05, 0.42):
                box(g, v3(0, DY + y, z + 0.0), v3(W - 0.02, 0.06, 0.022), 'wood')
            box(g, v3(0, DY + hgt - 0.04, z), v3(W, 0.05, 0.04), 'wood_dark')
            ang = math.atan2(hgt - 0.3, 2 * (W - 0.15))
            box(g, v3(0, DY + hgt / 2, z + (0.03 if z > 0 else -0.03)), v3(math.hypot(hgt - 0.3, 2 * (W - 0.15)) / 2, 0.04, 0.018),
                'wood_dark', R=rot_z(ang))
    # the load
    with grouped(g, 'bales'):
        layers = spec.get('layers', 3)
        for k in range(layers):
            for i in range(5):
                for j in range(5):
                    if k == layers - 1 and rng.random() < 0.3:
                        continue
                    if layers == 1 and rng.random() < 0.7:
                        continue
                    x = -W + 0.24 + i * 0.47 + rng.uniform(-0.02, 0.02)
                    z = -L + 0.06 + 0.47 + j * 0.94 + rng.uniform(-0.03, 0.03)
                    bale(g, v3(x, DY + 0.18 + k * 0.365, z), rng.uniform(-0.04, 0.04), rng)
        if spec.get('fallen', True):
            bale(g, v3(W + 0.45, 0.18, 0.5), 1.4, rng)
            bale(g, v3(W + 0.30, 0.20, -0.4), 0.3, rng)
    with grouped(g, 'straw'):
        for _ in range(26 if layers == 1 else 10):
            x, z = rng.uniform(-W + 0.1, W - 0.1), rng.uniform(-L + 0.1, L - 0.1)
            blob(g, v3(x, DY + 0.01, z), v3(rng.uniform(0.08, 0.22), 0.025, rng.uniform(0.08, 0.25)), 'straw', e=(0.6, 0.6), nu=8, nv=3)
    return g, [], dict(seats=[], wheelbase=3.1)


# ---------------------------------------------------------------- the horse trailer

def horse_trailer(spec):
    g = Machine()
    X = 0.95                          # half width
    Y0, Y1 = 0.50, 2.50               # floor and roof
    ZR, ZF, ZN = -2.30, 1.90, 2.28    # back, the front wall's foot, the nose
    YB = 1.42                         # top of the lower panel (the stripe's)
    with grouped(g, 'body'):
        inner_box(g, v3(-X + 0.02, Y0 + 0.02, ZR + 0.02), v3(X - 0.02, Y1 - 0.02, ZF - 0.02), 'interior')
    # the sides: lower panel with the decal, the upper with slat windows behind and head windows ahead
    for s in (1, -1):
        name = 'trailer_l' if s > 0 else 'trailer_r'
        side_panel(g, [(ZR, YB), (ZN, YB), (ZN, 1.30), (ZF, Y0), (ZR, Y0)], s * X, name, ZN, ZR, YB, Y0)
        upper = []
        slats = (ZR + 0.12, 0.05)           # the slat zone, back to just behind the head windows
        gaps = [(1.60, 1.70), (1.84, 1.94), (2.08, 2.18)]
        ys = [YB] + [y for gp in gaps for y in gp] + [2.32]
        for i in range(len(ys) - 1):
            if i % 2 == 0:
                upper.append((slats[0], slats[1], ys[i], ys[i + 1]))
        upper.append((slats[0], slats[1], 2.32, Y1))
        upper.append((ZR, slats[0], YB, Y1))
        hw = (0.25, 1.10, 1.70, 2.22)       # head window: z0, z1, y0, y1
        upper += [(slats[1], hw[0], YB, Y1), (hw[0], hw[1], YB, hw[2]), (hw[0], hw[1], hw[3], Y1), (hw[1], ZN, YB, 2.25)]
        for z0, z1, y0, y1 in upper:
            q = [v3(s * X, y1, z0), v3(s * X, y1, z1), v3(s * X, y0, z1), v3(s * X, y0, z0)]
            g.poly(q, 'white', v3(s, 0, 0))
        g.poly([v3(s * X, 2.25, hw[1]), v3(s * X, 2.25, ZN), v3(s * X, Y1, 2.06), v3(s * X, Y1, hw[1])], 'white', v3(s, 0, 0))
        for y0, y1 in gaps:
            for z in np.arange(slats[0] + 0.15, slats[1], 0.16):
                tube(g, [v3(s * (X - 0.01), y0, z), v3(s * (X - 0.01), y1, z)], 0.008, 'steel', n=4)
        # the head window let down on its hinges, the bars behind it
        for z in np.arange(hw[0] + 0.08, hw[1], 0.11):
            tube(g, [v3(s * (X - 0.01), hw[2], z), v3(s * (X - 0.01), hw[3], z)], 0.009, 'steel', n=4)
        with grouped(g, 'glass'):
            g.poly([v3(s * (X + 0.05), hw[2], hw[0]), v3(s * (X + 0.05), hw[2], hw[1]), v3(s * (X + 0.24), hw[2] - 0.38, hw[1]),
                    v3(s * (X + 0.24), hw[2] - 0.38, hw[0])], 'smoked', v3(s, 0.4, 0), both=True)
        tube(g, [v3(s * (X + 0.05), hw[2], hw[0]), v3(s * (X + 0.24), hw[2] - 0.38, hw[0]), v3(s * (X + 0.24), hw[2] - 0.38, hw[1]),
                 v3(s * (X + 0.05), hw[2], hw[1])], 0.012, 'alu', n=4, caps=False)
        tube(g, [v3(s * X, hw[2] - 0.01, hw[0]), v3(s * X, hw[2] - 0.01, hw[1])], 0.015, 'alu', n=6)
        # the frame round the openings and the rubbing rail along the panel's top
        tube(g, [v3(s * X, YB, ZR), v3(s * X, YB, ZN)], 0.018, 'alu', n=6)
        tube(g, [v3(s * X, Y1, ZR), v3(s * X, Y1, 2.06)], 0.035, 'alu', n=8)
        # marker lights: amber ahead, red behind, one midway
        for z, ln in ((ZN - 0.1, 'amber_lens'), (ZR + 0.08, 'red_lens'), (-0.2, 'amber_lens')):
            lamp(g, v3(s * (X + 0.02), Y1 - 0.08, z), v3(s, 0, 0), 0.035, 0.02, lens=ln, body='alu', depth=0.03)
        # the fenders over the tandem wheels and the wheels
        for z in (-0.95, -0.15):
            trailer_wheel(g, v3(s * 1.07, 0.36, z), s, rim='white')
        with grouped(g, 'body'):
            ff = [v3(0, 0.36 + 0.46 * math.sin(math.radians(a)) + 0.12, -0.55 + 0.80 * math.cos(math.radians(a))) for a in np.linspace(0, 180, 13)]
            for a, b in zip(ff[:-1], ff[1:]):
                q = [v3(s * X, a[1], a[2]), v3(s * 1.21, a[1], a[2]), v3(s * 1.21, b[1], b[2]), v3(s * X, b[1], b[2])]
                g.poly(q, 'alu_tread', (a + b) / 2 - v3(0, 0.36, -0.55), both=True)
            tube(g, [v3(s * 1.21, p[1], p[2]) for p in ff], 0.012, 'alu', n=4)
        # the tack door ahead of the wheels on the left: its frame and handle (its outline's in the decal)
        if s > 0:
            lathe(g, v3(X + 0.005, 1.05, 1.18), [(0, 0), (0.04, 0), (0.04, 0.02), (0, 0.025)], 'chrome', n=8, axis=v3(1, 0, 0))
            box(g, v3(X + 0.02, 1.02, 1.18), v3(0.012, 0.012, 0.07), 'chrome')
            for y in (0.7, 1.25):
                box(g, v3(X + 0.012, y, 1.85), v3(0.012, 0.04, 0.03), 'alu')
    # the nose: tread-plate stone guard up its slope, the front wall, the slope up to the roof
    g.poly([v3(X, Y0, ZF), v3(-X, Y0, ZF), v3(-X, 1.30, ZN), v3(X, 1.30, ZN)], 'alu_tread', v3(0, -0.5, 1))
    g.poly([v3(X, 1.30, ZN), v3(-X, 1.30, ZN), v3(-X, 2.25, ZN), v3(X, 2.25, ZN)], 'white', v3(0, 0, 1))
    g.poly([v3(X, 2.25, ZN), v3(-X, 2.25, ZN), v3(-X, Y1, 2.06), v3(X, Y1, 2.06)], 'white', v3(0, 1, 1))
    with grouped(g, 'glass'):
        g.poly([v3(0.55, 2.12, ZN + 0.005), v3(-0.55, 2.12, ZN + 0.005), v3(-0.55, 1.70, ZN + 0.005), v3(0.55, 1.70, ZN + 0.005)],
               'smoked', v3(0, 0, 1))
    tube(g, [v3(0.56, 2.13, ZN), v3(-0.56, 2.13, ZN), v3(-0.56, 1.69, ZN), v3(0.56, 1.69, ZN)], 0.015, 'alu', n=4, closed=True)
    # the roof, its edge rails and the two roof vents
    g.poly([v3(X, Y1, ZR), v3(X, Y1, 2.06), v3(-X, Y1, 2.06), v3(-X, Y1, ZR)], 'white', v3(0, 1, 0))
    for z in (-1.4, 0.4):
        faces_box(g, v3(0, Y1 + 0.03, z), v3(0.26, 0.03, 0.26), {'side': 'alu', 'end': 'alu'})
        faces_box(g, v3(0, Y1 + 0.09, z - 0.05), v3(0.24, 0.015, 0.24), {'side': 'white', 'end': 'white'}, R=rot_x(0.12))
    for x in (-0.6, 0.0, 0.6):
        lamp(g, v3(x, Y1 - 0.06, ZN + 0.005 - 0.0), v3(0, 0, 1), 0.035, 0.02, lens='amber_lens', body='alu', depth=0.03)
    # underneath: the floor's cross members and frame rails, the axles on their leaf springs
    box(g, v3(0, Y0 - 0.02, (ZR + ZF) / 2), v3(X, 0.02, (ZF - ZR) / 2), 'alu')
    for s in (1, -1):
        box(g, v3(s * 0.65, Y0 - 0.08, (ZR + ZF) / 2), v3(0.04, 0.06, (ZF - ZR) / 2), 'galv')
        box(g, v3(s * 0.85, 0.40, -0.55), v3(0.03, 0.025, 0.65), 'black')
    for z in (-0.95, -0.15):
        tube(g, [v3(-1.0, 0.36, z), v3(1.0, 0.36, z)], 0.035, 'black', n=8)
    # the tongue: its A-frame to the coupler, the jack down on its foot, the chains and battery box
    for s in (1, -1):
        tube(g, [v3(s * 0.62, 0.44, ZF - 0.05), v3(s * 0.05, 0.50, 3.30)], 0.045, 'galv', n=6,
             prof=[(-1, -1), (1, -1), (1, 1), (-1, 1)])
    faces_box(g, v3(0, 0.50, 3.40), v3(0.06, 0.05, 0.14), {'side': 'galv', 'end': 'galv'})
    lathe(g, v3(0, 0.44, 3.47), [(0, 0), (0.055, 0), (0.06, 0.04), (0.04, 0.08), (0, 0.09)], 'galv', n=10, axis=v3(0, 1, 0))
    tube(g, [v3(0.0, 0.03, 3.0), v3(0.0, 1.02, 3.0)], 0.04, 'galv', n=8)
    lathe(g, v3(0.0, 0.0, 3.0), [(0, 0), (0.1, 0), (0.1, 0.025), (0, 0.025)], 'galv', n=10)
    tube(g, [v3(0.0, 1.02, 3.0), v3(0.0, 1.06, 3.0), v3(0.15, 1.06, 3.0), v3(0.15, 1.16, 3.0)], 0.01, 'steel', n=4)
    faces_box(g, v3(0.0, 0.62, 2.62), v3(0.15, 0.10, 0.11), {'side': 'black', 'end': 'black'})
    with grouped(g, 'loose'):
        for s in (1, -1):
            tube(g, [v3(s * 0.10, 0.48, 3.30), v3(s * 0.14, 0.16, 3.20), v3(s * 0.16, 0.05, 3.0), v3(s * 0.15, 0.20, 2.85)],
                 0.012, 'galv', n=4)
    # behind: the two swing doors (their windows and cam latch bars), the bumper, lamps and plate
    for s in (1, -1):
        x0, x1 = s * 0.01, s * (X - 0.01)
        q = [v3(x0, Y1 - 0.06, ZR), v3(x1, Y1 - 0.06, ZR), v3(x1, Y0 + 0.02, ZR), v3(x0, Y0 + 0.02, ZR)]
        g.poly(q, 'white', v3(0, 0, -1))
        with grouped(g, 'glass'):
            g.poly([v3(s * 0.15, 2.25, ZR - 0.005), v3(s * 0.80, 2.25, ZR - 0.005), v3(s * 0.80, 1.80, ZR - 0.005),
                    v3(s * 0.15, 1.80, ZR - 0.005)], 'smoked', v3(0, 0, -1))
        tube(g, [v3(s * 0.48, 0.62, ZR - 0.035), v3(s * 0.48, 2.35, ZR - 0.035)], 0.016, 'chrome', n=6)
        box(g, v3(s * 0.48, 1.30, ZR - 0.05), v3(0.012, 0.012, 0.03), 'chrome')
        tube(g, [v3(s * 0.48, 1.30, ZR - 0.07), v3(s * 0.62, 1.22, ZR - 0.07)], 0.01, 'chrome', n=4)
        for y in (0.75, 1.55, 2.30):
            box(g, v3(s * (X - 0.02), y, ZR - 0.015), v3(0.03, 0.05, 0.015), 'alu')
        lamp(g, v3(s * 0.78, 0.66, ZR - 0.02), v3(0, 0, -1), 0.07, 0.04, lens='red_lens', depth=0.04)
    tube(g, [v3(0, 0.60, ZR - 0.03), v3(0, 2.44, ZR - 0.03)], 0.012, 'alu', n=4)
    faces_box(g, v3(0, 0.43, ZR - 0.06), v3(X, 0.05, 0.06), {'+y': 'alu_tread', 'side': 'alu', 'end': 'alu'})
    decal_quad(g, [v3(0.16, 0.66, ZR - 0.122), v3(-0.16, 0.66, ZR - 0.122), v3(-0.16, 0.50, ZR - 0.122), v3(0.16, 0.50, ZR - 0.122)],
               'plate', v3(0, 0, -1))
    return g, [], dict(seats=[], wheelbase=0.8)


SPECS = {
    'tractor_green': dict(build=tractor, weight=1, paint=(46, 120, 52), paint_dark=(36, 92, 40), rim=(226, 190, 40),
                          seat=(214, 180, 36), stripe=(226, 190, 40), maker='GRANGER', model='4030', canopy=False, weights=True),
    'tractor_red': dict(build=tractor, weight=1, paint=(168, 30, 26), paint_dark=(126, 26, 22), rim=(206, 204, 196),
                        seat=(30, 30, 32), stripe=(230, 228, 220), maker='HARLOW', model='560', canopy=True, weights=False),
    'hay_wagon': dict(build=hay_wagon, weight=1, paint=(150, 30, 26), paint_dark=(110, 26, 22), rim=(150, 30, 26), layers=3),
    'hay_wagon_empty': dict(build=hay_wagon, weight=1, paint=(46, 110, 50), paint_dark=(36, 84, 40), rim=(220, 186, 40),
                            layers=1, fallen=False),
    'horse_trailer': dict(build=horse_trailer, weight=1, paint=(232, 230, 224), paint_dark=(80, 80, 84), rim=(232, 230, 224),
                          stripe=(110, 28, 40), stripe2=(46, 64, 96), maker='PRAIRIE LINE', model='2H STRAIGHT LOAD'),
}


# ---------------------------------------------------------------- the atlas

def tile_origin(mat):
    k = MATERIALS.index(mat)
    return (k % 8) * TILE, 512 + (k // 8) * TILE


def tile_uv(T, mat):
    """Uvs into the material's square: projected on the plane its normal faces most, at 32 px a metre."""
    n = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
    ax = np.argmax(np.abs(n), 1)
    A = np.where(ax[:, None] == 0, T[..., 2], T[..., 0])
    B = np.where(ax[:, None] == 1, T[..., 2], -T[..., 1])
    px = TILE / 2 / TILE_M
    A, B = A * px, B * px
    A = A - np.floor(A.min(1, keepdims=True) / (TILE / 2)) * (TILE / 2)
    B = B - np.floor(B.min(1, keepdims=True) / (TILE / 2)) * (TILE / 2)
    A = np.clip(A, 0.5, TILE - 0.5)
    B = np.clip(B, 0.5, TILE - 0.5)
    x0, y0 = tile_origin(mat)
    return np.stack([(x0 + A) / ATLAS, (y0 + B) / ATLAS], -1)


def pattern(mat, spec, rng):
    """A material's 64 px pattern (2 m), periodic, as floats 0-255."""
    n = 64
    sd = int(rng.integers(1 << 30))
    noise = periodic_noise(n, 1.2, sd)
    fine = periodic_noise(n, 0.4, sd + 1)
    yy, xx = np.mgrid[0:n, 0:n]
    col = {
        'paint': spec.get('paint', (120, 120, 120)), 'paint_dark': spec.get('paint_dark', (90, 90, 90)),
        'rim': spec.get('rim', (200, 200, 200)), 'engine': shade(spec.get('paint', (90, 90, 90)), 0.8),
        'black': (34, 34, 36), 'rubber': (30, 30, 31), 'steel': (120, 122, 124), 'chrome': (200, 204, 208),
        'rust': (118, 62, 32), 'seat': spec.get('seat', (40, 40, 42)), 'lens': (236, 236, 222), 'red_lens': (190, 24, 24),
        'amber_lens': (232, 140, 20), 'smoked': (40, 46, 52), 'treadplate': (150, 152, 154), 'wood': (148, 118, 82),
        'wood_dark': (98, 76, 52), 'bale_side': (196, 172, 104), 'bale_end': (184, 160, 92), 'twine': (220, 120, 30),
        'straw': (204, 182, 112), 'white': spec.get('paint', (232, 230, 224)), 'alu': (186, 190, 194),
        'alu_tread': (196, 200, 204), 'interior': (40, 38, 36), 'galv': (150, 156, 158), 'canopy': spec.get('rim', (220, 200, 60)),
        'char_rubber': (26, 25, 24),
    }[mat]
    base = np.ones((n, n, 3)) * np.array(col, float)
    k = 1 + noise[..., None] * 0.02 + fine[..., None] * 0.015
    if mat in ('treadplate', 'alu_tread'):
        # diamond plate: raised lugs in a herringbone
        u, v = xx % 6, yy % 6
        lug = ((xx // 6 + yy // 6) % 2 == 0) & (np.abs(u - v) <= 1) | ((xx // 6 + yy // 6) % 2 == 1) & (np.abs(u + v - 5) <= 1)
        k = k + lug[..., None] * 0.16 - 0.04
    elif mat in ('wood', 'wood_dark'):
        grain = periodic_noise(n, 2.4, sd + 2, n)
        k = 1 + (grain * 0.10 + fine * 0.05)[..., None]
        k = k * (1 + (np.sin(xx / n * 2 * np.pi * 9 + grain * 2) * 0.05)[..., None])
    elif mat in ('bale_side', 'straw'):
        # straw laid along, each stalk a streak
        st = periodic_noise(n, 0.3, sd + 4, n)
        streak = np.roll(st, 0, 0)
        k = 1 + (np.repeat(streak.mean(1, keepdims=True), n, 1) * 0.12 + fine * 0.08)[..., None]
        k = k + ((yy % 5 == 0) * -0.08 + (xx % 13 == 0) * 0.05)[..., None]
    elif mat == 'bale_end':
        # the cut face: flakes, stalk ends dotted dark and light
        dots = fine > 1.2
        k = 1 + noise[..., None] * 0.1 - dots[..., None] * 0.25 + (fine < -1.4)[..., None] * 0.15
        k = k - (((yy // 9) % 2 == 0) & (yy % 9 == 0))[..., None] * 0.15
    elif mat == 'rubber':
        k = 1 + fine[..., None] * 0.05 + (yy % 4 == 0)[..., None] * -0.05
    elif mat == 'char_rubber':
        crack = np.abs(periodic_noise(n, 1.6, sd + 5)) < 0.12
        k = 1 + fine[..., None] * 0.1 + crack[..., None] * 0.9
    elif mat in ('steel', 'alu', 'chrome', 'galv'):
        k = 1 + periodic_noise(n, 1.8, sd + 3)[..., None] * 0.06 + (np.sin(yy / n * 2 * np.pi * 3) * 0.04)[..., None]
        if mat == 'galv':
            k = k + (periodic_noise(n, 0.9, sd + 6) > 0.8)[..., None] * 0.1
    elif mat == 'seat':
        k = k - (yy % 10 == 0)[..., None] * 0.12
    elif mat == 'canopy':
        k = k + (xx % 16 == 0)[..., None] * -0.1
    elif mat == 'rust':
        k = 1 + noise[..., None] * 0.15 + fine[..., None] * 0.1
    return np.clip(base * k, 0, 255)


def text(d, xy, s, size, fill, anchor='la', bold=True):
    d.text(xy, s, font=font(size, bold), fill=tuple(int(c) for c in fill), anchor=anchor)


def decal(name, spec, w, hh):
    paint = tuple(spec.get('paint', (120, 120, 120)))
    im = Image.new('RGB', (w, hh), paint)
    d = ImageDraw.Draw(im)
    left = name.endswith('_l')
    if name in ('hood_l', 'hood_r'):
        stripe = tuple(spec.get('stripe', (230, 220, 200)))
        # the stripe along the hood's shoulder, the maker's name in it, the louvred front panel
        d.rectangle([0, 6, w, 20], fill=stripe)
        d.rectangle([0, 22, w, 24], fill=stripe)
        lo = (8, 34, 150, hh - 10) if left else (w - 150, 34, w - 8, hh - 10)
        d.rectangle(lo, fill=shade(paint, 0.85))
        for y in range(lo[1] + 6, lo[3] - 4, 7):
            d.rectangle([lo[0] + 8, y, lo[2] - 8, y + 3], fill=(24, 24, 24))
            d.line([lo[0] + 8, y + 4, lo[2] - 8, y + 4], fill=shade(paint, 1.2))
        tx = 330 if left else w - 330
        text(d, (tx, 52), spec.get('maker', ''), 30, stripe, 'mm')
        text(d, (tx, 76), spec.get('model', ''), 18, stripe, 'mm')
        d.line([0, hh - 3, w, hh - 3], fill=shade(paint, 0.7), width=2)
    elif name == 'grille':
        im = Image.new('RGB', (w, hh), (22, 22, 22))
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, w - 1, hh - 1], outline=paint, width=6)
        for x in range(12, w - 10, 9):
            d.rectangle([x, 8, x + 3, hh - 8], fill=shade(paint, 0.9))
        for y in range(30, hh - 8, 26):
            d.rectangle([8, y, w - 8, y + 3], fill=shade(paint, 0.75))
        d.rectangle([20, 10, w - 20, 26], fill=tuple(spec.get('stripe', paint)))
        text(d, (w // 2, 18), spec.get('maker', '')[:7], 12, (20, 20, 20), 'mm')
    elif name == 'dash':
        im = Image.new('RGB', (w, hh), (30, 30, 32))
        d = ImageDraw.Draw(im)
        for i, (lab, frac) in enumerate((('TEMP', 0.55), ('RPM', 0.3), ('OIL', 0.65), ('FUEL', 0.2))):
            cx, cy, r = 34 + i * 62, 46, 25
            d.ellipse([cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3], fill=(150, 150, 150))
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(232, 228, 214))
            for t in range(9):
                a = math.radians(210 - t * 30)
                d.line([cx + (r - 6) * math.cos(a), cy - (r - 6) * math.sin(a), cx + r * math.cos(a), cy - r * math.sin(a)], fill=(20, 20, 20))
            a = math.radians(210 - frac * 240)
            d.line([cx, cy, cx + (r - 4) * math.cos(a), cy - (r - 4) * math.sin(a)], fill=(200, 30, 20), width=2)
            text(d, (cx, cy + 12), lab, 8, (20, 20, 20), 'mm')
        d.ellipse([10, 78, 20, 88], fill=(120, 120, 120))
        d.rectangle([w - 40, 78, w - 14, 88], fill=(60, 60, 60))
    elif name == 'smv':
        im = Image.new('RGB', (w, hh), (30, 30, 30))
        d = ImageDraw.Draw(im)
        d.polygon([(w / 2, 1), (1, hh - 1), (w - 1, hh - 1)], fill=(200, 30, 24))
        d.polygon([(w / 2, 16), (13, hh - 8), (w - 13, hh - 8)], fill=(250, 110, 20))
    elif name == 'plate':
        im = Image.new('RGB', (w, hh), (226, 224, 210))
        d = ImageDraw.Draw(im)
        d.rectangle([1, 1, w - 2, hh - 2], outline=(40, 60, 110), width=2)
        text(d, (w // 2, 8), 'FARM', 9, (40, 60, 110), 'mm')
        text(d, (w // 2, 28), 'GH 4471', 16, (30, 30, 34), 'mm')
    elif name in ('fender_l', 'fender_r'):
        im = Image.new('RGB', (w, hh), paint)
        d = ImageDraw.Draw(im)
        d.line([0, 2, w, 2], fill=shade(paint, 1.2), width=2)
        tx = 40 if left else w - 40
        d.rectangle([tx - 30, 20, tx + 30, 44], fill=(236, 200, 30))
        text(d, (tx, 32), 'DANGER', 9, (20, 20, 20), 'mm')
    elif name in ('trailer_l', 'trailer_r'):
        im = Image.new('RGB', (w, hh), paint)
        d = ImageDraw.Draw(im)
        s1, s2 = tuple(spec.get('stripe', (110, 28, 40))), tuple(spec.get('stripe2', (46, 64, 96)))
        # the swoosh: two bands sweeping up toward the front
        for yb, col, th in ((44, s1, 14), (62, s2, 8)):
            pts = []
            for x in range(0, w + 1, 16):
                f = x / w if not left else 1 - x / w
                y = yb + 26 * f * f
                pts.append((x, y))
            d.polygon(pts + [(x, y + th) for x, y in reversed(pts)], fill=col)
        fx = 330 if left else w - 330
        text(d, (fx, 24), spec.get('maker', ''), 22, s1, 'mm')
        text(d, (fx + (160 if left else -160), 24), spec.get('model', ''), 10, s2, 'mm')
        # rivet rows along top and bottom, seams with their rivets every panel
        for x in range(4, w, 10):
            d.point([(x, 3), (x, hh - 4)], fill=shade(paint, 0.7))
        for x in range(0, w, 96):
            d.line([x, 0, x, hh], fill=shade(paint, 0.88))
            for y in range(6, hh, 12):
                d.point([(x + 2, y)], fill=shade(paint, 0.7))
        if left:
            # the tack door's outline, its hinges ahead (the door from z 1.0 to 1.85)
            zf, zb = 2.28, -2.30
            u0, u1 = (zf - 1.85) / (zf - zb) * w, (zf - 1.0) / (zf - zb) * w
            d.rectangle([u0, 14, u1, hh - 10], outline=shade(paint, 0.55), width=2)
            d.rectangle([u0 + 3, 17, u1 - 3, hh - 13], outline=shade(paint, 1.1), width=1)
        d.line([0, hh - 1, w, hh - 1], fill=shade(paint, 0.6), width=2)
    return np.asarray(im, float)


def build_atlas(spec, rng):
    img = np.zeros((ATLAS, ATLAS, 3))
    for name, (x, y, w, hh) in DECALS.items():
        img[y:y + hh, x:x + w] = decal(name, spec, w, hh)
    for mat in MATERIALS:
        p = pattern(mat, spec, rng)
        x, y = tile_origin(mat)
        img[y:y + TILE, x:x + TILE] = np.tile(p, (2, 2, 1))
    return img


def glassy_mask():
    m = np.zeros((ATLAS, ATLAS), bool)
    for mat in GLASSY:
        x, y = tile_origin(mat)
        m[y:y + TILE, x:x + TILE] = True
    return m


def weather(img, state, rng):
    """Grime and dust for a usable machine; sun-faded paint, grime and rust breaking through for an
    abandoned or wrecked one; char with rust and ash for a burnt one."""
    sd = int(rng.integers(1 << 30))
    grime = periodic_noise(ATLAS, 1.5, sd)
    fine = periodic_noise(ATLAS, 0.6, sd + 1)
    spots = periodic_noise(ATLAS, 1.1, sd + 2)
    out = img.copy()
    keep = glassy_mask()[..., None]
    if state == 'usable':
        out = out * (1 - 0.03 * np.clip(grime, 0, 3)[..., None]) + np.clip(fine, 0, 2)[..., None] * 2
        return np.clip(np.where(keep, img, out), 0, 255)
    heavy = state == 'wrecked'
    if state in ('abandoned', 'wrecked'):
        grey = out.mean(2, keepdims=True)
        out = out * 0.7 + grey * 0.3 + 14                                   # sun-faded
        out = out * (1 - (0.10 if heavy else 0.07) * np.clip(grime + 0.5, 0, 3)[..., None])
        rust = np.clip((spots + fine * 0.35 - (0.9 if heavy else 1.25)) * 2.2, 0, 1)[..., None]
        rc = np.array([116, 60, 30], float) * (1 + fine[..., None] * 0.08)
        out = out * (1 - rust) + rc * rust
        dust = np.clip(fine * 0.5 + 0.3, 0, 1)[..., None] * 0.08
        out = out * (1 - dust) + np.array([150, 136, 112]) * dust
        return np.clip(np.where(keep, img * 0.75, out), 0, 255)
    # burnt
    char = np.array([34, 31, 29], float) * (1 + grime[..., None] * 0.15)
    rust = np.clip((spots - 0.9) * 1.2, 0, 0.8)[..., None]
    ash = np.clip((fine - 1.0) * 2, 0, 1)[..., None]
    out = char * (1 - rust) + np.array([84, 48, 30]) * rust
    out = out * (1 - ash * 0.6) + np.array([150, 146, 140]) * ash * 0.6
    return np.clip(out, 0, 255)


# ---------------------------------------------------------------- states and packing

def state_items(g, state, rng):
    """The triangles a state keeps: [(triangle, material, decal uvs, wheel)], materials swapped for
    what's left of them (cracked rubber on a burnt one)."""
    G = np.array(g.G)
    M = np.array(g.M)
    keep = np.ones(len(g.T), bool)
    if state in ('wrecked', 'burnt'):
        keep &= G != 'glass'
        keep &= G != 'cap'
    if state == 'wrecked':
        keep &= G != 'backrest'
        keep &= G != ('hoodside_r' if rng.random() < 0.5 else 'hoodside_l')
        if rng.random() < 0.5:
            keep &= G != 'canopy'
    if state == 'burnt':
        keep &= ~np.isin(G, ['cushion', 'backrest', 'loose', 'canopy', 'bales', 'straw', 'lugs'])
        cen = np.array(g.T).mean(1)
        torn = periodic_noise(64, 1.2, int(rng.integers(1 << 30)))
        ix = (np.floor(cen[:, 0] * 5) % 64).astype(int)
        iz = (np.floor(cen[:, 2] * 5) % 64).astype(int)
        keep &= ~(np.isin(G, ['boards', 'rack']) & (torn[iz, ix] > 0.2))
    items = []
    for i in np.where(keep)[0]:
        mat = M[i]
        if state == 'burnt' and mat == 'rubber':
            mat = 'char_rubber'
        if state == 'burnt' and mat in ('seat', 'smoked', 'lens', 'red_lens', 'amber_lens'):
            mat = 'black'
        items.append((g.T[i], mat, g.UV[i], g.W[i]))
    return items


def mesh_bytes(T, UV, glass, sel, centre):
    order = np.concatenate([np.where(sel & ~glass)[0], np.where(sel & glass)[0]])
    Q = np.round((T[order] - centre) * 1000).astype('>i2').reshape(-1, 3)
    U = np.round(np.clip(UV[order], 0, 1) * 65535).astype('>u2').reshape(-1, 2)
    head = struct.pack('>ii', int((sel & ~glass).sum()), int((sel & glass).sum()))
    return head + np.concatenate([Q.view('>u2'), U], 1).astype('>u2').tobytes()


def build(key, spec, preview=None):
    spec = dict(spec, id=key)
    g, wheels, meta = spec['build'](spec)
    print(f'{key}: {len(g.T)} triangles', flush=True)
    T0 = np.array(g.T)
    atlas = build_atlas(spec, np.random.default_rng(java_hash(key) & 0xFFFFF))
    variants = []
    for state in VARIANTS:
        rng = np.random.default_rng(java_hash(key + state) & 0xFFFFF)
        tris, uvs, glass, wh = [], [], [], []
        for t, mat, uvx, w in state_items(g, state, rng):
            if uvx is not None:
                x0, y0, w_, hh = DECALS[mat]
                uvs.append(np.stack([(x0 + 0.5 + uvx[:, 0] * (w_ - 1)) / ATLAS, (y0 + 0.5 + uvx[:, 1] * (hh - 1)) / ATLAS], -1))
                tris.append(t)
                glass.append(False)
                wh.append(w)
            else:
                for p in subdivide([t], None):
                    uvs.append(tile_uv(p[None], mat)[0])
                    tris.append(p)
                    glass.append(mat in ('smoked',) and state != 'burnt')
                    wh.append(w)
        img = weather(atlas, state, rng)
        im = Image.fromarray(img.astype(np.uint8))
        variants.append(dict(state=state, T=np.array(tris), UV=np.array(uvs), glass=np.array(glass), W=np.array(wh), img=im))
        if preview:
            os.makedirs(preview, exist_ok=True)
            im.save(os.path.join(preview, f'{key}_{state}.png'))
    base = variants[0]['T']
    lo, hi = base.reshape(-1, 3).min(0), base.reshape(-1, 3).max(0)
    centre = np.array([(lo[0] + hi[0]) / 2, 0, (lo[2] + hi[2]) / 2])
    buf = io.BytesIO()
    buf.write(b'ZCAR')
    buf.write(struct.pack('>ii', 3, len(variants)))
    for v in variants:
        jpg = io.BytesIO()
        v['img'].save(jpg, 'JPEG', quality=88, subsampling=0)
        jpg = jpg.getvalue()
        nb = v['state'].encode()
        buf.write(struct.pack('>H', len(nb)))
        buf.write(nb)
        buf.write(struct.pack('>i', len(jpg)))
        buf.write(jpg)
        buf.write(mesh_bytes(v['T'], v['UV'], v['glass'], v['W'] < 0, centre))
        buf.write(struct.pack('>i', 0))
        buf.write(struct.pack('>i', len(wheels)))
        for k, (c, r, steers) in enumerate(wheels):
            buf.write(struct.pack('>4fb', *(np.asarray(c, float) - centre), r, 1 if steers else 0))
            buf.write(mesh_bytes(v['T'], v['UV'], v['glass'], v['W'] == k, centre))
    blob_ = scramble(zlib.compress(buf.getvalue(), 9), key)
    with open(os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/cars', key + '.zcar'), 'wb') as f:
        f.write(blob_)
    # what can be stood on: the body (not glass, the loose hoses and chains, or thin rails)
    solid = np.array([grp not in ('glass', 'loose', 'cap', 'straw') for grp in g.G])
    S = T0[solid] - centre
    glo, nx, nz, grid = heights(S)
    size = hi - lo
    print(f'{key:20s} {len(blob_) // 1024:5d} KB  {size[0]:.1f} x {size[1]:.1f} x {size[2]:.1f} m  '
          + '  '.join(f'{v["state"]} {len(v["T"])}' for v in variants), flush=True)
    out = {'id': key, 'kind': 'farm', 'weight': spec['weight'], 'size': [round(float(s), 3) for s in size],
           'cell': CELL, 'x0': round(float(glo[0]), 3), 'z0': round(float(glo[2]), 3), 'nx': nx, 'nz': nz,
           'heights': [int(round(max(0.0, hh) * 100)) for hh in grid.ravel()],
           'seats': [[round(float(a - b), 3) for a, b in zip(s, centre)] for s in meta['seats']],
           'wheelbase': round(float(meta['wheelbase']), 3)}
    if meta.get('handles'):
        out['handles'] = [[round(float(a - b), 3) for a, b in zip(h, centre)] for h in meta['handles']]
    if meta.get('steering'):
        st = meta['steering']
        out['steering'] = [round(float(st[i] - centre[i]), 3) for i in range(3)] + [round(float(x), 3) for x in st[3:]]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default=None)
    ap.add_argument('--preview', default=None, help="also write each state's atlas here")
    a = ap.parse_args()
    models = []
    for key, spec in SPECS.items():
        if a.only and key != a.only:
            continue
        models.append(build(key, spec, a.preview))
    idx = os.path.join(ROOT, 'src/main/resources/data/zomboidcraft/zc_cars/models.json')
    data = json.load(open(idx))
    ids = {m['id'] for m in models}
    data['models'] = [m for m in data['models'] if m['id'] not in ids] + models
    data['models'].sort(key=lambda m: m['id'])
    with open(idx, 'w') as f:
        json.dump(data, f, separators=(',', ':'))
    print(len(models), 'farm machines')


if __name__ == '__main__':
    main()
