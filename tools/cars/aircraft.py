#!/usr/bin/env python3
"""Turns the aircraft models Josiah bought into the carrier's parked jets and helicopter.

    python3 tools/cars/aircraft.py SRC_DIR [--only corvid] [--preview OUT_DIR]

SRC_DIR is jets.zip from the project files, unpacked: f22/F22-RAPTOR.obj (+ tex/), apache/Apache
AH64_LOW.obj (+ tex/) and su27/obj/OBJ FILE.obj (+ OBJ MATERIALS/). Their licences let the mod use
them but not pass the files on, so the originals never go in this repository; only the repainted,
packed models do (credited in CREDITS.md):
    jet_corvid      the single-seat stealth fighter (the bought F-22 model)
    jet_gyrfalcon   the twin-tailed heavy fighter (the bought Su-27 model, cut down by Reduce.java)
    heli_hornbill   the two-seat attack helicopter (the bought AH-64D model), on the carrier's helipad

Each model is turned into the cars' model space (metres, wheels on y = 0, nose toward +z, the
pilot's left toward +x). The game culls back faces, so seen.js (headless Chromium) renders the
model from a few hundred cameras to turn every triangle toward the side people see, twin the bare
sheets and drop what nobody can see; Reduce.java decimates a model too heavy to ship and later
finds, for every texel, the spot on the full model it came from. Each model gets its own texture
atlas (the source's own uv islands where they're sound, flat charts elsewhere), each texel painted
from the source texture under it, then given made-up markings (no real insignia, names or
serials) and weathered for the state in 3D, so the paint flows across chart seams. The
states are the cars': usable (clean), abandoned (grimy, streaked, gull droppings, a hydraulic
leak), wrecked (nose gear folded so the nose rests on the deck, a tailplane gone, glass smashed,
scorched and holed) and burnt (a charred shell with no glass).
"""
import argparse
import io
import json
import os
import struct
import subprocess
import sys
import tempfile
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from pack import CELL, VARIANTS, heights, java_hash, scramble  # noqa: E402
from wreck import fbm, sstep, voronoi_edge  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
ATLAS_W, ATLAS_H = 2048, 1024


# ---------------------------------------------------------------- source models

def load_mtl(path):
    mats, m = {}, None
    for line in open(path, errors='replace'):
        p = line.strip().split(None, 1)
        if not p:
            continue
        if p[0] == 'newmtl':
            m = mats.setdefault(p[1].strip(), {'Kd': (0.7, 0.7, 0.7), 'd': 1.0, 'map': None})
        elif m is None or len(p) < 2:
            continue
        elif p[0] == 'Kd':
            m['Kd'] = tuple(float(x) for x in p[1].split()[:3])
        elif p[0] == 'd':
            m['d'] = float(p[1].split()[0])
        elif p[0] == 'Tr':
            m['d'] = 1 - float(p[1].split()[0])
        elif p[0] == 'map_Kd':
            m['map'] = p[1].strip().replace('\\', '/')
    return mats


def load_obj(path):
    """Triangles of an OBJ with their source uvs (nan where none), material and object names."""
    V, VT = [], []
    tri, tex, mat, obj = [], [], [], []
    mnames, onames = {}, {}
    m = o = 0
    lib = None
    with open(path, errors='replace') as f:
        for line in f:
            c = line[:2]
            if c == 'v ':
                p = line.split()
                V.append((float(p[1]), float(p[2]), float(p[3])))
            elif c == 'vt':
                p = line.split()
                VT.append((float(p[1]), float(p[2]) if len(p) > 2 else 0.0))
            elif c == 'f ':
                vs, ts = [], []
                for t in line.split()[1:]:
                    q = t.split('/')
                    i = int(q[0])
                    vs.append(i - 1 if i > 0 else len(V) + i)
                    j = int(q[1]) if len(q) > 1 and q[1] else 0
                    ts.append(j - 1 if j > 0 else (len(VT) + j if j < 0 else -1))
                for k in range(1, len(vs) - 1):
                    tri.append((vs[0], vs[k], vs[k + 1]))
                    tex.append((ts[0], ts[k], ts[k + 1]))
                    mat.append(m)
                    obj.append(o)
            elif line.startswith('usemtl'):
                name = line[6:].strip()
                m = mnames.setdefault(name, len(mnames))
            elif c in ('o ', 'g '):
                name = line[2:].strip()
                o = onames.setdefault(name, len(onames))
            elif line.startswith('mtllib'):
                lib = line[6:].strip()
    V = np.array(V)
    VT = np.vstack([np.array(VT).reshape(-1, 2), [[np.nan, np.nan]]])
    VT[np.abs(VT) > 1000] = np.nan  # some exporters leave garbage (1e38) in unused uvs
    tri, tex = np.array(tri), np.array(tex)
    return dict(P=V[tri], UV=VT[tex], mat=np.array(mat), obj=np.array(obj),
                mnames=sorted(mnames, key=mnames.get), onames=sorted(onames, key=onames.get), mtllib=lib)


