"""Adds the pack's pieces that Mixamo never saw to mix.json. pieces.json comes from pieces.html (kept with her files in the
project's storage, zomboid-mod/assets/woman/tools/), which poses the original model exactly as the Mixamo upload was
posed (arms out, heels kept), so the pieces land in the same bind space.
Tight pieces copy the skin of the Mixamo body under them (inverse-distance over the nearest body vertices); loose ones
(skirt, slip dress) keep the artist's own skin, renamed from the UE mannequin to Mixamo bones.
    python3 add_pieces.py mix.json pieces.json out.json"""
import os, sys, json, base64, re, importlib.util
import numpy as np
from scipy.spatial import cKDTree
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
spec = importlib.util.spec_from_file_location('zsk', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'pack.py'))
P = importlib.util.module_from_spec(spec); spec.loader.exec_module(P)

TIGHT = ['shirt', 'short', 'boot']
LOOSE = ['skirt', 'Dress']
mix_path, pieces_path, out_path = sys.argv[1:4]
raw = json.load(open(mix_path))
J = P.load_mix(mix_path)
B = J['bones']; bi = {n: i for i, n in enumerate(B)}
body = next(m for m in J['meshes'] if m['name'] == 'body')
tree = cKDTree(body['pos'])
pieces = {m['name']: m for m in json.load(open(pieces_path))['meshes']}
dec = lambda s, t: np.frombuffer(base64.b64decode(s), t)
enc = lambda a: base64.b64encode(np.ascontiguousarray(a).tobytes()).decode()

FING = {'thumb': 'Thumb', 'index': 'Index', 'middle': 'Middle', 'ring': 'Ring', 'pinky': 'Pinky'}
def ue2mix(n):
    s = 'Left' if n.endswith('_l') else 'Right' if n.endswith('_r') else ''
    if n == 'pelvis': return 'Hips'
    if n in ('spine_01', 'spine_02'): return 'Spine'
    if n == 'spine_03': return 'Spine1'
    if n in ('spine_04', 'spine_05'): return 'Spine2'
    if n.startswith('neck'): return 'Neck'
    if n == 'head' or n.startswith(('c_', 'tong')): return 'Head'
    if n.startswith('clavicle'): return s + 'Shoulder'
    if n.startswith('upperarm'): return s + 'Arm'
    if n.startswith('lowerarm'): return s + 'ForeArm'
    if n.startswith('hand') or 'metacarpal' in n: return s + 'Hand'
    m = re.match(r'(thumb|index|middle|ring|pinky)_0(\d)', n)
    if m: return s + 'Hand' + FING[m.group(1)] + m.group(2)
    if n.startswith('thigh'): return s + 'UpLeg'
    if n.startswith('calf'): return s + 'Leg'
    if n.startswith('foot'): return s + 'Foot'
    if n.startswith('ball'): return s + 'ToeBase'
    raise KeyError(n)

def top4(W):
    idx = np.argsort(-W, 1)[:, :4]
    w = np.take_along_axis(W, idx, 1)
    w /= np.maximum(w.sum(1, keepdims=True), 1e-9)
    return idx.astype(np.uint8), w.astype(np.float32)

def normals(pos, ix):
    n = np.zeros_like(pos); t = pos[ix]
    fn = np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0])
    for k in range(3): np.add.at(n, ix[:, k], fn)
    return n / (np.linalg.norm(n, axis=1, keepdims=True) + 1e-12)

for name in TIGHT + LOOSE:
    m = pieces[name]; n = m['n']
    pos = dec(m['pos'], np.float32).reshape(n, 3).astype(np.float64)
    ix = dec(m['ix'], np.uint32).reshape(-1, 3).astype(np.int64)
    W = np.zeros((n, len(B)))
    if name in TIGHT:
        d, j = tree.query(pos, k=8)
        w = 1 / (d + 0.004) ** 2; w /= w.sum(1, keepdims=True)
        for k in range(8):
            np.add.at(W, (np.repeat(np.arange(n), 4), body['si'][j[:, k]].ravel()), (body['sw'][j[:, k]] * w[:, k:k + 1]).ravel())
    else:
        si = dec(m['si'], np.uint16).reshape(n, 4); sw = dec(m['sw'], np.float32).reshape(n, 4)
        names = m['bones']
        for k in range(4):
            tgt = np.array([bi['mixamorig' + ue2mix(names[i])] if x > 0 else 0 for i, x in zip(si[:, k], sw[:, k])])
            np.add.at(W, (np.arange(n), tgt), sw[:, k])
    si, sw = top4(W)
    print(name, n, 'verts', len(ix), 'tris', 'bones used', sorted({B[i][9:] for i, x in zip(si.ravel(), sw.ravel()) if x > 0.05}))
    raw['meshes'].append({'name': name, 'n': n, 'pos': enc(pos.astype(np.float32)), 'normal': enc(normals(pos, ix).astype(np.float32)),
                          'uv': enc(dec(m['uv'], np.float32)), 'si': enc(si), 'sw': enc(sw), 'ix': enc(ix.astype(np.uint32))})
json.dump(raw, open(out_path, 'w'))
