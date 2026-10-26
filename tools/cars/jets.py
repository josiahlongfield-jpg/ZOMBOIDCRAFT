#!/usr/bin/env python3
"""Builds the fighter jets parked on the aircraft carrier, from scratch (no source models).

    python3 tools/cars/jets.py [--only corvid] [--preview OUT_DIR]

writes src/main/resources/assets/zomboidcraft/cars/<id>.zcar, the packed format pack.py uses
for the cars, and puts the jets into data/zomboidcraft/zc_cars/models.json with kind "jet"
(pack.py keeps them when it rewrites the cars). Two airframes, at full size:
    jet_corvid     a single-seat stealth fighter: chined nose, caret intakes, diamond wing,
                   canted twin tails and flat nozzles
    jet_gyrfalcon  a two-seat heavy fighter: box intakes, swept wing, upright twin tails,
                   round nozzles, a belly tank and missiles

Each is lofted from cross-sections (fuselage, intakes, wings, tails) with a cockpit tub, canopy,
seats and panels, landing gear and doors, a tailhook and launch bar for the carrier. The airframe
is textured from six blueprint views (top, bottom, both sides, front, back): each triangle uses
the view its normal faces most, and each view is painted texel by texel from the point on the jet
it lands on, so panel lines, control surfaces, markings and weathering line up across parts.
Small parts (gear, seats, stores, glass) use flat material tiles.

The four states are the cars': usable (clean, canopy shut), abandoned (weathered, canopy up,
intake covers in), wrecked (nose gear collapsed, canopy smashed, scorched, a stabilator gone)
and burnt (a charred shell). Model space is the cars': metres, wheels on y = 0, nose toward +z,
the pilot's left toward +x.
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
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from pack import CELL, VARIANTS, heights, java_hash, scramble  # noqa: E402
from wreck import fbm, sstep, vnoise, voronoi_edge  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
AW, AH = 2048, 1024
PX = 40  # texels per metre in the blueprint views
XMAX, YMAX, ZMAX, ZMIN = 7.2, 6.0, 0.5, -19.5
# the blueprint views: where each sits in the atlas, and its size in texels
VIEWS = {
    'top': (0, 0, 576, 800), 'bottom': (576, 0, 576, 800),
    'left': (1152, 0, 800, 240), 'right': (1152, 240, 800, 240),
    'front': (1152, 480, 576, 240), 'back': (1152, 720, 576, 240),
}
TILE = 32
TILE_ORIGIN = (0, 808)  # flat material tiles fill the strip under the plan views


def font(size, bold=True):
    for name in ('DejaVuSansCondensed-Bold.ttf', 'DejaVuSans-Bold.ttf') if bold else ('DejaVuSans.ttf',):
        for d in ('/usr/share/fonts/truetype/dejavu',):
            p = os.path.join(d, name)
            if os.path.exists(p):
                return ImageFont.truetype(p, size)
    return ImageFont.load_default()


# ---------------------------------------------------------------- geometry

def view_of(n):
    """The blueprint view a face with normal n uses."""
    a = np.abs(n)
    k = a.argmax(1)
    out = np.empty(len(n), object)
    out[(k == 1) & (n[:, 1] >= 0)] = 'top'
    out[(k == 1) & (n[:, 1] < 0)] = 'bottom'
    out[(k == 0) & (n[:, 0] >= 0)] = 'left'
    out[(k == 0) & (n[:, 0] < 0)] = 'right'
    out[(k == 2) & (n[:, 2] >= 0)] = 'front'
    out[(k == 2) & (n[:, 2] < 0)] = 'back'
    return out


def project(view, P):
    """Texel coordinates (u, v) in a view and the depth toward its viewer (smaller is nearer)."""
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    if view == 'top':
        return (XMAX - x) * PX, (ZMAX - z) * PX, -y
    if view == 'bottom':
        return (x + XMAX) * PX, (ZMAX - z) * PX, y
    if view == 'left':
        return (ZMAX - z) * PX, (YMAX - y) * PX, -x
    if view == 'right':
        return (z - ZMIN) * PX, (YMAX - y) * PX, x
    if view == 'front':
        return (x + XMAX) * PX, (YMAX - y) * PX, -z
    return (XMAX - x) * PX, (YMAX - y) * PX, z


class Mesh:
    """A triangle soup: positions, a part name per triangle, and how each is textured."""

    def __init__(self):
        self.T, self.UV, self.part, self.mat, self.glass = [], [], [], [], []

    def add(self, T, part, mat=None, glass=False, orient='volume', centre=None, uv=None):
        """mat None: the airframe's blueprint views; else a material tile's name. uv: explicit
        atlas uvs per vertex (n, 3, 2) instead. orient: 'volume' for a closed shell, 'centre'
        to face away from a point, 'inward' to face toward it, None to keep the winding."""
        T = np.asarray(T, float).reshape(-1, 3, 3)
        uv = None if uv is None else np.asarray(uv, float).reshape(-1, 3, 2)
        n = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
        keep = np.linalg.norm(n, axis=1) > 1e-8
        T, n = T[keep], n[keep]
        if uv is not None:
            uv = uv[keep]
        if not len(T):
            return
        flip = np.zeros(len(T), bool)
        if orient == 'volume':
            flip[:] = np.einsum('ij,ij->i', T[:, 0], np.cross(T[:, 1], T[:, 2])).sum() < 0
        elif orient in ('centre', 'inward'):
            dot = np.einsum('ij,ij->i', n, T.mean(1) - np.asarray(centre, float))
            flip = dot < 0 if orient == 'centre' else dot > 0
        T[flip] = T[flip][:, ::-1]
        if uv is not None:
            uv[flip] = uv[flip][:, ::-1]
        self.T.append(T)
        self.UV.append(uv if uv is not None else np.full(T.shape[:2] + (2,), np.nan))
        self.part += [part] * len(T)
        self.mat += [mat] * len(T)
        self.glass += [glass] * len(T)

    def drop_last(self):
        n = len(self.T.pop())
        self.UV.pop()
        del self.part[-n:], self.mat[-n:], self.glass[-n:]

    def arrays(self):
        return (np.concatenate(self.T), np.concatenate(self.UV), np.array(self.part, object), np.array(self.mat, object),
                np.array(self.glass))

    def transform(self, fn):
        for i, T in enumerate(self.T):
            self.T[i] = fn(T)


def loft(rings, cap0=True, cap1=True):
    """Triangles skinning a run of rings (S, N, 3), each a closed loop, with fan caps at the ends."""
    R = np.asarray(rings, float)
    A, B = R[:-1], np.roll(R[:-1], -1, 1)
    C, D = np.roll(R[1:], -1, 1), R[1:]
    tris = [np.stack([A, B, C], 2).reshape(-1, 3, 3), np.stack([A, C, D], 2).reshape(-1, 3, 3)]
    for cap, ring in ((cap0, R[0]), (cap1, R[-1])):
        if cap:
            c = np.repeat(ring.mean(0)[None], len(ring), 0)
            tris.append(np.stack([c, np.roll(ring, -1, 0), ring], 1))
    return np.concatenate(tris)


def loft_labels(S, N, label, cap0=True, cap1=True):
    """The label(s, j) of every triangle loft() makes for S rings of N points (caps get None)."""
    L = np.array([[label(s, j) for j in range(N)] for s in range(S - 1)], object).reshape(-1)
    out = [L, L]
    out += [np.array([None] * N, object)] * (int(cap0) + int(cap1))
    return np.concatenate(out)


def box(lo, hi):
    """A cuboid's twelve triangles."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    v = np.array([[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0],
                  [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]], float)
    f = [(0, 2, 1), (0, 3, 2), (4, 5, 6), (4, 6, 7), (0, 1, 5), (0, 5, 4),
         (3, 6, 2), (3, 7, 6), (0, 4, 7), (0, 7, 3), (1, 2, 6), (1, 6, 5)]
    return v[np.array(f)]


def beam(a, b, w, h=None, up=(0, 1, 0)):
    """A square-ish bar from a to b, w wide and h tall."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    h = w if h is None else h
    d = b - a
    d /= np.linalg.norm(d)
    s = np.cross(d, up)
    if np.linalg.norm(s) < 1e-6:
        s = np.cross(d, (1, 0, 0))
    s /= np.linalg.norm(s)
    u = np.cross(s, d)
    corners = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
    rings = [[p + s * cx * w / 2 + u * cy * h / 2 for cx, cy in corners] for p in (a, b)]
    return loft(rings)


def tube(a, b, r0, r1=None, n=14, cap0=True, cap1=True):
    """A round bar or cylinder from a to b, radius r0 tapering to r1."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    r1 = r0 if r1 is None else r1
    d = b - a
    d /= np.linalg.norm(d)
    s = np.cross(d, (0, 1, 0))
    if np.linalg.norm(s) < 1e-6:
        s = np.cross(d, (1, 0, 0))
    s /= np.linalg.norm(s)
    u = np.cross(s, d)
    t = np.linspace(0, 2 * np.pi, n, endpoint=False)
    rings = [p + r * (np.outer(np.cos(t), s) + np.outer(np.sin(t), u)) for p, r in ((a, r0), (b, r1))]
    return loft(rings, cap0, cap1)


def wheel(c, r, w, n=18):
    """A tyre on its rim: axle along x through c."""
    c = np.asarray(c, float)
    t = np.linspace(0, 2 * np.pi, n, endpoint=False)
    out = []
    # the tread, its rounded shoulders, and the sidewalls stepping in to the hub
    prof = [(-w / 2, r * 0.62), (-w / 2, r * 0.9), (-w * 0.42, r * 0.98), (w * 0.42, r * 0.98), (w / 2, r * 0.9), (w / 2, r * 0.62)]
    rings = [np.stack([np.full(n, c[0] + px), c[1] + pr * np.sin(t), c[2] + pr * np.cos(t)], 1) for px, pr in prof]
    tyre = loft(rings, False, False)
    hub = np.concatenate([tube(c + (-w / 2 - 0.005, 0, 0), c + (-w / 2 + 0.06, 0, 0), r * 0.62, r * 0.55, n),
                          tube(c + (w / 2 - 0.06, 0, 0), c + (w / 2 + 0.005, 0, 0), r * 0.55, r * 0.62, n)])
    out.append(('tyre', tyre))
    out.append(('hub', hub))
    return out