class Aircraft:
    """One bought model, turned into model space, with per-triangle classes and parts."""

    def __init__(self, spec, src):
        self.spec = spec
        path = os.path.join(src, spec['obj'])
        cache = os.path.join(src, '.cache', spec['id'] + '.npz')
        if spec.get('reduced'):
            # decimate.py's output: already in model space, classes and materials carried over
            d = np.load(os.path.join(src, spec['reduced']), allow_pickle=True)
            m = {k: d[k] for k in d.files}
            m['mnames'], m['onames'] = list(m['mnames']), list(m['onames'])
        else:
            m = load_obj(path)
        base = os.path.dirname(path)
        mats = spec.get('mtl') or (load_mtl(os.path.join(base, m['mtllib'])) if m.get('mtllib') and os.path.exists(os.path.join(base, m['mtllib'])) else {})
        self.mats = [mats.get(n, {'Kd': (0.7, 0.7, 0.7), 'd': 1.0, 'map': None}) for n in m['mnames']]
        self.base = base
        if spec.get('drop'):
            on0 = np.array(m['onames'] + [''], object)[m['obj']].astype(str)
            stray = np.zeros(len(on0), bool)
            for n in spec['drop']:
                stray |= np.char.find(np.char.add(np.char.add(' ', on0), ' '), ' ' + n + ' ') >= 0
            stray |= np.isin(np.array(m['mnames'] + [''], object)[m['mat']].astype(str), list(spec['drop']))
            for k in ('P', 'UV', 'mat', 'obj'):
                m[k] = m[k][~stray]
        P = m['P'] * spec.get('scale', 1.0)
        if spec.get('turn'):
            P = P * np.array([-1, 1, -1])
        lo, hi = P.reshape(-1, 3).min(0), P.reshape(-1, 3).max(0)
        P = P - np.array([(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2])
        self.P, self.UV, self.mat, self.obj = P, m['UV'], m['mat'], m['obj']
        self.mnames, self.onames = m['mnames'], m['onames']
        on = np.array(self.onames + [''], object)[self.obj]
        mn = np.array(self.mnames + [''], object)[self.mat]
        self.part_names = on
        cls = np.full(len(P), 'body', object)
        for i, mm in enumerate(self.mats):
            if mm['d'] < 0.9:
                cls[self.mat == i] = 'glass'
        self.cls = cls
        cls[self.objects(spec.get('glass', ())) | np.isin(mn, list(spec.get('glass', ())))] = 'glass'
        # a cockpit group that also holds the windscreen keeps it as glass
        cockpit = self.objects(spec.get('cockpit', ())) | np.isin(mn, list(spec.get('cockpit', ())))
        cls[cockpit & (cls != 'glass')] = 'interior'
        # a cockpit modelled as part of the hull (tub, seat, panel) is whatever lies wholly in a box
        for box in spec.get('tub', ()):
            inside = ((P >= np.array(box[:3])) & (P <= np.array(box[3:]))).all(axis=(1, 2))
            cls[inside & (cls == 'body')] = 'interior'

    def objects(self, names):
        on = self.part_names.astype(str)
        sel = np.zeros(len(self.P), bool)
        for n in names:
            sel |= np.char.find(np.char.add(np.char.add(' ', on), ' '), ' ' + n + ' ') >= 0
        return sel


# ---------------------------------------------------------------- facing

def fibonacci(n):
    i = np.arange(n) + 0.5
    phi = np.arccos(1 - 2 * i / n)
    th = np.pi * (1 + 5 ** 0.5) * i
    return np.stack([np.cos(th) * np.sin(phi), np.cos(phi), np.sin(th) * np.sin(phi)], 1)


def views_for(ac):
    """Cameras all round the airframe, low down where people walk, and from each seat."""
    P = ac.P.reshape(-1, 3)
    lo, hi = P.min(0), P.max(0)
    c = (lo + hi) / 2
    diag = np.linalg.norm(hi - lo)
    out = [dict(eye=list(c + d * diag * 1.3), at=list(c), fov=45) for d in fibonacci(120)]
    r = max(hi[0] - lo[0], hi[2] - lo[2]) / 2 + 2.5
    for y in (0.4, 1.6, 3.0):
        for a in np.linspace(0, 2 * np.pi, 24, endpoint=False):
            out.append(dict(eye=[c[0] + np.cos(a) * r, y, c[2] + np.sin(a) * r], at=[c[0], max(1.0, y * 0.6), c[2]], fov=60))
    # under the wings and fuselage, looking about
    for x in np.linspace(lo[0] + 1, hi[0] - 1, 5):
        for z in np.linspace(lo[2] + 1, hi[2] - 1, 6):
            for d in ((1, 0.6, 0), (-1, 0.6, 0), (0, 0.6, 1), (0, 0.6, -1), (0, 1, 0.01)):
                out.append(dict(eye=[x, 0.35, z], at=[x + d[0], 0.35 + d[1], z + d[2]], fov=100, near=0.02))
    # from each seat, the head moved about a little (and once more in close-up, so the smallest
    # switch counts as seen), and from over the cockpit looking in: what none of these sees is
    # buried inside other parts
    for e in ac.spec['eyes']:
        e = np.array(e, float)
        for o in ((0, 0, 0), (0.12, 0, 0), (-0.12, 0, 0), (0, 0.08, 0.1), (0, -0.08, -0.05)):
            for d in list(fibonacci(26)):
                out.append(dict(eye=list(e + o), at=list(e + o + d), fov=100, near=0.02))
        for d in list(fibonacci(90)):
            out.append(dict(eye=list(e), at=list(e + d), fov=40, near=0.02))
        for a in np.linspace(0, 2 * np.pi, 12, endpoint=False):
            out.append(dict(eye=[e[0] + np.cos(a) * 1.1, e[1] + 1.0, e[2] + np.sin(a) * 1.3], at=list(e - [0, 0.5, 0]), fov=60))
    return out


def orient(ac, drop_hidden=False, twins=True):
    """The bought models mix triangle windings and the game culls back faces, so each triangle is
    turned to face the side it's seen from (a few hundred views rendered with seen.js), one seen
    from both sides (a bare sheet) gets a twin facing the other way, and with drop_hidden those no
    view sees are dropped. Glass is left out of the views, so the cockpit is seen through it, and
    always gets both sides."""
    glass = ac.cls == 'glass'
    solid = np.where(~glass)[0]
    with tempfile.TemporaryDirectory() as tmp:
        ac.P[solid].astype('<f4').tofile(os.path.join(tmp, 't.f32'))
        json.dump([{k: [float(x) for x in v] if isinstance(v, list) else float(v) for k, v in view.items()}
                   for view in views_for(ac)], open(os.path.join(tmp, 'v.json'), 'w'))
        subprocess.run(['node', os.path.join(HERE, 'seen.js'), os.path.join(tmp, 't.f32'), os.path.join(tmp, 'v.json'),
                        os.path.join(tmp, 'o.i32'), '1024'], check=True, cwd=ROOT)
        counts = np.fromfile(os.path.join(tmp, 'o.i32'), '<i4').reshape(2, -1)
    front = np.zeros(len(ac.P), int)
    back = np.zeros(len(ac.P), int)
    front[solid], back[solid] = counts
    seen = (front + back) > 0
    flip = back > front
    both = glass | ((np.minimum(front, back) >= 2) & (np.minimum(front, back) >= 0.25 * np.maximum(front, back)))
    keep = seen | glass | (not drop_hidden)
    for name in ('P', 'UV'):
        a = getattr(ac, name)
        a[flip] = a[flip][:, [0, 2, 1]]
    print(f'  facing: {flip.sum()} turned, {both.sum()} two-sided, {(~seen & ~glass).sum()} never seen'
          + (' (dropped)' if drop_hidden else ''), flush=True)
    # twins go first: they share their originals' texels, and the original (the side seen most)
    # is rasterized last, so the paint follows it
    twin = np.where(both & keep & twins)[0]
    idx = np.concatenate([twin, np.where(keep)[0]])
    rev = np.concatenate([np.ones(len(twin), bool), np.zeros(keep.sum(), bool)])
    for name in ('P', 'UV', 'mat', 'obj', 'cls', 'part_names'):
        a = getattr(ac, name)[idx]
        if name in ('P', 'UV'):
            a[rev] = a[rev][:, [0, 2, 1]]
        setattr(ac, name, a)


def java(*args):
    """Runs Reduce.java (compiled once into the source folder's cache)."""
    out = os.path.join(SRC_CACHE[0], 'java')
    if not os.path.exists(os.path.join(out, 'Reduce.class')) or \
            os.path.getmtime(os.path.join(out, 'Reduce.class')) < os.path.getmtime(os.path.join(HERE, 'Reduce.java')):
        os.makedirs(out, exist_ok=True)
        subprocess.run(['javac', '-d', out, os.path.join(HERE, 'Reduce.java')], check=True)
    subprocess.run(['java', '-Xmx6g', '-cp', out, 'Reduce'] + [str(a) for a in args], check=True)


SRC_CACHE = ['.']


def reduce(ac, target):
    """Cuts a far too detailed model down to target triangles (Reduce.java), keeping the original
    so its texture can be carried over texel by texel (source_colours). The cockpit is left whole:
    target counts only the rest."""
    inner = ac.cls == 'interior'
    cut = np.where(~inner)[0]
    with tempfile.TemporaryDirectory() as tmp:
        f = lambda n: os.path.join(tmp, n)
        ac.P[cut].astype('<f4').tofile(f('t.f32'))
        (ac.mat[cut] * 8 + np.searchsorted(['body', 'glass', 'interior'], ac.cls[cut].astype(str))).astype('<i4').tofile(f('g.i32'))
        java('decimate', f('t.f32'), f('g.i32'), target, f('o.f32'), f('s.i32'))
        P = np.fromfile(f('o.f32'), '<f4').astype(float).reshape(-1, 3, 3)
        src = cut[np.fromfile(f('s.i32'), '<i4')]
    whole = np.where(inner)[0]
    ac.source = dict(P=ac.P, UV=ac.UV, mat=ac.mat, cls=ac.cls)
    ac.P = np.concatenate([P, ac.P[whole]])
    ac.UV = np.concatenate([np.full((len(P), 3, 2), np.nan), ac.UV[whole]])
    for name in ('mat', 'obj', 'cls', 'part_names'):
        setattr(ac, name, getattr(ac, name)[np.concatenate([src, whole])])
    print(f'  cockpit kept whole: {len(whole)} triangles', flush=True)


# ---------------------------------------------------------------- states

def sag(P, keep, pivot_z, toward):
    """Pitches the airframe about the main wheels' contact line until its nose (toward +1) or its
    tail (toward -1) rests on the deck: a folded nose gear, a broken tail wheel."""
    Q = P[keep].reshape(-1, 3)
    end = (Q[:, 2] - pivot_z) * toward > 1.0

    def low(a):
        return (Q[end, 1] * np.cos(a) - (Q[end, 2] - pivot_z) * toward * np.sin(a)).min()
    a0, a1 = 0.0, 0.35
    for _ in range(40):
        mid = (a0 + a1) / 2
        if low(mid) > 0.02:
            a0 = mid
        else:
            a1 = mid
    a = a0 * toward
    y, z = P[..., 1], P[..., 2] - pivot_z
    out = P.copy()
    out[..., 1] = y * np.cos(a) - z * np.sin(a)
    out[..., 2] = pivot_z + z * np.cos(a) + y * np.sin(a)
    return out


def lost(ac, what):
    """Triangles of the named parts, or picked by a function of the aircraft."""
    sel = np.zeros(len(ac.P), bool)
    for w in what:
        sel |= w(ac) if callable(w) else ac.objects((w,))
    return sel


def state_geometry(ac, state, rng):
    """The state's triangles, moved where they've slumped: (P, keep mask)."""
    s = ac.spec
    keep = np.ones(len(ac.P), bool)
    P = ac.P.copy()
    glass = ac.cls == 'glass'
    if state == 'wrecked':
        keep &= ~lost(ac, s.get('lost_when_wrecked', ()))
        keep &= ~(glass & (rng.random(len(P)) < 0.5))
        if s.get('sag'):
            P = sag(P, keep, s['sag'][1], s['sag'][0])
    elif state == 'burnt':
        keep &= ~glass
        keep &= ~lost(ac, s.get('lost_when_burnt', ()))
    return P, keep


# ---------------------------------------------------------------- texture

def find_texture(base, name):
    if not name:
        return None
    for cand in (os.path.join(base, name), os.path.join(base, os.path.basename(name))):
        if os.path.exists(cand):
            return cand
    for root, _, files in os.walk(os.path.dirname(base)):
        if os.path.basename(name) in files:
            return os.path.join(root, os.path.basename(name))
    return None


def islands(P, UV):
    """Groups triangles into the source model's uv islands (corners joined where both the
    position and the uv match); triangles without uvs come back as -1."""
    n = len(P)
    has = ~np.isnan(UV).any(axis=(1, 2))
    key = np.concatenate([np.round(P.reshape(-1, 3) * 2000), np.round(np.nan_to_num(UV.reshape(-1, 2)) * 200000)], 1)
    _, vid = np.unique(key.astype(np.int64), axis=0, return_inverse=True)
    vid = vid.reshape(-1, 3)
    parent = np.arange(n)

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    first = {}
    for t in np.where(has)[0]:
        for v in vid[t]:
            o = first.setdefault(int(v), t)
            if o != t:
                ra, rb = find(o), find(t)
                if ra != rb:
                    parent[ra] = rb
    roots = np.array([find(t) for t in range(n)])
    roots[~has] = -1
    return roots


def projected(P, members, px):
    """A chart without source uvs: the triangles flattened onto their mean plane."""
    nrm = np.cross(P[members, 1] - P[members, 0], P[members, 2] - P[members, 0])
    n0 = nrm.sum(0)
    n0 = n0 / np.linalg.norm(n0) if np.linalg.norm(n0) > 1e-9 else np.array([0.0, 1.0, 0.0])
    up = np.array([0, 1, 0]) if abs(n0[1]) < 0.9 else np.array([1, 0, 0])
    t = np.cross(up, n0)
    t /= np.linalg.norm(t)
    b = np.cross(n0, t)
    return np.stack([P[members] @ t, -(P[members] @ b)], -1) * px


def flat_charts(P, members, part):
    """Flood-fills triangles with no source uvs into charts of similar facing, per part."""
    nrm = np.cross(P[:, 1] - P[:, 0], P[:, 2] - P[:, 0])
    area = np.linalg.norm(nrm, axis=1)
    nrm = nrm / np.maximum(area, 1e-12)[:, None]
    key = np.round(P.reshape(-1, 3) * 2000).astype(np.int64)
    _, vid = np.unique(key, axis=0, return_inverse=True)
    vid = vid.reshape(-1, 3)
    near = {}
    for t in members:
        for v in vid[t]:
            near.setdefault(int(v), []).append(t)
    chart = {}
    charts = []
    for s in sorted(members, key=lambda t: -area[t]):
        if s in chart:
            continue
        cid = len(charts)
        stack, got = [s], []
        chart[s] = cid
        while stack:
            t = stack.pop()
            got.append(t)
            for v in vid[t]:
                for u in near[int(v)]:
                    if u not in chart and part[u] == part[s] and nrm[u] @ nrm[s] > 0.7:
                        chart[u] = cid
                        stack.append(u)
        charts.append(np.array(got))
    return charts


def folds(q, px=24):
    """Whether a chart's triangles (in metres) cover the same spot twice."""
    q = q * px
    q = q - q.reshape(-1, 2).min(0)
    w, h = (np.ceil(q.reshape(-1, 2).max(0)) + 1).astype(int)
    if w * h > 4_000_000:
        return True
    hits = np.zeros((h, w), np.int32)
    for t in q:
        x0, y0 = np.floor(t.min(0)).astype(int)
        x1, y1 = np.ceil(t.max(0)).astype(int)
        xs, ys = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        a, b, c = t
        d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(d) < 1e-9:
            continue
        l0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / d
        l1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / d
        ins = (l0 > 0.02) & (l1 > 0.02) & (1 - l0 - l1 > 0.02)
        hits[y0:y1, x0:x1] += ins
    covered = (hits > 0).sum()
    return covered > 0 and (hits > 1).sum() > 0.02 * covered


def make_atlas(ac):
    """Packs the source model's own uv islands (scaled to a common texel density, so each texel
    of the atlas has one spot on the airframe) plus flat charts for anything without uvs, as
    densely as fits the atlas. The cockpit gets its own, finer density (spec cockpit_px, at least
    the bought texture's own), so its panels and dials stay as sharp as they were bought."""
    P, UV = ac.P, ac.UV
    W, H = atlas_size(ac)
    inner = ac.cls == 'interior'
    part = ac.obj * 4 + np.searchsorted(['body', 'glass', 'interior'], ac.cls.astype(str))
    roots = islands(P, UV)
    # mirrored models lay both halves over the same uvs and join them at the centre line: split
    # every island at x = 0 (and where the cockpit meets the hull), and give any part that still
    # folds over itself flat charts instead
    side = np.sign(np.round(P[:, :, 0].mean(1), 3)) + 3 * inner
    area3 = np.linalg.norm(np.cross(P[:, 1] - P[:, 0], P[:, 2] - P[:, 0]), axis=1) / 2
    groups, shapes, loose = [], [], list(np.where(roots < 0)[0])
    for r in np.unique(roots[roots >= 0]):
        for sd in (-1, 0, 1, 2, 3, 4):
            g = np.where((roots == r) & (side == sd))[0]
            if not len(g):
                continue
            q = UV[g] * np.array([1, -1])
            e1, e2 = q[:, 1] - q[:, 0], q[:, 2] - q[:, 0]
            a2 = np.abs(e1[:, 0] * e2[:, 1] - e1[:, 1] * e2[:, 0]).sum() / 2
            scale = np.sqrt(area3[g].sum() / max(a2, 1e-12))
            q = q * scale
            if a2 < 1e-9 or scale > 1e4 or folds(q):
                loose += list(g)
            else:
                groups.append(g)
                shapes.append(q)
    for g in flat_charts(P, np.array(loose, int), part) if loose else []:
        groups.append(g)
        shapes.append(projected(P, g, 1.0))
    fine = [inner[g[0]] for g in groups]
    cpx = ac.spec.get('cockpit_px', 128)
    for px in (80, 72, 64, 56, 48, 44, 40, 36, 32, 28, 24):
        uvpx = np.zeros((len(P), 3, 2))
        rects = []
        scale = [max(px, cpx) if f else px for f in fine]
        for i, (g, q) in enumerate(zip(groups, shapes)):
            q = q * scale[i]
            lo = q.reshape(-1, 2).min(0)
            w, h = q.reshape(-1, 2).max(0) - lo
            turn = w < h
            rects.append((i, lo, turn, int(np.ceil(max(w, h))) + 2, int(np.ceil(min(w, h))) + 2))
        rects.sort(key=lambda r: -r[4])
        x = y = rowh = 0
        fits = True
        for i, lo, turn, w, h in rects:
            if w > W:
                fits = False
                break
            if x + w > W:
                x, y, rowh = 0, y + rowh, 0
            q = shapes[i] * scale[i] - lo
            if turn:
                q = np.stack([q[..., 1], q[..., 0]], -1)
            uvpx[groups[i]] = q + np.array([x + 1, y + 1])
            x += w
            rowh = max(rowh, h)
        if fits and y + rowh <= H:
            print(f'  atlas {W}x{H} at {px} px/m, cockpit at {max(px, cpx)}, {len(groups)} charts', flush=True)
            return uvpx, px
    raise SystemExit('model too big for the atlas')


def atlas_size(ac):
    return ac.spec.get('atlas', (ATLAS_W, ATLAS_H))


def texels(P, uvpx, W, H):
    """Which triangle covers each atlas texel and where on it (barycentrics), padded 3 texels."""
    tri = np.full((H, W), -1, np.int32)
    bary = np.zeros((H, W, 3), np.float32)
    for t in range(len(P)):
        q = uvpx[t]
        x0, y0 = np.floor(q.min(0) - 1).astype(int)
        x1, y1 = np.ceil(q.max(0) + 1).astype(int)
        x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, W), min(y1, H)
        if x1 <= x0 or y1 <= y0:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        a, b, c = q
        d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(d) < 1e-9:
            continue
        l0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / d
        l1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / d
        l2 = 1 - l0 - l1
        ins = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
        yy, xx = np.nonzero(ins)
        tri[yy + y0, xx + x0] = t
        bary[yy + y0, xx + x0] = np.stack([l0[ins], l1[ins], l2[ins]], -1)
    for _ in range(3):
        empty = tri < 0
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            src, sb = np.roll(tri, (dy, dx), (0, 1)), np.roll(bary, (dy, dx), (0, 1))
            m = empty & (src >= 0) & (tri < 0)
            tri[m] = src[m]
            bary[m] = sb[m]
    return tri, bary


