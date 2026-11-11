"""Export PZ .x body/clothes/anims to one JSON for the three.js preview.
usage: python3 -I export.py <media dir> <out.json>
"""
import sys, json, math
sys.path.insert(0, sys.argv[0].rsplit('/', 1)[0])
import xfile
import numpy as np

media = sys.argv[1].rstrip('/')
out = sys.argv[2]


def mat(o):
    return np.array(o.nums[:16], dtype=float).reshape(4, 4)  # D3D row-major (row vectors)


def frames(root):
    res = []

    def rec(o, parent):
        for k in o.kids:
            if k.kind == 'Frame':
                ftm = k.child('FrameTransformMatrix')
                m = mat(ftm[0]) if ftm else np.eye(4)
                res.append({'name': k.name, 'parent': parent, 'm': m})
                rec(k, k.name)
    rec(root, None)
    return res


def mesh_data(path, keep=None):
    root = xfile.parse(path)
    meshes = list(xfile.walk(root, 'Mesh'))
    outm = []
    for me in meshes:
        if keep and me.name not in keep:
            continue
        nums = me.nums
        nv = int(nums[0])
        pos = np.array(nums[1:1 + 3 * nv]).reshape(nv, 3)
        p = 1 + 3 * nv
        nf = int(nums[p]); p += 1
        faces = []
        for _ in range(nf):
            c = int(nums[p]); idx = [int(x) for x in nums[p + 1:p + 1 + c]]; p += 1 + c
            faces.append(idx)
        nrm = me.child('MeshNormals')[0]
        nn = int(nrm.nums[0])
        nvec = np.array(nrm.nums[1:1 + 3 * nn]).reshape(nn, 3)
        q = 1 + 3 * nn
        nfn = int(nrm.nums[q]); q += 1
        nfaces = []
        for _ in range(nfn):
            c = int(nrm.nums[q]); idx = [int(x) for x in nrm.nums[q + 1:q + 1 + c]]; q += 1 + c
            nfaces.append(idx)
        tc = me.child('MeshTextureCoords')[0]
        nt = int(tc.nums[0])
        uv = np.array(tc.nums[1:1 + 2 * nt]).reshape(nt, 2)
        # skin
        W = [dict() for _ in range(nv)]
        bones = []
        for sw in me.child('SkinWeights'):
            bname = sw.strs[0]
            k = int(sw.nums[0])
            idx = [int(x) for x in sw.nums[1:1 + k]]
            wts = sw.nums[1 + k:1 + 2 * k]
            off = np.array(sw.nums[1 + 2 * k:1 + 2 * k + 16]).reshape(4, 4)
            bi = len(bones)
            bones.append({'name': bname, 'off': off.flatten().tolist()})
            for a, b in zip(idx, wts):
                W[a][bi] = W[a].get(bi, 0) + b
        P, N, T, SI, SW = [], [], [], [], []
        for f, fn in zip(faces, nfaces):
            for t in range(1, len(f) - 1):
                for c in (0, t, t + 1):
                    vi = f[c]
                    P.extend(pos[vi]); N.extend(nvec[fn[c]]); T.extend(uv[vi])
                    top = sorted(W[vi].items(), key=lambda kv: -kv[1])[:4]
                    s = sum(w for _, w in top) or 1
                    top += [(0, 0.0)] * (4 - len(top))
                    SI.extend(int(b) for b, _ in top); SW.extend(w / s for _, w in top)
        r = lambda a, d=5: [round(float(x), d) for x in a]
        outm.append({'name': me.name, 'pos': r(P), 'nrm': r(N, 3), 'uv': r(T), 'si': SI, 'sw': r(SW, 3), 'bones': bones,
                     'bbox': [pos.min(0).tolist(), pos.max(0).tolist()]})
    return root, outm


def anim(path):
    root = xfile.parse(path)
    tps = 4800.0
    for o in root.kids:
        if o.kind == 'AnimTicksPerSecond':
            tps = o.nums[0]
    aset = [o for o in root.kids if o.kind == 'AnimationSet'][0]
    tracks = []
    dur = 0
    for a in aset.child('Animation'):
        bone = a.refs[0]
        tr = {'bone': bone}
        for k in a.child('AnimationKey'):
            ns = k.nums
            kt = int(ns[0]); nk = int(ns[1]); p = 2
            times, vals = [], []
            for _ in range(nk):
                t = ns[p]; c = int(ns[p + 1]); v = ns[p + 2:p + 2 + c]; p += 2 + c
                times.append(t / tps); vals.append(v)
            dur = max(dur, times[-1])
            if kt == 0:
                # .x stores w,x,y,z for the row-vector matrix; three wants the conjugate (x,y,z,w)
                tr['qt'] = [round(x, 4) for x in times]
                tr['q'] = [round(c, 5) for w, x, y, z in vals for c in (-x, -y, -z, w)]
            elif kt == 1:
                tr['st'] = [round(x, 4) for x in times]; tr['s'] = [round(c, 5) for v in vals for c in v]
            elif kt == 2:
                tr['pt'] = [round(x, 4) for x in times]; tr['p'] = [round(c, 5) for v in vals for c in v]
            else:
                raise ValueError(f'key type {kt}')
        tracks.append(tr)
    return {'name': aset.name, 'dur': dur, 'tracks': tracks}


def qmat(x, y, z, w):
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


body_root, body = mesh_data(f'{media}/models_X/Skinned/MaleBody.x')
fr = frames(body_root)
# sanity: Dummy01 rotation key vs frame matrix (column convention = transpose of D3D)
skel = [{'name': f['name'], 'parent': f['parent'], 'm': [round(float(x), 6) for x in f['m'].flatten()]} for f in fr]

clips = {}
for nm in ['Zombie_Walk', 'Zombie_Walk2', 'Zombie_Walk3', 'Zombie_Walk5_Faster', 'Zombie_Sprint', 'Zombie_Sprint02', 'Zombie_Idle']:
    clips[nm] = anim(f'{media}/anims_X/Zombie/{nm}.x')

d = {f['name']: f for f in fr}
tr0 = [t for t in clips['Zombie_Walk']['tracks'] if t['bone'] == 'Bip01_Spine1'][0]
q = tr0['q'][:4]
Mcol = d['Bip01_Spine1']['m'][:3, :3].T
print('spine1 bind (col conv)\n', np.round(Mcol, 3))
print('spine1 key0 (conj)\n', np.round(qmat(*q), 3))

parts = {'body': body}
for key, path in [('trousers', 'Clothes/Bob_Trousers.x'), ('jacket', 'Clothes/Bob_JacketLeather.x'),
                  ('hair', 'Hair/Bob_Hair_Messy.x')]:
    _, ms = mesh_data(f'{media}/models_X/Skinned/{path}')
    parts[key] = ms
    print(key, [(m['name'], len(m['pos']) // 9, m['bbox']) for m in ms])
print('body', [(m['name'], len(m['pos']) // 9, m['bbox']) for m in body])
for nm, c in clips.items():
    b = [t for t in c['tracks'] if t['bone'] == 'Bip01'][0]
    p = b.get('p', [])
    print(nm, round(c['dur'], 3), len(c['tracks']), 'Bip01 pos first/last', p[:3], p[-3:])
json.dump({'skel': skel, 'parts': parts, 'clips': clips}, open(out, 'w'), separators=(',', ':'))
