"""Turns a clean low-poly car (.glb) into weathered, wrecked variants for ZomboidCraft.

Each car gets its own texture atlas (the source models share a flat colour palette, so there is
nowhere to paint rust). The atlas is made by grouping each part's triangles into flat charts and
packing them; every texel then knows its 3D position, normal and source colour, and the
weathering (muted paint, dirt, rust, streaks, dust, moss, blood, cracked glass, scorch) is painted
from those in 3D so it flows across chart seams.

Geometry damage uses the separate parts the models ship with: doors swing open or go missing,
glass panes get knocked out, tyres go flat or come off (and the body sags onto that corner), the
nose crumples, and a burnt shell sits low on its rims.

    python3 tools/cars/wreck.py MODEL.glb OUT_DIR --variants abandoned,wrecked,burnt --seed 3
writes OUT_DIR/<name>_<variant>.json (positions, uvs, normals) and .png (+ _s.png, _n.png LabPBR).
"""
import argparse, json, os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glb import load

PX_PER_M = 64  # 4x Minecraft's 16 px per block, same density as the HD zombie skins


# ---------------------------------------------------------------- parts

def classify(name):
    n = name.lower()
    if 'wheel' in n or 'tire' in n:
        return 'wheel'
    if 'win' in n or n.startswith('wn '):
        return 'glass'
    if 'door' in n:
        return 'door'
    if 'interior' in n or 'interier' in n or 'inerior' in n:
        return 'interior'
    return 'body'


def bbox(P):
    Q = P.reshape(-1, 3)
    return Q.min(0), Q.max(0)


def components(P):
    """Connected pieces of a triangle soup (vertices matched by position)."""
    key = np.round(P.reshape(-1, 3) * 2000).astype(np.int64)
    _, vid = np.unique(key, axis=0, return_inverse=True)
    vid = vid.reshape(-1, 3)
    par = np.arange(vid.max() + 1)

    def find(a):
        while par[a] != a:
            par[a] = par[par[a]]
            a = par[a]
        return a
    for t in vid:
        r0 = find(t[0])
        for v in t[1:]:
            r = find(v)
            if r != r0:
                par[r] = r0
    roots = np.array([find(t[0]) for t in vid])
    _, comp = np.unique(roots, return_inverse=True)
    return comp


class Car:
    def __init__(self, path):
        parts, self.src = load(path)
        self.name = os.path.basename(path)[:-4]
        self.src = np.asarray(self.src.convert('RGB')).astype(np.float32) / 255
        tris, uvs, pid, cls = [], [], [], []
        self.parts = []
        for i, p in enumerate(parts):
            c = classify(p['name'])
            self.parts.append({'name': p['name'], 'cls': c})
            tris.append(p['P']); uvs.append(p['UV'])
            pid.append(np.full(len(p['P']), i)); cls.append([c] * len(p['P']))
        self.P = np.concatenate(tris)
        self.UV = np.concatenate(uvs)
        self.pid = np.concatenate(pid)
        self.cls = np.array(sum(cls, []))
        lo, hi = bbox(self.P)
        self.P[:, :, 1] -= lo[1]
        self.lo, self.hi = bbox(self.P)
        # glass panes: split every glass part into its separate pieces
        self.pane = np.full(len(self.P), -1)
        n = 0
        for i, p in enumerate(self.parts):
            if p['cls'] != 'glass':
                continue
            m = np.where(self.pid == i)[0]
            comp = components(self.P[m])
            self.pane[m] = comp + n
            n += comp.max() + 1
        self.npanes = n
        self._doors()
        self._wheels()

    def _doors(self):
        """Hinges for each door; door windows ride along with their door."""
        self.door_of = {}  # part index -> door part index
        doors = [i for i, p in enumerate(self.parts) if p['cls'] == 'door']
        for i in doors:
            lo, hi = bbox(self.P[self.pid == i])
            s = hi - lo
            p = self.parts[i]
            p['lo'], p['hi'] = lo, hi
            if s[2] >= s[0]:
                p['kind'] = 'side'
            elif s[1] < 0.5 * s[0]:
                p['kind'] = 'tailgate'
            else:
                p['kind'] = 'rear'
            self.door_of[i] = i
        for i, p in enumerate(self.parts):
            if p['cls'] != 'glass':
                continue
            lo, hi = bbox(self.P[self.pid == i])
            c = (lo + hi) / 2
            for d in doors:
                dl, dh = self.parts[d]['lo'] - 0.06, self.parts[d]['hi'] + 0.06
                if dl[0] <= c[0] <= dh[0] and dl[2] <= c[2] <= dh[2] and lo[1] >= dl[1] - 0.05 and (hi - lo)[0] < 0.5:
                    self.door_of[i] = d
                    break

    def _wheels(self):
        self.wheels = []
        for i, p in enumerate(self.parts):
            if p['cls'] != 'wheel':
                continue
            lo, hi = bbox(self.P[self.pid == i])
            r = (hi[1] - lo[1]) / 2
            if lo[1] > 0.15:  # spare wheel on a tailgate
                p['cls'] = 'body'
                self.cls[self.pid == i] = 'wheel'
                continue
            p['centre'] = (lo + hi) / 2
            p['radius'] = r
            self.wheels.append(i)