def section(w, top, bot, cy, n=2.4, nb=None, N=48):
    """A fuselage cross-section: a superellipse w wide each side, top above and bot below the
    chine line at height cy; n near 1 gives a sharp chine, 2 a round section, more a boxy one."""
    nb = n if nb is None else nb
    t = 2 * np.pi * np.arange(N) / N
    c, s = np.cos(t), np.sin(t)
    x = w * np.sign(c) * np.abs(c) ** (2 / n)
    y = np.where(s >= 0, cy + top * np.abs(s) ** (2 / n), cy - bot * np.abs(s) ** (2 / nb))
    return np.stack([x, y], 1)


def interp(table, d):
    """Values from a station table [(d, v0, v1, ...)] at d, by monotone (PCHIP-like) smoothing."""
    tab = np.asarray(table, float)
    ds = tab[:, 0]
    out = []
    for k in range(1, tab.shape[1]):
        out.append(np.interp(d, ds, tab[:, k]))
    # a light smoothing pass: Catmull-Rom between the stations
    i = np.clip(np.searchsorted(ds, d) - 1, 0, len(ds) - 2)
    t = (d - ds[i]) / (ds[i + 1] - ds[i])
    res = []
    for k in range(1, tab.shape[1]):
        p0 = tab[max(i - 1, 0), k]
        p1, p2 = tab[i, k], tab[i + 1, k]
        p3 = tab[min(i + 2, len(ds) - 1), k]
        cr = 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3)
        lo, hi = min(p1, p2), max(p1, p2)
        res.append(float(np.clip(cr, lo - 0.25 * (hi - lo), hi + 0.25 * (hi - lo))))
    return res


def naca(f, t):
    """Half-thickness of a symmetric NACA section at chord fraction f, t the thickness ratio."""
    return 5 * t * (0.2969 * np.sqrt(f) - 0.1260 * f - 0.3516 * f ** 2 + 0.2843 * f ** 3 - 0.1036 * f ** 4)


class Surface:
    """A lifting surface (wing, stabilator, fin) on the jet's left side: from root to tip along
    span direction sdir, a leading and trailing edge station (d, aft from the nose) at each end,
    thickness ratios and the root's (x, y). Mirrored to the right in build."""

    def __init__(self, root, tip_s, le, te, thick, sdir=(1, 0, 0), dihedral=0.0, d_shift=0.0):
        self.root = np.asarray(root, float)  # (x, y) of the root
        self.span = tip_s
        self.le, self.te = le, te  # (root, tip) d of the edges
        self.thick = thick  # (root, tip) thickness ratios
        a = math.radians(dihedral)
        sd = np.asarray(sdir, float)
        if dihedral:
            sd = np.array([math.cos(a), math.sin(a), 0])
        self.sdir = sd / np.linalg.norm(sd)

    def edges(self, s):
        k = s / self.span
        return self.le[0] + (self.le[1] - self.le[0]) * k, self.te[0] + (self.te[1] - self.te[0]) * k

    def point(self, s, f, side):
        """The point at span s, chord fraction f (0 leading edge), upper (+1) or lower (-1) skin."""
        le, te = self.edges(s)
        chord = te - le
        th = self.thick[0] + (self.thick[1] - self.thick[0]) * s / self.span
        base = np.array([self.root[0], self.root[1], 0.0]) + self.sdir * s
        normal = np.cross(self.sdir, (0, 0, 1))  # up for a wing, outboard for a fin
        if normal[1] < 0 or (abs(normal[1]) < 1e-6 and normal[0] < 0):
            normal = -normal
        p = base + normal * side * naca(f, th) * chord
        p[2] = -(le + f * chord)
        return p

    def mesh(self, stations=10, chord_pts=13, s0=0.0):
        f = (1 - np.cos(np.linspace(0, np.pi, chord_pts))) / 2
        rings = []
        for s in np.linspace(s0, self.span, stations):
            up = [self.point(s, fi, 1) for fi in f]
            lo = [self.point(s, fi, -1) for fi in f[::-1][1:-1]]
            rings.append(np.array(up + lo))
        return loft(rings)

    def param(self, P):
        """Span fraction and chord fraction of points P on the left copy (x >= 0)."""
        rel = P - np.array([self.root[0], self.root[1], 0.0])
        s = rel @ self.sdir
        k = np.clip(s / self.span, -0.2, 1.2)
        le = self.le[0] + (self.le[1] - self.le[0]) * k
        te = self.te[0] + (self.te[1] - self.te[0]) * k
        f = (-P[..., 2] - le) / (te - le)
        return k, f


def mirror(T):
    M = T.copy()
    M[..., 0] *= -1
    return M[:, ::-1]


# ---------------------------------------------------------------- the airframes

