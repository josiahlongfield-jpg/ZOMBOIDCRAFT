"""Iron sight kit for the local (Brita) guns: simple solid shapes in item pixels, flat toon colours, merged into a
local gun's .zmesh (old sight triangles cut out first). Josiah 2026-11-01: real-life sights for every gun.

Frame: item pixels, x across (8 = centre), y up, z along the gun (muzzle toward -z).
"""
import io, math, struct, zlib, importlib.util
import numpy as np
from PIL import Image

_sp = importlib.util.spec_from_file_location('cp', '/home/user/zomboidcraft/tools/cars/pack.py')
_cp = importlib.util.module_from_spec(_sp); _sp.loader.exec_module(_cp)
scramble = _cp.scramble

# flat toon palette (shading comes from the renderer)
PALETTE = {
    'steel': (46, 48, 52), 'blued': (34, 36, 44), 'park': (72, 74, 72), 'black': (26, 26, 28),
    'white': (236, 234, 226), 'orange': (240, 112, 28), 'brass': (196, 158, 76), 'green': (120, 220, 120),
    'red': (200, 40, 32), 'silver': (176, 178, 182), 'olive': (78, 82, 58),
}
SW = 8          # swatch size in the strip


# ---------------------------------------------------------------- zmesh io
def read(path, key):
    raw = zlib.decompress(scramble(open(path, 'rb').read(), key))
    b = io.BytesIO(raw); magic = b.read(4); ver = struct.unpack('>i', b.read(4))[0]; outline = struct.unpack('>f', b.read(4))[0]
    png = b.read(struct.unpack('>i', b.read(4))[0])
    tex = Image.open(io.BytesIO(png)).convert('RGB')
    parts = []
    dt = np.dtype([('x', '>i2'), ('y', '>i2'), ('z', '>i2'), ('u', '>u2'), ('v', '>u2'), ('nx', 'i1'), ('ny', 'i1'), ('nz', 'i1')])
    for _ in range(struct.unpack('>i', b.read(4))[0]):
        name = b.read(struct.unpack('>H', b.read(2))[0]).decode(); t = struct.unpack('>i', b.read(4))[0]
        parts.append([name, np.frombuffer(b.read(t * 3 * dt.itemsize), dtype=dt).copy()])
    return {'outline': outline, 'tex': tex, 'parts': parts, 'dt': dt}