# ---------------------------------------------------------------- atlas

def build_atlas(car, px_per_m=PX_PER_M, width=1024):
    """Flat charts per part, shelf-packed. Returns per-triangle uv in pixels and atlas size."""
    P = car.P
    nrm = np.cross(P[:, 1] - P[:, 0], P[:, 2] - P[:, 0])
    area = np.linalg.norm(nrm, axis=1)
    nrm = nrm / np.maximum(area, 1e-12)[:, None]
    car.nrm = nrm
    key = np.round(P.reshape(-1, 3) * 2000).astype(np.int64)
    _, vid = np.unique(key, axis=0, return_inverse=True)
    vid = vid.reshape(-1, 3)
    edges = {}
    for t, (a, b, c) in enumerate(vid):
        for e in ((a, b), (b, c), (c, a)):
            edges.setdefault((min(e), max(e)), []).append(t)
    nb = [[] for _ in range(len(P))]
    for ts in edges.values():
        for a in ts:
            for b in ts:
                if a != b:
                    nb[a].append(b)
    chart = np.full(len(P), -1)
    charts = []
    order = np.argsort(-area)
    for s in order:
        if chart[s] >= 0:
            continue
        cid = len(charts)
        n0 = nrm[s]
        stack = [s]; chart[s] = cid; members = []
        while stack:
            t = stack.pop(); members.append(t)
            for u in nb[t]:
                if chart[u] < 0 and car.pid[u] == car.pid[s] and nrm[u] @ n0 > 0.88:
                    chart[u] = cid; stack.append(u)
        charts.append(members)
    uvpx = np.zeros((len(P), 3, 2))
    rects = []
    for cid, m in enumerate(charts):
        n0 = (nrm[m] * area[m, None]).sum(0)
        n0 = n0 / np.linalg.norm(n0) if np.linalg.norm(n0) > 1e-9 else np.array([0.0, 1.0, 0.0])
        up = np.array([0, 1, 0]) if abs(n0[1]) < 0.9 else np.array([1, 0, 0])
        t = np.cross(up, n0); t /= np.linalg.norm(t)
        b = np.cross(n0, t)
        q = np.stack([P[m] @ t, -(P[m] @ b)], -1) * px_per_m
        q -= q.reshape(-1, 2).min(0)
        uvpx[m] = q
        w, h = q.reshape(-1, 2).max(0)
        rects.append((cid, int(np.ceil(w)) + 4, int(np.ceil(h)) + 4))
    rects.sort(key=lambda r: -r[2])
    x = y = rowh = 0
    off = {}
    for cid, w, h in rects:
        if x + w > width:
            x = 0; y += rowh; rowh = 0
        off[cid] = (x + 2, y + 2)
        x += w; rowh = max(rowh, h)
    height = y + rowh
    H = 1 << int(np.ceil(np.log2(max(height, 16))))
    for cid, m in enumerate(charts):
        uvpx[m] += np.array(off[cid])
    car.uvpx, car.atlas = uvpx, (width, H)
    return uvpx, (width, H)


