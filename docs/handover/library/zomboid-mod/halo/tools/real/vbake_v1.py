"""Bake a level piece's real Reach lighting into its vertex colours, for the game (which can't sample a lightmap per pixel).
usage: vbake.py <in.glb> <lightmap _lm.png or -> <out.glb>

Triangles with lightmap UVs (TEXCOORD_1) are split (1 into 4, neighbours 1 into 2 so no cracks) wherever the light
across them differs from what their corners give, down to about one lightmap texel, so shadow edges stay sharp. Every
vertex then gets COLOR_0 = the light there, encoded for a plain "texture x vertex colour" draw:
    c = (tone(E * pi * EXPO)) ^ (1/2.2) / K      (8-bit, alpha 1)
and the scene extras say {"reachLight": {"scale": K}}: the game brightens textures by K so lit surfaces can be brighter
than the bare picture. Pieces lit per vertex (COLOR_0 already holding light) are re-encoded the same way; unlit ones get
a soft sun-and-sky fallback so nothing is drawn at full white.
"""
import sys, json, struct, numpy as np
from PIL import Image
sys.path.insert(0, '/tmp/claude-0/halo')
from glbread import G

EXPO, K = 2.0, 1.3
TOL = 12.0 / 255      # allowed error in the encoded colour before a triangle is split
MIN_EDGE = 3.0       # stop splitting below this many lightmap texels


def encode(E):
    v = np.maximum(E, 0) * np.pi * EXPO
    v = v / (1 + v * 0.25)
    return np.clip(v ** (1 / 2.2) / K, 0, 1)


def load_lm(path):
    a = np.asarray(Image.open(path).convert('RGBA'), np.float32) / 255
    return a[..., :3] * a[..., 3:4] * 2.0  # RGBM, R = 2 (mklm.py)


def sample(L, uv):
    H, W, _ = L.shape
    x = uv[:, 0] * W - 0.5; y = uv[:, 1] * H - 0.5
    x0 = np.floor(x).astype(np.int64); y0 = np.floor(y).astype(np.int64); fx = (x - x0)[:, None]; fy = (y - y0)[:, None]
    def at(yy, xx): return L[np.clip(yy, 0, H - 1), np.clip(xx, 0, W - 1)]
    return (at(y0, x0) * (1 - fx) * (1 - fy) + at(y0, x0 + 1) * fx * (1 - fy) + at(y0 + 1, x0) * (1 - fx) * fy + at(y0 + 1, x0 + 1) * fx * fy)


def refine(attrs, T, L):
    """attrs: dict name -> (n,k) float arrays (must include 'uv1'); T: (t,3). Returns refined attrs, T, colour per vertex."""
    H, W, _ = L.shape
    sc = np.array([W, H], np.float64)
    for it in range(12):
        uv = attrs['uv1'].astype(np.float64)
        C = encode(sample(L, uv))
        a, b, c = T[:, 0], T[:, 1], T[:, 2]
        e = np.stack([np.linalg.norm((uv[b] - uv[a]) * sc, axis=1), np.linalg.norm((uv[c] - uv[b]) * sc, axis=1),
                      np.linalg.norm((uv[a] - uv[c]) * sc, axis=1)], 1)
        # light at edge midpoints and the middle vs what the corners interpolate to
        pts = [(a, b), (b, c), (c, a)]
        err = np.zeros(len(T))
        for p, q in pts:
            m = encode(sample(L, (uv[p] + uv[q]) / 2)); err = np.maximum(err, np.abs(m - (C[p] + C[q]) / 2).max(1))
        mid = encode(sample(L, (uv[a] + uv[b] + uv[c]) / 3)); err = np.maximum(err, np.abs(mid - (C[a] + C[b] + C[c]) / 3).max(1))
        red = (err > TOL) & (e.max(1) > MIN_EDGE)
        if not red.any():
            return attrs, T, C
        # edges to split; promote triangles with 2+ split edges to full splits until stable
        def ekey(p, q): return np.minimum(p, q).astype(np.int64) * (1 << 32) + np.maximum(p, q)
        keys = np.stack([ekey(a, b), ekey(b, c), ekey(c, a)], 1)
        while True:
            split = np.unique(keys[red].ravel())
            ns = np.isin(keys, split).sum(1)
            prom = (~red) & (ns >= 2)
            if not prom.any(): break
            red |= prom
        split = np.unique(keys[red].ravel())
        # new midpoint vertices
        p = (split >> 32).astype(np.int64); q = (split & 0xffffffff).astype(np.int64)
        n0 = len(attrs['uv1'])
        attrs = {k: np.concatenate([v, (v[p] + v[q]) / 2]) for k, v in attrs.items()}
        mid_of = dict(zip(split.tolist(), range(n0, n0 + len(split))))
        out = []
        green = (~red) & np.isin(keys, split).any(1)
        keep = (~red) & (~green)
        out.append(T[keep])
        for t, k in zip(T[red], keys[red]):
            m0, m1, m2 = mid_of[int(k[0])], mid_of[int(k[1])], mid_of[int(k[2])]
            out.append(np.array([[t[0], m0, m2], [m0, t[1], m1], [m2, m1, t[2]], [m0, m1, m2]]))
        for t, k in zip(T[green], keys[green]):
            for j in range(3):
                if int(k[j]) in mid_of:
                    m = mid_of[int(k[j])]; v0, v1, v2 = t[j], t[(j + 1) % 3], t[(j + 2) % 3]
                    out.append(np.array([[v0, m, v2], [m, v1, v2]])); break
        T = np.concatenate([o.reshape(-1, 3) for o in out]).astype(np.int64)
    uv = attrs['uv1'].astype(np.float64)
    return attrs, T, encode(sample(L, uv))


