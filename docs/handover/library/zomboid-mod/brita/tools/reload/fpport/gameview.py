# The game's maths (GunPoses + RifleView + SurvivorArms.drawPosed, finger split) drawn from the eye: a check against the tool.
import sys, io, struct, zlib, json, importlib.util, numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, '.'); import zsk
ROOT = '/home/user/zomboidcraft'
sp = importlib.util.spec_from_file_location('cp', ROOT + '/tools/cars/pack.py'); cp = importlib.util.module_from_spec(sp); sp.loader.exec_module(cp)
GUNS = '/mnt/project-files/zomboid-mod/brita/local-pc/guns'
ARM = np.array([0.56, -0.52, -0.72])
def zmesh(gid):
    raw = zlib.decompress(cp.scramble(open(f'{GUNS}/{gid}.zmesh', 'rb').read(), 'local/' + gid))
    b = io.BytesIO(raw); b.read(12); png = b.read(struct.unpack('>i', b.read(4))[0])
    tex = np.asarray(Image.open(io.BytesIO(png)).convert('RGB')).astype(float)
    tris = []; cols = []
    for _ in range(struct.unpack('>i', b.read(4))[0]):
        name = b.read(struct.unpack('>H', b.read(2))[0]).decode(); t = struct.unpack('>i', b.read(4))[0]
        rec = np.frombuffer(b.read(t * 39), dtype=[('x', '>i2'), ('y', '>i2'), ('z', '>i2'), ('u', '>u2'), ('v', '>u2'), ('s', 'V3')])
        pos = (np.stack([rec['x'], rec['y'], rec['z']], -1) / 1000.0).reshape(-1, 3, 3)
        uv = (np.stack([rec['u'], rec['v']], -1) / 65535.0).reshape(-1, 3, 2).mean(1)
        h, w = tex.shape[:2]
        c = tex[np.clip((uv[:, 1] * h).astype(int), 0, h - 1), np.clip((uv[:, 0] * w).astype(int), 0, w - 1)]
        tris.append(pos); cols.append(c)
    return np.concatenate(tris), np.concatenate(cols)
def qmat(q):
    x, y, z, w = q
    return np.array([[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)], [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)], [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]])
def M(pos, q):
    m = np.eye(4); m[:3, :3] = qmat(q); m[:3, 3] = pos; return m
