"""Loot items as toon meshes instead of block elements.

The same builders and painter as gen_loot.py, with the block limits taken off: bars keep their true angle (no 22.5
degree snapping), arcs are finer, and the three stepped boxes of a round bar become one real cylinder whose sides
and ends are mapped onto the painted faces of those boxes, so labels and wear carry over. Every other box stays a
box, rotated exactly. Output is triangles in item model space (0-16) with uvs into one painted atlas per item.

    python3 mesh.py --only 'canned_beans|flashlight' --out /tmp/x     # preview json + atlas png per item
"""
import argparse
import json
import math
import re
import sys
from pathlib import Path

import numpy as np

import gen_loot as G

M, kit = G.M, G.kit
SEGS = 24                  # sides on a full cylinder


# ---------------------------------------------------------------- free angles

def _seg(self, p0, p1, w, t, mat, z=0.0, plane='xy', over=None, **kw):
    """Model.seg without the 22.5 degree snap: the bar keeps the angle it was drawn at."""
    (x0, y0), (x1, y1) = p0, p1
    L = math.hypot(x1 - x0, y1 - y0)
    phi = math.degrees(math.atan2(y1 - y0, x1 - x0))
    base = round(phi / 90.0) * 90.0
    ang = phi - base
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    ext = over if over is not None else w * 0.25
    half_l, half_w = L / 2 + ext, w / 2
    along_x = round(base / 90.0) % 2 == 0
    hx, hy = (half_l, half_w) if along_x else (half_w, half_l)
    a = 0 if plane == 'xy' else 2
    lo, hi = [0, 0, 0], [0, 0, 0]
    lo[a], hi[a] = mx - hx, mx + hx
    lo[1], hi[1] = my - hy, my + hy
    d = 2 if plane == 'xy' else 0
    lo[d], hi[d] = z - t / 2, z + t / 2
    rot = None
    if abs(ang) > 1e-6:
        origin = [0, 0, 0]
        origin[a], origin[1], origin[d] = mx, my, z
        rot = dict(axis='z' if plane == 'xy' else 'x', angle=ang if plane == 'xy' else -ang, origin=origin)
    return self.box(lo, hi, mat, rot=rot, axis=a if along_x else 1, **kw)


def _arc(self, centre, radius, a0, a1, w, t, mat, steps=None, z=0.0, **kw):
    steps = max(steps or 0, int(abs(a1 - a0) / 7.5), 2)
    pts = [(centre[0] + radius * math.cos(math.radians(a0 + (a1 - a0) * i / steps)),
            centre[1] + radius * math.sin(math.radians(a0 + (a1 - a0) * i / steps))) for i in range(steps + 1)]
    # a little overlap so the finer pieces close up round the bend
    return [self.seg(pts[i], pts[i + 1], w, t, mat, z=z, over=w * 0.1, **kw) for i in range(steps)]


M.Model.seg = _seg
M.Model.arc = _arc


# ---------------------------------------------------------------- geometry