def load_texture(ac, i, tex):
    """A source texture with its own serials painted out (spec scrub)."""
    img = np.asarray(Image.open(tex).convert('RGB'), float).copy()
    rng = np.random.default_rng(i)
    for x0, y0, x1, y1 in ac.spec.get('scrub', {}).get(ac.mnames[i], ()):
        ring = np.concatenate([img[y0 - 3:y0, x0:x1].reshape(-1, 3), img[y1:y1 + 3, x0:x1].reshape(-1, 3),
                               img[y0:y1, x0 - 3:x0].reshape(-1, 3), img[y0:y1, x1:x1 + 3].reshape(-1, 3)])
        img[y0:y1, x0:x1] = np.median(ring, 0) * (1 + (rng.random((y1 - y0, x1 - x0, 1)) - 0.5) * 0.06)
    ao = ac.spec.get('ao', {}).get(ac.mnames[i])
    if ao:
        img *= np.asarray(Image.open(find_texture(ac.base, ao)).convert('L'), float)[..., None] / 255
    return img


def source_colours(ac, t, w, p=None, n=None):
    """The bought model's own colour at each texel (its texture where it has one, else its Kd)
    and the material it came from. For a reduced model each texel is first matched to the
    closest spot on the original."""
    rt = t
    if getattr(ac, 'source', None) is not None:
        src = ac.source
        with tempfile.TemporaryDirectory() as tmp:
            f = lambda k: os.path.join(tmp, k)
            src['P'].astype('<f4').tofile(f('t.f32'))
            (src['mat'] * 8 + np.searchsorted(['body', 'glass', 'interior'], src['cls'].astype(str))).astype('<i4').tofile(f('g.i32'))
            g = ac.mat[t] * 8 + np.searchsorted(['body', 'glass', 'interior'], ac.cls[t].astype(str))
            np.concatenate([p, n, g[:, None]], 1).astype('<f4').tofile(f('q.f32'))
            java('nearest', f('t.f32'), f('g.i32'), f('q.f32'), f('o.f32'))
            raw = np.fromfile(f('o.f32'), '<f4').reshape(-1, 4)
        hit = raw[:, 0].view('<i4').copy()
        found = hit >= 0
        t = np.where(found, hit, 0)
        w = raw[:, 1:].astype(float)
        mat, UV = np.where(found, src['mat'][t], -1), src['UV']
    else:
        found = np.ones(len(t), bool)
        mat, UV = ac.mat[t], ac.UV
    out = np.zeros((len(t), 3))
    for i, mm in enumerate(ac.mats):
        sel = (mat == i) & found
        if not sel.any():
            continue
        tex = find_texture(ac.base, mm['map']) if mm['map'] else None
        uv = (UV[t[sel]] * w[sel, :, None]).sum(1)
        if tex:
            img = load_texture(ac, i, tex)
            h, wd = img.shape[:2]
            good = ~np.isnan(uv).any(1)
            uv = np.nan_to_num(uv)
            sx = np.clip(((uv[:, 0] % 1.0) * wd).astype(int), 0, wd - 1)
            sy = np.clip(((1 - uv[:, 1] % 1.0) * h).astype(int), 0, h - 1)
            out[sel] = np.where(good[:, None], img[sy, sx], np.median(img.reshape(-1, 3), 0))
        else:
            out[sel] = np.array(mm['Kd']) * 255
    # texels the original had nothing near: the reduced triangle's own material colour
    for i in np.unique(ac.mat[rt[~found]]):
        out[~found & (ac.mat[rt] == i)] = np.array(ac.mats[i]['Kd']) * 255
    mat = np.where(mat >= 0, mat, ac.mat[rt])
    return out, np.array(ac.mnames, object)[mat]