PACK = {}
def pack(model='pz_survivor_f'):
    if model in PACK: return PACK[model]
    G, names, par, inv = zsk.load(f'/mnt/project-files/zomboid-mod/survivor/packs/{model}.zsk', model)
    body = [g for g in G if g['piece'] == 'body' and g['level'] == 0][0]
    n = len(names); f1 = [names.index('Bip01_R_Finger1'), names.index('Bip01_L_Finger1')]
    J = body['joint'].copy(); W = body['weight']; P = body['pos']
    for s in range(2):
        ys = []; idx = []
        for v in range(len(P)):
            for k in range(4):
                if J[v, k] == f1[s] and W[v, k] >= 0.05:
                    ys.append((inv[f1[s]] @ np.append(P[v], 1))[1]); idx.append(v); break
        a = np.sort(ys); c = np.array([a[len(a) // 8], a[len(a) * 3 // 8], a[len(a) * 5 // 8], a[len(a) * 7 // 8]])
        for _ in range(50):
            g = np.abs(a[:, None] - c[None]).argmin(1); c = np.array([a[g == k].mean() if (g == k).any() else c[k] for k in range(4)])
        for v, y in zip(idx, ys):
            k = int(np.abs(c - y).argmin()); finger = 3 - k if s == 0 else k
            for q in range(4):
                if body['joint'][v, q] == f1[s] and W[v, q] > 0: J[v, q] = n + s * 4 + finger
    armb = {names.index(f'Bip01_{sd}_{p}') for sd in 'RL' for p in ['UpperArm', 'Forearm', 'Hand', 'Finger0', 'Finger1']}
    low = armb - {names.index('Bip01_R_UpperArm'), names.index('Bip01_L_UpperArm')}
    oj = body['joint']
    okv = np.array([sum(W[v, k] for k in range(4) if W[v, k] > 0 and oj[v, k] in armb) > 0.99 and oj[v, np.argmax(W[v])] in low for v in range(len(P))])
    Tr = body['tris']; Tr = Tr[okv[Tr].all(1)]
    rest = [np.linalg.inv(m)[:3, 3] for m in inv]
    side = [0 if np.sum((r - rest[names.index('Bip01_R_UpperArm')])**2) <= np.sum((r - rest[names.index('Bip01_L_UpperArm')])**2) else 1 for r in rest]
    PACK[model] = (names, inv, P, J, W, Tr, side)
    return PACK[model]
FING = ['index', 'middle', 'ring', 'little']
def arms(bones, model='pz_survivor_f'):
    names, inv, P, J, W, T, side = pack(model); n = len(names)
    m = [None] * (n + 8); sh = {}
    for s, sd in enumerate('RL'):
        sh[s] = np.array(bones[sd + '_UpperArm'][:3])
        for part in ['UpperArm', 'Forearm', 'Hand', 'Finger0', 'Finger1']:
            b = names.index(f'Bip01_{sd}_{part}'); v = bones[f'{sd}_{part}']; m[b] = M(v[:3], v[3:]) @ inv[b]
        f1 = names.index(f'Bip01_{sd}_Finger1')
        for k in range(4):
            v = bones.get(f'{sd}_Finger1_{FING[k]}', bones[f'{sd}_Finger1']); m[n + s * 4 + k] = M(v[:3], v[3:]) @ inv[f1]
    for b in range(n + 8):
        if m[b] is None:
            z = np.zeros((4, 4)); z[:3, 3] = sh[side[b]]; z[3, 3] = 1; m[b] = z
    Mm = np.stack(m); ph = np.concatenate([P, np.ones((len(P), 1))], 1)
    out = np.zeros((len(P), 3))
    for k in range(4):
        out += W[:, k:k + 1] * np.einsum('vij,vj->vi', Mm[J[:, k]], ph)[:, :3]
    return out[T]
def clip(T, C, near=0.05):
    outT = []; outC = []
    for t, c in zip(T, C):
        inside = -t[:, 2] >= near
        if inside.all(): outT.append(t); outC.append(c); continue
        if not inside.any(): continue
        poly = []
        for k in range(3):
            a, b = t[k], t[(k + 1) % 3]; ia, ib = -a[2] >= near, -b[2] >= near
            if ia: poly.append(a)
            if ia != ib:
                u = (-near - a[2]) / (b[2] - a[2]); poly.append(a + u * (b - a))
        for k in range(1, len(poly) - 1): outT.append(np.array([poly[0], poly[k], poly[k + 1]])); outC.append(c)
    return np.array(outT), np.array(outC)
def render(gid, mode, poses, size=(1210, 681), vfov=70):
    p = poses[mode]; g = p['gun']; q = qmat(g['q']); s = g['scale']; t = np.array(g['t'])
    tris, cols = zmesh(gid)
    gun = (tris / 16 - 0.5) * s @ q.T + t
    arm = arms(p['bones'])
    allt = np.concatenate([gun, arm]) + ARM          # hand frame -> eye frame
    allc = np.concatenate([cols, np.tile([[224, 172, 140]], (len(arm), 1))])
    W, H = size; f = H / 2 / np.tan(np.radians(vfov / 2))
    allt, allc = clip(allt, allc)
    z = -allt[..., 2]; ok = (z > 0.0499).all(1)
    nrm = np.cross(allt[:, 1] - allt[:, 0], allt[:, 2] - allt[:, 0]); ln = np.linalg.norm(nrm, axis=1) + 1e-12
    light = np.abs(nrm @ np.array([0.3, 0.8, 0.5]) / ln / np.linalg.norm([0.3, 0.8, 0.5]))
    sx = W / 2 + f * allt[..., 0] / np.maximum(z, 1e-3); sy = H / 2 - f * allt[..., 1] / np.maximum(z, 1e-3)
    order = np.argsort(-z.mean(1))
    im = Image.new('RGB', size, (222, 226, 230)); d = ImageDraw.Draw(im)
    for i in order:
        if not ok[i] or ln[i] < 1e-10: continue
        c = tuple(int(v) for v in np.clip(allc[i] * (0.45 + 0.55 * light[i]), 0, 255))
        d.polygon([(sx[i, k], sy[i, k]) for k in range(3)], fill=c)
    d.line([(W/2-8, H/2), (W/2+8, H/2)], fill=(200, 40, 40)); d.line([(W/2, H/2-8), (W/2, H/2+8)], fill=(200, 40, 40))
    return im
if __name__ == '__main__':
    gid, mode = sys.argv[1], sys.argv[2]
    render(gid, mode, json.load(open(f'poses_out/{gid}.json'))).save(f'game_{gid}_{mode}.png')