def rasterize(car):
    """Per-texel attributes: source position, normal, source colour, class id, pane, part."""
    W, H = car.atlas
    tri = np.full((H, W), -1, np.int32)
    bary = np.zeros((H, W, 3), np.float32)
    for t in range(len(car.P)):
        q = car.uvpx[t]
        x0, y0 = np.floor(q.min(0) - 1).astype(int)
        x1, y1 = np.ceil(q.max(0) + 1).astype(int)
        x0, y0 = max(x0, 0), max(y0, 0)
        x1, y1 = min(x1, W), min(y1, H)
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
        if not ins.any():
            continue
        yy, xx = np.nonzero(ins)
        tri[yy + y0, xx + x0] = t
        bary[yy + y0, xx + x0] = np.stack([l0[ins], l1[ins], l2[ins]], -1)
    # pad: grow coverage 3 texels so filtering never reads empty space
    for _ in range(3):
        empty = tri < 0
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            src = np.roll(tri, (dy, dx), (0, 1)); sb = np.roll(bary, (dy, dx), (0, 1))
            m = empty & (src >= 0) & (tri < 0)
            tri[m] = src[m]; bary[m] = sb[m]
    ok = tri >= 0
    t = tri[ok]; w = bary[ok]
    pos = (car.P[t] * w[:, :, None]).sum(1)
    uv = (car.UV[t] * w[:, :, None]).sum(1)
    sh, sw = car.src.shape[:2]
    sx = np.clip((uv[:, 0] % 1.0) * sw, 0, sw - 1).astype(int)
    sy = np.clip((uv[:, 1] % 1.0) * sh, 0, sh - 1).astype(int)
    car.tex = {'ok': ok, 'tri': t, 'pos': pos, 'nrm': car.nrm[t], 'base': car.src[sy, sx],
               'cls': car.cls[t], 'pane': car.pane[t], 'pid': car.pid[t]}


# ---------------------------------------------------------------- noise

def _hash(ix, iy, iz, seed):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(p, seed=0):
    f = np.floor(p).astype(np.int64)
    r = p - f
    r = r * r * (3 - 2 * r)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (r[:, 0] if dx else 1 - r[:, 0]) * (r[:, 1] if dy else 1 - r[:, 1]) * (r[:, 2] if dz else 1 - r[:, 2])
                out = out + w * _hash(f[:, 0] + dx, f[:, 1] + dy, f[:, 2] + dz, seed)
    return out


def fbm(p, seed=0, octaves=4):
    s, a, tot = 0, 1.0, 0
    for o in range(octaves):
        s = s + a * vnoise(p * (2 ** o), seed + o * 17)
        tot += a; a *= 0.5
    return s / tot


def voronoi_edge(p, seed=0):
    """Distance to the nearest cell border (F2 - F1), for shattered glass."""
    f = np.floor(p).astype(np.int64)
    d1 = np.full(len(p), 9.0); d2 = np.full(len(p), 9.0)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                c = f + np.array([dx, dy, dz])
                fp = np.stack([_hash(c[:, 0], c[:, 1], c[:, 2], seed + k) for k in range(3)], -1)
                d = np.linalg.norm(c + fp - p, axis=1)
                d2 = np.where(d < d1, d1, np.minimum(d2, d)); d1 = np.minimum(d1, d)
    return d2 - d1


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t)[..., None] if np.ndim(t) else t
    return a * (1 - t) + b * t


# ---------------------------------------------------------------- variants