# ---------------------------------------------------------------- paint

def font(size, bold=True):
    name = 'DejaVuSansCondensed-Bold.ttf' if bold else 'DejaVuSans.ttf'
    try:
        return ImageFont.truetype(os.path.join('/usr/share/fonts/truetype/dejavu', name), size)
    except OSError:
        return ImageFont.load_default()


def text_mask(text, h_px):
    f = font(int(h_px * 1.35))
    l, t, r, b = f.getbbox(text)
    im = Image.new('L', (r - l + 4, b - t + 4), 0)
    ImageDraw.Draw(im).text((2 - l, 2 - t), text, fill=255, font=f)
    return np.asarray(im, float) / 255


def insignia(a, b, r):
    """The made-up low-visibility national mark the carrier's jets share: ring, bar, diamond."""
    d = np.sqrt(a * a + b * b)
    ring = (d < r) & (d > r * 0.78)
    bar = (np.abs(b) < r * 0.16) & (np.abs(a) < r * 1.55) & (d > r * 0.78)
    diamond = (np.abs(a) + np.abs(b)) < r * 0.55
    return ring | bar | diamond


def text_on_side(p, n, text, centre_z, top_y, height, sel):
    """Coverage of text painted on both sides of the airframe, reading nose-to-tail on each."""
    mask = text_mask(text, 48)
    mh, mw = mask.shape
    width = height * mw / mh
    left = n[:, 0] > 0.35   # +x is the pilot's left: seen from there, the nose is on your left
    fu = np.where(left, (centre_z + width / 2 - p[:, 2]) / width, (p[:, 2] - (centre_z - width / 2)) / width)
    fv = (top_y - p[:, 1]) / height
    ok = (fu >= 0) & (fu < 1) & (fv >= 0) & (fv < 1) & sel & (np.abs(n[:, 0]) > 0.35)
    iu = np.clip((fu * mw).astype(int), 0, mw - 1)
    iv = np.clip((fv * mh).astype(int), 0, mh - 1)
    return np.where(ok, mask[iv, iu], 0.0)


