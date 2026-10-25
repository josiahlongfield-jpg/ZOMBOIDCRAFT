"""Turns a clean low-poly car (.glb) into weathered, wrecked variants for ZomboidCraft.

Each car gets its own texture atlas (the source models share a flat colour palette, so there is
nowhere to paint rust). The atlas is made by grouping each part's triangles into flat charts and
packing them; every texel then knows its 3D position, normal and source colour, and the
weathering (muted paint, dirt, rust, streaks, dust, moss, blood, cracked glass, scorch) is painted
from those in 3D so it flows across chart seams.

Geometry damage uses the separate parts the models ship with: doors swing open or go missing,
glass panes get knocked out, tyres go flat or come off (and the body sags onto that corner), the
nose crumples, and a burnt shell sits low on its rims. The boot (back doors, tailgate, hatch, or
a lid cut out of the body) is split off with its hinge so the game can swing it open, and the load
space behind it gets a dark lining and some boxes and bags.

    python3 tools/cars/wreck.py MODEL.glb OUT_DIR --variants abandoned,wrecked,burnt --seed 3
writes OUT_DIR/<name>_<variant>.json (positions, uvs, normals) and .png (+ _s.png LabPBR specular)
for previews; tools/cars/pack.py builds the packed files the mod ships.
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
        """Hinges for each door; door windows (and a spare wheel on a back door) ride along with it."""
        self.door_of = {}  # part index -> door part index
        doors = [i for i, p in enumerate(self.parts) if p['cls'] == 'door']
        width = self.hi[0] - self.lo[0]
        for i in doors:
            lo, hi = bbox(self.P[self.pid == i])
            s = hi - lo
            p = self.parts[i]
            p['lo'], p['hi'] = lo, hi
            p['back'] = (lo[2] + hi[2]) / 2 < self.lo[2] + 0.3 * (self.hi[2] - self.lo[2])
            if s[2] >= s[0]:
                p['kind'] = 'side'
            elif s[1] < 0.5 * s[0]:
                p['kind'] = 'tailgate'  # hinged at the bottom, folds down
            elif s[0] > 0.55 * width:
                p['kind'] = 'hatch'  # full width, hinged at the top, lifts
            else:
                p['kind'] = 'rear'  # one of a pair, hinged at the outer edge
            self.door_of[i] = i
        for i, p in enumerate(self.parts):
            if p['cls'] == 'door' or not (p['cls'] == 'glass' or 'wheel' in p['name'].lower()):
                continue
            lo, hi = bbox(self.P[self.pid == i])
            c = (lo + hi) / 2
            for d in doors:
                dp = self.parts[d]
                reach = 0.3 if dp['back'] else 0.06
                dl, dh = dp['lo'] - [0.06, 0.06, reach], dp['hi'] + [0.06, 0.06, reach]
                narrow = (hi - lo)[0] < 0.5 or (dp['back'] and (hi - lo)[0] <= (dp['hi'] - dp['lo'])[0] + 0.1)
                if (dl[0] <= c[0] <= dh[0] and dl[2] <= c[2] <= dh[2] and lo[1] >= dl[1] - 0.05 and hi[1] <= dh[1]
                        and narrow and (p['cls'] == 'glass' or dp['back'])):
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
    # left at the kerb: dusty and sun-faded but whole, the ones worth hot-wiring later
    'usable':    dict(rust=0.06, dirt=0.5, fade=0.3, moss=0.0, blood=0.0, open=0.0, missing=0.0, smash=0.0, flat=0.0, gone=0.0, crash=0, burnt=0),
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


def swing(kind, lo, hi):
    """Pivot, axis and full opening angle for a door of this kind with this closed bounding box."""
    sgn = 1 if (lo[0] + hi[0]) > 0 else -1
    if kind == 'side':
        return np.array([hi[0] if sgn > 0 else lo[0], 0, hi[2]]), 'y', -sgn * 1.15
    if kind == 'tailgate':
        return np.array([0, lo[1], lo[2]]), 'x', -1.5
    if kind == 'hatch':
        return np.array([0, hi[1], hi[2]]), 'x', 1.2
    if kind == 'shutter':  # a box truck's roll-up door, folded up under the roof inside
        return np.array([0, hi[1] - 0.08, lo[2]]), 'x', -1.6
    return np.array([hi[0] if sgn > 0 else lo[0], 0, lo[2]]), 'y', sgn * 1.6


def turn(Q, pivot, axis, a):
    return rot_x(Q, pivot, a) if axis == 'x' else rot_y(Q, pivot, a)


def damage(car, v, rng):
    """Damaged positions, which triangles stay, broken and cracked panes, and the doors still shut."""
    P = car.P.copy()
    keep = np.ones(len(P), bool)
    shut = []
    # doors
    for i, p in enumerate(car.parts):
        if p['cls'] != 'door':
            continue
        sel = np.isin(car.pid, [j for j, d in car.door_of.items() if d == i])
        r = rng.random()
        if r < v['missing']:
            keep &= ~sel
        elif r < v['missing'] + v['open']:
            pivot, axis, a = swing(p['kind'], p['lo'], p['hi'])
            P[sel] = turn(P[sel], pivot, axis, a * rng.uniform(0.5, 1.0))
        else:
            shut.append(i)
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
    return P, keep, broken, cracked, shut


# ---------------------------------------------------------------- opening boots

def boot(car, P, keep, shut, mode):
    """
    The parts that swing open when someone searches the car: the shut back doors, tailgate or hatch
    the model has, or else a lid cut out of the body (a saloon's boot lid, a hatchback's or estate's
    hatch, a box truck's back doors). Returns a lid index per triangle (-1 for the fixed body), each
    lid's (pivot, axis, angle) on the damaged positions, and dark backing triangles that close the
    hole behind each lid.
    """
    lid = np.full(len(P), -1)
    specs, openings = [], []
    if mode is None:
        return lid, specs, []
    O, n, c = car.P, car.nrm, car.P.mean(1)
    lo, hi = car.lo, car.hi
    width, length = hi[0] - lo[0], hi[2] - lo[2]
    body = (car.cls == 'body') & np.isin(car.pid, list(car.door_of), invert=True)

    def add(sel, kind=None, pivot=None, axis=None, a=None):
        sel = sel & keep & (lid < 0)
        if sel.sum() < 2:
            return
        if kind is not None:
            pivot, axis, a = swing(kind, *bbox(P[sel]))
        lid[sel] = len(specs)
        specs.append((pivot, axis, a))
        openings.append((kind, bbox(P[sel])))

    doors = [i for i in shut if car.parts[i]['back'] and car.parts[i]['kind'] != 'side']
    has_back = any(p['cls'] == 'door' and p['back'] and p['kind'] != 'side' for p in car.parts)
    if doors:
        for i in doors:
            add(np.isin(car.pid, [j for j, d in car.door_of.items() if d == i]), car.parts[i]['kind'])
    elif has_back:
        # its back doors hang open or are gone already: nothing to swing, but still stuff inside
        for p in car.parts:
            if p['cls'] == 'door' and p['back'] and p['kind'] != 'side':
                openings.append((p['kind'], (p['lo'], p['hi'])))
    elif mode == 'truck':
        back = body & (n[:, 2] < -0.7) & (c[:, 1] > 0.5 * hi[1]) & (np.abs(c[:, 0]) < 0.44 * width)
        if not back.any():
            return lid, specs, []
        # the roll-up door: every face of the panel (both sides and its slat edges) at the back
        add(body & (c[:, 2] < c[back, 2].min() + 0.08) & (c[:, 1] > 0.3 * hi[1]) & (np.abs(c[:, 0]) < 0.44 * width), 'shutter')
    else:
        panes = []
        for k in range(car.npanes):
            m = car.pane == k
            pl, ph = bbox(O[m])
            if ph[0] - pl[0] > 0.45 * width and (pl[2] + ph[2]) / 2 < lo[2] + 0.5 * length and pl[1] > 0.5 * hi[1]:
                panes.append((pl[2], k, pl, ph))
        if panes:
            _, k, wl, wh = min(panes, key=lambda t: t[0])
        else:  # a convertible: the boot is the rear fifth
            k, wl, wh = -2, lo + [0, 0, 0.2 * length], hi
        inner = (np.abs(n[:, 0]) < 0.6) & (np.abs(c[:, 0]) < 0.46 * width)
        if wl[2] - lo[2] > 0.35:
            # saloon: the deck behind the rear window and the panel below it down to the lamps
            deck = body & (O[..., 2].max(1) < wl[2] - 0.02)
            top = O[deck][..., 1].max()
            sel = deck & inner & (O[..., 1].min(1) > top - 0.28)
            if (sel & keep).sum() >= 2:
                q = P[sel & keep]
                add(sel, pivot=np.array([0, q[..., 1].max(), q[..., 2].max()]), axis='x', a=1.1)
        else:
            # hatchback or estate: the rear window and the panel below it, hinged at the roof
            sel = ((car.pane == k) | (body & inner & (c[:, 2] < wl[2] + 0.15) & (n[:, 2] < -0.3))) \
                & (c[:, 1] > wl[1] - 0.45) & (c[:, 1] < wh[1] + 0.02)
            add(sel, 'hatch')
    return lid, specs, rooms(openings, hi[2] - lo[2])


def rooms(openings, length):
    """The load space behind the lids, as boxes (lo, hi, open faces): what you see once it's open."""
    back = [o for o in openings if o[0] in ('rear', 'shutter', 'hatch')]
    out = []
    if back:
        l = np.min([b[1][0] for b in back], 0); h = np.max([b[1][1] for b in back], 0)
        kind = back[0][0]
        if kind == 'hatch':
            out.append((l + [0.06, 0.02, 0.08], np.array([h[0] - 0.06, h[1] - 0.1, h[2] + 0.45]), ('-z',)))
        else:
            depth = min(0.45 * length, 3.5) if kind == 'shutter' else min(0.3 * length, 1.6)
            out.append((l + [0.04, 0.02, 0.06], np.array([h[0] - 0.04, h[1] - 0.03, l[2] + depth]), ('-z',)))
    for kind, (l, h) in openings:
        if kind is None:  # a saloon's boot under its lid
            out.append((l + [0.15, -0.08, 0.08], h - [0.15, 0.04, 0.03], ('-z', '+y')))
    return out


# load-space contents: (width, height, depth) ranges in metres and the colours they come in
ITEMS = {
    'box': ((0.3, 0.5), (0.22, 0.4), (0.28, 0.45), ['card', 'card_dark']),
    'bag': ((0.5, 0.7), (0.2, 0.3), (0.25, 0.32), ['olive', 'black', 'navy']),
    'case': ((0.45, 0.6), (0.14, 0.2), (0.35, 0.45), ['brown', 'black']),
    'can': ((0.18, 0.22), (0.28, 0.32), (0.26, 0.3), ['red']),
    'tools': ((0.4, 0.5), (0.18, 0.22), (0.2, 0.24), ['red', 'navy']),
    'medic': ((0.4, 0.55), (0.22, 0.3), (0.25, 0.3), ['orange', 'medic_red']),
    'crate': ((0.4, 0.6), (0.25, 0.35), (0.3, 0.4), ['black', 'olive']),
}
THEMES = {
    'trunk': ['box', 'bag', 'case', 'bag', 'can'], 'work': ['box', 'tools', 'can', 'box', 'bag'],
    'police': ['bag', 'box', 'crate'], 'swat': ['crate', 'crate', 'bag'], 'ambulance': ['medic', 'medic', 'box'],
    'fire': ['tools', 'bag', 'box'], 'cargo': ['box', 'box', 'box', 'crate'],
}
COLOURS = {'liner': (0.055, 0.055, 0.06), 'card': (0.4, 0.31, 0.2), 'card_dark': (0.31, 0.24, 0.16),
           'olive': (0.19, 0.21, 0.14), 'black': (0.07, 0.07, 0.075), 'navy': (0.11, 0.13, 0.2),
           'brown': (0.26, 0.17, 0.11), 'red': (0.38, 0.09, 0.07), 'orange': (0.5, 0.25, 0.08),
           'medic_red': (0.45, 0.12, 0.1)}


def cuboid(lo, hi, yaw=0.0, skip=(), both=False):
    """A box's faces as triangles (outward, or both ways for a liner), turned by yaw about its centre."""
    c = (lo + hi) / 2
    v = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    if yaw:
        v = rot_y(v, c, yaw)
    faces = {'-x': (0, 1, 3, 2), '+x': (4, 6, 7, 5), '-y': (0, 4, 5, 1), '+y': (2, 3, 7, 6),
             '-z': (0, 2, 6, 4), '+z': (1, 5, 7, 3)}
    tris = []
    for name, (a, b, cc, d) in faces.items():
        if name in skip:
            continue
        tris += [v[[a, b, cc]], v[[a, cc, d]]]
        if both:
            tris += [v[[cc, b, a]], v[[d, cc, a]]]
    return tris


def contents(room, theme, rng, full):
    """The liner of a load space and some boxes and bags on its floor: [(triangles, colour)]."""
    lo, hi, open_faces = room
    out = [(cuboid(lo, hi, skip=open_faces, both=True), 'liner')]
    if full <= 0:
        return out
    room_h = hi[1] - lo[1] - 0.04
    z = lo[2] + 0.05
    for row in range(3):
        if z > hi[2] - 0.25:
            break
        x, depth = lo[0] + 0.05, 0.0
        while x < hi[0] - 0.2:
            kind = THEMES[theme][rng.integers(len(THEMES[theme]))]
            (w0, w1), (h0, h1), (d0, d1), cols = ITEMS[kind]
            w, h, d = rng.uniform(w0, w1), min(rng.uniform(h0, h1), room_h), rng.uniform(d0, d1)
            if x + w > hi[0] - 0.05 or z + d > hi[2] - 0.03 or h < 0.1:
                x += 0.15
                continue
            if rng.random() < full:
                b = np.array([x, lo[1], z]); t = b + [w, h, d]
                col = cols[rng.integers(len(cols))]
                out.append((cuboid(b, t, rng.uniform(-0.18, 0.18)), col))
                if kind == 'box' and rng.random() < 0.35 and h * 1.8 < room_h:  # one stacked on top
                    s = rng.uniform(0.6, 0.9)
                    b2 = b + [w * (1 - s) / 2, h, d * (1 - s) / 2]
                    out.append((cuboid(b2, b2 + [w * s, h * s, d * s], rng.uniform(-0.3, 0.3)), col))
                depth = max(depth, d)
            x += w + rng.uniform(0.03, 0.12)
        z += max(depth, 0.3) + 0.05
    return out


# ---------------------------------------------------------------- wheels and seats

def wheel_groups(car, P, keep):
    """
    Each road wheel (tyre, rim and hub together) as its own group so the game can spin and steer
    it: a wheel index per triangle (-1 for none) and [(centre, radius, steers)] per wheel. Wheels
    are told apart by side and by gaps along the car; the front-most axle steers.
    """
    on = np.isin(car.pid, car.wheels) & keep
    wheel = np.full(len(P), -1)
    specs = []
    c = P.mean(1)
    idx = np.where(on)[0]
    if not len(idx):
        return wheel, specs
    groups = []
    for side in (1, -1):
        mine = idx[np.sign(c[idx, 0] + 1e-6) == side]
        if not len(mine):
            continue
        mine = mine[np.argsort(c[mine, 2])]
        start = 0
        for k in range(1, len(mine) + 1):
            if k == len(mine) or c[mine[k], 2] - c[mine[k - 1], 2] > 0.35:
                groups.append(mine[start:k])
                start = k
    front = max(c[g, 2].mean() for g in groups)
    for g in groups:
        lo, hi = bbox(P[g])
        wheel[g] = len(specs)
        specs.append(((lo + hi) / 2, (hi[1] - lo[1]) / 2, c[g, 2].mean() > front - 0.4))
    return wheel, specs


def seats(car):
    """
    Where people sit, as eye positions (driver first, on the left, +x): beside each side door, at
    the bottom of its window plus a bit. Buses and the like without a front-left door get a guess.
    """
    side = [i for i, p in enumerate(car.parts) if p['cls'] == 'door' and p['kind'] == 'side']
    out = []
    for i in side:
        p = car.parts[i]
        glass = [j for j, d in car.door_of.items() if d == i and j != i]
        sill = min(bbox(car.P[car.pid == j])[0][1] for j in glass) if glass else p['hi'][1]
        cx, cz = (p['lo'][0] + p['hi'][0]) / 2, (p['lo'][2] + p['hi'][2]) / 2
        out.append(np.array([cx * 0.45, sill + 0.2, cz - 0.25 * (p['hi'][2] - p['lo'][2])]))
    if not out or min(s[1] for s in out) < 0.45 * car.hi[1]:
        # a bus: its doors are glass to the floor, so seat the driver behind the windscreen
        eye, front = 0.62 * car.hi[1], car.hi[2] - 1.2
        return [np.array([0.3 * (car.hi[0] - car.lo[0]), eye, front])] + \
            [np.array([0.5 * (-1) ** k, eye, front - 1.5 - k]) for k in range(3)]
    out.sort(key=lambda s: (-s[2] // 0.5, -s[0]))  # front row first, left before right
    front = [s for s in out if s[2] > out[0][2] - 0.5]
    if not any(s[0] > 0 for s in front):  # no driver's door (some vans): mirror the passenger's
        out.insert(0, out[0] * [-1, 1, 1])
    return out[:4]


# ---------------------------------------------------------------- output

def build_variant(car, vname, seed, boot_mode='car', theme='trunk'):
    """
    The damaged car as a dict: tris (n,3,3) and uv (n,3,2, 0-1), lid (n, -1 for the fixed body) and
    lids [(pivot, axis, angle)] for the parts that open, wheel (n, -1 for none) and wheels
    [(centre, radius, steers)], glass (n, see-through from inside), plus the colour and specular images.
    """
    rng = np.random.default_rng(seed)
    v = VARIANTS[vname]
    P, keep, broken, cracked, shut = damage(car, v, rng)
    lid, lids, spaces = boot(car, P, keep, shut, boot_mode)
    c, sm, f0 = paint(car, v, np.random.default_rng(seed + 99), broken, cracked)
    W, H = car.atlas
    img = np.zeros((H, W, 3), np.float32); img[car.tex['ok']] = c
    spec = np.zeros((H, W, 4), np.float32); spec[car.tex['ok']] = np.stack([sm, f0, np.zeros_like(sm), np.zeros_like(sm)], -1)
    uv = car.uvpx[keep] / np.array([W, H])
    wheel, wheels = wheel_groups(car, P, keep)
    tris, lid, wheel, glass = P[keep], lid[keep], wheel[keep], (car.cls == 'glass')[keep]
    origin = np.where(keep)[0]  # each kept triangle's index in the source model
    full = 0.0 if v['burnt'] else {'usable': 0.8}.get(vname, 0.55)
    stuff = [piece for room in spaces for piece in contents(room, theme, rng, full)]
    if stuff:
        # each colour samples an 8x8 block the atlas doesn't use, painted flat
        free = np.argwhere(~car.tex['ok'][:H // 8 * 8, :W // 8 * 8].reshape(H // 8, 8, W // 8, 8).any((1, 3)))
        spot = {}
        for name in dict.fromkeys(col for _, col in stuff):
            by, bx = free[-1 - len(spot)] if len(free) > len(spot) else (0, 0)
            img[by * 8:by * 8 + 8, bx * 8:bx * 8 + 8] = COLOURS[name]
            spot[name] = [(bx * 8 + 4) / W, (by * 8 + 4) / H]
        add = np.array([t for ts, _ in stuff for t in ts])
        uv = np.concatenate([uv, np.array([[spot[col]] * 3 for ts, col in stuff for _ in ts])])
        tris = np.concatenate([tris, add])
        lid = np.concatenate([lid, np.full(len(add), -1)])
        wheel = np.concatenate([wheel, np.full(len(add), -1)])
        glass = np.concatenate([glass, np.zeros(len(add), bool)])
        origin = np.concatenate([origin, np.full(len(add), -1)])
    colour = Image.fromarray((img * 255 + 0.5).astype(np.uint8))
    specular = Image.fromarray((spec * 255 + 0.5).astype(np.uint8), 'RGBA')
    return {'tris': tris, 'uv': uv, 'lid': lid, 'lids': lids, 'wheel': wheel, 'wheels': wheels, 'glass': glass,
            'origin': origin, 'colour': colour, 'specular': specular}


def make_variant(car, vname, seed, out_dir, opened=0.0):
    b = build_variant(car, vname, seed)
    Pk, uv, colour, specular = b['tris'].copy(), b['uv'], b['colour'], b['specular']
    for k, (pivot, axis, a) in enumerate(b['lids']):
        Pk[b['lid'] == k] = turn(Pk[b['lid'] == k], pivot, axis, a * opened)
    stem = f"{car.name.lower()}_{vname}"
    colour.save(os.path.join(out_dir, stem + '.png'))
    specular.save(os.path.join(out_dir, stem + '_s.png'))
    nr = np.cross(Pk[:, 1] - Pk[:, 0], Pk[:, 2] - Pk[:, 0]); nr /= np.linalg.norm(nr, axis=1, keepdims=True) + 1e-12
    lo, hi = bbox(Pk)
    json.dump({'name': stem, 'texture': stem + '.png', 'size': [round(x, 3) for x in (hi - lo)],
               'pos': np.round(Pk.reshape(-1), 4).tolist(), 'uv': np.round(uv.reshape(-1), 5).tolist(),
               'nrm': np.round(np.repeat(nr, 3, 0).reshape(-1), 3).tolist()},
              open(os.path.join(out_dir, stem + '.json'), 'w'))
    return stem


def prepare(path):
    """Loads a model and lays out its atlas (smaller texels for buses and trucks)."""
    car = Car(path)
    ppm = PX_PER_M
    while build_atlas(car, ppm)[1][1] > 2048:  # keep the atlas at 1024x2048 at most
        ppm *= 0.85
    rasterize(car)
    return car


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('model'); ap.add_argument('out')
    ap.add_argument('--variants', default='usable,abandoned,wrecked,burnt')
    ap.add_argument('--seed', type=int, default=1)
    ap.add_argument('--open', type=float, default=0.0, help='how far the boot is open, 0-1')
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    car = prepare(a.model)
    for i, vn in enumerate(a.variants.split(',')):
        print(make_variant(car, vn, a.seed * 1000 + i, a.out, a.open), car.atlas)


if __name__ == '__main__':
    main()