def write(m, path, key):
    png = io.BytesIO(); m['tex'].save(png, 'PNG', optimize=True)
    out = io.BytesIO()
    out.write(b'ZMSH'); out.write(struct.pack('>i', 3)); out.write(struct.pack('>f', m['outline']))
    out.write(struct.pack('>i', len(png.getvalue()))); out.write(png.getvalue())
    out.write(struct.pack('>i', len(m['parts'])))
    for name, rec in m['parts']:
        b = name.encode(); out.write(struct.pack('>H', len(b))); out.write(b)
        out.write(struct.pack('>i', len(rec) // 3)); out.write(rec.astype(m['dt']).tobytes())
    open(path, 'wb').write(scramble(zlib.compress(out.getvalue(), 9), key))


def positions(rec):
    return np.stack([rec['x'], rec['y'], rec['z']], -1).astype(float).reshape(-1, 3, 3) / 1000


def cut(m, boxes, part='body'):
    """Drops a part's triangles with any corner inside any box (x0,x1,y0,y1,z0,z1), so no shard of an old sight is
    left; keep each box's floor just above the surface the sight stands on. Returns how many went."""
    for p in m['parts']:
        if p[0] != part:
            continue
        P = positions(p[1]); keep = np.ones(len(P), bool)
        for x0, x1, y0, y1, z0, z1 in boxes:
            inb = (P[..., 0] >= x0) & (P[..., 0] <= x1) & (P[..., 1] >= y0) & (P[..., 1] <= y1) & (P[..., 2] >= z0) & (P[..., 2] <= z1)
            keep &= ~inb.any(1)
        gone = int((~keep).sum())
        p[1] = p[1].reshape(-1, 3)[keep].reshape(-1)
        return gone
    return 0


# ---------------------------------------------------------------- shapes
BEVEL = 0.014   # item px: the worn chamfer every edge gets (scaled down on small parts)


class Shape:
    """Triangles (n,3,3), each with a colour, a paint kind ('face' painted metal, 'edge' worn edge, 'plain') and
    tile UVs (n,3,2) in 0..1 of that colour's painted tile."""
    def __init__(self):
        self.tris = []; self.cols = []; self.uvs = []

    def add(self, tris, col, kind='face', uvs=None):
        tris = np.asarray(tris, float).reshape(-1, 3, 3)
        if uvs is None:
            uvs = np.tile([[0.2, 0.2], [0.8, 0.2], [0.8, 0.8]], (len(tris), 1, 1))
        self.tris.append(tris); self.uvs.append(np.asarray(uvs, float).reshape(-1, 3, 2)); self.cols += [(col, kind)] * len(tris)
        return self

    def quad(self, a, b, c, d, col, kind='face', uv=None):
        u = uv or [(0, 0), (1, 0), (1, 1), (0, 1)]
        return self.add([[a, b, c], [a, c, d]], col, kind, [[u[0], u[1], u[2]], [u[0], u[2], u[3]]])

    def box(self, x0, x1, y0, y1, z0, z1, col, bevel=None):
        return self.prism([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], z0, z1, col, 'xy', bevel)

    def prism(self, poly, z0, z1, col, plane='xy', bevel=None):
        """A flat 2D outline extruded, every edge chamfered (a worn edge): plane 'xy' extrudes along z (a blade
        seen from behind), 'zy' along x (a side profile), 'xz' along y. poly: any simple polygon (ear-clipped)."""
        poly = [tuple(map(float, p)) for p in poly]
        if _area(poly) < 0:
            poly = poly[::-1]
        if plane != 'xy':
            # those axis swaps mirror the outline: built as xy, then the axes are swapped and each face turned over
            S = Shape().prism(poly, z0, z1, col, 'xy', bevel)
            t = np.concatenate(S.tris)[:, :, {'zy': [2, 1, 0], 'xz': [0, 2, 1]}[plane]][:, ::-1]
            uv = np.concatenate(S.uvs)[:, ::-1]
            for (c_, k), tt, uu in zip(S.cols, t, uv):
                self.add([tt], c_, k, [uu])
            return self
        n = len(poly); el = [math.dist(poly[i], poly[(i + 1) % n]) for i in range(n)]
        c = BEVEL if bevel is None else bevel
        c = min(c, 0.2 * min(el), 0.25 * (z1 - z0))
        P, cutedge = _corner_cut(poly, c) if c > 1e-4 else (poly, [False] * n)
        Pi = _inset(P, c) if c > 1e-4 else P
        za, zb = (z0 + c, z1 - c) if c > 1e-4 else (z0, z1)
        lo = np.min(P, 0); span = np.maximum(np.max(P, 0) - lo, 1e-6)
        uv = lambda q: tuple((np.asarray(q) - lo) / span)
        for i, j, k in _earclip(Pi):
            self.add([[(*Pi[i], z0), (*Pi[k], z0), (*Pi[j], z0)]], col, 'face', [[uv(Pi[i]), uv(Pi[k]), uv(Pi[j])]])
            self.add([[(*Pi[i], z1), (*Pi[j], z1), (*Pi[k], z1)]], col, 'face', [[uv(Pi[i]), uv(Pi[j]), uv(Pi[k])]])
        m = len(P)
        for i in range(m):
            j = (i + 1) % m; a, b = P[i], P[j]
            self.quad((*a, za), (*b, za), (*b, zb), (*a, zb), col, 'edge' if cutedge[i] else 'face')
            if c > 1e-4:
                self.quad((*Pi[i], z0), (*Pi[j], z0), (*b, za), (*a, za), col, 'edge', [(0, 0), (1, 0), (1, 0.3), (0, 0.3)])
                self.quad((*a, zb), (*b, zb), (*Pi[j], z1), (*Pi[i], z1), col, 'edge', [(0, 0.7), (1, 0.7), (1, 1), (0, 1)])
        return self

    def ring(self, cx, cy, r_in, r_out, z0, z1, col, n=20, plane='xy', bevel=None):
        """An annulus (an aperture) extruded along z, its four rims chamfered."""
        if plane != 'xy':
            S = Shape().ring(cx, cy, r_in, r_out, z0, z1, col, n, 'xy', bevel)
            t = np.concatenate(S.tris)[:, :, {'zy': [2, 1, 0], 'xz': [0, 2, 1]}[plane]][:, ::-1]
            uv = np.concatenate(S.uvs)[:, ::-1]
            for (c_, k), tt, uu in zip(S.cols, t, uv):
                self.add([tt], c_, k, [uu])
            return self
        c = BEVEL if bevel is None else bevel
        c = min(c, 0.25 * (r_out - r_in), 0.25 * (z1 - z0))
        za, zb = z0 + c, z1 - c
        P = lambda r, k, z: (cx + r * math.cos(2 * math.pi * k / n), cy + r * math.sin(2 * math.pi * k / n), z)
        for k in range(n):
            u0, u1 = k / n, (k + 1) / n
            k1 = k + 1
            # outer and inner walls
            self.quad(P(r_out, k, za), P(r_out, k1, za), P(r_out, k1, zb), P(r_out, k, zb), col, 'face', [(u0, 0), (u1, 0), (u1, 1), (u0, 1)])
            self.quad(P(r_in, k1, za), P(r_in, k, za), P(r_in, k, zb), P(r_in, k1, zb), col, 'face', [(u1, 0), (u0, 0), (u0, 1), (u1, 1)])
            # flat faces (front and back)
            ri, ro = r_in + c, r_out - c
            self.quad(P(ri, k, z1), P(ro, k, z1), P(ro, k1, z1), P(ri, k1, z1), col, 'face', [(u0, 0), (u0, 1), (u1, 1), (u1, 0)])
            self.quad(P(ri, k1, z0), P(ro, k1, z0), P(ro, k, z0), P(ri, k, z0), col, 'face', [(u1, 0), (u1, 1), (u0, 1), (u0, 0)])
            # worn chamfers on the four rims
            e = [(u0, 0), (u1, 0), (u1, 0.3), (u0, 0.3)]
            self.quad(P(r_out, k, zb), P(r_out, k1, zb), P(ro, k1, z1), P(ro, k, z1), col, 'edge', e)
            self.quad(P(ro, k, z0), P(ro, k1, z0), P(r_out, k1, za), P(r_out, k, za), col, 'edge', e)
            self.quad(P(ri, k1, z1), P(r_in, k1, zb), P(r_in, k, zb), P(ri, k, z1), col, 'edge', e)
            self.quad(P(r_in, k1, za), P(ri, k1, z0), P(ri, k, z0), P(r_in, k, za), col, 'edge', e)
        return self

    def cyl(self, cx, cy, r, z0, z1, col, n=12, plane='xy', bevel=None):
        return self.prism([(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)], z0, z1, col, plane, bevel)

    def sphere(self, c, r, col, n=10):
        c = np.asarray(c, float)
        for i in range(n):
            t0, t1 = math.pi * i / n, math.pi * (i + 1) / n
            for j in range(2 * n):
                p0, p1 = 2 * math.pi * j / (2 * n), 2 * math.pi * (j + 1) / (2 * n)
                s = lambda t, p: c + r * np.array([math.sin(t) * math.cos(p), math.cos(t), math.sin(t) * math.sin(p)])
                u0, u1, v0, v1 = j / (2 * n), (j + 1) / (2 * n), i / n, (i + 1) / n
                self.quad(s(t0, p1), s(t1, p1), s(t1, p0), s(t0, p0), col, 'plain', [(u1, v0), (u1, v1), (u0, v1), (u0, v0)])
        return self

    def merged(self):
        return np.concatenate(self.tris), self.cols, np.concatenate(self.uvs)


def _corner_cut(poly, c):
    """Cuts each sharp convex corner (interior angle under 140 degrees) back by c: the edge's worn chamfer seen
    end-on. Returns the new outline and which of its edges are cuts."""
    n = len(poly); out = []; cut = []
    for i in range(n):
        a, v, b = np.array(poly[i - 1]), np.array(poly[i]), np.array(poly[(i + 1) % n])
        d0, d1 = a - v, b - v; l0, l1 = np.linalg.norm(d0), np.linalg.norm(d1)
        cross = (v - a)[0] * (b - v)[1] - (v - a)[1] * (b - v)[0]
        ang = math.degrees(math.acos(np.clip(np.dot(d0, d1) / (l0 * l1 + 1e-12), -1, 1)))
        if cross > 0 and ang < 140 and l0 > 2.5 * c and l1 > 2.5 * c:
            out += [tuple(v + d0 / l0 * c), tuple(v + d1 / l1 * c)]; cut += [True, False]
        else:
            out.append(tuple(v)); cut.append(False)
    return out, cut


def _inset(P, c):
    """The outline moved inward by c (mitred, clamped)."""
    n = len(P); out = []
    for i in range(n):
        a, v, b = np.array(P[i - 1]), np.array(P[i]), np.array(P[(i + 1) % n])
        e0 = (v - a) / (np.linalg.norm(v - a) + 1e-12); e1 = (b - v) / (np.linalg.norm(b - v) + 1e-12)
        n0 = np.array([-e0[1], e0[0]]); n1 = np.array([-e1[1], e1[0]])
        m = n0 + n1; k = 1 + np.dot(n0, n1)
        off = m / k if k > 0.2 else (n0 + n1) / (np.linalg.norm(n0 + n1) + 1e-12) * 2.2
        if np.linalg.norm(off) > 2.2:
            off = off / np.linalg.norm(off) * 2.2
        out.append(tuple(v + off * c))
    return out


def _area(p):
    return sum(p[i][0] * p[(i + 1) % len(p)][1] - p[(i + 1) % len(p)][0] * p[i][1] for i in range(len(p))) / 2


def _earclip(poly):
    idx = list(range(len(poly))); out = []
    def inside(pt, a, b, c):
        d = lambda p, q, r: (p[0] - r[0]) * (q[1] - r[1]) - (q[0] - r[0]) * (p[1] - r[1])
        d1, d2, d3 = d(pt, a, b), d(pt, b, c), d(pt, c, a)
        return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))
    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        for k in range(len(idx)):
            i, j, l = idx[k - 1], idx[k], idx[(k + 1) % len(idx)]
            a, b, c = poly[i], poly[j], poly[l]
            if (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]) <= 0:
                continue
            if any(inside(poly[m], a, b, c) for m in idx if m not in (i, j, l)):
                continue
            out.append((i, j, l)); idx.pop(k); break
    if len(idx) == 3:
        out.append(tuple(idx))
    return out