VARIANTS = {
    # rust, dirt, fade, moss, blood chance, doors open, doors missing, panes broken, flat tyres, missing wheels, crash, burnt
    'parked':    dict(rust=0.12, dirt=0.55, fade=0.35, moss=0.1, blood=0.0, open=0.0, missing=0.0, smash=0.1, flat=0.25, gone=0.0, crash=0, burnt=0),
    'abandoned': dict(rust=0.3, dirt=0.75, fade=0.6, moss=0.35, blood=0.35, open=0.35, missing=0.0, smash=0.35, flat=0.4, gone=0.0, crash=0, burnt=0),
    'wrecked':   dict(rust=0.45, dirt=0.9, fade=0.7, moss=0.45, blood=0.7, open=0.4, missing=0.25, smash=0.75, flat=0.5, gone=0.3, crash=1, burnt=0),
    'burnt':     dict(rust=0.6, dirt=0.6, fade=0, moss=0.25, blood=0, open=0.15, missing=0.1, smash=1.0, flat=0, gone=0, crash=0, burnt=1),
}

MUD = np.array([0.20, 0.17, 0.13]); DUST = np.array([0.42, 0.40, 0.36])
RUST_DARK = np.array([0.22, 0.10, 0.05]); RUST_ORANGE = np.array([0.48, 0.24, 0.09])
MOSS = np.array([0.17, 0.22, 0.09]); BLOOD = np.array([0.22, 0.02, 0.02])
CHAR = np.array([0.07, 0.065, 0.06]); ASH = np.array([0.33, 0.32, 0.30])


