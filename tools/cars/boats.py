#!/usr/bin/env python3
"""Builds the marina's boats: made here from measurements, not from a bought pack.

    python3 tools/cars/boats.py [--only boat_bowrider] [--preview OUT_DIR]

Each boat is modelled the way a yard would loft her: a hull from station sections (deep V with
chines and a spray rail for the powerboats, a round bilge with a fin keel for the sloop, a flat
riveted bottom for the skiff), then her decks and cockpit liners, and then what's fitted: consoles
with dashes and wheels, windshields in alloy frames, upholstered seats, bow rails, cleats, a bimini
or a T-top, outboards and sterndrives, a radar arch, a mast with its rigging and sail cover.
Everything is painted into one atlas per boat and state: the hull sides from her own chart
(antifouling, boot stripe, sheer stripes, registration numbers), a decal strip (transom name,
dash gauges, portlights, engine cowls) and tiles for the materials (non-skid, teak, vinyl,
canvas, carpet, stainless...). The states are the cars': usable, abandoned (grime, an algae line,
rust streaks, silvered teak), wrecked (glass gone, canvas shredded, scorched, holed, listing) and
burnt (charred, nothing soft left).

Model space as the cars (metres, nose toward +z, port toward +x, centred on x and z) except that
y = 0 is the keel and the waterline sits at WL = 0.89, a water block's surface: a boat placed in
the top water block floats at her lines. They're packed as .zcar like the cars and listed in
models.json with kind "boat" (CarModels.Model.boat): never on the street, nowhere to sit, and the
interact key searches her lockers.
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
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from pack import CELL, VARIANTS, heights, java_hash, scramble  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
WL = 0.89
UP = np.array([0.0, 1.0, 0.0])


def v3(*a):
    return np.array(a, float)


def unit(v):
    v = np.asarray(v, float)
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def spline(t, pts):
    """Catmull-Rom through control points [(t, value)...], clamped at the ends."""
    ts = [p[0] for p in pts]
    vs = [p[1] for p in pts]
    if t <= ts[0]:
        return vs[0]
    if t >= ts[-1]:
        return vs[-1]
    i = max(k for k in range(len(ts) - 1) if ts[k] <= t)
    p0 = vs[max(i - 1, 0)]
    p1, p2 = vs[i], vs[i + 1]
    p3 = vs[min(i + 2, len(vs) - 1)]
    s = (t - ts[i]) / (ts[i + 1] - ts[i])
    return 0.5 * (2 * p1 + (-p0 + p2) * s + (2 * p0 - 5 * p1 + 4 * p2 - p3) * s * s + (-p0 + 3 * p1 - 3 * p2 + p3) * s ** 3)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def frame(t):
    """Two unit vectors square to t and to each other."""
    t = unit(t)
    a = np.cross(t, UP)
    if np.linalg.norm(a) < 1e-6:
        a = np.cross(t, v3(1, 0, 0))
    a = unit(a)
    return a, np.cross(t, a)


# ---------------------------------------------------------------- triangles

def triangulate(P):
    """Ear clipping in the polygon's own plane; triangles keep the polygon's winding."""
    n = len(P)
    if n == 3:
        return [(0, 1, 2)]
    if n == 4:
        return [(0, 1, 2), (0, 2, 3)]
    N = np.zeros(3)
    for i in range(n):
        a, b = P[i], P[(i + 1) % n]
        N += [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]
    k = int(np.argmax(np.abs(N)))
    ax = [i for i in range(3) if i != k]
    Q = np.array(P)[:, ax]
    area = 0.5 * sum(Q[i, 0] * Q[(i + 1) % n, 1] - Q[(i + 1) % n, 0] * Q[i, 1] for i in range(n))
    idx = list(range(n))
    flip = area < 0
    if flip:
        idx.reverse()
    out = []

    def inside(p, a, b, c):
        d1 = (p[0] - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (p[1] - b[1])
        d2 = (p[0] - c[0]) * (b[1] - c[1]) - (b[0] - c[0]) * (p[1] - c[1])
        d3 = (p[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (p[1] - a[1])
        return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))

    while len(idx) > 3:
        m = len(idx)
        for t in range(m):
            i0, i1, i2 = idx[t - 1], idx[t], idx[(t + 1) % m]
            a, b, c = Q[i0], Q[i1], Q[i2]
            if (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0]) <= 1e-12:
                continue
            if any(inside(Q[j], a, b, c) for j in idx if j not in (i0, i1, i2)):
                continue
            out.append((i0, i1, i2))
            idx.pop(t)
            break
        else:
            break
    for t in range(1, len(idx) - 1):
        out.append((idx[0], idx[t], idx[t + 1]))
    return [(i, k, j) for i, j, k in out] if flip else out


class Geo:
    """Triangles with a material, a group (what a state may take away) and optionally a decal's uvs."""

    def __init__(self):
        self.T, self.M, self.G, self.UV = [], [], [], []
        self.group = 'body'

    def tri(self, a, b, c, mat, out=None, uv=None, both=False):
        a, b, c = (np.asarray(v, float) for v in (a, b, c))
        n = np.cross(b - a, c - a)
        if n @ n < 1e-14:
            return
        if out is not None and n @ np.asarray(out, float) < 0:
            b, c = c, b
            uv = None if uv is None else [uv[0], uv[2], uv[1]]
        for k in range(2 if both else 1):
            if k:
                b, c = c, b
                uv = None if uv is None else [uv[0], uv[2], uv[1]]
            self.T.append(np.stack([a, b, c]))
            self.M.append(mat)
            self.G.append(self.group)
            self.UV.append(None if uv is None else np.asarray(uv, float))

    def poly(self, pts, mat, out=None, uv=None, both=False):
        pts = [np.asarray(p, float) for p in pts]
        for i, j, k in triangulate(pts):
            self.tri(pts[i], pts[j], pts[k], mat, out, None if uv is None else [uv[i], uv[j], uv[k]], both)

    def quad(self, a, b, c, d, mat, out=None, uv=None, both=False):
        self.poly([a, b, c, d], mat, out, uv, both)


class grouped:
    """with grouped(g, 'canvas'): everything made inside belongs to that group."""

    def __init__(self, g, name):
        self.g, self.name = g, name

    def __enter__(self):
        self.old = self.g.group
        self.g.group = self.name

    def __exit__(self, *a):
        self.g.group = self.old


# ---------------------------------------------------------------- shapes

def box(g, c, h, mat, R=None, skip=()):
    """A box centred on c, half-sizes h, turned by R; skip names faces left off ('-y', '+z'...)."""
    c, h = np.asarray(c, float), np.asarray(h, float)
    R = np.eye(3) if R is None else R
    for a in range(3):
        u, v = [i for i in range(3) if i != a]
        for s in (-1, 1):
            if ('+' if s > 0 else '-') + 'xyz'[a] in skip:
                continue
            pts = []
            for su, sv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                e = np.zeros(3)
                e[a], e[u], e[v] = s, su, sv
                pts.append(c + R @ (h * e))
            n = np.zeros(3)
            n[a] = s
            g.poly(pts, mat, R @ n)


def blob(g, c, h, mat, e=(0.3, 0.3), nu=14, nv=8, R=None):
    """A rounded box (a superellipsoid): e near 0 is boxy, 1 an ellipsoid. Cushions, cowls, domes."""
    c, h = np.asarray(c, float), np.asarray(h, float)
    R = np.eye(3) if R is None else R

    def f(w, k):
        return math.copysign(abs(w) ** k, w)

    def p(i, j):
        u = -math.pi + 2 * math.pi * i / nu
        v = -math.pi / 2 + math.pi * j / nv
        x = h[0] * f(math.cos(v), e[1]) * f(math.cos(u), e[0])
        y = h[1] * f(math.sin(v), e[1])
        z = h[2] * f(math.cos(v), e[1]) * f(math.sin(u), e[0])
        return c + R @ v3(x, y, z)

    for i in range(nu):
        for j in range(nv):
            q = [p(i, j), p(i + 1, j), p(i + 1, j + 1), p(i, j + 1)]
            g.tri(q[0], q[1], q[2], mat, np.mean(q[:3], 0) - c)
            g.tri(q[0], q[2], q[3], mat, np.mean([q[0], q[2], q[3]], 0) - c)


def circle(n):
    return [(math.cos(2 * math.pi * k / n), math.sin(2 * math.pi * k / n)) for k in range(n)]


def tube(g, pts, r, mat, n=6, caps=True, prof=None, closed=False, up=None):
    """A sweep along a polyline: round (n sides) or the profile given, r a radius or one per point."""
    P = [np.asarray(p, float) for p in pts]
    m = len(P)
    rs = list(r) if hasattr(r, '__len__') else [r] * m
    prof = prof or circle(n)
    rings, tans = [], []
    for i in range(m):
        if closed:
            t = unit(P[(i + 1) % m] - P[i]) + unit(P[i] - P[i - 1])
        elif i == 0:
            t = P[1] - P[0]
        elif i == m - 1:
            t = P[-1] - P[-2]
        else:
            t = unit(P[i + 1] - P[i]) + unit(P[i] - P[i - 1])
        t = unit(t)
        if up is not None:
            a = unit(np.cross(t, up))
            b = np.cross(t, a)
        else:
            a, b = frame(t)
        rings.append([P[i] + rs[i] * (ca * a + cb * b) for ca, cb in prof])
        tans.append(t)
    k = len(prof)
    for i in range(m if closed else m - 1):
        i2 = (i + 1) % m
        mid = (P[i] + P[i2]) / 2
        for j in range(k):
            q = [rings[i][j], rings[i][(j + 1) % k], rings[i2][(j + 1) % k], rings[i2][j]]
            g.poly(q, mat, np.mean(q, 0) - mid)
    if caps and not closed:
        g.poly(rings[0], mat, -tans[0])
        g.poly(rings[-1], mat, tans[-1])


def rect(w, h):
    return [(-w, -h), (w, -h), (w, h), (-w, h)]


def lathe(g, c, prof, mat, n=12, axis=None, mats=None):
    """Turned about axis (default up) through c: prof [(radius, height)...] from the bottom middle,
    out and up and back to the top middle, so its faces look out."""
    c = np.asarray(c, float)
    ax = unit(UP if axis is None else axis)
    a, b = frame(ax)

    def p(k, r, y):
        ang = 2 * math.pi * k / n
        return c + ax * y + r * (math.cos(ang) * a + math.sin(ang) * b)

    for i in range(len(prof) - 1):
        (r0, y0), (r1, y1) = prof[i], prof[i + 1]
        nr, ny = (y1 - y0), -(r1 - r0)
        for k in range(n):
            ang = 2 * math.pi * (k + 0.5) / n
            out = nr * (math.cos(ang) * a + math.sin(ang) * b) + ny * ax
            q = [p(k, r0, y0), p(k + 1, r0, y0), p(k + 1, r1, y1), p(k, r1, y1)]
            g.poly(q, mats[i] if mats else mat, out)


def foil(chord, thick, n=8):
    """A symmetric foil section as (along, across) points: nose at +along."""
    pts = []
    for k in range(n + 1):
        s = k / n
        x = (1 - s) * chord - chord / 2
        y = 5 * thick * (0.2969 * math.sqrt(max(s, 0)) - 0.126 * s - 0.3516 * s * s + 0.2843 * s ** 3 - 0.1036 * s ** 4)
        pts.append((x, y))
    top = [(-x, y) for x, y in pts]
    return top + [(x, -y) for x, y in reversed(top[1:-1])]


def fin(g, sections, mat):
    """A lofted fin (keel, rudder, outboard leg): [(centre, chord, thick)...] stacked, nose toward +z."""
    rings = []
    for c, chord, thick in sections:
        c = np.asarray(c, float)
        rings.append([c + v3(y, 0, x) for x, y in foil(chord, thick)])
    k = len(rings[0])
    for i in range(len(rings) - 1):
        mid = (np.mean(rings[i], 0) + np.mean(rings[i + 1], 0)) / 2
        for j in range(k):
            q = [rings[i][j], rings[i][(j + 1) % k], rings[i + 1][(j + 1) % k], rings[i + 1][j]]
            g.poly(q, mat, np.mean(q, 0) - mid)
    g.poly(rings[0], mat, rings[0][0] * 0 + (np.asarray(sections[0][0]) - np.asarray(sections[1][0])))
    g.poly(rings[-1], mat, np.asarray(sections[-1][0]) - np.asarray(sections[-2][0]))


# ---------------------------------------------------------------- the hull

class Hull:
    """Station by station: half-beam at the sheer, sheer height, keel height and the section's shape.
    Curves are control points [(t, value)] with t from the transom (0) to the stem (1)."""

    def __init__(self, L, B, sheer, keel, plan, kind='v', dead=(20, 40), chine=0.84, flare=0.05, bow_closed=True,
                 bilge=None):
        self.L, self.B = L, B
        self.z0, self.z1 = -L / 2, L / 2
        self.sheer, self.keel, self.plan = sheer, keel, plan
        self.kind, self.dead, self.chine, self.flare = kind, dead, chine, flare
        self.bow_closed = bow_closed
        self.bilge = bilge

    def t(self, z):
        return min(1.0, max(0.0, (z - self.z0) / self.L))

    def xs(self, z):
        return max(0.0, self.B / 2 * spline(self.t(z), self.plan))

    def ys(self, z):
        return spline(self.t(z), self.sheer)

    def yk(self, z):
        return min(spline(self.t(z), self.keel), self.ys(z) - 0.02)

    def section(self, z):
        """Port half from the keel to the sheer: (x, y) points, the same count at every station."""
        t, xs, ys, yk = self.t(z), self.xs(z), self.ys(z), self.yk(z)
        if self.kind == 'round':
            yb = min(self.bilge if self.bilge else WL + 0.1, ys - 0.05)
            yb = max(yb, yk + 0.02)
            xb = xs * 0.97
            pts = [(0.0, yk)]
            for k in range(1, 7):
                ph = math.pi / 2 * k / 6
                pts.append((xb * math.sin(ph), yk + (yb - yk) * (1 - math.cos(ph))))
            pts.append((xs * 0.995, (yb + ys) / 2))
            pts.append((xs, ys))
            return pts
        if self.kind == 'flat':
            xc = xs * self.chine
            return [(0.0, yk), (xc * 0.5, yk), (xc, yk), (xc + 0.01, yk + 0.02),
                    (xc + (xs - xc) * 0.5 - self.flare * 0.3, yk + (ys - yk) * 0.5), (xs, ys)]
        dead = math.radians(self.dead[0] + (self.dead[1] - self.dead[0]) * t ** 2)
        xc = xs * self.chine * max(0.0, 1 - max(0.0, t - 0.86) / 0.14) ** 0.7
        yc = min(yk + xc * math.tan(dead), ys - 0.12)
        xc = min(xc, (yc - yk) / max(math.tan(dead), 1e-3))
        lip = 0.035 * min(1.0, xc / 0.4)
        mx = (xc + lip + xs) / 2 - self.flare * t
        return [(0.0, yk), (xc * 0.46, yk + xc * 0.46 * math.tan(dead)), (xc * 0.48, yk + xc * 0.46 * math.tan(dead) + 0.012),
                (xc, yc), (xc + lip, yc + 0.012), (mx, (yc + ys) / 2), (xs, ys)]

    def x_at(self, z, y):
        """The hull's half-width at height y (inside face, ignoring thickness)."""
        s = self.section(z)
        if y <= s[0][1]:
            return 0.0
        for (x0, y0), (x1, y1) in zip(s, s[1:]):
            if y0 <= y <= y1 and y1 > y0:
                return x0 + (x1 - x0) * (y - y0) / (y1 - y0)
        return s[-1][0]


