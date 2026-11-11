"""Convert Tomb's FBX body to the preview part format, skinned to the vanilla .x bind (offsets) so PZ anims drive it.
usage: python3 -I tombfbx.py <fbx> <vanilla body .x> <out.json>"""
import sys, json
sys.path.insert(0, sys.argv[0].rsplit('/', 1)[0])
import numpy as np, fbxbin, export as E
fbx, xbody, out = sys.argv[1:4]
r, _ = fbxbin.read(fbx)
objs = r.one('Objects'); conns = r.one('Connections')
byid = {k.props[0]: k for k in objs.kids}
links = [(c.props[1], c.props[2]) for c in conns.kids]
geo = [k for k in objs.kids if k.name == 'Geometry'][0]
V = geo.one('Vertices').props[0].reshape(-1, 3)
PVI = geo.one('PolygonVertexIndex').props[0]
uvl = geo.one('LayerElementUV'); UV = uvl.one('UV').props[0].reshape(-1, 2); UVI = uvl.one('UVIndex').props[0]
print('uv mapping', uvl.one('MappingInformationType').props[0], uvl.one('ReferenceInformationType').props[0])
nl = geo.one('LayerElementNormal'); N = nl.one('Normals').props[0].reshape(-1, 3)
print('normal mapping', nl.one('MappingInformationType').props[0], nl.one('ReferenceInformationType').props[0], N.shape, len(PVI))
# FBX is right-handed, the .x bodies are left-handed (D3D): mirror z
Z = np.array([1.0, 1.0, -1.0]); V = V * Z; N = N * Z
name = lambda n: n.props[1].split('\x00')[0].replace(' ', '_')
clusters = {}
for k in objs.kids:
    if k.name == 'Deformer' and k.props[2] == 'Cluster':
        bone = [byid[c] for c, p in links if p == k.props[0] and c in byid and byid[c].name == 'Model']
        clusters[name(bone[0])] = (k.one('Indexes').props[0] if k.one('Indexes') else np.zeros(0, int),
                                   k.one('Weights').props[0] if k.one('Weights') else np.zeros(0),
                                   k.one('Transform').props[0].reshape(4, 4), k.one('TransformLink').props[0].reshape(4, 4))
print(len(clusters), sorted(clusters)[:40])
# vanilla .x body: offsets
root, xb = E.mesh_data(xbody)
xoff = {b['name']: np.array(b['off']).reshape(4, 4) for b in xb[0]['bones']}
X, Y = [], []
for b, (_, _, T, TL) in clusters.items():
    if b in xoff:
        pm = (TL @ np.linalg.inv(T))[3, :3] if False else (TL[3, :3])      # bone origin, fbx global
        X.append(pm * Z); Y.append(np.linalg.inv(xoff[b])[3, :3])
X, Y = np.array(X), np.array(Y)
mx, my = X.mean(0), Y.mean(0); Xc, Yc = X - mx, Y - my
U, Sg, Vt = np.linalg.svd(Yc.T @ Xc); D = np.eye(3); D[2, 2] = np.sign(np.linalg.det(U @ Vt))
R = U @ D @ Vt; s = (Sg * np.diag(D)).sum() / (Xc ** 2).sum(); t = my - s * R @ mx
res = np.linalg.norm((s * (R @ X.T)).T + t - Y, axis=1)
print('similarity scale', s, 'resid max/mean', res.max(), res.mean(), 'n', len(X))
print(np.round(R, 3))
Vx = (s * (R @ V.T)).T + t
# weights per control point, only bones the .x body skins; others fold into nearest ancestor present
names = [b for b in clusters if b in xoff]
bi = {b: i for i, b in enumerate(names)}
W = [dict() for _ in range(len(V))]
parent = {f['name']: f['parent'] for f in E.frames(root)}
def keep(b):
    while b and b not in bi: b = parent.get(b)
    return b
for b, (idx, w, _, _) in clusters.items():
    kb = keep(b)
    if kb is None: continue
    for i, ww in zip(idx, w): W[i][bi[kb]] = W[i].get(bi[kb], 0) + ww
P, NN, TT, SI, SW = [], [], [], [], []
poly = []; pvi_i = 0
for j, v in enumerate(PVI):
    poly.append((v if v >= 0 else ~v, j))
    if v < 0:
        for k in range(1, len(poly) - 1):
            for (cp, pv) in (poly[0], poly[k], poly[k + 1]):
                P.extend(Vx[cp]); n = R @ N[pv]; NN.extend(n / (np.linalg.norm(n) + 1e-9))
                u = UV[UVI[pv]]; TT.extend((u[0], 1 - u[1]))
                top = sorted(W[cp].items(), key=lambda kv: -kv[1])[:4]; sm = sum(x for _, x in top) or 1
                top += [(0, 0.0)] * (4 - len(top)); SI.extend(int(a) for a, _ in top); SW.extend(x / sm for _, x in top)
        poly = []
rd = lambda a, d=5: [round(float(x), d) for x in a]
part = {'name': 'TombBody', 'pos': rd(P), 'nrm': rd(NN, 3), 'uv': rd(TT), 'si': SI, 'sw': rd(SW, 3),
        'bones': [{'name': b, 'off': xoff[b].flatten().tolist()} for b in names], 'bbox': [Vx.min(0).tolist(), Vx.max(0).tolist()]}
print('tris', len(P) // 9, 'bbox', part['bbox'], 'vanilla bbox', xb[0]['bbox'])
json.dump(part, open(out, 'w'), separators=(',', ':'))
