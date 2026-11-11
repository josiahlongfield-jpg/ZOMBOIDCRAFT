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


def sample_wrap(L, uv):
    H, W, _ = L.shape
    x = uv[:, 0] * W - 0.5; y = uv[:, 1] * H - 0.5
    x0 = np.floor(x).astype(np.int64); y0 = np.floor(y).astype(np.int64); fx = (x - x0)[:, None]; fy = (y - y0)[:, None]
    def at(yy, xx): return L[np.mod(yy, H), np.mod(xx, W)]
    return (at(y0, x0) * (1 - fx) * (1 - fy) + at(y0, x0 + 1) * fx * (1 - fy) + at(y0 + 1, x0) * (1 - fx) * fy + at(y0 + 1, x0 + 1) * fx * fy)


def refine(attrs, T, sigs, iters=12):
    """attrs: dict name -> (n,k) float arrays; T: (t,3); sigs: list of dict(f=callable(attr dict)->(n,c) values,
    uv=attr name, sc=(2,) texels per uv unit, tol, mn=min edge in texels). Splits triangles until every signal is
    linear enough across each triangle. Returns refined attrs, T."""
    for it in range(iters):
        if not sigs: break
        a, b, c = T[:, 0], T[:, 1], T[:, 2]
        red = np.zeros(len(T), bool)
        for s in sigs:
            uv = attrs[s['uv']]; sc = np.asarray(s['sc'], np.float64)
            V = s['f'](attrs)
            e = np.stack([np.linalg.norm((uv[b] - uv[a]) * sc, axis=1), np.linalg.norm((uv[c] - uv[b]) * sc, axis=1),
                          np.linalg.norm((uv[a] - uv[c]) * sc, axis=1)], 1)
            err = np.zeros(len(T))
            for p, q in ((a, b), (b, c), (c, a)):
                m = s['f']({k: (v[p] + v[q]) / 2 for k, v in attrs.items()})
                err = np.maximum(err, np.abs(m - (V[p] + V[q]) / 2).max(1))
            m = s['f']({k: (v[a] + v[b] + v[c]) / 3 for k, v in attrs.items()})
            err = np.maximum(err, np.abs(m - (V[a] + V[b] + V[c]) / 3).max(1))
            red |= (err > s['tol']) & (e.max(1) > s['mn'])
        if not red.any():
            break
        def ekey(p, q): return np.minimum(p, q).astype(np.int64) * (1 << 32) + np.maximum(p, q)
        keys = np.stack([ekey(a, b), ekey(b, c), ekey(c, a)], 1)
        while True:
            split = np.unique(keys[red].ravel())
            ns = np.isin(keys, split).sum(1)
            prom = (~red) & (ns >= 2)
            if not prom.any(): break
            red |= prom
        split = np.unique(keys[red].ravel())
        p = (split >> 32).astype(np.int64); q = (split & 0xffffffff).astype(np.int64)
        n0 = len(attrs['pos'])
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
    return attrs, T


ROOT = '/tmp/claude-0/halo/real'
TEXDIR = ROOT + '/tex'
CAP = 2048
_IMG = {}


def img(uri, mode='RGB'):
    k = (uri, mode)
    if k not in _IMG:
        _IMG[k] = Image.open(ROOT + '/' + uri).convert(mode)
    return _IMG[k]


def pow2(n):
    return int(min(CAP, max(16, 2 ** round(np.log2(max(n, 1))))))


def bake_layer(buri, bx, duri, dx, dco):
    """One picture for a terrain layer: base x detail x 4.59 (linear, gamma 2.0 decode), at the coarser map's tiling with
    the finer one repeated a whole number of times inside. Returns (uri, transform [sx, sy, ox, oy])."""
    import hashlib
    key = json.dumps([buri, bx, duri, dx, dco])
    name = 'baked__' + hashlib.md5(key.encode()).hexdigest()[:16] + '.png'
    B = img(buri)
    if duri is None:
        xf = list(bx)
        D = None
    else:
        D = img(duri)
    reps = []; size = []; xf = [0, 0, 0, 0]
    for ax in range(2):
        b, d = abs(bx[ax]) or 1, abs(dx[ax]) or 1 if D is not None else (abs(bx[ax]) or 1)
        if D is None or d >= b:
            n = max(1, round(d / b)); xf[ax] = bx[ax]; xf[2 + ax] = bx[2 + ax]; reps.append(('d', n))
            cw = B.size[ax]; fw = D.size[ax] * n if D is not None else 0
        else:
            n = max(1, round(b / d)); xf[ax] = dx[ax]; xf[2 + ax] = dx[2 + ax]; reps.append(('b', n))
            cw = D.size[ax]; fw = B.size[ax] * n
        size.append(pow2(max(cw, fw)))
    W, H = size
    def tiled(I, nx, ny):
        tw, th = max(1, round(W / nx)), max(1, round(H / ny))
        t = np.asarray(I.resize((tw, th), Image.LANCZOS), np.float32) / 255
        t = np.tile(t, (ny, nx, 1))
        return np.asarray(Image.fromarray((t * 255).astype(np.uint8)).resize((W, H), Image.LANCZOS), np.float32) / 255
    nb = [r[1] if r[0] == 'b' else 1 for r in reps]; nd = [r[1] if r[0] == 'd' else 1 for r in reps]
    lb = tiled(B, nb[0], nb[1]) ** 2.0
    if D is not None:
        ld = tiled(D, nd[0], nd[1]) ** 2.0
        lin = lb * ld * 4.59479 * dco
    else:
        lin = lb * dco
    if not __import__('os').path.exists(TEXDIR + '/' + name):
        out = (np.clip(lin, 0, 1) ** (1 / 2.2) * 255 + 0.5).astype(np.uint8)
        Image.fromarray(out).save(TEXDIR + '/' + name, optimize=False, compress_level=6)
    return 'tex/' + name, xf


