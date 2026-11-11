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
class Shape:
    """Triangles (n,3,3) with one palette colour each."""
    def __init__(self):
        self.tris = []; self.cols = []

    def add(self, tris, col):
        tris = np.asarray(tris, float).reshape(-1, 3, 3); self.tris.append(tris); self.cols += [col] * len(tris)
        return self

    def quad(self, a, b, c, d, col):
        return self.add([[a, b, c], [a, c, d]], col)

    def box(self, x0, x1, y0, y1, z0, z1, col):
        v = [[x, y, z] for z in (z0, z1) for y in (y0, y1) for x in (x0, x1)]
        f = [(0, 2, 3, 1), (4, 5, 7, 6), (0, 1, 5, 4), (2, 6, 7, 3), (0, 4, 6, 2), (1, 3, 7, 5)]
        for a, b, c, d in f:
            self.quad(v[a], v[b], v[c], v[d], col)
        return self

    def prism(self, poly, z0, z1, col, plane='xy'):
        """A flat 2D outline extruded: plane 'xy' extrudes along z (a blade seen from behind), 'zy' along x
        (a side profile), 'xz' along y. poly: list of 2D points, any simple polygon (ear-clipped)."""
        poly = [tuple(p) for p in poly]
        if _area(poly) < 0:
            poly = poly[::-1]
        if plane != 'xy':
            # those axis swaps mirror the outline: built as xy, then the axes are swapped and each face turned over
            S = Shape().prism(poly, z0, z1, col, 'xy'); t = np.concatenate(S.tris)
            t = t[:, :, {'zy': [2, 1, 0], 'xz': [0, 2, 1]}[plane]][:, ::-1]
            return self.add(t, col)
        def P(a, b, w):
            return (a, b, w)
        for i, j, k in _earclip(poly):
            self.add([[P(*poly[i], z0), P(*poly[k], z0), P(*poly[j], z0)]], col)
            self.add([[P(*poly[i], z1), P(*poly[j], z1), P(*poly[k], z1)]], col)
        n = len(poly)
        for i in range(n):
            a, b = poly[i], poly[(i + 1) % n]
            self.quad(P(*a, z0), P(*b, z0), P(*b, z1), P(*a, z1), col)
        return self

    def ring(self, cx, cy, r_in, r_out, z0, z1, col, n=20, plane='xy'):
        """An annulus (an aperture) extruded along z."""
        for i in range(n):
            a0, a1 = 2 * math.pi * i / n, 2 * math.pi * (i + 1) / n
            p = lambda r, a: (cx + r * math.cos(a), cy + r * math.sin(a))
            q = [p(r_out, a0), p(r_out, a1), p(r_in, a1), p(r_in, a0)]
            self.prism(q, z0, z1, col, plane)
        return self

    def cyl(self, cx, cy, r, z0, z1, col, n=12, plane='xy'):
        return self.prism([(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)], z0, z1, col, plane)

    def sphere(self, c, r, col, n=10):
        c = np.asarray(c, float)
        for i in range(n):
            t0, t1 = math.pi * i / n, math.pi * (i + 1) / n
            for j in range(2 * n):
                p0, p1 = 2 * math.pi * j / (2 * n), 2 * math.pi * (j + 1) / (2 * n)
                s = lambda t, p: c + r * np.array([math.sin(t) * math.cos(p), math.cos(t), math.sin(t) * math.sin(p)])
                self.quad(s(t0, p1), s(t1, p1), s(t1, p0), s(t0, p0), col)
        return self

    def merged(self):
        return np.concatenate(self.tris), self.cols


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


def add_shapes(m, shape, part='body'):
    """Adds the shape's triangles to a part, painting them from a palette strip appended under the texture. A colour
    is a PALETTE name or an (r, g, b)."""
    tex = m['tex']; W, H = tex.size
    tris, cols = shape.merged()
    rgb = lambda c: PALETTE[c] if isinstance(c, str) else tuple(c)
    names = list(dict.fromkeys(rgb(c) for c in cols))
    per_row = max(1, W // SW); strip_h = SW * ((len(names) + per_row - 1) // per_row)
    new = Image.new('RGB', (W, H + strip_h), (0, 0, 0)); new.paste(tex, (0, 0))
    for i, n in enumerate(names):
        new.paste(Image.new('RGB', (SW, SW), n), ((i % per_row) * SW, H + (i // per_row) * SW))
    # existing V coords squeeze into the top H rows
    for p in m['parts']:
        p[1]['v'] = np.round(p[1]['v'].astype(float) * H / (H + strip_h)).astype(np.uint16)
    rec = np.zeros(len(tris) * 3, dtype=m['dt'])
    q = np.clip(np.round(tris.reshape(-1, 3) * 1000), -32768, 32767)
    rec['x'], rec['y'], rec['z'] = q[:, 0], q[:, 1], q[:, 2]
    n = np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0]); n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
    nn = np.repeat(np.round(n * 127), 3, axis=0)
    rec['nx'], rec['ny'], rec['nz'] = nn[:, 0], nn[:, 1], nn[:, 2]
    for t, c in enumerate(cols):
        i = names.index(rgb(c)); u = ((i % per_row) * SW + SW / 2) / W; v = (H + (i // per_row) * SW + SW / 2) / (H + strip_h)
        rec['u'][t * 3:t * 3 + 3] = round(u * 65535); rec['v'][t * 3:t * 3 + 3] = round(v * 65535)
    for p in m['parts']:
        if p[0] == part:
            p[1] = np.concatenate([p[1], rec]); break
    m['tex'] = new
    return m