# ---------------------------------------------------------------- merging
def colour_at(m, box, part='body'):
    """The mean texture colour of a part's triangles whose centre lies in a box: to patch a hole in the gun's own paint."""
    tex = np.asarray(m['tex'], float); H, W = tex.shape[:2]
    for p in m['parts']:
        if p[0] != part:
            continue
        P = positions(p[1]); c = P.mean(1); x0, x1, y0, y1, z0, z1 = box
        sel = (c[:, 0] >= x0) & (c[:, 0] <= x1) & (c[:, 1] >= y0) & (c[:, 1] <= y1) & (c[:, 2] >= z0) & (c[:, 2] <= z1)
        r = p[1].reshape(-1, 3)[sel]
        if not len(r):
            return PALETTE['steel']
        u = np.clip((r['u'].astype(float) / 65535 * W).astype(int), 0, W - 1); v = np.clip((r['v'].astype(float) / 65535 * H).astype(int), 0, H - 1)
        return tuple(int(x) for x in np.median(tex[v.ravel(), u.ravel()], 0))
    return PALETTE['steel']


TS = 64          # painted tile size in the strip
JITTER = 0.0025  # item px: hand-made unevenness, the same for every copy of a corner so nothing tears


def _rgb(c):
    return np.array(PALETTE[c] if isinstance(c, str) else c, float)