def paint(car, v, rng, broken_panes, cracked_panes):
    T = car.tex
    p, n, c, cls = T['pos'], T['nrm'], T['base'].copy(), T['cls']
    seed = int(rng.integers(1 << 20))
    paintable = (cls == 'body') | (cls == 'door')
    glass = cls == 'glass'; wheel = cls == 'wheel'; inside = cls == 'interior'
    h = p[:, 1]
    lum = c @ np.array([0.3, 0.59, 0.11])
    up = np.clip(n[:, 1], 0, 1)
    side = 1 - np.abs(n[:, 1])
    # mute every colour first: the source palette is toy-bright
    c = mix(c, lum[:, None].repeat(3, 1), 0.35) * 0.8
    # sun-faded roof, bonnet and boot
    c = np.where(paintable[:, None], mix(c, (lum * 0.85 + 0.1)[:, None].repeat(3, 1), sstep(0.4, 0.9, up) * 0.45 * v['fade']), c)
    # grime streaks running down from windows and seams
    st = fbm(p * np.array([9, 0.7, 9]), seed + 1, 3) * (0.6 + 0.4 * fbm(p * 1.5, seed + 23, 2))
    c = c * (1 - (sstep(0.58, 0.72, st) * 0.22 * side * v['dirt'])[:, None])
    # mud and road dirt from the sills down
    dm = np.clip((0.75 - h) / 0.6, 0, 1) * (0.55 + 0.45 * fbm(p * 5, seed + 2))
    c = mix(c, MUD, np.where(inside, 0, dm * 0.8 * v['dirt']))
    # rust: lower panels, arches and random blooms
    if v['rust'] > 0:
        r = fbm(p * 2.5, seed + 3) * 0.7 + fbm(p * 11, seed + 4) * 0.3 + (0.6 - h).clip(0) * 0.35
        th = 0.78 - 0.32 * v['rust']
        m = sstep(th, th + 0.05, r) * paintable
        ring = sstep(th - 0.07, th, r) * (1 - m) * paintable
        rc = mix(RUST_DARK, RUST_ORANGE, fbm(p * 20, seed + 5))
        rc = rc * (0.75 + 0.5 * vnoise(p * 90, seed + 6))[:, None]
        c = c * (1 - 0.4 * ring)[:, None]
        c = mix(c, rc, m)
    # dust, leaves and grit on the flat tops
    c = mix(c, DUST, sstep(0.5, 0.9, up) * (0.25 + 0.35 * fbm(p * 3, seed + 7)) * v['dirt'] * (paintable | glass))
    sp = vnoise(p * 45, seed + 8)
    leaf = (sp > 0.93) & (up > 0.6) & (paintable | glass)
    c[leaf] = mix(np.array([0.24, 0.16, 0.07]), np.array([0.12, 0.09, 0.05]), vnoise(p[leaf] * 9, seed + 9))
    # moss in the low, shaded places
    if v['moss'] > 0:
        low = (0.55 - h).clip(0) * 1.2  # creeps up from the sills and wheel arches
        mm = sstep(0.68, 0.8, fbm(p * 9, seed + 10) * 0.8 + vnoise(p * 40, seed + 25) * 0.2 + low) * v['moss']
        c = mix(c, MOSS * (0.7 + 0.6 * vnoise(p * 60, seed + 11))[:, None], mm * (paintable | wheel))
    # blood: one or two smears with drips, on a door or the bonnet
    if rng.random() < v['blood']:
        for _ in range(int(rng.integers(1, 3))):
            cand = np.nonzero(paintable & (h > 0.5) & (h < car.hi[1] - 0.15))[0]
            o = p[cand[rng.integers(len(cand))]]
            d = p - o
            drip = np.where(d[:, 1] < 0, np.abs(d[:, 1]) * 0.35, np.abs(d[:, 1]))
            dist = np.sqrt(d[:, 0] ** 2 + d[:, 2] ** 2 + drip ** 2)
            dn = dist + (fbm(p * 18, seed + 12) - 0.5) * 0.25
            dripmask = (d[:, 1] < 0) & (vnoise(p * np.array([40, 1.5, 40]), seed + 13) > 0.72) & (dist < 0.6)
            bm = np.maximum(sstep(0.32, 0.18, dn), dripmask) * (paintable | glass)
            c = mix(c, BLOOD * (0.8 + 0.5 * vnoise(p * 50, seed + 14))[:, None], bm * 0.9)
    # glass: dark, filmed with grime, some panes shattered
    if glass.any():
        g = c * 0.45 + 0.03
        g = mix(g, DUST * 0.7, (0.25 + 0.35 * fbm(p * 4, seed + 15)) * v['dirt'])
        for k in cracked_panes:
            sel = np.nonzero(T['pane'] == k)[0]
            if not len(sel):
                continue
            o = p[sel[rng.integers(len(sel))]]
            q = p[sel] - o
            d = np.linalg.norm(q, axis=1)
            # shatter cells small at the impact and growing outwards, like a spider web
            e = voronoi_edge(q / (0.02 + 0.3 * d)[:, None] * 0.5, seed + 16 + k)
            reach = sstep(0.9, 0.3, d + 0.3 * vnoise(p[sel] * 8, seed + k))
            line = (e < 0.05 + 0.04 * sstep(0.1, 0, d)) & (vnoise(p[sel] * 30, seed + 30 + k) < 0.5 + 0.5 * reach)
            hit = line & (reach > 0.05)
            g[sel[hit]] = mix(g[sel[hit]], np.array([0.58, 0.6, 0.6]), (0.5 + 0.5 * reach[hit]))
            g[sel[d < 0.03]] = np.array([0.7, 0.72, 0.72])
        c = np.where(glass[:, None], g, c)
    c = np.where(inside[:, None], c * 0.55 * (0.7 + 0.4 * fbm(p * 7, seed + 17))[:, None], c)
    c = np.where(wheel[:, None], mix(c, DUST * 0.6, np.clip(0.5 - h, 0, 1) * v['dirt']), c)
    # burnt shell: charcoal and orange heat-rust, paint gone, ash on top
    if v['burnt']:
        b = fbm(p * 3, seed + 18)
        rb = sstep(0.62, 0.8, b + 0.2 * vnoise(p * 25, seed + 19))
        bc = mix(CHAR, mix(RUST_DARK, RUST_ORANGE * 0.8, vnoise(p * 12, seed + 24)), rb * 0.85)
        bc = mix(bc, ASH, sstep(0.5, 0.9, up) * 0.5 * fbm(p * 6, seed + 20))
        bc = bc * (0.8 + 0.4 * vnoise(p * 70, seed + 21))[:, None]
        keep = sstep(0.85, 0.95, fbm(p * 2, seed + 22))  # odd patch of blistered paint survives
        c = np.where((paintable | inside | wheel)[:, None], mix(bc, c * 0.5, keep * paintable), c)
    c = np.clip(c, 0, 1)
    # LabPBR specular: smoothness by material (paint semi-gloss, rust and dirt rough, glass glossy)
    sm = np.where(glass, 0.85, np.where(paintable, 0.45, 0.2))
    if v['rust'] > 0:
        sm = np.where(paintable, sm * (1 - 0.8 * m), sm)
    sm = sm * (1 - 0.5 * dm * v['dirt'])
    if v['burnt']:
        sm = sm * 0.3
    f0 = np.where(glass, 10, np.where(paintable, 12, 6)) / 255.0
    return c, sm, f0