def rot_of(p):
    """4x4 for a part's rotation (identity when it has none)."""
    T = np.eye(4)
    r = p.get('rot')
    if not r:
        return T
    a = math.radians(r['angle'])
    c, s = math.cos(a), math.sin(a)
    R = {'x': [[1, 0, 0], [0, c, -s], [0, s, c]], 'y': [[c, 0, s], [0, 1, 0], [-s, 0, c]],
         'z': [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[r['axis']]
    o = np.array(r['origin'], float)
    T[:3, :3] = R
    T[:3, 3] = o - np.array(R) @ o
    return T


def quad(face, lo, hi):
    """A face's corners TL, TR, BR, BL as seen from outside (Minecraft's uv order)."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    return np.array({'north': [[x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]],
                     'south': [[x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]],
                     'east': [[x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]],
                     'west': [[x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0]],
                     'up': [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]],
                     'down': [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]]}[face], float)


def face_name(ax, sgn):
    return next(n for n, f in M.FACES.items() if f[0] == ax and f[1] == sgn)


class Mesh:
    def __init__(self):
        self.v, self.uv, self.n = [], [], []

    def tri(self, ps, uvs, ns):
        for p, t, n in zip(ps, uvs, ns):
            self.v.append(p)
            self.uv.append(t)
            self.n.append(n)


def project(p, q, rect):
    """uv of point p on face quad q (TL, TR, BR, BL) whose atlas rect is [u0, v0, u1, v1] (0-16), clamped to it."""
    tl, tr, bl = q[0], q[1], q[3]
    du, dv = tr - tl, bl - tl
    s = np.clip(np.dot(p - tl, du) / max(np.dot(du, du), 1e-9), 0, 1)
    t = np.clip(np.dot(p - tl, dv) / max(np.dot(dv, dv), 1e-9), 0, 1)
    u0, v0, u1, v1 = rect
    return ((u0 + (u1 - u0) * s) / 16, (v0 + (v1 - v0) * t) / 16)


def add_box(mesh, p, rects):
    T = rot_of(p)
    for face, rect in rects.items():
        q = quad(face, p['lo'], p['hi'])
        w = (np.c_[q, np.ones(4)] @ T.T)[:, :3]
        u0, v0, u1, v1 = [c / 16 for c in rect]
        t = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        ax, sgn = M.FACES[face][:2]
        n = np.zeros(3)
        n[ax] = sgn
        n = T[:3, :3] @ n
        mesh.tri([w[0], w[3], w[2]], [t[0], t[3], t[2]], [n] * 3)
        mesh.tri([w[0], w[2], w[1]], [t[0], t[2], t[1]], [n] * 3)


def add_cylinder(mesh, boxes, rects):
    """One round bar from its stepped boxes: sides mapped onto the widest box's side faces, ends onto the middle
    box's ends (a box's face missing from rects is buried, so that stretch isn't drawn)."""
    first = boxes[0]
    a, r = first['axis'], first['round']
    o = [i for i in range(3) if i != a]
    lo, hi = first['lo'], first['hi']
    c = {o[0]: (lo[o[0]] + hi[o[0]]) / 2, o[1]: (lo[o[1]] + hi[o[1]]) / 2}
    y0, y1 = lo[a], hi[a]
    T = rot_of(first)

    def wide(ax):        # the box whose face looking along ax is widest
        return max(boxes, key=lambda b: b['hi'][o[0] if ax == o[1] else o[1]] - b['lo'][o[0] if ax == o[1] else o[1]])

    def pt(theta, y):
        p = np.zeros(3)
        p[a] = y
        p[o[0]] = c[o[0]] + r * math.cos(theta)
        p[o[1]] = c[o[1]] + r * math.sin(theta)
        return p

    def apply(p):
        return (T @ np.r_[p, 1])[:3]

    R3 = T[:3, :3]
    for i in range(SEGS):
        t0, t1 = 2 * math.pi * i / SEGS, 2 * math.pi * (i + 1) / SEGS
        tm = (t0 + t1) / 2
        cm, sm = math.cos(tm), math.sin(tm)
        ax, sgn = (o[0], 1 if cm > 0 else -1) if abs(cm) >= abs(sm) else (o[1], 1 if sm > 0 else -1)
        b = wide(ax)
        name = face_name(ax, sgn)
        key = (id(b), name)
        if key not in rects:
            continue
        q = quad(name, b['lo'], b['hi'])
        ps = [pt(t0, y0), pt(t1, y0), pt(t1, y1), pt(t0, y1)]
        ts = [project(p, q, rects[key]) for p in ps]
        ns = []
        for th in (t0, t1, t1, t0):
            n = np.zeros(3)
            n[o[0]], n[o[1]] = math.cos(th), math.sin(th)
            ns.append(R3 @ n)
        w = [apply(p) for p in ps]
        # outward winding: the side seen from outside runs counter-clockwise
        if np.dot(np.cross(w[1] - w[0], w[2] - w[0]), ns[0] + ns[1]) < 0:
            w, ts, ns = w[::-1], ts[::-1], ns[::-1]
        mesh.tri([w[0], w[1], w[2]], [ts[0], ts[1], ts[2]], [ns[0], ns[1], ns[2]])
        mesh.tri([w[0], w[2], w[3]], [ts[0], ts[2], ts[3]], [ns[0], ns[2], ns[3]])
    mid = boxes[-1]
    for sgn, y in ((1, y1), (-1, y0)):
        name = face_name(a, sgn)
        key = (id(mid), name)
        if key not in rects:
            continue
        q = quad(name, mid['lo'], mid['hi'])
        n = np.zeros(3)
        n[a] = sgn
        n = R3 @ n
        centre = np.zeros(3)
        centre[a], centre[o[0]], centre[o[1]] = y, c[o[0]], c[o[1]]
        for i in range(SEGS):
            p0 = pt(2 * math.pi * i / SEGS, y)
            p1 = pt(2 * math.pi * (i + 1) / SEGS, y)
            w = [apply(centre), apply(p0), apply(p1)]
            ts = [project(p, q, rects[key]) for p in (centre, p0, p1)]
            if np.dot(np.cross(w[1] - w[0], w[2] - w[0]), n) < 0:
                w, ts = [w[0], w[2], w[1]], [ts[0], ts[2], ts[1]]
            mesh.tri(w, ts, [n] * 3)


def add_sphere(mesh, boxes, rects):
    """A ball from its two stepped rods: each patch of the surface is mapped onto the box face it looks through."""
    lo = np.min([b['lo'] for b in boxes], 0)
    hi = np.max([b['hi'] for b in boxes], 0)
    c, rad = (lo + hi) / 2, (hi - lo) / 2
    a = boxes[0]['axis']
    T = rot_of(boxes[0])
    R3 = T[:3, :3]
    n_lat, n_lon = 12, SEGS

    def pt(i, j):
        th, ph = math.pi * i / n_lat, 2 * math.pi * j / n_lon
        d = np.zeros(3)
        o = [k for k in range(3) if k != a]
        d[a] = math.cos(th)
        d[o[0]], d[o[1]] = math.sin(th) * math.cos(ph), math.sin(th) * math.sin(ph)
        return c + d * rad, d

    for i in range(n_lat):
        for j in range(n_lon):
            cs = [pt(i, j), pt(i + 1, j), pt(i + 1, j + 1), pt(i, j + 1)]
            mid = sum(d for _p, d in cs)
            ax = int(np.argmax(np.abs(mid)))
            sgn = 1 if mid[ax] > 0 else -1
            name = face_name(ax, sgn)
            # the face that sees this patch: the box reaching furthest out along it, with that face drawn
            cand = [b for b in boxes if (id(b), name) in rects]
            if not cand:
                continue
            b = max(cand, key=lambda b: b['hi'][ax] if sgn > 0 else -b['lo'][ax])
            q = quad(name, b['lo'], b['hi'])
            ps = [p for p, _d in cs]
            ts = [project(p, q, rects[(id(b), name)]) for p in ps]
            ns = [R3 @ (d / np.maximum(rad, 1e-6) / np.linalg.norm(d / np.maximum(rad, 1e-6))) for _p, d in cs]
            w = [(T @ np.r_[p, 1])[:3] for p in ps]
            for tri in ((0, 1, 2), (0, 2, 3)):
                tw = [w[k] for k in tri]
                cr = np.cross(tw[1] - tw[0], tw[2] - tw[0])
                if np.linalg.norm(cr) < 1e-9:
                    continue
                order = tri if np.dot(cr, sum(ns[k] for k in tri)) > 0 else tri[::-1]
                mesh.tri([w[k] for k in order], [ts[k] for k in order], [ns[k] for k in order])


def rod_groups(parts):
    """Runs of parts that one rod() call made: (start index, count)."""
    out, i = [], 0
    while i < len(parts):
        p = parts[i]
        if 'round' not in p:
            i += 1
            continue
        n = 1 if p['round'] < 0.3 else 3
        run = parts[i:i + n]
        if len(run) == n and all(q.get('round') == p['round'] and q.get('axis') == p.get('axis') for q in run):
            out.append((i, n))
            i += n
        else:
            i += 1
    return out


def build(name):
    """(mesh, atlas image dict) for one item, in item model space centred on 8."""
    m = G.make(name)
    patches = [((0, key[1]), face, layers) for key, face, layers in kit.paint([m], px=G.px_for(m))]
    img, uvs, T = kit.pack(patches)
    lo, hi = m.bounds()
    shift = np.array([8.0, 8.0, 8.0]) - (lo + hi) / 2
    parts = []
    for p in m.parts:
        q = dict(p, lo=p['lo'] + shift, hi=p['hi'] + shift)
        if p['rot']:
            q['rot'] = dict(p['rot'], origin=list(np.asarray(p['rot']['origin']) + shift))
        parts.append(q)
    rects_of = {}
    for (key, face), rect in uvs.items():
        rects_of.setdefault(key[1], {})[face] = rect
    mesh = Mesh()
    done = set()
    k = 0
    while k < len(parts):          # balls first: runs of rod boxes carrying the same ball tag
        tag = parts[k].get('ball')
        if tag is None:
            k += 1
            continue
        end = k
        while end < len(parts) and parts[end].get('ball') == tag:
            end += 1
        boxes = parts[k:end]
        rects = {(id(b), f): r for j, b in zip(range(k, end), boxes) for f, r in rects_of.get(j, {}).items()}
        add_sphere(mesh, boxes, rects)
        done.update(range(k, end))
        k = end
    for start, n in rod_groups(parts):
        if start in done:
            continue
        boxes = parts[start:start + n]
        rects = {(id(b), f): r for k, b in zip(range(start, start + n), boxes) for f, r in rects_of.get(k, {}).items()}
        add_cylinder(mesh, boxes, rects)
        done.update(range(start, start + n))
    for k, p in enumerate(parts):
        if k not in done and k in rects_of:
            add_box(mesh, p, rects_of[k])
    return mesh, img, T


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default='.')
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    for name in [n for n in G.ITEMS if re.search(a.only, n)]:
        mesh, img, T = build(name)
        kit.save_textures(img, out / f'{name}.png')
        json.dump({'tex': f'{name}.png', 'v': np.round(np.array(mesh.v), 4).tolist(),
                   'uv': np.round(np.array(mesh.uv), 5).tolist(), 'n': np.round(np.array(mesh.n), 3).tolist()},
                  open(out / f'{name}.mesh.json', 'w'))
        print(name, len(mesh.v) // 3, 'tris', f'{T}px')


if __name__ == '__main__':
    sys.exit(main())