def detail_factor(j, m):
    """Average brightness a shader's detail map(s) add on top of its base map (which is all the game draws)."""
    h = (m.get('extras') or {}).get('halo') or {}
    t = h.get('textures', {}); cu = h.get('curves', {}); r = h.get('reals', {}); op = h.get('options', {})
    f = np.ones(3)
    alb = op.get('albedo', 'default')
    for nm in ('detail_map', 'detail_map2'):
        if nm not in t or (nm == 'detail_map2' and alb not in ('two_detail', 'two_detail_overlay', 'two_detail_black_point')):
            continue
        uri = j['images'][j['textures'][t[nm]]['source']].get('uri')
        if not uri: continue
        a = np.asarray(img(uri).resize((64, 64), Image.BOX), np.float32) / 255
        if cu.get(nm) in (None, 1, 2, 5): a = a ** 2.0
        f *= a.reshape(-1, 3).mean(0) * 4.59479
    ac = r.get('albedo_color')
    if ac and alb == 'default': f *= np.array(ac[:3])
    return np.clip(f, 0.25, 4.0)


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
    layer_mat = {}
    def get_layer_mat(mi, k, buri, bx, duri, dx, dco):
        key = (mi, k)
        if key not in layer_mat:
            uri, xf = bake_layer(buri, bx, duri, dx, dco)
            j['images'].append(dict(uri=uri, mimeType='image/png'))
            j['textures'].append(dict(sampler=0, source=len(j['images']) - 1))
            ti = len(j['textures']) - 1
            ref = dict(index=ti)
            if tuple(xf) != (1, 1, 0, 0): ref['extensions'] = dict(KHR_texture_transform=dict(scale=xf[:2], offset=xf[2:]))
            mm = dict(name=j['materials'][mi]['name'] + '_L%d' % k, pbrMetallicRoughness=dict(baseColorTexture=ref, metallicFactor=0.0, roughnessFactor=0.9),
                      doubleSided=j['materials'][mi].get('doubleSided', False),
                      extras=dict(halo=dict(group='baked_layer', layer=k, textures=dict(base_map=ti), reals=dict(base_map=xf), curves=dict(base_map=3))))
            if k > 0: mm['alphaMode'] = 'BLEND'
            j['materials'].append(mm); layer_mat[key] = len(j['materials']) - 1
        return layer_mat[key]
    dfac = {}
    for mesh in j['meshes']:
        prims = []
        for p in mesh['primitives']:
            at = p['attributes']; mi = p.get('material')
            h = ((j['materials'][mi].get('extras') or {}).get('halo') or {}) if mi is not None else {}
            P = g.acc(at['POSITION']).astype(np.float32)
            T = g.acc(p['indices']).astype(np.int64).reshape(-1, 3)
            A = {'pos': P.astype(np.float64)}
            if 'TEXCOORD_0' in at: A['uv0'] = g.acc(at['TEXCOORD_0']).astype(np.float64)
            nv0 += len(P)
            sigs = []
            lit = 'TEXCOORD_1' in at and L is not None
            if lit:
                A['uv1'] = g.acc(at['TEXCOORD_1']).astype(np.float64)
                sigs.append(dict(f=lambda d: encode(sample(L, d['uv1'])), uv='uv1', sc=(L.shape[1], L.shape[0]), tol=TOL, mn=MIN_EDGE))
            elif 'COLOR_0' in at:
                A['col'] = g.acc(at['COLOR_0']).astype(np.float64)[:, :3]
            # terrain: active layers and the blend map that mixes them
            layers = []; Bm = None
            if h.get('group') == 'rmtr' and 'uv0' in A:
                t = h.get('textures', {}); r = h.get('reals', {}); op = h.get('options', {})
                for k in range(4):
                    if op.get('material_%d' % k, 'off') == 'off' or 'base_map_m_%d' % k not in t: continue
                    uri = lambda nm: j['images'][j['textures'][t[nm]]['source']]['uri'] if nm in t else None
                    layers.append(dict(k=k, b=uri('base_map_m_%d' % k), bx=r.get('base_map_m_%d' % k, [1, 1, 0, 0]),
                                       d=uri('detail_map_m_%d' % k), dx=r.get('detail_map_m_%d' % k, [1, 1, 0, 0]),
                                       dco=(r.get('diffuse_coefficient_m_%d' % k) or [1])[0]))
                if len(layers) > 1 and 'blend_map' in t:
                    buri = j['images'][j['textures'][t['blend_map']]['source']]['uri']
                    Bm = np.asarray(img(buri, 'RGBA'), np.float32) / 255
                    bxf = np.array(r.get('blend_map', [1, 1, 0, 0]), np.float64)
                    ch = [l['k'] for l in layers]
                    def wf(d, Bm=Bm, bxf=bxf, ch=ch):
                        w = np.maximum(sample_wrap(Bm, d['uv0'] * bxf[:2] + bxf[2:])[:, ch], 0)
                        s = w.sum(1, keepdims=True)
                        return np.where(s > 1e-3, w / np.maximum(s, 1e-6), np.eye(len(ch))[0])
                    sigs.append(dict(f=wf, uv='uv0', sc=(Bm.shape[1] * abs(bxf[0]), Bm.shape[0] * abs(bxf[1])), tol=0.1, mn=2.0))
            A, T = refine(A, T, sigs)
            # light at every vertex, encoded
            if lit:
                C = encode(sample(L, A['uv1']))
            elif 'col' in A:
                C = encode(A['col'])
            else:
                Pp = A['pos']
                fn = np.cross(Pp[T[:, 1]] - Pp[T[:, 0]], Pp[T[:, 2]] - Pp[T[:, 0]]); N = np.zeros_like(Pp)
                for c in range(3): np.add.at(N, T[:, c], fn)
                N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
                C = encode(0.05 + 0.22 * np.maximum(N @ sun, 0)[:, None] * np.array([1.0, 0.95, 0.85]))
            nv1 += len(A['pos'])
            na = dict(POSITION=put(A['pos'].astype(np.float32), 'VEC3', 5126, 34962, minmax=True))
            if 'uv0' in A: na['TEXCOORD_0'] = put(A['uv0'].astype(np.float32), 'VEC2', 5126, 34962)
            if 'uv1' in A: na['TEXCOORD_1'] = put(A['uv1'].astype(np.float32), 'VEC2', 5126, 34962)
            def colacc(Cc, a=None):
                a = np.ones(len(Cc)) if a is None else a
                col = np.concatenate([np.round(np.clip(Cc, 0, 1) * 255), np.round(np.clip(a, 0, 1) * 255)[:, None]], 1).astype(np.uint8)
                return put(col, 'VEC4', 5121, 34962, normalized=True)
            if layers:
                if Bm is not None:
                    W8 = sigs[-1]['f'](A)
                else:
                    W8 = np.zeros((len(C), len(layers))); W8[:, 0] = 1
                cum = np.cumsum(W8, 1)
                for li, l in enumerate(layers):
                    lm_i = get_layer_mat(mi, li, l['b'], l['bx'], l['d'], l['dx'], l['dco'])
                    if li == 0:
                        Tk = T; al = None
                    else:
                        al = W8[:, li] / np.maximum(cum[:, li], 1e-6)
                        keep = (al[T] > 1.5 / 255).any(1); Tk = T[keep]
                        if not len(Tk): continue
                    q = dict(attributes=dict(na, COLOR_0=colacc(C, al)), indices=put(Tk.astype(np.uint32).reshape(-1), 'SCALAR', 5125, 34963), material=lm_i, mode=4)
                    prims.append(q)
                continue
            if mi is not None and h.get('group') in ('rmsh', 'rmfl', 'rmhg', 'rmgl'):
                if mi not in dfac: dfac[mi] = detail_factor(j, j['materials'][mi])
                C = np.clip(C * dfac[mi] ** (1 / 2.2), 0, 1)
            na['COLOR_0'] = colacc(C)
            p['attributes'] = na
            p['indices'] = put(T.astype(np.uint32).reshape(-1), 'SCALAR', 5125, 34963)
            prims.append(p)
        mesh['primitives'] = prims
    j['bufferViews'] = views; j['accessors'] = accs
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
    print('vbake', src.split('/')[-1], 'vertices', nv0, '->', nv1, 'layer mats', len(layer_mat), 'MB %.1f' % (len(out_bin) / 1e6), flush=True)


if __name__ == '__main__':
    main(*sys.argv[1:4])