# ---------------------------------------------------------------- geometry damage

def rot_y(Q, pivot, a):
    d = Q - pivot
    ca, sa = np.cos(a), np.sin(a)
    x = d[..., 0] * ca + d[..., 2] * sa
    z = -d[..., 0] * sa + d[..., 2] * ca
    return np.stack([x, d[..., 1], z], -1) + pivot


def rot_x(Q, pivot, a):
    d = Q - pivot
    ca, sa = np.cos(a), np.sin(a)
    y = d[..., 1] * ca - d[..., 2] * sa
    z = d[..., 1] * sa + d[..., 2] * ca
    return np.stack([d[..., 0], y, z], -1) + pivot


def damage(car, v, rng):
    P = car.P.copy()
    keep = np.ones(len(P), bool)
    # doors
    for i, p in enumerate(car.parts):
        if p['cls'] != 'door':
            continue
        sel = np.isin(car.pid, [j for j, d in car.door_of.items() if d == i])
        lo, hi = p['lo'], p['hi']
        r = rng.random()
        if r < v['missing']:
            keep &= ~sel
        elif r < v['missing'] + v['open']:
            if p['kind'] == 'side':
                sgn = 1 if (lo[0] + hi[0]) > 0 else -1
                pivot = np.array([hi[0] if sgn > 0 else lo[0], 0, hi[2]])
                P[sel] = rot_y(P[sel], pivot, -sgn * rng.uniform(0.5, 1.15))
            elif p['kind'] == 'tailgate':
                P[sel] = rot_x(P[sel], np.array([0, lo[1], lo[2]]), rng.uniform(1.3, 1.57))
            else:
                sgn = 1 if (lo[0] + hi[0]) > 0 else -1
                pivot = np.array([hi[0] if sgn > 0 else lo[0], 0, lo[2]])
                P[sel] = rot_y(P[sel], pivot, sgn * rng.uniform(0.8, 1.6))
    # glass
    broken, cracked = set(), set()
    for k in range(car.npanes):
        r = rng.random()
        if v['burnt'] or r < v['smash'] * 0.6:
            broken.add(k)
        elif r < v['smash']:
            cracked.add(k)
    keep &= ~np.isin(car.pane, list(broken))
    # wheels: flat, gone, or rims only when burnt
    drops = []
    for i in car.wheels:
        w = car.parts[i]; cen, rad = w['centre'], w['radius']
        sel = car.pid == i
        r = rng.random()
        if v['burnt']:
            Q = P[sel]; Q[..., 1] = cen[1] + (Q[..., 1] - cen[1]) * 0.7; Q[..., 2] = cen[2] + (Q[..., 2] - cen[2]) * 0.7
            Q[..., 1] -= rad * 0.3; P[sel] = Q; drops.append((cen, rad * 0.3))
        elif r < v['gone']:
            keep &= ~sel; drops.append((cen, rad * 0.62))
        elif r < v['gone'] + v['flat']:
            Q = P[sel]; b = cen[1] - rad
            Q[..., 1] = b + (Q[..., 1] - b) * 0.78
            sq = np.clip(1 - (Q[..., 1] - b) / rad, 0, 1)
            Q[..., 0] = cen[0] + (Q[..., 0] - cen[0]) * (1 + 0.35 * sq)
            P[sel] = Q; drops.append((cen, rad * 0.2))
        else:
            drops.append((cen, 0.0))
    # body settles onto the low corners (a plane fitted through the wheel drops)
    if drops and any(d for _, d in drops):
        A = np.array([[1, c[0], c[2]] for c, _ in drops]); y = np.array([d for _, d in drops])
        coef = np.linalg.lstsq(A, y, rcond=None)[0]
        body = car.cls != 'wheel'
        if v['burnt']:
            body = np.ones(len(P), bool) & (car.cls != 'wheel')
        P[body, :, 1] -= coef[0] + coef[1] * P[body, :, 0] + coef[2] * P[body, :, 2]
    # crumpled nose
    if v['crash']:
        zf = car.hi[2]; depth = rng.uniform(0.35, 0.7); bias = rng.uniform(-1, 1)
        z0 = zf - 0.9
        nz = P[..., 2]
        t = np.clip((nz - z0) / (zf - z0), 0, 1)
        side_w = 1 + 0.6 * bias * np.sign(P[..., 0])
        P[..., 2] = nz - t ** 2 * depth * side_w
        jag = vnoise(P.reshape(-1, 3) * 6, int(rng.integers(1000))).reshape(P.shape[:2]) - 0.5
        P[..., 1] -= t ** 2 * 0.12 * (1 + jag)
        P[..., 0] += t ** 2 * jag * 0.08
        cracked |= {k for k in range(car.npanes) if k not in broken}
    P[..., 1] = np.maximum(P[..., 1], -0.02)
    return P, keep, broken, cracked