def make_tile(rgb, kind, seed):
    """One painted tile in the Brita guns' hand-painted way: mottled metal, a soft highlight streak, darker
    painted borders, a few light scratches; 'edge' is the worn, lighter metal on a chamfer."""
    rng = np.random.default_rng(seed)
    def blur_noise(k):
        small = rng.random((k, k))
        return np.asarray(Image.fromarray((small * 255).astype(np.uint8)).resize((TS, TS), Image.BICUBIC), float) / 255 - 0.5
    y, x = np.mgrid[0:TS, 0:TS] / (TS - 1)
    lum = 1 + 0.22 * blur_noise(5) + 0.08 * blur_noise(16) + 0.05 * (rng.random((TS, TS)) - 0.5)
    base = np.repeat(_rgb(rgb)[None, None], TS, 0).repeat(TS, 1)
    lift = lambda img, amt: img + (235 - img) * amt   # works on near-black paint too
    if kind == 'edge':
        img = lift(base, 0.30) * lum[..., None]
        for _ in range(6):   # dark chips
            cx, cy = rng.integers(4, TS - 4, 2); img[max(cy - 1, 0):cy + 2, max(cx - 2, 0):cx + 2] *= 0.6
    else:
        img = base * lum[..., None]
        if kind == 'face':
            streak = np.exp(-((y - rng.uniform(0.3, 0.45)) / 0.16) ** 2)
            img = lift(img, 0.10 * streak[..., None])
            d = np.minimum.reduce([x, y, 1 - x, 1 - y]) * (TS - 1)
            img *= (1 - 0.30 * np.clip(1 - d / 7, 0, 1))[..., None]
        for _ in range(2):   # light scratches
            x0, y0 = rng.uniform(0.15, 0.85, 2) * TS; ang = rng.uniform(0, math.pi); L = rng.uniform(6, 16)
            for t in np.linspace(0, L, int(L * 2)):
                xi, yi = int(x0 + t * math.cos(ang)), int(y0 + t * math.sin(ang))
                if 0 <= xi < TS and 0 <= yi < TS:
                    img[yi, xi] = lift(img[yi, xi], 0.13)
    return np.clip(img, 0, 255).astype(np.uint8)