class Jet:
    """Everything a jet's builder and painter share."""
    id = ''
    seats = []

    def __init__(self, state):
        self.state = state
        self.m = Mesh()
        self.surfaces = {}
        self.rng = np.random.default_rng(java_hash(self.id + state) & 0xFFFF)

    # the fuselage: stations (d, w, top, bot, cy, n, nb)
    body = []
    tub = None  # (d0, d1, floor y): the cockpit's well
    N = 48

    def ring(self, d):
        w, top, bot, cy, n, nb = interp(self.body, d)
        return section(w, top, bot, cy, max(n, 1.05), max(nb, 1.05), self.N)

    def tub_range(self):
        """Ring indices that drop to the cockpit floor (the top centre), fixed for every station."""
        t = 2 * np.pi * np.arange(self.N) / self.N
        return np.where((t > math.radians(56)) & (t < math.radians(124)))[0]

    def fuselage(self):
        d0, d1 = self.body[0][0], self.body[-1][0]
        ds = list(np.arange(d0, d1, 0.2)) + [d1]
        if self.tub:
            a, b, _ = self.tub
            ds = sorted(set([round(v, 3) for v in ds if not (a - 0.05 < v < a + 0.05 or b - 0.05 < v < b + 0.05)]
                            + [a - 0.03, a, b, b + 0.03]))
        idx = self.tub_range()
        k1, k2 = idx[0], idx[-1]
        rings, tubbed = [], []
        for d in ds:
            r = self.ring(d)
            inside = self.tub and self.tub[0] <= d <= self.tub[1]
            if inside:
                floor = self.tub[2]
                xr = r[k1 - 1, 0]
                r[k1:k2 + 1, 0] = np.linspace(xr, -xr, k2 - k1 + 1)
                r[k1:k2 + 1, 1] = floor
            rings.append(np.column_stack([r[:, 0], r[:, 1], np.full(self.N, -d)]))
            tubbed.append(inside)
        self.rim = [(d, rings[i][k1 - 1]) for i, d in enumerate(ds)]
        T = loft(rings[::-1])
        if np.einsum('ij,ij->i', T[:, 0], np.cross(T[:, 1], T[:, 2])).sum() < 0:
            T = T[:, ::-1]
        S = len(rings)
        rev = tubbed[::-1]

        def label(s, j):
            if (rev[s] or rev[s + 1]) and k1 - 1 <= j <= k2:
                return 'tub'
            return 'fuse'
        lab = loft_labels(S, self.N, label)
        self.m.add(T[lab != 'tub'], 'fuse', orient=None)
        self.m.add(T[lab == 'tub'], 'tub', 'cockpit', orient=None)

    def top_y(self, d, x):
        """The fuselage's (uncut) top surface height at d, x."""
        r = self.ring(d)
        top = r[r[:, 1] >= r[:, 1].mean()]
        o = np.argsort(top[:, 0])
        return float(np.interp(x, top[o, 0], top[o, 1]))

    def rim_at(self, d):
        """Where the cockpit sill is at d: (x, y) of its left edge."""
        ds = np.array([r[0] for r in self.rim])
        xs = np.array([r[1][0] for r in self.rim])
        ys = np.array([r[1][1] for r in self.rim])
        return float(np.interp(d, ds, xs)), float(np.interp(d, ds, ys))

    def surface(self, name, srf, stations=10, part=None, mat=None):
        self.surfaces[name] = srf
        T = srf.mesh(stations)
        self.m.add(T, part or name, mat)
        self.m.add(mirror(T), part or name, mat)

    def canopy(self, d0, d1, peak, apex, rear=None, bows=(), frame_w=0.06, state=None):
        """The bubble: from the windscreen's foot at d0 to its tail at d1, rising peak above the
        sill at apex (d). Shut, raised on its hinge at the back, smashed, or gone."""
        state = state or self.state
        ds = np.linspace(d0, d1, 26)
        rings = []
        for d in ds:
            xr, yr = self.rim_at(d)
            if d <= apex:
                h = peak * math.sin(min(1.0, (d - d0) / (apex - d0)) * math.pi / 2) ** 0.7
            else:
                h = peak * math.cos(min(1.0, (d - apex) / (d1 - apex)) * math.pi / 2) ** 0.9
            h = max(h, 0.015)
            a = np.linspace(0, np.pi, 15)
            xs = xr * 1.01 * np.cos(a)
            ys = yr + h * np.sin(a) ** 0.8
            if not (self.tub[0] <= d <= self.tub[1]):
                ys = np.maximum(ys, [self.top_y(d, xv) + 0.012 for xv in xs])
            ring = np.stack([xs, ys, np.full(15, -d)], 1)
            rings.append(ring)
        R = np.array(rings)
        # an open strip (no closing bottom): rings run across, from left sill to right
        A, B, C, D = R[:-1, :-1], R[:-1, 1:], R[1:, 1:], R[1:, :-1]
        glass = np.concatenate([np.stack([A, B, C], 2).reshape(-1, 3, 3), np.stack([A, C, D], 2).reshape(-1, 3, 3)])
        frame = []
        # the sill frame each side, the bows across
        for side in (0, 14):
            pts = R[:, side]
            for p, q in zip(pts[:-1], pts[1:]):
                frame.append(beam(p, q, frame_w, frame_w * 0.8))
        for bd in bows:
            i = int(np.argmin(np.abs(ds - bd)))
            for p, q in zip(R[i, :-1], R[i, 1:]):
                frame.append(beam(p, q, frame_w * 0.8, frame_w * 0.7, up=(0, 0, 1)))
        frame = np.concatenate(frame)
        centre = np.array([0, self.rim_at(apex)[1], -apex])
        hinge = np.array([0, self.rim_at(d1)[1] + 0.02, -d1 + 0.05])
        moving = rear if rear is not None else d0
        if state == 'abandoned':
            # raised on its hinge at the back, 38 degrees
            glass = rot_x(glass, hinge, math.radians(38), lambda P: -P[..., 2] > moving - 0.01)
            frame = rot_x(frame, hinge, math.radians(38), lambda P: -P[..., 2] > moving - 0.01)
            centre = rot_x(centre[None, None], hinge, math.radians(38))[0, 0]
        if state in ('usable', 'abandoned'):
            self.m.add(glass, 'canopy', 'glass_gold', glass=True, orient='centre', centre=centre - (0, 0.4, 0))
            # and its inside, so the pilot sees out through glass too
            self.m.add(glass, 'canopy', 'glass_gold', glass=True, orient='inward', centre=centre - (0, 0.4, 0))
        elif state == 'wrecked':
            # a few shards left in the frame
            keep = self.rng.random(len(glass)) < 0.22
            self.m.add(glass[keep], 'canopy', 'glass_gold', glass=True, orient='centre', centre=centre - (0, 0.4, 0))
        if state != 'burnt':
            self.m.add(frame, 'frame', 'frame')
        else:
            self.m.add(frame[: len(frame) // 2], 'frame', 'char_metal')

    def decal(self, quad):
        """The instrument-panel picture on a quad facing aft (corners: pilot's lower left,
        lower right, upper right, upper left)."""
        if self.state == 'burnt':
            return
        x0, y0, w, h = TILES_UV['mfd']
        uvq = np.array([[x0, y0 + h], [x0 + w, y0 + h], [x0 + w, y0], [x0, y0]], float) / (AW, AH)
        q = quad + (0, 0, -0.006)
        self.m.add(np.array([q[[0, 1, 2]], q[[0, 2, 3]]]), 'panel', 'mfd', orient=None,
                   uv=np.array([uvq[[0, 1, 2]], uvq[[0, 2, 3]]]))

    def seat(self, d, floor):
        """An ejection seat facing forward, its pan's front edge at d."""
        m = self.m
        m.add(box((-0.27, floor, -(d + 0.62)), (0.27, floor + 0.32, -d)), 'seat', 'seat_frame')
        m.add(box((-0.25, floor + 0.32, -(d + 0.6)), (0.25, floor + 0.42, -(d + 0.05))), 'seat', 'cushion')
        # the back, leaning 15 degrees, with the headbox on top
        back = box((-0.25, 0, -0.1), (0.25, 0.85, 0.0))
        back = rot_x(back, np.zeros(3), math.radians(-15)) + (0, floor + 0.42, -(d + 0.58))
        m.add(back, 'seat', 'cushion')
        head = box((-0.2, 0, -0.16), (0.2, 0.3, 0.0))
        head = rot_x(head, np.zeros(3), math.radians(-15)) + (0, floor + 1.25, -(d + 0.82))
        m.add(head, 'seat', 'seat_frame')
        for x in (-0.28, 0.24):
            m.add(box((x, floor + 0.3, -(d + 0.75)), (x + 0.04, floor + 1.3, -(d + 0.62))), 'seat', 'seat_frame')
        # the firing handle between the knees, yellow and black
        m.add(box((-0.07, floor + 0.38, -(d + 0.02)), (0.07, floor + 0.46, -(d - 0.02))), 'seat', 'yellow_black')
        self.eye = (0.0, floor + 1.02, -(d + 0.35))

    def panel(self, d, floor, top, half, side_stick=False, deep=0.55):
        """The instrument panel facing aft at d, glare shield over it, consoles down the sides."""
        m = self.m
        z = -d
        self.decal(np.array([[half, floor + 0.25, z], [-half, floor + 0.25, z], [-half, top, z], [half, top, z]]))
        m.add(box((-half, floor, z), (half, top, z + deep)), 'panel', 'panel_black')
        m.add(box((-half - 0.02, top, z - 0.08), (half + 0.02, top + 0.06, z + deep)), 'panel', 'panel_black')
        # the head-up display's glass on the shield
        hud = np.array([[0.13, top + 0.06, z + 0.02], [-0.13, top + 0.06, z + 0.02], [-0.13, top + 0.27, z - 0.06],
                        [0.13, top + 0.27, z - 0.06]])
        m.add(np.array([hud[[0, 1, 2]], hud[[0, 2, 3]], hud[[0, 2, 1]], hud[[0, 3, 2]]]), 'panel', 'glass_clear', glass=True,
              orient=None)
        m.add(box((-0.14, top + 0.04, z - 0.02), (0.14, top + 0.08, z + 0.16)), 'panel', 'panel_black')
        for sx in (1, -1):
            lo = np.array([sx * (half - 0.02), floor + 0.25, z - 1.7])
            m.add(box(np.minimum(lo, lo + (sx * -0.16, 0.22, 1.7)), np.maximum(lo, lo + (sx * -0.16, 0.22, 1.7))), 'panel',
                  'console')
        if side_stick:
            m.add(tube((-half + 0.1, floor + 0.47, z - 1.1), (-half + 0.1, floor + 0.66, z - 1.08), 0.022), 'panel', 'stick')
            m.add(tube((half - 0.1, floor + 0.47, z - 1.0), (half - 0.1, floor + 0.62, z - 1.05), 0.02), 'panel', 'stick')
        else:
            m.add(tube((0, floor, z - 0.75), (0, floor + 0.62, z - 0.70), 0.022), 'panel', 'stick')
            m.add(box((-0.04, floor + 0.58, z - 0.74), (0.04, floor + 0.7, z - 0.66)), 'panel', 'stick')

    def gear(self, d, x, r, width, top_y, steer=False, collapsed=False, flat=False):
        """A landing gear leg at d (a pair at +-x, or one on the centreline when x is 0): the
        strut down from the bay at top_y to the wheel, a drag brace, the bay and its doors."""
        if collapsed:
            return
        m = self.m
        rr = r * (0.84 if flat else 1.0)
        z = -d
        for sx in ((1, -1) if x else (1,)):
            cx = sx * x
            hub = np.array([cx, rr, z])
            if self.state == 'burnt':
                for _, T in wheel(hub, r * 0.62, width):
                    m.add(T, 'gear', 'char_metal', orient='centre', centre=hub)
            else:
                for kind, T in wheel(hub, rr, width):
                    m.add(T, 'gear', 'tyre' if kind == 'tyre' else 'hub', orient='centre', centre=hub)
            # the leg comes down beside the wheel (mains) or into a fork over it (nose)
            leg_x = cx - sx * (width / 2 + 0.07) if x else 0.0
            top = np.array([leg_x * (0.85 if x else 1), top_y + 0.1, z - (0.0 if x else 0.25)])
            low = np.array([leg_x, rr + (0.0 if x else r + 0.12), z])
            m.add(tube(top, low + (0, 0.45, 0), 0.08 if x else 0.065), 'gear', 'strut')
            m.add(tube(low + (0, 0.47, 0), low, 0.06 if x else 0.05), 'gear', 'strut_chrome')
            if x:
                m.add(tube(low, hub, 0.045), 'gear', 'strut')
            else:
                for fx in (-1, 1):
                    m.add(box((fx * (width / 2 + 0.06) - 0.025, rr, z - 0.05), (fx * (width / 2 + 0.06) + 0.025, low[1] + 0.02, z + 0.05)),
                          'gear', 'strut')
                m.add(box((-(width / 2 + 0.09), low[1], z - 0.06), (width / 2 + 0.09, low[1] + 0.06, z + 0.06)), 'gear', 'strut')
            m.add(tube((leg_x * 0.7, top_y + 0.05, z + 0.7), low + (0, 0.7, 0), 0.03), 'gear', 'strut')
            bw, bl = (0.3, 0.75) if x else (0.15, 0.55)
            # bay doors hanging open: outboard of the mains, either side of the nose leg
            for px in ((cx + sx * (bw + 0.02),) if x else (bw + 0.02, -bw - 0.02)):
                m.add(box((px - 0.012, top_y - (0.55 if x else 0.42), z - bl * 0.9), (px + 0.012, top_y + 0.25, z + bl * 0.9)), 'gear',
                      'airframe_grey')
        if steer:
            # the catapult launch bar, lowered to the deck
            m.add(beam((0, r + 0.3, z - 0.1), (0, 0.05, z + 0.95), 0.07, 0.045), 'gear', 'airframe_dark')

    def hook(self, d0, d1, y):
        m = self.m
        m.add(beam((0, y, -d0), (0, y - 0.18, -d1), 0.1, 0.09), 'hook', 'hook')
        m.add(box((-0.08, y - 0.3, -(d1 + 0.12)), (0.08, y - 0.12, -(d1 - 0.1))), 'hook', 'black')

    def ladder(self, d, side=1):
        """The boarding ladder hooked over the left sill."""
        if self.state not in ('usable', 'abandoned'):
            return
        xr, yr = self.rim_at(d)
        top = np.array([side * (xr + 0.02), yr + 0.05, -d])
        foot = np.array([side * (xr + 0.95), 0.0, -d])
        for dz in (-0.2, 0.2):
            self.m.add(beam(top + (0, 0, dz), foot + (0, 0, dz), 0.045), 'ladder', 'ladder')
        for k in np.linspace(0.12, 0.92, 8):
            p = top + (foot - top) * k
            self.m.add(beam(p + (0, 0, -0.2), p + (0, 0, 0.2), 0.035), 'ladder', 'ladder')
        self.m.add(beam(top + (0, 0, -0.2), top + (-side * 0.12, -0.05, -0.2), 0.04), 'ladder', 'ladder')
        self.m.add(beam(top + (0, 0, 0.2), top + (-side * 0.12, -0.05, 0.2), 0.04), 'ladder', 'ladder')

    def cover(self, corners, streamer_at):
        """A red intake or exhaust plug with its remove-before-flight streamer."""
        if self.state != 'abandoned':
            return
        q = np.asarray(corners, float)
        self.m.add(np.array([q[[0, 1, 2]], q[[0, 2, 3]], q[[0, 2, 1]], q[[0, 3, 2]]]), 'cover', 'red', orient=None)
        p = np.asarray(streamer_at, float)
        self.m.add(box(p - (0.012, 0.75, 0.04), p + (0.012, 0, 0.04)), 'cover', 'red_tag')

    def missile(self, nose, length, r, fins=True, tile='missile'):
        """A missile lying along -z from its nose: body, nose cone, fins."""
        n = np.asarray(nose, float)
        tail = n - (0, 0, length)
        body = tube(n - (0, 0, r * 3), tail, r, r, 12)
        cone = tube(n, n - (0, 0, r * 3), 0.004, r, 12, cap0=False, cap1=False)
        x0, y0, w, h = TILES_UV[tile + '_body']
        fu = np.clip((n[2] - body[..., 2]) / length, 0, 1)
        fv = np.abs(np.arctan2(body[..., 1] - n[1], body[..., 0] - n[0])) / np.pi
        uv = np.stack([(x0 + 1 + fu * (w - 2)) / AW, (y0 + 1 + fv * (h - 2)) / AH], -1)
        self.m.add(body, 'store', tile + '_body', orient='centre', centre=n - (0, 0, length / 2), uv=uv)
        self.m.add(cone, 'store', tile + '_nose', orient='centre', centre=n - (0, 0, r * 2.5))
        if fins:
            for k, at in enumerate((length * 0.12, length * 0.95)):
                span = r * (2.6 if k else 1.9)
                for a in (45, 135):
                    t = math.radians(a)
                    dvec = np.array([math.cos(t), math.sin(t), 0])
                    c = n - (0, 0, at)
                    fin = np.array([c - dvec * span, c + dvec * span, c + dvec * span * 0.5 - (0, 0, r * 2.5),
                                    c - dvec * span * 0.5 - (0, 0, r * 2.5)])
                    thick = np.cross(dvec, (0, 0, 1)) * 0.006
                    self.m.add(loft([fin + thick, fin - thick]), 'store', 'missile_fin')

    def finish_states(self):
        """Geometry damage for the wrecked and burnt states, once everything is built."""
        if self.state in ('wrecked', 'burnt'):
            # the nose leg folded: she sits on her nose, pivoting about the main wheels
            pivot = np.array([0, self.main_r, -self.main_d])
            P = np.concatenate(self.m.T).reshape(-1, 3)
            P = P[P[:, 2] > pivot[2] + 1.0]
            ang = 0.0
            for a in np.radians(np.arange(0.25, 16, 0.25)):
                if rot_x(P, pivot, -a)[:, 1].min() < 0.03:
                    break
                ang = a
            self.m.transform(lambda T: rot_x(T, pivot, -ang))



def rot_x(T, pivot, a, where=None):
    """Turns points about the x axis through pivot (positive tips the front, +z, up)."""
    T = np.array(T, float)
    c, s = math.cos(a), math.sin(a)
    rel = T - pivot
    y = rel[..., 1] * c + rel[..., 2] * s
    z = -rel[..., 1] * s + rel[..., 2] * c
    out = np.stack([rel[..., 0], y, z], -1) + pivot
    if where is not None:
        m = where(T)
        if m.ndim == 2:
            m = m.all(-1)
        out = np.where(m[..., None, None] if out.ndim == 3 else m[..., None], out, T)
    return out


class Corvid(Jet):
    """The single-seat stealth fighter: 18.9 m long, 13.6 m span, 5.1 m tall."""
    id = 'jet_corvid'
    name = 'Corvid'
    length = 18.9
    body = [
        # d, w, top, bot, cy, n, nb
        (0.00, 0.00, 0.00, 0.00, 2.22, 1.3, 1.3),
        (0.35, 0.15, 0.09, 0.09, 2.21, 1.3, 1.3),
        (1.20, 0.38, 0.25, 0.25, 2.18, 1.35, 1.4),
        (2.20, 0.56, 0.39, 0.41, 2.15, 1.45, 1.5),
        (3.20, 0.70, 0.50, 0.52, 2.12, 1.5, 1.6),
        (4.10, 0.79, 0.58, 0.60, 2.10, 1.6, 1.7),
        (5.00, 0.86, 0.63, 0.67, 2.08, 1.7, 1.8),
        (6.00, 0.92, 0.65, 0.71, 2.07, 1.8, 1.9),
        (7.00, 0.98, 0.64, 0.73, 2.08, 2.0, 2.0),
        (8.00, 1.12, 0.60, 0.72, 2.10, 2.3, 2.1),
        (9.00, 1.36, 0.55, 0.66, 2.13, 2.5, 2.2),
        (10.0, 1.60, 0.50, 0.62, 2.17, 2.6, 2.3),
        (11.0, 1.72, 0.46, 0.60, 2.19, 2.7, 2.4),
        (13.0, 1.72, 0.42, 0.56, 2.19, 2.7, 2.4),
        (14.5, 1.62, 0.38, 0.50, 2.17, 2.7, 2.4),
        (15.8, 1.50, 0.33, 0.45, 2.15, 2.7, 2.4),
        (16.9, 1.42, 0.28, 0.38, 2.13, 2.7, 2.4),
    ]
    tub = (4.15, 6.25, 1.72)
    main_d, main_r = 11.2, 0.45
    nose_y0 = 2.2
    seats_d = [5.0]
    nose_d = 4.6

    def build(self):
        s = self.state
        self.fuselage()
        # caret intakes: swept faces, the ducts running back into the body
        for sx in (1, -1):
            face = [(1.00, 2.50, 6.25), (1.58, 2.50, 6.60), (1.74, 1.56, 7.05), (1.04, 1.56, 6.72)]
            rings = []
            for k, dd in enumerate((0.0, 1.0, 2.4, 3.8)):
                pull = [0.0, 0.05, 0.25, 0.55][k]
                rings.append([(sx * (x - (pull if x > 1.2 else 0.0) * 0.4), y + (0.0 if y > 2 else pull * 0.35), -(d + dd))
                              for x, y, d in face])
            ring_ext = np.array(rings)
            T = loft(ring_ext[::-1], cap0=False, cap1=False)
            self.m.add(T, 'intake', orient='centre', centre=(sx * 1.25, 2.05, -(8.5)))
            inset = np.array([(sx * (x + (0.06 if x < 1.2 else -0.06)), y + (-0.06 if y > 2 else 0.06), -(d + 0.02)) for x, y, d in face])
            # the duct bends away from the body as it goes in, so the fuselage never shows through it
            deep = inset + (0, 0, -1.1) + np.array([[sx * 0.14, 0, 0]] * 4) * (np.abs(inset[:, :1]) < 1.3)
            outer = ring_ext[0]
            mid = inset.mean(0)
            self.m.add(loft([inset, outer], False, False), 'intake_lip', 'intake_lip', orient='centre', centre=mid + (0, 0, -1))
            self.m.add(loft([deep, inset], True, False), 'intake_in', 'dark', orient='inward', centre=mid + (0, 0, -0.7))
            self.cover([inset[0] + (0, 0, -0.05), inset[1] + (0, 0, -0.05), inset[2] + (0, 0, -0.05), inset[3] + (0, 0, -0.05)],
                       (sx * 1.3, 1.56, -(6.95)))
        # wings, stabilators, the canted fins
        self.surface('wing', Surface((0.9, 2.36), 5.88, (8.0 - 0.82 * 0.9, 12.53), (15.6 + 0.82 * 0.31, 14.06), (0.045, 0.035),
                                     dihedral=-3.25), stations=12)
        self.surface('stab', Surface((1.05, 2.13), 3.37, (15.0 - 0.55 * 0.9, 17.52), (18.9 + 0.55 * 0.3, 18.04), (0.04, 0.03)),
                     stations=7)
        if s in ('wrecked', 'burnt'):
            # the right stabilator was torn off
            self.m.drop_last()
        cant = math.radians(28)
        self.surface('fin', Surface((1.30, 2.40), 2.9, (12.9, 14.12), (16.5, 15.55), (0.05, 0.035),
                                    sdir=(math.sin(cant), math.cos(cant), 0)), stations=8)
        # the stabilator pivot fairings
        for sx in (1, -1):
            self.m.add(tube((sx * 1.42, 2.14, -14.6), (sx * 1.42, 2.14, -17.9), 0.16, 0.06, 12), 'boom', 'airframe_grey',
                       orient='centre', centre=(sx * 1.42, 2.14, -16.2))
        # flat thrust-vectoring nozzles
        for sx in (1, -1):
            cx = sx * 0.62
            a = [(cx - 0.50, 1.86), (cx + 0.50, 1.86), (cx + 0.50, 2.34), (cx - 0.50, 2.34)]
            b = [(cx - 0.47, 1.93), (cx + 0.47, 1.93), (cx + 0.47, 2.27), (cx - 0.47, 2.27)]
            ra = np.array([(x, y, -16.3) for x, y in a])
            rb = np.array([(x, y, -17.75) for x, y in b])
            self.m.add(loft([rb, ra], False, False), 'nozzle', 'nozzle', orient='centre', centre=(cx, 2.1, -17))
            inner = np.array([(x + (0.05 if x < cx else -0.05), y + (0.05 if y < 2.1 else -0.05), -17.74) for x, y in b])
            self.m.add(loft([inner, rb], False, False), 'nozzle', 'nozzle_lip', orient='centre', centre=(cx, 2.1, -16.5))
            self.m.add(loft([inner + (0, 0, 0.5), inner], True, False), 'nozzle', 'dark', orient='inward', centre=(cx, 2.1, -17.5))
            self.cover(inner + (0, 0, 0.03), (cx, 1.93, -17.72))
        # cockpit: seat, panel, consoles, the bubble
        floor = self.tub[2]
        self.panel(4.2, floor, 2.47, 0.40, side_stick=True)
        self.seat(5.0, floor)
        self.canopy(3.85, 8.0, 0.64, 5.2, rear=3.8, bows=())
        self.ladder(5.1)
        # gear: nose leg under the cockpit, mains outboard of the weapons bays
        self.gear(4.6, 0, 0.30, 0.17, 1.48, steer=True, collapsed=s in ('wrecked', 'burnt'))
        self.gear(11.2, 1.55, 0.45, 0.27, 1.56, flat=s == 'wrecked')
        self.hook(15.2, 16.8, 1.82)
        # blade antennas on the spine and belly
        for d, y, up in ((8.6, 2.74, 1), (12.0, 2.64, 1), (9.5, 1.48, -1)):
            self.m.add(box((-0.01, min(y, y + up * 0.22), -(d + 0.35)), (0.01, max(y, y + up * 0.22), -d)), 'antenna', 'airframe_dark')
        self.finish_states()


class Gyrfalcon(Jet):
    """The two-seat heavy fighter: 19.4 m long, 13.1 m span, 5.6 m tall."""
    id = 'jet_gyrfalcon'
    name = 'Gyrfalcon'
    length = 19.43
    body = [
        (0.00, 0.00, 0.00, 0.00, 2.44, 2.0, 2.0),
        (0.55, 0.19, 0.19, 0.19, 2.42, 2.0, 2.0),
        (1.50, 0.37, 0.37, 0.37, 2.38, 2.0, 2.0),
        (2.60, 0.51, 0.51, 0.50, 2.34, 2.0, 2.0),
        (3.60, 0.62, 0.60, 0.57, 2.32, 2.1, 2.0),
        (4.50, 0.70, 0.66, 0.63, 2.31, 2.2, 2.1),
        (5.50, 0.76, 0.70, 0.67, 2.32, 2.3, 2.2),
        (6.60, 0.80, 0.71, 0.71, 2.34, 2.4, 2.3),
        (7.70, 0.83, 0.70, 0.73, 2.36, 2.5, 2.4),
        (8.70, 0.90, 0.64, 0.75, 2.38, 2.6, 2.5),
        (9.60, 1.10, 0.52, 0.74, 2.40, 2.8, 2.6),
        (10.5, 1.45, 0.42, 0.72, 2.43, 3.0, 2.6),
        (11.5, 1.70, 0.36, 0.68, 2.45, 3.2, 2.6),
        (13.5, 1.70, 0.34, 0.64, 2.43, 3.2, 2.6),
        (15.5, 1.62, 0.30, 0.60, 2.38, 3.0, 2.6),
        (17.0, 1.42, 0.26, 0.54, 2.31, 2.8, 2.5),
        (17.9, 1.26, 0.22, 0.47, 2.26, 2.6, 2.4),
    ]
    tub = (4.75, 8.95, 1.92)
    main_d, main_r = 12.0, 0.48
    nose_y0 = 2.4
    seats_d = [5.65, 7.85]
    nose_d = 5.4

    def build(self):
        s = self.state
        self.fuselage()
        # big box intakes, raked, the top lip leading
        for sx in (1, -1):
            face = [(0.80, 2.80, 6.05), (1.66, 2.80, 6.05), (1.70, 1.72, 6.85), (0.84, 1.72, 6.85)]
            rings = []
            for k, dd in enumerate((0.0, 1.2, 2.6, 4.2)):
                pull = [0.0, 0.0, 0.1, 0.35][k]
                rings.append([(sx * (x - (pull if x > 1.2 else 0.0)), y - (0.0 if y > 2.2 else -pull * 0.6), -(d + dd))
                              for x, y, d in face])
            ring_ext = np.array(rings)
            self.m.add(loft(ring_ext[::-1], cap0=False, cap1=False), 'intake', orient='centre', centre=(sx * 1.25, 2.25, -8.5))
            inset = np.array([(sx * (x + (0.07 if x < 1.2 else -0.07)), y + (-0.07 if y > 2.2 else 0.07), -(d + 0.02)) for x, y, d in face])
            mid = inset.mean(0)
            self.m.add(loft([inset, ring_ext[0]], False, False), 'intake_lip', 'intake_lip', orient='centre', centre=mid + (0, 0, -1))
            deep = inset + (0, 0, -1.2) + np.array([[sx * 0.16, 0, 0]] * 4) * (np.abs(inset[:, :1]) < 1.2)
            self.m.add(loft([deep, inset], True, False), 'intake_in', 'dark', orient='inward', centre=mid + (0, 0, -0.6))
            self.cover(inset + (0, 0, -0.05), (sx * 1.25, 1.75, -6.82))
            # the ramp actuator fairing on the intake's top
            self.m.add(box((sx * 0.9 - 0.25, 2.80, -9.6), (sx * 0.9 + 0.25, 2.86, -6.1)), 'intake_top')
        self.surface('wing', Surface((1.0, 2.62), 5.52, (8.6 - 0.7, 13.42), (14.4, 14.4), (0.066, 0.03), dihedral=-1.0), stations=12)
        self.surface('stab', Surface((1.45, 2.06), 2.85, (15.9 - 0.2 * 0.84, 18.18), (19.43, 19.43), (0.045, 0.03), dihedral=-2.0),
                     stations=7)
        if s in ('wrecked', 'burnt'):
            self.m.drop_last()
        cant = math.radians(2)
        self.surface('fin', Surface((1.50, 2.66), 2.98, (14.7, 16.9), (18.6, 18.55), (0.055, 0.035),
                                    sdir=(math.sin(cant), math.cos(cant), 0)), stations=8)
        for sx in (1, -1):
            # the fin-tip pods, the tail booms carrying the stabilators
            top = np.array([sx * (1.50 + 2.98 * math.sin(cant)), 2.66 + 2.98 * math.cos(cant), 0])
            self.m.add(tube(top + (0, 0.02, -16.7), top + (0, 0.02, -18.9), 0.07, 0.07, 10), 'fin_pod', 'airframe_dark',
                       orient='centre', centre=top + (0, 0.02, -17.8))
            self.m.add(beam((sx * 1.55, 2.06, -13.6), (sx * 1.55, 2.06, -19.2), 0.2, 0.36), 'boom', 'airframe_grey')
        # round nozzles with their petals
        for sx in (1, -1):
            cx = sx * 0.66
            a, b = np.array([cx, 2.02, -17.6]), np.array([cx, 2.02, -19.05])
            self.m.add(tube(a, b, 0.6, 0.53, 20, cap0=False, cap1=False), 'nozzle', 'nozzle', orient='centre', centre=(a + b) / 2)
            self.m.add(tube(b + (0, 0, 0.6), b + (0, 0, 0.01), 0.47, 0.47, 20, cap0=True, cap1=False), 'nozzle', 'dark',
                       orient='inward', centre=b + (0, 0, 0.3))
            t = np.linspace(0, 2 * np.pi, 21)
            lip = []
            for i in range(20):
                p0 = b + (0, 0.53 * math.sin(t[i]), 0) + (0.53 * math.cos(t[i]), 0, 0)
                p1 = b + (0, 0.53 * math.sin(t[i + 1]), 0) + (0.53 * math.cos(t[i + 1]), 0, 0)
                q0 = b + (0, 0.47 * math.sin(t[i]), 0.0) + (0.47 * math.cos(t[i]), 0, 0)
                q1 = b + (0, 0.47 * math.sin(t[i + 1]), 0.0) + (0.47 * math.cos(t[i + 1]), 0, 0)
                lip += [[p0, q1, p1], [p0, q0, q1]]
            self.m.add(np.array(lip), 'nozzle', 'nozzle_lip', orient='centre', centre=b + (0, 0, 1.0))
            self.cover([(cx - 0.36, 1.66, -19.0), (cx + 0.36, 1.66, -19.0), (cx + 0.36, 2.38, -19.0), (cx - 0.36, 2.38, -19.0)],
                       (cx, 1.68, -19.0))
        floor = self.tub[2]
        self.panel(4.8, floor, 2.73, 0.40)
        self.seat(5.65, floor)
        # the back seater's panel and seat
        self.m.add(box((-0.38, floor, -7.05), (0.38, floor + 0.85, -6.9)), 'panel', 'panel_black')
        self.decal(np.array([[0.36, floor + 0.35, -7.05], [-0.36, floor + 0.35, -7.05], [-0.36, floor + 0.82, -7.05],
                             [0.36, floor + 0.82, -7.05]]))
        eye = self.eye
        self.seat(7.85, floor)
        self.eyes = [eye, self.eye]
        self.canopy(4.4, 9.9, 0.8, 6.9, rear=5.3, bows=(5.3,))
        self.ladder(5.9)
        self.gear(5.4, 0, 0.33, 0.2, 1.66, steer=True, collapsed=s in ('wrecked', 'burnt'))
        self.gear(12.0, 1.38, 0.48, 0.28, 1.78, flat=s == 'wrecked')
        self.hook(16.0, 17.6, 1.86)
        # the belly tank and the missiles
        if s != 'burnt':
            ty = 1.12
            self.m.add(tube((0, ty, -9.4), (0, ty, -13.4), 0.35, 0.35, 18, False, False), 'store', 'tank', orient='centre',
                       centre=(0, ty, -11.4))
            self.m.add(tube((0, ty, -8.3), (0, ty, -9.4), 0.02, 0.35, 18, False, False), 'store', 'tank', orient='centre',
                       centre=(0, ty, -9.2))
            self.m.add(tube((0, ty, -13.4), (0, ty, -14.6), 0.35, 0.04, 18, False, False), 'store', 'tank', orient='centre',
                       centre=(0, ty, -13.6))
            self.m.add(box((-0.06, ty + 0.3, -13.0), (0.06, 1.78, -9.8)), 'store', 'airframe_grey')
            for sx in (1, -1):
                # wing pylon with a short-range missile on each rail
                px = sx * 3.55
                yw = 2.62 - (3.55 - 1.0) * math.tan(math.radians(1.0)) - 0.17
                self.m.add(box((px - 0.07, yw - 0.42, -12.9), (px + 0.07, yw, -10.3)), 'store', 'airframe_grey')
                self.m.add(box((px - 0.32, yw - 0.5, -12.6), (px + 0.32, yw - 0.42, -10.5)), 'store', 'airframe_grey')
                for rx in (-0.32, 0.32):
                    self.missile((px + rx, yw - 0.6, -9.9), 2.9, 0.064, tile='sr')
                # medium-range missiles semi-recessed at the fuselage's lower corners
                for d0 in (7.6, 12.2):
                    if s == 'wrecked' and d0 > 10 and sx < 0:
                        continue
                    self.missile((sx * 1.18, 1.62, -d0), 3.66, 0.1, tile='mr')
        self.eye = self.eyes[0]
        self.finish_states()


AIRFRAMES = {'corvid': Corvid, 'gyrfalcon': Gyrfalcon}


# ---------------------------------------------------------------- material tiles

TILE_NAMES = ['tyre', 'hub', 'strut', 'strut_chrome', 'bay', 'bay_door', 'dark', 'intake_lip', 'nozzle', 'nozzle_lip',
              'glass_gold', 'glass_clear', 'frame', 'cockpit', 'seat_frame', 'cushion', 'panel_black', 'console', 'stick',
              'yellow_black', 'red', 'red_tag', 'hook', 'black', 'ladder', 'airframe_grey', 'airframe_dark', 'tank',
              'missile_fin', 'sr_body', 'sr_nose', 'mr_body', 'mr_nose', 'char_metal', 'mfd']
TILES_UV = {}


def tile_rect(name):
    i = TILE_NAMES.index(name)
    per_row = 1152 // TILE
    x = TILE_ORIGIN[0] + (i % per_row) * TILE
    y = TILE_ORIGIN[1] + (i // per_row) * TILE
    if name == 'mfd':
        # the instrument panel decal gets a wide slot of its own
        return (1728, 480, 320, 160)
    if name in ('sr_body', 'mr_body'):
        return (1728, 656 + (0 if name == 'sr_body' else 40), 320, 32)
    return (x, y, TILE, TILE)


for _n in TILE_NAMES:
    TILES_UV[_n] = tile_rect(_n)


def paint_tiles(img, jet, state, rng):
    """Every flat material tile, aged for the state."""
    A = img
    burnt = state == 'burnt'
    dirty = {'usable': 0.15, 'abandoned': 0.55, 'wrecked': 0.75, 'burnt': 0.3}[state]
    base = {
        'tyre': (28, 28, 30), 'hub': (150, 152, 150), 'strut': (176, 178, 176), 'strut_chrome': (205, 208, 210),
        'bay': (196, 198, 196), 'bay_door': (190, 192, 190), 'dark': (14, 14, 16), 'intake_lip': (120, 124, 128),
        'nozzle': (82, 78, 74), 'nozzle_lip': (60, 56, 52), 'glass_gold': (104, 92, 62), 'glass_clear': (160, 200, 190),
        'frame': (48, 50, 54), 'cockpit': (64, 68, 72), 'seat_frame': (40, 42, 44), 'cushion': (64, 70, 52),
        'panel_black': (26, 27, 29), 'console': (34, 35, 37), 'stick': (20, 20, 20), 'yellow_black': (210, 170, 30),
        'red': (172, 30, 28), 'red_tag': (190, 34, 30), 'hook': (226, 226, 222), 'black': (20, 20, 20),
        'ladder': (214, 176, 40), 'airframe_grey': jet.paint_main, 'airframe_dark': jet.paint_dark, 'tank': jet.paint_main,
        'missile_fin': (205, 207, 205), 'sr_nose': (60, 62, 64), 'mr_nose': (226, 226, 222), 'char_metal': (40, 36, 33),
    }
    for name in TILE_NAMES:
        x, y, w, h = TILES_UV[name]
        if name == 'mfd':
            A[y:y + h, x:x + w] = mfd_panel(w, h, state)
            continue
        if name in ('sr_body', 'mr_body'):
            A[y:y + h, x:x + w] = missile_strip(w, h, name)
            continue
        c = np.array(base[name], float)
        n = rng.random((h, w, 1)) * 0.1 - 0.05
        grime = (rng.random((h, w, 1)) < dirty * 0.08) * -0.25
        tile = c * (1 + n + grime)
        if name == 'yellow_black':
            yy, xx = np.mgrid[0:h, 0:w]
            tile = np.where((((xx + yy) // 4) % 2 == 0)[..., None], tile, np.array([24, 24, 24]))
        if name == 'hook':
            yy, xx = np.mgrid[0:h, 0:w]
            tile = np.where((((xx + yy) // 6) % 2 == 0)[..., None], tile, np.array([24, 24, 24]))
        if burnt and name not in ('dark',):
            tile = mix_np(tile, np.array([42, 37, 33], float), 0.85) * (1 + rng.random((h, w, 1)) * 0.2)
        A[y:y + h, x:x + w] = np.clip(tile, 0, 255)


def mix_np(a, b, t):
    return a * (1 - t) + b * t


def mfd_panel(w, h, state):
    """The instrument panel: black with three multi-function displays and the up-front panel."""
    im = Image.new('RGB', (w, h), (24, 25, 27))
    d = ImageDraw.Draw(im)
    lit = state == 'usable'
    for k, (x0, y0, x1, y1) in enumerate([(10, 46, 104, 140), (113, 30, 207, 124), (216, 46, 310, 140)]):
        d.rectangle([x0 - 4, y0 - 4, x1 + 4, y1 + 4], fill=(40, 41, 44))
        for b in range(5):
            d.rectangle([x0 + 6 + b * 18, y0 - 3, x0 + 14 + b * 18, y0 - 1], fill=(70, 72, 76))
            d.rectangle([x0 + 6 + b * 18, y1 + 1, x0 + 14 + b * 18, y1 + 3], fill=(70, 72, 76))
        d.rectangle([x0, y0, x1, y1], fill=(10, 22, 16) if lit else (14, 16, 16))
        if lit:
            g = (80, 220, 120)
            if k == 1:
                d.ellipse([x0 + 15, y0 + 15, x1 - 15, y1 - 15], outline=g)
                d.line([x0 + 47, y0 + 20, x0 + 47, y1 - 20], fill=g)
            else:
                for i in range(6):
                    d.line([x0 + 8, y0 + 12 + i * 13, x0 + 30 + (i * 37) % 50, y0 + 12 + i * 13], fill=g)
    d.rectangle([118, 4, 202, 24], fill=(30, 34, 30))
    for i in range(8):
        d.rectangle([122 + i * 10, 8, 128 + i * 10, 20], fill=(52, 54, 56))
    return np.asarray(im, float)


def missile_strip(w, h, name):
    """A missile body, nose at the left: white or grey, yellow and brown live-round bands."""
    im = Image.new('RGB', (w, h), (214, 216, 212) if name == 'mr_body' else (176, 180, 178))
    d = ImageDraw.Draw(im)
    for x0, col in ((18, (196, 160, 40)), (26, (120, 80, 40)), (int(w * 0.6), (196, 160, 40))):
        d.rectangle([x0, 0, x0 + 4, h], fill=col)
    for x in range(40, w, 37):
        d.line([x, 0, x, h], fill=(150, 152, 150))
    return np.asarray(im, float)


# ---------------------------------------------------------------- the blueprint views

def rasterize(view, T, part, nrm):
    """Per texel of a view: the point on the jet it shows, its part and normal (nearest wins)."""
    x0, y0, W, H = VIEWS[view]
    depth = np.full((H, W), np.inf)
    P = np.zeros((H, W, 3))
    N = np.zeros((H, W, 3))
    pid = np.full((H, W), -1, int)
    u, v, dep = project(view, T)
    for i in range(len(T)):
        uu, vv, dd = u[i], v[i], dep[i]
        lo_u, hi_u = int(max(0, math.floor(uu.min() - 1))), int(min(W - 1, math.ceil(uu.max() + 1)))
        lo_v, hi_v = int(max(0, math.floor(vv.min() - 1))), int(min(H - 1, math.ceil(vv.max() + 1)))
        if hi_u < lo_u or hi_v < lo_v:
            continue
        gv, gu = np.mgrid[lo_v:hi_v + 1, lo_u:hi_u + 1]
        cu, cv = gu + 0.5, gv + 0.5
        den = (vv[1] - vv[2]) * (uu[0] - uu[2]) + (uu[2] - uu[1]) * (vv[0] - vv[2])
        if abs(den) < 1e-9:
            continue
        a = ((vv[1] - vv[2]) * (cu - uu[2]) + (uu[2] - uu[1]) * (cv - vv[2])) / den
        b = ((vv[2] - vv[0]) * (cu - uu[2]) + (uu[0] - uu[2]) * (cv - vv[2])) / den
        c = 1 - a - b
        e = -0.6 / max(1.0, np.ptp(uu) + np.ptp(vv))
        inside = (a >= e) & (b >= e) & (c >= e)
        if not inside.any():
            continue
        a, b, c = np.clip(a, 0, 1), np.clip(b, 0, 1), np.clip(c, 0, 1)
        s = a + b + c
        a, b, c = a / s, b / s, c / s
        z = a * dd[0] + b * dd[1] + c * dd[2]
        win = inside & (z < depth[gv, gu])
        if not win.any():
            continue
        gy, gx = gv[win], gu[win]
        depth[gy, gx] = z[win]
        P[gy, gx] = (a[win, None] * T[i, 0] + b[win, None] * T[i, 1] + c[win, None] * T[i, 2])
        N[gy, gx] = nrm[i]
        pid[gy, gx] = part[i]
    covered = pid >= 0
    # bleed the edges out a few texels so sampling at a face's border never catches background
    for _ in range(4):
        grow = ~covered
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            src = np.roll(np.roll(covered, dy, 0), dx, 1) & grow
            if src.any():
                P[src] = np.roll(np.roll(P, dy, 0), dx, 1)[src]
                N[src] = np.roll(np.roll(N, dy, 0), dx, 1)[src]
                pid[src] = np.roll(np.roll(pid, dy, 0), dx, 1)[src]
                covered = covered | src
                grow = ~covered
    return P, N, pid, covered


PARTS = ['fuse', 'tub', 'intake', 'intake_top', 'wing', 'stab', 'fin', 'boom', 'nozzle', 'fin_pod']


def line(dist, width=1.0):
    """Coverage of an antialiased line given the distance to it in texels."""
    return np.clip(width - np.abs(dist) + 0.5, 0, 1)


def text_mask(text, h_px, bold=True):
    f = font(int(h_px * 1.35), bold)
    l, t, r, b = f.getbbox(text)
    im = Image.new('L', (r - l + 4, b - t + 4), 0)
    ImageDraw.Draw(im).text((2 - l, 2 - t), text, fill=255, font=f)
    return np.asarray(im, float) / 255


def stamp(mask, U, V, u0, v0, w, h):
    """Samples a mask image laid over the rectangle (u0, v0)-(u0+w, v0+h) at coordinates U, V."""
    mh, mw = mask.shape
    fu = (U - u0) / w
    fv = (V - v0) / h
    ok = (fu >= 0) & (fu < 1) & (fv >= 0) & (fv < 1)
    iu = np.clip((fu * mw).astype(int), 0, mw - 1)
    iv = np.clip((fv * mh).astype(int), 0, mh - 1)
    return np.where(ok, mask[iv, iu], 0.0)


def insignia(a, b, r):
    """The (fictional) low-visibility national mark: a ring, a bar through it, a diamond inside."""
    d = np.sqrt(a * a + b * b)
    ring = (d < r) & (d > r * 0.78)
    bar = (np.abs(b) < r * 0.16) & (np.abs(a) < r * 1.55) & (d > r * 0.78)
    diamond = (np.abs(a) + np.abs(b)) < r * 0.55
    return (ring | bar | diamond).astype(float)


def field(P, covered, scale, seed, octaves=4, noise=None):
    """fbm of the jet's surface points (scaled per axis) over a view, only where it's covered."""
    out = np.zeros(covered.shape)
    Q = P[covered] * np.asarray(scale, float)
    out[covered] = (noise or fbm)(Q, seed, octaves) if noise is None else noise(Q, seed)
    return out


def paint_view(jet, view, P, N, pid, covered, state, rng):
    H, W = pid.shape
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    d = -z
    part = np.array(PARTS + ['?'] * 40, object)[np.clip(pid, 0, None)]
    up = N[..., 1] > 0.35
    col = np.zeros((H, W, 3))
    main, dark, belly = (np.array(c, float) for c in (jet.paint_main, jet.paint_dark, jet.paint_belly))
    # camouflage: soft blotches of the darker grey over the top and sides, belly lighter
    camo = field(P, covered, (0.35, 0.2, 0.22), 11)
    blot = sstep(jet.camo_cut - 0.03, jet.camo_cut + 0.03, camo)
    col[:] = main
    col = col * (1 - blot[..., None]) + dark * blot[..., None]
    under = (N[..., 1] < -0.3)
    col = np.where(under[..., None], belly, col)
    grain = field(P, covered, 6, 5, 3) - 0.5
    col = col * (1 + grain[..., None] * 0.06)
    tub = part == 'tub'
    col[tub] = (64, 68, 72)
    lines = np.zeros((H, W))
    # panel lines: fuselage stations, plus spine and chine runs
    fus = (part == 'fuse') | (part == 'intake') | (part == 'intake_top')
    for s in jet.stations:
        lines = np.maximum(lines, line((d - s) * PX) * fus)
    for xs in jet.spine_x:
        lines = np.maximum(lines, line((np.abs(x) - xs) * PX) * fus * (N[..., 1] > 0.6) * (d > 7.5))
        lines = np.maximum(lines, line((np.abs(x) - xs) * PX) * fus * (N[..., 1] < -0.6))
    # access panels (side: d0, d1, y0, y1; top: d0, d1, x0, x1, both sides)
    side = (np.abs(N[..., 0]) > 0.5)
    for d0, d1, y0, y1 in jet.side_panels:
        inside = (d > d0) & (d < d1) & (y > y0) & (y < y1)
        edge = np.minimum(np.minimum(np.abs(d - d0), np.abs(d - d1)), np.minimum(np.abs(y - y0), np.abs(y - y1))) * PX
        lines = np.maximum(lines, line(edge) * inside * side * fus)
    for d0, d1, x0, x1 in jet.belly_doors:
        ax = np.abs(x)
        saw = (np.abs(((d * 4) % 1) - 0.5) - 0.25) * 0.12 * jet.sawtooth
        inside = (d > d0 + saw) & (d < d1 - saw) & (ax > x0) & (ax < x1)
        edge = np.minimum(np.minimum(np.abs(d - d0 - saw), np.abs(d - d1 + saw)), np.minimum(np.abs(ax - x0), np.abs(ax - x1))) * PX
        lines = np.maximum(lines, line(edge, 1.2) * (edge < 2) * ((d > d0 - 0.1) & (d < d1 + 0.1) & (ax > x0 - 0.05) & (ax < x1 + 0.05)) * (N[..., 1] < -0.3))
    # lifting surfaces: ribs, spars, the control surfaces' hinge lines and gaps
    for name, srf in jet.surfaces.items():
        on = part == name
        if not on.any():
            continue
        Pl = P.copy()
        Pl[..., 0] = np.abs(Pl[..., 0])
        k, f = srf.param(Pl)
        span = srf.span
        for r in (np.arange(0.12, 1.0, 0.21) if name == 'wing' else (0.35, 0.7)):
            lines = np.maximum(lines, line((k - r) * span * PX, 0.7) * on * 0.45)
        for spar in (0.15, 0.55):
            lines = np.maximum(lines, line((f - spar) * 2.0 * PX, 0.7) * on * 0.35)
        hinge = jet.hinges.get(name)
        if hinge:
            f0, k0, k1 = hinge
            cs = (f > f0) & (k > k0) & (k < k1)
            lines = np.maximum(lines, line((f - f0) * 3.0 * PX, 1.3) * (k > k0) * (k < k1) * on)
            for kk in (k0, k1) + tuple(jet.splits.get(name, ())):
                lines = np.maximum(lines, line((k - kk) * span * PX, 1.2) * (f > f0) * on)
            col[cs & on] *= 0.97
        lf = jet.lef.get(name)
        if lf:
            lines = np.maximum(lines, line((f - lf) * 3.0 * PX, 1.1) * on * (k > 0.08))
        if name == 'wing' and jet.walkway:
            # no-step walkway outline on the wing root
            ww = (k > 0.03) & (k < 0.2) & (f > 0.25) & (f < 0.65) & on & up
            edge = np.minimum(np.minimum(np.abs(k - 0.03), np.abs(k - 0.2)) * span, np.minimum(np.abs(f - 0.25), np.abs(f - 0.65)) * 5) * PX
            col[ww & (edge < 1.5)] = col[ww & (edge < 1.5)] * 0.55
    col *= (1 - lines[..., None] * 0.24)
    # rivets along the fuselage stations
    riv = np.zeros((H, W), bool)
    for s in jet.stations[::2]:
        dd = np.abs(d - s - 0.05) * PX
        riv |= (dd < 0.7) & ((((x + y) * PX) % 5) < 1.0) & fus
    col[riv] *= 0.85
    # markings
    low = dark * 0.72
    paint_marks(jet, view, P, N, part, col, low, state)
    # exhaust soot round the nozzles and on the stabilators
    hot = np.exp(-np.maximum(0, (jet.length - 1.2 - d)) / 1.2) * (np.abs(x) < 2.6)
    col = col * (1 - hot[..., None] * jet.soot)
    # the state's weathering
    col = weather(jet, view, P, N, part, col, state, rng, covered)
    col[~covered] = (128, 128, 128)
    return np.clip(col, 0, 255)


def paint_marks(jet, view, P, N, part, col, low, state):
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    d = -z
    up, down = N[..., 1] > 0.35, N[..., 1] < -0.35
    ax = np.abs(x)
    # national marks: top of the left wing, under the right, both sides of the intakes
    wing = part == 'wing'
    ix, idd = jet.insignia_wing
    m = insignia(x - ix, d - idd, 0.62) * wing * up
    m = np.maximum(m, insignia(-x - ix, d - idd, 0.62) * wing * down)
    sx, sd, sy = jet.insignia_side
    side = (np.abs(N[..., 0]) > 0.5) & ((part == 'intake') | (part == 'fuse'))
    m = np.maximum(m, insignia(d - sd, y - sy, 0.36) * side)
    col[m > 0] = col[m > 0] * 0.2 + low * 0.8
    # tail code, modex and the service's name: laid on so they read the right way from each side
    fin = (part == 'fin') & (np.abs(N[..., 0]) > 0.5)
    if view in ('left', 'right'):
        def place(text, dc, ytop, hgt, sel, px=40):
            mask = text_mask(text, px)
            wid = hgt * mask.shape[1] / mask.shape[0]
            t = (d - (dc - wid / 2)) / wid if view == 'left' else ((dc + wid / 2) - d) / wid
            k = stamp(mask, t, (ytop - y) / hgt, 0, 0, 1, 1) * sel
            col[k > 0.45] = col[k > 0.45] * 0.25 + low * 0.75
        place(jet.tail_code, *jet.code_box, fin)
        place(jet.word, *jet.word_box, fin, 24)
        place(jet.modex, *jet.modex_box, part == 'fuse')
        # the rescue arrow under the canopy: red triangle and the yellow-on-black ejection warning
        rd, ry = jet.rescue
        tri = (np.abs(d - rd) < (ry + 0.16 - y) * 0.6) & (y < ry + 0.16) & (y > ry - 0.02)
        col[tri & (part == 'fuse')] = (168, 36, 30)
        # formation lights: pale green strips
        for fd, fy, fl in jet.formation:
            strip = (np.abs(d - fd) < fl / 2) & (np.abs(y - fy) < 0.035)
            col[strip & ((part == 'fuse') | fin)] = (150, 175, 120)
    if view == 'top':
        # the walkway's no-step words and the refuelling door outline on the spine
        rd0, rd1 = jet.refuel
        door = (np.abs(x) < 0.22) & (d > rd0) & (d < rd1) & (part == 'fuse')
        edge = np.minimum(np.abs(np.abs(x) - 0.22), np.minimum(np.abs(d - rd0), np.abs(d - rd1))) * PX
        col[door & (edge < 1.3)] *= 0.6


def weather(jet, view, P, N, part, col, state, rng, covered):
    H, W = part.shape
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    up = N[..., 1] > 0.35
    amt = {'usable': 0.25, 'abandoned': 0.8, 'wrecked': 0.95, 'burnt': 0.0}[state]
    if state == 'burnt':
        char = np.array([34, 31, 29], float)
        ash = np.array([118, 114, 108], float)
        rustc = np.array([92, 60, 40], float)
        n = field(P, covered, 1.6, 41)
        n2 = field(P, covered, 5.0, 43)
        c = char * (1 + (n2[..., None] - 0.5) * 0.6)
        c = np.where((n > 0.66)[..., None], mix_np(c, ash, 0.55 * sstep(0.66, 0.8, n)[..., None]), c)
        c = np.where((n < 0.32)[..., None], mix_np(c, rustc, 0.8 * sstep(0.32, 0.2, n)[..., None]), c)
        # bare metal where the paint burnt through, dulled
        bare = (n2 > 0.76) & (n > 0.55)
        c[bare] = (78, 76, 72)
        return c
    # grime gathering in the panel lines and low spots, rain streaks running down and aft
    g = field(P, covered, (1.2, 2.5, 1.2), 21)
    col = col * (1 - (sstep(0.45, 0.8, g) * 0.16 * amt)[..., None])
    if view in ('left', 'right', 'front', 'back'):
        st = field(P, covered, (9, 0.4, 9), 23)
    else:
        st = field(P, covered, (9, 9, 0.35), 23)
    col = col * (1 - (sstep(0.55, 0.85, st) * 0.18 * amt)[..., None])
    # sun-faded top surfaces
    col = np.where(up[..., None], col * (1 - 0.06 * amt) + np.array([12, 12, 10]) * amt, col)
    if state in ('abandoned', 'wrecked'):
        # gull droppings on the tops, a leak of hydraulic fluid down from the gear bays
        spot = rng.random((H, W)) < 0.0035 * amt
        col[spot & up] = (214, 212, 204)
        leak = field(P, covered, (14, 0.6, 14), 29)
        oily = (leak > 0.72) & (N[..., 1] < 0.2) & (y < 2.2)
        col[oily] *= 0.7
    if state == 'wrecked':
        # scorch round the tail and over the left side, bullet holes, a torn panel or two
        sc = field(P, covered, 0.6, 31)
        burn = sstep(0.58, 0.78, sc)
        col = col * (1 - burn[..., None] * 0.75)
        holes = field(P, covered, 9.0, 37, noise=voronoi_edge)
        pick = field(P, covered, 0.9, 33) > 0.7
        col[(holes > 0.55) & pick] = (16, 15, 14)
        col[(holes > 0.45) & (holes <= 0.55) & pick] *= 1.25
        torn = field(P, covered, 2.2, 35)
        bare = (torn > 0.78)
        col[bare] = np.array([150, 152, 150]) * (1 + (rng.random(bare.sum()) - 0.5)[:, None] * 0.1)
    return col


# ---------------------------------------------------------------- packing

def uv_for(T, view_of_tri, mats, UV):
    """Fills in atlas UVs (0..1, v down) for every triangle without explicit ones."""
    for i in range(len(T)):
        if not np.isnan(UV[i, 0, 0]):
            continue
        mat = mats[i]
        if mat is None:
            v = view_of_tri[i]
            x0, y0, W, H = VIEWS[v]
            u, vv, _ = project(v, T[i])
            UV[i, :, 0] = (np.clip(u, 0.5, W - 0.5) + x0) / AW
            UV[i, :, 1] = (np.clip(vv, 0.5, H - 0.5) + y0) / AH
        else:
            x0, y0, w, h = TILES_UV[mat]
            n = np.cross(T[i, 1] - T[i, 0], T[i, 2] - T[i, 0])
            a = int(np.abs(n).argmax())
            axes = [(2, 1), (0, 2), (0, 1)][a]
            q = T[i][:, axes] * 24.0
            q = q - np.floor(q.min(0) / (w - 4)) * (w - 4)
            q = np.clip(q, 0, w - 4)
            UV[i, :, 0] = (x0 + 2 + q[:, 0] * (w - 4) / max(w - 4, 1)) / AW
            UV[i, :, 1] = (y0 + 2 + np.clip(q[:, 1], 0, h - 4)) / AH


def build_state(cls, state):
    jet = cls(state)
    jet.build()
    return (jet,) + jet.m.arrays()


def make_atlas(jet, T, part, mat, state):
    rng = np.random.default_rng(java_hash(jet.id + state) & 0xFFFFF)
    img = np.zeros((AH, AW, 3))
    img[:] = (120, 122, 124)
    nrm = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
    nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
    views = view_of(nrm)
    air = np.array([m is None for m in mat])
    pid = np.array([PARTS.index(p) if p in PARTS else len(PARTS) for p in part])
    for v, (x0, y0, W, H) in VIEWS.items():
        sel = air & (views == v)
        P, N, pidmap, covered = rasterize(v, T[sel], pid[sel], nrm[sel])
        img[y0:y0 + H, x0:x0 + W] = paint_view(jet, v, P, N, pidmap, covered, state, rng)
    paint_tiles(img, jet, state, rng)
    return img, views


def build(cls, out_dir=None):
    variants = []
    for state in VARIANTS:
        jet, T, UV, part, mat, glass = build_state(cls, state)
        img, views = make_atlas(jet, T, part, mat, state)
        uv_for(T, views, mat, UV)
        variants.append(dict(state=state, jet=jet, T=T, UV=UV, glass=glass, img=Image.fromarray(img.astype(np.uint8))))
        print(f'  {cls.id} {state}: {len(T)} triangles', flush=True)
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
        order = np.concatenate([np.where(~v['glass'])[0], np.where(v['glass'])[0]])
        Q = np.round((v['T'][order] - centre) * 1000).astype('>i2').reshape(-1, 3)
        U = np.round(np.clip(v['UV'][order], 0, 1) * 65535).astype('>u2').reshape(-1, 2)
        buf.write(struct.pack('>ii', int((~v['glass']).sum()), int(v['glass'].sum())))
        buf.write(np.concatenate([Q.view('>u2'), U], 1).astype('>u2').tobytes())
        buf.write(struct.pack('>i', 0))  # lids
        buf.write(struct.pack('>i', 0))  # wheels (baked into the body: jets aren't driven)
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
            v['img'].save(os.path.join(out_dir, f'{cls.id}_{v["state"]}.png'))
            np.savez_compressed(os.path.join(out_dir, f'{cls.id}_{v["state"]}.npz'), T=v['T'] - centre, UV=v['UV'], glass=v['glass'])
    blob = scramble(zlib.compress(buf.getvalue(), 9), cls.id)
    out = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/cars', cls.id + '.zcar')
    with open(out, 'wb') as f:
        f.write(blob)
    glo, nx, nz, grid = heights(base - centre)
    floor = floors(base - centre, glo, nx, nz)
    size = hi - lo
    jet = variants[0]['jet']
    eyes = getattr(jet, 'eyes', [jet.eye])
    print(f'{cls.id:20s} {len(blob) // 1024:5d} KB  {size[0]:.1f} x {size[1]:.1f} x {size[2]:.1f} m', flush=True)
    return {'id': cls.id, 'kind': 'jet', 'weight': 1, 'size': [round(float(s), 3) for s in size],
            'cell': CELL, 'x0': round(float(glo[0]), 3), 'z0': round(float(glo[2]), 3), 'nx': nx, 'nz': nz,
            'heights': [int(round(h * 100)) for h in grid.ravel()],
            'floors': [int(round(f * 100)) for f in floor.ravel()],
            'seats': [[round(float(a - b), 3) for a, b in zip(e, centre)] for e in eyes],
            'wheelbase': round(float(jet.main_d - jet.nose_d), 3)}


def floors(P, lo, nx, nz):
    """The underside of the jet over each CELL column: the lowest surface above the deck, so
    hull blocks leave room to walk under the wings (0 where the jet reaches the deck)."""
    grid = np.full((nz, nx), np.inf)
    for a in np.linspace(0, 1, 9):
        for b in np.linspace(0, 1 - a, max(2, int(9 * (1 - a)))):
            q = P[:, 0] * (1 - a - b) + P[:, 1] * a + P[:, 2] * b
            ix = np.clip(((q[:, 0] - lo[0]) / CELL).astype(int), 0, nx - 1)
            iz = np.clip(((q[:, 2] - lo[2]) / CELL).astype(int), 0, nz - 1)
            np.minimum.at(grid, (iz, ix), q[:, 1])
    grid[~np.isfinite(grid)] = 0
    return grid


# the shared paint and marking data, per airframe
Corvid.paint_main, Corvid.paint_dark, Corvid.paint_belly = (138, 143, 148), (112, 117, 123), (146, 150, 154)
Corvid.camo_cut, Corvid.soot, Corvid.sawtooth = 0.56, 0.35, 1.0
Corvid.stations = [1.0, 2.6, 3.85, 7.4, 8.4, 9.6, 10.9, 12.2, 13.5, 14.8, 16.0]
Corvid.spine_x = [0.38, 0.95]
Corvid.side_panels = [(2.9, 3.6, 1.95, 2.35), (8.0, 9.2, 2.35, 2.6), (13.0, 14.6, 2.0, 2.4), (10.6, 11.6, 1.7, 2.0)]
Corvid.belly_doors = [(7.4, 12.6, 0.12, 0.62), (8.4, 10.6, 0.9, 1.3), (3.9, 5.4, 0.0, 0.17), (10.4, 12.0, 1.3, 1.75)]
Corvid.hinges = {'wing': (0.8, 0.12, 0.95), 'fin': (0.68, 0.05, 0.78), 'stab': (0.93, 0.1, 0.9)}
Corvid.splits = {'wing': (0.5,)}
Corvid.lef = {'wing': 0.1}
Corvid.walkway = True
Corvid.insignia_wing = (4.2, 12.6)
Corvid.insignia_side = (1.3, 9.0, 2.0)
Corvid.tail_code, Corvid.modex, Corvid.word = 'GH', '104', 'NAVY'
Corvid.code_box = (14.75, 4.05, 0.62)
Corvid.modex_box = (2.85, 2.33, 0.34)
Corvid.word_box = (14.55, 4.5, 0.17)
Corvid.rescue = (4.8, 2.25)
Corvid.formation = [(3.4, 2.15, 0.5), (14.2, 3.2, 0.6), (15.5, 2.2, 0.4)]
Corvid.refuel = (9.2, 10.0)

Gyrfalcon.paint_main, Gyrfalcon.paint_dark, Gyrfalcon.paint_belly = (142, 148, 153), (104, 111, 119), (150, 155, 160)
Gyrfalcon.camo_cut, Gyrfalcon.soot, Gyrfalcon.sawtooth = 0.52, 0.45, 0.0
Gyrfalcon.stations = [1.0, 2.6, 4.4, 9.6, 10.6, 11.8, 13.0, 14.3, 15.6, 16.8]
Gyrfalcon.spine_x = [0.3, 1.0]
Gyrfalcon.side_panels = [(3.0, 3.9, 2.1, 2.5), (9.2, 10.2, 2.2, 2.5), (14.0, 15.4, 2.2, 2.5), (11.0, 12.6, 1.9, 2.2)]
Gyrfalcon.belly_doors = [(4.8, 6.2, 0.0, 0.2), (11.2, 12.8, 1.0, 1.6)]
Gyrfalcon.hinges = {'wing': (0.82, 0.25, 0.92), 'fin': (0.7, 0.05, 0.7), 'stab': (0.95, 0.1, 0.9)}
Gyrfalcon.splits = {'wing': (0.62,)}
Gyrfalcon.lef = {}
Gyrfalcon.walkway = True
Gyrfalcon.insignia_wing = (4.4, 12.9)
Gyrfalcon.insignia_side = (1.3, 8.9, 2.25)
Gyrfalcon.tail_code, Gyrfalcon.modex, Gyrfalcon.word = 'GH', '211', 'NAVY'
Gyrfalcon.code_box = (17.2, 4.35, 0.7)
Gyrfalcon.modex_box = (3.2, 2.6, 0.38)
Gyrfalcon.word_box = (17.4, 4.9, 0.2)
Gyrfalcon.rescue = (5.4, 2.55)
Gyrfalcon.formation = [(3.7, 2.35, 0.5), (16.8, 3.7, 0.7), (17.6, 2.3, 0.4)]
Gyrfalcon.refuel = (10.4, 11.0)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default=None)
    ap.add_argument('--preview', default=None, help='also write each state as png + npz here')
    a = ap.parse_args()
    models = []
    for key, cls in AIRFRAMES.items():
        if a.only and key != a.only:
            continue
        models.append(build(cls, a.preview))
    idx = os.path.join(ROOT, 'src/main/resources/data/zomboidcraft/zc_cars/models.json')
    data = json.load(open(idx))
    ids = {m['id'] for m in models}
    data['models'] = [m for m in data['models'] if m['id'] not in ids] + models
    data['models'].sort(key=lambda m: m['id'])
    with open(idx, 'w') as f:
        json.dump(data, f, separators=(',', ':'))
    print(len(models), 'jets')


if __name__ == '__main__':
    main()