# ---------------------------------------------------------------- output

def normal_map(W, H):
    """Flat tangent-space normals with a little noise on rust; LabPBR _n (AO in blue, height in alpha)."""
    n = np.zeros((H, W, 4), np.uint8)
    n[..., 0] = 128; n[..., 1] = 128; n[..., 2] = 255; n[..., 3] = 255
    return n


def make_variant(car, vname, seed, out_dir):
    rng = np.random.default_rng(seed)
    v = VARIANTS[vname]
    P, keep, broken, cracked = damage(car, v, rng)
    c, sm, f0 = paint(car, v, np.random.default_rng(seed + 99), broken, cracked)
    W, H = car.atlas
    img = np.zeros((H, W, 3), np.float32); img[car.tex['ok']] = c
    spec = np.zeros((H, W, 4), np.float32); spec[car.tex['ok']] = np.stack([sm, f0, np.zeros_like(sm), np.zeros_like(sm)], -1)
    stem = f"{car.name.lower()}_{vname}"
    Image.fromarray((img * 255 + 0.5).astype(np.uint8)).save(os.path.join(out_dir, stem + '.png'))
    Image.fromarray((spec * 255 + 0.5).astype(np.uint8), 'RGBA').save(os.path.join(out_dir, stem + '_s.png'))
    Image.fromarray(normal_map(W, H), 'RGBA').save(os.path.join(out_dir, stem + '_n.png'))
    Pk = P[keep]; uv = car.uvpx[keep] / np.array([W, H])
    nr = np.cross(Pk[:, 1] - Pk[:, 0], Pk[:, 2] - Pk[:, 0]); nr /= np.linalg.norm(nr, axis=1, keepdims=True) + 1e-12
    lo, hi = bbox(Pk)
    json.dump({'name': stem, 'texture': stem + '.png', 'size': [round(x, 3) for x in (hi - lo)],
               'pos': np.round(Pk.reshape(-1), 4).tolist(), 'uv': np.round(uv.reshape(-1), 5).tolist(),
               'nrm': np.round(np.repeat(nr, 3, 0).reshape(-1), 3).tolist()},
              open(os.path.join(out_dir, stem + '.json'), 'w'))
    return stem


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('model'); ap.add_argument('out')
    ap.add_argument('--variants', default='parked,abandoned,wrecked,burnt')
    ap.add_argument('--seed', type=int, default=1)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    car = Car(a.model)
    ppm = PX_PER_M
    while build_atlas(car, ppm)[1][1] > 2048:  # buses and trucks: keep the atlas at 1024x2048
        ppm *= 0.85
    rasterize(car)
    for i, vn in enumerate(a.variants.split(',')):
        print(make_variant(car, vn, a.seed * 1000 + i, a.out), car.atlas)


if __name__ == '__main__':
    main()