def stations(hull, marks, step=0.2):
    zs = set(np.round(np.linspace(hull.z0, hull.z1, int(math.ceil(hull.L / step)) + 1), 4))
    zs |= {round(m, 4) for m in marks if hull.z0 < m < hull.z1}
    return sorted(zs)


def hull_shell(g, hull, zs):
    S = [hull.section(z) for z in zs]
    for side, mat in ((1, 'hull_port'), (-1, 'hull_stbd')):
        for i in range(len(zs) - 1):
            ymid = (hull.yk(zs[i]) + hull.ys(zs[i])) / 2
            for j in range(len(S[i]) - 1):
                q = [v3(side * S[a][b][0], S[a][b][1], zs[a]) for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
                c = np.mean(q, 0)
                g.quad(*q, mat, v3(side * max(abs(c[0]), 0.05), c[1] - ymid, 0), both=True)
    # the transom, and the bow's end when she has a flat one (the skiff)
    for z, out, closed in ((zs[0], -1, True), (zs[-1], 1, not hull.bow_closed)):
        if not closed:
            continue
        s = hull.section(z)
        pts = [v3(x, y, z) for x, y in s] + [v3(-x, y, z) for x, y in reversed(s[1:])]
        g.poly(pts, 'transom' if out < 0 else 'hull_port', v3(0, 0, out))


def deck_section(hull, z, kind, p):
    """Across the deck at z, port sheer to starboard: [(x, y, material of the span to the next)]."""
    xs, ys = hull.xs(z), hull.ys(z)
    if kind == 'deck':
        cam = p.get('camber', 0.06)
        cs = [1, 0.93, 0.6, 0.25, 0, -0.25, -0.6, -0.93, -1]
        return [(xs * c, ys + cam * (1 - c * c) * min(1, xs / 0.6), 'gelcoat' if abs(c) == 1 else p.get('deck', 'nonskid'))
                for c in cs]
    w = min(p.get('side', 0.22), xs * 0.45)
    xw = xs - w
    sole = p['sole']
    xi = max(0.05, min(xw, hull.x_at(z, sole) - 0.05))
    pad = p.get('pad', 0.3)
    yl = max(sole + 0.05, ys - pad)
    xl = xi + (xw - xi) * (yl - sole) / max(ys - sole, 1e-3)
    liner = p.get('liner', 'gelcoat')
    upper = p.get('upper', liner)
    sm = p.get('sole_mat', 'nonskid')
    return [(xs, ys, 'gelcoat'), (xw, ys + 0.01, upper), (xl, yl, liner), (xi, sole, sm),
            (-xi, sole, liner), (-xl, yl, upper), (-xw, ys + 0.01, 'gelcoat'), (-xs, ys, None)]


def decks(g, hull, zs, zones):
    """The deck, cockpits and their bulkheads from zones [(z from, z to, kind, params)]."""
    def zone(z):
        for z0, z1, kind, p in zones:
            if z0 <= z <= z1:
                return kind, p
        return 'deck', {}

    for i in range(len(zs) - 1):
        za, zb = zs[i], zs[i + 1]
        kind, p = zone((za + zb) / 2)
        A, B = deck_section(hull, za, kind, p), deck_section(hull, zb, kind, p)
        for j in range(len(A) - 1):
            mat = A[j][2]
            q = [v3(A[j][0], A[j][1], za), v3(B[j][0], B[j][1], zb), v3(B[j + 1][0], B[j + 1][1], zb), v3(A[j + 1][0], A[j + 1][1], za)]
            c = np.mean(q, 0)
            if kind == 'deck' or j in (0, len(A) - 2) or j == 3:
                out = UP
            else:
                out = v3(-c[0], 0.2, 0)    # a liner looks in across the cockpit
            g.quad(*q, mat, out)
    # bulkheads where a cockpit meets deck or a step, and the transom's inside
    bounds = sorted({z for z0, z1, _, _ in zones for z in (z0, z1)})
    for zb in bounds:
        if not hull.z0 <= zb <= hull.z1:
            continue
        aft = zone(zb - 0.01) if zb - 0.01 >= hull.z0 else ('deck', {'top': True})
        fwd = zone(zb + 0.01) if zb + 0.01 <= hull.z1 else ('deck', {})
        for (ka, pa), (kf, pf), out in ((aft, fwd, -1), (fwd, aft, 1)):
            if ka == 'cockpit' and (kf != 'cockpit' or pf['sole'] > pa['sole']):
                S = deck_section(hull, zb, 'cockpit', pa)
                lo = [(S[3][0], S[3][1]), (S[2][0], S[2][1]), (S[1][0], S[1][1])]
                if kf == 'cockpit':
                    top = [(x, pf['sole']) for x in (S[3][0], -S[3][0])]
                    pts = [v3(x, y, zb) for x, y in (lo[0],)] + [v3(x, y, zb) for x, y in top][::-1][::-1] + [v3(-lo[0][0], lo[0][1], zb)]
                    pts = [v3(lo[0][0], lo[0][1], zb), v3(lo[0][0], pf['sole'], zb), v3(-lo[0][0], pf['sole'], zb), v3(-lo[0][0], lo[0][1], zb)]
                else:
                    D = deck_section(hull, zb, 'deck', pf)
                    xw = S[1][0]
                    top = [(x, y) for x, y, _ in D if abs(x) < xw - 1e-6]
                    pts = [v3(x, y, zb) for x, y in lo] + [v3(xw, S[1][1], zb)] + [v3(x, y, zb) for x, y in top] + \
                          [v3(-xw, S[1][1], zb)] + [v3(-x, y, zb) for x, y in reversed(lo)]
                g.poly(pts, pa.get('bulkhead', 'gelcoat'), v3(0, 0, out))


def rubrail(g, hull, zs, r=0.035, mat='rubrail', drop=0.03):
    pts = [v3(hull.xs(z) + r * 0.4, hull.ys(z) - drop, z) for z in zs]
    pts = pts + [v3(-p[0], p[1], p[2]) for p in reversed(pts)]
    # round the stern: across the transom's top
    pts = [v3(-pts[0][0], pts[0][1], zs[0] - r * 0.4)] + pts + [v3(pts[0][0], pts[0][1], zs[0] - r * 0.4)]
    tube(g, pts, r, mat, n=5, up=UP)


# ---------------------------------------------------------------- fittings

def cleat(g, p, along, mat='steel'):
    p = np.asarray(p, float)
    a = unit(along)
    box(g, p + v3(0, 0.015, 0), v3(0.03, 0.015, 0.03), mat)
    tube(g, [p + v3(0, 0.03, 0) - a * 0.1 + v3(0, 0.01, 0), p + v3(0, 0.05, 0) - a * 0.05, p + v3(0, 0.055, 0),
             p + v3(0, 0.05, 0) + a * 0.05, p + v3(0, 0.03, 0) + a * 0.1 + v3(0, 0.01, 0)], 0.012, mat, n=5)


def wheel(g, c, normal, r=0.19, mat='black'):
    c = np.asarray(c, float)
    a, b = frame(normal)
    ring = [c + r * (math.cos(k * math.pi / 8) * a + math.sin(k * math.pi / 8) * b) for k in range(16)]
    tube(g, ring, 0.017, mat, n=6, closed=True)
    for k in range(3):
        ang = k * 2 * math.pi / 3 + math.pi / 2
        tube(g, [c, c + (r - 0.01) * (math.cos(ang) * a + math.sin(ang) * b)], 0.011, 'steel', n=4)
    lathe(g, c - unit(normal) * 0.03, [(0, 0), (0.035, 0), (0.035, 0.05), (0, 0.06)], 'steel', n=8, axis=normal)
    tube(g, [c - unit(normal) * 0.03, c - unit(normal) * 0.2], 0.02, 'black', n=6)


def seat(g, c, facing, w=0.26, sole=None, mat='vinyl', base='steel', back=0.42):
    """A helm bucket seat on a pedestal: cushion, backrest, the pedestal down to the sole."""
    c = np.asarray(c, float)
    R = rot_y(math.atan2(facing[0], facing[2]))
    with grouped(g, 'cushion'):
        blob(g, c, v3(w, 0.07, 0.24), mat, e=(0.25, 0.35), R=R)
        blob(g, c + R @ v3(0, 0.26, -0.24), v3(w, back / 2, 0.07), mat, e=(0.25, 0.35), R=R @ rot_x(-0.18))
        blob(g, c + R @ v3(0, 0.02, 0.0), v3(w * 0.96, 0.05, 0.23), 'vinyl_accent', e=(0.3, 0.4), nu=10, nv=4, R=R)
    if sole is not None:
        with grouped(g, 'seatbase'):
            tube(g, [c - v3(0, 0.06, 0), v3(c[0], sole, c[2])], 0.04, base, n=8)
            lathe(g, v3(c[0], sole, c[2]), [(0, 0), (0.15, 0), (0.12, 0.03), (0, 0.035)], base, n=10)


def bench(g, x0, x1, y, z, depth, mat='vinyl', back=None, R=None):
    c = v3((x0 + x1) / 2, y, z)
    with grouped(g, 'cushion'):
        blob(g, c, v3(abs(x1 - x0) / 2, 0.07, depth / 2), mat, e=(0.2, 0.35), nu=16)
        if back:
            blob(g, c + v3(0, 0.25, back * (depth / 2 + 0.05)), v3(abs(x1 - x0) / 2, 0.2, 0.06), mat, e=(0.2, 0.35),
                 nu=16, R=rot_x(0.15 * back))


def rail(g, pts, posts, r=0.016, mat='steel', base=None):
    """A bow rail: the top tube through pts, and posts down from those indices to their bases."""
    tube(g, pts, r, mat, n=6)
    for i, b in posts:
        tube(g, [pts[i], b], r * 0.9, mat, n=5)
        lathe(g, b, [(0, 0), (0.035, 0), (0.03, 0.02), (0, 0.025)], mat, n=6)


def navlights(g, p, w=0.06):
    p = np.asarray(p, float)
    box(g, p + v3(0.03, 0, 0), v3(0.03, 0.025, w), 'navred')
    box(g, p - v3(0.03, 0, 0), v3(0.03, 0.025, w), 'navgreen')


def fender(g, top, hang, mat='fender'):
    """A fender hung on its line over the side."""
    top, hang = np.asarray(top, float), np.asarray(hang, float)
    with grouped(g, 'fender'):
        tube(g, [top, hang + v3(0, 0.3, 0)], 0.006, 'rope', n=4)
        lathe(g, hang - v3(0, 0.25, 0), [(0, 0), (0.06, 0.01), (0.1, 0.06), (0.1, 0.44), (0.06, 0.49), (0, 0.5)], mat, n=10)


def outboard(g, x, z, ytop, s=1.0, mat='engine', label=True, tiller=False):
    """A big four-stroke on the transom: clamp bracket, cowl (with its decal), midsection, leg, prop."""
    zc = z - 0.32 * s
    box(g, v3(x, ytop - 0.1, z - 0.05), v3(0.12 * s, 0.14, 0.05), 'engine_metal')
    blob(g, v3(x, ytop + 0.33 * s, zc - 0.02), v3(0.25 * s, 0.3 * s, 0.38 * s), mat, e=(0.6, 0.62), nu=16, nv=10)
    blob(g, v3(x, ytop + 0.04 * s, zc), v3(0.23 * s, 0.1 * s, 0.34 * s), 'engine_lower', e=(0.4, 0.4), nu=14, nv=6)
    if label:
        for side in (1, -1):
            xq = x + side * 0.247 * s
            pts = [v3(xq, ytop + 0.22 * s, zc + side * 0.22 * s), v3(xq, ytop + 0.22 * s, zc - side * 0.26 * s),
                   v3(xq, ytop + 0.42 * s, zc - side * 0.26 * s), v3(xq, ytop + 0.42 * s, zc + side * 0.22 * s)]
            g.quad(*pts, 'cowl', v3(side, 0, 0), uv=[(0, 1), (1, 1), (1, 0), (0, 0)])
    ylow = -0.18 if not tiller else WL - 0.45
    ys_ = [ytop - 0.05, (ytop + ylow) / 2 + 0.1, ylow + 0.18]
    fin(g, [(v3(x, y, zc + 0.02), 0.34 * s * (1 - 0.18 * k), 0.12) for k, y in enumerate(ys_)], 'engine_lower')
    box(g, v3(x, ylow + 0.33, zc - 0.02), v3(0.16 * s, 0.008, 0.24 * s), 'engine_lower')
    blob(g, v3(x, ylow + 0.12, zc), v3(0.06 * s, 0.06 * s, 0.24 * s), 'engine_lower', e=(1, 1), nu=10, nv=6)
    fin(g, [(v3(x, ylow + 0.08, zc + 0.02), 0.2 * s, 0.1), (v3(x, ylow - 0.06, zc - 0.02), 0.12 * s, 0.1)], 'engine_lower')
    hub = v3(x, ylow + 0.12, zc - 0.26 * s)
    lathe(g, hub, [(0, 0), (0.04, 0.01), (0.035, 0.08), (0, 0.1)], 'prop', n=8, axis=v3(0, 0, -1))
    for k in range(3):
        ang = k * 2 * math.pi / 3 + 0.3
        d = v3(math.cos(ang), math.sin(ang), 0)
        t = v3(-math.sin(ang), math.cos(ang), 0)
        tip = hub + d * 0.17 * s
        g.quad(hub + d * 0.03 + t * 0.05 + v3(0, 0, -0.03), tip + t * 0.04 + v3(0, 0, -0.05),
               tip - t * 0.03 + v3(0, 0, -0.02), hub + d * 0.03 - t * 0.04 + v3(0, 0, -0.06), 'prop', both=True)
    if tiller:
        tube(g, [v3(x, ytop + 0.1 * s, zc + 0.2 * s), v3(x, ytop + 0.16 * s, zc + 0.7 * s)], 0.022, 'black', n=6)


def sterndrive(g, x, z0, s=1.0):
    """A sterndrive's leg under the swim platform: gimbal housing, the leg and its prop."""
    zc = z0 - 0.3 * s
    blob(g, v3(x, WL + 0.02, z0 - 0.08), v3(0.2 * s, 0.14, 0.1), 'engine_lower', e=(0.5, 0.5), nu=10, nv=6)
    fin(g, [(v3(x, WL + 0.05, zc + 0.05), 0.42 * s, 0.14), (v3(x, WL - 0.35, zc), 0.34 * s, 0.13), (v3(x, WL - 0.55, zc), 0.3 * s, 0.12)],
        'engine_lower')
    box(g, v3(x, WL - 0.18, zc - 0.02), v3(0.14 * s, 0.008, 0.24 * s), 'engine_lower')
    blob(g, v3(x, WL - 0.62, zc), v3(0.06, 0.06, 0.24 * s), 'engine_lower', e=(1, 1), nu=10, nv=6)
    hub = v3(x, WL - 0.62, zc - 0.26 * s)
    for k in range(3):
        ang = k * 2 * math.pi / 3 + 0.5
        d = v3(math.cos(ang), math.sin(ang), 0)
        t = v3(-math.sin(ang), math.cos(ang), 0)
        tip = hub + d * 0.18 * s
        g.quad(hub + t * 0.05, tip + t * 0.04 + v3(0, 0, -0.03), tip - t * 0.03, hub - t * 0.04 + v3(0, 0, -0.05), 'prop', both=True)


def windshield(g, zb, ybase, xmax, rise, rake, wrap, gap=0.0, frame_mat='alu', panels=3, centre_open=False):
    """A curved, raked windshield in an alloy frame: its foot along z = zb - wrap (x/xmax)^2."""
    def base(x):
        return v3(x, ybase(x), zb - wrap * (x / xmax) ** 2)

    def top(x):
        b = base(x * 0.97)
        return b + v3(-x * 0.03, rise * (1 - 0.15 * (x / xmax) ** 2), -rake * (1 - 0.3 * (x / xmax) ** 2))

    spans = []
    if gap > 0:
        edges = np.linspace(gap / 2, xmax, panels + 1)
        spans += [(a, b) for a, b in zip(edges, edges[1:])]
        spans += [(-b, -a) for a, b in zip(edges, edges[1:])]
    else:
        edges = np.linspace(-xmax, xmax, 2 * panels + 1)
        spans += list(zip(edges, edges[1:]))
    for k, (a, b) in enumerate(spans):
        with grouped(g, f'glass{k}'):
            xs = np.linspace(a, b, 4)
            for x0, x1 in zip(xs, xs[1:]):
                g.quad(base(x0), base(x1), top(x1), top(x0), 'glass', both=True)
        for x in (a, b):
            tube(g, [base(x), top(x)], 0.016, frame_mat, n=5)
    for lo, hi in ((gap / 2, xmax), (-xmax, -gap / 2)) if gap > 0 else ((-xmax, xmax),):
        xs = np.linspace(lo, hi, 9)
        tube(g, [top(x) + v3(0, 0.012, 0) for x in xs], 0.02, frame_mat, n=5)
        tube(g, [base(x) for x in xs], 0.018, frame_mat, n=4)
    if centre_open and gap > 0:
        # the walk-through's middle pane, swung open to port
        hinge = top(gap / 2)
        with grouped(g, 'glass_mid'):
            p0, p1 = base(gap / 2), top(gap / 2)
            swing = v3(-0.15, 0, -gap * 0.95)
            g.quad(p0, p1, p1 + swing, p0 + swing, 'glass', both=True)
        tube(g, [hinge, hinge + v3(-0.15, 0, -gap * 0.95)], 0.014, frame_mat, n=4)
    return base, top


def canopy(g, x_half, z_front, z_back, y_top, sag=0.06, mat='canvas', droop=0.12, nx=8, nz=5):
    """A canvas top stretched over its bows: flat-ish crown, sides drooping."""
    pts = {}
    for i in range(nx + 1):
        for j in range(nz + 1):
            u = -1 + 2 * i / nx
            w = j / nz
            z = z_front + (z_back - z_front) * w
            y = y_top - droop * u ** 4 - sag * math.sin(math.pi * w) * (1 - u * u) * 0.5
            pts[i, j] = v3(x_half * u, y, z)
    with grouped(g, 'canvas'):
        for i in range(nx):
            for j in range(nz):
                g.quad(pts[i, j], pts[i + 1, j], pts[i + 1, j + 1], pts[i, j + 1], mat, UP, both=True)
    return pts


def lifejacket(g, c, yaw=0.0):
    R = rot_y(yaw)
    with grouped(g, 'loose'):
        blob(g, c, v3(0.22, 0.05, 0.28), 'lifevest', e=(0.4, 0.6), R=R, nu=10, nv=6)
        box(g, c + R @ v3(0, 0.05, 0.05), v3(0.18, 0.006, 0.02), 'black', R=R)


def coil(g, c, r=0.18, mat='rope', turns=3):
    with grouped(g, 'loose'):
        for k in range(turns):
            rr = r - k * 0.025
            pts = [np.asarray(c) + v3(rr * math.cos(a), 0.015 + k * 0.012, rr * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 13)[:-1]]
            tube(g, pts, 0.012, mat, n=4, closed=True)


def cooler(g, c, yaw=0.0, mat='cooler'):
    R = rot_y(yaw)
    box(g, c + R @ v3(0, 0.17, 0), v3(0.32, 0.17, 0.2), mat, R=R)
    box(g, c + R @ v3(0, 0.36, 0), v3(0.33, 0.025, 0.21), mat, R=R)
    tube(g, [c + R @ v3(0.34, 0.25, -0.06), c + R @ v3(0.36, 0.25, 0), c + R @ v3(0.34, 0.25, 0.06)], 0.01, 'black', n=4)
    tube(g, [c + R @ v3(-0.34, 0.25, -0.06), c + R @ v3(-0.36, 0.25, 0), c + R @ v3(-0.34, 0.25, 0.06)], 0.01, 'black', n=4)


def antenna(g, base, h, r=0.012):
    lathe(g, base, [(0, 0), (0.03, 0), (0.025, 0.08), (0, 0.09)], 'black', n=6)
    tube(g, [base + v3(0, 0.08, 0), base + v3(0, h, 0)], [r, r * 0.4], 'antenna', n=4)


def hatch(g, c, hx, hz, y, smoked=True):
    """A deck hatch: a raised alloy frame round a smoked acrylic lid."""
    c = np.asarray(c, float)
    box(g, v3(c[0], y + 0.02, c[2]), v3(hx, 0.02, hz), 'alu', skip=('-y',))
    box(g, v3(c[0], y + 0.045, c[2]), v3(hx - 0.03, 0.006, hz - 0.03), 'smoked' if smoked else 'gelcoat', skip=('-y',))


# ---------------------------------------------------------------- the boats

def bowrider(spec):
    """A 21-foot bowrider: sterndrive, open bow, walk-through windshield, bimini over the cockpit."""
    g = Geo()
    h = Hull(6.4, 2.4, sheer=[(0, 1.58), (0.5, 1.68), (0.85, 1.86), (1, 1.98)],
             keel=[(0, 0.02), (0.5, 0.0), (0.7, 0.12), (0.88, 0.6), (1, 1.72)],
             plan=[(0, 0.86), (0.3, 0.99), (0.45, 1.0), (0.7, 0.86), (0.88, 0.55), (1, 0.0)], dead=(21, 42))
    sole_a, sole_b = WL + 0.16, WL + 0.3
    zw = 0.95
    zones = [(-3.2, -2.55, 'deck', {'camber': 0.02}),
             (-2.55, zw - 0.15, 'cockpit', {'sole': sole_a, 'side': 0.2, 'upper': 'vinyl', 'liner': 'vinyl_lo', 'sole_mat': 'carpet'}),
             (zw - 0.15, zw + 0.42, 'cockpit', {'sole': sole_a, 'side': 0.2, 'upper': 'gelcoat', 'liner': 'gelcoat', 'sole_mat': 'carpet'}),
             (zw + 0.42, 2.45, 'cockpit', {'sole': sole_b, 'side': 0.17, 'upper': 'vinyl', 'liner': 'vinyl_lo', 'sole_mat': 'carpet'}),
             (2.45, 3.2, 'deck', {'camber': 0.05})]
    zs = stations(h, [z for a, b, _, _ in zones for z in (a, b)])
    hull_shell(g, h, zs)
    decks(g, h, zs, zones)
    rubrail(g, h, zs)
    # swim platform with its teak top and a folding ladder, and the sterndrive under it
    zt = h.z0
    box(g, v3(0, WL + 0.3, zt - 0.32), v3(0.95, 0.04, 0.32), 'gelcoat', skip=('+z',))
    box(g, v3(0, WL + 0.345, zt - 0.32), v3(0.92, 0.008, 0.29), 'teak', skip=('-y',))
    for x in (0.55, 0.75):
        tube(g, [v3(x, WL + 0.36, zt - 0.6), v3(x, WL + 0.45, zt - 0.6), v3(x, WL + 0.45, zt - 0.2)], 0.014, 'steel', n=5)
    for k in range(3):
        tube(g, [v3(0.55, WL + 0.45, zt - 0.25 - k * 0.12), v3(0.75, WL + 0.45, zt - 0.25 - k * 0.12)], 0.012, 'steel', n=4)
    sterndrive(g, 0, zt - 0.05)
    # the engine box aft, with its sunpad, and the stern seat in front of it
    ys0 = h.ys(-2.55)
    box(g, v3(0, (sole_a + ys0) / 2 + 0.05, -2.3), v3(0.62, (ys0 - sole_a) / 2 + 0.05, 0.28), 'gelcoat')
    with grouped(g, 'cushion'):
        blob(g, v3(0, ys0 + 0.14, -2.3), v3(0.6, 0.05, 0.26), 'vinyl', e=(0.2, 0.4))
    for side in (1, -1):
        x0 = h.x_at(-2.2, sole_a + 0.3) - 0.08
        bench(g, side * 0.66, side * x0, sole_a + 0.4, -2.05, 0.46, back=-1)
        box(g, v3(side * (0.66 + x0) / 2, sole_a + 0.17, -2.05), v3(abs(x0 - 0.66) / 2, 0.17, 0.22), 'gelcoat')
    # consoles either side of the walk-through, the helm to starboard
    for side in (1, -1):
        xin, xout = 0.3, h.x_at(zw + 0.1, h.ys(zw)) - 0.12
        c = v3(side * (xin + xout) / 2, (sole_a + h.ys(zw)) / 2 + 0.02, zw + 0.1)
        blob(g, c, v3((xout - xin) / 2, (h.ys(zw) - sole_a) / 2 + 0.05, 0.3), 'gelcoat', e=(0.15, 0.3), nu=16)
        # the dash: a raked face on the console's aft top
        y0, y1 = h.ys(zw) - 0.02, h.ys(zw) + 0.1
        d = [v3(side * xin, y0, zw - 0.17), v3(side * xout, y0, zw - 0.17), v3(side * xout, y1, zw + 0.02), v3(side * xin, y1, zw + 0.02)]
        uv = [(0, 1), (1, 1), (1, 0), (0, 0)] if side < 0 else [(1, 1), (0, 1), (0, 0), (1, 0)]
        g.quad(*d, 'dash' if side < 0 else 'dash_glove', v3(0, 0.6, -1), uv=uv)
        g.quad(d[2], d[3], d[3] + v3(0, 0.0, 0.4), d[2] + v3(0, 0.0, 0.4), 'black_vinyl', UP)
    wheel(g, v3(-0.6, h.ys(zw) + 0.08, zw - 0.3), v3(0, 0.55, -0.85))
    tube(g, [v3(-0.95, h.ys(zw) - 0.05, zw - 0.2), v3(-0.97, h.ys(zw) + 0.1, zw - 0.28)], 0.012, 'steel', n=4)
    blob(g, v3(-0.97, h.ys(zw) + 0.12, zw - 0.29), v3(0.025, 0.025, 0.025), 'black', e=(1, 1), nu=6, nv=4)
    xmax = h.x_at(zw, h.ys(zw)) - 0.02
    windshield(g, zw + 0.25, lambda x: h.ys(zw + 0.25 - 0.4 * (x / xmax) ** 2) + 0.07, xmax, 0.42, 0.32, 0.4, gap=0.56,
               centre_open=True, panels=2)
    seat(g, v3(-0.6, sole_a + 0.48, zw - 0.75), v3(0, 0, 1), sole=sole_a)
    seat(g, v3(0.6, sole_a + 0.48, zw - 0.75), v3(0, 0, 1), sole=sole_a)
    # the bow's lounges, wrapping round the forepeak
    for side in (1, -1):
        for z0, z1 in ((zw + 0.5, 1.5), (1.5, 2.25)):
            zc = (z0 + z1) / 2
            xo = h.x_at(zc, sole_b + 0.35) - 0.05
            yaw = math.atan2(h.xs(z1) - h.xs(z0), z1 - z0) * side
            bench(g, side * (xo - 0.38), side * xo, sole_b + 0.38, zc, z1 - z0, R=None)
            box(g, v3(side * (xo - 0.2), sole_b + 0.15, zc), v3(0.19, 0.15, (z1 - z0) / 2), 'gelcoat', R=rot_y(yaw))
    bench(g, -0.35, 0.35, sole_b + 0.38, 2.3, 0.28)
    # the bow: rail, cleat, lights, anchor locker
    pts = [v3(h.xs(z) - 0.06, h.ys(z) + 0.38, z) for z in np.linspace(1.4, 3.05, 7)]
    pts = pts + [v3(0, h.ys(3.18) + 0.38, 3.18)] + [v3(-p[0], p[1], p[2]) for p in reversed(pts)]
    rail(g, pts, [(i, v3(pts[i][0] * 1.0, h.ys(pts[i][2]) + 0.01, pts[i][2])) for i in (0, 3, 6, 8, 11, 14)])
    cleat(g, v3(0, h.ys(2.7) + 0.05, 2.7), v3(0, 0, 1))
    navlights(g, v3(0, h.ys(3.1) + 0.07, 3.05))
    hatch(g, v3(0, 0, 2.75), 0.22, 0.2, h.ys(2.75) + 0.03, smoked=False)
    for z in (-2.9, -0.2, 1.8):
        for side in (1, -1):
            cleat(g, v3(side * (h.xs(z) - 0.1), h.ys(z) + 0.01, z), v3(0, 0, 1))
    # the stern light's pole and the bimini, up over the cockpit on its bows
    tube(g, [v3(0.9, h.ys(-2.9), -2.9), v3(0.9, h.ys(-2.9) + 0.95, -2.9)], 0.015, 'steel', n=5)
    blob(g, v3(0.9, h.ys(-2.9) + 0.98, -2.9), v3(0.03, 0.035, 0.03), 'lens', e=(1, 1), nu=6, nv=4)
    ytop = sole_a + 1.95
    for zb, zt2 in ((-0.55, -0.6), (-1.75, -1.6)):
        xb = h.xs(zb) - 0.05
        arc = [v3(xb * math.cos(a), h.ys(zb) + (ytop - h.ys(zb)) * math.sin(a) ** 0.6, zb + (zt2 - zb) * math.sin(a)) for a in np.linspace(0, math.pi, 11)]
        tube(g, arc, 0.013, 'steel', n=5)
    canopy(g, h.xs(-1.0) - 0.08, -0.62, -1.62, ytop - 0.01, droop=0.1)
    for side in (1, -1):
        tube(g, [v3(side * (h.xs(-0.62) - 0.12), ytop - 0.1, -0.62), v3(side * 0.85, h.ys(zw) + 0.48, zw - 0.05)], 0.005, 'strap', n=3)
    # what's lying about
    coil(g, v3(0.3, sole_a, -1.3))
    lifejacket(g, v3(-0.2, sole_b + 0.02, 1.6), 0.6)
    cooler(g, v3(0.25, sole_a, -0.15), 0.2)
    for side, z in ((1, -1.1), (-1, 0.4)):
        fender(g, v3(side * (h.xs(z) - 0.05), h.ys(z) + 0.03, z), v3(side * (h.xs(z) + 0.12), h.ys(z) - 0.25, z))
    return g, h


def deck_y(h, z, x, cam=0.06):
    xs = h.xs(z)
    return h.ys(z) + cam * (1 - min(1.0, (x / max(xs, 1e-3)) ** 2)) * min(1, xs / 0.6)


def trunk(g, h, za, zf, inset, yroof, rnd=0.9, rake=0.25, roofmat='nonskid', cam=0.06, crown=0.05):
    """A cabin trunk on the deck: its sides lean in to the roof, its front rounds off in plan and rakes
    aft, its aft face stands square over the cockpit. The sides carry the portlights' decal."""
    def half(z):
        w = h.xs(z) - inset
        if z > zf - rnd:
            w *= math.sqrt(max(0.0, 1 - ((z - (zf - rnd)) / rnd) ** 2))
        return max(w, 0.0)

    zs_ = list(np.linspace(za, zf, 16))
    ring = []
    for z in zs_:
        w = half(z)
        wr = max(0.0, half(min(zf, z + rake)) - 0.06)
        ring.append((z, w, deck_y(h, z, w, cam), wr))
    for (z0, w0, y0, r0), (z1, w1, y1, r1) in zip(ring, ring[1:]):
        u0, u1 = (z0 - za) / (zf - za), (z1 - za) / (zf - za)
        for side in (1, -1):
            q = [v3(side * w0, y0, z0), v3(side * w1, y1, z1), v3(side * r1, yroof, z1), v3(side * r0, yroof, z0)]
            uu = (lambda u: u) if side > 0 else (lambda u: 1 - u)
            uv = [(uu(u0), 1), (uu(u1), 1), (uu(u1), 0), (uu(u0), 0)]
            g.quad(*q, 'cabin_side', v3(side, 0.3, 0), uv=uv)
        cs = [1, 0.85, 0.4, 0, -0.4, -0.85, -1]
        for a, b in zip(cs, cs[1:]):
            q = [v3(r0 * a, yroof + crown * (1 - a * a), z0), v3(r1 * a, yroof + crown * (1 - a * a), z1),
                 v3(r1 * b, yroof + crown * (1 - b * b), z1), v3(r0 * b, yroof + crown * (1 - b * b), z0)]
            g.quad(*q, 'gelcoat' if abs(a) == 1 or abs(b) == 1 else roofmat, UP)
    z0, w0, y0, r0 = ring[0]
    cs = [0.85, 0.4, 0, -0.4, -0.85]
    pts = [v3(w0, y0, z0), v3(r0, yroof, z0)] + [v3(r0 * c, yroof + crown * (1 - c * c), z0) for c in cs] + \
          [v3(-r0, yroof, z0), v3(-w0, y0, z0)]
    g.poly(pts, 'gelcoat', v3(0, 0, -1))
    return half


def companionway(g, z, y0, y1, w=0.32):
    """The cabin door: louvred teak boards in a frame, a hair proud of the aft face."""
    g.quad(v3(w, y0, z - 0.012), v3(-w, y0, z - 0.012), v3(-w, y1, z - 0.012), v3(w, y1, z - 0.012), 'cabin_front',
           v3(0, 0, -1), uv=[(0, 1), (1, 1), (1, 0), (0, 0)])


def centre_console(spec):
    """A 23-foot centre console: twin outboards, T-top with rod holders, leaning post, casting deck."""
    g = Geo()
    h = Hull(7.0, 2.5, sheer=[(0, 1.64), (0.5, 1.74), (0.85, 1.96), (1, 2.14)],
             keel=[(0, 0.02), (0.5, 0.0), (0.7, 0.14), (0.88, 0.66), (1, 1.88)],
             plan=[(0, 0.88), (0.3, 0.99), (0.45, 1.0), (0.7, 0.85), (0.88, 0.52), (1, 0.0)], dead=(22, 44))
    sole, solef = WL + 0.2, WL + 0.44
    zones = [(-3.5, 1.75, 'cockpit', {'sole': sole, 'side': 0.17, 'pad': 0.25, 'sole_mat': 'nonskid'}),
             (1.75, 2.95, 'cockpit', {'sole': solef, 'side': 0.15, 'pad': 0.25, 'sole_mat': 'nonskid'}),
             (2.95, 3.5, 'deck', {'camber': 0.05})]
    zs = stations(h, [z for a, b, _, _ in zones for z in (a, b)])
    hull_shell(g, h, zs)
    decks(g, h, zs, zones)
    rubrail(g, h, zs)
    zt = h.z0
    yt = h.ys(zt)
    for x in (0.44, -0.44):
        outboard(g, x, zt, yt - 0.08, s=1.0)
    box(g, v3(0, yt - 0.25, zt - 0.02), v3(0.75, 0.2, 0.03), 'engine_metal')
    # the transom's bait station: livewell, sink and cutting board
    box(g, v3(0, sole + 0.33, zt + 0.25), v3(0.7, 0.33, 0.25), 'gelcoat')
    hatch(g, v3(-0.3, 0, zt + 0.25), 0.22, 0.17, sole + 0.66)
    box(g, v3(0.33, sole + 0.68, zt + 0.25), v3(0.25, 0.012, 0.18), 'cooler')
    # the console: dash, wheel, throttles, compass, a plotter, its windshield and grab rail
    zc0, zc1, xc = 0.2, 1.1, 0.44
    yc = sole + 1.1
    blob(g, v3(0, (sole + yc) / 2, (zc0 + zc1) / 2), v3(xc, (yc - sole) / 2, (zc1 - zc0) / 2), 'gelcoat', e=(0.12, 0.2), nu=16, nv=8)
    g.quad(v3(xc - 0.05, sole + 0.15, zc1 + 0.005), v3(-xc + 0.05, sole + 0.15, zc1 + 0.005),
           v3(-xc + 0.05, yc - 0.1, zc1 + 0.005), v3(xc - 0.05, yc - 0.1, zc1 + 0.005), 'console', v3(0, 0, 1),
           uv=[(0, 1), (1, 1), (1, 0), (0, 0)])
    d = [v3(-xc + 0.03, yc - 0.18, zc0 - 0.02), v3(xc - 0.03, yc - 0.18, zc0 - 0.02), v3(xc - 0.03, yc + 0.02, zc0 + 0.25), v3(-xc + 0.03, yc + 0.02, zc0 + 0.25)]
    g.quad(*d, 'dash', v3(0, 0.6, -1), uv=[(1, 1), (0, 1), (0, 0), (1, 0)])
    wheel(g, v3(-0.15, yc - 0.2, zc0 - 0.12), v3(0, 0.45, -0.9))
    blob(g, v3(0.25, yc - 0.12, zc0 - 0.02), v3(0.08, 0.05, 0.1), 'black', e=(0.4, 0.4), nu=8, nv=4)
    for x in (0.22, 0.28):
        tube(g, [v3(x, yc - 0.08, zc0 - 0.02), v3(x, yc + 0.04, zc0 - 0.1)], 0.01, 'steel', n=4)
    lathe(g, v3(0, yc + 0.02, zc0 + 0.35), [(0, 0), (0.08, 0), (0.07, 0.05), (0.04, 0.08), (0, 0.085)], 'black', n=10)
    box(g, v3(0.18, yc + 0.08, zc0 + 0.32), v3(0.14, 0.1, 0.02), 'smoked', R=rot_x(-0.25))
    windshield(g, zc1 - 0.15, lambda x: yc + 0.02, xc - 0.04, 0.38, 0.22, 0.15, panels=1)
    loop = [v3(xc + 0.04, yc - 0.05, z) for z in (zc0 + 0.3, zc1 - 0.1)] + [v3(xc - 0.1, yc - 0.05, zc1 + 0.06), v3(-xc + 0.1, yc - 0.05, zc1 + 0.06)] + \
           [v3(-xc - 0.04, yc - 0.05, z) for z in (zc1 - 0.1, zc0 + 0.3)]
    tube(g, loop, 0.016, 'steel', n=5)
    # the T-top: four legs, a hardtop with a dome and whips, rod holders along its back
    yT = sole + 2.12
    legs = [v3(sx * (xc - 0.05), sole, z) for sx in (1, -1) for z in (zc0 + 0.05, zc1 - 0.05)]
    for p0 in legs:
        tube(g, [p0, v3(p0[0] * 1.6, sole + 1.25, p0[2] + (0.1 if p0[2] > 0.6 else -0.25)), v3(p0[0] * 1.9, yT - 0.06, p0[2] + (0.1 if p0[2] > 0.6 else -0.35))], 0.024, 'alu', n=6)
    blob(g, v3(0, yT, 0.55), v3(0.95, 0.05, 1.05), 'gelcoat', e=(0.15, 0.35), nu=16, nv=6)
    tube(g, [v3(0.85, yT - 0.06, z) for z in (-0.4, 1.5)], 0.022, 'alu', n=5)
    tube(g, [v3(-0.85, yT - 0.06, z) for z in (-0.4, 1.5)], 0.022, 'alu', n=5)
    lathe(g, v3(0, yT + 0.05, 0.6), [(0, 0), (0.22, 0), (0.22, 0.1), (0.15, 0.18), (0, 0.2)], 'cooler', n=12)
    for x in (0.7, -0.7):
        antenna(g, v3(x, yT + 0.04, 1.3), 2.3)
    for k in range(5):
        x = -0.6 + k * 0.3
        tube(g, [v3(x, yT - 0.02, -0.5), v3(x, yT + 0.35, -0.68)], 0.025, 'alu', n=5)
    # the leaning post with its cooler seat, rod holders in the gunwales, the bow's hatches and rail
    zl = -0.45
    for x in (0.45, -0.45):
        tube(g, [v3(x, sole, zl - 0.15), v3(x, sole + 0.95, zl - 0.15)], 0.022, 'alu', n=5)
    tube(g, [v3(0.45, sole + 0.95, zl - 0.15), v3(-0.45, sole + 0.95, zl - 0.15)], 0.022, 'alu', n=5)
    with grouped(g, 'cushion'):
        blob(g, v3(0, sole + 0.9, zl - 0.12), v3(0.48, 0.14, 0.09), 'vinyl', e=(0.25, 0.4))
        blob(g, v3(0, sole + 1.2, zl - 0.2), v3(0.4, 0.14, 0.07), 'vinyl', e=(0.25, 0.4), R=rot_x(-0.15))
    cooler(g, v3(0, sole, zl + 0.25))
    for side in (1, -1):
        for z in (-2.6, -2.0, -1.4, -0.8):
            x = side * (h.xs(z) - 0.09)
            tube(g, [v3(x, h.ys(z) - 0.15, z + 0.06), v3(x, h.ys(z) + 0.03, z)], 0.024, 'steel', n=6)
        for z in (-3.0, -0.2, 2.1):
            cleat(g, v3(side * (h.xs(z) - 0.08), h.ys(z) + 0.01, z), v3(0, 0, 1))
    hatch(g, v3(0.4, 0, 2.2), 0.28, 0.3, solef)
    hatch(g, v3(-0.4, 0, 2.2), 0.28, 0.3, solef)
    hatch(g, v3(0, 0, 3.15), 0.2, 0.18, h.ys(3.15) + 0.04, smoked=False)
    pts = [v3(h.xs(z) - 0.05, h.ys(z) + 0.3, z) for z in np.linspace(1.6, 3.3, 6)]
    pts = pts + [v3(0, h.ys(3.42) + 0.3, 3.42)] + [v3(-p[0], p[1], p[2]) for p in reversed(pts)]
    rail(g, pts, [(i, v3(pts[i][0], h.ys(pts[i][2]) + 0.01, pts[i][2])) for i in (0, 3, 5, 7, 10, 12)])
    navlights(g, v3(0, h.ys(3.35) + 0.07, 3.3))
    tube(g, [v3(0.0, h.ys(-3.3) + 0.0, -3.3), v3(0.0, h.ys(-3.3) + 1.0, -3.3)], 0.015, 'steel', n=5)
    blob(g, v3(0.0, h.ys(-3.3) + 1.03, -3.3), v3(0.03, 0.035, 0.03), 'lens', e=(1, 1), nu=6, nv=4)
    coil(g, v3(-0.5, solef, 2.6))
    lifejacket(g, v3(0.5, sole + 0.02, -1.6), 1.2)
    for side, z in ((1, -1.0), (-1, 0.8)):
        fender(g, v3(side * (h.xs(z) - 0.05), h.ys(z) + 0.03, z), v3(side * (h.xs(z) + 0.12), h.ys(z) - 0.25, z))
    return g, h


def cruiser(spec):
    """A 32-foot express cruiser: cabin with portlights, wraparound windshield, radar arch, swim platform."""
    g = Geo()
    h = Hull(9.6, 3.2, sheer=[(0, 1.92), (0.5, 2.0), (0.85, 2.28), (1, 2.5)],
             keel=[(0, 0.04), (0.45, 0.0), (0.68, 0.14), (0.88, 0.78), (1, 2.24)],
             plan=[(0, 0.9), (0.3, 1.0), (0.5, 1.0), (0.72, 0.86), (0.9, 0.5), (1, 0.0)], dead=(18, 40), flare=0.08)
    sole = WL + 0.46
    za = -0.9
    zones = [(-4.8, za, 'cockpit', {'sole': sole, 'side': 0.24, 'upper': 'vinyl', 'liner': 'gelcoat', 'pad': 0.32,
                                     'sole_mat': 'teak'}),
             (za, 4.8, 'deck', {'camber': 0.07})]
    zs = stations(h, [za])
    hull_shell(g, h, zs)
    decks(g, h, zs, zones)
    rubrail(g, h, zs, r=0.04)
    yr = h.ys(za) + 0.58
    half = trunk(g, h, za, 3.0, 0.34, yr, rnd=1.4, rake=0.35, cam=0.07)
    companionway(g, za, sole + 0.05, yr - 0.08, w=0.3)
    for z, hx, hz in ((1.0, 0.3, 0.3), (2.1, 0.26, 0.24)):
        hatch(g, v3(0, 0, z), hx, hz, yr + 0.04)
    hatch(g, v3(0, 0, 3.5), 0.32, 0.3, h.ys(3.5) + 0.06)
    # the helm and companion dashes under the windshield, the windshield wrapping round them
    wmax = h.xs(za) - 0.22
    for side in (1, -1):
        xin, xout = 0.42, wmax - 0.02
        c = v3(side * (xin + xout) / 2, (sole + yr) / 2 - 0.05, za - 0.25)
        box(g, c, v3((xout - xin) / 2, (yr - sole) / 2 - 0.05, 0.25), 'gelcoat')
        dd = [v3(side * xin, yr - 0.12, za - 0.5), v3(side * xout, yr - 0.12, za - 0.5), v3(side * xout, yr + 0.02, za - 0.15), v3(side * xin, yr + 0.02, za - 0.15)]
        g.quad(*dd, 'dash' if side < 0 else 'dash_glove', v3(0, 0.6, -1), uv=[(1, 1), (0, 1), (0, 0), (1, 0)] if side > 0 else [(0, 1), (1, 1), (1, 0), (0, 0)])
    wheel(g, v3(-0.75, yr - 0.1, za - 0.62), v3(0, 0.5, -0.85), r=0.2)
    windshield(g, za - 0.05, lambda x: yr + 0.04 - (yr - h.ys(za) - 0.1) * (abs(x) / wmax) ** 6, wmax, 0.58, 0.42, 0.55,
               gap=0.0, panels=3)
    # the cockpit: helm seat, U-lounge aft, wet bar, table
    bench(g, -0.35, -1.1, sole + 0.48, za - 1.15, 0.5, back=-1)
    with grouped(g, 'seatbase'):
        box(g, v3(-0.72, sole + 0.22, za - 1.15), v3(0.32, 0.22, 0.2), 'gelcoat')
    bench(g, 0.35, 1.0, sole + 0.48, za - 1.15, 0.5, back=-1)
    box(g, v3(0.67, sole + 0.22, za - 1.15), v3(0.3, 0.22, 0.2), 'gelcoat')
    zl = h.z0 + 0.45
    xl = h.x_at(zl, sole + 0.3) - 0.08
    bench(g, -xl + 0.05, xl - 0.05, sole + 0.42, zl, 0.5)
    box(g, v3(0, sole + 0.18, zl), v3(xl - 0.05, 0.18, 0.25), 'gelcoat')
    for z0, z1 in ((zl + 0.3, -2.4),):
        xo = h.x_at((z0 + z1) / 2, sole + 0.3) - 0.06
        bench(g, xo - 0.45, xo, sole + 0.42, (z0 + z1) / 2, z1 - z0)
        box(g, v3(xo - 0.22, sole + 0.18, (z0 + z1) / 2), v3(0.22, 0.18, (z1 - z0) / 2), 'gelcoat')
    box(g, v3(-1.0, sole + 0.45, -2.1), v3(0.3, 0.45, 0.35), 'gelcoat')
    box(g, v3(-1.0, sole + 0.915, -2.1), v3(0.31, 0.015, 0.36), 'teak')
    lathe(g, v3(-1.0, sole + 0.93, -2.0), [(0, 0), (0.12, 0), (0.12, -0.08), (0, -0.08)], 'steel', n=10)
    tube(g, [v3(0, sole, -2.6), v3(0, sole + 0.68, -2.6)], 0.04, 'steel', n=8)
    blob(g, v3(0, sole + 0.7, -2.6), v3(0.38, 0.025, 0.28), 'teak', e=(0.5, 0.3), nu=14, nv=4)
    # the radar arch, dome and lights; the bimini from the windshield back to it
    zr = -2.9
    yz = h.ys(zr)
    leg = [v3(h.xs(zr) - 0.16, yz, zr), v3(h.xs(zr) - 0.24, yz + 0.55, zr - 0.18), v3(h.xs(zr) - 0.42, yz + 1.05, zr - 0.36),
           v3(1.0, yz + 1.33, zr - 0.46), v3(0.6, yz + 1.44, zr - 0.5)]
    arch = leg + [v3(-p[0], p[1], p[2]) for p in reversed(leg)]
    taper = [1.25, 1.12, 1.0, 0.9, 0.85, 0.85, 0.9, 1.0, 1.12, 1.25]
    tube(g, arch, taper, 'gelcoat', prof=rect(0.055, 0.2), up=v3(0, 0, 1))
    zr -= 0.2
    yA = h.ys(zr) + 1.5
    lathe(g, v3(0, yA, zr - 0.3), [(0, 0), (0.28, 0), (0.28, 0.14), (0.2, 0.2), (0, 0.22)], 'cooler', n=14)
    antenna(g, v3(0.7, yA - 0.04, zr - 0.3), 2.0)
    tube(g, [v3(-0.6, yA - 0.04, zr - 0.3), v3(-0.6, yA + 0.6, zr - 0.3)], 0.015, 'steel', n=5)
    blob(g, v3(-0.6, yA + 0.63, zr - 0.3), v3(0.03, 0.035, 0.03), 'lens', e=(1, 1), nu=6, nv=4)
    for side in (1, -1):
        blob(g, v3(side * 0.85, yA - 0.2, zr - 0.15), v3(0.05, 0.03, 0.03), 'lens', e=(1, 1), nu=6, nv=4)
    canopy(g, wmax - 0.15, za - 0.38, zr + 0.1, yr + 0.6, droop=0.08)
    # the swim platform and its ladder, the twin sterndrives under it
    zt = h.z0
    box(g, v3(0, WL + 0.36, zt - 0.42), v3(1.3, 0.05, 0.42), 'gelcoat', skip=('+z',))
    box(g, v3(0, WL + 0.415, zt - 0.42), v3(1.27, 0.008, 0.39), 'teak', skip=('-y',))
    for x in (0.7, 0.95):
        tube(g, [v3(x, WL + 0.42, zt - 0.8), v3(x, WL - 0.4, zt - 0.86)], 0.015, 'steel', n=5)
    for k in range(4):
        tube(g, [v3(0.7, WL + 0.2 - k * 0.2, zt - 0.81 - k * 0.015), v3(0.95, WL + 0.2 - k * 0.2, zt - 0.81 - k * 0.015)], 0.014, 'steel', n=4)
    for x in (0.55, -0.55):
        sterndrive(g, x, zt - 0.05, 1.15)
    # the foredeck: pulpit and rails back to the windshield, the anchor on its roller, windlass, cleats
    pts = [v3(h.xs(z) - 0.06, deck_y(h, z, h.xs(z)) + 0.62, z) for z in np.linspace(-0.4, 4.45, 9)]
    pts = pts + [v3(0, h.ys(4.72) + 0.62, 4.72)] + [v3(-p[0], p[1], p[2]) for p in reversed(pts)]
    rail(g, pts, [(i, v3(pts[i][0], deck_y(h, pts[i][2], pts[i][0]) + 0.01, pts[i][2])) for i in (0, 2, 4, 6, 8, 9, 10, 12, 14, 16, 18)])
    box(g, v3(0, h.ys(4.62) + 0.06, 4.62), v3(0.06, 0.05, 0.22), 'steel')
    tube(g, [v3(0, h.ys(4.55) + 0.1, 4.4), v3(0, h.ys(4.75) + 0.05, 4.85)], 0.035, 'steel', n=6)
    blob(g, v3(0, h.ys(4.75) - 0.05, 4.88), v3(0.16, 0.1, 0.06), 'steel', e=(0.5, 0.5), nu=8, nv=4)
    lathe(g, v3(0, deck_y(h, 4.1, 0), 4.1), [(0, 0), (0.12, 0), (0.1, 0.12), (0.07, 0.17), (0, 0.18)], 'steel', n=10)
    tube(g, [v3(0, deck_y(h, 4.1, 0) + 0.06, 4.1), v3(0, deck_y(h, 4.4, 0) + 0.1, 4.45)], 0.01, 'rope', n=4)
    for side in (1, -1):
        for z in (-4.3, -1.4, 1.6, 4.0):
            cleat(g, v3(side * (h.xs(z) - 0.12), deck_y(h, z, h.xs(z) - 0.12) + 0.0, z), v3(0, 0, 1))
    navlights(g, v3(0, h.ys(4.6) + 0.1, 4.55))
    lifejacket(g, v3(0.3, sole + 0.02, -3.2), 2.2)
    coil(g, v3(1.1, h.ys(-4.0) + 0.0, -4.1), 0.14)
    for side, z in ((1, -2.0), (1, 0.6), (-1, -1.0)):
        fender(g, v3(side * (h.xs(z) - 0.05), h.ys(z) + 0.03, z), v3(side * (h.xs(z) + 0.14), h.ys(z) - 0.3, z))
    return g, h


def sloop(spec):
    """A 30-foot sloop: fin keel and spade rudder, cabin trunk with portlights, a masthead rig with its
    sail on the boom under a cover and the jib rolled on the forestay, lifelines, a tiller."""
    g = Geo()
    h = Hull(9.2, 3.0, kind='round', sheer=[(0, 1.6), (0.5, 1.64), (0.85, 1.84), (1, 1.98)],
             keel=[(0, 1.02), (0.1, 0.66), (0.4, 0.44), (0.7, 0.5), (0.9, 0.86), (1, 1.76)],
             plan=[(0, 0.68), (0.2, 0.9), (0.5, 1.0), (0.75, 0.82), (0.92, 0.42), (1, 0.0)], bilge=WL + 0.08)
    sole = WL + 0.2
    za, zf = -1.6, 1.5
    zones = [(-4.4, za, 'cockpit', {'sole': sole, 'side': 0.36, 'liner': 'gelcoat', 'sole_mat': 'teak', 'pad': 0.2}),
             (za, 4.6, 'deck', {'camber': 0.06})]
    zs = stations(h, [za, -4.4])
    hull_shell(g, h, zs)
    decks(g, h, zs, zones)
    # the toe rail, teak along the sheer
    for side in (1, -1):
        tube(g, [v3(side * (h.xs(z) - 0.03), h.ys(z) + 0.035, z) for z in zs[1:-1]], 1.0, 'teak', prof=rect(0.02, 0.035), up=UP)
    # keel, bulb and rudder
    fin(g, [(v3(0, 0.48, 0.15), 1.5, 0.11), (v3(0, -0.2, -0.05), 1.15, 0.11), (v3(0, -0.85, -0.2), 0.85, 0.1)], 'antifoul')
    blob(g, v3(0, -0.92, -0.25), v3(0.14, 0.1, 0.62), 'antifoul', e=(1, 0.8), nu=12, nv=6)
    fin(g, [(v3(0, 0.8, -3.6), 0.5, 0.12), (v3(0, 0.1, -3.62), 0.44, 0.12), (v3(0, -0.4, -3.65), 0.34, 0.12)], 'antifoul')
    # coamings and winches round the cockpit, the tiller
    for side in (1, -1):
        cz = np.linspace(-4.0, za, 8)
        tube(g, [v3(side * (h.xs(z) - 0.36), h.ys(z) + 0.12, z) for z in cz], 1.0, 'teak', prof=rect(0.025, 0.12), up=UP)
        for z in (-2.3, -3.3):
            p = v3(side * (h.xs(z) - 0.2), h.ys(z) + 0.01, z)
            lathe(g, p, [(0, 0), (0.09, 0), (0.08, 0.05), (0.065, 0.16), (0.075, 0.2), (0, 0.21)], 'steel', n=10)
            lathe(g, p + v3(0, 0.205, 0), [(0, 0), (0.045, 0), (0.04, 0.03), (0, 0.035)], 'black', n=8)
    tube(g, [v3(0, sole + 0.3, -3.65), v3(0, sole + 0.45, -3.2), v3(0, sole + 0.55, -2.4)], [0.035, 0.03, 0.022], 'wood', n=6)
    # the seats either side of the footwell: lockers with their lids, cushions on top
    for side in (1, -1):
        cz = list(np.linspace(-3.9, za, 6))
        for z0, z1 in zip(cz, cz[1:]):
            xw0 = h.xs(z0) - 0.36
            xw1 = h.xs(z1) - 0.36
            top = sole + 0.42
            q = [v3(side * (xw0 - 0.46), sole, z0), v3(side * (xw1 - 0.46), sole, z1), v3(side * (xw1 - 0.46), top, z1), v3(side * (xw0 - 0.46), top, z0)]
            g.quad(*q, 'gelcoat', v3(-side, 0, 0))
            g.quad(v3(side * (xw0 - 0.46), top, z0), v3(side * (xw1 - 0.46), top, z1), v3(side * xw1, top, z1), v3(side * xw0, top, z0), 'teak', UP)
        with grouped(g, 'cushion'):
            xm = h.xs(-2.9) - 0.36 - 0.23
            blob(g, v3(side * xm, sole + 0.46, -2.85), v3(0.21, 0.04, 0.95), 'vinyl', e=(0.2, 0.35))
    for z in (-3.9, za):
        xw = h.xs(z) - 0.36 - 0.46
        g.quad(v3(xw, sole, z), v3(-xw, sole, z), v3(-xw, sole + 0.42, z), v3(xw, sole + 0.42, z), 'gelcoat', v3(0, 0, 1 if z < -3 else -1))
    # the cabin trunk: portlights, handrails, companionway with its sliding hatch and dodger
    yr = h.ys(za) + 0.48
    trunk(g, h, za, zf, 0.42, yr, rnd=0.9, rake=0.2)
    companionway(g, za, sole + 0.05, yr - 0.04, w=0.28)
    box(g, v3(0, yr + 0.09, za + 0.4), v3(0.34, 0.05, 0.4), 'gelcoat')
    box(g, v3(0, yr + 0.145, za + 0.3), v3(0.3, 0.012, 0.32), 'teak')
    for side in (1, -1):
        rail_pts = [v3(side * 0.62, yr + 0.08, z) for z in np.linspace(za + 0.3, zf - 0.6, 6)]
        tube(g, rail_pts, 1.0, 'wood', prof=rect(0.018, 0.022), up=UP)
        for p in rail_pts[::1]:
            box(g, p - v3(0, 0.045, 0), v3(0.016, 0.03, 0.03), 'wood')
    hatch(g, v3(0, 0, zf - 0.3), 0.26, 0.26, yr + 0.02)
    hatch(g, v3(0, 0, 2.6), 0.3, 0.3, h.ys(2.6) + 0.05)
    for side in (1, -1):
        for z in (-0.4, 0.6):
            lathe(g, v3(side * 0.5, yr + 0.04, z), [(0, 0), (0.07, 0), (0.07, 0.04), (0, 0.045)], 'steel', n=8)
    for f in np.linspace(0, 1, 4):
        pass
    xd = h.xs(za) - 0.55
    for zb in (za, za + 0.55):
        arc = [v3(xd * math.cos(a), yr + 0.05 + 0.62 * math.sin(a), zb) for a in np.linspace(0, math.pi, 9)]
        tube(g, arc, 0.012, 'steel', n=4)
    canopy(g, xd, za + 0.6, za - 0.02, yr + 0.67, droop=0.45, nx=10, nz=4)
    # the rig: mast, spreaders, boom and sail cover, forestay with the furled jib, shrouds, backstay
    zm = 0.9
    ym0 = yr + 0.05
    ytop = ym0 + 11.2
    with grouped(g, 'mastfoot'):
        tube(g, [v3(0, ym0, zm), v3(0, ym0 + 3.0, zm)], [0.09, 0.085], 'mast', n=8)
    with grouped(g, 'rigtop'):
        tube(g, [v3(0, ym0 + 3.0, zm), v3(0, ym0 + 7.0, zm), v3(0, ytop, zm - 0.05)], [0.085, 0.075, 0.055], 'mast', n=8)
        box(g, v3(0, ytop + 0.05, zm - 0.02), v3(0.05, 0.06, 0.12), 'alu')
        tube(g, [v3(0, ytop + 0.1, zm), v3(0, ytop + 0.9, zm)], 0.008, 'antenna', n=3)
        ysp = ym0 + 6.4
        for side in (1, -1):
            tube(g, [v3(0, ysp, zm), v3(side * 0.8, ysp + 0.05, zm - 0.12)], [0.025, 0.018], 'alu', n=5)
            cp = v3(side * (h.xs(zm) - 0.12), h.ys(zm) + 0.05, zm - 0.05)
            tube(g, [v3(0, ytop - 0.1, zm - 0.05), v3(side * 0.8, ysp + 0.05, zm - 0.12), cp], 0.006, 'steel', n=4)
            tube(g, [v3(0, ysp - 0.1, zm), v3(side * (h.xs(zm + 0.5) - 0.12), h.ys(zm + 0.5) + 0.05, zm + 0.5)], 0.005, 'steel', n=3)
            tube(g, [v3(0, ysp - 0.1, zm), v3(side * (h.xs(zm - 0.6) - 0.12), h.ys(zm - 0.6) + 0.05, zm - 0.6)], 0.005, 'steel', n=3)
        stem = v3(0, h.ys(4.55) + 0.1, 4.55)
        tube(g, [v3(0, ytop - 0.05, zm + 0.02), stem], 0.006, 'steel', n=4)
        tube(g, [v3(0, ytop - 0.05, zm - 0.1), v3(0, h.ys(h.z0) + 0.15, h.z0 + 0.05)], 0.005, 'steel', n=3)
    with grouped(g, 'sail'):
        fs = [v3(0, ytop - 0.05, zm + 0.02) * (1 - f) + stem * f for f in np.linspace(0.08, 0.95, 9)]
        tube(g, fs, [0.025 + 0.06 * (k / 8) ** 1.5 for k in range(9)], 'sail', n=8)
        tube(g, [fs[-1] + (fs[-1] - fs[-2]) * 0.3, fs[-1]], 0.0, 'canvas', n=8, caps=False)
    yb = ym0 + 1.05
    zb_end = -3.75
    with grouped(g, 'boom'):
        tube(g, [v3(0, yb, zm - 0.08), v3(0, yb - 0.05, zb_end)], 0.055, 'mast', n=8, prof=[(0.6 * math.cos(a), math.sin(a)) for a in np.linspace(0, 2 * math.pi, 9)[:-1]])
    with grouped(g, 'sail'):
        cov = [v3(0, yb + 0.1 - 0.05 * f, zm - 0.1 + (zb_end + 0.3 - zm + 0.1) * f) for f in np.linspace(0, 1, 10)]
        tube(g, cov, [0.2 - 0.1 * f for f in np.linspace(0, 1, 10)], 'canvas', n=8,
             prof=[(0.75 * math.cos(a), 1.0 * math.sin(a) - 0.1) for a in np.linspace(0, 2 * math.pi, 11)[:-1]])
    tube(g, [v3(0, yb - 0.05, zb_end + 0.1), v3(0, h.ys(-3.9) + 0.05, -3.95)], 0.004, 'rope', n=3)
    # lifelines: stanchions, pulpit and pushpit
    for side in (1, -1):
        st = []
        for z in np.linspace(-3.9, 3.5, 7):
            b = v3(side * (h.xs(z) - 0.06), h.ys(z) + 0.02, z)
            tube(g, [b, b + v3(-side * 0.02, 0.62, 0)], 0.013, 'steel', n=5)
            st.append(b)
        for hgt in (0.32, 0.6):
            tube(g, [b + v3(-side * 0.02, hgt, 0) for b in st], 0.004, 'steel', n=3)
    pul = [v3(h.xs(z) - 0.06, h.ys(z) + 0.62, z) for z in (3.5, 4.0)] + [v3(0.15, h.ys(4.4) + 0.6, 4.42)]
    pul = pul + [v3(-p[0], p[1], p[2]) for p in reversed(pul)]
    rail(g, pul, [(0, v3(pul[0][0], h.ys(3.5) + 0.02, 3.5)), (2, v3(0.15, h.ys(4.4) + 0.02, 4.4)), (3, v3(-0.15, h.ys(4.4) + 0.02, 4.4)), (5, v3(-pul[0][0], h.ys(3.5) + 0.02, 3.5))])
    push = [v3(h.xs(z) - 0.06, h.ys(z) + 0.62, z) for z in (-3.9, -4.4)] + [v3(0.5, h.ys(-4.55) + 0.6, -4.55)]
    push = push + [v3(-p[0], p[1], p[2]) for p in reversed(push)]
    rail(g, push, [(1, v3(push[1][0], h.ys(-4.4) + 0.02, -4.4)), (4, v3(-push[1][0], h.ys(-4.4) + 0.02, -4.4))])
    navlights(g, v3(0, h.ys(4.45) + 0.66, 4.42))
    for side in (1, -1):
        for z in (-4.1, 3.9):
            cleat(g, v3(side * (h.xs(z) - 0.15), h.ys(z) + 0.01, z), v3(0, 0, 1))
    # an outboard on her stern bracket
    box(g, v3(-0.5, h.ys(h.z0) - 0.2, h.z0 - 0.08), v3(0.16, 0.18, 0.06), 'steel')
    outboard(g, -0.5, h.z0 - 0.12, h.ys(h.z0) - 0.35, s=0.5, tiller=True, label=False)
    coil(g, v3(0.35, sole, -3.4), 0.15)
    for side, z in ((1, -1.0), (-1, 0.0)):
        fender(g, v3(side * (h.xs(z) - 0.05), h.ys(z) + 0.3, z), v3(side * (h.xs(z) + 0.12), h.ys(z) - 0.15, z))
    return g, h


def skiff(spec):
    """A 15-foot riveted aluminium jon boat: flat bottom, square bow, three benches, a tiller outboard."""
    g = Geo()
    h = Hull(4.6, 1.75, kind='flat', sheer=[(0, 1.3), (0.6, 1.3), (1, 1.4)],
             keel=[(0, 0.72), (0.62, 0.72), (0.85, 0.84), (1, 1.12)],
             plan=[(0, 0.9), (0.55, 1.0), (1, 0.74)], chine=0.84, bow_closed=False, flare=0.06)
    sole = 0.78
    zones = [(-2.3, 2.12, 'cockpit', {'sole': sole, 'side': 0.035, 'liner': 'alu', 'upper': 'alu', 'sole_mat': 'alu', 'pad': 0.1,
                                      'bulkhead': 'alu'}),
             (2.12, 2.3, 'deck', {'camber': 0.0, 'deck': 'alu'})]
    zs = stations(h, [2.12])
    hull_shell(g, h, zs)
    decks(g, h, zs, zones)
    for side in (1, -1):
        tube(g, [v3(side * h.xs(z), h.ys(z) + 0.01, z) for z in zs], 1.0, 'alu', prof=rect(0.025, 0.02), up=UP)
    for z in np.linspace(-2.0, 1.8, 10):
        xi = h.x_at(z, sole + 0.02) - 0.03
        box(g, v3(0, sole + 0.012, z), v3(xi, 0.012, 0.02), 'alu')
    for z, wood in ((-1.7, False), (-0.1, True), (1.35, False)):
        xo = h.x_at(z, h.ys(z) - 0.1) - 0.01
        box(g, v3(0, h.ys(z) - 0.12, z), v3(xo, 0.02, 0.15), 'wood' if wood else 'alu')
        box(g, v3(0, h.ys(z) - 0.3, z + 0.13), v3(xo - 0.02, 0.16, 0.01), 'alu')
    outboard(g, 0, h.z0, h.ys(h.z0) - 0.06, s=0.62, tiller=True, label=True)
    # the gear left aboard: a fuel tank, oars across the seats, an anchor on its line, a bucket
    box(g, v3(0.42, sole + 0.14, -1.95), v3(0.16, 0.13, 0.24), 'gascan')
    tube(g, [v3(0.42, sole + 0.27, -2.05), v3(0.42, sole + 0.32, -1.95), v3(0.42, sole + 0.27, -1.85)], 0.012, 'black', n=4)
    tube(g, [v3(0.42, sole + 0.27, -2.2), v3(0.1, sole + 0.35, -2.4), v3(0, sole + 0.5, -2.45)], 0.008, 'black', n=4)
    for x in (-0.25, 0.25):
        tube(g, [v3(x, h.ys(0) - 0.08, -1.9), v3(x * 1.2, h.ys(0) - 0.08, 0.9)], 0.02, 'wood', n=5)
        box(g, v3(x * 1.2, h.ys(0) - 0.08, 1.1), v3(0.07, 0.008, 0.25), 'wood')
    box(g, v3(-0.3, sole + 0.05, 1.8), v3(0.12, 0.05, 0.04), 'black')
    tube(g, [v3(-0.3, sole + 0.05, 1.76), v3(-0.3, sole + 0.05, 1.5)], 0.015, 'black', n=4)
    coil(g, v3(-0.25, sole, 1.55), 0.12)
    lathe(g, v3(0.3, sole, 0.7), [(0, 0), (0.1, 0), (0.13, 0.28), (0.12, 0.28), (0.09, 0.02), (0, 0.02)], 'cooler', n=10)
    with grouped(g, 'loose'):
        tube(g, [v3(-0.5, h.ys(-0.5) - 0.05, -1.5), v3(-0.35, h.ys(0.8) + 0.1, 1.6)], [0.012, 0.004], 'black', n=4)
    return g, h


SPECS = {
    'boat_bowrider': dict(build=bowrider, weight=4, name='SECOND WIND', port='GRAYHAVEN', reg='GH 4471 AB',
                          hull=(242, 240, 233), stripes=[(0.14, 0.2, (128, 24, 30)), (0.24, 0.26, (40, 40, 44))],
                          boot=(128, 24, 30), bottom=(38, 52, 92), vinyl=(236, 230, 214), accent=(128, 24, 30),
                          canvas=(122, 26, 32), carpet=(96, 98, 104), cowl=(30, 31, 34)),
    'boat_console': dict(build=centre_console, weight=3, name='REEL THERAPY', port='GRAYHAVEN', reg='GH 2208 CF',
                         hull=(190, 212, 220), stripes=[(0.1, 0.16, (24, 40, 70))], boot=(24, 40, 70), bottom=(28, 28, 30),
                         vinyl=(232, 228, 216), accent=(24, 40, 70), canvas=(24, 40, 70), cowl=(232, 232, 228),
                         engine='TIDEMARK', hp='250', name_col=(24, 40, 70)),
    'boat_cruiser': dict(build=cruiser, weight=3, name='CALDER BELLE', port='GRAYHAVEN', reg='GH 1187 KM',
                         hull=(244, 242, 236), stripes=[(0.16, 0.26, (26, 36, 66)), (0.3, 0.33, (26, 36, 66))],
                         boot=(26, 36, 66), bottom=(132, 44, 34), vinyl=(238, 232, 218), accent=(26, 36, 66),
                         canvas=(30, 40, 66), hull_windows=[(-0.4, 1.8, 0.38, 0.56)], ports=4, name_h=0.17),
    'boat_sloop': dict(build=sloop, weight=3, name='MERROW LASS', port='GRAYHAVEN', reg='GH 3390 SL',
                       hull=(26, 40, 74), stripes=[(0.05, 0.065, (196, 160, 70))], boot=(238, 236, 228), bottom=(150, 40, 36),
                       vinyl=(214, 206, 186), accent=(26, 40, 74), canvas=(36, 70, 120), name_col=(214, 182, 96),
                       reg_col=(230, 228, 220), ports=3, cabin=(236, 232, 222), name_h=0.13),
    'boat_skiff': dict(build=skiff, weight=4, name='', port='', reg='GH 6022 JB', hull=(84, 92, 62), stripes=[],
                       boot=(84, 92, 62), bottom=(84, 92, 62), riveted=True, cowl=(40, 42, 46), engine='TIDEMARK', hp='25',
                       reg_col=(236, 234, 226), deck_col=(176, 180, 182)),
}


# ---------------------------------------------------------------- textures

ATLAS = 1024
HULL_PX = 64            # hull chart pixels a metre
HULL_ROWS = 192         # each side's chart covers y from 3 m down to the keel
HULL_TOP = 3.0
TILE = 128              # a material's square: its 64 px pattern twice each way
TILE_M = 2.0            # metres one pattern covers
DECALS = {               # region (x, y, w, h) in the atlas
    'transom': (0, 384, 256, 128), 'dash': (256, 384, 128, 64), 'dash_glove': (256, 448, 128, 64),
    'cabin_side': (384, 384, 256, 64), 'console': (384, 448, 128, 64), 'cowl': (512, 448, 128, 64),
    'name_side': (640, 384, 256, 64), 'cabin_front': (640, 448, 128, 64), 'cowl_tiller': (768, 448, 128, 64),
}
MATERIALS = ['gelcoat', 'nonskid', 'teak', 'carpet', 'vinyl', 'vinyl_lo', 'vinyl_accent', 'black_vinyl', 'canvas',
             'steel', 'alu', 'black', 'rubrail', 'glass', 'smoked', 'engine', 'engine_lower', 'engine_metal', 'prop',
             'navred', 'navgreen', 'lens', 'fender', 'rope', 'strap', 'cooler', 'lifevest', 'antenna', 'wood', 'sail',
             'mast', 'antifoul', 'gascan']
assert len(MATERIALS) <= 33


def tile_origin(mat):
    k = MATERIALS.index(mat)
    if k == 32:
        return 896, 384     # the decal strip's spare square
    return (k % 8) * TILE, 512 + (k // 8) * TILE


def periodic_noise(n, beta, seed, m=None):
    rng = np.random.default_rng(seed)
    m = m or n
    fy = np.fft.fftfreq(n)[:, None]
    fx = np.fft.fftfreq(m)[None, :]
    r = np.sqrt(fx * fx + fy * fy)
    r[0, 0] = 1
    spec = (rng.normal(size=(n, m)) + 1j * rng.normal(size=(n, m))) / r ** beta
    spec[0, 0] = 0
    x = np.real(np.fft.ifft2(spec))
    return (x - x.mean()) / (x.std() + 1e-9)


def subdivide(T, keep=None, limit=1.5):
    """Splits the longest edge of every triangle (keep marks those to split) until all are under limit."""
    out = []
    stack = [t for t in T]
    while stack:
        t = stack.pop()
        e = [np.linalg.norm(t[(i + 1) % 3] - t[i]) for i in range(3)]
        i = int(np.argmax(e))
        if e[i] <= limit:
            out.append(t)
            continue
        a, b, c = t[i], t[(i + 1) % 3], t[(i + 2) % 3]
        m = (a + b) / 2
        stack.append(np.stack([a, m, c]))
        stack.append(np.stack([m, b, c]))
    return out


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


def hull_uv(T, side, h):
    z = T[..., 2]
    u = (h.z1 + 0.3 - z) if side > 0 else (z - h.z0 + 0.3)
    v = np.clip((HULL_TOP - T[..., 1]) * HULL_PX, 0.5, HULL_ROWS - 0.5)
    y0 = 0 if side > 0 else HULL_ROWS
    return np.stack([np.clip(u * HULL_PX, 0.5, ATLAS - 0.5) / ATLAS, (y0 + v) / ATLAS], -1)


def transom_uv(T, h):
    x0, y0, w, hh = DECALS['transom']
    xt = h.xs(h.z0) + 0.05
    ytop = h.ys(h.z0) + 0.05
    ybot = min(h.yk(h.z0), WL - 0.4)
    u = (xt - T[..., 0]) / (2 * xt)
    v = (ytop - T[..., 1]) / (ytop - ybot)
    return np.stack([(x0 + 0.5 + np.clip(u, 0, 1) * (w - 1)) / ATLAS, (y0 + 0.5 + np.clip(v, 0, 1) * (hh - 1)) / ATLAS], -1)


def font(size, bold=True, serif=False):
    name = ('DejaVuSerif' if serif else 'DejaVuSans') + ('-Bold' if bold else '') + '.ttf'
    try:
        return ImageFont.truetype(os.path.join('/usr/share/fonts/truetype/dejavu', name), size)
    except OSError:
        return ImageFont.load_default()


def shade(c, k):
    return tuple(int(max(0, min(255, v * k))) for v in c)


def pattern(mat, spec, rng):
    """A material's 64 px pattern (2 m), periodic, as floats 0-255."""
    n = 64
    sd = int(rng.integers(1 << 30))
    noise = periodic_noise(n, 1.2, sd)
    fine = periodic_noise(n, 0.4, sd + 1)
    yy, xx = np.mgrid[0:n, 0:n]
    col = {
        'gelcoat': spec.get('deck_col', (238, 236, 228)), 'nonskid': spec.get('deck_col', (228, 225, 214)),
        'teak': (150, 108, 66), 'carpet': spec.get('carpet', (96, 98, 104)), 'vinyl': spec.get('vinyl', (236, 230, 214)),
        'vinyl_lo': spec.get('vinyl', (236, 230, 214)), 'vinyl_accent': spec.get('accent', (128, 24, 30)),
        'black_vinyl': (34, 34, 36), 'canvas': spec.get('canvas', (40, 50, 76)), 'steel': (196, 200, 204),
        'alu': (178, 182, 186), 'black': (30, 30, 32), 'rubrail': spec.get('rubrail', (40, 40, 42)),
        'glass': (150, 170, 178), 'smoked': (48, 54, 60), 'engine': spec.get('cowl', (30, 31, 34)),
        'engine_lower': shade(spec.get('cowl', (30, 31, 34)), 1.0), 'engine_metal': (70, 72, 76), 'prop': (150, 150, 146),
        'navred': (200, 30, 30), 'navgreen': (30, 170, 60), 'lens': (240, 240, 230), 'fender': (34, 72, 140),
        'rope': (206, 196, 168), 'strap': (40, 40, 44), 'cooler': (230, 230, 226), 'lifevest': (236, 96, 20),
        'antenna': (232, 232, 228), 'wood': (126, 70, 34), 'sail': (236, 236, 228), 'mast': (190, 194, 198),
        'antifoul': spec.get('bottom', (38, 52, 92)), 'gascan': (178, 34, 28),
    }[mat]
    base = np.ones((n, n, 3)) * np.array(col, float)
    k = 1 + noise[..., None] * 0.02 + fine[..., None] * 0.015
    if mat == 'nonskid':
        dia = ((xx + yy) % 4 == 0) | ((xx - yy) % 4 == 0)
        k = k - dia[..., None] * 0.07
    elif mat == 'teak':
        plank = (xx % 4 == 0)[..., None]
        grain = periodic_noise(n, 2.2, sd + 2, n)
        k = 1 + grain[..., None] * 0.08 + fine[..., None] * 0.05 - plank * 0.62
        base = base * (1 + (((xx // 4) * 37) % 7 - 3)[..., None] * 0.02)
    elif mat == 'carpet':
        k = 1 + fine[..., None] * 0.1 + noise[..., None] * 0.04
    elif mat in ('vinyl', 'vinyl_lo'):
        seam = (yy % 8 == 0)[..., None]
        k = k - seam * 0.12 + (yy % 8 == 1)[..., None] * 0.04
    elif mat == 'canvas':
        k = 1 + fine[..., None] * 0.05 + ((xx + yy) % 2)[..., None] * 0.03
    elif mat in ('steel', 'alu', 'mast', 'antenna'):
        k = 1 + periodic_noise(n, 1.8, sd + 3)[..., None] * 0.06 + (np.sin(yy / n * 2 * np.pi * 3) * 0.05)[..., None]
    elif mat == 'rope':
        k = 1 + (((xx + yy * 2) % 6) < 2)[..., None] * -0.18 + fine[..., None] * 0.04
    elif mat == 'glass':
        k = 1 + (yy / n * 0.15 - 0.05)[..., None]
    elif mat == 'carpet':
        pass
    return np.clip(base * k, 0, 255)


def hull_chart(spec, h, side, rng):
    """One side's hull: bottom paint, boot stripe, topsides with their stripes, her numbers forward."""
    W, H = ATLAS, HULL_ROWS
    zz = (h.z1 + 0.3 - np.arange(W) / HULL_PX) if side > 0 else (h.z0 - 0.3 + np.arange(W) / HULL_PX)
    yy = HULL_TOP - (np.arange(H) + 0.5) / HULL_PX
    Y = np.repeat(yy[:, None], W, 1)
    Z = np.repeat(zz[None, :], H, 0)
    ys = np.array([h.ys(z) for z in zz])[None, :]
    img = np.ones((H, W, 3)) * np.array(spec['hull'], float)
    below = Y < WL + 0.04
    img[below] = spec['bottom']
    boot = (Y >= WL + 0.04) & (Y < WL + 0.1)
    img[boot] = spec['boot']
    for a, b, c in spec['stripes']:
        m = (Y <= ys - a) & (Y > ys - b)
        img[m] = c
    for wz0, wz1, wy0, wy1 in spec.get('hull_windows', []):
        cz, cy = (wz0 + wz1) / 2, None
        rz = (wz1 - wz0) / 2
        top, bot = ys - wy0, ys - wy1
        e = ((Z - cz) / rz) ** 6 + ((Y - (top + bot) / 2) / ((top - bot) / 2)) ** 6
        img[e < 1.15] = (175, 178, 182)
        img[e < 1] = (24, 30, 36)
        img[(e < 0.6) & (Y > (top + bot) / 2)] = (40, 50, 58)
    if spec.get('riveted'):
        n = periodic_noise(H, 1.0, int(rng.integers(1 << 30)), W)
        img = img * (1 + n[..., None] * 0.04)
        for z in np.arange(h.z0, h.z1, 0.4):
            u = int(((h.z1 + 0.3 - z) if side > 0 else (z - h.z0 + 0.3)) * HULL_PX)
            img[:, u:u + 2] *= 0.82
            img[::5, u + 4] = img[::5, u + 4] * 0.8 + 30
        for yv in (0.95, 1.12):
            r = int((HULL_TOP - yv) * HULL_PX)
            img[r:r + 1] *= 0.75
            img[r + 3, ::5] = img[r + 3, ::5] * 0.8 + 30
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    # her numbers on the bow, reading forward to aft on both sides
    reg = spec.get('reg')
    if reg:
        zc = h.z1 - h.L * 0.2
        yt = h.ys(zc) - 0.32
        f = font(int(0.2 * HULL_PX))
        u = (h.z1 + 0.3 - zc) * HULL_PX if side > 0 else (zc - h.z0 + 0.3) * HULL_PX
        tw = d.textlength(reg, font=f)
        d.text((u - tw / 2, (HULL_TOP - yt) * HULL_PX), reg, fill=spec.get('reg_col', (20, 22, 26)), font=f)
    return np.asarray(im, float)


def decal(name, spec, h, w, hh):
    im = Image.new('RGB', (w, hh), (0, 0, 0))
    d = ImageDraw.Draw(im)
    if name == 'transom':
        xt = h.xs(h.z0) + 0.05
        ytop = h.ys(h.z0) + 0.05
        ybot = min(h.yk(h.z0), WL - 0.4)
        arr = np.ones((hh, w, 3)) * np.array(spec['hull'], float)
        yv = ytop - (np.arange(hh) + 0.5) / hh * (ytop - ybot)
        Y = np.repeat(yv[:, None], w, 1)
        arr[Y < WL + 0.04] = spec['bottom']
        arr[(Y >= WL + 0.04) & (Y < WL + 0.1)] = spec['boot']
        ys0 = h.ys(h.z0)
        # her stripes carry round the stern's corners, leaving the middle for her name
        X = np.repeat(((np.arange(w) + 0.5) / w)[None, :], hh, 0)
        corner = np.abs(X - 0.5) > spec.get('name_span', 0.36)
        for a, b, c in spec['stripes']:
            arr[(Y <= ys0 - a) & (Y > ys0 - b) & corner] = c
        im = Image.fromarray(arr.astype(np.uint8))
        d = ImageDraw.Draw(im)
        px_m = hh / (ytop - ybot)
        ny = (ytop - (ys0 - 0.05)) * px_m
        size = spec.get('name_h', 0.15)
        if not spec['name']:
            return np.asarray(im, float)
        f = font(max(9, int(size * 1.3 * px_m)), serif=True)
        l, t, r, b = f.getbbox(spec['name'])
        d.text(((w - (r - l)) / 2 - l, ny - t), spec['name'], fill=spec.get('name_col', (24, 30, 52)), font=f)
        f2 = font(max(7, int(0.075 * 1.3 * px_m)))
        l2, t2, r2, b2 = f2.getbbox(spec['port'])
        d.text(((w - (r2 - l2)) / 2 - l2, ny + (b - t) + 0.035 * px_m - t2), spec['port'], fill=spec.get('name_col', (24, 30, 52)), font=f2)
    elif name in ('dash', 'dash_glove'):
        d.rectangle([0, 0, w, hh], fill=(26, 26, 28))
        if name == 'dash':
            for k, (cx, r) in enumerate(((18, 11), (44, 15), (74, 15), (102, 11), (118, 7))):
                cy = 34
                d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(12, 12, 14), outline=(190, 190, 194))
                d.line([cx, cy, cx + int(r * 0.7 * math.cos(2.4 + k)), cy - int(r * 0.7 * abs(math.sin(2.4 + k)))], fill=(230, 120, 40))
            for k in range(6):
                d.rectangle([12 + k * 18, 52, 22 + k * 18, 58], fill=(60, 60, 64), outline=(120, 120, 124))
        else:
            d.rounded_rectangle([30, 18, 98, 50], 6, fill=(44, 44, 46), outline=(80, 80, 84))
            d.rectangle([56, 30, 72, 34], fill=(150, 150, 154))
    elif name == 'cowl':
        d.rectangle([0, 0, w, hh], fill=spec.get('cowl', (30, 31, 34)))
        f = font(15)
        d.text((10, 16), spec.get('engine', 'TIDEMARK'), fill=(230, 230, 226), font=f)
        d.rectangle([10, 38, 118, 41], fill=spec.get('accent', (128, 24, 30)))
        f2 = font(10)
        d.text((86, 44), spec.get('hp', '200'), fill=(200, 200, 196), font=f2)
    elif name == 'cabin_side':
        d.rectangle([0, 0, w, hh], fill=spec.get('cabin', spec.get('deck_col', (238, 236, 228))))
        for k in range(spec.get('ports', 4)):
            cx = 30 + k * (w - 60) / max(1, spec.get('ports', 4) - 1)
            d.rounded_rectangle([cx - 20, 20, cx + 20, 42], 10, fill=(30, 36, 42), outline=(180, 182, 186), width=2)
    elif name == 'cabin_front':
        d.rectangle([0, 0, w, hh], fill=(150, 100, 58))
        d.rectangle([0, 0, w - 1, hh - 1], outline=(196, 198, 200), width=3)
        for k in range(5):
            y = 8 + k * 11
            d.rectangle([10, y, w - 10, y + 8], fill=(126, 80, 44))
            for x in range(14, w - 14, 4):
                d.line([x, y + 1, x + 2, y + 7], fill=(70, 44, 24))
        d.rectangle([w // 2 - 8, 28, w // 2 + 8, 34], fill=(200, 200, 204))
    elif name == 'console':
        d.rectangle([0, 0, w, hh], fill=spec.get('deck_col', (238, 236, 228)))
        for k in range(5):
            d.rectangle([20, 14 + k * 8, 108, 17 + k * 8], fill=(200, 198, 190))
    return np.asarray(im, float)


def build_atlas(spec, h, rng):
    img = np.zeros((ATLAS, ATLAS, 3))
    img[0:HULL_ROWS] = hull_chart(spec, h, 1, rng)
    img[HULL_ROWS:2 * HULL_ROWS] = hull_chart(spec, h, -1, rng)
    for name, (x, y, w, hh) in DECALS.items():
        img[y:y + hh, x:x + w] = decal(name, spec, h, w, hh)
    for mat in MATERIALS:
        p = pattern(mat, spec, rng)
        x, y = tile_origin(mat)
        img[y:y + TILE, x:x + TILE] = np.tile(p, (2, 2, 1))
    return img


def region(img, mat):
    x, y = tile_origin(mat)
    return img[y:y + TILE, x:x + TILE]


def chart_z(side, u, h):
    return (h.z1 + 0.3 - u / HULL_PX) if side > 0 else (h.z0 - 0.3 + u / HULL_PX)


def weather(img, state, spec, h, rng):
    """Grime, an algae line, rust streaks, silvered teak and faded canvas for an abandoned boat; scorch,
    scrapes and holes for a wreck; for a burnt one, char down to a scorched line at the water."""
    out = img.copy()
    sd = int(rng.integers(1 << 30))
    grime = periodic_noise(ATLAS, 1.5, sd)
    fine = periodic_noise(ATLAS, 0.6, sd + 1)
    if state == 'usable':
        return np.clip(out * (1 - 0.03 * np.clip(grime, 0, 3)[..., None]), 0, 255)
    rows = np.arange(HULL_ROWS)
    Y = (HULL_TOP - (rows + 0.5) / HULL_PX)[:, None]
    if state == 'burnt':
        char = np.array([22, 20, 18], float)
        blist = np.clip(0.5 + periodic_noise(ATLAS, 0.9, sd + 7) * 0.3, 0, 1)[..., None]
        burnt = char * (0.8 + 0.45 * blist) + np.array([10, 6, 2]) * blist
        for y0 in (0, HULL_ROWS):
            k = np.clip((Y - (WL - 0.02)) / 0.3, 0, 1)[..., None] * 0.9 + 0.08
            sect = out[y0:y0 + HULL_ROWS]
            sect[:] = sect * 0.55 * (1 - k) + burnt[y0:y0 + HULL_ROWS] * k
            out[y0:y0 + HULL_ROWS] = sect
        rest = out[2 * HULL_ROWS:]
        k = np.clip(0.88 + fine[2 * HULL_ROWS:, :, None] * 0.06, 0, 1)
        out[2 * HULL_ROWS:] = rest * (1 - k) + burnt[2 * HULL_ROWS:] * k
        ash = np.clip(periodic_noise(ATLAS, 1.1, sd + 9) - 1.6, 0, 1)[..., None]
        out = out * (1 - ash * 0.4) + np.array([104, 100, 94]) * ash * 0.4
        for mat in METAL:
            r = region(out, mat)
            r[:] = r * 0.45 + np.array([92, 86, 78]) * 0.4
        return np.clip(out, 0, 255)
    g = np.clip(0.5 + grime * 0.35 + fine * 0.15, 0, 1)
    a = 0.14 + g * 0.2
    out = out * (1 - a[..., None]) + np.array([92, 86, 70], float) * a[..., None]
    grey = out.mean(-1, keepdims=True)
    out = out * 0.8 + grey * 0.2
    # teak goes silver, canvas fades and spots with mildew, vinyl yellows
    t = region(out, 'teak')
    t[:] = t.mean(-1, keepdims=True) * np.array([1.0, 0.98, 0.94]) * 1.05 + 18
    for mat in ('canvas', 'sail'):
        c = region(out, mat)
        c[:] = c * 0.75 + 52
        spots = np.clip(periodic_noise(TILE, 0.5, sd + 11) - 1.6, 0, 1)[..., None]
        c[:] = c * (1 - spots * 0.6) + np.array([40, 44, 30]) * spots * 0.6
    for mat in ('vinyl', 'vinyl_lo', 'cooler'):
        v = region(out, mat)
        v[:] = v * np.array([1.0, 0.96, 0.86])
    # the hull charts: algae at the waterline, streaks running down from the sheer
    for side, y0 in ((1, 0), (-1, HULL_ROWS)):
        sect = out[y0:y0 + HULL_ROWS]
        band = np.exp(-((Y - (WL + 0.05)) / 0.09) ** 2)
        k = (band * (0.55 + 0.3 * np.clip(fine[y0:y0 + HULL_ROWS], -1, 1)))[..., None]
        sect[:] = sect * (1 - k) + np.array([62, 78, 40], float) * k
        for _ in range(int(h.L * 7)):
            u = int(rng.integers(0, int((h.L + 0.6) * HULL_PX)))
            z = chart_z(side, u, h)
            top = int((HULL_TOP - h.ys(min(max(z, h.z0), h.z1))) * HULL_PX)
            ln = int(rng.integers(20, 90))
            wdt = int(rng.integers(1, 4))
            col = np.array([120, 70, 36], float) if rng.random() < 0.4 else np.array([70, 66, 58], float)
            for r in range(max(0, top), min(HULL_ROWS, top + ln)):
                f = 0.35 * (1 - (r - top) / ln)
                sect[r, u:u + wdt] = sect[r, u:u + wdt] * (1 - f) + col * f
        out[y0:y0 + HULL_ROWS] = sect
    if state == 'abandoned':
        for _ in range(500):
            x, y = int(rng.integers(0, ATLAS)), int(rng.integers(512, ATLAS))
            out[y:y + 2, x:x + 2] = (232, 230, 220)
        return np.clip(out, 0, 255)
    soot = np.clip(periodic_noise(ATLAS, 1.9, sd + 5) - 0.7, 0, 2)[..., None]
    k = np.clip(soot * 0.6, 0, 0.85)
    out = out * (1 - k) + np.array([20, 18, 16]) * k
    # scrapes along the topsides and a few holes stove in above the waterline
    for side, y0 in ((1, 0), (-1, HULL_ROWS)):
        for _ in range(16):
            u = int(rng.integers(0, int(h.L * HULL_PX)))
            r = int(rng.integers(30, 150))
            ln = int(rng.integers(20, 120))
            out[y0 + r:y0 + r + 1, u:u + ln] = out[y0 + r:y0 + r + 1, u:u + ln] * 0.5 + 100
        for _ in range(int(rng.integers(1, 4))):
            u = int(rng.integers(40, int(h.L * HULL_PX) - 40))
            z = chart_z(side, u, h)
            ys = h.ys(min(max(z, h.z0), h.z1))
            r0 = int((HULL_TOP - (WL + 0.15 + rng.random() * (ys - WL - 0.35))) * HULL_PX)
            rad = rng.uniform(5, 10)
            yy, xx = np.mgrid[-16:17, -16:17]
            ang = np.arctan2(yy, xx)
            edge = rad * (1 + 0.35 * np.sin(ang * 5 + rng.random() * 6) + 0.2 * np.sin(ang * 11 + rng.random() * 6))
            d = np.hypot(xx, yy * 1.2)
            reg = out[y0 + r0 - 16:y0 + r0 + 17, u - 16:u + 17]
            if reg.shape[:2] == d.shape:
                reg[(d < edge + 4) & (d >= edge)] = reg[(d < edge + 4) & (d >= edge)] * 0.5 + np.array([110, 104, 88]) * 0.5
                reg[(d < edge + 9) & (d >= edge + 4) & ((xx + yy) % 3 == 0)] *= 0.7
                reg[d < edge] = (12, 11, 10)
    return np.clip(out, 0, 255)


# ---------------------------------------------------------------- states and packing

METAL = ('steel', 'alu', 'engine_metal', 'engine_lower', 'prop', 'mast')


def state_mesh(g, h, state, rng, metal_hull=False):
    """The triangles a state keeps, [(triangle as built, material, decal uvs)], and how a state poses
    them: a wreck lists and settles by the stern; a burnt boat has burnt down to her waterline."""
    T = np.array(g.T)
    G = np.array(g.G)
    M = np.array(g.M)
    keep = np.ones(len(T), bool)
    cen = T.mean(1)
    torn = periodic_noise(64, 1.2, int(rng.integers(1 << 30)))

    def shred(sel, amount):
        ix = (np.floor(cen[:, 0] * 6) % 64).astype(int)
        iz = (np.floor(cen[:, 2] * 6) % 64).astype(int)
        return sel & (torn[iz, ix] < (amount - 0.5) * 3)

    if state in ('wrecked', 'burnt'):
        for grp in set(G):
            if grp.startswith('glass') and (state == 'burnt' or rng.random() < 0.8):
                keep &= G != grp
        keep &= ~((M == 'glass') & (state == 'burnt'))
        keep &= G != 'fender'
    if state == 'abandoned':
        keep &= ~shred(G == 'canvas', 0.2)
    if state == 'wrecked':
        keep &= ~shred(G == 'canvas', 0.6)
        if rng.random() < 0.5:
            keep &= G != 'cushion'
    items = []
    if state != 'burnt' or metal_hull:
        if state == 'burnt':
            keep &= ~np.isin(G, ['cushion', 'loose']) & (M != 'engine')
        for i in np.where(keep)[0]:
            items.append((T[i], M[i], g.UV[i]))
    else:
        keep &= ~np.isin(G, ['canvas', 'cushion', 'fender', 'loose', 'sail', 'rigtop', 'boom']) & (M != 'engine')
        ph = rng.uniform(0, 2 * math.pi, 4)

        def cut(p):
            return (WL + 0.3 + 0.1 * np.sin(p[..., 2] * 1.7 + ph[0]) + 0.07 * np.sin(p[..., 2] * 4.1 + ph[1])
                    + 0.05 * np.sin(p[..., 0] * 5.3 + ph[2]) + 0.03 * np.sin(p[..., 2] * 11 + ph[3]))

        for i in np.where(keep)[0]:
            if M[i] in METAL[2:]:
                items.append((T[i], M[i], g.UV[i]))
                continue
            if T[i][:, 1].min() > cut(T[i]).max() + 0.05:
                continue
            parts = [T[i]] if T[i][:, 1].max() < cut(T[i]).min() - 0.05 or g.UV[i] is not None else subdivide([T[i]], None, 0.12)
            for q in parts:
                if q.mean(0)[1] < cut(q.mean(0)):
                    items.append((q, M[i], g.UV[i]))
    if state == 'wrecked':
        R = rot_z(math.radians(4.5)) @ rot_x(math.radians(-1.5))
        piv = v3(0, WL - 0.12, 0)

        def pose(t):
            return (t - piv) @ R.T + piv
    else:
        def pose(t):
            return t
    return items, pose


def build(key, spec, preview=None):
    g, h = spec['build'](spec)
    print(f'{key}: {len(g.T)} triangles', flush=True)
    T0 = np.array(g.T)
    base_rng = np.random.default_rng(java_hash(key) & 0xFFFFF)
    atlas = build_atlas(spec, h, base_rng)
    variants = []
    for state in VARIANTS:
        rng = np.random.default_rng(java_hash(key + state) & 0xFFFFF)
        items, pose = state_mesh(g, h, state, rng, spec.get('riveted', False))
        tris, uvs, glass = [], [], []
        for t, mat, uvx in items:
            if uvx is not None:
                x0, y0, w, hh = DECALS[mat]
                uvs.append(np.stack([(x0 + 0.5 + uvx[:, 0] * (w - 1)) / ATLAS, (y0 + 0.5 + uvx[:, 1] * (hh - 1)) / ATLAS], -1))
                tris.append(pose(t))
                glass.append(False)
            elif mat in ('hull_port', 'hull_stbd'):
                uvs.append(hull_uv(t[None], 1 if mat == 'hull_port' else -1, h)[0])
                tris.append(pose(t))
                glass.append(False)
            elif mat == 'transom':
                uvs.append(transom_uv(t[None], h)[0])
                tris.append(pose(t))
                glass.append(False)
            else:
                for p in subdivide([t], None):
                    uvs.append(tile_uv(p[None], mat)[0])
                    tris.append(pose(p))
                    glass.append(mat == 'glass')
        img = weather(atlas, state, spec, h, rng)
        im = Image.fromarray(img.astype(np.uint8))
        variants.append(dict(state=state, T=np.array(tris), UV=np.array(uvs), glass=np.array(glass), img=im))
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
        v['img'].save(jpg, 'JPEG', quality=85, subsampling=0)
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
        buf.write(struct.pack('>i', 0))
        buf.write(struct.pack('>i', 0))
    blob_ = scramble(zlib.compress(buf.getvalue(), 9), key)
    with open(os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/cars', key + '.zcar'), 'wb') as f:
        f.write(blob_)
    # what can be stood on: her hull, decks and soles (not rails, glass, canvas or the rig)
    solid = np.array([grp in ('body',) and m not in ('glass', 'steel', 'canvas', 'strap', 'rope', 'antenna', 'mast', 'sail')
                      for grp, m in zip(g.G, g.M)])
    S = T0[solid] - centre
    glo, nx, nz, grid = heights(S)
    glo = glo.copy()
    size = hi - lo
    print(f'{key:20s} {len(blob_) // 1024:5d} KB  {size[0]:.1f} x {size[1]:.1f} x {size[2]:.1f} m  '
          + '  '.join(f'{v["state"]} {len(v["T"])}' for v in variants), flush=True)
    return {'id': key, 'kind': 'boat', 'weight': spec['weight'], 'size': [round(float(s), 3) for s in size],
            'cell': CELL, 'x0': round(float(glo[0]), 3), 'z0': round(float(glo[2]), 3), 'nx': nx, 'nz': nz,
            'heights': [int(round(max(0.0, hh) * 100)) for hh in grid.ravel()],
            'seats': [], 'wheelbase': round(float(size[2] * 0.6), 3)}


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
    print(len(models), 'boats')


if __name__ == '__main__':
    main()