def mark(col, cover, ink, strength=0.8):
    k = np.clip(cover, 0, 1)[:, None] * strength
    return col * (1 - k) + np.asarray(ink, float) * k


def markings(ac, p, n, col):
    s = ac.spec.get('marks', {})
    body = s.get('body_sel', lambda p, n: np.ones(len(p), bool))(p, n)
    ink = np.asarray(s.get('ink', (70, 74, 80)), float)
    for (ix, iz, r) in s.get('wing_insignia', ()):
        up, down = n[:, 1] > 0.5, n[:, 1] < -0.5
        m = insignia(p[:, 0] - ix, p[:, 2] - iz, r) & up & (p[:, 1] > s.get('wing_y', 0))
        m |= insignia(-p[:, 0] - ix, p[:, 2] - iz, r) & down & (p[:, 1] > s.get('wing_y', 0) - 0.4)
        col = mark(col, m, ink)
    for (iz, iy, r) in s.get('side_insignia', ()):
        side = (np.abs(n[:, 0]) > 0.6) & body & (np.abs(p[:, 0]) < s.get('side_x', 9))
        col = mark(col, insignia(p[:, 2] - iz, p[:, 1] - iy, r) & side, ink)
    for text, cz, ty, h, where in s.get('texts', ()):
        sel = where(p, n) & body
        col = mark(col, text_on_side(p, n, text, cz, ty, h, sel) > 0.45, ink, 0.85)
    for (cz, cy, ln, colour) in s.get('strips', ()):
        strip = (np.abs(p[:, 2] - cz) < ln / 2) & (np.abs(p[:, 1] - cy) < 0.035) & (np.abs(n[:, 0]) > 0.5) & body
        col[strip] = colour
    return col


