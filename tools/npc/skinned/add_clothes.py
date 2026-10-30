"""Adds clothes that Mixamo never saw to mix.json, for any character. clothes.json comes from clothes.html: the body and the
pieces exported together from Blender, posed exactly as the Mixamo upload. The exported body is fitted onto mix.json's body
(turn, scale and place found by itself), the pieces move with it, and each piece copies the skin of the Mixamo body under
it (inverse-distance over the nearest body vertices), so it bends with her.
    python add_clothes.py mix.json clothes.json out.json [body prefix, default Vanilla]
Pieces keep their exported names ("<mesh>.<material>" when a mesh has several materials); give them a piece in the spec."""
import sys, json, base64, itertools
import numpy as np
from scipy.spatial import cKDTree

mix_path, clothes_path, out_path = sys.argv[1:4]
prefix = sys.argv[4] if len(sys.argv) > 4 else 'Vanilla'
dec = lambda s, t: np.frombuffer(base64.b64decode(s), t)
enc = lambda a: base64.b64encode(np.ascontiguousarray(a).tobytes()).decode()

raw = json.load(open(mix_path))
nb = len(raw['bones'])
body = [m for m in raw['meshes'] if m['name'].startswith(prefix)]
if not body:
    sys.exit('no mesh in mix.json starts with ' + prefix)
bpos = np.concatenate([dec(m['pos'], np.float32).reshape(-1, 3) for m in body]).astype(np.float64)
bsi = np.concatenate([dec(m['si'], np.uint8).reshape(-1, 4) for m in body]).astype(np.int64)
bsw = np.concatenate([dec(m['sw'], np.float32).reshape(-1, 4) for m in body]).astype(np.float64)
tree = cKDTree(bpos)

cl = json.load(open(clothes_path))['meshes']
for m in cl:
    m['pos'] = dec(m['pos'], np.float32).reshape(m['n'], 3).astype(np.float64)
src = [m for m in cl if m['name'].startswith(prefix)]
if not src:
    sys.exit('export the body (' + prefix + ') together with the clothes, so they can be lined up')
spos = np.concatenate([m['pos'] for m in src])
sample = spos[np.random.default_rng(1).choice(len(spos), min(4000, len(spos)), replace=False)]

def fit(R):
    """Scale and place after turn R by bounding box, then a few closest-point passes; returns (s, R, t, mean miss)."""
    p = sample @ R.T
    s = np.ptp(bpos[:, 1]) / max(np.ptp(p[:, 1]), 1e-9)
    t = (bpos.min(0) + bpos.max(0)) / 2 - s * (p.min(0) + p.max(0)) / 2
    for _ in range(12):
        q = s * p + t
        d, j = tree.query(q)
        a, b = p, bpos[j]
        ma, mb = a.mean(0), b.mean(0)
        s = ((a - ma) * (b - mb)).sum() / max(((a - ma) ** 2).sum(), 1e-12)
        t = mb - s * ma
    d, _ = tree.query(s * p + t)
    return s, R, t, d.mean()

# every right-angled turn, in case Blender and Mixamo disagree which way is up or forward
turns = []
for perm in itertools.permutations(range(3)):
    for sg in itertools.product((1, -1), repeat=3):
        R = np.zeros((3, 3))
        for r, (c, g) in enumerate(zip(perm, sg)):
            R[r, c] = g
        if np.linalg.det(R) > 0:
            turns.append(R)
s, R, t, miss = min((fit(R) for R in turns), key=lambda f: f[3])
height = np.ptp(bpos[:, 1])
print(f'body lined up: miss {miss * 100:.2f} cm on a {height:.2f} m body (under about 1 cm is good)')

def normals(pos, ix):
    n = np.zeros_like(pos); tri = pos[ix]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    for k in range(3): np.add.at(n, ix[:, k], fn)
    return n / (np.linalg.norm(n, axis=1, keepdims=True) + 1e-12)

names = {m['name'] for m in raw['meshes']}
for m in cl:
    if m['name'].startswith(prefix):
        continue
    n = m['n']
    pos = s * (m['pos'] @ R.T) + t
    ix = dec(m['ix'], np.uint32).reshape(-1, 3).astype(np.int64)
    d, j = tree.query(pos, k=8)
    w = 1 / (d + 0.004) ** 2; w /= w.sum(1, keepdims=True)
    W = np.zeros((n, nb))
    for k in range(8):
        np.add.at(W, (np.repeat(np.arange(n), 4), bsi[j[:, k]].ravel()), (bsw[j[:, k]] * w[:, k:k + 1]).ravel())
    top = np.argsort(-W, 1)[:, :4]
    tw = np.take_along_axis(W, top, 1); tw /= np.maximum(tw.sum(1, keepdims=True), 1e-9)
    name = m['name'].replace(' ', '_')
    raw['meshes'] = [x for x in raw['meshes'] if x['name'] != name]
    raw['meshes'].append({'name': name, 'n': n, 'pos': enc(pos.astype(np.float32)), 'normal': enc(normals(pos, ix).astype(np.float32)),
                          'uv': enc(dec(m['uv'], np.float32)), 'si': enc(top.astype(np.uint8)), 'sw': enc(tw.astype(np.float32)),
                          'ix': enc(ix.astype(np.uint32))})
    print(f'{name}: {n} verts, {len(ix)} tris, {d[:, 0].max() * 100:.1f} cm from the body at most')
json.dump(raw, open(out_path, 'w'))
print('wrote', out_path)