def _jitter(P):
    q = np.round(P * 2000).astype(np.int64)
    h = (q[..., 0] * 73856093) ^ (q[..., 1] * 19349663) ^ (q[..., 2] * 83492791)
    out = np.empty_like(P)
    for k, salt in enumerate((0x9E3779B1, 0x85EBCA77, 0xC2B2AE3D)):
        v = ((h ^ salt) * 2654435761) & 0xFFFF
        out[..., k] = (v / 65535.0 - 0.5) * 2 * JITTER
    return P + out


def add_shapes(m, shape, part='body', seed=1):
    """Adds the shape's triangles to a part, painted from tiles appended under the gun's texture. A colour is a
    PALETTE name or an (r, g, b)."""
    tex = m['tex']; W, H = tex.size
    tris, cols, uvs = shape.merged()
    key = lambda ck: (tuple(int(v) for v in _rgb(ck[0])), ck[1])
    tiles = list(dict.fromkeys(key(ck) for ck in cols))
    per_row = max(1, W // TS); strip_h = TS * ((len(tiles) + per_row - 1) // per_row)
    new = Image.new('RGB', (W, H + strip_h), (0, 0, 0)); new.paste(tex, (0, 0))
    for i, (rgb, kind) in enumerate(tiles):
        new.paste(Image.fromarray(make_tile(rgb, kind, seed * 1000 + i)), ((i % per_row) * TS, H + (i // per_row) * TS))
    for p in m['parts']:
        p[1]['v'] = np.round(p[1]['v'].astype(float) * H / (H + strip_h)).astype(np.uint16)
    n = np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0]); n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
    tj = _jitter(tris)
    rec = np.zeros(len(tris) * 3, dtype=m['dt'])
    q = np.clip(np.round(tj.reshape(-1, 3) * 1000), -32768, 32767)
    rec['x'], rec['y'], rec['z'] = q[:, 0], q[:, 1], q[:, 2]
    nn = np.repeat(np.round(n * 127), 3, axis=0)
    rec['nx'], rec['ny'], rec['nz'] = nn[:, 0], nn[:, 1], nn[:, 2]
    mg = 3   # keep clear of the tile's border so filtering never picks up a neighbour
    idx = np.array([tiles.index(key(ck)) for ck in cols])
    ox = (idx % per_row) * TS + mg; oy = H + (idx // per_row) * TS + mg; sz = TS - 2 * mg
    u = (ox[:, None] + np.clip(uvs[:, :, 0], 0, 1) * sz) / W
    v = (oy[:, None] + np.clip(uvs[:, :, 1], 0, 1) * sz) / (H + strip_h)
    rec['u'] = np.round(u.reshape(-1) * 65535); rec['v'] = np.round(v.reshape(-1) * 65535)
    for p in m['parts']:
        if p[0] == part:
            p[1] = np.concatenate([p[1], rec]); break
    m['tex'] = new
    return m