def weather(p, n, col, cls, state, rng, soot_at):
    """The state's grime, streaks, fading and damage, painted from each texel's 3D spot."""
    up = n[:, 1] > 0.35
    inside = cls == 'interior'
    seed = 11
    if state == 'burnt':
        char = np.array([34, 31, 29], float)
        ash = np.array([118, 114, 108], float)
        rustc = np.array([92, 60, 40], float)
        a = fbm(p * 1.6, seed + 30, 4)
        b = fbm(p * 5.0, seed + 32, 3)
        c = char * (1 + (b[:, None] - 0.5) * 0.6)
        c = np.where((a > 0.66)[:, None], c * (1 - 0.55 * sstep(0.66, 0.8, a)[:, None]) + ash * 0.55 * sstep(0.66, 0.8, a)[:, None], c)
        c = np.where((a < 0.32)[:, None], c * (1 - 0.8 * sstep(0.32, 0.2, a)[:, None]) + rustc * 0.8 * sstep(0.32, 0.2, a)[:, None], c)
        c[(b > 0.76) & (a > 0.55)] = (78, 76, 72)
        return c
    # under the canopy only a little dust settles: no rain streaks, the panels left legible
    amt = np.where(inside, 0.3, 1.0) * {'usable': 0.25, 'abandoned': 0.8, 'wrecked': 0.95}[state]
    g = fbm(p * np.array([1.2, 2.5, 1.2]), seed + 10, 4)
    col = col * (1 - (sstep(0.45, 0.8, g) * 0.16 * amt)[:, None])
    side = np.abs(n[:, 1]) < 0.6
    st = np.where(side, fbm(p * np.array([9, 0.4, 9]), seed + 12, 4), fbm(p * np.array([9, 9, 0.35]), seed + 12, 4))
    col = col * (1 - (sstep(0.55, 0.85, st) * 0.18 * amt * ~inside)[:, None])
    col = np.where(up[:, None], col * (1 - 0.06 * amt[:, None]) + np.array([12, 12, 10]) * amt[:, None], col)
    # exhaust soot behind the engines
    for (sx, sy, sz, r) in soot_at:
        d = np.sqrt((p[:, 0] - sx) ** 2 + (p[:, 1] - sy) ** 2 + np.minimum(0, p[:, 2] - sz) ** 2 * 0.15)
        hot = np.exp(-np.maximum(0, d - r) / 0.9) * (p[:, 2] < sz + 1.5)
        col = col * (1 - (hot * 0.35 * (0.5 + amt))[:, None])
    if state in ('abandoned', 'wrecked'):
        spot = (rng.random(len(p)) < 0.0035 * amt) & up & ~inside
        col[spot] = (214, 212, 204)
        leak = fbm(p * np.array([14, 0.6, 14]), seed + 18, 3)
        col[(leak > 0.72) & (n[:, 1] < 0.2) & (p[:, 1] < 2.2) & ~inside] *= 0.7
    if state == 'wrecked':
        burn = sstep(0.58, 0.78, fbm(p * 0.6, seed + 20, 4))
        col = col * (1 - burn[:, None] * 0.75)
        holes = voronoi_edge(p * 9.0, seed + 26)
        pick = (fbm(p * 0.9, seed + 22, 3) > 0.7) & ~inside
        col[(holes > 0.55) & pick] = (16, 15, 14)
        col[(holes > 0.45) & (holes <= 0.55) & pick] *= 1.25
        bare = (fbm(p * 2.2, seed + 24, 3) > 0.78) & ~inside
        col[bare] = np.array([150, 152, 150]) * (1 + (rng.random(bare.sum()) - 0.5)[:, None] * 0.1)
    return col


def surface(ac, tri, bary):
    """Each covered texel's spot on the parked airframe, its normal, class and source colour."""
    ok = tri >= 0
    t, w = tri[ok], bary[ok]
    p = (ac.P[t] * w[:, :, None]).sum(1)
    n = np.cross(ac.P[t, 1] - ac.P[t, 0], ac.P[t, 2] - ac.P[t, 0])
    n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-12)
    cls = ac.cls[t].astype(str)
    col, mname = source_colours(ac, t, w, p, n)
    return dict(ok=ok, p=p, n=n, cls=cls, base=ac.spec['tone'](col, p, n, cls, mname))


def paint(ac, state, s, rng):
    """The atlas for a state, weathered where each texel sits on the parked airframe."""
    p, n, cls = s['p'], s['n'], s['cls']
    col = markings(ac, p, n, s['base'].copy()) if state != 'burnt' else s['base'].copy()
    col = weather(p, n, col, cls, state, rng, ac.spec.get('soot', ()))
    glass = cls == 'glass'
    gc = np.asarray(ac.spec.get('glass_colour', (96, 104, 104)), float)
    col[glass] = gc * (1 + (rng.random((glass.sum(), 1)) - 0.5) * 0.06) * (0.75 if state == 'abandoned' else 1.0)
    if state == 'wrecked':
        crack = voronoi_edge(p[glass] * 7.0, 51) < 0.05
        col[np.where(glass)[0][crack]] = (210, 214, 210)
    img = np.full(s['ok'].shape + (3,), 120.0)
    img[s['ok']] = col
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


# ---------------------------------------------------------------- packing

def floors(P, lo, nx, nz):
    """The underside of the airframe over each CELL column: the lowest surface above the deck, so
    hull blocks leave room to walk under the wings (0 where it reaches the deck)."""
    grid = np.full((nz, nx), np.inf)
    for a in np.linspace(0, 1, 9):
        for b in np.linspace(0, 1 - a, max(2, int(9 * (1 - a)))):
            q = P[:, 0] * (1 - a - b) + P[:, 1] * a + P[:, 2] * b
            ix = np.clip(((q[:, 0] - lo[0]) / CELL).astype(int), 0, nx - 1)
            iz = np.clip(((q[:, 2] - lo[2]) / CELL).astype(int), 0, nz - 1)
            np.minimum.at(grid, (iz, ix), q[:, 1])
    grid[~np.isfinite(grid)] = 0
    return grid