def fallback(P):
    return None


def main(src, lm, dst):
    g = G(src)
    L = load_lm(lm) if lm != '-' else None
    j = g.j
    out_bin = bytearray(); views = []; accs = []
    def put(arr, typ, ctype, target=None, normalized=False, minmax=False):
        arr = np.ascontiguousarray(arr)
        while len(out_bin) % 4: out_bin.append(0)
        views.append(dict(buffer=0, byteOffset=len(out_bin), byteLength=arr.nbytes, **({'target': target} if target else {})))
        out_bin.extend(arr.tobytes())
        a = dict(bufferView=len(views) - 1, componentType=ctype, count=int(arr.shape[0]), type=typ)
        if normalized: a['normalized'] = True
        if minmax: a['min'] = arr.min(0).tolist(); a['max'] = arr.max(0).tolist()
        accs.append(a); return len(accs) - 1
    sun = np.array([0.4, 0.85, 0.35]); sun /= np.linalg.norm(sun)
    nv0 = nv1 = 0
    for mesh in j['meshes']:
        for p in mesh['primitives']:
            at = p['attributes']
            P = g.acc(at['POSITION']).astype(np.float32)
            T = g.acc(p['indices']).astype(np.int64).reshape(-1, 3)
            A = {'pos': P.astype(np.float64)}
            if 'TEXCOORD_0' in at: A['uv0'] = g.acc(at['TEXCOORD_0']).astype(np.float64)
            nv0 += len(P)
            if 'TEXCOORD_1' in at and L is not None:
                A['uv1'] = g.acc(at['TEXCOORD_1']).astype(np.float64)
                A, T, C = refine(A, T, L)
            elif 'COLOR_0' in at:
                E = g.acc(at['COLOR_0']).astype(np.float64)[:, :3]
                C = encode(E)
            else:
                fn = np.cross(P[T[:, 1]] - P[T[:, 0]], P[T[:, 2]] - P[T[:, 0]]); N = np.zeros_like(P)
                for c in range(3): np.add.at(N, T[:, c], fn)
                N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
                E = 0.05 + 0.22 * np.maximum(N @ sun, 0)[:, None] * np.array([1.0, 0.95, 0.85])
                C = encode(E)
            nv1 += len(A['pos'])
            na = dict(POSITION=put(A['pos'].astype(np.float32), 'VEC3', 5126, 34962, minmax=True))
            if 'uv0' in A: na['TEXCOORD_0'] = put(A['uv0'].astype(np.float32), 'VEC2', 5126, 34962)
            if 'uv1' in A: na['TEXCOORD_1'] = put(A['uv1'].astype(np.float32), 'VEC2', 5126, 34962)
            col = np.concatenate([np.round(C * 255), np.full((len(C), 1), 255)], 1).astype(np.uint8)
            na['COLOR_0'] = put(col, 'VEC4', 5121, 34962, normalized=True)
            p['attributes'] = na
            p['indices'] = put(T.astype(np.uint32).reshape(-1), 'SCALAR', 5125, 34963)
    j['bufferViews'] = views; j['accessors'] = accs
    # image bufferViews (embedded pictures) are not carried over: the exporter writes them as tex/ files
    for im in j.get('images', []):
        if 'bufferView' in im: raise SystemExit('embedded images not supported: export with ZC_TEXLIB')
    while len(out_bin) % 4: out_bin.append(0)
    j['buffers'] = [dict(byteLength=len(out_bin))]
    j['scenes'][0].setdefault('extras', {})['reachLight'] = dict(scale=K, expo=EXPO, encoding='srgb vertex colour')
    js = json.dumps(j, separators=(',', ':')).encode()
    while len(js) % 4: js += b' '
    with open(dst, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(out_bin)) + struct.pack('<II', len(js), 0x4E4F534A) + js
                + struct.pack('<II', len(out_bin), 0x004E4942) + bytes(out_bin))
    print('vbake', src.split('/')[-1], 'vertices', nv0, '->', nv1, 'MB %.1f' % (len(out_bin) / 1e6), flush=True)


if __name__ == '__main__':
    main(*sys.argv[1:4])