def build(spec, src, out_dir=None):
    SRC_CACHE[0] = os.path.join(src, '.cache')
    ac = Aircraft(spec, src)
    print(f'{spec["id"]}: {len(ac.P)} triangles', flush=True)
    if spec.get('reduce_to'):
        # hidden insides go first, then the rest is cut down and looked at again for its sides
        orient(ac, True, twins=False)
        reduce(ac, spec['reduce_to'])
        orient(ac, True)
    else:
        orient(ac, spec.get('drop_hidden', False))
    uvpx, px = make_atlas(ac)
    W, H = atlas_size(ac)
    tri, bary = texels(ac.P, uvpx, W, H)
    surf = surface(ac, tri, bary)
    UV = uvpx / np.array([W, H])
    variants = []
    for state in VARIANTS:
        rng = np.random.default_rng(java_hash(spec['id'] + state) & 0xFFFFF)
        P, keep = state_geometry(ac, state, rng)
        img = paint(ac, state, surf, rng)
        glass = (ac.cls == 'glass')[keep]
        variants.append(dict(state=state, T=P[keep], UV=UV[keep], glass=glass, img=img))
        print(f'  {state}: {keep.sum()} triangles', flush=True)
    base = variants[0]['T']
    lo, hi = base.reshape(-1, 3).min(0), base.reshape(-1, 3).max(0)
    centre = np.array([(lo[0] + hi[0]) / 2, 0, (lo[2] + hi[2]) / 2])
    buf = io.BytesIO()
    buf.write(b'ZCAR')
    buf.write(struct.pack('>ii', 3, len(variants)))
    for v in variants:
        jpg = io.BytesIO()
        quality, subsampling = spec.get('jpeg', (82, 2))
        v['img'].save(jpg, 'JPEG', quality=quality, subsampling=subsampling)
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
        buf.write(struct.pack('>i', 0))  # wheels: baked into the body, these are never driven
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
            v['img'].save(os.path.join(out_dir, f'{spec["id"]}_{v["state"]}.png'))
    blob = scramble(zlib.compress(buf.getvalue(), 9), spec['id'])
    with open(os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/cars', spec['id'] + '.zcar'), 'wb') as f:
        f.write(blob)
    glo, nx, nz, grid = heights(base - centre)
    floor = floors(base - centre, glo, nx, nz)
    size = hi - lo
    print(f'{spec["id"]:20s} {len(blob) // 1024:5d} KB  {size[0]:.1f} x {size[1]:.1f} x {size[2]:.1f} m', flush=True)
    return {'id': spec['id'], 'kind': 'jet', 'weight': 1, 'size': [round(float(s), 3) for s in size],
            'cell': CELL, 'x0': round(float(glo[0]), 3), 'z0': round(float(glo[2]), 3), 'nx': nx, 'nz': nz,
            'heights': [int(round(h * 100)) for h in grid.ravel()],
            'floors': [int(round(f * 100)) for f in floor.ravel()],
            'seats': [[round(float(a - b), 3) for a, b in zip(e, centre)] for e in spec['eyes']],
            'wheelbase': round(float(spec['wheelbase']), 3)}


# ---------------------------------------------------------------- the three aircraft

def tone_f22(c, p, n, cls, mat):
    """The F-22 texture is a pale grey atlas with its edge tape in white: brought down to the
    two-tone greys of the real paint, the light edges kept as a slightly lighter grey."""
    lum = c @ np.array([0.3, 0.59, 0.11]) / 255
    dark, light = np.array([92, 97, 103], float), np.array([150, 155, 160], float)
    out = dark + (light - dark) * np.clip((lum - 0.25) / 0.7, 0, 1)[:, None] ** 1.1
    inside = cls == 'interior'
    out[inside] = c[inside]
    return out


SU27_SKIN = ('Aluminum', 'Metal_Aluminum_Anodized', '_Metal_Aluminum_Anodized_1', '_Metal_Aluminum_Anodized_2',
             'S_46db8329_ee31_4635_9f82_4f33d504ac6c')
# its engine ends wear stone and tile stand-ins: bare metal, darker toward the nozzles
SU27_METAL = {'Metal_Embossed': (112, 112, 110), 'PENKOU_46db8329_ee31_4635_9f82_4f33d504ac6c': (84, 86, 88),
              'Metal_Steel_Textured_White': (70, 68, 66), 'Color_B05': (58, 52, 48), 'Color_A08': (124, 36, 32)}


def tone_su27(c, p, n, cls, mat):
    """The bought Su-27 is mostly painted with plain cyan stand-in materials, so its skin is
    repainted here: the three-tone blue-grey splinter of the real ones, a paler belly, panel lines
    at the fuselage stations, an off-white radome. Gear bays keep their green primer, toned down."""
    out = np.where((cls == 'interior')[:, None], c, c * 0.94)
    skin = np.isin(mat, SU27_SKIN) & (cls == 'body')
    light, mid, dark = np.array([152, 166, 180.0]), np.array([112, 136, 164.0]), np.array([118, 128, 140.0])
    belly = np.array([168, 178, 188.0])
    f = fbm(p * np.array([0.2, 0.32, 0.15]), 71, 3)
    g = fbm(p * np.array([0.26, 0.3, 0.2]) + 9.3, 73, 3)
    camo = np.where((f > 0.52)[:, None], mid, np.where((g > 0.56)[:, None], dark, light))
    camo = np.where((n[:, 1] < -0.4)[:, None], belly, camo)
    grain = fbm(p * 5.0, 75, 2)[:, None] - 0.5
    camo = camo * (1 + grain * 0.05)
    stations = np.abs(((p[:, 2] + 0.35) % 1.15) - 0.575) > 0.565
    spars = (np.abs(p[:, 0]) > 2.6) & (np.abs(((np.abs(p[:, 0]) - 2.6) % 1.3) - 0.65) > 0.64) & (np.abs(n[:, 1]) > 0.6)
    camo[stations | spars] *= 0.84
    out[skin] = camo[skin]
    radome = (p[:, 2] > 8.45) & (cls == 'body')
    out[radome] = np.array([196, 199, 196.0]) * (1 + grain[radome] * 0.06)
    bays = mat == '_Metal_Rusted_1'
    out[bays] = out[bays] * 0.45 + np.array([88, 104, 94.0]) * 0.55
    heat = np.clip((-p[:, 2] - 5.0) / 4.0, 0, 1)[:, None]
    for name, col in SU27_METAL.items():
        m = mat == name
        tone = np.array(col, float) * (1 + grain[m] * 0.08)
        if name in ('Metal_Embossed', 'PENKOU_46db8329_ee31_4635_9f82_4f33d504ac6c'):
            # bare titanium, straw and blue where it runs hot
            tone = tone * (1 - heat[m] * 0.25) + heat[m] * np.array([10, 6, 14.0])
        out[m] = tone
    return out


SPECS = {
    'corvid': dict(
        id='jet_corvid', obj='f22/F22-RAPTOR.obj', scale=4.1,
        mtl={'F22_Base': {'Kd': (0.6, 0.6, 0.6), 'd': 1.0, 'map': 'tex/F22_base.png'},
             'F22_Glass': {'Kd': (0.58, 0.33, 0.0), 'd': 0.24, 'map': None}},
        ao={'F22_Base': 'tex/F22_AO.png'},
        cockpit=('Cockpit',), tub=[(-0.62, 1.9, 3.7, 0.62, 3.45, 6.8)], cockpit_px=160, atlas=(2048, 2048),
        lost_when_wrecked=('Chassis01', 'Chassis02', 'Chassis03', 'Elevator_L'), sag=(1, -2.1),
        # the design eye point: looking through the HUD combiner, a little down onto it, so the HUD fills
        # the middle of the view (Josiah 2026-10-26); the glow regions keep the old eyes (cockpit_glow GLOW_EYES)
        wheelbase=5.95, eyes=[(0.0, 3.12, 5.42)],
        glass_colour=(104, 92, 62), tone=tone_f22,
        soot=[(0.6, 1.9, -7.4, 0.6), (-0.6, 1.9, -7.4, 0.6)],
        marks=dict(
            ink=(66, 70, 76),
            wing_insignia=[(4.0, -2.6, 0.62)], wing_y=1.6,
            side_insignia=[(1.6, 2.2, 0.36)], side_x=2.2,
            texts=[('GH', -6.4, 4.4, 0.62, lambda p, n: (p[:, 1] > 2.6) & (np.abs(p[:, 0]) > 1.4)),
                   ('NAVY', -6.6, 4.85, 0.17, lambda p, n: (p[:, 1] > 2.6) & (np.abs(p[:, 0]) > 1.4)),
                   ('104', 6.6, 2.55, 0.34, lambda p, n: (p[:, 2] > 5.5) & (np.abs(p[:, 0]) < 1.2))],
            strips=[(3.0, 2.1, 0.5, (150, 175, 120))],
        ),
    ),
    'hornbill': dict(
        id='heli_hornbill', obj='apache/Apache AH64_LOW.obj', turn=True,
        mtl={'body_mat': {'Kd': (0.4, 0.4, 0.38), 'd': 1.0, 'map': 'tex/body_BaseColor.png'},
             'gun_mat': {'Kd': (0.3, 0.3, 0.3), 'd': 1.0, 'map': 'tex/guns_BaseColor.png'},
             'wheels_mat': {'Kd': (0.2, 0.2, 0.2), 'd': 1.0, 'map': 'tex/wheels_BaseColor.png'},
             'interior_material': {'Kd': (0.3, 0.3, 0.3), 'd': 1.0, 'map': 'tex/interior_BaseColor.png'}},
        scrub={'body_mat': [(56, 128, 98, 148), (250, 183, 292, 203)]},
        cockpit=('interior.001',), cockpit_px=128, atlas=(2048, 2048),
        # the tail wheel snapped off: the boom sinks onto the deck
        lost_when_wrecked=(lambda ac: ac.objects(('wheels',)) & (ac.P.mean(1)[:, 2] < -6.5),), sag=(-1, 3.0),
        wheelbase=10.75, eyes=[(0.0, 2.98, 3.0), (0.0, 2.6, 4.35)],
        tone=lambda c, p, n, cls, mat: c * 1.08,
        soot=[(1.1, 2.7, -1.8, 0.4), (-1.1, 2.7, -1.8, 0.4)],
        marks=dict(
            ink=(32, 33, 30),
            side_insignia=[(-4.6, 1.85, 0.3)], side_x=0.7,
            texts=[('305', -7.9, 3.9, 0.32, lambda p, n: p[:, 2] < -7.0),
                   ('NAVY', -5.7, 2.06, 0.2, lambda p, n: (p[:, 2] < -5.0) & (p[:, 2] > -7.0))],
        ),
    ),
    'gyrfalcon': dict(
        id='jet_gyrfalcon', obj='su27/obj/OBJ FILE.obj', drop=('S_270', 'Metal_Rusted'), reduce_to=20000,
        cockpit=('Group249',), cockpit_px=128, atlas=(2048, 2048),
        lost_when_wrecked=(lambda ac: ac.objects(('G_1_1',)) & (ac.P.mean(1)[:, 2] > 0),
                           lambda ac: (ac.P.mean(1)[:, 0] > 3.2) & (ac.P.mean(1)[:, 2] < -5.8) & (np.abs(ac.P.mean(1)[:, 1] - 2.25) < 0.45)),
        sag=(1, -3.0), wheelbase=5.5, eyes=[(0.0, 3.54, 5.322)],
        tone=tone_su27, glass_colour=(110, 122, 124),
        soot=[(1.1, 1.65, -9.0, 0.6), (-1.1, 1.65, -9.0, 0.6)],
        marks=dict(
            ink=(52, 64, 84),
            wing_insignia=[(5.0, -4.1, 0.62)], wing_y=2.2,
            side_insignia=[(-6.6, 4.3, 0.3)], side_x=2.4,
            texts=[('GH', -6.6, 5.15, 0.5, lambda p, n: (p[:, 1] > 3.9) & (np.abs(p[:, 0]) > 1.8)),
                   ('NAVY', -6.6, 5.52, 0.14, lambda p, n: (p[:, 1] > 3.9) & (np.abs(p[:, 0]) > 1.8)),
                   ('211', 7.45, 2.9, 0.52, lambda p, n: (p[:, 2] > 6.8) & (p[:, 2] < 8.4) & (np.abs(p[:, 0]) < 1.0))],
        ),
    ),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src', help='the unpacked jets.zip (kept out of the repository)')
    ap.add_argument('--only', default=None)
    ap.add_argument('--preview', default=None, help='also write each state\'s atlas here')
    a = ap.parse_args()
    models = []
    for key, spec in SPECS.items():
        if a.only and key != a.only:
            continue
        models.append(build(spec, a.src, a.preview))
    idx = os.path.join(ROOT, 'src/main/resources/data/zomboidcraft/zc_cars/models.json')
    data = json.load(open(idx))
    ids = {m['id'] for m in models}
    data['models'] = [m for m in data['models'] if m['id'] not in ids] + models
    data['models'].sort(key=lambda m: m['id'])
    with open(idx, 'w') as f:
        json.dump(data, f, separators=(',', ':'))
    print(len(models), 'aircraft')


if __name__ == '__main__':
    main()
